package com.usermc.horizon.ship;

/**
 * Absolute movement directions in Minecraft coordinates.
 * North = -Z, South = +Z, East = +X, West = -X.
 */
public enum MoveDirection {

    NORTH ( 0,  0, -1),
    SOUTH ( 0,  0,  1),
    EAST  ( 1,  0,  0),
    WEST  (-1,  0,  0),
    UP    ( 0,  1,  0),
    DOWN  ( 0, -1,  0);

    public final int dx;
    public final int dy;
    public final int dz;

    MoveDirection(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    /** Parse case-insensitive string. Returns null if not found. */
    public static MoveDirection fromString(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "north", "n" -> NORTH;
            case "south", "s" -> SOUTH;
            case "east",  "e" -> EAST;
            case "west",  "w" -> WEST;
            case "up",    "u" -> UP;
            case "down",  "d" -> DOWN;
            default           -> null;
        };
    }
}