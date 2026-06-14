package com.usermc.horizon.mission;

import java.util.UUID;

/** One mission on the board or in a player's active slot. */
public class Mission {

    private final UUID        missionId;
    private final MissionType type;
    private final String      title;
    private final String      description;
    private final UUID        targetBeaconId;
    private final String      targetBeaconName;
    private final int         difficulty;   // 1 = easy, 2 = medium, 3 = hard
    private final int         rewardEc;
    private final long        rewardXp;
    private final long        expiresAt;    // epoch ms, 0 = never

    private       MissionStatus status;
    private       UUID          acceptedBy;   // null if AVAILABLE
    private       long          acceptedAt;

    public Mission(UUID missionId, MissionType type, String title, String description,
                   UUID targetBeaconId, String targetBeaconName,
                   int difficulty, int rewardEc, long rewardXp, long expiresAt) {
        this.missionId        = missionId;
        this.type             = type;
        this.title            = title;
        this.description      = description;
        this.targetBeaconId   = targetBeaconId;
        this.targetBeaconName = targetBeaconName;
        this.difficulty       = difficulty;
        this.rewardEc         = rewardEc;
        this.rewardXp         = rewardXp;
        this.expiresAt        = expiresAt;
        this.status           = MissionStatus.AVAILABLE;
    }

    // Getters
    public UUID        getMissionId()       { return missionId; }
    public MissionType getType()            { return type; }
    public String      getTitle()           { return title; }
    public String      getDescription()     { return description; }
    public UUID        getTargetBeaconId()  { return targetBeaconId; }
    public String      getTargetBeaconName(){ return targetBeaconName; }
    public int         getDifficulty()      { return difficulty; }
    public int         getRewardEc()        { return rewardEc; }
    public long        getRewardXp()        { return rewardXp; }
    public long        getExpiresAt()       { return expiresAt; }
    public MissionStatus getStatus()        { return status; }
    public UUID        getAcceptedBy()      { return acceptedBy; }
    public long        getAcceptedAt()      { return acceptedAt; }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    public boolean isAvailable() { return status == MissionStatus.AVAILABLE && !isExpired(); }

    // State changes
    public void accept(UUID playerUUID) {
        this.status     = MissionStatus.ACTIVE;
        this.acceptedBy = playerUUID;
        this.acceptedAt = System.currentTimeMillis();
    }

    public void complete() { this.status = MissionStatus.COMPLETED; }
    public void expire()   { this.status = MissionStatus.EXPIRED; }

    /** Stars string for difficulty display. */
    public String difficultyStars() {
        return "★".repeat(difficulty) + "☆".repeat(3 - difficulty);
    }
}