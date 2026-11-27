package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.compat.VisualBarrierHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class GeneratedFloorRuntimeData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_generated_floor_runtime";
    private final Map<OwnerKey, DimensionState> states = new HashMap<>();

    public static GeneratedFloorRuntimeData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(GeneratedFloorRuntimeData::new, GeneratedFloorRuntimeData::load), DATA_NAME);
    }

    public static GeneratedFloorRuntimeData load(CompoundTag tag, HolderLookup.Provider registries) {
        GeneratedFloorRuntimeData data = new GeneratedFloorRuntimeData();
        ListTag dimensionsTag = tag.getList("Dimensions", Tag.TAG_COMPOUND);
        for (int i = 0; i < dimensionsTag.size(); i++) {
            CompoundTag dimensionTag = dimensionsTag.getCompound(i);
            if (!dimensionTag.contains("DimensionKey")) {
                continue;
            }
            String dimensionKey = dimensionTag.getString("DimensionKey");
            DungeonInstanceAllocationData.InstanceKey instanceKey =
                DungeonInstanceAllocationData.InstanceKey.loadOptional(dimensionTag);
            List<InvalidGeneratedData> invalidData = new ArrayList<>(loadInvalidGeneratedData(dimensionTag));
            LastGeneratedPlacement placement = dimensionTag.contains("LastGeneratedPlacement", Tag.TAG_COMPOUND)
                ? loadLastGeneratedPlacement(dimensionTag.getCompound("LastGeneratedPlacement"), invalidData)
                : null;
            HubGateRuntimeState gateState = dimensionTag.contains("HubGateState", Tag.TAG_COMPOUND)
                ? loadHubGateRuntimeState(dimensionTag.getCompound("HubGateState"), invalidData)
                : null;
            CleanupState cleanupState = dimensionTag.contains("CleanupState", Tag.TAG_COMPOUND)
                ? loadCleanupState(dimensionTag.getCompound("CleanupState"), invalidData)
                : null;
            GenerationFailureState generationFailure = dimensionTag.contains("GenerationFailure", Tag.TAG_COMPOUND)
                ? loadGenerationFailureState(dimensionTag.getCompound("GenerationFailure"))
                : null;
            DimensionState state = new DimensionState(
                placement, gateState, cleanupState, generationFailure, List.copyOf(invalidData)
            );
            OwnerKey owner = new OwnerKey(dimensionKey, instanceKey);
            if (data.states.containsKey(owner)) {
                throw new IllegalStateException(
                    "Duplicate generated-floor runtime owner in save: " + owner.describe()
                );
            }
            if (!state.isEmpty()) {
                data.states.put(owner, state);
            } else if (instanceKey != null) {
                data.states.put(owner, DimensionState.EMPTY);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag dimensionsTag = new ListTag();
        List<Map.Entry<OwnerKey, DimensionState>> owners = new ArrayList<>(states.entrySet());
        owners.sort(Map.Entry.comparingByKey());
        for (Map.Entry<OwnerKey, DimensionState> ownerEntry : owners) {
            OwnerKey owner = ownerEntry.getKey();
            DimensionState state = ownerEntry.getValue();
            CompoundTag dimensionTag = new CompoundTag();
            dimensionTag.putString("DimensionKey", owner.dimensionKey());
            if (owner.instanceKey() != null) {
                owner.instanceKey().save(dimensionTag);
            }
            if (state.lastGeneratedPlacement() != null) {
                dimensionTag.put("LastGeneratedPlacement", saveLastGeneratedPlacement(state.lastGeneratedPlacement()));
            }
            if (state.hubGateState() != null) {
                dimensionTag.put("HubGateState", saveHubGateRuntimeState(state.hubGateState()));
            }
            if (state.cleanupState() != null) {
                dimensionTag.put("CleanupState", saveCleanupState(state.cleanupState()));
            }
            if (state.generationFailure() != null) {
                dimensionTag.put("GenerationFailure", saveGenerationFailureState(state.generationFailure()));
            }
            if (!state.invalidData().isEmpty()) {
                dimensionTag.put("InvalidGeneratedData", saveInvalidGeneratedData(state.invalidData()));
            }
            dimensionsTag.add(dimensionTag);
        }
        tag.put("Dimensions", dimensionsTag);
        return tag;
    }

    @Nullable
    public DungeonInstanceAllocationData.InstanceKey getInstanceKey(String dimensionKey) {
        return ownerForDimension(dimensionKey).instanceKey();
    }

    public List<DungeonInstanceAllocationData.InstanceKey> getInstanceKeys(String dimensionKey) {
        return states.keySet().stream()
            .filter(owner -> owner.dimensionKey().equals(dimensionKey))
            .map(OwnerKey::instanceKey)
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(key -> key.instanceId().toString()))
            .toList();
    }

    public boolean initializeInstance(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (dimensionKey == null || dimensionKey.isBlank() || instanceKey == null) {
            return false;
        }
        OwnerKey owner = new OwnerKey(dimensionKey, instanceKey);
        if (states.containsKey(owner)) {
            return true;
        }
        states.put(owner, DimensionState.EMPTY);
        setDirty();
        return true;
    }

    public boolean canRetireInstance(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (dimensionKey == null || dimensionKey.isBlank() || instanceKey == null) {
            return false;
        }
        DimensionState state = states.get(new OwnerKey(dimensionKey, instanceKey));
        return state == null || (
            state.lastGeneratedPlacement() == null
                && state.cleanupState() == null
                && state.generationFailure() == null
                && state.invalidData().isEmpty()
        );
    }

    public boolean retireInstance(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (!canRetireInstance(dimensionKey, instanceKey)) {
            return false;
        }
        boolean removed = states.remove(new OwnerKey(dimensionKey, instanceKey)) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public boolean bindInstance(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (!canBindInstance(dimensionKey, instanceKey)) {
            return false;
        }
        OwnerKey currentOwner = ownerForDimension(dimensionKey);
        OwnerKey targetOwner = new OwnerKey(dimensionKey, instanceKey);
        if (targetOwner.equals(currentOwner)) {
            return true;
        }
        DimensionState state = states.remove(currentOwner);
        states.put(targetOwner, state == null ? DimensionState.EMPTY : state);
        setDirty();
        return true;
    }

    public boolean canBindInstance(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (dimensionKey == null || dimensionKey.isBlank() || instanceKey == null) {
            return false;
        }
        OwnerKey currentOwner = ownerForDimension(dimensionKey);
        if (currentOwner.instanceKey() == null || instanceKey.equals(currentOwner.instanceKey())) {
            return true;
        }
        DimensionState state = states.getOrDefault(currentOwner, DimensionState.EMPTY);
        return state.lastGeneratedPlacement() == null
            && state.cleanupState() == null
            && state.generationFailure() == null;
    }

    @Nullable
    public LastGeneratedPlacement getLastGeneratedPlacement(String dimensionKey) {
        return getLastGeneratedPlacement(ownerForDimension(dimensionKey));
    }

    @Nullable
    public LastGeneratedPlacement getLastGeneratedPlacement(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return getLastGeneratedPlacement(new OwnerKey(dimensionKey, instanceKey));
    }

    @Nullable
    private LastGeneratedPlacement getLastGeneratedPlacement(OwnerKey owner) {
        DimensionState state = states.get(owner);
        return state == null ? null : state.lastGeneratedPlacement();
    }

    public void setLastGeneratedPlacement(String dimensionKey, LastGeneratedPlacement placement) {
        setLastGeneratedPlacement(ownerForDimension(dimensionKey), placement);
    }

    public void setLastGeneratedPlacement(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        LastGeneratedPlacement placement
    ) {
        setLastGeneratedPlacement(new OwnerKey(dimensionKey, instanceKey), placement);
    }

    private void setLastGeneratedPlacement(OwnerKey owner, LastGeneratedPlacement placement) {
        DimensionState current = states.getOrDefault(owner, DimensionState.EMPTY);
        states.put(owner, new DimensionState(
            placement, current.hubGateState(), current.cleanupState(), current.generationFailure(),
            withoutIssueOwner(current.invalidData(), "LastGeneratedPlacement")
        ));
        setDirty();
    }

    @Nullable
    public LastGeneratedPlacement removeLastGeneratedPlacement(String dimensionKey) {
        return removeLastGeneratedPlacement(ownerForDimension(dimensionKey));
    }

    @Nullable
    public LastGeneratedPlacement removeLastGeneratedPlacement(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return removeLastGeneratedPlacement(new OwnerKey(dimensionKey, instanceKey));
    }

    @Nullable
    private LastGeneratedPlacement removeLastGeneratedPlacement(OwnerKey owner) {
        DimensionState current = states.get(owner);
        if (current == null || current.lastGeneratedPlacement() == null) {
            return null;
        }
        LastGeneratedPlacement removed = current.lastGeneratedPlacement();
        storeDimensionState(owner, new DimensionState(
            null, current.hubGateState(), current.cleanupState(), current.generationFailure(), current.invalidData()
        ));
        return removed;
    }

    @Nullable
    public HubGateRuntimeState getHubGateState(String dimensionKey) {
        return getHubGateState(ownerForDimension(dimensionKey));
    }

    @Nullable
    public HubGateRuntimeState getHubGateState(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return getHubGateState(new OwnerKey(dimensionKey, instanceKey));
    }

    @Nullable
    private HubGateRuntimeState getHubGateState(OwnerKey owner) {
        DimensionState state = states.get(owner);
        return state == null ? null : state.hubGateState();
    }

    public void setHubGateState(String dimensionKey, HubGateRuntimeState gateState) {
        setHubGateState(ownerForDimension(dimensionKey), gateState);
    }

    public void setHubGateState(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        HubGateRuntimeState gateState
    ) {
        setHubGateState(new OwnerKey(dimensionKey, instanceKey), gateState);
    }

    private void setHubGateState(OwnerKey owner, HubGateRuntimeState gateState) {
        DimensionState current = states.getOrDefault(owner, DimensionState.EMPTY);
        states.put(owner, new DimensionState(
            current.lastGeneratedPlacement(), gateState, current.cleanupState(), current.generationFailure(),
            withoutIssueOwner(current.invalidData(), "HubGateState")
        ));
        setDirty();
    }

    public void clearHubGateState(String dimensionKey) {
        clearHubGateState(ownerForDimension(dimensionKey));
    }

    public void clearHubGateState(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        clearHubGateState(new OwnerKey(dimensionKey, instanceKey));
    }

    private void clearHubGateState(OwnerKey owner) {
        DimensionState current = states.get(owner);
        if (current == null || current.hubGateState() == null) {
            return;
        }
        storeDimensionState(owner, new DimensionState(
            current.lastGeneratedPlacement(), null, current.cleanupState(), current.generationFailure(), current.invalidData()
        ));
    }

    @Nullable
    public CleanupState getCleanupState(String dimensionKey) {
        return getCleanupState(ownerForDimension(dimensionKey));
    }

    @Nullable
    public CleanupState getCleanupState(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return getCleanupState(new OwnerKey(dimensionKey, instanceKey));
    }

    @Nullable
    private CleanupState getCleanupState(OwnerKey owner) {
        DimensionState state = states.get(owner);
        return state == null ? null : state.cleanupState();
    }

    public void setCleanupState(String dimensionKey, CleanupState cleanupState) {
        setCleanupState(ownerForDimension(dimensionKey), cleanupState);
    }

    public void setCleanupState(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        CleanupState cleanupState
    ) {
        setCleanupState(new OwnerKey(dimensionKey, instanceKey), cleanupState);
    }

    private void setCleanupState(OwnerKey owner, CleanupState cleanupState) {
        DimensionState current = states.getOrDefault(owner, DimensionState.EMPTY);
        states.put(owner, new DimensionState(
            current.lastGeneratedPlacement(), current.hubGateState(), cleanupState, current.generationFailure(),
            withoutIssueOwner(current.invalidData(), "CleanupState")
        ));
        setDirty();
    }

    @Nullable
    public CleanupState removeCleanupState(String dimensionKey) {
        return removeCleanupState(ownerForDimension(dimensionKey));
    }

    @Nullable
    public CleanupState removeCleanupState(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return removeCleanupState(new OwnerKey(dimensionKey, instanceKey));
    }

    @Nullable
    private CleanupState removeCleanupState(OwnerKey owner) {
        DimensionState current = states.get(owner);
        if (current == null || current.cleanupState() == null) {
            return null;
        }
        CleanupState removed = current.cleanupState();
        storeDimensionState(owner, new DimensionState(
            current.lastGeneratedPlacement(), current.hubGateState(), null, current.generationFailure(), current.invalidData()
        ));
        return removed;
    }

    @Nullable
    public GenerationFailureState getGenerationFailure(String dimensionKey) {
        return getGenerationFailure(ownerForDimension(dimensionKey));
    }

    @Nullable
    public GenerationFailureState getGenerationFailure(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return getGenerationFailure(new OwnerKey(dimensionKey, instanceKey));
    }

    @Nullable
    private GenerationFailureState getGenerationFailure(OwnerKey owner) {
        DimensionState state = states.get(owner);
        return state == null ? null : state.generationFailure();
    }

    public void setGenerationFailure(String dimensionKey, GenerationFailureState failure) {
        setGenerationFailure(ownerForDimension(dimensionKey), failure);
    }

    public void setGenerationFailure(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        GenerationFailureState failure
    ) {
        setGenerationFailure(new OwnerKey(dimensionKey, instanceKey), failure);
    }

    private void setGenerationFailure(OwnerKey owner, GenerationFailureState failure) {
        DimensionState current = states.getOrDefault(owner, DimensionState.EMPTY);
        states.put(owner, new DimensionState(
            current.lastGeneratedPlacement(), current.hubGateState(), current.cleanupState(), failure, current.invalidData()
        ));
        setDirty();
    }

    @Nullable
    public GenerationFailureState clearGenerationFailure(String dimensionKey) {
        return clearGenerationFailure(ownerForDimension(dimensionKey));
    }

    @Nullable
    public GenerationFailureState clearGenerationFailure(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return clearGenerationFailure(new OwnerKey(dimensionKey, instanceKey));
    }

    @Nullable
    private GenerationFailureState clearGenerationFailure(OwnerKey owner) {
        DimensionState current = states.get(owner);
        if (current == null || current.generationFailure() == null) return null;
        GenerationFailureState removed = current.generationFailure();
        storeDimensionState(owner, new DimensionState(
            current.lastGeneratedPlacement(), current.hubGateState(), current.cleanupState(), null, current.invalidData()
        ));
        return removed;
    }

    public List<InvalidGeneratedData> getInvalidGeneratedData(String dimensionKey) {
        DimensionState state = states.get(ownerForDimension(dimensionKey));
        return state == null ? List.of() : state.invalidData();
    }

    private void storeDimensionState(OwnerKey owner, DimensionState state) {
        if (state.isEmpty()) {
            if (owner.instanceKey() == null) {
                states.remove(owner);
            } else {
                states.put(owner, DimensionState.EMPTY);
            }
        } else {
            states.put(owner, state);
        }
        setDirty();
    }

    private OwnerKey ownerForDimension(String dimensionKey) {
        if (dimensionKey == null || dimensionKey.isBlank()) {
            throw new IllegalArgumentException("Dungeon generated runtime requires a dimension");
        }
        List<OwnerKey> owners = states.keySet().stream()
            .filter(owner -> owner.dimensionKey().equals(dimensionKey))
            .toList();
        if (owners.size() > 1) {
            throw new IllegalStateException(
                "Multiple generated-floor runtime owners require explicit instance identity: "
                    + dimensionKey
            );
        }
        return owners.isEmpty() ? new OwnerKey(dimensionKey, null) : owners.getFirst();
    }

    private record OwnerKey(
        String dimensionKey,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey
    ) implements Comparable<OwnerKey> {
        private OwnerKey {
            if (dimensionKey == null || dimensionKey.isBlank()) {
                throw new IllegalArgumentException("Dungeon generated runtime requires a dimension");
            }
        }

        private String describe() {
            return dimensionKey + "/"
                + (instanceKey == null ? "LEGACY_UNBOUND" : instanceKey.instanceId() + "@" + instanceKey.epoch());
        }

        @Override
        public int compareTo(OwnerKey other) {
            return describe().compareTo(other.describe());
        }
    }

    @Nullable
    private static LastGeneratedPlacement loadLastGeneratedPlacement(
        CompoundTag tag,
        List<InvalidGeneratedData> invalidData
    ) {
        BlockPos floorBuildOrigin = readRequiredBlockPos(
            tag, "FloorBuildOrigin", "LastGeneratedPlacement", invalidData
        );
        if (floorBuildOrigin == null) return null;
        List<RoomControllerBlockEntity.RoomBounds> bounds = new ArrayList<>();
        ListTag boundsTag = tag.getList("Bounds", Tag.TAG_COMPOUND);
        for (int i = 0; i < boundsTag.size(); i++) {
            RoomControllerBlockEntity.RoomBounds boundsEntry = loadBounds(boundsTag.getCompound(i));
            if (boundsEntry != null) {
                bounds.add(boundsEntry);
            }
        }
        List<BlockPos> controllers = loadPositions(tag.getList("Controllers", Tag.TAG_COMPOUND));
        List<GeneratedRoomRuntimePlan> roomPlans = new ArrayList<>();
        ListTag roomPlansTag = tag.getList("RoomPlans", Tag.TAG_COMPOUND);
        for (int i = 0; i < roomPlansTag.size(); i++) {
            GeneratedRoomRuntimePlan roomPlan = loadGeneratedRoomRuntimePlan(
                roomPlansTag.getCompound(i), i + 1, invalidData
            );
            if (roomPlan != null) {
                roomPlans.add(roomPlan);
            }
        }
        int claimRadius = tag.contains("ClaimRadius") ? tag.getInt("ClaimRadius") : 120;
        FrontierState frontier = tag.contains("Frontier", Tag.TAG_COMPOUND)
            ? loadFrontierState(tag.getCompound("Frontier"), invalidData) : null;
        List<BlockPos> retainedRoute = loadPositions(tag.getList("RetainedRoute", Tag.TAG_COMPOUND));
        PruneState pruneState = tag.contains("PruneState", Tag.TAG_COMPOUND) ? loadPruneState(tag.getCompound("PruneState")) : null;
        return new LastGeneratedPlacement(
            floorBuildOrigin, List.copyOf(bounds), List.copyOf(controllers), List.copyOf(roomPlans), claimRadius,
            frontier, List.copyOf(retainedRoute), pruneState,
            tag.contains("VisualTheme") ? tag.getString("VisualTheme") : ""
        );
    }

    private static CompoundTag saveLastGeneratedPlacement(LastGeneratedPlacement placement) {
        CompoundTag tag = new CompoundTag();
        tag.put("FloorBuildOrigin", NbtUtils.writeBlockPos(placement.floorBuildOrigin()));
        ListTag boundsTag = new ListTag();
        for (RoomControllerBlockEntity.RoomBounds bounds : placement.bounds()) {
            boundsTag.add(saveBounds(bounds));
        }
        tag.put("Bounds", boundsTag);
        tag.put("Controllers", savePositions(placement.controllers()));
        ListTag roomPlansTag = new ListTag();
        for (GeneratedRoomRuntimePlan roomPlan : placement.roomPlans()) {
            roomPlansTag.add(saveGeneratedRoomRuntimePlan(roomPlan));
        }
        tag.put("RoomPlans", roomPlansTag);
        tag.putInt("ClaimRadius", placement.claimRadius());
        if (!placement.visualTheme().isBlank()) tag.putString("VisualTheme", placement.visualTheme());
        if (placement.frontier() != null) tag.put("Frontier", saveFrontierState(placement.frontier()));
        tag.put("RetainedRoute", savePositions(placement.retainedRoute()));
        if (placement.pruneState() != null) tag.put("PruneState", savePruneState(placement.pruneState()));
        return tag;
    }

    @Nullable
    private static FrontierState loadFrontierState(CompoundTag tag, List<InvalidGeneratedData> invalidData) {
        BlockPos source = readRequiredBlockPos(tag, "SourceController", "Frontier", invalidData);
        if (source == null) return null;
        List<FrontierCandidate> candidates = new ArrayList<>();
        ListTag list = tag.getList("Candidates", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            GeneratedRoomRuntimePlan plan = loadGeneratedRoomRuntimePlan(
                entry.getCompound("Plan"), i + 1, invalidData
            );
            List<GeneratedRoomRuntimePlan> branchPlans = new ArrayList<>();
            ListTag branchPlansTag = entry.getList("BranchPlans", Tag.TAG_COMPOUND);
            for (int j = 0; j < branchPlansTag.size(); j++) {
                GeneratedRoomRuntimePlan branchPlan = loadGeneratedRoomRuntimePlan(
                    branchPlansTag.getCompound(j), j + 1, invalidData
                );
                if (branchPlan != null) branchPlans.add(branchPlan);
            }
            RoomControllerBlockEntity.RoomBounds bounds = entry.contains("Bounds", Tag.TAG_COMPOUND) ? loadBounds(entry.getCompound("Bounds")) : null;
            BlockPos exit = readOptionalBlockPos(entry, "SourceExit");
            if (plan != null && bounds != null && exit != null) {
                if (branchPlans.isEmpty()) branchPlans.add(plan);
                candidates.add(new FrontierCandidate(
                    exit, entry.getString("ExitFacing"), plan, bounds, List.copyOf(branchPlans)
                ));
            }
        }
        return candidates.isEmpty() ? null : new FrontierState(source, List.copyOf(candidates));
    }

    private static CompoundTag saveFrontierState(FrontierState state) {
        CompoundTag tag = new CompoundTag();
        tag.put("SourceController", NbtUtils.writeBlockPos(state.sourceController()));
        ListTag list = new ListTag();
        for (FrontierCandidate candidate : state.candidates()) {
            CompoundTag entry = new CompoundTag();
            entry.put("SourceExit", NbtUtils.writeBlockPos(candidate.sourceExit()));
            entry.putString("ExitFacing", candidate.exitFacing());
            entry.put("Plan", saveGeneratedRoomRuntimePlan(candidate.plan()));
            entry.put("Bounds", saveBounds(candidate.bounds()));
            if (candidate.branchPlans().size() > 1) {
                ListTag branchPlans = new ListTag();
                for (GeneratedRoomRuntimePlan branchPlan : candidate.branchPlans()) {
                    branchPlans.add(saveGeneratedRoomRuntimePlan(branchPlan));
                }
                entry.put("BranchPlans", branchPlans);
            }
            list.add(entry);
        }
        tag.put("Candidates", list);
        return tag;
    }

    private static PruneState loadPruneState(CompoundTag tag) {
        List<RoomControllerBlockEntity.RoomBounds> bounds = new ArrayList<>();
        ListTag list = tag.getList("Bounds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            RoomControllerBlockEntity.RoomBounds entry = loadBounds(list.getCompound(i));
            if (entry != null) bounds.add(entry);
        }
        return new PruneState(List.copyOf(bounds), tag.getInt("BoundsIndex"), tag.getLong("BoundsOffset"));
    }

    private static CompoundTag savePruneState(PruneState state) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (RoomControllerBlockEntity.RoomBounds bounds : state.bounds()) list.add(saveBounds(bounds));
        tag.put("Bounds", list);
        tag.putInt("BoundsIndex", state.boundsIndex());
        tag.putLong("BoundsOffset", state.boundsOffset());
        return tag;
    }

    @Nullable
    private static GeneratedRoomRuntimePlan loadGeneratedRoomRuntimePlan(
        CompoundTag tag,
        int fallbackIndex,
        List<InvalidGeneratedData> invalidData
    ) {
        BlockPos controllerPos = readOptionalBlockPos(tag, "ControllerPos");
        if (controllerPos == null) {
            if (tag.contains("ControllerPos")) {
                addInvalidData(invalidData, "RoomPlan[" + fallbackIndex + "]", "ControllerPos", tag);
            }
            return null;
        }
        BlockPos placementOrigin = readOptionalBlockPos(tag, "PlacementOrigin");
        if (placementOrigin == null && tag.contains("PlacementOrigin")) {
            addInvalidData(invalidData, "RoomPlan[" + fallbackIndex + "]", "PlacementOrigin", tag);
        }
        RoomControllerBlockEntity.RoomBounds bounds = tag.contains("Bounds", Tag.TAG_COMPOUND) ? loadBounds(tag.getCompound("Bounds")) : null;
        return new GeneratedRoomRuntimePlan(
            tag.contains("Index") ? tag.getInt("Index") : fallbackIndex,
            controllerPos,
            tag.contains("TemplateLabel") ? tag.getString("TemplateLabel") : "unknown",
            tag.contains("SafeEligible") && tag.getBoolean("SafeEligible"),
            tag.contains("SafeRolled") && tag.getBoolean("SafeRolled"),
            tag.contains("Rotation") ? tag.getString("Rotation") : "NONE",
            placementOrigin,
            bounds,
            readOptionalBlockPos(tag, "IncomingConnectorPos"),
            tag.contains("IncomingConnectorFacing") ? tag.getString("IncomingConnectorFacing") : "NONE",
            readOptionalBlockPos(tag, "OutgoingConnectorPos"),
            tag.contains("OutgoingConnectorFacing") ? tag.getString("OutgoingConnectorFacing") : "NONE",
            tag.contains("OutgoingConnectorCount") ? tag.getInt("OutgoingConnectorCount") : 0,
            tag.contains("GameplayRole") ? tag.getString("GameplayRole") : (tag.getBoolean("SafeRolled") ? "SAFE" : "COMBAT"),
            tag.contains("EffectiveProfile") ? tag.getString("EffectiveProfile") : (tag.getBoolean("SafeRolled") ? "SAFE_ROOM" : "STANDARD_COMBAT"),
            tag.contains("ObjectiveType") ? tag.getString("ObjectiveType") : "NONE"
        );
    }

    private static CompoundTag saveGeneratedRoomRuntimePlan(GeneratedRoomRuntimePlan roomPlan) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Index", roomPlan.index());
        putOptionalBlockPos(tag, "ControllerPos", roomPlan.controllerPos());
        tag.putString("TemplateLabel", roomPlan.templateLabel());
        tag.putBoolean("SafeEligible", roomPlan.safeEligible());
        tag.putBoolean("SafeRolled", roomPlan.safeRolled());
        tag.putString("Rotation", roomPlan.rotation());
        putOptionalBlockPos(tag, "PlacementOrigin", roomPlan.placementOrigin());
        if (roomPlan.bounds() != null) {
            tag.put("Bounds", saveBounds(roomPlan.bounds()));
        }
        putOptionalBlockPos(tag, "IncomingConnectorPos", roomPlan.incomingConnectorPos());
        tag.putString("IncomingConnectorFacing", roomPlan.incomingConnectorFacing());
        putOptionalBlockPos(tag, "OutgoingConnectorPos", roomPlan.outgoingConnectorPos());
        tag.putString("OutgoingConnectorFacing", roomPlan.outgoingConnectorFacing());
        tag.putInt("OutgoingConnectorCount", roomPlan.outgoingConnectorCount());
        tag.putString("GameplayRole", roomPlan.gameplayRole());
        tag.putString("EffectiveProfile", roomPlan.effectiveProfile());
        tag.putString("ObjectiveType", roomPlan.objectiveType());
        return tag;
    }
    @Nullable
    private static HubGateRuntimeState loadHubGateRuntimeState(
        CompoundTag tag,
        List<InvalidGeneratedData> invalidData
    ) {
        BlockPos floorBuildOrigin = readRequiredBlockPos(tag, "FloorBuildOrigin", "HubGateState", invalidData);
        if (floorBuildOrigin == null) return null;
        BlockPos anchorA = readOptionalBlockPos(tag, "AnchorA");
        BlockPos anchorB = readOptionalBlockPos(tag, "AnchorB");
        RoomControllerBlockEntity.RoomBounds bounds = tag.contains("Bounds", Tag.TAG_COMPOUND) ? loadBounds(tag.getCompound("Bounds")) : null;
        List<BlockPos> ownedPositions = loadPositions(tag.getList("OwnedPositions", Tag.TAG_COMPOUND));
        List<VisualBarrierHandle> visualHandles = new ArrayList<>();
        if (tag.contains("VisualBarriers", Tag.TAG_LIST)) {
            ListTag visualBarriersTag = tag.getList("VisualBarriers", Tag.TAG_COMPOUND);
            for (int i = 0; i < visualBarriersTag.size(); i++) {
                CompoundTag handleTag = visualBarriersTag.getCompound(i);
                VisualBarrierHandle handle = VisualBarrierHandle.parse(handleTag.getString("Backend"), handleTag.getInt("Id"));
                if (handle != null) visualHandles.add(handle);
            }
        } else if (tag.contains("VisualBackend") && tag.contains("VisualId")) {
            VisualBarrierHandle handle = VisualBarrierHandle.parse(tag.getString("VisualBackend"), tag.getInt("VisualId"));
            if (handle != null) visualHandles.add(handle);
        }
        return new HubGateRuntimeState(
            floorBuildOrigin,
            anchorA,
            anchorB,
            bounds,
            List.copyOf(ownedPositions),
            List.copyOf(visualHandles),
            tag.contains("LastEligibleAirCount") ? tag.getInt("LastEligibleAirCount") : 0,
            tag.contains("LastPlacedCount") ? tag.getInt("LastPlacedCount") : 0,
            tag.contains("LastRemovedCount") ? tag.getInt("LastRemovedCount") : 0,
            tag.contains("LastSkippedNonAirCount") ? tag.getInt("LastSkippedNonAirCount") : 0,
            tag.contains("LastOperation") ? tag.getString("LastOperation") : "NONE"
        );
    }

    private static CompoundTag saveHubGateRuntimeState(HubGateRuntimeState state) {
        CompoundTag tag = new CompoundTag();
        tag.put("FloorBuildOrigin", NbtUtils.writeBlockPos(state.floorBuildOrigin()));
        putOptionalBlockPos(tag, "AnchorA", state.anchorA());
        putOptionalBlockPos(tag, "AnchorB", state.anchorB());
        if (state.bounds() != null) {
            tag.put("Bounds", saveBounds(state.bounds()));
        }
        tag.put("OwnedPositions", savePositions(state.ownedPositions()));
        ListTag visualBarriersTag = new ListTag();
        for (VisualBarrierHandle handle : state.visualHandles()) {
            CompoundTag handleTag = new CompoundTag();
            handleTag.putString("Backend", handle.backend().name());
            handleTag.putInt("Id", handle.id());
            visualBarriersTag.add(handleTag);
        }
        tag.put("VisualBarriers", visualBarriersTag);
        tag.putInt("LastEligibleAirCount", state.lastEligibleAirCount());
        tag.putInt("LastPlacedCount", state.lastPlacedCount());
        tag.putInt("LastRemovedCount", state.lastRemovedCount());
        tag.putInt("LastSkippedNonAirCount", state.lastSkippedNonAirCount());
        tag.putString("LastOperation", state.lastOperation());
        return tag;
    }

    @Nullable
    private static CleanupState loadCleanupState(CompoundTag tag, List<InvalidGeneratedData> invalidData) {
        BlockPos floorBuildOrigin = readRequiredBlockPos(tag, "FloorBuildOrigin", "CleanupState", invalidData);
        if (floorBuildOrigin == null) return null;
        List<RoomControllerBlockEntity.RoomBounds> bounds = new ArrayList<>();
        ListTag boundsTag = tag.getList("Bounds", Tag.TAG_COMPOUND);
        for (int i = 0; i < boundsTag.size(); i++) {
            RoomControllerBlockEntity.RoomBounds boundsEntry = loadBounds(boundsTag.getCompound(i));
            if (boundsEntry != null) {
                bounds.add(boundsEntry);
            }
        }
        List<RoomControllerBlockEntity.RoomBounds> liquidSweepBounds = new ArrayList<>();
        ListTag liquidSweepTag = tag.getList("LiquidSweepBounds", Tag.TAG_COMPOUND);
        for (int i = 0; i < liquidSweepTag.size(); i++) {
            RoomControllerBlockEntity.RoomBounds sweepEntry = loadBounds(liquidSweepTag.getCompound(i));
            if (sweepEntry != null) {
                liquidSweepBounds.add(sweepEntry);
            }
        }
        return new CleanupState(
            floorBuildOrigin,
            List.copyOf(bounds),
            tag.contains("CurrentBoundsIndex") ? tag.getInt("CurrentBoundsIndex") : 0,
            tag.contains("CurrentBoundsOffset") ? tag.getLong("CurrentBoundsOffset") : 0L,
            tag.contains("VisitedPositions") ? tag.getLong("VisitedPositions") : 0L,
            tag.contains("ClearedBlocks") ? tag.getLong("ClearedBlocks") : 0L,
            tag.contains("StartedGameTime") ? tag.getLong("StartedGameTime") : 0L,
            tag.contains("LiquidsObserved") && tag.getBoolean("LiquidsObserved"),
            tag.contains("ConfirmPasses") ? tag.getInt("ConfirmPasses") : 0,
            List.copyOf(liquidSweepBounds),
            tag.contains("LiquidSweepBoundsIndex") ? tag.getInt("LiquidSweepBoundsIndex") : 0,
            tag.contains("LiquidSweepOffset") ? tag.getLong("LiquidSweepOffset") : 0L,
            tag.contains("LiquidFrontier") ? tag.getLongArray("LiquidFrontier") : new long[0],
            tag.contains("LiquidFrontierIndex") ? tag.getInt("LiquidFrontierIndex") : 0
        );
    }

    private static CompoundTag saveCleanupState(CleanupState state) {
        CompoundTag tag = new CompoundTag();
        tag.put("FloorBuildOrigin", NbtUtils.writeBlockPos(state.floorBuildOrigin()));
        ListTag boundsTag = new ListTag();
        for (RoomControllerBlockEntity.RoomBounds bounds : state.bounds()) {
            boundsTag.add(saveBounds(bounds));
        }
        tag.put("Bounds", boundsTag);
        tag.putInt("CurrentBoundsIndex", state.currentBoundsIndex());
        tag.putLong("CurrentBoundsOffset", state.currentBoundsOffset());
        tag.putLong("VisitedPositions", state.visitedPositions());
        tag.putLong("ClearedBlocks", state.clearedBlocks());
        tag.putLong("StartedGameTime", state.startedGameTime());
        tag.putBoolean("LiquidsObserved", state.liquidsObserved());
        tag.putInt("ConfirmPasses", state.confirmPasses());
        ListTag liquidSweepTag = new ListTag();
        for (RoomControllerBlockEntity.RoomBounds sweep : state.liquidSweepBounds()) {
            liquidSweepTag.add(saveBounds(sweep));
        }
        tag.put("LiquidSweepBounds", liquidSweepTag);
        tag.putInt("LiquidSweepBoundsIndex", state.liquidSweepBoundsIndex());
        tag.putLong("LiquidSweepOffset", state.liquidSweepOffset());
        tag.putLongArray("LiquidFrontier", state.liquidFrontier());
        tag.putInt("LiquidFrontierIndex", state.liquidFrontierIndex());
        return tag;
    }

    private static GenerationFailureState loadGenerationFailureState(CompoundTag tag) {
        return new GenerationFailureState(
            tag.getString("Code"),
            !tag.contains("Retryable") || tag.getBoolean("Retryable"),
            tag.contains("Attempts") ? tag.getInt("Attempts") : 1,
            tag.contains("FirstGameTime") ? tag.getLong("FirstGameTime") : 0L,
            tag.contains("LastGameTime") ? tag.getLong("LastGameTime") : 0L,
            readOptionalBlockPos(tag, "ControllerPos"),
            readOptionalBlockPos(tag, "ConnectorPos"),
            tag.contains("Detail") ? tag.getString("Detail") : "NONE"
        );
    }

    private static CompoundTag saveGenerationFailureState(GenerationFailureState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Code", state.code());
        tag.putBoolean("Retryable", state.retryable());
        tag.putInt("Attempts", state.attempts());
        tag.putLong("FirstGameTime", state.firstGameTime());
        tag.putLong("LastGameTime", state.lastGameTime());
        putOptionalBlockPos(tag, "ControllerPos", state.controllerPos());
        putOptionalBlockPos(tag, "ConnectorPos", state.connectorPos());
        tag.putString("Detail", state.detail());
        return tag;
    }

    private static List<InvalidGeneratedData> loadInvalidGeneratedData(CompoundTag dimensionTag) {
        List<InvalidGeneratedData> invalidData = new ArrayList<>();
        ListTag list = dimensionTag.getList("InvalidGeneratedData", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains("Owner") || !entry.contains("Key") || !entry.contains("Payload", Tag.TAG_COMPOUND)) {
                continue;
            }
            invalidData.add(new InvalidGeneratedData(
                entry.getString("Owner"), entry.getString("Key"),
                entry.contains("Reason") ? entry.getString("Reason") : "MALFORMED_REQUIRED_BLOCK_POS",
                entry.getCompound("Payload")
            ));
        }
        return List.copyOf(invalidData);
    }

    private static ListTag saveInvalidGeneratedData(List<InvalidGeneratedData> invalidData) {
        ListTag list = new ListTag();
        for (InvalidGeneratedData issue : invalidData) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Owner", issue.owner());
            entry.putString("Key", issue.key());
            entry.putString("Reason", issue.reason());
            entry.put("Payload", issue.payload());
            list.add(entry);
        }
        return list;
    }

    @Nullable
    private static RoomControllerBlockEntity.RoomBounds loadBounds(CompoundTag tag) {
        BlockPos min = readOptionalBlockPos(tag, "Min");
        BlockPos max = readOptionalBlockPos(tag, "Max");
        if (min == null || max == null) {
            return null;
        }
        return new RoomControllerBlockEntity.RoomBounds(min, max);
    }

    private static CompoundTag saveBounds(RoomControllerBlockEntity.RoomBounds bounds) {
        CompoundTag tag = new CompoundTag();
        putOptionalBlockPos(tag, "Min", bounds.min());
        putOptionalBlockPos(tag, "Max", bounds.max());
        return tag;
    }

    private static List<BlockPos> loadPositions(ListTag list) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag posTag = list.getCompound(i);
            BlockPos pos = readOptionalBlockPos(posTag, "Pos");
            if (pos != null) {
                positions.add(pos.immutable());
            }
        }
        return positions;
    }

    private static ListTag savePositions(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            CompoundTag posTag = new CompoundTag();
            putOptionalBlockPos(posTag, "Pos", pos);
            list.add(posTag);
        }
        return list;
    }

    private static void putOptionalBlockPos(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos != null) {
            tag.put(key, NbtUtils.writeBlockPos(pos));
        }
    }

    @Nullable
    private static BlockPos readOptionalBlockPos(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        return NbtUtils.readBlockPos(tag, key).map(BlockPos::immutable).orElse(null);
    }

    @Nullable
    private static BlockPos readRequiredBlockPos(
        CompoundTag tag,
        String key,
        String owner,
        List<InvalidGeneratedData> invalidData
    ) {
        if (!tag.contains(key)) return BlockPos.ZERO;
        BlockPos parsed = NbtUtils.readBlockPos(tag, key).map(BlockPos::immutable).orElse(null);
        if (parsed == null) addInvalidData(invalidData, owner, key, tag);
        return parsed;
    }

    private static void addInvalidData(
        List<InvalidGeneratedData> invalidData,
        String owner,
        String key,
        CompoundTag payload
    ) {
        invalidData.removeIf(issue -> issue.owner().equals(owner) && issue.key().equals(key));
        invalidData.add(new InvalidGeneratedData(owner, key, "MALFORMED_REQUIRED_BLOCK_POS", payload));
    }

    private static List<InvalidGeneratedData> withoutIssueOwner(
        List<InvalidGeneratedData> invalidData,
        String owner
    ) {
        return invalidData.stream().filter(issue -> !issue.owner().equals(owner)).toList();
    }

    private record DimensionState(
        @Nullable LastGeneratedPlacement lastGeneratedPlacement,
        @Nullable HubGateRuntimeState hubGateState,
        @Nullable CleanupState cleanupState,
        @Nullable GenerationFailureState generationFailure,
        List<InvalidGeneratedData> invalidData
    ) {
        private static final DimensionState EMPTY = new DimensionState(null, null, null, null, List.of());

        private boolean isEmpty() {
            return lastGeneratedPlacement == null && hubGateState == null && cleanupState == null
                && generationFailure == null && invalidData.isEmpty();
        }
    }

    public record LastGeneratedPlacement(
        BlockPos floorBuildOrigin,
        List<RoomControllerBlockEntity.RoomBounds> bounds,
        List<BlockPos> controllers,
        List<GeneratedRoomRuntimePlan> roomPlans,
        int claimRadius,
        @Nullable FrontierState frontier,
        List<BlockPos> retainedRoute,
        @Nullable PruneState pruneState,
        String visualTheme
    ) {
        public LastGeneratedPlacement {
            visualTheme = visualTheme == null ? "" : visualTheme.trim().toLowerCase(java.util.Locale.ROOT);
        }

        public LastGeneratedPlacement(
            BlockPos floorBuildOrigin,
            List<RoomControllerBlockEntity.RoomBounds> bounds,
            List<BlockPos> controllers,
            List<GeneratedRoomRuntimePlan> roomPlans,
            int claimRadius,
            @Nullable FrontierState frontier,
            List<BlockPos> retainedRoute,
            @Nullable PruneState pruneState
        ) {
            this(floorBuildOrigin, bounds, controllers, roomPlans, claimRadius, frontier, retainedRoute, pruneState, "");
        }

        public LastGeneratedPlacement(
            BlockPos floorBuildOrigin,
            List<RoomControllerBlockEntity.RoomBounds> bounds,
            List<BlockPos> controllers,
            List<GeneratedRoomRuntimePlan> roomPlans,
            int claimRadius,
            String visualTheme
        ) {
            this(floorBuildOrigin, bounds, controllers, roomPlans, claimRadius, null, List.of(), null, visualTheme);
        }

        public LastGeneratedPlacement(BlockPos floorBuildOrigin, List<RoomControllerBlockEntity.RoomBounds> bounds, List<BlockPos> controllers, List<GeneratedRoomRuntimePlan> roomPlans, int claimRadius) {
            this(floorBuildOrigin, bounds, controllers, roomPlans, claimRadius, null, List.of(), null, "");
        }

        public LastGeneratedPlacement(BlockPos floorBuildOrigin, List<RoomControllerBlockEntity.RoomBounds> bounds, List<BlockPos> controllers, List<GeneratedRoomRuntimePlan> roomPlans) {
            this(floorBuildOrigin, bounds, controllers, roomPlans, 120);
        }

        public LastGeneratedPlacement(BlockPos floorBuildOrigin, List<RoomControllerBlockEntity.RoomBounds> bounds, List<BlockPos> controllers) {
            this(floorBuildOrigin, bounds, controllers, List.of(), 120);
        }

        public LastGeneratedPlacement withRuntime(List<RoomControllerBlockEntity.RoomBounds> newBounds, List<BlockPos> newControllers, List<GeneratedRoomRuntimePlan> newPlans, @Nullable FrontierState newFrontier, List<BlockPos> newRetainedRoute, @Nullable PruneState newPruneState) {
            return new LastGeneratedPlacement(floorBuildOrigin, newBounds, newControllers, newPlans, claimRadius, newFrontier, newRetainedRoute, newPruneState, visualTheme);
        }

        public String floorBuildOriginValidation(int recoveryRadius) {
            return validateOriginAgainstBounds(floorBuildOrigin, bounds, recoveryRadius, "STORED_PLACEMENT");
        }

        public boolean ownsFloorBuildOrigin() {
            return bounds != null && bounds.stream().anyMatch(candidate -> candidate != null && candidate.contains(floorBuildOrigin));
        }
    }

    public record FrontierState(BlockPos sourceController, List<FrontierCandidate> candidates) {
    }

    public record FrontierCandidate(
        BlockPos sourceExit,
        String exitFacing,
        GeneratedRoomRuntimePlan plan,
        RoomControllerBlockEntity.RoomBounds bounds,
        List<GeneratedRoomRuntimePlan> branchPlans
    ) {
        public FrontierCandidate {
            branchPlans = branchPlans == null || branchPlans.isEmpty() ? List.of(plan) : List.copyOf(branchPlans);
        }

        public FrontierCandidate(
            BlockPos sourceExit,
            String exitFacing,
            GeneratedRoomRuntimePlan plan,
            RoomControllerBlockEntity.RoomBounds bounds
        ) {
            this(sourceExit, exitFacing, plan, bounds, List.of(plan));
        }
    }

    public record PruneState(List<RoomControllerBlockEntity.RoomBounds> bounds, int boundsIndex, long boundsOffset) {
        public boolean complete() {
            return boundsIndex >= bounds.size();
        }

        public PruneState advance(int index, long offset) {
            return new PruneState(bounds, index, offset);
        }
    }

    public record GeneratedRoomRuntimePlan(
        int index,
        BlockPos controllerPos,
        String templateLabel,
        boolean safeEligible,
        boolean safeRolled,
        String rotation,
        @Nullable BlockPos placementOrigin,
        @Nullable RoomControllerBlockEntity.RoomBounds bounds,
        @Nullable BlockPos incomingConnectorPos,
        String incomingConnectorFacing,
        @Nullable BlockPos outgoingConnectorPos,
        String outgoingConnectorFacing,
        int outgoingConnectorCount,
        String gameplayRole,
        String effectiveProfile,
        String objectiveType
    ) {
        public GeneratedRoomRuntimePlan {
            gameplayRole = gameplayRole == null || gameplayRole.isBlank() ? "COMBAT" : gameplayRole;
            effectiveProfile = effectiveProfile == null || effectiveProfile.isBlank() ? "STANDARD_COMBAT" : effectiveProfile;
            objectiveType = objectiveType == null || objectiveType.isBlank() ? "NONE" : objectiveType;
        }
        public GeneratedRoomRuntimePlan(BlockPos controllerPos, String templateLabel, boolean safeEligible, boolean safeRolled) {
            this(0, controllerPos, templateLabel, safeEligible, safeRolled, "NONE", null, null, null, "NONE", null, "NONE", 0, safeRolled ? "SAFE" : "COMBAT", safeRolled ? "SAFE_ROOM" : "STANDARD_COMBAT", "NONE");
        }

        public boolean hasPlacementMetadata() {
            return placementOrigin != null && bounds != null && !templateLabel.isBlank();
        }

        public boolean supportsPlayerDirectedExit() {
            return outgoingConnectorCount > 1
                && incomingConnectorPos != null
                && outgoingConnectorPos != null
                && !"NONE".equals(incomingConnectorFacing)
                && !"NONE".equals(outgoingConnectorFacing);
        }

        public GeneratedRoomRuntimePlan asSafeEntry() {
            if (index != 1 || safeEligible && safeRolled
                && "SAFE_ROOM".equals(effectiveProfile) && "NONE".equals(objectiveType)) {
                return this;
            }
            return new GeneratedRoomRuntimePlan(
                index,
                controllerPos,
                templateLabel,
                true,
                true,
                rotation,
                placementOrigin,
                bounds,
                incomingConnectorPos,
                incomingConnectorFacing,
                outgoingConnectorPos,
                outgoingConnectorFacing,
                outgoingConnectorCount,
                gameplayRole,
                "SAFE_ROOM",
                "NONE"
            );
        }
    }
    public record HubGateRuntimeState(
        BlockPos floorBuildOrigin,
        @Nullable BlockPos anchorA,
        @Nullable BlockPos anchorB,
        @Nullable RoomControllerBlockEntity.RoomBounds bounds,
        List<BlockPos> ownedPositions,
        List<VisualBarrierHandle> visualHandles,
        int lastEligibleAirCount,
        int lastPlacedCount,
        int lastRemovedCount,
        int lastSkippedNonAirCount,
        String lastOperation
    ) {
        public String floorBuildOriginValidation(int recoveryRadius) {
            return validateOriginAgainstBounds(
                floorBuildOrigin, bounds == null ? List.of() : List.of(bounds), recoveryRadius, "STORED_GATE_STATE"
            );
        }
    }

    public record CleanupState(
        BlockPos floorBuildOrigin,
        List<RoomControllerBlockEntity.RoomBounds> bounds,
        int currentBoundsIndex,
        long currentBoundsOffset,
        long visitedPositions,
        long clearedBlocks,
        long startedGameTime,
        boolean liquidsObserved,
        int confirmPasses,
        List<RoomControllerBlockEntity.RoomBounds> liquidSweepBounds,
        int liquidSweepBoundsIndex,
        long liquidSweepOffset,
        long[] liquidFrontier,
        int liquidFrontierIndex
    ) {
        public CleanupState(
            BlockPos floorBuildOrigin,
            List<RoomControllerBlockEntity.RoomBounds> bounds,
            int currentBoundsIndex,
            long currentBoundsOffset,
            long visitedPositions,
            long clearedBlocks,
            long startedGameTime
        ) {
            this(
                floorBuildOrigin,
                bounds,
                currentBoundsIndex,
                currentBoundsOffset,
                visitedPositions,
                clearedBlocks,
                startedGameTime,
                false,
                0,
                List.of(),
                0,
                0L,
                new long[0],
                0
            );
        }

        public CleanupState(
            BlockPos floorBuildOrigin,
            List<RoomControllerBlockEntity.RoomBounds> bounds,
            int currentBoundsIndex,
            long currentBoundsOffset,
            long visitedPositions,
            long clearedBlocks,
            long startedGameTime,
            boolean liquidsObserved,
            int confirmPasses
        ) {
            this(
                floorBuildOrigin,
                bounds,
                currentBoundsIndex,
                currentBoundsOffset,
                visitedPositions,
                clearedBlocks,
                startedGameTime,
                liquidsObserved,
                confirmPasses,
                List.of(),
                0,
                0L,
                new long[0],
                0
            );
        }

        public String floorBuildOriginValidation(int recoveryRadius) {
            return validateOriginAgainstBounds(floorBuildOrigin, bounds, recoveryRadius, "CLEANUP_STATE");
        }

        public boolean ownsFloorBuildOrigin() {
            return bounds != null && bounds.stream().anyMatch(candidate -> candidate != null && candidate.contains(floorBuildOrigin));
        }

        public long totalPositions() {
            long total = 0L;
            for (RoomControllerBlockEntity.RoomBounds boundsEntry : bounds) {
                total += (long) boundsEntry.getWidth() * boundsEntry.getHeight() * boundsEntry.getDepth();
            }
            return total;
        }

        public int progressPercent() {
            long total = totalPositions();
            if (total <= 0L) {
                return 100;
            }
            return (int) Math.min(100L, (visitedPositions * 100L) / total);
        }

        public boolean complete() {
            return mainPassComplete() && liquidSweepComplete();
        }

        public boolean mainPassComplete() {
            return currentBoundsIndex >= bounds.size() || visitedPositions >= totalPositions();
        }

        public boolean liquidSweepComplete() {
            return (liquidSweepBounds == null
                    || liquidSweepBounds.isEmpty()
                    || liquidSweepBoundsIndex >= liquidSweepBounds.size())
                && (liquidFrontier == null
                    || liquidFrontier.length == 0
                    || liquidFrontierIndex >= liquidFrontier.length);
        }

        public CleanupState advance(int nextBoundsIndex, long nextBoundsOffset, long addedVisited, long addedCleared) {
            return advance(
                nextBoundsIndex,
                nextBoundsOffset,
                addedVisited,
                addedCleared,
                false,
                confirmPasses
            );
        }

        public CleanupState advance(
            int nextBoundsIndex,
            long nextBoundsOffset,
            long addedVisited,
            long addedCleared,
            boolean liquidsThisTick,
            int nextConfirmPasses
        ) {
            boolean restartingPass = nextBoundsIndex == 0
                && nextBoundsOffset == 0L
                && nextConfirmPasses > confirmPasses;
            return new CleanupState(
                floorBuildOrigin,
                bounds,
                nextBoundsIndex,
                nextBoundsOffset,
                restartingPass ? addedVisited : visitedPositions + addedVisited,
                clearedBlocks + addedCleared,
                startedGameTime,
                liquidsObserved || liquidsThisTick,
                Math.max(0, nextConfirmPasses),
                liquidSweepBounds,
                liquidSweepBoundsIndex,
                liquidSweepOffset,
                liquidFrontier,
                liquidFrontierIndex
            );
        }

        public CleanupState withLiquidSweepBounds(
            List<RoomControllerBlockEntity.RoomBounds> sweepBounds
        ) {
            return new CleanupState(
                floorBuildOrigin,
                bounds,
                currentBoundsIndex,
                currentBoundsOffset,
                visitedPositions,
                clearedBlocks,
                startedGameTime,
                liquidsObserved,
                confirmPasses,
                sweepBounds == null ? List.of() : List.copyOf(sweepBounds),
                0,
                0L,
                new long[0],
                0
            );
        }

        public CleanupState advanceLiquidSweep(
            int nextBoundsIndex,
            long nextBoundsOffset,
            long addedCleared,
            long[] nextFrontier,
            int nextFrontierIndex
        ) {
            return new CleanupState(
                floorBuildOrigin,
                bounds,
                currentBoundsIndex,
                currentBoundsOffset,
                visitedPositions,
                clearedBlocks + addedCleared,
                startedGameTime,
                liquidsObserved,
                confirmPasses,
                liquidSweepBounds,
                nextBoundsIndex,
                nextBoundsOffset,
                nextFrontier == null ? new long[0] : nextFrontier,
                Math.max(0, nextFrontierIndex)
            );
        }
    }

    private static String validateOriginAgainstBounds(
        BlockPos origin,
        List<RoomControllerBlockEntity.RoomBounds> bounds,
        int recoveryRadius,
        String owner
    ) {
        if (origin == null) return "INVALID_" + owner + "_ORIGIN_MISSING";
        if (bounds == null || bounds.isEmpty()) return "INVALID_" + owner + "_BOUNDS_MISSING";
        long radius = Math.max(0, recoveryRadius);
        long maximumDistanceSquared = radius * radius;
        for (RoomControllerBlockEntity.RoomBounds candidate : bounds) {
            if (candidate == null) continue;
            long dx = axisDistance(origin.getX(), candidate.min().getX(), candidate.max().getX());
            long dy = axisDistance(origin.getY(), candidate.min().getY(), candidate.max().getY());
            long dz = axisDistance(origin.getZ(), candidate.min().getZ(), candidate.max().getZ());
            if (dx * dx + dy * dy + dz * dz <= maximumDistanceSquared) {
                return "VALID_" + owner + "_BOUNDS";
            }
        }
        return "INVALID_" + owner + "_ORIGIN_OUTSIDE_RECOVERY_RADIUS";
    }

    private static long axisDistance(int value, int minimum, int maximum) {
        if (value < minimum) return (long) minimum - value;
        if (value > maximum) return (long) value - maximum;
        return 0L;
    }

    public record GenerationFailureState(
        String code,
        boolean retryable,
        int attempts,
        long firstGameTime,
        long lastGameTime,
        @Nullable BlockPos controllerPos,
        @Nullable BlockPos connectorPos,
        String detail
    ) {
        public GenerationFailureState {
            code = code == null || code.isBlank() ? "UNKNOWN_GENERATION_FAILURE" : code;
            attempts = Math.max(1, attempts);
            firstGameTime = Math.max(0L, firstGameTime);
            lastGameTime = Math.max(firstGameTime, lastGameTime);
            detail = detail == null || detail.isBlank() ? "NONE" : detail;
            controllerPos = controllerPos == null ? null : controllerPos.immutable();
            connectorPos = connectorPos == null ? null : connectorPos.immutable();
        }
    }

    public record InvalidGeneratedData(String owner, String key, String reason, CompoundTag payload) {
        public InvalidGeneratedData {
            owner = owner == null || owner.isBlank() ? "UNKNOWN" : owner;
            key = key == null || key.isBlank() ? "UNKNOWN" : key;
            reason = reason == null || reason.isBlank() ? "MALFORMED_REQUIRED_BLOCK_POS" : reason;
            payload = payload == null ? new CompoundTag() : payload.copy();
        }

        @Override
        public CompoundTag payload() {
            return payload.copy();
        }
    }
}
