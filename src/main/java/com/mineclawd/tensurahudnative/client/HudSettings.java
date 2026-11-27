package com.mineclawd.tensurahudnative.client;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;

final class HudSettings {
    private static final String FILE_NAME = "tensura_hud_native-client.properties";
    private static HudSettings INSTANCE;

    boolean enabled = true;
    float hudScale = 1.0F;
    boolean hideVanillaHotbar = true;
    boolean hideVanillaVitals = true;
    boolean hideVanillaArmor = true;
    boolean hideVanillaExperience = true;
    boolean showInactiveObjective = true;
    boolean showUtilityButtons = true;
    boolean showSelectedItem = true;
    boolean showOverheadStatus = true;
    boolean testLocalThemeOverride = false;
    boolean themedDialogue = false;
    boolean showQuestTracker = true;
    String hudTheme = HudThemes.VANILLA_ID;
    String questTheme = QuestThemes.INHERIT_ID;
    String objectiveBridgePath = "";

    private final Path path;

    private HudSettings(Path path) {
        this.path = path;
    }

    static HudSettings get() {
        if (INSTANCE == null) {
            Minecraft minecraft = Minecraft.getInstance();
            Path configDir = minecraft.gameDirectory.toPath().resolve("config");
            INSTANCE = new HudSettings(configDir.resolve(FILE_NAME));
            INSTANCE.load();
        }
        return INSTANCE;
    }

    void toggle() {
        enabled = !enabled;
        save();
    }

    private void load() {
        Properties properties = defaults();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException ignored) {
            }
        }
        enabled = bool(properties, "enabled", true);
        hudScale = clamp(floatValue(properties, "hudScale", 1.0F), 0.65F, 1.5F);
        hideVanillaHotbar = bool(properties, "hideVanillaHotbar", true);
        hideVanillaVitals = bool(properties, "hideVanillaVitals", true);
        hideVanillaArmor = bool(properties, "hideVanillaArmor", true);
        hideVanillaExperience = bool(properties, "hideVanillaExperience", true);
        showInactiveObjective = bool(properties, "showInactiveObjective", true);
        showUtilityButtons = bool(properties, "showUtilityButtons", true);
        showSelectedItem = bool(properties, "showSelectedItem", true);
        showOverheadStatus = bool(properties, "showOverheadStatus", true);
        testLocalThemeOverride = "fixed".equals(properties.getProperty("themeMode", "automatic"));
        themedDialogue = bool(properties, "themedDialogue", false);
        showQuestTracker = bool(properties, "showQuestTracker", true);
        hudTheme = normalizedTheme(properties.getProperty("hudTheme", HudThemes.VANILLA_ID));
        questTheme = normalizedQuestTheme(properties.getProperty("questTheme", QuestThemes.INHERIT_ID));
        objectiveBridgePath = properties.getProperty("objectiveBridgePath", "").trim();
        if (!Files.isRegularFile(path)) {
            save();
        }
    }

    void save() {
        Properties properties = defaults();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("hudScale", Float.toString(hudScale));
        properties.setProperty("hideVanillaHotbar", Boolean.toString(hideVanillaHotbar));
        properties.setProperty("hideVanillaVitals", Boolean.toString(hideVanillaVitals));
        properties.setProperty("hideVanillaArmor", Boolean.toString(hideVanillaArmor));
        properties.setProperty("hideVanillaExperience", Boolean.toString(hideVanillaExperience));
        properties.setProperty("showInactiveObjective", Boolean.toString(showInactiveObjective));
        properties.setProperty("showUtilityButtons", Boolean.toString(showUtilityButtons));
        properties.setProperty("showSelectedItem", Boolean.toString(showSelectedItem));
        properties.setProperty("showOverheadStatus", Boolean.toString(showOverheadStatus));
        properties.setProperty("themeMode", testLocalThemeOverride ? "fixed" : "automatic");
        properties.setProperty("themedDialogue", Boolean.toString(themedDialogue));
        properties.setProperty("showQuestTracker", Boolean.toString(showQuestTracker));
        properties.setProperty("hudTheme", hudTheme);
        properties.setProperty("questTheme", questTheme);
        properties.setProperty("objectiveBridgePath", objectiveBridgePath);
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temp)) {
                properties.store(output, "Tensura HUD Native client configuration");
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    private static Properties defaults() {
        Properties properties = new Properties();
        properties.setProperty("enabled", "true");
        properties.setProperty("hudScale", "1.0");
        properties.setProperty("hideVanillaHotbar", "true");
        properties.setProperty("hideVanillaVitals", "true");
        properties.setProperty("hideVanillaArmor", "true");
        properties.setProperty("hideVanillaExperience", "true");
        properties.setProperty("showInactiveObjective", "true");
        properties.setProperty("showUtilityButtons", "true");
        properties.setProperty("showSelectedItem", "true");
        properties.setProperty("showOverheadStatus", "true");
        properties.setProperty("themeMode", "automatic");
        properties.setProperty("themedDialogue", "false");
        properties.setProperty("showQuestTracker", "true");
        properties.setProperty("hudTheme", HudThemes.VANILLA_ID);
        properties.setProperty("questTheme", QuestThemes.INHERIT_ID);
        properties.setProperty("objectiveBridgePath", "");
        return properties;
    }

    private static String normalizedTheme(String requested) {
        return HudThemes.isAvailable(requested) ? requested.trim().toLowerCase() : HudThemes.VANILLA_ID;
    }

    private static String normalizedQuestTheme(String requested) {
        String normalized = requested == null ? "" : requested.trim().toLowerCase(Locale.ROOT);
        return QuestThemes.INHERIT_ID.equals(normalized) || QuestThemes.isAvailable(normalized)
                ? normalized
                : QuestThemes.INHERIT_ID;
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static float floatValue(Properties properties, String key, float fallback) {
        try {
            return Float.parseFloat(properties.getProperty(key, Float.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
