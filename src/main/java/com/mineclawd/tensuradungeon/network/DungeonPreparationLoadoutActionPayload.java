package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.DungeonPreparationLoadoutData;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonPreparationLoadoutActionPayload(
    Action action,
    UUID targetId,
    DungeonPreparationLoadoutData.Category category,
    int inventorySlot,
    String skillId,
    long observedRevision
) implements CustomPacketPayload {
    public static final Type<DungeonPreparationLoadoutActionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID,
            "dungeon_preparation_loadout_action"
        )
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonPreparationLoadoutActionPayload>
        STREAM_CODEC = StreamCodec.of(
            DungeonPreparationLoadoutActionPayload::write,
            DungeonPreparationLoadoutActionPayload::read
        );

    public DungeonPreparationLoadoutActionPayload {
        action = Objects.requireNonNull(action, "Preparation loadout action");
        targetId = Objects.requireNonNull(targetId, "Preparation loadout target");
        skillId = skillId == null ? "" : skillId;
        if (observedRevision < 0L) {
            throw new IllegalArgumentException("Preparation loadout revision cannot be negative");
        }
        if (action == Action.REFRESH) {
            category = null;
            inventorySlot = -1;
            skillId = "";
        } else if (action.isSkillAction()) {
            category = null;
            if (skillId.isBlank()) {
                throw new IllegalArgumentException("Skill loadout mutation requires a skill ID");
            }
            if (inventorySlot < 0 || inventorySlot >= action.slotCapacity()) {
                throw new IllegalArgumentException("Skill loadout mutation requires a bounded slot");
            }
        } else {
            category = Objects.requireNonNull(category, "Preparation loadout category");
            skillId = "";
            if (action == Action.SELECT && inventorySlot < 0) {
                throw new IllegalArgumentException("Preparation loadout selection requires an inventory slot");
            }
            if (action == Action.CLEAR) {
                inventorySlot = -1;
            }
        }
    }

    public DungeonPreparationLoadoutActionPayload(
        Action action,
        UUID targetId,
        DungeonPreparationLoadoutData.Category category,
        int inventorySlot,
        long observedRevision
    ) {
        this(action, targetId, category, inventorySlot, "", observedRevision);
    }

    public static DungeonPreparationLoadoutActionPayload refresh(
        UUID targetId,
        long observedRevision
    ) {
        return new DungeonPreparationLoadoutActionPayload(
            Action.REFRESH,
            targetId,
            null,
            -1,
            "",
            Math.max(0L, observedRevision)
        );
    }

    public static DungeonPreparationLoadoutActionPayload assignActiveSkill(
        UUID targetId,
        String skillId,
        int slot,
        long observedRevision
    ) {
        return skill(Action.ASSIGN_ACTIVE_SKILL, targetId, skillId, slot, observedRevision);
    }

    public static DungeonPreparationLoadoutActionPayload clearActiveSkill(
        UUID targetId,
        String skillId,
        int slot,
        long observedRevision
    ) {
        return skill(Action.CLEAR_ACTIVE_SKILL, targetId, skillId, slot, observedRevision);
    }

    public static DungeonPreparationLoadoutActionPayload assignPassiveSkill(
        UUID targetId,
        String skillId,
        int slot,
        long observedRevision
    ) {
        return skill(Action.ASSIGN_PASSIVE_SKILL, targetId, skillId, slot, observedRevision);
    }

    public static DungeonPreparationLoadoutActionPayload clearPassiveSkill(
        UUID targetId,
        String skillId,
        int slot,
        long observedRevision
    ) {
        return skill(Action.CLEAR_PASSIVE_SKILL, targetId, skillId, slot, observedRevision);
    }

    private static DungeonPreparationLoadoutActionPayload skill(
        Action action,
        UUID targetId,
        String skillId,
        int slot,
        long observedRevision
    ) {
        return new DungeonPreparationLoadoutActionPayload(
            action,
            targetId,
            null,
            slot,
            skillId,
            Math.max(0L, observedRevision)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(
        FriendlyByteBuf buffer,
        DungeonPreparationLoadoutActionPayload payload
    ) {
        buffer.writeEnum(payload.action);
        buffer.writeUUID(payload.targetId);
        buffer.writeBoolean(payload.category != null);
        if (payload.category != null) {
            buffer.writeEnum(payload.category);
        }
        buffer.writeVarInt(payload.inventorySlot + 1);
        buffer.writeUtf(payload.skillId, 128);
        buffer.writeVarLong(payload.observedRevision);
    }

    private static DungeonPreparationLoadoutActionPayload read(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID targetId = buffer.readUUID();
        DungeonPreparationLoadoutData.Category category = buffer.readBoolean()
            ? buffer.readEnum(DungeonPreparationLoadoutData.Category.class)
            : null;
        return new DungeonPreparationLoadoutActionPayload(
            action,
            targetId,
            category,
            buffer.readVarInt() - 1,
            buffer.readUtf(128),
            buffer.readVarLong()
        );
    }

    public enum Action {
        REFRESH,
        SELECT,
        CLEAR,
        ASSIGN_ACTIVE_SKILL,
        CLEAR_ACTIVE_SKILL,
        ASSIGN_PASSIVE_SKILL,
        CLEAR_PASSIVE_SKILL;

        public boolean isSkillAction() {
            return this == ASSIGN_ACTIVE_SKILL
                || this == CLEAR_ACTIVE_SKILL
                || this == ASSIGN_PASSIVE_SKILL
                || this == CLEAR_PASSIVE_SKILL;
        }

        public int slotCapacity() {
            return this == ASSIGN_PASSIVE_SKILL || this == CLEAR_PASSIVE_SKILL
                ? 2
                : 3;
        }
    }
}
