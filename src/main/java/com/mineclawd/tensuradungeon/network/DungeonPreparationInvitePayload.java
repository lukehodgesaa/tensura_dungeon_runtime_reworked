package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonPreparationInvitePayload(UUID hostId, String hostName)
    implements CustomPacketPayload {
    public static final Type<DungeonPreparationInvitePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "preparation_invite")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonPreparationInvitePayload> STREAM_CODEC =
        StreamCodec.of(DungeonPreparationInvitePayload::write, DungeonPreparationInvitePayload::read);

    public DungeonPreparationInvitePayload {
        hostName = hostName == null ? "" : hostName.substring(0, Math.min(32, hostName.length()));
    }

    private static void write(FriendlyByteBuf buffer, DungeonPreparationInvitePayload payload) {
        buffer.writeUUID(payload.hostId);
        buffer.writeUtf(payload.hostName, 32);
    }

    private static DungeonPreparationInvitePayload read(FriendlyByteBuf buffer) {
        return new DungeonPreparationInvitePayload(buffer.readUUID(), buffer.readUtf(32));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
