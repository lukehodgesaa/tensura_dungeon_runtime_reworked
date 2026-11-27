package com.mineclawd.tensuradungeon.runtime.enemy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.util.RandomSource;

public final class DungeonEnemyKinSelector {
    private static final Set<DungeonEnemyRole> SUPPORTED_ROLES = EnumSet.of(
        DungeonEnemyRole.MELEE,
        DungeonEnemyRole.RANGED,
        DungeonEnemyRole.FLANK,
        DungeonEnemyRole.HIGHGROUND,
        DungeonEnemyRole.ELITE_CANDIDATE
    );
    private static final List<String> INCOMPATIBLE_MODIFIERS = List.of(
        "swarm", "summoner", "riftbound"
    );
    public static final Set<DungeonEnemyRole> STANDARD_REQUIRED_ROLES =
        Collections.unmodifiableSet(EnumSet.of(
            DungeonEnemyRole.MELEE,
            DungeonEnemyRole.RANGED,
            DungeonEnemyRole.HIGHGROUND
        ));

    private DungeonEnemyKinSelector() {
    }

    public static boolean isFeasible(
        int floorNumber,
        long seed,
        Set<String> activeModifierIds
    ) {
        return select(floorNumber, seed, STANDARD_REQUIRED_ROLES, activeModifierIds).selected();
    }

    public static boolean isFeasibleForGroup(
        String kinGroup,
        int floorNumber,
        long seed,
        Set<String> activeModifierIds
    ) {
        return selectForGroup(
            kinGroup,
            floorNumber,
            seed,
            STANDARD_REQUIRED_ROLES,
            activeModifierIds,
            Integer.MAX_VALUE
        ).selected();
    }

    public static DungeonEnemyKinSelection selectForGroup(
        String kinGroup,
        int floorNumber,
        long seed,
        Set<DungeonEnemyRole> requiredRoles,
        Set<String> activeModifierIds,
        int maxCompositionCost
    ) {
        int floor = Math.max(1, floorNumber);
        String normalizedGroup = normalize(kinGroup);
        if (!DungeonEnemyKinCatalog.isKnownGroup(normalizedGroup)) {
            return DungeonEnemyKinSelection.unavailable(
                floor, seed, "UNKNOWN_KIN_GROUP", List.of("UNKNOWN_KIN_GROUP:" + normalizedGroup)
            );
        }
        Set<String> normalizedModifiers = normalizeModifierIds(activeModifierIds);
        List<String> incompatibilities = INCOMPATIBLE_MODIFIERS.stream()
            .filter(normalizedModifiers::contains)
            .map(id -> "INCOMPATIBLE_MODIFIER:" + id)
            .toList();
        if (!incompatibilities.isEmpty()) {
            return DungeonEnemyKinSelection.unavailable(
                floor, seed, "KIN_POPULATION_INCOMPATIBLE", incompatibilities
            );
        }

        RoleRequirements requirements = RoleRequirements.from(requiredRoles);
        if (!requirements.valid()) {
            return DungeonEnemyKinSelection.unavailable(
                floor, seed, requirements.reason(), requirements.rejectionReasons()
            );
        }
        if (maxCompositionCost < 1) {
            return DungeonEnemyKinSelection.unavailable(
                floor, seed, "COMPOSITION_COST_EXHAUSTED", List.of("MAX_COMPOSITION_COST_INVALID")
            );
        }

        List<DungeonEnemyDefinition> candidates = DungeonEnemyPoolRegistry.eligibleKinDefinitions(
            normalizedGroup, floor, maxCompositionCost, normalizedModifiers
        );
        List<DungeonEnemyDefinition> ordinary = candidates.stream()
            .filter(definition -> definition.usage() == DungeonEnemyUsage.NORMAL)
            .toList();
        List<DungeonEnemyDefinition> elite = candidates.stream()
            .filter(definition -> definition.usage() != DungeonEnemyUsage.NORMAL)
            .toList();
        String rejection = validateFamily(ordinary, elite, requirements);
        if (rejection != null) {
            return DungeonEnemyKinSelection.unavailable(
                floor,
                seed,
                "KIN_FAMILY_NOT_FEASIBLE",
                List.of(normalizedGroup + ":" + rejection)
            );
        }
        return new DungeonEnemyKinSelection(
            floor,
            seed,
            DungeonEnemyKinCatalog.CATALOG_VERSION,
            normalizedGroup,
            candidates.stream().map(DungeonEnemyDefinition::entityTypeId).sorted().toList(),
            List.of(),
            "SELECTED"
        );
    }

    public static DungeonEnemyKinSelection select(
        int floorNumber,
        long seed,
        Set<DungeonEnemyRole> requiredRoles,
        Set<String> activeModifierIds
    ) {
        return select(floorNumber, seed, requiredRoles, activeModifierIds, Integer.MAX_VALUE);
    }

