package com.mineclawd.tensuradungeon.compat.tensura;

import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

public final class TensuraSubordinateCastCompat {
    private static final String TENSURA_STORAGES_CLASS =
        "io.github.manasmods.tensura.storage.TensuraStorages";
    private static final String SKILL_API_CLASS =
        "io.github.manasmods.manascore.skill.api.SkillAPI";
    private static final String SKILL_STORAGE_CLASS =
        "io.github.manasmods.manascore.skill.impl.SkillStorage";
    private static final String SKILL_CLASS =
        "io.github.manasmods.manascore.skill.api.ManasSkill";
    private static final String SKILL_INSTANCE_CLASS =
        "io.github.manasmods.manascore.skill.api.ManasSkillInstance";
    private static final String TENSURA_MAGIC_CLASS =
        "io.github.manasmods.tensura.ability.magic.Magic";
    private static final String ABILITY_CLASS =
        "io.github.manasmods.tensura.storage.ability.IAbility";
    private static final String ABILITY_SLOT_CLASS =
        "io.github.manasmods.tensura.storage.ability.AbilitySlot";
    private static final String TICKING_SKILL_CLASS =
        "io.github.manasmods.manascore.skill.impl.TickingSkill";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Contract cachedContract;
    private static volatile String lastFailure = "NONE";
    private static volatile String lastLoggedFailure = "";

    private TensuraSubordinateCastCompat() {
    }

    public static String availabilityReason() {
        return lastFailure;
    }

