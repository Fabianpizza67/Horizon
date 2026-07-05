package com.usermc.horizon.navigation;

import org.bukkit.Material;

/**
 * The three tiers of navigation chip.
 *
 * SINGLE   — cheap, one warp then consumed
 * MULTI    — moderate cost, 5 uses before consumed
 * REUSABLE — expensive, permanent
 *
 * Each chip stores its location data in PersistentDataContainer tags
 * so it works as a normal inventory item — tradeable, droppable, rackable.
 */
public enum ChipType {

    SINGLE  ("Single-Use Chip",  Material.PAPER,        1,  "§8One-time warp, then consumed"),
    MULTI   ("Multi-Use Chip",   Material.MAP,           5,  "§85 uses before consumed"),
    REUSABLE("Reusable Chip",    Material.FILLED_MAP,   -1,  "§8Permanent — never consumed");

    private final String   displayName;
    private final Material material;
    private final int      maxUses;   // -1 = infinite
    private final String   loreHint;

    ChipType(String displayName, Material material, int maxUses, String loreHint) {
        this.displayName = displayName;
        this.material    = material;
        this.maxUses     = maxUses;
        this.loreHint    = loreHint;
    }

    public String   getDisplayName() { return displayName; }
    public Material getMaterial()    { return material; }
    public int      getMaxUses()     { return maxUses; }
    public String   getLoreHint()    { return loreHint; }
    public boolean  isInfinite()     { return maxUses < 0; }
}