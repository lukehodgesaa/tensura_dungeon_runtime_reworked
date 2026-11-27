package com.mineclawd.tensuradungeon.runtime;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class OverworldRiftGeometry {
    public static final long ENTRANCE_OPENING_TICKS = 150L;
    public static final long ENTRANCE_CLOSING_TICKS = 110L;
    public static final double PORTAL_HALF_DEPTH = 5.0D;
    public static final double PLAYER_MARGIN = 0.65D;
    public static final int SONAR_WAVE_COUNT = 3;
    public static final long SONAR_FIRST_AT_TICKS = 330L;
    public static final long SONAR_INTERVAL_TICKS = 160L;
    public static final float SONAR_DURATION_TICKS = 140.0F;
    public static final float SONAR_MAX_RADIUS = 3_072.0F;
    public static final float SONAR_IMPACT_HALF_WIDTH = 72.0F;
    public static final float SONAR_AFTERSHOCK_CLIMAX_TICKS = 100.0F;
    public static final float SONAR_AFTERSHOCK_RELEASE_TICKS = 20.0F;
    public static final long GRAVITY_PULL_START_TICKS =
        SONAR_FIRST_AT_TICKS + SONAR_INTERVAL_TICKS * (SONAR_WAVE_COUNT - 1)
            + Math.round(SONAR_DURATION_TICKS);
    public static final long GRAVITY_PULL_DURATION_TICKS = 30L * 20L;
    public static final double GRAVITY_PULL_RADIUS = 512.0D;
    public static final double GRAVITY_PULL_MAX_SPEED = 5.2D;

    private static final double MINIMUM_SCAR_CENTER_Y = 382.0D;
    private static final double MINIMUM_TERRAIN_CLEARANCE = 310.0D;
    private static final int SWEEP_SAMPLES = 16;

    public static final List<ScarNode> MAIN_SCAR = List.of(
        new ScarNode(0.0F, -216.0F, 0.8F),
        new ScarNode(-9.0F, -184.0F, 5.0F),
        new ScarNode(-27.0F, -151.0F, 12.0F),
        new ScarNode(-15.0F, -118.0F, 19.0F),
        new ScarNode(-36.0F, -82.0F, 27.0F),
        new ScarNode(-20.0F, -45.0F, 35.0F),
        new ScarNode(-31.0F, -9.0F, 42.0F),
        new ScarNode(-10.0F, 30.0F, 38.0F),
        new ScarNode(-19.0F, 69.0F, 44.0F),
        new ScarNode(7.0F, 105.0F, 39.0F),
        new ScarNode(-4.0F, 141.0F, 31.0F),
        new ScarNode(20.0F, 174.0F, 23.0F),
        new ScarNode(11.0F, 202.0F, 12.0F),
        new ScarNode(18.0F, 216.0F, 0.8F)
    );

    private OverworldRiftGeometry() {
    }

    public static double centerY(BlockPos anchor) {
        return Math.max(MINIMUM_SCAR_CENTER_Y, anchor.getY() + MINIMUM_TERRAIN_CLEARANCE);
    }

    public static float orientationFacing(BlockPos anchor, Vec3 observer) {
        double dx = observer.x - (anchor.getX() + 0.5D);
        double dz = observer.z - (anchor.getZ() + 0.5D);
        if (dx * dx + dz * dz < 0.0001D) {
            return deterministicOrientation(anchor);
        }
        return normalizeOrientation((float) Math.toDegrees(Math.atan2(dz, dx)));
    }

    public static float deterministicOrientation(BlockPos anchor) {
        long mixed = anchor.asLong() ^ Long.rotateLeft(anchor.asLong() * 0x9E3779B97F4A7C15L, 17);
        return Math.floorMod(mixed, 36_000L) / 100.0F;
    }

    public static float normalizeOrientation(float degrees) {
        if (!Float.isFinite(degrees)) {
            return 0.0F;
        }
        float normalized = degrees % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    public static Basis basis(float orientationDegrees) {
        double radians = Math.toRadians(normalizeOrientation(orientationDegrees));
        double normalX = Math.cos(radians);
        double normalZ = Math.sin(radians);
        return new Basis(-normalZ, normalX, normalX, normalZ);
    }

    public static long sonarStartTick(int waveIndex) {
        if (waveIndex < 0 || waveIndex >= SONAR_WAVE_COUNT) {
            throw new IllegalArgumentException("Invalid rift sonar wave index: " + waveIndex);
        }
        return SONAR_FIRST_AT_TICKS + SONAR_INTERVAL_TICKS * waveIndex;
    }

    public static boolean sonarActive(float riftAge, int waveIndex) {
        float localAge = riftAge - sonarStartTick(waveIndex);
        return localAge >= 0.0F && localAge <= SONAR_DURATION_TICKS;
    }

    public static float sonarProgress(float riftAge, int waveIndex) {
        float localAge = riftAge - sonarStartTick(waveIndex);
        return clamp01(localAge / SONAR_DURATION_TICKS);
    }

    public static float sonarRadius(float riftAge, int waveIndex) {
        return SONAR_MAX_RADIUS * sonarProgress(riftAge, waveIndex);
    }

    public static float sonarCrossingAge(BlockPos anchor, Vec3 point, int waveIndex) {
        double distance = Math.sqrt(horizontalDistanceSquared(anchor, point));
        float distanceProgress = clamp01((float) (distance / SONAR_MAX_RADIUS));
        return sonarStartTick(waveIndex) + SONAR_DURATION_TICKS * distanceProgress;
    }

    public static float sonarEnvelope(float riftAge, int waveIndex) {
        float localAge = riftAge - sonarStartTick(waveIndex);
        if (localAge < 0.0F || localAge > SONAR_DURATION_TICKS) {
            return 0.0F;
        }
        float appearing = smooth(clamp01(localAge / 6.0F));
        float disappearing = smooth(clamp01((SONAR_DURATION_TICKS - localAge) / 14.0F));
        return appearing * disappearing;
    }

    public static double horizontalDistanceSquared(BlockPos anchor, Vec3 point) {
        double dx = point.x - (anchor.getX() + 0.5D);
        double dz = point.z - (anchor.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    public static float sonarImpactStrength(BlockPos anchor, Vec3 point, float riftAge) {
        double horizontalDistance = Math.sqrt(horizontalDistanceSquared(anchor, point));
        float strongest = 0.0F;
        for (int waveIndex = 0; waveIndex < SONAR_WAVE_COUNT; waveIndex++) {
            if (sonarActive(riftAge, waveIndex)) {
                float separation = (float) Math.abs(horizontalDistance - sonarRadius(riftAge, waveIndex));
                float rawStrength = 1.0F - separation / SONAR_IMPACT_HALF_WIDTH;
                strongest = Math.max(strongest, smooth(clamp01(rawStrength)));
            }
            strongest = Math.max(
                strongest,
                sonarAftershockStrengthForWave(anchor, point, riftAge, waveIndex)
            );
        }
        return strongest;
    }

    public static boolean gravityPullActive(long riftAge) {
        long localAge = riftAge - GRAVITY_PULL_START_TICKS;
        return localAge >= 0L && localAge < GRAVITY_PULL_DURATION_TICKS;
    }

    public static float gravityPullProgress(long riftAge) {
        long localAge = riftAge - GRAVITY_PULL_START_TICKS;
        return clamp01(localAge / (float) GRAVITY_PULL_DURATION_TICKS);
    }

    public static double gravityPullSpeed(double distance, long riftAge) {
        if (!gravityPullActive(riftAge) || distance <= 0.0D || distance > GRAVITY_PULL_RADIUS) {
            return 0.0D;
        }
        double distanceProgress = Math.max(0.0D, Math.min(1.0D, distance / GRAVITY_PULL_RADIUS));
        double build = 0.35D + 0.65D * smooth(gravityPullProgress(riftAge));
        double speed = (0.45D + 4.75D * distanceProgress) * build;
        return Math.min(GRAVITY_PULL_MAX_SPEED, speed);
    }

    private static float sonarAftershockStrengthForWave(
        BlockPos anchor,
        Vec3 point,
        float riftAge,
        int waveIndex
    ) {
        double horizontalDistance = Math.sqrt(horizontalDistanceSquared(anchor, point));
        if (horizontalDistance > SONAR_MAX_RADIUS) {
            return 0.0F;
        }
        float aftershockAge = riftAge - sonarCrossingAge(anchor, point, waveIndex);
        float releaseEnd = SONAR_AFTERSHOCK_CLIMAX_TICKS + SONAR_AFTERSHOCK_RELEASE_TICKS;
        if (aftershockAge < 0.0F || aftershockAge > releaseEnd) {
            return 0.0F;
        }
        float climb = 0.78F + 0.22F * smooth(clamp01(aftershockAge / SONAR_AFTERSHOCK_CLIMAX_TICKS));
        if (aftershockAge <= SONAR_AFTERSHOCK_CLIMAX_TICKS) {
            return climb;
        }
        float release = 1.0F - smooth(clamp01(
            (aftershockAge - SONAR_AFTERSHOCK_CLIMAX_TICKS) / SONAR_AFTERSHOCK_RELEASE_TICKS
        ));
        return climb * release;
    }

    public static boolean intersectsPortal(
        BlockPos anchor,
        float orientationDegrees,
        Vec3 previousCenter,
        Vec3 currentCenter
    ) {
        Basis basis = basis(orientationDegrees);
        LocalPoint previous = toLocal(anchor, basis, previousCenter);
        LocalPoint current = toLocal(anchor, basis, currentCenter);
        if (containsLocal(previous, PLAYER_MARGIN) || containsLocal(current, PLAYER_MARGIN)) {
            return true;
        }

        double depthDelta = current.depth() - previous.depth();
        if (Math.abs(depthDelta) > 0.000001D) {
            double crossing = -previous.depth() / depthDelta;
            if (crossing >= 0.0D && crossing <= 1.0D
                && containsLocal(interpolate(previous, current, crossing), PLAYER_MARGIN)) {
                return true;
            }
        }

        double nearestDepth = Math.min(Math.abs(previous.depth()), Math.abs(current.depth()));
        if (nearestDepth > PORTAL_HALF_DEPTH + PLAYER_MARGIN
            && previous.depth() * current.depth() > 0.0D) {
            return false;
        }
        for (int sample = 1; sample < SWEEP_SAMPLES; sample++) {
            double progress = sample / (double) SWEEP_SAMPLES;
            if (containsLocal(interpolate(previous, current, progress), PLAYER_MARGIN)) {
                return true;
            }
        }
        return false;
    }

    private static LocalPoint toLocal(BlockPos anchor, Basis basis, Vec3 point) {
        double dx = point.x - (anchor.getX() + 0.5D);
        double dz = point.z - (anchor.getZ() + 0.5D);
        return new LocalPoint(
            dx * basis.rightX() + dz * basis.rightZ(),
            point.y - centerY(anchor),
            dx * basis.normalX() + dz * basis.normalZ()
        );
    }

    private static LocalPoint interpolate(LocalPoint start, LocalPoint end, double progress) {
        return new LocalPoint(
            start.u() + (end.u() - start.u()) * progress,
            start.v() + (end.v() - start.v()) * progress,
            start.depth() + (end.depth() - start.depth()) * progress
        );
    }

    private static boolean containsLocal(LocalPoint point, double margin) {
        if (Math.abs(point.depth()) > PORTAL_HALF_DEPTH + margin) {
            return false;
        }
        ScarNode first = MAIN_SCAR.getFirst();
        ScarNode last = MAIN_SCAR.getLast();
        if (point.v() < first.v() - margin || point.v() > last.v() + margin) {
            return false;
        }
        for (int index = 0; index < MAIN_SCAR.size() - 1; index++) {
            ScarNode lower = MAIN_SCAR.get(index);
            ScarNode upper = MAIN_SCAR.get(index + 1);
            if (point.v() < lower.v() - margin || point.v() > upper.v() + margin) {
                continue;
            }
            double span = upper.v() - lower.v();
            double progress = span <= 0.0D ? 0.0D : (point.v() - lower.v()) / span;
            progress = Math.max(0.0D, Math.min(1.0D, progress));
            double centerU = lower.u() + (upper.u() - lower.u()) * progress;
            double halfWidth = lower.halfWidth() + (upper.halfWidth() - lower.halfWidth()) * progress;
            return Math.abs(point.u() - centerU) <= halfWidth + margin;
        }
        return false;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    public record ScarNode(float u, float v, float halfWidth) {
    }

    public record Basis(double rightX, double rightZ, double normalX, double normalZ) {
    }

    private record LocalPoint(double u, double v, double depth) {
    }
}
