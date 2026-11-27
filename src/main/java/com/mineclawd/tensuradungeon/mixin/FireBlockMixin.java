package com.mineclawd.tensuradungeon.mixin;

import com.mineclawd.tensuradungeon.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
abstract class FireBlockMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tensuraDungeon$suppressFireSpreadInDungeon(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo callback
    ) {
        if (!level.dimension().equals(ModDimensions.DUNGEON)) {
            return;
        }
        if (!level.getBlockState(pos.below()).isFireSource(level, pos.below(), Direction.UP)) {
            level.removeBlock(pos, false);
        }
        callback.cancel();
    }
}
