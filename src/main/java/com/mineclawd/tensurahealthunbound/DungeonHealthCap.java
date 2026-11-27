package com.mineclawd.tensurahealthunbound;

import com.mineclawd.tensuradungeon.registry.ModDimensions;
import com.mojang.logging.LogUtils;
import io.github.manasmods.tensura.mixin.accessor.AccessorRangedAttribute;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public final class DungeonHealthCap {
    public static final double UNBOUNDED_MAX_HEALTH = 1.0E30D;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile double stockCap = Double.NaN;

    private DungeonHealthCap() {
    }

    public static synchronized void liftGlobalCap() {
        Attribute attribute = Attributes.MAX_HEALTH.value();
        if (!(attribute instanceof RangedAttribute ranged)) {
            LOGGER.warn("[Tensura Health Unbound] MAX_HEALTH is not a ranged attribute; leaving it untouched.");
            return;
        }
        double current = ranged.getMaxValue();
        if (current >= UNBOUNDED_MAX_HEALTH) {
            return;
        }
        if (!(attribute instanceof AccessorRangedAttribute accessor)) {
            LOGGER.warn("[Tensura Health Unbound] Tensura's attribute accessor is missing; dungeon mobs keep the {} cap.", current);
            return;
        }
        stockCap = current;
        accessor.setMaxValue(UNBOUNDED_MAX_HEALTH);
        LOGGER.info("[Tensura Health Unbound] Max health cap lifted for dungeon mobs; everything else keeps {}.", current);
    }

    public static boolean isUncapped(LivingEntity entity) {
        return !(entity instanceof Player)
            && entity.level() != null
            && ModDimensions.DUNGEON.equals(entity.level().dimension());
    }

    public static double capFor(LivingEntity entity, double maxHealth) {
        double cap = stockCap;
        if (Double.isNaN(cap) || maxHealth <= cap || isUncapped(entity)) {
            return maxHealth;
        }
        return cap;
    }
}
