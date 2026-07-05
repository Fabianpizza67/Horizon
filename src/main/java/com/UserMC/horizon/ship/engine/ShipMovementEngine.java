package com.usermc.horizon.ship.engine;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.RelativeBlock;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.ShipScanner;
import com.usermc.horizon.ship.ShipStructure;
import com.usermc.horizon.story.StoryObjectiveType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Processes all ship movement and rotation from a single Bukkit repeating task.
 *
 * Supports two request types:
 *   MOVE   — translate ship by (dx, dy, dz)
 *   ROTATE — rotate ship 90° CW or CCW in one tick
 *
 * Rotation formulas (verified):
 *   CW:  new_x = -old_dz,  new_z =  old_dx   (East→South, South→West, ...)
 *   CCW: new_x =  old_dz,  new_z = -old_dx   (East→North, North→West, ...)
 */
public class ShipMovementEngine {

    private final Horizon           plugin;
    private final CollisionDetector collision;
    private final ConcurrentLinkedQueue<MovementRequest> queue = new ConcurrentLinkedQueue<>();
    private BukkitTask engineTask;

    public ShipMovementEngine(Horizon plugin) {
        this.plugin    = plugin;
        this.collision = new CollisionDetector();
    }

    public void start() {
        long interval = plugin.getHorizonConfig().getMovementIntervalTicks();
        engineTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, interval, interval);
        plugin.getLogger().info("Movement engine started (interval: " + interval + " ticks).");
    }

    public void stop() {
        if (engineTask != null) engineTask.cancel();
        queue.clear();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void queueMovement(MovementRequest request) {
        queue.removeIf(r -> r.getShip().getShipId().equals(request.getShip().getShipId()));
        queue.add(request);
    }

    /** Instant warp teleport — skips collision and queue. Destination chunks must be pre-loaded. */
    public void teleportShipNow(Ship ship, int dx, int dy, int dz) {
        ship.setProcessing(true);
        try { translateBlocks(ship, dx, dy, dz); }
        finally { ship.setProcessing(false); }
    }

    // -----------------------------------------------------------------------
    // Engine tick
    // -----------------------------------------------------------------------

    private void tick() {
        if (queue.isEmpty()) return;
        Set<UUID> processed = new HashSet<>();
        Iterator<MovementRequest> it = queue.iterator();
        while (it.hasNext()) {
            MovementRequest req = it.next();
            UUID id = req.getShip().getShipId();
            if (processed.contains(id)) continue;
            it.remove();
            processed.add(id);
            dispatch(req);
        }
    }

    private void dispatch(MovementRequest req) {
        Ship ship = req.getShip();
        if (ship.isProcessing() || !ship.isReady()) return;
        if (plugin.getWarpManager().isCharging(ship)) return;

        ship.setProcessing(true);
        try {
            if (req.getType() == MovementRequest.Type.ROTATE) {
                rotateShip(ship, req.isClockwise());
            } else {
                executeMove(ship, req);
            }
        } finally {
            ship.setProcessing(false);
        }
    }

    // -----------------------------------------------------------------------
    // Translation
    // -----------------------------------------------------------------------

    private void executeMove(Ship ship, MovementRequest req) {
        int dx = req.dx(), dy = req.dy(), dz = req.dz();

        if ((dx != 0 || dz != 0) && ship.getSpeedMultiplier() < 1.0) {
            if (Math.random() > ship.getSpeedMultiplier()) return;
        }

        if (collision.hasCollision(ship, dx, dy, dz)) {
            notifyPassengers(ship, "§c[Horizon] Navigation blocked — collision ahead.");
            return;
        }

        translateBlocks(ship, dx, dy, dz);
    }

    private void translateBlocks(Ship ship, int dx, int dy, int dz) {
        ShipStructure structure = ship.getStructure();
        Location oldCore = ship.getCoreLocation();
        World world = oldCore.getWorld();
        if (world == null) return;

        int cx = oldCore.getBlockX(), cy = oldCore.getBlockY(), cz = oldCore.getBlockZ();
        List<RelativeBlock> blocks = structure.getBlocks();
        List<RelativeBlock> sorted = sortForTranslation(blocks, dx, dy, dz);

        Set<Long> newPositions = new HashSet<>(blocks.size() * 2);
        for (RelativeBlock rb : blocks)
            newPositions.add(ShipScanner.packKey(rb.newX(cx,dx), rb.newY(cy,dy), rb.newZ(cz,dz)));

        // Phase 1: place
        for (RelativeBlock rb : sorted)
            world.getBlockAt(rb.newX(cx,dx), rb.newY(cy,dy), rb.newZ(cz,dz))
                    .setBlockData(rb.blockData(), false);

        // Phase 2: teleport passengers
        teleportPassengers(ship, world, dx, dy, dz);

        // Phase 3: teleport crew NPCs
        plugin.getCrewManager().teleportCrewNpcs(ship, dx, dy, dz);

        // Phase 4: clear old positions not in new layout
        for (RelativeBlock rb : sorted) {
            long key = ShipScanner.packKey(rb.absoluteX(cx), rb.absoluteY(cy), rb.absoluteZ(cz));
            if (!newPositions.contains(key))
                world.getBlockAt(rb.absoluteX(cx), rb.absoluteY(cy), rb.absoluteZ(cz))
                        .setType(Material.AIR, false);
        }

        // Phase 5: update indices
        Location newCore = new Location(world, cx+dx, cy+dy, cz+dz,
                oldCore.getYaw(), oldCore.getPitch());
        plugin.getShipManager().updateCoreIndex(ship, oldCore);
        ship.setCoreLocation(newCore);
        plugin.getStationManager().updateForShip(ship, dx, dy, dz);
        plugin.getChipRackManager().updateForShip(ship, dx, dy, dz);
        plugin.getChipCreationManager().onShipMoved(ship, dx, dy, dz);

        // Lightweight async position/state sync — keeps the DB from drifting
        // behind reality between the heavier 30s structure auto-saves.
        plugin.getShipManager().persistState(ship);
    }

    // -----------------------------------------------------------------------
    // Rotation
    // -----------------------------------------------------------------------

    /**
     * Rotates the ship 90° clockwise or counter-clockwise around its core.
     *
     * Block position transform:
     *   CW:  (dx, dz) → (-dz,  dx)
     *   CCW: (dx, dz) → ( dz, -dx)
     *
     * Block facing data is also rotated for Directional and Rotatable blocks.
     * Players and stations are rotated around the core position.
     */
    private void rotateShip(Ship ship, boolean cw) {
        ShipStructure oldStructure = ship.getStructure();
        Location core = ship.getCoreLocation();
        World world = core.getWorld();
        if (world == null) return;

        int cx = core.getBlockX(), cz = core.getBlockZ();

        // Build rotated structure
        List<RelativeBlock> oldBlocks = oldStructure.getBlocks();
        List<RelativeBlock> newBlocks = new ArrayList<>(oldBlocks.size());

        for (RelativeBlock rb : oldBlocks) {
            int newDx = cw ? -rb.dz() :  rb.dz();
            int newDz = cw ?  rb.dx() : -rb.dx();
            BlockData rotatedData = rotateBlockData(rb.blockData(), cw);
            newBlocks.add(new RelativeBlock(newDx, rb.dy(), newDz, rotatedData, rb.tileEntity()));
        }

        // Collect old world positions
        Set<Long> oldPositions = new HashSet<>(oldBlocks.size() * 2);
        for (RelativeBlock rb : oldBlocks)
            oldPositions.add(ShipScanner.packKey(rb.absoluteX(cx), rb.absoluteY(core.getBlockY()), rb.absoluteZ(cz)));

        // Collect new world positions
        Set<Long> newPositions = new HashSet<>(newBlocks.size() * 2);
        for (RelativeBlock rb : newBlocks)
            newPositions.add(ShipScanner.packKey(cx + rb.dx(), core.getBlockY() + rb.dy(), cz + rb.dz()));

        // Place blocks at rotated positions
        for (RelativeBlock rb : newBlocks)
            world.getBlockAt(cx + rb.dx(), core.getBlockY() + rb.dy(), cz + rb.dz())
                    .setBlockData(rb.blockData(), false);

        // Rotate passengers around core
        for (UUID uuid : new HashSet<>(ship.getPassengers())) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) { ship.removePassenger(uuid); continue; }
            double pdx = p.getX() - cx;
            double pdz = p.getZ() - cz;
            double newPdx = cw ? -pdz :  pdz;
            double newPdz = cw ?  pdx : -pdx;
            float  newYaw = p.getYaw() + (cw ? 90f : -90f);
            p.teleport(new Location(world, cx + newPdx, p.getY(), cz + newPdz,
                    newYaw, p.getPitch()));
        }

        // Rotate crew NPCs
        plugin.getCrewManager().teleportCrewNpcsRotate(ship, cx, cz, cw);

        // Clear old positions not in new layout
        for (RelativeBlock rb : oldBlocks) {
            long key = ShipScanner.packKey(rb.absoluteX(cx), rb.absoluteY(core.getBlockY()), rb.absoluteZ(cz));
            if (!newPositions.contains(key))
                world.getBlockAt(rb.absoluteX(cx), rb.absoluteY(core.getBlockY()), rb.absoluteZ(cz))
                        .setType(Material.AIR, false);
        }

        // Update ship state
        ShipStructure rotatedStructure = new ShipStructure(newBlocks);
        ship.setStructure(rotatedStructure);
        ship.setHeading(ship.getHeading() + (cw ? 90f : -90f));

        // Rotate station locations
        plugin.getStationManager().rotateForShip(ship, cw);
        plugin.getChipRackManager().rotateForShip(ship, cw);
        plugin.getChipCreationManager().onShipRotated(ship);

        // Rotation changes the structure layout (relative offsets) AND heading,
        // so persist the FULL ship record (async — structure serialization
        // happens off the main thread inside save()).
        plugin.getShipManager().getDao().save(ship);

        for (java.util.UUID uuid : ship.getPassengers()) {
            org.bukkit.entity.Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) plugin.getStoryManager().progressObjective(p, StoryObjectiveType.ROTATE_SHIP);
        }
    }

    // -----------------------------------------------------------------------
    // Block data rotation helpers
    // -----------------------------------------------------------------------

    private BlockData rotateBlockData(BlockData data, boolean cw) {
        BlockData rotated = data.clone();
        if (rotated instanceof Directional d) {
            d.setFacing(rotateFace(d.getFacing(), cw));
        }
        if (rotated instanceof Rotatable r) {
            r.setRotation(rotateFace(r.getRotation(), cw));
        }
        return rotated;
    }

    private BlockFace rotateFace(BlockFace face, boolean cw) {
        return switch (face) {
            case NORTH -> cw ? BlockFace.EAST  : BlockFace.WEST;
            case EAST  -> cw ? BlockFace.SOUTH : BlockFace.NORTH;
            case SOUTH -> cw ? BlockFace.WEST  : BlockFace.EAST;
            case WEST  -> cw ? BlockFace.NORTH : BlockFace.SOUTH;
            default    -> face; // UP, DOWN, etc. unchanged
        };
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    private void teleportPassengers(Ship ship, World world, int dx, int dy, int dz) {
        for (UUID uuid : new HashSet<>(ship.getPassengers())) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) { ship.removePassenger(uuid); continue; }
            Location pl = p.getLocation();
            p.teleport(new Location(world,
                    pl.getX()+dx, pl.getY()+dy, pl.getZ()+dz,
                    pl.getYaw(), pl.getPitch()));
        }
    }

    private List<RelativeBlock> sortForTranslation(List<RelativeBlock> blocks, int dx, int dy, int dz) {
        List<RelativeBlock> sorted = new ArrayList<>(blocks);
        Comparator<RelativeBlock> comp;
        if      (dx > 0) comp = Comparator.comparingInt(RelativeBlock::dx).reversed();
        else if (dx < 0) comp = Comparator.comparingInt(RelativeBlock::dx);
        else if (dy > 0) comp = Comparator.comparingInt(RelativeBlock::dy).reversed();
        else if (dy < 0) comp = Comparator.comparingInt(RelativeBlock::dy);
        else if (dz > 0) comp = Comparator.comparingInt(RelativeBlock::dz).reversed();
        else             comp = Comparator.comparingInt(RelativeBlock::dz);
        sorted.sort(comp);
        return sorted;
    }

    private void notifyPassengers(Ship ship, String msg) {
        for (UUID uuid : ship.getPassengers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        }
    }
}