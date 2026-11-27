package com.mineclawd.tensuraadditions.compat.moreskills;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BerettaAttackDataLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    public static final BerettaAttackDataLoader INSTANCE = new BerettaAttackDataLoader();

    private BerettaAttackDataLoader() {
        super(GSON, "moreskills/beretta_attacks");
    }

    @Override
    protected void apply(
            Map<ResourceLocation, JsonElement> resources,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        Map<ResourceLocation, BerettaAttackDefinition> definitions = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation sourceId = entry.getKey();
            ResourceLocation definitionId = ResourceLocation.fromNamespaceAndPath(
                    sourceId.getNamespace(),
                    "moreskills/beretta/" + sourceId.getPath()
            );

            try {
                definitions.put(definitionId, parse(definitionId, entry.getValue().getAsJsonObject()));
            } catch (RuntimeException exception) {
                throw new JsonParseException("Invalid Beretta attack definition " + sourceId, exception);
            }
        }

        BerettaAttackRegistry.replaceDataDefinitions(definitions);
    }

    private static BerettaAttackDefinition parse(ResourceLocation id, JsonObject root) {
        String displayName = requiredString(root, "display_name");
        String animation = requiredString(root, "animation");
        int durationTicks = requiredInt(root, "duration_ticks");
        int cooldownTicks = intValue(root, "cooldown_ticks", 0);
        int impactTick = requiredInt(root, "impact_tick");
        BerettaPreviewPattern previewPattern = enumValue(
                BerettaPreviewPattern.class,
                root,
                "preview_pattern",
                BerettaPreviewPattern.NONE
        );
        BerettaTargetingMode targeting = enumValue(
                BerettaTargetingMode.class,
                root,
                "targeting",
                BerettaTargetingMode.SURFACE
        );
        boolean overlay = booleanValue(root, "overlay", false);
        BerettaDamageProfile damage = parseDamage(root.getAsJsonObject("damage"));
        BerettaMeteorStormProfile meteorStorm = parseMeteorStorm(
                root.has("meteor_storm") ? root.getAsJsonObject("meteor_storm") : null,
                durationTicks
        );

        JsonArray phaseArray = root.has("phases") ? root.getAsJsonArray("phases") : new JsonArray();
        List<BerettaAttackPhase> phases = phaseArray.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(phase -> new BerettaAttackPhase(
                        requiredString(phase, "id"),
                        requiredInt(phase, "start_tick"),
                        requiredInt(phase, "end_tick"),
                        enumValue(BerettaPreviewPattern.class, phase, "preview", previewPattern),
                        booleanValue(phase, "damaging", false)
                ))
                .toList();

        return new BerettaAttackDefinition(
                id,
                displayName,
                animation,
                durationTicks,
                cooldownTicks,
                impactTick,
                previewPattern,
                targeting,
                damage,
                overlay,
                meteorStorm,
                phases
        );
    }

    private static BerettaDamageProfile parseDamage(JsonObject root) {
        if (root == null) {
            return BerettaDamageProfile.none();
        }

        BerettaDamageProfile.Mode mode = enumValue(
                BerettaDamageProfile.Mode.class,
                root,
                "mode",
                BerettaDamageProfile.Mode.NONE
        );
        return new BerettaDamageProfile(
                mode,
                doubleValue(root, "amount", 0.0D),
                doubleValue(root, "cap", 0.0D),
                booleanValue(root, "respects_resistances", true)
        );
    }

    private static BerettaMeteorStormProfile parseMeteorStorm(JsonObject root, int durationTicks) {
        if (root == null || !booleanValue(root, "enabled", false)) {
            return BerettaMeteorStormProfile.none();
        }

        int phaseCount = intValue(root, "phase_count", 3);
        int phaseDuration = intValue(
                root,
                "phase_duration_ticks",
                Math.max(1, durationTicks / Math.max(1, phaseCount))
        );
        return new BerettaMeteorStormProfile(
                true,
                doubleValue(root, "width", 91.0D),
                doubleValue(root, "length", 96.0D),
                doubleValue(root, "drop_height", 80.0D),
                phaseCount,
                phaseDuration,
                intValue(root, "meteor_interval_ticks", 4),
                intValue(root, "meteors_per_wave", 3),
                intValue(root, "fall_ticks", 24),
                doubleValue(root, "preview_radius", 2.0D),
                doubleValue(root, "impact_radius", 2.5D),
                doubleValue(root, "phase_shift", 18.0D)
        );
    }

    private static String requiredString(JsonObject root, String key) {
        if (!root.has(key)) {
            throw new JsonParseException("Missing required field: " + key);
        }
        return root.get(key).getAsString();
    }

    private static int requiredInt(JsonObject root, String key) {
        if (!root.has(key)) {
            throw new JsonParseException("Missing required field: " + key);
        }
        return root.get(key).getAsInt();
    }

    private static int intValue(JsonObject root, String key, int fallback) {
        return root.has(key) ? root.get(key).getAsInt() : fallback;
    }

    private static double doubleValue(JsonObject root, String key, double fallback) {
        return root.has(key) ? root.get(key).getAsDouble() : fallback;
    }

    private static boolean booleanValue(JsonObject root, String key, boolean fallback) {
        return root.has(key) ? root.get(key).getAsBoolean() : fallback;
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type,
            JsonObject root,
            String key,
            T fallback
    ) {
        if (!root.has(key)) {
            return fallback;
        }

        String value = root.get(key).getAsString().trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("Unknown " + key + " value: " + value, exception);
        }
    }
}
