package com.usermc.horizon.space;

import org.bukkit.Material;

import java.util.*;

/**
 * Tiers of asteroid clusters.
 *
 * Each tier defines:
 *   weights      — material → relative spawn weight (higher = more common)
 *   minSize/maxSize — block count range per cluster
 *   minRespawnMin/maxRespawnMin — per-block respawn time range in minutes
 *
 * Materials randomize on respawn — a block that was iron ore may come
 * back as gold ore. This is intentional: location matters, not just
 * what was there before.
 */
public enum ClusterType {

    COMMON("Common", "§7",
            Map.of(
                    Material.STONE,      35,
                    Material.IRON_ORE,   25,
                    Material.COAL_ORE,   20,
                    Material.COPPER_ORE, 15,
                    Material.GRAVEL,      5
            ),
            15, 35,   // block count range
            60, 180   // respawn minutes range (1–3 hours)
    ),

    UNCOMMON("Uncommon", "§e",
            Map.of(
                    Material.IRON_ORE,     25,
                    Material.GOLD_ORE,     20,
                    Material.LAPIS_ORE,    20,
                    Material.REDSTONE_ORE, 15,
                    Material.GLOWSTONE,    20   // Dilithium ingredient
            ),
            20, 45,     // block count range
            180, 360    // respawn minutes range (3–6 hours)
    ),

    RARE("Rare", "§b",
            Map.of(
                    Material.GOLD_ORE,        15,
                    Material.DIAMOND_ORE,     20,
                    Material.EMERALD_ORE,     15,
                    Material.AMETHYST_CLUSTER,25,  // Dilithium ingredient
                    Material.ANCIENT_DEBRIS,  25
            ),
            25, 60,     // block count range
            360, 720    // respawn minutes range (6–12 hours)
    );

    private final String              displayName;
    private final String              colour;
    private final Map<Material, Integer> weights;
    private final int minSize, maxSize;
    private final int minRespawnMin, maxRespawnMin;

    /** Total weight sum — cached for O(1) random selection. */
    private final int totalWeight;

    ClusterType(String displayName, String colour,
                Map<Material, Integer> weights,
                int minSize, int maxSize,
                int minRespawnMin, int maxRespawnMin) {
        this.displayName    = displayName;
        this.colour         = colour;
        this.weights        = Collections.unmodifiableMap(weights);
        this.minSize        = minSize;
        this.maxSize        = maxSize;
        this.minRespawnMin  = minRespawnMin;
        this.maxRespawnMin  = maxRespawnMin;
        this.totalWeight    = weights.values().stream().mapToInt(Integer::intValue).sum();
    }

    public String getDisplayName() { return displayName; }
    public String getColour()      { return colour; }
    public int    getMinSize()     { return minSize; }
    public int    getMaxSize()     { return maxSize; }

    /** Pick a cluster size within this tier's range. */
    public int randomSize(Random rng) {
        return rng.nextInt(maxSize - minSize + 1) + minSize;
    }

    /**
     * Pick a random material from this tier's weight table.
     * Called both at cluster generation AND at block respawn time,
     * so respawned blocks may differ from what was originally there.
     */
    public Material randomMaterial(Random rng) {
        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (Map.Entry<Material, Integer> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) return entry.getKey();
        }
        return weights.keySet().iterator().next(); // fallback, should never hit
    }

    /**
     * Pick a random respawn delay in milliseconds for one block.
     * Each block in a cluster respawns independently.
     */
    public long randomRespawnMs(Random rng) {
        int minutes = rng.nextInt(maxRespawnMin - minRespawnMin + 1) + minRespawnMin;
        return (long) minutes * 60 * 1000;
    }

    public static ClusterType fromString(String s) {
        if (s == null) return null;
        for (ClusterType t : values())
            if (t.name().equalsIgnoreCase(s) || t.displayName.equalsIgnoreCase(s)) return t;
        return null;
    }
}