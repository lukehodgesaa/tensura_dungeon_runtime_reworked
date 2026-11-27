package com.mineclawd.tensuradungeon.runtime.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class DungeonThemeSelectionPolicy {
    private static final ResourceLocation CONFIG_ID = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID, "dungeon/theme_selection.json"
    );
    private static ResourceManager cachedManager;
    private static Snapshot cachedSnapshot;

    private DungeonThemeSelectionPolicy() {
    }

    public static synchronized Snapshot get(ServerLevel level) {
        ResourceManager manager = level.getServer().getResourceManager();
        if (cachedSnapshot == null || cachedManager != manager) {
            cachedManager = manager;
            cachedSnapshot = load(manager);
        }
        return cachedSnapshot;
    }

    public static synchronized Snapshot reload(ServerLevel level) {
        cachedManager = level.getServer().getResourceManager();
        cachedSnapshot = load(cachedManager);
        return cachedSnapshot;
    }

    private static Snapshot load(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(CONFIG_ID);
        if (resource.isEmpty()) {
            return new Snapshot(defaultConfig(), List.of("Missing theme selection resource: " + CONFIG_ID));
        }
        List<String> errors = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Mode mode = Mode.parse(string(root, "mode", "MILESTONE"));
            if (mode == null) {
                errors.add("Unknown theme selection mode: " + string(root, "mode", ""));
                mode = Mode.MILESTONE;
            }
            JsonArray entries = root.getAsJsonArray("themes");
            List<ThemeUnlock> unlocks = new ArrayList<>();
            if (entries == null) {
                errors.add("Theme selection resource has no themes array");
            } else {
                for (JsonElement element : entries) {
                    if (!element.isJsonObject()) {
                        errors.add("Theme selection entry is not an object");
                        continue;
                    }
                    JsonObject entry = element.getAsJsonObject();
                    String id = string(entry, "id", "");
                    if (id.isBlank()) {
                        errors.add("Theme selection entry missing id");
                        continue;
                    }
                    unlocks.add(new ThemeUnlock(id, integer(entry, "unlockFloor", 1)));
                }
            }
            Config config = Config.of(mode, unlocks);
            return new Snapshot(config, List.copyOf(errors));
        } catch (Exception exception) {
            errors.add("Theme selection parse failed: " + exception.getClass().getSimpleName()
                + ": " + exception.getMessage());
            return new Snapshot(defaultConfig(), List.copyOf(errors));
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive()
            ? object.get(key).getAsString().trim() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive()
            ? object.get(key).getAsInt() : fallback;
    }

    public static Config defaultConfig() {
        return Config.of(
            Mode.MILESTONE,
            List.of(
                new ThemeUnlock("original", 1),
                new ThemeUnlock("gothic", 1),
                new ThemeUnlock("dwarven", 50),
                new ThemeUnlock("sand", 100),
                new ThemeUnlock("overgrown", 150),
                new ThemeUnlock("frozen", 200),
                new ThemeUnlock("abyssal_prismarine", 250),
                new ThemeUnlock("dark_volcanic", 300)
            )
        );
    }

    public enum Mode {
        MILESTONE,
        UNGATED_RANDOM;

        static Mode parse(String value) {
            if (value == null) return null;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    public record ThemeUnlock(String id, int unlockFloor) {
        public ThemeUnlock {
            id = normalize(id);
            if (id.isBlank()) throw new IllegalArgumentException("Theme unlock id must be present");
            unlockFloor = Math.max(1, unlockFloor);
        }
    }

    public record Config(Mode mode, List<ThemeUnlock> unlocks) {
        public Config {
            mode = mode == null ? Mode.MILESTONE : mode;
            List<ThemeUnlock> normalized = unlocks == null ? List.of() : unlocks;
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            List<ThemeUnlock> ordered = new ArrayList<>();
            for (ThemeUnlock unlock : normalized) {
                if (unlock == null || !ids.add(unlock.id())) {
                    throw new IllegalArgumentException("Theme unlock ids must be non-null and unique");
                }
                ordered.add(unlock);
            }
            if (ordered.isEmpty()) throw new IllegalArgumentException("At least one theme unlock is required");
            unlocks = List.copyOf(ordered);
        }

        public static Config of(Mode mode, List<ThemeUnlock> unlocks) {
            return new Config(mode, unlocks);
        }

        public List<String> eligibleThemes(Set<String> availableThemes, int highestUnlockedFloor) {
            Set<String> available = normalizeSet(availableThemes);
            int highest = Math.max(1, highestUnlockedFloor);
            return unlocks.stream()
                .filter(unlock -> mode == Mode.UNGATED_RANDOM || unlock.unlockFloor() <= highest)
                .map(ThemeUnlock::id)
                .filter(available::contains)
                .toList();
        }

        public Selection select(Set<String> availableThemes, int highestUnlockedFloor, long seed, int floor) {
            List<String> eligible = eligibleThemes(availableThemes, highestUnlockedFloor);
            if (eligible.isEmpty()) return new Selection("", eligible, "THEME_POOL_EMPTY");
            long mixed = mix64(seed ^ ((long) Math.max(1, floor) * 0xD1B54A32D192ED03L));
            int selected = (int) Math.floorMod(mixed, eligible.size());
            return new Selection(eligible.get(selected), eligible, "NONE");
        }
    }

    public record Selection(String themeId, List<String> eligibleThemes, String reason) {
        public Selection {
            themeId = normalize(themeId);
            eligibleThemes = eligibleThemes == null ? List.of() : List.copyOf(eligibleThemes);
            reason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
        }

        public boolean succeeded() {
            return !themeId.isBlank() && "NONE".equals(reason);
        }
    }

    public record Snapshot(Config config, List<String> errors) {
        public Snapshot {
            config = config == null ? defaultConfig() : config;
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public boolean valid() {
            return errors.isEmpty();
        }

        public Selection select(Set<String> availableThemes, int highestUnlockedFloor, long seed, int floor) {
            if (!valid()) return new Selection("", List.of(), "THEME_CONFIG_INVALID");
            return config.select(availableThemes, highestUnlockedFloor, seed, floor);
        }
    }

    private static Set<String> normalizeSet(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return values.stream().map(DungeonThemeSelectionPolicy::normalize)
            .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }
}
