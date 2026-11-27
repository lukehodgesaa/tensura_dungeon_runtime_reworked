package com.mineclawd.tensuradungeon.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class MonsterCoreRecoveryPolicy {
    private static final int BASIS_POINTS = 10_000;

    private MonsterCoreRecoveryPolicy() {}

    public static RecoveryRoll roll(RecoveryRequest request) {
        StableRandom random = new StableRandom(seedFor(request));
        int recoveryRoll = random.nextInt(BASIS_POINTS);
        int guaranteedTier = request.firstClearMilestone() ? milestoneTier(request.floor()) : 0;
        boolean guaranteed = guaranteedTier > 0;
        int currentTier = currentTier(request.floor());
        if (!guaranteed && recoveryRoll >= request.source().recoveryChanceBasisPoints(currentTier)) {
            return RecoveryRoll.miss(recoveryRoll);
        }

        int tierRoll = random.nextInt(BASIS_POINTS);
        int tier = guaranteed ? guaranteedTier : selectTier(currentTier, tierRoll, random);
        int chargeBandRoll = random.nextInt(BASIS_POINTS);
        ChargeRange chargeRange = chargeRange(chargeBandRoll);
        int naturalChargeBasisPoints = chargeRange.minimumBasisPoints()
            + random.nextInt(chargeRange.maximumBasisPoints() - chargeRange.minimumBasisPoints() + 1);
        long capacity = CoreSatchelData.CoreRecord.capacityForTier(tier);
        long naturalCharge = capacity * naturalChargeBasisPoints / BASIS_POINTS;
        UUID coreId = UUID.nameUUIDFromBytes((
            "tensura_dungeon:monster_core:"
                + request.runSeed() + ":"
                + request.floor() + ":"
                + request.source().id() + ":"
                + request.firstClearMilestone() + ":"
                + request.encounterKey()
        ).getBytes(StandardCharsets.UTF_8));
        MonsterCoreTechniqueCatalog.Definition technique = MonsterCoreTechniqueCatalog.forEncounter(
            request.encounterKey(), request.source()
        );
        CoreSatchelData.CoreRecord core = new CoreSatchelData.CoreRecord(
            coreId,
            tier,
            naturalCharge,
            capacity,
            naturalCharge,
            technique.familyId(),
            technique.techniqueId()
        );
        return RecoveryRoll.recovered(
            guaranteed,
            recoveryRoll,
            tierRoll,
            chargeBandRoll,
            naturalChargeBasisPoints,
            core
        );
    }

    public static int currentTier(int floor) {
        if (floor < 1 || floor > 300) {
            throw new IllegalArgumentException("Dungeon floor must be between 1 and 300");
        }
        return Math.min(6, floor / 50 + 1);
    }

    static int milestoneTier(int floor) {
        return switch (floor) {
            case 50 -> 2;
            case 100 -> 3;
            case 150 -> 4;
            case 200 -> 5;
            case 250, 300 -> 6;
            default -> 0;
        };
    }

    private static int selectTier(int currentTier, int roll, StableRandom random) {
        if (currentTier == 1) return 1;
        if (currentTier == 2) return roll < 7_500 ? 2 : 1;
        if (currentTier == 3) {
            if (roll < 6_500) return 3;
            if (roll < 9_000) return 2;
            return 1;
        }
        if (roll < 6_000) return currentTier;
        if (roll < 8_500) return currentTier - 1;
        if (roll < 9_500) return currentTier - 2;
        return 1 + random.nextInt(currentTier - 3);
    }

    private static ChargeRange chargeRange(int roll) {
        if (roll < 3_000) return new ChargeRange(500, 1_999);
        if (roll < 6_500) return new ChargeRange(2_000, 4_499);
        if (roll < 8_700) return new ChargeRange(4_500, 6_999);
        if (roll < 9_700) return new ChargeRange(7_000, 8_999);
        return new ChargeRange(9_000, 10_000);
    }

    private static long seedFor(RecoveryRequest request) {
        long hash = 0xcbf29ce484222325L;
        byte[] bytes = request.encounterKey().getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) {
            hash ^= value & 0xffL;
            hash *= 0x100000001b3L;
        }
        hash ^= request.runSeed();
        hash ^= (long) request.floor() << 32;
        hash ^= (long) request.source().ordinal() << 56;
        if (request.firstClearMilestone()) hash ^= 0x6a09e667f3bcc909L;
        return mix64(hash);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public enum EncounterSource {
        ORDINARY_COMBAT("ordinary", 2_000, 1_200, 600, 300, 250, 200),
        ELITE("elite", 4_500, 3_000, 2_000, 1_200, 1_000, 800),
        REGULAR_FLOOR_BOSS("boss", 10_000, 7_500, 5_000, 3_500, 3_000, 2_500);

        private final String id;
        private final int[] recoveryChanceBasisPoints;

        EncounterSource(String id, int... recoveryChanceBasisPoints) {
            this.id = id;
            if (recoveryChanceBasisPoints.length != 6) {
                throw new IllegalArgumentException("Recovery source must define Tier I-VI chances");
            }
            this.recoveryChanceBasisPoints = recoveryChanceBasisPoints.clone();
        }

        public String id() {
            return id;
        }

        public int recoveryChanceBasisPoints(int currentTier) {
            if (currentTier < 1 || currentTier > 6) {
                throw new IllegalArgumentException("Current Core Tier must be between I and VI");
            }
            return recoveryChanceBasisPoints[currentTier - 1];
        }

        public static Optional<EncounterSource> parse(String value) {
            if (value == null || value.isBlank()) return Optional.empty();
            String normalized = value.toLowerCase(Locale.ROOT);
            for (EncounterSource source : values()) {
                if (source.id.equals(normalized)) return Optional.of(source);
            }
            return Optional.empty();
        }
    }

    public record RecoveryRequest(
        String encounterKey,
        int floor,
        EncounterSource source,
        boolean firstClearMilestone,
        long runSeed
    ) {
        public RecoveryRequest {
            if (encounterKey == null || encounterKey.isBlank() || encounterKey.length() > 128) {
                throw new IllegalArgumentException("Recovery encounter key must contain 1-128 characters");
            }
            currentTier(floor);
            if (source == null) {
                throw new IllegalArgumentException("Recovery encounter source is required");
            }
            if (firstClearMilestone && milestoneTier(floor) == 0) {
                throw new IllegalArgumentException(
                    "First-clear milestone guarantee is only valid on floors 50, 100, 150, 200, 250, or 300"
                );
            }
            if (firstClearMilestone && source != EncounterSource.REGULAR_FLOOR_BOSS) {
                throw new IllegalArgumentException(
                    "First-clear milestone guarantee requires the regular floor boss source"
                );
            }
        }
    }

    public record RecoveryRoll(
        boolean recovered,
        boolean guaranteed,
        int recoveryRollBasisPoints,
        int tierSelectionRollBasisPoints,
        int chargeBandRollBasisPoints,
        int naturalChargeBasisPoints,
        CoreSatchelData.CoreRecord core
    ) {
        public RecoveryRoll {
            if (recoveryRollBasisPoints < 0 || recoveryRollBasisPoints >= BASIS_POINTS) {
                throw new IllegalArgumentException("Recovery roll must be within 0-9,999 basis points");
            }
            if (recovered) {
                if (core == null) throw new IllegalArgumentException("Recovered roll requires an exact Core record");
                if (tierSelectionRollBasisPoints < 0 || tierSelectionRollBasisPoints >= BASIS_POINTS) {
                    throw new IllegalArgumentException("Tier roll must be within 0-9,999 basis points");
                }
                if (chargeBandRollBasisPoints < 0 || chargeBandRollBasisPoints >= BASIS_POINTS) {
                    throw new IllegalArgumentException("Charge-band roll must be within 0-9,999 basis points");
                }
                if (naturalChargeBasisPoints < 500 || naturalChargeBasisPoints > BASIS_POINTS) {
                    throw new IllegalArgumentException("Natural Charge must be within 5-100 percent");
                }
            } else if (guaranteed || core != null || tierSelectionRollBasisPoints != -1
                || chargeBandRollBasisPoints != -1 || naturalChargeBasisPoints != -1) {
                throw new IllegalArgumentException("Missed recovery roll cannot contain recovered Core state");
            }
        }

        private static RecoveryRoll miss(int recoveryRoll) {
            return new RecoveryRoll(false, false, recoveryRoll, -1, -1, -1, null);
        }

        private static RecoveryRoll recovered(
            boolean guaranteed,
            int recoveryRoll,
            int tierRoll,
            int chargeBandRoll,
            int naturalChargeBasisPoints,
            CoreSatchelData.CoreRecord core
        ) {
            return new RecoveryRoll(
                true,
                guaranteed,
                recoveryRoll,
                tierRoll,
                chargeBandRoll,
                naturalChargeBasisPoints,
                core
            );
        }
    }

    private record ChargeRange(int minimumBasisPoints, int maximumBasisPoints) {}

    private static final class StableRandom {
        private long state;

        private StableRandom(long seed) {
            state = seed;
        }

        private int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("Random bound must be positive");
            state += 0x9e3779b97f4a7c15L;
            return (int) Long.remainderUnsigned(mix64(state), bound);
        }
    }
}
