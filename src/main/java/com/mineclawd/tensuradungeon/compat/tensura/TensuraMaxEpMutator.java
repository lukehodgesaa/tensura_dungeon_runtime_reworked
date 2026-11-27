package com.mineclawd.tensuradungeon.compat.tensura;

import com.mineclawd.tensuradungeon.reward.MaxEpMutator;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class TensuraMaxEpMutator implements MaxEpMutator {
    private static final String TENSURA_STORAGES_CLASS = "io.github.manasmods.tensura.storage.TensuraStorages";
    private static final String TENSURA_ATTRIBUTES_CLASS = "io.github.manasmods.tensura.registry.attribute.TensuraAttributes";
    private static final String ENERGY_HELPER_CLASS = "io.github.manasmods.tensura.util.EnergyHelper";
    private static final String MUTATION_PATH = "ATTRIBUTE_BASE_SPLIT_SYNC";
    public static final TensuraMaxEpMutator INSTANCE = new TensuraMaxEpMutator();

    private TensuraMaxEpMutator() {
    }

    @Override
    public MutationResult increaseMaxEp(Player player, int requestedIncrease) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return new MutationResult(false, requestedIncrease, MUTATION_PATH, "PLAYER_NOT_SERVER_PLAYER");
        }
        if (requestedIncrease <= 0) {
            return new MutationResult(false, requestedIncrease, MUTATION_PATH, "INVALID_AMOUNT");
        }
        TensuraCompatStatus status = TensuraCompat.getStatus();
        if (status.integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return new MutationResult(false, requestedIncrease, MUTATION_PATH, status.integrationState().name());
        }

        try {
            Double maxEpBefore = invokeStaticDouble("getMaxEP", serverPlayer);
            Double maxAuraBefore = invokeStaticDouble("getMaxAura", serverPlayer);
            Double maxMagiculeBefore = invokeStaticDouble("getMaxMagicule", serverPlayer);
            Object auraAttribute = getTensuraAttributeInstance(serverPlayer, "MAX_AURA");
            Object magiculeAttribute = getTensuraAttributeInstance(serverPlayer, "MAX_MAGICULE");
            if (auraAttribute == null && magiculeAttribute == null) {
                return new MutationResult(false, requestedIncrease, MUTATION_PATH,
                    "IllegalStateException - MAX_ENERGY_ATTRIBUTES_MISSING");
            }

            double splitAmount = requestedIncrease / 2.0D;
            if (auraAttribute != null) {
                setAttributeBaseValue(auraAttribute, getAttributeBaseValue(auraAttribute) + splitAmount);
            }
            if (magiculeAttribute != null) {
                setAttributeBaseValue(magiculeAttribute, getAttributeBaseValue(magiculeAttribute) + splitAmount);
            }

            Object existence = getExistence(serverPlayer);
            if (existence != null) {
                if (auraAttribute != null) {
                    invokeExistenceSetter(existence, "setAura", getAttributeValue(auraAttribute));
                }
                if (magiculeAttribute != null) {
                    invokeExistenceSetter(existence, "setMagicule", getAttributeValue(magiculeAttribute));
                }
                existence.getClass().getMethod("markDirty").invoke(existence);
            }

            boolean changed = changed(maxEpBefore, invokeStaticDouble("getMaxEP", serverPlayer))
                || changed(maxAuraBefore, invokeStaticDouble("getMaxAura", serverPlayer))
                || changed(maxMagiculeBefore, invokeStaticDouble("getMaxMagicule", serverPlayer));
            return new MutationResult(changed, requestedIncrease, MUTATION_PATH, "NONE");
        } catch (Exception | LinkageError ex) {
            return new MutationResult(false, requestedIncrease, MUTATION_PATH,
                ex.getClass().getSimpleName() + normalizeMessage(ex.getMessage()));
        }
    }

    private static Object getExistence(ServerPlayer player) throws Exception {
        Class<?> storagesClass = Class.forName(TENSURA_STORAGES_CLASS);
        Method method = storagesClass.getMethod("getExistenceFrom", LivingEntity.class);
        return method.invoke(null, player);
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

    private static Double invokeStaticDouble(String methodName, ServerPlayer player) {
        try {
            Class<?> helperClass = Class.forName(ENERGY_HELPER_CLASS);
            Object value = helperClass.getMethod(methodName, LivingEntity.class).invoke(null, player);
            return value instanceof Number number ? number.doubleValue() : null;
        } catch (Exception | LinkageError ex) {
            return null;
        }
    }

    private static boolean changed(Double before, Double after) {
        return Math.abs(safeNumber(after) - safeNumber(before)) > 0.0001D;
    }

    private static double safeNumber(Double value) {
        return value == null ? 0.0D : value.doubleValue();
    }

    private static String normalizeMessage(String message) {
        return message == null || message.isBlank() ? "" : " - " + message;
    }
}
