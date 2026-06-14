package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.mission.Mission;
import com.usermc.horizon.mission.MissionStatus;
import com.usermc.horizon.mission.MissionType;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class MissionDAO {

    private final Horizon plugin;

    public MissionDAO(Horizon plugin) { this.plugin = plugin; }

    public void save(Mission m) {
        String sql = """
            INSERT INTO horizon_missions
                (mission_id, type, title, description, target_beacon_id,
                 target_beacon_name, difficulty, reward_ec, reward_xp,
                 expires_at, status, accepted_by, accepted_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
                status      = VALUES(status),
                accepted_by = VALUES(accepted_by),
                accepted_at = VALUES(accepted_at)
        """;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1,  m.getMissionId().toString());
                ps.setString(2,  m.getType().name());
                ps.setString(3,  m.getTitle());
                ps.setString(4,  m.getDescription());
                ps.setString(5,  m.getTargetBeaconId().toString());
                ps.setString(6,  m.getTargetBeaconName());
                ps.setInt   (7,  m.getDifficulty());
                ps.setInt   (8,  m.getRewardEc());
                ps.setLong  (9,  m.getRewardXp());
                ps.setLong  (10, m.getExpiresAt());
                ps.setString(11, m.getStatus().name());
                ps.setString(12, m.getAcceptedBy() != null ? m.getAcceptedBy().toString() : null);
                ps.setLong  (13, m.getAcceptedAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save mission", e);
            }
        });
    }

    public void delete(UUID missionId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM horizon_missions WHERE mission_id = ?")) {
                ps.setString(1, missionId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete mission", e);
            }
        });
    }

    /** Load all non-completed missions. Called on startup. */
    public List<Mission> loadActive() {
        List<Mission> list = new ArrayList<>();
        String sql = "SELECT * FROM horizon_missions WHERE status IN ('AVAILABLE','ACTIVE')";
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement  s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Mission m = fromResultSet(rs);
                if (m != null) list.add(m);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load missions", e);
        }
        return list;
    }

    private Mission fromResultSet(ResultSet rs) throws SQLException {
        try {
            UUID   id      = UUID.fromString(rs.getString("mission_id"));
            MissionType t  = MissionType.valueOf(rs.getString("type"));
            String title   = rs.getString("title");
            String desc    = rs.getString("description");
            UUID   beacon  = UUID.fromString(rs.getString("target_beacon_id"));
            String bName   = rs.getString("target_beacon_name");
            int    diff    = rs.getInt("difficulty");
            int    ec      = rs.getInt("reward_ec");
            long   xp      = rs.getLong("reward_xp");
            long   exp     = rs.getLong("expires_at");

            Mission m = new Mission(id, t, title, desc, beacon, bName, diff, ec, xp, exp);

            String statusStr = rs.getString("status");
            MissionStatus status = MissionStatus.valueOf(statusStr);
            String acceptedByStr = rs.getString("accepted_by");
            if (status == MissionStatus.ACTIVE && acceptedByStr != null) {
                m.accept(UUID.fromString(acceptedByStr));
            }
            return m;
        } catch (Exception e) {
            plugin.getLogger().warning("Malformed mission row: " + e.getMessage());
            return null;
        }
    }
}