package com.mineclawd.tensuradungeon.runtime.enemy;

import java.util.Set;

public record DungeonEnemyDefinition(
    String entityTypeId,
    DungeonEnemyFamily family,
    Set<DungeonEnemyRole> roles,
    int minFloor,
    int maxFloor,
    int weight,
    boolean enabled,
    String sourceModId,
    String notes,
    DungeonEnemyUsage usage,
    String kinGroup
) {
    public DungeonEnemyDefinition(
        String entityTypeId,
        DungeonEnemyFamily family,
        Set<DungeonEnemyRole> roles,
        int minFloor,
        int maxFloor,
        int weight,
        boolean enabled,
        String sourceModId,
        String notes
    ) {
        this(
            entityTypeId,
            family,
            roles,
            minFloor,
            maxFloor,
            weight,
            enabled,
            sourceModId,
            notes,
            roles.contains(DungeonEnemyRole.ELITE_CANDIDATE)
                ? DungeonEnemyUsage.ELITE_ONLY
                : DungeonEnemyUsage.NORMAL,
            ""
        );
    }

    public DungeonEnemyDefinition(
        String entityTypeId,
        DungeonEnemyFamily family,
        Set<DungeonEnemyRole> roles,
        int minFloor,
        int maxFloor,
        int weight,
        boolean enabled,
        String sourceModId,
        String notes,
        DungeonEnemyUsage usage
    ) {
        this(entityTypeId, family, roles, minFloor, maxFloor, weight, enabled, sourceModId, notes, usage, "");
    }

    public boolean supportsRole(DungeonEnemyRole role) {
        return roles.contains(role) || roles.contains(DungeonEnemyRole.GENERIC);
    }

    public boolean isSelectableForRole(DungeonEnemyRole role) {
        return usage.allowsCurrentRole(role) && supportsRole(role);
    }

    public boolean isFloorAllowed(int floorNumber) {
        return floorNumber >= minFloor && floorNumber <= maxFloor;
    }

    public String rankLabel() {
        String familyId = family == null ? "" : family.id();
        return familyId != null && familyId.startsWith("rank_")
            ? familyId.substring("rank_".length()).toUpperCase(java.util.Locale.ROOT)
            : "UNRANKED";
    }

    public int compositionCost() {
        return switch (rankLabel()) {
            case "SS" -> 14;
            case "S" -> 10;
            case "A" -> 7;
            case "B" -> 5;
            case "C" -> 3;
            case "D" -> 2;
            default -> 1;
        };
    }
}
