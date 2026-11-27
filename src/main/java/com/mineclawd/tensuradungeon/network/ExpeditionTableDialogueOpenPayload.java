package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExpeditionTableDialogueOpenPayload(BlockPos tablePos)
    implements CustomPacketPayload {
    public static final Type<ExpeditionTableDialogueOpenPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID, "expedition_table_dialogue_open"
        )
    );
    public static final StreamCodec<FriendlyByteBuf, ExpeditionTableDialogueOpenPayload> STREAM_CODEC =
        StreamCodec.of(
            ExpeditionTableDialogueOpenPayload::write,
            ExpeditionTableDialogueOpenPayload::read
        );

    public ExpeditionTableDialogueOpenPayload {
        tablePos = tablePos.immutable();
    }

    private static void write(
        FriendlyByteBuf buffer,
        ExpeditionTableDialogueOpenPayload payload
    ) {
        buffer.writeBlockPos(payload.tablePos);
    }

    private static ExpeditionTableDialogueOpenPayload read(FriendlyByteBuf buffer) {
        return new ExpeditionTableDialogueOpenPayload(buffer.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
