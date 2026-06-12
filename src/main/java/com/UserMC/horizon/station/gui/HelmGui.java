package com.usermc.horizon.station.gui;

import com.usermc.horizon.Horizon;
import com.usermc.horizon.crew.CrewMember;
import com.usermc.horizon.ship.Ship;
import com.usermc.horizon.ship.engine.MovementRequest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.Optional;

/**
 * Helm Console GUI — 3 rows.
 *
 * Controls are heading-relative, not cardinal.
 * The ship's stored heading determines what "forward" means in world space.
 *
 * Layout (slot numbers):
 *   [  ] [FWD] [  ] [  ] [ U ] [  ] [  ] [INF] [  ]
 *   [◄L] [   ] [►R] [  ] [   ] [  ] [S-] [S+]  [  ]
 *   [  ] [BCK] [  ] [↺L] [ D ] [↻R] [AP] [   ] [  ]
 *
 * ↺ / ↻ = rotate ship 90° left / right (one step per movement tick, animated)
 */
public class HelmGui extends HorizonGui {

    private static final int SLOT_FORWARD      = 1;
    private static final int SLOT_STRAFE_LEFT  = 9;
    private static final int SLOT_STRAFE_RIGHT = 11;
    private static final int SLOT_BACKWARD     = 19;
    private static final int SLOT_UP           = 4;
    private static final int SLOT_DOWN         = 22;
    private static final int SLOT_INFO         = 7;
    private static final int SLOT_SPD_DN       = 15;
    private static final int SLOT_SPD_UP       = 16;
    private static final int SLOT_ROTATE_LEFT  = 21;
    private static final int SLOT_ROTATE_RIGHT = 23;
    private static final int SLOT_AUTO         = 24;

    private int     speed;
    private boolean autopilotSelectMode = false;

    public HelmGui(Ship ship, Player player) {
        super(ship, player, 3, "§b⚙ Helm — " + ship.getName());
        this.speed = Horizon.getInstance().getHorizonConfig().getDefaultSpeed();
    }

    @Override
    public void build() {
        inventory.clear();
        Horizon plugin      = Horizon.getInstance();
        boolean warpCharging = plugin.getWarpManager().isCharging(ship);
        boolean autopilotOn  = plugin.getAutoPilot().isActive(ship);

        String heading = headingLabel(ship.getHeading());

        // --- Movement arrows (relative) ---
        String colour = warpCharging ? "§c" : (autopilotSelectMode ? "§e" : "§a");
        String suffix = autopilotSelectMode ? " §8(autopilot)" : "";

        inventory.setItem(SLOT_FORWARD,      makeItem(Material.LIME_DYE,  colour + "▲ Forward" + suffix,  "§8→ " + heading));
        inventory.setItem(SLOT_BACKWARD,     makeItem(Material.LIME_DYE,  colour + "▼ Backward" + suffix, "§8← " + opposite(heading)));
        inventory.setItem(SLOT_STRAFE_LEFT,  makeItem(Material.CYAN_DYE,  colour + "◄ Strafe Left" + suffix));
        inventory.setItem(SLOT_STRAFE_RIGHT, makeItem(Material.CYAN_DYE,  colour + "► Strafe Right" + suffix));
        inventory.setItem(SLOT_UP,           makeItem(Material.LIGHT_BLUE_DYE, colour + "▲ Up"));
        inventory.setItem(SLOT_DOWN,         makeItem(Material.LIGHT_BLUE_DYE, colour + "▼ Down"));

        // --- Rotation ---
        String rotColour = warpCharging ? "§c" : "§6";
        inventory.setItem(SLOT_ROTATE_LEFT,  makeItem(Material.ORANGE_DYE, rotColour + "↺ Rotate Left",  "§8Turn 90° CCW"));
        inventory.setItem(SLOT_ROTATE_RIGHT, makeItem(Material.ORANGE_DYE, rotColour + "↻ Rotate Right", "§8Turn 90° CW"));

        // --- Info ---
        String speedColour = speed >= 4 ? "§c" : speed >= 3 ? "§e" : "§a";
        inventory.setItem(SLOT_INFO, makeItem(Material.PAPER,
                "§b" + ship.getName(),
                "§7Heading: §f" + heading,
                "§7Speed:   " + speedColour + speed,
                "§7Mult:    §f" + String.format("%.0f%%", ship.getSpeedMultiplier() * 100),
                warpCharging ? "§c⚠ Warp charging — helm locked" : "§7Status: §f" + ship.getStatus()
        ));

        // --- Speed controls ---
        inventory.setItem(SLOT_SPD_DN, makeItem(Material.RED_DYE,
                speed <= 1 ? "§8─ Min speed" : "§c─ Decrease Speed", "§7Current: §f" + speed));
        inventory.setItem(SLOT_SPD_UP, makeItem(Material.GREEN_DYE,
                speed >= plugin.getHorizonConfig().getMaxSpeed() ? "§8+ Max speed" : "§a+ Increase Speed",
                "§7Current: §f" + speed));

        // --- Autopilot ---
        Optional<CrewMember> helmsmanOpt = plugin.getCrewManager().getHelmsman(ship.getShipId());

        if (autopilotOn) {
            var dir = plugin.getAutoPilot().getHeading(ship);
            inventory.setItem(SLOT_AUTO, makeItem(Material.CLOCK,
                    "§c■ Stop Autopilot",
                    dir != null ? "§7Heading: §f" + dir.name() : "",
                    helmsmanOpt.map(h -> "§7Helm: §f" + h.getName()).orElse(""),
                    "§eClick to disengage"
            ));
        } else if (autopilotSelectMode) {
            inventory.setItem(SLOT_AUTO, makeItem(Material.CLOCK,
                    "§e⚙ Select Heading...",
                    "§7Click a movement arrow to set autopilot course.",
                    "§eClick again to cancel"
            ));
        } else {
            boolean hasHelm = helmsmanOpt.isPresent();
            inventory.setItem(SLOT_AUTO, makeItem(
                    hasHelm ? Material.CLOCK : Material.BARRIER,
                    hasHelm ? "§a⚙ Engage Autopilot" : "§c⚙ No Helmsman",
                    hasHelm
                            ? "§7Helmsman: §f" + helmsmanOpt.get().getName()
                              + " §8(Mo." + String.format("%.0f", helmsmanOpt.get().getMorale()) + "%)"
                            : "§7Hire one via §f/ship crew hire",
                    hasHelm ? "§eClick to select heading" : ""
            ));
        }

        fillAll();
    }

