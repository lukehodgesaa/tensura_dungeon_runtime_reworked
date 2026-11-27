package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonUiRequestPayload(Action action, long observedRevision, int floor) implements CustomPacketPayload {
    public enum Action {
        REFRESH,
        CLAIM_OLDEST,
        CLAIM_ALL,
        SELECT_FLOOR,
        PORTAL_CONTINUE,
        PORTAL_RETURN_HOME
    }

    public static final Type<DungeonUiRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "native_gui_request")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonUiRequestPayload> STREAM_CODEC =
        StreamCodec.of(DungeonUiRequestPayload::write, DungeonUiRequestPayload::read);

    public DungeonUiRequestPayload {
        action = action == null ? Action.REFRESH : action;
        observedRevision = Math.max(0L, observedRevision);
        floor = Math.max(0, floor);
    }

    public DungeonUiRequestPayload(Action action, long observedRevision) {
        this(action, observedRevision, 0);
    }

    private static void write(FriendlyByteBuf buffer, DungeonUiRequestPayload payload) {
        buffer.writeEnum(payload.action);
        buffer.writeVarLong(payload.observedRevision);
        buffer.writeVarInt(payload.floor);
    }

    private static DungeonUiRequestPayload read(FriendlyByteBuf buffer) {
        return new DungeonUiRequestPayload(buffer.readEnum(Action.class), buffer.readVarLong(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
