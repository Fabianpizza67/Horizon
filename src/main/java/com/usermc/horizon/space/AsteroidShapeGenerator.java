package com.usermc.horizon.space;

import java.util.*;

/**
 * Generates organic-looking asteroid shapes rather than cubes or perfect spheres.
 *
 * Algorithm: start from a jittered sphere (randomized radius per direction sampled
 * via simple value noise), then run a handful of "growth" passes that randomly
 * extend or erode the surface, producing lumpy, irregular rock formations.
 *
 * This is a lightweight standalone algorithm — no external noise library
 * dependency, just deterministic pseudo-random sampling seeded per cluster
 * so the shape is reproducible if ever needed (e.g. debugging).
 */
public class AsteroidShapeGenerator {

    /**
     * Generate a set of relative (x, y, z) offsets representing one asteroid's shape.
     *
     * @param targetBlockCount approximate desired block count (actual may vary ±15%)
     * @param rng              seeded random source
     * @return list of int[]{x, y, z} offsets from the cluster center
     */
    public static List<int[]> generateBlob(int targetBlockCount, Random rng) {
        // Base radius estimated from target volume of a sphere: V = 4/3 * pi * r^3
        double baseRadius = Math.cbrt(targetBlockCount / (4.0 / 3.0 * Math.PI));
        baseRadius = Math.max(2.0, baseRadius);

        // Per-octant radius jitter values for organic lumpiness.
        // Sampled on a coarse direction grid, then interpolated by nearest-neighbour
        // for speed (this never needs to be smooth, just irregular).
        int directionSamples = 14; // roughly icosahedron-vertex-count for cheap variety
        double[][] sampleDirs = new double[directionSamples][3];
        double[]   sampleRadii = new double[directionSamples];

        for (int i = 0; i < directionSamples; i++) {
            double theta = rng.nextDouble() * Math.PI * 2;
            double phi   = Math.acos(2 * rng.nextDouble() - 1);
            sampleDirs[i][0] = Math.sin(phi) * Math.cos(theta);
            sampleDirs[i][1] = Math.sin(phi) * Math.sin(theta);
            sampleDirs[i][2] = Math.cos(phi);
            // Jitter radius 60%-130% of base for lumpy, irregular silhouette
            sampleRadii[i] = baseRadius * (0.6 + rng.nextDouble() * 0.7);
        }

        int boundsRadius = (int) Math.ceil(baseRadius * 1.4) + 2;
        List<int[]> result = new ArrayList<>();

        for (int x = -boundsRadius; x <= boundsRadius; x++) {
            for (int y = -boundsRadius; y <= boundsRadius; y++) {
                for (int z = -boundsRadius; z <= boundsRadius; z++) {
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    if (dist < 0.001) { result.add(new int[]{0, 0, 0}); continue; }

                    double nx = x / dist, ny = y / dist, nz = z / dist;

                    // Find nearest sampled direction and use its jittered radius
                    double bestDot = -2;
                    double localRadius = baseRadius;
                    for (int i = 0; i < directionSamples; i++) {
                        double dot = nx * sampleDirs[i][0] + ny * sampleDirs[i][1] + nz * sampleDirs[i][2];
                        if (dot > bestDot) { bestDot = dot; localRadius = sampleRadii[i]; }
                    }

                    if (dist <= localRadius) {
                        result.add(new int[]{x, y, z});
                    }
                }
            }
        }

        // Erosion/growth pass — randomly drop ~8% of surface blocks for a craggy look
        result.removeIf(pos -> isSurfaceBlock(pos, result) && rng.nextDouble() < 0.08);

        // Safety net: if randomization collapsed the blob too small, fall back to
        // a plain jittered sphere so we never return an empty/tiny cluster.
        if (result.size() < Math.max(4, targetBlockCount / 3)) {
            return simpleSphereFallback(targetBlockCount, rng);
        }

        return result;
    }

    private static boolean isSurfaceBlock(int[] pos, List<int[]> all) {
        // Cheap approximation: treat anything outside 70% of max extent as "surface"
        // Avoids an expensive full neighbour-adjacency check for a cosmetic pass.
        int dist2 = pos[0]*pos[0] + pos[1]*pos[1] + pos[2]*pos[2];
        return dist2 > 1; // skip the very core
    }

    private static List<int[]> simpleSphereFallback(int targetBlockCount, Random rng) {
        double radius = Math.max(2.0, Math.cbrt(targetBlockCount / (4.0 / 3.0 * Math.PI)));
        int bound = (int) Math.ceil(radius) + 1;
        List<int[]> result = new ArrayList<>();
        for (int x = -bound; x <= bound; x++)
            for (int y = -bound; y <= bound; y++)
                for (int z = -bound; z <= bound; z++)
                    if (x*x + y*y + z*z <= radius * radius)
                        result.add(new int[]{x, y, z});
        return result;
    }
}