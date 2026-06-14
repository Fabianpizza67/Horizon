package com.usermc.horizon.economy;

import com.usermc.horizon.Horizon;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Physical Energy Credit chips — tradeable items that represent wallet balance.
 *
 * Denominations:
 *   GOLD_NUGGET  =   10 EC  (small change)
 *   GOLD_INGOT   =  100 EC  (standard)
 *   GOLD_BLOCK   = 1000 EC  (large denomination)
 *
 * Chips are deposited at the Engineering Console to convert to digital balance,
 * and withdrawn from the Engineering Console to convert back to physical items.
 */
public class CreditChip {

    public static final NamespacedKey PDC_VALUE =
            new NamespacedKey(Horizon.getInstance(), "credit_chip_value");

    public static final int NUGGET_VALUE = 10;
    public static final int INGOT_VALUE  = 100;
    public static final int BLOCK_VALUE  = 1_000;

    private CreditChip() {}

    // -----------------------------------------------------------------------
    // Creation
    // -----------------------------------------------------------------------

    public static ItemStack nuggets(int amount) { return create(Material.GOLD_NUGGET,  NUGGET_VALUE, amount); }
    public static ItemStack ingots (int amount) { return create(Material.GOLD_INGOT,   INGOT_VALUE,  amount); }
    public static ItemStack blocks (int amount) { return create(Material.GOLD_BLOCK,   BLOCK_VALUE,  amount); }

    private static ItemStack create(Material mat, int value, int stackSize) {
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(64, stackSize)));
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§6⬡ Credit Chip §8[" + value + " EC]");
        meta.setLore(List.of(
                "§7Denomination: §f" + value + " Energy Credits",
                "§8Deposit at Engineering Console."
        ));
        meta.getPersistentDataContainer().set(PDC_VALUE, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
        return item;
    }

    // -----------------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------------

    /** Returns the EC value of this item, or 0 if it's not a credit chip. */
    public static int getValue(ItemStack item) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer v = meta.getPersistentDataContainer().get(PDC_VALUE, PersistentDataType.INTEGER);
        return v != null ? v : 0;
    }

    public static boolean isCreditChip(ItemStack item) { return getValue(item) > 0; }

    // -----------------------------------------------------------------------
    // Inventory helpers
    // -----------------------------------------------------------------------

    /** Scan a player's inventory, consume all chips and return total EC value. */
    public static long depositAll(org.bukkit.entity.Player player) {
        long total = 0;
        ItemStack[] inv = player.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            int val = getValue(inv[i]);
            if (val == 0) continue;
            total += (long) val * inv[i].getAmount();
            player.getInventory().setItem(i, null);
        }
        return total;
    }

    /**
     * Give a player chips worth exactly {@code amount} EC.
     * Uses the fewest items possible (blocks → ingots → nuggets).
     * Returns leftover EC that couldn't be dispensed (should be 0 if divisible by 10).
     */
    public static long dispense(org.bukkit.entity.Player player, long amount) {
        long remaining = amount;

        int blocks  = (int)(remaining / BLOCK_VALUE);
        remaining  %= BLOCK_VALUE;
        int ingots  = (int)(remaining / INGOT_VALUE);
        remaining  %= INGOT_VALUE;
        int nuggets = (int)(remaining / NUGGET_VALUE);
        remaining  %= NUGGET_VALUE;

        if (blocks  > 0) dispenseBatched(player, CreditChip::blocks,  blocks);
        if (ingots  > 0) dispenseBatched(player, CreditChip::ingots,  ingots);
        if (nuggets > 0) dispenseBatched(player, CreditChip::nuggets, nuggets);

        return remaining; // leftover (non-dispensable fraction)
    }

    @FunctionalInterface
    private interface ChipFactory { ItemStack make(int n); }

    private static void dispenseBatched(org.bukkit.entity.Player player, ChipFactory factory, int total) {
        while (total > 0) {
            int give = Math.min(64, total);
            player.getInventory().addItem(factory.make(give));
            total -= give;
        }
    }
}