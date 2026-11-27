package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonNavigationPayload(List<Target> targets) implements CustomPacketPayload {
    public static final Type<DungeonNavigationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "navigation_targets"));
    public static final StreamCodec<FriendlyByteBuf, DungeonNavigationPayload> STREAM_CODEC = StreamCodec.of(DungeonNavigationPayload::write, DungeonNavigationPayload::read);

    public DungeonNavigationPayload {
        targets = List.copyOf(targets.subList(0, Math.min(4, targets.size())));
    }

    private static void write(FriendlyByteBuf buffer, DungeonNavigationPayload payload) {
        buffer.writeVarInt(payload.targets.size());
        for (Target target : payload.targets) {
            buffer.writeBlockPos(target.pos());
            buffer.writeBoolean(target.branch());
            buffer.writeBoolean(target.portal());
        }
    }

    private static DungeonNavigationPayload read(FriendlyByteBuf buffer) {
        int count = Math.min(4, Math.max(0, buffer.readVarInt()));
        List<Target> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(new Target(buffer.readBlockPos(), buffer.readBoolean(), buffer.readBoolean()));
        }
        return new DungeonNavigationPayload(targets);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Target(BlockPos pos, boolean branch, boolean portal) {
        public Target { pos = pos.immutable(); }
    }
}
