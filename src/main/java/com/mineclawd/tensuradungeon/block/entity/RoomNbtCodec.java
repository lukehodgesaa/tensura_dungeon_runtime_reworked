package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.compat.VisualBarrierBackend;
import com.mineclawd.tensuradungeon.compat.VisualBarrierHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

final class RoomNbtCodec {
    private RoomNbtCodec() {
    }

    static void saveLifecycleState(CompoundTag tag, RoomLifecycle lifecycle) {
        RoomLifecycle.Snapshot state = lifecycle.snapshot();
        tag.putString("RoomState", state.state().name());
        tag.putBoolean("Completed", state.completed());
        tag.putString("LastActivationFailureReason", state.lastActivationFailureReason());
        tag.putString("LastFailureReason", state.lastFailureReason());
        tag.putString("LastCompletionReason", state.lastCompletionReason());
        tag.putString("RoomProfile", state.profile().name());
        tag.putInt("ReactivationCooldownTicks", state.reactivationCooldownTicks());
        tag.putInt("HubGateResealTicks", state.hubGateResealTicks());
        tag.putLong("LastMaintenanceGameTime", state.lastMaintenanceGameTime());
        tag.putLong("CompletedProtectionUntilGameTime", state.completedProtectionUntilGameTime());
        tag.putBoolean("BranchPreviewMode", state.branchPreviewMode());
    }

    static void loadLifecycleState(CompoundTag tag, RoomLifecycle lifecycle) {
        lifecycle.loadPersistentState(
            RoomControllerBlockEntity.RoomState.byName(tag.getString("RoomState")),
            tag.getBoolean("Completed"),
            tag.getString("LastActivationFailureReason"),
            tag.contains("LastFailureReason") ? tag.getString("LastFailureReason") : "NONE",
            tag.contains("LastCompletionReason") ? tag.getString("LastCompletionReason") : "NONE",
            RoomProfile.byName(tag.getString("RoomProfile")),
            tag.getInt("ReactivationCooldownTicks"),
            tag.contains("HubGateResealTicks") ? tag.getInt("HubGateResealTicks") : 0,
            tag.getLong("LastMaintenanceGameTime"),
            tag.contains("CompletedProtectionUntilGameTime") ? tag.getLong("CompletedProtectionUntilGameTime") : 0L,
            tag.contains("BranchPreviewMode") && tag.getBoolean("BranchPreviewMode")
        );
    }

    static void saveEncounterRemovalState(CompoundTag tag, RoomEncounter encounter) {
        tag.putBoolean("HadAnyMobConfirmedRemoved", encounter.removalState().hadAnyMobConfirmedRemoved());
    }

    static void loadEncounterRemovalState(CompoundTag tag, RoomEncounter encounter) {
        encounter.importRemovalState(new RoomEncounter.RemovalState(tag.getBoolean("HadAnyMobConfirmedRemoved")));
    }

