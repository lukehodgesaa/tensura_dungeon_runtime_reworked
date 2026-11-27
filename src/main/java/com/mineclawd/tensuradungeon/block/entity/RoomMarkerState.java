package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.registry.ModBlocks;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

final class RoomMarkerState {
    boolean bakedMarkerMode;
    String lastValidationSummary = "PASS";

    int rawSpawnAnchorCount;
    int rawRangedSpawnAnchorCount;
    int rawFlankSpawnAnchorCount;
    int rawHighgroundSpawnAnchorCount;
    int rawEliteSpawnAnchorCount;
    int rawBossSpawnAnchorCount;
    int rawBarrierAnchorCount;
    int rawBarrierAnchor1Count;
    int rawBarrierAnchor2Count;
    int rawBarrierAnchor3Count;
    int rawBarrierAnchor4Count;
    int rawMobResetAnchorCount;
    int rawExitAnchorCount;
    int rawBoundsAnchorCount;
    int rawObjectiveAnchorCount;
    int rawInteractAnchorCount;

    final List<BlockPos> rawSpawnAnchorPositions = new ArrayList<>();
    final List<BlockPos> rawRangedSpawnAnchorPositions = new ArrayList<>();
    final List<BlockPos> rawFlankSpawnAnchorPositions = new ArrayList<>();
    final List<BlockPos> rawHighgroundSpawnAnchorPositions = new ArrayList<>();
    final List<BlockPos> rawEliteSpawnAnchorPositions = new ArrayList<>();
    final List<BlockPos> rawBossSpawnAnchorPositions = new ArrayList<>();
    final List<BlockPos> rawBarrierAnchorPositions = new ArrayList<>();
    final List<BlockPos> rawBarrierAnchor1Positions = new ArrayList<>();
    final List<BlockPos> rawBarrierAnchor2Positions = new ArrayList<>();
    final List<BlockPos> rawBarrierAnchor3Positions = new ArrayList<>();
    final List<BlockPos> rawBarrierAnchor4Positions = new ArrayList<>();

    final List<BlockPos> spawnAnchors = new ArrayList<>();
    final List<BlockPos> rangedSpawnAnchors = new ArrayList<>();
    final List<BlockPos> flankSpawnAnchors = new ArrayList<>();
    final List<BlockPos> highgroundSpawnAnchors = new ArrayList<>();
    final List<BlockPos> eliteSpawnAnchors = new ArrayList<>();
    final List<BlockPos> bossSpawnAnchors = new ArrayList<>();
    final List<BlockPos> barrierAnchors = new ArrayList<>();
    final List<BlockPos> barrierAnchor1Anchors = new ArrayList<>();
    final List<BlockPos> barrierAnchor2Anchors = new ArrayList<>();
    final List<BlockPos> barrierAnchor3Anchors = new ArrayList<>();
    final List<BlockPos> barrierAnchor4Anchors = new ArrayList<>();
    final List<BlockPos> mobResetAnchors = new ArrayList<>();
    final List<BlockPos> exitAnchors = new ArrayList<>();
    final List<BlockPos> boundsAnchors = new ArrayList<>();
    final List<BlockPos> objectiveAnchors = new ArrayList<>();
    final List<BlockPos> interactAnchors = new ArrayList<>();

    RoomControllerBlockEntity.RoomBounds explicitRoomBounds;
    BlockPos explicitBoundsAnchorA;
    BlockPos explicitBoundsAnchorB;
    final Set<BlockPos> boundsPreviewPositions = new HashSet<>();
    final Set<BlockPos> claimPreviewPositions = new HashSet<>();

    boolean markerCacheReady;
    boolean markerCacheDirty = true;
    String markerCacheDirtyReason = "UNINITIALIZED";
    long markerScanCount;
    String lastMarkerScanReason = "NONE";
    long lastMarkerScanGameTime = -1L;
    long lastMarkerScanDurationNanos = -1L;
    String lastMarkerScanPerformanceWarning = "NONE";

