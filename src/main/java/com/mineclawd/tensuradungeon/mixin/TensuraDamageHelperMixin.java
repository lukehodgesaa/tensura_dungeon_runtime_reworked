package com.mineclawd.tensuradungeon.mixin;

import com.mineclawd.tensuradungeon.event.DungeonCombatObservationEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.manasmods.tensura.damage.TensuraDamageHelper", remap = false)
public abstract class TensuraDamageHelperMixin {
    @Inject(
        method = "directSpiritualHurt(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;FF)Z",
        at = @At("HEAD"),
        require = 0
    )
    private static void tensuraDungeon$observeSpiritualDamage(
        LivingEntity target,
        Entity attacker,
        DamageSource source,
        float amount,
        float resistanceFactor,
        CallbackInfoReturnable<Boolean> ci
    ) {
        DungeonCombatObservationEvents.onSpiritualDamage(target, attacker, source, amount, resistanceFactor);
    }
}
