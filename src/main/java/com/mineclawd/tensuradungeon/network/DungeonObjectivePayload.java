package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.DungeonHudHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonObjectivePayload(
    boolean active,
    int floorNumber,
    String roomState,
    String objectiveId,
    String objectiveLabel,
    int objectiveProgress,
    int objectiveTarget,
    int remainingTicks,
    int durationTicks,
    int enemiesAlive,
    int enemiesSpawned,
    String modifierNames,
    String modifierSummary,
    int compositionSpent,
    int compositionBudget,
    String compositionSummary,
    long runElapsedTicks,
    long revisionGameTime,
    String recoveryTier,
    int recoveryWindowPermille,
    int recoveryLastCharge
) implements CustomPacketPayload {
    public static final Type<DungeonObjectivePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_objective")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonObjectivePayload> STREAM_CODEC =
        StreamCodec.of(DungeonObjectivePayload::write, DungeonObjectivePayload::read);

    public DungeonObjectivePayload {
        floorNumber = Math.max(0, floorNumber);
        roomState = safe(roomState, "NONE");
        objectiveId = safe(objectiveId, "NONE");
        objectiveLabel = safe(objectiveLabel, "No active room objective");
        objectiveProgress = Math.max(0, objectiveProgress);
        objectiveTarget = Math.max(0, objectiveTarget);
        remainingTicks = Math.max(0, remainingTicks);
        durationTicks = Math.max(0, durationTicks);
        enemiesAlive = Math.max(0, enemiesAlive);
        enemiesSpawned = Math.max(0, enemiesSpawned);
        modifierNames = safe(modifierNames, "");
        modifierSummary = safe(modifierSummary, "");
        compositionSpent = Math.max(0, compositionSpent);
        compositionBudget = Math.max(0, compositionBudget);
        compositionSummary = safe(compositionSummary, "");
        runElapsedTicks = Math.max(0L, runElapsedTicks);
        revisionGameTime = Math.max(0L, revisionGameTime);
        recoveryTier = safe(recoveryTier, "STABLE");
        recoveryWindowPermille = Math.max(0, recoveryWindowPermille);
        recoveryLastCharge = Math.max(0, recoveryLastCharge);
    }

    public static DungeonObjectivePayload from(DungeonHudHooks.DungeonHudSnapshot snapshot) {
        return new DungeonObjectivePayload(
            snapshot.objectiveActive(),
            snapshot.floorNumber(),
            snapshot.roomState(),
            snapshot.objectiveId(),
            snapshot.objectiveLabel(),
            snapshot.objectiveProgress(),
            snapshot.objectiveTarget(),
            snapshot.objectiveRemainingTicks(),
            snapshot.objectiveDurationTicks(),
            snapshot.enemiesAlive(),
            snapshot.enemiesSpawned(),
            snapshot.activeModifierNames(),
            snapshot.modifierSummaryForObjectivePanel(),
            snapshot.compositionSpent(),
            snapshot.compositionBudget(),
            snapshot.compositionSummary(),
            snapshot.runElapsedTicks(),
            snapshot.revisionGameTime(),
            snapshot.recoveryTier(),
            snapshot.recoveryWindowPermille(),
            snapshot.recoveryLastCharge()
        );
    }

    private static void write(FriendlyByteBuf buffer, DungeonObjectivePayload payload) {
        buffer.writeBoolean(payload.active);
        buffer.writeVarInt(payload.floorNumber);
        buffer.writeUtf(payload.roomState, 48);
        buffer.writeUtf(payload.objectiveId, 48);
        buffer.writeUtf(payload.objectiveLabel, 160);
        buffer.writeVarInt(payload.objectiveProgress);
        buffer.writeVarInt(payload.objectiveTarget);
        buffer.writeVarInt(payload.remainingTicks);
        buffer.writeVarInt(payload.durationTicks);
        buffer.writeVarInt(payload.enemiesAlive);
        buffer.writeVarInt(payload.enemiesSpawned);
        buffer.writeUtf(payload.modifierNames, 256);
        buffer.writeUtf(payload.modifierSummary, 256);
        buffer.writeVarInt(payload.compositionSpent);
        buffer.writeVarInt(payload.compositionBudget);
        buffer.writeUtf(payload.compositionSummary, 256);
        buffer.writeVarLong(payload.runElapsedTicks);
        buffer.writeVarLong(payload.revisionGameTime);
        buffer.writeUtf(payload.recoveryTier, 16);
        buffer.writeVarInt(payload.recoveryWindowPermille);
        buffer.writeVarInt(payload.recoveryLastCharge);
    }

    private static DungeonObjectivePayload read(FriendlyByteBuf buffer) {
        return new DungeonObjectivePayload(
            buffer.readBoolean(),
            buffer.readVarInt(),
            buffer.readUtf(48),
            buffer.readUtf(48),
            buffer.readUtf(160),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readUtf(256),
            buffer.readUtf(256),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readUtf(256),
            buffer.readVarLong(),
            buffer.readVarLong(),
            buffer.readUtf(16),
            buffer.readVarInt(),
            buffer.readVarInt()
        );
    }

    public DungeonObjectivePayload(
        boolean active, int floorNumber, String roomState, String objectiveId, String objectiveLabel,
        int objectiveProgress, int objectiveTarget, int remainingTicks, int durationTicks,
        int enemiesAlive, int enemiesSpawned, String modifierNames, String modifierSummary,
        int compositionSpent, int compositionBudget, String compositionSummary,
        long runElapsedTicks, long revisionGameTime
    ) {
        this(
            active, floorNumber, roomState, objectiveId, objectiveLabel, objectiveProgress,
            objectiveTarget, remainingTicks, durationTicks, enemiesAlive, enemiesSpawned,
            modifierNames, modifierSummary, compositionSpent, compositionBudget, compositionSummary,
            runElapsedTicks, revisionGameTime, "STABLE", 0, 0
        );
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
