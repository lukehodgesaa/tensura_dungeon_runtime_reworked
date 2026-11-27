package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.runtime.RoomBudgetResult;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;

public record RoomDebugSnapshot(Core core, Objective objective, Encounter encounter, Barrier barrier, Markers markers) {
    public record Core(
        RoomControllerBlockEntity.RoomState roomState,
        Difficulty difficulty,
        int activationRadius,
        String activationAreaMode,
        String activationInsetStatus,
        boolean activationBlocked,
        String activationBlockReason,
        boolean requiresActiveRun,
        boolean registeredToRun,
        String runActivationBlockReason,
        RoomProfile roomProfile,
        RoomBudgetResult budgetResult,
        double profileBudgetMultiplier,
        int profileBaseMaxAlive,
        int profileTargetMaxAlive,
        int profileSuggestedMaxAlive,
        int profileInitialSpawnCount,
        int activeFloorNumber,
        int finalCalculatedRoomBudget,
        int finalCalculatedMaxAlive,
        boolean profileAllowsCombat,
        boolean profileAllowsObjective,
        String profileNotes,
        String profileCompletionPolicy,
        boolean bossCapableRoom,
        boolean spawnBossAnchorPresent,
        int spawnBossAnchorCount,
        String profileDirectorFallbackReason,
        String lastCompletionReason,
        boolean ruleZoneProtectionActive,
        boolean protectionGraceActive,
        long protectionGraceTicksRemaining,
        String protectedBoundsMode,
        String lastActivationFailure,
        int associatedActivePlayerCount,
        int nearbyActiveRoomCount,
        boolean completed
    ) {
    }

    public record Objective(
        RoomObjectiveType configuredType,
        RoomObjectiveType activeType,
        boolean active,
        int timerTicks,
        int timerMaxTicks,
        String selectionMode,
        String forcedOverrideSummary,
        String autoSelected,
        String autoCandidateList,
        String autoRejectionSummary,
        int autoRollValue,
        boolean completed,
        String lastResult,
        String lastRejectionReason,
        int clearAllRemainingMobCount,
        String eliteMobUuid,
        boolean elitePresent,
        boolean eliteAlive,
        boolean eliteDefeated,
        String targetUuid,
        String targetEntityType,
        boolean targetAlive,
        int killEliteTargetRemaining,
        int durationTicks,
        int remainingTicks,
        int elapsedTicks,
        boolean surviveTimerActive,
        boolean surviveTimerPaused,
        int objectivePlayersInBounds,
        boolean runeObjectiveActive,
        int runeRequiredCount,
        int runeActivatedCount,
        int runeRemainingCount,
        String runeSelectedPositionsSummary,
        String runeCrystalUuidsSummary,
        double runeActivationRadius,
        int runePlayersInRange,
        int remainingRuneCrystalCount
    ) {
    }

    public record Encounter(
        String lastSpawnStrategy,
        String lastSpawnModifierSummary,
        int pendingEnemyCount,
        int waveSize,
        int wavesSpawned,
        int waveCooldownTicks,
        String waveSummary,
        double lastSpawnArmorValue,
        double lastSpawnArmorToughnessValue,
        String moltenModifierDebugStatus,
        String lastMoltenRetaliationTickSummary,
        String lastMoltenRetaliationTargetSummary,
        int totalMoltenRetaliations,
        int moltenDamageEventsSeen,
        int moltenDamageEventsWithLivingAttacker,
        int moltenDamageEventsWithTrackedTarget,
        String lastMoltenRejectionReason,
        String regenModifierDebugStatus,
        int regenerativeIntervalTicks,
        String lastRegenPulseTickSummary,
        int lastRegenPulseHealedCount,
        double lastRegenPulseTotalHealthRestored,
        String summonerModifierDebugStatus,
        int summonerIntervalTicks,
        int summonersSelectedCount,
        int activeSummonersCount,
        int activeSummonedAddsCount,
        int totalSummonsThisRoom,
        String lastSummonPulseTickSummary,
        String lastSummonResult,
        String lastSummonRejectionReason,
        String lastSummonerEntityType,
        String lastSummonedEntityType,
        boolean enemyPoolRegistryEnabled,
        String lastEnemyPoolSpawnSource,
        String lastEnemyPoolRoleRequested,
        String lastEnemyPoolEntitySelected,
        String lastEnemyPoolFamily,
        String lastEnemyPoolSourceMod,
        String lastEnemyPoolFallbackReason,
        int lastEnemyPoolCandidateCount,
        String lastEnemyPoolError,
        int currentAliveTrackedMobCount,
        int trackedMobCount,
        int lastSpawnRequestedCount,
        int lastSpawnActualCount,
        String lastSpawnSkippedReason,
        int lastSpawnMeleeUsedCount,
        int lastSpawnRangedUsedCount,
        int lastSpawnFlankUsedCount,
        int lastSpawnHighgroundUsedCount,
        int lastSpawnGenericUsedCount,
        int unresolvedTrackedMobCount,
        int staleTrackedMobCount,
        int mobResetCooldownCount
    ) {
    }

