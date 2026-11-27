package com.mineclawd.tensurahudnative.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mineclawd.tensurahudnative.client.OverheadStatusRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Shadow @Final protected EntityRenderDispatcher entityRenderDispatcher;
    @Shadow @Final private Font font;

    @Inject(method = "render", at = @At("HEAD"))
    private void tensuraHudNative$renderOverhead(Entity entity, float entityYaw, float partialTick,
                                                  PoseStack poseStack, MultiBufferSource buffers,
                                                  int packedLight, CallbackInfo callbackInfo) {
        Quaternionf cameraRotation = this.entityRenderDispatcher.cameraOrientation();
        OverheadStatusRenderer.renderFromEntityRenderer(
                entity, partialTick, poseStack, buffers, cameraRotation, this.font);
    }
}
