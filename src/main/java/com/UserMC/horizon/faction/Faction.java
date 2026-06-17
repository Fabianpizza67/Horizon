package com.usermc.horizon.faction;

import java.util.*;

/**
 * A player-created faction in Horizon.
 *
 * Contains its own member roster, bank balance, and diplomatic relations
 * to other factions. Persisted to MariaDB via FactionDAO.
 */
public class Faction {

    private final UUID   factionId;
    private       String name;
    private       String description;
    private       UUID   leaderUUID;
    private       long   bankBalance;
    private       boolean dirty;

    /** Members keyed by player UUID — includes the leader. */
    private final Map<UUID, FactionMember> members = new LinkedHashMap<>();

    /**
     * Relations to other factions. Keyed by other faction's UUID.
     * Only CONFIRMED (non-neutral) relations are stored here.
     * Pending proposals live in FactionManager.
     */
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

    /** Returns false if there are insufficient funds. */
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
    public Map<UUID, FactionMember> getMembers()     { return Collections.unmodifiableMap(members); }
    public int getMemberCount()                       { return members.size(); }

    public FactionMember getMember(UUID uuid)         { return members.get(uuid); }
    public boolean hasMember(UUID uuid)               { return members.containsKey(uuid); }
    public boolean isLeader(UUID uuid)                { return uuid.equals(leaderUUID); }

    public void addMember(FactionMember member) {
        members.put(member.getPlayerUUID(), member);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    // --- Relations ---
    public FactionRelation getRelation(UUID otherFactionId) {
        return relations.getOrDefault(otherFactionId, FactionRelation.NEUTRAL);
    }

    public void setRelation(UUID otherFactionId, FactionRelation relation) {
        if (relation == FactionRelation.NEUTRAL) {
            relations.remove(otherFactionId);
        } else {
            relations.put(otherFactionId, relation);
        }
        markDirty();
    }

    public Map<UUID, FactionRelation> getAllRelations() {
        return Collections.unmodifiableMap(relations);
    }

    // --- Dirty ---
    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { dirty = true; }
    public void    clearDirty() { dirty = false; }
}