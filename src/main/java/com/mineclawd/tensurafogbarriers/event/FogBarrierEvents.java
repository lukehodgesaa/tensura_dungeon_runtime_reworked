package com.mineclawd.tensurafogbarriers.event;

import com.mineclawd.tensurafogbarriers.TensuraFogBarriers;
import com.mineclawd.tensurafogbarriers.block.GoldenFogBarrierBlock;
import com.mineclawd.tensurafogbarriers.config.FogBarrierConfig;
import com.mineclawd.tensurafogbarriers.registry.ModBlocks;
import com.mineclawd.tensurafogbarriers.service.FogBarrierService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = TensuraFogBarriers.MOD_ID)
public final class FogBarrierEvents {
    private FogBarrierEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        FogBarrierService.get(event.getServer()).reconcileLoadedBackings();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FogBarrierService.unbind(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FogBarrierService.get(player.getServer()).syncPlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FogBarrierService.get(player.getServer()).syncPlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FogBarrierService.get(player.getServer()).syncPlayer(player);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity().getServer() != null) {
            FogBarrierService.get(event.getEntity().getServer()).collide(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
        var projectile = event.getProjectile();
        var state = projectile.level().getBlockState(hit.getBlockPos());
        if (!state.is(ModBlocks.GOLDEN_FOG_BARRIER.get()) || !FogBarrierConfig.REFLECT_PROJECTILES.get()) return;
        if (!projectile.level().isClientSide() && projectile.getServer() != null) {
            FogBarrierService.get(projectile.getServer()).reflectPlacedBlockProjectile(
                projectile, hit, state.getValue(GoldenFogBarrierBlock.AXIS)
            );
        }
        event.setCanceled(true);
    }
}
