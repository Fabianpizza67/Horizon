package com.usermc.horizon.rank;

import java.util.UUID;

/** Stores progression data for one player. */
public class CaptainProfile {

    private final UUID        playerUUID;
    private       String      playerName;
    private       CaptainRank rank;
    private       long        experience;
    private       int         missionsCompleted;
    private       long        totalWarpDistance;  // blocks
    private       boolean     dirty;

    public CaptainProfile(UUID playerUUID, String playerName, CaptainRank rank,
                          long experience, int missionsCompleted, long totalWarpDistance) {
        this.playerUUID       = playerUUID;
        this.playerName       = playerName;
        this.rank             = rank;
        this.experience       = experience;
        this.missionsCompleted= missionsCompleted;
        this.totalWarpDistance= totalWarpDistance;
        this.dirty            = false;
    }

    public UUID        getPlayerUUID()        { return playerUUID; }
    public String      getPlayerName()        { return playerName; }
    public CaptainRank getRank()              { return rank; }
    public long        getExperience()        { return experience; }
    public int         getMissionsCompleted() { return missionsCompleted; }
    public long        getTotalWarpDistance() { return totalWarpDistance; }
    public boolean     isDirty()              { return dirty; }

    public void setPlayerName(String n)        { this.playerName  = n; markDirty(); }
    public void setRank(CaptainRank r)         { this.rank        = r; markDirty(); }
    public void addMissionsCompleted(int n)    { this.missionsCompleted += n; markDirty(); }
    public void addWarpDistance(long d)        { this.totalWarpDistance += d; markDirty(); }

    public void markDirty()  { this.dirty = true; }
    public void clearDirty() { this.dirty = false; }

    /**
     * Add XP and return true if the player ranked up.
     */
    public boolean addExperience(long xp) {
        this.experience += xp;
        CaptainRank newRank = CaptainRank.fromXp(this.experience);
        if (newRank != this.rank) {
            this.rank = newRank;
            markDirty();
            return true; // ranked up
        }
        markDirty();
        return false;
    }

    /** XP needed to reach the next rank. */
    public long xpToNextRank() {
        if (rank.isMaxRank()) return 0;
        return rank.next().getXpRequired() - experience;
    }
}