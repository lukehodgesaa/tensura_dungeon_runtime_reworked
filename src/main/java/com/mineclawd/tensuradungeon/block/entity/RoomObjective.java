package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.runtime.DungeonSoundHooks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Monster;

final class RoomObjective {
    private static final int SURVIVE_TIMER_TARGET_TICKS = 30 * 20;
    private static final int RUNE_MIN_REQUIRED = 1;
    private static final int RUNE_MAX_REQUIRED = 3;
    private static final double RUNE_ACTIVATION_RADIUS = 3.0D;

    String rewardObjectiveSnapshot() {
        if (objectiveDisabled) {
            return "NONE";
        }
        if (activeObjectiveType != null) {
            return activeObjectiveType.name();
        }
        if (configuredObjectiveType != null) {
            return configuredObjectiveType.name();
        }
        return "UNKNOWN_OBJECTIVE";
    }

    final PlayerSessionAssociation playerSessionAssociation;

    RoomObjectiveType configuredObjectiveType = RoomObjectiveType.CLEAR_ALL;
    boolean objectiveOverrideEnabled;
    boolean objectiveDisabled;
    RoomObjectiveType activeObjectiveType = RoomObjectiveType.CLEAR_ALL;
    boolean objectiveCompleted;
    String objectiveSelectionMode = "AUTO";
    String autoObjectiveSelected = "NONE";
    String autoObjectiveCandidateList = "NONE";
    String autoObjectiveRejectionSummary = "NONE";
    int autoObjectiveRollValue = -1;
    int lastSurviveTimerMilestoneSeconds = -1;
    String lastObjectiveResult = "NONE";
    String lastObjectiveRejectionReason = "NONE";
    String objectiveTargetEntityType = "NONE";
    int objectiveDurationTicks = SURVIVE_TIMER_TARGET_TICKS;
    int objectiveRemainingTicks;
    boolean objectivePaused;
    int objectivePlayersInBounds;
    final List<BlockPos> selectedRuneAnchorPositions = new ArrayList<>();
    final Set<UUID> runeCrystalUuids = new HashSet<>();
    int runeRequiredCount;
    int runeActivatedCount;
    boolean objectiveActive;
    int objectiveTimerTicks;

    RoomObjective(PlayerSessionAssociation playerSessionAssociation) {
        this.playerSessionAssociation = playerSessionAssociation;
    }

    void initializeForActivation(RoomObjectiveType selectedObjectiveType, int durationTicks) {
        objectiveActive = !objectiveDisabled;
        objectiveTimerTicks = 0;
        activeObjectiveType = selectedObjectiveType == null ? RoomObjectiveType.CLEAR_ALL : selectedObjectiveType;
        objectiveCompleted = false;
        lastObjectiveRejectionReason = "NONE";
        objectiveTargetEntityType = "NONE";
        objectiveDurationTicks = durationTicks;
        objectiveRemainingTicks = activeObjectiveType.usesTimer() ? objectiveDurationTicks : 0;
        objectivePaused = false;
        objectivePlayersInBounds = 0;
    }

    void configure(RoomObjectiveType objectiveType) {
        objectiveDisabled = false;
        objectiveOverrideEnabled = objectiveType != null;
        configuredObjectiveType = objectiveType == null ? RoomObjectiveType.CLEAR_ALL : objectiveType;
        resetObjectiveRuntimeState();
    }

    void setDisabled(boolean disabled) {
        objectiveDisabled = disabled;
        if (disabled) {
            objectiveOverrideEnabled = false;
        }
        resetObjectiveRuntimeState();
    }

    void recordAutomaticCompletion(String completionReason) {
        lastObjectiveResult = completionReason;
        objectiveCompleted = true;
        objectiveActive = false;
    }

    void deactivateForRoomCompletion() {
        objectiveActive = false;
    }

    void markRoomCompleted() {
        objectiveCompleted = true;
    }

    ClearAllEffect updateClearAllState(boolean rosterEmpty, boolean pendingWaves, boolean confirmedRemoval) {
        if (activeObjectiveType.usesEliteTarget() || activeObjectiveType.usesRuneTargets()) {
            return ClearAllEffect.NONE;
        }
        if (objectiveActive && activeObjectiveType.usesTimer()) {
            return ClearAllEffect.NONE;
        }
        if (!rosterEmpty || pendingWaves) {
            return ClearAllEffect.NONE;
        }
        objectiveActive = false;
        if (!confirmedRemoval) {
            return ClearAllEffect.WARN_EMPTY;
        }
        lastObjectiveResult = "CLEAR_ALL_COMPLETE";
        objectiveCompleted = true;
        return ClearAllEffect.COMPLETE;
    }

