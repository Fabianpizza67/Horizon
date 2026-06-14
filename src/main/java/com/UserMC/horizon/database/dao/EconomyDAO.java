package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class EconomyDAO {

    private final Horizon plugin;

    public EconomyDAO(Horizon plugin) { this.plugin = plugin; }

    public long getBalance(UUID uuid) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT balance FROM horizon_economy WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("balance");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get balance for " + uuid, e);
        }
        return 0;
    }

    public void setBalance(UUID uuid, String playerName, long balance) {
        String sql = """
            INSERT INTO horizon_economy (player_uuid, player_name, balance)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE balance = VALUES(balance), player_name = VALUES(player_name)
        """;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setLong  (3, Math.max(0, balance));
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to set balance for " + uuid, e);
            }
        });
    }

    /**
     * Synchronous balance write — used during plugin shutdown so the final
     * cached balance is guaranteed to hit the database before the connection
     * pool closes. UPDATE-only: if the row doesn't exist yet there's nothing
     * to flush (balance would still be 0/default).
     */
    public void setBalanceSync(UUID uuid, long balance) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE horizon_economy SET balance = ? WHERE player_uuid = ?")) {
            ps.setLong  (1, Math.max(0, balance));
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync-save balance for " + uuid, e);
        }
    }

    public void addToBalance(UUID uuid, String playerName, long amount) {
        String sql = """
            INSERT INTO horizon_economy (player_uuid, player_name, balance, total_earned)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                balance       = balance + VALUES(balance),
                total_earned  = total_earned + VALUES(total_earned),
                player_name   = VALUES(player_name)
        """;
        long earned = amount > 0 ? amount : 0;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setLong  (3, amount);
                ps.setLong  (4, earned);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to add to balance for " + uuid, e);
            }
        });
    }
}