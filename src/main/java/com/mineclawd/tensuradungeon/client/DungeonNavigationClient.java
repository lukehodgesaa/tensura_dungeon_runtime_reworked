package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.DungeonNavigationPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DungeonNavigationClient {
    private static volatile List<DungeonNavigationPayload.Target> targets = List.of();

    private DungeonNavigationClient() {}

    public static void handle(DungeonNavigationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> targets = payload.targets());
    }

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "navigation"), DungeonNavigationClient::render);
    }

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || targets.isEmpty()) return;
        int count = targets.size();
        int centerX = graphics.guiWidth() / 2;
        int spacing = 42;
        int startX = centerX - ((count - 1) * spacing) / 2;
        for (int i = 0; i < count; i++) {
            DungeonNavigationPayload.Target target = targets.get(i);
            double dx = target.pos().getX() + 0.5D - minecraft.player.getX();
            double dz = target.pos().getZ() + 0.5D - minecraft.player.getZ();
            float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float angle = Mth.wrapDegrees(targetYaw - minecraft.player.getYRot());
            int x = startX + i * spacing;
            int color = target.portal() ? 0xFFB56CFF : target.branch() ? 0xFFFFC857 : 0xFF65D9FF;
            drawArrow(graphics, x, 18, angle, color);
            int distance = Mth.floor(Math.sqrt(dx * dx + dz * dz));
            String label = target.portal() ? "Portal" : target.branch() ? "Exit " + (i + 1) : "Next";
            String text = label + " · " + distance + "m";
            graphics.drawString(minecraft.font, text, x - minecraft.font.width(text) / 2, 31, color, true);
        }
    }

    private static void drawArrow(GuiGraphics graphics, int x, int y, float angle, int color) {
        RenderSystem.enableBlend();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        graphics.fill(-2, -8, 3, 7, color);
        graphics.fill(-7, -8, 8, -5, color);
        graphics.fill(-5, -11, 6, -7, color);
        graphics.fill(-3, -14, 4, -10, color);
        graphics.pose().popPose();
        RenderSystem.disableBlend();
    }
}
