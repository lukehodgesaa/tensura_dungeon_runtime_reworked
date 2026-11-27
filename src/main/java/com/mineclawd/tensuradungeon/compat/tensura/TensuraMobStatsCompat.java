package com.mineclawd.tensuradungeon.compat.tensura;

import com.mineclawd.tensuradungeon.runtime.DungeonMobStatProfile;
import java.lang.reflect.Method;
import net.minecraft.world.entity.LivingEntity;

public final class TensuraMobStatsCompat {
    private static final String TENSURA_STORAGES_CLASS = "io.github.manasmods.tensura.storage.TensuraStorages";
    private static final String TENSURA_ATTRIBUTES_CLASS = "io.github.manasmods.tensura.registry.attribute.TensuraAttributes";
    private static final String ENERGY_HELPER_CLASS = "io.github.manasmods.tensura.util.EnergyHelper";
    private static final String MAX_SPIRITUAL_HEALTH = "MAX_SPIRITUAL_HEALTH";

    private TensuraMobStatsCompat() {
    }

    public static void applyFloorScaling(LivingEntity entity, DungeonMobStatProfile profile) {
        if (entity == null || profile == null || profile.isIdentity()) {
            return;
        }
        if (TensuraCompat.getStatus().integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return;
        }
        try {
            Object existence = getExistence(entity);
            boolean mutated = false;

            Object spiritualHealthAttribute = getTensuraAttributeInstance(entity, MAX_SPIRITUAL_HEALTH);
            if (spiritualHealthAttribute != null && profile.maxSpiritualHealthMultiplier() != 1.0D) {
                double scaledSpiritualHealth = Math.max(1.0D, getAttributeBaseValue(spiritualHealthAttribute) * profile.maxSpiritualHealthMultiplier());
                setAttributeBaseValue(spiritualHealthAttribute, scaledSpiritualHealth);
                if (existence != null) {
                    invokeExistenceSetter(existence, "setSpiritualHealth", getAttributeValue(spiritualHealthAttribute));
                }
                mutated = true;
            }

            if (profile.epMultiplier() != 1.0D) {
                Double baseMaxEp = invokeStaticDouble(ENERGY_HELPER_CLASS, "getBaseMaxEP", entity);
                if (baseMaxEp != null && baseMaxEp > 0.0D) {
                    invokeStaticVoid(ENERGY_HELPER_CLASS, "setBaseMaxEP", entity, Math.max(1.0D, baseMaxEp * profile.epMultiplier()));
                    if (existence != null) {
                        Double scaledMaxEp = invokeStaticDouble(ENERGY_HELPER_CLASS, "getMaxEP", entity);
                        if (scaledMaxEp != null) {
                            invokeExistenceSetter(existence, "setEP", scaledMaxEp);
                        }
                    }
                    mutated = true;
                }
            }

            if (mutated && existence != null) {
                existence.getClass().getMethod("markDirty").invoke(existence);
            }
        } catch (Exception | LinkageError ignored) {
        }
    }

    public static void applyEliteSpiritualHealthScaling(LivingEntity entity, double multiplier) {
        if (entity == null || multiplier == 1.0D) {
            return;
        }
        if (TensuraCompat.getStatus().integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return;
        }
        try {
            Object spiritualHealthAttribute = getTensuraAttributeInstance(entity, MAX_SPIRITUAL_HEALTH);
            if (spiritualHealthAttribute == null) {
                return;
            }
            double scaledSpiritualHealth = Math.max(
                1.0D, getAttributeBaseValue(spiritualHealthAttribute) * multiplier
            );
            setAttributeBaseValue(spiritualHealthAttribute, scaledSpiritualHealth);
            Object existence = getExistence(entity);
            if (existence != null) {
                invokeExistenceSetter(
                    existence, "setSpiritualHealth", getAttributeValue(spiritualHealthAttribute)
                );
                existence.getClass().getMethod("markDirty").invoke(existence);
            }
        } catch (Exception | LinkageError ignored) {
        }
    }

    private static Object getExistence(LivingEntity entity) throws Exception {
        Class<?> storagesClass = Class.forName(TENSURA_STORAGES_CLASS);
        Method method = storagesClass.getMethod("getExistenceFrom", LivingEntity.class);
        return method.invoke(null, entity);
    }

    private static Object getTensuraAttributeInstance(LivingEntity entity, String fieldName) throws Exception {
        Object holder = Class.forName(TENSURA_ATTRIBUTES_CLASS).getField(fieldName).get(null);
        Method method = LivingEntity.class.getMethod("getAttribute", Class.forName("net.minecraft.core.Holder"));
        return method.invoke(entity, holder);
    }

    private static double getAttributeBaseValue(Object attributeInstance) throws Exception {
        Object value = attributeInstance.getClass().getMethod("getBaseValue").invoke(attributeInstance);
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static double getAttributeValue(Object attributeInstance) throws Exception {
        Object value = attributeInstance.getClass().getMethod("getValue").invoke(attributeInstance);
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static void setAttributeBaseValue(Object attributeInstance, double value) throws Exception {
        attributeInstance.getClass().getMethod("setBaseValue", double.class).invoke(attributeInstance, value);
    }

    private static void invokeExistenceSetter(Object existence, String methodName, double value) throws Exception {
        existence.getClass().getMethod(methodName, double.class).invoke(existence, value);
    }

    private static Double invokeStaticDouble(String className, String methodName, LivingEntity entity) {
        try {
            Class<?> helperClass = Class.forName(className);
            Method method = helperClass.getMethod(methodName, LivingEntity.class);
            Object value = method.invoke(null, entity);
            return value instanceof Number number ? number.doubleValue() : null;
        } catch (Exception | LinkageError ex) {
            return null;
        }
    }

    private static void invokeStaticVoid(String className, String methodName, LivingEntity entity, double value) throws Exception {
        Class<?> helperClass = Class.forName(className);
        Method method = helperClass.getMethod(methodName, LivingEntity.class, double.class);
        method.invoke(null, entity, value);
    }
}
