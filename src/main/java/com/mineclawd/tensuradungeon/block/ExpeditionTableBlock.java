package com.mineclawd.tensuradungeon.block;

import com.mineclawd.tensuradungeon.block.entity.ExpeditionTableBlockEntity;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ExpeditionTableBlock extends BaseEntityBlock {
    public static final MapCodec<ExpeditionTableBlock> CODEC = simpleCodec(ExpeditionTableBlock::new);
    public static final int MIN_X = -3;
    public static final int MAX_X = 3;
    public static final int MIN_Z = -1;
    public static final int MAX_Z = 1;

    private static final VoxelShape TABLETOP = Block.box(0.0D, 14.0D, 0.0D, 16.0D, 17.0D, 16.0D);
    private static final VoxelShape LEG = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 14.0D, 13.0D);

    public ExpeditionTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExpeditionTableBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos masterPos = context.getClickedPos();
        return canOccupy(context.getLevel(), masterPos) ? defaultBlockState() : null;
    }

    @Override
    public void setPlacedBy(
        Level level,
        BlockPos pos,
        BlockState state,
        @Nullable LivingEntity placer,
        ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        forEachFootprint((offsetX, offsetZ) -> {
            if (offsetX == 0 && offsetZ == 0) {
                return;
            }
            BlockState part = ModBlocks.EXPEDITION_TABLE_PART.get().defaultBlockState()
                .setValue(ExpeditionTablePartBlock.OFFSET_X, ExpeditionTablePartBlock.encodeX(offsetX))
                .setValue(ExpeditionTablePartBlock.OFFSET_Z, ExpeditionTablePartBlock.encodeZ(offsetZ));
            level.setBlock(pos.offset(offsetX, 0, offsetZ), part, Block.UPDATE_ALL);
        });
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ensureFootprint(level, pos);
    }

    @Override
    protected void onRemove(
        BlockState state,
        Level level,
        BlockPos pos,
        BlockState newState,
        boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            removeFootprint(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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
        return ExpeditionTableInteraction.openDialogue(player, pos);
    }

    @Override
    protected VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return shapeForOffset(0, 0);
    }

    @Override
    protected VoxelShape getCollisionShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return shapeForOffset(0, 0);
    }

    static VoxelShape shapeForOffset(int offsetX, int offsetZ) {
        boolean cornerLeg = Math.abs(offsetX) == MAX_X && Math.abs(offsetZ) == MAX_Z;
        boolean frontCenterLeg = offsetX == 0 && offsetZ == MIN_Z;
        return cornerLeg || frontCenterLeg ? Shapes.or(TABLETOP, LEG) : TABLETOP;
    }

    public static boolean isMasterOrPart(BlockState state) {
        return state.is(ModBlocks.EXPEDITION_TABLE.get())
            || state.is(ModBlocks.EXPEDITION_TABLE_PART.get());
    }

    public static BlockPos resolveMaster(BlockPos pos, BlockState state) {
        return state.is(ModBlocks.EXPEDITION_TABLE_PART.get())
            ? ExpeditionTablePartBlock.masterPos(pos, state)
            : pos;
    }

    private static boolean canOccupy(Level level, BlockPos masterPos) {
        final boolean[] available = {true};
        forEachFootprint((offsetX, offsetZ) -> {
            if (!available[0]) {
                return;
            }
            BlockPos footprintPos = masterPos.offset(offsetX, 0, offsetZ);
            if (!level.getWorldBorder().isWithinBounds(footprintPos)
                || !level.getBlockState(footprintPos).canBeReplaced()
                || !level.getBlockState(footprintPos.above()).canBeReplaced()) {
                available[0] = false;
            }
        });
        return available[0];
    }

    private static void removeFootprint(Level level, BlockPos masterPos) {
        forEachFootprint((offsetX, offsetZ) -> {
            if (offsetX == 0 && offsetZ == 0) {
                return;
            }
            BlockPos partPos = masterPos.offset(offsetX, 0, offsetZ);
            BlockState partState = level.getBlockState(partPos);
            if (partState.is(ModBlocks.EXPEDITION_TABLE_PART.get())
                && ExpeditionTablePartBlock.masterPos(partPos, partState).equals(masterPos)) {
                level.removeBlock(partPos, false);
            }
        });
    }

    public static void ensureFootprint(ServerLevel level, BlockPos masterPos) {
        forEachFootprint((offsetX, offsetZ) -> {
            if (offsetX == 0 && offsetZ == 0) {
                return;
            }
            BlockPos partPos = masterPos.offset(offsetX, 0, offsetZ);
            BlockState state = level.getBlockState(partPos);
            if (state.is(ModBlocks.EXPEDITION_TABLE_PART.get())) {
                return;
            }
            if (!state.canBeReplaced() || !level.getBlockState(partPos.above()).canBeReplaced()) {
                return;
            }
            level.setBlock(partPos, ModBlocks.EXPEDITION_TABLE_PART.get().defaultBlockState()
                .setValue(ExpeditionTablePartBlock.OFFSET_X, ExpeditionTablePartBlock.encodeX(offsetX))
                .setValue(ExpeditionTablePartBlock.OFFSET_Z, ExpeditionTablePartBlock.encodeZ(offsetZ)), Block.UPDATE_ALL);
        });
    }

    private static void forEachFootprint(BiConsumer<Integer, Integer> consumer) {
        for (int offsetX = MIN_X; offsetX <= MAX_X; offsetX++) {
            for (int offsetZ = MIN_Z; offsetZ <= MAX_Z; offsetZ++) {
                consumer.accept(offsetX, offsetZ);
            }
        }
    }
}
