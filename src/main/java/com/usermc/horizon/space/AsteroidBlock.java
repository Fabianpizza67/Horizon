package com.usermc.horizon.space;

import org.bukkit.Material;

import java.util.UUID;

/**
 * A single block belonging to an asteroid cluster.
 *
 * Tracks its own independent respawn timer — when mined, minedAt is set
 * and the block becomes AIR in the world. Once minedAt + respawnDelayMs
 * has passed, the AsteroidManager regenerates it with a freshly randomized
 * material (per ClusterType.randomMaterial).
 */
public class AsteroidBlock {

    private final UUID   blockId;
    private final UUID   clusterId;
    private final int    relX, relY, relZ; // offset from cluster center

    private Material currentMaterial;
    private boolean   mined;
    private long      minedAt;       // epoch millis, 0 if not mined
    private long      respawnAt;     // epoch millis, 0 if not mined
    private boolean   dirty;

    public AsteroidBlock(UUID blockId, UUID clusterId, int relX, int relY, int relZ,
                         Material currentMaterial, boolean mined, long minedAt, long respawnAt) {
        this.blockId         = blockId;
        this.clusterId       = clusterId;
        this.relX            = relX;
        this.relY            = relY;
        this.relZ            = relZ;
        this.currentMaterial = currentMaterial;
        this.mined           = mined;
        this.minedAt         = minedAt;
        this.respawnAt       = respawnAt;
        this.dirty           = false;
    }

    public UUID getBlockId()   { return blockId; }
    public UUID getClusterId() { return clusterId; }
    public int  getRelX()      { return relX; }
    public int  getRelY()      { return relY; }
    public int  getRelZ()      { return relZ; }

    public Material getCurrentMaterial() { return currentMaterial; }
    public boolean  isMined()            { return mined; }
    public long     getRespawnAt()       { return respawnAt; }

    /** True if this block is mined but its respawn timer has elapsed. */
    public boolean isReadyToRespawn() {
        return mined && System.currentTimeMillis() >= respawnAt;
    }

    /** Mark this block as mined right now, scheduling its respawn. */
    public void markMined(long respawnDelayMs) {
        this.mined     = true;
        this.minedAt   = System.currentTimeMillis();
        this.respawnAt = minedAt + respawnDelayMs;
        markDirty();
    }

    /** Regenerate this block with a freshly randomized material. */
    public void respawn(Material newMaterial) {
        this.currentMaterial = newMaterial;
        this.mined           = false;
        this.minedAt         = 0;
        this.respawnAt       = 0;
        markDirty();
    }

    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { dirty = true; }
    public void    clearDirty() { dirty = false; }
}