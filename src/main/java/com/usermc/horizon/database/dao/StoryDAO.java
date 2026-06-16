package com.usermc.horizon.database.dao;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.story.StoryArc;
import com.usermc.horizon.story.StoryChapter;
import com.usermc.horizon.story.StoryObjectiveType;
import com.usermc.horizon.story.StoryStatus;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class StoryDAO {

    private final Horizon plugin;

    public StoryDAO(Horizon plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Arc save (async + sync)
    // -----------------------------------------------------------------------

    public void saveArc(StoryArc arc) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveArcBlocking(arc));
    }

    /** Synchronous — used at shutdown so the write completes before the pool closes. */
    public void saveArcSync(StoryArc arc) {
        saveArcBlocking(arc);
    }

    private void saveArcBlocking(StoryArc arc) {
        String sql = """
            INSERT INTO horizon_story_arcs
                (arc_id, player_uuid, premise, current_chapter, total_chapters, status)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                premise         = VALUES(premise),
                current_chapter = VALUES(current_chapter),
                status          = VALUES(status)
        """;
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arc.getArcId().toString());
            ps.setString(2, arc.getPlayerUUID().toString());
            ps.setString(3, arc.getPremise());
            ps.setInt   (4, arc.getCurrentChapterNumber());
            ps.setInt   (5, arc.getTotalChapters());
            ps.setString(6, arc.getStatus().name());
            ps.executeUpdate();
            arc.clearDirty();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save story arc", e);
        }
    }

    // -----------------------------------------------------------------------
    // Chapter save (async + sync)
    // -----------------------------------------------------------------------

    public void saveChapter(StoryChapter chapter) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveChapterBlocking(chapter));
    }

    /** Synchronous — used at shutdown so the write completes before the pool closes. */
    public void saveChapterSync(StoryChapter chapter) {
        saveChapterBlocking(chapter);
    }

    private void saveChapterBlocking(StoryChapter chapter) {
        String sql = """
            INSERT INTO horizon_story_chapters
                (chapter_id, arc_id, chapter_number, title, narrative,
                 objective_flavor, completion_text, objective_type,
                 progress, required, reward_credits, reward_xp, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                title             = VALUES(title),
                narrative         = VALUES(narrative),
                objective_flavor  = VALUES(objective_flavor),
                completion_text   = VALUES(completion_text),
                progress          = VALUES(progress),
                status            = VALUES(status)
        """;
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,  chapter.getChapterId().toString());
            ps.setString(2,  chapter.getArcId().toString());
            ps.setInt   (3,  chapter.getChapterNumber());
            ps.setString(4,  chapter.getTitle());
            ps.setString(5,  chapter.getNarrative());
            ps.setString(6,  chapter.getObjectiveFlavor());
            ps.setString(7,  chapter.getCompletionText());
            ps.setString(8,  chapter.getObjectiveType().name());
            ps.setInt   (9,  chapter.getProgress());
            ps.setInt   (10, chapter.getRequired());
            ps.setInt   (11, chapter.getRewardCredits());
            ps.setInt   (12, chapter.getRewardXp());
            ps.setString(13, chapter.getStatus().name());
            ps.executeUpdate();
            chapter.clearDirty();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save story chapter", e);
        }
    }

    // -----------------------------------------------------------------------
    // Loading — synchronous, called on startup only
    // -----------------------------------------------------------------------

    /** Loads every ACTIVE arc (with its chapters) for all players. Called at startup. */
    public List<StoryArc> loadActiveArcs() {
        List<StoryArc> arcs = new ArrayList<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             Statement  stmt = c.createStatement();
             ResultSet  rs   = stmt.executeQuery(
                     "SELECT * FROM horizon_story_arcs WHERE status = 'ACTIVE'")) {
            while (rs.next()) {
                arcs.add(arcFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load story arcs", e);
        }

        for (StoryArc arc : arcs) {
            arc.getChapters().addAll(loadChapters(arc.getArcId()));
        }
        return arcs;
    }

    /** Returns true if this player has EVER had a story arc (used to avoid re-triggering the intro). */
    public boolean hasAnyArc(UUID playerUUID) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM horizon_story_arcs WHERE player_uuid = ? LIMIT 1")) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check story history for " + playerUUID, e);
            return true; // fail safe: assume yes, don't spam-generate on DB errors
        }
    }

    private List<StoryChapter> loadChapters(UUID arcId) {
        List<StoryChapter> chapters = new ArrayList<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM horizon_story_chapters WHERE arc_id = ? ORDER BY chapter_number ASC")) {
            ps.setString(1, arcId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) chapters.add(chapterFromResultSet(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load story chapters for arc " + arcId, e);
        }
        return chapters;
    }

    private StoryArc arcFromResultSet(ResultSet rs) throws SQLException {
        UUID arcId    = UUID.fromString(rs.getString("arc_id"));
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
        String premise = rs.getString("premise");
        int current   = rs.getInt("current_chapter");
        int total     = rs.getInt("total_chapters");
        StoryStatus status = StoryStatus.valueOf(rs.getString("status"));
        StoryArc arc = new StoryArc(arcId, playerId, premise, current, total, status);
        arc.clearDirty();
        return arc;
    }

    private StoryChapter chapterFromResultSet(ResultSet rs) throws SQLException {
        UUID chapterId = UUID.fromString(rs.getString("chapter_id"));
        UUID arcId     = UUID.fromString(rs.getString("arc_id"));
        int number     = rs.getInt("chapter_number");
        String title   = rs.getString("title");
        String narrative = rs.getString("narrative");
        String flavor  = rs.getString("objective_flavor");
        String completion = rs.getString("completion_text");
        StoryObjectiveType type = StoryObjectiveType.valueOf(rs.getString("objective_type"));
        int progress = rs.getInt("progress");
        int required = rs.getInt("required");
        int rewardCredits = rs.getInt("reward_credits");
        int rewardXp = rs.getInt("reward_xp");
        StoryStatus status = StoryStatus.valueOf(rs.getString("status"));

        StoryChapter chapter = new StoryChapter(chapterId, arcId, number, title, narrative,
                flavor, completion, type, progress, required, rewardCredits, rewardXp, status);
        chapter.clearDirty();
        return chapter;
    }
}