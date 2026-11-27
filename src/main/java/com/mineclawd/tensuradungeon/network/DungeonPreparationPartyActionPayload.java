package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonPreparationPartyActionPayload(
    Action action,
    UUID targetId,
    long observedRevision
) implements CustomPacketPayload {
    public static final Type<DungeonPreparationPartyActionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID,
            "preparation_party_action"
        )
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonPreparationPartyActionPayload>
        STREAM_CODEC = StreamCodec.of(
            DungeonPreparationPartyActionPayload::write,
            DungeonPreparationPartyActionPayload::read
        );
    private static final UUID NONE = new UUID(0L, 0L);

    public DungeonPreparationPartyActionPayload {
        if (action == null) throw new IllegalArgumentException("Missing party action");
        targetId = targetId == null ? NONE : targetId;
        observedRevision = Math.max(0L, observedRevision);
    }

    public static DungeonPreparationPartyActionPayload refresh(long revision) {
        return new DungeonPreparationPartyActionPayload(Action.REFRESH, NONE, revision);
    }

    private static void write(FriendlyByteBuf buffer, DungeonPreparationPartyActionPayload payload) {
        buffer.writeEnum(payload.action);
        buffer.writeUUID(payload.targetId);
        buffer.writeVarLong(payload.observedRevision);
    }

    private static DungeonPreparationPartyActionPayload read(FriendlyByteBuf buffer) {
        return new DungeonPreparationPartyActionPayload(
            buffer.readEnum(Action.class),
            buffer.readUUID(),
            buffer.readVarLong()
        );
    }

    public enum Action {
        REFRESH,
        INVITE_PLAYER,
        ADD_SUBORDINATE,
        REMOVE_MEMBER,
        TOGGLE_READY,
        CONFIRM_READY,
        START_DUNGEON,
        ACCEPT_INVITE,
        DECLINE_INVITE
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
