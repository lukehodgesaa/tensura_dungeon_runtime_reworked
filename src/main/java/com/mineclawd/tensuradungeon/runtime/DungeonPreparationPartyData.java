package com.mineclawd.tensuradungeon.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonPreparationPartyData extends SavedData {
    static final String DATA_NAME = "tensura_dungeon_preparation_parties";
    public static final int MAX_MEMBERS = 4;
    private static final String PARTIES_KEY = "Parties";
    private static final String INVITES_KEY = "Invites";

    private final Map<UUID, PartyRecord> partiesByHost = new HashMap<>();
    private final Map<UUID, PendingInvite> invitesByTarget = new HashMap<>();

    public static DungeonPreparationPartyData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(DungeonPreparationPartyData::new, DungeonPreparationPartyData::load),
            DATA_NAME
        );
    }

    public static DungeonPreparationPartyData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        DungeonPreparationPartyData data = new DungeonPreparationPartyData();
        Set<UUID> claimedMembers = new HashSet<>();
        ListTag parties = tag.getList(PARTIES_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < parties.size(); index++) {
            CompoundTag partyTag = parties.getCompound(index);
            if (!partyTag.hasUUID("Host")) continue;
            UUID host = partyTag.getUUID("Host");
            ResourceLocation dimension = ResourceLocation.tryParse(partyTag.getString("Dimension"));
            if (dimension == null) continue;
            List<MemberRecord> members = new ArrayList<>();
            ListTag memberTags = partyTag.getList("Members", Tag.TAG_COMPOUND);
            for (int memberIndex = 0; memberIndex < memberTags.size(); memberIndex++) {
                CompoundTag memberTag = memberTags.getCompound(memberIndex);
                if (!memberTag.hasUUID("Id")) continue;
                MemberKind kind = parseKind(memberTag.getString("Kind"));
                if (kind == null) continue;
                members.add(new MemberRecord(
                    memberTag.getUUID("Id"),
                    bounded(memberTag.getString("Name"), 32),
                    kind,
                    memberTag.getBoolean("Ready")
                ));
                if (members.size() == MAX_MEMBERS) break;
            }
            if (members.stream().noneMatch(member -> member.id().equals(host))) {
                members.add(0, new MemberRecord(host, "Host", MemberKind.PLAYER, false));
            }
            List<MemberRecord> boundedMembers =
                List.copyOf(members.subList(0, Math.min(MAX_MEMBERS, members.size())));
            if (boundedMembers.stream().map(MemberRecord::id).anyMatch(claimedMembers::contains)) {
                continue;
            }
            data.partiesByHost.put(host, new PartyRecord(
                host,
                dimension,
                BlockPos.of(partyTag.getLong("Table")),
                Math.max(1L, partyTag.getLong("Revision")),
                boundedMembers
            ));
            boundedMembers.stream().map(MemberRecord::id).forEach(claimedMembers::add);
        }
        ListTag invites = tag.getList(INVITES_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < invites.size(); index++) {
            CompoundTag inviteTag = invites.getCompound(index);
            if (!inviteTag.hasUUID("Target") || !inviteTag.hasUUID("Host")) continue;
            data.invitesByTarget.put(
                inviteTag.getUUID("Target"),
                new PendingInvite(
                    inviteTag.getUUID("Host"),
                    inviteTag.getUUID("Target"),
                    Math.max(0L, inviteTag.getLong("Created"))
                )
            );
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag parties = new ListTag();
        partiesByHost.values().stream()
            .sorted(Comparator.comparing(record -> record.host().toString()))
            .forEach(record -> {
                CompoundTag partyTag = new CompoundTag();
                partyTag.putUUID("Host", record.host());
                partyTag.putString("Dimension", record.dimension().toString());
                partyTag.putLong("Table", record.tablePos().asLong());
                partyTag.putLong("Revision", record.revision());
                ListTag members = new ListTag();
                for (MemberRecord member : record.members()) {
                    CompoundTag memberTag = new CompoundTag();
                    memberTag.putUUID("Id", member.id());
                    memberTag.putString("Name", member.name());
                    memberTag.putString("Kind", member.kind().name());
                    memberTag.putBoolean("Ready", member.ready());
                    members.add(memberTag);
                }
                partyTag.put("Members", members);
                parties.add(partyTag);
            });
        tag.put(PARTIES_KEY, parties);
        ListTag invites = new ListTag();
        invitesByTarget.values().stream()
            .sorted(Comparator.comparing(invite -> invite.target().toString()))
            .forEach(invite -> {
                CompoundTag inviteTag = new CompoundTag();
                inviteTag.putUUID("Host", invite.host());
                inviteTag.putUUID("Target", invite.target());
                inviteTag.putLong("Created", invite.createdGameTime());
                invites.add(inviteTag);
            });
        tag.put(INVITES_KEY, invites);
        return tag;
    }

    public PartyRecord open(ServerPlayer host, BlockPos tablePos) {
        UUID hostId = host.getUUID();
        PartyRecord existing = partiesByHost.get(hostId);
        if (existing == null) {
            PartyRecord joined = partyFor(hostId).orElse(null);
            if (joined != null) return joined;
        }
        if (existing == null) {
            PartyRecord created = new PartyRecord(
                hostId,
                host.level().dimension().location(),
                tablePos.immutable(),
                1L,
                List.of(new MemberRecord(
                    hostId,
                    host.getGameProfile().getName(),
                    MemberKind.PLAYER,
                    false
                ))
            );
            partiesByHost.put(hostId, created);
            setDirty();
            return created;
        }
        PartyRecord refreshed = existing.withLocation(
            host.level().dimension().location(),
            tablePos
        ).withMemberName(hostId, host.getGameProfile().getName());
        if (!refreshed.equals(existing)) {
            partiesByHost.put(hostId, refreshed);
            setDirty();
        }
        return refreshed;
    }

    public Optional<PartyRecord> partyFor(UUID memberId) {
        return partiesByHost.values().stream()
            .filter(party -> party.members().stream().anyMatch(member -> member.id().equals(memberId)))
            .findFirst();
    }

    public MutationResult invite(ServerPlayer host, UUID targetId, long observedRevision) {
        PartyRecord party = partiesByHost.get(host.getUUID());
        if (party == null) return result(null, MutationStatus.NO_PARTY);
        if (party.revision() != observedRevision) return result(party, MutationStatus.STALE_REVISION);
        if (party.members().size() >= MAX_MEMBERS) return result(party, MutationStatus.PARTY_FULL);
        ServerPlayer target = host.getServer().getPlayerList().getPlayer(targetId);
        if (!isNearbyCandidate(party, target)) {
            return result(party, MutationStatus.INVALID_CANDIDATE);
        }
        PartyRecord targetParty = partyFor(targetId).orElse(null);
        if (targetParty != null
            && !(targetParty.host().equals(targetId) && targetParty.members().size() == 1)) {
            return result(party, MutationStatus.ALREADY_IN_PARTY);
        }
        invitesByTarget.put(
            targetId,
            new PendingInvite(host.getUUID(), targetId, host.level().getGameTime())
        );
        setDirty();
        return result(party, MutationStatus.INVITED);
    }

    public MutationResult respond(ServerPlayer target, UUID hostId, boolean accept) {
        PendingInvite invite = invitesByTarget.get(target.getUUID());
        PartyRecord party = partiesByHost.get(hostId);
        if (invite == null || !invite.host().equals(hostId) || party == null) {
            return result(party, MutationStatus.INVITE_EXPIRED);
        }
        invitesByTarget.remove(target.getUUID());
        if (target.level().getGameTime() - invite.createdGameTime() > 1_200L) {
            setDirty();
            return result(party, MutationStatus.INVITE_EXPIRED);
        }
        if (!accept) {
            setDirty();
            return result(party, MutationStatus.DECLINED);
        }
        if (party.members().size() >= MAX_MEMBERS) {
            setDirty();
            return result(party, MutationStatus.PARTY_FULL);
        }
        PartyRecord targetParty = partyFor(target.getUUID()).orElse(null);
        if (targetParty != null
            && targetParty.host().equals(target.getUUID())
            && targetParty.members().size() == 1) {
            partiesByHost.remove(target.getUUID());
            targetParty = null;
        }
        if (targetParty != null || !isNearbyCandidate(party, target)) {
            setDirty();
            return result(party, MutationStatus.INVALID_CANDIDATE);
        }
        List<MemberRecord> members = new ArrayList<>(party.members());
        members.add(new MemberRecord(
            target.getUUID(),
            target.getGameProfile().getName(),
            MemberKind.PLAYER,
            false
        ));
        PartyRecord updated = party.withRoster(members);
        partiesByHost.put(hostId, updated);
        setDirty();
        return result(updated, MutationStatus.JOINED);
    }

    public MutationResult addSubordinate(
        ServerPlayer host,
        UUID entityId,
        String name,
        long observedRevision
    ) {
        PartyRecord party = partiesByHost.get(host.getUUID());
        if (party == null) return result(null, MutationStatus.NO_PARTY);
        if (party.revision() != observedRevision) return result(party, MutationStatus.STALE_REVISION);
        if (party.members().size() >= MAX_MEMBERS) return result(party, MutationStatus.PARTY_FULL);
        if (party.members().stream().anyMatch(member -> member.id().equals(entityId))) {
            return result(party, MutationStatus.ALREADY_IN_PARTY);
        }
        List<MemberRecord> members = new ArrayList<>(party.members());
        members.add(new MemberRecord(entityId, bounded(name, 32), MemberKind.SUBORDINATE, false));
        PartyRecord updated = party.withRoster(members);
        partiesByHost.put(host.getUUID(), updated);
        setDirty();
        return result(updated, MutationStatus.JOINED);
    }

    public MutationResult remove(
        ServerPlayer host,
        UUID memberId,
        long observedRevision
    ) {
        PartyRecord party = partiesByHost.get(host.getUUID());
        if (party == null) return result(null, MutationStatus.NO_PARTY);
        if (party.revision() != observedRevision) return result(party, MutationStatus.STALE_REVISION);
        if (memberId == null || memberId.equals(party.host())) {
            return result(party, MutationStatus.NOT_MEMBER);
        }
        List<MemberRecord> members = party.members().stream()
            .filter(member -> !member.id().equals(memberId))
            .toList();
        if (members.size() == party.members().size()) {
            return result(party, MutationStatus.NOT_MEMBER);
        }
        PartyRecord updated = party.withRoster(members);
        partiesByHost.put(host.getUUID(), updated);
        setDirty();
        return result(updated, MutationStatus.REMOVED);
    }

    public MutationResult toggleReady(
        ServerPlayer member,
        int selectedFloor,
        long observedRevision
    ) {
        return toggleReady(member, selectedFloor, observedRevision, Set.of());
    }

    public MutationResult toggleReady(
        ServerPlayer member,
        int selectedFloor,
        long observedRevision,
        Set<UUID> verifiedOwnedSubordinates
    ) {
        PartyRecord party = partyFor(member.getUUID()).orElse(null);
        if (party == null) return result(null, MutationStatus.NO_PARTY);
        if (party.revision() != observedRevision) return result(party, MutationStatus.STALE_REVISION);
        DungeonFloorProgressData progress = DungeonFloorProgressData.get(member.serverLevel());
        if (progress.getHighestUnlockedFloor(member.getUUID()) < selectedFloor) {
            return result(party, MutationStatus.FLOOR_LOCKED);
        }
        MemberRecord entry = party.members().stream()
            .filter(candidate -> candidate.id().equals(member.getUUID()))
            .filter(candidate -> candidate.kind() == MemberKind.PLAYER)
            .findFirst()
            .orElse(null);
        if (entry == null) {
            return result(party, MutationStatus.NOT_MEMBER);
        }
        Optional<List<MemberRecord>> replacements = readyStateFor(
            party,
            entry.id(),
            !entry.ready(),
            verifiedOwnedSubordinates
        );
        if (replacements.isEmpty()) {
            return result(party, MutationStatus.INVALID_CANDIDATE);
        }
        PartyRecord updated = party.withMembers(replacements.get());
        partiesByHost.put(party.host(), updated);
        setDirty();
        return result(updated, MutationStatus.READY_CHANGED);
    }

    static Optional<List<MemberRecord>> readyStateFor(
        PartyRecord party,
        UUID actorId,
        boolean ready,
        Set<UUID> verifiedOwnedSubordinates
    ) {
        MemberRecord actor = party.members().stream()
            .filter(member -> member.id().equals(actorId))
            .filter(member -> member.kind() == MemberKind.PLAYER)
            .findFirst()
            .orElse(null);
        if (actor == null) {
            return Optional.empty();
        }
        Set<UUID> retainedSubordinates = party.members().stream()
            .filter(member -> member.kind() == MemberKind.SUBORDINATE)
            .map(MemberRecord::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<UUID> verified = verifiedOwnedSubordinates == null
            ? Set.of()
            : verifiedOwnedSubordinates;
        if (ready
            && actorId.equals(party.host())
            && !verified.containsAll(retainedSubordinates)) {
            return Optional.empty();
        }
        return Optional.of(party.members().stream()
            .map(member -> member.id().equals(actorId)
                || actorId.equals(party.host())
                    && member.kind() == MemberKind.SUBORDINATE
                    ? member.withReady(ready)
                    : member)
            .toList());
    }

    public PartyRecord invalidateReadyForFloor(UUID hostId) {
        PartyRecord party = partiesByHost.get(hostId);
        if (party == null || party.members().stream().noneMatch(MemberRecord::ready)) return party;
        PartyRecord updated = party.withMembers(
            party.members().stream().map(member -> member.withReady(false)).toList()
        );
        partiesByHost.put(hostId, updated);
        setDirty();
        return updated;
    }

    public PartyRecord invalidateReadyForMember(UUID memberId) {
        PartyRecord party = partyFor(memberId).orElse(null);
        if (party == null) return null;
        List<MemberRecord> members = new ArrayList<>(party.members());
        boolean changed = false;
        for (int index = 0; index < members.size(); index++) {
            MemberRecord member = members.get(index);
            boolean hostLinkedSubordinate = memberId.equals(party.host())
                && member.kind() == MemberKind.SUBORDINATE;
            if ((member.id().equals(memberId) || hostLinkedSubordinate) && member.ready()) {
                members.set(index, member.withReady(false));
                changed = true;
            }
        }
        if (!changed) return party;
        PartyRecord updated = party.withMembers(members);
        partiesByHost.put(party.host(), updated);
        setDirty();
        return updated;
    }

    public Optional<PendingInvite> pendingInvite(UUID targetId) {
        return Optional.ofNullable(invitesByTarget.get(targetId));
    }

    private static boolean isNearbyCandidate(PartyRecord party, ServerPlayer target) {
        if (target == null || !target.isAlive()
            || !target.level().dimension().location().equals(party.dimension())) {
            return false;
        }
        return target.distanceToSqr(
            party.tablePos().getX() + 0.5D,
            party.tablePos().getY() + 0.5D,
            party.tablePos().getZ() + 0.5D
        ) <= 64.0D * 64.0D;
    }

    private static MutationResult result(PartyRecord party, MutationStatus status) {
        return new MutationResult(status, party);
    }

    private static MemberKind parseKind(String value) {
        try {
            return MemberKind.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String bounded(String value, int max) {
        if (value == null) return "";
        return value.substring(0, Math.min(max, value.length()));
    }

    public enum MemberKind {
        PLAYER,
        SUBORDINATE
    }

    public record MemberRecord(UUID id, String name, MemberKind kind, boolean ready) {
        public MemberRecord {
            if (id == null || kind == null) throw new IllegalArgumentException("Incomplete party member");
            name = bounded(name, 32);
        }

        MemberRecord withReady(boolean replacement) {
            return new MemberRecord(id, name, kind, replacement);
        }
    }

    public record PartyRecord(
        UUID host,
        ResourceLocation dimension,
        BlockPos tablePos,
        long revision,
        List<MemberRecord> members
    ) {
        public PartyRecord {
            if (host == null || dimension == null || tablePos == null) {
                throw new IllegalArgumentException("Incomplete preparation party");
            }
            revision = Math.max(1L, revision);
            List<MemberRecord> boundedMembers = members == null ? List.of() : List.copyOf(members);
            if (boundedMembers.isEmpty() || boundedMembers.size() > MAX_MEMBERS) {
                throw new IllegalArgumentException("Preparation party size is out of bounds");
            }
            Set<UUID> ids = new HashSet<>();
            for (MemberRecord member : boundedMembers) {
                if (!ids.add(member.id())) throw new IllegalArgumentException("Duplicate party member");
            }
            members = boundedMembers;
            tablePos = tablePos.immutable();
        }

        PartyRecord withLocation(ResourceLocation newDimension, BlockPos newTablePos) {
            if (dimension.equals(newDimension) && tablePos.equals(newTablePos)) return this;
            return new PartyRecord(host, newDimension, newTablePos, revision + 1L, members);
        }

        PartyRecord withMemberName(UUID memberId, String name) {
            List<MemberRecord> changed = new ArrayList<>(members);
            for (int index = 0; index < changed.size(); index++) {
                MemberRecord member = changed.get(index);
                if (member.id().equals(memberId) && !member.name().equals(name)) {
                    changed.set(index, new MemberRecord(member.id(), name, member.kind(), member.ready()));
                    return new PartyRecord(host, dimension, tablePos, revision + 1L, changed);
                }
            }
            return this;
        }

        PartyRecord withMembers(List<MemberRecord> replacements) {
            return new PartyRecord(host, dimension, tablePos, revision + 1L, replacements);
        }

        PartyRecord withRoster(List<MemberRecord> replacements) {
            return new PartyRecord(
                host,
                dimension,
                tablePos,
                revision + 1L,
                replacements.stream().map(member -> member.withReady(false)).toList()
            );
        }
    }

    public record PendingInvite(UUID host, UUID target, long createdGameTime) {}

    public enum MutationStatus {
        INVITED,
        DECLINED,
        JOINED,
        REMOVED,
        READY_CHANGED,
        NO_PARTY,
        NOT_MEMBER,
        STALE_REVISION,
        PARTY_FULL,
        INVALID_CANDIDATE,
        ALREADY_IN_PARTY,
        INVITE_EXPIRED,
        FLOOR_LOCKED
    }

    public record MutationResult(MutationStatus status, PartyRecord party) {}
}
