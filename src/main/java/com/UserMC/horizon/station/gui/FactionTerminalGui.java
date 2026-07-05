package com.usermc.horizon.station.gui;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.faction.*;
import com.usermc.horizon.ship.Ship;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.*;

/**
 * Faction Terminal GUI — 5 rows.
 *
 * Tabs: OVERVIEW | MEMBERS | RANKS | DIPLOMACY | BANK
 */
public class FactionTerminalGui extends HorizonGui {

    private enum Tab { OVERVIEW, MEMBERS, RANKS, DIPLOMACY, BANK }

    private static final int SLOT_TAB_OVERVIEW  = 36;
    private static final int SLOT_TAB_MEMBERS   = 37;
    private static final int SLOT_TAB_RANKS     = 38;
    private static final int SLOT_TAB_DIPLOMACY = 39;
    private static final int SLOT_TAB_BANK      = 40;

    private Tab currentTab = Tab.OVERVIEW;

    /** Which rank is selected in the RANKS tab for permission editing. */
    private UUID selectedRankId = null;

    public FactionTerminalGui(Ship ship, Player player) {
        super(ship, player, 5, "§5⚑ Faction Terminal");
    }

    @Override
    public void build() {
        inventory.clear();
        buildTabBar();
        switch (currentTab) {
            case OVERVIEW  -> buildOverview();
            case MEMBERS   -> buildMembers();
            case RANKS     -> buildRanks();
            case DIPLOMACY -> buildDiplomacy();
            case BANK      -> buildBank();
        }
        fillAll();
    }

    // -----------------------------------------------------------------------
    // Tab bar (row 4, slots 36-44)
    // -----------------------------------------------------------------------

    private void buildTabBar() {
        Faction f = faction();
        inventory.setItem(SLOT_TAB_OVERVIEW,
                makeItem(Material.PAPER, tabLabel(Tab.OVERVIEW), "§7General info"));
        inventory.setItem(SLOT_TAB_MEMBERS,
                makeItem(f != null ? Material.PLAYER_HEAD : Material.BARRIER,
                        tabLabel(Tab.MEMBERS), f != null ? "§7Roster" : "§8Not in a faction"));
        inventory.setItem(SLOT_TAB_RANKS,
                makeItem(f != null ? Material.NAME_TAG : Material.BARRIER,
                        tabLabel(Tab.RANKS), f != null ? "§7Rank editor" : "§8Not in a faction"));
        inventory.setItem(SLOT_TAB_DIPLOMACY,
                makeItem(f != null ? Material.COMPASS : Material.BARRIER,
                        tabLabel(Tab.DIPLOMACY), f != null ? "§7Relations" : "§8Not in a faction"));
        inventory.setItem(SLOT_TAB_BANK,
                makeItem(f != null ? Material.GOLD_INGOT : Material.BARRIER,
                        tabLabel(Tab.BANK), f != null ? "§7Faction bank" : "§8Not in a faction"));
    }

    private String tabLabel(Tab t) {
        String prefix = currentTab == t ? "§5§l▶ " : "§8";
        return switch (t) {
            case OVERVIEW  -> prefix + "Overview";
            case MEMBERS   -> prefix + "Members";
            case RANKS     -> prefix + "Ranks";
            case DIPLOMACY -> prefix + "Diplomacy";
            case BANK      -> prefix + "Bank";
        };
    }

    // -----------------------------------------------------------------------
    // OVERVIEW
    // -----------------------------------------------------------------------