    int objectiveTimerMaxTicks() {
        return activeObjectiveType.usesTimer() ? SURVIVE_TIMER_TARGET_TICKS : 0;
    }

    void resetRuntimeState(int surviveTimerTargetTicks, boolean overrideEnabled) {
        objectiveActive = false;
        objectiveTimerTicks = 0;
        activeObjectiveType = RoomObjectiveType.CLEAR_ALL;
        objectiveCompleted = false;
        objectiveSelectionMode = objectiveDisabled ? "NONE" : overrideEnabled ? "FORCED" : "AUTO";
        autoObjectiveSelected = "NONE";
        lastSurviveTimerMilestoneSeconds = -1;
        autoObjectiveCandidateList = "NONE";
        autoObjectiveRejectionSummary = "NONE";
        autoObjectiveRollValue = -1;
        lastObjectiveResult = "NONE";
        lastObjectiveRejectionReason = "NONE";
        objectiveTargetEntityType = "NONE";
        objectiveDurationTicks = surviveTimerTargetTicks;
        objectiveRemainingTicks = 0;
        objectivePaused = false;
        objectivePlayersInBounds = 0;
        selectedRuneAnchorPositions.clear();
        runeCrystalUuids.clear();
        runeRequiredCount = 0;
        runeActivatedCount = 0;
    }

    CompletionEffect updateObjective(ServerLevel level, Context context, UUID eliteMobUuid) {
        if (!objectiveActive) {
            return CompletionEffect.NONE;
        }

        if (activeObjectiveType.usesTimer()) {
            objectivePlayersInBounds = countAssociatedPlayersInBounds(level, context.bounds());
            if (objectivePlayersInBounds <= 0) {
                objectivePaused = true;
                lastObjectiveRejectionReason = "TIMER_PAUSED_NO_PLAYER_IN_BOUNDS";
                lastObjectiveResult = "TIMER_PAUSED_NO_PLAYER_IN_BOUNDS";
                return CompletionEffect.NONE;
            }

            objectivePaused = false;
            lastObjectiveRejectionReason = "NONE";
            lastObjectiveResult = objectiveTimerTicks <= 0 ? "TIMER_STARTED" : "TIMER_TICKING";
            objectiveTimerTicks += 1;
            objectiveRemainingTicks = Math.max(0, objectiveDurationTicks - objectiveTimerTicks);
            int remainingSeconds = objectiveRemainingTicks / 20;
            if ((remainingSeconds == 20 || remainingSeconds == 10 || remainingSeconds == 5) && remainingSeconds != lastSurviveTimerMilestoneSeconds && objectiveRemainingTicks % 20 == 0) {
                lastSurviveTimerMilestoneSeconds = remainingSeconds;
                DungeonSoundHooks.objectiveProgress(level, context.controllerPos());
                announceObjectiveProgress(level, context.bounds(), Component.literal("Survive: " + remainingSeconds + "s remaining."));
            }
            if (objectiveTimerTicks >= objectiveDurationTicks) {
                lastObjectiveResult = "TIMER_COMPLETED";
                objectiveCompleted = true;
                objectiveActive = false;
                objectivePaused = false;
                objectiveRemainingTicks = 0;
                announceObjectiveProgress(level, context.bounds(), Component.literal("Objective complete. Survived 30 seconds."));
                return new CompletionEffect("SURVIVE_TIMER_COMPLETE", false);
            }
            return CompletionEffect.NONE;
        }

        if (activeObjectiveType.usesRuneTargets()) {
            return updateRuneObjective(level, context);
        }

        if (activeObjectiveType.usesEliteTarget()) {
            if (eliteMobUuid == null) {
                return CompletionEffect.NONE;
            }
            Entity eliteEntity = level.getEntity(eliteMobUuid);
            if (eliteEntity == null || !eliteEntity.isAlive()) {
                lastObjectiveResult = "TARGET_KILLED";
                objectiveCompleted = true;
                objectiveActive = false;
                announceObjectiveProgress(level, context.bounds(), Component.literal("Elite target defeated."));
                return new CompletionEffect("KILL_ELITE_COMPLETE", true);
            }
        }
        return CompletionEffect.NONE;
    }

