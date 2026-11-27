package com.mineclawd.tensuradungeon.reward;

public final class DungeonCurrencyPolicy {
    public static final int BASIS_POINTS_PER_WHOLE = 10_000;
    public static final int MAX_COMPLETION_BONUS_BASIS_POINTS = 3_000;
    public static final String BRONZE_COIN_ITEM_ID = "tensura:bronze_coin";
    public static final String SILVER_COIN_ITEM_ID = "tensura:silver_coin";
    public static final String GOLD_COIN_ITEM_ID = "tensura:gold_coin";
    public static final String STELLAR_GOLD_COIN_ITEM_ID = "tensura:stellar_gold_coin";

    public static final long BRONZE_PER_SILVER = 100L;
    public static final long SILVER_PER_GOLD = 100L;
    public static final long GOLD_PER_STELLAR = 1_000L;
    public static final long BRONZE_PER_GOLD = BRONZE_PER_SILVER * SILVER_PER_GOLD;
    public static final long BRONZE_PER_STELLAR = BRONZE_PER_GOLD * GOLD_PER_STELLAR;
    public static final long SESSION_CAP_BRONZE = BRONZE_PER_STELLAR - 1L;

    private static final long[] TEN_FLOOR_BASE_GOLD = {
        0L, 1L, 3L, 7L, 15L,
        22L, 30L, 40L, 52L, 65L,
        75L, 90L, 105L, 122L, 140L,
        160L, 180L, 200L, 220L, 240L,
        265L, 290L, 315L, 340L, 365L,
        390L, 410L, 430L, 450L, 470L
    };

    private DungeonCurrencyPolicy() {
    }

    public static long baseRewardBronze(int floorNumber) {
        if (floorNumber < 1 || floorNumber > 300) {
            throw new IllegalArgumentException("Dungeon currency floor must be between 1 and 300: " + floorNumber);
        }
        if (floorNumber <= 10) {
            return BRONZE_PER_GOLD / 4L;
        }
        if (floorNumber == 300) {
            return 500L * BRONZE_PER_GOLD;
        }
        return TEN_FLOOR_BASE_GOLD[(floorNumber - 1) / 10] * BRONZE_PER_GOLD;
    }

    public static long capSessionBronze(long candidateBronze) {
        if (candidateBronze < 0L) {
            throw new IllegalArgumentException("Dungeon currency cannot be negative: " + candidateBronze);
        }
        return Math.min(candidateBronze, SESSION_CAP_BRONZE);
    }

    public static SessionPayout decomposeSession(long candidateBronze) {
        long canonicalBronze = capSessionBronze(candidateBronze);
        int goldCoins = Math.toIntExact(canonicalBronze / BRONZE_PER_GOLD);
        long afterGold = canonicalBronze % BRONZE_PER_GOLD;
        int silverCoins = Math.toIntExact(afterGold / BRONZE_PER_SILVER);
        int bronzeCoins = Math.toIntExact(afterGold % BRONZE_PER_SILVER);
        return new SessionPayout(canonicalBronze, bronzeCoins, silverCoins, goldCoins);
    }

    public static CompletionAward completionAward(
        int floorNumber,
        ClearRank clearRank,
        int modifierBonusBasisPoints
    ) {
        if (clearRank == null) {
            throw new IllegalArgumentException("Dungeon clear rank is required");
        }
        if (modifierBonusBasisPoints < 0) {
            throw new IllegalArgumentException("Modifier bonus cannot be negative: " + modifierBonusBasisPoints);
        }
        long baseBronze = baseRewardBronze(floorNumber);
        int combinedBonusBasisPoints = Math.min(
            MAX_COMPLETION_BONUS_BASIS_POINTS,
            clearRank.bonusBasisPoints() + Math.min(modifierBonusBasisPoints, MAX_COMPLETION_BONUS_BASIS_POINTS)
        );
        long bonusBronze = Math.multiplyExact(baseBronze, combinedBonusBasisPoints) / BASIS_POINTS_PER_WHOLE;
        return new CompletionAward(
            floorNumber,
            clearRank,
            baseBronze,
            clearRank.bonusBasisPoints(),
            modifierBonusBasisPoints,
            combinedBonusBasisPoints,
            bonusBronze,
            decomposeSession(Math.addExact(baseBronze, bonusBronze))
        );
    }

    public enum ClearRank {
        C(0),
        B(500),
        A(1_000),
        S(1_500),
        SS(2_000);

        private final int bonusBasisPoints;

        ClearRank(int bonusBasisPoints) {
            this.bonusBasisPoints = bonusBasisPoints;
        }

        public int bonusBasisPoints() {
            return bonusBasisPoints;
        }
    }

    public record CompletionAward(
        int floorNumber,
        ClearRank clearRank,
        long baseBronze,
        int rankBonusBasisPoints,
        int modifierBonusBasisPoints,
        int appliedCombinedBonusBasisPoints,
        long bonusBronze,
        SessionPayout payout
    ) {
    }

    public record SessionPayout(long canonicalBronze, int bronzeCoins, int silverCoins, int goldCoins) {
        public SessionPayout {
            if (canonicalBronze < 0L || canonicalBronze > SESSION_CAP_BRONZE) {
                throw new IllegalArgumentException("Canonical session currency is outside its cap: " + canonicalBronze);
            }
            if (bronzeCoins < 0 || bronzeCoins >= BRONZE_PER_SILVER) {
                throw new IllegalArgumentException("Bronze remainder is outside 0-99: " + bronzeCoins);
            }
            if (silverCoins < 0 || silverCoins >= SILVER_PER_GOLD) {
                throw new IllegalArgumentException("Silver remainder is outside 0-99: " + silverCoins);
            }
            if (goldCoins < 0 || goldCoins >= GOLD_PER_STELLAR) {
                throw new IllegalArgumentException("Gold payout is outside 0-999: " + goldCoins);
            }
            long recomposed = bronzeCoins
                + silverCoins * BRONZE_PER_SILVER
                + goldCoins * BRONZE_PER_GOLD;
            if (recomposed != canonicalBronze) {
                throw new IllegalArgumentException(
                    "Denominations do not equal canonical Bronze: expected " + canonicalBronze + ", got " + recomposed
                );
            }
        }
    }
}
