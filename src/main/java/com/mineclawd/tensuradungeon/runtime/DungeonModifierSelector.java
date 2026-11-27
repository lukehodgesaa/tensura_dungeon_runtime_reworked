package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.runtime.enemy.DungeonBossResolver;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinCatalog;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinSelector;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.RandomSource;

public final class DungeonModifierSelector {
    private static final Set<DungeonModifierDefinition> LIVE_CORE_CANDIDATES = EnumSet.of(
        DungeonModifierDefinition.ARMORED,
        DungeonModifierDefinition.MOLTEN,
        DungeonModifierDefinition.REGENERATIVE,
        DungeonModifierDefinition.SWARM,
        DungeonModifierDefinition.SUMMONER
    );
    private static final Set<String> TEST_ADDON_CANDIDATE_IDS = Set.of(
        "glass-cannon", "barbed-hide", "shatterproof", "rallying-cry", "dread-aura"
    );
    private static final String BOSS_AFFINITY_ID = "boss-affinity";
    private static final String RISING_HEAT_ID = "rising-heat";
    private static final int RISING_HEAT_MIN_FLOOR = 10;
    private static final int RISING_HEAT_CHANCE_PERCENT = 20;
    private static final List<BoonDefinition> BOONS = List.of(
        new BoonDefinition("damage-boost", 10),
        new BoonDefinition("barrier", 10)
    );

    private DungeonModifierSelector() {}

