package com.mineclawd.tensuraadditions.compat.moreskills;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = TensuraAdditions.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class MoreSkillsCompatEvents {
    private MoreSkillsCompatEvents() {
    }

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(BerettaAttackDataLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tensura_additions")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("beretta_attack")
                                .then(Commands.argument("attack", StringArgumentType.word())
                                        .executes(context -> {
                                            var source = context.getSource();
                                            var result = BerettaOverlayManager.startNearest(
                                                    source.getLevel(),
                                                    source.getPosition(),
                                                    StringArgumentType.getString(context, "attack")
                                            );
                                            source.sendSuccess(
                                                    () -> Component.literal(result.message()),
                                                    true
                                            );
                                            return result.success() ? 1 : 0;
                                        }))));
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (MoreSkillsCompat.isLoaded()) {
            BerettaOverlayManager.tickActive();
        }
    }
}
