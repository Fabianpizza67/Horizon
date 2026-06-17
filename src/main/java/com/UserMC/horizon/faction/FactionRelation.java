package com.usermc.horizon.faction;

/**
 * Diplomatic relationship between two factions.
 *
 * State transitions:
 *   NEUTRAL  → ALLIED        (mutual: both leaders/officers must accept)
 *   NEUTRAL  → AT_WAR        (unilateral: one faction declares)
 *   NEUTRAL  → TRADE_PARTNER (mutual: both must accept)
 *   AT_WAR   → NEUTRAL       (mutual: both must accept peace)
 *   ALLIED   → NEUTRAL       (unilateral: either side withdraws)
 *   TRADE_PARTNER → NEUTRAL  (unilateral: either side withdraws)
 */
public enum FactionRelation {

    NEUTRAL      ("Neutral",       "§7", false),
    ALLIED       ("Allied",        "§a", false),
    TRADE_PARTNER("Trade Partner", "§b", false),
    AT_WAR       ("At War",        "§c", true);

    private final String displayName;
    private final String colour;
    private final boolean hostile;

    FactionRelation(String displayName, String colour, boolean hostile) {
        this.displayName = displayName;
        this.colour      = colour;
        this.hostile     = hostile;
    }

    public String  getDisplayName() { return displayName; }
    public String  getColour()      { return colour; }
    public boolean isHostile()      { return hostile; }

    public String coloured() { return colour + displayName; }
}