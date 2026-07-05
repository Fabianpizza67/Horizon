package com.usermc.horizon.station.gui;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.navigation.ChipRack;
import com.usermc.horizon.ship.Ship;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Chip Rack retrieval GUI — 3 rows (27 slots) mirroring the rack's storage
 * 1:1. Clicking a filled slot withdraws that chip into the player's
 * inventory (or drops it at their feet if their inventory is full) and
 * clears the rack slot. Empty slots do nothing.
 *
 * Insertion happens OUTSIDE this GUI — right-clicking the physical rack
 * block while holding a chip auto-inserts it (see NavigationChipListener),
 * consistent with the project's "least UI possible" philosophy and the
 * fact that Horizon GUIs cancel all real drag-and-drop by design.
 */
public class ChipRackGui extends HorizonGui {

    private final ChipRack rack;

    public ChipRackGui(Ship ship, Player player, ChipRack rack) {
        super(ship, player, 3, "§b◈ Chip Rack");
        this.rack = rack;
    }

    @Override
    public void build() {
        inventory.clear();
        Map<Integer, ItemStack> contents = Horizon.getInstance().getChipRackManager().getContents(rack.getRackId());

        for (int slot = 0; slot < ChipRack.CAPACITY; slot++) {
            ItemStack chip = contents.get(slot);
            if (chip != null) {
                inventory.setItem(slot, chip);
            } else {
                inventory.setItem(slot, makeItem(Material.GRAY_STAINED_GLASS_PANE, "§8Empty Slot"));
            }
        }
    }

    @Override
    public boolean handleClick(int slot, ClickType click) {
        if (slot < 0 || slot >= ChipRack.CAPACITY) return false;

        Map<Integer, ItemStack> contents = Horizon.getInstance().getChipRackManager().getContents(rack.getRackId());
        ItemStack chip = contents.get(slot);
        if (chip == null) return false;

        var leftover = player.getInventory().addItem(chip.clone());
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }

        Horizon.getInstance().getChipRackManager().setSlot(rack.getRackId(), slot, null);
        player.sendActionBar("§b[Chip Rack] §fRetrieved chip.");
        return true; // refresh to show the now-empty slot
    }
}