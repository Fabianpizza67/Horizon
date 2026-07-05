package com.usermc.horizon.command;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.faction.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * /faction command tree — updated for the flexible rank/permission system.
 *
 * Rank management:
 *   /faction rank create <name>              — create a new custom rank
 *   /faction rank delete <name>              — delete a custom rank
 *   /faction rank rename <name> <newname>    — rename any rank (including Leader/Default)
 *   /faction rank assign <player> <rank>     — assign a non-leader rank to a member
 *   /faction rank promote <player>           — promote a member to leader rank
 *   /faction rank demote <player>            — demote a leader to default rank
 *   /faction rank setdefault <rank>          — set which rank new members receive
 *   /faction rank perm <rank> <perm> on|off  — toggle a permission on a rank
 *   /faction rank list                       — list all ranks and their permissions
 */
public class FactionCommand implements CommandExecutor, TabCompleter {

    private static final String PFX = "§5[Faction] §r";
    private final Horizon plugin;

    public FactionCommand(Horizon plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (args.length == 0) { sendHelp(player); return true; }

        return switch (args[0].toLowerCase()) {
            case "help"     -> { sendHelp(player); yield true; }
            case "create"   -> cmdCreate(player, args);
            case "disband"  -> cmdDisband(player, args);
            case "invite"   -> cmdInvite(player, args);
            case "accept"   -> cmdAccept(player, args);
            case "decline"  -> cmdDecline(player, args);
            case "leave"    -> cmdLeave(player, args);
            case "kick"     -> cmdKick(player, args);
            case "rank"     -> cmdRank(player, args);
            case "info"     -> cmdInfo(player, args);
            case "list"     -> cmdList(player, args);
            case "bank"     -> cmdBank(player, args);
            case "ally"     -> cmdDiplomacy(player, args, FactionRelation.ALLIED);
            case "trade"    -> cmdDiplomacy(player, args, FactionRelation.TRADE_PARTNER);
            case "war"      -> cmdDiplomacy(player, args, FactionRelation.AT_WAR);
            case "peace"    -> cmdDiplomacy(player, args, FactionRelation.NEUTRAL);
            default -> { player.sendMessage(PFX + "§cUnknown subcommand. /faction help"); yield true; }
        };
    }

    // -----------------------------------------------------------------------
    // Help
    // -----------------------------------------------------------------------

    private void sendHelp(Player p) {
        p.sendMessage("§5§l╔══ Faction ══╗");
        p.sendMessage("§5/faction create <name>  §7— Found (costs §6" + FactionManager.FORMATION_COST + " EC§7)");
        p.sendMessage("§5/faction invite/accept/decline/leave/kick");
        p.sendMessage("§5/faction rank <create|delete|rename|assign|promote|demote|setdefault|perm|list>");
        p.sendMessage("§5/faction info [name] | list");
        p.sendMessage("§5/faction bank deposit/withdraw <amount>");
        p.sendMessage("§5/faction ally/trade/war/peace <faction>");
        p.sendMessage("§5/faction disband");
    }

    // -----------------------------------------------------------------------
    // Create / Disband
    // -----------------------------------------------------------------------

    private boolean cmdCreate(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(PFX + "§cUsage: /faction create <name>"); return true; }
        String name = args[1];
        if (name.length() > 32) { p.sendMessage(PFX + "§cName too long (max 32 chars)."); return true; }

        if (plugin.getFactionManager().getPlayerFaction(p.getUniqueId()) != null) {
            p.sendMessage(PFX + "§cLeave your current faction first."); return true; }

