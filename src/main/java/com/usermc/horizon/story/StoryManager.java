package com.usermc.horizon.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.StoryDAO;
import com.usermc.horizon.rank.CaptainProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.*;

/**
 * Orchestrates AI-generated "Captain's Log" story arcs.
 *
 * Each player can have one ACTIVE arc at a time — a self-contained 5-chapter
 * mini-narrative. Gemini writes all narrative text (title, log entries,
 * objective flavor, completion text) fresh for every arc. The mechanical
 * objective behind each chapter is always one of {@link StoryObjectiveType},
 * chosen by this manager and hooked into normal gameplay systems — so every
 * chapter is guaranteed-completable even if the AI text varies wildly.
 *
 * If no Gemini API key is configured, the entire feature is silently absent
 * (no UI prompts, no errors) — everything else in Horizon works normally.
 *
 * If a key IS configured but a generation call fails (network issue, bad
 * key, rate limit), a pre-written fallback chapter is used instead so the
 * player's progress is never blocked.
 */
public class StoryManager {

    /** Chapters per arc. Matches the DB column default. */
    public static final int TOTAL_CHAPTERS = 5;

    private static final String SYSTEM_INSTRUCTION = """
        You are the narrative engine for "Horizon", an original Star Trek-inspired \
        space exploration experience on a Minecraft server. You write short, \
        self-contained 5-chapter story arcs for individual player captains.

        TONE: Warm, hopeful, curious science-fiction mystery and discovery — gentle \
        wonder, found knowledge, first contact, ancient mysteries with benevolent or \
        bittersweet (never malevolent) resolutions. Think: uncovering a forgotten \
        civilization's quiet legacy, not uncovering a threat.

        STRICTLY AVOID: horror, body horror, gore, existential dread, violence, death \
        of named characters, disturbing or unsettling imagery, anything inappropriate \
        for a young audience. Do not use real Star Trek IP — invent entirely original \
        ship names, species, factions, people and locations.

        Respond with ONLY a single valid JSON object matching the schema given in the \
        prompt. No markdown formatting, no code fences, no commentary outside the JSON.
        """;

    private final Horizon     plugin;
    private final StoryDAO    dao;
    private final GeminiClient gemini;
    private final Random      rng = new Random();

    /** ACTIVE (and recently-completed) arcs, keyed by player UUID. */
    private final Map<UUID, StoryArc> arcs = new HashMap<>();

    /** Players currently awaiting a Gemini response — prevents duplicate calls. */
    private final Set<UUID> generating = new HashSet<>();

    public StoryManager(Horizon plugin) {
        this.plugin = plugin;
        this.dao    = new StoryDAO(plugin);
        this.gemini = new GeminiClient(
                plugin.getHorizonConfig().getGeminiApiKey(),
                plugin.getHorizonConfig().getGeminiModel());
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        if (!isEnabled()) {
            plugin.getLogger().info("Story Log disabled (no Gemini API key configured).");
            return;
        }
        for (StoryArc arc : dao.loadActiveArcs()) {
            arcs.put(arc.getPlayerUUID(), arc);
        }
        plugin.getLogger().info("Loaded " + arcs.size() + " active story arc(s).");
    }

    /** Called from onDisable(). Final synchronous flush. */
    public void saveAll() { flushDirty(); }

    /** Synchronously persist every dirty arc/chapter. Safe mid-session. */
    public void flushDirty() {
        for (StoryArc arc : arcs.values()) {
            if (arc.isDirty()) dao.saveArcSync(arc);
            for (StoryChapter c : arc.getChapters()) {
                if (c.isDirty()) dao.saveChapterSync(c);
            }
        }
    }

    public boolean isEnabled() {
        return gemini.isConfigured();
    }

    // -----------------------------------------------------------------------
    // Public state for GUI
    // -----------------------------------------------------------------------

    /** The player's current arc, or null if they've never started one / it completed. */
    public StoryArc getArc(UUID playerUUID) {
        return arcs.get(playerUUID);
    }

    public boolean isGenerating(UUID playerUUID) {
        return generating.contains(playerUUID);
    }

    /** True if the player can click "Begin Exploration Log" right now. */
    public boolean canStartNewArc(UUID playerUUID) {
        if (!isEnabled() || generating.contains(playerUUID)) return false;
        StoryArc arc = arcs.get(playerUUID);
        return arc == null || arc.getStatus() == StoryStatus.COMPLETED;
    }

