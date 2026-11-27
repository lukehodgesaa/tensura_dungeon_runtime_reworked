package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonUiRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonUiSnapshotPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonUiSnapshotClient {
    private static volatile DungeonUiSnapshotPayload latest;
    private static volatile long updateSequence;

    private DungeonUiSnapshotClient() {}

    public static void handle(DungeonUiSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            latest = payload;
            updateSequence++;
        });
    }

    public static DungeonUiSnapshotPayload latest() {
        return latest;
    }

    public static long updateSequence() {
        return updateSequence;
    }

    public static void request(DungeonUiRequestPayload.Action action) {
        request(action, 0);
    }

    public static void request(DungeonUiRequestPayload.Action action, int floor) {
        long revision = latest == null ? 0L : latest.revision();
        PacketDistributor.sendToServer(new DungeonUiRequestPayload(action, revision, floor));
    }
}
