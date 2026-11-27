package com.mineclawd.tensurafogbarriers.data;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

public enum FogVariant implements StringRepresentable {
    GOLD("gold", "Gold", 11),
    TEAL("teal", "Teal", 8),
    CRIMSON("crimson", "Crimson", 10),
    VIOLET("violet", "Violet", 8),
    BLACK("black", "Black", 3);

    private final String serializedName;
    private final String displayName;
    private final int lightLevel;

    FogVariant(String serializedName, String displayName, int lightLevel) {
        this.serializedName = serializedName;
        this.displayName = displayName;
        this.lightLevel = lightLevel;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String displayName() {
        return displayName;
    }

    public int lightLevel() {
        return lightLevel;
    }

    public String textureName() {
        return this == GOLD ? "golden_fog" : serializedName + "_fog";
    }

    public FogVariant next() {
        FogVariant[] variants = values();
        return variants[(ordinal() + 1) % variants.length];
    }

    public static Optional<FogVariant> byName(String raw) {
        if (raw == null) return Optional.empty();
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.equals("yellow") || normalized.equals("golden")) normalized = "gold";
        if (normalized.equals("cyan") || normalized.equals("blue")) normalized = "teal";
        if (normalized.equals("red")) normalized = "crimson";
        if (normalized.equals("purple")) normalized = "violet";
        String requested = normalized;
        return Arrays.stream(values()).filter(value -> value.serializedName.equals(requested)).findFirst();
    }

    public static FogVariant fromType(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        Optional<FogVariant> explicit = Arrays.stream(values())
            .filter(value -> normalized.contains(value.serializedName))
            .findFirst();
        if (explicit.isPresent()) return explicit.get();
        if (normalized.contains("hub") || normalized.contains("safe") || normalized.contains("checkpoint")) return TEAL;
        if (normalized.contains("frontier") || normalized.contains("unknown")) return VIOLET;
        if (normalized.contains("danger") || normalized.contains("hostile")) return CRIMSON;
        if (normalized.contains("void") || normalized.contains("sealed")) return BLACK;
        return GOLD;
    }
}
