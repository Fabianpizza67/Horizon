package com.usermc.horizon.station;

import org.bukkit.Location;

import java.util.UUID;

/** One placed station block on a ship. */
public class ShipStation {

    private final UUID        stationId;
    private final UUID        shipId;
    private final StationType type;
    private       Location    location;
    private       boolean     dirty;

    public ShipStation(UUID stationId, UUID shipId, StationType type, Location location) {
        this.stationId = stationId;
        this.shipId    = shipId;
        this.type      = type;
        this.location  = location.clone();
        this.dirty     = false;
    }

    public UUID        getStationId() { return stationId; }
    public UUID        getShipId()    { return shipId; }
    public StationType getType()      { return type; }
    public Location    getLocation()  { return location.clone(); }

    public void setLocation(Location loc) { this.location = loc.clone(); markDirty(); }

    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { dirty = true; }
    public void    clearDirty() { dirty = false; }
}