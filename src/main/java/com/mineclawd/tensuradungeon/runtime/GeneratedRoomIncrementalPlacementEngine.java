package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.mixin.StructureTemplateAccessor;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mojang.logging.LogUtils;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalMetadata;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeRoomRuntimeRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeTemplateDescriptor;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Clearable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

final class GeneratedRoomIncrementalPlacementEngine {
    private static final int PLACE_FLAGS =
        Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final long FLUID_PROGRESS_WEIGHT = 256L;
    private static final long CHUNK_SYNC_PROGRESS_WEIGHT = 8_192L;
    private static final Map<MinecraftServer, Map<OperationKey, ActivePlacement>> ACTIVE =
        new WeakHashMap<>();

    private GeneratedRoomIncrementalPlacementEngine() {
    }

    static boolean hasActive(
        ServerLevel level,
        GeneratedFloorSession session,
        GeneratedRoomPlacementOperation.Request request
    ) {
        OperationKey key = key(level, session, request);
        if (key == null) return false;
        synchronized (ACTIVE) {
            Map<OperationKey, ActivePlacement> placements = ACTIVE.get(level.getServer());
            return placements != null && placements.containsKey(key);
        }
    }

    static ProgressSnapshot progress(
        ServerLevel level,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        if (level == null || instanceKey == null) return ProgressSnapshot.none();
        synchronized (ACTIVE) {
            Map<OperationKey, ActivePlacement> placements = ACTIVE.get(level.getServer());
            if (placements == null) return ProgressSnapshot.none();
            return placements.entrySet().stream()
                .filter(entry -> entry.getKey().dimension().equals(level.dimension().location()))
                .filter(entry -> entry.getKey().instanceKey().equals(instanceKey))
                .min(java.util.Comparator.comparingInt(entry -> entry.getKey().roomIndex()))
                .map(entry -> entry.getValue().progressSnapshot(entry.getKey().roomIndex()))
                .orElseGet(ProgressSnapshot::none);
        }
    }

    static AdvanceResult advance(
        ServerLevel level,
        GeneratedFloorSession session,
        GeneratedRoomPlacementOperation.Request request,
        StructureTemplate template,
        Rotation rotation,
        DungeonTemplateMechanicalMetadata metadata,
        GeneratedRoomPlacementOperation.SliceBudget budget
    ) {
        OperationKey key = key(level, session, request);
        if (key == null) {
            return AdvanceResult.failed("MISSING_INCREMENTAL_OPERATION_IDENTITY", Phase.VALIDATE_TEMPLATE, 0.0D);
        }

        ActivePlacement active;
        synchronized (ACTIVE) {
            Map<OperationKey, ActivePlacement> placements =
                ACTIVE.computeIfAbsent(level.getServer(), ignored -> new HashMap<>());
            active = placements.get(key);
            if (active == null) {
                StartResult start = ActivePlacement.start(level, request, template, rotation, metadata);
                if (start.active() == null) {
                    return AdvanceResult.failed(start.reason(), Phase.VALIDATE_TEMPLATE, 0.0D);
                }
                active = start.active();
                placements.put(key, active);
            } else if (!active.matches(request)) {
                placements.remove(key);
                return AdvanceResult.failed("INCREMENTAL_REQUEST_CHANGED", active.phase, active.workMillis());
            }
        }

        AdvanceResult result = active.advance(level, budget);
        if (result.terminal()) {
            synchronized (ACTIVE) {
                Map<OperationKey, ActivePlacement> placements = ACTIVE.get(level.getServer());
                if (placements != null) {
                    placements.remove(key);
                    if (placements.isEmpty()) ACTIVE.remove(level.getServer());
                }
            }
        }
        return result;
    }

    static void discard(
        ServerLevel level,
        GeneratedFloorSession session,
        GeneratedRoomPlacementOperation.Request request
    ) {
        OperationKey key = key(level, session, request);
        if (key == null) return;
        synchronized (ACTIVE) {
            Map<OperationKey, ActivePlacement> placements = ACTIVE.get(level.getServer());
            if (placements != null) {
                placements.remove(key);
                if (placements.isEmpty()) ACTIVE.remove(level.getServer());
            }
        }
    }

    @Nullable
    private static OperationKey key(
        ServerLevel level,
        GeneratedFloorSession session,
        GeneratedRoomPlacementOperation.Request request
    ) {
        if (level == null || session == null || request == null || request.roomPlan() == null
            || session.instanceKey() == null) {
            return null;
        }
        return new OperationKey(
            level.dimension().location(),
            session.instanceKey(),
            request.roomPlan().index(),
            request.roomPlan().controllerPos()
        );
    }

