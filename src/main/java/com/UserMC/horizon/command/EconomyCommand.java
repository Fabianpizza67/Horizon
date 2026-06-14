package com.usermc.horizon.command;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.rank.CaptainProfile;
import com.usermc.horizon.rank.CaptainRank;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * /ec — Economy commands
 *   /ec balance             — check your EC balance
 *   /ec pay <player> <amt>  — transfer EC to another player
 *   /ec missions            — list your active missions
 *   /ec rank                — show your captain rank and XP
 *   /ec top                 — richest players leaderboard
 *
 * Admin (horizon.admin):
 *   /ec give <player> <amt>
 *   /ec take <player> <amt>
 *   /ec set  <player> <amt>
 */
public class EconomyCommand implements CommandExecutor, TabCompleter {

    private static final String PFX = "§6[Economy] §r";
    private final Horizon plugin;

    public EconomyCommand(Horizon plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command."); return true;
        }
        if (args.length == 0) { showBalance(player); return true; }

        return switch (args[0].toLowerCase()) {
            case "balance", "bal", "b" -> { showBalance(player); yield true; }
            case "pay"                  -> cmdPay(player, args);
            case "rank"                 -> cmdRank(player);
            case "missions", "m"        -> cmdMissions(player);
            case "top"                  -> cmdTop(player);
            case "give"                 -> cmdGive(player, args);
            case "take"                 -> cmdTake(player, args);
            case "set"                  -> cmdSet(player, args);
            default -> {
                player.sendMessage(PFX + "§cUnknown subcommand. Try §f/ec balance§c.");
                yield true;
            }
        };
    }

    // -----------------------------------------------------------------------
    // Balance
    // -----------------------------------------------------------------------

    private void showBalance(Player player) {
        long bal = plugin.getEconomyManager().getBalance(player);
        CaptainProfile profile = plugin.getRankManager().getOrCreate(player);
        player.sendMessage("§6§l[ Account Summary ]");
        player.sendMessage("§7Balance:  §6" + plugin.getEconomyManager().format(bal));
        player.sendMessage("§7Rank:     " + profile.getRank().getChatPrefix());
        player.sendMessage("§7XP:       §b" + profile.getExperience()
                + (profile.getRank().isMaxRank() ? " §8(max)" :
                " §8/ " + profile.getRank().next().getXpRequired()
                + " §7(+" + profile.xpToNextRank() + " to next)"));
        player.sendMessage("§7Missions: §f" + profile.getMissionsCompleted() + " §7completed");
        player.sendMessage("§8Deposit/withdraw Credit Chips at the Engineering Console.");
    }

    // -----------------------------------------------------------------------
    // Pay
    // -----------------------------------------------------------------------

    private boolean cmdPay(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage(PFX + "§cUsage: /ec pay <player> <amount>"); return true; }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(PFX + "§cPlayer §f" + args[1] + " §cnot online."); return true; }
        if (target.equals(player)) {
            player.sendMessage(PFX + "§cYou can't pay yourself."); return true; }
        long amount;
        try { amount = Long.parseLong(args[2]); }
        catch (NumberFormatException e) { player.sendMessage(PFX + "§cAmount must be a number."); return true; }
        if (amount <= 0) { player.sendMessage(PFX + "§cAmount must be positive."); return true; }

        if (!plugin.getEconomyManager().transfer(player, target, amount)) {
            player.sendMessage(PFX + "§cInsufficient funds. Balance: §f"
                    + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)));
            return true;
        }
        player.sendMessage(PFX + "§aPaid §6" + plugin.getEconomyManager().format(amount)
                + " §ato §f" + target.getName() + "§a.");
        target.sendMessage(PFX + "§aYou received §6" + plugin.getEconomyManager().format(amount)
                + " §afrom §f" + player.getName() + "§a.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Rank
    // -----------------------------------------------------------------------

    private boolean cmdRank(Player player) {
        CaptainProfile profile = plugin.getRankManager().getOrCreate(player);
        CaptainRank rank = profile.getRank();
        player.sendMessage("§b§l[ Captain Profile — " + player.getName() + " ]");
        player.sendMessage("§7Rank:        " + rank.getChatPrefix());
        player.sendMessage("§7XP:          §b" + profile.getExperience());
        if (!rank.isMaxRank()) {
            player.sendMessage("§7Next rank:   §f" + rank.next().getDisplayName()
                    + " §8(+" + profile.xpToNextRank() + " XP needed)");
        }
        player.sendMessage("§7Missions:    §f" + profile.getMissionsCompleted());
        player.sendMessage("§7Warp dist:   §f"
                + String.format("%,d", profile.getTotalWarpDistance()) + " §7blocks");
        return true;
    }

    // -----------------------------------------------------------------------
    // Active missions
    // -----------------------------------------------------------------------

    private boolean cmdMissions(Player player) {
        var active = plugin.getMissionManager().getActiveMissions(player.getUniqueId());
        if (active.isEmpty()) {
            player.sendMessage(PFX + "§7No active missions. Visit the §bMission Terminal §7to take one.");
            return true;
        }
        player.sendMessage("§b§lActive Missions §8(" + active.size() + "):");
        for (var m : active) {
            player.sendMessage("  " + m.getType().getColour() + m.getTitle());
            player.sendMessage("  §8→ Warp to §b" + m.getTargetBeaconName()
                    + "§8 — Reward: §6" + m.getRewardEc() + " EC §b+" + m.getRewardXp() + " XP");
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Leaderboard
    // -----------------------------------------------------------------------

    private boolean cmdTop(Player player) {
        var top = plugin.getRankManager().getLeaderboard(10);
        if (top.isEmpty()) { player.sendMessage(PFX + "§7No captains ranked yet."); return true; }
        player.sendMessage("§b§l[ Top Captains ]");
        int i = 1;
        for (var p : top) {
            String medal = i == 1 ? "§6★" : i == 2 ? "§7★" : i == 3 ? "§c★" : "§8" + i + ".";
            player.sendMessage(medal + " §f" + p.getPlayerName()
                    + "  " + p.getRank().getChatPrefix()
                    + "  §8" + p.getExperience() + " XP");
            i++;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    private boolean cmdGive(Player player, String[] args) {
        if (!player.hasPermission("horizon.admin")) { player.sendMessage(PFX + "§cNo permission."); return true; }
        if (args.length < 3) { player.sendMessage(PFX + "§cUsage: /ec give <player> <amount>"); return true; }
        Player t = plugin.getServer().getPlayer(args[1]);
        if (t == null) { player.sendMessage(PFX + "§cPlayer not found."); return true; }
        long amt = parseLong(args[2]);
        if (amt <= 0) { player.sendMessage(PFX + "§cInvalid amount."); return true; }
        plugin.getEconomyManager().addBalance(t, amt);
        player.sendMessage(PFX + "§aGave §6" + amt + " EC §ato §f" + t.getName());
        t.sendMessage(PFX + "§aAn admin credited your account with §6" + amt + " EC§a.");
        return true;
    }

    private boolean cmdTake(Player player, String[] args) {
        if (!player.hasPermission("horizon.admin")) { player.sendMessage(PFX + "§cNo permission."); return true; }
        if (args.length < 3) { player.sendMessage(PFX + "§cUsage: /ec take <player> <amount>"); return true; }
        Player t = plugin.getServer().getPlayer(args[1]);
        if (t == null) { player.sendMessage(PFX + "§cPlayer not found."); return true; }
        long amt = parseLong(args[2]);
        plugin.getEconomyManager().addBalance(t, -amt);
        player.sendMessage(PFX + "§aDeducted §6" + amt + " EC §afrom §f" + t.getName());
        return true;
    }

    private boolean cmdSet(Player player, String[] args) {
        if (!player.hasPermission("horizon.admin")) { player.sendMessage(PFX + "§cNo permission."); return true; }
        if (args.length < 3) { player.sendMessage(PFX + "§cUsage: /ec set <player> <amount>"); return true; }
        Player t = plugin.getServer().getPlayer(args[1]);
        if (t == null) { player.sendMessage(PFX + "§cPlayer not found."); return true; }
        long amt = parseLong(args[2]);
        plugin.getEconomyManager().setBalance(t, amt);
        player.sendMessage(PFX + "§aSet §f" + t.getName() + "§a's balance to §6" + amt + " EC§a.");
        return true;
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1)
            return List.of("balance","pay","rank","missions","top","give","take","set")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && List.of("pay","give","take","set").contains(args[0].toLowerCase()))
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        return List.of();
    }
}