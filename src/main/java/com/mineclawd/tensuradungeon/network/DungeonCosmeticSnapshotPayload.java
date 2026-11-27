package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.CosmeticKey;
import com.mineclawd.tensuradungeon.runtime.DungeonCosmeticData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonCosmeticSnapshotPayload(
    int schemaVersion,
    long revision,
    List<CosmeticKey> owned,
    CosmeticKey equippedHud,
    CosmeticKey equippedQuest,
    DungeonCosmeticData.MutationStatus status,
    String message
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 1;
    public static final int MAX_OWNED = 64;
    public static final Type<DungeonCosmeticSnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_cosmetics")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonCosmeticSnapshotPayload> STREAM_CODEC =
        StreamCodec.of(DungeonCosmeticSnapshotPayload::write, DungeonCosmeticSnapshotPayload::read);

    public DungeonCosmeticSnapshotPayload {
        if (schemaVersion < 0 || revision < 0L) {
            throw new IllegalArgumentException("Cosmetic schema and revision cannot be negative");
        }
        owned = owned == null ? List.of() : List.copyOf(owned);
        if (owned.size() > MAX_OWNED) {
            throw new IllegalArgumentException("Cosmetic snapshot exceeds " + MAX_OWNED + " entitlements");
        }
        Set<CosmeticKey> unique = new HashSet<>();
        for (CosmeticKey key : owned) {
            if (key == null || !key.cataloged() || !unique.add(key)) {
                throw new IllegalArgumentException("Cosmetic snapshot contains an invalid or duplicate entitlement");
            }
        }
        equippedHud = Objects.requireNonNull(equippedHud, "Equipped HUD cosmetic");
        equippedQuest = Objects.requireNonNull(equippedQuest, "Equipped quest cosmetic");
        if (equippedHud.type() != CosmeticKey.Type.HUD_THEME
            || equippedQuest.type() != CosmeticKey.Type.QUEST_THEME) {
            throw new IllegalArgumentException("Equipped cosmetics do not match their slots");
        }
        if (!equippedHud.cataloged() || !unique.contains(equippedHud)) {
            throw new IllegalArgumentException("Equipped HUD cosmetic must be cataloged and owned");
        }
        if ((!equippedQuest.cataloged() && !equippedQuest.equals(CosmeticKey.QUEST_INHERIT))
            || (equippedQuest.entitlementRequired() && !unique.contains(equippedQuest))) {
            throw new IllegalArgumentException(
                "Equipped quest cosmetic must be inherited or a cataloged owned entitlement"
            );
        }
        status = status == null ? DungeonCosmeticData.MutationStatus.NO_CHANGE : status;
        message = message == null ? "" : message;
        if (message.length() > 160) {
            throw new IllegalArgumentException("Cosmetic status message exceeds 160 characters");
        }
    }

    public static DungeonCosmeticSnapshotPayload from(
        DungeonCosmeticData.Snapshot snapshot,
        DungeonCosmeticData.MutationStatus status,
        String message
    ) {
        return new DungeonCosmeticSnapshotPayload(
            CURRENT_SCHEMA,
            snapshot.revision(),
            snapshot.owned(),
            snapshot.equippedHud(),
            snapshot.equippedQuest(),
            status,
            message
        );
    }

    public boolean supportedSchema() {
        return schemaVersion == CURRENT_SCHEMA;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, DungeonCosmeticSnapshotPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.revision);
        buffer.writeVarInt(payload.owned.size());
        payload.owned.forEach(key -> writeKey(buffer, key));
        writeKey(buffer, payload.equippedHud);
        writeKey(buffer, payload.equippedQuest);
        buffer.writeEnum(payload.status);
        buffer.writeUtf(payload.message, 160);
    }

    private static DungeonCosmeticSnapshotPayload read(FriendlyByteBuf buffer) {
        int schema = buffer.readVarInt();
        long revision = buffer.readVarLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_OWNED) {
            throw new IllegalArgumentException("Invalid cosmetic entitlement count: " + count);
        }
        List<CosmeticKey> owned = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            owned.add(readKey(buffer));
        }
        return new DungeonCosmeticSnapshotPayload(
            schema,
            revision,
            owned,
            readKey(buffer),
            readKey(buffer),
            buffer.readEnum(DungeonCosmeticData.MutationStatus.class),
            buffer.readUtf(160)
        );
    }

    static void writeKey(FriendlyByteBuf buffer, CosmeticKey key) {
        buffer.writeEnum(key.type());
        buffer.writeUtf(key.id(), 48);
    }

    static CosmeticKey readKey(FriendlyByteBuf buffer) {
        return new CosmeticKey(buffer.readEnum(CosmeticKey.Type.class), buffer.readUtf(48));
    }
}
