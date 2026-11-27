package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.command.GeneratedLinearConnectorPlacementCommands;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorLazyPlacementService;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

interface GeneratedRoomHooks {
    static GeneratedRoomHooks production(
        ServerLevel level,
        BlockPos controllerPos,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return new ProductionGeneratedRoomHooks(level, controllerPos, instanceKey);
    }

    boolean isFrontierCandidate();

    boolean commitFrontierOnEntry();

    boolean isFirstGeneratedRoom();

    void closeHubGateAfterFirstRoomEntry();

    boolean openSuccessorIncomingSeal();

    void retainTerminalOutgoingSeal();

    void resolvePlayerDirectedExitAndPlaceNext(@Nullable ServerPlayer player);
}

final class ProductionGeneratedRoomHooks implements GeneratedRoomHooks {
    private final ServerLevel level;
    private final BlockPos controllerPos;
    private final DungeonInstanceAllocationData.InstanceKey instanceKey;

    ProductionGeneratedRoomHooks(
        ServerLevel level,
        BlockPos controllerPos,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        this.level = level;
        this.controllerPos = controllerPos;
        this.instanceKey = instanceKey;
    }

    @Override
    public boolean isFrontierCandidate() {
        return GeneratedFloorLazyPlacementService.isFrontierCandidate(
            level, instanceKey, controllerPos
        );
    }

    @Override
    public boolean commitFrontierOnEntry() {
        return GeneratedFloorLazyPlacementService.commitFrontierOnEntry(
            level, instanceKey, controllerPos
        );
    }

    @Override
    public boolean isFirstGeneratedRoom() {
        return GeneratedLinearConnectorPlacementCommands.isFirstGeneratedRoom(
            level, instanceKey, controllerPos
        );
    }

    @Override
    public void closeHubGateAfterFirstRoomEntry() {
        DungeonInstanceAllocationData.Allocation allocation = instanceKey == null
            ? null
            : DungeonInstanceAllocationData.get(level).forIdentity(instanceKey).orElse(null);
        if (instanceKey == null || allocation != null && allocation.soloPreparation() == null) {
            GeneratedLinearConnectorPlacementCommands.closeHubGateAfterFirstRoomEntry(
                level, instanceKey, controllerPos
            );
        }
    }

    @Override
    public boolean openSuccessorIncomingSeal() {
        return GeneratedFloorLazyPlacementService.openSuccessorIncomingSeal(
            level, instanceKey, controllerPos
        );
    }

    @Override
    public void retainTerminalOutgoingSeal() {
        GeneratedFloorLazyPlacementService.retainTerminalOutgoingSeal(
            level, instanceKey, controllerPos
        );
    }

    @Override
    public void resolvePlayerDirectedExitAndPlaceNext(@Nullable ServerPlayer player) {
        GeneratedFloorLazyPlacementService.resolvePlayerDirectedExitAndPlaceNext(
            level, instanceKey, controllerPos, player
        );
    }
}
