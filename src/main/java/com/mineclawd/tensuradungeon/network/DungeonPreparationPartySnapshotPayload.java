package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.PreparedFloorModifierPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonPreparationPartySnapshotPayload(
    long revision,
    UUID hostId,
    int selectedFloor,
    boolean viewerIsHost,
    List<Member> members,
    List<Candidate> playerCandidates,
    List<Candidate> subordinateCandidates,
    List<String> hostileModifierIds,
    String boonModifierId,
    String message,
    DispatchState dispatchState,
    String dispatchPhase,
    int preparationElapsedSeconds,
    int estimatedRemainingSeconds
) implements CustomPacketPayload {
    public static final Type<DungeonPreparationPartySnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID,
            "preparation_party_snapshot"
        )
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonPreparationPartySnapshotPayload>
        STREAM_CODEC = StreamCodec.of(
            DungeonPreparationPartySnapshotPayload::write,
            DungeonPreparationPartySnapshotPayload::read
        );

    public DungeonPreparationPartySnapshotPayload {
        revision = Math.max(0L, revision);
        selectedFloor = Math.max(1, selectedFloor);
        members = bounded(members, 4);
        playerCandidates = bounded(playerCandidates, 32);
        subordinateCandidates = bounded(subordinateCandidates, 32);
        hostileModifierIds = boundedStrings(
            hostileModifierIds, PreparedFloorModifierPlan.MAX_HOSTILE_MODIFIERS, 64
        );
        boonModifierId = bounded(boonModifierId, 64);
        message = bounded(message, 192);
        dispatchState = dispatchState == null ? DispatchState.IDLE : dispatchState;
        dispatchPhase = bounded(dispatchPhase, 96);
        preparationElapsedSeconds = Math.max(0, Math.min(359_999, preparationElapsedSeconds));
        estimatedRemainingSeconds = Math.max(-1, Math.min(3_599, estimatedRemainingSeconds));
    }

    public DungeonPreparationPartySnapshotPayload(
        long revision,
        UUID hostId,
        int selectedFloor,
        boolean viewerIsHost,
        List<Member> members,
        List<Candidate> playerCandidates,
        List<Candidate> subordinateCandidates,
        List<String> hostileModifierIds,
        String boonModifierId,
        String message
    ) {
        this(
            revision,
            hostId,
            selectedFloor,
            viewerIsHost,
            members,
            playerCandidates,
            subordinateCandidates,
            hostileModifierIds,
            boonModifierId,
            message,
            DispatchState.IDLE,
            "",
            0,
            -1
        );
    }

    public DungeonPreparationPartySnapshotPayload(
        long revision,
        UUID hostId,
        int selectedFloor,
        boolean viewerIsHost,
        List<Member> members,
        List<Candidate> playerCandidates,
        List<Candidate> subordinateCandidates,
        List<String> hostileModifierIds,
        String boonModifierId,
        String message,
        DispatchState dispatchState,
        String dispatchPhase
    ) {
        this(
            revision,
            hostId,
            selectedFloor,
            viewerIsHost,
            members,
            playerCandidates,
            subordinateCandidates,
            hostileModifierIds,
            boonModifierId,
            message,
            dispatchState,
            dispatchPhase,
            0,
            -1
        );
    }

    private static void write(
        FriendlyByteBuf buffer,
        DungeonPreparationPartySnapshotPayload payload
    ) {
        buffer.writeVarLong(payload.revision);
        buffer.writeUUID(payload.hostId);
        buffer.writeVarInt(payload.selectedFloor);
        buffer.writeBoolean(payload.viewerIsHost);
        buffer.writeVarInt(payload.members.size());
        payload.members.forEach(member -> member.write(buffer));
        buffer.writeVarInt(payload.playerCandidates.size());
        payload.playerCandidates.forEach(candidate -> candidate.write(buffer));
        buffer.writeVarInt(payload.subordinateCandidates.size());
        payload.subordinateCandidates.forEach(candidate -> candidate.write(buffer));
        buffer.writeVarInt(payload.hostileModifierIds.size());
        payload.hostileModifierIds.forEach(id -> buffer.writeUtf(id, 64));
        buffer.writeUtf(payload.boonModifierId, 64);
        buffer.writeUtf(payload.message, 192);
        buffer.writeEnum(payload.dispatchState);
        buffer.writeUtf(payload.dispatchPhase, 96);
        buffer.writeVarInt(payload.preparationElapsedSeconds);
        buffer.writeVarInt(payload.estimatedRemainingSeconds);
    }

    private static DungeonPreparationPartySnapshotPayload read(FriendlyByteBuf buffer) {
        long revision = buffer.readVarLong();
        UUID hostId = buffer.readUUID();
        int selectedFloor = buffer.readVarInt();
        boolean viewerIsHost = buffer.readBoolean();
        int memberCount = readCount(buffer, 4, "party members");
        List<Member> members = new ArrayList<>(memberCount);
        for (int index = 0; index < memberCount; index++) members.add(Member.read(buffer));
        int playerCount = readCount(buffer, 32, "player candidates");
        List<Candidate> players = new ArrayList<>(playerCount);
        for (int index = 0; index < playerCount; index++) players.add(Candidate.read(buffer));
        int subordinateCount = readCount(buffer, 32, "subordinate candidates");
        List<Candidate> subordinates = new ArrayList<>(subordinateCount);
        for (int index = 0; index < subordinateCount; index++) {
            subordinates.add(Candidate.read(buffer));
        }
        int modifierCount = readCount(
            buffer, PreparedFloorModifierPlan.MAX_HOSTILE_MODIFIERS, "hostile modifiers"
        );
        List<String> hostileModifierIds = new ArrayList<>(modifierCount);
        for (int index = 0; index < modifierCount; index++) {
            hostileModifierIds.add(buffer.readUtf(64));
        }
        return new DungeonPreparationPartySnapshotPayload(
            revision,
            hostId,
            selectedFloor,
            viewerIsHost,
            members,
            players,
            subordinates,
            hostileModifierIds,
            buffer.readUtf(64),
            buffer.readUtf(192),
            buffer.readEnum(DispatchState.class),
            buffer.readUtf(96),
            buffer.readVarInt(),
            buffer.readVarInt()
        );
    }

    public boolean partyEligible() {
        return members.stream()
            .filter(member -> member.kind() == Kind.PLAYER)
            .allMatch(member -> member.online() && member.floorEligible());
    }

    public boolean allPlayersReady() {
        return partyEligible()
            && members.stream()
                .filter(member -> member.kind() == Kind.PLAYER)
                .allMatch(Member::ready);
    }

    public boolean soloDispatchRoster() {
        return members.size() == 1
            && members.getFirst().kind() == Kind.PLAYER
            && members.getFirst().host();
    }

    public boolean subordinatePreparationRoster() {
        if (members.isEmpty() || members.getFirst().kind() != Kind.PLAYER
            || !members.getFirst().host()) {
            return false;
        }
        return members.stream().skip(1).allMatch(member -> member.kind() == Kind.SUBORDINATE);
    }

    public UUID preferredMemberId(UUID viewerId) {
        return viewerId != null && members.stream().anyMatch(member -> member.id().equals(viewerId))
            ? viewerId
            : hostId;
    }

    public record Member(
        UUID id,
        String name,
        String raceName,
        Kind kind,
        boolean host,
        boolean online,
        boolean floorEligible,
        int highestUnlockedFloor,
        boolean ready
    ) {
        public Member {
            name = bounded(name, 32);
            raceName = bounded(raceName, 48);
            highestUnlockedFloor = Math.max(0, highestUnlockedFloor);
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(id);
            buffer.writeUtf(name, 32);
            buffer.writeUtf(raceName, 48);
            buffer.writeEnum(kind);
            buffer.writeBoolean(host);
            buffer.writeBoolean(online);
            buffer.writeBoolean(floorEligible);
            buffer.writeVarInt(highestUnlockedFloor);
            buffer.writeBoolean(ready);
        }

        private static Member read(FriendlyByteBuf buffer) {
            return new Member(
                buffer.readUUID(),
                buffer.readUtf(32),
                buffer.readUtf(48),
                buffer.readEnum(Kind.class),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readBoolean()
            );
        }
    }

    public record Candidate(UUID id, String name, Kind kind) {
        public Candidate {
            name = bounded(name, 32);
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUUID(id);
            buffer.writeUtf(name, 32);
            buffer.writeEnum(kind);
        }

        private static Candidate read(FriendlyByteBuf buffer) {
            return new Candidate(buffer.readUUID(), buffer.readUtf(32), buffer.readEnum(Kind.class));
        }
    }

    public enum Kind {
        PLAYER,
        SUBORDINATE
    }

    public enum DispatchState {
        IDLE,
        PREPARING,
        READY,
        READY_TRANSPORT_PENDING,
        PENDING_TELEPORT,
        ACTIVE,
        FAILED,
        UNSUPPORTED_PARTY,
        CLEANUP_PENDING
    }

    private static int readCount(FriendlyByteBuf buffer, int max, String field) {
        int count = buffer.readVarInt();
        if (count < 0 || count > max) {
            throw new IllegalArgumentException("Invalid " + field + " count: " + count);
        }
        return count;
    }

    private static <T> List<T> bounded(List<T> values, int max) {
        if (values == null || values.isEmpty()) return List.of();
        return List.copyOf(values.subList(0, Math.min(max, values.size())));
    }

    private static List<String> boundedStrings(List<String> values, int max, int length) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().limit(max).map(value -> bounded(value, length)).toList();
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.substring(0, Math.min(max, value.length()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
