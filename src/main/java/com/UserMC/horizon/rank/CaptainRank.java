package com.usermc.horizon.rank;

/**
 * Captain rank progression.
 * XP is earned through missions, warp jumps and exploration.
 * Higher ranks unlock larger ship classes and more crew slots.
 */
public enum CaptainRank {

    CADET          ("Cadet",               0,       1),
    ENSIGN         ("Ensign",              100,     2),
    LIEUTENANT     ("Lieutenant",          500,     3),
    LT_COMMANDER   ("Lieutenant Commander",1_500,   4),
    COMMANDER      ("Commander",           3_500,   5),
    CAPTAIN        ("Captain",             7_500,   6),
    COMMODORE      ("Commodore",           15_000,  7),
    REAR_ADMIRAL   ("Rear Admiral",        30_000,  8),
    VICE_ADMIRAL   ("Vice Admiral",        60_000,  9),
    ADMIRAL        ("Admiral",             100_000, 10);

    private final String displayName;
    private final long   xpRequired;
    private final int    tier;

    CaptainRank(String displayName, long xpRequired, int tier) {
        this.displayName = displayName;
        this.xpRequired  = xpRequired;
        this.tier        = tier;
    }

    public String getDisplayName() { return displayName; }
    public long   getXpRequired()  { return xpRequired; }
    public int    getTier()        { return tier; }

    /** Prefix shown in chat and GUIs, e.g. §b[Cadet] */
    public String getChatPrefix() {
        String colour = switch (this) {
            case CADET, ENSIGN          -> "§7";
            case LIEUTENANT, LT_COMMANDER -> "§a";
            case COMMANDER, CAPTAIN     -> "§b";
            case COMMODORE, REAR_ADMIRAL -> "§d";
            case VICE_ADMIRAL, ADMIRAL  -> "§6";
        };
        return colour + "[" + displayName + "]";
    }

    /** Next rank, or this rank if already Admiral. */
    public CaptainRank next() {
        CaptainRank[] values = values();
        return ordinal() + 1 < values.length ? values[ordinal() + 1] : this;
    }

    public boolean isMaxRank() { return this == ADMIRAL; }

    /** Determine rank from total XP. */
    public static CaptainRank fromXp(long xp) {
        CaptainRank result = CADET;
        for (CaptainRank r : values()) {
            if (xp >= r.xpRequired) result = r;
        }
        return result;
    }
}