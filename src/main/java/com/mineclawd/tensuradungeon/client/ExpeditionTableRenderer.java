package com.mineclawd.tensuradungeon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mineclawd.tensuradungeon.block.entity.ExpeditionTableBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.util.RenderUtil;

public final class ExpeditionTableRenderer extends GeoBlockRenderer<ExpeditionTableBlockEntity> {
    public ExpeditionTableRenderer() {
        super(new ExpeditionTableGeoModel());
    }

    @Override
    public boolean shouldRenderOffScreen(ExpeditionTableBlockEntity blockEntity) {
        return true;
    }

    @Override
    public void renderRecursively(
        PoseStack poseStack,
        ExpeditionTableBlockEntity animatable,
        GeoBone bone,
        RenderType renderType,
        MultiBufferSource bufferSource,
        VertexConsumer buffer,
        boolean isReRender,
        float partialTick,
        int packedLight,
        int packedOverlay,
        int renderColor
    ) {
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);
        buffer = checkAndRefreshBuffer(isReRender, buffer, bufferSource, renderType);
        renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, renderColor);

        if (!isReRender) {
            applyRenderLayersForBone(
                poseStack,
                animatable,
                bone,
                renderType,
                bufferSource,
                buffer,
                partialTick,
                packedLight,
                packedOverlay
            );
        }

        renderChildBones(
            poseStack,
            animatable,
            bone,
            renderType,
            bufferSource,
            buffer,
            isReRender,
            partialTick,
            packedLight,
            packedOverlay,
            renderColor
        );
        poseStack.popPose();
    }
}
