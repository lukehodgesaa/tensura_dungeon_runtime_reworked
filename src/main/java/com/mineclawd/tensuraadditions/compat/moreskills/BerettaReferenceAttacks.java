package com.mineclawd.tensuraadditions.compat.moreskills;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

final class BerettaReferenceAttacks {
    private static final String NAMESPACE = "tensura_additions";

    private BerettaReferenceAttacks() {
    }

    static void register() {
        registerReference(
                "low_void_laser",
                "Low Void Laser",
                "Attack - Low Void Laser",
                122,
                32,
                BerettaPreviewPattern.LANES,
                List.of(
                        phase("rune", 1, 10, BerettaPreviewPattern.RUNE, false),
                        phase("lane_warning", 10, 32, BerettaPreviewPattern.LANES, false),
                        phase("laser_fire", 32, 122, BerettaPreviewPattern.LANES, true)
                )
        );
        registerReference(
                "orb_cascade",
                "Orb Cascade",
                "Attack - Orb Cascade",
                164,
                34,
                BerettaPreviewPattern.ORBITING_ORBS,
                List.of(
                        phase("rune", 1, 14, BerettaPreviewPattern.RUNE, false),
                        phase("launch_warning", 14, 34, BerettaPreviewPattern.ORBITING_ORBS, false),
                        phase("orb_impacts", 34, 164, BerettaPreviewPattern.ORBITING_ORBS, true)
                )
        );
        registerReference(
                "darkness_cannon",
                "Darkness Cannon",
                "Attack - Darkness Cannon",
                110,
                28,
                BerettaPreviewPattern.CANNON_POINTS,
                List.of(
                        phase("target_points", 1, 28, BerettaPreviewPattern.CANNON_POINTS, false),
                        phase("cannon_strikes", 28, 110, BerettaPreviewPattern.CANNON_POINTS, true)
                )
        );
        registerReference(
                "mineral_eruption",
                "Mineral Eruption",
                "Attack - Mineral Eruption",
                104,
                24,
                BerettaPreviewPattern.ERUPTION_TARGETS,
                List.of(
                        phase("target_runes", 12, 24, BerettaPreviewPattern.ERUPTION_TARGETS, false),
                        phase("eruption", 24, 104, BerettaPreviewPattern.ERUPTION_TARGETS, true)
                )
        );
        registerReference(
                "reversed_cross",
                "Reversed Cross",
                "Attack - Reversed Cross",
                124,
                34,
                BerettaPreviewPattern.CROSS_LANES,
                List.of(
                        phase("rune", 1, 14, BerettaPreviewPattern.RUNE, false),
                        phase("cross_warning", 14, 34, BerettaPreviewPattern.CROSS_LANES, false),
                        phase("cross_slashes", 34, 124, BerettaPreviewPattern.CROSS_LANES, true)
                )
        );
        registerReference(
                "chaos_rings",
                "Chaos Rings",
                "Ability 2 - Chaos Rings",
                138,
                24,
                BerettaPreviewPattern.EXPANDING_RINGS,
                List.of(
                        phase("ring_warning", 12, 24, BerettaPreviewPattern.EXPANDING_RINGS, false),
                        phase("ring_expansion", 24, 138, BerettaPreviewPattern.EXPANDING_RINGS, true)
                )
        );
        registerReference(
                "machina_grid",
                "Machina Grid",
                "Ability 4 - Machina Grid",
                132,
                36,
                BerettaPreviewPattern.GRID,
                List.of(
                        phase("grid_rune", 1, 14, BerettaPreviewPattern.GRID, false),
                        phase("grid_warning", 14, 36, BerettaPreviewPattern.GRID, false),
                        phase("grid_waves", 36, 132, BerettaPreviewPattern.GRID, true)
                )
        );
        registerReference(
                "inverted_fusion",
                "Inverted Fusion",
                "Ability 1 - Inverted Fusion",
                138,
                32,
                BerettaPreviewPattern.DASH_LANES,
                List.of(
                        phase("barrier", 1, 14, BerettaPreviewPattern.RUNE, false),
                        phase("dash_warning", 14, 32, BerettaPreviewPattern.DASH_LANES, false),
                        phase("dash_sequence", 32, 138, BerettaPreviewPattern.DASH_LANES, true)
                )
        );
        registerReference(
                "abyssal_gear_sweep",
                "Abyssal Gear Sweep",
                "Ability 3 - Abyssal Gear Sweep",
                166,
                32,
                BerettaPreviewPattern.ROTATING_SPOKES,
                List.of(
                        phase("spoke_warning", 16, 32, BerettaPreviewPattern.ROTATING_SPOKES, false),
                        phase("rotating_sweep", 32, 166, BerettaPreviewPattern.ROTATING_SPOKES, true)
                )
        );
        registerReference(
                "chaos_core_exposure",
                "Chaos Core Exposure",
                "Attack - Chaos Core Exposure",
                92,
                0,
                BerettaPreviewPattern.EXPOSED_CORE,
                List.of(
                        phase("exposed_core", 1, 74, BerettaPreviewPattern.EXPOSED_CORE, false),
                        phase("core_recharge", 74, 92, BerettaPreviewPattern.EXPOSED_CORE, false)
                )
        );

        BerettaAttackRegistry.registerBuiltIn(new BerettaAttackDefinition(
                id("chaotic_iron_maiden"),
                "Chaotic Iron Maiden",
                "Attack - Mineral Eruption",
                60,
                80,
                32,
                BerettaPreviewPattern.CONE,
                BerettaTargetingMode.SURFACE,
                BerettaDamageProfile.maxHealthPercent(0.25D, 0.0D, true),
                false,
                List.of(
                        phase("rune_assembly", 1, 12, BerettaPreviewPattern.RUNE, false),
                        phase("lift_and_charge", 12, 20, BerettaPreviewPattern.RUNE, false),
                        phase("spike_warning", 20, 32, BerettaPreviewPattern.CONE, false),
                        phase("iron_spike_impact", 32, 44, BerettaPreviewPattern.SPIKE_LINE, true),
                        phase("recovery", 44, 60, BerettaPreviewPattern.NONE, false)
                )
        ));

        BerettaAttackRegistry.registerBuiltIn(new BerettaAttackDefinition(
                id("void_meteor_storm"),
                "Void Meteor Storm",
                "Attack - Orb Cascade",
                600,
                100,
                0,
                BerettaPreviewPattern.METEOR_RAIN,
                BerettaTargetingMode.SURFACE,
                BerettaDamageProfile.maxHealthPercent(0.06D, 0.0D, true),
                false,
                BerettaMeteorStormProfile.defaultProfile(),
                List.of(
                        phase("meteor_rain_phase_1", 0, 200, BerettaPreviewPattern.METEOR_RAIN, true),
                        phase("meteor_rain_phase_2", 200, 400, BerettaPreviewPattern.METEOR_RAIN, true),
                        phase("meteor_rain_phase_3", 400, 600, BerettaPreviewPattern.METEOR_RAIN, true)
                )
        ));
    }

    private static void registerReference(
            String id,
            String displayName,
            String animation,
            int durationTicks,
            int impactTick,
            BerettaPreviewPattern preview,
            List<BerettaAttackPhase> phases
    ) {
        BerettaAttackRegistry.registerBuiltIn(new BerettaAttackDefinition(
                id("reference/" + id),
                displayName,
                animation,
                durationTicks,
                0,
                impactTick,
                preview,
                BerettaTargetingMode.SURFACE,
                BerettaDamageProfile.none(),
                false,
                phases
        ));
    }

    private static BerettaAttackPhase phase(
            String id,
            int start,
            int end,
            BerettaPreviewPattern preview,
            boolean damaging
    ) {
        return new BerettaAttackPhase(id, start, end, preview, damaging);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "moreskills/beretta/" + path);
    }
}
