package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.KurobeForgeCoreTechniqueService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class KurobeForgeCoreTechniqueEvents {
    private KurobeForgeCoreTechniqueEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        KurobeForgeCoreTechniqueService.Result result =
            KurobeForgeCoreTechniqueService.activate(player, event.getItemStack());
        if (!result.handled()) return;
        event.setCanceled(true);
        event.setCancellationResult(
            result.status() == KurobeForgeCoreTechniqueService.ResultStatus.ACTIVATED
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL
        );
        if (!result.message().isBlank()) {
            player.displayClientMessage(Component.literal(result.message()), true);
        }
    }
}
