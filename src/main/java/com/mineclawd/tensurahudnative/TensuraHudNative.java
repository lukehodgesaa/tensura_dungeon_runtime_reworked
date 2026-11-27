package com.mineclawd.tensurahudnative;

import com.mojang.logging.LogUtils;
import com.mineclawd.tensurahudnative.client.ClientHudController;
import com.mineclawd.tensurahudnative.client.ClientHudCommands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = TensuraHudNative.MOD_ID, dist = Dist.CLIENT)
public final class TensuraHudNative {
    public static final String MOD_ID = "tensura_hud_native";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TensuraHudNative(IEventBus modEventBus) {
        modEventBus.addListener(ClientHudController::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(ClientHudCommands::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(ClientHudController::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientHudController::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ClientHudController::onRenderLayer);
    }
}
