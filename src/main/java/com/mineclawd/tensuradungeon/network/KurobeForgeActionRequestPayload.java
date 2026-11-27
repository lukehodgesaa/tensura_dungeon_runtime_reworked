package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record KurobeForgeActionRequestPayload(
    int schemaVersion,
    long requestId,
    long observedRevision,
    KurobeForgeSnapshotPayload.Service service,
    String weaponId,
    String coreId,
    int coreSlot
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 2;
    public static final Type<KurobeForgeActionRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "kurobe_forge_action_request")
    );
    public static final StreamCodec<FriendlyByteBuf, KurobeForgeActionRequestPayload> STREAM_CODEC =
        StreamCodec.of(KurobeForgeActionRequestPayload::write, KurobeForgeActionRequestPayload::read);

    public KurobeForgeActionRequestPayload {
        if (schemaVersion < 0 || requestId <= 0L || observedRevision < 0L) {
            throw new IllegalArgumentException("Kurobe action identity is invalid");
        }
        service = Objects.requireNonNull(service, "Kurobe action service");
        weaponId = weaponId == null ? "" : weaponId;
        if (weaponId.isBlank() || weaponId.length() > 160) {
            throw new IllegalArgumentException("Kurobe action weapon identity is invalid");
        }
        coreId = coreId == null ? "" : coreId;
        if (coreId.length() > 36 || coreSlot < -1 || coreSlot >= KurobeForgeSnapshotPayload.MAX_CORES) {
            throw new IllegalArgumentException("Kurobe action Core selection is invalid");
        }
        if (coreId.isBlank() != (coreSlot == -1)) {
            throw new IllegalArgumentException("Kurobe action Core identity and slot disagree");
        }
    }

    private static void write(FriendlyByteBuf buffer, KurobeForgeActionRequestPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.requestId);
        buffer.writeVarLong(payload.observedRevision);
        buffer.writeEnum(payload.service);
        buffer.writeUtf(payload.weaponId, 160);
        buffer.writeUtf(payload.coreId, 36);
        buffer.writeVarInt(payload.coreSlot + 1);
    }

    private static KurobeForgeActionRequestPayload read(FriendlyByteBuf buffer) {
        return new KurobeForgeActionRequestPayload(
            buffer.readVarInt(), buffer.readVarLong(), buffer.readVarLong(),
            buffer.readEnum(KurobeForgeSnapshotPayload.Service.class), buffer.readUtf(160),
            buffer.readUtf(36), buffer.readVarInt() - 1
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
