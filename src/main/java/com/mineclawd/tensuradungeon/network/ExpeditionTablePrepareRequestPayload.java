package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExpeditionTablePrepareRequestPayload(BlockPos tablePos)
    implements CustomPacketPayload {
    public static final Type<ExpeditionTablePrepareRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID, "expedition_table_prepare_request"
        )
    );
    public static final StreamCodec<FriendlyByteBuf, ExpeditionTablePrepareRequestPayload> STREAM_CODEC =
        StreamCodec.of(
            ExpeditionTablePrepareRequestPayload::write,
            ExpeditionTablePrepareRequestPayload::read
        );

    public ExpeditionTablePrepareRequestPayload {
        tablePos = tablePos.immutable();
    }

    private static void write(
        FriendlyByteBuf buffer,
        ExpeditionTablePrepareRequestPayload payload
    ) {
        buffer.writeBlockPos(payload.tablePos);
    }

    private static ExpeditionTablePrepareRequestPayload read(FriendlyByteBuf buffer) {
        return new ExpeditionTablePrepareRequestPayload(buffer.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
