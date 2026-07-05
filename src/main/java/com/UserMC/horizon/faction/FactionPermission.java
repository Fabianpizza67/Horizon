package com.usermc.horizon.faction;

/**
 * All permissions a faction rank can be granted, stored as bit flags in a
 * single long so checking/toggling is fast and the schema never needs to
 * change when new permissions are added (just add another bit).
 *
 * Grouped loosely by category for the rank editor GUI, but mechanically
 * they're all just bits in the same bitmask.
 *
 * The LEADER rank ignores this entirely and is always treated as having
 * every permission — see FactionRankDef#hasPermission.
 */
public enum FactionPermission {

    // --- Membership ---
    INVITE_MEMBERS    (1L,      "Invite Members",      "Send invites to new players"),
    KICK_MEMBERS      (1L << 1, "Kick Members",         "Remove members of lower rank"),
    MANAGE_RANKS      (1L << 2, "Manage Ranks",         "Create/edit ranks and assign them to members"),

    // --- Bank ---
    BANK_DEPOSIT      (1L << 3, "Bank Deposit",         "Deposit credits into the faction bank"),
    BANK_WITHDRAW     (1L << 4, "Bank Withdraw",         "Withdraw credits from the faction bank"),
    BANK_VIEW         (1L << 5, "Bank View",            "View the faction bank balance"),

    // --- Diplomacy ---
    PROPOSE_ALLIANCE  (1L << 6, "Propose Alliance",      "Propose or accept alliances"),
    PROPOSE_TRADE     (1L << 7, "Propose Trade",         "Propose or accept trade partnerships"),
    DECLARE_WAR       (1L << 8, "Declare War",           "Declare war on another faction"),
    PROPOSE_PEACE     (1L << 9, "Propose Peace",         "Propose or accept peace"),

    // --- Stations ---
    CREATE_STATION    (1L << 10,"Create Station",        "Register a new faction-owned station"),
    MANAGE_STATION    (1L << 11,"Manage Station",        "Modify or deregister faction stations"),
    STATION_BUILD     (1L << 12,"Station Build",         "Place/break blocks on faction stations"),

    // --- Ships ---
    SHIP_BUILD        (1L << 13,"Ship Build",            "Place/break blocks on faction-registered ships"),
    SHIP_HELM         (1L << 14,"Ship Helm",             "Pilot faction-registered ships"),
    USE_DRYDOCK       (1L << 15,"Use Drydock",           "Use faction drydock bays for repairs");

    private final long   bit;
    private final String displayName;
    private final String description;

    FactionPermission(long bit, String displayName, String description) {
        this.bit         = bit;
        this.displayName = displayName;
        this.description = description;
    }

    public long   getBit()         { return bit; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    /** Bitmask with every permission set — used for the protected LEADER rank. */
    public static long allPermissions() {
        long mask = 0L;
        for (FactionPermission p : values()) mask |= p.bit;
        return mask;
    }

    /** Bitmask with no permissions set — used for the protected DEFAULT rank. */
    public static long noPermissions() {
        return 0L;
    }
}