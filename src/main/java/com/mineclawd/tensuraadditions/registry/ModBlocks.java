package com.mineclawd.tensuraadditions.registry;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TensuraAdditions.MOD_ID);

    public static final DeferredBlock<Block> DEEPSLATE_BRICK_WALL_V2 = BLOCKS.register("deepslate_brick_wall_v2",
        () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_BRICKS)
            .noOcclusion()));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
