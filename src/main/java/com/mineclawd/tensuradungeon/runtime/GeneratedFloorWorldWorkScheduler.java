package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.network.DungeonPreparationReadyPayload;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

public final class GeneratedFloorWorldWorkScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final double GREEN_TO_YELLOW_MS = 35.0D;
    static final double YELLOW_TO_GREEN_MS = 32.0D;
    static final double YELLOW_TO_RED_MS = 45.0D;
    static final double RED_TO_YELLOW_MS = 40.0D;
    static final int GREEN_CLEANUP_BUDGET = GeneratedFloorCleanupProcessor.DEFAULT_BLOCKS_PER_TICK;
    static final int YELLOW_CLEANUP_BUDGET = 1_250;
    private static final int GREEN_CHUNKS_PER_TICK = 2;
    private static final int YELLOW_CHUNKS_PER_TICK = 1;
    private static final long GREEN_PLACEMENT_SLICE_NANOS = 15_000_000L;
    private static final long YELLOW_PLACEMENT_SLICE_NANOS = 6_000_000L;
    private static final long RED_PLACEMENT_SLICE_NANOS = 2_000_000L;
    private static final int GREEN_BLOCK_OPERATIONS = 163_840;
    private static final int YELLOW_BLOCK_OPERATIONS = 65_536;
    private static final int RED_BLOCK_OPERATIONS = 16_384;
    private static final int GREEN_FLUID_OPERATIONS = 256;
    private static final int YELLOW_FLUID_OPERATIONS = 128;
    private static final int RED_FLUID_OPERATIONS = 64;
    private static final int CHUNK_TICKET_TIMEOUT_TICKS = 40;
    private static final TicketType<Long> WORLD_WORK_CHUNK_TICKET = TicketType.create(
        "tensura_dungeon_world_work",
        Long::compare,
        CHUNK_TICKET_TIMEOUT_TICKS
    );
    private static final long INITIAL_LOOKAHEAD_DELAY_TICKS = 10L;
    private static final long BETWEEN_LOOKAHEAD_ROOMS_TICKS = 10L;
    private static final int LOOKAHEAD_PLACEMENT_ATTEMPTS = 2;
    private static final long STARVATION_PROMOTION_TICKS = 100L;
    private static final Map<MinecraftServer, SchedulerState> STATES = new WeakHashMap<>();

    private GeneratedFloorWorldWorkScheduler() {
    }

    public static void beginTick(ServerLevel level) {
        SchedulerState state = state(level);
        int serverTick = level.getServer().getTickCount();
        if (state.serverTick == serverTick) {
            return;
        }
        state.serverTick = serverTick;
        state.placementClaimed = false;
        state.cleanupClaimed = false;
        double averageTickMs = level.getServer().getAverageTickTimeNanos() / 1_000_000.0D;
        state.loadBand = nextLoadBand(state.loadBand, averageTickMs);
        state.averageTickMs = averageTickMs;
    }

    public static void requestLookahead(ServerLevel level, BlockPos activatedControllerPos) {
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(level)
                .currentIdentity(level.dimension().location().toString())
                .orElse(null);
        requestLookahead(level, instanceKey, activatedControllerPos);
    }

    public static void requestLookahead(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos activatedControllerPos
    ) {
        requestPlacement(
            level, instanceKey, activatedControllerPos, Urgency.FRONTIER, LOOKAHEAD_PLACEMENT_ATTEMPTS,
            level.getGameTime() + INITIAL_LOOKAHEAD_DELAY_TICKS, false
        );
    }

    public static void requestCompletedRoomSuccessor(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos completedControllerPos
    ) {
        requestPlacement(
            level, instanceKey, completedControllerPos, Urgency.FRONTIER,
            LOOKAHEAD_PLACEMENT_ATTEMPTS, level.getGameTime(), false
        );
    }

    public static void requestCriticalStartupLookahead(
        ServerLevel level,
        BlockPos activatedControllerPos,
        boolean forcedByMaximumDelay
    ) {
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(level)
                .currentIdentity(level.dimension().location().toString())
                .orElse(null);
        requestCriticalStartupLookahead(
            level, instanceKey, activatedControllerPos, forcedByMaximumDelay
        );
    }

    public static void requestCriticalStartupBootstrap(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos plannedFirstControllerPos
    ) {
        requestPlacement(
            level, instanceKey, plannedFirstControllerPos, Urgency.CRITICAL, 2,
            level.getGameTime() + INITIAL_LOOKAHEAD_DELAY_TICKS, false
        );
    }

    public static void requestCriticalStartupLookahead(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos activatedControllerPos,
        boolean forcedByMaximumDelay
    ) {
        requestPlacement(
            level, instanceKey, activatedControllerPos,
            forcedByMaximumDelay ? Urgency.FORCED : Urgency.CRITICAL,
            1, level.getGameTime(), false
        );
    }

    public static void processPlacement(ServerLevel level) {
        beginTick(level);
        GeneratedFloorCleanupProcessor.reconcilePersistedFogPanels(level);
        SchedulerState state = state(level);
        ResourceKey<Level> dimension = level.dimension();
        String dimensionKey = dimension.location().toString();
        DungeonInstanceAllocationData allocationData = DungeonInstanceAllocationData.get(level);
        reconcilePermanentGenerationFailures(
            level, dimensionKey, allocationData, state
        );
        for (PendingPlacement reconstructed : reconstructPlacements(level)) {
            if (reconstructed.instanceKey != null) {
                state.placements.putIfAbsent(
                    new WorkKey(dimension, reconstructed.instanceKey), reconstructed
                );
            }
        }
        List<WorkKey> staleKeys = state.placements.keySet().stream()
            .filter(key -> key.dimension().equals(dimension))
            .filter(key -> !allocationData.matchesLive(dimensionKey, key.instanceKey()))
            .toList();
        for (WorkKey staleKey : staleKeys) {
            PendingPlacement stale = state.placements.remove(staleKey);
            if (stale != null) {
                releaseChunkTickets(level, stale);
                LOGGER.warn(
                    "[TD-SCHED] work=PLACEMENT action=STALE_INSTANCE_REJECTED expected={} dimension={}",
                    staleKey.instanceKey(), dimension.location()
                );
            }
        }
        WorkKey workKey = selectPlacementWork(
            state, dimension, level.getGameTime(), state.loadBand
        );
        if (workKey == null) return;
        PendingPlacement pending = state.placements.get(workKey);
        if (pending == null) return;
        long queueAgeTicks = Math.max(0L, level.getGameTime() - pending.enqueuedGameTime);
        Urgency effectiveUrgency = effectiveUrgency(pending.urgency, queueAgeTicks);

        boolean activeRunRequired = pending.urgency == Urgency.NORMAL
            || pending.urgency == Urgency.HIGH || pending.urgency == Urgency.FRONTIER;
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (activeRunRequired && (!manager.bindInstance(workKey.instanceKey())
            || !manager.isActiveRun() || manager.isRunCompleted())) {
            releaseChunkTickets(level, pending);
            state.placements.remove(workKey);
            return;
        }
        if (!GeneratedFloorLazyPlacementService.needsLookahead(
            level, workKey.instanceKey(), pending.activatedControllerPos
        )) {
            releaseChunkTickets(level, pending);
            state.placements.remove(workKey);
            return;
        }
        if (state.placementClaimed) {
            return;
        }
        state.lastPlacementInstance = workKey.instanceKey();
        if (!prepareChunks(level, pending, chunkBudget(state.loadBand, effectiveUrgency))) {
            return;
        }

        state.placementClaimed = true;
        long started = System.nanoTime();
        GeneratedFloorLazyPlacementService.PlacementResult result =
            GeneratedFloorLazyPlacementService.ensureLookahead(
                level,
                workKey.instanceKey(),
                pending.activatedControllerPos,
                placementSliceBudget(state.loadBand)
            );
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0D;
        state.lastPlacementMs = elapsedMs;
        state.lastPlacementGameTime = level.getGameTime();
        int remainingAttempts = result.inProgress()
            ? pending.remainingAttempts : pending.remainingAttempts - 1;
        LOGGER.atLevel(result.inProgress() ? org.slf4j.event.Level.DEBUG : org.slf4j.event.Level.INFO).log(
            "[TD-SCHED] work=PLACEMENT urgency={} effectiveUrgency={} band={} reconstructed={} activatedController={} placed={} failed={} inProgress={} roomIndex={} reason={} queueAgeTicks={} elapsedMs={} remainingAttempts={} dimension={}",
            pending.urgency, effectiveUrgency, state.loadBand, pending.reconstructed,
            pending.activatedControllerPos, result.placed(), result.failed(), result.inProgress(),
            result.roomIndex(), result.reason(), queueAgeTicks, elapsedMs,
            Math.max(0, remainingAttempts), dimension.location()
        );

        if (result.inProgress()) {
            return;
        }
        releaseChunkTickets(level, pending);

        if (result.failed() && isPreparing(allocationData, workKey.instanceKey())) {
            failStartupPreparation(level, dimensionKey, workKey.instanceKey(), result.reason());
            state.placements.remove(workKey);
            return;
        }
        if (result.failed() && GeneratedFloorLazyPlacementService.hasPermanentGenerationFailure(
            level, workKey.instanceKey()
        )) {
            state.placements.remove(workKey);
            return;
        }
        if (result.placed() && startupRoomsReady(level, dimensionKey, workKey.instanceKey())) {
            boolean becameReady = allocationData.transition(
                workKey.instanceKey(),
                DungeonInstanceAllocationData.LifecycleState.READY,
                "TWO_STARTUP_ROOMS_VERIFIED",
                level.getGameTime()
            );
            if (becameReady) {
                DungeonInstanceAllocationData.Allocation ready = allocationData
                    .forIdentity(workKey.instanceKey()).orElse(null);
                ServerPlayer host = ready == null
                    ? null
                    : level.getServer().getPlayerList().getPlayer(ready.host());
                if (host != null && ready.soloPreparation() != null) {
                    PreparationTiming timing = preparationTiming(level, ready);
                    PacketDistributor.sendToPlayer(
                        host,
                        new DungeonPreparationReadyPayload(
                            ready.soloPreparation().requestedFloor(),
                            timing.elapsedSeconds()
                        )
                    );
                }
            }
        }
        if (result.placed() && remainingAttempts > 0
            && GeneratedFloorLazyPlacementService.needsLookahead(
                level, workKey.instanceKey(), pending.activatedControllerPos
            )) {
            state.placements.put(
                workKey,
                pending.nextAttempt(remainingAttempts, level.getGameTime() + BETWEEN_LOOKAHEAD_ROOMS_TICKS)
            );
        } else {
            state.placements.remove(workKey);
        }
    }

    public static void processCleanup(ServerLevel level) {
        beginTick(level);
        SchedulerState state = state(level);
        if (state.cleanupClaimed) {
            return;
        }
        int budget = cleanupBudget(state.loadBand, state.placementClaimed);
        if (budget <= 0) {
            return;
        }
        String dimensionKey = level.dimension().location().toString();
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        List<DungeonInstanceAllocationData.InstanceKey> cleanupCandidates = allocations.allocations().stream()
            .filter(allocation -> allocation.dimension().equals(dimensionKey))
            .filter(allocation -> !allocation.lifecycle().state().reusable())
            .filter(allocation ->
                allocation.lifecycle().state() != DungeonInstanceAllocationData.LifecycleState.QUARANTINED
            )
            .map(DungeonInstanceAllocationData.Allocation::key)
            .filter(instanceKey -> {
                GeneratedFloorSession session =
                    GeneratedFloorRepository.get(level, dimensionKey, instanceKey).hydrate();
                return session.cleanupState() != null
                    || session.placement() != null && session.placement().pruneState() != null;
            })
            .toList();
        DungeonInstanceAllocationData.InstanceKey selected =
            selectRoundRobin(cleanupCandidates, state.lastCleanupInstance);
        if (selected == null) {
            return;
        }
        GeneratedFloorCleanupProcessor.CleanupTickResult full =
            GeneratedFloorCleanupProcessor.tick(level, dimensionKey, selected, budget);
        if (full.attempted()) {
            state.cleanupClaimed = true;
            state.lastCleanupInstance = selected;
            return;
        }
        GeneratedFloorCleanupProcessor.CleanupTickResult prune =
            GeneratedFloorCleanupProcessor.tickPrune(level, dimensionKey, selected, budget);
        state.cleanupClaimed = prune.attempted();
        if (prune.attempted()) {
            state.lastCleanupInstance = selected;
        }
    }

    public static Snapshot snapshot(ServerLevel level) {
        beginTick(level);
        SchedulerState state = state(level);
        WorkKey selected = selectPlacementWork(
            state, level.dimension(), level.getGameTime(), state.loadBand
        );
        PendingPlacement pending = selected == null ? null : state.placements.get(selected);
        DungeonInstanceAllocationData.InstanceKey instanceKey = pending == null
            ? DungeonInstanceAllocationData.get(level)
                .currentIdentity(level.dimension().location().toString())
                .orElse(null)
            : pending.instanceKey;
        return new Snapshot(
            state.loadBand,
            state.averageTickMs,
            pending == null ? "IDLE" : pending.chunkPreparationComplete ? "PLACEMENT_READY" : "CHUNK_PREPARATION",
            pending == null ? "NONE" : pending.urgency.name(),
            pending == null ? 0L : Math.max(0L, level.getGameTime() - pending.enqueuedGameTime),
            state.lastPlacementMs,
            state.lastPlacementGameTime,
            cleanupBudget(state.loadBand, state.placementClaimed),
            instanceKey == null ? "NONE" : instanceKey.instanceId().toString(),
            instanceKey == null ? 0L : instanceKey.epoch()
        );
    }

    public static PreparationTiming preparationTiming(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        if (level == null || allocation == null || allocation.soloPreparation() == null) {
            return PreparationTiming.unavailable();
        }
        long elapsedTicks = Math.max(
            0L,
            level.getGameTime() - allocation.soloPreparation().requestedGameTime()
        );
        int elapsedSeconds = elapsedSeconds(
            elapsedTicks,
            level.getServer().getAverageTickTimeNanos() / 1_000_000.0D
        );
        if (allocation.lifecycle().state()
            == DungeonInstanceAllocationData.LifecycleState.READY) {
            return new PreparationTiming(elapsedSeconds, 0, 100);
        }
        if (allocation.lifecycle().state()
            != DungeonInstanceAllocationData.LifecycleState.PREPARING) {
            return new PreparationTiming(elapsedSeconds, -1, 0);
        }

        GeneratedFloorSession session = GeneratedFloorRepository.get(
            level,
            allocation.dimension(),
            allocation.key()
        ).hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || placement.roomPlans().size() < 2) {
            return new PreparationTiming(elapsedSeconds, -1, 0);
        }
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> startupPlans =
            placement.roomPlans().subList(0, 2);
        long totalUnits = startupPlans.stream()
            .mapToLong(GeneratedFloorWorldWorkScheduler::estimatedPlanUnits)
            .sum();
        long completedUnits = startupPlans.stream()
            .filter(plan -> placement.controllers().contains(plan.controllerPos()))
            .mapToLong(GeneratedFloorWorldWorkScheduler::estimatedPlanUnits)
            .sum();
        GeneratedRoomIncrementalPlacementEngine.ProgressSnapshot active =
            GeneratedRoomIncrementalPlacementEngine.progress(level, allocation.key());
        if (active.active()) {
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan activePlan = startupPlans.stream()
                .filter(plan -> plan.index() == active.roomIndex())
                .findFirst()
                .orElse(null);
            if (activePlan != null
                && !placement.controllers().contains(activePlan.controllerPos())) {
                completedUnits += Math.round(
                    estimatedPlanUnits(activePlan) * active.fraction()
                );
            }
        }
        double progress = totalUnits <= 0L
            ? 0.0D
            : Math.min(1.0D, completedUnits / (double) totalUnits);
        int progressPercent = (int) Math.floor(progress * 100.0D);
        return new PreparationTiming(
            elapsedSeconds,
            estimateRemainingSeconds(elapsedSeconds, progress),
            progressPercent
        );
    }

    static int elapsedSeconds(long elapsedTicks, double averageTickMs) {
        if (elapsedTicks <= 0L) return 0;
        double boundedTickMs = Double.isFinite(averageTickMs)
            ? Math.max(50.0D, Math.min(2_000.0D, averageTickMs))
            : 50.0D;
        return (int) Math.min(
            359_999L,
            Math.max(1L, (long) Math.ceil(elapsedTicks * boundedTickMs / 1_000.0D))
        );
    }

    static int estimateRemainingSeconds(int elapsedSeconds, double progress) {
        if (elapsedSeconds <= 0 || !Double.isFinite(progress) || progress < 0.03D) {
            return -1;
        }
        if (progress >= 1.0D) return 0;
        double remaining = elapsedSeconds * (1.0D - progress) / progress;
        return (int) Math.min(3_599L, Math.max(1L, (long) Math.ceil(remaining)));
    }

    private static long estimatedPlanUnits(
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan
    ) {
        if (plan == null || plan.bounds() == null) return 1L;
        return Math.max(
            1L,
            (long) plan.bounds().getWidth()
                * plan.bounds().getHeight()
                * plan.bounds().getDepth()
        );
    }

    static LoadBand nextLoadBand(LoadBand current, double averageTickMs) {
        if (!Double.isFinite(averageTickMs) || averageTickMs < 0.0D) {
            return current;
        }
        return switch (current) {
            case GREEN -> averageTickMs >= YELLOW_TO_RED_MS
                ? LoadBand.RED
                : averageTickMs >= GREEN_TO_YELLOW_MS ? LoadBand.YELLOW : LoadBand.GREEN;
            case YELLOW -> averageTickMs >= YELLOW_TO_RED_MS
                ? LoadBand.RED
                : averageTickMs < YELLOW_TO_GREEN_MS ? LoadBand.GREEN : LoadBand.YELLOW;
            case RED -> averageTickMs < YELLOW_TO_GREEN_MS
                ? LoadBand.GREEN
                : averageTickMs < RED_TO_YELLOW_MS ? LoadBand.YELLOW : LoadBand.RED;
        };
    }

    static boolean placementAllowed(LoadBand band, Urgency urgency) {
        return switch (band) {
            case GREEN -> true;
            case YELLOW -> urgency != Urgency.NORMAL;
            case RED -> urgency == Urgency.FORCED || urgency == Urgency.FRONTIER;
        };
    }

    static Urgency effectiveUrgency(Urgency urgency, long queueAgeTicks) {
        if (urgency == null) return Urgency.NORMAL;
        if (urgency == Urgency.FORCED || urgency == Urgency.FRONTIER) return urgency;
        long promotions = Math.max(0L, queueAgeTicks) / STARVATION_PROMOTION_TICKS;
        int promotedOrdinal = (int) Math.min(
            Urgency.FRONTIER.ordinal(),
            urgency.ordinal() + promotions
        );
        return Urgency.values()[promotedOrdinal];
    }

    static int cleanupBudget(LoadBand band, boolean placementClaimed) {
        if (placementClaimed) {
            return 0;
        }
        return switch (band) {
            case GREEN -> GREEN_CLEANUP_BUDGET;
            case YELLOW -> YELLOW_CLEANUP_BUDGET;
            case RED -> 0;
        };
    }

    static GeneratedRoomPlacementOperation.SliceBudget placementSliceBudget(LoadBand band) {
        return switch (band) {
            case GREEN -> new GeneratedRoomPlacementOperation.SliceBudget(
                GREEN_PLACEMENT_SLICE_NANOS,
                GREEN_BLOCK_OPERATIONS,
                GREEN_FLUID_OPERATIONS,
                8
            );
            case YELLOW -> new GeneratedRoomPlacementOperation.SliceBudget(
                YELLOW_PLACEMENT_SLICE_NANOS,
                YELLOW_BLOCK_OPERATIONS,
                YELLOW_FLUID_OPERATIONS,
                4
            );
            case RED -> new GeneratedRoomPlacementOperation.SliceBudget(
                RED_PLACEMENT_SLICE_NANOS,
                RED_BLOCK_OPERATIONS,
                RED_FLUID_OPERATIONS,
                1
            );
        };
    }

    private static int chunkBudget(LoadBand band, Urgency urgency) {
        return switch (band) {
            case GREEN -> GREEN_CHUNKS_PER_TICK;
            case YELLOW -> urgency != Urgency.NORMAL
                ? YELLOW_CHUNKS_PER_TICK : 0;
            case RED -> urgency == Urgency.FORCED ? YELLOW_CHUNKS_PER_TICK : 0;
        };
    }

    private static void requestPlacement(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos activatedControllerPos,
        Urgency urgency,
        int attempts,
        long dueGameTime,
        boolean reconstructed
    ) {
        if (activatedControllerPos == null || attempts <= 0) {
            return;
        }
        SchedulerState state = state(level);
        ResourceKey<Level> dimension = level.dimension();
        if (instanceKey == null) {
            LOGGER.warn(
                "[TD-SCHED] work=PLACEMENT action=MISSING_INSTANCE_REJECTED controller={} dimension={}",
                activatedControllerPos, dimension.location()
            );
            return;
        }
        WorkKey workKey = new WorkKey(dimension, instanceKey);
        PendingPlacement requested = new PendingPlacement(
            activatedControllerPos.immutable(), urgency, attempts, dueGameTime,
            level.getGameTime(), reconstructed, false, instanceKey
        );
        PendingPlacement existing = state.placements.get(workKey);
        if (existing == null
            || !existing.activatedControllerPos.equals(requested.activatedControllerPos)) {
            if (existing != null) {
                releaseChunkTickets(level, existing);
            }
            state.placements.put(workKey, requested);
            return;
        }
        state.placements.put(workKey, existing.merge(requested));
    }

    @Nullable
    private static WorkKey selectPlacementWork(
        SchedulerState state,
        ResourceKey<Level> dimension,
        long gameTime,
        LoadBand loadBand
    ) {
        if (state.placementClaimed) return null;
        List<Map.Entry<WorkKey, PendingPlacement>> eligible = state.placements.entrySet().stream()
            .filter(entry -> entry.getKey().dimension().equals(dimension))
            .filter(entry -> gameTime >= entry.getValue().dueGameTime)
            .filter(entry -> placementAllowed(
                loadBand,
                effectiveUrgency(
                    entry.getValue().urgency,
                    Math.max(0L, gameTime - entry.getValue().enqueuedGameTime)
                )
            ))
            .toList();
        if (eligible.isEmpty()) return null;
        Urgency highest = eligible.stream()
            .map(entry -> effectiveUrgency(
                entry.getValue().urgency,
                Math.max(0L, gameTime - entry.getValue().enqueuedGameTime)
            ))
            .max(Comparator.comparingInt(Urgency::ordinal))
            .orElse(Urgency.NORMAL);
        List<DungeonInstanceAllocationData.InstanceKey> instanceKeys = eligible.stream()
            .filter(entry -> effectiveUrgency(
                entry.getValue().urgency,
                Math.max(0L, gameTime - entry.getValue().enqueuedGameTime)
            ) == highest)
            .map(entry -> entry.getKey().instanceKey())
            .distinct()
            .toList();
        DungeonInstanceAllocationData.InstanceKey selected =
            selectRoundRobin(instanceKeys, state.lastPlacementInstance);
        return eligible.stream()
            .map(Map.Entry::getKey)
            .filter(key -> key.instanceKey().equals(selected))
            .min(Comparator.comparing(key -> key.dimension().location().toString()))
            .orElse(null);
    }

    @Nullable
    static DungeonInstanceAllocationData.InstanceKey selectRoundRobin(
        List<DungeonInstanceAllocationData.InstanceKey> candidates,
        @Nullable DungeonInstanceAllocationData.InstanceKey lastServed
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<DungeonInstanceAllocationData.InstanceKey> ordered = new ArrayList<>(
            candidates.stream().filter(Objects::nonNull).distinct().toList()
        );
        ordered.sort(
            Comparator.comparing((DungeonInstanceAllocationData.InstanceKey key) ->
                key.instanceId().toString()
            ).thenComparingLong(DungeonInstanceAllocationData.InstanceKey::epoch)
        );
        if (ordered.isEmpty()) return null;
        if (lastServed == null) return ordered.getFirst();
        for (DungeonInstanceAllocationData.InstanceKey candidate : ordered) {
            if (compareInstanceKeys(candidate, lastServed) > 0) {
                return candidate;
            }
        }
        return ordered.getFirst();
    }

    private static int compareInstanceKeys(
        DungeonInstanceAllocationData.InstanceKey left,
        DungeonInstanceAllocationData.InstanceKey right
    ) {
        int id = left.instanceId().toString().compareTo(right.instanceId().toString());
        return id != 0 ? id : Long.compare(left.epoch(), right.epoch());
    }

    private static List<PendingPlacement> reconstructPlacements(ServerLevel level) {
        List<PendingPlacement> reconstructed = new ArrayList<>();
        String dimensionKey = level.dimension().location().toString();
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        for (DungeonInstanceAllocationData.Allocation allocation : allocations.allocations()) {
            if (!allocation.dimension().equals(dimensionKey)
                || allocation.lifecycle().state()
                    != DungeonInstanceAllocationData.LifecycleState.PREPARING) {
                continue;
            }
            GeneratedFloorSession session = GeneratedFloorRepository.get(
                level, dimensionKey, allocation.key()
            ).hydrate();
            GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
            boolean startupRoomsComplete = placement != null
                && placement.roomPlans().size() >= 2
                && placement.controllers().size() >= 2
                && startupRoomsReady(level, dimensionKey, allocation.key());
            if (placement == null || placement.roomPlans().size() < 2
                || startupRoomsComplete || session.generationFailure() != null
                || session.cleanupState() != null) {
                continue;
            }
            BlockPos startupAnchor = placement.controllers().isEmpty()
                ? placement.roomPlans().getFirst().controllerPos()
                : placement.controllers().getFirst();
            if (!GeneratedFloorLazyPlacementService.needsLookahead(
                level, allocation.key(), startupAnchor
            )) {
                continue;
            }
            reconstructed.add(new PendingPlacement(
                startupAnchor, Urgency.CRITICAL, 2 - placement.controllers().size(),
                level.getGameTime() + INITIAL_LOOKAHEAD_DELAY_TICKS,
                level.getGameTime(), true, false, allocation.key()
            ));
        }
        DungeonRunManager manager = DungeonRunManager.get(level);
        boolean reconstructedExactActiveRun = false;
        for (DungeonInstanceAllocationData.Allocation allocation : allocations.allocations()) {
            DungeonInstanceAllocationData.LifecycleState lifecycle = allocation.lifecycle().state();
            if (!allocation.dimension().equals(dimensionKey)
                || lifecycle != DungeonInstanceAllocationData.LifecycleState.ACTIVE
                    && lifecycle != DungeonInstanceAllocationData.LifecycleState.SUSPENDED
                || !manager.isActiveRun(allocation.key())
                || manager.isRunCompleted(allocation.key())
                || !manager.isPresetMode(allocation.key())) {
                continue;
            }
            GeneratedFloorSession session = GeneratedFloorRepository.get(
                level, dimensionKey, allocation.key()
            ).hydrate();
            GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
            Predicate<BlockPos> persistedControllerValid = pos -> {
                if (placement == null) return false;
                int planIndex = findPlanIndex(placement.roomPlans(), pos);
                return planIndex >= 0
                    && GeneratedRoomPlacementOperation.existingControllerValidationFailure(
                        level, session, placement.roomPlans().get(planIndex), false
                    ) == null;
            };
            BlockPos controller = selectReconstructionAnchor(
                placement,
                manager.getActiveRoomControllerPositions(allocation.key()),
                manager.getCompletedRoomControllerPositions(allocation.key()),
                manager.getPresetExpectedControllerPos(allocation.key()),
                pos -> GeneratedFloorLazyPlacementService.needsLookahead(
                    level, allocation.key(), pos
                ),
                persistedControllerValid
            );
            if (controller == null) {
                continue;
            }
            PendingPlacement exact = new PendingPlacement(
                controller.immutable(), Urgency.FRONTIER, LOOKAHEAD_PLACEMENT_ATTEMPTS,
                level.getGameTime() + INITIAL_LOOKAHEAD_DELAY_TICKS,
                level.getGameTime(), true, false, allocation.key()
            );
            LOGGER.info(
                "[TD-SCHED] work=PLACEMENT action=RECONSTRUCTED activatedController={} dueGameTime={} instance={} epoch={} dimension={}",
                controller, exact.dueGameTime, allocation.key().instanceId(),
                allocation.key().epoch(), level.dimension().location()
            );
            reconstructed.add(exact);
            reconstructedExactActiveRun = true;
        }
        if (reconstructedExactActiveRun
            || !manager.isActiveRun() || manager.isRunCompleted() || !manager.isPresetMode()) {
            return List.copyOf(reconstructed);
        }
        BlockPos controller = manager.getActiveRoomControllerPositions().stream()
            .filter(pos -> GeneratedFloorLazyPlacementService.needsLookahead(level, pos))
            .min(Comparator.comparingLong(BlockPos::asLong))
            .orElse(null);
        if (controller == null) return List.copyOf(reconstructed);
        PendingPlacement legacy = new PendingPlacement(
            controller.immutable(), Urgency.FRONTIER, LOOKAHEAD_PLACEMENT_ATTEMPTS,
            level.getGameTime() + INITIAL_LOOKAHEAD_DELAY_TICKS,
            level.getGameTime(), true, false,
            DungeonInstanceAllocationData.get(level)
                .currentIdentity(level.dimension().location().toString())
                .orElse(null)
        );
        LOGGER.info(
            "[TD-SCHED] work=PLACEMENT action=RECONSTRUCTED activatedController={} dueGameTime={} dimension={}",
            controller, legacy.dueGameTime, level.dimension().location()
        );
        reconstructed.add(legacy);
        return List.copyOf(reconstructed);
    }

    static @Nullable BlockPos selectReconstructionAnchor(
        @Nullable GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        Set<BlockPos> activeControllers,
        Set<BlockPos> completedControllers,
        @Nullable BlockPos expectedController,
        Predicate<BlockPos> lookaheadNeeded
    ) {
        return selectReconstructionAnchor(
            placement,
            activeControllers,
            completedControllers,
            expectedController,
            lookaheadNeeded,
            pos -> placement != null && placement.controllers().contains(pos)
        );
    }

    static @Nullable BlockPos selectReconstructionAnchor(
        @Nullable GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        Set<BlockPos> activeControllers,
        Set<BlockPos> completedControllers,
        @Nullable BlockPos expectedController,
        Predicate<BlockPos> lookaheadNeeded,
        Predicate<BlockPos> persistedControllerValid
    ) {
        if (placement == null || lookaheadNeeded == null) return null;
        BlockPos active = (activeControllers == null ? Set.<BlockPos>of() : activeControllers)
            .stream()
            .filter(lookaheadNeeded)
            .min(Comparator.comparingLong(BlockPos::asLong))
            .orElse(null);
        if (active != null) return active.immutable();
        if (expectedController == null) {
            return null;
        }
        if (placement.controllers().contains(expectedController)
            && (persistedControllerValid == null || persistedControllerValid.test(expectedController))) {
            return null;
        }
        int expectedIndex = findPlanIndex(placement.roomPlans(), expectedController);
        if (expectedIndex <= 0) return null;
        BlockPos predecessor = placement.roomPlans().get(expectedIndex - 1).controllerPos();
        Set<BlockPos> completed = completedControllers == null
            ? Set.of() : completedControllers;
        return completed.contains(predecessor) && lookaheadNeeded.test(predecessor)
            ? predecessor.immutable() : null;
    }

    private static int findPlanIndex(
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans,
        BlockPos controllerPos
    ) {
        for (int index = 0; index < plans.size(); index++) {
            if (plans.get(index).controllerPos().equals(controllerPos)) return index;
        }
        return -1;
    }

    private static boolean prepareChunks(ServerLevel level, PendingPlacement pending, int maxChunks) {
        if (maxChunks <= 0) {
            return false;
        }
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan =
            GeneratedFloorLazyPlacementService.nextPreparedLookaheadPlan(
                level, pending.instanceKey, pending.activatedControllerPos
            );
        if (plan == null || plan.bounds() == null) {
            pending.chunkPreparationComplete = true;
            return true;
        }
        BoundingBox bounds = new BoundingBox(
            plan.bounds().min().getX(), plan.bounds().min().getY(), plan.bounds().min().getZ(),
            plan.bounds().max().getX(), plan.bounds().max().getY(), plan.bounds().max().getZ()
        );
        int minChunkX = bounds.minX() >> 4;
        int maxChunkX = bounds.maxX() >> 4;
        int minChunkZ = bounds.minZ() >> 4;
        int maxChunkZ = bounds.maxZ() >> 4;
        int prepared = 0;
        boolean allLoaded = true;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                long ticketKey = chunkPos.toLong();
                if (level.hasChunk(chunkX, chunkZ)) {
                    level.getChunkSource().addRegionTicket(
                        WORLD_WORK_CHUNK_TICKET,
                        chunkPos,
                        ChunkLevel.byStatus(FullChunkStatus.FULL),
                        ticketKey
                    );
                    pending.chunkTickets.add(ticketKey);
                    continue;
                }
                allLoaded = false;
                if (pending.chunkTickets.contains(ticketKey)) {
                    level.getChunkSource().addRegionTicket(
                        WORLD_WORK_CHUNK_TICKET,
                        chunkPos,
                        ChunkLevel.byStatus(FullChunkStatus.FULL),
                        ticketKey
                    );
                    continue;
                }
                if (prepared < maxChunks) {
                    level.getChunkSource().addRegionTicket(
                        WORLD_WORK_CHUNK_TICKET,
                        chunkPos,
                        ChunkLevel.byStatus(FullChunkStatus.FULL),
                        ticketKey
                    );
                    pending.chunkTickets.add(ticketKey);
                    prepared++;
                }
            }
        }
        if (!allLoaded) {
            LOGGER.debug(
                "[TD-SCHED] work=CHUNK_PREPARATION urgency={} preparedThisTick={} targetRoomIndex={} dimension={}",
                pending.urgency, prepared, plan.index(), level.dimension().location()
            );
            return false;
        }
        pending.chunkPreparationComplete = true;
        return true;
    }

    private static boolean startupRoomsReady(
        ServerLevel level,
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        GeneratedFloorSession session =
            GeneratedFloorRepository.get(level, dimensionKey, instanceKey).hydrate();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || session.generationFailure() != null) {
            return false;
        }
        int required = DungeonRunService.requiredInitialGeneratedRooms(
            placement.roomPlans().size()
        );
        if (required < 2 || placement.controllers().size() < required) {
            return false;
        }
        for (int index = 0; index < required; index++) {
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan = placement.roomPlans().get(index);
            if (!placement.controllers().contains(plan.controllerPos())) {
                return false;
            }
            if (plan.hasPlacementMetadata()) {
                if (GeneratedRoomPlacementOperation.existingControllerValidationFailure(
                    level, session, plan, false
                ) != null) {
                    return false;
                }
                continue;
            }
            if (!(level.getBlockEntity(plan.controllerPos()) instanceof RoomControllerBlockEntity controller)
                || !controller.matchesDungeonInstance(instanceKey)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPreparing(
        DungeonInstanceAllocationData allocations,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return allocations.forIdentity(instanceKey)
            .map(DungeonInstanceAllocationData.Allocation::lifecycle)
            .map(DungeonInstanceAllocationData.Lifecycle::state)
            .filter(state -> state == DungeonInstanceAllocationData.LifecycleState.PREPARING)
            .isPresent();
    }

    private static void reconcilePermanentGenerationFailures(
        ServerLevel level,
        String dimensionKey,
        DungeonInstanceAllocationData allocations,
        SchedulerState state
    ) {
        for (DungeonInstanceAllocationData.Allocation allocation : List.copyOf(
            allocations.allocations()
        )) {
            if (!dimensionKey.equals(allocation.dimension())
                || allocation.lifecycle().state().reusable()
                || allocation.lifecycle().state()
                    == DungeonInstanceAllocationData.LifecycleState.QUARANTINED
                || !GeneratedFloorLazyPlacementService.hasPermanentGenerationFailure(
                    level, allocation.key()
                )) {
                continue;
            }
            GeneratedFloorRuntimeData.GenerationFailureState failure = GeneratedFloorRepository.get(
                level, dimensionKey, allocation.key()
            ).hydrate().generationFailure();
            if (GeneratedFloorLazyPlacementService.isFallbackRoutedFailure(failure)) {
                continue;
            }
            GeneratedFloorFailureRecoveryService.RecoveryResult recovery =
                GeneratedFloorFailureRecoveryService.attempt(
                    level, allocation.key(), placementSliceBudget(state.loadBand)
                );
            if (recovery.routed() || recovery.deferred()) {
                PendingPlacement pending = state.placements.remove(
                    new WorkKey(level.dimension(), allocation.key())
                );
                if (pending != null) {
                    releaseChunkTickets(level, pending);
                }
                continue;
            }
            reconcilePermanentGenerationFailure(
                level, dimensionKey, allocations, state, allocation.key()
            );
        }
    }

    private static void reconcilePermanentGenerationFailure(
        ServerLevel level,
        String dimensionKey,
        DungeonInstanceAllocationData allocations,
        SchedulerState state,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        DungeonInstanceAllocationData.Allocation allocation =
            allocations.forIdentity(instanceKey).orElse(null);
        if (allocation == null) return;
        PendingPlacement pending = state.placements.remove(
            new WorkKey(level.dimension(), instanceKey)
        );
        if (pending != null) releaseChunkTickets(level, pending);

        ServerPlayer onlineHost = allocation.roster().stream()
            .map(level.getServer().getPlayerList()::getPlayer)
            .filter(java.util.Objects::nonNull)
            .findFirst().orElse(null);
        boolean terminalStarted;
        switch (allocation.lifecycle().state()) {
            case PREPARING -> {
                failStartupPreparation(
                    level, dimensionKey, instanceKey, "PERMANENT_GENERATION_FAILURE"
                );
                terminalStarted = allocations.forIdentity(instanceKey)
                    .map(current -> current.lifecycle().state()
                        == DungeonInstanceAllocationData.LifecycleState.CLEANING)
                    .orElse(false);
            }
            case READY -> terminalStarted = DungeonRunManager.get(level)
                .expireUndispatchedReadyInstance(
                    instanceKey, level, "READY_PERMANENT_GENERATION_FAILURE"
                );
            case ACTIVE, SUSPENDED -> {
                DungeonRunManager.get(level).onFailedFloorReturnToHub(
                    instanceKey, level, onlineHost
                );
                terminalStarted = allocations.forIdentity(instanceKey)
                    .map(current -> current.lifecycle().state()
                        == DungeonInstanceAllocationData.LifecycleState.COMPLETING
                        || current.lifecycle().state()
                            == DungeonInstanceAllocationData.LifecycleState.CLEANING)
                    .orElse(false);
            }
            default -> {
                return;
            }
        }
        if (!terminalStarted) {
            allocations.quarantine(
                instanceKey, "PERMANENT_GENERATION_FAILURE_TERMINAL_HANDOFF_FAILED",
                level.getGameTime()
            );
        }
        if (onlineHost != null) {
            onlineHost.sendSystemMessage(Component.literal(
                terminalStarted
                    ? "Dungeon generation became incompatible with the current runtime. "
                        + "The run has ended safely and cleanup has started; prepare a new dungeon afterward."
                    : "Dungeon generation became incompatible and automatic cleanup could not start. "
                        + "The instance was quarantined without further world mutation."
            ));
        }
        LOGGER.error(
            "Permanent generated-floor failure terminal handoff: instance={} epoch={} previousState={} accepted={} dimension={}",
            instanceKey.instanceId(), instanceKey.epoch(), allocation.lifecycle().state(),
            terminalStarted, dimensionKey
        );
    }

    private static void failStartupPreparation(
        ServerLevel level,
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        String reason
    ) {
        GeneratedFloorRepository repository =
            GeneratedFloorRepository.get(level, dimensionKey, instanceKey);
        GeneratedFloorSession session = repository.hydrate();
        if (session.cleanupState() != null || session.enqueueCleanupForPlacement()) {
            repository.flushCleanup(session);
        }
        session.removePlacement();
        repository.flushPlacement(session);
        session.clearComposition();
        repository.flushComposition(session);
        session.clearGenerationFailure();
        repository.flushGenerationFailure(session);

        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        boolean failed = allocations.transition(
            instanceKey,
            DungeonInstanceAllocationData.LifecycleState.FAILED_PREPARATION,
            "SOLO_STARTUP_PLACEMENT_FAILED_" + reason,
            level.getGameTime()
        );
        boolean cleaning = failed && allocations.transition(
            instanceKey,
            DungeonInstanceAllocationData.LifecycleState.CLEANING,
            "SOLO_FAILED_PREPARATION_CLEANUP",
            level.getGameTime()
        );
        if (!cleaning) {
            allocations.quarantine(
                instanceKey, "SOLO_FAILED_PREPARATION_TERMINAL_COMMIT_FAILED", level.getGameTime()
            );
        }
        level.getServer().overworld().getDataStorage().save();
    }

    private static void releaseChunkTickets(ServerLevel level, PendingPlacement pending) {
        int ticketLevel = ChunkLevel.byStatus(FullChunkStatus.FULL);
        for (long ticketKey : pending.chunkTickets) {
            ChunkPos chunkPos = new ChunkPos(ticketKey);
            level.getChunkSource().removeRegionTicket(
                WORLD_WORK_CHUNK_TICKET, chunkPos, ticketLevel, ticketKey
            );
        }
        pending.chunkTickets.clear();
    }

    private static SchedulerState state(ServerLevel level) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(level.getServer(), ignored -> new SchedulerState());
        }
    }

    public enum LoadBand {
        GREEN,
        YELLOW,
        RED
    }

    public enum Urgency {
        NORMAL,
        HIGH,
        CRITICAL,
        FRONTIER,
        FORCED
    }

    public record Snapshot(
        LoadBand loadBand,
        double averageTickMs,
        String queuePhase,
        String urgency,
        long queueAgeTicks,
        double lastPlacementMs,
        long lastPlacementGameTime,
        int cleanupBudget,
        String instanceId,
        long allocationEpoch
    ) {
    }

    public record PreparationTiming(
        int elapsedSeconds,
        int estimatedRemainingSeconds,
        int progressPercent
    ) {
        public PreparationTiming {
            elapsedSeconds = Math.max(0, Math.min(359_999, elapsedSeconds));
            estimatedRemainingSeconds = Math.max(
                -1,
                Math.min(3_599, estimatedRemainingSeconds)
            );
            progressPercent = Math.max(0, Math.min(100, progressPercent));
        }

        static PreparationTiming unavailable() {
            return new PreparationTiming(0, -1, 0);
        }
    }

    private static final class SchedulerState {
        private final Map<WorkKey, PendingPlacement> placements = new HashMap<>();
        private int serverTick = Integer.MIN_VALUE;
        private LoadBand loadBand = LoadBand.GREEN;
        private double averageTickMs;
        private boolean placementClaimed;
        private boolean cleanupClaimed;
        private double lastPlacementMs;
        private long lastPlacementGameTime;
        private DungeonInstanceAllocationData.InstanceKey lastPlacementInstance;
        private DungeonInstanceAllocationData.InstanceKey lastCleanupInstance;
    }

    private record WorkKey(
        ResourceKey<Level> dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        private WorkKey {
            Objects.requireNonNull(dimension, "Scheduler work requires a dimension");
            Objects.requireNonNull(instanceKey, "Scheduler work requires instance identity");
        }
    }

    private static final class PendingPlacement {
        private final BlockPos activatedControllerPos;
        private final Urgency urgency;
        private final int remainingAttempts;
        private final long dueGameTime;
        private final long enqueuedGameTime;
        private final boolean reconstructed;
        private final DungeonInstanceAllocationData.InstanceKey instanceKey;
        private final Set<Long> chunkTickets;
        private boolean chunkPreparationComplete;

        private PendingPlacement(
            BlockPos activatedControllerPos,
            Urgency urgency,
            int remainingAttempts,
            long dueGameTime,
            long enqueuedGameTime,
            boolean reconstructed,
            boolean chunkPreparationComplete,
            DungeonInstanceAllocationData.InstanceKey instanceKey
        ) {
            this.activatedControllerPos = activatedControllerPos;
            this.urgency = urgency;
            this.remainingAttempts = remainingAttempts;
            this.dueGameTime = dueGameTime;
            this.enqueuedGameTime = enqueuedGameTime;
            this.reconstructed = reconstructed;
            this.instanceKey = instanceKey;
            this.chunkTickets = new HashSet<>();
            this.chunkPreparationComplete = chunkPreparationComplete;
        }

        private PendingPlacement merge(PendingPlacement other) {
            Urgency mergedUrgency = urgency.ordinal() >= other.urgency.ordinal() ? urgency : other.urgency;
            PendingPlacement merged = new PendingPlacement(
                activatedControllerPos,
                mergedUrgency,
                Math.max(remainingAttempts, other.remainingAttempts),
                Math.min(dueGameTime, other.dueGameTime),
                Math.min(enqueuedGameTime, other.enqueuedGameTime),
                reconstructed && other.reconstructed,
                chunkPreparationComplete,
                instanceKey
            );
            merged.chunkTickets.addAll(chunkTickets);
            merged.chunkTickets.addAll(other.chunkTickets);
            return merged;
        }

        private PendingPlacement nextAttempt(int attempts, long nextDueGameTime) {
            return new PendingPlacement(
                activatedControllerPos, urgency, attempts, nextDueGameTime,
                enqueuedGameTime, reconstructed, false, instanceKey
            );
        }
    }
}
