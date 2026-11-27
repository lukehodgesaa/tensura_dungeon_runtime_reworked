package com.mineclawd.tensuradungeon.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;

public record StructureMarkerReadinessReport(
    BlockPos nearestControllerPos,
    RoomProfile roomProfile,
    String boundsSummary,
    int roomControllerCount,
    int boundsAnchorCount,
    int meleeSpawnCount,
    int rangedSpawnCount,
    int flankSpawnCount,
    int highgroundSpawnCount,
    int eliteSpawnCount,
    int bossSpawnCount,
    int barrierAnchorCount,
    int objectiveAnchorCount,
    int exitAnchorCount,
    int safeZoneAnchorCount,
    int mobResetAnchorCount,
    int floorEntryAnchorCount,
    int hubSpawnAnchorCount,
    int hubReturnAnchorCount,
    int hubSafeZoneAnchorCount,
    int hubGateAnchorCount,
    int hubFloorSelectAnchorCount,
    int floorBuildOriginCount,
    List<String> missingRequiredMarkers,
    List<String> warnings,
    boolean readinessPass,
    boolean safeForFutureJigsawUse
) {
    public String readinessLabel() {
        return readinessPass ? "PASS" : "FAIL";
    }

    public String jigsawTemplateReadinessLabel() {
        return safeForFutureJigsawUse ? "PASS" : "FAIL";
    }

    public String missingSummary() {
        return missingRequiredMarkers.isEmpty() ? "NONE" : String.join(", ", missingRequiredMarkers);
    }

    public String warningsSummary() {
        return warnings.isEmpty() ? "NONE" : String.join("; ", warnings);
    }
}
