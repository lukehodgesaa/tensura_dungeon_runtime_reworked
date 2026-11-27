package com.mineclawd.tensuradungeon.runtime.enemy;

public record DungeonEnemyFamily(String id) {
    public static DungeonEnemyFamily of(String id) {
        return new DungeonEnemyFamily(id);
    }
}