    static void saveEncounterTelemetry(CompoundTag tag, RoomEncounter encounter) {
        RoomEncounter.TelemetryState state = encounter.telemetryState();
        tag.putBoolean("EliteDefeated", state.eliteDefeated());
        tag.putInt("LastSpawnRequestedCount", state.lastSpawnRequestedCount());
        tag.putInt("LastSpawnActualCount", state.lastSpawnActualCount());
        tag.putString("LastSpawnSkippedReason", state.lastSpawnSkippedReason());
        tag.putString("LastSpawnStrategy", state.lastSpawnStrategy());
        tag.putInt("LastSpawnMeleeUsedCount", state.lastSpawnMeleeUsedCount());
        tag.putInt("LastSpawnRangedUsedCount", state.lastSpawnRangedUsedCount());
        tag.putInt("LastSpawnFlankUsedCount", state.lastSpawnFlankUsedCount());
        tag.putInt("LastSpawnHighgroundUsedCount", state.lastSpawnHighgroundUsedCount());
        tag.putInt("LastSpawnGenericUsedCount", state.lastSpawnGenericUsedCount());
        tag.putString("LastSpawnModifierSummary", state.lastSpawnModifierSummary());
        tag.putDouble("LastSpawnArmorValue", state.lastSpawnArmorValue());
        tag.putDouble("LastSpawnArmorToughnessValue", state.lastSpawnArmorToughnessValue());
        tag.putInt("EncounterTargetCount", state.encounterTargetCount());
        tag.putInt("EncounterWaveSize", state.waveSize());
        tag.putInt("EncounterWavesSpawned", state.wavesSpawned());
        tag.putInt("EncounterWaveCooldownTicks", state.waveCooldownTicks());
        tag.putInt("LastCompositionBudget", state.lastCompositionBudget());
        tag.putInt("LastCompositionSpent", state.lastCompositionSpent());
        tag.putString("LastCompositionSummary", state.lastCompositionSummary());
        tag.putString("LastMoltenModifierName", state.lastMoltenModifierName());
        tag.putInt("LastMoltenRetaliationTick", state.lastMoltenRetaliationTick());
        tag.putString("LastMoltenRetaliationTarget", state.lastMoltenRetaliationTarget());
        tag.putInt("TotalMoltenRetaliations", state.totalMoltenRetaliations());
        tag.putInt("MoltenDamageEventsSeen", state.moltenDamageEventsSeen());
        tag.putInt("MoltenDamageEventsWithLivingAttacker", state.moltenDamageEventsWithLivingAttacker());
        tag.putInt("MoltenDamageEventsWithTrackedTarget", state.moltenDamageEventsWithTrackedTarget());
        tag.putString("LastMoltenRejectionReason", state.lastMoltenRejectionReason());
        tag.putInt("SummonerPulseProgressTicks", state.summonerPulseProgressTicks());
        tag.putString("LastSummonerModifierName", state.lastSummonerModifierName());
        tag.putInt("LastSummonPulseTick", state.lastSummonPulseTick());
        tag.putInt("TotalSummonsThisRoom", state.totalSummonsThisRoom());
        tag.putString("LastSummonResult", state.lastSummonResult());
        tag.putString("LastSummonRejectionReason", state.lastSummonRejectionReason());
        tag.putString("LastSummonerEntityType", state.lastSummonerEntityType());
        tag.putString("LastSummonedEntityType", state.lastSummonedEntityType());
        tag.putInt("RegenerativePulseProgressTicks", state.regenerativePulseProgressTicks());
        tag.putString("LastRegenModifierName", state.lastRegenModifierName());
        tag.putInt("LastRegenPulseTick", state.lastRegenPulseTick());
        tag.putInt("LastRegenPulseHealedCount", state.lastRegenPulseHealedCount());
        tag.putDouble("LastRegenPulseTotalHealthRestored", state.lastRegenPulseTotalHealthRestored());
        if (state.eliteMobUuid() != null) {
            tag.putUUID("EliteMobUuid", state.eliteMobUuid());
        }
        if (state.bossMobUuid() != null) {
            tag.putUUID("BossMobUuid", state.bossMobUuid());
        }
        if (state.bossSpawnAttempted()) {
            tag.putBoolean("BossSpawnAttempted", true);
        }
    }

