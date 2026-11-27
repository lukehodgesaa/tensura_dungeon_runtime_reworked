package com.mineclawd.tensuraadditions.compat.moreskills;

import com.github.wal_bos.moreskills.client.fx.BerettaVfxRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@EventBusSubscriber(
        modid = "tensura_additions",
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT
)
public final class BerettaOverlayClient {
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Map<Integer, ActiveOverlay> ACTIVE = new HashMap<>();

    private BerettaOverlayClient() {
    }

    public static void handle(BerettaOverlayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                ACTIVE.put(
                        payload.entityId(),
                        new ActiveOverlay(
                                payload.attackId(),
                                payload.animation(),
                                payload.durationTicks(),
                                payload.origin(),
                                payload.impactPoint(),
                                payload.direction(),
                                payload.meteorStorm(),
                                payload.seed(),
                                minecraft.level.getGameTime()
                        )
                );
            }
        });
    }

    @SubscribeEvent
    public static void liftCastingBeretta(RenderLivingEvent.Pre<?, ?> event) {
        float lift = liftOffset(event.getEntity().getId());
        if (lift > 0.001F) {
            event.getPoseStack().translate(0.0D, lift, 0.0D);
        }
    }

    private static float liftOffset(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }
        ActiveOverlay overlay = ACTIVE.get(entityId);
        if (overlay == null) {
            return 0.0F;
        }
        float age = (float) (minecraft.level.getGameTime() - overlay.startGameTime());
        return casterLift(age);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }

        BerettaVfxRenderHelper.Frame frame = BerettaVfxRenderHelper.begin(event);
        if (frame == null) {
            return;
        }

        boolean drew = false;
        Iterator<Map.Entry<Integer, ActiveOverlay>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ActiveOverlay> entry = iterator.next();
            ActiveOverlay overlay = entry.getValue();
            float age = BerettaVfxRenderHelper.age(frame, overlay.startGameTime());
            if (age > overlay.durationTicks() + 4.0F) {
                iterator.remove();
                continue;
            }

            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive()) {
                iterator.remove();
                continue;
            }

            drew |= overlay.meteorStorm().enabled()
                    ? renderMeteorStorm(frame, overlay, age)
                    : renderIronMaiden(frame, overlay, age);
        }

        BerettaVfxRenderHelper.end(frame, drew);
    }

    private static boolean renderIronMaiden(
            BerettaVfxRenderHelper.Frame frame,
            ActiveOverlay overlay,
            float age
    ) {
        if (!overlay.attackId().getPath().endsWith("chaotic_iron_maiden")) {
            return false;
        }

        Vec3 source = overlay.origin();
        Vec3 impact = overlay.impactPoint();
        Vec3 direction = overlay.direction();
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        Vec3 caster = source.add(UP.scale(casterLift(age)));
        Vec3 core = caster.add(UP.scale(2.35D));
        boolean drew = false;

        float pulse = 0.72F + 0.28F * (float) Math.sin(age * 0.72D);
        float charge = smooth(clamp(age / 20.0F));

        drew |= BerettaVfxRenderHelper.runeRing(
                frame,
                caster.add(0.0D, 0.05D, 0.0D),
                2.7D + charge * 0.7D,
                -age * 0.12D,
                72,
                0.88F,
                0.12F,
                1.0F,
                0.82F
        );
        drew |= BerettaVfxRenderHelper.freeRing(
                frame,
                core.add(direction.scale(-0.25D)),
                side,
                UP,
                2.6D + charge * 0.55D,
                age * 0.11D,
                72,
                0.74F,
                0.08F,
                1.0F,
                0.78F
        );
        drew |= BerettaVfxRenderHelper.orb(
                frame,
                core,
                0.68D + pulse * 0.16D,
                0.84F,
                0.12F,
                1.0F,
                0.94F
        );
        drew |= BerettaVfxRenderHelper.bloomOrb(
                frame,
                core,
                1.15D + charge * 0.35D,
                age,
                overlay.seed(),
                0.9F
        );
        drew |= BerettaVfxRenderHelper.sparkField(
                frame,
                core,
                3.4D + charge * 1.2D,
                age,
                overlay.seed(),
                28,
                0.9F
        );
        drew |= BerettaVfxRenderHelper.verticalRay(
                frame,
                core,
                0.32D,
                3.0D + charge * 2.0D,
                0.76F,
                overlay.seed(),
                0.64F + charge * 0.3F
        );

        if (age >= 12.0F && age < 36.0F) {
            float warningAlpha = 0.7F + 0.25F * (float) Math.sin(age * 0.9D);
            for (int lane = -3; lane <= 3; lane++) {
                Vec3 laneStart = source.add(side.scale(lane * 0.95D));
                Vec3 laneEnd = impact.add(side.scale(lane * 0.72D));
                drew |= BerettaVfxRenderHelper.lane(
                        frame,
                        laneStart,
                        laneEnd,
                        0.24D + Math.abs(lane) * 0.025D,
                        0.96F,
                        0.14F,
                        1.0F,
                        warningAlpha
                );
            }
        }

        if (age >= 8.0F && age < 35.0F) {
            for (int projectile = -3; projectile <= 3; projectile++) {
                double offset = projectile * 0.72D;
                Vec3 launch = core.add(side.scale(offset * 0.45D));
                Vec3 landing = impact.add(UP.scale(0.9D)).add(side.scale(offset * 0.48D));
                float stagger = (projectile + 3) * 0.65F;
                float progress = clamp((age - 10.0F - stagger) / 17.0F);
                Vec3 current = lerp(launch, landing, progress);
                Vec3 previous = lerp(launch, current, Math.max(0.0D, progress - 0.18D));

                drew |= BerettaVfxRenderHelper.beam(
                        frame,
                        previous,
                        current,
                        0.22D,
                        1.0F,
                        0.2F,
                        1.0F,
                        0.82F
                );
                drew |= BerettaVfxRenderHelper.orb(
                        frame,
                        current,
                        0.36D + pulse * 0.08D,
                        0.98F,
                        0.2F,
                        1.0F,
                        0.98F
                );
                drew |= BerettaVfxRenderHelper.bloomOrb(
                        frame,
                        current,
                        0.7D,
                        age + projectile,
                        overlay.seed() + projectile,
                        0.88F
                );
                drew |= BerettaVfxRenderHelper.shard(
                        frame,
                        current,
                        direction,
                        0.3D,
                        1.5D,
                        0.9F,
                        0.12F,
                        1.0F,
                        0.95F
                );
            }
        }

        if (age >= 16.0F && age < 34.0F) {
            float beamAlpha = 0.45F + 0.4F * clamp((age - 16.0F) / 8.0F);
            drew |= BerettaVfxRenderHelper.bloomBeam(
                    frame,
                    core,
                    impact.add(UP.scale(0.9D)),
                    0.3D,
                    1.0F,
                    overlay.seed(),
                    beamAlpha
            );
        }

        if (age >= 32.0F && age < 60.0F) {
            float impactAge = age - 32.0F;
            float impactProgress = smooth(clamp(impactAge / 8.0F));
            float alpha = 1.0F - clamp(impactAge / 28.0F);
            Vec3 blast = impact.add(0.0D, 0.08D, 0.0D);

            drew |= BerettaVfxRenderHelper.warningCircle(
                    frame,
                    blast,
                    7.5D * Math.max(impactProgress, 0.08F),
                    pulse,
                    alpha
            );
            drew |= BerettaVfxRenderHelper.disk(
                    frame,
                    blast,
                    7.2D * impactProgress,
                    impactAge * 0.08D,
                    72,
                    0.86F,
                    0.1F,
                    1.0F,
                    alpha * 0.72F
            );
            drew |= BerettaVfxRenderHelper.shockwaveBloom(
                    frame,
                    blast,
                    9.0D * (0.35D + impactProgress),
                    impactAge,
                    overlay.seed() + 1000,
                    0.9F
            );
            drew |= BerettaVfxRenderHelper.runeRing(
                    frame,
                    blast,
                    4.5D + impactProgress * 3.0D,
                    -impactAge * 0.18D,
                    64,
                    0.92F,
                    0.16F,
                    1.0F,
                    alpha
            );
            drew |= BerettaVfxRenderHelper.verticalRay(
                    frame,
                    blast,
                    0.62D,
                    5.0D * impactProgress + 0.4D,
                    0.94F,
                    overlay.seed() + 900,
                    alpha
            );

            for (int spike = 0; spike <= 8; spike++) {
                double along = spike / 8.0D;
                double lateral = Math.sin((spike + overlay.seed()) * 1.7D) * 0.95D;
                Vec3 position = lerp(source, impact, along).add(side.scale(lateral));
                double height = (2.8D + (spike % 3) * 0.7D) * impactProgress + 0.25D;
                drew |= BerettaVfxRenderHelper.pillar(
                        frame,
                        position,
                        0.56D,
                        height,
                        0.86F,
                        0.12F,
                        1.0F,
                        alpha
                );
                drew |= BerettaVfxRenderHelper.impactBloom(
                        frame,
                        position,
                        0.95D,
                        impactAge + spike * 0.4F,
                        overlay.seed() + spike,
                        alpha
                );
            }

            for (int ray = 0; ray < 8; ray++) {
                Vec3 radial = BerettaVfxRenderHelper.rotate(direction, ray * Math.PI / 4.0D);
                drew |= BerettaVfxRenderHelper.beam(
                        frame,
                        blast,
                        blast.add(radial.scale(8.0D * impactProgress)),
                        0.22D,
                        0.94F,
                        0.18F,
                        1.0F,
                        alpha * 0.9F
                );
            }
        }

        return drew;
    }

    private static boolean renderMeteorStorm(
            BerettaVfxRenderHelper.Frame frame,
            ActiveOverlay overlay,
            float age
    ) {
        BerettaMeteorStormProfile profile = overlay.meteorStorm();
        if (!profile.enabled()) {
            return false;
        }

        Vec3 source = overlay.origin();
        Vec3 direction = overlay.direction();
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        int wholeAge = Math.max(0, (int) age);
        int currentPhase = profile.phaseAt(wholeAge);
        Vec3 currentCenter = BerettaMeteorStormMath.phaseCenter(
                overlay.impactPoint(),
                direction,
                profile,
                currentPhase
        );
        boolean drew = false;

        Vec3 caster = source.add(UP.scale(casterLift(age)));
        Vec3 core = caster.add(UP.scale(2.35D));
        float casterPulse = 0.75F + 0.25F * (float) Math.sin(age * 0.72D);
        drew |= BerettaVfxRenderHelper.runeRing(
                frame,
                caster.add(0.0D, 0.05D, 0.0D),
                2.8D,
                -age * 0.1D,
                72,
                0.9F,
                0.1F,
                1.0F,
                0.78F
        );
        drew |= BerettaVfxRenderHelper.freeRing(
                frame,
                core.add(direction.scale(-0.3D)),
                side,
                UP,
                2.65D,
                age * 0.12D,
                72,
                0.75F,
                0.08F,
                1.0F,
                0.72F
        );
        drew |= BerettaVfxRenderHelper.orb(
                frame,
                core,
                0.72D + casterPulse * 0.14D,
                0.88F,
                0.12F,
                1.0F,
                0.94F
        );
        drew |= BerettaVfxRenderHelper.verticalRay(
                frame,
                core,
                0.32D,
                4.0D,
                0.78F,
                overlay.seed(),
                0.7F
        );

        double halfWidth = profile.width() * 0.5D;
        double halfLength = profile.length() * 0.5D;
        Vec3 cornerA = currentCenter.add(side.scale(halfWidth)).add(direction.scale(halfLength));
        Vec3 cornerB = currentCenter.add(side.scale(-halfWidth)).add(direction.scale(halfLength));
        Vec3 cornerC = currentCenter.add(side.scale(-halfWidth)).add(direction.scale(-halfLength));
        Vec3 cornerD = currentCenter.add(side.scale(halfWidth)).add(direction.scale(-halfLength));
        float boundaryPulse = 0.55F + 0.3F * (float) Math.sin(age * 0.28D);
        drew |= BerettaVfxRenderHelper.lane(frame, cornerA, cornerB, 0.18D, 0.7F, 0.12F, 1.0F, boundaryPulse);
        drew |= BerettaVfxRenderHelper.lane(frame, cornerB, cornerC, 0.18D, 0.7F, 0.12F, 1.0F, boundaryPulse);
        drew |= BerettaVfxRenderHelper.lane(frame, cornerC, cornerD, 0.18D, 0.7F, 0.12F, 1.0F, boundaryPulse);
        drew |= BerettaVfxRenderHelper.lane(frame, cornerD, cornerA, 0.18D, 0.7F, 0.12F, 1.0F, boundaryPulse);

        int phaseAge = wholeAge - currentPhase * profile.phaseDurationTicks();
        if (phaseAge >= 0 && phaseAge < 18) {
            float transitionAlpha = 1.0F - clamp(phaseAge / 18.0F);
            drew |= BerettaVfxRenderHelper.shockwaveBloom(
                    frame,
                    currentCenter,
                    22.0D,
                    phaseAge,
                    overlay.seed() + currentPhase,
                    transitionAlpha
            );
            drew |= BerettaVfxRenderHelper.runeRing(
                    frame,
                    currentCenter,
                    13.0D + phaseAge * 0.45D,
                    phaseAge * 0.08D,
                    96,
                    0.96F,
                    0.14F,
                    1.0F,
                    transitionAlpha
            );
        }

        int firstWave = Math.max(0, (int) Math.floor((age - profile.fallTicks()) / profile.meteorIntervalTicks()) - 1);
        int lastWave = Math.max(firstWave, (int) Math.ceil((age + profile.fallTicks()) / profile.meteorIntervalTicks()));
        for (int wave = firstWave; wave <= lastWave; wave++) {
            int impactAge = (int) BerettaMeteorStormMath.impactAge(profile, wave);
            if (impactAge >= overlay.durationTicks() || impactAge < 0) {
                continue;
            }

            int impactPhase = profile.phaseAt(impactAge);
            Vec3 impactCenter = BerettaMeteorStormMath.phaseCenter(
                    overlay.impactPoint(),
                    direction,
                    profile,
                    impactPhase
            );
            for (int meteor = 0; meteor < profile.meteorsPerWave(); meteor++) {
                int meteorIndex = wave * profile.meteorsPerWave() + meteor;
                Vec3 impact = BerettaMeteorStormMath.meteorImpact(
                        impactCenter,
                        direction,
                        profile,
                        impactPhase,
                        meteorIndex,
                        overlay.seed()
                );
                int timeToImpact = impactAge - wholeAge;

                if (timeToImpact >= 0 && timeToImpact <= profile.fallTicks()) {
                    float previewAlpha = 0.45F + 0.45F * (float) Math.sin((age + meteorIndex) * 0.8D);
                    drew |= BerettaVfxRenderHelper.warningCircle(
                            frame,
                            impact,
                            profile.previewRadius() * (0.86D + 0.14D * casterPulse),
                            casterPulse,
                            previewAlpha
                    );
                    drew |= BerettaVfxRenderHelper.runeRing(
                            frame,
                            impact,
                            profile.previewRadius() * 0.7D,
                            age * 0.18D + meteorIndex,
                            24,
                            0.94F,
                            0.16F,
                            1.0F,
                            previewAlpha * 0.72F
                    );
                }

                if (timeToImpact >= 0 && timeToImpact <= profile.fallTicks()) {
                    float fallProgress = clamp(
                            (age - (impactAge - profile.fallTicks())) / (float) profile.fallTicks()
                    );
                    Vec3 start = impact.add(UP.scale(profile.dropHeight()));
                    Vec3 current = lerp(start, impact, fallProgress);
                    Vec3 previous = lerp(start, current, Math.max(0.0D, fallProgress - 0.14D));
                    drew |= BerettaVfxRenderHelper.beam(
                            frame,
                            previous,
                            current,
                            0.16D,
                            0.78F,
                            0.1F,
                            1.0F,
                            0.84F
                    );
                    drew |= BerettaVfxRenderHelper.orb(
                            frame,
                            current,
                            0.28D + casterPulse * 0.06D,
                            0.9F,
                            0.1F,
                            1.0F,
                            0.94F
                    );
                    drew |= BerettaVfxRenderHelper.bloomOrb(
                            frame,
                            current,
                            0.55D,
                            age,
                            overlay.seed() + meteorIndex,
                            0.82F
                    );
                    drew |= BerettaVfxRenderHelper.shard(
                            frame,
                            current,
                            new Vec3(0.0D, -1.0D, 0.0D),
                            0.22D,
                            1.15D,
                            0.94F,
                            0.14F,
                            1.0F,
                            0.9F
                    );
                }

                if (timeToImpact < 0 && timeToImpact >= -6) {
                    float impactAlpha = 1.0F - clamp((-timeToImpact) / 6.0F);
                    drew |= BerettaVfxRenderHelper.impactBloom(
                            frame,
                            impact,
                            profile.impactRadius() * 1.25D,
                            -timeToImpact,
                            overlay.seed() + meteorIndex,
                            impactAlpha
                    );
                }
            }
        }

        return drew;
    }

    private static float casterLift(float age) {
        if (age < 8.0F) {
            return 1.35F * smooth(clamp(age / 8.0F));
        }
        if (age < 23.0F) {
            return 1.35F;
        }
        if (age < 32.0F) {
            return 1.35F * (1.0F - smooth(clamp((age - 23.0F) / 9.0F)));
        }
        return 0.0F;
    }

    private static Vec3 lerp(Vec3 start, Vec3 end, double progress) {
        return start.add(end.subtract(start).scale(progress));
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record ActiveOverlay(
            ResourceLocation attackId,
            String animation,
            int durationTicks,
            Vec3 origin,
            Vec3 impactPoint,
            Vec3 direction,
            BerettaMeteorStormProfile meteorStorm,
            int seed,
            long startGameTime
    ) {
        private ActiveOverlay {
            direction = new Vec3(direction.x, 0.0D, direction.z);
            direction = direction.lengthSqr() < 1.0E-4D
                    ? new Vec3(0.0D, 0.0D, 1.0D)
                    : direction.normalize();
        }
    }
}
