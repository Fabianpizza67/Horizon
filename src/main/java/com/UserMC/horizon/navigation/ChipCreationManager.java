package com.usermc.horizon.navigation;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.ship.Ship;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manages all in-progress navigation chip creations.
 *
 * Flow:
 *   1. Player at a Navigation Console chooses a chip type and confirms.
 *   2. Materials are deducted immediately (no refund on cancel).
 *   3. A 2-minute (default) countdown begins, ship position captured NOW.
 *   4. Every second, check accumulated drift:
 *        - drift < CANCEL_THRESHOLD_BLOCKS  → extend timer slightly (recalculating)
 *        - drift >= CANCEL_THRESHOLD_BLOCKS → cancel outright
 *   5. Any ship rotation during creation cancels outright (handled via
 *      onShipRotated, called from the movement engine).
 *   6. On completion, the chip item is created and dropped/given to the player.
 *
 * Wiring required in ShipMovementEngine (not editable this session):
 *   - After a successful translation, call:
 *       plugin.getChipCreationManager().onShipMoved(ship, dx, dy, dz);
 *   - After a successful rotation, call:
 *       plugin.getChipCreationManager().onShipRotated(ship);
 */
public class ChipCreationManager {

    private static final long   DEFAULT_DURATION_TICKS   = 20L * 120; // 2 minutes
    private static final double CANCEL_THRESHOLD_BLOCKS  = 50.0;      // total drift before hard cancel
    private static final long   EXTEND_TICKS_PER_MOVE     = 20L * 5;   // +5s per small movement tick

    private final Horizon plugin;

    /** One active session per player — starting a new one while one is active is disallowed. */
    private final Map<UUID, ChipCreationSession> sessions = new HashMap<>();

    /** Reverse index: shipId → set of player UUIDs currently creating a chip aboard it. */
    private final Map<UUID, Set<UUID>> sessionsByShip = new HashMap<>();

    private BukkitTask tickTask;

