package com.usermc.horizon.listener;

import com.usermc.horizon.Horizon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles mining of asteroid blocks.
 *
 * Asteroid blocks are real placed blocks (e.g. IRON_ORE) so they look and
 * sound correct when mined, but their drop and respawn behavior is fully
 * controlled by AsteroidManager rather than vanilla block drop tables —
 * this lets a "stone" looking asteroid block drop something different than
 * vanilla stone would, and lets us schedule the per-block respawn timer.
 */
public class AsteroidMiningListener implements Listener {

    private final Horizon plugin;

    public AsteroidMiningListener(Horizon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        var manager = plugin.getAsteroidManager();
        if (manager == null) return;

        Location loc = event.getBlock().getLocation();
        if (!manager.isAsteroidBlock(loc)) return;

        Material mined = manager.mineBlock(loc);
        if (mined == null) return; // already mined (race) or not actually tracked

        // Suppress vanilla drops — we control exactly what's given
        event.setDropItems(false);

        Player player = event.getPlayer();
        ItemStack drop = new ItemStack(mined, 1);
        var leftover = player.getInventory().addItem(drop);
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }

        player.sendActionBar("§b[Mining] §f+1 " + formatMaterialName(mined));
    }

    private String formatMaterialName(Material mat) {
        String[] parts = mat.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}