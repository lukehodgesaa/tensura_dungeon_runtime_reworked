package com.mineclawd.tensuradungeon.mixin;

import com.mineclawd.tensuradungeon.client.ExpeditionTableDialogueClient;
import com.mineclawd.tensuradungeon.client.SubordinateDebriefClient;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class ExpeditionCameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float xRot, float yRot, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void tensuraDungeon$applyExpeditionDialoguePose(
        net.minecraft.world.level.BlockGetter level,
        net.minecraft.world.entity.Entity entity,
        boolean detached,
        boolean thirdPerson,
        float partialTick,
        CallbackInfo callback
    ) {
        if (ExpeditionTableDialogueClient.isDirectCameraOverrideActive()) {
            setPosition(ExpeditionTableDialogueClient.directCameraPosition());
            setRotation(
                ExpeditionTableDialogueClient.directCameraYaw(),
                ExpeditionTableDialogueClient.directCameraPitch(),
                0.0F
            );
            return;
        }
        if (!SubordinateDebriefClient.isDirectCameraOverrideActive()) {
            return;
        }
        setPosition(SubordinateDebriefClient.directCameraPosition());
        setRotation(
            SubordinateDebriefClient.directCameraYaw(),
            SubordinateDebriefClient.directCameraPitch(),
            0.0F
        );
    }
}
