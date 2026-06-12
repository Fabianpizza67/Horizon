package com.usermc.horizon.listener;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.station.ShipStation;
import com.usermc.horizon.station.StationItem;
import com.usermc.horizon.station.StationType;
import com.usermc.horizon.station.gui.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/**
 * Handles the full lifecycle of ship station blocks:
 *
 *   BlockPlaceEvent   — if the placed item is a Horizon station item, register it
 *                       in the StationManager for whichever ship it lands on.
 *   BlockBreakEvent   — if the broken block is a registered station, unregister it.
 *   PlayerInteractEvent — right-clicking a registered station opens the matching GUI.
 */
public class StationListener implements Listener {

    private final Horizon plugin;

    public StationListener(Horizon plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Placement — register the station
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        StationType type = StationItem.getType(event.getItemInHand());
        if (type == null) return;

        Player player = event.getPlayer();
        Block  block  = event.getBlockPlaced();

        // Must be placed on a registered ship
        Ship ship = plugin.getShipManager().getShipAt(block.getLocation());
        if (ship == null) {
            // Allow the block to place visually, but warn and cancel registration
            player.sendMessage("§e[Horizon] This station is not on a registered ship — it won't be functional.");
            return;
        }

        // Owner check
        if (!ship.isOwner(player) && !player.hasPermission("horizon.admin")) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] Only the ship owner can install stations.");
            return;
        }

        // Check for duplicate station type (one of each type per ship)
        if (plugin.getStationManager().getOfType(ship.getShipId(), type) != null) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] This ship already has a " + type.getDisplayName() + ".");
            return;
        }

        ShipStation station = new ShipStation(
                UUID.randomUUID(), ship.getShipId(), type, block.getLocation());
        plugin.getStationManager().register(station);

        // Station changed structure — mark for rescan
        ship.markStructureDirty();

        player.sendMessage("§a[Horizon] " + type.getDisplayName()
                + " installed on §f" + ship.getName() + "§a. Right-click to use.");
    }

    // -----------------------------------------------------------------------
    // Breaking — unregister the station
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ShipStation station = plugin.getStationManager()
                .getAtLocation(event.getBlock().getLocation());
        if (station == null) return;

        Player player = event.getPlayer();
        Ship   ship   = plugin.getShipManager().getById(station.getShipId());

        if (ship != null && !ship.isOwner(player) && !player.hasPermission("horizon.admin")) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] Only the ship owner can remove stations.");
            return;
        }

        if (ship != null && ship.isProcessing()) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] Cannot remove stations while the ship is moving.");
            return;
        }

        plugin.getStationManager().unregister(station);
        if (ship != null) ship.markStructureDirty();

        player.sendMessage("§e[Horizon] " + station.getType().getDisplayName() + " removed.");
    }

    // -----------------------------------------------------------------------
    // Interaction — open the matching GUI
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        Block       block   = event.getClickedBlock();
        ShipStation station = plugin.getStationManager().getAtLocation(block.getLocation());
        if (station == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Ship   ship   = plugin.getShipManager().getById(station.getShipId());

        if (ship == null) {
            player.sendMessage("§c[Horizon] Station's ship no longer exists.");
            return;
        }

        // Player must be aboard or within bounds
        if (!ship.isWithinBounds(player.getLocation()) && !player.hasPermission("horizon.admin")) {
            player.sendMessage("§c[Horizon] You must be aboard the ship to use this station.");
            return;
        }

        openGui(player, ship, station.getType());
    }

    // -----------------------------------------------------------------------
    // GUI dispatch
    // -----------------------------------------------------------------------

    private void openGui(Player player, Ship ship, StationType type) {
        HorizonGui gui = switch (type) {
            case HELM        -> new HelmGui(ship, player);
            case NAVIGATION  -> new NavigationGui(ship, player);
            case ENGINEERING -> new EngineeringGui(ship, player);
        };
        plugin.getGuiManager().open(player, gui);
    }
}