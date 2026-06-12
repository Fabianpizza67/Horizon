package com.usermc.horizon.station;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.fuel.FuelItem;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Creates station block items and registers all Horizon crafting recipes.
 *
 * Station recipes:
 *   Helm Console        (BARREL)          — I C I / I R I / S S S
 *   Navigation Console  (CARTOGRAPHY_TABLE)— G P G / G M G / L L L
 *   Engineering Console (BLAST_FURNACE)   — I R I / R R R / I R I  ← simplified
 *
 * Fuel recipe:
 *   Dilithium Crystal ×2 — G G G / G A G / G G G
 *   (8 Glowstone Dust + 1 Amethyst Shard)
 */
public class StationItem {

    public static final NamespacedKey PDC_STATION_TYPE =
            new NamespacedKey(Horizon.getInstance(), "station_type");

    private StationItem() {}

    // -----------------------------------------------------------------------
    // Item creation
    // -----------------------------------------------------------------------

    public static ItemStack create(StationType type) {
        ItemStack item = new ItemStack(type.getBlockMaterial());
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§b⚙ " + type.getDisplayName());
        meta.setLore(List.of(
                type.getDescription(),
                "",
                "§8Place on your ship to activate."
        ));
        meta.getPersistentDataContainer()
                .set(PDC_STATION_TYPE, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public static StationType getType(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer()
                .get(PDC_STATION_TYPE, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return StationType.valueOf(raw); }
        catch (IllegalArgumentException e) { return null; }
    }

    public static boolean isStationItem(ItemStack item) { return getType(item) != null; }

    // -----------------------------------------------------------------------
    // Recipe registration
    // -----------------------------------------------------------------------

    public static void registerRecipes(Horizon plugin) {
        registerHelm(plugin);
        registerNavigation(plugin);
        registerEngineering(plugin);
        registerDilithium(plugin);
    }

    private static void registerHelm(Horizon plugin) {
        // I C I
        // I R I    I=IRON_INGOT  C=COMPASS  R=REDSTONE  S=STONE_BRICKS
        // S S S
        NamespacedKey key = new NamespacedKey(plugin, "helm_console");
        ShapedRecipe r = new ShapedRecipe(key, create(StationType.HELM));
        r.shape("ICI", "IRI", "SSS");
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('C', Material.COMPASS);
        r.setIngredient('R', Material.REDSTONE);
        r.setIngredient('S', Material.STONE_BRICKS);
        plugin.getServer().addRecipe(r);
    }

    private static void registerNavigation(Horizon plugin) {
        // G P G
        // G M G    G=GOLD_INGOT  P=PAPER  M=FILLED_MAP  L=LAPIS_LAZULI
        // L L L
        NamespacedKey key = new NamespacedKey(plugin, "navigation_console");
        ShapedRecipe r = new ShapedRecipe(key, create(StationType.NAVIGATION));
        r.shape("GPG", "GMG", "LLL");
        r.setIngredient('G', Material.GOLD_INGOT);
        r.setIngredient('P', Material.PAPER);
        r.setIngredient('M', Material.FILLED_MAP);
        r.setIngredient('L', Material.LAPIS_LAZULI);
        plugin.getServer().addRecipe(r);
    }

    private static void registerEngineering(Horizon plugin) {
        // I R I
        // R R R    I=IRON_INGOT  R=REDSTONE  (4 iron + 5 redstone — simple and achievable)
        // I R I
        NamespacedKey key = new NamespacedKey(plugin, "engineering_console");
        ShapedRecipe r = new ShapedRecipe(key, create(StationType.ENGINEERING));
        r.shape("IRI", "RRR", "IRI");
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('R', Material.REDSTONE);
        plugin.getServer().addRecipe(r);
    }

    /**
     * Dilithium Crystal fuel recipe.
     * G G G
     * G A G   8 Glowstone Dust + 1 Amethyst Shard → 2 Dilithium Crystals
     * G G G
     */
    private static void registerDilithium(Horizon plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "dilithium_crystal");
        // Result: 2 crystals per craft
        ItemStack result = FuelItem.create(2);
        ShapedRecipe r = new ShapedRecipe(key, result);
        r.shape("GGG", "GAG", "GGG");
        r.setIngredient('G', Material.GLOWSTONE_DUST);
        r.setIngredient('A', Material.AMETHYST_SHARD);
        plugin.getServer().addRecipe(r);
    }
}