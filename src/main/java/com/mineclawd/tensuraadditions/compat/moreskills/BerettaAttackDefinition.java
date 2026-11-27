package com.mineclawd.tensuraadditions.compat.moreskills;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record BerettaAttackDefinition(
        ResourceLocation id,
        String displayName,
        String animation,
        int durationTicks,
        int cooldownTicks,
        int impactTick,
        BerettaPreviewPattern previewPattern,
        BerettaTargetingMode targeting,
        BerettaDamageProfile damage,
        boolean overlay,
        BerettaMeteorStormProfile meteorStorm,
        List<BerettaAttackPhase> phases
) {
    public BerettaAttackDefinition(
            ResourceLocation id,
            String displayName,
            String animation,
            int durationTicks,
            int cooldownTicks,
            int impactTick,
            BerettaPreviewPattern previewPattern,
            BerettaTargetingMode targeting,
            BerettaDamageProfile damage,
            boolean overlay,
            List<BerettaAttackPhase> phases
    ) {
        this(
                id,
                displayName,
                animation,
                durationTicks,
                cooldownTicks,
                impactTick,
                previewPattern,
                targeting,
                damage,
                overlay,
                BerettaMeteorStormProfile.none(),
                phases
        );
    }

    public BerettaAttackDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(previewPattern, "previewPattern");
        Objects.requireNonNull(targeting, "targeting");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(meteorStorm, "meteorStorm");
        phases = List.copyOf(Objects.requireNonNull(phases, "phases"));

        if (displayName.isBlank() || animation.isBlank()) {
            throw new IllegalArgumentException("displayName and animation cannot be blank");
        }
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("cooldownTicks cannot be negative");
        }
        if (impactTick < 0 || impactTick >= durationTicks) {
            throw new IllegalArgumentException("impactTick must be inside the attack duration");
        }
        for (BerettaAttackPhase phase : phases) {
            if (phase.endTick() > durationTicks) {
                throw new IllegalArgumentException("phase exceeds attack duration: " + phase.id());
            }
        }
    }

    public BerettaAttackPhase phaseAt(int age) {
        for (BerettaAttackPhase phase : phases) {
            if (phase.activeAt(age)) {
                return phase;
            }
        }
        return null;
    }
}