    static void loadEncounterTelemetry(CompoundTag tag, RoomEncounter encounter) {
        int lastSpawnRequestedCount = tag.getInt("LastSpawnRequestedCount");
        int encounterTargetCount = tag.contains("EncounterTargetCount") ? tag.getInt("EncounterTargetCount") : lastSpawnRequestedCount;
        encounter.importTelemetryState(new RoomEncounter.TelemetryState(
            tag.getBoolean("EliteDefeated"),
            lastSpawnRequestedCount,
            tag.getInt("LastSpawnActualCount"),
            tag.contains("LastSpawnSkippedReason") ? tag.getString("LastSpawnSkippedReason") : "NONE",
            tag.contains("LastSpawnStrategy") ? tag.getString("LastSpawnStrategy") : "GENERIC_ONLY",
            tag.getInt("LastSpawnMeleeUsedCount"),
            tag.getInt("LastSpawnRangedUsedCount"),
            tag.getInt("LastSpawnFlankUsedCount"),
            tag.getInt("LastSpawnHighgroundUsedCount"),
            tag.getInt("LastSpawnGenericUsedCount"),
            tag.contains("LastSpawnModifierSummary") ? tag.getString("LastSpawnModifierSummary") : "NONE",
            tag.contains("LastSpawnArmorValue") ? tag.getDouble("LastSpawnArmorValue") : 0.0D,
            tag.contains("LastSpawnArmorToughnessValue") ? tag.getDouble("LastSpawnArmorToughnessValue") : 0.0D,
            encounterTargetCount,
            tag.contains("EncounterWaveSize") ? tag.getInt("EncounterWaveSize") : Math.max(1, (encounterTargetCount + 4) / 5),
            tag.contains("EncounterWavesSpawned") ? tag.getInt("EncounterWavesSpawned") : 0,
            tag.contains("EncounterWaveCooldownTicks") ? tag.getInt("EncounterWaveCooldownTicks") : 0,
            tag.contains("LastCompositionBudget") ? tag.getInt("LastCompositionBudget") : 0,
            tag.contains("LastCompositionSpent") ? tag.getInt("LastCompositionSpent") : 0,
            tag.contains("LastCompositionSummary") ? tag.getString("LastCompositionSummary") : "NONE",
            tag.contains("LastMoltenModifierName") ? tag.getString("LastMoltenModifierName") : "NONE",
            tag.contains("LastMoltenRetaliationTick") ? tag.getInt("LastMoltenRetaliationTick") : -1,
            tag.contains("LastMoltenRetaliationTarget") ? tag.getString("LastMoltenRetaliationTarget") : "NONE",
            tag.contains("TotalMoltenRetaliations") ? tag.getInt("TotalMoltenRetaliations") : 0,
            tag.contains("MoltenDamageEventsSeen") ? tag.getInt("MoltenDamageEventsSeen") : 0,
            tag.contains("MoltenDamageEventsWithLivingAttacker") ? tag.getInt("MoltenDamageEventsWithLivingAttacker") : 0,
            tag.contains("MoltenDamageEventsWithTrackedTarget") ? tag.getInt("MoltenDamageEventsWithTrackedTarget") : 0,
            tag.contains("LastMoltenRejectionReason") ? tag.getString("LastMoltenRejectionReason") : "NONE",
            tag.contains("SummonerPulseProgressTicks") ? tag.getInt("SummonerPulseProgressTicks") : 0,
            tag.contains("LastSummonerModifierName") ? tag.getString("LastSummonerModifierName") : "NONE",
            tag.contains("LastSummonPulseTick") ? tag.getInt("LastSummonPulseTick") : -1,
            tag.contains("TotalSummonsThisRoom") ? tag.getInt("TotalSummonsThisRoom") : 0,
            tag.contains("LastSummonResult") ? tag.getString("LastSummonResult") : "NONE",
            tag.contains("LastSummonRejectionReason") ? tag.getString("LastSummonRejectionReason") : "NONE",
            tag.contains("LastSummonerEntityType") ? tag.getString("LastSummonerEntityType") : "NONE",
            tag.contains("LastSummonedEntityType") ? tag.getString("LastSummonedEntityType") : "NONE",
            tag.contains("RegenerativePulseProgressTicks") ? tag.getInt("RegenerativePulseProgressTicks") : 0,
            tag.contains("LastRegenModifierName") ? tag.getString("LastRegenModifierName") : "NONE",
            tag.contains("LastRegenPulseTick") ? tag.getInt("LastRegenPulseTick") : -1,
            tag.contains("LastRegenPulseHealedCount") ? tag.getInt("LastRegenPulseHealedCount") : 0,
            tag.contains("LastRegenPulseTotalHealthRestored") ? tag.getDouble("LastRegenPulseTotalHealthRestored") : 0.0D,
            tag.hasUUID("EliteMobUuid") ? tag.getUUID("EliteMobUuid") : null,
            tag.hasUUID("BossMobUuid") ? tag.getUUID("BossMobUuid") : null,
            tag.contains("BossSpawnAttempted") && tag.getBoolean("BossSpawnAttempted")
        ));
    }

    static void saveEncounterRoster(CompoundTag tag, RoomEncounter encounter) {
        RoomEncounter.RosterState state = encounter.rosterState();
        tag.put("TrackedMobs", saveUuids(state.trackedMobUuids()));
        tag.put("SummonerMobs", saveUuids(state.summonerMobUuids()));
        tag.put("SummonedMobs", saveUuids(state.summonedMobUuids()));
        tag.put("SummonedMobOwners", saveUuidMap(state.summonedMobOwners()));
    }

    static void loadEncounterRoster(CompoundTag tag, RoomEncounter encounter) {
        encounter.importRosterState(new RoomEncounter.RosterState(
            List.copyOf(loadUuids(tag.getList("TrackedMobs", Tag.TAG_INT_ARRAY))),
            List.copyOf(loadUuids(tag.getList("SummonerMobs", Tag.TAG_INT_ARRAY))),
            List.copyOf(loadUuids(tag.getList("SummonedMobs", Tag.TAG_INT_ARRAY))),
            Map.copyOf(loadUuidMap(tag.getList("SummonedMobOwners", Tag.TAG_COMPOUND)))
        ));
    }

