package com.usermc.horizon.station;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.StationDAO;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.ShipScanner;
import org.bukkit.Location;

import java.util.*;

public class StationManager {

    private final Horizon    plugin;
    private final StationDAO dao;

    private final Map<Long, ShipStation>        locationIndex = new HashMap<>();
    private final Map<UUID, List<ShipStation>>  shipIndex     = new HashMap<>();

    public StationManager(Horizon plugin) {
        this.plugin = plugin;
        this.dao    = new StationDAO(plugin);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        for (ShipStation s : dao.loadAll()) {
            locationIndex.put(packLoc(s.getLocation()), s);
            shipIndex.computeIfAbsent(s.getShipId(), k -> new ArrayList<>()).add(s);
        }
        plugin.getLogger().info("Loaded " + locationIndex.size() + " station(s).");
    }

    public void saveAll() {
        for (ShipStation s : locationIndex.values()) {
            if (s.isDirty()) dao.save(s);
        }
    }

    // -----------------------------------------------------------------------
    // Register / Unregister
    // -----------------------------------------------------------------------

    public void register(ShipStation station) {
        locationIndex.put(packLoc(station.getLocation()), station);
        shipIndex.computeIfAbsent(station.getShipId(), k -> new ArrayList<>()).add(station);
        dao.save(station);
    }

    public void unregister(ShipStation station) {
        locationIndex.remove(packLoc(station.getLocation()));
        List<ShipStation> list = shipIndex.get(station.getShipId());
        if (list != null) list.remove(station);
        dao.delete(station.getStationId());
    }

    // -----------------------------------------------------------------------
    // Translation — called after every ship move
    // -----------------------------------------------------------------------

    public void updateForShip(Ship ship, int dx, int dy, int dz) {
        for (ShipStation s : getForShip(ship.getShipId())) {
            locationIndex.remove(packLoc(s.getLocation()));
            s.setLocation(s.getLocation().add(dx, dy, dz));
            locationIndex.put(packLoc(s.getLocation()), s);
        }
    }

    // -----------------------------------------------------------------------
    // Rotation — called after every ship 90° rotate
    // -----------------------------------------------------------------------

    /**
     * Rotates all station positions 90° around the ship's core.
     *
     * CW formula:  new_x = core_x + (-old_dz),  new_z = core_z + (old_dx)
     * CCW formula: new_x = core_x + (old_dz),   new_z = core_z + (-old_dx)
     */
    public void rotateForShip(Ship ship, boolean clockwise) {
        Location core = ship.getCoreLocation();
        int cx = core.getBlockX();
        int cz = core.getBlockZ();

        for (ShipStation s : getForShip(ship.getShipId())) {
            locationIndex.remove(packLoc(s.getLocation()));

            int dx = s.getLocation().getBlockX() - cx;
            int dz = s.getLocation().getBlockZ() - cz;

            int newDx = clockwise ? -dz :  dz;
            int newDz = clockwise ?  dx : -dx;

            Location newLoc = new Location(
                    s.getLocation().getWorld(),
                    cx + newDx,
                    s.getLocation().getBlockY(),
                    cz + newDz
            );
            s.setLocation(newLoc);
            locationIndex.put(packLoc(newLoc), s);
        }
    }

    // -----------------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------------

    public ShipStation getAtLocation(Location loc) {
        return locationIndex.get(packLoc(loc));
    }

    public List<ShipStation> getForShip(UUID shipId) {
        return shipIndex.getOrDefault(shipId, Collections.emptyList());
    }

    public ShipStation getOfType(UUID shipId, StationType type) {
        return getForShip(shipId).stream()
                .filter(s -> s.getType() == type)
                .findFirst().orElse(null);
    }

    private long packLoc(Location loc) {
        return ShipScanner.packKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}