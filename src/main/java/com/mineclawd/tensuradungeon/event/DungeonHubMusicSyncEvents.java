package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.DungeonHubMusicStatePayload;
import com.mineclawd.tensuradungeon.registry.ModDimensions;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID)
public final class DungeonHubMusicSyncEvents {
    static final int SYNC_INTERVAL_TICKS = 20;
    private static final Map<UUID, Boolean> LAST_SENT = new HashMap<>();

    private DungeonHubMusicSyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
            && player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            sync(player, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_SENT.remove(player.getUUID());
            sync(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_SENT.remove(player.getUUID());
            sync(player, true);
        }
    }

    private static void sync(ServerPlayer player, boolean force) {
        if (!NetworkRegistry.hasChannel(player.connection, DungeonHubMusicStatePayload.TYPE.id())) {
            return;
        }
        boolean inHub = isInsideHub(player);
        Boolean previous = LAST_SENT.put(player.getUUID(), inHub);
        if (force || previous == null || previous != inHub) {
            PacketDistributor.sendToPlayer(
                player,
                new DungeonHubMusicStatePayload(DungeonHubMusicStatePayload.CURRENT_SCHEMA, inHub)
            );
        }
    }

    static boolean isInsideHub(ServerPlayer player) {
        if (player == null || !player.level().dimension().equals(ModDimensions.DUNGEON)) {
            return false;
        }
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        return manager.isHubEnabled() && manager.isPlayerInsideHubSafeZone(player.blockPosition());
    }
}