    enum Phase {
        VALIDATE_TEMPLATE,
        VALIDATE_CELL,
        VALIDATE_EXISTING,
        CLEAR_STALE,
        PLACE_SOLIDS,
        STAGE_FLUIDS,
        PLACE_FLUIDS,
        RECONCILE_SOLIDS,
        RECONCILE_FLUIDS,
        SYNC_WATCHED_CHUNKS,
        COMPLETE,
        FAILED
    }

    record AdvanceResult(
        boolean complete,
        boolean failed,
        String reason,
        Phase phase,
        Map<String, List<BlockPos>> worldMarkerPositions,
        double workMs,
        int placedBlocks,
        int resumedBlocks,
        int skippedAirBlocks
    ) {
        boolean terminal() {
            return complete || failed;
        }

        static AdvanceResult inProgress(ActivePlacement active) {
            return new AdvanceResult(
                false,
                false,
                "IN_PROGRESS_" + active.phase.name(),
                active.phase,
                Map.of(),
                active.workMillis(),
                active.placedBlocks,
                active.resumedBlocks,
                active.skippedAirBlocks
            );
        }

        static AdvanceResult complete(ActivePlacement active) {
            return new AdvanceResult(
                true,
                false,
                "INCREMENTAL_PLACEMENT_COMPLETE",
                Phase.COMPLETE,
                active.worldMarkerPositions,
                active.workMillis(),
                active.placedBlocks,
                active.resumedBlocks,
                active.skippedAirBlocks
            );
        }

        static AdvanceResult failed(String reason, Phase phase, double workMs) {
            return new AdvanceResult(
                false, true, reason, phase, Map.of(), workMs, 0, 0, 0
            );
        }
    }

    record ProgressSnapshot(
        boolean active,
        int roomIndex,
        Phase phase,
        long completedUnits,
        long totalUnits
    ) {
        ProgressSnapshot {
            phase = phase == null ? Phase.VALIDATE_TEMPLATE : phase;
            completedUnits = Math.max(0L, completedUnits);
            totalUnits = Math.max(0L, totalUnits);
            if (totalUnits > 0L) completedUnits = Math.min(completedUnits, totalUnits);
        }

        static ProgressSnapshot none() {
            return new ProgressSnapshot(false, 0, Phase.VALIDATE_TEMPLATE, 0L, 0L);
        }

        double fraction() {
            return !active || totalUnits <= 0L
                ? 0.0D
                : Math.min(1.0D, completedUnits / (double) totalUnits);
        }
    }

    private record OperationKey(
        ResourceLocation dimension,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        int roomIndex,
        BlockPos controllerPos
    ) {
        private OperationKey {
            controllerPos = controllerPos == null ? BlockPos.ZERO : controllerPos.immutable();
        }
    }

    private record StartResult(@Nullable ActivePlacement active, String reason) {
        private static StartResult success(ActivePlacement active) {
            return new StartResult(active, "STARTED");
        }

        private static StartResult failed(String reason) {
            return new StartResult(null, reason);
        }
    }

    private static final class ActivePlacement {
        private final GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan;
        private final PlacementRequestIdentity requestIdentity;
        private final StructurePlaceSettings settings;
        private final List<StructureTemplate.StructureBlockInfo> blocks;
        private final DungeonTemplateMechanicalMetadata metadata;
        private final Map<Long, String> expectedMarkersByLocalPosition;
        private final Set<Long> seenMarkerLocalPositions = new LinkedHashSet<>();
        private final Map<String, List<BlockPos>> worldMarkerPositions;
        private final IntArrayList solidIndices = new IntArrayList();
        private final IntArrayList fluidIndices = new IntArrayList();
        private final Set<Long> touchedChunks = new LinkedHashSet<>();
        private List<Long> touchedChunkOrder = List.of();
        private Phase phase = Phase.VALIDATE_TEMPLATE;
        @Nullable
        private Int2ObjectOpenHashMap<BlockState> observedExistingBlocks;
        private IntArrayList staleCells;
        private int cursor;
        private int reconcileCursor;
        private int syncCursor;
        private int countedNonAir;
        private int countedFluid;
        private int skippedAirBlocks;
        private int placedBlocks;
        private int resumedBlocks;
        private long workNanos;
        private String failureReason = "NONE";

        private ActivePlacement(
            GeneratedRoomPlacementOperation.Request request,
            StructureTemplate template,
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> blocks,
            DungeonTemplateMechanicalMetadata metadata,
            Map<Long, String> expectedMarkersByLocalPosition,
            Map<String, List<BlockPos>> worldMarkerPositions
        ) {
            this.roomPlan = request.roomPlan();
            this.requestIdentity = PlacementRequestIdentity.from(request);
            this.settings = settings;
            this.blocks = blocks;
            this.metadata = metadata;
            this.expectedMarkersByLocalPosition = expectedMarkersByLocalPosition;
            this.worldMarkerPositions = worldMarkerPositions;
        }

