package com.mineclawd.tensuradungeon.command;

import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.getPlayer;

import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotFactory;
import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotPayload;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

final class KurobeForgeCommandRegistrar {
    private KurobeForgeCommandRegistrar() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("forge")
            .then(Commands.literal("open")
                .executes(KurobeForgeCommandRegistrar::open)
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(KurobeForgeCommandRegistrar::openForPlayer))));
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
                "Unable to resolve the player for Kurobe Forge opening."
            ));
            return 0;
        }
    }

    private static int open(CommandSourceStack source, ServerPlayer player) {
        PacketDistributor.sendToPlayer(
            player,
            KurobeForgeSnapshotFactory.create(
                player,
                KurobeForgeSnapshotPayload.Service.PROMOTE,
                0L
            )
        );
        source.sendSuccess(() -> Component.literal("Opened Kurobe's Forge."), false);
        return Command.SINGLE_SUCCESS;
    }
}
