package com.mineclawd.tensuraadditions.compat.moreskills;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class MoreSkillsCompatNetwork {
    private MoreSkillsCompatNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TensuraAdditions.MOD_ID).versioned("1");
        registrar.playToClient(
                BerettaOverlayPayload.TYPE,
                BerettaOverlayPayload.STREAM_CODEC,
                FMLEnvironment.dist == Dist.CLIENT ? BerettaOverlayClient::handle : null
        );
    }

    public static void sendOverlay(
            ServerLevel level,
            LivingEntity beretta,
            ResourceLocation attackId,
            String animation,
            int durationTicks,
            Vec3 origin,
            Vec3 impactPoint,
            Vec3 direction,
            int seed
    ) {
        sendOverlay(
                level,
                beretta,
                attackId,
                animation,
                durationTicks,
                origin,
                impactPoint,
                direction,
                BerettaMeteorStormProfile.none(),
                seed
        );
    }

    public static void sendOverlay(
            ServerLevel level,
            LivingEntity beretta,
            ResourceLocation attackId,
            String animation,
            int durationTicks,
            Vec3 origin,
            Vec3 impactPoint,
            Vec3 direction,
            BerettaMeteorStormProfile meteorStorm,
            int seed
    ) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                beretta,
                new BerettaOverlayPayload(
                        beretta.getId(),
                        attackId,
                        animation,
                        durationTicks,
                        origin.x,
                        origin.y,
                        origin.z,
                        impactPoint.x,
                        impactPoint.y,
                        impactPoint.z,
                        direction.x,
                        direction.z,
                        meteorStorm,
                        seed
                )
        );
    }
}
