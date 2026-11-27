package com.mineclawd.tensuradungeon.runtime.enemy;

import java.util.Locale;
import java.util.Optional;

public enum DungeonEnemyRank {
    F(1),
    E(6),
    D(51),
    C(76),
    B(126),
    A(176),
    S(226),
    SS(276);

    private static final int INTRODUCTION_FLOORS = 20;
    private final int unlockFloor;

    DungeonEnemyRank(int unlockFloor) {
        this.unlockFloor = unlockFloor;
    }

    public int unlockFloor() {
        return unlockFloor;
    }

    public static Optional<DungeonEnemyRank> fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(label.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static DungeonEnemyRank ordinaryCeiling(int floor) {
        int boundedFloor = Math.max(1, floor);
        DungeonEnemyRank ceiling = F;
        for (DungeonEnemyRank rank : values()) {
            if (boundedFloor >= rank.unlockFloor) {
                ceiling = rank;
            }
        }
        return ceiling;
    }

    public static boolean isEligible(
        int floor,
        DungeonEnemyRank rank,
        boolean eliteRequest,
        boolean swarmActive
    ) {
        DungeonEnemyRank ceiling = selectionCeiling(floor, eliteRequest);
        int distance = ceiling.ordinal() - rank.ordinal();
        return distance >= 0 && (distance <= 2 || swarmActive);
    }

    public static boolean isKinEligible(
        int floor,
        DungeonEnemyRank rank,
        boolean eliteRequest,
        boolean swarmActive
    ) {
        DungeonEnemyRank ordinaryCeiling = ordinaryCeiling(floor);
        DungeonEnemyRank kinCeiling = ordinaryCeiling == SS
            ? SS
            : values()[ordinaryCeiling.ordinal() + 1];
        int distance = kinCeiling.ordinal() - rank.ordinal();
        return distance >= 0 && (distance <= 2 || swarmActive);
    }

    public static int weightMultiplier(int floor, DungeonEnemyRank rank, boolean eliteRequest) {
        DungeonEnemyRank ceiling = selectionCeiling(floor, eliteRequest);
        int distance = ceiling.ordinal() - rank.ordinal();
        if (distance < 0) {
            return 0;
        }
        if (distance == 0) {
            int introductionProgress = Math.max(0, floor - ceiling.unlockFloor);
            return 1 + Math.min(5, introductionProgress / Math.max(1, INTRODUCTION_FLOORS / 5));
        }
        return distance == 1 ? 4 : 1;
    }

    private static DungeonEnemyRank selectionCeiling(int floor, boolean eliteRequest) {
        DungeonEnemyRank ordinary = ordinaryCeiling(floor);
        if (!eliteRequest || ordinary == SS) {
            return ordinary;
        }
        return values()[ordinary.ordinal() + 1];
    }
}
