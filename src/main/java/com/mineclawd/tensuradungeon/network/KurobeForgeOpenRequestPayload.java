package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record KurobeForgeOpenRequestPayload(
    KurobeForgeSnapshotPayload.Service initialService,
    long requestId
) implements CustomPacketPayload {
    public static final Type<KurobeForgeOpenRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "kurobe_forge_open_request")
    );
    public static final StreamCodec<FriendlyByteBuf, KurobeForgeOpenRequestPayload> STREAM_CODEC =
        StreamCodec.of(KurobeForgeOpenRequestPayload::write, KurobeForgeOpenRequestPayload::read);

    public KurobeForgeOpenRequestPayload {
        initialService = Objects.requireNonNull(initialService, "Kurobe initial service");
        if (requestId <= 0L) throw new IllegalArgumentException("Kurobe request identity must be positive");
    }

    private static void write(FriendlyByteBuf buffer, KurobeForgeOpenRequestPayload payload) {
        buffer.writeEnum(payload.initialService);
        buffer.writeVarLong(payload.requestId);
    }

    private static KurobeForgeOpenRequestPayload read(FriendlyByteBuf buffer) {
        return new KurobeForgeOpenRequestPayload(
            buffer.readEnum(KurobeForgeSnapshotPayload.Service.class), buffer.readVarLong()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
