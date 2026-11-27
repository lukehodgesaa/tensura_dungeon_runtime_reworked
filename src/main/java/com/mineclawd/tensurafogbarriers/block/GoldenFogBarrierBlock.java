package com.mineclawd.tensurafogbarriers.block;

import com.mineclawd.tensurafogbarriers.data.FogAnimationStyle;
import com.mineclawd.tensurafogbarriers.data.FogVariant;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class GoldenFogBarrierBlock extends Block {
    public static final MapCodec<GoldenFogBarrierBlock> CODEC = simpleCodec(GoldenFogBarrierBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<FogVariant> VARIANT = EnumProperty.create("variant", FogVariant.class);
    public static final EnumProperty<FogAnimationStyle> STYLE = EnumProperty.create("style", FogAnimationStyle.class);
    private static final VoxelShape X_OUTLINE = Block.box(7.0D, 0.0D, 0.0D, 9.0D, 16.0D, 16.0D);
    private static final VoxelShape Z_OUTLINE = Block.box(0.0D, 0.0D, 7.0D, 16.0D, 16.0D, 9.0D);

    public GoldenFogBarrierBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(AXIS, Direction.Axis.X)
            .setValue(VARIANT, FogVariant.GOLD)
            .setValue(STYLE, FogAnimationStyle.PORTAL));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        FogVariant next = state.getValue(VARIANT).next();
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(VARIANT, next), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.75F, 0.75F + next.ordinal() * 0.1F);
            player.displayClientMessage(Component.literal("Fog barrier color: " + next.displayName()), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.isShiftKeyDown() || !stack.is(Items.CLOCK)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        FogAnimationStyle next = state.getValue(STYLE).next();
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(STYLE, next), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.82F + next.ordinal() * 0.08F);
            player.displayClientMessage(Component.literal("Fog barrier animation: " + next.displayName()), true);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? X_OUTLINE : Z_OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, VARIANT, STYLE);
    }
}
