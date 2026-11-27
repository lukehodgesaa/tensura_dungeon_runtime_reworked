package dev.luke.tensuradungeon.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record CoinValue(long bronze) implements Comparable<CoinValue> {
    public static final long BRONZE_PER_SILVER = 100L;
    public static final long BRONZE_PER_GOLD = 10_000L;
    public static final long BRONZE_PER_STELLAR_GOLD = 1_000_000L;

    public CoinValue {
        bronze = Math.max(0L, bronze);
    }

    public static CoinValue ofBronze(long bronze) {
        return new CoinValue(bronze);
    }

    public Breakdown breakdown() {
        return normalizeCoinValue(bronze);
    }

    public CoinValue add(CoinValue other) {
        return new CoinValue(addCoinValue(bronze, other == null ? 0L : other.bronze));
    }

    public CoinValue subtract(CoinValue other) {
        return new CoinValue(subtractCoinValue(bronze, other == null ? 0L : other.bronze));
    }

    public String format() {
        return formatCoinValue(bronze);
    }

    public String compact() {
        Breakdown value = breakdown();
        List<String> parts = new ArrayList<>(4);
        if (value.stellarGold() > 0) parts.add(value.stellarGold() + "SG");
        if (value.gold() > 0) parts.add(value.gold() + "G");
        if (value.silver() > 0) parts.add(value.silver() + "S");
        if (value.bronze() > 0 || parts.isEmpty()) parts.add(value.bronze() + "B");
        return String.join(" ", parts);
    }

    @Override
    public int compareTo(CoinValue other) {
        return compareCoinValue(bronze, other == null ? 0L : other.bronze);
    }

    public static Breakdown normalizeCoinValue(long bronze) {
        long remaining = Math.max(0L, bronze);
        long stellar = remaining / BRONZE_PER_STELLAR_GOLD;
        remaining %= BRONZE_PER_STELLAR_GOLD;
        int gold = (int) (remaining / BRONZE_PER_GOLD);
        remaining %= BRONZE_PER_GOLD;
        int silver = (int) (remaining / BRONZE_PER_SILVER);
        int bronzePart = (int) (remaining % BRONZE_PER_SILVER);
        return new Breakdown(stellar, gold, silver, bronzePart);
    }

    public static String formatCoinValue(long bronze) {
        Breakdown value = normalizeCoinValue(bronze);
        List<String> parts = new ArrayList<>(4);
        if (value.stellarGold() > 0) {
            parts.add(String.format(Locale.ROOT, "%,d Stellar Gold", value.stellarGold()));
        }
        if (value.gold() > 0) parts.add(value.gold() + " Gold");
        if (value.silver() > 0) parts.add(value.silver() + " Silver");
        if (value.bronze() > 0 || parts.isEmpty()) parts.add(value.bronze() + " Bronze");
        return String.join(", ", parts);
    }

    public static long addCoinValue(long leftBronze, long rightBronze) {
        long left = Math.max(0L, leftBronze);
        long right = Math.max(0L, rightBronze);
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public static long subtractCoinValue(long leftBronze, long rightBronze) {
        return Math.max(0L, Math.max(0L, leftBronze) - Math.max(0L, rightBronze));
    }

    public static int compareCoinValue(long leftBronze, long rightBronze) {
        return Long.compare(Math.max(0L, leftBronze), Math.max(0L, rightBronze));
    }

    public record Breakdown(long stellarGold, int gold, int silver, int bronze) {}
}
