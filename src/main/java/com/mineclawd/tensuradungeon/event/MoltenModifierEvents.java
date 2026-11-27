package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierCombatRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class MoltenModifierEvents {
    public static final int PLAYER_FIRE_TICKS = DungeonModifierCombatRules.MOLTEN_PLAYER_FIRE_TICKS;

    private MoltenModifierEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
            || attacker == target) {
            RoomControllerBlockEntity.recordGlobalMoltenDamageEventSeen(level);
            RoomControllerBlockEntity.recordGlobalMoltenRejection(level, "NO_LIVING_ATTACKER");
            return;
        }

        RoomControllerBlockEntity controller = RoomControllerBlockEntity.findActiveControllerTracking(level, attacker);
        if (controller != null) {
            controller.recordMoltenDamageEventSeen();
            if (!controller.isMoltenModifierActive(level)) {
                controller.recordMoltenRejection("MODIFIER_NOT_ACTIVE");
                return;
            }
            if (!(target instanceof Player)) {
                controller.recordMoltenRejection("TARGET_NOT_PLAYER");
                return;
            }

            controller.recordMoltenDamageEventWithLivingAttacker();
            target.setRemainingFireTicks(
                Math.max(target.getRemainingFireTicks(), PLAYER_FIRE_TICKS)
            );
            controller.recordMoltenRetaliation(attacker);
            return;
        }

        controller = RoomControllerBlockEntity.findActiveControllerTracking(level, target);
        if (controller == null) {
            RoomControllerBlockEntity.recordGlobalMoltenDamageEventSeen(level);
            RoomControllerBlockEntity.recordGlobalMoltenRejection(level, "TARGET_NOT_TRACKED");
            return;
        }

        controller.recordMoltenDamageEventSeen();
        if (!controller.isMoltenModifierActive(level)) {
            controller.recordMoltenRejection("MODIFIER_NOT_ACTIVE");
            return;
        }
        if (controller.isTrackedBoss(level, target)) {
            controller.recordMoltenRejection("TARGET_BOSS_EXCLUDED");
            return;
        }

        controller.recordMoltenDamageEventWithLivingAttacker();
        controller.recordMoltenTrackedTargetEvent();
        attacker.setRemainingFireTicks(
            Math.max(attacker.getRemainingFireTicks(), PLAYER_FIRE_TICKS)
        );
        controller.recordMoltenRetaliation(attacker);
    }
}
