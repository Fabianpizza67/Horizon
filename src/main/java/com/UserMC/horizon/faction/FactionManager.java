package com.usermc.horizon.faction;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.database.dao.FactionDAO;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Central manager for all player factions.
 *
 * In-memory state:
 *   factions       — all factions keyed by UUID
 *   memberIndex    — playerUUID → their faction (fast "which faction am I in?" lookup)
 *   pendingInvites — invited playerUUID → factionId (expires on relog or 5 minutes)
 *   pendingDiplomacy — canonical pair string → (proposingFactionId, proposed relation)
 *
 * All mutating operations call dao.saveFaction/saveMember/saveRelation immediately
 * (async writes). saveAll() on shutdown uses sync writes.
 */
public class FactionManager {

    /** Formation cost in Energy Credits. */
    public static final long FORMATION_COST = 500L;

    private final Horizon    plugin;
    private final FactionDAO dao;

    private final Map<UUID, Faction>   factions    = new LinkedHashMap<>();
    private final Map<UUID, UUID>      memberIndex = new HashMap<>(); // playerUUID → factionId

    /** Pending player invites: invitedPlayerUUID → factionId */
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    /**
     * Pending diplomacy proposals.
     * Key: canonical pair "smallerUUID:largerUUID"
     * Value: [proposingFactionId, FactionRelation]
     */
    private final Map<String, Object[]> pendingDiplomacy = new HashMap<>();

