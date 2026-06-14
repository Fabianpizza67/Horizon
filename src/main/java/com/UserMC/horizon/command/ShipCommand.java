package com.usermc.horizon.command;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.crew.CrewMember;
import com.usermc.horizon.crew.CrewRole;
import com.usermc.horizon.ship.*;
import com.usermc.horizon.warp.WarpBeacon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * /ship command — player-facing subset is intentionally minimal.
 *
 * PLAYERS can use:
 *   register, board, disembark, list, info, delete
 *   warp abort (emergency only)
 *
 * ADMINS (horizon.admin) additionally:
 *   warp admin create/delete
 *   crew hire/fire/list/info
 *   scan (override rescan without walking to Ship Core)
 *
 * Everything else — movement, fueling, warp destination, autopilot —
 * is handled physically through station block GUIs.
 */
public class ShipCommand implements CommandExecutor, TabCompleter {

    private static final String PFX = "§b[Horizon] §r";
    private final Horizon plugin;

    public ShipCommand(Horizon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use ship commands."); return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        return switch (args[0].toLowerCase()) {
            case "help"      -> { sendHelp(player); yield true; }
            case "register"  -> cmdRegister(player, args);
            case "board"     -> cmdBoard(player, args);
            case "disembark" -> cmdDisembark(player, args);
            case "list"      -> cmdList(player, args);
            case "info"      -> cmdInfo(player, args);
            case "delete"    -> cmdDelete(player, args);
            case "fuel"      -> cmdFuelGive(player, args);
            case "scan"      -> cmdScan(player, args);   // admin override
            case "crew"      -> cmdCrew(player, args);   // admin only
            case "save"      -> cmdSave(player, args);   // admin only
            default -> {
                player.sendMessage(PFX + "§cUnknown subcommand. Use §f/ship help§c.");
                yield true;
            }
        };
    }

    // -----------------------------------------------------------------------
    // Help
    // -----------------------------------------------------------------------

    private void sendHelp(Player p) {
        p.sendMessage("§b§l╔══ Horizon ══╗");
        p.sendMessage("§b/ship register <name> §7— Register ship (look at Lodestone)");
        p.sendMessage("§b/ship board <name>    §7— Teleport to a ship");
        p.sendMessage("§b/ship disembark       §7— Leave current ship");
        p.sendMessage("§b/ship list            §7— Your ships");
        p.sendMessage("§b/ship info [name]     §7— Ship details");
        p.sendMessage("§b/ship delete <name>   §7— Delete a ship");
        p.sendMessage("§b/ship warp abort      §7— Emergency warp abort");
        p.sendMessage("§7Move, fuel and navigate using the §bHelm§7, §bEngineering§7 and §bNavigation§7 consoles.");
        if (p.hasPermission("horizon.admin")) {
            p.sendMessage("§8Admin: /ship scan | /ship warp admin | /ship crew | /ship fuel give | /ship save");
        }
    }

    // -----------------------------------------------------------------------
    // Register
    // -----------------------------------------------------------------------

    private boolean cmdRegister(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(PFX + "§cUsage: /ship register <name>"); return true; }
        String name = join(args, 1);
        if (name.length() > 64) { player.sendMessage(PFX + "§cName too long (max 64)."); return true; }

        for (Ship s : plugin.getShipManager().getByOwner(player.getUniqueId())) {
            if (s.getName().equalsIgnoreCase(name)) {
                player.sendMessage(PFX + "§cYou already have a ship named §f" + name); return true;
            }
        }

        var target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != Material.LODESTONE) {
            player.sendMessage(PFX + "§cLook directly at a §fLodestone§c to register your Ship Core.");
            return true;
        }
        if (plugin.getShipManager().getAtCoreLocation(target.getLocation()) != null) {
            player.sendMessage(PFX + "§cThis Lodestone is already a Ship Core."); return true;
        }

        Ship ship = new Ship(UUID.randomUUID(), name, player.getUniqueId(),
                ShipClass.SHUTTLE, target.getLocation(), 0f);

        int maxBlocks = player.hasPermission("horizon.admin")
                ? plugin.getHorizonConfig().getAdminLimit()
                : plugin.getHorizonConfig().getPlayerHardLimit();

        player.sendMessage(PFX + "§eScanning §f" + name + "§e...");
        plugin.getShipManager().getScanner().scan(target.getLocation(), maxBlocks,
                structure -> {
                    int count = structure.getBlockCount();
                    ShipClass sc = ShipClass.fromBlockCount(count, player.hasPermission("horizon.admin"));
                    ship.setShipClass(sc);
                    ship.setStructure(structure);
                    plugin.getShipManager().register(ship);
                    player.sendMessage(PFX + "§a§f" + name + " §aregistered ("
                            + sc.getDisplayName() + ", " + count + " blocks).");
                    player.sendMessage("§7Craft and place §bHelm§7, §bNavigation§7 and §bEngineering§7 consoles to operate your ship.");
                },
                err -> player.sendMessage(PFX + "§cScan failed: " + err));
        return true;
    }

    // -----------------------------------------------------------------------
    // Board / Disembark
    // -----------------------------------------------------------------------

    private boolean cmdBoard(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(PFX + "§cUsage: /ship board <name>"); return true; }
        Ship ship = plugin.getShipManager().getByName(join(args, 1));
        if (ship == null) { player.sendMessage(PFX + "§cNo ship named §f" + join(args, 1)); return true; }
        Location dest = ship.getCoreLocation().add(0, 1, 0);
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());
        player.teleport(dest);
        ship.addPassenger(player.getUniqueId());
        player.sendMessage(PFX + "§aBoarded §f" + ship.getName() + "§a.");
        return true;
    }

    private boolean cmdDisembark(Player player, String[] args) {
        boolean found = false;
        for (Ship s : plugin.getShipManager().getAllShips()) {
            if (s.isPassenger(player.getUniqueId())) {
                s.removePassenger(player.getUniqueId());
                player.sendMessage(PFX + "§aDisembarked from §f" + s.getName() + "§a.");
                found = true;
            }
        }
        if (!found) player.sendMessage(PFX + "§cNot aboard any ship.");
        return true;
    }

    // -----------------------------------------------------------------------
    // List / Info / Delete
    // -----------------------------------------------------------------------

    private boolean cmdList(Player player, String[] args) {
        List<Ship> owned = plugin.getShipManager().getByOwner(player.getUniqueId());
        if (owned.isEmpty()) {
            player.sendMessage(PFX + "No ships registered. Build one, place a Lodestone and use §f/ship register§r.");
            return true;
        }
        player.sendMessage("§b§lYour ships §8(" + owned.size() + "):");
        for (Ship s : owned) {
            int stations = plugin.getStationManager().getForShip(s.getShipId()).size();
            player.sendMessage("  §b" + s.getName() + " §8— §f" + s.getShipClass().getDisplayName()
                    + " §8— fuel §f" + s.getFuelLevel()
                    + " §8— §f" + stations + " §8stations");
        }
        return true;
    }

    private boolean cmdInfo(Player player, String[] args) {
        Ship ship;
        if (args.length >= 2) {
            ship = plugin.getShipManager().getByName(join(args, 1));
            if (ship == null) { player.sendMessage(PFX + "§cNo ship named §f" + join(args, 1)); return true; }
        } else {
            ship = currentShip(player);
            if (ship == null) { player.sendMessage(PFX + "§cNot aboard a ship. Specify a name."); return true; }
        }
        Location c = ship.getCoreLocation();
        player.sendMessage("§b§l[ " + ship.getName() + " ]");
        player.sendMessage("§7Class:  §f" + ship.getShipClass().getDisplayName() + "  §7Status: §f" + ship.getStatus());
        player.sendMessage("§7Core:   §f" + c.getBlockX() + " " + c.getBlockY() + " " + c.getBlockZ());
        player.sendMessage("§7Fuel:   " + plugin.getFuelManager().fuelBar(ship));
        if (ship.getStructure() != null)
            player.sendMessage("§7Blocks: §f" + ship.getStructure().getBlockCount()
                    + "§8/§f" + ship.getShipClass().getHardLimit());
        int stationCount = plugin.getStationManager().getForShip(ship.getShipId()).size();
        int crewCount    = plugin.getCrewManager().getCrewForShip(ship.getShipId()).size();
        int maxCrew      = ship.getShipClass().getMaxCrewSlots()
                + plugin.getRankManager().getBonusCrewSlots(ship.getOwnerUUID());
        player.sendMessage("§7Stations: §f" + stationCount + "  §7Crew: §f" + crewCount
                + "§8/§f" + maxCrew);
        return true;
    }

    private boolean cmdDelete(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(PFX + "§cUsage: /ship delete <name>"); return true; }
        Ship ship = plugin.getShipManager().getByName(join(args, 1));
        if (ship == null) { player.sendMessage(PFX + "§cNo ship named §f" + join(args, 1)); return true; }
        if (!ship.isOwner(player) && !player.hasPermission("horizon.admin")) {
            player.sendMessage(PFX + "§cNot your ship."); return true; }
        if (ship.isProcessing()) {
            player.sendMessage(PFX + "§cShip is moving."); return true; }
        plugin.getShipManager().delete(ship);
        player.sendMessage(PFX + "§aShip §f" + ship.getName() + " §adeleted.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Fuel give (admin)
    // -----------------------------------------------------------------------

    private boolean cmdFuelGive(Player player, String[] args) {
        // /ship fuel give <player> <crystals>
        if (!player.hasPermission("horizon.admin")) {
            player.sendMessage(PFX + "§cNo permission."); return true; }
        if (args.length < 4) {
            player.sendMessage(PFX + "§cUsage: /ship fuel give <player> <crystals>"); return true; }
        Player target = plugin.getServer().getPlayer(args[2]);
        if (target == null) {
            player.sendMessage(PFX + "§cPlayer §f" + args[2] + " §cnot online."); return true; }
        int amount;
        try { amount = Math.max(1, Integer.parseInt(args[3])); }
        catch (NumberFormatException e) {
            player.sendMessage(PFX + "§cAmount must be a number."); return true; }
        target.getInventory().addItem(com.usermc.horizon.fuel.FuelItem.create(amount));
        target.sendMessage(PFX + "§aYou received §f" + amount + " §aDilithium Crystal(s).");
        player.sendMessage(PFX + "§aGave §f" + amount + " §aDilithium Crystal(s) to §f" + target.getName() + "§a.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Warp (emergency abort + admin beacon management)
    // -----------------------------------------------------------------------

    private boolean cmdWarp(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PFX + "§7Use the §bNavigation Console §7on your ship to warp.");
            if (player.hasPermission("horizon.admin"))
                player.sendMessage(PFX + "§8Admin: /ship warp admin create/delete <name>");
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "abort" -> {
                Ship ship = currentShip(player);
                if (ship == null) { player.sendMessage(PFX + "§cNot on a ship."); yield true; }
                if (!plugin.getWarpManager().isCharging(ship)) {
                    player.sendMessage(PFX + "§eWarp drive is not charging."); yield true; }
                plugin.getWarpManager().abortWarp(ship);
                yield true;
            }
            case "admin" -> cmdWarpAdmin(player, args);
            default -> {
                player.sendMessage(PFX + "§7Use the §bNavigation Console §7to warp.");
                yield true;
            }
        };
    }

    private boolean cmdWarpAdmin(Player player, String[] args) {
        if (!player.hasPermission("horizon.admin")) {
            player.sendMessage(PFX + "§cNo permission."); return true; }
        if (args.length < 3) {
            player.sendMessage("§b/ship warp admin create <name> [desc]");
            player.sendMessage("§b/ship warp admin delete <name>");
            player.sendMessage("§b/ship warp admin list");
            return true;
        }
        return switch (args[2].toLowerCase()) {
            case "create" -> {
                if (args.length < 4) { player.sendMessage(PFX + "§cUsage: /ship warp admin create <name> [desc]"); yield true; }
                String desc = args.length >= 5 ? join(args, 4) : "";
                WarpBeacon b = new WarpBeacon(UUID.randomUUID(), args[3], player.getLocation(), desc, false);
                plugin.getWarpManager().register(b, true);
                player.sendMessage(PFX + "§aBeacon §f" + args[3] + " §acreated here.");
                yield true;
            }
            case "delete" -> {
                if (args.length < 4) { player.sendMessage(PFX + "§cUsage: /ship warp admin delete <name>"); yield true; }
                String name = join(args, 3);
                player.sendMessage(plugin.getWarpManager().deleteBeacon(name)
                        ? PFX + "§aBeacon §f" + name + " §adeleted."
                        : PFX + "§cNo beacon named §f" + name);
                yield true;
            }
            case "list" -> {
                var all = plugin.getWarpManager().getAllBeacons();
                if (all.isEmpty()) { player.sendMessage(PFX + "No beacons exist."); yield true; }
                player.sendMessage("§b§lWarp Beacons §8(" + all.size() + "):");
                for (WarpBeacon b : all) {
                    Location l = b.getLocation();
                    player.sendMessage("  §b" + b.getName() + " §8@ §f"
                            + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                            + (b.getDescription().isBlank() ? "" : " §8— §7" + b.getDescription()));
                }
                yield true;
            }
            default -> { player.sendMessage(PFX + "§cUnknown admin subcommand."); yield true; }
        };
    }

    // -----------------------------------------------------------------------
    // Scan (admin override)
    // -----------------------------------------------------------------------

    private boolean cmdScan(Player player, String[] args) {
        if (!player.hasPermission("horizon.admin")) {
            player.sendMessage(PFX + "§7Rescan your ship by §bShift+right-clicking§7 the Ship Core (Lodestone).");
            return true;
        }
        Ship ship = currentShip(player);
        if (ship == null) { player.sendMessage(PFX + "§cNot on a ship."); return true; }
        player.sendMessage(PFX + "§eRescanning §f" + ship.getName() + "§e...");
        plugin.getShipManager().getScanner().scan(ship.getCoreLocation(),
                plugin.getHorizonConfig().getAdminLimit(),
                structure -> {
                    ShipClass sc = ShipClass.fromBlockCount(structure.getBlockCount(), true);
                    ship.setShipClass(sc); ship.setStructure(structure);
                    player.sendMessage(PFX + "§aScan complete — §f"
                            + structure.getBlockCount() + " §ablocks.");
                },
                err -> player.sendMessage(PFX + "§cScan failed: " + err));
        return true;
    }

    // -----------------------------------------------------------------------
    // Save (admin only) — manual full sync flush before a planned restart
    // -----------------------------------------------------------------------

    private boolean cmdSave(Player player, String[] args) {
        if (!player.hasPermission("horizon.admin")) {
            player.sendMessage(PFX + "§cNo permission."); return true; }
        player.sendMessage(PFX + "§eFlushing all ship, station, crew, rank and economy data...");
        plugin.forceSaveAll();
        player.sendMessage(PFX + "§aDone. Safe to restart.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Crew (admin only)
    // -----------------------------------------------------------------------

    private boolean cmdCrew(Player player, String[] args) {
        if (!player.hasPermission("horizon.admin")) {
            player.sendMessage(PFX + "§cCrew management requires admin permission."); return true; }
        if (args.length < 2) { sendCrewHelp(player); return true; }
        return switch (args[1].toLowerCase()) {
            case "hire"  -> cmdCrewHire(player, args);
            case "fire"  -> cmdCrewFire(player, args);
            case "list"  -> cmdCrewList(player, args);
            case "info"  -> cmdCrewInfo(player, args);
            default -> { sendCrewHelp(player); yield true; }
        };
    }

    private void sendCrewHelp(Player p) {
        p.sendMessage("§8Admin crew commands:");
        p.sendMessage("§b/ship crew hire <name> <species> <role> [skill]");
        p.sendMessage("§b/ship crew fire <name>");
        p.sendMessage("§b/ship crew list");
        p.sendMessage("§b/ship crew info <name>");
    }

    private boolean cmdCrewHire(Player player, String[] args) {
        if (args.length < 5) { player.sendMessage(PFX + "§cUsage: /ship crew hire <name> <species> <role> [skill 1-10]"); return true; }
        Ship ship = currentShip(player);
        if (ship == null) { player.sendMessage(PFX + "§cNot on a ship."); return true; }
        CrewRole role = CrewRole.fromString(args[4]);
        if (role == null) {
            player.sendMessage(PFX + "§cInvalid role. Options: "
                    + String.join(", ", Arrays.stream(CrewRole.values()).map(CrewRole::name).toList()));
            return true;
        }
        int skill = args.length >= 6 ? Math.clamp(parseInt(args[5], 1), 1, 10) : 1;
        int salary = 100 + (skill - 1) * 50;
        try {
            CrewMember cm = plugin.getCrewManager().hire(ship, args[2], args[3], role, skill, salary,
                    ship.getCoreLocation().add(0, 1, 0));
            player.sendMessage(PFX + "§aHired §f" + cm.getName() + " §a[" + args[3] + "] as "
                    + role.getDisplayName() + " (skill " + skill + ").");
        } catch (IllegalStateException e) { player.sendMessage(PFX + "§c" + e.getMessage()); }
        return true;
    }

    private boolean cmdCrewFire(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage(PFX + "§cUsage: /ship crew fire <name>"); return true; }
        Ship ship = currentShip(player);
        if (ship == null) { player.sendMessage(PFX + "§cNot on a ship."); return true; }
        CrewMember cm = plugin.getCrewManager().getByName(ship.getShipId(), args[2]);
        if (cm == null) { player.sendMessage(PFX + "§cNo crew named §f" + args[2]); return true; }
        plugin.getCrewManager().fire(cm, false);
        player.sendMessage(PFX + "§f" + cm.getName() + " §adismissed.");
        return true;
    }

    private boolean cmdCrewList(Player player, String[] args) {
        Ship ship = currentShip(player);
        if (ship == null) { player.sendMessage(PFX + "§cNot on a ship."); return true; }
        var crew = plugin.getCrewManager().getCrewForShip(ship.getShipId());
        int maxCrew = ship.getShipClass().getMaxCrewSlots()
                + plugin.getRankManager().getBonusCrewSlots(ship.getOwnerUUID());
        player.sendMessage("§b§lCrew — " + ship.getName() + " §8(" + crew.size()
                + "/" + maxCrew + ")");
        if (crew.isEmpty()) { player.sendMessage("  §7None."); return true; }
        for (CrewMember cm : crew)
            player.sendMessage("  " + cm.moraleColour() + cm.getName()
                    + " §8[§7" + cm.getSpecies() + "§8] §8— §f" + cm.getRole().getDisplayName()
                    + " §8Sk." + cm.getSkillLevel()
                    + " Mo." + String.format("%.0f", cm.getMorale()) + "%");
        return true;
    }

    private boolean cmdCrewInfo(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage(PFX + "§cUsage: /ship crew info <name>"); return true; }
        Ship ship = currentShip(player);
        if (ship == null) { player.sendMessage(PFX + "§cNot on a ship."); return true; }
        CrewMember cm = plugin.getCrewManager().getByName(ship.getShipId(), args[2]);
        if (cm == null) { player.sendMessage(PFX + "§cNo crew named §f" + args[2]); return true; }
        player.sendMessage("§b§l[ " + cm.getName() + " ]");
        player.sendMessage("§7Species: §f" + cm.getSpecies() + "  §7Role: §f" + cm.getRole().getDisplayName());
        player.sendMessage("§7Skill: §f" + cm.getSkillLevel() + "/10  §7Morale: "
                + cm.moraleColour() + String.format("%.1f", cm.getMorale()) + "%");
        player.sendMessage("§7Salary: §f" + cm.getSalary() + " §7EC/day");
        return true;
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private Ship currentShip(Player player) {
        for (Ship s : plugin.getShipManager().getAllShips())
            if (s.isPassenger(player.getUniqueId())) return s;
        return plugin.getShipManager().getShipAt(player.getLocation());
    }

    private String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1)
            return filter(List.of("help","register","board","disembark","list","info","delete","warp","scan","crew","save","fuel"), args[0]);

        return switch (args[0].toLowerCase()) {
            case "info","delete","board" -> args.length == 2
                    ? filter(plugin.getShipManager().getAllShips().stream().map(Ship::getName).toList(), args[1])
                    : List.of();
            case "warp" -> {
                if (args.length == 2) yield filter(List.of("abort","admin"), args[1]);
                if (args.length == 3 && args[1].equalsIgnoreCase("admin"))
                    yield filter(List.of("create","delete","list"), args[2]);
                yield List.of();
            }
            case "crew" -> {
                if (args.length == 2) yield filter(List.of("hire","fire","list","info"), args[1]);
                if (args.length == 5 && args[1].equalsIgnoreCase("hire"))
                    yield filter(Arrays.stream(CrewRole.values()).map(r -> r.name().toLowerCase()).toList(), args[4]);
                yield List.of();
            }
            default -> List.of();
        };
    }

    private List<String> filter(List<String> opts, String partial) {
        return opts.stream().filter(s -> s.toLowerCase().startsWith(partial.toLowerCase())).toList();
    }
}