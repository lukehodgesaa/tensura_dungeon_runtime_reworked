package com.mineclawd.tensuraadditions.registry;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import com.mineclawd.tensuraadditions.entity.DungeonExitPortalEntity;
import com.mineclawd.tensuraadditions.entity.SpawnRuneEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, TensuraAdditions.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<DungeonExitPortalEntity>>
        DUNGEON_EXIT_PORTAL = ENTITY_TYPES.register("dungeon_exit_portal",
            () -> EntityType.Builder.<DungeonExitPortalEntity>of(
                    DungeonExitPortalEntity::new, MobCategory.MISC)
                .sized(3.25F, 5.0F)
                .clientTrackingRange(12)
                .updateInterval(1)
                .build(TensuraAdditions.MOD_ID + ":dungeon_exit_portal"));

    public static final DeferredHolder<EntityType<?>, EntityType<SpawnRuneEntity>>
        SPAWN_RUNE = ENTITY_TYPES.register("spawn_rune",
            () -> EntityType.Builder.<SpawnRuneEntity>of(
                    SpawnRuneEntity::new, MobCategory.MISC)
                .sized(8.0F, 0.1F)
                .clientTrackingRange(12)
                .updateInterval(1)
                .build(TensuraAdditions.MOD_ID + ":spawn_rune"));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
