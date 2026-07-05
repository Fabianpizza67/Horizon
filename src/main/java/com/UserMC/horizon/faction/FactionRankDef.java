package com.usermc.horizon.faction;

import java.util.UUID;

/**
 * A custom rank definition belonging to one faction.
 *
 * Every faction always has exactly two protected ranks, created automatically
 * when the faction is founded and never deletable:
 *
 *   LEADER  — isLeaderRank=true.  Always treated as having every permission
 *             regardless of its stored bitmask. Can be renamed. Can be held
 *             by multiple players. At least one member must hold a leader
 *             rank at all times (enforced in FactionManager).
 *
 *   DEFAULT — isDefaultRank=true. Always starts with zero permissions but
 *             its permission bitmask CAN be edited by the leader if they
 *             want new recruits to start with some access. Can be renamed.
 *             Automatically assigned to new members unless the leader has
 *             changed which rank new members default to (see Faction#
 *             getNewMemberRankId).
 *
 * All other ranks are fully custom — name, permissions, and hierarchy
 * position are entirely up to the faction leader.
 *
 * hierarchyPosition is just a sort order for display (e.g. in the members
 * list) — lower numbers show first/"higher". It has no mechanical effect
 * on permissions; permissions are purely the bitmask.
 */
public class FactionRankDef {

    private final UUID    rankId;
    private final UUID    factionId;
    private       String  name;
    private       long    permissions;
    private       int     hierarchyPosition;
    private final boolean isLeaderRank;
    private final boolean isDefaultRank;
    private       boolean dirty;

    public FactionRankDef(UUID rankId, UUID factionId, String name, long permissions,
                          int hierarchyPosition, boolean isLeaderRank, boolean isDefaultRank) {
        this.rankId            = rankId;
        this.factionId         = factionId;
        this.name              = name;
        this.permissions       = permissions;
        this.hierarchyPosition = hierarchyPosition;
        this.isLeaderRank      = isLeaderRank;
        this.isDefaultRank     = isDefaultRank;
        this.dirty             = false;
    }

    // --- Identity ---
    public UUID getRankId()    { return rankId; }
    public UUID getFactionId() { return factionId; }

    // --- Name (both protected ranks CAN be renamed) ---
    public String getName() { return name; }
    public void   setName(String name) { this.name = name; markDirty(); }

    // --- Permissions ---

    /**
     * True if this rank has the given permission.
     * The LEADER rank always returns true regardless of its stored bitmask —
     * this guarantees a faction leader can never accidentally lock themselves
     * out by editing their own rank's permission bits.
     */
    public boolean hasPermission(FactionPermission perm) {
        if (isLeaderRank) return true;
        return (permissions & perm.getBit()) != 0;
    }

    public long getPermissionsBitmask() { return permissions; }

    public void setPermission(FactionPermission perm, boolean granted) {
        if (isLeaderRank) return; // no-op — leader is always all-permissions
        if (granted) permissions |= perm.getBit();
        else         permissions &= ~perm.getBit();
        markDirty();
    }

    public void setPermissionsBitmask(long mask) {
        if (isLeaderRank) return; // no-op
        this.permissions = mask;
        markDirty();
    }

    // --- Hierarchy ---
    public int  getHierarchyPosition()      { return hierarchyPosition; }
    public void setHierarchyPosition(int p) { this.hierarchyPosition = p; markDirty(); }

    // --- Protection flags ---
    public boolean isLeaderRank()  { return isLeaderRank; }
    public boolean isDefaultRank() { return isDefaultRank; }
    public boolean isProtected()   { return isLeaderRank || isDefaultRank; }

    // --- Dirty ---
    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { dirty = true; }
    public void    clearDirty() { dirty = false; }
}