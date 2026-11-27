package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonExitPromptPayload(int completedFloor, int nextFloor) implements CustomPacketPayload {
    public static final Type<DungeonExitPromptPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "exit_prompt")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonExitPromptPayload> STREAM_CODEC =
        StreamCodec.of(DungeonExitPromptPayload::write, DungeonExitPromptPayload::read);

    public DungeonExitPromptPayload {
        completedFloor = Math.max(1, completedFloor);
        nextFloor = Math.max(completedFloor + 1, nextFloor);
    }

    private static void write(FriendlyByteBuf buffer, DungeonExitPromptPayload payload) {
        buffer.writeVarInt(payload.completedFloor);
        buffer.writeVarInt(payload.nextFloor);
    }

    private static DungeonExitPromptPayload read(FriendlyByteBuf buffer) {
        return new DungeonExitPromptPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
