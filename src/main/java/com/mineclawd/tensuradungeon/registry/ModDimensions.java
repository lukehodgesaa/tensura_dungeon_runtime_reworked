package com.mineclawd.tensuradungeon.registry;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModDimensions {
    public static final ResourceKey<Level> DUNGEON = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "dungeon")
    );

    private ModDimensions() {
    }
}
