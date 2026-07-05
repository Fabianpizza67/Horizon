package com.usermc.horizon.navigation;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.ChipRackDAO;
import com.usermc.horizon.database.dao.ChipStorageDAO;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.ShipScanner;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Central manager for all standalone Chip Rack blocks.
 *
 * Mirrors the same responsibilities StationManager has for station blocks
 * (location index, ship-relative position updates on move/rotate) but is
 * fully self-contained since this session cannot safely edit the existing
 * Station system files.
 *
 * Wiring required (see bottom of file comment):
 *   - ShipMovementEngine: call updateForShip(ship, dx, dy, dz) after translate,
 *     and rotateForShip(ship, cw) after rotate — same pattern as StationManager.
 */
public class ChipRackManager {

    private final Horizon        plugin;
    private final ChipRackDAO    dao;
    private final ChipStorageDAO storageDao;

    private final Map<Long, ChipRack>       locationIndex = new HashMap<>();
    private final Map<UUID, List<ChipRack>> shipIndex     = new HashMap<>();

    /** In-memory cache of rack contents, keyed by rackId → slot → item. Loaded lazily on GUI open. */
    private final Map<UUID, Map<Integer, ItemStack>> contentsCache = new HashMap<>();

    private BukkitTask autoSaveTask;

    public ChipRackManager(Horizon plugin) {
        this.plugin     = plugin;
        this.dao        = new ChipRackDAO(plugin);
        this.storageDao = new ChipStorageDAO(plugin);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        for (ChipRack rack : dao.loadAll()) {
            locationIndex.put(packLoc(rack.getLocation()), rack);
            shipIndex.computeIfAbsent(rack.getShipId(), k -> new ArrayList<>()).add(rack);
        }
        plugin.getLogger().info("Loaded " + locationIndex.size() + " chip rack(s).");
        startAutoSave();
    }

    private void startAutoSave() {
        long interval = plugin.getHorizonConfig().getAutoSaveInterval() * 20L;
        autoSaveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (ChipRack rack : locationIndex.values()) {
                if (rack.isDirty()) dao.save(rack);
            }
        }, interval, interval);
    }

    public void saveAll() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        flushDirty();
    }

    public void flushDirty() {
        for (ChipRack rack : locationIndex.values()) {
            if (rack.isDirty()) dao.saveSync(rack);
        }
    }

    // -----------------------------------------------------------------------
    // Register / Unregister
    // -----------------------------------------------------------------------

    public void register(ChipRack rack) {
        locationIndex.put(packLoc(rack.getLocation()), rack);
        shipIndex.computeIfAbsent(rack.getShipId(), k -> new ArrayList<>()).add(rack);
        dao.save(rack);
    }

    public void unregister(ChipRack rack) {
        locationIndex.remove(packLoc(rack.getLocation()));
        List<ChipRack> list = shipIndex.get(rack.getShipId());
        if (list != null) list.remove(rack);
        dao.delete(rack.getRackId());
        storageDao.deleteContainer(rack.getRackId());
        contentsCache.remove(rack.getRackId());
    }

    // -----------------------------------------------------------------------
    // Position updates — call from ShipMovementEngine
    // -----------------------------------------------------------------------

    public void updateForShip(Ship ship, int dx, int dy, int dz) {
        for (ChipRack rack : getForShip(ship.getShipId())) {
            locationIndex.remove(packLoc(rack.getLocation()));
            rack.setLocation(rack.getLocation().add(dx, dy, dz));
            locationIndex.put(packLoc(rack.getLocation()), rack);
        }
    }

    public void rotateForShip(Ship ship, boolean clockwise) {
        Location core = ship.getCoreLocation();
        int cx = core.getBlockX(), cz = core.getBlockZ();

        for (ChipRack rack : getForShip(ship.getShipId())) {
            locationIndex.remove(packLoc(rack.getLocation()));
            int dx = rack.getLocation().getBlockX() - cx;
            int dz = rack.getLocation().getBlockZ() - cz;
            int newDx = clockwise ? -dz :  dz;
            int newDz = clockwise ?  dx : -dx;
            Location newLoc = new Location(rack.getLocation().getWorld(),
                    cx + newDx, rack.getLocation().getBlockY(), cz + newDz);
            rack.setLocation(newLoc);
            locationIndex.put(packLoc(newLoc), rack);
        }
    }

    // -----------------------------------------------------------------------
    // Contents — lazy-loaded cache backed by ChipStorageDAO
    // -----------------------------------------------------------------------

    public Map<Integer, ItemStack> getContents(UUID rackId) {
        return contentsCache.computeIfAbsent(rackId, storageDao::loadContainer);
    }

    public void setSlot(UUID rackId, int slot, ItemStack item) {
        Map<Integer, ItemStack> contents = getContents(rackId);
        if (item == null) contents.remove(slot);
        else contents.put(slot, item);
        storageDao.saveSlot(rackId, slot, item);
    }

    // -----------------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------------

    public ChipRack getAtLocation(Location loc) { return locationIndex.get(packLoc(loc)); }

    public List<ChipRack> getForShip(UUID shipId) {
        return shipIndex.getOrDefault(shipId, Collections.emptyList());
    }

    private long packLoc(Location loc) {
        return ShipScanner.packKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}