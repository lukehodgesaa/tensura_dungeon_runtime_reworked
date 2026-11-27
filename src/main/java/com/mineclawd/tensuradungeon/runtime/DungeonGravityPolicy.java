package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.registry.ModDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class DungeonGravityPolicy {
    private DungeonGravityPolicy() {
    }

    public static boolean suppressesBlockGravity(Level level) {
        return level != null && suppressesBlockGravity(level.dimension());
    }

    public static boolean suppressesBlockGravity(ResourceKey<Level> dimension) {
        return ModDimensions.DUNGEON.equals(dimension);
    }
}
