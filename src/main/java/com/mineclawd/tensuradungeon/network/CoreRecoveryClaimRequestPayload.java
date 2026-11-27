package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CoreRecoveryClaimRequestPayload(
    String sessionId,
    int floor,
    UUID coreId,
    int slot,
    Action action
) implements CustomPacketPayload {
    public static final Type<CoreRecoveryClaimRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "core_recovery_claim")
    );
    public static final StreamCodec<FriendlyByteBuf, CoreRecoveryClaimRequestPayload> STREAM_CODEC =
        StreamCodec.of(CoreRecoveryClaimRequestPayload::write, CoreRecoveryClaimRequestPayload::read);

    public CoreRecoveryClaimRequestPayload {
        sessionId = bounded(sessionId, 96);
        floor = Math.max(0, floor);
        if (coreId == null) throw new IllegalArgumentException("Recovered Core identity is required");
        if (action == null) throw new IllegalArgumentException("Core placement action is required");
        if (action == Action.QUICK_STORE) {
            if (slot != -1) {
                throw new IllegalArgumentException("Quick Core storage must not select a slot");
            }
        } else if (slot < 0 || slot >= 48) {
            throw new IllegalArgumentException("Core Satchel slot must be between 0 and 47");
        }
    }

    public CoreRecoveryClaimRequestPayload(String sessionId, int floor, UUID coreId, int slot) {
        this(sessionId, floor, coreId, slot, Action.STORE);
    }

    private static void write(FriendlyByteBuf buffer, CoreRecoveryClaimRequestPayload payload) {
        buffer.writeUtf(payload.sessionId, 96);
        buffer.writeVarInt(payload.floor);
        buffer.writeUUID(payload.coreId);
        buffer.writeVarInt(payload.slot);
        buffer.writeEnum(payload.action);
    }

    private static CoreRecoveryClaimRequestPayload read(FriendlyByteBuf buffer) {
        return new CoreRecoveryClaimRequestPayload(
            buffer.readUtf(96),
            buffer.readVarInt(),
            buffer.readUUID(),
            buffer.readVarInt(),
            buffer.readEnum(Action.class)
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

    public enum Action {
        STORE,
        REPLACE,
        QUICK_STORE
    }
}
