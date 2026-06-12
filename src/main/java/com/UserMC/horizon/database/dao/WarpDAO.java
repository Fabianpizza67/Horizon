package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.warp.WarpBeacon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class WarpDAO {

    private final Horizon plugin;

    public WarpDAO(Horizon plugin) {
        this.plugin = plugin;
    }

    public void save(WarpBeacon beacon) {
        String sql = """
            INSERT INTO horizon_warp_beacons
                (beacon_id, name, world_name, x, y, z, description, admin_only)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name        = VALUES(name),
                description = VALUES(description),
                admin_only  = VALUES(admin_only)
        """;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                Location loc = beacon.getLocation();
                ps.setString (1, beacon.getBeaconId().toString());
                ps.setString (2, beacon.getName());
                ps.setString (3, loc.getWorld().getName());
                ps.setInt    (4, loc.getBlockX());
                ps.setInt    (5, loc.getBlockY());
                ps.setInt    (6, loc.getBlockZ());
                ps.setString (7, beacon.getDescription());
                ps.setBoolean(8, beacon.isAdminOnly());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save warp beacon", e);
            }
        });
    }

    public void delete(UUID beaconId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM horizon_warp_beacons WHERE beacon_id = ?")) {
                ps.setString(1, beaconId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete warp beacon", e);
            }
        });
    }

    /** Synchronous load — called on startup only. */
    public List<WarpBeacon> loadAll() {
        List<WarpBeacon> beacons = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery("SELECT * FROM horizon_warp_beacons")) {

            while (rs.next()) {
                try {
                    UUID   id        = UUID.fromString(rs.getString("beacon_id"));
                    String name      = rs.getString("name");
                    String worldName = rs.getString("world_name");
                    int    x = rs.getInt("x"), y = rs.getInt("y"), z = rs.getInt("z");
                    String desc      = rs.getString("description");
                    boolean adminOnly = rs.getBoolean("admin_only");

                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        plugin.getLogger().warning("Warp beacon '" + name
                                + "': world '" + worldName + "' not loaded, skipping.");
                        continue;
                    }
                    beacons.add(new WarpBeacon(id, name, new Location(world, x, y, z), desc, adminOnly));
                } catch (Exception e) {
                    plugin.getLogger().warning("Malformed warp beacon row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load warp beacons", e);
        }
        return beacons;
    }
}