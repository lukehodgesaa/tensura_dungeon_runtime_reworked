package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonPreparationProtectionWarningPayload(
    UUID confirmationId,
    long partyRevision,
    String message
) implements CustomPacketPayload {
    public static final Type<DungeonPreparationProtectionWarningPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID,
            "preparation_protection_warning"
        )
    );
    public static final StreamCodec<
        FriendlyByteBuf,
        DungeonPreparationProtectionWarningPayload
    > STREAM_CODEC = StreamCodec.of(
        DungeonPreparationProtectionWarningPayload::write,
        DungeonPreparationProtectionWarningPayload::read
    );

    public DungeonPreparationProtectionWarningPayload {
        if (confirmationId == null) {
            throw new IllegalArgumentException("Missing protection confirmation ID");
        }
        partyRevision = Math.max(0L, partyRevision);
        message = bounded(message, 512);
    }

    private static void write(
        FriendlyByteBuf buffer,
        DungeonPreparationProtectionWarningPayload payload
    ) {
        buffer.writeUUID(payload.confirmationId);
        buffer.writeVarLong(payload.partyRevision);
        buffer.writeUtf(payload.message, 512);
    }

    private static DungeonPreparationProtectionWarningPayload read(FriendlyByteBuf buffer) {
        return new DungeonPreparationProtectionWarningPayload(
            buffer.readUUID(),
            buffer.readVarLong(),
            buffer.readUtf(512)
        );
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.substring(0, Math.min(max, value.length()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
