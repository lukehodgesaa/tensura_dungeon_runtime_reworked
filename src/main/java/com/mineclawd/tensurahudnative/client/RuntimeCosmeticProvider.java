package com.mineclawd.tensurahudnative.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

final class RuntimeCosmeticProvider {
    private static final String BRIDGE_CLASS =
            "com.mineclawd.tensuradungeon.client.DungeonCosmeticClient";
    private static final String BRIDGE_METHOD = "snapshot";

    private static boolean resolved;
    private static Method snapshotMethod;
    private static long appliedRevision = Long.MIN_VALUE;
    private static String appliedHudTheme = "";
    private static String appliedQuestTheme = "";
    private static String appliedPreviewHudTheme = "";

    private RuntimeCosmeticProvider() {
    }

    static void sync() {
        Method method = bridgeMethod();
        if (method == null) {
            return;
        }
        try {
            Object value = method.invoke(null);
            if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
                return;
            }
            Selection selection = selection(map);
            if (selection.revision() < 0L
                    || (selection.revision() == appliedRevision
                    && selection.hudTheme().equals(appliedHudTheme)
                    && selection.questTheme().equals(appliedQuestTheme)
                    && selection.previewHudTheme().equals(appliedPreviewHudTheme))) {
                return;
            }
            HudThemes.setRuntimeTheme(selection.hudTheme());
            HudThemes.setRuntimePreviewTheme(selection.previewHudTheme());
            QuestThemes.setRuntimeQuestTheme(selection.questTheme());
            appliedRevision = selection.revision();
            appliedHudTheme = selection.hudTheme();
            appliedQuestTheme = selection.questTheme();
            appliedPreviewHudTheme = selection.previewHudTheme();
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
        }
    }

    static Selection selection(Map<?, ?> map) {
        Object revisionValue = map.get("revision");
        long revision = revisionValue instanceof Number number ? number.longValue() : -1L;
        Object hudValue = map.get("hudTheme");
        Object questValue = map.get("questTheme");
        Object previewHudValue = map.get("hudPreviewTheme");
        return new Selection(
                revision,
                hudValue instanceof String text ? text : "",
                questValue instanceof String text ? text : "",
                previewHudValue instanceof String text ? text : ""
        );
    }

    private static synchronized Method bridgeMethod() {
        if (!resolved) {
            resolved = true;
            try {
                snapshotMethod = Class.forName(BRIDGE_CLASS).getMethod(BRIDGE_METHOD);
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
                snapshotMethod = null;
            }
        }
        return snapshotMethod;
    }

    record Selection(long revision, String hudTheme, String questTheme, String previewHudTheme) {
    }
}
