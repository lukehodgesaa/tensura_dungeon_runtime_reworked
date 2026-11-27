package com.mineclawd.tensurahudnative.client;

import com.mineclawd.dungeonsunbound.npc.DungeonNpc;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mineclawd.tensurahudnative.TensuraHudNative;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Locale;

public final class OverheadStatusRenderer {
    private static final ResourceLocation ALLY_CHROME = ResourceLocation.fromNamespaceAndPath(
            TensuraHudNative.MOD_ID, "textures/hud/overhead/ally_health_chrome.png");
    private static final ResourceLocation ENEMY_CHROME = ResourceLocation.fromNamespaceAndPath(
            TensuraHudNative.MOD_ID, "textures/hud/overhead/enemy_health_chrome.png");
    private static final ResourceLocation DUNGEON_DIMENSION = ResourceLocation.fromNamespaceAndPath(
            "tensura_dungeon", "dungeon");

    private static final double ALLY_RADIUS = 64.0D;
    private static final double ENEMY_RADIUS = 64.0D;
    private static final double ALLY_RADIUS_SQUARED = ALLY_RADIUS * ALLY_RADIUS;
    private static final double ENEMY_RADIUS_SQUARED = ENEMY_RADIUS * ENEMY_RADIUS;

    private static final float SOURCE_TO_DRAW_X = 240.0F / 512.0F;
    private static final float SOURCE_TO_DRAW_Y = 104.0F / 288.0F;
    private static final float FRAME_DRAW_WIDTH = 240.0F;
    private static final float FRAME_DRAW_HEIGHT = 104.0F;
    private static final float NAME_SCALE = 0.025F;
    private static final float BILLBOARD_SCALE = 0.010F;
    private static final double OVERHEAD_Y_OFFSET = 0.75D;
    private static final float WIDGET_TOP = 16.0F;

    private static final float ALLY_ALPHA_TOP = 249.0F * SOURCE_TO_DRAW_Y;
    private static final float ENEMY_ALPHA_TOP = 249.0F * SOURCE_TO_DRAW_Y;
    private static final float ALLY_HEALTH_LEFT = 116.0F * SOURCE_TO_DRAW_X - FRAME_DRAW_WIDTH / 2.0F;
    private static final float ALLY_HEALTH_RIGHT = 396.0F * SOURCE_TO_DRAW_X - FRAME_DRAW_WIDTH / 2.0F;
    private static final float ALLY_HEALTH_TOP = 258.0F * SOURCE_TO_DRAW_Y - ALLY_ALPHA_TOP + WIDGET_TOP;
    private static final float ALLY_HEALTH_HEIGHT = 8.0F * SOURCE_TO_DRAW_Y;
    private static final float ALLY_MAGICULE_LEFT = 120.0F * SOURCE_TO_DRAW_X - FRAME_DRAW_WIDTH / 2.0F;
    private static final float ALLY_MAGICULE_RIGHT = 392.0F * SOURCE_TO_DRAW_X - FRAME_DRAW_WIDTH / 2.0F;
    private static final float ALLY_MAGICULE_TOP = 268.0F * SOURCE_TO_DRAW_Y - ALLY_ALPHA_TOP + WIDGET_TOP;
    private static final float ALLY_MAGICULE_HEIGHT = 3.0F * SOURCE_TO_DRAW_Y;

    private static final float ENEMY_HEALTH_LEFT = 116.0F * SOURCE_TO_DRAW_X - FRAME_DRAW_WIDTH / 2.0F;
    private static final float ENEMY_HEALTH_RIGHT = 395.0F * SOURCE_TO_DRAW_X - FRAME_DRAW_WIDTH / 2.0F;
    private static final float ENEMY_HEALTH_TOP = 255.0F * SOURCE_TO_DRAW_Y - ENEMY_ALPHA_TOP + WIDGET_TOP;
    private static final float ENEMY_HEALTH_HEIGHT = 18.0F * SOURCE_TO_DRAW_Y;
    private static final float HEALTH_TEXT_RIGHT = 69.0F;
    private static final float HEALTH_TEXT_ABOVE_BAR = 14.0F;

    private static final int ALLY_HEALTH_COLOR = 0xFF63D63C;
    private static final int ALLY_HEALTH_DARK_COLOR = 0xFF2F8A2C;
    private static final int MAGICULE_COLOR = 0xFF35C9FF;
    private static final int MAGICULE_DARK_COLOR = 0xFF1676A5;
    private static final int ENEMY_HEALTH_COLOR = 0xFFE63C43;
    private static final int ENEMY_HEALTH_DARK_COLOR = 0xFF8C1F2E;
    private static final int NAME_COLOR = 0xFFFFFFFF;

