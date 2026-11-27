package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.network.ReturnWhistleActivationPayload;
import com.mineclawd.tensuradungeon.network.ReturnWhistleChannelStatePayload;
import com.mineclawd.tensuradungeon.registry.ModSoundEvents;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonReturnWhistleChannel {
    static final int CHANNEL_TICKS = 140;
    private static final double MOVEMENT_TOLERANCE_SQR = 0.0001D;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    private DungeonReturnWhistleChannel() {
    }

    public static boolean handleUse(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!DungeonPreparationLoadoutData.Category.RETURN_WHISTLE.eligible(held)) {
            interrupt(player, "another item was used");
            return false;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            showNotice(player, "SELECT THE REGISTERED WHISTLE IN YOUR MAIN HAND");
            return true;
        }
        Channel existing = CHANNELS.get(player.getUUID());
        if (existing != null) {
            return true;
        }
        ContextResolution resolution = resolveContext(player);
        Context context = resolution.context();
        if (context == null) {
            showNotice(player, resolution.message());
            return true;
        }
        if (RoomControllerBlockEntity.isPlayerGravityIncapacitated(player.serverLevel(), player)) {
            showNotice(player, "RETURN WHISTLE BLOCKED WHILE INCAPACITATED");
            return true;
        }
        DungeonPreparationLoadoutData.WhistleReservation reservation = context.reservation();
        if (player.getInventory().selected != reservation.inventorySlot()
            || !sameOneItem(held, reservation.expectedStack())) {
            showNotice(player, "SELECT THE EXACT WHISTLE REGISTERED IN YOUR LOADOUT");
            return true;
        }
        Channel channel = new Channel(
            context.allocation().key(), reservation, player.position(),
            player.serverLevel().dimension().location().toString(),
            player.serverLevel().getGameTime()
        );
        CHANNELS.put(player.getUUID(), channel);
        showProgress(player, 0L);
        player.playNotifySound(
            ModSoundEvents.RETURN_WHISTLE_CHANNEL.get(),
            SoundSource.PLAYERS, 0.9F, 1.0F
        );
        LOGGER.info(
            "[TD-WHISTLE] result=STARTED player={} instance={} epoch={}",
            player.getUUID(), channel.key().instanceId(), channel.key().epoch()
        );
        return true;
    }

    public static void tick(ServerPlayer player) {
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel == null) return;
        Context context = resolveContext(player).context();
        if (context == null || !context.allocation().key().equals(channel.key())
            || !context.reservation().sameReference(channel.reservation())) {
            interrupt(player, "the active run or registered Whistle changed");
            return;
        }
        if (!player.serverLevel().dimension().location().toString().equals(channel.dimension())
            || player.position().distanceToSqr(channel.startPosition()) > MOVEMENT_TOLERANCE_SQR) {
            interrupt(player, "movement interrupted the channel");
            return;
        }
        ItemStack selected = player.getInventory().getSelected();
        if (player.getInventory().selected != channel.reservation().inventorySlot()
            || !sameOneItem(selected, channel.reservation().expectedStack())) {
            interrupt(player, "the selected Whistle changed");
            return;
        }
        long elapsed = player.serverLevel().getGameTime() - channel.startGameTime();
        if (elapsed > 0L && elapsed < CHANNEL_TICKS && elapsed % 4L == 0L) {
            spawnChannelParticles(player, elapsed);
        }
        if (elapsed < CHANNEL_TICKS) {
            if (elapsed > 0L && elapsed % 2L == 0L) {
                showProgress(player, elapsed);
            }
            return;
        }
        showProgress(player, CHANNEL_TICKS);
        complete(player, context, channel);
    }

    public static void interrupt(ServerPlayer player, String reason) {
        Channel removed = CHANNELS.remove(player.getUUID());
        if (removed == null) return;
        stopPresentation(
            player, 0, "CHANNEL INTERRUPTED • " + reason.toUpperCase(java.util.Locale.ROOT),
            ReturnWhistleChannelStatePayload.Tone.WARNING, 60
        );
        LOGGER.info(
            "[TD-WHISTLE] result=INTERRUPTED player={} instance={} epoch={} reason={}",
            player.getUUID(), removed.key().instanceId(), removed.key().epoch(), reason
        );
    }

    public static void interruptIfChanneling(ServerPlayer player, String reason) {
        if (player != null) {
            interrupt(player, reason);
        }
    }

    public static boolean isChanneling(UUID playerId) {
        return CHANNELS.containsKey(playerId);
    }

    private static void complete(ServerPlayer player, Context context, Channel channel) {
        DungeonPreparationLoadoutData loadouts = DungeonPreparationLoadoutData.get(player.serverLevel());
        ItemStack activationStack = channel.reservation().expectedStack().copy();
        activationStack.setCount(1);
        DungeonRunManager.ProtectedHostEvacuationResult result =
            DungeonRunManager.get(player.serverLevel()).departHostWithWhistle(
                channel.key(), player.serverLevel(), player, channel.reservation()
            );
        CHANNELS.remove(player.getUUID());
        if (!result.accepted()) {
            stopPresentation(
                player, 100, "RETURN BLOCKED • " + result.reason(),
                ReturnWhistleChannelStatePayload.Tone.WARNING, 80
            );
            LOGGER.warn(
                "[TD-WHISTLE] result=REJECTED player={} instance={} epoch={} reason={}",
                player.getUUID(), channel.key().instanceId(), channel.key().epoch(), result.reason()
            );
            return;
        }
        stopPresentation(
            player, 100, "RETURN COMPLETE • DUNGEON HUB REACHED",
            ReturnWhistleChannelStatePayload.Tone.SUCCESS, 80
        );
        PacketDistributor.sendToPlayer(
            player, new ReturnWhistleActivationPayload(activationStack)
        );
        player.serverLevel().sendParticles(
            ParticleTypes.SOUL_FIRE_FLAME,
            player.getX(), player.getY() + 1.0D, player.getZ(),
            24, 0.45D, 0.8D, 0.45D, 0.025D
        );
        player.serverLevel().playSound(
            null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
            SoundSource.PLAYERS, 1.0F, 1.0F
        );
        LOGGER.info(
            "[TD-WHISTLE] result=DEPARTED player={} instance={} epoch={} reason={}",
            player.getUUID(), channel.key().instanceId(), channel.key().epoch(), result.reason()
        );
    }

    private static ContextResolution resolveContext(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(level).forPlayer(player.getUUID()).orElse(null);
        if (allocation == null || allocation.soloPreparation() == null
            || allocation.lifecycle().state() != DungeonInstanceAllocationData.LifecycleState.ACTIVE
            || allocation.entryDispatch() == null
            || allocation.entryDispatch().state()
                != DungeonInstanceAllocationData.DispatchState.ADMITTED
            || !allocation.dimension().equals(level.dimension().location().toString())
            || !allocation.host().equals(player.getUUID())) {
            return ContextResolution.rejected("NO ACTIVE HOST DUNGEON RUN FOR THIS WHISTLE");
        }
        if (!allocation.soloPreparation().hostAndSubordinateRoster(player.getUUID())) {
            return ContextResolution.rejected(
                "RETURN WHISTLE SUPPORTS HOST AND SUBORDINATES ONLY"
            );
        }
        DungeonInstanceAllocationData.FrozenParticipant participant = allocation.soloPreparation()
            .frozenParticipants().getFirst();
        if (participant.kind() != DungeonInstanceAllocationData.FrozenParticipantKind.HUMAN
            || !participant.id().equals(player.getUUID())
            || !participant.ownerId().equals(player.getUUID())) {
            return ContextResolution.rejected("HOST PARTICIPANT AUTHORITY COULD NOT BE VERIFIED");
        }
        DungeonPreparationLoadoutData.WhistleReservation reservation =
            DungeonPreparationLoadoutData.get(level).validateWhistleReservation(
                participant.id(), player, participant.loadoutRevision(),
                allocation.soloPreparation().requestedFloor()
            ).orElse(null);
        return reservation == null
            ? ContextResolution.rejected("REGISTERED WHISTLE CHANGED OR IS MISSING")
            : ContextResolution.accepted(new Context(allocation, reservation));
    }

    private static void showProgress(ServerPlayer player, long elapsedTicks) {
        int percent = (int) Math.min(100L, Math.max(0L, elapsedTicks) * 100L / CHANNEL_TICKS);
        PacketDistributor.sendToPlayer(player, new ReturnWhistleChannelStatePayload(
            true,
            percent,
            "HOLD USE • REMAIN STILL",
            ReturnWhistleChannelStatePayload.Tone.ACTIVE,
            0
        ));
    }

    private static void spawnChannelParticles(ServerPlayer player, long elapsedTicks) {
        double progress = Math.min(1.0D, Math.max(0L, elapsedTicks) / (double) CHANNEL_TICKS);
        int count = 1 + (int) Math.floor(progress * 6.0D);
        double spread = 0.18D + progress * 0.28D;
        player.serverLevel().sendParticles(
            ParticleTypes.SOUL_FIRE_FLAME,
            player.getX(), player.getY() + 0.9D, player.getZ(),
            count, spread, 0.45D + progress * 0.35D, spread, 0.006D + progress * 0.014D
        );
    }

    private static void showNotice(ServerPlayer player, String message) {
        PacketDistributor.sendToPlayer(player, new ReturnWhistleChannelStatePayload(
            false, 0, message, ReturnWhistleChannelStatePayload.Tone.WARNING, 60
        ));
    }

    private static void stopPresentation(
        ServerPlayer player,
        int percent,
        String message,
        ReturnWhistleChannelStatePayload.Tone tone,
        int displayTicks
    ) {
        PacketDistributor.sendToPlayer(player, new ReturnWhistleChannelStatePayload(
            false, percent, message, tone, displayTicks
        ));
        player.connection.send(new ClientboundStopSoundPacket(
            ModSoundEvents.RETURN_WHISTLE_CHANNEL.getId(), SoundSource.PLAYERS
        ));
    }

    private static boolean sameOneItem(ItemStack current, ItemStack expected) {
        return current != null && !current.isEmpty() && current.getCount() >= 1
            && ItemStack.isSameItemSameComponents(current, expected);
    }

    private record Context(
        DungeonInstanceAllocationData.Allocation allocation,
        DungeonPreparationLoadoutData.WhistleReservation reservation
    ) {
    }

    private record ContextResolution(Context context, String message) {
        static ContextResolution accepted(Context context) {
            return new ContextResolution(context, "");
        }

        static ContextResolution rejected(String message) {
            return new ContextResolution(null, message);
        }
    }

    private record Channel(
        DungeonInstanceAllocationData.InstanceKey key,
        DungeonPreparationLoadoutData.WhistleReservation reservation,
        Vec3 startPosition,
        String dimension,
        long startGameTime
    ) {
    }
}
