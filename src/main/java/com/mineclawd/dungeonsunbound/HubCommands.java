package com.mineclawd.dungeonsunbound;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = TensuraDungeonsUnbound.MOD_ID)
public final class HubCommands {
    private HubCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dungeonsunbound")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("hub")
                .then(Commands.literal("status").executes(HubCommands::status))
                .then(Commands.literal("rebuild").executes(HubCommands::rebuild)))
            .then(Commands.literal("verifytemplates").executes(HubCommands::verifyTemplates)));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        HubBootstrapData data = HubBootstrapData.get(server);
        int total = HubBootstrap.chunkCount(server);
        context.getSource().sendSuccess(() -> Component.literal(
            "Dungeons Unbound hub: state=" + data.state()
                + " chunks=" + data.nextChunk() + "/" + total
                + " npcChunks=" + data.nextEntityChunk()
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int verifyTemplates(CommandContext<CommandSourceStack> context) {
        TemplateCensusCheck.Result result = TemplateCensusCheck.run(context.getSource().getServer().overworld());
        for (String failure : result.failures()) {
            context.getSource().sendFailure(Component.literal("Census mismatch: " + failure));
        }
        context.getSource().sendSuccess(() -> Component.literal(
            "Dungeon templates: checked=" + result.checked() + " composite=" + result.skipped()
                + " disabled=" + result.disabled()
                + " failed=" + result.failures().size()
        ), false);
        return result.failures().isEmpty() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int rebuild(CommandContext<CommandSourceStack> context) {
        if (!HubBootstrap.rebuild(context.getSource().getServer())) {
            context.getSource().sendFailure(Component.literal("Hub manifest is missing; rebuild not started."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
            "Rebuilding the dungeon hub. NPCs are added again, so remove old copies first if needed."
        ), true);
        return Command.SINGLE_SUCCESS;
    }
}
