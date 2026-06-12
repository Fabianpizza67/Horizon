package com.usermc.horizon.ship;

import com.usermc.horizon.Horizon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Scans a ship's block structure via BFS flood-fill starting from the Ship Core.
 *
 * Runs INCREMENTALLY — N BFS steps per tick on the main thread, so world
 * access is always safe. No async world calls.
 *
 * For a 3000-block ship at 1000 steps/tick, scanning takes ~3 ticks (150ms).
 */
public class ShipScanner {

    // 6-face neighbour offsets
    private static final int[] NX = {1, -1, 0,  0, 0,  0};
    private static final int[] NY = {0,  0, 1, -1, 0,  0};
    private static final int[] NZ = {0,  0, 0,  0, 1, -1};

    private final Horizon plugin;

    public ShipScanner(Horizon plugin) {
        this.plugin = plugin;
    }

    /**
     * Start an asynchronous (incremental-tick) scan from startLocation.
     *
     * @param startLocation  The Ship Core block location
     * @param maxBlocks      Maximum non-air blocks to include
     * @param onComplete     Called with the completed ShipStructure on success
     * @param onError        Called with an error message on failure
     */
    public void scan(Location startLocation,
                     int maxBlocks,
                     Consumer<ShipStructure> onComplete,
                     Consumer<String> onError) {

        World world = startLocation.getWorld();
        int cx = startLocation.getBlockX();
        int cy = startLocation.getBlockY();
        int cz = startLocation.getBlockZ();
        int stepsPerTick = plugin.getHorizonConfig().getScanStepsPerTick();

        new BukkitRunnable() {

            final Deque<int[]>  queue   = new ArrayDeque<>();
            final Set<Long>     visited = new HashSet<>();
            final List<RelativeBlock> result  = new ArrayList<>();

            {
                // Seed the BFS with the core location
                enqueue(cx, cy, cz);
            }

            @Override
            public void run() {
                int steps = 0;

                while (!queue.isEmpty() && steps < stepsPerTick) {
                    int[] pos = queue.poll();
                    steps++;

                    int x = pos[0], y = pos[1], z = pos[2];
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();

                    // Skip air — don't store it, don't expand from it
                    if (type.isAir()) continue;

                    // Capture block data (safe on main thread)
                    BlockData data = block.getBlockData();
                    BlockState state = block.getState();
                    boolean isTile = (state instanceof TileState || state instanceof Container);

                    result.add(new RelativeBlock(x - cx, y - cy, z - cz, data, isTile));

                    // Hard limit check
                    if (result.size() >= maxBlocks) {
                        cancel();
                        onError.accept("Ship exceeds the block limit of " + maxBlocks + ".");
                        return;
                    }

                    // Expand to 6 neighbours
                    for (int i = 0; i < 6; i++) {
                        enqueue(x + NX[i], y + NY[i], z + NZ[i]);
                    }
                }

                // BFS complete?
                if (queue.isEmpty()) {
                    cancel();
                    onComplete.accept(new ShipStructure(result));
                }
                // Otherwise we'll continue next tick
            }

            /** Add to queue only if not already visited and within world height */
            private void enqueue(int x, int y, int z) {
                if (y < world.getMinHeight() || y > world.getMaxHeight()) return;
                long key = packKey(x, y, z);
                if (visited.add(key)) {
                    queue.add(new int[]{x, y, z});
                }
            }

        }.runTaskTimer(plugin, 0L, 1L);
    }

    // -----------------------------------------------------------------------
    // Pack (x, y, z) into a single long for fast Set lookups.
    // x and z are world coords (±30M), y is ±512.
    // We offset each to make them non-negative, then bit-pack.
    // -----------------------------------------------------------------------
    public static long packKey(int x, int y, int z) {
        // x: offset by 30_000_000, max value 60_000_000 → 26 bits
        // y: offset by 512, max value ~900 → 11 bits
        // z: offset by 30_000_000, max value 60_000_000 → 26 bits
        // Total 63 bits — fits in a signed long.
        long ox = (long)(x + 30_000_000);
        long oy = (long)(y + 512);
        long oz = (long)(z + 30_000_000);
        return (ox << 37) | (oy << 26) | oz;
    }
}
