package com.mineclawd.tensuradungeon.runtime.enemy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DungeonBossCatalog {
    public static final int CATALOG_VERSION = 1;

    private static final List<Entry> ENTRIES = List.of(
        entry("tensura:supermassive_slime", DungeonEnemyUsage.ELITE_OR_BOSS,
            List.of("slime"), List.of("slime")),
        entry("tensura:folgen", DungeonEnemyUsage.ELITE_OR_BOSS,
            List.of("humanoid"), List.of("humanoid")),
        entry("tensura:orc_lord", DungeonEnemyUsage.ELITE_OR_BOSS,
            List.of("orc"), List.of("orc")),
        entry("tensura:akash", DungeonEnemyUsage.ELITE_OR_BOSS,
            List.of("spiritual"), List.of("spiritual")),
        entry("tensura:arch_daemon", DungeonEnemyUsage.ELITE_OR_BOSS,
            List.of("daemon"), List.of("daemon")),

        entry("tensura:elemental_colossus", DungeonEnemyUsage.BOSS_ONLY,
            List.of("elemental", "spiritual"), List.of("elemental", "spiritual")),
        entry("tensura:ifrit", DungeonEnemyUsage.BOSS_ONLY,
            List.of("elemental", "spiritual"), List.of("elemental", "spiritual")),
        entry("tensura:orc_disaster", DungeonEnemyUsage.BOSS_ONLY,
            List.of("orc"), List.of("orc")),

        entry("tensura_neb:carrion", DungeonEnemyUsage.LATE_BOSS_ONLY,
            List.of("undead"), List.of("undead")),
        entry("tensura:charybdis", DungeonEnemyUsage.LATE_BOSS_ONLY,
            List.of("aquatic"), List.of("aquatic")),
        entry("tensura:gazel_dwargo", DungeonEnemyUsage.LATE_BOSS_ONLY,
            List.of("humanoid"), List.of("humanoid")),
        entry("tensura:hinata_sakaguchi", DungeonEnemyUsage.LATE_BOSS_ONLY,
            List.of("humanoid"), List.of("humanoid")),
        entry("tensura_neb:luminous_valentine", DungeonEnemyUsage.LATE_BOSS_ONLY,
            List.of("spiritual"), List.of("spiritual")),
        entry("tensura_neb:rimuru_ogre_fight", DungeonEnemyUsage.LATE_BOSS_ONLY,
            List.of("slime"), List.of("slime")),
        entry("tensura_neb:veldora", DungeonEnemyUsage.LATE_BOSS_ONLY,
            List.of("dragon"), List.of("dragon"))
    );

    private static final Map<String, Entry> BY_ENTITY_ID = ENTRIES.stream()
        .collect(Collectors.toUnmodifiableMap(Entry::entityTypeId, Function.identity()));

    private DungeonBossCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> forEntity(String entityTypeId) {
        if (entityTypeId == null) return Optional.empty();
        return Optional.ofNullable(BY_ENTITY_ID.get(normalize(entityTypeId)));
    }

    public static boolean isBossCapableUsage(DungeonEnemyUsage usage) {
        return usage == DungeonEnemyUsage.ELITE_OR_BOSS
            || usage == DungeonEnemyUsage.BOSS_ONLY
            || usage == DungeonEnemyUsage.LATE_BOSS_ONLY;
    }

    private static Entry entry(
        String entityTypeId,
        DungeonEnemyUsage usage,
        List<String> kinGroups,
        List<String> themes
    ) {
        return new Entry(entityTypeId, usage, Set.copyOf(kinGroups), Set.copyOf(themes));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Entry(
        String entityTypeId,
        DungeonEnemyUsage usage,
        Set<String> affinityKinGroups,
        Set<String> themeTags
    ) {
        public Entry {
            entityTypeId = normalize(entityTypeId);
            if (entityTypeId.isBlank() || usage == null || !isBossCapableUsage(usage)) {
                throw new IllegalArgumentException("Incomplete authored boss entry");
            }
            affinityKinGroups = normalizeSet(affinityKinGroups);
            themeTags = normalizeSet(themeTags);
            if (affinityKinGroups.isEmpty() && themeTags.isEmpty()) {
                throw new IllegalArgumentException("Authored boss requires an affinity key");
            }
        }

        public boolean matches(String kinGroup, String theme) {
            String normalizedKinGroup = normalize(kinGroup);
            String normalizedTheme = normalize(theme);
            return (!normalizedKinGroup.isBlank() && affinityKinGroups.contains(normalizedKinGroup))
                || (!normalizedTheme.isBlank() && themeTags.contains(normalizedTheme));
        }

        private static Set<String> normalizeSet(Iterable<String> values) {
            if (values == null) return Set.of();
            return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .map(DungeonBossCatalog::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        }
    }
}
