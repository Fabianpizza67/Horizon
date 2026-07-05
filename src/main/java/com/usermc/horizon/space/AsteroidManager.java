package com.usermc.horizon.space;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.AsteroidDAO;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Central manager for the asteroid field system.
 *
 * Three independent jobs run continuously, all NUC-friendly (bounded work
 * per tick, no unbounded loops):
 *
 *   1. Region scanning  — every few seconds, check online players' positions
 *      against already-generated regions. Queue any new regions for generation.
 *
 *   2. Region materialization — pop one queued region per tick (or every few
 *      ticks), turn its ClusterPlans into real AsteroidCluster/AsteroidBlock
 *      objects, and place the actual blocks in the world. Spread across
 *      multiple ticks if a region has several large clusters.
 *
 *   3. Respawn ticking — every RESPAWN_CHECK_INTERVAL ticks, scan mined
 *      blocks whose timer has elapsed and regenerate them with a freshly
 *      randomized material. Only iterates the (small) set of currently-mined
 *      blocks, never the full block list.
 *
 * Only ONE world is asteroid-enabled (the Deep Space void world) — configured
 * via config.yml `asteroids.world-name`.
 */
public class AsteroidManager {

    private static final int  REGION_SCAN_INTERVAL_TICKS   = 100;  // 5 seconds
    private static final int  GENERATION_TICK_INTERVAL     = 2;    // materialize work every 2 ticks
    private static final int  MAX_BLOCKS_PLACED_PER_TICK   = 400;  // throttle world edits
    private static final int  RESPAWN_CHECK_INTERVAL_TICKS = 200;  // 10 seconds
    private static final int  PLAYER_GENERATION_RADIUS     = AsteroidFieldGenerator.REGION_SIZE; // regions within 1 region-radius

    private final Horizon              plugin;
    private final AsteroidDAO          dao;
    private AsteroidFieldGenerator     generator;

    /** All loaded clusters, keyed by cluster UUID. */
    private final Map<UUID, AsteroidCluster> clusters = new HashMap<>();

    /** Fast lookup: packed block location → [clusterId, blockId], for mining events. */
    private final Map<Long, UUID[]> blockIndex = new HashMap<>();

    /** Currently-mined blocks awaiting respawn — small working set, not the full block list. */
    private final Set<AsteroidBlock> mining = new HashSet<>();

    /** Region keys already generated (from DB at startup, plus newly generated this session). */
    private final Set<Long> generatedRegions = new HashSet<>();

    /** Regions queued for materialization. */
    private final Queue<long[]> regionQueue = new ConcurrentLinkedQueue<>();

    /** Pending cluster plans not yet materialized into blocks (split across ticks). */
    private final Queue<AsteroidFieldGenerator.ClusterPlan> pendingPlans = new ConcurrentLinkedQueue<>();

    private World asteroidWorld;
    private BukkitTask scanTask, materializeTask, respawnTask;

    public AsteroidManager(Horizon plugin) {
        this.plugin = plugin;
        this.dao    = new AsteroidDAO(plugin);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        String worldName = plugin.getHorizonConfig().getAsteroidWorldName();
        asteroidWorld = Bukkit.getWorld(worldName);
        if (asteroidWorld == null) {
            plugin.getLogger().warning("Asteroid world '" + worldName + "' not found — asteroid system disabled.");
            return;
        }

        generator = new AsteroidFieldGenerator(asteroidWorld.getSeed());
        generatedRegions.addAll(dao.loadGeneratedRegionKeys());

        for (AsteroidCluster cluster : dao.loadAllClusters()) {
            clusters.put(cluster.getClusterId(), cluster);
        }

        Map<UUID, List<AsteroidBlock>> blocksByCluster = dao.loadAllBlocksByCluster();
        for (var entry : blocksByCluster.entrySet()) {
            AsteroidCluster cluster = clusters.get(entry.getKey());
            if (cluster == null) continue;
            for (AsteroidBlock block : entry.getValue()) {
                cluster.addBlock(block);
                indexBlock(cluster, block);
                if (block.isMined()) mining.add(block);
            }
        }

        plugin.getLogger().info("Loaded " + clusters.size() + " asteroid cluster(s), "
                + blockLocationCount() + " block(s), " + generatedRegions.size() + " region(s) on record.");

        startTasks();
    }

