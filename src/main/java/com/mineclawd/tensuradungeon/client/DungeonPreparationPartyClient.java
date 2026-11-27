package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonPreparationInvitePayload;
import com.mineclawd.tensuradungeon.network.DungeonPreparationPartyActionPayload;
import com.mineclawd.tensuradungeon.network.DungeonPreparationPartySnapshotPayload;
import com.mineclawd.tensuradungeon.network.DungeonPreparationProtectionWarningPayload;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonPreparationPartyClient {
    private static volatile DungeonPreparationPartySnapshotPayload latest;
    private static final AtomicLong UPDATE_SEQUENCE = new AtomicLong();

    private DungeonPreparationPartyClient() {}

    public static void handle(
        DungeonPreparationPartySnapshotPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            latest = payload;
            UPDATE_SEQUENCE.incrementAndGet();
        });
    }

    public static void handleInvite(
        DungeonPreparationInvitePayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
            new DungeonPreparationInviteScreen(payload.hostId(), payload.hostName())
        ));
    }

    public static void handleProtectionWarning(
        DungeonPreparationProtectionWarningPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            Screen parent = minecraft.screen;
            minecraft.setScreen(new DungeonPreparationProtectionWarningScreen(parent, payload));
        });
    }

    static void confirmReady(DungeonPreparationProtectionWarningPayload payload) {
        PacketDistributor.sendToServer(
            new DungeonPreparationPartyActionPayload(
                DungeonPreparationPartyActionPayload.Action.CONFIRM_READY,
                payload.confirmationId(),
                payload.partyRevision()
            )
        );
    }

    public static DungeonPreparationPartySnapshotPayload latest() {
        return latest;
    }

    public static long updateSequence() {
        return UPDATE_SEQUENCE.get();
    }

    public static void refresh() {
        PacketDistributor.sendToServer(
            DungeonPreparationPartyActionPayload.refresh(latest == null ? 0L : latest.revision())
        );
    }

    public static void act(
        DungeonPreparationPartyActionPayload.Action action,
        UUID targetId
    ) {
        PacketDistributor.sendToServer(new DungeonPreparationPartyActionPayload(
            action,
            targetId,
            latest == null ? 0L : latest.revision()
        ));
    }
}