        private static StartResult start(
            ServerLevel level,
            GeneratedRoomPlacementOperation.Request request,
            StructureTemplate template,
            Rotation rotation,
            DungeonTemplateMechanicalMetadata metadata
        ) {
            if (template == null || rotation == null || metadata == null) {
                return StartResult.failed("INCREMENTAL_TEMPLATE_METADATA_MISSING");
            }
            GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan = request.roomPlan();
            if (!metadata.solverEligible()) {
                return StartResult.failed(
                    "INCREMENTAL_TEMPLATE_MECHANICALLY_INELIGIBLE_"
                        + String.join("_", metadata.rejectionReasons())
                );
            }
            if (metadata.entityCount() != 0) {
                return StartResult.failed("INCREMENTAL_TEMPLATE_ENTITIES_UNSUPPORTED");
            }
            if (template.getSize().getX() != metadata.width()
                || template.getSize().getY() != metadata.height()
                || template.getSize().getZ() != metadata.depth()) {
                return StartResult.failed("INCREMENTAL_TEMPLATE_SIZE_MISMATCH");
            }

            StructureTemplateAccessor accessor = (StructureTemplateAccessor) (Object) template;
            List<StructureTemplate.Palette> palettes = accessor.tensuraDungeon$getPalettes();
            if (palettes.size() != 1 || !accessor.tensuraDungeon$getEntityInfoList().isEmpty()) {
                return StartResult.failed("INCREMENTAL_TEMPLATE_PALETTE_OR_ENTITY_CONTRACT");
            }
            List<StructureTemplate.StructureBlockInfo> blocks = palettes.getFirst().blocks();
            if (blocks.size() != metadata.serializedBlocks()) {
                return StartResult.failed("INCREMENTAL_TEMPLATE_BLOCK_CENSUS_MISMATCH");
            }

            StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(rotation)
                .setIgnoreEntities(true)
                .setKnownShape(true);
            BoundingBox actualBounds = template.getBoundingBox(settings, roomPlan.placementOrigin());
            if (!sameBounds(actualBounds, roomPlan.bounds())) {
                return StartResult.failed("INCREMENTAL_TEMPLATE_BOUNDS_MISMATCH");
            }
            if (actualBounds.minY() < level.getMinBuildHeight()
                || actualBounds.maxY() >= level.getMaxBuildHeight()) {
                return StartResult.failed(
                    "INCREMENTAL_TEMPLATE_OUTSIDE_BUILD_HEIGHT_"
                        + actualBounds.minY() + "_" + actualBounds.maxY()
                );
            }

            Map<Long, String> expectedByLocal = new HashMap<>();
            Map<String, List<BlockPos>> worldMarkers = new LinkedHashMap<>();
            for (Map.Entry<String, List<BlockPos>> entry : metadata.markerPositions().entrySet()) {
                List<BlockPos> transformed = new ArrayList<>();
                for (BlockPos localPos : entry.getValue()) {
                    if (expectedByLocal.put(localPos.asLong(), entry.getKey()) != null) {
                        return StartResult.failed("INCREMENTAL_DUPLICATE_MARKER_POSITION");
                    }
                    BlockPos relative = StructureTemplate.calculateRelativePosition(settings, localPos);
                    transformed.add(roomPlan.placementOrigin().offset(relative).immutable());
                }
                worldMarkers.put(entry.getKey(), List.copyOf(transformed));
            }
            List<BlockPos> controllers = worldMarkers.getOrDefault("room_controller", List.of());
            if (controllers.size() != 1 || !controllers.getFirst().equals(roomPlan.controllerPos())) {
                return StartResult.failed("INCREMENTAL_CONTROLLER_MANIFEST_MISMATCH");
            }
            return StartResult.success(new ActivePlacement(
                request,
                template,
                settings,
                blocks,
                metadata,
                Map.copyOf(expectedByLocal),
                immutablePositionMap(worldMarkers)
            ));
        }

