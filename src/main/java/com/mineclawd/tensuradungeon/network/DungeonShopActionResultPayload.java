package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonShopActionResultPayload(
    int schemaVersion,
    long requestId,
    ResultStatus status,
    String message,
    DungeonShopSnapshotPayload snapshot
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 2;
    public static final Type<DungeonShopActionResultPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_shop_action_result")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonShopActionResultPayload> STREAM_CODEC =
        StreamCodec.of(DungeonShopActionResultPayload::write, DungeonShopActionResultPayload::read);

    public DungeonShopActionResultPayload {
        if (schemaVersion < 0 || requestId < 0L) {
            throw new IllegalArgumentException("Dungeon Shop action result identity is invalid");
        }
        status = Objects.requireNonNull(status, "Dungeon Shop action result status");
        message = message == null ? "" : message;
        if (message.length() > 256) {
            throw new IllegalArgumentException("Dungeon Shop action result message exceeds 256 characters");
        }
        snapshot = Objects.requireNonNull(snapshot, "Dungeon Shop action result snapshot");
    }

    public boolean supportedSchema() {
        return schemaVersion == CURRENT_SCHEMA;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, DungeonShopActionResultPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.requestId);
        buffer.writeEnum(payload.status);
        buffer.writeUtf(payload.message, 256);
        DungeonShopSnapshotPayload.STREAM_CODEC.encode(buffer, payload.snapshot);
    }

    private static DungeonShopActionResultPayload read(FriendlyByteBuf buffer) {
        return new DungeonShopActionResultPayload(
            buffer.readVarInt(),
            buffer.readVarLong(),
            buffer.readEnum(ResultStatus.class),
            buffer.readUtf(256),
            DungeonShopSnapshotPayload.STREAM_CODEC.decode(buffer)
        );
    }

    public enum ResultStatus {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        INVENTORY_FULL,
        STALE_REVISION,
        SOLD_OUT,
        ALREADY_OWNED,
        MISSING_INGREDIENTS,
        UNGRANTABLE_PRODUCT,
        OFFER_NOT_FOUND,
        OFFER_EXPIRED,
        LOCKED_REQUIREMENT,
        REPLAYED_REQUEST,
        UNSUPPORTED_ACTION,
        INVALID_REQUEST,
        CLOCK_STALE,
        INTERNAL_ERROR
    }
}
