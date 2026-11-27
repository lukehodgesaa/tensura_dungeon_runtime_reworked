package com.mineclawd.tensuradungeon.block;

import com.mineclawd.tensuradungeon.network.DungeonPreparationOpenPayload;
import com.mineclawd.tensuradungeon.network.ExpeditionTableDialogueOpenPayload;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mineclawd.tensuradungeon.runtime.DungeonPreparationPartyData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class ExpeditionTableInteraction {
    public static final double MAX_DIALOGUE_DISTANCE_SQUARED = 100.0D;

    private ExpeditionTableInteraction() {
    }

    public static InteractionResult openDialogue(Player player, BlockPos tablePos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!NetworkRegistry.hasChannel(
            serverPlayer.connection,
            ExpeditionTableDialogueOpenPayload.TYPE.id()
        )) {
            serverPlayer.sendSystemMessage(Component.literal(
                "Expedition dialogue is unavailable for this client."
            ));
            return InteractionResult.CONSUME;
        }
        PacketDistributor.sendToPlayer(
            serverPlayer,
            new ExpeditionTableDialogueOpenPayload(tablePos)
        );
        return InteractionResult.CONSUME;
    }

    public static void prepareExpedition(ServerPlayer player, BlockPos tablePos) {
        if (!player.serverLevel().getBlockState(tablePos).is(ModBlocks.EXPEDITION_TABLE.get())
            || player.distanceToSqr(Vec3.atCenterOf(tablePos)) > MAX_DIALOGUE_DISTANCE_SQUARED) {
            player.sendSystemMessage(Component.literal(
                "Move closer to the Expedition Table to prepare a dungeon run."
            ));
            return;
        }
        if (!NetworkRegistry.hasChannel(
            player.connection,
            DungeonPreparationOpenPayload.TYPE.id()
        )) {
            player.sendSystemMessage(Component.literal(
                "Dungeon preparation interface is unavailable for this client."
            ));
            return;
        }
        DungeonPreparationPartyData.get(player.serverLevel()).open(player, tablePos);
        PacketDistributor.sendToPlayer(player, new DungeonPreparationOpenPayload());
    }
}
