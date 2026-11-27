package com.mineclawd.tensuradungeon.runtime;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record CosmeticKey(Type type, String id) implements Comparable<CosmeticKey> {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,47}");

    public static final CosmeticKey HUD_GOTHIC = new CosmeticKey(Type.HUD_THEME, "gothic");
    public static final CosmeticKey HUD_FREY = new CosmeticKey(Type.HUD_THEME, "frey");
    public static final CosmeticKey HUD_RIMURU = new CosmeticKey(Type.HUD_THEME, "rimuru");
    public static final CosmeticKey QUEST_GOTHIC = new CosmeticKey(Type.QUEST_THEME, "gothic");
    public static final CosmeticKey QUEST_FREY = new CosmeticKey(Type.QUEST_THEME, "frey");
    public static final CosmeticKey QUEST_INHERIT = new CosmeticKey(Type.QUEST_THEME, "inherit");

    @Deprecated(forRemoval = false)
    public static final CosmeticKey HUD_HARPY = HUD_FREY;
    @Deprecated(forRemoval = false)
    public static final CosmeticKey QUEST_HARPY = QUEST_FREY;

    private static final Set<CosmeticKey> CATALOG = Set.of(
        HUD_GOTHIC,
        HUD_FREY,
        HUD_RIMURU,
        QUEST_GOTHIC,
        QUEST_FREY
    );

    public CosmeticKey {
        type = Objects.requireNonNull(type, "Cosmetic type");
        id = normalizeId(id);
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Unsupported cosmetic id: " + id);
        }
    }

    public String canonicalId() {
        return type.name().toLowerCase(Locale.ROOT) + ':' + id;
    }

    public boolean cataloged() {
        return CATALOG.contains(this);
    }

    public boolean entitlementRequired() {
        return !equals(QUEST_INHERIT);
    }

    public static Set<CosmeticKey> catalog() {
        return CATALOG;
    }

    public static Optional<CosmeticKey> fromCanonicalId(String value) {
        if (value == null) {
            return Optional.empty();
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            return Optional.empty();
        }
        try {
            Type type = Type.valueOf(value.substring(0, separator).trim().toUpperCase(Locale.ROOT));
            return Optional.of(new CosmeticKey(type, value.substring(separator + 1)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(CosmeticKey other) {
        int byType = type.compareTo(other.type);
        return byType != 0 ? byType : id.compareTo(other.id);
    }

    private static String normalizeId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "harpy".equals(normalized) ? "frey" : normalized;
    }

    public enum Type {
        HUD_THEME,
        QUEST_THEME
    }
}
