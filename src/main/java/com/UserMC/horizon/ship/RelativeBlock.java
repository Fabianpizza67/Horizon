package com.usermc.horizon.ship;

import org.bukkit.block.data.BlockData;

/**
 * Represents a single non-air block in a ship's structure,
 * stored as an offset from the Ship Core block.
 */
public record RelativeBlock(int dx, int dy, int dz, BlockData blockData, boolean tileEntity) {

    /** Absolute world X given the core's world X */
    public int absoluteX(int coreX) { return coreX + dx; }
    /** Absolute world Y given the core's world Y */
    public int absoluteY(int coreY) { return coreY + dy; }
    /** Absolute world Z given the core's world Z */
    public int absoluteZ(int coreZ) { return coreZ + dz; }

    /** Absolute world X after a movement delta */
    public int newX(int coreX, int moveDx) { return coreX + dx + moveDx; }
    /** Absolute world Y after a movement delta */
    public int newY(int coreY, int moveDy) { return coreY + dy + moveDy; }
    /** Absolute world Z after a movement delta */
    public int newZ(int coreZ, int moveDz) { return coreZ + dz + moveDz; }
}