    static void saveEncounterCombatProgression(CompoundTag tag, RoomEncounter encounter) {
        RoomEncounter.CombatProgressionState state = encounter.combatProgressionState();
        ListTag blasts = new ListTag();
        for (RoomEncounter.PendingBlastState blast : state.pendingBlasts()) {
            CompoundTag blastTag = new CompoundTag();
            blastTag.putDouble("X", blast.x());
            blastTag.putDouble("Y", blast.y());
            blastTag.putDouble("Z", blast.z());
            blastTag.putInt("FuseTicks", blast.fuseTicks());
            blasts.add(blastTag);
        }
        tag.put("PendingModifierBlasts", blasts);
        if (state.riftboundCarrierUuid() != null) {
            tag.putUUID("RiftboundCarrier", state.riftboundCarrierUuid());
        }
        tag.putInt("RiftboundPulseTicks", state.riftboundPulseTicks());
        tag.putInt("RiftboundTotalSummons", state.riftboundTotalSummons());
        tag.put("GravityFlightTicks", saveUuidIntMap(state.gravityFlightTicks()));
        tag.put("GravityGraceTicks", saveUuidIntMap(state.gravityGraceTicks()));
        tag.put("GravityHeavyTicks", saveUuidIntMap(state.gravityHeavyTicks()));
        tag.put("GravityHunterByPlayer", saveUuidMap(state.gravityHunterByPlayer()));
        tag.put("GravityChannelTicks", saveUuidIntMap(state.gravityChannelTicks()));
        CompoundTag modifierState = encounter.modifierStateTag();
        if (!modifierState.isEmpty()) tag.put("EncounterModifierState", modifierState);
    }

    static void loadEncounterCombatProgression(CompoundTag tag, RoomEncounter encounter) {
        List<RoomEncounter.PendingBlastState> blasts = new ArrayList<>();
        ListTag blastTags = tag.getList("PendingModifierBlasts", Tag.TAG_COMPOUND);
        for (int index = 0; index < blastTags.size() && index < 4; index++) {
            CompoundTag blastTag = blastTags.getCompound(index);
            blasts.add(new RoomEncounter.PendingBlastState(
                blastTag.getDouble("X"),
                blastTag.getDouble("Y"),
                blastTag.getDouble("Z"),
                Math.max(0, blastTag.getInt("FuseTicks"))
            ));
        }
        encounter.importCombatProgressionState(new RoomEncounter.CombatProgressionState(
            List.copyOf(blasts),
            tag.hasUUID("RiftboundCarrier") ? tag.getUUID("RiftboundCarrier") : null,
            Math.max(0, tag.getInt("RiftboundPulseTicks")),
            Math.max(0, tag.getInt("RiftboundTotalSummons")),
            Map.copyOf(loadUuidIntMap(tag.getList("GravityFlightTicks", Tag.TAG_COMPOUND))),
            Map.copyOf(loadUuidIntMap(tag.getList("GravityGraceTicks", Tag.TAG_COMPOUND))),
            Map.copyOf(loadUuidIntMap(tag.getList("GravityHeavyTicks", Tag.TAG_COMPOUND))),
            Map.copyOf(loadUuidMap(tag.getList("GravityHunterByPlayer", Tag.TAG_COMPOUND))),
            Map.copyOf(loadUuidIntMap(tag.getList("GravityChannelTicks", Tag.TAG_COMPOUND)))
        ));
        if (tag.contains("EncounterModifierState", Tag.TAG_COMPOUND)) {
            encounter.importModifierState(tag.getCompound("EncounterModifierState"));
        }
    }

    static void saveObjectiveState(CompoundTag tag, RoomObjective objective) {
        RoomObjective.PersistentState state = objective.persistentState();
        tag.putString("ConfiguredObjectiveType", state.configuredObjectiveType().name());
        tag.putBoolean("ObjectiveOverrideEnabled", state.objectiveOverrideEnabled());
        tag.putBoolean("ObjectiveDisabled", state.objectiveDisabled());
        tag.putString("ActiveObjectiveType", state.activeObjectiveType().name());
        tag.putBoolean("ObjectiveCompleted", state.objectiveCompleted());
        tag.putString("ObjectiveSelectionMode", state.objectiveSelectionMode());
        tag.putString("AutoObjectiveSelected", state.autoObjectiveSelected());
        tag.putString("AutoObjectiveCandidateList", state.autoObjectiveCandidateList());
        tag.putString("AutoObjectiveRejectionSummary", state.autoObjectiveRejectionSummary());
        tag.putInt("AutoObjectiveRollValue", state.autoObjectiveRollValue());
        tag.putString("LastObjectiveResult", state.lastObjectiveResult());
        tag.putString("LastObjectiveRejectionReason", state.lastObjectiveRejectionReason());
        tag.putString("ObjectiveTargetEntityType", state.objectiveTargetEntityType());
        tag.putInt("ObjectiveDurationTicks", state.objectiveDurationTicks());
        tag.putInt("ObjectiveRemainingTicks", state.objectiveRemainingTicks());
        tag.putBoolean("ObjectivePaused", state.objectivePaused());
        tag.putInt("ObjectivePlayersInBounds", state.objectivePlayersInBounds());
        tag.putInt("RuneRequiredCount", state.runeRequiredCount());
        tag.putInt("RuneActivatedCount", state.runeActivatedCount());
        tag.put("SelectedRuneAnchorPositions", savePositions(state.selectedRuneAnchorPositions()));
        tag.put("RuneCrystalUuids", saveUuids(state.runeCrystalUuids()));
        tag.putBoolean("ObjectiveActive", state.objectiveActive());
        tag.putInt("ObjectiveTimerTicks", state.objectiveTimerTicks());
    }

