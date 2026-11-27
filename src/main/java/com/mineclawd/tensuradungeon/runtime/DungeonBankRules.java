package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import java.util.Objects;

public final class DungeonBankRules {
    public static final int STACK_SIZE = 64;
    public static final int MAXIMUM_SLOTS = 144;

    private DungeonBankRules() {}

    public static int occupiedSlots(Balance balance) {
        Objects.requireNonNull(balance, "Dungeon Bank balance");
        long slots = 0L;
        for (Currency currency : Currency.values()) {
            slots = Math.addExact(slots, ceilDiv(balance.amount(currency), STACK_SIZE));
        }
        return Math.toIntExact(slots);
    }

    public static long depositFee(long amount, int basisPoints) {
        return depositFeeBronze(amount, Currency.BRONZE, basisPoints);
    }

    public static int withdrawalFeeBasisPoints(int depositFeeBasisPoints) {
        if (depositFeeBasisPoints < 0 || depositFeeBasisPoints > 10_000) {
            throw new IllegalArgumentException("Dungeon Bank fee inputs are invalid");
        }
        return depositFeeBasisPoints / 2;
    }

    public static int withdrawalFeeBasisPoints(Grade grade) {
        return withdrawalFeeBasisPoints(
            Objects.requireNonNull(grade, "Dungeon Bank grade").depositFeeBasisPoints()
        );
    }

    public static long bronzeValue(Currency currency) {
        Objects.requireNonNull(currency, "Dungeon Bank currency");
        return switch (currency) {
            case BRONZE -> 1L;
            case SILVER -> DungeonCurrencyPolicy.BRONZE_PER_SILVER;
            case GOLD -> DungeonCurrencyPolicy.BRONZE_PER_GOLD;
            case STELLAR_GOLD -> DungeonCurrencyPolicy.BRONZE_PER_STELLAR;
        };
    }

    public static long depositFeeBronze(long amount, Currency currency, int basisPoints) {
        if (amount <= 0L || basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("Dungeon Bank deposit fee inputs are invalid");
        }
        long value = Math.multiplyExact(amount, bronzeValue(currency));
        return Math.max(1L, ceilDiv(Math.multiplyExact(value, basisPoints), 10_000L));
    }

    public static Balance depositFeeAsBalance(long amount, Currency currency, int basisPoints) {
        return decomposeCanonical(depositFeeBronze(amount, currency, basisPoints));
    }

    public static Balance depositNetAsBalance(long amount, Currency currency, int basisPoints) {
        long value = Math.multiplyExact(amount, bronzeValue(currency));
        long fee = depositFeeBronze(amount, currency, basisPoints);
        return decomposeCanonical(Math.subtractExact(value, fee));
    }

    public static long withdrawalFeeBronze(long amount, Currency currency, int basisPoints) {
        if (amount <= 0L || basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("Dungeon Bank withdrawal fee inputs are invalid");
        }
        long value = Math.multiplyExact(amount, bronzeValue(currency));
        if (value <= 1L || basisPoints == 0) return 0L;
        long fee = ceilDiv(Math.multiplyExact(value, basisPoints), 10_000L);
        return Math.min(value - 1L, Math.max(1L, fee));
    }

    public static Balance withdrawalFeeAsBalance(long amount, Currency currency, int basisPoints) {
        return decomposeCanonical(withdrawalFeeBronze(amount, currency, basisPoints));
    }

    public static Balance withdrawalNetAsBalance(long amount, Currency currency, int basisPoints) {
        long value = Math.multiplyExact(amount, bronzeValue(currency));
        long fee = withdrawalFeeBronze(amount, currency, basisPoints);
        return decomposeCanonical(Math.subtractExact(value, fee));
    }

    public static long canonicalValue(long bronze, long silver, long gold, long stellarGold) {
        if (bronze < 0L || silver < 0L || gold < 0L || stellarGold < 0L) {
            throw new IllegalArgumentException("Dungeon Bank canonical value cannot be negative");
        }
        return Math.addExact(
            bronze,
            Math.addExact(
                Math.multiplyExact(silver, DungeonCurrencyPolicy.BRONZE_PER_SILVER),
                Math.addExact(
                    Math.multiplyExact(gold, DungeonCurrencyPolicy.BRONZE_PER_GOLD),
                    Math.multiplyExact(stellarGold, DungeonCurrencyPolicy.BRONZE_PER_STELLAR)
                )
            )
        );
    }

    public static Balance decomposeCanonical(long bronzeValue) {
        if (bronzeValue < 0L) {
            throw new IllegalArgumentException("Dungeon Bank canonical value cannot be negative");
        }
        long stellar = bronzeValue / DungeonCurrencyPolicy.BRONZE_PER_STELLAR;
        long afterStellar = bronzeValue % DungeonCurrencyPolicy.BRONZE_PER_STELLAR;
        long gold = afterStellar / DungeonCurrencyPolicy.BRONZE_PER_GOLD;
        long afterGold = afterStellar % DungeonCurrencyPolicy.BRONZE_PER_GOLD;
        long silver = afterGold / DungeonCurrencyPolicy.BRONZE_PER_SILVER;
        long bronze = afterGold % DungeonCurrencyPolicy.BRONZE_PER_SILVER;
        return new Balance(bronze, silver, gold, stellar);
    }

    private static long ceilDiv(long value, long divisor) {
        if (value <= 0L) return 0L;
        return value / divisor + (value % divisor == 0L ? 0L : 1L);
    }

