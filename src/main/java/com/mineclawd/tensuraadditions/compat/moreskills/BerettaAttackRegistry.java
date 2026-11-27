package com.mineclawd.tensuraadditions.compat.moreskills;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BerettaAttackRegistry {
    private static final Map<ResourceLocation, BerettaAttackDefinition> BUILT_INS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, BerettaAttackDefinition> DATA_DEFINITIONS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, BerettaAttackDefinition> OVERRIDES = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private BerettaAttackRegistry() {
    }

    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }

        bootstrapped = true;
        BerettaReferenceAttacks.register();
    }

    public static void register(BerettaAttackDefinition definition) {
        if (view().containsKey(definition.id())) {
            throw new IllegalStateException("Duplicate Beretta attack definition: " + definition.id());
        }

        OVERRIDES.put(definition.id(), definition);
    }

    public static void replace(BerettaAttackDefinition definition) {
        OVERRIDES.put(definition.id(), definition);
    }

    static void registerBuiltIn(BerettaAttackDefinition definition) {
        BerettaAttackDefinition previous = BUILT_INS.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalStateException("Duplicate built-in Beretta attack definition: " + definition.id());
        }
    }

    static void replaceDataDefinitions(Map<ResourceLocation, BerettaAttackDefinition> definitions) {
        DATA_DEFINITIONS.clear();
        DATA_DEFINITIONS.putAll(definitions);
    }

    public static BerettaAttackDefinition get(ResourceLocation id) {
        return view().get(id);
    }

    public static boolean contains(ResourceLocation id) {
        return view().containsKey(id);
    }

    public static Collection<BerettaAttackDefinition> all() {
        return Collections.unmodifiableCollection(view().values());
    }

    static Map<ResourceLocation, BerettaAttackDefinition> snapshot() {
        return Map.copyOf(view());
    }

    private static Map<ResourceLocation, BerettaAttackDefinition> view() {
        Map<ResourceLocation, BerettaAttackDefinition> view = new LinkedHashMap<>(BUILT_INS);
        view.putAll(DATA_DEFINITIONS);
        view.putAll(OVERRIDES);
        return view;
    }
}
