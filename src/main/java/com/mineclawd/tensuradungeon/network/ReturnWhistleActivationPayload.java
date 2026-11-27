package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ReturnWhistleActivationPayload(ItemStack whistle)
    implements CustomPacketPayload {
    public static final Type<ReturnWhistleActivationPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID, "return_whistle_activation"
        )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ReturnWhistleActivationPayload>
        STREAM_CODEC = StreamCodec.of(
            ReturnWhistleActivationPayload::write,
            ReturnWhistleActivationPayload::read
        );

    public ReturnWhistleActivationPayload {
        if (whistle == null || whistle.isEmpty()) {
            throw new IllegalArgumentException("Return Whistle activation requires one exact item");
        }
        whistle = whistle.copy();
        whistle.setCount(1);
    }

    private static void write(
        RegistryFriendlyByteBuf buffer,
        ReturnWhistleActivationPayload payload
    ) {
        ItemStack.STREAM_CODEC.encode(buffer, payload.whistle);
    }

    private static ReturnWhistleActivationPayload read(RegistryFriendlyByteBuf buffer) {
        return new ReturnWhistleActivationPayload(ItemStack.STREAM_CODEC.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
