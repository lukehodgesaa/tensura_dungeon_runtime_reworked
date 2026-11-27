package com.mineclawd.tensuradungeon;

import com.mineclawd.tensuradungeon.command.TensuraDungeonCommands;
import com.mineclawd.tensuradungeon.client.DungeonHubMusicConfig;
import com.mineclawd.tensuradungeon.compat.TensuraSkillCompatHooks;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mineclawd.tensuradungeon.network.DungeonNetwork;
import com.mineclawd.tensuradungeon.registry.ModBlockEntities;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mineclawd.tensuradungeon.registry.ModCreativeTabs;
import com.mineclawd.tensuradungeon.registry.ModEntities;
import com.mineclawd.tensuradungeon.registry.ModItems;
import com.mineclawd.tensuradungeon.registry.ModSoundEvents;
import com.mineclawd.tensuradungeon.runtime.DungeonShopConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(TensuraDungeonRuntime.MOD_ID)
public class TensuraDungeonRuntime {
    public static final String MOD_ID = "tensura_dungeon";

    public TensuraDungeonRuntime(IEventBus modEventBus, ModContainer container) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModSoundEvents.register(modEventBus);
        ModEntities.register(modEventBus);
        container.registerConfig(ModConfig.Type.CLIENT, DungeonHubMusicConfig.SPEC);
        modEventBus.addListener(DungeonNetwork::register);
        TensuraCompat.initialize();
        TensuraSkillCompatHooks.initialize();
        TensuraCompat.refreshSkillHookState();

        NeoForge.EVENT_BUS.addListener(TensuraDungeonCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(DungeonShopConfiguration::onAddReloadListeners);
    }
}
