package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.entity.RiftVoidWarpEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class RiftVoidWarpRenderer extends GeoEntityRenderer<RiftVoidWarpEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID,
        "textures/entity/rift_magic_circle.png"
    );
    private static final RenderType CIRCLE_RENDER_TYPE = RenderType.create(
        "tensura_dungeon_rift_magic_circle",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        524_288,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, false, false))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(false)
    );

    public RiftVoidWarpRenderer(EntityRendererProvider.Context context) {
        super(context, new RiftVoidWarpModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public RenderType getRenderType(
        RiftVoidWarpEntity entity,
        ResourceLocation texture,
        MultiBufferSource bufferSource,
        float partialTick
    ) {
        return CIRCLE_RENDER_TYPE;
    }

    @Override
    protected void applyRotations(
        RiftVoidWarpEntity entity,
        PoseStack poseStack,
        float ageInTicks,
        float rotationYaw,
        float partialTick,
        float scale
    ) {
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - entity.getOrientationDegrees()));
        float circleScale = entity.getSize() / 5.0F;
        if (circleScale != 1.0F) {
            poseStack.scale(circleScale, circleScale, circleScale);
        }
    }

    @Override
    protected int getBlockLightLevel(RiftVoidWarpEntity entity, BlockPos pos) {
        return 15;
    }
}
