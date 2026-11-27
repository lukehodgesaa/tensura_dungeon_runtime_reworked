package dev.luke.tensuradungeon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class DungeonBankUiApi {
    public enum ActionType {
        UPGRADE,
        DEPOSIT,
        WITHDRAW
    }

    public record Action(ActionType type, long snapshotRevision, String quoteId) {
        public Action {
            type = Objects.requireNonNull(type, "Dungeon Bank action type");
            quoteId = quoteId == null ? "" : quoteId;
            if (snapshotRevision < 0L) {
                throw new IllegalArgumentException("Dungeon Bank snapshot revision cannot be negative");
            }
        }
    }

    private static Consumer<DungeonBankView> openAction = view -> {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DungeonBankScreen screen) {
            screen.applyAuthoritativeView(view);
        } else {
            minecraft.setScreen(new DungeonBankScreen(view, minecraft.screen));
        }
    };
    private static Consumer<Action> actionSink = action -> {};

    private DungeonBankUiApi() {}

    public static void open(DungeonBankView view) {
        openAction.accept(Objects.requireNonNull(view, "Dungeon Bank view"));
    }

    public static void openVisualPreview() {
        open(inventoryPreview());
    }

    private static DungeonBankView inventoryPreview() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return DungeonBankView.visualPreview(DungeonShopView.Wallet.empty(), List.of());
        }

        Map<DungeonBankView.Currency, Long> totals = new EnumMap<>(DungeonBankView.Currency.class);
        Map<DungeonBankView.Currency, Integer> firstAmounts =
            new EnumMap<>(DungeonBankView.Currency.class);
        Map<DungeonBankView.Currency, Integer> firstSlots =
            new EnumMap<>(DungeonBankView.Currency.class);
        for (int slot = 0; slot <= 35; slot++) {
            collectEligibleStack(
                minecraft.player.getInventory().getItem(slot), slot, totals, firstAmounts, firstSlots
            );
        }
        collectEligibleStack(
            minecraft.player.getOffhandItem(), 40, totals, firstAmounts, firstSlots
        );

        List<DungeonBankView.DepositQuote> quotes = new ArrayList<>();
        for (DungeonBankView.Currency currency : DungeonBankView.Currency.values()) {
            long available = totals.getOrDefault(currency, 0L);
            int stackAmount = firstAmounts.getOrDefault(currency, 0);
            if (available <= 0L || stackAmount <= 0) continue;
            long depositAmount = Math.min(64L, stackAmount);
            long depositValue = depositAmount * currency.bronzeValue();
            long feeBronze = Math.max(
                1L,
                (depositValue * 200L + 9_999L) / 10_000L
            );
            DungeonShopView.Wallet fee = DungeonBankView.decompose(feeBronze);
            DungeonShopView.Wallet bankReceives = DungeonBankView.decompose(
                depositValue - feeBronze
            );
            quotes.add(new DungeonBankView.DepositQuote(
                "preview:slot_" + firstSlots.get(currency) + "_"
                    + currency.name().toLowerCase(Locale.ROOT),
                currency,
                available,
                depositAmount,
                fee,
                bankReceives,
                available - depositAmount,
                1,
                true,
                ""
            ));
        }
        DungeonShopView.Wallet wallet = new DungeonShopView.Wallet(
            totals.getOrDefault(DungeonBankView.Currency.BRONZE, 0L),
            totals.getOrDefault(DungeonBankView.Currency.SILVER, 0L),
            totals.getOrDefault(DungeonBankView.Currency.GOLD, 0L),
            totals.getOrDefault(DungeonBankView.Currency.STELLAR_GOLD, 0L)
        );
        return DungeonBankView.visualPreview(wallet, quotes);
    }

    private static void collectEligibleStack(
        ItemStack stack,
        int slot,
        Map<DungeonBankView.Currency, Long> totals,
        Map<DungeonBankView.Currency, Integer> firstAmounts,
        Map<DungeonBankView.Currency, Integer> firstSlots
    ) {
        DungeonBankView.Currency currency = currencyOf(stack);
        if (currency == null || stack.isEmpty()) return;
        totals.merge(currency, (long) stack.getCount(), Long::sum);
        firstAmounts.putIfAbsent(currency, stack.getCount());
        firstSlots.putIfAbsent(currency, slot);
    }

    private static DungeonBankView.Currency currencyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (DungeonBankView.Currency currency : DungeonBankView.Currency.values()) {
            if (currency.itemId().equals(itemId)) return currency;
        }
        return "tensura:stellar_coin".equals(itemId)
            ? DungeonBankView.Currency.STELLAR_GOLD : null;
    }

    public static void setOpenAction(Consumer<DungeonBankView> action) {
        openAction = Objects.requireNonNull(action, "Dungeon Bank open action");
    }

    public static void setActionSink(Consumer<Action> sink) {
        actionSink = Objects.requireNonNull(sink, "Dungeon Bank action sink");
    }

    static boolean emitUpgrade(DungeonBankView view) {
        Objects.requireNonNull(view, "Dungeon Bank view");
        if (view.preview() || !view.upgrade().offered() || !view.upgrade().affordable()) {
            return false;
        }
        actionSink.accept(new Action(
            ActionType.UPGRADE, view.revision(), view.upgrade().quoteId()
        ));
        return true;
    }

    static boolean emitDeposit(DungeonBankView view, DungeonBankView.DepositQuote quote) {
        Objects.requireNonNull(view, "Dungeon Bank view");
        Objects.requireNonNull(quote, "Dungeon Bank deposit quote");
        boolean quoteIsCurrent = view.depositQuotes().stream()
            .anyMatch(candidate -> candidate.quoteId().equals(quote.quoteId()));
        if (view.preview() || !quoteIsCurrent || !quote.depositable()) {
            return false;
        }
        actionSink.accept(new Action(ActionType.DEPOSIT, view.revision(), quote.quoteId()));
        return true;
    }

    static boolean emitWithdrawal(DungeonBankView view, DungeonBankView.Slot slot) {
        Objects.requireNonNull(view, "Dungeon Bank view");
        Objects.requireNonNull(slot, "Dungeon Bank withdrawal slot");
        DungeonBankView.Slot current = view.slotAt(slot.index());
        if (view.preview() || current == null || !current.equals(slot) || !slot.withdrawable()) {
            return false;
        }
        actionSink.accept(new Action(
            ActionType.WITHDRAW, view.revision(), slot.withdrawalQuoteId()
        ));
        return true;
    }
}
