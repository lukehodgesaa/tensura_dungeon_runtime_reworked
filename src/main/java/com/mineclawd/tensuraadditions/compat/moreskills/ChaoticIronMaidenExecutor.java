package com.mineclawd.tensuraadditions.compat.moreskills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ChaoticIronMaidenExecutor implements BerettaAttackExecutor {
    public static final double ATTACK_RANGE = 36.0D;
    private static final double CORRIDOR_RADIUS = 3.75D;
    private static final double IMPACT_RADIUS = 7.0D;

    @Override
    public void start(BerettaAttackContext context) {
        MoreSkillsCompatNetwork.sendOverlay(
                context.level(),
                context.beretta(),
                context.definition().id(),
                context.definition().animation(),
                context.definition().durationTicks(),
                context.origin(),
                context.impactPoint(),
                direction(context),
                context.beretta().getRandom().nextInt()
        );
        playSound(
                context.level(),
                context.origin(),
                SoundEvents.ILLUSIONER_CAST_SPELL,
                1.25F,
                0.55F
        );
    }

    @Override
    public void tick(BerettaAttackContext context) {
        if (context.age() != context.definition().impactTick()) {
            return;
        }

        applyDamage(context);
        playSound(
                context.level(),
                context.impactPoint(),
                SoundEvents.GENERIC_EXPLODE.value(),
                1.5F,
                0.72F
        );
    }

    private static void applyDamage(BerettaAttackContext context) {
        if (context.impactApplied()) {
            return;
        }
        context.markImpactApplied();

        Vec3 origin = context.origin();
        Vec3 impactPoint = context.impactPoint();
        AABB bounds = segmentBounds(origin, impactPoint);
        for (LivingEntity target : context.level().getEntitiesOfClass(
                LivingEntity.class,
                bounds,
                entity -> entity != context.beretta() && entity.isAlive()
        )) {
            if (!isAffected(target.getBoundingBox().getCenter(), origin, impactPoint)) {
                continue;
            }

            float damage = damageAmount(context, target);
            if (damage <= 0.0F || !target.hurt(
                    context.beretta().damageSources().mobAttack(context.beretta()),
                    damage
            )) {
                continue;
            }
            context.markDamaged(target);
        }
    }

    private static AABB segmentBounds(Vec3 origin, Vec3 impactPoint) {
        return new AABB(
                Math.min(origin.x, impactPoint.x) - IMPACT_RADIUS,
                Math.min(origin.y, impactPoint.y) - 4.0D,
                Math.min(origin.z, impactPoint.z) - IMPACT_RADIUS,
                Math.max(origin.x, impactPoint.x) + IMPACT_RADIUS,
                Math.max(origin.y, impactPoint.y) + 4.0D,
                Math.max(origin.z, impactPoint.z) + IMPACT_RADIUS
        );
    }

    private static boolean isAffected(Vec3 point, Vec3 origin, Vec3 impactPoint) {
        if (point.distanceToSqr(impactPoint) <= IMPACT_RADIUS * IMPACT_RADIUS) {
            return true;
        }

        Vec3 segment = impactPoint.subtract(origin);
        double segmentLengthSqr = segment.lengthSqr();
        if (segmentLengthSqr < 1.0E-4D) {
            return false;
        }

        double progress = point.subtract(origin).dot(segment) / segmentLengthSqr;
        if (progress <= 0.0D || progress >= 1.0D) {
            return false;
        }

        Vec3 closest = origin.add(segment.scale(progress));
        return point.distanceToSqr(closest) <= CORRIDOR_RADIUS * CORRIDOR_RADIUS;
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

    private static void playSound(
            ServerLevel level,
            Vec3 position,
            net.minecraft.sounds.SoundEvent sound,
            float volume,
            float pitch
    ) {
        level.playSound(
                null,
                position.x,
                position.y,
                position.z,
                sound,
                SoundSource.HOSTILE,
                volume,
                pitch
        );
    }
}
