package com.mineclawd.tensurafogbarriers.network;

import com.mineclawd.tensurafogbarriers.TensuraFogBarriers;
import com.mineclawd.tensurafogbarriers.client.ClientFogBarrierCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class FogBarrierNetwork {
    private FogBarrierNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TensuraFogBarriers.MOD_ID).versioned("2");
        registrar.playToServer(
            CycleFogBarrierPayload.TYPE,
            CycleFogBarrierPayload.STREAM_CODEC,
            CycleFogBarrierPayload::handle
        );
        registrar.playToServer(
            CycleFogBarrierStylePayload.TYPE,
            CycleFogBarrierStylePayload.STREAM_CODEC,
            CycleFogBarrierStylePayload::handle
        );
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registrar.playToClient(
                FogBarrierSnapshotPayload.TYPE,
                FogBarrierSnapshotPayload.STREAM_CODEC,
                ClientFogBarrierCache::handleSnapshot
            );
        } else {
            registrar.playToClient(
                FogBarrierSnapshotPayload.TYPE,
                FogBarrierSnapshotPayload.STREAM_CODEC,
                (payload, context) -> { }
            );
        }
    }
}
