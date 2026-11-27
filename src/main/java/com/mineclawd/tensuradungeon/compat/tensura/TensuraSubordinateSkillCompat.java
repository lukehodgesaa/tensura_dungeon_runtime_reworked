package com.mineclawd.tensuradungeon.compat.tensura;

import com.mineclawd.tensuradungeon.item.SubordinateSkillScrollCatalog;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

public final class TensuraSubordinateSkillCompat {
    private static final String TENSURA_STORAGES_CLASS =
        "io.github.manasmods.tensura.storage.TensuraStorages";
    private static final String SKILL_API_CLASS =
        "io.github.manasmods.manascore.skill.api.SkillAPI";
    private static final String REGISTRAR_CLASS =
        "dev.architectury.registry.registries.Registrar";
    private static final String SKILL_STORAGE_CLASS =
        "io.github.manasmods.manascore.skill.impl.SkillStorage";
    private static final String SKILL_CLASS =
        "io.github.manasmods.manascore.skill.api.ManasSkill";
    private static final String SKILL_INSTANCE_CLASS =
        "io.github.manasmods.manascore.skill.api.ManasSkillInstance";
    private static final String TENSURA_SKILL_CLASS =
        "io.github.manasmods.tensura.ability.TensuraSkill";
    private static final String TENSURA_SKILL_TAGS_CLASS =
        "io.github.manasmods.tensura.data.TensuraSkillTags";
    private static final String TENSURA_SKILL_INSTANCE_CLASS =
        "io.github.manasmods.tensura.ability.TensuraSkillInstance";
    private static final String ABILITY_CLASS =
        "io.github.manasmods.tensura.storage.ability.IAbility";
    private static final String ABILITY_SLOT_CLASS =
        "io.github.manasmods.tensura.storage.ability.AbilitySlot";
    public static final int MAX_PROJECTED_SKILLS = 64;
    public static final int ACTIVE_SLOT_CAPACITY = 3;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Contract cachedContract;
    private static volatile String lastFailure = "NONE";
    private static volatile String lastLoggedFailure = "";

    private TensuraSubordinateSkillCompat() {}

    public static String availabilityReason() {
        return lastFailure;
    }

