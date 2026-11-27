package com.mineclawd.tensuraadditions.compat.moreskills;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BerettaAttackExecutorRegistry {
    private static final Map<ResourceLocation, BerettaAttackExecutor> EXECUTORS = new LinkedHashMap<>();

    private BerettaAttackExecutorRegistry() {
    }

    public static void register(ResourceLocation attackId, BerettaAttackExecutor executor) {
        if (EXECUTORS.putIfAbsent(attackId, executor) != null) {
            throw new IllegalStateException("Duplicate Beretta attack executor: " + attackId);
        }
    }

    public static void replace(ResourceLocation attackId, BerettaAttackExecutor executor) {
        EXECUTORS.put(attackId, executor);
    }

    public static BerettaAttackExecutor get(ResourceLocation attackId) {
        return EXECUTORS.get(attackId);
    }
}
