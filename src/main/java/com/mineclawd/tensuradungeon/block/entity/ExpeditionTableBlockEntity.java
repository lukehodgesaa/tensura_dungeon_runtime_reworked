package com.mineclawd.tensuradungeon.block.entity;

import com.mineclawd.tensuradungeon.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class ExpeditionTableBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE =
        RawAnimation.begin().thenLoop("animation.expedition_table.idle");
    private static final RawAnimation IDLE_DIALOGUE =
        RawAnimation.begin().thenLoop("animation.expedition_table.idle_dialogue");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean clientDialogueActive;

    public ExpeditionTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPEDITION_TABLE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(worldPosition, getBlockState().getBlock(), 2);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "expedition_table", 8,
            state -> state.setAndContinue(clientDialogueActive ? IDLE_DIALOGUE : IDLE)));
    }

    public void setClientDialogueActive(boolean active) {
        clientDialogueActive = active;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
