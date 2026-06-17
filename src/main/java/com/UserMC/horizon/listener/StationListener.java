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

public class StationListener implements Listener {

    private final Horizon plugin;

    public StationListener(Horizon plugin) { this.plugin = plugin; }

    // -----------------------------------------------------------------------
    // Placement
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        StationType type = StationItem.getType(event.getItemInHand());
        if (type == null) return;

        Player player = event.getPlayer();
        Block  block  = event.getBlockPlaced();

        Ship ship = plugin.getShipManager().getShipAt(block.getLocation());
        if (ship == null) {
            player.sendMessage("§e[Horizon] Station placed but is not on a registered ship — won't be functional.");
            return;
        }

        if (!ship.isOwner(player) && !player.hasPermission("horizon.admin")) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] Only the ship owner can install stations.");
            return;
        }

        if (plugin.getStationManager().getOfType(ship.getShipId(), type) != null) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] This ship already has a " + type.getDisplayName() + ".");
            return;
        }

        ShipStation station = new ShipStation(UUID.randomUUID(), ship.getShipId(), type, block.getLocation());
        plugin.getStationManager().register(station);
        ship.markStructureDirty();
        player.sendMessage("§a[Horizon] " + type.getDisplayName() + " installed on §f" + ship.getName()
                + "§a. Right-click to use.");
    }

    // -----------------------------------------------------------------------
    // Breaking
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ShipStation station = plugin.getStationManager().getAtLocation(event.getBlock().getLocation());
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
    // Interaction — open the correct GUI
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        ShipStation station = plugin.getStationManager().getAtLocation(event.getClickedBlock().getLocation());
        if (station == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Ship   ship   = plugin.getShipManager().getById(station.getShipId());

        if (ship == null) {
            player.sendMessage("§c[Horizon] Station's ship no longer exists.");
            return;
        }

        if (!ship.isWithinBounds(player.getLocation()) && !player.hasPermission("horizon.admin")) {
            player.sendMessage("§c[Horizon] You must be aboard the ship to use this station.");
            return;
        }

        HorizonGui gui = switch (station.getType()) {
            case HELM             -> new HelmGui(ship, player);
            case NAVIGATION       -> new NavigationGui(ship, player);
            case ENGINEERING      -> new EngineeringGui(ship, player);
            case MISSION_TERMINAL -> new MissionBoardGui(ship, player);
            case FACTION_TERMINAL -> new FactionTerminalGui(ship, player);
        };

        plugin.getGuiManager().open(player, gui);
    }

    // -----------------------------------------------------------------------
    // Prevent block modifications while ship is moving
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShipBlockBreak(BlockBreakEvent event) {
        ShipStation station = plugin.getStationManager().getAtLocation(event.getBlock().getLocation());
        if (station != null) return; // already handled above

        Ship ship = plugin.getShipManager().getShipAt(event.getBlock().getLocation());
        if (ship == null) return;

        if (ship.isProcessing()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[Horizon] Cannot modify ship blocks while moving.");
            return;
        }
        if (ship.isReady()) {
            ship.markStructureDirty();
            event.getPlayer().sendActionBar(
                    "§e[Horizon] Structure changed — §fShift+right-click §ethe Ship Core to rescan.");
        }
    }
}