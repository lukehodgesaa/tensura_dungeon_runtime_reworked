package com.mineclawd.tensuradungeon.compat.tensura;

import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyVariantDefinition;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyVariantRegistry;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

public final class TensuraMobVariantCompat {
    private TensuraMobVariantCompat() {
    }

    public static void applySpawnVariant(Mob mob, RandomSource random) {
        applySpawnVariant(mob, 1, Set.of(), random);
    }

    public static void applySpawnVariant(Mob mob, int floor, Set<String> themeTags, RandomSource random) {
        if (TensuraCompat.getStatus().integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return;
        }
        ResourceLocation entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (entityKey == null) return;
        List<DungeonEnemyVariantDefinition> selections = DungeonEnemyVariantRegistry.roll(
            entityKey.toString(), Math.max(1, floor), themeTags == null ? Set.of() : themeTags, random
        );
        for (DungeonEnemyVariantDefinition selection : selections) {
            try {
                applySelection(mob, selection);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
    }

    private static void applySelection(Mob mob, DungeonEnemyVariantDefinition selection) throws ReflectiveOperationException {
        if ("size".equals(selection.channel())) {
            Method getSize = mob.getClass().getMethod("getSize");
            if (((Number) getSize.invoke(mob)).floatValue() <= 1.0F) {
                float size = Float.parseFloat(selection.variantId());
                mob.getClass().getMethod(selection.setterName(), float.class, boolean.class).invoke(mob, size, true);
            }
            return;
        }

        Class<?> variantClass = Class.forName(selection.enumClassName());
        Object variant = enumConstant(variantClass, selection.variantId());
        mob.getClass().getMethod(selection.setterName(), variantClass).invoke(mob, variant);
        if ("tensura:hound_dog".equals(selection.entityTypeId())) {
            mob.getClass().getMethod("updateStatsByVariant").invoke(mob);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }
}
