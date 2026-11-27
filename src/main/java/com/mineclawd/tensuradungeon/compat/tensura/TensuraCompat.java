package com.mineclawd.tensuradungeon.compat.tensura;

import com.mineclawd.tensuradungeon.compat.TensuraSkillCompatHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.Container;
import net.minecraft.world.entity.FlyingMob;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

public final class TensuraCompat {
    public static final String SUPPORTED_TENSURA_VERSION = "2.0.1.0";
    public static final String SUPPORTED_MANASCORE_VERSION = "4.0.0.2";
    private static final List<String> MODULE_IDS = List.of(
        "tensura", "manascore", "manascore_storage", "manascore_skill", "manascore_race"
    );

    private static boolean initialized;
    private static TensuraCompatStatus status = TensuraCompatStatus.notInitialized();

    private TensuraCompat() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        status = probeStatus();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static TensuraCompatStatus getStatus() {
        if (!initialized) {
            initialize();
        }
        return status;
    }

    public static void refreshSkillHookState() {
        if (!initialized) {
            return;
        }
        status = status.withSkillGriefHook(TensuraSkillCompatHooks.isSkillGriefHookRegistered());
    }

    public static boolean isFlyingEnemyArchetype(LivingEntity entity) {
        if (entity instanceof FlyingMob) {
            return true;
        }
        if (entity == null || getStatus().integrationState()
            != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return false;
        }
        try {
            Class<?> flyingContract = Class.forName(
                "io.github.manasmods.tensura.entity.template.subclass.IFlying",
                false,
                TensuraCompat.class.getClassLoader()
            );
            return flyingContract.isInstance(entity);
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    public static boolean interruptHeldSkills(LivingEntity entity) {
        if (entity == null || getStatus().integrationState()
            != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return false;
        }
        try {
            Class<?> skillStorage = Class.forName(
                "io.github.manasmods.manascore.skill.impl.SkillStorage",
                false,
                TensuraCompat.class.getClassLoader()
            );
            skillStorage.getMethod("removeTickingSkill", LivingEntity.class)
                .invoke(null, entity);
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    public static String raceDisplayName(LivingEntity entity) {
        String defaultMobName = defaultMobName(entity);
        if (entity == null || getStatus().integrationState()
            != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return defaultMobName;
        }
        try {
            Class<?> raceApi = Class.forName(
                "io.github.manasmods.manascore.race.api.RaceAPI",
                false,
                TensuraCompat.class.getClassLoader()
            );
            Object races = raceApi.getMethod("getRaceFrom", LivingEntity.class)
                .invoke(null, entity);
            Class<?> racesContract = Class.forName(
                "io.github.manasmods.manascore.race.api.Races",
                false,
                TensuraCompat.class.getClassLoader()
            );
            Object optionalRace = racesContract.getMethod("getRace").invoke(races);
            if (!(optionalRace instanceof Optional<?> optional) || optional.isEmpty()) {
                return defaultMobName;
            }
            Object race = optional.get();
            Object display = race.getClass().getMethod("getDisplayName").invoke(race);
            String value = display instanceof Component component
                ? component.getString()
                : String.valueOf(display);
            return value.isBlank() ? defaultMobName : value;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return defaultMobName;
        }
    }

    private static String defaultMobName(LivingEntity entity) {
        if (entity == null) {
            return "Subordinate";
        }
        String typeName = entity.getType().getDescription().getString();
        if (!typeName.isBlank()) {
            return typeName;
        }
        String entityName = entity.getName().getString();
        return entityName.isBlank() ? "Subordinate" : entityName;
    }

    public static Optional<UUID> subordinateOwnerId(LivingEntity entity) {
        if (entity == null || getStatus().integrationState()
            != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return Optional.empty();
        }
        try {
            Class<?> helper = Class.forName(
                "io.github.manasmods.tensura.util.SubordinateHelper",
                false,
                TensuraCompat.class.getClassLoader()
            );
            Object owner = helper.getMethod(
                "getSubordinateOwnerUUID",
                LivingEntity.class
            ).invoke(null, entity);
            return owner instanceof UUID ownerId ? Optional.of(ownerId) : Optional.empty();
        } catch (ReflectiveOperationException | LinkageError ex) {
            return Optional.empty();
        }
    }

    public static Optional<Container> subordinateInventory(LivingEntity entity) {
        if (entity == null || getStatus().integrationState()
            != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return Optional.empty();
        }
        try {
            Class<?> humanoid = Class.forName(
                "io.github.manasmods.tensura.entity.template.TensuraHumanoidEntity",
                false,
                TensuraCompat.class.getClassLoader()
            );
            if (!humanoid.isInstance(entity)) {
                return Optional.empty();
            }
            Object inventory = humanoid.getField("inventory").get(entity);
            return inventory instanceof Container container
                ? Optional.of(container)
                : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }

    public static Optional<EpSnapshot> readEp(LivingEntity entity) {
        if (entity == null || getStatus().integrationState()
            != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return Optional.empty();
        }
        try {
            ClassLoader loader = TensuraCompat.class.getClassLoader();
            Class<?> storages = Class.forName(
                "io.github.manasmods.tensura.storage.TensuraStorages", false, loader
            );
            Object existence = storages.getMethod("getExistenceFrom", LivingEntity.class)
                .invoke(null, entity);
            Object currentValue = existence.getClass().getMethod("getEP").invoke(existence);
            Class<?> helper = Class.forName(
                "io.github.manasmods.tensura.util.EnergyHelper", false, loader
            );
            Object maximumValue = helper.getMethod("getMaxEP", LivingEntity.class)
                .invoke(null, entity);
            if (!(currentValue instanceof Number current)
                || !(maximumValue instanceof Number maximum)) {
                return Optional.empty();
            }
            double currentEp = current.doubleValue();
            double maxEp = maximum.doubleValue();
            if (!Double.isFinite(currentEp) || !Double.isFinite(maxEp)) {
                return Optional.empty();
            }
            return Optional.of(new EpSnapshot(
                Math.max(0.0D, currentEp), Math.max(0.0D, maxEp)
            ));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }

    public static OptionalDouble readMagicule(LivingEntity entity) {
        Optional<MagiculeSnapshot> snapshot = readMagiculeSnapshot(entity);
        return snapshot.isPresent()
            ? OptionalDouble.of(snapshot.get().current())
            : OptionalDouble.empty();
    }

    public static Optional<MagiculeSnapshot> readMagiculeSnapshot(LivingEntity entity) {
        if (entity == null || getStatus().integrationState()
            != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return Optional.empty();
        }
        try {
            ClassLoader loader = TensuraCompat.class.getClassLoader();
            Class<?> storages = Class.forName(
                "io.github.manasmods.tensura.storage.TensuraStorages", false, loader
            );
            Object existence = storages.getMethod("getExistenceFrom", LivingEntity.class)
                .invoke(null, entity);
            Class<?> helper = Class.forName(
                "io.github.manasmods.tensura.util.EnergyHelper", false, loader
            );
            Object currentValue = existence.getClass().getMethod("getMagicule")
                .invoke(existence);
            Object maximumValue = helper.getMethod("getMaxMagicule", LivingEntity.class)
                .invoke(null, entity);
            if (!(currentValue instanceof Number current)
                || !(maximumValue instanceof Number maximum)) {
                return Optional.empty();
            }
            double currentMagicule = current.doubleValue();
            double maximumMagicule = maximum.doubleValue();
            if (!Double.isFinite(currentMagicule) || !Double.isFinite(maximumMagicule)) {
                return Optional.empty();
            }
            return Optional.of(new MagiculeSnapshot(currentMagicule, maximumMagicule));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
        }
        return Optional.empty();
    }

    public static Optional<Boolean> pauseSubordinateFollow(LivingEntity entity) {
        if (!(entity instanceof Mob mob)
            || getStatus().integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return Optional.empty();
        }
        try {
            ClassLoader loader = TensuraCompat.class.getClassLoader();
            Class<?> helper = Class.forName(
                "io.github.manasmods.tensura.util.SubordinateHelper", false, loader
            );
            Class<?> subordinate = Class.forName(
                "io.github.manasmods.tensura.entity.template.subclass.ISubordinate", false, loader
            );
            if (!subordinate.isInstance(entity)) {
                return Optional.empty();
            }
            boolean alreadyStaying = (boolean) helper.getMethod(
                "isOrderedToStay", LivingEntity.class
            ).invoke(null, entity);
            boolean wandering = (boolean) subordinate.getMethod("isWandering").invoke(entity);
            if (alreadyStaying || wandering) {
                return Optional.of(false);
            }
            helper.getMethod("setStay", Mob.class).invoke(null, mob);
            boolean staying = (boolean) helper.getMethod(
                "isOrderedToStay", LivingEntity.class
            ).invoke(null, entity);
            return staying ? Optional.of(true) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }

    public static Optional<Boolean> wanderSubordinateAtHub(LivingEntity entity) {
        if (!(entity instanceof Mob mob)
            || getStatus().integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return Optional.empty();
        }
        try {
            ClassLoader loader = TensuraCompat.class.getClassLoader();
            Class<?> helper = Class.forName(
                "io.github.manasmods.tensura.util.SubordinateHelper", false, loader
            );
            Class<?> subordinate = Class.forName(
                "io.github.manasmods.tensura.entity.template.subclass.ISubordinate", false, loader
            );
            if (!subordinate.isInstance(entity)) {
                return Optional.empty();
            }
            boolean alreadyStaying = (boolean) helper.getMethod(
                "isOrderedToStay", LivingEntity.class
            ).invoke(null, entity);
            boolean wandering = (boolean) subordinate.getMethod("isWandering").invoke(entity);
            if (alreadyStaying || wandering) {
                return Optional.of(false);
            }
            helper.getMethod("setWander", Mob.class).invoke(null, mob);
            return (boolean) subordinate.getMethod("isWandering").invoke(entity)
                ? Optional.of(true)
                : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }

    public static boolean restoreSubordinateFollow(LivingEntity entity) {
        if (!(entity instanceof Mob mob)
            || getStatus().integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return false;
        }
        try {
            ClassLoader loader = TensuraCompat.class.getClassLoader();
            Class<?> helper = Class.forName(
                "io.github.manasmods.tensura.util.SubordinateHelper", false, loader
            );
            Class<?> subordinate = Class.forName(
                "io.github.manasmods.tensura.entity.template.subclass.ISubordinate", false, loader
            );
            if (!subordinate.isInstance(entity)) {
                return false;
            }
            helper.getMethod("setFollow", Mob.class).invoke(null, mob);
            boolean staying = (boolean) helper.getMethod(
                "isOrderedToStay", LivingEntity.class
            ).invoke(null, entity);
            boolean wandering = (boolean) subordinate.getMethod("isWandering").invoke(entity);
            return !staying && !wandering;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return false;
        }
    }

    public static Optional<GoblinPortraitTextures> goblinPortraitTextures(LivingEntity entity) {
        if (entity == null || getStatus().integrationState()
            != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return Optional.empty();
        }
        try {
            ClassLoader loader = TensuraCompat.class.getClassLoader();
            Class<?> goblin = Class.forName(
                "io.github.manasmods.tensura.entity.monster.GoblinEntity",
                false,
                loader
            );
            if (!goblin.isInstance(entity)) {
                return Optional.empty();
            }
            Class<?> skin = Class.forName(
                "io.github.manasmods.tensura.entity.variant.GoblinVariant$Skin",
                false,
                loader
            );
            Class<?> hair = Class.forName(
                "io.github.manasmods.tensura.entity.variant.GoblinVariant$Hair",
                false,
                loader
            );
            ResourceLocation skinTexture = (ResourceLocation)skin
                .getMethod("getTextureLocation", goblin)
                .invoke(null, entity);
            Object faceVariant = goblin.getMethod("getFace").invoke(entity);
            ResourceLocation faceTexture = (ResourceLocation)faceVariant.getClass()
                .getMethod("getTextureLocation")
                .invoke(faceVariant);
            ResourceLocation hairTexture = (ResourceLocation)hair
                .getMethod("getTextureLocation", goblin)
                .invoke(null, entity);
            int hairColor = (Integer)goblin.getMethod("getHairColor").invoke(entity);
            return Optional.of(new GoblinPortraitTextures(
                skinTexture,
                faceTexture,
                hairTexture,
                hairColor == 0 ? 0xFFFFFFFF : 0xFF000000 | hairColor
            ));
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ex) {
            return Optional.empty();
        }
    }

    public record GoblinPortraitTextures(
        ResourceLocation skin,
        ResourceLocation face,
        ResourceLocation hair,
        int hairColor
    ) {
    }

    public record EpSnapshot(double current, double maximum) {
        public EpSnapshot {
            current = Double.isFinite(current) ? Math.max(0.0D, current) : 0.0D;
            maximum = Double.isFinite(maximum) ? Math.max(0.0D, maximum) : 0.0D;
        }
    }

    public record MagiculeSnapshot(double current, double maximum) {
        public MagiculeSnapshot {
            current = Double.isFinite(current) ? Math.max(0.0D, current) : 0.0D;
            maximum = Double.isFinite(maximum) ? Math.max(0.0D, maximum) : 0.0D;
        }
    }

    static TensuraCompatStatus classifyVersions(
        boolean tensuraPresent,
        String tensuraVersion,
        boolean manascorePresent,
        String manascoreVersion
    ) {
        List<String> modules = new ArrayList<>();
        if (tensuraPresent) {
            modules.add("tensura");
        }
        if (manascorePresent) {
            modules.add("manascore");
        }
        if (!tensuraPresent) {
            return TensuraCompatStatus.absent("NONE", true);
        }
        if (!manascorePresent || !SUPPORTED_TENSURA_VERSION.equals(tensuraVersion)
            || !SUPPORTED_MANASCORE_VERSION.equals(manascoreVersion)) {
            return TensuraCompatStatus.unsupported(modules, tensuraVersion, manascoreVersion,
                "SUPPORTED=tensura:" + SUPPORTED_TENSURA_VERSION + ",manascore:" + SUPPORTED_MANASCORE_VERSION);
        }
        return TensuraCompatStatus.available(modules, tensuraVersion, manascoreVersion);
    }

    private static TensuraCompatStatus probeStatus() {
        boolean tensuraPresent = hasMod("tensura");
        if (!tensuraPresent) {
            return TensuraCompatStatus.absent("NONE", true);
        }
        boolean manascorePresent = hasMod("manascore");
        String tensuraVersion = versionOf("tensura");
        String manascoreVersion = versionOf("manascore");
        List<String> modules = installedSupportedModules();
        TensuraCompatStatus classified = classifyVersions(true, tensuraVersion, manascorePresent, manascoreVersion);
        if (classified.integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return TensuraCompatStatus.unsupported(modules, tensuraVersion, manascoreVersion,
                classified.lastProbeError());
        }
        try {
            verifySupportedContract();
            return TensuraCompatStatus.available(modules, tensuraVersion, manascoreVersion);
        } catch (ReflectiveOperationException | LinkageError ex) {
            return TensuraCompatStatus.broken(modules, tensuraVersion, manascoreVersion,
                ex.getClass().getSimpleName() + detail(ex.getMessage()));
        }
    }

    private static void verifySupportedContract() throws ReflectiveOperationException {
        ClassLoader loader = TensuraCompat.class.getClassLoader();
        Class<?> storages = Class.forName("io.github.manasmods.tensura.storage.TensuraStorages", false, loader);
        Class<?> attributes = Class.forName("io.github.manasmods.tensura.registry.attribute.TensuraAttributes", false, loader);
        Class<?> helper = Class.forName("io.github.manasmods.tensura.util.EnergyHelper", false, loader);
        Class<?> gainType = Class.forName("io.github.manasmods.tensura.util.EnergyHelper$GainType", false, loader);
        Class<?> existence = Class.forName("io.github.manasmods.tensura.storage.ep.IExistence", false, loader);
        Class<?> raceApi = Class.forName("io.github.manasmods.manascore.race.api.RaceAPI", false, loader);
        Class<?> races = Class.forName("io.github.manasmods.manascore.race.api.Races", false, loader);
        Class<?> raceInstance = Class.forName("io.github.manasmods.manascore.race.api.ManasRaceInstance", false, loader);
        Class<?> skillApi = Class.forName("io.github.manasmods.manascore.skill.api.SkillAPI", false, loader);
        Class<?> skillStorage = Class.forName("io.github.manasmods.manascore.skill.impl.SkillStorage", false, loader);
        Class<?> skillInstance = Class.forName("io.github.manasmods.manascore.skill.api.ManasSkillInstance", false, loader);
        Class<?> tensuraSkill = Class.forName("io.github.manasmods.tensura.ability.TensuraSkill", false, loader);
        Class<?> tensuraSkillInstance = Class.forName("io.github.manasmods.tensura.ability.TensuraSkillInstance", false, loader);
        Class<?> skillEvents = Class.forName("io.github.manasmods.manascore.skill.api.SkillEvents", false, loader);
        Class.forName("io.github.manasmods.manascore.skill.api.SkillEvents$SkillActivationEvent", false, loader);
        Class.forName("io.github.manasmods.manascore.skill.api.SkillEvents$SkillReleaseEvent", false, loader);
        Class.forName("io.github.manasmods.manascore.skill.api.SkillEvents$SkillToggleEvent", false, loader);
        Class.forName("io.github.manasmods.manascore.skill.api.SkillEvents$SkillTickEvent", false, loader);
        Class<?> goblin = Class.forName("io.github.manasmods.tensura.entity.monster.GoblinEntity", false, loader);
        Class<?> goblinSkin = Class.forName("io.github.manasmods.tensura.entity.variant.GoblinVariant$Skin", false, loader);
        Class<?> goblinFace = Class.forName("io.github.manasmods.tensura.entity.variant.GoblinVariant$Face", false, loader);
        Class<?> goblinHair = Class.forName("io.github.manasmods.tensura.entity.variant.GoblinVariant$Hair", false, loader);
        Class<?> humanoid = Class.forName(
            "io.github.manasmods.tensura.entity.template.TensuraHumanoidEntity", false, loader
        );

        storages.getMethod("getExistenceFrom", LivingEntity.class);
        for (String field : List.of("MAX_AURA", "MAX_MAGICULE", "LIMITED_SPIRITUAL_MAX_AURA",
            "LIMITED_SPIRITUAL_MAX_MAGICULE", "MAX_SPIRITUAL_HEALTH")) {
            attributes.getField(field);
        }
        for (String method : List.of("getMaxEP", "getMaxAura", "getMaxMagicule", "getBaseMaxEP")) {
            helper.getMethod(method, LivingEntity.class);
        }
        helper.getMethod("setBaseMaxEP", LivingEntity.class, double.class);
        helper.getMethod("gainMagicule", LivingEntity.class, double.class, gainType);
        helper.getMethod("gainAura", LivingEntity.class, double.class, gainType);
        for (String method : List.of("getAura", "getMagicule", "getEP", "getGainedEP", "getHumanKill", "getSleepModeTime")) {
            existence.getMethod(method);
        }
        for (String method : List.of("isSkippingEPDrop", "isSpiritualForm", "isDemonLordSeed", "isTrueDemonLord", "isHeroEgg", "isTrueHero")) {
            existence.getMethod(method);
        }
        existence.getMethod("getSpawnType");
        existence.getMethod("setAura", double.class);
        existence.getMethod("setMagicule", double.class);
        existence.getMethod("setSpiritualHealth", double.class);
        existence.getMethod("setEP", double.class);
        existence.getMethod("markDirty");
        raceApi.getMethod("getRaceFrom", LivingEntity.class);
        races.getMethod("getRace");
        raceInstance.getMethod("getRaceId");
        raceInstance.getMethod("getDisplayName");
        skillApi.getMethod("getSkillsFrom", LivingEntity.class);
        skillStorage.getMethod("getLearnedSkills");
        skillStorage.getMethod("removeTickingSkill", LivingEntity.class);
        skillInstance.getMethod("getSkillId");
        skillInstance.getMethod("getCoolDown", int.class);
        tensuraSkill.getMethod("getMagiculeCost", LivingEntity.class, skillInstance, int.class);
        tensuraSkillInstance.getMethod("getMagiculeCost", LivingEntity.class, int.class);
        skillInstance.getMethod("isTemporarySkill");
        skillInstance.getMethod("isToggled");
        skillEvents.getField("ACTIVATE_SKILL");
        skillEvents.getField("RELEASE_SKILL");
        skillEvents.getField("TOGGLE_SKILL");
        skillEvents.getField("SKILL_PRE_TICK");
        goblin.getMethod("getFace");
        goblin.getMethod("getHairColor");
        goblinSkin.getMethod("getTextureLocation", goblin);
        goblinFace.getMethod("getTextureLocation");
        goblinHair.getMethod("getTextureLocation", goblin);
        if (!Container.class.isAssignableFrom(humanoid.getField("inventory").getType())) {
            throw new NoSuchFieldException("TensuraHumanoidEntity.inventory is not a Container");
        }
    }

    private static boolean hasMod(String modId) {
        return ModList.get().getModContainerById(modId).isPresent();
    }

    private static String versionOf(String modId) {
        return ModList.get().getMods().stream()
            .filter(info -> modId.equals(info.getModId()))
            .findFirst()
            .map(IModInfo::getVersion)
            .map(Object::toString)
            .orElse("UNKNOWN");
    }

    private static List<String> installedSupportedModules() {
        return MODULE_IDS.stream().filter(TensuraCompat::hasMod).toList();
    }

    private static String detail(String message) {
        return message == null || message.isBlank() ? "" : ":" + message;
    }
}
