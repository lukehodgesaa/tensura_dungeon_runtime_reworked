package com.mineclawd.tensurafogbarriers.network;

import com.mineclawd.tensurafogbarriers.TensuraFogBarriers;
import com.mineclawd.tensurafogbarriers.service.FogBarrierService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CycleFogBarrierPayload(int panelId) implements CustomPacketPayload {
    public static final Type<CycleFogBarrierPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraFogBarriers.MOD_ID, "cycle_panel_color")
    );
    public static final StreamCodec<FriendlyByteBuf, CycleFogBarrierPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeVarInt(payload.panelId),
        buffer -> new CycleFogBarrierPayload(buffer.readVarInt())
    );

    public static void handle(CycleFogBarrierPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            FogBarrierService.get(player.getServer()).cycleVariant(player, payload.panelId);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
