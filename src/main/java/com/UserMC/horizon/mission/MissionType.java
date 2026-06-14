package com.usermc.horizon.mission;

public enum MissionType {
    DELIVERY  ("Delivery",   "§e", "Transport priority cargo to the destination."),
    SURVEY    ("Survey",     "§b", "Conduct a scientific survey at the target location."),
    PATROL    ("Patrol",     "§c", "Provide a security presence at the target location."),
    SALVAGE   ("Salvage",    "§6", "Recover valuable materials from the target area.");

    private final String displayName;
    private final String colour;
    private final String flavourText;

    MissionType(String displayName, String colour, String flavourText) {
        this.displayName = displayName;
        this.colour      = colour;
        this.flavourText = flavourText;
    }

    public String getDisplayName() { return displayName; }
    public String getColour()      { return colour; }
    public String getFlavourText() { return flavourText; }
}