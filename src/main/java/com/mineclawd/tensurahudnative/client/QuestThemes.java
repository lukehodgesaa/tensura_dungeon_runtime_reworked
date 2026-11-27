package com.mineclawd.tensurahudnative.client;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class QuestThemes {
    public static final String INHERIT_ID = "inherit";
    public static final String GOTHIC_ID = "gothic";
    public static final String FREY_ID = "frey";
    public static final String RIMURU_ID = "rimuru";
    public static final String BENIMARU_ID = "benimaru";
    private static final String LEGACY_HARPY_ID = "harpy";

    private static final Map<String, QuestTheme> THEMES = createThemes();
    private static volatile String runtimeQuestThemeId = "";

    private QuestThemes() {
    }

    public static Set<String> availableQuestThemeIds() {
        return THEMES.keySet();
    }

    public static boolean isAvailable(String id) {
        return THEMES.containsKey(normalize(id));
    }

    public static void setRuntimeQuestTheme(String id) {
        String normalized = normalize(id);
        runtimeQuestThemeId = INHERIT_ID.equals(normalized) || isAvailable(normalized)
                ? normalized
                : "";
    }

    public static void clearRuntimeQuestTheme() {
        runtimeQuestThemeId = "";
    }

    static HudTheme.ObjectiveVisual activeObjective(HudSettings settings, HudTheme hudTheme) {
        String requested = settings.testLocalThemeOverride || runtimeQuestThemeId.isBlank()
                ? settings.questTheme
                : runtimeQuestThemeId;
        String normalized = normalize(requested);
        if (normalized.isBlank() || INHERIT_ID.equals(normalized)) {
            return hudTheme.presentation().objective();
        }
        QuestTheme selected = THEMES.get(normalized);
        return selected == null ? hudTheme.presentation().objective() : selected.objective();
    }

    static QuestTheme resolve(String id) {
        return THEMES.getOrDefault(normalize(id), THEMES.get(GOTHIC_ID));
    }

    private static Map<String, QuestTheme> createThemes() {
        Map<String, QuestTheme> themes = new LinkedHashMap<>();
        HudTheme gothicHud = HudThemes.resolve(HudThemes.GOTHIC_ID);
        QuestTheme gothic = new QuestTheme(
                GOTHIC_ID,
                "Moon-Silver Gothic Quest",
                gothicHud.presentation().objective()
        );
        themes.put(gothic.id(), gothic);
        HudTheme freyHud = HudThemes.resolve(HudThemes.FREY_ID);
        QuestTheme frey = new QuestTheme(
                FREY_ID,
                "Frey Quest",
                freyHud.presentation().objective()
        );
        themes.put(frey.id(), frey);
        HudTheme rimuruHud = HudThemes.resolve(HudThemes.RIMURU_ID);
        QuestTheme rimuru = new QuestTheme(
                RIMURU_ID,
                "Rimuru Slime Quest",
                rimuruHud.presentation().objective()
        );
        themes.put(rimuru.id(), rimuru);
        HudTheme benimaruHud = HudThemes.resolve(HudThemes.BENIMARU_ID);
        QuestTheme benimaru = new QuestTheme(
                BENIMARU_ID,
                "Benimaru Horned Tsuba Quest",
                benimaruHud.presentation().objective()
        );
        themes.put(benimaru.id(), benimaru);
        return Map.copyOf(themes);
    }

    private static String normalize(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return LEGACY_HARPY_ID.equals(normalized) ? FREY_ID : normalized;
    }
}