    public static CastProbe inspectAssignedSkill(LivingEntity entity, int slot) {
        if (entity == null) {
            return CastProbe.unavailable(slot, "ENTITY_NULL");
        }
        if (slot < 0 || slot >= TensuraSubordinateSkillCompat.ACTIVE_SLOT_CAPACITY) {
            return CastProbe.unavailable(slot, "SLOT_OUT_OF_RANGE");
        }
        Contract contract = contract();
        if (contract == null) {
            return CastProbe.unavailable(slot, availabilityReason());
        }
        try {
            Object ability = contract.getAbilityFrom().invoke(null, entity);
            Object skills = contract.getSkillsFrom().invoke(null, entity);
            if (ability == null || skills == null) {
                return CastProbe.unavailable(slot, "NATIVE_STORAGE_MISSING");
            }
            List<?> slots = abilitySlots(contract, ability);
            if (slots == null || slot >= slots.size()) {
                return CastProbe.unavailable(slot, "ABILITY_SLOT_UNAVAILABLE");
            }
            Object abilitySlot = slots.get(slot);
            Object skill = invokeSafely(contract.getAbilitySlotSkill(), abilitySlot);
            if (skill == null || Boolean.TRUE.equals(
                invokeSafely(contract.getAbilitySlotEmpty(), abilitySlot)
            )) {
                return CastProbe.empty(slot);
            }
            String skillId = String.valueOf(contract.getRegistryName().invoke(skill));
            int mode = Math.max(0, integer(invokeSafely(contract.getAbilitySlotMode(), abilitySlot), 0));
            Object instance = liveSkillInstance(contract, skills, skillId);
            if (instance == null) {
                return CastProbe.unavailable(slot, skillId + ":LEARNED_INSTANCE_MISSING");
            }
            NativeCastProfile profile = profileFor(skillId, mode);
            if (profile == NativeCastProfile.UNSUPPORTED) {
                return new CastProbe(
                    ProbeStatus.UNSUPPORTED,
                    new NativeCastDescriptor(skillId, slot, mode, 1, profile, instance),
                    "UNSUPPORTED_NATIVE_CAST_PROFILE"
                );
            }
            int castTicks = nativeCastingTime(contract, instance, entity, mode);
            if (profile == NativeCastProfile.REPEAT) {
                castTicks = Math.max(1, castTicks);
            }
            return new CastProbe(
                ProbeStatus.AVAILABLE,
                new NativeCastDescriptor(skillId, slot, mode, castTicks, profile, instance),
                "NATIVE_PROFILE_AVAILABLE"
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("inspect-assigned", failure);
            return CastProbe.failed(slot, lastFailure);
        }
    }

    public static StartResult start(LivingEntity entity, NativeCastDescriptor descriptor) {
        if (entity == null || descriptor == null) {
            return new StartResult(StartStatus.FAILED, "ENTITY_OR_DESCRIPTOR_NULL");
        }
        Contract contract = contract();
        if (contract == null) {
            return new StartResult(StartStatus.FAILED, availabilityReason());
        }
        try {
            Object storage = contract.getSkillsFrom().invoke(null, entity);
            ResourceLocation skillId = ResourceLocation.tryParse(descriptor.skillId());
            if (storage == null || skillId == null || !"tensura".equals(skillId.getNamespace())) {
                return new StartResult(StartStatus.REJECTED_NATIVE, "INVALID_NATIVE_SKILL_ID");
            }
            boolean started = Boolean.TRUE.equals(contract.startHoldSkill().invoke(
                storage, skillId, descriptor.slot(), descriptor.mode()
            ));
            if (started) {
                return new StartResult(StartStatus.ACCEPTED, "NATIVE_START_HOLD_ACCEPTED");
            }
            return new StartResult(
                rejectionStatus(contract, descriptor.instance(), entity, descriptor.mode()),
                rejectionDetail(contract, descriptor.instance(), entity, descriptor.mode())
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("start", failure);
            return new StartResult(StartStatus.FAILED, lastFailure);
        }
    }

    public static NativeHeldSnapshot held(LivingEntity entity, NativeCastDescriptor descriptor) {
        if (entity == null || descriptor == null) {
            return new NativeHeldSnapshot(HeldStatus.FAILED, 0, "ENTITY_OR_DESCRIPTOR_NULL");
        }
        Contract contract = contract();
        if (contract == null) {
            return new NativeHeldSnapshot(HeldStatus.FAILED, 0, availabilityReason());
        }
        try {
            Object storage = contract.getSkillsFrom().invoke(null, entity);
            if (storage == null) {
                return new NativeHeldSnapshot(HeldStatus.FAILED, 0, "NATIVE_SKILL_STORAGE_MISSING");
            }
            Object held = contract.getHeldSkills().invoke(storage);
            if (!(held instanceof Iterable<?> iterable)) {
                return new NativeHeldSnapshot(HeldStatus.FAILED, 0, "NATIVE_HELD_LIST_UNAVAILABLE");
            }
            for (Object ticking : iterable) {
                if (matches(contract, ticking, descriptor)) {
                    return new NativeHeldSnapshot(
                        HeldStatus.PRESENT,
                        Math.max(0, integer(invokeSafely(contract.getTickingDuration(), ticking), 0)),
                        "NATIVE_HELD_PRESENT"
                    );
                }
            }
            return new NativeHeldSnapshot(HeldStatus.ABSENT, 0, "NATIVE_HELD_ABSENT");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("held", failure);
            return new NativeHeldSnapshot(HeldStatus.FAILED, 0, lastFailure);
        }
    }

    public static ReleaseResult release(LivingEntity entity, NativeCastDescriptor descriptor) {
        if (entity == null || descriptor == null) {
            return new ReleaseResult(ReleaseStatus.FAILED, "ENTITY_OR_DESCRIPTOR_NULL");
        }
        Contract contract = contract();
        if (contract == null) {
            return new ReleaseResult(ReleaseStatus.FAILED, availabilityReason());
        }
        try {
            Object storage = contract.getSkillsFrom().invoke(null, entity);
            if (storage == null) {
                return new ReleaseResult(ReleaseStatus.FAILED, "NATIVE_SKILL_STORAGE_MISSING");
            }
            Object instance = liveSkillInstance(contract, storage, descriptor.skillId());
            if (instance == null) {
                return new ReleaseResult(ReleaseStatus.FAILED, "LEARNED_INSTANCE_MISSING");
            }
            contract.handleSkillRelease().invoke(
                storage, instance, descriptor.slot(), descriptor.mode(), false
            );
            return new ReleaseResult(ReleaseStatus.REQUESTED, "NATIVE_RELEASE_REQUESTED");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("release", failure);
            return new ReleaseResult(ReleaseStatus.FAILED, lastFailure);
        }
    }

    public static CleanupResult cancelIfOwned(LivingEntity entity, NativeCastDescriptor descriptor) {
        if (entity == null || descriptor == null) {
            return new CleanupResult(CleanupStatus.FAILED, "ENTITY_OR_DESCRIPTOR_NULL");
        }
        Contract contract = contract();
        if (contract == null) {
            return new CleanupResult(CleanupStatus.FAILED, availabilityReason());
        }
        try {
            Object storage = contract.getSkillsFrom().invoke(null, entity);
            Object held = storage == null ? null : contract.getHeldSkills().invoke(storage);
            if (!(held instanceof Iterable<?> iterable)) {
                return new CleanupResult(CleanupStatus.FAILED, "NATIVE_HELD_LIST_UNAVAILABLE");
            }
            boolean matching = false;
            boolean unrelated = false;
            for (Object ticking : iterable) {
                if (matches(contract, ticking, descriptor)) {
                    matching = true;
                } else {
                    unrelated = true;
                }
            }
            if (!matching) {
                return new CleanupResult(CleanupStatus.NONE, "NATIVE_HELD_ALREADY_ABSENT");
            }
            if (unrelated) {
                return new CleanupResult(CleanupStatus.UNRELATED, "UNRELATED_NATIVE_HOLD_PRESENT");
            }
            contract.removeTickingSkill().invoke(null, entity);
            return new CleanupResult(CleanupStatus.CLEARED, "NATIVE_HELD_CLEARED");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("cancel", failure);
            return new CleanupResult(CleanupStatus.FAILED, lastFailure);
        }
    }

    private static boolean matches(Contract contract, Object ticking, NativeCastDescriptor descriptor)
        throws ReflectiveOperationException {
        Object skill = invokeSafely(contract.getTickingSkill(), ticking);
        if (skill == null) {
            return false;
        }
        String id = String.valueOf(contract.getRegistryName().invoke(skill));
        int mode = integer(invokeSafely(contract.getTickingMode(), ticking), -1);
        int key = integer(invokeSafely(contract.getTickingKey(), ticking), descriptor.slot());
        return descriptor.skillId().equals(id)
            && descriptor.mode() == mode
            && descriptor.slot() == key;
    }

    static NativeCastProfile profileFor(String skillId, int mode) {
        if (!"tensura:fire_lance".equals(skillId)) {
            return NativeCastProfile.UNSUPPORTED;
        }
        return switch (mode) {
            case 0 -> NativeCastProfile.CHARGE;
            case 1 -> NativeCastProfile.REPEAT;
            default -> NativeCastProfile.UNSUPPORTED;
        };
    }

    private static StartStatus rejectionStatus(
        Contract contract, Object instance, LivingEntity entity, int mode
    ) {
        try {
            if (contract.getOnCoolDown() != null
                && Boolean.TRUE.equals(contract.getOnCoolDown().invoke(instance, mode))) {
                return StartStatus.REJECTED_COOLDOWN;
            }
            if (contract.getCanActivateSkill() != null
                && !Boolean.TRUE.equals(contract.getCanActivateSkill().invoke(instance, entity, mode))) {
                return StartStatus.REJECTED_RESOURCE;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("start-rejection", failure);
        }
        return StartStatus.REJECTED_NATIVE;
    }

    private static String rejectionDetail(
        Contract contract, Object instance, LivingEntity entity, int mode
    ) {
        StartStatus status = rejectionStatus(contract, instance, entity, mode);
        return switch (status) {
            case REJECTED_COOLDOWN -> "NATIVE_COOLDOWN_REJECTED";
            case REJECTED_RESOURCE -> "NATIVE_RESOURCE_REJECTED";
            default -> "NATIVE_START_REJECTED";
        };
    }

    private static Object liveSkillInstance(Contract contract, Object storage, String skillId)
        throws ReflectiveOperationException {
        ResourceLocation id = ResourceLocation.tryParse(skillId);
        if (id == null) {
            return null;
        }
        Object value = contract.getSkillFromStorage().invoke(storage, id);
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static int nativeCastingTime(
        Contract contract, Object instance, LivingEntity entity, int mode
    ) throws ReflectiveOperationException {
        if (contract.getCastingTime() == null) {
            return 1;
        }
        Object value = contract.getCastingTime().invoke(instance, entity, mode);
        return value instanceof Number number ? Math.max(1, number.intValue()) : 1;
    }

    private static List<?> abilitySlots(Contract contract, Object ability)
        throws ReflectiveOperationException {
        Object value = contract.getAbilitySlots().invoke(ability);
        if (!(value instanceof Iterable<?> iterable)) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list;
        }
        List<Object> result = new ArrayList<>();
        for (Object entry : iterable) {
            result.add(entry);
        }
        return result;
    }

    private static Object invokeSafely(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Contract contract() {
        try {
            if (TensuraCompat.getStatus().integrationState()
                != TensuraCompatStatus.IntegrationState.AVAILABLE) {
                lastFailure = "NATIVE_COMPAT_" + TensuraCompat.getStatus().integrationState().name()
                    + ":" + TensuraCompat.getStatus().lastProbeErrorSummary();
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
            Class<?> storage = Class.forName(SKILL_STORAGE_CLASS);
            Class<?> skill = Class.forName(SKILL_CLASS);
            Class<?> instance = Class.forName(SKILL_INSTANCE_CLASS);
            Class<?> magic = Class.forName(TENSURA_MAGIC_CLASS);
            Class<?> ability = Class.forName(ABILITY_CLASS);
            Class<?> abilitySlot = Class.forName(ABILITY_SLOT_CLASS);
            Class<?> ticking = Class.forName(TICKING_SKILL_CLASS);
            Contract resolved = new Contract(
                storages.getMethod("getAbilityFrom", LivingEntity.class),
                api.getMethod("getSkillsFrom", LivingEntity.class),
                storage.getMethod("getSkill", ResourceLocation.class),
                storage.getMethod("startHoldSkill", ResourceLocation.class, int.class, int.class),
                storage.getMethod("handleSkillRelease", instance, int.class, int.class, boolean.class),
                storage.getMethod("getHeldSkills"),
                storage.getMethod("removeTickingSkill", LivingEntity.class),
                skill.getMethod("getRegistryName"),
                optionalMethod(instance, "canActivateSkill", LivingEntity.class, int.class),
                optionalMethod(instance, "onCoolDown", int.class),
                magic.getMethod("getCastingTime", instance, LivingEntity.class, int.class),
                ability.getMethod("getAbilitySlots"),
                abilitySlot.getMethod("getSkill"),
                abilitySlot.getMethod("getMode"),
                optionalMethod(abilitySlot, "isEmpty"),
                ticking.getMethod("getSkill"),
                ticking.getMethod("getMode"),
                ticking.getMethod("getKeyNumber"),
                ticking.getMethod("getDuration")
            );
            cachedContract = resolved;
            lastFailure = "NONE";
            return resolved;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rememberFailure("contract", failure);
            return null;
        }
    }

    private static Method optionalMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void rememberFailure(String phase, Throwable failure) {
        Throwable root = failure;
        while (root instanceof InvocationTargetException invocation
            && invocation.getCause() != null) {
            root = invocation.getCause();
        }
        String detail = root == null ? "MISSING_OR_EMPTY" : root.getClass().getSimpleName();
        String message = root == null || root.getMessage() == null
            ? ""
            : root.getMessage().replaceAll("\\s+", " ");
        if (message.length() > 96) {
            message = message.substring(0, 96);
        }
        lastFailure = phase + ":" + detail + (message.isBlank() ? "" : ":" + message);
        if (!lastFailure.equals(lastLoggedFailure)) {
            lastLoggedFailure = lastFailure;
            LOGGER.warn("Native subordinate cast bridge unavailable: {}", lastFailure);
        }
    }

    public enum NativeCastProfile {
        CHARGE,
        REPEAT,
        UNSUPPORTED
    }

    public enum ProbeStatus {
        AVAILABLE,
        EMPTY,
        UNSUPPORTED,
        UNAVAILABLE,
        FAILED
    }

    public record NativeCastDescriptor(
        String skillId,
        int slot,
        int mode,
        int castingTicks,
        NativeCastProfile profile,
        Object instance
    ) {
        public NativeCastDescriptor {
            skillId = skillId == null ? "" : skillId;
            slot = Math.max(0, slot);
            mode = Math.max(0, mode);
            castingTicks = Math.max(1, castingTicks);
            profile = profile == null ? NativeCastProfile.UNSUPPORTED : profile;
        }
    }

    public record CastProbe(ProbeStatus status, NativeCastDescriptor descriptor, String detail) {
        public CastProbe {
            status = status == null ? ProbeStatus.FAILED : status;
            detail = detail == null ? "" : detail;
        }

        static CastProbe empty(int slot) {
            return new CastProbe(ProbeStatus.EMPTY, null, "ABILITY_SLOT_EMPTY:" + slot);
        }

        static CastProbe unavailable(int slot, String detail) {
            return new CastProbe(ProbeStatus.UNAVAILABLE, null, detail + ":" + slot);
        }

        static CastProbe failed(int slot, String detail) {
            return new CastProbe(ProbeStatus.FAILED, null, detail + ":" + slot);
        }
    }

    public enum StartStatus {
        ACCEPTED,
        REJECTED_RESOURCE,
        REJECTED_COOLDOWN,
        REJECTED_NATIVE,
        FAILED
    }

    public record StartResult(StartStatus status, String detail) {
        public StartResult {
            status = status == null ? StartStatus.FAILED : status;
            detail = detail == null ? "" : detail;
        }
    }

    public enum HeldStatus {
        PRESENT,
        ABSENT,
        FAILED
    }

    public record NativeHeldSnapshot(HeldStatus status, int duration, String detail) {
        public NativeHeldSnapshot {
            status = status == null ? HeldStatus.FAILED : status;
            duration = Math.max(0, duration);
            detail = detail == null ? "" : detail;
        }
    }

    public enum ReleaseStatus {
        REQUESTED,
        FAILED
    }

    public record ReleaseResult(ReleaseStatus status, String detail) {
        public ReleaseResult {
            status = status == null ? ReleaseStatus.FAILED : status;
            detail = detail == null ? "" : detail;
        }
    }

    public enum CleanupStatus {
        CLEARED,
        NONE,
        UNRELATED,
        FAILED
    }

    public record CleanupResult(CleanupStatus status, String detail) {
        public CleanupResult {
            status = status == null ? CleanupStatus.FAILED : status;
            detail = detail == null ? "" : detail;
        }
    }

    private record Contract(
        Method getAbilityFrom,
        Method getSkillsFrom,
        Method getSkillFromStorage,
        Method startHoldSkill,
        Method handleSkillRelease,
        Method getHeldSkills,
        Method removeTickingSkill,
        Method getRegistryName,
        Method getCanActivateSkill,
        Method getOnCoolDown,
        Method getCastingTime,
        Method getAbilitySlots,
        Method getAbilitySlotSkill,
        Method getAbilitySlotMode,
        Method getAbilitySlotEmpty,
        Method getTickingSkill,
        Method getTickingMode,
        Method getTickingKey,
        Method getTickingDuration
    ) {
    }
}
