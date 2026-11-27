package com.mineclawd.tensuradungeon.runtime.enemy;

import com.mineclawd.tensuradungeon.block.entity.RoomProfile;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

public final class DungeonEnemyPoolRegistry {
    private static final List<DungeonEnemyDefinition> DEFINITIONS = new ArrayList<>();
    private static boolean initialized;

    private DungeonEnemyPoolRegistry() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        DEFINITIONS.clear();
        registerTensuraRankPool();
        initialized = true;
    }
    public static boolean isInitialized() {
        initialize();
        return initialized;
    }

    public static List<DungeonEnemyDefinition> getDefinitions() {
        initialize();
        return Collections.unmodifiableList(DEFINITIONS);
    }

    public static Optional<DungeonEnemyDefinition> eligibleAuthoredBossDefinition(
        String entityTypeId,
        int floorNumber,
        boolean milestoneBoss
    ) {
        String normalizedId = entityTypeId == null
            ? ""
            : entityTypeId.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizedId.isBlank()) {
            return Optional.empty();
        }
        return eligibleBossDefinitions(floorNumber, milestoneBoss).stream()
            .filter(definition -> normalizedId.equals(definition.entityTypeId()))
            .filter(definition -> DungeonBossCatalog.forEntity(definition.entityTypeId())
                .map(entry -> entry.usage() == definition.usage())
                .orElse(false))
            .findFirst();
    }

    public static Optional<EntityType<?>> registeredEntityType(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(resolveEntityType(entityTypeId.trim().toLowerCase(java.util.Locale.ROOT)));
    }

    public static Map<DungeonEnemyRole, Integer> getRoleCounts() {
        initialize();
        Map<DungeonEnemyRole, Integer> counts = new EnumMap<>(DungeonEnemyRole.class);
        for (DungeonEnemyRole role : DungeonEnemyRole.values()) {
            counts.put(role, 0);
        }
        for (DungeonEnemyDefinition definition : DEFINITIONS) {
            for (DungeonEnemyRole role : definition.roles()) {
                counts.put(role, counts.getOrDefault(role, 0) + 1);
            }
        }
        return counts;
    }

    public static Map<String, Integer> getFamilyCounts() {
        initialize();
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (DungeonEnemyDefinition definition : DEFINITIONS) {
            counts.merge(definition.family().id(), 1, Integer::sum);
        }
        return counts;
    }

    public static long getEnabledDefinitionCount() {
        initialize();
        return DEFINITIONS.stream().filter(DungeonEnemyDefinition::enabled).count();
    }

    public static long getFallbackDefinitionCount() {
        initialize();
        return DEFINITIONS.stream().filter(definition -> "minecraft".equals(definition.sourceModId())).count();
    }

    public static long getRelatedDefinitionCount() {
        initialize();
        return DEFINITIONS.stream().filter(definition -> !"minecraft".equals(definition.sourceModId())).count();
    }

    static List<DungeonEnemyDefinition> eligibleKinDefinitions(
        String kinGroup,
        int floorNumber,
        int maxCompositionCost,
        Set<String> activeModifierIds
    ) {
        initialize();
        boolean swarmActive = activeModifierIds != null
            && activeModifierIds.stream().anyMatch(id -> "swarm".equalsIgnoreCase(id));
        return DEFINITIONS.stream()
            .filter(definition -> definition.enabled())
            .filter(definition -> Objects.equals(definition.kinGroup(), kinGroup))
            .filter(definition -> definition.isFloorAllowed(floorNumber))
            .filter(definition -> definition.compositionCost() <= maxCompositionCost)
            .filter(definition -> definition.usage() == DungeonEnemyUsage.NORMAL
                || definition.usage() == DungeonEnemyUsage.ELITE_ONLY
                || definition.usage() == DungeonEnemyUsage.ELITE_OR_BOSS)
            .filter(definition -> isKinRankEligible(
                definition,
                floorNumber,
                definition.usage() != DungeonEnemyUsage.NORMAL,
                swarmActive
            ))
            .sorted(java.util.Comparator.comparing(DungeonEnemyDefinition::entityTypeId))
            .toList();
    }

    static List<DungeonEnemyDefinition> eligibleBossDefinitions(
        int floorNumber,
        boolean milestoneBoss
    ) {
        initialize();
        return DEFINITIONS.stream()
            .filter(DungeonEnemyDefinition::enabled)
            .filter(definition -> DungeonBossCatalog.isBossCapableUsage(definition.usage()))
            .filter(definition -> definition.isFloorAllowed(floorNumber))
            .filter(definition -> definition.usage() != DungeonEnemyUsage.LATE_BOSS_ONLY || milestoneBoss)
            .filter(definition -> isRankEligible(definition, floorNumber, true, false))
            .sorted(java.util.Comparator.comparing(DungeonEnemyDefinition::entityTypeId))
            .toList();
    }

    static List<DungeonEnemyDefinition> eligibleKinBossDefinitions(
        int floorNumber,
        boolean milestoneBoss
    ) {
        initialize();
        return DEFINITIONS.stream()
            .filter(DungeonEnemyDefinition::enabled)
            .filter(definition -> DungeonBossCatalog.isBossCapableUsage(definition.usage()))
            .filter(definition -> definition.isFloorAllowed(floorNumber))
            .filter(definition -> definition.usage() != DungeonEnemyUsage.LATE_BOSS_ONLY || milestoneBoss)
            .filter(definition -> isKinRankEligible(definition, floorNumber, true, false))
            .sorted(java.util.Comparator.comparing(DungeonEnemyDefinition::entityTypeId))
            .toList();
    }

    public static DungeonEnemyPoolResolution resolve(RandomSource random, int floorNumber, RoomProfile roomProfile, DungeonEnemyRole requestedRole, Set<DungeonModifierDefinition> activeModifiers, boolean fallbackAllowed) {
        return resolve(random, floorNumber, roomProfile, requestedRole, activeModifiers, fallbackAllowed, Integer.MAX_VALUE);
    }

    public static DungeonEnemyPoolResolution resolve(RandomSource random, int floorNumber, RoomProfile roomProfile, DungeonEnemyRole requestedRole, Set<DungeonModifierDefinition> activeModifiers, boolean fallbackAllowed, int maxCompositionCost) {
        return resolve(
            random,
            floorNumber,
            roomProfile,
            requestedRole,
            activeModifiers,
            fallbackAllowed,
            maxCompositionCost,
            ""
        );
    }

    public static DungeonEnemyPoolResolution resolve(
        RandomSource random,
        int floorNumber,
        RoomProfile roomProfile,
        DungeonEnemyRole requestedRole,
        Set<DungeonModifierDefinition> activeModifiers,
        boolean fallbackAllowed,
        int maxCompositionCost,
        String kinGroup
    ) {
        initialize();
        String normalizedKinGroup = kinGroup == null ? "" : kinGroup.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            boolean eliteRequest = requestedRole == DungeonEnemyRole.ELITE_CANDIDATE;
            boolean swarmActive = activeModifiers != null && activeModifiers.contains(DungeonModifierDefinition.SWARM);
            boolean kinConstrained = !normalizedKinGroup.isBlank();
            List<DungeonEnemyDefinition> candidates = candidatesFor(
                floorNumber,
                requestedRole,
                maxCompositionCost,
                normalizedKinGroup,
                eliteRequest,
                swarmActive,
                kinConstrained
            );
            boolean kinRoleFallback = false;
            boolean defaultEliteFallback = false;
            if (candidates.isEmpty() && kinConstrained) {
                if (eliteRequest) {
                    List<DungeonEnemyDefinition> defaultElites = candidatesFor(
                        floorNumber,
                        requestedRole,
                        maxCompositionCost,
                        "",
                        true,
                        swarmActive,
                        false
                    );
                    if (!defaultElites.isEmpty()) {
                        candidates = defaultElites;
                        defaultEliteFallback = true;
                    }
                } else if (requestedRole != DungeonEnemyRole.MELEE) {
                    List<DungeonEnemyDefinition> meleeFallback = candidatesFor(
                        floorNumber,
                        DungeonEnemyRole.MELEE,
                        maxCompositionCost,
                        normalizedKinGroup,
                        false,
                        swarmActive,
                        true
                    );
                    if (!meleeFallback.isEmpty()) {
                        candidates = meleeFallback;
                        kinRoleFallback = true;
                    }
                }
            }
            String fallbackReason = defaultEliteFallback
                ? "KIN_ELITE_FALLBACK_TO_DEFAULT"
                : kinRoleFallback ? "KIN_ROLE_FALLBACK_TO_MELEE" : "NONE";

            DungeonEnemyDefinition selected = weightedPick(random, candidates, floorNumber, eliteRequest);
            if (selected != null) {
                EntityType<?> entityType = resolveEntityType(selected.entityTypeId());
                if (entityType != null) {
                    return new DungeonEnemyPoolResolution(
                        entityType,
                        selected,
                        new DungeonEnemyPoolDebugInfo(
                            true, "ENEMY_POOL", requestedRole.name(), selected.entityTypeId(),
                            selected.family().id(), selected.sourceModId(), fallbackReason,
                            candidates.size(), "NONE"
                        )
                    );
                }
                return new DungeonEnemyPoolResolution(
                    null,
                    selected,
                    new DungeonEnemyPoolDebugInfo(
                        true, "NONE", requestedRole.name(), selected.entityTypeId(),
                        selected.family().id(), selected.sourceModId(), "INVALID_ENTITY_TYPE",
                        candidates.size(), "ENTITY_TYPE_LOOKUP_FAILED"
                    )
                );
            }

            return new DungeonEnemyPoolResolution(
                null,
                null,
                new DungeonEnemyPoolDebugInfo(
                    true, "NONE", requestedRole.name(), "NONE", "NONE", "NONE",
                    "NO_CANDIDATES", candidates.size(), "NONE"
                )
            );
        } catch (Exception ex) {
            return new DungeonEnemyPoolResolution(
                null,
                null,
                new DungeonEnemyPoolDebugInfo(true, "NONE", requestedRole.name(), "NONE", "NONE", "NONE", "REGISTRY_RESOLUTION_FAILED", 0, ex.getClass().getSimpleName())
            );
        }
    }

    private static List<DungeonEnemyDefinition> candidatesFor(
        int floorNumber,
        DungeonEnemyRole requestedRole,
        int maxCompositionCost,
        String normalizedKinGroup,
        boolean eliteRequest,
        boolean swarmActive,
        boolean kinEligibility
    ) {
        List<DungeonEnemyDefinition> candidates = new ArrayList<>();
        for (DungeonEnemyDefinition definition : DEFINITIONS) {
            if (!definition.enabled()
                || !definition.isFloorAllowed(floorNumber)
                || !definition.isSelectableForRole(requestedRole)
                || definition.compositionCost() > maxCompositionCost
                || (!normalizedKinGroup.isBlank()
                    && !normalizedKinGroup.equals(definition.kinGroup()))
                || (kinEligibility
                    ? !isKinRankEligible(definition, floorNumber, eliteRequest, swarmActive)
                    : !isRankEligible(definition, floorNumber, eliteRequest, swarmActive))) {
                continue;
            }
            candidates.add(definition);
        }
        return candidates;
    }

    private static boolean isKinRankEligible(
        DungeonEnemyDefinition definition,
        int floorNumber,
        boolean eliteRequest,
        boolean swarmActive
    ) {
        return DungeonEnemyRank.fromLabel(definition.rankLabel())
            .map(rank -> DungeonEnemyRank.isKinEligible(
                floorNumber, rank, eliteRequest, swarmActive
            ))
            .orElse(true);
    }

    private static DungeonEnemyDefinition weightedPick(
        RandomSource random,
        List<DungeonEnemyDefinition> candidates,
        int floorNumber,
        boolean eliteRequest
    ) {
        if (candidates.isEmpty()) {
            return null;
        }
        int totalWeight = 0;
        for (DungeonEnemyDefinition candidate : candidates) {
            totalWeight += effectiveWeight(candidate, floorNumber, eliteRequest);
        }
        int roll = random.nextInt(totalWeight);
        int running = 0;
        for (DungeonEnemyDefinition candidate : candidates) {
            running += effectiveWeight(candidate, floorNumber, eliteRequest);
            if (roll < running) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private static boolean isRankEligible(
        DungeonEnemyDefinition definition,
        int floorNumber,
        boolean eliteRequest,
        boolean swarmActive
    ) {
        return DungeonEnemyRank.fromLabel(definition.rankLabel())
            .map(rank -> DungeonEnemyRank.isEligible(floorNumber, rank, eliteRequest, swarmActive))
            .orElse(true);
    }

    private static int effectiveWeight(
        DungeonEnemyDefinition definition,
        int floorNumber,
        boolean eliteRequest
    ) {
        int multiplier = DungeonEnemyRank.fromLabel(definition.rankLabel())
            .map(rank -> DungeonEnemyRank.weightMultiplier(floorNumber, rank, eliteRequest))
            .orElse(1);
        return Math.max(1, definition.weight()) * Math.max(1, multiplier);
    }

    private static void registerTensuraRankPool() {
        registerRankCandidate("tensura:barghest", "F", 12, DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK);
        registerRankCandidate("tensura:dragon_peacock", "F", 12, DungeonEnemyRole.RANGED, DungeonEnemyRole.HIGHGROUND);
        registerRankCandidate("tensura:hound_dog", "F", 12, DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK);
        registerRankCandidate("tensura:slime", "F", 12, DungeonEnemyRole.MELEE);

        registerRankCandidate("tensura:army_wasp", "E", 12, DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK, DungeonEnemyRole.HIGHGROUND);
        registerRankCandidate("tensura:direwolf", "E", 12, DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK);
        registerRankCandidate("tensura:evil_centipede", "E", 12, DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK);
        registerRankCandidate("tensura:giant_ant", "E", 12, DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:giant_bat", "E", 12, DungeonEnemyRole.RANGED, DungeonEnemyRole.HIGHGROUND);
        registerRankCandidate("tensura:horned_bear", "E", 12, DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:leech_lizard", "E", 12, DungeonEnemyRole.MELEE);

        registerRankCandidate("tensura:black_spider", "D", 10, DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK);
        registerRankCandidate("tensura:giant_bear", "D", 10, DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:hover_lizard", "D", 10, DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK);
        registerRankCandidate("tensura:knight_spider", "D", 10, DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK);
        registerRankCandidate("tensura:phantaspore", "D", 10, DungeonEnemyRole.RANGED);
        registerRankCandidate("tensura:salamander", "D", 10, DungeonEnemyRole.RANGED);

        registerRankCandidate("tensura:armorsaurus", "C", 8, DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:basilisk", "C", 8, DungeonEnemyRole.RANGED);
        registerRankCandidate("tensura:falmuth_knight", "C", 8, DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:feathered_serpent", "C", 8, DungeonEnemyRole.RANGED, DungeonEnemyRole.HIGHGROUND);
        registerRankCandidate("tensura:hell_caterpillar", "C", 8, DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK);
        registerRankCandidate("tensura:hell_moth", "C", 8, DungeonEnemyRole.MELEE, DungeonEnemyRole.HIGHGROUND);
        registerRankCandidate("tensura:metal_slime", "C", 8, DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:supermassive_slime", "C", 8, DungeonEnemyUsage.ELITE_OR_BOSS,
            DungeonEnemyRole.MELEE, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:sylphide", "C", 8, DungeonEnemyRole.RANGED, DungeonEnemyRole.HIGHGROUND);
        registerRankCandidate("tensura:tempest_serpent", "C", 8, DungeonEnemyRole.RANGED, DungeonEnemyRole.HIGHGROUND);
        registerRankCandidate("tensura:undine", "C", 8, DungeonEnemyRole.RANGED);
        registerRankCandidate("tensura:unicorn", "C", 8, DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:war_gnome", "C", 8, DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:winged_cat", "C", 8, DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK, DungeonEnemyRole.HIGHGROUND);

        registerRankCandidate("tensura:blade_tiger", "B", 5, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.MELEE, DungeonEnemyRole.FLANK, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:folgen", "D", 10, DungeonEnemyUsage.ELITE_OR_BOSS,
            DungeonEnemyRole.MELEE, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:kirara_mizutani", "B", 5, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.RANGED, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:kyoya_tachibana", "B", 5, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.RANGED, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:lesser_daemon", "B", 5, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.RANGED, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:shogo_taguchi", "B", 5, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.MELEE, DungeonEnemyRole.ELITE_CANDIDATE);

        registerRankCandidate("tensura:aqua_frog", "A", 3, DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:beast_gnome", "A", 3, DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:elemental_colossus", "A", 3, DungeonEnemyUsage.BOSS_ONLY,
            DungeonEnemyRole.RANGED);
        registerRankCandidate("tensura:greater_daemon", "A", 3, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.RANGED, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:mai_furuki", "A", 3, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.RANGED, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:mark_lauren", "A", 3, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.RANGED, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:megalodon", "A", 3, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.MELEE, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:orc_lord", "E", 12, DungeonEnemyUsage.ELITE_OR_BOSS,
            DungeonEnemyRole.MELEE, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:shin_ryusei", "A", 3, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.RANGED, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:shinji_tanimura", "A", 3, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.RANGED, DungeonEnemyRole.ELITE_CANDIDATE);

        registerRankCandidate("tensura:akash", "S", 2, DungeonEnemyUsage.ELITE_OR_BOSS,
            DungeonEnemyRole.MELEE, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:arch_daemon", "S", 2, DungeonEnemyUsage.ELITE_OR_BOSS,
            DungeonEnemyRole.RANGED, DungeonEnemyRole.ELITE_CANDIDATE);
        registerRankCandidate("tensura:ifrit", "A", 3, DungeonEnemyUsage.BOSS_ONLY,
            DungeonEnemyRole.RANGED);
        registerRankCandidate("tensura:orc_disaster", "A", 3, DungeonEnemyUsage.BOSS_ONLY,
            DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:shizu", "S", 2, DungeonEnemyUsage.ELITE_ONLY,
            DungeonEnemyRole.RANGED, DungeonEnemyRole.ELITE_CANDIDATE);

        registerRankCandidate("tensura_neb:carrion", "SS", 1, DungeonEnemyUsage.LATE_BOSS_ONLY,
            DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:charybdis", "S", 2, DungeonEnemyUsage.LATE_BOSS_ONLY,
            DungeonEnemyRole.RANGED);
        registerRankCandidate("tensura:gazel_dwargo", "SS", 1, DungeonEnemyUsage.LATE_BOSS_ONLY,
            DungeonEnemyRole.MELEE);
        registerRankCandidate("tensura:hinata_sakaguchi", "SS", 1, DungeonEnemyUsage.LATE_BOSS_ONLY,
            DungeonEnemyRole.RANGED);
        registerRankCandidate("tensura_neb:luminous_valentine", "SS", 1, DungeonEnemyUsage.LATE_BOSS_ONLY,
            DungeonEnemyRole.RANGED);
        registerRankCandidate("tensura_neb:rimuru_ogre_fight", "S", 2, DungeonEnemyUsage.LATE_BOSS_ONLY,
            DungeonEnemyRole.RANGED);
        registerRankCandidate("tensura_neb:veldora", "SS", 1, DungeonEnemyUsage.LATE_BOSS_ONLY,
            DungeonEnemyRole.RANGED);
    }

    private static void registerRankCandidate(String entityTypeId, String rank, int weight, DungeonEnemyRole... roles) {
        registerRankCandidate(entityTypeId, rank, weight, DungeonEnemyUsage.NORMAL, roles);
    }

    private static void registerRankCandidate(
        String entityTypeId,
        String rank,
        int weight,
        DungeonEnemyUsage usage,
        DungeonEnemyRole... roles
    ) {
        String sourceModId = ResourceLocation.parse(entityTypeId).getNamespace();
        registerOptionalCandidate(
            entityTypeId,
            sourceModId,
            "rank_" + rank.toLowerCase(),
            Set.of(roles),
            1,
            999,
            weight,
            "Tensura rank " + rank + " dungeon pool",
            usage
        );
    }

    private static void registerOptionalCandidate(
        String entityTypeId,
        String sourceModId,
        String familyId,
        Set<DungeonEnemyRole> roles,
        int minFloor,
        int maxFloor,
        int weight,
        String notes,
        DungeonEnemyUsage usage
    ) {
        if (resolveEntityType(entityTypeId) != null) {
            add(entityTypeId, sourceModId, familyId, roles, minFloor, maxFloor, weight, notes, usage);
        }
    }

    private static void add(
        String entityTypeId,
        String sourceModId,
        String familyId,
        Set<DungeonEnemyRole> roles,
        int minFloor,
        int maxFloor,
        int weight,
        String notes,
        DungeonEnemyUsage usage
    ) {
        DEFINITIONS.add(new DungeonEnemyDefinition(
            entityTypeId,
            DungeonEnemyFamily.of(familyId),
            new HashSet<>(roles),
            minFloor,
            maxFloor,
            weight,
            true,
            sourceModId,
            notes,
            usage,
            DungeonEnemyKinCatalog.groupFor(entityTypeId)
        ));
    }

    private static EntityType<?> resolveEntityType(String entityTypeId) {
        try {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(entityTypeId)).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }
}
