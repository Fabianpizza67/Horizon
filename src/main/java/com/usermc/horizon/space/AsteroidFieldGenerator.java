package com.usermc.horizon.space;

import org.bukkit.World;

import java.util.*;

/**
 * Procedurally decides where asteroid clusters should exist across the void world.
 *
 * Generation is region-based and lazy: the world is conceptually divided into
 * REGION_SIZE x REGION_SIZE x REGION_SIZE cubes (in blocks). A region is only
 * "rolled" for clusters the first time a player gets near it — at that point
 * a deterministic seed (derived from region coordinates + world seed) decides
 * how many clusters spawn in that region and of what tier, so the same region
 * always generates the same way even if rolled on two different occasions
 * (e.g. after a region's clusters are all eventually cleared from memory).
 *
 * Tier weighting: common clusters are far more frequent than rare ones, but
 * EVERY region has a chance at something — there's no "safe zone" vs "danger
 * zone" split, just probability. This keeps exploration always worth doing.
 */
public class AsteroidFieldGenerator {

    /** Size of one cubic generation region, in blocks. */
    public static final int REGION_SIZE = 512;

    /** Max clusters that can spawn in a single region. */
    private static final int MAX_CLUSTERS_PER_REGION = 3;

    /** Chance (0–1) that a region has ANY clusters at all. */
    private static final double REGION_OCCUPANCY_CHANCE = 0.65;

    /** Relative tier weights for cluster generation. */
    private static final int WEIGHT_COMMON   = 60;
    private static final int WEIGHT_UNCOMMON = 30;
    private static final int WEIGHT_RARE     = 10;
    private static final int WEIGHT_TOTAL    = WEIGHT_COMMON + WEIGHT_UNCOMMON + WEIGHT_RARE;

    /** Minimum block-space gap enforced between two cluster centers in the same region. */
    private static final int MIN_CLUSTER_SPACING = 40;

    private final long worldSeed;

    public AsteroidFieldGenerator(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    /** Region coordinate for a given world block coordinate. */
    public static long[] regionOf(int blockX, int blockY, int blockZ) {
        return new long[]{
                Math.floorDiv(blockX, REGION_SIZE),
                Math.floorDiv(blockY, REGION_SIZE),
                Math.floorDiv(blockZ, REGION_SIZE)
        };
    }

    /** Deterministic seed for a specific region — same region always produces same result. */
    private long seedForRegion(long rx, long ry, long rz) {
        long h = worldSeed;
        h = h * 31 + rx;
        h = h * 31 + ry;
        h = h * 31 + rz;
        // Mix bits (splitmix64-style) for better distribution from sequential region coords
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return h;
    }

    /**
     * Generate the list of cluster "plans" for a region — center coordinates and
     * tiers, but NOT yet materialized into actual AsteroidCluster/blocks. The
     * caller (AsteroidManager) turns these into real clusters on demand.
     */
    public List<ClusterPlan> generateRegionPlan(long rx, long ry, long rz) {
        Random rng = new Random(seedForRegion(rx, ry, rz));
        List<ClusterPlan> plans = new ArrayList<>();

        if (rng.nextDouble() > REGION_OCCUPANCY_CHANCE) return plans; // empty region

        int clusterCount = 1 + rng.nextInt(MAX_CLUSTERS_PER_REGION);
        List<int[]> placedCenters = new ArrayList<>();

        int regionOriginX = (int) (rx * REGION_SIZE);
        int regionOriginY = (int) (ry * REGION_SIZE);
        int regionOriginZ = (int) (rz * REGION_SIZE);

        int attempts = 0;
        while (plans.size() < clusterCount && attempts < clusterCount * 10) {
            attempts++;

            int localX = rng.nextInt(REGION_SIZE);
            int localY = rng.nextInt(REGION_SIZE);
            int localZ = rng.nextInt(REGION_SIZE);

            int worldX = regionOriginX + localX;
            int worldY = regionOriginY + localY;
            int worldZ = regionOriginZ + localZ;

            boolean tooClose = false;
            for (int[] c : placedCenters) {
                double d = Math.sqrt(Math.pow(c[0]-worldX,2)+Math.pow(c[1]-worldY,2)+Math.pow(c[2]-worldZ,2));
                if (d < MIN_CLUSTER_SPACING) { tooClose = true; break; }
            }
            if (tooClose) continue;

            placedCenters.add(new int[]{worldX, worldY, worldZ});
            ClusterType tier = rollTier(rng);
            plans.add(new ClusterPlan(worldX, worldY, worldZ, tier));
        }

        return plans;
    }

    private ClusterType rollTier(Random rng) {
        int roll = rng.nextInt(WEIGHT_TOTAL);
        if (roll < WEIGHT_COMMON) return ClusterType.COMMON;
        if (roll < WEIGHT_COMMON + WEIGHT_UNCOMMON) return ClusterType.UNCOMMON;
        return ClusterType.RARE;
    }

    /** A planned cluster location/tier, before block generation. */
    public record ClusterPlan(int centerX, int centerY, int centerZ, ClusterType tier) {}
}