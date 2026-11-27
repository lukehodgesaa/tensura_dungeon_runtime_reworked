package com.mineclawd.tensurafogbarriers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FogBarrierConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue BOUNCE_ENABLED = BUILDER
        .comment("Bounce entities away from animated fog panels.")
        .define("impacts.bounceEnabled", true);
    public static final ModConfigSpec.DoubleValue BOUNCE_STRENGTH = BUILDER
        .comment("Minimum horizontal speed applied away from a fog barrier.")
        .defineInRange("impacts.bounceStrength", 0.72D, 0.05D, 4.0D);
    public static final ModConfigSpec.DoubleValue BOUNCE_VERTICAL_LIFT = BUILDER
        .comment("Minimum upward velocity added to a barrier bounce.")
        .defineInRange("impacts.bounceVerticalLift", 0.16D, 0.0D, 1.5D);
    public static final ModConfigSpec.IntValue BOUNCE_COOLDOWN_TICKS = BUILDER
        .comment("Per-entity cooldown that prevents repeated bounce loops.")
        .defineInRange("impacts.bounceCooldownTicks", 4, 1, 40);
    public static final ModConfigSpec.BooleanValue CREATIVE_MODE_EXEMPT = BUILDER
        .comment("Allow creative-mode players through rendered fog panels.")
        .define("impacts.creativeModeExempt", false);
    public static final ModConfigSpec.BooleanValue REFLECT_PROJECTILES = BUILDER
        .comment("Reflect projectiles from panels and placed Golden Fog Barrier blocks.")
        .define("projectiles.reflect", true);
    public static final ModConfigSpec.DoubleValue PROJECTILE_SPEED_MULTIPLIER = BUILDER
        .comment("Speed retained by the reflected normal component.")
        .defineInRange("projectiles.speedMultiplier", 1.0D, 0.1D, 3.0D);
    public static final ModConfigSpec.IntValue PROJECTILE_COOLDOWN_TICKS = BUILDER
        .comment("Per-projectile cooldown that prevents reflection loops.")
        .defineInRange("projectiles.cooldownTicks", 2, 1, 20);
    public static final ModConfigSpec.BooleanValue IMPACT_EFFECTS = BUILDER
        .comment("Play a sound and small particle burst when a barrier reacts.")
        .define("impacts.effects", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private FogBarrierConfig() {
    }
}
