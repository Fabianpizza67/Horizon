package com.usermc.horizon.world;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

/**
 * Forces every position in the Deep Space world to report THE_VOID biome.
 *
 * This matters purely for client-side rendering and ambience: THE_VOID biome
 * gives the correct black sky, removes the normal sky-fog gradient, and
 * avoids any biome-specific ambient sounds or particle effects that would
 * feel completely wrong floating in open space.
 *
 * Without this, a void world technically has no blocks but still renders
 * with whatever default overworld biome fog/sky the dimension type implies,
 * which looks wrong for a space setting.
 */
public class VoidBiomeProvider extends BiomeProvider {

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return Biome.THE_VOID;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return List.of(Biome.THE_VOID);
    }
}