package com.usermc.horizon.warp;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.WarpDAO;
import com.usermc.horizon.ship.RelativeBlock;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.ShipScanner;
import com.usermc.horizon.ship.ShipStructure;
import com.usermc.horizon.story.StoryObjectiveType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;

/**
 * Manages warp beacons and the full warp sequence for ships.
 *
 * Warp sequence:
 *   1. Captain runs /ship warp goto <beacon>
 *   2. 5-second charge countdown (sounds + titles to all passengers)
 *   3. Instant block teleport to destination (one sync tick)
 *   4. Arrival effects + fuel deducted
 *
 * The instant teleport loads destination chunks synchronously before moving blocks.
 * For a 3000-block ship this is ~6000 block operations — acceptable as a rare event.
 */
public class WarpManager {

    private final Horizon plugin;
    private final WarpDAO dao;

    /** All known warp beacons, keyed by name (lowercase) for fast lookup. */
    private final Map<String, WarpBeacon> beaconsByName = new HashMap<>();
    private final Map<UUID,   WarpBeacon> beaconsById   = new HashMap<>();

    /** Ships currently in charge countdown, keyed by ship UUID. */
    private final Map<UUID, BukkitTask> chargingShips = new HashMap<>();

    private static final int CHARGE_SECONDS = 5;

    public WarpManager(Horizon plugin) {
        this.plugin = plugin;
        this.dao    = new WarpDAO(plugin);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        for (WarpBeacon b : dao.loadAll()) {
            register(b, false);
        }
        plugin.getLogger().info("Loaded " + beaconsByName.size() + " warp beacon(s).");
    }

    // -----------------------------------------------------------------------
    // Beacon management
    // -----------------------------------------------------------------------

    public void register(WarpBeacon beacon, boolean persist) {
        beaconsByName.put(beacon.getName().toLowerCase(), beacon);
        beaconsById.put(beacon.getBeaconId(), beacon);
        if (persist) dao.save(beacon);
    }

    public boolean deleteBeacon(String name) {
        WarpBeacon b = beaconsByName.remove(name.toLowerCase());
        if (b == null) return false;
        beaconsById.remove(b.getBeaconId());
        dao.delete(b.getBeaconId());
        return true;
    }

    public WarpBeacon getBeacon(String name) {
        return beaconsByName.get(name.toLowerCase());
    }

    public Collection<WarpBeacon> getAllBeacons() {
        return Collections.unmodifiableCollection(beaconsByName.values());
    }

    public boolean isCharging(Ship ship) {
        return chargingShips.containsKey(ship.getShipId());
    }

    // -----------------------------------------------------------------------
    // Warp sequence
    // -----------------------------------------------------------------------

    /**
     * Begin the warp sequence to the named beacon.
     * Called from the /ship warp goto command.
     */
    public void initiateWarp(Ship ship, WarpBeacon target, Player captain) {
        if (isCharging(ship)) {
            captain.sendMessage("§c[Warp] Drive is already charging.");
            return;
        }
        if (!ship.isReady()) {
            captain.sendMessage("§c[Warp] Ship structure not scanned — use /ship scan first.");
            return;
        }
        if (ship.isProcessing()) {
            captain.sendMessage("§c[Warp] Ship is currently manoeuvring.");
            return;
        }

        double distance = target.distanceTo(ship.getCoreLocation());
        int    fuelCost = plugin.getFuelManager().calculateWarpCost(distance);

        if (!plugin.getFuelManager().hasEnoughFuel(ship, fuelCost)) {
            captain.sendMessage("§c[Warp] Insufficient fuel — need §f" + fuelCost
                    + " §cunits, have §f" + ship.getFuelLevel() + "§c.");
            return;
        }

        broadcastToShip(ship, "§b[Navigation] Plotting course to §f" + target.getName()
                + " §b— distance §f" + String.format("%.0f", distance)
                + " §bblocks — fuel cost §f" + fuelCost + "§b.");

        broadcastToShip(ship, "§e[Warp Drive] Charging — all hands brace for jump.");

        ship.setWarpStatus(WarpStatus.CHARGING);

        int[] countdown = {CHARGE_SECONDS};
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (countdown[0] > 0) {
                    // Show title to every passenger
                    for (UUID uuid : ship.getPassengers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendTitle(
                                    "§b§lWARP DRIVE",
                                    "§eJumping in §f" + countdown[0] + "§e...",
                                    5, 25, 5
                            );
                            p.playSound(p.getLocation(),
                                    Sound.BLOCK_BEACON_POWER_SELECT, 1.0f,
                                    0.5f + (float)(CHARGE_SECONDS - countdown[0]) * 0.15f);
                        }
                    }
                    countdown[0]--;
                } else {
                    cancel();
                    chargingShips.remove(ship.getShipId());
                    ship.setWarpStatus(WarpStatus.JUMPING);
                    executeJump(ship, target, fuelCost, distance);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        chargingShips.put(ship.getShipId(), task);
    }

