package com.usermc.horizon.command;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.faction.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * /faction command tree.
 *
 * Player commands:
 *   create <name>               — found a faction (costs 500 EC)
 *   disband                     — dissolve your faction (leader only)
 *   invite <player>             — invite a player (officer+)
 *   accept                      — accept a pending invite
 *   decline                     — decline a pending invite
 *   leave                       — leave your faction
 *   kick <player>               — kick a member (officer+, lower rank only)
 *   rank <player> <rank>        — set a member's rank (officer+ for member/recruit, leader for officer)
 *   transfer <player>           — transfer leadership (leader only)
 *   info [name]                 — view faction info
 *   list                        — list all factions
 *   bank deposit <amount>       — deposit EC to faction bank
 *   bank withdraw <amount>      — withdraw EC from faction bank (officer+)
 *   ally <faction>              — propose / accept alliance
 *   trade <faction>             — propose / accept trade partnership
 *   war <faction>               — declare war (immediate, unilateral)
 *   peace <faction>             — propose / confirm peace (returns to neutral)
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
            case "transfer" -> cmdTransfer(player, args);
            case "info"     -> cmdInfo(player, args);
            case "list"     -> cmdList(player, args);
            case "bank"     -> cmdBank(player, args);
            case "ally"     -> cmdDiplomacy(player, args, FactionRelation.ALLIED);
            case "trade"    -> cmdDiplomacy(player, args, FactionRelation.TRADE_PARTNER);
            case "war"      -> cmdDiplomacy(player, args, FactionRelation.AT_WAR);
            case "peace"    -> cmdDiplomacy(player, args, FactionRelation.NEUTRAL);
            default -> { player.sendMessage(PFX + "§cUnknown subcommand."); yield true; }
        };
    }

    // -----------------------------------------------------------------------

    private void sendHelp(Player p) {
        p.sendMessage("§5§l╔══ Faction Commands ══╗");
        p.sendMessage("§5/faction create <name> §7— Found (costs §6500 EC§7)");
        p.sendMessage("§5/faction invite/accept/decline/leave/kick");
        p.sendMessage("§5/faction rank <player> <rank> §7— Set member rank");
        p.sendMessage("§5/faction transfer <player> §7— Hand over leadership");
        p.sendMessage("§5/faction info [name] | list");
        p.sendMessage("§5/faction bank deposit/withdraw <amount>");
        p.sendMessage("§5/faction ally/trade/war/peace <faction>");
        p.sendMessage("§5/faction disband §7— Dissolve your faction");
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
                p.sendMessage(PFX + "§cThat name is already taken.");
            else
                p.sendMessage(PFX + "§cInsufficient funds — creating a faction costs §6"
                        + FactionManager.FORMATION_COST + " EC§c.");
            return true;
        }
        p.sendMessage(PFX + "§5" + f.getName() + " §7has been founded. You are its leader.");
        p.sendMessage("§7Use §5/faction invite <player> §7to recruit members.");
        return true;
    }

    private boolean cmdDisband(Player p, String[] args) {
        Faction f = requireFaction(p); if (f == null) return true;
        if (!f.isLeader(p.getUniqueId())) { p.sendMessage(PFX + "§cOnly the leader can disband."); return true; }
        plugin.getFactionManager().disband(f, p);
        p.sendMessage(PFX + "§c" + f.getName() + " §7has been dissolved.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Membership
    // -----------------------------------------------------------------------

    private boolean cmdInvite(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(PFX + "§cUsage: /faction invite <player>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        FactionMember me = f.getMember(p.getUniqueId());
        if (me == null || !me.getRank().isAtLeast(FactionRank.OFFICER)) {
            p.sendMessage(PFX + "§cOfficer rank required to invite."); return true; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { p.sendMessage(PFX + "§c" + args[1] + " is not online."); return true; }
        if (target.equals(p)) { p.sendMessage(PFX + "§cYou cannot invite yourself."); return true; }

        boolean ok = plugin.getFactionManager().invite(f, target);
        if (!ok) { p.sendMessage(PFX + "§c" + target.getName() + " is already in a faction."); return true; }

        p.sendMessage(PFX + "§7Invited §5" + target.getName() + " §7to " + f.getName() + ".");
        target.sendMessage("§5[Faction] §f" + p.getName() + " §7invited you to join §5" + f.getName()
                + "§7. Use §f/faction accept §7or §f/faction decline§7.");
        return true;
    }

    private boolean cmdAccept(Player p, String[] args) {
        if (!plugin.getFactionManager().hasPendingInvite(p.getUniqueId())) {
            p.sendMessage(PFX + "§cNo pending faction invite."); return true; }
        Faction joined = plugin.getFactionManager().acceptInvite(p);
        if (joined == null) { p.sendMessage(PFX + "§cInvite expired."); return true; }
        p.sendMessage(PFX + "§7You joined §5" + joined.getName() + "§7.");
        broadcastToFaction(joined, "§5[Faction] §f" + p.getName() + " §7has joined the faction.");
        return true;
    }

    private boolean cmdDecline(Player p, String[] args) {
        if (!plugin.getFactionManager().hasPendingInvite(p.getUniqueId())) {
            p.sendMessage(PFX + "§cNo pending faction invite."); return true; }
        plugin.getFactionManager().declineInvite(p.getUniqueId());
        p.sendMessage(PFX + "§7Invite declined.");
        return true;
    }

    private boolean cmdLeave(Player p, String[] args) {
        Faction f = requireFaction(p); if (f == null) return true;
        String name = f.getName();
        boolean ok = plugin.getFactionManager().leave(p);
        if (!ok) {
            p.sendMessage(PFX + "§cTransfer leadership with §f/faction transfer <player> §cbefore leaving.");
            return true;
        }
        p.sendMessage(PFX + "§7You left §5" + name + "§7.");
        broadcastToFaction(f, "§5[Faction] §f" + p.getName() + " §7has left the faction.");
        return true;
    }

    private boolean cmdKick(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(PFX + "§cUsage: /faction kick <player>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        FactionMember me = f.getMember(p.getUniqueId());

        Player target = Bukkit.getPlayerExact(args[1]);
        UUID targetUUID = target != null ? target.getUniqueId()
                : findMemberUUID(f, args[1]);
        if (targetUUID == null) { p.sendMessage(PFX + "§c" + args[1] + " not found in faction."); return true; }

        boolean ok = plugin.getFactionManager().kick(f, me, targetUUID);
        if (!ok) {
            p.sendMessage(PFX + "§cCannot kick: check their rank or your own permissions."); return true; }

        p.sendMessage(PFX + "§f" + args[1] + " §7has been kicked from the faction.");
        if (target != null) target.sendMessage(PFX + "§7You were kicked from §5" + f.getName() + "§7.");
        return true;
    }

    private boolean cmdRank(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage(PFX + "§cUsage: /faction rank <player> <recruit|member|officer>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;
        FactionMember me = f.getMember(p.getUniqueId());

        FactionRank newRank = FactionRank.fromString(args[2]);
        if (newRank == null || newRank == FactionRank.LEADER) {
            p.sendMessage(PFX + "§cValid ranks: recruit, member, officer"); return true; }

        UUID targetUUID = findMemberUUID(f, args[1]);
        if (targetUUID == null) { p.sendMessage(PFX + "§c" + args[1] + " not in your faction."); return true; }

        boolean ok = plugin.getFactionManager().setRank(f, me, targetUUID, newRank);
        if (!ok) { p.sendMessage(PFX + "§cCannot set that rank — check permissions."); return true; }
        p.sendMessage(PFX + "§f" + args[1] + " §7is now §r" + newRank.coloured() + "§7.");
        return true;
    }

    private boolean cmdTransfer(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(PFX + "§cUsage: /faction transfer <player>"); return true; }
        Faction f = requireFaction(p); if (f == null) return true;

        UUID targetUUID = findMemberUUID(f, args[1]);
        if (targetUUID == null) { p.sendMessage(PFX + "§c" + args[1] + " not in your faction."); return true; }

        boolean ok = plugin.getFactionManager().transferLeadership(f, p, targetUUID);
        if (!ok) { p.sendMessage(PFX + "§cLeadership transfer failed."); return true; }
        p.sendMessage(PFX + "§7Leadership transferred to §5" + args[1] + "§7.");
        broadcastToFaction(f, "§5[Faction] §f" + args[1] + " §7is now the faction leader.");
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
            if (f == null) { p.sendMessage(PFX + "§cYou are not in a faction. Specify a name."); return true; }
        }

        Faction myFaction = plugin.getFactionManager().getPlayerFaction(p.getUniqueId());
        FactionRelation rel = myFaction != null && !myFaction.getFactionId().equals(f.getFactionId())
                ? myFaction.getRelation(f.getFactionId()) : null;

        p.sendMessage("§5§l[ " + f.getName() + " ]"
                + (rel != null ? "  " + rel.coloured() : ""));
        if (!f.getDescription().isBlank()) p.sendMessage("§7" + f.getDescription());
        p.sendMessage("§7Leader: §f" + getNameOf(f.getLeaderUUID(), f)
                + "  §7Members: §f" + f.getMemberCount());
        p.sendMessage("§7Bank: §6" + f.getBankBalance() + " EC");
        p.sendMessage("§7Founded: §f" + new java.util.Date(f.getCreatedAt()).toString().substring(0, 10));

        // Show members (brief)
        StringBuilder members = new StringBuilder("§7Roster: ");
        f.getMembers().values().stream()
                .sorted(Comparator.comparingInt((FactionMember m) -> m.getRank().getTier()).reversed())
                .forEach(m -> members.append(m.getRank().getColour())
                        .append(m.getPlayerName()).append("§8, "));
        if (members.length() > 9) {
            members.setLength(members.length() - 4);
            p.sendMessage(members.toString());
        }
        return true;
    }

    private boolean cmdList(Player p, String[] args) {
        Collection<Faction> all = plugin.getFactionManager().getAllFactions();
        if (all.isEmpty()) { p.sendMessage(PFX + "§7No factions exist yet."); return true; }
        p.sendMessage("§5§lFactions §8(" + all.size() + "):");
        for (Faction f : all) {
            Faction mine = plugin.getFactionManager().getPlayerFaction(p.getUniqueId());
            FactionRelation rel = mine != null && !mine.getFactionId().equals(f.getFactionId())
                    ? mine.getRelation(f.getFactionId()) : null;
            p.sendMessage("  §5" + f.getName() + " §8[§f" + f.getMemberCount() + "§8]"
                    + (rel != null && rel != FactionRelation.NEUTRAL ? "  " + rel.coloured() : ""));
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
                if (!ok) p.sendMessage(PFX + "§cInsufficient funds.");
                else p.sendMessage(PFX + "§7Deposited §6" + amount + " EC §7to §5" + f.getName() + "§7.");
                yield true;
            }
            case "withdraw" -> {
                boolean ok = plugin.getFactionManager().withdrawFromBank(f, p, amount);
                if (!ok) p.sendMessage(PFX + "§cInsufficient funds or insufficient rank (Officer+).");
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

        FactionMember me = mine.getMember(p.getUniqueId());
        if (me == null || !me.getRank().isAtLeast(FactionRank.OFFICER)) {
            p.sendMessage(PFX + "§cOfficer rank required for diplomacy."); return true; }

        Faction target = plugin.getFactionManager().getFactionByName(args[1]);
        if (target == null) { p.sendMessage(PFX + "§cNo faction named §5" + args[1]); return true; }
        if (target.getFactionId().equals(mine.getFactionId())) {
            p.sendMessage(PFX + "§cCannot set relations with yourself."); return true; }

        plugin.getFactionManager().proposeRelation(mine, target, relation);

        // Build appropriate feedback message
        String action = switch (relation) {
            case ALLIED        -> "proposed an alliance with";
            case TRADE_PARTNER -> "proposed a trade partnership with";
            case AT_WAR        -> "declared war on";
            case NEUTRAL       -> "proposed peace with";
        };
        boolean isImmediate = relation == FactionRelation.AT_WAR || relation == FactionRelation.NEUTRAL;

        p.sendMessage(PFX + "§7You " + action + " §5" + target.getName() + "§7."
                + (isImmediate ? "" : " §8Awaiting their response."));
        broadcastToFaction(target, "§5[Faction] §5" + mine.getName() + " §7has " + action + " you.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private Faction requireFaction(Player p) {
        Faction f = plugin.getFactionManager().getPlayerFaction(p.getUniqueId());
        if (f == null) p.sendMessage(PFX + "§cYou are not in a faction. Use §f/faction create <name>§c.");
        return f;
    }

    private UUID findMemberUUID(Faction faction, String name) {
        for (FactionMember m : faction.getMembers().values())
            if (m.getPlayerName().equalsIgnoreCase(name)) return m.getPlayerUUID();
        return null;
    }

    private String getNameOf(UUID uuid, Faction faction) {
        FactionMember m = faction.getMember(uuid);
        if (m != null) return m.getPlayerName();
        var p = plugin.getServer().getOfflinePlayer(uuid);
        return p.getName() != null ? p.getName() : uuid.toString().substring(0, 8);
    }

    private void broadcastToFaction(Faction faction, String message) {
        for (FactionMember member : faction.getMembers().values()) {
            Player online = plugin.getServer().getPlayer(member.getPlayerUUID());
            if (online != null) online.sendMessage(message);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1)
            return filter(List.of("help","create","disband","invite","accept","decline","leave",
                    "kick","rank","transfer","info","list","bank","ally","trade","war","peace"), args[0]);

        return switch (args[0].toLowerCase()) {
            case "invite","kick","transfer" -> args.length == 2
                    ? plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList()
                    : List.of();
            case "rank" -> {
                if (args.length == 3) yield filter(List.of("recruit","member","officer"), args[2]);
                yield List.of();
            }
            case "info","ally","trade","war","peace" -> args.length == 2
                    ? plugin.getFactionManager().getAllFactions().stream()
                    .map(Faction::getName).filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList()
                    : List.of();
            case "bank" -> {
                if (args.length == 2) yield filter(List.of("deposit","withdraw"), args[1]);
                yield List.of();
            }
            default -> List.of();
        };
    }

    private List<String> filter(List<String> opts, String partial) {
        return opts.stream().filter(s -> s.toLowerCase().startsWith(partial.toLowerCase())).toList();
    }
}