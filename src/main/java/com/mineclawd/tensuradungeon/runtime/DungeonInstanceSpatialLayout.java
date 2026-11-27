package com.mineclawd.tensuradungeon.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;

public final class DungeonInstanceSpatialLayout {
    static final int MIN_HUB_CLEARANCE = 131_072;
    static final int PREFERRED_MAX_HUB_DISTANCE = 524_288;
    static final int MINIMUM_VERTICAL_BUILD_SPAN = 1_024;
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_ROWS = 3;
    private static final int RANDOM_ATTEMPTS = 64;
    private static final int BORDER_PADDING = 16;

    private DungeonInstanceSpatialLayout() {
    }

    public static Result planRemoteGrid(
        BlockPos hubOrigin,
        int minimumBuildHeight,
        int maximumBuildHeight,
        double borderMinX,
        double borderMaxX,
        double borderMinZ,
        double borderMaxZ,
        long entropy
    ) {
        if (hubOrigin == null || maximumBuildHeight <= minimumBuildHeight) {
            return Result.failed("INVALID_REMOTE_GRID_INPUT");
        }
        if (maximumBuildHeight - minimumBuildHeight < MINIMUM_VERTICAL_BUILD_SPAN) {
            return Result.failed("DUNGEON_BUILD_HEIGHT_TOO_SMALL");
        }

        int halfCell = DungeonInstanceAllocationData.CELL_DIAMETER / 2;
        int rightReach = (GRID_COLUMNS - 1) * DungeonInstanceAllocationData.CELL_STRIDE
            + halfCell - 1;
        int southReach = (GRID_ROWS - 1) * DungeonInstanceAllocationData.CELL_STRIDE
            + halfCell - 1;
        int minimumAnchorX = saturatedCeil(borderMinX) + halfCell + BORDER_PADDING;
        int maximumAnchorX = saturatedFloor(borderMaxX) - rightReach - BORDER_PADDING;
        int minimumAnchorZ = saturatedCeil(borderMinZ) + halfCell + BORDER_PADDING;
        int maximumAnchorZ = saturatedFloor(borderMaxZ) - southReach - BORDER_PADDING;
        if (minimumAnchorX > maximumAnchorX || minimumAnchorZ > maximumAnchorZ) {
            return Result.failed("WORLD_BORDER_TOO_SMALL_FOR_DUNGEON_GRID");
        }

        int centerOffsetX = ((GRID_COLUMNS - 1) * DungeonInstanceAllocationData.CELL_STRIDE) / 2;
        int centerOffsetZ = ((GRID_ROWS - 1) * DungeonInstanceAllocationData.CELL_STRIDE) / 2;
        double maximumGridRadius = Math.hypot(
            centerOffsetX + halfCell,
            centerOffsetZ + halfCell
        );
        double minimumCenterDistance = MIN_HUB_CLEARANCE + maximumGridRadius;
        SplittableRandom random = new SplittableRandom(entropy);
        Candidate best = null;
        for (int attempt = 0; attempt < RANDOM_ATTEMPTS; attempt++) {
            double angle = random.nextDouble(0.0D, Math.PI * 2.0D);
            double radius = random.nextDouble(
                minimumCenterDistance,
                Math.max(minimumCenterDistance + 1.0D, PREFERRED_MAX_HUB_DISTANCE)
            );
            int centerX = alignToChunk(hubOrigin.getX() + Math.cos(angle) * radius);
            int centerZ = alignToChunk(hubOrigin.getZ() + Math.sin(angle) * radius);
            int anchorX = clamp(centerX - centerOffsetX, minimumAnchorX, maximumAnchorX);
            int anchorZ = clamp(centerZ - centerOffsetZ, minimumAnchorZ, maximumAnchorZ);
            Candidate candidate = candidate(
                hubOrigin, anchorX, anchorZ, halfCell, rightReach, southReach
            );
            if (best == null || candidate.clearanceSquared() > best.clearanceSquared()) {
                best = candidate;
            }
            if (candidate.clearanceSquared()
                >= (double) MIN_HUB_CLEARANCE * MIN_HUB_CLEARANCE) {
                return Result.success(
                    new BlockPos(anchorX, centeredBuildY(minimumBuildHeight, maximumBuildHeight), anchorZ),
                    "RANDOM_REMOTE_GRID"
                );
            }
        }

        for (Candidate candidate : borderCandidates(
            hubOrigin, minimumAnchorX, maximumAnchorX, minimumAnchorZ, maximumAnchorZ,
            halfCell, rightReach, southReach
        )) {
            if (best == null || candidate.clearanceSquared() > best.clearanceSquared()) {
                best = candidate;
            }
        }
        if (best == null || best.clearanceSquared()
            < (double) MIN_HUB_CLEARANCE * MIN_HUB_CLEARANCE) {
            return Result.failed("WORLD_BORDER_CANNOT_ISOLATE_DUNGEON_GRID_FROM_HUB");
        }
        return Result.success(
            new BlockPos(
                best.anchorX(),
                centeredBuildY(minimumBuildHeight, maximumBuildHeight),
                best.anchorZ()
            ),
            "FARTHEST_BORDER_SAFE_REMOTE_GRID"
        );
    }

