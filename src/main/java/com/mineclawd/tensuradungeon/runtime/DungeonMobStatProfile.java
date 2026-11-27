package com.mineclawd.tensuradungeon.runtime;

import java.util.Locale;

public record DungeonMobStatProfile(
    int floorNumber,
    double maxHealthMultiplier,
    double movementSpeedMultiplier,
    double armorBonus,
    double attackDamageMultiplier,
    double attackSpeedMultiplier,
    double knockbackResistanceBonus,
    double maxSpiritualHealthMultiplier,
    double epMultiplier
) {
    private static final double HEALTH_PER_FLOOR = 0.08D;
    private static final double SPEED_PER_FLOOR = 0.01D;
    private static final double ARMOR_PER_FLOOR = 0.35D;
    private static final double MAX_ARMOR_BONUS = 20.0D;
    private static final double DAMAGE_PER_FLOOR = 0.05D;
    private static final double ATTACK_SPEED_PER_FLOOR = 0.015D;
    private static final double KNOCKBACK_RESISTANCE_PER_FLOOR = 0.02D;
    private static final double MAX_KNOCKBACK_RESISTANCE_BONUS = 0.75D;
    private static final double SPIRITUAL_HEALTH_PER_FLOOR = 0.10D;
    private static final double EP_PER_FLOOR = 0.12D;

    public static DungeonMobStatProfile forFloor(int floorNumber) {
        int effectiveFloor = Math.max(1, floorNumber);
        int floorOffset = effectiveFloor - 1;
        return new DungeonMobStatProfile(
            effectiveFloor,
            1.0D + (floorOffset * HEALTH_PER_FLOOR),
            1.0D + (floorOffset * SPEED_PER_FLOOR),
            Math.min(MAX_ARMOR_BONUS, floorOffset * ARMOR_PER_FLOOR),
            1.0D + (floorOffset * DAMAGE_PER_FLOOR),
            1.0D + (floorOffset * ATTACK_SPEED_PER_FLOOR),
            Math.min(MAX_KNOCKBACK_RESISTANCE_BONUS, floorOffset * KNOCKBACK_RESISTANCE_PER_FLOOR),
            1.0D + (floorOffset * SPIRITUAL_HEALTH_PER_FLOOR),
            1.0D + (floorOffset * EP_PER_FLOOR)
        );
    }

    public boolean isIdentity() {
        return floorNumber <= 1;
    }

    public String debugSummary() {
        if (isIdentity()) {
            return "FLOOR 1 BASELINE";
        }
        return String.format(
            Locale.ROOT,
            "floor %d: hp x%.2f, speed x%.2f, armor +%.2f, damage x%.2f, atk_speed x%.2f, kb_res +%.2f, spiritual_hp x%.2f, ep x%.2f",
            floorNumber,
            maxHealthMultiplier,
            movementSpeedMultiplier,
            armorBonus,
            attackDamageMultiplier,
            attackSpeedMultiplier,
            knockbackResistanceBonus,
            maxSpiritualHealthMultiplier,
            epMultiplier
        );
    }
}
