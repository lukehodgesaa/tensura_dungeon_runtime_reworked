package com.mineclawd.tensuradungeon.block.entity;

public enum RoomObjectiveType {
    CLEAR_ALL(false),
    SURVIVE_TIMER(false),
    KILL_ELITE(false),
    ACTIVATE_RUNES(true);

    private final boolean requiresObjectiveAnchor;

    RoomObjectiveType(boolean requiresObjectiveAnchor) {
        this.requiresObjectiveAnchor = requiresObjectiveAnchor;
    }

    public boolean requiresObjectiveAnchor() {
        return requiresObjectiveAnchor;
    }

    public boolean usesTimer() {
        return this == SURVIVE_TIMER;
    }

    public boolean usesEliteTarget() {
        return this == KILL_ELITE;
    }

    public boolean usesRuneTargets() {
        return this == ACTIVATE_RUNES;
    }

    public static RoomObjectiveType byName(String name) {
        for (RoomObjectiveType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return CLEAR_ALL;
    }
}
