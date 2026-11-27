package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.block.BarrierAnchorBlock;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity.RoomBounds;
import com.mineclawd.tensuradungeon.compat.VisualBarrierHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

final class RoomBarrierController {
    static final BlockState BARRIER_PREVIEW_BLOCK_STATE = Blocks.BLUE_STAINED_GLASS.defaultBlockState();

    private static final int BARRIER_MAX_WIDTH = 11;
    private static final int BARRIER_MAX_HEIGHT = 9;
    private static final int BOUNDED_BARRIER_MAX_XZ = 40;
    private static final int BOUNDED_BARRIER_MAX_HEIGHT = 30;
    private static final int BOUNDED_BARRIER_MAX_DEPTH = 12;
    private static final int BOUNDED_BARRIER_MAX_TOTAL_PLACED = 1200;
    private static final int TEMPORARY_SEAL_MAX_TOTAL_PLACED = 16384;

    private final Runnable markChanged;
    private final BarrierBackendChain backends;

    private final Set<BlockPos> placedBarrierPositions = new HashSet<>();
    private final List<VisualBarrierHandle> placedVisualBarrierHandles = new ArrayList<>();
    private final Set<BlockPos> temporarySealPositions = new HashSet<>();
    private final Map<BlockPos, Set<BlockPos>> temporarySealGroups = new LinkedHashMap<>();
    private final Map<BlockPos, VisualBarrierHandle> temporaryVisualSealHandles = new LinkedHashMap<>();
    private final Set<BlockPos> barrierPreviewPositions = new HashSet<>();
    String lastBarrierMaterialSummary = "NONE";
    String lastBarrierSolveMode = "NONE";
    String lastBarrierSolveResult = "NONE";
    String lastBarrierSolveReason = "NONE";
    String lastBarrierPlaneSummary = "NONE";
    String lastBarrierVolumeBoundsSummary = "NONE";
    String lastBarrierVolumeSizeSummary = "NONE";
    int lastBarrierFillCandidateCount;
    int lastBarrierSolvedWidth;
    int lastBarrierSolvedHeight;
    int lastBarrierSolvedPositionCount;
    int lastBarrierPlacedBlockCount;
    int lastBarrierSkippedBlockCount;
    int lastBarrierClearedBlockCount;
    final List<String> lastBarrierDebugLines = new ArrayList<>();

    RoomBarrierController(Runnable markChanged) {
        this(markChanged, BarrierBackendChain.production());
    }

    RoomBarrierController(Runnable markChanged, BarrierBackendChain backends) {
        this.markChanged = markChanged;
        this.backends = backends;
    }

    void closeBarrier(ServerLevel level, Inputs inputs) {
        openBarrier(level);
        String barrierType = gameplayBarrierType(inputs);
        BarrierSolvePlan plan = solveBarrierPlan(level, inputs);
        applyBarrierPlanMetrics(plan, backends.preferredMaterialSummary(barrierType), false);

        if (!placedVisualBarrierHandles.isEmpty()) {
            lastBarrierPlacedBlockCount = 0;
            lastBarrierSolveResult = "FAIL";
            lastBarrierSolveReason = "PERSISTED_VISUAL_BARRIER_OWNERSHIP_UNRESOLVED";
            appendBarrierDebugLines(plan, 0, lastBarrierSkippedBlockCount, lastBarrierClearedBlockCount);
            return;
        }

        int visualPanels = createVisualBarriers(level, plan, barrierType);
        if (visualPanels > 0) {
            lastBarrierPlacedBlockCount = visualPanels;
            lastBarrierSolveResult = plan.unpairedAnchors().isEmpty() ? "PASS" : lastBarrierSolveResult;
            appendBarrierDebugLines(plan, visualPanels, lastBarrierSkippedBlockCount, lastBarrierClearedBlockCount);
            markChanged.run();
            return;
        }

        placedBarrierPositions.clear();
        if (!plan.planes().isEmpty()) {
            lastBarrierSolveResult = "FAIL";
            lastBarrierSolveReason = "FOG_RUNE_BACKEND_UNAVAILABLE";
        }
        appendBarrierDebugLines(plan, 0, lastBarrierSkippedBlockCount, lastBarrierClearedBlockCount);
    }

    boolean placeBarrierPreview(ServerLevel level, Inputs inputs) {
        clearBarrierPreview(level);
        BarrierSolvePlan plan = solveBarrierPlan(level, inputs);
        applyBarrierPlanMetrics(
            plan,
            backends.preferredMaterialSummary(gameplayBarrierType(inputs)),
            true
        );
        boolean placedAny = false;
        int skippedBlocks = 0;
        for (BarrierPlaneSolveResult solved : plan.planes()) {
            for (BlockPos previewPos : solved.positions()) {
                BlockState current = level.getBlockState(previewPos);
                if (!current.isAir() && !current.is(BARRIER_PREVIEW_BLOCK_STATE.getBlock()) && !isRuntimeBarrierBlockState(current)) {
                    skippedBlocks++;
                    continue;
                }
                if (!containsWithEdgeTolerance(getResolvedBarrierRoomBounds(inputs), previewPos, 1)) {
                    skippedBlocks++;
                    continue;
                }
                level.setBlock(previewPos, BARRIER_PREVIEW_BLOCK_STATE, 3);
                barrierPreviewPositions.add(previewPos.immutable());
                placedAny = true;
            }
        }
        lastBarrierPlacedBlockCount = barrierPreviewPositions.size();
        lastBarrierSkippedBlockCount += skippedBlocks;
        lastBarrierSolveMode = lastBarrierSolveMode.equals("NONE") ? "NONE" : lastBarrierSolveMode + "_PREVIEW";
        lastBarrierSolveResult = placedAny && plan.unpairedAnchors().isEmpty() ? "PASS" : lastBarrierSolveResult;
        appendBarrierDebugLines(plan, lastBarrierPlacedBlockCount, lastBarrierSkippedBlockCount, lastBarrierClearedBlockCount);
        if (placedAny) {
            markChanged.run();
        }
        return placedAny || getRuntimeBarrierPositionCount(level, inputs) > 0;
    }

    boolean clearBarrierPreview(ServerLevel level) {
        boolean clearedAny = false;
        for (BlockPos previewPos : barrierPreviewPositions) {
            if (level.getBlockState(previewPos).is(BARRIER_PREVIEW_BLOCK_STATE.getBlock())) {
                level.removeBlock(previewPos, false);
                clearedAny = true;
            }
        }
        barrierPreviewPositions.clear();
        if (clearedAny) {
            markChanged.run();
        }
        return clearedAny;
    }

