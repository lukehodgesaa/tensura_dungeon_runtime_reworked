package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.DungeonRunService;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

interface RoomRunGateway {
    static RoomRunGateway production(
        ServerLevel level,
        BlockPos controllerPos,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return new ProductionRoomRunGateway(level, controllerPos, instanceKey);
    }

    boolean isActiveRun();

    boolean isDebugRoomActivationOverride();

    boolean isRunCompleted();

    boolean isFailed();

    boolean isPresetMode();

    boolean isHubEnabled();

    boolean isPlayerInsideHubSafeZone(@Nullable BlockPos playerPos);

    boolean isRoomRegisteredActive();

    boolean isRoomRegisteredCompleted();

    String presetActivationBlockReason();

    Set<BlockPos> activeRoomControllerPositions();

    String activateRoom();

    int remainingRoomCount();

    CompletionResult completeRoom(String completionReason);

    void recordCoreRecoveryEncounter(
        ServerPlayer rewardPlayer,
        DungeonRunService.FloorCompletionContext context
    );

    void applyFloorCompletionEffects(
        ServerPlayer rewardPlayer, CompletionResult result, DungeonRunService.FloorCompletionContext context
    );

    void failRoom(String reason);

    void returnFailedFloorToHub(@Nullable ServerPlayer player);

    int floorNumber();

    String modifierIdsSummary();

    String runSessionId();

    String completionReason();

    record CompletionResult(boolean floorCompletedNow, int floorNumber, String runSessionId, String completionReason) {
    }
}

final class ProductionRoomRunGateway implements RoomRunGateway {
    private final ServerLevel level;
    private final BlockPos controllerPos;
    private final DungeonRunManager manager;
    private final DungeonInstanceAllocationData.InstanceKey instanceKey;

    ProductionRoomRunGateway(
        ServerLevel level,
        BlockPos controllerPos,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        this.level = level;
        this.controllerPos = controllerPos;
        this.manager = DungeonRunManager.get(level);
        this.instanceKey = instanceKey;
    }

    @Override
    public boolean isActiveRun() {
        return manager.isActiveRun(instanceKey);
    }

    @Override
    public boolean isDebugRoomActivationOverride() {
        return manager.isDebugRoomActivationOverride(instanceKey);
    }

    @Override
    public boolean isRunCompleted() {
        return manager.isRunCompleted(instanceKey);
    }

    @Override
    public boolean isFailed() {
        return manager.isFailed(instanceKey);
    }

    @Override
    public boolean isPresetMode() {
        return manager.isPresetMode(instanceKey);
    }

    @Override
    public boolean isHubEnabled() {
        return manager.isHubEnabled(instanceKey);
    }

    @Override
    public boolean isPlayerInsideHubSafeZone(@Nullable BlockPos playerPos) {
        return manager.isPlayerInsideHubSafeZone(instanceKey, playerPos);
    }

    @Override
    public boolean isRoomRegisteredActive() {
        return manager.isRoomRegisteredActive(instanceKey, controllerPos);
    }

    @Override
    public boolean isRoomRegisteredCompleted() {
        return manager.isRoomRegisteredCompleted(instanceKey, controllerPos);
    }

    @Override
    public String presetActivationBlockReason() {
        return manager.getPresetActivationBlockReason(instanceKey, controllerPos);
    }

    @Override
    public Set<BlockPos> activeRoomControllerPositions() {
        return manager.getActiveRoomControllerPositions(instanceKey);
    }

    @Override
    public String activateRoom() {
        return manager.onRoomActivated(instanceKey, level, controllerPos);
    }

    @Override
    public int remainingRoomCount() {
        return manager.getRemainingRoomCount(instanceKey);
    }

    @Override
    public CompletionResult completeRoom(String completionReason) {
        DungeonRunManager.RoomCompletionResult result =
            manager.onRoomCompleted(instanceKey, level, controllerPos, completionReason);
        return new CompletionResult(
            result.floorCompletedNow(),
            result.floorNumber(),
            result.runSessionId(),
            result.completionReason()
        );
    }

    @Override
    public void recordCoreRecoveryEncounter(
        ServerPlayer rewardPlayer,
        DungeonRunService.FloorCompletionContext context
    ) {
        if (rewardPlayer == null
            || !manager.isActiveRun(instanceKey)
            || manager.isRunCompleted(instanceKey)
            || manager.isRoomRegisteredCompleted(instanceKey, controllerPos)
            || !manager.isRoomRegisteredActive(instanceKey, controllerPos)
            || (manager.isPresetMode(instanceKey)
                && !"NONE".equals(manager.getPresetActivationBlockReason(instanceKey, controllerPos)))) {
            return;
        }
        DungeonRunService.FloorCompletionContext completionContext = context == null
            ? new DungeonRunService.FloorCompletionContext(controllerPos, null, null, null)
            : context;
        com.mineclawd.tensuradungeon.runtime.CoreRecoveryEncounterClassifier.Classification classification =
            com.mineclawd.tensuradungeon.runtime.CoreRecoveryEncounterClassifier.classify(
                manager.getFloorNumber(instanceKey),
                completionContext.roomProfile(),
                completionContext.objective()
            );
        if (!classification.eligible()) return;

        String encounterKey = level.dimension().location()
            + "@"
            + controllerPos.getX() + ","
            + controllerPos.getY() + ","
            + controllerPos.getZ();
        com.mineclawd.tensuradungeon.runtime.CoreRecoveryData.get(level).recordEncounter(
            rewardPlayer.getUUID(),
            manager.getRunSessionId(instanceKey),
            manager.getFloorNumber(instanceKey),
            manager.getRunSeed(instanceKey),
            new com.mineclawd.tensuradungeon.runtime.CoreRecoveryData.EncounterReceipt(
                encounterKey,
                manager.getFloorNumber(instanceKey),
                completionContext.roomProfile(),
                completionContext.objective(),
                classification.kind()
            )
        );
    }

    @Override
    public void applyFloorCompletionEffects(
        ServerPlayer rewardPlayer, CompletionResult result, DungeonRunService.FloorCompletionContext context
    ) {
        manager.applyFloorCompletionEffects(
            instanceKey, level, rewardPlayer, result.floorNumber(), result.runSessionId(),
            result.completionReason(), context
        );
    }

    @Override
    public void failRoom(String reason) {
        manager.onRoomFailed(instanceKey, level, controllerPos, reason);
    }

    @Override
    public void returnFailedFloorToHub(@Nullable ServerPlayer player) {
        manager.preserveFailedFloorForRetry(instanceKey, level, player);
    }

    @Override
    public int floorNumber() {
        return manager.getFloorNumber(instanceKey);
    }

    @Override
    public String modifierIdsSummary() {
        return manager.getModifierIdsSummary(instanceKey);
    }

    @Override
    public String runSessionId() {
        return manager.getRunSessionId(instanceKey);
    }

    @Override
    public String completionReason() {
        return manager.getCompletionReason(instanceKey);
    }
}
