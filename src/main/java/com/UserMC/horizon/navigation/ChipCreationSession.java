package com.usermc.horizon.navigation;

import com.usermc.horizon.ship.Ship;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Tracks one in-progress navigation chip calculation.
 *
 * The chip always captures the coordinates the ship was at when creation
 * STARTED, even if the ship drifts a little afterward — the computer locked
 * onto that location the moment calculation began.
 *
 * Movement handling (see ChipCreationManager):
 *   - Small cumulative drift (under the cancel threshold) simply adds time
 *     to the remaining countdown, representing recalculation overhead.
 *   - Cumulative displacement beyond the threshold, OR any ship rotation,
 *     cancels the creation outright and refunds nothing (materials already
 *     consumed at start).
 */
public class ChipCreationSession {

    private final UUID     playerUUID;
    private final UUID     shipId;
    private final ChipType type;
    private final String   destinationName;
    private final Location capturedDestination; // frozen at session start

    private final Location originAtStart; // ship core location when creation began
    private long   remainingTicks;
    private double cumulativeDrift; // blocks moved so far, small-nudge tracking

    public ChipCreationSession(UUID playerUUID, Ship ship, ChipType type,
                               String destinationName, long initialDurationTicks) {
        this.playerUUID          = playerUUID;
        this.shipId              = ship.getShipId();
        this.type                = type;
        this.destinationName     = destinationName;
        this.capturedDestination = ship.getCoreLocation();
        this.originAtStart       = ship.getCoreLocation();
        this.remainingTicks      = initialDurationTicks;
        this.cumulativeDrift     = 0;
    }

    public UUID     getPlayerUUID()          { return playerUUID; }
    public UUID     getShipId()              { return shipId; }
    public ChipType getType()                { return type; }
    public String   getDestinationName()     { return destinationName; }
    public Location getCapturedDestination() { return capturedDestination.clone(); }
    public Location getOriginAtStart()       { return originAtStart.clone(); }

    public long    getRemainingTicks()      { return remainingTicks; }
    public void    tick()                   { remainingTicks--; }
    public void    tickBy(long amount)      { remainingTicks -= amount; }
    public boolean isComplete()             { return remainingTicks <= 0; }

    public void extendBy(long ticks) { remainingTicks += ticks; }

    public double getCumulativeDrift()      { return cumulativeDrift; }
    public void   addDrift(double blocks)   { cumulativeDrift += blocks; }
}