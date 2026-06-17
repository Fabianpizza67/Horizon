package com.usermc.horizon.station.gui;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.faction.*;
import com.usermc.horizon.ship.Ship;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.*;

/**
 * Faction Terminal GUI — 4 rows (36 slots).
 *
 * Tabs:
 *   OVERVIEW  — faction name/desc, leader, bank, join/leave/create
 *   MEMBERS   — roster with ranks
 *   DIPLOMACY — relations to other factions + pending proposals
 *   BANK      — deposit / withdraw
 *
 * Tab switcher in row 3 (slots 27-35).
 */
public class FactionTerminalGui extends HorizonGui {

    private enum Tab { OVERVIEW, MEMBERS, DIPLOMACY, BANK }

    // Tab slots
    private static final int SLOT_TAB_OVERVIEW  = 27;
    private static final int SLOT_TAB_MEMBERS   = 28;
    private static final int SLOT_TAB_DIPLOMACY = 29;
    private static final int SLOT_TAB_BANK      = 30;

    private Tab currentTab = Tab.OVERVIEW;

    public FactionTerminalGui(Ship ship, Player player) {
        super(ship, player, 4, "§5⚑ Faction Terminal");
    }

    @Override
    public void build() {
        inventory.clear();
        buildTabs();
        switch (currentTab) {
            case OVERVIEW  -> buildOverview();
            case MEMBERS   -> buildMembers();
            case DIPLOMACY -> buildDiplomacy();
            case BANK      -> buildBank();
        }
        fillAll();
    }

    // -----------------------------------------------------------------------
    // Tab bar (row 3)
    // -----------------------------------------------------------------------

    private void buildTabs() {
        Horizon plugin = Horizon.getInstance();
        Faction f = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());

