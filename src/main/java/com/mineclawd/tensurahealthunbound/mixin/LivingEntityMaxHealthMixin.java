package com.mineclawd.tensurahealthunbound.mixin;

import com.mineclawd.tensurahealthunbound.DungeonHealthCap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMaxHealthMixin {
    @Inject(method = "getAttributeValue", at = @At("RETURN"), cancellable = true)
    private void tensuraHealthUnbound$capOutsideDungeon(
        Holder<Attribute> attribute,
        CallbackInfoReturnable<Double> cir
    ) {
        if (attribute.value() != Attributes.MAX_HEALTH.value()) {
            return;
        }
        double value = cir.getReturnValueD();
        double capped = DungeonHealthCap.capFor((LivingEntity) (Object) this, value);
        if (capped != value) {
            cir.setReturnValue(capped);
        }
    }
}