    private void buildOverview() {
        Faction f = faction();
        if (f == null) {
            // Not in a faction
            inventory.setItem(13, makeItem(Material.WRITABLE_BOOK,
                    "§a§l+ Create Faction",
                    "§7Cost: §6" + FactionManager.FORMATION_COST + " EC",
                    "§7Use §f/faction create <name>"));

            if (Horizon.getInstance().getFactionManager().hasPendingInvite(player.getUniqueId())) {
                Faction inv = Horizon.getInstance().getFactionManager().getPendingInviteFaction(player.getUniqueId());
                inventory.setItem(11, makeItem(Material.LIME_DYE,
                        "§a§l✔ Accept Invite",
                        "§7Invited to: §5" + (inv != null ? inv.getName() : "Unknown"),
                        "§eClick to join!"));
                inventory.setItem(15, makeItem(Material.RED_DYE, "§c§l✖ Decline Invite"));
            }
            return;
        }

        FactionMember me = f.getMember(player.getUniqueId());
        FactionRankDef myRank = me != null ? f.getRank(me.getRankId()) : null;

        inventory.setItem(4, makeItem(Material.PURPLE_BANNER,
                "§5§l" + f.getName(),
                f.getDescription().isBlank() ? "§8No description" : "§7" + f.getDescription(),
                "",
                "§7Members: §f" + f.getMemberCount(),
                "§7Bank:    §6" + f.getBankBalance() + " EC",
                "§7Your rank: §f" + (myRank != null ? myRank.getName() : "Unknown")
        ));

        // Leave button
        boolean soloLeader = f.isLeader(player.getUniqueId()) && f.countLeaders() <= 1 && f.getMemberCount() > 1;
        inventory.setItem(22, makeItem(
                soloLeader ? Material.BARRIER : Material.RED_DYE,
                soloLeader ? "§c§lCannot Leave" : "§c§l← Leave Faction",
                soloLeader ? "§7Promote another leader first." : "§7Leave §5" + f.getName() + "§7 permanently."
        ));

        // Disband (leaders only)
        if (f.isLeader(player.getUniqueId())) {
            inventory.setItem(24, makeItem(Material.TNT, "§4§lDisband Faction",
                    "§7Use §f/faction disband §7to confirm.",
                    "§750% of bank balance refunded."));
        }
    }

    // -----------------------------------------------------------------------
    // MEMBERS
    // -----------------------------------------------------------------------

    private void buildMembers() {
        Faction f = faction(); if (f == null) return;

        List<FactionMember> sorted = f.getMembers().values().stream()
                .sorted(Comparator.comparingInt(m -> {
                    FactionRankDef r = f.getRank(m.getRankId());
                    return r != null ? r.getHierarchyPosition() : 99;
                }))
                .toList();

        int slot = 0;
        for (FactionMember m : sorted) {
            if (slot >= 36) break;
            FactionRankDef rank = f.getRank(m.getRankId());
            String rankName = rank != null ? rank.getName() : "?";
            boolean isMe = m.getPlayerUUID().equals(player.getUniqueId());

            List<String> lore = new ArrayList<>();
            lore.add("§7Rank: §f" + rankName);
            if (isMe) lore.add("§8— that's you");
            if (!isMe && f.memberHasPermission(player.getUniqueId(), FactionPermission.KICK_MEMBERS)
                    && !f.isLeader(m.getPlayerUUID()))
                lore.add("§8/faction kick " + m.getPlayerName());
            if (f.memberHasPermission(player.getUniqueId(), FactionPermission.MANAGE_RANKS))
                lore.add("§8/faction rank assign " + m.getPlayerName() + " <rank>");

            inventory.setItem(slot++, makeItem(Material.PLAYER_HEAD,
                    (f.isLeader(m.getPlayerUUID()) ? "§6" : "§f") + m.getPlayerName(), lore));
        }
    }

    // -----------------------------------------------------------------------
    // RANKS — two-level: rank list → permission editor for selected rank
    // -----------------------------------------------------------------------

    private void buildRanks() {
        Faction f = faction(); if (f == null) return;
        boolean canManage = f.memberHasPermission(player.getUniqueId(), FactionPermission.MANAGE_RANKS);

        if (selectedRankId != null && f.getRank(selectedRankId) != null) {
            buildRankPermEditor(f, f.getRank(selectedRankId), canManage);
        } else {
            selectedRankId = null;
            buildRankList(f, canManage);
        }
    }

    private void buildRankList(Faction f, boolean canManage) {
        UUID newMemberRankId = f.getNewMemberRankId();
        int slot = 0;
        for (FactionRankDef rank : f.getAllRanks()) {
            if (slot >= 27) break;
            List<String> lore = new ArrayList<>();
            if (rank.isLeaderRank())  lore.add("§6[Leader — always full permissions]");
            if (rank.isDefaultRank()) lore.add("§7[Default rank — editable]");
            if (rank.getRankId().equals(newMemberRankId)) lore.add("§a[Assigned to new members]");
            if (!rank.isLeaderRank()) {
                long count = Arrays.stream(FactionPermission.values()).filter(rank::hasPermission).count();
                lore.add("§7Permissions: §f" + count + "§7/" + FactionPermission.values().length);
            }
            if (canManage && !rank.isLeaderRank()) lore.add("§eClick to edit permissions");

            inventory.setItem(slot++, makeItem(
                    rank.isLeaderRank() ? Material.GOLDEN_SWORD : Material.STICK,
                    (rank.isLeaderRank() ? "§6" : "§f") + rank.getName(), lore));
        }

        if (canManage) {
            inventory.setItem(35, makeItem(Material.LIME_DYE,
                    "§a+ Create New Rank",
                    "§7Use §f/faction rank create <name>"));
        }
    }

