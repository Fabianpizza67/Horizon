package com.usermc.horizon.economy;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.EconomyDAO;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages Energy Credit balances for all players.
 *
 * Balances are cached in memory and flushed async to MariaDB.
 * Physical Credit Chips are converted to/from balance at the Engineering Console.
 */
public class EconomyManager {

    private final Horizon     plugin;
    private final EconomyDAO  dao;

    /** In-memory balance cache to avoid DB reads on every transaction. */
    private final Map<UUID, Long> cache = new HashMap<>();

    public EconomyManager(Horizon plugin) {
        this.plugin = plugin;
        this.dao    = new EconomyDAO(plugin);
    }

    // -----------------------------------------------------------------------
    // Balance
    // -----------------------------------------------------------------------

    public long getBalance(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), dao::getBalance);
    }

    public long getBalance(UUID uuid) {
        return cache.computeIfAbsent(uuid, dao::getBalance);
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    /**
     * Called from onDisable(). Synchronously flushes the in-memory balance
     * cache to the database, guaranteeing the last-known balance survives
     * even if the most recent async write hadn't completed yet.
     *
     * UPDATE-only and idempotent — every cached balance was already written
     * via async setBalance() at the time it changed, so this just re-confirms
     * the final value before the connection pool closes.
     */
    public void saveAll() { flushDirty(); }

    /** Synchronously flush every cached balance. Safe to call mid-session. */
    public void flushDirty() {
        for (var entry : cache.entrySet()) {
            dao.setBalanceSync(entry.getKey(), entry.getValue());
        }
    }

    /** Add (or subtract if negative) from a player's balance. Returns new balance. */
    public long addBalance(Player player, long amount) {
        long current = getBalance(player);
        long newBal  = Math.max(0, current + amount);
        cache.put(player.getUniqueId(), newBal);
        dao.setBalance(player.getUniqueId(), player.getName(), newBal);
        if (amount > 0) {
            // Track total earned separately for leaderboards
            dao.addToBalance(player.getUniqueId(), player.getName(), amount);
        }
        return newBal;
    }

    /** Returns false if insufficient funds. Does NOT deduct in that case. */
    public boolean deduct(Player player, long amount) {
        long current = getBalance(player);
        if (current < amount) return false;
        addBalance(player, -amount);
        return true;
    }

    /** Transfer from → to. Returns false if sender has insufficient funds. */
    public boolean transfer(Player from, Player to, long amount) {
        if (!deduct(from, amount)) return false;
        addBalance(to, amount);
        return true;
    }

    // -----------------------------------------------------------------------
    // Chip operations (called from EngineeringGui)
    // -----------------------------------------------------------------------

    /** Deposit all Credit Chips from inventory → digital balance. Returns EC deposited. */
    public long depositChips(Player player) {
        long value = CreditChip.depositAll(player);
        if (value > 0) addBalance(player, value);
        return value;
    }

    /**
     * Withdraw {@code amount} EC → physical chips.
     * Returns the amount actually dispensed (rounded down to nearest 10).
     */
    public long withdraw(Player player, long amount) {
        long roundedDown = (amount / CreditChip.NUGGET_VALUE) * CreditChip.NUGGET_VALUE;
        if (roundedDown <= 0) return 0;
        if (!deduct(player, roundedDown)) return 0;
        CreditChip.dispense(player, roundedDown);
        return roundedDown;
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    public void setBalance(Player player, long amount) {
        cache.put(player.getUniqueId(), Math.max(0, amount));
        dao.setBalance(player.getUniqueId(), player.getName(), Math.max(0, amount));
    }

    // -----------------------------------------------------------------------
    // Display helpers
    // -----------------------------------------------------------------------

    public String format(long amount) {
        if (amount >= 1_000_000) return String.format("%.1fM EC", amount / 1_000_000.0);
        if (amount >= 1_000)     return String.format("%.1fK EC", amount / 1_000.0);
        return amount + " EC";
    }
}