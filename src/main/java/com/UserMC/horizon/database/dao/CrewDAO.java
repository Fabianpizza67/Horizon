package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.crew.CrewMember;
import com.usermc.horizon.crew.CrewRole;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class CrewDAO {

    private final Horizon plugin;

    public CrewDAO(Horizon plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Save / Upsert
    // -----------------------------------------------------------------------

    public void save(CrewMember crew) {
        String sql = """
            INSERT INTO horizon_crew
                (crew_id, ship_id, npc_id, name, species, role,
                 skill_level, morale, salary)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                ship_id     = VALUES(ship_id),
                npc_id      = VALUES(npc_id),
                name        = VALUES(name),
                species     = VALUES(species),
                role        = VALUES(role),
                skill_level = VALUES(skill_level),
                morale      = VALUES(morale),
                salary      = VALUES(salary)
        """;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, crew.getCrewId().toString());
                ps.setString(2, crew.getShipId().toString());
                ps.setInt   (3, crew.getNpcId());
                ps.setString(4, crew.getName());
                ps.setString(5, crew.getSpecies());
                ps.setString(6, crew.getRole().name());
                ps.setInt   (7, crew.getSkillLevel());
                ps.setDouble(8, crew.getMorale());
                ps.setInt   (9, crew.getSalary());
                ps.executeUpdate();
                crew.clearDirty();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save crew member " + crew.getName(), e);
            }
        });
    }

    /**
     * Synchronous save — used during plugin shutdown so the write completes
     * before the connection pool is closed (async tasks aren't guaranteed
     * to run in time during onDisable).
     */
    public void saveSync(CrewMember crew) {
        String sql = """
            INSERT INTO horizon_crew
                (crew_id, ship_id, npc_id, name, species, role,
                 skill_level, morale, salary)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                ship_id     = VALUES(ship_id),
                npc_id      = VALUES(npc_id),
                name        = VALUES(name),
                species     = VALUES(species),
                role        = VALUES(role),
                skill_level = VALUES(skill_level),
                morale      = VALUES(morale),
                salary      = VALUES(salary)
        """;
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, crew.getCrewId().toString());
            ps.setString(2, crew.getShipId().toString());
            ps.setInt   (3, crew.getNpcId());
            ps.setString(4, crew.getName());
            ps.setString(5, crew.getSpecies());
            ps.setString(6, crew.getRole().name());
            ps.setInt   (7, crew.getSkillLevel());
            ps.setDouble(8, crew.getMorale());
            ps.setInt   (9, crew.getSalary());
            ps.executeUpdate();
            crew.clearDirty();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync-save crew member " + crew.getName(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Delete
    // -----------------------------------------------------------------------

    public void delete(UUID crewId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM horizon_crew WHERE crew_id = ?")) {
                ps.setString(1, crewId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete crew member " + crewId, e);
            }
        });
    }

    /** Delete all crew members for a ship (called when ship is deleted). */
    public void deleteForShip(UUID shipId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM horizon_crew WHERE ship_id = ?")) {
                ps.setString(1, shipId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete crew for ship " + shipId, e);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    /** Load all crew members. Called synchronously on startup. */
    public List<CrewMember> loadAll() {
        List<CrewMember> list = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery("SELECT * FROM horizon_crew")) {
            while (rs.next()) {
                CrewMember cm = fromResultSet(rs);
                if (cm != null) list.add(cm);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load crew members", e);
        }
        return list;
    }

    /** Load all crew for a specific ship. Synchronous. */
    public List<CrewMember> loadForShip(UUID shipId) {
        List<CrewMember> list = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM horizon_crew WHERE ship_id = ?")) {
            ps.setString(1, shipId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CrewMember cm = fromResultSet(rs);
                    if (cm != null) list.add(cm);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load crew for ship " + shipId, e);
        }
        return list;
    }

    private CrewMember fromResultSet(ResultSet rs) throws SQLException {
        try {
            UUID     crewId = UUID.fromString(rs.getString("crew_id"));
            UUID     shipId = UUID.fromString(rs.getString("ship_id"));
            int      npcId  = rs.getInt("npc_id");
            String   name   = rs.getString("name");
            String   species= rs.getString("species");
            CrewRole role   = CrewRole.valueOf(rs.getString("role"));
            int      skill  = rs.getInt("skill_level");
            double   morale = rs.getDouble("morale");
            int      salary = rs.getInt("salary");
            CrewMember cm = new CrewMember(crewId, shipId, npcId, name, species, role, skill, morale, salary);
            cm.clearDirty();
            return cm;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Malformed crew row: " + e.getMessage());
            return null;
        }
    }
}