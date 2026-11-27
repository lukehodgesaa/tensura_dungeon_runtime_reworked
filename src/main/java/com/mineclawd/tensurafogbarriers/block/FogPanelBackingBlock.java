package com.mineclawd.tensurafogbarriers.block;

import com.mineclawd.tensurafogbarriers.data.FogAnimationStyle;
import com.mineclawd.tensurafogbarriers.data.FogVariant;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FogPanelBackingBlock extends Block {
    public static final MapCodec<FogPanelBackingBlock> CODEC = simpleCodec(FogPanelBackingBlock::new);
    public static final BooleanProperty SOLID = BooleanProperty.create("solid");
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<FogVariant> VARIANT = EnumProperty.create("variant", FogVariant.class);
    public static final EnumProperty<FogAnimationStyle> STYLE = EnumProperty.create("style", FogAnimationStyle.class);

    public FogPanelBackingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(SOLID, false)
            .setValue(AXIS, Direction.Axis.X)
            .setValue(VARIANT, FogVariant.GOLD)
            .setValue(STYLE, FogAnimationStyle.PORTAL));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SOLID) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOLID, AXIS, VARIANT, STYLE);
    }
}