    public FactionManager(Horizon plugin) {
        this.plugin = plugin;
        this.dao    = new FactionDAO(plugin);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void loadAll() {
        List<Faction> loaded = dao.loadAll();
        for (Faction f : loaded) {
            factions.put(f.getFactionId(), f);
            f.getMembers().values().forEach(m -> memberIndex.put(m.getPlayerUUID(), f.getFactionId()));
        }
        plugin.getLogger().info("Loaded " + factions.size() + " faction(s).");
    }

    public void saveAll() { flushDirty(); }

    public void flushDirty() {
        for (Faction f : factions.values()) {
            if (f.isDirty()) dao.saveFactionSync(f);
        }
    }

    // -----------------------------------------------------------------------
    // Create / Disband
    // -----------------------------------------------------------------------

    /**
     * Create a new faction. Deducts FORMATION_COST from the player's balance.
     * Returns the new Faction, or null if the name is taken or they can't afford it.
     */
    public Faction create(Player player, String name) {
        if (getFactionByName(name) != null) return null;
        if (getPlayerFaction(player.getUniqueId()) != null) return null;
        if (!plugin.getEconomyManager().deduct(player, FORMATION_COST)) return null;

        UUID factionId = UUID.randomUUID();
        Faction faction = new Faction(factionId, name, "", player.getUniqueId(), 0L,
                System.currentTimeMillis());

        FactionMember leader = new FactionMember(player.getUniqueId(), factionId,
                FactionRank.LEADER, player.getName(), System.currentTimeMillis());
        faction.addMember(leader);

        factions.put(factionId, faction);
        memberIndex.put(player.getUniqueId(), factionId);

        dao.saveFaction(faction);
        dao.saveMember(leader);
        return faction;
    }

    /**
     * Disband a faction. Only the leader can do this.
     * Refunds 50% of the bank balance to the leader.
     */
    public void disband(Faction faction, Player leader) {
        // Cancel pending proposals involving this faction
        pendingDiplomacy.entrySet().removeIf(e ->
                e.getKey().contains(faction.getFactionId().toString()));
        pendingInvites.entrySet().removeIf(e ->
                e.getValue().equals(faction.getFactionId()));

        // Refund 50% of bank to leader
        long refund = faction.getBankBalance() / 2;
        if (refund > 0) {
            plugin.getEconomyManager().addBalance(leader, refund);
            leader.sendMessage("§e[Faction] §f" + refund + " EC §7refunded from the faction bank.");
        }

        // Remove all members from index
        faction.getMembers().keySet().forEach(memberIndex::remove);
        factions.remove(faction.getFactionId());
        dao.deleteFaction(faction.getFactionId());
    }

    // -----------------------------------------------------------------------
    // Invites
    // -----------------------------------------------------------------------

    /** Officer+ sends an invite to a player. Returns false if they're already in a faction. */
    public boolean invite(Faction faction, Player target) {
        if (getPlayerFaction(target.getUniqueId()) != null) return false;
        pendingInvites.put(target.getUniqueId(), faction.getFactionId());
        return true;
    }

    /** Invitee accepts. Returns the faction they joined, or null if no pending invite. */
    public Faction acceptInvite(Player player) {
        UUID factionId = pendingInvites.remove(player.getUniqueId());
        if (factionId == null) return null;
        Faction faction = factions.get(factionId);
        if (faction == null) return null;

        FactionMember member = new FactionMember(player.getUniqueId(), factionId,
                FactionRank.RECRUIT, player.getName(), System.currentTimeMillis());
        faction.addMember(member);
        memberIndex.put(player.getUniqueId(), factionId);

        dao.saveMember(member);
        return faction;
    }

    public boolean hasPendingInvite(UUID playerUUID) {
        return pendingInvites.containsKey(playerUUID);
    }

    public Faction getPendingInviteFaction(UUID playerUUID) {
        UUID fid = pendingInvites.get(playerUUID);
        return fid == null ? null : factions.get(fid);
    }

    public void declineInvite(UUID playerUUID) {
        pendingInvites.remove(playerUUID);
    }

    // -----------------------------------------------------------------------
    // Leave / Kick
    // -----------------------------------------------------------------------

    /** Player leaves their faction voluntarily. Leader must transfer first. */
    public boolean leave(Player player) {
        Faction faction = getPlayerFaction(player.getUniqueId());
        if (faction == null) return false;
        if (faction.isLeader(player.getUniqueId()) && faction.getMemberCount() > 1) return false;

        faction.removeMember(player.getUniqueId());
        memberIndex.remove(player.getUniqueId());
        dao.deleteMember(player.getUniqueId());

        if (faction.getMemberCount() == 0) {
            factions.remove(faction.getFactionId());
            dao.deleteFaction(faction.getFactionId());
        }
        return true;
    }

    /** Officer+ kicks another member. Cannot kick someone of equal or higher rank. */
    public boolean kick(Faction faction, FactionMember actor, UUID targetUUID) {
        FactionMember target = faction.getMember(targetUUID);
        if (target == null) return false;
        if (!actor.getRank().isAtLeast(FactionRank.OFFICER)) return false;
        if (target.getRank().getTier() >= actor.getRank().getTier()) return false;
        if (faction.isLeader(targetUUID)) return false;

        faction.removeMember(targetUUID);
        memberIndex.remove(targetUUID);
        dao.deleteMember(targetUUID);
        return true;
    }

    // -----------------------------------------------------------------------
    // Rank management
    // -----------------------------------------------------------------------

    /** Set a member's rank. Only leader can promote/demote to OFFICER. */
    public boolean setRank(Faction faction, FactionMember actor, UUID targetUUID, FactionRank newRank) {
        FactionMember target = faction.getMember(targetUUID);
        if (target == null) return false;
        if (newRank == FactionRank.LEADER) return false; // use transferLeadership instead
        if (!actor.getRank().isAtLeast(FactionRank.OFFICER)) return false;
        if (newRank == FactionRank.OFFICER && !actor.getRank().isAtLeast(FactionRank.LEADER)) return false;
        if (faction.isLeader(targetUUID)) return false;

        target.setRank(newRank);
        dao.saveMember(target);
        return true;
    }

    /** Leader transfers their LEADER rank to another member. */
    public boolean transferLeadership(Faction faction, Player currentLeader, UUID newLeaderUUID) {
        if (!faction.isLeader(currentLeader.getUniqueId())) return false;
        FactionMember newLeaderMember = faction.getMember(newLeaderUUID);
        FactionMember oldLeaderMember = faction.getMember(currentLeader.getUniqueId());
        if (newLeaderMember == null || oldLeaderMember == null) return false;

        newLeaderMember.setRank(FactionRank.LEADER);
        oldLeaderMember.setRank(FactionRank.OFFICER);
        faction.setLeaderUUID(newLeaderUUID);

        dao.saveMember(newLeaderMember);
        dao.saveMember(oldLeaderMember);
        dao.saveFaction(faction);
        return true;
    }

    // -----------------------------------------------------------------------
    // Bank
    // -----------------------------------------------------------------------

    public boolean depositToBank(Player player, Faction faction, long amount) {
        if (amount <= 0) return false;
        if (!plugin.getEconomyManager().deduct(player, amount)) return false;
        faction.depositBank(amount);
        dao.saveFaction(faction);
        return true;
    }

    public boolean withdrawFromBank(Faction faction, Player player, long amount) {
        FactionMember member = faction.getMember(player.getUniqueId());
        if (member == null || !member.getRank().isAtLeast(FactionRank.OFFICER)) return false;
        if (!faction.deductBank(amount)) return false;
        plugin.getEconomyManager().addBalance(player, amount);
        dao.saveFaction(faction);
        return true;
    }

    // -----------------------------------------------------------------------
    // Diplomacy
    // -----------------------------------------------------------------------

    /**
     * Propose a diplomatic relation from one faction to another.
     * AT_WAR takes effect immediately (unilateral).
     * ALLIED and TRADE_PARTNER require the other faction to also call propose() with the same relation.
     * NEUTRAL withdraws an existing relation (unilateral).
     */
    public boolean proposeRelation(Faction proposer, Faction target, FactionRelation relation) {
        if (proposer.getFactionId().equals(target.getFactionId())) return false;

        // Unilateral: war and returning to neutral
        if (relation == FactionRelation.AT_WAR || relation == FactionRelation.NEUTRAL) {
            setRelationBothSides(proposer, target, relation);
            return true;
        }

        // Mutual: check if the other side already proposed the same relation
        String key = pairKey(proposer.getFactionId(), target.getFactionId());
        Object[] existing = pendingDiplomacy.get(key);

        if (existing != null
                && existing[0].equals(target.getFactionId())
                && existing[1] == relation) {
            // Other side proposed first — confirm it
            pendingDiplomacy.remove(key);
            setRelationBothSides(proposer, target, relation);
            return true;
        }

        // Queue our proposal for the other side to accept
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

    /** Canonical pair key — always smaller UUID first so A→B and B→A resolve the same key. */
    private String pairKey(UUID a, UUID b) {
        return a.compareTo(b) <= 0
                ? a + ":" + b
                : b + ":" + a;
    }

    // -----------------------------------------------------------------------
    // Lookups
    // -----------------------------------------------------------------------

    public Faction getById(UUID factionId) { return factions.get(factionId); }

    public Faction getFactionByName(String name) {
        for (Faction f : factions.values())
            if (f.getName().equalsIgnoreCase(name)) return f;
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