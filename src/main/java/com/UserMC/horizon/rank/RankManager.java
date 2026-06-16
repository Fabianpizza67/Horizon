package com.usermc.horizon.rank;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.RankDAO;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manages CaptainProfiles for all online players.
 *
 * XP sources (called by other systems):
 *   - awardWarpXp(player, distance)     — called by WarpManager
 *   - awardMissionXp(player, xp)        — called by MissionManager
 *   - awardExploreXp(player, xp)        — future: anomaly/discovery system
 *
 * Auto-saves dirty profiles every 60 seconds.
 */
public class RankManager {

    private final Horizon  plugin;
    private final RankDAO  dao;

    private final Map<UUID, CaptainProfile> profiles = new HashMap<>();
    private BukkitTask autoSaveTask;

    public RankManager(Horizon plugin) {
        this.plugin = plugin;
        this.dao    = new RankDAO(plugin);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        for (CaptainProfile p : dao.loadAll()) {
            profiles.put(p.getPlayerUUID(), p);
        }
        plugin.getLogger().info("Loaded " + profiles.size() + " captain profile(s).");
        startAutoSave();
    }

    /** Called from onDisable(). Stops the auto-save loop and does a final SYNCHRONOUS flush. */
    public void saveAll() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        flushDirty();
    }

    /**
     * Synchronously persist every dirty captain profile (XP, rank, mission count, etc.).
     * Safe to call mid-session (e.g. an admin /ship save command).
     */
    public void flushDirty() {
        int saved = 0;
        for (CaptainProfile p : profiles.values()) {
            if (p.isDirty()) {
                dao.saveSync(p);
                saved++;
            }
        }
        if (saved > 0)
            plugin.getLogger().info("Flushed " + saved + " dirty captain profile(s) to database.");
    }

    private void startAutoSave() {
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> profiles.values().stream()
                        .filter(CaptainProfile::isDirty)
                        .forEach(dao::save),
                1200L, 1200L);
    }

    // -----------------------------------------------------------------------
    // Profile access
    // -----------------------------------------------------------------------

    /** Get or create a profile for a player. */
    public CaptainProfile getOrCreate(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), uuid -> {
            CaptainProfile existing = dao.load(uuid);
            if (existing != null) return existing;
            CaptainProfile fresh = new CaptainProfile(uuid, player.getName(),
                    CaptainRank.CADET, 0, 0, 0);
            dao.save(fresh);
            return fresh;
        });
    }

    public CaptainProfile get(UUID uuid) { return profiles.get(uuid); }

    /**
     * Bonus crew slots granted by rank, on top of a ship class's base slots.
     * Tier 1 (Cadet) = +0, Tier 2 (Ensign) = +1, ... Tier 10 (Admiral) = +9.
     */
    public int getBonusCrewSlots(UUID ownerUUID) {
        CaptainProfile profile = profiles.get(ownerUUID);
        if (profile == null) return 0;
        return Math.max(0, profile.getRank().getTier() - 1);
    }

    public List<CaptainProfile> getLeaderboard(int limit) {
        return profiles.values().stream()
                .sorted(Comparator.comparingLong(CaptainProfile::getExperience).reversed())
                .limit(limit)
                .toList();
    }

    // -----------------------------------------------------------------------
    // XP awarding
    // -----------------------------------------------------------------------

    public void awardWarpXp(Player player, double distanceBlocks) {
        long xp = Math.max(5, (long)(distanceBlocks / 50));
        award(player, xp, false);
        getOrCreate(player).addWarpDistance((long) distanceBlocks);
    }

    public void awardMissionXp(Player player, long xp) {
        award(player, xp, true);
    }

    public void awardExploreXp(Player player, long xp) {
        award(player, xp, false);
    }

    public void awardStoryXp(Player player, long xp) {
        award(player, xp, false);
    }

    private void award(Player player, long xp, boolean announce) {
        CaptainProfile profile = getOrCreate(player);
        profile.setPlayerName(player.getName());
        boolean rankedUp = profile.addExperience(xp);

        if (announce || rankedUp) {
            player.sendActionBar("§b+" + xp + " XP §8— §f" + profile.getRank().getDisplayName()
                    + " §8[" + profile.getExperience() + " XP]");
        }

        if (rankedUp) {
            notifyRankUp(player, profile);
        }
    }

    private void notifyRankUp(Player player, CaptainProfile profile) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.sendTitle(
                "§b§lRANK UP",
                "§fYou are now " + profile.getRank().getChatPrefix(),
                10, 60, 20
        );
        player.sendMessage("§b§l[Horizon] §rCongratulations, §f" + player.getName()
                + "§r! You have been promoted to §b" + profile.getRank().getDisplayName() + "§r.");

        // Announce to server
        Bukkit.broadcastMessage("§b[Horizon] §f" + player.getName()
                + " §7has been promoted to "
                + profile.getRank().getChatPrefix() + "§7!");
    }
}