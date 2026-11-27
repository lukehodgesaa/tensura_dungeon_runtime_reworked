package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.DungeonPreparationLoadoutData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record DungeonPreparationLoadoutSnapshotPayload(
    int schemaVersion,
    UUID targetId,
    String targetName,
    TargetKind targetKind,
    boolean editable,
    long revision,
    List<Selection> selections,
    List<Equipment> equipment,
    List<InventoryCandidate> inventoryCandidates,
    List<Integer> reservedInventorySlots,
    List<AbilitySlot> currentAbilities,
    List<SkillIcon> learnedSkills,
    List<SkillIcon> selectedPassiveSkills,
    boolean skillsAvailable,
    String message,
    double currentMagicule,
    double maxMagicule,
    boolean magiculeAvailable
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 9;
    public static final int MAX_SELECTIONS = 18;
    public static final int MAX_EQUIPMENT = 6;
    public static final int MAX_INVENTORY_CANDIDATES = 64;
    public static final int MAX_RESERVED_SLOTS = 64;
    public static final int MAX_CURRENT_ABILITIES = 3;
    public static final int MAX_LEARNED_SKILLS = 64;
    public static final int MAX_SELECTED_PASSIVE_SKILLS = 2;
    public static final Type<DungeonPreparationLoadoutSnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonRuntime.MOD_ID,
            "dungeon_preparation_loadout_snapshot"
        )
    );
    public static final StreamCodec<
        RegistryFriendlyByteBuf,
        DungeonPreparationLoadoutSnapshotPayload
    > STREAM_CODEC = StreamCodec.of(
        DungeonPreparationLoadoutSnapshotPayload::write,
        DungeonPreparationLoadoutSnapshotPayload::read
    );

    public DungeonPreparationLoadoutSnapshotPayload {
        if (schemaVersion < 0 || revision < 0L) {
            throw new IllegalArgumentException("Preparation loadout schema/revision cannot be negative");
        }
        targetId = Objects.requireNonNull(targetId, "Preparation loadout target");
        targetName = bounded(targetName, 32);
        targetKind = Objects.requireNonNull(targetKind, "Preparation loadout target kind");
        selections = selections == null ? List.of() : List.copyOf(selections);
        if (selections.size() > MAX_SELECTIONS) {
            throw new IllegalArgumentException("Preparation loadout snapshot exceeds bounded selection count");
        }
        Map<Integer, DungeonPreparationLoadoutData.Category> categoriesBySlot = new HashMap<>();
        for (Selection selection : selections) {
            Objects.requireNonNull(selection, "Preparation loadout selection");
            DungeonPreparationLoadoutData.Category previous = categoriesBySlot.putIfAbsent(
                selection.inventorySlot(),
                selection.category()
            );
            if (previous != null && previous != selection.category()) {
                throw new IllegalArgumentException(
                    "Preparation loadout inventory slot spans categories: "
                        + selection.inventorySlot()
                );
            }
        }
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        if (equipment.size() > MAX_EQUIPMENT) {
            throw new IllegalArgumentException("Preparation equipment projection exceeds its bound");
        }
        inventoryCandidates = inventoryCandidates == null
            ? List.of()
            : List.copyOf(inventoryCandidates);
        if (inventoryCandidates.size() > MAX_INVENTORY_CANDIDATES) {
            throw new IllegalArgumentException("Preparation inventory projection exceeds its bound");
        }
        Set<DungeonPreparationLoadoutData.Category> equipmentCategories = new HashSet<>();
        for (Equipment entry : equipment) {
            Objects.requireNonNull(entry, "Preparation equipment projection");
            if (!equipmentCategories.add(entry.category())) {
                throw new IllegalArgumentException(
                    "Duplicate preparation equipment category " + entry.category()
                );
            }
        }
        List<Integer> reservedSlots = reservedInventorySlots == null
            ? List.of()
            : new ArrayList<>(reservedInventorySlots);
        if (reservedSlots.size() > MAX_RESERVED_SLOTS
            || reservedSlots.stream().anyMatch(slot -> slot == null || slot < 0)) {
            throw new IllegalArgumentException("Invalid preparation reserved inventory slots");
        }
        reservedInventorySlots = reservedSlots.stream().distinct().sorted().toList();
        currentAbilities = currentAbilities == null ? List.of() : List.copyOf(currentAbilities);
        if (currentAbilities.size() > MAX_CURRENT_ABILITIES) {
            throw new IllegalArgumentException(
                "Preparation current-ability projection exceeds its bound"
            );
        }
        currentAbilities.forEach(ability -> Objects.requireNonNull(
            ability, "Preparation current-ability projection"
        ));
        learnedSkills = learnedSkills == null ? List.of() : List.copyOf(learnedSkills);
        if (learnedSkills.size() > MAX_LEARNED_SKILLS) {
            throw new IllegalArgumentException(
                "Preparation learned-skill projection exceeds its bound"
            );
        }
        learnedSkills.forEach(skill -> Objects.requireNonNull(
            skill, "Preparation learned-skill projection"
        ));
        selectedPassiveSkills = selectedPassiveSkills == null
            ? List.of()
            : List.copyOf(selectedPassiveSkills);
        if (selectedPassiveSkills.size() > MAX_SELECTED_PASSIVE_SKILLS) {
            throw new IllegalArgumentException(
                "Preparation selected-passive projection exceeds its bound"
            );
        }
        Set<String> selectedPassiveIds = new HashSet<>();
        selectedPassiveSkills.forEach(skill -> {
            Objects.requireNonNull(skill, "Preparation selected-passive projection");
            if (!skill.passive() || !selectedPassiveIds.add(skill.id())) {
                throw new IllegalArgumentException(
                    "Preparation selected-passive projection contains an invalid or duplicate skill"
                );
            }
        });
        message = bounded(message, 160);
        if (!magiculeAvailable || !Double.isFinite(currentMagicule)
            || !Double.isFinite(maxMagicule)) {
            currentMagicule = 0.0D;
            maxMagicule = 0.0D;
            magiculeAvailable = false;
        } else {
            currentMagicule = Math.max(0.0D, currentMagicule);
            maxMagicule = Math.max(0.0D, maxMagicule);
        }
    }

    public DungeonPreparationLoadoutSnapshotPayload(
        int schemaVersion,
        UUID targetId,
        String targetName,
        TargetKind targetKind,
        boolean editable,
        long revision,
        List<Selection> selections,
        List<Equipment> equipment,
        List<InventoryCandidate> inventoryCandidates,
        List<Integer> reservedInventorySlots,
        String message
    ) {
        this(
            schemaVersion,
            targetId,
            targetName,
            targetKind,
            editable,
            revision,
            selections,
            equipment,
            inventoryCandidates,
            reservedInventorySlots,
            List.of(),
            List.of(),
            List.of(),
            true,
            message,
            0.0D,
            0.0D,
            false
        );
    }

    public static DungeonPreparationLoadoutSnapshotPayload from(
        UUID targetId,
        String targetName,
        TargetKind targetKind,
        boolean editable,
        DungeonPreparationLoadoutData.LoadoutSnapshot snapshot,
        List<Equipment> equipment,
        List<InventoryCandidate> inventoryCandidates,
        List<Integer> reservedInventorySlots,
        String message
    ) {
        return from(
            targetId,
            targetName,
            targetKind,
            editable,
            snapshot,
            equipment,
            inventoryCandidates,
            reservedInventorySlots,
            List.of(),
            List.of(),
            List.of(),
            true,
            message
        );
    }

    public static DungeonPreparationLoadoutSnapshotPayload from(
        UUID targetId,
        String targetName,
        TargetKind targetKind,
        boolean editable,
        DungeonPreparationLoadoutData.LoadoutSnapshot snapshot,
        List<Equipment> equipment,
        List<InventoryCandidate> inventoryCandidates,
        List<Integer> reservedInventorySlots,
        List<SkillIcon> learnedSkills,
        boolean skillsAvailable,
        String message
    ) {
        return from(
            targetId,
            targetName,
            targetKind,
            editable,
            snapshot,
            equipment,
            inventoryCandidates,
            reservedInventorySlots,
            List.of(),
            learnedSkills,
            List.of(),
            skillsAvailable,
            message,
            0.0D,
            0.0D,
            false
        );
    }

    public DungeonPreparationLoadoutSnapshotPayload(
        int schemaVersion,
        UUID targetId,
        String targetName,
        TargetKind targetKind,
        boolean editable,
        long revision,
        List<Selection> selections,
        List<Equipment> equipment,
        List<InventoryCandidate> inventoryCandidates,
        List<Integer> reservedInventorySlots,
        List<AbilitySlot> currentAbilities,
        List<SkillIcon> learnedSkills,
        boolean skillsAvailable,
        String message
    ) {
        this(
            schemaVersion,
            targetId,
            targetName,
            targetKind,
            editable,
            revision,
            selections,
            equipment,
            inventoryCandidates,
            reservedInventorySlots,
            currentAbilities,
            learnedSkills,
            List.of(),
            skillsAvailable,
            message,
            0.0D,
            0.0D,
            false
        );
    }

    public static DungeonPreparationLoadoutSnapshotPayload from(
        UUID targetId,
        String targetName,
        TargetKind targetKind,
        boolean editable,
        DungeonPreparationLoadoutData.LoadoutSnapshot snapshot,
        List<Equipment> equipment,
        List<InventoryCandidate> inventoryCandidates,
        List<Integer> reservedInventorySlots,
        List<AbilitySlot> currentAbilities,
        List<SkillIcon> learnedSkills,
        List<SkillIcon> selectedPassiveSkills,
        boolean skillsAvailable,
        String message
    ) {
        return from(
            targetId,
            targetName,
            targetKind,
            editable,
            snapshot,
            equipment,
            inventoryCandidates,
            reservedInventorySlots,
            currentAbilities,
            learnedSkills,
            selectedPassiveSkills,
            skillsAvailable,
            message,
            0.0D,
            0.0D,
            false
        );
    }

    public static DungeonPreparationLoadoutSnapshotPayload from(
        UUID targetId,
        String targetName,
        TargetKind targetKind,
        boolean editable,
        DungeonPreparationLoadoutData.LoadoutSnapshot snapshot,
        List<Equipment> equipment,
        List<InventoryCandidate> inventoryCandidates,
        List<Integer> reservedInventorySlots,
        List<AbilitySlot> currentAbilities,
        List<SkillIcon> learnedSkills,
        List<SkillIcon> selectedPassiveSkills,
        boolean skillsAvailable,
        String message,
        double currentMagicule,
        double maxMagicule,
        boolean magiculeAvailable
    ) {
        List<Selection> selections = new ArrayList<>();
        for (DungeonPreparationLoadoutData.Category category
            : DungeonPreparationLoadoutData.Category.values()) {
            for (DungeonPreparationLoadoutData.SelectionView view : snapshot.category(category)) {
                int projectedSlots = category.maxQuantityPerSlot() == 1
                    ? view.quantity()
                    : 1;
                int projectedQuantity = category.maxQuantityPerSlot() == 1
                    ? 1
                    : view.quantity();
                for (int slot = 0; slot < projectedSlots; slot++) {
                    selections.add(new Selection(
                        category,
                        view.inventorySlot(),
                        projectedQuantity,
                        view.stack(),
                        view.valid()
                    ));
                }
            }
        }
        return new DungeonPreparationLoadoutSnapshotPayload(
            CURRENT_SCHEMA,
            targetId,
            targetName,
            targetKind,
            editable,
            snapshot.revision(),
            selections,
            equipment,
            inventoryCandidates,
            reservedInventorySlots,
            currentAbilities,
            learnedSkills,
            selectedPassiveSkills,
            skillsAvailable,
            message,
            currentMagicule,
            maxMagicule,
            magiculeAvailable
        );
    }

    public List<Selection> category(DungeonPreparationLoadoutData.Category category) {
        return selections.stream().filter(selection -> selection.category() == category).toList();
    }

    public int count(DungeonPreparationLoadoutData.Category category) {
        return category(category).stream()
            .filter(Selection::valid)
            .mapToInt(Selection::quantity)
            .sum();
    }

    public int slotCount(DungeonPreparationLoadoutData.Category category) {
        return (int)category(category).stream().filter(Selection::valid).count();
    }

    public ItemStack firstValid(DungeonPreparationLoadoutData.Category category) {
        return category(category).stream()
            .filter(Selection::valid)
            .map(Selection::stack)
            .findFirst()
            .map(ItemStack::copy)
            .orElse(ItemStack.EMPTY);
    }

    public ItemStack displayedEquipment(DungeonPreparationLoadoutData.Category category) {
        return equipment.stream()
            .filter(entry -> entry.category() == category)
            .map(Equipment::stack)
            .findFirst()
            .map(ItemStack::copy)
            .orElseGet(() -> firstValid(category));
    }

    public boolean canEdit(DungeonPreparationLoadoutData.Category category) {
        return editable && (
            targetKind == TargetKind.PLAYER
                || category == DungeonPreparationLoadoutData.Category.RESURRECTION_BRACELET
                || category == DungeonPreparationLoadoutData.Category.RETURN_WHISTLE
        );
    }

    public boolean supportedSchema() {
        return schemaVersion == CURRENT_SCHEMA;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(
        RegistryFriendlyByteBuf buffer,
        DungeonPreparationLoadoutSnapshotPayload payload
    ) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeUUID(payload.targetId);
        buffer.writeUtf(payload.targetName, 32);
        buffer.writeEnum(payload.targetKind);
        buffer.writeBoolean(payload.editable);
        buffer.writeVarLong(payload.revision);
        buffer.writeVarInt(payload.selections.size());
        for (Selection selection : payload.selections) {
            buffer.writeEnum(selection.category());
            buffer.writeVarInt(selection.inventorySlot());
            buffer.writeVarInt(selection.quantity());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, selection.stack());
            buffer.writeBoolean(selection.valid());
        }
        buffer.writeVarInt(payload.equipment.size());
        for (Equipment equipment : payload.equipment) {
            buffer.writeEnum(equipment.category());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, equipment.stack());
        }
        buffer.writeVarInt(payload.inventoryCandidates.size());
        for (InventoryCandidate candidate : payload.inventoryCandidates) {
            buffer.writeVarInt(candidate.inventorySlot());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, candidate.stack());
        }
        buffer.writeVarInt(payload.reservedInventorySlots.size());
        payload.reservedInventorySlots.forEach(buffer::writeVarInt);
        buffer.writeVarInt(payload.currentAbilities.size());
        for (AbilitySlot ability : payload.currentAbilities) {
            buffer.writeUtf(ability.id(), 128);
            buffer.writeUtf(ability.name(), 128);
            buffer.writeUtf(ability.icon(), 256);
            writeSkillDetails(buffer, ability.details());
        }
        buffer.writeVarInt(payload.learnedSkills.size());
        for (SkillIcon skill : payload.learnedSkills) {
            buffer.writeUtf(skill.id(), 128);
            buffer.writeUtf(skill.name(), 128);
            buffer.writeUtf(skill.icon(), 256);
            buffer.writeBoolean(skill.passive());
            writeSkillDetails(buffer, skill.details());
        }
        buffer.writeVarInt(payload.selectedPassiveSkills.size());
        for (SkillIcon skill : payload.selectedPassiveSkills) {
            buffer.writeUtf(skill.id(), 128);
            buffer.writeUtf(skill.name(), 128);
            buffer.writeUtf(skill.icon(), 256);
            buffer.writeBoolean(skill.passive());
            writeSkillDetails(buffer, skill.details());
        }
        buffer.writeBoolean(payload.skillsAvailable);
        buffer.writeUtf(payload.message, 160);
        buffer.writeDouble(payload.currentMagicule);
        buffer.writeDouble(payload.maxMagicule);
        buffer.writeBoolean(payload.magiculeAvailable);
    }

    private static DungeonPreparationLoadoutSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        int schemaVersion = buffer.readVarInt();
        UUID targetId = buffer.readUUID();
        String targetName = buffer.readUtf(32);
        TargetKind targetKind = buffer.readEnum(TargetKind.class);
        boolean editable = buffer.readBoolean();
        long revision = buffer.readVarLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_SELECTIONS) {
            throw new IllegalArgumentException("Invalid preparation loadout selection count: " + count);
        }
        List<Selection> selections = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            selections.add(new Selection(
                buffer.readEnum(DungeonPreparationLoadoutData.Category.class),
                buffer.readVarInt(),
                buffer.readVarInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                buffer.readBoolean()
            ));
        }
        int equipmentCount = buffer.readVarInt();
        if (equipmentCount < 0 || equipmentCount > MAX_EQUIPMENT) {
            throw new IllegalArgumentException(
                "Invalid preparation equipment count: " + equipmentCount
            );
        }
        List<Equipment> equipment = new ArrayList<>(equipmentCount);
        for (int index = 0; index < equipmentCount; index++) {
            equipment.add(new Equipment(
                buffer.readEnum(DungeonPreparationLoadoutData.Category.class),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
            ));
        }
        int inventoryCount = buffer.readVarInt();
        if (inventoryCount < 0 || inventoryCount > MAX_INVENTORY_CANDIDATES) {
            throw new IllegalArgumentException(
                "Invalid preparation inventory projection count: " + inventoryCount
            );
        }
        List<InventoryCandidate> inventoryCandidates = new ArrayList<>(inventoryCount);
        for (int index = 0; index < inventoryCount; index++) {
            inventoryCandidates.add(new InventoryCandidate(
                buffer.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
            ));
        }
        int reservedCount = buffer.readVarInt();
        if (reservedCount < 0 || reservedCount > MAX_RESERVED_SLOTS) {
            throw new IllegalArgumentException(
                "Invalid preparation reserved-slot count: " + reservedCount
            );
        }
        List<Integer> reservedSlots = new ArrayList<>(reservedCount);
        for (int index = 0; index < reservedCount; index++) {
            reservedSlots.add(buffer.readVarInt());
        }
        int currentAbilityCount = buffer.readVarInt();
        if (currentAbilityCount < 0 || currentAbilityCount > MAX_CURRENT_ABILITIES) {
            throw new IllegalArgumentException(
                "Invalid preparation current-ability projection count: " + currentAbilityCount
            );
        }
        List<AbilitySlot> currentAbilities = new ArrayList<>(currentAbilityCount);
        for (int index = 0; index < currentAbilityCount; index++) {
            currentAbilities.add(new AbilitySlot(
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(256),
                readSkillDetails(buffer)
            ));
        }
        int learnedSkillCount = buffer.readVarInt();
        if (learnedSkillCount < 0 || learnedSkillCount > MAX_LEARNED_SKILLS) {
            throw new IllegalArgumentException(
                "Invalid preparation learned-skill projection count: " + learnedSkillCount
            );
        }
        List<SkillIcon> learnedSkills = new ArrayList<>(learnedSkillCount);
        for (int index = 0; index < learnedSkillCount; index++) {
            learnedSkills.add(new SkillIcon(
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(256),
                buffer.readBoolean(),
                readSkillDetails(buffer)
            ));
        }
        int selectedPassiveCount = buffer.readVarInt();
        if (selectedPassiveCount < 0 || selectedPassiveCount > MAX_SELECTED_PASSIVE_SKILLS) {
            throw new IllegalArgumentException(
                "Invalid preparation selected-passive projection count: "
                    + selectedPassiveCount
            );
        }
        List<SkillIcon> selectedPassiveSkills = new ArrayList<>(selectedPassiveCount);
        for (int index = 0; index < selectedPassiveCount; index++) {
            selectedPassiveSkills.add(new SkillIcon(
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(256),
                buffer.readBoolean(),
                readSkillDetails(buffer)
            ));
        }
        boolean skillsAvailable = buffer.readBoolean();
        return new DungeonPreparationLoadoutSnapshotPayload(
            schemaVersion,
            targetId,
            targetName,
            targetKind,
            editable,
            revision,
            selections,
            equipment,
            inventoryCandidates,
            reservedSlots,
            currentAbilities,
            learnedSkills,
            selectedPassiveSkills,
            skillsAvailable,
            buffer.readUtf(160),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readBoolean()
        );
    }

    private static void writeSkillDetails(
        RegistryFriendlyByteBuf buffer,
        SkillDetails details
    ) {
        SkillDetails safe = details == null ? SkillDetails.unavailable() : details;
        buffer.writeVarInt(safe.mode());
        buffer.writeDouble(safe.magiculeCost());
        buffer.writeVarInt(safe.cooldownSeconds());
        buffer.writeBoolean(safe.magiculeCostAvailable());
        buffer.writeBoolean(safe.cooldownAvailable());
        buffer.writeBoolean(safe.resourceAvailable());
        buffer.writeBoolean(safe.enoughMagicule());
    }

    private static SkillDetails readSkillDetails(RegistryFriendlyByteBuf buffer) {
        return new SkillDetails(
            buffer.readVarInt(),
            buffer.readDouble(),
            buffer.readVarInt(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean()
        );
    }

    private static String bounded(String value, int maximumLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
    }

    public record Selection(
        DungeonPreparationLoadoutData.Category category,
        int inventorySlot,
        int quantity,
        ItemStack stack,
        boolean valid
    ) {
        public Selection {
            category = Objects.requireNonNull(category, "Preparation loadout category");
            if (inventorySlot < 0 || quantity <= 0) {
                throw new IllegalArgumentException("Preparation loadout selection is incomplete");
            }
            stack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
    }

    public record Equipment(
        DungeonPreparationLoadoutData.Category category,
        ItemStack stack
    ) {
        public Equipment {
            category = Objects.requireNonNull(category, "Preparation equipment category");
            stack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
    }

    public record InventoryCandidate(int inventorySlot, ItemStack stack) {
        public InventoryCandidate {
            if (inventorySlot < 0) {
                throw new IllegalArgumentException("Preparation inventory slot cannot be negative");
            }
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }
    }

    public record AbilitySlot(String id, String name, String icon, SkillDetails details) {
        public AbilitySlot(String id, String name, String icon) {
            this(id, name, icon, SkillDetails.unavailable());
        }

        public AbilitySlot {
            id = bounded(id, 128);
            name = bounded(name, 128);
            icon = bounded(icon, 256);
            details = details == null ? SkillDetails.unavailable() : details;
        }

        public boolean empty() {
            return id.isBlank();
        }
    }

    public record SkillIcon(
        String id,
        String name,
        String icon,
        boolean passive,
        SkillDetails details
    ) {
        public SkillIcon(String id, String icon) {
            this(id, prettifyId(id), icon, false, SkillDetails.unavailable());
        }

        public SkillIcon(String id, String name, String icon, boolean passive) {
            this(id, name, icon, passive, SkillDetails.unavailable());
        }

        public SkillIcon {
            id = bounded(id, 128);
            name = bounded(name, 128);
            icon = bounded(icon, 256);
            details = details == null ? SkillDetails.unavailable() : details;
            if (id.isBlank()) {
                throw new IllegalArgumentException("Preparation learned-skill ID cannot be blank");
            }
        }

        private static String prettifyId(String id) {
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
                result.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1));
            }
            return result.isEmpty() ? "Unknown Ability" : result.toString();
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

        public static SkillDetails unavailable() {
            return new SkillDetails(0, 0.0D, 0, false, false, false, true);
        }

        public boolean available() {
            return magiculeCostAvailable || cooldownAvailable;
        }

        public boolean warning() {
            return magiculeCostAvailable && resourceAvailable && !enoughMagicule;
        }
    }

    public enum TargetKind {
        PLAYER,
        SUBORDINATE
    }
}
