package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.compat.tensura.TensuraEnergyDebugHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class TensuraMaxEnergyProofData extends SavedData {
    private static final String DATA_NAME = "tensura_max_energy_proof_data";
    private final Map<UUID, SnapshotRecord> snapshots = new HashMap<>();
    private final Map<UUID, SourceSnapshotRecord> sourceSnapshots = new HashMap<>();

    public static TensuraMaxEnergyProofData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(TensuraMaxEnergyProofData::new, TensuraMaxEnergyProofData::load), DATA_NAME);
    }

    public static TensuraMaxEnergyProofData load(CompoundTag tag, HolderLookup.Provider registries) {
        TensuraMaxEnergyProofData data = new TensuraMaxEnergyProofData();
        ListTag snapshotsTag = tag.getList("Snapshots", Tag.TAG_COMPOUND);
        for (int i = 0; i < snapshotsTag.size(); i++) {
            CompoundTag snapshotTag = snapshotsTag.getCompound(i);
            if (!snapshotTag.hasUUID("Player")) {
                continue;
            }
            data.snapshots.put(snapshotTag.getUUID("Player"), SnapshotRecord.load(snapshotTag));
        }
        ListTag sourceSnapshotsTag = tag.getList("SourceSnapshots", Tag.TAG_COMPOUND);
        for (int i = 0; i < sourceSnapshotsTag.size(); i++) {
            CompoundTag snapshotTag = sourceSnapshotsTag.getCompound(i);
            if (!snapshotTag.hasUUID("Player")) {
                continue;
            }
            data.sourceSnapshots.put(snapshotTag.getUUID("Player"), SourceSnapshotRecord.load(snapshotTag));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag snapshotsTag = new ListTag();
        for (Map.Entry<UUID, SnapshotRecord> entry : snapshots.entrySet()) {
            CompoundTag snapshotTag = entry.getValue().save();
            snapshotTag.putUUID("Player", entry.getKey());
            snapshotsTag.add(snapshotTag);
        }
        tag.put("Snapshots", snapshotsTag);

        ListTag sourceSnapshotsTag = new ListTag();
        for (Map.Entry<UUID, SourceSnapshotRecord> entry : sourceSnapshots.entrySet()) {
            CompoundTag snapshotTag = entry.getValue().save();
            snapshotTag.putUUID("Player", entry.getKey());
            sourceSnapshotsTag.add(snapshotTag);
        }
        tag.put("SourceSnapshots", sourceSnapshotsTag);
        return tag;
    }

    public void saveSnapshot(UUID playerId, TensuraEnergyDebugHelper.EnergyDebugSnapshot snapshot, long gameTime) {
        snapshots.put(playerId, SnapshotRecord.from(snapshot, gameTime));
        setDirty();
    }

    public Optional<SnapshotRecord> getSnapshot(UUID playerId) {
        return Optional.ofNullable(snapshots.get(playerId));
    }

    public boolean clearSnapshot(UUID playerId) {
        if (snapshots.remove(playerId) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public void saveSourceSnapshot(UUID playerId, TensuraEnergyDebugHelper.SourceDebugSnapshot snapshot, long gameTime, String dimensionKey, String snapshotKind) {
        sourceSnapshots.put(playerId, SourceSnapshotRecord.from(snapshot, gameTime, dimensionKey, snapshotKind));
        setDirty();
    }

    public Optional<SourceSnapshotRecord> getSourceSnapshot(UUID playerId) {
        return Optional.ofNullable(sourceSnapshots.get(playerId));
    }

    public boolean clearSourceSnapshot(UUID playerId) {
        if (sourceSnapshots.remove(playerId) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public record SnapshotRecord(
        double aura,
        double magicule,
        double ep,
        Double maxEp,
        Double maxAura,
        Double maxMagicule,
        long recordedGameTime
    ) {
        private static SnapshotRecord from(TensuraEnergyDebugHelper.EnergyDebugSnapshot snapshot, long gameTime) {
            return new SnapshotRecord(snapshot.aura(), snapshot.magicule(), snapshot.ep(), snapshot.maxEp(), snapshot.maxAura(), snapshot.maxMagicule(), gameTime);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("Aura", aura);
            tag.putDouble("Magicule", magicule);
            tag.putDouble("EP", ep);
            putNullableDouble(tag, "MaxEP", maxEp);
            putNullableDouble(tag, "MaxAura", maxAura);
            putNullableDouble(tag, "MaxMagicule", maxMagicule);
            tag.putLong("RecordedGameTime", recordedGameTime);
            return tag;
        }

        private static SnapshotRecord load(CompoundTag tag) {
            return new SnapshotRecord(
                tag.getDouble("Aura"),
                tag.getDouble("Magicule"),
                tag.getDouble("EP"),
                getNullableDouble(tag, "MaxEP"),
                getNullableDouble(tag, "MaxAura"),
                getNullableDouble(tag, "MaxMagicule"),
                tag.getLong("RecordedGameTime")
            );
        }
    }

    public record SourceSnapshotRecord(
        double storageAura,
        double storageMagicule,
        double storageEp,
        boolean existenceAvailable,
        Double energyHelperMaxAura,
        Double energyHelperMaxMagicule,
        Double energyHelperMaxEp,
        AttributeRecord maxAuraAttribute,
        AttributeRecord maxMagiculeAttribute,
        AttributeRecord limitedSpiritualMaxAura,
        AttributeRecord limitedSpiritualMaxMagicule,
        LifecycleRecord lifecycle,
        RaceRecord race,
        SkillRecord skills,
        int rewardMailboxCount,
        boolean rewardMailboxUntouched,
        boolean placeholderRewardOnly,
        boolean safeFallbackMode,
        String readError,
        long recordedGameTime,
        String dimensionKey,
        String snapshotKind
    ) {
        private static SourceSnapshotRecord from(TensuraEnergyDebugHelper.SourceDebugSnapshot snapshot, long gameTime, String dimensionKey, String snapshotKind) {
            return new SourceSnapshotRecord(
                snapshot.storageAura(),
                snapshot.storageMagicule(),
                snapshot.storageEp(),
                snapshot.existenceAvailable(),
                snapshot.energyHelperMaxAura(),
                snapshot.energyHelperMaxMagicule(),
                snapshot.energyHelperMaxEp(),
                AttributeRecord.from(snapshot.maxAuraAttribute()),
                AttributeRecord.from(snapshot.maxMagiculeAttribute()),
                AttributeRecord.from(snapshot.limitedSpiritualMaxAura()),
                AttributeRecord.from(snapshot.limitedSpiritualMaxMagicule()),
                LifecycleRecord.from(snapshot.lifecycle()),
                RaceRecord.from(snapshot.race()),
                SkillRecord.from(snapshot.skills()),
                snapshot.rewardMailboxCount(),
                snapshot.rewardMailboxUntouched(),
                snapshot.placeholderRewardOnly(),
                snapshot.safeFallbackMode(),
                snapshot.readError(),
                gameTime,
                dimensionKey,
                snapshotKind
            );
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("StorageAura", storageAura);
            tag.putDouble("StorageMagicule", storageMagicule);
            tag.putDouble("StorageEP", storageEp);
            tag.putBoolean("ExistenceAvailable", existenceAvailable);
            putNullableDouble(tag, "EnergyHelperMaxAura", energyHelperMaxAura);
            putNullableDouble(tag, "EnergyHelperMaxMagicule", energyHelperMaxMagicule);
            putNullableDouble(tag, "EnergyHelperMaxEP", energyHelperMaxEp);
            tag.put("MaxAuraAttribute", maxAuraAttribute.save());
            tag.put("MaxMagiculeAttribute", maxMagiculeAttribute.save());
            tag.put("LimitedSpiritualMaxAura", limitedSpiritualMaxAura.save());
            tag.put("LimitedSpiritualMaxMagicule", limitedSpiritualMaxMagicule.save());
            tag.put("Lifecycle", lifecycle.save());
            tag.put("Race", race.save());
            tag.put("Skills", skills.save());
            tag.putInt("RewardMailboxCount", rewardMailboxCount);
            tag.putBoolean("RewardMailboxUntouched", rewardMailboxUntouched);
            tag.putBoolean("PlaceholderRewardOnly", placeholderRewardOnly);
            tag.putBoolean("SafeFallbackMode", safeFallbackMode);
            tag.putString("ReadError", readError);
            tag.putLong("RecordedGameTime", recordedGameTime);
            tag.putString("DimensionKey", dimensionKey);
            tag.putString("SnapshotKind", snapshotKind);
            return tag;
        }

        private static SourceSnapshotRecord load(CompoundTag tag) {
            return new SourceSnapshotRecord(
                tag.getDouble("StorageAura"),
                tag.getDouble("StorageMagicule"),
                tag.getDouble("StorageEP"),
                tag.getBoolean("ExistenceAvailable"),
                getNullableDouble(tag, "EnergyHelperMaxAura"),
                getNullableDouble(tag, "EnergyHelperMaxMagicule"),
                getNullableDouble(tag, "EnergyHelperMaxEP"),
                AttributeRecord.load(tag.getCompound("MaxAuraAttribute")),
                AttributeRecord.load(tag.getCompound("MaxMagiculeAttribute")),
                AttributeRecord.load(tag.getCompound("LimitedSpiritualMaxAura")),
                AttributeRecord.load(tag.getCompound("LimitedSpiritualMaxMagicule")),
                LifecycleRecord.load(tag.getCompound("Lifecycle")),
                RaceRecord.load(tag.getCompound("Race")),
                SkillRecord.load(tag.getCompound("Skills")),
                tag.getInt("RewardMailboxCount"),
                tag.getBoolean("RewardMailboxUntouched"),
                tag.getBoolean("PlaceholderRewardOnly"),
                tag.getBoolean("SafeFallbackMode"),
                tag.getString("ReadError"),
                tag.getLong("RecordedGameTime"),
                tag.getString("DimensionKey"),
                tag.getString("SnapshotKind")
            );
        }
    }

    public record AttributeRecord(String key, boolean present, Double baseValue, Double finalValue, List<ModifierRecord> modifiers, String readError) {
        private static AttributeRecord from(TensuraEnergyDebugHelper.AttributeSnapshot snapshot) {
            return new AttributeRecord(snapshot.key(), snapshot.present(), snapshot.baseValue(), snapshot.finalValue(), snapshot.modifiers().stream().map(ModifierRecord::from).toList(), snapshot.readError());
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Key", key);
            tag.putBoolean("Present", present);
            putNullableDouble(tag, "BaseValue", baseValue);
            putNullableDouble(tag, "FinalValue", finalValue);
            ListTag listTag = new ListTag();
            for (ModifierRecord modifier : modifiers) {
                listTag.add(modifier.save());
            }
            tag.put("Modifiers", listTag);
            tag.putString("ReadError", readError);
            return tag;
        }

        private static AttributeRecord load(CompoundTag tag) {
            ListTag listTag = tag.getList("Modifiers", Tag.TAG_COMPOUND);
            List<ModifierRecord> modifiers = new java.util.ArrayList<>();
            for (int i = 0; i < listTag.size(); i++) {
                modifiers.add(ModifierRecord.load(listTag.getCompound(i)));
            }
            return new AttributeRecord(tag.getString("Key"), tag.getBoolean("Present"), getNullableDouble(tag, "BaseValue"), getNullableDouble(tag, "FinalValue"), modifiers, tag.getString("ReadError"));
        }
    }

    public record ModifierRecord(String id, String label, Double amount, String operation, String readError) {
        private static ModifierRecord from(TensuraEnergyDebugHelper.AttributeModifierSnapshot snapshot) {
            return new ModifierRecord(snapshot.id(), snapshot.label(), snapshot.amount(), snapshot.operation(), snapshot.readError());
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Label", label);
            putNullableDouble(tag, "Amount", amount);
            tag.putString("Operation", operation == null ? "UNKNOWN" : operation);
            tag.putString("ReadError", readError);
            return tag;
        }

        private static ModifierRecord load(CompoundTag tag) {
            return new ModifierRecord(tag.getString("Id"), tag.getString("Label"), getNullableDouble(tag, "Amount"), tag.getString("Operation"), tag.getString("ReadError"));
        }
    }

    public record LifecycleRecord(Boolean skippingEpDrop, String spawnType, Double gainedEp, Double humanKill, Double sleepModeTime, Boolean spiritualForm, Boolean demonLordSeed, Boolean trueDemonLord, Boolean heroEgg, Boolean trueHero, String readError) {
        private static LifecycleRecord from(TensuraEnergyDebugHelper.LifecycleSnapshot snapshot) {
            return new LifecycleRecord(snapshot.skippingEpDrop(), snapshot.spawnType(), snapshot.gainedEp(), snapshot.humanKill(), snapshot.sleepModeTime(), snapshot.spiritualForm(), snapshot.demonLordSeed(), snapshot.trueDemonLord(), snapshot.heroEgg(), snapshot.trueHero(), snapshot.readError());
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            putNullableBoolean(tag, "SkippingEpDrop", skippingEpDrop);
            tag.putString("SpawnType", spawnType == null ? "UNKNOWN" : spawnType);
            putNullableDouble(tag, "GainedEp", gainedEp);
            putNullableDouble(tag, "HumanKill", humanKill);
            putNullableDouble(tag, "SleepModeTime", sleepModeTime);
            putNullableBoolean(tag, "SpiritualForm", spiritualForm);
            putNullableBoolean(tag, "DemonLordSeed", demonLordSeed);
            putNullableBoolean(tag, "TrueDemonLord", trueDemonLord);
            putNullableBoolean(tag, "HeroEgg", heroEgg);
            putNullableBoolean(tag, "TrueHero", trueHero);
            tag.putString("ReadError", readError);
            return tag;
        }

        private static LifecycleRecord load(CompoundTag tag) {
            return new LifecycleRecord(getNullableBoolean(tag, "SkippingEpDrop"), tag.getString("SpawnType"), getNullableDouble(tag, "GainedEp"), getNullableDouble(tag, "HumanKill"), getNullableDouble(tag, "SleepModeTime"), getNullableBoolean(tag, "SpiritualForm"), getNullableBoolean(tag, "DemonLordSeed"), getNullableBoolean(tag, "TrueDemonLord"), getNullableBoolean(tag, "HeroEgg"), getNullableBoolean(tag, "TrueHero"), tag.getString("ReadError"));
        }
    }

    public record RaceRecord(boolean present, String raceId, String raceClass, String raceDisplay, String readError) {
        private static RaceRecord from(TensuraEnergyDebugHelper.RaceSnapshot snapshot) {
            return new RaceRecord(snapshot.present(), snapshot.raceId(), snapshot.raceClass(), snapshot.raceDisplay(), snapshot.readError());
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Present", present);
            tag.putString("RaceId", raceId);
            tag.putString("RaceClass", raceClass);
            tag.putString("RaceDisplay", raceDisplay);
            tag.putString("ReadError", readError);
            return tag;
        }

        private static RaceRecord load(CompoundTag tag) {
            return new RaceRecord(tag.getBoolean("Present"), tag.getString("RaceId"), tag.getString("RaceClass"), tag.getString("RaceDisplay"), tag.getString("ReadError"));
        }
    }

    public record SkillRecord(int learnedSkillCount, Integer temporarySkillCount, Integer toggledSkillCount, List<String> learnedSkillIds, String readError) {
        private static SkillRecord from(TensuraEnergyDebugHelper.SkillSnapshot snapshot) {
            return new SkillRecord(snapshot.learnedSkillCount(), snapshot.temporarySkillCount(), snapshot.toggledSkillCount(), snapshot.learnedSkillIds(), snapshot.readError());
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("LearnedSkillCount", learnedSkillCount);
            putNullableInt(tag, "TemporarySkillCount", temporarySkillCount);
            putNullableInt(tag, "ToggledSkillCount", toggledSkillCount);
            ListTag listTag = new ListTag();
            for (String id : learnedSkillIds) {
                listTag.add(StringTag.valueOf(id));
            }
            tag.put("LearnedSkillIds", listTag);
            tag.putString("ReadError", readError);
            return tag;
        }

        private static SkillRecord load(CompoundTag tag) {
            ListTag listTag = tag.getList("LearnedSkillIds", Tag.TAG_STRING);
            List<String> ids = new java.util.ArrayList<>();
            for (int i = 0; i < listTag.size(); i++) {
                ids.add(listTag.getString(i));
            }
            return new SkillRecord(tag.getInt("LearnedSkillCount"), getNullableInt(tag, "TemporarySkillCount"), getNullableInt(tag, "ToggledSkillCount"), ids, tag.getString("ReadError"));
        }
    }

    private static void putNullableDouble(CompoundTag tag, String key, Double value) {
        if (value != null) {
            tag.putDouble(key, value);
        }
    }

    private static Double getNullableDouble(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getDouble(key) : null;
    }

    private static void putNullableBoolean(CompoundTag tag, String key, Boolean value) {
        if (value != null) {
            tag.putBoolean(key, value);
        }
    }

    private static Boolean getNullableBoolean(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getBoolean(key) : null;
    }

    private static void putNullableInt(CompoundTag tag, String key, Integer value) {
        if (value != null) {
            tag.putInt(key, value);
        }
    }

    private static Integer getNullableInt(CompoundTag tag, String key) {
        return tag.contains(key) ? tag.getInt(key) : null;
    }
}
