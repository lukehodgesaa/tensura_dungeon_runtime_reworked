package com.mineclawd.tensuradungeon.registry;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.entity.RiftVoidWarpEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
        BuiltInRegistries.ENTITY_TYPE,
        TensuraDungeonRuntime.MOD_ID
    );

    public static final DeferredHolder<EntityType<?>, EntityType<RiftVoidWarpEntity>> RIFT_VOID_WARP =
        ENTITIES.register("rift_void_warp", () -> EntityType.Builder.<RiftVoidWarpEntity>of(
                RiftVoidWarpEntity::new,
                MobCategory.MISC
            )
            .sized(0.01F, 0.01F)
            .noSummon()
            .build("rift_void_warp"));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
