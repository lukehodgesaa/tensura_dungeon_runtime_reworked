package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.DungeonReturnWhistleChannel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonReturnWhistleEvents {
    private DungeonReturnWhistleEvents() {
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
            && DungeonReturnWhistleChannel.handleUse(player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonReturnWhistleChannel.tick(player);
        }
    }

    @SubscribeEvent
    public static void onDamageTaken(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0.0F) {
            DungeonReturnWhistleChannel.interruptIfChanneling(player, "damage was taken");
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonReturnWhistleChannel.interruptIfChanneling(player, "an attack was attempted");
        }
    }

    @SubscribeEvent
    public static void onBreakAttempt(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonReturnWhistleChannel.interruptIfChanneling(player, "an attack was attempted");
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            DungeonReturnWhistleChannel.interruptIfChanneling(player, "the player died");
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        interrupt(event.getEntity(), "the player disconnected");
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        interrupt(event.getEntity(), "the active dungeon was left");
    }

    private static void interrupt(Entity entity, String reason) {
        if (entity instanceof ServerPlayer player) {
            DungeonReturnWhistleChannel.interruptIfChanneling(player, reason);
        }
    }
}
