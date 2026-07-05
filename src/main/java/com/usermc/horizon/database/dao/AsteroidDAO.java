package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.space.AsteroidBlock;
import com.usermc.horizon.space.AsteroidCluster;
import com.usermc.horizon.space.ClusterType;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class AsteroidDAO {

    private final Horizon plugin;

    public AsteroidDAO(Horizon plugin) { this.plugin = plugin; }

    // -----------------------------------------------------------------------
    // Region tracking — so we know which regions have already been generated
    // -----------------------------------------------------------------------

    public void markRegionGenerated(long rx, long ry, long rz) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement("""
                     INSERT IGNORE INTO horizon_asteroid_regions (region_x, region_y, region_z)
                     VALUES (?, ?, ?)
                 """)) {
                ps.setLong(1, rx); ps.setLong(2, ry); ps.setLong(3, rz);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to mark region generated", e);
            }
        });
    }

    /** Synchronous — called once at world load time to know what's already generated. */
    public Set<Long> loadGeneratedRegionKeys() {
        Set<Long> keys = new HashSet<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT region_x, region_y, region_z FROM horizon_asteroid_regions")) {
            while (rs.next()) {
                keys.add(packRegionKey(rs.getLong(1), rs.getLong(2), rs.getLong(3)));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load generated regions", e);
        }
        return keys;
    }

    public static long packRegionKey(long rx, long ry, long rz) {
        // Same bit-packing approach as ShipScanner.packKey, scaled for region coords
        long ox = rx + 2_000_000L;
        long oy = ry + 2_000_000L;
        long oz = rz + 2_000_000L;
        return (ox << 42) | (oy << 21) | oz;
    }

    // -----------------------------------------------------------------------
    // Clusters
    // -----------------------------------------------------------------------

    public void saveCluster(AsteroidCluster cluster) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveClusterSync(cluster));
    }

    public void saveClusterSync(AsteroidCluster cluster) {
        String sql = """
            INSERT IGNORE INTO horizon_asteroid_clusters
                (cluster_id, type, world_name, center_x, center_y, center_z, generated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cluster.getClusterId().toString());
            ps.setString(2, cluster.getType().name());
            ps.setString(3, cluster.getWorldName());
            ps.setInt   (4, cluster.getCenterX());
            ps.setInt   (5, cluster.getCenterY());
            ps.setInt   (6, cluster.getCenterZ());
            ps.setLong  (7, cluster.getGeneratedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save asteroid cluster", e);
        }
    }

    /** Synchronous — load all clusters. Called on plugin startup. */
    public List<AsteroidCluster> loadAllClusters() {
        List<AsteroidCluster> clusters = new ArrayList<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM horizon_asteroid_clusters")) {
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("cluster_id"));
                ClusterType type = ClusterType.valueOf(rs.getString("type"));
                String world = rs.getString("world_name");
                int cx = rs.getInt("center_x"), cy = rs.getInt("center_y"), cz = rs.getInt("center_z");
                long generatedAt = rs.getLong("generated_at");
                clusters.add(new AsteroidCluster(id, type, world, cx, cy, cz, generatedAt));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load asteroid clusters", e);
        }
        return clusters;
    }

    // -----------------------------------------------------------------------
    // Blocks
    // -----------------------------------------------------------------------

    /** Batch-insert all blocks for a freshly generated cluster. Synchronous — only happens once per cluster ever. */
    public void saveBlocksSync(Collection<AsteroidBlock> blocks) {
        if (blocks.isEmpty()) return;
        String sql = """
            INSERT INTO horizon_asteroid_blocks
                (block_id, cluster_id, rel_x, rel_y, rel_z, material, mined, mined_at, respawn_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection c = plugin.getDatabaseManager().getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (AsteroidBlock b : blocks) {
                    ps.setString (1, b.getBlockId().toString());
                    ps.setString (2, b.getClusterId().toString());
                    ps.setInt    (3, b.getRelX());
                    ps.setInt    (4, b.getRelY());
                    ps.setInt    (5, b.getRelZ());
                    ps.setString (6, b.getCurrentMaterial().name());
                    ps.setBoolean(7, b.isMined());
                    ps.setLong   (8, 0);
                    ps.setLong   (9, b.getRespawnAt());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to batch-save asteroid blocks", e);
        }
    }

    /** Lightweight async update for one block's mined/respawn state. */
    public void updateBlockState(AsteroidBlock block) {
        String sql = """
            UPDATE horizon_asteroid_blocks
            SET material = ?, mined = ?, respawn_at = ?
            WHERE block_id = ?
        """;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString (1, block.getCurrentMaterial().name());
                ps.setBoolean(2, block.isMined());
                ps.setLong   (3, block.getRespawnAt());
                ps.setString (4, block.getBlockId().toString());
                ps.executeUpdate();
                block.clearDirty();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update asteroid block state", e);
            }
        });
    }

    /** Synchronous — load all blocks for all clusters. Called once on startup. */
    public Map<UUID, List<AsteroidBlock>> loadAllBlocksByCluster() {
        Map<UUID, List<AsteroidBlock>> result = new HashMap<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM horizon_asteroid_blocks")) {
            while (rs.next()) {
                UUID blockId   = UUID.fromString(rs.getString("block_id"));
                UUID clusterId = UUID.fromString(rs.getString("cluster_id"));
                int relX = rs.getInt("rel_x"), relY = rs.getInt("rel_y"), relZ = rs.getInt("rel_z");
                org.bukkit.Material mat = org.bukkit.Material.valueOf(rs.getString("material"));
                boolean mined = rs.getBoolean("mined");
                long minedAt = rs.getLong("mined_at");
                long respawnAt = rs.getLong("respawn_at");

                AsteroidBlock block = new AsteroidBlock(blockId, clusterId, relX, relY, relZ,
                        mat, mined, minedAt, respawnAt);
                result.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(block);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load asteroid blocks", e);
        }
        return result;
    }
}