    void openBarrier(ServerLevel level) {
        int cleared = 0;
        int originalHandleCount = placedVisualBarrierHandles.size();
        List<VisualBarrierHandle> retained = new ArrayList<>();
        for (VisualBarrierHandle handle : placedVisualBarrierHandles) {
            VisualBarrierHandle.RemovalResult result = handle.remove();
            if (result == VisualBarrierHandle.RemovalResult.REMOVED) cleared++;
            if (result == VisualBarrierHandle.RemovalResult.UNAVAILABLE) retained.add(handle);
            if (result == VisualBarrierHandle.RemovalResult.UNRESOLVED) retained.add(handle);
        }
        placedVisualBarrierHandles.clear();
        placedVisualBarrierHandles.addAll(retained);
        for (BlockPos barrierPos : placedBarrierPositions) {
            if (isRuntimeBarrierBlockState(level.getBlockState(barrierPos))) {
                level.removeBlock(barrierPos, false);
                cleared++;
            }
        }
        placedBarrierPositions.clear();
        lastBarrierClearedBlockCount = cleared;
        if (cleared > 0 || retained.size() != originalHandleCount) {
            markChanged.run();
        }
    }
    private int createVisualBarriers(ServerLevel level, BarrierSolvePlan plan, String type) {
        List<BarrierBackendChain.VisualPanel> panels = new ArrayList<>();
        for (BarrierPlaneSolveResult solved : plan.planes()) {
            Vec3[] corners = visualPanelCorners(solved);
            if (corners != null) {
                panels.add(new BarrierBackendChain.VisualPanel(corners[0], corners[1]));
            }
        }
        BarrierBackendChain.VisualPlacement placement = backends.createVisuals(level, panels, type);
        if (!placement.handles().isEmpty()) {
            placedVisualBarrierHandles.addAll(placement.handles());
            lastBarrierMaterialSummary = placement.materialSummary();
        }
        return placement.handles().size();
    }

    private Vec3[] visualPanelCorners(BarrierPlaneSolveResult solved) {
        if (solved.positions().isEmpty()) return null;
        if ("BOUNDED_VOLUME".equals(solved.planeSummary()) && solved.anchorA() != null && solved.anchorB() != null) {
            int minX = Math.min(solved.anchorA().getX(), solved.anchorB().getX());
            int minY = Math.min(solved.anchorA().getY(), solved.anchorB().getY());
            int minZ = Math.min(solved.anchorA().getZ(), solved.anchorB().getZ());
            int maxX = Math.max(solved.anchorA().getX(), solved.anchorB().getX());
            int maxY = Math.max(solved.anchorA().getY(), solved.anchorB().getY());
            int maxZ = Math.max(solved.anchorA().getZ(), solved.anchorB().getZ());
            return panelCornersForBounds(minX, minY, minZ, maxX, maxY, maxZ, normalAxisFor(solved));
        }
        return visualPanelCorners(solved.positions(), normalAxisFor(solved));
    }

    private Vec3[] visualPanelCorners(List<BlockPos> positions, Direction.Axis fallbackNormal) {
        if (positions == null || positions.isEmpty()) return null;
        int minX = positions.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int minY = positions.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxX = positions.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int maxY = positions.stream().mapToInt(BlockPos::getY).max().orElse(0);
        int maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        Direction.Axis normal = sizeX < sizeZ ? Direction.Axis.X
            : sizeZ < sizeX ? Direction.Axis.Z : fallbackNormal;
        return panelCornersForBounds(minX, minY, minZ, maxX, maxY, maxZ, normal);
    }

