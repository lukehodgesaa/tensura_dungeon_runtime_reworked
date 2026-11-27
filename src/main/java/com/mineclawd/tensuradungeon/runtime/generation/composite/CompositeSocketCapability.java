package com.mineclawd.tensuradungeon.runtime.generation.composite;

import java.util.Locale;

public enum CompositeSocketCapability {
    SYSTEM("system"),
    GROUND_SPAWN("ground_spawn"),
    RANGED_SPAWN("ranged_spawn"),
    ELITE_SPAWN("elite_spawn"),
    BARRIER("barrier");

    private final String serializedName;

    CompositeSocketCapability(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CompositeSocketCapability parse(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (CompositeSocketCapability capability : values()) {
            if (capability.serializedName.equals(normalized)) return capability;
        }
        return null;
    }
}
