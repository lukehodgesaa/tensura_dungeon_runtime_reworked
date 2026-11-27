package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.client.DungeonClientSoundEffects;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientBootstrap {
    private static long lastHandledKurobeActionRequestId;
    private static long lastHandledShopActionRequestId = -1L;
    private static long lastHandledBankActionRequestId;

    public static final KeyMapping OPEN_DUNGEON = new KeyMapping(
            "key.tensura_dungeon_ui.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            85,
            "key.categories.tensura_dungeon_ui"
    );

    private ClientBootstrap() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(ClientBootstrap::registerKeys);
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::clientTick);
        DungeonUiApi.setOpenAction(() -> {
            DungeonRuntimeBridge.refresh();
            Minecraft.getInstance().setScreen(new DungeonScreen());
        });
        DungeonShopUiApi.setTabSink(DungeonRuntimeBridge::openShopTab);
        DungeonShopUiApi.setActionSink(DungeonRuntimeBridge::submitShopAction);
        DungeonBankUiApi.setActionSink(DungeonRuntimeBridge::submitBankAction);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_DUNGEON);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (DungeonRuntimeBridge.consumeLootSummaryOpenRequest()
                && DungeonRuntimeBridge.lootSummary() != null
                && !(minecraft.screen instanceof DungeonLootSummaryScreen)) {
            minecraft.setScreen(new DungeonLootSummaryScreen(minecraft.screen));
        }
        if (DungeonRuntimeBridge.consumeShopOpenRequest()
                && DungeonRuntimeBridge.shopSnapshot() != null) {
            boolean alreadyShop = minecraft.screen instanceof DungeonShopLdLib2Screen;
            if (!alreadyShop) {
                DungeonClientSoundEffects.playMenuOpen();
            }
            DungeonShopUiApi.open(
                DungeonShopProjectionMapper.from(DungeonRuntimeBridge.shopSnapshot())
            );
        }
        if (DungeonRuntimeBridge.consumeBankOpenRequest()
                && DungeonRuntimeBridge.bankSnapshot() != null) {
            boolean alreadyBank = minecraft.screen instanceof DungeonBankScreen;
            if (!alreadyBank) {
                DungeonClientSoundEffects.playMenuOpen();
            }
            DungeonBankUiApi.open(
                DungeonBankProjectionMapper.from(DungeonRuntimeBridge.bankSnapshot())
            );
        }
        if (DungeonRuntimeBridge.consumeKurobeForgeOpenRequest()
                && DungeonRuntimeBridge.kurobeForgeSnapshot() != null) {
            boolean alreadyForge = minecraft.screen instanceof KurobeForgeScreen;
            if (!alreadyForge) {
                DungeonClientSoundEffects.playMenuOpen();
            }
            KurobeForgeUiApi.open(
                KurobeForgeProjectionMapper.from(DungeonRuntimeBridge.kurobeForgeSnapshot())
            );
        }
        var kurobeResult = DungeonRuntimeBridge.kurobeForgeActionResult();
        if (kurobeResult != null
                && kurobeResult.requestId() > lastHandledKurobeActionRequestId) {
            lastHandledKurobeActionRequestId = kurobeResult.requestId();
            if (kurobeResult.status()
                    == com.mineclawd.tensuradungeon.network.KurobeForgeActionResultPayload.ResultStatus.SUCCESS) {
                DungeonClientSoundEffects.playShopPurchaseSuccess();
            } else {
                DungeonClientSoundEffects.playShopPurchaseFailure();
            }
        }
        var actionResult = DungeonRuntimeBridge.shopActionResult();
        if (actionResult != null && actionResult.requestId() > lastHandledShopActionRequestId) {
            lastHandledShopActionRequestId = actionResult.requestId();
            DungeonClientSoundEffects.playShopActionResult(actionResult);
        }
        var bankResult = DungeonRuntimeBridge.bankActionResult();
        if (bankResult != null && bankResult.requestId() > lastHandledBankActionRequestId) {
            lastHandledBankActionRequestId = bankResult.requestId();
            if (bankResult.status()
                    == com.mineclawd.tensuradungeon.network.DungeonBankActionResultPayload.ResultStatus.SUCCESS) {
                DungeonClientSoundEffects.playShopPurchaseSuccess();
            } else {
                DungeonClientSoundEffects.playShopPurchaseFailure();
            }
        }
        while (OPEN_DUNGEON.consumeClick()) {
            if (Screen.hasControlDown() && Screen.hasShiftDown()) {
                if (minecraft.screen instanceof KurobeForgeScreen) {
                    minecraft.setScreen(null);
                } else {
                    KurobeForgeUiApi.openVisualPreview();
                }
            } else if (Screen.hasControlDown()) {
                if (minecraft.screen instanceof DungeonBankScreen) {
                    minecraft.setScreen(null);
                } else {
                    DungeonRuntimeBridge.openBank();
                }
            } else if (minecraft.screen instanceof DungeonScreen) {
                minecraft.setScreen(null);
            } else {
                DungeonUiApi.open();
            }
        }
    }
}