    public ChipCreationManager(Horizon plugin) {
        this.plugin = plugin;
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 20L); // once per second
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        sessions.clear();
        sessionsByShip.clear();
    }

    // -----------------------------------------------------------------------
    // Material costs — consumed immediately on start, not refunded on cancel
    // -----------------------------------------------------------------------

    private Map<Material, Integer> costFor(ChipType type) {
        return switch (type) {
            case SINGLE   -> Map.of(Material.PAPER, 2, Material.REDSTONE, 1);
            case MULTI    -> Map.of(Material.IRON_INGOT, 4, Material.GOLD_INGOT, 2, Material.LAPIS_LAZULI, 1);
            case REUSABLE -> Map.of(Material.DIAMOND, 4, Material.GOLD_BLOCK, 2, Material.AMETHYST_SHARD, 1);
        };
    }

    private boolean hasMaterials(Player player, Map<Material, Integer> cost) {
        for (var entry : cost.entrySet()) {
            if (!player.getInventory().containsAtLeast(new ItemStack(entry.getKey()), entry.getValue()))
                return false;
        }
        return true;
    }

    private void consumeMaterials(Player player, Map<Material, Integer> cost) {
        for (var entry : cost.entrySet()) {
            player.getInventory().removeItem(new ItemStack(entry.getKey(), entry.getValue()));
        }
    }

    public String describeCost(ChipType type) {
        StringBuilder sb = new StringBuilder();
        costFor(type).forEach((mat, amt) -> sb.append(amt).append("x ").append(formatMaterial(mat)).append(", "));
        if (sb.length() > 2) sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    private String formatMaterial(Material mat) {
        String[] parts = mat.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase()).append(" ");
        return sb.toString().trim();
    }

    // -----------------------------------------------------------------------
    // Start / Cancel
    // -----------------------------------------------------------------------

    public boolean isCreating(UUID playerUUID) { return sessions.containsKey(playerUUID); }

    public ChipCreationSession getSession(UUID playerUUID) { return sessions.get(playerUUID); }

    /**
     * Attempt to start a new chip creation.
     * Returns a result code: "ok", "busy", "insufficient_materials".
     */
    public String startCreation(Player player, Ship ship, ChipType type, String destinationName) {
        if (isCreating(player.getUniqueId())) return "busy";

        Map<Material, Integer> cost = costFor(type);
        if (!hasMaterials(player, cost)) return "insufficient_materials";

        consumeMaterials(player, cost);

        ChipCreationSession session = new ChipCreationSession(
                player.getUniqueId(), ship, type, destinationName, DEFAULT_DURATION_TICKS);
        sessions.put(player.getUniqueId(), session);
        sessionsByShip.computeIfAbsent(ship.getShipId(), k -> new HashSet<>()).add(player.getUniqueId());

        player.sendMessage("§b[Navigation] §7Calculating jump data for §f" + destinationName
                + "§7... (§f2:00§7)");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.2f);
        return "ok";
    }

    private void cancelSession(UUID playerUUID, String reason) {
        ChipCreationSession session = sessions.remove(playerUUID);
        if (session == null) return;
        Set<UUID> shipSessions = sessionsByShip.get(session.getShipId());
        if (shipSessions != null) shipSessions.remove(playerUUID);

        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            player.sendMessage("§c[Navigation] Chip calculation aborted — " + reason + ".");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 0.7f);
        }
    }

    // -----------------------------------------------------------------------
    // Movement hooks — called from ShipMovementEngine
    // -----------------------------------------------------------------------

    /** Call after every successful ship translation. */
    public void onShipMoved(Ship ship, int dx, int dy, int dz) {
        Set<UUID> playerIds = sessionsByShip.get(ship.getShipId());
        if (playerIds == null || playerIds.isEmpty()) return;

        double moveDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        for (UUID uuid : new HashSet<>(playerIds)) {
            ChipCreationSession session = sessions.get(uuid);
            if (session == null) continue;

            session.addDrift(moveDist);
            if (session.getCumulativeDrift() >= CANCEL_THRESHOLD_BLOCKS) {
                cancelSession(uuid, "ship moved too far from the original coordinates");
            } else {
                session.extendBy(EXTEND_TICKS_PER_MOVE);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendActionBar("§e[Navigation] Recalculating... §7("
                        + formatTime(session.getRemainingTicks()) + ")");
            }
        }
    }

    /** Call after every successful ship rotation — always cancels any in-progress chip on that ship. */
    public void onShipRotated(Ship ship) {
        Set<UUID> playerIds = sessionsByShip.get(ship.getShipId());
        if (playerIds == null || playerIds.isEmpty()) return;
        for (UUID uuid : new HashSet<>(playerIds)) {
            cancelSession(uuid, "ship changed heading");
        }
    }

    // -----------------------------------------------------------------------
    // Tick — runs once per second
    // -----------------------------------------------------------------------

    private void tickAll() {
        if (sessions.isEmpty()) return;

        // This task runs once per second (every 20 game ticks), so decrement
        // each session's remaining tick-count by 20 to keep it in real tick
        // units for accurate formatTime() display.
        for (ChipCreationSession session : sessions.values()) {
            session.tickBy(20L);
        }

        List<UUID> completed = new ArrayList<>();
        for (var entry : new HashMap<>(sessions).entrySet()) {
            UUID uuid = entry.getKey();
            ChipCreationSession session = entry.getValue();

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                sessions.remove(uuid);
                continue;
            }

            if (session.isComplete()) {
                completeSession(player, session);
                completed.add(uuid);
            } else {
                player.sendActionBar("§b[Navigation] Calculating jump data... §7("
                        + formatTime(session.getRemainingTicks()) + ")");
            }
        }
        for (UUID uuid : completed) {
            sessions.remove(uuid);
        }
    }

    private void completeSession(Player player, ChipCreationSession session) {
        Set<UUID> shipSessions = sessionsByShip.get(session.getShipId());
        if (shipSessions != null) shipSessions.remove(player.getUniqueId());

        ItemStack chip = ChipItem.create(session.getType(), session.getDestinationName(),
                session.getCapturedDestination());

        var leftover = player.getInventory().addItem(chip);
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }

        player.sendMessage("§a[Navigation] Chip complete: §f" + session.getDestinationName() + "§a.");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
    }

    private String formatTime(long ticks) {
        long totalSeconds = Math.max(0, ticks / 20);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}