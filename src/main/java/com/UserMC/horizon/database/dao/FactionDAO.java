package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.faction.Faction;
import com.usermc.horizon.faction.FactionMember;
import com.usermc.horizon.faction.FactionRank;
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
        String sql = """
            INSERT INTO horizon_factions
                (faction_id, name, description, leader_uuid, bank_balance)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name         = VALUES(name),
                description  = VALUES(description),
                leader_uuid  = VALUES(leader_uuid),
                bank_balance = VALUES(bank_balance)
        """;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, faction.getFactionId().toString());
                ps.setString(2, faction.getName());
                ps.setString(3, faction.getDescription());
                ps.setString(4, faction.getLeaderUUID().toString());
                ps.setLong  (5, faction.getBankBalance());
                ps.executeUpdate();
                faction.clearDirty();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save faction " + faction.getName(), e);
            }
        });
    }

    public void saveFactionSync(Faction faction) {
        String sql = """
            INSERT INTO horizon_factions
                (faction_id, name, description, leader_uuid, bank_balance)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name         = VALUES(name),
                description  = VALUES(description),
                leader_uuid  = VALUES(leader_uuid),
                bank_balance = VALUES(bank_balance)
        """;
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, faction.getFactionId().toString());
            ps.setString(2, faction.getName());
            ps.setString(3, faction.getDescription());
            ps.setString(4, faction.getLeaderUUID().toString());
            ps.setLong  (5, faction.getBankBalance());
            ps.executeUpdate();
            faction.clearDirty();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync-save faction " + faction.getName(), e);
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
    // Members
    // -----------------------------------------------------------------------

    public void saveMember(FactionMember member) {
        String sql = """
            INSERT INTO horizon_faction_members
                (player_uuid, faction_id, player_name, rank, joined_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                faction_id  = VALUES(faction_id),
                player_name = VALUES(player_name),
                rank        = VALUES(rank)
        """;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, member.getPlayerUUID().toString());
                ps.setString(2, member.getFactionId().toString());
                ps.setString(3, member.getPlayerName());
                ps.setString(4, member.getRank().name());
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
        if (relation == FactionRelation.NEUTRAL) {
            deleteRelation(factionA, factionB);
            return;
        }
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
    // -----------------------------------------------------------------------

    public List<Faction> loadAll() {
        List<Faction> factions = new ArrayList<>();

        try (Connection c = plugin.getDatabaseManager().getConnection()) {

            // Load factions
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT * FROM horizon_factions")) {
                while (rs.next()) {
                    UUID   id     = UUID.fromString(rs.getString("faction_id"));
                    String name   = rs.getString("name");
                    String desc   = rs.getString("description");
                    UUID   leader = UUID.fromString(rs.getString("leader_uuid"));
                    long   bank   = rs.getLong("bank_balance");
                    long   created= rs.getTimestamp("created_at").getTime();
                    factions.add(new Faction(id, name, desc, leader, bank, created));
                }
            }

            // Load members into their factions
            Map<UUID, Faction> factionMap = new HashMap<>();
            factions.forEach(f -> factionMap.put(f.getFactionId(), f));

            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT * FROM horizon_faction_members")) {
                while (rs.next()) {
                    UUID   playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    UUID   factionId  = UUID.fromString(rs.getString("faction_id"));
                    String playerName = rs.getString("player_name");
                    FactionRank rank  = FactionRank.valueOf(rs.getString("rank"));
                    long joinedAt     = rs.getLong("joined_at");
                    Faction f = factionMap.get(factionId);
                    if (f != null) {
                        f.addMember(new FactionMember(playerUUID, factionId, rank, playerName, joinedAt));
                    }
                }
            }

            // Load relations
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT * FROM horizon_faction_relations")) {
                while (rs.next()) {
                    UUID           idA    = UUID.fromString(rs.getString("faction_a_id"));
                    UUID           idB    = UUID.fromString(rs.getString("faction_b_id"));
                    FactionRelation rel   = FactionRelation.valueOf(rs.getString("relation"));
                    Faction fA = factionMap.get(idA);
                    Faction fB = factionMap.get(idB);
                    if (fA != null) fA.setRelation(idB, rel);
                    if (fB != null) fB.setRelation(idA, rel);
                    // Clear dirty flags set by setRelation
                    if (fA != null) fA.clearDirty();
                    if (fB != null) fB.clearDirty();
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load factions", e);
        }

        return factions;
    }
}