        private AdvanceResult advance(
            ServerLevel level,
            GeneratedRoomPlacementOperation.SliceBudget budget
        ) {
            long sliceStartedAt = System.nanoTime();
            SliceLimiter limiter = new SliceLimiter(sliceStartedAt, budget);
            try {
                while (true) {
                    boolean progressed = switch (phase) {
                        case VALIDATE_TEMPLATE -> validateTemplate(limiter);
                        case VALIDATE_CELL -> validateCell(level, limiter);
                        case VALIDATE_EXISTING -> validateExisting(level, limiter);
                        case CLEAR_STALE -> clearStale(level, limiter);
                        case PLACE_SOLIDS -> place(level, limiter, solidIndices, false);
                        case STAGE_FLUIDS -> stageFluids(level, limiter);
                        case PLACE_FLUIDS -> place(level, limiter, fluidIndices, true);
                        case RECONCILE_SOLIDS -> reconcile(level, limiter, solidIndices, Phase.RECONCILE_FLUIDS);
                        case RECONCILE_FLUIDS -> reconcile(level, limiter, fluidIndices, Phase.SYNC_WATCHED_CHUNKS);
                        case SYNC_WATCHED_CHUNKS -> syncWatchedChunks(level, limiter);
                        case COMPLETE, FAILED -> false;
                    };
                    if (phase == Phase.FAILED) {
                        workNanos += System.nanoTime() - sliceStartedAt;
                        return AdvanceResult.failed(failureReason, phase, workMillis());
                    }
                    if (phase == Phase.COMPLETE) {
                        workNanos += System.nanoTime() - sliceStartedAt;
                        return AdvanceResult.complete(this);
                    }
                    if (!progressed || limiter.exhausted()) {
                        workNanos += System.nanoTime() - sliceStartedAt;
                        return AdvanceResult.inProgress(this);
                    }
                }
            } catch (RuntimeException exception) {
                failureReason = "INCREMENTAL_EXCEPTION_"
                    + exception.getClass().getSimpleName() + "_"
                    + String.valueOf(exception.getMessage()).replace(' ', '_');
                phase = Phase.FAILED;
                workNanos += System.nanoTime() - sliceStartedAt;
                return AdvanceResult.failed(failureReason, phase, workMillis());
            }
        }

        private ProgressSnapshot progressSnapshot(int roomIndex) {
            long templateUnits = Math.max(1L, blocks.size());
            long cellUnits = Math.max(1L, metadata.volume());
            long existingUnits = templateUnits;
            long solidUnits = Math.max(
                0L,
                (long) metadata.nonAirBlocks() - metadata.fluidBearingBlocks()
            );
            long fluidUnits = Math.max(0L, metadata.fluidBearingBlocks());
            long stagedFluidUnits = fluidUnits;
            long placedFluidUnits = fluidUnits * FLUID_PROGRESS_WEIGHT;
            long reconciledSolidUnits = solidUnits;
            long reconciledFluidUnits = fluidUnits;
            long chunkUnits = Math.max(1L, touchedChunks.size())
                * CHUNK_SYNC_PROGRESS_WEIGHT;
            long beforeCell = templateUnits;
            long beforeExisting = beforeCell + cellUnits;
            long beforeSolids = beforeExisting + existingUnits;
            long beforeStageFluids = beforeSolids + solidUnits;
            long beforePlaceFluids = beforeStageFluids + stagedFluidUnits;
            long beforeReconcileSolids = beforePlaceFluids + placedFluidUnits;
            long beforeReconcileFluids = beforeReconcileSolids + reconciledSolidUnits;
            long beforeSync = beforeReconcileFluids + reconciledFluidUnits;
            long totalUnits = beforeSync + chunkUnits;
            long completedUnits = switch (phase) {
                case VALIDATE_TEMPLATE -> cursor;
                case VALIDATE_CELL -> beforeCell + cursor;
                case VALIDATE_EXISTING -> beforeExisting + cursor;
                case CLEAR_STALE -> beforeSolids;
                case PLACE_SOLIDS -> beforeSolids + cursor;
                case STAGE_FLUIDS -> beforeStageFluids + cursor;
                case PLACE_FLUIDS -> beforePlaceFluids
                    + (long) cursor * FLUID_PROGRESS_WEIGHT;
                case RECONCILE_SOLIDS -> beforeReconcileSolids + reconcileCursor;
                case RECONCILE_FLUIDS -> beforeReconcileFluids + reconcileCursor;
                case SYNC_WATCHED_CHUNKS -> beforeSync
                    + (long) syncCursor * CHUNK_SYNC_PROGRESS_WEIGHT;
                case COMPLETE -> totalUnits;
                case FAILED -> 0L;
            };
            return new ProgressSnapshot(
                phase != Phase.FAILED,
                roomIndex,
                phase,
                completedUnits,
                totalUnits
            );
        }