    public static SkillProjection learnedSkills(LivingEntity entity) {
        Contract contract = contract();
        if (entity == null || contract == null) {
            return SkillProjection.unavailable();
        }
        try {
            Object storage = contract.getSkillsFrom().invoke(null, entity);
            Object learned = contract.getLearnedSkills().invoke(storage);
            if (!(learned instanceof Iterable<?> iterable)) {
                return SkillProjection.unavailable();
            }
            OptionalDouble currentMagicule = TensuraCompat.readMagicule(entity);
            LinkedHashMap<String, LearnedSkillIcon> byId = new LinkedHashMap<>();
            for (Object instance : iterable) {
                if (instance == null) {
                    continue;
                }
                try {
                    Object skill = contract.getSkillFromInstance().invoke(instance);
                    if (skill == null) {
                        skill = instance;
                    }
                    String id = normalizedSkillId(
                        contract.getSkillId().invoke(instance),
                        contract.getRegistryName().invoke(skill)
                    );
                    if (id.isBlank()) {
                        continue;
                    }
                    Object icon = contract.getSkillIcon().invoke(skill);
                    byId.putIfAbsent(id, new LearnedSkillIcon(
                        id,
                        skillName(contract, skill, id),
                        icon == null ? "" : String.valueOf(icon),
                        isPassiveSkill(id, skill, instance, entity, contract),
                        skillDetails(entity, contract, instance, skill, 0, currentMagicule)
                    ));
                    if (byId.size() >= MAX_PROJECTED_SKILLS) {
                        break;
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
            }
            List<LearnedSkillIcon> result = new ArrayList<>(byId.values());
            result.sort(Comparator.comparing(LearnedSkillIcon::id));
            return new SkillProjection(true, List.copyOf(result));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("learned-skills", failure);
            return SkillProjection.unavailable();
        }
    }

    public static AbilityProjection currentAbilities(LivingEntity entity) {
        Contract contract = contract();
        if (entity == null || contract == null) {
            return AbilityProjection.unavailable();
        }
        try {
            Object storage = contract.getAbilityFrom().invoke(null, entity);
            if (storage == null) {
                return AbilityProjection.unavailable();
            }
            List<?> slots = abilitySlots(contract, storage);
            if (slots == null) {
                rememberFailure("active-ability-slots", null);
                return AbilityProjection.unavailable();
            }
            Object skillStorage = contract.getSkillsFrom().invoke(null, entity);
            OptionalDouble currentMagicule = TensuraCompat.readMagicule(entity);
            List<AbilitySlot> result = new ArrayList<>(3);
            for (int index = 0; index < 3; index++) {
                Object slot = index < slots.size() ? slots.get(index) : null;
                Object skill = invokeSafely(slot, "getSkill");
                boolean empty = slot == null
                    || (booleanValue(invokeSafely(slot, "isEmpty"), true) && skill == null);
                if (empty || skill == null) {
                    result.add(AbilitySlot.blank());
                    continue;
                }
                String id = normalizedSkillId(null, contract.getRegistryName().invoke(skill));
                if (id.isBlank()) {
                    result.add(AbilitySlot.blank());
                    continue;
                }
                Object icon = contract.getSkillIcon().invoke(skill);
                Object instance = liveSkillInstance(contract, skillStorage, skill);
                int mode = integer(invokeSafely(slot, "getMode"), 0);
                result.add(new AbilitySlot(
                    id,
                    skillName(contract, skill, id),
                    icon == null ? "" : String.valueOf(icon),
                    skillDetails(entity, contract, instance, skill, mode, currentMagicule)
                ));
            }
            return new AbilityProjection(true, List.copyOf(result));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("current-abilities", failure);
            return AbilityProjection.unavailable();
        }
    }

    public static LearnResult learn(LivingEntity entity, String rawSkillId) {
        ResourceLocation skillId = parseTensuraSkillId(rawSkillId);
        if (skillId == null) {
            return LearnResult.invalidId();
        }
        Contract contract = contract();
        if (entity == null || contract == null) {
            return new LearnResult(LearnStatus.UNAVAILABLE, skillId.toString());
        }
        try {
            Object registry = contract.getSkillRegistry().invoke(null);
            if (!Boolean.TRUE.equals(contract.contains().invoke(registry, skillId))) {
                return new LearnResult(LearnStatus.INVALID_ID, skillId.toString());
            }
            Object skill = contract.registryGet().invoke(registry, skillId);
            if (skill == null) {
                return new LearnResult(LearnStatus.INVALID_ID, skillId.toString());
            }

            Object storage = contract.getSkillsFrom().invoke(null, entity);
            Object existing = unwrapOptional(
                contract.getSkillFromStorage().invoke(storage, skillId)
            );
            if (existing != null) {
                return new LearnResult(LearnStatus.ALREADY_KNOWN, skillId.toString());
            }

            Object instance = contract.createDefaultInstance().invoke(skill);
            boolean learned = Boolean.TRUE.equals(
                contract.learnSkill().invoke(storage, instance, Component.empty())
            );
            return new LearnResult(
                learned ? LearnStatus.LEARNED : LearnStatus.FAILED,
                skillId.toString()
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("learn", failure);
            return new LearnResult(LearnStatus.FAILED, skillId.toString());
        }
    }

    public static SkillMutationResult assignActiveSkill(
        LivingEntity entity,
        String rawSkillId,
        int slot
    ) {
        ResourceLocation skillId = parseTensuraSkillId(rawSkillId);
        if (skillId == null) {
            return SkillMutationResult.invalidId();
        }
        if (entity == null || slot < 0 || slot >= ACTIVE_SLOT_CAPACITY) {
            return entity == null
                ? SkillMutationResult.unavailable(skillId.toString())
                : new SkillMutationResult(SkillMutationStatus.INVALID_SLOT, skillId.toString());
        }
        Contract contract = contract();
        if (contract == null) {
            return SkillMutationResult.unavailable(skillId.toString());
        }
        try {
            Object skill = registeredSkill(contract, skillId);
            if (skill == null) {
                return new SkillMutationResult(SkillMutationStatus.INVALID_ID, skillId.toString());
            }
            Object skillStorage = contract.getSkillsFrom().invoke(null, entity);
            Object learnedInstance = unwrapOptional(
                contract.getSkillFromStorage().invoke(skillStorage, skillId)
            );
            if (learnedInstance == null) {
                return new SkillMutationResult(SkillMutationStatus.NOT_LEARNED, skillId.toString());
            }
            if (isPassiveSkill(skillId.toString(), skill, learnedInstance, entity, contract)) {
                return new SkillMutationResult(
                    SkillMutationStatus.INELIGIBLE,
                    skillId.toString()
                );
            }

            Object abilityStorage = contract.getAbilityFrom().invoke(null, entity);
            if (abilityStorage == null) {
                return SkillMutationResult.unavailable(skillId.toString());
            }
            int preset = integer(invoke(abilityStorage, "getActivePreset"), 0);
            List<?> slots = abilitySlots(contract, abilityStorage);
            if (slots == null || slot >= slots.size()) {
                return new SkillMutationResult(SkillMutationStatus.INVALID_SLOT, skillId.toString());
            }
            for (int index = 0; index < slots.size(); index++) {
                if (index == slot) {
                    continue;
                }
                if (skillId.toString().equals(nativeSkillId(contract, slots.get(index)))) {
                    return new SkillMutationResult(
                        SkillMutationStatus.DUPLICATE,
                        skillId.toString()
                    );
                }
            }
            if (skillId.toString().equals(nativeSkillId(contract, slots.get(slot)))) {
                return new SkillMutationResult(SkillMutationStatus.NO_CHANGE, skillId.toString());
            }
            contract.setAbilitySlotBySkill().invoke(abilityStorage, preset, slot, skill, 0);
            contract.markAbilityDirty().invoke(abilityStorage);
            return new SkillMutationResult(SkillMutationStatus.APPLIED, skillId.toString());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("assign-active", failure);
            return new SkillMutationResult(SkillMutationStatus.FAILED, skillId.toString());
        }
    }

    public static SkillMutationResult clearActiveSkill(LivingEntity entity, int slot) {
        if (entity == null) {
            return SkillMutationResult.unavailable("");
        }
        if (slot < 0 || slot >= ACTIVE_SLOT_CAPACITY) {
            return new SkillMutationResult(SkillMutationStatus.INVALID_SLOT, "");
        }
        Contract contract = contract();
        if (contract == null) {
            return SkillMutationResult.unavailable("");
        }
        try {
            Object abilityStorage = contract.getAbilityFrom().invoke(null, entity);
            if (abilityStorage == null) {
                return SkillMutationResult.unavailable("");
            }
            int preset = integer(invoke(abilityStorage, "getActivePreset"), 0);
            List<?> slots = abilitySlots(contract, abilityStorage);
            if (slots == null || slot >= slots.size()) {
                return new SkillMutationResult(SkillMutationStatus.INVALID_SLOT, "");
            }
            String currentId = nativeSkillId(contract, slots.get(slot));
            if (currentId.isBlank()) {
                return new SkillMutationResult(SkillMutationStatus.NO_CHANGE, "");
            }
            Object empty = contract.emptyAbilitySlot().invoke(null);
            contract.setAbilitySlotBySlot().invoke(abilityStorage, preset, slot, empty);
            contract.markAbilityDirty().invoke(abilityStorage);
            return new SkillMutationResult(SkillMutationStatus.CLEARED, currentId);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("clear-active", failure);
            return new SkillMutationResult(SkillMutationStatus.FAILED, "");
        }
    }

    public static TechniqueProjection inspectCoreTechnique(
        LivingEntity entity,
        String rawSkillId,
        int mode
    ) {
        ResourceLocation skillId = parseTensuraSkillId(rawSkillId);
        if (skillId == null) return TechniqueProjection.invalid();
        Contract contract = contract();
        if (entity == null || contract == null) {
            return new TechniqueProjection(
                TechniqueStatus.UNAVAILABLE, skillId.toString(), "", Math.max(0, mode), 0.0D
            );
        }
        try {
            Object skill = registeredSkill(contract, skillId);
            if (skill == null) {
                return new TechniqueProjection(
                    TechniqueStatus.INVALID_ID, skillId.toString(), "", Math.max(0, mode), 0.0D
                );
            }
            Object instance = contract.createDefaultInstance().invoke(skill);
            if (isPassiveSkill(
                skillId.toString(), skill, instance, entity, contract
            )) {
                return new TechniqueProjection(
                    TechniqueStatus.INELIGIBLE, skillId.toString(), skillName(contract, skill, skillId.toString()),
                    Math.max(0, mode), 0.0D
                );
            }
            Double cost = nativeMagiculeCost(contract, entity, instance, skill, Math.max(0, mode));
            if (cost == null || !Double.isFinite(cost)) {
                return new TechniqueProjection(
                    TechniqueStatus.COST_UNAVAILABLE, skillId.toString(),
                    skillName(contract, skill, skillId.toString()), Math.max(0, mode), 0.0D
                );
            }
            return new TechniqueProjection(
                TechniqueStatus.AVAILABLE,
                skillId.toString(),
                skillName(contract, skill, skillId.toString()),
                Math.max(0, mode),
                cost
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("core-technique-inspect", failure);
            return new TechniqueProjection(
                TechniqueStatus.FAILED, skillId.toString(), "", Math.max(0, mode), 0.0D
            );
        }
    }

    public static ActivationResult activateCoreTechnique(
        LivingEntity entity,
        String rawSkillId,
        int mode,
        int reservedSlot
    ) {
        ResourceLocation skillId = parseTensuraSkillId(rawSkillId);
        if (skillId == null) return new ActivationResult(ActivationStatus.INVALID_SLOT, "");
        Contract contract = contract();
        if (entity == null || contract == null || contract.startHoldSkillInstance() == null) {
            return new ActivationResult(ActivationStatus.UNAVAILABLE, skillId.toString());
        }
        try {
            Object skill = registeredSkill(contract, skillId);
            if (skill == null) return new ActivationResult(ActivationStatus.EMPTY, skillId.toString());
            Object storage = contract.getSkillsFrom().invoke(null, entity);
            Object instance = contract.createDefaultInstance().invoke(skill);
            if (isPassiveSkill(
                skillId.toString(), skill, instance, entity, contract
            )) {
                return new ActivationResult(ActivationStatus.INELIGIBLE, skillId.toString());
            }
            boolean started = Boolean.TRUE.equals(contract.startHoldSkillInstance().invoke(
                storage, instance, reservedSlot, Math.max(0, mode)
            ));
            return new ActivationResult(
                started ? ActivationStatus.ACTIVATED : ActivationStatus.NOT_READY,
                skillId.toString()
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("core-technique-activate", failure);
            return new ActivationResult(ActivationStatus.FAILED, skillId.toString());
        }
    }

    public static ResourceLocation parseTensuraSkillId(String rawSkillId) {
        if (rawSkillId == null || rawSkillId.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(rawSkillId);
        return parsed != null && "tensura".equals(parsed.getNamespace()) ? parsed : null;
    }

    private static String normalizedSkillId(Object primary, Object fallback) {
        for (Object candidate : new Object[] {primary, fallback}) {
            if (candidate == null) {
                continue;
            }
            ResourceLocation parsed = ResourceLocation.tryParse(String.valueOf(candidate));
            if (parsed != null && "tensura".equals(parsed.getNamespace())) {
                return parsed.toString();
            }
        }
        return "";
    }

    private static Object registeredSkill(Contract contract, ResourceLocation skillId)
        throws ReflectiveOperationException {
        Object registry = contract.getSkillRegistry().invoke(null);
        if (!Boolean.TRUE.equals(contract.contains().invoke(registry, skillId))) {
            return null;
        }
        return contract.registryGet().invoke(registry, skillId);
    }

    private static String nativeSkillId(Contract contract, Object abilitySlot)
        throws ReflectiveOperationException {
        Object skill = invokeSafely(abilitySlot, "getSkill");
        return skill == null
            ? ""
            : normalizedSkillId(null, contract.getRegistryName().invoke(skill));
    }

    private static Object liveSkillInstance(
        Contract contract,
        Object skillStorage,
        Object skill
    ) {
        if (skillStorage == null || skill == null) {
            return null;
        }
        try {
            Object lookup = contract.getSkillFromStorage().invoke(
                skillStorage, contract.getRegistryName().invoke(skill)
            );
            Object direct = unwrapOptional(lookup);
            if (direct != null) {
                return direct;
            }
            String registryName = String.valueOf(contract.getRegistryName().invoke(skill));
            Object learned = contract.getLearnedSkills().invoke(skillStorage);
            if (learned instanceof Iterable<?> iterable) {
                for (Object instance : iterable) {
                    String id = String.valueOf(contract.getSkillId().invoke(instance));
                    if (!registryName.isBlank() && registryName.equals(id)) {
                        return instance;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return null;
    }

    private static SkillDetails skillDetails(
        LivingEntity entity,
        Contract contract,
        Object instance,
        Object skill,
        int mode,
        OptionalDouble currentMagicule
    ) {
        int safeMode = Math.max(0, mode);
        Integer cooldown = nativeCooldownSeconds(entity, contract, instance, skill, safeMode);
        Double magiculeCost = nativeMagiculeCost(contract, entity, instance, skill, safeMode);
        boolean resourceAvailable = currentMagicule.isPresent();
        boolean enoughMagicule = magiculeCost == null
            || !resourceAvailable
            || currentMagicule.getAsDouble() >= magiculeCost;
        return new SkillDetails(
            safeMode,
            magiculeCost == null ? 0.0D : magiculeCost,
            cooldown == null ? 0 : cooldown,
            magiculeCost != null,
            cooldown != null,
            resourceAvailable,
            enoughMagicule
        );
    }

    private static Double nativeMagiculeCost(
        Contract contract,
        LivingEntity entity,
        Object instance,
        Object skill,
        int mode
    ) {
        if (entity == null || skill == null) {
            return null;
        }
        if (instance != null && contract.getMagiculeCostInstance() != null) {
            try {
                Object value = contract.getMagiculeCostInstance().invoke(instance, entity, mode);
                if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                    return Math.max(0.0D, number.doubleValue());
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        if (instance != null && contract.getMagiculeCostSkill() != null) {
            try {
                Object value = contract.getMagiculeCostSkill().invoke(skill, entity, instance, mode);
                if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                    return Math.max(0.0D, number.doubleValue());
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        return null;
    }

    private static Integer nativeCooldownSeconds(
        LivingEntity entity,
        Contract contract,
        Object instance,
        Object skill,
        int mode
    ) {
        if (skill == null) {
            return null;
        }
        try {
            Integer live = nativeLiveCooldown(contract, instance, mode);
            if (live != null && live > 0) {
                return live;
            }
            Object config = skillConfig(skill);
            if (config == null) {
                return null;
            }
            boolean mastered = nativeMastered(instance, entity);
            String modeId = nativeModeId(contract, skill, instance, mode);
            List<Field> cooldownFields = numericCooldownFields(config);
            if (cooldownFields.isEmpty()) {
                return null;
            }
            for (String candidate : cooldownFieldCandidates(modeId, mastered)) {
                Field field = findField(cooldownFields, candidate);
                Integer value = field == null ? null : cooldownValue(field, config);
                if (value != null) {
                    return value;
                }
            }

            Integer onlyValue = null;
            int positiveCount = 0;
            for (Field field : cooldownFields) {
                Integer value = cooldownValue(field, config);
                if (value != null) {
                    onlyValue = value;
                    positiveCount++;
                }
            }
            if (positiveCount == 1) {
                return onlyValue;
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return null;
        }
    }

    private static Integer nativeLiveCooldown(Contract contract, Object instance, int mode) {
        if (instance == null) {
            return null;
        }
        for (Method method : new Method[] {contract.getCoolDown(), contract.getCooldown()}) {
            if (method == null) {
                continue;
            }
            try {
                Object value = method.invoke(instance, Math.max(0, mode));
                if (value instanceof Number number && number.intValue() > 0) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        return null;
    }

    private static boolean nativeMastered(Object instance, LivingEntity entity) {
        if (instance == null || entity == null) {
            return false;
        }
        try {
            Method masteredMethod = instance.getClass().getMethod("isMastered", LivingEntity.class);
            return Boolean.TRUE.equals(masteredMethod.invoke(instance, entity));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static String nativeModeId(
        Contract contract,
        Object skill,
        Object instance,
        int mode
    ) {
        if (contract.getModeId() == null || skill == null || instance == null) {
            return "";
        }
        try {
            Object value = contract.getModeId().invoke(skill, instance, Math.max(0, mode));
            return value == null ? "" : String.valueOf(value);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private static List<Field> numericCooldownFields(Object config) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = config.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                Class<?> fieldType = field.getType();
                if (name.contains("cooldown")
                    && (fieldType == int.class || fieldType == long.class
                        || fieldType == float.class || fieldType == double.class
                        || Number.class.isAssignableFrom(fieldType))) {
                    fields.add(field);
                }
            }
        }
        fields.sort(Comparator.comparing(field -> field.getName().toLowerCase(Locale.ROOT)));
        return fields;
    }

    private static List<String> cooldownFieldCandidates(String modeId, boolean mastered) {
        List<String> candidates = new ArrayList<>();
        String mode = configToken(modeId);
        if ("default".equals(mode)) {
            mode = "";
        }
        if (mastered && !mode.isBlank()) {
            candidates.add("cooldown" + mode + "mastered");
            candidates.add(mode + "cooldownmastered");
            candidates.add("cooldownmastered" + mode);
        }
        if (!mode.isBlank()) {
            candidates.add("cooldown" + mode);
            candidates.add(mode + "cooldown");
        }
        if (mastered) {
            candidates.add("cooldownmastered");
        }
        candidates.add("cooldown");
        candidates.add("defaultcooldown");
        candidates.add("basecooldown");
        return candidates;
    }

    private static String configToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder token = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                token.append(Character.toLowerCase(character));
            }
        }
        return token.toString();
    }

    private static Field findField(List<Field> fields, String normalizedName) {
        for (Field field : fields) {
            if (configToken(field.getName()).equals(normalizedName)) {
                return field;
            }
        }
        return null;
    }

    private static Object skillConfig(Object skill) throws ReflectiveOperationException {
        for (Class<?> type = skill.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                    && field.getName().equalsIgnoreCase("CONFIG")) {
                    field.setAccessible(true);
                    return field.get(null);
                }
            }
        }
        return null;
    }

    private static Integer cooldownValue(Field field, Object config) {
        try {
            field.setAccessible(true);
            Object value = field.get(config);
            if (value instanceof Number number) {
                int seconds = number.intValue();
                return seconds > 0 ? seconds : null;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return null;
    }

    private static String skillName(Contract contract, Object skill, String id) {
        for (Method method : new Method[] {
            contract.getColoredName(), contract.getName(), contract.getDisplayName()
        }) {
            if (method == null) {
                continue;
            }
            try {
                Object value = method.invoke(skill);
                String text = value instanceof Component component
                    ? component.getString()
                    : value == null ? "" : String.valueOf(value);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        return prettifySkillId(id);
    }

    private static boolean isPassiveSkill(
        String id,
        Object skill,
        Object instance,
        LivingEntity entity,
        Contract contract
    ) {
        Optional<SubordinateSkillScrollCatalog.Definition> approved =
            SubordinateSkillScrollCatalog.findSkill(id);
        if (approved.isPresent() && approved.get().passive()) {
            return approved.get().passive();
        }
        Boolean nativeSlotEligible = nativeSlotEligible(contract, skill, instance, entity);
        if (nativeSlotEligible != null) {
            return !nativeSlotEligible;
        }
        return isTaggedSkill(contract, instance, contract == null ? null : contract.resistanceSkillTag())
            || isTaggedSkill(contract, instance, contract == null ? null : contract.intrinsicSkillTag());
    }

    private static Boolean nativeSlotEligible(
        Contract contract,
        Object skill,
        Object instance,
        LivingEntity entity
    ) {
        if (contract == null || contract.getCanBeSlotted() == null
            || skill == null || instance == null || entity == null) {
            return null;
        }
        try {
            Object value = contract.getCanBeSlotted().invoke(skill, instance, entity, 0);
            return value instanceof Boolean bool ? bool : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean isTaggedSkill(Contract contract, Object instance, Object tag) {
        if (contract == null || contract.getSkillTagCheck() == null || instance == null || tag == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(contract.getSkillTagCheck().invoke(instance, tag));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static String prettifySkillId(String id) {
        if (id == null || id.isBlank()) {
            return "Unknown Ability";
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
        return result.isEmpty() ? "Unknown Ability" : result.toString();
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static Object invoke(Object target, String methodName)
        throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private static Object invoke(
        Object target,
        String methodName,
        Class<?> parameterType,
        Object argument
    ) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName, parameterType).invoke(target, argument);
    }

    private static Object invokeSafely(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return invoke(target, methodName);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static List<Object> toList(Iterable<?> iterable) {
        List<Object> result = new ArrayList<>();
        for (Object value : iterable) {
            result.add(value);
        }
        return result;
    }

    private static List<?> abilitySlots(Contract contract, Object storage)
        throws ReflectiveOperationException {
        Object value = contract.getAbilitySlots().invoke(storage);
        if (!(value instanceof Iterable<?> iterable)) {
            return null;
        }
        return iterable instanceof List<?> list ? list : toList(iterable);
    }

    private static boolean hasAbility(List<?> slots) {
        for (Object slot : slots) {
            Object skill = invokeSafely(slot, "getSkill");
            if (skill != null || !booleanValue(invokeSafely(slot, "isEmpty"), true)) {
                return true;
            }
        }
        return false;
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    private static Contract contract() {
        try {
            TensuraCompatStatus status = TensuraCompat.getStatus();
            if (status.integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
                lastFailure = "NATIVE_COMPAT_" + status.integrationState().name()
                    + ":" + status.lastProbeErrorSummary();
                return null;
            }
        } catch (RuntimeException | LinkageError failure) {
            rememberFailure("compat-status", failure);
            return null;
        }
        Contract existing = cachedContract;
        if (existing != null) {
            return existing;
        }
        try {
            Class<?> storages = Class.forName(TENSURA_STORAGES_CLASS);
            Class<?> api = Class.forName(SKILL_API_CLASS);
            Class<?> registrar = Class.forName(REGISTRAR_CLASS);
            Class<?> storage = Class.forName(SKILL_STORAGE_CLASS);
            Class<?> skill = Class.forName(SKILL_CLASS);
            Class<?> instance = Class.forName(SKILL_INSTANCE_CLASS);
            Class<?> tensuraSkill = Class.forName(TENSURA_SKILL_CLASS);
            Class<?> skillTags = optionalClass(TENSURA_SKILL_TAGS_CLASS);
            Class<?> tensuraSkillInstance = Class.forName(TENSURA_SKILL_INSTANCE_CLASS);
            Class<?> ability = Class.forName(ABILITY_CLASS);
            Class<?> abilitySlot = Class.forName(ABILITY_SLOT_CLASS);
            Contract resolved = new Contract(
                storages.getMethod("getAbilityFrom", LivingEntity.class),
                api.getMethod("getSkillRegistry"),
                api.getMethod("getSkillsFrom", LivingEntity.class),
                storage.getMethod("getLearnedSkills"),
                storage.getMethod("getSkill", ResourceLocation.class),
                storage.getMethod("learnSkill", instance, MutableComponent.class),
                instance.getMethod("getSkill"),
                instance.getMethod("getSkillId"),
                skill.getMethod("getRegistryName"),
                skill.getMethod("getSkillIcon"),
                skill.getMethod("createDefaultInstance"),
                registrar.getMethod("contains", ResourceLocation.class),
                registrar.getMethod("get", ResourceLocation.class),
                optionalMethod(skill, "getColoredName"),
                optionalMethod(skill, "getName"),
                optionalMethod(skill, "getDisplayName"),
                optionalMethod(instance, "getCoolDown", int.class),
                optionalMethod(instance, "getCooldown", int.class),
                optionalMethod(instance, "canActivateSkill", LivingEntity.class, int.class),
                optionalMethod(tensuraSkillInstance, "getMagiculeCost", LivingEntity.class, int.class),
                optionalMethod(tensuraSkill, "getMagiculeCost", LivingEntity.class, instance, int.class),
                optionalMethod(tensuraSkill, "getModeId", instance, int.class),
                optionalMethod(tensuraSkill, "canBeSlotted", instance, LivingEntity.class, int.class),
                optionalMethod(instance, "is", TagKey.class),
                staticField(skillTags, "RESISTANCE_SKILLS"),
                staticField(skillTags, "INTRINSIC_SKILLS"),
                optionalMethod(storage, "startHoldSkill", instance, int.class, int.class),
                ability.getMethod("getAbilitySlots"),
                ability.getMethod(
                    "setAbilitySlot", int.class, int.class, skill, int.class
                ),
                ability.getMethod("setAbilitySlot", int.class, int.class, abilitySlot),
                abilitySlot.getMethod("getEmpty"),
                ability.getMethod("markDirty")
            );
            cachedContract = resolved;
            lastFailure = "NONE";
            return resolved;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("contract", failure);
            return null;
        }
    }

    private static void rememberFailure(String phase, Throwable failure) {
        Throwable root = rootCause(failure);
        String detail = root == null
            ? "MISSING_OR_EMPTY"
            : root.getClass().getSimpleName();
        String message = root == null || root.getMessage() == null
            ? ""
            : root.getMessage().replaceAll("\\s+", " ");
        if (message.length() > 96) {
            message = message.substring(0, 96);
        }
        String reason = phase + ":" + detail + (message.isBlank() ? "" : ":" + message);
        lastFailure = reason;
        if (!reason.equals(lastLoggedFailure)) {
            lastLoggedFailure = reason;
            LOGGER.warn("Native subordinate skill bridge unavailable: {}", reason);
        }
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current instanceof InvocationTargetException invocation
            && invocation.getCause() != null) {
            current = invocation.getCause();
        }
        return current;
    }

    public record SkillProjection(boolean available, List<LearnedSkillIcon> skills) {
        public SkillProjection {
            skills = skills == null ? List.of() : List.copyOf(skills);
        }

        private static SkillProjection unavailable() {
            return new SkillProjection(false, List.of());
        }
    }

    public record AbilityProjection(boolean available, List<AbilitySlot> slots) {
        public AbilityProjection {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }

        private static AbilityProjection unavailable() {
            return new AbilityProjection(false, List.of());
        }
    }

    public record AbilitySlot(String id, String name, String icon, SkillDetails details) {
        public AbilitySlot(String id, String name, String icon) {
            this(id, name, icon, SkillDetails.unavailable());
        }

        public AbilitySlot {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            icon = icon == null ? "" : icon;
            details = details == null ? SkillDetails.unavailable() : details;
        }

        private static AbilitySlot blank() {
            return new AbilitySlot("", "", "", SkillDetails.unavailable());
        }

        public boolean empty() {
            return id.isBlank();
        }
    }

    public record LearnedSkillIcon(
        String id,
        String name,
        String icon,
        boolean passive,
        SkillDetails details
    ) {
        public LearnedSkillIcon(String id, String icon) {
            this(id, prettifySkillId(id), icon, false, SkillDetails.unavailable());
        }

        public LearnedSkillIcon(String id, String name, String icon, boolean passive) {
            this(id, name, icon, passive, SkillDetails.unavailable());
        }

        public LearnedSkillIcon {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            icon = icon == null ? "" : icon;
            details = details == null ? SkillDetails.unavailable() : details;
        }
    }

    public record SkillDetails(
        int mode,
        double magiculeCost,
        int cooldownSeconds,
        boolean magiculeCostAvailable,
        boolean cooldownAvailable,
        boolean resourceAvailable,
        boolean enoughMagicule
    ) {
        public SkillDetails {
            mode = Math.max(0, mode);
            magiculeCost = Double.isFinite(magiculeCost) ? Math.max(0.0D, magiculeCost) : 0.0D;
            cooldownSeconds = Math.max(0, cooldownSeconds);
        }

        private static SkillDetails unavailable() {
            return new SkillDetails(0, 0.0D, 0, false, false, false, true);
        }

        public boolean available() {
            return magiculeCostAvailable || cooldownAvailable;
        }

        public boolean warning() {
            return magiculeCostAvailable && resourceAvailable && !enoughMagicule;
        }
    }

    public enum LearnStatus {
        LEARNED,
        ALREADY_KNOWN,
        INVALID_ID,
        UNAVAILABLE,
        FAILED
    }

    public enum SkillMutationStatus {
        APPLIED,
        CLEARED,
        NO_CHANGE,
        INVALID_ID,
        NOT_LEARNED,
        INELIGIBLE,
        DUPLICATE,
        INVALID_SLOT,
        UNAVAILABLE,
        FAILED
    }

    public record SkillMutationResult(SkillMutationStatus status, String skillId) {
        public SkillMutationResult {
            status = status == null ? SkillMutationStatus.FAILED : status;
            skillId = skillId == null ? "" : skillId;
        }

        private static SkillMutationResult invalidId() {
            return new SkillMutationResult(SkillMutationStatus.INVALID_ID, "");
        }

        private static SkillMutationResult unavailable(String skillId) {
            return new SkillMutationResult(SkillMutationStatus.UNAVAILABLE, skillId);
        }
    }

    public record LearnResult(LearnStatus status, String skillId) {
        public LearnResult {
            status = status == null ? LearnStatus.FAILED : status;
            skillId = skillId == null ? "" : skillId;
        }

        private static LearnResult invalidId() {
            return new LearnResult(LearnStatus.INVALID_ID, "");
        }
    }

    public record ActivationResult(
        ActivationStatus status,
        String skillId,
        int slot,
        String detail
    ) {
        public ActivationResult(ActivationStatus status, String skillId) {
            this(status, skillId, -1, "");
        }

        public ActivationResult(ActivationStatus status, String skillId, int slot) {
            this(status, skillId, slot, "");
        }

        public ActivationResult {
            status = status == null ? ActivationStatus.FAILED : status;
            skillId = skillId == null ? "" : skillId;
            slot = slot < -1 ? -1 : slot;
            detail = detail == null ? "" : detail;
            if (detail.length() > 160) {
                detail = detail.substring(0, 160);
            }
        }
    }

    public enum ActivationStatus {
        ACTIVATED,
        RELEASED,
        HELD,
        EMPTY,
        NOT_READY,
        INELIGIBLE,
        INVALID_SLOT,
        UNAVAILABLE,
        FAILED
    }

    private record Contract(
        Method getAbilityFrom,
        Method getSkillRegistry,
        Method getSkillsFrom,
        Method getLearnedSkills,
        Method getSkillFromStorage,
        Method learnSkill,
        Method getSkillFromInstance,
        Method getSkillId,
        Method getRegistryName,
        Method getSkillIcon,
        Method createDefaultInstance,
        Method contains,
        Method registryGet,
        Method getColoredName,
        Method getName,
        Method getDisplayName,
        Method getCoolDown,
        Method getCooldown,
        Method getCanActivateSkill,
        Method getMagiculeCostInstance,
        Method getMagiculeCostSkill,
        Method getModeId,
        Method getCanBeSlotted,
        Method getSkillTagCheck,
        Object resistanceSkillTag,
        Object intrinsicSkillTag,
        Method startHoldSkillInstance,
        Method getAbilitySlots,
        Method setAbilitySlotBySkill,
        Method setAbilitySlotBySlot,
        Method emptyAbilitySlot,
        Method markAbilityDirty
    ) {}

    public record TechniqueProjection(
        TechniqueStatus status,
        String skillId,
        String name,
        int mode,
        double magiculeCost
    ) {
        public TechniqueProjection {
            status = status == null ? TechniqueStatus.FAILED : status;
            skillId = skillId == null ? "" : skillId;
            name = name == null ? "" : name;
            mode = Math.max(0, mode);
            magiculeCost = Double.isFinite(magiculeCost) ? Math.max(0.0D, magiculeCost) : 0.0D;
        }

        private static TechniqueProjection invalid() {
            return new TechniqueProjection(TechniqueStatus.INVALID_ID, "", "", 0, 0.0D);
        }
    }

    public enum TechniqueStatus {
        AVAILABLE,
        INVALID_ID,
        INELIGIBLE,
        COST_UNAVAILABLE,
        UNAVAILABLE,
        FAILED
    }

    private static Method optionalMethod(Class<?> type, String name) {
        if (type == null) {
            return null;
        }
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method optionalMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        if (type == null) {
            return null;
        }
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name, false, TensuraSubordinateSkillCompat.class.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object staticField(Class<?> type, String name) {
        if (type == null) {
            return null;
        }
        try {
            return type.getField(name).get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
