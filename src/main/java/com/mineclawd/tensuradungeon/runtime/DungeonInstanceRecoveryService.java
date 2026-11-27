package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public final class DungeonInstanceRecoveryService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long RECONNECT_RETRY_INTERVAL_TICKS = 20L;
    private static final Map<DungeonInstanceAllocationData.InstanceKey, Long>
        NEXT_RECONNECT_RETRY_GAME_TIME = new HashMap<>();
    private static final Map<DungeonInstanceAllocationData.InstanceKey, Long>
        NEXT_CLEANUP_HUB_RETURN_RETRY_GAME_TIME = new HashMap<>();

    private DungeonInstanceRecoveryService() {
    }

    public static boolean suspendOnLogout(ServerPlayer player) {
        DungeonInstanceAllocationData allocations =
            DungeonInstanceAllocationData.get(player.serverLevel());
        DungeonInstanceAllocationData.Allocation allocation =
            allocations.forPlayer(player.getUUID()).orElse(null);
        boolean subordinatesReturned = allocation == null
            || DungeonSubordinateTransportData.get(player.serverLevel())
                .returnToHub(player.serverLevel(), allocation).accepted();
        DungeonInstanceAllocationData.MemberDispositionResult result =
            allocations.markMemberDisconnected(
                player.getUUID(), player.serverLevel().getGameTime()
            );
        if (!result.accepted()) {
            return false;
        }
        player.serverLevel().getServer().overworld().getDataStorage().save();
        return subordinatesReturned;
    }

    public static boolean suspendAbsentSoloMember(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation allocation =
            allocations.forIdentity(instanceKey).orElse(null);
        if (allocation == null || allocation.roster().size() != 1
            || allocation.lifecycle().state()
                != DungeonInstanceAllocationData.LifecycleState.ACTIVE) {
            return allocation != null
                && allocation.lifecycle().state()
                    == DungeonInstanceAllocationData.LifecycleState.SUSPENDED;
        }
        UUID playerId = allocation.roster().iterator().next();
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null && !player.isRemoved()) {
            return false;
        }
        DungeonInstanceAllocationData.MemberDispositionResult result =
            allocations.markMemberDisconnected(playerId, level.getGameTime());
        if (result.accepted()) {
            level.getServer().overworld().getDataStorage().save();
        }
        return result.accepted();
    }

    public static ResumeResult resumeOnLogin(ServerPlayer player) {
        DungeonDeferredReturnData deferredReturns =
            DungeonDeferredReturnData.get(player.serverLevel());
        DungeonDeferredReturnData.DeferredReturn deferred =
            deferredReturns.forPlayer(player.getUUID()).orElse(null);
        if (deferred != null) {
            ServerLevel deferredLevel = resolveLevel(player, deferred.dimension());
            DungeonInstanceAllocationData.Allocation live =
                DungeonInstanceAllocationData.get(player.serverLevel())
                    .forIdentity(deferred.instanceKey())
                    .orElse(null);
            if (deferredLevel != null && live != null
                && live.lifecycle().state()
                    == DungeonInstanceAllocationData.LifecycleState.COMPLETING
                && DungeonRunManager.get(deferredLevel).isRunCompleted(
                    deferred.instanceKey()
                )
                && DungeonRunManager.get(deferredLevel)
                    .closeDeferredTerminalInstance(
                        deferred.instanceKey(), deferredLevel, player
                    )) {
                return ResumeResult.succeeded(
                    "DEFERRED_COMPLETED_INSTANCE_CLOSED",
                    deferred.hubTarget().above()
                );
            }
            return returnDeferredToHub(player, deferredReturns, deferred);
        }
        DungeonInstanceAllocationData allocations =
            DungeonInstanceAllocationData.get(player.serverLevel());
        DungeonInstanceAllocationData.Allocation allocation =
            allocations.forPlayer(player.getUUID()).orElse(null);
        if (allocation == null) {
            return ResumeResult.ignored("NO_LIVE_ALLOCATION");
        }
        return switch (allocation.memberDisposition(player.getUUID())) {
            case IN_INSTANCE -> ResumeResult.ignored("NO_DEFERRED_RELOCATION");
            case RETURN_TO_HUB_ON_LOGIN -> returnToHub(
                player, allocation, "RETURN_TO_HUB_ON_LOGIN"
            );
            case RECONNECT_TO_INSTANCE -> resumeInstance(player, allocation);
        };
    }

    public static void retryPendingResumes(ServerLevel level) {
        if (level.getGameTime() % 10L != 0L) {
            return;
        }
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        String dimension = level.dimension().location().toString();
        long gameTime = level.getGameTime();
        retryPendingCleanupHubReturns(level, allocations, dimension, gameTime);
        boolean matchedAny = false;
        for (DungeonInstanceAllocationData.Allocation allocation : allocations.allocations()) {
            if (!dimension.equals(allocation.dimension())
                || allocation.lifecycle().state()
                    != DungeonInstanceAllocationData.LifecycleState.SUSPENDED
                || allocation.memberDisposition(allocation.host())
                    != DungeonInstanceAllocationData.MemberDisposition.RECONNECT_TO_INSTANCE) {
                NEXT_RECONNECT_RETRY_GAME_TIME.remove(allocation.key());
                continue;
            }
            matchedAny = true;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(allocation.host());
            if (player == null || player.isRemoved()) {
                continue;
            }
            long nextAttempt = NEXT_RECONNECT_RETRY_GAME_TIME.getOrDefault(
                allocation.key(), Long.MIN_VALUE
            );
            if (gameTime < nextAttempt) {
                continue;
            }
            LOGGER.debug(
                "[TD-RECOVER] retry-pending-resume attempt dimension={} instance={} epoch={} gameTime={}",
                dimension, allocation.key().instanceId(), allocation.key().epoch(), gameTime
            );
            ResumeResult result = resumeInstance(player, allocation);
            if (result.succeeded() || !result.retryable()) {
                NEXT_RECONNECT_RETRY_GAME_TIME.remove(allocation.key());
                if (result.succeeded()) {
                    LOGGER.info(
                        "Dungeon reconnect resumed after exact subordinate recovery: player={} instance={} epoch={} destination={}",
                        player.getUUID(), allocation.key().instanceId(), allocation.key().epoch(),
                        result.destination()
                    );
                } else {
                    LOGGER.warn(
                        "Dungeon reconnect could not resume after exact subordinate recovery: player={} instance={} epoch={} reason={}",
                        player.getUUID(), allocation.key().instanceId(), allocation.key().epoch(),
                        result.reason()
                    );
                }
            } else {
                NEXT_RECONNECT_RETRY_GAME_TIME.put(
                    allocation.key(), gameTime + RECONNECT_RETRY_INTERVAL_TICKS
                );
            }
        }
        if (!matchedAny && gameTime % 100L == 0L) {
            LOGGER.debug(
                "[TD-RECOVER] retry-pending-resume no-match dimension={} gameTime={} allocationCount={}",
                dimension, gameTime, allocations.allocations().size()
            );
        }
    }

    private static void retryPendingCleanupHubReturns(
        ServerLevel level,
        DungeonInstanceAllocationData allocations,
        String dimension,
        long gameTime
    ) {
        for (DungeonInstanceAllocationData.Allocation allocation : allocations.allocations()) {
            if (!dimension.equals(allocation.dimension())
                || allocation.lifecycle().state() != DungeonInstanceAllocationData.LifecycleState.CLEANING
                || allocation.entryDispatch() == null) {
                NEXT_CLEANUP_HUB_RETURN_RETRY_GAME_TIME.remove(allocation.key());
                continue;
            }
            List<ServerPlayer> stranded = allocation.roster().stream()
                .map(level.getServer().getPlayerList()::getPlayer)
                .filter(player -> player != null && !player.isRemoved())
                .filter(player -> player.serverLevel() == level)
                .filter(player -> allocation.cell().bounds().contains(player.blockPosition()))
                .toList();
            if (stranded.isEmpty()) {
                NEXT_CLEANUP_HUB_RETURN_RETRY_GAME_TIME.remove(allocation.key());
                continue;
            }
            long nextAttempt = NEXT_CLEANUP_HUB_RETURN_RETRY_GAME_TIME.getOrDefault(
                allocation.key(), Long.MIN_VALUE
            );
            if (gameTime < nextAttempt) {
                continue;
            }
            for (ServerPlayer player : stranded) {
                BlockPos from = player.blockPosition();
                ResumeResult result = returnToHub(player, allocation, "CLEANUP_PLAYER_INSIDE_INSTANCE_CELL");
                if (result.succeeded()) {
                    LOGGER.warn(
                        "Re-applied cleanup hub return after displaced login: player={} instance={} epoch={} from={} target={}",
                        player.getUUID(), allocation.key().instanceId(), allocation.key().epoch(),
                        from, result.destination()
                    );
                    NEXT_CLEANUP_HUB_RETURN_RETRY_GAME_TIME.remove(allocation.key());
                } else {
                    NEXT_CLEANUP_HUB_RETURN_RETRY_GAME_TIME.put(
                        allocation.key(), gameTime + RECONNECT_RETRY_INTERVAL_TICKS
                    );
                    LOGGER.warn(
                        "Cleanup hub return still pending after displaced login: player={} instance={} epoch={} reason={}",
                        player.getUUID(), allocation.key().instanceId(), allocation.key().epoch(),
                        result.reason()
                    );
                }
            }
        }
    }

    private static ResumeResult returnDeferredToHub(
        ServerPlayer player,
        DungeonDeferredReturnData data,
        DungeonDeferredReturnData.DeferredReturn deferred
    ) {
        ServerLevel targetLevel = resolveLevel(player, deferred.dimension());
        if (targetLevel == null) {
            return ResumeResult.rejected("DEFERRED_HUB_DIMENSION_UNAVAILABLE");
        }
        BlockPos target = deferred.hubTarget().above();
        targetLevel.getChunkAt(target);
        player.teleportTo(
            targetLevel,
            target.getX() + 0.5D,
            target.getY(),
            target.getZ() + 0.5D,
            player.getYRot(),
            player.getXRot()
        );
        player.fallDistance = 0.0F;
        if (!data.consume(player.getUUID(), deferred.instanceKey())) {
            return ResumeResult.rejected("DEFERRED_HUB_RETURN_CONSUME_REJECTED");
        }
        targetLevel.getServer().overworld().getDataStorage().save();
        player.sendSystemMessage(Component.literal(
            "Your closed dungeon party returned you to the hub."
        ));
        return ResumeResult.succeeded("DEFERRED_HUB_RETURN_DELIVERED", target);
    }

    public static boolean suspendAndReturnToHub(
        ServerLevel level,
        ServerPlayer player,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        String reason
    ) {
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.MemberDispositionResult disposition =
            allocations.requireHubReturnOnLogin(
                instanceKey, player.getUUID(), reason, level.getGameTime()
            );
        if (!disposition.accepted() || disposition.allocation() == null) {
            player.sendSystemMessage(Component.literal(
                "Dungeon recovery stopped: the exact instance could not be suspended."
            ));
            return false;
        }
        level.getServer().overworld().getDataStorage().save();
        ResumeResult returned = returnToHub(player, disposition.allocation(), reason);
        if (!returned.succeeded()) {
            player.sendSystemMessage(Component.literal(
                "Dungeon recovery stopped: no verified reset anchor or hub return was available."
            ));
        }
        return returned.succeeded();
    }

    public static ResumeResult returnMemberToHubNow(
        ServerPlayer player,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        String reason
    ) {
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(player.serverLevel())
                .forIdentity(instanceKey)
                .orElse(null);
        return allocation == null
            ? ResumeResult.rejected("TERMINAL_ALLOCATION_MISSING")
            : returnToHub(player, allocation, reason);
    }

    public static ResumeResult recoverOutsideActiveBounds(
        ServerLevel level,
        ServerPlayer player,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos activeControllerPos,
        boolean allowRetainedFallback
    ) {
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation allocation =
            allocations.forIdentity(instanceKey).orElse(null);
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (allocation == null || !allocation.roster().contains(player.getUUID())
            || allocation.lifecycle().state()
                != DungeonInstanceAllocationData.LifecycleState.ACTIVE
            || !manager.isActiveRun(instanceKey)
            || !manager.getActiveRoomControllerPositions(instanceKey)
                .contains(activeControllerPos)) {
            return ResumeResult.rejected("ACTIVE_BOUNDS_RECOVERY_OWNERSHIP_INVALID");
        }

        if (allowRetainedFallback) {
            GeneratedFloorRuntimeData.LastGeneratedPlacement placement =
                GeneratedFloorRepository.get(
                    level, level.dimension().location().toString(), instanceKey
                ).hydrate().placement();
            List<BlockPos> retained = placement == null
                ? List.of() : placement.retainedRoute();
            for (BlockPos controllerPos : retainedCompletedRecoveryOrder(
                retained,
                manager.getCompletedRoomControllerPositions(instanceKey),
                activeControllerPos
            )) {
                if (!allocation.cell().bounds().contains(controllerPos)) continue;
                level.getChunkAt(controllerPos);
                if (!(level.getBlockEntity(controllerPos)
                    instanceof RoomControllerBlockEntity controller)
                    || !controller.ownsDungeonInstance(instanceKey)) {
                    continue;
                }
                BlockPos destination = controller
                    .resolveCompletedRoomRecoveryDestination(level, player)
                    .orElse(null);
                if (destination == null) continue;
                teleportToCheckpoint(player, level, destination);
                player.sendSystemMessage(Component.literal(
                    "The active-room reset point was unsafe. You were moved to the newest retained cleared room; dungeon progression is paused for 30 seconds while you return."
                ));
                LOGGER.warn(
                    "Recovered dungeon member to retained cleared room: player={} instance={} epoch={} controller={} destination={}",
                    player.getUUID(), instanceKey.instanceId(), instanceKey.epoch(),
                    controllerPos, destination
                );
                return ResumeResult.succeeded(
                    "RETAINED_CLEARED_ROOM_RECOVERY", destination
                );
            }

            BlockPos entry = verifiedEntryCheckpoint(player, level, allocation);
            if (entry != null) {
                teleportToCheckpoint(player, level, entry);
                player.sendSystemMessage(Component.literal(
                    "No retained cleared-room reset was safe. You were moved to the initial safe room; dungeon progression is paused for 30 seconds while you return."
                ));
                LOGGER.warn(
                    "Recovered dungeon member to initial safe room: player={} instance={} epoch={} destination={}",
                    player.getUUID(), instanceKey.instanceId(), instanceKey.epoch(), entry
                );
                return ResumeResult.succeeded("INITIAL_SAFE_ROOM_RECOVERY", entry);
            }
        }

        manager.onFailedFloorReturnToHub(instanceKey, level, player);
        DungeonInstanceAllocationData.LifecycleState terminalState = allocations
            .forIdentity(instanceKey)
            .map(DungeonInstanceAllocationData.Allocation::lifecycle)
            .map(DungeonInstanceAllocationData.Lifecycle::state)
            .orElse(DungeonInstanceAllocationData.LifecycleState.REUSABLE);
        if (terminalState == DungeonInstanceAllocationData.LifecycleState.COMPLETING
            || terminalState == DungeonInstanceAllocationData.LifecycleState.CLEANING
            || terminalState == DungeonInstanceAllocationData.LifecycleState.REUSABLE) {
            player.sendSystemMessage(Component.literal(
                "No safe dungeon recovery checkpoint remained. The run ended safely and cleanup started."
            ));
            return ResumeResult.succeeded(
                "ACTIVE_BOUNDS_RECOVERY_TERMINATED", player.blockPosition()
            );
        }
        return ResumeResult.rejected("ACTIVE_BOUNDS_TERMINAL_HANDOFF_FAILED");
    }

    static List<BlockPos> retainedCompletedRecoveryOrder(
        List<BlockPos> retainedRoute,
        Set<BlockPos> completedControllers,
        BlockPos activeControllerPos
    ) {
        List<BlockPos> ordered = new ArrayList<>();
        for (int index = retainedRoute.size() - 1; index >= 0; index--) {
            BlockPos candidate = retainedRoute.get(index);
            if (!candidate.equals(activeControllerPos)
                && completedControllers.contains(candidate)
                && !ordered.contains(candidate)) {
                ordered.add(candidate.immutable());
            }
        }
        return List.copyOf(ordered);
    }

    private static void teleportToCheckpoint(
        ServerPlayer player,
        ServerLevel level,
        BlockPos destination
    ) {
        player.teleportTo(
            level,
            destination.getX() + 0.5D,
            destination.getY(),
            destination.getZ() + 0.5D,
            player.getYRot(),
            player.getXRot()
        );
        player.fallDistance = 0.0F;
    }

    public static void returnMemberToPositionNow(
        ServerPlayer player,
        ServerLevel level,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
    ) {
        player.teleportTo(level, x, y, z, yaw, pitch);
        player.fallDistance = 0.0F;
    }

    private static ResumeResult resumeInstance(
        ServerPlayer player,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        if (allocation.lifecycle().state()
                != DungeonInstanceAllocationData.LifecycleState.SUSPENDED
            || allocation.entryDispatch() == null
            || allocation.entryDispatch().state()
                != DungeonInstanceAllocationData.DispatchState.ADMITTED) {
            ResumeResult rejected = ResumeResult.rejected("INSTANCE_NOT_RESUMABLE");
            LOGGER.warn(
                "[TD-RECOVER] resume rejected state={} dispatch={} reason={} instance={} epoch={}",
                allocation.lifecycle().state(),
                allocation.entryDispatch() == null ? "NULL" : allocation.entryDispatch().state(),
                rejected.reason(), allocation.key().instanceId(), allocation.key().epoch()
            );
            return rejected;
        }
        ServerLevel targetLevel = resolveLevel(player, allocation.dimension());
        if (targetLevel == null) {
            ResumeResult rejected = ResumeResult.rejected("INSTANCE_DIMENSION_UNAVAILABLE");
            LOGGER.warn(
                "[TD-RECOVER] resume rejected dimension={} reason={} instance={} epoch={}",
                allocation.dimension(), rejected.reason(),
                allocation.key().instanceId(), allocation.key().epoch()
            );
            return rejected;
        }
        DungeonRunManager manager = DungeonRunManager.get(targetLevel);
        if (!manager.isActiveRun(allocation.key())) {
            ResumeResult rejected = ResumeResult.rejected("EXACT_RUN_OWNER_NOT_ACTIVE");
            LOGGER.warn(
                "[TD-RECOVER] resume rejected activeRun={} reason={} instance={} epoch={}",
                manager.isActiveRun(allocation.key()), rejected.reason(),
                allocation.key().instanceId(), allocation.key().epoch()
            );
            return rejected;
        }

        RoomControllerBlockEntity selected = null;
        var activeControllers = manager.getActiveRoomControllerPositions(allocation.key());
        for (BlockPos controllerPos : activeControllers) {
            if (!allocation.cell().bounds().contains(controllerPos)) {
                return ResumeResult.rejected("ACTIVE_CONTROLLER_OUTSIDE_INSTANCE_CELL");
            }
            targetLevel.getChunkAt(controllerPos);
            if (!(targetLevel.getBlockEntity(controllerPos)
                instanceof RoomControllerBlockEntity controller)
                || !controller.ownsDungeonInstance(allocation.key())
                || !controller.isActiveRoomForRecovery()) {
                continue;
            }
            if (selected != null) {
                return ResumeResult.rejected("MULTIPLE_ACTIVE_INSTANCE_CONTROLLERS");
            }
            selected = controller;
        }
        BlockPos destination = selected == null && activeControllers.isEmpty()
            ? verifiedEntryCheckpoint(player, targetLevel, allocation)
            : selected == null
                ? null
                : selected.resolvePlayerRecoveryDestination(targetLevel, player).orElse(null);
        if (destination == null) {
            ResumeResult rejected = ResumeResult.rejected(
                activeControllers.isEmpty()
                    ? "ENTRY_CHECKPOINT_UNSAFE"
                    : "ACTIVE_ROOM_RESET_DESTINATION_UNSAFE"
            );
            LOGGER.warn(
                "[TD-RECOVER] resume rejected activeControllers={} reason={} instance={} epoch={}",
                activeControllers.size(), rejected.reason(),
                allocation.key().instanceId(), allocation.key().epoch()
            );
            return rejected;
        }
        DungeonSubordinateTransportData transport =
            DungeonSubordinateTransportData.get(targetLevel);
        if (!transport.canResumeAtCheckpoint(targetLevel, allocation)) {
            ResumeResult retrying = ResumeResult.retrying("SUBORDINATE_RESUME_IDENTITY_UNAVAILABLE");
            LOGGER.info(
                "[TD-RECOVER] resume retrying subordinate checkpoint unavailable instance={} epoch={}",
                allocation.key().instanceId(), allocation.key().epoch()
            );
            return retrying;
        }
        DungeonSubordinateTransportData.TransportResult subordinates =
            transport.resumeAtCheckpoint(targetLevel, allocation, destination);
        if (!subordinates.accepted()) {
            ResumeResult result = retryableSubordinateResumeReason(subordinates.reason())
                ? ResumeResult.retrying(subordinates.reason())
                : ResumeResult.rejected(subordinates.reason());
            LOGGER.warn(
                "[TD-RECOVER] resume subordinate transport rejected reason={} retryable={} instance={} epoch={}",
                subordinates.reason(), result.retryable(),
                allocation.key().instanceId(), allocation.key().epoch()
            );
            return result;
        }
        player.teleportTo(
            targetLevel,
            destination.getX() + 0.5D,
            destination.getY(),
            destination.getZ() + 0.5D,
            player.getYRot(),
            player.getXRot()
        );
        player.fallDistance = 0.0F;
        DungeonInstanceAllocationData.MemberDispositionResult resumed =
            DungeonInstanceAllocationData.get(targetLevel).markMemberResumed(
                allocation.key(), player.getUUID(), targetLevel.getGameTime()
            );
        if (!resumed.accepted()) {
            transport.returnToHub(targetLevel, allocation);
            return ResumeResult.rejected("RESUME_PERSISTENCE_REJECTED");
        }
        targetLevel.getServer().overworld().getDataStorage().save();
        player.sendSystemMessage(Component.literal(
            "Dungeon run resumed at the active room reset anchor."
        ));
        return ResumeResult.succeeded("INSTANCE_RESUMED", destination);
    }

    private static boolean retryableSubordinateResumeReason(String reason) {
        return "SUBORDINATE_RESUME_IDENTITY_UNAVAILABLE".equals(reason)
            || "SUBORDINATE_RESUME_ROLLED_BACK".equals(reason);
    }

    private static @Nullable BlockPos verifiedEntryCheckpoint(
        ServerPlayer player,
        ServerLevel targetLevel,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        DungeonInstanceAllocationData.EntryDispatch dispatch = allocation.entryDispatch();
        if (dispatch == null
            || !allocation.cell().bounds().contains(dispatch.entryController())
            || !allocation.cell().bounds().contains(dispatch.entryDestination())
            || !allocation.cell().bounds().contains(dispatch.entryDestination().above())) {
            return null;
        }
        targetLevel.getChunkAt(dispatch.entryController());
        if (!(targetLevel.getBlockEntity(dispatch.entryController())
            instanceof RoomControllerBlockEntity controller)
            || !controller.ownsDungeonInstance(allocation.key())) {
            return null;
        }
        BlockPos destination = dispatch.entryDestination();
        BlockPos floor = destination.below();
        if (!targetLevel.getBlockState(floor).isFaceSturdy(targetLevel, floor, Direction.UP)
            || !targetLevel.getBlockState(destination)
                .getCollisionShape(targetLevel, destination).isEmpty()
            || !targetLevel.getBlockState(destination.above())
                .getCollisionShape(targetLevel, destination.above()).isEmpty()
            || !targetLevel.getFluidState(destination).isEmpty()
            || !targetLevel.getFluidState(destination.above()).isEmpty()) {
            return null;
        }
        AABB movedBounds = player.getBoundingBox().move(
            destination.getX() + 0.5D - player.getX(),
            destination.getY() - player.getY(),
            destination.getZ() + 0.5D - player.getZ()
        );
        return targetLevel.noCollision(player, movedBounds)
            ? destination.immutable()
            : null;
    }

    private static ResumeResult returnToHub(
        ServerPlayer player,
        DungeonInstanceAllocationData.Allocation allocation,
        String reason
    ) {
        if (allocation.entryDispatch() == null) {
            return ResumeResult.rejected("HUB_RETURN_CHECKPOINT_MISSING");
        }
        ServerLevel targetLevel = resolveLevel(player, allocation.dimension());
        if (targetLevel == null) {
            return ResumeResult.rejected("HUB_DIMENSION_UNAVAILABLE");
        }
        BlockPos target = allocation.entryDispatch().hubReturnTarget().above();
        targetLevel.getChunkAt(target);
        player.teleportTo(
            targetLevel,
            target.getX() + 0.5D,
            target.getY(),
            target.getZ() + 0.5D,
            player.getYRot(),
            player.getXRot()
        );
        player.fallDistance = 0.0F;
        if (player.getUUID().equals(allocation.host())) {
            DungeonSubordinateTransportData.get(targetLevel)
                .releaseHubHolds(targetLevel, allocation.key());
        }
        LOGGER.warn(
            "Returned dungeon member to persisted hub checkpoint: player={} instance={} epoch={} reason={}",
            player.getUUID(), allocation.key().instanceId(), allocation.key().epoch(), reason
        );
        return ResumeResult.succeeded("RETURNED_TO_HUB", target);
    }

    private static @Nullable ServerLevel resolveLevel(
        ServerPlayer player,
        String dimension
    ) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        return player.server.getLevel(key);
    }

    public record ResumeResult(
        boolean handled,
        boolean succeeded,
        boolean retryable,
        String reason,
        @Nullable BlockPos destination
    ) {
        static ResumeResult ignored(String reason) {
            return new ResumeResult(false, false, false, reason, null);
        }

        static ResumeResult rejected(String reason) {
            return new ResumeResult(true, false, false, reason, null);
        }

        static ResumeResult retrying(String reason) {
            return new ResumeResult(true, false, true, reason, null);
        }

        static ResumeResult succeeded(String reason, BlockPos destination) {
            return new ResumeResult(true, true, false, reason, destination.immutable());
        }
    }
}
