package com.mineclawd.tensuradungeon.runtime.enemy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DungeonEnemyKinCatalog {
    public static final int CATALOG_VERSION = 1;
    public static final String KIN_MODIFIER_PREFIX = "kin-";

    private static final List<Entry> ENTRIES = List.of(
        entry("tensura:barghest", "canid", "REVIEWED_VISUAL_CANID", "MEDIUM"),
        entry("tensura:dragon_peacock", "avian", "REVIEWED_VISUAL_AVIAN", "MEDIUM"),
        entry("tensura:hound_dog", "canid", "REVIEWED_VISUAL_CANID", "MEDIUM"),
        entry("tensura:slime", "slime", "REVIEWED_CREATURE_CLASS_SLIME", "HIGH"),

        entry("tensura:army_wasp", "arthropod", "REVIEWED_CREATURE_CLASS_ARTHROPOD", "HIGH"),
        entry("tensura:direwolf", "canid", "REVIEWED_VISUAL_CANID", "MEDIUM"),
        entry("tensura:evil_centipede", "arthropod", "REVIEWED_CREATURE_CLASS_ARTHROPOD", "HIGH"),
        entry("tensura:giant_ant", "arthropod", "REVIEWED_CREATURE_CLASS_ARTHROPOD", "HIGH"),
        entry("tensura:giant_bat", "bat", "REVIEWED_VISUAL_BAT", "MEDIUM"),
        entry("tensura:horned_bear", "bear", "REVIEWED_VISUAL_BEAR", "MEDIUM"),
        entry("tensura:leech_lizard", "reptilian", "REVIEWED_CREATURE_CLASS_REPTILIAN", "MEDIUM"),

        entry("tensura:black_spider", "arthropod", "REVIEWED_CREATURE_CLASS_ARTHROPOD", "HIGH"),
        entry("tensura:giant_bear", "bear", "REVIEWED_VISUAL_BEAR", "MEDIUM"),
        entry("tensura:hover_lizard", "reptilian", "REVIEWED_CREATURE_CLASS_REPTILIAN", "MEDIUM"),
        entry("tensura:knight_spider", "arthropod", "REVIEWED_CREATURE_CLASS_ARTHROPOD", "HIGH"),
        entry("tensura:phantaspore", "fungal", "REVIEWED_VISUAL_FUNGAL", "LOW"),
        entry("tensura:salamander", "elemental", "REVIEWED_CREATURE_CLASS_ELEMENTAL", "MEDIUM"),

        entry("tensura:armorsaurus", "reptilian", "REVIEWED_CREATURE_CLASS_REPTILIAN", "MEDIUM"),
        entry("tensura:basilisk", "reptilian", "REVIEWED_CREATURE_CLASS_REPTILIAN", "MEDIUM"),
        entry("tensura:falmuth_knight", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),
        entry("tensura:feathered_serpent", "reptilian", "REVIEWED_CREATURE_CLASS_REPTILIAN", "MEDIUM"),
        entry("tensura:hell_caterpillar", "arthropod", "REVIEWED_CREATURE_CLASS_ARTHROPOD", "HIGH"),
        entry("tensura:hell_moth", "arthropod", "REVIEWED_CREATURE_CLASS_ARTHROPOD", "HIGH"),
        entry("tensura:metal_slime", "slime", "REVIEWED_CREATURE_CLASS_SLIME", "HIGH"),
        entry("tensura:supermassive_slime", "slime", "REVIEWED_CREATURE_CLASS_SLIME", "HIGH"),
        entry("tensura:sylphide", "elemental", "REVIEWED_CREATURE_CLASS_ELEMENTAL", "MEDIUM"),
        entry("tensura:tempest_serpent", "reptilian", "REVIEWED_CREATURE_CLASS_REPTILIAN", "MEDIUM"),
        entry("tensura:undine", "elemental", "REVIEWED_CREATURE_CLASS_ELEMENTAL", "MEDIUM"),
        entry("tensura:unicorn", "equine", "REVIEWED_VISUAL_EQUINE", "MEDIUM"),
        entry("tensura:war_gnome", "elemental", "REVIEWED_CREATURE_CLASS_ELEMENTAL", "MEDIUM"),
        entry("tensura:winged_cat", "feline", "REVIEWED_VISUAL_FELINE", "MEDIUM"),

        entry("tensura:blade_tiger", "feline", "REVIEWED_VISUAL_FELINE", "MEDIUM"),
        entry("tensura:folgen", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),
        entry("tensura:kirara_mizutani", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),
        entry("tensura:kyoya_tachibana", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),
        entry("tensura:lesser_daemon", "daemon", "REVIEWED_CREATURE_CLASS_DAEMON", "HIGH"),
        entry("tensura:shogo_taguchi", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),

        entry("tensura:aqua_frog", "amphibian", "REVIEWED_CREATURE_CLASS_AMPHIBIAN", "MEDIUM"),
        entry("tensura:beast_gnome", "gnome", "REVIEWED_CREATURE_CLASS_GNOME", "MEDIUM"),
        entry("tensura:elemental_colossus", "elemental", "REVIEWED_CREATURE_CLASS_ELEMENTAL", "MEDIUM"),
        entry("tensura:mai_furuki", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),
        entry("tensura:mark_lauren", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),
        entry("tensura:greater_daemon", "daemon", "REVIEWED_CREATURE_CLASS_DAEMON", "HIGH"),
        entry("tensura:megalodon", "reptilian", "REVIEWED_CREATURE_CLASS_REPTILIAN", "MEDIUM"),
        entry("tensura:orc_lord", "orc", "REVIEWED_EXPLICIT_ORC", "MEDIUM"),
        entry("tensura:shin_ryusei", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),
        entry("tensura:shinji_tanimura", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),

        entry("tensura:akash", "spiritual", "REVIEWED_CREATURE_CLASS_SPIRITUAL", "HIGH"),
        entry("tensura:ifrit", "elemental", "REVIEWED_CREATURE_CLASS_ELEMENTAL", "MEDIUM"),
        entry("tensura:orc_disaster", "orc", "REVIEWED_EXPLICIT_ORC", "MEDIUM"),
        entry("tensura:shizu", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),
        entry("tensura:arch_daemon", "daemon", "REVIEWED_CREATURE_CLASS_DAEMON", "HIGH"),

        entry("tensura_neb:carrion", "undead", "REVIEWED_BOSS_VISUAL_UNDEAD", "LOW"),
        entry("tensura:charybdis", "aquatic", "REVIEWED_BOSS_VISUAL_AQUATIC", "MEDIUM"),
        entry("tensura:gazel_dwargo", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),
        entry("tensura:hinata_sakaguchi", "humanoid", "REVIEWED_CREATURE_CLASS_HUMANOID", "HIGH"),
        entry("tensura_neb:luminous_valentine", "spiritual", "REVIEWED_CREATURE_CLASS_SPIRITUAL", "HIGH"),
        entry("tensura_neb:rimuru_ogre_fight", "slime", "REVIEWED_CREATURE_CLASS_SLIME", "HIGH"),
        entry("tensura_neb:veldora", "dragon", "REVIEWED_BOSS_VISUAL_DRAGON", "MEDIUM")
    );

    private static final Map<String, Entry> BY_ENTITY_ID = ENTRIES.stream()
        .collect(Collectors.toUnmodifiableMap(Entry::entityTypeId, Function.identity()));

    private DungeonEnemyKinCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> forEntity(String entityTypeId) {
        if (entityTypeId == null) return Optional.empty();
        return Optional.ofNullable(BY_ENTITY_ID.get(entityTypeId.trim().toLowerCase(Locale.ROOT)));
    }

    public static String groupFor(String entityTypeId) {
        return forEntity(entityTypeId).map(Entry::kinGroup).orElse("");
    }

    public static boolean isKnownGroup(String kinGroup) {
        String normalized = normalize(kinGroup);
        return !normalized.isBlank()
            && entries().stream().anyMatch(entry -> entry.kinGroup().equals(normalized));
    }

    public static List<String> groups() {
        return new TreeSet<>(ENTRIES.stream().map(Entry::kinGroup).toList()).stream().toList();
    }

    public static List<Entry> entriesForGroup(String kinGroup) {
        String normalized = normalize(kinGroup);
        if (normalized.isBlank()) return List.of();
        return ENTRIES.stream().filter(entry -> entry.kinGroup().equals(normalized)).toList();
    }

    public static boolean isKinModifierId(String modifierId) {
        String normalized = normalize(modifierId);
        return normalized.startsWith(KIN_MODIFIER_PREFIX)
            && isKnownGroup(normalized.substring(KIN_MODIFIER_PREFIX.length()));
    }

    public static String groupForModifierId(String modifierId) {
        String normalized = normalize(modifierId);
        if (!normalized.startsWith(KIN_MODIFIER_PREFIX)) return "";
        String group = normalized.substring(KIN_MODIFIER_PREFIX.length());
        return isKnownGroup(group) ? group : "";
    }

    public static String modifierIdForGroup(String kinGroup) {
        String normalized = normalize(kinGroup);
        return isKnownGroup(normalized) ? KIN_MODIFIER_PREFIX + normalized : "";
    }

    public static String displayNameForGroup(String kinGroup) {
        String normalized = normalize(kinGroup);
        return isKnownGroup(normalized) ? titleCase(normalized) : "Unknown";
    }

    public static String displayNameForEntity(String entityTypeId) {
        String normalized = normalize(entityTypeId);
        int separator = normalized.indexOf(':');
        String path = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        return titleCase(path);
    }

    public static String describeGroup(String kinGroup) {
        List<String> names = entriesForGroup(kinGroup).stream()
            .map(Entry::entityTypeId)
            .distinct()
            .map(DungeonEnemyKinCatalog::displayNameForEntity)
            .toList();
        if (names.isEmpty()) return "No reviewed enemies are available for this family.";
        return bounded("Family: " + displayNameForGroup(kinGroup) + " - Enemies: "
            + String.join(", ", names), 256);
    }

    private static String titleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String part : value.split("[-_]+")) {
            if (part.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) result.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    private static Entry entry(String entityTypeId, String kinGroup, String evidence, String confidence) {
        return new Entry(entityTypeId, kinGroup, evidence, confidence);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    public record Entry(String entityTypeId, String kinGroup, String evidence, String confidence) {
        public Entry {
            entityTypeId = bounded(entityTypeId, 96).toLowerCase(Locale.ROOT);
            kinGroup = bounded(kinGroup, 48).toLowerCase(Locale.ROOT);
            evidence = bounded(evidence, 96);
            confidence = bounded(confidence, 16).toUpperCase(Locale.ROOT);
            if (entityTypeId.isBlank() || kinGroup.isBlank() || evidence.isBlank() || confidence.isBlank()) {
                throw new IllegalArgumentException("Incomplete Kin catalog entry");
            }
        }

        private static String bounded(String value, int maximum) {
            if (value == null) return "";
            return value.length() <= maximum ? value : value.substring(0, maximum);
        }
    }
}
