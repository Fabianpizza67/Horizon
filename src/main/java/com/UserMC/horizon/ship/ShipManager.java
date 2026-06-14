package com.usermc.horizon.ship;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.ShipDAO;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Owns all in-memory Ship objects.
 * Provides lookup by ID, owner, and world location.
 * Runs a periodic background save for dirty ships.
 */
public class ShipManager {

    private final Horizon plugin;
    private final ShipDAO dao;
    private final ShipScanner scanner;

    /** All loaded ships, keyed by their UUID. */
    private final Map<UUID, Ship> ships = new HashMap<>();

    /** Fast lookup: Ship Core block location key → Ship */
    private final Map<Long, Ship> coreIndex = new HashMap<>();

    private BukkitTask autoSaveTask;

    public ShipManager(Horizon plugin) {
        this.plugin  = plugin;
        this.dao     = new ShipDAO(plugin);
        this.scanner = new ShipScanner(plugin);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        List<Ship> loaded = dao.loadAll();
        for (Ship s : loaded) {
            ships.put(s.getShipId(), s);
            indexCore(s);
        }
        plugin.getLogger().info("Loaded " + loaded.size() + " ship(s) from database.");
        startAutoSave();
    }

    /** Called from onDisable(). Stops the auto-save loop and does a final SYNCHRONOUS flush. */
    public void saveAll() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        flushDirty();
    }

    /**
     * Synchronously persist every dirty ship (including structure_data).
     * Safe to call mid-session (e.g. an admin /ship save command) since it
     * doesn't touch the auto-save task.
     */
    public void flushDirty() {
        int saved = 0;
        for (Ship s : ships.values()) {
            if (s.isDirty()) {
                dao.saveSync(s);
                saved++;
            }
        }
        if (saved > 0)
            plugin.getLogger().info("Flushed " + saved + " dirty ship(s) to database.");
    }

    /**
     * Lightweight, async, hot-path persistence — call after every movement,
     * rotation, fuel change, or status change. Does NOT touch structure_data,
     * so it's cheap enough to call on every movement tick without re-serializing
     * potentially 100KB+ of block data each time.
     */
    public void persistState(Ship ship) {
        dao.updateState(ship);
    }

    private void startAutoSave() {
        long interval = plugin.getHorizonConfig().getAutoSaveInterval() * 20L;
        autoSaveTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, () -> {
                    for (Ship s : ships.values()) {
                        if (s.isDirty()) dao.save(s);
                    }
                }, interval, interval);
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Register a new ship after a successful scan.
     * Persists immediately.
     */
    public void register(Ship ship) {
        ships.put(ship.getShipId(), ship);
        indexCore(ship);
        dao.save(ship);
    }

    /**
     * Unregister and delete a ship permanently.
     */
    public void delete(Ship ship) {
        ships.remove(ship.getShipId());
        deindexCore(ship);
        dao.delete(ship.getShipId());
    }

    // -----------------------------------------------------------------------
    // Core block index — used by listeners and collision detection
    // -----------------------------------------------------------------------

    public void indexCore(Ship ship) {
        Location c = ship.getCoreLocation();
        coreIndex.put(ShipScanner.packKey(c.getBlockX(), c.getBlockY(), c.getBlockZ()), ship);
    }

    public void deindexCore(Ship ship) {
        Location c = ship.getCoreLocation();
        coreIndex.remove(ShipScanner.packKey(c.getBlockX(), c.getBlockY(), c.getBlockZ()));
    }

    /** Called by the movement engine after moving a ship to re-index its new core. */
    public void updateCoreIndex(Ship ship, Location oldCore) {
        coreIndex.remove(ShipScanner.packKey(
                oldCore.getBlockX(), oldCore.getBlockY(), oldCore.getBlockZ()));
        indexCore(ship);
    }

    // -----------------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------------

    public Ship getById(UUID shipId) {
        return ships.get(shipId);
    }

    public Ship getByName(String name) {
        for (Ship s : ships.values()) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    /** Get the ship whose Core block is at this exact location. */
    public Ship getAtCoreLocation(Location loc) {
        return coreIndex.get(
                ShipScanner.packKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    /** Get all ships owned by a player. */
    public List<Ship> getByOwner(UUID ownerUUID) {
        List<Ship> result = new ArrayList<>();
        for (Ship s : ships.values()) {
            if (s.getOwnerUUID().equals(ownerUUID)) result.add(s);
        }
        return result;
    }

    /**
     * Find which ship a given world location falls inside.
     * Checks bounding boxes — O(n) over all ships.
     * Acceptable for typical server sizes (< 200 ships at once).
     */
    public Ship getShipAt(Location loc) {
        for (Ship s : ships.values()) {
            if (s.isWithinBounds(loc)) return s;
        }
        return null;
    }

    public Collection<Ship> getAllShips() {
        return Collections.unmodifiableCollection(ships.values());
    }

    public ShipDAO    getDao()     { return dao; }
    public ShipScanner getScanner(){ return scanner; }
}