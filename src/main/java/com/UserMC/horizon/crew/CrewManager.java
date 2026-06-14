package com.usermc.horizon.crew;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.CrewDAO;
import com.usermc.horizon.ship.Ship;
import org.bukkit.entity.EntityType;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Central manager for all NPC crew members across all ships.
 *
 * Citizens integration is soft — if Citizens is not installed, all hiring still
 * works and crew is tracked in the database, but no visual NPCs are spawned.
 *
 * Morale decay:
 *   Every `moraleDecayIntervalTicks` ticks, each crew member loses a small
 *   amount of morale. This represents unpaid salary pressure. When morale
 *   hits 0, the crew member deserts (removed from ship and NPC despawns).
 *   Paying salaries (future economy system) will restore morale.
 *
 * Crew are stored per-ship in a Map<UUID shipId, List<CrewMember>>.
 */
public class CrewManager {

    private final Horizon  plugin;
    private final CrewDAO  dao;
    private final boolean  citizensAvailable;

    /** All crew, indexed by crewId for fast lookup. */
    private final Map<UUID, CrewMember>       allCrew    = new HashMap<>();
    /** Crew grouped by ship for ship-level operations. */
    private final Map<UUID, List<CrewMember>> shipCrew   = new HashMap<>();

    private BukkitTask moraleDecayTask;
    private BukkitTask autoSaveTask;

    /** How many morale points lost per decay tick. */
    private static final double MORALE_DECAY_PER_TICK = 0.5;
    /** How often morale decays (ticks). Default 5 minutes = 6000 ticks. */
    private static final long   MORALE_DECAY_INTERVAL = 6_000L;
    /** How often dirty crew (morale changes, hires, etc.) get flushed to DB. 60s. */
    private static final long   AUTO_SAVE_INTERVAL    = 1_200L;

    public CrewManager(Horizon plugin) {
        this.plugin  = plugin;
        this.dao     = new CrewDAO(plugin);
        this.citizensAvailable = plugin.getServer().getPluginManager().isPluginEnabled("Citizens");

        if (!citizensAvailable) {
            plugin.getLogger().warning("Citizens not found — crew NPCs will not spawn visually.");
        }
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        for (CrewMember cm : dao.loadAll()) {
            allCrew.put(cm.getCrewId(), cm);
            shipCrew.computeIfAbsent(cm.getShipId(), k -> new ArrayList<>()).add(cm);
        }
        plugin.getLogger().info("Loaded " + allCrew.size() + " crew member(s).");
        startMoraleDecay();
        startAutoSave();
    }

    /** Called from onDisable(). Stops periodic tasks and does a final SYNCHRONOUS flush. */
    public void saveAll() {
        if (moraleDecayTask != null) moraleDecayTask.cancel();
        if (autoSaveTask    != null) autoSaveTask.cancel();
        flushDirty();
    }

    /**
     * Synchronously persist every dirty crew member (morale, hires, role changes, etc.).
     * Safe to call mid-session (e.g. an admin /ship save command).
     */
    public void flushDirty() {
        int saved = 0;
        for (CrewMember cm : allCrew.values()) {
            if (cm.isDirty()) {
                dao.saveSync(cm);
                saved++;
            }
        }
        if (saved > 0)
            plugin.getLogger().info("Flushed " + saved + " dirty crew member(s) to database.");
    }

