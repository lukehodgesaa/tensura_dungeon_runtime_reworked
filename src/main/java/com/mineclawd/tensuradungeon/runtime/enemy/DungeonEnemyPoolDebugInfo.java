package com.mineclawd.tensuradungeon.runtime.enemy;

public record DungeonEnemyPoolDebugInfo(
    boolean registryEnabled,
    String spawnSource,
    String requestedRole,
    String selectedEntityId,
    String selectedFamily,
    String selectedSourceMod,
    String fallbackReason,
    int candidateCount,
    String error
) {
    public static DungeonEnemyPoolDebugInfo none() {
        return new DungeonEnemyPoolDebugInfo(false, "NONE", "NONE", "NONE", "NONE", "NONE", "NONE", 0, "NONE");
    }
}
