package com.mineclawd.tensuradungeon.runtime.enemy;

import java.util.List;
import java.util.Locale;

public record DungeonEnemyKinSelection(
    int floor,
    long seed,
    int catalogVersion,
    String selectedKinGroup,
    List<String> eligibleCandidateIds,
    List<String> rejectionReasons,
    String reason
) {
    public static final int MAX_CANDIDATES = 128;
    public static final int MAX_REJECTION_REASONS = 128;

    public DungeonEnemyKinSelection {
        floor = Math.max(1, floor);
        catalogVersion = Math.max(1, catalogVersion);
        selectedKinGroup = bounded(selectedKinGroup, 48).toLowerCase(Locale.ROOT);
        eligibleCandidateIds = boundedList(eligibleCandidateIds, MAX_CANDIDATES, 96);
        rejectionReasons = boundedList(rejectionReasons, MAX_REJECTION_REASONS, 128);
        reason = bounded(reason, 96);
    }

    public static DungeonEnemyKinSelection unavailable(
        int floor,
        long seed,
        String reason,
        List<String> rejectionReasons
    ) {
        return new DungeonEnemyKinSelection(
            floor,
            seed,
            DungeonEnemyKinCatalog.CATALOG_VERSION,
            "",
            List.of(),
            rejectionReasons,
            reason
        );
    }

    public boolean selected() {
        return !selectedKinGroup.isBlank() && !eligibleCandidateIds.isEmpty();
    }

    private static List<String> boundedList(List<String> values, int limit, int maximum) {
        if (values == null || values.isEmpty()) return List.of();
        return List.copyOf(values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> bounded(value, maximum))
            .limit(limit)
            .toList());
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