    boolean claimedMarkerMode;
    String markerOwnershipMode = "LEGACY_RADIUS_FALLBACK";
    String lastMarkerClaimSummary = "NONE";
    String lastMarkerClaimReason = "NONE";
    RoomControllerBlockEntity.ClaimBoundsResolution lastClaimBoundsResolution = RoomControllerBlockEntity.ClaimBoundsResolution.empty();
    int lastMarkerClaimRadius;
    int lastMarkerClaimTotal;
    int lastMarkerClaimSkippedOwnedByOther;
    int lastMarkerClaimStaleRemoved;
    long lastMarkerClaimDurationNanos = -1L;
    final Map<RoomClaimController.ClaimedMarkerKind, List<BlockPos>> claimedMarkers =
        new EnumMap<>(RoomClaimController.ClaimedMarkerKind.class);

    static RoomClaimController.ClaimedMarkerKind markerKindFor(BlockState state) {
        if (state.is(ModBlocks.SPAWN_MELEE.get())) return RoomClaimController.ClaimedMarkerKind.SPAWN_MELEE;
        if (state.is(ModBlocks.SPAWN_RANGED.get())) return RoomClaimController.ClaimedMarkerKind.SPAWN_RANGED;
        if (state.is(ModBlocks.SPAWN_FLANK.get())) return RoomClaimController.ClaimedMarkerKind.SPAWN_FLANK;
        if (state.is(ModBlocks.SPAWN_HIGHGROUND.get())) return RoomClaimController.ClaimedMarkerKind.SPAWN_HIGHGROUND;
        if (state.is(ModBlocks.SPAWN_ELITE.get())) return RoomClaimController.ClaimedMarkerKind.SPAWN_ELITE;
        if (state.is(ModBlocks.SPAWN_BOSS.get())) return RoomClaimController.ClaimedMarkerKind.SPAWN_BOSS;
        if (state.is(ModBlocks.BARRIER_ANCHOR.get())) return RoomClaimController.ClaimedMarkerKind.BARRIER;
        if (state.is(ModBlocks.BARRIER_ANCHOR_1.get())) return RoomClaimController.ClaimedMarkerKind.BARRIER_1;
        if (state.is(ModBlocks.BARRIER_ANCHOR_2.get())) return RoomClaimController.ClaimedMarkerKind.BARRIER_2;
        if (state.is(ModBlocks.BARRIER_ANCHOR_3.get())) return RoomClaimController.ClaimedMarkerKind.BARRIER_3;
        if (state.is(ModBlocks.BARRIER_ANCHOR_4.get())) return RoomClaimController.ClaimedMarkerKind.BARRIER_4;
        if (state.is(ModBlocks.MOB_RESET_ANCHOR.get())) return RoomClaimController.ClaimedMarkerKind.MOB_RESET;
        if (state.is(ModBlocks.EXIT_ANCHOR.get()) || state.is(ModBlocks.FLOOR_CONNECTOR_ANCHOR.get()) || state.is(ModBlocks.CONNECTOR_EXIT.get())) {
            return RoomClaimController.ClaimedMarkerKind.EXIT;
        }
        if (state.is(ModBlocks.BOUNDS_ANCHOR.get())) return RoomClaimController.ClaimedMarkerKind.BOUNDS;
        if (state.is(ModBlocks.OBJECTIVE_ANCHOR.get())) return RoomClaimController.ClaimedMarkerKind.OBJECTIVE;
        if (state.is(ModBlocks.INTERACT_ANCHOR.get())) return RoomClaimController.ClaimedMarkerKind.INTERACT;
        return null;
    }

