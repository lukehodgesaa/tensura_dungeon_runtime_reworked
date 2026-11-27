package com.mineclawd.tensuradungeon.runtime.generation;

public enum DungeonRoomRole {
    ENTRY,
    COMBAT,
    LARGE_COMBAT,
    ARENA,
    CORRIDOR,
    TRAVERSAL,
    INTERSECTION,
    INTERACTION,
    TREASURE,
    SAFE,
    FINALE;

    public static DungeonRoomRole parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
