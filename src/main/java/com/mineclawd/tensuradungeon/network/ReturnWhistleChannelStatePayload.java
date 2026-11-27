package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ReturnWhistleChannelStatePayload(
    boolean active,
    int percent,
    String message,
    Tone tone,
    int displayTicks
) implements CustomPacketPayload {
    public static final Type<ReturnWhistleChannelStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "return_whistle_channel_state")
    );
    public static final StreamCodec<FriendlyByteBuf, ReturnWhistleChannelStatePayload> STREAM_CODEC =
        StreamCodec.of(ReturnWhistleChannelStatePayload::write, ReturnWhistleChannelStatePayload::read);

    public ReturnWhistleChannelStatePayload {
        if (percent < 0 || percent > 100 || message == null || message.length() > 160
            || tone == null || displayTicks < 0 || displayTicks > 200) {
            throw new IllegalArgumentException("Invalid Return Whistle presentation state");
        }
    }

    private static void write(FriendlyByteBuf buffer, ReturnWhistleChannelStatePayload payload) {
        buffer.writeBoolean(payload.active);
        buffer.writeByte(payload.percent);
        buffer.writeUtf(payload.message, 160);
        buffer.writeEnum(payload.tone);
        buffer.writeVarInt(payload.displayTicks);
    }

    private static ReturnWhistleChannelStatePayload read(FriendlyByteBuf buffer) {
        return new ReturnWhistleChannelStatePayload(
            buffer.readBoolean(),
            buffer.readUnsignedByte(),
            buffer.readUtf(160),
            buffer.readEnum(Tone.class),
            buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Tone {
        ACTIVE,
        WARNING,
        SUCCESS
    }
}
