package com.usermc.horizon.warp;

import org.bukkit.Location;

import java.util.UUID;

/**
 * A named destination that ships can warp to.
 *
 * Beacons are placed by admins (or generated procedurally in future systems)
 * and represent points of interest in Deep Space: stations, anomalies,
 * unexplored regions, and so on.
 */
public class WarpBeacon {

    private final UUID     beaconId;
    private       String   name;
    private final Location location;
    private       String   description;
    private final boolean  adminOnly;

    public WarpBeacon(UUID beaconId, String name, Location location,
                      String description, boolean adminOnly) {
        this.beaconId    = beaconId;
        this.name        = name;
        this.location    = location.clone();
        this.description = description;
        this.adminOnly   = adminOnly;
    }

    public UUID     getBeaconId()         { return beaconId; }
    public String   getName()             { return name; }
    public Location getLocation()         { return location.clone(); }
    public String   getDescription()      { return description; }
    public boolean  isAdminOnly()         { return adminOnly; }

    public void setName(String name)           { this.name = name; }
    public void setDescription(String desc)    { this.description = desc; }

    /**
     * 3D Euclidean distance from another location to this beacon.
     * Returns MAX_VALUE if they are in different worlds.
     */
    public double distanceTo(Location from) {
        if (from.getWorld() == null || !from.getWorld().equals(location.getWorld()))
            return Double.MAX_VALUE;
        return from.distance(location);
    }
}