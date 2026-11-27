package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotPayload;
import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import java.util.List;
import java.util.Locale;

public final class KurobeForgeRules {
    private static final long[] CONDUIT_PRICE_GOLD = {5L, 80L, 315L, 800L, 1_560L, 2_575L};
    private static final long[] CERTIFICATION_PRICE_GOLD = {0L, 250L, 840L, 2_000L, 3_750L, 6_900L};
    private static final long[] PROMOTION_PRICE_GOLD = {0L, 125L, 420L, 1_000L, 1_875L, 3_450L};
    private static final int[] REQUIRED_FLOOR = {1, 50, 100, 150, 200, 250};
    private static final long[] SOCKET_PRICE_BRONZE = {
        25_000L, 200_000L, 500_000L, 1_000_000L, 1_550_000L, 2_150_000L
    };
    private static final long[] REMOVAL_PRICE_GOLD = {5L, 40L, 105L, 200L, 315L, 430L};
    private static final long[] FULL_RECHARGE_PRICE_BRONZE = {
        75_000L, 920_000L, 3_150_000L, 8_900_000L, 18_750_000L, 38_650_000L
    };
    private static final long[][] CAPACITY = {
        {0L, 2_500L, 5_000L, 7_500L, 10_000L, 12_500L, 15_000L},
        {15_000L, 20_833L, 26_667L, 32_500L, 38_333L, 44_167L, 50_000L},
        {50_000L, 66_667L, 83_333L, 100_000L, 116_667L, 133_333L, 150_000L},
        {150_000L, 191_667L, 233_333L, 275_000L, 316_667L, 358_333L, 400_000L},
        {400_000L, 500_000L, 600_000L, 700_000L, 800_000L, 900_000L, 1_000_000L},
        {1_000_000L, 1_250_000L, 1_500_000L, 1_750_000L, 2_000_000L, 2_250_000L, 2_500_000L}
    };

    private KurobeForgeRules() {}

    public static long conduitPriceBronze(KurobeForgeSnapshotPayload.Grade grade) {
        return gold(CONDUIT_PRICE_GOLD[requiredGrade(grade).ordinal()]);
    }

    public static String conduitPriceLabel(KurobeForgeSnapshotPayload.Grade grade) {
        return currencyLabel(conduitPriceBronze(grade));
    }

    public static long certificationPriceBronze(KurobeForgeSnapshotPayload.Grade target) {
        return gold(CERTIFICATION_PRICE_GOLD[requiredGrade(target).ordinal()]);
    }

    public static long promotionPriceBronze(KurobeForgeSnapshotPayload.Grade target) {
        return gold(PROMOTION_PRICE_GOLD[requiredGrade(target).ordinal()]);
    }

    public static int requiredFloor(KurobeForgeSnapshotPayload.Grade grade) {
        return REQUIRED_FLOOR[requiredGrade(grade).ordinal()];
    }

    public static List<String> certificationMaterials(KurobeForgeSnapshotPayload.Grade target) {
        return switch (requiredGrade(target)) {
            case NORMAL -> List.of();
            case SPECIAL -> List.of("tensura:low_magisteel_ingot");
            case RARE -> List.of("tensura:high_magisteel_ingot");
            case UNIQUE -> List.of("tensura:mithril_ingot");
            case LEGEND -> List.of(
                "tensura:orichalcum_ingot",
                "tensura:pure_magisteel_ingot",
                "tensura:adamantite_ingot"
            );
            case MYTHIC -> List.of("tensura:hihiirokane_ingot");
            case GENESIS -> throw new IllegalArgumentException("Genesis certification is reserved");
        };
    }

    public static String certificationMaterialLabel(KurobeForgeSnapshotPayload.Grade target) {
        return switch (requiredGrade(target)) {
            case NORMAL -> "NO CATALYST";
            case SPECIAL -> "1 LOW MAGISTEEL";
            case RARE -> "1 HIGH MAGISTEEL";
            case UNIQUE -> "1 MITHRIL";
            case LEGEND -> "1 ORICHALCUM + 1 PURE MAGISTEEL + 1 ADAMANTITE";
            case MYTHIC -> "1 HIHIIROKANE";
            case GENESIS -> "GENESIS CERTIFICATION IS RESERVED";
        };
    }

    public static long socketPriceBronze(int tier) {
        return SOCKET_PRICE_BRONZE[requiredTier(tier) - 1];
    }

    public static long removalPriceBronze(int tier) {
        return gold(REMOVAL_PRICE_GOLD[requiredTier(tier) - 1]);
    }

    public static long rechargePriceBronze(int tier, long totalCharge, long capacity) {
        requiredTier(tier);
        if (capacity <= 0L || totalCharge < 0L || totalCharge > capacity) {
            throw new IllegalArgumentException("Kurobe recharge state is invalid");
        }
        long missing = capacity - totalCharge;
        if (missing == 0L) return 0L;
        long full = FULL_RECHARGE_PRICE_BRONZE[tier - 1];
        if (missing == capacity) return full;
        long labor = full / 10L;
        long energy = full - labor;
        long numerator = Math.multiplyExact(energy, missing);
        return Math.addExact(labor, ceilDiv(numerator, capacity));
    }

