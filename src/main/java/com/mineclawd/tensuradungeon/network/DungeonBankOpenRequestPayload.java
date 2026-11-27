package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonBankOpenRequestPayload(long requestId) implements CustomPacketPayload {
    public static final Type<DungeonBankOpenRequestPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon_bank_open_request")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonBankOpenRequestPayload> STREAM_CODEC =
        StreamCodec.of(DungeonBankOpenRequestPayload::write, DungeonBankOpenRequestPayload::read);

    public DungeonBankOpenRequestPayload {
        if (requestId <= 0L) {
            throw new IllegalArgumentException("Dungeon Bank open request identity must be positive");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, DungeonBankOpenRequestPayload payload) {
        buffer.writeVarLong(payload.requestId);
    }

    private static DungeonBankOpenRequestPayload read(FriendlyByteBuf buffer) {
        return new DungeonBankOpenRequestPayload(buffer.readVarLong());
    }
}
