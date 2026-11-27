package com.mineclawd.tensuradungeon.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonBossResolver;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonBossResolution;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinCatalog;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinSelection;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinSelector;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class DungeonFloorProgressData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_floor_progress";
    private static final int MAX_RECENT_RUNS_PER_PLAYER = 64;
    private static final int MAX_ENEMY_KINDS_PER_RUN = 128;
    private static final double MAX_RECORDED_DAMAGE = 9_000_000_000_000_000.0D;
    private final Map<UUID, PlayerFloorProgress> progressByPlayer = new HashMap<>();
    private final Map<UUID, Map<Integer, PreparedFloorModifierPlan>> preparedPlansByPlayer = new HashMap<>();
    private final Map<UUID, List<CompletedRunSummary>> completedRunsByPlayer = new HashMap<>();
    private final Map<UUID, Map<Integer, LocalFloorRecord>> bestRecordsByPlayer = new HashMap<>();

    public static DungeonFloorProgressData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(DungeonFloorProgressData::new, DungeonFloorProgressData::load), DATA_NAME);
    }

    public static DungeonFloorProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonFloorProgressData data = new DungeonFloorProgressData();
        ListTag playersTag = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playersTag.size(); i++) {
            CompoundTag playerTag = playersTag.getCompound(i);
            if (!playerTag.hasUUID("Player")) {
                continue;
            }
            UUID playerId = playerTag.getUUID("Player");
            Set<Integer> completedFloors = new LinkedHashSet<>();
            for (int floor : playerTag.getIntArray("CompletedFloors")) {
                if (floor > 0) {
                    completedFloors.add(floor);
                }
            }
            data.progressByPlayer.put(playerId, new PlayerFloorProgress(
                Math.max(1, playerTag.contains("HighestUnlockedFloor") ? playerTag.getInt("HighestUnlockedFloor") : 1),
                completedFloors,
                Math.max(0, playerTag.contains("SelectedFloor") ? playerTag.getInt("SelectedFloor") : 0),
                Math.max(0, playerTag.contains("LastStartedFloor") ? playerTag.getInt("LastStartedFloor") : 0),
                Math.max(0, playerTag.contains("LastCompletedFloor") ? playerTag.getInt("LastCompletedFloor") : 0),
                playerTag.contains("LastCompletionGameTime") ? playerTag.getLong("LastCompletionGameTime") : 0L,
                playerTag.contains("ActiveRunStart", Tag.TAG_COMPOUND)
                    ? RunStartTelemetry.load(playerTag.getCompound("ActiveRunStart"))
                    : RunStartTelemetry.none()
            ));
            Map<Integer, PreparedFloorModifierPlan> preparedPlans = new HashMap<>();
            ListTag preparedPlanTags = playerTag.getList("PreparedModifierPlans", Tag.TAG_COMPOUND);
            for (int planIndex = 0; planIndex < preparedPlanTags.size(); planIndex++) {
                PreparedFloorModifierPlan plan =
                    PreparedFloorModifierPlan.load(preparedPlanTags.getCompound(planIndex));
                if (plan.floor() > 0) preparedPlans.put(plan.floor(), plan);
            }
            if (!preparedPlans.isEmpty()) data.preparedPlansByPlayer.put(playerId, preparedPlans);
            List<CompletedRunSummary> completedRuns = new ArrayList<>();
            ListTag completedRunTags = playerTag.getList("CompletedRuns", Tag.TAG_COMPOUND);
            int firstRetained = Math.max(0, completedRunTags.size() - MAX_RECENT_RUNS_PER_PLAYER);
            for (int runIndex = firstRetained; runIndex < completedRunTags.size(); runIndex++) {
                CompletedRunSummary summary = CompletedRunSummary.load(
                    playerId, completedRunTags.getCompound(runIndex)
                );
                if (summary.context().floor() > 0) completedRuns.add(summary);
            }
            if (!completedRuns.isEmpty()) data.completedRunsByPlayer.put(playerId, completedRuns);

            Map<Integer, LocalFloorRecord> bestRecords = new HashMap<>();
            ListTag recordTags = playerTag.getList("BestFloorRecords", Tag.TAG_COMPOUND);
            for (int recordIndex = 0; recordIndex < recordTags.size(); recordIndex++) {
                LocalFloorRecord record = LocalFloorRecord.load(playerId, recordTags.getCompound(recordIndex));
                if (record.floor() > 0 && record.clearTicks() > 0L) {
                    bestRecords.merge(record.floor(), record, DungeonFloorProgressData::fasterRecord);
                }
            }
            if (!bestRecords.isEmpty()) data.bestRecordsByPlayer.put(playerId, bestRecords);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, PlayerFloorProgress> entry : progressByPlayer.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", entry.getKey());
            PlayerFloorProgress progress = entry.getValue();
            playerTag.putInt("HighestUnlockedFloor", Math.max(1, progress.highestUnlockedFloor()));
            playerTag.putIntArray("CompletedFloors", progress.completedFloors().stream().mapToInt(Integer::intValue).sorted().toArray());
            if (progress.selectedFloor() > 0) {
                playerTag.putInt("SelectedFloor", progress.selectedFloor());
            }
            playerTag.putInt("LastStartedFloor", Math.max(0, progress.lastStartedFloor()));
            playerTag.putInt("LastCompletedFloor", Math.max(0, progress.lastCompletedFloor()));
            playerTag.putLong("LastCompletionGameTime", progress.lastCompletionGameTime());
            if (progress.activeRunStart().available()) {
                playerTag.put("ActiveRunStart", progress.activeRunStart().save());
            }
            Map<Integer, PreparedFloorModifierPlan> preparedPlans = preparedPlansByPlayer.get(entry.getKey());
            if (preparedPlans != null && !preparedPlans.isEmpty()) {
                ListTag preparedPlanTags = new ListTag();
                preparedPlans.values().stream()
                    .sorted(java.util.Comparator.comparingInt(PreparedFloorModifierPlan::floor))
                    .forEach(plan -> preparedPlanTags.add(plan.save()));
                playerTag.put("PreparedModifierPlans", preparedPlanTags);
            }
            List<CompletedRunSummary> completedRuns = completedRunsByPlayer.get(entry.getKey());
            if (completedRuns != null && !completedRuns.isEmpty()) {
                ListTag completedRunTags = new ListTag();
                completedRuns.forEach(summary -> completedRunTags.add(summary.save()));
                playerTag.put("CompletedRuns", completedRunTags);
            }
            Map<Integer, LocalFloorRecord> bestRecords = bestRecordsByPlayer.get(entry.getKey());
            if (bestRecords != null && !bestRecords.isEmpty()) {
                ListTag recordTags = new ListTag();
                bestRecords.values().stream()
                    .sorted(java.util.Comparator.comparingInt(LocalFloorRecord::floor))
                    .forEach(record -> recordTags.add(record.save()));
                playerTag.put("BestFloorRecords", recordTags);
            }
            playersTag.add(playerTag);
        }
        tag.put("Players", playersTag);
        return tag;
    }

    public PlayerFloorProgress getOrCreate(UUID playerId) {
        return progressByPlayer.computeIfAbsent(playerId, ignored -> defaultProgress());
    }

    public int getHighestUnlockedFloor(UUID playerId) {
        return getOrCreate(playerId).highestUnlockedFloor();
    }

    public boolean isFloorUnlocked(UUID playerId, int floor) {
        return floor >= 1 && floor <= getHighestUnlockedFloor(playerId);
    }

    public boolean isFloorCompleted(UUID playerId, int floor) {
        return floor >= 1 && getOrCreate(playerId).completedFloors().contains(floor);
    }

    public int getHighestCompletedFloor(UUID playerId) {
        return getOrCreate(playerId).completedFloors().stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);
    }

    public int getSelectedFloor(UUID playerId) {
        PlayerFloorProgress progress = getOrCreate(playerId);
        int selected = progress.selectedFloor();
        return selected >= 1 && selected <= progress.highestUnlockedFloor()
            ? selected
            : progress.highestUnlockedFloor();
    }

    public PreparedFloorModifierPlan getOrPrepareModifierPlan(UUID playerId, int floor) {
        getOrCreate(playerId);
        int safeFloor = Math.max(1, floor);
        Map<Integer, PreparedFloorModifierPlan> plans =
            preparedPlansByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
        PreparedFloorModifierPlan existing = plans.get(safeFloor);
        if (existing != null) return existing;
        long seed = UUID.randomUUID().getMostSignificantBits()
            ^ UUID.randomUUID().getLeastSignificantBits()
            ^ playerId.getMostSignificantBits()
            ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 17)
            ^ ((long) safeFloor << 32);
        PreparedFloorModifierPlan prepared =
            attachPreparedModifierProofs(
                DungeonModifierSelector.select(safeFloor, seed, "FLOOR_SELECTION")
            );
        plans.put(safeFloor, prepared);
        setDirty();
        return prepared;
    }

    public PreparedFloorModifierPlan prepareModifierPlan(
        UUID playerId,
        int floor,
        long seed,
        String reason
    ) {
        getOrCreate(playerId);
        PreparedFloorModifierPlan prepared =
            attachPreparedModifierProofs(DungeonModifierSelector.select(floor, seed, reason));
        preparedPlansByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>())
            .put(prepared.floor(), prepared);
        setDirty();
        return prepared;
    }

    public boolean clearPreparedModifierPlan(UUID playerId, int floor) {
        if (playerId == null) return false;
        Map<Integer, PreparedFloorModifierPlan> preparedPlans = preparedPlansByPlayer.get(playerId);
        if (preparedPlans == null) return false;
        boolean removed = preparedPlans.remove(Math.max(1, floor)) != null;
        if (!removed) return false;
        if (preparedPlans.isEmpty()) {
            preparedPlansByPlayer.remove(playerId);
        }
        setDirty();
        return true;
    }

    static PreparedFloorModifierPlan attachPreparedModifierProofs(PreparedFloorModifierPlan plan) {
        PreparedFloorModifierPlan withKin = attachKinSelection(plan);
        return attachBossResolution(withKin);
    }

    private static PreparedFloorModifierPlan attachKinSelection(PreparedFloorModifierPlan plan) {
        if (plan == null || plan.kinSelection().isPresent()) {
            return plan;
        }
        String familyGroup = plan.hostileModifierIds().stream()
            .map(DungeonEnemyKinCatalog::groupForModifierId)
            .filter(group -> !group.isBlank())
            .findFirst()
            .orElse("");
        DungeonEnemyKinSelection selection;
        if (!familyGroup.isBlank()) {
            selection = DungeonEnemyKinSelector.selectForGroup(
                familyGroup,
                plan.floor(),
                plan.seed(),
                DungeonEnemyKinSelector.STANDARD_REQUIRED_ROLES,
                Set.copyOf(plan.hostileModifierIds()),
                Integer.MAX_VALUE
            );
        } else if (plan.hostileModifierIds().stream()
            .anyMatch(id -> "kin".equalsIgnoreCase(id))) {
            selection = DungeonEnemyKinSelector.select(
                plan.floor(),
                plan.seed(),
                DungeonEnemyKinSelector.STANDARD_REQUIRED_ROLES,
                Set.copyOf(plan.hostileModifierIds()),
                Integer.MAX_VALUE
            );
        } else {
            return plan;
        }
        return plan.withKinSelection(selection);
    }

    private static PreparedFloorModifierPlan attachBossResolution(PreparedFloorModifierPlan plan) {
        if (plan == null || plan.bossResolution().isPresent()) {
            return plan;
        }
        boolean affinityRequested = plan.hostileModifierIds().stream()
            .anyMatch(id -> "boss-affinity".equalsIgnoreCase(id));
        DungeonBossResolution resolution = affinityRequested
            ? DungeonBossResolver.resolve(
                plan.floor(),
                plan.seed(),
                plan.kinGroup(),
                plan.bossAffinityTheme()
            )
            : DungeonBossResolver.resolveNormal(plan.floor(), plan.seed());
        return plan.withBossResolution(resolution);
    }

    public PreparedFloorModifierPlan getPreparedModifierPlan(UUID playerId, int floor) {
        Map<Integer, PreparedFloorModifierPlan> plans = preparedPlansByPlayer.get(playerId);
        return plans == null ? null : plans.get(Math.max(1, floor));
    }

    public FloorSelectionResult selectFloor(UUID playerId, int floor) {
        PlayerFloorProgress progress = getOrCreate(playerId);
        if (floor < 1) {
            return new FloorSelectionResult(false, "INVALID_FLOOR", floor, progress.highestUnlockedFloor());
        }
        if (floor > progress.highestUnlockedFloor()) {
            return new FloorSelectionResult(false, "LOCKED", floor, progress.highestUnlockedFloor());
        }
        if (progress.selectedFloor() != floor) {
            progressByPlayer.put(playerId, new PlayerFloorProgress(
                progress.highestUnlockedFloor(),
                progress.completedFloors(),
                floor,
                progress.lastStartedFloor(),
                progress.lastCompletedFloor(),
                progress.lastCompletionGameTime(),
                progress.activeRunStart()
            ));
            setDirty();
        }
        getOrPrepareModifierPlan(playerId, floor);
        return new FloorSelectionResult(true, "PASS", floor, progress.highestUnlockedFloor());
    }

    public FloorStartResult canStartFloor(UUID playerId, int floor) {
        if (floor < 1) {
            return new FloorStartResult(false, "INVALID_FLOOR", floor, getHighestUnlockedFloor(playerId), false);
        }
        int highestUnlocked = getHighestUnlockedFloor(playerId);
        if (floor > highestUnlocked) {
            return new FloorStartResult(false, "LOCKED", floor, highestUnlocked, false);
        }
        return new FloorStartResult(true, "PASS", floor, highestUnlocked, isFloorCompleted(playerId, floor));
    }

    public FloorStartResult recordFloorStarted(UUID playerId, int floor, long gameTime) {
        return recordFloorStarted(
            playerId, floor, gameTime, "", 0.0D, "UNAVAILABLE_NOT_CAPTURED"
        );
    }

    public FloorStartResult recordFloorStarted(
        UUID playerId,
        int floor,
        long gameTime,
        String sessionId,
        double entryEp,
        String epStatus
    ) {
        FloorStartResult check = canStartFloor(playerId, floor);
        if (!check.allowed()) {
            return check;
        }
        PlayerFloorProgress progress = getOrCreate(playerId);
        progressByPlayer.put(playerId, new PlayerFloorProgress(
            progress.highestUnlockedFloor(),
            new LinkedHashSet<>(progress.completedFloors()),
            progress.selectedFloor(),
            floor,
            progress.lastCompletedFloor(),
            progress.lastCompletionGameTime(),
            new RunStartTelemetry(floor, sessionId, gameTime, entryEp, epStatus)
        ));
        setDirty();
        return new FloorStartResult(true, "PASS", floor, progress.highestUnlockedFloor(), progress.completedFloors().contains(floor));
    }

    public FloorCompletionProgressResult recordFloorCompleted(UUID playerId, int floor, long gameTime) {
        PlayerFloorProgress progress = getOrCreate(playerId);
        int highestUnlockedBefore = progress.highestUnlockedFloor();
        boolean alreadyCompletedBefore = progress.completedFloors().contains(floor);
        Set<Integer> completedFloors = new LinkedHashSet<>(progress.completedFloors());
        if (floor > 0) {
            completedFloors.add(floor);
        }
        int highestUnlockedAfter = highestUnlockedBefore;
        boolean unlockedNextFloor = false;
        if (floor >= 1 && floor + 1 > highestUnlockedAfter) {
            highestUnlockedAfter = floor + 1;
            unlockedNextFloor = true;
        }
        progressByPlayer.put(playerId, new PlayerFloorProgress(
            Math.max(1, highestUnlockedAfter),
            completedFloors,
            progress.selectedFloor(),
            progress.lastStartedFloor(),
            Math.max(0, floor),
            gameTime,
            progress.activeRunStart()
        ));
        Map<Integer, PreparedFloorModifierPlan> preparedPlans = preparedPlansByPlayer.get(playerId);
        if (preparedPlans != null && preparedPlans.remove(floor) != null && preparedPlans.isEmpty()) {
            preparedPlansByPlayer.remove(playerId);
        }
        setDirty();
        return new FloorCompletionProgressResult(true, floor, alreadyCompletedBefore, highestUnlockedBefore, highestUnlockedAfter, unlockedNextFloor);
    }

    public boolean recordDamageDealt(UUID playerId, String sessionId, double damage) {
        return updateActiveCombat(playerId, sessionId, combat -> combat.addDamageDealt(damage));
    }

    public boolean recordDamageTaken(UUID playerId, String sessionId, double damage) {
        return updateActiveCombat(playerId, sessionId, combat -> combat.addDamageTaken(damage));
    }

    public boolean recordEnemyDefeated(
        UUID playerId,
        String sessionId,
        String entityTypeId,
        String displayName
    ) {
        return updateActiveCombat(
            playerId,
            sessionId,
            combat -> combat.addEnemyDefeat(entityTypeId, displayName)
        );
    }

    private boolean updateActiveCombat(
        UUID playerId,
        String sessionId,
        UnaryOperator<RunCombatTelemetry> update
    ) {
        if (playerId == null || update == null) return false;
        PlayerFloorProgress progress = getOrCreate(playerId);
        RunStartTelemetry start = progress.activeRunStart();
        if (!start.matchesSession(sessionId)) return false;
        RunCombatTelemetry updated = update.apply(start.combat());
        if (updated == null || updated.equals(start.combat())) return false;
        progressByPlayer.put(playerId, new PlayerFloorProgress(
            progress.highestUnlockedFloor(),
            progress.completedFloors(),
            progress.selectedFloor(),
            progress.lastStartedFloor(),
            progress.lastCompletedFloor(),
            progress.lastCompletionGameTime(),
            start.withCombat(updated)
        ));
        setDirty();
        return true;
    }

    public int clearProgress(UUID playerId) {
        PlayerFloorProgress removed = progressByPlayer.remove(playerId);
        preparedPlansByPlayer.remove(playerId);
        completedRunsByPlayer.remove(playerId);
        bestRecordsByPlayer.remove(playerId);
        if (removed != null) {
            setDirty();
            return removed.completedFloors().size();
        }
        return 0;
    }

    public List<Integer> getCompletedFloors(UUID playerId) {
        return List.copyOf(getOrCreate(playerId).completedFloors().stream().sorted().toList());
    }

    public String getCompletedFloorsSummary(UUID playerId) {
        List<Integer> completedFloors = getCompletedFloors(playerId);
        if (completedFloors.isEmpty()) {
            return "NONE";
        }
        return completedFloors.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    public String debugSummary(UUID playerId) {
        PlayerFloorProgress progress = getOrCreate(playerId);
        return "Highest Unlocked Floor: " + progress.highestUnlockedFloor()
            + ", Completed Floors: " + getCompletedFloorsSummary(playerId)
            + ", Selected Floor: " + getSelectedFloor(playerId)
            + ", Last Started Floor: " + (progress.lastStartedFloor() <= 0 ? "NONE" : progress.lastStartedFloor())
            + ", Last Completed Floor: " + (progress.lastCompletedFloor() <= 0 ? "NONE" : progress.lastCompletedFloor());
    }

    public CompletedRunSummary recordCompletedRun(
        UUID playerId,
        String playerName,
        RunContext context,
        RunRewards rewards,
        long completionGameTime,
        double currentEp,
        String currentEpStatus
    ) {
        RunContext normalizedContext = context == null
            ? new RunContext("", 0, "NONE", "UNKNOWN", 0, 0, "UNAVAILABLE_NOT_TRACKED", List.of())
            : context;
        CompletedRunSummary existing = normalizedContext.sessionId().isBlank()
            ? null
            : getRecentCompletedRuns(playerId).stream()
                .filter(summary -> summary.context().floor() == normalizedContext.floor()
                    && summary.context().sessionId().equals(normalizedContext.sessionId()))
                .findFirst()
                .orElse(null);
        if (existing != null) return existing;
        PlayerFloorProgress progress = getOrCreate(playerId);
        RunStartTelemetry start = progress.activeRunStart();
        boolean matchingStart = start.matches(normalizedContext.floor(), normalizedContext.sessionId());
        long clearTicks = matchingStart ? Math.max(0L, completionGameTime - start.gameTime()) : 0L;
        String timingStatus = clearTicks > 0L ? "MEASURED" : "UNAVAILABLE_NO_MATCHING_START";
        String epStatus = matchingStart
            && "AVAILABLE".equals(start.epStatus())
            && "AVAILABLE".equals(currentEpStatus)
                ? "AVAILABLE"
                : "UNAVAILABLE";
        double entryEp = "AVAILABLE".equals(epStatus) ? start.entryEp() : 0.0D;
        double finalEp = "AVAILABLE".equals(epStatus) ? Math.max(0.0D, currentEp) : 0.0D;
        RunCombatTelemetry combat = matchingStart
            ? start.combat()
            : RunCombatTelemetry.empty();
        RunPerformance performance = new RunPerformance(
            clearTicks,
            timingStatus,
            "SUCCESS",
            "C",
            "DEFERRED_TARGETS",
            "UNAVAILABLE_NOT_TRACKED",
            "NOT_IMPLEMENTED",
            entryEp,
            finalEp,
            Math.max(0.0D, finalEp - entryEp),
            epStatus
        );
        CompletedRunSummary summary = new CompletedRunSummary(
            playerId, playerName, normalizedContext, performance, rewards, combat, completionGameTime
        );
        List<CompletedRunSummary> recent = completedRunsByPlayer.computeIfAbsent(
            playerId, ignored -> new ArrayList<>()
        );
        recent.add(summary);
        while (recent.size() > MAX_RECENT_RUNS_PER_PLAYER) recent.remove(0);
        if (clearTicks > 0L) {
            LocalFloorRecord record = new LocalFloorRecord(
                playerId, playerName, normalizedContext.floor(), clearTicks, completionGameTime, "C"
            );
            bestRecordsByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>())
                .merge(normalizedContext.floor(), record, DungeonFloorProgressData::fasterRecord);
        }
        progressByPlayer.put(playerId, new PlayerFloorProgress(
            progress.highestUnlockedFloor(),
            progress.completedFloors(),
            progress.selectedFloor(),
            progress.lastStartedFloor(),
            progress.lastCompletedFloor(),
            progress.lastCompletionGameTime(),
            RunStartTelemetry.none()
        ));
        setDirty();
        return summary;
    }

    public List<CompletedRunSummary> getRecentCompletedRuns(UUID playerId) {
        List<CompletedRunSummary> summaries = completedRunsByPlayer.get(playerId);
        return summaries == null ? List.of() : List.copyOf(summaries);
    }

    public CompletedRunSummary getLatestCompletedRun(UUID playerId) {
        List<CompletedRunSummary> summaries = completedRunsByPlayer.get(playerId);
        return summaries == null || summaries.isEmpty() ? CompletedRunSummary.empty(playerId)
            : summaries.get(summaries.size() - 1);
    }

    public CompletedRunSummary getCompletedRun(UUID playerId, String sessionId) {
        if (playerId == null || sessionId == null || sessionId.isBlank()) {
            return CompletedRunSummary.empty(playerId);
        }
        List<CompletedRunSummary> summaries = completedRunsByPlayer.get(playerId);
        if (summaries == null) return CompletedRunSummary.empty(playerId);
        for (int index = summaries.size() - 1; index >= 0; index--) {
            CompletedRunSummary summary = summaries.get(index);
            if (sessionId.equals(summary.context().sessionId())) return summary;
        }
        return CompletedRunSummary.empty(playerId);
    }

    public List<LocalFloorRecord> getLocalRecords(int floor) {
        if (floor <= 0) return List.of();
        return bestRecordsByPlayer.values().stream()
            .map(records -> records.get(floor))
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparingLong(LocalFloorRecord::clearTicks)
                .thenComparing(LocalFloorRecord::playerName))
            .toList();
    }

    public boolean isPersonalBest(CompletedRunSummary summary) {
        if (summary == null || summary.context().floor() <= 0
            || summary.performance().clearTicks() <= 0L) {
            return false;
        }
        Map<Integer, LocalFloorRecord> records = bestRecordsByPlayer.get(summary.playerId());
        if (records == null) return false;
        LocalFloorRecord best = records.get(summary.context().floor());
        return best != null
            && best.clearTicks() == summary.performance().clearTicks()
            && best.completionGameTime() == summary.completionGameTime();
    }

    private static LocalFloorRecord fasterRecord(LocalFloorRecord first, LocalFloorRecord second) {
        return first.clearTicks() <= second.clearTicks() ? first : second;
    }

    private static PlayerFloorProgress defaultProgress() {
        return new PlayerFloorProgress(1, new LinkedHashSet<>(), 0, 0, 0, 0L, RunStartTelemetry.none());
    }

    public record PlayerFloorProgress(
        int highestUnlockedFloor,
        Set<Integer> completedFloors,
        int selectedFloor,
        int lastStartedFloor,
        int lastCompletedFloor,
        long lastCompletionGameTime,
        RunStartTelemetry activeRunStart
    ) {
        public PlayerFloorProgress {
            completedFloors = completedFloors == null ? new LinkedHashSet<>() : new LinkedHashSet<>(completedFloors);
            highestUnlockedFloor = Math.max(1, highestUnlockedFloor);
            selectedFloor = selectedFloor >= 1 && selectedFloor <= highestUnlockedFloor ? selectedFloor : 0;
            lastStartedFloor = Math.max(0, lastStartedFloor);
            lastCompletedFloor = Math.max(0, lastCompletedFloor);
            activeRunStart = activeRunStart == null ? RunStartTelemetry.none() : activeRunStart;
        }
    }

    public record RunStartTelemetry(
        int floor,
        String sessionId,
        long gameTime,
        double entryEp,
        String epStatus,
        RunCombatTelemetry combat
    ) {
        public RunStartTelemetry(
            int floor,
            String sessionId,
            long gameTime,
            double entryEp,
            String epStatus
        ) {
            this(floor, sessionId, gameTime, entryEp, epStatus, RunCombatTelemetry.empty());
        }

        public RunStartTelemetry {
            floor = Math.max(0, floor);
            sessionId = bounded(sessionId, 96);
            gameTime = Math.max(0L, gameTime);
            entryEp = Math.max(0.0D, entryEp);
            epStatus = bounded(epStatus, 48);
            combat = combat == null ? RunCombatTelemetry.empty() : combat;
        }

        static RunStartTelemetry none() {
            return new RunStartTelemetry(
                0, "", 0L, 0.0D, "UNAVAILABLE_NOT_CAPTURED", RunCombatTelemetry.empty()
            );
        }

        boolean available() {
            return floor > 0 && gameTime > 0L;
        }

        boolean matches(int completedFloor, String completedSessionId) {
            return available()
                && floor == completedFloor
                && (sessionId.isBlank() || sessionId.equals(completedSessionId));
        }

        boolean matchesSession(String candidateSessionId) {
            return available()
                && candidateSessionId != null
                && !candidateSessionId.isBlank()
                && sessionId.equals(candidateSessionId);
        }

        RunStartTelemetry withCombat(RunCombatTelemetry updatedCombat) {
            return new RunStartTelemetry(
                floor, sessionId, gameTime, entryEp, epStatus, updatedCombat
            );
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Floor", floor);
            tag.putString("SessionId", sessionId);
            tag.putLong("GameTime", gameTime);
            tag.putDouble("EntryEp", entryEp);
            tag.putString("EpStatus", epStatus);
            tag.put("Combat", combat.save());
            return tag;
        }

        static RunStartTelemetry load(CompoundTag tag) {
            return new RunStartTelemetry(
                tag.getInt("Floor"),
                tag.getString("SessionId"),
                tag.getLong("GameTime"),
                tag.getDouble("EntryEp"),
                tag.contains("EpStatus") ? tag.getString("EpStatus") : "UNAVAILABLE_NOT_CAPTURED",
                tag.contains("Combat", Tag.TAG_COMPOUND)
                    ? RunCombatTelemetry.load(tag.getCompound("Combat"))
                    : RunCombatTelemetry.empty()
            );
        }
    }

    public record RunCombatTelemetry(
        double damageDealt,
        double damageTaken,
        List<EnemyDefeat> enemyDefeats
    ) {
        public RunCombatTelemetry {
            damageDealt = boundedDamage(damageDealt);
            damageTaken = boundedDamage(damageTaken);
            enemyDefeats = enemyDefeats == null
                ? List.of()
                : List.copyOf(enemyDefeats.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(defeat -> defeat.kills() > 0)
                    .limit(MAX_ENEMY_KINDS_PER_RUN)
                    .toList());
        }

        public static RunCombatTelemetry empty() {
            return new RunCombatTelemetry(0.0D, 0.0D, List.of());
        }

        public int totalDefeated() {
            long total = enemyDefeats.stream().mapToLong(EnemyDefeat::kills).sum();
            return (int) Math.min(Integer.MAX_VALUE, total);
        }

        RunCombatTelemetry addDamageDealt(double amount) {
            double bounded = boundedDamage(amount);
            if (bounded <= 0.0D) return this;
            return new RunCombatTelemetry(
                boundedDamage(damageDealt + bounded), damageTaken, enemyDefeats
            );
        }

        RunCombatTelemetry addDamageTaken(double amount) {
            double bounded = boundedDamage(amount);
            if (bounded <= 0.0D) return this;
            return new RunCombatTelemetry(
                damageDealt, boundedDamage(damageTaken + bounded), enemyDefeats
            );
        }

        RunCombatTelemetry addEnemyDefeat(String entityTypeId, String displayName) {
            String boundedId = bounded(entityTypeId, 128);
            if (boundedId.isBlank()) return this;
            String boundedName = bounded(displayName, 80);
            List<EnemyDefeat> updated = new ArrayList<>(enemyDefeats);
            for (int index = 0; index < updated.size(); index++) {
                EnemyDefeat existing = updated.get(index);
                if (existing.entityTypeId().equals(boundedId)) {
                    updated.set(index, existing.increment());
                    return new RunCombatTelemetry(damageDealt, damageTaken, updated);
                }
            }
            if (updated.size() >= MAX_ENEMY_KINDS_PER_RUN - 1) {
                String otherId = "tensura_dungeon:other";
                for (int index = 0; index < updated.size(); index++) {
                    EnemyDefeat existing = updated.get(index);
                    if (existing.entityTypeId().equals(otherId)) {
                        updated.set(index, existing.increment());
                        return new RunCombatTelemetry(damageDealt, damageTaken, updated);
                    }
                }
                updated.add(new EnemyDefeat(otherId, "Other Enemies", 1));
            } else {
                updated.add(new EnemyDefeat(boundedId, boundedName, 1));
            }
            return new RunCombatTelemetry(damageDealt, damageTaken, updated);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("DamageDealt", damageDealt);
            tag.putDouble("DamageTaken", damageTaken);
            ListTag defeats = new ListTag();
            enemyDefeats.forEach(defeat -> defeats.add(defeat.save()));
            tag.put("EnemyDefeats", defeats);
            return tag;
        }

        static RunCombatTelemetry load(CompoundTag tag) {
            List<EnemyDefeat> defeats = new ArrayList<>();
            ListTag defeatTags = tag.getList("EnemyDefeats", Tag.TAG_COMPOUND);
            for (int index = 0;
                 index < defeatTags.size() && index < MAX_ENEMY_KINDS_PER_RUN;
                 index++) {
                EnemyDefeat defeat = EnemyDefeat.load(defeatTags.getCompound(index));
                if (defeat.kills() > 0 && !defeat.entityTypeId().isBlank()) defeats.add(defeat);
            }
            return new RunCombatTelemetry(
                tag.getDouble("DamageDealt"), tag.getDouble("DamageTaken"), defeats
            );
        }
    }

    public record EnemyDefeat(String entityTypeId, String displayName, int kills) {
        public EnemyDefeat {
            entityTypeId = bounded(entityTypeId, 128);
            displayName = bounded(displayName, 80);
            if (displayName.isBlank()) displayName = entityTypeId;
            kills = Math.max(0, kills);
        }

        EnemyDefeat increment() {
            return new EnemyDefeat(
                entityTypeId,
                displayName,
                kills == Integer.MAX_VALUE ? Integer.MAX_VALUE : kills + 1
            );
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("EntityType", entityTypeId);
            tag.putString("DisplayName", displayName);
            tag.putInt("Kills", kills);
            return tag;
        }

        static EnemyDefeat load(CompoundTag tag) {
            return new EnemyDefeat(
                tag.getString("EntityType"), tag.getString("DisplayName"), tag.getInt("Kills")
            );
        }
    }

    public record RunContext(
        String sessionId,
        int floor,
        String mode,
        String objective,
        int roomCount,
        int partySize,
        String partyComposition,
        List<String> modifierIds
    ) {
        public RunContext {
            sessionId = bounded(sessionId, 96);
            floor = Math.max(0, floor);
            mode = bounded(mode, 48);
            objective = bounded(objective, 96);
            roomCount = Math.max(0, roomCount);
            partySize = Math.max(0, Math.min(4, partySize));
            partyComposition = bounded(partyComposition, 96);
            modifierIds = modifierIds == null ? List.of()
                : List.copyOf(modifierIds.subList(0, Math.min(16, modifierIds.size())));
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("SessionId", sessionId);
            tag.putInt("Floor", floor);
            tag.putString("Mode", mode);
            tag.putString("Objective", objective);
            tag.putInt("RoomCount", roomCount);
            tag.putInt("PartySize", partySize);
            tag.putString("PartyComposition", partyComposition);
            ListTag modifiers = new ListTag();
            modifierIds.forEach(id -> {
                CompoundTag modifier = new CompoundTag();
                modifier.putString("Id", id);
                modifiers.add(modifier);
            });
            tag.put("Modifiers", modifiers);
            return tag;
        }

        static RunContext load(CompoundTag tag) {
            List<String> modifiers = new ArrayList<>();
            ListTag modifierTags = tag.getList("Modifiers", Tag.TAG_COMPOUND);
            for (int index = 0; index < modifierTags.size() && index < 16; index++) {
                modifiers.add(modifierTags.getCompound(index).getString("Id"));
            }
            return new RunContext(
                tag.getString("SessionId"), tag.getInt("Floor"), tag.getString("Mode"),
                tag.getString("Objective"), tag.getInt("RoomCount"),
                tag.contains("PartySize") ? tag.getInt("PartySize") : 0,
                tag.contains("PartyComposition")
                    ? tag.getString("PartyComposition")
                    : "UNAVAILABLE_NOT_TRACKED",
                modifiers
            );
        }
    }

    public record RunPerformance(
        long clearTicks,
        String timingStatus,
        String result,
        String clearRank,
        String rankStatus,
        String deathsStatus,
        String checkpointStatus,
        double entryEp,
        double currentEp,
        double epGain,
        String epStatus
    ) {
        public RunPerformance {
            clearTicks = Math.max(0L, clearTicks);
            timingStatus = bounded(timingStatus, 48);
            result = bounded(result, 24);
            clearRank = bounded(clearRank, 8);
            rankStatus = bounded(rankStatus, 48);
            deathsStatus = bounded(deathsStatus, 48);
            checkpointStatus = bounded(checkpointStatus, 48);
            entryEp = Math.max(0.0D, entryEp);
            currentEp = Math.max(0.0D, currentEp);
            epGain = Math.max(0.0D, epGain);
            epStatus = bounded(epStatus, 48);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("ClearTicks", clearTicks);
            tag.putString("TimingStatus", timingStatus);
            tag.putString("Result", result);
            tag.putString("ClearRank", clearRank);
            tag.putString("RankStatus", rankStatus);
            tag.putString("DeathsStatus", deathsStatus);
            tag.putString("CheckpointStatus", checkpointStatus);
            tag.putDouble("EntryEp", entryEp);
            tag.putDouble("CurrentEp", currentEp);
            tag.putDouble("EpGain", epGain);
            tag.putString("EpStatus", epStatus);
            return tag;
        }

        static RunPerformance load(CompoundTag tag) {
            return new RunPerformance(
                tag.getLong("ClearTicks"), tag.getString("TimingStatus"), tag.getString("Result"),
                tag.getString("ClearRank"), tag.getString("RankStatus"), tag.getString("DeathsStatus"),
                tag.getString("CheckpointStatus"), tag.getDouble("EntryEp"), tag.getDouble("CurrentEp"),
                tag.getDouble("EpGain"), tag.getString("EpStatus")
            );
        }
    }

    public record RunRewards(
        long baseCurrencyBronze,
        long bonusCurrencyBronze,
        long totalCurrencyBronze,
        int commonDrops,
        int uncommonDrops,
        int rareDrops,
        int epicDrops,
        int crystalCount
    ) {
        public RunRewards {
            baseCurrencyBronze = Math.max(0L, baseCurrencyBronze);
            bonusCurrencyBronze = Math.max(0L, bonusCurrencyBronze);
            totalCurrencyBronze = Math.max(0L, totalCurrencyBronze);
            commonDrops = Math.max(0, commonDrops);
            uncommonDrops = Math.max(0, uncommonDrops);
            rareDrops = Math.max(0, rareDrops);
            epicDrops = Math.max(0, epicDrops);
            crystalCount = Math.max(0, crystalCount);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("BaseCurrencyBronze", baseCurrencyBronze);
            tag.putLong("BonusCurrencyBronze", bonusCurrencyBronze);
            tag.putLong("TotalCurrencyBronze", totalCurrencyBronze);
            tag.putInt("CommonDrops", commonDrops);
            tag.putInt("UncommonDrops", uncommonDrops);
            tag.putInt("RareDrops", rareDrops);
            tag.putInt("EpicDrops", epicDrops);
            tag.putInt("CrystalCount", crystalCount);
            return tag;
        }

        static RunRewards load(CompoundTag tag) {
            return new RunRewards(
                tag.getLong("BaseCurrencyBronze"), tag.getLong("BonusCurrencyBronze"),
                tag.getLong("TotalCurrencyBronze"), tag.getInt("CommonDrops"),
                tag.getInt("UncommonDrops"), tag.getInt("RareDrops"), tag.getInt("EpicDrops"),
                tag.getInt("CrystalCount")
            );
        }
    }

    public record CompletedRunSummary(
        UUID playerId,
        String playerName,
        RunContext context,
        RunPerformance performance,
        RunRewards rewards,
        RunCombatTelemetry combat,
        long completionGameTime
    ) {
        public CompletedRunSummary(
            UUID playerId,
            String playerName,
            RunContext context,
            RunPerformance performance,
            RunRewards rewards,
            long completionGameTime
        ) {
            this(
                playerId, playerName, context, performance, rewards,
                RunCombatTelemetry.empty(), completionGameTime
            );
        }

        public CompletedRunSummary {
            playerId = playerId == null ? new UUID(0L, 0L) : playerId;
            playerName = bounded(playerName, 64);
            context = context == null ? new RunContext(
                "", 0, "NONE", "UNKNOWN", 0, 0, "UNAVAILABLE_NOT_TRACKED", List.of()
            ) : context;
            performance = performance == null ? new RunPerformance(0L, "UNAVAILABLE", "UNKNOWN", "C", "DEFERRED_TARGETS", "UNAVAILABLE_NOT_TRACKED", "NOT_IMPLEMENTED", 0, 0, 0, "UNAVAILABLE") : performance;
            rewards = rewards == null ? new RunRewards(0, 0, 0, 0, 0, 0, 0, 0) : rewards;
            combat = combat == null ? RunCombatTelemetry.empty() : combat;
            completionGameTime = Math.max(0L, completionGameTime);
        }

        static CompletedRunSummary empty(UUID playerId) {
            return new CompletedRunSummary(playerId, "", null, null, null, 0L);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("PlayerName", playerName);
            tag.put("Context", context.save());
            tag.put("Performance", performance.save());
            tag.put("Rewards", rewards.save());
            tag.put("Combat", combat.save());
            tag.putLong("CompletionGameTime", completionGameTime);
            return tag;
        }

        static CompletedRunSummary load(UUID playerId, CompoundTag tag) {
            return new CompletedRunSummary(
                playerId,
                tag.getString("PlayerName"),
                RunContext.load(tag.getCompound("Context")),
                RunPerformance.load(tag.getCompound("Performance")),
                RunRewards.load(tag.getCompound("Rewards")),
                tag.contains("Combat", Tag.TAG_COMPOUND)
                    ? RunCombatTelemetry.load(tag.getCompound("Combat"))
                    : RunCombatTelemetry.empty(),
                tag.getLong("CompletionGameTime")
            );
        }
    }

    public record LocalFloorRecord(
        UUID playerId,
        String playerName,
        int floor,
        long clearTicks,
        long completionGameTime,
        String clearRank
    ) {
        public LocalFloorRecord {
            playerId = playerId == null ? new UUID(0L, 0L) : playerId;
            playerName = bounded(playerName, 64);
            floor = Math.max(0, floor);
            clearTicks = Math.max(0L, clearTicks);
            completionGameTime = Math.max(0L, completionGameTime);
            clearRank = bounded(clearRank, 8);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("PlayerName", playerName);
            tag.putInt("Floor", floor);
            tag.putLong("ClearTicks", clearTicks);
            tag.putLong("CompletionGameTime", completionGameTime);
            tag.putString("ClearRank", clearRank);
            return tag;
        }

        static LocalFloorRecord load(UUID playerId, CompoundTag tag) {
            return new LocalFloorRecord(
                playerId, tag.getString("PlayerName"), tag.getInt("Floor"),
                tag.getLong("ClearTicks"), tag.getLong("CompletionGameTime"), tag.getString("ClearRank")
            );
        }
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static double boundedDamage(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) return 0.0D;
        return Math.min(MAX_RECORDED_DAMAGE, value);
    }

    public record FloorStartResult(boolean allowed, String status, int requestedFloor, int highestUnlockedFloor, boolean redoFloor) {
    }

    public record FloorSelectionResult(boolean selected, String status, int requestedFloor, int highestUnlockedFloor) {
    }

    public record FloorCompletionProgressResult(boolean updated, int floorCompleted, boolean alreadyCompletedBefore, int highestUnlockedBefore, int highestUnlockedAfter, boolean unlockedNextFloor) {
    }
}
