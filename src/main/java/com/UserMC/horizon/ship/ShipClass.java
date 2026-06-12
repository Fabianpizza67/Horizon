package com.usermc.horizon.ship;

public enum ShipClass {

    SHUTTLE    ("Shuttle",     200,   200,   2),
    FIGHTER    ("Fighter",     500,   500,   4),
    FRIGATE    ("Frigate",     900,   900,   8),
    CRUISER    ("Cruiser",    1500,  1500,  16),
    CARRIER    ("Carrier",    2000,  2000,  24),
    DREADNOUGHT("Dreadnought",2500,  3000,  32),
    ADMIN      ("Admin",     10000, 10000, 100);

    private final String displayName;
    /** Soft limit — debuffs applied above this */
    private final int softLimit;
    /** Hard limit — cannot register a ship above this */
    private final int hardLimit;
    private final int maxCrewSlots;

    ShipClass(String displayName, int softLimit, int hardLimit, int maxCrewSlots) {
        this.displayName   = displayName;
        this.softLimit     = softLimit;
        this.hardLimit     = hardLimit;
        this.maxCrewSlots  = maxCrewSlots;
    }

    public String getDisplayName() { return displayName; }
    public int    getSoftLimit()   { return softLimit; }
    public int    getHardLimit()   { return hardLimit; }
    public int    getMaxCrewSlots(){ return maxCrewSlots; }

    /**
     * Determine the ship class from a given block count.
     * Returns the smallest class whose hard limit fits the count.
     */
    public static ShipClass fromBlockCount(int blocks, boolean admin) {
        if (admin) return ADMIN;
        for (ShipClass sc : values()) {
            if (sc == ADMIN) continue;
            if (blocks <= sc.hardLimit) return sc;
        }
        return DREADNOUGHT; // largest non-admin
    }

    /**
     * Returns true if this ship is over its soft limit (performance debuffs apply).
     */
    public boolean isOverSoftLimit(int blockCount) {
        return blockCount > softLimit;
    }
}