package com.mineclawd.tensuradungeon.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonCosmeticData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_cosmetics";
    private static final String PLAYERS_KEY = "Players";
    private static final String PLAYER_KEY = "Player";
    private static final String REVISION_KEY = "Revision";
    private static final String OWNED_KEY = "Owned";
    private static final String TYPE_KEY = "Type";
    private static final String ID_KEY = "Id";
    private static final String EQUIPPED_HUD_KEY = "EquippedHud";
    private static final String EQUIPPED_QUEST_KEY = "EquippedQuest";

    private final Map<UUID, PlayerState> players = new HashMap<>();

    public static DungeonCosmeticData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(DungeonCosmeticData::new, DungeonCosmeticData::load),
            DATA_NAME
        );
    }

    public static DungeonCosmeticData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonCosmeticData data = new DungeonCosmeticData();
        ListTag playerTags = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < playerTags.size(); index++) {
            CompoundTag playerTag = playerTags.getCompound(index);
            if (!playerTag.hasUUID(PLAYER_KEY)) {
                throw new IllegalStateException("Cosmetic record is missing its player UUID");
            }
            UUID playerId = playerTag.getUUID(PLAYER_KEY);
            Set<CosmeticKey> owned = readOwned(playerTag.getList(OWNED_KEY, Tag.TAG_COMPOUND));
            CosmeticKey equippedHud = readEquipped(
                playerTag,
                EQUIPPED_HUD_KEY,
                CosmeticKey.Type.HUD_THEME,
                CosmeticKey.HUD_GOTHIC
            );
            CosmeticKey equippedQuest = readEquipped(
                playerTag,
                EQUIPPED_QUEST_KEY,
                CosmeticKey.Type.QUEST_THEME,
                CosmeticKey.QUEST_INHERIT
            );
            PlayerState state = new PlayerState(
                Math.max(0L, playerTag.getLong(REVISION_KEY)),
                withStarterEntitlement(owned),
                equippedHud,
                equippedQuest
            );
            validateEquippedOwnership(state);
            if (data.players.putIfAbsent(playerId, state) != null) {
                throw new IllegalStateException("Duplicate cosmetic player record: " + playerId);
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
                CompoundTag playerTag = new CompoundTag();
                PlayerState state = entry.getValue();
                playerTag.putUUID(PLAYER_KEY, entry.getKey());
                playerTag.putLong(REVISION_KEY, state.revision());
                ListTag ownedTags = new ListTag();
                state.owned().stream().sorted().forEach(key -> ownedTags.add(writeKey(key)));
                playerTag.put(OWNED_KEY, ownedTags);
                playerTag.put(EQUIPPED_HUD_KEY, writeKey(state.equippedHud()));
                playerTag.put(EQUIPPED_QUEST_KEY, writeKey(state.equippedQuest()));
                playerTags.add(playerTag);
            });
        tag.put(PLAYERS_KEY, playerTags);
        return tag;
    }

    public Snapshot snapshot(UUID playerId) {
        return snapshotOf(players.getOrDefault(playerId, PlayerState.defaults()));
    }

    public MutationResult grant(UUID playerId, CosmeticKey key) {
        requirePlayerAndKey(playerId, key);
        if (!key.cataloged()) {
            return result(MutationStatus.UNKNOWN_COSMETIC, players.getOrDefault(playerId, PlayerState.defaults()));
        }
        PlayerState previous = players.getOrDefault(playerId, PlayerState.defaults());
        if (previous.owned().contains(key)) {
            return result(MutationStatus.ALREADY_OWNED, previous);
        }
        Set<CosmeticKey> owned = new HashSet<>(previous.owned());
        owned.add(key);
        PlayerState updated = new PlayerState(
            nextRevision(previous.revision()),
            owned,
            previous.equippedHud(),
            previous.equippedQuest()
        );
        put(playerId, updated);
        return result(MutationStatus.GRANTED, updated);
    }

    public MutationResult equip(UUID playerId, CosmeticKey key, long observedRevision) {
        requirePlayerAndKey(playerId, key);
        if (observedRevision < 0L) {
            throw new IllegalArgumentException("Observed cosmetic revision cannot be negative");
        }
        PlayerState previous = players.getOrDefault(playerId, PlayerState.defaults());
        if (observedRevision != previous.revision()) {
            return result(MutationStatus.STALE_REVISION, previous);
        }
        if (!key.cataloged() && !key.equals(CosmeticKey.QUEST_INHERIT)) {
            return result(MutationStatus.UNKNOWN_COSMETIC, previous);
        }
        if (key.entitlementRequired() && !previous.owned().contains(key)) {
            return result(MutationStatus.NOT_OWNED, previous);
        }
        CosmeticKey equippedHud = previous.equippedHud();
        CosmeticKey equippedQuest = previous.equippedQuest();
        if (key.type() == CosmeticKey.Type.HUD_THEME) {
            if (equippedHud.equals(key)) {
                return result(MutationStatus.NO_CHANGE, previous);
            }
            equippedHud = key;
        } else {
            if (equippedQuest.equals(key)) {
                return result(MutationStatus.NO_CHANGE, previous);
            }
            equippedQuest = key;
        }
        PlayerState updated = new PlayerState(
            nextRevision(previous.revision()),
            previous.owned(),
            equippedHud,
            equippedQuest
        );
        put(playerId, updated);
        return result(MutationStatus.EQUIPPED, updated);
    }

    private static Set<CosmeticKey> readOwned(ListTag tags) {
        Set<CosmeticKey> owned = new HashSet<>();
        for (int index = 0; index < tags.size(); index++) {
            CosmeticKey key = readKey(tags.getCompound(index));
            if (!key.cataloged()) {
                throw new IllegalStateException("Unsupported cosmetic entitlement in save: " + key.canonicalId());
            }
            if (!owned.add(key)) {
                throw new IllegalStateException("Duplicate cosmetic entitlement in save: " + key.canonicalId());
            }
        }
        return owned;
    }

    private static CosmeticKey readEquipped(
        CompoundTag playerTag,
        String key,
        CosmeticKey.Type expectedType,
        CosmeticKey fallback
    ) {
        if (!playerTag.contains(key, Tag.TAG_COMPOUND)) {
            return fallback;
        }
        CosmeticKey cosmetic = readKey(playerTag.getCompound(key));
        if (cosmetic.type() != expectedType) {
            throw new IllegalStateException("Equipped cosmetic has the wrong slot type: " + cosmetic.canonicalId());
        }
        if (!cosmetic.cataloged() && !cosmetic.equals(CosmeticKey.QUEST_INHERIT)) {
            throw new IllegalStateException("Unsupported equipped cosmetic in save: " + cosmetic.canonicalId());
        }
        return cosmetic;
    }

    private static CosmeticKey readKey(CompoundTag tag) {
        String typeId = tag.getString(TYPE_KEY);
        String cosmeticId = tag.getString(ID_KEY);
        try {
            return new CosmeticKey(CosmeticKey.Type.valueOf(typeId), cosmeticId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid cosmetic key in save: " + typeId + ':' + cosmeticId, exception);
        }
    }

    private static CompoundTag writeKey(CosmeticKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TYPE_KEY, key.type().name());
        tag.putString(ID_KEY, key.id());
        return tag;
    }

    private static Set<CosmeticKey> withStarterEntitlement(Set<CosmeticKey> owned) {
        Set<CosmeticKey> normalized = new HashSet<>(owned);
        normalized.add(CosmeticKey.HUD_GOTHIC);
        return Set.copyOf(normalized);
    }

    private static void validateEquippedOwnership(PlayerState state) {
        if (!state.owned().contains(state.equippedHud())) {
            throw new IllegalStateException("Equipped HUD cosmetic is not owned: " + state.equippedHud().canonicalId());
        }
        if (state.equippedQuest().entitlementRequired() && !state.owned().contains(state.equippedQuest())) {
            throw new IllegalStateException(
                "Equipped quest cosmetic is not owned: " + state.equippedQuest().canonicalId()
            );
        }
    }

    private static void requirePlayerAndKey(UUID playerId, CosmeticKey key) {
        Objects.requireNonNull(playerId, "Cosmetic player");
        Objects.requireNonNull(key, "Cosmetic key");
    }

    private void put(UUID playerId, PlayerState state) {
        PlayerState previous = players.put(playerId, state);
        if (!state.equals(previous)) {
            setDirty();
        }
    }

    private static long nextRevision(long revision) {
        return revision == Long.MAX_VALUE ? 1L : revision + 1L;
    }

    private static MutationResult result(MutationStatus status, PlayerState state) {
        return new MutationResult(status, snapshotOf(state));
    }

    private static Snapshot snapshotOf(PlayerState state) {
        List<CosmeticKey> owned = new ArrayList<>(state.owned());
        owned.sort(CosmeticKey::compareTo);
        return new Snapshot(
            state.revision(),
            owned,
            state.equippedHud(),
            state.equippedQuest()
        );
    }

    public enum MutationStatus {
        GRANTED,
        EQUIPPED,
        NO_CHANGE,
        ALREADY_OWNED,
        NOT_OWNED,
        UNKNOWN_COSMETIC,
        STALE_REVISION
    }

    public record MutationResult(MutationStatus status, Snapshot snapshot) {
        public MutationResult {
            Objects.requireNonNull(status, "Cosmetic mutation status");
            Objects.requireNonNull(snapshot, "Cosmetic mutation snapshot");
        }

        public boolean changed() {
            return status == MutationStatus.GRANTED || status == MutationStatus.EQUIPPED;
        }
    }

    public record Snapshot(
        long revision,
        List<CosmeticKey> owned,
        CosmeticKey equippedHud,
        CosmeticKey equippedQuest
    ) {
        public Snapshot {
            if (revision < 0L) {
                throw new IllegalArgumentException("Cosmetic revision cannot be negative");
            }
            owned = List.copyOf(owned);
            Objects.requireNonNull(equippedHud, "Equipped HUD cosmetic");
            Objects.requireNonNull(equippedQuest, "Equipped quest cosmetic");
        }
    }

    private record PlayerState(
        long revision,
        Set<CosmeticKey> owned,
        CosmeticKey equippedHud,
        CosmeticKey equippedQuest
    ) {
        private PlayerState {
            if (revision < 0L) {
                throw new IllegalStateException("Cosmetic revision cannot be negative");
            }
            owned = withStarterEntitlement(owned);
            Objects.requireNonNull(equippedHud, "Equipped HUD cosmetic");
            Objects.requireNonNull(equippedQuest, "Equipped quest cosmetic");
        }

        static PlayerState defaults() {
            return new PlayerState(
                0L,
                Set.of(CosmeticKey.HUD_GOTHIC),
                CosmeticKey.HUD_GOTHIC,
                CosmeticKey.QUEST_INHERIT
            );
        }
    }
}
