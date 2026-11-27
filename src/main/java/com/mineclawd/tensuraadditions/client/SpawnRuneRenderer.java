package com.mineclawd.tensuraadditions.client;

import com.mineclawd.tensuraadditions.entity.SpawnRuneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class SpawnRuneRenderer extends GeoEntityRenderer<SpawnRuneEntity> {
    public SpawnRuneRenderer(EntityRendererProvider.Context context) {
        super(context, new SpawnRuneModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public RenderType getRenderType(
            SpawnRuneEntity entity,
            ResourceLocation texture,
            @Nullable MultiBufferSource bufferSource,
            float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    public void render(
            SpawnRuneEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        float scale = entity.getRuneScale();
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }
}
