package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceRecoveryService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonInstanceRecoveryEvents {
    private DungeonInstanceRecoveryEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonInstanceRecoveryService.suspendOnLogout(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DungeonInstanceRecoveryService.ResumeResult result =
            DungeonInstanceRecoveryService.resumeOnLogin(player);
        if (result.handled() && !result.succeeded()) {
            player.sendSystemMessage(Component.literal(
                result.retryable()
                    ? "Dungeon reconnect is waiting for the exact subordinate entity to load; "
                        + "resuming automatically."
                    : "Dungeon reconnect is paused: " + result.reason()
            ));
        }
    }
}
