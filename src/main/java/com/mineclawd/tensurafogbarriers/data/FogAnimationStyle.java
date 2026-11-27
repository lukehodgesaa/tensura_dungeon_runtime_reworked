package com.mineclawd.tensurafogbarriers.data;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

public enum FogAnimationStyle implements StringRepresentable {
    PORTAL("portal", "Portal Waves"),
    VEIL("veil", "Rising Veil"),
    VORTEX("vortex", "Arcane Vortex"),
    RUNES("runes", "Rune Drift");

    private final String serializedName;
    private final String displayName;

    FogAnimationStyle(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public FogAnimationStyle next() {
        FogAnimationStyle[] styles = values();
        return styles[(ordinal() + 1) % styles.length];
    }

    public String textureName(FogVariant variant) {
        String color = variant == FogVariant.GOLD ? "golden" : variant.getSerializedName();
        return this == PORTAL ? color + "_fog" : color + "_" + serializedName + "_fog";
    }

    public static Optional<FogAnimationStyle> byName(String raw) {
        if (raw == null) return Optional.empty();
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.equals("waves") || normalized.equals("nether")) normalized = "portal";
        if (normalized.equals("flow") || normalized.equals("rising")) normalized = "veil";
        if (normalized.equals("swirl")) normalized = "vortex";
        if (normalized.equals("rune") || normalized.equals("glyphs")) normalized = "runes";
        String requested = normalized;
        return Arrays.stream(values()).filter(value -> value.serializedName.equals(requested)).findFirst();
    }
}
