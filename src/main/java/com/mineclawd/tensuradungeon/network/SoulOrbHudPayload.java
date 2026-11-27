package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SoulOrbHudPayload(
    boolean active,
    String sessionId,
    int common,
    int uncommon,
    int rare,
    int epic,
    int activityRevision
) implements CustomPacketPayload {
    public static final Type<SoulOrbHudPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "soul_orb_hud")
    );
    public static final StreamCodec<FriendlyByteBuf, SoulOrbHudPayload> STREAM_CODEC =
        StreamCodec.of(SoulOrbHudPayload::write, SoulOrbHudPayload::read);

    public SoulOrbHudPayload {
        sessionId = sessionId == null || sessionId.isBlank() ? "NONE" : sessionId;
        common = Math.max(0, common);
        uncommon = Math.max(0, uncommon);
        rare = Math.max(0, rare);
        epic = Math.max(0, epic);
        activityRevision = Math.max(0, activityRevision);
    }

    private static void write(FriendlyByteBuf buffer, SoulOrbHudPayload payload) {
        buffer.writeBoolean(payload.active);
        buffer.writeUtf(payload.sessionId, 96);
        buffer.writeVarInt(payload.common);
        buffer.writeVarInt(payload.uncommon);
        buffer.writeVarInt(payload.rare);
        buffer.writeVarInt(payload.epic);
        buffer.writeVarInt(payload.activityRevision);
    }

    private static SoulOrbHudPayload read(FriendlyByteBuf buffer) {
        return new SoulOrbHudPayload(
            buffer.readBoolean(),
            buffer.readUtf(96),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
