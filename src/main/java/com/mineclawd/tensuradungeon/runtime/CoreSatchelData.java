package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class CoreSatchelData extends SavedData {
    static final String DATA_NAME = "tensura_dungeon_core_satchels";
    private static final String PLAYERS_KEY = "Players";
    private static final String PLAYER_KEY = "Player";
    private static final String GRADE_KEY = "Grade";
    private static final String CORES_KEY = "Cores";
    private static final String SLOT_KEY = "Slot";
    private static final String CORE_ID_KEY = "CoreId";
    private static final String TIER_KEY = "Tier";
    private static final String TOTAL_CHARGE_KEY = "TotalCharge";
    private static final String CAPACITY_KEY = "Capacity";
    private static final String NATURAL_CHARGE_KEY = "NaturalCharge";
    private static final String FAMILY_KEY = "Family";
    private static final String TECHNIQUE_KEY = "Technique";

    private final Map<UUID, SatchelGrade> gradesByPlayer = new HashMap<>();
    private final Map<UUID, Map<Integer, CoreRecord>> coresByPlayer = new HashMap<>();

    public static CoreSatchelData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(CoreSatchelData::new, CoreSatchelData::load),
            DATA_NAME
        );
    }

    public static CoreSatchelData load(CompoundTag tag, HolderLookup.Provider registries) {
        CoreSatchelData data = new CoreSatchelData();
        ListTag players = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag playerTag = players.getCompound(index);
            if (!playerTag.hasUUID(PLAYER_KEY)) {
                throw new IllegalStateException("Core Satchel record is missing its player UUID");
            }
            String gradeId = playerTag.getString(GRADE_KEY);
            SatchelGrade grade = SatchelGrade.fromId(gradeId).orElseThrow(
                () -> new IllegalStateException("Unsupported Core Satchel grade in save: " + gradeId)
            );
            UUID playerId = playerTag.getUUID(PLAYER_KEY);
            data.gradesByPlayer.put(playerId, grade);

            ListTag cores = playerTag.getList(CORES_KEY, Tag.TAG_COMPOUND);
            Map<Integer, CoreRecord> slots = new HashMap<>();
            Set<UUID> coreIds = new HashSet<>();
            for (int coreIndex = 0; coreIndex < cores.size(); coreIndex++) {
                CompoundTag coreTag = cores.getCompound(coreIndex);
                int slot = coreTag.getInt(SLOT_KEY);
                if (slot < 0 || slot >= grade.capacity()) {
                    throw new IllegalStateException(
                        "Core Satchel slot " + slot + " is outside " + grade.displayName() + " capacity"
                    );
                }
                if (!coreTag.hasUUID(CORE_ID_KEY)) {
                    throw new IllegalStateException("Stored Core record is missing its unique identity");
                }
                String family = coreTag.getString(FAMILY_KEY);
                String technique = coreTag.getString(TECHNIQUE_KEY);
                CoreRecord core = family.isBlank() || technique.isBlank()
                    ? new CoreRecord(
                        coreTag.getUUID(CORE_ID_KEY),
                        coreTag.getInt(TIER_KEY),
                        coreTag.getLong(TOTAL_CHARGE_KEY),
                        coreTag.getLong(CAPACITY_KEY),
                        coreTag.getLong(NATURAL_CHARGE_KEY)
                    )
                    : new CoreRecord(
                        coreTag.getUUID(CORE_ID_KEY),
                        coreTag.getInt(TIER_KEY),
                        coreTag.getLong(TOTAL_CHARGE_KEY),
                        coreTag.getLong(CAPACITY_KEY),
                        coreTag.getLong(NATURAL_CHARGE_KEY),
                        family,
                        technique
                    );
                if (slots.putIfAbsent(slot, core) != null) {
                    throw new IllegalStateException("Duplicate Core Satchel slot in save: " + slot);
                }
                if (!coreIds.add(core.coreId())) {
                    throw new IllegalStateException("Duplicate Monster Core identity in Satchel save: " + core.coreId());
                }
            }
            if (!slots.isEmpty()) {
                data.coresByPlayer.put(playerId, slots);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        gradesByPlayer.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID(PLAYER_KEY, entry.getKey());
                playerTag.putString(GRADE_KEY, entry.getValue().id());
                ListTag cores = new ListTag();
                coresByPlayer.getOrDefault(entry.getKey(), Map.of()).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(coreEntry -> {
                        CompoundTag coreTag = new CompoundTag();
                        CoreRecord core = coreEntry.getValue();
                        coreTag.putInt(SLOT_KEY, coreEntry.getKey());
                        coreTag.putUUID(CORE_ID_KEY, core.coreId());
                        coreTag.putInt(TIER_KEY, core.tier());
                        coreTag.putLong(TOTAL_CHARGE_KEY, core.totalCharge());
                        coreTag.putLong(CAPACITY_KEY, core.capacity());
                        coreTag.putLong(NATURAL_CHARGE_KEY, core.naturalCharge());
                        coreTag.putString(FAMILY_KEY, core.familyId());
                        coreTag.putString(TECHNIQUE_KEY, core.techniqueId());
                        cores.add(coreTag);
                    });
                playerTag.put(CORES_KEY, cores);
                players.add(playerTag);
            });
        tag.put(PLAYERS_KEY, players);
        return tag;
    }

    public GrantResult grantNormal(UUID playerId) {
        if (gradesByPlayer.containsKey(playerId)) {
            return GrantResult.ALREADY_OWNED;
        }
        gradesByPlayer.put(playerId, SatchelGrade.NORMAL);
        setDirty();
        return GrantResult.GRANTED;
    }

    public UpgradeStatus upgradeTo(UUID playerId, SatchelGrade targetGrade) {
        if (targetGrade == null) throw new IllegalArgumentException("Target Core Satchel grade is required");
        SatchelGrade currentGrade = gradesByPlayer.get(playerId);
        if (currentGrade == null) {
            return UpgradeStatus.SATCHEL_NOT_OWNED;
        }
        if (currentGrade == targetGrade) {
            return UpgradeStatus.ALREADY_OWNED;
        }
        if (currentGrade.next().filter(targetGrade::equals).isEmpty()) {
            return UpgradeStatus.NOT_NEXT_GRADE;
        }
        gradesByPlayer.put(playerId, targetGrade);
        setDirty();
        return UpgradeStatus.UPGRADED;
    }

    boolean rollbackUpgrade(UUID playerId, SatchelGrade targetGrade) {
        if (playerId == null || targetGrade == null || targetGrade == SatchelGrade.NORMAL) {
            return false;
        }
        SatchelGrade currentGrade = gradesByPlayer.get(playerId);
        if (currentGrade != targetGrade) {
            return false;
        }
        SatchelGrade previousGrade = SatchelGrade.values()[targetGrade.ordinal() - 1];
        gradesByPlayer.put(playerId, previousGrade);
        setDirty();
        return true;
    }

    public Optional<SatchelGrade> nextEligibleUpgrade(UUID playerId, int highestCompletedFloor) {
        SatchelGrade currentGrade = gradesByPlayer.get(playerId);
        if (currentGrade == null) {
            return Optional.empty();
        }
        return currentGrade.next()
            .filter(nextGrade -> highestCompletedFloor >= nextGrade.requiredFloor());
    }

    public AccessSnapshot access(UUID playerId) {
        SatchelGrade grade = gradesByPlayer.get(playerId);
        return grade == null ? AccessSnapshot.unowned() : AccessSnapshot.owned(grade);
    }

    Optional<SatchelGrade> grade(UUID playerId) {
        return Optional.ofNullable(gradesByPlayer.get(playerId));
    }

    public SatchelSnapshot snapshot(UUID playerId) {
        AccessSnapshot access = access(playerId);
        if (!access.acquired()) {
            return SatchelSnapshot.unowned();
        }
        LinkedHashMap<Integer, CoreRecord> cores = new LinkedHashMap<>();
        coresByPlayer.getOrDefault(playerId, Map.of()).entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> cores.put(entry.getKey(), entry.getValue()));
        return new SatchelSnapshot(access, cores);
    }

    public StoreResult storeFirstAvailable(UUID playerId, CoreRecord core) {
        SatchelGrade grade = gradesByPlayer.get(playerId);
        if (grade == null) {
            return new StoreResult(StoreStatus.SATCHEL_NOT_OWNED, -1, core);
        }
        Map<Integer, CoreRecord> slots = coresByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
        if (slots.values().stream().anyMatch(existing -> existing.coreId().equals(core.coreId()))) {
            return new StoreResult(StoreStatus.DUPLICATE_CORE_ID, -1, core);
        }
        for (int slot = 0; slot < grade.capacity(); slot++) {
            if (!slots.containsKey(slot)) {
                slots.put(slot, core);
                setDirty();
                return new StoreResult(StoreStatus.STORED, slot, core);
            }
        }
        return new StoreResult(StoreStatus.SATCHEL_FULL, -1, core);
    }

    public StoreResult storeAt(UUID playerId, int slot, CoreRecord core) {
        if (core == null) throw new IllegalArgumentException("Monster Core is required");
        SatchelGrade grade = gradesByPlayer.get(playerId);
        if (grade == null) {
            return new StoreResult(StoreStatus.SATCHEL_NOT_OWNED, -1, core);
        }
        if (slot < 0 || slot >= grade.capacity()) {
            return new StoreResult(StoreStatus.INVALID_SLOT, slot, core);
        }
        Map<Integer, CoreRecord> slots = coresByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
        if (slots.values().stream().anyMatch(existing -> existing.coreId().equals(core.coreId()))) {
            return new StoreResult(StoreStatus.DUPLICATE_CORE_ID, slot, core);
        }
        if (slots.containsKey(slot)) {
            return new StoreResult(StoreStatus.SLOT_OCCUPIED, slot, core);
        }
        slots.put(slot, core);
        setDirty();
        return new StoreResult(StoreStatus.STORED, slot, core);
    }

    public ReplaceResult replaceAt(UUID playerId, int slot, CoreRecord incoming) {
        if (incoming == null) throw new IllegalArgumentException("Incoming Monster Core is required");
        SatchelGrade grade = gradesByPlayer.get(playerId);
        if (grade == null) {
            return new ReplaceResult(ReplaceStatus.SATCHEL_NOT_OWNED, slot, incoming, null);
        }
        if (slot < 0 || slot >= grade.capacity()) {
            return new ReplaceResult(ReplaceStatus.INVALID_SLOT, slot, incoming, null);
        }
        Map<Integer, CoreRecord> slots = coresByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
        if (slots.values().stream().anyMatch(existing -> existing.coreId().equals(incoming.coreId()))) {
            return new ReplaceResult(ReplaceStatus.DUPLICATE_CORE_ID, slot, incoming, null);
        }
        CoreRecord displaced = slots.get(slot);
        if (displaced == null) {
            return new ReplaceResult(ReplaceStatus.SLOT_EMPTY, slot, incoming, null);
        }
        slots.put(slot, incoming);
        setDirty();
        return new ReplaceResult(ReplaceStatus.REPLACED, slot, incoming, displaced);
    }

    public Optional<CoreRecord> coreAt(UUID playerId, int slot) {
        return Optional.ofNullable(coresByPlayer.getOrDefault(playerId, Map.of()).get(slot));
    }

    public int slotOf(UUID playerId, UUID coreId) {
        if (coreId == null) return -1;
        return coresByPlayer.getOrDefault(playerId, Map.of()).entrySet().stream()
            .filter(entry -> entry.getValue().coreId().equals(coreId))
            .mapToInt(Map.Entry::getKey)
            .findFirst()
            .orElse(-1);
    }

    public TakeResult takeAt(UUID playerId, int slot, UUID expectedCoreId) {
        SatchelGrade grade = gradesByPlayer.get(playerId);
        if (grade == null) {
            return new TakeResult(TakeStatus.SATCHEL_NOT_OWNED, slot, null);
        }
        if (slot < 0 || slot >= grade.capacity()) {
            return new TakeResult(TakeStatus.INVALID_SLOT, slot, null);
        }
        Map<Integer, CoreRecord> slots = coresByPlayer.get(playerId);
        CoreRecord core = slots == null ? null : slots.get(slot);
        if (core == null) {
            return new TakeResult(TakeStatus.SLOT_EMPTY, slot, null);
        }
        if (expectedCoreId == null || !core.coreId().equals(expectedCoreId)) {
            return new TakeResult(TakeStatus.CORE_ID_MISMATCH, slot, core);
        }
        slots.remove(slot);
        if (slots.isEmpty()) coresByPlayer.remove(playerId);
        setDirty();
        return new TakeResult(TakeStatus.TAKEN, slot, core);
    }

    public enum GrantResult {
        GRANTED,
        ALREADY_OWNED
    }

    public enum UpgradeStatus {
        UPGRADED,
        SATCHEL_NOT_OWNED,
        ALREADY_OWNED,
        NOT_NEXT_GRADE
    }

    public enum StoreStatus {
        STORED,
        SATCHEL_NOT_OWNED,
        SATCHEL_FULL,
        DUPLICATE_CORE_ID,
        INVALID_SLOT,
        SLOT_OCCUPIED
    }

    public record StoreResult(StoreStatus status, int slot, CoreRecord core) {
    }

    public enum ReplaceStatus {
        REPLACED,
        SATCHEL_NOT_OWNED,
        DUPLICATE_CORE_ID,
        INVALID_SLOT,
        SLOT_EMPTY
    }

    public record ReplaceResult(
        ReplaceStatus status,
        int slot,
        CoreRecord incoming,
        CoreRecord displaced
    ) {
    }

    public enum TakeStatus {
        TAKEN,
        SATCHEL_NOT_OWNED,
        INVALID_SLOT,
        SLOT_EMPTY,
        CORE_ID_MISMATCH
    }

    public record TakeResult(TakeStatus status, int slot, CoreRecord core) {
    }

    public record CoreRecord(
        UUID coreId,
        int tier,
        long totalCharge,
        long capacity,
        long naturalCharge,
        String familyId,
        String techniqueId
    ) {
        private static final long[] TIER_CAPACITIES = {
            15_000L, 50_000L, 150_000L, 400_000L, 1_000_000L, 2_500_000L
        };

        public CoreRecord {
            if (coreId == null) {
                throw new IllegalArgumentException("Monster Core identity is required");
            }
            if (tier < 1 || tier > TIER_CAPACITIES.length) {
                throw new IllegalArgumentException("Core Tier must be between I and VI");
            }
            long approvedCapacity = capacityForTier(tier);
            if (capacity != approvedCapacity) {
                throw new IllegalArgumentException(
                    "Tier " + tier + " Core capacity must be " + approvedCapacity + ", got " + capacity
                );
            }
            if (totalCharge < 0L || totalCharge > capacity) {
                throw new IllegalArgumentException("Total Charge must be within Core capacity");
            }
            if (naturalCharge < 0L || naturalCharge > totalCharge) {
                throw new IllegalArgumentException("Natural Charge must be within Total Charge");
            }
            if (familyId == null || familyId.isBlank() || techniqueId == null || techniqueId.isBlank()
                || !MonsterCoreTechniqueCatalog.isKnown(familyId, techniqueId)) {
                throw new IllegalArgumentException("Monster Core family and technique are not approved");
            }
            familyId = familyId.toLowerCase(Locale.ROOT);
        }

        public CoreRecord(
            UUID coreId,
            int tier,
            long totalCharge,
            long capacity,
            long naturalCharge
        ) {
            this(
                coreId,
                tier,
                totalCharge,
                capacity,
                naturalCharge,
                MonsterCoreTechniqueCatalog.forLegacy(coreId).familyId(),
                MonsterCoreTechniqueCatalog.forLegacy(coreId).techniqueId()
            );
        }

        public CoreRecord withCharge(long newTotalCharge, long newNaturalCharge) {
            return new CoreRecord(
                coreId, tier, newTotalCharge, capacity, newNaturalCharge, familyId, techniqueId
            );
        }

        public CoreRecord consumeCharge(long amount) {
            if (amount < 0L || amount > totalCharge) {
                throw new IllegalArgumentException("Core charge debit is outside the available charge");
            }
            long naturalUsed = Math.min(naturalCharge, amount);
            return withCharge(totalCharge - amount, naturalCharge - naturalUsed);
        }

        public CoreRecord recharged() {
            return withCharge(capacity, naturalCharge);
        }

        public String displayName() {
            return MonsterCoreTechniqueCatalog.displayName(familyId, techniqueId);
        }

        public static long capacityForTier(int tier) {
            if (tier < 1 || tier > TIER_CAPACITIES.length) {
                throw new IllegalArgumentException("Core Tier must be between I and VI");
            }
            return TIER_CAPACITIES[tier - 1];
        }
    }

    public enum SatchelGrade {
        NORMAL("normal", "Normal", 6, 1, 0, 0, 0),
        SPECIAL("special", "Special", 12, 50, 3_500, 3, 500),
        RARE("rare", "Rare", 18, 100, 2_500, 5, 1_250),
        UNIQUE("unique", "Unique", 24, 150, 1_800, 7, 3_000),
        LEGEND("legend", "Legend", 36, 200, 1_200, 10, 5_600),
        MYTHIC("mythic", "Mythic", 48, 250, 800, 14, 10_300);

        private final String id;
        private final String displayName;
        private final int capacity;
        private final int requiredFloor;
        private final int appearanceBasisPoints;
        private final int guaranteedByEligibleDay;
        private final int priceGold;

        SatchelGrade(
            String id,
            String displayName,
            int capacity,
            int requiredFloor,
            int appearanceBasisPoints,
            int guaranteedByEligibleDay,
            int priceGold
        ) {
            this.id = id;
            this.displayName = displayName;
            this.capacity = capacity;
            this.requiredFloor = requiredFloor;
            this.appearanceBasisPoints = appearanceBasisPoints;
            this.guaranteedByEligibleDay = guaranteedByEligibleDay;
            this.priceGold = priceGold;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public int capacity() {
            return capacity;
        }

        public int requiredFloor() {
            return requiredFloor;
        }

        public int appearanceBasisPoints() {
            return appearanceBasisPoints;
        }

        public int guaranteedByEligibleDay() {
            return guaranteedByEligibleDay;
        }

        public int priceGold() {
            return priceGold;
        }

        public long priceBronze() {
            return Math.multiplyExact(priceGold, DungeonCurrencyPolicy.BRONZE_PER_GOLD);
        }

        Optional<SatchelGrade> next() {
            int nextOrdinal = ordinal() + 1;
            return nextOrdinal < values().length ? Optional.of(values()[nextOrdinal]) : Optional.empty();
        }

        static Optional<SatchelGrade> fromId(String id) {
            if (id == null || id.isBlank()) return Optional.empty();
            String normalized = id.toLowerCase(Locale.ROOT);
            for (SatchelGrade grade : values()) {
                if (grade.id.equals(normalized)) return Optional.of(grade);
            }
            return Optional.empty();
        }
    }

    public record AccessSnapshot(boolean acquired, String gradeName, int capacity) {
        public AccessSnapshot {
            if (!acquired) {
                gradeName = "";
                capacity = 0;
            } else if (gradeName == null || gradeName.isBlank() || capacity <= 0) {
                throw new IllegalArgumentException("Owned Core Satchel access requires a grade and capacity");
            }
        }

        static AccessSnapshot unowned() {
            return new AccessSnapshot(false, "", 0);
        }

        static AccessSnapshot owned(SatchelGrade grade) {
            return new AccessSnapshot(true, grade.displayName(), grade.capacity());
        }
    }

    public record SatchelSnapshot(AccessSnapshot access, Map<Integer, CoreRecord> cores) {
        public SatchelSnapshot {
            if (access == null || cores == null) {
                throw new IllegalArgumentException("Core Satchel snapshot values are required");
            }
            cores = Map.copyOf(cores);
            if (!access.acquired() && !cores.isEmpty()) {
                throw new IllegalArgumentException("Unowned Core Satchel snapshot cannot expose Core records");
            }
            for (Map.Entry<Integer, CoreRecord> entry : cores.entrySet()) {
                if (entry.getKey() < 0 || entry.getKey() >= access.capacity()) {
                    throw new IllegalArgumentException("Core slot is outside snapshot capacity: " + entry.getKey());
                }
            }
        }

        static SatchelSnapshot unowned() {
            return new SatchelSnapshot(AccessSnapshot.unowned(), Map.of());
        }
    }
}
