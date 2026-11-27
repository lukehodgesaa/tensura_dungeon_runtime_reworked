package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonBankActionRequestPayload(
    int schemaVersion,
    long requestId,
    long snapshotRevision,
    Action action,
    String quoteId
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 1;
    public static final Type<DungeonBankActionRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_bank_action_request")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonBankActionRequestPayload> STREAM_CODEC =
        StreamCodec.of(DungeonBankActionRequestPayload::write, DungeonBankActionRequestPayload::read);

    public DungeonBankActionRequestPayload {
        if (schemaVersion < 0 || requestId <= 0L || snapshotRevision < 0L) {
            throw new IllegalArgumentException("Dungeon Bank action identity is invalid");
        }
        action = Objects.requireNonNull(action, "Dungeon Bank action");
        quoteId = quoteId == null ? "" : quoteId;
        if (quoteId.isBlank() || quoteId.length() > 96) {
            throw new IllegalArgumentException("Dungeon Bank action quote ID is invalid");
        }
    }

    public boolean supportedSchema() {
        return schemaVersion == CURRENT_SCHEMA;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, DungeonBankActionRequestPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.requestId);
        buffer.writeVarLong(payload.snapshotRevision);
        buffer.writeEnum(payload.action);
        buffer.writeUtf(payload.quoteId, 96);
    }

    private static DungeonBankActionRequestPayload read(FriendlyByteBuf buffer) {
        return new DungeonBankActionRequestPayload(
            buffer.readVarInt(),
            buffer.readVarLong(),
            buffer.readVarLong(),
            buffer.readEnum(Action.class),
            buffer.readUtf(96)
        );
    }

    public enum Action {
        DEPOSIT,
        WITHDRAW,
        UPGRADE
    }
}
