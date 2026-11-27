package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonHubMusicStatePayload(int schema, boolean inHub) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 1;
    public static final Type<DungeonHubMusicStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID,
            "dungeon_hub_music_state"
        )
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonHubMusicStatePayload> STREAM_CODEC =
        StreamCodec.of(DungeonHubMusicStatePayload::write, DungeonHubMusicStatePayload::read);

    public DungeonHubMusicStatePayload {
        if (schema != CURRENT_SCHEMA) {
            throw new IllegalArgumentException(
                "Unsupported Dungeon Hub music state schema " + schema
            );
        }
    }

    private static void write(FriendlyByteBuf buffer, DungeonHubMusicStatePayload payload) {
        buffer.writeVarInt(payload.schema);
        buffer.writeBoolean(payload.inHub);
    }

    private static DungeonHubMusicStatePayload read(FriendlyByteBuf buffer) {
        return new DungeonHubMusicStatePayload(buffer.readVarInt(), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
