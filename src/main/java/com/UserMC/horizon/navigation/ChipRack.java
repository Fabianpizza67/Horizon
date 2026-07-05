package com.usermc.horizon.navigation;

import org.bukkit.Location;

import java.util.UUID;

/**
 * A standalone Chip Rack block — a dedicated 27-slot display/storage unit
 * for navigation chips, separate from any Station system. Cannot hold
 * anything except navigation chips (enforced by NavigationChipListener).
 *
 * Rack position moves and rotates along with its ship, same principle as
 * Station blocks, but tracked independently here since this session can't
 * safely touch the existing Station infrastructure.
 */
public class ChipRack {

    public static final int CAPACITY = 27;

    private final UUID     rackId;
    private final UUID     shipId;
    private       Location location;
    private       boolean  dirty;

    public ChipRack(UUID rackId, UUID shipId, Location location) {
        this.rackId   = rackId;
        this.shipId   = shipId;
        this.location = location.clone();
        this.dirty    = false;
    }

    public UUID     getRackId()  { return rackId; }
    public UUID     getShipId()  { return shipId; }
    public Location getLocation(){ return location.clone(); }

    public void setLocation(Location loc) { this.location = loc.clone(); markDirty(); }

    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { dirty = true; }
    public void    clearDirty() { dirty = false; }
}