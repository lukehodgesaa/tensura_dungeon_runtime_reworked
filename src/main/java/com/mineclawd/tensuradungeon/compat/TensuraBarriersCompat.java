package com.mineclawd.tensuradungeon.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

public final class TensuraBarriersCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "tensurabarriers";
    private static Method removeNow;
    private static boolean resolved;
    private static boolean warned;

    private TensuraBarriersCompat() {
    }

    public static VisualBarrierHandle.RemovalResult remove(int id) {
        if (id < 0) return VisualBarrierHandle.RemovalResult.ABSENT;
        if (!resolve()) return VisualBarrierHandle.RemovalResult.UNAVAILABLE;
        try {
            return (boolean) removeNow.invoke(null, id)
                ? VisualBarrierHandle.RemovalResult.REMOVED
                : VisualBarrierHandle.RemovalResult.ABSENT;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            warn(ex);
            return VisualBarrierHandle.RemovalResult.UNAVAILABLE;
        }
    }

    public static boolean isAvailable() {
        return resolve();
    }

    private static boolean resolve() {
        if (resolved) return removeNow != null;
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) return false;
        try {
            Class<?> api = Class.forName("com.tensurabarriers.api.TensuraBarriersAPI");
            removeNow = api.getMethod("removeNow", int.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            warn(ex);
            return false;
        }
    }

    private static void warn(Throwable ex) {
        if (warned) return;
        warned = true;
        LOGGER.warn("TensuraBarriers legacy-handle cleanup unavailable: {}", ex.toString());
    }
}
