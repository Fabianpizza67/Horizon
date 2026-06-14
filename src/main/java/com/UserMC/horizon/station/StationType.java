package com.usermc.horizon.station;

import org.bukkit.Material;

public enum StationType {
    HELM            ("Helm Console",      Material.BARREL,            "§7Pilot and helm control"),
    NAVIGATION      ("Navigation Console",Material.CARTOGRAPHY_TABLE, "§7Warp drive and star charts"),
    ENGINEERING     ("Engineering Console",Material.BLAST_FURNACE,    "§7Fuel, power and ship status"),
    MISSION_TERMINAL("Mission Terminal",  Material.LECTERN,           "§7Mission board and contracts");

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