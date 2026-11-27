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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonShopStockData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_shop_stock";
    private static final String PLAYERS_KEY = "Players";
    private static final String PLAYER_KEY = "Player";
    private static final String LAST_DAY_KEY = "LastDay";
    private static final String ESSENTIAL_STOCK_KEY = "EssentialStock";
    private static final String TODAYS_OFFERS_KEY = "TodaysOffers";
    private static final String TODAYS_PITY_KEY = "TodaysPity";
    private static final String OFFER_ID_KEY = "OfferId";
    private static final String QUANTITY_KEY = "Quantity";
    private static final String LAST_WEEK_KEY = "LastWeek";
    private static final String OFFERS_KEY = "Offers";
    private static final String PITY_KEY = "Pity";
    private static final String PURCHASED_WEEKLY_KEY = "PurchasedWeekly";
    private static final String COSMETIC_KEY = "Cosmetic";
    private static final String MISSED_WEEKS_KEY = "MissedWeeks";
    private static final String MISSED_DAYS_KEY = "MissedDays";
    private static final long UNPROCESSED_DAY = -1L;
    private static final long UNPROCESSED_WEEK = -1L;

    private final Map<UUID, PlayerState> statesByPlayer = new HashMap<>();

    public static DungeonShopStockData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(DungeonShopStockData::new, DungeonShopStockData::load),
            DATA_NAME
        );
    }

    public static DungeonShopStockData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        DungeonShopStockData data = new DungeonShopStockData();
        ListTag players = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag playerTag = players.getCompound(index);
            if (!playerTag.hasUUID(PLAYER_KEY)) {
                throw new IllegalStateException("Dungeon Shop stock record is missing its player UUID");
            }
            UUID playerId = playerTag.getUUID(PLAYER_KEY);
            long lastDay = playerTag.contains(LAST_DAY_KEY, Tag.TAG_LONG)
                ? playerTag.getLong(LAST_DAY_KEY)
                : UNPROCESSED_DAY;
            Map<String, Integer> essentialStock = readEssentialStock(
                playerTag.getList(ESSENTIAL_STOCK_KEY, Tag.TAG_COMPOUND)
            );
            List<String> todaysOffers = readOffers(
                playerTag.getList(TODAYS_OFFERS_KEY, Tag.TAG_COMPOUND), "Today's Finds offer"
            );
            Map<String, Integer> missedDays = readPity(
                playerTag.getList(TODAYS_PITY_KEY, Tag.TAG_COMPOUND),
                MISSED_DAYS_KEY,
                "Today's Finds pity"
            );
            if (!playerTag.contains(TODAYS_OFFERS_KEY, Tag.TAG_LIST)) {
                todaysOffers = essentialStock.keySet().stream()
                    .filter(id -> {
                        DungeonShopCatalog.OfferDefinition definition =
                            DungeonShopCatalog.definitionById(id);
                        return definition != null
                            && definition.category() == DungeonShopCatalog.Category.TODAYS_FINDS
                            && definition.productType() == DungeonShopCatalog.ProductType.ITEM;
                    })
                    .sorted()
                    .toList();
            }
            long lastWeek = playerTag.contains(LAST_WEEK_KEY, Tag.TAG_LONG)
                ? playerTag.getLong(LAST_WEEK_KEY)
                : UNPROCESSED_WEEK;
            List<String> offers = readOffers(
                playerTag.getList(OFFERS_KEY, Tag.TAG_COMPOUND), "Weekly Shop offer"
            );
            Map<String, Integer> pity = readPity(
                playerTag.getList(PITY_KEY, Tag.TAG_COMPOUND),
                MISSED_WEEKS_KEY,
                "Weekly Shop pity"
            );
            Set<String> purchasedWeekly = readOfferIds(
                playerTag.getList(PURCHASED_WEEKLY_KEY, Tag.TAG_COMPOUND)
            );
            PlayerState state = new PlayerState(
                lastDay, essentialStock, todaysOffers, missedDays,
                lastWeek, offers, pity, purchasedWeekly
            );
            if (data.statesByPlayer.putIfAbsent(playerId, state) != null) {
                throw new IllegalStateException("Duplicate Dungeon Shop stock player record: " + playerId);
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
                ListTag essentialStock = new ListTag();
                entry.getValue().essentialStock().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(stockEntry -> {
                        CompoundTag stockTag = new CompoundTag();
                        stockTag.putString(OFFER_ID_KEY, stockEntry.getKey());
                        stockTag.putInt(QUANTITY_KEY, stockEntry.getValue());
                        essentialStock.add(stockTag);
                    });
                playerTag.put(ESSENTIAL_STOCK_KEY, essentialStock);
                ListTag todaysOffers = new ListTag();
                entry.getValue().todaysOffers().forEach(id ->
                    todaysOffers.add(writeOfferId(id)));
                playerTag.put(TODAYS_OFFERS_KEY, todaysOffers);
                ListTag todaysPity = new ListTag();
                entry.getValue().missedDays().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(pityEntry -> {
                        CompoundTag pityTag = writeOfferId(pityEntry.getKey());
                        pityTag.putInt(MISSED_DAYS_KEY, pityEntry.getValue());
                        todaysPity.add(pityTag);
                    });
                playerTag.put(TODAYS_PITY_KEY, todaysPity);
                playerTag.putLong(LAST_WEEK_KEY, entry.getValue().lastWeek());
                ListTag offers = new ListTag();
                entry.getValue().offers().forEach(id -> offers.add(writeOfferId(id)));
                playerTag.put(OFFERS_KEY, offers);
                ListTag pity = new ListTag();
                entry.getValue().missedWeeks().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(pityEntry -> {
                        CompoundTag pityTag = writeOfferId(pityEntry.getKey());
                        pityTag.putInt(MISSED_WEEKS_KEY, pityEntry.getValue());
                        pity.add(pityTag);
                    });
                playerTag.put(PITY_KEY, pity);
                ListTag purchasedWeekly = new ListTag();
                entry.getValue().purchasedWeekly().stream().sorted()
                    .forEach(id -> purchasedWeekly.add(writeOfferId(id)));
                playerTag.put(PURCHASED_WEEKLY_KEY, purchasedWeekly);
                players.add(playerTag);
            });
        tag.put(PLAYERS_KEY, players);
        return tag;
    }

    public DailyRefreshResult refreshDaily(
        UUID playerId,
        long dayKey,
        int highestCompletedFloor
    ) {
        Objects.requireNonNull(playerId, "Dungeon Shop stock player");
        if (dayKey < 0L) {
            throw new IllegalArgumentException("Dungeon Shop day key cannot be negative");
        }
        PlayerState previous = statesByPlayer.getOrDefault(playerId, PlayerState.unprocessed());
        if (dayKey < previous.lastDay()) {
            return new DailyRefreshResult(
                DailyRefreshStatus.STALE_DAY,
                dailySnapshot(previous)
            );
        }
        if (dayKey == previous.lastDay()) {
            Map<String, Integer> reconciled = new HashMap<>(previous.essentialStock());
            for (DungeonShopCatalog.Entry entry
                : DungeonShopCatalog.essentialStockOffers(highestCompletedFloor)) {
                reconcileQuantity(reconciled, playerId, dayKey, entry);
            }
            List<String> todaysOffers = new ArrayList<>(previous.todaysOffers());
            Map<String, Integer> missedDays = new HashMap<>(previous.missedDays());
            for (DungeonShopCatalog.Entry entry
                : DungeonShopCatalog.todaysItemOffers(highestCompletedFloor)) {
                if (!todaysOffers.contains(entry.id()) && !missedDays.containsKey(entry.id())) {
                    boolean appeared = entry.guaranteedByEligibleWeek() <= 1
                        || rollBasisPoints(playerId, dayKey, entry.id())
                            < entry.appearanceBasisPoints();
                    missedDays.put(entry.id(), appeared ? 0 : 1);
                    if (appeared) {
                        todaysOffers.add(entry.id());
                    }
                }
                if (todaysOffers.contains(entry.id())) {
                    reconcileQuantity(reconciled, playerId, dayKey, entry);
                }
            }
            if (!reconciled.equals(previous.essentialStock())
                || !todaysOffers.equals(previous.todaysOffers())
                || !missedDays.equals(previous.missedDays())) {
                PlayerState updated = new PlayerState(
                    previous.lastDay(),
                    reconciled,
                    todaysOffers,
                    missedDays,
                    previous.lastWeek(),
                    previous.offers(),
                    previous.missedWeeks(),
                    previous.purchasedWeekly()
                );
                put(playerId, updated);
                return new DailyRefreshResult(
                    DailyRefreshStatus.REFRESHED_DAY,
                    dailySnapshot(updated)
                );
            }
            return new DailyRefreshResult(
                DailyRefreshStatus.UNCHANGED_DAY,
                dailySnapshot(previous)
            );
        }

        Map<String, Integer> quantities = new HashMap<>();
        for (DungeonShopCatalog.Entry entry
            : DungeonShopCatalog.essentialStockOffers(highestCompletedFloor)) {
            initializeQuantity(quantities, playerId, dayKey, entry);
        }
        List<String> todaysOffers = new ArrayList<>();
        Map<String, Integer> missedDays = new HashMap<>(previous.missedDays());
        for (DungeonShopCatalog.Entry entry
            : DungeonShopCatalog.todaysItemOffers(highestCompletedFloor)) {
            int eligibleDay = previous.missedDays().getOrDefault(entry.id(), 0) + 1;
            boolean appeared = eligibleDay >= entry.guaranteedByEligibleWeek()
                || rollBasisPoints(playerId, dayKey, entry.id()) < entry.appearanceBasisPoints();
            missedDays.put(entry.id(), appeared ? 0 : eligibleDay);
            if (appeared) {
                todaysOffers.add(entry.id());
                initializeQuantity(quantities, playerId, dayKey, entry);
            }
        }
        PlayerState refreshed = new PlayerState(
            dayKey,
            quantities,
            todaysOffers,
            missedDays,
            previous.lastWeek(),
            previous.offers(),
            previous.missedWeeks(),
            previous.purchasedWeekly()
        );
        put(playerId, refreshed);
        return new DailyRefreshResult(
            DailyRefreshStatus.REFRESHED_DAY,
            dailySnapshot(refreshed)
        );
    }

    private static void initializeQuantity(
        Map<String, Integer> quantities,
        UUID playerId,
        long dayKey,
        DungeonShopCatalog.Entry entry
    ) {
        quantities.put(
            entry.id(),
            entry.unlimited() ? entry.minimumQuantity() : rollQuantity(playerId, dayKey, entry)
        );
    }

    private static void reconcileQuantity(
        Map<String, Integer> quantities,
        UUID playerId,
        long dayKey,
        DungeonShopCatalog.Entry entry
    ) {
        Integer existing = quantities.get(entry.id());
        if (existing == null) {
            initializeQuantity(quantities, playerId, dayKey, entry);
        } else if (entry.unlimited()) {
            quantities.put(entry.id(), entry.minimumQuantity());
        } else if (existing > entry.maximumQuantity()) {
            quantities.put(entry.id(), entry.maximumQuantity());
        }
    }

    public RefreshResult refreshWeekly(
        UUID playerId,
        long weekKey,
        Set<CosmeticKey> ownedCosmetics
    ) {
        Objects.requireNonNull(playerId, "Dungeon Shop stock player");
        Set<CosmeticKey> owned = ownedCosmetics == null ? Set.of() : Set.copyOf(ownedCosmetics);
        if (weekKey < 0L) {
            throw new IllegalArgumentException("Dungeon Shop week key cannot be negative");
        }
        PlayerState previous = statesByPlayer.getOrDefault(
            playerId,
            PlayerState.unprocessed()
        );
        if (weekKey < previous.lastWeek()) {
            return new RefreshResult(RefreshStatus.STALE_WEEK, snapshot(previous));
        }
        if (weekKey == previous.lastWeek()) {
            PlayerState reconciled = reconcileCurrentWeek(
                playerId, weekKey, owned, previous
            );
            if (!reconciled.equals(previous)) {
                put(playerId, reconciled);
                return new RefreshResult(RefreshStatus.REFRESHED_WEEK, snapshot(reconciled));
            }
            return new RefreshResult(RefreshStatus.UNCHANGED_WEEK, snapshot(previous));
        }

        List<String> offers = new ArrayList<>();
        Map<String, Integer> missedWeeks = new HashMap<>();
        for (DungeonShopCatalog.Entry entry : DungeonShopCatalog.weeklyThemes()) {
            String offerId = entry.id();
            boolean ownedAlready = entry.cosmetic() != null && owned.contains(entry.cosmetic());
            int priorMisses = ownedAlready ? 0
                : previous.missedWeeks().getOrDefault(offerId, 0);
            int eligibleWeek = priorMisses + 1;
            boolean guaranteed = !ownedAlready
                && eligibleWeek >= entry.guaranteedByEligibleWeek();
            boolean appeared = guaranteed
                || rollBasisPoints(playerId, weekKey, offerId) < entry.appearanceBasisPoints();
            if (appeared) {
                offers.add(offerId);
                missedWeeks.put(offerId, 0);
            } else {
                missedWeeks.put(offerId, ownedAlready ? 0 : eligibleWeek);
            }
        }
        PlayerState refreshed = new PlayerState(
            previous.lastDay(),
            previous.essentialStock(),
            previous.todaysOffers(),
            previous.missedDays(),
            weekKey,
            offers,
            missedWeeks,
            Set.of()
        );
        put(playerId, refreshed);
        return new RefreshResult(RefreshStatus.REFRESHED_WEEK, snapshot(refreshed));
    }

    public boolean forceWeeklyOffer(UUID playerId, long weekKey, CosmeticKey cosmetic) {
        Objects.requireNonNull(playerId, "Dungeon Shop stock player");
        Objects.requireNonNull(cosmetic, "Dungeon Shop Weekly fixture cosmetic");
        if (weekKey < 0L) {
            throw new IllegalArgumentException("Dungeon Shop week key cannot be negative");
        }
        String offerId = weeklyOfferId(cosmetic);
        if (offerId == null) {
            throw new IllegalArgumentException(
                "Dungeon Shop Weekly fixture is outside the approved catalog"
            );
        }
        PlayerState previous = statesByPlayer.getOrDefault(
            playerId,
            PlayerState.unprocessed()
        );
        if (weekKey < previous.lastWeek()) {
            return false;
        }
        List<String> offers = previous.lastWeek() == weekKey
            ? new ArrayList<>(previous.offers())
            : new ArrayList<>();
        if (!offers.contains(offerId)) {
            offers.add(offerId);
        }
        Map<String, Integer> missedWeeks = new HashMap<>(previous.missedWeeks());
        missedWeeks.put(offerId, 0);
        put(playerId, new PlayerState(
            previous.lastDay(),
            previous.essentialStock(),
            previous.todaysOffers(),
            previous.missedDays(),
            weekKey,
            offers,
            missedWeeks,
            previous.lastWeek() == weekKey ? previous.purchasedWeekly() : Set.of()
        ));
        return true;
    }

    public WeeklySnapshot snapshot(UUID playerId) {
        return snapshot(statesByPlayer.getOrDefault(playerId, PlayerState.unprocessed()));
    }

    public DailySnapshot dailySnapshot(UUID playerId) {
        return dailySnapshot(statesByPlayer.getOrDefault(playerId, PlayerState.unprocessed()));
    }

    public ReservationResult reserveDaily(UUID playerId, long dayKey, String offerId) {
        Objects.requireNonNull(playerId, "Dungeon Shop Daily stock player");
        DungeonShopCatalog.Entry entry = DungeonShopCatalog.entryById(offerId, 0);
        if (entry == null || entry.category() == DungeonShopCatalog.Category.WEEKLY
            || entry.productType() != DungeonShopCatalog.ProductType.ITEM) {
            return new ReservationResult(ReservationStatus.NOT_FOUND, null);
        }
        PlayerState previous = statesByPlayer.getOrDefault(playerId, PlayerState.unprocessed());
        if (previous.lastDay() != dayKey) {
            return new ReservationResult(ReservationStatus.NO_PERIOD, null);
        }
        if (entry.category() == DungeonShopCatalog.Category.TODAYS_FINDS
            && !previous.todaysOffers().contains(offerId)) {
            return new ReservationResult(ReservationStatus.NO_PERIOD, null);
        }
        if (entry.unlimited()) {
            return new ReservationResult(ReservationStatus.UNLIMITED, null);
        }
        int quantity = previous.essentialStock().getOrDefault(offerId, 0);
        if (quantity <= 0) {
            return new ReservationResult(ReservationStatus.SOLD_OUT, null);
        }
        Map<String, Integer> remaining = new HashMap<>(previous.essentialStock());
        remaining.put(offerId, quantity - 1);
        put(playerId, new PlayerState(
            previous.lastDay(), remaining, previous.todaysOffers(), previous.missedDays(),
            previous.lastWeek(), previous.offers(),
            previous.missedWeeks(), previous.purchasedWeekly()
        ));
        return new ReservationResult(
            ReservationStatus.RESERVED,
            new Reservation(playerId, dayKey, offerId, ReservationPeriod.DAILY)
        );
    }

    public ReservationResult reserveWeekly(UUID playerId, long weekKey, String offerId) {
        Objects.requireNonNull(playerId, "Dungeon Shop Weekly stock player");
        DungeonShopCatalog.Entry entry = DungeonShopCatalog.entryById(offerId, 0);
        if (entry == null || entry.category() != DungeonShopCatalog.Category.WEEKLY
            || entry.productType() != DungeonShopCatalog.ProductType.ITEM) {
            return new ReservationResult(ReservationStatus.NOT_FOUND, null);
        }
        PlayerState previous = statesByPlayer.getOrDefault(playerId, PlayerState.unprocessed());
        if (previous.lastWeek() != weekKey || !previous.offers().contains(offerId)) {
            return new ReservationResult(ReservationStatus.NO_PERIOD, null);
        }
        if (previous.purchasedWeekly().contains(offerId)) {
            return new ReservationResult(ReservationStatus.SOLD_OUT, null);
        }
        Set<String> purchased = new HashSet<>(previous.purchasedWeekly());
        purchased.add(offerId);
        put(playerId, new PlayerState(
            previous.lastDay(), previous.essentialStock(),
            previous.todaysOffers(), previous.missedDays(), previous.lastWeek(),
            previous.offers(), previous.missedWeeks(), purchased
        ));
        return new ReservationResult(
            ReservationStatus.RESERVED,
            new Reservation(playerId, weekKey, offerId, ReservationPeriod.WEEKLY)
        );
    }

    public void rollback(Reservation reservation) {
        if (reservation == null) {
            return;
        }
        PlayerState previous = statesByPlayer.get(reservation.playerId());
        if (previous == null) {
            return;
        }
        if (reservation.period() == ReservationPeriod.DAILY
            && previous.lastDay() == reservation.periodKey()) {
            DungeonShopCatalog.Entry entry = DungeonShopCatalog.entryById(reservation.offerId(), 0);
            if (entry == null) {
                return;
            }
            Map<String, Integer> restored = new HashMap<>(previous.essentialStock());
            int quantity = restored.getOrDefault(reservation.offerId(), 0);
            restored.put(reservation.offerId(), Math.min(entry.maximumQuantity(), quantity + 1));
            put(reservation.playerId(), new PlayerState(
                previous.lastDay(), restored, previous.todaysOffers(), previous.missedDays(),
                previous.lastWeek(), previous.offers(),
                previous.missedWeeks(), previous.purchasedWeekly()
            ));
        } else if (reservation.period() == ReservationPeriod.WEEKLY
            && previous.lastWeek() == reservation.periodKey()) {
            Set<String> restored = new HashSet<>(previous.purchasedWeekly());
            if (restored.remove(reservation.offerId())) {
                put(reservation.playerId(), new PlayerState(
                    previous.lastDay(), previous.essentialStock(),
                    previous.todaysOffers(), previous.missedDays(), previous.lastWeek(),
                    previous.offers(), previous.missedWeeks(), restored
                ));
            }
        }
    }

    private static PlayerState reconcileCurrentWeek(
        UUID playerId,
        long weekKey,
        Set<CosmeticKey> owned,
        PlayerState previous
    ) {
        List<String> offers = new ArrayList<>(previous.offers());
        Map<String, Integer> missed = new HashMap<>(previous.missedWeeks());
        for (DungeonShopCatalog.Entry entry : DungeonShopCatalog.weeklyThemes()) {
            if (offers.contains(entry.id()) || missed.containsKey(entry.id())) {
                continue;
            }
            boolean ownedAlready = entry.cosmetic() != null && owned.contains(entry.cosmetic());
            boolean appeared = !ownedAlready
                && (entry.guaranteedByEligibleWeek() <= 1
                    || rollBasisPoints(playerId, weekKey, entry.id())
                        < entry.appearanceBasisPoints());
            if (appeared) {
                offers.add(entry.id());
                missed.put(entry.id(), 0);
            } else {
                missed.put(entry.id(), ownedAlready ? 0 : 1);
            }
        }
        return new PlayerState(
            previous.lastDay(), previous.essentialStock(),
            previous.todaysOffers(), previous.missedDays(), previous.lastWeek(),
            offers, missed, previous.purchasedWeekly()
        );
    }

    static int rollBasisPoints(UUID playerId, long weekKey, CosmeticKey cosmetic) {
        Objects.requireNonNull(cosmetic, "Dungeon Shop Weekly roll cosmetic");
        String offerId = weeklyOfferId(cosmetic);
        return rollBasisPoints(
            playerId,
            weekKey,
            offerId == null ? cosmetic.canonicalId() : offerId
        );
    }

    static int rollBasisPoints(UUID playerId, long weekKey, String offerId) {
        long seed = playerId.getMostSignificantBits()
            ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 17)
            ^ Long.rotateLeft(weekKey, 39)
            ^ ((long) offerId.hashCode() * 0x9E3779B97F4A7C15L);
        return (int) Math.floorMod(mix64(seed), 10_000L);
    }

    static int rollQuantity(
        UUID playerId,
        long dayKey,
        DungeonShopCatalog.Entry entry
    ) {
        int range = entry.maximumQuantity() - entry.minimumQuantity() + 1;
        long seed = playerId.getMostSignificantBits()
            ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 17)
            ^ Long.rotateLeft(dayKey, 39)
            ^ ((long) entry.id().hashCode() * 0x9E3779B97F4A7C15L);
        return entry.minimumQuantity()
            + (int) Math.floorMod(mix64(seed), (long) range);
    }

    private void put(UUID playerId, PlayerState state) {
        PlayerState previous = statesByPlayer.put(playerId, state);
        if (!state.equals(previous)) {
            setDirty();
        }
    }

    private static WeeklySnapshot snapshot(PlayerState state) {
        return new WeeklySnapshot(
            state.lastWeek(), state.offers(), state.missedWeeks(), state.purchasedWeekly()
        );
    }

    private static DailySnapshot dailySnapshot(PlayerState state) {
        return new DailySnapshot(
            state.lastDay(), state.essentialStock(), state.todaysOffers(), state.missedDays()
        );
    }

    private static Map<String, Integer> readEssentialStock(ListTag tags) {
        Map<String, Integer> stock = new HashMap<>();
        for (int index = 0; index < tags.size(); index++) {
            CompoundTag stockTag = tags.getCompound(index);
            String offerId = stockTag.getString(OFFER_ID_KEY);
            int quantity = stockTag.getInt(QUANTITY_KEY);
            if (ResourceLocation.tryParse(offerId) == null || quantity < 0) {
                throw new IllegalStateException(
                    "Invalid persisted Dungeon Shop Daily stock: " + offerId
                );
            }
            if (stock.putIfAbsent(offerId, quantity) != null) {
                throw new IllegalStateException(
                    "Duplicate persisted Essential Supplies offer: " + offerId
                );
            }
        }
        return stock;
    }

    private static List<String> readOffers(ListTag tags, String label) {
        List<String> offers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < tags.size(); index++) {
            String offerId = readOfferId(tags.getCompound(index));
            if (!seen.add(offerId)) {
                throw new IllegalStateException(
                    "Duplicate persisted " + label + ": " + offerId
                );
            }
            offers.add(offerId);
        }
        return offers;
    }

    private static Map<String, Integer> readPity(
        ListTag tags,
        String countKey,
        String label
    ) {
        Map<String, Integer> pity = new HashMap<>();
        for (int index = 0; index < tags.size(); index++) {
            CompoundTag pityTag = tags.getCompound(index);
            String offerId = readOfferId(pityTag);
            int missedPeriods = pityTag.getInt(countKey);
            if (missedPeriods < 0) {
                throw new IllegalStateException("Invalid persisted " + label + " record");
            }
            if (pity.putIfAbsent(offerId, missedPeriods) != null) {
                throw new IllegalStateException(
                    "Duplicate persisted " + label + " record: " + offerId
                );
            }
        }
        return pity;
    }

    private static Set<String> readOfferIds(ListTag tags) {
        Set<String> result = new HashSet<>();
        for (int index = 0; index < tags.size(); index++) {
            String offerId = readOfferId(tags.getCompound(index));
            if (!result.add(offerId)) {
                throw new IllegalStateException(
                    "Duplicate persisted Weekly purchase: " + offerId
                );
            }
        }
        return result;
    }

    private static String weeklyOfferId(CosmeticKey key) {
        return DungeonShopCatalog.weeklyThemes().stream()
            .filter(entry -> key.equals(entry.cosmetic()))
            .map(DungeonShopCatalog.Entry::id)
            .findFirst()
            .orElse(null);
    }

    private static CompoundTag writeOfferId(String offerId) {
        CompoundTag tag = new CompoundTag();
        tag.putString(OFFER_ID_KEY, offerId);
        return tag;
    }

    private static String readOfferId(CompoundTag tag) {
        String value = tag.contains(OFFER_ID_KEY, Tag.TAG_STRING)
            ? tag.getString(OFFER_ID_KEY)
            : legacyCosmeticOfferId(tag.getString(COSMETIC_KEY));
        if (value == null || value.isBlank() || value.length() > 64
            || ResourceLocation.tryParse(value) == null) {
            throw new IllegalStateException("Invalid persisted Dungeon Shop offer id: " + value);
        }
        return value;
    }

    private static String legacyCosmeticOfferId(String cosmeticId) {
        CosmeticKey key = CosmeticKey.fromCanonicalId(cosmeticId)
            .orElseThrow(() -> new IllegalStateException(
                "Invalid persisted Dungeon Shop cosmetic: " + cosmeticId
            ));
        if (CosmeticKey.HUD_GOTHIC.equals(key)) {
            return "tensura_dungeon:gothic_hud_theme";
        }
        if (CosmeticKey.HUD_FREY.equals(key)) {
            return "tensura_dungeon:frey_hud_theme";
        }
        if (CosmeticKey.QUEST_GOTHIC.equals(key)) {
            return "tensura_dungeon:gothic_quest_hud_theme";
        }
        throw new IllegalStateException(
            "Unsupported persisted Weekly Shop cosmetic: " + cosmeticId
        );
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    public enum RefreshStatus {
        REFRESHED_WEEK,
        UNCHANGED_WEEK,
        STALE_WEEK
    }

    public enum DailyRefreshStatus {
        REFRESHED_DAY,
        UNCHANGED_DAY,
        STALE_DAY
    }

    public enum ReservationStatus {
        RESERVED,
        UNLIMITED,
        SOLD_OUT,
        NO_PERIOD,
        NOT_FOUND
    }

    public enum ReservationPeriod {
        DAILY,
        WEEKLY
    }

    public record Reservation(
        UUID playerId,
        long periodKey,
        String offerId,
        ReservationPeriod period
    ) {
        public Reservation {
            Objects.requireNonNull(playerId, "Dungeon Shop reservation player");
            Objects.requireNonNull(offerId, "Dungeon Shop reservation offer");
            Objects.requireNonNull(period, "Dungeon Shop reservation period");
        }
    }

    public record ReservationResult(ReservationStatus status, Reservation reservation) {
        public ReservationResult {
            Objects.requireNonNull(status, "Dungeon Shop reservation status");
            if (status == ReservationStatus.RESERVED && reservation == null) {
                throw new IllegalArgumentException("Reserved Shop stock requires a reservation");
            }
        }
    }

    public record DailyRefreshResult(DailyRefreshStatus status, DailySnapshot snapshot) {
        public DailyRefreshResult {
            Objects.requireNonNull(status, "Dungeon Shop Daily stock refresh status");
            Objects.requireNonNull(snapshot, "Dungeon Shop Daily stock refresh snapshot");
        }
    }

    public record RefreshResult(RefreshStatus status, WeeklySnapshot snapshot) {
        public RefreshResult {
            Objects.requireNonNull(status, "Dungeon Shop stock refresh status");
            Objects.requireNonNull(snapshot, "Dungeon Shop stock refresh snapshot");
        }
    }

    public record WeeklySnapshot(
        long weekKey,
        List<String> offers,
        Map<String, Integer> missedWeeks,
        Set<String> purchasedOffers
    ) {
        public WeeklySnapshot(
            long weekKey,
            List<CosmeticKey> cosmeticOffers,
            Map<CosmeticKey, Integer> cosmeticMissedWeeks
        ) {
            this(
                weekKey,
                cosmeticOffers.stream()
                    .map(key -> legacyCosmeticOfferId(key.canonicalId()))
                    .toList(),
                cosmeticMissedWeeks.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                    entry -> legacyCosmeticOfferId(entry.getKey().canonicalId()),
                    Map.Entry::getValue
                )),
                Set.of()
            );
        }

        public WeeklySnapshot {
            if (weekKey < UNPROCESSED_WEEK) {
                throw new IllegalArgumentException("Dungeon Shop stock week is invalid");
            }
            offers = List.copyOf(offers);
            missedWeeks = Map.copyOf(missedWeeks);
            purchasedOffers = Set.copyOf(purchasedOffers);
            validateState(offers, missedWeeks, purchasedOffers);
        }
    }

    public record DailySnapshot(
        long dayKey,
        Map<String, Integer> quantities,
        List<String> todaysOffers,
        Map<String, Integer> missedDays
    ) {
        public DailySnapshot(long dayKey, Map<String, Integer> quantities) {
            this(dayKey, quantities, List.of(), Map.of());
        }

        public DailySnapshot {
            if (dayKey < UNPROCESSED_DAY) {
                throw new IllegalArgumentException("Dungeon Shop stock day is invalid");
            }
            quantities = Map.copyOf(quantities);
            todaysOffers = List.copyOf(todaysOffers);
            missedDays = Map.copyOf(missedDays);
            validateDailyState(quantities);
            validateDailySelection(todaysOffers, missedDays);
        }
    }

    private record PlayerState(
        long lastDay,
        Map<String, Integer> essentialStock,
        List<String> todaysOffers,
        Map<String, Integer> missedDays,
        long lastWeek,
        List<String> offers,
        Map<String, Integer> missedWeeks,
        Set<String> purchasedWeekly
    ) {
        private PlayerState {
            if (lastDay < UNPROCESSED_DAY) {
                throw new IllegalStateException("Dungeon Shop stock day is invalid");
            }
            if (lastWeek < UNPROCESSED_WEEK) {
                throw new IllegalStateException("Dungeon Shop stock week is invalid");
            }
            essentialStock = Map.copyOf(essentialStock);
            todaysOffers = List.copyOf(todaysOffers);
            missedDays = Map.copyOf(missedDays);
            offers = List.copyOf(offers);
            missedWeeks = Map.copyOf(missedWeeks);
            purchasedWeekly = Set.copyOf(purchasedWeekly);
            validateDailyState(essentialStock);
            validateDailySelection(todaysOffers, missedDays);
            validateState(offers, missedWeeks, purchasedWeekly);
        }

        static PlayerState unprocessed() {
            return new PlayerState(
                UNPROCESSED_DAY,
                Map.of(),
                List.of(),
                Map.of(),
                UNPROCESSED_WEEK,
                List.of(),
                Map.of(),
                Set.of()
            );
        }
    }

    private static void validateDailyState(Map<String, Integer> quantities) {
        if (quantities.size() > 512) {
            throw new IllegalArgumentException("Dungeon Shop Daily stock is oversized");
        }
        for (Map.Entry<String, Integer> stock : quantities.entrySet()) {
            if (ResourceLocation.tryParse(stock.getKey()) == null || stock.getValue() < 0) {
                throw new IllegalArgumentException(
                    "Dungeon Shop Daily stock contains an invalid entry"
                );
            }
        }
    }

    private static void validateDailySelection(
        List<String> offers,
        Map<String, Integer> missedDays
    ) {
        if (offers.size() > 256 || missedDays.size() > 256
            || new HashSet<>(offers).size() != offers.size()) {
            throw new IllegalArgumentException(
                "Dungeon Shop Today's Finds state is duplicated or oversized"
            );
        }
        Set<String> ids = new HashSet<>(offers);
        ids.addAll(missedDays.keySet());
        if (ids.stream().anyMatch(id -> ResourceLocation.tryParse(id) == null)
            || missedDays.values().stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException(
                "Dungeon Shop Today's Finds state contains an invalid entry"
            );
        }
    }

    private static void validateState(
        List<String> offers,
        Map<String, Integer> missedWeeks,
        Set<String> purchasedOffers
    ) {
        if (offers.size() > 256 || missedWeeks.size() > 256 || purchasedOffers.size() > 256
            || new HashSet<>(offers).size() != offers.size()) {
            throw new IllegalArgumentException("Dungeon Shop Weekly offers are duplicated or oversized");
        }
        Set<String> ids = new HashSet<>(offers);
        ids.addAll(missedWeeks.keySet());
        ids.addAll(purchasedOffers);
        if (ids.stream().anyMatch(id -> ResourceLocation.tryParse(id) == null)) {
            throw new IllegalArgumentException("Dungeon Shop Weekly state contains an invalid id");
        }
        for (Map.Entry<String, Integer> pity : missedWeeks.entrySet()) {
            if (pity.getValue() < 0) {
                throw new IllegalArgumentException("Dungeon Shop Weekly pity is outside its guarantee bound");
            }
        }
    }
}
