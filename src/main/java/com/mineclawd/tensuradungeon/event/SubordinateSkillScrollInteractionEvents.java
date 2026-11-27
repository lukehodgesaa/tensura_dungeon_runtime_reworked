package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.item.SubordinateSkillScrollItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class SubordinateSkillScrollInteractionEvents {
    private SubordinateSkillScrollInteractionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = handle(event, event.getTarget());
        if (result != null) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        InteractionResult result = handle(event, event.getTarget());
        if (result != null) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    private static InteractionResult handle(PlayerInteractEvent event, Entity target) {
        if (!(target instanceof LivingEntity livingEntity)) {
            return null;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SubordinateSkillScrollItem)
            || !SubordinateSkillScrollItem.isScroll(stack)) {
            return null;
        }

        InteractionResult result = InteractionResult.SUCCESS;
        if (!event.getEntity().level().isClientSide) {
            result = SubordinateSkillScrollItem.teach(
                stack, event.getEntity(), livingEntity, event.getHand()
            );
        }
        return result;
    }
}
