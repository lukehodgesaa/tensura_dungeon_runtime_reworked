package com.mineclawd.tensuradungeon.runtime.enemy;

import net.minecraft.world.entity.EntityType;

public record DungeonEnemyPoolResolution(
    EntityType<?> entityType,
    DungeonEnemyDefinition definition,
    DungeonEnemyPoolDebugInfo debugInfo
) {
    public boolean hasValidEntityType() {
        return entityType != null;
    }
}
