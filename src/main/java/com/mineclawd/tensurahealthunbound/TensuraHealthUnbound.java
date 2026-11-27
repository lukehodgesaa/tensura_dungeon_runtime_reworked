package com.mineclawd.tensurahealthunbound;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

@Mod(TensuraHealthUnbound.MOD_ID)
public final class TensuraHealthUnbound {
    public static final String MOD_ID = "tensura_health_unbound";

    public TensuraHealthUnbound(IEventBus modBus) {
        modBus.addListener(FMLLoadCompleteEvent.class,
            event -> event.enqueueWork(DungeonHealthCap::liftGlobalCap));
    }
}
