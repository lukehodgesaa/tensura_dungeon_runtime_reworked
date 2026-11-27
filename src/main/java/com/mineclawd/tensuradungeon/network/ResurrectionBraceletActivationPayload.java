package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ResurrectionBraceletActivationPayload(ItemStack bracelet)
    implements CustomPacketPayload {
    public static final Type<ResurrectionBraceletActivationPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID, "resurrection_bracelet_activation"
        )
    );
    public static final StreamCodec<
        RegistryFriendlyByteBuf,
        ResurrectionBraceletActivationPayload
    > STREAM_CODEC = StreamCodec.of(
        ResurrectionBraceletActivationPayload::write,
        ResurrectionBraceletActivationPayload::read
    );

    public ResurrectionBraceletActivationPayload {
        if (bracelet == null || bracelet.isEmpty()) {
            throw new IllegalArgumentException("Bracelet activation requires one exact item");
        }
        bracelet = bracelet.copy();
        bracelet.setCount(1);
    }

    private static void write(
        RegistryFriendlyByteBuf buffer,
        ResurrectionBraceletActivationPayload payload
    ) {
        ItemStack.STREAM_CODEC.encode(buffer, payload.bracelet);
    }

    private static ResurrectionBraceletActivationPayload read(
        RegistryFriendlyByteBuf buffer
    ) {
        return new ResurrectionBraceletActivationPayload(
            ItemStack.STREAM_CODEC.decode(buffer)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
