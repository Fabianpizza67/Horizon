package com.usermc.horizon.navigation;

import com.usermc.horizon.Horizon;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Navigation Chip — a physical, tradeable item whose destination data lives
 * entirely in its PersistentDataContainer. No central database row per chip;
 * the item itself is the source of truth, same as any other Minecraft item.
 * This is what makes chips naturally giveable, droppable, and rackable
 * without any extra sync logic.
 *
 * PDC layout:
 *   chip_type      (STRING)  — ChipType enum name
 *   chip_name      (STRING)  — display/location name, renameable after creation
 *   chip_world     (STRING)  — world name
 *   chip_x/y/z     (INT)     — destination coordinates
 *   chip_uses_left (INT)     — -1 for REUSABLE (infinite), else remaining uses
 */
public final class ChipItem {

    private static NamespacedKey key(String name) {
        return new NamespacedKey(Horizon.getInstance(), name);
    }

    private static final NamespacedKey KEY_TYPE  = key("chip_type");
    private static final NamespacedKey KEY_NAME  = key("chip_name");
    private static final NamespacedKey KEY_WORLD = key("chip_world");
    private static final NamespacedKey KEY_X     = key("chip_x");
    private static final NamespacedKey KEY_Y     = key("chip_y");
    private static final NamespacedKey KEY_Z     = key("chip_z");
    private static final NamespacedKey KEY_USES  = key("chip_uses_left");

    private ChipItem() {}

    // -----------------------------------------------------------------------
    // Creation
    // -----------------------------------------------------------------------

    public static ItemStack create(ChipType type, String name, Location destination) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta  meta = item.getItemMeta();

        meta.setDisplayName("§b◈ " + name);
        meta.setLore(List.of(
                "§7" + type.getDisplayName(),
                type.getLoreHint(),
                "",
                "§8" + destination.getWorld().getName() + " "
                        + destination.getBlockX() + " " + destination.getBlockY() + " " + destination.getBlockZ()
        ));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_TYPE,  PersistentDataType.STRING, type.name());
        pdc.set(KEY_NAME,  PersistentDataType.STRING, name);
        pdc.set(KEY_WORLD, PersistentDataType.STRING, destination.getWorld().getName());
        pdc.set(KEY_X,     PersistentDataType.INTEGER, destination.getBlockX());
        pdc.set(KEY_Y,     PersistentDataType.INTEGER, destination.getBlockY());
        pdc.set(KEY_Z,     PersistentDataType.INTEGER, destination.getBlockZ());
        pdc.set(KEY_USES,  PersistentDataType.INTEGER, type.getMaxUses());

        item.setItemMeta(meta);
        refreshLore(item);
        return item;
    }

    // -----------------------------------------------------------------------
    // Identification
    // -----------------------------------------------------------------------

    public static boolean isChip(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_TYPE, PersistentDataType.STRING);
    }

    public static ChipType getType(ItemStack item) {
        if (!isChip(item)) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(KEY_TYPE, PersistentDataType.STRING);
        try { return ChipType.valueOf(raw); } catch (Exception e) { return null; }
    }

    public static String getName(ItemStack item) {
        if (!isChip(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_NAME, PersistentDataType.STRING);
    }

    public static Location getDestination(ItemStack item, org.bukkit.Server server) {
        if (!isChip(item)) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String worldName = pdc.get(KEY_WORLD, PersistentDataType.STRING);
        World world = server.getWorld(worldName);
        if (world == null) return null;
        int x = pdc.getOrDefault(KEY_X, PersistentDataType.INTEGER, 0);
        int y = pdc.getOrDefault(KEY_Y, PersistentDataType.INTEGER, 0);
        int z = pdc.getOrDefault(KEY_Z, PersistentDataType.INTEGER, 0);
        return new Location(world, x, y, z);
    }

    public static int getUsesLeft(ItemStack item) {
        if (!isChip(item)) return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(KEY_USES, PersistentDataType.INTEGER, 0);
    }

    public static boolean isInfinite(ItemStack item) { return getUsesLeft(item) < 0; }

    // -----------------------------------------------------------------------
    // Mutation — rename, consume a use
    // -----------------------------------------------------------------------

    public static void rename(ItemStack item, String newName) {
        if (!isChip(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY_NAME, PersistentDataType.STRING, newName);
        meta.setDisplayName("§b◈ " + newName);
        item.setItemMeta(meta);
    }

    /**
     * Consume one use. Returns true if the chip should now be removed
     * (single/multi chip ran out), false if it survives (reusable, or
     * multi-chip with uses remaining).
     */
    public static boolean consumeUse(ItemStack item) {
        if (!isChip(item)) return true;
        int uses = getUsesLeft(item);
        if (uses < 0) return false; // infinite — never consumed

        int remaining = uses - 1;
        if (remaining <= 0) return true; // out of uses — caller should remove the item

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY_USES, PersistentDataType.INTEGER, remaining);
        item.setItemMeta(meta);
        refreshLore(item);
        return false;
    }

    // -----------------------------------------------------------------------
    // Lore refresh — keeps the displayed uses-remaining line accurate
    // -----------------------------------------------------------------------

    private static void refreshLore(ItemStack item) {
        if (!isChip(item)) return;
        ChipType type = getType(item);
        int uses = getUsesLeft(item);
        Location dest = getDestination(item, Horizon.getInstance().getServer());

        ItemMeta meta = item.getItemMeta();
        meta.setLore(List.of(
                "§7" + type.getDisplayName(),
                uses < 0 ? "§8Unlimited uses" : "§8" + uses + " use(s) remaining",
                "",
                dest != null
                        ? "§8" + dest.getWorld().getName() + " " + dest.getBlockX() + " " + dest.getBlockY() + " " + dest.getBlockZ()
                        : "§8Unknown world"
        ));
        item.setItemMeta(meta);
    }
}