    private void buildRankPermEditor(Faction f, FactionRankDef rank, boolean canManage) {
        // Back button
        inventory.setItem(0, makeItem(Material.ARROW, "§7← Back to rank list"));

        inventory.setItem(4, makeItem(Material.NAME_TAG,
                "§5§l" + rank.getName(),
                rank.isLeaderRank() ? "§6All permissions (leader)" : "§7Click permissions to toggle",
                canManage && !rank.isLeaderRank() ? "§eEditing enabled" : "§8View only"));

        FactionPermission[] perms = FactionPermission.values();
        for (int i = 0; i < perms.length && i < 27; i++) {
            FactionPermission perm = perms[i];
            boolean has = rank.hasPermission(perm);
            inventory.setItem(9 + i, makeItem(
                    has ? Material.LIME_DYE : Material.RED_DYE,
                    (has ? "§a" : "§c") + perm.getDisplayName(),
                    "§7" + perm.getDescription(),
                    canManage && !rank.isLeaderRank()
                            ? (has ? "§eClick to revoke" : "§eClick to grant")
                            : "§8Cannot edit"
            ));
        }
    }

    // -----------------------------------------------------------------------
    // DIPLOMACY
    // -----------------------------------------------------------------------

    private void buildDiplomacy() {
        Faction myFaction = faction(); if (myFaction == null) return;

        inventory.setItem(0, makeItem(Material.BOOK,
                "§5§lDiplomacy",
                "§7/faction ally/war/trade/peace <faction>",
                "§7War and peace are immediate.",
                "§7Alliances and trade require mutual acceptance."));

        int slot = 9;
        for (Faction other : Horizon.getInstance().getFactionManager().getAllFactions()) {
            if (other.getFactionId().equals(myFaction.getFactionId())) continue;
            if (slot >= 36) break;

            FactionRelation rel = myFaction.getRelation(other.getFactionId());
            boolean pendingFromUs   = Horizon.getInstance().getFactionManager()
                    .hasPendingProposal(myFaction.getFactionId(), other.getFactionId());
            boolean pendingFromThem = Horizon.getInstance().getFactionManager()
                    .hasPendingProposal(other.getFactionId(), myFaction.getFactionId());

            Material mat = switch (rel) {
                case ALLIED        -> Material.LIME_DYE;
                case TRADE_PARTNER -> Material.CYAN_DYE;
                case AT_WAR        -> Material.RED_DYE;
                default            -> Material.GRAY_DYE;
            };

            List<String> lore = new ArrayList<>();
            lore.add("§7Status: " + rel.coloured());
            lore.add("§7Members: §f" + other.getMemberCount());
            if (pendingFromUs)   lore.add("§e⏳ Your proposal pending");
            if (pendingFromThem) lore.add("§a⚡ They proposed — use /faction to respond");
            lore.add("§8/faction ally/war/trade/peace " + other.getName());

            inventory.setItem(slot++, makeItem(mat, rel.getColour() + other.getName(), lore));
        }
    }

    // -----------------------------------------------------------------------
    // BANK
    // -----------------------------------------------------------------------

