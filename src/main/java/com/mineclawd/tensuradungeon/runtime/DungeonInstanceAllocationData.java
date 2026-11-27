package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonCompositionData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonInstanceAllocationData extends SavedData {
    static final String DATA_NAME = "tensura_dungeon_instance_allocations";
    static final int CELL_DIAMETER = 8_192;
    static final int CELL_GAP = 2_048;
    static final int CELL_STRIDE = CELL_DIAMETER + CELL_GAP;
    public static final int MAX_CONCURRENT_SOLO_INSTANCES = 10;

    private static final String ALLOCATIONS_KEY = "Allocations";
    private static final String INSTANCE_ID_KEY = "InstanceId";
    private static final String EPOCH_KEY = "Epoch";
    private static final String DIMENSION_KEY = "Dimension";
    private static final String CELL_INDEX_KEY = "CellIndex";
    private static final String CELL_ORIGIN_KEY = "CellOrigin";
    private static final String CELL_MIN_KEY = "CellMin";
    private static final String CELL_MAX_KEY = "CellMax";
    private static final String HOST_KEY = "Host";
    private static final String ROSTER_KEY = "Roster";
    private static final String PLAYER_KEY = "Player";
    private static final String STATE_KEY = "State";
    private static final String TRANSITION_REASON_KEY = "TransitionReason";
    private static final String QUARANTINE_REASON_KEY = "QuarantineReason";
    private static final String CREATED_GAME_TIME_KEY = "CreatedGameTime";
    private static final String TRANSITION_GAME_TIME_KEY = "TransitionGameTime";
    private static final String SOLO_PREPARATION_KEY = "SoloPreparation";
    private static final String REQUESTED_FLOOR_KEY = "RequestedFloor";
    private static final String TARGET_ROOMS_KEY = "TargetRooms";
    private static final String ENTRY_DIRECTION_KEY = "EntryDirection";
    private static final String ATTACHMENT_TARGET_KEY = "AttachmentTarget";
    private static final String PLAN_SEED_KEY = "PlanSeed";
    private static final String REQUESTED_GAME_TIME_KEY = "RequestedGameTime";
    private static final String FROZEN_PARTICIPANTS_KEY = "FrozenParticipants";
    private static final String PARTICIPANT_ID_KEY = "Id";
    private static final String PARTICIPANT_NAME_KEY = "Name";
    private static final String PARTICIPANT_KIND_KEY = "Kind";
    private static final String PARTICIPANT_OWNER_KEY = "Owner";
    private static final String PARTICIPANT_SLOT_KEY = "Slot";
    private static final String PARTICIPANT_LOADOUT_REVISION_KEY = "LoadoutRevision";
    private static final String ENTRY_DISPATCH_KEY = "EntryDispatch";
    private static final String ENTRY_CONTROLLER_KEY = "EntryController";
    private static final String ENTRY_DESTINATION_KEY = "EntryDestination";
    private static final String HUB_RETURN_TARGET_KEY = "HubReturnTarget";
    private static final String DISPATCH_STATE_KEY = "DispatchState";
    private static final String DISPATCH_GAME_TIME_KEY = "DispatchGameTime";
    private static final String MEMBER_DISPOSITIONS_KEY = "MemberDispositions";
    private static final String MEMBER_DISPOSITION_KEY = "Disposition";
    private static final UUID LEGACY_HOST = new UUID(0L, 0L);

    private final Map<Integer, Allocation> allocationsByCell = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerToInstance = new LinkedHashMap<>();

    public static DungeonInstanceAllocationData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(DungeonInstanceAllocationData::new, DungeonInstanceAllocationData::load),
            DATA_NAME
        );
    }

    public static void reconcile(ServerLevel level) {
        DungeonInstanceAllocationData data = get(level);
        String dimension = level.dimension().location().toString();
        List<Allocation> allocations = data.allocations().stream()
            .filter(allocation -> allocation.dimension().equals(dimension))
            .filter(allocation -> !allocation.lifecycle().state().reusable())
            .toList();
        GeneratedFloorRuntimeData runtimeData = GeneratedFloorRuntimeData.get(level);
        RollingDungeonCompositionData compositionData = RollingDungeonCompositionData.get(level);
        DungeonRunManager runData = DungeonRunManager.get(level);
        retireReleasedInstanceOwners(data, dimension, runtimeData, compositionData, runData);
        boolean generatedOwnersAreUnkeyed =
            runtimeData.getInstanceKeys(dimension).isEmpty()
                && compositionData.getInstanceKeys(dimension).isEmpty();

        if (allocations.size() <= 1 && generatedOwnersAreUnkeyed) {
            GeneratedFloorRepository legacyRepository = GeneratedFloorRepository.get(level);
            GeneratedFloorSession legacySession = legacyRepository.hydrate();
            Allocation allocation = allocations.isEmpty() ? null : allocations.getFirst();
            if (allocation == null
                && (legacySession.placement() != null || legacySession.cleanupState() != null)) {
                GeneratedFloorRuntimeData.LastGeneratedPlacement placement =
                    legacySession.placement();
                GeneratedFloorRuntimeData.CleanupState cleanup = legacySession.cleanupState();
                BlockPos origin = placement != null
                    ? placement.floorBuildOrigin()
                    : cleanup.floorBuildOrigin();
                List<RoomControllerBlockEntity.RoomBounds> bounds = placement != null
                    ? placement.bounds()
                    : cleanup.bounds();
                DungeonRunManager run = DungeonRunManager.get(level);
                LifecycleState state = cleanup != null
                    ? LifecycleState.CLEANING
                    : run.isActiveRun() && run.isPresetMode()
                        ? run.isRunCompleted() ? LifecycleState.COMPLETING : LifecycleState.ACTIVE
                        : LifecycleState.PREPARING;
                allocation = data.adoptLegacyGeneratedFloor(
                    dimension,
                    origin,
                    level.getMinBuildHeight(),
                    level.getMaxBuildHeight(),
                    bounds,
                    state,
                    level.getGameTime()
                );
            }
            if (allocation != null) {
                reconcileAllocation(
                    level, data, allocation, legacyRepository, legacySession
                );
            }
            return;
        }

        InstanceKey primaryKey = allocations.isEmpty() ? null : allocations.getFirst().key();
        try {
            for (Allocation allocation : allocations) {
                GeneratedFloorRepository repository = GeneratedFloorRepository.get(
                    level, dimension, allocation.key()
                );
                reconcileAllocation(
                    level, data, allocation, repository, repository.hydrate()
                );
            }
        } finally {
            if (primaryKey != null) {
                DungeonRunManager run = DungeonRunManager.get(level);
                if (run.canBindInstance(primaryKey)) {
                    run.bindInstance(primaryKey);
                }
            }
        }
    }

    static void retireReleasedInstanceOwners(
        DungeonInstanceAllocationData allocations,
        String dimension,
        GeneratedFloorRuntimeData runtimeData,
        RollingDungeonCompositionData compositionData,
        DungeonRunManager runData
    ) {
        Set<InstanceKey> persistedKeys = new LinkedHashSet<>();
        persistedKeys.addAll(runtimeData.getInstanceKeys(dimension));
        persistedKeys.addAll(compositionData.getInstanceKeys(dimension));
        persistedKeys.addAll(runData.getPersistedInstanceKeys());
        for (InstanceKey key : persistedKeys) {
            Allocation allocation = allocations.forIdentity(key).orElse(null);
            if (allocation != null && !allocation.lifecycle().state().reusable()) {
                continue;
            }
            if (!runtimeData.canRetireInstance(dimension, key)
                || !compositionData.canRetireInstance(dimension, key)
                || !runData.canRetireInstance(key)) {
                continue;
            }
            runtimeData.retireInstance(dimension, key);
            compositionData.retireInstance(dimension, key);
            runData.retireInstance(key);
        }
    }

    private static void reconcileAllocation(
        ServerLevel level,
        DungeonInstanceAllocationData data,
        Allocation allocation,
        GeneratedFloorRepository repository,
        GeneratedFloorSession session
    ) {
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = session.placement();
        GeneratedFloorRuntimeData.CleanupState cleanup = session.cleanupState();
        DungeonRunManager run = DungeonRunManager.get(level);
        String dimension = allocation.dimension();
        if (allocation.lifecycle().state() == LifecycleState.QUARANTINED) {
            return;
        }
        boolean hasGeneratedOwner = allocation.key().equals(session.instanceKey())
            || placement != null
            || cleanup != null
            || session.compositionState() != null
            || session.generationFailure() != null;
        if (!hasGeneratedOwner
            && allocation.lifecycle().state() == LifecycleState.ALLOCATED
            && allocation.soloPreparation() != null) {
            return;
        }
        if (!bindExistingOwners(level, allocation, placement, repository)) {
            data.quarantine(
                allocation.key(),
                "INSTANCE_OWNER_BINDING_MISMATCH",
                level.getGameTime()
            );
            return;
        }

        List<RoomControllerBlockEntity.RoomBounds> authoritativeBounds = cleanup != null
            ? cleanup.bounds()
            : placement == null ? List.of() : placement.bounds();
        if (!boundsInside(allocation.cell().bounds(), authoritativeBounds)) {
            data.quarantine(
                allocation.key(),
                "PERSISTED_OWNERSHIP_OUTSIDE_CELL",
                level.getGameTime()
            );
            return;
        }
        if (cleanup == null && placement == null
            && allocation.lifecycle().state() == LifecycleState.CLEANING) {
            DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);
            if (!transport.safeForCleanup(level, allocation.key())) {
                if (transport.requiresQuarantine(allocation.key())) {
                    data.quarantine(
                        allocation.key(),
                        "SUBORDINATE_RETURN_UNRESOLVED_BEFORE_REUSE",
                        level.getGameTime()
                    );
                }
                return;
            }
            if (data.markReusable(
                allocation.key(),
                "CLEANUP_AND_OWNERSHIP_VERIFIED",
                level.getGameTime()
            )) {
                DungeonSubordinateTransportData.get(level).retire(level, allocation.key());
            }
            return;
        }
        if (cleanup != null) {
            data.transition(
                allocation.key(),
                LifecycleState.CLEANING,
                "GENERATED_CLEANUP_PENDING",
                level.getGameTime()
            );
            return;
        }
        if (cleanup == null && placement != null
            && allocation.lifecycle().state() == LifecycleState.CLEANING) {
            data.transition(
                allocation.key(),
                LifecycleState.PREPARING,
                "GENERATED_CLEANUP_COMPLETE_PLACEMENT_READY",
                level.getGameTime()
            );
            return;
        }
        if (run.isActiveRun()) {
            if (!run.isPresetMode() && placement != null
                && (allocation.lifecycle().state() == LifecycleState.ALLOCATED
                    || allocation.lifecycle().state() == LifecycleState.PREPARING)) {
                data.transition(
                    allocation.key(),
                    LifecycleState.PREPARING,
                    "GENERATED_PLACEMENT_AWAITING_RUN",
                    level.getGameTime()
                );
                return;
            }
            if (allocation.lifecycle().state() == LifecycleState.ALLOCATED) {
                data.transition(
                    allocation.key(),
                    LifecycleState.PREPARING,
                    "PERSISTED_GENERATION_PRESENT",
                    level.getGameTime()
                );
            }
            if (run.isRunCompleted()) {
                if (data.forIdentity(allocation.key())
                    .map(Allocation::lifecycle)
                    .map(Lifecycle::state)
                    .orElse(LifecycleState.QUARANTINED) == LifecycleState.PREPARING) {
                    data.transition(
                        allocation.key(),
                        LifecycleState.ACTIVE,
                        "RUN_RECONSTRUCTED_ACTIVE",
                        level.getGameTime()
                    );
                }
                data.transition(
                    allocation.key(),
                    LifecycleState.COMPLETING,
                    "RUN_COMPLETED_AWAITING_EXIT",
                    level.getGameTime()
                );
            } else if (allocation.lifecycle().state() != LifecycleState.SUSPENDED) {
                data.transition(
                    allocation.key(),
                    LifecycleState.ACTIVE,
                    "RUN_RECONSTRUCTED_ACTIVE",
                    level.getGameTime()
                );
            }
            return;
        }
        if (placement != null) {
            if (allocation.lifecycle().state() == LifecycleState.ALLOCATED) {
                data.transition(
                    allocation.key(),
                    LifecycleState.PREPARING,
                    "GENERATED_PLACEMENT_AWAITING_RUN",
                    level.getGameTime()
                );
            }
            return;
        }
        if (allocation.lifecycle().state() == LifecycleState.ALLOCATED
            && (allocation.soloPreparation() != null
                || "SOLO_PREPARATION_RESERVED".equals(allocation.lifecycle().transitionReason()))) {
            return;
        }
        LifecycleState state = allocation.lifecycle().state();
        if (state == LifecycleState.ALLOCATED || state == LifecycleState.PREPARING
            || state == LifecycleState.ACTIVE || state == LifecycleState.COMPLETING) {
            data.transition(
                allocation.key(),
                LifecycleState.CLEANING,
                "NO_REMAINING_WORLD_OWNERSHIP",
                level.getGameTime()
            );
        }
        DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);
        if (!transport.safeForCleanup(level, allocation.key())) {
            if (transport.requiresQuarantine(allocation.key())) {
                data.quarantine(
                    allocation.key(),
                    "SUBORDINATE_RETURN_UNRESOLVED_BEFORE_REUSE",
                    level.getGameTime()
                );
            }
            return;
        }
        if (data.markReusable(
            allocation.key(),
            "CLEANUP_AND_OWNERSHIP_VERIFIED",
            level.getGameTime()
        )) {
            DungeonSubordinateTransportData.get(level).retire(level, allocation.key());
        }
    }

    public static boolean bindExistingOwners(
        ServerLevel level,
        Allocation allocation,
        @Nullable GeneratedFloorRuntimeData.LastGeneratedPlacement placement
    ) {
        return bindExistingOwners(
            level,
            allocation,
            placement,
            GeneratedFloorRepository.get(level)
        );
    }

    private static boolean bindExistingOwners(
        ServerLevel level,
        Allocation allocation,
        @Nullable GeneratedFloorRuntimeData.LastGeneratedPlacement placement,
        GeneratedFloorRepository generated
    ) {
        if (level == null || allocation == null) {
            return false;
        }
        DungeonRunManager run = DungeonRunManager.get(level);
        if (!run.canBindInstance(allocation.key()) || !generated.canBindInstance(allocation.key())) {
            return false;
        }
        if (!run.bindInstance(allocation.key()) || !generated.bindInstance(allocation.key())) {
            return false;
        }
        if (placement == null) {
            return true;
        }
        for (BlockPos controllerPos : placement.controllers()) {
            if (level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller
                && !controller.bindDungeonInstance(allocation.key())) {
                return false;
            }
        }
        return true;
    }

    public static DungeonInstanceAllocationData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        DungeonInstanceAllocationData data = new DungeonInstanceAllocationData();
        ListTag allocations = tag.getList(ALLOCATIONS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < allocations.size(); index++) {
            Allocation allocation = loadAllocation(allocations.getCompound(index));
            Allocation duplicate = data.allocationsByCell.putIfAbsent(
                allocation.cell().index(), allocation
            );
            if (duplicate != null) {
                throw new IllegalStateException(
                    "Duplicate dungeon instance cell index in save: " + allocation.cell().index()
                );
            }
        }
        data.rebuildIndexes();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag allocations = new ListTag();
        allocationsByCell.values().stream()
            .sorted(Comparator.comparingInt(allocation -> allocation.cell().index()))
            .map(DungeonInstanceAllocationData::saveAllocation)
            .forEach(allocations::add);
        tag.put(ALLOCATIONS_KEY, allocations);
        return tag;
    }

    public ReservationResult reserveSingle(
        String dimension,
        UUID playerId,
        BlockPos floorBuildOrigin,
        int minimumBuildHeight,
        int maximumBuildHeight,
        List<RoomControllerBlockEntity.RoomBounds> plannedBounds,
        long gameTime
    ) {
        String normalizedDimension = normalizeDimension(dimension);
        Allocation current = allocationsByCell.values().stream()
            .filter(allocation -> !allocation.lifecycle().state().reusable())
            .min(Comparator.comparingInt(allocation -> allocation.cell().index()))
            .orElse(null);
        if (current != null && !current.lifecycle().state().reusable()) {
            if ((current.lifecycle().state() == LifecycleState.ALLOCATED
                    || current.lifecycle().state() == LifecycleState.PREPARING)
                && current.dimension().equals(normalizedDimension)
                && current.roster().contains(playerId)
                && current.cell().origin().equals(floorBuildOrigin)
                && boundsInside(current.cell().bounds(), plannedBounds)) {
                return ReservationResult.accepted(current, false, "EXISTING_RESERVATION");
            }
            return ReservationResult.rejected("SINGLE_INSTANCE_BUSY", current);
        }

        Allocation reusable = allocationsByCell.values().stream()
            .filter(allocation -> allocation.lifecycle().state().reusable())
            .min(Comparator.comparingInt(allocation -> allocation.cell().index()))
            .orElse(null);
        Cell cell;
        long epoch;
        if (reusable == null) {
            cell = new Cell(
                0,
                floorBuildOrigin.immutable(),
                boundsAround(floorBuildOrigin, minimumBuildHeight, maximumBuildHeight)
            );
            epoch = 1L;
        } else {
            if (!reusable.dimension().equals(normalizedDimension)) {
                return ReservationResult.rejected("REUSABLE_CELL_DIMENSION_MISMATCH", reusable);
            }
            cell = reusable.cell();
            epoch = reusable.key().epoch() + 1L;
            if (!cell.origin().equals(floorBuildOrigin)) {
                return ReservationResult.rejected("ORIGIN_OUTSIDE_REUSABLE_CELL", reusable);
            }
        }
        if (!boundsInside(cell.bounds(), plannedBounds)) {
            return ReservationResult.rejected("PLANNED_BOUNDS_OUTSIDE_CELL", reusable);
        }

        UUID instanceId = UUID.randomUUID();
        Allocation allocation = new Allocation(
            new InstanceKey(instanceId, epoch),
            normalizedDimension,
            cell,
            playerId,
            Set.of(playerId),
            null,
            null,
            Map.of(),
            new Lifecycle(
                LifecycleState.ALLOCATED,
                "SINGLE_INSTANCE_RESERVED",
                "",
                Math.max(0L, gameTime)
            ),
            Math.max(0L, gameTime)
        );
        allocationsByCell.put(cell.index(), allocation);
        rebuildIndexes();
        setDirty();
        return ReservationResult.accepted(allocation, true, reusable == null ? "NEW_CELL" : "REUSED_CELL");
    }

    public ReservationResult reserveSolo(
        String dimension,
        UUID playerId,
        BlockPos gridAnchor,
        int minimumBuildHeight,
        int maximumBuildHeight,
        List<RoomControllerBlockEntity.RoomBounds> plannedBoundsAtAnchor,
        long gameTime
    ) {
        if (playerId == null || gridAnchor == null) {
            throw new IllegalArgumentException("Solo dungeon reservation requires player and grid anchor");
        }
        String normalizedDimension = normalizeDimension(dimension);
        Allocation existing = forPlayer(playerId).orElse(null);
        if (existing != null) {
            if (existing.lifecycle().state() == LifecycleState.QUARANTINED) {
                return ReservationResult.rejected("PLAYER_INSTANCE_QUARANTINED", existing);
            }
            if (!existing.dimension().equals(normalizedDimension)) {
                return ReservationResult.rejected("PLAYER_INSTANCE_DIMENSION_MISMATCH", existing);
            }
            return ReservationResult.accepted(existing, false, "EXISTING_SOLO_RESERVATION");
        }

        Allocation mismatchedGrid = allocationsByCell.values().stream()
            .filter(allocation -> allocation.dimension().equals(normalizedDimension))
            .filter(allocation -> !allocation.cell().origin().equals(
                originForCell(gridAnchor, allocation.cell().index())
            ))
            .findFirst()
            .orElse(null);
        if (mismatchedGrid != null) {
            return ReservationResult.rejected("CELL_GRID_ANCHOR_MISMATCH", mismatchedGrid);
        }

        Allocation reusable = allocationsByCell.values().stream()
            .filter(allocation -> allocation.dimension().equals(normalizedDimension))
            .filter(allocation -> allocation.lifecycle().state().reusable())
            .filter(allocation -> allocation.cell().index() < MAX_CONCURRENT_SOLO_INSTANCES)
            .min(Comparator.comparingInt(allocation -> allocation.cell().index()))
            .orElse(null);
        int cellIndex = reusable == null
            ? firstUnallocatedSoloCell()
            : reusable.cell().index();
        if (cellIndex < 0) {
            return ReservationResult.rejected("SOLO_INSTANCE_CAPACITY_REACHED", null);
        }

        BlockPos cellOrigin = originForCell(gridAnchor, cellIndex);
        List<RoomControllerBlockEntity.RoomBounds> translatedBounds =
            translateBounds(plannedBoundsAtAnchor, cellOrigin.subtract(gridAnchor));
        Cell cell = reusable == null
            ? new Cell(
                cellIndex,
                cellOrigin,
                boundsAround(cellOrigin, minimumBuildHeight, maximumBuildHeight)
            )
            : reusable.cell();
        if (!cell.origin().equals(cellOrigin)) {
            return ReservationResult.rejected("CELL_GRID_ANCHOR_MISMATCH", reusable);
        }
        if (!boundsInside(cell.bounds(), translatedBounds)) {
            return ReservationResult.rejected("PLANNED_BOUNDS_OUTSIDE_CELL", reusable);
        }

        long epoch = reusable == null ? 1L : reusable.key().epoch() + 1L;
        Allocation allocation = new Allocation(
            new InstanceKey(UUID.randomUUID(), epoch),
            normalizedDimension,
            cell,
            playerId,
            Set.of(playerId),
            null,
            null,
            Map.of(),
            new Lifecycle(
                LifecycleState.ALLOCATED,
                "SOLO_PREPARATION_RESERVED",
                "",
                Math.max(0L, gameTime)
            ),
            Math.max(0L, gameTime)
        );
        allocationsByCell.put(cellIndex, allocation);
        rebuildIndexes();
        setDirty();
        return ReservationResult.accepted(
            allocation,
            true,
            reusable == null ? "NEW_SOLO_CELL" : "REUSED_SOLO_CELL"
        );
    }

    public ReservationResult requestSoloPreparation(
        String dimension,
        UUID playerId,
        BlockPos gridAnchor,
        int minimumBuildHeight,
        int maximumBuildHeight,
        List<RoomControllerBlockEntity.RoomBounds> plannedBoundsAtAnchor,
        int requestedFloor,
        int targetRooms,
        Direction entryDirection,
        long gameTime
    ) {
        return requestSoloPreparation(
            dimension,
            playerId,
            gridAnchor,
            minimumBuildHeight,
            maximumBuildHeight,
            plannedBoundsAtAnchor,
            requestedFloor,
            targetRooms,
            entryDirection,
            List.of(FrozenParticipant.host(playerId, -1L)),
            gameTime
        );
    }

    public ReservationResult requestSoloPreparation(
        String dimension,
        UUID playerId,
        BlockPos gridAnchor,
        int minimumBuildHeight,
        int maximumBuildHeight,
        List<RoomControllerBlockEntity.RoomBounds> plannedBoundsAtAnchor,
        int requestedFloor,
        int targetRooms,
        Direction entryDirection,
        List<FrozenParticipant> frozenParticipants,
        long gameTime
    ) {
        if (requestedFloor < 1 || targetRooms < 1 || entryDirection == null
            || !entryDirection.getAxis().isHorizontal()
            || !validFrozenParticipants(playerId, frozenParticipants)) {
            return ReservationResult.rejected("INVALID_SOLO_PREPARATION_PLAN", null);
        }
        ReservationResult reservation = reserveSolo(
            dimension,
            playerId,
            gridAnchor,
            minimumBuildHeight,
            maximumBuildHeight,
            plannedBoundsAtAnchor,
            gameTime
        );
        if (!reservation.accepted() || reservation.allocation() == null) {
            return reservation;
        }
        Allocation allocation = reservation.allocation();
        if (allocation.soloPreparation() != null) {
            if (!allocation.soloPreparation().frozenParticipants().equals(frozenParticipants)) {
                return ReservationResult.rejected(
                    "FROZEN_PARTICIPANT_ROSTER_MISMATCH",
                    allocation
                );
            }
            return ReservationResult.accepted(
                allocation,
                false,
                "EXISTING_SOLO_PREPARATION"
            );
        }
        if (allocation.lifecycle().state() != LifecycleState.ALLOCATED) {
            return ReservationResult.rejected("SOLO_INSTANCE_NOT_PREPARABLE", allocation);
        }
        SoloPreparation preparation = new SoloPreparation(
            requestedFloor,
            targetRooms,
            entryDirection,
            allocation.cell().origin(),
            soloPlanSeed(allocation.key(), requestedFloor, targetRooms, entryDirection),
            frozenParticipants,
            gameTime
        );
        Allocation prepared = allocation.withPreparation(
            preparation,
            new Lifecycle(
                LifecycleState.ALLOCATED,
                "SOLO_PREPARATION_REQUESTED",
                "",
                Math.max(0L, gameTime)
            )
        );
        allocationsByCell.put(prepared.cell().index(), prepared);
        rebuildIndexes();
        setDirty();
        return ReservationResult.accepted(
            prepared,
            reservation.created(),
            "SOLO_PREPARATION_REQUESTED"
        );
    }

    private static boolean validFrozenParticipants(
        UUID hostId,
        List<FrozenParticipant> participants
    ) {
        if (hostId == null || participants == null || participants.isEmpty()
            || participants.size() > 4) {
            return false;
        }
        Set<UUID> identities = new LinkedHashSet<>();
        for (int index = 0; index < participants.size(); index++) {
            FrozenParticipant participant = participants.get(index);
            if (participant == null || participant.slot() != index + 1
                || !identities.add(participant.id())) {
                return false;
            }
            if (index == 0) {
                if (!participant.id().equals(hostId)
                    || participant.kind() != FrozenParticipantKind.HUMAN
                    || !participant.ownerId().equals(hostId)) {
                    return false;
                }
            } else if (participant.kind() != FrozenParticipantKind.SUBORDINATE
                || !participant.ownerId().equals(hostId)) {
                return false;
            }
        }
        return true;
    }

    public CancellationResult cancelSoloPreparation(UUID playerId, long gameTime) {
        Allocation allocation = forPlayer(playerId).orElse(null);
        if (allocation == null) {
            return CancellationResult.rejected("NO_SOLO_PREPARATION", null);
        }
        if (allocation.lifecycle().state() != LifecycleState.ALLOCATED
            || allocation.soloPreparation() == null) {
            return CancellationResult.rejected("SOLO_PREPARATION_ALREADY_STARTED", allocation);
        }
        if (!transition(
            allocation.key(),
            LifecycleState.CLEANING,
            "SOLO_PREPARATION_CANCELED_BEFORE_MUTATION",
            gameTime
        ) || !markReusable(
            allocation.key(),
            "EMPTY_SOLO_PREPARATION_CANCELED",
            gameTime
        )) {
            return CancellationResult.rejected("SOLO_PREPARATION_CANCEL_FAILED", allocation);
        }
        return CancellationResult.accepted(allocation.key());
    }

    public Allocation adoptLegacyGeneratedFloor(
        String dimension,
        BlockPos floorBuildOrigin,
        int minimumBuildHeight,
        int maximumBuildHeight,
        List<RoomControllerBlockEntity.RoomBounds> ownedBounds,
        LifecycleState state,
        long gameTime
    ) {
        String normalizedDimension = normalizeDimension(dimension);
        Allocation current = currentForDimension(normalizedDimension).orElse(null);
        if (current != null) {
            return current;
        }
        UUID instanceId = UUID.nameUUIDFromBytes(
            ("tensura-dungeon-legacy:" + normalizedDimension + ":" + floorBuildOrigin.asLong())
                .getBytes(StandardCharsets.UTF_8)
        );
        Cell cell = new Cell(
            0,
            floorBuildOrigin.immutable(),
            boundsAround(floorBuildOrigin, minimumBuildHeight, maximumBuildHeight)
        );
        String quarantineReason = boundsInside(cell.bounds(), ownedBounds)
            ? ""
            : "LEGACY_BOUNDS_OUTSIDE_CELL";
        LifecycleState adoptedState = quarantineReason.isEmpty()
            ? state
            : LifecycleState.QUARANTINED;
        Allocation allocation = new Allocation(
            new InstanceKey(instanceId, 1L),
            normalizedDimension,
            cell,
            LEGACY_HOST,
            Set.of(),
            null,
            null,
            Map.of(),
            new Lifecycle(
                adoptedState,
                "ADOPTED_DIMENSION_GLOBAL_GENERATED_STATE",
                quarantineReason,
                Math.max(0L, gameTime)
            ),
            Math.max(0L, gameTime)
        );
        allocationsByCell.put(cell.index(), allocation);
        rebuildIndexes();
        setDirty();
        return allocation;
    }

    public Optional<Allocation> currentForDimension(String dimension) {
        String normalizedDimension = normalizeDimension(dimension);
        return allocationsByCell.values().stream()
            .filter(allocation -> allocation.dimension().equals(normalizedDimension))
            .filter(allocation -> !allocation.lifecycle().state().reusable())
            .min(Comparator.comparingInt(allocation -> allocation.cell().index()));
    }

    public Optional<Allocation> forPlayer(UUID playerId) {
        UUID instanceId = playerToInstance.get(playerId);
        if (instanceId == null) {
            return Optional.empty();
        }
        return allocationsByCell.values().stream()
            .filter(allocation -> allocation.key().instanceId().equals(instanceId))
            .findFirst();
    }

    public RevisionRefreshResult refreshFrozenParticipantRevisions(
        InstanceKey key,
        List<FrozenParticipant> requested
    ) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null || allocation.soloPreparation() == null) {
            return RevisionRefreshResult.rejected("NO_FROZEN_PREPARATION", allocation);
        }
        if (allocation.lifecycle().state() != LifecycleState.PREPARING
            && allocation.lifecycle().state() != LifecycleState.READY) {
            return RevisionRefreshResult.rejected(
                "FROZEN_REVISION_REFRESH_STATE_" + allocation.lifecycle().state(), allocation
            );
        }
        List<FrozenParticipant> frozen = allocation.soloPreparation().frozenParticipants();
        if (!sameFrozenParticipantIdentities(frozen, requested)
            || requested.stream().anyMatch(participant -> participant.loadoutRevision() < 0L)) {
            return RevisionRefreshResult.rejected(
                "FROZEN_PARTICIPANT_IDENTITY_MISMATCH", allocation
            );
        }
        if (frozen.equals(requested)) {
            return RevisionRefreshResult.accepted("FROZEN_REVISIONS_UNCHANGED", allocation, false);
        }
        SoloPreparation previous = allocation.soloPreparation();
        SoloPreparation replacement = new SoloPreparation(
            previous.requestedFloor(), previous.targetRooms(), previous.entryDirection(),
            previous.attachmentTarget(), previous.planSeed(), requested,
            previous.requestedGameTime()
        );
        Allocation refreshed = allocation.withPreparation(replacement, allocation.lifecycle());
        allocationsByCell.put(allocation.cell().index(), refreshed);
        setDirty();
        return RevisionRefreshResult.accepted("FROZEN_REVISIONS_REFRESHED", refreshed, true);
    }

    private static boolean sameFrozenParticipantIdentities(
        List<FrozenParticipant> frozen,
        List<FrozenParticipant> requested
    ) {
        if (frozen == null || requested == null || frozen.size() != requested.size()) {
            return false;
        }
        for (int index = 0; index < frozen.size(); index++) {
            FrozenParticipant left = frozen.get(index);
            FrozenParticipant right = requested.get(index);
            if (!left.id().equals(right.id()) || left.kind() != right.kind()
                || !left.ownerId().equals(right.ownerId()) || left.slot() != right.slot()
                || right.loadoutRevision() < left.loadoutRevision()) {
                return false;
            }
        }
        return true;
    }

    public boolean claimLegacyPlayer(InstanceKey key, UUID playerId) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null || playerId == null || !LEGACY_HOST.equals(allocation.host())
            || !allocation.roster().isEmpty() || allocation.lifecycle().state().reusable()
            || allocation.lifecycle().state() == LifecycleState.QUARANTINED) {
            return false;
        }
        allocationsByCell.put(
            allocation.cell().index(),
            new Allocation(
                allocation.key(), allocation.dimension(), allocation.cell(), playerId,
                Set.of(playerId), allocation.soloPreparation(), allocation.entryDispatch(),
                Map.of(), allocation.lifecycle(),
                allocation.createdGameTime()
            )
        );
        rebuildIndexes();
        setDirty();
        return true;
    }

    public List<Allocation> allocations() {
        return allocationsByCell.values().stream()
            .sorted(Comparator.comparingInt(allocation -> allocation.cell().index()))
            .toList();
    }

    public GridAnchorResolution establishedGridAnchor(String dimension) {
        String normalizedDimension = normalizeDimension(dimension);
        BlockPos resolved = null;
        int cells = 0;
        for (Allocation allocation : allocationsByCell.values()) {
            if (!allocation.dimension().equals(normalizedDimension)) {
                continue;
            }
            cells++;
            BlockPos candidate = gridAnchorForCell(allocation.cell());
            if (resolved == null) {
                resolved = candidate;
            } else if (!resolved.equals(candidate)) {
                return GridAnchorResolution.failed("INCONSISTENT_PERSISTED_CELL_GRID", cells);
            }
        }
        return resolved == null
            ? GridAnchorResolution.failed("NO_PERSISTED_CELL_GRID", 0)
            : GridAnchorResolution.resolved(resolved, cells);
    }

    public GridRelocationResult relocateReusableGrid(
        String dimension,
        BlockPos expectedGridAnchor,
        BlockPos replacementGridAnchor,
        int minimumBuildHeight,
        int maximumBuildHeight
    ) {
        if (expectedGridAnchor == null || replacementGridAnchor == null
            || maximumBuildHeight <= minimumBuildHeight) {
            return GridRelocationResult.rejected("INVALID_GRID_RELOCATION_REQUEST", 0);
        }
        String normalizedDimension = normalizeDimension(dimension);
        List<Allocation> retained = allocationsByCell.values().stream()
            .filter(allocation -> allocation.dimension().equals(normalizedDimension))
            .toList();
        if (retained.isEmpty()) {
            return GridRelocationResult.rejected("NO_PERSISTED_CELL_GRID", 0);
        }
        GridAnchorResolution established = establishedGridAnchor(normalizedDimension);
        if (!established.resolved() || !expectedGridAnchor.equals(established.gridAnchor())) {
            return GridRelocationResult.rejected("PERSISTED_GRID_CHANGED", retained.size());
        }
        if (retained.stream().anyMatch(allocation -> !allocation.lifecycle().state().reusable())) {
            return GridRelocationResult.rejected(
                "GRID_RELOCATION_DEFERRED_UNTIL_ALL_CELLS_REUSABLE", retained.size()
            );
        }
        for (Allocation allocation : retained) {
            BlockPos origin = originForCell(replacementGridAnchor, allocation.cell().index());
            Cell replacement = new Cell(
                allocation.cell().index(),
                origin,
                boundsAround(origin, minimumBuildHeight, maximumBuildHeight)
            );
            allocationsByCell.put(
                replacement.index(),
                new Allocation(
                    allocation.key(), allocation.dimension(), replacement, allocation.host(),
                    allocation.roster(), null, null, Map.of(), allocation.lifecycle(),
                    allocation.createdGameTime()
                )
            );
        }
        rebuildIndexes();
        setDirty();
        return GridRelocationResult.relocated(retained.size(), replacementGridAnchor);
    }

    public Optional<InstanceKey> currentIdentity(String dimension) {
        return currentForDimension(dimension).map(Allocation::key);
    }

    public boolean matchesCurrent(String dimension, @Nullable InstanceKey expected) {
        Optional<InstanceKey> current = currentIdentity(dimension);
        return expected == null ? current.isEmpty() : current.filter(expected::equals).isPresent();
    }

    public boolean matchesLive(String dimension, @Nullable InstanceKey expected) {
        if (expected == null) {
            return false;
        }
        String normalizedDimension = normalizeDimension(dimension);
        return find(expected)
            .filter(allocation -> allocation.dimension().equals(normalizedDimension))
            .filter(allocation -> !allocation.lifecycle().state().reusable())
            .filter(allocation -> allocation.lifecycle().state() != LifecycleState.QUARANTINED)
            .isPresent();
    }

    public Optional<Allocation> forIdentity(InstanceKey key) {
        return key == null ? Optional.empty() : find(key);
    }

    public String validateOwnedBounds(
        String dimension,
        @Nullable InstanceKey expected,
        RoomControllerBlockEntity.RoomBounds bounds
    ) {
        String normalizedDimension = normalizeDimension(dimension);
        Allocation current = expected == null
            ? currentForDimension(normalizedDimension).orElse(null)
            : find(expected)
                .filter(allocation -> allocation.dimension().equals(normalizedDimension))
                .filter(allocation -> !allocation.lifecycle().state().reusable())
                .orElse(null);
        if (current == null) {
            return expected == null ? "NO_ALLOCATION" : "STALE_INSTANCE_ALLOCATION";
        }
        if (expected != null && !current.key().equals(expected)) {
            return "STALE_INSTANCE_ALLOCATION";
        }
        return current.cell().bounds().contains(bounds)
            ? "VALID_INSTANCE_CELL_BOUNDS"
            : "OUTSIDE_INSTANCE_CELL_BOUNDS";
    }

    public boolean transition(
        InstanceKey key,
        LifecycleState next,
        String reason,
        long gameTime
    ) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null || allocation.lifecycle().state() == LifecycleState.QUARANTINED) {
            return false;
        }
        if (!allocation.lifecycle().state().canTransitionTo(next)) {
            return false;
        }
        allocationsByCell.put(
            allocation.cell().index(),
            allocation.withLifecycle(new Lifecycle(
                next,
                normalizeReason(reason),
                "",
                Math.max(0L, gameTime)
            ))
        );
        rebuildIndexes();
        setDirty();
        return true;
    }

    public boolean quarantine(InstanceKey key, String reason, long gameTime) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null) {
            return false;
        }
        String quarantineReason = normalizeReason(reason);
        allocationsByCell.put(
            allocation.cell().index(),
            allocation.withLifecycle(new Lifecycle(
                LifecycleState.QUARANTINED,
                "CELL_QUARANTINED",
                quarantineReason,
                Math.max(0L, gameTime)
            ))
        );
        rebuildIndexes();
        setDirty();
        return true;
    }

    public boolean markReusable(InstanceKey key, String reason, long gameTime) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null || allocation.lifecycle().state() != LifecycleState.CLEANING) {
            return false;
        }
        allocationsByCell.put(
            allocation.cell().index(),
            new Allocation(
                allocation.key(), allocation.dimension(), allocation.cell(), allocation.host(),
                allocation.roster(), null, null, Map.of(),
                new Lifecycle(
                    LifecycleState.REUSABLE, normalizeReason(reason), "", Math.max(0L, gameTime)
                ),
                allocation.createdGameTime()
            )
        );
        rebuildIndexes();
        setDirty();
        return true;
    }

    public DispatchResult prepareSoloDispatch(
        InstanceKey key,
        BlockPos entryController,
        BlockPos entryDestination,
        BlockPos hubReturnTarget,
        long gameTime
    ) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null) {
            return DispatchResult.rejected("SOLO_DISPATCH_ALLOCATION_MISSING", null);
        }
        if (allocation.lifecycle().state() != LifecycleState.READY
            && allocation.lifecycle().state() != LifecycleState.ACTIVE
            && allocation.lifecycle().state() != LifecycleState.SUSPENDED) {
            return DispatchResult.rejected("SOLO_DISPATCH_NOT_READY", allocation);
        }
        if (allocation.soloPreparation() == null || allocation.roster().size() != 1
            || !allocation.roster().contains(allocation.host())) {
            return DispatchResult.rejected("SOLO_DISPATCH_ROSTER_INVALID", allocation);
        }
        if (!allocation.cell().bounds().contains(entryController)
            || !allocation.cell().bounds().contains(entryDestination)
            || hubReturnTarget == null) {
            return DispatchResult.rejected("SOLO_DISPATCH_CHECKPOINT_INVALID", allocation);
        }
        EntryDispatch requested = new EntryDispatch(
            entryController,
            entryDestination,
            hubReturnTarget,
            DispatchState.PENDING_TELEPORT,
            Math.max(0L, gameTime)
        );
        if (allocation.entryDispatch() != null) {
            EntryDispatch existing = allocation.entryDispatch();
            if (!existing.sameRoute(requested)) {
                return DispatchResult.rejected("SOLO_DISPATCH_ROUTE_MISMATCH", allocation);
            }
            return DispatchResult.accepted("EXISTING_SOLO_DISPATCH", allocation, false);
        }
        Allocation prepared = allocation.withEntryDispatch(requested);
        allocationsByCell.put(prepared.cell().index(), prepared);
        rebuildIndexes();
        setDirty();
        return DispatchResult.accepted("SOLO_DISPATCH_PREPARED", prepared, true);
    }

    public DispatchResult markSoloDispatched(InstanceKey key, long gameTime) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null || allocation.entryDispatch() == null) {
            return DispatchResult.rejected("SOLO_DISPATCH_NOT_PREPARED", allocation);
        }
        if (allocation.lifecycle().state() != LifecycleState.READY
            && allocation.lifecycle().state() != LifecycleState.ACTIVE
            && allocation.lifecycle().state() != LifecycleState.SUSPENDED) {
            return DispatchResult.rejected("SOLO_DISPATCH_NOT_READY", allocation);
        }
        EntryDispatch existing = allocation.entryDispatch();
        if (existing.state() == DispatchState.ADMITTED
            && allocation.lifecycle().state() == LifecycleState.ACTIVE) {
            if (allocation.memberDisposition(allocation.host())
                != MemberDisposition.IN_INSTANCE) {
                Allocation resumed = allocation.withLifecycleAndMemberDisposition(
                    allocation.lifecycle(), allocation.host(), MemberDisposition.IN_INSTANCE
                );
                allocationsByCell.put(resumed.cell().index(), resumed);
                rebuildIndexes();
                setDirty();
                return DispatchResult.accepted("SOLO_DISPATCH_REACTIVATED", resumed, true);
            }
            return DispatchResult.accepted("SOLO_DISPATCH_ALREADY_ADMITTED", allocation, false);
        }
        Lifecycle active = new Lifecycle(
            LifecycleState.ACTIVE,
            "PLAYER_DISPATCHED",
            "",
            Math.max(0L, gameTime)
        );
        Allocation admitted = allocation.withLifecycleAndEntryDispatch(
            active,
            existing.withState(DispatchState.ADMITTED, gameTime)
        );
        if (allocation.memberDisposition(allocation.host())
            != MemberDisposition.IN_INSTANCE) {
            admitted = admitted.withLifecycleAndMemberDisposition(
                active, allocation.host(), MemberDisposition.IN_INSTANCE
            );
        }
        allocationsByCell.put(admitted.cell().index(), admitted);
        rebuildIndexes();
        setDirty();
        return DispatchResult.accepted("SOLO_DISPATCH_ADMITTED", admitted, true);
    }

    public MemberDispositionResult markMemberDisconnected(UUID playerId, long gameTime) {
        Allocation allocation = forPlayer(playerId).orElse(null);
        if (allocation == null || allocation.entryDispatch() == null
            || allocation.entryDispatch().state() != DispatchState.ADMITTED) {
            return MemberDispositionResult.rejected("MEMBER_NOT_ADMITTED", allocation);
        }
        LifecycleState state = allocation.lifecycle().state();
        if (state != LifecycleState.ACTIVE && state != LifecycleState.SUSPENDED) {
            return MemberDispositionResult.rejected("INSTANCE_NOT_RESUMABLE", allocation);
        }
        if (allocation.roster().size() != 1) {
            return MemberDispositionResult.rejected("PARTY_SUSPENSION_DEFERRED_TO_STAGE_5C_E2", allocation);
        }
        Lifecycle lifecycle = state == LifecycleState.SUSPENDED
            ? allocation.lifecycle()
            : new Lifecycle(
                LifecycleState.SUSPENDED,
                "SOLO_MEMBER_DISCONNECTED",
                "",
                Math.max(0L, gameTime)
            );
        Allocation suspended = allocation.withLifecycleAndMemberDisposition(
            lifecycle, playerId, MemberDisposition.RECONNECT_TO_INSTANCE
        );
        if (suspended.equals(allocation)) {
            return MemberDispositionResult.accepted("MEMBER_ALREADY_SUSPENDED", allocation, false);
        }
        allocationsByCell.put(suspended.cell().index(), suspended);
        rebuildIndexes();
        setDirty();
        return MemberDispositionResult.accepted("MEMBER_SUSPENDED", suspended, true);
    }

    public MemberDispositionResult markMemberResumed(
        InstanceKey key,
        UUID playerId,
        long gameTime
    ) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null || !allocation.roster().contains(playerId)) {
            return MemberDispositionResult.rejected("MEMBER_ALLOCATION_MISMATCH", allocation);
        }
        if (allocation.memberDisposition(playerId) == MemberDisposition.IN_INSTANCE
            && allocation.lifecycle().state() == LifecycleState.ACTIVE) {
            return MemberDispositionResult.accepted("MEMBER_ALREADY_ACTIVE", allocation, false);
        }
        if (allocation.memberDisposition(playerId) != MemberDisposition.RECONNECT_TO_INSTANCE
            || allocation.lifecycle().state() != LifecycleState.SUSPENDED
            || allocation.entryDispatch() == null
            || allocation.entryDispatch().state() != DispatchState.ADMITTED) {
            return MemberDispositionResult.rejected("MEMBER_NOT_RESUMABLE", allocation);
        }
        Lifecycle active = new Lifecycle(
            LifecycleState.ACTIVE,
            "SOLO_MEMBER_RECONNECTED",
            "",
            Math.max(0L, gameTime)
        );
        Allocation resumed = allocation.withLifecycleAndMemberDisposition(
            active, playerId, MemberDisposition.IN_INSTANCE
        );
        allocationsByCell.put(resumed.cell().index(), resumed);
        rebuildIndexes();
        setDirty();
        return MemberDispositionResult.accepted("MEMBER_RESUMED", resumed, true);
    }

    public MemberDispositionResult requireHubReturnOnLogin(
        InstanceKey key,
        UUID playerId,
        String reason,
        long gameTime
    ) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null || !allocation.roster().contains(playerId)) {
            return MemberDispositionResult.rejected("MEMBER_ALLOCATION_MISMATCH", allocation);
        }
        Lifecycle lifecycle = allocation.lifecycle().state() == LifecycleState.ACTIVE
            ? new Lifecycle(
                LifecycleState.SUSPENDED,
                normalizeReason(reason),
                "",
                Math.max(0L, gameTime)
            )
            : allocation.lifecycle();
        if (lifecycle.state() != LifecycleState.SUSPENDED) {
            return MemberDispositionResult.rejected("INSTANCE_NOT_SUSPENDABLE", allocation);
        }
        Allocation suspended = allocation.withLifecycleAndMemberDisposition(
            lifecycle, playerId, MemberDisposition.RETURN_TO_HUB_ON_LOGIN
        );
        allocationsByCell.put(suspended.cell().index(), suspended);
        rebuildIndexes();
        setDirty();
        return MemberDispositionResult.accepted("HUB_RETURN_REQUIRED", suspended, !suspended.equals(allocation));
    }

    public TerminalDispositionResult beginTerminalDisposition(
        InstanceKey key,
        String reason,
        long gameTime
    ) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null) {
            return TerminalDispositionResult.rejected("TERMINAL_ALLOCATION_MISSING", null);
        }
        LifecycleState state = allocation.lifecycle().state();
        if (state == LifecycleState.CLEANING) {
            return TerminalDispositionResult.accepted(
                "TERMINAL_CLEANUP_ALREADY_STARTED", allocation, false
            );
        }
        if (state != LifecycleState.ACTIVE && state != LifecycleState.SUSPENDED
            && state != LifecycleState.COMPLETING) {
            return TerminalDispositionResult.rejected(
                "TERMINAL_LIFECYCLE_INVALID_" + state.name(), allocation
            );
        }
        Map<UUID, MemberDisposition> dispositions = new LinkedHashMap<>();
        allocation.roster().forEach(
            playerId -> dispositions.put(playerId, MemberDisposition.RETURN_TO_HUB_ON_LOGIN)
        );
        Lifecycle completing = state == LifecycleState.COMPLETING
            ? allocation.lifecycle()
            : new Lifecycle(
                LifecycleState.COMPLETING,
                normalizeReason(reason),
                "",
                Math.max(0L, gameTime)
            );
        Allocation terminal = allocation.withLifecycleAndMemberDispositions(
            completing, dispositions
        );
        if (terminal.equals(allocation)) {
            return TerminalDispositionResult.accepted(
                "TERMINAL_DISPOSITION_ALREADY_PERSISTED", allocation, false
            );
        }
        allocationsByCell.put(terminal.cell().index(), terminal);
        rebuildIndexes();
        setDirty();
        return TerminalDispositionResult.accepted(
            "TERMINAL_DISPOSITION_PERSISTED", terminal, true
        );
    }

    public TerminalDispositionResult beginUndispatchedReadyDisposition(
        InstanceKey key,
        UUID playerId,
        String reason,
        long gameTime
    ) {
        Allocation allocation = find(key).orElse(null);
        if (allocation == null) {
            return TerminalDispositionResult.rejected("READY_ALLOCATION_MISSING", null);
        }
        if (allocation.lifecycle().state() != LifecycleState.READY) {
            return TerminalDispositionResult.rejected(
                "READY_LIFECYCLE_INVALID_" + allocation.lifecycle().state().name(), allocation
            );
        }
        if (playerId == null || allocation.soloPreparation() == null
            || !allocation.roster().equals(Set.of(playerId))) {
            return TerminalDispositionResult.rejected(
                "READY_SOLO_OWNERSHIP_MISMATCH", allocation
            );
        }
        if (allocation.entryDispatch() != null
            && allocation.entryDispatch().state() == DispatchState.ADMITTED) {
            return TerminalDispositionResult.rejected(
                "READY_PLAYER_ALREADY_ADMITTED", allocation
            );
        }
        Map<UUID, MemberDisposition> dispositions = Map.of(
            playerId, MemberDisposition.RETURN_TO_HUB_ON_LOGIN
        );
        Allocation terminal = allocation.withLifecycleAndMemberDispositions(
            new Lifecycle(
                LifecycleState.COMPLETING,
                normalizeReason(reason),
                "",
                Math.max(0L, gameTime)
            ),
            dispositions
        );
        allocationsByCell.put(terminal.cell().index(), terminal);
        rebuildIndexes();
        setDirty();
        return TerminalDispositionResult.accepted(
            "UNDISPATCHED_READY_DISPOSITION_PERSISTED", terminal, true
        );
    }

    static BlockPos originForCell(BlockPos anchor, int cellIndex) {
        if (cellIndex < 0) {
            throw new IllegalArgumentException("Dungeon instance cell index must be non-negative");
        }
        int gridX = cellIndex % 4;
        int gridZ = cellIndex / 4;
        return anchor.offset(gridX * CELL_STRIDE, 0, gridZ * CELL_STRIDE);
    }

    private static BlockPos gridAnchorForCell(Cell cell) {
        int gridX = cell.index() % 4;
        int gridZ = cell.index() / 4;
        return cell.origin().offset(-gridX * CELL_STRIDE, 0, -gridZ * CELL_STRIDE);
    }

    private int firstUnallocatedSoloCell() {
        for (int cellIndex = 0; cellIndex < MAX_CONCURRENT_SOLO_INSTANCES; cellIndex++) {
            if (!allocationsByCell.containsKey(cellIndex)) {
                return cellIndex;
            }
        }
        return -1;
    }

    private static List<RoomControllerBlockEntity.RoomBounds> translateBounds(
        List<RoomControllerBlockEntity.RoomBounds> bounds,
        BlockPos offset
    ) {
        if (bounds == null) {
            return List.of();
        }
        return bounds.stream()
            .map(value -> value == null ? null : new RoomControllerBlockEntity.RoomBounds(
                value.min().offset(offset),
                value.max().offset(offset)
            ))
            .toList();
    }

    private static long soloPlanSeed(
        InstanceKey key,
        int requestedFloor,
        int targetRooms,
        Direction entryDirection
    ) {
        long seed = key.instanceId().getMostSignificantBits()
            ^ Long.rotateLeft(key.instanceId().getLeastSignificantBits(), 17)
            ^ Long.rotateLeft(key.epoch(), 31);
        seed ^= (long) requestedFloor * 0x9E3779B97F4A7C15L;
        seed ^= (long) targetRooms * 0xC2B2AE3D27D4EB4FL;
        return seed ^ ((long) entryDirection.get2DDataValue() << 56);
    }

    private Optional<Allocation> find(InstanceKey key) {
        return allocationsByCell.values().stream()
            .filter(allocation -> allocation.key().equals(key))
            .findFirst();
    }

    private void rebuildIndexes() {
        playerToInstance.clear();
        for (Allocation allocation : allocationsByCell.values()) {
            if (allocation.lifecycle().state().reusable()) {
                continue;
            }
            for (UUID playerId : allocation.roster()) {
                UUID duplicate = playerToInstance.putIfAbsent(
                    playerId, allocation.key().instanceId()
                );
                if (duplicate != null && !duplicate.equals(allocation.key().instanceId())) {
                    throw new IllegalStateException(
                        "Dungeon player belongs to multiple live instance allocations: " + playerId
                    );
                }
            }
        }
    }

    private static boolean boundsInside(
        CellBounds cellBounds,
        List<RoomControllerBlockEntity.RoomBounds> ownedBounds
    ) {
        if (ownedBounds == null) {
            return true;
        }
        return ownedBounds.stream().allMatch(
            bounds -> bounds != null && cellBounds.contains(bounds)
        );
    }

    private static CellBounds boundsAround(
        BlockPos origin,
        int minimumBuildHeight,
        int maximumBuildHeight
    ) {
        int half = CELL_DIAMETER / 2;
        int maximumY = Math.max(minimumBuildHeight, maximumBuildHeight - 1);
        return new CellBounds(
            new BlockPos(origin.getX() - half, minimumBuildHeight, origin.getZ() - half),
            new BlockPos(origin.getX() + half - 1, maximumY, origin.getZ() + half - 1)
        );
    }

    private static Allocation loadAllocation(CompoundTag tag) {
        if (!tag.hasUUID(INSTANCE_ID_KEY)
            || !tag.contains(CELL_ORIGIN_KEY)
            || !tag.contains(CELL_MIN_KEY)
            || !tag.contains(CELL_MAX_KEY)) {
            throw new IllegalStateException("Dungeon instance allocation is missing required identity or cell bounds");
        }
        UUID instanceId = tag.getUUID(INSTANCE_ID_KEY);
        long epoch = tag.getLong(EPOCH_KEY);
        if (epoch <= 0L) {
            throw new IllegalStateException("Dungeon instance allocation epoch must be positive");
        }
        int cellIndex = tag.getInt(CELL_INDEX_KEY);
        if (cellIndex < 0) {
            throw new IllegalStateException("Dungeon instance cell index must be non-negative");
        }
        BlockPos origin = readRequiredPos(tag, CELL_ORIGIN_KEY);
        CellBounds bounds = new CellBounds(
            readRequiredPos(tag, CELL_MIN_KEY),
            readRequiredPos(tag, CELL_MAX_KEY)
        );
        UUID host = tag.hasUUID(HOST_KEY) ? tag.getUUID(HOST_KEY) : LEGACY_HOST;
        Set<UUID> roster = new LinkedHashSet<>();
        ListTag rosterTag = tag.getList(ROSTER_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < rosterTag.size(); index++) {
            CompoundTag playerTag = rosterTag.getCompound(index);
            if (!playerTag.hasUUID(PLAYER_KEY)) {
                throw new IllegalStateException("Dungeon instance roster entry is missing player UUID");
            }
            if (!roster.add(playerTag.getUUID(PLAYER_KEY))) {
                throw new IllegalStateException("Duplicate player in dungeon instance roster");
            }
        }
        LifecycleState state = LifecycleState.parse(tag.getString(STATE_KEY));
        String quarantineReason = tag.getString(QUARANTINE_REASON_KEY);
        if (state == LifecycleState.QUARANTINED && quarantineReason.isBlank()) {
            quarantineReason = "MISSING_PERSISTED_QUARANTINE_REASON";
        }
        SoloPreparation soloPreparation = tag.contains(SOLO_PREPARATION_KEY, Tag.TAG_COMPOUND)
            ? loadSoloPreparation(tag.getCompound(SOLO_PREPARATION_KEY), host)
            : null;
        EntryDispatch entryDispatch = tag.contains(ENTRY_DISPATCH_KEY, Tag.TAG_COMPOUND)
            ? loadEntryDispatch(tag.getCompound(ENTRY_DISPATCH_KEY))
            : null;
        Map<UUID, MemberDisposition> memberDispositions = new LinkedHashMap<>();
        ListTag dispositionTags = tag.getList(MEMBER_DISPOSITIONS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < dispositionTags.size(); index++) {
            CompoundTag dispositionTag = dispositionTags.getCompound(index);
            if (!dispositionTag.hasUUID(PLAYER_KEY)) {
                throw new IllegalStateException("Dungeon member disposition is missing player UUID");
            }
            UUID playerId = dispositionTag.getUUID(PLAYER_KEY);
            if (!roster.contains(playerId) || memberDispositions.putIfAbsent(
                playerId,
                MemberDisposition.parse(dispositionTag.getString(MEMBER_DISPOSITION_KEY))
            ) != null) {
                throw new IllegalStateException("Invalid dungeon member disposition owner");
            }
        }
        return new Allocation(
            new InstanceKey(instanceId, epoch),
            normalizeDimension(tag.getString(DIMENSION_KEY)),
            new Cell(cellIndex, origin, bounds),
            host,
            roster,
            soloPreparation,
            entryDispatch,
            memberDispositions,
            new Lifecycle(
                state,
                normalizeReason(tag.getString(TRANSITION_REASON_KEY)),
                quarantineReason,
                Math.max(0L, tag.getLong(TRANSITION_GAME_TIME_KEY))
            ),
            Math.max(0L, tag.getLong(CREATED_GAME_TIME_KEY))
        );
    }

    private static CompoundTag saveAllocation(Allocation allocation) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(INSTANCE_ID_KEY, allocation.key().instanceId());
        tag.putLong(EPOCH_KEY, allocation.key().epoch());
        tag.putString(DIMENSION_KEY, allocation.dimension());
        tag.putInt(CELL_INDEX_KEY, allocation.cell().index());
        tag.put(CELL_ORIGIN_KEY, NbtUtils.writeBlockPos(allocation.cell().origin()));
        tag.put(CELL_MIN_KEY, NbtUtils.writeBlockPos(allocation.cell().bounds().min()));
        tag.put(CELL_MAX_KEY, NbtUtils.writeBlockPos(allocation.cell().bounds().max()));
        tag.putUUID(HOST_KEY, allocation.host());
        ListTag roster = new ListTag();
        allocation.roster().stream()
            .sorted(Comparator.comparing(UUID::toString))
            .forEach(playerId -> {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID(PLAYER_KEY, playerId);
                roster.add(playerTag);
            });
        tag.put(ROSTER_KEY, roster);
        tag.putString(STATE_KEY, allocation.lifecycle().state().name());
        tag.putString(TRANSITION_REASON_KEY, allocation.lifecycle().transitionReason());
        tag.putString(QUARANTINE_REASON_KEY, allocation.lifecycle().quarantineReason());
        tag.putLong(CREATED_GAME_TIME_KEY, allocation.createdGameTime());
        tag.putLong(TRANSITION_GAME_TIME_KEY, allocation.lifecycle().transitionGameTime());
        if (allocation.soloPreparation() != null) {
            tag.put(SOLO_PREPARATION_KEY, saveSoloPreparation(allocation.soloPreparation()));
        }
        if (allocation.entryDispatch() != null) {
            tag.put(ENTRY_DISPATCH_KEY, saveEntryDispatch(allocation.entryDispatch()));
        }
        ListTag dispositions = new ListTag();
        allocation.memberDispositions().entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .forEach(entry -> {
                CompoundTag dispositionTag = new CompoundTag();
                dispositionTag.putUUID(PLAYER_KEY, entry.getKey());
                dispositionTag.putString(MEMBER_DISPOSITION_KEY, entry.getValue().name());
                dispositions.add(dispositionTag);
            });
        tag.put(MEMBER_DISPOSITIONS_KEY, dispositions);
        return tag;
    }

    private static SoloPreparation loadSoloPreparation(CompoundTag tag, UUID hostId) {
        Direction entryDirection = Direction.byName(tag.getString(ENTRY_DIRECTION_KEY));
        BlockPos attachmentTarget = readRequiredPos(tag, ATTACHMENT_TARGET_KEY);
        List<FrozenParticipant> participants = new ArrayList<>();
        ListTag participantTags = tag.getList(FROZEN_PARTICIPANTS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < participantTags.size() && participants.size() < 4; index++) {
            CompoundTag participantTag = participantTags.getCompound(index);
            if (!participantTag.hasUUID(PARTICIPANT_ID_KEY)
                || !participantTag.hasUUID(PARTICIPANT_OWNER_KEY)) {
                throw new IllegalStateException("Frozen dungeon participant is missing identity");
            }
            participants.add(new FrozenParticipant(
                participantTag.getUUID(PARTICIPANT_ID_KEY),
                participantTag.getString(PARTICIPANT_NAME_KEY),
                FrozenParticipantKind.parse(participantTag.getString(PARTICIPANT_KIND_KEY)),
                participantTag.getUUID(PARTICIPANT_OWNER_KEY),
                participantTag.getInt(PARTICIPANT_SLOT_KEY),
                participantTag.contains(PARTICIPANT_LOADOUT_REVISION_KEY, Tag.TAG_LONG)
                    ? participantTag.getLong(PARTICIPANT_LOADOUT_REVISION_KEY)
                    : -1L
            ));
        }
        if (participants.isEmpty()) {
            participants = List.of(FrozenParticipant.host(hostId, -1L));
        }
        if (!validFrozenParticipants(hostId, participants)) {
            throw new IllegalStateException("Frozen dungeon participant roster is invalid");
        }
        return new SoloPreparation(
            tag.getInt(REQUESTED_FLOOR_KEY),
            tag.getInt(TARGET_ROOMS_KEY),
            entryDirection,
            attachmentTarget,
            tag.getLong(PLAN_SEED_KEY),
            participants,
            tag.getLong(REQUESTED_GAME_TIME_KEY)
        );
    }

    private static CompoundTag saveSoloPreparation(SoloPreparation preparation) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(REQUESTED_FLOOR_KEY, preparation.requestedFloor());
        tag.putInt(TARGET_ROOMS_KEY, preparation.targetRooms());
        tag.putString(ENTRY_DIRECTION_KEY, preparation.entryDirection().getName());
        tag.put(ATTACHMENT_TARGET_KEY, NbtUtils.writeBlockPos(preparation.attachmentTarget()));
        tag.putLong(PLAN_SEED_KEY, preparation.planSeed());
        ListTag participants = new ListTag();
        for (FrozenParticipant participant : preparation.frozenParticipants()) {
            CompoundTag participantTag = new CompoundTag();
            participantTag.putUUID(PARTICIPANT_ID_KEY, participant.id());
            participantTag.putString(PARTICIPANT_NAME_KEY, participant.name());
            participantTag.putString(PARTICIPANT_KIND_KEY, participant.kind().name());
            participantTag.putUUID(PARTICIPANT_OWNER_KEY, participant.ownerId());
            participantTag.putInt(PARTICIPANT_SLOT_KEY, participant.slot());
            participantTag.putLong(
                PARTICIPANT_LOADOUT_REVISION_KEY,
                participant.loadoutRevision()
            );
            participants.add(participantTag);
        }
        tag.put(FROZEN_PARTICIPANTS_KEY, participants);
        tag.putLong(REQUESTED_GAME_TIME_KEY, preparation.requestedGameTime());
        return tag;
    }

    private static EntryDispatch loadEntryDispatch(CompoundTag tag) {
        return new EntryDispatch(
            readRequiredPos(tag, ENTRY_CONTROLLER_KEY),
            readRequiredPos(tag, ENTRY_DESTINATION_KEY),
            readRequiredPos(tag, HUB_RETURN_TARGET_KEY),
            DispatchState.parse(tag.getString(DISPATCH_STATE_KEY)),
            tag.getLong(DISPATCH_GAME_TIME_KEY)
        );
    }

    private static CompoundTag saveEntryDispatch(EntryDispatch dispatch) {
        CompoundTag tag = new CompoundTag();
        tag.put(ENTRY_CONTROLLER_KEY, NbtUtils.writeBlockPos(dispatch.entryController()));
        tag.put(ENTRY_DESTINATION_KEY, NbtUtils.writeBlockPos(dispatch.entryDestination()));
        tag.put(HUB_RETURN_TARGET_KEY, NbtUtils.writeBlockPos(dispatch.hubReturnTarget()));
        tag.putString(DISPATCH_STATE_KEY, dispatch.state().name());
        tag.putLong(DISPATCH_GAME_TIME_KEY, dispatch.dispatchGameTime());
        return tag;
    }

    private static BlockPos readRequiredPos(CompoundTag tag, String key) {
        return NbtUtils.readBlockPos(tag, key)
            .map(BlockPos::immutable)
            .orElseThrow(() -> new IllegalStateException(
                "Dungeon instance allocation has malformed position: " + key
            ));
    }

    private static String normalizeDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("Dungeon instance dimension must be present");
        }
        return dimension.trim();
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "UNSPECIFIED" : reason.trim();
    }

    public enum LifecycleState {
        ALLOCATED,
        PREPARING,
        FAILED_PREPARATION,
        READY,
        ACTIVE,
        SUSPENDED,
        COMPLETING,
        CLEANING,
        REUSABLE,
        QUARANTINED;

        static LifecycleState parse(String value) {
            try {
                return LifecycleState.valueOf(value);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                throw new IllegalStateException("Unsupported dungeon instance lifecycle state: " + value);
            }
        }

        public boolean reusable() {
            return this == REUSABLE;
        }

        public boolean allowsProtectedEvacuation() {
            return this == ACTIVE || this == SUSPENDED
                || this == COMPLETING || this == CLEANING;
        }

        boolean canTransitionTo(LifecycleState next) {
            if (this == next) {
                return true;
            }
            return switch (this) {
                case ALLOCATED -> next == PREPARING || next == FAILED_PREPARATION || next == CLEANING;
                case PREPARING -> next == READY || next == ACTIVE
                    || next == FAILED_PREPARATION || next == CLEANING;
                case FAILED_PREPARATION -> next == CLEANING;
                case READY -> next == ACTIVE || next == CLEANING;
                case ACTIVE -> next == SUSPENDED || next == COMPLETING || next == CLEANING;
                case SUSPENDED -> next == ACTIVE || next == COMPLETING || next == CLEANING;
                case COMPLETING -> next == CLEANING;
                case CLEANING -> next == REUSABLE || next == PREPARING;
                case REUSABLE, QUARANTINED -> false;
            };
        }
    }

    public enum MemberDisposition {
        IN_INSTANCE,
        RECONNECT_TO_INSTANCE,
        RETURN_TO_HUB_ON_LOGIN;

        static MemberDisposition parse(String value) {
            try {
                return MemberDisposition.valueOf(value);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                throw new IllegalStateException("Unknown dungeon member disposition: " + value);
            }
        }
    }

    public record InstanceKey(UUID instanceId, long epoch) {
        public InstanceKey {
            if (instanceId == null || epoch <= 0L) {
                throw new IllegalArgumentException("Dungeon instance identity requires UUID and positive epoch");
            }
        }

        public static @Nullable InstanceKey loadOptional(CompoundTag tag) {
            if (tag == null || !tag.hasUUID(INSTANCE_ID_KEY)
                || !tag.contains(EPOCH_KEY, Tag.TAG_LONG)) {
                return null;
            }
            long epoch = tag.getLong(EPOCH_KEY);
            return epoch > 0L ? new InstanceKey(tag.getUUID(INSTANCE_ID_KEY), epoch) : null;
        }

        public void save(CompoundTag tag) {
            tag.putUUID(INSTANCE_ID_KEY, instanceId);
            tag.putLong(EPOCH_KEY, epoch);
        }
    }

    public record Cell(int index, BlockPos origin, CellBounds bounds) {
        public Cell {
            if (index < 0 || origin == null || bounds == null || !bounds.contains(origin)) {
                throw new IllegalArgumentException("Dungeon instance cell must have valid index, origin, and bounds");
            }
            origin = origin.immutable();
        }
    }

    public record GridRelocationResult(
        boolean relocated,
        String reason,
        int retainedCells,
        @Nullable BlockPos gridAnchor
    ) {
        private static GridRelocationResult relocated(int retainedCells, BlockPos gridAnchor) {
            return new GridRelocationResult(
                true, "REUSABLE_GRID_RELOCATED", retainedCells, gridAnchor.immutable()
            );
        }

        private static GridRelocationResult rejected(String reason, int retainedCells) {
            return new GridRelocationResult(false, reason, retainedCells, null);
        }
    }

    public record CellBounds(BlockPos min, BlockPos max) {
        public CellBounds {
            if (min == null || max == null
                || min.getX() > max.getX()
                || min.getY() > max.getY()
                || min.getZ() > max.getZ()) {
                throw new IllegalArgumentException("Dungeon instance cell bounds are inverted or missing");
            }
            min = min.immutable();
            max = max.immutable();
        }

        public boolean contains(BlockPos pos) {
            return pos != null
                && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        public boolean contains(RoomControllerBlockEntity.RoomBounds bounds) {
            return bounds != null && contains(bounds.min()) && contains(bounds.max());
        }
    }

    public record Lifecycle(
        LifecycleState state,
        String transitionReason,
        String quarantineReason,
        long transitionGameTime
    ) {
        public Lifecycle {
            if (state == null) {
                throw new IllegalArgumentException("Dungeon instance lifecycle state is required");
            }
            transitionReason = normalizeReason(transitionReason);
            quarantineReason = quarantineReason == null ? "" : quarantineReason.trim();
            transitionGameTime = Math.max(0L, transitionGameTime);
            if (state == LifecycleState.QUARANTINED && quarantineReason.isBlank()) {
                throw new IllegalArgumentException("Quarantined dungeon instance requires a reason");
            }
        }
    }

    public record Allocation(
        InstanceKey key,
        String dimension,
        Cell cell,
        UUID host,
        Set<UUID> roster,
        @Nullable SoloPreparation soloPreparation,
        @Nullable EntryDispatch entryDispatch,
        Map<UUID, MemberDisposition> memberDispositions,
        Lifecycle lifecycle,
        long createdGameTime
    ) {
        public Allocation {
            if (key == null || cell == null || host == null || lifecycle == null) {
                throw new IllegalArgumentException("Dungeon instance allocation is incomplete");
            }
            dimension = normalizeDimension(dimension);
            roster = Set.copyOf(roster == null ? Set.of() : roster);
            Set<UUID> frozenRoster = roster;
            Map<UUID, MemberDisposition> normalized = new LinkedHashMap<>();
            if (memberDispositions != null) {
                memberDispositions.forEach((playerId, disposition) -> {
                    if (!frozenRoster.contains(playerId) || disposition == null) {
                        throw new IllegalArgumentException("Dungeon member disposition is outside the frozen roster");
                    }
                    normalized.put(playerId, disposition);
                });
            }
            frozenRoster.forEach(playerId -> normalized.putIfAbsent(playerId, MemberDisposition.IN_INSTANCE));
            memberDispositions = Map.copyOf(normalized);
            createdGameTime = Math.max(0L, createdGameTime);
        }

        public MemberDisposition memberDisposition(UUID playerId) {
            return memberDispositions.getOrDefault(playerId, MemberDisposition.IN_INSTANCE);
        }

        Allocation withLifecycle(Lifecycle next) {
            return new Allocation(
                key, dimension, cell, host, roster, soloPreparation, entryDispatch,
                memberDispositions, next, createdGameTime
            );
        }

        Allocation withPreparation(
            @Nullable SoloPreparation replacement,
            Lifecycle next
        ) {
            return new Allocation(
                key, dimension, cell, host, roster, replacement, entryDispatch,
                memberDispositions, next, createdGameTime
            );
        }

        Allocation withEntryDispatch(EntryDispatch replacement) {
            return new Allocation(
                key, dimension, cell, host, roster, soloPreparation, replacement,
                memberDispositions, lifecycle, createdGameTime
            );
        }

        Allocation withLifecycleAndEntryDispatch(Lifecycle next, @Nullable EntryDispatch replacement) {
            return new Allocation(
                key, dimension, cell, host, roster, soloPreparation, replacement,
                memberDispositions, next, createdGameTime
            );
        }

        Allocation withLifecycleAndMemberDisposition(
            Lifecycle next,
            UUID playerId,
            MemberDisposition disposition
        ) {
            Map<UUID, MemberDisposition> replacements = new LinkedHashMap<>(memberDispositions);
            replacements.put(playerId, disposition);
            return new Allocation(
                key, dimension, cell, host, roster, soloPreparation, entryDispatch,
                replacements, next, createdGameTime
            );
        }

        Allocation withLifecycleAndMemberDispositions(
            Lifecycle next,
            Map<UUID, MemberDisposition> replacements
        ) {
            return new Allocation(
                key, dimension, cell, host, roster, soloPreparation, entryDispatch,
                replacements, next, createdGameTime
            );
        }
    }

    public record MemberDispositionResult(
        boolean accepted,
        String reason,
        @Nullable Allocation allocation,
        boolean changed
    ) {
        static MemberDispositionResult accepted(
            String reason,
            Allocation allocation,
            boolean changed
        ) {
            return new MemberDispositionResult(true, reason, allocation, changed);
        }

        static MemberDispositionResult rejected(String reason, @Nullable Allocation allocation) {
            return new MemberDispositionResult(false, reason, allocation, false);
        }
    }

    public record RevisionRefreshResult(
        boolean accepted,
        String reason,
        @Nullable Allocation allocation,
        boolean changed
    ) {
        static RevisionRefreshResult accepted(
            String reason,
            Allocation allocation,
            boolean changed
        ) {
            return new RevisionRefreshResult(true, reason, allocation, changed);
        }

        static RevisionRefreshResult rejected(String reason, @Nullable Allocation allocation) {
            return new RevisionRefreshResult(false, reason, allocation, false);
        }
    }

    public record TerminalDispositionResult(
        boolean accepted,
        String reason,
        @Nullable Allocation allocation,
        boolean changed
    ) {
        static TerminalDispositionResult accepted(
            String reason,
            Allocation allocation,
            boolean changed
        ) {
            return new TerminalDispositionResult(true, reason, allocation, changed);
        }

        static TerminalDispositionResult rejected(
            String reason,
            @Nullable Allocation allocation
        ) {
            return new TerminalDispositionResult(false, reason, allocation, false);
        }
    }

    public record SoloPreparation(
        int requestedFloor,
        int targetRooms,
        Direction entryDirection,
        BlockPos attachmentTarget,
        long planSeed,
        List<FrozenParticipant> frozenParticipants,
        long requestedGameTime
    ) {
        public SoloPreparation {
            if (requestedFloor < 1 || targetRooms < 1 || entryDirection == null
                || !entryDirection.getAxis().isHorizontal() || attachmentTarget == null
                || frozenParticipants == null || frozenParticipants.isEmpty()
                || frozenParticipants.size() > 4) {
                throw new IllegalArgumentException("Solo preparation plan is invalid");
            }
            attachmentTarget = attachmentTarget.immutable();
            frozenParticipants = List.copyOf(frozenParticipants);
            requestedGameTime = Math.max(0L, requestedGameTime);
        }

        public boolean mixedRoster() {
            return frozenParticipants.size() > 1;
        }

        public boolean hostAndSubordinateRoster(UUID hostId) {
            if (hostId == null || frozenParticipants.isEmpty()) {
                return false;
            }
            FrozenParticipant host = frozenParticipants.getFirst();
            if (!host.id().equals(hostId) || !host.ownerId().equals(hostId)
                || host.kind() != FrozenParticipantKind.HUMAN || host.slot() != 1) {
                return false;
            }
            return frozenParticipants.stream().skip(1).allMatch(participant ->
                participant.kind() == FrozenParticipantKind.SUBORDINATE
                    && participant.ownerId().equals(hostId)
                    && !participant.id().equals(hostId)
            );
        }
    }

    public enum FrozenParticipantKind {
        HUMAN,
        SUBORDINATE;

        static FrozenParticipantKind parse(String value) {
            try {
                return FrozenParticipantKind.valueOf(value);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                throw new IllegalStateException("Unsupported frozen participant kind: " + value);
            }
        }
    }

    public record FrozenParticipant(
        UUID id,
        String name,
        FrozenParticipantKind kind,
        UUID ownerId,
        int slot,
        long loadoutRevision
    ) {
        public FrozenParticipant {
            if (id == null || kind == null || ownerId == null || slot < 1 || slot > 4
                || loadoutRevision < -1L) {
                throw new IllegalArgumentException("Frozen dungeon participant is incomplete");
            }
            name = name == null ? "" : name.trim();
            if (name.length() > 64) name = name.substring(0, 64);
        }

        public static FrozenParticipant host(UUID hostId, long loadoutRevision) {
            return new FrozenParticipant(
                hostId,
                "",
                FrozenParticipantKind.HUMAN,
                hostId,
                1,
                loadoutRevision
            );
        }
    }

    public enum DispatchState {
        PENDING_TELEPORT,
        ADMITTED;

        static DispatchState parse(String value) {
            try {
                return DispatchState.valueOf(value);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                throw new IllegalStateException("Unsupported dungeon entry dispatch state: " + value);
            }
        }
    }

    public record EntryDispatch(
        BlockPos entryController,
        BlockPos entryDestination,
        BlockPos hubReturnTarget,
        DispatchState state,
        long dispatchGameTime
    ) {
        public EntryDispatch {
            if (entryController == null || entryDestination == null || hubReturnTarget == null || state == null) {
                throw new IllegalArgumentException("Dungeon entry dispatch checkpoint is incomplete");
            }
            entryController = entryController.immutable();
            entryDestination = entryDestination.immutable();
            hubReturnTarget = hubReturnTarget.immutable();
            dispatchGameTime = Math.max(0L, dispatchGameTime);
        }

        boolean sameRoute(EntryDispatch other) {
            return other != null
                && entryController.equals(other.entryController)
                && entryDestination.equals(other.entryDestination)
                && hubReturnTarget.equals(other.hubReturnTarget);
        }

        EntryDispatch withState(DispatchState next, long gameTime) {
            return new EntryDispatch(
                entryController, entryDestination, hubReturnTarget, next, Math.max(0L, gameTime)
            );
        }
    }

    public record DispatchResult(
        boolean accepted,
        String reason,
        @Nullable Allocation allocation,
        boolean mutated
    ) {
        static DispatchResult accepted(String reason, Allocation allocation, boolean mutated) {
            return new DispatchResult(true, reason, allocation, mutated);
        }

        static DispatchResult rejected(String reason, @Nullable Allocation allocation) {
            return new DispatchResult(false, reason, allocation, false);
        }
    }

    public record ReservationResult(
        boolean accepted,
        String reason,
        @Nullable Allocation allocation,
        boolean created
    ) {
        static ReservationResult accepted(
            Allocation allocation,
            boolean created,
            String reason
        ) {
            return new ReservationResult(true, reason, allocation, created);
        }

        static ReservationResult rejected(String reason, @Nullable Allocation allocation) {
            return new ReservationResult(false, reason, allocation, false);
        }
    }

    public record GridAnchorResolution(
        @Nullable BlockPos gridAnchor,
        String reason,
        int retainedCells
    ) {
        static GridAnchorResolution resolved(BlockPos gridAnchor, int retainedCells) {
            return new GridAnchorResolution(
                gridAnchor == null ? null : gridAnchor.immutable(),
                "PERSISTED_CELL_GRID",
                Math.max(0, retainedCells)
            );
        }

        static GridAnchorResolution failed(String reason, int retainedCells) {
            return new GridAnchorResolution(null, normalizeReason(reason), Math.max(0, retainedCells));
        }

        public boolean resolved() {
            return gridAnchor != null;
        }
    }

    public record CancellationResult(
        boolean canceled,
        String reason,
        @Nullable InstanceKey instanceKey
    ) {
        static CancellationResult accepted(InstanceKey instanceKey) {
            return new CancellationResult(true, "SOLO_PREPARATION_CANCELED", instanceKey);
        }

        static CancellationResult rejected(String reason, @Nullable Allocation allocation) {
            return new CancellationResult(
                false,
                reason,
                allocation == null ? null : allocation.key()
            );
        }
    }
}
