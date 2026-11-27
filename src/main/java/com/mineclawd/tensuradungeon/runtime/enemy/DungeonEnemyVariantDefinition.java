package com.mineclawd.tensuradungeon.runtime.enemy;

import java.util.Set;

public record DungeonEnemyVariantDefinition(
    String entityTypeId,
    String channel,
    String setterName,
    String enumClassName,
    String variantId,
    int weight,
    int minFloor,
    int maxFloor,
    Set<String> themeTags
) {
    public boolean isEligible(int floor, Set<String> activeThemeTags) {
        return floor >= minFloor && floor <= maxFloor
            && (themeTags.isEmpty() || themeTags.stream().anyMatch(activeThemeTags::contains));
    }
}
