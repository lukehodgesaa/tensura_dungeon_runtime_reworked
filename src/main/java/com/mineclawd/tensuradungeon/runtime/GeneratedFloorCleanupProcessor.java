package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.compat.FogBarriersCompat;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public final class GeneratedFloorCleanupProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int DEFAULT_BLOCKS_PER_TICK = 5000;
    private static final int MAX_LIQUID_CONFIRM_PASSES = 2;
    private static final int LIQUID_SWEEP_MARGIN = 1;

    private GeneratedFloorCleanupProcessor() {
    }

    public static int reconcilePersistedFogPanels(ServerLevel level) {
        if (level == null || level.getGameTime() % 20L != 0L) {
            return 0;
        }
        String dimensionKey = level.dimension().location().toString();
        int removed = 0;
        for (DungeonInstanceAllocationData.Allocation allocation
            : DungeonInstanceAllocationData.get(level).allocations()) {
            if (!dimensionKey.equals(allocation.dimension())) {
                continue;
            }
            removed += reconcileAllocationFogPanels(level, allocation);
        }
        return removed;
    }

    public static int reconcileNewAllocationFogPanels(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        if (level == null || allocation == null
            || !level.dimension().location().toString().equals(allocation.dimension())) {
            return 0;
        }
        return FogBarriersCompat.removeStaleDungeonPanelsInBounds(
            level,
            allocation.cell().bounds().min(),
            allocation.cell().bounds().max(),
            allocation.createdGameTime()
        );
    }

    private static int reconcileAllocationFogPanels(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        DungeonInstanceAllocationData.LifecycleState lifecycle = allocation.lifecycle().state();
        if (lifecycle == DungeonInstanceAllocationData.LifecycleState.FAILED_PREPARATION
            || lifecycle == DungeonInstanceAllocationData.LifecycleState.CLEANING
            || lifecycle == DungeonInstanceAllocationData.LifecycleState.REUSABLE
            || lifecycle == DungeonInstanceAllocationData.LifecycleState.QUARANTINED) {
            return FogBarriersCompat.removeDungeonPanelsInBounds(
                level,
                allocation.cell().bounds().min(),
                allocation.cell().bounds().max()
            );
        }
        return reconcileNewAllocationFogPanels(level, allocation);
    }

    public static void retryDeferredTerminalCleanup(ServerLevel level) {
        if (level.getGameTime() % 10L != 0L) {
            return;
        }
        String dimensionKey = level.dimension().location().toString();
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonRunManager runs = DungeonRunManager.get(level);
        DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);
        for (DungeonInstanceAllocationData.Allocation allocation : allocations.allocations()) {
            if (!dimensionKey.equals(allocation.dimension())) {
                continue;
            }
            boolean exactRunReturned = runs.hasInstance(allocation.key())
                && runs.canRetireInstance(allocation.key());
            if (!terminalCleanupRetryEligible(
                allocation,
                exactRunReturned,
                transport.safeForCleanup(level, allocation.key())
            )) {
                continue;
            }
            BeginCleanupResult result = beginExactInstanceCleanup(level, allocation.key());
            if (result.accepted() && result.changed()) {
                LOGGER.info(
                    "Deferred terminal cleanup resumed after subordinate hub return: instance={} epoch={} reason={}",
                    allocation.key().instanceId(), allocation.key().epoch(), result.reason()
                );
            }
        }
    }

    static boolean terminalCleanupRetryEligible(
        DungeonInstanceAllocationData.Allocation allocation,
        boolean exactRunReturned,
        boolean subordinateReturnSafe
    ) {
        return allocation != null
            && allocation.lifecycle().state()
                == DungeonInstanceAllocationData.LifecycleState.COMPLETING
            && exactRunReturned
            && subordinateReturnSafe
            && !allocation.roster().isEmpty()
            && allocation.roster().stream().allMatch(playerId ->
                allocation.memberDisposition(playerId)
                    == DungeonInstanceAllocationData.MemberDisposition.RETURN_TO_HUB_ON_LOGIN
            );
    }

    public static BeginCleanupResult beginExactInstanceCleanup(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (instanceKey == null) {
            return BeginCleanupResult.rejected("INSTANCE_KEY_MISSING");
        }
        String dimensionKey = level.dimension().location().toString();
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation allocation =
            allocations.forIdentity(instanceKey).orElse(null);
        if (allocation == null || !allocation.dimension().equals(dimensionKey)) {
            return BeginCleanupResult.rejected("TERMINAL_ALLOCATION_MISMATCH");
        }
        if (allocation.lifecycle().state()
                == DungeonInstanceAllocationData.LifecycleState.CLEANING) {
            FogBarriersCompat.removeDungeonPanelsInBounds(
                level,
                allocation.cell().bounds().min(),
                allocation.cell().bounds().max()
            );
            return BeginCleanupResult.accepted("CLEANUP_ALREADY_PENDING", false);
        }
        if (allocation.lifecycle().state()
                != DungeonInstanceAllocationData.LifecycleState.COMPLETING) {
            return BeginCleanupResult.rejected("TERMINAL_ALLOCATION_NOT_COMPLETING");
        }
        DungeonSubordinateTransportData.TransportResult subordinateReturn =
            DungeonSubordinateTransportData.get(level).returnToHub(level, allocation);
        if (!subordinateReturn.accepted()
            || !DungeonSubordinateTransportData.get(level).safeForCleanup(level, instanceKey)) {
            return BeginCleanupResult.rejected("SUBORDINATE_RETURN_UNRESOLVED");
        }
        GeneratedFloorRepository repository =
            GeneratedFloorRepository.get(level, dimensionKey, instanceKey);
        GeneratedFloorSession session = repository.hydrate();
        if (session.cleanupState() != null) {
            FogBarriersCompat.removeDungeonPanelsInBounds(
                level,
                allocation.cell().bounds().min(),
                allocation.cell().bounds().max()
            );
            if (!allocations.transition(
                instanceKey,
                DungeonInstanceAllocationData.LifecycleState.CLEANING,
                "EXACT_TERMINAL_CLEANUP_RECOVERED",
                level.getGameTime()
            )) {
                return BeginCleanupResult.rejected("CLEANUP_LIFECYCLE_TRANSITION_REJECTED");
            }
            return BeginCleanupResult.accepted("CLEANUP_ALREADY_PENDING", true);
        }
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null) {
            allocations.quarantine(
                instanceKey, "TERMINAL_CLEANUP_PLACEMENT_MISSING", level.getGameTime()
            );
            return BeginCleanupResult.rejected("TERMINAL_CLEANUP_PLACEMENT_MISSING");
        }
        if (!allocation.cell().bounds().contains(placement.floorBuildOrigin())
            || placement.bounds().stream().anyMatch(
                bounds -> bounds == null || !allocation.cell().bounds().contains(bounds)
            )) {
            allocations.quarantine(
                instanceKey, "TERMINAL_CLEANUP_OWNERSHIP_OUTSIDE_CELL", level.getGameTime()
            );
            return BeginCleanupResult.rejected("TERMINAL_CLEANUP_OWNERSHIP_OUTSIDE_CELL");
        }
        FogBarriersCompat.removeDungeonPanelsInBounds(
            level,
            allocation.cell().bounds().min(),
            allocation.cell().bounds().max()
        );
        GeneratedFloorRuntimeData.CleanupState cleanup =
            new GeneratedFloorRuntimeData.CleanupState(
                placement.floorBuildOrigin().immutable(),
                List.copyOf(placement.bounds()),
                0,
                0L,
                0L,
                0L,
                level.getGameTime()
            );
        session.replaceCleanupState(cleanup);
        repository.flushCleanup(session);
        session.removePlacement();
        repository.flushPlacement(session);
        session.clearComposition();
        repository.flushComposition(session);
        session.clearGenerationFailure();
        repository.flushGenerationFailure(session);
        if (!allocations.transition(
            instanceKey,
            DungeonInstanceAllocationData.LifecycleState.CLEANING,
            "EXACT_TERMINAL_CLEANUP_QUEUED",
            level.getGameTime()
        )) {
            allocations.quarantine(
                instanceKey, "TERMINAL_CLEANUP_LIFECYCLE_COMMIT_FAILED", level.getGameTime()
            );
            return BeginCleanupResult.rejected("TERMINAL_CLEANUP_LIFECYCLE_COMMIT_FAILED");
        }
        level.getServer().overworld().getDataStorage().save();
        return BeginCleanupResult.accepted("EXACT_TERMINAL_CLEANUP_QUEUED", true);
    }

    public static CleanupTickResult tick(ServerLevel level, String dimensionKey, int maxPositions) {
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(level).currentIdentity(dimensionKey).orElse(null);
        return tick(level, dimensionKey, instanceKey, maxPositions);
    }

    public static CleanupTickResult tick(
        ServerLevel level,
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        int maxPositions
    ) {
        GeneratedFloorRepository repository = instanceKey == null
            ? GeneratedFloorRepository.get(level, dimensionKey)
            : GeneratedFloorRepository.get(level, dimensionKey, instanceKey);
        GeneratedFloorSession session = repository.hydrate();
        GeneratedFloorRuntimeData.CleanupState state = session.cleanupState();
        if (state == null) {
            return CleanupTickResult.none();
        }
        DungeonInstanceAllocationData allocationData = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation allocation =
            instanceKey == null
                ? allocationData.currentForDimension(dimensionKey).orElse(null)
                : allocationData.forIdentity(instanceKey).orElse(null);
        if (instanceKey != null && (allocation == null
            || !allocation.dimension().equals(dimensionKey)
            || allocation.lifecycle().state().reusable()
            || allocation.lifecycle().state() == DungeonInstanceAllocationData.LifecycleState.QUARANTINED)) {
            LOGGER.warn(
                "Generated cleanup rejected stale instance before mutation: instance={} dimension={}",
                instanceKey, dimensionKey
            );
            return CleanupTickResult.none();
        }
        if (allocation != null && state.bounds().stream().anyMatch(
            bounds -> !allocation.cell().bounds().contains(bounds)
        )) {
            allocationData.quarantine(
                allocation.key(),
                "FULL_CLEANUP_BOUNDS_OUTSIDE_CELL",
                level.getGameTime()
            );
            LOGGER.error(
                "Generated cleanup quarantined instance before mutation: instance={} epoch={} cell={} dimension={}",
                allocation.key().instanceId(), allocation.key().epoch(), allocation.cell().index(),
                dimensionKey
            );
            return new CleanupTickResult(
                true, false, 0L, 0L, state.currentBoundsIndex(), state.progressPercent()
            );
        }
        long tickStartedAt = System.nanoTime();
        int budget = Math.max(1, maxPositions);
        if (state.complete() || state.bounds().isEmpty()) {
            restoreFloorBuildOriginIfSafe(level, state);
            session.removeCleanupState();
            repository.flushCleanup(session);
            CleanupTickResult result = new CleanupTickResult(true, true, 0L, 0L, 0, 100);
            logFullCleanup(level, dimensionKey, state, result, budget, tickStartedAt);
            return result;
        }
        if (hasPlayerInsideCleanupBounds(level, state)) {
            return new CleanupTickResult(
                true, false, 0L, 0L, state.currentBoundsIndex(), state.progressPercent()
            );
        }

        int boundIndex = state.currentBoundsIndex();
        long offset = state.currentBoundsOffset();
        long visited = 0L;
        long cleared = 0L;
        boolean liquidsThisTick = false;
        if (state.confirmPasses() == 0
            && state.visitedPositions() == 0L
            && boundIndex == 0
            && offset == 0L) {
            for (RoomControllerBlockEntity.RoomBounds bounds : state.bounds()) {
                GeneratedRoomItemCleanup.clearPlacementDrops(level, bounds);
            }
        }

        GeneratedFloorSession.CleanupProgress progress;
        GeneratedFloorRuntimeData.CleanupState next;
        if (!state.mainPassComplete()) {
            while (visited < budget && boundIndex < state.bounds().size()) {
                RoomControllerBlockEntity.RoomBounds bounds = state.bounds().get(boundIndex);
                long volume = volume(bounds);
                if (offset >= volume) {
                    boundIndex++;
                    offset = 0L;
                    continue;
                }

                BlockPos pos = posFromOffset(bounds, offset);
                BlockState current = level.getBlockState(pos);
                if (!pos.equals(state.floorBuildOrigin()) && !current.isAir()) {
                    liquidsThisTick |= !current.getFluidState().isEmpty();
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    cleared++;
                }
                visited++;
                offset++;
            }

            boolean passFinished = boundIndex >= state.bounds().size();
            boolean anyLiquidsObserved = state.liquidsObserved() || liquidsThisTick;
            int nextConfirmPasses = state.confirmPasses();
            if (passFinished && anyLiquidsObserved && nextConfirmPasses < MAX_LIQUID_CONFIRM_PASSES) {
                nextConfirmPasses++;
                boundIndex = 0;
                offset = 0L;
                visited = 0L;
            }
            if (passFinished
                && anyLiquidsObserved
                && nextConfirmPasses >= MAX_LIQUID_CONFIRM_PASSES
                && state.liquidSweepBounds().isEmpty()
                && allocation != null) {
                List<RoomControllerBlockEntity.RoomBounds> sweep =
                    liquidSweepBoundsFor(allocation, state.bounds());
                if (!sweep.isEmpty()) {
                    session.setLiquidSweepBounds(sweep);
                }
            }
            progress = session.advanceCleanup(
                boundIndex,
                offset,
                visited,
                cleared,
                liquidsThisTick,
                nextConfirmPasses
            );
            next = progress == null ? null : progress.state();
        } else if (!state.liquidSweepComplete()) {
            List<Long> frontier = new ArrayList<>();
            for (long value : state.liquidFrontier()) {
                frontier.add(value);
            }
            int frontierIndex = state.liquidFrontierIndex();
            int sweepIndex = state.liquidSweepBoundsIndex();
            long sweepOffset = state.liquidSweepOffset();
            long ops = 0L;
            long sweepCleared = 0L;
            while (ops < budget) {
                if (frontierIndex < frontier.size()) {
                    BlockPos pos = BlockPos.of(frontier.get(frontierIndex++));
                    BlockState current = level.getBlockState(pos);
                    if (!current.isAir() && !current.getFluidState().isEmpty()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        sweepCleared++;
                        enqueueLiquidNeighbors(level, pos, frontier);
                    }
                    ops++;
                    continue;
                }
                if (sweepIndex >= state.liquidSweepBounds().size()) {
                    break;
                }
                RoomControllerBlockEntity.RoomBounds sweepBounds = state.liquidSweepBounds().get(sweepIndex);
                long sweepVolume = volume(sweepBounds);
                if (sweepOffset >= sweepVolume) {
                    sweepIndex++;
                    sweepOffset = 0L;
                    continue;
                }
                BlockPos pos = posFromOffset(sweepBounds, sweepOffset);
                sweepOffset++;
                ops++;
                BlockState current = level.getBlockState(pos);
                if (!current.isAir() && !current.getFluidState().isEmpty()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    sweepCleared++;
                    enqueueLiquidNeighbors(level, pos, frontier);
                }
            }
            long[] nextFrontier = new long[frontier.size() - frontierIndex];
            for (int i = frontierIndex; i < frontier.size(); i++) {
                nextFrontier[i - frontierIndex] = frontier.get(i);
            }
            visited = ops;
            cleared = sweepCleared;
            progress = session.advanceLiquidSweep(
                sweepIndex,
                sweepOffset,
                sweepCleared,
                nextFrontier,
                0
            );
            next = progress == null ? null : progress.state();
        } else {
            progress = null;
            next = state;
        }

        if (next != null && next.complete()) {
            restoreFloorBuildOriginIfSafe(level, next);
            repository.flushCleanup(session);
            CleanupTickResult result = new CleanupTickResult(true, true, visited, cleared, boundIndex, 100);
            logFullCleanup(level, dimensionKey, next, result, budget, tickStartedAt);
            return result;
        }
        if (next == null) {
            return CleanupTickResult.none();
        }

        repository.flushCleanup(session);
        CleanupTickResult result = new CleanupTickResult(true, false, visited, cleared, next.currentBoundsIndex(), next.progressPercent());
        logFullCleanup(level, dimensionKey, next, result, budget, tickStartedAt);
        return result;
    }

    private static void restoreFloorBuildOriginIfSafe(
        ServerLevel level,
        GeneratedFloorRuntimeData.CleanupState state
    ) {
        String validation = state.floorBuildOriginValidation(128);
        if (!validation.startsWith("VALID_")) {
            LOGGER.warn("Generated cleanup skipped unsafe floor-build-origin restoration: origin={} reason={}",
                state.floorBuildOrigin(), validation);
            return;
        }
        if (!state.ownsFloorBuildOrigin()
            && !level.getBlockState(state.floorBuildOrigin()).isAir()
            && !level.getBlockState(state.floorBuildOrigin()).is(ModBlocks.FLOOR_BUILD_ORIGIN.get())) {
            LOGGER.warn("Generated cleanup skipped occupied floor-build-origin restoration: origin={} reason=UNOWNED_BLOCK",
                state.floorBuildOrigin());
            return;
        }
        level.setBlock(state.floorBuildOrigin(), ModBlocks.FLOOR_BUILD_ORIGIN.get().defaultBlockState(), 3);
    }

    public static CleanupTickResult tickPrune(ServerLevel level, String dimensionKey, int maxPositions) {
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(level).currentIdentity(dimensionKey).orElse(null);
        return tickPrune(level, dimensionKey, instanceKey, maxPositions);
    }

    public static CleanupTickResult tickPrune(
        ServerLevel level,
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        int maxPositions
    ) {
        GeneratedFloorRepository repository = instanceKey == null
            ? GeneratedFloorRepository.get(level, dimensionKey)
            : GeneratedFloorRepository.get(level, dimensionKey, instanceKey);
        GeneratedFloorSession session = repository.hydrate();
        if (session.hasPendingCleanup()) return CleanupTickResult.none();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || placement.pruneState() == null) return CleanupTickResult.none();
        DungeonInstanceAllocationData allocationData = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation allocation =
            instanceKey == null
                ? allocationData.currentForDimension(dimensionKey).orElse(null)
                : allocationData.forIdentity(instanceKey).orElse(null);
        if (instanceKey != null && (allocation == null
            || !allocation.dimension().equals(dimensionKey)
            || allocation.lifecycle().state().reusable()
            || allocation.lifecycle().state() == DungeonInstanceAllocationData.LifecycleState.QUARANTINED)) {
            LOGGER.warn(
                "Generated prune rejected stale instance before mutation: instance={} dimension={}",
                instanceKey, dimensionKey
            );
            return CleanupTickResult.none();
        }
        if (allocation != null && placement.pruneState().bounds().stream().anyMatch(
            bounds -> !allocation.cell().bounds().contains(bounds)
        )) {
            allocationData.quarantine(
                allocation.key(),
                "PRUNE_BOUNDS_OUTSIDE_CELL",
                level.getGameTime()
            );
            LOGGER.error(
                "Generated prune quarantined instance before mutation: instance={} epoch={} cell={} dimension={}",
                allocation.key().instanceId(), allocation.key().epoch(), allocation.cell().index(),
                dimensionKey
            );
            return new CleanupTickResult(
                true, false, 0L, 0L, placement.pruneState().boundsIndex(), 0
            );
        }
        long tickStartedAt = System.nanoTime();
        int budget = Math.max(1, maxPositions);
        GeneratedFloorRuntimeData.PruneState state = placement.pruneState();
        if (state.complete()) {
            session.clearCompletedPrune();
            repository.flushPlacement(session);
            CleanupTickResult result = new CleanupTickResult(true, true, 0L, 0L, state.boundsIndex(), 100);
            logPrune(level, dimensionKey, placement.floorBuildOrigin(), state, result, budget, tickStartedAt);
            return result;
        }

        int boundIndex = state.boundsIndex();
        long offset = state.boundsOffset();
        long visited = 0L;
        long cleared = 0L;
        while (visited < budget && boundIndex < state.bounds().size()) {
            RoomControllerBlockEntity.RoomBounds bounds = state.bounds().get(boundIndex);
            long volume = volume(bounds);
            if (offset >= volume) {
                boundIndex++;
                offset = 0L;
                continue;
            }
            BlockPos pos = posFromOffset(bounds, offset++);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                cleared++;
            }
            visited++;
        }

        GeneratedFloorSession.PruneProgress pruneProgress = session.advancePrune(boundIndex, offset);
        GeneratedFloorRuntimeData.PruneState next = pruneProgress.state();
        repository.flushPlacement(session);
        int progress = state.bounds().isEmpty() ? 100 : Math.min(100, (boundIndex * 100) / state.bounds().size());
        CleanupTickResult result = new CleanupTickResult(true, pruneProgress.completed(), visited, cleared, boundIndex, progress);
        logPrune(level, dimensionKey, placement.floorBuildOrigin(), next, result, budget, tickStartedAt);
        return result;
    }

    private static void logFullCleanup(
        ServerLevel level,
        String dimensionKey,
        GeneratedFloorRuntimeData.CleanupState state,
        CleanupTickResult result,
        int budget,
        long tickStartedAt
    ) {
        LOGGER.debug("[TD-PERF] generated-cleanup mode=FULL completed={} originLong={} bounds={} budget={} visitedThisTick={} clearedThisTick={} visitedTotal={} clearedTotal={} boundsIndex={} boundsOffset={} progress={} startedGameTime={} gameTime={} elapsedMs={} dimension={}",
            result.completed(), state.floorBuildOrigin().asLong(), state.bounds().size(), budget,
            result.visitedPositions(), result.clearedBlocks(), state.visitedPositions(), state.clearedBlocks(),
            state.currentBoundsIndex(), state.currentBoundsOffset(), result.progressPercent(), state.startedGameTime(),
            level.getGameTime(), elapsedMillis(tickStartedAt), dimensionKey);
    }

    private static void logPrune(
        ServerLevel level,
        String dimensionKey,
        BlockPos floorBuildOrigin,
        GeneratedFloorRuntimeData.PruneState state,
        CleanupTickResult result,
        int budget,
        long tickStartedAt
    ) {
        LOGGER.debug("[TD-PERF] generated-cleanup mode=PRUNE completed={} originLong={} bounds={} budget={} visitedThisTick={} clearedThisTick={} boundsIndex={} boundsOffset={} progress={} gameTime={} elapsedMs={} dimension={}",
            result.completed(), floorBuildOrigin.asLong(), state.bounds().size(), budget,
            result.visitedPositions(), result.clearedBlocks(), state.boundsIndex(), state.boundsOffset(),
            result.progressPercent(), level.getGameTime(), elapsedMillis(tickStartedAt), dimensionKey);
    }

    private static double elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0D;
    }

    private static boolean hasPlayerInsideCleanupBounds(
        ServerLevel level,
        GeneratedFloorRuntimeData.CleanupState state
    ) {
        return level.players().stream()
            .filter(player -> !player.isSpectator())
            .anyMatch(player -> state.bounds().stream().anyMatch(bounds -> bounds.contains(player.blockPosition()))
                || state.liquidSweepBounds().stream().anyMatch(bounds -> bounds.contains(player.blockPosition())));
    }

    private static List<RoomControllerBlockEntity.RoomBounds> liquidSweepBoundsFor(
        DungeonInstanceAllocationData.Allocation allocation,
        List<RoomControllerBlockEntity.RoomBounds> roomBounds
    ) {
        if (allocation == null || allocation.cell() == null || allocation.cell().bounds() == null
            || roomBounds == null || roomBounds.isEmpty()) {
            return List.of();
        }
        DungeonInstanceAllocationData.CellBounds cell = allocation.cell().bounds();
        List<RoomControllerBlockEntity.RoomBounds> sweep = new ArrayList<>();
        for (RoomControllerBlockEntity.RoomBounds bounds : roomBounds) {
            if (bounds == null) {
                continue;
            }
            int minX = Math.max(cell.min().getX(), bounds.min().getX() - LIQUID_SWEEP_MARGIN);
            int minY = Math.max(cell.min().getY(), bounds.min().getY() - LIQUID_SWEEP_MARGIN);
            int minZ = Math.max(cell.min().getZ(), bounds.min().getZ() - LIQUID_SWEEP_MARGIN);
            int maxX = Math.min(cell.max().getX(), bounds.max().getX() + LIQUID_SWEEP_MARGIN);
            int maxY = Math.min(cell.max().getY(), bounds.max().getY() + LIQUID_SWEEP_MARGIN);
            int maxZ = Math.min(cell.max().getZ(), bounds.max().getZ() + LIQUID_SWEEP_MARGIN);
            if (minX <= maxX && minY <= maxY && minZ <= maxZ) {
                sweep.add(new RoomControllerBlockEntity.RoomBounds(
                    new BlockPos(minX, minY, minZ),
                    new BlockPos(maxX, maxY, maxZ)
                ));
            }
        }
        return List.copyOf(sweep);
    }

    private static void enqueueLiquidNeighbors(
        ServerLevel level,
        BlockPos pos,
        List<Long> frontier
    ) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.getBlockState(neighbor).getFluidState().isEmpty()) {
                frontier.add(neighbor.asLong());
            }
        }
    }

    public static BlockPos posFromOffset(RoomControllerBlockEntity.RoomBounds bounds, long offset) {
        int width = bounds.getWidth();
        int depth = bounds.getDepth();
        long layerSize = (long) width * depth;
        int y = (int) (offset / layerSize);
        long layerOffset = offset % layerSize;
        int z = (int) (layerOffset / width);
        int x = (int) (layerOffset % width);
        return new BlockPos(bounds.min().getX() + x, bounds.min().getY() + y, bounds.min().getZ() + z);
    }

    public static long volume(RoomControllerBlockEntity.RoomBounds bounds) {
        return (long) bounds.getWidth() * bounds.getHeight() * bounds.getDepth();
    }

    public record CleanupTickResult(boolean attempted, boolean completed, long visitedPositions, long clearedBlocks, int currentBoundsIndex, int progressPercent) {
        public static CleanupTickResult none() {
            return new CleanupTickResult(false, false, 0L, 0L, 0, 100);
        }
    }

    public record BeginCleanupResult(boolean accepted, String reason, boolean changed) {
        static BeginCleanupResult accepted(String reason, boolean changed) {
            return new BeginCleanupResult(true, reason, changed);
        }

        static BeginCleanupResult rejected(String reason) {
            return new BeginCleanupResult(false, reason, false);
        }
    }
}
