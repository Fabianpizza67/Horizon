package com.usermc.horizon.story;

import java.util.UUID;

/**
 * A single chapter of a story arc.
 *
 * All narrative fields (title, narrative, objectiveFlavor, completionText) are
 * generated fresh by Gemini for every arc — different every playthrough.
 * The mechanical objective (type/progress/required) is plugin-controlled so
 * every chapter is always completable through normal gameplay.
 */
public class StoryChapter {

    private final UUID    chapterId;
    private final UUID    arcId;
    private final int     chapterNumber;

    private String title;
    private String narrative;
    private String objectiveFlavor;
    private String completionText;

    private final StoryObjectiveType objectiveType;
    private int progress;
    private final int required;

    private final int rewardCredits;
    private final int rewardXp;

    private StoryStatus status;
    private boolean dirty;

    public StoryChapter(UUID chapterId, UUID arcId, int chapterNumber,
                        String title, String narrative, String objectiveFlavor, String completionText,
                        StoryObjectiveType objectiveType, int progress, int required,
                        int rewardCredits, int rewardXp, StoryStatus status) {
        this.chapterId      = chapterId;
        this.arcId          = arcId;
        this.chapterNumber  = chapterNumber;
        this.title          = title;
        this.narrative      = narrative;
        this.objectiveFlavor= objectiveFlavor;
        this.completionText = completionText;
        this.objectiveType  = objectiveType;
        this.progress       = progress;
        this.required       = required;
        this.rewardCredits  = rewardCredits;
        this.rewardXp       = rewardXp;
        this.status         = status;
        this.dirty          = false;
    }

    public UUID   getChapterId()     { return chapterId; }
    public UUID   getArcId()         { return arcId; }
    public int    getChapterNumber() { return chapterNumber; }

    public String getTitle()           { return title; }
    public String getNarrative()       { return narrative; }
    public String getObjectiveFlavor() { return objectiveFlavor; }
    public String getCompletionText()  { return completionText; }

    public StoryObjectiveType getObjectiveType() { return objectiveType; }
    public int getProgress() { return progress; }
    public int getRequired() { return required; }

    public int getRewardCredits() { return rewardCredits; }
    public int getRewardXp()      { return rewardXp; }

    public StoryStatus getStatus() { return status; }
    public void setStatus(StoryStatus s) { this.status = s; markDirty(); }

    public void incrementProgress() {
        this.progress = Math.min(required, progress + 1);
        markDirty();
    }

    public boolean isComplete() { return progress >= required; }

    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { this.dirty = true; }
    public void    clearDirty() { this.dirty = false; }
}