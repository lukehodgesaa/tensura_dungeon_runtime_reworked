package com.mineclawd.tensuradungeon.runtime.generation;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeRoomRuntimeRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeTemplateDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class RollingDungeonPlanAdapter {
    private RollingDungeonPlanAdapter() {
    }

    public static Result adapt(ServerLevel level, RollingDungeonSpatialSolver.Result solved) {
        var authored = DungeonTemplateMetadataRegistry.get(level);
        var mechanical = DungeonTemplateMechanicalRegistry.getForTemplateIds(
            level, authored, solved.nodes().stream()
                .map(RollingDungeonSpatialSolver.PlacedNode::templateId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
        );
        return adapt(level, solved, mechanical);
    }

    public static Result adapt(
        ServerLevel level,
        RollingDungeonSpatialSolver.Result solved,
        DungeonTemplateMechanicalRegistry.Snapshot mechanical
    ) {
        if (!solved.solved()) return new Result(false, List.of(), List.of("SPATIAL_RESULT_NOT_SOLVED"));
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        var authored = DungeonTemplateMetadataRegistry.get(level);
        for (RollingDungeonSpatialSolver.PlacedNode node : solved.nodes()) {
            DungeonTemplateMechanicalMetadata facts = mechanical.templates().get(node.templateId());
            List<BlockPos> controllers = facts == null
                ? List.of() : facts.markerPositions("room_controller");
            if (controllers.size() != 1) {
                errors.add(node.roomIndex() + ":CONTROLLER_COUNT_" + controllers.size());
                continue;
            }
            Optional<StructureTemplate> loaded;
            if (CompositeTemplateDescriptor.isCompositeId(node.templateId())) {
                CompositeRoomRuntimeRegistry.Resolution composite =
                    CompositeRoomRuntimeRegistry.resolve(level, node.templateId());
                loaded = composite.success() && composite.template() != null
                    ? Optional.of(composite.template()) : Optional.empty();
            } else {
                loaded = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(
                    TensuraDungeonRuntime.MOD_ID, "rooms/linear/" + node.templateId()
                ));
            }
            if (loaded.isEmpty()) {
                errors.add(node.roomIndex() + ":MISSING_TEMPLATE_" + node.templateId());
                continue;
            }
            StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(node.rotation());
            BoundingBox templateBounds = loaded.get().getBoundingBox(settings, node.placementOrigin());
            RollingDungeonSpatialSolver.Bounds expected = node.bounds();
            if (!sameBounds(templateBounds, expected)) {
                errors.add(node.roomIndex() + ":ROTATED_BOUNDS_MISMATCH_expected=" + expected
                    + "_actual=" + format(templateBounds));
                continue;
            }
            BlockPos transformedController = StructureTemplate.calculateRelativePosition(
                settings, controllers.getFirst()
            );
            BlockPos controllerPos = node.placementOrigin().offset(transformedController).immutable();
            DungeonTemplateMetadata metadata = authored.templates().get(node.templateId());
            boolean safeRoom = isSafeRoom(node.role(), metadata);
            DungeonRoomRole gameplayRole = resolveGameplayRole(node.role(), metadata, facts);
            String effectiveProfile = profileFor(gameplayRole, safeRoom);
            String objectiveType = resolveObjective(gameplayRole, safeRoom ? "NONE" : node.objectiveType(), facts);
            String preflightError = readinessError(facts, effectiveProfile, objectiveType, node.outgoingConnector() != null);
            if (preflightError != null) {
                errors.add(node.roomIndex() + ":" + node.templateId() + ":" + preflightError);
                continue;
            }
            plans.add(new GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan(
                node.roomIndex(), controllerPos, node.templateId(), safeRoom, safeRoom,
                node.rotation().name(), node.placementOrigin().immutable(),
                new RoomControllerBlockEntity.RoomBounds(
                    new BlockPos(expected.minX(), expected.minY(), expected.minZ()),
                    new BlockPos(expected.maxX(), expected.maxY(), expected.maxZ())
                ),
                node.incomingConnector().immutable(), node.incomingFacing().name(),
                node.outgoingConnector() == null ? null : node.outgoingConnector().immutable(),
                node.outgoingFacing() == null ? "NONE" : node.outgoingFacing().name(),
                node.outgoingConnectorCount(), gameplayRole.name(), effectiveProfile, objectiveType
            ));
        }
        return new Result(errors.isEmpty() && plans.size() == solved.nodes().size(), List.copyOf(plans), List.copyOf(errors));
    }

    private static DungeonRoomRole resolveGameplayRole(
        DungeonRoomRole positionalRole,
        DungeonTemplateMetadata metadata,
        DungeonTemplateMechanicalMetadata facts
    ) {
        if (positionalRole == DungeonRoomRole.ENTRY
            && metadata != null
            && metadata.roles().contains(DungeonRoomRole.COMBAT)
            && facts != null
            && facts.markerCount("combat_spawn") > 0) {
            return DungeonRoomRole.COMBAT;
        }
        return positionalRole;
    }

    static boolean isSafeRoom(DungeonRoomRole role, DungeonTemplateMetadata metadata) {
        return role == DungeonRoomRole.ENTRY
            || role == DungeonRoomRole.SAFE
            || metadata != null && metadata.alwaysSafe();
    }

    private static String resolveObjective(DungeonRoomRole role, String requested, DungeonTemplateMechanicalMetadata facts) {
        if (facts == null || role == DungeonRoomRole.SAFE || role == DungeonRoomRole.ENTRY
            || role == DungeonRoomRole.CORRIDOR || role == DungeonRoomRole.TRAVERSAL
            || role == DungeonRoomRole.INTERSECTION) return "NONE";
        List<String> allowed = switch (role) {
            case COMBAT -> List.of("CLEAR_ALL", "KILL_ELITE", "SURVIVE_TIMER", "ACTIVATE_RUNES");
            case LARGE_COMBAT, ARENA, FINALE -> List.of("KILL_ELITE", "SURVIVE_TIMER", "CLEAR_ALL");
            case INTERACTION -> List.of("ACTIVATE_RUNES", "NONE");
            case TREASURE -> List.of("CLEAR_ALL", "NONE");
            default -> List.of("NONE");
        };
        int requestedIndex = allowed.indexOf(requested);
        for (int step = 0; step < allowed.size(); step++) {
            String candidate = allowed.get(Math.floorMod(Math.max(0, requestedIndex) + step, allowed.size()));
            if (objectiveSupported(candidate, facts)) return candidate;
        }
        return "NONE";
    }

    private static boolean objectiveSupported(String objective, DungeonTemplateMechanicalMetadata facts) {
        if ("NONE".equals(objective)) return true;
        if ("ACTIVATE_RUNES".equals(objective)) return facts.markerCount("objective") > 0;
        return facts.markerCount("combat_spawn") > 0;
    }
    private static String readinessError(
        DungeonTemplateMechanicalMetadata facts,
        String profile,
        String objective,
        boolean hasOutgoingConnector
    ) {
        if (facts == null || !facts.solverEligible()) return "MECHANICALLY_INELIGIBLE";
        boolean combat = switch (profile) {
            case "STANDARD_COMBAT", "LARGE_COMBAT", "ARENA_WAVE", "CORRIDOR_LIGHT", "CORRIDOR_AMBUSH", "TREASURE_AMBUSH", "BOSS_CAPABLE" -> true;
            default -> false;
        };
        if (combat && facts.markerCount("combat_spawn") <= 0) return "PROFILE_REQUIRES_SPAWN";
        if (combat && facts.markerCount("mob_reset") <= 0) return "PROFILE_REQUIRES_RESET";
        if (combat && facts.markerCount("barrier") <= 0) return "PROFILE_REQUIRES_BARRIER";
        if ("ACTIVATE_RUNES".equals(objective) && facts.markerCount("objective") <= 0) return "OBJECTIVE_REQUIRES_ANCHOR";
        if (hasOutgoingConnector && facts.connectorMode() == DungeonTemplateMechanicalMetadata.ConnectorMode.TYPED
            && (facts.incomingConnectors().size() != 1 || facts.markerCount("connector_exit") < 1)) {
            return "TYPED_CONNECTOR_CONTRACT";
        }
        return null;
    }
    private static String profileFor(DungeonRoomRole role, boolean safeRoom) {
        if (safeRoom) return "SAFE_ROOM";
        return switch (role) {
            case ENTRY, CORRIDOR -> "CONNECTOR_EMPTY";
            case TRAVERSAL, INTERACTION -> "TRAVERSAL_HAZARD";
            case TREASURE -> "TREASURE_AMBUSH";
            case COMBAT -> "STANDARD_COMBAT";
            case LARGE_COMBAT -> "LARGE_COMBAT";
            case ARENA -> "ARENA_WAVE";
            case INTERSECTION -> "LARGE_COMBAT";
            case FINALE -> "BOSS_CAPABLE";
            case SAFE -> "SAFE_ROOM";
        };
    }
    private static boolean sameBounds(BoundingBox actual, RollingDungeonSpatialSolver.Bounds expected) {
        return actual.minX() == expected.minX() && actual.minY() == expected.minY() && actual.minZ() == expected.minZ()
            && actual.maxX() == expected.maxX() && actual.maxY() == expected.maxY() && actual.maxZ() == expected.maxZ();
    }

    private static String format(BoundingBox bounds) {
        return bounds.minX() + " " + bounds.minY() + " " + bounds.minZ() + " -> "
            + bounds.maxX() + " " + bounds.maxY() + " " + bounds.maxZ();
    }

    public record Result(
        boolean valid,
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans,
        List<String> errors
    ) {
    }
}
