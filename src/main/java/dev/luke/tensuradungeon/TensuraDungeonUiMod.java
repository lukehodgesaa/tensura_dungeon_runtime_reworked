package dev.luke.tensuradungeon;

import dev.luke.tensuradungeon.client.ClientBootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(TensuraDungeonUiMod.MOD_ID)
public final class TensuraDungeonUiMod {
    public static final String MOD_ID = "tensura_dungeon_ui";

    public TensuraDungeonUiMod(IEventBus modBus, ModContainer container) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.initialize(modBus);
        }
    }
}
