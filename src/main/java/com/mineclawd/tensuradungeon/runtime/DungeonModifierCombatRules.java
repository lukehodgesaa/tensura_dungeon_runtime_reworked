package com.mineclawd.tensuradungeon.runtime;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

public final class DungeonModifierCombatRules {
    public static final double GLASS_CANNON_HEALTH_MULTIPLIER = 0.80D;
    public static final double GLASS_CANNON_DAMAGE_MULTIPLIER = 1.25D;
    public static final double BARBED_HIDE_REFLECTION = 0.04D;
    public static final double SPELL_DAMPENING_DAMAGE_MULTIPLIER = 0.80D;
    public static final double SHATTERPROOF_MAX_EVENT_FRACTION = 0.35D;
    public static final double DEATH_RUN_SPEED_MULTIPLIER = 5.0D;
    public static final double PURSUER_MARK_SPEED_MULTIPLIER = 3.0D;
    public static final double PURSUER_MARK_HEALTH_MULTIPLIER = 3.0D;
    public static final double DEATH_ECHO_MAX_HEALTH_FRACTION = 0.02D;
    public static final int HOT_POTATO_HOLD_TICKS = 5 * 20;
    public static final int HOT_POTATO_FIRE_TICKS = 5 * 20;
    public static final int MOLTEN_PLAYER_FIRE_TICKS = 15 * 20;
    public static final int RISING_HEAT_STAGE_INTERVAL_TICKS = 600;
    public static final int RISING_HEAT_MAX_STAGE = 3;

    private DungeonModifierCombatRules() {}

    public static double glassCannonHealth(double maximumHealth) {
        return Math.max(1.0D, maximumHealth * GLASS_CANNON_HEALTH_MULTIPLIER);
    }

    public static float glassCannonDamage(float damage) {
        return damage <= 0.0F ? damage : (float) (damage * GLASS_CANNON_DAMAGE_MULTIPLIER);
    }

    public static float barbedHideReflection(float finalDirectMeleeDamage) {
        return finalDirectMeleeDamage <= 0.0F
            ? 0.0F
            : (float) (finalDirectMeleeDamage * BARBED_HIDE_REFLECTION);
    }

    public static float spellDampeningDamage(float nativeMagicDamage) {
        return nativeMagicDamage <= 0.0F
            ? nativeMagicDamage
            : (float) (nativeMagicDamage * SPELL_DAMPENING_DAMAGE_MULTIPLIER);
    }

    public static float shatterproofDamageCap(float incomingDamage, float maximumHealth) {
        if (incomingDamage <= 0.0F || maximumHealth <= 0.0F) return incomingDamage;
        return Math.min(incomingDamage, (float) (maximumHealth * SHATTERPROOF_MAX_EVENT_FRACTION));
    }

    public static float deathEchoDamage(float maximumHealth) {
        return maximumHealth <= 0.0F
            ? 0.0F
            : (float) (maximumHealth * DEATH_ECHO_MAX_HEALTH_FRACTION);
    }

    public static double multipliedBaseAmount(double totalMultiplier) {
        return Math.max(0.0D, totalMultiplier - 1.0D);
    }

    public static boolean isAmbushMarked(UUID entityUuid) {
        return entityUuid != null && (entityUuid.getLeastSignificantBits() & 1L) == 0L;
    }

    public static RisingHeatStage risingHeatStage(int elapsedTicks) {
        int safeTicks = Math.max(0, elapsedTicks);
        int stage = Math.min(RISING_HEAT_MAX_STAGE, safeTicks / RISING_HEAT_STAGE_INTERVAL_TICKS);
        return new RisingHeatStage(
            stage,
            1.0D + stage * 0.40D,
            switch (stage) {
                case 1 -> 1.0D + (1.0D / 6.0D);
                case 2 -> 1.0D + (1.0D / 3.0D);
                case 3 -> 1.5D;
                default -> 1.0D;
            }
        );
    }

    public static boolean isKnownDirectMelee(DamageSource source, LivingEntity target) {
        if (source == null || target == null || source.is(DamageTypes.THORNS)) return false;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity) || attacker == target || source.getDirectEntity() != attacker) {
            return false;
        }
        return source.is(DamageTypes.MOB_ATTACK)
            || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
            || source.is(DamageTypes.PLAYER_ATTACK);
    }

    public record RisingHeatStage(int stage, double damageMultiplier, double speedMultiplier) {}
}
