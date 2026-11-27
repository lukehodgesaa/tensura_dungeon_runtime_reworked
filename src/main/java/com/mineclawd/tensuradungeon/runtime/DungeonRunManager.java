package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class DungeonRunManager extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "tensura_dungeon_run_manager";
    private static final String INSTANCES_KEY = "Instances";
    private static final String RUN_STATE_KEY = "RunState";
    private static final DungeonRunService RUN_SERVICE = new DungeonRunService();

    private final Map<DungeonInstanceAllocationData.InstanceKey, DungeonRunState> runStates =
        new LinkedHashMap<>();
    private DungeonRunState runState = new DungeonRunState();

    public static DungeonRunManager get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(DungeonRunManager::new, DungeonRunManager::load), DATA_NAME);
    }

    public static DungeonRunManager load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonRunManager manager = new DungeonRunManager();
        if (tag.contains(INSTANCES_KEY, Tag.TAG_LIST)) {
            ListTag instances = tag.getList(INSTANCES_KEY, Tag.TAG_COMPOUND);
            for (int index = 0; index < instances.size(); index++) {
                CompoundTag entry = instances.getCompound(index);
                DungeonInstanceAllocationData.InstanceKey instanceKey =
                    DungeonInstanceAllocationData.InstanceKey.loadOptional(entry);
                if (instanceKey == null || !entry.contains(RUN_STATE_KEY, Tag.TAG_COMPOUND)) {
                    throw new IllegalStateException(
                        "Instance-keyed dungeon run entry is missing identity or run state"
                    );
                }
                DungeonRunState state = new DungeonRunState();
                state.load(entry.getCompound(RUN_STATE_KEY));
                if (!state.matchesInstance(instanceKey)) {
                    throw new IllegalStateException(
                        "Instance-keyed dungeon run entry disagrees with its embedded owner"
                    );
                }
                if (state.normalizeForReload()) {
                    manager.setDirty();
                }
                if (manager.runStates.putIfAbsent(instanceKey, state) != null) {
                    throw new IllegalStateException(
                        "Duplicate instance-keyed dungeon run entry: " + instanceKey
                    );
                }
            }
            if (!manager.runStates.isEmpty()) {
                manager.runState = manager.runStates.entrySet().stream()
                    .min(Map.Entry.comparingByKey(INSTANCE_KEY_ORDER))
                    .orElseThrow()
                    .getValue();
            }
        } else {
            manager.runState.load(tag);
            if (manager.runState.normalizeForReload()) {
                manager.setDirty();
            }
        }
        return manager;
    }

    private static final Comparator<DungeonInstanceAllocationData.InstanceKey> INSTANCE_KEY_ORDER =
        Comparator.comparing((DungeonInstanceAllocationData.InstanceKey key) -> key.instanceId().toString())
            .thenComparingLong(DungeonInstanceAllocationData.InstanceKey::epoch);

    @Nullable
    public DungeonInstanceAllocationData.InstanceKey getInstanceKey() {
        return runState.getInstanceKey();
    }

    public boolean canBindInstance(DungeonInstanceAllocationData.InstanceKey instanceKey) {
        return instanceKey != null && (runStates.containsKey(instanceKey)
            || runState.canBindInstance(instanceKey));
    }

    public boolean bindInstance(DungeonInstanceAllocationData.InstanceKey instanceKey) {
        DungeonRunState persisted = runStates.get(instanceKey);
        if (persisted != null) {
            runState = persisted;
            return true;
        }
        if (instanceKey != null && instanceKey.equals(runState.getInstanceKey())) {
            runStates.putIfAbsent(instanceKey, runState);
            return true;
        }
        DungeonInstanceAllocationData.InstanceKey previousKey = runState.getInstanceKey();
        if (!runState.bindInstance(instanceKey)) {
            return false;
        }
        if (previousKey != null) {
            runStates.remove(previousKey, runState);
        }
        runStates.put(instanceKey, runState);
        setDirty();
        return true;
    }

    public List<DungeonInstanceAllocationData.InstanceKey> getPersistedInstanceKeys() {
        List<DungeonInstanceAllocationData.InstanceKey> keys = new ArrayList<>(runStates.keySet());
        keys.sort(INSTANCE_KEY_ORDER);
        return List.copyOf(keys);
    }

    public boolean initializeInstance(DungeonInstanceAllocationData.InstanceKey instanceKey) {
        if (instanceKey == null) {
            return false;
        }
        if (runStates.containsKey(instanceKey)) {
            return true;
        }
        DungeonRunState state = new DungeonRunState();
        if (!state.bindInstance(instanceKey)) {
            return false;
        }
        DungeonRunState hubConfigurationSource = runState.hasHubConfiguration()
            ? runState
            : runStates.values().stream()
                .filter(DungeonRunState::hasHubConfiguration)
                .findFirst()
                .orElse(null);
        state.inheritHubConfiguration(hubConfigurationSource);
        runStates.put(instanceKey, state);
        setDirty();
        return true;
    }

    public boolean canRetireInstance(DungeonInstanceAllocationData.InstanceKey instanceKey) {
        if (instanceKey == null) {
            return false;
        }
        DungeonRunState state = runStates.get(instanceKey);
        if (state == null) {
            return true;
        }
        return !state.isActiveRun() || (
            state.isRunCompleted()
                && state.getLastHubTransitionReason().startsWith("RETURNED_TO_HUB")
        );
    }

    public boolean retireInstance(DungeonInstanceAllocationData.InstanceKey instanceKey) {
        if (!canRetireInstance(instanceKey)) {
            return false;
        }
        DungeonRunState removed = runStates.remove(instanceKey);
        if (removed == null) {
            return false;
        }
        if (runState == removed) {
            runState = runStates.entrySet().stream()
                .min(Map.Entry.comparingByKey(INSTANCE_KEY_ORDER))
                .map(Map.Entry::getValue)
                .orElseGet(DungeonRunState::new);
        }
        runState.inheritHubConfiguration(removed);
        setDirty();
        return true;
    }

    public boolean matchesInstance(@Nullable DungeonInstanceAllocationData.InstanceKey expected) {
        return runState.matchesInstance(expected);
    }

    private @Nullable DungeonRunState exactState(
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return instanceKey == null ? runState : runStates.get(instanceKey);
    }

    public boolean hasInstance(DungeonInstanceAllocationData.InstanceKey instanceKey) {
        return exactState(instanceKey) != null;
    }

    public void startRun(ServerLevel level) {
        RUN_SERVICE.startRun(runState, this::setDirty, level);
    }

    public void startRunAtFloor(ServerLevel level, int requestedFloor) {
        RUN_SERVICE.startRunAtFloor(runState, this::setDirty, level, requestedFloor);
    }

    public void endRun(ServerLevel level) {
        RUN_SERVICE.endRun(runState, this::setDirty, level);
    }

    public void resetRun(ServerLevel level) {
        RUN_SERVICE.resetRun(runState, this::setDirty, level);
    }

    public void endRunWithControllerCleanup(ServerLevel level) {
        RUN_SERVICE.endRunWithControllerCleanup(runState, this::setDirty, level);
    }

    public void resetRunWithControllerCleanup(ServerLevel level) {
        RUN_SERVICE.resetRunWithControllerCleanup(runState, this::setDirty, level);
    }

    public DungeonRunService.FloorStartOperationResult startFloor(
        ServerLevel level,
        UUID playerId,
        int requestedFloor,
        @Nullable RoomControllerBlockEntity controller
    ) {
        DungeonRunService.FloorStartOperationResult result = RUN_SERVICE.startFloor(
            runState, this::setDirty, level, DungeonFloorProgressData.get(level), playerId, requestedFloor, controller
        );
        initializeSoulOrbHaul(level, playerId, result.succeeded());
        return result;
    }

    public DungeonRunService.AdvanceFloorOperationResult advanceFloorFromCommand(
        ServerLevel level,
        UUID playerId,
        @Nullable RoomControllerBlockEntity controller
    ) {
        return RUN_SERVICE.advanceFloorFromCommand(
            runState, this::setDirty, level, DungeonFloorProgressData.get(level), playerId, controller
        );
    }

    public DungeonRunService.PresetArmOperationResult armPresetFloor(
        ServerLevel level,
        UUID playerId,
        int requestedFloor,
        int rooms,
        boolean armedFromHub,
        List<BlockPos> order
    ) {
        DungeonRunService.PresetArmOperationResult result = RUN_SERVICE.armPresetFloor(
            runState, this::setDirty, level, DungeonFloorProgressData.get(level), playerId,
            requestedFloor, rooms, armedFromHub, order
        );
        initializeSoulOrbHaul(level, playerId, result.succeeded());
        return result;
    }

    public DungeonRunService.GeneratedFloorArmOperationResult armGeneratedFloor(
        ServerLevel level,
        UUID playerId,
        DungeonRunService.GeneratedFloorArmPlan plan
    ) {
        DungeonRunService.GeneratedFloorArmOperationResult result = RUN_SERVICE.armGeneratedFloor(
            runState, this::setDirty, level, DungeonFloorProgressData.get(level), playerId, plan
        );
        initializeSoulOrbHaul(level, playerId, result.succeeded());
        return result;
    }

    public DungeonRunService.GeneratedFloorArmOperationResult armGeneratedFloor(
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        ServerLevel level,
        UUID playerId,
        DungeonRunService.GeneratedFloorArmPlan plan
    ) {
        DungeonRunState state = exactState(instanceKey);
        if (state == null) {
            return new DungeonRunService.GeneratedFloorArmOperationResult(
                "INSTANCE_RUN_OWNER_MISSING", null, List.of(), 0, false, -1, null
            );
        }
        DungeonRunService.GeneratedFloorArmOperationResult result = RUN_SERVICE.armGeneratedFloor(
            state, this::setDirty, level, DungeonFloorProgressData.get(level), playerId, plan
        );
        initializeSoulOrbHaul(level, playerId, state, result.succeeded());
        return result;
    }

    private void initializeSoulOrbHaul(ServerLevel level, UUID playerId, boolean succeeded) {
        initializeSoulOrbHaul(level, playerId, runState, succeeded);
    }

    private void initializeSoulOrbHaul(
        ServerLevel level,
        UUID playerId,
        DungeonRunState state,
        boolean succeeded
    ) {
        if (!succeeded || state == null) return;
        SoulOrbHaulData haul = SoulOrbHaulData.get(level);
        haul.beginSession(playerId, state.getRunSessionId(), state.getFloorNumber());
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) haul.syncHud(player);
    }

    public boolean isActiveRun(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state != null && state.isActiveRun();
    }

    public boolean isDebugRoomActivationOverride(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state != null && state.isDebugRoomActivationOverride();
    }

    public boolean isRunCompleted(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null || state.isRunCompleted();
    }

    public boolean isFailed(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null || state.isFailed();
    }

    public boolean isPresetMode(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state != null && state.isPresetMode();
    }

    public boolean isHubEnabled(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state != null && state.isHubEnabled();
    }

    public boolean isPlayerInsideHubSafeZone(
        DungeonInstanceAllocationData.InstanceKey key,
        @Nullable BlockPos pos
    ) {
        DungeonRunState state = exactState(key);
        return state != null && state.isPlayerInsideHubSafeZone(pos);
    }

    public boolean isRoomRegisteredActive(
        DungeonInstanceAllocationData.InstanceKey key,
        BlockPos pos
    ) {
        DungeonRunState state = exactState(key);
        return state != null && state.isRoomRegisteredActive(pos);
    }

    public boolean isRoomRegisteredCompleted(
        DungeonInstanceAllocationData.InstanceKey key,
        BlockPos pos
    ) {
        DungeonRunState state = exactState(key);
        return state != null && state.isRoomRegisteredCompleted(pos);
    }

    public String getPresetActivationBlockReason(
        DungeonInstanceAllocationData.InstanceKey key,
        BlockPos controllerPos
    ) {
        DungeonRunState state = exactState(key);
        return state == null ? "INSTANCE_RUN_OWNER_MISSING"
            : state.getPresetActivationBlockReason(controllerPos);
    }

    public Set<BlockPos> getActiveRoomControllerPositions(
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        DungeonRunState state = exactState(key);
        return state == null ? Set.of() : state.getActiveRoomControllerPositions();
    }

    public Set<BlockPos> getCompletedRoomControllerPositions(
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        DungeonRunState state = exactState(key);
        return state == null ? Set.of() : state.getCompletedRoomControllerPositions();
    }

    public Set<BlockPos> getAllActiveRoomControllerPositions() {
        java.util.LinkedHashSet<BlockPos> positions = new java.util.LinkedHashSet<>();
        if (runStates.isEmpty()) {
            positions.addAll(runState.getActiveRoomControllerPositions());
        } else {
            runStates.values().forEach(
                state -> positions.addAll(state.getActiveRoomControllerPositions())
            );
        }
        return Set.copyOf(positions);
    }

    public String onRoomActivated(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        BlockPos controllerPos
    ) {
        DungeonRunState state = exactState(key);
        return state == null ? "INSTANCE_RUN_OWNER_MISSING"
            : RUN_SERVICE.onRoomActivated(state, this::setDirty, level, controllerPos);
    }

    public int getRemainingRoomCount(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? 0 : state.getRemainingRoomCount();
    }

    public RoomCompletionResult onRoomCompleted(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        BlockPos controllerPos,
        String roomCompletionReason
    ) {
        DungeonRunState state = exactState(key);
        if (state == null) {
            return new RoomCompletionResult(false, 0, "NONE", "INSTANCE_RUN_OWNER_MISSING");
        }
        DungeonRunService.RoomCompletionResult result =
            RUN_SERVICE.onRoomCompleted(state, this::setDirty, level, controllerPos, roomCompletionReason);
        if (result.floorCompletedNow()) {
            beginTerminalDisposition(level, key, "INSTANCE_FLOOR_COMPLETED");
        }
        return new RoomCompletionResult(
            result.floorCompletedNow(), result.floorNumber(), result.runSessionId(), result.completionReason()
        );
    }

    public void applyFloorCompletionEffects(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        ServerPlayer rewardPlayer,
        int floorNumber,
        String runSessionId,
        String completionReason,
        DungeonRunService.FloorCompletionContext context
    ) {
        DungeonRunState state = exactState(key);
        if (state != null) {
            RUN_SERVICE.applyFloorCompletionEffects(
                state, level, rewardPlayer, floorNumber, runSessionId, completionReason, context
            );
        }
    }

    public void onRoomFailed(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        BlockPos controllerPos,
        String reason
    ) {
        DungeonRunState state = exactState(key);
        if (state != null) {
            RUN_SERVICE.onRoomFailed(
                state, this::setDirty, level, controllerPos, reason, key
            );
        }
    }

    public void preserveFailedFloorForRetry(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        @Nullable ServerPlayer player
    ) {
        DungeonRunState state = exactState(key);
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation allocation =
            allocations.forIdentity(key).orElse(null);
        if (state == null || allocation == null) {
            if (state != null) {
                RUN_SERVICE.onFailedFloorReturnToHub(state, this::setDirty, level, player);
            }
            return;
        }

        DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);
        DungeonSubordinateTransportData.TransportResult verified =
            transport.verifyReturnToHub(level, allocation);
        if (!verified.accepted()) {
            LOGGER.error(
                "Failed-run replay could not verify subordinate return; falling back to terminal recovery: instance={} epoch={} reason={}",
                key.instanceId(), key.epoch(), verified.reason()
            );
            onFailedFloorReturnToHub(key, level, player);
            return;
        }

        for (UUID memberId : allocation.roster()) {
            DungeonInstanceAllocationData.MemberDispositionResult disposition =
                allocations.requireHubReturnOnLogin(
                    key, memberId, "INSTANCE_FLOOR_FAILED_REPLAY_RETAINED", level.getGameTime()
                );
            if (!disposition.accepted()) {
                LOGGER.error(
                    "Failed-run replay could not persist hub disposition; falling back to terminal recovery: instance={} epoch={} member={} reason={}",
                    key.instanceId(), key.epoch(), memberId, disposition.reason()
                );
                onFailedFloorReturnToHub(key, level, player);
                return;
            }
        }

        ServerPlayer host = level.getServer().getPlayerList().getPlayer(allocation.host());
        ServerPlayer returnPlayer = host != null ? host : player;
        RUN_SERVICE.onFailedFloorReturnToHub(
            state, this::setDirty, level, returnPlayer
        );

        DungeonInstanceAllocationData.Allocation suspended =
            allocations.forIdentity(key).orElse(null);
        if (suspended != null) {
            DungeonSubordinateTransportData.TransportResult returned =
                transport.returnToHub(level, suspended);
            if (!returned.accepted()) {
                LOGGER.error(
                    "Failed-run replay retained but subordinate return was deferred: instance={} epoch={} reason={}",
                    key.instanceId(), key.epoch(), returned.reason()
                );
            }
        }
        level.getServer().overworld().getDataStorage().save();
        LOGGER.info(
            "Failed dungeon run retained for deterministic replay: instance={} epoch={} floor={} modifiers={}",
            key.instanceId(), key.epoch(), state.getRetryFloorNumber(),
            state.getRetryModifierIdsSummary()
        );
    }

    public void onFailedFloorReturnToHub(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        @Nullable ServerPlayer player
    ) {
        DungeonRunState state = exactState(key);
        if (state != null) {
            DungeonInstanceAllocationData.Allocation allocation =
                DungeonInstanceAllocationData.get(level).forIdentity(key).orElse(null);
            ServerPlayer inventoryOwner = allocation == null
                ? null
                : level.getServer().getPlayerList().getPlayer(allocation.host());
            DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);
            DungeonSubordinateTransportData.TerminalEscapePlan escapePlan = null;
            if (allocation != null && inventoryOwner != null
                && allocation.soloPreparation() != null
                && allocation.soloPreparation().hostAndSubordinateRoster(allocation.host())) {
                escapePlan = transport.prepareTerminalEscape(level, allocation, inventoryOwner);
                if (!escapePlan.accepted()) {
                    return;
                }
            }
            DungeonInstanceAllocationData.TerminalDispositionResult terminal =
                beginTerminalDisposition(level, key, "INSTANCE_FLOOR_FAILED");
            if (!terminal.accepted()) {
                return;
            }
            DungeonSubordinateTransportData.TerminalEscapeStage escapeStage = null;
            if (escapePlan != null) {
                escapeStage = transport.stageTerminalEscape(
                    level, terminal.allocation(), escapePlan
                );
                if (!escapeStage.accepted()) {
                    return;
                }
            }
            RUN_SERVICE.onFailedFloorReturnToHub(state, this::setDirty, level, player);
            if (player != null) {
                consumeDeferredReturn(level, player.getUUID(), key);
            }
            if (escapePlan != null) {
                DungeonPreparationLoadoutData loadouts = DungeonPreparationLoadoutData.get(level);
                boolean noItems = escapePlan.bracelets().isEmpty()
                    && escapePlan.whistles().isEmpty();
                boolean consumed = noItems || loadouts.consumeTerminalReservations(
                    escapePlan.bracelets(), escapePlan.whistles(), escapePlan.inventories(inventoryOwner)
                );
                DungeonSubordinateTransportData.TransportResult committed =
                    transport.commitTerminalEscape(level, escapeStage);
                if (!consumed) {
                    LOGGER.error(
                        "[TD-ESCAPE] result=ITEM_COMMIT_FAILED instance={} epoch={} trigger=INSTANCE_FLOOR_FAILED",
                        key.instanceId(), key.epoch()
                    );
                }
                if (!committed.accepted()) {
                    LOGGER.error(
                        "[TD-ESCAPE] result=DISPOSITION_COMMIT_FAILED instance={} epoch={} trigger=INSTANCE_FLOOR_FAILED reason={}",
                        key.instanceId(), key.epoch(), committed.reason()
                    );
                }
            }
            RUN_SERVICE.endRun(state, this::setDirty, level);
            GeneratedFloorCleanupProcessor.beginExactInstanceCleanup(level, key);
        }
    }

    public int getFloorNumber(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? 0 : state.getFloorNumber();
    }

    public String getModifierIdsSummary(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? "NONE" : state.getModifierIdsSummary();
    }

    public String getModifierDisplayNamesSummary(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? "NONE" : state.getModifierDisplayNamesSummary();
    }

    public String getRunSessionId(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? "NONE" : state.getRunSessionId();
    }

    public long getRunSeed(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? 0L : state.getRunSeed();
    }

    public int getTargetRoomCount(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? 0 : state.getTargetRoomCount();
    }

    public Set<DungeonModifierDefinition> getActiveModifiers(
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        DungeonRunState state = exactState(key);
        return state == null ? Set.of() : state.getActiveModifiers();
    }

    public Set<String> getActiveModifierIds(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? Set.of() : state.getActiveModifierIds();
    }

    public Set<String> getActiveRoomPressureIds(
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        DungeonRunState state = exactState(key);
        return state == null ? Set.of() : state.getActiveRoomPressureIds();
    }

    public DungeonRunState.PreparedKinState getPreparedKinState(
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        DungeonRunState state = exactState(key);
        return state == null
            ? new DungeonRunState.PreparedKinState(false, "", 0L, 0, List.of(), List.of())
            : state.getPreparedKinState();
    }

    public DungeonRunState.PreparedBossState getPreparedBossState(
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        DungeonRunState state = exactState(key);
        return state == null
            ? new DungeonRunState.PreparedBossState(
                false, false, false, 0, "", "", List.of(), List.of(), "", "INSTANCE_RUN_OWNER_MISSING"
            )
            : state.getPreparedBossState();
    }

    public String getCompletionReason(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? "INSTANCE_RUN_OWNER_MISSING" : state.getCompletionReason();
    }

    public @Nullable BlockPos getHubReturnTarget(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? null : state.getHubReturnTarget();
    }

    public boolean isExitPortalActive(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state != null && state.isExitPortalActive();
    }

    public @Nullable BlockPos getExitPortalPos(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? null : state.getExitPortalPos();
    }

    public int getNextFloorNumber(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? 0 : state.getNextFloorNumber();
    }

    public String getExitPortalReason(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null ? "INSTANCE_RUN_OWNER_MISSING" : state.getExitPortalReason();
    }

    public String getExitPortalReadinessDetails(DungeonInstanceAllocationData.InstanceKey key) {
        DungeonRunState state = exactState(key);
        return state == null
            ? "INSTANCE_RUN_OWNER_MISSING"
            : state.getExitPortalReadinessDetails();
    }

    public String getExitPortalInteractionBlockReason(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        ServerPlayer player,
        BlockPos clickedPos
    ) {
        DungeonRunState state = exactState(key);
        return state == null
            ? "INSTANCE_RUN_OWNER_MISSING"
            : RUN_SERVICE.getExitPortalInteractionBlockReason(state, level, player, clickedPos);
    }

    public @Nullable BlockPos getPresetExpectedControllerPos(
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        DungeonRunState state = exactState(key);
        return state == null ? null : state.getPresetExpectedControllerPos();
    }

    public DungeonRunService.GeneratedFloorEntryValidationResult validateGeneratedFloorEntry(
        ServerLevel level,
        UUID playerId,
        int requestedFloor
    ) {
        return RUN_SERVICE.validateGeneratedFloorEntry(
            runState,
            DungeonFloorProgressData.get(level),
            playerId,
            requestedFloor
        );
    }

    public String onRoomActivated(ServerLevel level, BlockPos controllerPos) {
        return RUN_SERVICE.onRoomActivated(runState, this::setDirty, level, controllerPos);
    }

    public boolean skipExpectedGeneratedRoom(
        DungeonInstanceAllocationData.InstanceKey key,
        BlockPos controllerPos,
        String reason
    ) {
        DungeonRunState state = exactState(key);
        if (state == null || !state.skipExpectedPresetRoom(controllerPos, reason)) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean undoSkippedGeneratedRoom(
        DungeonInstanceAllocationData.InstanceKey key,
        BlockPos controllerPos
    ) {
        DungeonRunState state = exactState(key);
        if (state == null || !state.undoSkippedPresetRoom(controllerPos)) {
            return false;
        }
        setDirty();
        return true;
    }

    public RoomCompletionResult onRoomCompleted(ServerLevel level, BlockPos controllerPos, String roomCompletionReason) {
        DungeonRunService.RoomCompletionResult result =
            RUN_SERVICE.onRoomCompleted(runState, this::setDirty, level, controllerPos, roomCompletionReason);
        return new RoomCompletionResult(
            result.floorCompletedNow(), result.floorNumber(), result.runSessionId(), result.completionReason()
        );
    }

    public record RoomCompletionResult(boolean floorCompletedNow, int floorNumber, String runSessionId, String completionReason) {
    }

    public void applyFloorCompletionEffects(
        ServerLevel level,
        ServerPlayer rewardPlayer,
        int floorNumber,
        String runSessionId,
        String completionReason,
        DungeonRunService.FloorCompletionContext context
    ) {
        RUN_SERVICE.applyFloorCompletionEffects(
            runState, level, rewardPlayer, floorNumber, runSessionId, completionReason, context
        );
    }

    public void onRoomFailed(ServerLevel level, BlockPos controllerPos, String reason) {
        RUN_SERVICE.onRoomFailed(runState, this::setDirty, level, controllerPos, reason);
    }

    public boolean isActiveRun() { return runState.isActiveRun(); }
    public String getRunSessionId() { return runState.getRunSessionId(); }
    public int getFloorNumber() { return runState.getFloorNumber(); }

    public boolean setFloorNumberForTesting(int floorNumber) {
        return RUN_SERVICE.setFloorNumberForTesting(runState, this::setDirty, floorNumber);
    }

    public boolean setTargetRoomCountForTesting(int targetRoomCount) {
        return RUN_SERVICE.setTargetRoomCountForTesting(runState, this::setDirty, targetRoomCount);
    }

    public boolean advanceToNextFloor(ServerLevel level) {
        return RUN_SERVICE.advanceToNextFloor(runState, this::setDirty, level);
    }

    public String getAdvanceToNextFloorBlockReason() {
        if (!runState.isActiveRun()) {
            return "activeRun=false";
        }
        if (!runState.isRunCompleted()) {
            return "runCompleted=false";
        }
        if (!runState.isNextFloorEligible()) {
            return "nextFloorEligible=false";
        }
        if (runState.getNextFloorNumber() <= runState.getFloorNumber()) {
            return "nextFloorNumber<=floorNumber (" + runState.getNextFloorNumber() + "<=" + runState.getFloorNumber() + ")";
        }
        if (runState.isFailed()) {
            return "failed=true reason=" + runState.getFailureReason();
        }
        if (runState.hasActiveRooms()) {
            return "active room still registered: " + runState.getActiveRoomPositionsSummary();
        }
        return "UNKNOWN_MANAGER_BLOCK";
    }

    public boolean setForcedModifier(String modifierId) {
        return RUN_SERVICE.setForcedModifier(runState, this::setDirty, modifierId);
    }

    public void clearModifiers() {
        RUN_SERVICE.clearModifiers(runState, this::setDirty);
    }

    public Set<DungeonModifierDefinition> getActiveModifiers() {
        return runState.getActiveModifiers();
    }

    public String getModifierReason() {
        return runState.getModifierReason();
    }

    public int getModifierCount() {
        return runState.getModifierCount();
    }

    public String getModifierIdsSummary() {
        return runState.getModifierIdsSummary();
    }

    public String getModifierDisplayNamesSummary() {
        return runState.getModifierDisplayNamesSummary();
    }

    public String getModifierCategoriesSummary() {
        return runState.getModifierCategoriesSummary();
    }

    public double getModifierBudgetMultiplier() {
        return runState.getModifierBudgetMultiplier();
    }

    public double getModifierMobCountMultiplier() {
        return runState.getModifierMobCountMultiplier();
    }

    public double getModifierHealthMultiplier() {
        return runState.getModifierHealthMultiplier();
    }

    public int getModifierMaxAliveBonus() {
        return runState.getModifierMaxAliveBonus();
    }

    public String getModifierPerformanceCostSummary() {
        return runState.getModifierPerformanceCostSummary();
    }

    public long getRunSeed() { return runState.getRunSeed(); }
    public int getActiveRoomCount() { return runState.getActiveRoomCount(); }
    public int getCompletedRoomCount() { return runState.getCompletedRoomCount(); }
    public int getTargetRoomCount() { return runState.getTargetRoomCount(); }
    public int getRemainingRoomCount() { return runState.getRemainingRoomCount(); }
    public boolean isPresetMode() { return runState.isPresetMode(); }

    public String getFloorModeLabel() {
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

    public int getPresetRoomCount() { return runState.getPresetRoomCount(); }
    public int getPresetCurrentIndex() { return runState.getPresetCurrentIndex(); }
    public List<BlockPos> getPresetRoomOrder() { return runState.getPresetRoomOrder(); }
    public BlockPos getPresetExpectedControllerPos() { return runState.getPresetExpectedControllerPos(); }
    public int getPresetCompletedCount() { return runState.getPresetCompletedCount(); }
    public int getPresetRemainingCount() { return runState.getPresetRemainingCount(); }
    public String getLastPresetTransitionReason() { return runState.getLastPresetTransitionReason(); }
    public String getLastPresetRejectedControllerSummary() { return runState.getLastPresetRejectedControllerSummary(); }
    public boolean isPresetRoomInOrder(BlockPos controllerPos) { return runState.isPresetRoomInOrder(controllerPos); }
    public boolean isPresetRoomCompletedByOrder(BlockPos controllerPos) { return runState.isPresetRoomCompletedByOrder(controllerPos); }
    public String getPresetControllerStatus(BlockPos controllerPos) { return runState.getPresetControllerStatus(controllerPos); }
    public String getPresetActivationBlockReason(BlockPos controllerPos) { return runState.getPresetActivationBlockReason(controllerPos); }
    public String getPresetOrderDebugSummary() { return runState.getPresetOrderDebugSummary(); }

    public boolean replacePendingPresetOrder(List<BlockPos> controllerOrder) {
        return RUN_SERVICE.replacePendingPresetOrder(runState, this::setDirty, controllerOrder);
    }
    public void enableRollingPresetMode(List<BlockPos> controllerOrder, int requestedRoomCount) {
        RUN_SERVICE.enableRollingPresetMode(runState, this::setDirty, controllerOrder, requestedRoomCount);
    }

    public boolean appendRollingPresetOrder(List<BlockPos> appendedControllers) {
        return RUN_SERVICE.appendRollingPresetOrder(runState, this::setDirty, appendedControllers);
    }
    public void enablePresetMode(List<BlockPos> controllerOrder) {
        RUN_SERVICE.enablePresetMode(runState, this::setDirty, controllerOrder);
    }

    public String getActiveRoomControllerPosString() { return runState.getActiveRoomControllerPosString(); }

    public boolean isFailed() { return runState.isFailed(); }
    public String getFailureReason() { return runState.getFailureReason(); }
    public boolean isDebugRoomActivationOverride() { return runState.isDebugRoomActivationOverride(); }

    public void setDebugRoomActivationOverride(boolean enabled) {
        RUN_SERVICE.setDebugRoomActivationOverride(runState, this::setDirty, enabled);
    }

    public boolean requiresActiveRunForRoomActivation() { return !runState.isDebugRoomActivationOverride(); }
    public boolean isRunCompleted() { return runState.isRunCompleted(); }
    public boolean isFloorCompleted() { return runState.isRunCompleted(); }
    public String getCompletionReason() { return runState.getCompletionReason(); }
    public boolean isNextFloorEligible() { return runState.isNextFloorEligible(); }
    public int getNextFloorNumber() { return runState.getNextFloorNumber(); }
    public boolean isExitPortalActive() { return runState.isExitPortalActive(); }
    public boolean isHubEnabled() { return runState.isHubEnabled(); }
    public @Nullable BlockPos getHubSpawnAnchorPos() { return runState.getHubSpawnAnchorPos(); }
    public @Nullable BlockPos getHubReturnAnchorPos() { return runState.getHubReturnAnchorPos(); }
    public @Nullable BlockPos getHubGateAnchorPos() { return runState.getHubGateAnchorPos(); }
    public @Nullable BlockPos getHubSafeZoneCornerA() { return runState.getHubSafeZoneCornerA(); }
    public @Nullable BlockPos getHubSafeZoneCornerB() { return runState.getHubSafeZoneCornerB(); }
    public String getHubGateState() { return runState.getHubGateState(); }
    public String getLastHubTransitionReason() { return runState.getLastHubTransitionReason(); }
    public int getPreparedFloorNumber() { return runState.getPreparedFloorNumber(); }
    public boolean isPreparedFloorReady() { return runState.isPreparedFloorReady(); }
    public boolean isPresetArmedFromHub() { return runState.isPresetArmedFromHub(); }
    public int getHubPreparedPresetRooms() { return runState.getHubPreparedPresetRooms(); }
    public String getArchivedPresetOrderSummary() { return runState.getArchivedPresetOrderSummary(); }
    public boolean isRetryPreservingModifiers() { return runState.isRetryPreservingModifiers(); }
    public int getRetryFloorNumber() { return runState.getRetryFloorNumber(); }
    public String getRetryModifierIdsSummary() { return runState.getRetryModifierIdsSummary(); }

    public String getExitPortalPosString() { return runState.getExitPortalPosString(); }

    public String getExitPortalSource() { return runState.getExitPortalSource(); }
    public String getExitPortalReason() { return runState.getExitPortalReason(); }
    public String getExitPortalReadinessDetails() { return runState.getExitPortalReadinessDetails(); }
    public String getPreparedModifierIdsSummary() { return runState.getModifierIdsSummary(); }
    public @Nullable BlockPos getExitPortalPos() { return runState.getExitPortalPos(); }

    public boolean isExitPortalBlockPresent(ServerLevel level) {
        return RUN_SERVICE.isExitPortalBlockPresent(runState, level);
    }

    public String getExitPortalDebugState(ServerLevel level) {
        return RUN_SERVICE.getExitPortalDebugState(runState, level);
    }

    public String getExitPortalInteractionBlockReason(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos clickedPos) {
        return RUN_SERVICE.getExitPortalInteractionBlockReason(runState, level, player, clickedPos);
    }

    public String getExitPortalActionBlockReason(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        return RUN_SERVICE.getExitPortalActionBlockReason(runState, level, player);
    }

    public void setHubSpawnAnchor(BlockPos pos) {
        RUN_SERVICE.setHubSpawnAnchor(runState, this::setDirty, pos);
    }

    public void setHubReturnAnchor(BlockPos pos) {
        RUN_SERVICE.setHubReturnAnchor(runState, this::setDirty, pos);
    }

    public void setHubGateAnchor(BlockPos pos) {
        RUN_SERVICE.setHubGateAnchor(runState, this::setDirty, pos);
    }

    public void setHubSafeZone(BlockPos cornerA, BlockPos cornerB) {
        RUN_SERVICE.setHubSafeZone(runState, this::setDirty, cornerA, cornerB);
    }

    public boolean isPlayerInsideHubSafeZone(@Nullable BlockPos pos) {
        return runState.isPlayerInsideHubSafeZone(pos);
    }

    public boolean returnPlayerToHub(ServerLevel level, @Nullable ServerPlayer player, String reason) {
        return RUN_SERVICE.returnPlayerToHub(runState, this::setDirty, level, player, reason);
    }

    public boolean returnPlayerToHubFromCommand(ServerLevel level, ServerPlayer player, String reason) {
        return RUN_SERVICE.returnPlayerToHubFromCommand(runState, this::setDirty, level, player, reason);
    }

    public void onSuccessfulPortalContinueToHub(ServerLevel level, @Nullable ServerPlayer player) {
        RUN_SERVICE.onSuccessfulPortalContinueToHub(runState, this::setDirty, level, player);
    }

    public DungeonRunService.PortalCommandOperationResult continueFromPortalCommand(
        ServerLevel level,
        ServerPlayer player,
        Runnable generatedFloorCleanup
    ) {
        return RUN_SERVICE.continueFromPortalCommand(
            runState, this::setDirty, level, player, generatedFloorCleanup
        );
    }

    public DungeonRunService.PortalCommandOperationResult continueFromPortalCommand(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        ServerPlayer player
    ) {
        DungeonRunState state = exactState(key);
        if (state == null) {
            return new DungeonRunService.PortalCommandOperationResult(
                "INSTANCE_RUN_OWNER_MISSING", null
            );
        }
        DungeonInstanceAllocationData.TerminalDispositionResult terminal =
            beginTerminalDisposition(level, key, "INSTANCE_PORTAL_CONTINUE");
        if (!terminal.accepted()) {
            return new DungeonRunService.PortalCommandOperationResult(terminal.reason(), null);
        }
        DungeonRunService.PortalCommandOperationResult result =
            RUN_SERVICE.continueFromPortalCommand(
            state,
            this::setDirty,
            level,
            player,
            () -> GeneratedFloorCleanupProcessor.beginExactInstanceCleanup(level, key)
        );
        if (result.succeeded()) {
            consumeDeferredReturn(level, player.getUUID(), key);
        }
        return result;
    }

    public DungeonRunService.PortalCommandOperationResult returnHomeFromPortalCommand(
        ServerLevel level,
        ServerPlayer player,
        Runnable generatedFloorCleanup
    ) {
        return RUN_SERVICE.returnHomeFromPortalCommand(
            runState, this::setDirty, level, player, generatedFloorCleanup
        );
    }

    public DungeonRunService.PortalCommandOperationResult returnHomeFromPortalCommand(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        ServerPlayer player
    ) {
        DungeonRunState state = exactState(key);
        if (state == null) {
            return new DungeonRunService.PortalCommandOperationResult(
                "INSTANCE_RUN_OWNER_MISSING", null
            );
        }
        DungeonInstanceAllocationData.TerminalDispositionResult terminal =
            beginTerminalDisposition(level, key, "INSTANCE_PORTAL_RETURN_HOME");
        if (!terminal.accepted()) {
            return new DungeonRunService.PortalCommandOperationResult(terminal.reason(), null);
        }
        DungeonRunService.PortalCommandOperationResult result =
            RUN_SERVICE.returnHomeFromPortalCommand(
            state,
            this::setDirty,
            level,
            player,
            () -> GeneratedFloorCleanupProcessor.beginExactInstanceCleanup(level, key)
        );
        if (result.succeeded()) {
            consumeDeferredReturn(level, player.getUUID(), key);
        }
        return result;
    }

    public ProtectedHostEvacuationResult evacuateProtectedHost(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        ServerPlayer player,
        DungeonPreparationLoadoutData.BraceletReservation bracelet
    ) {
        return completeRegisteredHostDeparture(
            key, level, player, bracelet, null,
            "RESURRECTION_BRACELET_HOST_EVACUATION",
            "PROTECTED_HOST_ALLOCATION_INVALID",
            "PROTECTED_HOST_BRACELET_COMMIT_FAILED",
            "HOST_BRACELET_EVACUATED"
        );
    }

    public ProtectedHostEvacuationResult departHostWithWhistle(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        ServerPlayer player,
        DungeonPreparationLoadoutData.WhistleReservation whistle
    ) {
        return completeRegisteredHostDeparture(
            key, level, player, null, whistle,
            "RETURN_WHISTLE_HOST_DEPARTURE",
            "WHISTLE_HOST_ALLOCATION_INVALID",
            "HOST_WHISTLE_DEPARTED_ITEM_COMMIT_FAILED",
            "HOST_WHISTLE_DEPARTED"
        );
    }

    private ProtectedHostEvacuationResult completeRegisteredHostDeparture(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        ServerPlayer player,
        @Nullable DungeonPreparationLoadoutData.BraceletReservation hostBracelet,
        @Nullable DungeonPreparationLoadoutData.WhistleReservation hostWhistle,
        String terminalReason,
        String invalidReason,
        String commitFailedReason,
        String successReason
    ) {
        DungeonRunState state = exactState(key);
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(level).forIdentity(key).orElse(null);
        if (state == null || allocation == null || player == null
            || (hostBracelet == null) == (hostWhistle == null)
            || !state.isActiveRun()
            || !allocation.lifecycle().state().allowsProtectedEvacuation()
            || allocation.entryDispatch() == null
            || allocation.entryDispatch().state()
                != DungeonInstanceAllocationData.DispatchState.ADMITTED
            || !allocation.dimension().equals(level.dimension().location().toString())
            || !allocation.host().equals(player.getUUID())
            || !allocation.roster().contains(player.getUUID())
            || allocation.soloPreparation() == null
            || !allocation.soloPreparation().hostAndSubordinateRoster(player.getUUID())) {
            return ProtectedHostEvacuationResult.rejected(invalidReason);
        }
        DungeonPreparationLoadoutData loadouts = DungeonPreparationLoadoutData.get(level);
        boolean hostReservationValid = hostBracelet != null
            ? loadouts.validateBraceletReservation(
                player.getUUID(), player, hostBracelet.acceptedRevision(),
                allocation.soloPreparation().requestedFloor()
            ).filter(current -> current.sameReference(hostBracelet)).isPresent()
            : loadouts.validateWhistleReservation(
                player.getUUID(), player, hostWhistle.acceptedRevision(),
                allocation.soloPreparation().requestedFloor()
            ).filter(current -> current.sameReference(hostWhistle)).isPresent();
        if (!hostReservationValid) {
            return ProtectedHostEvacuationResult.rejected(invalidReason);
        }
        DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);
        DungeonSubordinateTransportData.TerminalEscapePlan escapePlan =
            transport.prepareTerminalEscape(level, allocation, player);
        if (!escapePlan.accepted()) {
            return ProtectedHostEvacuationResult.rejected(escapePlan.reason());
        }
        DungeonInstanceAllocationData.TerminalDispositionResult terminal =
            beginTerminalDisposition(level, key, terminalReason);
        if (!terminal.accepted()) {
            return ProtectedHostEvacuationResult.rejected(terminal.reason());
        }
        DungeonSubordinateTransportData.TerminalEscapeStage escapeStage =
            transport.stageTerminalEscape(level, terminal.allocation(), escapePlan);
        if (!escapeStage.accepted()) {
            return ProtectedHostEvacuationResult.rejected(escapeStage.reason());
        }
        DungeonInstanceRecoveryService.ResumeResult returned =
            DungeonInstanceRecoveryService.returnMemberToHubNow(player, key, terminalReason);
        if (!returned.succeeded()) {
            transport.rollbackTerminalEscape(escapeStage);
            return ProtectedHostEvacuationResult.rejected(returned.reason());
        }
        DungeonPostEvacuationGuard.arm(player, key);
        List<DungeonPreparationLoadoutData.BraceletReservation> bracelets =
            new ArrayList<>(escapePlan.bracelets());
        List<DungeonPreparationLoadoutData.WhistleReservation> whistles =
            new ArrayList<>(escapePlan.whistles());
        if (hostBracelet != null) {
            bracelets.add(hostBracelet);
        } else {
            whistles.add(hostWhistle);
        }
        Map<UUID, net.minecraft.world.Container> protectionInventories =
            new LinkedHashMap<>(escapePlan.inventories(player));
        protectionInventories.put(player.getUUID(), player.getInventory());
        boolean consumed = loadouts.consumeTerminalReservations(
            bracelets, whistles, protectionInventories
        );
        DungeonSubordinateTransportData.TransportResult subordinateCommit =
            transport.commitTerminalEscape(level, escapeStage);
        if (!subordinateCommit.accepted()) {
            return ProtectedHostEvacuationResult.accepted(
                successReason + "_SUBORDINATE_DISPOSITION_DEFERRED_" + subordinateCommit.reason()
            );
        }
        consumeDeferredReturn(level, player.getUUID(), key);
        RUN_SERVICE.endRunWithControllerCleanup(state, this::setDirty, level);
        GeneratedFloorCleanupProcessor.BeginCleanupResult cleanup =
            GeneratedFloorCleanupProcessor.beginExactInstanceCleanup(level, key);
        if (!consumed) {
            return ProtectedHostEvacuationResult.accepted(commitFailedReason);
        }
        return ProtectedHostEvacuationResult.accepted(
            cleanup.accepted()
                ? successReason
                : successReason + "_CLEANUP_DEFERRED_" + cleanup.reason()
        );
    }

    public boolean abandonInstance(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        ServerPlayer player,
        String reason
    ) {
        DungeonRunState state = exactState(key);
        if (state == null) {
            return false;
        }
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation allocation =
            allocations.forIdentity(key).orElse(null);
        if (allocation != null
            && allocation.lifecycle().state()
                == DungeonInstanceAllocationData.LifecycleState.READY
            && allocation.soloPreparation() != null
            && allocation.roster().equals(Set.of(player.getUUID()))
            && (allocation.entryDispatch() == null
                || allocation.entryDispatch().state()
                    != DungeonInstanceAllocationData.DispatchState.ADMITTED)) {
            return abandonUndispatchedReadyInstance(
                allocations, allocation, state, level, player, reason
            );
        }
        DungeonInstanceAllocationData.TerminalDispositionResult terminal =
            beginTerminalDisposition(level, key, reason);
        if (!terminal.accepted()) {
            return false;
        }
        DungeonInstanceRecoveryService.ResumeResult returned =
            DungeonInstanceRecoveryService.returnMemberToHubNow(player, key, reason);
        if (!returned.succeeded()) {
            return false;
        }
        consumeDeferredReturn(level, player.getUUID(), key);
        invalidateExplicitRunReplay(state, level, player.getUUID());
        RUN_SERVICE.endRunWithControllerCleanup(state, this::setDirty, level);
        GeneratedFloorCleanupProcessor.BeginCleanupResult cleanup =
            GeneratedFloorCleanupProcessor.beginExactInstanceCleanup(level, key);
        return cleanup.accepted();
    }

    private void invalidateExplicitRunReplay(
        DungeonRunState state,
        ServerLevel level,
        UUID playerId
    ) {
        DungeonFloorProgressData.get(level).clearPreparedModifierPlan(
            playerId, state.getFloorNumber()
        );
        state.clearRetryCheckpoint();
        setDirty();
    }

    private boolean abandonUndispatchedReadyInstance(
        DungeonInstanceAllocationData allocations,
        DungeonInstanceAllocationData.Allocation allocation,
        DungeonRunState state,
        ServerLevel level,
        ServerPlayer player,
        String reason
    ) {
        return expireUndispatchedReadyInstance(
            allocations, allocation, state, level, player, reason
        );
    }

    public boolean expireUndispatchedReadyInstance(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        String reason
    ) {
        DungeonRunState state = exactState(key);
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation allocation =
            allocations.forIdentity(key).orElse(null);
        if (state == null || allocation == null
            || allocation.lifecycle().state()
                != DungeonInstanceAllocationData.LifecycleState.READY
            || allocation.soloPreparation() == null
            || allocation.roster().size() != 1
            || allocation.entryDispatch() != null
                && allocation.entryDispatch().state()
                    == DungeonInstanceAllocationData.DispatchState.ADMITTED) {
            return false;
        }
        UUID hostId = allocation.roster().iterator().next();
        ServerPlayer onlineHost = level.getServer().getPlayerList().getPlayer(hostId);
        return expireUndispatchedReadyInstance(
            allocations, allocation, state, level, onlineHost, reason
        );
    }

    private boolean expireUndispatchedReadyInstance(
        DungeonInstanceAllocationData allocations,
        DungeonInstanceAllocationData.Allocation allocation,
        DungeonRunState state,
        ServerLevel level,
        @Nullable ServerPlayer onlineHost,
        String reason
    ) {
        if (onlineHost != null
            && !RUN_SERVICE.returnPlayerToHub(
                state, this::setDirty, level, onlineHost, reason
            )) {
            return false;
        }
        UUID hostId = allocation.roster().iterator().next();
        DungeonInstanceAllocationData.TerminalDispositionResult terminal =
            allocations.beginUndispatchedReadyDisposition(
                allocation.key(), hostId, reason, level.getGameTime()
            );
        if (!terminal.accepted()) {
            return false;
        }
        level.getServer().overworld().getDataStorage().save();
        invalidateExplicitRunReplay(state, level, hostId);
        RUN_SERVICE.endRunWithControllerCleanup(state, this::setDirty, level);
        GeneratedFloorCleanupProcessor.BeginCleanupResult cleanup =
            GeneratedFloorCleanupProcessor.beginExactInstanceCleanup(level, allocation.key());
        return cleanup.accepted();
    }

    public boolean closeDeferredTerminalInstance(
        DungeonInstanceAllocationData.InstanceKey key,
        ServerLevel level,
        ServerPlayer player
    ) {
        DungeonRunState state = exactState(key);
        if (state == null || !state.isRunCompleted()) {
            return false;
        }
        CoreRecoveryData.get(level).finalizeForDeparture(
            player.getUUID(), state.getRunSessionId(), state.getFloorNumber()
        );
        RUN_SERVICE.onSuccessfulPortalContinueToHub(
            state, this::setDirty, level, player
        );
        consumeDeferredReturn(level, player.getUUID(), key);
        return GeneratedFloorCleanupProcessor.beginExactInstanceCleanup(level, key)
            .accepted();
    }

    private DungeonInstanceAllocationData.TerminalDispositionResult beginTerminalDisposition(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey key,
        String reason
    ) {
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation current =
            allocations.forIdentity(key).orElse(null);
        if (current == null) {
            return DungeonInstanceAllocationData.TerminalDispositionResult.rejected(
                "TERMINAL_ALLOCATION_MISSING", null
            );
        }
        DungeonInstanceAllocationData.LifecycleState state = current.lifecycle().state();
        if (state != DungeonInstanceAllocationData.LifecycleState.ACTIVE
            && state != DungeonInstanceAllocationData.LifecycleState.SUSPENDED
            && state != DungeonInstanceAllocationData.LifecycleState.COMPLETING
            && state != DungeonInstanceAllocationData.LifecycleState.CLEANING) {
            return DungeonInstanceAllocationData.TerminalDispositionResult.rejected(
                "TERMINAL_LIFECYCLE_INVALID_" + state.name(), current
            );
        }
        if (current.entryDispatch() == null) {
            return DungeonInstanceAllocationData.TerminalDispositionResult.rejected(
                "DEFERRED_HUB_RETURN_DISPATCH_MISSING", current
            );
        }
        DungeonDeferredReturnData deferredReturns = DungeonDeferredReturnData.get(level);
        String reconciliationFailure = reconcileRetiredDeferredReturns(
            allocations, deferredReturns, current
        );
        if (reconciliationFailure != null) {
            return DungeonInstanceAllocationData.TerminalDispositionResult.rejected(
                reconciliationFailure, current
            );
        }
        DungeonInstanceAllocationData.TerminalDispositionResult terminal =
            allocations.beginTerminalDisposition(key, reason, level.getGameTime());
        if (!terminal.accepted() || terminal.allocation() == null
            || !deferredReturns.recordAllocation(
                terminal.allocation(), reason, level.getGameTime()
            )) {
            return DungeonInstanceAllocationData.TerminalDispositionResult.rejected(
                "DEFERRED_HUB_RETURN_PERSISTENCE_FAILED", terminal.allocation()
            );
        }
        level.getServer().overworld().getDataStorage().save();
        return terminal;
    }

    private String reconcileRetiredDeferredReturns(
        DungeonInstanceAllocationData allocations,
        DungeonDeferredReturnData deferredReturns,
        DungeonInstanceAllocationData.Allocation current
    ) {
        Map<UUID, DungeonDeferredReturnData.DeferredReturn> conflicts =
            deferredReturns.conflictsFor(current);
        for (Map.Entry<UUID, DungeonDeferredReturnData.DeferredReturn> conflict
            : conflicts.entrySet()) {
            DungeonInstanceAllocationData.InstanceKey previousKey =
                conflict.getValue().instanceKey();
            DungeonInstanceAllocationData.Allocation previous =
                allocations.forIdentity(previousKey).orElse(null);
            if (previous != null && !previous.lifecycle().state().reusable()) {
                return "DEFERRED_HUB_RETURN_PREVIOUS_ALLOCATION_LIVE";
            }
            if (!canRetireInstance(previousKey)) {
                return "DEFERRED_HUB_RETURN_PREVIOUS_RUN_LIVE";
            }
        }
        for (Map.Entry<UUID, DungeonDeferredReturnData.DeferredReturn> conflict
            : conflicts.entrySet()) {
            DungeonInstanceAllocationData.InstanceKey previousKey =
                conflict.getValue().instanceKey();
            if (!deferredReturns.consume(conflict.getKey(), previousKey)) {
                return "DEFERRED_HUB_RETURN_RECONCILIATION_CHANGED";
            }
            LOGGER.warn(
                "Reconciled retired deferred dungeon return before terminal disposition: player={} previousInstance={} previousEpoch={} currentInstance={} currentEpoch={}",
                conflict.getKey(), previousKey.instanceId(), previousKey.epoch(),
                current.key().instanceId(), current.key().epoch()
            );
        }
        return null;
    }

    private static void consumeDeferredReturn(
        ServerLevel level,
        UUID playerId,
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        if (DungeonDeferredReturnData.get(level).consume(playerId, key)) {
            level.getServer().overworld().getDataStorage().save();
        }
    }

    public record ProtectedHostEvacuationResult(boolean accepted, String reason) {
        static ProtectedHostEvacuationResult accepted(String reason) {
            return new ProtectedHostEvacuationResult(true, reason);
        }

        static ProtectedHostEvacuationResult rejected(String reason) {
            return new ProtectedHostEvacuationResult(false, reason);
        }
    }

    public void onFailedFloorReturnToHub(ServerLevel level, @Nullable ServerPlayer player) {
        RUN_SERVICE.onFailedFloorReturnToHub(runState, this::setDirty, level, player);
    }

    public boolean prepareFloorFromHub(ServerLevel level, int requestedFloor) {
        return RUN_SERVICE.prepareFloorFromHub(runState, this::setDirty, level, requestedFloor);
    }

    public DungeonRunService.HubFloorPreparationResult prepareFloorFromHubCommand(
        ServerLevel level,
        UUID playerId,
        int requestedFloor
    ) {
        return RUN_SERVICE.prepareFloorFromHubCommand(
            runState, this::setDirty, level, DungeonFloorProgressData.get(level), playerId, requestedFloor
        );
    }

    public void markHubPresetPrepared(int rooms) {
        RUN_SERVICE.markHubPresetPrepared(runState, this::setDirty, rooms);
    }

    public void setHubGateState(String state, String reason) {
        RUN_SERVICE.setHubGateState(runState, this::setDirty, state, reason);
    }

    public int getCurrentFloorBaseBudget() { return DungeonBudgetEngine.calculateFloorBaseBudget(runState.getFloorNumber()); }
    public String getRunProgressSummary() { return runState.getRunProgressSummary(); }
    public String getActiveRoomPositionsSummary() { return runState.getActiveRoomPositionsSummary(); }
    public String getCompletedRoomPositionsSummary() { return runState.getCompletedRoomPositionsSummary(); }
    public boolean isRoomRegisteredActive(BlockPos pos) { return runState.isRoomRegisteredActive(pos); }
    public boolean isRoomRegisteredCompleted(BlockPos pos) { return runState.isRoomRegisteredCompleted(pos); }
    public Set<BlockPos> getActiveRoomControllerPositions() { return runState.getActiveRoomControllerPositions(); }
    public Set<BlockPos> getCompletedRoomControllerPositions() { return runState.getCompletedRoomControllerPositions(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (runStates.isEmpty()) {
            runState.save(tag);
            return tag;
        }
        ListTag instances = new ListTag();
        runStates.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(INSTANCE_KEY_ORDER))
            .forEach(entry -> {
                CompoundTag instanceTag = new CompoundTag();
                entry.getKey().save(instanceTag);
                CompoundTag runStateTag = new CompoundTag();
                entry.getValue().save(runStateTag);
                instanceTag.put(RUN_STATE_KEY, runStateTag);
                instances.add(instanceTag);
            });
        tag.put(INSTANCES_KEY, instances);
        return tag;
    }

    public static ExitPortalPlacementResult analyzeExitPortalPlacement(ServerLevel level, BlockPos controllerPos, @Nullable RoomControllerBlockEntity controller) {
        DungeonRunService.ExitPortalPlacementResult result = RUN_SERVICE.analyzeExitPortalPlacement(level, controllerPos, controller);
        return new ExitPortalPlacementResult(result.ready(), result.portalPos(), result.source(), result.reason(), result.details());
    }

    public record ExitPortalPlacementResult(boolean ready, @Nullable BlockPos portalPos, String source, String reason, String details) {
    }
}
