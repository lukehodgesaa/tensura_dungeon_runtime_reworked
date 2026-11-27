package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.block.BarrierAnchorBlock;
import com.mineclawd.tensuradungeon.block.entity.RoomBarrierController.BarrierPlaneSolveResult;
import com.mineclawd.tensuradungeon.block.entity.RoomBarrierController.BarrierSolvePlan;
import com.mineclawd.tensuradungeon.event.ActiveRoomProtectionEvents;
import com.mineclawd.tensuradungeon.registry.ModBlockEntities;
import com.mineclawd.tensuradungeon.runtime.DungeonBudgetEngine;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceRecoveryService;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierDefinition;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierCatalog;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.DungeonRunService;
import com.mineclawd.tensuradungeon.runtime.DungeonSoundHooks;
import com.mineclawd.tensuradungeon.runtime.DungeonSubordinateSkillCombatController;
import com.mineclawd.tensuradungeon.runtime.RoomBudgetResult;
import com.mineclawd.tensuradungeon.runtime.SubordinateDebriefController;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public class RoomControllerBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCAN_RADIUS = 24;
    private static final int ACTIVATION_BOUNDS_INSET = 3;
    private static final int ACTIVATION_BARRIER_WARNING_DISTANCE = 1;
    private static final int TICK_INTERVAL = 20;
    private static final int ACTIVE_ROOM_SAFETY_RADIUS = 64;
    private static final long MARKER_SCAN_WARN_THRESHOLD_NANOS = 10_000_000L;

    private static final BlockState BOUNDS_PREVIEW_BLOCK_STATE = Blocks.LIME_STAINED_GLASS.defaultBlockState();

    @Nullable
    private DungeonInstanceAllocationData.InstanceKey dungeonInstanceKey;

    private final RoomLifecycle lifecycle = new RoomLifecycle();
    final RoomMarkerState markerState = new RoomMarkerState();

    private final PlayerSessionAssociation playerSessionAssociation = new PlayerSessionAssociation();
    private final RoomObjective objective = new RoomObjective(playerSessionAssociation);
    private final RoomClaimController claimController = new RoomClaimController(markerState);
    final RoomEncounter encounter;
    private final RoomBarrierController barrierController;

    public RoomControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROOM_CONTROLLER.get(), pos, state);
        barrierController = new RoomBarrierController(this::setChanged);
        encounter = new RoomEncounter(
            pos,
            markerState,
            lifecycle,
            objective,
            barrierController::isRuntimeBarrierBlockState,
            () -> dungeonInstanceKey
        );
    }

    private RoomRunGateway roomRunGateway(ServerLevel level) {
        return RoomRunGateway.production(level, worldPosition, dungeonInstanceKey);
    }

    private GeneratedRoomHooks generatedRoomHooks(ServerLevel level) {
        return GeneratedRoomHooks.production(level, worldPosition, dungeonInstanceKey);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RoomControllerBlockEntity controller) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        GeneratedRoomHooks generatedHooks = controller.generatedRoomHooks(serverLevel);
        if (controller.isInstanceSuspended(serverLevel)) {
            DungeonSubordinateSkillCombatController.cancelForInstance(
                serverLevel,
                controller.getDungeonInstanceKey(),
                "INSTANCE_SUSPENDED"
            );
            controller.ensureMarkerCache(serverLevel, "SUSPENDED_INSTANCE");
            ActiveRoomProtectionEvents.syncProtectionRegistration(serverLevel, controller);
            return;
        }
        controller.lifecycle.tick(new RoomLifecycle.TickActions(
            () -> controller.normalizeAfterReload(serverLevel),
            () -> controller.barrierController.ensureRuneAppearance(serverLevel, controller.barrierInputs()),
            generatedHooks::closeHubGateAfterFirstRoomEntry,
            controller::setChanged,
            () -> controller.ensureMarkerCache(serverLevel, "SERVER_TICK_WARMUP"),
            () -> controller.syncRunActivationState(serverLevel),
            () -> ActiveRoomProtectionEvents.syncProtectionRegistration(serverLevel, controller),
            () -> controller.performResettingTick(serverLevel),
            new RoomLifecycle.ActiveTickActions(
                () -> controller.handleAssociatedActivePlayers(serverLevel),
                () -> controller.updateObjective(serverLevel),
                controller::updateMobResetCooldowns,
                () -> controller.applyEncounterEffect(serverLevel, controller.encounter.tickEncounterWaves(serverLevel)),
                () -> {
                    controller.updateTrackedMobs(serverLevel);
                    DungeonSubordinateSkillCombatController.tick(serverLevel, controller);
                },
                TICK_INTERVAL,
                () -> controller.applyEncounterEffect(serverLevel, controller.encounter.refreshAggroTargets(serverLevel)),
                () -> controller.applyEncounterEffect(serverLevel, controller.encounter.updateRegenerativePulse(serverLevel)),
                () -> controller.applyEncounterEffect(serverLevel, controller.encounter.updateSummonerPulse(serverLevel))
            ),
            new RoomLifecycle.InactiveTickActions(
                () -> controller.hasPlayerInActivationZone(serverLevel),
                generatedHooks::isFrontierCandidate,
                () -> !"NONE".equals(controller.getRunActivationBlockReason(serverLevel)),
                () -> controller.notifyRunActivationBlocked(serverLevel),
                () -> controller.isActivationBlocked(serverLevel),
                () -> controller.activate(serverLevel)
            )
        ));
        SubordinateDebriefController.tick(serverLevel, controller);
    }

    private void performResettingTick(ServerLevel level) {
        DungeonSubordinateSkillCombatController.cancelForInstance(
            level,
            dungeonInstanceKey,
            "ROOM_RESET"
        );
        lifecycle.performResettingTick(new RoomLifecycle.ResettingTickActions(
            () -> clearAllPreviews(level),
            () -> openBarrier(level),
            encounter::clearTrackingCollections,
            playerSessionAssociation::clearTransientState,
            () -> {
                encounter.resetRegenerativeState();
                encounter.resetMoltenState();
                encounter.resetSummonerState();
            },
            () -> ActiveRoomProtectionEvents.syncProtectionRegistration(level, this),
            encounter::resetRemovalEvidence,
            this::setChanged
        ));
    }

    public MarkerClaimResult claimMarkers(ServerLevel level, int requestedRadius, boolean force) {
        MarkerClaimResult result = claimController.claimMarkers(level, worldPosition, requestedRadius, force);
        markMarkerCacheDirty("CLAIM_MARKERS");
        ensureMarkerCache(level, "CLAIM_MARKERS_REFRESH");
        setChanged();
        return result;
    }

    MarkerClaimResult claimGeneratedMarkersWithinBounds(
        ServerLevel level,
        RoomBounds resolvedBounds,
        int requestedRadius,
        boolean force
    ) {
        MarkerClaimResult result = claimController.claimGeneratedMarkersWithinBounds(
            level,
            worldPosition,
            resolvedBounds,
            requestedRadius,
            force
        );
        markMarkerCacheDirty("CLAIM_MARKERS");
        ensureMarkerCache(level, "CLAIM_MARKERS_REFRESH");
        setChanged();
        return result;
    }

    GeneratedMarkerManifestResult claimGeneratedMarkersFromManifest(
        ServerLevel level,
        RoomBounds resolvedBounds,
        Map<String, List<BlockPos>> markerPositions,
        int resolvedClaimRadius
    ) {
        long startedAt = System.nanoTime();
        if (level == null || resolvedBounds == null || markerPositions == null) {
            return GeneratedMarkerManifestResult.failed("INVALID_GENERATED_MARKER_MANIFEST");
        }
        Map<RoomClaimController.ClaimedMarkerKind, List<BlockPos>> resolved =
            new EnumMap<>(RoomClaimController.ClaimedMarkerKind.class);
        Set<BlockPos> seen = new HashSet<>();
        int rawEntries = 0;
        int totalClaimed = 0;
        for (Map.Entry<String, List<BlockPos>> entry : markerPositions.entrySet()) {
            RoomClaimController.ClaimedMarkerKind expected =
                RoomMarkerState.markerKindForName(entry.getKey());
            for (BlockPos suppliedPos : entry.getValue()) {
                rawEntries++;
                if (suppliedPos == null) {
                    return GeneratedMarkerManifestResult.failed(
                        "GENERATED_MARKER_MANIFEST_NULL_POSITION_" + entry.getKey()
                    );
                }
                BlockPos markerPos = suppliedPos.immutable();
                if (!resolvedBounds.contains(markerPos)) {
                    return GeneratedMarkerManifestResult.failed(
                        "GENERATED_MARKER_OUTSIDE_BOUNDS_" + entry.getKey() + "_" + markerPos.toShortString()
                    );
                }
                if (!seen.add(markerPos)) {
                    return GeneratedMarkerManifestResult.failed(
                        "GENERATED_MARKER_DUPLICATE_POSITION_" + markerPos.toShortString()
                    );
                }
                if (expected == null) {
                    continue;
                }
                RoomClaimController.ClaimedMarkerKind actual =
                    RoomMarkerState.markerKindFor(level.getBlockState(markerPos));
                if (actual != expected) {
                    return GeneratedMarkerManifestResult.failed(
                        "GENERATED_MARKER_STATE_MISMATCH_" + entry.getKey() + "_" + markerPos.toShortString()
                    );
                }
                resolved.computeIfAbsent(expected, ignored -> new ArrayList<>()).add(markerPos);
                totalClaimed++;
            }
        }

        markerState.clearClaimedMarkers();
        resolved.forEach((kind, positions) -> markerState.claimedMarkers(kind).addAll(positions));
        long durationNanos = System.nanoTime() - startedAt;
        markerState.recordClaimResult(
            true,
            "CLAIMED_MARKERS",
            resolvedClaimRadius,
            totalClaimed,
            0,
            0,
            durationNanos,
            "GENERATED_MARKER_MANIFEST"
        );
        markMarkerCacheDirty("GENERATED_MARKER_MANIFEST");
        ensureMarkerCache(level, "GENERATED_MARKER_MANIFEST");
        setChanged();
        MarkerClaimResult result = new MarkerClaimResult(
            resolvedClaimRadius,
            totalClaimed,
            0,
            0,
            rawEntries,
            true,
            0,
            durationNanos,
            markerState.lastMarkerClaimSummary,
            "GENERATED_MARKER_MANIFEST"
        );
        return GeneratedMarkerManifestResult.success(result);
    }

    public ClaimPreviewResult previewClaimMarkers(ServerLevel level, int requestedRadius, boolean force) {
        clearClaimPreview(level);
        return claimController.previewClaimMarkers(level, worldPosition, requestedRadius, force);
    }

    public ClaimBoundsResolution resolveClaimBoundsForController(ServerLevel level, int radius) {
        return claimController.resolveClaimBoundsForController(level, worldPosition, radius);
    }

    public void scanMarkers(ServerLevel level) {
        scanMarkersInternal(level, "FORCED_SCAN");
    }

    private void ensureMarkerCache(ServerLevel level, String reason) {
        if (markerState.markerCacheReady && !markerState.markerCacheDirty) {
            return;
        }
        scanMarkersInternal(level, reason == null || reason.isBlank() ? "CACHE_WARMUP" : reason);
    }

    private void markMarkerCacheDirty(String reason) {
        markerState.markerCacheReady = false;
        markerState.markerCacheDirty = true;
        markerState.markerCacheDirtyReason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
    }
    private void scanMarkersInternal(ServerLevel level, String reason) {
        long startedAt = System.nanoTime();
        if (markerState.claimedMarkerMode) {
            scanClaimedMarkersInternal(level, reason, startedAt);
            return;
        }
        markerState.spawnAnchors.clear();
        markerState.rangedSpawnAnchors.clear();
        markerState.flankSpawnAnchors.clear();
        markerState.highgroundSpawnAnchors.clear();
        markerState.eliteSpawnAnchors.clear();
        markerState.bossSpawnAnchors.clear();
        markerState.barrierAnchors.clear();
        markerState.barrierAnchor1Anchors.clear();
        markerState.barrierAnchor2Anchors.clear();
        markerState.barrierAnchor3Anchors.clear();
        markerState.barrierAnchor4Anchors.clear();
        markerState.mobResetAnchors.clear();
        markerState.exitAnchors.clear();
        markerState.boundsAnchors.clear();
        markerState.objectiveAnchors.clear();
        markerState.interactAnchors.clear();
        List<BlockPos> nearbySpawnAnchors = new ArrayList<>();
        List<BlockPos> nearbyRangedSpawnAnchors = new ArrayList<>();
        List<BlockPos> nearbyFlankSpawnAnchors = new ArrayList<>();
        List<BlockPos> nearbyHighgroundSpawnAnchors = new ArrayList<>();
        List<BlockPos> nearbyEliteSpawnAnchors = new ArrayList<>();
        List<BlockPos> nearbyBossSpawnAnchors = new ArrayList<>();
        List<BlockPos> nearbyBarrierAnchors = new ArrayList<>();
        List<BlockPos> nearbyBarrierAnchor1Anchors = new ArrayList<>();
        List<BlockPos> nearbyBarrierAnchor2Anchors = new ArrayList<>();
        List<BlockPos> nearbyBarrierAnchor3Anchors = new ArrayList<>();
        List<BlockPos> nearbyBarrierAnchor4Anchors = new ArrayList<>();
        List<BlockPos> nearbyMobResetAnchors = new ArrayList<>();
        List<BlockPos> nearbyExitAnchors = new ArrayList<>();
        List<BlockPos> nearbyObjectiveAnchors = new ArrayList<>();
        List<BlockPos> nearbyInteractAnchors = new ArrayList<>();
        markerState.rawSpawnAnchorPositions.clear();
        markerState.rawRangedSpawnAnchorPositions.clear();
        markerState.rawFlankSpawnAnchorPositions.clear();
        markerState.rawHighgroundSpawnAnchorPositions.clear();
        markerState.rawEliteSpawnAnchorPositions.clear();
        markerState.rawBossSpawnAnchorPositions.clear();
        markerState.rawBarrierAnchorPositions.clear();
        markerState.rawBarrierAnchor1Positions.clear();
        markerState.rawBarrierAnchor2Positions.clear();
        markerState.rawBarrierAnchor3Positions.clear();
        markerState.rawBarrierAnchor4Positions.clear();
        BlockPos min = worldPosition.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS);
        BlockPos max = worldPosition.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);
        for (BlockPos foundPos : BlockPos.betweenClosed(min, max)) {
            BlockState foundState = level.getBlockState(foundPos);
            if (foundState.is(ModBlocks.BOUNDS_ANCHOR.get())) {
                markerState.boundsAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.SPAWN_MELEE.get())) {
                nearbySpawnAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.SPAWN_RANGED.get())) {
                nearbyRangedSpawnAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.SPAWN_FLANK.get())) {
                nearbyFlankSpawnAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.SPAWN_HIGHGROUND.get())) {
                nearbyHighgroundSpawnAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.SPAWN_ELITE.get())) {
                nearbyEliteSpawnAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.SPAWN_BOSS.get())) {
                nearbyBossSpawnAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.BARRIER_ANCHOR.get())) {
                nearbyBarrierAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.BARRIER_ANCHOR_1.get())) {
                nearbyBarrierAnchor1Anchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.BARRIER_ANCHOR_2.get())) {
                nearbyBarrierAnchor2Anchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.BARRIER_ANCHOR_3.get())) {
                nearbyBarrierAnchor3Anchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.BARRIER_ANCHOR_4.get())) {
                nearbyBarrierAnchor4Anchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.MOB_RESET_ANCHOR.get())) {
                nearbyMobResetAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.EXIT_ANCHOR.get()) || foundState.is(ModBlocks.FLOOR_CONNECTOR_ANCHOR.get()) || foundState.is(ModBlocks.CONNECTOR_EXIT.get())) {
                nearbyExitAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.OBJECTIVE_ANCHOR.get())) {
                nearbyObjectiveAnchors.add(foundPos.immutable());
            } else if (foundState.is(ModBlocks.INTERACT_ANCHOR.get())) {
                nearbyInteractAnchors.add(foundPos.immutable());
            }
        }
        markerState.rawSpawnAnchorCount = nearbySpawnAnchors.size();
        markerState.rawRangedSpawnAnchorCount = nearbyRangedSpawnAnchors.size();
        markerState.rawFlankSpawnAnchorCount = nearbyFlankSpawnAnchors.size();
        markerState.rawHighgroundSpawnAnchorCount = nearbyHighgroundSpawnAnchors.size();
        markerState.rawEliteSpawnAnchorCount = nearbyEliteSpawnAnchors.size();
        markerState.rawBossSpawnAnchorCount = nearbyBossSpawnAnchors.size();
        markerState.rawBarrierAnchorCount = nearbyBarrierAnchors.size();
        markerState.rawBarrierAnchor1Count = nearbyBarrierAnchor1Anchors.size();
        markerState.rawBarrierAnchor2Count = nearbyBarrierAnchor2Anchors.size();
        markerState.rawBarrierAnchor3Count = nearbyBarrierAnchor3Anchors.size();
        markerState.rawBarrierAnchor4Count = nearbyBarrierAnchor4Anchors.size();
        markerState.rawMobResetAnchorCount = nearbyMobResetAnchors.size();
        markerState.rawExitAnchorCount = nearbyExitAnchors.size();
        markerState.rawBoundsAnchorCount = markerState.boundsAnchors.size();
        markerState.rawObjectiveAnchorCount = nearbyObjectiveAnchors.size();
        markerState.rawInteractAnchorCount = nearbyInteractAnchors.size();
        markerState.rawSpawnAnchorPositions.addAll(nearbySpawnAnchors);
        markerState.rawRangedSpawnAnchorPositions.addAll(nearbyRangedSpawnAnchors);
        markerState.rawFlankSpawnAnchorPositions.addAll(nearbyFlankSpawnAnchors);
        markerState.rawHighgroundSpawnAnchorPositions.addAll(nearbyHighgroundSpawnAnchors);
        markerState.rawEliteSpawnAnchorPositions.addAll(nearbyEliteSpawnAnchors);
        markerState.rawBossSpawnAnchorPositions.addAll(nearbyBossSpawnAnchors);
        markerState.rawBarrierAnchorPositions.addAll(nearbyBarrierAnchors);
        markerState.rawBarrierAnchor1Positions.addAll(nearbyBarrierAnchor1Anchors);
        markerState.rawBarrierAnchor2Positions.addAll(nearbyBarrierAnchor2Anchors);
        markerState.rawBarrierAnchor3Positions.addAll(nearbyBarrierAnchor3Anchors);
        markerState.rawBarrierAnchor4Positions.addAll(nearbyBarrierAnchor4Anchors);
        RoomBounds bounds = roomBounds();
        if (bounds == null) {
            markerState.spawnAnchors.addAll(nearbySpawnAnchors);
            markerState.rangedSpawnAnchors.addAll(nearbyRangedSpawnAnchors);
            markerState.flankSpawnAnchors.addAll(nearbyFlankSpawnAnchors);
            markerState.highgroundSpawnAnchors.addAll(nearbyHighgroundSpawnAnchors);
            markerState.eliteSpawnAnchors.addAll(nearbyEliteSpawnAnchors);
            markerState.bossSpawnAnchors.addAll(nearbyBossSpawnAnchors);
            markerState.barrierAnchors.addAll(nearbyBarrierAnchors);
            markerState.barrierAnchor1Anchors.addAll(nearbyBarrierAnchor1Anchors);
            markerState.barrierAnchor2Anchors.addAll(nearbyBarrierAnchor2Anchors);
            markerState.barrierAnchor3Anchors.addAll(nearbyBarrierAnchor3Anchors);
            markerState.barrierAnchor4Anchors.addAll(nearbyBarrierAnchor4Anchors);
            markerState.mobResetAnchors.addAll(nearbyMobResetAnchors);
            markerState.exitAnchors.addAll(nearbyExitAnchors);
            markerState.objectiveAnchors.addAll(nearbyObjectiveAnchors);
            markerState.interactAnchors.addAll(nearbyInteractAnchors);
        } else {
            markerState.boundsAnchors.removeIf(markerPos -> !bounds.contains(markerPos));
            for (BlockPos markerPos : nearbySpawnAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.spawnAnchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyRangedSpawnAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.rangedSpawnAnchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyFlankSpawnAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.flankSpawnAnchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyHighgroundSpawnAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.highgroundSpawnAnchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyEliteSpawnAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.eliteSpawnAnchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyBossSpawnAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.bossSpawnAnchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyBarrierAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.barrierAnchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyBarrierAnchor1Anchors) {
                if (bounds.contains(markerPos)) {
                    markerState.barrierAnchor1Anchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyBarrierAnchor2Anchors) {
                if (bounds.contains(markerPos)) {
                    markerState.barrierAnchor2Anchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyBarrierAnchor3Anchors) {
                if (bounds.contains(markerPos)) {
                    markerState.barrierAnchor3Anchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyBarrierAnchor4Anchors) {
                if (bounds.contains(markerPos)) {
                    markerState.barrierAnchor4Anchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyMobResetAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.mobResetAnchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyExitAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.exitAnchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyObjectiveAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.objectiveAnchors.add(markerPos);
                }
            }
            for (BlockPos markerPos : nearbyInteractAnchors) {
                if (bounds.contains(markerPos)) {
                    markerState.interactAnchors.add(markerPos);
                }
            }
        }
        markerState.markerCacheReady = true;
        markerState.markerCacheDirty = false;
        markerState.markerCacheDirtyReason = "NONE";
        markerState.markerScanCount += 1;
        markerState.lastMarkerScanReason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
        markerState.lastMarkerScanGameTime = level.getGameTime();
        markerState.lastMarkerScanDurationNanos = System.nanoTime() - startedAt;
        markerState.lastMarkerScanPerformanceWarning = markerState.lastMarkerScanDurationNanos >= MARKER_SCAN_WARN_THRESHOLD_NANOS
            ? "SLOW_SCAN " + String.format(java.util.Locale.ROOT, "%.2f", markerState.lastMarkerScanDurationNanos / 1_000_000.0D) + "ms"
            : "NONE";
    }

    private void scanClaimedMarkersInternal(ServerLevel level, String reason, long startedAt) {
        markerState.initializeClaimedMarkerLists();

        markerState.spawnAnchors.clear();
        markerState.rangedSpawnAnchors.clear();
        markerState.flankSpawnAnchors.clear();
        markerState.highgroundSpawnAnchors.clear();
        markerState.eliteSpawnAnchors.clear();
        markerState.bossSpawnAnchors.clear();
        markerState.barrierAnchors.clear();
        markerState.barrierAnchor1Anchors.clear();
        markerState.barrierAnchor2Anchors.clear();
        markerState.barrierAnchor3Anchors.clear();
        markerState.barrierAnchor4Anchors.clear();
        markerState.mobResetAnchors.clear();
        markerState.exitAnchors.clear();
        markerState.boundsAnchors.clear();
        markerState.objectiveAnchors.clear();
        markerState.interactAnchors.clear();

        markerState.rawSpawnAnchorPositions.clear();
        markerState.rawRangedSpawnAnchorPositions.clear();
        markerState.rawFlankSpawnAnchorPositions.clear();
        markerState.rawHighgroundSpawnAnchorPositions.clear();
        markerState.rawEliteSpawnAnchorPositions.clear();
        markerState.rawBossSpawnAnchorPositions.clear();
        markerState.rawBarrierAnchorPositions.clear();
        markerState.rawBarrierAnchor1Positions.clear();
        markerState.rawBarrierAnchor2Positions.clear();
        markerState.rawBarrierAnchor3Positions.clear();
        markerState.rawBarrierAnchor4Positions.clear();

        int staleRemoved = 0;
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.SPAWN_MELEE, markerState.spawnAnchors, markerState.rawSpawnAnchorPositions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.SPAWN_RANGED, markerState.rangedSpawnAnchors, markerState.rawRangedSpawnAnchorPositions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.SPAWN_FLANK, markerState.flankSpawnAnchors, markerState.rawFlankSpawnAnchorPositions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.SPAWN_HIGHGROUND, markerState.highgroundSpawnAnchors, markerState.rawHighgroundSpawnAnchorPositions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.SPAWN_ELITE, markerState.eliteSpawnAnchors, markerState.rawEliteSpawnAnchorPositions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.SPAWN_BOSS, markerState.bossSpawnAnchors, markerState.rawBossSpawnAnchorPositions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.BARRIER, markerState.barrierAnchors, markerState.rawBarrierAnchorPositions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.BARRIER_1, markerState.barrierAnchor1Anchors, markerState.rawBarrierAnchor1Positions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.BARRIER_2, markerState.barrierAnchor2Anchors, markerState.rawBarrierAnchor2Positions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.BARRIER_3, markerState.barrierAnchor3Anchors, markerState.rawBarrierAnchor3Positions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.BARRIER_4, markerState.barrierAnchor4Anchors, markerState.rawBarrierAnchor4Positions);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.MOB_RESET, markerState.mobResetAnchors, null);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.EXIT, markerState.exitAnchors, null);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.BOUNDS, markerState.boundsAnchors, null);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.OBJECTIVE, markerState.objectiveAnchors, null);
        staleRemoved += copyValidClaimedMarkers(level, RoomClaimController.ClaimedMarkerKind.INTERACT, markerState.interactAnchors, null);

        markerState.rawSpawnAnchorCount = markerState.spawnAnchors.size();
        markerState.rawRangedSpawnAnchorCount = markerState.rangedSpawnAnchors.size();
        markerState.rawFlankSpawnAnchorCount = markerState.flankSpawnAnchors.size();
        markerState.rawHighgroundSpawnAnchorCount = markerState.highgroundSpawnAnchors.size();
        markerState.rawEliteSpawnAnchorCount = markerState.eliteSpawnAnchors.size();
        markerState.rawBossSpawnAnchorCount = markerState.bossSpawnAnchors.size();
        markerState.rawBarrierAnchorCount = markerState.barrierAnchors.size();
        markerState.rawBarrierAnchor1Count = markerState.barrierAnchor1Anchors.size();
        markerState.rawBarrierAnchor2Count = markerState.barrierAnchor2Anchors.size();
        markerState.rawBarrierAnchor3Count = markerState.barrierAnchor3Anchors.size();
        markerState.rawBarrierAnchor4Count = markerState.barrierAnchor4Anchors.size();
        markerState.rawMobResetAnchorCount = markerState.mobResetAnchors.size();
        markerState.rawExitAnchorCount = markerState.exitAnchors.size();
        markerState.rawBoundsAnchorCount = markerState.boundsAnchors.size();
        markerState.rawObjectiveAnchorCount = markerState.objectiveAnchors.size();
        markerState.rawInteractAnchorCount = markerState.interactAnchors.size();

        markerState.recordClaimedMarkerScan(staleRemoved);
        markerState.markerCacheReady = true;
        markerState.markerCacheDirty = false;
        markerState.markerCacheDirtyReason = "NONE";
        markerState.markerScanCount += 1;
        markerState.lastMarkerScanReason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
        markerState.lastMarkerScanGameTime = level.getGameTime();
        markerState.lastMarkerScanDurationNanos = System.nanoTime() - startedAt;
        markerState.lastMarkerScanPerformanceWarning = markerState.lastMarkerScanDurationNanos >= MARKER_SCAN_WARN_THRESHOLD_NANOS
            ? "SLOW_SCAN " + String.format(java.util.Locale.ROOT, "%.2f", markerState.lastMarkerScanDurationNanos / 1_000_000.0D) + "ms"
            : "NONE";
        if (staleRemoved > 0) {
            setChanged();
        }
    }

    private int copyValidClaimedMarkers(ServerLevel level, RoomClaimController.ClaimedMarkerKind kind, List<BlockPos> target, List<BlockPos> rawTarget) {
        List<BlockPos> source = markerState.claimedMarkers(kind);
        int before = source.size();
        if (!markerState.bakedMarkerMode) {
            source.removeIf(pos -> RoomMarkerState.markerKindFor(level.getBlockState(pos)) != kind);
        }
        for (BlockPos pos : source) {
            BlockPos immutable = pos.immutable();
            target.add(immutable);
            if (rawTarget != null) {
                rawTarget.add(immutable);
            }
        }
        return before - source.size();
    }

    public void bakeGeneratedMarkers(ServerLevel level) {
        if (level == null || markerState.bakedMarkerMode || !markerState.claimedMarkerMode) {
            return;
        }
        markerState.bakedMarkerMode = true;
        for (RoomClaimController.ClaimedMarkerKind kind : RoomClaimController.ClaimedMarkerKind.values()) {
            for (BlockPos markerPos : List.copyOf(markerState.claimedMarkers(kind))) {
                BlockState state = level.getBlockState(markerPos);
                BlockState replacement = state.is(ModBlocks.FLOOR_CONNECTOR_ANCHOR.get())
                    || state.is(ModBlocks.CONNECTOR_EXIT.get())
                    || state.is(ModBlocks.FLOOR_ENTRY_ANCHOR.get())
                    || state.is(ModBlocks.EXIT_ANCHOR.get())
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
                level.setBlock(markerPos, replacement, Block.UPDATE_CLIENTS);
            }
        }
        markMarkerCacheDirty("GENERATED_MARKERS_BAKED");
        ensureMarkerCache(level, "GENERATED_MARKERS_BAKED");
        setChanged();
    }

    boolean isBakedMarkerMode() {
        return markerState.bakedMarkerMode;
    }

    private boolean hasPlayerInActivationZone(ServerLevel level) {
        return playerSessionAssociation.hasPlayerInActivationZone(
            level, worldPosition, hasBoundsBox(), getActivationBounds()
        );
    }

    private boolean isActivationBlocked(ServerLevel level) {
        return !"None".equals(getActivationBlockReason(level));
    }

    private void notifyRunActivationBlocked(ServerLevel level) {
        String reason = getRunActivationBlockReason(level);
        playerSessionAssociation.notifyRunActivationBlocked(
            level,
            worldPosition,
            hasBoundsBox(),
            getActivationBounds(),
            Component.literal(activationFailureMessageForRunBlock(reason))
        );
    }

    private ServerPlayer getNearestPlayerInActivationZone(ServerLevel level) {
        return playerSessionAssociation.nearestPlayerInActivationZone(
            level, worldPosition, hasBoundsBox(), getActivationBounds()
        );
    }

    private void syncRunActivationState(ServerLevel level) {
        RoomRunGateway runGateway = roomRunGateway(level);
        playerSessionAssociation.recordRunActivationBlockReason(getRunActivationBlockReason(level));
        pruneRunActivationBlockMessageCooldowns(level);
        if (lifecycle.snapshot().state() == RoomState.ACTIVE && !runGateway.isActiveRun() && !runGateway.isDebugRoomActivationOverride()) {
            failRoom(level, "RUN_ENDED_DURING_ACTIVE_ROOM");
        }
    }

    private void pruneRunActivationBlockMessageCooldowns(ServerLevel level) {
        playerSessionAssociation.pruneRunActivationBlockMessageCooldowns(
            level, worldPosition, hasBoundsBox(), getActivationBounds()
        );
    }

    public boolean requiresActiveRun() {
        return true;
    }

    public String getRunActivationBlockReason(ServerLevel level) {
        RoomRunGateway runGateway = roomRunGateway(level);
        if (dungeonInstanceKey != null
            && !DungeonRunManager.get(level).hasInstance(dungeonInstanceKey)) {
            return "INSTANCE_RUN_OWNERSHIP_MISMATCH";
        }
        String presetBlockReason = runGateway.presetActivationBlockReason();
        if (!"NONE".equals(presetBlockReason)) {
            playerSessionAssociation.recordRunActivationBlockReason(presetBlockReason);
            return presetBlockReason;
        }
        for (BlockPos activePos : runGateway.activeRoomControllerPositions()) {
            if (!activePos.equals(worldPosition)) {
                String reason = "OTHER_ACTIVE_ROOM_" + activePos.toShortString();
                playerSessionAssociation.recordRunActivationBlockReason(reason);
                return reason;
            }
        }
        if (runGateway.isDebugRoomActivationOverride()) return "NONE";
        if (!runGateway.isActiveRun()) {
            return "NO_ACTIVE_RUN";
        }
        if (runGateway.isRunCompleted()) {
            return "RUN_COMPLETED";
        }
        if (runGateway.isFailed()) {
            return "RUN_FAILED";
        }
        ServerPlayer playerInActivationZone = getNearestPlayerInActivationZone(level);
        if (playerInActivationZone != null && dungeonInstanceKey != null) {
            DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
            boolean playerMatches = allocations
                .forPlayer(playerInActivationZone.getUUID())
                .map(DungeonInstanceAllocationData.Allocation::key)
                .filter(dungeonInstanceKey::equals)
                .isPresent();
            if (!playerMatches) {
                playerMatches = allocations.claimLegacyPlayer(
                    dungeonInstanceKey, playerInActivationZone.getUUID()
                );
            }
            if (!playerMatches) {
                return "PLAYER_INSTANCE_OWNERSHIP_MISMATCH";
            }
        }
        if (playerInActivationZone != null && runGateway.isHubEnabled() && runGateway.isPlayerInsideHubSafeZone(playerInActivationZone.blockPosition())) {
            playerSessionAssociation.recordRunActivationBlockReason("HUB_SAFE_ZONE_SUPPRESSED");
            return "HUB_SAFE_ZONE_SUPPRESSED";
        }
        return "NONE";
    }

    private String activationFailureMessageForRunBlock(String runBlockReason) {
        if (runBlockReason == null || runBlockReason.isBlank() || "NONE".equals(runBlockReason)) {
            return "NONE";
        }
        return switch (runBlockReason) {
            case "NO_ACTIVE_RUN" -> "No active dungeon run. Use /tensuradungeon run start first.";
            case "RUN_COMPLETED" -> "Dungeon floor is already complete. Use /tensuradungeon run reset or /tensuradungeon run end.";
            case "RUN_FAILED" -> "Dungeon run has failed. Use /tensuradungeon run reset or /tensuradungeon run end.";
            case "HUB_SAFE_ZONE_SUPPRESSED" -> "Dungeon room activation suppressed inside the hub safe zone.";
            case "INSTANCE_RUN_OWNERSHIP_MISMATCH" -> "Dungeon room rejected: its instance identity does not match the active run.";
            case "PLAYER_INSTANCE_OWNERSHIP_MISMATCH" -> "Dungeon room rejected: this player is not assigned to the room's instance.";
            case "PRESET_CONTROLLER_NOT_IN_ORDER" -> "Preset room rejected: controller is not in the current preset order.";
            case "PRESET_CONTROLLER_ALREADY_COMPLETED" -> "Preset room rejected: this controller is already completed for the current floor.";
            case "PRESET_NO_EXPECTED_CONTROLLER", "PRESET_EXPECTED_CONTROLLER_MISSING" -> "Preset room rejected: no expected controller is available.";
            case "PRESET_CONTROLLER_POS_MISSING" -> "Preset room rejected: controller position is missing.";
            default -> runBlockReason.startsWith("PRESET_UNEXPECTED_CONTROLLER_EXPECTED_")
                ? "Preset room rejected: not the expected next controller. " + runBlockReason
                : runBlockReason;
        };
    }

    public String getActivationBlockReason(ServerLevel level) {
        ensureMarkerCache(level, "ACTIVATION_CHECK");
        if (lifecycle.snapshot().branchPreviewMode()) {
            return "Branch preview is waiting for route commitment.";
        }
        if (roomRunGateway(level).isRoomRegisteredCompleted()) {
            return "Room already counted as completed for this floor.";
        }
        if (lifecycle.snapshot().profile().allowsCombat() && getOwnedNormalCombatAnchorCount() <= 0) {
            return "Combat profile requires owned combat spawn anchor.";
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return "World difficulty is Peaceful.";
        }
        if (lifecycle.snapshot().completed()) {
            return "Room is already completed.";
        }
        if (lifecycle.snapshot().state() != RoomState.INACTIVE) {
            return "Room is not inactive.";
        }
        if (hasAnotherActiveRoomNearby(level)) {
            return "Another room is already active nearby.";
        }
        return "None";
    }

    public boolean activate(ServerLevel level) {
        GeneratedRoomHooks generatedHooks = generatedRoomHooks(level);
        RoomRunGateway runGateway = roomRunGateway(level);
        RoomLifecycle.ActivationResult result = lifecycle.activate(new RoomLifecycle.ActivationActions(
            () -> ensureMarkerCache(level, "ACTIVATE"),
            new RoomLifecycle.ActivationGuards(
                generatedHooks::isFrontierCandidate,
                generatedHooks::commitFrontierOnEntry,
                () -> "Frontier route commitment failed for " + worldPosition.toShortString() + ".",
                () -> getRunActivationBlockReason(level),
                playerSessionAssociation::recordRunActivationBlockReason,
                this::activationFailureMessageForRunBlock,
                () -> getActivationBlockReason(level),
                runGateway::activateRoom
            ),
            generatedHooks::isFirstGeneratedRoom,
            () -> clearAllPreviews(level),
            () -> {
                encounter.prepareForActivation();
                playerSessionAssociation.clearTransientState();
                RoomObjectiveType selectedObjectiveType = objective.resolveObjectiveTypeForActivation(level, objectiveContext(level));
                objective.initializeForActivation(
                    selectedObjectiveType,
                    objective.getObjectiveDurationFor(selectedObjectiveType)
                );
            },
            new RoomLifecycle.ActivationRuntimeActions(
                () -> playerSessionAssociation.associateTriggeringPlayer(
                    level, worldPosition, hasBoundsBox(), getActivationBounds()
                ),
                () -> clearTemporarySeal(level),
                () -> closeBarrier(level),
                () -> {
                    DungeonSoundHooks.roomActivated(
                        level,
                        worldPosition,
                        isSafeRoomProfile(),
                        runGateway.remainingRoomCount() <= 1
                    );
                    applyEncounterEffect(level, encounter.spawnZombies(level));
                    if (encounter.requiresEliteAnchor(level) && !markerState.eliteSpawnAnchors.isEmpty()) {
                        encounter.spawnElite(level);
                        DungeonSoundHooks.eliteRevealed(level, worldPosition);
                    }
                    applyEncounterEffect(level, encounter.spawnBossIfAuthorized(level));
                    if (objective.activeObjectiveType.usesEliteTarget()) {
                        encounter.selectKillEliteTarget(level);
                    }
                    if (objective.activeObjectiveType.usesRuneTargets()) {
                        objective.initializeRuneObjective(level, objectiveContext(level));
                    }
                    objective.announceObjectiveStart(level, objectiveContext(level));
                }
            ),
            new RoomLifecycle.ActivationFinalizationActions(
                () -> isSafeRoomProfile()
                    ? "SAFE_ROOM_AUTO_COMPLETED"
                    : objective.objectiveDisabled ? "PASSIVE_ROOM_AUTO_COMPLETED" : "NONE",
                objective::recordAutomaticCompletion,
                () -> complete(level),
                () -> hasActiveModifier(level, DungeonModifierDefinition.SUMMONER),
                () -> {
                    if (encounter.selectSummoners(level)) {
                        setChanged();
                    }
                },
                () -> ActiveRoomProtectionEvents.syncProtectionRegistration(level, this),
                this::setChanged
            )
        ));
        if (!result.activated()) {
            return false;
        }
        if (!result.autoCompleted()) {
            SubordinateDebriefController.onCombatActivated(dungeonInstanceKey);
        }
        logActivationProfile(level, result.profileResult(), result.timing());
        return true;
    }
    private void logActivationProfile(
        ServerLevel level,
        String result,
        RoomLifecycle.ActivationTiming timing
    ) {
        LOGGER.info(
            "[TD-PERF] room-activation result={} controller={} markerCacheMs={} frontierCommitMs={} guardChecksMs={} registrationMs={} stateInitializationMs={} runtimeSetupMs={} hudStateReadyMs={} finalizationMs={} totalMs={} hudReadable={} roomState={} objective={} objectiveActive={} gameTime={} dimension={}",
            result, worldPosition, timing.markerCacheMs(), timing.frontierCommitMs(), timing.guardChecksMs(), timing.registrationMs(),
            timing.stateInitializationMs(), timing.runtimeSetupMs(), timing.hudStateReadyMs(), timing.finalizationMs(), timing.totalMs(),
            true, lifecycle.snapshot().state(), objective.activeObjectiveType, objective.objectiveActive,
            level.getGameTime(), level.dimension().location()
        );
    }

    private boolean hasAnotherActiveRoomNearby(ServerLevel level) {
        return getNearbyActiveRoomCount(level) > 0;
    }

    private int getNearbyActiveRoomCount(ServerLevel level) {
        int count = 0;
        int maxDistanceSqr = ACTIVE_ROOM_SAFETY_RADIUS * ACTIVE_ROOM_SAFETY_RADIUS;
        for (BlockPos activePos : roomRunGateway(level).activeRoomControllerPositions()) {
            if (activePos == null || activePos.equals(worldPosition)) {
                continue;
            }
            if (activePos.distSqr(worldPosition) > maxDistanceSqr) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(activePos);
            if (blockEntity instanceof RoomControllerBlockEntity other && other.lifecycle.snapshot().state() == RoomState.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    private void closeBarrier(ServerLevel level) {
        barrierController.closeBarrier(level, barrierInputs());
    }

    public boolean placeTemporarySeal(ServerLevel level, BlockPos connectorPos, boolean blackConcrete) {
        return barrierController.placeTemporarySeal(level, connectorPos, blackConcrete, barrierInputs());
    }

    public boolean clearTemporarySealNear(ServerLevel level, BlockPos connectorPos) {
        return barrierController.clearTemporarySealNear(level, connectorPos);
    }
    public void clearTemporarySeal(ServerLevel level) {
        barrierController.clearTemporarySeal(level);
    }

    public void clearTemporaryBlackSeal(ServerLevel level) {
        barrierController.clearTemporaryBlackSeal(level);
    }

    public void releaseBarrierResources(ServerLevel level) {
        barrierController.openBarrier(level);
        barrierController.clearTemporarySeal(level);
        barrierController.clearBarrierPreview(level);
    }

    public boolean placeBarrierPreview(ServerLevel level) {
        return barrierController.placeBarrierPreview(level, barrierInputs());
    }

    private boolean clearBarrierPreview(ServerLevel level) {
        return barrierController.clearBarrierPreview(level);
    }

    public boolean placeBoundsPreview(ServerLevel level) {
        clearBoundsPreview(level);
        RoomBounds bounds = roomBounds();
        if (bounds == null) {
            return false;
        }

        boolean placedAny = false;
        for (BlockPos previewPos : bounds.getCornerPositions()) {
            if (level.getBlockState(previewPos).isAir()) {
                level.setBlock(previewPos, BOUNDS_PREVIEW_BLOCK_STATE, 3);
                markerState.boundsPreviewPositions.add(previewPos.immutable());
                placedAny = true;
            }
        }
        if (placedAny) {
            setChanged();
        }
        return placedAny;
    }

    private boolean clearBoundsPreview(ServerLevel level) {
        return clearPreviewSet(level, markerState.boundsPreviewPositions, BOUNDS_PREVIEW_BLOCK_STATE);
    }

    private boolean clearClaimPreview(ServerLevel level) {
        boolean clearedAny = false;
        for (BlockPos previewPos : markerState.claimPreviewPositions) {
            if (claimController.isClaimPreviewBlockState(level.getBlockState(previewPos))) {
                level.removeBlock(previewPos, false);
                clearedAny = true;
            }
        }
        markerState.claimPreviewPositions.clear();
        if (clearedAny) {
            setChanged();
        }
        return clearedAny;
    }

    public boolean clearAllPreviews(ServerLevel level) {
        boolean clearedBarrier = clearBarrierPreview(level);
        boolean clearedBounds = clearBoundsPreview(level);
        boolean clearedClaim = clearClaimPreview(level);
        return clearedBarrier || clearedBounds || clearedClaim;
    }

    private boolean clearPreviewSet(ServerLevel level, Set<BlockPos> previewSet, BlockState previewState) {
        boolean clearedAny = false;
        for (BlockPos previewPos : previewSet) {
            if (level.getBlockState(previewPos).is(previewState.getBlock())) {
                level.removeBlock(previewPos, false);
                clearedAny = true;
            }
        }
        previewSet.clear();
        if (clearedAny) {
            setChanged();
        }
        return clearedAny;
    }

    RoomBarrierController.Inputs barrierInputs() {
        return new RoomBarrierController.Inputs(
            worldPosition,
            roomBounds(),
            getActivationRadius(),
            getRoomProfile(),
            hasSpawnBossAnchor(),
            objective.activeObjectiveType,
            objective.objectiveDisabled,
            doesProfileAllowCombat(),
            markerState.barrierAnchors,
            markerState.barrierAnchor1Anchors,
            markerState.barrierAnchor2Anchors,
            markerState.barrierAnchor3Anchors,
            markerState.barrierAnchor4Anchors
        );
    }

    private void openBarrier(ServerLevel level) {
        barrierController.openBarrier(level);
    }

    record GeneratedMarkerManifestResult(
        boolean success,
        String reason,
        @Nullable MarkerClaimResult claimResult
    ) {
        private static GeneratedMarkerManifestResult success(MarkerClaimResult result) {
            return new GeneratedMarkerManifestResult(true, "GENERATED_MARKER_MANIFEST_CLAIMED", result);
        }

        private static GeneratedMarkerManifestResult failed(String reason) {
            return new GeneratedMarkerManifestResult(false, reason, null);
        }
    }

    public record MarkerClaimResult(
        int radius,
        int totalClaimed,
        int skippedOwnedByOther,
        int skippedOutsideBounds,
        int rawScanned,
        boolean boundsFiltered,
        int staleRemoved,
        long durationNanos,
        String summary,
        String reason
    ) {
        public double durationMs() {
            return durationNanos < 0L ? -1.0D : durationNanos / 1_000_000.0D;
        }
    }

    public record ClaimPreviewResult(
        int radius,
        boolean force,
        boolean boundsFiltered,
        RoomBounds selectedBounds,
        int rawScanned,
        int totalClaimed,
        int skippedOutsideBounds,
        int skippedOwnedByOther,
        int previewBlocksPlaced,
        int previewPlacementSkipped,
        int edgeBlocksPlaced,
        boolean edgePlacementCapped,
        String claimedSummary,
        String skippedOutsideSummary,
        String skippedOwnedByOtherSummary,
        ClaimBoundsResolution boundsResolution
    ) {
    }

    public record ClaimBoundsCandidate(
        BlockPos anchorA,
        BlockPos anchorB,
        RoomBounds bounds,
        boolean containsController,
        int controllerCount,
        int foreignControllerCount,
        long volume,
        String rejectionReason,
        boolean selected
    ) {
        boolean equalsByValue(ClaimBoundsCandidate other) {
            return other != null
                && anchorA.equals(other.anchorA)
                && anchorB.equals(other.anchorB)
                && bounds.min().equals(other.bounds.min())
                && bounds.max().equals(other.bounds.max())
                && containsController == other.containsController
                && controllerCount == other.controllerCount
                && foreignControllerCount == other.foreignControllerCount
                && volume == other.volume
                && rejectionReason.equals(other.rejectionReason);
        }
    }

    public record ClaimBoundsResolution(
        int radius,
        List<BlockPos> nearbyBoundsAnchors,
        List<ClaimBoundsCandidate> candidates,
        RoomBounds selectedBounds,
        BlockPos selectedAnchorA,
        BlockPos selectedAnchorB,
        int selectedControllerCount,
        int selectedForeignControllerCount,
        int rejectMissingControllerCount,
        int rejectForeignControllerCount,
        String selectedReason
    ) {
        public static ClaimBoundsResolution empty() {
            return new ClaimBoundsResolution(0, List.of(), List.of(), null, null, null, 0, 0, 0, 0, "NONE");
        }
    }

    private void handleAssociatedActivePlayers(ServerLevel level) {
        PlayerSessionAssociation.ActivePlayerTickResult result = playerSessionAssociation.tickActivePlayers(
            level,
            worldPosition,
            lifecycle.snapshot().state() == RoomState.ACTIVE,
            lifecycle.snapshot().completed(),
            hasBoundsBox(),
            roomBounds(),
            getActivationBounds()
        );
        switch (result) {
            case PLAYER_DIED -> failRoom(level, "PLAYER_DIED");
            case PLAYER_LEFT_ACTIVE_BOUNDS -> recoverAssociatedPlayerInsideBounds(level);
            case PLAYER_RECOVERING_OUTSIDE_BOUNDS -> {
            }
            case NO_VALID_ACTIVE_PLAYER -> {
                if (dungeonInstanceKey == null
                    || !DungeonInstanceRecoveryService.suspendAbsentSoloMember(
                        level, dungeonInstanceKey
                    )) {
                    failRoom(level, "NO_VALID_ACTIVE_PLAYER");
                }
            }
            case CONTINUE -> {
                List<ServerPlayer> activePlayers = playerSessionAssociation.validPlayers(level);
                applyPlayerBoons(level, activePlayers);
                applyEncounterEffect(
                    level,
                    encounter.updateCombatProgression(level, activePlayers)
                );
            }
        }
    }

    private boolean isInstanceSuspended(ServerLevel level) {
        if (dungeonInstanceKey == null) {
            return false;
        }
        return DungeonInstanceAllocationData.get(level)
            .forIdentity(dungeonInstanceKey)
            .map(DungeonInstanceAllocationData.Allocation::lifecycle)
            .map(DungeonInstanceAllocationData.Lifecycle::state)
            .filter(state -> state == DungeonInstanceAllocationData.LifecycleState.SUSPENDED)
            .isPresent();
    }

    private void recoverAssociatedPlayerInsideBounds(ServerLevel level) {
        UUID playerId = playerSessionAssociation.pendingBoundsRecoveryPlayerId();
        ServerPlayer player = playerId == null
            ? null
            : level.getServer().getPlayerList().getPlayer(playerId);
        if (player == null || dungeonInstanceKey == null) {
            return;
        }
        Optional<BlockPos> destination = resolvePlayerRecoveryDestination(level, player);
        if (destination.isEmpty()) {
            DungeonInstanceRecoveryService.ResumeResult fallback =
                DungeonInstanceRecoveryService.recoverOutsideActiveBounds(
                    level, player, dungeonInstanceKey, worldPosition,
                    !playerSessionAssociation.fallbackRecoveryUsed(playerId)
                );
            if (fallback.succeeded()
                && ("RETAINED_CLEARED_ROOM_RECOVERY".equals(fallback.reason())
                    || "INITIAL_SAFE_ROOM_RECOVERY".equals(fallback.reason()))) {
                playerSessionAssociation.beginFallbackBoundsRecovery(playerId);
            } else {
                playerSessionAssociation.completeBoundsRecovery(playerId);
            }
            if (!fallback.succeeded()) {
                player.sendSystemMessage(Component.literal(
                    "Dungeon recovery could not find a safe retained checkpoint or close the run."
                ));
            }
            return;
        }
        teleportRecoveredPlayer(level, player, destination.get());
        playerSessionAssociation.completeBoundsRecovery(playerId);
        player.sendSystemMessage(Component.literal(
            "You left the active room and were returned to its reset anchor."
        ));
    }

    private static void teleportRecoveredPlayer(
        ServerLevel level,
        ServerPlayer player,
        BlockPos target
    ) {
        player.teleportTo(
            level,
            target.getX() + 0.5D,
            target.getY(),
            target.getZ() + 0.5D,
            player.getYRot(),
            player.getXRot()
        );
        player.fallDistance = 0.0F;
    }

    public boolean isCompletedRoomForRecovery() {
        return lifecycle.snapshot().state() == RoomState.COMPLETED
            && lifecycle.snapshot().completed()
            && hasBoundsBox();
    }

    public Optional<BlockPos> resolveCompletedRoomRecoveryDestination(
        ServerLevel level,
        ServerPlayer player
    ) {
        return resolveVerifiedPlayerRecoveryDestination(level, player, false);
    }

    private Optional<BlockPos> resolveVerifiedPlayerRecoveryDestination(
        ServerLevel level,
        ServerPlayer player,
        boolean activeRoom
    ) {
        if (player == null || dungeonInstanceKey == null
            || (activeRoom && !isActiveRoomForRecovery())
            || (!activeRoom && !isCompletedRoomForRecovery())) {
            return Optional.empty();
        }
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(level).forIdentity(dungeonInstanceKey).orElse(null);
        DungeonRunManager manager = DungeonRunManager.get(level);
        boolean registered = activeRoom
            ? manager.getActiveRoomControllerPositions(dungeonInstanceKey).contains(worldPosition)
            : manager.isRoomRegisteredCompleted(dungeonInstanceKey, worldPosition);
        if (allocation == null || !allocation.roster().contains(player.getUUID())
            || !allocation.dimension().equals(level.dimension().location().toString())
            || !allocation.cell().bounds().contains(worldPosition) || !registered) {
            return Optional.empty();
        }
        ensureMarkerCache(level, activeRoom
            ? "PLAYER_BOUNDS_RECOVERY" : "PLAYER_RETAINED_ROOM_RECOVERY");
        List<BlockPos> anchors = new ArrayList<>(markerState.mobResetAnchors);
        anchors.addAll(markerState.spawnAnchors);
        anchors.addAll(markerState.flankSpawnAnchors);
        anchors.addAll(markerState.rangedSpawnAnchors);
        anchors.addAll(markerState.eliteSpawnAnchors);
        anchors.addAll(markerState.highgroundSpawnAnchors);
        anchors.sort(Comparator.comparingDouble(anchor -> anchor.distSqr(player.blockPosition())));
        for (BlockPos anchor : anchors) {
            BlockPos base = markerState.bakedMarkerMode ? anchor : anchor.above();
            for (BlockPos destination : List.of(
                base, base.north(), base.south(), base.east(), base.west()
            )) {
                if (isVerifiedPlayerRecoveryDestination(
                    level, player, allocation, destination
                )) {
                    return Optional.of(destination.immutable());
                }
            }
        }
        return Optional.empty();
    }

    private boolean isVerifiedPlayerRecoveryDestination(
        ServerLevel level,
        ServerPlayer player,
        DungeonInstanceAllocationData.Allocation allocation,
        BlockPos destination
    ) {
        RoomBounds bounds = roomBounds();
        BlockPos floor = destination.below();
        if (bounds == null || !bounds.contains(floor) || !bounds.contains(destination)
            || !bounds.contains(destination.above())
            || !allocation.cell().bounds().contains(floor)
            || !allocation.cell().bounds().contains(destination)
            || !allocation.cell().bounds().contains(destination.above())
            || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
            || !level.getBlockState(destination).getCollisionShape(level, destination).isEmpty()
            || !level.getBlockState(destination.above())
                .getCollisionShape(level, destination.above()).isEmpty()
            || !level.getFluidState(destination).isEmpty()
            || !level.getFluidState(destination.above()).isEmpty()) {
            return false;
        }
        AABB movedBounds = player.getBoundingBox().move(
            destination.getX() + 0.5D - player.getX(),
            destination.getY() - player.getY(),
            destination.getZ() + 0.5D - player.getZ()
        );
        return level.noCollision(player, movedBounds);
    }

    public Optional<BlockPos> resolvePlayerRecoveryDestination(
        ServerLevel level,
        ServerPlayer player
    ) {
        return resolveVerifiedPlayerRecoveryDestination(level, player, true);
    }

    public boolean ownsDungeonInstance(DungeonInstanceAllocationData.InstanceKey expected) {
        return expected != null && expected.equals(dungeonInstanceKey);
    }

    public boolean isActiveRoomForRecovery() {
        return lifecycle.snapshot().state() == RoomState.ACTIVE
            && !lifecycle.snapshot().completed()
            && hasBoundsBox();
    }

    private void applyPlayerBoons(ServerLevel level, List<ServerPlayer> players) {
        if (!DungeonModifierCatalog.isRollable("barrier")
            || !DungeonRunManager.get(level).getActiveModifierIds(dungeonInstanceKey).contains("barrier")) {
            return;
        }
        String marker = DungeonRunManager.get(level).getRunSessionId(dungeonInstanceKey) + "@" + worldPosition.asLong();
        for (ServerPlayer player : players) {
            CompoundTag data = player.getPersistentData();
            if (marker.equals(data.getString("TensuraDungeonBarrierRoom"))) continue;
            player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), player.getMaxHealth() * 0.15F));
            data.putString("TensuraDungeonBarrierRoom", marker);
        }
    }

    private @javax.annotation.Nullable ServerPlayer resolveHubReturnPlayer(ServerLevel level) {
        return playerSessionAssociation.resolveHubReturnPlayer(
            level, worldPosition, hasBoundsBox(), getActivationBounds()
        );
    }

    private void updateObjective(ServerLevel level) {
        applyObjectiveCompletion(level, objective.updateObjective(level, objectiveContext(level), encounter.eliteMobUuid));
    }

    public boolean activateNearestRune(ServerLevel level, ServerPlayer player) {
        boolean activated = objective.activateNearestRune(
            level, player, getRoomState(), roomBounds(), worldPosition
        );
        if (activated) {
            setChanged();
        }
        return activated;
    }

    int countAssociatedPlayersInBounds(ServerLevel level) {
        return objective.countAssociatedPlayersInBounds(level, roomBounds());
    }

    private RoomObjective.Context objectiveContext(ServerLevel level) {
        return new RoomObjective.Context(
            worldPosition,
            roomBounds(),
            getRoomProfile(),
            getOwnedNormalCombatAnchorCount(),
            List.copyOf(markerState.eliteSpawnAnchors),
            List.copyOf(markerState.objectiveAnchors),
            getProfileInitialSpawnCount(),
            DungeonRunManager.get(level).getFloorNumber(dungeonInstanceKey)
        );
    }

    private void applyObjectiveCompletion(ServerLevel level, RoomObjective.CompletionEffect effect) {
        if (!effect.completesRoom()) {
            return;
        }
        if (effect.eliteDefeated()) {
            encounter.markEliteDefeated();
        }
        setLastCompletionReason(effect.completionReason());
        clearTrackedMobs(level);
        complete(level);
    }

    private void applyEncounterEffect(ServerLevel level, RoomEncounter.Effect effect) {
        if (effect.changed()) {
            setChanged();
        }
        if (!"NONE".equals(effect.activationFailureReason())) {
            recordActivationFailure(effect.activationFailureReason());
        }
        if (!"NONE".equals(effect.roomFailureReason())) {
            failActiveRoomForSpawnFailure(level, effect.roomFailureReason());
        }
    }

    private void updateTrackedMobs(ServerLevel level) {
        applyEncounterEffect(level, encounter.updateTrackedMobs(level));
        RoomObjective.ClearAllEffect clearAllEffect = objective.updateClearAllState(
            encounter.trackedMobUuids.isEmpty(),
            encounter.hasPendingWaves(),
            encounter.hadAnyMobConfirmedRemoved
        );
        if (clearAllEffect == RoomObjective.ClearAllEffect.NONE) {
            setChanged();
            return;
        }
        encounter.clearMissingMobTracking();
        if (clearAllEffect == RoomObjective.ClearAllEffect.COMPLETE) {
            lifecycle.setCompletionReason("CLEAR_ALL_COMPLETE");
            complete(level);
        } else {
            lifecycle.markResettingWarning(
                "WARN: Active room has no tracked mobs and is not completed.",
                "NO_TRACKED_MOBS_WARNING"
            );
            setChanged();
        }
    }

    private void updateMobResetCooldowns() {
        encounter.updateMobResetCooldowns();
    }

    void complete(ServerLevel level) {
        complete(level, null);
    }

    void complete(ServerLevel level, ServerPlayer fallbackRewardPlayer) {
        DungeonSubordinateSkillCombatController.cancelForInstance(
            level,
            dungeonInstanceKey,
            "ROOM_COMPLETED"
        );
        RoomRunGateway runGateway = roomRunGateway(level);
        GeneratedRoomHooks generatedHooks = generatedRoomHooks(level);
        DungeonRunService.FloorCompletionContext completionContext =
            new DungeonRunService.FloorCompletionContext(
                worldPosition,
                getRoomProfile() == null ? "UNKNOWN_PROFILE" : getRoomProfile().name(),
                objective.rewardObjectiveSnapshot(),
                getObjectiveSelectionMode()
            );
        lifecycle.complete(level.getGameTime(), new RoomLifecycle.CompletionActions<ServerPlayer>(
            () -> {
                ServerPlayer rewardPlayer = getRewardRecipient(level);
                if (rewardPlayer == null && fallbackRewardPlayer != null && !fallbackRewardPlayer.isSpectator()) {
                    rewardPlayer = fallbackRewardPlayer;
                }
                return rewardPlayer;
            },
            () -> objective.clearRuneObjectiveState(level),
            objective::deactivateForRoomCompletion,
            objective::markRoomCompleted,
            () -> objective.announceObjectiveProgress(level, roomBounds(), Component.literal("Room complete.")),
            () -> clearAllPreviews(level),
            () -> openBarrier(level),
            generatedHooks::openSuccessorIncomingSeal,
            generatedHooks::retainTerminalOutgoingSeal,
            () -> DungeonSoundHooks.roomCompleted(level, worldPosition),
            () -> DungeonSoundHooks.objectiveCompleted(level, worldPosition),
            rewardPlayer -> runGateway.recordCoreRecoveryEncounter(rewardPlayer, completionContext),
            playerSessionAssociation::clearTransientState,
            () -> ActiveRoomProtectionEvents.syncProtectionRegistration(level, this),
            generatedHooks::resolvePlayerDirectedExitAndPlaceNext,
            runGateway::completeRoom,
            () -> DungeonSoundHooks.floorCompleted(level, worldPosition),
            (rewardPlayer, runResult) -> runGateway.applyFloorCompletionEffects(
                rewardPlayer,
                runResult,
                completionContext
            ),
            this::setChanged
        ));
        SubordinateDebriefController.onRoomCompleted(level, this);
    }

    private void failRoom(ServerLevel level, String reason) {
        DungeonSubordinateSkillCombatController.cancelForInstance(
            level,
            dungeonInstanceKey,
            "ROOM_FAILED_" + reason
        );
        for (ServerPlayer player : playerSessionAssociation.validPlayers(level)) {
            player.getPersistentData().remove("TensuraDungeonBarrierRoom");
        }
        RoomRunGateway runGateway = roomRunGateway(level);
        lifecycle.fail(level.getGameTime(), reason, new RoomLifecycle.FailureActions(
            () -> clearAllPreviews(level),
            () -> openBarrier(level),
            () -> objective.clearRuneObjectiveState(level),
            () -> clearTrackedMobs(level),
            encounter::clearRecoveryTracking,
            playerSessionAssociation::clearTransientState,
            () -> {
                objective.resetObjectiveRuntimeState();
                encounter.clearEliteTarget();
                encounter.resetRegenerativeState();
                encounter.resetMoltenState();
            },
            encounter::resetRemovalEvidence,
            () -> ActiveRoomProtectionEvents.syncProtectionRegistration(level, this),
            () -> runGateway.failRoom(reason),
            () -> runGateway.returnFailedFloorToHub(resolveHubReturnPlayer(level)),
            this::setChanged
        ));
    }

    void failActiveRoomForSpawnFailure(ServerLevel level, String reason) {
        if (lifecycle.snapshot().state() == RoomState.ACTIVE) {
            failRoom(level, reason);
        }
    }

    public boolean failRoomCommand(ServerLevel level) {
        RoomState state = lifecycle.snapshot().state();
        if (state != RoomState.ACTIVE && state != RoomState.RESETTING) {
            return false;
        }
        failRoom(level, "COMMAND_FAIL");
        return true;
    }
    public boolean completeRoom(ServerLevel level) {
        return completeRoom(level, null);
    }

    public boolean completeRoom(ServerLevel level, ServerPlayer fallbackRewardPlayer) {
        if (lifecycle.snapshot().state() == RoomState.COMPLETED && lifecycle.snapshot().completed()) {
            lifecycle.setActivationFailureReason("Room was already complete.");
            return false;
        }

        RoomRunGateway runGateway = roomRunGateway(level);
        if (runGateway.isPresetMode() && lifecycle.snapshot().state() != RoomState.ACTIVE) {
            String presetReason = runGateway.presetActivationBlockReason();
            if ("NONE".equals(presetReason)) {
                presetReason = "PRESET_ROOM_NOT_ACTIVE_FOR_COMPLETION";
            }
            playerSessionAssociation.recordRunActivationBlockReason(presetReason);
            lifecycle.setActivationFailureReason(activationFailureMessageForRunBlock(presetReason));
            return false;
        }

        clearTrackedMobs(level);
        complete(level, fallbackRewardPlayer);
        return true;
    }

    public boolean resetRoom(ServerLevel level) {
        lifecycle.reset(level.getGameTime(), new RoomLifecycle.AdministrativeResetActions(
            () -> clearAllPreviews(level),
            () -> openBarrier(level),
            () -> objective.clearRuneObjectiveState(level),
            () -> clearTrackedMobs(level),
            encounter::resetRemovalEvidence,
            () -> {
                objective.resetObjectiveRuntimeState();
                encounter.clearEliteTarget();
                encounter.resetSpawnDebugState();
                encounter.resetRegenerativeState();
                encounter.resetMoltenState();
            },
            () -> playerSessionAssociation.recordRunActivationBlockReason("NONE"),
            () -> ActiveRoomProtectionEvents.syncProtectionRegistration(level, this),
            playerSessionAssociation::clearTransientState,
            () -> markMarkerCacheDirty("ROOM_RESET"),
            this::setChanged
        ));
        return true;
    }

    public boolean cleanupRoom(ServerLevel level) {
        lifecycle.cleanup(level.getGameTime(), hasBoundsBox(), cleanupActions(level));
        return true;
    }

    private void normalizeAfterReload(ServerLevel level) {
        if (dungeonInstanceKey == null) {
            var repositories =
                com.mineclawd.tensuradungeon.runtime.GeneratedFloorRepository.all(level);
            var matchingKeys = repositories.stream()
                .map(com.mineclawd.tensuradungeon.runtime.GeneratedFloorRepository::hydrate)
                .filter(session -> session.placement() != null
                    && session.placement().controllers().contains(worldPosition))
                .map(session -> session.instanceKey() != null
                    ? session.instanceKey()
                    : repositories.size() == 1
                        ? DungeonInstanceAllocationData.get(level)
                            .currentIdentity(level.dimension().location().toString())
                            .orElse(null)
                        : null)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
            if (matchingKeys.size() == 1) {
                bindDungeonInstance(matchingKeys.getFirst());
            }
        }
        lifecycle.normalizeAfterReload(
            level.getGameTime(),
            hasBoundsBox(),
            cleanupActions(level),
            new RoomLifecycle.ReloadNormalizationActions(
                encounter::resetSpawnDebugState,
                encounter::resetSummonerState,
                this::setChanged
            )
        );
    }

    private RoomLifecycle.CleanupActions cleanupActions(ServerLevel level) {
        return new RoomLifecycle.CleanupActions(
            () -> clearAllPreviews(level),
            () -> openBarrier(level),
            () -> clearTrackedMobs(level),
            encounter::clearRecoveryTracking,
            playerSessionAssociation::clearTransientState,
            () -> {
                objective.resetObjectiveRuntimeState();
                encounter.clearEliteTarget();
                encounter.resetRegenerativeState();
                encounter.resetMoltenState();
            },
            () -> ActiveRoomProtectionEvents.syncProtectionRegistration(level, this),
            this::setChanged
        );
    }

    void clearTrackedMobs(ServerLevel level) {
        objective.clearRuneObjectiveState(level);
        encounter.clearTrackedMobs(level);
    }

    public static void recordGlobalMoltenDamageEventSeen(ServerLevel level) {
        for (BlockPos controllerPos : DungeonRunManager.get(level).getAllActiveRoomControllerPositions()) {
            if (level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller && controller.lifecycle.snapshot().state() == RoomState.ACTIVE && controller.isMoltenModifierActive(level)) {
                controller.recordMoltenDamageEventSeen();
            }
        }
    }

    public static void recordGlobalMoltenRejection(ServerLevel level, String reason) {
        for (BlockPos controllerPos : DungeonRunManager.get(level).getAllActiveRoomControllerPositions()) {
            if (level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller && controller.lifecycle.snapshot().state() == RoomState.ACTIVE && controller.isMoltenModifierActive(level)) {
                controller.recordMoltenRejection(reason);
            }
        }
    }

    public static RoomControllerBlockEntity findActiveControllerTracking(ServerLevel level, Entity entity) {
        if (entity == null) {
            return null;
        }
        for (BlockPos controllerPos : DungeonRunManager.get(level).getAllActiveRoomControllerPositions()) {
            if (level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller && controller.lifecycle.snapshot().state() == RoomState.ACTIVE && controller.encounter.trackedMobUuids.contains(entity.getUUID())) {
                return controller;
            }
        }
        return null;
    }

    public static boolean isPlayerInActiveEncounter(ServerLevel level, LivingEntity entity) {
        return findActiveControllerForPlayer(level, entity) != null;
    }

    public static boolean isPlayerGravityIncapacitated(
        ServerLevel level,
        LivingEntity entity
    ) {
        RoomControllerBlockEntity controller = findActiveControllerForPlayer(level, entity);
        return controller != null && controller.encounter.isGravityIncapacitated(entity.getUUID());
    }

    public static RoomControllerBlockEntity findActiveControllerForPlayer(
        ServerLevel level,
        LivingEntity entity
    ) {
        if (!(entity instanceof ServerPlayer player)) return null;
        for (BlockPos controllerPos : DungeonRunManager.get(level).getAllActiveRoomControllerPositions()) {
            if (level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller
                && controller.lifecycle.snapshot().state() == RoomState.ACTIVE
                && controller.playerSessionAssociation.validPlayers(level).stream()
                    .anyMatch(candidate -> candidate.getUUID().equals(player.getUUID()))) {
                return controller;
            }
        }
        return null;
    }

    public boolean isTrackedBoss(ServerLevel level, LivingEntity entity) {
        return entity instanceof Mob mob
            && encounter.trackedMobUuids.contains(entity.getUUID())
            && encounter.isBossType(mob);
    }

    public boolean isTrackedSummoned(ServerLevel level, LivingEntity entity) {
        return entity != null
            && encounter.trackedMobUuids.contains(entity.getUUID())
            && encounter.isTrackedSummoned(entity);
    }

    public boolean isValidDungeonAggroTarget(
        ServerLevel level,
        Mob mob,
        LivingEntity target
    ) {
        return encounter.isValidDungeonAggroTarget(level, mob, target);
    }

    public LivingEntity selectDungeonAggroTarget(ServerLevel level, Mob mob) {
        return encounter.selectDungeonAggroTarget(level, mob);
    }

    private boolean hasActiveModifier(ServerLevel level, DungeonModifierDefinition modifier) {
        return level != null && encounter.hasActiveModifier(level, modifier);
    }

    public boolean isModifierActive(ServerLevel level, DungeonModifierDefinition modifier) {
        return hasActiveModifier(level, modifier);
    }

    public boolean isModifierIdActive(ServerLevel level, String modifierId) {
        return level != null
            && modifierId != null
            && DungeonModifierCatalog.isRollable(modifierId)
            && DungeonRunManager.get(level).getActiveModifierIds(dungeonInstanceKey).contains(modifierId);
    }

    public boolean queueExplosiveDeath(ServerLevel level, LivingEntity entity) {
        boolean queued = encounter.queueExplosiveDeath(level, entity);
        if (queued) setChanged();
        return queued;
    }

    public boolean isMoltenModifierActive(ServerLevel level) {
        return encounter.isMoltenModifierActive(level);
    }

    public void recordMoltenDamageEventSeen() {
        applyEncounterEffect((ServerLevel) level, encounter.recordMoltenDamageEventSeen());
    }

    public void recordMoltenDamageEventWithLivingAttacker() {
        applyEncounterEffect((ServerLevel) level, encounter.recordMoltenDamageEventWithLivingAttacker());
    }

    public void recordMoltenTrackedTargetEvent() {
        applyEncounterEffect((ServerLevel) level, encounter.recordMoltenTrackedTargetEvent());
    }

    public void recordMoltenRejection(String reason) {
        applyEncounterEffect((ServerLevel) level, encounter.recordMoltenRejection(reason));
    }

    public void recordMoltenRetaliation(LivingEntity attacker) {
        applyEncounterEffect((ServerLevel) level, encounter.recordMoltenRetaliation(attacker));
    }

    public RoomState getRoomState() {
        return lifecycle.snapshot().state();
    }

    public boolean isBranchPreviewMode() {
        return lifecycle.snapshot().branchPreviewMode();
    }

    public void setBranchPreviewMode(boolean branchPreviewMode) {
        if (lifecycle.snapshot().branchPreviewMode() == branchPreviewMode) {
            return;
        }
        lifecycle.setBranchPreviewMode(branchPreviewMode);
        setChanged();
    }

    public boolean hasExplicitBarrierChannels() {
        return !markerState.barrierAnchor1Anchors.isEmpty()
            || !markerState.barrierAnchor2Anchors.isEmpty()
            || !markerState.barrierAnchor3Anchors.isEmpty()
            || !markerState.barrierAnchor4Anchors.isEmpty();
    }

    public List<BlockPos> getExitAnchors() {
        return List.copyOf(markerState.exitAnchors);
    }

    public boolean hasSpawnBossAnchor() {
        return !markerState.bossSpawnAnchors.isEmpty();
    }

    public String getProfileCompletionPolicy() {
        return isSafeRoomProfile() ? "SAFE_AUTO_COMPLETE" : "COMBAT_OBJECTIVE";
    }

    public int getFinalCalculatedRoomBudget(ServerLevel level) {
        return getRoomBudgetResult(level).getRoomTotalBudget();
    }

    public int getFinalCalculatedMaxAlive(ServerLevel level) {
        return getRoomBudgetResult(level).getMaxAlive();
    }

    public void setExplicitRoomBounds(RoomBounds bounds, BlockPos anchorA, BlockPos anchorB) {
        if (bounds == null) {
            return;
        }
        boolean changed = markerState.explicitRoomBounds == null
            || !markerState.explicitRoomBounds.min().equals(bounds.min())
            || !markerState.explicitRoomBounds.max().equals(bounds.max())
            || !java.util.Objects.equals(markerState.explicitBoundsAnchorA, anchorA)
            || !java.util.Objects.equals(markerState.explicitBoundsAnchorB, anchorB);
        markerState.explicitRoomBounds = new RoomBounds(bounds.min().immutable(), bounds.max().immutable());
        markerState.explicitBoundsAnchorA = anchorA == null ? null : anchorA.immutable();
        markerState.explicitBoundsAnchorB = anchorB == null ? null : anchorB.immutable();
        if (changed) {
            markMarkerCacheDirty("EXPLICIT_ROOM_BOUNDS_UPDATED");
            setChanged();
        }
    }

    public GeneratedRoomBoundsWriter.Result writeGeneratedRoomBounds(ServerLevel level, RoomBounds bounds) {
        return GeneratedRoomBoundsWriter.writeGeneratedBounds(level, this, bounds);
    }

    public boolean hasBoundsBox() {
        return markerState.explicitRoomBounds != null || markerState.boundsAnchors.size() >= 2;
    }

    public ClaimBoundsResolution getLastClaimBoundsResolution() {
        return markerState.lastClaimBoundsResolution;
    }

    private BoundsMode boundsMode() {
        if (markerState.explicitRoomBounds != null) {
            return BoundsMode.EXPLICIT_BOX;
        }
        return hasBoundsBox() ? BoundsMode.ANCHOR_BOX : BoundsMode.RADIUS;
    }

    private RoomBounds roomBounds() {
        if (markerState.explicitRoomBounds != null) {
            return markerState.explicitRoomBounds;
        }
        if (markerState.boundsAnchors.size() < 2) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : markerState.boundsAnchors) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new RoomBounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    public boolean isInsideBounds(BlockPos pos) {
        RoomBounds bounds = roomBounds();
        return bounds != null && bounds.contains(pos);
    }
    public RoomProtectionView protectionView(ServerLevel level) {
        RoomLifecycle.Snapshot lifecycleState = lifecycle.snapshot();
        RoomBounds bounds = roomBounds();
        boolean ruleZoneActive = lifecycleState.state() == RoomState.ACTIVE && !lifecycleState.completed() && bounds != null;
        boolean graceActive = lifecycle.isProtectionGraceActive(level.getGameTime(), bounds != null);
        return new RoomProtectionView(
            worldPosition,
            lifecycleState.state(),
            bounds,
            ruleZoneActive,
            graceActive,
            graceActive ? lifecycle.protectionGraceTicksRemaining(level.getGameTime()) : 0L,
            bounds == null ? "NONE" : boundsMode().name()
        );
    }

    public RoomDebugSnapshot roomDebugSnapshot(ServerLevel level) {
        RoomLifecycle.Snapshot lifecycleState = lifecycle.snapshot();
        RoomProfile debugProfile = lifecycleState.profile();
        RoomBudgetResult debugBudget = getRoomBudgetResult(level);
        Entity debugElite = encounter.eliteMobUuid == null ? null : level.getEntity(encounter.eliteMobUuid);
        String runePositionsSummary = objective.selectedRuneAnchorPositions.isEmpty()
            ? "NONE"
            : objective.selectedRuneAnchorPositions.stream().map(RoomControllerBlockEntity::formatPos).collect(java.util.stream.Collectors.joining(", "));
        String runeUuidsSummary = objective.runeCrystalUuids.isEmpty()
            ? "NONE"
            : objective.runeCrystalUuids.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(", "));
        return new RoomDebugSnapshot(
            new RoomDebugSnapshot.Core(
                getRoomState(),
                level.getDifficulty(),
                getActivationRadius(),
                !hasBoundsBox() ? "RADIUS" : isUsingActivationInset() ? "INSET_BOX" : "BOUNDS_FALLBACK",
                !hasBoundsBox()
                    ? "N/A"
                    : isUsingActivationInset()
                        ? ACTIVATION_BOUNDS_INSET + " blocks X/Z"
                        : "Fallback to bounds; room too thin for " + ACTIVATION_BOUNDS_INSET + "-block X/Z inset.",
                isActivationBlocked(level),
                getActivationBlockReason(level),
                requiresActiveRun(),
                roomRunGateway(level).isRoomRegisteredActive(),
                getRunActivationBlockReason(level),
                debugProfile,
                debugBudget,
                debugProfile.getBudgetMultiplier(),
                debugProfile.getSuggestedMaxAlive(),
                debugBudget.getMaxAlive(),
                debugProfile.getSuggestedMaxAlive(),
                getProfileInitialSpawnCount(),
                DungeonRunManager.get(level).getFloorNumber(dungeonInstanceKey),
                getFinalCalculatedRoomBudget(level),
                getFinalCalculatedMaxAlive(level),
                doesProfileAllowCombat(),
                debugProfile.allowsObjective() && debugProfile != RoomProfile.SAFE_ROOM,
                debugProfile.getNotes(),
                getProfileCompletionPolicy(),
                debugProfile == RoomProfile.LARGE_COMBAT || debugProfile == RoomProfile.ARENA_WAVE,
                hasSpawnBossAnchor(),
                0,
                "NONE".equals(objective.autoObjectiveRejectionSummary) ? "NONE" : objective.autoObjectiveRejectionSummary,
                lifecycleState.lastCompletionReason(),
                lifecycleState.state() == RoomState.ACTIVE && !lifecycleState.completed() && hasBoundsBox(),
                lifecycle.isProtectionGraceActive(level.getGameTime(), hasBoundsBox()),
                lifecycle.isProtectionGraceActive(level.getGameTime(), hasBoundsBox())
                    ? lifecycle.protectionGraceTicksRemaining(level.getGameTime()) : 0L,
                hasBoundsBox() ? boundsMode().name() : "NONE",
                getLastActivationFailureReason(),
                playerSessionAssociation.associatedPlayerCount(),
                getNearbyActiveRoomCount(level),
                lifecycleState.completed()
            ),
            new RoomDebugSnapshot.Objective(
                getConfiguredObjectiveType(),
                objective.activeObjectiveType,
                objective.objectiveActive,
                objective.objectiveTimerTicks,
                objective.objectiveTimerMaxTicks(),
                getObjectiveSelectionMode(),
                getForcedObjectiveOverrideSummary(),
                objective.autoObjectiveSelected,
                objective.autoObjectiveCandidateList,
                getAutoObjectiveRejectionSummary(),
                objective.autoObjectiveRollValue,
                objective.objectiveCompleted,
                objective.lastObjectiveResult,
                objective.lastObjectiveRejectionReason,
                clearAllRemainingMobCount(),
                encounter.eliteMobUuid == null ? "NONE" : encounter.eliteMobUuid.toString(),
                debugElite != null,
                debugElite != null && debugElite.isAlive(),
                encounter.eliteDefeated,
                encounter.eliteMobUuid == null ? "NONE" : encounter.eliteMobUuid.toString(),
                objective.objectiveTargetEntityType,
                debugElite != null && debugElite.isAlive(),
                objective.activeObjectiveType.usesEliteTarget() && encounter.eliteMobUuid != null && !encounter.eliteDefeated ? 1 : 0,
                objective.objectiveDurationTicks,
                objective.objectiveRemainingTicks,
                objective.objectiveTimerTicks,
                objective.objectiveActive && objective.activeObjectiveType.usesTimer(),
                objective.objectivePaused,
                objective.objectivePlayersInBounds,
                objective.objectiveActive && objective.activeObjectiveType.usesRuneTargets(),
                objective.runeRequiredCount,
                objective.runeActivatedCount,
                Math.max(0, objective.runeRequiredCount - objective.runeActivatedCount),
                runePositionsSummary,
                runeUuidsSummary,
                objective.getRuneActivationRadius(),
                objective.getRunePlayersInRange(level),
                objective.getRemainingRuneCrystalCount(level)
            ),
            new RoomDebugSnapshot.Encounter(
                encounter.getLastSpawnStrategy(),
                encounter.getLastSpawnModifierSummary(),
                encounter.getPendingEnemyCount(),
                encounter.getWaveSize(),
                encounter.getWavesSpawned(),
                encounter.getWaveCooldownTicks(),
                encounter.getWaveSummary(),
                encounter.getLastSpawnArmorValue(),
                encounter.getLastSpawnArmorToughnessValue(),
                hasActiveModifier(level, DungeonModifierDefinition.MOLTEN) ? DungeonModifierDefinition.MOLTEN.getDisplayName() : "NONE",
                encounter.lastMoltenRetaliationTick < 0 ? "NONE" : Integer.toString(encounter.lastMoltenRetaliationTick),
                encounter.lastMoltenRetaliationTarget,
                encounter.totalMoltenRetaliations,
                encounter.moltenDamageEventsSeen,
                encounter.moltenDamageEventsWithLivingAttacker,
                encounter.moltenDamageEventsWithTrackedTarget,
                encounter.lastMoltenRejectionReason,
                hasActiveModifier(level, DungeonModifierDefinition.REGENERATIVE) ? DungeonModifierDefinition.REGENERATIVE.getDisplayName() : "NONE",
                encounter.regenerativeIntervalTicks(),
                encounter.lastRegenPulseTick < 0 ? "NONE" : Integer.toString(encounter.lastRegenPulseTick),
                encounter.lastRegenPulseHealedCount,
                encounter.lastRegenPulseTotalHealthRestored,
                hasActiveModifier(level, DungeonModifierDefinition.SUMMONER) ? DungeonModifierDefinition.SUMMONER.getDisplayName() : "NONE",
                encounter.summonerIntervalTicks(),
                encounter.summonerMobUuids.size(),
                encounter.countAliveMembers(level, encounter.summonerMobUuids),
                encounter.countAliveMembers(level, encounter.summonedMobUuids),
                encounter.totalSummonsThisRoom,
                encounter.lastSummonPulseTick < 0 ? "NONE" : Integer.toString(encounter.lastSummonPulseTick),
                encounter.lastSummonResult,
                encounter.lastSummonRejectionReason,
                encounter.lastSummonerEntityType,
                encounter.lastSummonedEntityType,
                encounter.enemyPoolRegistryEnabled,
                getLastEnemyPoolSpawnSource(),
                encounter.lastEnemyPoolRoleRequested,
                getLastEnemyPoolEntitySelected(),
                encounter.lastEnemyPoolFamily,
                encounter.lastEnemyPoolSourceMod,
                getLastEnemyPoolFallbackReason(),
                encounter.lastEnemyPoolCandidateCount,
                encounter.lastEnemyPoolError,
                encounter.getCurrentAliveTrackedMobCount(level),
                encounter.trackedMobUuids.size(),
                encounter.getLastSpawnRequestedCount(),
                encounter.getLastSpawnActualCount(),
                encounter.getLastSpawnSkippedReason(),
                encounter.getLastSpawnMeleeUsedCount(),
                encounter.getLastSpawnRangedUsedCount(),
                encounter.getLastSpawnFlankUsedCount(),
                encounter.getLastSpawnHighgroundUsedCount(),
                encounter.getLastSpawnGenericUsedCount(),
                encounter.missingMobTicks.size(),
                encounter.staleTrackedMobCount(),
                encounter.mobResetCooldownTicks.size()
            ),
            new RoomDebugSnapshot.Barrier(
                barrierController.lastBarrierMaterialSummary,
                barrierController.lastBarrierSolveMode,
                barrierController.lastBarrierSolveResult,
                barrierController.lastBarrierSolveReason,
                barrierController.lastBarrierPlaneSummary,
                barrierController.lastBarrierVolumeBoundsSummary,
                barrierController.lastBarrierVolumeSizeSummary,
                barrierController.lastBarrierFillCandidateCount,
                barrierController.lastBarrierSolvedWidth,
                barrierController.lastBarrierSolvedHeight,
                barrierController.lastBarrierSolvedPositionCount,
                barrierController.lastBarrierPlacedBlockCount,
                barrierController.lastBarrierSkippedBlockCount,
                barrierController.lastBarrierClearedBlockCount,
                barrierController.getLastBarrierDebugLines(),
                barrierController.getPlacedBarrierBlockCount(),
                runtimeBarrierPositionCount(level),
                barrierController.getBarrierPreviewBlockCount() + markerState.boundsPreviewPositions.size(),
                barrierController.getBarrierPreviewBlockCount()
            ),
            roomMarkerDebugSnapshot(level)
        );
    }

    public RoomDebugSnapshot.Markers roomMarkerDebugSnapshot(ServerLevel level) {
        Map<Direction, Integer> barrierFacingCounts = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            barrierFacingCounts.put(direction, 0);
        }
        for (BlockPos anchor : markerState.barrierAnchors) {
            Direction facing = barrierController.getBarrierFacing(level, anchor);
            barrierFacingCounts.put(facing, barrierFacingCounts.get(facing) + 1);
        }
        return new RoomDebugSnapshot.Markers(
            boundsMode(),
            roomBounds(),
            getActivationBounds(),
            markerState.spawnAnchors,
            markerState.rangedSpawnAnchors,
            markerState.flankSpawnAnchors,
            markerState.highgroundSpawnAnchors,
            markerState.eliteSpawnAnchors,
            markerState.bossSpawnAnchors,
            markerState.barrierAnchors,
            markerState.barrierAnchor1Anchors,
            markerState.barrierAnchor2Anchors,
            markerState.barrierAnchor3Anchors,
            markerState.barrierAnchor4Anchors,
            markerState.mobResetAnchors,
            markerState.exitAnchors,
            markerState.boundsAnchors,
            markerState.objectiveAnchors,
            markerState.interactAnchors,
            markerState.rawSpawnAnchorCount,
            markerState.rawRangedSpawnAnchorCount,
            markerState.rawFlankSpawnAnchorCount,
            markerState.rawHighgroundSpawnAnchorCount,
            markerState.rawEliteSpawnAnchorCount,
            markerState.rawBossSpawnAnchorCount,
            markerState.rawBarrierAnchorCount,
            markerState.rawMobResetAnchorCount,
            markerState.rawExitAnchorCount,
            markerState.rawBoundsAnchorCount,
            markerState.rawObjectiveAnchorCount,
            markerState.rawInteractAnchorCount,
            markerState.markerCacheReady,
            markerState.markerCacheDirty,
            markerState.markerCacheDirtyReason,
            markerState.markerScanCount,
            markerState.lastMarkerScanReason,
            markerState.lastMarkerScanGameTime,
            markerState.lastMarkerScanDurationNanos < 0L ? -1.0D : markerState.lastMarkerScanDurationNanos / 1_000_000.0D,
            markerState.lastMarkerScanPerformanceWarning,
            markerState.markerOwnershipMode,
            markerState.claimedMarkerMode,
            markerState.lastMarkerClaimRadius,
            markerState.lastMarkerClaimTotal,
            markerState.lastMarkerClaimSkippedOwnedByOther,
            markerState.lastMarkerClaimStaleRemoved,
            markerState.lastMarkerClaimSummary,
            markerState.lastMarkerClaimReason,
            markerState.lastMarkerClaimDurationNanos < 0L ? -1.0D : markerState.lastMarkerClaimDurationNanos / 1_000_000.0D,
            markerState.boundsPreviewPositions.size(),
            barrierFacingCounts
        );
    }

    private RoomBounds getActivationBounds() {
        RoomBounds bounds = roomBounds();
        if (bounds == null) {
            return null;
        }
        if (!canUseActivationInset(bounds)) {
            return bounds;
        }
        return bounds.insetHorizontal(ACTIVATION_BOUNDS_INSET);
    }

    private boolean isUsingActivationInset() {
        RoomBounds bounds = roomBounds();
        return bounds != null && canUseActivationInset(bounds);
    }

    private boolean canUseActivationInset(RoomBounds bounds) {
        return bounds.getWidth() > ACTIVATION_BOUNDS_INSET * 2 && bounds.getDepth() > ACTIVATION_BOUNDS_INSET * 2;
    }

    void setLastCompletionReason(String reason) {
        lifecycle.setCompletionReason(reason);
    }

    private int runtimeBarrierPositionCount(ServerLevel level) {
        return barrierController.getRuntimeBarrierPositionCount(level, barrierInputs());
    }

    private int getActivationRadius() {
        return PlayerSessionAssociation.ACTIVATION_RADIUS;
    }

    public String getLastActivationFailureReason() {
        return lifecycle.snapshot().lastActivationFailureReason();
    }

    void recordActivationFailure(String reason) {
        lifecycle.setActivationFailureReason(reason);
    }

    public RoomObjectiveType getConfiguredObjectiveType() {
        return objective.configuredObjectiveType;
    }

    public String getObjectiveSelectionMode() {
        return objective.objectiveSelectionMode;
    }

    public String getAutoObjectiveRejectionSummary() {
        return objective.autoObjectiveRejectionSummary;
    }

    public String getForcedObjectiveOverrideSummary() {
        return objective.objectiveDisabled
            ? "NONE"
            : objective.objectiveOverrideEnabled ? objective.configuredObjectiveType.name() : "AUTO";
    }

    private int clearAllRemainingMobCount() {
        return encounter.trackedMobUuids.size() + Math.max(0, encounter.encounterTargetCount - encounter.lastSpawnActualCount);
    }

    public double getNearestRuneDistance(ServerPlayer player, ServerLevel level) {
        return objective.getNearestRuneDistance(player, level);
    }

    public boolean isTrackedRuneCrystal(Entity entity) {
        return objective.isTrackedRuneCrystal(entity);
    }

    public RoomProfile getRoomProfile() {
        return lifecycle.snapshot().profile();
    }

    @Nullable
    public DungeonInstanceAllocationData.InstanceKey getDungeonInstanceKey() {
        return dungeonInstanceKey;
    }

    public LivingEntity selectDungeonSkillTarget(
        ServerLevel level,
        LivingEntity subordinate
    ) {
        return encounter.selectDungeonSkillTarget(level, subordinate);
    }

    public boolean bindDungeonInstance(DungeonInstanceAllocationData.InstanceKey instanceKey) {
        if (instanceKey == null) {
            return false;
        }
        if (instanceKey.equals(dungeonInstanceKey)) {
            return true;
        }
        if (dungeonInstanceKey != null
            && !dungeonInstanceKey.equals(instanceKey)
            && lifecycle.snapshot().state() != RoomState.INACTIVE) {
            return false;
        }
        dungeonInstanceKey = instanceKey;
        setChanged();
        return true;
    }

    public boolean matchesDungeonInstance(DungeonInstanceAllocationData.InstanceKey instanceKey) {
        return instanceKey != null && instanceKey.equals(dungeonInstanceKey);
    }

    public void setRoomProfile(RoomProfile profile) {
        lifecycle.setProfile(profile);
        setChanged();
    }

    private int getProfileInitialSpawnCount() {
        return lifecycle.snapshot().profile().getInitialSpawnCount();
    }

    public RoomBudgetResult getRoomBudgetResult(ServerLevel level) {
        DungeonRunManager manager = level == null ? null : DungeonRunManager.get(level);
        int floorNumber = manager == null ? 1 : manager.getFloorNumber(dungeonInstanceKey);
        return DungeonBudgetEngine.calculate(
            floorNumber,
            lifecycle.snapshot().profile(),
            manager == null ? java.util.Set.of() : manager.getActiveModifiers(dungeonInstanceKey)
        );
    }

    public boolean doesProfileAllowCombat() {
        RoomProfile profile = lifecycle.snapshot().profile();
        return profile.allowsCombat() && profile != RoomProfile.SAFE_ROOM;
    }

    private boolean isSafeRoomProfile() {
        return lifecycle.snapshot().profile() == RoomProfile.SAFE_ROOM;
    }

    public void setConfiguredObjectiveType(RoomObjectiveType objectiveType) {
        objective.configure(objectiveType);
        encounter.clearEliteTarget();
        encounter.resetSpawnDebugState();
        playerSessionAssociation.recordRunActivationBlockReason("NONE");
        lifecycle.clearCompletionContext();
        setChanged();
    }

    public void setObjectiveDisabled(boolean disabled) {
        objective.setDisabled(disabled);
        encounter.clearEliteTarget();
        encounter.resetSpawnDebugState();
        playerSessionAssociation.recordRunActivationBlockReason("NONE");
        lifecycle.clearCompletionContext();
        setChanged();
    }

    public RoomHudSnapshot roomHudSnapshot(ServerLevel level) {
        RoomObjectiveType objectiveType = objective.activeObjectiveType;
        int objectiveTarget = switch (objectiveType) {
            case CLEAR_ALL -> encounter.encounterTargetCount;
            case KILL_ELITE -> 1;
            case SURVIVE_TIMER -> objective.objectiveDurationTicks;
            case ACTIVATE_RUNES -> objective.runeRequiredCount;
        };
        int objectiveProgress = switch (objectiveType) {
            case CLEAR_ALL -> Math.max(0, objectiveTarget - clearAllRemainingMobCount());
            case KILL_ELITE -> encounter.eliteDefeated ? 1 : 0;
            case SURVIVE_TIMER -> objective.objectiveTimerTicks;
            case ACTIVATE_RUNES -> objective.runeActivatedCount;
        };
        int enemiesSpawned = encounter.lastSpawnActualCount
            + (objectiveType.usesEliteTarget() && encounter.eliteMobUuid != null ? 1 : 0);
        return new RoomHudSnapshot(
            objective.objectiveActive,
            lifecycle.snapshot().state(),
            objectiveType,
            objectiveProgress,
            objectiveTarget,
            objective.objectiveRemainingTicks,
            objective.objectiveDurationTicks,
            encounter.getCurrentAliveTrackedMobCount(level),
            enemiesSpawned,
            encounter.lastSpawnModifierSummary,
            encounter.lastCompositionSpent,
            encounter.lastCompositionBudget,
            encounter.lastCompositionSummary,
            encounter.getPendingEnemyCount(),
            encounter.getWaveSize(),
            encounter.getWavesSpawned(),
            encounter.getWaveCooldownTicks(),
            encounter.getWaveSummary(),
            encounter.modifierRolePresentationSummary(level)
        );
    }

    private List<List<BlockPos>> getExplicitBarrierChannelLists() {
        return List.of(markerState.barrierAnchor1Anchors, markerState.barrierAnchor2Anchors, markerState.barrierAnchor3Anchors, markerState.barrierAnchor4Anchors);
    }

    int getOwnedNormalCombatAnchorCount() {
        return markerState.spawnAnchors.size() + markerState.rangedSpawnAnchors.size() + markerState.flankSpawnAnchors.size() + markerState.highgroundSpawnAnchors.size();
    }

    public String getLastEnemyPoolSpawnSource() {
        return encounter.lastEnemyPoolSpawnSource;
    }

    public String getLastEnemyPoolEntitySelected() {
        return encounter.lastEnemyPoolEntitySelected;
    }

    public String getLastEnemyPoolFallbackReason() {
        return encounter.lastEnemyPoolFallbackReason;
    }

    public ValidationReport validate(ServerLevel level, BlockPos playerPos) {
        ensureMarkerCache(level, "VALIDATE");
        List<String> lines = new ArrayList<>();
        int failCount = 0;
        int warnCount = 0;
        RoomProfile profile = lifecycle.snapshot().profile();

        lines.add("INFO: Room profile is " + profile + ".");
        lines.add("INFO: Profile budget multiplier: " + profile.getBudgetMultiplier());
        lines.add("INFO: Profile suggested max alive: " + profile.getSuggestedMaxAlive());
        lines.add("INFO: Profile allows combat: " + profile.allowsCombat());
        lines.add("INFO: Profile allows objective: " + profile.allowsObjective());
        lines.add("INFO: Objective type is " + objective.configuredObjectiveType + ".");
        if (doesProfileAllowCombat() && getOwnedNormalCombatAnchorCount() <= 0) {
            lines.add("FAIL: Combat room profile has no owned combat spawn anchors.");
            failCount++;
        } else if (getOwnedNormalCombatAnchorCount() > 0) {
            lines.add("PASS: Combat spawn anchors found: " + getOwnedNormalCombatAnchorCount());
        } else {
            lines.add("PASS: Non-combat room profile does not require combat spawn anchors.");
        }
        if (!profile.allowsCombat() && !markerState.spawnAnchors.isEmpty()) { lines.add("WARN: Non-combat room profile has owned spawn anchors."); warnCount++; }
        if (markerState.barrierAnchors.isEmpty() && !hasExplicitBarrierChannels()) { lines.add("FAIL: No owned barrier anchors found."); failCount++; } else if (hasExplicitBarrierChannels()) { lines.add("PASS: Explicit barrier channels found."); } else { lines.add("PASS: Barrier anchors found: " + markerState.barrierAnchors.size()); }
        lines.add("INFO: Barrier anchors raw/owned: " + markerState.rawBarrierAnchorCount + " / " + markerState.barrierAnchors.size());
        lines.add("INFO: Barrier Channel 1 anchors raw/owned: " + markerState.rawBarrierAnchor1Count + " / " + markerState.barrierAnchor1Anchors.size());
        lines.add("INFO: Barrier Channel 2 anchors raw/owned: " + markerState.rawBarrierAnchor2Count + " / " + markerState.barrierAnchor2Anchors.size());
        lines.add("INFO: Barrier Channel 3 anchors raw/owned: " + markerState.rawBarrierAnchor3Count + " / " + markerState.barrierAnchor3Anchors.size());
        lines.add("INFO: Barrier Channel 4 anchors raw/owned: " + markerState.rawBarrierAnchor4Count + " / " + markerState.barrierAnchor4Anchors.size());
        if (markerState.rawBarrierAnchorCount > markerState.barrierAnchors.size()) { lines.add("WARN: Nearby unowned barrier_anchor markers exist and are excluded from barrier pairing."); warnCount++; }
        if (hasExplicitBarrierChannels() && !markerState.barrierAnchors.isEmpty()) { lines.add("INFO: Numbered barrier anchors are present; generic barrier_anchor will be ignored for solving."); }
        lines.add("INFO: Marker ownership mode: " + markerState.markerOwnershipMode);
        lines.add("INFO: Claimed marker summary: " + markerState.lastMarkerClaimSummary);
        if (markerState.claimedMarkerMode) {
            lines.add("PASS: Controller is using claimed marker cache.");
            if (markerState.lastMarkerClaimStaleRemoved > 0) {
                lines.add("WARN: Claimed marker cache pruned stale marker positions: " + markerState.lastMarkerClaimStaleRemoved);
                warnCount++;
            }
        } else {
            lines.add("WARN: Marker ownership is using legacy radius fallback.");
            warnCount++;
        }
        if (markerState.mobResetAnchors.isEmpty()) { lines.add("FAIL: No mob_reset_anchor markers found."); failCount++; } else { lines.add("PASS: Mob reset anchors found: " + markerState.mobResetAnchors.size()); }
        if (markerState.exitAnchors.isEmpty()) { lines.add("FAIL: No exit_anchor markers found."); failCount++; } else { lines.add("PASS: Exit anchors found: " + markerState.exitAnchors.size()); }
        lines.add("INFO: Objective anchors raw/owned: " + markerState.rawObjectiveAnchorCount + " / " + markerState.objectiveAnchors.size());
        lines.add("INFO: Interact anchors raw/owned: " + markerState.rawInteractAnchorCount + " / " + markerState.interactAnchors.size());
        lines.add("INFO: Melee spawn anchors raw/owned: " + markerState.rawSpawnAnchorCount + " / " + markerState.spawnAnchors.size());
        lines.add("INFO: Ranged spawn anchors raw/owned: " + markerState.rawRangedSpawnAnchorCount + " / " + markerState.rangedSpawnAnchors.size());
        lines.add("INFO: Flank spawn anchors raw/owned: " + markerState.rawFlankSpawnAnchorCount + " / " + markerState.flankSpawnAnchors.size());
        lines.add("INFO: Highground spawn anchors raw/owned: " + markerState.rawHighgroundSpawnAnchorCount + " / " + markerState.highgroundSpawnAnchors.size());
        lines.add("INFO: Elite spawn anchors raw/owned: " + markerState.rawEliteSpawnAnchorCount + " / " + markerState.eliteSpawnAnchors.size());
        if (objective.configuredObjectiveType.requiresObjectiveAnchor() && markerState.objectiveAnchors.isEmpty()) { lines.add("WARN: Objective requires owned objective_anchor but none were found."); warnCount++; }
        if (!objective.configuredObjectiveType.requiresObjectiveAnchor() && !markerState.objectiveAnchors.isEmpty()) { lines.add("INFO: objective_anchor markers are present but unused by the current objective."); }
        if (!markerState.interactAnchors.isEmpty()) { lines.add("INFO: interact_anchor markers are present for future interaction-style objectives but unused by the current objective."); }
        if (profile == RoomProfile.SAFE_ROOM && (!markerState.spawnAnchors.isEmpty() || !markerState.eliteSpawnAnchors.isEmpty() || objective.configuredObjectiveType != RoomObjectiveType.CLEAR_ALL)) { lines.add("WARN: SAFE_ROOM profile should not be configured as a combat encounter."); warnCount++; }
        if (profile.allowsCombat() && getOwnedNormalCombatAnchorCount() < profile.getInitialSpawnCount()) { lines.add("WARN: Profile requests more mobs than owned spawn anchors; anchors may be reused."); warnCount++; }
        if (profile == RoomProfile.CORRIDOR_AMBUSH && markerState.flankSpawnAnchors.isEmpty()) { lines.add("WARN: CORRIDOR_AMBUSH has no owned spawn_flank anchors."); warnCount++; }
        if (profile == RoomProfile.LARGE_COMBAT && getOwnedNormalCombatAnchorCount() <= 1) { lines.add("WARN: LARGE_COMBAT has only one owned normal combat anchor."); warnCount++; }
        if (profile == RoomProfile.ARENA_WAVE && getOwnedNormalCombatAnchorCount() < 3) { lines.add("WARN: ARENA_WAVE has fewer than three owned normal combat anchors."); warnCount++; }
        if (!objective.configuredObjectiveType.usesEliteTarget() && !markerState.eliteSpawnAnchors.isEmpty()) { lines.add("INFO: spawn_elite markers are present but unused by the current objective."); }
        if (markerState.boundsAnchors.size() == 1) { lines.add("WARN: Exactly one bounds_anchor found. Bounds require zero or at least two anchors."); warnCount++; }
        if (!hasBoundsBox()) { lines.add("WARN: Activation is currently using radius mode instead of bounds."); warnCount++; }
        if (hasBoundsBox()) { lines.add("INFO: Teleport/dimension escape protection is not enabled in Stage 1.5."); }

        RoomBounds bounds = roomBounds();
        if (bounds != null) {
            lines.add("PASS: Bounds dimensions are " + bounds.getWidth() + " x " + bounds.getHeight() + " x " + bounds.getDepth());
            if (canUseActivationInset(bounds)) {
                RoomBounds activationBounds = getActivationBounds();
                lines.add("PASS: Activation inset is " + ACTIVATION_BOUNDS_INSET + " blocks X/Z.");
                lines.add("PASS: Activation area dimensions are " + activationBounds.getWidth() + " x " + activationBounds.getHeight() + " x " + activationBounds.getDepth());
            } else {
                lines.add("WARN: Bounds box is too thin for a " + ACTIVATION_BOUNDS_INSET + "-block X/Z activation inset. Automatic activation falls back to full bounds.");
                warnCount++;
            }
        }

        if (countFallbackBarrierFacing(level) > 0) { lines.add("WARN: One or more barrier anchors are using facing fallback."); warnCount++; }
        if (runtimeBarrierPositionCount(level) == 0) { lines.add("WARN: Runtime barrier calculation produced zero positions."); warnCount++; }

        BarrierSolvePlan barrierPlan = barrierController.solveBarrierPlan(level, barrierInputs());
        if (!hasExplicitBarrierChannels() && markerState.barrierAnchors.size() > 1 && markerState.barrierAnchors.size() % 2 != 0) { lines.add("WARN: Barrier solve has odd owned barrier_anchor count: " + markerState.barrierAnchors.size() + " (UNPAIRED_BARRIER_ANCHOR_COUNT)"); warnCount++; }
        for (int channelIndex = 0; channelIndex < getExplicitBarrierChannelLists().size(); channelIndex++) {
            List<BlockPos> channel = getExplicitBarrierChannelLists().get(channelIndex);
            if (channel.size() == 1) { lines.add("WARN: Barrier Channel " + (channelIndex + 1) + " is unpaired."); warnCount++; }
            if (channel.size() > 2) { lines.add("WARN: Barrier Channel " + (channelIndex + 1) + " is ambiguous with " + channel.size() + " anchors."); warnCount++; }
        }
        if (!barrierPlan.unpairedAnchors().isEmpty()) { lines.add("WARN: Barrier solve left unpaired owned anchors: " + barrierPlan.unpairedAnchors().stream().map(anchor -> formatPos(anchor)).toList()); warnCount++; }
        for (BarrierPlaneSolveResult rejected : barrierPlan.rejectedPairs()) {
            lines.add("WARN: Rejected barrier pair " + formatPos(rejected.anchorA()) + " -> " + formatPos(rejected.anchorB()) + " because " + rejected.reason() + " (" + rejected.boundsRelation() + ")");
            warnCount++;
        }
        for (BarrierPlaneSolveResult solved : barrierPlan.planes()) {
            if (!solved.ownedAnchors()) { lines.add("WARN: Barrier pair uses unowned anchors: " + formatPos(solved.anchorA()) + " -> " + formatPos(solved.anchorB())); warnCount++; }
            if ("OUTSIDE_ROOM_BOUNDS_REJECTED".equals(solved.boundsRelation())) { lines.add("WARN: Barrier pair crosses room bounds: " + solved.volumeBoundsSummary()); warnCount++; }
            if ("EDGE_TOLERANCE_1".equals(solved.boundsRelation())) { lines.add("INFO: Barrier pair uses 1-block edge tolerance: " + solved.volumeBoundsSummary()); }
        }

        if (bounds != null) {
            if (bounds.getWidth() < 3) { lines.add("WARN: Bounds box is very thin on X: " + bounds.getWidth()); warnCount++; }
            if (bounds.getHeight() < 3) { lines.add("WARN: Bounds box is very thin on Y: " + bounds.getHeight()); warnCount++; }
            if (bounds.getDepth() < 3) { lines.add("WARN: Bounds box is very thin on Z: " + bounds.getDepth()); warnCount++; }
            for (BlockPos spawn : markerState.rawSpawnAnchorPositions) {
                if (!bounds.contains(spawn)) {
                    if (markerState.spawnAnchors.isEmpty()) { lines.add("WARN: Spawn anchor outside bounds at " + formatPos(spawn)); warnCount++; }
                    else { lines.add("INFO: Nearby unowned spawn_anchor outside bounds at " + formatPos(spawn)); }
                }
            }
            for (BlockPos ranged : markerState.rawRangedSpawnAnchorPositions) {
                if (!bounds.contains(ranged)) {
                    if (markerState.rangedSpawnAnchors.isEmpty()) { lines.add("WARN: Ranged spawn anchor outside bounds at " + formatPos(ranged)); warnCount++; }
                    else { lines.add("INFO: Nearby unowned spawn_ranged outside bounds at " + formatPos(ranged)); }
                }
            }
            for (BlockPos flank : markerState.rawFlankSpawnAnchorPositions) {
                if (!bounds.contains(flank)) {
                    if (markerState.flankSpawnAnchors.isEmpty()) { lines.add("WARN: Flank spawn anchor outside bounds at " + formatPos(flank)); warnCount++; }
                    else { lines.add("INFO: Nearby unowned spawn_flank outside bounds at " + formatPos(flank)); }
                }
            }
            for (BlockPos highground : markerState.rawHighgroundSpawnAnchorPositions) {
                if (!bounds.contains(highground)) {
                    if (markerState.highgroundSpawnAnchors.isEmpty()) { lines.add("WARN: Highground spawn anchor outside bounds at " + formatPos(highground)); warnCount++; }
                    else { lines.add("INFO: Nearby unowned spawn_highground outside bounds at " + formatPos(highground)); }
                }
            }
            for (BlockPos elite : markerState.rawEliteSpawnAnchorPositions) {
                if (!bounds.contains(elite)) {
                    if (markerState.eliteSpawnAnchors.isEmpty()) { lines.add("WARN: Elite spawn anchor outside bounds at " + formatPos(elite)); warnCount++; }
                    else { lines.add("INFO: Nearby unowned spawn_elite outside bounds at " + formatPos(elite)); }
                }
            }
            for (BlockPos barrier : markerState.rawBarrierAnchorPositions) {
                if (!bounds.contains(barrier)) {
                    if (markerState.barrierAnchors.isEmpty()) { lines.add("WARN: Barrier anchor outside bounds at " + formatPos(barrier)); warnCount++; }
                    else { lines.add("INFO: Nearby unowned barrier_anchor outside bounds at " + formatPos(barrier)); }
                }
            }
            appendChannelBoundsWarnings(lines, bounds, markerState.rawBarrierAnchor1Positions, markerState.barrierAnchor1Anchors, "barrier_anchor_1");
            appendChannelBoundsWarnings(lines, bounds, markerState.rawBarrierAnchor2Positions, markerState.barrierAnchor2Anchors, "barrier_anchor_2");
            appendChannelBoundsWarnings(lines, bounds, markerState.rawBarrierAnchor3Positions, markerState.barrierAnchor3Anchors, "barrier_anchor_3");
            appendChannelBoundsWarnings(lines, bounds, markerState.rawBarrierAnchor4Positions, markerState.barrierAnchor4Anchors, "barrier_anchor_4");
            RoomBounds activationBounds = getActivationBounds();
            if (activationBounds != null && canUseActivationInset(bounds)) {
                for (BlockPos barrier : markerState.barrierAnchors) {
                    if (getHorizontalDistanceToActivationEdge(activationBounds, barrier) <= ACTIVATION_BARRIER_WARNING_DISTANCE) {
                        lines.add("WARN: Activation edge is very close to barrier_anchor at " + formatPos(barrier) + " and may lock players outside.");
                        warnCount++;
                    }
                }
            }
            for (BlockPos exit : markerState.exitAnchors) {
                if (bounds.contains(exit)) { lines.add("WARN: Exit anchor inside room bounds at " + formatPos(exit)); warnCount++; }
            }
            warnCount += appendOverlapWarnings(level, lines, bounds);
        }

        ValidationSummary summary = failCount > 0 ? ValidationSummary.FAIL : (warnCount > 0 ? ValidationSummary.WARN : ValidationSummary.PASS);
        markerState.lastValidationSummary = summary.name();
        return new ValidationReport(summary, lines);
    }

    private int appendOverlapWarnings(ServerLevel level, List<String> lines, RoomBounds bounds) {
        int warnCount = 0;
        BlockPos min = worldPosition.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS);
        BlockPos max = worldPosition.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (pos.equals(worldPosition) || !level.getBlockState(pos).is(ModBlocks.ROOM_CONTROLLER.get())) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof RoomControllerBlockEntity other)) {
                continue;
            }

            other.scanMarkers(level);
            RoomBounds otherBounds = other.roomBounds();
            if (otherBounds == null) {
                continue;
            }

            int overlapMinX = Math.max(bounds.min().getX(), otherBounds.min().getX());
            int overlapMinY = Math.max(bounds.min().getY(), otherBounds.min().getY());
            int overlapMinZ = Math.max(bounds.min().getZ(), otherBounds.min().getZ());
            int overlapMaxX = Math.min(bounds.max().getX(), otherBounds.max().getX());
            int overlapMaxY = Math.min(bounds.max().getY(), otherBounds.max().getY());
            int overlapMaxZ = Math.min(bounds.max().getZ(), otherBounds.max().getZ());
            if (overlapMinX > overlapMaxX || overlapMinY > overlapMaxY || overlapMinZ > overlapMaxZ) {
                continue;
            }

            lines.add("WARN: Bounds overlap nearby room controller at " + formatPos(pos));
            lines.add("WARN: Overlap size: X=" + (overlapMaxX - overlapMinX + 1) + ", Y=" + (overlapMaxY - overlapMinY + 1) + ", Z=" + (overlapMaxZ - overlapMinZ + 1));
            lines.add("INFO: Current bounds min/max: " + formatPos(bounds.min()) + " -> " + formatPos(bounds.max()));
            lines.add("INFO: Other bounds min/max: " + formatPos(otherBounds.min()) + " -> " + formatPos(otherBounds.max()));
            warnCount++;
        }
        return warnCount;
    }

    private void appendChannelBoundsWarnings(List<String> lines, RoomBounds bounds, List<BlockPos> rawPositions, List<BlockPos> ownedPositions, String label) {
        for (BlockPos barrier : rawPositions) {
            if (!bounds.contains(barrier)) {
                if (ownedPositions.isEmpty()) {
                    lines.add("WARN: " + label + " outside bounds at " + formatPos(barrier));
                } else {
                    lines.add("INFO: Nearby unowned " + label + " outside bounds at " + formatPos(barrier));
                }
            }
        }
    }

    private int countFallbackBarrierFacing(ServerLevel level) {
        int fallbackCount = 0;
        for (BlockPos anchor : markerState.barrierAnchors) {
            BlockState state = level.getBlockState(anchor);
            if (!state.hasProperty(BarrierAnchorBlock.FACING)) {
                fallbackCount++;
            }
        }
        return fallbackCount;
    }

    private int getHorizontalDistanceToActivationEdge(RoomBounds activationBounds, BlockPos pos) {
        int dx = Math.min(Math.abs(pos.getX() - activationBounds.min().getX()), Math.abs(pos.getX() - activationBounds.max().getX()));
        int dz = Math.min(Math.abs(pos.getZ() - activationBounds.min().getZ()), Math.abs(pos.getZ() - activationBounds.max().getZ()));
        return Math.min(dx, dz);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (dungeonInstanceKey != null) {
            CompoundTag instanceTag = new CompoundTag();
            dungeonInstanceKey.save(instanceTag);
            tag.put("DungeonInstance", instanceTag);
        }
        RoomNbtCodec.saveLifecycleState(tag, lifecycle);
        RoomNbtCodec.saveEncounterRemovalState(tag, encounter);
        RoomNbtCodec.saveObjectiveState(tag, objective);
        RoomNbtCodec.saveEncounterTelemetry(tag, encounter);
        RoomNbtCodec.savePersistentMarkerState(tag, markerState);
        RoomNbtCodec.saveEncounterRoster(tag, encounter);
        RoomNbtCodec.saveEncounterCombatProgression(tag, encounter);
        RoomNbtCodec.saveBarrierState(tag, barrierController.persistentState());
        RoomNbtCodec.saveBoundsPreview(tag, markerState);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        dungeonInstanceKey = tag.contains("DungeonInstance", net.minecraft.nbt.Tag.TAG_COMPOUND)
            ? DungeonInstanceAllocationData.InstanceKey.loadOptional(tag.getCompound("DungeonInstance"))
            : null;
        RoomNbtCodec.loadLifecycleState(tag, lifecycle);
        RoomNbtCodec.loadEncounterRemovalState(tag, encounter);
        RoomNbtCodec.loadObjectiveState(tag, objective);
        RoomNbtCodec.loadEncounterTelemetry(tag, encounter);
        RoomNbtCodec.loadPersistentMarkerState(tag, markerState);
        RoomNbtCodec.loadEncounterRoster(tag, encounter);
        RoomNbtCodec.loadEncounterCombatProgression(tag, encounter);
        barrierController.importPersistentState(RoomNbtCodec.loadBarrierState(tag));
        RoomNbtCodec.loadBoundsPreviewAndResetTransientCache(tag, markerState);
        encounter.clearRecoveryTracking();
        playerSessionAssociation.clearTransientState();

        lifecycle.armReloadNormalization(encounter.trackedMobUuids.isEmpty());

        if (this.level instanceof ServerLevel serverLevel) {
            ActiveRoomProtectionEvents.syncProtectionRegistration(serverLevel, this);
        }
    }

    private ServerPlayer getRewardRecipient(ServerLevel level) {
        return playerSessionAssociation.rewardRecipient(
            level, worldPosition, hasBoundsBox(), getActivationBounds()
        );
    }

    static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    public enum RoomState {
        INACTIVE,
        ACTIVE,
        COMPLETED,
        FAILED,
        RESETTING;

        public static RoomState byName(String name) {
            for (RoomState state : values()) {
                if (state.name().equals(name)) {
                    return state;
                }
            }
            return INACTIVE;
        }
    }

    public enum BoundsMode {
        RADIUS,
        ANCHOR_BOX,
        EXPLICIT_BOX
    }

    public enum ValidationSummary {
        PASS,
        WARN,
        FAIL
    }

    public record ValidationReport(ValidationSummary summary, List<String> lines) {
    }

    public record RoomBounds(BlockPos min, BlockPos max) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        public AABB toAabb() {
            return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
        }

        public int getWidth() {
            return max.getX() - min.getX() + 1;
        }

        public int getHeight() {
            return max.getY() - min.getY() + 1;
        }

        public int getDepth() {
            return max.getZ() - min.getZ() + 1;
        }

        public List<BlockPos> getCornerPositions() {
            Set<BlockPos> corners = new HashSet<>();
            int[] xs = new int[] { min.getX(), max.getX() };
            int[] ys = new int[] { min.getY(), max.getY() };
            int[] zs = new int[] { min.getZ(), max.getZ() };
            for (int x : xs) {
                for (int y : ys) {
                    for (int z : zs) {
                        corners.add(new BlockPos(x, y, z));
                    }
                }
            }
            return new ArrayList<>(corners);
        }

        public RoomBounds insetHorizontal(int inset) {
            return new RoomBounds(new BlockPos(min.getX() + inset, min.getY(), min.getZ() + inset), new BlockPos(max.getX() - inset, max.getY(), max.getZ() - inset));
        }
    }
}
