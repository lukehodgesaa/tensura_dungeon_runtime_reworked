package com.mineclawd.tensurahudnative.client;

public final class HudPreferences {
    private HudPreferences() {
    }

    public static boolean themedDialogue() {
        return HudSettings.get().themedDialogue;
    }
}
