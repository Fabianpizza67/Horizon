package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.rank.CaptainProfile;
import com.usermc.horizon.rank.CaptainRank;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class RankDAO {

    private final Horizon plugin;

    public RankDAO(Horizon plugin) { this.plugin = plugin; }

    public void save(CaptainProfile profile) {
        String sql = """
            INSERT INTO horizon_captains
                (player_uuid, player_name, rank, experience, missions_completed, total_warp_distance)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name        = VALUES(player_name),
                rank               = VALUES(rank),
                experience         = VALUES(experience),
                missions_completed = VALUES(missions_completed),
                total_warp_distance= VALUES(total_warp_distance)
        """;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, profile.getPlayerUUID().toString());
                ps.setString(2, profile.getPlayerName());
                ps.setString(3, profile.getRank().name());
                ps.setLong  (4, profile.getExperience());
                ps.setInt   (5, profile.getMissionsCompleted());
                ps.setLong  (6, profile.getTotalWarpDistance());
                ps.executeUpdate();
                profile.clearDirty();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save captain profile", e);
            }
        });
    }

    /**
     * Synchronous save — used during plugin shutdown so the write completes
     * before the connection pool is closed (async tasks aren't guaranteed
     * to run in time during onDisable).
     */
    public void saveSync(CaptainProfile profile) {
        String sql = """
            INSERT INTO horizon_captains
                (player_uuid, player_name, rank, experience, missions_completed, total_warp_distance)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name        = VALUES(player_name),
                rank               = VALUES(rank),
                experience         = VALUES(experience),
                missions_completed = VALUES(missions_completed),
                total_warp_distance= VALUES(total_warp_distance)
        """;
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, profile.getPlayerUUID().toString());
            ps.setString(2, profile.getPlayerName());
            ps.setString(3, profile.getRank().name());
            ps.setLong  (4, profile.getExperience());
            ps.setInt   (5, profile.getMissionsCompleted());
            ps.setLong  (6, profile.getTotalWarpDistance());
            ps.executeUpdate();
            profile.clearDirty();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync-save captain profile", e);
        }
    }

    public CaptainProfile load(UUID uuid) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM horizon_captains WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return fromResultSet(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load captain profile", e);
        }
        return null;
    }

    public List<CaptainProfile> loadAll() {
        List<CaptainProfile> list = new ArrayList<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT * FROM horizon_captains ORDER BY experience DESC")) {
            while (rs.next()) {
                CaptainProfile p = fromResultSet(rs);
                if (p != null) list.add(p);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load all captain profiles", e);
        }
        return list;
    }

    private CaptainProfile fromResultSet(ResultSet rs) throws SQLException {
        try {
            UUID        uuid = UUID.fromString(rs.getString("player_uuid"));
            String      name = rs.getString("player_name");
            CaptainRank rank = CaptainRank.valueOf(rs.getString("rank"));
            long        xp   = rs.getLong("experience");
            int         mc   = rs.getInt("missions_completed");
            long        wd   = rs.getLong("total_warp_distance");
            CaptainProfile p = new CaptainProfile(uuid, name, rank, xp, mc, wd);
            p.clearDirty();
            return p;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Malformed captain row: " + e.getMessage());
            return null;
        }
    }
}