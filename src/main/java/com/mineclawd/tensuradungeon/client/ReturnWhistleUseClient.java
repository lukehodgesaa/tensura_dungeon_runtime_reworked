package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.ReturnWhistleChannelStatePayload;
import com.mineclawd.tensuradungeon.network.ReturnWhistleReleasePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT)
public final class ReturnWhistleUseClient {
    private static boolean active;
    private static int remainingDisplayTicks;
    private static ReturnWhistleChannelStatePayload presentation;
    private static float visualStrength;
    private static float visualTarget;
    private static int visualTicks;

    private ReturnWhistleUseClient() {
    }

    public static void handle(ReturnWhistleChannelStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            active = payload.active();
            presentation = payload;
            remainingDisplayTicks = payload.active() ? Integer.MAX_VALUE : payload.displayTicks();
            float progress = payload.percent() / 100.0F;
            visualTarget = payload.active() ? progress * progress * (3.0F - 2.0F * progress) : 0.0F;
        });
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            active = false;
            visualTarget = 0.0F;
            remainingDisplayTicks = 0;
            presentation = null;
        }
        visualTicks++;
        float smoothing = active ? 0.18F : 0.12F;
        visualStrength += (visualTarget - visualStrength) * smoothing;
        if (!active && visualStrength < 0.001F) {
            visualStrength = 0.0F;
        }
        if (!active && remainingDisplayTicks > 0) {
            remainingDisplayTicks--;
        }
        if (active && !minecraft.options.keyUse.isDown()) {
            active = false;
            visualTarget = 0.0F;
            PacketDistributor.sendToServer(new ReturnWhistleReleasePayload());
        }
    }

    @SubscribeEvent
    public static void distortWhistleView(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (visualStrength <= 0.0F || minecraft.player == null
            || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        double phase = (visualTicks + event.getPartialTick()) * 0.31D;
        double pulse = Math.sin(phase) * 0.009D;
        double swell = 0.012D + 0.018D * visualStrength;
        event.setFOV(event.getFOV() * (1.0D + visualStrength * (swell + pulse)));
    }

    @SubscribeEvent
    public static void renderWhistleVeil(RenderGuiEvent.Pre event) {
        if (visualStrength <= 0.0F) {
            return;
        }
        int width = event.getGuiGraphics().guiWidth();
        int height = event.getGuiGraphics().guiHeight();
        int hazeAlpha = Math.min(28, Math.round(28.0F * visualStrength));
        int edgeAlpha = Math.min(72, Math.round(72.0F * visualStrength));
        event.getGuiGraphics().fill(0, 0, width, height, hazeAlpha << 24 | 0x00106A8A);
        int edge = Math.max(4, Math.round(14.0F * visualStrength));
        event.getGuiGraphics().fill(0, 0, edge, height, edgeAlpha << 24 | 0x0028CFF5);
        event.getGuiGraphics().fill(width - edge, 0, width, height, edgeAlpha << 24 | 0x0028CFF5);
        event.getGuiGraphics().fill(0, 0, width, edge, edgeAlpha << 24 | 0x0028CFF5);
        event.getGuiGraphics().fill(0, height - edge, width, height, edgeAlpha << 24 | 0x0028CFF5);
    }

    @SubscribeEvent
    public static void renderStatus(RenderGuiEvent.Post event) {
        ReturnWhistleChannelStatePayload state = presentation;
        Minecraft minecraft = Minecraft.getInstance();
        if (state == null || minecraft.player == null
            || (!active && remainingDisplayTicks <= 0)) {
            return;
        }
        int centerX = event.getGuiGraphics().guiWidth() / 2;
        int topY = event.getGuiGraphics().guiHeight() - 128;
        String progress = "RETURN WHISTLE  " + state.percent() + "%";
        int contentWidth = Math.max(
            minecraft.font.width(state.message()), minecraft.font.width(progress)
        );
        int panelWidth = Math.max(190, contentWidth + 16);
        int left = centerX - panelWidth / 2;
        int right = centerX + panelWidth / 2;
        event.getGuiGraphics().fill(left, topY, right, topY + 34, 0xA010141B);
        event.getGuiGraphics().fill(left, topY, right, topY + 1, 0xCC38BDE8);
        int textColor = switch (state.tone()) {
            case ACTIVE -> 0xD8F6FF;
            case WARNING -> 0xFFD45A;
            case SUCCESS -> 0x72FF9C;
        };
        event.getGuiGraphics().drawCenteredString(
            minecraft.font, state.message(), centerX, topY + 5, textColor
        );
        event.getGuiGraphics().drawCenteredString(
            minecraft.font, progress, centerX, topY + 16, 0xFFFFFF
        );
        int railLeft = centerX - 82;
        int railRight = centerX + 82;
        event.getGuiGraphics().fill(railLeft, topY + 27, railRight, topY + 31, 0xCC27313F);
        int fillRight = railLeft + (railRight - railLeft) * state.percent() / 100;
        if (fillRight > railLeft) {
            event.getGuiGraphics().fill(
                railLeft, topY + 27, fillRight, topY + 31,
                state.tone() == ReturnWhistleChannelStatePayload.Tone.WARNING
                    ? 0xFFFFC857 : 0xFF38CFF5
            );
        }
    }
}
