package com.mineclawd.tensuradungeon.compat.tensura;

import com.mineclawd.tensuradungeon.runtime.DungeonModifierAvailability;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class TensuraAddonCompat {
    private static final String TENSURA_STORAGES = "io.github.manasmods.tensura.storage.TensuraStorages";
    private static final String TENSURA_EXISTENCE = "io.github.manasmods.tensura.storage.ep.IExistence";
    private static final String TENSURA_DAMAGE_HELPER = "io.github.manasmods.tensura.damage.TensuraDamageHelper";
    private static final String TENSURA_DAMAGE_SOURCE = "io.github.manasmods.tensura.damage.TensuraDamageSource";
    private static final String TENSURA_MOB_EFFECTS = "io.github.manasmods.tensura.registry.effect.TensuraMobEffects";
    private static final String TENSURA_SKILL_EVENTS = "io.github.manasmods.manascore.skill.api.SkillEvents";
    private static final String MANAS_SKILL_INSTANCE = "io.github.manasmods.manascore.skill.api.ManasSkillInstance";

    private TensuraAddonCompat() {
    }

    public static HookReport inspect() {
        if (!supportedRuntime()) {
            String reason = integrationReason();
            HookInspection unavailable = HookInspection.unavailable(reason);
            return new HookReport(unavailable, unavailable, unavailable, unavailable,
                HookInspection.unavailable(reason));
        }
        return new HookReport(
            inspectMagicule(),
            inspectNativeMagicClassification(),
            inspectDamageMetadata(),
            inspectCorrosion(),
            inspectSuccessfulCastEvent()
        );
    }

    public static OptionalDouble readMagicule(LivingEntity entity) {
        if (entity == null || !supportedRuntime()) {
            return OptionalDouble.empty();
        }
        try {
            Object existence = existence(entity);
            Object value = existence.getClass().getMethod("getMagicule").invoke(existence);
            if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                return OptionalDouble.of(Math.max(0.0D, number.doubleValue()));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return OptionalDouble.empty();
    }

    public static boolean writeMagicule(LivingEntity entity, double value) {
        if (entity == null || entity.level().isClientSide || !Double.isFinite(value) || value < 0.0D
            || !inspect().magicule().available()) {
            return false;
        }
        try {
            Object existence = existence(entity);
            existence.getClass().getMethod("setMagicule", double.class).invoke(existence, value);
            existence.getClass().getMethod("markDirty").invoke(existence);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean drainMagicule(LivingEntity entity, double amount) {
        if (entity == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return false;
        }
        OptionalDouble current = readMagicule(entity);
        if (current.isEmpty()) {
            return false;
        }
        double next = Math.max(0.0D, current.getAsDouble() - amount);
        return writeMagicule(entity, next);
    }

    public static boolean isNativeTensuraMagic(DamageSource source) {
        if (source == null || !inspect().nativeMagicClassification().available()) {
            return false;
        }
        try {
            Class<?> helper = load(TENSURA_DAMAGE_HELPER);
            Object value = helper.getMethod("isTensuraMagic", DamageSource.class).invoke(null, source);
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static Optional<NativeDamageMetadata> nativeDamageMetadata(DamageSource source) {
        if (source == null || !inspect().damageMetadata().available()) {
            return Optional.empty();
        }
        try {
            Class<?> sourceContract = load(TENSURA_DAMAGE_SOURCE);
            if (!sourceContract.isInstance(source)) {
                return Optional.empty();
            }
            Object ability = invoke(sourceContract, source, "tensura$getAbilityInstance");
            String abilityId = "";
            if (ability != null) {
                Object rawId = load(MANAS_SKILL_INSTANCE).getMethod("getSkillId").invoke(ability);
                abilityId = bounded(rawId == null ? "" : rawId.toString(), 128);
            }
            String magicType = enumName(invoke(sourceContract, source, "tensura$getMagicType"));
            String element = enumName(invoke(sourceContract, source, "tensura$getElement"));
            String skillType = enumName(invoke(sourceContract, source, "tensura$getSkillType"));
            boolean spiritual = Boolean.TRUE.equals(invoke(sourceContract, source, "tensura$isSpiritual"));
            if (abilityId.isBlank() && magicType.isBlank() && element.isBlank() && skillType.isBlank()
                && !spiritual) {
                return Optional.empty();
            }
            return Optional.of(new NativeDamageMetadata(
                isNativeTensuraMagic(source), abilityId, magicType, element, skillType, spiritual
            ));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    public static boolean applyCorrosion(LivingEntity entity, int durationTicks) {
        if (entity == null || entity.level().isClientSide || durationTicks <= 0) {
            return false;
        }
        Optional<Holder<MobEffect>> holder = corrosionHolder();
        if (holder.isEmpty()) {
            return false;
        }
        return entity.addEffect(new MobEffectInstance(
            holder.get(), durationTicks, 0, false, true, true
        ));
    }

    public static boolean clearCorrosion(LivingEntity entity) {
        Optional<Holder<MobEffect>> holder = corrosionHolder();
        return entity != null && holder.isPresent() && entity.removeEffect(holder.get());
    }

    private static HookInspection inspectMagicule() {
        try {
            Class<?> storages = load(TENSURA_STORAGES);
            Class<?> existence = load(TENSURA_EXISTENCE);
            storages.getMethod("getExistenceFrom", LivingEntity.class);
            existence.getMethod("getMagicule");
            existence.getMethod("setMagicule", double.class);
            existence.getMethod("markDirty");
            return HookInspection.available("MAGICULE_READ_WRITE_HOOK_VERIFIED");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return HookInspection.unavailable("MAGICULE_READ_WRITE_HOOK_UNAVAILABLE:" + ex.getClass().getSimpleName());
        }
    }

    private static HookInspection inspectNativeMagicClassification() {
        try {
            Class<?> helper = load(TENSURA_DAMAGE_HELPER);
            Class<?> sourceContract = load(TENSURA_DAMAGE_SOURCE);
            helper.getMethod("isTensuraMagic", DamageSource.class);
            sourceContract.getMethod("tensura$getMagicType");
            sourceContract.getMethod("tensura$getElement");
            return HookInspection.available("NATIVE_MAGIC_CLASSIFICATION_VERIFIED");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return HookInspection.unavailable("MAGIC_CLASSIFICATION_UNAVAILABLE:" + ex.getClass().getSimpleName());
        }
    }

    private static HookInspection inspectDamageMetadata() {
        try {
            Class<?> sourceContract = load(TENSURA_DAMAGE_SOURCE);
            Class<?> skillInstance = load(MANAS_SKILL_INSTANCE);
            for (String method : List.of(
                "tensura$getAbilityInstance", "tensura$getMagicType", "tensura$getElement",
                "tensura$getSkillType", "tensura$isSpiritual"
            )) {
                sourceContract.getMethod(method);
            }
            skillInstance.getMethod("getSkillId");
            return HookInspection.available("DAMAGE_METADATA_HOOK_VERIFIED");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return HookInspection.unavailable("DAMAGE_METADATA_HOOK_UNAVAILABLE:" + ex.getClass().getSimpleName());
        }
    }

    private static HookInspection inspectCorrosion() {
        try {
            Class<?> effects = load(TENSURA_MOB_EFFECTS);
            Field field = effects.getField("CORROSION");
            Object supplier = field.get(null);
            if (!(supplier instanceof Holder<?>)) {
                return HookInspection.unavailable("CORROSION_REGISTRY_HOLDER_UNAVAILABLE");
            }
            load("io.github.manasmods.tensura.effect.CorrosionEffect");
            return HookInspection.available("NATIVE_CORROSION_EFFECT_HOOK_VERIFIED");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return HookInspection.unavailable("CORROSION_HOOK_UNAVAILABLE:" + ex.getClass().getSimpleName());
        }
    }

    private static HookInspection inspectSuccessfulCastEvent() {
        try {
            Class<?> skillEvents = load(TENSURA_SKILL_EVENTS);
            for (String field : List.of("SUCCESSFUL_CAST", "CAST_SUCCESS", "SPELL_CAST_SUCCESS")) {
                try {
                    skillEvents.getField(field);
                    return HookInspection.unavailable("CAST_EVENT_SHAPE_UNVERIFIED");
                } catch (NoSuchFieldException ignored) {
                }
            }
            return HookInspection.unavailable("SUCCESSFUL_CAST_EVENT_UNEXPOSED");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return HookInspection.unavailable("SUCCESSFUL_CAST_EVENT_UNAVAILABLE:" + ex.getClass().getSimpleName());
        }
    }

    private static Optional<Holder<MobEffect>> corrosionHolder() {
        if (!inspect().corrosion().available()) {
            return Optional.empty();
        }
        try {
            Object supplier = load(TENSURA_MOB_EFFECTS).getField("CORROSION").get(null);
            if (!(supplier instanceof Holder<?> rawHolder)) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Holder<MobEffect> holder = (Holder<MobEffect>) rawHolder;
            return Optional.of(holder);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Object existence(LivingEntity entity) throws ReflectiveOperationException {
        Object existence = load(TENSURA_STORAGES)
            .getMethod("getExistenceFrom", LivingEntity.class)
            .invoke(null, entity);
        if (existence == null) {
            throw new IllegalStateException("Tensura existence storage is null");
        }
        return existence;
    }

    private static Object invoke(Class<?> owner, Object target, String method)
        throws ReflectiveOperationException {
        return owner.getMethod(method).invoke(target);
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name, false, TensuraAddonCompat.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new ClassNotFoundException("Tensura class load failed: " + name, ex);
        }
    }

    private static boolean supportedRuntime() {
        try {
            return TensuraCompat.getStatus().integrationState()
                == TensuraCompatStatus.IntegrationState.AVAILABLE;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static String integrationReason() {
        try {
            return "INTEGRATION_" + TensuraCompat.getStatus().integrationState().name();
        } catch (RuntimeException | LinkageError ex) {
            return "INTEGRATION_STATUS_UNAVAILABLE";
        }
    }

    private static String enumName(Object value) {
        if (value == null) return "";
        return bounded(value instanceof Enum<?> enumeration
            ? enumeration.name()
            : value.toString(), 64);
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    public record HookReport(
        HookInspection magicule,
        HookInspection nativeMagicClassification,
        HookInspection damageMetadata,
        HookInspection corrosion,
        HookInspection successfulCastEvent
    ) {
        public HookReport {
            magicule = normalize(magicule);
            nativeMagicClassification = normalize(nativeMagicClassification);
            damageMetadata = normalize(damageMetadata);
            corrosion = normalize(corrosion);
            successfulCastEvent = normalize(successfulCastEvent);
        }

        private static HookInspection normalize(HookInspection value) {
            return value == null ? HookInspection.unavailable("UNKNOWN") : value;
        }
    }

    public record HookInspection(boolean available, String reason) {
        public HookInspection {
            reason = reason == null || reason.isBlank() ? "UNKNOWN" : bounded(reason, 96);
        }

        private static HookInspection available(String reason) {
            return new HookInspection(true, reason);
        }

        private static HookInspection unavailable(String reason) {
            return new HookInspection(false, reason);
        }

        public DungeonModifierAvailability availability() {
            return available ? DungeonModifierAvailability.AVAILABLE
                : DungeonModifierAvailability.COMPATIBILITY_DISABLED;
        }
    }

    public record NativeDamageMetadata(
        boolean nativeMagic,
        String abilityId,
        String magicType,
        String element,
        String skillType,
        boolean spiritual
    ) {
        public NativeDamageMetadata {
            abilityId = bounded(abilityId, 128);
            magicType = bounded(magicType, 64);
            element = bounded(element, 64);
            skillType = bounded(skillType, 64);
        }

        public String metadataIdentity() {
            if (!abilityId.isBlank()) return "ability:" + abilityId;
            return String.join("|", List.of(
                "magic=" + magicType,
                "element=" + element,
                "skill=" + skillType,
                "spiritual=" + spiritual
            ));
        }
    }
}
