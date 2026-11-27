package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.DungeonPreparationPartyData;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorWorldWorkScheduler;
import com.mineclawd.tensuradungeon.runtime.PreparedFloorModifierPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

final class DungeonPreparationPartySnapshotFactory {
    private static final double CANDIDATE_RADIUS = 64.0D;
    private DungeonPreparationPartySnapshotFactory() {}

    static DungeonPreparationPartySnapshotPayload create(ServerPlayer viewer, String message) {
        DungeonPreparationPartyData data = DungeonPreparationPartyData.get(viewer.serverLevel());
        DungeonPreparationPartyData.PartyRecord party = data.partyFor(viewer.getUUID())
            .orElseGet(() -> data.open(viewer, viewer.blockPosition()));
        ServerLevel partyLevel = viewer.getServer().getLevel(
            ResourceKey.create(Registries.DIMENSION, party.dimension())
        );
        DungeonFloorProgressData progress = DungeonFloorProgressData.get(viewer.serverLevel());
        int selectedFloor = progress.getSelectedFloor(party.host());
        PreparedFloorModifierPlan modifierPlan =
            progress.getOrPrepareModifierPlan(party.host(), selectedFloor);
        Set<UUID> memberIds = new HashSet<>();
        List<DungeonPreparationPartySnapshotPayload.Member> members = new ArrayList<>();
        for (DungeonPreparationPartyData.MemberRecord member : party.members()) {
            memberIds.add(member.id());
            ServerPlayer online = viewer.getServer().getPlayerList().getPlayer(member.id());
            boolean player = member.kind() == DungeonPreparationPartyData.MemberKind.PLAYER;
            LivingEntity subordinate = !player && partyLevel != null
                && partyLevel.getEntity(member.id()) instanceof LivingEntity living
                    ? living
                    : null;
            int highest = player && online != null
                ? progress.getHighestUnlockedFloor(member.id())
                : player ? 0 : selectedFloor;
            boolean eligible = !player || online != null && highest >= selectedFloor;
            members.add(new DungeonPreparationPartySnapshotPayload.Member(
                member.id(),
                member.name(),
                player ? "" : TensuraCompat.raceDisplayName(subordinate),
                player
                    ? DungeonPreparationPartySnapshotPayload.Kind.PLAYER
                    : DungeonPreparationPartySnapshotPayload.Kind.SUBORDINATE,
                member.id().equals(party.host()),
                !player || online != null,
                eligible,
                highest,
                member.ready() && eligible
            ));
        }
        List<DungeonPreparationPartySnapshotPayload.Candidate> players = new ArrayList<>();
        if (viewer.getUUID().equals(party.host())) {
            for (ServerPlayer candidate : viewer.getServer().getPlayerList().getPlayers()) {
                DungeonPreparationPartyData.PartyRecord candidateParty =
                    data.partyFor(candidate.getUUID()).orElse(null);
                boolean unavailableParty = candidateParty != null
                    && !(candidateParty.host().equals(candidate.getUUID())
                        && candidateParty.members().size() == 1);
                if (memberIds.contains(candidate.getUUID())
                    || unavailableParty
                    || !candidate.level().dimension().location().equals(party.dimension())
                    || candidate.distanceToSqr(
                        party.tablePos().getX() + 0.5D,
                        party.tablePos().getY() + 0.5D,
                        party.tablePos().getZ() + 0.5D
                    ) > CANDIDATE_RADIUS * CANDIDATE_RADIUS) {
                    continue;
                }
                players.add(new DungeonPreparationPartySnapshotPayload.Candidate(
                    candidate.getUUID(),
                    candidate.getGameProfile().getName(),
                    DungeonPreparationPartySnapshotPayload.Kind.PLAYER
                ));
            }
        }
        players.sort(Comparator.comparing(
            DungeonPreparationPartySnapshotPayload.Candidate::name,
            String.CASE_INSENSITIVE_ORDER
        ));
        List<DungeonPreparationPartySnapshotPayload.Candidate> subordinates =
            viewer.getUUID().equals(party.host()) && partyLevel != null
                ? subordinateCandidates(partyLevel, party, memberIds)
                : List.of();
        DispatchProjection dispatch = dispatchProjection(viewer, party);
        return new DungeonPreparationPartySnapshotPayload(
            party.revision(),
            party.host(),
            selectedFloor,
            viewer.getUUID().equals(party.host()),
            members,
            players,
            subordinates,
            modifierPlan.hostileModifierIds(),
            modifierPlan.boonModifierId(),
            message,
            dispatch.state(),
            dispatch.phase(),
            dispatch.elapsedSeconds(),
            dispatch.estimatedRemainingSeconds()
        );
    }

