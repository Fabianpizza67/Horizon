package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.faction.FactionRankDef;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class FactionRankDAO {

    private final Horizon plugin;

    public FactionRankDAO(Horizon plugin) { this.plugin = plugin; }

    public void save(FactionRankDef rank) {
        String sql = """
            INSERT INTO horizon_faction_ranks
                (rank_id, faction_id, name, permissions, hierarchy_position, is_leader_rank, is_default_rank)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name               = VALUES(name),
                permissions        = VALUES(permissions),
                hierarchy_position = VALUES(hierarchy_position)
        """;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString (1, rank.getRankId().toString());
                ps.setString (2, rank.getFactionId().toString());
                ps.setString (3, rank.getName());
                ps.setLong   (4, rank.getPermissionsBitmask());
                ps.setInt    (5, rank.getHierarchyPosition());
                ps.setBoolean(6, rank.isLeaderRank());
                ps.setBoolean(7, rank.isDefaultRank());
                ps.executeUpdate();
                rank.clearDirty();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save faction rank " + rank.getName(), e);
            }
        });
    }

    public void saveSync(FactionRankDef rank) {
        String sql = """
            INSERT INTO horizon_faction_ranks
                (rank_id, faction_id, name, permissions, hierarchy_position, is_leader_rank, is_default_rank)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name               = VALUES(name),
                permissions        = VALUES(permissions),
                hierarchy_position = VALUES(hierarchy_position)
        """;
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString (1, rank.getRankId().toString());
            ps.setString (2, rank.getFactionId().toString());
            ps.setString (3, rank.getName());
            ps.setLong   (4, rank.getPermissionsBitmask());
            ps.setInt    (5, rank.getHierarchyPosition());
            ps.setBoolean(6, rank.isLeaderRank());
            ps.setBoolean(7, rank.isDefaultRank());
            ps.executeUpdate();
            rank.clearDirty();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync-save faction rank " + rank.getName(), e);
        }
    }

    public void delete(UUID rankId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM horizon_faction_ranks WHERE rank_id = ?")) {
                ps.setString(1, rankId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete faction rank " + rankId, e);
            }
        });
    }

    /** Synchronous — load all ranks for all factions. Called once on startup. */
    public Map<UUID, List<FactionRankDef>> loadAllByFaction() {
        Map<UUID, List<FactionRankDef>> result = new HashMap<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM horizon_faction_ranks")) {
            while (rs.next()) {
                UUID    rankId    = UUID.fromString(rs.getString("rank_id"));
                UUID    factionId = UUID.fromString(rs.getString("faction_id"));
                String  name      = rs.getString("name");
                long    perms     = rs.getLong("permissions");
                int     hierarchy = rs.getInt("hierarchy_position");
                boolean isLeader  = rs.getBoolean("is_leader_rank");
                boolean isDefault = rs.getBoolean("is_default_rank");

                FactionRankDef rank = new FactionRankDef(rankId, factionId, name, perms,
                        hierarchy, isLeader, isDefault);
                rank.clearDirty();
                result.computeIfAbsent(factionId, k -> new ArrayList<>()).add(rank);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load faction ranks", e);
        }
        return result;
    }
}