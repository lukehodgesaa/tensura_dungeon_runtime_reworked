package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraEnergyDebugHelper;
import com.mineclawd.tensuradungeon.compat.tensuraadditions.DungeonExitPortalVisual;
import com.mineclawd.tensuradungeon.event.ActiveRoomProtectionEvents;
import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyRegistry;
import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class DungeonRunService {
    private static final int NPC_BASE_ROOM_TARGET = 4;
    private static final int NPC_FLOORS_PER_ROOM_INCREASE = 10;
    private static final int GENERATED_FLOOR_MAX_ROOMS = 16;
    private static final int GENERATED_INITIAL_READY_ROOM_COUNT = 2;

    private static final double EXIT_PORTAL_USE_DISTANCE = 6.0D;

    public record RoomCompletionResult(boolean floorCompletedNow, int floorNumber, String runSessionId, String completionReason) {
    }

    public record FloorCompletionContext(
        BlockPos controllerPos,
        String roomProfile,
        String objective,
        String objectiveSelectionMode
    ) {
        public FloorCompletionContext {
            controllerPos = controllerPos == null ? null : controllerPos.immutable();
            roomProfile = normalizeCompletionValue(roomProfile, "UNKNOWN_PROFILE");
            objective = normalizeCompletionValue(objective, "UNKNOWN_OBJECTIVE");
            objectiveSelectionMode = normalizeCompletionValue(
                objectiveSelectionMode, "UNKNOWN_SELECTION_MODE"
            );
        }
    }

    public record ExitPortalPlacementResult(
        boolean ready,
        @Nullable BlockPos portalPos,
        String source,
        String reason,
        String details
    ) {
    }

    public record FloorStartOperationResult(
        String reason,
        @Nullable DungeonFloorProgressData.FloorStartResult floorStart,
        boolean preparedController
    ) {
        public boolean succeeded() {
            return "NONE".equals(reason);
        }
    }

    public record AdvanceFloorOperationResult(String reason, boolean preparedController) {
        public boolean succeeded() {
            return "NONE".equals(reason);
        }
    }

    public record PresetArmOperationResult(
        String reason,
        @Nullable BlockPos blockingController,
        @Nullable DungeonFloorProgressData.FloorStartResult floorStart,
        List<BlockPos> order,
        int preparedControllers
    ) {
        public boolean succeeded() {
            return "NONE".equals(reason);
        }
    }

    public record GeneratedFloorArmPlan(
        int requestedFloor,
        int requestedRooms,
        boolean rolling,
        List<BlockPos> order,
        List<BlockPos> availableControllers,
        List<BlockPos> usableControllers,
        List<BlockPos> safeControllers
    ) {
        public GeneratedFloorArmPlan {
            order = List.copyOf(order);
            availableControllers = List.copyOf(availableControllers);
            usableControllers = List.copyOf(usableControllers);
            safeControllers = List.copyOf(safeControllers);
        }

        public boolean hasSafeEntry() {
            return !order.isEmpty() && safeControllers.contains(order.getFirst());
        }
    }

    public record GeneratedFloorArmOperationResult(
        String reason,
        @Nullable DungeonFloorProgressData.FloorStartResult floorStart,
        List<BlockPos> order,
        int preparedControllers,
        boolean staleRunCleared,
        int blockingRoomIndex,
        @Nullable BlockPos blockingController
    ) {
        public GeneratedFloorArmOperationResult {
            order = List.copyOf(order);
        }

        public boolean succeeded() {
            return "NONE".equals(reason);
        }
    }

    public record GeneratedFloorEntryValidationResult(
        String reason,
        DungeonFloorProgressData.FloorStartResult floorStart
    ) {
        public boolean succeeded() {
            return "NONE".equals(reason);
        }
    }

    public static int roomTargetForFloor(int floor) {
        int normalizedFloor = Math.max(1, floor);
        return Math.min(
            GENERATED_FLOOR_MAX_ROOMS,
            NPC_BASE_ROOM_TARGET + ((normalizedFloor - 1) / NPC_FLOORS_PER_ROOM_INCREASE)
        );
    }

    public static int requiredInitialGeneratedRooms(int requestedRooms) {
        return Math.min(GENERATED_INITIAL_READY_ROOM_COUNT, Math.max(0, requestedRooms));
    }

    public GeneratedFloorEntryValidationResult validateGeneratedFloorEntry(
        DungeonRunState runState,
        DungeonFloorProgressData floorProgress,
        UUID playerId,
        int requestedFloor
    ) {
        DungeonFloorProgressData.FloorStartResult startResult =
            floorProgress.canStartFloor(playerId, requestedFloor);
        if (!startResult.allowed()) {
            return new GeneratedFloorEntryValidationResult("FLOOR_LOCKED", startResult);
        }
        if (runState.isActiveRun() && !runState.isRunCompleted()
            && (runState.getCompletedRoomCount() > 0 || runState.getActiveRoomCount() > 0)) {
            return new GeneratedFloorEntryValidationResult("RUN_IN_PROGRESS", startResult);
        }
        return new GeneratedFloorEntryValidationResult("NONE", startResult);
    }

    public record PortalCommandOperationResult(String reason, @Nullable BlockPos sharedSpawn) {
        public boolean succeeded() {
            return "NONE".equals(reason);
        }
    }

    public record HubFloorPreparationResult(String reason) {
        public boolean succeeded() {
            return "NONE".equals(reason);
        }
    }

    public void startRun(DungeonRunState runState, Runnable markDirty, ServerLevel level) {
        startRunAtFloor(runState, markDirty, level, 1);
    }

    public void startRunAtFloor(DungeonRunState runState, Runnable markDirty, ServerLevel level, int requestedFloor) {
        if (runState.isActiveRun()) {
            CoreRecoveryData.get(level).discardActiveSession(
                runState.getRunSessionId(),
                runState.getFloorNumber()
            );
        }
        clearExitPortal(runState, level, "RUN_NOT_COMPLETED");
        String newSessionId = UUID.randomUUID().toString();
        long newRunSeed = level.getSeed() ^ level.getGameTime() ^ UUID.randomUUID().getLeastSignificantBits();
        runState.startRunAtFloor(requestedFloor, newSessionId, newRunSeed);
        setExitPortalState(runState, false, null, "NONE", "RUN_NOT_COMPLETED", "run started");
        markDirty.run();
    }

    public void endRun(DungeonRunState runState, Runnable markDirty, ServerLevel level) {
        CoreRecoveryData.get(level).discardActiveSession(
            runState.getRunSessionId(),
            runState.getFloorNumber()
        );
        clearExitPortal(runState, level, "CLEARED");
        runState.endRun();
        runState.clearRetryCheckpoint();
        runState.clearHubPreparedPresetState();
        setExitPortalState(runState, false, null, "NONE", "CLEARED", "run cleared");
        markDirty.run();
    }

    public void resetRun(DungeonRunState runState, Runnable markDirty, ServerLevel level) {
        CoreRecoveryData.get(level).discardActiveSession(
            runState.getRunSessionId(),
            runState.getFloorNumber()
        );
        clearExitPortal(runState, level, "CLEARED");
        runState.resetRun();
        runState.clearRetryCheckpoint();
        runState.clearHubPreparedPresetState();
        setExitPortalState(runState, false, null, "NONE", "CLEARED", "run cleared");
        markDirty.run();
    }

    public void endRunWithControllerCleanup(DungeonRunState runState, Runnable markDirty, ServerLevel level) {
        cleanupActiveControllers(runState, level);
        ActiveRoomProtectionEvents.clearActiveControllerRegistrations(level);
        endRun(runState, markDirty, level);
    }

    public void resetRunWithControllerCleanup(DungeonRunState runState, Runnable markDirty, ServerLevel level) {
        cleanupActiveControllers(runState, level);
        ActiveRoomProtectionEvents.clearActiveControllerRegistrations(level);
        resetRun(runState, markDirty, level);
    }

    public FloorStartOperationResult startFloor(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        DungeonFloorProgressData floorProgress,
        UUID playerId,
        int requestedFloor,
        @Nullable RoomControllerBlockEntity controller
    ) {
        DungeonFloorProgressData.FloorStartResult startResult = floorProgress.canStartFloor(playerId, requestedFloor);
        if (!startResult.allowed()) {
            return new FloorStartOperationResult("FLOOR_LOCKED", startResult, false);
        }
        if (controller != null && controller.getRoomState() == RoomControllerBlockEntity.RoomState.ACTIVE) {
            return new FloorStartOperationResult("CONTROLLER_ACTIVE", startResult, false);
        }
        if (runState.isActiveRun() && !runState.isRunCompleted()
            && (runState.getCompletedRoomCount() > 0 || runState.getActiveRoomCount() > 0)) {
            return new FloorStartOperationResult("RUN_IN_PROGRESS", startResult, false);
        }
        boolean preparedController = prepareControllerForFloorStart(level, controller);
        boolean retryReplay = runState.isRetryReplayForFloor(requestedFloor);
        startRunAtFloor(runState, markDirty, level, requestedFloor);
        applyPreparedModifierPlan(floorProgress, runState, playerId, requestedFloor, markDirty);
        if (retryReplay) {
            runState.restoreRetryModifiers();
            markDirty.run();
        }
        recordFloorStartTelemetry(floorProgress, runState, level, playerId);
        return new FloorStartOperationResult("NONE", startResult, preparedController);
    }

    public AdvanceFloorOperationResult advanceFloorFromCommand(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        DungeonFloorProgressData floorProgress,
        UUID playerId,
        @Nullable RoomControllerBlockEntity controller
    ) {
        if (runState.getActiveRoomCount() > 0) {
            return new AdvanceFloorOperationResult("ACTIVE_ROOM", false);
        }
        if (!runState.isActiveRun()) {
            return new AdvanceFloorOperationResult("NO_ACTIVE_RUN", false);
        }
        if (!runState.isNextFloorEligible()) {
            return new AdvanceFloorOperationResult("NEXT_FLOOR_NOT_ELIGIBLE", false);
        }
        int nextFloor = runState.getNextFloorNumber();
        if (!floorProgress.isFloorUnlocked(playerId, nextFloor)) {
            return new AdvanceFloorOperationResult("FLOOR_LOCKED", false);
        }
        if (!runState.isRunCompleted() || nextFloor <= runState.getFloorNumber()) {
            return new AdvanceFloorOperationResult("ADVANCE_BLOCKED", false);
        }
        CoreRecoveryData.get(level).finalizeForDeparture(
            playerId,
            runState.getRunSessionId(),
            runState.getFloorNumber()
        );
        if (!advanceToNextFloor(runState, markDirty, level)) {
            return new AdvanceFloorOperationResult("ADVANCE_BLOCKED", false);
        }
        applyPreparedModifierPlan(
            floorProgress, runState, playerId, runState.getFloorNumber(), markDirty
        );
        recordFloorStartTelemetry(floorProgress, runState, level, playerId);
        return new AdvanceFloorOperationResult("NONE", prepareControllerForFloorStart(level, controller));
    }

    public PresetArmOperationResult armPresetFloor(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        DungeonFloorProgressData floorProgress,
        UUID playerId,
        int requestedFloor,
        int rooms,
        boolean armedFromHub,
        List<BlockPos> order
    ) {
        for (BlockPos controllerPos : order) {
            if (level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller
                && controller.getRoomState() == RoomControllerBlockEntity.RoomState.ACTIVE) {
                return new PresetArmOperationResult("CONTROLLER_ACTIVE", controllerPos, null, List.copyOf(order), 0);
            }
        }

        DungeonFloorProgressData.FloorStartResult startResult = floorProgress.canStartFloor(playerId, requestedFloor);
        if (!startResult.allowed()) {
            return new PresetArmOperationResult("FLOOR_LOCKED", null, startResult, List.copyOf(order), 0);
        }
        if (runState.isActiveRun() && !runState.isRunCompleted()
            && (runState.getCompletedRoomCount() > 0 || runState.getActiveRoomCount() > 0)) {
            return new PresetArmOperationResult("RUN_IN_PROGRESS", null, startResult, List.copyOf(order), 0);
        }

        boolean retryReplay = runState.isRetryReplayForFloor(requestedFloor);
        startRunAtFloor(runState, markDirty, level, requestedFloor);
        applyPreparedModifierPlan(floorProgress, runState, playerId, requestedFloor, markDirty);
        if (retryReplay) {
            runState.restoreRetryModifiers();
            markDirty.run();
        }
        recordFloorStartTelemetry(floorProgress, runState, level, playerId);
        if (order.stream().distinct().count() != order.size()) {
            return new PresetArmOperationResult("DUPLICATE_CONTROLLERS", null, startResult, List.copyOf(order), 0);
        }
        enablePresetMode(runState, markDirty, order);
        markHubPresetPrepared(runState, markDirty, armedFromHub ? rooms : 0);
        int preparedControllers = 0;
        for (BlockPos controllerPos : order) {
            if (level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller) {
                controller.resetRoom(level);
                preparedControllers++;
            }
        }
        return new PresetArmOperationResult("NONE", null, startResult, List.copyOf(order), preparedControllers);
    }

    public GeneratedFloorArmOperationResult armGeneratedFloor(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        DungeonFloorProgressData floorProgress,
        UUID playerId,
        GeneratedFloorArmPlan plan
    ) {
        List<BlockPos> order = plan.order();
        int requestedRooms = plan.requestedRooms();
        int requiredInitialRooms = requiredInitialGeneratedRooms(requestedRooms);
        if (requestedRooms < 1
            || (!plan.rolling() && order.size() < requestedRooms)
            || order.size() < requiredInitialRooms
            || order.stream().distinct().count() != order.size()) {
            return new GeneratedFloorArmOperationResult(
                "INCOMPLETE_OR_DUPLICATE_PLAN", null, order, 0, false, -1, null
            );
        }
        if (!plan.hasSafeEntry()) {
            return new GeneratedFloorArmOperationResult(
                "INITIAL_CONTROLLER_NOT_SAFE", null, order, 0, false, 0, order.getFirst()
            );
        }

        Set<BlockPos> available = Set.copyOf(plan.availableControllers());
        Set<BlockPos> usable = Set.copyOf(plan.usableControllers());
        Set<BlockPos> safe = Set.copyOf(plan.safeControllers());
        for (int index = 0; index < requiredInitialRooms; index++) {
            BlockPos expected = order.get(index);
            if (!available.contains(expected) || (!usable.contains(expected) && !safe.contains(expected))) {
                return new GeneratedFloorArmOperationResult(
                    "INITIAL_CONTROLLER_NOT_READY", null, order, 0, false, index, expected
                );
            }
            if (!(level.getBlockEntity(expected) instanceof RoomControllerBlockEntity controller)) {
                return new GeneratedFloorArmOperationResult(
                    "INITIAL_CONTROLLER_NOT_READY", null, order, 0, false, index, expected
                );
            }
            if (controller.getRoomState() == RoomControllerBlockEntity.RoomState.ACTIVE) {
                return new GeneratedFloorArmOperationResult(
                    "INITIAL_CONTROLLER_ACTIVE", null, order, 0, false, index, expected
                );
            }
        }

        DungeonFloorProgressData.FloorStartResult startResult =
            floorProgress.canStartFloor(playerId, plan.requestedFloor());
        if (!startResult.allowed()) {
            return new GeneratedFloorArmOperationResult(
                "FLOOR_LOCKED", startResult, order, 0, false, -1, null
            );
        }

        boolean staleRunCleared = false;
        if (runState.isActiveRun() && !runState.isFailed() && !runState.isRunCompleted()
            && (runState.getCompletedRoomCount() > 0 || runState.getActiveRoomCount() > 0)) {
            BlockPos activeController = plan.availableControllers().stream()
                .filter(pos -> level.getBlockEntity(pos) instanceof RoomControllerBlockEntity controller
                    && controller.getRoomState() == RoomControllerBlockEntity.RoomState.ACTIVE)
                .findFirst()
                .orElse(null);
            if (activeController != null) {
                return new GeneratedFloorArmOperationResult(
                    "RUN_IN_PROGRESS_ACTIVE_CONTROLLER", startResult, order, 0, false, -1, activeController
                );
            }
            resetRun(runState, markDirty, level);
            staleRunCleared = true;
        }

        boolean retryReplay = runState.isRetryReplayForFloor(plan.requestedFloor());
        setDebugRoomActivationOverride(runState, markDirty, false);
        startRunAtFloor(runState, markDirty, level, plan.requestedFloor());
        applyPreparedModifierPlan(
            floorProgress, runState, playerId, plan.requestedFloor(), markDirty
        );
        if (retryReplay) {
            runState.restoreRetryModifiers();
            markDirty.run();
        }
        recordFloorStartTelemetry(floorProgress, runState, level, playerId);
        if (plan.rolling()) {
            enableRollingPresetMode(runState, markDirty, order, requestedRooms);
        } else {
            enablePresetMode(runState, markDirty, order);
        }
        markHubPresetPrepared(runState, markDirty, 0);

        int preparedControllers = 0;
        for (BlockPos controllerPos : plan.availableControllers()) {
            if (order.contains(controllerPos)
                && level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller) {
                controller.resetRoom(level);
                preparedControllers++;
            }
        }
        return new GeneratedFloorArmOperationResult(
            "NONE", startResult, order, preparedControllers, staleRunCleared, -1, null
        );
    }

    public String onRoomActivated(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        BlockPos controllerPos
    ) {
        if (controllerPos == null || runState.isRunCompleted()) {
            return "RUN_NOT_AVAILABLE_FOR_ACTIVATION";
        }
        if (!runState.isActiveRun() && !runState.isDebugRoomActivationOverride()) {
            return "RUN_NOT_AVAILABLE_FOR_ACTIVATION";
        }

        BlockPos immutablePos = controllerPos.immutable();

        if (runState.isPresetMode()) {
            String blockReason = runState.getPresetActivationBlockReason(immutablePos);
            if (!"NONE".equals(blockReason)) {
                runState.setLastPresetTransitionReason("ACTIVATION_BLOCKED_" + blockReason);
                markDirty.run();
                return blockReason;
            }
        }

        for (BlockPos activePos : List.copyOf(runState.getActiveRoomControllerPositions())) {
            if (activePos.equals(immutablePos)) {
                return "ROOM_ALREADY_REGISTERED_ACTIVE";
            }
            if (level.getBlockEntity(activePos) instanceof RoomControllerBlockEntity activeController
                && activeController.getRoomState() == RoomControllerBlockEntity.RoomState.ACTIVE) {
                runState.setLastPresetTransitionReason("ACTIVATION_BLOCKED_OTHER_ACTIVE_ROOM_" + formatPos(activePos));
                markDirty.run();
                return "OTHER_ACTIVE_ROOM_" + formatPos(activePos);
            }
            runState.removeActiveRoom(activePos);
        }

        runState.registerActiveRoom(immutablePos);
        if (level.getBlockEntity(immutablePos) instanceof RoomControllerBlockEntity controller && controller.isBranchPreviewMode()) {
            controller.setBranchPreviewMode(false);
        }
        GeneratedFloorLazyPlacementService.trackActivatedRoute(level, immutablePos);
        GeneratedFloorWorldWorkScheduler.requestLookahead(level, immutablePos);
        runState.setLastPresetTransitionReason("ACTIVATED_" + formatPos(immutablePos));
        markDirty.run();
        return "NONE";
    }

    public RoomCompletionResult onRoomCompleted(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        BlockPos controllerPos,
        String roomCompletionReason
    ) {
        if (!runState.isActiveRun()) {
            return completionResult(runState, false);
        }

        if (controllerPos == null) {
            if (runState.isPresetMode()) {
                runState.setLastPresetTransitionReason("COMPLETION_BLOCKED_NULL_CONTROLLER");
                markDirty.run();
            }
            return completionResult(runState, false);
        }

        BlockPos immutablePos = controllerPos.immutable();

        if (runState.isPresetMode()) {
            String blockReason = runState.getPresetActivationBlockReason(immutablePos);
            if (!"NONE".equals(blockReason)) {
                runState.setLastPresetTransitionReason("COMPLETION_BLOCKED_" + blockReason);
                markDirty.run();
                return completionResult(runState, false);
            }

            BlockPos expected = runState.getPresetExpectedControllerPos();
            if (expected == null || !expected.equals(immutablePos)) {
                runState.setLastPresetTransitionReason("COMPLETION_BLOCKED_UNEXPECTED_CONTROLLER");
                runState.recordPresetRejection(immutablePos, "EXPECTED_" + formatPos(expected));
                markDirty.run();
                return completionResult(runState, false);
            }

            boolean wasNewCompletion = runState.addCompletedRoom(immutablePos);
            runState.removeActiveRoom(immutablePos);

            if (!wasNewCompletion) {
                runState.setLastPresetTransitionReason("COMPLETION_BLOCKED_DUPLICATE_CONTROLLER");
                runState.recordPresetRejection(immutablePos, "DUPLICATE_COMPLETION");
                markDirty.run();
                return completionResult(runState, false);
            }

            runState.advancePresetIndex();

            boolean floorCompletedNow = false;
            if (!runState.isRunCompleted() && runState.isPresetFloorComplete()) {
                runState.markRunCompleted("TARGET_ROOMS_COMPLETED");
                runState.setLastPresetTransitionReason("PRESET_FLOOR_COMPLETED");
                ensureExitPortalForCompletedFloor(runState, markDirty, level, immutablePos);
                floorCompletedNow = true;
            }

            runState.captureRoomCompletionFailureReason(roomCompletionReason);
            markDirty.run();
            return completionResult(runState, floorCompletedNow);
        }

        boolean wasNewCompletion = runState.addCompletedRoom(immutablePos);
        runState.removeActiveRoom(immutablePos);

        boolean floorCompletedNow = false;
        if (wasNewCompletion && !runState.isRunCompleted() && runState.getCompletedRoomCount() >= runState.getTargetRoomCount()) {
            runState.markRunCompleted("TARGET_ROOMS_COMPLETED");
            ensureExitPortalForCompletedFloor(runState, markDirty, level, immutablePos);
            floorCompletedNow = true;
        }

        runState.captureRoomCompletionFailureReason(roomCompletionReason);
        markDirty.run();
        return completionResult(runState, floorCompletedNow);
    }

    public void applyFloorCompletionEffects(
        DungeonRunState runState,
        ServerLevel level,
        ServerPlayer rewardPlayer,
        int floorNumber,
        String runSessionId,
        String completionReason,
        FloorCompletionContext context
    ) {
        if (runState == null || level == null || rewardPlayer == null) {
            return;
        }
        FloorCompletionContext completionContext = context == null
            ? new FloorCompletionContext(null, null, null, null)
            : context;
        SoulOrbHaulData haulData = SoulOrbHaulData.get(level);
        haulData.completeSession(level, rewardPlayer, runSessionId, floorNumber);

        DungeonFloorProgressData progressData = DungeonFloorProgressData.get(level);
        DungeonFloorProgressData.FloorCompletionProgressResult floorProgress =
            progressData.recordFloorCompleted(rewardPlayer.getUUID(), floorNumber, level.getGameTime());
        CoreRecoveryData.LiveFinalizeResult coreRecoveryResult =
            CoreRecoveryData.get(level).finalizeActiveSuccessfulRun(
            rewardPlayer.getUUID(),
            runSessionId,
            floorNumber,
            runState.getRunSeed(),
            !floorProgress.alreadyCompletedBefore()
        );
        RewardMailboxData.RewardSourceMetadata floorRewardMetadata =
            new RewardMailboxData.RewardSourceMetadata(
                "FLOOR_COMPLETION",
                floorNumber,
                completionContext.controllerPos(),
                completionContext.roomProfile(),
                completionContext.objective(),
                completionContext.objectiveSelectionMode(),
                runState.getModifierIdsSummary(),
                runSessionId,
                completionContext.controllerPos(),
                floorProgress.alreadyCompletedBefore()
                    ? "REPEAT_FLOOR_COMPLETION"
                    : "FIRST_TIME_FLOOR_COMPLETION",
                completionReason
            );

        DungeonCurrencyPolicy.CompletionAward currencyAward = DungeonCurrencyPolicy.completionAward(
            floorNumber, DungeonCurrencyPolicy.ClearRank.C, 0
        );
        ForgeMaterialRecoveryPolicy.RecoveryRoll forgeMaterialRoll = ForgeMaterialRecoveryPolicy.roll(
            new ForgeMaterialRecoveryPolicy.RecoveryRequest(
                runSessionId,
                floorNumber,
                ForgeMaterialRecoveryPolicy.FloorSource.classify(
                    floorNumber,
                    completionContext.roomProfile(),
                    completionContext.objective()
                ),
                !floorProgress.alreadyCompletedBefore(),
                runState.getRunSeed()
            )
        );
        List<ItemStack> completionRewardStacks = new ArrayList<>();
        DungeonCurrencyPolicy.SessionPayout payout = currencyAward.payout();
        addCompletionRewardStack(
            completionRewardStacks, DungeonCurrencyPolicy.GOLD_COIN_ITEM_ID, payout.goldCoins()
        );
        addCompletionRewardStack(
            completionRewardStacks, DungeonCurrencyPolicy.SILVER_COIN_ITEM_ID, payout.silverCoins()
        );
        addCompletionRewardStack(
            completionRewardStacks, DungeonCurrencyPolicy.BRONZE_COIN_ITEM_ID, payout.bronzeCoins()
        );
        forgeMaterialRoll.awards().forEach(award -> addCompletionRewardStack(
            completionRewardStacks, award.itemId(), award.count()
        ));
        haulData.registerCompletionRewards(
            rewardPlayer.getUUID(), runSessionId, completionRewardStacks
        );
        SoulOrbHaulData.Summary haulSummary = haulData.snapshot(rewardPlayer.getUUID(), runSessionId);

        TensuraEnergyDebugHelper.EnergyDebugSnapshot currentEnergy =
            TensuraEnergyDebugHelper.inspect(rewardPlayer);
        String currentEpStatus = currentEnergy.existenceAvailable() && currentEnergy.readError().isBlank()
            ? "AVAILABLE"
            : "UNAVAILABLE_" + (currentEnergy.readError().isBlank() ? "EXISTENCE" : currentEnergy.readError());
        int crystalCount = coreRecoveryResult.recovery().candidates().size();

        progressData.recordCompletedRun(
            rewardPlayer.getUUID(),
            rewardPlayer.getGameProfile().getName(),
            new DungeonFloorProgressData.RunContext(
                runSessionId,
                floorNumber,
                floorModeLabel(runState),
                "FINAL_ROOM:" + completionContext.objective(),
                runState.getTargetRoomCount(),
                0,
                "UNAVAILABLE_NOT_TRACKED",
                runState.getActiveModifiers().stream()
                    .map(modifier -> modifier.getId().toString())
                    .sorted()
                    .toList()
            ),
            new DungeonFloorProgressData.RunRewards(
                currencyAward.baseBronze(),
                currencyAward.bonusBronze(),
                currencyAward.payout().canonicalBronze(),
                haulSummary.counts().common(),
                haulSummary.counts().uncommon(),
                haulSummary.counts().rare(),
                haulSummary.counts().epic(),
                crystalCount
            ),
            level.getGameTime(),
            currentEnergy.ep(),
            currentEpStatus
        );

        long awardedCurrencyBronze = currencyAward.payout().canonicalBronze();
        DungeonCompletionPresentation.show(rewardPlayer, floorNumber, floorNumber + 1, awardedCurrencyBronze);
        RewardMailboxData.get(level).createFirstTimeFloorCompletionEpReward(
            level, rewardPlayer.getUUID(), floorRewardMetadata, level.getGameTime()
        );
    }

    private static void addCompletionRewardStack(
        List<ItemStack> rewardStacks,
        String itemId,
        int count
    ) {
        if (rewardStacks == null || itemId == null || itemId.isBlank() || count <= 0) return;
        try {
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId))
                .filter(item -> item != net.minecraft.world.item.Items.AIR)
                .ifPresent(item -> rewardStacks.add(new ItemStack(item, count)));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void onRoomFailed(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        BlockPos controllerPos,
        String reason
    ) {
        onRoomFailed(runState, markDirty, level, controllerPos, reason, null);
    }

    public void onRoomFailed(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        BlockPos controllerPos,
        String reason,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (!runState.isActiveRun()) {
            return;
        }
        CoreRecoveryData.get(level).discardActiveSession(
            runState.getRunSessionId(),
            runState.getFloorNumber()
        );
        BlockPos immutablePos = controllerPos.immutable();
        runState.markFailed(reason);
        runState.removeActiveRoom(immutablePos);
        clearExitPortal(runState, level, "CLEARED");
        String dimension = level.dimension().location().toString();
        GeneratedFloorRepository generatedRepository = instanceKey == null
            ? GeneratedFloorRepository.get(level)
            : GeneratedFloorRepository.get(level, dimension, instanceKey);
        GeneratedFloorSession generatedSession = generatedRepository.hydrate();
        boolean retainGeneratedReplay = generatedSession.placement() != null;
        if (!retainGeneratedReplay) {
            if (generatedSession.enqueueCleanupForPlacement()) {
                generatedRepository.flushCleanup(generatedSession);
                generatedSession.removePlacement();
                generatedRepository.flushPlacement(generatedSession);
            }
            generatedSession.clearComposition();
            generatedRepository.flushComposition(generatedSession);
        }
        runState.captureRetryModifiersFromActive();
        runState.onFloorFailed(true);
        archiveAndClearPresetState(runState, markDirty, "FLOOR_FAILED");
        markDirty.run();
    }

    public boolean advanceToNextFloor(DungeonRunState runState, Runnable markDirty, ServerLevel level) {
        if (!runState.isActiveRun() || !runState.isRunCompleted() || !runState.isNextFloorEligible()
            || runState.getNextFloorNumber() <= runState.getFloorNumber()) {
            return false;
        }
        runState.clearFailure();
        if (runState.hasActiveRooms()) {
            runState.clearActiveRooms();
        }
        clearExitPortal(runState, level, "ADVANCED_TO_NEXT_FLOOR");
        archiveAndClearPresetState(runState, markDirty, "ADVANCED_TO_NEXT_FLOOR");
        runState.advanceToNextFloor();
        runState.onAdvancedToNextFloor();
        markDirty.run();
        return true;
    }

    public boolean setFloorNumberForTesting(DungeonRunState runState, Runnable markDirty, int floorNumber) {
        if (!runState.isActiveRun() || runState.hasActiveRooms()) {
            return false;
        }
        runState.setFloorNumber(floorNumber);
        markDirty.run();
        return true;
    }

    public boolean setTargetRoomCountForTesting(DungeonRunState runState, Runnable markDirty, int targetRoomCount) {
        if (!runState.isActiveRun() || runState.hasActiveRooms() || runState.getCompletedRoomCount() > 0) {
            return false;
        }
        runState.setTargetRoomCount(targetRoomCount);
        markDirty.run();
        return true;
    }

    public boolean setForcedModifier(DungeonRunState runState, Runnable markDirty, String modifierId) {
        boolean changed = runState.setForcedModifier(modifierId);
        if (changed) {
            markDirty.run();
        }
        return changed;
    }

    public void clearModifiers(DungeonRunState runState, Runnable markDirty) {
        runState.clearModifiers("CLEARED");
        markDirty.run();
    }

    public boolean replacePendingPresetOrder(DungeonRunState runState, Runnable markDirty, List<BlockPos> controllerOrder) {
        boolean replaced = runState.replacePendingPresetOrder(controllerOrder);
        if (replaced) {
            markDirty.run();
        }
        return replaced;
    }

    public void enableRollingPresetMode(
        DungeonRunState runState,
        Runnable markDirty,
        List<BlockPos> controllerOrder,
        int requestedRoomCount
    ) {
        runState.enableRollingPresetMode(controllerOrder, requestedRoomCount);
        markDirty.run();
    }

    public boolean appendRollingPresetOrder(
        DungeonRunState runState,
        Runnable markDirty,
        List<BlockPos> appendedControllers
    ) {
        boolean appended = runState.appendRollingPresetOrder(appendedControllers);
        if (appended) {
            markDirty.run();
        }
        return appended;
    }

    public void enablePresetMode(DungeonRunState runState, Runnable markDirty, List<BlockPos> controllerOrder) {
        runState.enablePresetMode(controllerOrder);
        markDirty.run();
    }

    public void setDebugRoomActivationOverride(DungeonRunState runState, Runnable markDirty, boolean enabled) {
        runState.setDebugRoomActivationOverride(enabled);
        markDirty.run();
    }

    public void setHubSpawnAnchor(DungeonRunState runState, Runnable markDirty, BlockPos pos) {
        runState.setHubSpawnAnchor(pos);
        markDirty.run();
    }

    public void setHubReturnAnchor(DungeonRunState runState, Runnable markDirty, BlockPos pos) {
        runState.setHubReturnAnchor(pos);
        markDirty.run();
    }

    public void setHubGateAnchor(DungeonRunState runState, Runnable markDirty, BlockPos pos) {
        runState.setHubGateAnchor(pos);
        markDirty.run();
    }

    public void setHubSafeZone(DungeonRunState runState, Runnable markDirty, BlockPos cornerA, BlockPos cornerB) {
        runState.setHubSafeZone(cornerA, cornerB);
        markDirty.run();
    }

    public boolean returnPlayerToHub(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        @Nullable ServerPlayer player,
        String reason
    ) {
        BlockPos target = runState.getHubReturnTarget();
        if (!runState.isHubEnabled() || player == null || target == null) {
            runState.markHubReturnBlocked();
            return false;
        }
        player.teleportTo(
            level, target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D,
            player.getYRot(), player.getXRot()
        );
        runState.markReturnedToHub(reason);
        markDirty.run();
        return true;
    }

    public void onSuccessfulPortalContinueToHub(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        @Nullable ServerPlayer player
    ) {
        clearExitPortal(runState, level, "HUB_RETURN");
        runState.clearActiveRooms();
        runState.clearCompletedRooms();
        runState.prepareContinueToHub();
        archiveAndClearPresetState(runState, markDirty, "RETURNED_TO_HUB_AFTER_COMPLETION");
        returnPlayerToHub(runState, markDirty, level, player, "RETURNED_TO_HUB_AFTER_COMPLETION");
        markDirty.run();
    }

    public PortalCommandOperationResult continueFromPortalCommand(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        ServerPlayer player,
        Runnable generatedFloorCleanup
    ) {
        String blockReason = getExitPortalActionBlockReason(runState, level, player);
        if (!"NONE".equals(blockReason)) {
            return new PortalCommandOperationResult(blockReason, null);
        }
        CoreRecoveryData.get(level).finalizeForDeparture(
            player.getUUID(),
            runState.getRunSessionId(),
            runState.getFloorNumber()
        );
        onSuccessfulPortalContinueToHub(runState, markDirty, level, player);
        generatedFloorCleanup.run();
        ActiveRoomProtectionEvents.clearActiveControllerRegistrations(level);
        return new PortalCommandOperationResult("NONE", null);
    }

    public PortalCommandOperationResult returnHomeFromPortalCommand(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        ServerPlayer player,
        Runnable generatedFloorCleanup
    ) {
        String blockReason = getExitPortalActionBlockReason(runState, level, player);
        if (!"NONE".equals(blockReason)) {
            return new PortalCommandOperationResult(blockReason, null);
        }
        ServerLevel overworld = player.getServer().overworld();
        if (overworld == null) {
            return new PortalCommandOperationResult("OVERWORLD_UNAVAILABLE", null);
        }
        CoreRecoveryData.get(level).finalizeForDeparture(
            player.getUUID(),
            runState.getRunSessionId(),
            runState.getFloorNumber()
        );
        BlockPos spawnPos = overworld.getSharedSpawnPos();
        player.teleportTo(
            overworld, spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D,
            player.getYRot(), player.getXRot()
        );
        endRun(runState, markDirty, level);
        generatedFloorCleanup.run();
        ActiveRoomProtectionEvents.clearActiveControllerRegistrations(level);
        return new PortalCommandOperationResult("NONE", spawnPos);
    }

    public boolean returnPlayerToHubFromCommand(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        ServerPlayer player,
        String reason
    ) {
        if (!returnPlayerToHub(runState, markDirty, level, player, reason)) {
            return false;
        }
        ActiveRoomProtectionEvents.clearActiveControllerRegistrations(level);
        return true;
    }

    public HubFloorPreparationResult prepareFloorFromHubCommand(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        DungeonFloorProgressData floorProgress,
        UUID playerId,
        int requestedFloor
    ) {
        if (!runState.isFailed() && !floorProgress.isFloorUnlocked(playerId, requestedFloor)) {
            return new HubFloorPreparationResult("FLOOR_LOCKED");
        }
        if (!prepareFloorFromHub(runState, markDirty, level, requestedFloor)) {
            return new HubFloorPreparationResult("PREPARATION_FAILED");
        }
        ActiveRoomProtectionEvents.clearActiveControllerRegistrations(level);
        return new HubFloorPreparationResult("NONE");
    }

    public void onFailedFloorReturnToHub(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        @Nullable ServerPlayer player
    ) {
        clearExitPortal(runState, level, "HUB_RETURN_AFTER_FAILURE");
        runState.clearActiveRooms();
        runState.clearCompletedRooms();
        runState.prepareFailedFloorReturnToHub();
        archiveAndClearPresetState(runState, markDirty, "RETURNED_TO_HUB_AFTER_FAILURE");
        returnPlayerToHub(runState, markDirty, level, player, "RETURNED_TO_HUB_AFTER_FAILURE");
        markDirty.run();
    }

    public boolean prepareFloorFromHub(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        int requestedFloor
    ) {
        if (!runState.isActiveRun() || !runState.isHubEnabled()) {
            return false;
        }
        int floorToPrepare = Math.max(1, requestedFloor);
        if (runState.isFailed() && floorToPrepare != runState.getRetryFloorNumber()) {
            return false;
        }
        if (runState.isRunCompleted() && runState.isNextFloorEligible()
            && floorToPrepare != runState.getNextFloorNumber()
            && floorToPrepare != runState.getFloorNumber()) {
            return false;
        }
        clearExitPortal(runState, level, "PREPARE_FLOOR");
        boolean prepared = runState.prepareFloorFromHub(floorToPrepare, runState.isFailed());
        if (!prepared) {
            return false;
        }
        runState.clearHubPreparedPresetState();
        if (runState.isFailed() && runState.isRetryPreservingModifiers()
            && runState.getRetryFloorNumber() == floorToPrepare) {
            runState.restoreRetryModifiers();
        }
        runState.clearFailure();
        markDirty.run();
        return true;
    }

    public void markHubPresetPrepared(DungeonRunState runState, Runnable markDirty, int rooms) {
        runState.markHubPresetPrepared(rooms);
        markDirty.run();
    }

    public void setHubGateState(DungeonRunState runState, Runnable markDirty, String state, String reason) {
        runState.setHubGateState(state, reason);
        markDirty.run();
    }

    public boolean isExitPortalBlockPresent(DungeonRunState runState, ServerLevel level) {
        BlockPos portalPos = runState.getExitPortalPos();
        return level != null && portalPos != null && level.getBlockState(portalPos).is(ModBlocks.FLOOR_EXIT_PORTAL.get());
    }

    public String getExitPortalDebugState(DungeonRunState runState, ServerLevel level) {
        boolean blockPresent = isExitPortalBlockPresent(runState, level);
        if (runState.isExitPortalActive() && blockPresent) {
            return "ACTIVE_AND_BLOCK_PRESENT";
        }
        if (runState.isExitPortalActive()) {
            return runState.getExitPortalPos() == null ? "ACTIVE_WITHOUT_POSITION" : "ACTIVE_BUT_BLOCK_MISSING";
        }
        return blockPresent ? "INACTIVE_BUT_BLOCK_PRESENT" : "INACTIVE_AND_NO_BLOCK";
    }

    public String getExitPortalInteractionBlockReason(
        DungeonRunState runState,
        ServerLevel level,
        ServerPlayer player,
        BlockPos clickedPos
    ) {
        String actionReason = getExitPortalActionBlockReason(runState, level, player);
        if (!"NONE".equals(actionReason)) {
            return actionReason;
        }
        if (clickedPos == null) {
            return "EXIT_PORTAL_CLICK_POS_MISSING";
        }
        BlockPos portalPos = runState.getExitPortalPos();
        if (portalPos == null) {
            return "EXIT_PORTAL_BLOCK_POSITION_MISSING";
        }
        if (!clickedPos.equals(portalPos)) {
            return "EXIT_PORTAL_BLOCK_NOT_CURRENT";
        }
        return "NONE";
    }

    public String getExitPortalActionBlockReason(DungeonRunState runState, ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) {
            return "INVALID_CONTEXT";
        }
        if (!runState.isActiveRun()) {
            return "NO_ACTIVE_RUN";
        }
        if (!runState.isRunCompleted()) {
            return "RUN_NOT_COMPLETED";
        }
        if (!runState.isNextFloorEligible()) {
            return "NEXT_FLOOR_NOT_ELIGIBLE";
        }
        if (!runState.isExitPortalActive()) {
            return "EXIT_PORTAL_NOT_ACTIVE";
        }
        BlockPos portalPos = runState.getExitPortalPos();
        if (portalPos == null) {
            return "EXIT_PORTAL_POSITION_MISSING";
        }
        if (!isExitPortalBlockPresent(runState, level)) {
            return "EXIT_PORTAL_BLOCK_MISSING";
        }
        double maxDistanceSqr = EXIT_PORTAL_USE_DISTANCE * EXIT_PORTAL_USE_DISTANCE;
        if (player.distanceToSqr(portalPos.getX() + 0.5D, portalPos.getY() + 0.5D, portalPos.getZ() + 0.5D) > maxDistanceSqr) {
            return "PLAYER_TOO_FAR_FROM_EXIT_PORTAL";
        }
        return "NONE";
    }

    public void clearExitPortal(DungeonRunState runState, ServerLevel level, String reason) {
        DungeonExitPortalVisual.remove(
            level, runState.getExitPortalPos(), runState.getRunSessionId()
        );
        clearPlacedExitPortalBlock(runState, level);
        setExitPortalState(runState, false, null, "NONE", reason, "cleared");
    }

    public ExitPortalPlacementResult analyzeExitPortalPlacement(
        ServerLevel level,
        BlockPos controllerPos,
        @Nullable RoomControllerBlockEntity controller
    ) {
        if (level == null) {
            return new ExitPortalPlacementResult(false, null, "NONE", "INVALID_LEVEL", "level=null");
        }
        if (controller == null) {
            return new ExitPortalPlacementResult(
                false, null, "NONE", "CONTROLLER_NOT_FOUND", "controller=" + formatPos(controllerPos)
            );
        }

        controller.scanMarkers(level);
        List<BlockPos> exitAnchors = new ArrayList<>(controller.getExitAnchors());
        if (exitAnchors.isEmpty()) {
            return new ExitPortalPlacementResult(
                false, null, "NONE", "NO_EXIT_ANCHOR", "controller=" + formatPos(controller.getBlockPos())
            );
        }

        exitAnchors.sort(Comparator.comparingDouble(anchor -> anchor.distSqr(controllerPos)));
        List<String> failures = new ArrayList<>();
        for (BlockPos exitAnchor : exitAnchors) {
            BlockPos candidatePos = exitAnchor.above();
            String invalidReason = getInvalidExitPortalPositionReason(level, candidatePos);
            if (invalidReason == null) {
                return new ExitPortalPlacementResult(
                    true, candidatePos, "EXIT_ANCHOR", "READY",
                    "anchor=" + formatPos(exitAnchor) + " -> portal=" + formatPos(candidatePos)
                );
            }
            failures.add(formatPos(exitAnchor) + " -> " + invalidReason);
        }

        String details = failures.isEmpty() ? "no candidate anchors evaluated" : String.join("; ", failures);
        String primaryReason = failures.isEmpty()
            ? "NO_VALID_EXIT_POSITION"
            : failures.get(0).substring(failures.get(0).indexOf("->") + 3);
        return new ExitPortalPlacementResult(false, null, "EXIT_ANCHOR", primaryReason, details);
    }

    private void ensureExitPortalForCompletedFloor(
        DungeonRunState runState,
        Runnable markDirty,
        ServerLevel level,
        BlockPos controllerPos
    ) {
        if (!runState.isActiveRun() || runState.isFailed() || !runState.isRunCompleted()
            || !"TARGET_ROOMS_COMPLETED".equals(runState.getCompletionReason())) {
            setExitPortalState(runState, false, null, "NONE", "RUN_NOT_COMPLETED", "completedRun=false");
            markDirty.run();
            return;
        }
        if (runState.isExitPortalActive() && runState.getExitPortalPos() != null
            && level.getBlockState(runState.getExitPortalPos()).is(ModBlocks.FLOOR_EXIT_PORTAL.get())) {
            boolean visualPresent = DungeonExitPortalVisual.ensurePresent(
                level, runState.getExitPortalPos(), runState.getRunSessionId()
            );
            setExitPortalState(
                runState, true, runState.getExitPortalPos(), runState.getExitPortalSource(), "READY",
                "existing portal reused at " + formatPos(runState.getExitPortalPos())
                    + "; presentation=" + portalPresentation(visualPresent)
            );
            markDirty.run();
            return;
        }

        clearPlacedExitPortalBlock(runState, level);

        if (!(level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller)) {
            setExitPortalState(runState, false, null, "NONE", "CONTROLLER_NOT_FOUND", "controller=" + formatPos(controllerPos));
            markDirty.run();
            return;
        }

        ExitPortalPlacementResult result = analyzeExitPortalPlacement(level, controllerPos, controller);
        if (result.ready() && result.portalPos() != null) {
            level.setBlockAndUpdate(result.portalPos(), ModBlocks.FLOOR_EXIT_PORTAL.get().defaultBlockState());
            boolean visualPresent = DungeonExitPortalVisual.ensurePresent(
                level, result.portalPos(), runState.getRunSessionId()
            );
            setExitPortalState(
                runState, true, result.portalPos(), result.source(), result.reason(),
                result.details() + "; presentation=" + portalPresentation(visualPresent)
            );
            markDirty.run();
            return;
        }

        setExitPortalState(runState, false, null, result.source(), result.reason(), result.details());
        markDirty.run();
    }

    private static String portalPresentation(boolean visualPresent) {
        return visualPresent ? "TENSURA_ADDITIONS_ENTITY" : "AUTHORITATIVE_BLOCK_FALLBACK";
    }

    private static String floorModeLabel(DungeonRunState runState) {
        if (runState.isPresetMode()) {
            return "PRESET_LINEAR";
        }
        if (runState.isActiveRun()) {
            return "SINGLE_ROOM";
        }
        if (runState.isPreparedFloorReady()) {
            return "HUB_PREPARED";
        }
        return "NONE";
    }

    private static String normalizeCompletionValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static RoomCompletionResult completionResult(DungeonRunState runState, boolean completedNow) {
        return new RoomCompletionResult(
            completedNow, runState.getFloorNumber(), runState.getRunSessionId(), runState.getCompletionReason()
        );
    }

    private static @Nullable String getInvalidExitPortalPositionReason(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return "PORTAL_POSITION_MISSING";
        }
        BlockState stateAtPos = level.getBlockState(pos);
        if (!stateAtPos.canBeReplaced()) {
            return "EXIT_PORTAL_BLOCKED_AT_PORTAL";
        }
        BlockState stateAbove = level.getBlockState(pos.above());
        if (!stateAbove.canBeReplaced()) {
            return "EXIT_PORTAL_BLOCKED_ABOVE";
        }
        BlockState stateBelow = level.getBlockState(pos.below());
        if (!stateBelow.isSolidRender(level, pos.below())) {
            return "EXIT_PORTAL_NO_SOLID_FLOOR";
        }
        return null;
    }

    private void archiveAndClearPresetState(DungeonRunState runState, Runnable markDirty, String reason) {
        runState.archiveAndClearPresetState(reason);
        markDirty.run();
    }

    private static void clearPlacedExitPortalBlock(DungeonRunState runState, ServerLevel level) {
        BlockPos portalPos = runState.getExitPortalPos();
        if (portalPos != null && level.getBlockState(portalPos).is(ModBlocks.FLOOR_EXIT_PORTAL.get())) {
            level.setBlockAndUpdate(portalPos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void setExitPortalState(
        DungeonRunState runState,
        boolean active,
        BlockPos pos,
        String source,
        String reason,
        String details
    ) {
        runState.setExitPortalState(active, pos, source, reason, details);
    }

    private static void cleanupActiveControllers(DungeonRunState runState, ServerLevel level) {
        for (BlockPos controllerPos : List.copyOf(runState.getActiveRoomControllerPositions())) {
            if (level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller) {
                controller.cleanupRoom(level);
            }
        }
    }

    private static boolean prepareControllerForFloorStart(
        ServerLevel level,
        @Nullable RoomControllerBlockEntity controller
    ) {
        if (controller == null || controller.getRoomState() == RoomControllerBlockEntity.RoomState.ACTIVE) {
            return false;
        }
        controller.resetRoom(level);
        return true;
    }

    private static void recordFloorStartTelemetry(
        DungeonFloorProgressData floorProgress,
        DungeonRunState runState,
        ServerLevel level,
        UUID playerId
    ) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        TensuraEnergyDebugHelper.EnergyDebugSnapshot energy = player == null
            ? TensuraEnergyDebugHelper.EnergyDebugSnapshot.error("PLAYER_MISSING")
            : TensuraEnergyDebugHelper.inspect(player);
        String epStatus = energy.existenceAvailable() && energy.readError().isBlank()
            ? "AVAILABLE"
            : "UNAVAILABLE_" + (energy.readError().isBlank() ? "EXISTENCE" : energy.readError());
        floorProgress.recordFloorStarted(
            playerId,
            runState.getFloorNumber(),
            level.getGameTime(),
            runState.getRunSessionId(),
            energy.ep(),
            epStatus
        );
    }

    private static void applyPreparedModifierPlan(
        DungeonFloorProgressData floorProgress,
        DungeonRunState runState,
        UUID playerId,
        int floor,
        Runnable markDirty
    ) {
        runState.applyPreparedModifierPlan(floorProgress.getOrPrepareModifierPlan(playerId, floor));
        markDirty.run();
    }

    private static String formatPos(@Nullable BlockPos pos) {
        if (pos == null) {
            return "NONE";
        }
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
