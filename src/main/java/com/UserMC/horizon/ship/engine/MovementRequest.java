package com.usermc.horizon.ship.engine;

import com.usermc.horizon.ship.MoveDirection;
import com.usermc.horizon.ship.Ship;

import java.util.UUID;

/**
 * A queued action for the movement engine — either a translation or a 90° rotation.
 * Use the static factory methods rather than constructors directly.
 */
public class MovementRequest {

    public enum Type { MOVE, ROTATE }

    private final UUID          requestId;
    private final Ship          ship;
    private final Type          type;
    private final MoveDirection direction; // MOVE only
    private final int           speed;     // MOVE only
    private final boolean       clockwise; // ROTATE only

    // -----------------------------------------------------------------------
    // Factories
    // -----------------------------------------------------------------------

    public static MovementRequest move(Ship ship, MoveDirection dir, int speed) {
        return new MovementRequest(ship, dir, speed, false);
    }

    public static MovementRequest rotate(Ship ship, boolean clockwise) {
        return new MovementRequest(ship, null, 0, clockwise);
    }

    private MovementRequest(Ship ship, MoveDirection dir, int speed, boolean clockwise) {
        this.requestId = UUID.randomUUID();
        this.ship      = ship;
        this.type      = dir != null ? Type.MOVE : Type.ROTATE;
        this.direction = dir;
        this.speed     = speed;
        this.clockwise = clockwise;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public UUID          getRequestId() { return requestId; }
    public Ship          getShip()      { return ship; }
    public Type          getType()      { return type; }
    public MoveDirection getDirection() { return direction; }
    public int           getSpeed()     { return speed; }
    public boolean       isClockwise()  { return clockwise; }

    public int dx() { return direction != null ? direction.dx * speed : 0; }
    public int dy() { return direction != null ? direction.dy * speed : 0; }
    public int dz() { return direction != null ? direction.dz * speed : 0; }
}