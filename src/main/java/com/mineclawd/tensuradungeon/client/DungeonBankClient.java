package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonBankActionRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonBankActionResultPayload;
import com.mineclawd.tensuradungeon.network.DungeonBankOpenRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonBankSnapshotPayload;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonBankClient {
    private static final AtomicLong REQUEST_IDS = new AtomicLong(sessionRequestSeed());
    private static volatile DungeonBankSnapshotPayload latest;
    private static volatile DungeonBankActionResultPayload latestActionResult;
    private static volatile boolean openRequested;
    private static long lastAcceptedRequestId;

    private DungeonBankClient() {}

    public static void handle(DungeonBankSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> accept(payload));
    }

    public static void handleActionResult(
        DungeonBankActionResultPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> acceptActionResult(payload));
    }

    static synchronized boolean accept(DungeonBankSnapshotPayload payload) {
        if (payload == null || !payload.supportedSchema()) return false;
        if (payload.requestId() > 0L && payload.requestId() < lastAcceptedRequestId) return false;
        latest = payload;
        if (payload.requestId() > 0L) lastAcceptedRequestId = payload.requestId();
        if (payload.openRequested()) openRequested = true;
        return true;
    }

    static synchronized boolean acceptActionResult(DungeonBankActionResultPayload payload) {
        if (payload == null || !payload.supportedSchema()) return false;
        if (payload.requestId() > 0L && payload.requestId() < lastAcceptedRequestId) return false;
        if (!accept(payload.snapshot())) return false;
        latestActionResult = payload;
        return true;
    }

    public static DungeonBankSnapshotPayload latest() {
        return latest;
    }

    public static DungeonBankActionResultPayload latestActionResult() {
        return latestActionResult;
    }

    public static synchronized boolean consumeOpenRequested() {
        boolean requested = openRequested;
        openRequested = false;
        return requested;
    }

    public static void requestOpen() {
        PacketDistributor.sendToServer(new DungeonBankOpenRequestPayload(nextRequestId()));
    }

    public static void requestAction(
        DungeonBankActionRequestPayload.Action action,
        String quoteId,
        long snapshotRevision
    ) {
        PacketDistributor.sendToServer(new DungeonBankActionRequestPayload(
            DungeonBankActionRequestPayload.CURRENT_SCHEMA,
            nextRequestId(),
            snapshotRevision,
            action,
            quoteId
        ));
    }

    private static long nextRequestId() {
        return REQUEST_IDS.updateAndGet(current -> current == Long.MAX_VALUE ? 1L : current + 1L);
    }

    private static long sessionRequestSeed() {
        return ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE - 1L);
    }

    static synchronized void resetForTests() {
        latest = null;
        latestActionResult = null;
        openRequested = false;
        lastAcceptedRequestId = 0L;
        REQUEST_IDS.set(0L);
    }
}
