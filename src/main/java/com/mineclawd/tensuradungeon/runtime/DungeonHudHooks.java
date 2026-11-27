package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.RoomHudSnapshot;
import com.mineclawd.tensuradungeon.block.entity.RoomObjectiveType;
import com.mineclawd.tensuradungeon.block.entity.RoomProtectionView;
import com.mineclawd.tensuradungeon.compat.tensura.RecoveryOverdrawService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonHudHooks {
    private DungeonHudHooks() {
    }

    public static DungeonHudSnapshot current(ServerLevel level, ServerPlayer player) {
        DungeonRunManager run = DungeonRunManager.get(level);
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(level).forPlayer(player.getUUID())
                .map(DungeonInstanceAllocationData.Allocation::key)
                .orElse(null);
        long runElapsedTicks = runElapsedTicks(
            DungeonFloorProgressData.get(level).getOrCreate(player.getUUID()).activeRunStart(),
            run.isActiveRun(instanceKey),
            run.getFloorNumber(instanceKey),
            run.getRunSessionId(instanceKey),
            level.getGameTime()
        );
        RecoveryOverdrawService.Status recovery = recoveryStatus(player, level);
        RoomControllerBlockEntity controller = findPlayerController(level, player, instanceKey);
        if (controller == null) {
            return new DungeonHudSnapshot(
                false,
                run.getFloorNumber(instanceKey),
                null,
                "NONE",
                "NONE",
                "No active room objective",
                0,
                0,
                0,
                0,
                0,
                0,
                run.getModifierIdsSummary(instanceKey),
                run.getModifierDisplayNamesSummary(instanceKey),
                "NONE",
                0,
                0,
                "NONE",
                0,
                0,
                0,
                0,
                "NONE",
                runElapsedTicks,
                level.getGameTime(),
                recovery.tier(),
                recoveryWindowPermille(recovery),
                recovery.lastCharge(),
                "NONE"
            );
        }

        RoomHudSnapshot room = controller.roomHudSnapshot(level);
        RoomObjectiveType objective = room.objectiveType();
        return new DungeonHudSnapshot(
            room.objectiveActive(),
            run.getFloorNumber(instanceKey),
            controller.getBlockPos(),
            room.roomState().name(),
            objective.name(),
            objectiveLabel(objective),
            room.objectiveProgress(),
            room.objectiveTarget(),
            room.objectiveRemainingTicks(),
            room.objectiveDurationTicks(),
            room.enemiesAlive(),
            room.enemiesSpawned(),
            run.getModifierIdsSummary(instanceKey),
            run.getModifierDisplayNamesSummary(instanceKey),
            room.appliedEnemyModifierSummary(),
            room.compositionSpent(),
            room.compositionBudget(),
            room.compositionSummary(),
            room.enemiesPending(),
            room.waveSize(),
            room.wavesSpawned(),
            room.waveCooldownTicks(),
            room.waveSummary(),
            runElapsedTicks,
            level.getGameTime(),
            recovery.tier(),
            recoveryWindowPermille(recovery),
            recovery.lastCharge(),
            room.modifierRoleSummary()
        );
    }

    static long runElapsedTicks(
        DungeonFloorProgressData.RunStartTelemetry start,
        boolean activeRun,
        int floor,
        String sessionId,
        long gameTime
    ) {
        if (!activeRun || start == null || !start.matches(floor, sessionId)) return 0L;
        return Math.max(0L, gameTime - start.gameTime());
    }

    private static RoomControllerBlockEntity findPlayerController(
        ServerLevel level,
        ServerPlayer player,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        for (BlockPos pos : DungeonRunManager.get(level)
            .getActiveRoomControllerPositions(instanceKey)) {
            if (!(level.getBlockEntity(pos) instanceof RoomControllerBlockEntity controller)) {
                continue;
            }
            RoomProtectionView protection = controller.protectionView(level);
            if (protection.hasBounds() && protection.bounds().contains(player.blockPosition())) {
                return controller;
            }
        }
        return null;
    }

    private static String objectiveLabel(RoomObjectiveType objective) {
        return switch (objective) {
            case CLEAR_ALL -> "Kill all the enemies in the room";
            case KILL_ELITE -> "Defeat the elite target";
            case SURVIVE_TIMER -> "Survive until the timer expires";
            case ACTIVATE_RUNES -> "Activate the rune crystals";
        };
    }

    private static RecoveryOverdrawService.Status recoveryStatus(ServerPlayer player, ServerLevel level) {
        return RecoveryOverdrawService.snapshot(player, level.getGameTime());
    }

    private static int recoveryWindowPermille(RecoveryOverdrawService.Status status) {
        return Math.max(0, (int) Math.round(
            status.rollingFraction() * 1000.0D
        ));
    }

    public record DungeonHudSnapshot(
        boolean objectiveActive,
        int floorNumber,
        BlockPos controllerPos,
        String roomState,
        String objectiveId,
        String objectiveLabel,
        int objectiveProgress,
        int objectiveTarget,
        int objectiveRemainingTicks,
        int objectiveDurationTicks,
        int enemiesAlive,
        int enemiesSpawned,
        String activeModifierIds,
        String activeModifierNames,
        String appliedEnemyModifierSummary,
        int compositionSpent,
        int compositionBudget,
        String compositionSummary,
        int enemiesPending,
        int waveSize,
        int wavesSpawned,
        int waveCooldownTicks,
        String waveSummary,
        long runElapsedTicks,
        long revisionGameTime,
        String recoveryTier,
        int recoveryWindowPermille,
        int recoveryLastCharge,
        String modifierRoleSummary
    ) {
        public DungeonHudSnapshot(
            boolean objectiveActive, int floorNumber, BlockPos controllerPos, String roomState,
            String objectiveId, String objectiveLabel, int objectiveProgress, int objectiveTarget,
            int objectiveRemainingTicks, int objectiveDurationTicks, int enemiesAlive,
            int enemiesSpawned, String activeModifierIds, String activeModifierNames,
            String appliedEnemyModifierSummary, int compositionSpent, int compositionBudget,
            String compositionSummary, int enemiesPending, int waveSize, int wavesSpawned,
            int waveCooldownTicks, String waveSummary, long runElapsedTicks, long revisionGameTime
        ) {
            this(
                objectiveActive, floorNumber, controllerPos, roomState, objectiveId, objectiveLabel,
                objectiveProgress, objectiveTarget, objectiveRemainingTicks, objectiveDurationTicks,
                enemiesAlive, enemiesSpawned, activeModifierIds, activeModifierNames,
                appliedEnemyModifierSummary, compositionSpent, compositionBudget, compositionSummary,
                enemiesPending, waveSize, wavesSpawned, waveCooldownTicks, waveSummary,
                runElapsedTicks, revisionGameTime, "STABLE", 0, 0, "NONE"
            );
        }

        public DungeonHudSnapshot {
            runElapsedTicks = Math.max(0L, runElapsedTicks);
            revisionGameTime = Math.max(0L, revisionGameTime);
            recoveryTier = recoveryTier == null || recoveryTier.isBlank() ? "STABLE" : recoveryTier;
            recoveryWindowPermille = Math.max(0, recoveryWindowPermille);
            recoveryLastCharge = Math.max(0, recoveryLastCharge);
            modifierRoleSummary = modifierRoleSummary == null || modifierRoleSummary.isBlank()
                ? "NONE" : modifierRoleSummary;
        }

        public String objectiveProgressSummary() {
            return objectiveProgress + " / " + objectiveTarget;
        }

        public String enemyModifierSummary() {
            return "active=" + activeModifierNames + " | applied=" + appliedEnemyModifierSummary;
        }

        public String modifierSummaryForObjectivePanel() {
            if ("NONE".equals(modifierRoleSummary)) return appliedEnemyModifierSummary;
            String summary = appliedEnemyModifierSummary + " | roles=" + modifierRoleSummary;
            return summary.length() <= 256 ? summary : summary.substring(0, 256);
        }
    }
}
