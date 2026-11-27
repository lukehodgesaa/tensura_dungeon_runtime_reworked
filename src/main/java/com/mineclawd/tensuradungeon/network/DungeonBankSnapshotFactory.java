package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.reward.VanillaInventoryGrant;
import com.mineclawd.tensuradungeon.runtime.DungeonBankData;
import com.mineclawd.tensuradungeon.runtime.DungeonBankRules;
import com.mineclawd.tensuradungeon.runtime.DungeonShopCurrency;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonBankSnapshotFactory {
    private static final DungeonBankSnapshotPayload.Status READY =
        DungeonBankSnapshotPayload.Status.ready();

    private DungeonBankSnapshotFactory() {}

    public static void sendOpen(ServerPlayer player) {
        PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player, "Dungeon Bank player"),
            create(player, 0L, READY)
        );
    }

    static DungeonBankSnapshotPayload create(ServerPlayer player, long requestId) {
        return create(player, requestId, READY);
    }

    static DungeonBankSnapshotPayload create(
        ServerPlayer player,
        long requestId,
        DungeonBankSnapshotPayload.Status status
    ) {
        Objects.requireNonNull(player, "Dungeon Bank player");
        Objects.requireNonNull(status, "Dungeon Bank status");
        UUID playerId = player.getUUID();
        DungeonBankData.Snapshot bank = DungeonBankData.get(player.serverLevel()).snapshot(playerId);

        Map<DungeonBankRules.Currency, Long> eligibleTotals = eligibleTotals(player);
        DungeonBankSnapshotPayload.Wallet moneyOnHand = wallet(eligibleTotals);
        List<DungeonBankSnapshotPayload.DepositQuote> deposits = new ArrayList<>();
        for (int slot = 0; slot <= 35; slot++) {
            addDepositQuote(player, bank, slot, eligibleTotals, deposits);
        }
        addDepositQuote(player, bank, 40, eligibleTotals, deposits);

        List<DungeonBankSnapshotPayload.Slot> slots = packedSlots(player, bank);
        DungeonBankSnapshotPayload.Upgrade upgrade = upgrade(player, bank);
        return new DungeonBankSnapshotPayload(
            DungeonBankSnapshotPayload.CURRENT_SCHEMA,
            bank.revision(),
            requestId,
            true,
            bank.grade(),
            moneyOnHand,
            DungeonBankSnapshotPayload.Wallet.from(bank.balance()),
            deposits,
            slots,
            upgrade,
            status
        );
    }

    static DungeonBankSnapshotPayload.Status resultStatus(
        DungeonBankActionResultPayload.ResultStatus result,
        String message
    ) {
        Objects.requireNonNull(result, "Dungeon Bank result");
        return new DungeonBankSnapshotPayload.Status(result.name(), message == null ? "" : message);
    }

    private static Map<DungeonBankRules.Currency, Long> eligibleTotals(ServerPlayer player) {
        Map<DungeonBankRules.Currency, Long> totals = new EnumMap<>(DungeonBankRules.Currency.class);
        for (int slot = 0; slot <= 35; slot++) {
            merge(totals, player.getInventory().getItem(slot));
        }
        merge(totals, player.getInventory().getItem(40));
        return totals;
    }

    private static void merge(
        Map<DungeonBankRules.Currency, Long> totals,
        ItemStack stack
    ) {
        DungeonBankRules.Currency currency = currencyOf(stack);
        if (currency != null && !stack.isEmpty()) {
            totals.merge(currency, (long) stack.getCount(), Long::sum);
        }
    }

    private static DungeonBankSnapshotPayload.Wallet wallet(
        Map<DungeonBankRules.Currency, Long> totals
    ) {
        return new DungeonBankSnapshotPayload.Wallet(
            totals.getOrDefault(DungeonBankRules.Currency.BRONZE, 0L),
            totals.getOrDefault(DungeonBankRules.Currency.SILVER, 0L),
            totals.getOrDefault(DungeonBankRules.Currency.GOLD, 0L),
            totals.getOrDefault(DungeonBankRules.Currency.STELLAR_GOLD, 0L)
        );
    }

    private static void addDepositQuote(
        ServerPlayer player,
        DungeonBankData.Snapshot bank,
        int sourceSlot,
        Map<DungeonBankRules.Currency, Long> eligibleTotals,
        List<DungeonBankSnapshotPayload.DepositQuote> result
    ) {
        ItemStack stack = player.getInventory().getItem(sourceSlot);
        DungeonBankRules.Currency currency = currencyOf(stack);
        if (currency == null || stack.isEmpty()) return;

        long observedStackAmount = stack.getCount();
        long deposit = Math.min(observedStackAmount, DungeonBankRules.STACK_SIZE);
        int basisPoints = bank.grade().depositFeeBasisPoints();
        DungeonBankRules.Balance feeBreakdown = DungeonBankRules.depositFeeAsBalance(
            deposit, currency, basisPoints
        );
        DungeonBankRules.Balance receivesBreakdown = DungeonBankRules.depositNetAsBalance(
            deposit, currency, basisPoints
        );
        long receiveValue = DungeonBankRules.canonicalValue(
            receivesBreakdown.bronze(),
            receivesBreakdown.silver(),
            receivesBreakdown.gold(),
            receivesBreakdown.stellarGold()
        );
        DungeonBankRules.Balance after = receiveValue <= 0L
            ? bank.balance()
            : bank.balance().plus(receivesBreakdown);
        int additionalSlots = Math.max(
            0,
            DungeonBankRules.occupiedSlots(after) - bank.occupiedSlots()
        );
        boolean capacity = DungeonBankRules.occupiedSlots(after) <= bank.grade().slots();
        boolean depositable = receiveValue > 0L && capacity;
        String reason = receiveValue <= 0L
            ? "STACK IS TOO SMALL AFTER DEPOSIT TAX"
            : capacity ? "" : "BANK CAPACITY IS FULL";
        long available = eligibleTotals.getOrDefault(currency, 0L);
        result.add(new DungeonBankSnapshotPayload.DepositQuote(
            depositQuoteId(bank.revision(), sourceSlot, currency, observedStackAmount),
            currency,
            sourceSlot,
            available,
            deposit,
            DungeonBankSnapshotPayload.Wallet.from(feeBreakdown),
            DungeonBankSnapshotPayload.Wallet.from(receivesBreakdown),
            available - deposit,
            additionalSlots,
            depositable,
            reason
        ));
    }

    private static List<DungeonBankSnapshotPayload.Slot> packedSlots(
        ServerPlayer player,
        DungeonBankData.Snapshot bank
    ) {
        List<DungeonBankSnapshotPayload.Slot> result = new ArrayList<>();
        int index = 0;
        for (DungeonBankRules.Currency currency : DungeonBankRules.Currency.values()) {
            long remaining = bank.balance().amount(currency);
            while (remaining > 0L) {
                long amount = Math.min(DungeonBankRules.STACK_SIZE, remaining);
                DungeonBankRules.Balance fee = DungeonBankRules.withdrawalFeeAsBalance(
                    amount, currency, bank.grade().withdrawalFeeBasisPoints()
                );
                DungeonBankRules.Balance receives = DungeonBankRules.withdrawalNetAsBalance(
                    amount, currency, bank.grade().withdrawalFeeBasisPoints()
                );
                List<ItemStack> outputs = itemsFor(receives);
                boolean grantTarget = !outputs.isEmpty();
                boolean withdrawable = grantTarget && canGrant(player, outputs);
                result.add(new DungeonBankSnapshotPayload.Slot(
                    index,
                    currency,
                    amount,
                    withdrawalQuoteId(bank.revision(), index, currency, amount),
                    DungeonBankSnapshotPayload.Wallet.from(fee),
                    DungeonBankSnapshotPayload.Wallet.from(receives),
                    withdrawable,
                    withdrawable ? "" : grantTarget
                        ? "PLAYER INVENTORY IS FULL" : "CURRENCY ITEM IS NOT REGISTERED"
                ));
                remaining -= amount;
                index++;
            }
        }
        return List.copyOf(result);
    }

    private static DungeonBankSnapshotPayload.Upgrade upgrade(
        ServerPlayer player,
        DungeonBankData.Snapshot bank
    ) {
        if (!bank.grade().hasNext()) return DungeonBankSnapshotPayload.Upgrade.none();
        DungeonBankRules.Grade target = bank.grade().next();
        long costBronze = bank.grade().nextUpgradeCostBronze();
        DungeonBankRules.Balance cost = DungeonBankRules.decomposeCanonical(costBronze);
        boolean affordable = DungeonShopCurrency.prepare(player, costBronze).isPresent();
        return new DungeonBankSnapshotPayload.Upgrade(
            true,
            target,
            costBronze,
            DungeonBankSnapshotPayload.Wallet.from(cost),
            upgradeQuoteId(bank.revision(), target, costBronze),
            affordable,
            affordable ? "" : "INSUFFICIENT EQUIVALENT FUNDS"
        );
    }

    public static DungeonBankRules.Currency currencyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return null;
        String id = key.toString();
        for (DungeonBankRules.Currency currency : DungeonBankRules.Currency.values()) {
            if (currency.itemId().equals(id)) return currency;
        }
        return "tensura:stellar_coin".equals(id)
            ? DungeonBankRules.Currency.STELLAR_GOLD : null;
    }

    static boolean canGrant(ServerPlayer player, DungeonBankRules.Currency currency, long amount) {
        if (amount <= 0L || amount > DungeonBankRules.STACK_SIZE) return false;
        Item item = resolve(currency);
        if (item == null) return false;
        return canGrant(player, List.of(new ItemStack(item, Math.toIntExact(amount))));
    }

    public static boolean canGrant(ServerPlayer player, List<ItemStack> outputs) {
        return player != null && outputs != null && !outputs.isEmpty()
            && outputs.stream().allMatch(stack -> stack != null && !stack.isEmpty())
            && VanillaInventoryGrant.INSTANCE.canGrant(player, outputs);
    }

    public static List<ItemStack> itemsFor(DungeonBankRules.Balance balance) {
        Objects.requireNonNull(balance, "Dungeon Bank withdrawal balance");
        List<ItemStack> outputs = new ArrayList<>();
        for (DungeonBankRules.Currency currency : DungeonBankRules.Currency.values()) {
            long remaining = balance.amount(currency);
            if (remaining == 0L) continue;
            Item item = resolve(currency);
            if (item == null) return List.of();
            while (remaining > 0L) {
                int amount = Math.toIntExact(Math.min(DungeonBankRules.STACK_SIZE, remaining));
                outputs.add(new ItemStack(item, amount));
                remaining -= amount;
            }
        }
        return List.copyOf(outputs);
    }

    public static Item resolve(DungeonBankRules.Currency currency) {
        Item item = BuiltInRegistries.ITEM.getOptional(
            ResourceLocation.parse(currency.itemId())
        ).orElse(null);
        if (item == null && currency == DungeonBankRules.Currency.STELLAR_GOLD) {
            item = BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.parse("tensura:stellar_coin")
            ).orElse(null);
        }
        return item;
    }

    public static String depositQuoteId(
        long revision,
        int sourceSlot,
        DungeonBankRules.Currency currency,
        long amount
    ) {
        return "D:" + revision + ':' + sourceSlot + ':' + currency.name() + ':' + amount;
    }

    public static String withdrawalQuoteId(
        long revision,
        int index,
        DungeonBankRules.Currency currency,
        long amount
    ) {
        return "W:" + revision + ':' + index + ':' + currency.name() + ':' + amount;
    }

    static String upgradeQuoteId(
        long revision,
        DungeonBankRules.Grade target,
        long costBronze
    ) {
        return "U:" + revision + ':' + target.name() + ':' + costBronze;
    }
}
