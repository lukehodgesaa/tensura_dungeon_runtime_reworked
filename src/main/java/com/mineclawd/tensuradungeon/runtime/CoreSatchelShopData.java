package com.mineclawd.tensuradungeon.runtime;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class CoreSatchelShopData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_core_satchel_shop";
    private static final String PLAYERS_KEY = "Players";
    private static final String PLAYER_KEY = "Player";
    private static final String LAST_DAY_KEY = "LastDay";
    private static final String TRACKED_GRADE_KEY = "TrackedGrade";
    private static final String FAILED_ELIGIBLE_DAYS_KEY = "FailedEligibleDays";
    private static final String OFFERED_GRADE_KEY = "OfferedGrade";
    private static final String PINNED_KEY = "Pinned";
    private static final String PURCHASED_KEY = "Purchased";
    private static final long UNPROCESSED_DAY = -1L;

    private final Map<UUID, PlayerShopState> statesByPlayer = new HashMap<>();

    public static CoreSatchelShopData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(CoreSatchelShopData::new, CoreSatchelShopData::load),
            DATA_NAME
        );
    }

    public static CoreSatchelShopData load(CompoundTag tag, HolderLookup.Provider registries) {
        CoreSatchelShopData data = new CoreSatchelShopData();
        ListTag players = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag playerTag = players.getCompound(index);
            if (!playerTag.hasUUID(PLAYER_KEY)) {
                throw new IllegalStateException("Core Satchel shop record is missing its player UUID");
            }
            UUID playerId = playerTag.getUUID(PLAYER_KEY);
            long lastDay = playerTag.contains(LAST_DAY_KEY, Tag.TAG_LONG)
                ? playerTag.getLong(LAST_DAY_KEY)
                : UNPROCESSED_DAY;
            CoreSatchelData.SatchelGrade trackedGrade =
                readOptionalUpgradeGrade(playerTag, TRACKED_GRADE_KEY);
            CoreSatchelData.SatchelGrade offeredGrade =
                readOptionalUpgradeGrade(playerTag, OFFERED_GRADE_KEY);
            PlayerShopState state = new PlayerShopState(
                lastDay,
                trackedGrade,
                playerTag.getInt(FAILED_ELIGIBLE_DAYS_KEY),
                offeredGrade,
                playerTag.getBoolean(PINNED_KEY),
                playerTag.getBoolean(PURCHASED_KEY)
            );
            if (data.statesByPlayer.putIfAbsent(playerId, state) != null) {
                throw new IllegalStateException("Duplicate Core Satchel shop player record: " + playerId);
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
                PlayerShopState state = entry.getValue();
                playerTag.putUUID(PLAYER_KEY, entry.getKey());
                playerTag.putLong(LAST_DAY_KEY, state.lastDay());
                if (state.trackedGrade() != null) {
                    playerTag.putString(TRACKED_GRADE_KEY, state.trackedGrade().id());
                }
                playerTag.putInt(FAILED_ELIGIBLE_DAYS_KEY, state.failedEligibleDays());
                if (state.offeredGrade() != null) {
                    playerTag.putString(OFFERED_GRADE_KEY, state.offeredGrade().id());
                }
                playerTag.putBoolean(PINNED_KEY, state.pinned());
                playerTag.putBoolean(PURCHASED_KEY, state.purchased());
                players.add(playerTag);
            });
        tag.put(PLAYERS_KEY, players);
        return tag;
    }

    public RefreshResult refresh(
        UUID playerId,
        CoreSatchelData satchels,
        int highestCompletedFloor,
        long dayKey
    ) {
        if (playerId == null || satchels == null) {
            throw new IllegalArgumentException("Player and Core Satchel state are required");
        }
        if (dayKey < 0L) {
            throw new IllegalArgumentException("Personal-shop day key cannot be negative");
        }

        PlayerShopState previous = statesByPlayer.getOrDefault(playerId, PlayerShopState.unprocessed());
        if (dayKey < previous.lastDay()) {
            return new RefreshResult(RefreshStatus.STALE_DAY, snapshot(previous));
        }
        if (dayKey == previous.lastDay()) {
            return new RefreshResult(RefreshStatus.UNCHANGED_DAY, snapshot(previous));
        }

        Optional<CoreSatchelData.SatchelGrade> eligible =
            satchels.nextEligibleUpgrade(playerId, highestCompletedFloor);
        if (previous.pinned() && eligible.orElse(null) == previous.offeredGrade()
            && DungeonShopCatalog.hasSatchelUpgrade(previous.offeredGrade())) {
            PlayerShopState pinned = previous.withDay(dayKey);
            put(playerId, pinned);
            return new RefreshResult(RefreshStatus.PINNED_OFFER, snapshot(pinned));
        }
        if (eligible.isEmpty()) {
            PlayerShopState empty = PlayerShopState.empty(dayKey);
            put(playerId, empty);
            return new RefreshResult(RefreshStatus.REFRESHED_NO_OFFER, snapshot(empty));
        }

        CoreSatchelData.SatchelGrade target = eligible.orElseThrow();
        if (!DungeonShopCatalog.hasSatchelUpgrade(target)) {
            PlayerShopState empty = PlayerShopState.empty(dayKey);
            put(playerId, empty);
            return new RefreshResult(RefreshStatus.REFRESHED_NO_OFFER, snapshot(empty));
        }
        DungeonShopCatalog.Entry configured = DungeonShopCatalog.satchelUpgrade(target);
        int failedDays = previous.trackedGrade() == target ? previous.failedEligibleDays() : 0;
        int eligibleDay = failedDays + 1;
        PlayerShopState refreshed;
        RefreshStatus status;
        if (eligibleDay >= configured.guaranteedByEligibleWeek()) {
            refreshed = new PlayerShopState(dayKey, target, eligibleDay, target, true, false);
            status = RefreshStatus.PINNED_OFFER;
        } else if (rollBasisPoints(playerId, dayKey, target)
            < configured.appearanceBasisPoints()) {
            refreshed = new PlayerShopState(dayKey, target, 0, target, false, false);
            status = RefreshStatus.REFRESHED_OFFERED;
        } else {
            refreshed = new PlayerShopState(dayKey, target, eligibleDay, null, false, false);
            status = RefreshStatus.REFRESHED_NO_OFFER;
        }
        put(playerId, refreshed);
        return new RefreshResult(status, snapshot(refreshed));
    }

    public PurchaseStateStatus recordPurchased(
        UUID playerId,
        CoreSatchelData.SatchelGrade purchasedGrade,
        long dayKey
    ) {
        if (playerId == null || purchasedGrade == null) {
            throw new IllegalArgumentException("Player and purchased Core Satchel grade are required");
        }
        if (dayKey < 0L) {
            throw new IllegalArgumentException("Personal-shop day key cannot be negative");
        }
        PlayerShopState state = statesByPlayer.get(playerId);
        if (state == null || state.offeredGrade() == null) {
            return PurchaseStateStatus.NO_OFFER;
        }
        if (state.purchased()) {
            return PurchaseStateStatus.ALREADY_PURCHASED;
        }
        if (dayKey < state.lastDay()) {
            return PurchaseStateStatus.STALE_DAY;
        }
        if (state.offeredGrade() != purchasedGrade) {
            return PurchaseStateStatus.GRADE_MISMATCH;
        }
        if (!state.pinned() && dayKey != state.lastDay()) {
            return PurchaseStateStatus.OFFER_EXPIRED;
        }
        put(playerId, state.afterPurchase(dayKey));
        return PurchaseStateStatus.PURCHASE_RECORDED;
    }

    public OfferSnapshot snapshot(UUID playerId) {
        return snapshot(statesByPlayer.getOrDefault(playerId, PlayerShopState.unprocessed()));
    }

    static int rollBasisPoints(
        UUID playerId,
        long dayKey,
        CoreSatchelData.SatchelGrade grade
    ) {
        long seed = playerId.getMostSignificantBits()
            ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 21)
            ^ Long.rotateLeft(dayKey, 37)
            ^ ((long) grade.ordinal() * 0x9E3779B97F4A7C15L);
        long mixed = mix64(seed);
        return (int) Math.floorMod(mixed, 10_000L);
    }

    private void put(UUID playerId, PlayerShopState state) {
        if (!state.equals(statesByPlayer.put(playerId, state))) {
            setDirty();
        }
    }

    private static OfferSnapshot snapshot(PlayerShopState state) {
        return new OfferSnapshot(
            state.lastDay(),
            state.trackedGrade(),
            state.offeredGrade(),
            state.failedEligibleDays(),
            state.pinned(),
            state.purchased()
        );
    }

    private static CoreSatchelData.SatchelGrade readOptionalUpgradeGrade(
        CompoundTag tag,
        String key
    ) {
        if (!tag.contains(key, Tag.TAG_STRING) || tag.getString(key).isBlank()) {
            return null;
        }
        String gradeId = tag.getString(key);
        CoreSatchelData.SatchelGrade grade = CoreSatchelData.SatchelGrade.fromId(gradeId)
            .orElseThrow(() -> new IllegalStateException(
                "Unsupported Core Satchel shop grade in save: " + gradeId
            ));
        if (grade == CoreSatchelData.SatchelGrade.NORMAL) {
            throw new IllegalStateException("Normal Core Satchel cannot be a paid shop upgrade");
        }
        return grade;
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    public enum RefreshStatus {
        REFRESHED_OFFERED,
        REFRESHED_NO_OFFER,
        PINNED_OFFER,
        UNCHANGED_DAY,
        STALE_DAY
    }

    public enum PurchaseStateStatus {
        PURCHASE_RECORDED,
        NO_OFFER,
        STALE_DAY,
        GRADE_MISMATCH,
        OFFER_EXPIRED,
        ALREADY_PURCHASED
    }

    public record RefreshResult(RefreshStatus status, OfferSnapshot snapshot) {
        public RefreshResult {
            if (status == null || snapshot == null) {
                throw new IllegalArgumentException("Core Satchel shop refresh result is incomplete");
            }
        }
    }

    public record OfferSnapshot(
        long dayKey,
        CoreSatchelData.SatchelGrade trackedGrade,
        CoreSatchelData.SatchelGrade offeredGrade,
        int failedEligibleDays,
        boolean pinned,
        boolean purchased
    ) {
        public OfferSnapshot(
            long dayKey,
            CoreSatchelData.SatchelGrade trackedGrade,
            CoreSatchelData.SatchelGrade offeredGrade,
            int failedEligibleDays,
            boolean pinned
        ) {
            this(dayKey, trackedGrade, offeredGrade, failedEligibleDays, pinned, false);
        }

        public OfferSnapshot {
            if (dayKey < UNPROCESSED_DAY || failedEligibleDays < 0) {
                throw new IllegalArgumentException("Core Satchel shop snapshot counters are invalid");
            }
            if (pinned && offeredGrade == null) {
                throw new IllegalArgumentException("Pinned Core Satchel shop snapshot requires an offer");
            }
            if (offeredGrade != null && trackedGrade != offeredGrade) {
                throw new IllegalArgumentException("Core Satchel shop offer must match its tracked grade");
            }
            if (purchased && offeredGrade == null) {
                throw new IllegalArgumentException("Purchased Core Satchel shop snapshot requires an offer");
            }
            if (purchased && pinned) {
                throw new IllegalArgumentException("Purchased Core Satchel shop snapshot cannot remain pinned");
            }
        }

        public boolean offered() {
            return offeredGrade != null;
        }
    }

    private record PlayerShopState(
        long lastDay,
        CoreSatchelData.SatchelGrade trackedGrade,
        int failedEligibleDays,
        CoreSatchelData.SatchelGrade offeredGrade,
        boolean pinned,
        boolean purchased
    ) {
        private PlayerShopState {
            if (lastDay < UNPROCESSED_DAY || failedEligibleDays < 0) {
                throw new IllegalStateException("Core Satchel shop state counters are invalid");
            }
            if (offeredGrade != null && trackedGrade != offeredGrade) {
                throw new IllegalStateException("Core Satchel offer must match its tracked grade");
            }
            if (pinned && offeredGrade == null) {
                throw new IllegalStateException("Pinned Core Satchel shop state requires an offer");
            }
            if (purchased && offeredGrade == null) {
                throw new IllegalStateException("Purchased Core Satchel shop state requires an offer");
            }
            if (purchased && pinned) {
                throw new IllegalStateException("Purchased Core Satchel shop state cannot remain pinned");
            }
        }

        static PlayerShopState unprocessed() {
            return new PlayerShopState(UNPROCESSED_DAY, null, 0, null, false, false);
        }

        static PlayerShopState empty(long dayKey) {
            return new PlayerShopState(dayKey, null, 0, null, false, false);
        }

        PlayerShopState withDay(long dayKey) {
            return new PlayerShopState(
                dayKey, trackedGrade, failedEligibleDays, offeredGrade, pinned, purchased
            );
        }

        PlayerShopState afterPurchase(long dayKey) {
            return new PlayerShopState(
                dayKey, trackedGrade, failedEligibleDays, offeredGrade, false, true
            );
        }
    }
}
