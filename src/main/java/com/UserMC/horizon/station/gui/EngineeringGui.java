package com.usermc.horizon.station.gui;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.economy.CreditChip;
import com.usermc.horizon.rank.CaptainProfile;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.ShipStructure;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Engineering Console GUI — 4 rows.
 *
 * Row 0: Fuel gauge (9 coloured panes)
 * Row 1: Ship stats + refuel button
 * Row 2: Banking — balance display, deposit chips, withdraw options
 * Row 3: Crew morale summary + captain rank
 */
public class EngineeringGui extends HorizonGui {

    private static final int SLOT_REFUEL    = 9;
    private static final int SLOT_DEPOSIT   = 18;
    private static final int SLOT_W10       = 20;
    private static final int SLOT_W100      = 21;
    private static final int SLOT_W1000     = 22;
    private static final int SLOT_W5000     = 23;
    private static final int SLOT_BALANCE   = 19;

    public EngineeringGui(Ship ship, Player player) {
        super(ship, player, 4, "§b⚙ Engineering — " + ship.getName());
    }

    @Override
    public void build() {
        inventory.clear();
        Horizon plugin = Horizon.getInstance();
        int     fuel   = ship.getFuelLevel();
        int     maxFuel= plugin.getFuelManager().getMaxFuel(ship.getShipClass());

        // --- Row 0: Fuel gauge ---
        double ratio   = maxFuel == 0 ? 0 : (double) fuel / maxFuel;
        int    filled  = (int) Math.round(ratio * 9);
        Material mat   = ratio > 0.6 ? Material.LIME_STAINED_GLASS_PANE
                : ratio > 0.3 ? Material.YELLOW_STAINED_GLASS_PANE
                  :               Material.RED_STAINED_GLASS_PANE;
        String fuelSummary = "§7Fuel: §f" + fuel + " §8/ §f" + maxFuel
                + " §8(" + String.format("%.0f", ratio * 100) + "%)";
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, i < filled
                    ? makeItem(mat, "§b⚡ " + fuelSummary)
                    : makeItem(Material.GRAY_STAINED_GLASS_PANE, "§8▪ Empty — " + fuelSummary));
        }

        // --- Row 1: Refuel + ship stats ---
        boolean isFull = fuel >= maxFuel;
        inventory.setItem(SLOT_REFUEL, makeItem(
                isFull ? Material.BARRIER : Material.AMETHYST_SHARD,
                isFull ? "§8Fuel Tank Full" : "§a⚡ Inject Dilithium",
                isFull ? "§7Tank is already at capacity."
                        : "§7Consumes all Dilithium Crystals from your inventory.",
                "§8Each crystal = §b" + com.usermc.horizon.fuel.FuelItem.FUEL_PER_CRYSTAL + " §8units"
        ));

        ShipStructure struct = ship.getStructure();
        inventory.setItem(10, makeItem(Material.IRON_INGOT,
                "§7Class: §f" + ship.getShipClass().getDisplayName(),
                "§7Soft limit: §f" + ship.getShipClass().getSoftLimit(),
                "§7Hard limit: §f" + ship.getShipClass().getHardLimit()
        ));
        inventory.setItem(11, makeItem(Material.BRICKS,
                struct != null ? "§7Blocks: §f" + struct.getBlockCount() : "§eNo scan",
                struct != null ? "§7Size: §f" + struct.getWidth() + "×" + struct.getHeight()
                                 + "×" + struct.getLength() : "§7Shift+right-click Ship Core to scan"
        ));
        inventory.setItem(12, makeItem(Material.FEATHER,
                "§7Speed mult: §f" + String.format("%.0f%%", ship.getSpeedMultiplier() * 100),
                ship.getSpeedMultiplier() < 1.0 ? "§e⚠ Over soft limit" : "§aWithin limit"
        ));
        inventory.setItem(13, makeItem(Material.ENDER_PEARL,
                "§7Warp: §f" + ship.getWarpStatus(),
                "§7Status: §f" + ship.getStatus(),
                plugin.getWarpManager().isCharging(ship) ? "§e⚠ Warp charging..." : ""
        ));

        // --- Row 2: Banking ---
        long balance = plugin.getEconomyManager().getBalance(player);
        inventory.setItem(SLOT_BALANCE, makeItem(Material.GOLD_INGOT,
                "§6§lBalance: §f" + plugin.getEconomyManager().format(balance),
                "§710 EC  = §f1 Gold Nugget chip",
                "§7100 EC = §f1 Gold Ingot chip",
                "§71000 EC= §f1 Gold Block chip",
                "§8Deposit chips below to add to balance."
        ));
        inventory.setItem(SLOT_DEPOSIT, makeItem(
                Material.HOPPER,
                "§a⬇ Deposit Chips",
                "§7Converts all Credit Chips in your",
                "§7inventory into digital balance."
        ));

        // Withdraw buttons — grey out if insufficient funds
        withdrawButton(SLOT_W10,   10,   balance, "§710 EC");
        withdrawButton(SLOT_W100,  100,  balance, "§7100 EC");
        withdrawButton(SLOT_W1000, 1000, balance, "§71,000 EC");
        withdrawButton(SLOT_W5000, 5000, balance, "§75,000 EC");

        // --- Row 3: Crew morale + captain rank ---
        CaptainProfile profile = plugin.getRankManager().getOrCreate(player);
        inventory.setItem(27, makeItem(Material.NETHER_STAR,
                profile.getRank().getChatPrefix() + " §8— §7" + profile.getExperience() + " XP",
                "§7Missions: §f" + profile.getMissionsCompleted(),
                profile.getRank().isMaxRank() ? "§6Max rank achieved!" :
                        "§8+" + profile.xpToNextRank() + " XP to " + profile.getRank().next().getDisplayName()
        ));

        var crew = plugin.getCrewManager().getCrewForShip(ship.getShipId());
        int idx = 28;
        for (var cm : crew) {
            if (idx > 35) break;
            inventory.setItem(idx++, makeItem(Material.PLAYER_HEAD,
                    cm.moraleColour() + cm.getName(),
                    "§7" + cm.getSpecies() + " — " + cm.getRole().getDisplayName(),
                    "§7Sk.§f" + cm.getSkillLevel()
                            + "  Mo." + cm.moraleColour()
                            + String.format("%.0f", cm.getMorale()) + "%"
            ));
        }
        if (crew.isEmpty()) {
            inventory.setItem(28, makeItem(Material.BARRIER,
                    "§cNo Crew", "§7Hire crew via §f/ship crew hire"));
        }

        fillAll();
    }

    private void withdrawButton(int slot, long amount, long balance, String label) {
        boolean canAfford = balance >= amount;
        inventory.setItem(slot, makeItem(
                canAfford ? Material.GOLD_NUGGET : Material.GRAY_DYE,
                canAfford ? "§a⬆ Withdraw " + label : "§8Withdraw " + label,
                canAfford ? "§7Click to withdraw §f" + amount + " EC §7as chips."
                        : "§cInsufficient balance."
        ));
    }

    @Override
    public boolean handleClick(int slot, ClickType click) {
        Horizon plugin = Horizon.getInstance();

        if (slot == SLOT_REFUEL) {
            int maxFuel = plugin.getFuelManager().getMaxFuel(ship.getShipClass());
            if (ship.getFuelLevel() >= maxFuel) {
                player.sendActionBar("§e[Engineering] Fuel tank is full."); return false;
            }
            int added = plugin.getFuelManager().refuelFromInventory(ship, player);
            if (added == 0) player.sendActionBar("§c[Engineering] No Dilithium Crystals in inventory.");
            else player.sendActionBar("§a[Engineering] +" + added + " fuel units injected.");
            return true;
        }

        if (slot == SLOT_DEPOSIT) {
            long deposited = plugin.getEconomyManager().depositChips(player);
            if (deposited == 0) player.sendActionBar("§e[Engineering] No Credit Chips in inventory.");
            else player.sendActionBar("§a[Engineering] Deposited §6" + deposited + " EC §ato your balance.");
            return true;
        }

        long withdrawAmount = switch (slot) {
            case SLOT_W10   ->    10L;
            case SLOT_W100  ->   100L;
            case SLOT_W1000 -> 1_000L;
            case SLOT_W5000 -> 5_000L;
            default         ->     0L;
        };
        if (withdrawAmount > 0) {
            long given = plugin.getEconomyManager().withdraw(player, withdrawAmount);
            if (given == 0) player.sendActionBar("§c[Engineering] Insufficient balance.");
            else player.sendActionBar("§a[Engineering] Withdrew §6" + given + " EC §aas chips.");
            return true;
        }

        return false;
    }
}