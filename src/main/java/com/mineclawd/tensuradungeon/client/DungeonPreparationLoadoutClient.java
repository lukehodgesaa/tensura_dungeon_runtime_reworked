package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonPreparationLoadoutActionPayload;
import com.mineclawd.tensuradungeon.network.DungeonPreparationLoadoutSnapshotPayload;
import com.mineclawd.tensuradungeon.runtime.DungeonPreparationLoadoutData;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonPreparationLoadoutClient {
    private static volatile DungeonPreparationLoadoutSnapshotPayload latest;
    private static volatile long updateSequence;

    private DungeonPreparationLoadoutClient() {}

    public static void handle(
        DungeonPreparationLoadoutSnapshotPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            latest = payload;
            updateSequence++;
        });
    }

    public static DungeonPreparationLoadoutSnapshotPayload latest() {
        return latest;
    }

    public static long updateSequence() {
        return updateSequence;
    }

    public static void refresh() {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        refresh(Minecraft.getInstance().player.getUUID());
    }

    public static void refresh(UUID targetId) {
        long revision = latest == null ? 0L : latest.revision();
        PacketDistributor.sendToServer(
            DungeonPreparationLoadoutActionPayload.refresh(targetId, revision)
        );
    }

    public static void select(
        UUID targetId,
        DungeonPreparationLoadoutData.Category category,
        int inventorySlot
    ) {
        long revision = latest == null ? 0L : latest.revision();
        PacketDistributor.sendToServer(new DungeonPreparationLoadoutActionPayload(
            DungeonPreparationLoadoutActionPayload.Action.SELECT,
            targetId,
            category,
            inventorySlot,
            revision
        ));
    }

    public static void clear(UUID targetId, DungeonPreparationLoadoutData.Category category) {
        long revision = latest == null ? 0L : latest.revision();
        PacketDistributor.sendToServer(new DungeonPreparationLoadoutActionPayload(
            DungeonPreparationLoadoutActionPayload.Action.CLEAR,
            targetId,
            category,
            -1,
            revision
        ));
    }

    public static void assignActiveSkill(UUID targetId, String skillId, int slot) {
        sendSkill(DungeonPreparationLoadoutActionPayload.assignActiveSkill(
            targetId, skillId, slot, observedRevision()
        ));
    }

    public static void clearActiveSkill(UUID targetId, String skillId, int slot) {
        sendSkill(DungeonPreparationLoadoutActionPayload.clearActiveSkill(
            targetId, skillId, slot, observedRevision()
        ));
    }

    public static void assignPassiveSkill(UUID targetId, String skillId, int slot) {
        sendSkill(DungeonPreparationLoadoutActionPayload.assignPassiveSkill(
            targetId, skillId, slot, observedRevision()
        ));
    }

    public static void clearPassiveSkill(UUID targetId, String skillId, int slot) {
        sendSkill(DungeonPreparationLoadoutActionPayload.clearPassiveSkill(
            targetId, skillId, slot, observedRevision()
        ));
    }

    private static long observedRevision() {
        return latest == null ? 0L : latest.revision();
    }

    private static void sendSkill(DungeonPreparationLoadoutActionPayload payload) {
        if (Minecraft.getInstance().player != null) {
            PacketDistributor.sendToServer(payload);
        }
    }
}
