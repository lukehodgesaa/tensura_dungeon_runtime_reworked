package com.mineclawd.tensurafogbarriers.network;

import com.mineclawd.tensurafogbarriers.TensuraFogBarriers;
import com.mineclawd.tensurafogbarriers.data.FogBarrierPanel;
import com.mineclawd.tensurafogbarriers.data.FogAnimationStyle;
import com.mineclawd.tensurafogbarriers.data.FogVariant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FogBarrierSnapshotPayload(List<Entry> entries) implements CustomPacketPayload {
    private static final int MAX_PANELS = 2048;
    public static final Type<FogBarrierSnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraFogBarriers.MOD_ID, "panel_snapshot")
    );
    public static final StreamCodec<FriendlyByteBuf, FogBarrierSnapshotPayload> STREAM_CODEC = StreamCodec.of(
        FogBarrierSnapshotPayload::write, FogBarrierSnapshotPayload::read
    );

    public FogBarrierSnapshotPayload {
        entries = List.copyOf(entries.subList(0, Math.min(MAX_PANELS, entries.size())));
    }

    public static FogBarrierSnapshotPayload fromPanels(List<FogBarrierPanel> panels) {
        return new FogBarrierSnapshotPayload(panels.stream().map(Entry::fromPanel).toList());
    }

    private static void write(FriendlyByteBuf buffer, FogBarrierSnapshotPayload payload) {
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeVarInt(entry.id());
            buffer.writeByte(entry.axis() == FogBarrierPanel.Axis.X ? 0 : 1);
            buffer.writeDouble(entry.plane());
            buffer.writeDouble(entry.minY());
            buffer.writeDouble(entry.maxY());
            buffer.writeDouble(entry.minLateral());
            buffer.writeDouble(entry.maxLateral());
            buffer.writeUtf(entry.type(), 64);
            buffer.writeByte(entry.variant().ordinal());
            buffer.writeByte(entry.style().ordinal());
            buffer.writeLong(entry.createdGameTime());
        }
    }

    private static FogBarrierSnapshotPayload read(FriendlyByteBuf buffer) {
        int count = Math.min(MAX_PANELS, Math.max(0, buffer.readVarInt()));
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(
                buffer.readVarInt(),
                buffer.readByte() == 0 ? FogBarrierPanel.Axis.X : FogBarrierPanel.Axis.Z,
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readUtf(64), readVariant(buffer), readStyle(buffer), buffer.readLong()
            ));
        }
        return new FogBarrierSnapshotPayload(entries);
    }

    private static FogVariant readVariant(FriendlyByteBuf buffer) {
        int ordinal = buffer.readUnsignedByte();
        FogVariant[] variants = FogVariant.values();
        return ordinal < variants.length ? variants[ordinal] : FogVariant.GOLD;
    }

    private static FogAnimationStyle readStyle(FriendlyByteBuf buffer) {
        int ordinal = buffer.readUnsignedByte();
        FogAnimationStyle[] styles = FogAnimationStyle.values();
        return ordinal < styles.length ? styles[ordinal] : FogAnimationStyle.PORTAL;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
        int id,
        FogBarrierPanel.Axis axis,
        double plane,
        double minY,
        double maxY,
        double minLateral,
        double maxLateral,
        String type,
        FogVariant variant,
        FogAnimationStyle style,
        long createdGameTime
    ) {
        static Entry fromPanel(FogBarrierPanel panel) {
            return new Entry(
                panel.id(), panel.axis(), panel.plane(), panel.minY(), panel.maxY(),
                panel.minLateral(), panel.maxLateral(), panel.type(), panel.variant(), panel.style(), panel.createdGameTime()
            );
        }
    }
}
