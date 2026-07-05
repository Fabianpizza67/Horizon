package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.navigation.ChipRack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class ChipRackDAO {

    private final Horizon plugin;

    public ChipRackDAO(Horizon plugin) { this.plugin = plugin; }

    public void save(ChipRack rack) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveSync(rack));
    }

    public void saveSync(ChipRack rack) {
        String sql = """
            INSERT INTO horizon_chip_racks (rack_id, ship_id, world_name, x, y, z)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                ship_id    = VALUES(ship_id),
                world_name = VALUES(world_name),
                x = VALUES(x), y = VALUES(y), z = VALUES(z)
        """;
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            Location loc = rack.getLocation();
            ps.setString(1, rack.getRackId().toString());
            ps.setString(2, rack.getShipId().toString());
            ps.setString(3, loc.getWorld().getName());
            ps.setInt   (4, loc.getBlockX());
            ps.setInt   (5, loc.getBlockY());
            ps.setInt   (6, loc.getBlockZ());
            ps.executeUpdate();
            rack.clearDirty();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save chip rack", e);
        }
    }

    public void delete(UUID rackId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM horizon_chip_racks WHERE rack_id = ?")) {
                ps.setString(1, rackId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete chip rack", e);
            }
        });
    }

    public List<ChipRack> loadAll() {
        List<ChipRack> racks = new ArrayList<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM horizon_chip_racks")) {
            while (rs.next()) {
                UUID rackId = UUID.fromString(rs.getString("rack_id"));
                UUID shipId = UUID.fromString(rs.getString("ship_id"));
                String worldName = rs.getString("world_name");
                int x = rs.getInt("x"), y = rs.getInt("y"), z = rs.getInt("z");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Chip rack world '" + worldName + "' not loaded, skipping.");
                    continue;
                }
                ChipRack rack = new ChipRack(rackId, shipId, new Location(world, x, y, z));
                rack.clearDirty();
                racks.add(rack);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load chip racks", e);
        }
        return racks;
    }
}