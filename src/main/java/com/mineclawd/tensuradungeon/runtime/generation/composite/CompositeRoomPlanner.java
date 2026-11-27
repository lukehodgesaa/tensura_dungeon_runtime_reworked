package com.mineclawd.tensuradungeon.runtime.generation.composite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class CompositeRoomPlanner {
    private static final int MAX_ATTEMPTS = 2_000;
    private static final List<Rotation> ROTATIONS = List.of(
        Rotation.NONE,
        Rotation.CLOCKWISE_90,
        Rotation.CLOCKWISE_180,
        Rotation.COUNTERCLOCKWISE_90
    );

    private CompositeRoomPlanner() {
    }

    public static Result planAndCompile(
        String id,
        String theme,
        List<CompositeSegmentDefinition> catalog,
        CompositeRoomRequirements requirements,
        long seed,
        int minimumSegments,
        int maximumSegments
    ) {
        if (minimumSegments < 3 || maximumSegments < minimumSegments || maximumSegments > 12) {
            return Result.failed("INVALID_COMPOSITE_SEGMENT_LIMITS");
        }
        List<CompositeSegmentDefinition> matching = catalog == null ? List.of() : catalog.stream()
            .filter(segment -> theme != null && theme.equals(segment.theme()))
            .filter(CompositeSegmentDefinition::isCompositeEligible)
            .filter(CompositeSegmentDefinition::connectorCountMatchesKind)
            .toList();
        List<CompositeSegmentDefinition> entrances = matching.stream()
            .filter(segment -> segment.kind() == CompositeSegmentDefinition.Kind.ENTRANCE)
            .toList();
        List<CompositeSegmentDefinition> specializedExits = matching.stream()
            .filter(segment -> segment.kind() == CompositeSegmentDefinition.Kind.EXIT)
            .toList();
        List<CompositeSegmentDefinition> terminalAdapters = specializedExits.isEmpty()
            ? entrances : specializedExits;
        if (requirements.externalConnectorCount() > 2) {
            List<CompositeSegmentDefinition> junctions = matching.stream()
                .filter(segment -> segment.kind().isIntersection())
                .filter(segment -> segment.connectors().size() == requirements.externalConnectorCount())
                .toList();
            if (entrances.isEmpty() || terminalAdapters.isEmpty() || junctions.isEmpty()) {
                return Result.failed("COMPOSITE_INTERSECTION_REQUIRES_BOUNDARY_ADAPTER_AND_JUNCTION");
            }
            IntersectionSearch search = new IntersectionSearch(
                id,
                requirements,
                seed,
                minimumSegments,
                maximumSegments,
                ranked(entrances, seed, 0),
                rankedTerminalAdapters(terminalAdapters, seed, 1),
                ranked(junctions, seed, 2)
            );
            CompositeRoomCompiler.Result compiled = search.run();
            return compiled != null && compiled.success()
                ? new Result(true, compiled.room(), List.of(), search.attempts)
                : new Result(false, null, List.of(
                    search.lastFailure == null
                        ? "COMPOSITE_INTERSECTION_SEARCH_EXHAUSTED" : search.lastFailure
                ), search.attempts);
        }
        List<CompositeSegmentDefinition> linearMatching = matching.stream()
            .filter(CompositeSegmentDefinition::isLinearPrototypeEligible)
            .toList();
        entrances = linearMatching.stream()
            .filter(segment -> segment.kind() == CompositeSegmentDefinition.Kind.ENTRANCE)
            .toList();
        specializedExits = linearMatching.stream()
            .filter(segment -> segment.kind() == CompositeSegmentDefinition.Kind.EXIT)
            .toList();
        terminalAdapters = specializedExits.isEmpty() ? entrances : specializedExits;
        List<CompositeSegmentDefinition> bodies = linearMatching.stream()
            .filter(segment -> segment.kind().isBody())
            .toList();
        if (entrances.isEmpty() || terminalAdapters.isEmpty() || bodies.isEmpty()) {
            return Result.failed("COMPOSITE_CATALOG_REQUIRES_BOUNDARY_ADAPTER_AND_BODY");
        }

        Search search = new Search(
            id, requirements, seed, minimumSegments, maximumSegments,
            ranked(entrances, seed, 0), rankedTerminalAdapters(terminalAdapters, seed, 1), bodies
        );
        CompositeRoomCompiler.Result compiled = search.run();
        return compiled != null && compiled.success()
            ? new Result(true, compiled.room(), List.of(), search.attempts)
            : new Result(false, null, List.of(
                search.lastFailure == null ? "COMPOSITE_SEARCH_EXHAUSTED" : search.lastFailure
            ), search.attempts);
    }

    private static final class IntersectionSearch {
        private final String id;
        private final CompositeRoomRequirements requirements;
        private final long seed;
        private final int minimumSegments;
        private final int maximumSegments;
        private final List<CompositeSegmentDefinition> entrances;
        private final List<CompositeSegmentDefinition> exits;
        private final List<CompositeSegmentDefinition> junctions;
        private int attempts;
        @Nullable
        private String lastFailure;

        private IntersectionSearch(
            String id,
            CompositeRoomRequirements requirements,
            long seed,
            int minimumSegments,
            int maximumSegments,
            List<CompositeSegmentDefinition> entrances,
            List<CompositeSegmentDefinition> exits,
            List<CompositeSegmentDefinition> junctions
        ) {
            this.id = id;
            this.requirements = requirements;
            this.seed = seed;
            this.minimumSegments = minimumSegments;
            this.maximumSegments = maximumSegments;
            this.entrances = entrances;
            this.exits = exits;
            this.junctions = junctions;
        }

        @Nullable
        private CompositeRoomCompiler.Result run() {
            int exactSegments = 1 + requirements.externalConnectorCount();
            if (exactSegments < minimumSegments || exactSegments > maximumSegments) {
                lastFailure = "COMPOSITE_INTERSECTION_SEGMENT_LIMITS_REQUIRE_" + exactSegments;
                return null;
            }
            for (CompositeSegmentDefinition junction : junctions) {
                for (Rotation rotation : ROTATIONS) {
                    CompositeSegmentPlacement center = new CompositeSegmentPlacement(
                        junction, BlockPos.ZERO, rotation
                    );
                    List<Frontier> arms = junction.connectors().stream()
                        .filter(connector -> connector.role().mayPair())
                        .map(connector -> new Frontier(
                            transform(center, connector.pos()), rotation.rotate(connector.facing())
                        ))
                        .sorted(Comparator
                            .comparing((Frontier frontier) -> frontier.facing().getName())
                            .thenComparingLong(frontier -> frontier.pos().asLong()))
                        .toList();
                    if (arms.size() != requirements.externalConnectorCount()) {
                        lastFailure = "COMPOSITE_INTERSECTION_JUNCTION_ARM_COUNT_" + arms.size();
                        continue;
                    }
                    List<CompositeSegmentPlacement> placed = new ArrayList<>();
                    placed.add(center);
                    List<BoundingBox> bounds = new ArrayList<>();
                    bounds.add(CompositeRoomCompiler.bounds(center));
                    CompositeRoomCompiler.Result result = attachArm(0, arms, placed, bounds);
                    if (result != null && result.success()) return result;
                    if (attempts >= MAX_ATTEMPTS) return null;
                }
            }
            return null;
        }

        @Nullable
        private CompositeRoomCompiler.Result attachArm(
            int armIndex,
            List<Frontier> arms,
            List<CompositeSegmentPlacement> placed,
            List<BoundingBox> placedBounds
        ) {
            if (attempts >= MAX_ATTEMPTS) return null;
            if (armIndex >= arms.size()) {
                attempts++;
                CompositeRoomCompiler.Result result = CompositeRoomCompiler.compile(
                    id, placed, requirements, seed
                );
                if (!result.success()) lastFailure = result.failureSummary();
                return result;
            }
            List<CompositeSegmentDefinition> adapters = armIndex == 0 ? entrances : exits;
            for (CompositeSegmentDefinition adapter : rankedTerminalAdapters(
                adapters, seed, armIndex + 3
            )) {
                for (CompositeSegmentPlacement placement : adapterPlacements(
                    adapter, arms.get(armIndex), placedBounds, armIndex
                )) {
                    attempts++;
                    List<CompositeSegmentPlacement> nextPlaced = new ArrayList<>(placed);
                    nextPlaced.add(placement);
                    List<BoundingBox> nextBounds = new ArrayList<>(placedBounds);
                    nextBounds.add(CompositeRoomCompiler.bounds(placement));
                    CompositeRoomCompiler.Result result = attachArm(
                        armIndex + 1, arms, nextPlaced, nextBounds
                    );
                    if (result != null && result.success()) return result;
                    if (attempts >= MAX_ATTEMPTS) return null;
                }
            }
            return null;
        }

        private List<CompositeSegmentPlacement> adapterPlacements(
            CompositeSegmentDefinition adapter,
            Frontier frontier,
            List<BoundingBox> placedBounds,
            int armIndex
        ) {
            if (!adapter.isLinearPrototypeEligible()) return List.of();
            List<CompositeSegmentPlacement> result = new ArrayList<>();
            for (Rotation rotation : ROTATIONS) {
                for (int incomingIndex = 0; incomingIndex < adapter.connectors().size(); incomingIndex++) {
                    CompositeSegmentDefinition.Connector incoming = adapter.connectors().get(incomingIndex);
                    if (!incoming.role().mayPair()) continue;
                    if (rotation.rotate(incoming.facing()) != frontier.facing().getOpposite()) continue;
                    int outgoingIndex = incomingIndex == 0 ? 1 : 0;
                    if (!adapter.connectors().get(outgoingIndex).role().mayRemainExternal()) continue;
                    BlockPos target = frontier.pos().relative(frontier.facing());
                    BlockPos origin = target.subtract(CompositeRoomCompiler.rotate(incoming.pos(), rotation));
                    CompositeSegmentPlacement placement = new CompositeSegmentPlacement(
                        adapter, origin, rotation
                    );
                    BoundingBox candidateBounds = CompositeRoomCompiler.bounds(placement);
                    if (placedBounds.stream().anyMatch(
                        existing -> CompositeRoomCompiler.intersects(existing, candidateBounds)
                    )) continue;
                    result.add(placement);
                }
            }
            return result.stream().sorted(Comparator
                .comparingLong((CompositeSegmentPlacement placement) -> rank(
                    seed,
                    armIndex + 10,
                    placement.segment().id() + "@" + placement.rotation()
                ))
                .thenComparingLong(placement -> placement.origin().asLong()))
                .toList();
        }
    }

    private static List<CompositeSegmentDefinition> ranked(
        List<CompositeSegmentDefinition> values,
        long seed,
        int depth
    ) {
        return values.stream().sorted(Comparator
            .comparingLong((CompositeSegmentDefinition segment) -> rank(seed, depth, segment.id().toString()))
            .thenComparing(segment -> segment.id().toString())).toList();
    }

    private static List<CompositeSegmentDefinition> rankedTerminalAdapters(
        List<CompositeSegmentDefinition> values,
        long seed,
        int depth
    ) {
        return values.stream().sorted(Comparator
            .comparingInt((CompositeSegmentDefinition segment) ->
                segment.kind() == CompositeSegmentDefinition.Kind.EXIT ? 0 : 1
            )
            .thenComparingLong(segment -> rank(seed, depth, segment.id().toString()))
            .thenComparing(segment -> segment.id().toString())).toList();
    }

    private static long rank(long seed, int depth, String id) {
        long value = seed ^ ((long) id.hashCode() << 32) ^ depth * 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    private static BlockPos transform(CompositeSegmentPlacement placement, BlockPos local) {
        return placement.origin().offset(
            CompositeRoomCompiler.rotate(local, placement.rotation())
        ).immutable();
    }

    private static final class Search {
        private final String id;
        private final CompositeRoomRequirements requirements;
        private final long seed;
        private final int minimumSegments;
        private final int maximumSegments;
        private final List<CompositeSegmentDefinition> entrances;
        private final List<CompositeSegmentDefinition> exits;
        private final List<CompositeSegmentDefinition> bodies;
        private int attempts;
        @Nullable
        private String lastFailure;

        private Search(
            String id,
            CompositeRoomRequirements requirements,
            long seed,
            int minimumSegments,
            int maximumSegments,
            List<CompositeSegmentDefinition> entrances,
            List<CompositeSegmentDefinition> exits,
            List<CompositeSegmentDefinition> bodies
        ) {
            this.id = id;
            this.requirements = requirements;
            this.seed = seed;
            this.minimumSegments = minimumSegments;
            this.maximumSegments = maximumSegments;
            this.entrances = entrances;
            this.exits = exits;
            this.bodies = bodies;
        }

        @Nullable
        private CompositeRoomCompiler.Result run() {
            for (CompositeSegmentDefinition entrance : entrances) {
                for (int outgoingIndex : startingOutgoingIndices(entrance)) {
                    CompositeSegmentPlacement placement = new CompositeSegmentPlacement(
                        entrance, BlockPos.ZERO, Rotation.NONE
                    );
                    CompositeSegmentDefinition.Connector outgoing = entrance.connectors().get(outgoingIndex);
                    Frontier frontier = new Frontier(
                        transform(placement, outgoing.pos()),
                        placement.rotation().rotate(outgoing.facing())
                    );
                    List<CompositeSegmentPlacement> placed = new ArrayList<>();
                    placed.add(placement);
                    List<BoundingBox> bounds = new ArrayList<>();
                    bounds.add(CompositeRoomCompiler.bounds(placement));
                    CompositeRoomCompiler.Result result = search(placed, bounds, frontier, new HashMap<>());
                    if (result != null && result.success()) return result;
                    if (attempts >= MAX_ATTEMPTS) return null;
                }
            }
            return null;
        }

        @Nullable
        private CompositeRoomCompiler.Result search(
            List<CompositeSegmentPlacement> placed,
            List<BoundingBox> placedBounds,
            Frontier frontier,
            Map<String, Integer> useCounts
        ) {
            if (attempts >= MAX_ATTEMPTS) return null;
            boolean hasCombat = placed.stream().anyMatch(
                placement -> placement.segment().kind() == CompositeSegmentDefinition.Kind.COMBAT
            );
            if (placed.size() + 1 >= minimumSegments && hasCombat) {
                for (CompositeSegmentDefinition exit : rankedTerminalAdapters(
                    exits, seed, placed.size()
                )) {
                    for (Attachment attachment : attachments(exit, frontier, placedBounds, true)) {
                        attempts++;
                        List<CompositeSegmentPlacement> completed = new ArrayList<>(placed);
                        completed.add(attachment.placement());
                        CompositeRoomCompiler.Result result = CompositeRoomCompiler.compile(
                            id, completed, requirements, seed
                        );
                        if (result.success()) return result;
                        lastFailure = result.failureSummary();
                        if (attempts >= MAX_ATTEMPTS) return null;
                    }
                }
            }
            if (placed.size() + 1 >= maximumSegments) return null;

            List<CompositeSegmentDefinition> orderedBodies = ranked(bodies, seed, placed.size());
            for (CompositeSegmentDefinition body : orderedBodies) {
                int used = useCounts.getOrDefault(body.id().toString(), 0);
                if (used >= 2) continue;
                for (Attachment attachment : attachments(body, frontier, placedBounds, false)) {
                    attempts++;
                    List<CompositeSegmentPlacement> nextPlaced = new ArrayList<>(placed);
                    nextPlaced.add(attachment.placement());
                    List<BoundingBox> nextBounds = new ArrayList<>(placedBounds);
                    nextBounds.add(attachment.bounds());
                    Map<String, Integer> nextUses = new HashMap<>(useCounts);
                    nextUses.put(body.id().toString(), used + 1);
                    CompositeRoomCompiler.Result result = search(
                        nextPlaced, nextBounds, attachment.nextFrontier(), nextUses
                    );
                    if (result != null && result.success()) return result;
                    if (attempts >= MAX_ATTEMPTS) return null;
                }
            }
            return null;
        }

        private List<Attachment> attachments(
            CompositeSegmentDefinition segment,
            Frontier frontier,
            List<BoundingBox> placedBounds,
            boolean terminal
        ) {
            List<Attachment> result = new ArrayList<>();
            for (Rotation rotation : ROTATIONS) {
                for (int incomingIndex = 0; incomingIndex < segment.connectors().size(); incomingIndex++) {
                    CompositeSegmentDefinition.Connector incoming = segment.connectors().get(incomingIndex);
                    if (!incoming.role().mayPair()) continue;
                    if (rotation.rotate(incoming.facing()) != frontier.facing().getOpposite()) continue;
                    BlockPos target = frontier.pos().relative(frontier.facing());
                    BlockPos rotatedIncoming = CompositeRoomCompiler.rotate(incoming.pos(), rotation);
                    BlockPos origin = target.subtract(rotatedIncoming);
                    CompositeSegmentPlacement placement = new CompositeSegmentPlacement(segment, origin, rotation);
                    BoundingBox candidateBounds = CompositeRoomCompiler.bounds(placement);
                    if (placedBounds.stream().anyMatch(existing -> CompositeRoomCompiler.intersects(existing, candidateBounds))) {
                        continue;
                    }
                    int outgoingIndex = incomingIndex == 0 ? 1 : 0;
                    CompositeSegmentDefinition.Connector outgoing = segment.connectors().get(outgoingIndex);
                    if (terminal) {
                        if (!outgoing.role().mayRemainExternal()) continue;
                    } else if (!outgoing.role().mayPair()) {
                        continue;
                    }
                    result.add(new Attachment(
                        placement,
                        candidateBounds,
                        new Frontier(
                            transform(placement, outgoing.pos()),
                            rotation.rotate(outgoing.facing())
                        )
                    ));
                }
            }
            result.sort(Comparator
                .comparingLong((Attachment attachment) -> rank(
                    seed,
                    placedBounds.size(),
                    attachment.placement().segment().id() + "@" + attachment.placement().rotation()
                ))
                .thenComparingLong(attachment -> attachment.placement().origin().asLong()));
            return result;
        }

        private static List<Integer> startingOutgoingIndices(
            CompositeSegmentDefinition entrance
        ) {
            List<Integer> indices = new ArrayList<>();
            for (int outgoingIndex = 0; outgoingIndex < entrance.connectors().size(); outgoingIndex++) {
                int externalIndex = outgoingIndex == 0 ? 1 : 0;
                if (entrance.connectors().get(outgoingIndex).role().mayPair()
                    && entrance.connectors().get(externalIndex).role().mayRemainExternal()) {
                    indices.add(outgoingIndex);
                }
            }
            return indices;
        }

        private static BlockPos transform(CompositeSegmentPlacement placement, BlockPos local) {
            return placement.origin().offset(
                CompositeRoomCompiler.rotate(local, placement.rotation())
            ).immutable();
        }
    }

    private record Frontier(BlockPos pos, Direction facing) {
    }

    private record Attachment(
        CompositeSegmentPlacement placement,
        BoundingBox bounds,
        Frontier nextFrontier
    ) {
    }

    public record Result(
        boolean success,
        @Nullable CompiledCompositeRoom room,
        List<String> errors,
        int attempts
    ) {
        private static Result failed(String error) {
            return new Result(false, null, List.of(error), 0);
        }

        public String failureSummary() {
            return errors.isEmpty() ? "NONE" : String.join(" | ", errors);
        }
    }
}
