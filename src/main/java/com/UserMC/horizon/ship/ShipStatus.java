package com.usermc.horizon.ship;

public enum ShipStatus {
    /** Docked at a station — safe, modifications allowed */
    DOCKED,
    /** In open space — movement allowed */
    FLYING,
    /** Executing a warp jump */
    IN_WARP,
    /** Damaged/powerless — movement denied */
    DISABLED
}