    static RoomClaimController.ClaimedMarkerKind markerKindForName(String markerName) {
        if (markerName == null) return null;
        return switch (markerName) {
            case "spawn_melee" -> RoomClaimController.ClaimedMarkerKind.SPAWN_MELEE;
            case "spawn_ranged" -> RoomClaimController.ClaimedMarkerKind.SPAWN_RANGED;
            case "spawn_flank" -> RoomClaimController.ClaimedMarkerKind.SPAWN_FLANK;
            case "spawn_highground" -> RoomClaimController.ClaimedMarkerKind.SPAWN_HIGHGROUND;
            case "spawn_elite" -> RoomClaimController.ClaimedMarkerKind.SPAWN_ELITE;
            case "spawn_boss" -> RoomClaimController.ClaimedMarkerKind.SPAWN_BOSS;
            case "barrier_anchor" -> RoomClaimController.ClaimedMarkerKind.BARRIER;
            case "barrier_anchor_1" -> RoomClaimController.ClaimedMarkerKind.BARRIER_1;
            case "barrier_anchor_2" -> RoomClaimController.ClaimedMarkerKind.BARRIER_2;
            case "barrier_anchor_3" -> RoomClaimController.ClaimedMarkerKind.BARRIER_3;
            case "barrier_anchor_4" -> RoomClaimController.ClaimedMarkerKind.BARRIER_4;
            case "mob_reset_anchor" -> RoomClaimController.ClaimedMarkerKind.MOB_RESET;
            case "exit_anchor", "floor_connector_anchor", "connector_exit", "floor_entry_anchor" ->
                RoomClaimController.ClaimedMarkerKind.EXIT;
            case "bounds_anchor" -> RoomClaimController.ClaimedMarkerKind.BOUNDS;
            case "objective_anchor" -> RoomClaimController.ClaimedMarkerKind.OBJECTIVE;
            case "interact_anchor" -> RoomClaimController.ClaimedMarkerKind.INTERACT;
            default -> null;
        };
    }

    RoomMarkerState() {
        initializeClaimedMarkerLists();
    }

    void initializeClaimedMarkerLists() {
        for (RoomClaimController.ClaimedMarkerKind kind : RoomClaimController.ClaimedMarkerKind.values()) {
            claimedMarkers.computeIfAbsent(kind, ignored -> new ArrayList<>());
        }
    }

    void clearClaimedMarkers() {
        initializeClaimedMarkerLists();
        for (List<BlockPos> positions : claimedMarkers.values()) {
            positions.clear();
        }
    }

    List<BlockPos> claimedMarkers(RoomClaimController.ClaimedMarkerKind kind) {
        return claimedMarkers.computeIfAbsent(kind, ignored -> new ArrayList<>());
    }

    Set<BlockPos> claimedMarkerPositionsForConflictCheck() {
        Set<BlockPos> positions = new HashSet<>();
        if (!claimedMarkerMode) {
            return positions;
        }
        initializeClaimedMarkerLists();
        for (List<BlockPos> list : claimedMarkers.values()) {
            positions.addAll(list);
        }
        return positions;
    }

