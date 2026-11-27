package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonPreparationReadyPayload(
    int floor,
    int elapsedSeconds
) implements CustomPacketPayload {
    public static final Type<DungeonPreparationReadyPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID,
            "preparation_ready"
        )
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonPreparationReadyPayload> STREAM_CODEC =
        StreamCodec.of(DungeonPreparationReadyPayload::write, DungeonPreparationReadyPayload::read);

    public DungeonPreparationReadyPayload {
        if (floor < 1 || floor > 10_000) {
            throw new IllegalArgumentException("Invalid prepared dungeon floor");
        }
        elapsedSeconds = Math.max(0, Math.min(359_999, elapsedSeconds));
    }

    public DungeonPreparationReadyPayload(int floor) {
        this(floor, 0);
    }

    private static void write(FriendlyByteBuf buffer, DungeonPreparationReadyPayload payload) {
        buffer.writeVarInt(payload.floor);
        buffer.writeVarInt(payload.elapsedSeconds);
    }

    private static DungeonPreparationReadyPayload read(FriendlyByteBuf buffer) {
        return new DungeonPreparationReadyPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
