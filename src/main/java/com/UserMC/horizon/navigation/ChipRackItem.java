package com.usermc.horizon.navigation;

import com.usermc.horizon.Horizon;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The Chip Rack — a placeable block that displays and stores up to 27
 * navigation chips. Deliberately cheap to craft since it's pure storage/
 * display, encouraging players to build proper map rooms with several.
 *
 * Uses SMITHING_TABLE as its block form — chosen because it has no
 * meaningful use elsewhere in Horizon and its vanilla GUI is fully
 * intercepted by NavigationChipListener before it would ever open.
 */
public final class ChipRackItem {

    public static final NamespacedKey PDC_IS_RACK =
            new NamespacedKey(Horizon.getInstance(), "chip_rack_item");

    private ChipRackItem() {}

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.SMITHING_TABLE);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§b◈ Chip Rack");
        meta.setLore(List.of(
                "§7Stores and displays up to " + ChipRack.CAPACITY + " navigation chips.",
                "§8Place on your ship to activate."
        ));
        meta.getPersistentDataContainer().set(PDC_IS_RACK, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRackItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(PDC_IS_RACK, PersistentDataType.BOOLEAN);
    }

    /**
     * Recipe: I G I / G G G / I G I — 4 Iron Ingot + 5 Glass Pane.
     * Cheap and quick, matching its role as pure storage/display.
     */
    public static void registerRecipe(Horizon plugin) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(plugin, "chip_rack"), create());
        r.shape("IGI", "GGG", "IGI");
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('G', Material.GLASS_PANE);
        plugin.getServer().addRecipe(r);
    }
}