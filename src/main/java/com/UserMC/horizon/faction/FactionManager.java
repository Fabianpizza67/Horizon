package com.usermc.horizon.faction;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.FactionDAO;
import com.usermc.horizon.database.dao.FactionRankDAO;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Central manager for all player factions.
 *
 * Every faction always has exactly two protected ranks (created automatically
 * in create()): a LEADER rank (always full permissions, renamable, holdable
 * by multiple players, at least one member must always hold it) and a
 * DEFAULT rank (zero permissions by default but editable, renamable,
 * auto-assigned to new members unless the leader configures a different
 * "new member rank").
 *
 * All permission checks go through FactionRankDef#hasPermission rather than
 * a hardcoded tier comparison — see FactionPermission for the full list.
 */
public class FactionManager {

    public static final long FORMATION_COST = 500L;

    private final Horizon       plugin;
    private final FactionDAO    dao;
    private final FactionRankDAO rankDao;

    private final Map<UUID, Faction> factions    = new LinkedHashMap<>();
    private final Map<UUID, UUID>    memberIndex = new HashMap<>(); // playerUUID → factionId

    /** Pending player invites: invitedPlayerUUID → factionId */
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    /** Pending diplomacy proposals. Key: canonical pair "smallerUUID:largerUUID". Value: [proposingFactionId, FactionRelation] */
    private final Map<String, Object[]> pendingDiplomacy = new HashMap<>();

    public FactionManager(Horizon plugin) {
        this.plugin  = plugin;
        this.dao     = new FactionDAO(plugin);
        this.rankDao = new FactionRankDAO(plugin);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        Map<UUID, List<FactionRankDef>> ranksByFaction = rankDao.loadAllByFaction();

        for (FactionDAO.FactionRow row : dao.loadFactionRows()) {
            Faction f = new Faction(row.factionId(), row.name(), row.description(),
                    row.leaderUUID(), row.bankBalance(), row.createdAt());
            for (FactionRankDef rank : ranksByFaction.getOrDefault(row.factionId(), List.of())) {
                f.addRank(rank);
            }
            if (row.newMemberRankId() != null) f.setNewMemberRankId(row.newMemberRankId());
            f.clearDirty();
            factions.put(f.getFactionId(), f);
        }

        for (FactionDAO.MemberRow row : dao.loadMemberRows()) {
            Faction f = factions.get(row.factionId());
            if (f == null) continue;
            FactionMember m = new FactionMember(row.playerUUID(), row.factionId(), row.rankId(),
                    row.playerName(), row.joinedAt());
            m.clearDirty();
            f.addMember(m);
            memberIndex.put(row.playerUUID(), row.factionId());
        }

        for (Object[] rel : dao.loadRelationRows()) {
            UUID idA = (UUID) rel[0];
            UUID idB = (UUID) rel[1];
            FactionRelation r = (FactionRelation) rel[2];
            Faction fA = factions.get(idA);
            Faction fB = factions.get(idB);
            if (fA != null) { fA.setRelation(idB, r); fA.clearDirty(); }
            if (fB != null) { fB.setRelation(idA, r); fB.clearDirty(); }
        }

        plugin.getLogger().info("Loaded " + factions.size() + " faction(s).");
    }

    public void saveAll() { flushDirty(); }

