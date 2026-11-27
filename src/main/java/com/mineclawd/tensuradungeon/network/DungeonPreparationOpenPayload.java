package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonPreparationOpenPayload() implements CustomPacketPayload {
    public static final Type<DungeonPreparationOpenPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "preparation_open")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonPreparationOpenPayload> STREAM_CODEC =
        StreamCodec.of(DungeonPreparationOpenPayload::write, DungeonPreparationOpenPayload::read);

    private static void write(FriendlyByteBuf buffer, DungeonPreparationOpenPayload payload) {
    }

    private static DungeonPreparationOpenPayload read(FriendlyByteBuf buffer) {
        return new DungeonPreparationOpenPayload();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
