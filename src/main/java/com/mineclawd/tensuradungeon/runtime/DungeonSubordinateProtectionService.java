package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.network.ResurrectionBraceletActivationPayload;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.Container;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

public final class DungeonSubordinateProtectionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int POST_EVACUATION_INVULNERABILITY_TICKS = 60;

    private DungeonSubordinateProtectionService() {
    }

    public static boolean tryProtectFromDeath(
        ServerLevel level,
        LivingEntity participantEntity
    ) {
        if (level == null || participantEntity == null) {
            return false;
        }

        ProtectionContext context = resolveContext(level, participantEntity);
        if (context == null) {
            return false;
        }
        DungeonPreparationLoadoutData loadouts = DungeonPreparationLoadoutData.get(level);
        DungeonPreparationLoadoutData.BraceletReservation reservation =
            context.participant().kind()
                == DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE
                    ? loadouts.validateSubordinateBraceletReservation(
                        context.participant().id(),
                        context.owner().getUUID(),
                        context.protectionInventory(),
                        context.ownerInventory(),
                        context.participant().loadoutRevision(),
                        context.allocation().soloPreparation().requestedFloor()
                    ).orElse(null)
                    : loadouts.validateBraceletReservation(
                        context.participant().id(),
                        context.inventoryId(),
                        context.protectionInventory(),
                        context.participant().loadoutRevision(),
                        context.allocation().soloPreparation().requestedFloor()
                    ).orElse(null);
        if (reservation == null) {
            logRejected(context, "REGISTERED_BRACELET_REVALIDATION_FAILED");
            return false;
        }

        float deathHealth = participantEntity.getHealth();
        stabilize(participantEntity);
        String resultReason;
        boolean protectedParticipant;
        if (context.participant().kind()
            == DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE) {
            DungeonSubordinateTransportData.TransportResult result = context.transport()
                .evacuateProtectedSubordinate(
                    level,
                    context.allocation(),
                    participantEntity,
                    () -> loadouts.consumeTerminalReservations(
                        List.of(reservation),
                        List.of(),
                        context.protectionInventories()
                    )
                );
            protectedParticipant = result.accepted();
            resultReason = result.reason();
        } else if (participantEntity instanceof ServerPlayer player) {
            DungeonRunManager.ProtectedHostEvacuationResult result =
                DungeonRunManager.get(level).evacuateProtectedHost(
                    context.allocation().key(),
                    level,
                    player,
                    reservation
                );
            protectedParticipant = result.accepted();
            resultReason = result.reason();
        } else {
            protectedParticipant = false;
            resultReason = "HUMAN_PARTICIPANT_IS_NOT_SERVER_PLAYER";
        }
        if (!protectedParticipant) {
            participantEntity.setHealth(deathHealth);
            logRejected(context, resultReason);
            return false;
        }

        stabilize(participantEntity);
        clearHarmfulEffects(participantEntity);
        ItemStack activationStack = oneItem(reservation.expectedStack());
        PacketDistributor.sendToPlayer(
            context.owner(), new ResurrectionBraceletActivationPayload(activationStack)
        );
        playActivation(level, participantEntity);
        boolean subordinate = context.participant().kind()
            == DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE;
        context.owner().sendSystemMessage(Component.literal(
            "A Resurrection Bracelet protected " + participantEntity.getName().getString()
                + " and returned " + (subordinate ? "them" : "you")
                + " to the Dungeon Hub."
                + (subordinate ? " The run continues." : " The run has ended.")
        ));
        LOGGER.info(
            "[TD-BRACELET] result=PROTECTED participant={} kind={} owner={} instance={} epoch={} reason={}",
            participantEntity.getUUID(), context.participant().kind(), context.owner().getUUID(),
            context.allocation().key().instanceId(), context.allocation().key().epoch(), resultReason
        );
        return true;
    }

    private static ProtectionContext resolveContext(
        ServerLevel level,
        LivingEntity participantEntity
    ) {
        DungeonInstanceAllocationData allocations = DungeonInstanceAllocationData.get(level);
        DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);
        DungeonSubordinateTransportData.BindingRef bindingRef = transport
            .bindingForEntity(participantEntity.getUUID())
            .orElse(null);
        DungeonInstanceAllocationData.Allocation allocation = bindingRef == null
            ? participantEntity instanceof ServerPlayer player
                ? allocations.forPlayer(player.getUUID()).orElse(null)
                : null
            : allocations.forIdentity(bindingRef.key()).orElse(null);
        if (allocation == null || allocation.soloPreparation() == null
            || !allocation.lifecycle().state().allowsProtectedEvacuation()
            || allocation.entryDispatch() == null
            || allocation.entryDispatch().state()
                != DungeonInstanceAllocationData.DispatchState.ADMITTED
            || !allocation.dimension().equals(level.dimension().location().toString())) {
            return null;
        }
        DungeonInstanceAllocationData.FrozenParticipant participant = allocation.soloPreparation()
            .frozenParticipants().stream()
            .filter(candidate -> candidate.id().equals(participantEntity.getUUID()))
            .findFirst().orElse(null);
        if (participant == null || participant.loadoutRevision() < 0L) {
            return null;
        }
        if (participant.kind()
            == DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE) {
            if (bindingRef == null || !bindingRef.state().isInstanceBound()
                || participant.slot() != bindingRef.slot()
                || !participant.ownerId().equals(allocation.host())) {
                return null;
            }
        } else if (!(participantEntity instanceof ServerPlayer player)
            || !allocation.roster().contains(player.getUUID())
            || !participant.ownerId().equals(player.getUUID())) {
            return null;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(participant.ownerId());
        if (owner == null) return null;
        Container protectionInventory = participant.kind()
            == DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE
                ? TensuraCompat.subordinateInventory(participantEntity).orElse(null)
                : owner.getInventory();
        UUID inventoryId = participant.kind()
            == DungeonInstanceAllocationData.FrozenParticipantKind.SUBORDINATE
                ? participant.id()
                : owner.getUUID();
        return protectionInventory == null ? null : new ProtectionContext(
            allocation,
            participant,
            owner,
            transport,
            inventoryId,
            protectionInventory,
            owner.getInventory()
        );
    }

    static void stabilize(LivingEntity participant) {
        participant.setHealth(Math.max(1.0F, participant.getMaxHealth() * 0.25F));
        participant.setAbsorptionAmount(0.0F);
        participant.deathTime = 0;
        participant.invulnerableTime = POST_EVACUATION_INVULNERABILITY_TICKS;
        participant.clearFire();
        participant.fallDistance = 0.0F;
    }

    static void clearHarmfulEffects(LivingEntity participant) {
        for (MobEffectInstance effect : List.copyOf(participant.getActiveEffects())) {
            if (isHarmful(effect)) {
                participant.removeEffect(effect.getEffect());
            }
        }
    }

    static boolean isHarmful(MobEffectInstance effect) {
        return effect != null
            && effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
    }

    private static ItemStack oneItem(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static void playActivation(ServerLevel level, LivingEntity participant) {
        level.playSound(
            null, participant.blockPosition(), SoundEvents.TOTEM_USE,
            SoundSource.PLAYERS, 1.0F, 1.0F
        );
        level.sendParticles(
            ParticleTypes.TOTEM_OF_UNDYING,
            participant.getX(), participant.getY() + participant.getBbHeight() * 0.5D,
            participant.getZ(), 30, 0.35D, 0.5D, 0.35D, 0.0D
        );
    }

    private static void logRejected(ProtectionContext context, String reason) {
        LOGGER.warn(
            "[TD-BRACELET] result=REJECTED participant={} kind={} owner={} instance={} epoch={} reason={}",
            context.participant().id(), context.participant().kind(), context.owner().getUUID(),
            context.allocation().key().instanceId(), context.allocation().key().epoch(), reason
        );
    }

    private record ProtectionContext(
        DungeonInstanceAllocationData.Allocation allocation,
        DungeonInstanceAllocationData.FrozenParticipant participant,
        ServerPlayer owner,
        DungeonSubordinateTransportData transport,
        UUID inventoryId,
        Container protectionInventory,
        Container ownerInventory
    ) {
        Map<UUID, Container> protectionInventories() {
            Map<UUID, Container> inventories = new java.util.HashMap<>();
            if (protectionInventory != null) {
                inventories.put(inventoryId, protectionInventory);
            }
            if (ownerInventory != null) {
                inventories.put(owner.getUUID(), ownerInventory);
            }
            return Map.copyOf(inventories);
        }
    }
}
