package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity.RoomBounds;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class RoomClaimController {
    private static final int CLAIM_MARKERS_MAX_RADIUS = 256;
    private static final long CLAIM_MARKERS_WARN_THRESHOLD_NANOS = 50_000_000L;
    private static final BlockState CLAIM_PREVIEW_BOUNDS_EDGE_BLOCK_STATE = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
    private static final BlockState CLAIM_PREVIEW_SELECTED_BOUNDS_ANCHOR_BLOCK_STATE = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
    private static final BlockState CLAIM_PREVIEW_CLAIMED_MARKER_BLOCK_STATE = Blocks.LIME_STAINED_GLASS.defaultBlockState();
    private static final BlockState CLAIM_PREVIEW_SKIPPED_OUTSIDE_BLOCK_STATE = Blocks.RED_STAINED_GLASS.defaultBlockState();
    private static final BlockState CLAIM_PREVIEW_SKIPPED_OWNED_BY_OTHER_BLOCK_STATE = Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
    private static final int CLAIM_PREVIEW_EDGE_MAX_BLOCKS = 1024;

    private final RoomMarkerState state;

    RoomClaimController(RoomMarkerState state) {
        this.state = state;
    }

    RoomControllerBlockEntity.MarkerClaimResult claimMarkers(ServerLevel level, BlockPos origin, int requestedRadius, boolean force) {
        long startedAt = System.nanoTime();
        int radius = Math.max(1, Math.min(CLAIM_MARKERS_MAX_RADIUS, requestedRadius));

        state.initializeClaimedMarkerLists();
        state.clearClaimedMarkers();

        Set<BlockPos> otherClaimedMarkers = force ? Set.of() : findMarkersClaimedByOtherControllers(level, origin, radius);
        RoomControllerBlockEntity.ClaimBoundsResolution boundsResolution = resolveClaimBoundsForController(level, origin, radius);
        RoomBounds claimBounds = boundsResolution.selectedBounds();
        boolean boundsFiltered = claimBounds != null;
        boolean boundsRejectedUnsafe = !boundsFiltered && "NO_SINGLE_CONTROLLER_BOUNDS_PAIR".equals(boundsResolution.selectedReason());
        state.lastClaimBoundsResolution = boundsResolution;
        int skippedOwnedByOther = 0;
        int skippedOutsideBounds = 0;
        int rawScanned = 0;
        int totalClaimed = 0;

        if (!boundsRejectedUnsafe) {
            BlockPos min = origin.offset(-radius, -radius, -radius);
            BlockPos max = origin.offset(radius, radius, radius);
            for (BlockPos foundPos : BlockPos.betweenClosed(min, max)) {
                BlockPos markerPos = foundPos.immutable();
                ClaimedMarkerKind kind = RoomMarkerState.markerKindFor(level.getBlockState(markerPos));
                if (kind == null) {
                    continue;
                }
                rawScanned++;
                if (boundsFiltered && !claimBounds.contains(markerPos)) {
                    skippedOutsideBounds++;
                    continue;
                }
                if (!force && otherClaimedMarkers.contains(markerPos)) {
                    skippedOwnedByOther++;
                    continue;
                }
                List<BlockPos> list = state.claimedMarkers(kind);
                if (!list.contains(markerPos)) {
                    list.add(markerPos);
                    totalClaimed++;
                }
            }
        }

        if (claimBounds != null) {
            state.explicitRoomBounds = new RoomBounds(claimBounds.min().immutable(), claimBounds.max().immutable());
            state.explicitBoundsAnchorA = boundsResolution.selectedAnchorA() == null ? null : boundsResolution.selectedAnchorA().immutable();
            state.explicitBoundsAnchorB = boundsResolution.selectedAnchorB() == null ? null : boundsResolution.selectedAnchorB().immutable();
        }

        boolean hasClaimedMarkers = totalClaimed > 0;
        String claimOwnershipMode = boundsRejectedUnsafe ? "BOUNDS_REJECTED_NO_CLAIM" : (hasClaimedMarkers ? "CLAIMED_MARKERS" : "LEGACY_RADIUS_FALLBACK");
        String claimReason = boundsRejectedUnsafe
            ? boundsResolution.selectedReason()
            : (boundsFiltered
                ? (totalClaimed > 0 ? "BOUNDS_FILTERED_CLAIMED_MARKERS" : "BOUNDS_FILTERED_NO_MARKERS_FOUND")
                : (totalClaimed > 0 ? "LEGACY_RADIUS_CLAIM" : "LEGACY_RADIUS_NO_MARKERS_FOUND"));
        long durationNanos = System.nanoTime() - startedAt;
        state.recordClaimResult(hasClaimedMarkers, claimOwnershipMode, radius, totalClaimed, skippedOwnedByOther, 0, durationNanos, claimReason);
        if (durationNanos >= CLAIM_MARKERS_WARN_THRESHOLD_NANOS) {
            state.lastMarkerScanPerformanceWarning = "SLOW_CLAIM " + String.format(java.util.Locale.ROOT, "%.2f", durationNanos / 1_000_000.0D) + "ms";
        }

        return new RoomControllerBlockEntity.MarkerClaimResult(radius, totalClaimed, skippedOwnedByOther, skippedOutsideBounds, rawScanned, boundsFiltered, 0, durationNanos, state.lastMarkerClaimSummary, state.lastMarkerClaimReason);
    }

    RoomControllerBlockEntity.MarkerClaimResult claimGeneratedMarkersWithinBounds(
        ServerLevel level,
        BlockPos origin,
        RoomBounds resolvedBounds,
        int requestedRadius,
        boolean force
    ) {
        long startedAt = System.nanoTime();
        int radius = Math.max(1, Math.min(CLAIM_MARKERS_MAX_RADIUS, requestedRadius));
        state.clearClaimedMarkers();

        Set<BlockPos> otherClaimedMarkers = force ? Set.of() : findMarkersClaimedByOtherControllers(level, origin, radius);
        BlockPos radiusMin = origin.offset(-radius, -radius, -radius);
        BlockPos radiusMax = origin.offset(radius, radius, radius);
        BlockPos iterationMin = new BlockPos(
            Math.max(radiusMin.getX(), resolvedBounds.min().getX()),
            Math.max(radiusMin.getY(), resolvedBounds.min().getY()),
            Math.max(radiusMin.getZ(), resolvedBounds.min().getZ())
        );
        BlockPos iterationMax = new BlockPos(
            Math.min(radiusMax.getX(), resolvedBounds.max().getX()),
            Math.min(radiusMax.getY(), resolvedBounds.max().getY()),
            Math.min(radiusMax.getZ(), resolvedBounds.max().getZ())
        );
        List<BlockPos> nearbyBoundsAnchors = new ArrayList<>();
        int skippedOwnedByOther = 0;
        int rawScanned = 0;
        int totalClaimed = 0;

        if (iterationMin.getX() <= iterationMax.getX()
            && iterationMin.getY() <= iterationMax.getY()
            && iterationMin.getZ() <= iterationMax.getZ()) {
            for (BlockPos foundPos : BlockPos.betweenClosed(iterationMin, iterationMax)) {
                BlockPos markerPos = foundPos.immutable();
                ClaimedMarkerKind kind = RoomMarkerState.markerKindFor(level.getBlockState(markerPos));
                if (kind == null) {
                    continue;
                }
                rawScanned++;
                if (kind == ClaimedMarkerKind.BOUNDS) {
                    nearbyBoundsAnchors.add(markerPos);
                }
                if (!force && otherClaimedMarkers.contains(markerPos)) {
                    skippedOwnedByOther++;
                    continue;
                }
                List<BlockPos> list = state.claimedMarkers(kind);
                if (!list.contains(markerPos)) {
                    list.add(markerPos);
                    totalClaimed++;
                }
            }
        }

        state.lastClaimBoundsResolution = new RoomControllerBlockEntity.ClaimBoundsResolution(
            radius,
            nearbyBoundsAnchors,
            List.of(),
            resolvedBounds,
            state.explicitBoundsAnchorA,
            state.explicitBoundsAnchorB,
            1,
            0,
            0,
            0,
            "EXPLICIT_ROOM_BOUNDS"
        );
        boolean hasClaimedMarkers = totalClaimed > 0;
        String ownershipMode = "CLAIMED_MARKERS";
        String reason = hasClaimedMarkers ? "BOUNDS_FILTERED_CLAIMED_MARKERS" : "BOUNDS_FILTERED_NO_MARKERS_FOUND";
        long durationNanos = System.nanoTime() - startedAt;
        state.recordClaimResult(true, ownershipMode, radius, totalClaimed, skippedOwnedByOther, 0, durationNanos, reason);
        if (durationNanos >= CLAIM_MARKERS_WARN_THRESHOLD_NANOS) {
            state.lastMarkerScanPerformanceWarning = "SLOW_CLAIM "
                + String.format(java.util.Locale.ROOT, "%.2f", durationNanos / 1_000_000.0D) + "ms";
        }

        return new RoomControllerBlockEntity.MarkerClaimResult(
            radius,
            totalClaimed,
            skippedOwnedByOther,
            0,
            rawScanned,
            true,
            0,
            durationNanos,
            state.lastMarkerClaimSummary,
            state.lastMarkerClaimReason
        );
    }

    RoomControllerBlockEntity.ClaimPreviewResult previewClaimMarkers(ServerLevel level, BlockPos origin, int requestedRadius, boolean force) {
        int radius = Math.max(1, Math.min(CLAIM_MARKERS_MAX_RADIUS, requestedRadius));
        RoomControllerBlockEntity.ClaimBoundsResolution boundsResolution = resolveClaimBoundsForController(level, origin, radius);
        state.lastClaimBoundsResolution = boundsResolution;
        RoomBounds claimBounds = boundsResolution.selectedBounds();
        boolean boundsFiltered = claimBounds != null;
        Set<BlockPos> otherClaimedMarkers = force ? Set.of() : findMarkersClaimedByOtherControllers(level, origin, radius);
        Map<ClaimedMarkerKind, Integer> claimedByKind = new EnumMap<>(ClaimedMarkerKind.class);
        Map<ClaimedMarkerKind, Integer> skippedOutsideByKind = new EnumMap<>(ClaimedMarkerKind.class);
        Map<ClaimedMarkerKind, Integer> skippedOwnedByKind = new EnumMap<>(ClaimedMarkerKind.class);
        int rawScanned = 0;
        int totalClaimed = 0;
        int skippedOutsideBounds = 0;
        int skippedOwnedByOther = 0;
        int previewBlocksPlaced = 0;
        int previewPlacementSkipped = 0;
        int edgeBlocksPlaced = 0;
        boolean edgePlacementCapped = false;
        if (claimBounds != null) {
            OutlinePlacementResult outlineResult = placeClaimBoundsOutline(level, boundsResolution);
            edgeBlocksPlaced = outlineResult.placed();
            previewPlacementSkipped += outlineResult.skipped();
            edgePlacementCapped = outlineResult.capped();
            previewBlocksPlaced += outlineResult.placed();
        }
        if (claimBounds == null && "NO_SINGLE_CONTROLLER_BOUNDS_PAIR".equals(boundsResolution.selectedReason())) {
            return new RoomControllerBlockEntity.ClaimPreviewResult(radius, force, false, null, 0, 0, 0, 0, 0, 0, 0, false, "NONE", "NONE", "NONE", boundsResolution);
        }

        BlockPos min = origin.offset(-radius, -radius, -radius);
        BlockPos max = origin.offset(radius, radius, radius);
        for (BlockPos foundPos : BlockPos.betweenClosed(min, max)) {
            BlockPos markerPos = foundPos.immutable();
            ClaimedMarkerKind kind = RoomMarkerState.markerKindFor(level.getBlockState(markerPos));
            if (kind == null) {
                continue;
            }
            rawScanned++;
            if (boundsFiltered && !claimBounds.contains(markerPos)) {
                skippedOutsideBounds++;
                incrementMarkerKindCount(skippedOutsideByKind, kind);
                PreviewPlacementResult placement = placeClaimMarkerPreview(level, markerPos, claimPreviewStateFor(kind, ClaimPreviewCategory.SKIPPED_OUTSIDE_BOUNDS));
                previewBlocksPlaced += placement.placed();
                previewPlacementSkipped += placement.skipped();
                continue;
            }
            if (!force && otherClaimedMarkers.contains(markerPos)) {
                skippedOwnedByOther++;
                incrementMarkerKindCount(skippedOwnedByKind, kind);
                PreviewPlacementResult placement = placeClaimMarkerPreview(level, markerPos, CLAIM_PREVIEW_SKIPPED_OWNED_BY_OTHER_BLOCK_STATE);
                previewBlocksPlaced += placement.placed();
                previewPlacementSkipped += placement.skipped();
                continue;
            }
            totalClaimed++;
            incrementMarkerKindCount(claimedByKind, kind);
            PreviewPlacementResult placement = placeClaimMarkerPreview(level, markerPos, claimPreviewStateFor(kind, ClaimPreviewCategory.CLAIMED));
            previewBlocksPlaced += placement.placed();
            previewPlacementSkipped += placement.skipped();
        }

        return new RoomControllerBlockEntity.ClaimPreviewResult(radius, force, boundsFiltered, claimBounds, rawScanned, totalClaimed, skippedOutsideBounds, skippedOwnedByOther, previewBlocksPlaced, previewPlacementSkipped, edgeBlocksPlaced, edgePlacementCapped, buildMarkerKindSummary(claimedByKind), buildMarkerKindSummary(skippedOutsideByKind), buildMarkerKindSummary(skippedOwnedByKind), boundsResolution);
    }

    RoomControllerBlockEntity.ClaimBoundsResolution resolveClaimBoundsForController(ServerLevel level, BlockPos origin, int radius) {
        List<BlockPos> nearbyBoundsAnchors = new ArrayList<>();
        BlockPos min = origin.offset(-radius, -radius, -radius);
        BlockPos max = origin.offset(radius, radius, radius);
        for (BlockPos foundPos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(foundPos).is(ModBlocks.BOUNDS_ANCHOR.get())) {
                nearbyBoundsAnchors.add(foundPos.immutable());
            }
        }
        if (state.explicitRoomBounds != null) {
            return new RoomControllerBlockEntity.ClaimBoundsResolution(radius, nearbyBoundsAnchors, List.of(), state.explicitRoomBounds, state.explicitBoundsAnchorA, state.explicitBoundsAnchorB, 1, 0, 0, 0, "EXPLICIT_ROOM_BOUNDS");
        }
        if (nearbyBoundsAnchors.size() < 2) {
            return new RoomControllerBlockEntity.ClaimBoundsResolution(radius, nearbyBoundsAnchors, List.of(), null, null, null, 0, 0, 0, 0, "NOT_ENOUGH_BOUNDS_ANCHORS");
        }

        List<RoomControllerBlockEntity.ClaimBoundsCandidate> candidates = new ArrayList<>();
        RoomControllerBlockEntity.ClaimBoundsCandidate best = null;
        long bestVolume = Long.MAX_VALUE;
        int rejectMissingControllerCount = 0;
        int rejectForeignControllerCount = 0;
        boolean hadContainingCandidate = false;
        for (int firstIndex = 0; firstIndex < nearbyBoundsAnchors.size() - 1; firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < nearbyBoundsAnchors.size(); secondIndex++) {
                BlockPos first = nearbyBoundsAnchors.get(firstIndex);
                BlockPos second = nearbyBoundsAnchors.get(secondIndex);
                RoomBounds candidateBounds = new RoomBounds(
                    new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ())),
                    new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()))
                );
                boolean containsController = candidateBounds.contains(origin);
                int controllerCount = countControllersInBounds(level, candidateBounds);
                int foreignControllerCount = containsController ? Math.max(0, controllerCount - 1) : controllerCount;
                String rejectionReason = "NONE";
                if (!containsController) {
                    rejectionReason = "DOES_NOT_CONTAIN_CONTROLLER";
                    rejectMissingControllerCount++;
                } else if (foreignControllerCount > 0) {
                    rejectionReason = "FOREIGN_ROOM_CONTROLLER_PRESENT";
                    rejectForeignControllerCount++;
                    hadContainingCandidate = true;
                } else {
                    hadContainingCandidate = true;
                }
                long volume = (long) candidateBounds.getWidth() * candidateBounds.getHeight() * candidateBounds.getDepth();
                RoomControllerBlockEntity.ClaimBoundsCandidate candidate = new RoomControllerBlockEntity.ClaimBoundsCandidate(first, second, candidateBounds, containsController, controllerCount, foreignControllerCount, volume, rejectionReason, false);
                candidates.add(candidate);
                if (!containsController || foreignControllerCount > 0) {
                    continue;
                }
                if (volume < bestVolume) {
                    best = candidate;
                    bestVolume = volume;
                }
            }
        }
        List<RoomControllerBlockEntity.ClaimBoundsCandidate> decoratedCandidates = new ArrayList<>();
        for (RoomControllerBlockEntity.ClaimBoundsCandidate candidate : candidates) {
            decoratedCandidates.add(new RoomControllerBlockEntity.ClaimBoundsCandidate(candidate.anchorA(), candidate.anchorB(), candidate.bounds(), candidate.containsController(), candidate.controllerCount(), candidate.foreignControllerCount(), candidate.volume(), candidate.rejectionReason(), best != null && candidate.equalsByValue(best)));
        }
        decoratedCandidates.sort(Comparator.comparingLong(RoomControllerBlockEntity.ClaimBoundsCandidate::volume).thenComparing(candidate -> candidate.bounds().min().getX()).thenComparing(candidate -> candidate.bounds().min().getY()).thenComparing(candidate -> candidate.bounds().min().getZ()));
        if (best == null) {
            String selectedReason = rejectForeignControllerCount > 0 && hadContainingCandidate ? "NO_SINGLE_CONTROLLER_BOUNDS_PAIR" : "NO_CONTAINING_BOUNDS_PAIR";
            return new RoomControllerBlockEntity.ClaimBoundsResolution(radius, nearbyBoundsAnchors, decoratedCandidates, null, null, null, 0, 0, rejectMissingControllerCount, rejectForeignControllerCount, selectedReason);
        }
        return new RoomControllerBlockEntity.ClaimBoundsResolution(radius, nearbyBoundsAnchors, decoratedCandidates, best.bounds(), best.anchorA(), best.anchorB(), best.controllerCount(), best.foreignControllerCount(), rejectMissingControllerCount, rejectForeignControllerCount, "SINGLE_CONTROLLER_BOUNDS");
    }

    private int countControllersInBounds(ServerLevel level, RoomBounds bounds) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            if (level.getBlockState(pos).is(ModBlocks.ROOM_CONTROLLER.get())) {
                count++;
            }
        }
        return count;
    }

    private Set<BlockPos> findMarkersClaimedByOtherControllers(ServerLevel level, BlockPos origin, int radius) {
        Set<BlockPos> claimedByOther = new HashSet<>();
        BlockPos min = origin.offset(-radius, -radius, -radius);
        BlockPos max = origin.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (pos.equals(origin) || !level.getBlockState(pos).is(ModBlocks.ROOM_CONTROLLER.get())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RoomControllerBlockEntity other) {
                claimedByOther.addAll(other.markerState.claimedMarkerPositionsForConflictCheck());
            }
        }
        return claimedByOther;
    }

    private void incrementMarkerKindCount(Map<ClaimedMarkerKind, Integer> counts, ClaimedMarkerKind kind) {
        counts.put(kind, counts.getOrDefault(kind, 0) + 1);
    }

    private String buildMarkerKindSummary(Map<ClaimedMarkerKind, Integer> counts) {
        List<String> parts = new ArrayList<>();
        for (ClaimedMarkerKind kind : ClaimedMarkerKind.values()) {
            int count = counts.getOrDefault(kind, 0);
            if (count > 0) {
                parts.add(kind.name().toLowerCase(java.util.Locale.ROOT) + "=" + count);
            }
        }
        return parts.isEmpty() ? "NONE" : String.join(", ", parts);
    }

    private OutlinePlacementResult placeClaimBoundsOutline(ServerLevel level, RoomControllerBlockEntity.ClaimBoundsResolution resolution) {
        RoomBounds bounds = resolution.selectedBounds();
        if (bounds == null) {
            return new OutlinePlacementResult(0, 0, false);
        }
        Set<BlockPos> outlinePositions = new LinkedHashSet<>();
        addBoundsOutlinePositions(outlinePositions, bounds);
        int placed = 0;
        int skipped = 0;
        int considered = 0;
        boolean capped = false;
        for (BlockPos previewPos : outlinePositions) {
            considered++;
            if (considered > CLAIM_PREVIEW_EDGE_MAX_BLOCKS) {
                capped = true;
                break;
            }
            PreviewPlacementResult placement = placeClaimPreviewBlock(level, previewPos, CLAIM_PREVIEW_BOUNDS_EDGE_BLOCK_STATE);
            placed += placement.placed();
            skipped += placement.skipped();
        }
        if (resolution.selectedAnchorA() != null) {
            PreviewPlacementResult placement = placeClaimPreviewBlock(level, resolution.selectedAnchorA(), CLAIM_PREVIEW_SELECTED_BOUNDS_ANCHOR_BLOCK_STATE);
            placed += placement.placed();
            skipped += placement.skipped();
        }
        if (resolution.selectedAnchorB() != null) {
            PreviewPlacementResult placement = placeClaimPreviewBlock(level, resolution.selectedAnchorB(), CLAIM_PREVIEW_SELECTED_BOUNDS_ANCHOR_BLOCK_STATE);
            placed += placement.placed();
            skipped += placement.skipped();
        }
        return new OutlinePlacementResult(placed, skipped, capped);
    }

    private void addBoundsOutlinePositions(Set<BlockPos> positions, RoomBounds bounds) {
        int minX = bounds.min().getX();
        int minY = bounds.min().getY();
        int minZ = bounds.min().getZ();
        int maxX = bounds.max().getX();
        int maxY = bounds.max().getY();
        int maxZ = bounds.max().getZ();
        for (int x = minX; x <= maxX; x++) {
            positions.add(new BlockPos(x, minY, minZ));
            positions.add(new BlockPos(x, minY, maxZ));
            positions.add(new BlockPos(x, maxY, minZ));
            positions.add(new BlockPos(x, maxY, maxZ));
        }
        for (int y = minY; y <= maxY; y++) {
            positions.add(new BlockPos(minX, y, minZ));
            positions.add(new BlockPos(minX, y, maxZ));
            positions.add(new BlockPos(maxX, y, minZ));
            positions.add(new BlockPos(maxX, y, maxZ));
        }
        for (int z = minZ; z <= maxZ; z++) {
            positions.add(new BlockPos(minX, minY, z));
            positions.add(new BlockPos(minX, maxY, z));
            positions.add(new BlockPos(maxX, minY, z));
            positions.add(new BlockPos(maxX, maxY, z));
        }
    }

    private PreviewPlacementResult placeClaimMarkerPreview(ServerLevel level, BlockPos markerPos, BlockState previewState) {
        BlockPos[] candidates = new BlockPos[] {
            markerPos.above(),
            markerPos.relative(Direction.NORTH),
            markerPos.relative(Direction.SOUTH),
            markerPos.relative(Direction.EAST),
            markerPos.relative(Direction.WEST),
            markerPos.above(2),
            markerPos.above().relative(Direction.NORTH),
            markerPos.above().relative(Direction.SOUTH),
            markerPos.above().relative(Direction.EAST),
            markerPos.above().relative(Direction.WEST)
        };
        for (BlockPos candidate : candidates) {
            if (canPlaceClaimPreviewAt(level, candidate)) {
                level.setBlock(candidate, previewState, 3);
                state.claimPreviewPositions.add(candidate.immutable());
                return new PreviewPlacementResult(1, 0);
            }
        }
        return new PreviewPlacementResult(0, 1);
    }

    private PreviewPlacementResult placeClaimPreviewBlock(ServerLevel level, BlockPos targetPos, BlockState previewState) {
        if (!canPlaceClaimPreviewAt(level, targetPos)) {
            return new PreviewPlacementResult(0, 1);
        }
        level.setBlock(targetPos, previewState, 3);
        state.claimPreviewPositions.add(targetPos.immutable());
        return new PreviewPlacementResult(1, 0);
    }

    private boolean canPlaceClaimPreviewAt(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        return current.isAir() || isClaimPreviewBlockState(current);
    }

    boolean isClaimPreviewBlockState(BlockState state) {
        Block block = state.getBlock();
        return block == CLAIM_PREVIEW_BOUNDS_EDGE_BLOCK_STATE.getBlock()
            || block == CLAIM_PREVIEW_SELECTED_BOUNDS_ANCHOR_BLOCK_STATE.getBlock()
            || block == CLAIM_PREVIEW_CLAIMED_MARKER_BLOCK_STATE.getBlock()
            || block == CLAIM_PREVIEW_SKIPPED_OUTSIDE_BLOCK_STATE.getBlock()
            || block == CLAIM_PREVIEW_SKIPPED_OWNED_BY_OTHER_BLOCK_STATE.getBlock()
            || block == Blocks.CYAN_STAINED_GLASS
            || block == Blocks.BLUE_STAINED_GLASS
            || block == Blocks.PURPLE_STAINED_GLASS;
    }

    private BlockState claimPreviewStateFor(ClaimedMarkerKind kind, ClaimPreviewCategory category) {
        if (category == ClaimPreviewCategory.SKIPPED_OUTSIDE_BOUNDS) {
            return CLAIM_PREVIEW_SKIPPED_OUTSIDE_BLOCK_STATE;
        }
        if (category == ClaimPreviewCategory.SKIPPED_OWNED_BY_OTHER) {
            return CLAIM_PREVIEW_SKIPPED_OWNED_BY_OTHER_BLOCK_STATE;
        }
        return switch (kind) {
            case BARRIER -> Blocks.CYAN_STAINED_GLASS.defaultBlockState();
            case BARRIER_1 -> Blocks.CYAN_STAINED_GLASS.defaultBlockState();
            case BARRIER_2 -> Blocks.BLUE_STAINED_GLASS.defaultBlockState();
            case BARRIER_3 -> Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            case BARRIER_4 -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case SPAWN_BOSS -> Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            default -> CLAIM_PREVIEW_CLAIMED_MARKER_BLOCK_STATE;
        };
    }

    record PreviewPlacementResult(int placed, int skipped) {
    }

    record OutlinePlacementResult(int placed, int skipped, boolean capped) {
    }

    enum ClaimPreviewCategory {
        CLAIMED,
        SKIPPED_OUTSIDE_BOUNDS,
        SKIPPED_OWNED_BY_OTHER
    }

    enum ClaimedMarkerKind {
        SPAWN_MELEE,
        SPAWN_RANGED,
        SPAWN_FLANK,
        SPAWN_HIGHGROUND,
        SPAWN_ELITE,
        SPAWN_BOSS,
        BARRIER,
        BARRIER_1,
        BARRIER_2,
        BARRIER_3,
        BARRIER_4,
        MOB_RESET,
        EXIT,
        BOUNDS,
        OBJECTIVE,
        INTERACT
    }
}