    public static DungeonEnemyKinSelection select(
        int floorNumber,
        long seed,
        Set<DungeonEnemyRole> requiredRoles,
        Set<String> activeModifierIds,
        int maxCompositionCost
    ) {
        int floor = Math.max(1, floorNumber);
        Set<String> normalizedModifiers = normalizeModifierIds(activeModifierIds);
        List<String> incompatibilities = INCOMPATIBLE_MODIFIERS.stream()
            .filter(normalizedModifiers::contains)
            .map(id -> "INCOMPATIBLE_MODIFIER:" + id)
            .toList();
        if (!incompatibilities.isEmpty()) {
            return DungeonEnemyKinSelection.unavailable(
                floor, seed, "KIN_POPULATION_INCOMPATIBLE", incompatibilities
            );
        }

        RoleRequirements requirements = RoleRequirements.from(requiredRoles);
        if (!requirements.valid()) {
            return DungeonEnemyKinSelection.unavailable(
                floor, seed, requirements.reason(), requirements.rejectionReasons()
            );
        }
        if (maxCompositionCost < 1) {
            return DungeonEnemyKinSelection.unavailable(
                floor, seed, "COMPOSITION_COST_EXHAUSTED", List.of("MAX_COMPOSITION_COST_INVALID")
            );
        }

        List<FamilyAssessment> eligibleFamilies = new ArrayList<>();
        List<String> rejectionReasons = new ArrayList<>();
        for (String kinGroup : DungeonEnemyKinCatalog.groups()) {
            List<DungeonEnemyDefinition> candidates = DungeonEnemyPoolRegistry.eligibleKinDefinitions(
                kinGroup, floor, maxCompositionCost, normalizedModifiers
            );
            List<DungeonEnemyDefinition> ordinary = candidates.stream()
                .filter(definition -> definition.usage() == DungeonEnemyUsage.NORMAL)
                .toList();
            List<DungeonEnemyDefinition> elite = candidates.stream()
                .filter(definition -> definition.usage() != DungeonEnemyUsage.NORMAL)
                .toList();

            String rejection = validateFamily(ordinary, elite, requirements);
            if (rejection != null) {
                rejectionReasons.add(kinGroup + ":" + rejection);
                continue;
            }
            List<String> candidateIds = candidates.stream()
                .map(DungeonEnemyDefinition::entityTypeId)
                .sorted()
                .toList();
            eligibleFamilies.add(new FamilyAssessment(kinGroup, candidateIds));
        }

        if (eligibleFamilies.isEmpty()) {
            return DungeonEnemyKinSelection.unavailable(
                floor, seed, "NO_ROLE_SAFE_KIN_GROUP", rejectionReasons
            );
        }

        RandomSource random = RandomSource.create(seed);
        FamilyAssessment selected = eligibleFamilies.get(random.nextInt(eligibleFamilies.size()));
        return new DungeonEnemyKinSelection(
            floor,
            seed,
            DungeonEnemyKinCatalog.CATALOG_VERSION,
            selected.kinGroup(),
            selected.candidateIds(),
            rejectionReasons,
            "SELECTED"
        );
    }

    private static String validateFamily(
        List<DungeonEnemyDefinition> ordinary,
        List<DungeonEnemyDefinition> elite,
        RoleRequirements requirements
    ) {
        if (ordinary.stream().map(DungeonEnemyDefinition::entityTypeId).distinct().count() < 2) {
            return "ORDINARY_DIVERSITY_UNSAFE";
        }
        if (!ordinary.stream().anyMatch(definition -> definition.supportsRole(DungeonEnemyRole.MELEE))) {
            return "REQUIRED_ROLE_MISSING:MELEE";
        }
        if (requirements.rangedOrHighground()
            && ordinary.stream().noneMatch(definition -> definition.supportsRole(DungeonEnemyRole.RANGED)
                || definition.supportsRole(DungeonEnemyRole.HIGHGROUND))) {
            return "REQUIRED_ROLE_MISSING:RANGED_OR_HIGHGROUND";
        }
        for (DungeonEnemyRole role : requirements.exactRoles()) {
            if (ordinary.stream().noneMatch(definition -> definition.supportsRole(role))) {
                return "REQUIRED_ROLE_MISSING:" + role.name();
            }
        }
        if (requirements.eliteRequired()
            && elite.stream().noneMatch(definition -> definition.supportsRole(DungeonEnemyRole.ELITE_CANDIDATE))) {
            return "REQUIRED_ROLE_MISSING:ELITE_CANDIDATE";
        }
        return null;
    }

    private static Set<String> normalizeModifierIds(Set<String> activeModifierIds) {
        if (activeModifierIds == null || activeModifierIds.isEmpty()) return Set.of();
        Set<String> normalized = new HashSet<>();
        for (String id : activeModifierIds) {
            if (id != null && !id.isBlank()) normalized.add(id.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record FamilyAssessment(String kinGroup, List<String> candidateIds) {
    }

    private record RoleRequirements(
        boolean rangedOrHighground,
        boolean eliteRequired,
        Set<DungeonEnemyRole> exactRoles,
        boolean valid,
        String reason,
        List<String> rejectionReasons
    ) {
        static RoleRequirements from(Set<DungeonEnemyRole> requiredRoles) {
            if (requiredRoles == null || requiredRoles.isEmpty()) {
                return new RoleRequirements(false, false, Set.of(), false,
                    "REQUIRED_ROLE_SET_EMPTY", List.of("REQUIRED_ROLE_SET_EMPTY"));
            }
            EnumSet<DungeonEnemyRole> supplied = EnumSet.noneOf(DungeonEnemyRole.class);
            List<String> rejections = new ArrayList<>();
            for (DungeonEnemyRole role : requiredRoles) {
                if (role == null || !SUPPORTED_ROLES.contains(role)) {
                    rejections.add("UNSUPPORTED_REQUIRED_ROLE:" + (role == null ? "NULL" : role.name()));
                } else {
                    supplied.add(role);
                }
            }
            if (!rejections.isEmpty()) {
                return new RoleRequirements(false, false, Set.of(), false,
                    "UNSUPPORTED_REQUIRED_ROLE", rejections);
            }
            boolean ranged = supplied.remove(DungeonEnemyRole.RANGED);
            ranged |= supplied.remove(DungeonEnemyRole.HIGHGROUND);
            boolean elite = supplied.remove(DungeonEnemyRole.ELITE_CANDIDATE);
            return new RoleRequirements(ranged, elite, Set.copyOf(supplied), true, "NONE", List.of());
        }
    }
}
