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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonPotionShopData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_potion_shop";
    private static final String PLAYERS_KEY = "Players";
    private static final String PLAYER_KEY = "Player";
    private static final String LAST_DAY_KEY = "LastDay";
    private static final String QUANTITIES_KEY = "Quantities";
    private static final String OFFER_ID_KEY = "OfferId";
    private static final String QUANTITY_KEY = "Quantity";
    private static final long UNPROCESSED_DAY = -1L;

    private final Map<UUID, PlayerState> statesByPlayer = new HashMap<>();

    public static DungeonPotionShopData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(DungeonPotionShopData::new, DungeonPotionShopData::load),
            DATA_NAME
        );
    }

    public static DungeonPotionShopData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        DungeonPotionShopData data = new DungeonPotionShopData();
        ListTag players = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag playerTag = players.getCompound(index);
            if (!playerTag.hasUUID(PLAYER_KEY)) {
                throw new IllegalStateException("Potion Shop record is missing its player UUID");
            }
            UUID playerId = playerTag.getUUID(PLAYER_KEY);
            long lastDay = playerTag.contains(LAST_DAY_KEY, Tag.TAG_LONG)
                ? playerTag.getLong(LAST_DAY_KEY)
                : UNPROCESSED_DAY;
            if (lastDay < UNPROCESSED_DAY) {
                throw new IllegalStateException("Potion Shop day key cannot be below -1");
            }
            PlayerState state = new PlayerState(
                lastDay,
                readQuantities(playerTag.getList(QUANTITIES_KEY, Tag.TAG_COMPOUND))
            );
            if (data.statesByPlayer.putIfAbsent(playerId, state) != null) {
                throw new IllegalStateException("Duplicate Potion Shop player record: " + playerId);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        statesByPlayer.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID(PLAYER_KEY, entry.getKey());
                playerTag.putLong(LAST_DAY_KEY, entry.getValue().lastDay());
                ListTag quantities = new ListTag();
                entry.getValue().quantities().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(quantity -> {
                        CompoundTag quantityTag = new CompoundTag();
                        quantityTag.putString(OFFER_ID_KEY, quantity.getKey());
                        quantityTag.putInt(QUANTITY_KEY, quantity.getValue());
                        quantities.add(quantityTag);
                    });
                playerTag.put(QUANTITIES_KEY, quantities);
                players.add(playerTag);
            });
        tag.put(PLAYERS_KEY, players);
        return tag;
    }

    public RefreshResult refresh(UUID playerId, long dayKey) {
        Objects.requireNonNull(playerId, "Potion Shop player");
        if (dayKey < 0L) {
            throw new IllegalArgumentException("Potion Shop day key cannot be negative");
        }
        PlayerState previous = statesByPlayer.getOrDefault(playerId, PlayerState.unprocessed());
        if (dayKey < previous.lastDay()) {
            return new RefreshResult(RefreshStatus.STALE_DAY, snapshot(previous));
        }
        if (dayKey == previous.lastDay()) {
            Map<String, Integer> reconciled = reconcile(previous.quantities());
            if (!reconciled.equals(previous.quantities())) {
                PlayerState updated = new PlayerState(previous.lastDay(), reconciled);
                put(playerId, updated);
                return new RefreshResult(RefreshStatus.REFRESHED_DAY, snapshot(updated));
            }
            return new RefreshResult(RefreshStatus.UNCHANGED_DAY, snapshot(previous));
        }

        PlayerState refreshed = new PlayerState(dayKey, allQuantities());
        put(playerId, refreshed);
        return new RefreshResult(RefreshStatus.REFRESHED_DAY, snapshot(refreshed));
    }

    public PotionSnapshot snapshot(UUID playerId) {
        return snapshot(statesByPlayer.getOrDefault(playerId, PlayerState.unprocessed()));
    }

    public ReservationResult reserve(UUID playerId, String offerId) {
        Objects.requireNonNull(playerId, "Potion Shop reservation player");
        if (!DungeonShopCatalog.isPotionId(offerId)) {
            return new ReservationResult(ReservationStatus.NOT_FOUND, null);
        }
        PlayerState state = statesByPlayer.get(playerId);
        if (state == null || state.lastDay() < 0L) {
            return new ReservationResult(ReservationStatus.NO_DAY, null);
        }
        int quantity = state.quantities().getOrDefault(offerId, 0);
        if (quantity <= 0) {
            return new ReservationResult(ReservationStatus.SOLD_OUT, null);
        }
        if (DungeonShopCatalog.potionIsUnlimited(offerId)) {
            return new ReservationResult(
                ReservationStatus.UNLIMITED,
                new Reservation(playerId, offerId, false)
            );
        }
        Map<String, Integer> updated = new HashMap<>(state.quantities());
        updated.put(offerId, quantity - 1);
        put(playerId, new PlayerState(state.lastDay(), updated));
        return new ReservationResult(
            ReservationStatus.RESERVED,
            new Reservation(playerId, offerId, true)
        );
    }

    public void rollback(Reservation reservation) {
        if (reservation == null || !reservation.decremented()) {
            return;
        }
        PlayerState state = statesByPlayer.get(reservation.playerId());
        if (state == null) {
            return;
        }
        int current = state.quantities().getOrDefault(reservation.offerId(), 0);
        int restored = Math.min(
            DungeonShopCatalog.potionDailyQuantity(reservation.offerId()),
            current + 1
        );
        Map<String, Integer> updated = new HashMap<>(state.quantities());
        updated.put(reservation.offerId(), restored);
        put(reservation.playerId(), new PlayerState(state.lastDay(), updated));
    }

    private void put(UUID playerId, PlayerState state) {
        PlayerState previous = statesByPlayer.put(playerId, state);
        if (!state.equals(previous)) {
            setDirty();
        }
    }

    private static Map<String, Integer> allQuantities() {
        Map<String, Integer> quantities = new HashMap<>();
        for (DungeonShopCatalog.Entry entry : DungeonShopCatalog.potionSupplies(0)) {
            quantities.put(entry.id(), DungeonShopCatalog.potionDailyQuantity(entry.id()));
        }
        return quantities;
    }

    private static Map<String, Integer> reconcile(Map<String, Integer> previous) {
        Map<String, Integer> quantities = new HashMap<>(previous);
        for (DungeonShopCatalog.Entry entry : DungeonShopCatalog.potionSupplies(0)) {
            if (DungeonShopCatalog.potionIsUnlimited(entry.id())) {
                quantities.put(entry.id(), DungeonShopCatalog.potionDailyQuantity(entry.id()));
            } else {
                quantities.compute(
                    entry.id(),
                    (ignored, quantity) -> quantity == null
                        ? DungeonShopCatalog.potionDailyQuantity(entry.id())
                        : Math.min(quantity, DungeonShopCatalog.potionDailyQuantity(entry.id()))
                );
            }
        }
        return quantities;
    }

    private static PotionSnapshot snapshot(PlayerState state) {
        return new PotionSnapshot(state.lastDay(), state.quantities());
    }

    private static Map<String, Integer> readQuantities(ListTag tags) {
        Map<String, Integer> quantities = new HashMap<>();
        for (int index = 0; index < tags.size(); index++) {
            CompoundTag quantityTag = tags.getCompound(index);
            String offerId = quantityTag.getString(OFFER_ID_KEY);
            int quantity = quantityTag.getInt(QUANTITY_KEY);
            if (ResourceLocation.tryParse(offerId) == null || quantity < 0) {
                throw new IllegalStateException("Invalid persisted Potion Shop offer: " + offerId);
            }
            if (quantities.putIfAbsent(offerId, quantity) != null) {
                throw new IllegalStateException("Duplicate persisted Potion Shop offer: " + offerId);
            }
        }
        return quantities;
    }

    private record PlayerState(long lastDay, Map<String, Integer> quantities) {
        private PlayerState {
            if (lastDay < UNPROCESSED_DAY) {
                throw new IllegalArgumentException("Potion Shop day key cannot be below -1");
            }
            quantities = Map.copyOf(quantities);
        }

        static PlayerState unprocessed() {
            return new PlayerState(UNPROCESSED_DAY, Map.of());
        }
    }

    public enum RefreshStatus {
        REFRESHED_DAY,
        UNCHANGED_DAY,
        STALE_DAY
    }

    public enum ReservationStatus {
        RESERVED,
        UNLIMITED,
        SOLD_OUT,
        NO_DAY,
        NOT_FOUND
    }

    public record RefreshResult(RefreshStatus status, PotionSnapshot snapshot) {
    }

    public record PotionSnapshot(long dayKey, Map<String, Integer> quantities) {
        public PotionSnapshot {
            if (dayKey < UNPROCESSED_DAY) {
                throw new IllegalArgumentException("Potion Shop day key cannot be below -1");
            }
            quantities = Map.copyOf(quantities);
        }
    }

    public record ReservationResult(ReservationStatus status, Reservation reservation) {
    }

    public record Reservation(UUID playerId, String offerId, boolean decremented) {
    }
}
