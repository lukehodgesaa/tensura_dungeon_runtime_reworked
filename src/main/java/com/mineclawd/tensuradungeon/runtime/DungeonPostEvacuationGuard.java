package com.mineclawd.tensuradungeon.runtime;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public final class DungeonPostEvacuationGuard {
    static final int PROTECTION_TICKS = 200;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Guard> ACTIVE = new HashMap<>();

    private DungeonPostEvacuationGuard() {
    }

    public static void arm(
        ServerPlayer player,
        DungeonInstanceAllocationData.InstanceKey key
    ) {
        if (player == null || key == null) return;
        long armedAt = player.serverLevel().getGameTime();
        ACTIVE.put(player.getUUID(), new Guard(
            key, player.serverLevel().dimension(), armedAt, armedAt + PROTECTION_TICKS
        ));
        stabilize(player);
        LOGGER.info(
            "[TD-BRACELET] result=POST_EVACUATION_GUARD_ARMED participant={} instance={} epoch={} dimension={} expiresAt={}",
            player.getUUID(), key.instanceId(), key.epoch(),
            player.serverLevel().dimension().location(), armedAt + PROTECTION_TICKS
        );
    }

    public static boolean suppressDamage(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player)) return false;
        Guard guard = activeGuard(player);
        if (guard == null) return false;
        stabilize(player);
        LOGGER.warn(
            "[TD-BRACELET] result=POST_EVACUATION_DAMAGE_SUPPRESSED participant={} instance={} epoch={} source={} remainingTicks={}",
            player.getUUID(), guard.key().instanceId(), guard.key().epoch(),
            source == null ? "UNKNOWN" : source.getMsgId(),
            Math.max(0L, guard.expiresAt() - player.serverLevel().getGameTime())
        );
        return true;
    }

    public static boolean suppressDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player)) return false;
        Guard guard = activeGuard(player);
        if (guard == null) return false;
        stabilize(player);
        LOGGER.warn(
            "[TD-BRACELET] result=POST_EVACUATION_DEATH_SUPPRESSED participant={} instance={} epoch={} source={} remainingTicks={}",
            player.getUUID(), guard.key().instanceId(), guard.key().epoch(),
            source == null ? "UNKNOWN" : source.getMsgId(),
            Math.max(0L, guard.expiresAt() - player.serverLevel().getGameTime())
        );
        return true;
    }

    public static void tick(ServerPlayer player) {
        Guard guard = activeGuard(player);
        if (guard == null) return;
        stabilize(player);
    }

    public static void clear(ServerPlayer player, String reason) {
        if (player == null) return;
        Guard removed = ACTIVE.remove(player.getUUID());
        if (removed != null) {
            LOGGER.info(
                "[TD-BRACELET] result=POST_EVACUATION_GUARD_CLEARED participant={} instance={} epoch={} reason={}",
                player.getUUID(), removed.key().instanceId(), removed.key().epoch(), reason
            );
        }
    }

    private static Guard activeGuard(ServerPlayer player) {
        if (player == null) return null;
        Guard guard = ACTIVE.get(player.getUUID());
        if (guard == null) return null;
        long gameTime = player.serverLevel().getGameTime();
        if (!guard.dimension().equals(player.serverLevel().dimension())
            || !withinProtectionWindow(gameTime, guard.expiresAt())) {
            clear(player, guard.dimension().equals(player.serverLevel().dimension())
                ? "BOUNDED_WINDOW_COMPLETE"
                : "DESTINATION_DIMENSION_CHANGED");
            return null;
        }
        return guard;
    }

    private static void stabilize(ServerPlayer player) {
        DungeonSubordinateProtectionService.stabilize(player);
        DungeonSubordinateProtectionService.clearHarmfulEffects(player);
    }

    static boolean withinProtectionWindow(long gameTime, long expiresAt) {
        return gameTime <= expiresAt;
    }

    private record Guard(
        DungeonInstanceAllocationData.InstanceKey key,
        ResourceKey<Level> dimension,
        long armedAt,
        long expiresAt
    ) {
    }
}
