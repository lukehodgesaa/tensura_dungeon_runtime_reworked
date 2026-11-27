package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.CoreRecoveryData;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CoreRecoveryClaimResultPayload(
    CoreRecoveryData.ClaimStatus status,
    UUID coreId,
    int slot,
    String message
) implements CustomPacketPayload {
    public static final Type<CoreRecoveryClaimResultPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "core_recovery_claim_result")
    );
    public static final StreamCodec<FriendlyByteBuf, CoreRecoveryClaimResultPayload> STREAM_CODEC =
        StreamCodec.of(CoreRecoveryClaimResultPayload::write, CoreRecoveryClaimResultPayload::read);

    public CoreRecoveryClaimResultPayload {
        if (status == null || coreId == null) {
            throw new IllegalArgumentException("Core recovery claim result values are required");
        }
        slot = Math.max(-1, Math.min(47, slot));
        message = bounded(message, 160);
    }

    private static void write(FriendlyByteBuf buffer, CoreRecoveryClaimResultPayload payload) {
        buffer.writeEnum(payload.status);
        buffer.writeUUID(payload.coreId);
        buffer.writeVarInt(payload.slot + 1);
        buffer.writeUtf(payload.message, 160);
    }

    private static CoreRecoveryClaimResultPayload read(FriendlyByteBuf buffer) {
        return new CoreRecoveryClaimResultPayload(
            buffer.readEnum(CoreRecoveryData.ClaimStatus.class),
            buffer.readUUID(),
            buffer.readVarInt() - 1,
            buffer.readUtf(160)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
