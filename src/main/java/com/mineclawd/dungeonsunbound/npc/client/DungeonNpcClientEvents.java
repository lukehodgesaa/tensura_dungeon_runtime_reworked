package com.mineclawd.dungeonsunbound.npc.client;

import com.mineclawd.dungeonsunbound.TensuraDungeonsUnbound;
import com.mineclawd.dungeonsunbound.npc.DungeonNpcEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class DungeonNpcClientEvents {
    private DungeonNpcClientEvents() {
    }

    @EventBusSubscriber(modid = TensuraDungeonsUnbound.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(DungeonNpcEntities.DUNGEON_NPC.get(), DungeonNpcRenderer::new);
        }
    }

    @EventBusSubscriber(modid = TensuraDungeonsUnbound.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            DungeonDialogueClient.reset();
        }
    }
}
