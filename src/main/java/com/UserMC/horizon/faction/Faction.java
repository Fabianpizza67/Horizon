package com.usermc.horizon.faction;

import java.util.*;

/**
 * A player-created faction in Horizon.
 *
 * Now owns its own set of custom FactionRankDef objects in addition to
 * members and relations. Every faction always has exactly one LEADER rank
 * and one DEFAULT rank (both protected, see FactionRankDef), plus however
 * many custom ranks the leader has created.
 */
public class Faction {

    private final UUID   factionId;
    private       String name;
    private       String description;
    private       UUID   leaderUUID; // the player who founded it / current primary owner for display
    private       long   bankBalance;
    private       boolean dirty;

    /** Members keyed by player UUID — includes all leaders. */
    private final Map<UUID, FactionMember> members = new LinkedHashMap<>();

    /** Ranks keyed by rank UUID. Always contains exactly one leader rank and one default rank. */
    private final Map<UUID, FactionRankDef> ranks = new LinkedHashMap<>();

    /** Which rank new members are assigned on join. Defaults to the protected DEFAULT rank's ID. */
    private UUID newMemberRankId;

    /** Confirmed relations to other factions. Keyed by other faction's UUID. */
    private final Map<UUID, FactionRelation> relations = new HashMap<>();

    private final long createdAt;

    public Faction(UUID factionId, String name, String description,
                   UUID leaderUUID, long bankBalance, long createdAt) {
        this.factionId   = factionId;
        this.name        = name;
        this.description = description;
        this.leaderUUID  = leaderUUID;
        this.bankBalance = bankBalance;
        this.createdAt   = createdAt;
        this.dirty       = false;
    }

    // --- Identity ---
    public UUID   getFactionId()   { return factionId; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public UUID   getLeaderUUID()  { return leaderUUID; }
    public long   getCreatedAt()   { return createdAt; }

    public void setName(String n)        { this.name = n; markDirty(); }
    public void setDescription(String d) { this.description = d; markDirty(); }
    public void setLeaderUUID(UUID uuid) { this.leaderUUID = uuid; markDirty(); }

    // --- Bank ---
    public long getBankBalance() { return bankBalance; }

    public boolean deductBank(long amount) {
        if (bankBalance < amount) return false;
        bankBalance -= amount;
        markDirty();
        return true;
    }

    public void depositBank(long amount) {
        bankBalance += Math.max(0, amount);
        markDirty();
    }

    // --- Members ---
    public Map<UUID, FactionMember> getMembers() { return Collections.unmodifiableMap(members); }
    public int  getMemberCount()                  { return members.size(); }
    public FactionMember getMember(UUID uuid)     { return members.get(uuid); }
    public boolean hasMember(UUID uuid)           { return members.containsKey(uuid); }

    public void addMember(FactionMember member) { members.put(member.getPlayerUUID(), member); }
    public void removeMember(UUID uuid)         { members.remove(uuid); }

    /** True if this player holds a rank flagged isLeaderRank. Multiple players can be true. */
    public boolean isLeader(UUID uuid) {
        FactionMember m = members.get(uuid);
        if (m == null) return false;
        FactionRankDef rank = ranks.get(m.getRankId());
        return rank != null && rank.isLeaderRank();
    }

    /** Count of members currently holding a leader rank. */
    public int countLeaders() {
        int count = 0;
        for (FactionMember m : members.values()) if (isLeader(m.getPlayerUUID())) count++;
        return count;
    }

    // --- Ranks ---

    public Collection<FactionRankDef> getAllRanks() {
        return ranks.values().stream()
                .sorted(Comparator.comparingInt(FactionRankDef::getHierarchyPosition))
                .toList();
    }

    public FactionRankDef getRank(UUID rankId) { return ranks.get(rankId); }

    public FactionRankDef getRankByName(String name) {
        for (FactionRankDef r : ranks.values())
            if (r.getName().equalsIgnoreCase(name)) return r;
        return null;
    }

    public FactionRankDef getLeaderRank() {
        for (FactionRankDef r : ranks.values()) if (r.isLeaderRank()) return r;
        return null;
    }

    public FactionRankDef getDefaultRank() {
        for (FactionRankDef r : ranks.values()) if (r.isDefaultRank()) return r;
        return null;
    }

    public void addRank(FactionRankDef rank) { ranks.put(rank.getRankId(), rank); }

    /** Removes a custom rank. Refuses to remove protected ranks — check isProtected() first. */
    public boolean removeRank(UUID rankId) {
        FactionRankDef rank = ranks.get(rankId);
        if (rank == null || rank.isProtected()) return false;
        ranks.remove(rankId);
        return true;
    }

    /** Get the permission rank that should be assigned to a brand new member. */
    public UUID getNewMemberRankId() {
        return newMemberRankId != null ? newMemberRankId
                : (getDefaultRank() != null ? getDefaultRank().getRankId() : null);
    }

    public void setNewMemberRankId(UUID rankId) {
        this.newMemberRankId = rankId;
        markDirty();
    }

    /** Resolve a member's effective FactionRankDef, or null if data is inconsistent. */
    public FactionRankDef getMemberRank(UUID playerUUID) {
        FactionMember m = members.get(playerUUID);
        if (m == null) return null;
        return ranks.get(m.getRankId());
    }

    public boolean memberHasPermission(UUID playerUUID, FactionPermission perm) {
        FactionRankDef rank = getMemberRank(playerUUID);
        return rank != null && rank.hasPermission(perm);
    }

    // --- Relations ---
    public FactionRelation getRelation(UUID otherFactionId) {
        return relations.getOrDefault(otherFactionId, FactionRelation.NEUTRAL);
    }

    public void setRelation(UUID otherFactionId, FactionRelation relation) {
        if (relation == FactionRelation.NEUTRAL) relations.remove(otherFactionId);
        else relations.put(otherFactionId, relation);
        markDirty();
    }

    public Map<UUID, FactionRelation> getAllRelations() { return Collections.unmodifiableMap(relations); }

    // --- Dirty ---
    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { dirty = true; }
    public void    clearDirty() { dirty = false; }
}