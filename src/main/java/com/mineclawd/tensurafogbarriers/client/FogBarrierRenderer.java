package com.mineclawd.tensurafogbarriers.client;

import com.mineclawd.tensurafogbarriers.TensuraFogBarriers;
import com.mineclawd.tensurafogbarriers.data.FogAnimationStyle;
import com.mineclawd.tensurafogbarriers.data.FogBarrierPanel;
import com.mineclawd.tensurafogbarriers.data.FogVariant;
import com.mineclawd.tensurafogbarriers.network.FogBarrierSnapshotPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

@EventBusSubscriber(modid = TensuraFogBarriers.MOD_ID, value = Dist.CLIENT)
public final class FogBarrierRenderer {
    private static final ResourceLocation[][] FOG_TEXTURES = Arrays.stream(FogAnimationStyle.values())
        .map(style -> Arrays.stream(FogVariant.values())
            .map(variant -> ResourceLocation.fromNamespaceAndPath(
                TensuraFogBarriers.MOD_ID, "block/" + style.textureName(variant)))
            .toArray(ResourceLocation[]::new))
        .toArray(ResourceLocation[][]::new);
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
    private static final double MAX_RENDER_DISTANCE_SQR = 192.0D * 192.0D;

    private FogBarrierRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;
        if (ClientFogBarrierCache.panels().isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(RENDER_TYPE);
        PoseStack poseStack = new PoseStack();
        Vec3 camera = event.getCamera().getPosition();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();

        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (FogBarrierSnapshotPayload.Entry panel : ClientFogBarrierCache.panels()) {
            AABB visualBounds = visualBounds(panel);
            if (visualBounds.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQR || !event.getFrustum().isVisible(visualBounds)) continue;
            TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(FOG_TEXTURES[panel.style().ordinal()][panel.variant().ordinal()]);
            drawPanel(vertices, poseStack.last(), sprite, panel);
        }
        buffers.endBatch(RENDER_TYPE);
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void drawPanel(VertexConsumer vertices, PoseStack.Pose pose, TextureAtlasSprite sprite,
                                  FogBarrierSnapshotPayload.Entry panel) {
        double minY = panel.minY() - FogBarrierPanel.VISUAL_OVERSCAN;
        double maxY = panel.maxY() + FogBarrierPanel.VISUAL_OVERSCAN;
        double minLateral = panel.minLateral() - FogBarrierPanel.VISUAL_OVERSCAN;
        double maxLateral = panel.maxLateral() + FogBarrierPanel.VISUAL_OVERSCAN;
        float plane = (float) panel.plane();

        for (double y0 = minY; y0 < maxY - 1.0E-6D;) {
            double y1 = nextTileBoundary(y0, maxY);
            float vBottom = spriteV(sprite, tileCoordinate(y0, false));
            float vTop = spriteV(sprite, tileCoordinate(y1, true));
            for (double lateral0 = minLateral; lateral0 < maxLateral - 1.0E-6D;) {
                double lateral1 = nextTileBoundary(lateral0, maxLateral);
                float u0 = spriteU(sprite, tileCoordinate(lateral0, false));
                float u1 = spriteU(sprite, tileCoordinate(lateral1, true));
                float low = (float) lateral0;
                float high = (float) lateral1;
                float bottom = (float) y0;
                float top = (float) y1;
                if (panel.axis() == FogBarrierPanel.Axis.X) {
                    vertex(vertices, pose, plane, bottom, low, u0, vBottom, 1.0F, 0.0F, 0.0F);
                    vertex(vertices, pose, plane, top, low, u0, vTop, 1.0F, 0.0F, 0.0F);
                    vertex(vertices, pose, plane, top, high, u1, vTop, 1.0F, 0.0F, 0.0F);
                    vertex(vertices, pose, plane, bottom, high, u1, vBottom, 1.0F, 0.0F, 0.0F);
                } else {
                    vertex(vertices, pose, low, bottom, plane, u0, vBottom, 0.0F, 0.0F, 1.0F);
                    vertex(vertices, pose, high, bottom, plane, u1, vBottom, 0.0F, 0.0F, 1.0F);
                    vertex(vertices, pose, high, top, plane, u1, vTop, 0.0F, 0.0F, 1.0F);
                    vertex(vertices, pose, low, top, plane, u0, vTop, 0.0F, 0.0F, 1.0F);
                }
                lateral0 = lateral1;
            }
            y0 = y1;
        }
    }

    private static double nextTileBoundary(double value, double maximum) {
        double next = Math.floor(value + 1.0E-7D) + 1.0D;
        if (next <= value + 1.0E-7D) next = value + 1.0D;
        return Math.min(next, maximum);
    }

    private static float tileCoordinate(double value, boolean upperEdge) {
        double fraction = value - Math.floor(value);
        if (upperEdge && fraction < 1.0E-6D) return 1.0F;
        return (float) fraction;
    }

    private static float spriteU(TextureAtlasSprite sprite, float fraction) {
        return sprite.getU0() + (sprite.getU1() - sprite.getU0()) * fraction;
    }

    private static float spriteV(TextureAtlasSprite sprite, float fraction) {
        return sprite.getV1() + (sprite.getV0() - sprite.getV1()) * fraction;
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, float normalX, float normalY, float normalZ) {
        vertices.addVertex(pose, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    private static AABB visualBounds(FogBarrierSnapshotPayload.Entry panel) {
        double overscan = FogBarrierPanel.VISUAL_OVERSCAN;
        return panel.axis() == FogBarrierPanel.Axis.X
            ? new AABB(panel.plane() - 0.03D, panel.minY() - overscan, panel.minLateral() - overscan,
                panel.plane() + 0.03D, panel.maxY() + overscan, panel.maxLateral() + overscan)
            : new AABB(panel.minLateral() - overscan, panel.minY() - overscan, panel.plane() - 0.03D,
                panel.maxLateral() + overscan, panel.maxY() + overscan, panel.plane() + 0.03D);
    }
}
