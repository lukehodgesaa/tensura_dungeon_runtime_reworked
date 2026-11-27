package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.runtime.DungeonModifierDefinition;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierCatalog;
import com.mineclawd.tensuradungeon.runtime.DungeonBudgetEngine;
import com.mineclawd.tensuradungeon.runtime.DungeonMobStatProfile;
import com.mineclawd.tensuradungeon.runtime.DungeonMobStatScaler;
import com.mineclawd.tensuradungeon.runtime.DungeonPopulationAdmissionController;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.DungeonRunState;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.DungeonSubordinateTransportData;
import com.mineclawd.tensuradungeon.runtime.DungeonSoundHooks;
import com.mineclawd.tensuradungeon.runtime.RoomBudgetResult;
import com.mineclawd.tensuradungeon.runtime.SoulOrbMobIdentity;
import com.mineclawd.tensuradungeon.item.SoulOrbRarity;
import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyDefinition.EncounterClass;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyDefinition;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonBossCatalog;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonBossResolution;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonBossResolver;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinCatalog;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyPoolRegistry;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyPoolDebugInfo;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyPoolResolution;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyRole;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraMobVariantCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraMobWardCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class RoomEncounter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double ARMORED_ARMOR_BONUS = 4.0D;
    private static final double ARMORED_TOUGHNESS_BONUS = 1.0D;
    private static final int REGENERATIVE_INTERVAL_TICKS = 80;
    private static final double REGENERATIVE_HEAL_PERCENT = 0.03D;
    private static final int SUMMONER_INTERVAL_TICKS = 120;
    private static final int SUMMONER_MAX_SELECTED = 2;
    private static final int SUMMONER_MAX_ACTIVE_ADDS_PER_SUMMONER = 2;
    private static final int SUMMONER_MAX_ACTIVE_ADDS_PER_ROOM = 4;
    private static final int SUMMONER_MAX_TOTAL_ADDS_PER_ROOM = 8;
    private static final double SUMMONED_ADD_HEALTH_MULTIPLIER = 0.70D;
    private static final int RIFTBOUND_INTERVAL_TICKS = 160;
    private static final int RIFTBOUND_MAX_ACTIVE_ADDS = 2;
    private static final int RIFTBOUND_MAX_TOTAL_ADDS = 6;
    private static final int EXPLOSIVE_FUSE_TICKS = 30;
    private static final int EXPLOSIVE_MAX_PENDING = 4;
    private static final double EXPLOSIVE_RADIUS = 4.0D;
    private static final int GRAVITY_FLIGHT_TRIGGER_TICKS = 100;
    private static final int GRAVITY_CHANNEL_TICKS = 30;
    private static final int GRAVITY_INCAPACITATION_TICKS = 100;
    private static final int GRAVITY_DEATH_GRACE_TICKS = 240;
    private static final ResourceLocation SWIFT_MOVEMENT_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "swift_movement");
    private static final ResourceLocation SWIFT_ATTACK_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "swift_attack");
    private static final ResourceLocation BERSERKER_ATTACK_ID =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "berserker_attack");
    private static final int TEST_MOBS_PER_COMBAT_ROOM = 15;
    private static final double DUNGEON_AGGRO_RANGE = 512.0D;
    private static final int ENCOUNTER_WAVE_COUNT = 5;
    private static final int WAVE_DELAY_TICKS = 6 * 20;
    private static final int STALE_MOB_THRESHOLD_TICKS = 100;
    private static final int MOB_RESET_THRESHOLD_TICKS = 100;
    private static final int MOB_RESET_COOLDOWN_TICKS = 100;
    private static final int MOB_RESET_DISTANCE_SQR = 30 * 30;
    private static final int VERTICAL_STRANDED_MIN_GAP = 8;
    private static final int VERTICAL_STRANDED_SECONDS = 60;
    private static final int VERTICAL_RECOVERY_COOLDOWN_SECONDS = 30;
    private static final int DISENGAGED_RECOVERY_SECONDS = 90;
    private static final double MEANINGFUL_CONTACT_DISTANCE_SQR = 12.0D * 12.0D;
    private static final int MAX_CONSECUTIVE_EMPTY_WAVES = 5;
    private static final long ENCOUNTER_DIAGNOSTIC_INTERVAL_TICKS = 30L * 20L;
    private static final String INVALID_KIN_PROOF_FAMILY = "__invalid_kin_proof__";

    private final BlockPos controllerPos;
    private final RoomMarkerState markerState;
    private final RoomLifecycle lifecycle;
    private final RoomObjective objective;
    private final Predicate<BlockState> runtimeBarrierState;
    private final Supplier<DungeonInstanceAllocationData.InstanceKey> instanceKey;
    private final DungeonEncounterModifierRuntime modifierRuntime = new DungeonEncounterModifierRuntime();

    boolean hadAnyMobConfirmedRemoved;
    final Set<UUID> trackedMobUuids = new HashSet<>();
    final Set<UUID> summonerMobUuids = new HashSet<>();
    final Set<UUID> summonedMobUuids = new HashSet<>();
    final Map<UUID, UUID> summonedMobOwners = new HashMap<>();
    final Map<UUID, Integer> missingMobTicks = new HashMap<>();
    final Map<UUID, Integer> mobOutOfBoundsTicks = new HashMap<>();
    final Map<UUID, Integer> mobResetCooldownTicks = new HashMap<>();
    UUID eliteMobUuid;
    boolean eliteDefeated;
    UUID bossMobUuid;
    boolean bossSpawnAttempted;
    int regenerativePulseProgressTicks;
    String lastRegenModifierName = "NONE";
    int lastRegenPulseTick = -1;
    int lastRegenPulseHealedCount;
    double lastRegenPulseTotalHealthRestored;
    String lastMoltenModifierName = "NONE";
    int lastMoltenRetaliationTick = -1;
    String lastMoltenRetaliationTarget = "NONE";
    int totalMoltenRetaliations;
    int moltenDamageEventsSeen;
    int moltenDamageEventsWithLivingAttacker;
    int moltenDamageEventsWithTrackedTarget;
    String lastMoltenRejectionReason = "NONE";
    int summonerPulseProgressTicks;
    String lastSummonerModifierName = "NONE";
    int lastSummonPulseTick = -1;
    int totalSummonsThisRoom;
    String lastSummonResult = "NONE";
    String lastSummonRejectionReason = "NONE";
    String lastSummonerEntityType = "NONE";
    String lastSummonedEntityType = "NONE";
    boolean enemyPoolRegistryEnabled;
    String lastEnemyPoolSpawnSource = "NONE";
    String lastEnemyPoolRoleRequested = "NONE";
    String lastEnemyPoolEntitySelected = "NONE";
    String lastEnemyPoolFamily = "NONE";
    String lastEnemyPoolSourceMod = "NONE";
    String lastEnemyPoolFallbackReason = "NONE";
    int lastEnemyPoolCandidateCount;
    String lastEnemyPoolError = "NONE";

    int lastSpawnRequestedCount;
    int lastSpawnActualCount;
    String lastSpawnSkippedReason = "NONE";
    String lastSpawnStrategy = "GENERIC_ONLY";
    int lastSpawnMeleeUsedCount;
    int lastSpawnRangedUsedCount;
    int lastSpawnFlankUsedCount;
    int lastSpawnHighgroundUsedCount;
    int lastSpawnGenericUsedCount;
    String lastSpawnModifierSummary = "NONE";
    double lastSpawnArmorValue;
    double lastSpawnArmorToughnessValue;
    String lastSpawnFloorScalingSummary = "NONE";
    int lastCompositionBudget;
    int lastCompositionSpent;
    String lastCompositionSummary = "NONE";
    int encounterTargetCount;
    int waveSize;
    int wavesSpawned;
    int waveCooldownTicks;
    private int consecutiveEmptyWaves;
    private long lastEncounterDiagnosticGameTime = Long.MIN_VALUE;
    private final Map<UUID, Integer> verticalStrandedSeconds = new HashMap<>();
    private final Map<UUID, Integer> lastVerticalGap = new HashMap<>();
    private final Map<UUID, Integer> verticalRecoveryCooldownSeconds = new HashMap<>();
    private final Map<UUID, Integer> disengagedSeconds = new HashMap<>();
    private final List<PendingBlast> pendingBlasts = new ArrayList<>();
    private UUID riftboundCarrierUuid;
    private int riftboundPulseTicks;
    private int riftboundTotalSummons;
    private final Map<UUID, Integer> gravityFlightTicks = new HashMap<>();
    private final Map<UUID, Integer> gravityGraceTicks = new HashMap<>();
    private final Map<UUID, Integer> gravityHeavyTicks = new HashMap<>();
    private final Map<UUID, UUID> gravityHunterByPlayer = new HashMap<>();
    private final Map<UUID, UUID> gravityPlayerByHunter = new HashMap<>();
    private final Map<UUID, Integer> gravityChannelTicks = new HashMap<>();
    private int totalVerticalRecoveries;
    private String lastVerticalRecovery = "NONE";
    private boolean recoveryChanged;
    private String pendingActivationFailure = "NONE";
    RoomEncounter(
        BlockPos controllerPos,
        RoomMarkerState markerState,
        RoomLifecycle lifecycle,
        RoomObjective objective,
        Predicate<BlockState> runtimeBarrierState
    ) {
        this(controllerPos, markerState, lifecycle, objective, runtimeBarrierState, () -> null);
    }

    RoomEncounter(
        BlockPos controllerPos,
        RoomMarkerState markerState,
        RoomLifecycle lifecycle,
        RoomObjective objective,
        Predicate<BlockState> runtimeBarrierState,
        Supplier<DungeonInstanceAllocationData.InstanceKey> instanceKey
    ) {
        this.controllerPos = controllerPos.immutable();
        this.markerState = markerState;
        this.lifecycle = lifecycle;
        this.objective = objective;
        this.runtimeBarrierState = runtimeBarrierState;
        this.instanceKey = instanceKey == null ? () -> null : instanceKey;
    }

    void clearTrackingCollections() {
        trackedMobUuids.clear();
        summonerMobUuids.clear();
        summonedMobUuids.clear();
        summonedMobOwners.clear();
        clearRecoveryTracking();
        bossMobUuid = null;
        bossSpawnAttempted = false;
        resetCombatProgressionState();
    }

    void clearRecoveryTracking() {
        missingMobTicks.clear();
        mobOutOfBoundsTicks.clear();
        mobResetCooldownTicks.clear();
    }

    void resetRemovalEvidence() {
        hadAnyMobConfirmedRemoved = false;
    }

    void prepareForActivation() {
        resetRemovalEvidence();
        trackedMobUuids.clear();
        clearRecoveryTracking();
        eliteMobUuid = null;
        eliteDefeated = false;
        bossMobUuid = null;
        bossSpawnAttempted = false;
        resetSpawnDebugState();
        resetRegenerativeState();
        resetMoltenState();
        resetSummonerState();
        resetCombatProgressionState();
    }

    void clearEliteTarget() {
        eliteMobUuid = null;
        eliteDefeated = false;
    }

    void selectKillEliteTarget(ServerLevel level) {
        eliteMobUuid = objective.selectKillEliteTarget(
            level, eliteMobUuid, Set.copyOf(trackedMobUuids), Set.copyOf(summonedMobUuids)
        );
        eliteDefeated = false;
    }

    void markEliteDefeated() {
        eliteDefeated = true;
    }

    void clearMissingMobTracking() {
        missingMobTicks.clear();
    }

    void resetSpawnDebugState() {
        lastSpawnRequestedCount = 0;
        lastSpawnActualCount = 0;
        lastSpawnSkippedReason = "NONE";
        lastSpawnStrategy = "GENERIC_ONLY";
        lastSpawnMeleeUsedCount = 0;
        lastSpawnRangedUsedCount = 0;
        lastSpawnFlankUsedCount = 0;
        lastSpawnHighgroundUsedCount = 0;
        lastSpawnGenericUsedCount = 0;
        lastSpawnModifierSummary = "NONE";
        lastSpawnArmorValue = 0.0D;
        lastSpawnArmorToughnessValue = 0.0D;
        lastSpawnFloorScalingSummary = "NONE";
        lastCompositionBudget = 0;
        lastCompositionSpent = 0;
        lastCompositionSummary = "NONE";
        encounterTargetCount = 0;
        waveSize = 0;
        wavesSpawned = 0;
        waveCooldownTicks = 0;
        consecutiveEmptyWaves = 0;
        lastEncounterDiagnosticGameTime = Long.MIN_VALUE;
        verticalStrandedSeconds.clear();
        lastVerticalGap.clear();
        verticalRecoveryCooldownSeconds.clear();
        disengagedSeconds.clear();
        totalVerticalRecoveries = 0;
        lastVerticalRecovery = "NONE";
    }

    Effect spawnZombies(ServerLevel level) {
        RoomBudgetResult budgetResult = roomBudgetResult(level);
        lastSpawnRequestedCount = TEST_MOBS_PER_COMBAT_ROOM;
        if (requiresEliteAnchor(level) && !markerState.eliteSpawnAnchors.isEmpty()) {
            lastSpawnRequestedCount -= 1;
        }
        if (budgetResult.getModifierMobCountMultiplier() > 1.0D) {
            lastSpawnRequestedCount = Math.max(lastSpawnRequestedCount, (int) Math.round(lastSpawnRequestedCount * budgetResult.getModifierMobCountMultiplier()));
        }
        lastSpawnActualCount = 0;
        lastSpawnSkippedReason = "NONE";
        lastSpawnMeleeUsedCount = 0;
        lastSpawnRangedUsedCount = 0;
        lastSpawnFlankUsedCount = 0;
        lastSpawnHighgroundUsedCount = 0;
        lastSpawnGenericUsedCount = 0;
        lastSpawnModifierSummary = "NONE";
        lastSpawnArmorValue = 0.0D;
        lastSpawnArmorToughnessValue = 0.0D;
        lastSpawnFloorScalingSummary = "NONE";
        lastCompositionBudget = Math.max(lastSpawnRequestedCount, budgetResult.getRoomTotalBudget());
        lastCompositionSpent = 0;
        lastCompositionSummary = "NONE";
        lastSpawnStrategy = getSpawnStrategyName();
        encounterTargetCount = Math.min(lastSpawnRequestedCount, budgetResult.getMaxTotal());
        waveSize = Math.max(1, (encounterTargetCount + ENCOUNTER_WAVE_COUNT - 1) / ENCOUNTER_WAVE_COUNT);
        wavesSpawned = 0;
        waveCooldownTicks = 0;
        consecutiveEmptyWaves = 0;
        lastEncounterDiagnosticGameTime = Long.MIN_VALUE;
        resetEnemyPoolDebug();
        if (!profileAllowsCombat()) {
            lastSpawnSkippedReason = "SAFE_ROOM_PROFILE";
            encounterTargetCount = 0;
            return Effect.NONE;
        }
        if (encounterTargetCount <= 0) {
            lastSpawnSkippedReason = "PROFILE_REQUESTED_ZERO";
            return Effect.NONE;
        }
        if (markerState.ownedNormalCombatAnchorCount() <= 0) {
            lastSpawnSkippedReason = "NO_OWNED_COMBAT_SPAWN_ANCHORS";
            encounterTargetCount = 0;
            return Effect.NONE;
        }

        return spawnNextWave(level);
    }

    boolean requiresEliteAnchor(ServerLevel level) {
        return objective.activeObjectiveType.usesEliteTarget()
            || hasActiveModifier(level, DungeonModifierDefinition.ELITE_VANGUARD);
    }

    Effect tickEncounterWaves(ServerLevel level) {
        if (!hasPendingWaves()) {
            return Effect.NONE;
        }
        if (waveCooldownTicks > 0) {
            waveCooldownTicks--;
            return Effect.NONE;
        }
        return spawnNextWave(level);
    }

    boolean hasPendingWaves() {
        return lastSpawnActualCount < encounterTargetCount;
    }

    private Effect spawnNextWave(ServerLevel level) {
        if (wavesSpawned > 0) {
            DungeonSoundHooks.waveIncoming(level, controllerPos);
        }
        RoomBudgetResult budgetResult = roomBudgetResult(level);
        int availableAliveSlots = Math.max(0, budgetResult.getMaxAlive() - currentAliveTrackedMobCount(level));
        int remainingTotal = Math.max(0, encounterTargetCount - lastSpawnActualCount);
        int spawnCount = Math.min(waveSize, Math.min(availableAliveSlots, remainingTotal));
        spawnCount = Math.min(spawnCount, Math.max(0, budgetResult.getMaxTotal() - lastSpawnActualCount));
        if (spawnCount <= 0) {
            lastSpawnSkippedReason = "WAVE_WAITING_FOR_ALIVE_CAP";
            waveCooldownTicks = 20;
            return Effect.NONE;
        }

        List<SpawnAnchorChoice> preferredAnchors = buildWaveSpawnAnchors(level);
        if (preferredAnchors.isEmpty()) {
            lastSpawnSkippedReason = "NO_OWNED_COMBAT_SPAWN_ANCHORS";
            waveCooldownTicks = 20;
            return Effect.NONE;
        }

        int waveStartOffset = wavesSpawned % preferredAnchors.size();
        int attempts = spawnCount;
        int actualBeforeWave = lastSpawnActualCount;
        for (int i = 0; i < attempts; i++) {
            SpawnAnchorChoice choice = preferredAnchors.get((waveStartOffset + i) % preferredAnchors.size());
            BlockPos spawnPos = findSpawnPos(level, choice.anchor(), i + 1);
            int remainingAfterCurrent = Math.max(0, encounterTargetCount - lastSpawnActualCount - 1);
            int eliteReserve = objective.activeObjectiveType.usesEliteTarget() && eliteMobUuid == null && !markerState.eliteSpawnAnchors.isEmpty() ? 5 : 0;
            int maxCurrentCost = Math.max(1, lastCompositionBudget - lastCompositionSpent - eliteReserve - remainingAfterCurrent);
            SpawnedMob spawned = createMobForChoice(level, choice, spawnPos, maxCurrentCost);
            if (spawned == null) {
                continue;
            }
            Mob mob = spawned.mob();
            trackedMobUuids.add(mob.getUUID());
            lastCompositionSpent += spawned.definition() == null ? 1 : spawned.definition().compositionCost();
            lastSpawnActualCount++;
            incrementLastSpawnRoleCount(choice.role());
        }

        wavesSpawned++;
        waveCooldownTicks = hasPendingWaves() ? WAVE_DELAY_TICKS : 0;
        lastCompositionSummary = lastCompositionSpent + " / " + lastCompositionBudget + " points for " + lastSpawnActualCount + " / " + encounterTargetCount + " enemies";
        if (lastSpawnActualCount == 0) {
            lastSpawnSkippedReason = "SPAWN_CREATION_FAILED";
        } else if (hasPendingWaves()) {
            lastSpawnSkippedReason = "WAVES_PENDING";
        } else {
            lastSpawnSkippedReason = "NONE";
        }
        int addedThisWave = lastSpawnActualCount - actualBeforeWave;
        LOGGER.info("Dungeon encounter wave: controller={} wave={} requested={} added={} actual={} target={} alive={} pending={} reason={} anchors={}",
            controllerPos, wavesSpawned, attempts, addedThisWave, lastSpawnActualCount, encounterTargetCount,
            currentAliveTrackedMobCount(level), getPendingEnemyCount(), lastSpawnSkippedReason,
            markerState.ownedNormalCombatAnchorCount());
        String failureReason = "NONE";
        if (addedThisWave <= 0) {
            consecutiveEmptyWaves++;
            if (consecutiveEmptyWaves >= MAX_CONSECUTIVE_EMPTY_WAVES) {
                LOGGER.error("Dungeon encounter failed after repeated empty waves: controller={} attempts={} actual={} target={} reason={} poolSource={} poolError={}",
                    controllerPos, consecutiveEmptyWaves, lastSpawnActualCount, encounterTargetCount,
                    lastSpawnSkippedReason, lastEnemyPoolSpawnSource, lastEnemyPoolError);
                failureReason = "REPEATED_EMPTY_SPAWN_WAVES";
            }
        } else {
            consecutiveEmptyWaves = 0;
        }
        return new Effect(true, failureReason, "NONE");
    }

    private List<SpawnAnchorChoice> buildWaveSpawnAnchors(ServerLevel level) {
        List<SpawnAnchorChoice> anchors = buildPreferredSpawnAnchors();
        if (hasActiveModifier(level, DungeonModifierDefinition.SHADOWED)) {
            List<SpawnAnchorChoice> flank = anchors.stream()
                .filter(choice -> choice.role() == SpawnRole.FLANK)
                .toList();
            int bonus = (int) Math.ceil(flank.size() * 0.45D);
            for (int index = 0; index < bonus && !flank.isEmpty(); index++) {
                anchors.add(flank.get(index % flank.size()));
            }
        }
        ServerPlayer player = nearestActivePlayer(level);
        if (player == null || anchors.size() < 2) {
            return anchors;
        }
        anchors.sort((left, right) -> Integer.compare(
            spawnConcealmentScore(level, player, right.anchor()),
            spawnConcealmentScore(level, player, left.anchor())
        ));
        return anchors;
    }

    private ServerPlayer nearestActivePlayer(ServerLevel level) {
        ServerPlayer nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (markerState.hasBounds() && !markerState.isInsideBounds(player.blockPosition())) {
                continue;
            }
            double distance = player.distanceToSqr(Vec3.atCenterOf(controllerPos));
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private int spawnConcealmentScore(ServerLevel level, ServerPlayer player, BlockPos anchor) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(spawnBase(anchor));
        Vec3 direction = target.subtract(eye);
        double dot = direction.lengthSqr() <= 0.0001D ? 1.0D : direction.normalize().dot(player.getLookAngle());
        if (dot < -0.15D) {
            return 300;
        }
        HitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(eye) + 0.25D < target.distanceToSqr(eye)) {
            return 200;
        }
        return 100;
    }
    void spawnElite(ServerLevel level) {
        List<BlockPos> eliteAnchors = markerState.eliteSpawnAnchors;
        if (eliteAnchors.isEmpty()) {
            return;
        }
        if (currentAliveTrackedMobCount(level) >= roomBudgetResult(level).getMaxAlive()) {
            lastSpawnSkippedReason = "MAX_ALIVE_CAP_BLOCKED_ELITE";
            return;
        }

        BlockPos anchor = eliteAnchors.getFirst();
        BlockPos spawnPos = findSpawnPos(level, anchor, 0);
        SpawnedMob spawned = createMobForChoice(level, new SpawnAnchorChoice(anchor, SpawnRole.ELITE), spawnPos, Math.max(1, lastCompositionBudget - lastCompositionSpent));
        if (spawned == null) {
            lastSpawnSkippedReason = "SPAWN_CREATION_FAILED";
            return;
        }
        Mob elite = spawned.mob();
        lastCompositionSpent += spawned.definition() == null ? 1 : spawned.definition().compositionCost();
        lastCompositionSummary = lastCompositionSpent + " / " + lastCompositionBudget + " points for " + (lastSpawnActualCount + 1) + " enemies";

        elite.setCustomName(Component.literal("Elite " + elite.getType().getDescription().getString()));
        elite.setCustomNameVisible(true);
        elite.setPersistenceRequired();
        DungeonMobStatScaler.applyEliteBoost(elite);
        eliteMobUuid = elite.getUUID();
        trackedMobUuids.add(eliteMobUuid);
    }

    Effect spawnBossIfAuthorized(ServerLevel level) {
        if (markerState.bossSpawnAnchors.isEmpty()) {
            return Effect.NONE;
        }
        if (markerState.bossSpawnAnchors.size() != 1) {
            lastSpawnSkippedReason = "BOSS_ANCHOR_COUNT_INVALID";
            return Effect.CHANGED;
        }
        if (bossSpawnAttempted) {
            lastSpawnSkippedReason = bossMobUuid == null
                ? "BOSS_DISPATCH_ALREADY_ATTEMPTED"
                : "BOSS_ALREADY_DISPATCHED";
            return Effect.CHANGED;
        }

        DungeonRunManager manager = DungeonRunManager.get(level);
        boolean affinityActive = manager.getActiveModifierIds(instanceKey.get()).contains("boss-affinity");

        DungeonRunState.PreparedBossState preparedBoss =
            manager.getPreparedBossState(instanceKey.get());
        if (!preparedBoss.dispatchEligible()) {
            lastSpawnSkippedReason = "BOSS_PLAN_NOT_DISPATCH_ELIGIBLE";
            return Effect.CHANGED;
        }
        if (!preparedBoss.bossSlotAvailable()) {
            lastSpawnSkippedReason = "BOSS_SLOT_NOT_AVAILABLE";
            return Effect.CHANGED;
        }
        if (!preparedBoss.selected()) {
            lastSpawnSkippedReason = "BOSS_RESOLUTION_NOT_SELECTED";
            return Effect.CHANGED;
        }
        DungeonBossResolution currentFloorResolution = DungeonBossResolver.resolveNormal(
            manager.getFloorNumber(instanceKey.get()),
            0L
        );
        if (!currentFloorResolution.bossSlotAvailable()
            || currentFloorResolution.milestoneBoss() != preparedBoss.milestoneBoss()) {
            lastSpawnSkippedReason = "BOSS_RESOLUTION_FLOOR_MILESTONE_MISMATCH";
            return Effect.CHANGED;
        }
        boolean affinityMatchSelected = "AFFINITY_MATCH_SELECTED".equals(preparedBoss.resolutionReason());
        boolean neutralFallback = "AFFINITY_NO_MATCH_NEUTRAL_FALLBACK".equals(
            preparedBoss.resolutionReason()
        );
        boolean neutralSelected = "NEUTRAL_BOSS_SELECTED".equals(preparedBoss.resolutionReason());
        boolean affinityRequested = !preparedBoss.affinityKinGroup().isBlank()
            || !preparedBoss.affinityTheme().isBlank();
        if (preparedBoss.catalogVersion() != DungeonBossCatalog.CATALOG_VERSION
            || (affinityActive
                ? (affinityRequested
                    ? (!affinityMatchSelected && !neutralFallback)
                    : !neutralSelected)
                : !neutralSelected)
            || preparedBoss.candidateIds().isEmpty()
            || preparedBoss.candidateIds().stream()
                .anyMatch(candidateId -> DungeonBossCatalog.forEntity(candidateId).isEmpty())
            || preparedBoss.matchingCandidateIds().stream()
                .anyMatch(candidateId -> !preparedBoss.candidateIds().contains(candidateId))
            || !preparedBoss.candidateIds().contains(preparedBoss.selectedBossId())
            || (affinityMatchSelected && (
                !preparedBoss.matchingCandidateIds().contains(preparedBoss.selectedBossId())
                || !DungeonBossCatalog.forEntity(preparedBoss.selectedBossId())
                    .map(entry -> entry.matches(preparedBoss.affinityKinGroup(), preparedBoss.affinityTheme()))
                    .orElse(false)
            ))
            || (neutralFallback && (
                (preparedBoss.affinityKinGroup().isBlank() && preparedBoss.affinityTheme().isBlank())
                || !preparedBoss.matchingCandidateIds().isEmpty()
                || preparedBoss.candidateIds().stream()
                    .anyMatch(candidateId -> DungeonBossCatalog.forEntity(candidateId)
                        .map(entry -> entry.matches(
                            preparedBoss.affinityKinGroup(), preparedBoss.affinityTheme()
                        ))
                        .orElse(false))
            ))) {
            lastSpawnSkippedReason = "BOSS_RESOLUTION_PROOF_INVALID";
            return Effect.CHANGED;
        }

        String selectedBossId = preparedBoss.selectedBossId();
        DungeonEnemyDefinition definition = DungeonEnemyPoolRegistry
            .eligibleAuthoredBossDefinition(
                selectedBossId,
                manager.getFloorNumber(instanceKey.get()),
                preparedBoss.milestoneBoss()
            )
            .orElse(null);
        if (definition == null) {
            lastSpawnSkippedReason = "BOSS_SELECTED_ID_NOT_ELIGIBLE";
            return Effect.CHANGED;
        }
        EntityType<?> selectedType = DungeonEnemyPoolRegistry
            .registeredEntityType(selectedBossId)
            .orElse(null);
        if (selectedType == null) {
            lastSpawnSkippedReason = "BOSS_ENTITY_TYPE_UNREGISTERED";
            return Effect.CHANGED;
        }

        BlockPos spawnPos = findSafeSpawnPos(level, markerState.bossSpawnAnchors.getFirst(), 0);
        if (spawnPos == null) {
            lastSpawnSkippedReason = "BOSS_SPAWN_POSITION_INVALID";
            return Effect.CHANGED;
        }

        bossSpawnAttempted = true;
        Entity entity = selectedType.create(level);
        if (!(entity instanceof Mob boss)) {
            lastSpawnSkippedReason = "BOSS_ENTITY_NOT_MOB";
            return Effect.CHANGED;
        }
        boss.moveTo(
            spawnPos.getX() + 0.5D,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5D,
            level.random.nextFloat() * 360.0F,
            0.0F
        );
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.TRIGGERED, null);
        configureDungeonAggro(level, boss);
        boss.setPersistenceRequired();
        SoulOrbMobIdentity.stamp(
            boss,
            controllerPos,
            manager.getRunSessionId(instanceKey.get()),
            SoulOrbRarity.fromEnemyRank(definition.rankLabel()),
            EncounterClass.BOSS
        );
        if (!level.addFreshEntity(boss)) {
            boss.discard();
            lastSpawnSkippedReason = "BOSS_ENTITY_ADMISSION_REJECTED";
            return Effect.CHANGED;
        }
        bossMobUuid = boss.getUUID();
        trackedMobUuids.add(bossMobUuid);
        lastSpawnSkippedReason = "BOSS_DISPATCHED";
        return Effect.CHANGED;
    }

    private SpawnedMob createMobForChoice(ServerLevel level, SpawnAnchorChoice choice, BlockPos spawnPos, int maxCompositionCost) {
        DungeonEnemyPoolResolution resolution = resolveEnemyPool(level, choice, maxCompositionCost);
        EntityType<?> selectedType = resolution == null ? null : resolution.entityType();
        DungeonEnemyDefinition definition = resolution == null ? null : resolution.definition();
        String resolutionSpawnSource = resolution == null ? "NONE" : resolution.debugInfo().spawnSource();
        String fallbackReason = resolution == null ? "REGISTRY_RESOLUTION_FAILED" : resolution.debugInfo().fallbackReason();
        String fallbackError = resolution == null ? "RESOLUTION_WAS_NULL" : resolution.debugInfo().error();

        if (selectedType != null) {
            Entity entity = selectedType.create(level);
            if (entity instanceof Mob mob) {
                if ("ENEMY_POOL".equals(resolutionSpawnSource) && definition != null) {
                    lastEnemyPoolSpawnSource = "ENEMY_POOL";
                    lastEnemyPoolRoleRequested = choice.role() == SpawnRole.ELITE ? DungeonEnemyRole.ELITE_CANDIDATE.name() : lastEnemyPoolRoleRequested;
                    lastEnemyPoolEntitySelected = definition.entityTypeId();
                    lastEnemyPoolFamily = definition.family().id();
                    lastEnemyPoolSourceMod = definition.sourceModId();
                    lastEnemyPoolFallbackReason = "NONE";
                    lastEnemyPoolError = "NONE";
                } else {
                    recordEnemyPoolFallback(
                        fallbackReason,
                        selectedEntityIdForFallback(definition, selectedType),
                        selectedFamilyIdForFallback(definition, selectedType),
                        selectedSourceModForFallback(definition, selectedType),
                        fallbackError
                    );
                }
                mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.TRIGGERED, null);
                TensuraMobVariantCompat.applySpawnVariant(
                    mob,
                    Math.max(1, DungeonRunManager.get(level).getFloorNumber(instanceKey.get())),
                    variantThemeTags(level),
                    level.random
                );
                applySpawnModifiers(level, mob);
                configureDungeonAggro(level, mob);
                mob.setPersistenceRequired();
                SoulOrbMobIdentity.stamp(
                    mob,
                    controllerPos,
                    DungeonRunManager.get(level).getRunSessionId(instanceKey.get()),
                    SoulOrbRarity.fromEnemyRank(definition == null ? "" : definition.rankLabel()),
                    choice.role() == SpawnRole.ELITE ? EncounterClass.ELITE : EncounterClass.ORDINARY
                );
                if (!level.addFreshEntity(mob)) {
                    recordEnemyPoolFallback(
                        "ADD_FRESH_ENTITY_REJECTED",
                        selectedEntityIdForFallback(definition, selectedType),
                        selectedFamilyIdForFallback(definition, selectedType),
                        selectedSourceModForFallback(definition, selectedType),
                        "LEVEL_REJECTED_ENTITY"
                    );
                    return null;
                }
                return new SpawnedMob(mob, definition);
            }
            if (entity == null) {
                fallbackReason = "ENTITY_CREATE_RETURNED_NULL";
                fallbackError = "ENTITY_CREATE_RETURNED_NULL";
            } else if (!(entity instanceof Mob)) {
                fallbackReason = "ENTITY_CREATE_NOT_MOB";
                fallbackError = entity.getClass().getSimpleName();
            } else {
                fallbackReason = "ENTITY_CREATE_NOT_MONSTER";
                fallbackError = entity.getClass().getSimpleName();
            }
        } else if ("NONE".equals(fallbackReason)) {
            fallbackReason = "INVALID_ENTITY_TYPE";
            fallbackError = "ENTITY_TYPE_MISSING";
        }

        recordEnemyPoolFallback(
            fallbackReason,
            selectedEntityIdForFallback(definition, selectedType),
            selectedFamilyIdForFallback(definition, selectedType),
            selectedSourceModForFallback(definition, selectedType),
            fallbackError
        );
        return null;
    }
    private Set<String> variantThemeTags(ServerLevel level) {
        Set<String> tags = new HashSet<>();
        tags.add(lifecycle.snapshot().profile().name().toLowerCase(Locale.ROOT));
        for (DungeonModifierDefinition modifier
            : DungeonRunManager.get(level).getActiveModifiers(instanceKey.get())) {
            tags.add(modifier.name().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(tags);
    }
    private DungeonEnemyPoolResolution resolveEnemyPool(ServerLevel level, SpawnAnchorChoice choice, int maxCompositionCost) {
        DungeonRunManager manager = DungeonRunManager.get(level);
        Set<String> activeModifierIds = manager.getActiveModifierIds(instanceKey.get());
        String kinGroup = activeModifierIds.stream()
            .map(DungeonEnemyKinCatalog::groupForModifierId)
            .filter(group -> !group.isBlank())
            .findFirst()
            .orElse("");
        if (kinGroup.isBlank() && activeModifierIds.contains("kin")) {
            DungeonRunState.PreparedKinState preparedKin = manager.getPreparedKinState(instanceKey.get());
            kinGroup = preparedKin.selected() ? preparedKin.kinGroup() : INVALID_KIN_PROOF_FAMILY;
        }
        DungeonEnemyPoolResolution resolution = DungeonEnemyPoolRegistry.resolve(
            level.random,
            Math.max(1, manager.getFloorNumber(instanceKey.get())),
            lifecycle.snapshot().profile(),
            mapSpawnRole(choice.role()),
            manager.getActiveModifiers(instanceKey.get()),
            false,
            maxCompositionCost,
            kinGroup
        );
        applyEnemyPoolDebug(resolution.debugInfo());
        return resolution;
    }

    private String selectedEntityIdForFallback(DungeonEnemyDefinition definition, EntityType<?> selectedType) {
        if (definition != null) {
            return definition.entityTypeId();
        }
        return entityTypeRegistryName(selectedType);
    }

    private String selectedFamilyIdForFallback(DungeonEnemyDefinition definition, EntityType<?> selectedType) {
        if (definition != null) {
            return definition.family().id();
        }
        return inferFallbackFamily(selectedType);
    }

    private String selectedSourceModForFallback(DungeonEnemyDefinition definition, EntityType<?> selectedType) {
        if (definition != null) {
            return definition.sourceModId();
        }
        return namespaceForEntityType(selectedType);
    }

    private DungeonEnemyRole mapSpawnRole(SpawnRole role) {
        return switch (role) {
            case RANGED -> DungeonEnemyRole.RANGED;
            case FLANK -> DungeonEnemyRole.FLANK;
            case HIGHGROUND -> DungeonEnemyRole.HIGHGROUND;
            case ELITE -> DungeonEnemyRole.ELITE_CANDIDATE;
            default -> DungeonEnemyRole.MELEE;
        };
    }

    private void recordEnemyPoolFallback(String reason, String selectedEntityId, String familyId, String sourceModId, String error) {
        lastEnemyPoolSpawnSource = "FALLBACK_OLD_SPAWN";
        lastEnemyPoolEntitySelected = selectedEntityId;
        lastEnemyPoolFamily = familyId;
        lastEnemyPoolSourceMod = sourceModId;
        lastEnemyPoolFallbackReason = reason;
        lastEnemyPoolError = error;
    }

    private String entityTypeRegistryName(EntityType<?> type) {
        return type == null ? "NONE" : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    private String namespaceForEntityType(EntityType<?> type) {
        return type == null ? "NONE" : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace();
    }

    private String inferFallbackFamily(EntityType<?> type) {
        if (type == EntityType.ZOMBIE || type == EntityType.SKELETON || type == EntityType.HUSK || type == EntityType.STRAY) {
            return "undead";
        }
        if (type == EntityType.SPIDER) {
            return "arachnid";
        }
        if (type == EntityType.VINDICATOR || type == EntityType.PILLAGER || type == EntityType.EVOKER) {
            return "illager";
        }
        if (type == EntityType.WITCH) {
            return "caster";
        }
        return "fallback";
    }

    BlockPos findSummonSpawnPos(ServerLevel level, BlockPos summonerPos) {
        for (BlockPos anchor : markerState.spawnAnchors) {
            BlockPos pos = findSafeSpawnPos(level, anchor, 1);
            if (pos != null) {
                return pos;
            }
        }
        for (BlockPos anchor : markerState.flankSpawnAnchors) {
            BlockPos pos = findSafeSpawnPos(level, anchor, 1);
            if (pos != null) {
                return pos;
            }
        }
        BlockPos[] nearby = new BlockPos[] {
            summonerPos.east(), summonerPos.west(), summonerPos.north(), summonerPos.south(),
            summonerPos.east().north(), summonerPos.west().south(), summonerPos.above()
        };
        for (BlockPos candidate : nearby) {
            if (isValidSummonSpawnPos(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private BlockPos findSpawnPos(ServerLevel level, BlockPos anchor, int index) {
        int offset = Math.max(1, Math.min(index, 2));
        BlockPos base = spawnBase(anchor);
        BlockPos[] candidates = new BlockPos[] { base, base.east(offset), base.west(offset), base.north(offset), base.south(offset), base.east(offset).north(offset), base.west(offset).south(offset) };
        for (BlockPos candidate : candidates) {
            if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return base;
    }

    private BlockPos findSafeSpawnPos(ServerLevel level, BlockPos anchor, int index) {
        int offset = Math.max(1, Math.min(index, 2));
        BlockPos base = spawnBase(anchor);
        BlockPos[] candidates = new BlockPos[] { base, base.east(offset), base.west(offset), base.north(offset), base.south(offset), base.east(offset).north(offset), base.west(offset).south(offset) };
        for (BlockPos candidate : candidates) {
            if (isValidSummonSpawnPos(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private BlockPos spawnBase(BlockPos anchor) {
        return markerState.bakedMarkerMode ? anchor : anchor.above();
    }

    private boolean isValidSummonSpawnPos(ServerLevel level, BlockPos candidate) {
        if (candidate == null) {
            return false;
        }
        if (markerState.hasBounds() && !markerState.isInsideBounds(candidate)) {
            return false;
        }
        return level.getBlockState(candidate).isAir()
            && level.getBlockState(candidate.above()).isAir()
            && !level.getBlockState(candidate.below()).is(Blocks.LAVA)
            && !level.getBlockState(candidate).is(Blocks.LAVA)
            && !runtimeBarrierState.test(level.getBlockState(candidate))
            && !runtimeBarrierState.test(level.getBlockState(candidate.below()));
    }

    Effect refreshAggroTargets(ServerLevel level) {
        recoveryChanged = false;
        for (UUID uuid : trackedMobUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved()) {
                UUID assignedPlayerId = gravityPlayerByHunter.get(uuid);
                ServerPlayer assignedPlayer = assignedPlayerId == null
                    ? null
                    : level.getServer().getPlayerList().getPlayer(assignedPlayerId);
                if (assignedPlayer != null && assignedPlayer.serverLevel() == level) {
                    mob.setTarget(assignedPlayer);
                } else {
                    configureDungeonAggro(level, mob);
                }
                if (hasActiveModifier(level, DungeonModifierDefinition.BERSERKER)
                    && !isBossType(mob)) {
                    updateBerserkerAttackSpeed(mob);
                }
                if (mob.getTarget() instanceof ServerPlayer player) {
                    updateVerticalStraggler(level, mob, player);
                    updateDisengagedRecovery(level, mob, player);
                } else {
                    clearVerticalStraggler(uuid);
                    disengagedSeconds.remove(uuid);
                }
            }
        }
        verticalStrandedSeconds.keySet().removeIf(uuid -> !trackedMobUuids.contains(uuid));
        lastVerticalGap.keySet().removeIf(uuid -> !trackedMobUuids.contains(uuid));
        verticalRecoveryCooldownSeconds.keySet().removeIf(uuid -> !trackedMobUuids.contains(uuid));
        disengagedSeconds.keySet().removeIf(uuid -> !trackedMobUuids.contains(uuid));
        logEncounterDiagnostic(level);
        return recoveryChanged ? Effect.CHANGED : Effect.NONE;
    }

    boolean isValidDungeonAggroTarget(ServerLevel level, Mob mob, LivingEntity target) {
        if (level == null || mob == null || target == null
            || target.level() != level || !target.isAlive() || target.isRemoved()
            || mob.distanceToSqr(target) > DUNGEON_AGGRO_RANGE * DUNGEON_AGGRO_RANGE) {
            return false;
        }
        if (target instanceof ServerPlayer player) {
            return isEligibleDungeonPlayerTarget(player);
        }
        DungeonInstanceAllocationData.InstanceKey key = instanceKey.get();
        if (key == null) {
            return false;
        }
        DungeonSubordinateTransportData.Binding binding =
            DungeonSubordinateTransportData.get(level).binding(key).orElse(null);
        return binding != null
            && binding.entries().stream().anyMatch(entry ->
                entry.entityId().equals(target.getUUID())
                    && entry.state().isInstanceBound()
                    && isEligibleDungeonSubordinateTarget(level, target, binding.hostId())
            );
    }

    LivingEntity selectDungeonAggroTarget(ServerLevel level, Mob mob) {
        if (level == null || mob == null) {
            return null;
        }
        LivingEntity nearest = null;
        double nearestDistance = DUNGEON_AGGRO_RANGE * DUNGEON_AGGRO_RANGE;
        for (ServerPlayer player : level.players()) {
            if (!isEligibleDungeonPlayerTarget(player)) {
                continue;
            }
            double distance = mob.distanceToSqr(player);
            if (distance <= nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }

        DungeonInstanceAllocationData.InstanceKey key = instanceKey.get();
        if (key == null) {
            return nearest;
        }
        DungeonSubordinateTransportData.Binding binding =
            DungeonSubordinateTransportData.get(level).binding(key).orElse(null);
        if (binding == null) {
            return nearest;
        }
        for (DungeonSubordinateTransportData.Entry entry : binding.entries()) {
            if (!entry.state().isInstanceBound()) {
                continue;
            }
            Entity entity = level.getEntity(entry.entityId());
            if (!(entity instanceof LivingEntity subordinate)
                || !isEligibleDungeonSubordinateTarget(level, subordinate, binding.hostId())) {
                continue;
            }
            double distance = mob.distanceToSqr(subordinate);
            if (distance <= nearestDistance) {
                nearest = subordinate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    LivingEntity selectDungeonSkillTarget(ServerLevel level, LivingEntity subordinate) {
        if (level == null || subordinate == null || subordinate.level() != level
            || !subordinate.isAlive() || subordinate.isRemoved()) {
            return null;
        }
        LivingEntity nearest = null;
        double nearestDistance = DUNGEON_AGGRO_RANGE * DUNGEON_AGGRO_RANGE;
        for (UUID uuid : trackedMobUuids) {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof Mob mob) || mob == subordinate
                || !mob.isAlive() || mob.isRemoved()) {
                continue;
            }
            double distance = subordinate.distanceToSqr(mob);
            if (distance <= nearestDistance) {
                nearest = mob;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void logEncounterDiagnostic(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (lastEncounterDiagnosticGameTime != Long.MIN_VALUE
            && gameTime - lastEncounterDiagnosticGameTime < ENCOUNTER_DIAGNOSTIC_INTERVAL_TICKS) {
            return;
        }
        lastEncounterDiagnosticGameTime = gameTime;
        int alive = 0;
        int missing = 0;
        List<String> positions = new ArrayList<>();
        for (UUID uuid : trackedMobUuids) {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) {
                missing++;
                continue;
            }
            alive++;
            if (positions.size() < 8) {
                positions.add(mob.getType().toShortString() + "@" + mob.blockPosition().toShortString());
            }
        }
        ServerPlayer player = nearestActivePlayer(level);
        LOGGER.info("Dungeon encounter status: controller={} player={} tracked={} alive={} missing={} actual={} target={} pending={} waves={} reason={} mobs={}",
            controllerPos, player == null ? "NONE" : player.blockPosition().toShortString(),
            trackedMobUuids.size(), alive, missing, lastSpawnActualCount, encounterTargetCount,
            getPendingEnemyCount(), wavesSpawned, lastSpawnSkippedReason, positions);
    }

    private void configureDungeonAggro(ServerLevel level, Mob mob) {
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null && followRange.getBaseValue() < DUNGEON_AGGRO_RANGE) {
            followRange.setBaseValue(DUNGEON_AGGRO_RANGE);
        }
        LivingEntity nearest = selectDungeonAggroTarget(level, mob);
        if (nearest != null || mob.getTarget() != null) {
            mob.setTarget(nearest);
        }
    }

    private boolean isEligibleDungeonPlayerTarget(ServerPlayer player) {
        return player != null
            && !player.isSpectator()
            && player.isAlive()
            && (!markerState.hasBounds() || markerState.isInsideBounds(player.blockPosition()));
    }

    private boolean isEligibleDungeonSubordinateTarget(
        ServerLevel level,
        LivingEntity subordinate,
        UUID ownerId
    ) {
        return subordinate != null
            && subordinate.level() == level
            && subordinate.isAlive()
            && !subordinate.isRemoved()
            && (!markerState.hasBounds()
                || markerState.isInsideBounds(subordinate.blockPosition()))
            && ownerId != null
            && TensuraCompat.subordinateOwnerId(subordinate)
                .filter(ownerId::equals)
                .isPresent();
    }
    private void updateVerticalStraggler(ServerLevel level, Mob mob, ServerPlayer player) {
        UUID mobId = mob.getUUID();
        if (markerState.hasBounds() && !markerState.isInsideBounds(player.blockPosition())) {
            clearVerticalStraggler(mobId);
            return;
        }

        int cooldown = verticalRecoveryCooldownSeconds.getOrDefault(mobId, 0);
        if (cooldown > 0) {
            if (cooldown == 1) {
                verticalRecoveryCooldownSeconds.remove(mobId);
            } else {
                verticalRecoveryCooldownSeconds.put(mobId, cooldown - 1);
            }
            verticalStrandedSeconds.remove(mobId);
            lastVerticalGap.remove(mobId);
            return;
        }

        int verticalGap = Math.abs(mob.blockPosition().getY() - player.blockPosition().getY());
        if (verticalGap < VERTICAL_STRANDED_MIN_GAP) {
            clearVerticalStraggler(mobId);
            return;
        }

        Integer previousGap = lastVerticalGap.put(mobId, verticalGap);
        if (previousGap == null || verticalGap < previousGap) {
            verticalStrandedSeconds.put(mobId, 0);
            return;
        }

        int strandedSeconds = verticalStrandedSeconds.getOrDefault(mobId, 0) + 1;
        verticalStrandedSeconds.put(mobId, strandedSeconds);
        if (strandedSeconds < VERTICAL_STRANDED_SECONDS) {
            return;
        }

        BlockPos recoveryPos = findVerticalRecoveryPos(level, player, verticalGap);
        if (recoveryPos == null) {
            lastVerticalRecovery = "NO_VALID_RECOVERY_ANCHOR mob=" + mob.getType().toShortString()
                + " gap=" + verticalGap;
        } else {
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            mob.teleportTo(recoveryPos.getX() + 0.5D, recoveryPos.getY(), recoveryPos.getZ() + 0.5D);
            mob.setTarget(player);
            totalVerticalRecoveries++;
            lastVerticalRecovery = "RECOVERED mob=" + mob.getType().toShortString()
                + " gap=" + verticalGap
                + " destination=" + recoveryPos.getX() + " " + recoveryPos.getY() + " " + recoveryPos.getZ();
        }
        verticalStrandedSeconds.remove(mobId);
        lastVerticalGap.remove(mobId);
        verticalRecoveryCooldownSeconds.put(mobId, VERTICAL_RECOVERY_COOLDOWN_SECONDS);
        recoveryChanged = true;
    }

    private BlockPos findVerticalRecoveryPos(ServerLevel level, ServerPlayer player, int currentVerticalGap) {
        List<BlockPos> anchors = new ArrayList<>();
        anchors.addAll(markerState.mobResetAnchors);
        anchors.addAll(markerState.spawnAnchors);
        anchors.addAll(markerState.flankSpawnAnchors);
        anchors.addAll(markerState.rangedSpawnAnchors);
        anchors.addAll(markerState.eliteSpawnAnchors);
        anchors.addAll(markerState.highgroundSpawnAnchors);

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos anchor : anchors) {
            BlockPos candidate = findSafeSpawnPos(level, anchor, 0);
            if (candidate == null) {
                continue;
            }
            int candidateGap = Math.abs(candidate.getY() - player.blockPosition().getY());
            if (candidateGap >= currentVerticalGap) {
                continue;
            }
            double distance = candidate.distSqr(player.blockPosition());
            double tooClosePenalty = distance < 16.0D ? 10000.0D : 0.0D;
            double concealmentBonus = spawnConcealmentScore(level, player, anchor) * 5.0D;
            double score = candidateGap * 100000.0D + distance + tooClosePenalty - concealmentBonus;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private void clearVerticalStraggler(UUID mobId) {
        verticalStrandedSeconds.remove(mobId);
        lastVerticalGap.remove(mobId);
    }
    private void updateDisengagedRecovery(ServerLevel level, Mob mob, ServerPlayer player) {
        UUID mobId = mob.getUUID();
        if (markerState.hasBounds() && !markerState.isInsideBounds(player.blockPosition())) {
            disengagedSeconds.remove(mobId);
            return;
        }
        if (mob.distanceToSqr(player) <= MEANINGFUL_CONTACT_DISTANCE_SQR && mob.hasLineOfSight(player)) {
            disengagedSeconds.remove(mobId);
            return;
        }
        int seconds = disengagedSeconds.getOrDefault(mobId, 0) + 1;
        disengagedSeconds.put(mobId, seconds);
        if (seconds < DISENGAGED_RECOVERY_SECONDS) {
            return;
        }

        BlockPos recoveryPos = findDisengagedRecoveryPos(level, player);
        if (recoveryPos != null) {
            mob.getNavigation().stop();
            mob.setDeltaMovement(Vec3.ZERO);
            mob.teleportTo(recoveryPos.getX() + 0.5D, recoveryPos.getY(), recoveryPos.getZ() + 0.5D);
            mob.setTarget(player);
            totalVerticalRecoveries++;
            lastVerticalRecovery = "DISENGAGED_RECOVERY mob=" + mob.getType().toShortString()
                + " destination=" + recoveryPos.getX() + " " + recoveryPos.getY() + " " + recoveryPos.getZ();
            verticalRecoveryCooldownSeconds.put(mobId, VERTICAL_RECOVERY_COOLDOWN_SECONDS);
            recoveryChanged = true;
        }
        disengagedSeconds.remove(mobId);
    }

    private BlockPos findDisengagedRecoveryPos(ServerLevel level, ServerPlayer player) {
        List<BlockPos> anchors = new ArrayList<>();
        anchors.addAll(markerState.mobResetAnchors);
        anchors.addAll(markerState.spawnAnchors);
        anchors.addAll(markerState.flankSpawnAnchors);
        anchors.addAll(markerState.rangedSpawnAnchors);
        anchors.addAll(markerState.eliteSpawnAnchors);
        anchors.addAll(markerState.highgroundSpawnAnchors);

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos anchor : anchors) {
            BlockPos candidate = findSafeSpawnPos(level, anchor, 0);
            if (candidate == null) {
                continue;
            }
            double distance = candidate.distSqr(player.blockPosition());
            double tooClosePenalty = distance < 16.0D ? 10000.0D : 0.0D;
            double score = distance + tooClosePenalty;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }
    private void applySpawnModifiers(ServerLevel level, Mob mob) {
        RoomBudgetResult budgetResult = roomBudgetResult(level);
        applyModifierHealth(level, mob);
        DungeonMobStatProfile floorScaling = DungeonMobStatScaler.apply(mob, budgetResult);
        applyArmoredModifier(level, mob);
        applyMoltenModifier(level, mob);
        applySwiftModifier(level, mob);
        applyNativeWardModifiers(level, mob);
        modifierRuntime.applySpawnModifiers(
            level,
            mob,
            activeEncounterModifierIds(level),
            summonedMobUuids.contains(mob.getUUID()),
            isBossType(mob)
        );
        lastSpawnFloorScalingSummary = floorScaling.debugSummary();
        lastSpawnModifierSummary = buildLastSpawnModifierSummary(level, floorScaling);
        lastSpawnArmorValue = getAttributeBaseValue(mob.getAttribute(Attributes.ARMOR));
        lastSpawnArmorToughnessValue = getAttributeBaseValue(mob.getAttribute(Attributes.ARMOR_TOUGHNESS));
    }

    private void applyModifierHealth(ServerLevel level, Mob mob) {
        RoomBudgetResult budgetResult = roomBudgetResult(level);
        double healthMultiplier = budgetResult.getModifierHealthMultiplier();
        if (healthMultiplier == 1.0D) {
            return;
        }
        AttributeInstance maxHealthAttribute = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute == null) {
            return;
        }
        double adjustedMaxHealth = Math.max(1.0D, maxHealthAttribute.getBaseValue() * healthMultiplier);
        maxHealthAttribute.setBaseValue(adjustedMaxHealth);
        mob.setHealth((float) adjustedMaxHealth);
    }

    private void applyArmoredModifier(ServerLevel level, Mob mob) {
        if (!DungeonRunManager.get(level).getActiveModifiers(instanceKey.get())
            .contains(DungeonModifierDefinition.ARMORED)) {
            return;
        }

        applyAttributeBaseBonus(mob.getAttribute(Attributes.ARMOR), ARMORED_ARMOR_BONUS);
        applyAttributeBaseBonus(mob.getAttribute(Attributes.ARMOR_TOUGHNESS), ARMORED_TOUGHNESS_BONUS);
    }

    private void applyMoltenModifier(ServerLevel level, Mob mob) {
        if (!DungeonRunManager.get(level).getActiveModifiers(instanceKey.get())
            .contains(DungeonModifierDefinition.MOLTEN)) {
            lastMoltenModifierName = "NONE";
            return;
        }

        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        lastMoltenModifierName = DungeonModifierDefinition.MOLTEN.getDisplayName();
    }

    private void applySwiftModifier(ServerLevel level, Mob mob) {
        if (!hasActiveModifier(level, DungeonModifierDefinition.SWIFT)) return;
        applyPermanentMultiplier(mob.getAttribute(Attributes.MOVEMENT_SPEED), SWIFT_MOVEMENT_ID, 0.18D);
        applyPermanentMultiplier(mob.getAttribute(Attributes.ATTACK_SPEED), SWIFT_ATTACK_ID, 0.12D);
    }

    private void applyNativeWardModifiers(ServerLevel level, Mob mob) {
        if (isBossType(mob) || summonedMobUuids.contains(mob.getUUID())) return;
        for (String modifierId : DungeonRunManager.get(level).getActiveModifierIds(instanceKey.get())) {
            DungeonModifierCatalog.Entry entry = DungeonModifierCatalog.byId(modifierId);
            if (entry != null && entry.isRollable() && !entry.nativeSkillId().isBlank()) {
                TensuraMobWardCompat.grantIfAbsent(mob, entry.nativeSkillId());
            }
        }
    }

    private void applyPermanentMultiplier(
        AttributeInstance attribute,
        ResourceLocation id,
        double amount
    ) {
        if (attribute == null || attribute.getModifier(id) != null) return;
        attribute.addPermanentModifier(
            new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        );
    }

    private void updateBerserkerAttackSpeed(Mob mob) {
        AttributeInstance attackSpeed = mob.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) return;
        boolean active = mob.getHealth() <= mob.getMaxHealth() * 0.25F;
        if (active && attackSpeed.getModifier(BERSERKER_ATTACK_ID) == null) {
            attackSpeed.addTransientModifier(new AttributeModifier(
                BERSERKER_ATTACK_ID, 0.12D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        } else if (!active && attackSpeed.getModifier(BERSERKER_ATTACK_ID) != null) {
            attackSpeed.removeModifier(BERSERKER_ATTACK_ID);
        }
    }

    private void applyAttributeBaseBonus(AttributeInstance attribute, double bonus) {
        if (attribute == null || bonus == 0.0D) {
            return;
        }
        attribute.setBaseValue(attribute.getBaseValue() + bonus);
    }

    private double getAttributeBaseValue(AttributeInstance attribute) {
        return attribute == null ? 0.0D : attribute.getBaseValue();
    }

    private String buildLastSpawnModifierSummary(ServerLevel level, DungeonMobStatProfile floorScaling) {
        List<String> parts = new ArrayList<>();
        RoomBudgetResult budgetResult = roomBudgetResult(level);
        if (budgetResult.getModifierHealthMultiplier() != 1.0D) {
            parts.add("HEALTH x" + formatDebugDecimal(budgetResult.getModifierHealthMultiplier()));
        }
        if (floorScaling != null && !floorScaling.isIdentity()) {
            parts.add("FLOOR_STATS(" + floorScaling.debugSummary() + ")");
        }
        Set<DungeonModifierDefinition> modifiers =
            DungeonRunManager.get(level).getActiveModifiers(instanceKey.get());
        if (modifiers.contains(DungeonModifierDefinition.ARMORED)) {
            parts.add("ARMORED(armor +" + formatDebugDecimal(ARMORED_ARMOR_BONUS) + ", toughness +" + formatDebugDecimal(ARMORED_TOUGHNESS_BONUS) + ")");
        }
        if (modifiers.contains(DungeonModifierDefinition.MOLTEN)) {
            parts.add("MOLTEN(fire_resistance, retaliate 3s)");
        }
        if (modifiers.contains(DungeonModifierDefinition.REGENERATIVE)) {
            parts.add("REGENERATIVE(interval " + REGENERATIVE_INTERVAL_TICKS + ", heal " + formatDebugDecimal(REGENERATIVE_HEAL_PERCENT * 100.0D) + "%)");
        }
        if (modifiers.contains(DungeonModifierDefinition.SUMMONER)) {
            parts.add("SUMMONER(interval " + SUMMONER_INTERVAL_TICKS + ", max_adds " + SUMMONER_MAX_ACTIVE_ADDS_PER_ROOM + ")");
        }
        return parts.isEmpty() ? "NONE" : String.join(", ", parts);
    }

    private String formatDebugDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    int getLastSpawnRequestedCount() {
        return lastSpawnRequestedCount;
    }

    int getLastSpawnActualCount() {
        return lastSpawnActualCount;
    }

    int getCurrentAliveTrackedMobCount(ServerLevel level) {
        int count = 0;
        for (UUID uuid : trackedMobUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof Mob mob && entity.isAlive() && !isBossType(mob)) {
                count++;
            }
        }
        return count;
    }

    String getLastSpawnSkippedReason() {
        return lastSpawnSkippedReason;
    }

    String getLastSpawnStrategy() {
        return lastSpawnStrategy;
    }

    int getLastSpawnMeleeUsedCount() {
        return lastSpawnMeleeUsedCount;
    }

    int getLastSpawnRangedUsedCount() {
        return lastSpawnRangedUsedCount;
    }

    int getLastSpawnFlankUsedCount() {
        return lastSpawnFlankUsedCount;
    }

    int getLastSpawnHighgroundUsedCount() {
        return lastSpawnHighgroundUsedCount;
    }

    int getLastSpawnGenericUsedCount() {
        return lastSpawnGenericUsedCount;
    }

    String getLastSpawnModifierSummary() {
        return lastSpawnModifierSummary;
    }

    int getLastCompositionBudget() {
        return lastCompositionBudget;
    }

    int getLastCompositionSpent() {
        return lastCompositionSpent;
    }

    String getLastCompositionSummary() {
        return lastCompositionSummary;
    }

    int getEncounterTargetCount() {
        return encounterTargetCount;
    }

    int getWaveSize() {
        return waveSize;
    }

    int getWavesSpawned() {
        return wavesSpawned;
    }

    int getWaveCooldownTicks() {
        return waveCooldownTicks;
    }

    int getPendingEnemyCount() {
        return Math.max(0, encounterTargetCount - lastSpawnActualCount);
    }

    String getWaveSummary() {
        return "waves=" + wavesSpawned + " / " + ENCOUNTER_WAVE_COUNT
            + ", spawned=" + lastSpawnActualCount + " / " + encounterTargetCount
            + ", waveSize=" + waveSize
            + ", nextInTicks=" + waveCooldownTicks
            + ", verticalRecoveries=" + totalVerticalRecoveries
            + ", lastVerticalRecovery=" + lastVerticalRecovery;
    }

    double getLastSpawnArmorValue() {
        return lastSpawnArmorValue;
    }

    double getLastSpawnArmorToughnessValue() {
        return lastSpawnArmorToughnessValue;
    }

    private String getSpawnStrategyName() {
        return switch (lifecycle.snapshot().profile()) {
            case CORRIDOR_AMBUSH -> "FLANK_FIRST";
            case TREASURE_AMBUSH -> "TREASURE_FLANK";
            case LARGE_COMBAT -> "MIXED_COMBAT";
            case ARENA_WAVE -> "ALL_ROLES_VARIETY";
            case CORRIDOR_LIGHT -> "LOW_PRESSURE_MELEE";
            case SAFE_ROOM -> "NO_COMBAT";
            default -> "STANDARD_COMBAT";
        };
    }

    private List<SpawnAnchorChoice> buildPreferredSpawnAnchors() {
        List<SpawnAnchorChoice> ordered = new ArrayList<>();
        switch (lifecycle.snapshot().profile()) {
            case CORRIDOR_AMBUSH -> {
                addSpawnChoices(ordered, markerState.flankSpawnAnchors, SpawnRole.FLANK);
                addSpawnChoices(ordered, markerState.spawnAnchors, SpawnRole.MELEE);
                addSpawnChoices(ordered, markerState.rangedSpawnAnchors, SpawnRole.RANGED);
                addSpawnChoices(ordered, markerState.highgroundSpawnAnchors, SpawnRole.HIGHGROUND);
            }
            case TREASURE_AMBUSH -> {
                addSpawnChoices(ordered, markerState.flankSpawnAnchors, SpawnRole.FLANK);
                addSpawnChoices(ordered, markerState.spawnAnchors, SpawnRole.MELEE);
                addSpawnChoices(ordered, markerState.rangedSpawnAnchors, SpawnRole.RANGED);
                addSpawnChoices(ordered, markerState.highgroundSpawnAnchors, SpawnRole.HIGHGROUND);
            }
            case LARGE_COMBAT, ARENA_WAVE -> {
                addSpawnChoices(ordered, markerState.spawnAnchors, SpawnRole.MELEE);
                addSpawnChoices(ordered, markerState.rangedSpawnAnchors, SpawnRole.RANGED);
                addSpawnChoices(ordered, markerState.flankSpawnAnchors, SpawnRole.FLANK);
                addSpawnChoices(ordered, markerState.highgroundSpawnAnchors, SpawnRole.HIGHGROUND);
            }
            case CORRIDOR_LIGHT -> {
                addSpawnChoices(ordered, markerState.spawnAnchors, SpawnRole.MELEE);
                addSpawnChoices(ordered, markerState.flankSpawnAnchors, SpawnRole.FLANK);
                addSpawnChoices(ordered, markerState.rangedSpawnAnchors, SpawnRole.RANGED);
                addSpawnChoices(ordered, markerState.highgroundSpawnAnchors, SpawnRole.HIGHGROUND);
            }
            case SAFE_ROOM -> {
            }
            default -> {
                addSpawnChoices(ordered, markerState.spawnAnchors, SpawnRole.MELEE);
                addSpawnChoices(ordered, markerState.rangedSpawnAnchors, SpawnRole.RANGED);
                addSpawnChoices(ordered, markerState.flankSpawnAnchors, SpawnRole.FLANK);
                addSpawnChoices(ordered, markerState.highgroundSpawnAnchors, SpawnRole.HIGHGROUND);
            }
        }
        if (ordered.isEmpty()) {
            addSpawnChoices(ordered, markerState.spawnAnchors, SpawnRole.GENERIC);
            addSpawnChoices(ordered, markerState.rangedSpawnAnchors, SpawnRole.RANGED);
            addSpawnChoices(ordered, markerState.flankSpawnAnchors, SpawnRole.FLANK);
            addSpawnChoices(ordered, markerState.highgroundSpawnAnchors, SpawnRole.HIGHGROUND);
        }
        return ordered;
    }

    private void addSpawnChoices(List<SpawnAnchorChoice> choices, List<BlockPos> anchors, SpawnRole role) {
        for (BlockPos anchor : anchors) {
            choices.add(new SpawnAnchorChoice(anchor, role));
        }
    }

    private void incrementLastSpawnRoleCount(SpawnRole role) {
        switch (role) {
            case MELEE -> lastSpawnMeleeUsedCount++;
            case RANGED -> lastSpawnRangedUsedCount++;
            case FLANK -> lastSpawnFlankUsedCount++;
            case HIGHGROUND -> lastSpawnHighgroundUsedCount++;
            case GENERIC -> lastSpawnGenericUsedCount++;
            case ELITE -> {
            }
        }
    }

    void clearTrackedMobs(ServerLevel level) {
        modifierRuntime.clear(level, trackedMobUuids);
        for (UUID uuid : trackedMobUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                if (entity instanceof LivingEntity living) {
                    TensuraMobWardCompat.forgetDungeonGranted(living);
                }
                entity.discard();
            }
        }
        trackedMobUuids.clear();
        summonerMobUuids.clear();
        summonedMobUuids.clear();
        summonedMobOwners.clear();
        missingMobTicks.clear();
        mobOutOfBoundsTicks.clear();
        mobResetCooldownTicks.clear();
        eliteMobUuid = null;
        bossMobUuid = null;
        bossSpawnAttempted = false;
        resetCombatProgressionState();
        DungeonPopulationAdmissionController.remove(level, instanceKey.get(), controllerPos);
    }

    Effect updateTrackedMobs(ServerLevel level) {
        pendingActivationFailure = "NONE";
        List<UUID> resolvedThisTick = new ArrayList<>();
        List<UUID> staleToRemove = new ArrayList<>();
        for (UUID uuid : trackedMobUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity == null) {
                int missingTicks = missingMobTicks.getOrDefault(uuid, 0) + 1;
                missingMobTicks.put(uuid, missingTicks);
                if (missingTicks >= STALE_MOB_THRESHOLD_TICKS) {
                    staleToRemove.add(uuid);
                    hadAnyMobConfirmedRemoved = true;
                }
                continue;
            }

            resolvedThisTick.add(uuid);
            missingMobTicks.remove(uuid);
            if (!entity.isAlive()) {
                staleToRemove.add(uuid);
                hadAnyMobConfirmedRemoved = true;
                if (uuid.equals(eliteMobUuid)) {
                    eliteDefeated = true;
                }
                if (uuid.equals(bossMobUuid)) {
                    bossMobUuid = null;
                }
                continue;
            }
            maybeResetMobPosition(level, entity);
        }

        for (UUID uuid : resolvedThisTick) {
            if (!trackedMobUuids.contains(uuid)) {
                mobOutOfBoundsTicks.remove(uuid);
            }
        }
        for (UUID uuid : staleToRemove) {
            trackedMobUuids.remove(uuid);
            summonerMobUuids.remove(uuid);
            summonedMobUuids.remove(uuid);
            summonedMobOwners.remove(uuid);
            summonedMobOwners.entrySet().removeIf(entry -> entry.getValue().equals(uuid));
            mobOutOfBoundsTicks.remove(uuid);
            mobResetCooldownTicks.remove(uuid);
            if (uuid.equals(eliteMobUuid)) {
                eliteMobUuid = null;
            }
            if (uuid.equals(bossMobUuid)) {
                bossMobUuid = null;
            }
        }
        reportPopulation(level);
        return "NONE".equals(pendingActivationFailure)
            ? Effect.NONE
            : new Effect(false, "NONE", pendingActivationFailure);
    }

    private void reportPopulation(ServerLevel level) {
        int alive = 0;
        int protectedAlive = 0;
        int summonedAlive = 0;
        for (UUID uuid : trackedMobUuids) {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) {
                continue;
            }
            alive++;
            if (isBossType(mob)
                || uuid.equals(eliteMobUuid)
                || summonerMobUuids.contains(uuid)
                || uuid.equals(riftboundCarrierUuid)
                || gravityPlayerByHunter.containsKey(uuid)) {
                protectedAlive++;
            }
            if (summonedMobUuids.contains(uuid)) {
                summonedAlive++;
            }
        }
        PlannedPopulationDemand demand = plannedPopulationDemand(level);
        int pendingElite = demand.plannedEliteSlots() > 0
            && eliteMobUuid == null
            && !eliteDefeated ? 1 : 0;
        DungeonPopulationAdmissionController.report(
            level,
            instanceKey.get(),
            controllerPos,
            new DungeonPopulationAdmissionController.EncounterUsage(
                alive,
                Math.max(0, alive - protectedAlive),
                protectedAlive,
                summonedAlive,
                demand.plannedTotal(),
                getPendingEnemyCount() + pendingElite,
                demand.requiredRoleSlots()
            )
        );
    }

    private PlannedPopulationDemand plannedPopulationDemand(ServerLevel level) {
        int summonerSlots = hasActiveModifier(level, DungeonModifierDefinition.SUMMONER)
            ? (isLargeSummonerRoom() ? 2 : 1)
            : 0;
        return plannedPopulationDemand(
            encounterTargetCount,
            requiresEliteAnchor(level),
            summonerSlots,
            hasActiveModifier(level, DungeonModifierDefinition.RIFTBOUND)
        );
    }

    static PlannedPopulationDemand plannedPopulationDemand(
        int normalSlots,
        boolean eliteRequired,
        int summonerSlots,
        boolean riftboundRequired
    ) {
        int safeNormalSlots = Math.max(0, normalSlots);
        int safeSummonerSlots = Math.min(safeNormalSlots, Math.max(0, summonerSlots));
        int remainingNormalSlots = safeNormalSlots - safeSummonerSlots;
        int riftboundSlots = riftboundRequired && remainingNormalSlots > 0 ? 1 : 0;
        int eliteSlots = eliteRequired ? 1 : 0;
        return new PlannedPopulationDemand(
            safeNormalSlots + eliteSlots,
            eliteSlots,
            eliteSlots + safeSummonerSlots + riftboundSlots
        );
    }

    void updateMobResetCooldowns() {
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : mobResetCooldownTicks.entrySet()) {
            int remainingTicks = entry.getValue() - 1;
            if (remainingTicks <= 0) {
                expired.add(entry.getKey());
            } else {
                entry.setValue(remainingTicks);
            }
        }
        for (UUID uuid : expired) {
            mobResetCooldownTicks.remove(uuid);
        }
    }

    private void maybeResetMobPosition(ServerLevel level, Entity entity) {
        UUID entityId = entity.getUUID();
        boolean hasRoomBounds = markerState.hasBounds();
        boolean shouldReset = hasRoomBounds
            ? !markerState.isInsideBounds(entity.blockPosition())
            : entity.blockPosition().distSqr(controllerPos) > MOB_RESET_DISTANCE_SQR;
        if (!shouldReset) {
            mobOutOfBoundsTicks.remove(entityId);
            return;
        }

        int ticks = mobOutOfBoundsTicks.getOrDefault(entityId, 0) + 1;
        if (ticks < MOB_RESET_THRESHOLD_TICKS) {
            mobOutOfBoundsTicks.put(entityId, ticks);
            return;
        }

        if (mobResetCooldownTicks.getOrDefault(entityId, 0) > 0) {
            mobOutOfBoundsTicks.put(entityId, ticks);
            return;
        }

        mobOutOfBoundsTicks.remove(entityId);
        if (markerState.mobResetAnchors.isEmpty()) {
            pendingActivationFailure = "No mob_reset_anchor found for out-of-bounds mob recovery.";
            return;
        }

        BlockPos resetPos = markerState.nearestMobResetAnchor(entity.blockPosition()).above();
        entity.teleportTo(resetPos.getX() + 0.5D, resetPos.getY(), resetPos.getZ() + 0.5D);
        mobResetCooldownTicks.put(entityId, MOB_RESET_COOLDOWN_TICKS);
    }

    int staleTrackedMobCount() {
        int count = 0;
        for (int ticks : missingMobTicks.values()) {
            if (ticks >= STALE_MOB_THRESHOLD_TICKS) {
                count++;
            }
        }
        return count;
    }

    int summonerIntervalTicks() {
        return SUMMONER_INTERVAL_TICKS;
    }

    int regenerativeIntervalTicks() {
        return REGENERATIVE_INTERVAL_TICKS;
    }

    private boolean profileAllowsCombat() {
        RoomProfile profile = lifecycle.snapshot().profile();
        return profile.allowsCombat() && profile != RoomProfile.SAFE_ROOM;
    }

    private RoomBudgetResult roomBudgetResult(ServerLevel level) {
        DungeonRunManager manager = DungeonRunManager.get(level);
        return DungeonBudgetEngine.calculate(
            manager.getFloorNumber(instanceKey.get()),
            lifecycle.snapshot().profile(),
            manager.getActiveModifiers(instanceKey.get())
        );
    }

    private int currentAliveTrackedMobCount(ServerLevel level) {
        int count = 0;
        for (UUID uuid : trackedMobUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private void applyEnemyPoolDebug(DungeonEnemyPoolDebugInfo debugInfo) {
        enemyPoolRegistryEnabled = debugInfo.registryEnabled();
        lastEnemyPoolSpawnSource = debugInfo.spawnSource();
        lastEnemyPoolRoleRequested = debugInfo.requestedRole();
        lastEnemyPoolEntitySelected = debugInfo.selectedEntityId();
        lastEnemyPoolFamily = debugInfo.selectedFamily();
        lastEnemyPoolSourceMod = debugInfo.selectedSourceMod();
        lastEnemyPoolFallbackReason = debugInfo.fallbackReason();
        lastEnemyPoolCandidateCount = debugInfo.candidateCount();
        lastEnemyPoolError = debugInfo.error();
    }

    private void resetEnemyPoolDebug() {
        enemyPoolRegistryEnabled = false;
        lastEnemyPoolSpawnSource = "NONE";
        lastEnemyPoolRoleRequested = "NONE";
        lastEnemyPoolEntitySelected = "NONE";
        lastEnemyPoolFamily = "NONE";
        lastEnemyPoolSourceMod = "NONE";
        lastEnemyPoolFallbackReason = "NONE";
        lastEnemyPoolCandidateCount = 0;
        lastEnemyPoolError = "NONE";
    }

    Effect updateRegenerativePulse(ServerLevel level) {
        if (!hasActiveModifier(level, DungeonModifierDefinition.REGENERATIVE)) {
            regenerativePulseProgressTicks = 0;
            lastRegenModifierName = "NONE";
            return Effect.NONE;
        }

        lastRegenModifierName = DungeonModifierDefinition.REGENERATIVE.getDisplayName();
        regenerativePulseProgressTicks += 1;
        if (regenerativePulseProgressTicks < REGENERATIVE_INTERVAL_TICKS) {
            return Effect.NONE;
        }

        regenerativePulseProgressTicks = 0;
        lastRegenPulseTick = lifecycle.snapshot().tickCounter();
        lastRegenPulseHealedCount = 0;
        lastRegenPulseTotalHealthRestored = 0.0D;
        for (UUID uuid : trackedMobUuids) {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isAlive() || livingEntity.isRemoved()) {
                continue;
            }
            if (entity instanceof Mob mob && isBossType(mob)) {
                continue;
            }
            float maxHealth = livingEntity.getMaxHealth();
            float currentHealth = livingEntity.getHealth();
            if (maxHealth <= 0.0F || currentHealth >= maxHealth) {
                continue;
            }
            float healAmount = (float) Math.min(maxHealth - currentHealth, maxHealth * REGENERATIVE_HEAL_PERCENT);
            if (healAmount <= 0.0F) {
                continue;
            }
            livingEntity.heal(healAmount);
            lastRegenPulseHealedCount++;
            lastRegenPulseTotalHealthRestored += healAmount;
        }
        return Effect.CHANGED;
    }

    Effect updateSummonerPulse(ServerLevel level) {
        if (!hasActiveModifier(level, DungeonModifierDefinition.SUMMONER)) {
            summonerPulseProgressTicks = 0;
            lastSummonerModifierName = "NONE";
            if ("NONE".equals(lastSummonRejectionReason)) {
                lastSummonRejectionReason = "MODIFIER_NOT_ACTIVE";
            }
            return Effect.NONE;
        }
        if (lifecycle.snapshot().state() != RoomControllerBlockEntity.RoomState.ACTIVE) {
            lastSummonRejectionReason = "ROOM_NOT_ACTIVE";
            return Effect.NONE;
        }

        lastSummonerModifierName = DungeonModifierDefinition.SUMMONER.getDisplayName();
        boolean selectionChanged = summonerMobUuids.isEmpty() && selectSummoners(level);
        summonerPulseProgressTicks += 1;
        if (summonerPulseProgressTicks < SUMMONER_INTERVAL_TICKS) {
            return selectionChanged ? Effect.CHANGED : Effect.NONE;
        }

        summonerPulseProgressTicks = 0;
        lastSummonPulseTick = lifecycle.snapshot().tickCounter();
        if (summonerMobUuids.isEmpty()) {
            lastSummonResult = "NONE";
            lastSummonRejectionReason = "NO_ELIGIBLE_SUMMONERS";
            return Effect.CHANGED;
        }
        if (activeSummonedAddsCount(level) >= SUMMONER_MAX_ACTIVE_ADDS_PER_ROOM) {
            lastSummonResult = "NONE";
            lastSummonRejectionReason = "ROOM_ACTIVE_SUMMON_CAP_REACHED";
            return Effect.CHANGED;
        }
        if (totalSummonsThisRoom >= SUMMONER_MAX_TOTAL_ADDS_PER_ROOM) {
            lastSummonResult = "NONE";
            lastSummonRejectionReason = "ROOM_TOTAL_SUMMON_CAP_REACHED";
            return Effect.CHANGED;
        }

        for (UUID summonerUuid : List.copyOf(summonerMobUuids)) {
            Entity entity = level.getEntity(summonerUuid);
            if (!(entity instanceof Mob summoner) || !summoner.isAlive() || summoner.isRemoved()) {
                lastSummonResult = "NONE";
                lastSummonRejectionReason = "SUMMONER_REMOVED_OR_DEAD";
                continue;
            }
            if (countActiveSummonsForSummoner(level, summonerUuid) >= SUMMONER_MAX_ACTIVE_ADDS_PER_SUMMONER) {
                lastSummonResult = "NONE";
                lastSummonRejectionReason = "SUMMONER_CAP_REACHED";
                continue;
            }
            if (activeSummonedAddsCount(level) >= SUMMONER_MAX_ACTIVE_ADDS_PER_ROOM) {
                lastSummonResult = "NONE";
                lastSummonRejectionReason = "ROOM_ACTIVE_SUMMON_CAP_REACHED";
                break;
            }
            if (totalSummonsThisRoom >= SUMMONER_MAX_TOTAL_ADDS_PER_ROOM) {
                lastSummonResult = "NONE";
                lastSummonRejectionReason = "ROOM_TOTAL_SUMMON_CAP_REACHED";
                break;
            }

            BlockPos spawnPos = findSummonSpawnPos(level, summoner.blockPosition());
            if (spawnPos == null) {
                lastSummonResult = "NONE";
                lastSummonRejectionReason = "NO_VALID_SPAWN_POSITION";
                continue;
            }
            Mob summoned = createSummonedAdd(level, summoner, spawnPos);
            if (summoned == null) {
                lastSummonResult = "NONE";
                lastSummonRejectionReason = "NO_VALID_SPAWN_POSITION";
                continue;
            }

            trackedMobUuids.add(summoned.getUUID());
            summonedMobUuids.add(summoned.getUUID());
            summonedMobOwners.put(summoned.getUUID(), summonerUuid);
            totalSummonsThisRoom++;
            lastSummonerEntityType = entityTypeName(summoner.getType());
            lastSummonedEntityType = entityTypeName(summoned.getType());
            lastSummonResult = "APPLIED";
            lastSummonRejectionReason = "APPLIED";
            return Effect.CHANGED;
        }
        return Effect.CHANGED;
    }

    boolean selectSummoners(ServerLevel level) {
        if (!hasActiveModifier(level, DungeonModifierDefinition.SUMMONER)) {
            lastSummonerModifierName = "NONE";
            lastSummonRejectionReason = "MODIFIER_NOT_ACTIVE";
            return false;
        }
        int maxSummoners = isLargeSummonerRoom() ? 2 : 1;
        maxSummoners = Math.min(maxSummoners, SUMMONER_MAX_SELECTED);
        summonerMobUuids.clear();
        for (UUID uuid : trackedMobUuids) {
            if (summonerMobUuids.size() >= maxSummoners) {
                break;
            }
            Entity entity = level.getEntity(uuid);
            if (isEligibleSummoner(entity)) {
                summonerMobUuids.add(uuid);
            }
        }
        lastSummonerModifierName = DungeonModifierDefinition.SUMMONER.getDisplayName();
        if (summonerMobUuids.isEmpty()) {
            lastSummonRejectionReason = "NO_ELIGIBLE_SUMMONERS";
            lastSummonResult = "NONE";
        } else {
            lastSummonRejectionReason = "NONE";
        }
        return true;
    }

    private boolean isLargeSummonerRoom() {
        RoomProfile profile = lifecycle.snapshot().profile();
        return profile == RoomProfile.LARGE_COMBAT || profile == RoomProfile.ARENA_WAVE;
    }

    private boolean isEligibleSummoner(Entity entity) {
        if (!(entity instanceof Mob mob) || !(entity instanceof Monster) || !mob.isAlive() || mob.isRemoved()) {
            return false;
        }
        return !summonedMobUuids.contains(mob.getUUID()) && !isBossType(mob);
    }

    boolean isBossType(Mob mob) {
        if (mob == null) {
            return false;
        }
        if (mob.getUUID().equals(bossMobUuid)) {
            return true;
        }
        EntityType<?> type = mob.getType();
        return type == EntityType.ENDER_DRAGON || type == EntityType.WITHER || type == EntityType.WARDEN;
    }

    boolean isTrackedSummoned(LivingEntity entity) {
        return entity != null && summonedMobUuids.contains(entity.getUUID());
    }

    private int countActiveSummonsForSummoner(ServerLevel level, UUID summonerUuid) {
        int count = 0;
        for (Map.Entry<UUID, UUID> entry : summonedMobOwners.entrySet()) {
            if (!entry.getValue().equals(summonerUuid)) {
                continue;
            }
            Entity entity = level.getEntity(entry.getKey());
            if (entity != null && entity.isAlive() && !entity.isRemoved()) {
                count++;
            }
        }
        return count;
    }

    int countAliveMembers(ServerLevel level, Set<UUID> uuids) {
        int count = 0;
        for (UUID uuid : uuids) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive() && !entity.isRemoved()) {
                count++;
            }
        }
        return count;
    }

    int activeSummonedAddsCount(ServerLevel level) {
        return countAliveMembers(level, summonedMobUuids);
    }

    private Mob createSummonedAdd(ServerLevel level, Mob summoner, BlockPos spawnPos) {
        Entity entity = summoner.getType().create(level);
        if (!(entity instanceof Mob summoned) || !(summoned instanceof Monster) || isBossType(summoned)) {
            return null;
        }
        summoned.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        summoned.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.REINFORCEMENT, null);
        applySummonedAddStats(summoned);
        summoned.setPersistenceRequired();
        SoulOrbMobIdentity.inherit(summoner, summoned);
        level.addFreshEntity(summoned);
        return summoned;
    }

    private void applySummonedAddStats(Mob summoned) {
        AttributeInstance maxHealthAttribute = summoned.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            double adjustedMaxHealth = Math.max(1.0D, maxHealthAttribute.getBaseValue() * SUMMONED_ADD_HEALTH_MULTIPLIER);
            maxHealthAttribute.setBaseValue(adjustedMaxHealth);
            summoned.setHealth((float) adjustedMaxHealth);
        }
    }

    private String entityTypeName(EntityType<?> type) {
        return type == null ? "NONE" : type.toShortString();
    }

    Effect updateCombatProgression(ServerLevel level, List<ServerPlayer> activePlayers) {
        if (hasActiveModifier(level, DungeonModifierDefinition.SHADOWED)) {
            for (ServerPlayer player : activePlayers) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0, false, false));
            }
        }
        boolean changed = updatePendingBlasts(level);
        changed |= updateRiftbound(level);
        changed |= updateGravityHunters(level, activePlayers == null ? List.of() : activePlayers);
        Set<UUID> occupiedCarrierUuids = new HashSet<>(gravityPlayerByHunter.keySet());
        if (riftboundCarrierUuid != null) occupiedCarrierUuids.add(riftboundCarrierUuid);
        occupiedCarrierUuids.addAll(summonerMobUuids);
        changed |= modifierRuntime.update(
            level,
            activeEncounterModifierIds(level),
            trackedMobUuids,
            summonedMobUuids,
            eliteMobUuid,
            occupiedCarrierUuids,
            activePlayers,
            this::isBossType
        );
        return changed ? Effect.CHANGED : Effect.NONE;
    }

    private Set<String> activeEncounterModifierIds(ServerLevel level) {
        DungeonRunManager manager = DungeonRunManager.get(level);
        Set<String> ids = new HashSet<>(manager.getActiveModifierIds(instanceKey.get()));
        ids.addAll(manager.getActiveRoomPressureIds(instanceKey.get()));
        return Set.copyOf(ids);
    }

    boolean queueExplosiveDeath(ServerLevel level, LivingEntity entity) {
        if (!hasActiveModifier(level, DungeonModifierDefinition.EXPLOSIVE)
            || pendingBlasts.size() >= EXPLOSIVE_MAX_PENDING
            || entity == null
            || !trackedMobUuids.contains(entity.getUUID())
            || summonedMobUuids.contains(entity.getUUID())
            || entity.getUUID().equals(eliteMobUuid)
            || !(entity instanceof Mob mob)
            || isBossType(mob)) {
            return false;
        }
        pendingBlasts.add(new PendingBlast(entity.position(), EXPLOSIVE_FUSE_TICKS));
        return true;
    }

    private boolean updatePendingBlasts(ServerLevel level) {
        if (pendingBlasts.isEmpty()) return false;
        boolean changed = false;
        List<PendingBlast> remaining = new ArrayList<>();
        for (PendingBlast blast : pendingBlasts) {
            int fuse = blast.fuseTicks - 1;
            level.sendParticles(
                ParticleTypes.FLAME, blast.position.x, blast.position.y + 0.2D, blast.position.z,
                3, 0.35D, 0.15D, 0.35D, 0.01D
            );
            if (fuse > 0) {
                remaining.add(new PendingBlast(blast.position, fuse));
                continue;
            }
            for (ServerPlayer player : level.players()) {
                double distance = player.position().distanceTo(blast.position);
                if (distance > EXPLOSIVE_RADIUS || player.isSpectator() || !player.isAlive()) continue;
                float maximum = Math.min(16.0F, player.getMaxHealth() * 0.08F);
                float damage = (float) (maximum * (1.0D - distance / EXPLOSIVE_RADIUS));
                if (damage > 0.0F) {
                    player.hurt(level.damageSources().magic(), damage);
                }
            }
            level.sendParticles(
                ParticleTypes.EXPLOSION, blast.position.x, blast.position.y + 0.2D, blast.position.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D
            );
            changed = true;
        }
        pendingBlasts.clear();
        pendingBlasts.addAll(remaining);
        return changed || !remaining.isEmpty();
    }

    private boolean updateRiftbound(ServerLevel level) {
        if (!hasActiveModifier(level, DungeonModifierDefinition.RIFTBOUND)) {
            riftboundCarrierUuid = null;
            riftboundPulseTicks = 0;
            return false;
        }
        if (riftboundCarrierUuid == null) {
            riftboundCarrierUuid = trackedMobUuids.stream()
                .sorted()
                .filter(uuid -> {
                    Entity entity = level.getEntity(uuid);
                    return isEligibleSpecialCarrier(entity);
                })
                .findFirst()
                .orElse(null);
            if (riftboundCarrierUuid != null && level.getEntity(riftboundCarrierUuid) instanceof Mob carrier) {
                carrier.setCustomName(Component.literal("Riftbound Carrier"));
                carrier.setCustomNameVisible(true);
                return true;
            }
        }
        Entity entity = riftboundCarrierUuid == null ? null : level.getEntity(riftboundCarrierUuid);
        if (!(entity instanceof Mob carrier) || !carrier.isAlive() || carrier.isRemoved()) {
            riftboundCarrierUuid = null;
            riftboundPulseTicks = 0;
            return false;
        }
        riftboundPulseTicks++;
        if (riftboundPulseTicks < RIFTBOUND_INTERVAL_TICKS) return false;
        riftboundPulseTicks = 0;
        if (riftboundTotalSummons >= RIFTBOUND_MAX_TOTAL_ADDS
            || activeSummonedAddsCount(level) >= RIFTBOUND_MAX_ACTIVE_ADDS) {
            return false;
        }
        BlockPos spawnPos = findSummonSpawnPos(level, carrier.blockPosition());
        Mob summoned = spawnPos == null ? null : createSummonedAdd(level, carrier, spawnPos);
        if (summoned == null) return false;
        trackedMobUuids.add(summoned.getUUID());
        summonedMobUuids.add(summoned.getUUID());
        summonedMobOwners.put(summoned.getUUID(), carrier.getUUID());
        riftboundTotalSummons++;
        return true;
    }

    private boolean isEligibleSpecialCarrier(Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) {
            return false;
        }
        UUID id = mob.getUUID();
        return !summonedMobUuids.contains(id)
            && !summonerMobUuids.contains(id)
            && !id.equals(eliteMobUuid)
            && !gravityPlayerByHunter.containsKey(id)
            && !TensuraCompat.isFlyingEnemyArchetype(mob)
            && !isBossType(mob);
    }

    private boolean updateGravityHunters(ServerLevel level, List<ServerPlayer> activePlayers) {
        boolean changed = false;
        Set<UUID> activePlayerIds = new HashSet<>();
        for (ServerPlayer player : activePlayers) {
            activePlayerIds.add(player.getUUID());
            changed |= updateGravityForPlayer(level, player);
        }
        for (UUID playerId : List.copyOf(gravityHunterByPlayer.keySet())) {
            if (!activePlayerIds.contains(playerId)) changed |= clearGravityAssignment(playerId);
        }
        gravityFlightTicks.keySet().retainAll(activePlayerIds);
        gravityGraceTicks.keySet().retainAll(activePlayerIds);
        gravityHeavyTicks.keySet().retainAll(activePlayerIds);
        return changed;
    }

    private boolean updateGravityForPlayer(ServerLevel level, ServerPlayer player) {
        UUID playerId = player.getUUID();
        boolean changed = false;
        int heavy = gravityHeavyTicks.getOrDefault(playerId, 0);
        if (heavy > 0) {
            applyGravityIncapacitation(player);
            if (heavy == 1) gravityHeavyTicks.remove(playerId);
            else gravityHeavyTicks.put(playerId, heavy - 1);
            return true;
        }
        int grace = gravityGraceTicks.getOrDefault(playerId, 0);
        if (grace > 0) {
            gravityFlightTicks.remove(playerId);
            if (grace == 1) gravityGraceTicks.remove(playerId);
            else gravityGraceTicks.put(playerId, grace - 1);
            return changed;
        }

        UUID hunterId = gravityHunterByPlayer.get(playerId);
        Mob hunter = hunterId != null && level.getEntity(hunterId) instanceof Mob mob ? mob : null;
        if (hunterId != null && (hunter == null || !hunter.isAlive() || hunter.isRemoved())) {
            clearGravityAssignment(playerId);
            gravityGraceTicks.put(playerId, GRAVITY_DEATH_GRACE_TICKS);
            gravityFlightTicks.remove(playerId);
            return true;
        }
        if (hunter != null) {
            hunter.setTarget(player);
            int channel = gravityChannelTicks.getOrDefault(playerId, 0);
            if (channel > 0) {
                level.sendParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    3, 0.25D, 0.4D, 0.25D, 0.02D
                );
                if (channel == 1) {
                    gravityChannelTicks.remove(playerId);
                    applyGravityPull(player, hunter);
                    TensuraCompat.interruptHeldSkills(player);
                    player.setNoGravity(false);
                    gravityHeavyTicks.put(playerId, GRAVITY_INCAPACITATION_TICKS);
                    gravityFlightTicks.remove(playerId);
                } else {
                    gravityChannelTicks.put(playerId, channel - 1);
                }
                return true;
            }
        }

        if (!player.getAbilities().flying) {
            gravityFlightTicks.remove(playerId);
            return changed;
        }
        int flightTicks = gravityFlightTicks.getOrDefault(playerId, 0) + 1;
        gravityFlightTicks.put(playerId, flightTicks);
        if (flightTicks < GRAVITY_FLIGHT_TRIGGER_TICKS) return changed;
        gravityFlightTicks.remove(playerId);
        if (hunter == null) {
            hunter = selectGravityHunter(level, player);
            if (hunter == null) {
                LOGGER.info(
                    "Gravity Hunter unavailable: controller={} player={} tracked={} reason=NO_ELIGIBLE_ORDINARY_MOB",
                    controllerPos, player.getUUID(), trackedMobUuids.size()
                );
                return changed;
            }
            gravityHunterByPlayer.put(playerId, hunter.getUUID());
            gravityPlayerByHunter.put(hunter.getUUID(), playerId);
            hunter.setCustomName(Component.literal("Gravity Hunter"));
            hunter.setCustomNameVisible(true);
            hunter.setGlowingTag(true);
            LOGGER.info(
                "Gravity Hunter promoted: controller={} player={} hunter={} type={}",
                controllerPos, playerId, hunter.getUUID(), hunter.getType().toShortString()
            );
        }
        gravityChannelTicks.put(playerId, GRAVITY_CHANNEL_TICKS);
        hunter.setTarget(player);
        return true;
    }

    private Mob selectGravityHunter(ServerLevel level, ServerPlayer player) {
        return trackedMobUuids.stream()
            .map(level::getEntity)
            .filter(this::isEligibleSpecialCarrier)
            .filter(entity -> !entity.getUUID().equals(riftboundCarrierUuid))
            .map(Mob.class::cast)
            .min(java.util.Comparator
                .comparingDouble((Mob mob) -> mob.distanceToSqr(player))
                .thenComparing(mob -> mob.getUUID().toString()))
            .orElse(null);
    }

    private void applyGravityPull(ServerPlayer player, Mob hunter) {
        Vec3 difference = hunter.position().subtract(player.position());
        Vec3 horizontal = new Vec3(difference.x, 0.0D, difference.z);
        Vec3 pull = horizontal.lengthSqr() < 0.0001D ? Vec3.ZERO : horizontal.normalize().scale(1.1D);
        player.setDeltaMovement(pull.x, -0.8D, pull.z);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
    }

    private void applyGravityIncapacitation(ServerPlayer player) {
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        player.stopUsingItem();
        player.setSprinting(false);
        player.setPose(Pose.SWIMMING);
        player.setDeltaMovement(0.0D, Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        player.addEffect(new MobEffectInstance(
            MobEffects.MOVEMENT_SLOWDOWN, 5, 10, false, false, false
        ));
    }

    boolean isGravityIncapacitated(UUID playerId) {
        return gravityHeavyTicks.getOrDefault(playerId, 0) > 0;
    }

    private boolean clearGravityAssignment(UUID playerId) {
        UUID hunterId = gravityHunterByPlayer.remove(playerId);
        gravityChannelTicks.remove(playerId);
        if (hunterId != null) {
            gravityPlayerByHunter.remove(hunterId);
            return true;
        }
        return false;
    }

    private void resetCombatProgressionState() {
        modifierRuntime.reset();
        pendingBlasts.clear();
        riftboundCarrierUuid = null;
        riftboundPulseTicks = 0;
        riftboundTotalSummons = 0;
        gravityFlightTicks.clear();
        gravityGraceTicks.clear();
        gravityHeavyTicks.clear();
        gravityHunterByPlayer.clear();
        gravityPlayerByHunter.clear();
        gravityChannelTicks.clear();
    }

    void resetRegenerativeState() {
        regenerativePulseProgressTicks = 0;
        lastRegenModifierName = "NONE";
        lastRegenPulseTick = -1;
        lastRegenPulseHealedCount = 0;
        lastRegenPulseTotalHealthRestored = 0.0D;
    }

    void resetMoltenState() {
        lastMoltenModifierName = "NONE";
        lastMoltenRetaliationTick = -1;
        lastMoltenRetaliationTarget = "NONE";
        totalMoltenRetaliations = 0;
        moltenDamageEventsSeen = 0;
        moltenDamageEventsWithLivingAttacker = 0;
        moltenDamageEventsWithTrackedTarget = 0;
        lastMoltenRejectionReason = "NONE";
    }

    void resetSummonerState() {
        summonerMobUuids.clear();
        summonedMobUuids.clear();
        summonedMobOwners.clear();
        summonerPulseProgressTicks = 0;
        lastSummonerModifierName = "NONE";
        lastSummonPulseTick = -1;
        totalSummonsThisRoom = 0;
        lastSummonResult = "NONE";
        lastSummonRejectionReason = "NONE";
        lastSummonerEntityType = "NONE";
        lastSummonedEntityType = "NONE";
    }

    boolean hasActiveModifier(ServerLevel level, DungeonModifierDefinition modifier) {
        return DungeonRunManager.get(level).getActiveModifiers(instanceKey.get()).contains(modifier);
    }

    boolean isMoltenModifierActive(ServerLevel level) {
        return lifecycle.snapshot().state() == RoomControllerBlockEntity.RoomState.ACTIVE
            && hasActiveModifier(level, DungeonModifierDefinition.MOLTEN);
    }

    boolean isSummonerModifierActive(ServerLevel level) {
        return hasActiveModifier(level, DungeonModifierDefinition.SUMMONER);
    }

    Effect recordMoltenDamageEventSeen() {
        moltenDamageEventsSeen++;
        return Effect.CHANGED;
    }

    Effect recordMoltenDamageEventWithLivingAttacker() {
        moltenDamageEventsWithLivingAttacker++;
        return Effect.CHANGED;
    }

    Effect recordMoltenTrackedTargetEvent() {
        moltenDamageEventsWithTrackedTarget++;
        return Effect.CHANGED;
    }

    Effect recordMoltenRejection(String reason) {
        lastMoltenRejectionReason = reason;
        return Effect.CHANGED;
    }

    Effect recordMoltenRetaliation(LivingEntity attacker) {
        lastMoltenModifierName = DungeonModifierDefinition.MOLTEN.getDisplayName();
        lastMoltenRetaliationTick = lifecycle.snapshot().tickCounter();
        lastMoltenRetaliationTarget = attacker.getName().getString()
            + " (" + attacker.getType().getDescription().getString() + ")";
        totalMoltenRetaliations++;
        lastMoltenRejectionReason = "APPLIED";
        return Effect.CHANGED;
    }

    RemovalState removalState() {
        return new RemovalState(hadAnyMobConfirmedRemoved);
    }

    void importRemovalState(RemovalState state) {
        hadAnyMobConfirmedRemoved = state.hadAnyMobConfirmedRemoved();
    }

    TelemetryState telemetryState() {
        return new TelemetryState(
            eliteDefeated,
            lastSpawnRequestedCount,
            lastSpawnActualCount,
            lastSpawnSkippedReason,
            lastSpawnStrategy,
            lastSpawnMeleeUsedCount,
            lastSpawnRangedUsedCount,
            lastSpawnFlankUsedCount,
            lastSpawnHighgroundUsedCount,
            lastSpawnGenericUsedCount,
            lastSpawnModifierSummary,
            lastSpawnArmorValue,
            lastSpawnArmorToughnessValue,
            encounterTargetCount,
            waveSize,
            wavesSpawned,
            waveCooldownTicks,
            lastCompositionBudget,
            lastCompositionSpent,
            lastCompositionSummary,
            lastMoltenModifierName,
            lastMoltenRetaliationTick,
            lastMoltenRetaliationTarget,
            totalMoltenRetaliations,
            moltenDamageEventsSeen,
            moltenDamageEventsWithLivingAttacker,
            moltenDamageEventsWithTrackedTarget,
            lastMoltenRejectionReason,
            summonerPulseProgressTicks,
            lastSummonerModifierName,
            lastSummonPulseTick,
            totalSummonsThisRoom,
            lastSummonResult,
            lastSummonRejectionReason,
            lastSummonerEntityType,
            lastSummonedEntityType,
            regenerativePulseProgressTicks,
            lastRegenModifierName,
            lastRegenPulseTick,
            lastRegenPulseHealedCount,
            lastRegenPulseTotalHealthRestored,
            eliteMobUuid,
            bossMobUuid,
            bossSpawnAttempted
        );
    }

    void importTelemetryState(TelemetryState state) {
        eliteDefeated = state.eliteDefeated();
        lastSpawnRequestedCount = state.lastSpawnRequestedCount();
        lastSpawnActualCount = state.lastSpawnActualCount();
        lastSpawnSkippedReason = state.lastSpawnSkippedReason();
        lastSpawnStrategy = state.lastSpawnStrategy();
        lastSpawnMeleeUsedCount = state.lastSpawnMeleeUsedCount();
        lastSpawnRangedUsedCount = state.lastSpawnRangedUsedCount();
        lastSpawnFlankUsedCount = state.lastSpawnFlankUsedCount();
        lastSpawnHighgroundUsedCount = state.lastSpawnHighgroundUsedCount();
        lastSpawnGenericUsedCount = state.lastSpawnGenericUsedCount();
        lastSpawnModifierSummary = state.lastSpawnModifierSummary();
        lastSpawnArmorValue = state.lastSpawnArmorValue();
        lastSpawnArmorToughnessValue = state.lastSpawnArmorToughnessValue();
        encounterTargetCount = state.encounterTargetCount();
        waveSize = state.waveSize();
        wavesSpawned = state.wavesSpawned();
        waveCooldownTicks = state.waveCooldownTicks();
        lastCompositionBudget = state.lastCompositionBudget();
        lastCompositionSpent = state.lastCompositionSpent();
        lastCompositionSummary = state.lastCompositionSummary();
        lastMoltenModifierName = state.lastMoltenModifierName();
        lastMoltenRetaliationTick = state.lastMoltenRetaliationTick();
        lastMoltenRetaliationTarget = state.lastMoltenRetaliationTarget();
        totalMoltenRetaliations = state.totalMoltenRetaliations();
        moltenDamageEventsSeen = state.moltenDamageEventsSeen();
        moltenDamageEventsWithLivingAttacker = state.moltenDamageEventsWithLivingAttacker();
        moltenDamageEventsWithTrackedTarget = state.moltenDamageEventsWithTrackedTarget();
        lastMoltenRejectionReason = state.lastMoltenRejectionReason();
        summonerPulseProgressTicks = state.summonerPulseProgressTicks();
        lastSummonerModifierName = state.lastSummonerModifierName();
        lastSummonPulseTick = state.lastSummonPulseTick();
        totalSummonsThisRoom = state.totalSummonsThisRoom();
        lastSummonResult = state.lastSummonResult();
        lastSummonRejectionReason = state.lastSummonRejectionReason();
        lastSummonerEntityType = state.lastSummonerEntityType();
        lastSummonedEntityType = state.lastSummonedEntityType();
        regenerativePulseProgressTicks = state.regenerativePulseProgressTicks();
        lastRegenModifierName = state.lastRegenModifierName();
        lastRegenPulseTick = state.lastRegenPulseTick();
        lastRegenPulseHealedCount = state.lastRegenPulseHealedCount();
        lastRegenPulseTotalHealthRestored = state.lastRegenPulseTotalHealthRestored();
        eliteMobUuid = state.eliteMobUuid();
        bossMobUuid = state.bossMobUuid();
        bossSpawnAttempted = state.bossSpawnAttempted() || bossMobUuid != null;
    }

    RosterState rosterState() {
        return new RosterState(
            List.copyOf(trackedMobUuids),
            List.copyOf(summonerMobUuids),
            List.copyOf(summonedMobUuids),
            java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(summonedMobOwners))
        );
    }

    void importRosterState(RosterState state) {
        trackedMobUuids.clear();
        trackedMobUuids.addAll(state.trackedMobUuids());
        summonerMobUuids.clear();
        summonerMobUuids.addAll(state.summonerMobUuids());
        summonedMobUuids.clear();
        summonedMobUuids.addAll(state.summonedMobUuids());
        summonedMobOwners.clear();
        summonedMobOwners.putAll(state.summonedMobOwners());
    }

    CombatProgressionState combatProgressionState() {
        return new CombatProgressionState(
            pendingBlasts.stream()
                .map(blast -> new PendingBlastState(
                    blast.position.x, blast.position.y, blast.position.z, blast.fuseTicks
                ))
                .toList(),
            riftboundCarrierUuid,
            riftboundPulseTicks,
            riftboundTotalSummons,
            Map.copyOf(gravityFlightTicks),
            Map.copyOf(gravityGraceTicks),
            Map.copyOf(gravityHeavyTicks),
            Map.copyOf(gravityHunterByPlayer),
            Map.copyOf(gravityChannelTicks)
        );
    }

    void importCombatProgressionState(CombatProgressionState state) {
        resetCombatProgressionState();
        pendingBlasts.addAll(state.pendingBlasts.stream()
            .map(blast -> new PendingBlast(
                new Vec3(blast.x, blast.y, blast.z), Math.max(0, blast.fuseTicks)
            ))
            .toList());
        riftboundCarrierUuid = state.riftboundCarrierUuid;
        riftboundPulseTicks = Math.max(0, state.riftboundPulseTicks);
        riftboundTotalSummons = Math.max(0, state.riftboundTotalSummons);
        gravityFlightTicks.putAll(state.gravityFlightTicks);
        gravityGraceTicks.putAll(state.gravityGraceTicks);
        gravityHeavyTicks.putAll(state.gravityHeavyTicks);
        gravityHunterByPlayer.putAll(state.gravityHunterByPlayer);
        gravityHunterByPlayer.forEach((playerId, hunterId) ->
            gravityPlayerByHunter.put(hunterId, playerId));
        gravityChannelTicks.putAll(state.gravityChannelTicks);
    }

    CompoundTag modifierStateTag() {
        return modifierRuntime.saveState();
    }

    String modifierRolePresentationSummary(ServerLevel level) {
        return modifierRuntime.rolePresentationSummary(level);
    }

    void importModifierState(CompoundTag tag) {
        modifierRuntime.loadState(tag == null ? new CompoundTag() : tag);
    }

    record RemovalState(boolean hadAnyMobConfirmedRemoved) {
    }

    record TelemetryState(
        boolean eliteDefeated,
        int lastSpawnRequestedCount,
        int lastSpawnActualCount,
        String lastSpawnSkippedReason,
        String lastSpawnStrategy,
        int lastSpawnMeleeUsedCount,
        int lastSpawnRangedUsedCount,
        int lastSpawnFlankUsedCount,
        int lastSpawnHighgroundUsedCount,
        int lastSpawnGenericUsedCount,
        String lastSpawnModifierSummary,
        double lastSpawnArmorValue,
        double lastSpawnArmorToughnessValue,
        int encounterTargetCount,
        int waveSize,
        int wavesSpawned,
        int waveCooldownTicks,
        int lastCompositionBudget,
        int lastCompositionSpent,
        String lastCompositionSummary,
        String lastMoltenModifierName,
        int lastMoltenRetaliationTick,
        String lastMoltenRetaliationTarget,
        int totalMoltenRetaliations,
        int moltenDamageEventsSeen,
        int moltenDamageEventsWithLivingAttacker,
        int moltenDamageEventsWithTrackedTarget,
        String lastMoltenRejectionReason,
        int summonerPulseProgressTicks,
        String lastSummonerModifierName,
        int lastSummonPulseTick,
        int totalSummonsThisRoom,
        String lastSummonResult,
        String lastSummonRejectionReason,
        String lastSummonerEntityType,
        String lastSummonedEntityType,
        int regenerativePulseProgressTicks,
        String lastRegenModifierName,
        int lastRegenPulseTick,
        int lastRegenPulseHealedCount,
        double lastRegenPulseTotalHealthRestored,
        UUID eliteMobUuid,
        UUID bossMobUuid,
        boolean bossSpawnAttempted
    ) {
    }

    record RosterState(
        List<UUID> trackedMobUuids,
        List<UUID> summonerMobUuids,
        List<UUID> summonedMobUuids,
        Map<UUID, UUID> summonedMobOwners
    ) {
    }

    record CombatProgressionState(
        List<PendingBlastState> pendingBlasts,
        UUID riftboundCarrierUuid,
        int riftboundPulseTicks,
        int riftboundTotalSummons,
        Map<UUID, Integer> gravityFlightTicks,
        Map<UUID, Integer> gravityGraceTicks,
        Map<UUID, Integer> gravityHeavyTicks,
        Map<UUID, UUID> gravityHunterByPlayer,
        Map<UUID, Integer> gravityChannelTicks
    ) {
    }

    record PendingBlastState(double x, double y, double z, int fuseTicks) {
    }

    private record PendingBlast(Vec3 position, int fuseTicks) {
    }

    enum SpawnRole {
        MELEE,
        RANGED,
        FLANK,
        HIGHGROUND,
        ELITE,
        GENERIC
    }

    record SpawnAnchorChoice(BlockPos anchor, SpawnRole role) {
    }

    record SpawnedMob(Mob mob, DungeonEnemyDefinition definition) {
    }

    record PlannedPopulationDemand(
        int plannedTotal,
        int plannedEliteSlots,
        int requiredRoleSlots
    ) {
    }

    record Effect(boolean changed, String roomFailureReason, String activationFailureReason) {
        static final Effect NONE = new Effect(false, "NONE", "NONE");
        static final Effect CHANGED = new Effect(true, "NONE", "NONE");
    }
}
