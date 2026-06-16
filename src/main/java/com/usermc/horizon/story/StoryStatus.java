package com.usermc.horizon.story;

/** Lifecycle status for both story arcs and individual chapters. */
public enum StoryStatus {
    /** Currently in progress — chapter's objective is awaiting completion, or arc is ongoing. */
    ACTIVE,
    /** Finished successfully. */
    COMPLETED
}