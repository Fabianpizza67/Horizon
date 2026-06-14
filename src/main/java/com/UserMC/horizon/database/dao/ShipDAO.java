package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.ShipClass;
import com.usermc.horizon.ship.ShipStatus;
import com.usermc.horizon.ship.ShipStructure;
import com.usermc.horizon.warp.WarpStatus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class ShipDAO {

    private final Horizon plugin;

    public ShipDAO(Horizon plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Save / Upsert
    // -----------------------------------------------------------------------

    public void save(Ship ship) {
        String sql = """
            INSERT INTO horizon_ships
                (ship_id, name, owner_uuid, ship_class, world_name,
                 core_x, core_y, core_z, heading, status, warp_status,
                 fuel_level, structure_data)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name           = VALUES(name),
                ship_class     = VALUES(ship_class),
                world_name     = VALUES(world_name),
                core_x         = VALUES(core_x),
                core_y         = VALUES(core_y),
                core_z         = VALUES(core_z),
                heading        = VALUES(heading),
                status         = VALUES(status),
                warp_status    = VALUES(warp_status),
                fuel_level     = VALUES(fuel_level),
                structure_data = VALUES(structure_data)
        """;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                Location      core      = ship.getCoreLocation();
                ShipStructure structure = ship.getStructure();

                ps.setString(1,  ship.getShipId().toString());
                ps.setString(2,  ship.getName());
                ps.setString(3,  ship.getOwnerUUID().toString());
                ps.setString(4,  ship.getShipClass().name());
                ps.setString(5,  core.getWorld().getName());
                ps.setInt   (6,  core.getBlockX());
                ps.setInt   (7,  core.getBlockY());
                ps.setInt   (8,  core.getBlockZ());
                ps.setFloat (9,  ship.getHeading());
                ps.setString(10, ship.getStatus().name());
                ps.setString(11, ship.getWarpStatus().name());
                ps.setInt   (12, ship.getFuelLevel());
                ps.setString(13, structure != null ? structure.serialize() : null);

                ps.executeUpdate();
                ship.clearDirty();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save ship " + ship.getName(), e);
            }
        });
    }

    /**
     * Synchronous full save — INCLUDES structure_data.
     *
     * Used only when the calling thread MUST block until the write completes,
     * specifically during plugin shutdown (saveAll) where async tasks scheduled
     * via runTaskAsynchronously are not guaranteed to run before the connection
     * pool is closed.
     *
     * Safe to call from the main thread during onDisable() — a handful of
     * blocking UPDATEs at shutdown is negligible.
     */
    public void saveSync(Ship ship) {
        String sql = """
            INSERT INTO horizon_ships
                (ship_id, name, owner_uuid, ship_class, world_name,
                 core_x, core_y, core_z, heading, status, warp_status,
                 fuel_level, structure_data)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name           = VALUES(name),
                ship_class     = VALUES(ship_class),
                world_name     = VALUES(world_name),
                core_x         = VALUES(core_x),
                core_y         = VALUES(core_y),
                core_z         = VALUES(core_z),
                heading        = VALUES(heading),
                status         = VALUES(status),
                warp_status    = VALUES(warp_status),
                fuel_level     = VALUES(fuel_level),
                structure_data = VALUES(structure_data)
        """;

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            Location      core      = ship.getCoreLocation();
            ShipStructure structure = ship.getStructure();

            ps.setString(1,  ship.getShipId().toString());
            ps.setString(2,  ship.getName());
            ps.setString(3,  ship.getOwnerUUID().toString());
            ps.setString(4,  ship.getShipClass().name());
            ps.setString(5,  core.getWorld().getName());
            ps.setInt   (6,  core.getBlockX());
            ps.setInt   (7,  core.getBlockY());
            ps.setInt   (8,  core.getBlockZ());
            ps.setFloat (9,  ship.getHeading());
            ps.setString(10, ship.getStatus().name());
            ps.setString(11, ship.getWarpStatus().name());
            ps.setInt   (12, ship.getFuelLevel());
            ps.setString(13, structure != null ? structure.serialize() : null);

            ps.executeUpdate();
            ship.clearDirty();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync-save ship " + ship.getName(), e);
        }
    }

    /**
     * Lightweight async update — everything EXCEPT structure_data.
     *
     * Called after every movement/rotation/fuel/status change so the ship's
     * position in the database never drifts more than a fraction of a second
     * behind reality, without re-serializing the (potentially 100KB+) block
     * structure on every tick.
     *
     * structure_data is only rewritten on scan (via save/saveSync).
     */
    public void updateState(Ship ship) {
        String sql = """
            UPDATE horizon_ships SET
                world_name  = ?,
                core_x      = ?,
                core_y      = ?,
                core_z      = ?,
                heading     = ?,
                status      = ?,
                warp_status = ?,
                fuel_level  = ?
            WHERE ship_id = ?
        """;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                Location core = ship.getCoreLocation();

                ps.setString(1, core.getWorld().getName());
                ps.setInt   (2, core.getBlockX());
                ps.setInt   (3, core.getBlockY());
                ps.setInt   (4, core.getBlockZ());
                ps.setFloat (5, ship.getHeading());
                ps.setString(6, ship.getStatus().name());
                ps.setString(7, ship.getWarpStatus().name());
                ps.setInt   (8, ship.getFuelLevel());
                ps.setString(9, ship.getShipId().toString());

                ps.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update state for ship " + ship.getName(), e);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------

    public void delete(UUID shipId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM horizon_ships WHERE ship_id = ?")) {
                ps.setString(1, shipId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete ship " + shipId, e);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Load — synchronous, called on startup only
    // -----------------------------------------------------------------------

    public List<Ship> loadAll() {
        List<Ship> ships = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery("SELECT * FROM horizon_ships")) {
            while (rs.next()) {
                Ship s = fromResultSet(rs);
                if (s != null) ships.add(s);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load ships", e);
        }
        return ships;
    }

    public Ship loadById(UUID shipId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM horizon_ships WHERE ship_id = ?")) {
            ps.setString(1, shipId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return fromResultSet(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load ship " + shipId, e);
        }
        return null;
    }

    public List<Ship> loadByOwner(UUID ownerUUID) {
        List<Ship> ships = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM horizon_ships WHERE owner_uuid = ?")) {
            ps.setString(1, ownerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Ship s = fromResultSet(rs);
                    if (s != null) ships.add(s);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load ships for " + ownerUUID, e);
        }
        return ships;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Ship fromResultSet(ResultSet rs) throws SQLException {
        try {
            UUID      shipId    = UUID.fromString(rs.getString("ship_id"));
            String    name      = rs.getString("name");
            UUID      owner     = UUID.fromString(rs.getString("owner_uuid"));
            ShipClass sc        = ShipClass.valueOf(rs.getString("ship_class"));
            String    worldName = rs.getString("world_name");
            int       cx = rs.getInt("core_x"), cy = rs.getInt("core_y"), cz = rs.getInt("core_z");
            float     heading   = rs.getFloat("heading");
            ShipStatus  status  = ShipStatus.valueOf(rs.getString("status"));
            WarpStatus  warp    = safeWarpStatus(rs.getString("warp_status"));
            int       fuel      = rs.getInt("fuel_level");
            String    structJson= rs.getString("structure_data");

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Ship '" + name + "': world '" + worldName + "' not loaded.");
                return null;
            }

            Ship ship = new Ship(shipId, name, owner, sc, new Location(world, cx, cy, cz), heading);
            ship.setStatus(status);
            ship.setWarpStatus(warp);
            ship.setFuelLevel(fuel);

            if (structJson != null && !structJson.isBlank()) {
                try {
                    ship.setStructure(ShipStructure.deserialize(structJson));
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not deserialize structure for '" + name + "' — rescan needed.");
                    ship.markStructureDirty();
                }
            }

            ship.clearDirty();
            return ship;

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Malformed ship row: " + e.getMessage());
            return null;
        }
    }

    private WarpStatus safeWarpStatus(String s) {
        try { return WarpStatus.valueOf(s); }
        catch (Exception e) { return WarpStatus.IDLE; }
    }
}