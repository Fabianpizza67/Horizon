package com.usermc.horizon.listener;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.navigation.ChipItem;
import com.usermc.horizon.navigation.ChipRack;
import com.usermc.horizon.navigation.ChipRackItem;
import com.usermc.horizon.ship.Ship;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the full lifecycle of Chip Rack blocks, chip-in-chest prevention,
 * and the anvil-based rename flow for navigation chips.
 *
 * Interaction model (deliberately avoids drag-and-drop since Horizon GUIs
 * cancel all inventory clicks by design):
 *   - Right-click rack while HOLDING a chip → auto-inserts into first empty slot
 *   - Right-click rack while NOT holding a chip → opens retrieval GUI
 *   - Sneak + right-click while holding a chip (any target) → opens a real
 *     vanilla Anvil GUI pre-filled with the chip, letting the player use
 *     Minecraft's own rename text field at zero XP cost.
 */
public class NavigationChipListener implements Listener {

    private static final Set<InventoryType> VANILLA_STORAGE_TYPES = EnumSet.of(
            InventoryType.CHEST, InventoryType.BARREL, InventoryType.SHULKER_BOX,
            InventoryType.DISPENSER, InventoryType.DROPPER, InventoryType.HOPPER,
            InventoryType.ENDER_CHEST
    );

    private final Horizon plugin;

    /** Tracks which players currently have a chip-rename anvil open. */
    private final Map<UUID, ItemStack> activeRenames = new HashMap<>();

    public NavigationChipListener(Horizon plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Rack placement
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!ChipRackItem.isRackItem(event.getItemInHand())) return;

        Player player = event.getPlayer();
        Block  block  = event.getBlockPlaced();

