package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.faction.Faction;
import com.usermc.horizon.faction.FactionMember;
import com.usermc.horizon.faction.FactionRelation;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class FactionDAO {

    private final Horizon plugin;

    public FactionDAO(Horizon plugin) { this.plugin = plugin; }

    // -----------------------------------------------------------------------
    // Factions
    // -----------------------------------------------------------------------

    public void saveFaction(Faction faction) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveFactionInternal(faction));
    }

    public void saveFactionSync(Faction faction) { saveFactionInternal(faction); }

    private void saveFactionInternal(Faction faction) {
        String sql = """
            INSERT INTO horizon_factions
                (faction_id, name, description, leader_uuid, bank_balance, new_member_rank_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name                = VALUES(name),
                description         = VALUES(description),
                leader_uuid         = VALUES(leader_uuid),
                bank_balance        = VALUES(bank_balance),
                new_member_rank_id  = VALUES(new_member_rank_id)
        """;
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, faction.getFactionId().toString());
            ps.setString(2, faction.getName());
            ps.setString(3, faction.getDescription());
            ps.setString(4, faction.getLeaderUUID().toString());
            ps.setLong  (5, faction.getBankBalance());
            UUID newMemberRank = faction.getNewMemberRankId();
            ps.setString(6, newMemberRank != null ? newMemberRank.toString() : null);
            ps.executeUpdate();
            faction.clearDirty();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save faction " + faction.getName(), e);
        }
    }

    public void deleteFaction(UUID factionId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM horizon_factions WHERE faction_id = ?")) {
                ps.setString(1, factionId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete faction " + factionId, e);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Members — now stores rank_id (UUID) instead of a hardcoded rank name
    // -----------------------------------------------------------------------

    public void saveMember(FactionMember member) {
        String sql = """
            INSERT INTO horizon_faction_members
                (player_uuid, faction_id, player_name, rank_id, joined_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                faction_id  = VALUES(faction_id),
                player_name = VALUES(player_name),
                rank_id     = VALUES(rank_id)
        """;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, member.getPlayerUUID().toString());
                ps.setString(2, member.getFactionId().toString());
                ps.setString(3, member.getPlayerName());
                ps.setString(4, member.getRankId().toString());
                ps.setLong  (5, member.getJoinedAt());
                ps.executeUpdate();
                member.clearDirty();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save member " + member.getPlayerName(), e);
            }
        });
    }

    public void deleteMember(UUID playerUUID) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM horizon_faction_members WHERE player_uuid = ?")) {
                ps.setString(1, playerUUID.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete member " + playerUUID, e);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Relations
    // -----------------------------------------------------------------------

    public void saveRelation(UUID factionA, UUID factionB, FactionRelation relation) {
        if (relation == FactionRelation.NEUTRAL) { deleteRelation(factionA, factionB); return; }
        String sql = """
            INSERT INTO horizon_faction_relations (faction_a_id, faction_b_id, relation)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE relation = VALUES(relation)
        """;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, factionA.toString());
                ps.setString(2, factionB.toString());
                ps.setString(3, relation.name());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save faction relation", e);
            }
        });
    }

    private void deleteRelation(UUID factionA, UUID factionB) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement("""
                     DELETE FROM horizon_faction_relations
                     WHERE (faction_a_id = ? AND faction_b_id = ?)
                        OR (faction_a_id = ? AND faction_b_id = ?)
                 """)) {
                ps.setString(1, factionA.toString()); ps.setString(2, factionB.toString());
                ps.setString(3, factionB.toString()); ps.setString(4, factionA.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete faction relation", e);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Load all — synchronous, called on startup
    // Note: ranks are loaded separately via FactionRankDAO and stitched
    // together by FactionManager, since rank loading needs no faction
    // cross-reference beyond factionId.
    // -----------------------------------------------------------------------

    /** Raw faction row data, used by FactionManager before ranks are attached. */
    public record FactionRow(UUID factionId, String name, String description, UUID leaderUUID,
                             long bankBalance, long createdAt, UUID newMemberRankId) {}

    public List<FactionRow> loadFactionRows() {
        List<FactionRow> rows = new ArrayList<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM horizon_factions")) {
            while (rs.next()) {
                UUID   id     = UUID.fromString(rs.getString("faction_id"));
                String name   = rs.getString("name");
                String desc   = rs.getString("description");
                UUID   leader = UUID.fromString(rs.getString("leader_uuid"));
                long   bank   = rs.getLong("bank_balance");
                long   created= rs.getTimestamp("created_at").getTime();
                String nmrRaw = rs.getString("new_member_rank_id");
                UUID   nmr    = nmrRaw != null ? UUID.fromString(nmrRaw) : null;
                rows.add(new FactionRow(id, name, desc, leader, bank, created, nmr));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load faction rows", e);
        }
        return rows;
    }

    /** Raw member row data, keyed by factionId for easy stitching. */
    public record MemberRow(UUID playerUUID, UUID factionId, String playerName, UUID rankId, long joinedAt) {}

    public List<MemberRow> loadMemberRows() {
        List<MemberRow> rows = new ArrayList<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM horizon_faction_members")) {
            while (rs.next()) {
                UUID   playerUUID = UUID.fromString(rs.getString("player_uuid"));
                UUID   factionId  = UUID.fromString(rs.getString("faction_id"));
                String playerName = rs.getString("player_name");
                UUID   rankId     = UUID.fromString(rs.getString("rank_id"));
                long   joinedAt   = rs.getLong("joined_at");
                rows.add(new MemberRow(playerUUID, factionId, playerName, rankId, joinedAt));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load faction members", e);
        }
        return rows;
    }

    public List<Object[]> loadRelationRows() {
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM horizon_faction_relations")) {
            while (rs.next()) {
                UUID idA = UUID.fromString(rs.getString("faction_a_id"));
                UUID idB = UUID.fromString(rs.getString("faction_b_id"));
                FactionRelation rel = FactionRelation.valueOf(rs.getString("relation"));
                rows.add(new Object[]{idA, idB, rel});
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load faction relations", e);
        }
        return rows;
    }
}