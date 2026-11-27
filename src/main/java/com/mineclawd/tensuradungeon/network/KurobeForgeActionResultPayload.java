package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record KurobeForgeActionResultPayload(
    int schemaVersion,
    long requestId,
    ResultStatus status,
    String message,
    KurobeForgeSnapshotPayload snapshot
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 2;
    public static final Type<KurobeForgeActionResultPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "kurobe_forge_action_result")
    );
    public static final StreamCodec<FriendlyByteBuf, KurobeForgeActionResultPayload> STREAM_CODEC =
        StreamCodec.of(KurobeForgeActionResultPayload::write, KurobeForgeActionResultPayload::read);

    public KurobeForgeActionResultPayload {
        if (schemaVersion < 0 || requestId < 0L) {
            throw new IllegalArgumentException("Kurobe result identity is invalid");
        }
        status = Objects.requireNonNull(status, "Kurobe result status");
        message = message == null ? "" : message;
        if (message.length() > 256) throw new IllegalArgumentException("Kurobe result message is too long");
        snapshot = Objects.requireNonNull(snapshot, "Kurobe result snapshot");
    }

    public boolean supportedSchema() {
        return schemaVersion == CURRENT_SCHEMA;
    }

    private static void write(FriendlyByteBuf buffer, KurobeForgeActionResultPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.requestId);
        buffer.writeEnum(payload.status);
        buffer.writeUtf(payload.message, 256);
        KurobeForgeSnapshotPayload.STREAM_CODEC.encode(buffer, payload.snapshot);
    }

    private static KurobeForgeActionResultPayload read(FriendlyByteBuf buffer) {
        return new KurobeForgeActionResultPayload(
            buffer.readVarInt(), buffer.readVarLong(), buffer.readEnum(ResultStatus.class),
            buffer.readUtf(256), KurobeForgeSnapshotPayload.STREAM_CODEC.decode(buffer)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum ResultStatus {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        INVENTORY_FULL,
        STALE_REVISION,
        WEAPON_NOT_FOUND,
        WEAPON_INELIGIBLE,
        MAXIMUM_RANK,
        MAXIMUM_GRADE,
        FLOOR_LOCKED,
        MISSING_MATERIALS,
        NATIVE_REQUIREMENT,
        CERTIFICATION_REQUIRED,
        SATCHEL_REQUIRED,
        SATCHEL_FULL,
        CORE_NOT_SELECTED,
        CORE_NOT_FOUND,
        CORE_INCOMPATIBLE,
        SOCKET_OCCUPIED,
        NO_SOCKETED_CORE,
        CORE_BINDING_CONFLICT,
        CORRUPT_WEAPON_DATA,
        ALREADY_FULL,
        DUNGEON_ACTIVE,
        REPLAYED_REQUEST,
        UNSUPPORTED_ACTION,
        INVALID_REQUEST,
        INTERNAL_ERROR
    }
}
