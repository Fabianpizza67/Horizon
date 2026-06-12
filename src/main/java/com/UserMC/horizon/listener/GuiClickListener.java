package com.usermc.horizon.listener;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.station.gui.HorizonGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Routes all inventory interactions to the correct open HorizonGui.
 *
 * ALL clicks inside a Horizon GUI are cancelled by default — no items
 * can be picked up or moved. Individual GUIs handle their own click logic
 * through handleClick().
 */
public class GuiClickListener implements Listener {

    private final Horizon plugin;

    public GuiClickListener(Horizon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        HorizonGui gui = plugin.getGuiManager().get(player);
        if (gui == null) return;

        // Cancel ALL item movement in Horizon GUIs
        event.setCancelled(true);

        // Only process clicks in our GUI area (top inventory), not player's own inv
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(gui.getInventory())) return;

        boolean refresh = gui.handleClick(event.getSlot(), event.getClick());
        if (refresh) gui.refresh();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.getGuiManager().has(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getGuiManager().close(player);
    }
}