        private boolean validateTemplate(SliceLimiter limiter) {
            while (cursor < blocks.size()) {
                if (!limiter.tryBlockOperation()) return false;
                StructureTemplate.StructureBlockInfo info = blocks.get(cursor++);
                BlockPos localPos = info.pos();
                if (isUncompiledCompositeAuthoringMarker(info.state())) {
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                        info.state().getBlock()
                    );
                    fail("INCREMENTAL_UNCOMPILED_COMPOSITE_MARKER_" + blockId.getPath());
                    return false;
                }
                if (!insideTemplate(localPos)) {
                    fail("INCREMENTAL_LOCAL_POSITION_OUT_OF_BOUNDS_" + localPos.toShortString());
                    return false;
                }
                BlockPos worldPos = worldPosition(localPos);
                if (!roomPlan.bounds().contains(worldPos)) {
                    fail("INCREMENTAL_WORLD_POSITION_OUT_OF_BOUNDS_" + worldPos.toShortString());
                    return false;
                }
                BlockState expected = transformedState(info.state());
                String markerName = expectedMarkersByLocalPosition.get(localPos.asLong());
                if (markerName != null) {
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(info.state().getBlock());
                    if (!TensuraDungeonRuntime.MOD_ID.equals(blockId.getNamespace())
                        || !markerName.equals(blockId.getPath())) {
                        fail("INCREMENTAL_MARKER_NBT_MISMATCH_" + markerName);
                        return false;
                    }
                    seenMarkerLocalPositions.add(localPos.asLong());
                }
                if (isSerializedAir(expected) || expected.is(Blocks.STRUCTURE_VOID)) continue;

                countedNonAir++;
                touchedChunks.add(ChunkPos.asLong(worldPos));
                boolean fluidBearing = !expected.getFluidState().isEmpty();
                if (fluidBearing) {
                    countedFluid++;
                    fluidIndices.add(cursor - 1);
                } else {
                    solidIndices.add(cursor - 1);
                }
            }

            if (countedNonAir != metadata.nonAirBlocks()
                || countedFluid != metadata.fluidBearingBlocks()
                || seenMarkerLocalPositions.size() != expectedMarkersByLocalPosition.size()) {
                fail("INCREMENTAL_RUNTIME_CENSUS_MISMATCH");
                return false;
            }
            skippedAirBlocks = Math.toIntExact(metadata.volume() - countedNonAir);
            cursor = 0;
            phase = Phase.VALIDATE_CELL;
            return true;
        }

        private boolean validateCell(ServerLevel level, SliceLimiter limiter) {
            while ((long) cursor < metadata.volume()) {
                if (!limiter.tryBlockOperation()) return false;
                int cellIndex = cursor++;
                BlockPos worldPos = worldPositionForCellIndex(cellIndex);
                BlockState current = level.getBlockState(worldPos);
                if (isSerializedAir(current)) continue;
                if (roomPlanAllowsBuildOrigin(worldPos)
                    && current.is(ModBlocks.FLOOR_BUILD_ORIGIN.get())) {
                    continue;
                }
                if (observedExistingBlocks == null) {
                    observedExistingBlocks = new Int2ObjectOpenHashMap<>();
                }
                observedExistingBlocks.put(cellIndex, current);
            }
            cursor = 0;
            phase = Phase.VALIDATE_EXISTING;
            return true;
        }

        private boolean validateExisting(ServerLevel level, SliceLimiter limiter) {
            if (observedExistingBlocks != null && !observedExistingBlocks.isEmpty()) {
                while (cursor < blocks.size()) {
                    if (!limiter.tryBlockOperation()) return false;
                    StructureTemplate.StructureBlockInfo info = blocks.get(cursor++);
                    BlockState expected = transformedState(info.state());
                    if (isSerializedAir(expected) || expected.is(Blocks.STRUCTURE_VOID)) continue;
                    BlockPos worldPos = worldPosition(info.pos());
                    int existingCell = cellIndex(worldPos);
                    BlockState existing = observedExistingBlocks.remove(existingCell);
                    if (existing == null) continue;
                    boolean stagedFluidPlaceholder = !expected.getFluidState().isEmpty()
                        && existing.is(Blocks.BARRIER);
                    if (!stagedFluidPlaceholder
                        && !matchesExpectedState(level, worldPos, existing, expected)) {
                        staleCell(existingCell);
                        continue;
                    }
                    resumedBlocks++;
                }
                for (int leftover : observedExistingBlocks.keySet()) {
                    staleCell(leftover);
                }
                observedExistingBlocks = null;
            }
            cursor = 0;
            if (staleCells != null && !staleCells.isEmpty()) {
                LogUtils.getLogger().warn(
                    "Clearing {} stale blocks before generated room placement at {}",
                    staleCells.size(), worldPositionForCellIndex(staleCells.getInt(0)).toShortString()
                );
                phase = Phase.CLEAR_STALE;
                return true;
            }
            return beginPlacement(level);
        }

        private void staleCell(int cellIndex) {
            if (staleCells == null) {
                staleCells = new IntArrayList();
            }
            staleCells.add(cellIndex);
        }

