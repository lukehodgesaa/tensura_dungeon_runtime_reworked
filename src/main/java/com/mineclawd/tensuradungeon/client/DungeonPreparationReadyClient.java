package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.DungeonPreparationReadyPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT)
public final class DungeonPreparationReadyClient {
    private static final int NOTICE_TICKS = 120;
    private static int remainingTicks;
    private static int readyFloor;
    private static int readyElapsedSeconds;

    private DungeonPreparationReadyClient() {
    }

    public static void handle(DungeonPreparationReadyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof DungeonPreparationScreen) {
                return;
            }
            readyFloor = payload.floor();
            readyElapsedSeconds = payload.elapsedSeconds();
            remainingTicks = NOTICE_TICKS;
        });
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    @SubscribeEvent
    public static void renderReadyNotice(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (remainingTicks <= 0 || minecraft.player == null
            || minecraft.screen instanceof DungeonPreparationScreen) {
            return;
        }
        String title = "DUNGEON IS READY TO ENTER";
        String detail = "FLOOR " + readyFloor + "  |  ELAPSED "
            + formatDuration(readyElapsedSeconds);
        int centerX = event.getGuiGraphics().guiWidth() / 2;
        int topY = event.getGuiGraphics().guiHeight() - 128;
        int contentWidth = Math.max(minecraft.font.width(title), minecraft.font.width(detail));
        int panelWidth = Math.max(220, contentWidth + 18);
        int left = centerX - panelWidth / 2;
        int right = centerX + panelWidth / 2;
        event.getGuiGraphics().fill(left, topY, right, topY + 30, 0xA010141B);
        event.getGuiGraphics().fill(left, topY, right, topY + 1, 0xDD38CFF5);
        event.getGuiGraphics().drawCenteredString(
            minecraft.font, title, centerX, topY + 6, 0x72FF9C
        );
        event.getGuiGraphics().drawCenteredString(
            minecraft.font, detail, centerX, topY + 17, 0xD8F6FF
        );
    }

    private static String formatDuration(int seconds) {
        int bounded = Math.max(0, seconds);
        return String.format(
            java.util.Locale.ROOT,
            "%02d:%02d",
            bounded / 60,
            bounded % 60
        );
    }
}
