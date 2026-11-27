package com.mineclawd.tensuradungeon.runtime.enemy;

import java.util.List;
import java.util.Locale;

public record DungeonBossResolution(
    int floor,
    long seed,
    int catalogVersion,
    boolean bossSlotAvailable,
    boolean milestoneBoss,
    String affinityKinGroup,
    String authoredTheme,
    List<String> eligibleCandidateIds,
    List<String> matchingCandidateIds,
    String selectedBossId,
    String reason
) {
    public static final int MAX_CANDIDATES = 128;

    public DungeonBossResolution {
        floor = Math.max(1, floor);
        catalogVersion = Math.max(1, catalogVersion);
        milestoneBoss = bossSlotAvailable && milestoneBoss;
        affinityKinGroup = normalize(affinityKinGroup);
        authoredTheme = normalize(authoredTheme);
        eligibleCandidateIds = boundedIds(eligibleCandidateIds);
        matchingCandidateIds = boundedIds(matchingCandidateIds);
        selectedBossId = normalize(selectedBossId);
        reason = boundedReason(reason);
    }

    public static DungeonBossResolution noBossSlot(int floor, long seed) {
        return new DungeonBossResolution(
            floor, seed, DungeonBossCatalog.CATALOG_VERSION, false, false,
            "", "", List.of(), List.of(), "", "NO_BOSS_SLOT"
        );
    }

    public boolean selected() {
        return bossSlotAvailable && !selectedBossId.isBlank();
    }

    public boolean affinityMatched() {
        return "AFFINITY_MATCH_SELECTED".equals(reason);
    }

    public boolean neutralFallback() {
        return "AFFINITY_NO_MATCH_NEUTRAL_FALLBACK".equals(reason);
    }

    private static List<String> boundedIds(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return List.copyOf(values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(DungeonBossResolution::normalize)
            .limit(MAX_CANDIDATES)
            .toList());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String boundedReason(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return normalized.length() <= 96 ? normalized : normalized.substring(0, 96);
    }
}
