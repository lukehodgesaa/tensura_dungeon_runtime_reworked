package com.mineclawd.tensuradungeon.compat.twilightteleport;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

public final class TwilightTeleportCompat {
    private static final String MOD_ID = "twilightteleport";
    private static final String MANAGER_CLASS =
        "net.ochibo.twilightteleport.server.PendingTeleportManager";
    private static final String MARK_METHOD = "markNextExternalTeleportAsRift";
    private static final String CLEAR_METHOD = "clearNextExternalTeleportAsRift";

    private static boolean resolved;
    private static Method markMethod;
    private static Method clearMethod;

    private TwilightTeleportCompat() {
    }

    public static boolean markNextExternalTeleportAsRift(ServerPlayer player) {
        resolve();
        if (player == null || markMethod == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(markMethod.invoke(null, player));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static void clearNextExternalTeleportAsRift(ServerPlayer player) {
        resolve();
        if (player == null || clearMethod == null) {
            return;
        }
        try {
            clearMethod.invoke(null, player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            Class<?> manager = Class.forName(
                MANAGER_CLASS,
                false,
                TwilightTeleportCompat.class.getClassLoader()
            );
            markMethod = manager.getMethod(MARK_METHOD, ServerPlayer.class);
            clearMethod = manager.getMethod(CLEAR_METHOD, ServerPlayer.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            markMethod = null;
            clearMethod = null;
        }
    }
}
