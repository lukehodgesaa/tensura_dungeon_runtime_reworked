package com.mineclawd.tensuradungeon.block;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class RoomControllerBlock extends BaseEntityBlock {
    public static final MapCodec<RoomControllerBlock> CODEC = simpleCodec(RoomControllerBlock::new);

    public RoomControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoomControllerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != ModBlockEntities.ROOM_CONTROLLER.get()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> RoomControllerBlockEntity.serverTick(tickerLevel, pos, tickerState, (RoomControllerBlockEntity) blockEntity);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()
            && level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof RoomControllerBlockEntity controller) {
            controller.releaseBarrierResources(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(id, param);
    }
}
