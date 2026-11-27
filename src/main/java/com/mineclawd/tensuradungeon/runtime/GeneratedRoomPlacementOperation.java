package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.GeneratedRoomBoundsWriter;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.RoomObjectiveType;
import com.mineclawd.tensuradungeon.block.entity.RoomProfile;
import com.mineclawd.tensuradungeon.block.entity.StructureMarkerReadinessReport;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalMetadata;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeRoomRuntimeRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeTemplateDescriptor;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

public final class GeneratedRoomPlacementOperation {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GeneratedRoomPlacementOperation() {
    }

    public static Result place(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        Request request
    ) {
        Result result;
        do {
            result = place(level, repository, session, request, SliceBudget.unbounded());
        } while (result.inProgress());
        return result;
    }

    public static Result place(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        Request request,
        SliceBudget budget
    ) {
        long totalStartedAt = System.nanoTime();
        if (level == null || repository == null || session == null || request == null || budget == null) {
            return Result.failed(0, "INVALID_PLACEMENT_REQUEST", null, Metrics.empty(elapsedMillis(totalStartedAt)));
        }
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan = request.roomPlan();
        if (roomPlan == null) {
            return Result.failed(0, "INVALID_PLACEMENT_REQUEST", null, Metrics.empty(elapsedMillis(totalStartedAt)));
        }
        if (!roomPlan.hasPlacementMetadata()) {
            return Result.failed(
                roomPlan.index(), "LEGACY_PLAN_HAS_NO_PLACEMENT_METADATA", null,
                Metrics.empty(elapsedMillis(totalStartedAt))
            );
        }
        if (roomPlan.bounds() == null || roomPlan.controllerPos() == null || roomPlan.placementOrigin() == null) {
            return Result.failed(
                roomPlan.index(), "INCOMPLETE_PLACEMENT_METADATA", null,
                Metrics.empty(elapsedMillis(totalStartedAt))
            );
        }

        DungeonInstanceAllocationData allocationData = DungeonInstanceAllocationData.get(level);
        String dimension = level.dimension().location().toString();
        DungeonInstanceAllocationData.InstanceKey instanceKey = session.instanceKey();
        if (instanceKey == null) {
            return Result.failed(
                roomPlan.index(), "MISSING_GENERATED_INSTANCE_OWNERSHIP", null,
                Metrics.empty(elapsedMillis(totalStartedAt))
            );
        }
        String cellValidation = allocationData.validateOwnedBounds(
            dimension, instanceKey, roomPlan.bounds()
        );
        if (!"VALID_INSTANCE_CELL_BOUNDS".equals(cellValidation)) {
            allocationData.forIdentity(instanceKey).ifPresent(allocation ->
                allocationData.quarantine(
                    allocation.key(),
                    "ROOM_PLACEMENT_OWNERSHIP_" + cellValidation + "_INDEX_" + roomPlan.index(),
                    level.getGameTime()
                )
            );
            return Result.failed(
                roomPlan.index(), cellValidation, null,
                Metrics.empty(elapsedMillis(totalStartedAt))
            );
        }

        boolean activeOperation = GeneratedRoomIncrementalPlacementEngine.hasActive(
            level, session, request
        );
        if (!activeOperation && level.getBlockState(roomPlan.controllerPos())
            .is(ModBlocks.ROOM_CONTROLLER.get())) {
            BlockEntity existingBlockEntity = level.getBlockEntity(roomPlan.controllerPos());
            if (!request.allowExistingController()) {
                return Result.failed(
                    roomPlan.index(), "EXISTING_CONTROLLER_NOT_ALLOWED", null,
                    Metrics.empty(elapsedMillis(totalStartedAt))
                );
            }
            if (!(existingBlockEntity instanceof RoomControllerBlockEntity existingController)) {
                return Result.failed(
                    roomPlan.index(), "EXISTING_CONTROLLER_BLOCK_ENTITY_MISSING", null,
                    Metrics.empty(elapsedMillis(totalStartedAt))
                );
            }
            String existingFailure = validateExisting(level, session, request, existingController);
            if (existingFailure == null) {
                return registerExisting(
                    level, repository, session, request, existingController, totalStartedAt
                );
            }
            if ("EXISTING_CONTROLLER_INSTANCE_OWNERSHIP_MISMATCH".equals(existingFailure)) {
                return Result.failed(
                    roomPlan.index(), existingFailure, existingController,
                    Metrics.empty(elapsedMillis(totalStartedAt))
                );
            }
        }

        Rotation rotation = parseRotation(roomPlan.rotation());
        if (rotation == null) {
            return Result.failed(
                roomPlan.index(), "INVALID_ROTATION_" + roomPlan.rotation(), null,
                Metrics.empty(elapsedMillis(totalStartedAt))
            );
        }

        long phaseStartedAt = System.nanoTime();
        Optional<StructureTemplate> loaded = loadTemplate(level, roomPlan, request.preloadedTemplate());
        DungeonTemplateMechanicalMetadata mechanical =
            GeneratedRoomIncrementalPlacementEngine.loadMetadata(level, roomPlan);
        double templateLoadMs = elapsedMillis(phaseStartedAt);
        if (loaded.isEmpty()) {
            return Result.failed(
                roomPlan.index(), "MISSING_TEMPLATE_" + templateId(roomPlan), null,
                new Metrics(templateLoadMs, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    elapsedMillis(totalStartedAt))
            );
        }
        if (mechanical == null) {
            return Result.failed(
                roomPlan.index(), "MISSING_MECHANICAL_TEMPLATE_" + roomPlan.templateLabel(), null,
                new Metrics(templateLoadMs, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    elapsedMillis(totalStartedAt))
            );
        }

        double persistenceMs = 0.0D;
        if (!activeOperation) {
            if (request.persistBoundsBeforeMutation()) {
                phaseStartedAt = System.nanoTime();
                appendPlacedBounds(repository, session, roomPlan);
                level.getServer().overworld().getDataStorage().save();
                persistenceMs = elapsedMillis(phaseStartedAt);
            } else if (!placementContainsBounds(session, roomPlan.bounds())) {
                return Result.failed(
                    roomPlan.index(), "CLEANUP_MANIFEST_MISSING_ROOM_BOUNDS", null,
                    new Metrics(templateLoadMs, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                        persistenceMs, elapsedMillis(totalStartedAt))
                );
            }
        }

        GeneratedRoomIncrementalPlacementEngine.AdvanceResult advance =
            GeneratedRoomIncrementalPlacementEngine.advance(
                level, session, request, loaded.get(), rotation, mechanical, budget
            );
        if (advance.failed()) {
            return Result.failed(
                roomPlan.index(), advance.reason(), null,
                new Metrics(templateLoadMs, advance.workMs(), 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    0.0D, persistenceMs, elapsedMillis(totalStartedAt))
            );
        }
        if (!advance.complete()) {
            return Result.inProgress(
                roomPlan.index(), advance.reason(),
                new Metrics(templateLoadMs, advance.workMs(), 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                    0.0D, persistenceMs, elapsedMillis(totalStartedAt))
            );
        }

        phaseStartedAt = System.nanoTime();
        int removedPlacementDrops = GeneratedRoomItemCleanup.clearPlacementDrops(level, roomPlan.bounds());
        double itemCleanupMs = elapsedMillis(phaseStartedAt);

        phaseStartedAt = System.nanoTime();
        ControllerDiscovery discovery = discoverController(level, roomPlan);
        double controllerLookupMs = elapsedMillis(phaseStartedAt);
        if (discovery.controller() == null) {
            return Result.failed(
                roomPlan.index(), discovery.reason(), null,
                new Metrics(templateLoadMs, advance.workMs(), itemCleanupMs, controllerLookupMs, 0.0D,
                    0.0D, 0.0D, 0.0D, 0.0D, persistenceMs, elapsedMillis(totalStartedAt)),
                removedPlacementDrops, null, null
            );
        }
        RoomControllerBlockEntity controller = discovery.controller();

        phaseStartedAt = System.nanoTime();
        if (!configureController(controller, roomPlan, instanceKey)) {
            return Result.failed(
                roomPlan.index(), "CONTROLLER_INSTANCE_OWNERSHIP_MISMATCH", controller,
                new Metrics(templateLoadMs, advance.workMs(), itemCleanupMs, controllerLookupMs, 0.0D,
                    0.0D, 0.0D, 0.0D, 0.0D, persistenceMs, elapsedMillis(totalStartedAt)),
                removedPlacementDrops, null, null
            );
        }
        clearPreClaimMarker(level, request.preClaimMarkerToClear());
        double configurationMs = elapsedMillis(phaseStartedAt);

        GeneratedRoomBoundsWriter.ClaimResult claimResult =
            GeneratedRoomBoundsWriter.writeGeneratedBoundsAndClaimFromManifest(
                level,
                controller,
                roomPlan.bounds(),
                request.claimRadius(),
                advance.worldMarkerPositions()
            );
        GeneratedRoomBoundsWriter.ClaimTimings claimTimings = claimResult.timings();
        StructureMarkerReadinessReport readiness = claimResult.readinessReport();
        if (!claimResult.success() || readiness == null || !readiness.readinessPass()
            || !readiness.safeForFutureJigsawUse()) {
            if (request.persistControllerOnReadinessFailure()) {
                phaseStartedAt = System.nanoTime();
                appendPlacedRoom(repository, session, roomPlan);
                persistenceMs += elapsedMillis(phaseStartedAt);
            }
            String reason = readiness == null
                ? claimResult.boundsResult().reason() : readiness.missingSummary();
            return Result.failed(
                roomPlan.index(), "READINESS_FAILED_" + reason, controller,
                new Metrics(templateLoadMs, advance.workMs(), itemCleanupMs, controllerLookupMs,
                    configurationMs, claimTimings.boundsWriteMs(), claimTimings.markerClaimMs(),
                    claimTimings.readinessMs(), 0.0D, persistenceMs, elapsedMillis(totalStartedAt)),
                removedPlacementDrops, claimResult, null
            );
        }

        phaseStartedAt = System.nanoTime();
        controller.bakeGeneratedMarkers(level);
        controller.resetRoom(level);
        controller.setBranchPreviewMode(request.branchPreview());
        SealResult sealResult = sealRequiredOutgoingConnectors(
            level,
            roomPlan,
            controller,
            advance.worldMarkerPositions(),
            request.sealPolicy()
        );
        double postProcessMs = elapsedMillis(phaseStartedAt);
        if (!sealResult.success()) {
            return Result.failed(
                roomPlan.index(), "GENERATED_OUTGOING_SEAL_FAILED_" + sealResult.reason(), controller,
                new Metrics(templateLoadMs, advance.workMs(), itemCleanupMs, controllerLookupMs,
                    configurationMs, claimTimings.boundsWriteMs(), claimTimings.markerClaimMs(),
                    claimTimings.readinessMs(), postProcessMs, persistenceMs,
                    elapsedMillis(totalStartedAt)),
                removedPlacementDrops, claimResult, sealResult
            );
        }

        if (request.persistControllerOnSuccess()) {
            phaseStartedAt = System.nanoTime();
            appendPlacedRoom(repository, session, roomPlan);
            persistenceMs += elapsedMillis(phaseStartedAt);
        }
        return Result.placed(
            roomPlan.index(), controller, removedPlacementDrops, claimResult, sealResult,
            new Metrics(templateLoadMs, advance.workMs(), itemCleanupMs, controllerLookupMs,
                configurationMs, claimTimings.boundsWriteMs(), claimTimings.markerClaimMs(),
                claimTimings.readinessMs(), postProcessMs, persistenceMs,
                elapsedMillis(totalStartedAt)),
            false
        );
    }

