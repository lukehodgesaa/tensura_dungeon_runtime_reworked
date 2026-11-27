package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CoreSatchelAccessPayload(
    boolean acquired,
    String gradeName,
    int capacity,
    List<CoreEntry> cores
) implements CustomPacketPayload {
    public static final Type<CoreSatchelAccessPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "core_satchel_access")
    );
    public static final StreamCodec<FriendlyByteBuf, CoreSatchelAccessPayload> STREAM_CODEC =
        StreamCodec.of(CoreSatchelAccessPayload::write, CoreSatchelAccessPayload::read);

    public CoreSatchelAccessPayload {
        cores = cores == null ? List.of() : List.copyOf(cores);
        if (!acquired) {
            gradeName = "";
            capacity = 0;
            cores = List.of();
        } else if (gradeName == null || gradeName.isBlank() || capacity <= 0) {
            throw new IllegalArgumentException("Acquired Core Satchel payload requires a grade and capacity");
        }
        if (cores.size() > capacity) {
            throw new IllegalArgumentException("Core Satchel payload contains more Cores than slots");
        }
        Set<Integer> slots = new HashSet<>();
        Set<UUID> coreIds = new HashSet<>();
        for (CoreEntry core : cores) {
            if (core.slot() < 0 || core.slot() >= capacity) {
                throw new IllegalArgumentException("Core payload slot is outside Satchel capacity: " + core.slot());
            }
            if (!slots.add(core.slot())) {
                throw new IllegalArgumentException("Duplicate Core payload slot: " + core.slot());
            }
            if (!coreIds.add(core.coreId())) {
                throw new IllegalArgumentException("Duplicate Core payload identity: " + core.coreId());
            }
        }
    }

    public CoreSatchelAccessPayload(boolean acquired, String gradeName, int capacity) {
        this(acquired, gradeName, capacity, List.of());
    }

    public static CoreSatchelAccessPayload unowned() {
        return new CoreSatchelAccessPayload(false, "", 0, List.of());
    }

    private static void write(FriendlyByteBuf buffer, CoreSatchelAccessPayload payload) {
        buffer.writeBoolean(payload.acquired);
        buffer.writeUtf(payload.gradeName, 24);
        buffer.writeVarInt(payload.capacity);
        buffer.writeVarInt(payload.cores.size());
        for (CoreEntry core : payload.cores) {
            buffer.writeUUID(core.coreId());
            buffer.writeVarInt(core.slot());
            buffer.writeVarInt(core.tier());
            buffer.writeLong(core.totalCharge());
            buffer.writeLong(core.capacity());
            buffer.writeLong(core.naturalCharge());
            buffer.writeUtf(core.displayName(), 96);
        }
    }

    private static CoreSatchelAccessPayload read(FriendlyByteBuf buffer) {
        boolean acquired = buffer.readBoolean();
        String gradeName = buffer.readUtf(24);
        int capacity = buffer.readVarInt();
        int coreCount = buffer.readVarInt();
        if (coreCount < 0 || coreCount > 48) {
            throw new IllegalArgumentException("Invalid Core Satchel payload record count: " + coreCount);
        }
        List<CoreEntry> cores = new ArrayList<>(coreCount);
        for (int index = 0; index < coreCount; index++) {
            cores.add(new CoreEntry(
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readUtf(96)
            ));
        }
        return new CoreSatchelAccessPayload(
            acquired,
            gradeName,
            capacity,
            cores
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record CoreEntry(
        UUID coreId,
        int slot,
        int tier,
        long totalCharge,
        long capacity,
        long naturalCharge,
        String displayName
    ) {
        private static final String DEFAULT_DISPLAY_NAME = "MONSTER CORE";

        public CoreEntry(
            UUID coreId,
            int slot,
            int tier,
            long totalCharge,
            long capacity,
            long naturalCharge
        ) {
            this(coreId, slot, tier, totalCharge, capacity, naturalCharge, DEFAULT_DISPLAY_NAME);
        }

        public CoreEntry {
            if (coreId == null) {
                throw new IllegalArgumentException("Core payload identity is required");
            }
            if (tier < 1 || tier > 6) {
                throw new IllegalArgumentException("Core payload Tier must be between I and VI");
            }
            if (capacity <= 0L || totalCharge < 0L || totalCharge > capacity) {
                throw new IllegalArgumentException("Core payload Total Charge must be within capacity");
            }
            if (naturalCharge < 0L || naturalCharge > totalCharge) {
                throw new IllegalArgumentException("Core payload Natural Charge must be within Total Charge");
            }
            displayName = displayName == null || displayName.isBlank()
                ? DEFAULT_DISPLAY_NAME
                : displayName.length() <= 96 ? displayName : displayName.substring(0, 96);
        }
    }
}
