package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonShopActionRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonShopActionResultPayload;
import com.mineclawd.tensuradungeon.network.DungeonShopOpenRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonShopSnapshotPayload;
import java.util.concurrent.atomic.AtomicLong;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonShopClient {
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static volatile DungeonShopSnapshotPayload latest;
    private static volatile DungeonShopActionResultPayload latestActionResult;
    private static volatile boolean openRequested;
    private static long lastAcceptedRequestId;

    private DungeonShopClient() {}

    public static void handle(DungeonShopSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> accept(payload));
    }

    public static void handleActionResult(
        DungeonShopActionResultPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> acceptActionResult(payload));
    }

    static synchronized boolean accept(DungeonShopSnapshotPayload payload) {
        if (payload == null || !payload.supportedSchema()) {
            return false;
        }
        if (payload.requestId() > 0L && payload.requestId() < lastAcceptedRequestId) {
            return false;
        }
        latest = payload;
        if (payload.requestId() > 0L) {
            lastAcceptedRequestId = payload.requestId();
        }
        if (payload.openRequested()) {
            openRequested = true;
        }
        return true;
    }

    public static DungeonShopSnapshotPayload latest() {
        return latest;
    }

    public static DungeonShopActionResultPayload latestActionResult() {
        return latestActionResult;
    }

    public static synchronized boolean consumeOpenRequested() {
        boolean requested = openRequested;
        openRequested = false;
        return requested;
    }

    public static void requestOpen(DungeonShopSnapshotPayload.Tab tab) {
        long requestId = nextRequestId();
        PacketDistributor.sendToServer(new DungeonShopOpenRequestPayload(tab, requestId));
    }

    public static void requestAction(
        DungeonShopSnapshotPayload.Tab tab,
        DungeonShopActionRequestPayload.Action action,
        String offerId,
        long snapshotRevision
    ) {
        PacketDistributor.sendToServer(new DungeonShopActionRequestPayload(
            DungeonShopActionRequestPayload.CURRENT_SCHEMA,
            nextRequestId(),
            snapshotRevision,
            tab,
            action,
            offerId
        ));
    }

    static synchronized boolean acceptActionResult(DungeonShopActionResultPayload payload) {
        if (payload == null || !payload.supportedSchema()) {
            return false;
        }
        if (payload.requestId() > 0L && payload.requestId() < lastAcceptedRequestId) {
            return false;
        }
        if (!accept(payload.snapshot())) {
            return false;
        }
        latestActionResult = payload;
        return true;
    }

    private static long nextRequestId() {
        return REQUEST_IDS.updateAndGet(current -> current == Long.MAX_VALUE ? 1L : current + 1L);
    }

    static synchronized void resetForTests() {
        latest = null;
        latestActionResult = null;
        openRequested = false;
        lastAcceptedRequestId = 0L;
        REQUEST_IDS.set(0L);
    }
}
