package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.item.SoulOrbRarity;
import com.mineclawd.tensuradungeon.network.SoulOrbHudPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT)
public final class SoulOrbHudOverlay {
    private static final float DESIGN_WIDTH = 1280.0F;
    private static final float DESIGN_HEIGHT = 720.0F;
    private static final int PANEL_X = 930;
    private static final int PANEL_Y = 150;
    private static final int PANEL_WIDTH = 316;
    private static final int PANEL_HEIGHT = 34;
    private static final long ACTIVITY_HOLD_MS = 1_400L;
    private static final long ACTIVITY_FADE_MS = 600L;
    private static final ResourceLocation COMMON = texture("common");
    private static final ResourceLocation UNCOMMON = texture("uncommon");
    private static final ResourceLocation RARE = texture("rare");
    private static final ResourceLocation EPIC = texture("epic");
    private static volatile SoulOrbHudPayload snapshot = inactive();
    private static String lastSessionId = "NONE";
    private static int lastActivityRevision;
    private static long lastActivityAt = Long.MIN_VALUE;

    private SoulOrbHudOverlay() {}

    public static void handle(SoulOrbHudPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> accept(payload == null ? inactive() : payload, System.currentTimeMillis()));
    }

    private static void accept(SoulOrbHudPayload payload, long now) {
        if (!payload.sessionId().equals(lastSessionId)) {
            lastSessionId = payload.sessionId();
            lastActivityRevision = 0;
            lastActivityAt = Long.MIN_VALUE;
        }
        if (payload.active() && payload.activityRevision() > lastActivityRevision) {
            lastActivityAt = now;
        }
        lastActivityRevision = Math.max(lastActivityRevision, payload.activityRevision());
        snapshot = payload;
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        SoulOrbHudPayload current = snapshot;
        float visibility = visibilityAlpha(current.active(), System.currentTimeMillis(), lastActivityAt);
        if (visibility <= 0.0F || minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        float scale = Math.min(graphics.guiWidth() / DESIGN_WIDTH, graphics.guiHeight() / DESIGN_HEIGHT);
        float offsetX = (graphics.guiWidth() - DESIGN_WIDTH * scale) * 0.5F;
        float offsetY = (graphics.guiHeight() - DESIGN_HEIGHT * scale) * 0.5F;
        graphics.pose().pushPose();
        graphics.pose().translate(offsetX, offsetY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.setColor(1.0F, 1.0F, 1.0F, visibility);
        drawPanel(graphics, minecraft, current);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    private static void drawPanel(GuiGraphics graphics, Minecraft minecraft, SoulOrbHudPayload current) {
        int x = PANEL_X;
        int y = PANEL_Y;
        fillChamfered(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xDE050C15);
        graphics.fillGradient(x + 2, y + 2, x + PANEL_WIDTH - 2, y + PANEL_HEIGHT - 2,
            0xC90B202C, 0xE603080F);
        drawChamferedBorder(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xA43EB8D2);
        graphics.fill(x + 9, y + 1, x + PANEL_WIDTH - 9, y + 2, 0xB863DFFF);
        graphics.fill(x + 15, y - 3, x + 20, y + 2, 0xFF1E9DBA);
        graphics.fill(x + 17, y - 5, x + 19, y, 0xFF63DFFF);

        graphics.drawString(minecraft.font, "SOUL ORBS", x + 11, y + 13, 0xFFB7F8FF, true);
        graphics.fill(x + 75, y + 7, x + 76, y + PANEL_HEIGHT - 7, 0x783EB8D2);
        drawCount(graphics, minecraft, x + 82, y + 8, COMMON, SoulOrbRarity.COMMON, current.common());
        drawCount(graphics, minecraft, x + 140, y + 8, UNCOMMON, SoulOrbRarity.UNCOMMON, current.uncommon());
        drawCount(graphics, minecraft, x + 198, y + 8, RARE, SoulOrbRarity.RARE, current.rare());
        drawCount(graphics, minecraft, x + 256, y + 8, EPIC, SoulOrbRarity.EPIC, current.epic());
        for (int dividerX : new int[] {136, 194, 252}) {
            graphics.fill(x + dividerX, y + 7, x + dividerX + 1, y + PANEL_HEIGHT - 7, 0x573EB8D2);
        }
    }

    private static void drawCount(
        GuiGraphics graphics,
        Minecraft minecraft,
        int x,
        int y,
        ResourceLocation texture,
        SoulOrbRarity rarity,
        int count
    ) {
        graphics.blit(texture, x, y, 18, 18, 0.0F, 0.0F, 32, 32, 32, 32);
        String value = Integer.toString(count);
        graphics.drawString(minecraft.font, value, x + 21, y + 5, rarity.rgb(), true);
    }

    private static void fillChamfered(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 3, y, x + width - 3, y + height, color);
        graphics.fill(x + 1, y + 2, x + width - 1, y + height - 2, color);
        graphics.fill(x, y + 4, x + width, y + height - 4, color);
    }

    private static void drawChamferedBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 3, y, x + width - 3, y + 1, color);
        graphics.fill(x + 3, y + height - 1, x + width - 3, y + height, color);
        graphics.fill(x, y + 4, x + 1, y + height - 4, color);
        graphics.fill(x + width - 1, y + 4, x + width, y + height - 4, color);
        graphics.fill(x + 1, y + 2, x + 3, y + 4, color);
        graphics.fill(x + width - 3, y + 2, x + width - 1, y + 4, color);
        graphics.fill(x + 1, y + height - 4, x + 3, y + height - 2, color);
        graphics.fill(x + width - 3, y + height - 4, x + width - 1, y + height - 2, color);
    }

    private static ResourceLocation texture(String rarity) {
        return ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID, "textures/item/soul_orb_" + rarity + ".png"
        );
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        snapshot = inactive();
        lastSessionId = "NONE";
        lastActivityRevision = 0;
        lastActivityAt = Long.MIN_VALUE;
    }

    static float visibilityAlpha(boolean activeFloorObjective, long now, long activityAt) {
        if (!activeFloorObjective || activityAt == Long.MIN_VALUE) return 0.0F;
        long elapsed = Math.max(0L, now - activityAt);
        if (elapsed <= ACTIVITY_HOLD_MS) return 1.0F;
        if (elapsed >= ACTIVITY_HOLD_MS + ACTIVITY_FADE_MS) return 0.0F;
        return 1.0F - (elapsed - ACTIVITY_HOLD_MS) / (float) ACTIVITY_FADE_MS;
    }

    private static SoulOrbHudPayload inactive() {
        return new SoulOrbHudPayload(false, "NONE", 0, 0, 0, 0, 0);
    }
}