        inventory.setItem(SLOT_TAB_OVERVIEW,
                makeItem(Material.PAPER, tab(Tab.OVERVIEW), "§7General info"));
        inventory.setItem(SLOT_TAB_MEMBERS,
                makeItem(f != null ? Material.PLAYER_HEAD : Material.BARRIER,
                        tab(Tab.MEMBERS), f != null ? "§7Roster" : "§8Not in a faction"));
        inventory.setItem(SLOT_TAB_DIPLOMACY,
                makeItem(f != null ? Material.COMPASS : Material.BARRIER,
                        tab(Tab.DIPLOMACY), f != null ? "§7Alliances & wars" : "§8Not in a faction"));
        inventory.setItem(SLOT_TAB_BANK,
                makeItem(f != null ? Material.GOLD_INGOT : Material.BARRIER,
                        tab(Tab.BANK), f != null ? "§7Deposit / withdraw" : "§8Not in a faction"));
    }

    private String tab(Tab t) {
        String prefix = (currentTab == t) ? "§5§l▶ " : "§8";
        return switch (t) {
            case OVERVIEW  -> prefix + "Overview";
            case MEMBERS   -> prefix + "Members";
            case DIPLOMACY -> prefix + "Diplomacy";
            case BANK      -> prefix + "Bank";
        };
    }

    // -----------------------------------------------------------------------
    // OVERVIEW tab
    // -----------------------------------------------------------------------

    private void buildOverview() {
        Horizon plugin = Horizon.getInstance();
        Faction f = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());

        if (f == null) {
            // Not in a faction — show create / accept options
            inventory.setItem(13, makeItem(Material.WRITABLE_BOOK,
                    "§a§l+ Create Faction",
                    "§7Cost: §6" + FactionManager.FORMATION_COST + " EC",
                    "§7Use §f/faction create <name> §7to found a faction.",
                    "§8You must not be in another faction."
            ));
            boolean hasPending = plugin.getFactionManager().hasPendingInvite(player.getUniqueId());
            if (hasPending) {
                Faction inviting = plugin.getFactionManager().getPendingInviteFaction(player.getUniqueId());
                inventory.setItem(11, makeItem(Material.LIME_DYE,
                        "§a§l✔ Accept Invite",
                        "§7You have been invited to:",
                        "§5" + (inviting != null ? inviting.getName() : "Unknown"),
                        "",
                        "§eClick to join!"));
                inventory.setItem(15, makeItem(Material.RED_DYE,
                        "§c§l✖ Decline Invite", "§7Decline and remove the pending invite."));
            }
            return;
        }

        // Faction summary
        FactionMember myMembership = f.getMember(player.getUniqueId());
        long balance = plugin.getEconomyManager().getBalance(player);

        inventory.setItem(4, makeItem(Material.PURPLE_BANNER,
                "§5§l" + f.getName(),
                f.getDescription().isBlank() ? "§8No description set" : "§7" + f.getDescription(),
                "",
                "§7Leader: §f" + getNameOf(f.getLeaderUUID(), f),
                "§7Members: §f" + f.getMemberCount(),
                "§7Bank: §6" + f.getBankBalance() + " EC",
                "",
                "§7Your rank: " + (myMembership != null ? myMembership.getRank().coloured() : "§8None")
        ));

        // Leave button (not available to solo leader)
        boolean isSoloLeader = f.isLeader(player.getUniqueId()) && f.getMemberCount() > 1;
        inventory.setItem(22, makeItem(
                isSoloLeader ? Material.BARRIER : Material.RED_DYE,
                isSoloLeader ? "§c§lCannot Leave" : "§c§l← Leave Faction",
                isSoloLeader
                        ? "§7Transfer leadership first with §f/faction transfer <player>"
                        : "§7Leave §5" + f.getName() + "§7 permanently."
        ));

        // Disband (leader only)
        if (f.isLeader(player.getUniqueId())) {
            inventory.setItem(24, makeItem(Material.TNT,
                    "§4§lDisband Faction",
                    "§7Permanently dissolves the faction.",
                    "§7All members are removed.",
                    "§750% of bank balance refunded.",
                    "",
                    "§cUse §f/faction disband §cto confirm."));
        }
    }

    // -----------------------------------------------------------------------
    // MEMBERS tab
    // -----------------------------------------------------------------------

    private void buildMembers() {
        Horizon plugin = Horizon.getInstance();
        Faction f = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (f == null) return;

        FactionMember myMembership = f.getMember(player.getUniqueId());

        List<FactionMember> sorted = f.getMembers().values().stream()
                .sorted(Comparator.comparingInt((FactionMember m) -> m.getRank().getTier()).reversed())
                .toList();

        int slot = 0;
        for (FactionMember m : sorted) {
            if (slot >= 27) break;
            boolean isMe = m.getPlayerUUID().equals(player.getUniqueId());
            List<String> lore = new ArrayList<>();
            lore.add("§7Rank: " + m.getRank().coloured());
            if (isMe) lore.add("§8— that's you");
            // Show promote/demote/kick hints if applicable
            if (!isMe && myMembership != null && myMembership.getRank().isAtLeast(FactionRank.OFFICER)) {
                lore.add("");
                lore.add("§8/faction rank " + m.getPlayerName() + " <rank>");
                if (!f.isLeader(m.getPlayerUUID()))
                    lore.add("§8/faction kick " + m.getPlayerName());
            }
            inventory.setItem(slot++, makeItem(Material.PLAYER_HEAD,
                    m.getRank().getColour() + m.getPlayerName(), lore));
        }
    }

    // -----------------------------------------------------------------------
    // DIPLOMACY tab
    // -----------------------------------------------------------------------

    private void buildDiplomacy() {
        Horizon plugin = Horizon.getInstance();
        Faction myFaction = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (myFaction == null) return;

        // Instructions
        inventory.setItem(0, makeItem(Material.BOOK,
                "§5§lDiplomacy",
                "§7Propose relations with §f/faction ally/war/trade/peace <faction>",
                "§7The other faction's officer must accept for alliances/trade.",
                "§7War and peace proposals take immediate effect."));

        // All other factions and their relation to us
        int slot = 9;
        for (Faction other : plugin.getFactionManager().getAllFactions()) {
            if (other.getFactionId().equals(myFaction.getFactionId())) continue;
            if (slot >= 27) break;

            FactionRelation rel = myFaction.getRelation(other.getFactionId());
            boolean pendingFromUs = plugin.getFactionManager()
                    .hasPendingProposal(myFaction.getFactionId(), other.getFactionId());
            boolean pendingFromThem = plugin.getFactionManager()
                    .hasPendingProposal(other.getFactionId(), myFaction.getFactionId());
            FactionRelation proposedByThem = plugin.getFactionManager()
                    .getPendingProposalRelation(other.getFactionId(), myFaction.getFactionId());

            Material mat = switch (rel) {
                case ALLIED       -> Material.LIME_DYE;
                case TRADE_PARTNER-> Material.CYAN_DYE;
                case AT_WAR       -> Material.RED_DYE;
                default           -> Material.GRAY_DYE;
            };

            List<String> lore = new ArrayList<>();
            lore.add("§7Status: " + rel.coloured());
            lore.add("§7Members: §f" + other.getMemberCount());
            if (pendingFromUs) lore.add("§e⏳ Your proposal pending their acceptance");
            if (pendingFromThem && proposedByThem != null)
                lore.add("§a⚡ They proposed: " + proposedByThem.coloured() + " §a— use /faction to respond");
            lore.add("");
            lore.add("§8/faction ally/war/trade/peace " + other.getName());

            inventory.setItem(slot++, makeItem(mat, rel.getColour() + other.getName(), lore));
        }

        if (slot == 9) {
            inventory.setItem(13, makeItem(Material.COMPASS,
                    "§8No other factions yet",
                    "§7When other players create factions,",
                    "§7they will appear here."));
        }
    }

    // -----------------------------------------------------------------------
    // BANK tab
    // -----------------------------------------------------------------------

    private void buildBank() {
        Horizon plugin = Horizon.getInstance();
        Faction f = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
        if (f == null) return;

        FactionMember member = f.getMember(player.getUniqueId());
        boolean canWithdraw = member != null && member.getRank().isAtLeast(FactionRank.OFFICER);
        long playerBal = plugin.getEconomyManager().getBalance(player);

        inventory.setItem(12, makeItem(Material.GOLD_BLOCK,
                "§6§lFaction Bank",
                "§7Balance: §6" + f.getBankBalance() + " EC",
                "",
                "§8Any member can deposit.",
                "§8Officers and above can withdraw."));

        inventory.setItem(10, makeItem(Material.LIME_DYE,
                "§a§l+ Deposit",
                "§7Your balance: §6" + playerBal + " EC",
                "§7Use §f/faction bank deposit <amount>"));

        inventory.setItem(14, makeItem(
                canWithdraw ? Material.YELLOW_DYE : Material.BARRIER,
                canWithdraw ? "§e§l- Withdraw" : "§c§lWithdraw (Officer+)",
                canWithdraw
                        ? "§7Use §f/faction bank withdraw <amount>"
                        : "§7Rank §f" + FactionRank.OFFICER.getDisplayName() + " §7required to withdraw."));
    }

    // -----------------------------------------------------------------------
    // Click handler
    // -----------------------------------------------------------------------

    @Override
    public boolean handleClick(int slot, ClickType click) {
        Horizon plugin = Horizon.getInstance();

        // Tab switches
        if (slot == SLOT_TAB_OVERVIEW)  { currentTab = Tab.OVERVIEW;  return true; }
        if (slot == SLOT_TAB_MEMBERS)   {
            if (plugin.getFactionManager().getPlayerFaction(player.getUniqueId()) != null)
                currentTab = Tab.MEMBERS;
            return true;
        }
        if (slot == SLOT_TAB_DIPLOMACY) {
            if (plugin.getFactionManager().getPlayerFaction(player.getUniqueId()) != null)
                currentTab = Tab.DIPLOMACY;
            return true;
        }
        if (slot == SLOT_TAB_BANK) {
            if (plugin.getFactionManager().getPlayerFaction(player.getUniqueId()) != null)
                currentTab = Tab.BANK;
            return true;
        }

        // Overview actions
        if (currentTab == Tab.OVERVIEW) {
            // Accept invite
            if (slot == 11 && plugin.getFactionManager().hasPendingInvite(player.getUniqueId())) {
                player.closeInventory();
                Faction joined = plugin.getFactionManager().acceptInvite(player);
                if (joined != null) {
                    player.sendMessage("§5[Faction] §7You joined §5" + joined.getName() + "§7.");
                } else {
                    player.sendMessage("§c[Faction] Invite expired.");
                }
                return false;
            }
            // Decline invite
            if (slot == 15 && plugin.getFactionManager().hasPendingInvite(player.getUniqueId())) {
                plugin.getFactionManager().declineInvite(player.getUniqueId());
                player.sendActionBar("§c[Faction] Invite declined.");
                return true;
            }
            // Leave
            if (slot == 22) {
                Faction f = plugin.getFactionManager().getPlayerFaction(player.getUniqueId());
                if (f != null && !f.isLeader(player.getUniqueId()) || (f != null && f.getMemberCount() == 1)) {
                    player.closeInventory();
                    boolean left = plugin.getFactionManager().leave(player);
                    player.sendMessage(left
                            ? "§5[Faction] §7You left §5" + f.getName() + "§7."
                            : "§c[Faction] Transfer leadership before leaving.");
                    return false;
                }
                return false;
            }
        }

        return false;
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private String getNameOf(UUID uuid, Faction faction) {
        FactionMember m = faction.getMember(uuid);
        if (m != null) return m.getPlayerName();
        var p = Horizon.getInstance().getServer().getOfflinePlayer(uuid);
        return p.getName() != null ? p.getName() : uuid.toString().substring(0, 8);
    }
}