    /**
     * Periodic safety net for morale decay and other crew state changes —
     * without this, morale drift was only ever persisted at clean shutdown.
     */
    private void startAutoSave() {
        autoSaveTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, () -> {
                    for (CrewMember cm : allCrew.values()) {
                        if (cm.isDirty()) dao.save(cm);
                    }
                }, AUTO_SAVE_INTERVAL, AUTO_SAVE_INTERVAL);
    }

    private void startMoraleDecay() {
        moraleDecayTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tickMoraleDecay,
                        MORALE_DECAY_INTERVAL, MORALE_DECAY_INTERVAL);
    }

    private void tickMoraleDecay() {
        List<CrewMember> deserters = new ArrayList<>();
        for (CrewMember cm : allCrew.values()) {
            cm.adjustMorale(-MORALE_DECAY_PER_TICK);
            if (cm.getMorale() <= 0) deserters.add(cm);
        }
        for (CrewMember cm : deserters) {
            plugin.getLogger().info("Crew member " + cm.getName()
                    + " has deserted ship " + cm.getShipId() + " (zero morale).");
            fire(cm, true);
        }
    }

    // -----------------------------------------------------------------------
    // Hiring
    // -----------------------------------------------------------------------

    /**
     * Hire a new crew member for the given ship.
     *
     * @param ship     The ship they're joining
     * @param name     NPC display name
     * @param species  Species flavour text
     * @param role     Their station role
     * @param skill    Starting skill level (1–10)
     * @param salary   Daily salary in Energy Credits
     * @param spawnAt  World location to spawn the Citizens NPC (usually ship core +1Y)
     * @return The created CrewMember
     */
    public CrewMember hire(Ship ship, String name, String species, CrewRole role,
                           int skill, int salary, Location spawnAt) {

        List<CrewMember> existing = shipCrew.getOrDefault(ship.getShipId(), List.of());
        int bonusSlots = plugin.getRankManager().getBonusCrewSlots(ship.getOwnerUUID());
        int maxCrew = ship.getShipClass().getMaxCrewSlots() + bonusSlots;
        if (existing.size() >= maxCrew) {
            throw new IllegalStateException("Ship has reached max crew slots ("
                    + maxCrew + (bonusSlots > 0 ? ", includes +" + bonusSlots + " from rank" : "") + ").");
        }

        UUID crewId = UUID.randomUUID();
        int  npcId  = -1;

        // Spawn Citizens NPC if available
        if (citizensAvailable && spawnAt != null) {
            try {
                NPCRegistry registry = CitizensAPI.getNPCRegistry();
                NPC npc = registry.createNPC(EntityType.PLAYER, name);
                npc.spawn(spawnAt);
                npc.setProtected(true);                    // Can't be attacked
                npc.getNavigator().setPaused(true);        // Don't wander off the ship
                npcId = npc.getId();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to spawn Citizens NPC for " + name + ": " + e.getMessage());
            }
        }

        CrewMember cm = new CrewMember(crewId, ship.getShipId(), npcId, name, species,
                role, skill, 100.0, salary);
        allCrew.put(crewId, cm);
        shipCrew.computeIfAbsent(ship.getShipId(), k -> new ArrayList<>()).add(cm);
        dao.save(cm);

        return cm;
    }

    // -----------------------------------------------------------------------
    // Firing / Deserting
    // -----------------------------------------------------------------------

    /**
     * Remove a crew member from their ship.
     *
     * @param cm       The crew member to remove
     * @param deserted True if they deserted (morale = 0), false if dismissed
     */
    public void fire(CrewMember cm, boolean deserted) {
        // Stop autopilot if this crew member was the helmsman
        if (cm.getRole() == CrewRole.HELMSMAN) {
            Ship ship = plugin.getShipManager().getById(cm.getShipId());
            if (ship != null) {
                plugin.getAutoPilot().stopAutoPilot(ship);
            }
        }

        // Despawn Citizens NPC
        if (citizensAvailable && cm.hasCitizensNpc()) {
            try {
                NPC npc = CitizensAPI.getNPCRegistry().getById(cm.getNpcId());
                if (npc != null) npc.destroy();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to destroy NPC for " + cm.getName());
            }
        }

        allCrew.remove(cm.getCrewId());
        List<CrewMember> list = shipCrew.get(cm.getShipId());
        if (list != null) list.remove(cm);

        dao.delete(cm.getCrewId());
    }

    // -----------------------------------------------------------------------
    // Ship movement — teleport all crew NPCs when ship moves
    // -----------------------------------------------------------------------

    /**
     * Called by the movement engine after every ship move.
     * Teleports each crew NPC by the movement delta.
     */
    public void teleportCrewNpcs(Ship ship, int dx, int dy, int dz) {
        if (!citizensAvailable) return;

        List<CrewMember> crew = getCrewForShip(ship.getShipId());
        if (crew.isEmpty()) return;

        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        for (CrewMember cm : crew) {
            if (!cm.hasCitizensNpc()) continue;
            try {
                NPC npc = registry.getById(cm.getNpcId());
                if (npc == null || !npc.isSpawned()) continue;
                Location cur = npc.getEntity().getLocation();
                npc.teleport(cur.add(dx, dy, dz),
                        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to move crew NPC " + cm.getName());
            }
        }
    }

    /** Rotate all crew NPC positions 90° around the ship core. */
    public void teleportCrewNpcsRotate(Ship ship, int coreCx, int coreCz, boolean cw) {
        if (!citizensAvailable) return;
        List<CrewMember> crew = getCrewForShip(ship.getShipId());
        if (crew.isEmpty()) return;
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        for (CrewMember cm : crew) {
            if (!cm.hasCitizensNpc()) continue;
            try {
                NPC npc = registry.getById(cm.getNpcId());
                if (npc == null || !npc.isSpawned()) continue;
                Location cur = npc.getEntity().getLocation();
                double dx = cur.getX() - coreCx;
                double dz = cur.getZ() - coreCz;
                double newDx = cw ? -dz :  dz;
                double newDz = cw ?  dx : -dx;
                float  newYaw = cur.getYaw() + (cw ? 90f : -90f);
                npc.teleport(new Location(cur.getWorld(),
                                coreCx + newDx, cur.getY(), coreCz + newDz,
                                newYaw, cur.getPitch()),
                        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to rotate crew NPC " + cm.getName());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Morale helpers
    // -----------------------------------------------------------------------

    /** Boost morale of all crew on a ship (e.g., after completing a mission). */
    public void boostMorale(UUID shipId, double amount) {
        for (CrewMember cm : getCrewForShip(shipId)) {
            cm.adjustMorale(amount);
            dao.save(cm);
        }
    }

    // -----------------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------------

    public List<CrewMember> getCrewForShip(UUID shipId) {
        return shipCrew.getOrDefault(shipId, Collections.emptyList());
    }

    public Optional<CrewMember> getHelmsman(UUID shipId) {
        return getCrewForShip(shipId).stream()
                .filter(cm -> cm.getRole() == CrewRole.HELMSMAN)
                .findFirst();
    }

    public CrewMember getByName(UUID shipId, String name) {
        return getCrewForShip(shipId).stream()
                .filter(cm -> cm.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public boolean isCitizensAvailable() { return citizensAvailable; }
}