    public void flushDirty() {
        for (Faction f : factions.values()) {
            if (f.isDirty()) dao.saveFactionSync(f);
            for (FactionRankDef rank : f.getAllRanks()) {
                if (rank.isDirty()) rankDao.saveSync(rank);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Create / Disband
    // -----------------------------------------------------------------------

    /**
     * Create a new faction. Automatically creates the protected LEADER and
     * DEFAULT ranks and assigns the founder to LEADER.
     */
    public Faction create(Player player, String name) {
        if (getFactionByName(name) != null) return null;
        if (getPlayerFaction(player.getUniqueId()) != null) return null;
        if (!plugin.getEconomyManager().deduct(player, FORMATION_COST)) return null;

        UUID factionId = UUID.randomUUID();
        Faction faction = new Faction(factionId, name, "", player.getUniqueId(), 0L,
                System.currentTimeMillis());

        FactionRankDef leaderRank = new FactionRankDef(UUID.randomUUID(), factionId,
                "Leader", FactionPermission.allPermissions(), 0, true, false);
        FactionRankDef defaultRank = new FactionRankDef(UUID.randomUUID(), factionId,
                "Recruit", FactionPermission.noPermissions(), 99, false, true);

        faction.addRank(leaderRank);
        faction.addRank(defaultRank);
        faction.setNewMemberRankId(defaultRank.getRankId());

        FactionMember founder = new FactionMember(player.getUniqueId(), factionId,
                leaderRank.getRankId(), player.getName(), System.currentTimeMillis());
        faction.addMember(founder);

        factions.put(factionId, faction);
        memberIndex.put(player.getUniqueId(), factionId);

        dao.saveFaction(faction);
        dao.saveMember(founder);
        rankDao.save(leaderRank);
        rankDao.save(defaultRank);
        return faction;
    }

    public void disband(Faction faction, Player leader) {
        pendingDiplomacy.entrySet().removeIf(e -> e.getKey().contains(faction.getFactionId().toString()));
        pendingInvites.entrySet().removeIf(e -> e.getValue().equals(faction.getFactionId()));

        long refund = faction.getBankBalance() / 2;
        if (refund > 0) {
            plugin.getEconomyManager().addBalance(leader, refund);
            leader.sendMessage("§e[Faction] §f" + refund + " EC §7refunded from the faction bank.");
        }

        faction.getMembers().keySet().forEach(memberIndex::remove);
        factions.remove(faction.getFactionId());
        dao.deleteFaction(faction.getFactionId());
    }

    // -----------------------------------------------------------------------
    // Invites
    // -----------------------------------------------------------------------

    public boolean invite(Faction faction, FactionMember actor, Player target) {
        if (!actor.equals(faction.getMember(actor.getPlayerUUID()))) return false;
        if (!faction.memberHasPermission(actor.getPlayerUUID(), FactionPermission.INVITE_MEMBERS)) return false;
        if (getPlayerFaction(target.getUniqueId()) != null) return false;
        pendingInvites.put(target.getUniqueId(), faction.getFactionId());
        return true;
    }

    public Faction acceptInvite(Player player) {
        UUID factionId = pendingInvites.remove(player.getUniqueId());
        if (factionId == null) return null;
        Faction faction = factions.get(factionId);
        if (faction == null) return null;

        UUID newMemberRank = faction.getNewMemberRankId();
        if (newMemberRank == null) return null; // shouldn't happen — every faction has a default rank

        FactionMember member = new FactionMember(player.getUniqueId(), factionId, newMemberRank,
                player.getName(), System.currentTimeMillis());
        faction.addMember(member);
        memberIndex.put(player.getUniqueId(), factionId);
        dao.saveMember(member);
        return faction;
    }

    public boolean hasPendingInvite(UUID playerUUID) { return pendingInvites.containsKey(playerUUID); }

    public Faction getPendingInviteFaction(UUID playerUUID) {
        UUID fid = pendingInvites.get(playerUUID);
        return fid == null ? null : factions.get(fid);
    }

    public void declineInvite(UUID playerUUID) { pendingInvites.remove(playerUUID); }

    // -----------------------------------------------------------------------
    // Leave / Kick
    // -----------------------------------------------------------------------

    /** Player leaves voluntarily. A sole remaining leader cannot leave — promote someone else first. */
    public boolean leave(Player player) {
        Faction faction = getPlayerFaction(player.getUniqueId());
        if (faction == null) return false;
        if (faction.isLeader(player.getUniqueId()) && faction.countLeaders() <= 1
                && faction.getMemberCount() > 1) return false;

        faction.removeMember(player.getUniqueId());
        memberIndex.remove(player.getUniqueId());
        dao.deleteMember(player.getUniqueId());

        if (faction.getMemberCount() == 0) {
            factions.remove(faction.getFactionId());
            dao.deleteFaction(faction.getFactionId());
        }
        return true;
    }

    /** Requires KICK_MEMBERS permission. Cannot kick a leader rank holder. */
    public boolean kick(Faction faction, UUID actorUUID, UUID targetUUID) {
        if (!faction.memberHasPermission(actorUUID, FactionPermission.KICK_MEMBERS)) return false;
        FactionMember target = faction.getMember(targetUUID);
        if (target == null) return false;
        if (faction.isLeader(targetUUID)) return false;

        faction.removeMember(targetUUID);
        memberIndex.remove(targetUUID);
        dao.deleteMember(targetUUID);
        return true;
    }

    // -----------------------------------------------------------------------
    // Rank assignment (non-leader ranks only — see promote/demoteLeader for that)
    // -----------------------------------------------------------------------

    /** Requires MANAGE_RANKS. Cannot be used to assign the LEADER rank — use promoteToLeader instead. */
    public boolean assignRank(Faction faction, UUID actorUUID, UUID targetUUID, UUID rankId) {
        if (!faction.memberHasPermission(actorUUID, FactionPermission.MANAGE_RANKS)) return false;
        FactionMember target = faction.getMember(targetUUID);
        FactionRankDef rank  = faction.getRank(rankId);
        if (target == null || rank == null || rank.isLeaderRank()) return false;
        if (faction.isLeader(targetUUID)) return false; // demote via demoteFromLeader first

        target.setRankId(rankId);
        dao.saveMember(target);
        return true;
    }

    /** Requires MANAGE_RANKS. Grants the target the LEADER rank IN ADDITION to whatever they have — multiple leaders allowed. */
    public boolean promoteToLeader(Faction faction, UUID actorUUID, UUID targetUUID) {
        if (!faction.memberHasPermission(actorUUID, FactionPermission.MANAGE_RANKS)) return false;
        FactionMember target = faction.getMember(targetUUID);
        FactionRankDef leaderRank = faction.getLeaderRank();
        if (target == null || leaderRank == null) return false;

        target.setRankId(leaderRank.getRankId());
        dao.saveMember(target);
        return true;
    }

    /** Requires MANAGE_RANKS. Demotes a leader to the faction's default rank. Refuses if they're the last leader. */
    public boolean demoteFromLeader(Faction faction, UUID actorUUID, UUID targetUUID) {
        if (!faction.memberHasPermission(actorUUID, FactionPermission.MANAGE_RANKS)) return false;
        if (!faction.isLeader(targetUUID)) return false;
        if (faction.countLeaders() <= 1) return false; // never leave a faction leaderless

        FactionMember target = faction.getMember(targetUUID);
        UUID defaultRankId = faction.getNewMemberRankId();
        if (target == null || defaultRankId == null) return false;

        target.setRankId(defaultRankId);
        dao.saveMember(target);
        return true;
    }

    // -----------------------------------------------------------------------
    // Rank management — create/edit/delete custom ranks
    // -----------------------------------------------------------------------

    /** Requires MANAGE_RANKS. Creates a new custom rank with zero permissions, placed at the end of the hierarchy. */
    public FactionRankDef createRank(Faction faction, UUID actorUUID, String name) {
        if (!faction.memberHasPermission(actorUUID, FactionPermission.MANAGE_RANKS)) return null;
        if (faction.getRankByName(name) != null) return null;

        int position = faction.getAllRanks().size();
        FactionRankDef rank = new FactionRankDef(UUID.randomUUID(), faction.getFactionId(),
                name, FactionPermission.noPermissions(), position, false, false);
        faction.addRank(rank);
        rankDao.save(rank);
        return rank;
    }

    /** Requires MANAGE_RANKS. Refuses to delete protected ranks. Reassigns affected members to the default rank. */
    public boolean deleteRank(Faction faction, UUID actorUUID, UUID rankId) {
        if (!faction.memberHasPermission(actorUUID, FactionPermission.MANAGE_RANKS)) return false;
        FactionRankDef rank = faction.getRank(rankId);
        if (rank == null || rank.isProtected()) return false;

        UUID fallbackRankId = faction.getNewMemberRankId();
        for (FactionMember m : faction.getMembers().values()) {
            if (m.getRankId().equals(rankId)) {
                m.setRankId(fallbackRankId);
                dao.saveMember(m);
            }
        }

        faction.removeRank(rankId);
        rankDao.delete(rankId);
        return true;
    }

    /** Requires MANAGE_RANKS. Works on protected ranks too (renaming Leader/Default is always allowed). */
    public boolean renameRank(Faction faction, UUID actorUUID, UUID rankId, String newName) {
        if (!faction.memberHasPermission(actorUUID, FactionPermission.MANAGE_RANKS)) return false;
        FactionRankDef rank = faction.getRank(rankId);
        if (rank == null) return false;
        rank.setName(newName);
        rankDao.save(rank);
        return true;
    }

    /** Requires MANAGE_RANKS. No-op on the LEADER rank (always all-permissions). */
    public boolean setRankPermission(Faction faction, UUID actorUUID, UUID rankId,
                                     FactionPermission perm, boolean granted) {
        if (!faction.memberHasPermission(actorUUID, FactionPermission.MANAGE_RANKS)) return false;
        FactionRankDef rank = faction.getRank(rankId);
        if (rank == null || rank.isLeaderRank()) return false;
        rank.setPermission(perm, granted);
        rankDao.save(rank);
        return true;
    }

    /** Requires MANAGE_RANKS. Changes which rank new members receive on join (can't be the LEADER rank). */
    public boolean setNewMemberRank(Faction faction, UUID actorUUID, UUID rankId) {
        if (!faction.memberHasPermission(actorUUID, FactionPermission.MANAGE_RANKS)) return false;
        FactionRankDef rank = faction.getRank(rankId);
        if (rank == null || rank.isLeaderRank()) return false;
        faction.setNewMemberRankId(rankId);
        dao.saveFaction(faction);
        return true;
    }

    // -----------------------------------------------------------------------
    // Bank
    // -----------------------------------------------------------------------

    public boolean depositToBank(Player player, Faction faction, long amount) {
        if (amount <= 0) return false;
        if (!faction.memberHasPermission(player.getUniqueId(), FactionPermission.BANK_DEPOSIT)) return false;
        if (!plugin.getEconomyManager().deduct(player, amount)) return false;
        faction.depositBank(amount);
        dao.saveFaction(faction);
        return true;
    }

    public boolean withdrawFromBank(Faction faction, Player player, long amount) {
        if (!faction.memberHasPermission(player.getUniqueId(), FactionPermission.BANK_WITHDRAW)) return false;
        if (!faction.deductBank(amount)) return false;
        plugin.getEconomyManager().addBalance(player, amount);
        dao.saveFaction(faction);
        return true;
    }

    // -----------------------------------------------------------------------
    // Diplomacy
    // -----------------------------------------------------------------------

    public boolean proposeRelation(Faction proposer, UUID actorUUID, Faction target, FactionRelation relation) {
        if (proposer.getFactionId().equals(target.getFactionId())) return false;

        FactionPermission required = switch (relation) {
            case ALLIED        -> FactionPermission.PROPOSE_ALLIANCE;
            case TRADE_PARTNER -> FactionPermission.PROPOSE_TRADE;
            case AT_WAR        -> FactionPermission.DECLARE_WAR;
            case NEUTRAL       -> FactionPermission.PROPOSE_PEACE;
        };
        if (!proposer.memberHasPermission(actorUUID, required)) return false;

        if (relation == FactionRelation.AT_WAR || relation == FactionRelation.NEUTRAL) {
            setRelationBothSides(proposer, target, relation);
            return true;
        }

        String key = pairKey(proposer.getFactionId(), target.getFactionId());
        Object[] existing = pendingDiplomacy.get(key);

        if (existing != null && existing[0].equals(target.getFactionId()) && existing[1] == relation) {
            pendingDiplomacy.remove(key);
            setRelationBothSides(proposer, target, relation);
            return true;
        }

        pendingDiplomacy.put(key, new Object[]{proposer.getFactionId(), relation});
        return true;
    }

    private void setRelationBothSides(Faction a, Faction b, FactionRelation relation) {
        a.setRelation(b.getFactionId(), relation);
        b.setRelation(a.getFactionId(), relation);
        dao.saveFaction(a);
        dao.saveFaction(b);
        dao.saveRelation(a.getFactionId(), b.getFactionId(), relation);
    }

    public boolean hasPendingProposal(UUID proposerFaction, UUID targetFaction) {
        Object[] p = pendingDiplomacy.get(pairKey(proposerFaction, targetFaction));
        return p != null && p[0].equals(proposerFaction);
    }

    public FactionRelation getPendingProposalRelation(UUID proposerFaction, UUID targetFaction) {
        Object[] p = pendingDiplomacy.get(pairKey(proposerFaction, targetFaction));
        return (p != null) ? (FactionRelation) p[1] : null;
    }

    private String pairKey(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }

    // -----------------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------------

    public Faction getById(UUID factionId) { return factions.get(factionId); }

    public Faction getFactionByName(String name) {
        for (Faction f : factions.values()) if (f.getName().equalsIgnoreCase(name)) return f;
        return null;
    }

    public Faction getPlayerFaction(UUID playerUUID) {
        UUID fid = memberIndex.get(playerUUID);
        return fid == null ? null : factions.get(fid);
    }

    public FactionMember getMembership(UUID playerUUID) {
        Faction f = getPlayerFaction(playerUUID);
        return f == null ? null : f.getMember(playerUUID);
    }

    public Collection<Faction> getAllFactions() { return Collections.unmodifiableCollection(factions.values()); }
}