    public static PreparedFloorModifierPlan select(int floor, long seed, String reason) {
        int safeFloor = Math.max(1, floor);
        RandomSource random = RandomSource.create(seed);
        int targetCount = targetCount(safeFloor, random);
        int primaryCap = safeFloor < 20 ? 2 : 4;
        List<String> selectedIds = new ArrayList<>(targetCount);
        Set<String> wardCategories = new HashSet<>();
        int nullifications = 0;
        int primaryCount = 0;
        while (selectedIds.size() < targetCount) {
            int primaryCountAtStart = primaryCount;
            List<DungeonModifierDefinition> coreCandidates = LIVE_CORE_CANDIDATES.stream()
                .filter(candidate -> candidate.getMinFloor() <= safeFloor)
                .filter(candidate -> {
                    DungeonModifierCatalog.Entry entry =
                        DungeonModifierCatalog.byId(candidate.getId());
                    return entry != null && entry.isRollable();
                })
                .filter(candidate -> !selectedIds.contains(candidate.getId()))
                .filter(candidate -> DungeonModifierCompatibilityMatrix.areCompatible(
                    append(selectedIds, candidate.getId())))
                .filter(candidate -> !candidate.isPrimary() || primaryCountAtStart < primaryCap)
                .toList();
            List<DungeonModifierCatalog.Entry> addonCandidates = testAddonCandidates(
                safeFloor, seed, selectedIds, primaryCountAtStart, primaryCap
            );
            List<DungeonModifierCatalog.Entry> resistances = wardCandidates(
                safeFloor, false, wardCategories
            );
            List<DungeonModifierCatalog.Entry> nullificationCandidates =
                nullifications == 0
                    && primaryCount < primaryCap
                    ? wardCandidates(safeFloor, true, wardCategories)
                    : List.of();
            int coreWeight = coreCandidates.stream()
                .mapToInt(DungeonModifierDefinition::getWeight).sum();
            int addonWeight = addonCandidates.stream()
                .mapToInt(entry -> Math.max(1, entry.weight())).sum();
            int resistanceWeight = resistances.isEmpty() || wardCategories.size() >= 2 ? 0 : 10;
            int nullificationWeight =
                nullificationCandidates.isEmpty() || wardCategories.size() >= 2 ? 0 : 3;
            int roll = random.nextInt(Math.max(
                1, coreWeight + addonWeight + resistanceWeight + nullificationWeight
            ));
            if (roll < coreWeight) {
                DungeonModifierDefinition selectedCore = weighted(coreCandidates, random);
                selectedIds.add(selectedCore.getId());
                if (selectedCore.isPrimary()) primaryCount++;
            } else if ((roll -= coreWeight) < addonWeight) {
                DungeonModifierCatalog.Entry selectedAddon =
                    weightedEntries(addonCandidates, random);
                selectedIds.add(selectedAddon.id());
                if (isPrimaryEntry(selectedAddon)) primaryCount++;
            } else if ((roll -= addonWeight) < resistanceWeight) {
                DungeonModifierCatalog.Entry ward =
                    resistances.get(random.nextInt(resistances.size()));
                selectedIds.add(ward.id());
                wardCategories.add(ward.wardCategory());
            } else if (!nullificationCandidates.isEmpty()) {
                DungeonModifierCatalog.Entry ward =
                    nullificationCandidates.get(random.nextInt(nullificationCandidates.size()));
                selectedIds.add(ward.id());
                wardCategories.add(ward.wardCategory());
                nullifications++;
                primaryCount++;
            } else if (!coreCandidates.isEmpty()) {
                DungeonModifierDefinition selectedCore = weighted(coreCandidates, random);
                selectedIds.add(selectedCore.getId());
                if (selectedCore.isPrimary()) primaryCount++;
            } else if (!addonCandidates.isEmpty()) {
                DungeonModifierCatalog.Entry selectedAddon =
                    weightedEntries(addonCandidates, random);
                selectedIds.add(selectedAddon.id());
                if (isPrimaryEntry(selectedAddon)) primaryCount++;
            } else {
                break;
            }
        }
        if (selectedIds.size() < minimumCount(safeFloor)) {
            throw new IllegalStateException(
                "Unable to fill minimum modifier plan floor=" + safeFloor + " target=" + targetCount
                    + " selected=" + selectedIds
            );
        }
        if (selectedIds.stream().anyMatch(DungeonEnemyKinCatalog::isKinModifierId)
            && selectedIds.size() < maximumCount(safeFloor)
            && primaryCount < primaryCap
            && DungeonBossResolver.resolveNormal(safeFloor, seed).bossSlotAvailable()) {
            DungeonModifierCatalog.Entry bossAffinity =
                DungeonModifierCatalog.byId(BOSS_AFFINITY_ID);
            if (bossAffinity != null
                && bossAffinity.isRollable()
                && bossAffinity.minFloor() <= safeFloor
                && DungeonModifierCompatibilityMatrix.areCompatible(
                    append(selectedIds, BOSS_AFFINITY_ID))) {
                selectedIds.add(BOSS_AFFINITY_ID);
            }
        }
        String boonId = selectBoon(safeFloor, random);
        PreparedFloorModifierPlan plan = new PreparedFloorModifierPlan(
            safeFloor,
            seed,
            PreparedFloorModifierPlan.CURRENT_ALGORITHM,
            selectedIds,
            boonId,
            reason
        );
        if (safeFloor >= RISING_HEAT_MIN_FLOOR && random.nextInt(100) < RISING_HEAT_CHANCE_PERCENT) {
            plan = plan.withRoomPressure(RISING_HEAT_ID);
        }
        return plan;
    }

    private static List<DungeonModifierCatalog.Entry> testAddonCandidates(
        int floor,
        long seed,
        List<String> selectedIds,
        int primaryCount,
        int primaryCap
    ) {
        return DungeonModifierCatalog.entries().stream()
            .filter(entry -> TEST_ADDON_CANDIDATE_IDS.contains(entry.id())
                || DungeonEnemyKinCatalog.isKinModifierId(entry.id()))
            .filter(DungeonModifierCatalog.Entry::isRollable)
            .filter(entry -> entry.minFloor() <= floor)
            .filter(entry -> !selectedIds.contains(entry.id()))
            .filter(entry -> !DungeonEnemyKinCatalog.isKinModifierId(entry.id())
                || selectedIds.stream().noneMatch(DungeonEnemyKinCatalog::isKinModifierId))
            .filter(entry -> DungeonModifierCompatibilityMatrix.areCompatible(
                append(selectedIds, entry.id())))
            .filter(entry -> !isPrimaryEntry(entry) || primaryCount < primaryCap)
            .filter(entry -> !DungeonEnemyKinCatalog.isKinModifierId(entry.id())
                || DungeonEnemyKinSelector.isFeasibleForGroup(
                    DungeonEnemyKinCatalog.groupForModifierId(entry.id()),
                    floor,
                    seed,
                    Set.copyOf(selectedIds)
                ))
            .toList();
    }

