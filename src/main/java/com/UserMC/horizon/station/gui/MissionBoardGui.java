package com.usermc.horizon.station.gui;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.mission.Mission;
import com.usermc.horizon.ship.Ship;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * Mission Board GUI — 4 rows.
 *
 * Row 0–2 (slots 0–26): Available missions on the board
 * Row 3   (slots 27–35): Player's active missions + refresh button
 *
 * Clicking an available mission accepts it (up to the limit).
 * Accepted missions are shown in row 3 with their target and status.
 */
public class MissionBoardGui extends com.usermc.horizon.station.gui.HorizonGui {

    private List<Mission> boardMissions;
    private List<Mission> activeMissions;

    public MissionBoardGui(Ship ship, Player player) {
        super(ship, player, 4, "§b📋 Mission Board");
        refreshy();
    }

    private void refreshy() {
        Horizon plugin = Horizon.getInstance();
        this.boardMissions  = plugin.getMissionManager().getBoardMissions();
        this.activeMissions = plugin.getMissionManager().getActiveMissions(player.getUniqueId());
    }

    @Override
    public void build() {
        inventory.clear();
        Horizon plugin = Horizon.getInstance();
        int maxActive  = plugin.getMissionManager().getMaxActivePerPlayer();

        // --- Rows 0-2: Available board missions ---
        for (int i = 0; i < Math.min(boardMissions.size(), 27); i++) {
            Mission m = boardMissions.get(i);
            boolean alreadyActive = activeMissions.stream()
                    .anyMatch(a -> a.getMissionId().equals(m.getMissionId()));
            boolean boardFull = activeMissions.size() >= maxActive;

            Material mat = alreadyActive ? Material.LIME_STAINED_GLASS_PANE
                    : boardFull     ? Material.RED_STAINED_GLASS_PANE
                      :                 Material.BOOK;

            String statusLine = alreadyActive ? "§aAlready accepted."
                    : boardFull     ? "§cActive mission limit reached (" + maxActive + ")."
                      :                 "§eClick to accept.";

            long msLeft = m.getExpiresAt() - System.currentTimeMillis();
            String timeLeft = msLeft > 0
                    ? String.format("§8Expires in %dm", msLeft / 60000)
                    : "§cExpiring soon";

            inventory.setItem(i, makeItem(mat,
                    m.getType().getColour() + m.getType().getDisplayName() + " §8— " + m.difficultyStars(),
                    "§f" + m.getTitle(),
                    "§7" + m.getDescription(),
                    "",
                    "§7Target:   §b" + m.getTargetBeaconName(),
                    "§7Reward:   §6" + m.getRewardEc() + " EC  §b" + m.getRewardXp() + " XP",
                    "§7Difficulty: §f" + m.difficultyStars(),
                    timeLeft,
                    "",
                    statusLine
            ));
        }

        if (boardMissions.isEmpty()) {
            inventory.setItem(13, makeItem(Material.BARRIER,
                    "§8No Missions Available",
                    "§7The board is empty. Check back later.",
                    "§8Missions refresh every 30 minutes."
            ));
        }

        // --- Row 3: Active missions + info ---
        inventory.setItem(27, makeItem(Material.PAPER,
                "§b§lYour Active Missions §8(" + activeMissions.size() + "/" + maxActive + ")"));

        for (int i = 0; i < activeMissions.size() && i < 5; i++) {
            Mission m = activeMissions.get(i);
            inventory.setItem(28 + i, makeItem(Material.MAP,
                    m.getType().getColour() + m.getTitle(),
                    "§7Target: §b" + m.getTargetBeaconName(),
                    "§7Warp to §b" + m.getTargetBeaconName() + " §7to complete.",
                    "§7Reward: §6" + m.getRewardEc() + " EC  §b+" + m.getRewardXp() + " XP"
            ));
        }

        // Refresh button
        inventory.setItem(35, makeItem(Material.CLOCK,
                "§e↻ Refresh Board",
                "§7Re-checks board for new missions."
        ));

        // --- Captain's Log (AI story arc) ---
        buildStorySlot();

        fillAll();
    }

