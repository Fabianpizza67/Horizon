package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.station.ShipStation;
import com.usermc.horizon.station.StationType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class StationDAO {

    private final Horizon plugin;

    public StationDAO(Horizon plugin) {
        this.plugin = plugin;
    }

    public void save(ShipStation station) {
        String sql = """
            INSERT INTO horizon_stations
                (station_id, ship_id, type, world_name, x, y, z)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                ship_id    = VALUES(ship_id),
                type       = VALUES(type),
                world_name = VALUES(world_name),
                x = VALUES(x), y = VALUES(y), z = VALUES(z)
        """;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                Location loc = station.getLocation();
                ps.setString(1, station.getStationId().toString());
                ps.setString(2, station.getShipId().toString());
                ps.setString(3, station.getType().name());
                ps.setString(4, loc.getWorld().getName());
                ps.setInt   (5, loc.getBlockX());
                ps.setInt   (6, loc.getBlockY());
                ps.setInt   (7, loc.getBlockZ());
                ps.executeUpdate();
                station.clearDirty();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save station", e);
            }
        });
    }

    public void delete(UUID stationId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM horizon_stations WHERE station_id = ?")) {
                ps.setString(1, stationId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete station", e);
            }
        });
    }

    public List<ShipStation> loadAll() {
        List<ShipStation> list = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery("SELECT * FROM horizon_stations")) {
            while (rs.next()) {
                try {
                    UUID        id      = UUID.fromString(rs.getString("station_id"));
                    UUID        shipId  = UUID.fromString(rs.getString("ship_id"));
                    StationType type    = StationType.valueOf(rs.getString("type"));
                    String      world   = rs.getString("world_name");
                    int x = rs.getInt("x"), y = rs.getInt("y"), z = rs.getInt("z");
                    World w = Bukkit.getWorld(world);
                    if (w == null) { plugin.getLogger().warning("Station world '" + world + "' not found."); continue; }
                    ShipStation s = new ShipStation(id, shipId, type, new Location(w, x, y, z));
                    s.clearDirty();
                    list.add(s);
                } catch (Exception e) {
                    plugin.getLogger().warning("Malformed station row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load stations", e);
        }
        return list;
    }
}