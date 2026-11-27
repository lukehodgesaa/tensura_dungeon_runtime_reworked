package com.mineclawd.tensuraadditions.client;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import com.mineclawd.tensuraadditions.client.particle.SpawnRuneParticle;
import com.mineclawd.tensuraadditions.registry.ModEntities;
import com.mineclawd.tensuraadditions.registry.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(
    modid = TensuraAdditions.MOD_ID,
    bus = EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT
)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            ModEntities.DUNGEON_EXIT_PORTAL.get(), DungeonExitPortalRenderer::new);
        event.registerEntityRenderer(
            ModEntities.SPAWN_RUNE.get(), SpawnRuneRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPAWN_RUNE.get(), SpawnRuneParticle.Provider::new);
    }
}
