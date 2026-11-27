package dev.luke.tensuradungeon.client;

import java.util.Arrays;

public enum CrystalTierVisual {
    TIER_I(1, "Tier I Monster Core", "Depths 1-50", 0xFF315CB4),
    TIER_II(2, "Tier II Monster Core", "Depths 51-100", 0xFF2C70FF),
    TIER_III(3, "Tier III Monster Core", "Depths 101-150", 0xFF2CE7FF),
    TIER_IV(4, "Tier IV Monster Core", "Depths 151-200", 0xFF745EFF),
    TIER_V(5, "Tier V Monster Core", "Depths 201-250", 0xFFE142FF),
    TIER_VI(6, "Tier VI Monster Core", "Depths 251-299", 0xFFFF69EF),
    APEX(7, "Tier VII Apex Core", "Depth 300 guardian preview", 0xFFFFDDFF);

    private final int tier;
    private final String displayName;
    private final String plannedBand;
    private final int color;

    CrystalTierVisual(int tier, String displayName, String plannedBand, int color) {
        this.tier = tier;
        this.displayName = displayName;
        this.plannedBand = plannedBand;
        this.color = color;
    }

    public int tier() {
        return tier;
    }

    public String displayName() {
        return displayName;
    }

    public String plannedBand() {
        return plannedBand;
    }

    public int color() {
        return color;
    }

    public String rewardId() {
        return "monster-crystal-tier-" + tier;
    }

    public String tierLabel() {
        return tier == 7 ? "APEX" : "TIER " + roman(tier);
    }

    public static CrystalTierVisual forFloor(int floor) {
        int safeFloor = Math.max(1, floor);
        if (safeFloor >= 300) return APEX;
        return values()[Math.min(5, (safeFloor - 1) / 50)];
    }

    public static CrystalTierVisual fromRewardId(String rewardId) {
        if (rewardId == null || rewardId.isBlank()) return null;
        return Arrays.stream(values())
                .filter(tier -> tier.rewardId().equals(rewardId))
                .findFirst()
                .orElse(null);
    }

    private static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> "VII";
        };
    }
}
