package com.mineclawd.tensurahudnative.client;

final class HudPreviewController {
    private static final String BRIDGE_CLASS =
            "com.mineclawd.tensuradungeon.client.DungeonCosmeticClient";

    private HudPreviewController() {
    }

    static boolean isActive() {
        return ReflectionUtil.bool(
                ReflectionUtil.callStatic(BRIDGE_CLASS, "isHudPreviewActive"), false);
    }

    static int remainingSeconds(long now) {
        return Math.max(0, ReflectionUtil.integer(
                ReflectionUtil.callStatic(BRIDGE_CLASS, "hudPreviewRemainingSeconds"), 0));
    }

    static void cancel() {
        ReflectionUtil.callStatic(BRIDGE_CLASS, "cancelHudPreview");
    }

    static int remainingSeconds(long previewEndsAt, long now) {
        if (previewEndsAt <= now) {
            return 0;
        }
        long remaining = previewEndsAt - now;
        return (int) Math.max(1L, (remaining + 999L) / 1_000L);
    }
}
