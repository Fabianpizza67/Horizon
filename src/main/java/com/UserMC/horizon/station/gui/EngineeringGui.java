package com.usermc.horizon.station.gui;

import com.usermc.horizon.fuel.FuelItem;
import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.ShipStructure;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Engineering Console GUI — 3 rows.
 *
 * Layout:
 *   Row 0 (slots 0–8):  Fuel gauge — 9 glass panes, filled proportionally
 *   Row 1 (slots 9–17): [REFUEL] [GL] [GL] [CLASS] [BLOCKS] [SPEED] [WARP] [GL] [GL]
 *   Row 2 (slots 18–26): [CREW morale summary] filled with dark glass
 *
 * Clicking REFUEL takes all Dilithium Crystals from the player's inventory
 * and converts them to ship fuel instantly.
 */
public class EngineeringGui extends HorizonGui {

    private static final int SLOT_REFUEL = 9;

    public EngineeringGui(Ship ship, Player player) {
        super(ship, player, 3, "§b⚙ Engineering — " + ship.getName());
    }

    @Override
    public void build() {
        inventory.clear();
        Horizon plugin = Horizon.getInstance();
        int     fuel   = ship.getFuelLevel();
        int     maxFuel= plugin.getFuelManager().getMaxFuel(ship.getShipClass());

        // --- Row 0: Fuel gauge (9 panes) ---
        double ratio     = maxFuel == 0 ? 0 : (double) fuel / maxFuel;
        int    filled    = (int) Math.round(ratio * 9);
        Material filledMat = ratio > 0.6 ? Material.LIME_STAINED_GLASS_PANE
                : ratio > 0.3 ? Material.YELLOW_STAINED_GLASS_PANE
                  :               Material.RED_STAINED_GLASS_PANE;

        for (int i = 0; i < 9; i++) {
            if (i < filled) {
                inventory.setItem(i, makeItem(filledMat,
                        "§b⚡ Fuel: §f" + fuel + " §8/ §f" + maxFuel,
                        "§7" + String.format("%.0f%%", ratio * 100) + " capacity"));
            } else {
                inventory.setItem(i, makeItem(Material.GRAY_STAINED_GLASS_PANE,
                        "§8▪ Empty",
                        "§7" + String.format("%.0f%%", ratio * 100) + " capacity"));
            }
        }

        // --- Row 1: Refuel button + ship stats ---
        int maxFuelCap = plugin.getFuelManager().getMaxFuel(ship.getShipClass());
        boolean isFull = fuel >= maxFuelCap;
        inventory.setItem(SLOT_REFUEL, makeItem(
                isFull ? Material.BARRIER : Material.AMETHYST_SHARD,
                isFull ? "§8Fuel Tank Full" : "§a⚡ Inject Dilithium",
                isFull ? "§7Tank is already at capacity." : "§7Uses all Dilithium Crystals in your inventory.",
                isFull ? "" : "§8Each crystal = §b" + FuelItem.FUEL_PER_CRYSTAL + " §8units"
        ));

        ShipStructure struct = ship.getStructure();
        inventory.setItem(10, makeItem(Material.IRON_INGOT,
                "§7Class: §f" + ship.getShipClass().getDisplayName(),
                "§7Soft limit: §f" + ship.getShipClass().getSoftLimit() + " blocks",
                "§7Hard limit: §f" + ship.getShipClass().getHardLimit() + " blocks"
        ));

        inventory.setItem(11, makeItem(Material.BRICKS,
                struct != null ? "§7Blocks: §f" + struct.getBlockCount() : "§eNo scan — right-click Ship Core",
                struct != null ? "§7Size: §f" + struct.getWidth() + "×" + struct.getHeight() + "×" + struct.getLength() : "",
                struct != null && ship.isStructureDirty() ? "§e⚠ Structure changed — rescan at Ship Core" : ""
        ));

        inventory.setItem(12, makeItem(Material.FEATHER,
                "§7Speed multiplier: §f" + String.format("%.0f%%", ship.getSpeedMultiplier() * 100),
                ship.getSpeedMultiplier() < 1.0
                        ? "§e⚠ Over soft limit — speed penalty active"
                        : "§aWithin weight limit"
        ));

        inventory.setItem(13, makeItem(Material.ENDER_PEARL,
                "§7Warp status: §f" + ship.getWarpStatus().name(),
                "§7Ship status: §f" + ship.getStatus().name(),
                plugin.getWarpManager().isCharging(ship) ? "§e⚠ Warp drive charging..." : ""
        ));

        // --- Row 2: Crew morale summary ---
        var crew = plugin.getCrewManager().getCrewForShip(ship.getShipId());
        if (crew.isEmpty()) {
            inventory.setItem(18, makeItem(Material.BARRIER,
                    "§cNo Crew",
                    "§7Hire crew with §f/ship crew hire"));
        } else {
            int idx = 18;
            for (var cm : crew) {
                if (idx > 26) break;
                inventory.setItem(idx++, makeItem(Material.PLAYER_HEAD,
                        cm.moraleColour() + cm.getName(),
                        "§7" + cm.getSpecies() + " — " + cm.getRole().getDisplayName(),
                        "§7Skill: §f" + cm.getSkillLevel() + "  §7Morale: "
                                + cm.moraleColour() + String.format("%.0f", cm.getMorale()) + "%"
                ));
            }
        }

        fillAll();
    }

    @Override
    public boolean handleClick(int slot, ClickType click) {
        if (slot != SLOT_REFUEL) return false;

        Horizon plugin  = Horizon.getInstance();
        int     maxFuel = plugin.getFuelManager().getMaxFuel(ship.getShipClass());
        if (ship.getFuelLevel() >= maxFuel) {
            player.sendActionBar("§e[Engineering] Fuel tank is already full.");
            return false;
        }

        int added = plugin.getFuelManager().refuelFromInventory(ship, player);
        if (added == 0) {
            player.sendActionBar("§c[Engineering] No Dilithium Crystals in inventory.");
        } else {
            player.sendActionBar("§a[Engineering] Injected §f+" + added
                    + " §aunits — tank at §f" + ship.getFuelLevel() + "§a.");
        }
        return true; // refresh gauge
    }
}