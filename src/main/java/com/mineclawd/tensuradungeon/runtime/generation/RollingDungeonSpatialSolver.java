package com.mineclawd.tensuradungeon.runtime.generation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

public final class RollingDungeonSpatialSolver {
    public static final int GENERATOR_VERSION = 5;
    public static final int DEFAULT_HORIZON = 4;
    private static final int MAX_VISITED_NODES = 2_000;
    private static final int MAX_BACKTRACKS = 400;
    private static final int PROTECTED_AVOIDANCE_RADIUS = 128;
    private static final int QUALITY_BAND_WIDTH = 64;
    private static final int ORDINARY_WINDOW_PLACEMENT_BUDGET = 9;
    private static final long ENTRY_TIER_SALT = 0xA0761D6478BD642FL;
    private static final long MOTIF_RANK_SALT = 0xE7037ED1A0B428DBL;
    private static final long MOTIF_WEIGHT_TICKET_SALT = 0x8EBC6AF09C88C6E3L;
    private static final List<Rotation> ROTATIONS = List.of(
        Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90
    );

    private RollingDungeonSpatialSolver() {
    }

    public static GenerationContract currentGenerationContract(
        DungeonTemplateMetadataRegistry.Snapshot authored,
        DungeonTemplateMechanicalRegistry.Snapshot mechanical
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(authored.combatEntryPercent());
                List<DungeonTemplateMetadata> eligible = authored.enabledTemplates().stream()
                    .filter(metadata -> {
                        DungeonTemplateMechanicalMetadata facts = mechanical.templates().get(metadata.id());
                        return facts != null && facts.solverEligible();
                    })
                    .sorted(Comparator.comparing(DungeonTemplateMetadata::id))
                    .toList();
                output.writeInt(eligible.size());
                for (DungeonTemplateMetadata metadata : eligible) {
                    DungeonTemplateMechanicalMetadata facts = mechanical.templates().get(metadata.id());
                    writeString(output, metadata.id());
                    writeStrings(output, metadata.roles().stream().map(Enum::name).sorted().toList());
                    writeStrings(output, metadata.themes().stream().sorted().toList());
                    writeString(output, metadata.motif());
                    output.writeInt(authored.motifSelectionWeight(metadata.motif()));
                    writeString(output, metadata.visualTheme());
                    writeString(output, metadata.sizeClass());
                    output.writeInt(metadata.weight());
                    output.writeInt(metadata.repeatLimit());
                    output.writeBoolean(metadata.startAllowed());
                    output.writeBoolean(metadata.terminalAllowed());
                    output.writeBoolean(metadata.setPieceEligible());
                    output.writeInt(metadata.setPieceCost());
                    output.writeInt(facts.width());
                    output.writeInt(facts.height());
                    output.writeInt(facts.depth());
                    output.writeInt(facts.estimatedPlacementCost());
                    List<DungeonTemplateMechanicalMetadata.Connector> connectors = facts.connectors().stream()
                        .sorted(CONTRACT_CONNECTOR_ORDER)
                        .toList();
                    output.writeInt(connectors.size());
                    for (DungeonTemplateMechanicalMetadata.Connector connector : connectors) {
                        writeString(output, connector.type());
                        output.writeInt(connector.localPos().getX());
                        output.writeInt(connector.localPos().getY());
                        output.writeInt(connector.localPos().getZ());
                        writeString(output, connector.facing().name());
                        DungeonTemplateMechanicalMetadata.ClearanceBox clearance = connector.clearance();
                        output.writeInt(clearance.minX());
                        output.writeInt(clearance.minY());
                        output.writeInt(clearance.minZ());
                        output.writeInt(clearance.maxX());
                        output.writeInt(clearance.maxY());
                        output.writeInt(clearance.maxZ());
                        output.writeBoolean(connector.primary());
                    }
                    output.writeInt(facts.markerCount("combat_spawn"));
                    output.writeInt(facts.markerCount("mob_reset"));
                    output.writeInt(facts.markerCount("barrier"));
                    output.writeInt(facts.markerCount("objective"));
                    output.writeInt(facts.markerCount("exit_anchor"));
                }
            }
            return new GenerationContract(GENERATOR_VERSION, hex(sha256(bytes.toByteArray())));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to canonicalize eligible dungeon template pool", exception);
        }
    }

    private static final Comparator<DungeonTemplateMechanicalMetadata.Connector> CONTRACT_CONNECTOR_ORDER =
        Comparator.comparing(DungeonTemplateMechanicalMetadata.Connector::type)
            .thenComparingInt(connector -> connector.localPos().getX())
            .thenComparingInt(connector -> connector.localPos().getY())
            .thenComparingInt(connector -> connector.localPos().getZ())
            .thenComparing(connector -> connector.facing().name())
            .thenComparingInt(connector -> connector.clearance().minX())
            .thenComparingInt(connector -> connector.clearance().minY())
            .thenComparingInt(connector -> connector.clearance().minZ())
            .thenComparingInt(connector -> connector.clearance().maxX())
            .thenComparingInt(connector -> connector.clearance().maxY())
            .thenComparingInt(connector -> connector.clearance().maxZ())
            .thenComparing(DungeonTemplateMechanicalMetadata.Connector::primary);

    private static void writeStrings(DataOutputStream output, List<String> values) throws IOException {
        output.writeInt(values.size());
        for (String value : values) writeString(output, value);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static byte[] sha256(byte[] canonicalPool) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonicalPool);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", exception);
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    public static Result solve(
        DungeonGameplayComposer.Composition composition,
        DungeonTemplateMetadataRegistry.Snapshot authored,
        DungeonTemplateMechanicalRegistry.Snapshot mechanical,
        BlockPos startConnector,
        Direction initialDirection,
        int minBuildHeight,
        int maxBuildHeight,
        long seed
    ) {
        return solveWindow(
            composition.beats(), authored, mechanical, startConnector, initialDirection,
            minBuildHeight, maxBuildHeight, seed, List.of(), List.of(), false,
            new SelectionContext(composition.roomCount(), List.of())
        );
    }

    public static Result solveWindow(
        List<DungeonGameplayComposer.Beat> beats,
        DungeonTemplateMetadataRegistry.Snapshot authored,
        DungeonTemplateMechanicalRegistry.Snapshot mechanical,
        BlockPos startConnector,
        Direction initialDirection,
        int minBuildHeight,
        int maxBuildHeight,
        long seed,
        List<Bounds> reservedBounds
    ) {
        return solveWindow(
            beats, authored, mechanical, startConnector, initialDirection,
            minBuildHeight, maxBuildHeight, seed, reservedBounds, List.of(), false,
            new SelectionContext(inferTotalRoomCount(beats), List.of())
        );
    }

    public static Result solveWindow(
        List<DungeonGameplayComposer.Beat> beats,
        DungeonTemplateMetadataRegistry.Snapshot authored,
        DungeonTemplateMechanicalRegistry.Snapshot mechanical,
        BlockPos startConnector,
        Direction initialDirection,
        int minBuildHeight,
        int maxBuildHeight,
        long seed,
        List<Bounds> reservedBounds,
        List<Bounds> protectedBounds,
        boolean requireContinuation
    ) {
        return solveWindow(
            beats, authored, mechanical, startConnector, initialDirection, minBuildHeight, maxBuildHeight,
            seed, reservedBounds, protectedBounds, requireContinuation,
            new SelectionContext(inferTotalRoomCount(beats), List.of())
        );
    }

    public static Result solveWindow(
        List<DungeonGameplayComposer.Beat> beats,
        DungeonTemplateMetadataRegistry.Snapshot authored,
        DungeonTemplateMechanicalRegistry.Snapshot mechanical,
        BlockPos startConnector,
        Direction initialDirection,
        int minBuildHeight,
        int maxBuildHeight,
        long seed,
        List<Bounds> reservedBounds,
        List<Bounds> protectedBounds,
        boolean requireContinuation,
        SelectionContext selectionContext
    ) {
        List<DungeonGameplayComposer.Beat> window = beats.subList(0, Math.min(DEFAULT_HORIZON, beats.size()));
        String themeRoleGap = themeRoleGap(window, authored, mechanical, selectionContext.visualTheme());
        if (themeRoleGap != null) {
            return new Result(
                false, "THEME_ROLE_UNAVAILABLE", List.of(), 0, 0, 0, 0.0D,
                Map.of("THEME_ROLE_UNAVAILABLE", 1), List.of(themeRoleGap)
            );
        }
        Search search = new Search(
            window, authored, mechanical, minBuildHeight, maxBuildHeight, seed,
            reservedBounds, protectedBounds, requireContinuation, selectionContext
        );
        boolean solved = search.visit(0, startConnector, initialDirection.getOpposite());
        return search.result(solved);
    }

    private static String themeRoleGap(
        List<DungeonGameplayComposer.Beat> beats,
        DungeonTemplateMetadataRegistry.Snapshot authored,
        DungeonTemplateMechanicalRegistry.Snapshot mechanical,
        String visualTheme
    ) {
        if (visualTheme == null || visualTheme.isBlank()) return null;
        for (DungeonGameplayComposer.Beat beat : beats) {
            boolean available = authored.enabledTemplates().stream()
                .filter(metadata -> visualTheme.equals(metadata.visualTheme()))
                .filter(metadata -> rolePenalty(beat.role(), metadata.roles()) >= 0)
                .map(metadata -> mechanical.templates().get(metadata.id()))
                .anyMatch(facts -> facts != null && facts.solverEligible());
            if (!available) return "theme=" + visualTheme + " roomIndex=" + beat.index() + " role=" + beat.role();
        }
        return null;
    }

    private static int inferTotalRoomCount(List<DungeonGameplayComposer.Beat> beats) {
        return beats.stream().mapToInt(DungeonGameplayComposer.Beat::index).max().orElse(beats.size());
    }

    private static final class Search {
        private final List<DungeonGameplayComposer.Beat> beats;
        private final DungeonTemplateMetadataRegistry.Snapshot authored;
        private final DungeonTemplateMechanicalRegistry.Snapshot mechanical;
        private final int minBuildHeight;
        private final int maxBuildHeight;
        private final long seed;
        private final List<Bounds> reservedBounds;
        private final List<Bounds> protectedBounds;
        private final boolean requireContinuation;
        private final SelectionContext selectionContext;
        private final long startedNanos = System.nanoTime();
        private final List<PlacedNode> path = new ArrayList<>();
        private final Map<String, Integer> uses = new HashMap<>();
        private final Map<String, Integer> motifUses = new HashMap<>();
        private final Map<String, Integer> rejectionCounts = new LinkedHashMap<>();
        private final List<String> rejectionDetails = new ArrayList<>();
        private final boolean preferCombatEntry;
        private int visitedNodes;
        private int backtracks;
        private int deepestDepth;
        private boolean limitReached;

        private Search(
            List<DungeonGameplayComposer.Beat> beats,
            DungeonTemplateMetadataRegistry.Snapshot authored,
            DungeonTemplateMechanicalRegistry.Snapshot mechanical,
            int minBuildHeight,
            int maxBuildHeight,
            long seed,
            List<Bounds> reservedBounds,
            List<Bounds> protectedBounds,
            boolean requireContinuation,
            SelectionContext selectionContext
        ) {
            this.beats = beats;
            this.authored = authored;
            this.mechanical = mechanical;
            this.minBuildHeight = minBuildHeight;
            this.maxBuildHeight = maxBuildHeight;
            this.seed = seed;
            this.reservedBounds = List.copyOf(reservedBounds);
            this.protectedBounds = List.copyOf(protectedBounds);
            this.requireContinuation = requireContinuation;
            this.selectionContext = selectionContext;
            this.preferCombatEntry = Math.floorMod(mix64(seed ^ ENTRY_TIER_SALT), 100L)
                < authored.combatEntryPercent();
            for (String templateId : selectionContext.priorTemplateIds()) {
                uses.merge(templateId, 1, Integer::sum);
                DungeonTemplateMetadata metadata = authored.templates().get(templateId);
                if (metadata != null) motifUses.merge(metadata.motif(), 1, Integer::sum);
            }
        }

        private boolean visit(int depth, BlockPos targetConnector, Direction requiredIncomingFacing) {
            if (depth >= beats.size()) {
                if (!requireContinuation || hasContinuationCandidate(targetConnector, requiredIncomingFacing)) return true;
                reject("NO_FUTURE_CONTINUATION");
                rejectDetail("depth=" + depth + " nextConnector=" + targetConnector
                    + " incomingFacing=" + requiredIncomingFacing + " reason=NO_FUTURE_CONTINUATION");
                return false;
            }
            deepestDepth = Math.max(deepestDepth, depth);
            if (overLimit()) return false;
            DungeonGameplayComposer.Beat beat = beats.get(depth);
            List<Candidate> candidates = candidates(beat, depth, targetConnector, requiredIncomingFacing);
            if (candidates.isEmpty()) reject("NO_CANDIDATES_AT_DEPTH_" + (depth + 1));
            for (Candidate candidate : candidates) {
                if (overLimit()) return false;
                visitedNodes++;
                path.add(candidate.node());
                deepestDepth = Math.max(deepestDepth, depth + 1);
                uses.merge(candidate.node().templateId(), 1, Integer::sum);
                incrementMotif(candidate.node().templateId());
                if (candidate.nextConnector() == null) {
                    if (depth + 1 == beats.size() && !requireContinuation) return true;
                } else if (visit(depth + 1, candidate.nextConnector(), candidate.nextFacing().getOpposite())) {
                    return true;
                }
                decrementUse(candidate.node().templateId());
                decrementMotif(candidate.node().templateId());
                path.remove(path.size() - 1);
                backtracks++;
            }
            return false;
        }

        private List<Candidate> candidates(
            DungeonGameplayComposer.Beat beat,
            int depth,
            BlockPos targetConnector,
            Direction requiredIncomingFacing
        ) {
            List<Candidate> candidates = new ArrayList<>();
            String requiredTemplateId = selectionContext.requiredTemplateIdsByRoomIndex().get(beat.index());
            for (DungeonTemplateMetadata metadata : authored.enabledTemplates()) {
                if (!selectionContext.visualTheme().isBlank()
                    && !selectionContext.visualTheme().equals(metadata.visualTheme())) {
                    reject("THEME_MISMATCH");
                    continue;
                }
                if (requiredTemplateId != null && !requiredTemplateId.equals(metadata.id())) {
                    reject("REQUIRED_TEMPLATE_MISMATCH");
                    continue;
                }
                if (depth == 0 && selectionContext.excludedFirstTemplateIds().contains(metadata.id())) {
                    reject("BRANCH_FIRST_TEMPLATE_EXCLUDED");
                    continue;
                }
                DungeonTemplateMechanicalMetadata facts = mechanical.templates().get(metadata.id());
                if (facts == null || !facts.solverEligible()) {
                    reject("MECHANICALLY_INELIGIBLE");
                    continue;
                }
                int rolePenalty = rolePenalty(beat.role(), metadata.roles());
                if (rolePenalty < 0) {
                    reject("ROLE_MISMATCH");
                    continue;
                }
                rolePenalty = selectionRolePenalty(beat, metadata, rolePenalty);
                int used = uses.getOrDefault(metadata.id(), 0);
                if (used > metadata.repeatLimit()) {
                    reject("REPEAT_LIMIT");
                    continue;
                }
                if (beat.index() <= 1 && !metadata.startAllowed()) {
                    reject("START_NOT_ALLOWED");
                    continue;
                }
                if (beat.index() == 1 && facts.markerCount("exit_anchor") != 1) {
                    reject("SAFE_ENTRY_EXIT_ANCHOR_COUNT_" + facts.markerCount("exit_anchor"));
                    continue;
                }
                for (Rotation rotation : ROTATIONS) {
                    List<OrientedConnector> connectors = orientConnectors(facts.connectors(), rotation);
                    List<OrientedConnector> incomingConnectors = orientConnectors(facts.incomingConnectors(), rotation);
                    Bounds localBounds = orientedBounds(facts, rotation);
                    for (OrientedConnector incoming : incomingConnectors) {
                        if (incoming.facing() != requiredIncomingFacing) continue;
                        BlockPos incomingBoundary = attachmentBoundaryInsideBounds(incoming.localPos(), incoming.facing(), localBounds);
                        BlockPos placementOrigin = targetConnector.subtract(incomingBoundary);
                        Bounds worldBounds = localBounds.move(placementOrigin);
                        BlockPos incomingWorld = placementOrigin.offset(incoming.localPos());
                        if (worldBounds.minY() < minBuildHeight || worldBounds.maxY() >= maxBuildHeight) {
                            reject("BUILD_HEIGHT");
                            continue;
                        }
                        OverlapHit overlap = overlapHit(worldBounds);
                        if (overlap != null) {
                            reject("ROOM_OVERLAP");
                            rejectDetail("depth=" + (depth + 1) + " role=" + beat.role() + " template=" + metadata.id()
                                + " rotation=" + rotation + " incomingType=" + incoming.type()
                                + " incomingLocal=" + incoming.localPos() + " incomingWorld=" + incomingWorld
                                + " candidateBounds=" + worldBounds + " hitBounds=" + overlap.bounds()
                                + " hitCategory=" + overlap.category() + " hitOwner=" + overlap.owner());
                            continue;
                        }
                        List<OrientedConnector> outgoingOptions = outgoing(facts, connectors, incoming);
                        if (beat.index() == 1 && outgoingOptions.size() != 1) {
                            reject("ENTRY_OUTGOING_CONNECTOR_COUNT_" + outgoingOptions.size());
                            continue;
                        }
                        boolean trueTerminal = beat.role() == DungeonRoomRole.FINALE
                            && depth + 1 == beats.size() && !requireContinuation;
                        if (outgoingOptions.isEmpty() && trueTerminal && metadata.terminalAllowed()) {
                            String budgetRejection = placementBudgetRejection(metadata, beat, facts.estimatedPlacementCost());
                            if (budgetRejection != null) {
                                reject(budgetRejection);
                                continue;
                            }
                            int qualityScore = qualityScore(
                                metadata, facts, beat, targetConnector, worldBounds,
                                null, null, null, rolePenalty
                            );
                            candidates.add(new Candidate(
                                new PlacedNode(
                                    beat.index(), beat.role(), beat.objective(), metadata.id(), rotation, placementOrigin,
                                    worldBounds, incomingWorld, requiredIncomingFacing,
                                    null, null, 0, facts.estimatedPlacementCost(), metadata.themes()
                                ), null, null, rolePenalty, qualityScore, metadata.setPieceEligible()
                            ));
                        }
                        for (OrientedConnector outgoing : outgoingOptions) {
                            BlockPos outgoingWorld = placementOrigin.offset(outgoing.localPos());
                            Bounds clearance = outgoing.clearance().move(placementOrigin);
                            if (clearanceOverlapsPath(clearance)) {
                                reject("DOORWAY_CLEARANCE");
                                continue;
                            }
                            String budgetRejection = placementBudgetRejection(metadata, beat, facts.estimatedPlacementCost());
                            if (budgetRejection != null) {
                                reject(budgetRejection);
                                continue;
                            }
                            BlockPos next = attachmentSocketOutsideBounds(outgoingWorld, outgoing.facing(), worldBounds);
                            int qualityScore = qualityScore(
                                metadata, facts, beat, targetConnector, worldBounds,
                                outgoingWorld, next, outgoing, rolePenalty
                            );
                            candidates.add(new Candidate(
                                new PlacedNode(
                                    beat.index(), beat.role(), beat.objective(), metadata.id(), rotation, placementOrigin,
                                    worldBounds, incomingWorld, requiredIncomingFacing,
                                    outgoingWorld, outgoing.facing(), outgoingOptions.size(), facts.estimatedPlacementCost(), metadata.themes()
                                ), next, outgoing.facing(), rolePenalty, qualityScore, metadata.setPieceEligible()
                            ));
                        }
                    }
                }
            }
            return orderCandidates(candidates, beat, targetConnector, requiredIncomingFacing);
        }

        private int selectionRolePenalty(
            DungeonGameplayComposer.Beat beat,
            DungeonTemplateMetadata metadata,
            int basePenalty
        ) {
            if (beat.role() != DungeonRoomRole.ENTRY) return basePenalty;
            boolean nativeEntry = metadata.roles().contains(DungeonRoomRole.ENTRY)
                || metadata.roles().contains(DungeonRoomRole.TRAVERSAL)
                || metadata.roles().contains(DungeonRoomRole.CORRIDOR);
            boolean combatShell = !nativeEntry && metadata.roles().contains(DungeonRoomRole.COMBAT);
            if (!combatShell && !nativeEntry) return basePenalty;
            return preferCombatEntry == combatShell ? 0 : 40;
        }

        private boolean hasContinuationCandidate(BlockPos targetConnector, Direction requiredIncomingFacing) {
            for (DungeonTemplateMetadata metadata : authored.enabledTemplates()) {
                if (!selectionContext.visualTheme().isBlank()
                    && !selectionContext.visualTheme().equals(metadata.visualTheme())) continue;
                DungeonTemplateMechanicalMetadata facts = mechanical.templates().get(metadata.id());
                if (facts == null || !facts.solverEligible()) continue;
                for (Rotation rotation : ROTATIONS) {
                    List<OrientedConnector> connectors = orientConnectors(facts.connectors(), rotation);
                    Bounds localBounds = orientedBounds(facts, rotation);
                    for (OrientedConnector incoming : orientConnectors(facts.incomingConnectors(), rotation)) {
                        if (incoming.facing() != requiredIncomingFacing) continue;
                        BlockPos incomingBoundary = attachmentBoundaryInsideBounds(incoming.localPos(), incoming.facing(), localBounds);
                        BlockPos placementOrigin = targetConnector.subtract(incomingBoundary);
                        Bounds worldBounds = localBounds.move(placementOrigin);
                        if (worldBounds.minY() < minBuildHeight || worldBounds.maxY() >= maxBuildHeight) continue;
                        if (overlapHit(worldBounds) != null) continue;
                        for (OrientedConnector outgoing : outgoing(facts, connectors, incoming)) {
                            Bounds clearance = outgoing.clearance().move(placementOrigin);
                            if (!clearanceOverlapsPath(clearance)) return true;
                        }
                    }
                }
            }
            return false;
        }

        private BlockPos attachmentSocketOutsideBounds(BlockPos connector, Direction facing, Bounds bounds) {
            BlockPos socket = connector.relative(facing);
            int steps = 0;
            while (contains(bounds, socket) && steps++ < 8) {
                socket = socket.relative(facing);
            }
            return socket;
        }

        private BlockPos attachmentBoundaryInsideBounds(BlockPos connector, Direction facing, Bounds bounds) {
            BlockPos boundary = connector;
            int steps = 0;
            while (contains(bounds, boundary.relative(facing)) && steps++ < 8) {
                boundary = boundary.relative(facing);
            }
            return boundary;
        }

        private boolean contains(Bounds bounds, BlockPos pos) {
            return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
        }

        private String placementBudgetRejection(
            DungeonTemplateMetadata metadata,
            DungeonGameplayComposer.Beat beat,
            int candidateCost
        ) {
            if (metadata.setPieceEligible()) {
                if (isAdjacentToSetPiece()) return "SET_PIECE_SPACING";
                return usedSetPieceCredits() + metadata.setPieceCost() <= availableSetPieceCredits()
                    ? null : "SET_PIECE_CREDITS";
            }
            if (beat.index() == 1) return null;
            int total = candidateCost;
            for (PlacedNode node : path) {
                DungeonTemplateMetadata selected = authored.templates().get(node.templateId());
                if (node.roomIndex() != 1 && (selected == null || !selected.setPieceEligible())) {
                    total += node.placementCost();
                }
            }
            return total > ORDINARY_WINDOW_PLACEMENT_BUDGET ? "WINDOW_PLACEMENT_BUDGET" : null;
        }

        private int availableSetPieceCredits() {
            return selectionContext.totalRoomCount() <= 4 ? 0 : selectionContext.totalRoomCount() <= 8 ? 1 : 2;
        }

        private int usedSetPieceCredits() {
            int used = 0;
            for (String templateId : selectionContext.priorTemplateIds()) {
                DungeonTemplateMetadata metadata = authored.templates().get(templateId);
                if (metadata != null && metadata.setPieceEligible()) used += metadata.setPieceCost();
            }
            for (PlacedNode node : path) {
                DungeonTemplateMetadata metadata = authored.templates().get(node.templateId());
                if (metadata != null && metadata.setPieceEligible()) used += metadata.setPieceCost();
            }
            return used;
        }

        private boolean isAdjacentToSetPiece() {
            String previousTemplateId = previousTemplateId();
            if (previousTemplateId == null) return false;
            DungeonTemplateMetadata previous = authored.templates().get(previousTemplateId);
            return previous != null && previous.setPieceEligible();
        }

        private int qualityScore(
            DungeonTemplateMetadata metadata,
            DungeonTemplateMechanicalMetadata facts,
            DungeonGameplayComposer.Beat beat,
            BlockPos targetConnector,
            Bounds worldBounds,
            BlockPos outgoingWorld,
            BlockPos nextConnector,
            OrientedConnector outgoing,
            int rolePenalty
        ) {
            int score = rolePenalty + facts.estimatedPlacementCost() * 16 - metadata.weight() * 5;
            if (!path.isEmpty()) {
                PlacedNode previous = path.get(path.size() - 1);
                if (!disjoint(previous.themes(), metadata.themes())) score -= 35;
                if (outgoing != null) {
                    int previousTurn = turnSign(previous.incomingFacing().getOpposite(), previous.outgoingFacing());
                    int currentTurn = turnSign(previous.outgoingFacing(), outgoing.facing());
                    if (previousTurn != 0 && previousTurn == currentTurn) score += 34;
                    else if (previousTurn != 0 && currentTurn != 0) score -= 8;
                }
            }
            if (outgoing != null) {
                int elevationChange = Math.abs(outgoingWorld.getY() - targetConnector.getY());
                if (elevationChange >= 4) score -= Math.min(36, 12 + elevationChange);
            }
            score += protectedSpaceScore(targetConnector, worldBounds, nextConnector);
            if (beat.role() == DungeonRoomRole.LARGE_COMBAT && "LARGE".equals(metadata.sizeClass())) score -= 120;
            if (beat.role() == DungeonRoomRole.INTERSECTION && facts.connectors().size() >= 3) score -= 140;
            return score;
        }

        private List<Candidate> orderCandidates(
            List<Candidate> candidates,
            DungeonGameplayComposer.Beat beat,
            BlockPos targetConnector,
            Direction requiredIncomingFacing
        ) {
            if (candidates.isEmpty()) return List.of();
            Map<String, List<Candidate>> byTemplate = new LinkedHashMap<>();
            for (Candidate candidate : candidates) {
                byTemplate.computeIfAbsent(candidate.node().templateId(), ignored -> new ArrayList<>()).add(candidate);
            }
            Comparator<Candidate> geometryOrder = Comparator.comparingInt(Candidate::qualityScore)
                .thenComparingInt(candidate -> candidate.node().rotation().ordinal())
                .thenComparingLong(candidate -> candidate.node().incomingConnector().asLong())
                .thenComparingInt(candidate -> candidate.node().outgoingFacing() == null
                    ? Integer.MAX_VALUE : candidate.node().outgoingFacing().ordinal())
                .thenComparingLong(candidate -> candidate.node().outgoingConnector() == null
                    ? Long.MAX_VALUE : candidate.node().outgoingConnector().asLong());
            byTemplate.values().forEach(variants -> variants.sort(geometryOrder));

            boolean preferSetPiece = isStructuralBeat(beat.role())
                && candidates.stream().anyMatch(Candidate::setPiece);
            Map<String, Integer> roleTiers = new HashMap<>();
            Map<String, Integer> setPieceTiers = new HashMap<>();
            Map<String, Integer> templateQualities = new HashMap<>();
            for (Map.Entry<String, List<Candidate>> entry : byTemplate.entrySet()) {
                Candidate representative = entry.getValue().getFirst();
                roleTiers.put(entry.getKey(), representative.rolePenalty());
                setPieceTiers.put(entry.getKey(), preferSetPiece && !representative.setPiece() ? 1 : 0);
                templateQualities.put(entry.getKey(), representative.qualityScore());
            }
            Map<String, Integer> qualityBands = new HashMap<>();
            for (String templateId : byTemplate.keySet()) {
                int roleTier = roleTiers.get(templateId);
                int setPieceTier = setPieceTiers.get(templateId);
                int bestQuality = templateQualities.entrySet().stream()
                    .filter(entry -> roleTiers.get(entry.getKey()) == roleTier
                        && setPieceTiers.get(entry.getKey()) == setPieceTier)
                    .mapToInt(Map.Entry::getValue)
                    .min().orElse(templateQualities.get(templateId));
                qualityBands.put(templateId, beat.index() == 1 ? 0
                    : Math.floorDiv(templateQualities.get(templateId) - bestQuality, QUALITY_BAND_WIDTH));
            }

            Map<String, String> motifs = new HashMap<>();
            Map<String, Integer> motifUseTiers = new HashMap<>();
            for (String templateId : byTemplate.keySet()) {
                DungeonTemplateMetadata metadata = authored.templates().get(templateId);
                String motif = metadata == null ? "unclassified" : metadata.motif();
                motifs.put(templateId, motif);
                motifUseTiers.put(templateId, motifUses.getOrDefault(motif, 0));
            }
            String previousTemplateId = previousTemplateId();
            List<String> tierOrder = new ArrayList<>(byTemplate.keySet());
            tierOrder.sort((first, second) -> {
                int comparison = Integer.compare(roleTiers.get(first), roleTiers.get(second));
                if (comparison == 0) comparison = Integer.compare(setPieceTiers.get(first), setPieceTiers.get(second));
                if (comparison == 0) comparison = Integer.compare(motifUseTiers.get(first), motifUseTiers.get(second));
                if (comparison == 0) comparison = Integer.compare(qualityBands.get(first), qualityBands.get(second));
                return comparison == 0 ? first.compareTo(second) : comparison;
            });

            List<String> templateOrder = new ArrayList<>(tierOrder.size());
            int tierStart = 0;
            while (tierStart < tierOrder.size()) {
                String first = tierOrder.get(tierStart);
                int tierEnd = tierStart + 1;
                while (tierEnd < tierOrder.size() && sameSelectionTier(
                    first, tierOrder.get(tierEnd), roleTiers, setPieceTiers, motifUseTiers, qualityBands
                )) {
                    tierEnd++;
                }
                templateOrder.addAll(motifBalancedTemplateOrder(
                    tierOrder.subList(tierStart, tierEnd), motifs, beat.index(), targetConnector,
                    requiredIncomingFacing, previousTemplateId
                ));
                tierStart = tierEnd;
            }

            List<Candidate> ordered = new ArrayList<>(candidates.size());
            int groupStart = 0;
            while (groupStart < templateOrder.size()) {
                String first = templateOrder.get(groupStart);
                int groupEnd = groupStart + 1;
                while (groupEnd < templateOrder.size() && sameSelectionTier(
                    first, templateOrder.get(groupEnd), roleTiers, setPieceTiers, motifUseTiers, qualityBands
                )) {
                    groupEnd++;
                }
                int maximumVariants = 0;
                for (int i = groupStart; i < groupEnd; i++) {
                    maximumVariants = Math.max(maximumVariants, byTemplate.get(templateOrder.get(i)).size());
                }
                for (int variantIndex = 0; variantIndex < maximumVariants; variantIndex++) {
                    for (int i = groupStart; i < groupEnd; i++) {
                        List<Candidate> variants = byTemplate.get(templateOrder.get(i));
                        if (variantIndex < variants.size()) ordered.add(variants.get(variantIndex));
                    }
                }
                groupStart = groupEnd;
            }
            return ordered;
        }

        private List<String> motifBalancedTemplateOrder(
            List<String> templateIds,
            Map<String, String> motifs,
            int roomIndex,
            BlockPos targetConnector,
            Direction requiredIncomingFacing,
            String previousTemplateId
        ) {
            Map<String, List<String>> byMotif = new LinkedHashMap<>();
            for (String templateId : templateIds) {
                byMotif.computeIfAbsent(motifs.get(templateId), ignored -> new ArrayList<>()).add(templateId);
            }
            for (List<String> motifTemplates : byMotif.values()) {
                motifTemplates.sort((first, second) -> {
                    int comparison = Integer.compare(
                        uses.getOrDefault(first, 0), uses.getOrDefault(second, 0)
                    );
                    if (comparison == 0) comparison = Boolean.compare(
                        first.equals(previousTemplateId), second.equals(previousTemplateId)
                    );
                    if (comparison == 0) comparison = Long.compareUnsigned(
                        deterministicTemplateRank(first, roomIndex, targetConnector, requiredIncomingFacing),
                        deterministicTemplateRank(second, roomIndex, targetConnector, requiredIncomingFacing)
                    );
                    return comparison == 0 ? first.compareTo(second) : comparison;
                });
            }

            String previousMotif = previousTemplateId == null ? null : motifs.get(previousTemplateId);
            if (previousMotif == null && previousTemplateId != null) {
                DungeonTemplateMetadata previous = authored.templates().get(previousTemplateId);
                if (previous != null) previousMotif = previous.motif();
            }
            String adjacentMotif = previousMotif;
            List<String> motifOrder = new ArrayList<>(byMotif.keySet());
            motifOrder.sort((first, second) -> {
                int comparison = Boolean.compare(first.equals(adjacentMotif), second.equals(adjacentMotif));
                if (comparison == 0) comparison = Long.compareUnsigned(
                    deterministicMotifRank(first),
                    deterministicMotifRank(second)
                );
                return comparison == 0 ? first.compareTo(second) : comparison;
            });

            int maximumTemplates = byMotif.values().stream().mapToInt(List::size).max().orElse(0);
            List<String> balanced = new ArrayList<>(templateIds.size());
            for (int templateIndex = 0; templateIndex < maximumTemplates; templateIndex++) {
                for (String motif : motifOrder) {
                    List<String> variants = byMotif.get(motif);
                    if (templateIndex < variants.size()) balanced.add(variants.get(templateIndex));
                }
            }
            return balanced;
        }

        private boolean sameSelectionTier(
            String first,
            String second,
            Map<String, Integer> roleTiers,
            Map<String, Integer> setPieceTiers,
            Map<String, Integer> motifUseTiers,
            Map<String, Integer> qualityBands
        ) {
            return roleTiers.get(first).equals(roleTiers.get(second))
                && setPieceTiers.get(first).equals(setPieceTiers.get(second))
                && motifUseTiers.get(first).equals(motifUseTiers.get(second))
                && qualityBands.get(first).equals(qualityBands.get(second));
        }

        private boolean isStructuralBeat(DungeonRoomRole role) {
            return role == DungeonRoomRole.LARGE_COMBAT || role == DungeonRoomRole.INTERSECTION
                || role == DungeonRoomRole.ARENA;
        }

        private String previousTemplateId() {
            if (!path.isEmpty()) return path.getLast().templateId();
            return selectionContext.priorTemplateIds().isEmpty()
                ? null : selectionContext.priorTemplateIds().getLast();
        }

        private long deterministicTemplateRank(
            String templateId,
            int roomIndex,
            BlockPos targetConnector,
            Direction requiredIncomingFacing
        ) {
            long value = seed ^ ((long) templateId.hashCode() * 0x9E3779B97F4A7C15L)
                ^ ((long) roomIndex * 0xC2B2AE3D27D4EB4FL)
                ^ targetConnector.asLong() ^ requiredIncomingFacing.ordinal();
            for (PlacedNode node : path) {
                value = mix64(value ^ ((long) node.templateId().hashCode() * 0xD6E8FEB86659FD93L)
                    ^ node.rotation().ordinal()
                    ^ (node.outgoingFacing() == null ? 7L : node.outgoingFacing().ordinal()));
            }
            return mix64(value);
        }

        private long deterministicMotifRank(String motif) {
            long motifSeed = seed ^ ((long) motif.hashCode() * MOTIF_RANK_SALT);
            long bestRank = -1L;
            int ticketCount = authored.motifSelectionWeight(motif);
            for (int ticket = 1; ticket <= ticketCount; ticket++) {
                long rank = mix64(motifSeed ^ ((long) ticket * MOTIF_WEIGHT_TICKET_SALT));
                if (Long.compareUnsigned(rank, bestRank) < 0) bestRank = rank;
            }
            return bestRank;
        }

        private long mix64(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdL;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53L;
            return value ^ value >>> 33;
        }

        private int protectedSpaceScore(BlockPos incoming, Bounds candidate, BlockPos outgoing) {
            int score = 0;
            for (Bounds protectedBound : protectedBounds) {
                int candidateDistance = horizontalDistance(candidate, protectedBound);
                if (candidateDistance < PROTECTED_AVOIDANCE_RADIUS) {
                    score += (PROTECTED_AVOIDANCE_RADIUS - candidateDistance) * 2;
                }
                if (outgoing != null) {
                    int incomingDistance = horizontalDistance(incoming, protectedBound);
                    int outgoingDistance = horizontalDistance(outgoing, protectedBound);
                    int approach = incomingDistance - outgoingDistance;
                    if (approach > 0) score += Math.min(420, approach * 8);
                    else if (approach < 0) score -= Math.min(120, -approach * 3);
                }
            }
            return score;
        }

        private int horizontalDistance(BlockPos pos, Bounds bounds) {
            int dx = pos.getX() < bounds.minX() ? bounds.minX() - pos.getX()
                : pos.getX() > bounds.maxX() ? pos.getX() - bounds.maxX() : 0;
            int dz = pos.getZ() < bounds.minZ() ? bounds.minZ() - pos.getZ()
                : pos.getZ() > bounds.maxZ() ? pos.getZ() - bounds.maxZ() : 0;
            return dx + dz;
        }

        private int horizontalDistance(Bounds first, Bounds second) {
            int dx = first.maxX() < second.minX() ? second.minX() - first.maxX()
                : second.maxX() < first.minX() ? first.minX() - second.maxX() : 0;
            int dz = first.maxZ() < second.minZ() ? second.minZ() - first.maxZ()
                : second.maxZ() < first.minZ() ? first.minZ() - second.maxZ() : 0;
            return dx + dz;
        }

        private int turnSign(Direction incomingTravel, Direction outgoingTravel) {
            int cross = incomingTravel.getStepX() * outgoingTravel.getStepZ()
                - incomingTravel.getStepZ() * outgoingTravel.getStepX();
            return Integer.compare(cross, 0);
        }

        private OverlapHit overlapHit(Bounds candidate) {
            for (int i = 0; i < reservedBounds.size(); i++) {
                Bounds reserved = reservedBounds.get(i);
                if (candidate.overlaps(reserved)) return new OverlapHit(reserved, "RESERVED", "reservation_" + i);
            }
            for (PlacedNode node : path) {
                if (candidate.overlaps(node.bounds())) return new OverlapHit(node.bounds(), "CURRENT_SEARCH_PATH", node.templateId());
            }
            return null;
        }

        private boolean clearanceOverlapsPath(Bounds clearance) {
            for (Bounds reserved : reservedBounds) if (clearance.overlaps(reserved)) return true;
            for (int i = 0; i + 1 < path.size(); i++) {
                if (clearance.overlaps(path.get(i).bounds())) return true;
            }
            return false;
        }

        private boolean overLimit() {
            if (visitedNodes >= MAX_VISITED_NODES || backtracks >= MAX_BACKTRACKS) {
                limitReached = true;
                return true;
            }
            return false;
        }

        private Result result(boolean solved) {
            String reason = solved ? "SOLVED"
                : limitReached ? "SEARCH_BUDGET_EXHAUSTED" : "NO_COLLISION_FREE_HORIZON";
            return new Result(
                solved, reason, List.copyOf(path), deepestDepth, visitedNodes, backtracks,
                (System.nanoTime() - startedNanos) / 1_000_000.0D, Map.copyOf(rejectionCounts), List.copyOf(rejectionDetails)
            );
        }

        private void reject(String reason) {
            rejectionCounts.merge(reason, 1, Integer::sum);
        }

        private void rejectDetail(String detail) {
            if (rejectionDetails.size() < 128) rejectionDetails.add(detail);
        }

        private void decrementUse(String id) {
            int remaining = uses.getOrDefault(id, 1) - 1;
            if (remaining <= 0) uses.remove(id);
            else uses.put(id, remaining);
        }

        private void incrementMotif(String templateId) {
            DungeonTemplateMetadata metadata = authored.templates().get(templateId);
            if (metadata != null) motifUses.merge(metadata.motif(), 1, Integer::sum);
        }

        private void decrementMotif(String templateId) {
            DungeonTemplateMetadata metadata = authored.templates().get(templateId);
            if (metadata == null) return;
            int remaining = motifUses.getOrDefault(metadata.motif(), 1) - 1;
            if (remaining <= 0) motifUses.remove(metadata.motif());
            else motifUses.put(metadata.motif(), remaining);
        }
    }

    private static int rolePenalty(DungeonRoomRole requested, Set<DungeonRoomRole> offered) {
        if (requested == DungeonRoomRole.ENTRY) {
            return offered.contains(DungeonRoomRole.ENTRY)
                || offered.contains(DungeonRoomRole.TRAVERSAL)
                || offered.contains(DungeonRoomRole.CORRIDOR)
                || offered.contains(DungeonRoomRole.COMBAT) ? 0 : -1;
        }
        if (offered.contains(requested)) return 0;
        return switch (requested) {
            case ENTRY -> -1;
            case COMBAT -> offered.contains(DungeonRoomRole.LARGE_COMBAT) ? 80 : -1;
            case LARGE_COMBAT, ARENA -> offered.contains(DungeonRoomRole.COMBAT) ? 180 : -1;
            case INTERACTION -> offered.contains(DungeonRoomRole.TRAVERSAL) ? 140 : -1;
            case TREASURE -> offered.contains(DungeonRoomRole.COMBAT) ? 160 : -1;
            case SAFE -> offered.contains(DungeonRoomRole.TRAVERSAL) ? 220 : -1;
            case FINALE -> offered.contains(DungeonRoomRole.LARGE_COMBAT) || offered.contains(DungeonRoomRole.ARENA) ? 120 : -1;
            case INTERSECTION -> -1;
            case CORRIDOR, TRAVERSAL -> offered.contains(DungeonRoomRole.CORRIDOR) || offered.contains(DungeonRoomRole.TRAVERSAL) ? 20 : -1;
        };
    }

    private static List<OrientedConnector> orientConnectors(List<DungeonTemplateMechanicalMetadata.Connector> source, Rotation rotation) {
        List<OrientedConnector> oriented = new ArrayList<>();
        for (DungeonTemplateMechanicalMetadata.Connector connector : source) {
            oriented.add(new OrientedConnector(
                connector.type(), rotate(connector.localPos(), rotation), rotation.rotate(connector.facing()),
                rotate(connector.clearance(), rotation), connector.primary()
            ));
        }
        return oriented;
    }

    private static List<OrientedConnector> outgoing(DungeonTemplateMechanicalMetadata facts, List<OrientedConnector> connectors, OrientedConnector incoming) {
        if (facts.connectorMode() == DungeonTemplateMechanicalMetadata.ConnectorMode.TYPED) {
            return connectors.stream().filter(connector -> "connector_exit".equals(connector.type())).toList();
        }
        List<OrientedConnector> preferred = connectors.stream()
            .filter(connector -> connector != incoming && connector.facing() != incoming.facing() && connector.primary()).toList();
        if (!preferred.isEmpty()) return preferred;
        return connectors.stream().filter(connector -> connector != incoming && connector.facing() != incoming.facing()).toList();
    }

    private static Bounds orientedBounds(DungeonTemplateMechanicalMetadata facts, Rotation rotation) {
        BlockPos a = rotate(BlockPos.ZERO, rotation);
        BlockPos b = rotate(new BlockPos(facts.width() - 1, facts.height() - 1, facts.depth() - 1), rotation);
        return Bounds.between(a, b);
    }

    private static BlockPos rotate(BlockPos pos, Rotation rotation) {
        return switch (rotation) {
            case NONE -> pos;
            case CLOCKWISE_90 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
        };
    }

    private static Bounds rotate(DungeonTemplateMechanicalMetadata.ClearanceBox box, Rotation rotation) {
        Bounds result = null;
        for (int x : List.of(box.minX(), box.maxX())) {
            for (int z : List.of(box.minZ(), box.maxZ())) {
                BlockPos point = rotate(new BlockPos(x, box.minY(), z), rotation);
                Bounds corner = new Bounds(point.getX(), box.minY(), point.getZ(), point.getX(), box.maxY(), point.getZ());
                result = result == null ? corner : result.include(corner);
            }
        }
        return result;
    }

    private static boolean disjoint(Set<String> first, Set<String> second) {
        for (String value : first) if (second.contains(value)) return false;
        return true;
    }

    private record OrientedConnector(String type, BlockPos localPos, Direction facing, Bounds clearance, boolean primary) {
    }

    private record OverlapHit(Bounds bounds, String category, String owner) {
    }

    private record Candidate(
        PlacedNode node,
        BlockPos nextConnector,
        Direction nextFacing,
        int rolePenalty,
        int qualityScore,
        boolean setPiece
    ) {
    }

    public record GenerationContract(int generatorVersion, String eligibleTemplatePoolFingerprint) {
        public GenerationContract {
            if (generatorVersion <= 0) throw new IllegalArgumentException("Generator version must be positive");
            if (eligibleTemplatePoolFingerprint == null || eligibleTemplatePoolFingerprint.isBlank()) {
                throw new IllegalArgumentException("Eligible template pool fingerprint must be present");
            }
        }
    }

    public record SelectionContext(
        int totalRoomCount,
        List<String> priorTemplateIds,
        Set<String> excludedFirstTemplateIds,
        Map<Integer, String> requiredTemplateIdsByRoomIndex,
        String visualTheme
    ) {
        public SelectionContext {
            totalRoomCount = Math.max(1, totalRoomCount);
            priorTemplateIds = priorTemplateIds == null ? List.of() : List.copyOf(priorTemplateIds);
            excludedFirstTemplateIds = excludedFirstTemplateIds == null ? Set.of() : Set.copyOf(excludedFirstTemplateIds);
            requiredTemplateIdsByRoomIndex = requiredTemplateIdsByRoomIndex == null
                ? Map.of() : Map.copyOf(requiredTemplateIdsByRoomIndex);
            visualTheme = visualTheme == null ? "" : visualTheme.trim().toLowerCase(java.util.Locale.ROOT);
        }

        public SelectionContext(
            int totalRoomCount,
            List<String> priorTemplateIds,
            Set<String> excludedFirstTemplateIds,
            Map<Integer, String> requiredTemplateIdsByRoomIndex
        ) {
            this(totalRoomCount, priorTemplateIds, excludedFirstTemplateIds, requiredTemplateIdsByRoomIndex, "");
        }

        public SelectionContext(int totalRoomCount, List<String> priorTemplateIds) {
            this(totalRoomCount, priorTemplateIds, Set.of(), Map.of());
        }

        public SelectionContext(
            int totalRoomCount,
            List<String> priorTemplateIds,
            Set<String> excludedFirstTemplateIds
        ) {
            this(totalRoomCount, priorTemplateIds, excludedFirstTemplateIds, Map.of());
        }
    }

    public record PlacedNode(
        int roomIndex,
        DungeonRoomRole role,
        String objectiveType,
        String templateId,
        Rotation rotation,
        BlockPos placementOrigin,
        Bounds bounds,
        BlockPos incomingConnector,
        Direction incomingFacing,
        BlockPos outgoingConnector,
        Direction outgoingFacing,
        int outgoingConnectorCount,
        int placementCost,
        Set<String> themes
    ) {
        public PlacedNode {
            themes = Set.copyOf(themes);
        }
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Bounds between(BlockPos first, BlockPos second) {
            return new Bounds(
                Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ())
            );
        }

        public Bounds move(BlockPos offset) {
            return new Bounds(
                minX + offset.getX(), minY + offset.getY(), minZ + offset.getZ(),
                maxX + offset.getX(), maxY + offset.getY(), maxZ + offset.getZ()
            );
        }

        public Bounds include(Bounds other) {
            return new Bounds(
                Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ)
            );
        }

        public boolean overlaps(Bounds other) {
            return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        @Override
        public String toString() {
            return minX + " " + minY + " " + minZ + " -> " + maxX + " " + maxY + " " + maxZ;
        }
    }

    public record Result(
        boolean solved,
        String reason,
        List<PlacedNode> nodes,
        int deepestDepth,
        int visitedNodes,
        int backtracks,
        double elapsedMillis,
        Map<String, Integer> rejectionCounts,
        List<String> rejectionDetails
    ) {
    }
}
