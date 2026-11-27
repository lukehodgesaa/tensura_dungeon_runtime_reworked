package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT)
public final class DungeonScreenHudSuppression {
    private DungeonScreenHudSuppression() {}

    @SubscribeEvent
    public static void suppressCompleteHud(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof DungeonHudSuppressingScreen) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void suppressHud(RenderGuiLayerEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof DungeonHudSuppressingScreen) {
            event.setCanceled(true);
        }
    }
}
