package com.mineclawd.tensuradungeon.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonDeferredReturnData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_deferred_returns";
    private static final String RETURNS_KEY = "Returns";
    private static final String PLAYER_KEY = "Player";
    private static final String INSTANCE_KEY = "Instance";
    private static final String DIMENSION_KEY = "Dimension";
    private static final String HUB_TARGET_KEY = "HubTarget";
    private static final String REASON_KEY = "Reason";
    private static final String CREATED_GAME_TIME_KEY = "CreatedGameTime";

    private final Map<UUID, DeferredReturn> returnsByPlayer = new LinkedHashMap<>();

    public static DungeonDeferredReturnData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(DungeonDeferredReturnData::new, DungeonDeferredReturnData::load),
            DATA_NAME
        );
    }

    public boolean recordAllocation(
        DungeonInstanceAllocationData.Allocation allocation,
        String reason,
        long gameTime
    ) {
        if (allocation == null || allocation.entryDispatch() == null) {
            return false;
        }
        if (!conflictsFor(allocation).isEmpty()) {
            return false;
        }
        DeferredReturn requested = new DeferredReturn(
            allocation.key(),
            allocation.dimension(),
            allocation.entryDispatch().hubReturnTarget(),
            normalizeReason(reason),
            Math.max(0L, gameTime)
        );
        boolean changed = false;
        for (UUID playerId : allocation.roster()) {
            DeferredReturn existing = returnsByPlayer.get(playerId);
            if (!requested.equals(existing)) {
                returnsByPlayer.put(playerId, requested);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
        return true;
    }

    public Map<UUID, DeferredReturn> conflictsFor(
        DungeonInstanceAllocationData.Allocation allocation
    ) {
        if (allocation == null || allocation.entryDispatch() == null) {
            return Map.of();
        }
        Map<UUID, DeferredReturn> conflicts = new LinkedHashMap<>();
        for (UUID playerId : allocation.roster()) {
            DeferredReturn existing = returnsByPlayer.get(playerId);
            if (existing != null && !existing.instanceKey().equals(allocation.key())) {
                conflicts.put(playerId, existing);
            }
        }
        return Map.copyOf(conflicts);
    }

    public Optional<DeferredReturn> forPlayer(UUID playerId) {
        return Optional.ofNullable(returnsByPlayer.get(playerId));
    }

    public boolean consume(UUID playerId, DungeonInstanceAllocationData.InstanceKey expected) {
        DeferredReturn existing = returnsByPlayer.get(playerId);
        if (existing == null) {
            return true;
        }
        if (expected == null || !existing.instanceKey().equals(expected)) {
            return false;
        }
        returnsByPlayer.remove(playerId);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag returns = new ListTag();
        returnsByPlayer.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(UUID::toString)))
            .forEach(entry -> {
                CompoundTag value = new CompoundTag();
                value.putUUID(PLAYER_KEY, entry.getKey());
                CompoundTag instance = new CompoundTag();
                entry.getValue().instanceKey().save(instance);
                value.put(INSTANCE_KEY, instance);
                value.putString(DIMENSION_KEY, entry.getValue().dimension());
                value.put(HUB_TARGET_KEY, NbtUtils.writeBlockPos(entry.getValue().hubTarget()));
                value.putString(REASON_KEY, entry.getValue().reason());
                value.putLong(CREATED_GAME_TIME_KEY, entry.getValue().createdGameTime());
                returns.add(value);
            });
        tag.put(RETURNS_KEY, returns);
        return tag;
    }

    static DungeonDeferredReturnData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        DungeonDeferredReturnData data = new DungeonDeferredReturnData();
        ListTag returns = tag.getList(RETURNS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < returns.size(); index++) {
            CompoundTag value = returns.getCompound(index);
            if (!value.hasUUID(PLAYER_KEY)
                || !value.contains(INSTANCE_KEY, Tag.TAG_COMPOUND)) {
                throw new IllegalStateException("Malformed deferred dungeon return");
            }
            UUID playerId = value.getUUID(PLAYER_KEY);
            DungeonInstanceAllocationData.InstanceKey instanceKey =
                DungeonInstanceAllocationData.InstanceKey.loadOptional(
                    value.getCompound(INSTANCE_KEY)
                );
            BlockPos hubTarget = NbtUtils.readBlockPos(value, HUB_TARGET_KEY)
                .map(BlockPos::immutable)
                .orElseThrow(() -> new IllegalStateException(
                    "Deferred dungeon return is missing hub target"
                ));
            DeferredReturn deferred = new DeferredReturn(
                instanceKey,
                value.getString(DIMENSION_KEY),
                hubTarget,
                value.getString(REASON_KEY),
                value.getLong(CREATED_GAME_TIME_KEY)
            );
            if (data.returnsByPlayer.putIfAbsent(playerId, deferred) != null) {
                throw new IllegalStateException("Duplicate deferred dungeon return player");
            }
        }
        return data;
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "UNSPECIFIED" : reason.trim();
    }

    public record DeferredReturn(
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        String dimension,
        BlockPos hubTarget,
        String reason,
        long createdGameTime
    ) {
        public DeferredReturn {
            if (instanceKey == null || dimension == null || dimension.isBlank()
                || hubTarget == null) {
                throw new IllegalArgumentException("Deferred dungeon return is incomplete");
            }
            dimension = dimension.trim();
            hubTarget = hubTarget.immutable();
            reason = normalizeReason(reason);
            createdGameTime = Math.max(0L, createdGameTime);
        }
    }
}
