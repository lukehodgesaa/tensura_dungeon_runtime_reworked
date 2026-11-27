package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraAddonCompat;
import com.mineclawd.tensuradungeon.runtime.DungeonDamageRecursionGuard;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierDefinition;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierCombatRules;
import com.mineclawd.tensuradungeon.runtime.DungeonSubordinateTransportData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonCombatModifierEvents {
    private static final int POISON_STACK_DURATION_TICKS = 100;
    private static final int POISON_MAX_DURATION_TICKS = 600;
    private static final int POISON_MAX_AMPLIFIER = 4;

    private DungeonCombatModifierEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = event.getEntity();
        Entity sourceEntity = event.getSource().getEntity();
        if (DungeonDamageRecursionGuard.isActive(target, sourceEntity)) {
            return;
        }
        RoomControllerBlockEntity targetController =
            RoomControllerBlockEntity.findActiveControllerTracking(level, target);
        if (targetController != null
            && target instanceof Mob
            && !targetController.isTrackedBoss(level, target)
            && !targetController.isTrackedSummoned(level, target)
            && targetController.isModifierIdActive(level, "spell-dampening")
            && TensuraAddonCompat.nativeDamageMetadata(event.getSource())
                .map(TensuraAddonCompat.NativeDamageMetadata::nativeMagic)
                .orElse(false)) {
            event.setNewDamage(DungeonModifierCombatRules.spellDampeningDamage(event.getNewDamage()));
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
            || attacker == target) {
            applyFinalShatterproofClamp(event, targetController, target, level);
            return;
        }
        RoomControllerBlockEntity attackerController =
            RoomControllerBlockEntity.findActiveControllerTracking(level, attacker);
        if (attackerController != null
            && attacker instanceof Mob
            && !attackerController.isTrackedBoss(level, attacker)
            && !attackerController.isTrackedSummoned(level, attacker)
            && attackerController.isModifierIdActive(level, "glass-cannon")) {
            event.setNewDamage(DungeonModifierCombatRules.glassCannonDamage(event.getNewDamage()));
        }

        if (event.getSource().getDirectEntity() != attacker) {
            applyFinalShatterproofClamp(event, targetController, target, level);
            return;
        }
        RoomControllerBlockEntity playerController =
            RoomControllerBlockEntity.findActiveControllerForPlayer(level, attacker);
        if (playerController != null
            && playerController.isModifierIdActive(level, "damage-boost")) {
            float multiplier = playerController.isTrackedBoss(level, event.getEntity()) ? 1.26F : 1.18F;
            event.setNewDamage(event.getNewDamage() * multiplier);
        }
        RoomControllerBlockEntity controller =
            RoomControllerBlockEntity.findActiveControllerTracking(level, attacker);
        if (controller == null) {
            applyFinalShatterproofClamp(event, targetController, target, level);
            return;
        }
        if (controller.isModifierActive(level, DungeonModifierDefinition.BERSERKER)
            && attacker.getHealth() <= attacker.getMaxHealth() * 0.5F) {
            event.setNewDamage(event.getNewDamage() * 1.30F);
        }
        if (controller.isModifierActive(level, DungeonModifierDefinition.POISONOUS)) {
            MobEffectInstance current = event.getEntity().getEffect(MobEffects.POISON);
            int amplifier = current == null ? 0 : Math.min(POISON_MAX_AMPLIFIER, current.getAmplifier() + 1);
            int duration = Math.min(
                POISON_MAX_DURATION_TICKS,
                (current == null ? 0 : current.getDuration()) + POISON_STACK_DURATION_TICKS
            );
            event.getEntity().addEffect(
                new MobEffectInstance(MobEffects.POISON, duration, amplifier, false, true)
            );
        }
        applyFinalShatterproofClamp(event, targetController, target, level);
    }

    private static void applyFinalShatterproofClamp(
        LivingDamageEvent.Pre event,
        RoomControllerBlockEntity targetController,
        LivingEntity target,
        ServerLevel level
    ) {
        if (targetController != null
            && target instanceof Mob
            && !targetController.isTrackedBoss(level, target)
            && !targetController.isTrackedSummoned(level, target)
            && targetController.isModifierIdActive(level, "shatterproof")) {
            event.setNewDamage(DungeonModifierCombatRules.shatterproofDamageCap(
                event.getNewDamage(), target.getMaxHealth()
            ));
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
            || !(event.getEntity() instanceof Mob target)
            || event.getNewDamage() <= 0.0F) {
            return;
        }
        RoomControllerBlockEntity controller =
            RoomControllerBlockEntity.findActiveControllerTracking(level, target);
        if (controller == null
            || controller.isTrackedBoss(level, target)
            || controller.isTrackedSummoned(level, target)
            || !controller.isModifierIdActive(level, "barbed-hide")
            || !DungeonModifierCombatRules.isKnownDirectMelee(event.getSource(), target)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        float reflectedDamage = DungeonModifierCombatRules.barbedHideReflection(event.getNewDamage());
        if (reflectedDamage <= 0.0F) return;
        try (DungeonDamageRecursionGuard.Scope scope =
                 DungeonDamageRecursionGuard.enter(attacker, target)) {
            if (scope.entered()) {
                attacker.hurt(level.damageSources().thorns(target), reflectedDamage);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        DungeonSubordinateTransportData transport = DungeonSubordinateTransportData.get(level);
        if (transport.markLost(level, dead.getUUID())) {
            transport.bindingForEntity(dead.getUUID())
                .flatMap(reference -> transport.binding(reference.key()))
                .map(DungeonSubordinateTransportData.Binding::hostId)
                .map(level.getServer().getPlayerList()::getPlayer)
                .ifPresent(host -> host.sendSystemMessage(Component.literal(
                    "Subordinate " + dead.getName().getString()
                        + " was lost. The dungeon run continues with the remaining participants."
                )));
        }
        RoomControllerBlockEntity controller =
            RoomControllerBlockEntity.findActiveControllerTracking(level, dead);
        if (controller != null) {
            controller.queueExplosiveDeath(level, dead);
            if (dead instanceof Mob mob && controller.isModifierIdActive(level, "death-echo")) {
                applyDeathEcho(level, controller, mob);
            }
        }
    }

    private static void applyDeathEcho(
        ServerLevel level,
        RoomControllerBlockEntity controller,
        Mob dead
    ) {
        level.sendParticles(
            ParticleTypes.EXPLOSION,
            dead.getX(), dead.getY() + dead.getBbHeight() * 0.5D, dead.getZ(),
            1, 0.0D, 0.0D, 0.0D, 0.0D
        );
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()
                || !player.isAlive()
                || RoomControllerBlockEntity.findActiveControllerForPlayer(level, player) != controller) {
                continue;
            }
            float damage = DungeonModifierCombatRules.deathEchoDamage(player.getMaxHealth());
            if (damage > 0.0F) player.hurt(level.damageSources().magic(), damage);
        }
    }
}
