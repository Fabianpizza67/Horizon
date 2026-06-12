package com.usermc.horizon.station.gui;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.warp.WarpBeacon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Navigation Console GUI — shows all available warp beacons.
 *
 * Each beacon occupies one slot: a COMPASS named after the beacon.
 * Lore shows distance, description and fuel cost.
 * Clicking a beacon starts the warp charge sequence and closes the GUI.
 *
 * Rows scale with the number of beacons (min 1, max 6 rows).
 * If a warp is already charging, the GUI shows a locked state.
 */
public class NavigationGui extends HorizonGui {

    private final List<WarpBeacon> beacons;

    public NavigationGui(Ship ship, Player player) {
        super(ship, player,
                calculateRows(Horizon.getInstance().getWarpManager().getAllBeacons().size()),
                "§b🗺 Navigation — " + ship.getName());

        // Sort beacons by distance from ship
        List<WarpBeacon> all = new ArrayList<>(
                Horizon.getInstance().getWarpManager().getAllBeacons());
        all.removeIf(b -> b.isAdminOnly() && !player.hasPermission("horizon.admin"));
        all.sort(Comparator.comparingDouble(b -> b.distanceTo(ship.getCoreLocation())));
        this.beacons = all;
    }

    @Override
    public void build() {
        inventory.clear();
        Horizon plugin   = Horizon.getInstance();
        boolean charging = plugin.getWarpManager().isCharging(ship);
        int     fuel     = ship.getFuelLevel();

        if (charging) {
            // Locked state — show abort option in centre
            inventory.setItem(inventory.getSize() / 2,
                    makeItem(Material.BARRIER, "§c⚠ Warp Drive Charging",
                            "§7A jump is already in progress.",
                            "",
                            "§eClick to abort warp."));
            fillAll();
            return;
        }

        if (beacons.isEmpty()) {
            inventory.setItem(inventory.getSize() / 2,
                    makeItem(Material.ORANGE_STAINED_GLASS_PANE,
                            "§eNo Warp Beacons Found",
                            "§7Ask an admin to create beacons with",
                            "§f/ship warp admin create <name>"));
            fillAll();
            return;
        }

        for (int i = 0; i < beacons.size() && i < inventory.getSize(); i++) {
            WarpBeacon b    = beacons.get(i);
            double     dist = b.distanceTo(ship.getCoreLocation());
            int        cost = plugin.getFuelManager().calculateWarpCost(dist);
            boolean    canAfford = fuel >= cost;

            Material mat = canAfford ? Material.COMPASS : Material.CLOCK;
            String   distStr = dist == Double.MAX_VALUE
                    ? "§cDifferent world" : String.format("§7%.0f blocks away", dist);

            inventory.setItem(i, makeItem(mat,
                    (canAfford ? "§b" : "§8") + b.getName(),
                    distStr,
                    b.getDescription().isBlank() ? "§8No description" : "§7" + b.getDescription(),
                    "",
                    "§7Fuel cost: " + (canAfford ? "§a" : "§c") + cost + " units",
                    "§7Your fuel:  §f" + fuel,
                    "",
                    canAfford ? "§eClick to warp!" : "§cInsufficient fuel — visit Engineering."
            ));
        }

        fillAll();
    }

    @Override
    public boolean handleClick(int slot, ClickType click) {
        Horizon plugin = Horizon.getInstance();

        // Abort click when charging
        if (plugin.getWarpManager().isCharging(ship)) {
            plugin.getWarpManager().abortWarp(ship);
            player.sendActionBar("§e[Navigation] Warp aborted.");
            return true;
        }

        if (slot < 0 || slot >= beacons.size()) return false;

        WarpBeacon target = beacons.get(slot);
        player.closeInventory(); // close before the sequence starts
        plugin.getWarpManager().initiateWarp(ship, target, player);
        return false; // GUI already closed
    }

    private static int calculateRows(int beaconCount) {
        if (beaconCount == 0) return 1;
        return Math.min(6, (int) Math.ceil(beaconCount / 9.0));
    }
}