    private static boolean isPrimaryEntry(DungeonModifierCatalog.Entry entry) {
        return "PRIMARY".equals(entry.classification()) || "BOSS".equals(entry.classification());
    }

    private static DungeonModifierCatalog.Entry weightedEntries(
        List<DungeonModifierCatalog.Entry> candidates,
        RandomSource random
    ) {
        int total = candidates.stream().mapToInt(entry -> Math.max(1, entry.weight())).sum();
        int roll = random.nextInt(Math.max(1, total));
        for (DungeonModifierCatalog.Entry candidate : candidates) {
            roll -= Math.max(1, candidate.weight());
            if (roll < 0) return candidate;
        }
        return candidates.getLast();
    }

    private static List<DungeonModifierCatalog.Entry> wardCandidates(
        int floor,
        boolean nullification,
        Set<String> usedCategories
    ) {
        String suffix = nullification ? "nullification" : "resistance";
        return DungeonModifierCatalog.entries().stream()
            .filter(DungeonModifierCatalog.Entry::isRollable)
            .filter(entry -> entry.minFloor() <= floor)
            .filter(entry -> entry.id().startsWith("ward-" + suffix + "-"))
            .filter(entry -> !usedCategories.contains(entry.wardCategory()))
            .toList();
    }

    public static int minimumCount(int floor) {
        if (floor < 10) return 2;
        if (floor < 20) return 2;
        if (floor < 30) return 4;
        if (floor < 40) return 6;
        return 8;
    }

    public static int maximumCount(int floor) {
        if (floor < 10) return 2;
        if (floor < 20) return 4;
        if (floor < 30) return 6;
        if (floor < 40) return 8;
        return 10;
    }

    private static int targetCount(int floor, RandomSource random) {
        int minimum = minimumCount(floor);
        return minimum + random.nextInt(maximumCount(floor) - minimum + 1);
    }

    private static DungeonModifierDefinition weighted(
        List<DungeonModifierDefinition> candidates,
        RandomSource random
    ) {
        int total = candidates.stream().mapToInt(DungeonModifierDefinition::getWeight).sum();
        int roll = random.nextInt(Math.max(1, total));
        for (DungeonModifierDefinition candidate : candidates) {
            roll -= Math.max(1, candidate.getWeight());
            if (roll < 0) return candidate;
        }
        return candidates.getLast();
    }

    private static List<String> append(List<String> ids, String id) {
        List<String> appended = new ArrayList<>(ids.size() + 1);
        appended.addAll(ids);
        appended.add(id);
        return appended;
    }

    private static String selectBoon(int floor, RandomSource random) {
        int chance = floor < 10 ? 0 : floor < 30 ? 20 : 30;
        if (chance == 0 || random.nextInt(100) >= chance) return "";
        List<BoonDefinition> eligible = BOONS.stream()
            .filter(boon -> boon.minFloor <= floor)
            .filter(boon -> {
                DungeonModifierCatalog.Entry entry = DungeonModifierCatalog.byId(boon.id);
                return entry != null && entry.isRollable();
            })
            .toList();
        return eligible.isEmpty() ? "" : eligible.get(random.nextInt(eligible.size())).id;
    }

    private record BoonDefinition(String id, int minFloor) {}
}