    public static SealResult repairRuntimeOutgoingSeals(
        ServerLevel level,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        RoomControllerBlockEntity controller
    ) {
        Rotation rotation = parseRotation(roomPlan.rotation());
        Map<String, List<BlockPos>> markerPositions = rotation == null
            ? Map.of()
            : worldMarkerPositions(level, roomPlan, rotation);
        return sealRequiredOutgoingConnectors(
            level, roomPlan, controller, markerPositions, SealPolicy.RUNTIME_OUTGOING
        );
    }

    private static Result registerExisting(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        Request request,
        RoomControllerBlockEntity controller,
        long totalStartedAt
    ) {
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan = request.roomPlan();
        String validationFailure = validateExisting(level, session, request, controller);
        if (validationFailure != null) {
            return Result.failed(
                roomPlan.index(), validationFailure, controller, Metrics.empty(elapsedMillis(totalStartedAt))
            );
        }

        SealResult sealResult = repairRuntimeOutgoingSeals(level, roomPlan, controller);
        if (!sealResult.success()) {
            return Result.failed(
                roomPlan.index(), "GENERATED_OUTGOING_SEAL_FAILED_" + sealResult.reason(), controller,
                Metrics.empty(elapsedMillis(totalStartedAt)), 0, null, sealResult
            );
        }
        double persistenceMs = 0.0D;
        if (request.persistControllerOnSuccess()) {
            long persistenceStartedAt = System.nanoTime();
            appendPlacedRoom(repository, session, roomPlan);
            persistenceMs = elapsedMillis(persistenceStartedAt);
        }
        return Result.placed(
            roomPlan.index(), controller, 0, null, sealResult,
            new Metrics(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                persistenceMs, elapsedMillis(totalStartedAt)),
            true
        );
    }

