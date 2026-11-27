package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.registry.ModBlockEntities;
import com.mineclawd.tensuradungeon.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
    modid = TensuraDungeonRuntime.MOD_ID,
    bus = EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT
)
public final class ExpeditionTableClientEvents {
    private ExpeditionTableClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            ModBlockEntities.EXPEDITION_TABLE.get(),
            context -> new ExpeditionTableRenderer()
        );
        event.registerEntityRenderer(
            ModEntities.RIFT_VOID_WARP.get(),
            RiftVoidWarpRenderer::new
        );
    }
}
