package com.usermc.horizon.listener;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.Ship;
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
 * Handles events relating to the Ship Core block (LODESTONE).
 *
 * A lodestone becomes a Ship Core only when registered via /ship register.
 * Tracked in ShipManager's core index.
 */
public class ShipCoreListener implements Listener {

    private final Horizon plugin;

    public ShipCoreListener(Horizon plugin) {
        this.plugin = plugin;
    }

    /** Prevent breaking a registered Ship Core without deregistering first. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.LODESTONE) return;

        Ship ship = plugin.getShipManager().getAtCoreLocation(block.getLocation());
        if (ship == null) return;

        Player player = event.getPlayer();

        // Admins can force-break with confirmation item (future feature)
        if (!ship.isOwner(player) && !player.hasPermission("horizon.admin")) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] You don't own this ship core.");
            return;
        }

        // Cancel and prompt for deletion via command
        event.setCancelled(true);
        player.sendMessage("§e[Horizon] This is the core of ship §b" + ship.getName()
                + "§e. Use §f/ship delete " + ship.getName() + " §eto remove it.");
    }

    /** Prevent placing a block where a Ship Core already is (shouldn't happen, but safety net). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.LODESTONE) return;

        Ship existing = plugin.getShipManager().getAtCoreLocation(event.getBlock().getLocation());
        if (existing != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[Horizon] A ship core already exists here.");
        }
    }

    /** Right-clicking a registered Ship Core shows ship info. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCoreInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.LODESTONE) return;

        Ship ship = plugin.getShipManager().getAtCoreLocation(event.getClickedBlock().getLocation());
        if (ship == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        player.sendMessage("§b§l[ " + ship.getName() + " ]");
        player.sendMessage("§7Class: §f"   + ship.getShipClass().getDisplayName());
        player.sendMessage("§7Status: §f"  + ship.getStatus().name());
        player.sendMessage("§7Heading: §f" + String.format("%.1f°", ship.getHeading()));
        if (ship.getStructure() != null) {
            player.sendMessage("§7Blocks: §f" + ship.getStructure().getBlockCount()
                    + " §8(limit: " + ship.getShipClass().getHardLimit() + ")");
            if (ship.getShipClass().isOverSoftLimit(ship.getStructure().getBlockCount())) {
                player.sendMessage("§e⚠ Over soft limit — speed penalty active.");
            }
        } else {
            player.sendMessage("§e⚠ No structure scanned. Use §f/ship scan§e to scan.");
        }
        player.sendMessage("§7Passengers: §f" + ship.getPassengers().size());
    }

    /**
     * Prevent players inside a moving ship from breaking blocks on it.
     * Ship blocks should only be modified while docked.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShipBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.LODESTONE) return; // handled above

        Ship ship = plugin.getShipManager().getShipAt(event.getBlock().getLocation());
        if (ship == null) return;

        if (ship.isProcessing()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[Horizon] Cannot modify ship blocks while moving.");
            return;
        }

        // If ship is flying, warn that structure will need rescan
        if (ship.isReady()) {
            ship.markStructureDirty();
            event.getPlayer().sendMessage(
                    "§e[Horizon] Ship structure changed — use §f/ship scan §ewhen done building.");
        }
    }
}