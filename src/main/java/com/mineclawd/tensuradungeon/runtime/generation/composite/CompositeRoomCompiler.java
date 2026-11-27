package com.mineclawd.tensuradungeon.runtime.generation.composite;

import com.mineclawd.tensuradungeon.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class CompositeRoomCompiler {
    private static final int MAX_BARRIER_ASSOCIATION_DISTANCE_SQUARED = 16 * 16;
    private static final long MAX_COMPOSITE_VOLUME = 2_000_000L;

    private CompositeRoomCompiler() {
    }

    public static Result compile(
        String id,
        List<CompositeSegmentPlacement> placements,
        CompositeRoomRequirements requirements,
        long seed
    ) {
        if (id == null || id.isBlank() || placements == null || placements.isEmpty()
            || requirements == null) {
            return Result.failed("INVALID_COMPOSITE_COMPILATION_REQUEST");
        }
        if (requirements.requireCombatSegment() && placements.stream().noneMatch(
            placement -> placement.segment().kind() == CompositeSegmentDefinition.Kind.COMBAT
        )) {
            return Result.failed("COMPOSITE_REQUIRES_COMBAT_SEGMENT");
        }

        List<BoundingBox> placedBounds = new ArrayList<>();
        for (int index = 0; index < placements.size(); index++) {
            CompositeSegmentPlacement placement = placements.get(index);
            if (!placement.segment().isCompositeEligible()
                || !placement.segment().connectorCountMatchesKind()) {
                return Result.failed("SEGMENT_CONNECTOR_COUNT_" + placement.segment().id()
                    + "_" + placement.segment().connectors().size());
            }
            BoundingBox bounds = bounds(placement);
            for (int prior = 0; prior < placedBounds.size(); prior++) {
                if (intersects(bounds, placedBounds.get(prior))) {
                    return Result.failed("SEGMENT_BOUNDS_OVERLAP_" + prior + "_" + index);
                }
            }
            placedBounds.add(bounds);
        }

        BoundingBox compositeBounds = union(placedBounds);
        long volume = (long) compositeBounds.getXSpan() * compositeBounds.getYSpan()
            * compositeBounds.getZSpan();
        if (volume > MAX_COMPOSITE_VOLUME) {
            return Result.failed("COMPOSITE_VOLUME_UNSAFE_" + volume);
        }

        List<WorldConnector> connectors = new ArrayList<>();
        List<WorldSocket> sockets = new ArrayList<>();
        Map<Long, WorldCell> architecture = new LinkedHashMap<>();
        for (int segmentIndex = 0; segmentIndex < placements.size(); segmentIndex++) {
            CompositeSegmentPlacement placement = placements.get(segmentIndex);
            CompositeSegmentDefinition segment = placement.segment();
            Set<Long> authoredMarkers = new HashSet<>();
            for (int connectorIndex = 0; connectorIndex < segment.connectors().size(); connectorIndex++) {
                CompositeSegmentDefinition.Connector connector = segment.connectors().get(connectorIndex);
                authoredMarkers.add(connector.pos().asLong());
                connectors.add(new WorldConnector(
                    segmentIndex,
                    connectorIndex,
                    transform(placement, connector.pos()),
                    placement.rotation().rotate(connector.facing()),
                    connector.role(),
                    segment.id().toString(),
                    connectorReplacementState(placement, segment, connector)
                ));
            }
            for (int socketIndex = 0; socketIndex < segment.sockets().size(); socketIndex++) {
                CompositeSegmentDefinition.Socket socket = segment.sockets().get(socketIndex);
                authoredMarkers.add(socket.pos().asLong());
                sockets.add(new WorldSocket(
                    segmentIndex,
                    socketIndex,
                    transform(placement, socket.pos()),
                    socket.capabilities(),
                    segment.id().toString()
                ));
            }
            for (CompositeSegmentDefinition.Cell cell : segment.cells()) {
                if (authoredMarkers.contains(cell.pos().asLong())) continue;
                BlockState transformedState = cell.state().rotate(placement.rotation());
                if (isSerializedAir(transformedState) || transformedState.is(Blocks.STRUCTURE_VOID)) continue;
                if (isFinalRoomMarker(transformedState)) {
                    return Result.failed("SEGMENT_CONTAINS_FINAL_ROOM_MARKER_" + segment.id());
                }
                BlockPos worldPos = transform(placement, cell.pos());
                if (architecture.putIfAbsent(
                    worldPos.asLong(), new WorldCell(worldPos, transformedState, cell.nbt())
                ) != null) {
                    return Result.failed("DUPLICATE_COMPOSITE_CELL_" + worldPos.toShortString());
                }
            }
        }

        Pairing pairing = pair(connectors, placements.size());
        if (!pairing.valid()) return Result.failed(pairing.reason());
        if (pairing.externalIndices().size() != requirements.externalConnectorCount()) {
            return Result.failed("EXTERNAL_CONNECTOR_COUNT_" + pairing.externalIndices().size()
                + "_EXPECTED_" + requirements.externalConnectorCount());
        }
        if (!allSegmentsConnected(pairing.segmentEdges(), placements.size())) {
            return Result.failed("COMPOSITE_SEGMENTS_NOT_CONNECTED");
        }

        List<Integer> externalIndices = pairing.externalIndices().stream()
            .sorted(Comparator.comparingLong((Integer index) -> connectors.get(index).pos().asLong())
                .thenComparing(index -> connectors.get(index).facing().name()))
            .toList();
        for (int connectorIndex : externalIndices) {
            WorldConnector connector = connectors.get(connectorIndex);
            if (!externallyExposed(connector, placedBounds)) {
                return Result.failed("EXTERNAL_CONNECTOR_OBSTRUCTED_"
                    + connector.pos().toShortString() + "_" + connector.facing().name());
            }
            if (!onCompositeBoundary(connector, compositeBounds)) {
                return Result.failed("EXTERNAL_CONNECTOR_RECESSED_"
                    + connector.pos().toShortString() + "_" + connector.facing().name());
            }
        }

        Set<Integer> externalIndexSet = Set.copyOf(externalIndices);
        for (int connectorIndex = 0; connectorIndex < connectors.size(); connectorIndex++) {
            if (externalIndexSet.contains(connectorIndex)) continue;
            WorldConnector connector = connectors.get(connectorIndex);
            if (connector.replacementState() == null) {
                return Result.failed("SEGMENT_CONNECTOR_CHAMELEON_SOURCE_MISSING_"
                    + connector.sourceSegment() + "_" + connector.pos().toShortString());
            }
            if (architecture.putIfAbsent(
                connector.pos().asLong(),
                new WorldCell(connector.pos(), connector.replacementState(), null)
            ) != null) {
                return Result.failed("SEGMENT_CONNECTOR_CHAMELEON_OVERLAP_"
                    + connector.pos().toShortString());
            }
        }

        boolean[] usedSockets = new boolean[sockets.size()];
        Map<Integer, Assigned> assigned = new LinkedHashMap<>();
        for (int externalOrder = 0; externalOrder < externalIndices.size(); externalOrder++) {
            int connectorIndex = externalIndices.get(externalOrder);
            List<Integer> candidates = selectBarrierSockets(
                connectorIndex,
                connectors,
                sockets,
                usedSockets,
                requirements.barriersPerExternalConnector()
            );
            if (candidates.size() < requirements.barriersPerExternalConnector()) {
                return Result.failed("EXTERNAL_CONNECTOR_BARRIER_CAPACITY_" + externalOrder
                    + "_" + candidates.size() + "_EXPECTED_"
                    + requirements.barriersPerExternalConnector());
            }
            for (int offset = 0; offset < requirements.barriersPerExternalConnector(); offset++) {
                int socketIndex = candidates.get(offset);
                usedSockets[socketIndex] = true;
                assigned.put(socketIndex, new Assigned(CompositeFinalRole.BARRIER, externalOrder + 1));
            }
        }

        List<CompositeFinalRole> needs = new ArrayList<>();
        for (CompositeFinalRole role : CompositeFinalRole.values()) {
            if (role == CompositeFinalRole.BARRIER) continue;
            for (int count = 0; count < requirements.requiredRoles().getOrDefault(role, 0); count++) {
                needs.add(role);
            }
        }
        needs.sort(Comparator
            .comparingInt((CompositeFinalRole role) -> candidateCount(role, sockets, usedSockets))
            .thenComparingInt(Enum::ordinal));
        if (!assignNeeds(needs, sockets, usedSockets, assigned, seed)) {
            return Result.failed("ROLE_ASSIGNMENT_UNSATISFIED_" + needs);
        }

        for (int connectorIndex : externalIndices) {
            WorldConnector connector = connectors.get(connectorIndex);
            putFinalCell(
                architecture,
                connector.pos(),
                ModBlocks.FLOOR_CONNECTOR_ANCHOR.get().defaultBlockState(),
                "floor_connector"
            );
        }
        for (Map.Entry<Integer, Assigned> entry : assigned.entrySet()) {
            WorldSocket socket = sockets.get(entry.getKey());
            putFinalCell(
                architecture,
                socket.pos(),
                markerState(entry.getValue()),
                entry.getValue().role().name()
            );
        }

        BlockPos shift = new BlockPos(
            -compositeBounds.minX(), -compositeBounds.minY(), -compositeBounds.minZ()
        );
        List<CompiledCompositeRoom.Cell> outputCells = architecture.values().stream()
            .map(cell -> new CompiledCompositeRoom.Cell(
                cell.pos().offset(shift), cell.state(), cell.nbt()
            ))
            .sorted(Comparator
                .comparingInt((CompiledCompositeRoom.Cell cell) -> cell.pos().getY())
                .thenComparingInt(cell -> cell.pos().getX())
                .thenComparingInt(cell -> cell.pos().getZ()))
            .toList();

        Map<String, List<BlockPos>> markerPositions = new LinkedHashMap<>();
        for (int connectorIndex : externalIndices) {
            addMarker(markerPositions, "floor_connector_anchor", connectors.get(connectorIndex).pos().offset(shift));
        }
        List<CompiledCompositeRoom.Assignment> outputAssignments = new ArrayList<>();
        BlockPos controllerPos = null;
        for (Map.Entry<Integer, Assigned> entry : assigned.entrySet()) {
            WorldSocket socket = sockets.get(entry.getKey());
            Assigned assignment = entry.getValue();
            BlockPos localPos = socket.pos().offset(shift).immutable();
            String markerName = markerName(assignment);
            addMarker(markerPositions, markerName, localPos);
            outputAssignments.add(new CompiledCompositeRoom.Assignment(
                localPos,
                assignment.role(),
                assignment.barrierChannel(),
                socket.sourceSegment()
            ));
            if (assignment.role() == CompositeFinalRole.ROOM_CONTROLLER) {
                controllerPos = localPos;
            }
        }
        if (controllerPos == null) return Result.failed("COMPILED_CONTROLLER_MISSING");

        List<CompiledCompositeRoom.ExternalConnector> outputConnectors = externalIndices.stream()
            .map(index -> new CompiledCompositeRoom.ExternalConnector(
                connectors.get(index).pos().offset(shift), connectors.get(index).facing()
            ))
            .toList();
        CompiledCompositeRoom room = new CompiledCompositeRoom(
            id,
            compositeBounds.getXSpan(),
            compositeBounds.getYSpan(),
            compositeBounds.getZSpan(),
            outputCells,
            immutableMarkerMap(markerPositions),
            outputConnectors,
            controllerPos,
            placements.stream().map(placement -> placement.segment().id().toString()).toList(),
            List.copyOf(outputAssignments)
        );
        return Result.success(room);
    }

    private static Pairing pair(List<WorldConnector> connectors, int segmentCount) {
        Map<Long, List<Integer>> byPosition = new HashMap<>();
        for (int index = 0; index < connectors.size(); index++) {
            byPosition.computeIfAbsent(connectors.get(index).pos().asLong(), ignored -> new ArrayList<>())
                .add(index);
        }
        int[] partner = new int[connectors.size()];
        Arrays.fill(partner, -1);
        List<Set<Integer>> edges = new ArrayList<>();
        for (int index = 0; index < segmentCount; index++) edges.add(new LinkedHashSet<>());
        for (int index = 0; index < connectors.size(); index++) {
            WorldConnector connector = connectors.get(index);
            BlockPos target = connector.pos().relative(connector.facing());
            List<Integer> matches = byPosition.getOrDefault(target.asLong(), List.of()).stream()
                .filter(candidate -> connectors.get(candidate).segmentIndex() != connector.segmentIndex())
                .filter(candidate -> connectors.get(candidate).facing() == connector.facing().getOpposite())
                .toList();
            if (matches.size() > 1) {
                return Pairing.failed("AMBIGUOUS_SEGMENT_CONNECTOR_" + connector.pos().toShortString());
            }
            if (matches.size() == 1) partner[index] = matches.getFirst();
        }
        for (int index = 0; index < partner.length; index++) {
            int other = partner[index];
            if (other < 0) continue;
            if (other >= partner.length || partner[other] != index) {
                return Pairing.failed("ASYMMETRIC_SEGMENT_CONNECTOR_PAIR_" + index);
            }
            edges.get(connectors.get(index).segmentIndex()).add(connectors.get(other).segmentIndex());
        }
        List<Integer> external = new ArrayList<>();
        for (int index = 0; index < partner.length; index++) {
            WorldConnector connector = connectors.get(index);
            if (partner[index] >= 0 && !connector.role().mayPair()) {
                return Pairing.failed("EXTERNAL_SEGMENT_CONNECTOR_PAIRED_"
                    + connector.sourceSegment() + "_" + connector.pos().toShortString());
            }
            if (partner[index] < 0 && !connector.role().mayRemainExternal()) {
                return Pairing.failed("INTERNAL_SEGMENT_CONNECTOR_UNPAIRED_"
                    + connector.sourceSegment() + "_" + connector.pos().toShortString());
            }
            if (partner[index] < 0) external.add(index);
        }
        return new Pairing(true, "PAIRED", List.copyOf(external), edges.stream().map(Set::copyOf).toList());
    }

    private static boolean onCompositeBoundary(
        WorldConnector connector,
        BoundingBox bounds
    ) {
        return switch (connector.facing()) {
            case WEST -> connector.pos().getX() == bounds.minX();
            case EAST -> connector.pos().getX() == bounds.maxX();
            case NORTH -> connector.pos().getZ() == bounds.minZ();
            case SOUTH -> connector.pos().getZ() == bounds.maxZ();
            default -> false;
        };
    }

    private static boolean allSegmentsConnected(List<Set<Integer>> edges, int segmentCount) {
        if (segmentCount == 0) return false;
        Set<Integer> visited = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            if (!visited.add(current)) continue;
            queue.addAll(edges.get(current));
        }
        return visited.size() == segmentCount;
    }

    private static List<Integer> selectBarrierSockets(
        int externalConnectorIndex,
        List<WorldConnector> connectors,
        List<WorldSocket> sockets,
        boolean[] used,
        int requiredCount
    ) {
        WorldConnector external = connectors.get(externalConnectorIndex);
        if (requiredCount == 0) return List.of();
        List<Integer> eligible = new ArrayList<>();
        for (int socketIndex = 0; socketIndex < sockets.size(); socketIndex++) {
            WorldSocket socket = sockets.get(socketIndex);
            if (used[socketIndex] || socket.segmentIndex() != external.segmentIndex()
                || !socket.capabilities().contains(CompositeSocketCapability.BARRIER)) {
                continue;
            }
            int nearest = nearestConnectorIndex(socket, connectors);
            if (nearest != externalConnectorIndex) continue;
            if (socket.pos().distSqr(external.pos()) > MAX_BARRIER_ASSOCIATION_DISTANCE_SQUARED) continue;
            if (!onConnectorPlane(external, socket.pos())) continue;
            eligible.add(socketIndex);
        }
        eligible.sort(Comparator
            .comparingDouble((Integer index) -> sockets.get(index).pos().distSqr(external.pos()))
            .thenComparingLong(index -> sockets.get(index).pos().asLong()));
        if (requiredCount == 1) {
            return eligible.isEmpty() ? List.of() : List.of(eligible.getFirst());
        }

        List<BarrierPairCandidate> pairs = new ArrayList<>();
        for (int firstOffset = 0; firstOffset < eligible.size() - 1; firstOffset++) {
            int first = eligible.get(firstOffset);
            for (int secondOffset = firstOffset + 1; secondOffset < eligible.size(); secondOffset++) {
                int second = eligible.get(secondOffset);
                BlockPos firstPos = sockets.get(first).pos();
                BlockPos secondPos = sockets.get(second).pos();
                if (!barrierPairContainsConnector(external, firstPos, secondPos)) continue;
                long firstDistance = squaredDistance(firstPos, external.pos());
                long secondDistance = squaredDistance(secondPos, external.pos());
                long span = barrierPairSpan(external, firstPos, secondPos);
                pairs.add(new BarrierPairCandidate(
                    first,
                    second,
                    Math.max(firstDistance, secondDistance),
                    firstDistance + secondDistance,
                    span,
                    Math.min(firstPos.asLong(), secondPos.asLong()),
                    Math.max(firstPos.asLong(), secondPos.asLong())
                ));
            }
        }
        if (pairs.isEmpty()) return List.of();
        pairs.sort(Comparator
            .comparingLong(BarrierPairCandidate::maximumDistanceSquared)
            .thenComparingLong(BarrierPairCandidate::totalDistanceSquared)
            .thenComparingLong(BarrierPairCandidate::span)
            .thenComparingLong(BarrierPairCandidate::firstPositionKey)
            .thenComparingLong(BarrierPairCandidate::secondPositionKey));
        BarrierPairCandidate selected = pairs.getFirst();
        return List.of(selected.firstSocketIndex(), selected.secondSocketIndex());
    }

    private static boolean onConnectorPlane(WorldConnector connector, BlockPos socketPos) {
        return coordinate(socketPos, connector.facing().getAxis())
            == coordinate(connector.pos(), connector.facing().getAxis());
    }

    private static boolean barrierPairContainsConnector(
        WorldConnector connector,
        BlockPos first,
        BlockPos second
    ) {
        Direction.Axis crossAxis = connector.facing().getAxis() == Direction.Axis.X
            ? Direction.Axis.Z : Direction.Axis.X;
        int connectorCross = coordinate(connector.pos(), crossAxis);
        int firstCross = coordinate(first, crossAxis);
        int secondCross = coordinate(second, crossAxis);
        boolean crossesConnector = betweenInclusive(connectorCross, firstCross, secondCross);
        boolean reachesConnectorHeight = betweenInclusive(
            connector.pos().getY(), first.getY(), second.getY()
        );
        return crossesConnector
            && reachesConnectorHeight
            && (firstCross != secondCross || first.getY() != second.getY());
    }

    private static long barrierPairSpan(
        WorldConnector connector,
        BlockPos first,
        BlockPos second
    ) {
        Direction.Axis crossAxis = connector.facing().getAxis() == Direction.Axis.X
            ? Direction.Axis.Z : Direction.Axis.X;
        long crossSpan = Math.abs(
            coordinate(first, crossAxis) - coordinate(second, crossAxis)
        ) + 1L;
        long heightSpan = Math.abs(first.getY() - second.getY()) + 1L;
        return crossSpan * heightSpan;
    }

    private static int coordinate(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private static boolean betweenInclusive(int value, int first, int second) {
        return value >= Math.min(first, second) && value <= Math.max(first, second);
    }

    private static long squaredDistance(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dy = (long) first.getY() - second.getY();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static int nearestConnectorIndex(WorldSocket socket, List<WorldConnector> connectors) {
        int nearest = -1;
        double best = Double.MAX_VALUE;
        for (int index = 0; index < connectors.size(); index++) {
            WorldConnector connector = connectors.get(index);
            if (connector.segmentIndex() != socket.segmentIndex()) continue;
            double distance = socket.pos().distSqr(connector.pos());
            if (distance < best || distance == best && index < nearest) {
                nearest = index;
                best = distance;
            }
        }
        return nearest;
    }

    private static int candidateCount(
        CompositeFinalRole role,
        List<WorldSocket> sockets,
        boolean[] used
    ) {
        int count = 0;
        for (int index = 0; index < sockets.size(); index++) {
            if (!used[index] && sockets.get(index).capabilities().contains(role.capability())) count++;
        }
        return count;
    }

    private static boolean assignNeeds(
        List<CompositeFinalRole> needs,
        List<WorldSocket> sockets,
        boolean[] used,
        Map<Integer, Assigned> assigned,
        long seed
    ) {
        int[] matchedNeedBySocket = new int[sockets.size()];
        Arrays.fill(matchedNeedBySocket, -1);
        for (int needIndex = 0; needIndex < needs.size(); needIndex++) {
            if (!augmentRoleMatch(
                needIndex,
                needs,
                sockets,
                used,
                matchedNeedBySocket,
                new boolean[sockets.size()],
                seed
            )) {
                return false;
            }
        }
        for (int socketIndex = 0; socketIndex < matchedNeedBySocket.length; socketIndex++) {
            int needIndex = matchedNeedBySocket[socketIndex];
            if (needIndex < 0) continue;
            used[socketIndex] = true;
            assigned.put(socketIndex, new Assigned(needs.get(needIndex), 0));
        }
        return true;
    }

    private static boolean augmentRoleMatch(
        int needIndex,
        List<CompositeFinalRole> needs,
        List<WorldSocket> sockets,
        boolean[] unavailable,
        int[] matchedNeedBySocket,
        boolean[] visitedSockets,
        long seed
    ) {
        CompositeFinalRole role = needs.get(needIndex);
        List<Integer> candidates = new ArrayList<>();
        for (int socketIndex = 0; socketIndex < sockets.size(); socketIndex++) {
            if (!unavailable[socketIndex]
                && sockets.get(socketIndex).capabilities().contains(role.capability())) {
                candidates.add(socketIndex);
            }
        }
        candidates.sort(Comparator
            .comparingLong((Integer index) -> assignmentRank(seed, role, sockets.get(index)))
            .thenComparingLong(index -> sockets.get(index).pos().asLong()));
        for (int socketIndex : candidates) {
            if (visitedSockets[socketIndex]) continue;
            visitedSockets[socketIndex] = true;
            int displacedNeed = matchedNeedBySocket[socketIndex];
            if (displacedNeed < 0 || augmentRoleMatch(
                displacedNeed,
                needs,
                sockets,
                unavailable,
                matchedNeedBySocket,
                visitedSockets,
                seed
            )) {
                matchedNeedBySocket[socketIndex] = needIndex;
                return true;
            }
        }
        return false;
    }

    private static long assignmentRank(long seed, CompositeFinalRole role, WorldSocket socket) {
        long value = seed ^ socket.pos().asLong() ^ ((long) role.ordinal() << 56)
            ^ ((long) socket.segmentIndex() << 32) ^ socket.socketIndex();
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    private static BlockState markerState(Assigned assignment) {
        return switch (assignment.role()) {
            case ROOM_CONTROLLER -> ModBlocks.ROOM_CONTROLLER.get().defaultBlockState();
            case MOB_RESET -> ModBlocks.MOB_RESET_ANCHOR.get().defaultBlockState();
            case GROUND_SPAWN -> ModBlocks.SPAWN_MELEE.get().defaultBlockState();
            case RANGED_SPAWN -> ModBlocks.SPAWN_RANGED.get().defaultBlockState();
            case ELITE_SPAWN -> ModBlocks.SPAWN_ELITE.get().defaultBlockState();
            case BARRIER -> switch (assignment.barrierChannel()) {
                case 1 -> ModBlocks.BARRIER_ANCHOR_1.get().defaultBlockState();
                case 2 -> ModBlocks.BARRIER_ANCHOR_2.get().defaultBlockState();
                case 3 -> ModBlocks.BARRIER_ANCHOR_3.get().defaultBlockState();
                case 4 -> ModBlocks.BARRIER_ANCHOR_4.get().defaultBlockState();
                default -> throw new IllegalArgumentException("Invalid barrier channel");
            };
        };
    }

    private static String markerName(Assigned assignment) {
        return switch (assignment.role()) {
            case ROOM_CONTROLLER -> "room_controller";
            case MOB_RESET -> "mob_reset_anchor";
            case GROUND_SPAWN -> "spawn_melee";
            case RANGED_SPAWN -> "spawn_ranged";
            case ELITE_SPAWN -> "spawn_elite";
            case BARRIER -> "barrier_anchor_" + assignment.barrierChannel();
        };
    }

    private static void putFinalCell(
        Map<Long, WorldCell> cells,
        BlockPos pos,
        BlockState state,
        String type
    ) {
        WorldCell previous = cells.putIfAbsent(pos.asLong(), new WorldCell(pos, state, null));
        if (previous != null) {
            throw new IllegalStateException("Final composite marker overlaps architecture: " + type + " at " + pos);
        }
    }

    private static void addMarker(Map<String, List<BlockPos>> markers, String name, BlockPos pos) {
        markers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(pos.immutable());
    }

    private static Map<String, List<BlockPos>> immutableMarkerMap(Map<String, List<BlockPos>> markers) {
        Map<String, List<BlockPos>> result = new LinkedHashMap<>();
        markers.forEach((name, positions) -> result.put(name, List.copyOf(positions)));
        return Map.copyOf(result);
    }

    private static boolean isSerializedAir(BlockState state) {
        return state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR) || state.is(Blocks.VOID_AIR);
    }

    static boolean isFinalRoomMarker(BlockState state) {
        return state.is(ModBlocks.ROOM_CONTROLLER.get())
            || state.is(ModBlocks.MOB_RESET_ANCHOR.get())
            || state.is(ModBlocks.BOUNDS_ANCHOR.get())
            || state.is(ModBlocks.FLOOR_CONNECTOR_ANCHOR.get())
            || state.is(ModBlocks.CONNECTOR_EXIT.get())
            || state.is(ModBlocks.EXIT_ANCHOR.get())
            || state.is(ModBlocks.SPAWN_MELEE.get())
            || state.is(ModBlocks.SPAWN_RANGED.get())
            || state.is(ModBlocks.SPAWN_FLANK.get())
            || state.is(ModBlocks.SPAWN_HIGHGROUND.get())
            || state.is(ModBlocks.SPAWN_ELITE.get())
            || state.is(ModBlocks.SPAWN_BOSS.get())
            || state.is(ModBlocks.BARRIER_ANCHOR.get())
            || state.is(ModBlocks.BARRIER_ANCHOR_1.get())
            || state.is(ModBlocks.BARRIER_ANCHOR_2.get())
            || state.is(ModBlocks.BARRIER_ANCHOR_3.get())
            || state.is(ModBlocks.BARRIER_ANCHOR_4.get());
    }

    private static BlockPos transform(CompositeSegmentPlacement placement, BlockPos local) {
        return placement.origin().offset(rotate(local, placement.rotation())).immutable();
    }

    @Nullable
    private static BlockState connectorReplacementState(
        CompositeSegmentPlacement placement,
        CompositeSegmentDefinition segment,
        CompositeSegmentDefinition.Connector connector
    ) {
        Map<Long, CompositeSegmentDefinition.Cell> cellsByPosition = new HashMap<>();
        for (CompositeSegmentDefinition.Cell cell : segment.cells()) {
            cellsByPosition.put(cell.pos().asLong(), cell);
        }
        Direction inward = connector.facing().getOpposite();
        List<BlockPos> candidates = new ArrayList<>();
        for (int distance = 1; distance <= 3; distance++) {
            candidates.add(connector.pos().relative(inward, distance));
        }
        Direction firstSide = connector.facing().getAxis() == Direction.Axis.X
            ? Direction.NORTH : Direction.WEST;
        candidates.add(connector.pos().relative(firstSide));
        candidates.add(connector.pos().relative(firstSide.getOpposite()));
        candidates.add(connector.pos().below());
        candidates.add(connector.pos().above());

        for (BlockPos candidate : candidates) {
            CompositeSegmentDefinition.Cell source = cellsByPosition.get(candidate.asLong());
            if (source == null || source.nbt() != null || !isChameleonSource(source.state())) continue;
            return source.state().rotate(placement.rotation());
        }
        return null;
    }

    private static boolean isChameleonSource(BlockState state) {
        return !isSerializedAir(state)
            && !state.is(Blocks.STRUCTURE_VOID)
            && state.getFluidState().isEmpty()
            && !state.is(ModBlocks.SEGMENT_CONNECTOR.get())
            && !state.is(ModBlocks.SEGMENT_CONNECTOR_INTERNAL.get())
            && !state.is(ModBlocks.SEGMENT_CONNECTOR_EXTERNAL.get())
            && !state.is(ModBlocks.ROLE_SOCKET.get())
            && !isFinalRoomMarker(state);
    }

    static BlockPos rotate(BlockPos pos, Rotation rotation) {
        return switch (rotation) {
            case NONE -> pos;
            case CLOCKWISE_90 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
        };
    }

    static BoundingBox bounds(CompositeSegmentPlacement placement) {
        CompositeSegmentDefinition segment = placement.segment();
        BlockPos a = transform(placement, BlockPos.ZERO);
        BlockPos b = transform(placement, new BlockPos(
            segment.width() - 1, segment.height() - 1, segment.depth() - 1
        ));
        return BoundingBox.fromCorners(a, b);
    }

    private static BoundingBox union(List<BoundingBox> bounds) {
        BoundingBox result = new BoundingBox(
            bounds.getFirst().minX(), bounds.getFirst().minY(), bounds.getFirst().minZ(),
            bounds.getFirst().maxX(), bounds.getFirst().maxY(), bounds.getFirst().maxZ()
        );
        for (int index = 1; index < bounds.size(); index++) result.encapsulate(bounds.get(index));
        return result;
    }

    static boolean intersects(BoundingBox left, BoundingBox right) {
        return left.maxX() >= right.minX() && left.minX() <= right.maxX()
            && left.maxY() >= right.minY() && left.minY() <= right.maxY()
            && left.maxZ() >= right.minZ() && left.minZ() <= right.maxZ();
    }

    private static boolean externallyExposed(
        WorldConnector connector,
        List<BoundingBox> placedBounds
    ) {
        BlockPos outside = connector.pos().relative(connector.facing());
        return placedBounds.stream().noneMatch(bounds -> contains(bounds, outside));
    }

    private static boolean contains(BoundingBox bounds, BlockPos pos) {
        return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
            && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
            && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
    }

    private record WorldCell(BlockPos pos, BlockState state, @Nullable net.minecraft.nbt.CompoundTag nbt) {
        private WorldCell {
            pos = pos.immutable();
            nbt = nbt == null ? null : nbt.copy();
        }

        @Override
        public net.minecraft.nbt.CompoundTag nbt() {
            return nbt == null ? null : nbt.copy();
        }
    }

    private record WorldConnector(
        int segmentIndex,
        int connectorIndex,
        BlockPos pos,
        Direction facing,
        CompositeSegmentDefinition.ConnectorRole role,
        String sourceSegment,
        @Nullable BlockState replacementState
    ) {
    }

    private record WorldSocket(
        int segmentIndex,
        int socketIndex,
        BlockPos pos,
        Set<CompositeSocketCapability> capabilities,
        String sourceSegment
    ) {
    }

    private record Assigned(CompositeFinalRole role, int barrierChannel) {
    }

    private record BarrierPairCandidate(
        int firstSocketIndex,
        int secondSocketIndex,
        long maximumDistanceSquared,
        long totalDistanceSquared,
        long span,
        long firstPositionKey,
        long secondPositionKey
    ) {
    }

    private record Pairing(
        boolean valid,
        String reason,
        List<Integer> externalIndices,
        List<Set<Integer>> segmentEdges
    ) {
        private static Pairing failed(String reason) {
            return new Pairing(false, reason, List.of(), List.of());
        }
    }

    public record Result(boolean success, @Nullable CompiledCompositeRoom room, List<String> errors) {
        private static Result success(CompiledCompositeRoom room) {
            return new Result(true, room, List.of());
        }

        private static Result failed(String error) {
            return new Result(false, null, List.of(error));
        }

        public String failureSummary() {
            return errors.isEmpty() ? "NONE" : String.join(" | ", errors);
        }
    }
}
