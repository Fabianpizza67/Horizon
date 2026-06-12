package com.usermc.horizon.listener;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.Ship;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Manages the passenger list on ships.
 *
 * Players are automatically boarded when they walk onto (or are teleported to)
 * a ship, and automatically disembarked when they walk off.
 *
 * Boarding via /ship board also adds them to the passenger list directly.
 */
public class PlayerBoardListener implements Listener {

    private final Horizon plugin;

    public PlayerBoardListener(Horizon plugin) {
        this.plugin = plugin;
    }

    /** On join, check if the player is still inside a ship's bounds (e.g. after relog). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Ship ship = plugin.getShipManager().getShipAt(player.getLocation());
            if (ship != null && !ship.isPassenger(player.getUniqueId())) {
                ship.addPassenger(player.getUniqueId());
            }
        }, 5L);
    }

    /** On quit, remove from any ship. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (var ship : plugin.getShipManager().getAllShips()) {
            ship.removePassenger(player.getUniqueId());
        }
    }

    /**
     * Detect board/disembark by tracking movement.
     * Only fires when position changes by at least half a block to avoid spam.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only process if the player actually moved to a new block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();

        // Check if they've just left a ship they were on
        for (Ship ship : plugin.getShipManager().getAllShips()) {
            if (!ship.isPassenger(player.getUniqueId())) continue;
            if (!ship.isWithinBounds(event.getTo())) {
                ship.removePassenger(player.getUniqueId());
                // Don't send a message — disembark is silent (natural walking off)
                return;
            }
        }

        // Check if they've walked onto a new ship
        Ship target = plugin.getShipManager().getShipAt(event.getTo());
        if (target != null && !target.isPassenger(player.getUniqueId())) {
            target.addPassenger(player.getUniqueId());
            player.sendMessage("§b[Horizon] Boarded §f" + target.getName() + "§b.");
        }
    }
}