    public void shutdown() {
        if (scanTask        != null) scanTask.cancel();
        if (materializeTask != null) materializeTask.cancel();
        if (respawnTask     != null) respawnTask.cancel();
    }

    private int blockLocationCount() { return blockIndex.size(); }

    private void startTasks() {
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanForNewRegions,
                20L, REGION_SCAN_INTERVAL_TICKS);

        materializeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::materializeTick,
                40L, GENERATION_TICK_INTERVAL);

        respawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::respawnTick,
                100L, RESPAWN_CHECK_INTERVAL_TICKS);
    }

    // -----------------------------------------------------------------------
    // Job 1: Region scanning
    // -----------------------------------------------------------------------

    private void scanForNewRegions() {
        if (asteroidWorld == null) return;

        for (Player p : asteroidWorld.getPlayers()) {
            Location loc = p.getLocation();
            long[] centerRegion = AsteroidFieldGenerator.regionOf(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            // Check the 3x3x3 block of regions around the player's current region
            for (long dx = -1; dx <= 1; dx++) {
                for (long dy = -1; dy <= 1; dy++) {
                    for (long dz = -1; dz <= 1; dz++) {
                        long rx = centerRegion[0] + dx;
                        long ry = centerRegion[1] + dy;
                        long rz = centerRegion[2] + dz;
                        long key = AsteroidDAO.packRegionKey(rx, ry, rz);
                        if (generatedRegions.contains(key)) continue;

                        generatedRegions.add(key); // mark immediately to avoid double-queueing
                        regionQueue.add(new long[]{rx, ry, rz});
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Job 2: Region materialization (spread across ticks)
    // -----------------------------------------------------------------------

    private void materializeTick() {
        int blocksPlacedThisTick = 0;

        // First, drain any pending cluster plans from a region already popped
        while (!pendingPlans.isEmpty() && blocksPlacedThisTick < MAX_BLOCKS_PLACED_PER_TICK) {
            AsteroidFieldGenerator.ClusterPlan plan = pendingPlans.poll();
            blocksPlacedThisTick += materializeCluster(plan);
        }

        // Then pop a new region if we still have tick budget left
        if (blocksPlacedThisTick < MAX_BLOCKS_PLACED_PER_TICK && !regionQueue.isEmpty()) {
            long[] region = regionQueue.poll();
            if (region != null) {
                List<AsteroidFieldGenerator.ClusterPlan> plans =
                        generator.generateRegionPlan(region[0], region[1], region[2]);
                pendingPlans.addAll(plans);
                dao.markRegionGenerated(region[0], region[1], region[2]);
            }
        }
    }

    /** Turns one ClusterPlan into a real AsteroidCluster with placed blocks. Returns blocks placed. */
    private int materializeCluster(AsteroidFieldGenerator.ClusterPlan plan) {
        Random rng = new Random(seedFor(plan));
        List<int[]> shape = AsteroidShapeGenerator.generateBlob(plan.tier().randomSize(rng), rng);

        UUID clusterId = UUID.randomUUID();
        AsteroidCluster cluster = new AsteroidCluster(clusterId, plan.tier(), asteroidWorld.getName(),
                plan.centerX(), plan.centerY(), plan.centerZ(), System.currentTimeMillis());

        List<AsteroidBlock> blocks = new ArrayList<>(shape.size());
        for (int[] offset : shape) {
            int wx = plan.centerX() + offset[0];
            int wy = plan.centerY() + offset[1];
            int wz = plan.centerZ() + offset[2];

            if (wy < asteroidWorld.getMinHeight() || wy > asteroidWorld.getMaxHeight()) continue;

            Material mat = plan.tier().randomMaterial(rng);
            Block block = asteroidWorld.getBlockAt(wx, wy, wz);
            if (!block.getType().isAir()) continue; // don't overwrite existing structures (ships, stations)
            block.setType(mat, false);

            AsteroidBlock ab = new AsteroidBlock(UUID.randomUUID(), clusterId,
                    offset[0], offset[1], offset[2], mat, false, 0, 0);
            blocks.add(ab);
            cluster.addBlock(ab);
            indexBlock(cluster, ab);
        }

        clusters.put(clusterId, cluster);
        dao.saveCluster(cluster);
        dao.saveBlocksSync(blocks); // one-time batch insert, acceptable as a rare event

        return blocks.size();
    }

    private long seedFor(AsteroidFieldGenerator.ClusterPlan plan) {
        long h = asteroidWorld.getSeed();
        h = h * 31 + plan.centerX();
        h = h * 31 + plan.centerY();
        h = h * 31 + plan.centerZ();
        return h;
    }

    // -----------------------------------------------------------------------
    // Job 3: Respawn ticking
    // -----------------------------------------------------------------------

    private void respawnTick() {
        if (mining.isEmpty()) return;

        List<AsteroidBlock> ready = new ArrayList<>();
        for (AsteroidBlock b : mining) {
            if (b.isReadyToRespawn()) ready.add(b);
        }
        if (ready.isEmpty()) return;

        for (AsteroidBlock block : ready) {
            AsteroidCluster cluster = clusters.get(block.getClusterId());
            if (cluster == null) { mining.remove(block); continue; }

            Material newMat = cluster.getType().randomMaterial(new Random());
            block.respawn(newMat);
            mining.remove(block);

            int wx = cluster.getCenterX() + block.getRelX();
            int wy = cluster.getCenterY() + block.getRelY();
            int wz = cluster.getCenterZ() + block.getRelZ();

            Block worldBlock = asteroidWorld.getBlockAt(wx, wy, wz);
            if (worldBlock.getType().isAir()) {
                worldBlock.setType(newMat, false);
            }
            // If something else occupies the space now (a ship flew through), skip placing —
            // the block stays logically "respawned" and will just not show physically.
            // Edge case, acceptable: next mine attempt on that location simply won't find
            // a registered asteroid block there since the index still points to it correctly.

            dao.updateBlockState(block);
        }
    }

    // -----------------------------------------------------------------------
    // Mining — called from AsteroidMiningListener
    // -----------------------------------------------------------------------

    /** Returns true if this location is a registered (currently active) asteroid block. */
    public boolean isAsteroidBlock(Location loc) {
        return blockIndex.containsKey(packLoc(loc));
    }

    /**
     * Process a mining event for the given location.
     * Returns the material that was mined, or null if not an asteroid block
     * or it was already mined (race condition safety).
     */
    public Material mineBlock(Location loc) {
        long key = packLoc(loc);
        UUID[] ids = blockIndex.get(key);
        if (ids == null) return null;

        AsteroidCluster cluster = clusters.get(ids[0]);
        if (cluster == null) return null;

        AsteroidBlock block = findBlock(cluster, ids[1]);
        if (block == null || block.isMined()) return null;

        Material mined = block.getCurrentMaterial();
        long respawnDelay = cluster.getType().randomRespawnMs(new Random());
        block.markMined(respawnDelay);
        mining.add(block);
        dao.updateBlockState(block);

        return mined;
    }

    private AsteroidBlock findBlock(AsteroidCluster cluster, UUID blockId) {
        for (AsteroidBlock b : cluster.getBlocks()) {
            if (b.getBlockId().equals(blockId)) return b;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Indexing helpers
    // -----------------------------------------------------------------------

    private void indexBlock(AsteroidCluster cluster, AsteroidBlock block) {
        int wx = cluster.getCenterX() + block.getRelX();
        int wy = cluster.getCenterY() + block.getRelY();
        int wz = cluster.getCenterZ() + block.getRelZ();
        long key = packWorldCoord(wx, wy, wz);
        blockIndex.put(key, new UUID[]{cluster.getClusterId(), block.getBlockId()});
    }

    private long packLoc(Location loc) {
        return packWorldCoord(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private long packWorldCoord(int x, int y, int z) {
        long ox = (long)(x + 30_000_000);
        long oy = (long)(y + 512);
        long oz = (long)(z + 30_000_000);
        return (ox << 37) | (oy << 26) | oz;
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public Collection<AsteroidCluster> getAllClusters() { return Collections.unmodifiableCollection(clusters.values()); }

    public AsteroidCluster getNearestCluster(Location loc) {
        AsteroidCluster nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (AsteroidCluster c : clusters.values()) {
            double d = c.distanceTo(loc);
            if (d < bestDist) { bestDist = d; nearest = c; }
        }
        return nearest;
    }

    public World getAsteroidWorld() { return asteroidWorld; }
}