    public enum Currency {
        BRONZE(DungeonCurrencyPolicy.BRONZE_COIN_ITEM_ID),
        SILVER(DungeonCurrencyPolicy.SILVER_COIN_ITEM_ID),
        GOLD(DungeonCurrencyPolicy.GOLD_COIN_ITEM_ID),
        STELLAR_GOLD(DungeonCurrencyPolicy.STELLAR_GOLD_COIN_ITEM_ID);

        private final String itemId;

        Currency(String itemId) {
            this.itemId = itemId;
        }

        public String itemId() {
            return itemId;
        }
    }

    public enum Grade {
        NORMAL("NORMAL", "GRADE I", 18, 100, gold(20)),
        SPECIAL("SPECIAL", "GRADE II", 27, 200, gold(80)),
        RARE("RARE", "GRADE III", 36, 300, gold(250)),
        UNIQUE("UNIQUE", "GRADE IV", 45, 400, gold(750)),
        LEGEND("LEGEND", "GRADE V", 54, 500, stellar(2)),
        MYTHIC("MYTHIC", "GRADE VI", 63, 600, stellar(5)),
        GENESIS("GENESIS I", "GRADE VII", 72, 700, stellar(10)),
        GENESIS_II("GENESIS II", "GRADE VIII", 81, 800, stellar(20)),
        GENESIS_III("GENESIS III", "GRADE IX", 90, 900, stellar(40)),
        GENESIS_IV("GENESIS IV", "GRADE X", 99, 1_000, stellar(75)),
        GENESIS_V("GENESIS V", "GRADE XI", 108, 1_100, stellar(125)),
        GENESIS_VI("GENESIS VI", "GRADE XII", 117, 1_200, stellar(200)),
        GENESIS_VII("GENESIS VII", "GRADE XIII", 126, 1_300, stellar(300)),
        GENESIS_VIII("GENESIS VIII", "GRADE XIV", 135, 1_400, stellar(500)),
        GENESIS_IX("GENESIS IX", "GRADE XV", 144, 1_500, 0L);

        private final String label;
        private final String rankLabel;
        private final int slots;
        private final int depositFeeBasisPoints;
        private final long nextUpgradeCostBronze;

        Grade(
            String label,
            String rankLabel,
            int slots,
            int depositFeeBasisPoints,
            long nextUpgradeCostBronze
        ) {
            this.label = label;
            this.rankLabel = rankLabel;
            this.slots = slots;
            this.depositFeeBasisPoints = depositFeeBasisPoints;
            this.nextUpgradeCostBronze = nextUpgradeCostBronze;
        }

        public String label() {
            return label;
        }

        public String rankLabel() {
            return rankLabel;
        }

        public int slots() {
            return slots;
        }

        public int depositFeeBasisPoints() {
            return depositFeeBasisPoints;
        }

        public int withdrawalFeeBasisPoints() {
            return DungeonBankRules.withdrawalFeeBasisPoints(depositFeeBasisPoints);
        }

        public boolean hasNext() {
            return ordinal() + 1 < values().length;
        }

        public Grade next() {
            return hasNext() ? values()[ordinal() + 1] : this;
        }

        public long nextUpgradeCostBronze() {
            return nextUpgradeCostBronze;
        }

        private static long gold(long amount) {
            return Math.multiplyExact(amount, DungeonCurrencyPolicy.BRONZE_PER_GOLD);
        }

        private static long stellar(long amount) {
            return Math.multiplyExact(amount, DungeonCurrencyPolicy.BRONZE_PER_STELLAR);
        }
    }

    public record Balance(long bronze, long silver, long gold, long stellarGold) {
        public Balance {
            if (bronze < 0L || silver < 0L || gold < 0L || stellarGold < 0L) {
                throw new IllegalArgumentException("Dungeon Bank balances cannot be negative");
            }
        }

        public static Balance empty() {
            return new Balance(0L, 0L, 0L, 0L);
        }

        public long amount(Currency currency) {
            return switch (Objects.requireNonNull(currency, "Dungeon Bank currency")) {
                case BRONZE -> bronze;
                case SILVER -> silver;
                case GOLD -> gold;
                case STELLAR_GOLD -> stellarGold;
            };
        }

        public Balance with(Currency currency, long amount) {
            if (amount < 0L) {
                throw new IllegalArgumentException("Dungeon Bank balance cannot be negative");
            }
            return switch (Objects.requireNonNull(currency, "Dungeon Bank currency")) {
                case BRONZE -> new Balance(amount, silver, gold, stellarGold);
                case SILVER -> new Balance(bronze, amount, gold, stellarGold);
                case GOLD -> new Balance(bronze, silver, amount, stellarGold);
                case STELLAR_GOLD -> new Balance(bronze, silver, gold, amount);
            };
        }

        public Balance plus(Currency currency, long amount) {
            if (amount < 0L) {
                throw new IllegalArgumentException("Dungeon Bank credit cannot be negative");
            }
            return with(currency, Math.addExact(amount(currency), amount));
        }

        public Balance plus(Balance credit) {
            Objects.requireNonNull(credit, "Dungeon Bank credit");
            return plus(Currency.BRONZE, credit.bronze())
                .plus(Currency.SILVER, credit.silver())
                .plus(Currency.GOLD, credit.gold())
                .plus(Currency.STELLAR_GOLD, credit.stellarGold());
        }

        public Balance minus(Currency currency, long amount) {
            if (amount < 0L || amount > amount(currency)) {
                throw new IllegalArgumentException("Dungeon Bank debit exceeds its balance");
            }
            return with(currency, amount(currency) - amount);
        }
    }
}
