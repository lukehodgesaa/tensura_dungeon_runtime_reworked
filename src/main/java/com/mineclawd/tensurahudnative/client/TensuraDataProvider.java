package com.mineclawd.tensurahudnative.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class TensuraDataProvider {
    private static final String STORAGES = "io.github.manasmods.tensura.storage.TensuraStorages";
    private static final String ATTRIBUTES = "io.github.manasmods.tensura.registry.attribute.TensuraAttributes";
    private static final String SKILL_API = "io.github.manasmods.manascore.skill.api.SkillAPI";
    private static final String RACE_API = "io.github.manasmods.manascore.race.api.RaceAPI";
    private static final String KEYBINDS = "io.github.manasmods.tensura.client.TensuraKeybinds";

    private TensuraDataProvider() {
    }

    static Sample sample(Minecraft minecraft, LocalPlayer player, HudSettings settings, long now) {
        Object existence = ReflectionUtil.callStatic(STORAGES, "getExistenceFrom", player);
        Object abilityStorage = ReflectionUtil.callStatic(STORAGES, "getAbilityFrom", player);
        Object playerData = ReflectionUtil.callStatic(STORAGES, "getPlayerDataFrom", player);
        JsonObjectiveProvider.BridgeSnapshot bridge = JsonObjectiveProvider.read(minecraft, settings, now);
        RuntimeObjectiveProvider.Result runtimeObjective = RuntimeObjectiveProvider.read(now);

        double health = player.getHealth();
        double maxHealth = Math.max(1.0D, player.getMaxHealth());
        double ep = number(existence, "getEP", Double.NaN);
        double magicule = number(existence, "getMagicule", 0.0D);
        double aura = number(existence, "getAura", 0.0D);
        double maxMagicule = attribute(player, "MAX_MAGICULE", Math.max(1.0D, magicule));
        double maxAura = attribute(player, "MAX_AURA", Math.max(1.0D, aura));

        RaceState raceState = raceState(player, playerData);
        String race = firstNonBlank(raceState.raceName, bridge.race(), "Unknown");
        String currentForm = firstNonBlank(raceState.currentForm, bridge.currentForm(), race);
        int level = player.experienceLevel >= 0 ? player.experienceLevel : bridge.level();
        if (!Double.isFinite(ep)) {
            ep = bridge.ep();
        }
        if (!Double.isFinite(ep)) {
            ep = 0.0D;
        }

        HudSnapshot.Status status = new HudSnapshot.Status(
                safeComponent(player.getName(), "Player"),
                race,
                currentForm,
                Math.max(0, level),
                ep,
                health,
                maxHealth,
                magicule,
                maxMagicule,
                aura,
                maxAura,
                player.getArmorValue(),
                player.getFoodData() == null ? 0.0F : player.getFoodData().getSaturationLevel()
        );

        List<HudSnapshot.Ability> abilities = abilities(player, abilityStorage);
        ItemStack selected = player.getInventory().getSelected();
        int selectedSlot = player.getInventory().selected;
        ResourceLocation itemId = selected == null || selected.isEmpty() ? null
                : BuiltInRegistries.ITEM.getKey(selected.getItem());
        HudSnapshot.SelectedItem selectedItem = new HudSnapshot.SelectedItem(
                selected == null ? ItemStack.EMPTY : selected,
                selected == null || selected.isEmpty() ? "Empty Hand" : safeComponent(selected.getHoverName(), "Selected Item"),
                Integer.toString(selectedSlot + 1),
                selectedSlot,
                itemId == null ? "" : itemId.toString()
        );

        HudSnapshot.Evolution evolution = new HudSnapshot.Evolution(
                currentForm,
                raceState.trackedName,
                raceState.trackedProgress,
                bool(existence, "isDemonLordSeed"),
                bool(existence, "isTrueDemonLord"),
                bool(existence, "isHeroEgg"),
                bool(existence, "isTrueHero"),
                bool(existence, "isBlessed"),
                bool(existence, "isSpiritualForm"),
                integer(existence, "getSoulPoints", 0),
                objectString(ReflectionUtil.call(existence, "getAlignment"), "NEUTRAL")
        );

        String presetName = abilityStorage == null ? "" : objectString(
                ReflectionUtil.call(abilityStorage, "getPresetName", integer(abilityStorage, "getActivePreset", 0)), "");
        HudSnapshot.Objective objective = runtimeObjective.available()
                ? runtimeObjective.objective()
                : bridge.objective();
        HudSnapshot snapshot = new HudSnapshot(status, abilities, selectedItem, objective, evolution, presetName, now);
        return new Sample(snapshot, bridge.unlock());
    }

    static List<HudSnapshot.LearnedSkill> learnedSkills(LocalPlayer player) {
        Object storage = ReflectionUtil.callStatic(SKILL_API, "getSkillsFrom", player);
        Object learned = ReflectionUtil.call(storage, "getLearnedSkills");
        if (!(learned instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<HudSnapshot.LearnedSkill> result = new ArrayList<>();
        for (Object instance : iterable) {
            if (instance == null) {
                continue;
            }
            Object skill = firstNonNull(ReflectionUtil.call(instance, "getSkill"), instance);
            String id = objectString(firstNonNull(
                    ReflectionUtil.call(instance, "getSkillId"),
                    ReflectionUtil.call(skill, "getRegistryName")
            ), "");
            if (id.isBlank()) {
                continue;
            }
            result.add(new HudSnapshot.LearnedSkill(
                    id,
                    skillName(skill),
                    objectString(ReflectionUtil.call(skill, "getSkillIcon"), ""),
                    skillCategory(id, skill)
            ));
        }
        return List.copyOf(result);
    }

    static double magiculePercent(LivingEntity entity) {
        if (entity == null) {
            return 0.0D;
        }
        Object existence = ReflectionUtil.callStatic(STORAGES, "getExistenceFrom", entity);
        double current = number(existence, "getMagicule", Double.NaN);
        if (!Double.isFinite(current) || current <= 0.0D) {
            return 0.0D;
        }
        double maximum = attribute(entity, "MAX_MAGICULE", current);
        return maximum <= 0.0D ? 0.0D : clamp01((float) (current / maximum));
    }

    private static List<HudSnapshot.Ability> abilities(LocalPlayer player, Object abilityStorage) {
        List<?> slots = resolveDisplayedSlots(abilityStorage);
        String[] keys = new String[]{
                keyLabel("ACTIVATE_SLOT_1", "Z"),
                keyLabel("ACTIVATE_SLOT_2", "X"),
                keyLabel("ACTIVATE_SLOT_3", "C")
        };
        List<HudSnapshot.Ability> result = new ArrayList<>(3);
        Object skillStorage = ReflectionUtil.callStatic(SKILL_API, "getSkillsFrom", player);
        for (int index = 0; index < 3; index++) {
            Object slot = slots != null && index < slots.size() ? slots.get(index) : null;
            Object skill = ReflectionUtil.call(slot, "getSkill");
            boolean empty = slot == null || (ReflectionUtil.bool(ReflectionUtil.call(slot, "isEmpty"), false) && skill == null);
            if (empty || skill == null) {
                result.add(HudSnapshot.Ability.empty(keys[index]));
                continue;
            }
            int mode = integer(slot, "getMode", 0);
            Object instance = liveSkillInstance(skillStorage, skill);
            String id = objectString(ReflectionUtil.call(skill, "getRegistryName"), "");
            result.add(new HudSnapshot.Ability(
                    keys[index],
                    skillName(skill),
                    id,
                    skillModeName(skill, instance, mode),
                    mode,
                    cooldown(instance, mode),
                    mastery(instance),
                    false,
                    objectString(ReflectionUtil.call(skill, "getSkillIcon"), ""),
                    skillCategory(id, skill)
            ));
        }
        return List.copyOf(result);
    }

    private static List<?> resolveDisplayedSlots(Object abilityStorage) {
        if (abilityStorage == null) {
            return List.of();
        }
        int activePreset = integer(abilityStorage, "getActivePreset", 0);
        List<?> active = asList(ReflectionUtil.call(abilityStorage, "getAbilitySlots", activePreset));
        if (hasAbility(active)) {
            return active;
        }
        for (int preset = 0; preset <= 9; preset++) {
            List<?> candidate = asList(ReflectionUtil.call(abilityStorage, "getAbilitySlots", preset));
            if (hasAbility(candidate)) {
                return candidate;
            }
        }
        return active;
    }

    private static boolean hasAbility(List<?> slots) {
        if (slots == null) {
            return false;
        }
        for (Object slot : slots) {
            Object skill = ReflectionUtil.call(slot, "getSkill");
            if (skill != null || !ReflectionUtil.bool(ReflectionUtil.call(slot, "isEmpty"), true)) {
                return true;
            }
        }
        return false;
    }

    private static List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of();
    }

    private static Object liveSkillInstance(Object skillStorage, Object skill) {
        Object lookup = ReflectionUtil.call(skillStorage, "getSkill", skill);
        Object direct = ReflectionUtil.unwrapOptional(lookup);
        if (direct != null) {
            return direct;
        }
        String registryName = objectString(ReflectionUtil.call(skill, "getRegistryName"), "");
        Object learned = ReflectionUtil.call(skillStorage, "getLearnedSkills");
        if (learned instanceof Iterable<?> iterable) {
            for (Object instance : iterable) {
                String id = objectString(ReflectionUtil.call(instance, "getSkillId"), "");
                if (!registryName.isBlank() && registryName.equals(id)) {
                    return instance;
                }
            }
        }
        return null;
    }

    private static float mastery(Object instance) {
        if (instance == null) {
            return 0.0F;
        }
        double current = firstFinite(
                number(instance, "getMastery", Double.NaN),
                number(instance, "getMasteryXp", Double.NaN),
                number(instance, "getExperience", Double.NaN),
                number(instance, "getProgress", Double.NaN)
        );
        double max = firstFinite(
                number(instance, "getMaxMastery", Double.NaN),
                number(instance, "getRequiredXP", Double.NaN),
                number(instance, "getMaxExperience", Double.NaN),
                number(instance, "getMaxProgress", Double.NaN)
        );
        if (Double.isFinite(current) && Double.isFinite(max) && max > 0.0D) {
            return clamp01((float) (current / max));
        }
        for (String method : new String[]{"getMasteryPercent", "getMasteryProgress", "getProgressPercent"}) {
            double percent = number(instance, method, Double.NaN);
            if (Double.isFinite(percent)) {
                if (percent > 1.0D && percent <= 100.0D) {
                    percent /= 100.0D;
                }
                return clamp01((float) percent);
            }
        }
        return 0.0F;
    }

    private static int cooldown(Object instance, int mode) {
        if (instance == null) {
            return 0;
        }
        int best = 0;
        for (int candidate : mode > 0 ? new int[]{mode, mode - 1, 0} : new int[]{mode, 0}) {
            if (candidate < 0) {
                continue;
            }
            best = Math.max(best, ReflectionUtil.integer(ReflectionUtil.call(instance, "getCoolDown", candidate), 0));
            best = Math.max(best, ReflectionUtil.integer(ReflectionUtil.call(instance, "getCooldown", candidate), 0));
        }
        return Math.max(0, best);
    }

    static String skillModeName(Object skill, Object instance, int mode) {
        for (String method : new String[]{"getModeName", "getModeDisplayName", "getModeComponent", "getDisplayMode",
                "getStringOfMode", "modeToString", "getSubAbilityModeName"}) {
            String value = cleanModeLabel(ReflectionUtil.componentString(
                    ReflectionUtil.call(skill, method, instance, mode)));
            if (!value.isBlank()) {
                return value;
            }
        }
        for (String method : new String[]{"getModeName", "getModeDisplayName", "getModeComponent", "getDisplayMode",
                "getStringOfMode", "modeToString", "getSubAbilityModeName"}) {
            String value = cleanModeLabel(ReflectionUtil.componentString(ReflectionUtil.call(skill, method, mode)));
            if (!value.isBlank()) {
                return value;
            }
        }

        String modeId = cleanModeId(objectString(ReflectionUtil.call(skill, "getModeId", instance, mode), ""));
        if (!modeId.isBlank()) {
            String registryName = objectString(ReflectionUtil.call(skill, "getRegistryName"), "");
            int separator = registryName.indexOf(':');
            String namespace = separator > 0 ? registryName.substring(0, separator) : "tensura";
            String path = separator >= 0 && separator + 1 < registryName.length()
                    ? registryName.substring(separator + 1) : registryName;
            if (!path.isBlank()) {
                String key = namespace + ".skill.mode." + path + "." + modeId;
                String translated = cleanModeLabel(
                        ReflectionUtil.componentString(Component.translatable(key)));
                if (!translated.isBlank() && !translated.equals(key)) {
                    return translated;
                }
            }
            return prettifyId(modeId);
        }
        return mode <= 0 ? "Default" : "Mode " + mode;
    }

    private static String cleanModeLabel(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.isBlank() || cleaned.equalsIgnoreCase("null")
                || cleaned.contains("@") || cleaned.contains(".skill.mode.")) {
            return "";
        }
        return cleaned;
    }

    private static String cleanModeId(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        return cleaned.equalsIgnoreCase("null") || cleaned.contains("@") ? "" : cleaned;
    }

    private static String skillName(Object skill) {
        for (String method : new String[]{"getColoredName", "getName", "getDisplayName"}) {
            String value = ReflectionUtil.componentString(ReflectionUtil.call(skill, method));
            if (!value.isBlank()) {
                return value;
            }
        }
        String simple = skill == null ? "Empty" : skill.getClass().getSimpleName();
        return simple.replace("Skill", "").replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private static String keyLabel(String fieldName, String fallback) {
        Object wrapper = ReflectionUtil.staticField(KEYBINDS, fieldName);
        if (wrapper == null) {
            return fallback;
        }
        for (Object candidate : new Object[]{
                wrapper,
                ReflectionUtil.call(wrapper, "getKeybind"),
                ReflectionUtil.call(wrapper, "getKeyMapping"),
                ReflectionUtil.call(wrapper, "getMapping"),
                ReflectionUtil.call(wrapper, "getVanilla"),
                ReflectionUtil.call(wrapper, "getKey")
        }) {
            String label = ReflectionUtil.componentString(ReflectionUtil.call(candidate, "getTranslatedKeyMessage"));
            if (label.isBlank()) {
                Object key = ReflectionUtil.call(candidate, "getKey");
                label = ReflectionUtil.componentString(ReflectionUtil.call(key, "getDisplayName"));
            }
            label = normalizeKeyLabel(label);
            if (!label.isBlank()) {
                return label;
            }
        }
        return fallback;
    }

    static String normalizeKeyLabel(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        for (String prefix : new String[]{"key.keyboard.", "Keyboard ", "Key "}) {
            if (normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
                normalized = normalized.substring(prefix.length());
            }
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static RaceState raceState(LocalPlayer player, Object playerData) {
        Object races = ReflectionUtil.callStatic(RACE_API, "getRaceFrom", player);
        Object instance = ReflectionUtil.unwrapOptional(ReflectionUtil.call(races, "getRace"));
        Object race = ReflectionUtil.call(instance, "getRace");
        String current = firstNonBlank(
                ReflectionUtil.componentString(ReflectionUtil.call(instance, "getDisplayName")),
                ReflectionUtil.componentString(ReflectionUtil.call(race, "getName")),
                prettifyId(objectString(ReflectionUtil.call(instance, "getRaceId"), ""))
        );
        Object tracked = ReflectionUtil.call(playerData, "getTrackedEvolution");
        String trackedName = firstNonBlank(
                ReflectionUtil.componentString(ReflectionUtil.call(tracked, "getName")),
                prettifyId(objectString(ReflectionUtil.call(tracked, "getRegistryName"), ""))
        );
        float progress = tracked == null || instance == null ? 0.0F
                : normalizeProgress(ReflectionUtil.number(
                        ReflectionUtil.call(instance, "getEvolutionProgress", player, tracked), 0.0D));
        return new RaceState(current, current, trackedName, progress);
    }

    static float normalizeProgress(double rawProgress) {
        if (!Double.isFinite(rawProgress) || rawProgress <= 0.0D) {
            return 0.0F;
        }
        double normalized = rawProgress > 1.0D ? rawProgress / 100.0D : rawProgress;
        return clamp01((float) normalized);
    }

    private static double attribute(LivingEntity player, String fieldName, double fallback) {
        Object attribute = ReflectionUtil.staticField(ATTRIBUTES, fieldName);
        double value = ReflectionUtil.number(ReflectionUtil.call(player, "getAttributeValue", attribute), Double.NaN);
        return Double.isFinite(value) && value > 0.0D ? value : Math.max(1.0D, fallback);
    }

    private static double number(Object target, String method, double fallback) {
        return ReflectionUtil.number(ReflectionUtil.call(target, method), fallback);
    }

    private static int integer(Object target, String method, int fallback) {
        return ReflectionUtil.integer(ReflectionUtil.call(target, method), fallback);
    }

    private static boolean bool(Object target, String method) {
        return ReflectionUtil.bool(ReflectionUtil.call(target, method), false);
    }

    private static String safeComponent(Object value, String fallback) {
        String text = ReflectionUtil.componentString(value);
        return text.isBlank() ? fallback : text;
    }

    private static String objectString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static double firstFinite(double... values) {
        for (double value : values) {
            if (Double.isFinite(value)) {
                return value;
            }
        }
        return Double.NaN;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static String prettifyId(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        StringBuilder result = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private static String skillCategory(String id, Object skill) {
        String combined = (id + " " + (skill == null ? "" : skill.getClass().getName())).toLowerCase(Locale.ROOT);
        if (combined.contains("ultimate")) return "ultimate";
        if (combined.contains("unique")) return "unique";
        if (combined.contains("resistance")) return "resistance";
        if (combined.contains("intrinsic")) return "intrinsic";
        if (combined.contains("extra")) return "extra";
        return "common";
    }

    record Sample(HudSnapshot snapshot, JsonObjectiveProvider.UnlockEvent unlockEvent) {
    }

    private record RaceState(String raceName, String currentForm, String trackedName, float trackedProgress) {
    }
}
