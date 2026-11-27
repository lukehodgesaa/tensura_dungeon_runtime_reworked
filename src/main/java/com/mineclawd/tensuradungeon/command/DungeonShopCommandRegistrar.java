package com.mineclawd.tensuradungeon.command;

import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.getPlayer;

import com.mineclawd.tensuradungeon.network.DungeonShopSnapshotFactory;
import com.mineclawd.tensuradungeon.runtime.CosmeticKey;
import com.mineclawd.tensuradungeon.runtime.DungeonShopConfiguration;
import com.mineclawd.tensuradungeon.runtime.DungeonShopStockData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class DungeonShopCommandRegistrar {
    private DungeonShopCommandRegistrar() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> shop = Commands.literal("shop");
        shop.then(Commands.literal("open")
            .executes(DungeonShopCommandRegistrar::open)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(DungeonShopCommandRegistrar::openForPlayer)));
        shop.then(Commands.literal("test-weekly")
            .then(Commands.literal("frey")
                .executes(context -> forceWeekly(context, CosmeticKey.HUD_FREY)))
            .then(Commands.literal("gothic")
                .executes(context -> forceWeekly(context, CosmeticKey.HUD_GOTHIC))));
        shop.then(Commands.literal("config")
            .then(Commands.literal("status")
                .executes(DungeonShopCommandRegistrar::configStatus))
            .then(Commands.literal("validate")
                .executes(DungeonShopCommandRegistrar::validateConfig)));
        root.then(shop);
    }

    private static int configStatus(CommandContext<CommandSourceStack> context) {
        DungeonShopConfiguration.Snapshot active = DungeonShopConfiguration.current();
        context.getSource().sendSuccess(() -> Component.literal(
            "Active Dungeon Shop config (schema " + DungeonShopConfiguration.schemaVersion()
                + "): " + active.essentials().size() + " essentials, "
                + active.todaysFinds().size() + " Today's Finds, "
                + active.weekly().size() + " weekly, "
                + active.barters().size() + " barters."
        ), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "Directory: " + DungeonShopConfiguration.runtimeDirectory()
        ), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "Grade price multipliers: " + active.gradePriceMultipliers().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().displayName() + " "
                    + entry.getValue().numerator() + "/" + entry.getValue().denominator())
                .collect(java.util.stream.Collectors.joining(", "))
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int validateConfig(CommandContext<CommandSourceStack> context) {
        DungeonShopConfiguration.ValidationReport report =
            DungeonShopConfiguration.validateRuntimeConfig();
        if (!report.valid()) {
            context.getSource().sendFailure(Component.literal(
                "Dungeon Shop config is invalid: " + report.message()
            ));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
            report.message() + ": " + report.essentials() + " essentials, "
                + report.todaysFinds() + " Today's Finds, " + report.weekly()
                + " weekly, " + report.barters() + " barters. Active config was not changed."
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int open(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context.getSource());
        return player == null ? 0 : open(context.getSource(), player);
    }

    private static int openForPlayer(CommandContext<CommandSourceStack> context) {
        try {
            return open(context.getSource(), EntityArgument.getPlayer(context, "player"));
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(
                "Unable to resolve the player for Dungeon Shop opening."
            ));
            return 0;
        }
    }

    private static int open(CommandSourceStack source, ServerPlayer player) {
        DungeonShopSnapshotFactory.sendOpen(player);
        source.sendSuccess(() -> Component.literal("Opened the authoritative Dungeon Shop stock."), false);
        if (source.getEntity() != player) {
            player.sendSystemMessage(Component.literal("The Dungeon Shop has been opened."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int forceWeekly(
        CommandContext<CommandSourceStack> context,
        CosmeticKey cosmetic
    ) {
        ServerPlayer player = getPlayer(context.getSource());
        if (player == null) {
            return 0;
        }
        long weekKey = LocalDate.now(ZoneId.systemDefault())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toEpochDay();
        boolean applied = DungeonShopStockData.get(player.serverLevel())
            .forceWeeklyOffer(player.getUUID(), weekKey, cosmetic);
        if (!applied) {
            context.getSource().sendFailure(Component.literal(
                "The current Weekly rotation is newer than the test fixture request."
            ));
            return 0;
        }
        DungeonShopSnapshotFactory.sendOpen(player);
        context.getSource().sendSuccess(() -> Component.literal(
            "Added the " + cosmetic.id() + " Weekly offer for this player and week."
        ), false);
        player.sendSystemMessage(Component.literal(
            "Weekly Shop test offer added. Select WEEKLY after the Shop refreshes."
        ));
        return Command.SINGLE_SUCCESS;
    }
}