    static void loadObjectiveState(CompoundTag tag, RoomObjective objective) {
        boolean overrideEnabled = tag.contains("ObjectiveOverrideEnabled") && tag.getBoolean("ObjectiveOverrideEnabled");
        objective.importPersistentState(new RoomObjective.PersistentState(
            RoomObjectiveType.byName(tag.getString("ConfiguredObjectiveType")),
            overrideEnabled,
            tag.contains("ObjectiveDisabled") && tag.getBoolean("ObjectiveDisabled"),
            tag.contains("ActiveObjectiveType")
                ? RoomObjectiveType.byName(tag.getString("ActiveObjectiveType"))
                : RoomObjectiveType.CLEAR_ALL,
            tag.contains("ObjectiveCompleted") && tag.getBoolean("ObjectiveCompleted"),
            tag.contains("ObjectiveSelectionMode") ? tag.getString("ObjectiveSelectionMode") : (overrideEnabled ? "FORCED" : "AUTO"),
            tag.contains("AutoObjectiveSelected") ? tag.getString("AutoObjectiveSelected") : "NONE",
            tag.contains("AutoObjectiveCandidateList") ? tag.getString("AutoObjectiveCandidateList") : "NONE",
            tag.contains("AutoObjectiveRejectionSummary") ? tag.getString("AutoObjectiveRejectionSummary") : "NONE",
            tag.contains("AutoObjectiveRollValue") ? tag.getInt("AutoObjectiveRollValue") : -1,
            tag.contains("LastObjectiveResult") ? tag.getString("LastObjectiveResult") : "NONE",
            tag.contains("LastObjectiveRejectionReason") ? tag.getString("LastObjectiveRejectionReason") : "NONE",
            tag.contains("ObjectiveTargetEntityType") ? tag.getString("ObjectiveTargetEntityType") : "NONE",
            tag.contains("ObjectiveDurationTicks") ? tag.getInt("ObjectiveDurationTicks") : 30 * 20,
            tag.contains("ObjectiveRemainingTicks") ? tag.getInt("ObjectiveRemainingTicks") : 0,
            tag.contains("ObjectivePaused") && tag.getBoolean("ObjectivePaused"),
            tag.contains("ObjectivePlayersInBounds") ? tag.getInt("ObjectivePlayersInBounds") : 0,
            tag.contains("RuneRequiredCount") ? tag.getInt("RuneRequiredCount") : 0,
            tag.contains("RuneActivatedCount") ? tag.getInt("RuneActivatedCount") : 0,
            List.copyOf(loadPositions(tag.getList("SelectedRuneAnchorPositions", Tag.TAG_COMPOUND))),
            List.copyOf(loadUuids(tag.getList("RuneCrystalUuids", Tag.TAG_INT_ARRAY))),
            tag.getBoolean("ObjectiveActive"),
            tag.getInt("ObjectiveTimerTicks")
        ));
    }

