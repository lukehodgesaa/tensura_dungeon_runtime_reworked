package com.mineclawd.tensuradungeon.runtime.generation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record DungeonTemplateMechanicalMetadata(
    String id,
    int width,
    int height,
    int depth,
    long volume,
    int serializedBlocks,
    int nonAirBlocks,
    int fluidBearingBlocks,
    int entityCount,
    int estimatedPlacementCost,
    List<Connector> connectors,
    Map<String, Integer> markerCounts,
    Map<String, List<BlockPos>> markerPositions,
    boolean solverEligible,
    List<String> rejectionReasons
) {
    public DungeonTemplateMechanicalMetadata {
        connectors = connectors == null ? List.of() : List.copyOf(connectors);
        markerCounts = markerCounts == null ? Map.of() : Map.copyOf(markerCounts);
        if (markerPositions == null || markerPositions.isEmpty()) {
            markerPositions = Map.of();
        } else {
            Map<String, List<BlockPos>> copied = new LinkedHashMap<>();
            markerPositions.forEach((name, positions) -> copied.put(
                name,
                positions == null ? List.of() : positions.stream().map(BlockPos::immutable).toList()
            ));
            markerPositions = Map.copyOf(copied);
        }
        rejectionReasons = rejectionReasons == null ? List.of() : List.copyOf(rejectionReasons);
    }

    public DungeonTemplateMechanicalMetadata(
        String id,
        int width,
        int height,
        int depth,
        long volume,
        int estimatedPlacementCost,
        List<Connector> connectors,
        Map<String, Integer> markerCounts,
        boolean solverEligible,
        List<String> rejectionReasons
    ) {
        this(
            id, width, height, depth, volume,
            0, 0, 0, 0, estimatedPlacementCost, connectors, markerCounts, Map.of(),
            solverEligible, rejectionReasons
        );
    }

    public int markerCount(String marker) {
        return markerCounts.getOrDefault(marker, 0);
    }

    public List<BlockPos> markerPositions(String marker) {
        return markerPositions.getOrDefault(marker, List.of());
    }

    public ConnectorMode connectorMode() {
        return connectors.stream().anyMatch(connector -> "connector_exit".equals(connector.type()))
            ? ConnectorMode.TYPED : ConnectorMode.LEGACY;
    }

    public List<Connector> incomingConnectors() {
        if (connectorMode() == ConnectorMode.LEGACY) return connectors;
        return connectors.stream().filter(connector -> "floor_connector_anchor".equals(connector.type())).toList();
    }

    public enum ConnectorMode { TYPED, LEGACY }

    public record Connector(
        String type,
        BlockPos localPos,
        Direction facing,
        ClearanceBox clearance,
        boolean primary
    ) {
    }

    public record ClearanceBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }
}
