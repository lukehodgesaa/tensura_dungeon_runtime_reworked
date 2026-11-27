package com.mineclawd.tensuraadditions.compat.moreskills;

public record BerettaMeteorStormProfile(
        boolean enabled,
        double width,
        double length,
        double dropHeight,
        int phaseCount,
        int phaseDurationTicks,
        int meteorIntervalTicks,
        int meteorsPerWave,
        int fallTicks,
        double previewRadius,
        double impactRadius,
        double phaseShift
) {
    public BerettaMeteorStormProfile {
        if (enabled) {
            if (width <= 0.0D || length <= 0.0D || dropHeight <= 0.0D) {
                throw new IllegalArgumentException("Meteor storm dimensions must be positive");
            }
            if (phaseCount <= 0 || phaseDurationTicks <= 0) {
                throw new IllegalArgumentException("Meteor storm phases must be positive");
            }
            if (meteorIntervalTicks <= 0 || meteorsPerWave <= 0 || fallTicks <= 0) {
                throw new IllegalArgumentException("Meteor storm cadence must be positive");
            }
            if (previewRadius <= 0.0D || impactRadius <= 0.0D || phaseShift < 0.0D) {
                throw new IllegalArgumentException("Meteor storm radii and shift are invalid");
            }
        }
    }

    public static BerettaMeteorStormProfile none() {
        return new BerettaMeteorStormProfile(
                false,
                0.0D,
                0.0D,
                0.0D,
                0,
                0,
                0,
                0,
                0,
                0.0D,
                0.0D,
                0.0D
        );
    }

    public static BerettaMeteorStormProfile defaultProfile() {
        return new BerettaMeteorStormProfile(
                true,
                91.0D,
                96.0D,
                80.0D,
                3,
                200,
                4,
                3,
                24,
                2.0D,
                2.5D,
                18.0D
        );
    }

    public int phaseAt(int age) {
        if (!enabled) {
            return 0;
        }
        return Math.min(phaseCount - 1, Math.max(0, age / phaseDurationTicks));
    }
}
