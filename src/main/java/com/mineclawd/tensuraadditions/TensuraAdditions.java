package com.mineclawd.tensuraadditions;

import com.mineclawd.tensuraadditions.compat.tensura.TensuraEnergyCapacityEvents;
import com.mineclawd.tensuraadditions.compat.moreskills.MoreSkillsCompat;
import com.mineclawd.tensuraadditions.compat.moreskills.MoreSkillsCompatNetwork;
import com.mineclawd.tensuraadditions.registry.ModBlocks;
import com.mineclawd.tensuraadditions.registry.ModCreativeTabs;
import com.mineclawd.tensuraadditions.registry.ModEntities;
import com.mineclawd.tensuraadditions.registry.ModItems;
import com.mineclawd.tensuraadditions.registry.ModParticles;
import com.mineclawd.tensuraadditions.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(TensuraAdditions.MOD_ID)
public final class TensuraAdditions {
    public static final String MOD_ID = "tensura_additions";

    public TensuraAdditions(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModParticles.register(modEventBus);
        ModSounds.register(modEventBus);
        modEventBus.addListener(MoreSkillsCompatNetwork::register);

        TensuraEnergyCapacityEvents.initialize();
        MoreSkillsCompat.initialize();
    }
}
