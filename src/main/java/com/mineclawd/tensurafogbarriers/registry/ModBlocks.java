package com.mineclawd.tensurafogbarriers.registry;

import com.mineclawd.tensurafogbarriers.TensuraFogBarriers;
import com.mineclawd.tensurafogbarriers.block.FogPanelBackingBlock;
import com.mineclawd.tensurafogbarriers.block.GoldenFogBarrierBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TensuraFogBarriers.MOD_ID);

    public static final DeferredBlock<Block> GOLDEN_FOG_BARRIER = BLOCKS.register(
        "golden_fog_barrier",
        () -> new GoldenFogBarrierBlock(BlockBehaviour.Properties.of()
            .strength(-1.0F, 3_600_000.0F)
            .sound(SoundType.GLASS)
            .lightLevel(state -> state.getValue(GoldenFogBarrierBlock.VARIANT).lightLevel())
            .noOcclusion()
            .noLootTable()
            .pushReaction(PushReaction.BLOCK))
    );

    public static final DeferredBlock<Block> FOG_PANEL_BACKING = BLOCKS.register(
        "fog_panel_backing",
        () -> new FogPanelBackingBlock(BlockBehaviour.Properties.of()
            .strength(-1.0F, 3_600_000.0F)
            .lightLevel(state -> state.getValue(FogPanelBackingBlock.VARIANT).lightLevel())
            .noOcclusion()
            .noLootTable()
            .pushReaction(PushReaction.BLOCK))
    );

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
