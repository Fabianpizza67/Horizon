package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Generic slot-based item storage, shared by two features:
 *   - Standalone Chip Rack station blocks (27 slots)
 *   - The Navigation Console's built-in installed-chip row (9 slots)
 *
 * Both are keyed by a "containerId" (a UUID — either the rack's own ID or
 * the navigation console station's ID) plus a slot index, so one table and
 * one DAO covers both without duplicating persistence logic.
 *
 * Items are serialized via Bukkit's ObjectOutputStream + Base64, which is
 * the standard approach for persisting arbitrary ItemStacks (including PDC
 * tags) outside of a real inventory.
 */
public class ChipStorageDAO {

    private final Horizon plugin;

    public ChipStorageDAO(Horizon plugin) { this.plugin = plugin; }

    // -----------------------------------------------------------------------
    // Save one slot (null itemStack = clear the slot)
    // -----------------------------------------------------------------------

    public void saveSlot(UUID containerId, int slot, ItemStack item) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveSlotSync(containerId, slot, item));
    }

    public void saveSlotSync(UUID containerId, int slot, ItemStack item) {
        if (item == null) {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM horizon_chip_storage WHERE container_id = ? AND slot = ?")) {
                ps.setString(1, containerId.toString());
                ps.setInt   (2, slot);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clear chip storage slot", e);
            }
            return;
        }

        String serialized;
        try {
            serialized = serialize(item);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to serialize chip item", e);
            return;
        }

        String sql = """
            INSERT INTO horizon_chip_storage (container_id, slot, item_data)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE item_data = VALUES(item_data)
        """;
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, containerId.toString());
            ps.setInt   (2, slot);
            ps.setString(3, serialized);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save chip storage slot", e);
        }
    }

    // -----------------------------------------------------------------------
    // Load all slots for one container — synchronous, called on demand
    // (rack/console GUI open, or bulk-loaded at startup for all containers)
    // -----------------------------------------------------------------------

    public Map<Integer, ItemStack> loadContainer(UUID containerId) {
        Map<Integer, ItemStack> result = new HashMap<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT slot, item_data FROM horizon_chip_storage WHERE container_id = ?")) {
            ps.setString(1, containerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    String data = rs.getString("item_data");
                    try {
                        result.put(slot, deserialize(data));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to deserialize chip storage slot " + slot
                                + " for container " + containerId);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load chip storage container " + containerId, e);
        }
        return result;
    }

    /** Bulk-load every container's contents at startup — keyed by containerId then slot. */
    public Map<UUID, Map<Integer, ItemStack>> loadAll() {
        Map<UUID, Map<Integer, ItemStack>> result = new HashMap<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT container_id, slot, item_data FROM horizon_chip_storage")) {
            while (rs.next()) {
                UUID containerId = UUID.fromString(rs.getString("container_id"));
                int slot = rs.getInt("slot");
                String data = rs.getString("item_data");
                try {
                    result.computeIfAbsent(containerId, k -> new HashMap<>()).put(slot, deserialize(data));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to deserialize chip storage slot " + slot
                            + " for container " + containerId);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to bulk-load chip storage", e);
        }
        return result;
    }

    public void deleteContainer(UUID containerId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM horizon_chip_storage WHERE container_id = ?")) {
                ps.setString(1, containerId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete chip storage container", e);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Serialization helpers
    // -----------------------------------------------------------------------

    private String serialize(ItemStack item) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    private ItemStack deserialize(String data) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(data);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) bois.readObject();
        }
    }
}