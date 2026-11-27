package com.mineclawd.tensuradungeon.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinCatalog;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public final class DungeonRunState {
    private static final int DEFAULT_TARGET_ROOM_COUNT = 1;

    @Nullable
    private DungeonInstanceAllocationData.InstanceKey instanceKey;
    private boolean activeRun;
    private String runSessionId = "NONE";
    private long runSeed;
    private boolean failed;
    private String failureReason = "NONE";
    private boolean debugRoomActivationOverride;
    private final Set<DungeonModifierDefinition> activeModifiers = new LinkedHashSet<>();
    private final Set<String> activeModifierIds = new LinkedHashSet<>();
    private String modifierReason = "NONE";
    private final Set<DungeonModifierDefinition> retryModifiers = new LinkedHashSet<>();
    private final Set<String> retryModifierIds = new LinkedHashSet<>();
    private int floorNumber = 1;
    private final Set<BlockPos> activeRoomControllerPositions = new LinkedHashSet<>();
    private BlockPos activeRoomControllerPos;
    private final Set<BlockPos> completedRoomControllerPositions = new LinkedHashSet<>();
    private boolean runCompleted;
    private int targetRoomCount = DEFAULT_TARGET_ROOM_COUNT;
    private String completionReason = "NONE";
    private boolean nextFloorEligible;
    private int nextFloorNumber = 1;
    private boolean presetMode;
    private final List<BlockPos> presetRoomOrder = new ArrayList<>();
    private int presetCurrentIndex;
    private String lastPresetTransitionReason = "NONE";
    private String lastPresetRejectedControllerSummary = "NONE";
    private boolean exitPortalActive;
    private BlockPos exitPortalPos;
    private String exitPortalSource = "NONE";
    private String exitPortalReason = "NONE";
    private String exitPortalReadinessDetails = "NONE";
    private boolean hubEnabled;
    private BlockPos hubSpawnAnchorPos;
    private BlockPos hubReturnAnchorPos;
    private BlockPos hubGateAnchorPos;
    private BlockPos hubSafeZoneCornerA;
    private BlockPos hubSafeZoneCornerB;
    private String hubGateState = "CLOSED";
    private String lastHubTransitionReason = "NONE";
    private int preparedFloorNumber = 1;
    private boolean preparedFloorReady;
    private boolean presetArmedFromHub;
    private int hubPreparedPresetRooms;
    private String archivedPresetOrderSummary = "NONE";
    private int retryFloorNumber = 1;
    private boolean retryPreservesModifiers;
    private boolean preparedRoomPressureDispatchEligible;
    private String preparedRoomPressureId = "";
    private boolean preparedKinDispatchEligible;
    private String preparedKinGroup = "";
    private long preparedKinSeed;
    private int preparedKinCatalogVersion;
    private List<String> preparedKinCandidateIds = List.of();
    private List<String> preparedKinRejectionReasons = List.of();
    private boolean preparedBossDispatchEligible;
    private boolean preparedBossSlotAvailable;
    private boolean preparedBossMilestone;
    private int preparedBossCatalogVersion;
    private String preparedBossAffinityKinGroup = "";
    private String preparedBossAffinityTheme = "";
    private List<String> preparedBossCandidateIds = List.of();
    private List<String> preparedBossMatchingCandidateIds = List.of();
    private String preparedBossSelectedId = "";
    private String preparedBossResolutionReason = "NONE";

    @Nullable
    public DungeonInstanceAllocationData.InstanceKey getInstanceKey() {
        return instanceKey;
    }

    public boolean canBindInstance(DungeonInstanceAllocationData.InstanceKey replacement) {
        return replacement != null && (instanceKey == null || instanceKey.equals(replacement)
            || !activeRun || runCompleted);
    }

    public boolean bindInstance(DungeonInstanceAllocationData.InstanceKey replacement) {
        if (!canBindInstance(replacement)) {
            return false;
        }
        instanceKey = replacement;
        return true;
    }

    public boolean matchesInstance(@Nullable DungeonInstanceAllocationData.InstanceKey expected) {
        return expected != null && expected.equals(instanceKey);
    }

    public void startRunAtFloor(int requestedFloor, String sessionId, long seed) {
        int floorToStart = Math.max(1, requestedFloor);
        boolean preserveRetryCheckpoint = retryPreservesModifiers
            && retryFloorNumber == floorToStart;
        List<DungeonModifierDefinition> preservedRetryModifiers = preserveRetryCheckpoint
            ? List.copyOf(retryModifiers)
            : List.of();
        Set<String> preservedRetryModifierIds = preserveRetryCheckpoint
            ? Set.copyOf(retryModifierIds)
            : Set.of();
        activeRun = true;
        runSessionId = sanitize(sessionId);
        beginRunAtFloor(floorToStart);
        runSeed = seed;
        failed = false;
        failureReason = "NONE";
        clearModifiersInternal("NONE");
        retryModifiers.clear();
        retryModifiers.addAll(preservedRetryModifiers);
        retryModifierIds.clear();
        retryModifierIds.addAll(preservedRetryModifierIds);
        onRunStarted();
        if (preserveRetryCheckpoint) {
            retryFloorNumber = floorToStart;
            retryPreservesModifiers = true;
            restoreRetryModifiers();
        }
    }

    public void endRun() {
        activeRun = false;
        endRunProgress();
        failed = false;
        failureReason = "NONE";
        clearModifiersInternal("CLEARED");
    }

    public void resetRun() {
        activeRun = false;
        runSessionId = "NONE";
        resetRunProgress();
        runSeed = 0L;
        failed = false;
        failureReason = "NONE";
        clearModifiersInternal("CLEARED");
    }

    public boolean isActiveRun() {
        return activeRun;
    }

    public String getRunSessionId() {
        return sanitize(runSessionId);
    }

    public long getRunSeed() {
        return runSeed;
    }

    public boolean isFailed() {
        return failed;
    }

    public String getFailureReason() {
        return sanitize(failureReason);
    }

    public void clearFailure() {
        failed = false;
        failureReason = "NONE";
    }

    public void markFailed(String reason) {
        failed = true;
        failureReason = reason == null || reason.isBlank() ? "UNKNOWN_ROOM_FAILURE" : reason;
    }

    public void captureRoomCompletionFailureReason(String roomCompletionReason) {
        if (failed && "NONE".equals(failureReason) && roomCompletionReason != null) {
            failureReason = roomCompletionReason;
        }
    }

    public boolean isDebugRoomActivationOverride() {
        return debugRoomActivationOverride;
    }

    public void setDebugRoomActivationOverride(boolean enabled) {
        debugRoomActivationOverride = enabled;
    }

    public boolean setForcedModifier(String modifierId) {
        DungeonModifierDefinition modifier = DungeonModifierDefinition.byId(modifierId);
        DungeonModifierCatalog.Entry entry = DungeonModifierCatalog.byId(modifierId);
        if (modifier == null || entry == null || !entry.isRollable()) {
            return false;
        }
        activeModifiers.clear();
        activeModifiers.add(modifier);
        activeModifierIds.clear();
        activeModifierIds.add(modifier.getId());
        modifierReason = "FORCED";
        return true;
    }

    public void applyPreparedModifierPlan(PreparedFloorModifierPlan plan) {
        activeModifiers.clear();
        activeModifierIds.clear();
        clearPreparedKinState();
        clearPreparedBossState();
        clearPreparedRoomPressureState();
        if (plan == null) {
            modifierReason = "NONE";
            return;
        }
        preparedKinDispatchEligible = plan.isDispatchEligible();
        preparedKinGroup = normalizeOptional(plan.kinGroup());
        preparedKinSeed = plan.kinSeed();
        preparedKinCatalogVersion = Math.max(0, plan.kinCatalogVersion());
        preparedKinCandidateIds = List.copyOf(plan.kinCandidateIds());
        preparedKinRejectionReasons = List.copyOf(plan.kinRejectionReasons());
        preparedBossDispatchEligible = plan.isDispatchEligible();
        preparedBossSlotAvailable = plan.bossSlotAvailable();
        preparedBossMilestone = plan.bossMilestone();
        preparedBossCatalogVersion = Math.max(0, plan.bossCatalogVersion());
        preparedBossAffinityKinGroup = sanitize(plan.bossAffinityKinGroup());
        preparedBossAffinityTheme = sanitize(plan.bossAffinityTheme());
        preparedBossCandidateIds = List.copyOf(plan.bossCandidateIds());
        preparedBossMatchingCandidateIds = List.copyOf(plan.bossMatchingCandidateIds());
        preparedBossSelectedId = sanitize(plan.selectedBossId());
        preparedBossResolutionReason = sanitize(plan.bossResolutionReason());
        preparedRoomPressureDispatchEligible = plan.isDispatchEligible();
        preparedRoomPressureId = normalizeOptional(plan.roomPressureId());
        if (!plan.isDispatchEligible()) {
            modifierReason = "PLAN_REJECTED_" + plan.validationReason();
            return;
        }
        activeModifierIds.addAll(plan.hostileModifierIds());
        if (!plan.boonModifierId().isBlank()) activeModifierIds.add(plan.boonModifierId());
        activeModifiers.addAll(plan.resolveHostileModifiers());
        modifierReason = "PREPARED_" + plan.reason();
    }

    public void clearModifiers(String reason) {
        clearModifiersInternal(reason);
    }

    private void clearModifiersInternal(String reason) {
        activeModifiers.clear();
        activeModifierIds.clear();
        modifierReason = reason == null || reason.isBlank() ? "NONE" : reason;
    }

    private void clearPreparedKinState() {
        preparedKinDispatchEligible = false;
        preparedKinGroup = "";
        preparedKinSeed = 0L;
        preparedKinCatalogVersion = 0;
        preparedKinCandidateIds = List.of();
        preparedKinRejectionReasons = List.of();
    }

    private void clearPreparedRoomPressureState() {
        preparedRoomPressureDispatchEligible = false;
        preparedRoomPressureId = "";
    }

    private void clearPreparedBossState() {
        preparedBossDispatchEligible = false;
        preparedBossSlotAvailable = false;
        preparedBossMilestone = false;
        preparedBossCatalogVersion = 0;
        preparedBossAffinityKinGroup = "";
        preparedBossAffinityTheme = "";
        preparedBossCandidateIds = List.of();
        preparedBossMatchingCandidateIds = List.of();
        preparedBossSelectedId = "";
        preparedBossResolutionReason = "NONE";
    }

    public PreparedKinState getPreparedKinState() {
        return new PreparedKinState(
            preparedKinDispatchEligible,
            preparedKinGroup,
            preparedKinSeed,
            preparedKinCatalogVersion,
            preparedKinCandidateIds,
            preparedKinRejectionReasons
        );
    }

    public PreparedBossState getPreparedBossState() {
        return new PreparedBossState(
            preparedBossDispatchEligible,
            preparedBossSlotAvailable,
            preparedBossMilestone,
            preparedBossCatalogVersion,
            preparedBossAffinityKinGroup,
            preparedBossAffinityTheme,
            preparedBossCandidateIds,
            preparedBossMatchingCandidateIds,
            preparedBossSelectedId,
            preparedBossResolutionReason
        );
    }

    public String getPreparedRoomPressureId() {
        return preparedRoomPressureId;
    }

    public boolean isPreparedRoomPressureDispatchEligible() {
        return preparedRoomPressureDispatchEligible
            && !preparedRoomPressureId.isBlank()
            && DungeonRoomPressureCatalog.isKnown(preparedRoomPressureId);
    }

    public Set<String> getActiveRoomPressureIds() {
        return isPreparedRoomPressureDispatchEligible()
            && DungeonRoomPressureCatalog.isRollable(preparedRoomPressureId)
            ? Set.of(preparedRoomPressureId)
            : Set.of();
    }

    public Set<DungeonModifierDefinition> getActiveModifiers() {
        return runtimeActiveModifiers();
    }

    public Set<String> getActiveModifierIds() {
        return activeModifierIds.stream()
            .filter(DungeonModifierCatalog::isRollable)
            .collect(Collectors.toUnmodifiableSet());
    }

    public String getModifierReason() {
        return sanitize(modifierReason);
    }

    public int getModifierCount() {
        return getActiveModifierIds().size();
    }

    public String getModifierIdsSummary() {
        Set<String> runtimeIds = getActiveModifierIds();
        return runtimeIds.isEmpty() ? "NONE" : runtimeIds.stream().sorted().collect(Collectors.joining(", "));
    }

    public String getModifierDisplayNamesSummary() {
        Set<DungeonModifierDefinition> runtimeModifiers = runtimeActiveModifiers();
        if (runtimeModifiers.isEmpty()) {
            return "NONE";
        }
        return runtimeModifiers.stream().map(DungeonModifierDefinition::getDisplayName).sorted().collect(Collectors.joining(", "));
    }

    public String getModifierCategoriesSummary() {
        Set<DungeonModifierDefinition> runtimeModifiers = runtimeActiveModifiers();
        if (runtimeModifiers.isEmpty()) {
            return "NONE";
        }
        return runtimeModifiers.stream().map(modifier -> modifier.getCategory().name()).sorted().collect(Collectors.joining(", "));
    }

    public double getModifierBudgetMultiplier() {
        double total = 1.0D;
        for (DungeonModifierDefinition modifier : runtimeActiveModifiers()) {
            total *= modifier.getBudgetMultiplier();
        }
        return total;
    }

    public double getModifierMobCountMultiplier() {
        double total = 1.0D;
        for (DungeonModifierDefinition modifier : runtimeActiveModifiers()) {
            total *= modifier.getMobCountMultiplier();
        }
        return total;
    }

    public double getModifierHealthMultiplier() {
        double total = 1.0D;
        for (DungeonModifierDefinition modifier : runtimeActiveModifiers()) {
            total *= modifier.getHealthMultiplier();
        }
        return total;
    }

    public int getModifierMaxAliveBonus() {
        int total = 0;
        for (DungeonModifierDefinition modifier : runtimeActiveModifiers()) {
            total += modifier.getMaxAliveBonus();
        }
        return total;
    }

    public String getModifierPerformanceCostSummary() {
        Set<DungeonModifierDefinition> runtimeModifiers = runtimeActiveModifiers();
        if (runtimeModifiers.isEmpty()) {
            return "NONE";
        }
        return runtimeModifiers.stream().map(modifier -> modifier.getPerformanceCost().name().toLowerCase()).sorted().collect(Collectors.joining(", "));
    }

    public void captureRetryModifiersFromActive() {
        retryModifiers.clear();
        retryModifiers.addAll(runtimeActiveModifiers());
        retryModifierIds.clear();
        retryModifierIds.addAll(getActiveModifierIds());
    }

    public boolean hasRetryModifiers() {
        return !retryModifierIds.isEmpty();
    }

    public boolean isRetryReplayForFloor(int requestedFloor) {
        return retryPreservesModifiers && retryFloorNumber == Math.max(1, requestedFloor);
    }

    public void clearRetryCheckpoint() {
        retryModifiers.clear();
        retryModifierIds.clear();
        retryPreservesModifiers = false;
        retryFloorNumber = Math.max(1, floorNumber);
    }

    public void restoreRetryModifiers() {
        activeModifiers.clear();
        activeModifiers.addAll(retryModifiers.stream()
            .filter(modifier -> DungeonModifierCatalog.isRollable(modifier.getId()))
            .toList());
        activeModifierIds.clear();
        activeModifierIds.addAll(retryModifierIds.stream()
            .filter(DungeonModifierCatalog::isRollable)
            .toList());
        modifierReason = "RETRY_PRESERVED";
    }

    public String getRetryModifierIdsSummary() {
        return summarizeModifiers(retryModifiers.stream()
            .filter(modifier -> DungeonModifierCatalog.isRollable(modifier.getId()))
            .collect(Collectors.toCollection(LinkedHashSet::new)), true);
    }

    private Set<DungeonModifierDefinition> runtimeActiveModifiers() {
        return activeModifiers.stream()
            .filter(modifier -> DungeonModifierCatalog.isRollable(modifier.getId()))
            .collect(Collectors.toUnmodifiableSet());
    }

    public void beginRunAtFloor(int requestedFloor) {
        floorNumber = Math.max(1, requestedFloor);
        clearPreparedRoomPressureState();
        clearPreparedKinState();
        clearPreparedBossState();
        clearActiveRooms();
        completedRoomControllerPositions.clear();
        runCompleted = false;
        targetRoomCount = DEFAULT_TARGET_ROOM_COUNT;
        completionReason = "NONE";
        nextFloorEligible = false;
        nextFloorNumber = floorNumber + 1;
        clearPresetMode();
    }

    public void endRunProgress() {
        clearPreparedRoomPressureState();
        clearPreparedKinState();
        clearPreparedBossState();
        clearActiveRooms();
        completedRoomControllerPositions.clear();
        runCompleted = false;
        completionReason = "NONE";
        targetRoomCount = DEFAULT_TARGET_ROOM_COUNT;
        nextFloorEligible = false;
        nextFloorNumber = 1;
        clearPresetMode();
    }

    public void resetRunProgress() {
        floorNumber = 1;
        endRunProgress();
    }

    public void clearFloorProgressForAdvance() {
        clearPreparedRoomPressureState();
        clearPreparedKinState();
        clearPreparedBossState();
        clearActiveRooms();
        completedRoomControllerPositions.clear();
        runCompleted = false;
        completionReason = "NONE";
        nextFloorEligible = false;
        nextFloorNumber = floorNumber + 1;
    }

    public void advanceToNextFloor() {
        floorNumber = nextFloorNumber;
        clearFloorProgressForAdvance();
    }

    public void prepareFloor(int requestedFloor) {
        floorNumber = Math.max(1, requestedFloor);
        clearActiveRooms();
        completedRoomControllerPositions.clear();
        clearPresetMode();
        runCompleted = false;
        completionReason = "NONE";
        nextFloorEligible = false;
        nextFloorNumber = floorNumber + 1;
    }

    public void markRunCompleted(String reason) {
        runCompleted = true;
        completionReason = reason == null || reason.isBlank() ? "TARGET_ROOMS_COMPLETED" : reason;
        nextFloorEligible = true;
        nextFloorNumber = floorNumber + 1;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public void setFloorNumber(int floorNumber) {
        this.floorNumber = Math.max(1, floorNumber);
    }

    public boolean isRunCompleted() {
        return runCompleted;
    }

    public String getCompletionReason() {
        return completionReason;
    }

    public boolean isNextFloorEligible() {
        return nextFloorEligible;
    }

    public int getNextFloorNumber() {
        return nextFloorNumber;
    }

    public void setTargetRoomCount(int targetRoomCount) {
        this.targetRoomCount = Math.max(1, targetRoomCount);
    }

    public int getTargetRoomCount() {
        return targetRoomCount;
    }

    public int getCompletedRoomCount() {
        return completedRoomControllerPositions.size();
    }

    public int getRemainingRoomCount() {
        return Math.max(0, targetRoomCount - getCompletedRoomCount());
    }

    public boolean addCompletedRoom(@Nullable BlockPos controllerPos) {
        if (controllerPos == null) {
            return false;
        }
        return completedRoomControllerPositions.add(controllerPos.immutable());
    }

    public void clearCompletedRooms() {
        completedRoomControllerPositions.clear();
    }

    public boolean isRoomRegisteredCompleted(@Nullable BlockPos pos) {
        return pos != null && completedRoomControllerPositions.contains(pos.immutable());
    }

    public Set<BlockPos> getCompletedRoomControllerPositions() {
        return Set.copyOf(completedRoomControllerPositions);
    }

    public String getCompletedRoomPositionsSummary() {
        return summarizePositions(completedRoomControllerPositions);
    }

    public String getRunProgressSummary() {
        return getCompletedRoomCount() + " / " + getTargetRoomCount() + " completed";
    }

    public void clearActiveRooms() {
        activeRoomControllerPositions.clear();
        activeRoomControllerPos = null;
    }

    public void registerActiveRoom(@Nullable BlockPos controllerPos) {
        if (controllerPos == null) {
            return;
        }
        BlockPos immutablePos = controllerPos.immutable();
        activeRoomControllerPositions.add(immutablePos);
        activeRoomControllerPos = immutablePos;
    }

    public void removeActiveRoom(@Nullable BlockPos controllerPos) {
        if (controllerPos == null) {
            return;
        }
        activeRoomControllerPositions.remove(controllerPos.immutable());
        refreshActiveRoomControllerPos();
    }

    public boolean hasActiveRooms() {
        return !activeRoomControllerPositions.isEmpty();
    }

    public int getActiveRoomCount() {
        return activeRoomControllerPositions.size();
    }

    public String getActiveRoomControllerPosString() {
        return formatPos(activeRoomControllerPos);
    }

    public String getActiveRoomPositionsSummary() {
        return summarizePositions(activeRoomControllerPositions);
    }

    public boolean isRoomRegisteredActive(@Nullable BlockPos pos) {
        return pos != null && activeRoomControllerPositions.contains(pos.immutable());
    }

    public Set<BlockPos> getActiveRoomControllerPositions() {
        return Set.copyOf(activeRoomControllerPositions);
    }

    public boolean isPresetMode() {
        return presetMode;
    }

    public int getPresetRoomCount() {
        return presetRoomOrder.size();
    }

    public int getPresetCurrentIndex() {
        return presetCurrentIndex;
    }

    public List<BlockPos> getPresetRoomOrder() {
        return List.copyOf(presetRoomOrder);
    }

    public BlockPos getPresetExpectedControllerPos() {
        if (!presetMode || presetCurrentIndex < 0 || presetCurrentIndex >= presetRoomOrder.size()) {
            return null;
        }
        return presetRoomOrder.get(presetCurrentIndex);
    }

    public int getPresetCompletedCount() {
        if (!presetMode) {
            return 0;
        }
        return Math.max(0, Math.min(presetCurrentIndex, presetRoomOrder.size()));
    }

    public int getPresetRemainingCount() {
        if (!presetMode) {
            return 0;
        }
        return Math.max(0, presetRoomOrder.size() - getPresetCompletedCount());
    }

    public String getLastPresetTransitionReason() {
        return sanitize(lastPresetTransitionReason);
    }

    public void setLastPresetTransitionReason(String reason) {
        lastPresetTransitionReason = sanitize(reason);
    }

    public String getLastPresetRejectedControllerSummary() {
        return sanitize(lastPresetRejectedControllerSummary);
    }

    public boolean isPresetRoomInOrder(@Nullable BlockPos controllerPos) {
        return controllerPos != null && presetRoomOrder.contains(controllerPos.immutable());
    }

    public boolean isPresetRoomCompletedByOrder(@Nullable BlockPos controllerPos) {
        if (!presetMode || controllerPos == null) {
            return false;
        }
        int index = presetRoomOrder.indexOf(controllerPos.immutable());
        return index >= 0 && index < getPresetCompletedCount();
    }

    public String getPresetControllerStatus(@Nullable BlockPos controllerPos) {
        if (!presetMode) {
            return "PRESET_INACTIVE";
        }
        if (controllerPos == null) {
            return "NULL_CONTROLLER";
        }
        BlockPos immutablePos = controllerPos.immutable();
        int index = presetRoomOrder.indexOf(immutablePos);
        if (index < 0) {
            return "NOT_IN_PRESET_ORDER";
        }
        if (index < getPresetCompletedCount()) {
            return "COMPLETED";
        }
        BlockPos expected = getPresetExpectedControllerPos();
        if (expected != null && expected.equals(immutablePos)) {
            return "EXPECTED_NEXT";
        }
        return "PENDING_LOCKED";
    }

    public String getPresetActivationBlockReason(@Nullable BlockPos controllerPos) {
        if (!presetMode) {
            return "NONE";
        }
        if (controllerPos == null) {
            lastPresetRejectedControllerSummary = "NONE:NULL_CONTROLLER";
            return "PRESET_CONTROLLER_POS_MISSING";
        }

        BlockPos immutablePos = controllerPos.immutable();

        if (!isPresetRoomInOrder(immutablePos)) {
            recordPresetRejection(immutablePos, "NOT_IN_PRESET_ORDER");
            return "PRESET_CONTROLLER_NOT_IN_ORDER";
        }
        if (isPresetRoomCompletedByOrder(immutablePos)) {
            recordPresetRejection(immutablePos, "ALREADY_COMPLETED");
            return "PRESET_CONTROLLER_ALREADY_COMPLETED";
        }

        BlockPos expected = getPresetExpectedControllerPos();
        if (expected == null) {
            recordPresetRejection(immutablePos, "NO_EXPECTED_CONTROLLER");
            return "PRESET_NO_EXPECTED_CONTROLLER";
        }
        if (!expected.equals(immutablePos)) {
            recordPresetRejection(immutablePos, "EXPECTED_" + formatPresetPos(expected));
            return "PRESET_UNEXPECTED_CONTROLLER_EXPECTED_" + formatPresetPos(expected);
        }

        return "NONE";
    }

    public String getPresetOrderDebugSummary() {
        if (!presetMode || presetRoomOrder.isEmpty()) {
            return "NONE";
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < presetRoomOrder.size(); i++) {
            BlockPos pos = presetRoomOrder.get(i);
            String marker = i < getPresetCompletedCount() ? "DONE" : (pos.equals(getPresetExpectedControllerPos()) ? "NEXT" : "PENDING");
            parts.add((i + 1) + ":" + formatPresetPos(pos) + ":" + marker);
        }
        return String.join(" | ", parts);
    }

    public void recordPresetRejection(@Nullable BlockPos controllerPos, String reason) {
        lastPresetRejectedControllerSummary = formatPresetPos(controllerPos) + ":" + (reason == null || reason.isBlank() ? "UNKNOWN" : reason);
    }

    public void advancePresetIndex() {
        presetCurrentIndex = Math.min(presetRoomOrder.size(), presetCurrentIndex + 1);
        lastPresetTransitionReason = "ADVANCED_TO_PRESET_INDEX_" + presetCurrentIndex;
    }

    public boolean skipExpectedPresetRoom(@Nullable BlockPos controllerPos, String reason) {
        if (!presetMode || controllerPos == null || isRunCompleted()) {
            return false;
        }
        BlockPos immutablePos = controllerPos.immutable();
        int index = presetRoomOrder.indexOf(immutablePos);
        if (index < 0 || index != presetCurrentIndex
            || completedRoomControllerPositions.contains(immutablePos)) {
            recordPresetRejection(immutablePos, "SKIP_NOT_EXPECTED");
            return false;
        }
        completedRoomControllerPositions.add(immutablePos);
        presetCurrentIndex = Math.min(presetRoomOrder.size(), presetCurrentIndex + 1);
        lastPresetTransitionReason = "SKIPPED_GENERATED_ROOM_"
            + sanitize(reason == null || reason.isBlank() ? "RECOVERY" : reason);
        return true;
    }

    public boolean undoSkippedPresetRoom(@Nullable BlockPos controllerPos) {
        if (!presetMode || controllerPos == null) {
            return false;
        }
        BlockPos immutablePos = controllerPos.immutable();
        int index = presetRoomOrder.indexOf(immutablePos);
        if (index < 0 || presetCurrentIndex != index + 1
            || !completedRoomControllerPositions.remove(immutablePos)) {
            return false;
        }
        presetCurrentIndex = index;
        lastPresetTransitionReason = "FALLBACK_SKIP_ROLLED_BACK";
        return true;
    }

    public boolean isPresetFloorComplete() {
        return presetMode && presetRoomOrder.size() >= targetRoomCount && presetCurrentIndex >= targetRoomCount;
    }

    public void enableRollingPresetMode(List<BlockPos> controllerOrder, int requestedRoomCount) {
        enablePresetMode(controllerOrder);
        if (presetMode) {
            targetRoomCount = Math.max(presetRoomOrder.size(), requestedRoomCount);
            lastPresetTransitionReason = "ROLLING_PRESET_MODE_ENABLED";
        }
    }

    public boolean appendRollingPresetOrder(List<BlockPos> appendedControllers) {
        if (!presetMode || appendedControllers == null || appendedControllers.isEmpty()) return false;
        LinkedHashSet<BlockPos> merged = new LinkedHashSet<>(presetRoomOrder);
        for (BlockPos pos : appendedControllers) {
            if (pos == null || !merged.add(pos.immutable())) return false;
        }
        presetRoomOrder.clear();
        presetRoomOrder.addAll(merged);
        lastPresetTransitionReason = "ROLLING_PRESET_ORDER_APPENDED_" + appendedControllers.size();
        return true;
    }
    public void enablePresetMode(List<BlockPos> controllerOrder) {
        presetMode = false;
        presetRoomOrder.clear();
        presetCurrentIndex = 0;
        lastPresetTransitionReason = "PRESET_ORDER_EMPTY";
        lastPresetRejectedControllerSummary = "NONE";

        if (controllerOrder == null || controllerOrder.isEmpty()) {
            targetRoomCount = DEFAULT_TARGET_ROOM_COUNT;
            return;
        }

        LinkedHashSet<BlockPos> uniqueOrder = new LinkedHashSet<>();
        for (BlockPos pos : controllerOrder) {
            if (pos != null) {
                uniqueOrder.add(pos.immutable());
            }
        }

        presetRoomOrder.addAll(uniqueOrder);
        presetMode = !presetRoomOrder.isEmpty();
        presetCurrentIndex = 0;
        targetRoomCount = Math.max(1, presetRoomOrder.size());
        clearActiveRooms();
        completedRoomControllerPositions.clear();
        runCompleted = false;
        completionReason = "NONE";
        nextFloorEligible = false;
        nextFloorNumber = floorNumber + 1;
        lastPresetTransitionReason = presetMode ? "PRESET_MODE_ENABLED" : "PRESET_ORDER_EMPTY";
        lastPresetRejectedControllerSummary = "NONE";
    }

    public boolean replacePendingPresetOrder(List<BlockPos> controllerOrder) {
        if (!presetMode || controllerOrder == null || controllerOrder.size() != presetRoomOrder.size()) {
            return false;
        }
        LinkedHashSet<BlockPos> uniqueOrder = new LinkedHashSet<>();
        for (BlockPos pos : controllerOrder) {
            if (pos == null) {
                return false;
            }
            uniqueOrder.add(pos.immutable());
        }
        if (uniqueOrder.size() != presetRoomOrder.size()) {
            return false;
        }
        List<BlockPos> replacement = new ArrayList<>(uniqueOrder);
        int completedPrefixEnd = Math.min(presetCurrentIndex, presetRoomOrder.size());
        for (int i = 0; i < completedPrefixEnd; i++) {
            if (!presetRoomOrder.get(i).equals(replacement.get(i))) {
                return false;
            }
        }
        presetRoomOrder.clear();
        presetRoomOrder.addAll(replacement);
        lastPresetTransitionReason = "PLAYER_DIRECTED_MULTIWAY_TAIL_UPDATED";
        return true;
    }
    public void clearPresetMode() {
        presetMode = false;
        presetRoomOrder.clear();
        presetCurrentIndex = 0;
        lastPresetTransitionReason = "PRESET_MODE_CLEARED";
        lastPresetRejectedControllerSummary = "NONE";
    }

    public void onRunStarted() {
        preparedFloorNumber = floorNumber;
        preparedFloorReady = false;
        retryFloorNumber = floorNumber;
        retryPreservesModifiers = false;
        presetArmedFromHub = false;
        hubPreparedPresetRooms = 0;
        archivedPresetOrderSummary = "NONE";
        hubGateState = "CLOSED";
        lastHubTransitionReason = "RUN_STARTED";
    }

    public void onFloorFailed(boolean preserveRetryModifiers) {
        retryFloorNumber = floorNumber;
        retryPreservesModifiers = preserveRetryModifiers;
        preparedFloorNumber = retryFloorNumber;
        preparedFloorReady = false;
        hubGateState = "CLOSED";
        lastHubTransitionReason = "FLOOR_FAILED";
    }

    public void onAdvancedToNextFloor() {
        preparedFloorNumber = floorNumber;
        preparedFloorReady = false;
        hubGateState = "ACTIVE";
        lastHubTransitionReason = "ADVANCED_TO_NEXT_FLOOR";
    }

    public boolean isExitPortalActive() {
        return exitPortalActive;
    }

    public @Nullable BlockPos getExitPortalPos() {
        return exitPortalPos == null ? null : exitPortalPos.immutable();
    }

    public String getExitPortalPosString() {
        return formatPos(exitPortalPos);
    }

    public String getExitPortalSource() {
        return sanitize(exitPortalSource);
    }

    public String getExitPortalReason() {
        return sanitize(exitPortalReason);
    }

    public String getExitPortalReadinessDetails() {
        return sanitize(exitPortalReadinessDetails);
    }

    public void setExitPortalState(boolean active, @Nullable BlockPos pos, String source, String reason, String details) {
        exitPortalActive = active;
        exitPortalPos = pos == null ? null : pos.immutable();
        exitPortalSource = sanitize(source);
        exitPortalReason = sanitize(reason);
        exitPortalReadinessDetails = sanitize(details);
    }

    public boolean isHubEnabled() {
        return hubEnabled;
    }

    public @Nullable BlockPos getHubSpawnAnchorPos() {
        return hubSpawnAnchorPos == null ? null : hubSpawnAnchorPos.immutable();
    }

    public @Nullable BlockPos getHubReturnAnchorPos() {
        return hubReturnAnchorPos == null ? null : hubReturnAnchorPos.immutable();
    }

    public @Nullable BlockPos getHubGateAnchorPos() {
        return hubGateAnchorPos == null ? null : hubGateAnchorPos.immutable();
    }

    public @Nullable BlockPos getHubSafeZoneCornerA() {
        return hubSafeZoneCornerA == null ? null : hubSafeZoneCornerA.immutable();
    }

    public @Nullable BlockPos getHubSafeZoneCornerB() {
        return hubSafeZoneCornerB == null ? null : hubSafeZoneCornerB.immutable();
    }

    boolean hasHubConfiguration() {
        return hubSpawnAnchorPos != null || hubReturnAnchorPos != null || hubGateAnchorPos != null
            || (hubSafeZoneCornerA != null && hubSafeZoneCornerB != null);
    }

    void inheritHubConfiguration(DungeonRunState source) {
        if (source == null || !source.hasHubConfiguration()) {
            return;
        }
        if (hubSpawnAnchorPos == null) {
            hubSpawnAnchorPos = source.getHubSpawnAnchorPos();
        }
        if (hubReturnAnchorPos == null) {
            hubReturnAnchorPos = source.getHubReturnAnchorPos();
        }
        if (hubGateAnchorPos == null) {
            hubGateAnchorPos = source.getHubGateAnchorPos();
        }
        if (hubSafeZoneCornerA == null || hubSafeZoneCornerB == null) {
            hubSafeZoneCornerA = source.getHubSafeZoneCornerA();
            hubSafeZoneCornerB = source.getHubSafeZoneCornerB();
        }
        hubEnabled = hasHubConfiguration();
    }

    public String getHubGateState() {
        return sanitize(hubGateState);
    }

    public String getLastHubTransitionReason() {
        return sanitize(lastHubTransitionReason);
    }

    public int getPreparedFloorNumber() {
        return preparedFloorNumber;
    }

    public boolean isPreparedFloorReady() {
        return preparedFloorReady;
    }

    public boolean isPresetArmedFromHub() {
        return presetArmedFromHub;
    }

    public int getHubPreparedPresetRooms() {
        return hubPreparedPresetRooms;
    }

    public String getArchivedPresetOrderSummary() {
        return sanitize(archivedPresetOrderSummary);
    }

    public boolean isRetryPreservingModifiers() {
        return retryPreservesModifiers;
    }

    public int getRetryFloorNumber() {
        return retryFloorNumber;
    }

    public void setHubSpawnAnchor(@Nullable BlockPos pos) {
        hubEnabled = pos != null;
        hubSpawnAnchorPos = pos == null ? null : pos.immutable();
        if (hubReturnAnchorPos == null && pos != null) {
            hubReturnAnchorPos = pos.immutable();
        }
        lastHubTransitionReason = pos == null ? "HUB_SPAWN_CLEARED" : "HUB_SPAWN_SET";
    }

    public void setHubReturnAnchor(@Nullable BlockPos pos) {
        hubEnabled = pos != null || hubSpawnAnchorPos != null;
        hubReturnAnchorPos = pos == null ? null : pos.immutable();
        lastHubTransitionReason = pos == null ? "HUB_RETURN_CLEARED" : "HUB_RETURN_SET";
    }

    public void setHubGateAnchor(@Nullable BlockPos pos) {
        hubEnabled = pos != null || hubSpawnAnchorPos != null;
        hubGateAnchorPos = pos == null ? null : pos.immutable();
        lastHubTransitionReason = pos == null ? "HUB_GATE_CLEARED" : "HUB_GATE_SET";
    }

    public void setHubSafeZone(@Nullable BlockPos cornerA, @Nullable BlockPos cornerB) {
        hubEnabled = (cornerA != null && cornerB != null) || hubSpawnAnchorPos != null;
        hubSafeZoneCornerA = cornerA == null ? null : cornerA.immutable();
        hubSafeZoneCornerB = cornerB == null ? null : cornerB.immutable();
        lastHubTransitionReason = cornerA == null || cornerB == null ? "HUB_SAFE_ZONE_CLEARED" : "HUB_SAFE_ZONE_SET";
    }

    public boolean isPlayerInsideHubSafeZone(@Nullable BlockPos pos) {
        if (!hubEnabled || pos == null || hubSafeZoneCornerA == null || hubSafeZoneCornerB == null) {
            return false;
        }
        int minX = Math.min(hubSafeZoneCornerA.getX(), hubSafeZoneCornerB.getX());
        int minY = Math.min(hubSafeZoneCornerA.getY(), hubSafeZoneCornerB.getY());
        int minZ = Math.min(hubSafeZoneCornerA.getZ(), hubSafeZoneCornerB.getZ());
        int maxX = Math.max(hubSafeZoneCornerA.getX(), hubSafeZoneCornerB.getX());
        int maxY = Math.max(hubSafeZoneCornerA.getY(), hubSafeZoneCornerB.getY());
        int maxZ = Math.max(hubSafeZoneCornerA.getZ(), hubSafeZoneCornerB.getZ());
        return pos.getX() >= minX && pos.getX() <= maxX
            && pos.getY() >= minY && pos.getY() <= maxY
            && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public @Nullable BlockPos getHubReturnTarget() {
        BlockPos target = hubReturnAnchorPos != null ? hubReturnAnchorPos : hubSpawnAnchorPos;
        return target == null ? null : target.immutable();
    }

    public void markHubReturnBlocked() {
        lastHubTransitionReason = "HUB_RETURN_BLOCKED";
    }

    public void markReturnedToHub(String reason) {
        preparedFloorReady = false;
        hubGateState = "CLOSED";
        lastHubTransitionReason = reason == null || reason.isBlank() ? "RETURNED_TO_HUB" : reason;
    }

    public void prepareContinueToHub() {
        preparedFloorNumber = nextFloorNumber;
        preparedFloorReady = false;
        hubGateState = "CLOSED";
        presetArmedFromHub = false;
        hubPreparedPresetRooms = 0;
    }

    public void prepareFailedFloorReturnToHub() {
        preparedFloorNumber = retryFloorNumber;
        preparedFloorReady = false;
        hubGateState = "CLOSED";
        presetArmedFromHub = false;
        hubPreparedPresetRooms = 0;
    }

    public boolean prepareFloorFromHub(int requestedFloor, boolean failedRun) {
        int floorToPrepare = Math.max(1, requestedFloor);
        if (failedRun && floorToPrepare != retryFloorNumber) {
            return false;
        }
        preparedFloorNumber = floorToPrepare;
        floorNumber = floorToPrepare;
        clearActiveRooms();
        completedRoomControllerPositions.clear();
        clearPresetMode();
        runCompleted = false;
        completionReason = "NONE";
        nextFloorEligible = false;
        nextFloorNumber = floorNumber + 1;
        preparedFloorReady = true;
        hubGateState = "READY";
        lastHubTransitionReason = "FLOOR_PREPARED_FROM_HUB";
        return true;
    }

    public void markHubPresetPrepared(int rooms) {
        presetArmedFromHub = rooms > 0;
        hubPreparedPresetRooms = Math.max(0, rooms);
        archivedPresetOrderSummary = "NONE";
        if (rooms > 0) {
            lastHubTransitionReason = "HUB_PRESET_PREPARED";
        }
    }

    public void clearHubPreparedPresetState() {
        presetArmedFromHub = false;
        hubPreparedPresetRooms = 0;
    }

    public void archiveAndClearPresetState(String reason) {
        if (presetMode) {
            archivedPresetOrderSummary = getPresetOrderDebugSummary();
        } else if (archivedPresetOrderSummary == null || archivedPresetOrderSummary.isBlank()) {
            archivedPresetOrderSummary = "NONE";
        }
        clearPresetMode();
        clearHubPreparedPresetState();
        if (reason != null && !reason.isBlank()) {
            lastPresetTransitionReason = reason;
        }
    }

    public void setHubGateState(String state, String reason) {
        hubGateState = state == null || state.isBlank() ? "CLOSED" : state;
        lastHubTransitionReason = reason == null || reason.isBlank() ? "HUB_GATE_STATE_CHANGED" : reason;
    }

    public boolean normalizeForReload() {
        if (!hasActiveRooms()) {
            return false;
        }
        clearActiveRooms();
        return true;
    }

    public void load(CompoundTag tag) {
        instanceKey = tag.contains("DungeonInstance", Tag.TAG_COMPOUND)
            ? DungeonInstanceAllocationData.InstanceKey.loadOptional(tag.getCompound("DungeonInstance"))
            : null;
        activeRun = tag.getBoolean("ActiveRun");
        runSessionId = tag.contains("RunSessionId") ? tag.getString("RunSessionId") : "NONE";
        runSeed = tag.getLong("RunSeed");
        failed = tag.getBoolean("Failed");
        failureReason = tag.contains("FailureReason") ? tag.getString("FailureReason") : "NONE";
        debugRoomActivationOverride = tag.getBoolean("DebugRoomActivationOverride");
        modifierReason = tag.contains("ModifierReason") ? tag.getString("ModifierReason") : "NONE";
        activeModifiers.clear();
        activeModifiers.addAll(loadModifiers(tag.getList("ActiveModifiers", Tag.TAG_STRING)));
        activeModifierIds.clear();
        if (tag.contains("ActiveModifierIds", Tag.TAG_LIST)) {
            activeModifierIds.addAll(loadStrings(tag.getList("ActiveModifierIds", Tag.TAG_STRING)));
        } else {
            activeModifiers.forEach(modifier -> activeModifierIds.add(modifier.getId()));
        }
        retryModifiers.clear();
        retryModifiers.addAll(loadModifiers(tag.getList("RetryModifiers", Tag.TAG_STRING)));
        retryModifierIds.clear();
        if (tag.contains("RetryModifierIds", Tag.TAG_LIST)) {
            retryModifierIds.addAll(loadStrings(tag.getList("RetryModifierIds", Tag.TAG_STRING)));
        } else {
            retryModifiers.forEach(modifier -> retryModifierIds.add(modifier.getId()));
        }
        floorNumber = tag.contains("FloorNumber") ? tag.getInt("FloorNumber") : 1;
        runCompleted = tag.getBoolean("RunCompleted");
        targetRoomCount = tag.contains("TargetRoomCount") ? Math.max(1, tag.getInt("TargetRoomCount")) : DEFAULT_TARGET_ROOM_COUNT;
        completionReason = tag.contains("CompletionReason") ? tag.getString("CompletionReason") : "NONE";
        nextFloorEligible = tag.getBoolean("NextFloorEligible");
        nextFloorNumber = tag.contains("NextFloorNumber") ? Math.max(1, tag.getInt("NextFloorNumber")) : Math.max(1, floorNumber + 1);
        presetMode = tag.getBoolean("PresetMode");
        presetCurrentIndex = Math.max(0, tag.getInt("PresetCurrentIndex"));
        presetRoomOrder.clear();
        presetRoomOrder.addAll(loadOrderedPositions(tag.getList("PresetRoomOrder", Tag.TAG_COMPOUND)));
        lastPresetTransitionReason = tag.contains("LastPresetTransitionReason") ? tag.getString("LastPresetTransitionReason") : "NONE";
        lastPresetTransitionReason = sanitize(lastPresetTransitionReason);
        lastPresetRejectedControllerSummary = tag.contains("LastPresetRejectedControllerSummary") ? tag.getString("LastPresetRejectedControllerSummary") : "NONE";
        lastPresetRejectedControllerSummary = sanitize(lastPresetRejectedControllerSummary);
        loadActiveRooms(loadPositions(tag.getList("ActiveRoomControllerPositions", Tag.TAG_COMPOUND)),
            tag.contains("ActiveRoomControllerPos") ? BlockPos.of(tag.getLong("ActiveRoomControllerPos")) : null);
        completedRoomControllerPositions.clear();
        completedRoomControllerPositions.addAll(loadPositions(tag.getList("CompletedRoomControllerPositions", Tag.TAG_COMPOUND)));
        exitPortalActive = tag.getBoolean("ExitPortalActive");
        exitPortalSource = tag.contains("ExitPortalSource") ? tag.getString("ExitPortalSource") : "NONE";
        exitPortalReason = tag.contains("ExitPortalReason") ? tag.getString("ExitPortalReason") : "NONE";
        exitPortalReadinessDetails = tag.contains("ExitPortalReadinessDetails") ? tag.getString("ExitPortalReadinessDetails") : "NONE";
        exitPortalPos = tag.contains("ExitPortalPos") ? BlockPos.of(tag.getLong("ExitPortalPos")) : null;
        hubEnabled = tag.getBoolean("HubEnabled");
        hubGateState = tag.contains("HubGateState") ? tag.getString("HubGateState") : "CLOSED";
        lastHubTransitionReason = tag.contains("LastHubTransitionReason") ? tag.getString("LastHubTransitionReason") : "NONE";
        preparedFloorNumber = tag.contains("PreparedFloorNumber") ? Math.max(1, tag.getInt("PreparedFloorNumber")) : Math.max(1, floorNumber);
        preparedFloorReady = tag.getBoolean("PreparedFloorReady");
        presetArmedFromHub = tag.getBoolean("PresetArmedFromHub");
        hubPreparedPresetRooms = Math.max(0, tag.getInt("HubPreparedPresetRooms"));
        archivedPresetOrderSummary = tag.contains("ArchivedPresetOrderSummary") ? tag.getString("ArchivedPresetOrderSummary") : "NONE";
        retryFloorNumber = tag.contains("RetryFloorNumber") ? Math.max(1, tag.getInt("RetryFloorNumber")) : Math.max(1, floorNumber);
        retryPreservesModifiers = tag.getBoolean("RetryPreservesModifiers");
        preparedRoomPressureDispatchEligible = tag.getBoolean("PreparedRoomPressureDispatchEligible")
            && (!tag.contains("PreparedRoomPressureId")
                || tag.contains("PreparedRoomPressureId", Tag.TAG_STRING));
        preparedRoomPressureId = tag.contains("PreparedRoomPressureId", Tag.TAG_STRING)
            ? normalizeOptional(tag.getString("PreparedRoomPressureId")) : "";
        preparedKinDispatchEligible = tag.getBoolean("PreparedKinDispatchEligible");
        preparedKinGroup = tag.contains("PreparedKinGroup")
            ? normalizeOptional(tag.getString("PreparedKinGroup")) : "";
        preparedKinSeed = tag.contains("PreparedKinSeed") ? tag.getLong("PreparedKinSeed") : 0L;
        preparedKinCatalogVersion = tag.contains("PreparedKinCatalogVersion")
            ? Math.max(0, tag.getInt("PreparedKinCatalogVersion")) : 0;
        preparedKinCandidateIds = loadBoundedStrings(
            tag.getList("PreparedKinCandidateIds", Tag.TAG_STRING),
            PreparedFloorModifierPlan.MAX_KIN_CANDIDATES
        );
        preparedKinRejectionReasons = loadBoundedStrings(
            tag.getList("PreparedKinRejectionReasons", Tag.TAG_STRING),
            PreparedFloorModifierPlan.MAX_KIN_REJECTION_REASONS,
            false
        );
        preparedBossDispatchEligible = tag.getBoolean("PreparedBossDispatchEligible");
        preparedBossSlotAvailable = tag.getBoolean("PreparedBossSlotAvailable");
        preparedBossMilestone = tag.getBoolean("PreparedBossMilestone");
        preparedBossCatalogVersion = Math.max(0, tag.getInt("PreparedBossCatalogVersion"));
        preparedBossAffinityKinGroup = tag.contains("PreparedBossKinGroup") ? sanitize(tag.getString("PreparedBossKinGroup")) : "";
        preparedBossAffinityTheme = tag.contains("PreparedBossTheme") ? sanitize(tag.getString("PreparedBossTheme")) : "";
        preparedBossCandidateIds = loadBoundedStrings(
            tag.getList("PreparedBossCandidateIds", Tag.TAG_STRING),
            PreparedFloorModifierPlan.MAX_BOSS_CANDIDATES
        );
        preparedBossMatchingCandidateIds = loadBoundedStrings(
            tag.getList("PreparedBossMatchingCandidateIds", Tag.TAG_STRING),
            PreparedFloorModifierPlan.MAX_BOSS_CANDIDATES
        );
        preparedBossSelectedId = tag.contains("PreparedBossSelectedId") ? sanitize(tag.getString("PreparedBossSelectedId")) : "";
        preparedBossResolutionReason = tag.contains("PreparedBossResolutionReason")
            ? sanitize(tag.getString("PreparedBossResolutionReason")) : "NONE";
        hubSpawnAnchorPos = tag.contains("HubSpawnAnchorPos") ? BlockPos.of(tag.getLong("HubSpawnAnchorPos")) : null;
        hubReturnAnchorPos = tag.contains("HubReturnAnchorPos") ? BlockPos.of(tag.getLong("HubReturnAnchorPos")) : null;
        hubGateAnchorPos = tag.contains("HubGateAnchorPos") ? BlockPos.of(tag.getLong("HubGateAnchorPos")) : null;
        hubSafeZoneCornerA = tag.contains("HubSafeZoneCornerA") ? BlockPos.of(tag.getLong("HubSafeZoneCornerA")) : null;
        hubSafeZoneCornerB = tag.contains("HubSafeZoneCornerB") ? BlockPos.of(tag.getLong("HubSafeZoneCornerB")) : null;
    }

    public void save(CompoundTag tag) {
        if (instanceKey != null) {
            CompoundTag instanceTag = new CompoundTag();
            instanceKey.save(instanceTag);
            tag.put("DungeonInstance", instanceTag);
        }
        tag.putBoolean("ActiveRun", activeRun);
        tag.putString("RunSessionId", getRunSessionId());
        tag.putLong("RunSeed", runSeed);
        tag.putBoolean("Failed", failed);
        tag.putString("FailureReason", getFailureReason());
        tag.putBoolean("DebugRoomActivationOverride", debugRoomActivationOverride);
        tag.putString("ModifierReason", getModifierReason());
        tag.put("ActiveModifiers", saveModifiers(activeModifiers));
        tag.put("RetryModifiers", saveModifiers(retryModifiers));
        Set<String> legacyActiveIds = activeModifiers.stream()
            .map(DungeonModifierDefinition::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!activeModifierIds.equals(legacyActiveIds)) {
            tag.put("ActiveModifierIds", saveStrings(activeModifierIds));
        }
        Set<String> legacyRetryIds = retryModifiers.stream()
            .map(DungeonModifierDefinition::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!retryModifierIds.equals(legacyRetryIds)) {
            tag.put("RetryModifierIds", saveStrings(retryModifierIds));
        }
        tag.putInt("FloorNumber", floorNumber);
        tag.putBoolean("RunCompleted", runCompleted);
        tag.putInt("TargetRoomCount", targetRoomCount);
        tag.putString("CompletionReason", completionReason);
        tag.putBoolean("NextFloorEligible", nextFloorEligible);
        tag.putInt("NextFloorNumber", nextFloorNumber);
        tag.putBoolean("PresetMode", presetMode);
        tag.putInt("PresetCurrentIndex", presetCurrentIndex);
        tag.put("PresetRoomOrder", savePositions(presetRoomOrder));
        tag.putString("LastPresetTransitionReason", getLastPresetTransitionReason());
        tag.putString("LastPresetRejectedControllerSummary", getLastPresetRejectedControllerSummary());
        tag.put("ActiveRoomControllerPositions", savePositions(activeRoomControllerPositions));
        if (activeRoomControllerPos != null) {
            tag.putLong("ActiveRoomControllerPos", activeRoomControllerPos.asLong());
        }
        tag.put("CompletedRoomControllerPositions", savePositions(completedRoomControllerPositions));
        tag.putBoolean("ExitPortalActive", exitPortalActive);
        tag.putString("ExitPortalSource", getExitPortalSource());
        tag.putString("ExitPortalReason", getExitPortalReason());
        tag.putString("ExitPortalReadinessDetails", getExitPortalReadinessDetails());
        if (exitPortalPos != null) {
            tag.putLong("ExitPortalPos", exitPortalPos.asLong());
        }
        tag.putBoolean("HubEnabled", hubEnabled);
        tag.putString("HubGateState", getHubGateState());
        tag.putString("LastHubTransitionReason", getLastHubTransitionReason());
        tag.putInt("PreparedFloorNumber", preparedFloorNumber);
        tag.putBoolean("PreparedFloorReady", preparedFloorReady);
        tag.putBoolean("PresetArmedFromHub", presetArmedFromHub);
        tag.putInt("HubPreparedPresetRooms", hubPreparedPresetRooms);
        tag.putString("ArchivedPresetOrderSummary", getArchivedPresetOrderSummary());
        tag.putInt("RetryFloorNumber", retryFloorNumber);
        tag.putBoolean("RetryPreservesModifiers", retryPreservesModifiers);
        if (preparedRoomPressureDispatchEligible || !preparedRoomPressureId.isBlank()) {
            tag.putBoolean("PreparedRoomPressureDispatchEligible", preparedRoomPressureDispatchEligible);
            if (!preparedRoomPressureId.isBlank()) {
                tag.putString("PreparedRoomPressureId", preparedRoomPressureId);
            }
        }
        boolean hasPreparedKinState = preparedKinDispatchEligible
            || !preparedKinGroup.isBlank()
            || preparedKinSeed != 0L
            || preparedKinCatalogVersion > 0
            || !preparedKinCandidateIds.isEmpty()
            || !preparedKinRejectionReasons.isEmpty();
        if (hasPreparedKinState) {
            tag.putBoolean("PreparedKinDispatchEligible", preparedKinDispatchEligible);
            if (!preparedKinGroup.isBlank()) tag.putString("PreparedKinGroup", preparedKinGroup);
            tag.putLong("PreparedKinSeed", preparedKinSeed);
            tag.putInt("PreparedKinCatalogVersion", preparedKinCatalogVersion);
            if (!preparedKinCandidateIds.isEmpty()) {
                tag.put("PreparedKinCandidateIds", saveBoundedStrings(
                    preparedKinCandidateIds, PreparedFloorModifierPlan.MAX_KIN_CANDIDATES
                ));
            }
            if (!preparedKinRejectionReasons.isEmpty()) {
                tag.put("PreparedKinRejectionReasons", saveBoundedStrings(
                    preparedKinRejectionReasons, PreparedFloorModifierPlan.MAX_KIN_REJECTION_REASONS
                ));
            }
        }
        boolean hasPreparedBossState = preparedBossDispatchEligible
            || preparedBossSlotAvailable
            || preparedBossMilestone
            || preparedBossCatalogVersion > 0
            || !preparedBossAffinityKinGroup.isBlank()
            || !preparedBossAffinityTheme.isBlank()
            || !preparedBossCandidateIds.isEmpty()
            || !preparedBossMatchingCandidateIds.isEmpty()
            || !preparedBossSelectedId.isBlank()
            || !"NONE".equals(preparedBossResolutionReason);
        if (hasPreparedBossState) {
            tag.putBoolean("PreparedBossDispatchEligible", preparedBossDispatchEligible);
            tag.putBoolean("PreparedBossSlotAvailable", preparedBossSlotAvailable);
            tag.putBoolean("PreparedBossMilestone", preparedBossMilestone);
            tag.putInt("PreparedBossCatalogVersion", preparedBossCatalogVersion);
            tag.putString("PreparedBossKinGroup", sanitize(preparedBossAffinityKinGroup));
            tag.putString("PreparedBossTheme", sanitize(preparedBossAffinityTheme));
            if (!preparedBossCandidateIds.isEmpty()) {
                tag.put("PreparedBossCandidateIds", saveBoundedStrings(preparedBossCandidateIds));
            }
            if (!preparedBossMatchingCandidateIds.isEmpty()) {
                tag.put("PreparedBossMatchingCandidateIds", saveBoundedStrings(preparedBossMatchingCandidateIds));
            }
            tag.putString("PreparedBossSelectedId", sanitize(preparedBossSelectedId));
            tag.putString("PreparedBossResolutionReason", sanitize(preparedBossResolutionReason));
        }
        if (hubSpawnAnchorPos != null) {
            tag.putLong("HubSpawnAnchorPos", hubSpawnAnchorPos.asLong());
        }
        if (hubReturnAnchorPos != null) {
            tag.putLong("HubReturnAnchorPos", hubReturnAnchorPos.asLong());
        }
        if (hubGateAnchorPos != null) {
            tag.putLong("HubGateAnchorPos", hubGateAnchorPos.asLong());
        }
        if (hubSafeZoneCornerA != null) {
            tag.putLong("HubSafeZoneCornerA", hubSafeZoneCornerA.asLong());
        }
        if (hubSafeZoneCornerB != null) {
            tag.putLong("HubSafeZoneCornerB", hubSafeZoneCornerB.asLong());
        }
    }

    private static Set<DungeonModifierDefinition> loadModifiers(ListTag modifiersTag) {
        Set<DungeonModifierDefinition> modifiers = new LinkedHashSet<>();
        for (int i = 0; i < modifiersTag.size(); i++) {
            DungeonModifierDefinition modifier = DungeonModifierDefinition.byId(modifiersTag.getString(i));
            if (modifier != null) {
                modifiers.add(modifier);
            }
        }
        return modifiers;
    }

    private static ListTag saveModifiers(Set<DungeonModifierDefinition> modifiers) {
        ListTag tag = new ListTag();
        for (DungeonModifierDefinition modifier : modifiers) {
            tag.add(StringTag.valueOf(modifier.getId()));
        }
        return tag;
    }

    private static Set<String> loadStrings(ListTag valuesTag) {
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < valuesTag.size() && index < 8; index++) {
            String value = sanitize(valuesTag.getString(index));
            if (!value.isBlank() && !"NONE".equals(value)) values.add(value);
        }
        return values;
    }

    private static ListTag saveStrings(Set<String> values) {
        ListTag tag = new ListTag();
        values.stream().limit(8).forEach(value -> tag.add(StringTag.valueOf(sanitize(value))));
        return tag;
    }

    private static String summarizeModifiers(Set<DungeonModifierDefinition> modifiers, boolean ids) {
        if (modifiers.isEmpty()) {
            return "NONE";
        }
        return modifiers.stream()
            .map(modifier -> ids ? modifier.getId() : modifier.getDisplayName())
            .sorted()
            .collect(Collectors.joining(", "));
    }

    public void loadActiveRooms(Set<BlockPos> positions, @Nullable BlockPos activeControllerPos) {
        activeRoomControllerPositions.clear();
        if (positions != null) {
            for (BlockPos pos : positions) {
                if (pos != null) {
                    activeRoomControllerPositions.add(pos.immutable());
                }
            }
        }

        if (activeControllerPos != null) {
            activeRoomControllerPos = activeControllerPos.immutable();
        } else {
            refreshActiveRoomControllerPos();
        }
    }

    public void saveActiveRooms(CompoundTag tag) {
        tag.put("ActiveRoomControllerPositions", savePositions(activeRoomControllerPositions));
        if (activeRoomControllerPos != null) {
            tag.putLong("ActiveRoomControllerPos", activeRoomControllerPos.asLong());
        }
    }

    public record PreparedKinState(
        boolean dispatchEligible,
        String kinGroup,
        long seed,
        int catalogVersion,
        List<String> candidateIds,
        List<String> rejectionReasons
    ) {
        public PreparedKinState {
            kinGroup = normalizeOptional(kinGroup);
            catalogVersion = Math.max(0, catalogVersion);
            candidateIds = boundedKinCandidates(candidateIds);
            rejectionReasons = boundedKinRejections(rejectionReasons);
        }

        public boolean selected() {
            return dispatchEligible
                && catalogVersion == DungeonEnemyKinCatalog.CATALOG_VERSION
                && !kinGroup.isBlank()
                && !candidateIds.isEmpty()
                && candidateIds.stream().allMatch(candidateId ->
                    DungeonEnemyKinCatalog.forEntity(candidateId)
                        .map(entry -> kinGroup.equals(entry.kinGroup()))
                        .orElse(false));
        }

        private static List<String> boundedKinCandidates(List<String> values) {
            if (values == null || values.isEmpty()) return List.of();
            return List.copyOf(values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> normalizeOptional(value))
                .limit(PreparedFloorModifierPlan.MAX_KIN_CANDIDATES)
                .toList());
        }

        private static List<String> boundedKinRejections(List<String> values) {
            if (values == null || values.isEmpty()) return List.of();
            return List.copyOf(values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim())
                .limit(PreparedFloorModifierPlan.MAX_KIN_REJECTION_REASONS)
                .toList());
        }
    }

    public record PreparedBossState(
        boolean dispatchEligible,
        boolean bossSlotAvailable,
        boolean milestoneBoss,
        int catalogVersion,
        String affinityKinGroup,
        String affinityTheme,
        List<String> candidateIds,
        List<String> matchingCandidateIds,
        String selectedBossId,
        String resolutionReason
    ) {
        public PreparedBossState {
            catalogVersion = Math.max(0, catalogVersion);
            affinityKinGroup = sanitize(affinityKinGroup);
            affinityTheme = sanitize(affinityTheme);
            candidateIds = boundedBossIds(candidateIds);
            matchingCandidateIds = boundedBossIds(matchingCandidateIds);
            selectedBossId = sanitize(selectedBossId);
            resolutionReason = sanitize(resolutionReason);
        }

        public boolean selected() {
            return dispatchEligible && bossSlotAvailable && !selectedBossId.isBlank();
        }

        private static List<String> boundedBossIds(List<String> values) {
            if (values == null || values.isEmpty()) return List.of();
            return List.copyOf(values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> sanitize(value).toLowerCase(java.util.Locale.ROOT))
                .limit(PreparedFloorModifierPlan.MAX_BOSS_CANDIDATES)
                .toList());
        }
    }

    private static List<String> loadBoundedStrings(ListTag valuesTag, int maximum) {
        return loadBoundedStrings(valuesTag, maximum, true);
    }

    private static List<String> loadBoundedStrings(ListTag valuesTag, int maximum, boolean normalizeLowerCase) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < valuesTag.size() && index < maximum; index++) {
            String value = sanitize(valuesTag.getString(index));
            if (!value.isBlank()) {
                values.add(normalizeLowerCase ? value.toLowerCase(java.util.Locale.ROOT) : value);
            }
        }
        return List.copyOf(values);
    }

    private static ListTag saveBoundedStrings(List<String> values) {
        return saveBoundedStrings(values, PreparedFloorModifierPlan.MAX_BOSS_CANDIDATES);
    }

    private static ListTag saveBoundedStrings(List<String> values, int maximum) {
        ListTag tag = new ListTag();
        if (values != null) {
            values.stream()
                .limit(Math.max(0, maximum))
                .forEach(value -> tag.add(StringTag.valueOf(sanitize(value))));
        }
        return tag;
    }

    private void refreshActiveRoomControllerPos() {
        activeRoomControllerPos = activeRoomControllerPositions.isEmpty() ? null : activeRoomControllerPositions.iterator().next();
    }

    private static Set<BlockPos> loadPositions(ListTag list) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag posTag = list.getCompound(i);
            if (posTag.contains("Pos")) {
                NbtUtils.readBlockPos(posTag, "Pos").ifPresent(pos -> positions.add(pos.immutable()));
            }
        }
        return positions;
    }

    private static List<BlockPos> loadOrderedPositions(ListTag list) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag posTag = list.getCompound(i);
            if (posTag.contains("Pos")) {
                NbtUtils.readBlockPos(posTag, "Pos").ifPresent(pos -> positions.add(pos.immutable()));
            }
        }
        return positions;
    }

    private static ListTag savePositions(Iterable<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            CompoundTag posTag = new CompoundTag();
            posTag.put("Pos", NbtUtils.writeBlockPos(pos));
            list.add(posTag);
        }
        return list;
    }

    private static String summarizePositions(Set<BlockPos> positions) {
        if (positions.isEmpty()) {
            return "NONE";
        }
        return positions.stream().map(DungeonRunState::formatPos).sorted().collect(Collectors.joining(", "));
    }

    private static String formatPresetPos(@Nullable BlockPos pos) {
        return formatPos(pos);
    }

    private static String formatPos(@Nullable BlockPos pos) {
        if (pos == null) {
            return "NONE";
        }
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String sanitize(String value) {
        return value == null || value.isBlank() ? "NONE" : value;
    }

    private static String normalizeOptional(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
