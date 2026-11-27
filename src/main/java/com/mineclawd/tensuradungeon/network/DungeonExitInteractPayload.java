package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonExitInteractPayload() implements CustomPacketPayload {
    public static final Type<DungeonExitInteractPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "exit_portal_interact")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonExitInteractPayload> STREAM_CODEC =
        StreamCodec.of((buf, payload) -> { }, buf -> new DungeonExitInteractPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
