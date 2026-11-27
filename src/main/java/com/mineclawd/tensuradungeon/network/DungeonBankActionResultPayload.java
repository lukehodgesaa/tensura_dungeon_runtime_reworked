package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonBankActionResultPayload(
    int schemaVersion,
    long requestId,
    ResultStatus status,
    String message,
    DungeonBankSnapshotPayload snapshot
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 1;
    public static final Type<DungeonBankActionResultPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_bank_action_result")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonBankActionResultPayload> STREAM_CODEC =
        StreamCodec.of(DungeonBankActionResultPayload::write, DungeonBankActionResultPayload::read);

    public DungeonBankActionResultPayload {
        if (schemaVersion < 0 || requestId <= 0L) {
            throw new IllegalArgumentException("Dungeon Bank action result identity is invalid");
        }
        status = Objects.requireNonNull(status, "Dungeon Bank action result status");
        message = message == null ? "" : message;
        if (message.length() > 256) {
            throw new IllegalArgumentException("Dungeon Bank result message exceeds 256 characters");
        }
        snapshot = Objects.requireNonNull(snapshot, "Dungeon Bank result snapshot");
    }

    public boolean supportedSchema() {
        return schemaVersion == CURRENT_SCHEMA;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, DungeonBankActionResultPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.requestId);
        buffer.writeEnum(payload.status);
        buffer.writeUtf(payload.message, 256);
        DungeonBankSnapshotPayload.STREAM_CODEC.encode(buffer, payload.snapshot);
    }

    private static DungeonBankActionResultPayload read(FriendlyByteBuf buffer) {
        return new DungeonBankActionResultPayload(
            buffer.readVarInt(),
            buffer.readVarLong(),
            buffer.readEnum(ResultStatus.class),
            buffer.readUtf(256),
            DungeonBankSnapshotPayload.STREAM_CODEC.decode(buffer)
        );
    }

    public enum ResultStatus {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        INVENTORY_FULL,
        BANK_FULL,
        STALE_REVISION,
        QUOTE_EXPIRED,
        REPLAYED_REQUEST,
        MAXIMUM_GRADE,
        INVALID_REQUEST,
        INTERNAL_ERROR
    }
}
