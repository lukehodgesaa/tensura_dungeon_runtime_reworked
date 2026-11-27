package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.DungeonExitInteractPayload;
import com.mineclawd.tensuradungeon.registry.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT)
public final class DungeonExitPortalInputEvents {
    private DungeonExitPortalInputEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isUseItem()
            && event.getHand() == InteractionHand.MAIN_HAND
            && minecraft.player != null
            && minecraft.player.level().dimension().equals(ModDimensions.DUNGEON)) {
            PacketDistributor.sendToServer(new DungeonExitInteractPayload());
        }
    }
}
