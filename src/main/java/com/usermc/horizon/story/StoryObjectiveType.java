package com.usermc.horizon.story;

import java.util.Random;

/**
 * The mechanical objective underlying a story chapter.
 *
 * Gemini writes the narrative/flavor text, but the actual completion condition
 * is always one of these — hooked into existing Horizon systems
 * (warp, fuel, crew, scanning, helm rotation, mission board).
 *
 * This keeps every AI-generated chapter guaranteed-completable using normal
 * gameplay, regardless of how the narrative itself varies between players.
 */
public enum StoryObjectiveType {

    WARP_JUMP      ("the player must complete a warp jump to any destination using the Navigation console"),
    REFUEL         ("the player must refuel their ship's Dilithium reserves at the Engineering console"),
    HIRE_CREW      ("the player must recruit a new crew member aboard their ship"),
    SCAN_SHIP      ("the player must perform a full structural scan of their ship by interacting with the Ship Core"),
    ROTATE_SHIP    ("the player must perform a course correction — a 90-degree turn — at the Helm"),
    COMPLETE_MISSION("the player must complete a mission from their ship's Mission Terminal");

    private final String description;

    StoryObjectiveType(String description) {
        this.description = description;
    }

    /** Human/AI-readable description of what the player needs to do, for the Gemini prompt. */
    public String getDescription() { return description; }

    public static StoryObjectiveType random(Random rng) {
        StoryObjectiveType[] values = values();
        return values[rng.nextInt(values.length)];
    }

    /** Avoid repeating the same objective type as the previous chapter, where possible. */
    public static StoryObjectiveType randomExcluding(Random rng, StoryObjectiveType exclude) {
        StoryObjectiveType[] values = values();
        if (exclude == null || values.length <= 1) return random(rng);
        StoryObjectiveType result;
        do {
            result = values[rng.nextInt(values.length)];
        } while (result == exclude);
        return result;
    }
}