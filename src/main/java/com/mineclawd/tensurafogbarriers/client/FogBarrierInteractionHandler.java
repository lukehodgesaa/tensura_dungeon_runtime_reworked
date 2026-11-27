package com.mineclawd.tensurafogbarriers.client;

import com.mineclawd.tensurafogbarriers.TensuraFogBarriers;
import com.mineclawd.tensurafogbarriers.data.FogBarrierPanel;
import com.mineclawd.tensurafogbarriers.network.CycleFogBarrierPayload;
import com.mineclawd.tensurafogbarriers.network.CycleFogBarrierStylePayload;
import com.mineclawd.tensurafogbarriers.network.FogBarrierSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TensuraFogBarriers.MOD_ID, value = Dist.CLIENT)
public final class FogBarrierInteractionHandler {
    private static final double INTERACTION_REACH = 6.0D;
    private static final double EDGE_TOLERANCE = 0.25D;

    private FogBarrierInteractionHandler() {
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        if (!minecraft.player.isShiftKeyDown()) return;
        ItemStack held = minecraft.player.getMainHandItem();
        boolean cyclingColor = held.isEmpty();
        boolean cyclingStyle = held.is(Items.CLOCK);
        if (!cyclingColor && !cyclingStyle) return;

        FogBarrierSnapshotPayload.Entry target = raycastPanel(
            minecraft.player.getEyePosition(), minecraft.player.getViewVector(1.0F)
        );
        if (target == null) return;

        if (cyclingStyle) PacketDistributor.sendToServer(new CycleFogBarrierStylePayload(target.id()));
        else PacketDistributor.sendToServer(new CycleFogBarrierPayload(target.id()));
        event.setSwingHand(true);
        event.setCanceled(true);
    }

    private static FogBarrierSnapshotPayload.Entry raycastPanel(Vec3 origin, Vec3 direction) {
        FogBarrierSnapshotPayload.Entry nearest = null;
        double nearestDistance = INTERACTION_REACH + 1.0D;
        for (FogBarrierSnapshotPayload.Entry panel : ClientFogBarrierCache.panels()) {
            double denominator = panel.axis() == FogBarrierPanel.Axis.X ? direction.x : direction.z;
            if (Math.abs(denominator) < 1.0E-7D) continue;

            double originNormal = panel.axis() == FogBarrierPanel.Axis.X ? origin.x : origin.z;
            double distance = (panel.plane() - originNormal) / denominator;
            if (distance < 0.0D || distance > INTERACTION_REACH || distance >= nearestDistance) continue;

            double y = origin.y + direction.y * distance;
            double lateral = panel.axis() == FogBarrierPanel.Axis.X
                ? origin.z + direction.z * distance
                : origin.x + direction.x * distance;
            if (y < panel.minY() - EDGE_TOLERANCE || y > panel.maxY() + EDGE_TOLERANCE) continue;
            if (lateral < panel.minLateral() - EDGE_TOLERANCE || lateral > panel.maxLateral() + EDGE_TOLERANCE) continue;

            nearest = panel;
            nearestDistance = distance;
        }
        return nearest;
    }
}
