package com.usermc.horizon.fuel;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.ShipClass;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all fuel-related operations: max capacity, refueling, consumption.
 *
 * Fuel is stored as an integer on the Ship object and persisted to MariaDB.
 * Warp cost scales with distance — longer jumps burn more Dilithium.
 */
public class FuelManager {

    /** Blocks of travel per one unit of fuel at warp. */
    private static final double BLOCKS_PER_FUEL_UNIT = 100.0;

    private final Horizon plugin;

    public FuelManager(Horizon plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Capacity
    // -----------------------------------------------------------------------

    public int getMaxFuel(ShipClass sc) {
        return switch (sc) {
            case SHUTTLE      -> 200;
            case FIGHTER      -> 300;
            case FRIGATE      -> 500;
            case CRUISER      -> 1_000;
            case CARRIER      -> 1_500;
            case DREADNOUGHT  -> 2_000;
            case ADMIN        -> 9_999;
        };
    }

    // -----------------------------------------------------------------------
    // Refueling
    // -----------------------------------------------------------------------

    /**
     * Consume Dilithium Crystals from the player's inventory to refuel the ship.
     * Returns how many fuel units were actually added.
     */
    public int refuelFromInventory(Ship ship, Player player) {
        int maxFuel     = getMaxFuel(ship.getShipClass());
        int currentFuel = ship.getFuelLevel();
        int needed      = maxFuel - currentFuel;

        if (needed <= 0) return 0;

        int fuelAdded = 0;
        ItemStack[] inv = player.getInventory().getContents();

        for (int i = 0; i < inv.length && needed > 0; i++) {
            ItemStack slot = inv[i];
            if (!FuelItem.is(slot)) continue;

            int crystals    = slot.getAmount();
            int fromStack   = crystals * FuelItem.FUEL_PER_CRYSTAL;

            if (fromStack <= needed) {
                // Use the entire stack
                fuelAdded += fromStack;
                needed    -= fromStack;
                player.getInventory().setItem(i, null);
            } else {
                // Use partial stack
                int crystalsNeeded = (int) Math.ceil((double) needed / FuelItem.FUEL_PER_CRYSTAL);
                int fuelFromPartial = crystalsNeeded * FuelItem.FUEL_PER_CRYSTAL;
                fuelAdded += fuelFromPartial;
                needed     = 0;
                slot.setAmount(crystals - crystalsNeeded);
            }
        }

        ship.setFuelLevel(Math.min(maxFuel, currentFuel + fuelAdded));
        ship.markDirty();
        return fuelAdded;
    }

    // -----------------------------------------------------------------------
    // Consumption
    // -----------------------------------------------------------------------

    /** Calculate fuel cost for a warp jump of the given distance (in blocks). */
    public int calculateWarpCost(double distance) {
        return Math.max(1, (int) Math.ceil(distance / BLOCKS_PER_FUEL_UNIT));
    }

    public boolean hasEnoughFuel(Ship ship, int cost) {
        return ship.getFuelLevel() >= cost;
    }

    /** Deduct fuel. Returns false if not enough (does NOT deduct in that case). */
    public boolean consumeFuel(Ship ship, int amount) {
        if (ship.getFuelLevel() < amount) return false;
        ship.setFuelLevel(ship.getFuelLevel() - amount);
        ship.markDirty();
        return true;
    }

    // -----------------------------------------------------------------------
    // Display helpers
    // -----------------------------------------------------------------------

    /** Returns a coloured fuel bar string for chat display. */
    public String fuelBar(Ship ship) {
        int current = ship.getFuelLevel();
        int max     = getMaxFuel(ship.getShipClass());
        int bars    = 20;
        int filled  = (int) ((double) current / max * bars);

        String colour = filled > 14 ? "§a" : filled > 7 ? "§e" : "§c";
        return colour + "█".repeat(filled) + "§8" + "█".repeat(bars - filled)
                + " §f" + current + "§8/§f" + max;
    }
}