    static int centeredBuildY(int minimumBuildHeight, int maximumBuildHeight) {
        if (maximumBuildHeight <= minimumBuildHeight) {
            throw new IllegalArgumentException("Build-height range must be non-empty");
        }
        return minimumBuildHeight + Math.floorDiv(maximumBuildHeight - minimumBuildHeight, 2);
    }

    public static boolean requiresRemoteRelocation(
        BlockPos hubOrigin,
        BlockPos gridAnchor,
        int minimumBuildHeight,
        int maximumBuildHeight
    ) {
        if (hubOrigin == null || gridAnchor == null) return true;
        int halfCell = DungeonInstanceAllocationData.CELL_DIAMETER / 2;
        int rightReach = (GRID_COLUMNS - 1) * DungeonInstanceAllocationData.CELL_STRIDE
            + halfCell - 1;
        int southReach = (GRID_ROWS - 1) * DungeonInstanceAllocationData.CELL_STRIDE
            + halfCell - 1;
        Candidate current = candidate(
            hubOrigin,
            gridAnchor.getX(),
            gridAnchor.getZ(),
            halfCell,
            rightReach,
            southReach
        );
        return gridAnchor.getY() != centeredBuildY(minimumBuildHeight, maximumBuildHeight)
            || current.clearanceSquared()
                < (double) MIN_HUB_CLEARANCE * MIN_HUB_CLEARANCE;
    }

    private static List<Candidate> borderCandidates(
        BlockPos hubOrigin,
        int minimumAnchorX,
        int maximumAnchorX,
        int minimumAnchorZ,
        int maximumAnchorZ,
        int halfCell,
        int rightReach,
        int southReach
    ) {
        List<Candidate> candidates = new ArrayList<>(4);
        for (int anchorX : new int[] {minimumAnchorX, maximumAnchorX}) {
            for (int anchorZ : new int[] {minimumAnchorZ, maximumAnchorZ}) {
                candidates.add(candidate(
                    hubOrigin, anchorX, anchorZ, halfCell, rightReach, southReach
                ));
            }
        }
        return candidates;
    }

    private static Candidate candidate(
        BlockPos hubOrigin,
        int anchorX,
        int anchorZ,
        int halfCell,
        int rightReach,
        int southReach
    ) {
        int minX = anchorX - halfCell;
        int maxX = anchorX + rightReach;
        int minZ = anchorZ - halfCell;
        int maxZ = anchorZ + southReach;
        double dx = axisDistance(hubOrigin.getX(), minX, maxX);
        double dz = axisDistance(hubOrigin.getZ(), minZ, maxZ);
        return new Candidate(
            anchorX,
            anchorZ,
            dx * dx + dz * dz
        );
    }

    private static double axisDistance(int value, int minimum, int maximum) {
        if (value < minimum) return minimum - (double) value;
        if (value > maximum) return value - (double) maximum;
        return 0.0D;
    }

    private static int alignToChunk(double coordinate) {
        long rounded = Math.round(coordinate / 16.0D) * 16L;
        if (rounded < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (rounded > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) rounded;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int saturatedCeil(double value) {
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.ceil(value);
    }

    private static int saturatedFloor(double value) {
        if (value <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (value >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.floor(value);
    }

    private record Candidate(
        int anchorX,
        int anchorZ,
        double clearanceSquared
    ) {
    }

    public record Result(boolean valid, BlockPos gridAnchor, String reason) {
        private static Result success(BlockPos gridAnchor, String reason) {
            return new Result(true, gridAnchor.immutable(), reason);
        }

        private static Result failed(String reason) {
            return new Result(false, null, reason);
        }
    }
}
