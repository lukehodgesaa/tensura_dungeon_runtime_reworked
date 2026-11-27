package com.mineclawd.tensuradungeon.compat.tensura;

import com.mineclawd.tensuradungeon.runtime.RewardMailboxData;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class TensuraEnergyDebugHelper {
    private static final String TENSURA_STORAGES_CLASS = "io.github.manasmods.tensura.storage.TensuraStorages";
    private static final String TENSURA_ATTRIBUTES_CLASS = "io.github.manasmods.tensura.registry.attribute.TensuraAttributes";
    private static final String ENERGY_HELPER_CLASS = "io.github.manasmods.tensura.util.EnergyHelper";
    private static final String GAIN_TYPE_CLASS = "io.github.manasmods.tensura.util.EnergyHelper$GainType";
    private static final String GAIN_TYPE_NORMAL = "NORMAL";
    private static final String ATTRIBUTE_BASE_SPLIT_SYNC = "ATTRIBUTE_BASE_SPLIT_SYNC";
    private static final String LIMITED_SPIRITUAL_MAX_AURA = "LIMITED_SPIRITUAL_MAX_AURA";
    private static final String LIMITED_SPIRITUAL_MAX_MAGICULE = "LIMITED_SPIRITUAL_MAX_MAGICULE";

    private TensuraEnergyDebugHelper() {
    }

    public static EnergyDebugSnapshot inspect(ServerPlayer player) {
        return toEnergyDebugSnapshot(inspectSource(player));
    }

    public static SourceDebugSnapshot inspectSource(ServerPlayer player) {
        if (player == null) {
            return SourceDebugSnapshot.error("PLAYER_MISSING");
        }
        TensuraCompatStatus compatStatus = TensuraCompat.getStatus();
        if (compatStatus.integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return SourceDebugSnapshot.error(compatStatus.integrationState().name());
        }
        int mailboxCount = mailboxCount(player);
        try {
            Object existence = getExistence(player);
            boolean existenceAvailable = existence != null;
            double aura = existenceAvailable ? invokeDouble(existence, "getAura") : 0.0D;
            double magicule = existenceAvailable ? invokeDouble(existence, "getMagicule") : 0.0D;
            double ep = existenceAvailable ? invokeDouble(existence, "getEP") : 0.0D;
            Double maxEp = invokeStaticDouble(ENERGY_HELPER_CLASS, "getMaxEP", player);
            Double maxAura = invokeStaticDouble(ENERGY_HELPER_CLASS, "getMaxAura", player);
            Double maxMagicule = invokeStaticDouble(ENERGY_HELPER_CLASS, "getMaxMagicule", player);
            AttributeSnapshot maxAuraAttribute = captureAttribute(player, "MAX_AURA");
            AttributeSnapshot maxMagiculeAttribute = captureAttribute(player, "MAX_MAGICULE");
            AttributeSnapshot limitedAuraAttribute = captureAttribute(player, LIMITED_SPIRITUAL_MAX_AURA);
            AttributeSnapshot limitedMagiculeAttribute = captureAttribute(player, LIMITED_SPIRITUAL_MAX_MAGICULE);
            LifecycleSnapshot lifecycle = captureLifecycle(existence);
            RaceSnapshot race = captureRace(player);
            SkillSnapshot skills = captureSkills(player);
            TensuraCompatStatus status = TensuraCompat.getStatus();
            boolean placeholderRewardOnly = status.safeFallbackMode() && !status.anyMutationSafeEvidence();
            return new SourceDebugSnapshot(
                aura,
                magicule,
                ep,
                existenceAvailable,
                maxAura,
                maxMagicule,
                maxEp,
                maxAuraAttribute,
                maxMagiculeAttribute,
                limitedAuraAttribute,
                limitedMagiculeAttribute,
                lifecycle,
                race,
                skills,
                mailboxCount,
                true,
                placeholderRewardOnly,
                status.safeFallbackMode(),
                "NONE"
            );
        } catch (Exception | LinkageError ex) {
            return SourceDebugSnapshot.error(ex.getClass().getSimpleName());
        }
    }

    public static EnergyGrantResult grantMagicule(ServerPlayer player, double amount) {
        return grant(player, amount, "gainMagicule", "magicule");
    }

    public static EnergyGrantResult grantAura(ServerPlayer player, double amount) {
        return grant(player, amount, "gainAura", "aura");
    }

    public static MaxEnergyProgressionResult increaseMaxEp(ServerPlayer player, double amount) {
        EnergyDebugSnapshot before = inspect(player);
        int mailboxCountBefore = mailboxCount(player);
        if (player == null) {
            return MaxEnergyProgressionResult.skipped(amount, ATTRIBUTE_BASE_SPLIT_SYNC, before, before, mailboxCountBefore, mailboxCountBefore, "PLAYER_MISSING");
        }
        if (amount <= 0.0D) {
            return MaxEnergyProgressionResult.skipped(amount, ATTRIBUTE_BASE_SPLIT_SYNC, before, before, mailboxCountBefore, mailboxCountBefore, "INVALID_AMOUNT");
        }
        TensuraCompatStatus compatStatus = TensuraCompat.getStatus();
        if (compatStatus.integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return MaxEnergyProgressionResult.skipped(amount, ATTRIBUTE_BASE_SPLIT_SYNC, before, before,
                mailboxCountBefore, mailboxCountBefore, compatStatus.integrationState().name());
        }
        boolean helperCalled = false;
        String error = "NONE";
        try {
            applyAttributeSplitSyncMaxEp(player, amount);
            helperCalled = true;
        } catch (Exception | LinkageError ex) {
            error = ex.getClass().getSimpleName() + normalizeMessage(ex.getMessage());
        }
        EnergyDebugSnapshot after = inspect(player);
        int mailboxCountAfter = mailboxCount(player);
        return new MaxEnergyProgressionResult(amount, ATTRIBUTE_BASE_SPLIT_SYNC, helperCalled, before, after, mailboxCountBefore, mailboxCountAfter, error);
    }

    private static EnergyGrantResult grant(ServerPlayer player, double amount, String methodName, String kind) {
        EnergyDebugSnapshot before = inspect(player);
        int mailboxCountBefore = mailboxCount(player);
        if (player == null) {
            return EnergyGrantResult.skipped(kind, amount, before, before, mailboxCountBefore, mailboxCountBefore, "PLAYER_MISSING");
        }
        if (amount <= 0.0D) {
            return EnergyGrantResult.skipped(kind, amount, before, before, mailboxCountBefore, mailboxCountBefore, "INVALID_AMOUNT");
        }
        TensuraCompatStatus compatStatus = TensuraCompat.getStatus();
        if (compatStatus.integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE) {
            return EnergyGrantResult.skipped(kind, amount, before, before, mailboxCountBefore, mailboxCountBefore,
                compatStatus.integrationState().name());
        }
        boolean helperCalled = false;
        String error = "NONE";
        try {
            Class<?> helperClass = Class.forName(ENERGY_HELPER_CLASS);
            Class<?> gainTypeClass = Class.forName(GAIN_TYPE_CLASS);
            @SuppressWarnings("unchecked")
            Object gainTypeNormal = Enum.valueOf((Class<Enum>) gainTypeClass.asSubclass(Enum.class), GAIN_TYPE_NORMAL);
            Method method = helperClass.getMethod(methodName, LivingEntity.class, double.class, gainTypeClass);
            method.invoke(null, player, amount, gainTypeNormal);
            helperCalled = true;
        } catch (Exception | LinkageError ex) {
            error = ex.getClass().getSimpleName() + normalizeMessage(ex.getMessage());
        }
        EnergyDebugSnapshot after = inspect(player);
        int mailboxCountAfter = mailboxCount(player);
        return new EnergyGrantResult(kind, amount, helperCalled, before, after, mailboxCountBefore, mailboxCountAfter, error);
    }

    private static EnergyDebugSnapshot toEnergyDebugSnapshot(SourceDebugSnapshot source) {
        return new EnergyDebugSnapshot(
            source.storageAura(),
            source.storageMagicule(),
            source.storageEp(),
            source.energyHelperMaxEp(),
            source.energyHelperMaxAura(),
            source.energyHelperMaxMagicule(),
            source.existenceAvailable(),
            source.maxAuraAttribute().present(),
            source.maxMagiculeAttribute().present(),
            source.safeFallbackMode(),
            source.placeholderRewardOnly(),
            source.readError()
        );
    }

    private static int mailboxCount(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        try {
            return RewardMailboxData.get(player.serverLevel()).getMailbox(player.getUUID()).size();
        } catch (Exception | LinkageError ignored) {
            return 0;
        }
    }

    private static Object getExistence(ServerPlayer player) throws Exception {
        Class<?> storagesClass = Class.forName(TENSURA_STORAGES_CLASS);
        Method method = storagesClass.getMethod("getExistenceFrom", LivingEntity.class);
        return method.invoke(null, player);
    }

    private static void applyAttributeSplitSyncMaxEp(ServerPlayer player, double amount) throws Exception {
        Object auraAttribute = getTensuraAttributeInstance(player, "MAX_AURA");
        Object magiculeAttribute = getTensuraAttributeInstance(player, "MAX_MAGICULE");
        if (auraAttribute == null && magiculeAttribute == null) {
            throw new IllegalStateException("MAX_ENERGY_ATTRIBUTES_MISSING");
        }

        double splitAmount = amount / 2.0D;
        if (auraAttribute != null) {
            setAttributeBaseValue(auraAttribute, getAttributeBaseValue(auraAttribute) + splitAmount);
        }
        if (magiculeAttribute != null) {
            setAttributeBaseValue(magiculeAttribute, getAttributeBaseValue(magiculeAttribute) + splitAmount);
        }

        Object existence = getExistence(player);
        if (existence == null) {
            return;
        }
        if (auraAttribute != null) {
            invokeExistenceSetter(existence, "setAura", getAttributeValue(auraAttribute));
        }
        if (magiculeAttribute != null) {
            invokeExistenceSetter(existence, "setMagicule", getAttributeValue(magiculeAttribute));
        }
        existence.getClass().getMethod("markDirty").invoke(existence);
    }

    private static AttributeSnapshot captureAttribute(LivingEntity entity, String fieldName) {
        try {
            Object instance = getTensuraAttributeInstance(entity, fieldName);
            if (instance == null) {
                return AttributeSnapshot.missing(fieldName);
            }
            List<AttributeModifierSnapshot> modifiers = getAttributeModifiers(instance);
            return new AttributeSnapshot(
                fieldName,
                true,
                getAttributeBaseValue(instance),
                getAttributeValue(instance),
                modifiers,
                "NONE"
            );
        } catch (NoSuchFieldException ex) {
            return AttributeSnapshot.missing(fieldName);
        } catch (Exception | LinkageError ex) {
            return new AttributeSnapshot(fieldName, false, null, null, List.of(), ex.getClass().getSimpleName());
        }
    }

    private static List<AttributeModifierSnapshot> getAttributeModifiers(Object attributeInstance) {
        try {
            Object raw = attributeInstance.getClass().getMethod("getModifiers").invoke(attributeInstance);
            if (!(raw instanceof Collection<?> collection)) {
                return List.of();
            }
            List<AttributeModifierSnapshot> snapshots = new ArrayList<>();
            for (Object modifier : collection) {
                snapshots.add(captureModifier(modifier));
            }
            snapshots.sort(Comparator.comparing(AttributeModifierSnapshot::label));
            return snapshots;
        } catch (Exception | LinkageError ex) {
            return List.of(new AttributeModifierSnapshot("UNKNOWN", "UNKNOWN", null, null, ex.getClass().getSimpleName()));
        }
    }

    private static AttributeModifierSnapshot captureModifier(Object modifier) {
        if (modifier == null) {
            return new AttributeModifierSnapshot("UNKNOWN", "UNKNOWN", null, null, "NULL_MODIFIER");
        }
        try {
            String id = valueAsString(modifier.getClass().getMethod("id").invoke(modifier));
            Double amount = valueAsDouble(modifier.getClass().getMethod("amount").invoke(modifier));
            String operation = valueAsString(modifier.getClass().getMethod("operation").invoke(modifier));
            return new AttributeModifierSnapshot(id, modifier.getClass().getSimpleName(), amount, operation, "NONE");
        } catch (ReflectiveOperationException | LinkageError ex) {
            return new AttributeModifierSnapshot("UNKNOWN", modifier.getClass().getSimpleName(), null, null,
                ex.getClass().getSimpleName());
        }
    }

    private static LifecycleSnapshot captureLifecycle(Object existence) {
        if (existence == null) {
            return LifecycleSnapshot.empty("EXISTENCE_UNAVAILABLE");
        }
        try {
            return new LifecycleSnapshot(
                invokeBoolean(existence, "isSkippingEPDrop"),
                valueAsString(invokeExact(existence, "getSpawnType")),
                invokeDouble(existence, "getGainedEP"),
                invokeDouble(existence, "getHumanKill"),
                invokeDouble(existence, "getSleepModeTime"),
                invokeBoolean(existence, "isSpiritualForm"),
                invokeBoolean(existence, "isDemonLordSeed"),
                invokeBoolean(existence, "isTrueDemonLord"),
                invokeBoolean(existence, "isHeroEgg"),
                invokeBoolean(existence, "isTrueHero"),
                "NONE"
            );
        } catch (ReflectiveOperationException | LinkageError ex) {
            return LifecycleSnapshot.empty(ex.getClass().getSimpleName());
        }
    }

    private static RaceSnapshot captureRace(ServerPlayer player) {
        try {
            Class<?> raceApi = Class.forName("io.github.manasmods.manascore.race.api.RaceAPI");
            Object races = raceApi.getMethod("getRaceFrom", LivingEntity.class).invoke(null, player);
            Object optionalRace = races.getClass().getMethod("getRace").invoke(races);
            if (!(optionalRace instanceof java.util.Optional<?> optional) || optional.isEmpty()) {
                return RaceSnapshot.missing("RACE_UNAVAILABLE");
            }
            Object race = optional.get();
            Object display = race.getClass().getMethod("getDisplayName").invoke(race);
            return new RaceSnapshot(
                true,
                valueAsString(race.getClass().getMethod("getRaceId").invoke(race)),
                race.getClass().getSimpleName(),
                display instanceof net.minecraft.network.chat.Component component ? component.getString() : valueAsString(display),
                "NONE"
            );
        } catch (Exception | LinkageError ex) {
            return RaceSnapshot.missing(ex.getClass().getSimpleName());
        }
    }

    private static SkillSnapshot captureSkills(ServerPlayer player) {
        try {
            Class<?> skillApi = Class.forName("io.github.manasmods.manascore.skill.api.SkillAPI");
            Object skills = skillApi.getMethod("getSkillsFrom", LivingEntity.class).invoke(null, player);
            Object rawLearned = skills.getClass().getMethod("getLearnedSkills").invoke(skills);
            if (!(rawLearned instanceof Collection<?> learnedSkills)) {
                return SkillSnapshot.missing("LEARNED_SKILLS_NOT_COLLECTION");
            }
            List<String> learned = new ArrayList<>();
            int temporaryCount = 0;
            int toggledCount = 0;
            for (Object skill : learnedSkills) {
                learned.add(valueAsString(skill.getClass().getMethod("getSkillId").invoke(skill)));
                if (Boolean.TRUE.equals(skill.getClass().getMethod("isTemporarySkill").invoke(skill))) {
                    temporaryCount++;
                }
                if (Boolean.TRUE.equals(skill.getClass().getMethod("isToggled").invoke(skill))) {
                    toggledCount++;
                }
            }
            Collections.sort(learned);
            return new SkillSnapshot(
                learned.size(),
                temporaryCount,
                toggledCount,
                learned.stream().limit(10).toList(),
                "NONE"
            );
        } catch (Exception | LinkageError ex) {
            return SkillSnapshot.missing(ex.getClass().getSimpleName());
        }
    }

    private static Object getTensuraAttributeInstance(LivingEntity entity, String fieldName) throws Exception {
        Object holder = Class.forName(TENSURA_ATTRIBUTES_CLASS).getField(fieldName).get(null);
        Method method = LivingEntity.class.getMethod("getAttribute", Class.forName("net.minecraft.core.Holder"));
        return method.invoke(entity, holder);
    }

    private static double getAttributeBaseValue(Object attributeInstance) throws Exception {
        Object value = attributeInstance.getClass().getMethod("getBaseValue").invoke(attributeInstance);
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static double getAttributeValue(Object attributeInstance) throws Exception {
        Object value = attributeInstance.getClass().getMethod("getValue").invoke(attributeInstance);
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static void setAttributeBaseValue(Object attributeInstance, double value) throws Exception {
        attributeInstance.getClass().getMethod("setBaseValue", double.class).invoke(attributeInstance, value);
    }

    private static void invokeExistenceSetter(Object existence, String methodName, double value) throws Exception {
        existence.getClass().getMethod(methodName, double.class).invoke(existence, value);
    }

    private static double invokeDouble(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        Object value = method.invoke(target);
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static Double invokeStaticDouble(String className, String methodName, ServerPlayer player) {
        try {
            Class<?> helperClass = Class.forName(className);
            Method method = helperClass.getMethod(methodName, LivingEntity.class);
            Object value = method.invoke(null, player);
            return value instanceof Number number ? number.doubleValue() : null;
        } catch (Exception | LinkageError ex) {
            return null;
        }
    }

    private static Object invokeExact(Object target, String methodName) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private static Boolean invokeBoolean(Object target, String methodName) throws ReflectiveOperationException {
        Object value = invokeExact(target, methodName);
        return value instanceof Boolean bool ? bool : null;
    }

    private static Double valueAsDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static String valueAsString(Object value) {
        if (value == null) {
            return "UNKNOWN";
        }
        if (value instanceof ResourceLocation location) {
            return location.toString();
        }
        return String.valueOf(value);
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return " - " + message;
    }

    public record EnergyDebugSnapshot(
        double aura,
        double magicule,
        double ep,
        Double maxEp,
        Double maxAura,
        Double maxMagicule,
        boolean existenceAvailable,
        boolean auraAttributePresent,
        boolean magiculeAttributePresent,
        boolean safeFallbackMode,
        boolean placeholderRewardOnly,
        String readError
    ) {
        public static EnergyDebugSnapshot error(String readError) {
            return new EnergyDebugSnapshot(0.0D, 0.0D, 0.0D, null, null, null, false, false, false, true, true, readError);
        }
    }

    public record EnergyGrantResult(
        String kind,
        double amountRequested,
        boolean helperCalled,
        EnergyDebugSnapshot before,
        EnergyDebugSnapshot after,
        int mailboxCountBefore,
        int mailboxCountAfter,
        String error
    ) {
        public static EnergyGrantResult skipped(String kind, double amountRequested, EnergyDebugSnapshot before, EnergyDebugSnapshot after, int mailboxCountBefore, int mailboxCountAfter, String error) {
            return new EnergyGrantResult(kind, amountRequested, false, before, after, mailboxCountBefore, mailboxCountAfter, error);
        }

        public double auraDelta() {
            return after.aura() - before.aura();
        }

        public double magiculeDelta() {
            return after.magicule() - before.magicule();
        }

        public double epDelta() {
            return after.ep() - before.ep();
        }

        public boolean valueChanged() {
            return Math.abs(auraDelta()) > 0.0001D || Math.abs(magiculeDelta()) > 0.0001D || Math.abs(epDelta()) > 0.0001D;
        }

        public boolean mailboxUntouched() {
            return mailboxCountBefore == mailboxCountAfter;
        }
    }

    public record MaxEnergyProgressionResult(
        double amountRequested,
        String mutationPath,
        boolean helperCalled,
        EnergyDebugSnapshot before,
        EnergyDebugSnapshot after,
        int mailboxCountBefore,
        int mailboxCountAfter,
        String error
    ) {
        public static MaxEnergyProgressionResult skipped(double amountRequested, String mutationPath, EnergyDebugSnapshot before, EnergyDebugSnapshot after, int mailboxCountBefore, int mailboxCountAfter, String error) {
            return new MaxEnergyProgressionResult(amountRequested, mutationPath, false, before, after, mailboxCountBefore, mailboxCountAfter, error);
        }

        public double maxEpDelta() {
            return safeNumber(after.maxEp()) - safeNumber(before.maxEp());
        }

        public double maxAuraDelta() {
            return safeNumber(after.maxAura()) - safeNumber(before.maxAura());
        }

        public double maxMagiculeDelta() {
            return safeNumber(after.maxMagicule()) - safeNumber(before.maxMagicule());
        }

        public boolean valueChanged() {
            return Math.abs(maxEpDelta()) > 0.0001D || Math.abs(maxAuraDelta()) > 0.0001D || Math.abs(maxMagiculeDelta()) > 0.0001D;
        }

        public boolean mailboxUntouched() {
            return mailboxCountBefore == mailboxCountAfter;
        }

        private static double safeNumber(Double value) {
            return value == null ? 0.0D : value.doubleValue();
        }
    }

    public record SourceDebugSnapshot(
        double storageAura,
        double storageMagicule,
        double storageEp,
        boolean existenceAvailable,
        Double energyHelperMaxAura,
        Double energyHelperMaxMagicule,
        Double energyHelperMaxEp,
        AttributeSnapshot maxAuraAttribute,
        AttributeSnapshot maxMagiculeAttribute,
        AttributeSnapshot limitedSpiritualMaxAura,
        AttributeSnapshot limitedSpiritualMaxMagicule,
        LifecycleSnapshot lifecycle,
        RaceSnapshot race,
        SkillSnapshot skills,
        int rewardMailboxCount,
        boolean rewardMailboxUntouched,
        boolean placeholderRewardOnly,
        boolean safeFallbackMode,
        String readError
    ) {
        public static SourceDebugSnapshot error(String readError) {
            return new SourceDebugSnapshot(
                0.0D,
                0.0D,
                0.0D,
                false,
                null,
                null,
                null,
                AttributeSnapshot.missing("MAX_AURA"),
                AttributeSnapshot.missing("MAX_MAGICULE"),
                AttributeSnapshot.missing(LIMITED_SPIRITUAL_MAX_AURA),
                AttributeSnapshot.missing(LIMITED_SPIRITUAL_MAX_MAGICULE),
                LifecycleSnapshot.empty("UNAVAILABLE"),
                RaceSnapshot.missing("UNAVAILABLE"),
                SkillSnapshot.missing("UNAVAILABLE"),
                0,
                true,
                true,
                true,
                readError
            );
        }
    }

    public record AttributeSnapshot(
        String key,
        boolean present,
        Double baseValue,
        Double finalValue,
        List<AttributeModifierSnapshot> modifiers,
        String readError
    ) {
        public static AttributeSnapshot missing(String key) {
            return new AttributeSnapshot(key, false, null, null, List.of(), "MISSING");
        }
    }

    public record AttributeModifierSnapshot(
        String id,
        String label,
        Double amount,
        String operation,
        String readError
    ) {
    }

    public record LifecycleSnapshot(
        Boolean skippingEpDrop,
        String spawnType,
        Double gainedEp,
        Double humanKill,
        Double sleepModeTime,
        Boolean spiritualForm,
        Boolean demonLordSeed,
        Boolean trueDemonLord,
        Boolean heroEgg,
        Boolean trueHero,
        String readError
    ) {
        public static LifecycleSnapshot empty(String readError) {
            return new LifecycleSnapshot(null, "UNKNOWN", null, null, null, null, null, null, null, null, readError);
        }
    }

    public record RaceSnapshot(
        boolean present,
        String raceId,
        String raceClass,
        String raceDisplay,
        String readError
    ) {
        public static RaceSnapshot missing(String readError) {
            return new RaceSnapshot(false, "UNKNOWN", "UNKNOWN", "UNKNOWN", readError);
        }
    }

    public record SkillSnapshot(
        int learnedSkillCount,
        Integer temporarySkillCount,
        Integer toggledSkillCount,
        List<String> learnedSkillIds,
        String readError
    ) {
        public static SkillSnapshot missing(String readError) {
            return new SkillSnapshot(0, null, null, List.of(), readError);
        }
    }
}