    static void savePersistentMarkerState(CompoundTag tag, RoomMarkerState state) {
        RoomMarkerState.PersistentState persistent = state.persistentState();
        tag.put("SpawnAnchors", savePositions(persistent.spawnAnchors()));
        tag.put("RangedSpawnAnchors", savePositions(persistent.rangedSpawnAnchors()));
        tag.put("FlankSpawnAnchors", savePositions(persistent.flankSpawnAnchors()));
        tag.put("HighgroundSpawnAnchors", savePositions(persistent.highgroundSpawnAnchors()));
        tag.put("EliteSpawnAnchors", savePositions(persistent.eliteSpawnAnchors()));
        tag.put("BossSpawnAnchors", savePositions(persistent.bossSpawnAnchors()));
        tag.put("BarrierAnchors", savePositions(persistent.barrierAnchors()));
        tag.put("MobResetAnchors", savePositions(persistent.mobResetAnchors()));
        tag.put("ExitAnchors", savePositions(persistent.exitAnchors()));
        tag.put("BoundsAnchors", savePositions(persistent.boundsAnchors()));
        if (persistent.explicitRoomBounds() != null) {
            tag.putLong("ExplicitBoundsMin", persistent.explicitRoomBounds().min().asLong());
            tag.putLong("ExplicitBoundsMax", persistent.explicitRoomBounds().max().asLong());
        }
        if (persistent.explicitBoundsAnchorA() != null) {
            tag.putLong("ExplicitBoundsAnchorA", persistent.explicitBoundsAnchorA().asLong());
        }
        if (persistent.explicitBoundsAnchorB() != null) {
            tag.putLong("ExplicitBoundsAnchorB", persistent.explicitBoundsAnchorB().asLong());
        }
        tag.put("ObjectiveAnchors", savePositions(persistent.objectiveAnchors()));
        tag.put("InteractAnchors", savePositions(persistent.interactAnchors()));
        tag.putBoolean("ClaimedMarkerMode", persistent.claimedMarkerMode());
        tag.putBoolean("BakedMarkerMode", persistent.bakedMarkerMode());
        tag.putString("MarkerOwnershipMode", persistent.markerOwnershipMode());
        tag.putString("LastMarkerClaimSummary", persistent.lastMarkerClaimSummary());
        tag.putString("LastMarkerClaimReason", persistent.lastMarkerClaimReason());
        tag.putInt("LastMarkerClaimRadius", persistent.lastMarkerClaimRadius());
        tag.putInt("LastMarkerClaimTotal", persistent.lastMarkerClaimTotal());
        tag.putInt("LastMarkerClaimSkippedOwnedByOther", persistent.lastMarkerClaimSkippedOwnedByOther());
        tag.putInt("LastMarkerClaimStaleRemoved", persistent.lastMarkerClaimStaleRemoved());
        tag.putLong("LastMarkerClaimDurationNanos", persistent.lastMarkerClaimDurationNanos());
        CompoundTag claimedTag = new CompoundTag();
        for (RoomClaimController.ClaimedMarkerKind kind : RoomClaimController.ClaimedMarkerKind.values()) {
            claimedTag.put(kind.name(), savePositions(persistent.claimedMarkers().getOrDefault(kind, List.of())));
        }
        tag.put("ClaimedMarkers", claimedTag);
    }

    static void loadPersistentMarkerState(CompoundTag tag, RoomMarkerState state) {
        RoomControllerBlockEntity.RoomBounds explicitRoomBounds = tag.contains("ExplicitBoundsMin", Tag.TAG_LONG)
            && tag.contains("ExplicitBoundsMax", Tag.TAG_LONG)
            ? new RoomControllerBlockEntity.RoomBounds(
                BlockPos.of(tag.getLong("ExplicitBoundsMin")),
                BlockPos.of(tag.getLong("ExplicitBoundsMax"))
            )
            : null;
        BlockPos explicitBoundsAnchorA = tag.contains("ExplicitBoundsAnchorA", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("ExplicitBoundsAnchorA"))
            : null;
        BlockPos explicitBoundsAnchorB = tag.contains("ExplicitBoundsAnchorB", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("ExplicitBoundsAnchorB"))
            : null;
        boolean claimedMarkerMode = tag.contains("ClaimedMarkerMode") && tag.getBoolean("ClaimedMarkerMode");
        String markerOwnershipMode = tag.contains("MarkerOwnershipMode")
            ? tag.getString("MarkerOwnershipMode")
            : (claimedMarkerMode ? "CLAIMED_MARKERS" : "LEGACY_RADIUS_FALLBACK");
        boolean claimedCatalogPresent = tag.contains("ClaimedMarkers", Tag.TAG_COMPOUND);
        Map<RoomClaimController.ClaimedMarkerKind, List<BlockPos>> claimedMarkers = new HashMap<>();
        if (claimedCatalogPresent) {
            CompoundTag claimedTag = tag.getCompound("ClaimedMarkers");
            for (RoomClaimController.ClaimedMarkerKind kind : RoomClaimController.ClaimedMarkerKind.values()) {
                claimedMarkers.put(kind, List.copyOf(loadPositions(claimedTag.getList(kind.name(), Tag.TAG_COMPOUND))));
            }
        }
        state.importPersistentState(new RoomMarkerState.PersistentState(
            List.copyOf(loadPositions(tag.getList("SpawnAnchors", Tag.TAG_COMPOUND))),
            List.copyOf(loadPositions(tag.getList("RangedSpawnAnchors", Tag.TAG_COMPOUND))),
            List.copyOf(loadPositions(tag.getList("FlankSpawnAnchors", Tag.TAG_COMPOUND))),
            List.copyOf(loadPositions(tag.getList("HighgroundSpawnAnchors", Tag.TAG_COMPOUND))),
            List.copyOf(loadPositions(tag.getList("EliteSpawnAnchors", Tag.TAG_COMPOUND))),
            List.copyOf(loadPositions(tag.getList("BossSpawnAnchors", Tag.TAG_COMPOUND))),
            List.copyOf(loadPositions(tag.getList("BarrierAnchors", Tag.TAG_COMPOUND))),
            List.copyOf(loadPositions(tag.getList("MobResetAnchors", Tag.TAG_COMPOUND))),
            List.copyOf(loadPositions(tag.getList("ExitAnchors", Tag.TAG_COMPOUND))),
            List.copyOf(loadPositions(tag.getList("BoundsAnchors", Tag.TAG_COMPOUND))),
            explicitRoomBounds,
            explicitBoundsAnchorA,
            explicitBoundsAnchorB,
            List.copyOf(loadPositions(tag.getList("ObjectiveAnchors", Tag.TAG_COMPOUND))),
            List.copyOf(loadPositions(tag.getList("InteractAnchors", Tag.TAG_COMPOUND))),
            claimedMarkerMode,
            tag.contains("BakedMarkerMode") && tag.getBoolean("BakedMarkerMode"),
            markerOwnershipMode,
            tag.contains("LastMarkerClaimSummary") ? tag.getString("LastMarkerClaimSummary") : "NONE",
            tag.contains("LastMarkerClaimReason") ? tag.getString("LastMarkerClaimReason") : "NONE",
            tag.contains("LastMarkerClaimRadius") ? tag.getInt("LastMarkerClaimRadius") : 0,
            tag.contains("LastMarkerClaimTotal") ? tag.getInt("LastMarkerClaimTotal") : 0,
            tag.contains("LastMarkerClaimSkippedOwnedByOther") ? tag.getInt("LastMarkerClaimSkippedOwnedByOther") : 0,
            tag.contains("LastMarkerClaimStaleRemoved") ? tag.getInt("LastMarkerClaimStaleRemoved") : 0,
            tag.contains("LastMarkerClaimDurationNanos") ? tag.getLong("LastMarkerClaimDurationNanos") : -1L,
            Map.copyOf(claimedMarkers),
            claimedCatalogPresent,
            state.persistentState().boundsPreviewPositions()
        ));
    }

