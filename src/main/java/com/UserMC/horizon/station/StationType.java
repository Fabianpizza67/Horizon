package com.usermc.horizon.station;

import org.bukkit.Material;

/**
 * Every type of functional station block that can be placed on a ship.
 *
 * blockMaterial — the Minecraft block this station item becomes when placed.
 * The block itself carries no special data; the StationManager registry
 * identifies it purely by world location.
 */
public enum StationType {

    HELM        ("Helm Console",         Material.BARREL,           "§7Pilot and helm control"),
    NAVIGATION  ("Navigation Console",   Material.CARTOGRAPHY_TABLE,"§7Warp drive and star charts"),
    ENGINEERING ("Engineering Console",  Material.BLAST_FURNACE,    "§7Fuel, power and ship status");

    // Future: TACTICAL, SCIENCE, MEDICAL, CREW_TERMINAL

    private final String   displayName;
    private final Material blockMaterial;
    private final String   description;

    StationType(String displayName, Material blockMaterial, String description) {
        this.displayName   = displayName;
        this.blockMaterial = blockMaterial;
        this.description   = description;
    }

    public String   getDisplayName()  { return displayName; }
    public Material getBlockMaterial(){ return blockMaterial; }
    public String   getDescription()  { return description; }
}