    public static long accessibleCapacity(KurobeForgeSnapshotPayload.Grade grade, int rank) {
        if (rank < 0 || rank > 6) throw new IllegalArgumentException("Conduit Rank must be 0-6");
        return CAPACITY[requiredGrade(grade).ordinal()][rank];
    }

    public static int maximumCoreTier(KurobeForgeSnapshotPayload.Grade grade) {
        return requiredGrade(grade).ordinal() + 1;
    }

    public static boolean isActiveGrade(KurobeForgeSnapshotPayload.Grade grade) {
        return grade != null && grade != KurobeForgeSnapshotPayload.Grade.GENESIS;
    }

    public static long burstOutput(
        KurobeForgeSnapshotPayload.Grade grade,
        int rank,
        int coreTier
    ) {
        requiredTier(coreTier);
        long accessible = accessibleCapacity(grade, rank);
        return Math.min(CoreSatchelData.CoreRecord.capacityForTier(coreTier), accessible / 4L);
    }

    public static long flowOutput(
        KurobeForgeSnapshotPayload.Grade grade,
        int rank,
        int coreTier
    ) {
        requiredTier(coreTier);
        long accessible = accessibleCapacity(grade, rank);
        return Math.min(CoreSatchelData.CoreRecord.capacityForTier(coreTier), accessible / 100L);
    }

    public static KurobeForgeSnapshotPayload.Grade nextGrade(
        KurobeForgeSnapshotPayload.Grade grade
    ) {
        if (grade == KurobeForgeSnapshotPayload.Grade.GENESIS) return null;
        KurobeForgeSnapshotPayload.Grade checked = requiredGrade(grade);
        int next = checked.ordinal() + 1;
        return next < KurobeForgeSnapshotPayload.Grade.values().length
            ? KurobeForgeSnapshotPayload.Grade.values()[next]
            : null;
    }

    public static String currencyLabel(long bronzeValue) {
        if (bronzeValue < 0L) throw new IllegalArgumentException("Currency value cannot be negative");
        if (bronzeValue == 0L) return "FREE";
        long gold = bronzeValue / DungeonCurrencyPolicy.BRONZE_PER_GOLD;
        long afterGold = bronzeValue % DungeonCurrencyPolicy.BRONZE_PER_GOLD;
        long silver = afterGold / DungeonCurrencyPolicy.BRONZE_PER_SILVER;
        long bronze = afterGold % DungeonCurrencyPolicy.BRONZE_PER_SILVER;
        StringBuilder label = new StringBuilder();
        if (gold > 0L) label.append(String.format(Locale.ROOT, "%,d GOLD", gold));
        if (silver > 0L) {
            if (!label.isEmpty()) label.append("  ");
            label.append(String.format(Locale.ROOT, "%,d SILVER", silver));
        }
        if (bronze > 0L) {
            if (!label.isEmpty()) label.append("  ");
            label.append(String.format(Locale.ROOT, "%,d BRONZE", bronze));
        }
        return label.toString();
    }

    public static List<KurobeForgeSnapshotPayload.ServiceState> noWeaponServices() {
        return java.util.Arrays.stream(KurobeForgeSnapshotPayload.Service.values())
            .map(service -> unavailable(service, "SELECT AN ELIGIBLE WEAPON"))
            .toList();
    }

    public static KurobeForgeSnapshotPayload.ServiceState unavailable(
        KurobeForgeSnapshotPayload.Service service,
        String reason
    ) {
        return new KurobeForgeSnapshotPayload.ServiceState(service, false, "NOT AVAILABLE", reason);
    }

    public static KurobeForgeSnapshotPayload.ServiceState available(
        KurobeForgeSnapshotPayload.Service service,
        String cost
    ) {
        return new KurobeForgeSnapshotPayload.ServiceState(service, true, cost, "");
    }

    private static long gold(long amount) {
        return Math.multiplyExact(amount, DungeonCurrencyPolicy.BRONZE_PER_GOLD);
    }

    private static long ceilDiv(long value, long divisor) {
        return value / divisor + (value % divisor == 0L ? 0L : 1L);
    }

    private static int requiredTier(int tier) {
        if (tier < 1 || tier > 6) throw new IllegalArgumentException("Core Tier must be I-VI");
        return tier;
    }

    private static KurobeForgeSnapshotPayload.Grade requiredGrade(
        KurobeForgeSnapshotPayload.Grade grade
    ) {
        if (grade == null) throw new IllegalArgumentException("Equipment Grade is required");
        if (grade == KurobeForgeSnapshotPayload.Grade.GENESIS) {
            throw new IllegalArgumentException("Genesis Equipment Grade is reserved");
        }
        return grade;
    }
}
