package com.usermc.horizon.station.gui;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks all currently open Horizon GUIs.
 * Used by GuiClickListener to route click events to the right GUI.
 */
public class GuiManager {

    private final Map<UUID, HorizonGui> open = new HashMap<>();

    public void open(Player player, HorizonGui gui) {
        open.put(player.getUniqueId(), gui);
        gui.open();
    }

    public HorizonGui get(Player player) {
        return open.get(player.getUniqueId());
    }

    public boolean has(Player player) {
        return open.containsKey(player.getUniqueId());
    }

    public void close(Player player) {
        open.remove(player.getUniqueId());
    }

    public void closeAll() {
        for (UUID uuid : open.keySet()) {
            var player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) player.closeInventory();
        }
        open.clear();
    }
}