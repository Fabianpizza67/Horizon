package com.usermc.horizon.mission;

public enum MissionStatus {
    AVAILABLE,   // on the board, not taken
    ACTIVE,      // accepted by a player, in progress
    COMPLETED,   // successfully finished
    EXPIRED,     // timed out before completion or player left
    FAILED       // future: failed due to conditions not met
}