    void initializeRuneObjective(ServerLevel level, Context context) {
        clearRuneObjectiveState(level);
        List<BlockPos> eligibleAnchors = new ArrayList<>(context.objectiveAnchors());
        eligibleAnchors.removeIf(anchor -> context.hasBounds() && !context.isInsideBounds(anchor));
        eligibleAnchors.sort((left, right) -> Double.compare(left.distSqr(context.controllerPos()), right.distSqr(context.controllerPos())));
        if (eligibleAnchors.isEmpty()) {
            activeObjectiveType = RoomObjectiveType.CLEAR_ALL;
            lastObjectiveRejectionReason = "ACTIVATE_RUNES_NO_OBJECTIVE_ANCHORS";
            lastObjectiveResult = "ACTIVATE_RUNES_NO_OBJECTIVE_ANCHORS";
            return;
        }

        int required = Math.min(RUNE_MAX_REQUIRED, Math.max(RUNE_MIN_REQUIRED, eligibleAnchors.size()));
        for (int i = 0; i < required; i++) {
            BlockPos anchor = eligibleAnchors.get(i).immutable();
            EndCrystal crystal = EntityType.END_CRYSTAL.create(level);
            if (crystal == null) {
                continue;
            }
            crystal.setPos(anchor.getX() + 0.5D, anchor.getY() + 1.0D, anchor.getZ() + 0.5D);
            crystal.setShowBottom(false);
            level.addFreshEntity(crystal);
            selectedRuneAnchorPositions.add(anchor);
            runeCrystalUuids.add(crystal.getUUID());
        }
        runeRequiredCount = runeCrystalUuids.size();
        runeActivatedCount = 0;
        objectivePlayersInBounds = countAssociatedPlayersInBounds(level, context.bounds());
        if (runeRequiredCount <= 0) {
            activeObjectiveType = RoomObjectiveType.CLEAR_ALL;
            lastObjectiveRejectionReason = "ACTIVATE_RUNES_NO_OBJECTIVE_ANCHORS";
            lastObjectiveResult = "ACTIVATE_RUNES_NO_OBJECTIVE_ANCHORS";
            return;
        }
        lastObjectiveRejectionReason = "NONE";
        lastObjectiveResult = "RUNE_CRYSTALS_SPAWNED";
        announceObjectiveProgress(level, context.bounds(), Component.literal("Rune crystals marked."));
    }

