package com.mineclawd.tensurahealthunbound;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@EventBusSubscriber(modid = TensuraHealthUnbound.MOD_ID)
public final class TensuraHealthUnboundEvents {
    private TensuraHealthUnboundEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        DungeonHealthCap.liftGlobalCap();
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        float max = living.getMaxHealth();
        if (living.getHealth() > max) {
            living.setHealth(max);
        }
    }
}
