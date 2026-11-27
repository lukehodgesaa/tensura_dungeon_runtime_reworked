package com.mineclawd.tensuraadditions.compat.moreskills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VoidMeteorStormExecutor implements BerettaAttackExecutor {
    @Override
    public void start(BerettaAttackContext context) {
        BerettaMeteorStormProfile profile = context.definition().meteorStorm();
        MoreSkillsCompatNetwork.sendOverlay(
                context.level(),
                context.beretta(),
                context.definition().id(),
                context.definition().animation(),
                context.definition().durationTicks(),
                context.origin(),
                context.impactPoint(),
                direction(context),
                profile,
                context.seed()
        );
        context.level().playSound(
                null,
                context.origin().x,
                context.origin().y,
                context.origin().z,
                SoundEvents.ILLUSIONER_CAST_SPELL,
                SoundSource.HOSTILE,
                1.5F,
                0.42F
        );
    }

    @Override
    public void tick(BerettaAttackContext context) {
        BerettaMeteorStormProfile profile = context.definition().meteorStorm();
        if (!profile.enabled() || context.age() < profile.fallTicks()) {
            return;
        }

        int relativeAge = context.age() - profile.fallTicks();
        if (relativeAge % profile.meteorIntervalTicks() != 0) {
            return;
        }

        int wave = relativeAge / profile.meteorIntervalTicks();
        int phase = profile.phaseAt(context.age());
        Vec3 direction = direction(context);
        Vec3 center = BerettaMeteorStormMath.phaseCenter(
                context.impactPoint(),
                direction,
                profile,
                phase
        );

        for (int meteor = 0; meteor < profile.meteorsPerWave(); meteor++) {
            int meteorIndex = wave * profile.meteorsPerWave() + meteor;
            if (context.hasProcessedImpact(meteorIndex)) {
                continue;
            }
            context.markProcessedImpact(meteorIndex);

            Vec3 impact = BerettaMeteorStormMath.meteorImpact(
                    center,
                    direction,
                    profile,
                    phase,
                    meteorIndex,
                    context.seed()
            );
            applyMeteorDamage(context, impact, profile.impactRadius());

            if (meteorIndex % 6 == 0) {
                context.level().playSound(
                        null,
                        impact.x,
                        impact.y,
                        impact.z,
                        SoundEvents.GENERIC_EXPLODE.value(),
                        SoundSource.HOSTILE,
                        0.4F,
                        1.15F
                );
            }
        }
    }

    private static void applyMeteorDamage(
            BerettaAttackContext context,
            Vec3 impact,
            double radius
    ) {
        AABB bounds = new AABB(impact, impact).inflate(radius, 3.0D, radius);
        for (LivingEntity target : context.level().getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity != context.beretta() && entity.isAlive()
        )) {
            if (target.getBoundingBox().getCenter().distanceToSqr(impact) > radius * radius) {
                continue;
            }

            float damage = damageAmount(context, target);
            if (damage > 0.0F && target.hurt(
                    context.beretta().damageSources().mobAttack(context.beretta()),
                    damage
            )) {
                context.markDamaged(target);
            }
        }
    }

    private static float damageAmount(BerettaAttackContext context, LivingEntity target) {
        BerettaDamageProfile profile = context.definition().damage();
        double amount = switch (profile.mode()) {
            case NONE -> 0.0D;
            case FLAT -> profile.amount();
            case MAX_HEALTH_PERCENT -> target.getMaxHealth() * profile.amount();
            case CURRENT_HEALTH_PERCENT -> target.getHealth() * profile.amount();
        };
        if (profile.cap() > 0.0D) {
            amount = Math.min(amount, profile.cap());
        }
        return (float) Math.max(0.0D, amount);
    }

    private static Vec3 direction(BerettaAttackContext context) {
        Vec3 direction = context.impactPoint().subtract(context.origin());
        direction = new Vec3(direction.x, 0.0D, direction.z);
        return direction.lengthSqr() < 1.0E-4D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : direction.normalize();
    }
}
