package com.mineclawd.tensuradungeon.registry;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.ExpeditionTableBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TensuraDungeonRuntime.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RoomControllerBlockEntity>> ROOM_CONTROLLER =
        BLOCK_ENTITIES.register("room_controller", () -> BlockEntityType.Builder.of(
            RoomControllerBlockEntity::new,
            ModBlocks.ROOM_CONTROLLER.get()
        ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExpeditionTableBlockEntity>> EXPEDITION_TABLE =
        BLOCK_ENTITIES.register("expedition_table", () -> BlockEntityType.Builder.of(
            ExpeditionTableBlockEntity::new,
            ModBlocks.EXPEDITION_TABLE.get()
        ).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
