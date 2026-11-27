package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.CoreSatchelData;
import com.mineclawd.tensuradungeon.runtime.CoreSatchelScoreboardSync;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID)
public final class CoreSatchelAccessSync {
    private CoreSatchelAccessSync() {}

    public static CoreSatchelData.GrantResult grantNormal(ServerPlayer player) {
        CoreSatchelData data = CoreSatchelData.get(player.serverLevel());
        CoreSatchelData.GrantResult result = data.grantNormal(player.getUUID());
        send(player, data.snapshot(player.getUUID()));
        CoreSatchelScoreboardSync.sync(player);
        return result;
    }

    public static void send(ServerPlayer player) {
        CoreSatchelData data = CoreSatchelData.get(player.serverLevel());
        send(player, data.snapshot(player.getUUID()));
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        CoreSatchelScoreboardSync.removeObjective(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    private static void send(ServerPlayer player, CoreSatchelData.SatchelSnapshot snapshot) {
        CoreSatchelData.AccessSnapshot access = snapshot.access();
        PacketDistributor.sendToPlayer(player, new CoreSatchelAccessPayload(
            access.acquired(),
            access.gradeName(),
            access.capacity(),
            snapshot.cores().entrySet().stream()
                .map(entry -> new CoreSatchelAccessPayload.CoreEntry(
                    entry.getValue().coreId(),
                    entry.getKey(),
                    entry.getValue().tier(),
                    entry.getValue().totalCharge(),
                    entry.getValue().capacity(),
                    entry.getValue().naturalCharge(),
                    entry.getValue().displayName()
                ))
                .sorted(java.util.Comparator.comparingInt(CoreSatchelAccessPayload.CoreEntry::slot))
                .toList()
        ));
    }
}
