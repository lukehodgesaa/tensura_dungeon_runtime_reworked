package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class StructureMarkerReadinessValidator {
    private static final int FALLBACK_AUTHORING_SCAN_RADIUS = 32;

    private StructureMarkerReadinessValidator() {
    }

    public static StructureMarkerReadinessReport analyze(ServerLevel level, RoomControllerBlockEntity controller) {
        controller.scanMarkers(level);
        RoomDebugSnapshot.Markers markers = controller.roomMarkerDebugSnapshot(level);
        RoomControllerBlockEntity.RoomBounds bounds = markers.roomBounds();
        MarkerCountSummary markerCounts = scanKnownMarkers(level, controller.getBlockPos(), bounds);
        return buildReport(controller, markers, bounds, markerCounts, false);
    }

    public static StructureMarkerReadinessReport analyzeGenerated(
        ServerLevel level,
        RoomControllerBlockEntity controller,
        Map<String, List<BlockPos>> markerPositions
    ) {
        RoomDebugSnapshot.Markers markers = controller.roomMarkerDebugSnapshot(level);
        RoomControllerBlockEntity.RoomBounds bounds = markers.roomBounds();
        MarkerCountSummary markerCounts = new MarkerCountSummary(
            markerCount(markerPositions, "room_controller"),
            markerCount(markerPositions, "spawn_boss"),
            markerCount(markerPositions, "safe_zone_anchor"),
            markerCount(markerPositions, "hub_spawn_anchor"),
            markerCount(markerPositions, "hub_return_anchor"),
            markerCount(markerPositions, "hub_safe_zone_anchor"),
            markerCount(markerPositions, "hub_gate_anchor"),
            markerCount(markerPositions, "hub_floor_select_anchor"),
            markerCount(markerPositions, "floor_build_origin"),
            markerCount(markerPositions, "floor_entry_anchor"),
            markerCount(markerPositions, "floor_connector_anchor"),
            markerCount(markerPositions, "connector_exit"),
            markerCount(markerPositions, "barrier_anchor"),
            markerCount(markerPositions, "barrier_anchor_1")
                + markerCount(markerPositions, "barrier_anchor_2")
                + markerCount(markerPositions, "barrier_anchor_3")
                + markerCount(markerPositions, "barrier_anchor_4"),
            markerCount(markerPositions, "objective_anchor"),
            markerCount(markerPositions, "interact_anchor"),
            markerCount(markerPositions, "exit_anchor")
        );
        return buildReport(controller, markers, bounds, markerCounts, true);
    }

    private static int markerCount(
        Map<String, List<BlockPos>> markerPositions,
        String markerName
    ) {
        return markerPositions == null
            ? 0 : markerPositions.getOrDefault(markerName, List.of()).size();
    }

    private static StructureMarkerReadinessReport buildReport(
        RoomControllerBlockEntity controller,
        RoomDebugSnapshot.Markers markers,
        RoomControllerBlockEntity.RoomBounds bounds,
        MarkerCountSummary markerCounts,
        boolean allowGeneratedBoundsWithoutAnchors
    ) {
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        RoomProfile profile = controller.getRoomProfile();
        boolean combatProfile = controller.doesProfileAllowCombat();
        int totalCombatSpawns = markers.ownedCombatAnchorCount()
            + markers.eliteSpawnAnchors().size()
            + markerCounts.bossSpawns;
        int ownedMobResetAnchors = markers.mobResetAnchors().size();

        if (markerCounts.roomControllers != 1) {
            missing.add(markerCounts.roomControllers == 0 ? "room_controller" : "exactly one room_controller");
        }
        boolean generatedBoundsSatisfyContract = allowGeneratedBoundsWithoutAnchors
            && markers.hasBounds();
        if (!generatedBoundsSatisfyContract) {
            if (markers.boundsAnchors().isEmpty()) {
                missing.add(allowGeneratedBoundsWithoutAnchors
                    ? "bounds_anchor or generated room bounds" : "bounds_anchor");
            } else if (markers.boundsAnchors().size() == 1) {
                missing.add(allowGeneratedBoundsWithoutAnchors
                    ? "at least two bounds_anchor or generated room bounds"
                    : "at least two bounds_anchor");
                warnings.add("exactly one bounds_anchor found");
            }
        }
        if (combatProfile && totalCombatSpawns <= 0) {
            missing.add("at least one spawn marker");
        }
        if (combatProfile && ownedMobResetAnchors <= 0) {
            missing.add("mob_reset_anchor");
        }
        if ((markerCounts.barrierAnchors + markerCounts.barrierChannelAnchors) <= 0 && markerCounts.exitAnchors <= 0 && markerCounts.floorEntryAnchors <= 0 && markerCounts.floorConnectorAnchors <= 0 && markerCounts.connectorExitAnchors <= 0) {
            missing.add("barrier_anchor, barrier_anchor_1..4, floor_connector_anchor, connector_exit, or exit_anchor/floor_entry_anchor");
        }
        if (controller.getConfiguredObjectiveType().requiresObjectiveAnchor() && markerCounts.objectiveAnchors <= 0) {
            missing.add("objective_anchor");
        }
        if (markerCounts.interactAnchors > 0) {
            warnings.add("interact_anchor present; reserved for future interaction-style objectives");
        }

        if (!markers.hasBounds()) {
            warnings.add("radius mode only; add at least two bounds_anchor or use generated room bounds for template-safe ownership");
        }
        if (!markers.claimedMarkerMode()) {
            warnings.add("legacy marker radius fallback active; run /tensuradungeon room claimmarkers <radius> for large rooms");
        }
        if (combatProfile && (markerCounts.barrierAnchors + markerCounts.barrierChannelAnchors) <= 0) {
            warnings.add("no barrier anchors found for combat-room seal logic");
        }
        if (markerCounts.exitAnchors <= 0 && markerCounts.floorEntryAnchors <= 0 && markerCounts.floorConnectorAnchors <= 0 && markerCounts.connectorExitAnchors <= 0) {
            warnings.add("no exit_anchor/floor_entry_anchor/floor_connector_anchor/connector_exit found");
        }
        if (combatProfile && markers.rawMobResetAnchorCount() > ownedMobResetAnchors) {
            warnings.add("nearby mob_reset_anchor markers exist but are not owned by this controller; check saved bounds and claim filtering");
        }
        if (!combatProfile && totalCombatSpawns > 0) {
            warnings.add("non-combat profile has combat spawn markers");
        }
        if (profile == RoomProfile.CORRIDOR_AMBUSH && markers.flankSpawnAnchors().isEmpty()) {
            warnings.add("corridor_ambush has no spawn_flank marker");
        }
        if (profile == RoomProfile.BOSS_CAPABLE && markerCounts.bossSpawns <= 0) {
            warnings.add("boss_capable profile has no spawn_boss marker yet");
        }
        if (markerCounts.safeZoneAnchors > 0 && combatProfile) {
            warnings.add("safe_zone_anchor present inside a combat-room scan window");
        }

        boolean readinessPass = missing.isEmpty();
        boolean safeForFutureJigsawUse = readinessPass
            && markers.hasBounds()
            && markerCounts.roomControllers == 1
            && (markerCounts.exitAnchors > 0 || markerCounts.floorEntryAnchors > 0 || markerCounts.floorConnectorAnchors > 0 || markerCounts.connectorExitAnchors > 0)
            && (!combatProfile || ownedMobResetAnchors > 0);

        return new StructureMarkerReadinessReport(
            controller.getBlockPos(),
            profile,
            formatBoundsSummary(bounds),
            markerCounts.roomControllers,
            markers.boundsAnchors().size(),
            markers.spawnAnchors().size(),
            markers.rangedSpawnAnchors().size(),
            markers.flankSpawnAnchors().size(),
            markers.highgroundSpawnAnchors().size(),
            markers.eliteSpawnAnchors().size(),
            markerCounts.bossSpawns,
            markers.barrierAnchors().size(),
            markers.objectiveAnchors().size(),
            markers.exitAnchors().size(),
            markerCounts.safeZoneAnchors,
            ownedMobResetAnchors,
            markerCounts.floorEntryAnchors,
            markerCounts.hubSpawnAnchors,
            markerCounts.hubReturnAnchors,
            markerCounts.hubSafeZoneAnchors,
            markerCounts.hubGateAnchors,
            markerCounts.hubFloorSelectAnchors,
            markerCounts.floorBuildOrigins,
            List.copyOf(missing),
            List.copyOf(warnings),
            readinessPass,
            safeForFutureJigsawUse
        );
    }

    private static String formatBoundsSummary(RoomControllerBlockEntity.RoomBounds bounds) {
        if (bounds == null) {
            return "NONE";
        }
        return formatPos(bounds.min()) + " -> " + formatPos(bounds.max())
            + " (" + bounds.getWidth() + "x" + bounds.getHeight() + "x" + bounds.getDepth() + ")";
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static MarkerCountSummary scanKnownMarkers(ServerLevel level, BlockPos center, RoomControllerBlockEntity.RoomBounds bounds) {
        int roomControllers = 0;
        int bossSpawns = 0;
        int safeZoneAnchors = 0;
        int hubSpawnAnchors = 0;
        int hubReturnAnchors = 0;
        int hubSafeZoneAnchors = 0;
        int hubGateAnchors = 0;
        int hubFloorSelectAnchors = 0;
        int floorBuildOrigins = 0;
        int floorEntryAnchors = 0;
        int floorConnectorAnchors = 0;
        int connectorExitAnchors = 0;
        int barrierAnchors = 0;
        int barrierChannelAnchors = 0;
        int objectiveAnchors = 0;
        int interactAnchors = 0;
        int exitAnchors = 0;

        BlockPos min = bounds == null ? center.offset(-FALLBACK_AUTHORING_SCAN_RADIUS, -FALLBACK_AUTHORING_SCAN_RADIUS, -FALLBACK_AUTHORING_SCAN_RADIUS) : bounds.min();
        BlockPos max = bounds == null ? center.offset(FALLBACK_AUTHORING_SCAN_RADIUS, FALLBACK_AUTHORING_SCAN_RADIUS, FALLBACK_AUTHORING_SCAN_RADIUS) : bounds.max();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockPos markerPos = pos.immutable();
            BlockState state = level.getBlockState(markerPos);
            boolean inBounds = bounds == null || bounds.contains(markerPos);
            if (state.is(ModBlocks.ROOM_CONTROLLER.get()) && inBounds) {
                roomControllers += 1;
            } else if (state.is(ModBlocks.SPAWN_BOSS.get()) && inBounds) {
                bossSpawns += 1;
            } else if (state.is(ModBlocks.SAFE_ZONE_ANCHOR.get()) && inBounds) {
                safeZoneAnchors += 1;
            } else if (state.is(ModBlocks.HUB_SPAWN_ANCHOR.get())) {
                hubSpawnAnchors += 1;
            } else if (state.is(ModBlocks.HUB_RETURN_ANCHOR.get())) {
                hubReturnAnchors += 1;
            } else if (state.is(ModBlocks.HUB_SAFE_ZONE_ANCHOR.get())) {
                hubSafeZoneAnchors += 1;
            } else if (state.is(ModBlocks.HUB_GATE_ANCHOR.get())) {
                hubGateAnchors += 1;
            } else if (state.is(ModBlocks.HUB_FLOOR_SELECT_ANCHOR.get())) {
                hubFloorSelectAnchors += 1;
            } else if (state.is(ModBlocks.FLOOR_BUILD_ORIGIN.get())) {
                floorBuildOrigins += 1;
            } else if (state.is(ModBlocks.FLOOR_ENTRY_ANCHOR.get())) {
                floorEntryAnchors += 1;
            } else if (state.is(ModBlocks.FLOOR_CONNECTOR_ANCHOR.get()) && inBounds) {
                floorConnectorAnchors += 1;
            } else if (state.is(ModBlocks.CONNECTOR_EXIT.get()) && inBounds) {
                connectorExitAnchors += 1;
            } else if (state.is(ModBlocks.BARRIER_ANCHOR.get()) && inBounds) {
                barrierAnchors += 1;
            } else if ((state.is(ModBlocks.BARRIER_ANCHOR_1.get()) || state.is(ModBlocks.BARRIER_ANCHOR_2.get()) || state.is(ModBlocks.BARRIER_ANCHOR_3.get()) || state.is(ModBlocks.BARRIER_ANCHOR_4.get())) && inBounds) {
                barrierChannelAnchors += 1;
            } else if (state.is(ModBlocks.OBJECTIVE_ANCHOR.get()) && inBounds) {
                objectiveAnchors += 1;
            } else if (state.is(ModBlocks.INTERACT_ANCHOR.get()) && inBounds) {
                interactAnchors += 1;
            } else if (state.is(ModBlocks.EXIT_ANCHOR.get()) && inBounds) {
                exitAnchors += 1;
            }
        }

        return new MarkerCountSummary(roomControllers, bossSpawns, safeZoneAnchors, hubSpawnAnchors, hubReturnAnchors, hubSafeZoneAnchors, hubGateAnchors, hubFloorSelectAnchors, floorBuildOrigins, floorEntryAnchors, floorConnectorAnchors, connectorExitAnchors, barrierAnchors, barrierChannelAnchors, objectiveAnchors, interactAnchors, exitAnchors);
    }

    private record MarkerCountSummary(
        int roomControllers,
        int bossSpawns,
        int safeZoneAnchors,
        int hubSpawnAnchors,
        int hubReturnAnchors,
        int hubSafeZoneAnchors,
        int hubGateAnchors,
        int hubFloorSelectAnchors,
        int floorBuildOrigins,
        int floorEntryAnchors,
        int floorConnectorAnchors,
        int connectorExitAnchors,
        int barrierAnchors,
        int barrierChannelAnchors,
        int objectiveAnchors,
        int interactAnchors,
        int exitAnchors
    ) {
    }
}
