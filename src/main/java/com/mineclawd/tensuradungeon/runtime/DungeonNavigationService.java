package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.network.DungeonNavigationPayload;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class DungeonNavigationService {
    private DungeonNavigationService() {}

    public static void sync(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!NetworkRegistry.hasChannel(player.connection, DungeonNavigationPayload.TYPE.id())) continue;
            PacketDistributor.sendToPlayer(player, new DungeonNavigationPayload(targets(level, player)));
        }
    }

    private static List<DungeonNavigationPayload.Target> targets(ServerLevel level, ServerPlayer player) {
        DungeonRunManager run = DungeonRunManager.get(level);
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(level).forPlayer(player.getUUID())
                .map(DungeonInstanceAllocationData.Allocation::key)
                .orElse(null);
        if (!canResolveInstanceNavigation(
            instanceKey,
            instanceKey != null && run.isActiveRun(instanceKey),
            player.isSpectator()
        )) return List.of();
        List<DungeonNavigationPayload.Target> exitPortal = exitPortalTarget(
            run.isExitPortalActive(instanceKey), run.getExitPortalPos(instanceKey)
        );
        if (!exitPortal.isEmpty()) return exitPortal;
        if (DungeonHudHooks.current(level, player).objectiveActive()) return List.of();
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = GeneratedFloorRepository.get(
            level, level.dimension().location().toString(), instanceKey
        ).hydrate().placement();
        if (placement == null) return List.of();
        if (placement.frontier() != null && !placement.frontier().candidates().isEmpty()) {
            return placement.frontier().candidates().stream()
                .map(candidate -> new DungeonNavigationPayload.Target(candidate.sourceExit(), true, false))
                .distinct().limit(4).toList();
        }
        BlockPos next = run.getPresetExpectedControllerPos(instanceKey);
        return next == null ? List.of() : List.of(new DungeonNavigationPayload.Target(next, false, false));
    }

    static List<DungeonNavigationPayload.Target> exitPortalTarget(boolean active, @Nullable BlockPos portalPos) {
        return active && portalPos != null
            ? List.of(new DungeonNavigationPayload.Target(portalPos, false, true))
            : List.of();
    }

    static boolean canResolveInstanceNavigation(
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey,
        boolean exactRunActive,
        boolean spectator
    ) {
        return instanceKey != null && exactRunActive && !spectator;
    }
}
