package com.usermc.horizon.faction;

/**
 * Internal rank of a player within their faction.
 *
 * Permission ladder (highest to lowest):
 *   LEADER   — full control: disband, transfer leadership, war declarations, all bank access
 *   OFFICER  — invite, kick recruits, deposit/withdraw bank, propose diplomacy
 *   MEMBER   — deposit to bank, view all faction info
 *   RECRUIT  — view faction info only
 */
public enum FactionRank {

    RECRUIT ("Recruit",  "§7", 0),
    MEMBER  ("Member",   "§f", 1),
    OFFICER ("Officer",  "§a", 2),
    LEADER  ("Leader",   "§6", 3);

    private final String displayName;
    private final String colour;
    private final int    tier;

    FactionRank(String displayName, String colour, int tier) {
        this.displayName = displayName;
        this.colour      = colour;
        this.tier        = tier;
    }

    public String getDisplayName() { return displayName; }
    public String getColour()      { return colour; }
    public int    getTier()        { return tier; }

    public boolean isAtLeast(FactionRank other) { return this.tier >= other.tier; }

    public String coloured() { return colour + displayName; }

    public static FactionRank fromString(String s) {
        if (s == null) return null;
        for (FactionRank r : values())
            if (r.name().equalsIgnoreCase(s) || r.displayName.equalsIgnoreCase(s)) return r;
        return null;
    }
}