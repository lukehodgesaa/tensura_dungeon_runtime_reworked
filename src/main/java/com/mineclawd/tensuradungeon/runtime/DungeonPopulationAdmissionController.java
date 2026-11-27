package com.mineclawd.tensuradungeon.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class DungeonPopulationAdmissionController {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODE = "SHADOW_ONLY";
    public static final int REINFORCEMENT_BELL_MAX_PER_ROOM = 2;
    static final AdmissionLimits DEFAULT_SHADOW_LIMITS = new AdmissionLimits(80, 20);
    private static final int STALE_REPORT_TICKS = 100;
    private static final int LOG_INTERVAL_TICKS = 600;
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();

    private DungeonPopulationAdmissionController() {
    }

    public static void report(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos controllerPos,
        EncounterUsage usage
    ) {
        if (level == null || instanceKey == null || controllerPos == null || usage == null) {
            return;
        }
        State state = STATES.computeIfAbsent(level.getServer(), ignored -> new State());
        EncounterKey encounterKey = new EncounterKey(
            level.dimension().location().toString(),
            instanceKey,
            controllerPos.asLong()
        );
        int now = level.getServer().getTickCount();
        state.reports.put(encounterKey, new TimedUsage(now, usage.sanitized()));
        if (state.lastLogTick == Integer.MIN_VALUE || now - state.lastLogTick >= LOG_INTERVAL_TICKS) {
            state.lastLogTick = now;
            logSnapshot(snapshot(level.getServer()));
        }
    }

    public static void remove(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        BlockPos controllerPos
    ) {
        if (level == null || instanceKey == null || controllerPos == null) {
            return;
        }
        State state = STATES.get(level.getServer());
        if (state == null) {
            return;
        }
        state.reports.remove(new EncounterKey(
            level.dimension().location().toString(),
            instanceKey,
            controllerPos.asLong()
        ));
    }

    public static Snapshot snapshot(MinecraftServer server) {
        if (server == null) {
            return Snapshot.empty();
        }
        State state = STATES.get(server);
        if (state == null) {
            return Snapshot.empty();
        }
        int cutoff = server.getTickCount() - STALE_REPORT_TICKS;
        state.reports.entrySet().removeIf(entry -> entry.getValue().reportedAtTick() < cutoff);
        return summarize(state.reports.entrySet().stream()
            .map(entry -> new EncounterReport(entry.getKey(), entry.getValue().usage()))
            .toList());
    }

    static Snapshot summarize(Collection<EncounterReport> reports) {
        return summarize(reports, DEFAULT_SHADOW_LIMITS);
    }

    static Snapshot summarize(Collection<EncounterReport> reports, AdmissionLimits limits) {
        Map<DungeonInstanceAllocationData.InstanceKey, MutableInstanceUsage> byInstance = new HashMap<>();
        List<EncounterReport> validReports = new ArrayList<>();
        int alive = 0;
        int ordinary = 0;
        int protectedCount = 0;
        int summoned = 0;
        int planned = 0;
        int pending = 0;
        int requiredRoleSlots = 0;
        int encounterCount = 0;

        for (EncounterReport report : reports) {
            if (report == null || report.key() == null || report.usage() == null) {
                continue;
            }
            EncounterUsage usage = report.usage().sanitized();
            validReports.add(new EncounterReport(report.key(), usage));
            encounterCount++;
            alive += usage.alive();
            ordinary += usage.ordinaryAlive();
            protectedCount += usage.protectedAlive();
            summoned += usage.summonedAlive();
            planned += usage.planned();
            pending += usage.pending();
            requiredRoleSlots += usage.requiredRoleSlots();
            byInstance.computeIfAbsent(
                report.key().instanceKey(),
                ignored -> new MutableInstanceUsage()
            ).add(usage);
        }

        List<InstanceUsage> instances = new ArrayList<>();
        byInstance.forEach((key, usage) -> instances.add(usage.freeze(key)));
        instances.sort(Comparator
            .comparing((InstanceUsage usage) -> usage.instanceKey().instanceId().toString())
            .thenComparingLong(usage -> usage.instanceKey().epoch()));
        return new Snapshot(
            MODE,
            encounterCount,
            byInstance.size(),
            alive,
            ordinary,
            protectedCount,
            summoned,
            planned,
            pending,
            requiredRoleSlots,
            List.copyOf(instances),
            evaluateShadow(validReports, limits)
        );
    }

    static ReinforcementProof evaluateReinforcementBell(
        int existingReinforcements,
        ReinforcementCandidate candidate,
        ReinforcementPopulation population,
        AdmissionLimits limits
    ) {
        AdmissionLimits safeLimits = limits == null ? DEFAULT_SHADOW_LIMITS : limits.sanitized();
        ReinforcementPopulation safePopulation = population == null
            ? ReinforcementPopulation.empty()
            : population.sanitized();
        int safeExisting = Math.max(0, existingReinforcements);
        if (existingReinforcements < 0) {
            return ReinforcementProof.rejected(
                safeExisting,
                "INVALID_REINFORCEMENT_COUNT"
            );
        }
        if (existingReinforcements >= REINFORCEMENT_BELL_MAX_PER_ROOM) {
            return ReinforcementProof.rejected(safeExisting, "REINFORCEMENT_CAP_REACHED");
        }
        if (candidate == null) {
            return ReinforcementProof.rejected(safeExisting, "INVALID_CANDIDATE");
        }
        if (candidate.entityId().isBlank()) {
            return ReinforcementProof.rejected(safeExisting, "INVALID_CANDIDATE_ID");
        }
        if (candidate.entityId().equalsIgnoreCase("tensura:pegasus")) {
            return ReinforcementProof.rejected(safeExisting, "INELIGIBLE_EXCLUDED_ENTITY");
        }
        if (candidate.kind() != ReinforcementCandidateKind.ORDINARY) {
            return ReinforcementProof.rejected(
                safeExisting,
                "INELIGIBLE_" + candidate.kind().name()
            );
        }
        if (safePopulation.globalAlive() >= safeLimits.globalAliveLimit()) {
            return ReinforcementProof.rejected(safeExisting, "GLOBAL_POPULATION_LIMIT");
        }
        if (safePopulation.instanceAlive() >= safeLimits.perInstanceAliveLimit()) {
            return ReinforcementProof.rejected(safeExisting, "INSTANCE_POPULATION_LIMIT");
        }
        return new ReinforcementProof(
            true,
            safeExisting,
            safeExisting + 1,
            "ORDINARY_CANDIDATE_WITHIN_BOUNDS"
        );
    }

    private static ShadowEvaluation evaluateShadow(
        Collection<EncounterReport> reports,
        AdmissionLimits limits
    ) {
        AdmissionLimits safeLimits = limits == null ? DEFAULT_SHADOW_LIMITS : limits.sanitized();
        List<MutableShadowGrant> grants = reports.stream()
            .filter(report -> report != null && report.key() != null && report.usage() != null)
            .map(report -> new MutableShadowGrant(report.key(), report.usage().sanitized()))
            .sorted(Comparator.comparing(MutableShadowGrant::key, ENCOUNTER_ORDER))
            .toList();

        int globalAlive = grants.stream().mapToInt(MutableShadowGrant::granted).sum();
        int globalRemaining = Math.max(0, safeLimits.globalAliveLimit() - globalAlive);
        Map<DungeonInstanceAllocationData.InstanceKey, Integer> instanceRemaining = new HashMap<>();
        for (MutableShadowGrant grant : grants) {
            instanceRemaining.merge(grant.key().instanceKey(), grant.granted(), Integer::sum);
        }
        instanceRemaining.replaceAll((key, value) ->
            Math.max(0, safeLimits.perInstanceAliveLimit() - value)
        );

        globalRemaining = allocateFairly(grants, instanceRemaining, globalRemaining, true);
        allocateFairly(grants, instanceRemaining, globalRemaining, false);

        List<ShadowDecision> decisions = grants.stream()
            .map(MutableShadowGrant::freeze)
            .toList();
        int wouldAdmit = 0;
        int wouldCompress = 0;
        int wouldWait = 0;
        int shadowAdmitted = 0;
        int unadmittedDemand = 0;
        for (ShadowDecision decision : decisions) {
            shadowAdmitted += decision.shadowAdmitted();
            unadmittedDemand += decision.unadmittedDemand();
            switch (decision.outcome()) {
                case WOULD_ADMIT -> wouldAdmit++;
                case WOULD_COMPRESS_CANDIDATE -> wouldCompress++;
                case WOULD_WAIT -> wouldWait++;
            }
        }
        return new ShadowEvaluation(
            safeLimits,
            wouldAdmit,
            wouldCompress,
            wouldWait,
            shadowAdmitted,
            unadmittedDemand,
            List.copyOf(decisions)
        );
    }

    private static int allocateFairly(
        List<MutableShadowGrant> grants,
        Map<DungeonInstanceAllocationData.InstanceKey, Integer> instanceRemaining,
        int globalRemaining,
        boolean minimumPhase
    ) {
        boolean progressed = true;
        while (globalRemaining > 0 && progressed) {
            progressed = false;
            for (MutableShadowGrant grant : grants) {
                if (globalRemaining <= 0) {
                    break;
                }
                int target = minimumPhase ? grant.minimumSafe() : grant.planned();
                int remainingForInstance = instanceRemaining.getOrDefault(grant.key().instanceKey(), 0);
                if (grant.granted() >= target || remainingForInstance <= 0) {
                    continue;
                }
                grant.admitOne();
                instanceRemaining.put(grant.key().instanceKey(), remainingForInstance - 1);
                globalRemaining--;
                progressed = true;
            }
        }
        return globalRemaining;
    }

    private static void logSnapshot(Snapshot snapshot) {
        LOGGER.info(
            "[TD-POP] mode={} instances={} encounters={} alive={} ordinary={} protected={} summons={} planned={} pending={} requiredRoles={} shadowGlobalLimit={} shadowPerInstanceLimit={} wouldAdmit={} wouldCompress={} wouldWait={} shadowAdmitted={} unadmittedDemand={}",
            snapshot.mode(),
            snapshot.instances(),
            snapshot.encounters(),
            snapshot.alive(),
            snapshot.ordinaryAlive(),
            snapshot.protectedAlive(),
            snapshot.summonedAlive(),
            snapshot.planned(),
            snapshot.pending(),
            snapshot.requiredRoleSlots(),
            snapshot.shadow().limits().globalAliveLimit(),
            snapshot.shadow().limits().perInstanceAliveLimit(),
            snapshot.shadow().wouldAdmit(),
            snapshot.shadow().wouldCompress(),
            snapshot.shadow().wouldWait(),
            snapshot.shadow().shadowAdmitted(),
            snapshot.shadow().unadmittedDemand()
        );
        for (InstanceUsage usage : snapshot.perInstance()) {
            LOGGER.info(
                "[TD-POP] instance={}/{} encounters={} alive={} ordinary={} protected={} summons={} planned={} pending={} requiredRoles={}",
                usage.instanceKey().instanceId(),
                usage.instanceKey().epoch(),
                usage.encounters(),
                usage.alive(),
                usage.ordinaryAlive(),
                usage.protectedAlive(),
                usage.summonedAlive(),
                usage.planned(),
                usage.pending(),
                usage.requiredRoleSlots()
            );
        }
        for (ShadowDecision decision : snapshot.shadow().decisions()) {
            LOGGER.info(
                "[TD-POP] shadow instance={}/{} controller={} planned={} alive={} requiredRoles={} minimumSafe={} admitted={} unadmitted={} outcome={} reason={}",
                decision.key().instanceKey().instanceId(),
                decision.key().instanceKey().epoch(),
                BlockPos.of(decision.key().controllerPos()),
                decision.planned(),
                decision.alive(),
                decision.requiredRoleSlots(),
                decision.minimumSafe(),
                decision.shadowAdmitted(),
                decision.unadmittedDemand(),
                decision.outcome(),
                decision.reason()
            );
        }
    }

    public record EncounterUsage(
        int alive,
        int ordinaryAlive,
        int protectedAlive,
        int summonedAlive,
        int planned,
        int pending,
        int requiredRoleSlots
    ) {
        public EncounterUsage(
            int alive,
            int ordinaryAlive,
            int protectedAlive,
            int summonedAlive,
            int planned,
            int pending
        ) {
            this(alive, ordinaryAlive, protectedAlive, summonedAlive, planned, pending, 0);
        }

        EncounterUsage sanitized() {
            int safeAlive = Math.max(0, alive);
            int safeProtected = Math.min(safeAlive, Math.max(0, protectedAlive));
            int safeOrdinary = Math.min(safeAlive - safeProtected, Math.max(0, ordinaryAlive));
            int safePlanned = Math.max(0, planned);
            return new EncounterUsage(
                safeAlive,
                safeOrdinary,
                safeProtected,
                Math.min(safeAlive, Math.max(0, summonedAlive)),
                safePlanned,
                Math.min(safePlanned, Math.max(0, pending)),
                Math.min(safePlanned, Math.max(0, requiredRoleSlots))
            );
        }
    }

    public record InstanceUsage(
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        int encounters,
        int alive,
        int ordinaryAlive,
        int protectedAlive,
        int summonedAlive,
        int planned,
        int pending,
        int requiredRoleSlots
    ) {
    }

    public record Snapshot(
        String mode,
        int encounters,
        int instances,
        int alive,
        int ordinaryAlive,
        int protectedAlive,
        int summonedAlive,
        int planned,
        int pending,
        int requiredRoleSlots,
        List<InstanceUsage> perInstance,
        ShadowEvaluation shadow
    ) {
        static Snapshot empty() {
            return new Snapshot(
                MODE,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(),
                ShadowEvaluation.empty(DEFAULT_SHADOW_LIMITS)
            );
        }
    }

    record AdmissionLimits(int globalAliveLimit, int perInstanceAliveLimit) {
        AdmissionLimits sanitized() {
            return new AdmissionLimits(
                Math.max(0, globalAliveLimit),
                Math.max(0, perInstanceAliveLimit)
            );
        }
    }

    enum ReinforcementCandidateKind {
        ORDINARY,
        ELITE,
        BOSS,
        REQUIRED_ROLE,
        SUMMON,
        DUPLICATE_OBJECTIVE_ROLE,
        PEACEFUL_OR_PROTECTED,
        UNKNOWN
    }

    record ReinforcementCandidate(String entityId, ReinforcementCandidateKind kind) {
        ReinforcementCandidate {
            entityId = bounded(entityId == null ? "" : entityId, 96);
            kind = kind == null ? ReinforcementCandidateKind.UNKNOWN : kind;
        }

        static ReinforcementCandidate ordinary(String entityId) {
            return new ReinforcementCandidate(entityId, ReinforcementCandidateKind.ORDINARY);
        }

        private static String bounded(String value, int maximum) {
            return value.length() <= maximum ? value : value.substring(0, maximum);
        }
    }

    record ReinforcementPopulation(int globalAlive, int instanceAlive) {
        ReinforcementPopulation sanitized() {
            return new ReinforcementPopulation(
                Math.max(0, globalAlive),
                Math.max(0, instanceAlive)
            );
        }

        static ReinforcementPopulation empty() {
            return new ReinforcementPopulation(0, 0);
        }
    }

    record ReinforcementProof(
        boolean wouldAdmit,
        int existingReinforcements,
        int projectedReinforcements,
        String reason
    ) {
        static ReinforcementProof rejected(int existingReinforcements, String reason) {
            return new ReinforcementProof(
                false,
                existingReinforcements,
                existingReinforcements,
                reason == null || reason.isBlank() ? "REJECTED" : reason
            );
        }
    }

    enum ShadowOutcome {
        WOULD_ADMIT,
        WOULD_COMPRESS_CANDIDATE,
        WOULD_WAIT
    }

    record ShadowDecision(
        EncounterKey key,
        ShadowOutcome outcome,
        int alive,
        int planned,
        int requiredRoleSlots,
        int minimumSafe,
        int shadowAdmitted,
        int unadmittedDemand,
        String reason
    ) {
    }

    record ShadowEvaluation(
        AdmissionLimits limits,
        int wouldAdmit,
        int wouldCompress,
        int wouldWait,
        int shadowAdmitted,
        int unadmittedDemand,
        List<ShadowDecision> decisions
    ) {
        static ShadowEvaluation empty(AdmissionLimits limits) {
            return new ShadowEvaluation(limits.sanitized(), 0, 0, 0, 0, 0, List.of());
        }
    }

    record EncounterKey(
        String dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        long controllerPos
    ) {
    }

    record EncounterReport(EncounterKey key, EncounterUsage usage) {
    }

    private record TimedUsage(int reportedAtTick, EncounterUsage usage) {
    }

    private static final Comparator<EncounterKey> ENCOUNTER_ORDER = Comparator
        .comparing(EncounterKey::dimension)
        .thenComparing(key -> key.instanceKey().instanceId().toString())
        .thenComparingLong(key -> key.instanceKey().epoch())
        .thenComparingLong(EncounterKey::controllerPos);

    private static final class MutableShadowGrant {
        private final EncounterKey key;
        private final int alive;
        private final int planned;
        private final int requiredRoleSlots;
        private final int minimumSafe;
        private int granted;

        private MutableShadowGrant(EncounterKey key, EncounterUsage usage) {
            this.key = key;
            this.alive = usage.alive();
            this.planned = Math.max(alive, usage.planned());
            this.requiredRoleSlots = Math.min(planned, usage.requiredRoleSlots());
            this.minimumSafe = Math.min(
                planned,
                Math.max(
                    requiredRoleSlots,
                    Math.max(usage.protectedAlive(), (planned + 1) / 2)
                )
            );
            this.granted = alive;
        }

        EncounterKey key() {
            return key;
        }

        int planned() {
            return planned;
        }

        int minimumSafe() {
            return minimumSafe;
        }

        int granted() {
            return granted;
        }

        void admitOne() {
            granted++;
        }

        ShadowDecision freeze() {
            int unadmitted = Math.max(0, planned - granted);
            if (granted >= planned) {
                return new ShadowDecision(
                    key, ShadowOutcome.WOULD_ADMIT, alive, planned, usageRequiredRoleSlots(),
                    minimumSafe,
                    granted, 0, "FULL_NUMERIC_ROSTER_FITS"
                );
            }
            if (granted >= minimumSafe) {
                return new ShadowDecision(
                    key, ShadowOutcome.WOULD_COMPRESS_CANDIDATE, alive, planned,
                    usageRequiredRoleSlots(), minimumSafe,
                    granted, unadmitted, "NUMERIC_MINIMUM_MET_REQUIRED_ROLES_UNVERIFIED"
                );
            }
            return new ShadowDecision(
                key, ShadowOutcome.WOULD_WAIT, alive, planned, usageRequiredRoleSlots(),
                minimumSafe,
                granted, unadmitted, "NUMERIC_MINIMUM_NOT_MET"
            );
        }

        private int usageRequiredRoleSlots() {
            return requiredRoleSlots;
        }
    }

    private static final class State {
        private final Map<EncounterKey, TimedUsage> reports = new HashMap<>();
        private int lastLogTick = Integer.MIN_VALUE;
    }

    private static final class MutableInstanceUsage {
        private int encounters;
        private int alive;
        private int ordinaryAlive;
        private int protectedAlive;
        private int summonedAlive;
        private int planned;
        private int pending;
        private int requiredRoleSlots;

        void add(EncounterUsage usage) {
            encounters++;
            alive += usage.alive();
            ordinaryAlive += usage.ordinaryAlive();
            protectedAlive += usage.protectedAlive();
            summonedAlive += usage.summonedAlive();
            planned += usage.planned();
            pending += usage.pending();
            requiredRoleSlots += usage.requiredRoleSlots();
        }

        InstanceUsage freeze(DungeonInstanceAllocationData.InstanceKey instanceKey) {
            return new InstanceUsage(
                instanceKey,
                encounters,
                alive,
                ordinaryAlive,
                protectedAlive,
                summonedAlive,
                planned,
                pending,
                requiredRoleSlots
            );
        }
    }
}
