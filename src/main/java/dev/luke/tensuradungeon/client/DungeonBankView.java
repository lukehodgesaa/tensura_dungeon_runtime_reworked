package dev.luke.tensuradungeon.client;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record DungeonBankView(
    DungeonShopView.Wallet inventoryWallet,
    Grade grade,
    int unlockedSlots,
    int maximumSlots,
    List<Slot> slots,
    int depositFeeBasisPoints,
    List<DepositQuote> depositQuotes,
    Upgrade upgrade,
    boolean preview,
    String statusMessage,
    long revision
) {
    public static final int SLOTS_PER_PAGE = 45;
    public static final int PRODUCTION_MAXIMUM_SLOTS = 144;

    public DungeonBankView {
        inventoryWallet = inventoryWallet == null
            ? DungeonShopView.Wallet.empty() : inventoryWallet;
        grade = Objects.requireNonNull(grade, "Dungeon Bank grade");
        maximumSlots = Math.max(1, Math.min(PRODUCTION_MAXIMUM_SLOTS, maximumSlots));
        unlockedSlots = Math.max(0, Math.min(maximumSlots, unlockedSlots));
        slots = slots == null ? List.of() : List.copyOf(slots);
        depositFeeBasisPoints = Math.max(0, Math.min(10_000, depositFeeBasisPoints));
        depositQuotes = depositQuotes == null ? List.of() : List.copyOf(depositQuotes);
        upgrade = upgrade == null ? Upgrade.none() : upgrade;
        statusMessage = bounded(statusMessage, 160);
        if (revision < 0L) {
            throw new IllegalArgumentException("Dungeon Bank revision cannot be negative");
        }
        if (slots.size() > maximumSlots) {
            throw new IllegalArgumentException("Dungeon Bank has more occupied slots than its grid");
        }
        Set<Integer> indices = new HashSet<>();
        for (Slot slot : slots) {
            Objects.requireNonNull(slot, "Dungeon Bank slot");
            if (slot.index() >= maximumSlots) {
                throw new IllegalArgumentException("Dungeon Bank slot exceeds the grid");
            }
            if (slot.index() >= unlockedSlots) {
                throw new IllegalArgumentException("Dungeon Bank cannot occupy a locked slot");
            }
            if (!indices.add(slot.index())) {
                throw new IllegalArgumentException("Dungeon Bank contains duplicate slot indices");
            }
        }
        Set<String> quoteIds = new HashSet<>();
        for (DepositQuote quote : depositQuotes) {
            Objects.requireNonNull(quote, "Dungeon Bank deposit quote");
            if (!quoteIds.add(quote.quoteId())) {
                throw new IllegalArgumentException("Dungeon Bank contains duplicate deposit quote IDs");
            }
        }
        if (upgrade.offered() && upgrade.resultSlots() <= unlockedSlots) {
            throw new IllegalArgumentException("Dungeon Bank upgrade must add slots");
        }
        if (upgrade.offered() && upgrade.resultSlots() > maximumSlots) {
            throw new IllegalArgumentException("Dungeon Bank upgrade exceeds the grid");
        }
    }

    public static DungeonBankView visualPreview() {
        return visualPreview(
            new DungeonShopView.Wallet(64L, 0L, 0L, 0L),
            List.of(new DepositQuote(
                "preview:inventory_0_bronze_stack",
                Currency.BRONZE,
                64L,
                64L,
                new DungeonShopView.Wallet(2L, 0L, 0L, 0L),
                new DungeonShopView.Wallet(62L, 0L, 0L, 0L),
                0L,
                1,
                true,
                ""
            ))
        );
    }

    static DungeonBankView visualPreview(
        DungeonShopView.Wallet eligibleWallet,
        List<DepositQuote> eligibleQuotes
    ) {
        return new DungeonBankView(
            eligibleWallet,
            Grade.SPECIAL,
            Grade.SPECIAL.defaultSlots(),
            PRODUCTION_MAXIMUM_SLOTS,
            List.of(
                new Slot(0, Currency.BRONZE, 64L),
                new Slot(1, Currency.SILVER, 64L),
                new Slot(2, Currency.GOLD, 64L),
                new Slot(3, Currency.GOLD, 64L),
                new Slot(4, Currency.STELLAR_GOLD, 64L),
                new Slot(5, Currency.SILVER, 64L),
                new Slot(6, Currency.BRONZE, 64L),
                new Slot(7, Currency.GOLD, 64L)
            ),
            200,
            eligibleQuotes,
            new Upgrade(
                true,
                Grade.RARE,
                Grade.RARE.defaultSlots(),
                Grade.RARE.defaultDepositFeeBasisPoints(),
                new DungeonShopView.Wallet(0L, 0L, 80L, 0L),
                "preview:upgrade:rare",
                true,
                ""
            ),
            true,
            "UI PREVIEW - BANK TRANSACTIONS ARE NOT CONNECTED",
            0L
        );
    }

    public Slot slotAt(int index) {
        return slots.stream().filter(slot -> slot.index() == index).findFirst().orElse(null);
    }

    public int usedSlots() {
        return slots.size();
    }

    public int pageCount() {
        return Math.max(1, (maximumSlots + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
    }

    public DepositQuote depositQuoteFor(Currency currency) {
        if (currency == null) return null;
        return depositQuotes.stream()
            .filter(quote -> quote.currency() == currency)
            .findFirst()
            .orElse(null);
    }

    public String feeLabel() {
        if (depositFeeBasisPoints == 0) return "NO DEPOSIT FEE";
        int whole = depositFeeBasisPoints / 100;
        int fraction = depositFeeBasisPoints % 100;
        return fraction == 0
            ? "DEPOSIT FEE " + whole + "%"
            : "DEPOSIT FEE " + whole + "."
                + String.format(Locale.ROOT, "%02d", fraction) + "%";
    }

    public enum Grade {
        NORMAL("NORMAL", "GRADE I", 18, 100),
        SPECIAL("SPECIAL", "GRADE II", 27, 200),
        RARE("RARE", "GRADE III", 36, 300),
        UNIQUE("UNIQUE", "GRADE IV", 45, 400),
        LEGEND("LEGEND", "GRADE V", 54, 500),
        MYTHIC("MYTHIC", "GRADE VI", 63, 600),
        GENESIS("GENESIS I", "GRADE VII", 72, 700),
        GENESIS_II("GENESIS II", "GRADE VIII", 81, 800),
        GENESIS_III("GENESIS III", "GRADE IX", 90, 900),
        GENESIS_IV("GENESIS IV", "GRADE X", 99, 1_000),
        GENESIS_V("GENESIS V", "GRADE XI", 108, 1_100),
        GENESIS_VI("GENESIS VI", "GRADE XII", 117, 1_200),
        GENESIS_VII("GENESIS VII", "GRADE XIII", 126, 1_300),
        GENESIS_VIII("GENESIS VIII", "GRADE XIV", 135, 1_400),
        GENESIS_IX("GENESIS IX", "GRADE XV", 144, 1_500);

        private final String label;
        private final String rankLabel;
        private final int defaultSlots;
        private final int defaultDepositFeeBasisPoints;

        Grade(
            String label,
            String rankLabel,
            int defaultSlots,
            int defaultDepositFeeBasisPoints
        ) {
            this.label = label;
            this.rankLabel = rankLabel;
            this.defaultSlots = defaultSlots;
            this.defaultDepositFeeBasisPoints = defaultDepositFeeBasisPoints;
        }

        public String label() {
            return label;
        }

        public String rankLabel() {
            return rankLabel;
        }

        public int defaultSlots() {
            return defaultSlots;
        }

        public int defaultDepositFeeBasisPoints() {
            return defaultDepositFeeBasisPoints;
        }

        public String displayLabel() {
            return rankLabel + "  /  " + label;
        }
    }

    public enum Currency {
        BRONZE("BRONZE", "tensura:bronze_coin", 0xffe3a15c),
        SILVER("SILVER", "tensura:silver_coin", 0xffdce5ed),
        GOLD("GOLD", "tensura:gold_coin", 0xffffd04a),
        STELLAR_GOLD("STELLAR GOLD", "tensura:stellar_gold_coin", 0xffb26cff);

        private final String label;
        private final String itemId;
        private final int color;

        Currency(String label, String itemId, int color) {
            this.label = label;
            this.itemId = itemId;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public String itemId() {
            return itemId;
        }

        public int color() {
            return color;
        }

        public long bronzeValue() {
            return switch (this) {
                case BRONZE -> 1L;
                case SILVER -> 100L;
                case GOLD -> 10_000L;
                case STELLAR_GOLD -> 10_000_000L;
            };
        }
    }

    public record Slot(
        int index,
        Currency currency,
        long amount,
        String withdrawalQuoteId,
        DungeonShopView.Wallet fee,
        DungeonShopView.Wallet playerReceives,
        boolean withdrawable,
        String failureReason
    ) {
        public Slot(int index, Currency currency, long amount) {
            this(
                index,
                currency,
                amount,
                "preview:withdraw:" + index,
                DungeonShopView.Wallet.empty(),
                DungeonBankView.decompose(currency.bronzeValue() * amount),
                false,
                "UI PREVIEW"
            );
        }

        public Slot(
            int index,
            Currency currency,
            long amount,
            String withdrawalQuoteId,
            boolean withdrawable,
            String failureReason
        ) {
            this(
                index,
                currency,
                amount,
                withdrawalQuoteId,
                DungeonShopView.Wallet.empty(),
                DungeonBankView.decompose(currency.bronzeValue() * amount),
                withdrawable,
                failureReason
            );
        }

        public Slot {
            if (index < 0 || index >= PRODUCTION_MAXIMUM_SLOTS) {
                throw new IllegalArgumentException("Dungeon Bank slot index is invalid");
            }
            currency = Objects.requireNonNull(currency, "Dungeon Bank slot currency");
            if (amount <= 0L || amount > 64L) {
                throw new IllegalArgumentException(
                    "Dungeon Bank occupied slot amount must be between 1 and 64"
                );
            }
            fee = fee == null ? DungeonShopView.Wallet.empty() : fee;
            playerReceives = playerReceives == null
                ? DungeonShopView.Wallet.empty() : playerReceives;
            withdrawalQuoteId = bounded(withdrawalQuoteId, 96);
            if (withdrawalQuoteId.isBlank()) {
                throw new IllegalArgumentException("Dungeon Bank withdrawal quote cannot be blank");
            }
            failureReason = bounded(failureReason, 96);
            if (!withdrawable && failureReason.isBlank()) {
                throw new IllegalArgumentException(
                    "Unavailable Dungeon Bank withdrawal requires a reason"
                );
            }
        }
    }

    public record DepositQuote(
        String quoteId,
        Currency currency,
        long availableAmount,
        long depositAmount,
        DungeonShopView.Wallet fee,
        DungeonShopView.Wallet bankReceives,
        long walletRemaining,
        int requiredSlots,
        boolean depositable,
        String failureReason
    ) {
        public DepositQuote {
            quoteId = bounded(quoteId, 96);
            if (quoteId.isBlank()) {
                throw new IllegalArgumentException("Dungeon Bank deposit quote ID cannot be blank");
            }
            currency = Objects.requireNonNull(currency, "Dungeon Bank deposit quote currency");
            if (availableAmount < 0L || depositAmount <= 0L || depositAmount > 64L) {
                throw new IllegalArgumentException("Dungeon Bank deposit quote amount is invalid");
            }
            fee = fee == null ? DungeonShopView.Wallet.empty() : fee;
            bankReceives = bankReceives == null
                ? DungeonShopView.Wallet.empty() : bankReceives;
            long feeValue = canonicalValue(fee);
            long receiveValue = canonicalValue(bankReceives);
            long depositValue = Math.multiplyExact(
                depositAmount, currency.bronzeValue()
            );
            if (depositAmount > availableAmount || feeValue <= 0L
                    || feeValue > depositValue) {
                throw new IllegalArgumentException("Dungeon Bank deposit quote fee is invalid");
            }
            if (receiveValue != depositValue - feeValue) {
                throw new IllegalArgumentException("Dungeon Bank deposit quote net amount is invalid");
            }
            if (walletRemaining != availableAmount - depositAmount) {
                throw new IllegalArgumentException(
                    "Dungeon Bank deposit quote wallet remainder is invalid"
                );
            }
            if (requiredSlots < 0 || (depositable && receiveValue == 0L)) {
                throw new IllegalArgumentException("Dungeon Bank deposit quote net value is invalid");
            }
            failureReason = bounded(failureReason, 96);
            if (!depositable && failureReason.isBlank()) {
                throw new IllegalArgumentException(
                    "Unavailable Dungeon Bank deposit quote requires a reason"
                );
            }
        }
    }

    public static long canonicalValue(DungeonShopView.Wallet wallet) {
        Objects.requireNonNull(wallet, "Dungeon Bank wallet");
        return wallet.bronze()
            + wallet.silver() * 100L
            + wallet.gold() * 10_000L
            + wallet.stellarGold() * 10_000_000L;
    }

    public static DungeonShopView.Wallet decompose(long bronzeValue) {
        if (bronzeValue < 0L) {
            throw new IllegalArgumentException("Dungeon Bank bronze value cannot be negative");
        }
        long stellar = bronzeValue / 10_000_000L;
        long afterStellar = bronzeValue % 10_000_000L;
        long gold = afterStellar / 10_000L;
        long afterGold = afterStellar % 10_000L;
        long silver = afterGold / 100L;
        long bronze = afterGold % 100L;
        return new DungeonShopView.Wallet(bronze, silver, gold, stellar);
    }

    public static String walletText(DungeonShopView.Wallet wallet) {
        Objects.requireNonNull(wallet, "Dungeon Bank wallet");
        StringBuilder text = new StringBuilder();
        append(text, wallet.stellarGold(), "STELLAR");
        append(text, wallet.gold(), "GOLD");
        append(text, wallet.silver(), "SILVER");
        append(text, wallet.bronze(), "BRONZE");
        return text.length() == 0 ? "0 BRONZE" : text.toString();
    }

    private static void append(StringBuilder text, long amount, String label) {
        if (amount <= 0L) return;
        if (text.length() > 0) text.append(" + ");
        text.append(amount).append(' ').append(label);
    }

    public record Upgrade(
        boolean offered,
        Grade targetGrade,
        int resultSlots,
        int resultDepositFeeBasisPoints,
        DungeonShopView.Wallet cost,
        String quoteId,
        boolean affordable,
        String unavailableReason
    ) {
        public Upgrade(
            boolean offered,
            Grade targetGrade,
            int resultSlots,
            int resultDepositFeeBasisPoints,
            DungeonShopView.Wallet cost,
            boolean affordable,
            String unavailableReason
        ) {
            this(
                offered,
                targetGrade,
                resultSlots,
                resultDepositFeeBasisPoints,
                cost,
                offered ? "preview:upgrade:" + targetGrade.name().toLowerCase(Locale.ROOT) : "",
                affordable,
                unavailableReason
            );
        }

        public Upgrade {
            targetGrade = Objects.requireNonNull(targetGrade, "Dungeon Bank target grade");
            resultSlots = Math.max(0, Math.min(PRODUCTION_MAXIMUM_SLOTS, resultSlots));
            resultDepositFeeBasisPoints = Math.max(
                0,
                Math.min(10_000, resultDepositFeeBasisPoints)
            );
            cost = cost == null ? DungeonShopView.Wallet.empty() : cost;
            quoteId = bounded(quoteId, 96);
            if (offered && quoteId.isBlank()) {
                throw new IllegalArgumentException("Offered Dungeon Bank upgrade requires a quote ID");
            }
            unavailableReason = bounded(unavailableReason, 96);
        }

        public static Upgrade none() {
            return new Upgrade(
                false,
                Grade.GENESIS_IX,
                Grade.GENESIS_IX.defaultSlots(),
                Grade.GENESIS_IX.defaultDepositFeeBasisPoints(),
                DungeonShopView.Wallet.empty(),
                "",
                false,
                "MAXIMUM BANK GRADE"
            );
        }
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
