package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotPayload;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class KurobeForgeWeaponData {
    static final String ROOT = "tensura_dungeon:kurobe_forge";
    private static final String SCHEMA = "schema";
    private static final String GRADE = "grade";
    private static final String CONDUIT_RANK = "conduit_rank";
    private static final String WEAPON_ID = "weapon_id";
    private static final String CORE = "core";
    private static final String CORE_ID = "core_id";
    private static final String CORE_TIER = "tier";
    private static final String CORE_TOTAL = "total_charge";
    private static final String CORE_CAPACITY = "capacity";
    private static final String CORE_NATURAL = "natural_charge";
    private static final String CORE_FAMILY = "family";
    private static final String CORE_TECHNIQUE = "technique";
    private static final String TECHNIQUE_COOLDOWN_UNTIL = "technique_cooldown_until";
    private static final int CURRENT_SCHEMA = 3;

    private KurobeForgeWeaponData() {}

    public static State read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return State.initial();
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return State.initial();
        CompoundTag root = custom.copyTag();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return State.initial();
        CompoundTag forge = root.getCompound(ROOT);
        KurobeForgeSnapshotPayload.Grade grade = parseGrade(forge.getString(GRADE));
        int rank = forge.getInt(CONDUIT_RANK);
        if (rank < 0 || rank > 6) rank = 0;
        UUID weaponId = forge.hasUUID(WEAPON_ID) ? forge.getUUID(WEAPON_ID) : null;
        CoreSatchelData.CoreRecord core = readCore(forge);
        if (core != null && weaponId == null) {
            core = null;
        }
        long cooldownUntil = Math.max(0L, forge.getLong(TECHNIQUE_COOLDOWN_UNTIL));
        return new State(grade, rank, weaponId, core, cooldownUntil);
    }

    public static boolean hasCorruptSocket(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return false;
        CompoundTag root = custom.copyTag();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return false;
        CompoundTag forge = root.getCompound(ROOT);
        if (!forge.contains(CORE)) return false;
        return readCore(forge) == null || !forge.hasUUID(WEAPON_ID);
    }

    public static UUID ensureWeaponId(ItemStack stack) {
        State current = read(stack);
        if (current.weaponId() != null) return current.weaponId();
        UUID id = UUID.randomUUID();
        write(stack, new State(
            current.grade(), current.conduitRank(), id, current.core(), current.techniqueCooldownUntil()
        ));
        return id;
    }

    public static void setConduitRank(ItemStack stack, int rank) {
        requireStack(stack);
        State current = read(stack);
        UUID weaponId = current.weaponId() == null ? UUID.randomUUID() : current.weaponId();
        write(stack, new State(
            current.grade(), rank, weaponId, current.core(), current.techniqueCooldownUntil()
        ));
    }

    public static void promote(ItemStack stack, KurobeForgeSnapshotPayload.Grade target) {
        requireStack(stack);
        State current = read(stack);
        if (target == null || target.ordinal() != current.grade().ordinal() + 1) {
            throw new IllegalArgumentException("Kurobe promotion must advance exactly one grade");
        }
        UUID weaponId = current.weaponId() == null ? UUID.randomUUID() : current.weaponId();
        write(stack, new State(target, 0, weaponId, current.core(), 0L));
    }

    public static void socket(ItemStack stack, CoreSatchelData.CoreRecord core) {
        requireStack(stack);
        if (core == null) throw new IllegalArgumentException("Monster Core is required");
        State current = read(stack);
        if (current.core() != null) throw new IllegalStateException("Weapon socket is already occupied");
        UUID weaponId = current.weaponId() == null ? UUID.randomUUID() : current.weaponId();
        write(stack, new State(current.grade(), current.conduitRank(), weaponId, core, 0L));
    }

    public static CoreSatchelData.CoreRecord removeCore(ItemStack stack) {
        requireStack(stack);
        State current = read(stack);
        if (current.core() == null) throw new IllegalStateException("Weapon socket is empty");
        write(stack, new State(current.grade(), current.conduitRank(), current.weaponId(), null, 0L));
        return current.core();
    }

    public static void recharge(ItemStack stack) {
        requireStack(stack);
        State current = read(stack);
        if (current.core() == null) throw new IllegalStateException("Weapon socket is empty");
        CoreSatchelData.CoreRecord core = current.core();
        write(stack, new State(
            current.grade(),
            current.conduitRank(),
            current.weaponId(),
            core.recharged(),
            current.techniqueCooldownUntil()
        ));
    }

    public static boolean recordTechniqueCast(ItemStack stack, long chargeDebit, long cooldownUntil) {
        requireStack(stack);
        if (chargeDebit < 0L || cooldownUntil < 0L) return false;
        State current = read(stack);
        if (current.core() == null || chargeDebit > current.core().totalCharge()) return false;
        write(stack, new State(
            current.grade(),
            current.conduitRank(),
            current.weaponId(),
            current.core().consumeCharge(chargeDebit),
            cooldownUntil
        ));
        return true;
    }

    static void write(ItemStack stack, State state) {
        requireStack(stack);
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = custom == null ? new CompoundTag() : custom.copyTag();
        CompoundTag forge = root.contains(ROOT, Tag.TAG_COMPOUND)
            ? root.getCompound(ROOT).copy()
            : new CompoundTag();
        forge.putInt(SCHEMA, CURRENT_SCHEMA);
        forge.putString(GRADE, state.grade().name());
        forge.putInt(CONDUIT_RANK, state.conduitRank());
        forge.putLong(TECHNIQUE_COOLDOWN_UNTIL, state.techniqueCooldownUntil());
        if (state.weaponId() == null) forge.remove(WEAPON_ID);
        else forge.putUUID(WEAPON_ID, state.weaponId());
        if (state.core() == null) {
            forge.remove(CORE);
        } else {
            CompoundTag core = new CompoundTag();
            core.putUUID(CORE_ID, state.core().coreId());
            core.putInt(CORE_TIER, state.core().tier());
            core.putLong(CORE_TOTAL, state.core().totalCharge());
            core.putLong(CORE_CAPACITY, state.core().capacity());
            core.putLong(CORE_NATURAL, state.core().naturalCharge());
            core.putString(CORE_FAMILY, state.core().familyId());
            core.putString(CORE_TECHNIQUE, state.core().techniqueId());
            forge.put(CORE, core);
        }
        root.put(ROOT, forge);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static CoreSatchelData.CoreRecord readCore(CompoundTag forge) {
        if (!forge.contains(CORE, Tag.TAG_COMPOUND)) return null;
        CompoundTag core = forge.getCompound(CORE);
        if (!core.hasUUID(CORE_ID)) return null;
        try {
            String family = core.getString(CORE_FAMILY);
            String technique = core.getString(CORE_TECHNIQUE);
            return family.isBlank() || technique.isBlank()
                ? new CoreSatchelData.CoreRecord(
                    core.getUUID(CORE_ID),
                    core.getInt(CORE_TIER),
                    core.getLong(CORE_TOTAL),
                    core.getLong(CORE_CAPACITY),
                    core.getLong(CORE_NATURAL)
                )
                : new CoreSatchelData.CoreRecord(
                    core.getUUID(CORE_ID),
                    core.getInt(CORE_TIER),
                    core.getLong(CORE_TOTAL),
                    core.getLong(CORE_CAPACITY),
                    core.getLong(CORE_NATURAL),
                    family,
                    technique
                );
        } catch (IllegalArgumentException corrupted) {
            return null;
        }
    }

    private static void requireStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Forge weapon is required");
    }

    private static KurobeForgeSnapshotPayload.Grade parseGrade(String raw) {
        try {
            return KurobeForgeSnapshotPayload.Grade.valueOf(raw);
        } catch (IllegalArgumentException failure) {
            return KurobeForgeSnapshotPayload.Grade.NORMAL;
        }
    }

    public record State(
        KurobeForgeSnapshotPayload.Grade grade,
        int conduitRank,
        UUID weaponId,
        CoreSatchelData.CoreRecord core,
        long techniqueCooldownUntil
    ) {
        public State(
            KurobeForgeSnapshotPayload.Grade grade,
            int conduitRank,
            UUID weaponId,
            CoreSatchelData.CoreRecord core
        ) {
            this(grade, conduitRank, weaponId, core, 0L);
        }

        public State {
            if (grade == null || conduitRank < 0 || conduitRank > 6 || techniqueCooldownUntil < 0L) {
                throw new IllegalArgumentException("Kurobe weapon state is invalid");
            }
            if (core != null && weaponId == null) {
                throw new IllegalArgumentException("Socketed Kurobe weapon requires persistent identity");
            }
        }

        public static State initial() {
            return new State(KurobeForgeSnapshotPayload.Grade.NORMAL, 0, null, null, 0L);
        }
    }
}
