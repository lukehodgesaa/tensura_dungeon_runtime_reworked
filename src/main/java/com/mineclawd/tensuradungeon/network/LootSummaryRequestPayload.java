package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LootSummaryRequestPayload(Action action, String sessionId, long observedRevision, String entryId)
    implements CustomPacketPayload {
    public enum Action {
        OPEN_LOOT_SUMMARY,
        APPRAISE_HAUL,
        KEEP_ALL,
        QUICK_SELL,
        SELL_ALL,
        KEEP_ENTRY
    }

    public static final Type<LootSummaryRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "loot_summary_request")
    );
    public static final StreamCodec<FriendlyByteBuf, LootSummaryRequestPayload> STREAM_CODEC =
        StreamCodec.of(LootSummaryRequestPayload::write, LootSummaryRequestPayload::read);

    public LootSummaryRequestPayload {
        action = action == null ? Action.OPEN_LOOT_SUMMARY : action;
        sessionId = bounded(sessionId, 96);
        observedRevision = Math.max(0L, observedRevision);
        entryId = bounded(entryId, 128);
    }

    private static void write(FriendlyByteBuf buffer, LootSummaryRequestPayload payload) {
        buffer.writeEnum(payload.action);
        buffer.writeUtf(payload.sessionId, 96);
        buffer.writeVarLong(payload.observedRevision);
        buffer.writeUtf(payload.entryId, 128);
    }

    private static LootSummaryRequestPayload read(FriendlyByteBuf buffer) {
        return new LootSummaryRequestPayload(
            buffer.readEnum(Action.class), buffer.readUtf(96), buffer.readVarLong(), buffer.readUtf(128)
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
