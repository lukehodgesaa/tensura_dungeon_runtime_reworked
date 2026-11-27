package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.CosmeticKey;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonCosmeticActionPayload(
    Action action,
    CosmeticKey cosmetic,
    long observedRevision
) implements CustomPacketPayload {
    public static final Type<DungeonCosmeticActionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_cosmetic_action")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonCosmeticActionPayload> STREAM_CODEC =
        StreamCodec.of(DungeonCosmeticActionPayload::write, DungeonCosmeticActionPayload::read);

    public DungeonCosmeticActionPayload {
        action = Objects.requireNonNull(action, "Cosmetic action");
        cosmetic = Objects.requireNonNull(cosmetic, "Cosmetic action key");
        if (observedRevision < 0L) {
            throw new IllegalArgumentException("Observed cosmetic revision cannot be negative");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, DungeonCosmeticActionPayload payload) {
        buffer.writeEnum(payload.action);
        DungeonCosmeticSnapshotPayload.writeKey(buffer, payload.cosmetic);
        buffer.writeVarLong(payload.observedRevision);
    }

    private static DungeonCosmeticActionPayload read(FriendlyByteBuf buffer) {
        return new DungeonCosmeticActionPayload(
            buffer.readEnum(Action.class),
            DungeonCosmeticSnapshotPayload.readKey(buffer),
            buffer.readVarLong()
        );
    }

    public enum Action {
        EQUIP
    }
}
