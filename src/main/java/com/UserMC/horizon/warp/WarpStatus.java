package com.usermc.horizon.warp;

public enum WarpStatus {
    /** Warp drive offline — no fuel or ship disabled */
    OFFLINE,
    /** Ready to accept a warp destination */
    IDLE,
    /** Counting down to jump — cannot move or be modified */
    CHARGING,
    /** Warp jump in progress */
    JUMPING
}