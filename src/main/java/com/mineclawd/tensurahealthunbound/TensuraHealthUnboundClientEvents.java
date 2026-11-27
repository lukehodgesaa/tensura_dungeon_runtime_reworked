package com.mineclawd.tensurahealthunbound;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = TensuraHealthUnbound.MOD_ID, value = Dist.CLIENT)
public final class TensuraHealthUnboundClientEvents {
    private TensuraHealthUnboundClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        DungeonHealthCap.liftGlobalCap();
    }
}
