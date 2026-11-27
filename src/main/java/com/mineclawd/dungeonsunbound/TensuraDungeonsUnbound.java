package com.mineclawd.dungeonsunbound;

import com.mineclawd.dungeonsunbound.npc.DungeonNpcEntities;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(TensuraDungeonsUnbound.MOD_ID)
public final class TensuraDungeonsUnbound {
    public static final String MOD_ID = "tensura_dungeons_unbound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TensuraDungeonsUnbound(IEventBus modEventBus) {
        DungeonNpcEntities.register(modEventBus);
        LOGGER.info("Tensura: Dungeons Unbound loaded (runtime, additions, fog barriers, HUD, UI, lock-on, health unbound, NPCs).");
    }
}
