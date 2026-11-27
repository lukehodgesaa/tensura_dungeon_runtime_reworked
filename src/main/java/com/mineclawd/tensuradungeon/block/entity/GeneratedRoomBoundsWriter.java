package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class GeneratedRoomBoundsWriter {
    private static final int CLAIM_MARKERS_MAX_RADIUS = 256;

    private GeneratedRoomBoundsWriter() {
    }

    public static Result writeGeneratedBounds(ServerLevel level, RoomControllerBlockEntity controller, RoomControllerBlockEntity.RoomBounds bounds) {
        if (level == null) {
            return Result.fail("NO_LEVEL", null, 0);
        }
        if (controller == null) {
            return Result.fail("NO_CONTROLLER", null, 0);
        }
        if (bounds == null) {
            return Result.fail("NO_BOUNDS", null, 0);
        }

        RoomControllerBlockEntity.RoomBounds normalized = normalize(bounds);
        if (normalized.getWidth() <= 0 || normalized.getHeight() <= 0 || normalized.getDepth() <= 0) {
            return Result.fail("INVALID_BOUNDS", normalized, 0);
        }

        BlockPos controllerPos = controller.getBlockPos();
        if (!normalized.contains(controllerPos)) {
            return Result.fail("BOUNDS_DO_NOT_CONTAIN_CONTROLLER", normalized, 0);
        }

        int foreignControllers = countForeignControllers(level, controllerPos, normalized);
        if (foreignControllers > 0) {
            return Result.fail("FOREIGN_ROOM_CONTROLLER_PRESENT", normalized, foreignControllers);
        }

        controller.setExplicitRoomBounds(normalized, null, null);
        controller.scanMarkers(level);
        return new Result(true, "GENERATED_STRUCTURE_BOUNDS_WRITTEN", normalized, foreignControllers);
    }

    public static ClaimResult writeGeneratedBoundsAndClaim(
        ServerLevel level,
        RoomControllerBlockEntity controller,
        RoomControllerBlockEntity.RoomBounds bounds,
        int requestedClaimRadius,
        boolean forceClaim
    ) {
        return writeGeneratedBoundsAndClaimInternal(level, controller, bounds, requestedClaimRadius, forceClaim);
    }

    public static ClaimResult writeGeneratedBoundsAndClaimWithinResolvedBounds(
        ServerLevel level,
        RoomControllerBlockEntity controller,
        RoomControllerBlockEntity.RoomBounds bounds,
        int requestedClaimRadius,
        boolean forceClaim
    ) {
        return writeGeneratedBoundsAndClaimInternal(level, controller, bounds, requestedClaimRadius, forceClaim);
    }

    public static ClaimResult writeGeneratedBoundsAndClaimFromManifest(
        ServerLevel level,
        RoomControllerBlockEntity controller,
        RoomControllerBlockEntity.RoomBounds bounds,
        int requestedClaimRadius,
        Map<String, List<BlockPos>> markerPositions
    ) {
        long totalStartedAt = System.nanoTime();
        long phaseStartedAt = totalStartedAt;
        Result writeResult = validateAndWriteManifestBounds(level, controller, bounds);
        double boundsWriteMs = elapsedMillis(phaseStartedAt);
        if (!writeResult.success()) {
            return new ClaimResult(
                writeResult,
                null,
                null,
                new ClaimTimings(boundsWriteMs, 0.0D, 0.0D, elapsedMillis(totalStartedAt))
            );
        }

        int claimRadius = resolvedBoundsClaimRadius(
            controller.getBlockPos(), writeResult.bounds(), requestedClaimRadius
        );
        phaseStartedAt = System.nanoTime();
        RoomControllerBlockEntity.GeneratedMarkerManifestResult manifestResult =
            controller.claimGeneratedMarkersFromManifest(
                level, writeResult.bounds(), markerPositions, claimRadius
            );
        double markerClaimMs = elapsedMillis(phaseStartedAt);
        if (!manifestResult.success() || manifestResult.claimResult() == null) {
            return new ClaimResult(
                Result.fail(manifestResult.reason(), writeResult.bounds(), 0),
                null,
                null,
                new ClaimTimings(
                    boundsWriteMs, markerClaimMs, 0.0D, elapsedMillis(totalStartedAt)
                )
            );
        }

        phaseStartedAt = System.nanoTime();
        StructureMarkerReadinessReport readinessReport =
            StructureMarkerReadinessValidator.analyzeGenerated(
                level, controller, markerPositions
            );
        double readinessMs = elapsedMillis(phaseStartedAt);
        return new ClaimResult(
            writeResult,
            manifestResult.claimResult(),
            readinessReport,
            new ClaimTimings(
                boundsWriteMs, markerClaimMs, readinessMs, elapsedMillis(totalStartedAt)
            )
        );
    }

    private static Result validateAndWriteManifestBounds(
        ServerLevel level,
        RoomControllerBlockEntity controller,
        RoomControllerBlockEntity.RoomBounds bounds
    ) {
        if (level == null) return Result.fail("NO_LEVEL", null, 0);
        if (controller == null) return Result.fail("NO_CONTROLLER", null, 0);
        if (bounds == null) return Result.fail("NO_BOUNDS", null, 0);
        RoomControllerBlockEntity.RoomBounds normalized = normalize(bounds);
        if (normalized.getWidth() <= 0 || normalized.getHeight() <= 0
            || normalized.getDepth() <= 0) {
            return Result.fail("INVALID_BOUNDS", normalized, 0);
        }
        if (!normalized.contains(controller.getBlockPos())) {
            return Result.fail("BOUNDS_DO_NOT_CONTAIN_CONTROLLER", normalized, 0);
        }
        controller.setExplicitRoomBounds(normalized, null, null);
        return new Result(true, "GENERATED_MANIFEST_BOUNDS_WRITTEN", normalized, 0);
    }
    private static ClaimResult writeGeneratedBoundsAndClaimInternal(
        ServerLevel level,
        RoomControllerBlockEntity controller,
        RoomControllerBlockEntity.RoomBounds bounds,
        int requestedClaimRadius,
        boolean forceClaim
    ) {
        long totalStartedAt = System.nanoTime();
        long phaseStartedAt = totalStartedAt;
        Result writeResult = writeGeneratedBounds(level, controller, bounds);
        double boundsWriteMs = elapsedMillis(phaseStartedAt);
        if (!writeResult.success()) {
            return new ClaimResult(
                writeResult,
                null,
                null,
                new ClaimTimings(boundsWriteMs, 0.0D, 0.0D, elapsedMillis(totalStartedAt))
            );
        }

        int claimRadius = resolvedBoundsClaimRadius(
            controller.getBlockPos(), writeResult.bounds(), requestedClaimRadius
        );
        phaseStartedAt = System.nanoTime();
        RoomControllerBlockEntity.MarkerClaimResult claimResult = controller.claimGeneratedMarkersWithinBounds(
            level, writeResult.bounds(), claimRadius, forceClaim
        );
        double markerClaimMs = elapsedMillis(phaseStartedAt);
        phaseStartedAt = System.nanoTime();
        StructureMarkerReadinessReport readinessReport = StructureMarkerReadinessValidator.analyze(level, controller);
        double readinessMs = elapsedMillis(phaseStartedAt);
        return new ClaimResult(
            writeResult,
            claimResult,
            readinessReport,
            new ClaimTimings(boundsWriteMs, markerClaimMs, readinessMs, elapsedMillis(totalStartedAt))
        );
    }

    private static double elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0D;
    }

    static int resolvedBoundsClaimRadius(
        BlockPos controllerPos,
        RoomControllerBlockEntity.RoomBounds bounds,
        int requestedClaimRadius
    ) {
        int requiredRadius = Math.max(
            Math.max(
                Math.max(Math.abs(controllerPos.getX() - bounds.min().getX()), Math.abs(bounds.max().getX() - controllerPos.getX())),
                Math.max(Math.abs(controllerPos.getY() - bounds.min().getY()), Math.abs(bounds.max().getY() - controllerPos.getY()))
            ),
            Math.max(Math.abs(controllerPos.getZ() - bounds.min().getZ()), Math.abs(bounds.max().getZ() - controllerPos.getZ()))
        );
        return Math.max(1, Math.min(CLAIM_MARKERS_MAX_RADIUS, Math.max(requestedClaimRadius, requiredRadius)));
    }

    private static RoomControllerBlockEntity.RoomBounds normalize(RoomControllerBlockEntity.RoomBounds bounds) {
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        return new RoomControllerBlockEntity.RoomBounds(
            new BlockPos(
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ())
            ),
            new BlockPos(
                Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ())
            )
        );
    }

    private static int countForeignControllers(ServerLevel level, BlockPos controllerPos, RoomControllerBlockEntity.RoomBounds bounds) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockPos checkedPos = pos.immutable();
            if (checkedPos.equals(controllerPos) || !level.getBlockState(checkedPos).is(ModBlocks.ROOM_CONTROLLER.get())) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(checkedPos);
            if (blockEntity instanceof RoomControllerBlockEntity) {
                count++;
            }
        }
        return count;
    }

    public record Result(
        boolean success,
        String reason,
        RoomControllerBlockEntity.RoomBounds bounds,
        int foreignControllerCount
    ) {
        static Result fail(String reason, RoomControllerBlockEntity.RoomBounds bounds, int foreignControllerCount) {
            return new Result(false, reason, bounds, foreignControllerCount);
        }
    }

    public record ClaimResult(
        Result boundsResult,
        RoomControllerBlockEntity.MarkerClaimResult claimResult,
        StructureMarkerReadinessReport readinessReport,
        ClaimTimings timings
    ) {
        public boolean success() {
            return boundsResult != null && boundsResult.success() && claimResult != null && readinessReport != null;
        }
    }

    public record ClaimTimings(
        double boundsWriteMs,
        double markerClaimMs,
        double readinessMs,
        double totalMs
    ) {
    }
}
