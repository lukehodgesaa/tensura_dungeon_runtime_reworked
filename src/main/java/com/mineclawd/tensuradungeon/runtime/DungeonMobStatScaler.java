package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.compat.tensura.TensuraMobStatsCompat;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class DungeonMobStatScaler {
    private static final double ELITE_STAT_MULTIPLIER = 2.5D;

    private DungeonMobStatScaler() {
    }

    public static DungeonMobStatProfile apply(Mob mob, RoomBudgetResult budgetResult) {
        DungeonMobStatProfile profile = DungeonMobStatProfile.forFloor(budgetResult.getFloorNumber());
        if (profile.isIdentity()) {
            return profile;
        }

        multiplyAttribute(mob.getAttribute(Attributes.MAX_HEALTH), profile.maxHealthMultiplier());
        multiplyAttribute(mob.getAttribute(Attributes.MOVEMENT_SPEED), profile.movementSpeedMultiplier());
        addAttribute(mob.getAttribute(Attributes.ARMOR), profile.armorBonus());
        multiplyAttribute(mob.getAttribute(Attributes.ATTACK_DAMAGE), profile.attackDamageMultiplier());
        multiplyAttribute(mob.getAttribute(Attributes.ATTACK_SPEED), profile.attackSpeedMultiplier());
        addAttributeClamped(mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE), profile.knockbackResistanceBonus(), 0.0D, 1.0D);

        AttributeInstance maxHealthAttribute = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            mob.setHealth((float) maxHealthAttribute.getBaseValue());
        }

        TensuraMobStatsCompat.applyFloorScaling(mob, profile);
        return profile;
    }

    public static void applyEliteBoost(Mob mob) {
        if (mob == null) {
            return;
        }
        AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(eliteBoostedValue(maxHealth.getBaseValue()));
            mob.setHealth(mob.getMaxHealth());
        }
        TensuraMobStatsCompat.applyEliteSpiritualHealthScaling(mob, ELITE_STAT_MULTIPLIER);
    }

    static double eliteBoostedValue(double floorScaledValue) {
        return Math.max(1.0D, floorScaledValue * ELITE_STAT_MULTIPLIER);
    }

    private static void multiplyAttribute(AttributeInstance attribute, double multiplier) {
        if (attribute == null || multiplier == 1.0D) {
            return;
        }
        attribute.setBaseValue(Math.max(0.0001D, attribute.getBaseValue() * multiplier));
    }

    private static void addAttribute(AttributeInstance attribute, double bonus) {
        if (attribute == null || bonus == 0.0D) {
            return;
        }
        attribute.setBaseValue(attribute.getBaseValue() + bonus);
    }

    private static void addAttributeClamped(AttributeInstance attribute, double bonus, double min, double max) {
        if (attribute == null || bonus == 0.0D) {
            return;
        }
        attribute.setBaseValue(Math.max(min, Math.min(max, attribute.getBaseValue() + bonus)));
    }
}
