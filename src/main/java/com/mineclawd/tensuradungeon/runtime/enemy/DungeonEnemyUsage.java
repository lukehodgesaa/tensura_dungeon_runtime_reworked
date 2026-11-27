package com.mineclawd.tensuradungeon.runtime.enemy;

public enum DungeonEnemyUsage {
    NORMAL,
    ELITE_ONLY,
    ELITE_OR_BOSS,
    BOSS_ONLY,
    LATE_BOSS_ONLY;

    public boolean allowsCurrentRole(DungeonEnemyRole requestedRole) {
        boolean eliteRequest = requestedRole == DungeonEnemyRole.ELITE_CANDIDATE;
        return switch (this) {
            case NORMAL -> !eliteRequest;
            case ELITE_ONLY, ELITE_OR_BOSS -> eliteRequest;
            case BOSS_ONLY, LATE_BOSS_ONLY -> false;
        };
    }
}
