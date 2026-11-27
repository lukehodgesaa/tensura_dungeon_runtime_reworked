package com.mineclawd.tensuradungeon.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class AppraisalObservationLedger {
    public static final int MAX_PROFILES = 2_048;
    public static final int MAX_DIAGNOSTIC_ROWS = 20;
    private static final int FLOOR_BRACKET_SIZE = 50;
    private static final String UNKNOWN_ENCOUNTER = "UNKNOWN";

    private final List<Profile> profiles = new ArrayList<>();
    private long appraisedHauls;
    private long observedStacks;
    private long observedItems;
    private long overflowStacks;
    private long overflowItems;

    void observeHaul(int floor, List<Input> inputs) {
        appraisedHauls = saturatingAdd(appraisedHauls, 1L);
        if (inputs == null) return;
        int safeFloor = Math.max(1, floor);
        int bracketStart = ((safeFloor - 1) / FLOOR_BRACKET_SIZE) * FLOOR_BRACKET_SIZE + 1;
        for (Input input : inputs) {
            if (input == null || input.stack().isEmpty()) continue;
            ItemStack stack = input.stack().copy();
            long quantity = Math.max(1, stack.getCount());
            observedStacks = saturatingAdd(observedStacks, 1L);
            observedItems = saturatingAdd(observedItems, quantity);
            String encounter = normalizeEncounter(input.encounterClass());
            Profile match = profiles.stream()
                .filter(profile -> profile.bracketStart == bracketStart
                    && profile.encounterClass.equals(encounter)
                    && ItemStack.isSameItemSameComponents(profile.exemplar, stack))
                .findFirst()
                .orElse(null);
            if (match != null) {
                match.observe(safeFloor, quantity);
            } else if (profiles.size() < MAX_PROFILES) {
                profiles.add(new Profile(stack, bracketStart, encounter, safeFloor, quantity));
            } else {
                overflowStacks = saturatingAdd(overflowStacks, 1L);
                overflowItems = saturatingAdd(overflowItems, quantity);
            }
        }
    }

    public Snapshot snapshot(int requestedLimit) {
        int limit = Math.max(0, Math.min(MAX_DIAGNOSTIC_ROWS, requestedLimit));
        List<ProfileSnapshot> rows = profiles.stream()
            .sorted(Comparator.comparingLong(Profile::stackObservations).reversed()
                .thenComparing(Comparator.comparingLong(Profile::totalQuantity).reversed())
                .thenComparing(profile -> BuiltInRegistries.ITEM.getKey(profile.exemplar.getItem()).toString()))
            .limit(limit)
            .map(Profile::snapshot)
            .toList();
        return new Snapshot(
            appraisedHauls, observedStacks, observedItems, profiles.size(),
            overflowStacks, overflowItems, rows
        );
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putLong("AppraisedHauls", appraisedHauls);
        tag.putLong("ObservedStacks", observedStacks);
        tag.putLong("ObservedItems", observedItems);
        tag.putLong("OverflowStacks", overflowStacks);
        tag.putLong("OverflowItems", overflowItems);
        ListTag profileTags = new ListTag();
        for (Profile profile : profiles) profileTags.add(profile.save(registries));
        tag.put("Profiles", profileTags);
        return tag;
    }

    static AppraisalObservationLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        AppraisalObservationLedger ledger = new AppraisalObservationLedger();
        ledger.appraisedHauls = nonnegative(tag.getLong("AppraisedHauls"));
        ledger.observedStacks = nonnegative(tag.getLong("ObservedStacks"));
        ledger.observedItems = nonnegative(tag.getLong("ObservedItems"));
        ledger.overflowStacks = nonnegative(tag.getLong("OverflowStacks"));
        ledger.overflowItems = nonnegative(tag.getLong("OverflowItems"));
        ListTag profileTags = tag.getList("Profiles", Tag.TAG_COMPOUND);
        for (int index = 0; index < profileTags.size() && ledger.profiles.size() < MAX_PROFILES; index++) {
            Profile profile = Profile.load(profileTags.getCompound(index), registries);
            if (profile != null) ledger.profiles.add(profile);
        }
        return ledger;
    }

    record Input(ItemStack stack, String encounterClass) {
        Input {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            encounterClass = normalizeEncounter(encounterClass);
        }
    }

    public record Snapshot(
        long appraisedHauls,
        long observedStacks,
        long observedItems,
        int distinctProfiles,
        long overflowStacks,
        long overflowItems,
        List<ProfileSnapshot> profiles
    ) {
        public Snapshot {
            profiles = profiles == null ? List.of() : List.copyOf(profiles);
        }
    }

    public record ProfileSnapshot(
        String itemId,
        ItemStack exemplar,
        int bracketStart,
        int bracketEnd,
        String encounterClass,
        int minimumFloor,
        int maximumFloor,
        long stackObservations,
        long totalQuantity,
        boolean damageable,
        int damage,
        int maximumDamage,
        boolean enchanted,
        String componentFingerprint
    ) {
        public ProfileSnapshot {
            itemId = itemId == null ? "minecraft:air" : itemId;
            exemplar = exemplar == null ? ItemStack.EMPTY : exemplar.copy();
            encounterClass = normalizeEncounter(encounterClass);
            componentFingerprint = componentFingerprint == null ? "0" : componentFingerprint;
        }
    }

    private static final class Profile {
        private final ItemStack exemplar;
        private final int bracketStart;
        private final String encounterClass;
        private int minimumFloor;
        private int maximumFloor;
        private long stackObservations;
        private long totalQuantity;

        private Profile(ItemStack exemplar, int bracketStart, String encounterClass, int floor, long quantity) {
            this.exemplar = exemplar.copy();
            this.exemplar.setCount(1);
            this.bracketStart = Math.max(1, bracketStart);
            this.encounterClass = normalizeEncounter(encounterClass);
            this.minimumFloor = Math.max(1, floor);
            this.maximumFloor = Math.max(this.minimumFloor, floor);
            this.stackObservations = 1L;
            this.totalQuantity = Math.max(1L, quantity);
        }

        private void observe(int floor, long quantity) {
            minimumFloor = Math.min(minimumFloor, Math.max(1, floor));
            maximumFloor = Math.max(maximumFloor, Math.max(1, floor));
            stackObservations = saturatingAdd(stackObservations, 1L);
            totalQuantity = saturatingAdd(totalQuantity, Math.max(1L, quantity));
        }

        private long stackObservations() {
            return stackObservations;
        }

        private long totalQuantity() {
            return totalQuantity;
        }

        private ProfileSnapshot snapshot() {
            boolean damageable = exemplar.isDamageableItem();
            return new ProfileSnapshot(
                BuiltInRegistries.ITEM.getKey(exemplar.getItem()).toString(),
                exemplar,
                bracketStart,
                bracketStart + FLOOR_BRACKET_SIZE - 1,
                encounterClass,
                minimumFloor,
                maximumFloor,
                stackObservations,
                totalQuantity,
                damageable,
                damageable ? exemplar.getDamageValue() : 0,
                damageable ? exemplar.getMaxDamage() : 0,
                exemplar.isEnchanted(),
                Integer.toHexString(exemplar.getComponentsPatch().hashCode())
            );
        }

        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.put("Exemplar", exemplar.save(registries));
            tag.putInt("BracketStart", bracketStart);
            tag.putString("EncounterClass", encounterClass);
            tag.putInt("MinimumFloor", minimumFloor);
            tag.putInt("MaximumFloor", maximumFloor);
            tag.putLong("StackObservations", stackObservations);
            tag.putLong("TotalQuantity", totalQuantity);
            return tag;
        }

        private static Profile load(CompoundTag tag, HolderLookup.Provider registries) {
            if (!tag.contains("Exemplar", Tag.TAG_COMPOUND)) return null;
            ItemStack exemplar = ItemStack.parse(registries, tag.getCompound("Exemplar")).orElse(ItemStack.EMPTY);
            if (exemplar.isEmpty()) return null;
            Profile profile = new Profile(
                exemplar,
                Math.max(1, tag.getInt("BracketStart")),
                tag.getString("EncounterClass"),
                Math.max(1, tag.getInt("MinimumFloor")),
                Math.max(1L, tag.getLong("TotalQuantity"))
            );
            profile.maximumFloor = Math.max(profile.minimumFloor, tag.getInt("MaximumFloor"));
            profile.stackObservations = Math.max(1L, tag.getLong("StackObservations"));
            return profile;
        }
    }

    private static String normalizeEncounter(String value) {
        return value == null || value.isBlank() ? UNKNOWN_ENCOUNTER : value;
    }

    private static long nonnegative(long value) {
        return Math.max(0L, value);
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) return left;
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
