package com.usermc.horizon.crew;

/**
 * The role a crew member holds aboard a ship.
 * Each role provides a specific passive bonus when filled by a skilled crew member.
 *
 * Future systems (weapons, sensors, medical) will consume these bonuses directly.
 */
public enum CrewRole {

    HELMSMAN      ("Helmsman",               "Pilots the ship. Enables autopilot navigation."),
    ENGINEER      ("Engineer",               "Maintains ship systems. Reduces warp fuel consumption by up to 20%."),
    TACTICAL      ("Tactical Officer",       "Operates weapons. Improves weapon accuracy and reload speed."),
    SCIENCE       ("Science Officer",        "Runs sensors. Extends detection range and anomaly analysis."),
    MEDICAL       ("Medical Officer",        "Staffs the medical bay. Heals crew and treats combat injuries."),
    COMMUNICATIONS("Communications Officer", "Manages comms. Extends hailing range and enables signal intercept.");

    private final String displayName;
    private final String description;

    CrewRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public static CrewRole fromString(String s) {
        if (s == null) return null;
        for (CrewRole r : values()) {
            if (r.name().equalsIgnoreCase(s) || r.displayName.equalsIgnoreCase(s)) return r;
        }
        return null;
    }
}