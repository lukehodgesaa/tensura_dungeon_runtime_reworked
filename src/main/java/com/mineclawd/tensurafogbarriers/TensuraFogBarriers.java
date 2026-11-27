package com.mineclawd.tensurafogbarriers;

import com.mineclawd.tensurafogbarriers.config.FogBarrierConfig;
import com.mineclawd.tensurafogbarriers.network.FogBarrierNetwork;
import com.mineclawd.tensurafogbarriers.registry.ModBlocks;
import com.mineclawd.tensurafogbarriers.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(TensuraFogBarriers.MOD_ID)
public final class TensuraFogBarriers {
    public static final String MOD_ID = "tensura_fog_barriers";

    public TensuraFogBarriers(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, FogBarrierConfig.SPEC);
        modEventBus.addListener(FogBarrierNetwork::register);
        modEventBus.addListener(TensuraFogBarriers::addCreativeTabContents);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.GOLDEN_FOG_BARRIER.get());
        }
    }
}
