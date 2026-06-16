package com.usermc.horizon.story;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One full story arc for a single player — a self-contained, AI-generated
 * mini-narrative (4-6 chapters) with a premise established in chapter 1 and
 * a resolution in the final chapter's completion text.
 *
 * Only one ACTIVE arc exists per player at a time. After completion, a new
 * arc (with a brand new premise) can begin after a cooldown — giving a fresh
 * story each "season" rather than one infinite thread.
 */
public class StoryArc {

    private final UUID arcId;
    private final UUID playerUUID;

    /** Set once chapter 1 generation completes. Empty until then. */
    private String premise;

    private int currentChapterNumber;
    private final int totalChapters;
    private StoryStatus status;

    /** All generated chapters so far, in order. */
    private final List<StoryChapter> chapters = new ArrayList<>();

    private boolean dirty;

    public StoryArc(UUID arcId, UUID playerUUID, String premise,
                    int currentChapterNumber, int totalChapters, StoryStatus status) {
        this.arcId = arcId;
        this.playerUUID = playerUUID;
        this.premise = premise == null ? "" : premise;
        this.currentChapterNumber = currentChapterNumber;
        this.totalChapters = totalChapters;
        this.status = status;
        this.dirty = false;
    }

    public UUID getArcId()     { return arcId; }
    public UUID getPlayerUUID(){ return playerUUID; }

    public String getPremise() { return premise; }
    public void setPremise(String p) { this.premise = p; markDirty(); }

    public int getCurrentChapterNumber() { return currentChapterNumber; }
    public void setCurrentChapterNumber(int n) { this.currentChapterNumber = n; markDirty(); }

    public int getTotalChapters() { return totalChapters; }

    public StoryStatus getStatus() { return status; }
    public void setStatus(StoryStatus s) { this.status = s; markDirty(); }

    public List<StoryChapter> getChapters() { return chapters; }
    public void addChapter(StoryChapter chapter) { chapters.add(chapter); }

    /** The chapter the player is currently working on, or null if not yet generated. */
    public StoryChapter getCurrentChapter() {
        return chapters.stream()
                .filter(c -> c.getChapterNumber() == currentChapterNumber)
                .findFirst().orElse(null);
    }

    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { this.dirty = true; }
    public void    clearDirty() { this.dirty = false; }
}