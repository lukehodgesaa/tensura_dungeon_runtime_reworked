package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonShopActionRequestPayload(
    int schemaVersion,
    long requestId,
    long snapshotRevision,
    DungeonShopSnapshotPayload.Tab tab,
    Action action,
    String offerId
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 1;
    public static final Type<DungeonShopActionRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_shop_action_request")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonShopActionRequestPayload> STREAM_CODEC =
        StreamCodec.of(DungeonShopActionRequestPayload::write, DungeonShopActionRequestPayload::read);

    public DungeonShopActionRequestPayload {
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("Dungeon Shop action schema version cannot be negative");
        }
        if (requestId <= 0L || snapshotRevision < 0L) {
            throw new IllegalArgumentException("Dungeon Shop action identity and revision are invalid");
        }
        tab = Objects.requireNonNull(tab, "Dungeon Shop action tab");
        action = Objects.requireNonNull(action, "Dungeon Shop action type");
        offerId = requiredText(offerId, 96, "Dungeon Shop action offer id");
    }

    public boolean supportedSchema() {
        return schemaVersion == CURRENT_SCHEMA;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, DungeonShopActionRequestPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.requestId);
        buffer.writeVarLong(payload.snapshotRevision);
        buffer.writeEnum(payload.tab);
        buffer.writeEnum(payload.action);
        buffer.writeUtf(payload.offerId, 96);
    }

    private static DungeonShopActionRequestPayload read(FriendlyByteBuf buffer) {
        return new DungeonShopActionRequestPayload(
            buffer.readVarInt(),
            buffer.readVarLong(),
            buffer.readVarLong(),
            buffer.readEnum(DungeonShopSnapshotPayload.Tab.class),
            buffer.readEnum(Action.class),
            buffer.readUtf(96)
        );
    }

    public enum Action {
        PURCHASE,
        BARTER,
        VISIT_VENDOR
    }

    private static String requiredText(String value, int maximum, String field) {
        String checked = value == null ? "" : value;
        if (checked.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (checked.length() > maximum) {
            throw new IllegalArgumentException(field + " exceeds " + maximum + " characters");
        }
        return checked;
    }
}