        Ship ship = plugin.getShipManager().getShipAt(block.getLocation());
        if (ship == null) {
            player.sendMessage("§e[Horizon] Chip Rack placed but is not on a registered ship — won't be functional.");
            return;
        }
        if (!ship.isOwner(player) && !player.hasPermission("horizon.admin")) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] Only the ship owner can install a Chip Rack.");
            return;
        }

        ChipRack rack = new ChipRack(UUID.randomUUID(), ship.getShipId(), block.getLocation());
        plugin.getChipRackManager().register(rack);
        ship.markStructureDirty();
        player.sendMessage("§a[Horizon] Chip Rack installed on §f" + ship.getName() + "§a.");
    }

    // -----------------------------------------------------------------------
    // Rack breaking
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ChipRack rack = plugin.getChipRackManager().getAtLocation(event.getBlock().getLocation());
        if (rack == null) return;

        Player player = event.getPlayer();
        Ship   ship   = plugin.getShipManager().getById(rack.getShipId());

        if (ship != null && !ship.isOwner(player) && !player.hasPermission("horizon.admin")) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] Only the ship owner can remove the Chip Rack.");
            return;
        }
        if (ship != null && ship.isProcessing()) {
            event.setCancelled(true);
            player.sendMessage("§c[Horizon] Cannot remove blocks while the ship is moving.");
            return;
        }

        // Drop all stored chips rather than deleting them
        var contents = plugin.getChipRackManager().getContents(rack.getRackId());
        for (ItemStack item : contents.values()) {
            if (item != null) event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
        }

        plugin.getChipRackManager().unregister(rack);
        if (ship != null) ship.markStructureDirty();
        player.sendMessage("§e[Horizon] Chip Rack removed.");
    }

    // -----------------------------------------------------------------------
    // Rack interaction — insert on right-click-with-chip, else open GUI
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack held = event.getItem();

        // --- Anvil rename flow: sneak + right-click while holding a chip ---
        if (player.isSneaking() && ChipItem.isChip(held)) {
            event.setCancelled(true);
            openRenameAnvil(player, held);
            return;
        }

        if (event.getClickedBlock() == null) return;
        ChipRack rack = plugin.getChipRackManager().getAtLocation(event.getClickedBlock().getLocation());
        if (rack == null) return;

        event.setCancelled(true);

        Ship ship = plugin.getShipManager().getById(rack.getShipId());
        if (ship != null && !ship.isWithinBounds(player.getLocation()) && !player.hasPermission("horizon.admin")) {
            player.sendMessage("§c[Horizon] You must be aboard the ship to use this rack.");
            return;
        }

        if (ChipItem.isChip(held)) {
            insertChip(player, rack, held);
        } else {
            plugin.getGuiManager().open(player, new com.usermc.horizon.station.gui.ChipRackGui(ship, player, rack));
        }
    }

    private void insertChip(Player player, ChipRack rack, ItemStack chip) {
        var contents = plugin.getChipRackManager().getContents(rack.getRackId());
        for (int slot = 0; slot < ChipRack.CAPACITY; slot++) {
            if (!contents.containsKey(slot) || contents.get(slot) == null) {
                ItemStack toStore = chip.clone();
                toStore.setAmount(1);
                plugin.getChipRackManager().setSlot(rack.getRackId(), slot, toStore);

                ItemStack hand = player.getInventory().getItemInMainHand();
                hand.setAmount(hand.getAmount() - 1);

                player.sendActionBar("§b[Chip Rack] §fStored " + ChipItem.getName(chip) + "§7.");
                return;
            }
        }
        player.sendActionBar("§c[Chip Rack] Rack is full.");
    }

    // -----------------------------------------------------------------------
    // Chest prevention — chips cannot be placed in vanilla storage inventories
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        // Renaming anvils are handled separately below — don't block those
        if (event.getInventory().getType() == InventoryType.ANVIL) return;

        if (event.getClickedInventory() == null) return;
        if (!VANILLA_STORAGE_TYPES.contains(event.getClickedInventory().getType())) return;

        ItemStack moving = (event.getCursor() != null && !event.getCursor().getType().isAir())
                ? event.getCursor() : event.getCurrentItem();

        if (ChipItem.isChip(moving)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                p.sendActionBar("§c[Horizon] Navigation chips cannot be stored in containers — use a Chip Rack.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (ChipItem.isChip(event.getItem())) {
            event.setCancelled(true);
        }
    }

    // -----------------------------------------------------------------------
    // Anvil-based rename flow
    // -----------------------------------------------------------------------

    private void openRenameAnvil(Player player, ItemStack chip) {
        Inventory anvil = Bukkit.createInventory(player, InventoryType.ANVIL, "§bRename Navigation Chip");
        ItemStack chipCopy = chip.clone();
        chipCopy.setAmount(1);
        anvil.setItem(0, chipCopy);
        activeRenames.put(player.getUniqueId(), chip);
        player.openInventory(anvil);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (!activeRenames.containsKey(player.getUniqueId())) return;

        // Force zero repair cost — this is a free rename, not a real anvil operation
        event.getInventory().setRepairCost(0);

        ItemStack first = event.getInventory().getItem(0);
        if (first == null || !ChipItem.isChip(first)) return;

        String newName = event.getInventory().getRenameText();
        ItemStack result = first.clone();
        if (newName != null && !newName.isBlank()) {
            ItemMeta meta = result.getItemMeta();
            meta.setDisplayName("§b◈ " + newName);
            result.setItemMeta(meta);
        }
        event.setResult(result);
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!activeRenames.containsKey(player.getUniqueId())) return;

        // Result slot in an anvil is slot index 2
        if (event.getSlot() != 2) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || !ChipItem.isChip(result)) return;

        // Extract the renamed display name and finalize it into the chip's PDC
        String displayName = (result.hasItemMeta() && result.getItemMeta().hasDisplayName())
                ? stripPrefix(result.getItemMeta().getDisplayName())
                : ChipItem.getName(result);

        ChipItem.rename(result, displayName);
        activeRenames.remove(player.getUniqueId());
        // Let the vanilla pickup proceed naturally with our corrected PDC data
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() == InventoryType.ANVIL
                && event.getPlayer() instanceof Player player) {
            activeRenames.remove(player.getUniqueId());
        }
    }

    private String stripPrefix(String displayName) {
        return displayName.replace("§b◈ ", "").trim();
    }
}