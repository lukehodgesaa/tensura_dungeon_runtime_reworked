package com.mineclawd.tensuraadditions.compat.moreskills;

import net.minecraft.world.phys.Vec3;

public final class BerettaMeteorStormMath {
    private BerettaMeteorStormMath() {
    }

    public static Vec3 phaseCenter(
            Vec3 baseCenter,
            Vec3 direction,
            BerettaMeteorStormProfile profile,
            int phase
    ) {
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        return switch (phase % Math.max(1, profile.phaseCount())) {
            case 1 -> baseCenter
                    .add(side.scale(profile.phaseShift()))
                    .add(direction.scale(profile.phaseShift() * 0.35D));
            case 2 -> baseCenter
                    .add(side.scale(-profile.phaseShift()))
                    .add(direction.scale(-profile.phaseShift() * 0.25D));
            default -> baseCenter;
        };
    }

    public static Vec3 meteorImpact(
            Vec3 center,
            Vec3 direction,
            BerettaMeteorStormProfile profile,
            int phase,
            int meteorIndex,
            int seed
    ) {
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        double across = unit(seed, meteorIndex, phase, 11) - 0.5D;
        double forward = unit(seed, meteorIndex, phase, 23) - 0.5D;
        return center
                .add(side.scale(across * profile.width()))
                .add(direction.scale(forward * profile.length()));
    }

    public static long impactAge(BerettaMeteorStormProfile profile, int wave) {
        return profile.fallTicks() + (long) wave * profile.meteorIntervalTicks();
    }

    public static double unit(int seed, int meteorIndex, int phase, int salt) {
        long value = seed;
        value ^= 0x9E3779B97F4A7C15L * (meteorIndex + 1L);
        value ^= 0xC2B2AE3D27D4EB4FL * (phase + 1L);
        value ^= 0x165667B19E3779F9L * (salt + 1L);
        value = mix(value);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
