package com.usermc.horizon.ship;

import com.usermc.horizon.warp.WarpStatus;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Ship {

    private final UUID    shipId;
    private String        name;
    private final UUID    ownerUUID;
    private ShipClass     shipClass;
    private Location      coreLocation;
    private float         heading;      // Minecraft yaw: 0=South, 90=West, 180=North, 270=East
    private ShipStatus    status;
    private WarpStatus    warpStatus;
    private ShipStructure structure;
    private boolean       structureDirty;
    private boolean       dirty;
    private boolean       processing;
    private int           fuelLevel;

    private final Set<UUID> passengers = new HashSet<>();
    private final long createdAt;

    public Ship(UUID shipId, String name, UUID ownerUUID, ShipClass shipClass,
                Location coreLocation, float heading) {
        this.shipId         = shipId;
        this.name           = name;
        this.ownerUUID      = ownerUUID;
        this.shipClass      = shipClass;
        this.coreLocation   = coreLocation.clone();
        this.heading        = heading;
        this.status         = ShipStatus.DOCKED;
        this.warpStatus     = WarpStatus.IDLE;
        this.structureDirty = true;
        this.dirty          = false;
        this.processing     = false;
        this.fuelLevel      = 0;
        this.createdAt      = System.currentTimeMillis();
    }

    // --- Identity ---
    public UUID   getShipId()    { return shipId; }
    public String getName()      { return name; }
    public UUID   getOwnerUUID() { return ownerUUID; }
    public long   getCreatedAt() { return createdAt; }
    public void   setName(String n) { this.name = n; markDirty(); }

    // --- Class & Status ---
    public ShipClass  getShipClass()  { return shipClass; }
    public ShipStatus getStatus()     { return status; }
    public WarpStatus getWarpStatus() { return warpStatus; }
    public void setShipClass(ShipClass sc)   { this.shipClass  = sc; markDirty(); }
    public void setStatus(ShipStatus s)      { this.status     = s;  markDirty(); }
    public void setWarpStatus(WarpStatus ws) { this.warpStatus = ws; markDirty(); }

    // --- Location & Heading ---
    public Location getCoreLocation()          { return coreLocation.clone(); }
    public float    getHeading()               { return heading; }
    public void     setCoreLocation(Location l){ this.coreLocation = l.clone(); markDirty(); }
    public void     setHeading(float h)        { this.heading = ((h % 360) + 360) % 360; markDirty(); }

    // --- Structure ---
    public ShipStructure getStructure()      { return structure; }
    public boolean       isStructureDirty()  { return structureDirty; }
    public void          markStructureDirty(){ this.structureDirty = true; }

    public void setStructure(ShipStructure s) {
        this.structure      = s;
        this.structureDirty = false;
        markDirty();
    }

    public boolean isReady() { return structure != null && !structureDirty; }

    // --- Dirty ---
    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { this.dirty = true; }
    public void    clearDirty() { this.dirty = false; }

    // --- Processing ---
    public boolean isProcessing()           { return processing; }
    public void    setProcessing(boolean p) { this.processing = p; }

    // --- Fuel ---
    public int  getFuelLevel()      { return fuelLevel; }
    public void setFuelLevel(int f) { this.fuelLevel = Math.max(0, f); markDirty(); }

    // --- Passengers ---
    public Set<UUID> getPassengers()            { return Collections.unmodifiableSet(passengers); }
    public void      addPassenger(UUID uuid)    { passengers.add(uuid); }
    public void      removePassenger(UUID uuid) { passengers.remove(uuid); }
    public boolean   isPassenger(UUID uuid)     { return passengers.contains(uuid); }
    public boolean   isOwner(Player player)     { return player.getUniqueId().equals(ownerUUID); }

    // --- Bounding box ---
    public boolean isWithinBounds(Location loc) {
        if (structure == null) return false;
        if (!loc.getWorld().equals(coreLocation.getWorld())) return false;
        int rx = loc.getBlockX() - coreLocation.getBlockX();
        int ry = loc.getBlockY() - coreLocation.getBlockY();
        int rz = loc.getBlockZ() - coreLocation.getBlockZ();
        return rx >= structure.getMinX() - 1 && rx <= structure.getMaxX() + 1
                && ry >= structure.getMinY() - 1 && ry <= structure.getMaxY() + 1
                && rz >= structure.getMinZ() - 1 && rz <= structure.getMaxZ() + 1;
    }

    // --- Speed multiplier ---
    public double getSpeedMultiplier() {
        if (structure == null) return 1.0;
        int blocks = structure.getBlockCount();
        if (blocks <= shipClass.getSoftLimit()) return 1.0;
        double excess = (double)(blocks - shipClass.getSoftLimit())
                / (double)(shipClass.getHardLimit() - shipClass.getSoftLimit());
        return Math.max(0.5, 1.0 - 0.5 * excess);
    }

    // -----------------------------------------------------------------------
    // Heading-relative direction vectors
    // Heading follows Minecraft yaw: 0=South(+Z), 90=West(-X), 180=North(-Z), 270=East(+X)
    // -----------------------------------------------------------------------

    /**
     * Returns the (dx, dy, dz) unit vector the ship is currently facing.
     * Snaps to the nearest cardinal direction.
     */
    public int[] getForwardVector() {
        int h = Math.floorMod(Math.round(heading), 360);
        if (h < 45 || h >= 315) return new int[]{ 0, 0,  1};  // South
        if (h < 135)            return new int[]{-1, 0,  0};  // West
        if (h < 225)            return new int[]{ 0, 0, -1};  // North
        return                         new int[]{ 1, 0,  0};  // East
    }

    /**
     * Right of the ship's facing direction.
     * Formula: (dx, dz) → (-dz, dx)
     * Verified: facing South(0,0,1) → right = West(-1,0,0) ✓
     */
    public int[] getRightVector() {
        int[] f = getForwardVector();
        return new int[]{-f[2], 0, f[0]};
    }

    /** Left of the ship's facing direction (inverse of right). */
    public int[] getLeftVector() {
        int[] r = getRightVector();
        return new int[]{-r[0], 0, -r[2]};
    }

    /**
     * Resolve a relative direction to an absolute MoveDirection based on current heading.
     * UP and DOWN are always absolute.
     */
    public MoveDirection resolveDirection(String relative) {
        int[] vec = switch (relative.toUpperCase()) {
            case "FORWARD"      -> getForwardVector();
            case "BACKWARD"     -> { int[] f = getForwardVector(); yield new int[]{-f[0], 0, -f[2]}; }
            case "STRAFE_LEFT"  -> getLeftVector();
            case "STRAFE_RIGHT" -> getRightVector();
            case "UP"           -> new int[]{0, 1, 0};
            case "DOWN"         -> new int[]{0, -1, 0};
            default             -> getForwardVector();
        };
        return vectorToDirection(vec);
    }

    private MoveDirection vectorToDirection(int[] v) {
        if (v[1] > 0) return MoveDirection.UP;
        if (v[1] < 0) return MoveDirection.DOWN;
        if (v[2] > 0) return MoveDirection.SOUTH;
        if (v[2] < 0) return MoveDirection.NORTH;
        if (v[0] > 0) return MoveDirection.EAST;
        return MoveDirection.WEST;
    }
}