    // -----------------------------------------------------------------------
    // Starting a new arc
    // -----------------------------------------------------------------------

    public void startNewArc(Player player) {
        if (!canStartNewArc(player.getUniqueId())) return;

        UUID arcId = UUID.randomUUID();
        StoryArc arc = new StoryArc(arcId, player.getUniqueId(), "", 1, TOTAL_CHAPTERS, StoryStatus.ACTIVE);
        arcs.put(player.getUniqueId(), arc);
        dao.saveArc(arc);

        player.sendMessage("§b§l[Captain's Log] §r§7Transmitting deep-space probe telemetry...");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.0f);

        generateChapter(arc, player, true);
    }

    // -----------------------------------------------------------------------
    // Objective progress — called by gameplay hooks
    // -----------------------------------------------------------------------

    public void progressObjective(Player player, StoryObjectiveType type) {
        StoryArc arc = arcs.get(player.getUniqueId());
        if (arc == null || arc.getStatus() != StoryStatus.ACTIVE) return;

        StoryChapter chapter = arc.getCurrentChapter();
        if (chapter == null || chapter.getStatus() != StoryStatus.ACTIVE) return;
        if (chapter.getObjectiveType() != type) return;

        chapter.incrementProgress();
        dao.saveChapter(chapter);

        if (chapter.isComplete()) {
            completeChapter(player, arc, chapter);
        } else {
            player.sendActionBar("§b[Captain's Log] §f" + chapter.getProgress() + "/" + chapter.getRequired()
                    + " §7— " + chapter.getTitle());
        }
    }

    private void completeChapter(Player player, StoryArc arc, StoryChapter chapter) {
        chapter.setStatus(StoryStatus.COMPLETED);
        dao.saveChapter(chapter);

        plugin.getEconomyManager().addBalance(player, chapter.getRewardCredits());
        plugin.getRankManager().awardStoryXp(player, chapter.getRewardXp());

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        player.sendTitle("§b§l📡 TRANSMISSION", "§f" + chapter.getTitle() + " — Complete", 10, 70, 20);
        player.sendMessage("§b§l[Captain's Log] §r" + chapter.getCompletionText());
        player.sendMessage("§7Reward: §6+" + chapter.getRewardCredits() + " EC  §b+"
                + chapter.getRewardXp() + " XP");

        if (chapter.getChapterNumber() >= arc.getTotalChapters()) {
            arc.setStatus(StoryStatus.COMPLETED);
            dao.saveArc(arc);
            player.sendMessage("§b§l[Captain's Log] §r§7This story has reached its conclusion. "
                    + "A new exploration log can begin from the Mission Terminal.");
            return;
        }

        arc.setCurrentChapterNumber(chapter.getChapterNumber() + 1);
        dao.saveArc(arc);

        player.sendMessage("§7[Captain's Log] §8Receiving next transmission...");
        generateChapter(arc, player, false);
    }

    // -----------------------------------------------------------------------
    // Gemini generation
    // -----------------------------------------------------------------------

    private void generateChapter(StoryArc arc, Player player, boolean isFirst) {
        UUID playerUUID = player.getUniqueId();
        generating.add(playerUUID);

        int chapterNumber = arc.getCurrentChapterNumber();

        StoryObjectiveType previousType = null;
        if (!isFirst) {
            previousType = arc.getChapters().stream()
                    .filter(c -> c.getChapterNumber() == chapterNumber - 1)
                    .map(StoryChapter::getObjectiveType)
                    .findFirst().orElse(null);
        }
        StoryObjectiveType objectiveType = StoryObjectiveType.randomExcluding(rng, previousType);

        int rewardCredits = 150 * chapterNumber;
        int rewardXp      = 75  * chapterNumber;
        if (chapterNumber == arc.getTotalChapters()) {
            rewardCredits *= 2;
            rewardXp      *= 2;
        }
        final int finalCredits = rewardCredits;
        final int finalXp      = rewardXp;

        String prompt = isFirst
                ? buildFirstPrompt(player, objectiveType)
                : buildNextPrompt(player, arc, chapterNumber, objectiveType);

        gemini.generateJson(SYSTEM_INSTRUCTION, prompt).whenComplete((json, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    generating.remove(playerUUID);

                    StoryChapter chapter;
                    if (err == null) {
                        try {
                            chapter = parseChapter(json, arc, chapterNumber, objectiveType,
                                    finalCredits, finalXp, isFirst);
                        } catch (Exception ex) {
                            plugin.getLogger().warning("Failed to parse Gemini story response: " + ex.getMessage());
                            chapter = fallbackChapter(arc, chapterNumber, objectiveType, finalCredits, finalXp, isFirst);
                        }
                    } else {
                        plugin.getLogger().warning("Gemini story generation failed: " + err.getMessage());
                        chapter = fallbackChapter(arc, chapterNumber, objectiveType, finalCredits, finalXp, isFirst);
                    }

                    arc.addChapter(chapter);
                    dao.saveChapter(chapter);
                    dao.saveArc(arc);

                    notifyNewChapter(player, chapter);
                })
        );
    }

    // -----------------------------------------------------------------------
    // Prompt construction
    // -----------------------------------------------------------------------

    private String buildFirstPrompt(Player player, StoryObjectiveType objectiveType) {
        CaptainProfile profile = plugin.getRankManager().getOrCreate(player);
        return """
            Captain: %s
            Rank: %s

            Begin a brand new 5-chapter story arc for this captain. This is CHAPTER 1 of %d.

            For this chapter, the player's objective is mechanically defined as: %s

            Respond with this exact JSON schema:
            {
              "premise": "2-3 sentence overall hook for the whole arc",
              "chapterTitle": "short evocative title for chapter 1",
              "narrative": "2-4 short paragraphs, a captain's log entry introducing the mystery and setting the scene",
              "objectiveFlavor": "1-2 sentences framing the objective above in-universe",
              "completionText": "1-2 short paragraphs, a log entry shown when the player completes this objective"
            }
            """.formatted(
                player.getName(),
                profile.getRank().getDisplayName(),
                TOTAL_CHAPTERS,
                objectiveType.getDescription()
        );
    }

    private String buildNextPrompt(Player player, StoryArc arc, int chapterNumber, StoryObjectiveType objectiveType) {
        CaptainProfile profile = plugin.getRankManager().getOrCreate(player);
        StoryChapter prev = arc.getChapters().stream()
                .filter(c -> c.getChapterNumber() == chapterNumber - 1)
                .findFirst().orElse(null);

        String prevTitle = prev != null ? prev.getTitle() : "Unknown";
        String prevText  = prev != null ? prev.getCompletionText() : "";

        String finalNote = chapterNumber == arc.getTotalChapters()
                ? " This is the FINAL chapter — its completionText should resolve the arc's premise with a satisfying, hopeful conclusion."
                : "";

        return """
            Captain: %s
            Rank: %s

            ARC PREMISE: %s

            Previous chapter (%d of %d) — "%s":
            %s

            This is CHAPTER %d of %d.%s

            For this chapter, the player's objective is mechanically defined as: %s

            Respond with this exact JSON schema:
            {
              "chapterTitle": "...",
              "narrative": "2-4 short paragraphs continuing the story",
              "objectiveFlavor": "1-2 sentences framing the objective above in-universe",
              "completionText": "1-2 short paragraphs shown when the player completes this objective"
            }
            """.formatted(
                player.getName(),
                profile.getRank().getDisplayName(),
                arc.getPremise(),
                chapterNumber - 1, arc.getTotalChapters(), prevTitle, prevText,
                chapterNumber, arc.getTotalChapters(), finalNote,
                objectiveType.getDescription()
        );
    }

    // -----------------------------------------------------------------------
    // Response parsing & fallback
    // -----------------------------------------------------------------------

    private StoryChapter parseChapter(String json, StoryArc arc, int chapterNumber,
                                      StoryObjectiveType objectiveType,
                                      int rewardCredits, int rewardXp, boolean isFirst) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        if (isFirst && obj.has("premise")) {
            arc.setPremise(obj.get("premise").getAsString());
        }

        String title     = getOrDefault(obj, "chapterTitle", "Uncharted");
        String narrative = getOrDefault(obj, "narrative", defaultNarrative(objectiveType));
        String flavor    = getOrDefault(obj, "objectiveFlavor", objectiveType.getDescription());
        String completion= getOrDefault(obj, "completionText", defaultCompletion());

        return new StoryChapter(UUID.randomUUID(), arc.getArcId(), chapterNumber,
                title, narrative, flavor, completion,
                objectiveType, 0, 1, rewardCredits, rewardXp, StoryStatus.ACTIVE);
    }

    private String getOrDefault(JsonObject obj, String key, String def) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : def;
    }

    private StoryChapter fallbackChapter(StoryArc arc, int chapterNumber, StoryObjectiveType objectiveType,
                                         int rewardCredits, int rewardXp, boolean isFirst) {
        if (isFirst && arc.getPremise().isBlank()) {
            arc.setPremise("A faint, repeating signal has been logged drifting through nearby space — "
                    + "its origin and meaning unknown.");
        }
        return new StoryChapter(UUID.randomUUID(), arc.getArcId(), chapterNumber,
                "Uncharted Signal",
                defaultNarrative(objectiveType),
                objectiveType.getDescription(),
                defaultCompletion(),
                objectiveType, 0, 1, rewardCredits, rewardXp, StoryStatus.ACTIVE);
    }

    private String defaultNarrative(StoryObjectiveType type) {
        return "The long-range sensors crackle with an unfamiliar signal pattern — faint, rhythmic, "
                + "and clearly not natural. It fades before a full analysis can complete.\n\n"
                + "Command suggests routine operations may help the array recalibrate and "
                + "lock onto the source again. Specifically: " + type.getDescription() + ".";
    }

    private String defaultCompletion() {
        return "The signal sharpens for a moment — just long enough to confirm it isn't noise. "
                + "Whatever is out there, it's listening too.";
    }

    // -----------------------------------------------------------------------
    // Notification
    // -----------------------------------------------------------------------

    private void notifyNewChapter(Player player, StoryChapter chapter) {
        if (player == null || !player.isOnline()) return;
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        player.sendTitle("§b§l📡 TRANSMISSION RECEIVED", "§f" + chapter.getTitle(), 10, 70, 20);
        player.sendMessage("§b§l[Captain's Log] §r§7New entry received. Check the Mission Terminal.");
    }

    // -----------------------------------------------------------------------
    // Captain's Log book GUI
    // -----------------------------------------------------------------------

    /** Opens a read-only book view of the player's current chapter. Does not give the item. */
    public void openLog(Player player) {
        StoryArc arc = arcs.get(player.getUniqueId());
        if (arc == null) return;
        StoryChapter chapter = arc.getCurrentChapter();
        if (chapter == null) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(chapter.getTitle());
        meta.setAuthor("Unknown Origin");

        StringBuilder fullText = new StringBuilder();
        fullText.append("§l").append(chapter.getTitle()).append("§r\n\n");
        fullText.append(chapter.getNarrative());
        fullText.append("\n\n§9§lObjective\n§r");
        fullText.append(chapter.getObjectiveFlavor());
        fullText.append("\n\n§7Progress: §f").append(chapter.getProgress())
                .append("§7/§f").append(chapter.getRequired());
        fullText.append("\n§7Reward: §6").append(chapter.getRewardCredits())
                .append(" EC  §b").append(chapter.getRewardXp()).append(" XP");

        for (String page : splitIntoPages(fullText.toString(), 256)) {
            meta.addPage(page);
        }

        book.setItemMeta(meta);
        player.openBook(book);
    }

    /** Splits text into book pages, breaking on whitespace so words aren't cut mid-way. */
    private List<String> splitIntoPages(String text, int maxCharsPerPage) {
        List<String> pages = new ArrayList<>();
        String[] paragraphs = text.split("\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() + 1 > maxCharsPerPage) {
                if (current.length() > 0) {
                    pages.add(current.toString());
                    current = new StringBuilder();
                }
                // Paragraph itself longer than a page — hard-wrap it
                while (paragraph.length() > maxCharsPerPage) {
                    int breakAt = paragraph.lastIndexOf(' ', maxCharsPerPage);
                    if (breakAt <= 0) breakAt = maxCharsPerPage;
                    pages.add(paragraph.substring(0, breakAt));
                    paragraph = paragraph.substring(breakAt).trim();
                }
            }
            if (current.length() > 0) current.append("\n");
            current.append(paragraph);
        }
        if (current.length() > 0) pages.add(current.toString());
        if (pages.isEmpty()) pages.add(" ");
        return pages;
    }
}