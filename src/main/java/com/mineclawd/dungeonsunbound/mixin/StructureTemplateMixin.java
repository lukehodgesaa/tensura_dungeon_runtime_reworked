package com.mineclawd.dungeonsunbound.mixin;

import com.mineclawd.dungeonsunbound.structure.OptionalBlockFallback;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
    @Inject(method = "load", at = @At("HEAD"))
    private void dungeonsUnbound$fallbackMissingBlocks(HolderGetter<Block> blocks, CompoundTag tag, CallbackInfo ci) {
        OptionalBlockFallback.apply(blocks, tag);
    }
}
