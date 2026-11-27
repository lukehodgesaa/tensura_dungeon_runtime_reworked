package com.mineclawd.tensuradungeon.runtime.generation.composite;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonRoomRole;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadata;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public record CompositeTemplateDescriptor(String id, String theme, int variant) {
    public static final String ID_PREFIX = "composite_v1__";
    public static final String INTERSECTION_THREE_ID_PREFIX = "composite_intersection3_v1__";
    public static final String INTERSECTION_FOUR_ID_PREFIX = "composite_intersection4_v1__";
    public static final int CANDIDATES_PER_THEME = 12;
    public static final int INTERSECTION_CANDIDATES_PER_THEME = 4;

    public CompositeTemplateDescriptor {
        id = id == null ? "" : id;
        theme = theme == null ? "" : theme.toLowerCase(Locale.ROOT);
        int candidateCount = candidateCount(id);
        if (id.isBlank() || theme.isBlank() || candidateCount == 0
            || variant < 0 || variant >= candidateCount) {
            throw new IllegalArgumentException("Invalid composite template descriptor");
        }
    }

    public static boolean isCompositeId(String id) {
        return prefix(id) != null;
    }

    public static CompositeTemplateDescriptor parse(String id) {
        String prefix = prefix(id);
        if (prefix == null) return null;
        int separator = id.lastIndexOf("__");
        if (separator < prefix.length() || separator + 2 >= id.length()) return null;
        String theme = id.substring(prefix.length(), separator);
        try {
            int variant = Integer.parseInt(id.substring(separator + 2));
            return new CompositeTemplateDescriptor(id, theme, variant);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static List<DungeonTemplateMetadata> discoverMetadata(ResourceManager manager) {
        Map<String, Set<CompositeSegmentDefinition.Kind>> themes = new LinkedHashMap<>();
        manager.listResources(
            "structure/segments",
            resource -> resource.getPath().endsWith(".nbt")
        ).keySet().stream()
            .filter(resource -> TensuraDungeonRuntime.MOD_ID.equals(resource.getNamespace()))
            .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
            .forEach(resource -> addKind(resource, themes));
        List<DungeonTemplateMetadata> result = new ArrayList<>();
        for (Map.Entry<String, Set<CompositeSegmentDefinition.Kind>> entry : themes.entrySet()) {
            Set<CompositeSegmentDefinition.Kind> kinds = entry.getValue();
            if (isCompleteTheme(kinds)) {
                addMetadataCandidates(
                    result, ID_PREFIX, entry.getKey(), CANDIDATES_PER_THEME,
                    Set.of(DungeonRoomRole.COMBAT), "COMPOSITE"
                );
            }
            if (isCompleteIntersectionTheme(kinds, CompositeSegmentDefinition.Kind.INTERSECTION_THREE)) {
                addMetadataCandidates(
                    result, INTERSECTION_THREE_ID_PREFIX, entry.getKey(),
                    INTERSECTION_CANDIDATES_PER_THEME,
                    Set.of(DungeonRoomRole.INTERSECTION), "COMPOSITE_INTERSECTION"
                );
            }
            if (isCompleteIntersectionTheme(kinds, CompositeSegmentDefinition.Kind.INTERSECTION_FOUR)) {
                addMetadataCandidates(
                    result, INTERSECTION_FOUR_ID_PREFIX, entry.getKey(),
                    INTERSECTION_CANDIDATES_PER_THEME,
                    Set.of(DungeonRoomRole.INTERSECTION), "COMPOSITE_INTERSECTION"
                );
            }
        }
        return List.copyOf(result);
    }

    private static void addMetadataCandidates(
        List<DungeonTemplateMetadata> result,
        String prefix,
        String theme,
        int count,
        Set<DungeonRoomRole> roles,
        String family
    ) {
        for (int variant = 0; variant < count; variant++) {
            String id = prefix + theme + "__" + String.format(Locale.ROOT, "%02d", variant);
            result.add(new DungeonTemplateMetadata(
                id,
                true,
                roles,
                Set.of("composite", "segments", theme),
                theme,
                family,
                8,
                1,
                false,
                false,
                false,
                false,
                0,
                "NONE",
                theme
            ));
        }
    }

    static boolean isCompleteTheme(Set<CompositeSegmentDefinition.Kind> kinds) {
        return kinds != null
            && kinds.contains(CompositeSegmentDefinition.Kind.ENTRANCE)
            && kinds.contains(CompositeSegmentDefinition.Kind.COMBAT);
    }

    static boolean isCompleteIntersectionTheme(
        Set<CompositeSegmentDefinition.Kind> kinds,
        CompositeSegmentDefinition.Kind intersectionKind
    ) {
        return kinds != null
            && kinds.contains(CompositeSegmentDefinition.Kind.ENTRANCE)
            && intersectionKind != null
            && intersectionKind.isIntersection()
            && kinds.contains(intersectionKind);
    }

    private static void addKind(
        ResourceLocation resource,
        Map<String, Set<CompositeSegmentDefinition.Kind>> themes
    ) {
        String prefix = "structure/segments/";
        String path = resource.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".nbt")) return;
        String[] parts = path.substring(prefix.length(), path.length() - 4).split("/");
        if (parts.length < 3) return;
        CompositeSegmentDefinition.Kind kind = CompositeSegmentDefinition.Kind.parse(parts[1]);
        if (kind == null) return;
        themes.computeIfAbsent(parts[0].toLowerCase(Locale.ROOT), ignored -> new LinkedHashSet<>()).add(kind);
    }

    public long compilationSeed() {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < id.length(); index++) {
            hash ^= id.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    public int minimumSegments() {
        if (isIntersection()) return 1 + externalConnectorCount();
        return 3 + variant % 4;
    }

    public int maximumSegments() {
        if (isIntersection()) return minimumSegments();
        return Math.min(9, minimumSegments() + 2);
    }

    public boolean isIntersection() {
        return externalConnectorCount() > 2;
    }

    public int externalConnectorCount() {
        if (id.startsWith(INTERSECTION_THREE_ID_PREFIX)) return 3;
        if (id.startsWith(INTERSECTION_FOUR_ID_PREFIX)) return 4;
        return 2;
    }

    public CompositeRoomRequirements roomRequirements() {
        return isIntersection()
            ? CompositeRoomRequirements.intersectionPrototype(externalConnectorCount())
            : CompositeRoomRequirements.smallCombatPrototype();
    }

    private static int candidateCount(String id) {
        String prefix = prefix(id);
        if (prefix == null) return 0;
        return prefix.equals(ID_PREFIX) ? CANDIDATES_PER_THEME : INTERSECTION_CANDIDATES_PER_THEME;
    }

    private static String prefix(String id) {
        if (id == null) return null;
        if (id.startsWith(INTERSECTION_THREE_ID_PREFIX)) return INTERSECTION_THREE_ID_PREFIX;
        if (id.startsWith(INTERSECTION_FOUR_ID_PREFIX)) return INTERSECTION_FOUR_ID_PREFIX;
        if (id.startsWith(ID_PREFIX)) return ID_PREFIX;
        return null;
    }
}
