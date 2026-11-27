package com.mineclawd.tensuradungeon.compat.tensura;

import com.mineclawd.tensuradungeon.registry.ModDimensions;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public final class TensuraDaemonGroundingCompat {
    private static final Set<String> DUNGEON_DAEMON_IDS = Set.of(
        "tensura:lesser_daemon",
        "tensura:greater_daemon",
        "tensura:arch_daemon"
    );
    private static final String FLYING_CONTRACT =
        "io.github.manasmods.tensura.entity.template.subclass.IFlying";
    private static volatile FlyingMethods flyingMethods;

    private TensuraDaemonGroundingCompat() {
    }

    public static boolean shouldForceGrounded(
        ResourceKey<Level> dimension,
        String entityTypeId
    ) {
        return ModDimensions.DUNGEON.equals(dimension) && isDungeonDaemonId(entityTypeId);
    }

    public static boolean isDungeonDaemonId(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            return false;
        }
        return DUNGEON_DAEMON_IDS.contains(entityTypeId.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean forceGrounded(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()
            || !(entity.level().dimension().equals(ModDimensions.DUNGEON))) {
            return false;
        }
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityId == null || !isDungeonDaemonId(entityId.toString())) {
            return false;
        }

        entity.setNoGravity(false);
        if (!(entity instanceof Mob mob)) {
            return true;
        }
        FlyingMethods methods = flyingMethods();
        if (!methods.available()) {
            return true;
        }
        try {
            if (methods.switchNavigator() != null) {
                methods.switchNavigator().invoke(mob, mob, false);
            }
            if (methods.setFlying() != null) {
                methods.setFlying().invoke(mob, false);
            }
            if (methods.setWasFlying() != null) {
                methods.setWasFlying().invoke(mob, false);
            }
            if (methods.setFlyingTick() != null) {
                methods.setFlyingTick().invoke(mob, 0);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return true;
    }

    private static FlyingMethods flyingMethods() {
        FlyingMethods cached = flyingMethods;
        if (cached != null) {
            return cached;
        }
        synchronized (TensuraDaemonGroundingCompat.class) {
            if (flyingMethods == null) {
                flyingMethods = FlyingMethods.discover();
            }
            return flyingMethods;
        }
    }

    private record FlyingMethods(
        Method switchNavigator,
        Method setFlying,
        Method setWasFlying,
        Method setFlyingTick
    ) {
        private static FlyingMethods discover() {
            try {
                Class<?> contract = Class.forName(
                    FLYING_CONTRACT,
                    false,
                    TensuraDaemonGroundingCompat.class.getClassLoader()
                );
                return new FlyingMethods(
                    find(contract, "switchNavigator", Mob.class, boolean.class),
                    find(contract, "setFlying", boolean.class),
                    find(contract, "setWasFlying", boolean.class),
                    find(contract, "setFlyingTick", int.class)
                );
            } catch (ClassNotFoundException | LinkageError ignored) {
                return new FlyingMethods(null, null, null, null);
            }
        }

        private static Method find(Class<?> type, String name, Class<?>... parameters) {
            try {
                return type.getMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }

        private boolean available() {
            return setFlying() != null || setWasFlying() != null || setFlyingTick() != null;
        }
    }
}
