package com.mineclawd.dungeonsunbound.npc;

import com.mineclawd.dungeonsunbound.TensuraDungeonsUnbound;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DungeonNpcEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, TensuraDungeonsUnbound.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<DungeonNpc>> DUNGEON_NPC =
        ENTITY_TYPES.register("dungeon_npc", () -> EntityType.Builder.<DungeonNpc>of(DungeonNpc::new, MobCategory.MISC)
            .sized(0.7F, 2.0F)
            .eyeHeight(1.75F)
            .clientTrackingRange(10)
            .build(TensuraDungeonsUnbound.MOD_ID + ":dungeon_npc"));

    private DungeonNpcEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(DungeonNpcEntities::createAttributes);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(DUNGEON_NPC.get(), DungeonNpc.createAttributes().build());
    }
}
