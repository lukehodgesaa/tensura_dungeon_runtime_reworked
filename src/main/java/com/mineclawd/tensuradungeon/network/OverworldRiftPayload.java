package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.OverworldRiftGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OverworldRiftPayload(
    boolean active,
    BlockPos anchor,
    float orientationDegrees,
    long startedAt,
    long endsAt
) implements CustomPacketPayload {
    public static final Type<OverworldRiftPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "overworld_rift")
    );
    public static final StreamCodec<FriendlyByteBuf, OverworldRiftPayload> STREAM_CODEC =
        StreamCodec.of(OverworldRiftPayload::write, OverworldRiftPayload::read);

    public OverworldRiftPayload {
        anchor = anchor == null ? BlockPos.ZERO : anchor.immutable();
        if (!active) {
            anchor = BlockPos.ZERO;
            orientationDegrees = 0.0F;
            startedAt = 0L;
            endsAt = 0L;
        } else {
            orientationDegrees = OverworldRiftGeometry.normalizeOrientation(orientationDegrees);
        }
    }

    public static OverworldRiftPayload inactive() {
        return new OverworldRiftPayload(false, BlockPos.ZERO, 0.0F, 0L, 0L);
    }

    private static void write(FriendlyByteBuf buffer, OverworldRiftPayload payload) {
        buffer.writeBoolean(payload.active());
        buffer.writeBlockPos(payload.anchor());
        buffer.writeFloat(payload.orientationDegrees());
        buffer.writeLong(payload.startedAt());
        buffer.writeLong(payload.endsAt());
    }

    private static OverworldRiftPayload read(FriendlyByteBuf buffer) {
        return new OverworldRiftPayload(
            buffer.readBoolean(),
            buffer.readBlockPos(),
            buffer.readFloat(),
            buffer.readLong(),
            buffer.readLong()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