    /** Abort a charging sequence (e.g., if captain cancels). */
    public void abortWarp(Ship ship) {
        BukkitTask task = chargingShips.remove(ship.getShipId());
        if (task != null) {
            task.cancel();
            ship.setWarpStatus(WarpStatus.IDLE);
            broadcastToShip(ship, "§c[Warp Drive] Jump aborted.");
        }
    }

    // -----------------------------------------------------------------------
    // Jump execution — instant block teleport
    // -----------------------------------------------------------------------

    private void executeJump(Ship ship, WarpBeacon target, int fuelCost, double distance) {
        try {
            Location src  = ship.getCoreLocation();
            Location dest = target.getLocation();
            World    world = src.getWorld();

            int dx = dest.getBlockX() - src.getBlockX();
            int dy = dest.getBlockY() - src.getBlockY();
            int dz = dest.getBlockZ() - src.getBlockZ();

            // Pre-load all destination chunks synchronously before touching blocks
            preloadDestinationChunks(world, ship, dx, dy, dz);

            // Execute block teleport
            plugin.getMovementEngine().teleportShipNow(ship, dx, dy, dz);

            // Consume fuel
            plugin.getFuelManager().consumeFuel(ship, fuelCost);

            ship.setWarpStatus(WarpStatus.IDLE);

            // Arrival effects
            for (UUID uuid : ship.getPassengers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                p.sendTitle("§a§lARRIVED", "§7" + target.getName(), 5, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.2f);
            }

            broadcastToShip(ship, "§a[Navigation] Arrived at §b" + target.getName()
                    + "§a. Fuel remaining: §f" + ship.getFuelLevel() + "§a.");

            if (!target.getDescription().isBlank()) {
                broadcastToShip(ship, "§8[Beacon] §7" + target.getDescription());
            }

            // Award warp XP to all passengers and check mission completion
            for (UUID uuid : ship.getPassengers()) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null) continue;
                plugin.getRankManager().awardWarpXp(p, distance);
                plugin.getStoryManager().progressObjective(p, StoryObjectiveType.WARP_JUMP);
            }
            plugin.getMissionManager().checkArrival(ship, target);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Warp jump failed for ship " + ship.getName(), e);
            ship.setWarpStatus(WarpStatus.IDLE);
            broadcastToShip(ship, "§c[Warp Drive] Jump failed — emergency reversion.");
        }
    }

    /**
     * Force-load all chunks that the ship's bounding box will occupy at the destination.
     * Runs synchronously — acceptable for a rare warp event.
     */
    private void preloadDestinationChunks(World world, Ship ship, int dx, int dy, int dz) {
        ShipStructure structure = ship.getStructure();
        if (structure == null) return;

        Location core = ship.getCoreLocation();
        int cx = core.getBlockX(), cy = core.getBlockY(), cz = core.getBlockZ();

        Set<Long> chunks = new HashSet<>();
        for (RelativeBlock rb : structure.getBlocks()) {
            int nx = rb.newX(cx, dx) >> 4;
            int nz = rb.newZ(cz, dz) >> 4;
            chunks.add(ShipScanner.packKey(nx, 0, nz));
        }

        for (long key : chunks) {
            // Decode chunk coords — we packed with ShipScanner.packKey so reverse it
            // Simpler: just load by iterating bounding box corners
        }

        // Simpler approach: load chunks covering the destination bounding box
        int minCX = (cx + structure.getMinX() + dx) >> 4;
        int maxCX = (cx + structure.getMaxX() + dx) >> 4;
        int minCZ = (cz + structure.getMinZ() + dz) >> 4;
        int maxCZ = (cz + structure.getMaxZ() + dz) >> 4;

        for (int chunkX = minCX; chunkX <= maxCX; chunkX++) {
            for (int chunkZ = minCZ; chunkZ <= maxCZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    world.loadChunk(chunkX, chunkZ);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void broadcastToShip(Ship ship, String message) {
        for (UUID uuid : ship.getPassengers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }
}