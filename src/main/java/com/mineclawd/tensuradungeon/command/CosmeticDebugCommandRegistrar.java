package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.network.DungeonCosmeticSnapshotPayload;
import com.mineclawd.tensuradungeon.runtime.CosmeticKey;
import com.mineclawd.tensuradungeon.runtime.DungeonCosmeticData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

final class CosmeticDebugCommandRegistrar {
    private CosmeticDebugCommandRegistrar() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("hudpreset")
            .then(Commands.literal("gothic").executes(CosmeticDebugCommandRegistrar::equipGothic))
            .then(Commands.literal("frey").executes(CosmeticDebugCommandRegistrar::equipFrey))
            .then(Commands.literal("rimuru").executes(CosmeticDebugCommandRegistrar::equipRimuru))
            .then(Commands.literal("status").executes(CosmeticDebugCommandRegistrar::status)));
    }

    static int equipGothic(CommandContext<CommandSourceStack> context) {
        return grantAndEquip(context, CosmeticKey.HUD_GOTHIC, "Gothic");
    }

    static int equipFrey(CommandContext<CommandSourceStack> context) {
        return grantAndEquip(context, CosmeticKey.HUD_FREY, "Frey");
    }

    static int equipRimuru(CommandContext<CommandSourceStack> context) {
        return grantAndEquip(context, CosmeticKey.HUD_RIMURU, "Rimuru");
    }

    static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) {
            return 0;
        }
        DungeonCosmeticData.Snapshot snapshot =
            DungeonCosmeticData.get(player.serverLevel()).snapshot(player.getUUID());
        source.sendSuccess(() -> Component.literal(
            "HUD preset: " + snapshot.equippedHud().id()
                + " | Quest cosmetic: " + snapshot.equippedQuest().id()
                + " | Revision: " + snapshot.revision()
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int grantAndEquip(
        CommandContext<CommandSourceStack> context,
        CosmeticKey preset,
        String displayName
    ) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) {
            return 0;
        }

        DungeonCosmeticData data = DungeonCosmeticData.get(player.serverLevel());
        DungeonCosmeticData.MutationResult grant = data.grant(player.getUUID(), preset);
        DungeonCosmeticData.MutationResult equip =
            data.equip(player.getUUID(), preset, grant.snapshot().revision());
        if (equip.status() != DungeonCosmeticData.MutationStatus.EQUIPPED
            && equip.status() != DungeonCosmeticData.MutationStatus.NO_CHANGE) {
            source.sendFailure(Component.literal(
                "Unable to equip " + displayName + " HUD preset: " + equip.status()
            ));
            return 0;
        }

        String message = displayName + " HUD preset equipped.";
        if (NetworkRegistry.hasChannel(player.connection, DungeonCosmeticSnapshotPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, DungeonCosmeticSnapshotPayload.from(
                equip.snapshot(),
                equip.status(),
                message
            ));
        }
        source.sendSuccess(() -> Component.literal(
            message + " Quest cosmetic remains " + equip.snapshot().equippedQuest().id() + '.'
        ), false);
        return Command.SINGLE_SUCCESS;
    }
}
