package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.OverworldRiftPayload;
import com.mineclawd.tensuradungeon.registry.ModSoundEvents;
import com.mineclawd.tensuradungeon.runtime.OverworldRiftGeometry;
import com.mineclawd.tensuradungeon.runtime.OverworldRiftGeometry.ScarNode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class OverworldRiftRenderer {
    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "minecraft", "textures/misc/white.png"
    );
    private static final ResourceLocation CRYSTAL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID, "textures/effect/heaven_scar_demon_crystal.png"
    );

    private static final RenderType STORM_RENDER_TYPE = RenderType.create(
        "tensura_dungeon_heaven_scar_storm",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        524_288,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(false)
    );
    private static final RenderType VOID_RENDER_TYPE = RenderType.create(
        "tensura_dungeon_heaven_scar_void",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        524_288,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
            .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(false)
    );
    private static final RenderType GLOW_RENDER_TYPE = texturedRenderType(
        "tensura_dungeon_heaven_scar_glow", WHITE_TEXTURE
    );
    private static final RenderType CRYSTAL_RENDER_TYPE = texturedRenderType(
        "tensura_dungeon_heaven_scar_crystal", CRYSTAL_TEXTURE
    );

    private static final float MAX_RENDER_DISTANCE = OverworldRiftGeometry.SONAR_MAX_RADIUS;
    private static final float DISTANCE_FADE_START = MAX_RENDER_DISTANCE - 256.0F;
    private static final float TRACE_DURATION_TICKS = 46.0F;
    private static final float CLOSE_DURATION_TICKS = 110.0F;
    private static final int SONAR_SEGMENTS = 128;
    private static final float SONAR_RIB_SPACING = 48.0F;
    private static final float PULL_AIR_MAX_RADIUS = (float) OverworldRiftGeometry.GRAVITY_PULL_RADIUS;
    private static final float PULL_AIR_MIN_RADIUS = 48.0F;
    private static final int PULL_AIR_HEIGHT_BANDS = 14;
    private static final int PULL_AIR_LINES_PER_BAND = 8;
    private static final int PULL_AIR_SEGMENTS = 14;
    private static final float PULL_AIR_LINE_WIDTH = 2.8F;
    private static final float PULL_AIR_LINE_HEIGHT = 9.0F;
    private static final float STORM_BOLT_LIFE_TICKS = 9.0F;
    private static final float STORM_BOLT_FLASH_TICKS = 4.0F;
    private static final int STORM_BOLT_SEGMENTS = 7;
    private static final float STORM_BOLT_MIN_RADIUS = 140.0F;
    private static final float STORM_BOLT_MAX_RADIUS = 640.0F;

    private static final List<ScarNode> MAIN_SCAR = OverworldRiftGeometry.MAIN_SCAR;

    private static final List<Branch> BRANCHES = List.of(
        branch(1, -1, 24.0F, 3.2F, point(0, 0), point(24, -12), point(55, -4), point(84, -20)),
        branch(2, 1, 31.0F, 3.8F, point(0, 0), point(28, 18), point(52, 13), point(79, 37), point(116, 44)),
        branch(3, -1, 39.0F, 3.0F, point(0, 0), point(31, 10), point(61, -5), point(91, 16)),
        branch(4, 1, 47.0F, 4.5F, point(0, 0), point(25, -13), point(57, -21), point(88, -4), point(124, -17)),
        branch(5, -1, 55.0F, 4.0F, point(0, 0), point(29, 24), point(59, 31), point(94, 19), point(131, 46)),
        branch(5, 1, 63.0F, 2.7F, point(0, 0), point(22, 11), point(48, 3), point(73, 20)),
        branch(6, -1, 70.0F, 3.4F, point(0, 0), point(35, -9), point(68, 8), point(103, -2)),
        branch(7, 1, 77.0F, 4.7F, point(0, 0), point(33, 21), point(69, 16), point(105, 42), point(142, 35)),
        branch(8, -1, 85.0F, 4.3F, point(0, 0), point(27, 17), point(62, 9), point(99, 34), point(137, 26)),
        branch(8, 1, 93.0F, 2.8F, point(0, 0), point(24, -16), point(50, -7), point(76, -24)),
        branch(9, -1, 101.0F, 3.6F, point(0, 0), point(32, -14), point(64, -2), point(98, -21)),
        branch(10, 1, 109.0F, 4.0F, point(0, 0), point(29, 22), point(62, 29), point(96, 18), point(129, 43)),
        branch(11, -1, 117.0F, 3.2F, point(0, 0), point(27, 11), point(56, 4), point(86, 24)),
        branch(12, 1, 125.0F, 2.5F, point(0, 0), point(22, -9), point(47, 5), point(75, -7))
    );

    private static final List<RiftShard> SHARDS = List.of(
        new RiftShard(1, -1, 20, -2, 15, 31, -18, 58, 0.2F),
        new RiftShard(2, 1, 27, 8, 12, 24, 24, 64, 1.1F),
        new RiftShard(3, -1, 39, -12, 19, 36, -31, 71, 2.0F),
        new RiftShard(4, 1, 24, 18, 14, 29, 17, 78, 2.8F),
        new RiftShard(4, -1, 58, 6, 11, 21, -46, 83, 3.4F),
        new RiftShard(5, 1, 48, -8, 20, 41, 32, 90, 4.1F),
        new RiftShard(6, -1, 33, 15, 16, 34, -22, 96, 4.8F),
        new RiftShard(6, 1, 66, 7, 10, 23, 48, 101, 5.5F),
        new RiftShard(7, -1, 52, -16, 22, 44, -35, 107, 6.2F),
        new RiftShard(8, 1, 31, 13, 13, 27, 21, 112, 6.9F),
        new RiftShard(8, -1, 75, 4, 17, 32, -53, 118, 7.6F),
        new RiftShard(9, 1, 57, -11, 23, 46, 38, 124, 8.3F),
        new RiftShard(10, -1, 35, 17, 14, 30, -27, 130, 9.0F),
        new RiftShard(10, 1, 82, 3, 11, 25, 57, 135, 9.7F),
        new RiftShard(11, -1, 54, -9, 19, 37, -41, 141, 10.4F),
        new RiftShard(12, 1, 29, 10, 12, 27, 29, 147, 11.1F)
    );

    private static volatile RiftView view = RiftView.inactive();
    private static RiftPullWindSound pullWindSound;
    private static final List<StormBolt> STORM_BOLTS = new ArrayList<>();
    private static long nextStormBoltAtTick;
    private static float stormFlashUntil;

    private OverworldRiftRenderer() {
    }

    public static void handle(OverworldRiftPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> view = payload.active()
            ? new RiftView(
                true,
                payload.anchor(),
                payload.orientationDegrees(),
                payload.startedAt(),
                payload.endsAt()
            )
            : RiftView.inactive());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        view = RiftView.inactive();
        stopPullWind();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        RiftView current = view;
        if (!current.active() || client.level == null || !Level.OVERWORLD.equals(client.level.dimension())) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float gameTime = client.level.getGameTime() + partialTick;
        if (gameTime > current.endsAt() + 40.0F) {
            view = RiftView.inactive();
            return;
        }

        double centerX = current.anchor().getX() + 0.5D;
        double centerY = resolveScarCenterY(current.anchor());
        double centerZ = current.anchor().getZ() + 0.5D;
        Vec3 camera = event.getCamera().getPosition();
        Vec3 scarCenter = new Vec3(centerX, centerY, centerZ);
        double horizontalX = camera.x - scarCenter.x;
        double horizontalZ = camera.z - scarCenter.z;
        double distance = Math.sqrt(horizontalX * horizontalX + horizontalZ * horizontalZ);
        if (distance >= MAX_RENDER_DISTANCE) {
            return;
        }

        float age = Math.max(0.0F, gameTime - current.startedAt());
        float appearing = smooth(remap(age, 0.0F, 12.0F));
        float closing = smooth(remap(current.endsAt() - gameTime, 0.0F, CLOSE_DURATION_TICKS));
        float distanceAlpha = 1.0F - smooth(remap((float) distance, DISTANCE_FADE_START, MAX_RENDER_DISTANCE));
        float lifeAlpha = Mth.clamp(appearing * closing * distanceAlpha, 0.0F, 1.0F);
        if (lifeAlpha < 0.01F) {
            return;
        }

        PlaneBasis skyBasis = worldBasis(current.orientationDegrees());
        PoseStack poseStack = event.getPoseStack();
        BufferBuilder stormBuilder = new BufferBuilder(
            new ByteBufferBuilder(262_144), VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR
        );
        BufferBuilder voidBuilder = new BufferBuilder(
            new ByteBufferBuilder(262_144), VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR
        );
        BufferBuilder glowBuilder = new BufferBuilder(
            new ByteBufferBuilder(524_288), VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY
        );
        BufferBuilder crystalBuilder = new BufferBuilder(
            new ByteBufferBuilder(524_288), VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY
        );

        poseStack.pushPose();
        poseStack.translate(centerX - camera.x, centerY - camera.y, centerZ - camera.z);
        float sonarBottom = (float) (client.level.getMinBuildHeight() - 48.0D - centerY);
        float sonarTop = (float) (Math.max(
            client.level.getMaxBuildHeight() + 128.0D,
            centerY + 192.0D
        ) - centerY);
        drawPressureWaves(
            stormBuilder, poseStack.last(), gameTime, age, closing, lifeAlpha, sonarBottom, sonarTop
        );
        drawPullAirLines(
            stormBuilder, poseStack.last(), skyBasis, gameTime, age, closing, lifeAlpha, sonarBottom, sonarTop
        );
        if (client.level.isThundering()) {
            updateStormBolts(gameTime, current);
            drawStormBolts(stormBuilder, poseStack.last(), skyBasis, gameTime, current);
        }
        drawHeavenScar(
            stormBuilder, voidBuilder, glowBuilder, crystalBuilder, poseStack.last(), skyBasis,
            gameTime, age, closing, lifeAlpha
        );
        poseStack.popPose();

        drawImmediate(STORM_RENDER_TYPE, stormBuilder);
        drawImmediate(VOID_RENDER_TYPE, voidBuilder);
        drawImmediate(GLOW_RENDER_TYPE, glowBuilder);
        drawImmediate(CRYSTAL_RENDER_TYPE, crystalBuilder);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        RiftView current = view;
        if (pullWindSound != null && pullWindSound.isStopped()) {
            pullWindSound = null;
        }
        if (!current.active() || client.level == null || client.player == null
            || !Level.OVERWORLD.equals(client.level.dimension())) {
            stopPullWind();
            return;
        }
        float gameTime = client.level.getGameTime();
        float age = gameTime - current.startedAt();
        if (!OverworldRiftGeometry.gravityPullActive((long) age)) {
            return;
        }
        if (pullWindSound == null) {
            pullWindSound = new RiftPullWindSound(current.anchor());
            client.getSoundManager().play(pullWindSound);
        }
    }

    private static void stopPullWind() {
        if (pullWindSound != null) {
            Minecraft.getInstance().getSoundManager().stop(pullWindSound);
            pullWindSound = null;
        }
    }

    @SubscribeEvent
    public static void renderPressureFlash(RenderGuiEvent.Pre event) {
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float strength = pressureStrength(Minecraft.getInstance(), partialTick);
        if (strength <= 0.001F) {
            return;
        }

        int width = event.getGuiGraphics().guiWidth();
        int height = event.getGuiGraphics().guiHeight();
        float crest = strength * strength;
        int blackAlpha = Math.round(92.0F * strength * (1.0F - 0.58F * crest));
        int whiteAlpha = Math.round(196.0F * crest);
        event.getGuiGraphics().fill(0, 0, width, height, blackAlpha << 24);
        event.getGuiGraphics().fill(0, 0, width, height, whiteAlpha << 24 | 0x00FFFFFF);

        int edgeAlpha = Math.round(224.0F * strength);
        int edgeX = Math.max(10, Math.round(width * (0.035F + 0.115F * strength)));
        int edgeY = Math.max(8, Math.round(height * (0.045F + 0.105F * strength)));
        int edgeColor = edgeAlpha << 24;
        event.getGuiGraphics().fill(0, 0, edgeX, height, edgeColor);
        event.getGuiGraphics().fill(width - edgeX, 0, width, height, edgeColor);
        event.getGuiGraphics().fill(0, 0, width, edgeY, edgeColor);
        event.getGuiGraphics().fill(0, height - edgeY, width, height, edgeColor);

        int compressionAlpha = Math.round(118.0F * crest);
        int compressionHeight = Math.max(1, Math.round(1.0F + 4.0F * strength));
        int centerY = height / 2;
        event.getGuiGraphics().fill(
            0, centerY - compressionHeight, width, centerY + compressionHeight,
            compressionAlpha << 24 | 0x00FFFFFF
        );
    }

    @SubscribeEvent
    public static void renderStormFlash(RenderGuiEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !Level.OVERWORLD.equals(client.level.dimension())
            || !client.level.isThundering()) {
            return;
        }
        float gameTime = client.level.getGameTime()
            + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float strength = (stormFlashUntil - gameTime) / STORM_BOLT_FLASH_TICKS;
        if (strength <= 0.0F) {
            return;
        }
        strength = Mth.clamp(strength, 0.0F, 1.0F);
        int width = event.getGuiGraphics().guiWidth();
        int height = event.getGuiGraphics().guiHeight();
        int redAlpha = Math.round(64.0F * strength * strength);
        event.getGuiGraphics().fill(0, 0, width, height, redAlpha << 24 | 0x00FF2020);
        int whiteAlpha = Math.round(90.0F * strength * strength * strength);
        event.getGuiGraphics().fill(0, 0, width, height, whiteAlpha << 24 | 0x00FFFFFF);
    }

    @SubscribeEvent
    public static void compressPressureFov(ViewportEvent.ComputeFov event) {
        float strength = pressureStrength(Minecraft.getInstance(), (float) event.getPartialTick());
        if (strength > 0.001F) {
            event.setFOV(event.getFOV() * (1.0D - 0.085D * strength));
        }
    }

    @SubscribeEvent
    public static void shakePressureCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft client = Minecraft.getInstance();
        float strength = pressureStrength(client, (float) event.getPartialTick());
        if (strength <= 0.001F || client.level == null) {
            return;
        }
        double time = client.level.getGameTime() + event.getPartialTick();
        event.setYaw(event.getYaw() + (float) Math.sin(time * 1.73D) * 0.34F * strength);
        event.setPitch(event.getPitch() + (float) Math.cos(time * 2.21D) * 0.46F * strength);
        event.setRoll(event.getRoll() + (float) Math.sin(time * 1.37D + 0.8D) * 1.15F * strength);
    }

    private static float pressureStrength(Minecraft client, float partialTick) {
        RiftView current = view;
        if (!current.active() || client.level == null || client.player == null
            || !Level.OVERWORLD.equals(client.level.dimension())) {
            return 0.0F;
        }
        float gameTime = client.level.getGameTime() + partialTick;
        float age = gameTime - current.startedAt();
        double x = client.player.xo + (client.player.getX() - client.player.xo) * partialTick;
        double z = client.player.zo + (client.player.getZ() - client.player.zo) * partialTick;
        return OverworldRiftGeometry.sonarImpactStrength(
            current.anchor(), new Vec3(x, client.player.getY(), z), age
        );
    }

    private static RenderType texturedRenderType(String name, ResourceLocation texture) {
        return RenderType.create(
            name,
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1_048_576,
            false,
            true,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .createCompositeState(false)
        );
    }

    private static void drawImmediate(RenderType renderType, BufferBuilder builder) {
        MeshData mesh = builder.build();
        if (mesh != null) {
            renderType.draw(mesh);
        }
    }

    private static double resolveScarCenterY(BlockPos anchor) {
        return OverworldRiftGeometry.centerY(anchor);
    }

    private static PlaneBasis worldBasis(float orientationDegrees) {
        OverworldRiftGeometry.Basis basis = OverworldRiftGeometry.basis(orientationDegrees);
        return new PlaneBasis(
            new Vec3(basis.rightX(), 0.0D, basis.rightZ()),
            new Vec3(0.0D, 1.0D, 0.0D),
            new Vec3(basis.normalX(), 0.0D, basis.normalZ())
        );
    }

    private static void drawHeavenScar(
        VertexConsumer storm,
        VertexConsumer voidConsumer,
        VertexConsumer glow,
        VertexConsumer crystal,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float gameTime,
        float age,
        float closing,
        float alpha
    ) {
        float traceProgress = smooth(remap(age, 0.0F, TRACE_DURATION_TICKS));
        float pulse = 0.84F + 0.16F * Mth.sin(gameTime * 0.095F);
        drawStormBruise(storm, pose, basis, traceProgress, age, closing, alpha);
        drawMainTear(voidConsumer, glow, crystal, pose, basis, gameTime, age, closing, alpha, traceProgress, pulse);
        drawBranches(storm, voidConsumer, glow, crystal, pose, basis, gameTime, age, closing, alpha, pulse);
        drawShards(glow, crystal, pose, basis, gameTime, age, closing, alpha, pulse);
    }

    private static void drawPressureWaves(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float gameTime,
        float age,
        float closing,
        float alpha,
        float bottom,
        float top
    ) {
        for (int waveIndex = 0; waveIndex < OverworldRiftGeometry.SONAR_WAVE_COUNT; waveIndex++) {
            if (!OverworldRiftGeometry.sonarActive(age, waveIndex)) {
                continue;
            }
            float radius = OverworldRiftGeometry.sonarRadius(age, waveIndex);
            if (radius < 2.0F) {
                continue;
            }
            float envelope = OverworldRiftGeometry.sonarEnvelope(age, waveIndex) * closing * alpha;
            float pulse = 0.90F + 0.10F * Mth.sin(gameTime * 0.17F + waveIndex * 1.9F);
            float waveAlpha = envelope * pulse;

            drawCylinder(consumer, pose, radius - 34.0F, bottom, top,
                0, 0, 0, Math.round(58.0F * waveAlpha));
            drawCylinder(consumer, pose, radius - 18.0F, bottom, top,
                148, 148, 148, Math.round(42.0F * waveAlpha));
            drawCylinder(consumer, pose, radius - 7.0F, bottom, top,
                0, 0, 0, Math.round(116.0F * waveAlpha));
            drawCylinder(consumer, pose, radius, bottom, top,
                245, 245, 245, Math.round(146.0F * waveAlpha));
            drawCylinder(consumer, pose, radius + 11.0F, bottom, top,
                2, 2, 2, Math.round(104.0F * waveAlpha));
            drawCylinder(consumer, pose, radius + 27.0F, bottom, top,
                188, 188, 188, Math.round(38.0F * waveAlpha));

            float ribOffset = (gameTime * 0.35F + waveIndex * 17.0F) % SONAR_RIB_SPACING;
            for (float ribY = bottom + ribOffset; ribY < top; ribY += SONAR_RIB_SPACING) {
                float ripple = Mth.sin(ribY * 0.052F + gameTime * 0.08F + waveIndex) * 7.0F;
                drawCylinder(consumer, pose, radius + ripple, ribY, Math.min(top, ribY + 2.25F),
                    255, 255, 255, Math.round(94.0F * waveAlpha));
            }
        }
    }

    private static void drawPullAirLines(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float gameTime,
        float age,
        float closing,
        float alpha,
        float bottom,
        float top
    ) {
        if (!OverworldRiftGeometry.gravityPullActive((long) age)) {
            return;
        }
        float pullProgress = OverworldRiftGeometry.gravityPullProgress((long) age);
        float pullAlpha = smooth(remap(pullProgress, 0.0F, 0.12F))
            * (1.0F - smooth(remap(pullProgress, 0.86F, 1.0F)))
            * closing
            * alpha;
        if (pullAlpha < 0.01F) {
            return;
        }
        float heightSpan = Math.max(1.0F, top - bottom);
        for (int band = 0; band < PULL_AIR_HEIGHT_BANDS; band++) {
            float bandFraction = (band + 0.5F) / PULL_AIR_HEIGHT_BANDS;
            float baseV = bottom + bandFraction * heightSpan;
            for (int line = 0; line < PULL_AIR_LINES_PER_BAND; line++) {
                float angle = Mth.TWO_PI * (line + band * 0.37F) / PULL_AIR_LINES_PER_BAND
                    + gameTime * 0.006F;
                float phase = line * 1.73F + band * 2.31F;
                drawPullAirLine(consumer, pose, basis, angle, baseV, phase, gameTime, pullAlpha);
            }
        }
    }

    private static void drawPullAirLine(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float angle,
        float baseV,
        float phase,
        float gameTime,
        float alpha
    ) {
        float radialU = Mth.cos(angle);
        float radialD = Mth.sin(angle);
        float tangentU = -Mth.sin(angle);
        float tangentD = Mth.cos(angle);
        float halfWidth = PULL_AIR_LINE_WIDTH * 0.5F;
        float halfHeight = PULL_AIR_LINE_HEIGHT * 0.5F;

        float previousU = 0.0F;
        float previousV = 0.0F;
        float previousD = 0.0F;
        for (int segment = 0; segment <= PULL_AIR_SEGMENTS; segment++) {
            float progress = segment / (float) PULL_AIR_SEGMENTS;
            float radius = Mth.lerp(progress * progress, PULL_AIR_MIN_RADIUS, PULL_AIR_MAX_RADIUS);
            float radialFalloff = 0.35F + 0.65F * (radius / PULL_AIR_MAX_RADIUS);
            float wiggle = (Mth.sin(radius * 0.018F + gameTime * 0.30F + phase)
                + Mth.sin(radius * 0.043F + gameTime * 0.58F + phase * 1.7F) * 0.45F)
                * 4.5F * radialFalloff;
            float u = radialU * radius;
            float d = radialD * radius;
            float v = baseV + wiggle;
            if (segment > 0) {
                float segmentAlpha = alpha * (0.55F + 0.45F * progress);
                drawBasisQuad(
                    consumer, pose, basis,
                    previousU - tangentU * halfWidth, previousV - halfHeight,
                    previousD - tangentD * halfWidth,
                    previousU + tangentU * halfWidth, previousV - halfHeight,
                    previousD + tangentD * halfWidth,
                    u + tangentU * halfWidth, v + halfHeight, d + tangentD * halfWidth,
                    u - tangentU * halfWidth, v + halfHeight, d - tangentD * halfWidth,
                    235, 240, 255, Math.round(14.0F * segmentAlpha)
                );
            }
            previousU = u;
            previousV = v;
            previousD = d;
        }
    }

    private static void drawBasisQuad(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float u0,
        float v0,
        float d0,
        float u1,
        float v1,
        float d1,
        float u2,
        float v2,
        float d2,
        float u3,
        float v3,
        float d3,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        if (alpha <= 0) {
            return;
        }
        colorVertex(consumer, pose, basis, u0, v0, d0, red, green, blue, alpha);
        colorVertex(consumer, pose, basis, u1, v1, d1, red, green, blue, alpha);
        colorVertex(consumer, pose, basis, u2, v2, d2, red, green, blue, alpha);
        colorVertex(consumer, pose, basis, u3, v3, d3, red, green, blue, alpha);
    }

    private static void updateStormBolts(float gameTime, RiftView current) {
        STORM_BOLTS.removeIf(bolt -> gameTime - bolt.spawnTick() >= STORM_BOLT_LIFE_TICKS);
        if (gameTime < nextStormBoltAtTick) {
            return;
        }
        RandomSource random = RandomSource.create(
            (long) (gameTime * 31.7F) + current.anchor().asLong()
        );
        float angle = random.nextFloat() * Mth.TWO_PI;
        float radius = STORM_BOLT_MIN_RADIUS
            + random.nextFloat() * (STORM_BOLT_MAX_RADIUS - STORM_BOLT_MIN_RADIUS);
        STORM_BOLTS.add(new StormBolt(angle, radius, random.nextFloat(), (long) gameTime));
        if (STORM_BOLTS.size() > 8) {
            STORM_BOLTS.remove(0);
        }
        float age = gameTime - current.startedAt();
        int minInterval;
        int maxInterval;
        if (age >= OverworldRiftGeometry.GRAVITY_PULL_START_TICKS) {
            minInterval = 5;
            maxInterval = 14;
        } else if (age >= OverworldRiftGeometry.sonarStartTick(0)
            + OverworldRiftGeometry.SONAR_DURATION_TICKS) {
            minInterval = 12;
            maxInterval = 26;
        } else {
            minInterval = 25;
            maxInterval = 55;
        }
        if (age >= OverworldRiftGeometry.GRAVITY_PULL_START_TICKS
            + OverworldRiftGeometry.GRAVITY_PULL_DURATION_TICKS) {
            minInterval = 25;
            maxInterval = 55;
        }
        nextStormBoltAtTick = (long) gameTime
            + minInterval + random.nextInt(maxInterval - minInterval + 1);
        stormFlashUntil = gameTime + STORM_BOLT_FLASH_TICKS;
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forLocalAmbience(
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                1.2F,
                0.85F + random.nextFloat() * 0.3F
            )
        );
    }

    private static void drawStormBolts(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float gameTime,
        RiftView current
    ) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || STORM_BOLTS.isEmpty()) {
            return;
        }
        double centerY = resolveScarCenterY(current.anchor());
        float topY = client.level.getMaxBuildHeight() + 48.0F;
        float groundY = current.anchor().getY();
        for (StormBolt bolt : STORM_BOLTS) {
            float fade = 1.0F - (gameTime - bolt.spawnTick()) / STORM_BOLT_LIFE_TICKS;
            if (fade <= 0.0F) {
                continue;
            }
            float cos = Mth.cos(bolt.angle());
            float sin = Mth.sin(bolt.angle());
            float radius = bolt.radius();
            float baseU = (float) (cos * radius * basis.right().x + sin * radius * basis.right().z);
            float baseD = (float) (cos * radius * basis.forward().x + sin * radius * basis.forward().z);
            float perpU = (float) (-sin * basis.right().x + cos * basis.right().z);
            float perpD = (float) (-sin * basis.forward().x + cos * basis.forward().z);

            float previousU = 0.0F;
            float previousV = 0.0F;
            float previousD = 0.0F;
            for (int segment = 0; segment <= STORM_BOLT_SEGMENTS; segment++) {
                float progress = segment / (float) STORM_BOLT_SEGMENTS;
                float wobble = (Mth.sin(progress * 6.283F + bolt.seed() * 21.0F) * 5.0F
                    + Mth.sin(progress * 13.7F + bolt.seed() * 7.0F) * 2.5F)
                    * (0.35F + progress);
                float u = baseU + perpU * wobble;
                float d = baseD + perpD * wobble;
                float v = Mth.lerp(progress, topY - (float) centerY, groundY - (float) centerY);
                if (segment > 0) {
                    drawBasisQuad(consumer, pose, basis,
                        previousU - perpU * 2.6F, previousV, previousD - perpD * 2.6F,
                        previousU + perpU * 2.6F, previousV, previousD + perpD * 2.6F,
                        u + perpU * 2.6F, v, d + perpD * 2.6F,
                        u - perpU * 2.6F, v, d - perpD * 2.6F,
                        200, 25, 30, Math.round(45.0F * fade));
                    drawBasisQuad(consumer, pose, basis,
                        previousU - perpU * 1.1F, previousV, previousD - perpD * 1.1F,
                        previousU + perpU * 1.1F, previousV, previousD + perpD * 1.1F,
                        u + perpU * 1.1F, v, d + perpD * 1.1F,
                        u - perpU * 1.1F, v, d - perpD * 1.1F,
                        255, 90, 55, Math.round(220.0F * fade));
                }
                previousU = u;
                previousV = v;
                previousD = d;
            }
        }
    }

    private static void drawCylinder(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float radius,
        float bottom,
        float top,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        if (radius <= 0.0F || top <= bottom || alpha <= 0) {
            return;
        }
        int clampedAlpha = Mth.clamp(alpha, 0, 255);
        for (int segment = 0; segment < SONAR_SEGMENTS; segment++) {
            float firstAngle = Mth.TWO_PI * segment / SONAR_SEGMENTS;
            float secondAngle = Mth.TWO_PI * (segment + 1) / SONAR_SEGMENTS;
            float x0 = Mth.cos(firstAngle) * radius;
            float z0 = Mth.sin(firstAngle) * radius;
            float x1 = Mth.cos(secondAngle) * radius;
            float z1 = Mth.sin(secondAngle) * radius;
            consumer.addVertex(pose, x0, bottom, z0).setColor(red, green, blue, clampedAlpha);
            consumer.addVertex(pose, x1, bottom, z1).setColor(red, green, blue, clampedAlpha);
            consumer.addVertex(pose, x1, top, z1).setColor(red, green, blue, clampedAlpha);
            consumer.addVertex(pose, x0, top, z0).setColor(red, green, blue, clampedAlpha);
        }
    }

    private static void drawStormBruise(
        VertexConsumer storm,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float traceProgress,
        float age,
        float closing,
        float alpha
    ) {
        float stormOpen = smooth(remap(age, 8.0F, 94.0F)) * closing;
        int segmentCount = MAIN_SCAR.size() - 1;
        for (int index = 0; index < segmentCount; index++) {
            float reveal = segmentReveal(traceProgress, index, segmentCount);
            if (reveal <= 0.001F) {
                continue;
            }
            ScarNode start = MAIN_SCAR.get(index);
            ScarNode finish = partialNode(start, MAIN_SCAR.get(index + 1), reveal);
            float innerWidth = Mth.lerp(stormOpen, 22.0F, 76.0F) + (start.halfWidth() + finish.halfWidth());
            drawColorRibbon(storm, pose, basis, start.u(), start.v(), finish.u(), finish.v(),
                innerWidth, 0.0F, 34, 0, 5, Math.round(48.0F * alpha * stormOpen));
            drawColorRibbon(storm, pose, basis, start.u(), start.v(), finish.u(), finish.v(),
                innerWidth + 88.0F, -0.05F, 10, 0, 3, Math.round(24.0F * alpha * stormOpen));
        }
    }

    private static void drawMainTear(
        VertexConsumer voidConsumer,
        VertexConsumer glow,
        VertexConsumer crystal,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float gameTime,
        float age,
        float closing,
        float alpha,
        float traceProgress,
        float pulse
    ) {
        int segmentCount = MAIN_SCAR.size() - 1;
        float strike = 1.0F - smooth(remap(age, 48.0F, 116.0F));
        for (int index = 0; index < segmentCount; index++) {
            float reveal = segmentReveal(traceProgress, index, segmentCount);
            if (reveal <= 0.001F) {
                continue;
            }

            ScarNode start = MAIN_SCAR.get(index);
            ScarNode finish = partialNode(start, MAIN_SCAR.get(index + 1), reveal);
            float startScale = tearScale(index, age, closing);
            float finishScale = Mth.lerp(reveal, startScale, tearScale(index + 1, age, closing));
            float startWidth = Math.max(0.08F, start.halfWidth() * startScale);
            float finishWidth = Math.max(0.08F, finish.halfWidth() * finishScale);

            drawColorQuad(voidConsumer, pose, basis,
                start.u() - startWidth, start.v(),
                start.u() + startWidth, start.v(),
                finish.u() + finishWidth, finish.v(),
                finish.u() - finishWidth, finish.v(),
                0.0F, 1, 0, 0, 255);

            float textureOffset = gameTime * 0.0028F + index * 0.173F;
            drawScarEdge(glow, crystal, pose, basis,
                start.u() - startWidth, start.v(), finish.u() - finishWidth, finish.v(),
                -1, textureOffset, alpha, pulse, index);
            drawScarEdge(glow, crystal, pose, basis,
                start.u() + startWidth, start.v(), finish.u() + finishWidth, finish.v(),
                1, -textureOffset, alpha, pulse, index + 1);

            if (strike > 0.005F) {
                int strikeAlpha = Math.round(230.0F * alpha * strike);
                drawTexturedRibbon(glow, pose, basis,
                    start.u(), start.v(), finish.u(), finish.v(),
                    13.0F * strike + 2.0F, 0.18F,
                    textureOffset, 190, 14, 24, Math.round(strikeAlpha * 0.50F));
                drawTexturedRibbon(glow, pose, basis,
                    start.u(), start.v(), finish.u(), finish.v(),
                    1.8F, 0.24F,
                    -textureOffset, 255, 92, 52, strikeAlpha);
            }
        }
    }

    private static void drawScarEdge(
        VertexConsumer glow,
        VertexConsumer crystal,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float u0,
        float v0,
        float u1,
        float v1,
        int side,
        float textureOffset,
        float alpha,
        float pulse,
        int colorPhase
    ) {
        boolean ember = colorPhase % 3 == 0;
        int red = ember ? 184 : 122;
        int green = ember ? 14 : 3;
        int blue = ember ? 24 : 10;
        drawTexturedRibbon(glow, pose, basis, u0, v0, u1, v1,
            25.0F, 0.08F, textureOffset, red, green, blue, Math.round(82.0F * alpha * pulse));
        drawTexturedRibbon(crystal, pose, basis, u0, v0, u1, v1,
            14.0F, 0.14F, textureOffset * side, 210, 110, 105, Math.round(226.0F * alpha));
        drawTexturedRibbon(glow, pose, basis, u0, v0, u1, v1,
            4.0F, 0.23F, -textureOffset, 224, 22, 28, Math.round(220.0F * alpha));
        drawTexturedRibbon(glow, pose, basis, u0, v0, u1, v1,
            1.25F, 0.29F, textureOffset, 255, 74, 38, Math.round(250.0F * alpha));
    }

    private static void drawBranches(
        VertexConsumer storm,
        VertexConsumer voidConsumer,
        VertexConsumer glow,
        VertexConsumer crystal,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float gameTime,
        float age,
        float closing,
        float alpha,
        float pulse
    ) {
        for (int branchIndex = 0; branchIndex < BRANCHES.size(); branchIndex++) {
            Branch branch = BRANCHES.get(branchIndex);
            float branchReveal = smooth(remap(age, branch.delay(), branch.delay() + 52.0F));
            if (branchReveal <= 0.001F) {
                continue;
            }
            ScarNode root = MAIN_SCAR.get(branch.rootNode());
            float rootScale = tearScale(branch.rootNode(), age, closing);
            float rootU = root.u() + branch.side() * root.halfWidth() * rootScale;
            float branchPhase = branchIndex * 1.73F;
            float stretch = 0.90F + 0.18F * Mth.sin(gameTime * 0.045F + branchPhase * 1.3F);
            float flicker = 0.78F + 0.22F * Mth.sin(gameTime * 0.19F + branchPhase * 2.1F);
            float wobbleStrength = 2.2F + 1.6F * Mth.sin(gameTime * 0.07F + branchPhase * 0.8F);
            float branchWidth = Mth.lerp(branchReveal, 0.35F, branch.maxWidth()) * closing
                * (0.90F + 0.10F * flicker);
            int segmentCount = branch.points().size() - 1;

            for (int segment = 0; segment < segmentCount; segment++) {
                float reveal = segmentReveal(branchReveal, segment, segmentCount);
                if (reveal <= 0.001F) {
                    continue;
                }
                BranchPoint first = branch.points().get(segment);
                BranchPoint target = branch.points().get(segment + 1);
                BranchPoint finish = new BranchPoint(
                    Mth.lerp(reveal, first.outward(), target.outward()),
                    Mth.lerp(reveal, first.rise(), target.rise())
                );
                float wobbleStart = Mth.sin(gameTime * 0.13F + branchPhase + segment * 0.9F)
                    * wobbleStrength;
                float wobbleFinish = Mth.sin(
                    gameTime * 0.13F + branchPhase + (segment + 1) * 0.9F + 1.3F
                ) * wobbleStrength;
                float riseStart = Mth.sin(gameTime * 0.11F + branchPhase * 1.4F + segment * 0.8F)
                    * 2.5F;
                float riseFinish = Mth.sin(
                    gameTime * 0.11F + branchPhase * 1.4F + (segment + 1) * 0.8F + 1.7F
                ) * 2.5F;
                float u0 = rootU + branch.side() * (first.outward() * stretch + wobbleStart);
                float v0 = root.v() + first.rise() * stretch + riseStart;
                float u1 = rootU + branch.side() * (finish.outward() * stretch + wobbleFinish);
                float v1 = root.v() + finish.rise() * stretch + riseFinish;

                drawColorRibbon(storm, pose, basis, u0, v0, u1, v1,
                    branchWidth * 6.0F + 12.0F, -0.03F,
                    28, 0, 5, Math.round(26.0F * alpha * branchReveal * (0.75F + 0.25F * flicker)));
                drawColorRibbon(voidConsumer, pose, basis, u0, v0, u1, v1,
                    branchWidth, 0.0F, 1, 0, 0, 255);

                LocalLine leftEdge = offsetLine(u0, v0, u1, v1, -branchWidth * 0.5F);
                LocalLine rightEdge = offsetLine(u0, v0, u1, v1, branchWidth * 0.5F);
                float textureOffset = gameTime * 0.012F + branchIndex * 0.129F + segment * 0.21F;
                int red = branchIndex % 3 == 0 ? 178 : 120;
                int green = branchIndex % 3 == 0 ? 12 : 3;
                int blue = branchIndex % 3 == 0 ? 24 : 9;
                drawBranchEdge(glow, crystal, pose, basis, leftEdge, textureOffset,
                    red, green, blue, alpha * flicker, pulse);
                drawBranchEdge(glow, crystal, pose, basis, rightEdge, -textureOffset,
                    red, green, blue, alpha * flicker, pulse);
            }
        }
    }

    private static void drawBranchEdge(
        VertexConsumer glow,
        VertexConsumer crystal,
        PoseStack.Pose pose,
        PlaneBasis basis,
        LocalLine line,
        float textureOffset,
        int red,
        int green,
        int blue,
        float alpha,
        float pulse
    ) {
        drawTexturedRibbon(glow, pose, basis, line.u0(), line.v0(), line.u1(), line.v1(),
            7.5F, 0.08F, textureOffset, red, green, blue, Math.round(70.0F * alpha * pulse));
        drawTexturedRibbon(crystal, pose, basis, line.u0(), line.v0(), line.u1(), line.v1(),
            3.1F, 0.15F, textureOffset, 205, 96, 92, Math.round(210.0F * alpha));
        drawTexturedRibbon(glow, pose, basis, line.u0(), line.v0(), line.u1(), line.v1(),
            0.85F, 0.22F, -textureOffset, 255, 60, 32, Math.round(238.0F * alpha));
    }

    private static void drawShards(
        VertexConsumer glow,
        VertexConsumer crystal,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float gameTime,
        float age,
        float closing,
        float alpha,
        float pulse
    ) {
        for (int index = 0; index < SHARDS.size(); index++) {
            RiftShard shard = SHARDS.get(index);
            float reveal = smooth(remap(age, shard.delay(), shard.delay() + 48.0F)) * closing;
            if (reveal <= 0.001F) {
                continue;
            }
            ScarNode root = MAIN_SCAR.get(shard.rootNode());
            float drift = Mth.sin(gameTime * 0.026F + shard.phase()) * (2.0F + index % 3);
            float bob = Mth.sin(gameTime * 0.019F + shard.phase() * 1.7F) * 3.0F;
            float centerU = root.u() + shard.side() * (root.halfWidth() + shard.outward() * reveal + drift);
            float centerV = root.v() + shard.rise() + bob;
            float rotation = shard.tiltDegrees() + Mth.sin(gameTime * 0.011F + shard.phase()) * 7.0F;
            float shardAlpha = alpha * reveal;

            drawRotatedTexturedQuad(glow, pose, basis, centerU, centerV,
                shard.width() * 1.45F, shard.height() * 1.35F, rotation, 0.04F,
                gameTime * 0.0015F + index * 0.17F,
                index % 2 == 0 ? 145 : 190,
                index % 2 == 0 ? 5 : 14,
                index % 2 == 0 ? 10 : 26,
                Math.round(60.0F * shardAlpha * pulse));
            drawRotatedTexturedQuad(crystal, pose, basis, centerU, centerV,
                shard.width(), shard.height(), rotation, 0.12F,
                -gameTime * 0.002F + index * 0.13F,
                205, 100, 96, Math.round(205.0F * shardAlpha));
            drawRotatedTexturedQuad(glow, pose, basis, centerU, centerV,
                Math.max(1.2F, shard.width() * 0.12F), shard.height() * 0.9F, rotation, 0.21F,
                gameTime * 0.003F, 255, 58, 30, Math.round(180.0F * shardAlpha));
        }
    }

    private static float tearScale(int nodeIndex, float age, float closing) {
        float tear = smooth(remap(age, 16.0F + nodeIndex * 5.0F, 66.0F + nodeIndex * 6.0F));
        return (0.03F + 0.97F * tear) * closing;
    }

    private static float segmentReveal(float progress, int segmentIndex, int segmentCount) {
        float start = segmentIndex / (float) segmentCount;
        float end = (segmentIndex + 1.0F) / segmentCount;
        return smooth(remap(progress, start, end));
    }

    private static ScarNode partialNode(ScarNode start, ScarNode target, float progress) {
        return new ScarNode(
            Mth.lerp(progress, start.u(), target.u()),
            Mth.lerp(progress, start.v(), target.v()),
            Mth.lerp(progress, start.halfWidth(), target.halfWidth())
        );
    }

    private static LocalLine offsetLine(float u0, float v0, float u1, float v1, float offset) {
        float deltaU = u1 - u0;
        float deltaV = v1 - v0;
        float length = Mth.sqrt(deltaU * deltaU + deltaV * deltaV);
        if (length < 0.0001F) {
            return new LocalLine(u0, v0, u1, v1);
        }
        float normalU = -deltaV / length;
        float normalV = deltaU / length;
        return new LocalLine(
            u0 + normalU * offset,
            v0 + normalV * offset,
            u1 + normalU * offset,
            v1 + normalV * offset
        );
    }

    private static void drawColorRibbon(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float u0,
        float v0,
        float u1,
        float v1,
        float width,
        float depth,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        if (alpha <= 0 || width <= 0.0F) {
            return;
        }
        Ribbon ribbon = ribbon(u0, v0, u1, v1, width);
        drawColorQuad(consumer, pose, basis,
            ribbon.u0(), ribbon.v0(), ribbon.u1(), ribbon.v1(),
            ribbon.u2(), ribbon.v2(), ribbon.u3(), ribbon.v3(),
            depth, red, green, blue, alpha);
    }

    private static void drawTexturedRibbon(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float u0,
        float v0,
        float u1,
        float v1,
        float width,
        float depth,
        float textureOffset,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        if (alpha <= 0 || width <= 0.0F) {
            return;
        }
        Ribbon ribbon = ribbon(u0, v0, u1, v1, width);
        float length = Mth.sqrt((u1 - u0) * (u1 - u0) + (v1 - v0) * (v1 - v0));
        float textureLength = Math.max(0.2F, length / 42.0F);
        drawTexturedQuad(consumer, pose, basis,
            ribbon.u0(), ribbon.v0(), ribbon.u1(), ribbon.v1(),
            ribbon.u2(), ribbon.v2(), ribbon.u3(), ribbon.v3(),
            depth,
            textureOffset, 0.0F, textureOffset + textureLength, 1.0F,
            red, green, blue, alpha);
    }

    private static Ribbon ribbon(float u0, float v0, float u1, float v1, float width) {
        float deltaU = u1 - u0;
        float deltaV = v1 - v0;
        float length = Mth.sqrt(deltaU * deltaU + deltaV * deltaV);
        float normalU = length < 0.0001F ? width * 0.5F : -deltaV / length * width * 0.5F;
        float normalV = length < 0.0001F ? 0.0F : deltaU / length * width * 0.5F;
        return new Ribbon(
            u0 + normalU, v0 + normalV,
            u0 - normalU, v0 - normalV,
            u1 - normalU, v1 - normalV,
            u1 + normalU, v1 + normalV
        );
    }

    private static void drawRotatedTexturedQuad(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float centerU,
        float centerV,
        float width,
        float height,
        float degrees,
        float depth,
        float textureOffset,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        if (alpha <= 0 || width <= 0.0F || height <= 0.0F) {
            return;
        }
        float radians = degrees * Mth.DEG_TO_RAD;
        float rightU = Mth.cos(radians) * width * 0.5F;
        float rightV = Mth.sin(radians) * width * 0.5F;
        float upU = -Mth.sin(radians) * height * 0.5F;
        float upV = Mth.cos(radians) * height * 0.5F;
        drawTexturedQuad(consumer, pose, basis,
            centerU - rightU - upU, centerV - rightV - upV,
            centerU + rightU - upU, centerV + rightV - upV,
            centerU + rightU + upU, centerV + rightV + upV,
            centerU - rightU + upU, centerV - rightV + upV,
            depth,
            textureOffset, 0.0F, textureOffset + 1.0F, 1.0F,
            red, green, blue, alpha);
    }

    private static void drawColorQuad(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float u0,
        float v0,
        float u1,
        float v1,
        float u2,
        float v2,
        float u3,
        float v3,
        float depth,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        colorVertex(consumer, pose, basis, u0, v0, depth, red, green, blue, alpha);
        colorVertex(consumer, pose, basis, u1, v1, depth, red, green, blue, alpha);
        colorVertex(consumer, pose, basis, u2, v2, depth, red, green, blue, alpha);
        colorVertex(consumer, pose, basis, u3, v3, depth, red, green, blue, alpha);
    }

    private static void drawTexturedQuad(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float localU0,
        float localV0,
        float localU1,
        float localV1,
        float localU2,
        float localV2,
        float localU3,
        float localV3,
        float depth,
        float textureU0,
        float textureV0,
        float textureU1,
        float textureV1,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        texturedVertex(consumer, pose, basis, localU0, localV0, depth, textureU0, textureV0,
            red, green, blue, alpha);
        texturedVertex(consumer, pose, basis, localU1, localV1, depth, textureU1, textureV0,
            red, green, blue, alpha);
        texturedVertex(consumer, pose, basis, localU2, localV2, depth, textureU1, textureV1,
            red, green, blue, alpha);
        texturedVertex(consumer, pose, basis, localU3, localV3, depth, textureU0, textureV1,
            red, green, blue, alpha);
    }

    private static void colorVertex(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float localU,
        float localV,
        float depth,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        float x = (float) (basis.right().x * localU + basis.up().x * localV + basis.forward().x * depth);
        float y = (float) (basis.right().y * localU + basis.up().y * localV + basis.forward().y * depth);
        float z = (float) (basis.right().z * localU + basis.up().z * localV + basis.forward().z * depth);
        consumer.addVertex(pose, x, y, z)
            .setColor(red, green, blue, Mth.clamp(alpha, 0, 255));
    }

    private static void texturedVertex(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        PlaneBasis basis,
        float localU,
        float localV,
        float depth,
        float textureU,
        float textureV,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        float x = (float) (basis.right().x * localU + basis.up().x * localV + basis.forward().x * depth);
        float y = (float) (basis.right().y * localU + basis.up().y * localV + basis.forward().y * depth);
        float z = (float) (basis.right().z * localU + basis.up().z * localV + basis.forward().z * depth);
        consumer.addVertex(pose, x, y, z)
            .setColor(red, green, blue, Mth.clamp(alpha, 0, 255))
            .setUv(textureU, textureV)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, (float) basis.forward().x, (float) basis.forward().y, (float) basis.forward().z);
    }

    private static float remap(float value, float start, float end) {
        if (end <= start) {
            return value >= end ? 1.0F : 0.0F;
        }
        return Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static Branch branch(
        int rootNode,
        int side,
        float delay,
        float maxWidth,
        BranchPoint... points
    ) {
        return new Branch(rootNode, side, delay, maxWidth, List.of(points));
    }

    private static BranchPoint point(float outward, float rise) {
        return new BranchPoint(outward, rise);
    }

    private record BranchPoint(float outward, float rise) {
    }

    private record Branch(int rootNode, int side, float delay, float maxWidth, List<BranchPoint> points) {
        private Branch {
            points = List.copyOf(points);
        }
    }

    private record RiftShard(
        int rootNode,
        int side,
        float outward,
        float rise,
        float width,
        float height,
        float tiltDegrees,
        float delay,
        float phase
    ) {
    }

    private record StormBolt(float angle, float radius, float seed, long spawnTick) {
    }

    private record LocalLine(float u0, float v0, float u1, float v1) {
    }

    private record Ribbon(float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
    }

    private record PlaneBasis(Vec3 right, Vec3 up, Vec3 forward) {
    }

    private static final class RiftPullWindSound implements TickableSoundInstance {
        private final ResourceLocation location = ModSoundEvents.RIFT_PULL_WIND.get().getLocation();
        private final RandomSource random = SoundInstance.createUnseededRandom();
        private final BlockPos anchor;
        private Sound sound;
        private float volume;
        private boolean stopped;

        private RiftPullWindSound(BlockPos anchor) {
            this.anchor = anchor.immutable();
        }

        @Override
        public void tick() {
            Minecraft client = Minecraft.getInstance();
            RiftView current = view;
            if (!current.active() || client.level == null || client.player == null) {
                fadeOutAndStop();
                return;
            }
            float gameTime = client.level.getGameTime();
            float age = gameTime - current.startedAt();
            float pullProgress = OverworldRiftGeometry.gravityPullProgress((long) age);
            if (pullProgress <= 0.0F || pullProgress >= 1.0F) {
                fadeOutAndStop();
                return;
            }
            double dx = client.player.getX() - (anchor.getX() + 0.5D);
            double dz = client.player.getZ() - (anchor.getZ() + 0.5D);
            float distance = (float) Math.sqrt(dx * dx + dz * dz);
            float distanceFactor = 1.0F - smooth(remap(
                distance, 0.0F, (float) OverworldRiftGeometry.GRAVITY_PULL_RADIUS
            ));
            float envelope = smooth(remap(pullProgress, 0.0F, 0.15F))
                * (1.0F - smooth(remap(pullProgress, 0.82F, 1.0F)));
            float targetVolume = 0.55F * envelope * distanceFactor;
            volume += (targetVolume - volume) * 0.12F;
            if (volume < 0.004F && targetVolume < 0.004F) {
                stopped = true;
            }
        }

        private void fadeOutAndStop() {
            volume *= 0.82F;
            if (volume < 0.004F) {
                stopped = true;
            }
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public ResourceLocation getLocation() {
            return location;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            WeighedSoundEvents events = manager.getSoundEvent(location);
            sound = events == null ? SoundManager.EMPTY_SOUND : events.getSound(random);
            return events;
        }

        @Override
        public Sound getSound() {
            return sound;
        }

        @Override
        public SoundSource getSource() {
            return SoundSource.AMBIENT;
        }

        @Override
        public boolean isLooping() {
            return true;
        }

        @Override
        public boolean isRelative() {
            return false;
        }

        @Override
        public int getDelay() {
            return 0;
        }

        @Override
        public float getVolume() {
            return volume;
        }

        @Override
        public float getPitch() {
            return 1.0F;
        }

        @Override
        public double getX() {
            return anchor.getX() + 0.5D;
        }

        @Override
        public double getY() {
            return OverworldRiftGeometry.centerY(anchor);
        }

        @Override
        public double getZ() {
            return anchor.getZ() + 0.5D;
        }

        @Override
        public SoundInstance.Attenuation getAttenuation() {
            return SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    private record RiftView(
        boolean active,
        BlockPos anchor,
        float orientationDegrees,
        long startedAt,
        long endsAt
    ) {
        private RiftView {
            anchor = anchor.immutable();
            orientationDegrees = active
                ? OverworldRiftGeometry.normalizeOrientation(orientationDegrees)
                : 0.0F;
        }

        private static RiftView inactive() {
            return new RiftView(false, BlockPos.ZERO, 0.0F, 0L, 0L);
        }
    }
}