        private boolean clearStale(ServerLevel level, SliceLimiter limiter) {
            while (cursor < staleCells.size()) {
                if (!limiter.tryBlockOperation()) return false;
                BlockPos worldPos = worldPositionForCellIndex(staleCells.getInt(cursor++));
                Clearable.tryClear(level.getBlockEntity(worldPos));
                level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
            }
            staleCells = null;
            cursor = 0;
            return beginPlacement(level);
        }

        private boolean beginPlacement(ServerLevel level) {
            BlockPos markerToClear = currentRequestBuildOrigin();
            if (markerToClear != null
                && level.getBlockState(markerToClear).is(ModBlocks.FLOOR_BUILD_ORIGIN.get())) {
                level.setBlock(markerToClear, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
            }
            cursor = 0;
            phase = Phase.PLACE_SOLIDS;
            return true;
        }

        private boolean stageFluids(ServerLevel level, SliceLimiter limiter) {
            while (cursor < fluidIndices.size()) {
                if (!limiter.tryBlockOperation()) return false;
                StructureTemplate.StructureBlockInfo info =
                    blocks.get(fluidIndices.getInt(cursor++));
                BlockPos worldPos = worldPosition(info.pos());
                BlockState expected = transformedState(info.state());
                BlockState current = level.getBlockState(worldPos);
                if (current.is(Blocks.BARRIER)
                    || current.equals(expected)
                    || (!isSerializedAir(current)
                        && matchesExpectedState(level, worldPos, current, expected))) {
                    continue;
                }
                if (!isSerializedAir(current)) {
                    fail("INCREMENTAL_FLUID_STAGING_CONFLICT_" + worldPos.toShortString());
                    return false;
                }
                boolean changed = level.setBlock(
                    worldPos, Blocks.BARRIER.defaultBlockState(), PLACE_FLAGS
                );
                if (!changed && !level.getBlockState(worldPos).is(Blocks.BARRIER)) {
                    fail("INCREMENTAL_FLUID_STAGING_FAILED_" + worldPos.toShortString());
                    return false;
                }
            }
            cursor = 0;
            phase = Phase.PLACE_FLUIDS;
            return true;
        }

        private boolean place(
            ServerLevel level,
            SliceLimiter limiter,
            IntArrayList indices,
            boolean fluidPhase
        ) {
            while (cursor < indices.size()) {
                if (fluidPhase) {
                    if (!limiter.tryFluidOperation()) return false;
                } else if (!limiter.tryBlockOperation()) {
                    return false;
                }
                StructureTemplate.StructureBlockInfo info = blocks.get(indices.getInt(cursor++));
                BlockPos worldPos = worldPosition(info.pos());
                BlockState expected = transformedState(info.state());
                BlockState current = level.getBlockState(worldPos);
                boolean needsNbt = info.nbt() != null && !expected.is(ModBlocks.ROOM_CONTROLLER.get());
                boolean matchesExpected = current.equals(expected)
                    || (!isSerializedAir(current)
                        && matchesExpectedState(level, worldPos, current, expected));
                if (matchesExpected && !needsNbt) {
                    continue;
                }
                boolean allowedBuildOrigin = roomPlanAllowsBuildOrigin(worldPos)
                    && current.is(ModBlocks.FLOOR_BUILD_ORIGIN.get());
                boolean stagedFluidPlaceholder = fluidPhase && current.is(Blocks.BARRIER);
                if (!isSerializedAir(current) && !matchesExpected && !allowedBuildOrigin
                    && !stagedFluidPlaceholder) {
                    fail("INCREMENTAL_PLACEMENT_CONFLICT_" + worldPos.toShortString());
                    return false;
                }

                if (needsNbt) {
                    Clearable.tryClear(level.getBlockEntity(worldPos));
                    level.setBlock(worldPos, Blocks.BARRIER.defaultBlockState(), PLACE_FLAGS);
                }
                boolean changed = level.setBlock(worldPos, expected, PLACE_FLAGS);
                if (!changed && !level.getBlockState(worldPos).equals(expected)) {
                    fail("INCREMENTAL_SET_BLOCK_FAILED_" + worldPos.toShortString());
                    return false;
                }
                placedBlocks++;
                if (needsNbt) {
                    BlockEntity blockEntity = level.getBlockEntity(worldPos);
                    if (blockEntity == null) {
                        fail("INCREMENTAL_BLOCK_ENTITY_MISSING_" + worldPos.toShortString());
                        return false;
                    }
                    var tag = info.nbt().copy();
                    tag.putInt("x", worldPos.getX());
                    tag.putInt("y", worldPos.getY());
                    tag.putInt("z", worldPos.getZ());
                    if (blockEntity instanceof RandomizableContainer) {
                        tag.putLong("LootTableSeed", level.getRandom().nextLong());
                    }
                    blockEntity.loadWithComponents(tag, level.registryAccess());
                    blockEntity.setChanged();
                }
            }
            cursor = 0;
            phase = fluidPhase ? Phase.RECONCILE_SOLIDS : Phase.STAGE_FLUIDS;
            return true;
        }

        private boolean reconcile(
            ServerLevel level,
            SliceLimiter limiter,
            IntArrayList indices,
            Phase nextPhase
        ) {
            while (reconcileCursor < indices.size()) {
                if (!limiter.tryBlockOperation()) return false;
                StructureTemplate.StructureBlockInfo info =
                    blocks.get(indices.getInt(reconcileCursor++));
                BlockPos worldPos = worldPosition(info.pos());
                BlockState current = level.getBlockState(worldPos);
                BlockState reconciled = Block.updateFromNeighbourShapes(current, level, worldPos);
                if (!current.equals(reconciled)) {
                    level.setBlock(worldPos, reconciled, PLACE_FLAGS);
                }
                level.blockUpdated(worldPos, reconciled.getBlock());
            }
            reconcileCursor = 0;
            phase = nextPhase;
            if (phase == Phase.SYNC_WATCHED_CHUNKS) {
                touchedChunkOrder = List.copyOf(touchedChunks);
            }
            return true;
        }

        private boolean syncWatchedChunks(ServerLevel level, SliceLimiter limiter) {
            while (syncCursor < touchedChunkOrder.size()) {
                if (!limiter.tryChunkSync()) return false;
                ChunkPos chunkPos = new ChunkPos(touchedChunkOrder.get(syncCursor++));
                List<ServerPlayer> watchers =
                    level.getChunkSource().chunkMap.getPlayers(chunkPos, false);
                if (watchers.isEmpty()) continue;
                ClientboundLevelChunkWithLightPacket packet =
                    new ClientboundLevelChunkWithLightPacket(
                        level.getChunk(chunkPos.x, chunkPos.z),
                        level.getChunkSource().getLightEngine(),
                        null,
                        null
                    );
                for (ServerPlayer watcher : watchers) {
                    watcher.connection.send(packet);
                }
            }
            phase = Phase.COMPLETE;
            return true;
        }

        private BlockPos worldPosition(BlockPos localPos) {
            BlockPos relative = StructureTemplate.calculateRelativePosition(settings, localPos);
            return roomPlan.placementOrigin().offset(relative);
        }

        private BlockState transformedState(BlockState state) {
            return state.mirror(settings.getMirror()).rotate(settings.getRotation());
        }

        private static boolean isSerializedAir(BlockState state) {
            return state.is(Blocks.AIR)
                || state.is(Blocks.CAVE_AIR)
                || state.is(Blocks.VOID_AIR);
        }

        private static boolean isUncompiledCompositeAuthoringMarker(BlockState state) {
            return state.is(ModBlocks.ROLE_SOCKET.get())
                || state.is(ModBlocks.SEGMENT_CONNECTOR.get())
                || state.is(ModBlocks.SEGMENT_CONNECTOR_INTERNAL.get())
                || state.is(ModBlocks.SEGMENT_CONNECTOR_EXTERNAL.get());
        }

        private static boolean matchesExpectedState(
            ServerLevel level,
            BlockPos worldPos,
            BlockState current,
            BlockState expected
        ) {
            if (current.equals(expected)) return true;
            BlockState reconciledExpected = Block.updateFromNeighbourShapes(
                expected, level, worldPos
            );
            return current.equals(reconciledExpected);
        }

        private BlockPos worldPositionForCellIndex(int cellIndex) {
            int width = roomPlan.bounds().getWidth();
            int depth = roomPlan.bounds().getDepth();
            int xOffset = cellIndex % width;
            int yzOffset = cellIndex / width;
            int zOffset = yzOffset % depth;
            int yOffset = yzOffset / depth;
            return roomPlan.bounds().min().offset(xOffset, yOffset, zOffset);
        }

        private int cellIndex(BlockPos worldPos) {
            int xOffset = worldPos.getX() - roomPlan.bounds().min().getX();
            int yOffset = worldPos.getY() - roomPlan.bounds().min().getY();
            int zOffset = worldPos.getZ() - roomPlan.bounds().min().getZ();
            return (yOffset * roomPlan.bounds().getDepth() + zOffset)
                * roomPlan.bounds().getWidth() + xOffset;
        }

        private boolean insideTemplate(BlockPos pos) {
            return pos.getX() >= 0 && pos.getX() < metadata.width()
                && pos.getY() >= 0 && pos.getY() < metadata.height()
                && pos.getZ() >= 0 && pos.getZ() < metadata.depth();
        }

        private boolean roomPlanAllowsBuildOrigin(@Nullable BlockPos worldPos) {
            BlockPos marker = currentRequestBuildOrigin();
            return marker != null && (worldPos == null || marker.equals(worldPos));
        }

        @Nullable
        private BlockPos currentRequestBuildOrigin() {
            return requestIdentity.preClaimMarkerToClear();
        }

        private boolean matches(GeneratedRoomPlacementOperation.Request request) {
            return requestIdentity.equals(PlacementRequestIdentity.from(request));
        }

        private void fail(String reason) {
            failureReason = reason;
            phase = Phase.FAILED;
        }

        private double workMillis() {
            return workNanos / 1_000_000.0D;
        }
    }