    private void buildBank() {
        Faction f = faction(); if (f == null) return;
        boolean canDeposit  = f.memberHasPermission(player.getUniqueId(), FactionPermission.BANK_DEPOSIT);
        boolean canWithdraw = f.memberHasPermission(player.getUniqueId(), FactionPermission.BANK_WITHDRAW);
        long playerBal = Horizon.getInstance().getEconomyManager().getBalance(player);

        inventory.setItem(12, makeItem(Material.GOLD_BLOCK,
                "§6§lFaction Bank",
                "§7Balance: §6" + f.getBankBalance() + " EC"));

        inventory.setItem(10, makeItem(
                canDeposit ? Material.LIME_DYE : Material.BARRIER,
                canDeposit ? "§a§l+ Deposit" : "§c§lDeposit (no permission)",
                "§7Your balance: §6" + playerBal + " EC",
                "§7Use §f/faction bank deposit <amount>"));

        inventory.setItem(14, makeItem(
                canWithdraw ? Material.YELLOW_DYE : Material.BARRIER,
                canWithdraw ? "§e§l- Withdraw" : "§c§lWithdraw (no permission)",
                "§7Use §f/faction bank withdraw <amount>"));
    }

    // -----------------------------------------------------------------------
    // Click handler
    // -----------------------------------------------------------------------

    @Override
    public boolean handleClick(int slot, ClickType click) {
        // Tab switches
        if (slot == SLOT_TAB_OVERVIEW)  { currentTab = Tab.OVERVIEW;  selectedRankId = null; return true; }
        if (slot == SLOT_TAB_MEMBERS)   { currentTab = Tab.MEMBERS;   selectedRankId = null; return true; }
        if (slot == SLOT_TAB_RANKS)     { currentTab = Tab.RANKS;     return true; }
        if (slot == SLOT_TAB_DIPLOMACY) { currentTab = Tab.DIPLOMACY; selectedRankId = null; return true; }
        if (slot == SLOT_TAB_BANK)      { currentTab = Tab.BANK;      selectedRankId = null; return true; }

        Faction f = faction();
        if (f == null) return handleNoFactionClick(slot);

        return switch (currentTab) {
            case OVERVIEW  -> handleOverviewClick(slot, f);
            case RANKS     -> handleRanksClick(slot, f);
            default        -> false;
        };
    }

    private boolean handleNoFactionClick(int slot) {
        var fm = Horizon.getInstance().getFactionManager();
        if (slot == 11 && fm.hasPendingInvite(player.getUniqueId())) {
            player.closeInventory();
            Faction joined = fm.acceptInvite(player);
            player.sendMessage(joined != null
                    ? "§5[Faction] §7Joined §5" + joined.getName() + "§7."
                    : "§c[Faction] Invite expired.");
            return false;
        }
        if (slot == 15 && fm.hasPendingInvite(player.getUniqueId())) {
            fm.declineInvite(player.getUniqueId());
            player.sendActionBar("§c[Faction] Invite declined.");
            return true;
        }
        return false;
    }

    private boolean handleOverviewClick(int slot, Faction f) {
        if (slot == 22) {
            player.closeInventory();
            boolean left = Horizon.getInstance().getFactionManager().leave(player);
            player.sendMessage(left
                    ? "§5[Faction] §7Left §5" + f.getName() + "§7."
                    : "§c[Faction] Promote another leader before leaving.");
            return false;
        }
        return false;
    }

    private boolean handleRanksClick(int slot, Faction f) {
        boolean canManage = f.memberHasPermission(player.getUniqueId(), FactionPermission.MANAGE_RANKS);

        // Back button in perm editor
        if (slot == 0 && selectedRankId != null) {
            selectedRankId = null;
            return true;
        }

        if (selectedRankId == null) {
            // Rank list — clicking a rank opens its permission editor
            if (!canManage) return false;
            List<FactionRankDef> ranks = new ArrayList<>(f.getAllRanks());
            if (slot < ranks.size()) {
                FactionRankDef rank = ranks.get(slot);
                if (!rank.isLeaderRank()) { selectedRankId = rank.getRankId(); return true; }
            }
            return false;
        }

        // Permission editor — slots 9–(9+perms.length-1) are permission toggles
        FactionRankDef rank = f.getRank(selectedRankId);
        if (rank == null || rank.isLeaderRank() || !canManage) return false;

        FactionPermission[] perms = FactionPermission.values();
        int permIndex = slot - 9;
        if (permIndex >= 0 && permIndex < perms.length) {
            FactionPermission perm = perms[permIndex];
            boolean currently = rank.hasPermission(perm);
            Horizon.getInstance().getFactionManager()
                    .setRankPermission(f, player.getUniqueId(), rank.getRankId(), perm, !currently);
            return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private Faction faction() {
        return Horizon.getInstance().getFactionManager().getPlayerFaction(player.getUniqueId());
    }
}