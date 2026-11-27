package com.mineclawd.tensuradungeon.runtime.generation;

import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class RollingDungeonCompositionData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_rolling_composition";
    private final Map<OwnerKey, State> states = new HashMap<>();

    public static RollingDungeonCompositionData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(RollingDungeonCompositionData::new, RollingDungeonCompositionData::load), DATA_NAME
        );
    }

    public static RollingDungeonCompositionData load(CompoundTag tag, HolderLookup.Provider registries) {
        RollingDungeonCompositionData data = new RollingDungeonCompositionData();
        ListTag dimensions = tag.getList("Dimensions", Tag.TAG_COMPOUND);
        for (int i = 0; i < dimensions.size(); i++) {
            CompoundTag entry = dimensions.getCompound(i);
            String dimension = entry.getString("Dimension");
            if (dimension.isBlank()) continue;
            DungeonInstanceAllocationData.InstanceKey instanceKey =
                DungeonInstanceAllocationData.InstanceKey.loadOptional(entry);
            List<BeatState> beats = new ArrayList<>();
            ListTag beatTags = entry.getList("Beats", Tag.TAG_COMPOUND);
            for (int j = 0; j < beatTags.size(); j++) {
                CompoundTag beat = beatTags.getCompound(j);
                DungeonRoomRole role = DungeonRoomRole.parse(beat.getString("Role"));
                if (role == null) continue;
                DungeonGameplayComposer.TerminalEncounterRole terminalRole =
                    beat.contains("TerminalEncounterRole", Tag.TAG_STRING)
                        ? DungeonGameplayComposer.TerminalEncounterRole.parse(
                            beat.getString("TerminalEncounterRole")
                        )
                        : DungeonGameplayComposer.TerminalEncounterRole.NONE;
                beats.add(new BeatState(
                    beat.getInt("Index"), role, beat.getInt("Intensity"),
                    beat.getString("Objective"), beat.getString("Purpose"),
                    new DungeonGameplayComposer.TerminalEncounterPlan(
                        terminalRole,
                        beat.contains("TerminalRequiredTheme", Tag.TAG_STRING)
                            ? beat.getString("TerminalRequiredTheme") : "",
                        beat.contains("MilestoneBoss", Tag.TAG_BYTE) && beat.getBoolean("MilestoneBoss")
                    )
                ));
            }
            if (!beats.isEmpty()) {
                int generatorVersion = entry.contains("GeneratorVersion", Tag.TAG_INT)
                    ? entry.getInt("GeneratorVersion") : 0;
                String poolFingerprint = entry.contains("EligibleTemplatePoolFingerprint", Tag.TAG_STRING)
                    ? entry.getString("EligibleTemplatePoolFingerprint") : "";
                OwnerKey owner = new OwnerKey(dimension, instanceKey);
                if (data.states.containsKey(owner)) {
                    throw new IllegalStateException(
                        "Duplicate rolling composition owner in save: " + owner.describe()
                    );
                }
                State state = new State(
                    entry.getInt("Floor"), entry.getInt("RoomCount"), entry.getLong("Seed"),
                    Math.max(0, entry.getInt("Cursor")), generatorVersion, poolFingerprint, List.copyOf(beats)
                );
                data.states.put(owner, state);
            } else if (instanceKey != null) {
                OwnerKey owner = new OwnerKey(dimension, instanceKey);
                if (data.states.containsKey(owner)) {
                    throw new IllegalStateException(
                        "Duplicate rolling composition owner in save: " + owner.describe()
                    );
                }
                data.states.put(owner, null);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag dimensions = new ListTag();
        List<OwnerKey> owners = new ArrayList<>(states.keySet());
        owners.sort(Comparator.naturalOrder());
        for (OwnerKey owner : owners) {
            State state = states.get(owner);
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", owner.dimension());
            if (owner.instanceKey() != null) {
                owner.instanceKey().save(entry);
            }
            if (state != null) {
                entry.putInt("Floor", state.floor());
                entry.putInt("RoomCount", state.roomCount());
                entry.putLong("Seed", state.seed());
                entry.putInt("Cursor", state.cursor());
                if (state.hasGenerationContract()) {
                    entry.putInt("GeneratorVersion", state.generatorVersion());
                    entry.putString("EligibleTemplatePoolFingerprint", state.eligibleTemplatePoolFingerprint());
                }
                ListTag beats = new ListTag();
                for (BeatState beat : state.beats()) {
                    CompoundTag beatTag = new CompoundTag();
                    beatTag.putInt("Index", beat.index());
                    beatTag.putString("Role", beat.role().name());
                    beatTag.putInt("Intensity", beat.intensity());
                    beatTag.putString("Objective", beat.objective());
                    beatTag.putString("Purpose", beat.purpose());
                    if (beat.terminalEncounter().reserved()) {
                        beatTag.putString("TerminalEncounterRole", beat.terminalEncounter().role().name());
                        beatTag.putString("TerminalRequiredTheme", beat.terminalEncounter().requiredTheme());
                        if (beat.terminalEncounter().milestoneBoss()) {
                            beatTag.putBoolean("MilestoneBoss", true);
                        }
                    }
                    beats.add(beatTag);
                }
                entry.put("Beats", beats);
            }
            dimensions.add(entry);
        }
        tag.put("Dimensions", dimensions);
        return tag;
    }

    public void replaceState(String dimension, State state) {
        states.put(ownerForDimension(dimension), state);
        setDirty();
    }

    public void replaceState(
        String dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        State state
    ) {
        states.put(new OwnerKey(dimension, instanceKey), state);
        setDirty();
    }

    @Nullable
    public DungeonInstanceAllocationData.InstanceKey getInstanceKey(String dimension) {
        return ownerForDimension(dimension).instanceKey();
    }

    public List<DungeonInstanceAllocationData.InstanceKey> getInstanceKeys(String dimension) {
        return states.keySet().stream()
            .filter(owner -> owner.dimension().equals(dimension))
            .map(OwnerKey::instanceKey)
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(key -> key.instanceId().toString()))
            .toList();
    }

    public boolean initializeInstance(
        String dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (dimension == null || dimension.isBlank() || instanceKey == null) {
            return false;
        }
        OwnerKey owner = new OwnerKey(dimension, instanceKey);
        if (states.containsKey(owner)) {
            return true;
        }
        states.put(owner, null);
        setDirty();
        return true;
    }

    public boolean canRetireInstance(
        String dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return dimension != null && !dimension.isBlank() && instanceKey != null
            && states.get(new OwnerKey(dimension, instanceKey)) == null;
    }

    public boolean retireInstance(
        String dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (!canRetireInstance(dimension, instanceKey)) {
            return false;
        }
        boolean removed = states.containsKey(new OwnerKey(dimension, instanceKey));
        if (removed) {
            states.remove(new OwnerKey(dimension, instanceKey));
            setDirty();
        }
        return removed;
    }

    public boolean bindInstance(
        String dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (!canBindInstance(dimension, instanceKey)) {
            return false;
        }
        OwnerKey currentOwner = ownerForDimension(dimension);
        OwnerKey targetOwner = new OwnerKey(dimension, instanceKey);
        if (targetOwner.equals(currentOwner)) {
            return true;
        }
        State state = states.remove(currentOwner);
        states.put(targetOwner, state);
        setDirty();
        return true;
    }

    public boolean canBindInstance(
        String dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (dimension == null || dimension.isBlank() || instanceKey == null) {
            return false;
        }
        OwnerKey currentOwner = ownerForDimension(dimension);
        return currentOwner.instanceKey() == null
            || instanceKey.equals(currentOwner.instanceKey())
            || states.get(currentOwner) == null;
    }

    @Nullable
    public State getState(String dimension) {
        return states.get(ownerForDimension(dimension));
    }

    @Nullable
    public State getState(
        String dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return states.get(new OwnerKey(dimension, instanceKey));
    }

    public boolean remove(String dimension) {
        return remove(ownerForDimension(dimension));
    }

    public boolean remove(
        String dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return remove(new OwnerKey(dimension, instanceKey));
    }

    private boolean remove(OwnerKey owner) {
        if (!states.containsKey(owner) || states.get(owner) == null) return false;
        if (owner.instanceKey() == null) {
            states.remove(owner);
        } else {
            states.put(owner, null);
        }
        setDirty();
        return true;
    }

    private OwnerKey ownerForDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("Rolling composition requires a dimension");
        }
        List<OwnerKey> owners = states.keySet().stream()
            .filter(owner -> owner.dimension().equals(dimension))
            .toList();
        if (owners.size() > 1) {
            throw new IllegalStateException(
                "Multiple rolling composition owners require explicit instance identity: " + dimension
            );
        }
        return owners.isEmpty() ? new OwnerKey(dimension, null) : owners.getFirst();
    }

    private record OwnerKey(
        String dimension,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey
    ) implements Comparable<OwnerKey> {
        private OwnerKey {
            if (dimension == null || dimension.isBlank()) {
                throw new IllegalArgumentException("Rolling composition requires a dimension");
            }
        }

        private String describe() {
            return dimension + "/"
                + (instanceKey == null ? "LEGACY_UNBOUND" : instanceKey.instanceId() + "@" + instanceKey.epoch());
        }

        @Override
        public int compareTo(OwnerKey other) {
            return describe().compareTo(other.describe());
        }
    }

    public record State(
        int floor,
        int roomCount,
        long seed,
        int cursor,
        int generatorVersion,
        String eligibleTemplatePoolFingerprint,
        List<BeatState> beats
    ) {
        public State {
            roomCount = Math.max(1, roomCount);
            beats = List.copyOf(beats);
            cursor = Math.min(Math.max(0, cursor), beats.size());
            if (generatorVersion <= 0 || eligibleTemplatePoolFingerprint == null
                || eligibleTemplatePoolFingerprint.isBlank()) {
                generatorVersion = 0;
                eligibleTemplatePoolFingerprint = "";
            }
        }

        public boolean hasGenerationContract() {
            return generatorVersion > 0 && !eligibleTemplatePoolFingerprint.isBlank();
        }

        public boolean matchesGenerationContract(RollingDungeonSpatialSolver.GenerationContract contract) {
            return hasGenerationContract()
                && generatorVersion == contract.generatorVersion()
                && eligibleTemplatePoolFingerprint.equals(contract.eligibleTemplatePoolFingerprint());
        }

        public State bindGenerationContract(RollingDungeonSpatialSolver.GenerationContract contract) {
            return hasGenerationContract() ? this : new State(
                floor, roomCount, seed, cursor, contract.generatorVersion(),
                contract.eligibleTemplatePoolFingerprint(), beats
            );
        }

        public boolean complete() {
            return cursor >= beats.size();
        }

        public static State initialize(
            DungeonGameplayComposer.Composition composition,
            int initiallySolved,
            RollingDungeonSpatialSolver.GenerationContract generationContract
        ) {
            List<BeatState> beats = composition.beats().stream().map(BeatState::from).toList();
            return new State(
                composition.floor(), composition.roomCount(), composition.seed(),
                Math.min(Math.max(0, initiallySolved), beats.size()),
                generationContract.generatorVersion(), generationContract.eligibleTemplatePoolFingerprint(), beats
            );
        }

        @Nullable
        public State advance(int expectedCursor, int newCursor) {
            return cursor == expectedCursor ? withCursor(newCursor) : null;
        }

        @Nullable
        public State deferAndAdvance(int expectedCursor, int deferredOffset, int newCursor) {
            if (cursor != expectedCursor) return null;
            State reordered = deferCurrentBeat(deferredOffset);
            return reordered == null ? null : reordered.withCursor(newCursor);
        }

        public List<BeatState> nextWindow(int size) {
            if (complete() || size <= 0) return List.of();
            return beats.subList(cursor, Math.min(beats.size(), cursor + size));
        }
        public List<DungeonGameplayComposer.Beat> nextBeats(int size) {
            return materialize(nextWindow(size));
        }

        public List<DungeonGameplayComposer.Beat> deferredNextBeats(int offset, int size) {
            State deferred = deferCurrentBeat(offset);
            return deferred == null ? List.of() : deferred.nextBeats(size);
        }

        @Nullable
        private State deferCurrentBeat(int offset) {
            int target = cursor + offset;
            if (offset <= 0 || cursor < 0 || cursor >= beats.size() || target >= beats.size()) return null;
            if (beats.get(target).role() == DungeonRoomRole.FINALE) return null;
            List<BeatState> reordered = new ArrayList<>(beats);
            BeatState blocked = reordered.remove(cursor);
            reordered.add(target, blocked);
            return new State(
                floor, roomCount, seed, cursor, generatorVersion, eligibleTemplatePoolFingerprint,
                List.copyOf(reordered)
            );
        }

        private List<DungeonGameplayComposer.Beat> materialize(List<BeatState> window) {
            List<DungeonGameplayComposer.Beat> result = new ArrayList<>(window.size());
            for (int i = 0; i < window.size(); i++) {
                BeatState beat = window.get(i);
                result.add(new DungeonGameplayComposer.Beat(
                    cursor + i + 1, beat.role(), beat.intensity(), beat.objective(), beat.purpose(),
                    beat.terminalEncounter()
                ));
            }
            return List.copyOf(result);
        }

        private State withCursor(int newCursor) {
            return new State(
                floor, roomCount, seed, newCursor, generatorVersion, eligibleTemplatePoolFingerprint, beats
            );
        }

        public DungeonGameplayComposer.Composition toComposition() {
            List<DungeonGameplayComposer.Beat> restored = beats.stream().map(BeatState::toBeat).toList();
            return new DungeonGameplayComposer.Composition(floor, roomCount, seed, restored, Map.of(), Map.of());
        }
    }

    public record BeatState(
        int index,
        DungeonRoomRole role,
        int intensity,
        String objective,
        String purpose,
        DungeonGameplayComposer.TerminalEncounterPlan terminalEncounter
    ) {
        public BeatState {
            objective = objective == null || objective.isBlank() ? "NONE" : objective;
            purpose = purpose == null ? "" : purpose;
            terminalEncounter = terminalEncounter == null
                ? DungeonGameplayComposer.TerminalEncounterPlan.none() : terminalEncounter;
        }

        public BeatState(int index, DungeonRoomRole role, int intensity, String objective, String purpose) {
            this(
                index, role, intensity, objective, purpose,
                DungeonGameplayComposer.TerminalEncounterPlan.none()
            );
        }

        private static BeatState from(DungeonGameplayComposer.Beat beat) {
            return new BeatState(
                beat.index(), beat.role(), beat.intensity(), beat.objective(), beat.purpose(),
                beat.terminalEncounter()
            );
        }

        private DungeonGameplayComposer.Beat toBeat() {
            return new DungeonGameplayComposer.Beat(
                index, role, intensity, objective, purpose, terminalEncounter
            );
        }
    }
}
