package com.usermc.horizon.station.gui;

import com.usermc.horizon.ship.Ship;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Base for all Horizon inventory GUIs.
 *
 * Subclasses implement:
 *   build()        — populate the inventory with items
 *   handleClick()  — react to a slot click, return true to refresh after
 */
public abstract class HorizonGui {

    protected final Ship   ship;
    protected final Player player;
    protected Inventory    inventory;

    protected static final ItemStack FILLER =
            makeItem(Material.GRAY_STAINED_GLASS_PANE, "§r", List.of());

    protected HorizonGui(Ship ship, Player player, int rows, String title) {
        this.ship     = ship;
        this.player   = player;
        this.inventory = Bukkit.createInventory(null, rows * 9, title);
    }

    /** Populate the inventory. Called before opening and after each refresh. */
    public abstract void build();

    /**
     * Handle a click on slot {@code slot}.
     * @return true if the GUI should be rebuilt after this click
     */
    public abstract boolean handleClick(int slot, ClickType click);

    public Inventory getInventory() { return inventory; }
    public Ship      getShip()      { return ship; }

    /** Open this GUI for the player. Always call build() first. */
    public void open() {
        build();
        player.openInventory(inventory);
    }

    /** Rebuild contents without closing the inventory. */
    public void refresh() {
        build();
        player.updateInventory();
    }

    // -----------------------------------------------------------------------
    // Item helpers
    // -----------------------------------------------------------------------

    protected static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    protected static ItemStack makeItem(Material mat, String name, String... lore) {
        return makeItem(mat, name, Arrays.asList(lore));
    }

    protected void fill(Material mat) {
        ItemStack pane = makeItem(mat, "§r", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, pane);
        }
    }

    protected void fillAll() { fill(Material.GRAY_STAINED_GLASS_PANE); }
}