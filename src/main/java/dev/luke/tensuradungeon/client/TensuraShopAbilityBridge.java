package dev.luke.tensuradungeon.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class TensuraShopAbilityBridge {
    private static final String ASPECTUAL =
        "io.github.manasmods.tensura.registry.magic.AspectualMagics";
    private static final String SPIRITUAL =
        "io.github.manasmods.tensura.registry.magic.SpiritualMagics";
    private static final Map<String, AbilityMetadata> CACHE = new ConcurrentHashMap<>();

    private TensuraShopAbilityBridge() {}

    static AbilityMetadata resolve(String translationKey) {
        if (translationKey == null || translationKey.isBlank()) {
            return AbilityMetadata.NONE;
        }
        return CACHE.computeIfAbsent(translationKey, TensuraShopAbilityBridge::resolveUncached);
    }

    private static AbilityMetadata resolveUncached(String translationKey) {
        RegistryReference reference = registryReference(translationKey);
        ResourceLocation icon = null;
        Component name = Component.translatable(translationKey);
        Object ability = null;
        if (reference != null) {
            try {
                Class<?> registryOwner = Class.forName(reference.ownerClass());
                Field supplierField = registryOwner.getField(reference.fieldName());
                Object supplier = supplierField.get(null);
                ability = supplier instanceof Supplier<?> typed
                    ? typed.get()
                    : supplier.getClass().getMethod("get").invoke(supplier);
                Object nativeIcon = ability.getClass().getMethod("getSkillIcon").invoke(ability);
                if (nativeIcon instanceof ResourceLocation resourceLocation) {
                    icon = resourceLocation;
                }
                Object nativeName = ability.getClass().getMethod("getColoredName").invoke(ability);
                if (nativeName instanceof Component component) {
                    name = component;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        return new AbilityMetadata(
            name,
            Component.translatable(translationKey + ".description"),
            icon,
            ability
        );
    }

    static AbilityMetrics metrics(String translationKey, LivingEntity viewer) {
        AbilityMetadata metadata = resolve(translationKey);
        if (viewer == null || metadata.nativeAbility() == null) {
            return AbilityMetrics.UNAVAILABLE;
        }
        try {
            Object ability = metadata.nativeAbility();
            Object instance = playerInstanceOrDefault(viewer, ability);
            if (instance == null) return AbilityMetrics.UNAVAILABLE;
            Method costMethod = method(ability.getClass(), "getMagiculeCost", 3);
            if (costMethod == null) return AbilityMetrics.UNAVAILABLE;
            double cost = ((Number) costMethod.invoke(ability, viewer, instance, 0)).doubleValue();
            String output = rawOutput(translationKey, ability, instance, viewer);
            if (output.isBlank()) return AbilityMetrics.UNAVAILABLE;
            return new AbilityMetrics(output, wholeNumber(cost) + " MAGICULES", true);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return AbilityMetrics.UNAVAILABLE;
        }
    }

    private static Object playerInstanceOrDefault(LivingEntity viewer, Object ability)
        throws ReflectiveOperationException {
        Class<?> skillUtils = Class.forName("io.github.manasmods.tensura.ability.SkillUtils");
        Method getSkill = method(skillUtils, "getSkillOrNull", 2);
        Object instance = getSkill == null ? null : getSkill.invoke(null, viewer, ability);
        if (instance != null) return instance;
        Method createDefault = method(ability.getClass(), "createDefaultInstance", 0);
        return createDefault == null ? null : createDefault.invoke(ability);
    }

    private static String rawOutput(
        String translationKey,
        Object ability,
        Object instance,
        LivingEntity viewer
    ) throws ReflectiveOperationException {
        Object config = config(ability);
        if (config == null) return "";
        boolean mastered = isMastered(instance, viewer);
        return switch (translationKey) {
            case "tensura.skill.stone_shot" -> {
                double earth = number(config, "earthDamage");
                double magic = number(config, "magicDamage");
                yield decimal(earth + magic) + "/HIT ("
                    + decimal(earth) + " EARTH + " + decimal(magic) + " MAGIC)";
            }
            case "tensura.skill.icicle_lance" ->
                decimal(number(config, "magicDamage")) + " MAGIC/HIT";
            case "tensura.skill.thunder_lance" -> {
                double single = number(config, "lightningDamage")
                    + number(config, "magicDamage");
                double rain = number(config, "lightningDamageRain")
                    + number(config, "magicDamageRain");
                yield decimal(single) + "/HIT SINGLE · " + decimal(rain) + "/HIT RAIN";
            }
            case "tensura.skill.magic_barrier" -> "NON-DAMAGING";
            case "tensura.skill.acid_shell" ->
                decimal(number(config, "magicDamage")) + " MAGIC/HIT";
            case "tensura.skill.solar_flare" ->
                decimal(number(config, "flareDamage")) + " MAGIC AOE";
            case "tensura.skill.hellfire" ->
                decimal(number(config, mastered ? "sphereDamageMastered" : "sphereDamage"))
                    + " MAGIC/HIT";
            case "tensura.skill.chain_explosion" ->
                decimal(number(config, mastered ? "magicDamageMastered" : "magicDamage"))
                    + "/EXPLOSION x" + wholeNumber(number(config, "explosionNumber"));
            default -> "";
        };
    }

    private static Object config(Object ability) throws ReflectiveOperationException {
        Field configField = ability.getClass().getDeclaredField("CONFIG");
        configField.setAccessible(true);
        return configField.get(null);
    }

    private static boolean isMastered(Object instance, LivingEntity viewer)
        throws ReflectiveOperationException {
        Method mastered = method(instance.getClass(), "isMastered", 1);
        return mastered != null && Boolean.TRUE.equals(mastered.invoke(instance, viewer));
    }

    private static double number(Object target, String fieldName)
        throws ReflectiveOperationException {
        Field field = target.getClass().getField(fieldName);
        return ((Number) field.get(target)).doubleValue();
    }

    private static Method method(Class<?> type, String name, int parameterCount) {
        for (Method candidate : type.getMethods()) {
            if (candidate.getName().equals(name)
                && candidate.getParameterCount() == parameterCount) {
                return candidate;
            }
        }
        return null;
    }

    private static String wholeNumber(double value) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        format.setGroupingUsed(true);
        return format.format(Math.round(value));
    }

    private static String decimal(double value) {
        long whole = Math.round(value);
        if (Math.abs(value - whole) < 0.001D) return Long.toString(whole);
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static RegistryReference registryReference(String translationKey) {
        return switch (translationKey) {
            case "tensura.skill.stone_shot" ->
                new RegistryReference(ASPECTUAL, "STONE_SHOT");
            case "tensura.skill.icicle_lance" ->
                new RegistryReference(ASPECTUAL, "ICICLE_LANCE");
            case "tensura.skill.thunder_lance" ->
                new RegistryReference(ASPECTUAL, "THUNDER_LANCE");
            case "tensura.skill.magic_barrier" ->
                new RegistryReference(ASPECTUAL, "MAGIC_BARRIER");
            case "tensura.skill.acid_shell" ->
                new RegistryReference(ASPECTUAL, "ACID_SHELL");
            case "tensura.skill.chain_explosion" ->
                new RegistryReference(ASPECTUAL, "CHAIN_EXPLOSION");
            case "tensura.skill.solar_flare" ->
                new RegistryReference(SPIRITUAL, "SOLAR_FLARE");
            case "tensura.skill.hellfire" ->
                new RegistryReference(SPIRITUAL, "HELLFIRE");
            default -> null;
        };
    }

    record AbilityMetadata(
        Component name,
        Component description,
        ResourceLocation icon,
        Object nativeAbility
    ) {
        private static final AbilityMetadata NONE = new AbilityMetadata(
            Component.empty(),
            Component.empty(),
            null,
            null
        );

        boolean hasIcon() {
            return icon != null;
        }
    }

    record AbilityMetrics(String rawOutput, String magiculeCost, boolean available) {
        private static final AbilityMetrics UNAVAILABLE =
            new AbilityMetrics("", "", false);
    }

    private record RegistryReference(String ownerClass, String fieldName) {}
}
