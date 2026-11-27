package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SubordinateDebriefPayload(
    int entityId,
    String subordinateName,
    Issue issue
) implements CustomPacketPayload {
    public static final int MAX_NAME_LENGTH = 64;
    public static final Type<SubordinateDebriefPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "subordinate_debrief")
    );
    public static final StreamCodec<FriendlyByteBuf, SubordinateDebriefPayload> STREAM_CODEC =
        StreamCodec.of(SubordinateDebriefPayload::write, SubordinateDebriefPayload::read);

    public SubordinateDebriefPayload {
        if (entityId <= 0) {
            throw new IllegalArgumentException("Debrief requires a live subordinate entity id");
        }
        subordinateName = subordinateName == null ? "" : subordinateName;
        if (subordinateName.length() > MAX_NAME_LENGTH) {
            subordinateName = subordinateName.substring(0, MAX_NAME_LENGTH);
        }
        issue = issue == null ? Issue.GENERIC : issue;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, SubordinateDebriefPayload payload) {
        buffer.writeInt(payload.entityId);
        buffer.writeUtf(payload.subordinateName, MAX_NAME_LENGTH);
        buffer.writeEnum(payload.issue);
    }

    private static SubordinateDebriefPayload read(FriendlyByteBuf buffer) {
        return new SubordinateDebriefPayload(
            buffer.readInt(),
            buffer.readUtf(MAX_NAME_LENGTH),
            buffer.readEnum(Issue.class)
        );
    }

    public enum Issue {
        MAGICULE,
        HURT,
        GENERIC
    }
}
