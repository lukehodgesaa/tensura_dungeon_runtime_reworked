package com.mineclawd.tensuraadditions.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mineclawd.dungeonsunbound.npc.DungeonNpc;
import com.mineclawd.tensuraadditions.TensuraAdditions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@EventBusSubscriber(
    modid = TensuraAdditions.MOD_ID,
    bus = EventBusSubscriber.Bus.GAME,
    value = Dist.CLIENT
)
public final class DungeonNpcNameplateEvents {
    private static final ResourceLocation DUNGEON_DIMENSION =
        ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "dungeon");

    private static final double LABEL_RANGE = 24.0D;
    private static final double LABEL_RANGE_SQUARED = LABEL_RANGE * LABEL_RANGE;
    private static final float TITLE_SCALE = 0.72F;
    private static final float LABEL_SCALE = 0.022F;
    private static final float LABEL_GAP = 0.18F;

    private DungeonNpcNameplateEvents() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !DUNGEON_DIMENSION.equals(level.dimension().location())
            || minecraft.player == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Font font = minecraft.font;
        BufferBuilder plaqueBuffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        );

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 771, 1, 0);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        boolean drewPlaques = false;
        List<RenderedLabel> labels = new ArrayList<>();
        PoseStack poseStack = event.getPoseStack();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof DungeonNpc npc) || !npc.isAlive()
                || !npc.profile().showName()
                || npc.distanceToSqr(camera) > LABEL_RANGE_SQUARED) {
                continue;
            }

            String name = npc.profile().name();
            String title = npc.profile().title();
            if (name == null || name.isBlank()) {
                continue;
            }

            Component nameText = Component.literal(name);
            Component titleText = title == null || title.isBlank()
                ? null
                : Component.literal("<" + title + ">");
            int nameWidth = font.width(nameText);
            int titleWidth = titleText == null ? 0 : Math.round(font.width(titleText) * TITLE_SCALE);
            int plaqueWidth = Math.max(nameWidth + 12, titleWidth + 12);
            int stripWidth = Math.max(titleWidth + 10, 40);
            float labelScale = LABEL_SCALE * Mth.clamp(npc.profile().scale(), 0.80F, 1.35F);
            float labelBottom = titleText == null ? 10.0F : 24.0F;
            PlaquePalette palette = paletteFor(name, title);
            double verticalOffset = verticalOffsetFor(name, title);

            double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - camera.x;
            double y = Mth.lerp(partialTick, entity.yOld, entity.getY())
                + entity.getBbHeight() + LABEL_GAP + labelBottom * labelScale + verticalOffset - camera.y;
            double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - camera.z;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(labelScale, -labelScale, labelScale);

            Matrix4f matrix = poseStack.last().pose();
            float nameLeft = -plaqueWidth * 0.5F;
            float nameRight = plaqueWidth * 0.5F;
            float stripLeft = -stripWidth * 0.5F;
            float stripRight = stripWidth * 0.5F;

            drawSteppedPlaque(
                plaqueBuffer,
                matrix,
                nameLeft,
                -7.0F,
                nameRight,
                10.0F,
                palette.nameFill(),
                palette.nameBorder()
            );
            if (titleText != null) {
                drawSteppedPlaque(
                    plaqueBuffer,
                    matrix,
                    stripLeft,
                    11.0F,
                    stripRight,
                    24.0F,
                    palette.titleFill(),
                    palette.titleBorder()
                );
            }
            drewPlaques = true;
            poseStack.popPose();

            labels.add(new RenderedLabel(
                nameText,
                titleText,
                nameWidth,
                titleWidth,
                x,
                y,
                z,
                labelScale,
                palette
            ));
        }

        if (drewPlaques) {
            BufferUploader.drawWithShader(plaqueBuffer.buildOrThrow());
        }
        for (RenderedLabel label : labels) {
            renderText(minecraft, font, poseStack, label);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void renderText(
        Minecraft minecraft,
        Font font,
        PoseStack poseStack,
        RenderedLabel label
    ) {
        poseStack.pushPose();
        poseStack.translate(label.x(), label.y(), label.z());
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(label.labelScale(), -label.labelScale(), label.labelScale());

        MultiBufferSource.BufferSource textBuffers = MultiBufferSource.immediate(
            new ByteBufferBuilder(16_384)
        );
        int packedLight = LightTexture.FULL_BRIGHT;
        font.drawInBatch(
            label.nameText(),
            -label.nameWidth() * 0.5F,
            -3.0F,
            label.palette().nameTextColor(),
            false,
            poseStack.last().pose(),
            textBuffers,
            Font.DisplayMode.NORMAL,
            0,
            packedLight
        );
        if (label.titleText() != null) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 13.0F, 0.0F);
            poseStack.scale(TITLE_SCALE, TITLE_SCALE, TITLE_SCALE);
            font.drawInBatch(
                label.titleText(),
                -font.width(label.titleText()) * 0.5F,
                0.0F,
                label.palette().titleTextColor(),
                false,
                poseStack.last().pose(),
                textBuffers,
                Font.DisplayMode.NORMAL,
                0,
                packedLight
            );
            poseStack.popPose();
        }
        textBuffers.endBatch();
        poseStack.popPose();
    }

    private static PlaquePalette paletteFor(String name, String title) {
        String identity = (name + " " + (title == null ? "" : title)).toLowerCase(Locale.ROOT);
        if (identity.contains("gard") || identity.contains("mjoll") || identity.contains("bank")) {
            return PlaquePalette.GARD;
        }
        if (identity.contains("kaijin") || identity.contains("armor") || identity.contains("armour")) {
            return PlaquePalette.KAIJIN;
        }
        if (identity.contains("kurobe") || identity.contains("weapon")) {
            return PlaquePalette.KUROBE;
        }
        if (identity.contains("gobta") || identity.contains("goblin")) {
            return PlaquePalette.GOBTA;
        }
        if (identity.contains("testarossa")) {
            return PlaquePalette.TESTAROSSA;
        }
        if (identity.contains("treyni") || identity.contains("dryad") || identity.contains("jura")) {
            return PlaquePalette.TREYNI;
        }
        return PlaquePalette.DEFAULT;
    }

    private static double verticalOffsetFor(String name, String title) {
        String identity = (name + " " + (title == null ? "" : title)).toLowerCase(Locale.ROOT);
        return identity.contains("kaijin") ? 0.50D : 0.0D;
    }

    private static void drawSteppedPlaque(
        BufferBuilder buffer,
        Matrix4f matrix,
        float left,
        float top,
        float right,
        float bottom,
        int fill,
        int border
    ) {
        int fillR = (fill >> 16) & 0xFF;
        int fillG = (fill >> 8) & 0xFF;
        int fillB = fill & 0xFF;
        int fillA = (fill >> 24) & 0xFF;
        int borderR = (border >> 16) & 0xFF;
        int borderG = (border >> 8) & 0xFF;
        int borderB = border & 0xFF;
        int borderA = (border >> 24) & 0xFF;

        quad(buffer, matrix, left + 2.0F, top, right - 2.0F, bottom, fillR, fillG, fillB, fillA);
        quad(buffer, matrix, left, top + 2.0F, right, bottom - 2.0F, fillR, fillG, fillB, fillA);

        quad(buffer, matrix, left + 2.0F, top, right - 2.0F, top + 1.0F, borderR, borderG, borderB, borderA);
        quad(buffer, matrix, left + 2.0F, bottom - 1.0F, right - 2.0F, bottom, borderR, borderG, borderB, borderA);
        quad(buffer, matrix, left, top + 2.0F, left + 1.0F, bottom - 2.0F, borderR, borderG, borderB, borderA);
        quad(buffer, matrix, right - 1.0F, top + 2.0F, right, bottom - 2.0F, borderR, borderG, borderB, borderA);
        quad(buffer, matrix, left + 1.0F, top + 1.0F, left + 2.0F, top + 2.0F, borderR, borderG, borderB, borderA);
        quad(buffer, matrix, right - 2.0F, top + 1.0F, right - 1.0F, top + 2.0F, borderR, borderG, borderB, borderA);
        quad(buffer, matrix, left + 1.0F, bottom - 2.0F, left + 2.0F, bottom - 1.0F, borderR, borderG, borderB, borderA);
        quad(buffer, matrix, right - 2.0F, bottom - 2.0F, right - 1.0F, bottom - 1.0F, borderR, borderG, borderB, borderA);
    }

    private static void quad(
        BufferBuilder buffer,
        Matrix4f matrix,
        float left,
        float top,
        float right,
        float bottom,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        buffer.addVertex(matrix, left, top, 0.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, right, top, 0.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, right, bottom, 0.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, left, bottom, 0.0F).setColor(red, green, blue, alpha);
    }

    private record RenderedLabel(
        Component nameText,
        Component titleText,
        int nameWidth,
        int titleWidth,
        double x,
        double y,
        double z,
        float labelScale,
        PlaquePalette palette
    ) {
    }

    private record PlaquePalette(
        int nameFill,
        int nameBorder,
        int titleFill,
        int titleBorder,
        int nameTextColor,
        int titleTextColor
    ) {
        private static final PlaquePalette DEFAULT = new PlaquePalette(
            0xE01B2428,
            0xE0AEB6B8,
            0xE0293237,
            0xD58D9A9D,
            0xFFE4F0F0,
            0xFF9FD3D3
        );
        private static final PlaquePalette GARD = new PlaquePalette(
            0xE0153533,
            0xE0D1A654,
            0xE0182927,
            0xD7B88937,
            0xFFFFE7A3,
            0xFFFFD37A
        );
        private static final PlaquePalette KAIJIN = new PlaquePalette(
            0xE03A251B,
            0xE0C48345,
            0xE0553423,
            0xD7B66C36,
            0xFFFFD28A,
            0xFFFFB56F
        );
        private static final PlaquePalette KUROBE = new PlaquePalette(
            0xE01E203A,
            0xE09A9FC4,
            0xE02A263F,
            0xD7D56E42,
            0xFFE8E1D6,
            0xFFFFAC6B
        );
        private static final PlaquePalette GOBTA = new PlaquePalette(
            0xE022341F,
            0xE0A8B967,
            0xE02D4726,
            0xD7C2A955,
            0xFFE6F0B5,
            0xFFB8D478
        );
        private static final PlaquePalette TESTAROSSA = new PlaquePalette(
            0xE0381828,
            0xE0D48A96,
            0xE04C1F31,
            0xD7C36576,
            0xFFFFD8E2,
            0xFFFF9DAC
        );
        private static final PlaquePalette TREYNI = new PlaquePalette(
            0xE01F4C43,
            0xE09BD6B5,
            0xE02D665B,
            0xD778B99A,
            0xFFE1F7DE,
            0xFFB8EAC5
        );
    }
}
