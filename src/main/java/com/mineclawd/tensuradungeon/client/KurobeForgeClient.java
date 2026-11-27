package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.KurobeForgeActionRequestPayload;
import com.mineclawd.tensuradungeon.network.KurobeForgeActionResultPayload;
import com.mineclawd.tensuradungeon.network.KurobeForgeOpenRequestPayload;
import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotPayload;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class KurobeForgeClient {
    private static final long REQUEST_SESSION_BASE = ThreadLocalRandom.current().nextLong(
        1L, Long.MAX_VALUE - 1_000_000L
    );
    private static final AtomicLong REQUEST_IDS = new AtomicLong(REQUEST_SESSION_BASE);
    private static volatile KurobeForgeSnapshotPayload latest;
    private static volatile KurobeForgeActionResultPayload latestResult;
    private static volatile boolean openRequested;
    private static long lastAcceptedRequestId;

    private KurobeForgeClient() {}

    public static void handle(KurobeForgeSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> accept(payload));
    }

    public static void handleActionResult(
        KurobeForgeActionResultPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> acceptActionResult(payload));
    }

    public static KurobeForgeSnapshotPayload latest() {
        return latest;
    }

    public static KurobeForgeActionResultPayload latestActionResult() {
        return latestResult;
    }

    public static synchronized boolean consumeOpenRequested() {
        boolean requested = openRequested;
        openRequested = false;
        return requested;
    }

    public static void requestOpen(KurobeForgeSnapshotPayload.Service initialService) {
        PacketDistributor.sendToServer(new KurobeForgeOpenRequestPayload(initialService, nextRequestId()));
    }

    public static void requestAction(
        KurobeForgeSnapshotPayload.Service service,
        String weaponId,
        long observedRevision,
        String coreId,
        int coreSlot
    ) {
        PacketDistributor.sendToServer(new KurobeForgeActionRequestPayload(
            KurobeForgeActionRequestPayload.CURRENT_SCHEMA,
            nextRequestId(),
            observedRevision,
            service,
            weaponId,
            coreId,
            coreSlot
        ));
    }

    static synchronized boolean accept(KurobeForgeSnapshotPayload payload) {
        if (payload == null || !payload.supportedSchema()) return false;
        if (payload.requestId() > 0L && payload.requestId() < lastAcceptedRequestId) return false;
        latest = payload;
        if (payload.requestId() > 0L) lastAcceptedRequestId = payload.requestId();
        if (payload.openRequested()) openRequested = true;
        return true;
    }

    static synchronized boolean acceptActionResult(KurobeForgeActionResultPayload payload) {
        if (payload == null || !payload.supportedSchema()) return false;
        if (payload.requestId() > 0L && payload.requestId() < lastAcceptedRequestId) return false;
        if (!accept(payload.snapshot())) return false;
        latestResult = payload;
        return true;
    }

    private static long nextRequestId() {
        return REQUEST_IDS.updateAndGet(current -> current == Long.MAX_VALUE ? 1L : current + 1L);
    }

    static synchronized void resetForTests() {
        latest = null;
        latestResult = null;
        openRequested = false;
        lastAcceptedRequestId = 0L;
        REQUEST_IDS.set(0L);
    }
}
