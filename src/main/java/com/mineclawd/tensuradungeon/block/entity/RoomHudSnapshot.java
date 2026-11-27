package com.mineclawd.tensuradungeon.block.entity;

public record RoomHudSnapshot(
    boolean objectiveActive,
    RoomControllerBlockEntity.RoomState roomState,
    RoomObjectiveType objectiveType,
    int objectiveProgress,
    int objectiveTarget,
    int objectiveRemainingTicks,
    int objectiveDurationTicks,
    int enemiesAlive,
    int enemiesSpawned,
    String appliedEnemyModifierSummary,
    int compositionSpent,
    int compositionBudget,
    String compositionSummary,
    int enemiesPending,
    int waveSize,
    int wavesSpawned,
    int waveCooldownTicks,
    String waveSummary,
    String modifierRoleSummary
) {
}
