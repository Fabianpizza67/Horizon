package com.usermc.horizon.crew;

import java.util.UUID;

/**
 * Represents a single NPC crew member aboard a ship.
 *
 * Each crew member has:
 *  - A Citizens NPC ID (the visual representation aboard the ship)
 *  - A role that determines their function
 *  - A skill level (1–10) that scales their effectiveness
 *  - A morale value (0–100) that decays without pay and affects performance
 *  - A salary (energy credits per in-game day) owed to them
 *
 * Morale thresholds:
 *  ≥ 75  — Full effectiveness
 *  50–74 — Minor penalties (autopilot may drift slightly)
 *  25–49 — Significant penalties (half effectiveness)
 *   0–24 — Critical — crew member may desert
 */
public class CrewMember {

    private final UUID     crewId;
    private final UUID     shipId;
    private       int      npcId;         // Citizens NPC ID, -1 if Citizens not available
    private       String   name;
    private       String   species;
    private       CrewRole role;
    private       int      skillLevel;    // 1–10
    private       double   morale;        // 0.0–100.0
    private       int      salary;        // EC per in-game day
    private       boolean  dirty;

    public CrewMember(UUID crewId, UUID shipId, int npcId, String name, String species,
                      CrewRole role, int skillLevel, double morale, int salary) {
        this.crewId     = crewId;
        this.shipId     = shipId;
        this.npcId      = npcId;
        this.name       = name;
        this.species    = species;
        this.role       = role;
        this.skillLevel = skillLevel;
        this.morale     = morale;
        this.salary     = salary;
        this.dirty      = false;
    }

    // --- Identity ---
    public UUID   getCrewId() { return crewId; }
    public UUID   getShipId() { return shipId; }

    // --- Citizens ---
    public int  getNpcId()        { return npcId; }
    public void setNpcId(int id)  { this.npcId = id; markDirty(); }
    public boolean hasCitizensNpc() { return npcId >= 0; }

    // --- Personal ---
    public String getName()    { return name; }
    public String getSpecies() { return species; }

    public void setName(String n)    { this.name    = n; markDirty(); }
    public void setSpecies(String s) { this.species = s; markDirty(); }

    // --- Role & Skill ---
    public CrewRole getRole()       { return role; }
    public int      getSkillLevel() { return skillLevel; }

    public void setRole(CrewRole r)      { this.role       = r;  markDirty(); }
    public void setSkillLevel(int level) { this.skillLevel = Math.clamp(level, 1, 10); markDirty(); }

    // --- Morale ---
    public double getMorale() { return morale; }

    public void setMorale(double m) {
        this.morale = Math.clamp(m, 0.0, 100.0);
        markDirty();
    }

    public void adjustMorale(double delta) { setMorale(this.morale + delta); }

    /** Effectiveness multiplier from morale: 0.0 (critical) to 1.0 (full). */
    public double moraleMultiplier() {
        if (morale >= 75) return 1.0;
        if (morale >= 50) return 0.75;
        if (morale >= 25) return 0.5;
        return 0.25;
    }

    public String moraleColour() {
        if (morale >= 75) return "§a";
        if (morale >= 50) return "§e";
        if (morale >= 25) return "§6";
        return "§c";
    }

    // --- Salary ---
    public int  getSalary()     { return salary; }
    public void setSalary(int s){ this.salary = Math.max(0, s); markDirty(); }

    // --- Dirty flag ---
    public boolean isDirty()    { return dirty; }
    public void    markDirty()  { this.dirty = true; }
    public void    clearDirty() { this.dirty = false; }

    @Override
    public String toString() {
        return name + " [" + species + "] — " + role.getDisplayName()
                + " Sk." + skillLevel + " Mo." + String.format("%.0f", morale) + "%";
    }
}