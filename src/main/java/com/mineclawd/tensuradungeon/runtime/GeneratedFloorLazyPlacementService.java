package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeRoomRuntimeRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeTemplateDescriptor;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class GeneratedFloorLazyPlacementService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_AUTOMATIC_GENERATION_FAILURE_ATTEMPTS = 3;
    private static final long SEAL_REPAIR_INTERVAL_TICKS = 20L;
    private static final String TERMINAL_OUTGOING_SEAL_FAILED = "TERMINAL_OUTGOING_SEAL_FAILED";
    private static final String REAR_CONNECTOR_SEAL_FAILED = "REAR_CONNECTOR_SEAL_FAILED";
    private static final String GENERATED_OUTGOING_SEAL_FAILED = "GENERATED_OUTGOING_SEAL_FAILED";
    private static final String GENERATION_CONTRACT_MISMATCH = "GENERATION_CONTRACT_MISMATCH";
    static final String GENERATED_ROOM_PLACEMENT_FAILED = "GENERATED_ROOM_PLACEMENT_FAILED";
    static final String GENERATED_ROOM_PLACEMENT_FALLBACK_PENDING =
        "GENERATED_ROOM_PLACEMENT_FALLBACK_PENDING";
    static final String GENERATED_ROOM_PLACEMENT_FALLBACK_ROUTED =
        "GENERATED_ROOM_PLACEMENT_FALLBACK_ROUTED";
    private GeneratedFloorLazyPlacementService() {
    }

    public static void processPendingSealRepair(ServerLevel level) {
        if (level.getGameTime() % SEAL_REPAIR_INTERVAL_TICKS != 0L) return;
        for (GeneratedFloorRepository repository : GeneratedFloorRepository.all(level)) {
            processPendingSealRepair(level, repository);
        }
    }

    private static void processPendingSealRepair(
        ServerLevel level,
        GeneratedFloorRepository repository
    ) {
        GeneratedFloorSession session = repository.hydrate();
        if (repository.scopeIdentity() != null && session.instanceKey() == null) return;
        GeneratedFloorRuntimeData.GenerationFailureState failure = session.generationFailure();
        if (!isSealRepairFailure(failure)) return;
        if (generationContractFailure(
            level, repository, session, failure.controllerPos(), level.dimension().location().toString()
        ) != null) return;
        retryPendingSealRepair(level, repository, session, failure);
    }

    public static void validateResumedGenerationContract(ServerLevel level) {
        DungeonRunManager manager = DungeonRunManager.get(level);
        DungeonInstanceAllocationData.InstanceKey previousKey = manager.getInstanceKey();
        try {
            for (GeneratedFloorRepository repository : GeneratedFloorRepository.all(level)) {
                GeneratedFloorSession session = repository.hydrate();
                DungeonInstanceAllocationData.InstanceKey instanceKey = repository.scopeIdentity();
                if (instanceKey != null && session.instanceKey() == null) continue;
                if (instanceKey != null
                    && (!manager.canBindInstance(instanceKey)
                        || !manager.bindInstance(instanceKey))) {
                    continue;
                }
                validateResumedGenerationContract(level, manager, repository, session);
            }
        } finally {
            if (previousKey != null && manager.canBindInstance(previousKey)) {
                manager.bindInstance(previousKey);
            }
        }
    }

    private static void validateResumedGenerationContract(
        ServerLevel level,
        DungeonRunManager manager,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session
    ) {
        if (!manager.isActiveRun() || manager.isRunCompleted()) return;
        if (session.placement() == null || session.compositionState() == null) {
            return;
        }

        long started = System.nanoTime();
        PlacementResult failure = generationContractFailure(
            level,
            repository,
            session,
            manager.getActiveRoomControllerPositions().stream()
                .min(java.util.Comparator.comparingLong(BlockPos::asLong))
                .orElse(null),
            level.dimension().location().toString()
        );
        LOGGER.info(
            "[TD-PERF] resumed-generation-contract result={} reason={} elapsedMs={} dimension={}",
            failure == null ? "PASS" : "FAIL",
            failure == null ? "NONE" : failure.reason(),
            elapsedMillis(started),
            level.dimension().location()
        );
    }

    public static PlacementResult ensureLookahead(ServerLevel level, BlockPos activatedControllerPos) {
        return ensureLookahead(
            level,
            GeneratedFloorRepository.get(level),
            activatedControllerPos,
            GeneratedRoomPlacementOperation.SliceBudget.unbounded()
        );
    }

    public static PlacementResult ensureLookahead(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos activatedControllerPos
    ) {
        return ensureLookahead(
            level,
            instanceKey,
            activatedControllerPos,
            GeneratedRoomPlacementOperation.SliceBudget.unbounded()
        );
    }

    public static PlacementResult ensureLookahead(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos activatedControllerPos,
        GeneratedRoomPlacementOperation.SliceBudget sliceBudget
    ) {
        return ensureLookahead(
            level,
            GeneratedFloorRepository.get(
                level, level.dimension().location().toString(), instanceKey
            ),
            activatedControllerPos,
            sliceBudget
        );
    }

    private static PlacementResult ensureLookahead(
        ServerLevel level,
        GeneratedFloorRepository repository,
        BlockPos activatedControllerPos,
        GeneratedRoomPlacementOperation.SliceBudget sliceBudget
    ) {
        GeneratedFloorSession session = repository.hydrate();
        String dimensionKey = level.dimension().location().toString();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || placement.roomPlans().isEmpty()) {
            return PlacementResult.skipped("NO_GENERATED_PLAN");
        }
        GeneratedFloorRuntimeData.GenerationFailureState persistedFailure = session.generationFailure();
        if (persistedFailure != null && !persistedFailure.retryable()
            && !isFallbackRoutedFailure(persistedFailure)) {
            return blockedByGenerationFailure(persistedFailure);
        }
        PlacementResult contractFailure = generationContractFailure(
            level, repository, session, activatedControllerPos, dimensionKey
        );
        if (contractFailure != null) return contractFailure;
        GeneratedFloorRuntimeData.GenerationFailureState existingFailure = session.generationFailure();
        if (isSealRepairFailure(existingFailure)
            && !retryPendingSealRepair(level, repository, session, existingFailure)) {
            return blockedByGenerationFailure(session.generationFailure());
        }
        placement = session.placement();
        if (isRollingExpansionFailure(existingFailure) && !existingFailure.retryable()) {
            return blockedByGenerationFailure(existingFailure);
        }

        DungeonInstanceAllocationData.Allocation allocation = session.instanceKey() == null
            ? null
            : DungeonInstanceAllocationData.get(level)
                .forIdentity(session.instanceKey()).orElse(null);
        boolean startupLookahead = allocation != null
            && allocation.lifecycle().state()
                == DungeonInstanceAllocationData.LifecycleState.PREPARING
            && placement.controllers().size() < 2;
        if (!startupLookahead) {
            placement = expandRollingPlansIfNeeded(
                level, repository, session, dimensionKey, placement, activatedControllerPos
            );
        }
        GeneratedFloorRuntimeData.GenerationFailureState expansionFailure = session.generationFailure();
        if (isRollingExpansionFailure(expansionFailure)) {
            return blockedByGenerationFailure(expansionFailure);
        }
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans = placement.roomPlans();
        int activatedIndex = -1;
        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i).controllerPos().equals(activatedControllerPos)) {
                activatedIndex = i;
                break;
            }
        }
        if (activatedIndex < 0) {
            return PlacementResult.skipped("ACTIVATED_CONTROLLER_NOT_IN_GENERATED_PLAN");
        }

        int lookaheadIndex = findLookaheadIndex(level, session, placement, activatedIndex);
        if (lookaheadIndex < 0) {
            return PlacementResult.skipped("ALL_LOOKAHEAD_ROOMS_PLACED");
        }
        if (lookaheadIndex > 0 && plans.get(lookaheadIndex - 1).supportsPlayerDirectedExit()) {
            return PlacementResult.skipped("WAITING_FOR_MULTIWAY_ROOM_COMPLETION");
        }
        return placePlannedRoom(
            level,
            repository,
            session,
            dimensionKey,
            placement,
            plans.get(lookaheadIndex),
            false,
            sliceBudget
        );
    }

    static boolean needsLookahead(ServerLevel level, BlockPos activatedControllerPos) {
        GeneratedFloorSession session = GeneratedFloorRepository.get(level).hydrate();
        return needsLookahead(level, session, activatedControllerPos);
    }

    static boolean needsLookahead(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos activatedControllerPos
    ) {
        GeneratedFloorSession session = GeneratedFloorRepository.get(
            level, level.dimension().location().toString(), instanceKey
        ).hydrate();
        return needsLookahead(level, session, activatedControllerPos);
    }

    private static boolean needsLookahead(
        ServerLevel level,
        GeneratedFloorSession session,
        BlockPos activatedControllerPos
    ) {
        GeneratedFloorRuntimeData.GenerationFailureState failure = session.generationFailure();
        if (failure != null && !failure.retryable() && !isFallbackRoutedFailure(failure)) {
            return false;
        }
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || placement.roomPlans().isEmpty()) return false;
        int activatedIndex = findPlanIndex(placement.roomPlans(), activatedControllerPos);
        if (activatedIndex < 0) return false;
        if (findLookaheadIndex(level, session, placement, activatedIndex) >= 0) return true;
        var composition = session.compositionState();
        return composition != null && !composition.complete()
            && placement.roomPlans().size() - activatedIndex <= 2;
    }

    static GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan nextPreparedLookaheadPlan(
        ServerLevel level,
        BlockPos activatedControllerPos
    ) {
        GeneratedFloorSession session = GeneratedFloorRepository.get(level).hydrate();
        return nextPreparedLookaheadPlan(level, session, activatedControllerPos);
    }

    static GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan nextPreparedLookaheadPlan(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos activatedControllerPos
    ) {
        GeneratedFloorSession session = GeneratedFloorRepository.get(
            level, level.dimension().location().toString(), instanceKey
        ).hydrate();
        return nextPreparedLookaheadPlan(level, session, activatedControllerPos);
    }

    private static GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan nextPreparedLookaheadPlan(
        ServerLevel level,
        GeneratedFloorSession session,
        BlockPos activatedControllerPos
    ) {
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || placement.roomPlans().isEmpty()) return null;
        int activatedIndex = findPlanIndex(placement.roomPlans(), activatedControllerPos);
        if (activatedIndex < 0) return null;
        int lookaheadIndex = findLookaheadIndex(level, session, placement, activatedIndex);
        return lookaheadIndex < 0 ? null : placement.roomPlans().get(lookaheadIndex);
    }

    private static int findLookaheadIndex(
        ServerLevel level,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        int activatedIndex
    ) {
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans = placement.roomPlans();
        int maxLookaheadIndex = Math.min(plans.size() - 1, activatedIndex + 2);
        for (int i = 0; i <= maxLookaheadIndex; i++) {
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan = plans.get(i);
            if (isFallbackRoutedPlan(session, plan)) {
                continue;
            }
            if (!placement.controllers().contains(plan.controllerPos())) {
                return i;
            }
            if (i >= activatedIndex && plan.hasPlacementMetadata()
                && GeneratedRoomPlacementOperation.existingControllerValidationFailure(
                    level, session, plan, false
                ) != null) {
                return i;
            }
        }
        return -1;
    }

    private static GeneratedFloorRuntimeData.LastGeneratedPlacement expandRollingPlansIfNeeded(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        String dimensionKey,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        BlockPos activatedControllerPos
    ) {
        var state = session.compositionState();
        if (state == null || state.complete()) return placement;
        int activatedIndex = findPlanIndex(placement.roomPlans(), activatedControllerPos);
        if (activatedIndex < 0 || placement.roomPlans().size() - activatedIndex > 2 || placement.roomPlans().isEmpty()) return placement;
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan tail = placement.roomPlans().get(placement.roomPlans().size() - 1);
        Direction facing = parseDirection(tail.outgoingConnectorFacing());
        if (facing == null || tail.outgoingConnectorPos() == null) return placement;

        BlockPos start = tail.outgoingConnectorPos().relative(facing);
        var authored = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry.get(level);
        var mechanical = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry.getForTheme(
            level, authored, placement.visualTheme()
        );
        List<com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds> reserved = buildExpansionReservations(level, placement);
        List<com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds> protectedBounds = buildProtectedReservations(level);
        int maximum = Math.min(com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.DEFAULT_HORIZON,
            state.beats().size() - state.cursor());
        var selectionContext = new com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.SelectionContext(
            state.roomCount(), placement.roomPlans().stream()
                .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::templateLabel).toList(),
            Set.of(), Map.of(), placement.visualTheme()
        );
        com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Result acceptedSolve = null;
        com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonPlanAdapter.Result acceptedAdaptation = null;
        int acceptedSize = 0;
        int acceptedDeferralOffset = 0;
        for (int size = maximum; size >= 1; size--) {
            long attemptStarted = System.nanoTime();
            var solved = com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.solveWindow(
                state.nextBeats(size), authored, mechanical, start, facing,
                level.getMinBuildHeight(), level.getMaxBuildHeight(),
                state.seed() ^ (0x9E3779B97F4A7C15L * state.cursor()), reserved, protectedBounds,
                state.cursor() + size < state.beats().size(), selectionContext
            );
            com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonPlanAdapter.Result adapted = null;
            if (solved.solved()) adapted = com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonPlanAdapter.adapt(
                level, solved, mechanical
            );
            boolean passed = solved.solved() && adapted != null && adapted.valid() && adapted.plans().size() == size;
            LOGGER.info("[TD-PERF] rolling-solve cursor={} requestedSize={} result={} successfulSize={} deepestDepth={} visitedNodes={} backtracks={} elapsedMs={} rejections={} startConnector={} facing={} reservedBounds={} straightRescueAttempted=false adapterErrors={} rejectionDetails={} dimension={}",
                state.cursor(), size, passed ? "PASS" : "FAIL", passed ? size : 0, solved.deepestDepth(),
                solved.visitedNodes(), solved.backtracks(), (System.nanoTime() - attemptStarted) / 1_000_000.0D,
                solved.rejectionCounts(), start, facing, reserved.size(), adapted == null ? List.of() : adapted.errors(), solved.rejectionDetails(), dimensionKey);
            if (passed) {
                acceptedSolve = solved;
                acceptedAdaptation = adapted;
                acceptedSize = size;
                break;
            }
        }
        int maximumDeferral = Math.min(3, state.beats().size() - state.cursor() - 1);
        for (int offset = 1; acceptedSolve == null && offset <= maximumDeferral; offset++) {
            var deferredBeats = state.deferredNextBeats(offset, 1);
            if (deferredBeats.isEmpty()) continue;
            var solved = com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.solveWindow(
                deferredBeats, authored, mechanical, start, facing,
                level.getMinBuildHeight(), level.getMaxBuildHeight(),
                state.seed() ^ (0xD1B54A32D192ED03L * (state.cursor() + offset)), reserved, protectedBounds,
                state.cursor() + 1 < state.beats().size(), selectionContext
            );
            var adapted = solved.solved()
                ? com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonPlanAdapter.adapt(level, solved, mechanical) : null;
            boolean passed = solved.solved() && adapted != null && adapted.valid() && adapted.plans().size() == 1;
            LOGGER.info("Rolling beat deferral attempt: cursor={} offset={} deferredRole={} candidateRole={} result={} deepestDepth={} rejections={} dimension={}",
                state.cursor(), offset, state.nextBeats(1).getFirst().role(), deferredBeats.getFirst().role(),
                passed ? "PASS" : "FAIL", solved.deepestDepth(), solved.rejectionCounts(), dimensionKey);
            if (passed) {
                acceptedSolve = solved;
                acceptedAdaptation = adapted;
                acceptedSize = 1;
                acceptedDeferralOffset = offset;
            }
        }
        if (acceptedSolve == null || acceptedAdaptation == null) {
            LOGGER.error("Rolling horizon expansion failed after fallback and beat deferral: cursor={} requestedMaximum={} startConnector={} facing={} reservedBounds={} straightRescueAttempted=false dimension={}",
                state.cursor(), maximum, start, facing, reserved.size(), dimensionKey);
            recordGenerationFailure(
                level, repository, session, "ROLLING_EXPANSION_EXHAUSTED",
                activatedControllerPos, start,
                "cursor=" + state.cursor() + " requestedMaximum=" + maximum + " reservedBounds=" + reserved.size()
            );
            return placement;
        }

        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> appended = acceptedAdaptation.plans();
        List<BlockPos> appendedControllers = appended.stream()
            .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::controllerPos).toList();
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> merged = new ArrayList<>(placement.roomPlans());
        merged.addAll(appended);
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (!isExpansionCommitValid(manager, placement, state.cursor(), acceptedSize, appendedControllers)) {
            LOGGER.error("Rolling expansion proposal rejected before commit: cursor={} size={} dimension={}", state.cursor(), acceptedSize, dimensionKey);
            recordGenerationFailure(
                level, repository, session, "ROLLING_EXPANSION_PREFLIGHT_REJECTED",
                activatedControllerPos, start,
                "cursor=" + state.cursor() + " acceptedSize=" + acceptedSize + " appendedControllers=" + appendedControllers.size()
            );
            return placement;
        }
        GeneratedFloorSession.RollingExpansion committed = session.commitRollingExpansion(
            state.cursor(), acceptedDeferralOffset, appended
        );
        if (committed == null) {
            LOGGER.error("Rolling horizon composition commit rejected before plan append: cursor={} deferralOffset={} dimension={}",
                state.cursor(), acceptedDeferralOffset, dimensionKey);
            recordGenerationFailure(
                level, repository, session, "ROLLING_EXPANSION_COMMIT_REJECTED",
                activatedControllerPos, start,
                "cursor=" + state.cursor() + " deferralOffset=" + acceptedDeferralOffset
            );
            return placement;
        }
        repository.flushComposition(session);
        repository.flushPlacement(session);
        if (manager.isActiveRun() && !manager.appendRollingPresetOrder(appendedControllers)) {
            throw new IllegalStateException("Preflighted rolling preset append unexpectedly failed");
        }
        clearGenerationFailure(repository, session,
            "ROLLING_EXPANSION_EXHAUSTED", "ROLLING_EXPANSION_PREFLIGHT_REJECTED", "ROLLING_EXPANSION_COMMIT_REJECTED");
        LOGGER.info("Rolling horizon appended: cursor={} requestedMaximum={} added={} deferralOffset={} straightRescueUsed=false totalPlans={} targetRooms={} dimension={}",
            state.cursor(), maximum, acceptedSize, acceptedDeferralOffset, merged.size(), state.roomCount(), dimensionKey);
        return committed.placement();
    }

    private static boolean isRollingExpansionFailure(GeneratedFloorRuntimeData.GenerationFailureState failure) {
        return failure != null && failure.code().startsWith("ROLLING_EXPANSION_");
    }

    static boolean isFallbackRoutedFailure(
        @Nullable GeneratedFloorRuntimeData.GenerationFailureState failure
    ) {
        return failure != null && GENERATED_ROOM_PLACEMENT_FALLBACK_ROUTED.equals(failure.code());
    }

    private static boolean isFallbackRoutedPlan(
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan
    ) {
        GeneratedFloorRuntimeData.GenerationFailureState failure = session.generationFailure();
        return isFallbackRoutedFailure(failure)
            && failure.controllerPos() != null
            && failure.controllerPos().equals(plan.controllerPos());
    }

    private static boolean isSealRepairFailure(GeneratedFloorRuntimeData.GenerationFailureState failure) {
        return failure != null && (TERMINAL_OUTGOING_SEAL_FAILED.equals(failure.code())
            || REAR_CONNECTOR_SEAL_FAILED.equals(failure.code())
            || GENERATED_OUTGOING_SEAL_FAILED.equals(failure.code()));
    }

    private static boolean attemptPendingSealRepair(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.GenerationFailureState failure
    ) {
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || failure.controllerPos() == null) return false;
        if (!(level.getBlockEntity(failure.controllerPos()) instanceof RoomControllerBlockEntity controller)) return false;
        boolean repaired;
        if (TERMINAL_OUTGOING_SEAL_FAILED.equals(failure.code())) {
            repaired = retainTerminalOutgoingSeal(level, failure.controllerPos());
        } else if (REAR_CONNECTOR_SEAL_FAILED.equals(failure.code())) {
            repaired = failure.connectorPos() != null
                && controller.placeTemporarySeal(level, failure.connectorPos(), true);
        } else if (GENERATED_OUTGOING_SEAL_FAILED.equals(failure.code())) {
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan = placement.roomPlans().stream()
                .filter(candidate -> candidate.controllerPos().equals(failure.controllerPos()))
                .findFirst().orElse(null);
            if (plan == null) return false;
            GeneratedRoomPlacementOperation.SealResult result =
                GeneratedRoomPlacementOperation.repairRuntimeOutgoingSeals(level, plan, controller);
            repaired = result.success();
            if (repaired && !session.placement().controllers().contains(plan.controllerPos())) {
                session.appendPlacedRoom(plan);
                repository.flushPlacement(session);
            }
        } else {
            return false;
        }
        if (!repaired) return false;
        clearGenerationFailure(repository, session, failure.code());
        LOGGER.info("Generated seal repair completed: code={} controller={} connector={} dimension={}",
            failure.code(), failure.controllerPos(), failure.connectorPos(), level.dimension().location());
        return true;
    }

    private static boolean retryPendingSealRepair(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.GenerationFailureState failure
    ) {
        if (!failure.retryable()) return false;
        if (attemptPendingSealRepair(level, repository, session, failure)) return true;
        recordGenerationFailure(
            level, repository, session, failure.code(), failure.controllerPos(),
            failure.connectorPos(), failure.detail()
        );
        return false;
    }

    public static PlacementResult validateReadyDispatchContract(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        @Nullable BlockPos controllerPos
    ) {
        GeneratedFloorRepository repository = GeneratedFloorRepository.get(
            level, level.dimension().location().toString(), instanceKey
        );
        GeneratedFloorSession session = repository.hydrate();
        PlacementResult failure = generationContractFailure(
            level, repository, session, controllerPos,
            level.dimension().location().toString()
        );
        return failure == null
            ? PlacementResult.skipped("GENERATION_CONTRACT_VALID")
            : failure;
    }

    public static boolean hasPermanentGenerationFailure(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        GeneratedFloorRuntimeData.GenerationFailureState failure =
            GeneratedFloorRepository.get(
                level, level.dimension().location().toString(), instanceKey
            ).hydrate().generationFailure();
        return failure != null && !failure.retryable();
    }

    private static PlacementResult blockedByGenerationFailure(GeneratedFloorRuntimeData.GenerationFailureState failure) {
        return PlacementResult.failed(
            0,
            "GENERATION_BLOCKED_" + failure.code() + "_ATTEMPT_" + failure.attempts()
        );
    }

    @Nullable
    private static PlacementResult generationContractFailure(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        @Nullable BlockPos controllerPos,
        String dimensionKey
    ) {
        var compositionState = session.compositionState();
        if (compositionState == null) return null;
        var authoredPool = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry.get(level);
        var placement = session.placement();
        var mechanicalPool = placement != null && !placement.visualTheme().isBlank()
            ? com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry.getForTheme(
                level, authoredPool, placement.visualTheme()
            )
            : com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry.getForTemplateIds(
                level, authoredPool, placement == null ? List.of() : placement.roomPlans().stream()
                    .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::templateLabel).toList()
            );
        var currentContract = com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver
            .currentGenerationContract(authoredPool, mechanicalPool);
        if (!compositionState.hasGenerationContract()) {
            session.bindLegacyGenerationContract(currentContract);
            repository.flushComposition(session);
            return null;
        }
        if (compositionState.matchesGenerationContract(currentContract)) {
            clearGenerationFailure(repository, session, GENERATION_CONTRACT_MISMATCH);
            return null;
        }
        GeneratedFloorRuntimeData.GenerationFailureState mismatch = recordGenerationFailure(
            level, repository, session, GENERATION_CONTRACT_MISMATCH,
            controllerPos, null,
            "expectedVersion=" + compositionState.generatorVersion()
                + " currentVersion=" + currentContract.generatorVersion()
                + " expectedPool=" + compositionState.eligibleTemplatePoolFingerprint()
                + " currentPool=" + currentContract.eligibleTemplatePoolFingerprint()
        );
        return blockedByGenerationFailure(mismatch);
    }

    private static GeneratedFloorRuntimeData.GenerationFailureState recordGenerationFailure(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        String code,
        @Nullable BlockPos controllerPos,
        @Nullable BlockPos connectorPos,
        String detail
    ) {
        long gameTime = level.getGameTime();
        GeneratedFloorRuntimeData.GenerationFailureState existing = session.generationFailure();
        boolean sameFailure = existing != null && existing.code().equals(code)
            && java.util.Objects.equals(existing.controllerPos(), controllerPos)
            && java.util.Objects.equals(existing.connectorPos(), connectorPos);
        int attempts = sameFailure ? existing.attempts() + 1 : 1;
        boolean retryable = automaticRetryAllowed(code, attempts);
        GeneratedFloorRuntimeData.GenerationFailureState failure = new GeneratedFloorRuntimeData.GenerationFailureState(
            code,
            retryable,
            attempts,
            sameFailure ? existing.firstGameTime() : gameTime,
            gameTime,
            controllerPos,
            connectorPos,
            detail
        );
        session.replaceGenerationFailure(failure);
        repository.flushGenerationFailure(session);
        LOGGER.error("Generated progression blocked: code={} retryable={} attempts={} controller={} connector={} detail={} dimension={}",
            failure.code(), failure.retryable(), failure.attempts(), failure.controllerPos(), failure.connectorPos(),
            failure.detail(), level.dimension().location());
        return failure;
    }

    static boolean automaticRetryAllowed(String code, int attempts) {
        if (GENERATION_CONTRACT_MISMATCH.equals(code)) return false;
        if (code != null && (code.startsWith("ROLLING_EXPANSION_")
            || TERMINAL_OUTGOING_SEAL_FAILED.equals(code)
            || REAR_CONNECTOR_SEAL_FAILED.equals(code)
            || GENERATED_OUTGOING_SEAL_FAILED.equals(code))) {
            return attempts < MAX_AUTOMATIC_GENERATION_FAILURE_ATTEMPTS;
        }
        return false;
    }

    private static void clearGenerationFailure(
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        String... codes
    ) {
        GeneratedFloorRuntimeData.GenerationFailureState failure = session.generationFailure();
        if (failure == null || java.util.Arrays.stream(codes).noneMatch(failure.code()::equals)) return;
        session.clearGenerationFailure();
        repository.flushGenerationFailure(session);
    }

    private static List<com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds> buildExpansionReservations(
        ServerLevel level,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement
    ) {
        List<com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds> reserved = new ArrayList<>();
        for (RoomControllerBlockEntity.RoomBounds bounds : placement.bounds()) reserved.add(toSolverBounds(bounds));
        GeneratedFloorRuntimeData.PruneState prune = placement.pruneState();
        if (prune != null) {
            for (int i = Math.max(0, prune.boundsIndex()); i < prune.bounds().size(); i++) {
                var bounds = toSolverBounds(prune.bounds().get(i));
                if (!reserved.contains(bounds)) reserved.add(bounds);
            }
        }
        reserved.addAll(buildProtectedReservations(level));
        return List.copyOf(reserved);
    }

    private static List<com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds> buildProtectedReservations(
        ServerLevel level
    ) {
        DungeonRunManager manager = DungeonRunManager.get(level);
        BlockPos hubA = manager.getHubSafeZoneCornerA();
        BlockPos hubB = manager.getHubSafeZoneCornerB();
        if (hubA == null || hubB == null) return List.of();
        return List.of(new com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds(
            Math.min(hubA.getX(), hubB.getX()), level.getMinBuildHeight(), Math.min(hubA.getZ(), hubB.getZ()),
            Math.max(hubA.getX(), hubB.getX()), level.getMaxBuildHeight() - 1, Math.max(hubA.getZ(), hubB.getZ())
        ));
    }

    private static boolean isExpansionCommitValid(
        DungeonRunManager manager,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        int cursor,
        int added,
        List<BlockPos> appendedControllers
    ) {
        if (added <= 0 || appendedControllers.size() != added || placement.roomPlans().size() != cursor) return false;
        java.util.LinkedHashSet<BlockPos> unique = new java.util.LinkedHashSet<>(manager.getPresetRoomOrder());
        for (BlockPos controller : appendedControllers) {
            if (controller == null || !unique.add(controller)) return false;
        }
        return !manager.isActiveRun() || manager.getPresetRoomCount() == placement.roomPlans().size();
    }
    public static PlacementResult resolvePlayerDirectedExitAndPlaceNext(
        ServerLevel level,
        BlockPos completedControllerPos,
        ServerPlayer player
    ) {
        return resolvePlayerDirectedExitAndPlaceNext(
            level, null, completedControllerPos, player
        );
    }

    public static PlacementResult resolvePlayerDirectedExitAndPlaceNext(
        ServerLevel level,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos completedControllerPos,
        ServerPlayer player
    ) {
        GeneratedFloorRepository repository = repositoryFor(level, instanceKey);
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (instanceKey != null && !manager.bindInstance(instanceKey)) {
            return PlacementResult.skipped("INSTANCE_RUN_OWNER_MISSING");
        }
        GeneratedFloorSession session = repository.hydrate();
        String dimensionKey = level.dimension().location().toString();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || placement.roomPlans().isEmpty()) return PlacementResult.skipped("NO_GENERATED_PLAN");

        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans = placement.roomPlans();
        int completedIndex = findPlanIndex(plans, completedControllerPos);
        if (completedIndex < 0 || completedIndex + 1 >= plans.size()) return PlacementResult.skipped("COMPLETED_ROOM_HAS_NO_GENERATED_SUCCESSOR");

        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan completedPlan = plans.get(completedIndex);
        if (!completedPlan.supportsPlayerDirectedExit()) return PlacementResult.skipped("COMPLETED_ROOM_IS_NOT_MULTIWAY");

        PlacementResult contractFailure = generationContractFailure(
            level, repository, session, completedControllerPos, dimensionKey
        );
        if (contractFailure != null) return contractFailure;

        List<RuntimeConnector> exits = new ArrayList<>(collectOutgoingConnectors(level, completedPlan));
        Direction plannedFacing = parseDirection(completedPlan.outgoingConnectorFacing());
        if (plannedFacing != null && completedPlan.outgoingConnectorPos() != null
            && exits.stream().noneMatch(exit -> exit.worldPos().equals(completedPlan.outgoingConnectorPos()))) {
            exits.add(new RuntimeConnector(completedPlan.outgoingConnectorPos(), plannedFacing, true));
        }
        if (exits.size() < 2) return placePlannedRoom(level, repository, session, dimensionKey, placement, plans.get(completedIndex + 1));

        List<GeneratedFloorRuntimeData.FrontierCandidate> frontier = new ArrayList<>();
        GeneratedFloorRuntimeData.FrontierCandidate plannedSuccessor =
            existingPlannedSuccessorCandidate(placement, plans, completedIndex);
        if (plannedSuccessor != null) {
            frontier.add(plannedSuccessor);
            LOGGER.info(
                "Frontier candidate accepted: source={} exit={} facing={} controller={} mode=PLANNED_SUCCESSOR templates={}",
                completedControllerPos, plannedSuccessor.sourceExit(), plannedSuccessor.exitFacing(),
                plannedSuccessor.plan().controllerPos(),
                plannedSuccessor.branchPlans().stream()
                    .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::templateLabel).toList()
            );
        }
        for (RuntimeConnector exit : exits) {
            if (plannedSuccessor != null && plannedSuccessor.sourceExit().equals(exit.worldPos())) {
                continue;
            }
            GeneratedFloorRuntimeData.LastGeneratedPlacement livePlacement = session.placement();
            List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> branchPlans = solveFrontierBranch(
                level, livePlacement, plans, completedIndex, exit, frontier, session.compositionState()
            );
            if (branchPlans.isEmpty()) continue;
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan previewPlan = branchPlans.getFirst();
            if (!isTransformedTailSafe(level, livePlacement, branchPlans, 0)) {
                LOGGER.warn("Frontier candidate rejected: source={} exit={} facing={} controller={} reason=BRANCH_GEOMETRY_UNSAFE",
                    completedControllerPos, exit.worldPos(), exit.facing(), previewPlan.controllerPos());
                continue;
            }
            PlacementResult result = placePlannedRoom(level, repository, session, dimensionKey, livePlacement, previewPlan, true);
            if (result.placed()) {
                frontier.add(new GeneratedFloorRuntimeData.FrontierCandidate(
                    exit.worldPos(), exit.facing().name(), previewPlan, previewPlan.bounds(), branchPlans
                ));
                LOGGER.info("Frontier candidate accepted: source={} exit={} facing={} controller={} mode=DISTINCT_BRANCH_SEQUENCE templates={}",
                    completedControllerPos, exit.worldPos(), exit.facing(), previewPlan.controllerPos(),
                    branchPlans.stream().map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::templateLabel).toList());
            } else {
                LOGGER.warn("Frontier candidate rejected: source={} exit={} facing={} controller={} reason={}",
                    completedControllerPos, exit.worldPos(), exit.facing(), previewPlan.controllerPos(), result.reason());
            }
        }
        if (frontier.isEmpty()) return PlacementResult.failed(completedPlan.index() + 1, "NO_SAFE_FRONTIER_BRANCH");
        session.stageFrontier(completedControllerPos, frontier);
        repository.flushPlacement(session);
        DungeonSoundHooks.frontierPreviewed(level, completedControllerPos);
        LOGGER.info("Generated frontier previews: source={} branches={} dimension={}", completedControllerPos, frontier.size(), dimensionKey);
        return PlacementResult.placed(completedPlan.index() + 1, frontier.get(0).plan().controllerPos());
    }

    static @Nullable GeneratedFloorRuntimeData.FrontierCandidate existingPlannedSuccessorCandidate(
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans,
        int completedIndex
    ) {
        if (placement == null || plans == null || completedIndex < 0 || completedIndex + 1 >= plans.size()) {
            return null;
        }
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan source = plans.get(completedIndex);
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan successor = plans.get(completedIndex + 1);
        Direction facing = parseDirection(source.outgoingConnectorFacing());
        if (source.outgoingConnectorPos() == null || facing == null
            || !placement.controllers().contains(successor.controllerPos())
            || !placement.bounds().contains(successor.bounds())) {
            return null;
        }
        return new GeneratedFloorRuntimeData.FrontierCandidate(
            source.outgoingConnectorPos(),
            facing.name(),
            successor,
            successor.bounds(),
            List.copyOf(plans.subList(completedIndex + 1, plans.size()))
        );
    }

    public static boolean isFrontierCandidate(ServerLevel level, BlockPos controllerPos) {
        return isFrontierCandidate(level, null, controllerPos);
    }

    public static boolean isFrontierCandidate(
        ServerLevel level,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos controllerPos
    ) {
        return repositoryFor(level, instanceKey).hydrate().isFrontierCandidate(controllerPos);
    }

    public static boolean commitFrontierOnEntry(ServerLevel level, BlockPos enteredControllerPos) {
        return commitFrontierOnEntry(level, null, enteredControllerPos);
    }

    public static boolean commitFrontierOnEntry(
        ServerLevel level,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos enteredControllerPos
    ) {
        GeneratedFloorRepository repository = repositoryFor(level, instanceKey);
        GeneratedFloorSession session = repository.hydrate();
        String dimensionKey = level.dimension().location().toString();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || placement.frontier() == null) return false;
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (instanceKey != null && !manager.bindInstance(instanceKey)) {
            return false;
        }
        if (!manager.isRoomRegisteredCompleted(placement.frontier().sourceController())) {
            LOGGER.warn("Generated frontier commitment rejected before source completion: source={} entered={} expected={} activeRooms={}",
                placement.frontier().sourceController(), enteredControllerPos, manager.getPresetExpectedControllerPos(),
                manager.getActiveRoomControllerPositions());
            return false;
        }
        if (manager.getActiveRoomCount() > 0) return false;

        GeneratedFloorRuntimeData.FrontierCandidate selected = placement.frontier().candidates().stream()
            .filter(candidate -> candidate.plan().controllerPos().equals(enteredControllerPos))
            .findFirst().orElse(null);
        if (selected == null) return false;

        int sourceIndex = findPlanIndex(placement.roomPlans(), placement.frontier().sourceController());
        Direction facing = parseDirection(selected.exitFacing());
        if (sourceIndex < 0 || facing == null) return false;
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> resolved = resolveCommittedTail(
            placement.roomPlans(), sourceIndex, selected
        );
        if (resolved == null) return false;

        RoomControllerBlockEntity sourceController = level.getBlockEntity(placement.frontier().sourceController())
            instanceof RoomControllerBlockEntity controller ? controller : null;
        for (GeneratedFloorRuntimeData.FrontierCandidate candidate : placement.frontier().candidates()) {
            if (candidate == selected) continue;
            GeneratedFloorRuntimeData.GenerationFailureState pendingRepair = session.generationFailure();
            if (sourceController != null && pendingRepair != null
                && "FRONTIER_UNCHOSEN_SEAL_FAILED".equals(pendingRepair.code())
                && placement.frontier().sourceController().equals(pendingRepair.controllerPos())
                && candidate.sourceExit().equals(pendingRepair.connectorPos())) {
                sourceController.clearTemporarySealNear(level, candidate.sourceExit());
            }
            boolean runeSealed = sourceController != null
                && sourceController.placeTemporarySeal(level, candidate.sourceExit(), false);
            if (!runeSealed) {
                boolean emergencySealed = sourceController != null
                    && sourceController.placeTemporarySeal(level, candidate.sourceExit(), true);
                recordGenerationFailure(
                    level, repository, session, "FRONTIER_UNCHOSEN_SEAL_FAILED",
                    placement.frontier().sourceController(), candidate.sourceExit(),
                    "discardedController=" + candidate.plan().controllerPos().toShortString()
                        + " emergencyBlackSeal=" + emergencySealed
                );
                LOGGER.error("Generated frontier unchosen seal failed: source={} exit={} discardedController={} emergencyBlackSeal={} dimension={}",
                    placement.frontier().sourceController(), candidate.sourceExit(), candidate.plan().controllerPos(), emergencySealed, dimensionKey);
                return false;
            }
        }

        if (!manager.replacePendingPresetOrder(resolved.stream().map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::controllerPos).toList())) {
            return false;
        }
        GeneratedFloorSession.FrontierCommit commit = session.commitFrontier(enteredControllerPos, resolved);
        if (commit == null) return false;
        repository.flushPlacement(session);
        clearGenerationFailure(repository, session, "FRONTIER_UNCHOSEN_SEAL_FAILED");

        moveRearSeal(level, repository, session, commit.committedPlacement());
        if (level.getBlockEntity(enteredControllerPos) instanceof RoomControllerBlockEntity selectedController) {
            selectedController.setBranchPreviewMode(false);
            selectedController.clearTemporarySeal(level);
        }
        DungeonSoundHooks.frontierCommitted(level, enteredControllerPos);
        LOGGER.info("Generated frontier committed: entered={} discarded={} retained={} dimension={}", enteredControllerPos,
            commit.discardedBounds().size(), commit.retainedRoute().size(), dimensionKey);
        return true;
    }

    public static boolean openSuccessorIncomingSeal(ServerLevel level, BlockPos completedControllerPos) {
        return openSuccessorIncomingSeal(level, null, completedControllerPos);
    }

    public static boolean openSuccessorIncomingSeal(
        ServerLevel level,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos completedControllerPos
    ) {
        GeneratedFloorRepository repository = repositoryFor(level, instanceKey);
        GeneratedFloorSession session = repository.hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null) {
            LOGGER.warn("Successor entrance seal clear skipped: no generated placement for completed={}", completedControllerPos);
            return false;
        }
        int completedIndex = findPlanIndex(placement.roomPlans(), completedControllerPos);
        if (completedIndex < 0) {
            LOGGER.warn("Successor entrance seal clear skipped: completed controller is not in the generated plan completed={}",
                completedControllerPos);
            return false;
        }
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan completed = placement.roomPlans().get(completedIndex);
        retainRemoteFirstRoomEntranceSeal(level, instanceKey, completedIndex, completed);
        if (completedIndex + 1 >= placement.roomPlans().size()) {
            LOGGER.warn("Successor entrance seal clear skipped: no successor plan for completed={}", completedControllerPos);
            return false;
        }
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan successor = placement.roomPlans().get(completedIndex + 1);
        RoomControllerBlockEntity successorController = level.getBlockEntity(successor.controllerPos())
            instanceof RoomControllerBlockEntity found ? found : null;
        if (successorController == null
            || !successorReadyForTransition(level, session, successor, successorController)) {
            boolean loadingSealPlaced = retainSuccessorLoadingSeal(level, completed);
            LOGGER.info(
                "Deferred generated successor entrance release: completed={} successor={} successorReady={} loadingSealPlaced={} dimension={}",
                completedControllerPos, successor.controllerPos(), false, loadingSealPlaced, level.dimension().location()
            );
            GeneratedFloorWorldWorkScheduler.requestCompletedRoomSuccessor(
                level, instanceKey, completedControllerPos
            );
            return false;
        }

        boolean cleared = false;
        if (successor.incomingConnectorPos() != null) {
            cleared = successorController.clearTemporarySealNear(level, successor.incomingConnectorPos());
        }
        if (!cleared && completed.outgoingConnectorPos() != null
            && level.getBlockEntity(completedControllerPos) instanceof RoomControllerBlockEntity completedController) {
            cleared = completedController.clearTemporarySealNear(level, completed.outgoingConnectorPos());
        }
        if (cleared) {
            LOGGER.info("Opened generated successor entrance seal: completed={} successor={} incoming={} outgoing={}",
                completedControllerPos, successor.controllerPos(), successor.incomingConnectorPos(), completed.outgoingConnectorPos());
        } else {
            LOGGER.info("Generated successor was ready; no transition seal remained to clear: completed={} successor={} incoming={} outgoing={}",
                completedControllerPos, successor.controllerPos(), successor.incomingConnectorPos(), completed.outgoingConnectorPos());
        }
        return true;
    }

    private static boolean successorReadyForTransition(
        ServerLevel level,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan successor,
        RoomControllerBlockEntity successorController
    ) {
        if (session.placement() == null
            || !session.placement().controllers().contains(successor.controllerPos())) {
            return false;
        }
        boolean branchPreview = successorController.isBranchPreviewMode();
        return GeneratedRoomPlacementOperation.existingControllerValidationFailure(
            level, session, successor, branchPreview
        ) == null;
    }

    private static boolean retainSuccessorLoadingSeal(
        ServerLevel level,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan completed
    ) {
        if (completed.outgoingConnectorPos() == null
            || !(level.getBlockEntity(completed.controllerPos()) instanceof RoomControllerBlockEntity controller)) {
            LOGGER.error(
                "Generated successor loading seal could not be attempted: missing completed controller or outgoing connector completed={} outgoing={} dimension={}",
                completed.controllerPos(), completed.outgoingConnectorPos(), level.dimension().location()
            );
            return false;
        }
        if (controller.placeTemporarySeal(level, completed.outgoingConnectorPos(), false)) {
            return true;
        }
        boolean emergencyBlackSeal = controller.placeTemporarySeal(level, completed.outgoingConnectorPos(), true);
        if (!emergencyBlackSeal) {
            LOGGER.error(
                "Generated successor loading seal failed in both visual and emergency modes: completed={} outgoing={} dimension={}",
                completed.controllerPos(), completed.outgoingConnectorPos(), level.dimension().location()
            );
        }
        return emergencyBlackSeal;
    }

    private static void retainRemoteFirstRoomEntranceSeal(
        ServerLevel level,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey,
        int completedIndex,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan completed
    ) {
        if (completedIndex != 0 || instanceKey == null || completed.incomingConnectorPos() == null) {
            return;
        }
        DungeonInstanceAllocationData.Allocation allocation = DungeonInstanceAllocationData.get(level)
            .forIdentity(instanceKey)
            .orElse(null);
        if (allocation == null || allocation.soloPreparation() == null) {
            return;
        }
        boolean sealed = level.getBlockEntity(completed.controllerPos())
            instanceof RoomControllerBlockEntity controller
            && controller.placeTemporarySeal(level, completed.incomingConnectorPos(), false);
        if (sealed) {
            LOGGER.info(
                "Retained remote generated-floor entrance seal: controller={} incoming={} instance={} epoch={}",
                completed.controllerPos(), completed.incomingConnectorPos(),
                instanceKey.instanceId(), instanceKey.epoch()
            );
        } else {
            LOGGER.error(
                "Remote generated-floor entrance seal placement failed: controller={} incoming={} instance={} epoch={}",
                completed.controllerPos(), completed.incomingConnectorPos(),
                instanceKey.instanceId(), instanceKey.epoch()
            );
        }
    }
    public static boolean retainTerminalOutgoingSeal(ServerLevel level, BlockPos completedControllerPos) {
        return retainTerminalOutgoingSeal(level, null, completedControllerPos);
    }

    public static boolean retainTerminalOutgoingSeal(
        ServerLevel level,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos completedControllerPos
    ) {
        GeneratedFloorRepository repository = repositoryFor(level, instanceKey);
        GeneratedFloorSession session = repository.hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null) return false;
        int completedIndex = findPlanIndex(placement.roomPlans(), completedControllerPos);
        if (completedIndex < 0 || completedIndex + 1 < placement.roomPlans().size()) return false;
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan completed = placement.roomPlans().get(completedIndex);
        DungeonRunManager manager = DungeonRunManager.get(level);
        int targetRoomCount = instanceKey == null
            ? manager.getTargetRoomCount()
            : manager.getTargetRoomCount(instanceKey);
        if (completed.index() < targetRoomCount) {
            return false;
        }
        RoomControllerBlockEntity controller = level.getBlockEntity(completedControllerPos)
            instanceof RoomControllerBlockEntity found ? found : null;
        List<BlockPos> authoredBoundaries = collectOutgoingConnectors(level, completed).stream()
            .map(RuntimeConnector::worldPos)
            .toList();
        List<BlockPos> terminalBoundaries = terminalBoundaryPositions(
            completed.incomingConnectorPos(),
            completed.outgoingConnectorPos(),
            authoredBoundaries
        );
        BlockPos failedBoundary = null;
        int sealedCount = 0;
        if (controller != null) {
            for (BlockPos boundary : terminalBoundaries) {
                if (controller.placeTemporarySeal(level, boundary, false)) {
                    sealedCount++;
                } else if (failedBoundary == null) {
                    failedBoundary = boundary;
                }
            }
        } else if (!terminalBoundaries.isEmpty()) {
            failedBoundary = terminalBoundaries.getFirst();
        }
        boolean sealed = controller != null
            && !terminalBoundaries.isEmpty()
            && sealedCount == terminalBoundaries.size();
        if (sealed) {
            clearGenerationFailure(repository, session, TERMINAL_OUTGOING_SEAL_FAILED);
            LOGGER.info(
                "Retained all terminal generated boundary seals: completed={} boundaries={} sealed={} roomIndex={} targetRooms={}",
                completedControllerPos, terminalBoundaries, sealedCount,
                completed.index(), manager.getTargetRoomCount()
            );
        } else {
            recordGenerationFailure(
                level, repository, session, TERMINAL_OUTGOING_SEAL_FAILED,
                completedControllerPos, failedBoundary,
                "boundaries=" + terminalBoundaries.size() + " sealed=" + sealedCount
                    + " roomIndex=" + completed.index() + " targetRooms=" + manager.getTargetRoomCount()
            );
            LOGGER.error(
                "Terminal generated boundary sealing failed: completed={} failedBoundary={} boundaries={} sealed={} roomIndex={} targetRooms={}",
                completedControllerPos, failedBoundary, terminalBoundaries, sealedCount,
                completed.index(), manager.getTargetRoomCount()
            );
        }
        return sealed;
    }

    private static GeneratedFloorRepository repositoryFor(
        ServerLevel level,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return instanceKey == null
            ? GeneratedFloorRepository.get(level)
            : GeneratedFloorRepository.get(
                level, level.dimension().location().toString(), instanceKey
            );
    }

    static List<BlockPos> terminalBoundaryPositions(
        @Nullable BlockPos incoming,
        @Nullable BlockPos plannedOutgoing,
        List<BlockPos> authoredOutgoing
    ) {
        java.util.LinkedHashSet<BlockPos> boundaries = new java.util.LinkedHashSet<>();
        if (incoming != null) boundaries.add(incoming.immutable());
        if (authoredOutgoing != null) {
            for (BlockPos boundary : authoredOutgoing) {
                if (boundary != null) boundaries.add(boundary.immutable());
            }
        }
        if (plannedOutgoing != null) boundaries.add(plannedOutgoing.immutable());
        return List.copyOf(boundaries);
    }
    public static void trackActivatedRoute(ServerLevel level, BlockPos controllerPos) {
        GeneratedFloorRepository repository = GeneratedFloorRepository.get(level);
        GeneratedFloorSession session = repository.hydrate();
        GeneratedFloorSession.RouteRetention retained = session.trackActivatedRoute(controllerPos);
        if (retained != null) {
            repository.flushPlacement(session);
            moveRearSeal(level, repository, session, retained.placement());
        } else if (session.placement() != null && session.generationFailure() != null
            && REAR_CONNECTOR_SEAL_FAILED.equals(session.generationFailure().code())) {
            moveRearSeal(level, repository, session, session.placement());
        }
    }
    private static boolean moveRearSeal(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement
    ) {
        for (BlockPos controllerPos : placement.retainedRoute()) {
            if (level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller) controller.clearTemporaryBlackSeal(level);
        }
        if (placement.retainedRoute().size() < 3) {
            clearGenerationFailure(repository, session, REAR_CONNECTOR_SEAL_FAILED);
            return true;
        }
        BlockPos oldest = placement.retainedRoute().get(0);
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan = placement.roomPlans().stream()
            .filter(candidate -> candidate.controllerPos().equals(oldest)).findFirst().orElse(null);
        BlockPos connector = plan == null ? null : plan.incomingConnectorPos();
        boolean sealed = connector != null
            && level.getBlockEntity(oldest) instanceof RoomControllerBlockEntity controller
            && controller.placeTemporarySeal(level, connector, true);
        if (!sealed) {
            recordGenerationFailure(
                level, repository, session, REAR_CONNECTOR_SEAL_FAILED, oldest, connector,
                "template=" + (plan == null ? "NONE" : plan.templateLabel())
                    + " bounds=" + (plan == null ? "NONE" : plan.bounds())
            );
            LOGGER.error("Rear connector seal placement failed: template={} controller={} connector={} bounds={}",
                plan == null ? "NONE" : plan.templateLabel(), oldest, connector, plan == null ? "NONE" : plan.bounds());
            return false;
        }
        clearGenerationFailure(repository, session, REAR_CONNECTOR_SEAL_FAILED);
        return true;
    }

    private static int findPlanIndex(List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans, BlockPos controllerPos) {
        for (int i = 0; i < plans.size(); i++) if (plans.get(i).controllerPos().equals(controllerPos)) return i;
        return -1;
    }

    private static List<RuntimeConnector> collectOutgoingConnectors(
        ServerLevel level,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan
    ) {
        Rotation rotation = parseRotation(roomPlan.rotation());
        if (rotation == null || roomPlan.placementOrigin() == null || roomPlan.incomingConnectorPos() == null) {
            return List.of();
        }
        ResourceLocation templateId = ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID,
            "rooms/linear/" + roomPlan.templateLabel()
        );
        Optional<StructureTemplate> loaded;
        if (CompositeTemplateDescriptor.isCompositeId(roomPlan.templateLabel())) {
            CompositeRoomRuntimeRegistry.Resolution composite =
                CompositeRoomRuntimeRegistry.resolve(level, roomPlan.templateLabel());
            loaded = composite.success() && composite.template() != null
                ? Optional.of(composite.template()) : Optional.empty();
        } else {
            loaded = level.getStructureManager().get(templateId);
        }
        if (loaded.isEmpty()) {
            return List.of();
        }

        StructurePlaceSettings settings = new StructurePlaceSettings().setMirror(Mirror.NONE).setRotation(rotation);
        BoundingBox localBounds = loaded.get().getBoundingBox(settings, BlockPos.ZERO);
        List<RuntimeConnector> connectors = new ArrayList<>();
        addRuntimeConnectors(connectors, loaded.get(), settings, localBounds, roomPlan.placementOrigin(), ModBlocks.FLOOR_CONNECTOR_ANCHOR.get(), false);
        addRuntimeConnectors(connectors, loaded.get(), settings, localBounds, roomPlan.placementOrigin(), ModBlocks.CONNECTOR_EXIT.get(), true);
        if (connectors.isEmpty()) {
            addRuntimeConnectors(connectors, loaded.get(), settings, localBounds, roomPlan.placementOrigin(), ModBlocks.FLOOR_ENTRY_ANCHOR.get(), false);
            addRuntimeConnectors(connectors, loaded.get(), settings, localBounds, roomPlan.placementOrigin(), ModBlocks.EXIT_ANCHOR.get(), false);
        }

        connectors.removeIf(connector -> connector.worldPos().equals(roomPlan.incomingConnectorPos()));
        if (connectors.stream().anyMatch(RuntimeConnector::outgoingPreferred)) {
            connectors.removeIf(connector -> !connector.outgoingPreferred());
        }
        return connectors;
    }

    private static void addRuntimeConnectors(
        List<RuntimeConnector> connectors,
        StructureTemplate template,
        StructurePlaceSettings settings,
        BoundingBox localBounds,
        BlockPos placementOrigin,
        net.minecraft.world.level.block.Block markerBlock,
        boolean outgoingPreferred
    ) {
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, markerBlock)) {
            connectors.add(new RuntimeConnector(
                placementOrigin.offset(info.pos()),
                inferHorizontalFacing(info.pos(), localBounds),
                outgoingPreferred
            ));
        }
    }

    private static Direction inferHorizontalFacing(BlockPos pos, BoundingBox bounds) {
        int west = Math.abs(pos.getX() - bounds.minX());
        int east = Math.abs(pos.getX() - bounds.maxX());
        int north = Math.abs(pos.getZ() - bounds.minZ());
        int south = Math.abs(pos.getZ() - bounds.maxZ());
        int best = Math.min(Math.min(west, east), Math.min(north, south));
        if (west == best) {
            return Direction.WEST;
        }
        if (east == best) {
            return Direction.EAST;
        }
        return north == best ? Direction.NORTH : Direction.SOUTH;
    }

    private static double playerDirectionScore(
        ServerPlayer player,
        RuntimeConnector connector,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan
    ) {
        if (player == null) {
            return connector.worldPos().equals(roomPlan.outgoingConnectorPos()) ? 1.0D : 0.0D;
        }
        Vec3 look = player.getLookAngle();
        double horizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
        if (horizontalLength < 0.0001D) {
            return 0.0D;
        }
        double facingDot = (look.x / horizontalLength) * connector.facing().getStepX()
            + (look.z / horizontalLength) * connector.facing().getStepZ();
        double proximityTieBreak = -player.blockPosition().distSqr(connector.worldPos()) * 0.000001D;
        return facingDot + proximityTieBreak;
    }

    private static List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> transformTailForExit(
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans,
        int completedIndex,
        RuntimeConnector selected
    ) {
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan completed = plans.get(completedIndex);
        Direction oldFacing = parseDirection(completed.outgoingConnectorFacing());
        if (oldFacing == null || completed.outgoingConnectorPos() == null) {
            return null;
        }
        Rotation delta = rotationBetween(oldFacing, selected.facing());
        if (delta == null) {
            return null;
        }

        BlockPos oldPivot = attachmentSocketOutsideBounds(
            completed.outgoingConnectorPos(), oldFacing, completed.bounds()
        );
        BlockPos newPivot = attachmentSocketOutsideBounds(
            selected.worldPos(), selected.facing(), completed.bounds()
        );
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> transformed = new ArrayList<>(plans);
        transformed.set(completedIndex, new GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan(
            completed.index(), completed.controllerPos(), completed.templateLabel(), completed.safeEligible(), completed.safeRolled(),
            completed.rotation(), completed.placementOrigin(), completed.bounds(), completed.incomingConnectorPos(),
            completed.incomingConnectorFacing(), selected.worldPos(), selected.facing().name(), completed.outgoingConnectorCount(),
            completed.gameplayRole(), completed.effectiveProfile(), completed.objectiveType()
        ));
        for (int i = completedIndex + 1; i < plans.size(); i++) {
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan = plans.get(i);
            Rotation planRotation = parseRotation(plan.rotation());
            if (planRotation == null || plan.placementOrigin() == null || plan.bounds() == null) {
                return null;
            }
            transformed.set(i, new GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan(
                plan.index(),
                transformPos(plan.controllerPos(), oldPivot, newPivot, delta),
                plan.templateLabel(),
                plan.safeEligible(),
                plan.safeRolled(),
                combineRotations(planRotation, delta).name(),
                transformPos(plan.placementOrigin(), oldPivot, newPivot, delta),
                transformBounds(plan.bounds(), oldPivot, newPivot, delta),
                transformNullablePos(plan.incomingConnectorPos(), oldPivot, newPivot, delta),
                rotateFacingName(plan.incomingConnectorFacing(), delta),
                transformNullablePos(plan.outgoingConnectorPos(), oldPivot, newPivot, delta),
                rotateFacingName(plan.outgoingConnectorFacing(), delta),
                plan.outgoingConnectorCount(), plan.gameplayRole(), plan.effectiveProfile(), plan.objectiveType()
            ));
        }
        return transformed;
    }

    private static List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> solveFrontierBranch(
        ServerLevel level,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans,
        int completedIndex,
        RuntimeConnector exit,
        List<GeneratedFloorRuntimeData.FrontierCandidate> accepted,
        @Nullable com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonCompositionData.State composition
    ) {
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan completed = plans.get(completedIndex);
        int branchLength = plans.size() - completedIndex - 1;
        if (branchLength <= 0 || branchLength > com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.DEFAULT_HORIZON) {
            LOGGER.warn("Frontier branch solve rejected: source={} exit={} reason=INVALID_BRANCH_LENGTH length={}",
                completed.controllerPos(), exit.worldPos(), branchLength);
            return List.of();
        }
        List<com.mineclawd.tensuradungeon.runtime.generation.DungeonGameplayComposer.Beat> beats = new ArrayList<>(branchLength);
        for (int i = completedIndex + 1; i < plans.size(); i++) {
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan canonical = plans.get(i);
            com.mineclawd.tensuradungeon.runtime.generation.DungeonRoomRole role = null;
            int intensity = 1;
            String objective = canonical.objectiveType();
            String purpose = "branch_sequence";
            if (composition != null && i < composition.beats().size()) {
                var beat = composition.beats().get(i);
                role = beat.role();
                intensity = beat.intensity();
                objective = beat.objective();
                purpose = beat.purpose();
            }
            if (role == null) {
                role = com.mineclawd.tensuradungeon.runtime.generation.DungeonRoomRole.parse(canonical.gameplayRole());
            }
            if (role == null) {
                LOGGER.warn("Frontier branch solve rejected: source={} exit={} reason=UNKNOWN_BRANCH_ROLE planIndex={}",
                    completed.controllerPos(), exit.worldPos(), canonical.index());
                return List.of();
            }
            beats.add(new com.mineclawd.tensuradungeon.runtime.generation.DungeonGameplayComposer.Beat(
                canonical.index(), role, intensity, objective, purpose
            ));
        }
        var authored = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry.get(level);
        var mechanical = com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry.getForTheme(
            level, authored, placement.visualTheme()
        );

        List<com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds> reserved = new ArrayList<>();
        for (RoomControllerBlockEntity.RoomBounds bounds : placement.bounds()) {
            reserved.add(toSolverBounds(bounds));
        }
        for (GeneratedFloorRuntimeData.FrontierCandidate candidate : accepted) {
            for (GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan branchPlan : candidate.branchPlans()) {
                var candidateBounds = toSolverBounds(branchPlan.bounds());
                if (!reserved.contains(candidateBounds)) reserved.add(candidateBounds);
            }
        }
        BlockPos socket = attachmentSocketOutsideBounds(exit.worldPos(), exit.facing(), completed.bounds());
        int totalRoomCount = composition == null ? plans.size() : composition.roomCount();
        boolean requireContinuation = composition != null && composition.cursor() < composition.beats().size();
        List<String> priorTemplates = plans.subList(0, completedIndex + 1).stream()
            .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::templateLabel).toList();
        Set<String> excludedFirstTemplates = accepted.stream()
            .map(candidate -> candidate.plan().templateLabel()).collect(java.util.stream.Collectors.toSet());
        long baseSeed = (composition == null ? level.getSeed() : composition.seed())
            ^ completed.controllerPos().asLong() ^ exit.worldPos().asLong();
        for (int attempt = 0; attempt < 8; attempt++) {
            Set<String> exclusions = attempt == 0 ? excludedFirstTemplates : Set.of();
            var selectionContext = new com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.SelectionContext(
                totalRoomCount, priorTemplates, exclusions, Map.of(), placement.visualTheme()
            );
            long seed = baseSeed ^ (0x9E3779B97F4A7C15L * attempt);
            var solved = com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.solveWindow(
                beats, authored, mechanical, socket, exit.facing(), level.getMinBuildHeight(), level.getMaxBuildHeight(),
                seed, reserved, buildProtectedReservations(level), requireContinuation, selectionContext
            );
            var adapted = solved.solved()
                ? com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonPlanAdapter.adapt(level, solved, mechanical) : null;
            if (!solved.solved() || adapted == null || !adapted.valid() || adapted.plans().size() != branchLength) {
                continue;
            }
            List<String> sequence = adapted.plans().stream()
                .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::templateLabel).toList();
            boolean duplicate = accepted.stream().anyMatch(candidate -> candidate.branchPlans().stream()
                .map(GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan::templateLabel).toList().equals(sequence));
            if (!duplicate) return adapted.plans();
        }
        LOGGER.warn("Frontier branch solve rejected: source={} exit={} reason=NO_DISTINCT_BRANCH_SEQUENCE",
            completed.controllerPos(), exit.worldPos());
        return List.of();
    }

    private static com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds toSolverBounds(
        RoomControllerBlockEntity.RoomBounds bounds
    ) {
        return new com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver.Bounds(
            bounds.min().getX(), bounds.min().getY(), bounds.min().getZ(),
            bounds.max().getX(), bounds.max().getY(), bounds.max().getZ()
        );
    }

    private static List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> resolveCommittedTail(
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans,
        int sourceIndex,
        GeneratedFloorRuntimeData.FrontierCandidate selected
    ) {
        Direction selectedFacing = parseDirection(selected.exitFacing());
        if (selectedFacing == null) return null;
        if (selected.branchPlans().size() == plans.size() - sourceIndex - 1) {
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan source = plans.get(sourceIndex);
            List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> resolved = new ArrayList<>(
                plans.subList(0, sourceIndex + 1)
            );
            resolved.set(sourceIndex, new GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan(
                source.index(), source.controllerPos(), source.templateLabel(), source.safeEligible(), source.safeRolled(),
                source.rotation(), source.placementOrigin(), source.bounds(), source.incomingConnectorPos(),
                source.incomingConnectorFacing(), selected.sourceExit(), selectedFacing.name(), source.outgoingConnectorCount(),
                source.gameplayRole(), source.effectiveProfile(), source.objectiveType()
            ));
            resolved.addAll(selected.branchPlans());
            return List.copyOf(resolved);
        }
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> transformed = transformTailForExit(
            plans, sourceIndex, new RuntimeConnector(selected.sourceExit(), selectedFacing, true)
        );
        if (transformed == null || sourceIndex + 1 >= transformed.size()) return null;
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan expected = transformed.get(sourceIndex + 1);
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan preview = selected.plan();
        if (expected.controllerPos().equals(preview.controllerPos())) return transformed;

        Direction oldFacing = parseDirection(expected.outgoingConnectorFacing());
        Direction newFacing = parseDirection(preview.outgoingConnectorFacing());
        if (oldFacing == null || newFacing == null || expected.outgoingConnectorPos() == null
            || preview.outgoingConnectorPos() == null) return null;
        Rotation delta = rotationBetween(oldFacing, newFacing);
        if (delta == null) return null;
        BlockPos oldPivot = attachmentSocketOutsideBounds(expected.outgoingConnectorPos(), oldFacing, expected.bounds());
        BlockPos newPivot = attachmentSocketOutsideBounds(preview.outgoingConnectorPos(), newFacing, preview.bounds());

        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> rebased = new ArrayList<>(transformed);
        rebased.set(sourceIndex + 1, preview);
        for (int i = sourceIndex + 2; i < transformed.size(); i++) {
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan = transformed.get(i);
            Rotation planRotation = parseRotation(plan.rotation());
            if (planRotation == null || plan.bounds() == null) return null;
            rebased.set(i, new GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan(
                plan.index(), transformPos(plan.controllerPos(), oldPivot, newPivot, delta),
                plan.templateLabel(), plan.safeEligible(), plan.safeRolled(),
                combineRotations(planRotation, delta).name(),
                transformPos(plan.placementOrigin(), oldPivot, newPivot, delta),
                transformBounds(plan.bounds(), oldPivot, newPivot, delta),
                transformNullablePos(plan.incomingConnectorPos(), oldPivot, newPivot, delta),
                rotateFacingName(plan.incomingConnectorFacing(), delta),
                transformNullablePos(plan.outgoingConnectorPos(), oldPivot, newPivot, delta),
                rotateFacingName(plan.outgoingConnectorFacing(), delta),
                plan.outgoingConnectorCount(), plan.gameplayRole(), plan.effectiveProfile(), plan.objectiveType()
            ));
        }
        return rebased;
    }

    private static BlockPos attachmentSocketOutsideBounds(
        BlockPos connector,
        Direction facing,
        RoomControllerBlockEntity.RoomBounds bounds
    ) {
        BlockPos socket = connector.relative(facing);
        int steps = 0;
        while (bounds != null && bounds.contains(socket) && steps++ < 8) {
            socket = socket.relative(facing);
        }
        return socket;
    }

    private static boolean isTransformedTailSafe(
        ServerLevel level,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans,
        int tailStart
    ) {
        DungeonRunManager manager = DungeonRunManager.get(level);
        BlockPos hubA = manager.getHubSafeZoneCornerA();
        BlockPos hubB = manager.getHubSafeZoneCornerB();
        for (int i = tailStart; i < plans.size(); i++) {
            RoomControllerBlockEntity.RoomBounds bounds = plans.get(i).bounds();
            if (bounds == null || bounds.min().getY() < level.getMinBuildHeight() || bounds.max().getY() >= level.getMaxBuildHeight()) {
                return false;
            }
            for (RoomControllerBlockEntity.RoomBounds placedBounds : placement.bounds()) {
                if (boundsOverlap(bounds, placedBounds)) {
                    return false;
                }
            }
            if (hubA != null && hubB != null && overlapsHub(bounds, hubA, hubB)) {
                return false;
            }
        }
        return true;
    }

    private static boolean boundsOverlap(RoomControllerBlockEntity.RoomBounds first, RoomControllerBlockEntity.RoomBounds second) {
        return first.min().getX() <= second.max().getX() && first.max().getX() >= second.min().getX()
            && first.min().getY() <= second.max().getY() && first.max().getY() >= second.min().getY()
            && first.min().getZ() <= second.max().getZ() && first.max().getZ() >= second.min().getZ();
    }

    private static boolean overlapsHub(RoomControllerBlockEntity.RoomBounds bounds, BlockPos first, BlockPos second) {
        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxZ = Math.max(first.getZ(), second.getZ());
        return bounds.min().getX() <= maxX && bounds.max().getX() >= minX
            && bounds.min().getZ() <= maxZ && bounds.max().getZ() >= minZ;
    }

    private static Rotation rotationBetween(Direction from, Direction to) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(from) == to) {
                return rotation;
            }
        }
        return null;
    }

    private static Rotation combineRotations(Rotation first, Rotation second) {
        int quarterTurns = (rotationQuarterTurns(first) + rotationQuarterTurns(second)) & 3;
        return switch (quarterTurns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static int rotationQuarterTurns(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
    }

    private static BlockPos transformPos(BlockPos pos, BlockPos oldPivot, BlockPos newPivot, Rotation rotation) {
        int x = pos.getX() - oldPivot.getX();
        int y = pos.getY() - oldPivot.getY();
        int z = pos.getZ() - oldPivot.getZ();
        int transformedX;
        int transformedZ;
        switch (rotation) {
            case CLOCKWISE_90 -> {
                transformedX = -z;
                transformedZ = x;
            }
            case CLOCKWISE_180 -> {
                transformedX = -x;
                transformedZ = -z;
            }
            case COUNTERCLOCKWISE_90 -> {
                transformedX = z;
                transformedZ = -x;
            }
            default -> {
                transformedX = x;
                transformedZ = z;
            }
        }
        return newPivot.offset(transformedX, y, transformedZ);
    }

    private static BlockPos transformNullablePos(BlockPos pos, BlockPos oldPivot, BlockPos newPivot, Rotation rotation) {
        return pos == null ? null : transformPos(pos, oldPivot, newPivot, rotation);
    }

    private static RoomControllerBlockEntity.RoomBounds transformBounds(
        RoomControllerBlockEntity.RoomBounds bounds,
        BlockPos oldPivot,
        BlockPos newPivot,
        Rotation rotation
    ) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x : new int[] { bounds.min().getX(), bounds.max().getX() }) {
            for (int y : new int[] { bounds.min().getY(), bounds.max().getY() }) {
                for (int z : new int[] { bounds.min().getZ(), bounds.max().getZ() }) {
                    BlockPos transformed = transformPos(new BlockPos(x, y, z), oldPivot, newPivot, rotation);
                    minX = Math.min(minX, transformed.getX());
                    minY = Math.min(minY, transformed.getY());
                    minZ = Math.min(minZ, transformed.getZ());
                    maxX = Math.max(maxX, transformed.getX());
                    maxY = Math.max(maxY, transformed.getY());
                    maxZ = Math.max(maxZ, transformed.getZ());
                }
            }
        }
        return new RoomControllerBlockEntity.RoomBounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private static String rotateFacingName(String facingName, Rotation rotation) {
        Direction facing = parseDirection(facingName);
        return facing == null ? "NONE" : rotation.rotate(facing).name();
    }

    private static Direction parseDirection(String name) {
        if (name == null) {
            return null;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction.name().equalsIgnoreCase(name)) {
                return direction;
            }
        }
        return null;
    }

    private static Rotation parseRotation(String name) {
        try {
            return Rotation.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
    static PlacementResult placeFallbackRoom(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        GeneratedRoomPlacementOperation.SliceBudget sliceBudget
    ) {
        if (roomPlan == null) {
            return PlacementResult.failed(0, "FALLBACK_ROOM_PLAN_MISSING");
        }
        GeneratedFloorRepository repository = GeneratedFloorRepository.get(
            level, level.dimension().location().toString(), instanceKey
        );
        GeneratedFloorSession session = repository.hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null) {
            return PlacementResult.failed(roomPlan.index(), "NO_GENERATED_PLAN");
        }
        PlacementResult result = placePlannedRoom(
            level,
            repository,
            session,
            level.dimension().location().toString(),
            placement,
            roomPlan,
            false,
            sliceBudget == null
                ? GeneratedRoomPlacementOperation.SliceBudget.unbounded()
                : sliceBudget
        );
        if (result.failed()) {
            GeneratedFloorSession updated = repository.hydrate();
            GeneratedFloorRuntimeData.GenerationFailureState failure = updated.generationFailure();
            if (failure == null
                || !GENERATED_ROOM_PLACEMENT_FAILED.equals(failure.code())
                || !java.util.Objects.equals(
                    failure.controllerPos(), roomPlan == null ? null : roomPlan.controllerPos()
                )) {
                recordGenerationFailure(
                    level,
                    repository,
                    updated,
                    GENERATED_ROOM_PLACEMENT_FAILED,
                    roomPlan.controllerPos(),
                    null,
                    result.reason()
                );
            }
        }
        return result;
    }

    private static PlacementResult placePlannedRoom(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        String dimensionKey,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan
    ) {
        return placePlannedRoom(level, repository, session, dimensionKey, placement, roomPlan, false);
    }

    private static PlacementResult placePlannedRoom(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        String dimensionKey,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        boolean branchPreview
    ) {
        return placePlannedRoom(
            level,
            repository,
            session,
            dimensionKey,
            placement,
            roomPlan,
            branchPreview,
            GeneratedRoomPlacementOperation.SliceBudget.unbounded()
        );
    }

    private static PlacementResult placePlannedRoom(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        String dimensionKey,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        boolean branchPreview,
        GeneratedRoomPlacementOperation.SliceBudget sliceBudget
    ) {
        if (!roomPlan.hasPlacementMetadata()) {
            return PlacementResult.skipped("LEGACY_PLAN_HAS_NO_PLACEMENT_METADATA");
        }
        if (placement.controllers().contains(roomPlan.controllerPos())) {
            String validationFailure = GeneratedRoomPlacementOperation.existingControllerValidationFailure(
                level, session, roomPlan, branchPreview
            );
            if (validationFailure == null) {
                releaseCompletedPredecessorTransitionSeal(level, session.instanceKey(), roomPlan.controllerPos());
                return PlacementResult.skipped("LOOKAHEAD_ALREADY_PLACED");
            }
            if (session.removePlacedController(roomPlan.controllerPos())) {
                repository.flushPlacement(session);
                placement = session.placement();
                LOGGER.warn(
                    "Stale generated room completion marker removed; resuming placement: roomIndex={} template={} controller={} reason={} dimension={}",
                    roomPlan.index(), roomPlan.templateLabel(), roomPlan.controllerPos(), validationFailure, dimensionKey
                );
            }
        }

        GeneratedRoomPlacementOperation.Result result = GeneratedRoomPlacementOperation.place(
            level,
            repository,
            session,
            new GeneratedRoomPlacementOperation.Request(
                roomPlan,
                null,
                placement.claimRadius(),
                branchPreview,
                GeneratedRoomPlacementOperation.SealPolicy.RUNTIME_OUTGOING,
                GeneratedRoomPlacementOperation.ControllerDiscoveryPolicy.EXPECTED_POSITION,
                true,
                true,
                true,
                true,
                preClaimMarkerForLazyPlacement(placement, roomPlan.index())
            ),
            sliceBudget
        );
        GeneratedRoomPlacementOperation.Metrics metrics = result.metrics();
        if (result.inProgress()) {
            return PlacementResult.inProgress(roomPlan.index(), result.reason());
        }
        if (result.failed()) {
            if (result.sealResult() != null && !result.sealResult().success()) {
                recordGenerationFailure(
                    level,
                    repository,
                    session,
                    GENERATED_OUTGOING_SEAL_FAILED,
                    roomPlan.controllerPos(),
                    result.sealResult().connectorPos(),
                    result.sealResult().reason()
                );
            } else {
                recordGenerationFailure(
                    level,
                    repository,
                    session,
                    GENERATED_ROOM_PLACEMENT_FAILED,
                    roomPlan.controllerPos(),
                    null,
                    result.reason()
                );
            }
            LOGGER.warn(
                "[TD-PERF] generated-lazy-room result=FAIL roomIndex={} template={} reason={} templateLoadMs={} structurePlaceMs={} itemCleanupMs={} controllerLookupMs={} configurationMs={} boundsWriteMs={} markerClaimMs={} readinessMs={} postProcessMs={} persistenceMs={} totalMs={} volume={} claimRadius={} removedPlacementDrops={} branchPreview={} dimension={}",
                roomPlan.index(), roomPlan.templateLabel(), result.reason(), metrics.templateLoadMs(),
                metrics.structurePlaceMs(), metrics.itemCleanupMs(), metrics.controllerLookupMs(),
                metrics.configurationMs(), metrics.boundsWriteMs(), metrics.markerClaimMs(), metrics.readinessMs(),
                metrics.postProcessMs(), metrics.persistenceMs(), metrics.totalMs(), roomVolume(roomPlan.bounds()),
                placement.claimRadius(), result.removedPlacementDrops(), branchPreview, dimensionKey
            );
            return PlacementResult.failed(roomPlan.index(), result.reason());
        }

        clearGenerationFailure(repository, session, GENERATED_OUTGOING_SEAL_FAILED);
        LOGGER.info(
            "[TD-PERF] generated-lazy-room result=PASS roomIndex={} template={} controller={} existingController={} templateLoadMs={} structurePlaceMs={} itemCleanupMs={} controllerLookupMs={} configurationMs={} boundsWriteMs={} markerClaimMs={} readinessMs={} postProcessMs={} persistenceMs={} totalMs={} volume={} claimRadius={} removedPlacementDrops={} branchPreview={} dimension={}",
            roomPlan.index(), roomPlan.templateLabel(), result.controllerPos(), result.existingController(),
            metrics.templateLoadMs(), metrics.structurePlaceMs(), metrics.itemCleanupMs(), metrics.controllerLookupMs(),
            metrics.configurationMs(), metrics.boundsWriteMs(), metrics.markerClaimMs(), metrics.readinessMs(),
            metrics.postProcessMs(), metrics.persistenceMs(), metrics.totalMs(), roomVolume(roomPlan.bounds()),
            placement.claimRadius(), result.removedPlacementDrops(), branchPreview, dimensionKey
        );
        LOGGER.info(
            "Generated lazy room placed: index={} template={} controller={} dimension={} removedPlacementDrops={} existingController={}",
            roomPlan.index(), roomPlan.templateLabel(), result.controllerPos(), dimensionKey,
            result.removedPlacementDrops(), result.existingController()
        );
        releaseCompletedPredecessorTransitionSeal(level, session.instanceKey(), roomPlan.controllerPos());
        return PlacementResult.placed(roomPlan.index(), result.controllerPos());
    }

    private static void releaseCompletedPredecessorTransitionSeal(
        ServerLevel level,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos placedControllerPos
    ) {
        if (placedControllerPos == null) return;
        GeneratedFloorRepository repository = repositoryFor(level, instanceKey);
        GeneratedFloorSession session = repository.hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null) return;
        int successorIndex = findPlanIndex(placement.roomPlans(), placedControllerPos);
        if (successorIndex <= 0) return;
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan predecessor =
            placement.roomPlans().get(successorIndex - 1);
        if (!(level.getBlockEntity(predecessor.controllerPos()) instanceof RoomControllerBlockEntity controller)
            || !controller.isCompletedRoomForRecovery()) {
            return;
        }
        openSuccessorIncomingSeal(level, instanceKey, predecessor.controllerPos());
    }

    @Nullable
    static BlockPos preClaimMarkerForLazyPlacement(
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        int roomIndex
    ) {
        return roomIndex == 1 && placement != null ? placement.floorBuildOrigin() : null;
    }

    private static double elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0D;
    }

    private static long roomVolume(RoomControllerBlockEntity.RoomBounds bounds) {
        if (bounds == null) {
            return 0L;
        }
        return (long) bounds.getWidth() * bounds.getHeight() * bounds.getDepth();
    }

    private record RuntimeConnector(BlockPos worldPos, Direction facing, boolean outgoingPreferred) {
    }

    public record PlacementResult(
        boolean placed,
        boolean failed,
        boolean inProgress,
        int roomIndex,
        BlockPos controllerPos,
        String reason
    ) {
        static PlacementResult placed(int roomIndex, BlockPos controllerPos) {
            return new PlacementResult(true, false, false, roomIndex, controllerPos, "PLACED");
        }

        static PlacementResult inProgress(int roomIndex, String reason) {
            return new PlacementResult(false, false, true, roomIndex, null, reason);
        }

        static PlacementResult skipped(String reason) {
            return new PlacementResult(false, false, false, 0, null, reason);
        }

        static PlacementResult failed(int roomIndex, String reason) {
            LOGGER.error("Generated lazy room placement failed: index={} reason={}", roomIndex, reason);
            return new PlacementResult(false, true, false, roomIndex, null, reason);
        }
    }
}
