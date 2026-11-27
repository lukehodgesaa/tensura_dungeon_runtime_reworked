package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.event.OverworldRiftEvents;
import com.mineclawd.tensuradungeon.runtime.OverworldRiftEventData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

final class OverworldRiftCommandRegistrar {
    private OverworldRiftCommandRegistrar() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("rift")
            .then(Commands.literal("open").executes(context -> open(context.getSource())))
            .then(Commands.literal("close").executes(context -> close(context.getSource())))
            .then(Commands.literal("status").executes(context -> status(context.getSource()))));
    }

    private static int open(CommandSourceStack source) {
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        if (!Level.OVERWORLD.equals(level.dimension())) {
            source.sendFailure(Component.literal("The Shattered Halo can only open in the Overworld."));
            return 0;
        }
        BlockPos requestedAnchor = player.blockPosition().relative(player.getDirection(), 224);
        if (!OverworldRiftEventData.get(level).forceOpen(level, requestedAnchor)) {
            source.sendFailure(Component.literal(
                "Rift opening failed: configure the Dungeon HUB spawn and stand over solid terrain."
            ));
            return 0;
        }
        OverworldRiftEvents.announceForcedOpen(level);
        source.sendSuccess(() -> Component.literal(
            "Opened the Shattered Halo above " + TensuraDungeonCommands.formatPos(
                OverworldRiftEventData.get(level).snapshot().anchor()
            ) + "."
        ), true);
        return 1;
    }

    private static int close(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        if (!OverworldRiftEventData.get(overworld).forceClose(overworld)) {
            source.sendFailure(Component.literal("No Overworld rift is active."));
            return 0;
        }
        OverworldRiftEvents.announceForcedClose(overworld);
        source.sendSuccess(() -> Component.literal("Closed the Shattered Halo."), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        OverworldRiftEventData.Snapshot snapshot = OverworldRiftEventData.get(overworld).snapshot();
        BlockPos anchor = snapshot.anchor();
        source.sendSuccess(() -> Component.literal(
            snapshot.active()
                ? "Shattered Halo: ACTIVE at " + TensuraDungeonCommands.formatPos(anchor)
                    + " | closes at game time " + snapshot.endsAt()
                : "Shattered Halo: INACTIVE | next daily roll at game time " + snapshot.nextRollAt()
        ), false);
        return 1;
    }
}
