package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.DungeonBankRules;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonBankSnapshotPayload(
    int schemaVersion,
    long revision,
    long requestId,
    boolean openRequested,
    DungeonBankRules.Grade grade,
    Wallet moneyOnHand,
    Wallet bankedFunds,
    List<DepositQuote> depositQuotes,
    List<Slot> slots,
    Upgrade upgrade,
    Status status
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 3;
    public static final int MAX_DEPOSIT_QUOTES = 37;
    public static final Type<DungeonBankSnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_bank_snapshot")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonBankSnapshotPayload> STREAM_CODEC =
        StreamCodec.of(DungeonBankSnapshotPayload::write, DungeonBankSnapshotPayload::read);

    public DungeonBankSnapshotPayload {
        if (schemaVersion < 0 || revision < 0L || requestId < 0L) {
            throw new IllegalArgumentException("Dungeon Bank snapshot identity is invalid");
        }
        grade = Objects.requireNonNull(grade, "Dungeon Bank snapshot grade");
        moneyOnHand = moneyOnHand == null ? Wallet.empty() : moneyOnHand;
        bankedFunds = bankedFunds == null ? Wallet.empty() : bankedFunds;
        depositQuotes = depositQuotes == null ? List.of() : List.copyOf(depositQuotes);
        slots = slots == null ? List.of() : List.copyOf(slots);
        upgrade = upgrade == null ? Upgrade.none() : upgrade;
        status = status == null ? Status.ready() : status;
        if (depositQuotes.size() > MAX_DEPOSIT_QUOTES) {
            throw new IllegalArgumentException("Dungeon Bank snapshot has too many deposit quotes");
        }
        if (slots.size() > DungeonBankRules.MAXIMUM_SLOTS) {
            throw new IllegalArgumentException("Dungeon Bank snapshot has too many slots");
        }
        Set<String> depositIds = new HashSet<>();
        for (DepositQuote quote : depositQuotes) {
            if (quote == null || !depositIds.add(quote.quoteId())) {
                throw new IllegalArgumentException("Dungeon Bank deposit quote IDs must be unique");
            }
        }
        Set<Integer> slotIndices = new HashSet<>();
        Set<String> withdrawalIds = new HashSet<>();
        Map<DungeonBankRules.Currency, Long> packedTotals =
            new EnumMap<>(DungeonBankRules.Currency.class);
        for (Slot slot : slots) {
            if (slot == null || !slotIndices.add(slot.index())) {
                throw new IllegalArgumentException("Dungeon Bank occupied slot indices must be unique");
            }
            if (slot.index() >= grade.slots()) {
                throw new IllegalArgumentException("Dungeon Bank occupied slot exceeds current grade");
            }
            if (!withdrawalIds.add(slot.withdrawalQuoteId())) {
                throw new IllegalArgumentException("Dungeon Bank withdrawal quote IDs must be unique");
            }
            packedTotals.merge(slot.currency(), slot.amount(), Math::addExact);
        }
        Wallet packedWallet = new Wallet(
            packedTotals.getOrDefault(DungeonBankRules.Currency.BRONZE, 0L),
            packedTotals.getOrDefault(DungeonBankRules.Currency.SILVER, 0L),
            packedTotals.getOrDefault(DungeonBankRules.Currency.GOLD, 0L),
            packedTotals.getOrDefault(DungeonBankRules.Currency.STELLAR_GOLD, 0L)
        );
        if (!packedWallet.equals(bankedFunds)) {
            throw new IllegalArgumentException(
                "Dungeon Bank packed slots do not equal the authoritative Bank balance"
            );
        }
    }

    public boolean supportedSchema() {
        return schemaVersion == CURRENT_SCHEMA;
    }

    public int unlockedSlots() {
        return grade.slots();
    }

    public int depositFeeBasisPoints() {
        return grade.depositFeeBasisPoints();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, DungeonBankSnapshotPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.revision);
        buffer.writeVarLong(payload.requestId);
        buffer.writeBoolean(payload.openRequested);
        buffer.writeEnum(payload.grade);
        payload.moneyOnHand.write(buffer);
        payload.bankedFunds.write(buffer);
        buffer.writeVarInt(payload.depositQuotes.size());
        payload.depositQuotes.forEach(quote -> quote.write(buffer));
        buffer.writeVarInt(payload.slots.size());
        payload.slots.forEach(slot -> slot.write(buffer));
        payload.upgrade.write(buffer);
        payload.status.write(buffer);
    }

    private static DungeonBankSnapshotPayload read(FriendlyByteBuf buffer) {
        int schemaVersion = buffer.readVarInt();
        long revision = buffer.readVarLong();
        long requestId = buffer.readVarLong();
        boolean openRequested = buffer.readBoolean();
        DungeonBankRules.Grade grade = buffer.readEnum(DungeonBankRules.Grade.class);
        Wallet moneyOnHand = Wallet.read(buffer);
        Wallet bankedFunds = Wallet.read(buffer);
        int depositCount = boundedCount(buffer.readVarInt(), MAX_DEPOSIT_QUOTES, "deposit quote");
        List<DepositQuote> deposits = new ArrayList<>(depositCount);
        for (int index = 0; index < depositCount; index++) deposits.add(DepositQuote.read(buffer));
        int slotCount = boundedCount(
            buffer.readVarInt(), DungeonBankRules.MAXIMUM_SLOTS, "occupied slot"
        );
        List<Slot> slots = new ArrayList<>(slotCount);
        for (int index = 0; index < slotCount; index++) slots.add(Slot.read(buffer));
        return new DungeonBankSnapshotPayload(
            schemaVersion,
            revision,
            requestId,
            openRequested,
            grade,
            moneyOnHand,
            bankedFunds,
            deposits,
            slots,
            Upgrade.read(buffer),
            Status.read(buffer)
        );
    }

    private static int boundedCount(int count, int maximum, String field) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid Dungeon Bank " + field + " count: " + count);
        }
        return count;
    }

    public record Wallet(long bronze, long silver, long gold, long stellarGold) {
        public Wallet {
            if (bronze < 0L || silver < 0L || gold < 0L || stellarGold < 0L) {
                throw new IllegalArgumentException("Dungeon Bank wallet values cannot be negative");
            }
        }

        public static Wallet empty() {
            return new Wallet(0L, 0L, 0L, 0L);
        }

        public static Wallet from(DungeonBankRules.Balance balance) {
            Objects.requireNonNull(balance, "Dungeon Bank balance");
            return new Wallet(
                balance.bronze(), balance.silver(), balance.gold(), balance.stellarGold()
            );
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarLong(bronze);
            buffer.writeVarLong(silver);
            buffer.writeVarLong(gold);
            buffer.writeVarLong(stellarGold);
        }

        private static Wallet read(FriendlyByteBuf buffer) {
            return new Wallet(
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong()
            );
        }
    }

    public record DepositQuote(
        String quoteId,
        DungeonBankRules.Currency currency,
        int sourceSlot,
        long availableAmount,
        long depositAmount,
        Wallet fee,
        Wallet bankReceives,
        long walletRemaining,
        int additionalSlots,
        boolean depositable,
        String failureReason
    ) {
        public DepositQuote {
            quoteId = required(quoteId, 96, "deposit quote id");
            currency = Objects.requireNonNull(currency, "Dungeon Bank deposit currency");
            if ((sourceSlot < 0 || sourceSlot > 35) && sourceSlot != 40) {
                throw new IllegalArgumentException("Dungeon Bank deposit source is not inventory/offhand");
            }
            fee = fee == null ? Wallet.empty() : fee;
            bankReceives = bankReceives == null ? Wallet.empty() : bankReceives;
            long feeValue = DungeonBankRules.canonicalValue(
                fee.bronze(), fee.silver(), fee.gold(), fee.stellarGold()
            );
            long receiveValue = DungeonBankRules.canonicalValue(
                bankReceives.bronze(),
                bankReceives.silver(),
                bankReceives.gold(),
                bankReceives.stellarGold()
            );
            long depositValue = Math.multiplyExact(
                depositAmount, DungeonBankRules.bronzeValue(currency)
            );
            if (availableAmount < 0L || depositAmount <= 0L
                || depositAmount > DungeonBankRules.STACK_SIZE
                || depositAmount > availableAmount || feeValue <= 0L
                || feeValue > depositValue
                || receiveValue != depositValue - feeValue
                || walletRemaining != availableAmount - depositAmount || additionalSlots < 0) {
                throw new IllegalArgumentException("Dungeon Bank deposit quote values are invalid");
            }
            Wallet canonicalFee = Wallet.from(
                DungeonBankRules.decomposeCanonical(feeValue)
            );
            Wallet canonicalReceipt = Wallet.from(
                DungeonBankRules.decomposeCanonical(receiveValue)
            );
            if (!canonicalFee.equals(fee) || !canonicalReceipt.equals(bankReceives)) {
                throw new IllegalArgumentException(
                    "Dungeon Bank deposit quote breakdown is not canonical"
                );
            }
            failureReason = text(failureReason, 96, "deposit failure reason");
            if (!depositable && failureReason.isBlank()) {
                throw new IllegalArgumentException("Unavailable Bank deposit requires a reason");
            }
            if (depositable && receiveValue <= 0L) {
                throw new IllegalArgumentException(
                    "Depositable Bank deposit quote requires a positive receipt"
                );
            }
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(quoteId, 96);
            buffer.writeEnum(currency);
            buffer.writeVarInt(sourceSlot);
            buffer.writeVarLong(availableAmount);
            buffer.writeVarLong(depositAmount);
            fee.write(buffer);
            bankReceives.write(buffer);
            buffer.writeVarLong(walletRemaining);
            buffer.writeVarInt(additionalSlots);
            buffer.writeBoolean(depositable);
            buffer.writeUtf(failureReason, 96);
        }

        private static DepositQuote read(FriendlyByteBuf buffer) {
            return new DepositQuote(
                buffer.readUtf(96),
                buffer.readEnum(DungeonBankRules.Currency.class),
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                Wallet.read(buffer),
                Wallet.read(buffer),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readUtf(96)
            );
        }
    }

    public record Slot(
        int index,
        DungeonBankRules.Currency currency,
        long amount,
        String withdrawalQuoteId,
        Wallet fee,
        Wallet playerReceives,
        boolean withdrawable,
        String failureReason
    ) {
        public Slot(
            int index,
            DungeonBankRules.Currency currency,
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
                Wallet.empty(),
                Wallet.from(DungeonBankRules.decomposeCanonical(
                    Math.multiplyExact(amount, DungeonBankRules.bronzeValue(currency))
                )),
                withdrawable,
                failureReason
            );
        }

        public Slot {
            if (index < 0 || index >= DungeonBankRules.MAXIMUM_SLOTS) {
                throw new IllegalArgumentException("Dungeon Bank slot index is invalid");
            }
            currency = Objects.requireNonNull(currency, "Dungeon Bank slot currency");
            if (amount <= 0L || amount > DungeonBankRules.STACK_SIZE) {
                throw new IllegalArgumentException("Dungeon Bank stack amount is invalid");
            }
            withdrawalQuoteId = required(withdrawalQuoteId, 96, "withdrawal quote id");
            fee = fee == null ? Wallet.empty() : fee;
            playerReceives = playerReceives == null ? Wallet.empty() : playerReceives;
            long sourceValue = Math.multiplyExact(
                amount, DungeonBankRules.bronzeValue(currency)
            );
            long feeValue = DungeonBankRules.canonicalValue(
                fee.bronze(), fee.silver(), fee.gold(), fee.stellarGold()
            );
            long receiveValue = DungeonBankRules.canonicalValue(
                playerReceives.bronze(),
                playerReceives.silver(),
                playerReceives.gold(),
                playerReceives.stellarGold()
            );
            if (feeValue < 0L || feeValue >= sourceValue
                || receiveValue <= 0L || feeValue + receiveValue != sourceValue
                || !Wallet.from(DungeonBankRules.decomposeCanonical(feeValue)).equals(fee)
                || !Wallet.from(DungeonBankRules.decomposeCanonical(receiveValue))
                    .equals(playerReceives)) {
                throw new IllegalArgumentException("Dungeon Bank withdrawal quote values are invalid");
            }
            failureReason = text(failureReason, 96, "withdrawal failure reason");
            if (!withdrawable && failureReason.isBlank()) {
                throw new IllegalArgumentException("Unavailable Bank withdrawal requires a reason");
            }
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(index);
            buffer.writeEnum(currency);
            buffer.writeVarLong(amount);
            buffer.writeUtf(withdrawalQuoteId, 96);
            fee.write(buffer);
            playerReceives.write(buffer);
            buffer.writeBoolean(withdrawable);
            buffer.writeUtf(failureReason, 96);
        }

        private static Slot read(FriendlyByteBuf buffer) {
            return new Slot(
                buffer.readVarInt(),
                buffer.readEnum(DungeonBankRules.Currency.class),
                buffer.readVarLong(),
                buffer.readUtf(96),
                Wallet.read(buffer),
                Wallet.read(buffer),
                buffer.readBoolean(),
                buffer.readUtf(96)
            );
        }
    }

    public record Upgrade(
        boolean offered,
        DungeonBankRules.Grade targetGrade,
        long costBronze,
        Wallet cost,
        String quoteId,
        boolean affordable,
        String unavailableReason
    ) {
        public Upgrade {
            targetGrade = Objects.requireNonNull(targetGrade, "Dungeon Bank upgrade target");
            if (costBronze < 0L) {
                throw new IllegalArgumentException("Dungeon Bank upgrade cost cannot be negative");
            }
            cost = cost == null ? Wallet.empty() : cost;
            quoteId = offered ? required(quoteId, 96, "upgrade quote id") : text(
                quoteId, 96, "upgrade quote id"
            );
            unavailableReason = text(unavailableReason, 96, "upgrade unavailable reason");
            if (offered && costBronze <= 0L) {
                throw new IllegalArgumentException("Offered Dungeon Bank upgrade requires a cost");
            }
        }

        public static Upgrade none() {
            return new Upgrade(
                false,
                DungeonBankRules.Grade.GENESIS_IX,
                0L,
                Wallet.empty(),
                "",
                false,
                "MAXIMUM BANK GRADE"
            );
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(offered);
            buffer.writeEnum(targetGrade);
            buffer.writeVarLong(costBronze);
            cost.write(buffer);
            buffer.writeUtf(quoteId, 96);
            buffer.writeBoolean(affordable);
            buffer.writeUtf(unavailableReason, 96);
        }

        private static Upgrade read(FriendlyByteBuf buffer) {
            return new Upgrade(
                buffer.readBoolean(),
                buffer.readEnum(DungeonBankRules.Grade.class),
                buffer.readVarLong(),
                Wallet.read(buffer),
                buffer.readUtf(96),
                buffer.readBoolean(),
                buffer.readUtf(96)
            );
        }
    }

    public record Status(String code, String message) {
        public Status {
            code = required(code, 48, "status code");
            message = text(message, 256, "status message");
        }

        public static Status ready() {
            return new Status("READY", "");
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(code, 48);
            buffer.writeUtf(message, 256);
        }

        private static Status read(FriendlyByteBuf buffer) {
            return new Status(buffer.readUtf(48), buffer.readUtf(256));
        }
    }

    private static String required(String value, int maximum, String field) {
        String checked = text(value, maximum, field);
        if (checked.isBlank()) {
            throw new IllegalArgumentException("Dungeon Bank " + field + " is required");
        }
        return checked;
    }

    private static String text(String value, int maximum, String field) {
        String checked = value == null ? "" : value;
        if (checked.length() > maximum) {
            throw new IllegalArgumentException(
                "Dungeon Bank " + field + " exceeds " + maximum + " characters"
            );
        }
        return checked;
    }
}
