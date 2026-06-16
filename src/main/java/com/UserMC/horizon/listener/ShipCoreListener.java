package com.usermc.horizon.listener;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.station.ShipStation;
import com.usermc.horizon.story.StoryObjectiveType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Handles the Ship Core (Lodestone) block.
 *
 * Right-click          → show ship info
 * Shift + right-click  → rescan ship structure (replaces /ship scan)
 */
public class ShipCoreListener implements Listener {

    private final Horizon plugin;

    public ShipCoreListener(Horizon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.LODESTONE) return;
        Ship ship = plugin.getShipManager().getAtCoreLocation(event.getBlock().getLocation());
        if (ship == null) return;

        Player player = event.getPlayer();
        if (!ship.isOwner(player) && !player.hasPermission("horizon.admin")) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] You don't own this Ship Core.");
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§e[Horizon] To remove this ship, use §f/ship delete " + ship.getName() + "§e.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.LODESTONE) return;
        if (plugin.getShipManager().getAtCoreLocation(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[Horizon] A Ship Core already exists here.");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.LODESTONE) return;

        Ship ship = plugin.getShipManager().getAtCoreLocation(event.getClickedBlock().getLocation());
        if (ship == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (player.isSneaking()) {
            // Shift + right-click = rescan
            if (!ship.isOwner(player) && !player.hasPermission("horizon.admin")) {
                player.sendMessage("§c[Horizon] Only the ship owner can rescan.");
                return;
            }
            if (ship.isProcessing()) {
                player.sendMessage("§c[Horizon] Ship is moving — wait before rescanning.");
                return;
            }
            int maxBlocks = player.hasPermission("horizon.admin")
                    ? plugin.getHorizonConfig().getAdminLimit()
                    : plugin.getHorizonConfig().getPlayerHardLimit();

            player.sendMessage("§e[Horizon] Scanning §f" + ship.getName() + "§e...");
            plugin.getShipManager().getScanner().scan(ship.getCoreLocation(), maxBlocks,
                    structure -> {
                        int count = structure.getBlockCount();
                        com.usermc.horizon.ship.ShipClass sc =
                                com.usermc.horizon.ship.ShipClass.fromBlockCount(
                                        count, player.hasPermission("horizon.admin"));
                        ship.setShipClass(sc);
                        ship.setStructure(structure);
                        player.sendMessage("§a[Horizon] Scan complete — §f" + count
                                + " §ablocks, §f" + sc.getDisplayName() + "§a.");
                        plugin.getStoryManager().progressObjective(player, StoryObjectiveType.SCAN_SHIP);
                    },
                    err -> player.sendMessage("§c[Horizon] Scan failed: " + err));
        } else {
            // Right-click = info
            printInfo(player, ship);
        }
    }

    /** Also prevent breaking any block that is a registered station while ship is moving. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShipBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.LODESTONE) return;

        ShipStation station = plugin.getStationManager()
                .getAtLocation(event.getBlock().getLocation());
        if (station != null) return; // handled by StationListener

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

    private void printInfo(Player p, Ship ship) {
        p.sendMessage("§b§l[ " + ship.getName() + " ]");
        p.sendMessage("§7Class:  §f" + ship.getShipClass().getDisplayName()
                + "  §7Status: §f" + ship.getStatus());
        p.sendMessage("§7Fuel:   " + plugin.getFuelManager().fuelBar(ship));
        if (ship.getStructure() != null) {
            p.sendMessage("§7Blocks: §f" + ship.getStructure().getBlockCount()
                    + " §8/ §f" + ship.getShipClass().getHardLimit());
        }
        if (ship.isStructureDirty()) {
            p.sendMessage("§e⚠ Structure needs rescan — §fShift+right-click§e this core.");
        }
        p.sendMessage("§7Stations installed: §f"
                + plugin.getStationManager().getForShip(ship.getShipId()).size());
        p.sendMessage("§8Shift+right-click to rescan structure.");
    }
}