    private static DispatchProjection dispatchProjection(
        ServerPlayer viewer,
        DungeonPreparationPartyData.PartyRecord party
    ) {
        boolean subordinateRoster = party.members().stream().skip(1)
            .allMatch(member -> member.kind() == DungeonPreparationPartyData.MemberKind.SUBORDINATE);
        if (party.members().isEmpty()
            || party.members().getFirst().kind() != DungeonPreparationPartyData.MemberKind.PLAYER
            || !party.members().getFirst().id().equals(party.host())
            || !subordinateRoster) {
            return new DispatchProjection(
                DungeonPreparationPartySnapshotPayload.DispatchState.UNSUPPORTED_PARTY,
                "Invited-player dispatch is not available yet.",
                0,
                -1
            );
        }
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(viewer.serverLevel())
                .forPlayer(party.host())
                .orElse(null);
        if (allocation == null) {
            return new DispatchProjection(
                DungeonPreparationPartySnapshotPayload.DispatchState.IDLE,
                "Ready to start.",
                0,
                -1
            );
        }
        if (allocation.soloPreparation() == null
            || !frozenRosterMatches(allocation.soloPreparation(), party)) {
            return new DispatchProjection(
                DungeonPreparationPartySnapshotPayload.DispatchState.FAILED,
                "Frozen participant roster does not match this briefing.",
                0,
                -1
            );
        }
        if (allocation.entryDispatch() != null
            && allocation.entryDispatch().state()
                == DungeonInstanceAllocationData.DispatchState.PENDING_TELEPORT) {
            return new DispatchProjection(
                DungeonPreparationPartySnapshotPayload.DispatchState.PENDING_TELEPORT,
                "Entry route committed; teleport pending.",
                0,
                -1
            );
        }
        DungeonPreparationPartySnapshotPayload.DispatchState state = switch (
            allocation.lifecycle().state()
        ) {
            case PREPARING -> DungeonPreparationPartySnapshotPayload.DispatchState.PREPARING;
            case READY -> DungeonPreparationPartySnapshotPayload.DispatchState.READY;
            case ACTIVE, SUSPENDED -> DungeonPreparationPartySnapshotPayload.DispatchState.ACTIVE;
            case COMPLETING, CLEANING ->
                DungeonPreparationPartySnapshotPayload.DispatchState.CLEANUP_PENDING;
            case FAILED_PREPARATION, QUARANTINED ->
                DungeonPreparationPartySnapshotPayload.DispatchState.FAILED;
            case ALLOCATED, REUSABLE ->
                DungeonPreparationPartySnapshotPayload.DispatchState.IDLE;
        };
        String phase = allocation.lifecycle().transitionReason();
        ServerLevel allocationLevel = viewer.getServer().getLevel(ResourceKey.create(
            Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.parse(allocation.dimension())
        ));
        GeneratedFloorWorldWorkScheduler.PreparationTiming timing = allocationLevel == null
            ? new GeneratedFloorWorldWorkScheduler.PreparationTiming(0, -1, 0)
            : GeneratedFloorWorldWorkScheduler.preparationTiming(allocationLevel, allocation);
        return new DispatchProjection(
            state,
            phase.isBlank() ? "Ready to start." : phase,
            timing.elapsedSeconds(),
            timing.estimatedRemainingSeconds()
        );
    }

    private static boolean frozenRosterMatches(
        DungeonInstanceAllocationData.SoloPreparation preparation,
        DungeonPreparationPartyData.PartyRecord party
    ) {
        if (preparation.frozenParticipants().size() != party.members().size()) return false;
        for (int index = 0; index < party.members().size(); index++) {
            DungeonPreparationPartyData.MemberRecord member = party.members().get(index);
            DungeonInstanceAllocationData.FrozenParticipant frozen =
                preparation.frozenParticipants().get(index);
            DungeonInstanceAllocationData.FrozenParticipantKind expectedKind = member.kind()
                == DungeonPreparationPartyData.MemberKind.PLAYER
                    ? DungeonInstanceAllocationData.FrozenParticipantKind.HUMAN
                    : DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE;
            if (!frozen.id().equals(member.id()) || frozen.kind() != expectedKind
                || frozen.slot() != index + 1 || !frozen.ownerId().equals(party.host())) {
                return false;
            }
        }
        return true;
    }

    private record DispatchProjection(
        DungeonPreparationPartySnapshotPayload.DispatchState state,
        String phase,
        int elapsedSeconds,
        int estimatedRemainingSeconds
    ) {}

    private static List<DungeonPreparationPartySnapshotPayload.Candidate> subordinateCandidates(
        ServerLevel level,
        DungeonPreparationPartyData.PartyRecord party,
        Set<UUID> memberIds
    ) {
        AABB bounds = new AABB(party.tablePos()).inflate(CANDIDATE_RADIUS);
        List<DungeonPreparationPartySnapshotPayload.Candidate> candidates = new ArrayList<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
            if (entity instanceof ServerPlayer || !entity.isAlive() || memberIds.contains(entity.getUUID())) {
                continue;
            }
            if (!TensuraCompat.subordinateOwnerId(entity)
                .filter(party.host()::equals)
                .isPresent()) {
                continue;
            }
            candidates.add(new DungeonPreparationPartySnapshotPayload.Candidate(
                entity.getUUID(),
                entity.getName().getString(),
                DungeonPreparationPartySnapshotPayload.Kind.SUBORDINATE
            ));
            if (candidates.size() == 32) break;
        }
        candidates.sort(Comparator.comparing(
            DungeonPreparationPartySnapshotPayload.Candidate::name,
            String.CASE_INSENSITIVE_ORDER
        ));
        return List.copyOf(candidates);
    }
}
