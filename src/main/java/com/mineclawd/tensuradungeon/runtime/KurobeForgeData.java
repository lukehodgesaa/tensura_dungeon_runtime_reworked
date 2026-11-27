package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotPayload;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class KurobeForgeData extends SavedData {
    static final String DATA_NAME = "tensura_dungeon_kurobe_forge";
    private static final String CERTIFICATIONS = "Certifications";
    private static final String TUTORIALS = "TutorialSockets";
    private static final String BINDINGS = "CoreBindings";
    private static final String PLAYER = "Player";
    private static final String GRADE = "Grade";
    private static final String CORE = "Core";
    private static final String WEAPON = "Weapon";

    private final Map<UUID, KurobeForgeSnapshotPayload.Grade> certifications = new HashMap<>();
    private final Set<UUID> tutorialSocketsUsed = new HashSet<>();
    private final Map<UUID, CoreBinding> coreBindings = new HashMap<>();

    public static KurobeForgeData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(KurobeForgeData::new, KurobeForgeData::load),
            DATA_NAME
        );
    }

    public static KurobeForgeData load(CompoundTag tag, HolderLookup.Provider registries) {
        KurobeForgeData data = new KurobeForgeData();
        ListTag certifications = tag.getList(CERTIFICATIONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < certifications.size(); index++) {
            CompoundTag entry = certifications.getCompound(index);
            if (!entry.hasUUID(PLAYER)) continue;
            try {
                KurobeForgeSnapshotPayload.Grade grade = KurobeForgeSnapshotPayload.Grade.valueOf(
                    entry.getString(GRADE)
                );
                if (grade != KurobeForgeSnapshotPayload.Grade.NORMAL) {
                    data.certifications.put(entry.getUUID(PLAYER), grade);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        ListTag tutorials = tag.getList(TUTORIALS, Tag.TAG_COMPOUND);
        for (int index = 0; index < tutorials.size(); index++) {
            CompoundTag entry = tutorials.getCompound(index);
            if (entry.hasUUID(PLAYER)) data.tutorialSocketsUsed.add(entry.getUUID(PLAYER));
        }
        Set<UUID> weaponIds = new HashSet<>();
        ListTag bindings = tag.getList(BINDINGS, Tag.TAG_COMPOUND);
        for (int index = 0; index < bindings.size(); index++) {
            CompoundTag entry = bindings.getCompound(index);
            if (!entry.hasUUID(CORE) || !entry.hasUUID(PLAYER) || !entry.hasUUID(WEAPON)) continue;
            UUID core = entry.getUUID(CORE);
            UUID weapon = entry.getUUID(WEAPON);
            if (data.coreBindings.containsKey(core) || !weaponIds.add(weapon)) {
                throw new IllegalStateException("Duplicate Kurobe Core or weapon binding in save data");
            }
            data.coreBindings.put(core, new CoreBinding(entry.getUUID(PLAYER), weapon));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag certificationList = new ListTag();
        certifications.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> {
                CompoundTag value = new CompoundTag();
                value.putUUID(PLAYER, entry.getKey());
                value.putString(GRADE, entry.getValue().name());
                certificationList.add(value);
            });
        tag.put(CERTIFICATIONS, certificationList);

        ListTag tutorialList = new ListTag();
        tutorialSocketsUsed.stream().sorted(Comparator.comparing(UUID::toString)).forEach(player -> {
            CompoundTag value = new CompoundTag();
            value.putUUID(PLAYER, player);
            tutorialList.add(value);
        });
        tag.put(TUTORIALS, tutorialList);

        ListTag bindingList = new ListTag();
        coreBindings.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> {
                CompoundTag value = new CompoundTag();
                value.putUUID(CORE, entry.getKey());
                value.putUUID(PLAYER, entry.getValue().playerId());
                value.putUUID(WEAPON, entry.getValue().weaponId());
                bindingList.add(value);
            });
        tag.put(BINDINGS, bindingList);
        return tag;
    }

    public KurobeForgeSnapshotPayload.Grade certifiedGrade(UUID playerId) {
        return certifications.getOrDefault(playerId, KurobeForgeSnapshotPayload.Grade.NORMAL);
    }

    public boolean certify(UUID playerId, KurobeForgeSnapshotPayload.Grade target) {
        if (playerId == null || target == null
            || target == KurobeForgeSnapshotPayload.Grade.GENESIS) return false;
        KurobeForgeSnapshotPayload.Grade current = certifiedGrade(playerId);
        if (target.ordinal() != current.ordinal() + 1) return false;
        certifications.put(playerId, target);
        setDirty();
        return true;
    }

    boolean rollbackCertification(UUID playerId, KurobeForgeSnapshotPayload.Grade target) {
        if (playerId == null || target == null || target == KurobeForgeSnapshotPayload.Grade.NORMAL
            || target == KurobeForgeSnapshotPayload.Grade.GENESIS
            || certifiedGrade(playerId) != target) return false;
        KurobeForgeSnapshotPayload.Grade previous = KurobeForgeSnapshotPayload.Grade.values()[
            target.ordinal() - 1
        ];
        if (previous == KurobeForgeSnapshotPayload.Grade.NORMAL) certifications.remove(playerId);
        else certifications.put(playerId, previous);
        setDirty();
        return true;
    }

    public boolean tutorialSocketAvailable(UUID playerId) {
        return playerId != null && !tutorialSocketsUsed.contains(playerId);
    }

    public boolean markTutorialSocketUsed(UUID playerId) {
        if (playerId == null || !tutorialSocketsUsed.add(playerId)) return false;
        setDirty();
        return true;
    }

    boolean rollbackTutorialSocket(UUID playerId) {
        if (playerId == null || !tutorialSocketsUsed.remove(playerId)) return false;
        setDirty();
        return true;
    }

    public CoreBinding binding(UUID coreId) {
        return coreId == null ? null : coreBindings.get(coreId);
    }

    public boolean bindingMatches(UUID coreId, UUID playerId, UUID weaponId) {
        return coreId != null && playerId != null && weaponId != null
            && new CoreBinding(playerId, weaponId).equals(coreBindings.get(coreId));
    }

    public boolean bindCore(UUID coreId, UUID playerId, UUID weaponId) {
        if (coreId == null || playerId == null || weaponId == null || coreBindings.containsKey(coreId)) {
            return false;
        }
        if (coreBindings.values().stream().anyMatch(binding -> binding.weaponId().equals(weaponId))) {
            return false;
        }
        coreBindings.put(coreId, new CoreBinding(playerId, weaponId));
        setDirty();
        return true;
    }

    public boolean unbindCore(UUID coreId, UUID playerId, UUID weaponId) {
        if (!bindingMatches(coreId, playerId, weaponId)) return false;
        coreBindings.remove(coreId);
        setDirty();
        return true;
    }

    public PlayerSnapshot snapshot(UUID playerId) {
        Map<UUID, UUID> bindings = new HashMap<>();
        coreBindings.forEach((core, binding) -> {
            if (binding.playerId().equals(playerId)) bindings.put(core, binding.weaponId());
        });
        return new PlayerSnapshot(
            certifiedGrade(playerId),
            tutorialSocketAvailable(playerId),
            Map.copyOf(bindings)
        );
    }

    public record CoreBinding(UUID playerId, UUID weaponId) {
        public CoreBinding {
            if (playerId == null || weaponId == null) {
                throw new IllegalArgumentException("Kurobe Core binding identity is required");
            }
        }
    }

    public record PlayerSnapshot(
        KurobeForgeSnapshotPayload.Grade certifiedGrade,
        boolean tutorialSocketAvailable,
        Map<UUID, UUID> coreBindings
    ) {
        public PlayerSnapshot {
            if (certifiedGrade == null || coreBindings == null) {
                throw new IllegalArgumentException("Kurobe player snapshot is incomplete");
            }
            coreBindings = Map.copyOf(coreBindings);
        }
    }
}
