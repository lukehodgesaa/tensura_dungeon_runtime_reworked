package com.mineclawd.tensuradungeon.block;

import com.mineclawd.tensuradungeon.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ExpeditionTablePartBlock extends Block {
    public static final IntegerProperty OFFSET_X = IntegerProperty.create(
        "offset_x", 0, ExpeditionTableBlock.MAX_X - ExpeditionTableBlock.MIN_X
    );
    public static final IntegerProperty OFFSET_Z = IntegerProperty.create(
        "offset_z", 0, ExpeditionTableBlock.MAX_Z - ExpeditionTableBlock.MIN_Z
    );

    public ExpeditionTablePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(OFFSET_X, encodeX(0))
            .setValue(OFFSET_Z, encodeZ(0)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OFFSET_X, OFFSET_Z);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos masterPos = masterPos(pos, state);
        return level.getBlockState(masterPos).is(ModBlocks.EXPEDITION_TABLE.get())
            ? ExpeditionTableInteraction.openDialogue(player, masterPos)
            : InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState result = super.playerWillDestroy(level, pos, state, player);
        if (!level.isClientSide() && player instanceof ServerPlayer) {
            BlockPos masterPos = masterPos(pos, state);
            if (level.getBlockState(masterPos).is(ModBlocks.EXPEDITION_TABLE.get())) {
                level.destroyBlock(masterPos, !player.isCreative(), player);
            }
        }
        return result;
    }

    @Override
    protected VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return ExpeditionTableBlock.shapeForOffset(
            decodeX(state.getValue(OFFSET_X)), decodeZ(state.getValue(OFFSET_Z))
        );
    }

    @Override
    protected VoxelShape getCollisionShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return getShape(state, level, pos, context);
    }

    public static BlockPos masterPos(BlockPos partPos, BlockState state) {
        return partPos.offset(
            -decodeX(state.getValue(OFFSET_X)),
            0,
            -decodeZ(state.getValue(OFFSET_Z))
        );
    }

    static int encodeX(int logicalOffset) {
        return logicalOffset - ExpeditionTableBlock.MIN_X;
    }

    static int encodeZ(int logicalOffset) {
        return logicalOffset - ExpeditionTableBlock.MIN_Z;
    }

    static int decodeX(int storedOffset) {
        return storedOffset + ExpeditionTableBlock.MIN_X;
    }

    static int decodeZ(int storedOffset) {
        return storedOffset + ExpeditionTableBlock.MIN_Z;
    }
}
