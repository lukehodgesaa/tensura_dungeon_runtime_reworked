package com.mineclawd.tensuradungeon.runtime.enemy;

import com.mineclawd.tensuradungeon.runtime.generation.DungeonGameplayComposer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.util.RandomSource;

public final class DungeonBossResolver {
    private DungeonBossResolver() {
    }

    public static DungeonBossResolution resolveNormal(int floorNumber, long seed) {
        return resolveInternal(floorNumber, seed, "", "", false);
    }

    public static DungeonBossResolution resolve(
        int floorNumber,
        long seed,
        String affinityKinGroup,
        String authoredTheme
    ) {
        return resolveInternal(floorNumber, seed, affinityKinGroup, authoredTheme, true);
    }

    private static DungeonBossResolution resolveInternal(
        int floorNumber,
        long seed,
        String affinityKinGroup,
        String authoredTheme,
        boolean affinityRequested
    ) {
        int floor = Math.max(1, floorNumber);
        DungeonGameplayComposer.TerminalEncounterPlan terminal =
            DungeonGameplayComposer.TerminalEncounterPlan.forFloor(floor, "");
        if (terminal.role() != DungeonGameplayComposer.TerminalEncounterRole.BOSS) {
            return DungeonBossResolution.noBossSlot(floor, seed);
        }

        String kinGroup = normalize(affinityKinGroup);
        String theme = normalize(authoredTheme);
        List<DungeonEnemyDefinition> defaultDefinitions = DungeonEnemyPoolRegistry
            .eligibleBossDefinitions(floor, terminal.milestoneBoss());
        List<DungeonEnemyDefinition> authoredDefault = defaultDefinitions.stream()
            .filter(DungeonBossResolver::matchesAuthoredUsage)
            .toList();
        List<DungeonEnemyDefinition> authoredKin = affinityRequested && !kinGroup.isBlank()
            ? DungeonEnemyPoolRegistry.eligibleKinBossDefinitions(
                    floor, terminal.milestoneBoss()
                ).stream()
                .filter(DungeonBossResolver::matchesAuthoredUsage)
                .toList()
            : List.of();
        List<String> defaultIds = authoredDefault.stream()
            .map(DungeonEnemyDefinition::entityTypeId)
            .distinct()
            .sorted()
            .toList();
        List<String> kinIds = authoredKin.stream()
            .map(DungeonEnemyDefinition::entityTypeId)
            .distinct()
            .sorted()
            .toList();
        List<String> eligibleIds = Stream.concat(defaultIds.stream(), kinIds.stream())
            .distinct()
            .sorted()
            .toList();
        if (eligibleIds.isEmpty()) {
            return new DungeonBossResolution(
                floor, seed, DungeonBossCatalog.CATALOG_VERSION, true, terminal.milestoneBoss(),
                kinGroup, theme, List.of(), List.of(), "", "NO_AUTHORED_BOSS_CANDIDATES"
            );
        }

        List<DungeonEnemyDefinition> matchingDefinitions =
            affinityRequested && !kinGroup.isBlank() ? authoredKin : authoredDefault;
        List<String> matchingIds = matchingDefinitions.stream()
            .filter(definition -> DungeonBossCatalog.forEntity(definition.entityTypeId())
                .map(entry -> entry.matches(kinGroup, theme)).orElse(false))
            .map(DungeonEnemyDefinition::entityTypeId)
            .distinct()
            .sorted()
            .toList();
        boolean requested = affinityRequested && (!kinGroup.isBlank() || !theme.isBlank());
        if (requested && matchingIds.isEmpty()) {
            List<String> fallbackPool = defaultIds.isEmpty() ? eligibleIds : defaultIds;
            return resolution(
                floor, seed, terminal.milestoneBoss(), kinGroup, theme,
                eligibleIds, matchingIds, pick(fallbackPool, seed),
                "AFFINITY_NO_MATCH_NEUTRAL_FALLBACK"
            );
        }
        if (requested && !matchingIds.isEmpty()) {
            return resolution(
                floor, seed, terminal.milestoneBoss(), kinGroup, theme,
                eligibleIds, matchingIds, pick(matchingIds, seed),
                "AFFINITY_MATCH_SELECTED"
            );
        }
        return resolution(
            floor, seed, terminal.milestoneBoss(), "", "", defaultIds, List.of(),
            pick(defaultIds, seed), "NEUTRAL_BOSS_SELECTED"
        );
    }

    private static DungeonBossResolution resolution(
        int floor,
        long seed,
        boolean milestoneBoss,
        String kinGroup,
        String theme,
        List<String> eligibleIds,
        List<String> matchingIds,
        String selectedBossId,
        String reason
    ) {
        return new DungeonBossResolution(
            floor,
            seed,
            DungeonBossCatalog.CATALOG_VERSION,
            true,
            milestoneBoss,
            kinGroup,
            theme,
            eligibleIds,
            matchingIds,
            selectedBossId,
            reason
        );
    }

    private static boolean matchesAuthoredUsage(DungeonEnemyDefinition definition) {
        return DungeonBossCatalog.forEntity(definition.entityTypeId())
            .map(entry -> entry.usage() == definition.usage())
            .orElse(false);
    }

    private static String pick(List<String> candidates, long seed) {
        if (candidates.isEmpty()) return "";
        return candidates.get(RandomSource.create(seed).nextInt(candidates.size()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
