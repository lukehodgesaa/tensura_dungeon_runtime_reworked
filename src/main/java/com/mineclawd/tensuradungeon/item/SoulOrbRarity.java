package com.mineclawd.tensuradungeon.item;

import java.util.Locale;
import net.minecraft.world.item.Rarity;

public enum SoulOrbRarity {
    COMMON(Rarity.COMMON, 0xF1F4FF),
    UNCOMMON(Rarity.UNCOMMON, 0x35A9FF),
    RARE(Rarity.RARE, 0xAD50FF),
    EPIC(Rarity.EPIC, 0xFFB730);

    private final Rarity itemRarity;
    private final int rgb;

    SoulOrbRarity(Rarity itemRarity, int rgb) {
        this.itemRarity = itemRarity;
        this.rgb = rgb;
    }

    public Rarity itemRarity() {
        return itemRarity;
    }

    public int rgb() {
        return rgb;
    }

    public static SoulOrbRarity fromEnemyRank(String rank) {
        return switch (normalize(rank)) {
            case "A", "S", "SS" -> EPIC;
            case "B" -> RARE;
            case "C", "D" -> UNCOMMON;
            default -> COMMON;
        };
    }

    public static SoulOrbRarity parse(String value) {
        try {
            return valueOf(normalize(value));
        } catch (IllegalArgumentException ignored) {
            return COMMON;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
