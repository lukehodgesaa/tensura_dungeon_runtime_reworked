package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraDaemonGroundingCompat;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonDaemonGroundingEvents {
    private DungeonDaemonGroundingEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void groundDaemonBeforeTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity living) {
            TensuraDaemonGroundingCompat.forceGrounded(living);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void groundDaemonAfterTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living) {
            TensuraDaemonGroundingCompat.forceGrounded(living);
        }
    }
}