    @Override
    public boolean handleClick(int slot, ClickType click) {
        Horizon plugin = Horizon.getInstance();

        if (slot == SLOT_SPD_DN) { speed = Math.max(1, speed - 1); return true; }
        if (slot == SLOT_SPD_UP) {
            speed = Math.min(plugin.getHorizonConfig().getMaxSpeed(), speed + 1);
            return true;
        }

        if (slot == SLOT_AUTO) {
            if (plugin.getAutoPilot().isActive(ship)) {
                plugin.getAutoPilot().stopAutoPilot(ship);
                player.sendActionBar("§e[Helm] Autopilot disengaged.");
            } else {
                autopilotSelectMode = !autopilotSelectMode;
            }
            return true;
        }

        // Rotation buttons
        if (slot == SLOT_ROTATE_LEFT || slot == SLOT_ROTATE_RIGHT) {
            if (plugin.getWarpManager().isCharging(ship)) {
                player.sendActionBar("§c[Helm] Warp charging — helm locked.");
                return false;
            }
            if (!ship.isReady()) {
                player.sendActionBar("§c[Helm] Ship not scanned — Shift+right-click Ship Core.");
                return false;
            }
            boolean cw = (slot == SLOT_ROTATE_RIGHT);
            plugin.getMovementEngine().queueMovement(MovementRequest.rotate(ship, cw));
            return true;
        }

        // Movement buttons
        String relative = slotToRelative(slot);
        if (relative == null) return false;

        if (plugin.getWarpManager().isCharging(ship)) {
            player.sendActionBar("§c[Helm] Warp charging — helm locked.");
            return false;
        }
        if (!ship.isReady()) {
            player.sendActionBar("§c[Helm] Ship not scanned.");
            return false;
        }

        if (autopilotSelectMode) {
            autopilotSelectMode = false;
            // Only horizontal directions make sense for autopilot
            if (relative.equals("UP") || relative.equals("DOWN")) {
                player.sendActionBar("§c[Helm] Autopilot cannot hold vertical course.");
                return true;
            }
            var helmsmanOpt = plugin.getCrewManager().getHelmsman(ship.getShipId());
            if (helmsmanOpt.isEmpty()) {
                player.sendActionBar("§c[Helm] No Helmsman on crew.");
                return true;
            }
            // Resolve relative → absolute for autopilot
            var absDir = ship.resolveDirection(relative);
            plugin.getAutoPilot().startAutoPilot(ship, absDir, speed, helmsmanOpt.get(), player);
            player.closeInventory();
            return false;
        }

        // Single movement step
        var dir = ship.resolveDirection(relative);
        plugin.getMovementEngine().queueMovement(MovementRequest.move(ship, dir, speed));
        return true;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String slotToRelative(int slot) {
        return switch (slot) {
            case SLOT_FORWARD      -> "FORWARD";
            case SLOT_BACKWARD     -> "BACKWARD";
            case SLOT_STRAFE_LEFT  -> "STRAFE_LEFT";
            case SLOT_STRAFE_RIGHT -> "STRAFE_RIGHT";
            case SLOT_UP           -> "UP";
            case SLOT_DOWN         -> "DOWN";
            default                -> null;
        };
    }

    private String headingLabel(float h) {
        int n = Math.floorMod(Math.round(h), 360);
        if (n < 45 || n >= 315) return "South";
        if (n < 135)            return "West";
        if (n < 225)            return "North";
        return                         "East";
    }

    private String opposite(String dir) {
        return switch (dir) {
            case "North" -> "South";
            case "South" -> "North";
            case "East"  -> "West";
            case "West"  -> "East";
            default      -> dir;
        };
    }
}