    RoomControllerBlockEntity.RoomBounds roomBounds() {
        if (explicitRoomBounds != null) {
            return explicitRoomBounds;
        }
        if (boundsAnchors.size() < 2) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : boundsAnchors) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new RoomControllerBlockEntity.RoomBounds(
            new BlockPos(minX, minY, minZ),
            new BlockPos(maxX, maxY, maxZ)
        );
    }

    boolean hasBounds() {
        return explicitRoomBounds != null || boundsAnchors.size() >= 2;
    }

    boolean isInsideBounds(BlockPos pos) {
        RoomControllerBlockEntity.RoomBounds bounds = roomBounds();
        return bounds != null && bounds.contains(pos);
    }

    int ownedNormalCombatAnchorCount() {
        return spawnAnchors.size() + rangedSpawnAnchors.size() + flankSpawnAnchors.size() + highgroundSpawnAnchors.size();
    }

    BlockPos nearestMobResetAnchor(BlockPos currentPos) {
        BlockPos nearest = mobResetAnchors.getFirst();
        double nearestDistance = nearest.distSqr(currentPos);
        for (BlockPos anchor : mobResetAnchors) {
            double distance = anchor.distSqr(currentPos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = anchor;
            }
        }
        return nearest;
    }

    String buildClaimedMarkerSummary() {
        initializeClaimedMarkerLists();
        List<String> parts = new ArrayList<>();
        for (RoomClaimController.ClaimedMarkerKind kind : RoomClaimController.ClaimedMarkerKind.values()) {
            int count = claimedMarkers.getOrDefault(kind, List.of()).size();
            if (count > 0) {
                parts.add(kind.name().toLowerCase(java.util.Locale.ROOT) + "=" + count);
            }
        }
        return parts.isEmpty() ? "NONE" : String.join(", ", parts);
    }

    void refreshLastMarkerClaimSummary() {
        lastMarkerClaimSummary = buildClaimedMarkerSummary();
    }

    void recordClaimResult(
        boolean claimedMarkerMode,
        String markerOwnershipMode,
        int radius,
        int totalClaimed,
        int skippedOwnedByOther,
        int staleRemoved,
        long durationNanos,
        String reason
    ) {
        this.claimedMarkerMode = claimedMarkerMode;
        this.markerOwnershipMode = markerOwnershipMode;
        this.lastMarkerClaimRadius = radius;
        this.lastMarkerClaimTotal = totalClaimed;
        this.lastMarkerClaimSkippedOwnedByOther = skippedOwnedByOther;
        this.lastMarkerClaimStaleRemoved = staleRemoved;
        this.lastMarkerClaimDurationNanos = durationNanos;
        this.lastMarkerClaimReason = reason;
        refreshLastMarkerClaimSummary();
    }

    void recordClaimedMarkerScan(int staleRemoved) {
        lastMarkerClaimStaleRemoved = staleRemoved;
        refreshLastMarkerClaimSummary();
        markerOwnershipMode = "CLAIMED_MARKERS";
    }

    PersistentState persistentState() {
        Map<RoomClaimController.ClaimedMarkerKind, List<BlockPos>> claimed = new EnumMap<>(RoomClaimController.ClaimedMarkerKind.class);
        claimedMarkers.forEach((kind, positions) -> claimed.put(kind, List.copyOf(positions)));
        return new PersistentState(
            List.copyOf(spawnAnchors),
            List.copyOf(rangedSpawnAnchors),
            List.copyOf(flankSpawnAnchors),
            List.copyOf(highgroundSpawnAnchors),
            List.copyOf(eliteSpawnAnchors),
            List.copyOf(bossSpawnAnchors),
            List.copyOf(barrierAnchors),
            List.copyOf(mobResetAnchors),
            List.copyOf(exitAnchors),
            List.copyOf(boundsAnchors),
            explicitRoomBounds,
            explicitBoundsAnchorA,
            explicitBoundsAnchorB,
            List.copyOf(objectiveAnchors),
            List.copyOf(interactAnchors),
            claimedMarkerMode,
            bakedMarkerMode,
            markerOwnershipMode,
            lastMarkerClaimSummary,
            lastMarkerClaimReason,
            lastMarkerClaimRadius,
            lastMarkerClaimTotal,
            lastMarkerClaimSkippedOwnedByOther,
            lastMarkerClaimStaleRemoved,
            lastMarkerClaimDurationNanos,
            java.util.Collections.unmodifiableMap(claimed),
            true,
            List.copyOf(boundsPreviewPositions)
        );
    }

    void importPersistentState(PersistentState state) {
        replace(spawnAnchors, state.spawnAnchors());
        replace(rangedSpawnAnchors, state.rangedSpawnAnchors());
        replace(flankSpawnAnchors, state.flankSpawnAnchors());
        replace(highgroundSpawnAnchors, state.highgroundSpawnAnchors());
        replace(eliteSpawnAnchors, state.eliteSpawnAnchors());
        replace(bossSpawnAnchors, state.bossSpawnAnchors());
        replace(barrierAnchors, state.barrierAnchors());
        replace(mobResetAnchors, state.mobResetAnchors());
        replace(exitAnchors, state.exitAnchors());
        replace(boundsAnchors, state.boundsAnchors());
        explicitRoomBounds = state.explicitRoomBounds();
        explicitBoundsAnchorA = state.explicitBoundsAnchorA();
        explicitBoundsAnchorB = state.explicitBoundsAnchorB();
        replace(objectiveAnchors, state.objectiveAnchors());
        replace(interactAnchors, state.interactAnchors());
        claimedMarkerMode = state.claimedMarkerMode();
        bakedMarkerMode = state.bakedMarkerMode();
        markerOwnershipMode = state.markerOwnershipMode();
        lastMarkerClaimSummary = state.lastMarkerClaimSummary();
        lastMarkerClaimReason = state.lastMarkerClaimReason();
        lastMarkerClaimRadius = state.lastMarkerClaimRadius();
        lastMarkerClaimTotal = state.lastMarkerClaimTotal();
        lastMarkerClaimSkippedOwnedByOther = state.lastMarkerClaimSkippedOwnedByOther();
        lastMarkerClaimStaleRemoved = state.lastMarkerClaimStaleRemoved();
        lastMarkerClaimDurationNanos = state.lastMarkerClaimDurationNanos();
        clearClaimedMarkers();
        state.claimedMarkers().forEach((kind, positions) -> claimedMarkers(kind).addAll(positions));
        if (state.claimedCatalogPresent()) {
            refreshLastMarkerClaimSummary();
        }
        boundsPreviewPositions.clear();
        boundsPreviewPositions.addAll(state.boundsPreviewPositions());
    }

    void importBoundsPreviewOnLoad(List<BlockPos> positions) {
        boundsPreviewPositions.clear();
        boundsPreviewPositions.addAll(positions);
        markerCacheReady = false;
        markerCacheDirty = true;
        markerCacheDirtyReason = "LOAD";
        markerScanCount = 0L;
        lastMarkerScanReason = "NONE";
        lastMarkerScanGameTime = -1L;
        lastMarkerScanDurationNanos = -1L;
        lastMarkerScanPerformanceWarning = "NONE";
    }

    private static void replace(List<BlockPos> target, List<BlockPos> source) {
        target.clear();
        target.addAll(source);
    }

    record PersistentState(
        List<BlockPos> spawnAnchors,
        List<BlockPos> rangedSpawnAnchors,
        List<BlockPos> flankSpawnAnchors,
        List<BlockPos> highgroundSpawnAnchors,
        List<BlockPos> eliteSpawnAnchors,
        List<BlockPos> bossSpawnAnchors,
        List<BlockPos> barrierAnchors,
        List<BlockPos> mobResetAnchors,
        List<BlockPos> exitAnchors,
        List<BlockPos> boundsAnchors,
        RoomControllerBlockEntity.RoomBounds explicitRoomBounds,
        BlockPos explicitBoundsAnchorA,
        BlockPos explicitBoundsAnchorB,
        List<BlockPos> objectiveAnchors,
        List<BlockPos> interactAnchors,
        boolean claimedMarkerMode,
        boolean bakedMarkerMode,
        String markerOwnershipMode,
        String lastMarkerClaimSummary,
        String lastMarkerClaimReason,
        int lastMarkerClaimRadius,
        int lastMarkerClaimTotal,
        int lastMarkerClaimSkippedOwnedByOther,
        int lastMarkerClaimStaleRemoved,
        long lastMarkerClaimDurationNanos,
        Map<RoomClaimController.ClaimedMarkerKind, List<BlockPos>> claimedMarkers,
        boolean claimedCatalogPresent,
        List<BlockPos> boundsPreviewPositions
    ) {
    }
}