    private record PlacementRequestIdentity(
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan,
        int claimRadius,
        boolean branchPreview,
        GeneratedRoomPlacementOperation.SealPolicy sealPolicy,
        GeneratedRoomPlacementOperation.ControllerDiscoveryPolicy controllerDiscovery,
        boolean allowExistingController,
        boolean persistBoundsBeforeMutation,
        boolean persistControllerOnReadinessFailure,
        boolean persistControllerOnSuccess,
        @Nullable BlockPos preClaimMarkerToClear
    ) {
        private PlacementRequestIdentity {
            if (preClaimMarkerToClear != null) {
                preClaimMarkerToClear = preClaimMarkerToClear.immutable();
            }
        }

        private static PlacementRequestIdentity from(
            GeneratedRoomPlacementOperation.Request request
        ) {
            return new PlacementRequestIdentity(
                request.roomPlan(),
                request.claimRadius(),
                request.branchPreview(),
                request.sealPolicy(),
                request.controllerDiscovery(),
                request.allowExistingController(),
                request.persistBoundsBeforeMutation(),
                request.persistControllerOnReadinessFailure(),
                request.persistControllerOnSuccess(),
                request.preClaimMarkerToClear()
            );
        }
    }

    private static boolean sameBounds(
        BoundingBox actual,
        com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity.RoomBounds expected
    ) {
        return actual.minX() == expected.min().getX()
            && actual.minY() == expected.min().getY()
            && actual.minZ() == expected.min().getZ()
            && actual.maxX() == expected.max().getX()
            && actual.maxY() == expected.max().getY()
            && actual.maxZ() == expected.max().getZ();
    }

