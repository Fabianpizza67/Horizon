package com.usermc.horizon.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/**
 * Produces a completely empty world — no terrain, no caves, no bedrock,
 * no decorations, no structures, no natural mob spawning.
 *
 * This is the "Deep Space" world generator. Asteroid clusters are NOT
 * generated here — that's handled separately and lazily by AsteroidManager
 * placing real blocks as players explore. This class's only job is making
 * sure nothing else (vanilla terrain, caves, ravines, ore veins, villages)
 * tries to generate into the same space.
 *
 * Registered via Horizon#getDefaultWorldGenerator so Multiverse (or any
 * other world-creation tool) can reference it by plugin name:
 *   /mv create DeepSpace normal -g Horizon
 */
public class VoidWorldGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise()       { return false; }
    @Override
    public boolean shouldGenerateSurface()     { return false; }
    @Override
    public boolean shouldGenerateBedrock()     { return false; }
    @Override
    public boolean shouldGenerateCaves()       { return false; }
    @Override
    public boolean shouldGenerateDecorations() { return false; }
    @Override
    public boolean shouldGenerateMobs()        { return false; }
    @Override
    public boolean shouldGenerateStructures()  { return false; }

    /**
     * Fixed spawn point in the void — a small platform should be built
     * here manually (or via /ship or admin tools) since there's otherwise
     * nothing for a player to stand on when they first join this world.
     */
    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, 100, 0.5);
    }
}