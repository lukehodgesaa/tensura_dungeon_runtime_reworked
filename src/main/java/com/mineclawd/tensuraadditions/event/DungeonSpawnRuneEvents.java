package com.mineclawd.tensuraadditions.event;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import com.mineclawd.tensuraadditions.entity.SpawnRuneEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

@EventBusSubscriber(modid = TensuraAdditions.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DungeonSpawnRuneEvents {
    public static final String DUNGEON_SOURCE_KEY = "TensuraDungeonSoulOrbSource";
    public static final String PLAYED_KEY = "TensuraAdditionsSpawnRunePlayed";
    public static final String RESTORE_KEY = "TensuraAdditionsSpawnRuneRestore";
    public static final String PALETTE = "red";

    private DungeonSpawnRuneEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (event.loadedFromDisk()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(DUNGEON_SOURCE_KEY) || persistent.contains(PLAYED_KEY)) {
            return;
        }

        double baseX = mob.getX();
        double baseY = mob.getY();
        double baseZ = mob.getZ();
        double depth = Math.max(
            SpawnRuneEntity.DEFAULT_START_DEPTH,
            mob.getDimensions(Pose.STANDING).height() + 1.0D);
        boolean wasNoAi = mob.isNoAi();
        boolean wasInvulnerable = mob.isInvulnerable();
        boolean wasSilent = mob.isSilent();
        boolean wasNoGravity = mob.isNoGravity();
        boolean wasInvisible = mob.isInvisible();

        persistent.putBoolean(PLAYED_KEY, true);
        CompoundTag restore = new CompoundTag();
        restore.putBoolean("NoAi", wasNoAi);
        restore.putBoolean("Invulnerable", wasInvulnerable);
        restore.putBoolean("Silent", wasSilent);
        restore.putBoolean("NoGravity", wasNoGravity);
        restore.putBoolean("Invisible", wasInvisible);
        persistent.put(RESTORE_KEY, restore);

        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        mob.setNoGravity(true);
        mob.setInvisible(true);
        mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
        mob.moveTo(baseX, baseY - depth, baseZ,
            mob.getYRot(), mob.getXRot());

        MinecraftServer server = serverLevel.getServer();
        server.tell(new TickTask(server.getTickCount() + 1, () -> {
            if (!mob.isRemoved() && mob.isAlive() && mob.level() == serverLevel) {
                SpawnRuneEntity rune = SpawnRuneEntity.create(
                    serverLevel, mob, PALETTE,
                    wasNoAi, wasInvulnerable, wasSilent, wasNoGravity, wasInvisible,
                    baseX, baseY, baseZ, depth);
                if (rune != null && serverLevel.addFreshEntity(rune)) {
                    return;
                }
                restoreAfterFailedStart(
                    mob, wasNoAi, wasInvulnerable, wasSilent, wasNoGravity, wasInvisible,
                    baseX, baseY, baseZ);
            }
        }));
    }

    private static void restoreAfterFailedStart(
            Mob mob,
            boolean wasNoAi,
            boolean wasInvulnerable,
            boolean wasSilent,
            boolean wasNoGravity,
            boolean wasInvisible,
            double baseX,
            double baseY,
            double baseZ) {
        if (mob == null || mob.isRemoved()) {
            return;
        }
        mob.setNoAi(wasNoAi);
        mob.setInvulnerable(wasInvulnerable);
        mob.setSilent(wasSilent);
        mob.setNoGravity(wasNoGravity);
        mob.setInvisible(wasInvisible);
        mob.moveTo(baseX, baseY, baseZ, mob.getYRot(), mob.getXRot());
        mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
        CompoundTag persistent = mob.getPersistentData();
        persistent.remove(PLAYED_KEY);
        persistent.remove(RESTORE_KEY);
    }

    @SubscribeEvent
    public static void onEntityTravel(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        if (!(entity instanceof Mob mob)) {
            return;
        }
        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(RESTORE_KEY)) {
            return;
        }
        CompoundTag restore = persistent.getCompound(RESTORE_KEY);
        mob.setNoAi(restore.getBoolean("NoAi"));
        mob.setInvulnerable(restore.getBoolean("Invulnerable"));
        mob.setSilent(restore.getBoolean("Silent"));
        mob.setNoGravity(restore.getBoolean("NoGravity"));
        mob.setInvisible(restore.getBoolean("Invisible"));
        persistent.remove(RESTORE_KEY);
    }
}