    private static boolean loggedDungeonHook;
    private static boolean loggedLocalEntity;
    private static boolean loggedRemoteEntity;

    private OverheadStatusRenderer() {
    }

    static void render(RenderLevelStageEvent event, Minecraft minecraft, HudSettings settings) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || minecraft.level == null || !isDungeonDimension(minecraft.level)
                || minecraft.player == null) {
            return;
        }

        LocalPlayer localPlayer = minecraft.player;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float hudScale = settings == null ? 1.0F : settings.hudScale;
        boolean rendered = false;

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            TrackType type = trackType(living, localPlayer);
            if (type == TrackType.NONE || !withinRange(living, localPlayer, type)) {
                continue;
            }
            renderOne(poseStack, buffers, camera, minecraft.font, living, type,
                    cameraPosition, partialTick, hudScale);
            rendered = true;
        }

        if (rendered) {
            buffers.endBatch();
        }
    }

    static boolean shouldTrack(LivingEntity entity, Player localPlayer) {
        return entity != null && localPlayer != null && trackType(entity, localPlayer) != TrackType.NONE;
    }

    public static void renderFromEntityRenderer(Entity entity, float partialTick, PoseStack poseStack,
                                                MultiBufferSource buffers, Quaternionf cameraRotation, Font font) {
        Minecraft minecraft = Minecraft.getInstance();
        HudSettings settings = HudSettings.get();
        if (!loggedDungeonHook && entity instanceof LivingEntity
                && minecraft.level != null && isDungeonDimension(minecraft.level)) {
            loggedDungeonHook = true;
            TensuraHudNative.LOGGER.info(
                    "Overhead entity-render hook active in {} (HUD enabled={}, overhead enabled={}, GUI hidden={}, screen open={})",
                    minecraft.level.dimension().location(),
                    settings != null && settings.enabled,
                    settings != null && settings.showOverheadStatus,
                    minecraft.options != null && minecraft.options.hideGui,
                    minecraft.screen != null
            );
        }
        if (!canRender(minecraft, settings) || !(entity instanceof LivingEntity living)) {
            return;
        }
        TrackType type = trackType(living, minecraft.player);
        if (type == TrackType.NONE || !withinRange(living, minecraft.player, type)) {
            return;
        }

        boolean localEntity = living == minecraft.player;
        if ((localEntity && !loggedLocalEntity) || (!localEntity && !loggedRemoteEntity)) {
            if (localEntity) {
                loggedLocalEntity = true;
            } else {
                loggedRemoteEntity = true;
            }
            TensuraHudNative.LOGGER.info(
                    "Overhead status rendering matched {} ({}) as {} at {} blocks (allied={}, owned={})",
                    living.getDisplayName().getString(), living.getClass().getName(), type,
                    String.format(Locale.ROOT, "%.2f", Math.sqrt(living.distanceToSqr(minecraft.player))),
                    living.isAlliedTo(minecraft.player), isOwnedBy(living, minecraft.player)
            );
        }

        float hudScale = settings == null ? 1.0F : settings.hudScale;
        poseStack.pushPose();
        poseStack.translate(0.0D, living.getBbHeight() + OVERHEAD_Y_OFFSET, 0.0D);
        poseStack.mulPose(cameraRotation);
        renderWidget(poseStack, buffers, font, living, type, hudScale);
        poseStack.popPose();
    }

    private static boolean canRender(Minecraft minecraft, HudSettings settings) {
        return settings != null && settings.enabled && settings.showOverheadStatus
                && minecraft.player != null && minecraft.level != null
                && isDungeonDimension(minecraft.level)
                && minecraft.options != null && !minecraft.options.hideGui;
    }

    static boolean isDungeonDimension(ClientLevel level) {
        return level != null && DUNGEON_DIMENSION.equals(level.dimension().location());
    }

    static boolean isTeammate(LivingEntity entity, Player localPlayer) {
        return entity != null && localPlayer != null && trackType(entity, localPlayer) == TrackType.TEAMMATE;
    }

    static boolean isEnemy(LivingEntity entity, Player localPlayer) {
        return entity != null && localPlayer != null && trackType(entity, localPlayer) == TrackType.ENEMY;
    }

    private static void renderOne(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                  Camera camera, Font font, LivingEntity living, TrackType type,
                                  Vec3 cameraPosition, float partialTick, float hudScale) {
        Vec3 position = living.getPosition(partialTick);
        poseStack.pushPose();
        poseStack.translate(
                position.x - cameraPosition.x,
                position.y - cameraPosition.y + living.getBbHeight() + OVERHEAD_Y_OFFSET,
                position.z - cameraPosition.z
        );
        poseStack.mulPose(camera.rotation());

        renderName(poseStack, buffers, font, living.getDisplayName(), hudScale);
        renderWidget(poseStack, buffers, font, living, type, hudScale);
        poseStack.popPose();
    }

    private static void renderName(PoseStack poseStack, MultiBufferSource buffers,
                                   Font font, Component name, float hudScale) {
        poseStack.pushPose();
        poseStack.scale(NAME_SCALE * hudScale, -NAME_SCALE * hudScale, NAME_SCALE * hudScale);
        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(name) / 2.0F;
        font.drawInBatch(
                name,
                x,
                0.0F,
                NAME_COLOR,
                false,
                matrix,
                buffers,
                Font.DisplayMode.SEE_THROUGH,
                0,
                LightTexture.FULL_BRIGHT
        );
        poseStack.popPose();
    }

    private static void renderWidget(PoseStack poseStack, MultiBufferSource buffers,
                                     Font font, LivingEntity living, TrackType type, float hudScale) {
        poseStack.pushPose();
        poseStack.scale(BILLBOARD_SCALE * hudScale, -BILLBOARD_SCALE * hudScale,
                BILLBOARD_SCALE * hudScale);
        Matrix4f matrix = poseStack.last().pose();
        ResourceLocation chrome = type == TrackType.TEAMMATE ? ALLY_CHROME : ENEMY_CHROME;
        float alphaTop = type == TrackType.TEAMMATE ? ALLY_ALPHA_TOP : ENEMY_ALPHA_TOP;
        float frameTop = WIDGET_TOP - alphaTop;
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        renderTextureImmediate(matrix, chrome, -FRAME_DRAW_WIDTH / 2.0F, frameTop,
                FRAME_DRAW_WIDTH, FRAME_DRAW_HEIGHT);

        float health = percent(living.getHealth(), living.getMaxHealth());
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder fillBuilder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        if (type == TrackType.TEAMMATE) {
            drawProgress(fillBuilder, matrix, ALLY_HEALTH_LEFT, ALLY_HEALTH_TOP,
                    ALLY_HEALTH_RIGHT - ALLY_HEALTH_LEFT, ALLY_HEALTH_HEIGHT,
                    health, ALLY_HEALTH_DARK_COLOR, ALLY_HEALTH_COLOR);
            double magicule = TensuraDataProvider.magiculePercent(living);
            drawProgress(fillBuilder, matrix, ALLY_MAGICULE_LEFT, ALLY_MAGICULE_TOP,
                    ALLY_MAGICULE_RIGHT - ALLY_MAGICULE_LEFT, ALLY_MAGICULE_HEIGHT,
                    (float) magicule, MAGICULE_DARK_COLOR, MAGICULE_COLOR);
        } else {
            drawProgress(fillBuilder, matrix, ENEMY_HEALTH_LEFT, ENEMY_HEALTH_TOP,
                    ENEMY_HEALTH_RIGHT - ENEMY_HEALTH_LEFT, ENEMY_HEALTH_HEIGHT,
                    health, ENEMY_HEALTH_DARK_COLOR, ENEMY_HEALTH_COLOR);
        }
        BufferUploader.drawWithShader(fillBuilder.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();

        String healthText = "HP " + formatHealthValue(living.getHealth()) + " / "
                + formatHealthValue(living.getMaxHealth()) + " " + Math.round(health * 100.0F) + "%";
        float barTop = type == TrackType.TEAMMATE ? ALLY_HEALTH_TOP : ENEMY_HEALTH_TOP;
        float textY = barTop - HEALTH_TEXT_ABOVE_BAR;
        float textX = HEALTH_TEXT_RIGHT - font.width(healthText);
        font.drawInBatch(
                healthText,
                textX,
                textY,
                0xFFFFFFFF,
                true,
                matrix,
                buffers,
                Font.DisplayMode.SEE_THROUGH,
                0,
                LightTexture.FULL_BRIGHT
        );
        poseStack.popPose();
    }

    private static void renderTextureImmediate(Matrix4f matrix, ResourceLocation texture,
                                               float left, float top, float width, float height) {
        float right = left + width;
        float bottom = top + height;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        builder.addVertex(matrix, right, top, 0.0F)
                .setUv(1.0F, 0.0F).setColor(255, 255, 255, 255);
        builder.addVertex(matrix, left, top, 0.0F)
                .setUv(0.0F, 0.0F).setColor(255, 255, 255, 255);
        builder.addVertex(matrix, left, bottom, 0.0F)
                .setUv(0.0F, 1.0F).setColor(255, 255, 255, 255);
        builder.addVertex(matrix, right, bottom, 0.0F)
                .setUv(1.0F, 1.0F).setColor(255, 255, 255, 255);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void drawProgress(BufferBuilder builder, Matrix4f matrix,
                                     float left, float top, float width, float height,
                                     float percent, int darkColor, int brightColor) {
        int trackColor = withAlpha(darkColor, 215);
        drawRect(builder, matrix, left, top, left + width, top + height, 0.001F, trackColor);
        float fillWidth = width * Mth.clamp(percent, 0.0F, 1.0F);
        if (fillWidth <= 0.5F) {
            return;
        }
        drawRect(builder, matrix, left, top, left + fillWidth, top + height, 0.002F, brightColor);
        float highlightHeight = Math.min(2.0F, Math.max(0.0F, height - 1.0F));
        if (highlightHeight > 0.0F) {
            drawRect(builder, matrix, left + 2.0F, top + 1.0F,
                    Math.max(left + 2.0F, left + fillWidth - 2.0F), top + 1.0F + highlightHeight,
                    0.003F, withAlpha(0xFFFFFFFF, 110));
        }
    }

    private static void drawRect(BufferBuilder builder, Matrix4f matrix,
                                 float left, float top, float right, float bottom,
                                 float z, int argb) {
        builder.addVertex(matrix, right, top, z).setColor(argb);
        builder.addVertex(matrix, left, top, z).setColor(argb);
        builder.addVertex(matrix, left, bottom, z).setColor(argb);
        builder.addVertex(matrix, right, bottom, z).setColor(argb);
    }

    private static TrackType trackType(LivingEntity entity, Player localPlayer) {
        if (isCustomNpc(entity)
                || !entity.isAlive() || entity.isDeadOrDying()
                || entity.isInvisible() || entity.isInvisibleTo(localPlayer)
                || entity instanceof ArmorStand) {
            return TrackType.NONE;
        }
        if (entity instanceof Player other) {
            if (other.isSpectator()) {
                return TrackType.NONE;
            }
            return TrackType.TEAMMATE;
        }
        if (entity.isAlliedTo(localPlayer) || isOwnedBy(entity, localPlayer)) {
            return TrackType.TEAMMATE;
        }
        return TrackType.ENEMY;
    }

    private static boolean isCustomNpc(LivingEntity entity) {
        return entity instanceof DungeonNpc;
    }

    private static boolean isOwnedBy(LivingEntity entity, Player localPlayer) {
        return entity instanceof OwnableEntity ownable
                && ownable.getOwnerUUID() != null
                && ownable.getOwnerUUID().equals(localPlayer.getUUID());
    }

    private static boolean withinRange(LivingEntity entity, Player localPlayer, TrackType type) {
        double maxDistance = type == TrackType.TEAMMATE ? ALLY_RADIUS_SQUARED : ENEMY_RADIUS_SQUARED;
        return entity.distanceToSqr(localPlayer) <= maxDistance;
    }

    private static float percent(float current, float maximum) {
        if (!Float.isFinite(current) || !Float.isFinite(maximum) || maximum <= 0.0F) {
            return 0.0F;
        }
        return Mth.clamp(current / maximum, 0.0F, 1.0F);
    }

    private static String formatHealthValue(float value) {
        if (!Float.isFinite(value)) {
            return "0";
        }
        float magnitude = Math.abs(value);
        if (magnitude >= 1.0E9F) {
            return String.format(Locale.ROOT, "%.2fB", value / 1.0E9F);
        }
        if (magnitude >= 1.0E6F) {
            return String.format(Locale.ROOT, "%.2fM", value / 1.0E6F);
        }
        if (magnitude >= 1000.0F) {
            return String.format(Locale.ROOT, "%,.0f", value);
        }
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private enum TrackType {
        NONE,
        TEAMMATE,
        ENEMY
    }
}
