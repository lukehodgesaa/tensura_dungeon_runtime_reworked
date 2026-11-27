package com.mineclawd.tensuradungeon.runtime.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeRoomRuntimeRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeTemplateDescriptor;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class DungeonTemplateMetadataRegistry {
    private static final int DEFAULT_MOTIF_SELECTION_WEIGHT = 10;
    private static final int MAX_MOTIF_SELECTION_WEIGHT = 100;
    private static final int DEFAULT_COMBAT_ENTRY_PERCENT = 25;
    private static final ResourceLocation METADATA_ID = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID, "dungeon/template_metadata.json"
    );
    private static ResourceManager cachedManager;
    private static Snapshot cachedSnapshot;

    private DungeonTemplateMetadataRegistry() {
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
        CompositeRoomRuntimeRegistry.invalidate();
        cachedManager = level.getServer().getResourceManager();
        cachedSnapshot = load(cachedManager);
        return cachedSnapshot;
    }

    private static Snapshot load(ResourceManager manager) {
        List<String> errors = new ArrayList<>();
        Map<String, DungeonTemplateMetadata> templates = new LinkedHashMap<>();
        Map<String, Integer> motifSelectionWeights = new LinkedHashMap<>();
        int combatEntryPercent = DEFAULT_COMBAT_ENTRY_PERCENT;
        Optional<Resource> resource = manager.getResource(METADATA_ID);
        if (resource.isEmpty()) {
            return new Snapshot(Map.of(), List.of("Missing metadata resource: " + METADATA_ID));
        }
        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("combatEntryPercent")) {
                JsonElement authoredCombatEntryPercent = root.get("combatEntryPercent");
                if (!authoredCombatEntryPercent.isJsonPrimitive()
                    || !authoredCombatEntryPercent.getAsJsonPrimitive().isNumber()) {
                    errors.add("combatEntryPercent must be an integer between 0 and 100");
                } else {
                    int parsed = authoredCombatEntryPercent.getAsInt();
                    if (parsed < 0 || parsed > 100) {
                        errors.add("combatEntryPercent must be between 0 and 100");
                    } else {
                        combatEntryPercent = parsed;
                    }
                }
            }
            JsonObject authoredMotifWeights = root.getAsJsonObject("motifSelectionWeights");
            if (authoredMotifWeights != null) {
                for (Map.Entry<String, JsonElement> entry : authoredMotifWeights.entrySet()) {
                    String motif = entry.getKey().trim().toLowerCase(java.util.Locale.ROOT);
                    if (motif.isEmpty() || !entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                        errors.add("Motif selection weight must be a named integer: " + entry.getKey());
                        continue;
                    }
                    int weight = entry.getValue().getAsInt();
                    if (weight < 1 || weight > MAX_MOTIF_SELECTION_WEIGHT) {
                        errors.add("Motif selection weight for " + motif + " must be between 1 and "
                            + MAX_MOTIF_SELECTION_WEIGHT);
                        continue;
                    }
                    motifSelectionWeights.put(motif, weight);
                }
            }
            JsonArray entries = root.getAsJsonArray("templates");
            if (entries == null) {
                return new Snapshot(Map.of(), List.of("Metadata resource has no templates array"));
            }
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                String id = string(entry, "id", "").trim();
                if (id.isEmpty()) {
                    errors.add("Template entry missing id");
                    continue;
                }
                if (templates.containsKey(id)) {
                    errors.add("Duplicate template id: " + id);
                    continue;
                }
                Set<DungeonRoomRole> roles = new LinkedHashSet<>();
                for (String roleName : strings(entry, "roles")) {
                    DungeonRoomRole role = DungeonRoomRole.parse(roleName);
                    if (role == null) errors.add(id + " has unknown role " + roleName);
                    else roles.add(role);
                }
                if (roles.isEmpty()) errors.add(id + " has no valid gameplay roles");
                boolean enabled = bool(entry, "enabled", true);
                String exclusionReason = string(entry, "exclusionReason", "NONE");
                if (!enabled && "NONE".equals(exclusionReason)) {
                    errors.add(id + " is disabled without an exclusionReason");
                }
                List<String> themes = strings(entry, "themes");
                String motif = string(entry, "motif", themes.isEmpty() ? "unclassified" : themes.getFirst());
                templates.put(id, new DungeonTemplateMetadata(
                    id,
                    enabled,
                    roles,
                    Set.copyOf(themes),
                    motif,
                    string(entry, "sizeClass", "MEDIUM").toUpperCase(),
                    integer(entry, "weight", 10),
                    integer(entry, "repeatLimit", roles.contains(DungeonRoomRole.CORRIDOR) ? 1 : 2),
                    bool(entry, "startAllowed", true),
                    bool(entry, "terminalAllowed", false),
                    bool(entry, "alwaysSafe", false),
                    bool(entry, "setPieceEligible", false),
                    integer(entry, "setPieceCost", 1),
                    exclusionReason,
                    string(entry, "visualTheme", "legacy")
                ));
            }
        } catch (Exception exception) {
            errors.add("Metadata parse failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        for (DungeonTemplateMetadata composite : CompositeTemplateDescriptor.discoverMetadata(manager)) {
            DungeonTemplateMetadata collision = templates.putIfAbsent(composite.id(), composite);
            if (collision != null) {
                errors.add("Composite template id collides with authored metadata: " + composite.id());
            }
        }
        return new Snapshot(
            Map.copyOf(templates), List.copyOf(errors), Map.copyOf(motifSelectionWeights),
            combatEntryPercent
        );
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
    }

    private static List<String> strings(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray(key)) {
            if (element.isJsonPrimitive()) values.add(element.getAsString());
        }
        return values;
    }

    public record Snapshot(
        Map<String, DungeonTemplateMetadata> templates,
        List<String> errors,
        Map<String, Integer> motifSelectionWeights,
        int combatEntryPercent
    ) {
        public Snapshot {
            templates = Map.copyOf(templates);
            errors = List.copyOf(errors);
            motifSelectionWeights = Map.copyOf(motifSelectionWeights);
            if (combatEntryPercent < 0 || combatEntryPercent > 100) {
                throw new IllegalArgumentException("combatEntryPercent must be between 0 and 100");
            }
        }

        public Snapshot(Map<String, DungeonTemplateMetadata> templates, List<String> errors) {
            this(templates, errors, Map.of(), DEFAULT_COMBAT_ENTRY_PERCENT);
        }

        public Snapshot(
            Map<String, DungeonTemplateMetadata> templates,
            List<String> errors,
            Map<String, Integer> motifSelectionWeights
        ) {
            this(templates, errors, motifSelectionWeights, DEFAULT_COMBAT_ENTRY_PERCENT);
        }

        public int motifSelectionWeight(String motif) {
            return motifSelectionWeights.getOrDefault(motif, DEFAULT_MOTIF_SELECTION_WEIGHT);
        }

        public List<DungeonTemplateMetadata> enabledTemplates() {
            return templates.values().stream().filter(DungeonTemplateMetadata::enabled).toList();
        }

        public List<DungeonTemplateMetadata> excludedTemplates() {
            return templates.values().stream().filter(template -> !template.enabled()).toList();
        }

        public Set<String> visualThemes() {
            return templates.values().stream()
                .filter(DungeonTemplateMetadata::enabled)
                .map(DungeonTemplateMetadata::visualTheme)
                .filter(theme -> !theme.isBlank() && !"legacy".equals(theme))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        public boolean valid() {
            return !templates.isEmpty() && errors.isEmpty();
        }
    }
}
