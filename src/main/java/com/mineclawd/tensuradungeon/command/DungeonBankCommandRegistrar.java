package com.mineclawd.tensuradungeon.command;

import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.getPlayer;

import com.mineclawd.tensuradungeon.network.DungeonBankSnapshotFactory;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.slf4j.Logger;

final class DungeonBankCommandRegistrar {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DungeonBankCommandRegistrar() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("bank")
            .then(Commands.literal("open")
                .executes(DungeonBankCommandRegistrar::open)
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(DungeonBankCommandRegistrar::openForPlayer))));
    }

    private static int open(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context.getSource());
        return player == null ? 0 : open(context.getSource(), player);
    }

    private static int openForPlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = resolveOnlinePlayer(context);
        if (player == null) {
            CommandSourceStack source = context.getSource();
            LOGGER.warn(
                "Dungeon Bank NPC open could not resolve target: input='{}' sourceEntity={} onlinePlayers={}",
                context.getInput(),
                source.getEntity(),
                onlinePlayerNames(source)
            );
            source.sendFailure(Component.literal(
                "Unable to resolve the player for Dungeon Bank opening; use an online player name."
            ));
            return 0;
        }
        return open(context.getSource(), player);
    }

    private static ServerPlayer resolveOnlinePlayer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        PlayerList players = source.getServer().getPlayerList();
        String target = targetToken(context.getInput());

        if (target == null || target.isBlank() || target.startsWith("@")) {
            if (source.getEntity() instanceof ServerPlayer player) {
                return player;
            }
            return onlyOnlinePlayer(players);
        }

        ServerPlayer direct = players.getPlayerByName(target);
        if (direct != null) return direct;
        for (ServerPlayer candidate : players.getPlayers()) {
            if (candidate.getName().getString().equalsIgnoreCase(target)) {
                return candidate;
            }
        }

        return onlyOnlinePlayer(players);
    }

    private static String targetToken(String input) {
        if (input == null) return null;
        int end = input.length();
        while (end > 0 && Character.isWhitespace(input.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && !Character.isWhitespace(input.charAt(start - 1))) {
            start--;
        }
        if (start >= end) return null;
        String target = input.substring(start, end).trim();
        if (target.length() >= 2
            && ((target.startsWith("\"") && target.endsWith("\""))
                || (target.startsWith("'") && target.endsWith("'")))) {
            target = target.substring(1, target.length() - 1).trim();
        }
        return target;
    }

    private static ServerPlayer onlyOnlinePlayer(PlayerList players) {
        return players.getPlayers().size() == 1 ? players.getPlayers().get(0) : null;
    }

    private static String onlinePlayerNames(CommandSourceStack source) {
        return source.getServer().getPlayerList().getPlayers().stream()
            .map(player -> player.getName().getString())
            .toList()
            .toString();
    }

    private static int open(CommandSourceStack source, ServerPlayer player) {
        try {
            DungeonBankSnapshotFactory.sendOpen(player);
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Dungeon Bank snapshot creation failed for player {} ({})",
                player.getName().getString(),
                player.getUUID(),
                exception
            );
            source.sendFailure(Component.literal(
                "Dungeon Bank could not open; the server rejected the bank snapshot."
            ));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Opened Mjollmile's Dungeon Bank."), false);
        return Command.SINGLE_SUCCESS;
    }
}
