package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import com.mineclawd.tensuradungeon.reward.VanillaInventoryGrant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DungeonShopCurrency {
    private DungeonShopCurrency() {}

    public static Optional<PaymentPlan> exactPlan(
        long bronze,
        long silver,
        long gold,
        long priceBronze
    ) {
        return exactPlan(bronze, silver, gold, 0L, priceBronze);
    }

    public static Optional<PaymentPlan> exactPlan(
        long bronze,
        long silver,
        long gold,
        long stellarGold,
        long priceBronze
    ) {
        validateCounts(bronze, silver, gold, stellarGold, priceBronze);

        long stellarPayment = Math.min(
            stellarGold,
            priceBronze / DungeonCurrencyPolicy.BRONZE_PER_STELLAR
        );
        long afterStellar = priceBronze
            - stellarPayment * DungeonCurrencyPolicy.BRONZE_PER_STELLAR;
        long goldPayment = Math.min(
            gold,
            afterStellar / DungeonCurrencyPolicy.BRONZE_PER_GOLD
        );
        long remaining = afterStellar - goldPayment * DungeonCurrencyPolicy.BRONZE_PER_GOLD;
        long silverPayment = Math.min(silver, remaining / DungeonCurrencyPolicy.BRONZE_PER_SILVER);
        long bronzePayment = remaining - silverPayment * DungeonCurrencyPolicy.BRONZE_PER_SILVER;
        if (bronzePayment > bronze) {
            return Optional.empty();
        }
        return Optional.of(new PaymentPlan(
            bronzePayment,
            silverPayment,
            goldPayment,
            stellarPayment,
            0L,
            0L,
            0L,
            0L,
            priceBronze
        ));
    }

    public static Optional<PaymentPlan> equivalentPlan(
        long bronze,
        long silver,
        long gold,
        long priceBronze
    ) {
        return equivalentPlan(bronze, silver, gold, 0L, priceBronze);
    }

    public static Optional<PaymentPlan> equivalentPlan(
        long bronze,
        long silver,
        long gold,
        long stellarGold,
        long priceBronze
    ) {
        validateCounts(bronze, silver, gold, stellarGold, priceBronze);
        Optional<PaymentPlan> exact = exactPlan(
            bronze,
            silver,
            gold,
            stellarGold,
            priceBronze
        );
        if (exact.isPresent()) {
            return exact;
        }

        long lowerCapacity = lowerCapacity(bronze, silver);
        long goldCapacity = addCapacity(
            lowerCapacity,
            gold,
            DungeonCurrencyPolicy.BRONZE_PER_GOLD
        );
        long stellarShortfall = priceBronze > goldCapacity
            ? priceBronze - goldCapacity
            : 0L;
        long stellarPayment = ceilDiv(
            stellarShortfall,
            DungeonCurrencyPolicy.BRONZE_PER_STELLAR
        );
        if (stellarPayment > stellarGold) {
            return Optional.empty();
        }
        long stellarValue = multiplyOrMax(
            stellarPayment,
            DungeonCurrencyPolicy.BRONZE_PER_STELLAR
        );
        long afterStellar = stellarValue >= priceBronze ? 0L : priceBronze - stellarValue;
        long goldShortfall = afterStellar > lowerCapacity
            ? afterStellar - lowerCapacity
            : 0L;
        long goldPayment = ceilDiv(goldShortfall, DungeonCurrencyPolicy.BRONZE_PER_GOLD);
        if (goldPayment > gold) {
            return Optional.empty();
        }

        long goldValue = multiplyOrMax(
            goldPayment,
            DungeonCurrencyPolicy.BRONZE_PER_GOLD
        );
        long remaining = goldValue >= afterStellar ? 0L : afterStellar - goldValue;
        Optional<LowerPlan> lower = lowerPlan(bronze, silver, remaining);
        if (lower.isEmpty()) {
            return Optional.empty();
        }

        LowerPlan lowerPlan = lower.orElseThrow();
        long debitValue = Math.addExact(
            stellarValue,
            Math.addExact(
                goldValue,
                Math.addExact(
                    lowerPlan.bronze(),
                    Math.multiplyExact(lowerPlan.silver(), DungeonCurrencyPolicy.BRONZE_PER_SILVER)
                )
            )
        );
        long changeValue = Math.subtractExact(debitValue, priceBronze);
        long changeStellarGold = changeValue / DungeonCurrencyPolicy.BRONZE_PER_STELLAR;
        long afterStellarChange = changeValue % DungeonCurrencyPolicy.BRONZE_PER_STELLAR;
        long changeGold = afterStellarChange / DungeonCurrencyPolicy.BRONZE_PER_GOLD;
        long afterGoldChange = afterStellarChange % DungeonCurrencyPolicy.BRONZE_PER_GOLD;
        long changeSilver = afterGoldChange / DungeonCurrencyPolicy.BRONZE_PER_SILVER;
        long changeBronze = afterGoldChange % DungeonCurrencyPolicy.BRONZE_PER_SILVER;
        return Optional.of(new PaymentPlan(
            lowerPlan.bronze(),
            lowerPlan.silver(),
            goldPayment,
            stellarPayment,
            changeBronze,
            changeSilver,
            changeGold,
            changeStellarGold,
            priceBronze
        ));
    }

    public static Optional<PaymentPlan> prepare(ServerPlayer player, long priceBronze) {
        if (player == null) {
            throw new IllegalArgumentException("Shop payment player is required");
        }
        DungeonBankRules.Balance bank = DungeonBankData.get(player.serverLevel())
            .snapshot(player.getUUID()).balance();
        return equivalentPlan(
            Math.addExact(
                count(player, DungeonCurrencyPolicy.BRONZE_COIN_ITEM_ID), bank.bronze()
            ),
            Math.addExact(
                count(player, DungeonCurrencyPolicy.SILVER_COIN_ITEM_ID), bank.silver()
            ),
            Math.addExact(
                count(player, DungeonCurrencyPolicy.GOLD_COIN_ITEM_ID), bank.gold()
            ),
            Math.addExact(
                count(player, DungeonCurrencyPolicy.STELLAR_GOLD_COIN_ITEM_ID),
                bank.stellarGold()
            ),
            priceBronze
        );
    }

    public static DebitReceipt debit(ServerPlayer player, PaymentPlan plan) {
        if (player == null || plan == null) {
            throw new IllegalArgumentException("Shop debit player and plan are required");
        }
        Inventory inventory = player.getInventory();
        List<ItemStack> before = snapshot(inventory);
        DungeonBankData bankData = DungeonBankData.get(player.serverLevel());
        DungeonBankData.Snapshot bankBefore = bankData.snapshot(player.getUUID());
        DungeonBankRules.Balance bankAfter = bankBefore.balance();
        try {
            bankAfter = consumeCombined(
                inventory,
                resolve(DungeonCurrencyPolicy.STELLAR_GOLD_COIN_ITEM_ID),
                DungeonBankRules.Currency.STELLAR_GOLD,
                plan.stellarGold(),
                bankAfter
            );
            bankAfter = consumeCombined(
                inventory,
                resolve(DungeonCurrencyPolicy.GOLD_COIN_ITEM_ID),
                DungeonBankRules.Currency.GOLD,
                plan.gold(),
                bankAfter
            );
            bankAfter = consumeCombined(
                inventory,
                resolve(DungeonCurrencyPolicy.SILVER_COIN_ITEM_ID),
                DungeonBankRules.Currency.SILVER,
                plan.silver(),
                bankAfter
            );
            bankAfter = consumeCombined(
                inventory,
                resolve(DungeonCurrencyPolicy.BRONZE_COIN_ITEM_ID),
                DungeonBankRules.Currency.BRONZE,
                plan.bronze(),
                bankAfter
            );
            List<ItemStack> change = changeStacks(plan);
            if (!change.isEmpty()) {
                if (VanillaInventoryGrant.INSTANCE.canGrant(player, change)) {
                    VanillaInventoryGrant.INSTANCE.grant(player, change);
                } else {
                    bankAfter = addChangeToBank(bankAfter, plan);
                    if (DungeonBankRules.occupiedSlots(bankAfter) > bankBefore.grade().slots()) {
                        throw new ChangeInventoryException();
                    }
                }
            }
            if (!bankAfter.equals(bankBefore.balance())) {
                DungeonBankData.MutationResult mutation = bankData.replaceBalances(
                    player.getUUID(), bankBefore.revision(), bankAfter
                );
                if (!mutation.applied()) {
                    if (mutation.status() == DungeonBankData.MutationStatus.CAPACITY_EXCEEDED) {
                        throw new ChangeInventoryException();
                    }
                    throw new IllegalStateException("Dungeon Bank changed during Shop debit");
                }
            }
        } catch (RuntimeException failure) {
            restore(inventory, before);
            bankData.restore(player.getUUID(), bankBefore);
            throw failure;
        }
        inventory.setChanged();
        return new DebitReceipt(before, bankBefore);
    }

    private static void validateCounts(
        long bronze,
        long silver,
        long gold,
        long stellarGold,
        long priceBronze
    ) {
        if (bronze < 0L || silver < 0L || gold < 0L || stellarGold < 0L || priceBronze <= 0L) {
            throw new IllegalArgumentException("Shop currency counts and price must be positive or zero");
        }
    }

    private static Optional<LowerPlan> lowerPlan(long bronze, long silver, long remaining) {
        if (remaining <= 0L) {
            return Optional.of(new LowerPlan(0L, 0L));
        }
        long missingAfterBronze = remaining > bronze ? remaining - bronze : 0L;
        long silverPayment = ceilDiv(missingAfterBronze, DungeonCurrencyPolicy.BRONZE_PER_SILVER);
        if (silverPayment > silver) {
            return Optional.empty();
        }
        long silverValue = Math.multiplyExact(
            silverPayment,
            DungeonCurrencyPolicy.BRONZE_PER_SILVER
        );
        long bronzePayment = remaining > silverValue ? remaining - silverValue : 0L;
        if (bronzePayment > bronze) {
            return Optional.empty();
        }
        return Optional.of(new LowerPlan(bronzePayment, silverPayment));
    }

    private static long lowerCapacity(long bronze, long silver) {
        if (silver > Long.MAX_VALUE / DungeonCurrencyPolicy.BRONZE_PER_SILVER) {
            return Long.MAX_VALUE;
        }
        long silverValue = silver * DungeonCurrencyPolicy.BRONZE_PER_SILVER;
        if (bronze > Long.MAX_VALUE - silverValue) {
            return Long.MAX_VALUE;
        }
        return silverValue + bronze;
    }

    private static long addCapacity(long base, long count, long unit) {
        long value = multiplyOrMax(count, unit);
        if (base == Long.MAX_VALUE || value == Long.MAX_VALUE
            || base > Long.MAX_VALUE - value) {
            return Long.MAX_VALUE;
        }
        return base + value;
    }

    private static long ceilDiv(long value, long divisor) {
        if (value <= 0L) {
            return 0L;
        }
        return value / divisor + (value % divisor == 0L ? 0L : 1L);
    }

    private static long multiplyOrMax(long left, long right) {
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static int count(ServerPlayer player, String itemId) {
        Item item = resolve(itemId);
        return item == null ? 0 : player.getInventory().countItem(item);
    }

    private static Item resolve(String itemId) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
    }

    private static DungeonBankRules.Balance consumeCombined(
        Inventory inventory,
        Item item,
        DungeonBankRules.Currency currency,
        long amount,
        DungeonBankRules.Balance bank
    ) {
        if (amount == 0L) {
            return bank;
        }
        if (item == null) {
            throw new IllegalStateException("Required Shop currency item is not registered");
        }
        long remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0L; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }
            int before = stack.getCount();
            int taken = Math.toIntExact(Math.min(remaining, (long) before));
            stack.shrink(taken);
            remaining -= taken;
        }
        if (remaining > bank.amount(currency)) {
            throw new IllegalStateException("Shop currency holdings changed during debit");
        }
        return bank.minus(currency, remaining);
    }

    private static List<ItemStack> snapshot(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            result.add(inventory.getItem(slot).copy());
        }
        return result;
    }

    private static void restore(Inventory inventory, List<ItemStack> snapshot) {
        for (int slot = 0; slot < snapshot.size(); slot++) {
            inventory.setItem(slot, snapshot.get(slot).copy());
        }
        inventory.setChanged();
    }

    private static List<ItemStack> changeStacks(PaymentPlan plan) {
        List<ItemStack> change = new ArrayList<>(4);
        addStack(change, DungeonCurrencyPolicy.STELLAR_GOLD_COIN_ITEM_ID, plan.changeStellarGold());
        addStack(change, DungeonCurrencyPolicy.GOLD_COIN_ITEM_ID, plan.changeGold());
        addStack(change, DungeonCurrencyPolicy.SILVER_COIN_ITEM_ID, plan.changeSilver());
        addStack(change, DungeonCurrencyPolicy.BRONZE_COIN_ITEM_ID, plan.changeBronze());
        return List.copyOf(change);
    }

    private static void addStack(List<ItemStack> stacks, String itemId, long count) {
        if (count == 0L) {
            return;
        }
        Item item = resolve(itemId);
        if (item == null) {
            throw new IllegalStateException("Required Shop currency item is not registered");
        }
        long remaining = count;
        while (remaining > 0L) {
            int stack = Math.toIntExact(Math.min(DungeonBankRules.STACK_SIZE, remaining));
            stacks.add(new ItemStack(item, stack));
            remaining -= stack;
        }
    }

    private static DungeonBankRules.Balance addChangeToBank(
        DungeonBankRules.Balance bank,
        PaymentPlan plan
    ) {
        return bank
            .plus(DungeonBankRules.Currency.BRONZE, plan.changeBronze())
            .plus(DungeonBankRules.Currency.SILVER, plan.changeSilver())
            .plus(DungeonBankRules.Currency.GOLD, plan.changeGold())
            .plus(DungeonBankRules.Currency.STELLAR_GOLD, plan.changeStellarGold());
    }

    public record PaymentPlan(
        long bronze,
        long silver,
        long gold,
        long stellarGold,
        long changeBronze,
        long changeSilver,
        long changeGold,
        long changeStellarGold,
        long priceBronze
    ) {
        public PaymentPlan(long bronze, long silver, long gold, long priceBronze) {
            this(bronze, silver, gold, 0L, 0L, 0L, 0L, 0L, priceBronze);
        }

        public PaymentPlan {
            if (bronze < 0L || silver < 0L || gold < 0L
                || stellarGold < 0L || changeBronze < 0L || changeSilver < 0L
                || changeGold < 0L || changeStellarGold < 0L
                || priceBronze <= 0L) {
                throw new IllegalArgumentException("Shop payment plan values are invalid");
            }
            long debitValue = Math.addExact(
                Math.addExact(
                    Math.addExact(
                        bronze,
                        Math.multiplyExact(silver, DungeonCurrencyPolicy.BRONZE_PER_SILVER)
                    ),
                    Math.multiplyExact(gold, DungeonCurrencyPolicy.BRONZE_PER_GOLD)
                ),
                Math.multiplyExact(stellarGold, DungeonCurrencyPolicy.BRONZE_PER_STELLAR)
            );
            long changeValue = Math.addExact(
                Math.addExact(
                    Math.addExact(
                        changeBronze,
                        Math.multiplyExact(changeSilver, DungeonCurrencyPolicy.BRONZE_PER_SILVER)
                    ),
                    Math.multiplyExact(changeGold, DungeonCurrencyPolicy.BRONZE_PER_GOLD)
                ),
                Math.multiplyExact(changeStellarGold, DungeonCurrencyPolicy.BRONZE_PER_STELLAR)
            );
            if (debitValue < changeValue || debitValue - changeValue != priceBronze) {
                throw new IllegalArgumentException("Shop payment plan does not settle its price");
            }
        }
    }

    public static final class DebitReceipt {
        private final List<ItemStack> before;
        private final DungeonBankData.Snapshot bankBefore;
        private boolean rolledBack;

        private DebitReceipt(
            List<ItemStack> before,
            DungeonBankData.Snapshot bankBefore
        ) {
            this.before = before.stream().map(ItemStack::copy).toList();
            this.bankBefore = bankBefore;
        }

        public void rollback(ServerPlayer player) {
            if (rolledBack) {
                return;
            }
            if (player == null) {
                throw new IllegalArgumentException("Shop rollback player is required");
            }
            restore(player.getInventory(), before);
            DungeonBankData.get(player.serverLevel()).restore(player.getUUID(), bankBefore);
            rolledBack = true;
        }
    }

    private record LowerPlan(long bronze, long silver) {
    }

    public static final class ChangeInventoryException extends IllegalStateException {
        private ChangeInventoryException() {
            super("Shop currency change cannot fit in the player inventory");
        }
    }
}
