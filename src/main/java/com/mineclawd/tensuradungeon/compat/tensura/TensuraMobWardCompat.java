package com.mineclawd.tensuradungeon.compat.tensura;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class TensuraMobWardCompat {
    private static final String SKILL_API_CLASS = "io.github.manasmods.manascore.skill.api.SkillAPI";
    private static final String REGISTRAR_CLASS = "dev.architectury.registry.registries.Registrar";
    private static final String SKILL_STORAGE_CLASS = "io.github.manasmods.manascore.skill.impl.SkillStorage";
    private static final String SKILL_CLASS = "io.github.manasmods.manascore.skill.api.ManasSkill";
    private static final String SKILL_INSTANCE_CLASS = "io.github.manasmods.manascore.skill.api.ManasSkillInstance";
    private static final String GRANTED_TAG = "TensuraDungeonGrantedWardSkills";
    private static final int MAX_TRACKED_WARD_SKILLS = 2;
    private static final Set<String> REGISTERED_WARD_CATEGORIES = Set.of(
        "abnormal_condition", "cold", "corrosion", "darkness_attack", "earth_attack", "electricity",
        "flame_attack", "gravity_attack", "heat", "holy_attack", "light_attack", "magic", "pain",
        "paralysis", "physical_attack", "pierce", "poison", "spatial_attack", "spiritual_attack",
        "thermal_fluctuation", "water_attack", "wind_attack"
    );

    private TensuraMobWardCompat() {}

    public static boolean isSkillAvailable(String skillId) {
        return inspectSkill(skillId).safeToMutate();
    }

    public static WardHookInspection inspectSkill(String skillId) {
        SkillResolution resolution = resolveSkill(skillId);
        return resolution.inspection();
    }

    public static boolean grantIfAbsent(LivingEntity entity, String skillId) {
        if (!(entity instanceof Mob) || entity.level().isClientSide) return false;

        SkillResolution resolution = resolveSkill(skillId);
        if (!resolution.safeToMutate()) return false;
        try {
            Object storage = resolution.getSkillsFrom().invoke(null, entity);
            Object existing = resolution.getSkill().invoke(storage, resolution.resourceId());
            if (existing instanceof Optional<?> optional && optional.isPresent()) return true;

            if (!canRemember(entity, resolution.normalizedSkillId())) return false;

            Object instance = resolution.createDefaultInstance().invoke(resolution.skill());
            boolean learned = Boolean.TRUE.equals(
                resolution.learnSkill().invoke(storage, instance, Component.empty())
            );
            if (!learned) return false;
            if (remember(entity, resolution.normalizedSkillId())) return true;

            try {
                resolution.forgetSkill().invoke(storage, resolution.resourceId(), Component.empty());
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static void forgetDungeonGranted(LivingEntity entity) {
        if (!(entity instanceof Mob) || entity.level().isClientSide) return;
        List<String> grantedIds = readGrantedSkillIds(entity);
        if (grantedIds == null || grantedIds.isEmpty()) return;

        List<SkillResolution> resolutions = new ArrayList<>(grantedIds.size());
        for (String skillId : grantedIds) {
            SkillResolution resolution = resolveSkill(skillId);
            if (!resolution.safeToMutate()) return;
            resolutions.add(resolution);
        }

        try {
            Object storage = resolutions.getFirst().getSkillsFrom().invoke(null, entity);
            for (SkillResolution resolution : resolutions) {
                resolution.forgetSkill().invoke(storage, resolution.resourceId(), Component.empty());
            }
            entity.getPersistentData().remove(GRANTED_TAG);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    public static List<String> grantedSkillIds(LivingEntity entity) {
        if (entity == null) return List.of();
        List<String> granted = readGrantedSkillIds(entity);
        return granted == null ? List.of() : granted;
    }

    private static SkillResolution resolveSkill(String skillId) {
        ResourceLocation resourceId = parseRegisteredWardId(skillId);
        if (resourceId == null) {
            return SkillResolution.invalid(skillId, invalidReason(skillId));
        }

        TensuraCompatStatus status;
        try {
            status = TensuraCompat.getStatus();
        } catch (RuntimeException | LinkageError ex) {
            return SkillResolution.failed(
                resourceId, TensuraCompatStatus.IntegrationState.BROKEN,
                "STATUS_PROBE_FAILED:" + ex.getClass().getSimpleName()
            );
        }
        if (status.integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return SkillResolution.failed(
                resourceId, status.integrationState(), "INTEGRATION_" + status.integrationState().name()
            );
        }

        try {
            Class<?> api = Class.forName(SKILL_API_CLASS);
            Class<?> registrarType = Class.forName(REGISTRAR_CLASS);
            Class<?> storage = Class.forName(SKILL_STORAGE_CLASS);
            Class<?> skillType = Class.forName(SKILL_CLASS);
            Class<?> skillInstance = Class.forName(SKILL_INSTANCE_CLASS);
            Method getSkillRegistry = api.getMethod("getSkillRegistry");
            Method getSkillsFrom = api.getMethod("getSkillsFrom", LivingEntity.class);
            Method getSkill = storage.getMethod("getSkill", ResourceLocation.class);
            Method learnSkill = storage.getMethod("learnSkill", skillInstance, MutableComponent.class);
            Method forgetSkill = storage.getMethod("forgetSkill", ResourceLocation.class, MutableComponent.class);

            Object registry = getSkillRegistry.invoke(null);
            Method contains = registrarType.getMethod("contains", ResourceLocation.class);
            Method get = registrarType.getMethod("get", ResourceLocation.class);
            boolean present = Boolean.TRUE.equals(contains.invoke(registry, resourceId));
            if (!present) {
                ResourceLocation legacyDatabaseId = legacyDatabaseIdFor(resourceId);
                String reason = legacyDatabaseId != null
                    && Boolean.TRUE.equals(contains.invoke(registry, legacyDatabaseId))
                    ? "REGISTERED_ID_MISSING_LEGACY_DATABASE_ALIAS_PRESENT"
                    : "NATIVE_ID_UNAVAILABLE";
                return new SkillResolution(
                    resourceId, status.integrationState(), true, false, true, true, false,
                    null, getSkillsFrom, getSkill, learnSkill, forgetSkill, null, reason
                );
            }

            Object skill = get.invoke(registry, resourceId);
            if (skill == null) {
                return new SkillResolution(
                    resourceId, status.integrationState(), true, false, true, true, false,
                    null, getSkillsFrom, getSkill, learnSkill, forgetSkill, null, "NATIVE_ID_UNAVAILABLE"
                );
            }
            Method createDefaultInstance = skillType.getMethod("createDefaultInstance");
            return new SkillResolution(
                resourceId, status.integrationState(), true, true, true, true, true,
                skill, getSkillsFrom, getSkill, learnSkill, forgetSkill, createDefaultInstance, "NONE"
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return SkillResolution.failed(
                resourceId, TensuraCompatStatus.IntegrationState.BROKEN,
                "WARD_API_PROBE_FAILED:" + ex.getClass().getSimpleName()
            );
        }
    }

    private static ResourceLocation parseRegisteredWardId(String skillId) {
        if (skillId == null || skillId.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(skillId);
        if (id == null || !"tensura".equals(id.getNamespace())) return null;

        String path = id.getPath();
        String suffix;
        if (path.endsWith("_resistance")) {
            suffix = "_resistance";
        } else if (path.endsWith("_nullification")) {
            suffix = "_nullification";
        } else {
            return null;
        }
        String category = path.substring(0, path.length() - suffix.length());
        if (!REGISTERED_WARD_CATEGORIES.contains(category)) return null;
        return id;
    }

    private static String invalidReason(String skillId) {
        if (skillId != null && skillId.contains("fire_attack")) return "LEGACY_FIRE_ATTACK_ID";
        return "INVALID_NATIVE_WARD_ID";
    }

    private static ResourceLocation legacyDatabaseIdFor(ResourceLocation registeredId) {
        if (registeredId == null || !"tensura".equals(registeredId.getNamespace())) return null;
        String path = registeredId.getPath();
        if (!path.startsWith("flame_attack_")) return null;
        return ResourceLocation.tryParse("tensura:fire_attack_" + path.substring("flame_attack_".length()));
    }

    private static List<String> readGrantedSkillIds(LivingEntity entity) {
        ListTag granted = entity.getPersistentData().getList(GRANTED_TAG, Tag.TAG_STRING);
        if (granted.size() > MAX_TRACKED_WARD_SKILLS) return null;
        List<String> ids = new ArrayList<>(granted.size());
        for (int index = 0; index < granted.size(); index++) {
            String skillId = granted.getString(index);
            if (parseRegisteredWardId(skillId) == null || ids.contains(skillId)) return null;
            ids.add(skillId);
        }
        return List.copyOf(ids);
    }

    private static boolean canRemember(LivingEntity entity, String skillId) {
        List<String> existing = readGrantedSkillIds(entity);
        return existing != null && (existing.contains(skillId) || existing.size() < MAX_TRACKED_WARD_SKILLS);
    }

    private static boolean remember(LivingEntity entity, String skillId) {
        List<String> existing = readGrantedSkillIds(entity);
        if (existing == null) return false;
        if (existing.contains(skillId)) return true;
        if (existing.size() >= MAX_TRACKED_WARD_SKILLS) return false;
        ListTag granted = entity.getPersistentData().getList(GRANTED_TAG, Tag.TAG_STRING);
        granted.add(StringTag.valueOf(skillId));
        entity.getPersistentData().put(GRANTED_TAG, granted);
        return true;
    }

    public record WardHookInspection(
        String skillId,
        TensuraCompatStatus.IntegrationState integrationState,
        boolean nativeIdValid,
        boolean registryEntryAvailable,
        boolean grantApiAvailable,
        boolean forgetApiAvailable,
        boolean instanceFactoryAvailable,
        String reason
    ) {
        public WardHookInspection {
            skillId = skillId == null ? "" : skillId;
            integrationState = integrationState == null
                ? TensuraCompatStatus.IntegrationState.BROKEN : integrationState;
            reason = reason == null || reason.isBlank() ? "NONE" : reason;
        }

        public boolean safeToMutate() {
            return nativeIdValid && registryEntryAvailable && grantApiAvailable
                && forgetApiAvailable && instanceFactoryAvailable
                && integrationState == TensuraCompatStatus.IntegrationState.AVAILABLE;
        }
    }

    private record SkillResolution(
        ResourceLocation resourceId,
        TensuraCompatStatus.IntegrationState integrationState,
        boolean nativeIdValid,
        boolean registryEntryAvailable,
        boolean grantApiAvailable,
        boolean forgetApiAvailable,
        boolean instanceFactoryAvailable,
        Object skill,
        Method getSkillsFrom,
        Method getSkill,
        Method learnSkill,
        Method forgetSkill,
        Method createDefaultInstance,
        String reason
    ) {
        private static SkillResolution invalid(String rawSkillId, String reason) {
            return new SkillResolution(
                rawSkillId == null ? null : ResourceLocation.tryParse(rawSkillId),
                TensuraCompatStatus.IntegrationState.BROKEN,
                false, false, false, false, false,
                null, null, null, null, null, null, reason
            );
        }

        private static SkillResolution failed(
            ResourceLocation resourceId,
            TensuraCompatStatus.IntegrationState state,
            String reason
        ) {
            return new SkillResolution(
                resourceId, state, true, false, false, false, false,
                null, null, null, null, null, null, reason
            );
        }

        private String normalizedSkillId() {
            return resourceId == null ? "" : resourceId.toString();
        }

        private boolean safeToMutate() {
            return new WardHookInspection(
                normalizedSkillId(), integrationState, nativeIdValid, registryEntryAvailable,
                grantApiAvailable, forgetApiAvailable, instanceFactoryAvailable, reason
            ).safeToMutate();
        }

        private WardHookInspection inspection() {
            return new WardHookInspection(
                normalizedSkillId(), integrationState, nativeIdValid, registryEntryAvailable,
                grantApiAvailable, forgetApiAvailable, instanceFactoryAvailable, reason
            );
        }
    }
}
