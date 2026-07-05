package com.usermc.horizon.faction;

import java.util.UUID;

/**
 * A player's membership record inside a faction.
 *
 * Now references a rank by UUID (rankId) rather than a hardcoded enum value,
 * since ranks are fully custom per-faction. Use FactionManager/Faction to
 * resolve rankId → FactionRankDef when you need to check permissions.
 */
public class FactionMember {

    private final UUID   playerUUID;
    private final UUID   factionId;
    private       UUID   rankId;
    private       String playerName; // cached for display
    private final long   joinedAt;
    private       boolean dirty;

    public FactionMember(UUID playerUUID, UUID factionId, UUID rankId,
                         String playerName, long joinedAt) {
        this.playerUUID = playerUUID;
        this.factionId  = factionId;
        this.rankId     = rankId;
        this.playerName = playerName;
        this.joinedAt   = joinedAt;
        this.dirty      = false;
    }

    public UUID   getPlayerUUID() { return playerUUID; }
    public UUID   getFactionId()  { return factionId; }
    public UUID   getRankId()     { return rankId; }
    public String getPlayerName() { return playerName; }
    public long   getJoinedAt()   { return joinedAt; }

    public void setRankId(UUID rankId)     { this.rankId = rankId; markDirty(); }
    public void setPlayerName(String name) { this.playerName = name; }

    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { dirty = true; }
    public void    clearDirty() { dirty = false; }
}