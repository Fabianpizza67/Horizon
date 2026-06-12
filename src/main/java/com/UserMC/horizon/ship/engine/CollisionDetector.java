package com.usermc.horizon.ship.engine;

import com.usermc.horizon.ship.RelativeBlock;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.ShipScanner;
import com.usermc.horizon.ship.ShipStructure;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks whether a ship can move by the given delta without hitting anything.
 *
 * Runs synchronously on the main thread — world access is safe.
 *
 * Strategy:
 *   1. Build the set of current ship block positions (so overlapping positions
 *      are not flagged as collisions — that would prevent any overlap movement).
 *   2. For each new block position after the move, check if a non-air,
 *      non-ship-owned block exists there.
 */
public class CollisionDetector {

    /**
     * Returns true if moving the ship by (dx, dy, dz) would result in a collision.
     */
    public boolean hasCollision(Ship ship, int dx, int dy, int dz) {
        ShipStructure structure = ship.getStructure();
        if (structure == null) return false;

        Location core = ship.getCoreLocation();
        World world   = core.getWorld();
        if (world == null) return true;

        List<RelativeBlock> blocks = structure.getBlocks();
        int cx = core.getBlockX();
        int cy = core.getBlockY();
        int cz = core.getBlockZ();

        // Build the set of all current block positions (as packed longs)
        Set<Long> currentPositions = new HashSet<>(blocks.size() * 2);
        for (RelativeBlock rb : blocks) {
            currentPositions.add(ShipScanner.packKey(
                    rb.absoluteX(cx), rb.absoluteY(cy), rb.absoluteZ(cz)));
        }

        // Check each new position
        for (RelativeBlock rb : blocks) {
            int nx = rb.newX(cx, dx);
            int ny = rb.newY(cy, dy);
            int nz = rb.newZ(cz, dz);

            // Out of world bounds?
            if (ny < world.getMinHeight() || ny > world.getMaxHeight()) return true;

            // Block at new position that's neither air nor part of this ship?
            if (!world.getBlockAt(nx, ny, nz).getType().isAir()) {
                long key = ShipScanner.packKey(nx, ny, nz);
                if (!currentPositions.contains(key)) {
                    return true; // collision with an external block
                }
            }
        }

        return false;
    }
}