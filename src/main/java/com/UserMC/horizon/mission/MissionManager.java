package com.usermc.horizon.mission;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.MissionDAO;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.warp.WarpBeacon;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central mission board manager.
 *
 * Board holds up to MAX_BOARD_SIZE available missions generated from warp beacons.
 * Refreshes every REFRESH_INTERVAL_TICKS (default 30 min).
 *
 * Completion trigger: WarpManager calls checkArrival(ship, beacon) when a
 * ship successfully completes a warp jump. Any passenger who has an ACTIVE
 * mission targeting that beacon gets it completed and receives rewards.
 *
 * Players can hold at most MAX_ACTIVE_PER_PLAYER active missions simultaneously.
 */
public class MissionManager {

    private static final int  MAX_BOARD_SIZE        = 12;
    private static final int  MAX_ACTIVE_PER_PLAYER = 2;
    private static final long REFRESH_INTERVAL_TICKS= 20L * 60 * 30; // 30 minutes

    private final Horizon          plugin;
    private final MissionDAO       dao;
    private final MissionGenerator generator;

    /** All missions currently on the board or active. */
    private final Map<UUID, Mission> missions = new LinkedHashMap<>();

    /** Quick lookup: playerUUID → their active missions. */
    private final Map<UUID, List<Mission>> playerMissions = new HashMap<>();

    private BukkitTask refreshTask;

    public MissionManager(Horizon plugin) {
        this.plugin    = plugin;
        this.dao       = new MissionDAO(plugin);
        this.generator = new MissionGenerator();
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        for (Mission m : dao.loadActive()) {
            missions.put(m.getMissionId(), m);
            if (m.getStatus() == MissionStatus.ACTIVE && m.getAcceptedBy() != null) {
                playerMissions.computeIfAbsent(m.getAcceptedBy(), k -> new ArrayList<>()).add(m);
            }
        }
        plugin.getLogger().info("Loaded " + missions.size() + " mission(s).");
        if (availableMissions().size() < 3) refreshBoard(); // seed board on first run
        startRefreshTask();
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
    }

    private void startRefreshTask() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::cleanAndRefresh, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    // -----------------------------------------------------------------------
    // Board management
    // -----------------------------------------------------------------------

    /** Remove expired/completed missions and generate new ones to fill the board. */
    public void cleanAndRefresh() {
        // Remove expired and completed
        missions.entrySet().removeIf(e -> {
            Mission m = e.getValue();
            if (m.getStatus() == MissionStatus.COMPLETED
                    || m.getStatus() == MissionStatus.FAILED) return true;
            if (m.isExpired() && m.getStatus() == MissionStatus.AVAILABLE) {
                m.expire();
                dao.save(m);
                return true;
            }
            return false;
        });
        refreshBoard();
    }

    private void refreshBoard() {
        Collection<WarpBeacon> beacons = plugin.getWarpManager().getAllBeacons();
        if (beacons.isEmpty()) return;

        int needed = MAX_BOARD_SIZE - (int) availableMissions().count();
        if (needed <= 0) return;

        List<WarpBeacon> beaconList = new ArrayList<>(beacons);
        Collections.shuffle(beaconList);

        int generated = 0;
        for (WarpBeacon beacon : beaconList) {
            if (generated >= needed) break;
            int perBeacon = Math.min(2, needed - generated);
            for (Mission m : generator.generateFor(beacon, perBeacon)) {
                missions.put(m.getMissionId(), m);
                dao.save(m);
                generated++;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Accept
    // -----------------------------------------------------------------------

    /**
     * Player accepts a mission from the board.
     * Returns the accepted Mission, or null with a reason message if failed.
     */
    public Mission accept(Player player, UUID missionId) {
        Mission m = missions.get(missionId);
        if (m == null || !m.isAvailable()) return null;

        List<Mission> active = playerMissions.getOrDefault(player.getUniqueId(), List.of());
        if (active.size() >= MAX_ACTIVE_PER_PLAYER) return null;

        m.accept(player.getUniqueId());
        playerMissions.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(m);
        dao.save(m);
        return m;
    }

    // -----------------------------------------------------------------------
    // Completion — called by WarpManager on ship arrival at a beacon
    // -----------------------------------------------------------------------

    public void checkArrival(Ship ship, WarpBeacon beacon) {
        for (UUID uuid : ship.getPassengers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            List<Mission> active = playerMissions.getOrDefault(uuid, List.of());
            List<Mission> toComplete = active.stream()
                    .filter(m -> m.getStatus() == MissionStatus.ACTIVE
                            && m.getTargetBeaconId().equals(beacon.getBeaconId()))
                    .toList();

            for (Mission m : toComplete) {
                completeMission(player, m);
            }
        }
    }

    private void completeMission(Player player, Mission m) {
        m.complete();
        dao.save(m);
        List<Mission> active = playerMissions.get(player.getUniqueId());
        if (active != null) active.remove(m);

        // Rewards
        plugin.getEconomyManager().addBalance(player, m.getRewardEc());
        plugin.getRankManager().awardMissionXp(player, m.getRewardXp());
        plugin.getRankManager().getOrCreate(player).addMissionsCompleted(1);

        // Notify
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        player.sendTitle("§a§lMISSION COMPLETE", "§7" + m.getTitle(), 5, 50, 15);
        player.sendMessage("§a§l[Mission] §r§fMission complete: §b" + m.getTitle());
        player.sendMessage("§7Reward: §6+" + m.getRewardEc() + " EC  §b+" + m.getRewardXp() + " XP");
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public List<Mission> getBoardMissions() {
        return missions.values().stream()
                .filter(Mission::isAvailable)
                .sorted(Comparator.comparingInt(Mission::getDifficulty))
                .toList();
    }

    public List<Mission> getActiveMissions(UUID playerUUID) {
        return playerMissions.getOrDefault(playerUUID, Collections.emptyList())
                .stream().filter(m -> m.getStatus() == MissionStatus.ACTIVE).toList();
    }

    public int getMaxActivePerPlayer() { return MAX_ACTIVE_PER_PLAYER; }

    private java.util.stream.Stream<Mission> availableMissions() {
        return missions.values().stream().filter(Mission::isAvailable);
    }
}