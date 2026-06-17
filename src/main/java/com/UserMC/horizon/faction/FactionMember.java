package com.usermc.horizon.faction;

import java.util.UUID;

/** A player's membership record inside a faction. */
public class FactionMember {

    private final UUID        playerUUID;
    private final UUID        factionId;
    private       FactionRank rank;
    private       String      playerName; // cached for display
    private final long        joinedAt;
    private       boolean     dirty;

    public FactionMember(UUID playerUUID, UUID factionId, FactionRank rank,
                         String playerName, long joinedAt) {
        this.playerUUID = playerUUID;
        this.factionId  = factionId;
        this.rank       = rank;
        this.playerName = playerName;
        this.joinedAt   = joinedAt;
        this.dirty      = false;
    }

    public UUID        getPlayerUUID() { return playerUUID; }
    public UUID        getFactionId()  { return factionId; }
    public FactionRank getRank()       { return rank; }
    public String      getPlayerName() { return playerName; }
    public long        getJoinedAt()   { return joinedAt; }

    public void setRank(FactionRank r)        { this.rank = r; markDirty(); }
    public void setPlayerName(String name)    { this.playerName = name; }

    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { dirty = true; }
    public void    clearDirty() { dirty = false; }
}