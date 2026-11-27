package com.mineclawd.tensuradungeon.runtime;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonBankData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_bank";
    private static final String PLAYERS_KEY = "Players";
    private static final String PLAYER_KEY = "Player";
    private static final String REVISION_KEY = "Revision";
    private static final String GRADE_KEY = "Grade";
    private static final String BRONZE_KEY = "Bronze";
    private static final String SILVER_KEY = "Silver";
    private static final String GOLD_KEY = "Gold";
    private static final String STELLAR_GOLD_KEY = "StellarGold";

    private final Map<UUID, PlayerState> players = new HashMap<>();

    public static DungeonBankData get(ServerLevel level) {
        Objects.requireNonNull(level, "Dungeon Bank level");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(DungeonBankData::new, DungeonBankData::load),
            DATA_NAME
        );
    }

    public static DungeonBankData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonBankData data = new DungeonBankData();
        ListTag playerTags = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < playerTags.size(); index++) {
            CompoundTag playerTag = playerTags.getCompound(index);
            if (!playerTag.hasUUID(PLAYER_KEY)) {
                throw new IllegalStateException("Dungeon Bank record is missing its player UUID");
            }
            UUID playerId = playerTag.getUUID(PLAYER_KEY);
            DungeonBankRules.Grade grade;
            try {
                grade = DungeonBankRules.Grade.valueOf(playerTag.getString(GRADE_KEY));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Dungeon Bank record has an invalid grade", exception);
            }
            DungeonBankRules.Balance balance = new DungeonBankRules.Balance(
                nonnegative(playerTag.getLong(BRONZE_KEY), BRONZE_KEY),
                nonnegative(playerTag.getLong(SILVER_KEY), SILVER_KEY),
                nonnegative(playerTag.getLong(GOLD_KEY), GOLD_KEY),
                nonnegative(playerTag.getLong(STELLAR_GOLD_KEY), STELLAR_GOLD_KEY)
            );
            if (DungeonBankRules.occupiedSlots(balance) > grade.slots()) {
                throw new IllegalStateException("Dungeon Bank record exceeds its unlocked capacity");
            }
            PlayerState state = new PlayerState(
                Math.max(0L, playerTag.getLong(REVISION_KEY)),
                grade,
                balance
            );
            if (data.players.putIfAbsent(playerId, state) != null) {
                throw new IllegalStateException("Duplicate Dungeon Bank player record: " + playerId);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerTags = new ListTag();
        players.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> {
                Snapshot snapshot = snapshotOf(entry.getValue(), true);
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID(PLAYER_KEY, entry.getKey());
                playerTag.putLong(REVISION_KEY, snapshot.revision());
                playerTag.putString(GRADE_KEY, snapshot.grade().name());
                playerTag.putLong(BRONZE_KEY, snapshot.balance().bronze());
                playerTag.putLong(SILVER_KEY, snapshot.balance().silver());
                playerTag.putLong(GOLD_KEY, snapshot.balance().gold());
                playerTag.putLong(STELLAR_GOLD_KEY, snapshot.balance().stellarGold());
                playerTags.add(playerTag);
            });
        tag.put(PLAYERS_KEY, playerTags);
        return tag;
    }

    public Snapshot snapshot(UUID playerId) {
        Objects.requireNonNull(playerId, "Dungeon Bank player");
        return snapshotOf(
            players.getOrDefault(playerId, PlayerState.defaults()),
            players.containsKey(playerId)
        );
    }

    public MutationResult replaceBalances(
        UUID playerId,
        long observedRevision,
        DungeonBankRules.Balance balance
    ) {
        Objects.requireNonNull(playerId, "Dungeon Bank player");
        Objects.requireNonNull(balance, "Dungeon Bank replacement balance");
        if (observedRevision < 0L) {
            throw new IllegalArgumentException("Dungeon Bank observed revision cannot be negative");
        }
        boolean initialized = players.containsKey(playerId);
        PlayerState previous = players.getOrDefault(playerId, PlayerState.defaults());
        if (previous.revision() != observedRevision) {
            return result(MutationStatus.STALE_REVISION, previous, initialized);
        }
        if (DungeonBankRules.occupiedSlots(balance) > previous.grade().slots()) {
            return result(MutationStatus.CAPACITY_EXCEEDED, previous, initialized);
        }
        if (previous.balance().equals(balance)) {
            return result(MutationStatus.NO_CHANGE, previous, initialized);
        }
        PlayerState updated = new PlayerState(
            nextRevision(previous.revision()),
            previous.grade(),
            balance
        );
        put(playerId, updated);
        return result(MutationStatus.APPLIED, updated, true);
    }

    public MutationResult upgrade(
        UUID playerId,
        long observedRevision,
        DungeonBankRules.Grade target
    ) {
        Objects.requireNonNull(playerId, "Dungeon Bank player");
        Objects.requireNonNull(target, "Dungeon Bank target grade");
        if (observedRevision < 0L) {
            throw new IllegalArgumentException("Dungeon Bank observed revision cannot be negative");
        }
        boolean initialized = players.containsKey(playerId);
        PlayerState previous = players.getOrDefault(playerId, PlayerState.defaults());
        if (previous.revision() != observedRevision) {
            return result(MutationStatus.STALE_REVISION, previous, initialized);
        }
        if (!previous.grade().hasNext()) {
            return result(MutationStatus.MAXIMUM_GRADE, previous, initialized);
        }
        if (previous.grade().next() != target) {
            return result(MutationStatus.INVALID_GRADE, previous, initialized);
        }
        PlayerState updated = new PlayerState(
            nextRevision(previous.revision()),
            target,
            previous.balance()
        );
        put(playerId, updated);
        return result(MutationStatus.APPLIED, updated, true);
    }

    void restore(UUID playerId, Snapshot snapshot) {
        Objects.requireNonNull(playerId, "Dungeon Bank rollback player");
        Objects.requireNonNull(snapshot, "Dungeon Bank rollback snapshot");
        if (DungeonBankRules.occupiedSlots(snapshot.balance()) > snapshot.grade().slots()) {
            throw new IllegalArgumentException("Dungeon Bank rollback snapshot exceeds capacity");
        }
        if (!snapshot.initialized()) {
            if (players.remove(playerId) != null) setDirty();
            return;
        }
        put(playerId, new PlayerState(snapshot.revision(), snapshot.grade(), snapshot.balance()));
    }

    private void put(UUID playerId, PlayerState state) {
        PlayerState previous = players.put(playerId, state);
        if (!state.equals(previous)) setDirty();
    }

    private static long nonnegative(long value, String field) {
        if (value < 0L) {
            throw new IllegalStateException("Dungeon Bank " + field + " balance is negative");
        }
        return value;
    }

    private static long nextRevision(long revision) {
        return revision == Long.MAX_VALUE ? 1L : revision + 1L;
    }

    private static Snapshot snapshotOf(PlayerState state, boolean initialized) {
        return new Snapshot(state.revision(), state.grade(), state.balance(), initialized);
    }

    private static MutationResult result(
        MutationStatus status,
        PlayerState state,
        boolean initialized
    ) {
        return new MutationResult(status, snapshotOf(state, initialized));
    }

    public enum MutationStatus {
        APPLIED,
        NO_CHANGE,
        STALE_REVISION,
        CAPACITY_EXCEEDED,
        MAXIMUM_GRADE,
        INVALID_GRADE
    }

    public record Snapshot(
        long revision,
        DungeonBankRules.Grade grade,
        DungeonBankRules.Balance balance,
        boolean initialized
    ) {
        public Snapshot {
            if (revision < 0L) {
                throw new IllegalArgumentException("Dungeon Bank revision cannot be negative");
            }
            grade = Objects.requireNonNull(grade, "Dungeon Bank grade");
            balance = Objects.requireNonNull(balance, "Dungeon Bank balance");
        }

        public int occupiedSlots() {
            return DungeonBankRules.occupiedSlots(balance);
        }
    }

    public record MutationResult(MutationStatus status, Snapshot snapshot) {
        public MutationResult {
            status = Objects.requireNonNull(status, "Dungeon Bank mutation status");
            snapshot = Objects.requireNonNull(snapshot, "Dungeon Bank mutation snapshot");
        }

        public boolean applied() {
            return status == MutationStatus.APPLIED;
        }
    }

    private record PlayerState(
        long revision,
        DungeonBankRules.Grade grade,
        DungeonBankRules.Balance balance
    ) {
        private static PlayerState defaults() {
            return new PlayerState(
                0L,
                DungeonBankRules.Grade.NORMAL,
                DungeonBankRules.Balance.empty()
            );
        }
    }
}
