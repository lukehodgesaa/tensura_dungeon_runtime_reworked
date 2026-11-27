package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public final class DungeonSubordinateTransportData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String DATA_NAME = "tensura_dungeon_subordinate_transport";
    private static final String BINDINGS_KEY = "Bindings";
    private static final String ENTRIES_KEY = "Entries";
    private static final String HUB_HOLDS_KEY = "HubHolds";
    private static final double HUB_HOLD_DISCOVERY_RADIUS = 64.0D;
    private static final double HUB_HOLD_CONTAINMENT_RADIUS_SQR = 72.0D * 72.0D;
    private static final int HEARTBEAT_INTERVAL_TICKS = 10;
    private static final int RECOVERY_RETRY_INTERVAL_TICKS = 200;
    private static final int RECOVERY_GRACE_TICKS = 100;
    private static final int PERSIST_INTERVAL_TICKS = 200;
    private static final int RECOVERY_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.FULL);
    private static final TicketType<Long> RECOVERY_CHUNK_TICKET = TicketType.create(
        "tensura_dungeon_subordinate_recovery",
        Long::compare,
        40
    );

    private final Map<DungeonInstanceAllocationData.InstanceKey, Binding> bindings =
        new LinkedHashMap<>();
    private final Map<DungeonInstanceAllocationData.InstanceKey, HubHoldBinding> hubHolds =
        new LinkedHashMap<>();
    private final Map<UUID, EntityBindingRef> entityIndex = new HashMap<>();
    private final Set<UUID> conflictedEntityIds = new HashSet<>();
    private final Map<UUID, RecoveryObservation> observations = new HashMap<>();
    private final Map<UUID, RecoveryTicket> recoveryTickets = new HashMap<>();
    private final Map<UUID, LivingEntity> enteredEntitiesAwaitingHost = new HashMap<>();
    private final Map<UUID, Long> lastDirtyGameTime = new HashMap<>();
    private long nextRecoveryTicketKey = 1L;

    public static DungeonSubordinateTransportData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(DungeonSubordinateTransportData::new, DungeonSubordinateTransportData::load),
            DATA_NAME
        );
    }

    public static DungeonSubordinateTransportData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        DungeonSubordinateTransportData data = new DungeonSubordinateTransportData();
        ListTag records = tag.getList(BINDINGS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < records.size(); index++) {
            CompoundTag record = records.getCompound(index);
            DungeonInstanceAllocationData.InstanceKey key =
                DungeonInstanceAllocationData.InstanceKey.loadOptional(record);
            if (key == null || !record.hasUUID("Host")) continue;
            List<Entry> entries = new ArrayList<>();
            ListTag entryTags = record.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
            for (int entryIndex = 0; entryIndex < entryTags.size(); entryIndex++) {
                CompoundTag entry = entryTags.getCompound(entryIndex);
                if (!entry.hasUUID("Id") || !entry.hasUUID("Owner")) continue;
                entries.add(new Entry(
                    entry.getUUID("Id"),
                    entry.getUUID("Owner"),
                    entry.getInt("Slot"),
                    TransportState.parse(entry.getString("State")),
                    NbtUtils.readBlockPos(entry, "LastKnown").orElse(BlockPos.ZERO),
                    entry.getBoolean("RestoreFollow")
                ));
            }
            if (!entries.isEmpty()) {
                Binding binding = new Binding(
                    key,
                    record.getString("Dimension"),
                    record.getUUID("Host"),
                    entries
                );
                data.bindings.put(key, binding);
            }
        }
        ListTag holdRecords = tag.getList(HUB_HOLDS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < holdRecords.size(); index++) {
            CompoundTag record = holdRecords.getCompound(index);
            DungeonInstanceAllocationData.InstanceKey key =
                DungeonInstanceAllocationData.InstanceKey.loadOptional(record);
            if (key == null || !record.hasUUID("Host")) continue;
            List<HubHoldEntry> entries = new ArrayList<>();
            ListTag entryTags = record.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
            for (int entryIndex = 0; entryIndex < entryTags.size(); entryIndex++) {
                CompoundTag entry = entryTags.getCompound(entryIndex);
                if (!entry.hasUUID("Id") || !entry.hasUUID("Owner")) continue;
                entries.add(new HubHoldEntry(
                    entry.getUUID("Id"),
                    entry.getUUID("Owner"),
                    NbtUtils.readBlockPos(entry, "LastKnown").orElse(BlockPos.ZERO),
                    entry.getBoolean("RestoreFollow")
                ));
            }
            Optional<BlockPos> hubTarget = NbtUtils.readBlockPos(record, "HubTarget");
            if (!entries.isEmpty() && hubTarget.isPresent()) {
                data.hubHolds.put(key, new HubHoldBinding(
                    key,
                    record.getString("Dimension"),
                    record.getUUID("Host"),
                    hubTarget.get(),
                    entries,
                    record.getBoolean("ReleasePending")
                ));
            }
        }
        data.rebuildEntityIndex();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag records = new ListTag();
        bindings.values().stream()
            .sorted(Comparator.comparing(binding -> binding.key().instanceId().toString()))
            .forEach(binding -> {
                CompoundTag record = new CompoundTag();
                binding.key().save(record);
                record.putString("Dimension", binding.dimension());
                record.putUUID("Host", binding.hostId());
                ListTag entries = new ListTag();
                for (Entry value : binding.entries()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("Id", value.entityId());
                    entry.putUUID("Owner", value.ownerId());
                    entry.putInt("Slot", value.slot());
                    entry.putString("State", value.state().name());
                    entry.put("LastKnown", NbtUtils.writeBlockPos(value.lastKnown()));
                    if (value.restoreFollow()) {
                        entry.putBoolean("RestoreFollow", true);
                    }
                    entries.add(entry);
                }
                record.put(ENTRIES_KEY, entries);
                records.add(record);
            });
        tag.put(BINDINGS_KEY, records);
        ListTag holdRecords = new ListTag();
        hubHolds.values().stream()
            .sorted(Comparator.comparing(binding -> binding.key().instanceId().toString()))
            .forEach(binding -> {
                CompoundTag record = new CompoundTag();
                binding.key().save(record);
                record.putString("Dimension", binding.dimension());
                record.putUUID("Host", binding.hostId());
                record.put("HubTarget", NbtUtils.writeBlockPos(binding.hubTarget()));
                if (binding.releasePending()) {
                    record.putBoolean("ReleasePending", true);
                }
                ListTag entries = new ListTag();
                for (HubHoldEntry value : binding.entries()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("Id", value.entityId());
                    entry.putUUID("Owner", value.ownerId());
                    entry.put("LastKnown", NbtUtils.writeBlockPos(value.lastKnown()));
                    if (value.restoreFollow()) {
                        entry.putBoolean("RestoreFollow", true);
                    }
                    entries.add(entry);
                }
                record.put(ENTRIES_KEY, entries);
                holdRecords.add(record);
            });
        tag.put(HUB_HOLDS_KEY, holdRecords);
        return tag;
    }

    public Optional<Binding> binding(DungeonInstanceAllocationData.InstanceKey key) {
        return Optional.ofNullable(bindings.get(key));
    }

    Optional<HubHoldBinding> hubHold(DungeonInstanceAllocationData.InstanceKey key) {
        return Optional.ofNullable(hubHolds.get(key));
    }

    void recordHubHold(HubHoldBinding binding) {
        hubHolds.put(binding.key(), binding);
        setDirty();
    }

    public Optional<BindingRef> bindingForEntity(UUID entityId) {
        EntityBindingRef ref = entityIndex.get(entityId);
        if (ref == null || conflictedEntityIds.contains(entityId)) {
            return Optional.empty();
        }
        return Optional.of(new BindingRef(ref.key(), ref.entry().slot(), ref.entry().state()));
    }

    void recordBinding(Binding binding) {
        Binding existing = bindings.get(binding.key());
        if (existing != null && !existing.sameIdentities(binding)) {
            throw new IllegalArgumentException("Subordinate transport binding identity changed");
        }
        for (Entry entry : binding.entries()) {
            EntityBindingRef existingRef = entityIndex.get(entry.entityId());
            if (existingRef != null && !existingRef.key().equals(binding.key())) {
                throw new IllegalArgumentException("Subordinate entity is already transport-bound");
            }
        }
        bindings.put(binding.key(), binding);
        rebuildEntityIndex();
        setDirty();
    }

    public TransportResult holdNonParticipantFollowersAtHub(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        if (level == null || allocation == null || allocation.soloPreparation() == null
            || allocation.entryDispatch() == null
            || !allocation.dimension().equals(level.dimension().location().toString())) {
            return TransportResult.rejected("HUB_HOLD_ALLOCATION_INVALID");
        }
        HubHoldBinding existing = hubHolds.get(allocation.key());
        if (existing != null) {
            return existing.hostId().equals(allocation.host())
                ? TransportResult.accepted("HUB_HOLD_ALREADY_RECORDED")
                : TransportResult.rejected("HUB_HOLD_OWNER_MISMATCH");
        }

        Set<UUID> frozenIds = allocation.soloPreparation().frozenParticipants().stream()
            .map(DungeonInstanceAllocationData.FrozenParticipant::id)
            .collect(java.util.stream.Collectors.toSet());
        BlockPos hubTarget = allocation.entryDispatch().hubReturnTarget().above();
        AABB hubBounds = new AABB(hubTarget).inflate(
            HUB_HOLD_DISCOVERY_RADIUS,
            HUB_HOLD_DISCOVERY_RADIUS / 2.0D,
            HUB_HOLD_DISCOVERY_RADIUS
        );
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, hubBounds)
            .stream()
            .filter(entity -> entity.isAlive() && !frozenIds.contains(entity.getUUID()))
            .filter(entity -> !entityIndex.containsKey(entity.getUUID()))
            .filter(entity -> TensuraCompat.subordinateOwnerId(entity)
                .filter(allocation.host()::equals).isPresent())
            .sorted(Comparator.comparing(entity -> entity.getUUID().toString()))
            .toList();
        if (candidates.isEmpty()) {
            return TransportResult.accepted("NO_NONPARTICIPANT_FOLLOWERS_AT_HUB");
        }

        List<LivingEntity> changed = new ArrayList<>();
        List<HubHoldEntry> entries = new ArrayList<>();
        for (LivingEntity entity : candidates) {
            Optional<Boolean> held = TensuraCompat.wanderSubordinateAtHub(entity);
            if (held.isEmpty()) {
                for (LivingEntity rollback : changed) {
                    TensuraCompat.restoreSubordinateFollow(rollback);
                }
                return TransportResult.rejected("NONPARTICIPANT_FOLLOW_CONTAINMENT_UNAVAILABLE");
            }
            if (held.get()) {
                changed.add(entity);
            }
            entries.add(new HubHoldEntry(
                entity.getUUID(), allocation.host(), entity.blockPosition(), held.get()
            ));
        }
        hubHolds.put(allocation.key(), new HubHoldBinding(
            allocation.key(), allocation.dimension(), allocation.host(), hubTarget,
            entries, false
        ));
        setDirty();
        level.getServer().overworld().getDataStorage().save();
        return TransportResult.accepted("NONPARTICIPANT_FOLLOWERS_HELD_AT_HUB");
    }

    public void releaseHubHolds(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        HubHoldBinding binding = hubHolds.get(key);
        if (binding == null) return;
        List<HubHoldEntry> remaining = new ArrayList<>();
        for (HubHoldEntry entry : binding.entries()) {
            if (!entry.restoreFollow()) {
                continue;
            }
            LivingEntity entity = resolve(level, entry.entityId(), entry.ownerId());
            if (entity == null || !TensuraCompat.restoreSubordinateFollow(entity)) {
                remaining.add(entry);
            }
        }
        if (remaining.isEmpty()) {
            hubHolds.remove(key);
        } else {
            hubHolds.put(key, binding.withEntries(remaining, true));
        }
        setDirty();
    }

    public TransportResult enter(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation,
        BlockPos destination
    ) {
        List<DungeonInstanceAllocationData.FrozenParticipant> subordinateProfiles =
            allocation.soloPreparation().frozenParticipants().stream()
                .filter(participant -> participant.kind()
                    == DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE)
                .toList();
        if (subordinateProfiles.isEmpty()) return TransportResult.accepted("NO_SUBORDINATES");
        List<LivingEntity> entities = resolveAll(level, subordinateProfiles);
        if (entities.size() != subordinateProfiles.size()) {
            return TransportResult.rejected("SUBORDINATE_ENTRY_IDENTITY_UNAVAILABLE");
        }
        Binding expected = new Binding(
            allocation.key(),
            allocation.dimension(),
            allocation.host(),
            subordinateProfiles.stream().map(profile -> new Entry(
                profile.id(), profile.ownerId(), profile.slot(), TransportState.ENTRY_PENDING,
                BlockPos.containing(entities.get(subordinateProfiles.indexOf(profile)).position())
            )).toList()
        );
        Binding existing = bindings.get(allocation.key());
        if (existing != null && !existing.sameIdentities(expected)) {
            return TransportResult.rejected("SUBORDINATE_BINDING_MISMATCH");
        }
        List<BlockPos> origins = entities.stream().map(entity -> entity.blockPosition().immutable()).toList();
        List<TransportState> priorStates = existing == null
            ? subordinateProfiles.stream().map(profile -> TransportState.AT_HUB).toList()
            : existing.entries().stream().map(Entry::state).toList();
        recordBinding(expected);
        try {
            level.getChunkAt(destination);
            for (int index = 0; index < entities.size(); index++) {
                LivingEntity entity = entities.get(index);
                UUID entityId = entity.getUUID();
                UUID ownerId = subordinateProfiles.get(index).ownerId();
                if (TensuraCompat.pauseSubordinateFollow(entity).isEmpty()) {
                    throw new IllegalStateException("Subordinate follow containment unavailable");
                }
                double offset = (index - (entities.size() - 1) / 2.0D) * 0.28D;
                double targetX = destination.getX() + 0.5D + offset;
                double targetY = destination.getY();
                double targetZ = destination.getZ() + 0.5D;
                ensureRecoveryTicket(level, entityId, destination);
                entity.teleportTo(
                    targetX,
                    targetY,
                    targetZ
                );
                entity.fallDistance = 0.0F;
                if (!validEnteredEntity(
                    level, entity, entityId, ownerId, targetX, targetY, targetZ
                )) {
                    throw new IllegalStateException("Subordinate disappeared during entry");
                }
                enteredEntitiesAwaitingHost.put(entityId, entity);
            }
        } catch (RuntimeException failure) {
            rollbackTeleport(level, allocation.key(), entities, origins, priorStates);
            for (LivingEntity entity : entities) {
                enteredEntitiesAwaitingHost.remove(entity.getUUID());
                clearRecoveryTicket(level, entity.getUUID());
                TensuraCompat.restoreSubordinateFollow(entity);
            }
            LOGGER.error(
                "[TD-SUBORDINATE-ENTRY] result=ROLLED_BACK instance={} epoch={} destination={} failure={}",
                allocation.key().instanceId(), allocation.key().epoch(), destination,
                failure.toString(), failure
            );
            return TransportResult.rejected(
                "SUBORDINATE_ENTRY_ROLLED_BACK_" + failure.getClass().getSimpleName()
            );
        }
        for (LivingEntity entity : entities) {
            updateEntry(
                allocation.key(), entity.getUUID(), TransportState.IN_INSTANCE,
                entity.blockPosition(), level.getGameTime(), true
            );
        }
        level.getServer().overworld().getDataStorage().save();
        return TransportResult.accepted("SUBORDINATES_ENTERED");
    }

    public void restoreEnteredFollowers(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        if (level == null || allocation == null) return;
        Binding binding = bindings.get(allocation.key());
        if (binding == null) return;
        for (Entry entry : binding.entries()) {
            if (!entry.state().isInstanceBound()) continue;
            LivingEntity entity = enteredEntitiesAwaitingHost.remove(entry.entityId());
            if (!validBoundEntity(level, entity, entry.entityId(), entry.ownerId())) {
                entity = resolve(level, entry.entityId(), entry.ownerId());
            }
            if (entity != null) {
                TensuraCompat.restoreSubordinateFollow(entity);
                clearRecoveryTicket(level, entry.entityId());
            }
        }
    }

    public TransportResult verifyReturnToHub(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        HubReturnPlan plan = planHubReturn(level, allocation);
        return plan.accepted()
            ? TransportResult.accepted(plan.reason())
            : TransportResult.rejected(plan.reason());
    }

    public TransportResult returnToHub(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        HubReturnPlan plan = planHubReturn(level, allocation);
        if (!plan.accepted()) {
            return TransportResult.rejected(plan.reason());
        }
        List<Entry> activeEntries = plan.entries();
        if (activeEntries.isEmpty()) {
            level.getServer().overworld().getDataStorage().save();
            return TransportResult.accepted(plan.reason());
        }
        BlockPos target = plan.target();
        List<LivingEntity> entities = plan.entities();
        List<BlockPos> origins = entities.stream().map(entity -> entity.blockPosition().immutable()).toList();
        try {
            for (int index = 0; index < entities.size(); index++) {
                Entry entry = activeEntries.get(index);
                LivingEntity entity = entities.get(index);
                double offset = (entry.slot() - 2.5D) * 0.28D;
                entity.teleportTo(
                    target.getX() + 0.5D + offset,
                    target.getY(),
                    target.getZ() + 0.5D
                );
                entity.fallDistance = 0.0F;
                if (resolve(level, entry.entityId(), entry.ownerId()) == null) {
                    throw new IllegalStateException("Subordinate disappeared during hub return");
                }
            }
        } catch (RuntimeException failure) {
            rollbackTeleport(
                level,
                allocation.key(),
                entities,
                origins,
                activeEntries.stream().map(Entry::state).toList()
            );
            return TransportResult.rejected("SUBORDINATE_RETURN_ROLLED_BACK");
        }
        for (Entry entry : activeEntries) {
            LivingEntity entity = resolve(level, entry.entityId(), entry.ownerId());
            if (entity != null) {
                updateEntry(
                    allocation.key(), entry.entityId(), TransportState.AT_HUB,
                    entity.blockPosition(), level.getGameTime(), true
                );
                clearRecoveryTicket(level, entry.entityId());
            }
        }
        level.getServer().overworld().getDataStorage().save();
        return TransportResult.accepted("SUBORDINATES_RETURNED_TO_HUB");
    }

    TerminalEscapePlan prepareTerminalEscape(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation,
        ServerPlayer inventoryOwner
    ) {
        if (inventoryOwner == null || allocation == null
            || !allocation.host().equals(inventoryOwner.getUUID())) {
            return TerminalEscapePlan.rejected("TERMINAL_ESCAPE_OWNER_INVALID");
        }
        HubReturnPlan hubPlan = planHubReturn(level, allocation);
        if (!hubPlan.accepted()) {
            return TerminalEscapePlan.rejected(hubPlan.reason());
        }
        if (hubPlan.entries().isEmpty()) {
            BlockPos target = allocation.entryDispatch() == null
                ? hubPlan.target()
                : allocation.entryDispatch().hubReturnTarget().above();
            return TerminalEscapePlan.accepted(
                allocation.key(), target, List.of()
            );
        }
        DungeonPreparationLoadoutData loadouts = DungeonPreparationLoadoutData.get(level);
        Map<UUID, DungeonInstanceAllocationData.FrozenParticipant> profiles = new HashMap<>();
        allocation.soloPreparation().frozenParticipants().stream()
            .filter(participant -> participant.kind()
                == DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE)
            .forEach(participant -> profiles.put(participant.id(), participant));
        List<TerminalEscapeEntry> resolutions = new ArrayList<>();
        for (int index = 0; index < hubPlan.entries().size(); index++) {
            Entry entry = hubPlan.entries().get(index);
            DungeonInstanceAllocationData.FrozenParticipant profile = profiles.get(entry.entityId());
            if (profile == null || profile.loadoutRevision() < 0L) {
                return TerminalEscapePlan.rejected("TERMINAL_ESCAPE_PROFILE_MISSING");
            }
            LivingEntity subordinate = hubPlan.entities().get(index);
            Container subordinateInventory = TensuraCompat.subordinateInventory(subordinate)
                .orElse(null);
            if (subordinateInventory == null) {
                return TerminalEscapePlan.rejected("TERMINAL_ESCAPE_INVENTORY_UNAVAILABLE");
            }
            DungeonPreparationLoadoutData.WhistleReservation whistle = loadouts
                .validateSubordinateWhistleReservation(
                    profile.id(),
                    inventoryOwner.getUUID(),
                    subordinateInventory,
                    inventoryOwner.getInventory(),
                    profile.loadoutRevision(),
                    allocation.soloPreparation().requestedFloor()
                )
                .orElse(null);
            if (whistle != null) {
                resolutions.add(new TerminalEscapeEntry(
                    entry, hubPlan.entities().get(index), TerminalEscapeOutcome.WHISTLE,
                    null, whistle
                ));
                continue;
            }
            if (survivesUnprotectedCollapse(allocation.key(), entry.entityId())) {
                resolutions.add(new TerminalEscapeEntry(
                    entry, hubPlan.entities().get(index),
                    TerminalEscapeOutcome.UNPROTECTED_SURVIVAL, null, null
                ));
                continue;
            }
            DungeonPreparationLoadoutData.BraceletReservation bracelet = loadouts
                .validateSubordinateBraceletReservation(
                    profile.id(),
                    inventoryOwner.getUUID(),
                    subordinateInventory,
                    inventoryOwner.getInventory(),
                    profile.loadoutRevision(),
                    allocation.soloPreparation().requestedFloor()
                )
                .orElse(null);
            resolutions.add(new TerminalEscapeEntry(
                entry,
                hubPlan.entities().get(index),
                bracelet == null
                    ? TerminalEscapeOutcome.LOST
                    : TerminalEscapeOutcome.BRACELET,
                bracelet,
                null
            ));
        }
        return TerminalEscapePlan.accepted(allocation.key(), hubPlan.target(), resolutions);
    }

    TerminalEscapeStage stageTerminalEscape(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation,
        TerminalEscapePlan plan
    ) {
        if (level == null || allocation == null || plan == null || !plan.accepted()
            || !allocation.key().equals(plan.key()) || allocation.entryDispatch() == null) {
            return TerminalEscapeStage.rejected("TERMINAL_ESCAPE_PLAN_INVALID");
        }
        HubReturnPlan current = planHubReturn(level, allocation);
        if (!current.accepted()
            || !current.entries().stream().map(Entry::entityId).toList().equals(
                plan.entries().stream().map(value -> value.entry().entityId()).toList()
            )) {
            return TerminalEscapeStage.rejected("TERMINAL_ESCAPE_PLAN_STALE");
        }
        List<TerminalEscapeEntry> moved = new ArrayList<>();
        Map<UUID, BlockPos> origins = new HashMap<>();
        Map<UUID, Boolean> restoreFollow = new HashMap<>();
        try {
            if (!plan.entries().isEmpty()) {
                level.getChunkAt(plan.target());
            }
            for (TerminalEscapeEntry resolution : plan.entries()) {
                if (resolution.outcome() == TerminalEscapeOutcome.LOST) {
                    continue;
                }
                LivingEntity entity = resolution.entity();
                origins.put(entity.getUUID(), entity.blockPosition().immutable());
                if (resolution.outcome() == TerminalEscapeOutcome.BRACELET) {
                    Optional<Boolean> paused = TensuraCompat.pauseSubordinateFollow(entity);
                    if (paused.isEmpty()) {
                        throw new IllegalStateException("Bracelet follow containment unavailable");
                    }
                    restoreFollow.put(entity.getUUID(), paused.get());
                }
                double offset = (resolution.entry().slot() - 2.5D) * 0.28D;
                entity.teleportTo(
                    plan.target().getX() + 0.5D + offset,
                    plan.target().getY(),
                    plan.target().getZ() + 0.5D
                );
                entity.fallDistance = 0.0F;
                if (resolve(
                    level, resolution.entry().entityId(), resolution.entry().ownerId()
                ) != entity) {
                    throw new IllegalStateException("Subordinate disappeared during terminal return");
                }
                moved.add(resolution);
            }
        } catch (RuntimeException failure) {
            rollbackTerminalMoves(moved, origins, restoreFollow);
            return TerminalEscapeStage.rejected("TERMINAL_ESCAPE_RETURN_ROLLED_BACK");
        }
        return TerminalEscapeStage.accepted(plan, moved, origins, restoreFollow);
    }

    void rollbackTerminalEscape(TerminalEscapeStage stage) {
        if (stage != null && stage.accepted()) {
            rollbackTerminalMoves(stage.moved(), stage.origins(), stage.restoreFollow());
        }
    }

    TransportResult commitTerminalEscape(ServerLevel level, TerminalEscapeStage stage) {
        if (level == null || stage == null || !stage.accepted()) {
            return TransportResult.rejected("TERMINAL_ESCAPE_STAGE_INVALID");
        }
        long gameTime = level.getGameTime();
        for (TerminalEscapeEntry resolution : stage.plan().entries()) {
            Entry entry = resolution.entry();
            LivingEntity entity = resolution.entity();
            if (resolution.outcome() == TerminalEscapeOutcome.LOST) {
                entity.kill();
                updateEntry(
                    stage.plan().key(), entry.entityId(), TransportState.LOST,
                    entry.lastKnown(), gameTime, true
                );
            } else if (resolution.outcome() == TerminalEscapeOutcome.BRACELET) {
                updateEvacuatedEntry(
                    stage.plan().key(), entry.entityId(), entity.blockPosition(),
                    stage.restoreFollow().getOrDefault(entry.entityId(), false), gameTime
                );
            } else {
                updateEntry(
                    stage.plan().key(), entry.entityId(), TransportState.AT_HUB,
                    entity.blockPosition(), gameTime, true
                );
            }
            clearRecoveryTicket(level, entry.entityId());
        }
        level.getServer().overworld().getDataStorage().save();
        return TransportResult.accepted("TERMINAL_SUBORDINATE_ESCAPE_RESOLVED");
    }

    private static void rollbackTerminalMoves(
        List<TerminalEscapeEntry> moved,
        Map<UUID, BlockPos> origins,
        Map<UUID, Boolean> restoreFollow
    ) {
        for (TerminalEscapeEntry resolution : moved) {
            LivingEntity entity = resolution.entity();
            BlockPos origin = origins.get(entity.getUUID());
            if (origin != null) {
                entity.teleportTo(
                    origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D
                );
                entity.fallDistance = 0.0F;
            }
            if (restoreFollow.getOrDefault(entity.getUUID(), false)) {
                TensuraCompat.restoreSubordinateFollow(entity);
            }
        }
    }

    static boolean survivesUnprotectedCollapse(
        DungeonInstanceAllocationData.InstanceKey key,
        UUID entityId
    ) {
        if (key == null || entityId == null) {
            return false;
        }
        long mixed = key.instanceId().getMostSignificantBits()
            ^ Long.rotateLeft(key.instanceId().getLeastSignificantBits(), 17)
            ^ Long.rotateLeft(entityId.getMostSignificantBits(), 31)
            ^ Long.rotateLeft(entityId.getLeastSignificantBits(), 47)
            ^ key.epoch() * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return Math.floorMod(mixed, 100L) < 70L;
    }

    private HubReturnPlan planHubReturn(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        if (level == null || allocation == null || allocation.soloPreparation() == null
            || !allocation.dimension().equals(level.dimension().location().toString())) {
            return HubReturnPlan.rejected("SUBORDINATE_RETURN_ALLOCATION_INVALID");
        }
        List<DungeonInstanceAllocationData.FrozenParticipant> profiles = allocation
            .soloPreparation().frozenParticipants().stream()
            .filter(participant -> participant.kind()
                == DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE)
            .toList();
        Binding binding = bindings.get(allocation.key());
        if (profiles.isEmpty()) {
            return binding == null
                ? HubReturnPlan.accepted("NO_SUBORDINATE_BINDING", null, List.of(), List.of())
                : HubReturnPlan.rejected("UNEXPECTED_SUBORDINATE_BINDING");
        }
        if (!bindingMatchesFrozenRoster(allocation, binding, profiles)) {
            return HubReturnPlan.rejected(
                binding == null ? "SUBORDINATE_BINDING_MISSING" : "SUBORDINATE_BINDING_MISMATCH"
            );
        }
        if (allocation.entryDispatch() == null) {
            return HubReturnPlan.rejected("SUBORDINATE_HUB_CHECKPOINT_MISSING");
        }
        reconcileUnresolvedHubEntries(level, allocation);
        binding = bindings.get(allocation.key());
        if (binding == null) {
            return HubReturnPlan.rejected("SUBORDINATE_BINDING_MISSING");
        }
        BlockPos target = allocation.entryDispatch().hubReturnTarget().above();
        level.getChunkAt(target);
        List<Entry> activeEntries = binding.entries().stream()
            .filter(DungeonSubordinateTransportData::requiresHubTeleport)
            .toList();
        if (activeEntries.isEmpty()) {
            return HubReturnPlan.accepted(
                "NO_LIVE_SUBORDINATES_TO_RETURN", target, List.of(), List.of()
            );
        }
        List<LivingEntity> entities = new ArrayList<>();
        for (Entry entry : activeEntries) {
            LivingEntity entity = resolveWithRecovery(level, entry, level.getGameTime());
            if (entity == null) {
                level.getServer().overworld().getDataStorage().save();
                return HubReturnPlan.rejected(
                    entry.state() == TransportState.MISSING_OR_REMOVED
                        ? "SUBORDINATE_REMOVED_OR_MISSING"
                        : "SUBORDINATE_RETURN_IDENTITY_UNAVAILABLE"
                );
            }
            entities.add(entity);
        }
        return HubReturnPlan.accepted("SUBORDINATE_RETURN_VERIFIED", target, activeEntries, entities);
    }

    private void reconcileUnresolvedHubEntries(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        Binding binding = bindings.get(allocation.key());
        if (binding == null) return;
        DungeonRunManager runs = DungeonRunManager.get(level);
        if (!runs.isHubEnabled(allocation.key())) return;
        for (Entry entry : binding.entries()) {
            if (entry.state() != TransportState.UNRESOLVED) continue;
            LivingEntity entity = resolve(level, entry.entityId(), entry.ownerId());
            if (entity == null
                || !runs.isPlayerInsideHubSafeZone(allocation.key(), entity.blockPosition())) {
                continue;
            }
            updateEntry(
                allocation.key(), entry.entityId(), TransportState.AT_HUB,
                entity.blockPosition(), level.getGameTime(), true
            );
            clearRecoveryTicket(level, entry.entityId());
            observations.remove(entry.entityId());
            LOGGER.info(
                "Reconciled unresolved subordinate at persisted hub safe zone: entity={} instance={} epoch={} position={}",
                entry.entityId(), allocation.key().instanceId(), allocation.key().epoch(),
                entity.blockPosition()
            );
        }
    }

    static boolean bindingMatchesFrozenRoster(
        DungeonInstanceAllocationData.Allocation allocation,
        Binding binding,
        List<DungeonInstanceAllocationData.FrozenParticipant> profiles
    ) {
        if (allocation == null || binding == null || profiles == null
            || !binding.key().equals(allocation.key())
            || !binding.dimension().equals(allocation.dimension())
            || !binding.hostId().equals(allocation.host())
            || binding.entries().size() != profiles.size()) {
            return false;
        }
        for (int index = 0; index < profiles.size(); index++) {
            DungeonInstanceAllocationData.FrozenParticipant profile = profiles.get(index);
            Entry entry = binding.entries().get(index);
            if (!entry.entityId().equals(profile.id())
                || !entry.ownerId().equals(profile.ownerId())
                || entry.slot() != profile.slot()) {
                return false;
            }
        }
        return true;
    }

    static boolean requiresHubTeleport(Entry entry) {
        return entry != null
            && entry.state() != TransportState.LOST
            && !entry.state().isEvacuated()
            && stableState(entry.state()) != TransportState.AT_HUB;
    }

    public TransportResult evacuateProtectedSubordinate(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation,
        LivingEntity entity,
        BooleanSupplier consumeBracelet
    ) {
        if (level == null || allocation == null || entity == null || consumeBracelet == null
            || !allocation.lifecycle().state().allowsProtectedEvacuation()
            || allocation.entryDispatch() == null
            || allocation.entryDispatch().state()
                != DungeonInstanceAllocationData.DispatchState.ADMITTED
            || !allocation.dimension().equals(level.dimension().location().toString())) {
            return TransportResult.rejected("PROTECTED_EVACUATION_ALLOCATION_INVALID");
        }
        Binding binding = bindings.get(allocation.key());
        Entry entry = binding == null ? null : binding.entry(entity.getUUID());
        if (binding == null || !binding.hostId().equals(allocation.host()) || entry == null
            || !entry.state().isInstanceBound()
            || resolve(level, entry.entityId(), entry.ownerId()) != entity) {
            return TransportResult.rejected("PROTECTED_EVACUATION_IDENTITY_INVALID");
        }

        BlockPos origin = entity.blockPosition().immutable();
        BlockPos target = allocation.entryDispatch().hubReturnTarget().above();
        level.getChunkAt(target);
        Optional<Boolean> followPaused = TensuraCompat.pauseSubordinateFollow(entity);
        if (followPaused.isEmpty()) {
            return TransportResult.rejected("PROTECTED_EVACUATION_FOLLOW_CONTAINMENT_UNAVAILABLE");
        }
        double offset = (entry.slot() - 2.5D) * 0.28D;
        try {
            entity.teleportTo(
                target.getX() + 0.5D + offset,
                target.getY(),
                target.getZ() + 0.5D
            );
            entity.fallDistance = 0.0F;
            if (resolve(level, entry.entityId(), entry.ownerId()) != entity
                || !consumeBracelet.getAsBoolean()) {
                entity.teleportTo(
                    origin.getX() + 0.5D,
                    origin.getY(),
                    origin.getZ() + 0.5D
                );
                entity.fallDistance = 0.0F;
                if (followPaused.get()) {
                    TensuraCompat.restoreSubordinateFollow(entity);
                }
                return TransportResult.rejected("PROTECTED_EVACUATION_ROLLED_BACK");
            }
        } catch (RuntimeException failure) {
            entity.teleportTo(
                origin.getX() + 0.5D,
                origin.getY(),
                origin.getZ() + 0.5D
            );
            entity.fallDistance = 0.0F;
            if (followPaused.get()) {
                TensuraCompat.restoreSubordinateFollow(entity);
            }
            return TransportResult.rejected("PROTECTED_EVACUATION_ROLLED_BACK");
        }
        updateEvacuatedEntry(
            allocation.key(), entry.entityId(), entity.blockPosition(),
            followPaused.get(), level.getGameTime()
        );
        clearRecoveryTicket(level, entry.entityId());
        level.getServer().overworld().getDataStorage().save();
        return TransportResult.accepted("SUBORDINATE_BRACELET_EVACUATED");
    }

    public boolean canResumeAtCheckpoint(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        Binding binding = bindings.get(allocation.key());
        if (binding == null) return true;
        for (Entry entry : binding.entries()) {
            if (entry.state() == TransportState.LOST) continue;
            if (entry.state().isEvacuated()) {
                if (resolveWithRecovery(level, entry, level.getGameTime()) == null) return false;
                continue;
            }
            LivingEntity entity = resolveWithRecovery(level, entry, level.getGameTime());
            if (entity == null) {
                if (stableState(entry.state()) != TransportState.AT_HUB) return false;
                continue;
            }
            if (stableState(entry.state()) == TransportState.AT_HUB) {
                updateEntry(
                    allocation.key(), entry.entityId(), TransportState.AT_HUB,
                    entity.blockPosition(), level.getGameTime(), false
                );
            }
        }
        return true;
    }

    public TransportResult resumeAtCheckpoint(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation,
        BlockPos destination
    ) {
        Binding binding = bindings.get(allocation.key());
        if (binding == null) return TransportResult.accepted("NO_SUBORDINATE_BINDING");
        if (!canResumeAtCheckpoint(level, allocation)) {
            return TransportResult.rejected("SUBORDINATE_RESUME_IDENTITY_UNAVAILABLE");
        }
        List<Entry> activeEntries = binding.entries().stream()
            .filter(entry -> {
                if (entry.state() == TransportState.LOST || entry.state().isEvacuated()) {
                    return false;
                }
                TransportState stable = stableState(entry.state());
                return stable == TransportState.AT_HUB || stable == TransportState.IN_INSTANCE;
            })
            .toList();
        List<LivingEntity> entities = new ArrayList<>();
        for (Entry entry : activeEntries) {
            LivingEntity entity = resolve(level, entry.entityId(), entry.ownerId());
            if (entity == null) return TransportResult.rejected("SUBORDINATE_RESUME_IDENTITY_UNAVAILABLE");
            entities.add(entity);
        }
        List<BlockPos> origins = entities.stream().map(entity -> entity.blockPosition().immutable()).toList();
        try {
            for (int index = 0; index < entities.size(); index++) {
                LivingEntity entity = entities.get(index);
                double offset = (activeEntries.get(index).slot() - 2.5D) * 0.28D;
                entity.teleportTo(
                    destination.getX() + 0.5D + offset,
                    destination.getY(),
                    destination.getZ() + 0.5D
                );
                entity.fallDistance = 0.0F;
                if (resolve(level, entity.getUUID(), activeEntries.get(index).ownerId()) == null) {
                    throw new IllegalStateException("Subordinate disappeared during checkpoint resume");
                }
            }
        } catch (RuntimeException failure) {
            rollbackTeleport(
                level,
                allocation.key(),
                entities,
                origins,
                activeEntries.stream().map(Entry::state).toList()
            );
            return TransportResult.rejected("SUBORDINATE_RESUME_ROLLED_BACK");
        }
        for (LivingEntity entity : entities) {
            updateEntry(
                allocation.key(), entity.getUUID(), TransportState.IN_INSTANCE,
                entity.blockPosition(), level.getGameTime(), true
            );
            clearRecoveryTicket(level, entity.getUUID());
        }
        level.getServer().overworld().getDataStorage().save();
        return TransportResult.accepted("SUBORDINATES_RESUMED");
    }

    public void tick(ServerLevel level) {
        if (level.getGameTime() % HEARTBEAT_INTERVAL_TICKS != 0L) return;
        for (HubHoldBinding binding : List.copyOf(hubHolds.values())) {
            if (!binding.dimension().equals(level.dimension().location().toString())) continue;
            DungeonInstanceAllocationData.Allocation allocation =
                DungeonInstanceAllocationData.get(level).forIdentity(binding.key()).orElse(null);
            boolean runAwayFromHub = allocation != null
                && (allocation.lifecycle().state()
                    == DungeonInstanceAllocationData.LifecycleState.ACTIVE
                    || allocation.lifecycle().state()
                        == DungeonInstanceAllocationData.LifecycleState.SUSPENDED);
            if (binding.releasePending() || !runAwayFromHub) {
                releaseHubHolds(level, binding.key());
                continue;
            }
            containHubHolds(level, binding);
        }
        List<Binding> relevant = bindings.values().stream()
            .filter(binding -> binding.dimension().equals(level.dimension().location().toString()))
            .toList();
        for (Binding binding : relevant) {
            DungeonInstanceAllocationData.Allocation allocation =
                DungeonInstanceAllocationData.get(level).forIdentity(binding.key()).orElse(null);
            if (allocation != null && (allocation.lifecycle().state()
                    == DungeonInstanceAllocationData.LifecycleState.SUSPENDED
                || allocation.lifecycle().state()
                    == DungeonInstanceAllocationData.LifecycleState.COMPLETING
                || allocation.lifecycle().state()
                    == DungeonInstanceAllocationData.LifecycleState.CLEANING)
                && !reconnectPendingForOnlineMember(level, allocation)) {
                returnToHub(level, allocation);
                continue;
            }
            for (Entry entry : binding.entries()) {
                heartbeatEntry(level, allocation, binding.key(), entry, level.getGameTime());
            }
        }
    }

    static boolean reconnectPendingForOnlineMember(
        DungeonInstanceAllocationData.Allocation allocation,
        Set<UUID> onlinePlayerIds
    ) {
        return allocation != null
            && allocation.lifecycle().state()
                == DungeonInstanceAllocationData.LifecycleState.SUSPENDED
            && allocation.roster().stream().anyMatch(playerId ->
                allocation.memberDisposition(playerId)
                    == DungeonInstanceAllocationData.MemberDisposition.RECONNECT_TO_INSTANCE
                    && onlinePlayerIds.contains(playerId));
    }

    private static boolean reconnectPendingForOnlineMember(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        for (UUID playerId : allocation.roster()) {
            if (allocation.memberDisposition(playerId)
                    == DungeonInstanceAllocationData.MemberDisposition.RECONNECT_TO_INSTANCE
                && level.getServer().getPlayerList().getPlayer(playerId) != null) {
                return true;
            }
        }
        return false;
    }

    public boolean sameActivePartyMember(ServerLevel level, UUID firstId, UUID secondId) {
        if (level == null || firstId == null || secondId == null || firstId.equals(secondId)) {
            return false;
        }
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonInstanceAllocationData.Allocation first = allocationForParticipant(allocations, firstId);
        DungeonInstanceAllocationData.Allocation second = allocationForParticipant(allocations, secondId);
        if (first == null || second == null || !first.key().equals(second.key())) {
            return false;
        }
        return sameActivePartyMember(
            first,
            bindings.get(first.key()),
            level.dimension().location().toString(),
            firstId,
            secondId
        );
    }

    static boolean sameActivePartyMember(
        DungeonInstanceAllocationData.Allocation allocation,
        Binding binding,
        String levelDimension,
        UUID firstId,
        UUID secondId
    ) {
        if (allocation == null || firstId == null || secondId == null
            || firstId.equals(secondId) || levelDimension == null
            || !allocation.dimension().equals(levelDimension)
            || allocation.entryDispatch() == null
            || allocation.entryDispatch().state() != DungeonInstanceAllocationData.DispatchState.ADMITTED
            || (allocation.lifecycle().state() != DungeonInstanceAllocationData.LifecycleState.ACTIVE
                && allocation.lifecycle().state() != DungeonInstanceAllocationData.LifecycleState.COMPLETING)) {
            return false;
        }
        return activePartyParticipant(allocation, binding, firstId)
            && activePartyParticipant(allocation, binding, secondId);
    }

    private DungeonInstanceAllocationData.Allocation allocationForParticipant(
        DungeonInstanceAllocationData allocations,
        UUID participantId
    ) {
        EntityBindingRef ref = entityIndex.get(participantId);
        if (ref != null && !conflictedEntityIds.contains(participantId)
            && bindings.containsKey(ref.key())) {
            return allocations.forIdentity(ref.key()).orElse(null);
        }
        return allocations.forPlayer(participantId).orElse(null);
    }

    private static boolean activePartyParticipant(
        DungeonInstanceAllocationData.Allocation allocation,
        Binding binding,
        UUID participantId
    ) {
        if (allocation.roster().contains(participantId)) {
            return true;
        }
        if (binding == null || !binding.key().equals(allocation.key())
            || !binding.hostId().equals(allocation.host())) {
            return false;
        }
        Entry entry = binding.entry(participantId);
        return entry != null
            && entry.ownerId().equals(allocation.host())
            && entry.state().isInstanceBound();
    }

    public boolean markLost(ServerLevel level, UUID entityId) {
        EntityBindingRef ref = entityIndex.get(entityId);
        Binding binding = ref == null ? null : bindings.get(ref.key());
        if (ref == null || conflictedEntityIds.contains(entityId)
            || binding == null
            || !binding.dimension().equals(level.dimension().location().toString())) {
            return false;
        }
        if (ref.entry().state() == TransportState.LOST) return false;
        updateEntry(
            ref.key(), entityId, TransportState.LOST,
            ref.entry().lastKnown(), level.getGameTime(), true
        );
        clearRecoveryTicket(level, entityId);
        return true;
    }

    public boolean markRemoved(ServerLevel level, UUID entityId) {
        return markLost(level, entityId);
    }

    public boolean safeForCleanup(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        Binding binding = bindings.get(key);
        if (binding == null) return true;
        if (!binding.dimension().equals(level.dimension().location().toString())) return false;
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(level).forIdentity(key).orElse(null);
        if (allocation == null || !allocation.dimension().equals(binding.dimension())) return false;
        reconcileUnresolvedHubEntries(level, allocation);
        binding = bindings.get(key);
        if (binding == null) return true;
        for (Entry entry : binding.entries()) {
            if (entry.state() == TransportState.LOST) continue;
            TransportState stable = stableState(entry.state());
            if (stable != TransportState.AT_HUB && stable != TransportState.EVACUATED) return false;
            LivingEntity entity = resolveWithRecovery(level, entry, level.getGameTime());
            if (entity == null) return false;
            if (stable == TransportState.EVACUATED) {
                if (!containEvacuated(level, binding, entry, entity)) return false;
            } else {
                updateEntry(key, entry.entityId(), TransportState.AT_HUB,
                    entity.blockPosition(), level.getGameTime(), false);
            }
        }
        return true;
    }

    public boolean requiresQuarantine(DungeonInstanceAllocationData.InstanceKey key) {
        Binding binding = bindings.get(key);
        return binding != null && (conflictedEntityIds.stream().anyMatch(entityId ->
                binding.entries().stream().anyMatch(entry -> entry.entityId().equals(entityId)))
            || binding.entries().stream().anyMatch(entry ->
                entry.state() == TransportState.UNRESOLVED
                    || entry.state() == TransportState.MISSING_OR_REMOVED
                    || entry.state() == TransportState.ENTRY_PENDING));
    }

    public boolean safeForCleanup(DungeonInstanceAllocationData.InstanceKey key) {
        Binding binding = bindings.get(key);
        return binding == null || binding.entries().stream()
            .allMatch(entry -> stableState(entry.state()) == TransportState.AT_HUB
                || stableState(entry.state()) == TransportState.EVACUATED
                || entry.state() == TransportState.LOST);
    }

    public void retire(DungeonInstanceAllocationData.InstanceKey key) {
        Binding removed = bindings.remove(key);
        if (removed != null) {
            for (Entry entry : removed.entries()) {
                entityIndex.remove(entry.entityId());
                observations.remove(entry.entityId());
                recoveryTickets.remove(entry.entityId());
                lastDirtyGameTime.remove(entry.entityId());
            }
            rebuildEntityIndex();
            setDirty();
        }
        HubHoldBinding hold = hubHolds.get(key);
        if (hold != null && !hold.releasePending()) {
            hubHolds.put(key, hold.withEntries(hold.entries(), true));
            setDirty();
        }
    }

    public void retire(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        releaseHubHolds(level, key);
        Binding removed = bindings.get(key);
        if (removed != null) {
            for (Entry entry : removed.entries()) {
                clearRecoveryTicket(level, entry.entityId());
                if (entry.restoreFollow()) {
                    LivingEntity entity = resolve(level, entry.entityId(), entry.ownerId());
                    if (entity != null) {
                        TensuraCompat.restoreSubordinateFollow(entity);
                    }
                }
            }
        }
        retire(key);
    }

    private void containHubHolds(ServerLevel level, HubHoldBinding binding) {
        List<HubHoldEntry> updated = new ArrayList<>();
        boolean changed = false;
        int index = 0;
        for (HubHoldEntry entry : binding.entries()) {
            LivingEntity entity = resolve(level, entry.entityId(), entry.ownerId());
            if (entity == null) {
                updated.add(entry);
                index++;
                continue;
            }
            TensuraCompat.wanderSubordinateAtHub(entity);
            double offset = (index - (binding.entries().size() - 1) / 2.0D) * 0.8D;
            double targetX = binding.hubTarget().getX() + 0.5D + offset;
            double targetY = binding.hubTarget().getY();
            double targetZ = binding.hubTarget().getZ() + 0.5D;
            if (entity.distanceToSqr(targetX, targetY, targetZ)
                > HUB_HOLD_CONTAINMENT_RADIUS_SQR) {
                level.getChunkAt(binding.hubTarget());
                entity.teleportTo(targetX, targetY, targetZ);
                entity.fallDistance = 0.0F;
            }
            HubHoldEntry next = entry.withLastKnown(entity.blockPosition());
            updated.add(next);
            changed |= !next.lastKnown().equals(entry.lastKnown());
            index++;
        }
        if (changed) {
            hubHolds.put(binding.key(), binding.withEntries(updated, false));
            setDirty();
        }
    }

    private void heartbeatEntry(
        ServerLevel level,
        DungeonInstanceAllocationData.Allocation allocation,
        DungeonInstanceAllocationData.InstanceKey key,
        Entry entry,
        long gameTime
    ) {
        if (entry.state() == TransportState.MISSING_OR_REMOVED
            || entry.state() == TransportState.ENTRY_PENDING
            || stableState(entry.state()) == null) {
            return;
        }
        LivingEntity entity = resolve(level, entry.entityId(), entry.ownerId());
        if (entity != null) {
            Binding binding = bindings.get(key);
            if (entry.state().isEvacuated()
                && (allocation == null || binding == null
                    || !containEvacuated(level, binding, entry, entity))) {
                return;
            }
            updateEntry(key, entry.entityId(), stableState(entry.state()),
                entity.blockPosition(), gameTime, false);
            clearRecoveryTicket(level, entry.entityId());
            observations.remove(entry.entityId());
            return;
        }
        resolveWithRecovery(level, entry, gameTime);
    }

    private LivingEntity resolveWithRecovery(
        ServerLevel level,
        Entry entry,
        long gameTime
    ) {
        if (entry.state() == TransportState.MISSING_OR_REMOVED
            || stableState(entry.state()) == null) {
            return null;
        }
        LivingEntity resolved = resolve(level, entry.entityId(), entry.ownerId());
        if (resolved != null) {
            TransportState target = stableState(entry.state());
            updateEntry(
                entryKey(entry.entityId()), entry.entityId(), target,
                resolved.blockPosition(), gameTime, false
            );
            clearRecoveryTicket(level, entry.entityId());
            observations.remove(entry.entityId());
            return resolved;
        }

        TransportState target = stableState(entry.state());
        if (entry.state() == target) {
            updateEntry(
                entryKey(entry.entityId()), entry.entityId(), unloadedState(target),
                entry.lastKnown(), gameTime, true
            );
            return null;
        }

        RecoveryObservation observation = observations.computeIfAbsent(
            entry.entityId(), ignored -> new RecoveryObservation()
        );
        if (entry.state() == TransportState.UNRESOLVED
            && gameTime - observation.lastAttemptGameTime() < RECOVERY_RETRY_INTERVAL_TICKS) {
            return null;
        }
        if (observation.pendingSinceGameTime() < 0L) {
            observation.start(gameTime);
        } else if (gameTime - observation.pendingSinceGameTime() >= RECOVERY_GRACE_TICKS) {
            clearRecoveryTicket(level, entry.entityId());
            updateEntry(
                entryKey(entry.entityId()), entry.entityId(), TransportState.UNRESOLVED,
                entry.lastKnown(), gameTime, true
            );
            observation.finish(gameTime);
            return null;
        }
        observation.attempt(gameTime);
        updateEntry(
            entryKey(entry.entityId()), entry.entityId(), pendingState(target),
            entry.lastKnown(), gameTime, true
        );
        ensureRecoveryTicket(level, entry.entityId(), entry.lastKnown());
        resolved = resolve(level, entry.entityId(), entry.ownerId());
        if (resolved != null) {
            updateEntry(
                entryKey(entry.entityId()), entry.entityId(), target,
                resolved.blockPosition(), gameTime, true
            );
            clearRecoveryTicket(level, entry.entityId());
            observations.remove(entry.entityId());
        }
        return resolved;
    }

    private List<LivingEntity> resolveAll(
        ServerLevel level,
        List<DungeonInstanceAllocationData.FrozenParticipant> participants
    ) {
        List<LivingEntity> entities = new ArrayList<>();
        for (DungeonInstanceAllocationData.FrozenParticipant participant : participants) {
            LivingEntity entity = resolve(level, participant.id(), participant.ownerId());
            if (entity == null) return List.of();
            entities.add(entity);
        }
        return entities;
    }

    private static LivingEntity resolve(ServerLevel level, UUID entityId, UUID ownerId) {
        if (!(level.getEntity(entityId) instanceof LivingEntity living) || living.isRemoved()
            || !living.isAlive() || TensuraCompat.subordinateOwnerId(living)
                .filter(ownerId::equals).isEmpty()) {
            return null;
        }
        return living;
    }

    private static boolean validEnteredEntity(
        ServerLevel level,
        LivingEntity entity,
        UUID entityId,
        UUID ownerId,
        double targetX,
        double targetY,
        double targetZ
    ) {
        return validBoundEntity(level, entity, entityId, ownerId)
            && entity.distanceToSqr(targetX, targetY, targetZ) <= 4.0D;
    }

    private static boolean validBoundEntity(
        ServerLevel level,
        LivingEntity entity,
        UUID entityId,
        UUID ownerId
    ) {
        return entity != null
            && entity.level() == level
            && entity.getUUID().equals(entityId)
            && !entity.isRemoved()
            && entity.isAlive()
            && TensuraCompat.subordinateOwnerId(entity).filter(ownerId::equals).isPresent();
    }

    private void rollbackTeleport(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey key,
        List<LivingEntity> entities,
        List<BlockPos> origins,
        List<TransportState> priorStates
    ) {
        for (int index = 0; index < entities.size(); index++) {
            LivingEntity entity = entities.get(index);
            try {
                BlockPos origin = origins.get(index);
                level.getChunkAt(origin);
                entity.teleportTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            } catch (RuntimeException ignored) {
            }
            TransportState prior = index < priorStates.size() ? stableState(priorStates.get(index)) : null;
            TransportState result = prior == null ? TransportState.UNRESOLVED : prior;
            updateEntry(key, entity.getUUID(), result, entity.blockPosition(), level.getGameTime(), true);
        }
        level.getServer().overworld().getDataStorage().save();
    }

    private DungeonInstanceAllocationData.InstanceKey entryKey(UUID entityId) {
        EntityBindingRef ref = entityIndex.get(entityId);
        return ref == null ? null : ref.key();
    }

    private void updateEntry(
        DungeonInstanceAllocationData.InstanceKey key,
        UUID entityId,
        TransportState state,
        BlockPos lastKnown,
        long gameTime,
        boolean forceDirty
    ) {
        if (key == null) return;
        Binding binding = bindings.get(key);
        if (binding == null) return;
        Entry prior = binding.entry(entityId);
        if (prior == null) return;
        BlockPos immutable = lastKnown.immutable();
        boolean stateChanged = prior.state() != state;
        boolean chunkChanged = new ChunkPos(prior.lastKnown()).toLong()
            != new ChunkPos(immutable).toLong();
        if (!stateChanged && prior.lastKnown().equals(immutable)) return;
        bindings.put(key, binding.withState(entityId, state, immutable));
        EntityBindingRef ref = entityIndex.get(entityId);
        if (ref != null) {
            entityIndex.put(entityId, new EntityBindingRef(key, bindings.get(key).entry(entityId)));
        }
        long previousDirty = lastDirtyGameTime.getOrDefault(
            entityId, -(long) PERSIST_INTERVAL_TICKS
        );
        if (forceDirty || stateChanged || chunkChanged
            || gameTime - previousDirty >= PERSIST_INTERVAL_TICKS) {
            lastDirtyGameTime.put(entityId, gameTime);
            setDirty();
        }
    }

    private void updateEvacuatedEntry(
        DungeonInstanceAllocationData.InstanceKey key,
        UUID entityId,
        BlockPos lastKnown,
        boolean restoreFollow,
        long gameTime
    ) {
        Binding binding = bindings.get(key);
        if (binding == null || binding.entry(entityId) == null) return;
        bindings.put(key, binding.withEvacuation(entityId, lastKnown, restoreFollow));
        entityIndex.put(entityId, new EntityBindingRef(key, bindings.get(key).entry(entityId)));
        lastDirtyGameTime.put(entityId, gameTime);
        setDirty();
    }

    private boolean containEvacuated(
        ServerLevel level,
        Binding binding,
        Entry entry,
        LivingEntity entity
    ) {
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(level).forIdentity(binding.key()).orElse(null);
        if (allocation == null || allocation.entryDispatch() == null
            || TensuraCompat.pauseSubordinateFollow(entity).isEmpty()) {
            return false;
        }
        BlockPos target = allocation.entryDispatch().hubReturnTarget().above();
        double offset = (entry.slot() - 2.5D) * 0.28D;
        double targetX = target.getX() + 0.5D + offset;
        double targetY = target.getY();
        double targetZ = target.getZ() + 0.5D;
        if (entity.distanceToSqr(targetX, targetY, targetZ) > 4.0D) {
            level.getChunkAt(target);
            entity.teleportTo(targetX, targetY, targetZ);
            entity.fallDistance = 0.0F;
        }
        updateEntry(
            binding.key(), entry.entityId(), TransportState.EVACUATED,
            entity.blockPosition(), level.getGameTime(), false
        );
        return true;
    }

    private void rebuildEntityIndex() {
        entityIndex.clear();
        conflictedEntityIds.clear();
        for (Binding binding : bindings.values()) {
            for (Entry entry : binding.entries()) {
                EntityBindingRef prior = entityIndex.putIfAbsent(
                    entry.entityId(), new EntityBindingRef(binding.key(), entry)
                );
                if (prior != null && !prior.key().equals(binding.key())) {
                    conflictedEntityIds.add(entry.entityId());
                }
            }
        }
    }

    private void ensureRecoveryTicket(ServerLevel level, UUID entityId, BlockPos position) {
        ChunkPos requested = new ChunkPos(position);
        RecoveryTicket existing = recoveryTickets.get(entityId);
        if (existing != null && (!existing.dimension().equals(level.dimension().location().toString())
            || existing.chunk().toLong() != requested.toLong())) {
            removeRecoveryTicket(level, existing);
            existing = null;
        }
        long ticketKey = existing == null ? nextRecoveryTicketKey++ : existing.ticketKey();
        level.getChunkSource().addRegionTicket(
            RECOVERY_CHUNK_TICKET, requested, RECOVERY_TICKET_LEVEL, ticketKey
        );
        recoveryTickets.put(entityId, new RecoveryTicket(
            level.dimension().location().toString(), requested, ticketKey
        ));
        level.getChunk(requested.x, requested.z);
    }

    private void clearRecoveryTicket(ServerLevel level, UUID entityId) {
        RecoveryTicket ticket = recoveryTickets.remove(entityId);
        if (ticket != null && ticket.dimension().equals(level.dimension().location().toString())) {
            removeRecoveryTicket(level, ticket);
        }
    }

    private static void removeRecoveryTicket(ServerLevel level, RecoveryTicket ticket) {
        level.getChunkSource().removeRegionTicket(
            RECOVERY_CHUNK_TICKET,
            ticket.chunk(),
            RECOVERY_TICKET_LEVEL,
            ticket.ticketKey()
        );
    }

    private static TransportState stableState(TransportState state) {
        return switch (state) {
            case AT_HUB, UNLOADED_AT_HUB, RECOVERY_PENDING_AT_HUB -> TransportState.AT_HUB;
            case IN_INSTANCE, UNLOADED_IN_INSTANCE, RECOVERY_PENDING_IN_INSTANCE ->
                TransportState.IN_INSTANCE;
            case EVACUATED, UNLOADED_EVACUATED, RECOVERY_PENDING_EVACUATED ->
                TransportState.EVACUATED;
            default -> null;
        };
    }

    private static TransportState pendingState(TransportState target) {
        return switch (target) {
            case AT_HUB -> TransportState.RECOVERY_PENDING_AT_HUB;
            case EVACUATED -> TransportState.RECOVERY_PENDING_EVACUATED;
            default -> TransportState.RECOVERY_PENDING_IN_INSTANCE;
        };
    }

    private static TransportState unloadedState(TransportState target) {
        return switch (target) {
            case AT_HUB -> TransportState.UNLOADED_AT_HUB;
            case EVACUATED -> TransportState.UNLOADED_EVACUATED;
            default -> TransportState.UNLOADED_IN_INSTANCE;
        };
    }

    public enum TransportState {
        AT_HUB,
        IN_INSTANCE,
        UNLOADED_AT_HUB,
        UNLOADED_IN_INSTANCE,
        RECOVERY_PENDING_AT_HUB,
        RECOVERY_PENDING_IN_INSTANCE,
        EVACUATED,
        UNLOADED_EVACUATED,
        RECOVERY_PENDING_EVACUATED,
        ENTRY_PENDING,
        UNRESOLVED,
        MISSING_OR_REMOVED,
        LOST;

        public boolean isInstanceBound() {
            return this == IN_INSTANCE || this == UNLOADED_IN_INSTANCE
                || this == RECOVERY_PENDING_IN_INSTANCE;
        }

        boolean isEvacuated() {
            return this == EVACUATED || this == UNLOADED_EVACUATED
                || this == RECOVERY_PENDING_EVACUATED;
        }

        static TransportState parse(String value) {
            try {
                return TransportState.valueOf(value);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                return UNRESOLVED;
            }
        }
    }

    public record Entry(
        UUID entityId,
        UUID ownerId,
        int slot,
        TransportState state,
        BlockPos lastKnown,
        boolean restoreFollow
    ) {
        public Entry(
            UUID entityId,
            UUID ownerId,
            int slot,
            TransportState state,
            BlockPos lastKnown
        ) {
            this(entityId, ownerId, slot, state, lastKnown, false);
        }

        public Entry {
            if (entityId == null || ownerId == null || slot < 2 || slot > 4 || state == null
                || lastKnown == null) {
                throw new IllegalArgumentException("Incomplete subordinate transport entry");
            }
            lastKnown = lastKnown.immutable();
        }
    }

    record HubHoldEntry(
        UUID entityId,
        UUID ownerId,
        BlockPos lastKnown,
        boolean restoreFollow
    ) {
        HubHoldEntry {
            if (entityId == null || ownerId == null || lastKnown == null) {
                throw new IllegalArgumentException("Incomplete subordinate Hub hold entry");
            }
            lastKnown = lastKnown.immutable();
        }

        HubHoldEntry withLastKnown(BlockPos position) {
            return new HubHoldEntry(entityId, ownerId, position, restoreFollow);
        }
    }

    record HubHoldBinding(
        DungeonInstanceAllocationData.InstanceKey key,
        String dimension,
        UUID hostId,
        BlockPos hubTarget,
        List<HubHoldEntry> entries,
        boolean releasePending
    ) {
        HubHoldBinding {
            if (key == null || dimension == null || dimension.isBlank() || hostId == null
                || hubTarget == null || entries == null || entries.isEmpty()) {
                throw new IllegalArgumentException("Incomplete subordinate Hub hold binding");
            }
            entries = List.copyOf(entries);
            Set<UUID> identities = new HashSet<>();
            for (HubHoldEntry entry : entries) {
                if (!entry.ownerId().equals(hostId) || !identities.add(entry.entityId())) {
                    throw new IllegalArgumentException("Invalid subordinate Hub hold roster");
                }
            }
            hubTarget = hubTarget.immutable();
        }

        HubHoldBinding withEntries(List<HubHoldEntry> nextEntries, boolean pending) {
            return new HubHoldBinding(
                key, dimension, hostId, hubTarget, nextEntries, pending
            );
        }
    }

    public record Binding(
        DungeonInstanceAllocationData.InstanceKey key,
        String dimension,
        UUID hostId,
        List<Entry> entries
    ) {
        public Binding {
            if (key == null || dimension == null || dimension.isBlank() || hostId == null
                || entries == null || entries.isEmpty() || entries.size() > 3) {
                throw new IllegalArgumentException("Incomplete subordinate transport binding");
            }
            entries = List.copyOf(entries);
            Set<UUID> identities = new HashSet<>();
            Set<Integer> slots = new HashSet<>();
            for (Entry entry : entries) {
                if (!entry.ownerId().equals(hostId) || !identities.add(entry.entityId())
                    || !slots.add(entry.slot())) {
                    throw new IllegalArgumentException("Invalid subordinate transport roster");
                }
            }
        }

        boolean sameIdentities(Binding other) {
            if (!key.equals(other.key) || !dimension.equals(other.dimension)
                || !hostId.equals(other.hostId) || entries.size() != other.entries.size()) {
                return false;
            }
            for (int index = 0; index < entries.size(); index++) {
                Entry left = entries.get(index);
                Entry right = other.entries.get(index);
                if (!left.entityId().equals(right.entityId())
                    || !left.ownerId().equals(right.ownerId()) || left.slot() != right.slot()) {
                    return false;
                }
            }
            return true;
        }

        Entry entry(UUID entityId) {
            return entries.stream().filter(value -> value.entityId().equals(entityId)).findFirst()
                .orElse(null);
        }

        Binding withState(UUID entityId, TransportState state, BlockPos lastKnown) {
            return new Binding(key, dimension, hostId, entries.stream()
                .map(entry -> entry.entityId().equals(entityId)
                    ? new Entry(
                        entry.entityId(), entry.ownerId(), entry.slot(), state, lastKnown,
                        entry.restoreFollow()
                    )
                    : entry)
                .toList());
        }

        Binding withEvacuation(
            UUID entityId,
            BlockPos lastKnown,
            boolean restoreFollow
        ) {
            return new Binding(key, dimension, hostId, entries.stream()
                .map(entry -> entry.entityId().equals(entityId)
                    ? new Entry(
                        entry.entityId(), entry.ownerId(), entry.slot(), TransportState.EVACUATED,
                        lastKnown, restoreFollow
                    )
                    : entry)
                .toList());
        }
    }

    public record BindingRef(
        DungeonInstanceAllocationData.InstanceKey key,
        int slot,
        TransportState state
    ) {
    }

    private record HubReturnPlan(
        boolean accepted,
        String reason,
        BlockPos target,
        List<Entry> entries,
        List<LivingEntity> entities
    ) {
        private HubReturnPlan {
            reason = reason == null ? "" : reason;
            entries = entries == null ? List.of() : List.copyOf(entries);
            entities = entities == null ? List.of() : List.copyOf(entities);
        }

        static HubReturnPlan accepted(
            String reason,
            BlockPos target,
            List<Entry> entries,
            List<LivingEntity> entities
        ) {
            return new HubReturnPlan(true, reason, target, entries, entities);
        }

        static HubReturnPlan rejected(String reason) {
            return new HubReturnPlan(false, reason, null, List.of(), List.of());
        }
    }

    enum TerminalEscapeOutcome {
        WHISTLE,
        UNPROTECTED_SURVIVAL,
        BRACELET,
        LOST
    }

    record TerminalEscapeEntry(
        Entry entry,
        LivingEntity entity,
        TerminalEscapeOutcome outcome,
        DungeonPreparationLoadoutData.BraceletReservation bracelet,
        DungeonPreparationLoadoutData.WhistleReservation whistle
    ) {
    }

    record TerminalEscapePlan(
        boolean accepted,
        String reason,
        DungeonInstanceAllocationData.InstanceKey key,
        BlockPos target,
        List<TerminalEscapeEntry> entries
    ) {
        TerminalEscapePlan {
            reason = reason == null ? "" : reason;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        static TerminalEscapePlan accepted(
            DungeonInstanceAllocationData.InstanceKey key,
            BlockPos target,
            List<TerminalEscapeEntry> entries
        ) {
            return new TerminalEscapePlan(true, "TERMINAL_ESCAPE_VERIFIED", key, target, entries);
        }

        static TerminalEscapePlan rejected(String reason) {
            return new TerminalEscapePlan(false, reason, null, null, List.of());
        }

        List<DungeonPreparationLoadoutData.BraceletReservation> bracelets() {
            return entries.stream().map(TerminalEscapeEntry::bracelet)
                .filter(java.util.Objects::nonNull).toList();
        }

        List<DungeonPreparationLoadoutData.WhistleReservation> whistles() {
            return entries.stream().map(TerminalEscapeEntry::whistle)
                .filter(java.util.Objects::nonNull).toList();
        }

        Map<UUID, Container> inventories(ServerPlayer host) {
            Map<UUID, Container> result = new HashMap<>();
            if (host != null) {
                result.put(host.getUUID(), host.getInventory());
            }
            for (TerminalEscapeEntry entry : entries) {
                if (entry.bracelet() == null && entry.whistle() == null) continue;
                Container inventory = TensuraCompat.subordinateInventory(entry.entity()).orElse(null);
                if (inventory == null) return Map.of();
                result.put(entry.entry().entityId(), inventory);
            }
            return Map.copyOf(result);
        }
    }

    record TerminalEscapeStage(
        boolean accepted,
        String reason,
        TerminalEscapePlan plan,
        List<TerminalEscapeEntry> moved,
        Map<UUID, BlockPos> origins,
        Map<UUID, Boolean> restoreFollow
    ) {
        TerminalEscapeStage {
            reason = reason == null ? "" : reason;
            moved = moved == null ? List.of() : List.copyOf(moved);
            origins = origins == null ? Map.of() : Map.copyOf(origins);
            restoreFollow = restoreFollow == null ? Map.of() : Map.copyOf(restoreFollow);
        }

        static TerminalEscapeStage accepted(
            TerminalEscapePlan plan,
            List<TerminalEscapeEntry> moved,
            Map<UUID, BlockPos> origins,
            Map<UUID, Boolean> restoreFollow
        ) {
            return new TerminalEscapeStage(
                true, "TERMINAL_ESCAPE_STAGED", plan, moved, origins, restoreFollow
            );
        }

        static TerminalEscapeStage rejected(String reason) {
            return new TerminalEscapeStage(false, reason, null, List.of(), Map.of(), Map.of());
        }
    }

    private record EntityBindingRef(
        DungeonInstanceAllocationData.InstanceKey key,
        Entry entry
    ) {
    }

    private record RecoveryTicket(
        String dimension,
        ChunkPos chunk,
        long ticketKey
    ) {
    }

    private static final class RecoveryObservation {
        private long lastAttemptGameTime = -1L;
        private long pendingSinceGameTime = -1L;

        long lastAttemptGameTime() {
            return lastAttemptGameTime;
        }

        long pendingSinceGameTime() {
            return pendingSinceGameTime;
        }

        void start(long gameTime) {
            pendingSinceGameTime = gameTime;
        }

        void attempt(long gameTime) {
            lastAttemptGameTime = gameTime;
        }

        void finish(long gameTime) {
            pendingSinceGameTime = -1L;
            lastAttemptGameTime = gameTime;
        }
    }

    public record TransportResult(boolean accepted, String reason) {
        static TransportResult accepted(String reason) {
            return new TransportResult(true, reason);
        }

        static TransportResult rejected(String reason) {
            return new TransportResult(false, reason);
        }
    }
}
