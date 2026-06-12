package com.usermc.horizon.fuel;

import com.usermc.horizon.Horizon;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Dilithium Crystal — the warp fuel item for Horizon.
 *
 * Material: AMETHYST_SHARD (looks crystalline, fits the theme).
 * Identified by a PersistentDataContainer tag so regular amethyst shards
 * won't accidentally be consumed as fuel.
 *
 * Each crystal = FUEL_PER_CRYSTAL units of warp fuel.
 */
public class FuelItem {

    public static final int  FUEL_PER_CRYSTAL = 50;
    public static final NamespacedKey PDC_KEY =
            new NamespacedKey(Horizon.getInstance(), "dilithium_crystal");

    private FuelItem() {}

    /** Create a stack of Dilithium Crystals. */
    public static ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, amount));
        ItemMeta  meta = item.getItemMeta();

        meta.setDisplayName("§b⚡ Dilithium Crystal");
        meta.setLore(List.of(
                "§7A refined crystalline energy matrix,",
                "§7essential for warp field generation.",
                "",
                "§8Fuel value: §b" + FUEL_PER_CRYSTAL + " §8units per crystal"
        ));
        meta.getPersistentDataContainer().set(PDC_KEY, PersistentDataType.BOOLEAN, true);

        item.setItemMeta(meta);
        return item;
    }

    /** Returns true if this item is a Horizon Dilithium Crystal. */
    public static boolean is(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(PDC_KEY, PersistentDataType.BOOLEAN);
    }
}