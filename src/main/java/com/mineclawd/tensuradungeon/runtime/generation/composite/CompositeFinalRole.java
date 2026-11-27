package com.mineclawd.tensuradungeon.runtime.generation.composite;

public enum CompositeFinalRole {
    ROOM_CONTROLLER(CompositeSocketCapability.SYSTEM),
    MOB_RESET(CompositeSocketCapability.SYSTEM),
    GROUND_SPAWN(CompositeSocketCapability.GROUND_SPAWN),
    RANGED_SPAWN(CompositeSocketCapability.RANGED_SPAWN),
    ELITE_SPAWN(CompositeSocketCapability.ELITE_SPAWN),
    BARRIER(CompositeSocketCapability.BARRIER);

    private final CompositeSocketCapability capability;

    CompositeFinalRole(CompositeSocketCapability capability) {
        this.capability = capability;
    }

    public CompositeSocketCapability capability() {
        return capability;
    }
}
