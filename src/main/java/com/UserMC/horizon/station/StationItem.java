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

public class StationItem {

    public static final NamespacedKey PDC_STATION_TYPE =
            new NamespacedKey(Horizon.getInstance(), "station_type");

    private StationItem() {}

    public static ItemStack create(StationType type) {
        ItemStack item = new ItemStack(type.getBlockMaterial());
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§b⚙ " + type.getDisplayName());
        meta.setLore(List.of(type.getDescription(), "", "§8Place on your ship to activate."));
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

    public static void registerRecipes(Horizon plugin) {
        registerHelm(plugin);
        registerNavigation(plugin);
        registerEngineering(plugin);
        registerMissionTerminal(plugin);
        registerFactionTerminal(plugin);
        registerDilithium(plugin);
    }

    // -----------------------------------------------------------------------
    // Helm Console (BARREL)
    // I C I / I R I / S S S
    // I=IRON_INGOT  C=COMPASS  R=REDSTONE  S=STONE_BRICKS
    // -----------------------------------------------------------------------
    private static void registerHelm(Horizon plugin) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(plugin, "helm_console"),
                create(StationType.HELM));
        r.shape("ICI", "IRI", "SSS");
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('C', Material.COMPASS);
        r.setIngredient('R', Material.REDSTONE);
        r.setIngredient('S', Material.STONE_BRICKS);
        plugin.getServer().addRecipe(r);
    }

    // -----------------------------------------------------------------------
    // Navigation Console (CARTOGRAPHY_TABLE)
    // G P G / G M G / L L L
    // G=GOLD_INGOT  P=PAPER  M=FILLED_MAP  L=LAPIS_LAZULI
    // -----------------------------------------------------------------------
    private static void registerNavigation(Horizon plugin) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(plugin, "navigation_console"),
                create(StationType.NAVIGATION));
        r.shape("GPG", "GMG", "LLL");
        r.setIngredient('G', Material.GOLD_INGOT);
        r.setIngredient('P', Material.PAPER);
        r.setIngredient('M', Material.FILLED_MAP);
        r.setIngredient('L', Material.LAPIS_LAZULI);
        plugin.getServer().addRecipe(r);
    }

    // -----------------------------------------------------------------------
    // Engineering Console (BLAST_FURNACE)
    // I R I / R R R / I R I   (4 iron + 5 redstone)
    // -----------------------------------------------------------------------
    private static void registerEngineering(Horizon plugin) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(plugin, "engineering_console"),
                create(StationType.ENGINEERING));
        r.shape("IRI", "RRR", "IRI");
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('R', Material.REDSTONE);
        plugin.getServer().addRecipe(r);
    }

    // -----------------------------------------------------------------------
    // Mission Terminal (LECTERN)
    // P P P / P B P / P P P
    // P=PAPER  B=REDSTONE_BLOCK
    // -----------------------------------------------------------------------
    private static void registerMissionTerminal(Horizon plugin) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(plugin, "mission_terminal"),
                create(StationType.MISSION_TERMINAL));
        r.shape("PPP", "PBP", "PPP");
        r.setIngredient('P', Material.PAPER);
        r.setIngredient('B', Material.REDSTONE_BLOCK);
        plugin.getServer().addRecipe(r);
    }

    // -----------------------------------------------------------------------
    // Faction Terminal (LOOM)
    // G B G / B D B / G B G
    // G=GOLD_INGOT  B=BLUE_DYE  D=DIAMOND
    // -----------------------------------------------------------------------
    private static void registerFactionTerminal(Horizon plugin) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(plugin, "faction_terminal"),
                create(StationType.FACTION_TERMINAL));
        r.shape("GBG", "BDB", "GBG");
        r.setIngredient('G', Material.GOLD_INGOT);
        r.setIngredient('B', Material.BLUE_DYE);
        r.setIngredient('D', Material.DIAMOND);
        plugin.getServer().addRecipe(r);
    }

    // -----------------------------------------------------------------------
    // Dilithium Crystal (×2)
    // G G G / G A G / G G G
    // G=GLOWSTONE_DUST  A=AMETHYST_SHARD
    // -----------------------------------------------------------------------
    private static void registerDilithium(Horizon plugin) {
        ShapedRecipe r = new ShapedRecipe(new NamespacedKey(plugin, "dilithium_crystal"),
                FuelItem.create(2));
        r.shape("GGG", "GAG", "GGG");
        r.setIngredient('G', Material.GLOWSTONE_DUST);
        r.setIngredient('A', Material.AMETHYST_SHARD);
        plugin.getServer().addRecipe(r);
    }
}