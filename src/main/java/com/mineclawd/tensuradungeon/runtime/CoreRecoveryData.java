package com.mineclawd.tensuradungeon.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class CoreRecoveryData extends SavedData {
    static final String DATA_NAME = "tensura_dungeon_core_recovery";
    private static final String OPERATOR_TEST_PREFIX = "operator-test-tier1:";
    private static final int MAX_OPERATOR_TEST_SEARCH = 100_000;
    private final Map<UUID, Map<String, CompletedRecovery>> recoveriesByPlayer = new HashMap<>();
    private final Map<UUID, Map<String, ActiveRecovery>> activeRecoveriesByPlayer = new HashMap<>();

    public static CoreRecoveryData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(CoreRecoveryData::new, CoreRecoveryData::load),
            DATA_NAME
        );
    }

    public static CoreRecoveryData load(CompoundTag tag, HolderLookup.Provider registries) {
        CoreRecoveryData data = new CoreRecoveryData();
        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            CompoundTag playerTag = players.getCompound(playerIndex);
            if (!playerTag.hasUUID("Player")) {
                throw new IllegalStateException("Core recovery player record is missing its UUID");
            }
            UUID playerId = playerTag.getUUID("Player");
            Map<String, CompletedRecovery> sessions = new HashMap<>();
            ListTag recoveryTags = playerTag.getList("CompletedRecoveries", Tag.TAG_COMPOUND);
            for (int recoveryIndex = 0; recoveryIndex < recoveryTags.size(); recoveryIndex++) {
                CompletedRecovery recovery = CompletedRecovery.load(recoveryTags.getCompound(recoveryIndex));
                if (sessions.putIfAbsent(recoveryKey(recovery.sessionId(), recovery.floor()), recovery) != null) {
                    throw new IllegalStateException(
                        "Duplicate completed Core recovery session/floor in save: "
                            + recovery.sessionId() + "/" + recovery.floor()
                    );
                }
            }
            if (!sessions.isEmpty()) data.recoveriesByPlayer.put(playerId, sessions);

            Map<String, ActiveRecovery> activeSessions = new HashMap<>();
            ListTag activeTags = playerTag.getList("ActiveRecoveries", Tag.TAG_COMPOUND);
            for (int activeIndex = 0; activeIndex < activeTags.size(); activeIndex++) {
                ActiveRecovery active = ActiveRecovery.load(activeTags.getCompound(activeIndex));
                String key = recoveryKey(active.sessionId(), active.floor());
                if (activeSessions.putIfAbsent(key, active) != null) {
                    throw new IllegalStateException(
                        "Duplicate active Core recovery session/floor in save: "
                            + active.sessionId() + "/" + active.floor()
                    );
                }
                if (sessions.containsKey(key)) {
                    throw new IllegalStateException(
                        "Core recovery session/floor cannot be both active and completed: "
                            + active.sessionId() + "/" + active.floor()
                    );
                }
            }
            if (!activeSessions.isEmpty()) data.activeRecoveriesByPlayer.put(playerId, activeSessions);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        Set<UUID> playerIds = new HashSet<>(recoveriesByPlayer.keySet());
        playerIds.addAll(activeRecoveriesByPlayer.keySet());
        playerIds.stream()
            .sorted(Comparator.comparing(UUID::toString))
            .forEach(playerId -> {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID("Player", playerId);
                ListTag recoveries = new ListTag();
                recoveriesByPlayer.getOrDefault(playerId, Map.of()).values().stream()
                    .sorted(Comparator.comparing(CompletedRecovery::sessionId)
                        .thenComparingInt(CompletedRecovery::floor))
                    .forEach(recovery -> recoveries.add(recovery.save()));
                playerTag.put("CompletedRecoveries", recoveries);
                ListTag activeRecoveries = new ListTag();
                activeRecoveriesByPlayer.getOrDefault(playerId, Map.of()).values().stream()
                    .sorted(Comparator.comparing(ActiveRecovery::sessionId)
                        .thenComparingInt(ActiveRecovery::floor))
                    .forEach(active -> activeRecoveries.add(active.save()));
                playerTag.put("ActiveRecoveries", activeRecoveries);
                players.add(playerTag);
            });
        tag.put("Players", players);
        return tag;
    }

    public FinalizeResult finalizeSuccessfulRun(
        UUID playerId,
        String sessionId,
        int floor,
        long runSeed,
        List<EncounterInput> encounters
    ) {
        if (playerId == null) throw new IllegalArgumentException("Recovery player UUID is required");
        validateSessionId(sessionId);
        MonsterCoreRecoveryPolicy.currentTier(floor);
        if (encounters == null) throw new IllegalArgumentException("Recovery encounter list is required");

        String recoveryKey = recoveryKey(sessionId, floor);
        Map<String, CompletedRecovery> sessions = recoveriesByPlayer.get(playerId);
        CompletedRecovery existing = sessions == null ? null : sessions.get(recoveryKey);
        if (existing != null) {
            return new FinalizeResult(FinalizeStatus.ALREADY_FINALIZED, existing);
        }

        Set<String> encounterKeys = new HashSet<>();
        List<RecoveryAttempt> attempts = new ArrayList<>();
        for (EncounterInput encounter : encounters) {
            if (encounter == null) throw new IllegalArgumentException("Recovery encounter cannot be null");
            if (!encounterKeys.add(encounter.encounterKey())) {
                throw new IllegalArgumentException(
                    "Duplicate Core recovery encounter key: " + encounter.encounterKey()
                );
            }
            MonsterCoreRecoveryPolicy.RecoveryRoll roll = MonsterCoreRecoveryPolicy.roll(
                new MonsterCoreRecoveryPolicy.RecoveryRequest(
                    encounter.encounterKey(),
                    floor,
                    encounter.source(),
                    encounter.firstClearMilestone(),
                    runSeed
                )
            );
            attempts.add(new RecoveryAttempt(encounter, roll));
        }

        CompletedRecovery recovery = new CompletedRecovery(sessionId, floor, runSeed, attempts, Set.of());
        if (sessions == null) {
            sessions = new HashMap<>();
            recoveriesByPlayer.put(playerId, sessions);
        }
        sessions.put(recoveryKey, recovery);
        setDirty();
        return new FinalizeResult(FinalizeStatus.FINALIZED, recovery);
    }

    public CompletedRecovery snapshot(UUID playerId, String sessionId) {
        Map<String, CompletedRecovery> sessions = recoveriesByPlayer.get(playerId);
        if (sessions == null) return CompletedRecovery.missing();
        return sessions.values().stream()
            .filter(recovery -> recovery.sessionId().equals(sessionId))
            .max(Comparator.comparingInt(CompletedRecovery::floor))
            .orElse(CompletedRecovery.missing());
    }

    public CompletedRecovery snapshot(UUID playerId, String sessionId, int floor) {
        Map<String, CompletedRecovery> sessions = recoveriesByPlayer.get(playerId);
        return sessions == null
            ? CompletedRecovery.missing()
            : sessions.getOrDefault(recoveryKey(sessionId, floor), CompletedRecovery.missing());
    }

    public TestCandidateResult injectTierOneTestCandidate(
        UUID playerId,
        String sessionId,
        int floor
    ) {
        if (playerId == null) throw new IllegalArgumentException("Recovery player UUID is required");
        validateSessionId(sessionId);
        MonsterCoreRecoveryPolicy.currentTier(floor);
        String key = recoveryKey(sessionId, floor);
        Map<String, CompletedRecovery> sessions = recoveriesByPlayer.get(playerId);
        CompletedRecovery recovery = sessions == null ? null : sessions.get(key);
        if (recovery == null) {
            return new TestCandidateResult(
                TestCandidateStatus.SESSION_NOT_FOUND,
                null,
                CompletedRecovery.missing()
            );
        }
        if (recovery.summaryFinalized()) {
            return new TestCandidateResult(
                TestCandidateStatus.SUMMARY_FINALIZED,
                null,
                recovery
            );
        }

        RecoveryAttempt pendingFixture = recovery.attempts().stream()
            .filter(attempt -> attempt.encounter().encounterKey().startsWith(OPERATOR_TEST_PREFIX))
            .filter(attempt -> attempt.roll().recovered() && attempt.roll().core().tier() == 1)
            .filter(attempt -> !recovery.claimedCoreIds().contains(attempt.roll().core().coreId()))
            .findFirst()
            .orElse(null);
        if (pendingFixture != null) {
            return new TestCandidateResult(
                TestCandidateStatus.ALREADY_PENDING,
                pendingFixture.roll().core(),
                recovery
            );
        }

        Set<String> existingKeys = recovery.attempts().stream()
            .map(attempt -> attempt.encounter().encounterKey())
            .collect(java.util.stream.Collectors.toSet());
        for (int index = 0; index < MAX_OPERATOR_TEST_SEARCH; index++) {
            String encounterKey = OPERATOR_TEST_PREFIX + index;
            if (existingKeys.contains(encounterKey)) continue;
            EncounterInput encounter = new EncounterInput(
                encounterKey,
                MonsterCoreRecoveryPolicy.EncounterSource.ORDINARY_COMBAT,
                false
            );
            MonsterCoreRecoveryPolicy.RecoveryRoll roll = MonsterCoreRecoveryPolicy.roll(
                new MonsterCoreRecoveryPolicy.RecoveryRequest(
                    encounterKey,
                    floor,
                    encounter.source(),
                    false,
                    recovery.runSeed()
                )
            );
            if (!roll.recovered() || roll.core().tier() != 1) continue;

            List<RecoveryAttempt> attempts = new ArrayList<>(recovery.attempts());
            attempts.add(new RecoveryAttempt(encounter, roll));
            CompletedRecovery updated = new CompletedRecovery(
                recovery.sessionId(),
                recovery.floor(),
                recovery.runSeed(),
                attempts,
                recovery.returnedCandidates(),
                recovery.claimedCoreIds(),
                recovery.summaryFinalized()
            );
            sessions.put(key, updated);
            setDirty();
            return new TestCandidateResult(
                TestCandidateStatus.INJECTED,
                roll.core(),
                updated
            );
        }
        return new TestCandidateResult(
            TestCandidateStatus.NO_TIER_ONE_RESULT,
            null,
            recovery
        );
    }

    public ClaimResult claimCandidate(
        UUID playerId,
        String sessionId,
        int floor,
        UUID coreId,
        int slot,
        CoreSatchelData satchel
    ) {
        if (playerId == null || coreId == null || satchel == null) {
            throw new IllegalArgumentException("Core recovery claim values are required");
        }
        validateSessionId(sessionId);
        String key = recoveryKey(sessionId, floor);
        Map<String, CompletedRecovery> sessions = recoveriesByPlayer.get(playerId);
        CompletedRecovery recovery = sessions == null ? null : sessions.get(key);
        if (recovery == null) {
            return new ClaimResult(ClaimStatus.SESSION_NOT_FOUND, slot, null, CompletedRecovery.missing());
        }
        if (recovery.summaryFinalized()) {
            return new ClaimResult(ClaimStatus.SUMMARY_FINALIZED, slot, null, recovery);
        }
        CoreSatchelData.CoreRecord core = recovery.candidates().stream()
            .filter(candidate -> candidate.coreId().equals(coreId))
            .findFirst()
            .orElse(null);
        if (core == null) {
            return new ClaimResult(ClaimStatus.CANDIDATE_NOT_FOUND, slot, null, recovery);
        }
        if (recovery.discardedCoreIds().contains(coreId)) {
            return new ClaimResult(ClaimStatus.CANDIDATE_NOT_FOUND, slot, core, recovery);
        }
        if (recovery.claimedCoreIds().contains(coreId)) {
            return new ClaimResult(ClaimStatus.ALREADY_CLAIMED, satchel.slotOf(playerId, coreId), core, recovery);
        }

        int existingSlot = satchel.slotOf(playerId, coreId);
        if (existingSlot >= 0) {
            CompletedRecovery healed = recovery.withClaimed(coreId);
            sessions.put(key, healed);
            setDirty();
            return new ClaimResult(ClaimStatus.CLAIMED, existingSlot, core, healed);
        }

        CoreSatchelData.StoreResult stored = satchel.storeAt(playerId, slot, core);
        ClaimStatus status = switch (stored.status()) {
            case STORED -> ClaimStatus.CLAIMED;
            case SATCHEL_NOT_OWNED -> ClaimStatus.SATCHEL_NOT_OWNED;
            case INVALID_SLOT -> ClaimStatus.INVALID_SLOT;
            case SLOT_OCCUPIED -> ClaimStatus.SLOT_OCCUPIED;
            case SATCHEL_FULL -> ClaimStatus.SATCHEL_FULL;
            case DUPLICATE_CORE_ID -> ClaimStatus.ALREADY_CLAIMED;
        };
        if (status != ClaimStatus.CLAIMED) {
            return new ClaimResult(status, slot, core, recovery);
        }

        CompletedRecovery claimed = recovery.withClaimed(coreId);
        sessions.put(key, claimed);
        setDirty();
        return new ClaimResult(ClaimStatus.CLAIMED, slot, core, claimed);
    }

    public ClaimResult claimCandidateFirstAvailable(
        UUID playerId,
        String sessionId,
        int floor,
        UUID coreId,
        CoreSatchelData satchel
    ) {
        if (playerId == null || coreId == null || satchel == null) {
            throw new IllegalArgumentException("Quick Core recovery claim values are required");
        }
        validateSessionId(sessionId);
        String key = recoveryKey(sessionId, floor);
        Map<String, CompletedRecovery> sessions = recoveriesByPlayer.get(playerId);
        CompletedRecovery recovery = sessions == null ? null : sessions.get(key);
        if (recovery == null) {
            return new ClaimResult(ClaimStatus.SESSION_NOT_FOUND, -1, null, CompletedRecovery.missing());
        }
        if (recovery.summaryFinalized()) {
            return new ClaimResult(ClaimStatus.SUMMARY_FINALIZED, -1, null, recovery);
        }
        CoreSatchelData.CoreRecord core = recovery.candidates().stream()
            .filter(candidate -> candidate.coreId().equals(coreId))
            .findFirst()
            .orElse(null);
        if (core == null || recovery.discardedCoreIds().contains(coreId)) {
            return new ClaimResult(ClaimStatus.CANDIDATE_NOT_FOUND, -1, core, recovery);
        }
        if (recovery.claimedCoreIds().contains(coreId)) {
            return new ClaimResult(
                ClaimStatus.ALREADY_CLAIMED,
                satchel.slotOf(playerId, coreId),
                core,
                recovery
            );
        }

        int existingSlot = satchel.slotOf(playerId, coreId);
        if (existingSlot >= 0) {
            CompletedRecovery healed = recovery.withClaimed(coreId);
            sessions.put(key, healed);
            setDirty();
            return new ClaimResult(ClaimStatus.CLAIMED, existingSlot, core, healed);
        }

        CoreSatchelData.StoreResult stored = satchel.storeFirstAvailable(playerId, core);
        ClaimStatus status = switch (stored.status()) {
            case STORED -> ClaimStatus.CLAIMED;
            case SATCHEL_NOT_OWNED -> ClaimStatus.SATCHEL_NOT_OWNED;
            case SATCHEL_FULL -> ClaimStatus.SATCHEL_FULL;
            case DUPLICATE_CORE_ID -> ClaimStatus.ALREADY_CLAIMED;
            case INVALID_SLOT -> ClaimStatus.INVALID_SLOT;
            case SLOT_OCCUPIED -> ClaimStatus.SLOT_OCCUPIED;
        };
        if (status != ClaimStatus.CLAIMED) {
            return new ClaimResult(status, stored.slot(), core, recovery);
        }

        CompletedRecovery claimed = recovery.withClaimed(coreId);
        sessions.put(key, claimed);
        setDirty();
        return new ClaimResult(ClaimStatus.CLAIMED, stored.slot(), core, claimed);
    }

    public ClaimResult replaceCandidate(
        UUID playerId,
        String sessionId,
        int floor,
        UUID coreId,
        int slot,
        CoreSatchelData satchel
    ) {
        if (playerId == null || coreId == null || satchel == null) {
            throw new IllegalArgumentException("Core recovery replacement values are required");
        }
        validateSessionId(sessionId);
        String key = recoveryKey(sessionId, floor);
        Map<String, CompletedRecovery> sessions = recoveriesByPlayer.get(playerId);
        CompletedRecovery recovery = sessions == null ? null : sessions.get(key);
        if (recovery == null) {
            return new ClaimResult(ClaimStatus.SESSION_NOT_FOUND, slot, null, CompletedRecovery.missing());
        }
        if (recovery.summaryFinalized()) {
            return new ClaimResult(ClaimStatus.SUMMARY_FINALIZED, slot, null, recovery);
        }
        CoreSatchelData.CoreRecord incoming = recovery.candidates().stream()
            .filter(candidate -> candidate.coreId().equals(coreId))
            .findFirst()
            .orElse(null);
        if (incoming == null) {
            return new ClaimResult(ClaimStatus.CANDIDATE_NOT_FOUND, slot, null, recovery);
        }
        if (recovery.discardedCoreIds().contains(coreId)) {
            return new ClaimResult(ClaimStatus.CANDIDATE_NOT_FOUND, slot, incoming, recovery);
        }
        if (recovery.claimedCoreIds().contains(coreId) || satchel.slotOf(playerId, coreId) >= 0) {
            return new ClaimResult(
                ClaimStatus.ALREADY_CLAIMED,
                satchel.slotOf(playerId, coreId),
                incoming,
                recovery
            );
        }

        CoreSatchelData.CoreRecord displaced = satchel.coreAt(playerId, slot).orElse(null);
        if (displaced == null) {
            return new ClaimResult(ClaimStatus.SLOT_EMPTY, slot, incoming, recovery);
        }
        CompletedRecovery replacedRecovery = recovery.withReplacement(coreId, displaced);
        CoreSatchelData.ReplaceResult replacement = satchel.replaceAt(playerId, slot, incoming);
        ClaimStatus status = switch (replacement.status()) {
            case REPLACED -> ClaimStatus.REPLACED;
            case SATCHEL_NOT_OWNED -> ClaimStatus.SATCHEL_NOT_OWNED;
            case DUPLICATE_CORE_ID -> ClaimStatus.ALREADY_CLAIMED;
            case INVALID_SLOT -> ClaimStatus.INVALID_SLOT;
            case SLOT_EMPTY -> ClaimStatus.SLOT_EMPTY;
        };
        if (status != ClaimStatus.REPLACED) {
            return new ClaimResult(status, slot, incoming, recovery);
        }

        sessions.put(key, replacedRecovery);
        setDirty();
        return new ClaimResult(ClaimStatus.REPLACED, slot, incoming, replacedRecovery);
    }

    public DepartureFinalizeResult finalizeForDeparture(
        UUID playerId,
        String sessionId,
        int floor
    ) {
        if (playerId == null) throw new IllegalArgumentException("Recovery player UUID is required");
        validateSessionId(sessionId);
        String key = recoveryKey(sessionId, floor);
        Map<String, CompletedRecovery> sessions = recoveriesByPlayer.get(playerId);
        CompletedRecovery recovery = sessions == null ? null : sessions.get(key);
        if (recovery == null) {
            return new DepartureFinalizeResult(
                DepartureFinalizeStatus.SESSION_NOT_FOUND,
                0,
                CompletedRecovery.missing()
            );
        }
        if (recovery.summaryFinalized()) {
            return new DepartureFinalizeResult(
                DepartureFinalizeStatus.ALREADY_FINALIZED,
                recovery.discardedCandidateCount(),
                recovery
            );
        }

        CompletedRecovery finalized = recovery.withSummaryFinalized();
        sessions.put(key, finalized);
        setDirty();
        return new DepartureFinalizeResult(
            DepartureFinalizeStatus.FINALIZED,
            finalized.discardedCandidateCount(),
            finalized
        );
    }

    public RecordEncounterResult recordEncounter(
        UUID playerId,
        String sessionId,
        int floor,
        long runSeed,
        EncounterReceipt receipt
    ) {
        if (playerId == null) throw new IllegalArgumentException("Recovery player UUID is required");
        validateSessionId(sessionId);
        MonsterCoreRecoveryPolicy.currentTier(floor);
        if (receipt == null) throw new IllegalArgumentException("Recovery encounter receipt is required");
        if (receipt.floor() != floor) {
            throw new IllegalArgumentException("Recovery receipt floor does not match its active session");
        }
        String key = recoveryKey(sessionId, floor);
        Map<String, CompletedRecovery> completed = recoveriesByPlayer.get(playerId);
        if (completed != null && completed.containsKey(key)) {
            return new RecordEncounterResult(RecordEncounterStatus.SESSION_FINALIZED, null);
        }

        Map<String, ActiveRecovery> activeSessions = activeRecoveriesByPlayer.computeIfAbsent(
            playerId, ignored -> new HashMap<>()
        );
        ActiveRecovery active = activeSessions.get(key);
        if (active == null) {
            active = new ActiveRecovery(sessionId, floor, runSeed, List.of());
        } else if (active.runSeed() != runSeed) {
            throw new IllegalStateException("Active Core recovery run seed changed for " + sessionId + "/" + floor);
        }
        if (active.receipts().stream().anyMatch(existing ->
            existing.encounterKey().equals(receipt.encounterKey())
        )) {
            return new RecordEncounterResult(RecordEncounterStatus.DUPLICATE, active);
        }
        List<EncounterReceipt> receipts = new ArrayList<>(active.receipts());
        receipts.add(receipt);
        ActiveRecovery updated = new ActiveRecovery(sessionId, floor, runSeed, receipts);
        activeSessions.put(key, updated);
        setDirty();
        return new RecordEncounterResult(RecordEncounterStatus.RECORDED, updated);
    }

    public LiveFinalizeResult finalizeActiveSuccessfulRun(
        UUID playerId,
        String sessionId,
        int floor,
        long runSeed,
        boolean firstTimeFloorCompletion
    ) {
        validateSessionId(sessionId);
        String key = recoveryKey(sessionId, floor);
        ActiveRecovery active = activeRecoveriesByPlayer
            .getOrDefault(playerId, Map.of())
            .getOrDefault(key, new ActiveRecovery(sessionId, floor, runSeed, List.of()));
        if (active.runSeed() != runSeed) {
            throw new IllegalStateException("Active Core recovery run seed changed before finalization");
        }

        List<EncounterInput> eligible = new ArrayList<>();
        int skipped = 0;
        for (EncounterReceipt receipt : active.receipts()) {
            switch (receipt.kind()) {
                case ORDINARY_COMBAT -> eligible.add(new EncounterInput(
                    receipt.encounterKey(),
                    MonsterCoreRecoveryPolicy.EncounterSource.ORDINARY_COMBAT,
                    false
                ));
                case ELITE -> eligible.add(new EncounterInput(
                    receipt.encounterKey(),
                    MonsterCoreRecoveryPolicy.EncounterSource.ELITE,
                    false
                ));
                case REGULAR_FLOOR_BOSS -> eligible.add(new EncounterInput(
                    receipt.encounterKey(),
                    MonsterCoreRecoveryPolicy.EncounterSource.REGULAR_FLOOR_BOSS,
                    false
                ));
                case MILESTONE_BOSS -> {
                    if (firstTimeFloorCompletion) {
                        eligible.add(new EncounterInput(
                            receipt.encounterKey(),
                            MonsterCoreRecoveryPolicy.EncounterSource.REGULAR_FLOOR_BOSS,
                            true
                        ));
                    } else {
                        skipped++;
                    }
                }
            }
        }

        FinalizeResult finalized = finalizeSuccessfulRun(playerId, sessionId, floor, runSeed, eligible);
        Map<String, ActiveRecovery> activeSessions = activeRecoveriesByPlayer.get(playerId);
        if (activeSessions != null) {
            activeSessions.remove(key);
            if (activeSessions.isEmpty()) activeRecoveriesByPlayer.remove(playerId);
            setDirty();
        }
        return new LiveFinalizeResult(finalized.status(), finalized.recovery(), active.receipts().size(), skipped);
    }

    public int discardActiveSession(String sessionId, int floor) {
        if (sessionId == null || sessionId.isBlank()) return 0;
        String key = recoveryKey(sessionId, floor);
        int removed = 0;
        List<UUID> emptyPlayers = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, ActiveRecovery>> entry : activeRecoveriesByPlayer.entrySet()) {
            if (entry.getValue().remove(key) != null) removed++;
            if (entry.getValue().isEmpty()) emptyPlayers.add(entry.getKey());
        }
        emptyPlayers.forEach(activeRecoveriesByPlayer::remove);
        if (removed > 0) setDirty();
        return removed;
    }

    public ActiveRecovery activeSnapshot(UUID playerId, String sessionId, int floor) {
        return activeRecoveriesByPlayer.getOrDefault(playerId, Map.of())
            .getOrDefault(recoveryKey(sessionId, floor), ActiveRecovery.missing());
    }

    private static void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 96) {
            throw new IllegalArgumentException("Recovery session ID must contain 1-96 characters");
        }
    }

    private static String recoveryKey(String sessionId, int floor) {
        return sessionId + "\u0000" + floor;
    }

    public enum FinalizeStatus {
        FINALIZED,
        ALREADY_FINALIZED
    }

    public record FinalizeResult(FinalizeStatus status, CompletedRecovery recovery) {}

    public enum RecordEncounterStatus {
        RECORDED,
        DUPLICATE,
        SESSION_FINALIZED
    }

    public record RecordEncounterResult(RecordEncounterStatus status, ActiveRecovery active) {}

    public record LiveFinalizeResult(
        FinalizeStatus status,
        CompletedRecovery recovery,
        int recordedReceipts,
        int skippedUnresolvedReceipts
    ) {}

    public enum TestCandidateStatus {
        INJECTED,
        ALREADY_PENDING,
        SESSION_NOT_FOUND,
        NO_TIER_ONE_RESULT,
        SUMMARY_FINALIZED
    }

    public record TestCandidateResult(
        TestCandidateStatus status,
        CoreSatchelData.CoreRecord core,
        CompletedRecovery recovery
    ) {}

    public enum ClaimStatus {
        CLAIMED,
        SESSION_NOT_FOUND,
        CANDIDATE_NOT_FOUND,
        ALREADY_CLAIMED,
        SATCHEL_NOT_OWNED,
        SATCHEL_FULL,
        INVALID_SLOT,
        SLOT_OCCUPIED,
        SUMMARY_FINALIZED,
        REPLACED,
        SLOT_EMPTY
    }

    public record ClaimResult(
        ClaimStatus status,
        int slot,
        CoreSatchelData.CoreRecord core,
        CompletedRecovery recovery
    ) {
        public boolean claimed() {
            return status == ClaimStatus.CLAIMED || status == ClaimStatus.REPLACED;
        }
    }

    public enum DepartureFinalizeStatus {
        FINALIZED,
        ALREADY_FINALIZED,
        SESSION_NOT_FOUND
    }

    public record DepartureFinalizeResult(
        DepartureFinalizeStatus status,
        int discardedCandidates,
        CompletedRecovery recovery
    ) {}

    public enum ReceiptKind {
        ORDINARY_COMBAT,
        ELITE,
        REGULAR_FLOOR_BOSS,
        MILESTONE_BOSS
    }

    public record EncounterReceipt(
        String encounterKey,
        int floor,
        String roomProfile,
        String objective,
        ReceiptKind kind
    ) {
        public EncounterReceipt {
            if (encounterKey == null || encounterKey.isBlank() || encounterKey.length() > 128) {
                throw new IllegalArgumentException("Recovery encounter key must contain 1-128 characters");
            }
            MonsterCoreRecoveryPolicy.currentTier(floor);
            roomProfile = bounded(roomProfile, 48, "UNKNOWN_PROFILE");
            objective = bounded(objective, 48, "UNKNOWN_OBJECTIVE");
            if (kind == null) throw new IllegalArgumentException("Recovery receipt kind is required");
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("EncounterKey", encounterKey);
            tag.putInt("Floor", floor);
            tag.putString("RoomProfile", roomProfile);
            tag.putString("Objective", objective);
            tag.putString("Kind", kind.name());
            return tag;
        }

        private static EncounterReceipt load(CompoundTag tag) {
            try {
                return new EncounterReceipt(
                    tag.getString("EncounterKey"),
                    tag.getInt("Floor"),
                    tag.getString("RoomProfile"),
                    tag.getString("Objective"),
                    ReceiptKind.valueOf(tag.getString("Kind"))
                );
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid active Core recovery receipt", exception);
            }
        }
    }

    public record ActiveRecovery(
        String sessionId,
        int floor,
        long runSeed,
        List<EncounterReceipt> receipts
    ) {
        public ActiveRecovery {
            if (sessionId == null) {
                throw new IllegalArgumentException("Active recovery session ID is required");
            }
            if (!sessionId.isBlank()) {
                validateSessionId(sessionId);
                MonsterCoreRecoveryPolicy.currentTier(floor);
            }
            receipts = receipts == null ? List.of() : List.copyOf(receipts);
            Set<String> keys = new HashSet<>();
            for (EncounterReceipt receipt : receipts) {
                if (receipt.floor() != floor) {
                    throw new IllegalArgumentException("Active recovery receipt floor mismatch");
                }
                if (!keys.add(receipt.encounterKey())) {
                    throw new IllegalArgumentException(
                        "Duplicate active recovery encounter: " + receipt.encounterKey()
                    );
                }
            }
        }

        public boolean available() {
            return !sessionId.isBlank();
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("SessionId", sessionId);
            tag.putInt("Floor", floor);
            tag.putLong("RunSeed", runSeed);
            ListTag receiptTags = new ListTag();
            receipts.forEach(receipt -> receiptTags.add(receipt.save()));
            tag.put("Receipts", receiptTags);
            return tag;
        }

        private static ActiveRecovery load(CompoundTag tag) {
            List<EncounterReceipt> receipts = new ArrayList<>();
            ListTag receiptTags = tag.getList("Receipts", Tag.TAG_COMPOUND);
            for (int index = 0; index < receiptTags.size(); index++) {
                receipts.add(EncounterReceipt.load(receiptTags.getCompound(index)));
            }
            return new ActiveRecovery(
                tag.getString("SessionId"),
                tag.getInt("Floor"),
                tag.getLong("RunSeed"),
                receipts
            );
        }

        static ActiveRecovery missing() {
            return new ActiveRecovery("", 0, 0L, List.of());
        }
    }

    private static String bounded(String value, int maximumLength, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    public record EncounterInput(
        String encounterKey,
        MonsterCoreRecoveryPolicy.EncounterSource source,
        boolean firstClearMilestone
    ) {
        public EncounterInput {
            if (encounterKey == null || encounterKey.isBlank() || encounterKey.length() > 128) {
                throw new IllegalArgumentException("Recovery encounter key must contain 1-128 characters");
            }
            if (source == null) throw new IllegalArgumentException("Recovery encounter source is required");
        }
    }

    public record RecoveryAttempt(
        EncounterInput encounter,
        MonsterCoreRecoveryPolicy.RecoveryRoll roll
    ) {
        public RecoveryAttempt {
            if (encounter == null || roll == null) {
                throw new IllegalArgumentException("Recovery attempt values are required");
            }
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("EncounterKey", encounter.encounterKey());
            tag.putString("Source", encounter.source().id());
            tag.putBoolean("FirstClearMilestone", encounter.firstClearMilestone());
            tag.putBoolean("Recovered", roll.recovered());
            tag.putBoolean("Guaranteed", roll.guaranteed());
            tag.putInt("RecoveryRollBasisPoints", roll.recoveryRollBasisPoints());
            tag.putInt("TierSelectionRollBasisPoints", roll.tierSelectionRollBasisPoints());
            tag.putInt("ChargeBandRollBasisPoints", roll.chargeBandRollBasisPoints());
            tag.putInt("NaturalChargeBasisPoints", roll.naturalChargeBasisPoints());
            if (roll.recovered()) {
                CoreSatchelData.CoreRecord core = roll.core();
                tag.putUUID("CoreId", core.coreId());
                tag.putInt("Tier", core.tier());
                tag.putLong("TotalCharge", core.totalCharge());
                tag.putLong("Capacity", core.capacity());
                tag.putLong("NaturalCharge", core.naturalCharge());
                tag.putString("Family", core.familyId());
                tag.putString("Technique", core.techniqueId());
            }
            return tag;
        }

        private static RecoveryAttempt load(CompoundTag tag) {
            MonsterCoreRecoveryPolicy.EncounterSource source =
                MonsterCoreRecoveryPolicy.EncounterSource.parse(tag.getString("Source"))
                    .orElseThrow(() -> new IllegalStateException(
                        "Unsupported Core recovery source in save: " + tag.getString("Source")
                    ));
            EncounterInput encounter = new EncounterInput(
                tag.getString("EncounterKey"),
                source,
                tag.getBoolean("FirstClearMilestone")
            );
            boolean recovered = tag.getBoolean("Recovered");
            MonsterCoreRecoveryPolicy.RecoveryRoll roll;
            if (recovered) {
                if (!tag.hasUUID("CoreId")) {
                    throw new IllegalStateException("Recovered Core candidate is missing its unique identity");
                }
                String family = tag.getString("Family");
                String technique = tag.getString("Technique");
                CoreSatchelData.CoreRecord core = family.isBlank() || technique.isBlank()
                    ? new CoreSatchelData.CoreRecord(
                        tag.getUUID("CoreId"),
                        tag.getInt("Tier"),
                        tag.getLong("TotalCharge"),
                        tag.getLong("Capacity"),
                        tag.getLong("NaturalCharge"),
                        MonsterCoreTechniqueCatalog.forEncounter(
                            encounter.encounterKey(), encounter.source()
                        ).familyId(),
                        MonsterCoreTechniqueCatalog.forEncounter(
                            encounter.encounterKey(), encounter.source()
                        ).techniqueId()
                    )
                    : new CoreSatchelData.CoreRecord(
                        tag.getUUID("CoreId"),
                        tag.getInt("Tier"),
                        tag.getLong("TotalCharge"),
                        tag.getLong("Capacity"),
                        tag.getLong("NaturalCharge"),
                        family,
                        technique
                    );
                roll = new MonsterCoreRecoveryPolicy.RecoveryRoll(
                    true,
                    tag.getBoolean("Guaranteed"),
                    tag.getInt("RecoveryRollBasisPoints"),
                    tag.getInt("TierSelectionRollBasisPoints"),
                    tag.getInt("ChargeBandRollBasisPoints"),
                    tag.getInt("NaturalChargeBasisPoints"),
                    core
                );
            } else {
                roll = new MonsterCoreRecoveryPolicy.RecoveryRoll(
                    false,
                    false,
                    tag.getInt("RecoveryRollBasisPoints"),
                    -1,
                    -1,
                    -1,
                    null
                );
            }
            return new RecoveryAttempt(encounter, roll);
        }
    }

    public record CompletedRecovery(
        String sessionId,
        int floor,
        long runSeed,
        List<RecoveryAttempt> attempts,
        List<CoreSatchelData.CoreRecord> returnedCandidates,
        Set<UUID> claimedCoreIds,
        Set<UUID> discardedCoreIds,
        boolean summaryFinalized
    ) {
        public CompletedRecovery(String sessionId, int floor, long runSeed, List<RecoveryAttempt> attempts) {
            this(sessionId, floor, runSeed, attempts, List.of(), Set.of(), Set.of(), false);
        }

        public CompletedRecovery(
            String sessionId,
            int floor,
            long runSeed,
            List<RecoveryAttempt> attempts,
            Set<UUID> claimedCoreIds
        ) {
            this(sessionId, floor, runSeed, attempts, List.of(), claimedCoreIds, Set.of(), false);
        }

        public CompletedRecovery(
            String sessionId,
            int floor,
            long runSeed,
            List<RecoveryAttempt> attempts,
            Set<UUID> claimedCoreIds,
            boolean summaryFinalized
        ) {
            this(
                sessionId, floor, runSeed, attempts, List.of(), claimedCoreIds, Set.of(), summaryFinalized
            );
        }

        public CompletedRecovery(
            String sessionId,
            int floor,
            long runSeed,
            List<RecoveryAttempt> attempts,
            List<CoreSatchelData.CoreRecord> returnedCandidates,
            Set<UUID> claimedCoreIds,
            boolean summaryFinalized
        ) {
            this(
                sessionId,
                floor,
                runSeed,
                attempts,
                returnedCandidates,
                claimedCoreIds,
                returnedCandidates == null
                    ? Set.of()
                    : returnedCandidates.stream()
                        .map(CoreSatchelData.CoreRecord::coreId)
                        .filter(coreId -> claimedCoreIds == null || !claimedCoreIds.contains(coreId))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                summaryFinalized
            );
        }

        public CompletedRecovery {
            if (sessionId == null) {
                throw new IllegalArgumentException("Completed recovery session ID is required");
            }
            if (!sessionId.isBlank()) {
                validateSessionId(sessionId);
                MonsterCoreRecoveryPolicy.currentTier(floor);
            }
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
            returnedCandidates = returnedCandidates == null ? List.of() : List.copyOf(returnedCandidates);
            claimedCoreIds = claimedCoreIds == null ? Set.of() : Set.copyOf(claimedCoreIds);
            discardedCoreIds = discardedCoreIds == null ? Set.of() : Set.copyOf(discardedCoreIds);
            Set<String> encounterKeys = new HashSet<>();
            Set<UUID> coreIds = new HashSet<>();
            for (RecoveryAttempt attempt : attempts) {
                if (attempt == null) {
                    throw new IllegalArgumentException("Completed recovery attempt cannot be null");
                }
                if (!encounterKeys.add(attempt.encounter().encounterKey())) {
                    throw new IllegalArgumentException(
                        "Duplicate completed recovery encounter: " + attempt.encounter().encounterKey()
                    );
                }
                if (attempt.roll().recovered() && !coreIds.add(attempt.roll().core().coreId())) {
                    throw new IllegalArgumentException(
                        "Duplicate completed recovery Core identity: " + attempt.roll().core().coreId()
                    );
                }
                if (!sessionId.isBlank()) {
                    MonsterCoreRecoveryPolicy.RecoveryRoll expected = MonsterCoreRecoveryPolicy.roll(
                        new MonsterCoreRecoveryPolicy.RecoveryRequest(
                            attempt.encounter().encounterKey(),
                            floor,
                            attempt.encounter().source(),
                            attempt.encounter().firstClearMilestone(),
                            runSeed
                        )
                    );
                    if (!expected.equals(attempt.roll())) {
                        throw new IllegalArgumentException(
                            "Completed recovery roll does not match its deterministic provenance: "
                                + attempt.encounter().encounterKey()
                        );
                    }
                }
            }
            for (CoreSatchelData.CoreRecord returned : returnedCandidates) {
                if (returned == null) {
                    throw new IllegalArgumentException("Returned recovery Core cannot be null");
                }
                if (!coreIds.add(returned.coreId())) {
                    throw new IllegalArgumentException(
                        "Duplicate returned recovery Core identity: " + returned.coreId()
                    );
                }
            }
            if (!coreIds.containsAll(claimedCoreIds)) {
                throw new IllegalArgumentException("Claimed Core identity is not a recovered candidate");
            }
            if (!coreIds.containsAll(discardedCoreIds)) {
                throw new IllegalArgumentException("Discarded Core identity is not a recovered candidate");
            }
            if (claimedCoreIds.stream().anyMatch(discardedCoreIds::contains)) {
                throw new IllegalArgumentException("A recovered Core cannot be both claimed and discarded");
            }
        }

        public boolean available() {
            return !sessionId.isBlank();
        }

        public List<CoreSatchelData.CoreRecord> candidates() {
            LinkedHashMap<UUID, CoreSatchelData.CoreRecord> candidates = new LinkedHashMap<>();
            attempts.stream()
                .filter(attempt -> attempt.roll().recovered())
                .map(attempt -> attempt.roll().core())
                .forEach(core -> candidates.put(core.coreId(), core));
            returnedCandidates.forEach(core -> candidates.put(core.coreId(), core));
            return List.copyOf(candidates.values());
        }

        public List<CoreSatchelData.CoreRecord> pendingCandidates() {
            if (summaryFinalized) return List.of();
            return candidates().stream()
                .filter(core -> !claimedCoreIds.contains(core.coreId()))
                .filter(core -> !discardedCoreIds.contains(core.coreId()))
                .toList();
        }

        public int discardedCandidateCount() {
            return summaryFinalized
                ? candidates().size() - claimedCoreIds.size()
                : discardedCoreIds.size();
        }

        private CompletedRecovery withClaimed(UUID coreId) {
            if (claimedCoreIds.contains(coreId)) return this;
            Set<UUID> claimed = new HashSet<>(claimedCoreIds);
            claimed.add(coreId);
            return new CompletedRecovery(
                sessionId,
                floor,
                runSeed,
                attempts,
                returnedCandidates,
                claimed,
                discardedCoreIds,
                summaryFinalized
            );
        }

        private CompletedRecovery withReplacement(
            UUID incomingCoreId,
            CoreSatchelData.CoreRecord displaced
        ) {
            Set<UUID> claimed = new HashSet<>(claimedCoreIds);
            claimed.add(incomingCoreId);
            Set<UUID> discarded = new HashSet<>(discardedCoreIds);
            boolean alreadyCandidate = candidates().stream()
                .anyMatch(candidate -> candidate.coreId().equals(displaced.coreId()));
            if (alreadyCandidate) {
                claimed.remove(displaced.coreId());
                discarded.add(displaced.coreId());
            }
            return new CompletedRecovery(
                sessionId,
                floor,
                runSeed,
                attempts,
                returnedCandidates,
                claimed,
                discarded,
                summaryFinalized
            );
        }

        private CompletedRecovery withSummaryFinalized() {
            return summaryFinalized
                ? this
                : new CompletedRecovery(
                    sessionId,
                    floor,
                    runSeed,
                    attempts,
                    returnedCandidates,
                    claimedCoreIds,
                    discardedCoreIds,
                    true
                );
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("SessionId", sessionId);
            tag.putInt("Floor", floor);
            tag.putLong("RunSeed", runSeed);
            ListTag attemptTags = new ListTag();
            attempts.forEach(attempt -> attemptTags.add(attempt.save()));
            tag.put("Attempts", attemptTags);
            ListTag returnedTags = new ListTag();
            returnedCandidates.forEach(core -> returnedTags.add(saveCore(core)));
            tag.put("ReturnedCandidates", returnedTags);
            ListTag claimedTags = new ListTag();
            claimedCoreIds.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(coreId -> {
                    CompoundTag claimedTag = new CompoundTag();
                    claimedTag.putUUID("CoreId", coreId);
                    claimedTags.add(claimedTag);
                });
            tag.put("ClaimedCores", claimedTags);
            ListTag discardedTags = new ListTag();
            discardedCoreIds.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(coreId -> {
                    CompoundTag discardedTag = new CompoundTag();
                    discardedTag.putUUID("CoreId", coreId);
                    discardedTags.add(discardedTag);
                });
            tag.put("DiscardedCores", discardedTags);
            tag.putBoolean("SummaryFinalized", summaryFinalized);
            return tag;
        }

        private static CompletedRecovery load(CompoundTag tag) {
            List<RecoveryAttempt> attempts = new ArrayList<>();
            ListTag attemptTags = tag.getList("Attempts", Tag.TAG_COMPOUND);
            for (int index = 0; index < attemptTags.size(); index++) {
                attempts.add(RecoveryAttempt.load(attemptTags.getCompound(index)));
            }
            Set<UUID> claimedCoreIds = new HashSet<>();
            List<CoreSatchelData.CoreRecord> returnedCandidates = new ArrayList<>();
            ListTag returnedTags = tag.getList("ReturnedCandidates", Tag.TAG_COMPOUND);
            for (int index = 0; index < returnedTags.size(); index++) {
                returnedCandidates.add(loadCore(returnedTags.getCompound(index)));
            }
            ListTag claimedTags = tag.getList("ClaimedCores", Tag.TAG_COMPOUND);
            for (int index = 0; index < claimedTags.size(); index++) {
                CompoundTag claimedTag = claimedTags.getCompound(index);
                if (!claimedTag.hasUUID("CoreId")) {
                    throw new IllegalStateException("Claimed Core recovery record is missing its identity");
                }
                if (!claimedCoreIds.add(claimedTag.getUUID("CoreId"))) {
                    throw new IllegalStateException("Duplicate claimed Core identity in recovery save");
                }
            }
            Set<UUID> discardedCoreIds = new HashSet<>();
            if (tag.contains("DiscardedCores", Tag.TAG_LIST)) {
                ListTag discardedTags = tag.getList("DiscardedCores", Tag.TAG_COMPOUND);
                for (int index = 0; index < discardedTags.size(); index++) {
                    CompoundTag discardedTag = discardedTags.getCompound(index);
                    if (!discardedTag.hasUUID("CoreId")) {
                        throw new IllegalStateException(
                            "Discarded Core recovery record is missing its identity"
                        );
                    }
                    if (!discardedCoreIds.add(discardedTag.getUUID("CoreId"))) {
                        throw new IllegalStateException(
                            "Duplicate discarded Core identity in recovery save"
                        );
                    }
                }
            } else {
                returnedCandidates.stream()
                    .map(CoreSatchelData.CoreRecord::coreId)
                    .filter(coreId -> !claimedCoreIds.contains(coreId))
                    .forEach(discardedCoreIds::add);
            }
            return new CompletedRecovery(
                tag.getString("SessionId"),
                tag.getInt("Floor"),
                tag.getLong("RunSeed"),
                attempts,
                returnedCandidates,
                claimedCoreIds,
                discardedCoreIds,
                tag.getBoolean("SummaryFinalized")
            );
        }

        static CompletedRecovery missing() {
            return new CompletedRecovery(
                "", 0, 0L, List.of(), List.of(), Set.of(), Set.of(), false
            );
        }
    }

    private static CompoundTag saveCore(CoreSatchelData.CoreRecord core) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("CoreId", core.coreId());
        tag.putInt("Tier", core.tier());
        tag.putLong("TotalCharge", core.totalCharge());
        tag.putLong("Capacity", core.capacity());
        tag.putLong("NaturalCharge", core.naturalCharge());
        tag.putString("Family", core.familyId());
        tag.putString("Technique", core.techniqueId());
        return tag;
    }

    private static CoreSatchelData.CoreRecord loadCore(CompoundTag tag) {
        if (!tag.hasUUID("CoreId")) {
            throw new IllegalStateException("Returned recovery Core is missing its identity");
        }
        String family = tag.getString("Family");
        String technique = tag.getString("Technique");
        return family.isBlank() || technique.isBlank()
            ? new CoreSatchelData.CoreRecord(
                tag.getUUID("CoreId"),
                tag.getInt("Tier"),
                tag.getLong("TotalCharge"),
                tag.getLong("Capacity"),
                tag.getLong("NaturalCharge")
            )
            : new CoreSatchelData.CoreRecord(
                tag.getUUID("CoreId"),
                tag.getInt("Tier"),
                tag.getLong("TotalCharge"),
                tag.getLong("Capacity"),
                tag.getLong("NaturalCharge"),
                family,
                technique
            );
    }
}
