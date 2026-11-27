package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.registry.ModDimensions;
import com.mineclawd.tensuradungeon.runtime.DungeonPostEvacuationGuard;
import com.mineclawd.tensuradungeon.runtime.DungeonSubordinateProtectionService;
import com.mineclawd.tensuradungeon.runtime.DungeonSubordinateTransportData;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonMobCohesionEvents {
    private DungeonMobCohesionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void enforceDungeonHostileTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
            || !(mob.level() instanceof ServerLevel level)
            || !level.dimension().equals(ModDimensions.DUNGEON)) {
            return;
        }
        RoomControllerBlockEntity controller =
            RoomControllerBlockEntity.findActiveControllerTracking(level, mob);
        if (controller == null) {
            return;
        }
        LivingEntity requested = event.getNewAboutToBeSetTarget();
        if (controller.isValidDungeonAggroTarget(level, mob, requested)) {
            return;
        }
        event.setNewAboutToBeSetTarget(controller.selectDungeonAggroTarget(level, mob));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventGravityIncapacitatedAttack(AttackEntityEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level
            && RoomControllerBlockEntity.isPlayerGravityIncapacitated(
                level, event.getEntity()
            )) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventPostBraceletEvacuationDamage(LivingIncomingDamageEvent event) {
        if (DungeonPostEvacuationGuard.suppressDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity().level() instanceof ServerLevel level
            && level.dimension().equals(ModDimensions.DUNGEON)
            && (isOwnerSubordinatePair(event.getEntity(), event.getSource().getEntity())
                || isOwnerSubordinatePair(
                    event.getEntity(), event.getSource().getDirectEntity()
                ))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventDungeonFriendlyFire(LivingDamageEvent.Pre event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        Entity sourceEntity = event.getSource().getEntity();
        Entity directSource = event.getSource().getDirectEntity();
        if (isGravityIncapacitatedAttacker(level, sourceEntity)
            || isGravityIncapacitatedAttacker(level, directSource)) {
            event.setNewDamage(0.0F);
            return;
        }
        DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);

        if (level.dimension().equals(ModDimensions.DUNGEON)) {
            Entity attacker = event.getSource().getEntity();
            Entity directEntity = event.getSource().getDirectEntity();
            if (event.getEntity() instanceof ServerPlayer
                && (isDungeonPlayer(attacker) || isDungeonPlayer(directEntity))) {
                event.setNewDamage(0.0F);
                return;
            }

            if (isOwnerSubordinatePair(event.getEntity(), attacker)
                || isOwnerSubordinatePair(event.getEntity(), directEntity)) {
                event.setNewDamage(0.0F);
                return;
            }

            if ((attacker != null && transport.sameActivePartyMember(
                    level, event.getEntity().getUUID(), attacker.getUUID())
                ) || (directEntity != null && transport.sameActivePartyMember(
                    level, event.getEntity().getUUID(), directEntity.getUUID())
                )) {
                event.setNewDamage(0.0F);
                return;
            }
        }

        RoomControllerBlockEntity victimOwner = RoomControllerBlockEntity.findActiveControllerTracking(level, event.getEntity());
        Entity attacker = event.getSource().getEntity();
        if (victimOwner != null && attacker != null
            && victimOwner == RoomControllerBlockEntity.findActiveControllerTracking(level, attacker)) {
            event.setNewDamage(0.0F);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventRegisteredBraceletDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (DungeonPostEvacuationGuard.suppressDeath(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (DungeonSubordinateProtectionService.tryProtectFromDeath(level, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void maintainPostBraceletEvacuationGuard(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonPostEvacuationGuard.tick(player);
        }
    }

    @SubscribeEvent
    public static void clearPostBraceletEvacuationGuard(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonPostEvacuationGuard.clear(player, "PLAYER_LOGGED_OUT");
        }
    }

    private static boolean isDungeonPlayer(Entity entity) {
        return entity instanceof ServerPlayer player
            && player.level().dimension().equals(ModDimensions.DUNGEON);
    }

    private static boolean isOwnerSubordinatePair(LivingEntity victim, Entity attacker) {
        if (victim == null || attacker == null) return false;
        if (attacker instanceof ServerPlayer player
            && TensuraCompat.subordinateOwnerId(victim)
                .filter(player.getUUID()::equals).isPresent()) {
            return true;
        }
        return victim instanceof ServerPlayer player
            && attacker instanceof LivingEntity living
            && TensuraCompat.subordinateOwnerId(living)
                .filter(player.getUUID()::equals).isPresent();
    }

    private static boolean isGravityIncapacitatedAttacker(ServerLevel level, Entity entity) {
        return entity instanceof ServerPlayer player
            && RoomControllerBlockEntity.isPlayerGravityIncapacitated(level, player);
    }
}