        Faction f = plugin.getFactionManager().create(p, name);
        if (f == null) {
            if (plugin.getFactionManager().getFactionByName(name) != null)
                p.sendMessage(PFX + "§cName already taken.");
            else
                p.sendMessage(PFX + "§cInsufficient funds — requires §6" + FactionManager.FORMATION_COST + " EC§c.");
            return true;
        }
        p.sendMessage(PFX + "§5" + f.getName() + " §7founded. You are its leader.");
        p.sendMessage("§7Use §5/faction invite <player> §7to recruit members.");
        return true;
    }

    private boolean cmdDisband(Player p, String[] args) {
        Faction f = requireFaction(p); if (f == null) return true;
        if (!f.isLeader(p.getUniqueId())) { p.sendMessage(PFX + "§cLeaders only."); return true; }
        plugin.getFactionManager().disband(f, p);
        p.sendMessage(PFX + "§c" + f.getName() + " §7dissolved.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Membership
    // -----------------------------------------------------------------------

    private boolean cmdInvite(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(PFX + "§cUsage: /faction invite <player>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        FactionMember me = f.getMember(p.getUniqueId());

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { p.sendMessage(PFX + "§c" + args[1] + " not online."); return true; }
        if (target.equals(p)) { p.sendMessage(PFX + "§cCannot invite yourself."); return true; }

        boolean ok = plugin.getFactionManager().invite(f, me, target);
        if (!ok) {
            if (!f.memberHasPermission(p.getUniqueId(), FactionPermission.INVITE_MEMBERS))
                p.sendMessage(PFX + "§cYou don't have invite permission.");
            else
                p.sendMessage(PFX + "§c" + target.getName() + " is already in a faction.");
            return true;
        }

        p.sendMessage(PFX + "§7Invited §5" + target.getName() + "§7.");
        target.sendMessage("§5[Faction] §f" + p.getName() + " §7invited you to §5" + f.getName()
                + "§7. Use §f/faction accept §7or §f/faction decline§7.");
        return true;
    }

    private boolean cmdAccept(Player p, String[] args) {
        if (!plugin.getFactionManager().hasPendingInvite(p.getUniqueId())) {
            p.sendMessage(PFX + "§cNo pending invite."); return true; }
        Faction joined = plugin.getFactionManager().acceptInvite(p);
        if (joined == null) { p.sendMessage(PFX + "§cInvite expired."); return true; }
        p.sendMessage(PFX + "§7Joined §5" + joined.getName() + "§7.");
        broadcastToFaction(joined, "§5[Faction] §f" + p.getName() + " §7joined.");
        return true;
    }

    private boolean cmdDecline(Player p, String[] args) {
        plugin.getFactionManager().declineInvite(p.getUniqueId());
        p.sendMessage(PFX + "§7Invite declined.");
        return true;
    }

    private boolean cmdLeave(Player p, String[] args) {
        Faction f = requireFaction(p); if (f == null) return true;
        String name = f.getName();
        boolean ok = plugin.getFactionManager().leave(p);
        if (!ok) {
            p.sendMessage(PFX + "§cPromote another leader with §f/faction rank promote <player> §cbefore leaving.");
            return true;
        }
        p.sendMessage(PFX + "§7Left §5" + name + "§7.");
        broadcastToFaction(f, "§5[Faction] §f" + p.getName() + " §7left.");
        return true;
    }

    private boolean cmdKick(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(PFX + "§cUsage: /faction kick <player>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;

        UUID targetUUID = findMemberUUID(f, args[1]);
        if (targetUUID == null) { p.sendMessage(PFX + "§c" + args[1] + " not in faction."); return true; }

        boolean ok = plugin.getFactionManager().kick(f, p.getUniqueId(), targetUUID);
        if (!ok) {
            p.sendMessage(PFX + "§cCannot kick: missing permission or target is a leader."); return true; }

        p.sendMessage(PFX + "§f" + args[1] + " §7kicked.");
        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null) target.sendMessage(PFX + "§7You were kicked from §5" + f.getName() + "§7.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Rank management
    // -----------------------------------------------------------------------

    private boolean cmdRank(Player p, String[] args) {
        if (args.length < 2) { sendRankHelp(p); return true; }
        return switch (args[1].toLowerCase()) {
            case "create"     -> cmdRankCreate(p, args);
            case "delete"     -> cmdRankDelete(p, args);
            case "rename"     -> cmdRankRename(p, args);
            case "assign"     -> cmdRankAssign(p, args);
            case "promote"    -> cmdRankPromote(p, args);
            case "demote"     -> cmdRankDemote(p, args);
            case "setdefault" -> cmdRankSetDefault(p, args);
            case "perm"       -> cmdRankPerm(p, args);
            case "list"       -> cmdRankList(p, args);
            default -> { sendRankHelp(p); yield true; }
        };
    }

    private void sendRankHelp(Player p) {
        p.sendMessage("§5Rank commands (all require MANAGE_RANKS permission):");
        p.sendMessage("§5/faction rank create <name>");
        p.sendMessage("§5/faction rank delete <name>");
        p.sendMessage("§5/faction rank rename <name> <newname>");
        p.sendMessage("§5/faction rank assign <player> <rank>");
        p.sendMessage("§5/faction rank promote <player>  §7— grants leader rank");
        p.sendMessage("§5/faction rank demote <player>   §7— removes leader rank");
        p.sendMessage("§5/faction rank setdefault <rank> §7— rank given to new members");
        p.sendMessage("§5/faction rank perm <rank> <permission> on|off");
        p.sendMessage("§5/faction rank list");
    }

    private boolean cmdRankCreate(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage(PFX + "§cUsage: /faction rank create <name>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        FactionRankDef rank = plugin.getFactionManager().createRank(f, p.getUniqueId(), args[2]);
        if (rank == null) {
            p.sendMessage(PFX + "§cCouldn't create rank — missing permission or name already exists.");
            return true;
        }
        p.sendMessage(PFX + "§aRank §f" + rank.getName() + " §acreated.");
        return true;
    }

    private boolean cmdRankDelete(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage(PFX + "§cUsage: /faction rank delete <name>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        FactionRankDef rank = f.getRankByName(args[2]);
        if (rank == null) { p.sendMessage(PFX + "§cRank §f" + args[2] + " §cnot found."); return true; }
        if (!plugin.getFactionManager().deleteRank(f, p.getUniqueId(), rank.getRankId())) {
            p.sendMessage(PFX + "§cCannot delete: missing permission or protected rank."); return true; }
        p.sendMessage(PFX + "§aRank §f" + args[2] + " §adeleted.");
        return true;
    }

    private boolean cmdRankRename(Player p, String[] args) {
        if (args.length < 4) { p.sendMessage(PFX + "§cUsage: /faction rank rename <name> <newname>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        FactionRankDef rank = f.getRankByName(args[2]);
        if (rank == null) { p.sendMessage(PFX + "§cRank §f" + args[2] + " §cnot found."); return true; }
        if (!plugin.getFactionManager().renameRank(f, p.getUniqueId(), rank.getRankId(), args[3])) {
            p.sendMessage(PFX + "§cCannot rename: missing permission."); return true; }
        p.sendMessage(PFX + "§aRank renamed to §f" + args[3] + "§a.");
        return true;
    }

    private boolean cmdRankAssign(Player p, String[] args) {
        if (args.length < 4) { p.sendMessage(PFX + "§cUsage: /faction rank assign <player> <rank>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        UUID targetUUID = findMemberUUID(f, args[2]);
        if (targetUUID == null) { p.sendMessage(PFX + "§c" + args[2] + " not in faction."); return true; }
        FactionRankDef rank = f.getRankByName(args[3]);
        if (rank == null) { p.sendMessage(PFX + "§cRank §f" + args[3] + " §cnot found."); return true; }
        if (!plugin.getFactionManager().assignRank(f, p.getUniqueId(), targetUUID, rank.getRankId())) {
            p.sendMessage(PFX + "§cCannot assign: missing permission or invalid rank."); return true; }
        p.sendMessage(PFX + "§f" + args[2] + " §7is now §r" + rank.getName() + "§7.");
        return true;
    }

    private boolean cmdRankPromote(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage(PFX + "§cUsage: /faction rank promote <player>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        UUID targetUUID = findMemberUUID(f, args[2]);
        if (targetUUID == null) { p.sendMessage(PFX + "§c" + args[2] + " not in faction."); return true; }
        if (!plugin.getFactionManager().promoteToLeader(f, p.getUniqueId(), targetUUID)) {
            p.sendMessage(PFX + "§cCannot promote: missing permission."); return true; }
        p.sendMessage(PFX + "§f" + args[2] + " §7is now a leader.");
        broadcastToFaction(f, "§5[Faction] §f" + args[2] + " §7has been promoted to leader.");
        return true;
    }

    private boolean cmdRankDemote(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage(PFX + "§cUsage: /faction rank demote <player>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        UUID targetUUID = findMemberUUID(f, args[2]);
        if (targetUUID == null) { p.sendMessage(PFX + "§c" + args[2] + " not in faction."); return true; }
        if (!plugin.getFactionManager().demoteFromLeader(f, p.getUniqueId(), targetUUID)) {
            p.sendMessage(PFX + "§cCannot demote: missing permission or last leader."); return true; }
        p.sendMessage(PFX + "§f" + args[2] + " §7demoted from leader.");
        return true;
    }

    private boolean cmdRankSetDefault(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage(PFX + "§cUsage: /faction rank setdefault <rank>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        FactionRankDef rank = f.getRankByName(args[2]);
        if (rank == null) { p.sendMessage(PFX + "§cRank §f" + args[2] + " §cnot found."); return true; }
        if (!plugin.getFactionManager().setNewMemberRank(f, p.getUniqueId(), rank.getRankId())) {
            p.sendMessage(PFX + "§cCannot set: missing permission or leader rank."); return true; }
        p.sendMessage(PFX + "§aNew members will now receive §f" + rank.getName() + "§a.");
        return true;
    }

    private boolean cmdRankPerm(Player p, String[] args) {
        // /faction rank perm <rankname> <permission> on|off
        if (args.length < 5) {
            p.sendMessage(PFX + "§cUsage: /faction rank perm <rank> <permission> on|off");
            p.sendMessage("§7Permissions: " + String.join(", ",
                    Arrays.stream(FactionPermission.values()).map(FactionPermission::name).toList()));
            return true;
        }
        Faction f = requireFaction(p); if (f == null) return true;
        FactionRankDef rank = f.getRankByName(args[2]);
        if (rank == null) { p.sendMessage(PFX + "§cRank §f" + args[2] + " §cnot found."); return true; }

        FactionPermission perm;
        try { perm = FactionPermission.valueOf(args[3].toUpperCase()); }
        catch (IllegalArgumentException e) {
            p.sendMessage(PFX + "§cUnknown permission §f" + args[3]); return true; }

        boolean granted = args[4].equalsIgnoreCase("on");
        if (!plugin.getFactionManager().setRankPermission(f, p.getUniqueId(), rank.getRankId(), perm, granted)) {
            p.sendMessage(PFX + "§cCannot edit: missing permission or leader rank."); return true; }

        p.sendMessage(PFX + "§f" + rank.getName() + "§7: §f" + perm.getDisplayName()
                + " §7set to " + (granted ? "§aON" : "§cOFF") + "§7.");
        return true;
    }

    private boolean cmdRankList(Player p, String[] args) {
        Faction f = requireFaction(p); if (f == null) return true;
        p.sendMessage("§5§lRanks — " + f.getName());
        UUID defaultRankId = f.getNewMemberRankId();
        for (FactionRankDef rank : f.getAllRanks()) {
            String flags = "";
            if (rank.isLeaderRank())  flags += " §6[Leader]";
            if (rank.isDefaultRank()) flags += " §7[Default]";
            if (rank.getRankId().equals(defaultRankId)) flags += " §a[New Members]";
            p.sendMessage("  §5" + rank.getName() + flags);

            if (!rank.isLeaderRank()) {
                List<String> perms = Arrays.stream(FactionPermission.values())
                        .filter(rank::hasPermission)
                        .map(FactionPermission::getDisplayName)
                        .toList();
                if (perms.isEmpty()) p.sendMessage("    §8No permissions");
                else p.sendMessage("    §8" + String.join(", ", perms));
            } else {
                p.sendMessage("    §8All permissions (leader)");
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Info / List
    // -----------------------------------------------------------------------

    private boolean cmdInfo(Player p, String[] args) {
        Faction f;
        if (args.length >= 2) {
            f = plugin.getFactionManager().getFactionByName(args[1]);
            if (f == null) { p.sendMessage(PFX + "§cNo faction named §5" + args[1]); return true; }
        } else {
            f = plugin.getFactionManager().getPlayerFaction(p.getUniqueId());
            if (f == null) { p.sendMessage(PFX + "§cNot in a faction. Specify a name."); return true; }
        }
        printInfo(p, f);
        return true;
    }

    private void printInfo(Player p, Faction f) {
        p.sendMessage("§5§l[ " + f.getName() + " ]");
        if (!f.getDescription().isBlank()) p.sendMessage("§7" + f.getDescription());
        p.sendMessage("§7Members: §f" + f.getMemberCount() + "  §7Bank: §6" + f.getBankBalance() + " EC");

        StringBuilder roster = new StringBuilder("§7Roster: ");
        f.getMembers().values().forEach(m -> {
            FactionRankDef rank = f.getRank(m.getRankId());
            String rName = rank != null ? rank.getName() : "?";
            roster.append("§f").append(m.getPlayerName()).append(" §8[").append(rName).append("]§7, ");
        });
        if (roster.length() > 9) { roster.setLength(roster.length() - 2); p.sendMessage(roster.toString()); }
    }

    private boolean cmdList(Player p, String[] args) {
        Collection<Faction> all = plugin.getFactionManager().getAllFactions();
        if (all.isEmpty()) { p.sendMessage(PFX + "§7No factions exist yet."); return true; }
        p.sendMessage("§5§lFactions §8(" + all.size() + "):");
        for (Faction f : all) {
            p.sendMessage("  §5" + f.getName() + " §8[§f" + f.getMemberCount() + " members§8]");
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Bank
    // -----------------------------------------------------------------------

    private boolean cmdBank(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(PFX + "§cUsage: /faction bank <deposit|withdraw> <amount>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;

        long amount;
        try { amount = Long.parseLong(args[2]); }
        catch (NumberFormatException e) { p.sendMessage(PFX + "§cAmount must be a number."); return true; }
        if (amount <= 0) { p.sendMessage(PFX + "§cAmount must be positive."); return true; }

        return switch (args[1].toLowerCase()) {
            case "deposit" -> {
                boolean ok = plugin.getFactionManager().depositToBank(p, f, amount);
                if (!ok) p.sendMessage(PFX + "§cInsufficient funds or no deposit permission.");
                else p.sendMessage(PFX + "§7Deposited §6" + amount + " EC §7to §5" + f.getName() + "§7.");
                yield true;
            }
            case "withdraw" -> {
                boolean ok = plugin.getFactionManager().withdrawFromBank(f, p, amount);
                if (!ok) p.sendMessage(PFX + "§cInsufficient funds or no withdraw permission.");
                else p.sendMessage(PFX + "§7Withdrew §6" + amount + " EC §7from §5" + f.getName() + "§7.");
                yield true;
            }
            default -> { p.sendMessage(PFX + "§cUse deposit or withdraw."); yield true; }
        };
    }

    // -----------------------------------------------------------------------
    // Diplomacy
    // -----------------------------------------------------------------------

    private boolean cmdDiplomacy(Player p, String[] args, FactionRelation relation) {
        if (args.length < 2) {
            p.sendMessage(PFX + "§cUsage: /faction " + args[0] + " <faction name>"); return true; }
        Faction mine = requireFaction(p); if (mine == null) return true;
        Faction target = plugin.getFactionManager().getFactionByName(args[1]);
        if (target == null) { p.sendMessage(PFX + "§cNo faction named §5" + args[1]); return true; }
        if (target.getFactionId().equals(mine.getFactionId())) {
            p.sendMessage(PFX + "§cCannot set relations with yourself."); return true; }

        boolean ok = plugin.getFactionManager().proposeRelation(mine, p.getUniqueId(), target, relation);
        if (!ok) {
            p.sendMessage(PFX + "§cYou don't have permission for that diplomatic action."); return true; }

        String action = switch (relation) {
            case ALLIED        -> "proposed an alliance with";
            case TRADE_PARTNER -> "proposed a trade partnership with";
            case AT_WAR        -> "declared war on";
            case NEUTRAL       -> "proposed peace with";
        };
        boolean immediate = relation == FactionRelation.AT_WAR || relation == FactionRelation.NEUTRAL;
        p.sendMessage(PFX + "§7You " + action + " §5" + target.getName() + "§7."
                + (immediate ? "" : " §8Awaiting their response."));
        broadcastToFaction(target, "§5[Faction] §5" + mine.getName() + " §7has " + action + " you.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private Faction requireFaction(Player p) {
        Faction f = plugin.getFactionManager().getPlayerFaction(p.getUniqueId());
        if (f == null) p.sendMessage(PFX + "§cNot in a faction. Use §f/faction create <name>§c.");
        return f;
    }

    private UUID findMemberUUID(Faction faction, String name) {
        for (FactionMember m : faction.getMembers().values())
            if (m.getPlayerName().equalsIgnoreCase(name)) return m.getPlayerUUID();
        return null;
    }

    private void broadcastToFaction(Faction faction, String message) {
        for (FactionMember m : faction.getMembers().values()) {
            Player online = plugin.getServer().getPlayer(m.getPlayerUUID());
            if (online != null) online.sendMessage(message);
        }
    }

    // -----------------------------------------------------------------------
    // Tab completion
    // -----------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) return filter(List.of("help","create","disband","invite","accept","decline",
                "leave","kick","rank","info","list","bank","ally","trade","war","peace"), args[0]);

        return switch (args[0].toLowerCase()) {
            case "invite","kick" -> args.length == 2
                    ? plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList()
                    : List.of();
            case "info","ally","trade","war","peace" -> args.length == 2
                    ? plugin.getFactionManager().getAllFactions().stream()
                    .map(Faction::getName).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList()
                    : List.of();
            case "bank" -> args.length == 2 ? filter(List.of("deposit","withdraw"), args[1]) : List.of();
            case "rank" -> {
                if (args.length == 2) yield filter(List.of("create","delete","rename","assign",
                        "promote","demote","setdefault","perm","list"), args[1]);

                Faction f = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
                if (f == null) yield List.of();

                List<String> rankNames = f.getAllRanks().stream().map(FactionRankDef::getName).toList();
                List<String> memberNames = f.getMembers().values().stream().map(FactionMember::getPlayerName).toList();

                yield switch (args[1].toLowerCase()) {
                    case "delete","rename","setdefault" -> args.length == 3 ? filter(rankNames, args[2]) : List.of();
                    case "assign" -> {
                        if (args.length == 3) yield filter(memberNames, args[2]);
                        if (args.length == 4) yield filter(rankNames, args[3]);
                        yield List.of();
                    }
                    case "promote","demote" -> args.length == 3 ? filter(memberNames, args[2]) : List.of();
                    case "perm" -> {
                        if (args.length == 3) yield filter(rankNames, args[2]);
                        if (args.length == 4) yield filter(
                                Arrays.stream(FactionPermission.values()).map(FactionPermission::name).toList(), args[3]);
                        if (args.length == 5) yield filter(List.of("on","off"), args[4]);
                        yield List.of();
                    }
                    default -> List.of();
                };
            }
            default -> List.of();
        };
    }

    private List<String> filter(List<String> opts, String partial) {
        return opts.stream().filter(s -> s.toLowerCase().startsWith(partial.toLowerCase())).toList();
    }
}