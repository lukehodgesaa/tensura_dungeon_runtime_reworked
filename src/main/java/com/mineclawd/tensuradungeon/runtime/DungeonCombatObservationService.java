package com.mineclawd.tensuradungeon.runtime;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class DungeonCombatObservationService {
    static final ConcurrentHashMap<ResourceKey<Level>, DungeonCombatObservationStore> STORES =
        new ConcurrentHashMap<>();

    private DungeonCombatObservationService() {
    }

    public static DungeonCombatObservationStore store(ServerLevel level) {
        return STORES.computeIfAbsent(level.dimension(), key -> new DungeonCombatObservationStore());
    }

    public static void clear(ServerLevel level) {
        STORES.remove(level.dimension());
    }
}
