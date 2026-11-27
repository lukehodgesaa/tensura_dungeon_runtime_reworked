package com.mineclawd.tensuradungeon.mixin;

import com.mineclawd.tensuradungeon.runtime.DungeonGravityPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({FallingBlock.class, BrushableBlock.class, PointedDripstoneBlock.class, ScaffoldingBlock.class})
abstract class GravityAffectedBlockMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tensuraDungeon$keepGravityBlockInPlace(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo callback
    ) {
        if (DungeonGravityPolicy.suppressesBlockGravity(level)) {
            callback.cancel();
        }
    }
}
