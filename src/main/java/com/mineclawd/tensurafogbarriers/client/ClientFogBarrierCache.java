package com.mineclawd.tensurafogbarriers.client;

import com.mineclawd.tensurafogbarriers.TensuraFogBarriers;
import com.mineclawd.tensurafogbarriers.network.FogBarrierSnapshotPayload;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = TensuraFogBarriers.MOD_ID, value = Dist.CLIENT)
public final class ClientFogBarrierCache {
    private static volatile List<FogBarrierSnapshotPayload.Entry> panels = List.of();

    private ClientFogBarrierCache() {
    }

    public static void handleSnapshot(FogBarrierSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> panels = List.copyOf(payload.entries()));
    }

    public static List<FogBarrierSnapshotPayload.Entry> panels() {
        return panels;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        panels = List.of();
    }
}
