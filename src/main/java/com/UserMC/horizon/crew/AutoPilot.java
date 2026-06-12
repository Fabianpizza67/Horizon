package com.usermc.horizon.crew;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.MoveDirection;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.engine.MovementRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Helmsman autopilot system.
 *
 * When activated, the assigned helmsman NPC continuously flies the ship
 * in the set direction at a configurable speed.
 *
 * Morale affects reliability:
 *   ≥ 75  — Perfectly reliable
 *   50–74 — 10% chance of skipping a movement tick (minor hesitation)
 *   25–49 — 25% chance of skip, 5% chance of slight course deviation
 *    0–24 — 50% skip chance, 15% deviation — helmsman is barely functional
 *
 * The autopilot shuts down automatically if:
 *   - The ship hits a collision
 *   - The helmsman deserts (morale = 0)
 *   - The captain runs /ship crew autopilot stop
 *   - The ship enters warp
 */
public class AutoPilot {

    private record PilotState(MoveDirection direction, int speed, UUID helmsmanCrewId, BukkitTask task) {}

    private final Horizon plugin;
    private final Map<UUID, PilotState> active = new HashMap<>();
    private final Random rng = new Random();

    public AutoPilot(Horizon plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Activate / Deactivate
    // -----------------------------------------------------------------------

    /**
     * Start autopilot for a ship.
     *
     * @param ship      The ship to autopilot
     * @param direction Direction of travel
     * @param speed     Speed in blocks per movement tick
     * @param helmsman  The helmsman crew member doing the flying
     * @param captain   Player who issued the command (for feedback)
     */
    public void startAutoPilot(Ship ship, MoveDirection direction, int speed,
                               CrewMember helmsman, Player captain) {
        // Stop any existing autopilot first
        stopAutoPilot(ship);

        int intervalTicks = plugin.getHorizonConfig().getMovementIntervalTicks();

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {

            // Safety guards
            if (!ship.isReady() || plugin.getWarpManager().isCharging(ship)) {
                stopAutoPilot(ship);
                broadcastToShip(ship, "§e[Autopilot] Disengaged.");
                return;
            }

            // Retrieve current helmsman morale
            double morale = helmsman.getMorale();

            // Zero morale — helmsman can no longer function
            if (morale <= 0) {
                stopAutoPilot(ship);
                broadcastToShip(ship, "§c[Autopilot] " + helmsman.getName()
                        + " has abandoned the helm.");
                return;
            }

            // Morale-based skip probability
            double skipChance = 0.0;
            if      (morale < 25) skipChance = 0.50;
            else if (morale < 50) skipChance = 0.25;
            else if (morale < 75) skipChance = 0.10;

            if (rng.nextDouble() < skipChance) return; // helmsman hesitates

            // Morale-based deviation: occasionally veer off course
            MoveDirection actualDir = direction;
            double deviationChance = 0.0;
            if      (morale < 25) deviationChance = 0.15;
            else if (morale < 50) deviationChance = 0.05;

            if (deviationChance > 0 && rng.nextDouble() < deviationChance) {
                MoveDirection[] all = MoveDirection.values();
                actualDir = all[rng.nextInt(all.length)];
                broadcastToShip(ship, "§7[Helm] " + helmsman.getName()
                        + " drifted slightly off course...");
            }

            // Queue movement via the engine
            plugin.getMovementEngine().queueMovement(MovementRequest.move(ship, actualDir, speed));

        }, 0L, intervalTicks);

        active.put(ship.getShipId(), new PilotState(direction, speed, helmsman.getCrewId(), task));

        broadcastToShip(ship, "§a[Autopilot] " + helmsman.getName()
                + " has taken the helm — heading §f" + direction.name().toLowerCase()
                + " §aat speed §f" + speed + "§a.");
    }

    /**
     * Stop autopilot for a ship.
     */
    public void stopAutoPilot(Ship ship) {
        PilotState state = active.remove(ship.getShipId());
        if (state != null) {
            state.task().cancel();
        }
    }

    public boolean isActive(Ship ship) {
        return active.containsKey(ship.getShipId());
    }

    public MoveDirection getHeading(Ship ship) {
        PilotState s = active.get(ship.getShipId());
        return s == null ? null : s.direction();
    }

    // -----------------------------------------------------------------------
    // Shutdown
    // -----------------------------------------------------------------------

    public void stopAll() {
        for (PilotState state : active.values()) state.task().cancel();
        active.clear();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private void broadcastToShip(Ship ship, String message) {
        for (UUID uuid : ship.getPassengers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }
}