package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ReturnWhistleReleasePayload() implements CustomPacketPayload {
    public static final Type<ReturnWhistleReleasePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "return_whistle_release")
    );
    public static final StreamCodec<FriendlyByteBuf, ReturnWhistleReleasePayload> STREAM_CODEC =
        StreamCodec.of(ReturnWhistleReleasePayload::write, ReturnWhistleReleasePayload::read);

    private static void write(FriendlyByteBuf buffer, ReturnWhistleReleasePayload payload) {
    }

    private static ReturnWhistleReleasePayload read(FriendlyByteBuf buffer) {
        return new ReturnWhistleReleasePayload();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