    private void buildStorySlot() {
        Horizon plugin = Horizon.getInstance();
        var story = plugin.getStoryManager();

        if (!story.isEnabled()) return; // silently absent without a Gemini key

        var arc = story.getArc(player.getUniqueId());

        if (story.isGenerating(player.getUniqueId())) {
            inventory.setItem(33, makeItem(Material.CLOCK,
                    "§b📡 Transmission Incoming...",
                    "§7Deep-space telemetry is being processed.",
                    "§7Check back in a moment."
            ));
            return;
        }

        if (story.canStartNewArc(player.getUniqueId())) {
            boolean isNewSeason = arc != null; // a previous arc completed
            inventory.setItem(33, makeItem(Material.WRITTEN_BOOK,
                    isNewSeason ? "§b📡 Begin New Exploration Log" : "§b📡 Begin Exploration Log",
                    "§7A faint signal lingers at the edge of sensor range.",
                    "§7Click to start receiving transmissions."
            ));
            return;
        }

        if (arc != null) {
            var chapter = arc.getCurrentChapter();
            if (chapter != null) {
                inventory.setItem(33, makeItem(Material.WRITTEN_BOOK,
                        "§b📖 Captain's Log — Ch. " + chapter.getChapterNumber() + "/" + arc.getTotalChapters(),
                        "§f" + chapter.getTitle(),
                        "§7" + chapter.getObjectiveFlavor(),
                        "",
                        "§7Progress: §f" + chapter.getProgress() + "§7/§f" + chapter.getRequired(),
                        "§7Reward: §6" + chapter.getRewardCredits() + " EC  §b" + chapter.getRewardXp() + " XP",
                        "",
                        "§eClick to read."
                ));
            }
        }
    }

    @Override
    public boolean handleClick(int slot, ClickType click) {
        // Captain's Log slot
        if (slot == 33) {
            Horizon plugin = Horizon.getInstance();
            var story = plugin.getStoryManager();
            if (!story.isEnabled()) return false;

            if (story.canStartNewArc(player.getUniqueId())) {
                story.startNewArc(player);
                player.closeInventory();
                return false;
            }

            var arc = story.getArc(player.getUniqueId());
            if (arc != null && arc.getCurrentChapter() != null) {
                story.openLog(player);
                return false;
            }
            return false;
        }

        // Refresh button
        if (slot == 35) { refreshy(); return true; }

        // Active mission slots — no action (display only)
        if (slot >= 27) return false;

        // Board mission slots
        if (slot >= boardMissions.size()) return false;

        Mission m = boardMissions.get(slot);
        if (!m.isAvailable()) { player.sendActionBar("§cThis mission is no longer available."); return true; }

        boolean alreadyActive = activeMissions.stream()
                .anyMatch(a -> a.getMissionId().equals(m.getMissionId()));
        if (alreadyActive) { player.sendActionBar("§eYou already have this mission."); return false; }

        if (activeMissions.size() >= Horizon.getInstance().getMissionManager().getMaxActivePerPlayer()) {
            player.sendActionBar("§cActive mission limit reached. Complete one first.");
            return false;
        }

        Mission accepted = Horizon.getInstance().getMissionManager().accept(player, m.getMissionId());
        if (accepted != null) {
            player.sendMessage("§a[Mission] Accepted: §f" + accepted.getTitle());
            player.sendMessage("§7Warp to §b" + accepted.getTargetBeaconName()
                    + " §7to complete. Reward: §6" + accepted.getRewardEc()
                    + " EC §b+" + accepted.getRewardXp() + " XP");
            player.closeInventory();
        } else {
            player.sendActionBar("§cCould not accept mission.");
        }
        return false;
    }
}