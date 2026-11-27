package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.DungeonObjectivePayload;
import com.mineclawd.tensuradungeon.network.DungeonCosmeticSnapshotPayload;
import com.mineclawd.tensuradungeon.runtime.DungeonCosmeticData;
import com.mineclawd.tensuradungeon.runtime.DungeonHudHooks;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID)
public final class DungeonObjectiveSyncEvents {
    static final int SYNC_INTERVAL_TICKS = 5;

    private DungeonObjectiveSyncEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
            && player.tickCount % SYNC_INTERVAL_TICKS == 0) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
            syncCosmetics(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
            syncCosmetics(player);
        }
    }

    private static void sync(ServerPlayer player) {
        if (!NetworkRegistry.hasChannel(player.connection, DungeonObjectivePayload.TYPE.id())) return;
        PacketDistributor.sendToPlayer(player, DungeonObjectivePayload.from(
            DungeonHudHooks.current(player.serverLevel(), player)
        ));
    }

    private static void syncCosmetics(ServerPlayer player) {
        if (!NetworkRegistry.hasChannel(player.connection, DungeonCosmeticSnapshotPayload.TYPE.id())) return;
        PacketDistributor.sendToPlayer(player, DungeonCosmeticSnapshotPayload.from(
            DungeonCosmeticData.get(player.serverLevel()).snapshot(player.getUUID()),
            DungeonCosmeticData.MutationStatus.NO_CHANGE,
            ""
        ));
    }
}
