package com.mineclawd.tensuradungeon.compat;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class TensuraSkillCompatBootstrap {
    private TensuraSkillCompatBootstrap() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        TensuraSkillCompatHooks.initialize();
    }
}
