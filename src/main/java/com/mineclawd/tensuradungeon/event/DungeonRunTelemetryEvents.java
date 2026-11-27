package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.SoulOrbMobIdentity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonRunTelemetryEvents {
    private DungeonRunTelemetryEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
            || event.getNewDamage() <= 0.0F) {
            return;
        }

        DungeonFloorProgressData progress = DungeonFloorProgressData.get(level);
        if (event.getEntity() instanceof ServerPlayer player) {
            String sessionId = activeSession(level, player);
            if (sessionId != null) {
                progress.recordDamageTaken(player.getUUID(), sessionId, event.getNewDamage());
            }
        }

        if (!(event.getEntity() instanceof Mob mob)) return;
        SoulOrbMobIdentity identity = SoulOrbMobIdentity.read(mob).orElse(null);
        if (identity == null) return;
        ServerPlayer player = creditedPlayer(level, mob, identity, event.getSource().getEntity());
        if (player != null) {
            progress.recordDamageDealt(
                player.getUUID(), identity.sessionId(), event.getNewDamage()
            );
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)
            || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        SoulOrbMobIdentity identity = SoulOrbMobIdentity.read(mob).orElse(null);
        if (identity == null) return;
        ServerPlayer player = creditedPlayer(level, mob, identity, event.getSource().getEntity());
        if (player == null) return;

        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        String displayName = mob.getType().getDescription().getString();
        if (displayName.isBlank()) displayName = entityTypeId.getPath();
        DungeonFloorProgressData.get(level).recordEnemyDefeated(
            player.getUUID(), identity.sessionId(), entityTypeId.toString(), displayName
        );
    }

    static ServerPlayer creditedPlayer(
        ServerLevel level,
        Mob mob,
        SoulOrbMobIdentity identity,
        Entity responsible
    ) {
        if (!(responsible instanceof ServerPlayer player)
            || player.serverLevel() != level
            || !identity.sessionId().equals(activeSession(level, player))) {
            return null;
        }
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(level).forPlayer(player.getUUID())
                .map(DungeonInstanceAllocationData.Allocation::key)
                .orElse(null);
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (!manager.isRoomRegisteredActive(instanceKey, identity.controllerPos())
            || !(level.getBlockEntity(identity.controllerPos())
                instanceof RoomControllerBlockEntity controller)
            || instanceKey != null && !controller.matchesDungeonInstance(instanceKey)) {
            return null;
        }
        RoomControllerBlockEntity.RoomBounds bounds = controller.protectionView(level).bounds();
        return bounds != null && bounds.contains(player.blockPosition()) ? player : null;
    }

    private static String activeSession(ServerLevel level, ServerPlayer player) {
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(level).forPlayer(player.getUUID())
                .map(DungeonInstanceAllocationData.Allocation::key)
                .orElse(null);
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (!manager.isActiveRun(instanceKey) || manager.isRunCompleted(instanceKey)) return null;
        String sessionId = manager.getRunSessionId(instanceKey);
        return sessionId == null || sessionId.isBlank() || "NONE".equals(sessionId)
            ? null
            : sessionId;
    }
}