    private Vec3[] panelCornersForBounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        Direction.Axis normal
    ) {
        if (normal == Direction.Axis.X) {
            double plane = (minX + maxX + 1.0D) * 0.5D;
            return new Vec3[] {
                new Vec3(plane, minY, minZ),
                new Vec3(plane, maxY + 1.0D, maxZ + 1.0D)
            };
        }
        double plane = (minZ + maxZ + 1.0D) * 0.5D;
        return new Vec3[] {
            new Vec3(minX, minY, plane),
            new Vec3(maxX + 1.0D, maxY + 1.0D, plane)
        };
    }

    private Direction.Axis normalAxisFor(BarrierPlaneSolveResult solved) {
        if ("BOUNDED_VOLUME".equals(solved.planeSummary())) {
            if (solved.sizeX() < solved.sizeZ()) return Direction.Axis.X;
            if (solved.sizeZ() < solved.sizeX()) return Direction.Axis.Z;
        }
        return solved.facing().getAxis();
    }

    private BlockState orientBarrierState(BlockState state, Direction.Axis normal) {
        if ((normal == Direction.Axis.X || normal == Direction.Axis.Z)
            && state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_AXIS, normal);
        }
        return state;
    }
    String gameplayBarrierType(Inputs inputs) {
        if (inputs.profile() == RoomProfile.SAFE_ROOM) return "dungeon_safe";
        if (inputs.profile() == RoomProfile.BOSS_CAPABLE || inputs.hasSpawnBossAnchor()) return "dungeon_boss";
        if (inputs.activeObjectiveType() == RoomObjectiveType.KILL_ELITE) return "dungeon_elite";
        if (!inputs.objectiveDisabled()) return "dungeon_objective";
        if (inputs.profileAllowsCombat()) return "dungeon_combat";
        return "dungeon_progression";
    }

    boolean placeTemporarySeal(ServerLevel level, BlockPos connectorPos, boolean blackConcrete, Inputs inputs) {
        BarrierSolvePlan plan = solveBarrierPlan(level, inputs);
        BarrierPlaneSolveResult selected = blackConcrete ? null : plan.planes().stream()
            .min(Comparator.comparingDouble(plane -> planeCenterDistanceSqr(plane, connectorPos)))
            .orElse(null);
        List<BlockPos> sealPositions = selected == null
            ? solveConnectorSealPlane(level, connectorPos, inputs)
            : selected.positions();
        BlockPos connectorKey = connectorPos.immutable();

        VisualBarrierHandle previousVisual = temporaryVisualSealHandles.get(connectorKey);
        boolean visualBackendUnavailable = false;
        boolean ownershipChanged = false;
        if (previousVisual != null) {
            VisualBarrierHandle.RemovalResult removal = previousVisual.remove();
            visualBackendUnavailable = removal == VisualBarrierHandle.RemovalResult.UNAVAILABLE
                || removal == VisualBarrierHandle.RemovalResult.UNRESOLVED;
            if (!visualBackendUnavailable) {
                temporaryVisualSealHandles.remove(connectorKey);
                ownershipChanged = true;
            }
        }

        Set<BlockPos> previousBlocks = temporarySealGroups.remove(connectorKey);
        if (previousBlocks != null) {
            clearOwnedSealPositions(level, Set.copyOf(previousBlocks));
            temporarySealPositions.removeAll(previousBlocks);
            ownershipChanged = true;
        }

        RoomBounds resolvedBounds = getResolvedBarrierRoomBounds(inputs);
        Direction.Axis connectorNormal = resolvedBounds == null
            ? normalAxisForPositions(sealPositions, Direction.Axis.Z)
            : inferConnectorNormal(connectorPos, resolvedBounds).getAxis();
        if (!blackConcrete) {
            if (visualBackendUnavailable) {
                if (ownershipChanged) markChanged.run();
                return true;
            }
            if (!sealPositions.isEmpty()) {
                Vec3[] corners = selected == null
                    ? visualPanelCorners(sealPositions, connectorNormal)
                    : visualPanelCorners(selected);
                if (corners != null) {
                    VisualBarrierHandle visual = backends.createPreferredVisual(
                        level, corners[0], corners[1], "dungeon_frontier"
                    );
                    if (visual != null) {
                        temporaryVisualSealHandles.put(connectorKey, visual);
                        markChanged.run();
                        return true;
                    }
                }
            }
            if (ownershipChanged) markChanged.run();
            return false;
        }

        BlockState baseSealState = Blocks.BLACK_CONCRETE.defaultBlockState();
        Set<BlockPos> ownedGroup = temporarySealGroups.computeIfAbsent(connectorKey, ignored -> new HashSet<>());
        Direction.Axis normal = selected == null
            ? normalAxisForPositions(sealPositions, connectorNormal)
            : normalAxisFor(selected);
        BlockState sealState = orientBarrierState(baseSealState, normal);
        int placedThisCall = placeOwnedSealPositions(level, sealPositions, sealState, ownedGroup, inputs);
        if (placedThisCall == 0 && selected != null) {
            sealPositions = solveConnectorSealPlane(level, connectorPos, inputs);
            sealState = orientBarrierState(baseSealState, normalAxisForPositions(sealPositions, connectorNormal));
            placedThisCall = placeOwnedSealPositions(level, sealPositions, sealState, ownedGroup, inputs);
        }
        if (placedThisCall == 0) {
            temporarySealGroups.remove(connectorKey);
            if (ownershipChanged) markChanged.run();
            return false;
        }
        markChanged.run();
        return true;
    }

    private Direction.Axis normalAxisForPositions(List<BlockPos> positions, Direction.Axis fallbackNormal) {
        if (positions == null || positions.isEmpty()) return fallbackNormal;
        int minX = positions.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxX = positions.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        return sizeX < sizeZ ? Direction.Axis.X : sizeZ < sizeX ? Direction.Axis.Z : fallbackNormal;
    }
    private int placeOwnedSealPositions(
        ServerLevel level,
        List<BlockPos> positions,
        BlockState sealState,
        Set<BlockPos> ownedGroup,
        Inputs inputs
    ) {
        int placed = 0;
        for (BlockPos pos : positions) {
            BlockState current = level.getBlockState(pos);
            if (!isBarrierFillCandidate(current)
                || !containsWithEdgeTolerance(getResolvedBarrierRoomBounds(inputs), pos, 1)) {
                continue;
            }
            level.setBlock(pos, sealState, 3);
            BlockPos ownedPos = pos.immutable();
            temporarySealPositions.add(ownedPos);
            ownedGroup.add(ownedPos);
            placed++;
        }
        return placed;
    }

    private List<BlockPos> solveConnectorSealPlane(ServerLevel level, BlockPos connectorPos, Inputs inputs) {
        RoomBounds bounds = getResolvedBarrierRoomBounds(inputs);
        if (bounds == null) return List.of();
        Direction normal = inferConnectorNormal(connectorPos, bounds);
        Direction lateral = normal.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        int minLateral = lateral.getAxis() == Direction.Axis.X ? bounds.min().getX() - 1 : bounds.min().getZ() - 1;
        int maxLateral = lateral.getAxis() == Direction.Axis.X ? bounds.max().getX() + 1 : bounds.max().getZ() + 1;
        int minY = bounds.min().getY() - 1;
        int maxY = bounds.max().getY() + 1;

        BlockPos seed = null;
        int seedDistance = Integer.MAX_VALUE;
        int connectorLateral = lateralCoordinate(connectorPos, lateral);
        for (int lateralValue = minLateral; lateralValue <= maxLateral; lateralValue++) {
            for (int y = minY; y <= maxY; y++) {
                BlockPos candidate = connectorPlanePos(connectorPos, lateral, lateralValue, y);
                if (!isBarrierFillCandidate(level.getBlockState(candidate))) continue;
                int distance = Math.abs(lateralValue - connectorLateral) + Math.abs(y - connectorPos.getY());
                if (distance < seedDistance) {
                    seed = candidate.immutable();
                    seedDistance = distance;
                }
            }
        }
        if (seed == null) return List.of();

        List<BlockPos> solved = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(seed);
        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            int currentLateral = lateralCoordinate(current, lateral);
            if (currentLateral < minLateral || currentLateral > maxLateral
                || current.getY() < minY || current.getY() > maxY
                || !containsWithEdgeTolerance(bounds, current, 1)
                || !isBarrierFillCandidate(level.getBlockState(current))
                || !visited.add(current)) {
                continue;
            }
            solved.add(current);
            if (solved.size() >= TEMPORARY_SEAL_MAX_TOTAL_PLACED) {
                return List.of();
            }
            pending.add(current.relative(lateral));
            pending.add(current.relative(lateral.getOpposite()));
            pending.add(current.above());
            pending.add(current.below());
        }
        return List.copyOf(solved);
    }

    private int lateralCoordinate(BlockPos pos, Direction lateral) {
        return lateral.getAxis() == Direction.Axis.X ? pos.getX() : pos.getZ();
    }

    private BlockPos connectorPlanePos(BlockPos connectorPos, Direction lateral, int lateralValue, int y) {
        return lateral.getAxis() == Direction.Axis.X
            ? new BlockPos(lateralValue, y, connectorPos.getZ())
            : new BlockPos(connectorPos.getX(), y, lateralValue);
    }

    private Direction inferConnectorNormal(BlockPos connectorPos, RoomBounds bounds) {
        int west = Math.abs(connectorPos.getX() - bounds.min().getX());
        int east = Math.abs(connectorPos.getX() - bounds.max().getX());
        int north = Math.abs(connectorPos.getZ() - bounds.min().getZ());
        int south = Math.abs(connectorPos.getZ() - bounds.max().getZ());
        int minimum = Math.min(Math.min(west, east), Math.min(north, south));
        if (minimum == west) return Direction.WEST;
        if (minimum == east) return Direction.EAST;
        if (minimum == north) return Direction.NORTH;
        return Direction.SOUTH;
    }

    boolean clearTemporarySealNear(ServerLevel level, BlockPos connectorPos) {
        BlockPos connectorKey = connectorPos.immutable();
        boolean visualOwnershipChanged = false;
        VisualBarrierHandle visual = temporaryVisualSealHandles.get(connectorKey);
        if (visual != null) {
            VisualBarrierHandle.RemovalResult result = visual.remove();
            if (result != VisualBarrierHandle.RemovalResult.UNAVAILABLE
                && result != VisualBarrierHandle.RemovalResult.UNRESOLVED) {
                temporaryVisualSealHandles.remove(connectorKey);
                visualOwnershipChanged = true;
            }
        }

        Set<BlockPos> selected = temporarySealGroups.get(connectorKey);
        if (selected == null) {
            if (temporarySealPositions.isEmpty()) {
                if (visualOwnershipChanged) markChanged.run();
                return visualOwnershipChanged;
            }
            boolean legacyChanged = clearOwnedSealPositions(level, Set.copyOf(temporarySealPositions));
            temporarySealPositions.clear();
            temporarySealGroups.clear();
            if (legacyChanged || visualOwnershipChanged) markChanged.run();
            return legacyChanged || visualOwnershipChanged;
        }

        Set<BlockPos> ownedPositions = Set.copyOf(selected);
        boolean changed = clearOwnedSealPositions(level, ownedPositions);
        temporarySealPositions.removeAll(ownedPositions);
        temporarySealGroups.remove(connectorKey);
        if (changed || visualOwnershipChanged) markChanged.run();
        return changed || visualOwnershipChanged;
    }
    private boolean clearOwnedSealPositions(ServerLevel level, Set<BlockPos> ownedPositions) {
        boolean changed = false;
        for (BlockPos pos : ownedPositions) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.BLACK_CONCRETE) || isRuntimeBarrierBlockState(state)) {
                level.removeBlock(pos, false);
                changed = true;
            }
        }
        return changed;
    }
    void clearTemporaryBlackSeal(ServerLevel level) {
        boolean changed = temporarySealPositions.removeIf(pos -> {
            if (!level.getBlockState(pos).is(Blocks.BLACK_CONCRETE)) return false;
            level.removeBlock(pos, false);
            return true;
        });
        temporarySealGroups.values().forEach(group -> group.retainAll(temporarySealPositions));
        temporarySealGroups.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (changed) markChanged.run();
    }
    void clearTemporarySeal(ServerLevel level) {
        int visualCount = temporaryVisualSealHandles.size();
        temporaryVisualSealHandles.entrySet().removeIf(entry ->
            {
                VisualBarrierHandle.RemovalResult result = entry.getValue().remove();
                return result != VisualBarrierHandle.RemovalResult.UNAVAILABLE
                    && result != VisualBarrierHandle.RemovalResult.UNRESOLVED;
            }
        );
        boolean ownershipChanged = temporaryVisualSealHandles.size() != visualCount;
        boolean changed = false;
        for (BlockPos pos : temporarySealPositions) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.BLACK_CONCRETE) || isRuntimeBarrierBlockState(state)) {
                level.removeBlock(pos, false);
                changed = true;
            }
        }
        temporarySealPositions.clear();
        temporarySealGroups.clear();
        if (changed || ownershipChanged) {
            markChanged.run();
        }
    }
    private double planeCenterDistanceSqr(BarrierPlaneSolveResult plane, BlockPos connectorPos) {
        if (connectorPos == null || plane.positions().isEmpty()) {
            return Double.MAX_VALUE;
        }
        double x = plane.positions().stream().mapToInt(BlockPos::getX).average().orElse(0.0D);
        double y = plane.positions().stream().mapToInt(BlockPos::getY).average().orElse(0.0D);
        double z = plane.positions().stream().mapToInt(BlockPos::getZ).average().orElse(0.0D);
        double dx = connectorPos.getX() + 0.5D - x;
        double dy = connectorPos.getY() + 0.5D - y;
        double dz = connectorPos.getZ() + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }
    void applyBarrierPlanMetrics(BarrierSolvePlan plan, String barrierMaterial, boolean preview) {
        lastBarrierDebugLines.clear();
        lastBarrierMaterialSummary = barrierMaterial;
        lastBarrierSolveMode = plan.mode();
        lastBarrierSolveResult = plan.planes().isEmpty() ? "FAIL" : ((plan.unpairedAnchors().isEmpty() && plan.rejectedPairs().isEmpty()) ? "PASS" : "WARN");
        lastBarrierSolveReason = plan.reason();
        lastBarrierPlaneSummary = plan.planes().isEmpty() ? "NONE" : plan.planes().get(plan.planes().size() - 1).planeSummary();
        lastBarrierVolumeBoundsSummary = plan.planes().isEmpty() ? "NONE" : plan.planes().get(plan.planes().size() - 1).volumeBoundsSummary();
        lastBarrierVolumeSizeSummary = plan.planes().isEmpty() ? "NONE" : plan.planes().get(plan.planes().size() - 1).volumeSizeSummary();
        lastBarrierFillCandidateCount = plan.planes().stream().mapToInt(BarrierPlaneSolveResult::fillCandidateCount).sum();
        lastBarrierSolvedWidth = plan.planes().isEmpty() ? 0 : plan.planes().get(plan.planes().size() - 1).width();
        lastBarrierSolvedHeight = plan.planes().isEmpty() ? 0 : plan.planes().get(plan.planes().size() - 1).height();
        lastBarrierSolvedPositionCount = plan.planes().stream().mapToInt(result -> result.positions().size()).sum();
        lastBarrierPlacedBlockCount = 0;
        lastBarrierSkippedBlockCount = plan.rejectedPairs().size() + plan.unpairedAnchors().size();
        if (preview && "NONE".equals(lastBarrierSolveMode)) {
            lastBarrierSolveMode = "NONE";
        }
    }

    void appendBarrierDebugLines(BarrierSolvePlan plan, int placedBlocks, int skippedBlocks, int clearedBlocks) {
        lastBarrierDebugLines.clear();
        lastBarrierDebugLines.add("Barrier Anchor Count: " + plan.anchorCount());
        lastBarrierDebugLines.add("Barrier Pair Count: " + plan.planes().size());
        int pairNumber = 1;
        String labelPrefix = "EXPLICIT_CHANNEL_PAIRS".equals(plan.mode()) ? "Barrier Channel #" : "Barrier Pair #";
        for (BarrierPlaneSolveResult result : plan.planes()) {
            lastBarrierDebugLines.add(labelPrefix + pairNumber + " Anchors: " + RoomControllerBlockEntity.formatPos(result.anchorA()) + " -> " + RoomControllerBlockEntity.formatPos(result.anchorB()));
            lastBarrierDebugLines.add(labelPrefix + pairNumber + " Owned Anchors: " + result.ownedAnchors());
            lastBarrierDebugLines.add(labelPrefix + pairNumber + " Bounds Relation: " + result.boundsRelation());
            lastBarrierDebugLines.add(labelPrefix + pairNumber + " Bounds: " + result.volumeBoundsSummary());
            lastBarrierDebugLines.add(labelPrefix + pairNumber + " Size: " + result.volumeSizeSummary());
            lastBarrierDebugLines.add(labelPrefix + pairNumber + " Fill Candidates: " + result.fillCandidateCount());
            lastBarrierDebugLines.add(labelPrefix + pairNumber + " Placed / Skipped: " + result.positions().size() + " / 0");
            lastBarrierDebugLines.add(labelPrefix + pairNumber + " Result / Reason: " + (result.positions().isEmpty() ? "FAIL" : "PASS") + " / " + result.reason());
            pairNumber++;
        }
        for (BarrierPlaneSolveResult rejected : plan.rejectedPairs()) {
            lastBarrierDebugLines.add("Barrier Pair Rejected Anchors: " + RoomControllerBlockEntity.formatPos(rejected.anchorA()) + " -> " + RoomControllerBlockEntity.formatPos(rejected.anchorB()));
            lastBarrierDebugLines.add("Barrier Pair Rejected Bounds Relation: " + rejected.boundsRelation());
            lastBarrierDebugLines.add("Barrier Pair Rejected Reason: " + rejected.reason());
        }
        if (!plan.unpairedAnchors().isEmpty()) {
            lastBarrierDebugLines.add("Barrier Unpaired Anchors: " + plan.unpairedAnchors().stream().map(anchor -> RoomControllerBlockEntity.formatPos(anchor)).toList());
        }
        lastBarrierDebugLines.add("Barrier Total Fill Candidates: " + lastBarrierFillCandidateCount);
        lastBarrierDebugLines.add("Barrier Total Placed / Skipped / Cleared: " + placedBlocks + " / " + skippedBlocks + " / " + clearedBlocks);
        lastBarrierDebugLines.add("Barrier Solve Result / Barrier Solve Reason: " + lastBarrierSolveResult + " / " + lastBarrierSolveReason);
    }

    List<BarrierPlaneSolveResult> solveBarrierPlanes(ServerLevel level, Inputs inputs) {
        return solveBarrierPlan(level, inputs).planes();
    }

    BarrierSolvePlan solveBarrierPlan(ServerLevel level, Inputs inputs) {
        RoomBounds roomBounds = getResolvedBarrierRoomBounds(inputs);
        List<BlockPos> barrierAnchors = inputs.barrierAnchors();
        if (inputs.hasExplicitBarrierChannels()) {
            return solveExplicitBarrierChannels(level, roomBounds, inputs);
        }
        if (barrierAnchors.isEmpty()) {
            return new BarrierSolvePlan("NONE", List.of(), List.of(), List.of(), List.of(), "NO_BARRIER_ANCHORS", barrierAnchors.size());
        }
        if (barrierAnchors.size() == 1) {
            BarrierPlaneSolveResult single = solveAdaptiveBarrierPlane(level, barrierAnchors.get(0));
            return new BarrierSolvePlan("ADAPTIVE_SINGLE_ANCHOR", List.of(), List.of(single), List.of(), List.of(), "SINGLE_ANCHOR_ADAPTIVE", barrierAnchors.size());
        }
        if (barrierAnchors.size() == 2) {
            BarrierPlaneSolveResult bounded = solveBoundedAirFillBarrierVolume(level, barrierAnchors.get(0), barrierAnchors.get(1), 1, roomBounds, true);
            return new BarrierSolvePlan("BOUNDED_AIR_FILL_VOLUME", List.of(new BarrierAnchorPair(0, 1)), bounded.positions().isEmpty() ? List.of() : List.of(bounded), List.of(), List.of(bounded), bounded.reason(), barrierAnchors.size());
        }

        List<BarrierPairCandidate> candidates = new ArrayList<>();
        List<BarrierPlaneSolveResult> rejectedPairs = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < barrierAnchors.size() - 1; firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < barrierAnchors.size(); secondIndex++) {
                BarrierPlaneSolveResult result = solveBoundedAirFillBarrierVolume(level, barrierAnchors.get(firstIndex), barrierAnchors.get(secondIndex), candidates.size() + rejectedPairs.size() + 1, roomBounds, true);
                if (result.positions().isEmpty()) {
                    rejectedPairs.add(result);
                    continue;
                }
                candidates.add(buildBarrierPairCandidate(firstIndex, secondIndex, result));
            }
        }

        candidates.sort(Comparator.comparingLong(BarrierPairCandidate::score)
            .thenComparingInt(candidate -> candidate.result().fillCandidateCount())
            .thenComparingInt(candidate -> candidate.result().totalVolumeCells()));

        Set<Integer> usedAnchors = new HashSet<>();
        List<BarrierAnchorPair> selectedPairs = new ArrayList<>();
        List<BarrierPlaneSolveResult> selectedResults = new ArrayList<>();
        for (BarrierPairCandidate candidate : candidates) {
            if (usedAnchors.contains(candidate.firstIndex()) || usedAnchors.contains(candidate.secondIndex())) {
                continue;
            }
            usedAnchors.add(candidate.firstIndex());
            usedAnchors.add(candidate.secondIndex());
            selectedPairs.add(new BarrierAnchorPair(candidate.firstIndex(), candidate.secondIndex()));
            selectedResults.add(candidate.result());
        }

        List<BlockPos> unpairedAnchors = new ArrayList<>();
        for (int index = 0; index < barrierAnchors.size(); index++) {
            if (!usedAnchors.contains(index)) {
                unpairedAnchors.add(barrierAnchors.get(index));
            }
        }

        String reason;
        if (barrierAnchors.size() % 2 != 0) {
            reason = "UNPAIRED_BARRIER_ANCHOR_COUNT";
        } else if (!unpairedAnchors.isEmpty()) {
            reason = "UNPAIRED_BARRIER_ANCHORS";
        } else if (selectedResults.isEmpty()) {
            reason = "NO_VALID_BARRIER_PAIRS";
        } else {
            reason = "MULTI_PAIR_SOLVED";
        }
        return new BarrierSolvePlan("MULTI_BOUNDED_AIR_FILL_VOLUME", selectedPairs, selectedResults, List.copyOf(unpairedAnchors), List.copyOf(rejectedPairs), reason, barrierAnchors.size());
    }

    private BarrierSolvePlan solveExplicitBarrierChannels(ServerLevel level, RoomBounds roomBounds, Inputs inputs) {
        List<BarrierPlaneSolveResult> solved = new ArrayList<>();
        List<BarrierPlaneSolveResult> rejected = new ArrayList<>();
        List<BlockPos> unpairedAnchors = new ArrayList<>();
        List<BarrierAnchorPair> selectedPairs = new ArrayList<>();
        List<List<BlockPos>> channels = getExplicitBarrierChannelLists(inputs);
        int totalAnchors = 0;
        for (int channelIndex = 0; channelIndex < channels.size(); channelIndex++) {
            List<BlockPos> channelAnchors = channels.get(channelIndex);
            int channelNumber = channelIndex + 1;
            String pairLabel = "CHANNEL_" + channelNumber;
            totalAnchors += channelAnchors.size();
            if (channelAnchors.isEmpty()) {
                continue;
            }
            if (channelAnchors.size() == 1) {
                BlockPos anchor = channelAnchors.get(0);
                rejected.add(rejectedBarrierResult(anchor, anchor, pairLabel, "CHANNEL_UNPAIRED", roomBounds, true, "EXPLICIT_CHANNEL_PAIRS"));
                unpairedAnchors.add(anchor);
                continue;
            }
            if (channelAnchors.size() > 2) {
                rejected.add(rejectedBarrierResult(channelAnchors.get(0), channelAnchors.get(1), pairLabel, "CHANNEL_AMBIGUOUS_COUNT_" + channelAnchors.size(), roomBounds, true, "EXPLICIT_CHANNEL_PAIRS"));
                unpairedAnchors.addAll(channelAnchors);
                continue;
            }
            BarrierPlaneSolveResult result = solveBoundedAirFillBarrierVolume(level, channelAnchors.get(0), channelAnchors.get(1), channelNumber, roomBounds, true, true, pairLabel, "EXPLICIT_CHANNEL_PAIRS");
            if (result.positions().isEmpty()) {
                rejected.add(result);
            } else {
                solved.add(result);
                selectedPairs.add(new BarrierAnchorPair(channelIndex, channelIndex));
            }
        }

        String reason;
        if (!unpairedAnchors.isEmpty()) {
            reason = "UNPAIRED_BARRIER_CHANNELS";
        } else if (solved.isEmpty()) {
            reason = rejected.isEmpty() ? "NO_BARRIER_CHANNELS" : "NO_VALID_BARRIER_CHANNELS";
        } else if (!rejected.isEmpty()) {
            reason = "PARTIAL_CHANNEL_SOLVE";
        } else {
            reason = "EXPLICIT_CHANNELS_SOLVED";
        }
        return new BarrierSolvePlan("EXPLICIT_CHANNEL_PAIRS", selectedPairs, solved, List.copyOf(unpairedAnchors), List.copyOf(rejected), reason, totalAnchors);
    }

    private List<List<BlockPos>> getExplicitBarrierChannelLists(Inputs inputs) {
        return List.of(
            inputs.barrierAnchor1Anchors(),
            inputs.barrierAnchor2Anchors(),
            inputs.barrierAnchor3Anchors(),
            inputs.barrierAnchor4Anchors()
        );
    }

    private BarrierPairCandidate buildBarrierPairCandidate(int firstIndex, int secondIndex, BarrierPlaneSolveResult result) {
        int minDimension = Math.min(result.sizeX(), result.sizeZ());
        long score = ((long) minDimension * 1_000_000L) + ((long) result.fillCandidateCount() * 1_000L) + result.totalVolumeCells();
        return new BarrierPairCandidate(firstIndex, secondIndex, result, score);
    }

    private BarrierPlaneSolveResult solveBoundedAirFillBarrierVolume(ServerLevel level, BlockPos first, BlockPos second, int pairIndex, RoomBounds roomBounds, boolean ownedAnchors) {
        return solveBoundedAirFillBarrierVolume(level, first, second, pairIndex, roomBounds, ownedAnchors, false, "PAIR_" + pairIndex, "BOUNDED_AIR_FILL_VOLUME");
    }

    private BarrierPlaneSolveResult solveBoundedAirFillBarrierVolume(ServerLevel level, BlockPos first, BlockPos second, int pairIndex, RoomBounds roomBounds, boolean ownedAnchors, boolean explicitChannel, String pairLabel, String mode) {
        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int maxY = Math.max(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxZ = Math.max(first.getZ(), second.getZ());
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        String boundsSummary = RoomControllerBlockEntity.formatPos(new BlockPos(minX, minY, minZ)) + " -> " + RoomControllerBlockEntity.formatPos(new BlockPos(maxX, maxY, maxZ));
        String volumeSizeSummary = sizeX + " x " + sizeY + " x " + sizeZ;
        String boundsRelation = classifyBoundsRelation(roomBounds, minX, minY, minZ, maxX, maxY, maxZ);

        if (!ownedAnchors) {
            return new BarrierPlaneSolveResult(List.of(), Direction.NORTH, Direction.EAST, Math.max(sizeX, sizeZ), sizeY, "BOUNDED_VOLUME", boundsSummary, volumeSizeSummary, 0, "UNOWNED_BARRIER_ANCHORS", mode, pairLabel, first, second, false, boundsRelation, sizeX, sizeY, sizeZ, sizeX * sizeY * sizeZ);
        }
        if ("OUTSIDE_ROOM_BOUNDS_REJECTED".equals(boundsRelation)) {
            return new BarrierPlaneSolveResult(List.of(), Direction.NORTH, Direction.EAST, Math.max(sizeX, sizeZ), sizeY, "BOUNDED_VOLUME", boundsSummary, volumeSizeSummary, 0, "OUTSIDE_ROOM_BOUNDS_REJECTED", mode, pairLabel, first, second, true, boundsRelation, sizeX, sizeY, sizeZ, sizeX * sizeY * sizeZ);
        }
        if (sizeX > BOUNDED_BARRIER_MAX_XZ || sizeZ > BOUNDED_BARRIER_MAX_XZ || sizeY > BOUNDED_BARRIER_MAX_HEIGHT || Math.min(sizeX, sizeZ) > BOUNDED_BARRIER_MAX_DEPTH) {
            return new BarrierPlaneSolveResult(List.of(), Direction.NORTH, Direction.EAST, Math.max(sizeX, sizeZ), sizeY, "BOUNDED_VOLUME", boundsSummary, volumeSizeSummary, 0, "VOLUME_TOO_LARGE", mode, pairLabel, first, second, true, boundsRelation, sizeX, sizeY, sizeZ, sizeX * sizeY * sizeZ);
        }

        List<BlockPos> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (roomBounds != null && !containsWithEdgeTolerance(roomBounds, pos, 1)) {
                        continue;
                    }
                    if (isBarrierFillCandidate(level.getBlockState(pos))) {
                        positions.add(pos.immutable());
                    }
                }
            }
        }

        if (positions.isEmpty()) {
            return new BarrierPlaneSolveResult(List.of(), Direction.NORTH, Direction.EAST, Math.max(sizeX, sizeZ), sizeY, "BOUNDED_VOLUME", boundsSummary, volumeSizeSummary, 0, "NO_FILLABLE_AIR", mode, pairLabel, first, second, true, boundsRelation, sizeX, sizeY, sizeZ, sizeX * sizeY * sizeZ);
        }
        if (positions.size() > BOUNDED_BARRIER_MAX_TOTAL_PLACED) {
            return new BarrierPlaneSolveResult(List.of(), Direction.NORTH, Direction.EAST, Math.max(sizeX, sizeZ), sizeY, "BOUNDED_VOLUME", boundsSummary, volumeSizeSummary, positions.size(), "VOLUME_TOO_LARGE", mode, pairLabel, first, second, true, boundsRelation, sizeX, sizeY, sizeZ, sizeX * sizeY * sizeZ);
        }
        if (explicitChannel) {
            String explicitFailure = getExplicitChannelDoorwayLimitFailure(sizeX, sizeY, sizeZ, positions.size());
            if (explicitFailure != null) {
                return new BarrierPlaneSolveResult(List.of(), Direction.NORTH, Direction.EAST, Math.max(sizeX, sizeZ), sizeY, "BOUNDED_VOLUME", boundsSummary, volumeSizeSummary, positions.size(), explicitFailure, mode, pairLabel, first, second, true, boundsRelation, sizeX, sizeY, sizeZ, sizeX * sizeY * sizeZ);
            }
        }

        return new BarrierPlaneSolveResult(positions, Direction.NORTH, Direction.EAST, Math.max(sizeX, sizeZ), sizeY, "BOUNDED_VOLUME", boundsSummary, volumeSizeSummary, positions.size(), "BOUNDED_VOLUME_FILLED", mode, pairLabel, first, second, true, boundsRelation, sizeX, sizeY, sizeZ, sizeX * sizeY * sizeZ);
    }

    private String getExplicitChannelDoorwayLimitFailure(int sizeX, int sizeY, int sizeZ, int fillCandidates) {
        int thin = Math.min(sizeX, sizeZ);
        if (thin > 2) {
            return "CHANNEL_NOT_THIN_VOLUME";
        }
        if (sizeY > 32 || Math.max(sizeX, sizeZ) > 32) {
            return "CHANNEL_EXCEEDS_LARGE_GATE_LIMITS";
        }
        if (fillCandidates > 1024) {
            return "CHANNEL_VOLUME_TOO_LARGE";
        }
        return null;
    }

    private BarrierPlaneSolveResult rejectedBarrierResult(BlockPos first, BlockPos second, String pairLabel, String reason, RoomBounds roomBounds, boolean ownedAnchors, String mode) {
        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int maxY = Math.max(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxZ = Math.max(first.getZ(), second.getZ());
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        String boundsSummary = RoomControllerBlockEntity.formatPos(new BlockPos(minX, minY, minZ)) + " -> " + RoomControllerBlockEntity.formatPos(new BlockPos(maxX, maxY, maxZ));
        String volumeSizeSummary = sizeX + " x " + sizeY + " x " + sizeZ;
        String boundsRelation = classifyBoundsRelation(roomBounds, minX, minY, minZ, maxX, maxY, maxZ);
        return new BarrierPlaneSolveResult(List.of(), Direction.NORTH, Direction.EAST, Math.max(sizeX, sizeZ), sizeY, "BOUNDED_VOLUME", boundsSummary, volumeSizeSummary, 0, reason, mode, pairLabel, first, second, ownedAnchors, boundsRelation, sizeX, sizeY, sizeZ, sizeX * sizeY * sizeZ);
    }

    RoomBounds getResolvedBarrierRoomBounds(Inputs inputs) {
        RoomBounds bounds = inputs.roomBounds();
        if (bounds != null) {
            return bounds;
        }
        int radius = inputs.activationRadius();
        return new RoomBounds(
            inputs.controllerPos().offset(-radius, -radius, -radius),
            inputs.controllerPos().offset(radius, radius, radius)
        );
    }

    private String classifyBoundsRelation(RoomBounds bounds, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (bounds == null) {
            return "INSIDE_ROOM_BOUNDS";
        }
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);
        if (bounds.contains(min) && bounds.contains(max)) {
            return "INSIDE_ROOM_BOUNDS";
        }
        RoomBounds expanded = new RoomBounds(bounds.min().offset(-1, -1, -1), bounds.max().offset(1, 1, 1));
        if (expanded.contains(min) && expanded.contains(max)) {
            return "EDGE_TOLERANCE_1";
        }
        return "OUTSIDE_ROOM_BOUNDS_REJECTED";
    }

    boolean containsWithEdgeTolerance(RoomBounds bounds, BlockPos pos, int tolerance) {
        if (bounds == null) {
            return true;
        }
        RoomBounds expanded = new RoomBounds(bounds.min().offset(-tolerance, -tolerance, -tolerance), bounds.max().offset(tolerance, tolerance, tolerance));
        return expanded.contains(pos);
    }

    private BarrierPlaneSolveResult solveAdaptiveBarrierPlane(ServerLevel level, BlockPos anchor) {
        Direction facing = getBarrierFacing(level, anchor);
        Direction lateral = getBarrierLateralAxis(facing);
        BlockPos seed = isBarrierFillCandidate(level.getBlockState(anchor)) ? anchor : anchor.above();
        int negativeExtent = scanBarrierExtent(level, seed, lateral.getOpposite());
        int positiveExtent = scanBarrierExtent(level, seed, lateral);
        int width = negativeExtent + positiveExtent + 1;
        int height = scanBarrierHeight(level, seed, lateral, negativeExtent, positiveExtent);
        List<BlockPos> positions = new ArrayList<>();
        if (height <= 0 || width <= 0) {
            return new BarrierPlaneSolveResult(List.of(), facing, lateral, width, height, planeSummaryFor(facing, lateral), "NONE", "NONE", 0, "NO_OPEN_BARRIER_PLANE_FROM_ANCHOR", "ADAPTIVE_SINGLE_ANCHOR", "PAIR_1", anchor, anchor, true, "INSIDE_ROOM_BOUNDS", width, height, 1, 0);
        }
        for (int y = 0; y < height; y++) {
            for (int lateralOffset = -negativeExtent; lateralOffset <= positiveExtent; lateralOffset++) {
                BlockPos pos = seed.relative(lateral, lateralOffset).above(y).immutable();
                if (isBarrierFillCandidate(level.getBlockState(pos))) {
                    positions.add(pos);
                }
            }
        }
        String reason = "SOLVED";
        if (width >= BARRIER_MAX_WIDTH) {
            reason = "SOLVED_WIDTH_CAP_REACHED";
        }
        if (height >= BARRIER_MAX_HEIGHT) {
            reason = "SOLVED_HEIGHT_CAP_REACHED";
        }
        return new BarrierPlaneSolveResult(positions, facing, lateral, width, height, planeSummaryFor(facing, lateral), "NONE", "NONE", positions.size(), reason, "ADAPTIVE_SINGLE_ANCHOR", "PAIR_1", anchor, anchor, true, "INSIDE_ROOM_BOUNDS", width, height, 1, width * height);
    }

    private int scanBarrierExtent(ServerLevel level, BlockPos seed, Direction direction) {
        int maxHalfWidth = BARRIER_MAX_WIDTH / 2;
        int extent = 0;
        for (int distance = 1; distance <= maxHalfWidth; distance++) {
            BlockPos candidate = seed.relative(direction, distance);
            if (!isBarrierFillCandidate(level.getBlockState(candidate))) {
                break;
            }
            extent = distance;
        }
        return extent;
    }

    private int scanBarrierHeight(ServerLevel level, BlockPos seed, Direction lateral, int negativeExtent, int positiveExtent) {
        int height = 0;
        for (int y = 0; y < BARRIER_MAX_HEIGHT; y++) {
            boolean rowHasFillCandidate = false;
            for (int lateralOffset = -negativeExtent; lateralOffset <= positiveExtent; lateralOffset++) {
                BlockPos candidate = seed.relative(lateral, lateralOffset).above(y);
                if (isBarrierFillCandidate(level.getBlockState(candidate))) {
                    rowHasFillCandidate = true;
                    break;
                }
            }
            if (!rowHasFillCandidate) {
                break;
            }
            height++;
        }
        return height;
    }

    private Direction getBarrierLateralAxis(Direction facing) {
        return switch (facing) {
            case EAST, WEST -> Direction.NORTH;
            case NORTH, SOUTH -> Direction.EAST;
            default -> Direction.EAST;
        };
    }

    private String planeSummaryFor(Direction facing, Direction lateral) {
        String widthAxis = lateral == Direction.EAST || lateral == Direction.WEST ? "X" : "Z";
        String thicknessAxis = facing == Direction.EAST || facing == Direction.WEST ? "X" : "Z";
        return widthAxis + "/Y, " + thicknessAxis + " thickness 1, facing " + facing;
    }

    Direction getBarrierFacing(ServerLevel level, BlockPos anchor) {
        BlockState state = level.getBlockState(anchor);
        if (state.hasProperty(BarrierAnchorBlock.FACING)) {
            return state.getValue(BarrierAnchorBlock.FACING);
        }
        return resolveBarrierFacingFallback(level, anchor);
    }

    private Direction resolveBarrierFacingFallback(ServerLevel level, BlockPos anchor) {
        Map<Direction, Integer> scores = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int clearCount = 0;
            for (int y = 0; y < BARRIER_MAX_HEIGHT; y++) {
                if (level.getBlockState(anchor.relative(direction).above(y)).isAir()) {
                    clearCount++;
                }
            }
            scores.put(direction, clearCount);
        }
        return scores.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Direction.NORTH);
    }

    int getPlacedBarrierBlockCount() {
        return placedBarrierPositions.size();
    }

    int getBarrierPreviewBlockCount() {
        return barrierPreviewPositions.size();
    }

    int getRuntimeBarrierPositionCount(ServerLevel level, Inputs inputs) {
        int count = 0;
        for (BarrierPlaneSolveResult solved : solveBarrierPlanes(level, inputs)) {
            count += solved.positions().size();
        }
        return count;
    }

    void ensureRuneAppearance(ServerLevel level, Inputs inputs) {
        String gameplayType = gameplayBarrierType(inputs);
        placedVisualBarrierHandles.forEach(handle -> handle.ensureDungeonAppearance(gameplayType));
        temporaryVisualSealHandles.values().forEach(handle -> handle.ensureDungeonAppearance("dungeon_sealed"));
    }

    boolean isBarrierFillCandidate(BlockState state) {
        return state.isAir()
            || isRuntimeBarrierBlockState(state)
            || state.is(BARRIER_PREVIEW_BLOCK_STATE.getBlock());
    }

    boolean isRuntimeBarrierBlockState(BlockState state) {
        return LegacyRuntimeBarrierBlocks.recognizes(state);
    }

    List<String> getLastBarrierDebugLines() {
        long unresolved = placedVisualBarrierHandles.stream().filter(handle -> !handle.isResolved()).count()
            + temporaryVisualSealHandles.values().stream().filter(handle -> !handle.isResolved()).count();
        if (unresolved == 0) {
            return List.copyOf(lastBarrierDebugLines);
        }
        List<String> diagnostic = new ArrayList<>(lastBarrierDebugLines);
        diagnostic.add("Unresolved persisted visual barrier handles: " + unresolved);
        return List.copyOf(diagnostic);
    }

    PersistentState persistentState() {
        Map<BlockPos, List<BlockPos>> groups = new LinkedHashMap<>();
        temporarySealGroups.forEach((connector, positions) ->
            groups.put(connector.immutable(), List.copyOf(positions))
        );
        return new PersistentState(
            List.copyOf(placedBarrierPositions),
            List.copyOf(placedVisualBarrierHandles),
            List.copyOf(temporarySealPositions),
            groups,
            java.util.Collections.unmodifiableMap(new LinkedHashMap<>(temporaryVisualSealHandles)),
            List.copyOf(barrierPreviewPositions)
        );
    }

    void importPersistentState(PersistentState persistent) {
        placedBarrierPositions.clear();
        placedBarrierPositions.addAll(persistent.placedBarrierPositions());
        placedVisualBarrierHandles.clear();
        placedVisualBarrierHandles.addAll(persistent.placedVisualBarrierHandles());
        temporarySealPositions.clear();
        temporarySealPositions.addAll(persistent.temporarySealPositions());
        temporarySealGroups.clear();
        persistent.temporarySealGroups().forEach((connector, positions) ->
            temporarySealGroups.put(connector.immutable(), new HashSet<>(positions))
        );
        temporaryVisualSealHandles.clear();
        temporaryVisualSealHandles.putAll(persistent.temporaryVisualSealHandles());
        barrierPreviewPositions.clear();
        barrierPreviewPositions.addAll(persistent.barrierPreviewPositions());
    }

    record PersistentState(
        List<BlockPos> placedBarrierPositions,
        List<VisualBarrierHandle> placedVisualBarrierHandles,
        List<BlockPos> temporarySealPositions,
        Map<BlockPos, List<BlockPos>> temporarySealGroups,
        Map<BlockPos, VisualBarrierHandle> temporaryVisualSealHandles,
        List<BlockPos> barrierPreviewPositions
    ) {
    }

    record Inputs(
        BlockPos controllerPos,
        RoomBounds roomBounds,
        int activationRadius,
        RoomProfile profile,
        boolean hasSpawnBossAnchor,
        RoomObjectiveType activeObjectiveType,
        boolean objectiveDisabled,
        boolean profileAllowsCombat,
        List<BlockPos> barrierAnchors,
        List<BlockPos> barrierAnchor1Anchors,
        List<BlockPos> barrierAnchor2Anchors,
        List<BlockPos> barrierAnchor3Anchors,
        List<BlockPos> barrierAnchor4Anchors
    ) {
        Inputs {
            barrierAnchors = List.copyOf(barrierAnchors);
            barrierAnchor1Anchors = List.copyOf(barrierAnchor1Anchors);
            barrierAnchor2Anchors = List.copyOf(barrierAnchor2Anchors);
            barrierAnchor3Anchors = List.copyOf(barrierAnchor3Anchors);
            barrierAnchor4Anchors = List.copyOf(barrierAnchor4Anchors);
        }

        boolean hasExplicitBarrierChannels() {
            return !barrierAnchor1Anchors.isEmpty()
                || !barrierAnchor2Anchors.isEmpty()
                || !barrierAnchor3Anchors.isEmpty()
                || !barrierAnchor4Anchors.isEmpty();
        }
    }

    record BarrierPlaneSolveResult(
        List<BlockPos> positions,
        Direction facing,
        Direction lateral,
        int width,
        int height,
        String planeSummary,
        String volumeBoundsSummary,
        String volumeSizeSummary,
        int fillCandidateCount,
        String reason,
        String mode,
        String pairLabel,
        BlockPos anchorA,
        BlockPos anchorB,
        boolean ownedAnchors,
        String boundsRelation,
        int sizeX,
        int sizeY,
        int sizeZ,
        int totalVolumeCells
    ) {
    }

    record BarrierAnchorPair(int firstIndex, int secondIndex) {
    }

    record BarrierPairCandidate(int firstIndex, int secondIndex, BarrierPlaneSolveResult result, long score) {
    }

    record BarrierSolvePlan(
        String mode,
        List<BarrierAnchorPair> pairs,
        List<BarrierPlaneSolveResult> planes,
        List<BlockPos> unpairedAnchors,
        List<BarrierPlaneSolveResult> rejectedPairs,
        String reason,
        int anchorCount
    ) {
    }
}
