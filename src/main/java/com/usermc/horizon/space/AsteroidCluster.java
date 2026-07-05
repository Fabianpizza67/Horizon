package com.usermc.horizon.space;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

/**
 * One asteroid cluster — a cloud of AsteroidBlocks anchored at a center point
 * in a world. Clusters are generated procedurally by AsteroidFieldGenerator
 * and persisted via AsteroidDAO.
 */
public class AsteroidCluster {

    private final UUID         clusterId;
    private final ClusterType  type;
    private final String       worldName;
    private final int          centerX, centerY, centerZ;
    private final long         generatedAt;

    /** All blocks belonging to this cluster, keyed by blockId. */
    private final Map<UUID, AsteroidBlock> blocks = new LinkedHashMap<>();

    public AsteroidCluster(UUID clusterId, ClusterType type, String worldName,
                           int centerX, int centerY, int centerZ, long generatedAt) {
        this.clusterId   = clusterId;
        this.type        = type;
        this.worldName   = worldName;
        this.centerX     = centerX;
        this.centerY     = centerY;
        this.centerZ     = centerZ;
        this.generatedAt = generatedAt;
    }

    public UUID        getClusterId()  { return clusterId; }
    public ClusterType getType()       { return type; }
    public String      getWorldName()  { return worldName; }
    public int         getCenterX()    { return centerX; }
    public int         getCenterY()    { return centerY; }
    public int         getCenterZ()    { return centerZ; }
    public long         getGeneratedAt(){ return generatedAt; }

    public Location getCenterLocation(World world) {
        return new Location(world, centerX, centerY, centerZ);
    }

    public void addBlock(AsteroidBlock block) {
        blocks.put(block.getBlockId(), block);
    }

    public Collection<AsteroidBlock> getBlocks() { return Collections.unmodifiableCollection(blocks.values()); }
    public int getBlockCount() { return blocks.size(); }

    /** Rough bounding radius — useful for distance culling when scanning for nearby clusters. */
    public int getApproxRadius() {
        int max = 0;
        for (AsteroidBlock b : blocks.values()) {
            int d = Math.max(Math.abs(b.getRelX()), Math.max(Math.abs(b.getRelY()), Math.abs(b.getRelZ())));
            if (d > max) max = d;
        }
        return max + 1;
    }

    /** Distance from a world location to this cluster's center, in blocks. */
    public double distanceTo(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) return Double.MAX_VALUE;
        double dx = loc.getX() - centerX;
        double dy = loc.getY() - centerY;
        double dz = loc.getZ() - centerZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}