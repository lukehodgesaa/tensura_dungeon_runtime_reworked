package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.LootSummaryPayload;
import com.mineclawd.tensuradungeon.network.LootSummaryRequestPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class LootSummaryClient {
    private static volatile LootSummaryPayload latest;
    private static volatile boolean openRequested;

    private LootSummaryClient() {}

    public static void handle(LootSummaryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            latest = payload;
            if (payload.openRequested() && payload.available()) {
                openRequested = true;
            }
        });
    }

    public static LootSummaryPayload latest() {
        return latest;
    }

    public static boolean consumeOpenRequested() {
        boolean requested = openRequested;
        openRequested = false;
        return requested;
    }

    public static void request(LootSummaryRequestPayload.Action action, String sessionId, String entryId) {
        LootSummaryPayload current = latest;
        long revision = current != null && current.sessionId().equals(sessionId) ? current.revision() : 0L;
        PacketDistributor.sendToServer(new LootSummaryRequestPayload(action, sessionId, revision, entryId));
    }
}
