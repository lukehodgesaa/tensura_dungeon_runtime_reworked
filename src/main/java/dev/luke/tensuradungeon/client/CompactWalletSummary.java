package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import java.math.BigInteger;
import java.util.Locale;

final class CompactWalletSummary {
    private static final BigInteger HUNDRED = BigInteger.valueOf(100L);

    private CompactWalletSummary() {}

    static Value summarize(long bronze, long silver, long gold, long stellarGold) {
        BigInteger total = BigInteger.valueOf(Math.max(0L, bronze))
            .add(BigInteger.valueOf(Math.max(0L, silver)).multiply(
                BigInteger.valueOf(DungeonCurrencyPolicy.BRONZE_PER_SILVER)))
            .add(BigInteger.valueOf(Math.max(0L, gold)).multiply(
                BigInteger.valueOf(DungeonCurrencyPolicy.BRONZE_PER_GOLD)))
            .add(BigInteger.valueOf(Math.max(0L, stellarGold)).multiply(
                BigInteger.valueOf(DungeonCurrencyPolicy.BRONZE_PER_STELLAR)));
        if (total.compareTo(BigInteger.valueOf(DungeonCurrencyPolicy.BRONZE_PER_STELLAR)) >= 0) {
            return value(Denomination.STELLAR, total, DungeonCurrencyPolicy.BRONZE_PER_STELLAR);
        }
        if (total.compareTo(BigInteger.valueOf(DungeonCurrencyPolicy.BRONZE_PER_GOLD)) >= 0) {
            return value(Denomination.GOLD, total, DungeonCurrencyPolicy.BRONZE_PER_GOLD);
        }
        if (total.compareTo(BigInteger.valueOf(DungeonCurrencyPolicy.BRONZE_PER_SILVER)) >= 0) {
            return value(Denomination.SILVER, total, DungeonCurrencyPolicy.BRONZE_PER_SILVER);
        }
        return new Value(Denomination.BRONZE, total.toString());
    }

    private static Value value(Denomination denomination, BigInteger total, long unit) {
        return new Value(denomination, formatAmount(total, BigInteger.valueOf(unit)));
    }

    private static String formatAmount(BigInteger total, BigInteger unit) {
        BigInteger[] division = total.divideAndRemainder(unit);
        int hundredths = division[1].multiply(HUNDRED).divide(unit).intValue();
        if (hundredths == 0) return division[0].toString();
        if (hundredths % 10 == 0) return division[0] + "." + (hundredths / 10);
        return division[0] + "." + String.format(Locale.ROOT, "%02d", hundredths);
    }

    enum Denomination {
        BRONZE("BRONZE", "tensura:bronze_coin", 0xFFE3A15C),
        SILVER("SILVER", "tensura:silver_coin", 0xFFDCE5ED),
        GOLD("GOLD", "tensura:gold_coin", 0xFFFFD04A),
        STELLAR("STELLAR", "tensura:stellar_gold_coin", 0xFF63D9FF);

        private final String label;
        private final String itemId;
        private final int color;

        Denomination(String label, String itemId, int color) {
            this.label = label;
            this.itemId = itemId;
            this.color = color;
        }

        String label() { return label; }
        String itemId() { return itemId; }
        int color() { return color; }
    }

    record Value(Denomination denomination, String amount) {
        String displayText() {
            return amount + " " + denomination.label();
        }
    }
}
