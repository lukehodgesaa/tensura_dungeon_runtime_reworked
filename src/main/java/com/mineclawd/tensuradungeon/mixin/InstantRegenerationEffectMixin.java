package com.mineclawd.tensuradungeon.mixin;

import com.mineclawd.tensuradungeon.compat.tensura.RecoveryOverdrawService;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "io.github.manasmods.tensura.effect.ability.InstantRegenerationEffect", remap = false)
public abstract class InstantRegenerationEffectMixin {
    @Redirect(
        method = "heal",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V",
            remap = true
        ),
        require = 0
    )
    private void tensuraDungeon$chargeRecoveryOverdrawAfterFullHeal(LivingEntity entity, float amount) {
        float before = entity.getHealth();
        entity.heal(amount);
        RecoveryOverdrawService.afterInstantRegenerationHeal(entity, before);
    }
}
