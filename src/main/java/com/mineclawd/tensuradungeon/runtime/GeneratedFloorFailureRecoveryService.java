package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

final class GeneratedFloorFailureRecoveryService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GeneratedFloorFailureRecoveryService() {
    }

    static RecoveryResult attempt(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        GeneratedRoomPlacementOperation.SliceBudget sliceBudget
    ) {
        if (level == null || instanceKey == null) {
            return RecoveryResult.noFallback("RECOVERY_REQUEST_INVALID");
        }

        String dimensionKey = level.dimension().location().toString();
        GeneratedFloorRepository repository = GeneratedFloorRepository.get(
            level, dimensionKey, instanceKey
        );
        GeneratedFloorSession session = repository.hydrate();
        GeneratedFloorRuntimeData.GenerationFailureState failure = session.generationFailure();
        if (failure == null || (!GeneratedFloorLazyPlacementService.GENERATED_ROOM_PLACEMENT_FAILED.equals(failure.code())
            && !GeneratedFloorLazyPlacementService.GENERATED_ROOM_PLACEMENT_FALLBACK_PENDING.equals(failure.code()))) {
            return RecoveryResult.noFallback("FAILURE_NOT_ELIGIBLE_FOR_ROOM_FALLBACK");
        }

        DungeonInstanceAllocationData.Allocation allocation = DungeonInstanceAllocationData.get(level)
            .forIdentity(instanceKey).orElse(null);
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (allocation == null || !manager.isActiveRun(instanceKey) || manager.isRunCompleted(instanceKey)) {
            return RecoveryResult.noFallback("INSTANCE_RUN_NOT_ACTIVE");
        }
        if (!manager.isPresetMode(instanceKey) || failure.controllerPos() == null) {
            return RecoveryResult.noFallback("FALLBACK_REQUIRES_GENERATED_PRESET_ORDER");
        }

        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        if (placement == null || placement.roomPlans().isEmpty()) {
            return RecoveryResult.noFallback("GENERATED_PLACEMENT_UNAVAILABLE");
        }
        int failedIndex = planIndex(placement.roomPlans(), failure.controllerPos());
        if (failedIndex < 0) {
            return RecoveryResult.noFallback("FAILED_ROOM_PLAN_NOT_FOUND");
        }

        Candidate candidate = findCandidate(level, session, placement, allocation, instanceKey, manager, failedIndex);
        Set<BlockPos> activeRooms = manager.getActiveRoomControllerPositions(instanceKey);
        if (!activeRooms.isEmpty() && (candidate == null || activeRooms.size() != 1
            || !activeRooms.contains(candidate.plan().controllerPos()))) {
            return RecoveryResult.deferred("CURRENT_ROOM_MUST_FINISH_BEFORE_FALLBACK");
        }

        List<ServerPlayer> onlineParticipants = onlineParticipants(level, allocation);
        if (onlineParticipants.isEmpty()) {
            return RecoveryResult.deferred("WAITING_FOR_PARTICIPANT_TO_RECONNECT");
        }

        if (candidate == null) {
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan successor = findUnplacedSuccessorPlan(
                placement, failedIndex
            );
            if (successor == null) {
                return RecoveryResult.noFallback("NO_VALIDATED_SUCCESSOR_ROOM");
            }
            if (GeneratedFloorLazyPlacementService.GENERATED_ROOM_PLACEMENT_FAILED.equals(failure.code())) {
                markFallbackPending(level, repository, session, failure, successor);
                failure = session.generationFailure();
            }
            GeneratedFloorLazyPlacementService.PlacementResult fallbackPlacement =
                GeneratedFloorLazyPlacementService.placeFallbackRoom(
                    level, instanceKey, successor, sliceBudget
                );
            if (fallbackPlacement.inProgress()) {
                return RecoveryResult.deferred("FALLBACK_SUCCESSOR_PLACEMENT_IN_PROGRESS");
            }
            if (!fallbackPlacement.placed()
                && !"LOOKAHEAD_ALREADY_PLACED".equals(fallbackPlacement.reason())) {
                return RecoveryResult.noFallback(
                    "FALLBACK_SUCCESSOR_PLACEMENT_FAILED_" + fallbackPlacement.reason()
                );
            }
            session = repository.hydrate();
            placement = session.placement();
            if (placement == null) {
                return RecoveryResult.noFallback("FALLBACK_SUCCESSOR_PLACEMENT_STATE_MISSING");
            }
            failedIndex = planIndex(placement.roomPlans(), failure.controllerPos());
            candidate = failedIndex < 0
                ? null
                : findCandidate(level, session, placement, allocation, instanceKey, manager, failedIndex);
            if (candidate == null) {
                return RecoveryResult.noFallback("FALLBACK_SUCCESSOR_NOT_VALID_AFTER_PLACEMENT");
            }
        }

        boolean targetActive = candidate.controller().getRoomState()
            == RoomControllerBlockEntity.RoomState.ACTIVE
            && manager.getActiveRoomControllerPositions(instanceKey)
                .contains(candidate.plan().controllerPos());
        if (!checkpointSafeForParticipants(level, allocation, candidate.plan(), candidate.destination(), onlineParticipants)) {
            return RecoveryResult.noFallback("SUCCESSOR_ENTRY_CHECKPOINT_UNSAFE");
        }

        DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);
        if (!transport.canResumeAtCheckpoint(level, allocation)) {
            return RecoveryResult.noFallback("SUBORDINATE_CHECKPOINT_IDENTITY_UNAVAILABLE");
        }

        if (GeneratedFloorLazyPlacementService.GENERATED_ROOM_PLACEMENT_FAILED.equals(failure.code())) {
            markFallbackPending(level, repository, session, failure, candidate.plan());
            failure = session.generationFailure();
        }

        boolean failedRoomSkipped = manager.isRoomRegisteredCompleted(instanceKey, failure.controllerPos());
        if (!targetActive) {
            if (!failedRoomSkipped
                && !manager.skipExpectedGeneratedRoom(
                    instanceKey, failure.controllerPos(), "PLACEMENT_RECOVERY"
                )) {
                return RecoveryResult.noFallback("FAILED_ROOM_SKIP_REJECTED");
            }
            String activationBlockReason = manager.getPresetActivationBlockReason(
                instanceKey, candidate.plan().controllerPos()
            );
            if (!"NONE".equals(activationBlockReason)) {
                if (!failedRoomSkipped) {
                    manager.undoSkippedGeneratedRoom(instanceKey, failure.controllerPos());
                }
                return RecoveryResult.noFallback(
                    "SUCCESSOR_ACTIVATION_ORDER_REJECTED_" + activationBlockReason
                );
            }
            if (!candidate.controller().activate(level)) {
                if (!failedRoomSkipped) {
                    manager.undoSkippedGeneratedRoom(instanceKey, failure.controllerPos());
                }
                return RecoveryResult.noFallback("SUCCESSOR_CONTROLLER_ACTIVATION_FAILED");
            }
        }

        DungeonSubordinateTransportData.TransportResult subordinateResult =
            transport.resumeAtCheckpoint(level, allocation, candidate.destination());
        if (!subordinateResult.accepted()) {
            LOGGER.error(
                "[TD-FALLBACK] successor subordinate transport failed: instance={} epoch={} successor={} reason={}",
                instanceKey.instanceId(), instanceKey.epoch(), candidate.plan().controllerPos(),
                subordinateResult.reason()
            );
            return RecoveryResult.noFallback("SUCCESSOR_SUBORDINATE_TRANSPORT_FAILED_" + subordinateResult.reason());
        }

        for (ServerPlayer participant : onlineParticipants) {
            teleportParticipant(participant, level, candidate.destination());
            participant.sendSystemMessage(Component.literal(
                "A dungeon room could not be built safely. The run routed you through the next validated room."
            ));
        }

        markFallbackRouted(level, repository, session, failure, candidate);
        LOGGER.warn(
            "[TD-FALLBACK] routed generated-room failure through validated successor: instance={} epoch={} failed={} successor={} destination={} dimension={}",
            instanceKey.instanceId(), instanceKey.epoch(), failure.controllerPos(),
            candidate.plan().controllerPos(), candidate.destination(), dimensionKey
        );
        return RecoveryResult.routed(candidate.destination());
    }

    @Nullable
    private static Candidate findCandidate(
        ServerLevel level,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        DungeonInstanceAllocationData.Allocation allocation,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        DungeonRunManager manager,
        int failedIndex
    ) {
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans = placement.roomPlans();
        for (int index = failedIndex + 1; index < plans.size(); index++) {
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan = plans.get(index);
            if (!placement.controllers().contains(plan.controllerPos())
                || plan.bounds() == null || plan.incomingConnectorPos() == null
                || manager.isRoomRegisteredCompleted(instanceKey, plan.controllerPos())) {
                continue;
            }
            if (!allocation.cell().bounds().contains(plan.controllerPos())
                || !allocation.cell().bounds().contains(plan.incomingConnectorPos())
                || !allocation.cell().bounds().contains(plan.incomingConnectorPos().above())
                || GeneratedRoomPlacementOperation.existingControllerValidationFailure(
                    level, session, plan, false
                ) != null) {
                continue;
            }
            if (!(level.getBlockEntity(plan.controllerPos()) instanceof RoomControllerBlockEntity controller)
                || !controller.matchesDungeonInstance(instanceKey)) {
                continue;
            }
            RoomControllerBlockEntity.RoomState roomState = controller.getRoomState();
            boolean registeredActive = manager.getActiveRoomControllerPositions(instanceKey)
                .contains(plan.controllerPos());
            if (roomState == RoomControllerBlockEntity.RoomState.ACTIVE && !registeredActive) {
                continue;
            }
            if (roomState != RoomControllerBlockEntity.RoomState.INACTIVE
                && !(roomState == RoomControllerBlockEntity.RoomState.ACTIVE && registeredActive)) {
                continue;
            }
            BlockPos destination = verifiedEntryCheckpoint(level, allocation, plan);
            if (destination == null) {
                continue;
            }
            return new Candidate(plan, controller, destination);
        }
        return null;
    }

    @Nullable
    private static BlockPos verifiedEntryCheckpoint(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan
    ) {
        BlockPos connector = plan.incomingConnectorPos();
        RoomControllerBlockEntity.RoomBounds bounds = plan.bounds();
        if (connector == null || bounds == null || !bounds.contains(connector)
            || !bounds.contains(connector.above()) || !bounds.contains(connector.above().above())
            || !allocation.cell().bounds().contains(connector)
            || !allocation.cell().bounds().contains(connector.above())
            || !allocation.cell().bounds().contains(connector.above().above())) {
            return null;
        }
        level.getChunkAt(connector);
        BlockPos destination = connector.above();
        if (level.getBlockState(connector).isAir()
            || !level.getBlockState(connector).isFaceSturdy(level, connector, Direction.UP)
            || !level.getBlockState(destination).getCollisionShape(level, destination).isEmpty()
            || !level.getBlockState(destination.above()).getCollisionShape(level, destination.above()).isEmpty()
            || !level.getFluidState(destination).isEmpty()
            || !level.getFluidState(destination.above()).isEmpty()) {
            return null;
        }
        return destination.immutable();
    }

    private static boolean checkpointSafeForParticipants(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan,
        BlockPos destination,
        List<ServerPlayer> participants
    ) {
        if (!allocation.cell().bounds().contains(destination)
            || !allocation.cell().bounds().contains(destination.above())
            || plan.bounds() == null || !plan.bounds().contains(destination)
            || !plan.bounds().contains(destination.above())) {
            return false;
        }
        for (ServerPlayer participant : participants) {
            AABB movedBounds = participant.getBoundingBox().move(
                destination.getX() + 0.5D - participant.getX(),
                destination.getY() - participant.getY(),
                destination.getZ() + 0.5D - participant.getZ()
            );
            if (!level.noCollision(participant, movedBounds)) {
                return false;
            }
        }
        return true;
    }

    private static List<ServerPlayer> onlineParticipants(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        List<ServerPlayer> result = new ArrayList<>();
        for (java.util.UUID playerId : allocation.roster()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                result.add(player);
            }
        }
        return List.copyOf(result);
    }

    private static int planIndex(
        List<GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan> plans,
        BlockPos controllerPos
    ) {
        for (int index = 0; index < plans.size(); index++) {
            if (controllerPos.equals(plans.get(index).controllerPos())) {
                return index;
            }
        }
        return -1;
    }

    @Nullable
    private static GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan findUnplacedSuccessorPlan(
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        int failedIndex
    ) {
        int successorIndex = failedIndex + 1;
        if (successorIndex < 0 || successorIndex >= placement.roomPlans().size()) {
            return null;
        }
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan successor =
            placement.roomPlans().get(successorIndex);
        return successor.hasPlacementMetadata()
            && !placement.controllers().contains(successor.controllerPos())
            ? successor : null;
    }

    private static void markFallbackPending(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.GenerationFailureState failure,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan successor
    ) {
        GeneratedFloorRuntimeData.GenerationFailureState pending =
            new GeneratedFloorRuntimeData.GenerationFailureState(
                GeneratedFloorLazyPlacementService.GENERATED_ROOM_PLACEMENT_FALLBACK_PENDING,
                false,
                failure.attempts(),
                failure.firstGameTime(),
                level.getGameTime(),
                failure.controllerPos(),
                successor.incomingConnectorPos(),
                "failedReason=" + failure.detail()
                    + " successor=" + successor.controllerPos()
            );
        session.replaceGenerationFailure(pending);
        repository.flushGenerationFailure(session);
        level.getServer().overworld().getDataStorage().save();
    }

    private static void markFallbackRouted(
        ServerLevel level,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session,
        GeneratedFloorRuntimeData.GenerationFailureState failure,
        Candidate candidate
    ) {
        GeneratedFloorRuntimeData.GenerationFailureState routed =
            new GeneratedFloorRuntimeData.GenerationFailureState(
                GeneratedFloorLazyPlacementService.GENERATED_ROOM_PLACEMENT_FALLBACK_ROUTED,
                false,
                failure.attempts(),
                failure.firstGameTime(),
                level.getGameTime(),
                failure.controllerPos(),
                candidate.plan().incomingConnectorPos(),
                "failedReason=" + failure.detail()
                    + " successor=" + candidate.plan().controllerPos()
                    + " destination=" + candidate.destination()
            );
        session.replaceGenerationFailure(routed);
        repository.flushGenerationFailure(session);
        level.getServer().overworld().getDataStorage().save();
    }

    private static void teleportParticipant(
        ServerPlayer participant,
        ServerLevel level,
        BlockPos destination
    ) {
        participant.teleportTo(
            level,
            destination.getX() + 0.5D,
            destination.getY(),
            destination.getZ() + 0.5D,
            participant.getYRot(),
            participant.getXRot()
        );
        participant.fallDistance = 0.0F;
    }

    record Candidate(
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan plan,
        RoomControllerBlockEntity controller,
        BlockPos destination
    ) {
    }

    record RecoveryResult(Status status, String reason, @Nullable BlockPos destination) {
        static RecoveryResult routed(BlockPos destination) {
            return new RecoveryResult(Status.ROUTED, "SUCCESSOR_ROUTED", destination.immutable());
        }

        static RecoveryResult deferred(String reason) {
            return new RecoveryResult(Status.DEFERRED, reason, null);
        }

        static RecoveryResult noFallback(String reason) {
            return new RecoveryResult(Status.NO_FALLBACK, reason, null);
        }

        boolean routed() {
            return status == Status.ROUTED;
        }

        boolean deferred() {
            return status == Status.DEFERRED;
        }
    }

    enum Status {
        ROUTED,
        DEFERRED,
        NO_FALLBACK
    }
}
