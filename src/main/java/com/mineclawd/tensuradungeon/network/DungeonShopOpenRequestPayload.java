package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonShopOpenRequestPayload(
    DungeonShopSnapshotPayload.Tab tab,
    long requestId
) implements CustomPacketPayload {
    public static final Type<DungeonShopOpenRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_shop_open_request")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonShopOpenRequestPayload> STREAM_CODEC =
        StreamCodec.of(DungeonShopOpenRequestPayload::write, DungeonShopOpenRequestPayload::read);

    public DungeonShopOpenRequestPayload {
        tab = Objects.requireNonNull(tab, "Dungeon Shop requested tab");
        if (requestId <= 0L) {
            throw new IllegalArgumentException("Client Dungeon Shop request identity must be positive");
        }
    }

    private static void write(FriendlyByteBuf buffer, DungeonShopOpenRequestPayload payload) {
        buffer.writeEnum(payload.tab);
        buffer.writeVarLong(payload.requestId);
    }

    private static DungeonShopOpenRequestPayload read(FriendlyByteBuf buffer) {
        return new DungeonShopOpenRequestPayload(
            buffer.readEnum(DungeonShopSnapshotPayload.Tab.class),
            buffer.readVarLong()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
