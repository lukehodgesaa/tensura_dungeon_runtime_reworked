package com.mineclawd.tensuraadditions.compat.moreskills;

import java.util.Objects;

public record BerettaDamageProfile(
        Mode mode,
        double amount,
        double cap,
        boolean respectsResistances
) {
    public BerettaDamageProfile {
        Objects.requireNonNull(mode, "mode");
        if (!Double.isFinite(amount) || amount < 0.0D) {
            throw new IllegalArgumentException("amount must be finite and non-negative");
        }
        if (!Double.isFinite(cap) || cap < 0.0D) {
            throw new IllegalArgumentException("cap must be finite and non-negative");
        }
    }

    public static BerettaDamageProfile none() {
        return new BerettaDamageProfile(Mode.NONE, 0.0D, 0.0D, true);
    }

    public static BerettaDamageProfile flat(double damage, boolean respectsResistances) {
        return new BerettaDamageProfile(Mode.FLAT, damage, 0.0D, respectsResistances);
    }

    public static BerettaDamageProfile maxHealthPercent(
            double percent,
            double cap,
            boolean respectsResistances
    ) {
        return new BerettaDamageProfile(Mode.MAX_HEALTH_PERCENT, percent, cap, respectsResistances);
    }

    public enum Mode {
        NONE,
        FLAT,
        MAX_HEALTH_PERCENT,
        CURRENT_HEALTH_PERCENT
    }
}