    public record Barrier(
        String lastMaterialSummary,
        String lastSolveMode,
        String lastSolveResult,
        String lastSolveReason,
        String lastPlaneSummary,
        String lastVolumeBoundsSummary,
        String lastVolumeSizeSummary,
        int lastFillCandidateCount,
        int lastSolvedWidth,
        int lastSolvedHeight,
        int lastSolvedPositionCount,
        int lastPlacedBlockCount,
        int lastSkippedBlockCount,
        int lastClearedBlockCount,
        List<String> lastDebugLines,
        int placedBlockCount,
        int runtimePositionCount,
        int previewBlockCount,
        int barrierPreviewBlockCount
    ) {
        public Barrier {
            lastDebugLines = List.copyOf(lastDebugLines);
        }
    }

    public record Markers(
        RoomControllerBlockEntity.BoundsMode boundsMode,
        RoomControllerBlockEntity.RoomBounds roomBounds,
        RoomControllerBlockEntity.RoomBounds activationBounds,
        List<BlockPos> spawnAnchors,
        List<BlockPos> rangedSpawnAnchors,
        List<BlockPos> flankSpawnAnchors,
        List<BlockPos> highgroundSpawnAnchors,
        List<BlockPos> eliteSpawnAnchors,
        List<BlockPos> bossSpawnAnchors,
        List<BlockPos> barrierAnchors,
        List<BlockPos> barrierAnchor1Anchors,
        List<BlockPos> barrierAnchor2Anchors,
        List<BlockPos> barrierAnchor3Anchors,
        List<BlockPos> barrierAnchor4Anchors,
        List<BlockPos> mobResetAnchors,
        List<BlockPos> exitAnchors,
        List<BlockPos> boundsAnchors,
        List<BlockPos> objectiveAnchors,
        List<BlockPos> interactAnchors,
        int rawSpawnAnchorCount,
        int rawRangedSpawnAnchorCount,
        int rawFlankSpawnAnchorCount,
        int rawHighgroundSpawnAnchorCount,
        int rawEliteSpawnAnchorCount,
        int rawBossSpawnAnchorCount,
        int rawBarrierAnchorCount,
        int rawMobResetAnchorCount,
        int rawExitAnchorCount,
        int rawBoundsAnchorCount,
        int rawObjectiveAnchorCount,
        int rawInteractAnchorCount,
        boolean markerCacheReady,
        boolean markerCacheDirty,
        String markerCacheDirtyReason,
        long markerScanCount,
        String lastMarkerScanReason,
        long lastMarkerScanGameTime,
        double lastMarkerScanDurationMs,
        String lastMarkerScanPerformanceWarning,
        String markerOwnershipMode,
        boolean claimedMarkerMode,
        int lastMarkerClaimRadius,
        int lastMarkerClaimTotal,
        int lastMarkerClaimSkippedOwnedByOther,
        int lastMarkerClaimStaleRemoved,
        String lastMarkerClaimSummary,
        String lastMarkerClaimReason,
        double lastMarkerClaimDurationMs,
        int boundsPreviewBlockCount,
        Map<Direction, Integer> barrierFacingCounts
    ) {
        public Markers {
            spawnAnchors = List.copyOf(spawnAnchors);
            rangedSpawnAnchors = List.copyOf(rangedSpawnAnchors);
            flankSpawnAnchors = List.copyOf(flankSpawnAnchors);
            highgroundSpawnAnchors = List.copyOf(highgroundSpawnAnchors);
            eliteSpawnAnchors = List.copyOf(eliteSpawnAnchors);
            bossSpawnAnchors = List.copyOf(bossSpawnAnchors);
            barrierAnchors = List.copyOf(barrierAnchors);
            barrierAnchor1Anchors = List.copyOf(barrierAnchor1Anchors);
            barrierAnchor2Anchors = List.copyOf(barrierAnchor2Anchors);
            barrierAnchor3Anchors = List.copyOf(barrierAnchor3Anchors);
            barrierAnchor4Anchors = List.copyOf(barrierAnchor4Anchors);
            mobResetAnchors = List.copyOf(mobResetAnchors);
            exitAnchors = List.copyOf(exitAnchors);
            boundsAnchors = List.copyOf(boundsAnchors);
            objectiveAnchors = List.copyOf(objectiveAnchors);
            interactAnchors = List.copyOf(interactAnchors);
            barrierFacingCounts = Map.copyOf(barrierFacingCounts);
        }

        public boolean hasBounds() {
            return roomBounds != null;
        }

        public boolean hasAnyBarrierAnchor() {
            return !barrierAnchors.isEmpty()
                || !barrierAnchor1Anchors.isEmpty()
                || !barrierAnchor2Anchors.isEmpty()
                || !barrierAnchor3Anchors.isEmpty()
                || !barrierAnchor4Anchors.isEmpty();
        }

        public int ownedCombatAnchorCount() {
            return spawnAnchors.size() + rangedSpawnAnchors.size() + flankSpawnAnchors.size() + highgroundSpawnAnchors.size();
        }

        public boolean hasSpawnBossAnchor() {
            return !bossSpawnAnchors.isEmpty();
        }
    }
}