    CompletionEffect updateRuneObjective(ServerLevel level, Context context) {
        objectivePlayersInBounds = countAssociatedPlayersInBounds(level, context.bounds());
        List<UUID> completedRunes = new ArrayList<>();
        for (UUID uuid : runeCrystalUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity == null || !entity.isAlive()) {
                completedRunes.add(uuid);
            }
        }
        if (!completedRunes.isEmpty()) {
            runeCrystalUuids.removeAll(completedRunes);
            runeActivatedCount = Math.min(runeRequiredCount, runeActivatedCount + completedRunes.size());
            lastObjectiveResult = runeCrystalUuids.isEmpty() ? "RUNE_ALL_COMPLETED" : "RUNE_ACTIVATED";
            lastObjectiveRejectionReason = "NONE";
            if (runeCrystalUuids.isEmpty()) {
                announceObjectiveProgress(level, context.bounds(), Component.literal("All rune crystals broken."));
            } else {
                announceObjectiveProgress(level, context.bounds(), Component.literal("Rune activated: " + runeActivatedCount + " / " + runeRequiredCount + "."));
            }
        } else if (!runeCrystalUuids.isEmpty()) {
            lastObjectiveResult = "ACTIVATE_RUNES_IN_PROGRESS";
        }
        if (runeRequiredCount > 0 && runeCrystalUuids.isEmpty()) {
            objectiveCompleted = true;
            objectiveActive = false;
            return new CompletionEffect("ACTIVATE_RUNES_COMPLETE", false);
        }
        return CompletionEffect.NONE;
    }

    void clearRuneObjectiveState(ServerLevel level) {
        if (level != null) {
            for (UUID uuid : runeCrystalUuids) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
        selectedRuneAnchorPositions.clear();
        runeCrystalUuids.clear();
        runeRequiredCount = 0;
        runeActivatedCount = 0;
    }

    boolean activateNearestRune(
        ServerLevel level,
        ServerPlayer player,
        RoomControllerBlockEntity.RoomState roomState,
        RoomControllerBlockEntity.RoomBounds bounds,
        BlockPos controllerPos
    ) {
        if (roomState != RoomControllerBlockEntity.RoomState.ACTIVE || !activeObjectiveType.usesRuneTargets()) {
            lastObjectiveRejectionReason = "ROOM_NOT_ACTIVE";
            return false;
        }
        if (player == null || player.isSpectator()) {
            lastObjectiveRejectionReason = "RUNE_NOT_IN_RANGE";
            return false;
        }
        boolean eligiblePlayer = playerSessionAssociation.isAssociated(player.getUUID())
            || (bounds != null && bounds.contains(player.blockPosition()));
        if (!eligiblePlayer) {
            lastObjectiveRejectionReason = "RUNE_NOT_IN_RANGE";
            return false;
        }
        Entity nearest = null;
        double nearestDistance = RUNE_ACTIVATION_RADIUS * RUNE_ACTIVATION_RADIUS;
        for (UUID uuid : runeCrystalUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            double distance = entity.distanceToSqr(player);
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }
        if (nearest == null) {
            lastObjectiveRejectionReason = "RUNE_NOT_IN_RANGE";
            lastObjectiveResult = "RUNE_NOT_IN_RANGE";
            return false;
        }
        nearest.discard();
        lastObjectiveRejectionReason = "NONE";
        lastObjectiveResult = "RUNE_ACTIVATED";
        DungeonSoundHooks.objectiveProgress(level, controllerPos);
        announceObjectiveProgress(level, bounds, Component.literal("Rune activated: " + Math.min(runeRequiredCount, runeActivatedCount + 1) + " / " + runeRequiredCount + "."));
        return true;
    }

    int getObjectiveDurationFor(RoomObjectiveType objectiveType) {
        return objectiveType != null && objectiveType.usesTimer() ? SURVIVE_TIMER_TARGET_TICKS : 0;
    }

    int countAssociatedPlayersInBounds(ServerLevel level, RoomControllerBlockEntity.RoomBounds bounds) {
        int count = 0;
        for (ServerPlayer player : playerSessionAssociation.validPlayers(level)) {
            if (bounds == null || bounds.contains(player.blockPosition())) {
                count++;
            }
        }
        return count;
    }

    RoomObjectiveType resolveObjectiveTypeForActivation(ServerLevel level, Context context) {
        if (objectiveDisabled) {
            objectiveSelectionMode = "NONE";
            autoObjectiveSelected = "NONE";
            autoObjectiveCandidateList = "NONE";
            autoObjectiveRejectionSummary = "OBJECTIVE_DISABLED";
            autoObjectiveRollValue = -1;
            lastObjectiveResult = "OBJECTIVE_DISABLED";
            lastObjectiveRejectionReason = "OBJECTIVE_DISABLED";
            return RoomObjectiveType.CLEAR_ALL;
        }
        if (!context.profile().allowsObjective() || context.profile() == RoomProfile.SAFE_ROOM) {
            objectiveSelectionMode = "AUTO";
            autoObjectiveSelected = "NONE";
            autoObjectiveCandidateList = "NONE";
            autoObjectiveRejectionSummary = "SAFE_ROOM_NO_COMBAT_OBJECTIVE";
            autoObjectiveRollValue = -1;
            lastObjectiveResult = "SAFE_ROOM_NO_COMBAT_OBJECTIVE";
            lastObjectiveRejectionReason = "SAFE_ROOM_NO_COMBAT_OBJECTIVE";
            return RoomObjectiveType.CLEAR_ALL;
        }
        if (objectiveOverrideEnabled) {
            objectiveSelectionMode = "FORCED";
            autoObjectiveSelected = "NONE";
            autoObjectiveCandidateList = "NONE";
            autoObjectiveRejectionSummary = "NONE";
            autoObjectiveRollValue = -1;
            lastObjectiveResult = "OBJECTIVE_FORCED";
            return configuredObjectiveType;
        }
        return selectAutoObjective(level, context);
    }

    RoomObjectiveType selectAutoObjective(ServerLevel level, Context context) {
        objectiveSelectionMode = "AUTO";
        List<WeightedObjectiveCandidate> candidates = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        candidates.add(new WeightedObjectiveCandidate(RoomObjectiveType.CLEAR_ALL, 60));
        if (isAutoKillEliteValid(context)) {
            candidates.add(new WeightedObjectiveCandidate(RoomObjectiveType.KILL_ELITE, 20));
        } else {
            rejected.add("OBJECTIVE_AUTO_REJECTED_NO_ELITE_TARGET");
        }
        if (isAutoSurviveTimerValid(context)) {
            candidates.add(new WeightedObjectiveCandidate(RoomObjectiveType.SURVIVE_TIMER, 15));
        }
        if (isAutoActivateRunesValid(context)) {
            candidates.add(new WeightedObjectiveCandidate(RoomObjectiveType.ACTIVATE_RUNES, 20));
        } else {
            rejected.add("OBJECTIVE_AUTO_REJECTED_NO_OBJECTIVE_ANCHORS");
        }

        autoObjectiveCandidateList = buildAutoCandidateSummary(candidates);
        autoObjectiveRejectionSummary = rejected.isEmpty() ? "NONE" : String.join(", ", rejected);
        int totalWeight = 0;
        for (WeightedObjectiveCandidate candidate : candidates) {
            totalWeight += candidate.weight();
        }
        if (totalWeight <= 0) {
            autoObjectiveSelected = RoomObjectiveType.CLEAR_ALL.name();
            autoObjectiveRollValue = -1;
            lastObjectiveResult = "OBJECTIVE_AUTO_FALLBACK_CLEAR_ALL";
            return RoomObjectiveType.CLEAR_ALL;
        }

        autoObjectiveRollValue = Math.floorMod((int) (levelHashSeed(level) + context.controllerPos().asLong() + context.floorNumber()), totalWeight);
        int running = 0;
        for (WeightedObjectiveCandidate candidate : candidates) {
            running += candidate.weight();
            if (autoObjectiveRollValue < running) {
                autoObjectiveSelected = candidate.objectiveType().name();
                lastObjectiveResult = switch (candidate.objectiveType()) {
                    case KILL_ELITE -> "OBJECTIVE_AUTO_SELECTED_KILL_ELITE";
                    case SURVIVE_TIMER -> "OBJECTIVE_AUTO_SELECTED_SURVIVE_TIMER";
                    case ACTIVATE_RUNES -> "OBJECTIVE_AUTO_SELECTED_ACTIVATE_RUNES";
                    default -> "OBJECTIVE_AUTO_SELECTED_CLEAR_ALL";
                };
                return candidate.objectiveType();
            }
        }

        autoObjectiveSelected = RoomObjectiveType.CLEAR_ALL.name();
        lastObjectiveResult = "OBJECTIVE_AUTO_FALLBACK_CLEAR_ALL";
        return RoomObjectiveType.CLEAR_ALL;
    }

    private boolean isAutoKillEliteValid(Context context) {
        return context.profile().allowsCombat() && context.ownedNormalCombatAnchorCount() > 0
            && (!context.eliteSpawnAnchors().isEmpty() || context.profileInitialSpawnCount() > 0);
    }

    private boolean isAutoSurviveTimerValid(Context context) {
        return context.profile().allowsCombat() && context.profile() != RoomProfile.SAFE_ROOM;
    }

    private boolean isAutoActivateRunesValid(Context context) {
        return context.profile().allowsCombat() && !context.objectiveAnchors().isEmpty();
    }

    private String buildAutoCandidateSummary(List<WeightedObjectiveCandidate> candidates) {
        if (candidates.isEmpty()) {
            return "NONE";
        }
        List<String> parts = new ArrayList<>();
        for (WeightedObjectiveCandidate candidate : candidates) {
            parts.add(candidate.objectiveType().name() + "(" + candidate.weight() + ")");
        }
        return String.join(", ", parts);
    }

    private int levelHashSeed(ServerLevel level) {
        return level == null ? 0 : level.dimension().location().hashCode();
    }

    void resetObjectiveRuntimeState() {
        resetRuntimeState(SURVIVE_TIMER_TARGET_TICKS, objectiveOverrideEnabled);
    }

    void announceObjectiveStart(ServerLevel level, Context context) {
        if (context.profile() == RoomProfile.SAFE_ROOM) {
            announceObjectiveProgress(level, context.bounds(), Component.literal("Safe room reached."));
            return;
        }
        if (objectiveDisabled) {
            announceObjectiveProgress(level, context.bounds(), Component.literal("Passage secured."));
            return;
        }
        DungeonSoundHooks.objectiveStarted(level, context.controllerPos());
        Component message = switch (activeObjectiveType) {
            case KILL_ELITE -> Component.literal("Objective: Defeat the elite target.");
            case SURVIVE_TIMER -> Component.literal("Objective: Survive for 30 seconds.");
            case ACTIVATE_RUNES -> Component.literal("Objective: Break the rune crystals.");
            default -> Component.literal("Objective: Clear all enemies.");
        };
        announceObjectiveProgress(level, context.bounds(), message);
        if (activeObjectiveType.usesEliteTarget()) {
            announceObjectiveProgress(level, context.bounds(), Component.literal("Elite target marked."));
        }
    }

    void announceObjectiveProgress(ServerLevel level, RoomControllerBlockEntity.RoomBounds bounds, Component message) {
        if (message == null) {
            return;
        }
        boolean sent = false;
        for (ServerPlayer player : playerSessionAssociation.validPlayers(level)) {
            if (bounds != null && !bounds.contains(player.blockPosition())) {
                continue;
            }
            player.sendSystemMessage(message);
            sent = true;
        }
        if (sent) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            if (bounds == null || bounds.contains(player.blockPosition())) {
                player.sendSystemMessage(message);
            }
        }
    }

    UUID selectKillEliteTarget(ServerLevel level, UUID currentEliteMobUuid, Set<UUID> trackedMobs, Set<UUID> summonedMobs) {
        Entity preferredTarget = currentEliteMobUuid == null ? null : level.getEntity(currentEliteMobUuid);
        if (!isEligibleKillEliteTarget(preferredTarget, trackedMobs, summonedMobs)) {
            preferredTarget = null;
        }
        if (preferredTarget == null) {
            for (UUID uuid : trackedMobs) {
                Entity candidate = level.getEntity(uuid);
                if (!isEligibleKillEliteTarget(candidate, trackedMobs, summonedMobs)) {
                    continue;
                }
                preferredTarget = candidate;
                break;
            }
        }
        if (preferredTarget == null) {
            activeObjectiveType = RoomObjectiveType.CLEAR_ALL;
            objectiveTargetEntityType = "NONE";
            lastObjectiveResult = "FALLBACK_TO_CLEAR_ALL";
            lastObjectiveRejectionReason = "NO_ELIGIBLE_ELITE_TARGET";
            return null;
        }

        objectiveTargetEntityType = EntityType.getKey(preferredTarget.getType()).toString();
        lastObjectiveResult = "TARGET_SELECTED";
        lastObjectiveRejectionReason = "NONE";
        if (preferredTarget instanceof LivingEntity livingTarget) {
            livingTarget.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        }
        return preferredTarget.getUUID();
    }

    private boolean isEligibleKillEliteTarget(Entity entity, Set<UUID> trackedMobs, Set<UUID> summonedMobs) {
        if (!(entity instanceof Monster monster)) {
            return false;
        }
        UUID uuid = monster.getUUID();
        return monster.isAlive() && trackedMobs.contains(uuid) && !summonedMobs.contains(uuid);
    }

    double getNearestRuneDistance(ServerPlayer player, ServerLevel level) {
        if (player == null) {
            return -1.0D;
        }
        double nearest = Double.MAX_VALUE;
        for (UUID uuid : runeCrystalUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            nearest = Math.min(nearest, Math.sqrt(entity.distanceToSqr(player)));
        }
        return nearest == Double.MAX_VALUE ? -1.0D : nearest;
    }

    double getRuneActivationRadius() {
        return RUNE_ACTIVATION_RADIUS;
    }

    int getRunePlayersInRange(ServerLevel level) {
        int count = 0;
        for (ServerPlayer player : playerSessionAssociation.validPlayers(level)) {
            if (getNearestRuneDistance(player, level) >= 0.0D && getNearestRuneDistance(player, level) <= RUNE_ACTIVATION_RADIUS) {
                count++;
            }
        }
        return count;
    }

    int getRemainingRuneCrystalCount(ServerLevel level) {
        int count = 0;
        for (UUID uuid : runeCrystalUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                count++;
            }
        }
        return count;
    }

    boolean isTrackedRuneCrystal(Entity entity) {
        return entity != null && runeCrystalUuids.contains(entity.getUUID());
    }

    PersistentState persistentState() {
        return new PersistentState(
            configuredObjectiveType,
            objectiveOverrideEnabled,
            objectiveDisabled,
            activeObjectiveType,
            objectiveCompleted,
            objectiveSelectionMode,
            autoObjectiveSelected,
            autoObjectiveCandidateList,
            autoObjectiveRejectionSummary,
            autoObjectiveRollValue,
            lastObjectiveResult,
            lastObjectiveRejectionReason,
            objectiveTargetEntityType,
            objectiveDurationTicks,
            objectiveRemainingTicks,
            objectivePaused,
            objectivePlayersInBounds,
            runeRequiredCount,
            runeActivatedCount,
            List.copyOf(selectedRuneAnchorPositions),
            List.copyOf(runeCrystalUuids),
            objectiveActive,
            objectiveTimerTicks
        );
    }

    void importPersistentState(PersistentState state) {
        configuredObjectiveType = state.configuredObjectiveType();
        objectiveOverrideEnabled = state.objectiveOverrideEnabled();
        objectiveDisabled = state.objectiveDisabled();
        activeObjectiveType = state.activeObjectiveType();
        objectiveCompleted = state.objectiveCompleted();
        objectiveSelectionMode = state.objectiveSelectionMode();
        autoObjectiveSelected = state.autoObjectiveSelected();
        autoObjectiveCandidateList = state.autoObjectiveCandidateList();
        autoObjectiveRejectionSummary = state.autoObjectiveRejectionSummary();
        autoObjectiveRollValue = state.autoObjectiveRollValue();
        lastObjectiveResult = state.lastObjectiveResult();
        lastObjectiveRejectionReason = state.lastObjectiveRejectionReason();
        objectiveTargetEntityType = state.objectiveTargetEntityType();
        objectiveDurationTicks = state.objectiveDurationTicks();
        objectiveRemainingTicks = state.objectiveRemainingTicks();
        objectivePaused = state.objectivePaused();
        objectivePlayersInBounds = state.objectivePlayersInBounds();
        runeRequiredCount = state.runeRequiredCount();
        runeActivatedCount = state.runeActivatedCount();
        selectedRuneAnchorPositions.clear();
        selectedRuneAnchorPositions.addAll(state.selectedRuneAnchorPositions());
        runeCrystalUuids.clear();
        runeCrystalUuids.addAll(state.runeCrystalUuids());
        objectiveActive = state.objectiveActive();
        objectiveTimerTicks = state.objectiveTimerTicks();
    }

    record PersistentState(
        RoomObjectiveType configuredObjectiveType,
        boolean objectiveOverrideEnabled,
        boolean objectiveDisabled,
        RoomObjectiveType activeObjectiveType,
        boolean objectiveCompleted,
        String objectiveSelectionMode,
        String autoObjectiveSelected,
        String autoObjectiveCandidateList,
        String autoObjectiveRejectionSummary,
        int autoObjectiveRollValue,
        String lastObjectiveResult,
        String lastObjectiveRejectionReason,
        String objectiveTargetEntityType,
        int objectiveDurationTicks,
        int objectiveRemainingTicks,
        boolean objectivePaused,
        int objectivePlayersInBounds,
        int runeRequiredCount,
        int runeActivatedCount,
        List<BlockPos> selectedRuneAnchorPositions,
        List<UUID> runeCrystalUuids,
        boolean objectiveActive,
        int objectiveTimerTicks
    ) {
    }

    record Context(
        BlockPos controllerPos,
        RoomControllerBlockEntity.RoomBounds bounds,
        RoomProfile profile,
        int ownedNormalCombatAnchorCount,
        List<BlockPos> eliteSpawnAnchors,
        List<BlockPos> objectiveAnchors,
        int profileInitialSpawnCount,
        int floorNumber
    ) {
        Context {
            eliteSpawnAnchors = List.copyOf(eliteSpawnAnchors);
            objectiveAnchors = List.copyOf(objectiveAnchors);
        }

        boolean hasBounds() {
            return bounds != null;
        }

        boolean isInsideBounds(BlockPos pos) {
            return bounds != null && bounds.contains(pos);
        }
    }

    record CompletionEffect(String completionReason, boolean eliteDefeated) {
        static final CompletionEffect NONE = new CompletionEffect("NONE", false);

        boolean completesRoom() {
            return !"NONE".equals(completionReason);
        }
    }

    enum ClearAllEffect {
        NONE,
        COMPLETE,
        WARN_EMPTY
    }

    record WeightedObjectiveCandidate(RoomObjectiveType objectiveType, int weight) {
    }
}