    @Nullable
    static String existingControllerValidationFailure(
        ServerLevel level,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        boolean branchPreview
    ) {
        if (level == null || session == null || roomPlan == null || !roomPlan.hasPlacementMetadata()
            || roomPlan.controllerPos() == null) {
            return "EXISTING_CONTROLLER_METADATA_MISSING";
        }
        if (!level.getBlockState(roomPlan.controllerPos()).is(ModBlocks.ROOM_CONTROLLER.get())) {
            return "EXISTING_CONTROLLER_BLOCK_MISSING";
        }
        BlockEntity blockEntity = level.getBlockEntity(roomPlan.controllerPos());
        if (!(blockEntity instanceof RoomControllerBlockEntity controller)) {
            return "EXISTING_CONTROLLER_BLOCK_ENTITY_MISSING";
        }
        Request request = new Request(
            roomPlan,
            null,
            1,
            branchPreview,
            SealPolicy.RUNTIME_OUTGOING,
            ControllerDiscoveryPolicy.EXPECTED_POSITION,
            true,
            false,
            false,
            false,
            null
        );
        return validateExisting(level, session, request, controller);
    }

    @Nullable
    private static String validateExisting(
        ServerLevel level,
        GeneratedFloorSession session,
        Request request,
        RoomControllerBlockEntity controller
    ) {
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan = request.roomPlan();
        DungeonInstanceAllocationData.InstanceKey instanceKey = session.instanceKey();
        if (instanceKey == null || (!controller.matchesDungeonInstance(instanceKey)
            && !controller.bindDungeonInstance(instanceKey))) {
            return "EXISTING_CONTROLLER_INSTANCE_OWNERSHIP_MISMATCH";
        }
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || !placement.bounds().contains(roomPlan.bounds())) {
            return "EXISTING_CONTROLLER_BOUNDS_NOT_OWNED";
        }
        RoomControllerBlockEntity.RoomBounds actualBounds = controller.protectionView(level).bounds();
        if (!roomPlan.bounds().equals(actualBounds)) {
            return "EXISTING_CONTROLLER_BOUNDS_MISMATCH";
        }
        var markers = controller.roomMarkerDebugSnapshot(level);
        if (!markers.claimedMarkerMode() || !"CLAIMED_MARKERS".equals(markers.markerOwnershipMode())) {
            return "EXISTING_CONTROLLER_NOT_GENERATED";
        }
        RoomProfile expectedProfile = RoomProfile.byName(roomPlan.effectiveProfile());
        if (controller.getRoomProfile() != expectedProfile) {
            return "EXISTING_CONTROLLER_PROFILE_MISMATCH";
        }
        boolean objectiveDisabled = "NONE".equalsIgnoreCase(roomPlan.objectiveType());
        RoomObjectiveType expectedObjective = objectiveDisabled ? null : RoomObjectiveType.byName(roomPlan.objectiveType());
        if ((objectiveDisabled && !"NONE".equals(controller.getForcedObjectiveOverrideSummary()))
            || (!objectiveDisabled && controller.getConfiguredObjectiveType() != expectedObjective)) {
            return "EXISTING_CONTROLLER_OBJECTIVE_MISMATCH";
        }
        if (controller.isBranchPreviewMode() != request.branchPreview()) {
            return "EXISTING_CONTROLLER_PREVIEW_MODE_MISMATCH";
        }
        return null;
    }

    private static ControllerDiscovery discoverController(
        ServerLevel level,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan
    ) {
        BlockEntity blockEntity = level.getBlockEntity(roomPlan.controllerPos());
        if (blockEntity instanceof RoomControllerBlockEntity controller) {
            return ControllerDiscovery.found(controller, 1);
        }
        return ControllerDiscovery.missing("EXPECTED_CONTROLLER_NOT_FOUND", 0);
    }

    private static boolean configureController(
        RoomControllerBlockEntity controller,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (!controller.bindDungeonInstance(instanceKey)) {
            return false;
        }
        controller.setRoomProfile(RoomProfile.byName(roomPlan.effectiveProfile()));
        boolean objectiveDisabled = "NONE".equalsIgnoreCase(roomPlan.objectiveType());
        controller.setConfiguredObjectiveType(
            objectiveDisabled ? null : RoomObjectiveType.byName(roomPlan.objectiveType())
        );
        controller.setObjectiveDisabled(objectiveDisabled);
        return true;
    }

    private static void clearPreClaimMarker(ServerLevel level, @Nullable BlockPos markerPos) {
        if (markerPos != null && level.getBlockState(markerPos).is(ModBlocks.FLOOR_BUILD_ORIGIN.get())) {
            level.setBlock(markerPos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static SealResult sealRequiredOutgoingConnectors(
        ServerLevel level,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        RoomControllerBlockEntity controller,
        Map<String, List<BlockPos>> markerPositions,
        SealPolicy policy
    ) {
        List<ConnectorOpening> outwardOpenings =
            collectConnectors(markerPositions, roomPlan.bounds());
        String mode;
        if (policy == SealPolicy.INITIAL_PRESERVE_INCOMING_FACE) {
            Direction incomingFacing = parseDirection(roomPlan.incomingConnectorFacing());
            BlockPos incomingConnector = roomPlan.incomingConnectorPos();
            outwardOpenings.removeIf(opening -> opening.worldPos().equals(incomingConnector)
                || opening.facing() == incomingFacing);
            mode = "INITIAL_CONNECTOR";
        } else {
            if (roomPlan.incomingConnectorPos() == null) {
                outwardOpenings.clear();
            } else {
                outwardOpenings.removeIf(opening ->
                    opening.worldPos().equals(roomPlan.incomingConnectorPos())
                );
            }
            if (outwardOpenings.stream().anyMatch(ConnectorOpening::outgoingPreferred)) {
                outwardOpenings.removeIf(opening -> !opening.outgoingPreferred());
            }
            mode = "RUNTIME_CONNECTOR";
            if (outwardOpenings.isEmpty()) {
                if (roomPlan.outgoingConnectorPos() == null) {
                    return SealResult.success("NO_OUTGOING_CONNECTOR", 0);
                }
                Direction plannedFacing = parseDirection(roomPlan.outgoingConnectorFacing());
                if (!isVerifiedPlannedConnector(roomPlan, plannedFacing)) {
                    return SealResult.failed(
                        roomPlan.outgoingConnectorPos(),
                        "PLANNED_CONNECTOR_INVALID_POSITION_OR_FACING"
                    );
                }
                outwardOpenings = new ArrayList<>(List.of(new ConnectorOpening(
                    roomPlan.outgoingConnectorPos(), plannedFacing, true
                )));
                mode = markerPositions.isEmpty()
                    ? "VERIFIED_PLANNED_RECOVERY" : "VERIFIED_PLANNED_CONNECTOR";
            }
        }

        int placed = 0;
        SealResult firstFailure = null;
        for (ConnectorOpening opening : outwardOpenings) {
            if (!controller.placeTemporarySeal(level, opening.worldPos(), true)) {
                LOGGER.error(
                    "Generated outgoing seal placement failed: template={} controller={} connector={} facing={} bounds={} mode={}",
                    roomPlan.templateLabel(), controller.getBlockPos(), opening.worldPos(),
                    opening.facing(), roomPlan.bounds(), mode
                );
                SealResult failure = SealResult.failed(opening.worldPos(), mode + "_SEAL_FAILED");
                if (policy == SealPolicy.RUNTIME_OUTGOING) {
                    return failure;
                }
                if (firstFailure == null) firstFailure = failure;
                continue;
            }
            placed++;
        }
        if (firstFailure != null) {
            return firstFailure;
        }
        return SealResult.success(mode + "_SEALED_" + placed, placed);
    }

    private static List<ConnectorOpening> collectConnectors(
        Map<String, List<BlockPos>> markerPositions,
        RoomControllerBlockEntity.RoomBounds bounds
    ) {
        List<ConnectorOpening> connectors = new ArrayList<>();
        addConnectorPositions(
            connectors,
            markerPositions.getOrDefault("floor_connector_anchor", List.of()),
            bounds,
            false
        );
        addConnectorPositions(
            connectors,
            markerPositions.getOrDefault("connector_exit", List.of()),
            bounds,
            true
        );
        if (connectors.isEmpty()) {
            addConnectorPositions(
                connectors,
                markerPositions.getOrDefault("floor_entry_anchor", List.of()),
                bounds,
                false
            );
            addConnectorPositions(
                connectors,
                markerPositions.getOrDefault("exit_anchor", List.of()),
                bounds,
                false
            );
        }
        return connectors;
    }

    private static void addConnectorPositions(
        List<ConnectorOpening> connectors,
        List<BlockPos> positions,
        RoomControllerBlockEntity.RoomBounds bounds,
        boolean outgoingPreferred
    ) {
        for (BlockPos position : positions) {
            connectors.add(new ConnectorOpening(
                position.immutable(),
                inferHorizontalFacing(position, bounds),
                outgoingPreferred
            ));
        }
    }

    private static Direction inferHorizontalFacing(
        BlockPos pos,
        RoomControllerBlockEntity.RoomBounds bounds
    ) {
        int west = Math.abs(pos.getX() - bounds.min().getX());
        int east = Math.abs(pos.getX() - bounds.max().getX());
        int north = Math.abs(pos.getZ() - bounds.min().getZ());
        int south = Math.abs(pos.getZ() - bounds.max().getZ());
        int best = Math.min(Math.min(west, east), Math.min(north, south));
        if (west == best) return Direction.WEST;
        if (east == best) return Direction.EAST;
        return north == best ? Direction.NORTH : Direction.SOUTH;
    }

    private static boolean isVerifiedPlannedConnector(
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        @Nullable Direction facing
    ) {
        BlockPos connector = roomPlan.outgoingConnectorPos();
        RoomControllerBlockEntity.RoomBounds bounds = roomPlan.bounds();
        if (connector == null || bounds == null || facing == null || !facing.getAxis().isHorizontal()
            || !bounds.contains(connector)) {
            return false;
        }
        return switch (facing) {
            case WEST -> connector.getX() == bounds.min().getX();
            case EAST -> connector.getX() == bounds.max().getX();
            case NORTH -> connector.getZ() == bounds.min().getZ();
            case SOUTH -> connector.getZ() == bounds.max().getZ();
            default -> false;
        };
    }

    private static Map<String, List<BlockPos>> worldMarkerPositions(
        ServerLevel level,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        Rotation rotation
    ) {
        DungeonTemplateMechanicalMetadata metadata =
            GeneratedRoomIncrementalPlacementEngine.loadMetadata(level, roomPlan);
        if (metadata == null) return Map.of();
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(rotation);
        Map<String, List<BlockPos>> transformed = new LinkedHashMap<>();
        metadata.markerPositions().forEach((name, localPositions) -> transformed.put(
            name,
            localPositions.stream().map(local -> roomPlan.placementOrigin().offset(
                StructureTemplate.calculateRelativePosition(settings, local)
            ).immutable()).toList()
        ));
        return Map.copyOf(transformed);
    }

    private static Optional<StructureTemplate> loadTemplate(
        ServerLevel level,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        @Nullable StructureTemplate preloadedTemplate
    ) {
        if (preloadedTemplate != null) {
            return Optional.of(preloadedTemplate);
        }
        if (CompositeTemplateDescriptor.isCompositeId(roomPlan.templateLabel())) {
            CompositeRoomRuntimeRegistry.Resolution composite =
                CompositeRoomRuntimeRegistry.resolve(level, roomPlan.templateLabel());
            return composite.success() && composite.template() != null
                ? Optional.of(composite.template()) : Optional.empty();
        }
        return level.getStructureManager().get(templateId(roomPlan));
    }

    private static ResourceLocation templateId(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan) {
        return ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID,
            "rooms/linear/" + roomPlan.templateLabel()
        );
    }

    private static boolean placementContainsBounds(
        GeneratedFloorSession session,
        RoomControllerBlockEntity.RoomBounds bounds
    ) {
        return session.placement() != null && session.placement().bounds().contains(bounds);
    }

    private static void appendPlacedRoom(
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan
    ) {
        session.appendPlacedRoom(roomPlan);
        repository.flushPlacement(session);
    }

    private static void appendPlacedBounds(
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan
    ) {
        session.appendPlacedBounds(roomPlan);
        repository.flushPlacement(session);
    }

    @Nullable
    private static Rotation parseRotation(String name) {
        try {
            return Rotation.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Nullable
    private static Direction parseDirection(String name) {
        if (name == null || name.isBlank() || "NONE".equalsIgnoreCase(name)) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            if (direction.name().equalsIgnoreCase(name)) {
                return direction;
            }
        }
        return null;
    }

    private static double elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0D;
    }

    public enum SealPolicy {
        INITIAL_PRESERVE_INCOMING_FACE,
        RUNTIME_OUTGOING
    }

    public enum ControllerDiscoveryPolicy {
        EXPECTED_POSITION
    }

    public record SliceBudget(
        long maxNanos,
        int maxBlockOperations,
        int maxFluidOperations,
        int maxChunkSyncs
    ) {
        public SliceBudget {
            if (maxNanos <= 0L || maxBlockOperations <= 0
                || maxFluidOperations <= 0 || maxChunkSyncs <= 0) {
                throw new IllegalArgumentException("placement slice limits must be positive");
            }
        }

        public static SliceBudget unbounded() {
            return new SliceBudget(
                Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE
            );
        }
    }

    public record Request(
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        @Nullable StructureTemplate preloadedTemplate,
        int claimRadius,
        boolean branchPreview,
        SealPolicy sealPolicy,
        ControllerDiscoveryPolicy controllerDiscovery,
        boolean allowExistingController,
        boolean persistBoundsBeforeMutation,
        boolean persistControllerOnReadinessFailure,
        boolean persistControllerOnSuccess,
        @Nullable BlockPos preClaimMarkerToClear
    ) {
        public Request {
            if (claimRadius < 1) {
                throw new IllegalArgumentException("claimRadius must be at least 1");
            }
            if (sealPolicy == null || controllerDiscovery == null) {
                throw new IllegalArgumentException("placement policies are required");
            }
        }
    }

    public record Result(
        boolean placed,
        boolean failed,
        boolean inProgress,
        boolean existingController,
        int roomIndex,
        @Nullable BlockPos controllerPos,
        String reason,
        int removedPlacementDrops,
        @Nullable GeneratedRoomBoundsWriter.ClaimResult claimResult,
        @Nullable SealResult sealResult,
        Metrics metrics
    ) {
        private static Result placed(
            int roomIndex,
            RoomControllerBlockEntity controller,
            int removedPlacementDrops,
            @Nullable GeneratedRoomBoundsWriter.ClaimResult claimResult,
            SealResult sealResult,
            Metrics metrics,
            boolean existingController
        ) {
            return new Result(
                true, false, false, existingController, roomIndex,
                controller.getBlockPos().immutable(),
                existingController ? "EXISTING_CONTROLLER_REGISTERED" : "PLACED",
                removedPlacementDrops, claimResult, sealResult, metrics
            );
        }

        private static Result inProgress(int roomIndex, String reason, Metrics metrics) {
            return new Result(
                false, false, true, false, roomIndex, null, reason,
                0, null, null, metrics
            );
        }

        private static Result failed(int roomIndex, String reason, @Nullable RoomControllerBlockEntity controller, Metrics metrics) {
            return failed(roomIndex, reason, controller, metrics, 0, null, null);
        }

        private static Result failed(
            int roomIndex,
            String reason,
            @Nullable RoomControllerBlockEntity controller,
            Metrics metrics,
            int removedPlacementDrops,
            @Nullable GeneratedRoomBoundsWriter.ClaimResult claimResult,
            @Nullable SealResult sealResult
        ) {
            return new Result(
                false, true, false, false, roomIndex,
                controller == null ? null : controller.getBlockPos().immutable(), reason,
                removedPlacementDrops, claimResult, sealResult, metrics
            );
        }
    }

    public record SealResult(
        boolean success,
        @Nullable BlockPos connectorPos,
        String reason,
        int placedCount
    ) {
        private static SealResult success(String reason, int placedCount) {
            return new SealResult(true, null, reason, placedCount);
        }

        private static SealResult failed(@Nullable BlockPos connectorPos, String reason) {
            return new SealResult(false, connectorPos == null ? null : connectorPos.immutable(), reason, 0);
        }
    }

    public record Metrics(
        double templateLoadMs,
        double structurePlaceMs,
        double itemCleanupMs,
        double controllerLookupMs,
        double configurationMs,
        double boundsWriteMs,
        double markerClaimMs,
        double readinessMs,
        double postProcessMs,
        double persistenceMs,
        double totalMs
    ) {
        private static Metrics empty(double totalMs) {
            return new Metrics(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                totalMs);
        }
    }

    private record ConnectorOpening(BlockPos worldPos, Direction facing, boolean outgoingPreferred) {
    }

    private record ControllerDiscovery(
        @Nullable RoomControllerBlockEntity controller,
        int count,
        String reason
    ) {
        private static ControllerDiscovery found(RoomControllerBlockEntity controller, int count) {
            return new ControllerDiscovery(controller, count, "FOUND");
        }

        private static ControllerDiscovery missing(String reason, int count) {
            return new ControllerDiscovery(null, count, reason);
        }
    }
}
