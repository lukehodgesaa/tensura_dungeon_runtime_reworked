package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import com.mineclawd.tensuradungeon.runtime.DungeonShopCurrency;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class CurrencyCommandRegistrar {
    private static final int MAX_DENOMINATION_COUNT = 100_000;
    private static final int MAX_PERSISTED_STACK_COUNT = 999;

    private CurrencyCommandRegistrar() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("currency")
            .then(Commands.literal("give")
                .then(Commands.argument("gold", IntegerArgumentType.integer(0, MAX_DENOMINATION_COUNT))
                    .executes(context -> give(context, "gold"))
                    .then(Commands.argument("silver", IntegerArgumentType.integer(0, MAX_DENOMINATION_COUNT))
                        .executes(context -> give(context, "silver"))
                        .then(Commands.argument("bronze", IntegerArgumentType.integer(0, MAX_DENOMINATION_COUNT))
                            .executes(context -> give(context, "bronze"))))))
            .then(Commands.literal("take")
                .then(Commands.argument("gold", IntegerArgumentType.integer(0, MAX_DENOMINATION_COUNT))
                    .executes(context -> take(context, "gold"))
                    .then(Commands.argument("silver", IntegerArgumentType.integer(0, MAX_DENOMINATION_COUNT))
                        .executes(context -> take(context, "silver"))
                        .then(Commands.argument("bronze", IntegerArgumentType.integer(0, MAX_DENOMINATION_COUNT))
                            .executes(context -> take(context, "bronze"))))))
            .then(Commands.literal("balance")
                .executes(CurrencyCommandRegistrar::balance)));
    }

    private static int give(CommandContext<CommandSourceStack> context, String lastArgument) {
        ServerPlayer player = TensuraDungeonCommands.getPlayer(context.getSource());
        if (player == null) {
            return 0;
        }

        Counts counts = counts(context, lastArgument);
        if (counts.isZero()) {
            context.getSource().sendFailure(Component.literal(
                "Give at least one Gold, Silver, or Bronze coin."
            ));
            return 0;
        }

        try {
            if (!canGrantCurrency(player, counts)) {
                context.getSource().sendFailure(Component.literal(
                    "Your inventory needs one existing coin stack or one empty slot per denomination."
                ));
                return 0;
            }
            grantCurrency(player, counts);
        } catch (IllegalStateException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        player.getInventory().setChanged();
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Gave " + counts.gold() + " Gold, " + counts.silver() + " Silver, "
                    + counts.bronze() + " Bronze coins."
            ),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int take(CommandContext<CommandSourceStack> context, String lastArgument) {
        ServerPlayer player = TensuraDungeonCommands.getPlayer(context.getSource());
        if (player == null) {
            return 0;
        }

        Counts counts = counts(context, lastArgument);
        long priceBronze;
        try {
            priceBronze = counts.canonicalBronze();
        } catch (ArithmeticException exception) {
            context.getSource().sendFailure(Component.literal("That currency amount is too large."));
            return 0;
        }
        if (priceBronze <= 0L) {
            context.getSource().sendFailure(Component.literal(
                "Take at least one Gold, Silver, or Bronze coin."
            ));
            return 0;
        }

        var plan = DungeonShopCurrency.prepare(player, priceBronze);
        if (plan.isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                "Not enough equivalent Bronze/Silver/Gold/Stellar Gold currency. This fixture grants Bronze/Silver/Gold; use vanilla /give for Stellar Gold."
            ));
            return 0;
        }
        try {
            DungeonShopCurrency.debit(player, plan.get());
        } catch (RuntimeException exception) {
            context.getSource().sendFailure(Component.literal(
                "Currency debit failed safely: " + exception.getMessage()
            ));
            return 0;
        }
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Took " + counts.gold() + " Gold, " + counts.silver() + " Silver, "
                    + counts.bronze() + " Bronze coins."
            ),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int balance(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = TensuraDungeonCommands.getPlayer(context.getSource());
        if (player == null) {
            return 0;
        }
        int gold = count(player, DungeonCurrencyPolicy.GOLD_COIN_ITEM_ID);
        int silver = count(player, DungeonCurrencyPolicy.SILVER_COIN_ITEM_ID);
        int bronze = count(player, DungeonCurrencyPolicy.BRONZE_COIN_ITEM_ID);
        long canonicalBronze = new Counts(gold, silver, bronze).canonicalBronze();
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Balance: " + gold + " Gold, " + silver + " Silver, " + bronze
                    + " Bronze | canonical=" + canonicalBronze + " Bronze"
                    + " | Stellar Gold excluded"
            ),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static Counts counts(CommandContext<CommandSourceStack> context, String lastArgument) {
        int gold = IntegerArgumentType.getInteger(context, "gold");
        int silver = lastArgument.equals("silver") || lastArgument.equals("bronze")
            ? IntegerArgumentType.getInteger(context, "silver") : 0;
        int bronze = lastArgument.equals("bronze")
            ? IntegerArgumentType.getInteger(context, "bronze") : 0;
        return new Counts(gold, silver, bronze);
    }

    private static boolean canGrantCurrency(ServerPlayer player, Counts counts) {
        boolean[] emptySlots = new boolean[player.getInventory().getContainerSize()];
        for (int slot = 0; slot < emptySlots.length; slot++) {
            emptySlots[slot] = player.getInventory().getItem(slot).isEmpty();
        }
        return canGrantDenomination(player, DungeonCurrencyPolicy.GOLD_COIN_ITEM_ID, counts.gold(), emptySlots)
            && canGrantDenomination(player, DungeonCurrencyPolicy.SILVER_COIN_ITEM_ID, counts.silver(), emptySlots)
            && canGrantDenomination(player, DungeonCurrencyPolicy.BRONZE_COIN_ITEM_ID, counts.bronze(), emptySlots);
    }

    private static boolean canGrantDenomination(
        ServerPlayer player,
        String itemId,
        int count,
        boolean[] emptySlots
    ) {
        if (count == 0) {
            return true;
        }
        Item item = resolve(itemId);
        if (item == null) {
            throw new IllegalStateException("Currency item is not registered: " + itemId);
        }
        for (int slot = 0; slot < emptySlots.length; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                int available = Math.max(0, MAX_PERSISTED_STACK_COUNT - stack.getCount());
                int moved = Math.min(available, count);
                count -= moved;
                if (count == 0) {
                    return true;
                }
            }
        }
        for (int slot = 0; slot < emptySlots.length; slot++) {
            if (emptySlots[slot] && count > 0) {
                emptySlots[slot] = false;
                count -= Math.min(MAX_PERSISTED_STACK_COUNT, count);
            }
        }
        return count == 0;
    }

    private static void grantCurrency(ServerPlayer player, Counts counts) {
        grantDenomination(player, DungeonCurrencyPolicy.GOLD_COIN_ITEM_ID, counts.gold());
        grantDenomination(player, DungeonCurrencyPolicy.SILVER_COIN_ITEM_ID, counts.silver());
        grantDenomination(player, DungeonCurrencyPolicy.BRONZE_COIN_ITEM_ID, counts.bronze());
    }

    private static void grantDenomination(ServerPlayer player, String itemId, int count) {
        if (count == 0) {
            return;
        }
        Item item = resolve(itemId);
        if (item == null) {
            throw new IllegalStateException("Currency item is not registered: " + itemId);
        }
        int remaining = count;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item) && remaining > 0) {
                int available = Math.max(0, MAX_PERSISTED_STACK_COUNT - stack.getCount());
                int moved = Math.min(available, remaining);
                if (moved > 0) {
                    stack.grow(moved);
                    remaining -= moved;
                }
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).isEmpty() && remaining > 0) {
                int moved = Math.min(MAX_PERSISTED_STACK_COUNT, remaining);
                player.getInventory().setItem(slot, new ItemStack(item, moved));
                remaining -= moved;
            }
        }
        if (remaining != 0) {
            throw new IllegalStateException("Currency fixture inventory placement changed during execution.");
        }
    }

    private static int count(ServerPlayer player, String itemId) {
        Item item = resolve(itemId);
        return item == null ? 0 : player.getInventory().countItem(item);
    }

    private static Item resolve(String itemId) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElse(null);
    }

    private record Counts(int gold, int silver, int bronze) {
        private Counts {
            if (gold < 0 || silver < 0 || bronze < 0) {
                throw new IllegalArgumentException("Currency counts cannot be negative");
            }
        }

        private boolean isZero() {
            return gold == 0 && silver == 0 && bronze == 0;
        }

        private long canonicalBronze() {
            return Math.addExact(
                Math.addExact(
                    Math.multiplyExact((long) gold, DungeonCurrencyPolicy.BRONZE_PER_GOLD),
                    Math.multiplyExact((long) silver, DungeonCurrencyPolicy.BRONZE_PER_SILVER)
                ),
                bronze
            );
        }
    }
}
