package com.mineclawd.tensuraadditions.compat.moreskills;

import java.util.Objects;

public record BerettaAttackPhase(
        String id,
        int startTick,
        int endTick,
        BerettaPreviewPattern preview,
        boolean damaging
) {
    public BerettaAttackPhase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(preview, "preview");
        if (id.isBlank()) {
            throw new IllegalArgumentException("phase id cannot be blank");
        }
        if (startTick < 0 || endTick <= startTick) {
            throw new IllegalArgumentException("phase must have a positive, non-negative tick interval");
        }
    }

    public boolean activeAt(int age) {
        return age >= startTick && age < endTick;
    }
}