    static void saveBoundsPreview(CompoundTag tag, RoomMarkerState state) {
        tag.put("BoundsPreviewBlocks", savePositions(state.persistentState().boundsPreviewPositions()));
    }

    static void loadBoundsPreviewAndResetTransientCache(CompoundTag tag, RoomMarkerState state) {
        state.importBoundsPreviewOnLoad(loadPositions(tag.getList("BoundsPreviewBlocks", Tag.TAG_COMPOUND)));
    }

    static void saveBarrierState(CompoundTag tag, RoomBarrierController.PersistentState state) {
        tag.put("PlacedBarriers", savePositions(state.placedBarrierPositions()));
        ListTag visualBarriers = new ListTag();
        for (VisualBarrierHandle handle : state.placedVisualBarrierHandles()) {
            CompoundTag handleTag = new CompoundTag();
            handleTag.putString("Backend", handle.persistedBackendName());
            handleTag.putInt("Id", handle.id());
            visualBarriers.add(handleTag);
        }
        tag.put("VisualBarriers", visualBarriers);
        tag.put("TemporarySeals", savePositions(state.temporarySealPositions()));
        ListTag temporarySealGroups = new ListTag();
        for (Map.Entry<BlockPos, List<BlockPos>> entry : state.temporarySealGroups().entrySet()) {
            CompoundTag groupTag = new CompoundTag();
            groupTag.put("Connector", NbtUtils.writeBlockPos(entry.getKey()));
            groupTag.put("Positions", savePositions(entry.getValue()));
            temporarySealGroups.add(groupTag);
        }
        tag.put("TemporarySealGroups", temporarySealGroups);
        ListTag temporaryVisualSeals = new ListTag();
        for (Map.Entry<BlockPos, VisualBarrierHandle> entry : state.temporaryVisualSealHandles().entrySet()) {
            CompoundTag handleTag = new CompoundTag();
            handleTag.put("Connector", NbtUtils.writeBlockPos(entry.getKey()));
            handleTag.putString("Backend", entry.getValue().persistedBackendName());
            handleTag.putInt("Id", entry.getValue().id());
            temporaryVisualSeals.add(handleTag);
        }
        tag.put("TemporaryVisualSeals", temporaryVisualSeals);
        tag.put("BarrierPreviewBlocks", savePositions(state.barrierPreviewPositions()));
    }

