package com.mineclawd.tensuradungeon.block;

import com.mineclawd.tensuradungeon.network.DungeonExitPromptPayload;
import com.mineclawd.tensuradungeon.network.LootSummaryPayload;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.CoreRecoveryData;
import com.mineclawd.tensuradungeon.runtime.SoulOrbHaulData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public class FloorExitPortalBlock extends Block {
    public static final int INTERACTION_WIDTH = 1;
    public static final int INTERACTION_HEIGHT = 6;
    public static final int INTERACTION_DEPTH = 1;
    public static final BooleanProperty VISUAL_ENTITY_PRESENT =
        BooleanProperty.create("visual_entity_present");

    public FloorExitPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(VISUAL_ENTITY_PRESENT, false));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return isVisualEntityPresent(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VISUAL_ENTITY_PRESENT);
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        return openMenu(serverLevel, serverPlayer, pos);
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            return openMenu(serverLevel, serverPlayer, pos) == InteractionResult.CONSUME
                ? ItemInteractionResult.CONSUME
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static boolean isVisualEntityPresent(BlockState state) {
        return state != null
            && state.hasProperty(VISUAL_ENTITY_PRESENT)
            && state.getValue(VISUAL_ENTITY_PRESENT);
    }

    public static void setVisualEntityPresent(ServerLevel level, BlockPos pos, boolean present) {
        if (level == null || pos == null) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof FloorExitPortalBlock)
            || !current.hasProperty(VISUAL_ENTITY_PRESENT)
            || current.getValue(VISUAL_ENTITY_PRESENT) == present) {
            return;
        }
        level.setBlock(pos, current.setValue(VISUAL_ENTITY_PRESENT, present), Block.UPDATE_CLIENTS);
    }

    public static boolean containsInteractionVolume(
        ServerLevel level,
        BlockPos pos,
        ServerPlayer player
    ) {
        if (level == null || pos == null || player == null) {
            return false;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof FloorExitPortalBlock)) {
            return false;
        }
        AABB volume = new AABB(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + INTERACTION_WIDTH, pos.getY() + INTERACTION_HEIGHT, pos.getZ() + INTERACTION_DEPTH
        );
        return player.getBoundingBox().intersects(volume);
    }

    public static InteractionResult openMenu(ServerLevel serverLevel, ServerPlayer serverPlayer, BlockPos pos) {
        DungeonRunManager manager = DungeonRunManager.get(serverLevel);
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(serverLevel)
                .forPlayer(serverPlayer.getUUID())
                .map(DungeonInstanceAllocationData.Allocation::key)
                .orElse(null);
        String blockReason = instanceKey == null
            ? manager.getExitPortalInteractionBlockReason(serverLevel, serverPlayer, pos)
            : manager.getExitPortalInteractionBlockReason(
                instanceKey, serverLevel, serverPlayer, pos
            );
        if (!"NONE".equals(blockReason)) {
            serverPlayer.sendSystemMessage(Component.literal("Exit portal unavailable: " + blockReason));
            serverPlayer.sendSystemMessage(Component.literal("Exit Portal Reason: "
                + (instanceKey == null ? manager.getExitPortalReason()
                    : manager.getExitPortalReason(instanceKey))));
            serverPlayer.sendSystemMessage(Component.literal("Exit Portal Details: "
                + (instanceKey == null ? manager.getExitPortalReadinessDetails()
                    : manager.getExitPortalReadinessDetails(instanceKey))));
            return InteractionResult.CONSUME;
        }

        int nextFloor = instanceKey == null
            ? manager.getNextFloorNumber()
            : manager.getNextFloorNumber(instanceKey);
        String runSessionId = instanceKey == null
            ? manager.getRunSessionId()
            : manager.getRunSessionId(instanceKey);
        if (NetworkRegistry.hasChannel(serverPlayer.connection, LootSummaryPayload.TYPE.id())) {
            SoulOrbHaulData.Summary summary = SoulOrbHaulData.get(serverLevel).revealForDisplay(
                serverPlayer.getUUID(), runSessionId
            );
            if (summary.available()) {
                PacketDistributor.sendToPlayer(serverPlayer, LootSummaryPayload.from(
                    serverPlayer, summary, SoulOrbHaulData.ActionStatus.SUCCESS, "", true, true, nextFloor,
                    CoreRecoveryData.get(serverLevel)
                        .snapshot(serverPlayer.getUUID(), summary.sessionId(), summary.floor())
                        .pendingCandidates()
                ));
                return InteractionResult.CONSUME;
            }
        }
        if (NetworkRegistry.hasChannel(serverPlayer.connection, DungeonExitPromptPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(serverPlayer,
                new DungeonExitPromptPayload(
                    instanceKey == null ? manager.getFloorNumber() : manager.getFloorNumber(instanceKey),
                    nextFloor
                ));
        }
        return InteractionResult.CONSUME;
    }
}
