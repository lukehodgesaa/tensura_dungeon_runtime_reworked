package com.mineclawd.tensuradungeon.block.entity;

import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class RoomLifecycle {
    static final int REACTIVATION_COOLDOWN_TICKS = 60;
    static final int HUB_GATE_RESEAL_DELAY_TICKS = 10 * 20;
    static final int POST_COMPLETION_PROTECTION_GRACE_TICKS = 300;

    private RoomControllerBlockEntity.RoomState state = RoomControllerBlockEntity.RoomState.INACTIVE;
    private boolean completed;
    private boolean branchPreviewMode;
    private int tickCounter;
    private int reactivationCooldownTicks;
    private int hubGateResealTicks;
    private long lastMaintenanceGameTime;
    private long completedProtectionUntilGameTime;
    private String lastActivationFailureReason = "None";
    private String lastFailureReason = "NONE";
    private String lastCompletionReason = "NONE";
    private boolean frontierCommitRetryRequiresExit;
    private RoomProfile profile = RoomProfile.STANDARD_COMBAT;
    private boolean reloadNormalizationPending;

    Snapshot snapshot() {
        return new Snapshot(
            state,
            completed,
            branchPreviewMode,
            tickCounter,
            reactivationCooldownTicks,
            hubGateResealTicks,
            lastMaintenanceGameTime,
            completedProtectionUntilGameTime,
            lastActivationFailureReason,
            lastFailureReason,
            lastCompletionReason,
            profile,
            reloadNormalizationPending
        );
    }

    TickAdvance advanceOrdinaryTick(Runnable ensureRuneAppearance) {
        tickCounter += 1;
        boolean firstTick = tickCounter == 1;
        if (firstTick) {
            ensureRuneAppearance.run();
        }
        if (reactivationCooldownTicks > 0) {
            reactivationCooldownTicks = Math.max(0, reactivationCooldownTicks - 1);
        }
        boolean closeHubGate = false;
        if (hubGateResealTicks > 0) {
            hubGateResealTicks -= 1;
            closeHubGate = hubGateResealTicks == 0;
        }
        return new TickAdvance(tickCounter, firstTick, closeHubGate);
    }

    void tick(TickActions actions) {
        if (reloadNormalizationPending) {
            actions.normalizeAfterReload().run();
            return;
        }

        TickAdvance advance = advanceOrdinaryTick(actions.ensureRuneAppearance());
        if (advance.closeHubGate()) {
            actions.closeHubGate().run();
            actions.markChangedAfterHubGate().run();
        }
        actions.ensureMarkerCache().run();
        actions.syncRunActivationState().run();
        actions.syncProtection().run();

        if (state == RoomControllerBlockEntity.RoomState.RESETTING) {
            actions.performResettingTick().run();
            return;
        }

        if (state == RoomControllerBlockEntity.RoomState.ACTIVE) {
            tickActiveRoom(advance.tickCounter(), actions.activeRoom());
            return;
        }

        tickInactiveRoom(actions.inactiveRoom());
    }

    private void tickActiveRoom(int currentTick, ActiveTickActions actions) {
        actions.handlePlayers().run();
        if (state != RoomControllerBlockEntity.RoomState.ACTIVE) {
            return;
        }
        actions.updateObjective().run();
        if (state != RoomControllerBlockEntity.RoomState.ACTIVE) {
            return;
        }
        actions.updateMobResetCooldowns().run();
        actions.tickEncounterWaves().run();
        actions.updateTrackedMobs().run();
        if (currentTick % actions.aggroRefreshInterval() == 0) {
            actions.refreshAggroTargets().run();
        }
        if (state != RoomControllerBlockEntity.RoomState.ACTIVE) {
            return;
        }
        actions.updateRegenerativePulse().run();
        actions.updateSummonerPulse().run();
    }

    private void tickInactiveRoom(InactiveTickActions actions) {
        if (completed
            || state != RoomControllerBlockEntity.RoomState.INACTIVE
            || reactivationCooldownTicks > 0) {
            return;
        }
        if (!actions.hasPlayerInActivationZone().getAsBoolean()) {
            frontierCommitRetryRequiresExit = false;
            return;
        }
        if (frontierCommitRetryRequiresExit) return;
        if (!actions.frontierCandidate().getAsBoolean()) {
            if (actions.runActivationBlocked().getAsBoolean()) {
                actions.notifyRunActivationBlocked().run();
                return;
            }
            if (actions.activationBlocked().getAsBoolean()) {
                return;
            }
        }
        actions.activate().run();
    }

    void performResettingTick(ResettingTickActions actions) {
        actions.clearPreviews().run();
        actions.openBarrier().run();
        actions.clearEncounterState().run();
        actions.clearPlayerState().run();
        actions.resetModifierState().run();
        finishResettingTick();
        actions.syncProtection().run();
        actions.clearConfirmedMobRemovalFlag().run();
        actions.markChanged().run();
    }

    void scheduleHubGateReseal() {
        hubGateResealTicks = HUB_GATE_RESEAL_DELAY_TICKS;
    }

    ActivationResult activate(ActivationActions actions) {
        long activationStartedAt = System.nanoTime();
        long phaseStartedAt = activationStartedAt;
        actions.ensureMarkerCache().run();
        double markerCacheMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();

        ActivationGuards guards = actions.guards();
        if (guards.frontierCandidate().getAsBoolean() && !guards.commitFrontierOnEntry().getAsBoolean()) {
            frontierCommitRetryRequiresExit = true;
            lastActivationFailureReason = guards.frontierCommitFailureReason().get();
            return ActivationResult.failed();
        }
        frontierCommitRetryRequiresExit = false;
        double frontierCommitMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();

        String runBlockReason = guards.runBlockReason().get();
        guards.recordRunBlockReason().accept(runBlockReason);
        if (!"NONE".equals(runBlockReason)) {
            lastActivationFailureReason = guards.runBlockMessage().apply(runBlockReason);
            return ActivationResult.failed();
        }

        String activationBlockReason = guards.activationBlockReason().get();
        lastActivationFailureReason = activationBlockReason;
        if (!"None".equals(activationBlockReason)) {
            return ActivationResult.failed();
        }
        double guardChecksMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();

        String registrationResult = guards.registerRoom().get();
        if (!"NONE".equals(registrationResult)) {
            lastActivationFailureReason = registrationResult;
            guards.recordRunBlockReason().accept(registrationResult);
            return ActivationResult.failed();
        }
        double registrationMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();

        if (actions.firstGeneratedRoom().getAsBoolean()) {
            scheduleHubGateReseal();
        }
        actions.clearPreviews().run();
        beginActivation();
        actions.initializeRuntimeState().run();
        guards.recordRunBlockReason().accept("NONE");
        clearCompletionContext();
        double stateInitializationMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();

        ActivationRuntimeActions runtime = actions.runtime();
        runtime.associateTriggeringPlayer().run();
        runtime.clearTemporarySeal().run();
        runtime.closeBarrier().run();
        runtime.setupRoomRuntime().run();
        double runtimeSetupMs = elapsedMillis(phaseStartedAt);
        double hudStateReadyMs = elapsedMillis(activationStartedAt);
        phaseStartedAt = System.nanoTime();

        ActivationFinalizationActions finalization = actions.finalization();
        String autoCompletionReason = finalization.autoCompletionReason().get();
        if (!"NONE".equals(autoCompletionReason)) {
            setCompletionReason(autoCompletionReason);
            finalization.prepareAutoCompletion().accept(autoCompletionReason);
            finalization.complete().run();
            return new ActivationResult(
                true,
                true,
                "AUTO_COMPLETED",
                new ActivationTiming(
                    markerCacheMs,
                    frontierCommitMs,
                    guardChecksMs,
                    registrationMs,
                    stateInitializationMs,
                    runtimeSetupMs,
                    hudStateReadyMs,
                    elapsedMillis(phaseStartedAt),
                    elapsedMillis(activationStartedAt)
                )
            );
        }
        if (finalization.summonerModifierActive().getAsBoolean()) {
            finalization.selectSummoners().run();
        }
        finishActivation();
        finalization.syncProtection().run();
        finalization.markChanged().run();
        return new ActivationResult(
            true,
            false,
            "PASS",
            new ActivationTiming(
                markerCacheMs,
                frontierCommitMs,
                guardChecksMs,
                registrationMs,
                stateInitializationMs,
                runtimeSetupMs,
                hudStateReadyMs,
                elapsedMillis(phaseStartedAt),
                elapsedMillis(activationStartedAt)
            )
        );
    }

    private static double elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0D;
    }

    void beginActivation() {
        frontierCommitRetryRequiresExit = false;
        state = RoomControllerBlockEntity.RoomState.ACTIVE;
        completed = false;
    }

    void finishActivation() {
        reactivationCooldownTicks = 0;
    }

    void beginCompletion() {
        state = RoomControllerBlockEntity.RoomState.COMPLETED;
        completed = true;
    }

    <T> void complete(long gameTime, CompletionActions<T> actions) {
        T rewardRecipient = actions.resolveRewardRecipient().get();
        actions.clearRuneObjective().run();
        beginCompletion();
        actions.deactivateObjective().run();
        String completionReason = lastCompletionReason;
        boolean automatic = isAutomaticCompletion(completionReason);
        if (automatic) {
            actions.markAutomaticObjectiveCompleted().run();
        } else {
            actions.announceNormalCompletion().run();
        }
        startCompletionProtection(gameTime);
        actions.clearPreviews().run();
        actions.openBarrier().run();
        if (!actions.openSuccessorIncomingSeal().getAsBoolean()) {
            actions.retainTerminalOutgoingSeal().run();
        }
        actions.playRoomCompletedSound().run();
        if (!automatic) {
            actions.playObjectiveCompletedSound().run();
        }
        if (rewardRecipient != null) {
            actions.createRoomReward().accept(rewardRecipient);
        }
        actions.clearPlayerAssociation().run();
        actions.syncProtection().run();
        actions.resolvePlayerDirectedExit().accept(rewardRecipient);
        RoomRunGateway.CompletionResult runResult = actions.completeRun().apply(completionReason);
        if (runResult.floorCompletedNow()) {
            actions.playFloorCompletedSound().run();
        }
        if (rewardRecipient != null && runResult.floorCompletedNow()) {
            actions.createFloorReward().accept(rewardRecipient, runResult);
        }
        actions.markChanged().run();
    }

    private static boolean isAutomaticCompletion(String reason) {
        return "SAFE_ROOM_AUTO_COMPLETED".equals(reason)
            || "PASSIVE_ROOM_AUTO_COMPLETED".equals(reason);
    }

    void startCompletionProtection(long gameTime) {
        completedProtectionUntilGameTime = gameTime + POST_COMPLETION_PROTECTION_GRACE_TICKS;
    }

    void beginFailure(String reason) {
        lastFailureReason = reason;
        lastActivationFailureReason = reason;
        lastCompletionReason = "NONE";
    }

    void fail(long gameTime, String reason, FailureActions actions) {
        beginFailure(reason);
        actions.clearPreviews().run();
        actions.openBarrier().run();
        actions.clearRuneObjective().run();
        actions.clearTrackedMobs().run();
        actions.clearEncounterTimers().run();
        actions.clearPlayerAssociation().run();
        actions.resetRuntimeState().run();
        finishFailure(gameTime);
        actions.clearConfirmedMobRemovalFlag().run();
        actions.syncProtection().run();
        actions.failRun().run();
        actions.returnFailedFloorToHub().run();
        actions.markChanged().run();
    }

    void finishFailure(long gameTime) {
        frontierCommitRetryRequiresExit = false;
        state = RoomControllerBlockEntity.RoomState.INACTIVE;
        completed = false;
        reactivationCooldownTicks = REACTIVATION_COOLDOWN_TICKS;
        lastMaintenanceGameTime = gameTime;
        completedProtectionUntilGameTime = 0L;
    }

    void markResettingWarning(String activationReason, String failureReason) {
        lastActivationFailureReason = activationReason;
        lastFailureReason = failureReason;
        state = RoomControllerBlockEntity.RoomState.RESETTING;
        completed = false;
    }

    void beginAdministrativeReset() {
        frontierCommitRetryRequiresExit = false;
        state = RoomControllerBlockEntity.RoomState.INACTIVE;
        completed = false;
    }

    void reset(long gameTime, AdministrativeResetActions actions) {
        actions.clearPreviews().run();
        actions.openBarrier().run();
        actions.clearRuneObjective().run();
        actions.clearTrackedMobs().run();
        beginAdministrativeReset();
        actions.clearConfirmedMobRemovalFlag().run();
        actions.resetRuntimeState().run();
        actions.recordNoRunActivationBlock().run();
        finishAdministrativeReset(gameTime);
        actions.syncProtection().run();
        actions.clearPlayerAssociation().run();
        actions.markMarkerCacheDirty().run();
        actions.syncProtection().run();
        actions.markChanged().run();
    }

    void finishAdministrativeReset(long gameTime) {
        lastCompletionReason = "NONE";
        completedProtectionUntilGameTime = 0L;
        tickCounter = 0;
        reactivationCooldownTicks = 0;
        lastMaintenanceGameTime = gameTime;
    }

    void cleanup(long gameTime, boolean hasBounds) {
        if (!isProtectionGraceActive(gameTime, hasBounds)) {
            completedProtectionUntilGameTime = 0L;
        }
        state = completed
            ? RoomControllerBlockEntity.RoomState.COMPLETED
            : RoomControllerBlockEntity.RoomState.INACTIVE;
        lastMaintenanceGameTime = gameTime;
    }

    void cleanup(long gameTime, boolean hasBounds, CleanupActions actions) {
        actions.clearPreviews().run();
        actions.openBarrier().run();
        actions.clearTrackedMobs().run();
        actions.clearEncounterTimers().run();
        actions.clearPlayerAssociation().run();
        actions.resetRuntimeState().run();
        cleanup(gameTime, hasBounds);
        actions.syncProtection().run();
        actions.markChanged().run();
    }

    void finishResettingTick() {
        frontierCommitRetryRequiresExit = false;
        state = completed
            ? RoomControllerBlockEntity.RoomState.COMPLETED
            : RoomControllerBlockEntity.RoomState.INACTIVE;
    }

    void normalizeAfterReload() {
        state = completed
            ? RoomControllerBlockEntity.RoomState.COMPLETED
            : RoomControllerBlockEntity.RoomState.INACTIVE;
        reactivationCooldownTicks = Math.max(reactivationCooldownTicks, REACTIVATION_COOLDOWN_TICKS);
        reloadNormalizationPending = false;
    }

    void normalizeAfterReload(
        long gameTime,
        boolean hasBounds,
        CleanupActions cleanupActions,
        ReloadNormalizationActions reloadActions
    ) {
        cleanup(gameTime, hasBounds, cleanupActions);
        reloadActions.resetSpawnDebugState().run();
        reloadActions.resetSummonerState().run();
        normalizeAfterReload();
        reloadActions.markChanged().run();
    }

    void armReloadNormalization(boolean trackedMobsEmpty) {
        reloadNormalizationPending = state == RoomControllerBlockEntity.RoomState.ACTIVE
            || state == RoomControllerBlockEntity.RoomState.RESETTING;
        if (state == RoomControllerBlockEntity.RoomState.ACTIVE && trackedMobsEmpty) {
            state = RoomControllerBlockEntity.RoomState.RESETTING;
            completed = false;
            reloadNormalizationPending = true;
        }
    }

    void setActivationFailureReason(String reason) {
        lastActivationFailureReason = reason;
    }

    void setCompletionReason(String reason) {
        lastCompletionReason = reason == null ? "NONE" : reason;
    }

    void clearCompletionContext() {
        lastCompletionReason = "NONE";
        completedProtectionUntilGameTime = 0L;
    }

    void setProfile(RoomProfile profile) {
        this.profile = profile == null ? RoomProfile.STANDARD_COMBAT : profile;
    }

    void setBranchPreviewMode(boolean branchPreviewMode) {
        this.branchPreviewMode = branchPreviewMode;
    }

    boolean isProtectionGraceActive(long gameTime, boolean hasBounds) {
        return completed && hasBounds && gameTime <= completedProtectionUntilGameTime;
    }

    long protectionGraceTicksRemaining(long gameTime) {
        return Math.max(0L, completedProtectionUntilGameTime - gameTime);
    }

    void loadPersistentState(
        RoomControllerBlockEntity.RoomState state,
        boolean completed,
        String lastActivationFailureReason,
        String lastFailureReason,
        String lastCompletionReason,
        RoomProfile profile,
        int reactivationCooldownTicks,
        int hubGateResealTicks,
        long lastMaintenanceGameTime,
        long completedProtectionUntilGameTime,
        boolean branchPreviewMode
    ) {
        frontierCommitRetryRequiresExit = false;
        this.state = state;
        this.completed = completed;
        this.lastActivationFailureReason = lastActivationFailureReason;
        this.lastFailureReason = lastFailureReason;
        this.lastCompletionReason = lastCompletionReason;
        this.profile = profile;
        this.reactivationCooldownTicks = reactivationCooldownTicks;
        this.hubGateResealTicks = hubGateResealTicks;
        this.lastMaintenanceGameTime = lastMaintenanceGameTime;
        this.completedProtectionUntilGameTime = completedProtectionUntilGameTime;
        this.branchPreviewMode = branchPreviewMode;
    }

    record TickAdvance(int tickCounter, boolean firstTick, boolean closeHubGate) {
    }

    record TickActions(
        Runnable normalizeAfterReload,
        Runnable ensureRuneAppearance,
        Runnable closeHubGate,
        Runnable markChangedAfterHubGate,
        Runnable ensureMarkerCache,
        Runnable syncRunActivationState,
        Runnable syncProtection,
        Runnable performResettingTick,
        ActiveTickActions activeRoom,
        InactiveTickActions inactiveRoom
    ) {
    }

    record ActiveTickActions(
        Runnable handlePlayers,
        Runnable updateObjective,
        Runnable updateMobResetCooldowns,
        Runnable tickEncounterWaves,
        Runnable updateTrackedMobs,
        int aggroRefreshInterval,
        Runnable refreshAggroTargets,
        Runnable updateRegenerativePulse,
        Runnable updateSummonerPulse
    ) {
    }

    record InactiveTickActions(
        BooleanSupplier hasPlayerInActivationZone,
        BooleanSupplier frontierCandidate,
        BooleanSupplier runActivationBlocked,
        Runnable notifyRunActivationBlocked,
        BooleanSupplier activationBlocked,
        Runnable activate
    ) {
    }

    record ResettingTickActions(
        Runnable clearPreviews,
        Runnable openBarrier,
        Runnable clearEncounterState,
        Runnable clearPlayerState,
        Runnable resetModifierState,
        Runnable syncProtection,
        Runnable clearConfirmedMobRemovalFlag,
        Runnable markChanged
    ) {
    }

    record ActivationActions(
        Runnable ensureMarkerCache,
        ActivationGuards guards,
        BooleanSupplier firstGeneratedRoom,
        Runnable clearPreviews,
        Runnable initializeRuntimeState,
        ActivationRuntimeActions runtime,
        ActivationFinalizationActions finalization
    ) {
    }

    record ActivationGuards(
        BooleanSupplier frontierCandidate,
        BooleanSupplier commitFrontierOnEntry,
        Supplier<String> frontierCommitFailureReason,
        Supplier<String> runBlockReason,
        Consumer<String> recordRunBlockReason,
        Function<String, String> runBlockMessage,
        Supplier<String> activationBlockReason,
        Supplier<String> registerRoom
    ) {
    }

    record ActivationRuntimeActions(
        Runnable associateTriggeringPlayer,
        Runnable clearTemporarySeal,
        Runnable closeBarrier,
        Runnable setupRoomRuntime
    ) {
    }

    record ActivationFinalizationActions(
        Supplier<String> autoCompletionReason,
        Consumer<String> prepareAutoCompletion,
        Runnable complete,
        BooleanSupplier summonerModifierActive,
        Runnable selectSummoners,
        Runnable syncProtection,
        Runnable markChanged
    ) {
    }

    record ActivationResult(boolean activated, boolean autoCompleted, String profileResult, ActivationTiming timing) {
        static ActivationResult failed() {
            return new ActivationResult(false, false, "FAILED", null);
        }
    }

    record ActivationTiming(
        double markerCacheMs,
        double frontierCommitMs,
        double guardChecksMs,
        double registrationMs,
        double stateInitializationMs,
        double runtimeSetupMs,
        double hudStateReadyMs,
        double finalizationMs,
        double totalMs
    ) {
    }

    record CompletionActions<T>(
        Supplier<T> resolveRewardRecipient,
        Runnable clearRuneObjective,
        Runnable deactivateObjective,
        Runnable markAutomaticObjectiveCompleted,
        Runnable announceNormalCompletion,
        Runnable clearPreviews,
        Runnable openBarrier,
        BooleanSupplier openSuccessorIncomingSeal,
        Runnable retainTerminalOutgoingSeal,
        Runnable playRoomCompletedSound,
        Runnable playObjectiveCompletedSound,
        Consumer<T> createRoomReward,
        Runnable clearPlayerAssociation,
        Runnable syncProtection,
        Consumer<T> resolvePlayerDirectedExit,
        Function<String, RoomRunGateway.CompletionResult> completeRun,
        Runnable playFloorCompletedSound,
        BiConsumer<T, RoomRunGateway.CompletionResult> createFloorReward,
        Runnable markChanged
    ) {
    }

    record FailureActions(
        Runnable clearPreviews,
        Runnable openBarrier,
        Runnable clearRuneObjective,
        Runnable clearTrackedMobs,
        Runnable clearEncounterTimers,
        Runnable clearPlayerAssociation,
        Runnable resetRuntimeState,
        Runnable clearConfirmedMobRemovalFlag,
        Runnable syncProtection,
        Runnable failRun,
        Runnable returnFailedFloorToHub,
        Runnable markChanged
    ) {
    }

    record AdministrativeResetActions(
        Runnable clearPreviews,
        Runnable openBarrier,
        Runnable clearRuneObjective,
        Runnable clearTrackedMobs,
        Runnable clearConfirmedMobRemovalFlag,
        Runnable resetRuntimeState,
        Runnable recordNoRunActivationBlock,
        Runnable syncProtection,
        Runnable clearPlayerAssociation,
        Runnable markMarkerCacheDirty,
        Runnable markChanged
    ) {
    }

    record CleanupActions(
        Runnable clearPreviews,
        Runnable openBarrier,
        Runnable clearTrackedMobs,
        Runnable clearEncounterTimers,
        Runnable clearPlayerAssociation,
        Runnable resetRuntimeState,
        Runnable syncProtection,
        Runnable markChanged
    ) {
    }

    record ReloadNormalizationActions(
        Runnable resetSpawnDebugState,
        Runnable resetSummonerState,
        Runnable markChanged
    ) {
    }

    record Snapshot(
        RoomControllerBlockEntity.RoomState state,
        boolean completed,
        boolean branchPreviewMode,
        int tickCounter,
        int reactivationCooldownTicks,
        int hubGateResealTicks,
        long lastMaintenanceGameTime,
        long completedProtectionUntilGameTime,
        String lastActivationFailureReason,
        String lastFailureReason,
        String lastCompletionReason,
        RoomProfile profile,
        boolean reloadNormalizationPending
    ) {
    }
}