    static RoomBarrierController.PersistentState loadBarrierState(CompoundTag tag) {
        List<BlockPos> placedBarriers = loadPositions(tag.getList("PlacedBarriers", Tag.TAG_COMPOUND));
        List<VisualBarrierHandle> visualBarriers = new ArrayList<>();
        if (tag.contains("VisualBarriers", Tag.TAG_LIST)) {
            ListTag visualBarrierTags = tag.getList("VisualBarriers", Tag.TAG_COMPOUND);
            for (int index = 0; index < visualBarrierTags.size(); index++) {
                CompoundTag handleTag = visualBarrierTags.getCompound(index);
                visualBarriers.add(loadPersistedVisualHandle(handleTag));
            }
        } else {
            for (int barrierId : tag.getIntArray("VisualBarrierIds")) {
                if (barrierId >= 0) {
                    visualBarriers.add(new VisualBarrierHandle(VisualBarrierBackend.TENSURA, barrierId));
                }
            }
        }

        List<BlockPos> temporarySeals = loadPositions(tag.getList("TemporarySeals", Tag.TAG_COMPOUND));
        Map<BlockPos, List<BlockPos>> temporarySealGroups = new LinkedHashMap<>();
        ListTag groupTags = tag.getList("TemporarySealGroups", Tag.TAG_COMPOUND);
        for (int index = 0; index < groupTags.size(); index++) {
            CompoundTag groupTag = groupTags.getCompound(index);
            NbtUtils.readBlockPos(groupTag, "Connector").ifPresent(connector ->
                temporarySealGroups.put(
                    connector.immutable(),
                    loadPositions(groupTag.getList("Positions", Tag.TAG_COMPOUND))
                )
            );
        }

        Map<BlockPos, VisualBarrierHandle> temporaryVisualSeals = new LinkedHashMap<>();
        ListTag temporaryVisualTags = tag.getList("TemporaryVisualSeals", Tag.TAG_COMPOUND);
        for (int index = 0; index < temporaryVisualTags.size(); index++) {
            CompoundTag handleTag = temporaryVisualTags.getCompound(index);
            NbtUtils.readBlockPos(handleTag, "Connector").ifPresent(connector ->
                temporaryVisualSeals.put(connector.immutable(), loadPersistedVisualHandle(handleTag))
            );
        }

        return new RoomBarrierController.PersistentState(
            placedBarriers,
            List.copyOf(visualBarriers),
            temporarySeals,
            temporarySealGroups,
            temporaryVisualSeals,
            loadPositions(tag.getList("BarrierPreviewBlocks", Tag.TAG_COMPOUND))
        );
    }

    private static VisualBarrierHandle loadPersistedVisualHandle(CompoundTag handleTag) {
        String backendName = handleTag.contains("Backend", Tag.TAG_STRING) ? handleTag.getString("Backend") : "";
        int id = handleTag.contains("Id", Tag.TAG_INT) ? handleTag.getInt("Id") : -1;
        return VisualBarrierHandle.parsePersisted(backendName, id);
    }

    private static void replacePositions(List<BlockPos> target, ListTag source) {
        target.clear();
        target.addAll(loadPositions(source));
    }

    private static ListTag savePositions(Iterable<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            CompoundTag posTag = new CompoundTag();
            posTag.put("Pos", NbtUtils.writeBlockPos(pos));
            list.add(posTag);
        }
        return list;
    }

    private static List<BlockPos> loadPositions(ListTag list) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtUtils.readBlockPos(list.getCompound(i), "Pos").ifPresent(positions::add);
        }
        return positions;
    }

    private static ListTag saveUuids(Iterable<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            list.add(NbtUtils.createUUID(uuid));
        }
        return list;
    }

    private static Set<UUID> loadUuids(ListTag list) {
        Set<UUID> uuids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            uuids.add(NbtUtils.loadUUID(list.get(i)));
        }
        return uuids;
    }

    private static ListTag saveUuidMap(Map<UUID, UUID> map) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, UUID> entry : map.entrySet()) {
            CompoundTag pairTag = new CompoundTag();
            pairTag.putUUID("Mob", entry.getKey());
            pairTag.putUUID("Owner", entry.getValue());
            list.add(pairTag);
        }
        return list;
    }

    private static Map<UUID, UUID> loadUuidMap(ListTag list) {
        Map<UUID, UUID> map = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag pairTag = list.getCompound(i);
            if (pairTag.hasUUID("Mob") && pairTag.hasUUID("Owner")) {
                map.put(pairTag.getUUID("Mob"), pairTag.getUUID("Owner"));
            }
        }
        return map;
    }

    private static ListTag saveUuidIntMap(Map<UUID, Integer> map) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Integer> entry : map.entrySet()) {
            CompoundTag valueTag = new CompoundTag();
            valueTag.putUUID("Id", entry.getKey());
            valueTag.putInt("Value", entry.getValue());
            list.add(valueTag);
        }
        return list;
    }

    private static Map<UUID, Integer> loadUuidIntMap(ListTag list) {
        Map<UUID, Integer> map = new HashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag valueTag = list.getCompound(index);
            if (valueTag.hasUUID("Id")) {
                map.put(valueTag.getUUID("Id"), Math.max(0, valueTag.getInt("Value")));
            }
        }
        return map;
    }
}
