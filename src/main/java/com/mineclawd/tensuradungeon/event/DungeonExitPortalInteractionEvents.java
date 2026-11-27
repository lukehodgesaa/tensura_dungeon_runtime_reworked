package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.FloorExitPortalBlock;
import com.mineclawd.tensuradungeon.compat.tensuraadditions.DungeonExitPortalVisual;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID)
public final class DungeonExitPortalInteractionEvents {
    static final int PROMPT_INTERVAL_TICKS = 5;

    private static final Component INTERACT_PROMPT = Component.translatable(
        "message.tensura_dungeon.exit_portal_interact"
    ).withStyle(ChatFormatting.AQUA);
    private static final Set<UUID> PROMPT_VISIBLE = new HashSet<>();

    private DungeonExitPortalInteractionEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || player.tickCount % PROMPT_INTERVAL_TICKS != 0) {
            return;
        }

        boolean inside = readyPortalForPlayer(player) != null;
        UUID playerId = player.getUUID();
        if (inside) {
            player.displayClientMessage(INTERACT_PROMPT, true);
            PROMPT_VISIBLE.add(playerId);
        } else if (PROMPT_VISIBLE.remove(playerId)) {
            player.displayClientMessage(Component.empty(), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player
            && player.level() instanceof ServerLevel level
            && event.getHand() == InteractionHand.MAIN_HAND) {
            InteractionResult result = openReadyPortal(level, player);
            if (result != null) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
            && player.level() instanceof ServerLevel level
            && event.getHand() == InteractionHand.MAIN_HAND) {
            InteractionResult result = openReadyPortal(level, player);
            if (result != null) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player
            && player.level() instanceof ServerLevel level
            && event.getHand() == InteractionHand.MAIN_HAND) {
            InteractionResult result = openReadyPortal(level, player);
            if (result != null) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player
            && player.level() instanceof ServerLevel level
            && event.getHand() == InteractionHand.MAIN_HAND) {
            InteractionResult result = openReadyPortal(level, player);
            if (result != null) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PROMPT_VISIBLE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
            && PROMPT_VISIBLE.remove(player.getUUID())) {
            player.displayClientMessage(Component.empty(), true);
        }
    }

    public static boolean openMenuFromClientIntent(ServerPlayer player) {
        BlockPos portalPos = readyPortalForPlayer(player);
        if (portalPos == null) {
            return false;
        }
        FloorExitPortalBlock.openMenu(player.serverLevel(), player, portalPos);
        return true;
    }

    private static InteractionResult openReadyPortal(ServerLevel level, ServerPlayer player) {
        BlockPos portalPos = readyPortalForPlayer(player);
        return portalPos == null ? null : FloorExitPortalBlock.openMenu(level, player, portalPos);
    }

    private static BlockPos readyPortalForPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            playerInstanceKey(level, player);
        BlockPos portalPos = instanceKey == null
            ? manager.getExitPortalPos()
            : manager.getExitPortalPos(instanceKey);
        boolean active = instanceKey == null
            ? manager.isExitPortalActive()
            : manager.isExitPortalActive(instanceKey);
        String runSessionId = instanceKey == null
            ? manager.getRunSessionId()
            : manager.getRunSessionId(instanceKey);
        if (!active || portalPos == null) {
            return null;
        }

        DungeonExitPortalVisual.ensurePresent(level, portalPos, runSessionId);
        String blockReason = instanceKey == null
            ? manager.getExitPortalInteractionBlockReason(level, player, portalPos)
            : manager.getExitPortalInteractionBlockReason(
                instanceKey, level, player, portalPos
            );
        return "NONE".equals(blockReason)
            && FloorExitPortalBlock.containsInteractionVolume(level, portalPos, player)
            ? portalPos
            : null;
    }

    private static DungeonInstanceAllocationData.InstanceKey playerInstanceKey(
        ServerLevel level,
        ServerPlayer player
    ) {
        return DungeonInstanceAllocationData.get(level)
            .forPlayer(player.getUUID())
            .map(DungeonInstanceAllocationData.Allocation::key)
            .orElse(null);
    }
}