    private static Map<String, List<BlockPos>> immutablePositionMap(
        Map<String, List<BlockPos>> positions
    ) {
        Map<String, List<BlockPos>> copied = new LinkedHashMap<>();
        positions.forEach((name, values) -> copied.put(
            name, values.stream().map(BlockPos::immutable).toList()
        ));
        return Map.copyOf(copied);
    }

    private static final class SliceLimiter {
        private final long deadlineNanos;
        private final int maxBlockOperations;
        private final int maxFluidOperations;
        private final int maxChunkSyncs;
        private int blockOperations;
        private int fluidOperations;
        private int chunkSyncs;

        private SliceLimiter(
            long startedAt,
            GeneratedRoomPlacementOperation.SliceBudget budget
        ) {
            long maxNanos = budget.maxNanos();
            this.deadlineNanos = maxNanos == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : startedAt + Math.max(1L, maxNanos);
            this.maxBlockOperations = budget.maxBlockOperations();
            this.maxFluidOperations = budget.maxFluidOperations();
            this.maxChunkSyncs = budget.maxChunkSyncs();
        }

        private boolean tryBlockOperation() {
            if (blockOperations >= maxBlockOperations || timeExpired()) return false;
            blockOperations++;
            return true;
        }

        private boolean tryFluidOperation() {
            if (fluidOperations >= maxFluidOperations || !tryBlockOperation()) return false;
            fluidOperations++;
            return true;
        }

        private boolean tryChunkSync() {
            if (chunkSyncs >= maxChunkSyncs || timeExpired()) return false;
            chunkSyncs++;
            return true;
        }

        private boolean exhausted() {
            return blockOperations >= maxBlockOperations
                || fluidOperations >= maxFluidOperations
                || chunkSyncs >= maxChunkSyncs
                || timeExpired();
        }

        private boolean timeExpired() {
            return deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos;
        }
    }

    static DungeonTemplateMechanicalMetadata loadMetadata(
        ServerLevel level,
        GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan roomPlan
    ) {
        if (CompositeTemplateDescriptor.isCompositeId(roomPlan.templateLabel())) {
            CompositeRoomRuntimeRegistry.Resolution composite =
                CompositeRoomRuntimeRegistry.resolve(level, roomPlan.templateLabel());
            return composite.success() ? composite.mechanical() : null;
        }
        var authored = DungeonTemplateMetadataRegistry.get(level);
        return DungeonTemplateMechanicalRegistry.getForTemplateIds(
            level, authored, List.of(roomPlan.templateLabel())
        ).templates().get(roomPlan.templateLabel());
    }
}
