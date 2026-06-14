package com.usermc.horizon.mission;

import com.usermc.horizon.warp.WarpBeacon;

import java.util.*;

/**
 * Generates procedural missions targeting existing warp beacons.
 * Called by MissionManager when the board needs refreshing.
 *
 * All mission types complete on arrival at the target beacon — the destination
 * IS the objective. Warp Manager notifies MissionManager when a ship arrives.
 */
public class MissionGenerator {

    private static final Random RNG = new Random();

    // Mission title templates per type
    private static final Map<MissionType, String[]> TITLES = Map.of(
            MissionType.DELIVERY, new String[]{
                    "Priority Cargo to %s",
                    "Emergency Supplies for %s",
                    "Classified Shipment to %s",
                    "Medical Supplies: %s",
                    "Diplomatic Pouch to %s"
            },
            MissionType.SURVEY, new String[]{
                    "Stellar Survey: %s",
                    "Scientific Analysis at %s",
                    "Anomaly Investigation near %s",
                    "Sensor Sweep: %s Region",
                    "Resource Assessment at %s"
            },
            MissionType.PATROL, new String[]{
                    "Security Patrol: %s",
                    "Escort Duty at %s",
                    "Threat Assessment: %s",
                    "Peacekeeping Operation at %s",
                    "Piracy Deterrence near %s"
            },
            MissionType.SALVAGE, new String[]{
                    "Wreck Recovery at %s",
                    "Salvage Operation: %s",
                    "Emergency Retrieval near %s",
                    "Field Salvage at %s",
                    "Derelict Recovery: %s Sector"
            }
    );

    private static final Map<MissionType, String[]> DESCRIPTIONS = Map.of(
            MissionType.DELIVERY, new String[]{
                    "Transport a sealed cargo container to %s. Time-sensitive.",
                    "Deliver emergency rations to the outpost at %s.",
                    "Carry a priority diplomatic pouch to %s. Do not open.",
                    "Emergency medical supplies needed at %s. Warp immediately."
            },
            MissionType.SURVEY, new String[]{
                    "Conduct a detailed sensor sweep of the %s region and report findings.",
                    "An unusual energy signature was detected near %s. Investigate.",
                    "Perform a resource mapping survey of the area surrounding %s.",
                    "Scientific instruments require calibration data from the %s sector."
            },
            MissionType.PATROL, new String[]{
                    "Provide a visible security presence at %s for one rotation cycle.",
                    "Reports of unidentified vessels near %s. Investigate and log.",
                    "Escort a civilian convoy through the %s sector safely.",
                    "The %s region has seen increased piracy. Your presence is needed."
            },
            MissionType.SALVAGE, new String[]{
                    "A derelict vessel was detected near %s. Recover what you can.",
                    "Emergency salvage required at %s following a recent incident.",
                    "Recover a drifting cargo container reported near %s.",
                    "Retrieve sensor logs from an abandoned probe near %s."
            }
    );

    /** Generate a batch of missions targeting the given beacon. */
    public List<Mission> generateFor(WarpBeacon beacon, int count) {
        List<Mission> missions = new ArrayList<>();
        MissionType[] types = MissionType.values();

        for (int i = 0; i < count; i++) {
            MissionType type       = types[RNG.nextInt(types.length)];
            int         difficulty = RNG.nextInt(3) + 1;
            int         baseEc     = difficulty * 80;
            int         rewardEc   = baseEc + RNG.nextInt(baseEc);
            long        rewardXp   = difficulty * 25L + RNG.nextInt(25);

            String title = String.format(
                    randomFrom(TITLES.get(type)), beacon.getName());
            String description = String.format(
                    randomFrom(DESCRIPTIONS.get(type)), beacon.getName());

            // Missions expire after 2 hours
            long expiresAt = System.currentTimeMillis() + (2 * 60 * 60 * 1000L);

            missions.add(new Mission(
                    UUID.randomUUID(),
                    type,
                    title,
                    description,
                    beacon.getBeaconId(),
                    beacon.getName(),
                    difficulty,
                    rewardEc,
                    rewardXp,
                    expiresAt
            ));
        }
        return missions;
    }

    private static <T> T randomFrom(T[] array) {
        return array[RNG.nextInt(array.length)];
    }
}