package com.mineclawd.tensurafogbarriers.registry;

import com.mineclawd.tensurafogbarriers.TensuraFogBarriers;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TensuraFogBarriers.MOD_ID);
    public static final DeferredItem<BlockItem> GOLDEN_FOG_BARRIER = ITEMS.registerSimpleBlockItem(ModBlocks.GOLDEN_FOG_BARRIER);

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
