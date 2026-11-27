package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.compat.tensura.TensuraSubordinateSkillCompat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonPreparationLoadoutData extends SavedData {
    static final String DATA_NAME = "tensura_dungeon_preparation_loadouts";
    private static final String PLAYERS_KEY = "Players";
    private static final String PLAYER_KEY = "Player";
    private static final String INVENTORY_OWNER_KEY = "InventoryOwner";
    private static final String INITIALIZED_KEY = "Initialized";
    private static final String EQUIPMENT_DEFAULTS_VERSION_KEY = "EquipmentDefaultsVersion";
    private static final int CURRENT_EQUIPMENT_DEFAULTS_VERSION = 1;
    private static final String REVISION_KEY = "Revision";
    private static final String SELECTIONS_KEY = "Selections";
    private static final String CATEGORY_KEY = "Category";
    private static final String SLOT_KEY = "Slot";
    private static final String QUANTITY_KEY = "Quantity";
    private static final String STACK_KEY = "Stack";
    private static final String PASSIVE_SKILLS_KEY = "PassiveSkills";
    private static final int MAX_PASSIVE_SKILLS = 2;
    private static final String VOLATILE_EXISTENCE_DURABILITY_COMPONENT =
        "tensura:existence_durability";
    private static final int SHARED_PLAYER_INVENTORY_SLOT_BASE = 1_000_000;

    private final Map<UUID, EnumMap<Category, List<ItemReference>>> selectionsByPlayer =
        new HashMap<>();
    private final Map<UUID, Long> revisionsByPlayer = new HashMap<>();
    private final Map<UUID, UUID> inventoryOwnersByLoadout = new HashMap<>();
    private final Map<UUID, List<String>> passiveSkillsByLoadout = new HashMap<>();
    private final Set<UUID> initializedPlayers = new HashSet<>();
    private final Set<UUID> equipmentDefaultsHydratedPlayers = new HashSet<>();

    public static DungeonPreparationLoadoutData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(DungeonPreparationLoadoutData::new, DungeonPreparationLoadoutData::load),
            DATA_NAME
        );
    }

    public static DungeonPreparationLoadoutData load(
        CompoundTag tag,
        HolderLookup.Provider registries
    ) {
        DungeonPreparationLoadoutData data = new DungeonPreparationLoadoutData();
        ListTag players = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            CompoundTag playerTag = players.getCompound(playerIndex);
            if (!playerTag.hasUUID(PLAYER_KEY)) {
                continue;
            }
            UUID playerId = playerTag.getUUID(PLAYER_KEY);
            data.inventoryOwnersByLoadout.put(
                playerId,
                playerTag.hasUUID(INVENTORY_OWNER_KEY)
                    ? playerTag.getUUID(INVENTORY_OWNER_KEY)
                    : playerId
            );
            if (playerTag.getBoolean(INITIALIZED_KEY)) {
                data.initializedPlayers.add(playerId);
            }
            if (playerTag.getInt(EQUIPMENT_DEFAULTS_VERSION_KEY)
                >= CURRENT_EQUIPMENT_DEFAULTS_VERSION) {
                data.equipmentDefaultsHydratedPlayers.add(playerId);
            }
            data.revisionsByPlayer.put(playerId, Math.max(0L, playerTag.getLong(REVISION_KEY)));
            EnumMap<Category, List<ItemReference>> categories = new EnumMap<>(Category.class);
            ListTag selections = playerTag.getList(SELECTIONS_KEY, Tag.TAG_COMPOUND);
            for (int selectionIndex = 0; selectionIndex < selections.size(); selectionIndex++) {
                CompoundTag selectionTag = selections.getCompound(selectionIndex);
                Optional<Category> category = Category.parse(selectionTag.getString(CATEGORY_KEY));
                if (category.isEmpty()) {
                    continue;
                }
                int slot = selectionTag.getInt(SLOT_KEY);
                int quantity = selectionTag.getInt(QUANTITY_KEY);
                ItemStack stack = ItemStack.parse(
                    registries,
                    selectionTag.getCompound(STACK_KEY)
                ).orElse(ItemStack.EMPTY);
                if (slot < 0 || quantity <= 0 || stack.isEmpty()) {
                    continue;
                }
                stack = stack.copyWithCount(1);
                categories.computeIfAbsent(category.get(), ignored -> new ArrayList<>())
                    .add(new ItemReference(slot, quantity, stack));
            }
            normalize(categories);
            normalizeExclusiveInventorySlots(categories);
            if (!categories.isEmpty()) {
                data.selectionsByPlayer.put(playerId, categories);
            }
            ListTag passiveSkills = playerTag.getList(PASSIVE_SKILLS_KEY, Tag.TAG_STRING);
            List<String> passiveIds = new ArrayList<>();
            for (int passiveIndex = 0; passiveIndex < passiveSkills.size(); passiveIndex++) {
                String skillId = passiveSkills.getString(passiveIndex);
                String normalizedSkillId = normalizedSkillId(skillId);
                if (!normalizedSkillId.isBlank()
                    && !passiveIds.contains(normalizedSkillId)
                    && passiveIds.size() < MAX_PASSIVE_SKILLS) {
                    passiveIds.add(normalizedSkillId);
                }
            }
            if (!passiveIds.isEmpty()) {
                data.passiveSkillsByLoadout.put(playerId, List.copyOf(passiveIds));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Set<UUID> playerIds = new HashSet<>(initializedPlayers);
        playerIds.addAll(equipmentDefaultsHydratedPlayers);
        playerIds.addAll(selectionsByPlayer.keySet());
        playerIds.addAll(revisionsByPlayer.keySet());
        playerIds.addAll(inventoryOwnersByLoadout.keySet());
        playerIds.addAll(passiveSkillsByLoadout.keySet());
        ListTag players = new ListTag();
        playerIds.stream().sorted(Comparator.comparing(UUID::toString)).forEach(playerId -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(PLAYER_KEY, playerId);
            playerTag.putUUID(
                INVENTORY_OWNER_KEY,
                inventoryOwnersByLoadout.getOrDefault(playerId, playerId)
            );
            playerTag.putBoolean(INITIALIZED_KEY, initializedPlayers.contains(playerId));
            playerTag.putInt(
                EQUIPMENT_DEFAULTS_VERSION_KEY,
                equipmentDefaultsHydratedPlayers.contains(playerId)
                    ? CURRENT_EQUIPMENT_DEFAULTS_VERSION
                    : 0
            );
            playerTag.putLong(REVISION_KEY, revision(playerId));
            ListTag selections = new ListTag();
            EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(playerId);
            if (categories != null) {
                for (Category category : Category.values()) {
                    for (ItemReference reference : categories.getOrDefault(category, List.of())) {
                        CompoundTag selectionTag = new CompoundTag();
                        selectionTag.putString(CATEGORY_KEY, category.name());
                        selectionTag.putInt(SLOT_KEY, reference.inventorySlot());
                        selectionTag.putInt(QUANTITY_KEY, reference.quantity());
                        selectionTag.put(STACK_KEY, reference.expectedStack().save(registries));
                        selections.add(selectionTag);
                    }
                }
            }
            playerTag.put(SELECTIONS_KEY, selections);
            ListTag passiveSkills = new ListTag();
            for (String skillId : passiveSkillsByLoadout.getOrDefault(playerId, List.of())) {
                passiveSkills.add(net.minecraft.nbt.StringTag.valueOf(skillId));
            }
            playerTag.put(PASSIVE_SKILLS_KEY, passiveSkills);
            players.add(playerTag);
        });
        tag.put(PLAYERS_KEY, players);
        return tag;
    }

    public LoadoutSnapshot snapshot(ServerPlayer player) {
        boolean frozen = isFrozenLoadout(player.getUUID(), player);
        if (!frozen) {
            ensureDefaults(player);
            refreshEquipmentRuntimeComponents(player.getUUID(), player);
            synchronizeFixedEquipmentSelections(player.getUUID(), player);
        }
        return snapshot(player.getUUID(), player);
    }

    public LoadoutSnapshot snapshot(UUID loadoutId, ServerPlayer inventoryOwner) {
        if (loadoutId == null || inventoryOwner == null) {
            return new LoadoutSnapshot(0L, Map.of());
        }
        boolean frozen = isFrozenLoadout(loadoutId, inventoryOwner);
        if (!frozen) {
            bindInventoryOwner(loadoutId, inventoryOwner.getUUID());
            reconcileMovedSelections(loadoutId, inventoryOwner);
        }
        return snapshotFromInventory(loadoutId, inventoryOwner.getInventory(), frozen);
    }

    public LoadoutSnapshot snapshotSubordinate(
        UUID loadoutId,
        UUID hostId,
        ServerLevel level,
        Container inventory
    ) {
        if (loadoutId == null || hostId == null || level == null || inventory == null) {
            return new LoadoutSnapshot(0L, Map.of());
        }
        boolean frozen = isFrozenLoadout(loadoutId, hostId, level);
        ServerPlayer host = level.getServer().getPlayerList().getPlayer(hostId);
        Container hostInventory = host == null ? null : host.getInventory();
        Set<UUID> partyLoadoutIds = partyLoadoutIds(level, hostId);
        if (!frozen) {
            bindInventoryOwner(loadoutId, loadoutId);
            reconcileSubordinateProtectionSelections(
                loadoutId,
                hostId,
                inventory,
                hostInventory,
                partyLoadoutIds
            );
        }
        return snapshotFromProtectionInventories(
            loadoutId,
            hostId,
            inventory,
            hostInventory,
            frozen
        );
    }

    public static int sharedPlayerInventorySlot(int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= SHARED_PLAYER_INVENTORY_SLOT_BASE) {
            throw new IllegalArgumentException("Shared player inventory slot is outside its bound");
        }
        return SHARED_PLAYER_INVENTORY_SLOT_BASE + inventorySlot;
    }

    public static boolean isSharedPlayerInventorySlot(int inventorySlot) {
        return inventorySlot >= SHARED_PLAYER_INVENTORY_SLOT_BASE;
    }

    public static int decodeSharedPlayerInventorySlot(int inventorySlot) {
        if (!isSharedPlayerInventorySlot(inventorySlot)) {
            throw new IllegalArgumentException("Slot is not a shared player inventory slot");
        }
        return inventorySlot - SHARED_PLAYER_INVENTORY_SLOT_BASE;
    }

    private LoadoutSnapshot snapshotFromInventory(
        UUID loadoutId,
        Container inventory,
        boolean frozen
    ) {
        EnumMap<Category, List<SelectionView>> views = new EnumMap<>(Category.class);
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        Map<SelectionSlot, Integer> reservedQuantities = new HashMap<>();
        Map<SelectionSlot, ItemStack> expectedStacks = new HashMap<>();
        Set<SelectionSlot> inconsistentSlots = new HashSet<>();
        if (categories != null) {
            for (Category category : Category.values()) {
                for (ItemReference reference : categories.getOrDefault(category, List.of())) {
                    SelectionSlot key = new SelectionSlot(category, reference.inventorySlot());
                    reservedQuantities.merge(key, reference.quantity(), Integer::sum);
                    ItemStack previous = expectedStacks.putIfAbsent(
                        key,
                        reference.expectedStack()
                    );
                    if (previous != null
                        && !ItemStack.isSameItemSameComponents(
                            previous,
                            reference.expectedStack()
                        )) {
                        inconsistentSlots.add(key);
                    }
                }
            }
        }
        for (Category category : Category.values()) {
            List<SelectionView> categoryViews = new ArrayList<>();
            if (categories != null) {
                for (ItemReference reference : categories.getOrDefault(category, List.of())) {
                    ItemStack current = inventoryStack(inventory, reference.inventorySlot());
                    SelectionSlot key = new SelectionSlot(category, reference.inventorySlot());
                    int requiredQuantity = reservedQuantities.getOrDefault(
                        key,
                        reference.quantity()
                    );
                    ItemStack expected = expectedStacks.getOrDefault(
                        key,
                        reference.expectedStack()
                    );
                    boolean valid = !inconsistentSlots.contains(key)
                        && current != null
                        && !current.isEmpty()
                        && current.getCount() >= requiredQuantity
                        && (ItemStack.isSameItemSameComponents(current, expected)
                            || (frozen
                                && isPlayerCombatEquipment(category)
                                && sameEquipmentExceptVolatileRuntimeComponents(
                                    current, expected
                                )))
                        && category.eligible(current)
                        && reference.quantity() <= category.maxQuantityFromInventoryStack();
                    categoryViews.add(new SelectionView(
                        category,
                        reference.inventorySlot(),
                        reference.quantity(),
                        valid ? current.copyWithCount(1) : reference.expectedStack().copy(),
                        valid
                    ));
                }
            }
            views.put(category, List.copyOf(categoryViews));
        }
        return new LoadoutSnapshot(revision(loadoutId), views);
    }

    private LoadoutSnapshot snapshotFromProtectionInventories(
        UUID loadoutId,
        UUID hostId,
        Container subordinateInventory,
        Container hostInventory,
        boolean frozen
    ) {
        EnumMap<Category, List<SelectionView>> views = new EnumMap<>(Category.class);
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        if (categories != null) {
            for (Category category : Category.values()) {
                List<SelectionView> categoryViews = new ArrayList<>();
                for (ItemReference reference : categories.getOrDefault(category, List.of())) {
                    ItemStack current = protectionStack(
                        reference,
                        subordinateInventory,
                        hostInventory
                    );
                    boolean valid = current != null
                        && !current.isEmpty()
                        && current.getCount() >= reference.quantity()
                        && reference.matches(current)
                        && category.eligible(current)
                        && reference.quantity() <= category.maxQuantityFromInventoryStack();
                    categoryViews.add(new SelectionView(
                        category,
                        reference.inventorySlot(),
                        reference.quantity(),
                        valid ? current.copyWithCount(1) : reference.expectedStack().copy(),
                        valid
                    ));
                }
                views.put(category, List.copyOf(categoryViews));
            }
        } else {
            for (Category category : Category.values()) {
                views.put(category, List.of());
            }
        }
        return new LoadoutSnapshot(revision(loadoutId), views);
    }

    private static Set<UUID> partyLoadoutIds(ServerLevel level, UUID hostId) {
        if (level == null || hostId == null) {
            return hostId == null ? Set.of() : Set.of(hostId);
        }
        return DungeonPreparationPartyData.get(level)
            .partyFor(hostId)
            .map(party -> party.members().stream()
                .map(DungeonPreparationPartyData.MemberRecord::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()))
            .orElse(Set.of(hostId));
    }

    private static boolean isFrozenLoadout(UUID loadoutId, ServerPlayer inventoryOwner) {
        return isFrozenLoadout(
            loadoutId, inventoryOwner.getUUID(), inventoryOwner.serverLevel()
        );
    }

    private static boolean isFrozenLoadout(
        UUID loadoutId,
        UUID hostId,
        ServerLevel level
    ) {
        DungeonInstanceAllocationData.Allocation allocation =
            DungeonInstanceAllocationData.get(level)
                .forPlayer(hostId)
                .orElse(null);
        return allocation != null
            && allocation.soloPreparation() != null
            && allocation.lifecycle().state()
                != DungeonInstanceAllocationData.LifecycleState.ALLOCATED
            && !allocation.lifecycle().state().reusable()
            && allocation.soloPreparation().frozenParticipants().stream()
                .anyMatch(participant -> participant.id().equals(loadoutId)
                    && participant.ownerId().equals(hostId));
    }

    private void reconcileMovedSelections(UUID loadoutId, ServerPlayer inventoryOwner) {
        refreshEquipmentRuntimeComponents(loadoutId, inventoryOwner);
        reconcileMovedSelections(
            loadoutId,
            inventoryOwner.getUUID(),
            inventoryOwner.getInventory(),
            partyLoadoutIds(inventoryOwner.serverLevel(), inventoryOwner.getUUID())
        );
    }

    private void reconcileMovedSelections(
        UUID loadoutId,
        UUID inventoryOwnerId,
        Container inventoryOwner,
        Set<UUID> partyLoadoutIds
    ) {
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        if (categories == null || categories.isEmpty()) {
            return;
        }
        List<ItemStack> inventory = new ArrayList<>(inventoryOwner.getContainerSize());
        for (int slot = 0; slot < inventoryOwner.getContainerSize(); slot++) {
            inventory.add(inventoryOwner.getItem(slot));
        }
        boolean changed = reconcileMovedSelections(
            categories,
            inventory,
            reservedInventorySlotsForParty(
                inventoryOwnerId,
                loadoutId,
                partyLoadoutIds
            )
        );
        if (changed) {
            advanceRevision(loadoutId);
        }
    }

    static boolean reconcileMovedSelections(
        EnumMap<Category, List<ItemReference>> categories,
        List<ItemStack> inventory,
        Set<Integer> reservedInventorySlots
    ) {
        if (categories == null || categories.isEmpty() || inventory == null) {
            return false;
        }
        Set<Integer> reserved = reservedInventorySlots == null
            ? Set.of()
            : reservedInventorySlots;
        List<SelectionEntry> entries = new ArrayList<>();
        List<PendingSelection> pending = new ArrayList<>();
        Map<Integer, Category> occupiedCategories = new HashMap<>();
        Map<Integer, Integer> occupiedQuantities = new HashMap<>();
        Map<Integer, ItemReference> resolved = new HashMap<>();

        for (Category category : Category.values()) {
            for (ItemReference reference : categories.getOrDefault(category, List.of())) {
                int entryIndex = entries.size();
                entries.add(new SelectionEntry(category, reference));
                ItemStack current = inventoryStack(inventory, reference.inventorySlot());
                if (!reserved.contains(reference.inventorySlot())
                    && canUseSelectionSlot(
                        category,
                        reference,
                        current,
                        occupiedCategories,
                        occupiedQuantities
                    )) {
                    resolved.put(entryIndex, reference);
                    registerSelectionSlot(
                        category,
                        reference,
                        occupiedCategories,
                        occupiedQuantities
                    );
                } else {
                    pending.add(new PendingSelection(entryIndex, category, reference));
                }
            }
        }

        List<List<PendingSelection>> pendingGroups = new ArrayList<>();
        for (PendingSelection selection : pending) {
            List<PendingSelection> group = pendingGroups.stream()
                .filter(existing -> samePendingGroup(existing.getFirst(), selection))
                .findFirst()
                .orElse(null);
            if (group == null) {
                pendingGroups.add(new ArrayList<>(List.of(selection)));
            } else {
                group.add(selection);
            }
        }

        List<List<Integer>> candidates = new ArrayList<>();
        for (List<PendingSelection> group : pendingGroups) {
            PendingSelection selection = group.getFirst();
            List<Integer> matchingSlots = new ArrayList<>();
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (reserved.contains(slot) || occupiedCategories.containsKey(slot)) {
                    continue;
                }
                ItemStack current = inventoryStack(inventory, slot);
                if (validSelection(
                    selection.category(),
                    selection.reference(),
                    current
                ) && current.getCount() >= pendingGroupQuantity(group)) {
                    matchingSlots.add(slot);
                }
            }
            matchingSlots.sort(Comparator.<Integer>comparingInt(slot ->
                slot == selection.reference().inventorySlot() ? 0 : 1
            ).thenComparingInt(Integer::intValue));
            candidates.add(List.copyOf(matchingSlots));
        }

        Map<Integer, Integer> slotToPending = new HashMap<>();
        for (int pendingIndex = 0; pendingIndex < pendingGroups.size(); pendingIndex++) {
            assignPendingSelection(
                pendingIndex,
                candidates,
                slotToPending,
                new HashSet<>()
            );
        }
        for (Map.Entry<Integer, Integer> assignment : slotToPending.entrySet()) {
            List<PendingSelection> group = pendingGroups.get(assignment.getValue());
            ItemStack current = inventoryStack(inventory, assignment.getKey());
            for (PendingSelection selection : group) {
                resolved.put(
                    selection.entryIndex(),
                    new ItemReference(
                        assignment.getKey(),
                        selection.reference().quantity(),
                        current.copyWithCount(1)
                    )
                );
            }
        }

        EnumMap<Category, List<ItemReference>> rebuilt = new EnumMap<>(Category.class);
        boolean changed = false;
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            SelectionEntry entry = entries.get(entryIndex);
            ItemReference replacement = resolved.get(entryIndex);
            if (replacement == null) {
                changed = true;
                continue;
            }
            changed |= replacement.inventorySlot() != entry.reference().inventorySlot();
            rebuilt.computeIfAbsent(entry.category(), ignored -> new ArrayList<>()).add(replacement);
        }
        if (!changed) {
            return false;
        }
        for (Category category : Category.values()) {
            List<ItemReference> references = rebuilt.getOrDefault(category, List.of());
            if (references.isEmpty()) {
                categories.remove(category);
            } else {
                categories.put(category, List.copyOf(references));
            }
        }
        return true;
    }

    private static boolean assignPendingSelection(
        int pendingIndex,
        List<List<Integer>> candidates,
        Map<Integer, Integer> slotToPending,
        Set<Integer> visitedSlots
    ) {
        for (int slot : candidates.get(pendingIndex)) {
            if (!visitedSlots.add(slot)) {
                continue;
            }
            Integer previous = slotToPending.get(slot);
            if (previous == null
                || assignPendingSelection(previous, candidates, slotToPending, visitedSlots)) {
                slotToPending.put(slot, pendingIndex);
                return true;
            }
        }
        return false;
    }

    private static boolean validSelection(
        Category category,
        ItemReference reference,
        ItemStack current
    ) {
        return reference.matches(current) && category.eligible(current);
    }

    private static boolean canUseSelectionSlot(
        Category category,
        ItemReference reference,
        ItemStack current,
        Map<Integer, Category> occupiedCategories,
        Map<Integer, Integer> occupiedQuantities
    ) {
        if (!validSelection(category, reference, current)) {
            return false;
        }
        Category occupiedCategory = occupiedCategories.get(reference.inventorySlot());
        if (occupiedCategory != null && occupiedCategory != category) {
            return false;
        }
        int occupiedQuantity = occupiedQuantities.getOrDefault(reference.inventorySlot(), 0);
        return current.getCount() >= occupiedQuantity + reference.quantity();
    }

    private static void registerSelectionSlot(
        Category category,
        ItemReference reference,
        Map<Integer, Category> occupiedCategories,
        Map<Integer, Integer> occupiedQuantities
    ) {
        occupiedCategories.putIfAbsent(reference.inventorySlot(), category);
        occupiedQuantities.merge(
            reference.inventorySlot(),
            reference.quantity(),
            Integer::sum
        );
    }

    private static boolean samePendingGroup(
        PendingSelection first,
        PendingSelection second
    ) {
        return first.category() == second.category()
            && first.reference().inventorySlot() == second.reference().inventorySlot()
            && ItemStack.isSameItemSameComponents(
                first.reference().expectedStack(),
                second.reference().expectedStack()
            );
    }

    private static int pendingGroupQuantity(List<PendingSelection> group) {
        return group.stream()
            .mapToInt(selection -> selection.reference().quantity())
            .sum();
    }

    private static ItemStack inventoryStack(List<ItemStack> inventory, int inventorySlot) {
        return inventorySlot < 0 || inventorySlot >= inventory.size()
            ? ItemStack.EMPTY
            : inventory.get(inventorySlot);
    }

    public UUID inventoryOwner(UUID loadoutId) {
        return loadoutId == null
            ? null
            : inventoryOwnersByLoadout.getOrDefault(loadoutId, loadoutId);
    }

    public Optional<BraceletReservation> validateBraceletReservation(
        UUID loadoutId,
        ServerPlayer inventoryOwner,
        long acceptedRevision,
        int floor
    ) {
        return inventoryOwner == null ? Optional.empty() : validateBraceletReservation(
            loadoutId, inventoryOwner.getUUID(), inventoryOwner.getInventory(),
            acceptedRevision, floor
        );
    }

    public Optional<BraceletReservation> validateBraceletReservation(
        UUID loadoutId,
        UUID inventoryOwnerId,
        Container inventory,
        long acceptedRevision,
        int floor
    ) {
        if (loadoutId == null || inventoryOwnerId == null || inventory == null
            || acceptedRevision < 0L || floor < 1
            || !inventoryOwnerId.equals(inventoryOwner(loadoutId))
            || revision(loadoutId) != acceptedRevision) {
            return Optional.empty();
        }
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        List<ItemReference> references = categories == null
            ? List.of()
            : categories.getOrDefault(Category.RESURRECTION_BRACELET, List.of());
        if (references.size() != 1) {
            return Optional.empty();
        }
        ItemReference reference = references.getFirst();
        ItemStack current = inventoryStack(inventory, reference.inventorySlot());
        if (reference.quantity() != 1 || !reference.matches(current)
            || !Category.RESURRECTION_BRACELET.eligible(current)
            || !braceletProtectsFloor(current, floor)) {
            return Optional.empty();
        }
        return Optional.of(new BraceletReservation(
            loadoutId,
            inventoryOwnerId,
            acceptedRevision,
            reference.inventorySlot(),
            floor,
            reference.expectedStack()
        ));
    }

    public Optional<BraceletReservation> validateSubordinateBraceletReservation(
        UUID loadoutId,
        UUID hostId,
        Container subordinateInventory,
        Container hostInventory,
        long acceptedRevision,
        int floor
    ) {
        return validateSubordinateProtectionReference(
            loadoutId,
            hostId,
            subordinateInventory,
            hostInventory,
            acceptedRevision,
            floor,
            Category.RESURRECTION_BRACELET
        ).map(reference -> new BraceletReservation(
            loadoutId,
            reference.ownerId(),
            acceptedRevision,
            reference.inventorySlot(),
            floor,
            reference.expectedStack()
        ));
    }

    public boolean consumeBraceletReservation(
        BraceletReservation reservation,
        ServerPlayer inventoryOwner
    ) {
        return consumeTerminalReservations(
            reservation == null ? List.of() : List.of(reservation),
            List.of(),
            inventoryOwner
        );
    }

    public Optional<WhistleReservation> validateWhistleReservation(
        UUID loadoutId,
        ServerPlayer inventoryOwner,
        long acceptedRevision,
        int floor
    ) {
        return inventoryOwner == null ? Optional.empty() : validateWhistleReservation(
            loadoutId, inventoryOwner.getUUID(), inventoryOwner.getInventory(),
            acceptedRevision, floor
        );
    }

    public Optional<WhistleReservation> validateWhistleReservation(
        UUID loadoutId,
        UUID inventoryOwnerId,
        Container inventory,
        long acceptedRevision,
        int floor
    ) {
        if (loadoutId == null || inventoryOwnerId == null || inventory == null
            || acceptedRevision < 0L || floor < 1
            || !inventoryOwnerId.equals(inventoryOwner(loadoutId))
            || revision(loadoutId) != acceptedRevision) {
            return Optional.empty();
        }
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        List<ItemReference> references = categories == null
            ? List.of()
            : categories.getOrDefault(Category.RETURN_WHISTLE, List.of());
        if (references.size() != 1) {
            return Optional.empty();
        }
        ItemReference reference = references.getFirst();
        ItemStack current = inventoryStack(inventory, reference.inventorySlot());
        if (reference.quantity() != 1 || !reference.matches(current)
            || !Category.RETURN_WHISTLE.eligible(current)
            || !whistleProtectsFloor(current, floor)) {
            return Optional.empty();
        }
        return Optional.of(new WhistleReservation(
            loadoutId,
            inventoryOwnerId,
            acceptedRevision,
            reference.inventorySlot(),
            floor,
            reference.expectedStack()
        ));
    }

    public Optional<WhistleReservation> validateSubordinateWhistleReservation(
        UUID loadoutId,
        UUID hostId,
        Container subordinateInventory,
        Container hostInventory,
        long acceptedRevision,
        int floor
    ) {
        return validateSubordinateProtectionReference(
            loadoutId,
            hostId,
            subordinateInventory,
            hostInventory,
            acceptedRevision,
            floor,
            Category.RETURN_WHISTLE
        ).map(reference -> new WhistleReservation(
            loadoutId,
            reference.ownerId(),
            acceptedRevision,
            reference.inventorySlot(),
            floor,
            reference.expectedStack()
        ));
    }

    private Optional<ResolvedProtectionReference> validateSubordinateProtectionReference(
        UUID loadoutId,
        UUID hostId,
        Container subordinateInventory,
        Container hostInventory,
        long acceptedRevision,
        int floor,
        Category category
    ) {
        if (loadoutId == null || hostId == null || subordinateInventory == null
            || acceptedRevision < 0L || floor < 1
            || !loadoutId.equals(inventoryOwner(loadoutId))
            || revision(loadoutId) != acceptedRevision) {
            return Optional.empty();
        }
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        List<ItemReference> references = categories == null
            ? List.of()
            : categories.getOrDefault(category, List.of());
        if (references.size() != 1) {
            return Optional.empty();
        }
        ItemReference reference = references.getFirst();
        ResolvedProtectionReference resolved = resolveProtectionReference(
            loadoutId,
            hostId,
            reference,
            subordinateInventory,
            hostInventory
        );
        if (resolved == null) {
            return Optional.empty();
        }
        ItemStack current = resolved.current();
        boolean protects = category == Category.RESURRECTION_BRACELET
            ? braceletProtectsFloor(current, floor)
            : whistleProtectsFloor(current, floor);
        return reference.quantity() == 1
            && reference.matches(current)
            && category.eligible(current)
            && protects
            ? Optional.of(resolved)
            : Optional.empty();
    }

    public boolean consumeWhistleReservation(
        WhistleReservation reservation,
        ServerPlayer inventoryOwner
    ) {
        return consumeTerminalReservations(
            List.of(),
            reservation == null ? List.of() : List.of(reservation),
            inventoryOwner
        );
    }

    public boolean consumeTerminalReservations(
        List<BraceletReservation> bracelets,
        List<WhistleReservation> whistles,
        ServerPlayer inventoryOwner
    ) {
        if (inventoryOwner == null) {
            return false;
        }
        return consumeTerminalReservations(
            bracelets,
            whistles,
            Map.of(inventoryOwner.getUUID(), inventoryOwner.getInventory())
        );
    }

    public boolean consumeTerminalReservations(
        List<BraceletReservation> bracelets,
        List<WhistleReservation> whistles,
        Map<UUID, Container> inventories
    ) {
        if (inventories == null || bracelets == null || whistles == null
            || bracelets.isEmpty() && whistles.isEmpty()) {
            return false;
        }
        Set<ReservationKey> reservationKeys = new HashSet<>();
        Map<OwnedInventorySlot, Integer> ownedQuantities = new HashMap<>();
        Map<OwnedInventorySlot, ItemStack> ownedExpected = new HashMap<>();
        for (BraceletReservation reservation : bracelets) {
            if (reservation == null
                || !inventories.containsKey(reservation.inventoryOwnerId())
                || !reservationKeys.add(new ReservationKey(
                    reservation.loadoutId(), Category.RESURRECTION_BRACELET
                ))) {
                return false;
            }
            if (!terminalReservationValid(
                reservation.loadoutId(),
                reservation.inventoryOwnerId(),
                reservation.inventorySlot(),
                reservation.expectedStack(),
                reservation.acceptedRevision(),
                reservation.floor(),
                Category.RESURRECTION_BRACELET,
                inventories.get(reservation.inventoryOwnerId())
            ) || !registerTerminalConsumption(
                    ownedQuantities, ownedExpected, reservation.inventoryOwnerId(),
                    reservation.inventorySlot(),
                    reservation.expectedStack()
                )) {
                return false;
            }
        }
        for (WhistleReservation reservation : whistles) {
            if (reservation == null
                || !inventories.containsKey(reservation.inventoryOwnerId())
                || !reservationKeys.add(new ReservationKey(
                    reservation.loadoutId(), Category.RETURN_WHISTLE
                ))) {
                return false;
            }
            if (!terminalReservationValid(
                reservation.loadoutId(),
                reservation.inventoryOwnerId(),
                reservation.inventorySlot(),
                reservation.expectedStack(),
                reservation.acceptedRevision(),
                reservation.floor(),
                Category.RETURN_WHISTLE,
                inventories.get(reservation.inventoryOwnerId())
            ) || !registerTerminalConsumption(
                    ownedQuantities, ownedExpected, reservation.inventoryOwnerId(),
                    reservation.inventorySlot(),
                    reservation.expectedStack()
                )) {
                return false;
            }
        }
        for (Map.Entry<OwnedInventorySlot, Integer> entry : ownedQuantities.entrySet()) {
            Container inventory = inventories.get(entry.getKey().ownerId());
            ItemStack current = inventoryStack(inventory, entry.getKey().slot());
            if (current.isEmpty() || current.getCount() < entry.getValue()
                || !ItemStack.isSameItemSameComponents(
                    current, ownedExpected.get(entry.getKey())
                )) {
                return false;
            }
        }

        ownedQuantities.forEach((slot, quantity) -> {
            Container inventory = inventories.get(slot.ownerId());
            inventoryStack(inventory, slot.slot()).shrink(quantity);
            inventory.setChanged();
        });
        Set<UUID> changedLoadouts = new HashSet<>();
        for (ReservationKey key : reservationKeys) {
            EnumMap<Category, List<ItemReference>> categories =
                selectionsByPlayer.get(key.loadoutId());
            if (categories != null) {
                categories.remove(key.category());
                if (categories.isEmpty()) {
                    selectionsByPlayer.remove(key.loadoutId());
                }
            }
            changedLoadouts.add(key.loadoutId());
        }
        changedLoadouts.forEach(this::advanceRevision);
        return true;
    }

    private static boolean registerTerminalConsumption(
        Map<OwnedInventorySlot, Integer> quantitiesBySlot,
        Map<OwnedInventorySlot, ItemStack> expectedBySlot,
        UUID inventoryOwnerId,
        int inventorySlot,
        ItemStack expectedStack
    ) {
        OwnedInventorySlot slot = new OwnedInventorySlot(inventoryOwnerId, inventorySlot);
        ItemStack prior = expectedBySlot.putIfAbsent(slot, expectedStack);
        if (prior != null && !ItemStack.isSameItemSameComponents(prior, expectedStack)) {
            return false;
        }
        quantitiesBySlot.merge(slot, 1, Integer::sum);
        return true;
    }

    private boolean terminalReservationValid(
        UUID loadoutId,
        UUID inventoryOwnerId,
        int inventorySlot,
        ItemStack expectedStack,
        long acceptedRevision,
        int floor,
        Category category,
        Container inventory
    ) {
        if (loadoutId == null || inventoryOwnerId == null || inventory == null
            || expectedStack == null || expectedStack.isEmpty()
            || acceptedRevision < 0L || floor < 1
            || revision(loadoutId) != acceptedRevision) {
            return false;
        }
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        List<ItemReference> references = categories == null
            ? List.of()
            : categories.getOrDefault(category, List.of());
        if (references.size() != 1) {
            return false;
        }
        ItemReference reference = references.getFirst();
        boolean shared = isSharedPlayerInventorySlot(reference.inventorySlot());
        int expectedSlot = shared
            ? decodeSharedPlayerInventorySlot(reference.inventorySlot())
            : reference.inventorySlot();
        UUID expectedOwner = shared
            ? inventoryOwnerId
            : inventoryOwner(loadoutId);
        ItemStack current = inventoryStack(inventory, inventorySlot);
        boolean protects = category == Category.RESURRECTION_BRACELET
            ? braceletProtectsFloor(current, floor)
            : whistleProtectsFloor(current, floor);
        return expectedOwner != null
            && expectedOwner.equals(inventoryOwnerId)
            && expectedSlot == inventorySlot
            && reference.quantity() == 1
            && ItemStack.isSameItemSameComponents(reference.expectedStack(), expectedStack)
            && reference.matches(current)
            && category.eligible(current)
            && protects;
    }

    static boolean braceletPathProtectsFloor(String itemPath, int floor) {
        if (itemPath == null || floor < 1
            || !itemPath.startsWith("resurrection_bracelet_")) {
            return false;
        }
        int braceletRank = switch (itemPath.substring("resurrection_bracelet_".length())) {
            case "normal" -> 0;
            case "special" -> 1;
            case "rare" -> 2;
            case "unique" -> 3;
            case "legend" -> 4;
            case "mythic" -> 5;
            case "genesis" -> 6;
            default -> -1;
        };
        int floorRank = Math.min(5, floor / 50);
        return braceletRank >= floorRank;
    }

    private static boolean braceletProtectsFloor(ItemStack stack, int floor) {
        return braceletPathProtectsFloor(
            BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),
            floor
        );
    }

    static boolean whistlePathProtectsFloor(String itemPath, int floor) {
        if (itemPath == null || floor < 1 || !itemPath.startsWith("return_whistle_")) {
            return false;
        }
        int whistleRank = switch (itemPath.substring("return_whistle_".length())) {
            case "normal" -> 0;
            case "special" -> 1;
            case "rare" -> 2;
            case "unique" -> 3;
            case "legend" -> 4;
            case "mythic" -> 5;
            case "genesis" -> 6;
            default -> -1;
        };
        int floorRank = Math.min(5, floor / 50);
        return whistleRank >= floorRank;
    }

    private static boolean whistleProtectsFloor(ItemStack stack, int floor) {
        return whistlePathProtectsFloor(
            BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), floor
        );
    }

    public MutationResult select(
        ServerPlayer player,
        Category category,
        int inventorySlot,
        long observedRevision
    ) {
        ensureDefaults(player);
        return select(player.getUUID(), player, category, inventorySlot, observedRevision);
    }

    public MutationResult select(
        UUID loadoutId,
        ServerPlayer inventoryOwner,
        Category category,
        int inventorySlot,
        long observedRevision
    ) {
        if (loadoutId == null || inventoryOwner == null) {
            return new MutationResult(
                MutationStatus.INVALID_TARGET,
                new LoadoutSnapshot(0L, Map.of())
            );
        }
        bindInventoryOwner(loadoutId, inventoryOwner.getUUID());
        Set<UUID> partyLoadoutIds = partyLoadoutIds(
            inventoryOwner.serverLevel(),
            inventoryOwner.getUUID()
        );
        if (category == null) {
            return result(loadoutId, inventoryOwner, MutationStatus.UNKNOWN_CATEGORY);
        }
        long revisionBeforeReconciliation = revision(loadoutId);
        reconcileMovedSelections(loadoutId, inventoryOwner);
        if (observedRevision != revisionBeforeReconciliation
            && observedRevision != revision(loadoutId)) {
            return result(loadoutId, inventoryOwner, MutationStatus.STALE_REVISION);
        }
        ItemStack stack = inventoryStack(inventoryOwner, inventorySlot);
        if (stack.isEmpty() || !category.eligible(stack)) {
            return result(loadoutId, inventoryOwner, MutationStatus.INELIGIBLE_ITEM);
        }
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.computeIfAbsent(
            loadoutId,
            ignored -> new EnumMap<>(Category.class)
        );
        int equipmentSlot = loadoutId.equals(inventoryOwner.getUUID())
            ? equipmentInventorySlot(category, inventoryOwner)
            : -1;
        if (isInventorySlotReserved(
            inventoryOwner.getUUID(),
            loadoutId,
            category,
            inventorySlot,
            partyLoadoutIds
        ) || (equipmentSlot >= 0 && equipmentSlot != inventorySlot
            && isInventorySlotReserved(
                inventoryOwner.getUUID(),
                loadoutId,
                category,
                equipmentSlot,
                partyLoadoutIds
            ))) {
            return result(loadoutId, inventoryOwner, MutationStatus.ITEM_ALREADY_SELECTED);
        }
        if (equipmentSlot >= 0) {
            equipSelection(inventoryOwner, inventorySlot, equipmentSlot);
            ItemStack equipped = inventoryStack(inventoryOwner, equipmentSlot);
            categories.put(category, List.of(
                new ItemReference(equipmentSlot, 1, equipped.copyWithCount(1))
            ));
            advanceRevision(loadoutId);
            return result(loadoutId, inventoryOwner, MutationStatus.SELECTED);
        }
        MutationStatus selectionStatus = applySelection(
            categories,
            category,
            inventorySlot,
            stack
        );
        if (selectionStatus != MutationStatus.SELECTED) {
            return result(loadoutId, inventoryOwner, selectionStatus);
        }
        advanceRevision(loadoutId);
        return result(loadoutId, inventoryOwner, MutationStatus.SELECTED);
    }

    public MutationResult selectSubordinateProtection(
        UUID loadoutId,
        Container inventory,
        Category category,
        int inventorySlot,
        long observedRevision
    ) {
        return selectSubordinateProtection(
            loadoutId,
            loadoutId,
            inventory,
            null,
            loadoutId == null ? Set.of() : Set.of(loadoutId),
            category,
            inventorySlot,
            observedRevision
        );
    }

    public MutationResult selectSubordinateProtection(
        UUID loadoutId,
        ServerPlayer host,
        Container subordinateInventory,
        Category category,
        int inventorySlot,
        long observedRevision
    ) {
        return selectSubordinateProtection(
            loadoutId,
            host == null ? null : host.getUUID(),
            subordinateInventory,
            host == null ? null : host.getInventory(),
            host == null
                ? Set.of()
                : partyLoadoutIds(host.serverLevel(), host.getUUID()),
            category,
            inventorySlot,
            observedRevision
        );
    }

    public MutationResult selectSubordinateProtection(
        UUID loadoutId,
        UUID hostId,
        Container subordinateInventory,
        Container hostInventory,
        Set<UUID> partyLoadoutIds,
        Category category,
        int inventorySlot,
        long observedRevision
    ) {
        if (loadoutId == null || hostId == null || subordinateInventory == null) {
            return new MutationResult(MutationStatus.INVALID_TARGET, new LoadoutSnapshot(0L, Map.of()));
        }
        bindInventoryOwner(loadoutId, loadoutId);
        if (category != Category.RESURRECTION_BRACELET && category != Category.RETURN_WHISTLE) {
            return subordinateResult(
                loadoutId,
                hostId,
                subordinateInventory,
                hostInventory,
                MutationStatus.UNKNOWN_CATEGORY
            );
        }
        long revisionBeforeReconciliation = revision(loadoutId);
        reconcileSubordinateProtectionSelections(
            loadoutId,
            hostId,
            subordinateInventory,
            hostInventory,
            partyLoadoutIds
        );
        if (observedRevision != revisionBeforeReconciliation
            && observedRevision != revision(loadoutId)) {
            return subordinateResult(
                loadoutId,
                hostId,
                subordinateInventory,
                hostInventory,
                MutationStatus.STALE_REVISION
            );
        }
        ItemStack stack = protectionStack(inventorySlot, subordinateInventory, hostInventory);
        if (stack.isEmpty() || !category.eligible(stack)) {
            return subordinateResult(
                loadoutId,
                hostId,
                subordinateInventory,
                hostInventory,
                MutationStatus.INELIGIBLE_ITEM
            );
        }
        if (isProtectionInventorySlotReserved(
            loadoutId,
            hostId,
            category,
            inventorySlot,
            partyLoadoutIds
        )) {
            return subordinateResult(
                loadoutId,
                hostId,
                subordinateInventory,
                hostInventory,
                MutationStatus.ITEM_ALREADY_SELECTED
            );
        }
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.computeIfAbsent(
            loadoutId, ignored -> new EnumMap<>(Category.class)
        );
        MutationStatus status = applySelection(categories, category, inventorySlot, stack);
        if (status == MutationStatus.SELECTED) {
            advanceRevision(loadoutId);
        }
        return subordinateResult(
            loadoutId,
            hostId,
            subordinateInventory,
            hostInventory,
            status
        );
    }

    static MutationStatus applySelection(
        EnumMap<Category, List<ItemReference>> categories,
        Category category,
        int inventorySlot,
        ItemStack stack
    ) {
        List<ItemReference> selections = new ArrayList<>(
            categories.getOrDefault(category, List.of())
        );
        if (category.cap() == 1 && category.maxQuantityPerSlot() == 1) {
            categories.put(category, List.of(
                new ItemReference(inventorySlot, 1, stack.copyWithCount(1))
            ));
            return MutationStatus.SELECTED;
        }

        int existingIndex = -1;
        for (int index = 0; index < selections.size(); index++) {
            if (selections.get(index).inventorySlot() == inventorySlot) {
                existingIndex = index;
                break;
            }
        }
        if (category.maxQuantityPerSlot() == 1) {
            int selectedSlots = selections.stream().mapToInt(ItemReference::quantity).sum();
            if (selectedSlots >= category.cap()) {
                return MutationStatus.CATEGORY_FULL;
            }
            if (existingIndex >= 0) {
                ItemReference existing = selections.get(existingIndex);
                int selectedFromStack = selections.stream()
                    .filter(selection -> selection.inventorySlot() == inventorySlot)
                    .mapToInt(ItemReference::quantity)
                    .sum();
                if (!ItemStack.isSameItemSameComponents(existing.expectedStack(), stack)
                    || selectedFromStack >= stack.getCount()) {
                    return MutationStatus.INSUFFICIENT_STACK_COUNT;
                }
                List<ItemReference> consolidated = new ArrayList<>();
                for (int index = 0; index < selections.size(); index++) {
                    ItemReference selection = selections.get(index);
                    if (selection.inventorySlot() != inventorySlot) {
                        consolidated.add(selection);
                    } else if (index == existingIndex) {
                        consolidated.add(new ItemReference(
                            inventorySlot,
                            selectedFromStack + 1,
                            stack.copyWithCount(1)
                        ));
                    }
                }
                selections = consolidated;
            } else {
                selections.add(new ItemReference(inventorySlot, 1, stack.copyWithCount(1)));
            }
        } else if (existingIndex >= 0) {
            ItemReference existing = selections.get(existingIndex);
            if (!ItemStack.isSameItemSameComponents(existing.expectedStack(), stack)
                || existing.quantity() >= stack.getCount()
                || existing.quantity() >= category.maxQuantityPerSlot()) {
                return MutationStatus.INSUFFICIENT_STACK_COUNT;
            }
            selections.set(existingIndex, new ItemReference(
                inventorySlot,
                existing.quantity() + 1,
                stack.copyWithCount(1)
            ));
        } else {
            if (selections.size() >= category.cap()) {
                return MutationStatus.CATEGORY_FULL;
            }
            selections.add(new ItemReference(inventorySlot, 1, stack.copyWithCount(1)));
        }
        categories.put(category, List.copyOf(selections));
        return MutationStatus.SELECTED;
    }

    public MutationResult clear(
        ServerPlayer player,
        Category category,
        long observedRevision
    ) {
        ensureDefaults(player);
        return clear(player.getUUID(), player, category, observedRevision);
    }

    public MutationResult clear(
        UUID loadoutId,
        ServerPlayer inventoryOwner,
        Category category,
        long observedRevision
    ) {
        if (loadoutId == null || inventoryOwner == null) {
            return new MutationResult(
                MutationStatus.INVALID_TARGET,
                new LoadoutSnapshot(0L, Map.of())
            );
        }
        bindInventoryOwner(loadoutId, inventoryOwner.getUUID());
        if (category == null) {
            return result(loadoutId, inventoryOwner, MutationStatus.UNKNOWN_CATEGORY);
        }
        long revisionBeforeReconciliation = revision(loadoutId);
        reconcileMovedSelections(loadoutId, inventoryOwner);
        if (observedRevision != revisionBeforeReconciliation
            && observedRevision != revision(loadoutId)) {
            return result(loadoutId, inventoryOwner, MutationStatus.STALE_REVISION);
        }
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        boolean hasSelection = categories != null && categories.containsKey(category);
        EnumMap<Category, List<ItemReference>> currentCategories = categories == null
            ? new EnumMap<>(Category.class)
            : categories;
        int equipmentSlot = loadoutId.equals(inventoryOwner.getUUID())
            ? selectedEquipmentInventorySlot(category, inventoryOwner, currentCategories)
            : -1;
        ItemStack equipped = equipmentSlot >= 0
            ? inventoryStack(inventoryOwner, equipmentSlot)
            : ItemStack.EMPTY;
        if (!hasSelection && (equipmentSlot < 0 || !category.eligible(equipped))) {
            return result(loadoutId, inventoryOwner, MutationStatus.NO_CHANGE);
        }
        if (equipmentSlot >= 0) {
            if (isInventorySlotReserved(
                inventoryOwner.getUUID(), loadoutId, category, equipmentSlot
            )) {
                return result(loadoutId, inventoryOwner, MutationStatus.ITEM_ALREADY_SELECTED);
            }
            if (!equipped.isEmpty()) {
                int destination = firstUnreservedStorageSlot(
                    inventoryOwner,
                    reservedInventorySlotsForMutation(
                        inventoryOwner.getUUID(), loadoutId, category
                    ),
                    equipmentSlot
                );
                if (destination < 0) {
                    return result(loadoutId, inventoryOwner, MutationStatus.INVENTORY_FULL);
                }
                unequipSelection(inventoryOwner, equipmentSlot, destination);
            }
        }
        if (categories != null) {
            categories.remove(category);
            if (categories.isEmpty()) {
                selectionsByPlayer.remove(loadoutId);
            }
        }
        advanceRevision(loadoutId);
        return result(loadoutId, inventoryOwner, MutationStatus.CLEARED);
    }

    public MutationResult clearSubordinateProtection(
        UUID loadoutId,
        ServerPlayer host,
        Container subordinateInventory,
        Category category,
        long observedRevision
    ) {
        return clearSubordinateProtection(
            loadoutId,
            host == null ? null : host.getUUID(),
            subordinateInventory,
            host == null ? null : host.getInventory(),
            host == null
                ? Set.of()
                : partyLoadoutIds(host.serverLevel(), host.getUUID()),
            category,
            observedRevision
        );
    }

    public MutationResult clearSubordinateProtection(
        UUID loadoutId,
        Container inventory,
        Category category,
        long observedRevision
    ) {
        return clearSubordinateProtection(
            loadoutId,
            loadoutId,
            inventory,
            null,
            loadoutId == null ? Set.of() : Set.of(loadoutId),
            category,
            observedRevision
        );
    }

    public MutationResult clearSubordinateProtection(
        UUID loadoutId,
        UUID hostId,
        Container subordinateInventory,
        Container hostInventory,
        Set<UUID> partyLoadoutIds,
        Category category,
        long observedRevision
    ) {
        if (loadoutId == null || hostId == null || subordinateInventory == null) {
            return new MutationResult(MutationStatus.INVALID_TARGET, new LoadoutSnapshot(0L, Map.of()));
        }
        bindInventoryOwner(loadoutId, loadoutId);
        if (category != Category.RESURRECTION_BRACELET && category != Category.RETURN_WHISTLE) {
            return subordinateResult(
                loadoutId,
                hostId,
                subordinateInventory,
                hostInventory,
                MutationStatus.UNKNOWN_CATEGORY
            );
        }
        long revisionBeforeReconciliation = revision(loadoutId);
        reconcileSubordinateProtectionSelections(
            loadoutId,
            hostId,
            subordinateInventory,
            hostInventory,
            partyLoadoutIds
        );
        if (observedRevision != revisionBeforeReconciliation
            && observedRevision != revision(loadoutId)) {
            return subordinateResult(
                loadoutId,
                hostId,
                subordinateInventory,
                hostInventory,
                MutationStatus.STALE_REVISION
            );
        }
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        if (categories == null || !categories.containsKey(category)) {
            return subordinateResult(
                loadoutId,
                hostId,
                subordinateInventory,
                hostInventory,
                MutationStatus.NO_CHANGE
            );
        }
        categories.remove(category);
        if (categories.isEmpty()) {
            selectionsByPlayer.remove(loadoutId);
        }
        advanceRevision(loadoutId);
        return subordinateResult(
            loadoutId,
            hostId,
            subordinateInventory,
            hostInventory,
            MutationStatus.CLEARED
        );
    }

    public Set<Integer> reservedInventorySlots(UUID inventoryOwnerId, UUID exceptLoadoutId) {
        if (inventoryOwnerId == null) {
            return Set.of();
        }
        Set<Integer> reserved = new HashSet<>();
        selectionsByPlayer.forEach((loadoutId, categories) -> {
            if (loadoutId.equals(exceptLoadoutId)
                || !inventoryOwnerId.equals(
                    inventoryOwnersByLoadout.getOrDefault(loadoutId, loadoutId)
                )) {
                return;
            }
            categories.values().forEach(references ->
                references.forEach(reference -> reserved.add(reference.inventorySlot()))
            );
        });
        return Set.copyOf(reserved);
    }

    public Set<Integer> reservedInventorySlotsForParty(
        UUID inventoryOwnerId,
        UUID exceptLoadoutId,
        Set<UUID> partyLoadoutIds
    ) {
        Set<Integer> reserved = new HashSet<>(
            reservedInventorySlots(inventoryOwnerId, exceptLoadoutId)
        );
        if (inventoryOwnerId == null || partyLoadoutIds == null || partyLoadoutIds.isEmpty()) {
            return Set.copyOf(reserved);
        }
        selectionsByPlayer.forEach((loadoutId, categories) -> {
            if (loadoutId.equals(exceptLoadoutId) || !partyLoadoutIds.contains(loadoutId)) {
                return;
            }
            categories.values().forEach(references -> references.forEach(reference -> {
                if (isSharedPlayerInventorySlot(reference.inventorySlot())) {
                    reserved.add(decodeSharedPlayerInventorySlot(reference.inventorySlot()));
                }
            }));
        });
        return Set.copyOf(reserved);
    }

    public Set<Integer> reservedProtectionCandidateSlots(
        UUID loadoutId,
        UUID hostId,
        ServerLevel level
    ) {
        return reservedProtectionCandidateSlots(
            loadoutId,
            hostId,
            partyLoadoutIds(level, hostId)
        );
    }

    private Set<Integer> reservedProtectionCandidateSlots(
        UUID loadoutId,
        UUID hostId,
        Set<UUID> partyLoadoutIds
    ) {
        Set<Integer> reserved = new HashSet<>(
            reservedInventorySlots(loadoutId, loadoutId)
        );
        for (int slot : reservedInventorySlotsForParty(
            hostId,
            loadoutId,
            partyLoadoutIds
        )) {
            reserved.add(sharedPlayerInventorySlot(slot));
        }
        return Set.copyOf(reserved);
    }

    private Set<Integer> reservedInventorySlotsForMutation(
        UUID inventoryOwnerId,
        UUID selectedLoadoutId,
        Category selectedCategory
    ) {
        Set<Integer> reserved = new HashSet<>();
        selectionsByPlayer.forEach((loadoutId, categories) -> {
            if (!inventoryOwnerId.equals(
                inventoryOwnersByLoadout.getOrDefault(loadoutId, loadoutId)
            )) {
                return;
            }
            for (Category category : Category.values()) {
                if (loadoutId.equals(selectedLoadoutId) && category == selectedCategory) {
                    continue;
                }
                categories.getOrDefault(category, List.of()).forEach(reference ->
                    reserved.add(reference.inventorySlot())
                );
            }
        });
        return Set.copyOf(reserved);
    }

    public boolean releaseSubordinateLoadout(UUID loadoutId, UUID priorHostInventoryOwnerId) {
        UUID storedOwner = loadoutId == null ? null : inventoryOwnersByLoadout.get(loadoutId);
        if (loadoutId == null
            || priorHostInventoryOwnerId == null
            || storedOwner == null
            || !storedOwner.equals(loadoutId)
                && !storedOwner.equals(priorHostInventoryOwnerId)) {
            return false;
        }
        boolean changed = selectionsByPlayer.remove(loadoutId) != null;
        changed |= passiveSkillsByLoadout.remove(loadoutId) != null;
        changed |= revisionsByPlayer.remove(loadoutId) != null;
        changed |= inventoryOwnersByLoadout.remove(loadoutId) != null;
        changed |= initializedPlayers.remove(loadoutId);
        changed |= equipmentDefaultsHydratedPlayers.remove(loadoutId);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public long revision(UUID playerId) {
        return Math.max(0L, revisionsByPlayer.getOrDefault(playerId, 0L));
    }

    public List<String> selectedPassiveSkills(UUID loadoutId) {
        return loadoutId == null
            ? List.of()
            : passiveSkillsByLoadout.getOrDefault(loadoutId, List.of());
    }

    public String validateSubordinateSkillLoadout(
        UUID loadoutId,
        LivingEntity subordinate
    ) {
        if (loadoutId == null || subordinate == null) {
            return "Subordinate skill loadout target is unavailable.";
        }
        TensuraSubordinateSkillCompat.SkillProjection learned =
            TensuraSubordinateSkillCompat.learnedSkills(subordinate);
        TensuraSubordinateSkillCompat.AbilityProjection active =
            TensuraSubordinateSkillCompat.currentAbilities(subordinate);
        if (!learned.available() || !active.available()) {
            return "";
        }
        Map<String, TensuraSubordinateSkillCompat.LearnedSkillIcon> learnedById = new HashMap<>();
        learned.skills().forEach(skill -> learnedById.putIfAbsent(skill.id(), skill));
        Set<String> activeIds = new HashSet<>();
        for (TensuraSubordinateSkillCompat.AbilitySlot slot : active.slots()) {
            if (slot.empty()) {
                continue;
            }
            if (!activeIds.add(slot.id())) {
                return "Review the subordinate skill loadout; an active ability is duplicated.";
            }
            TensuraSubordinateSkillCompat.LearnedSkillIcon learnedSkill = learnedById.get(slot.id());
            if (learnedSkill != null && learnedSkill.passive()) {
                return "Review the subordinate skill loadout; a passive ability is in an active slot.";
            }
        }
        for (String passiveId : selectedPassiveSkills(loadoutId)) {
            TensuraSubordinateSkillCompat.LearnedSkillIcon learnedSkill = learnedById.get(passiveId);
            if (learnedSkill == null || !learnedSkill.passive()) {
                return "Review the subordinate passive loadout; a selected ability is no longer learned.";
            }
        }
        return "";
    }

    public SkillMutationResult selectActiveSkill(
        UUID loadoutId,
        LivingEntity subordinate,
        String skillId,
        int slot,
        long observedRevision
    ) {
        if (loadoutId == null || subordinate == null) {
            return new SkillMutationResult(SkillMutationStatus.INVALID_TARGET);
        }
        bindInventoryOwner(loadoutId, loadoutId);
        if (observedRevision != revision(loadoutId)) {
            return new SkillMutationResult(SkillMutationStatus.STALE_REVISION);
        }
        TensuraSubordinateSkillCompat.SkillMutationResult result =
            TensuraSubordinateSkillCompat.assignActiveSkill(subordinate, skillId, slot);
        SkillMutationStatus status = mapSkillMutationStatus(result.status());
        if (status == SkillMutationStatus.APPLIED) {
            advanceRevision(loadoutId);
        }
        return new SkillMutationResult(status);
    }

    public SkillMutationResult clearActiveSkill(
        UUID loadoutId,
        LivingEntity subordinate,
        int slot,
        long observedRevision
    ) {
        if (loadoutId == null || subordinate == null) {
            return new SkillMutationResult(SkillMutationStatus.INVALID_TARGET);
        }
        bindInventoryOwner(loadoutId, loadoutId);
        if (observedRevision != revision(loadoutId)) {
            return new SkillMutationResult(SkillMutationStatus.STALE_REVISION);
        }
        TensuraSubordinateSkillCompat.SkillMutationResult result =
            TensuraSubordinateSkillCompat.clearActiveSkill(subordinate, slot);
        SkillMutationStatus status = mapSkillMutationStatus(result.status());
        if (status == SkillMutationStatus.CLEARED) {
            advanceRevision(loadoutId);
        }
        return new SkillMutationResult(status);
    }

    public SkillMutationResult selectPassiveSkill(
        UUID loadoutId,
        LivingEntity subordinate,
        String skillId,
        int slot,
        long observedRevision
    ) {
        if (loadoutId == null || subordinate == null) {
            return new SkillMutationResult(SkillMutationStatus.INVALID_TARGET);
        }
        bindInventoryOwner(loadoutId, loadoutId);
        if (observedRevision != revision(loadoutId)) {
            return new SkillMutationResult(SkillMutationStatus.STALE_REVISION);
        }
        if (slot < 0 || slot >= MAX_PASSIVE_SKILLS) {
            return new SkillMutationResult(SkillMutationStatus.INVALID_SLOT);
        }
        String normalized = normalizedSkillId(skillId);
        if (normalized.isBlank()) {
            return new SkillMutationResult(SkillMutationStatus.INVALID_ID);
        }
        TensuraSubordinateSkillCompat.SkillProjection projection =
            TensuraSubordinateSkillCompat.learnedSkills(subordinate);
        if (!projection.available()) {
            return new SkillMutationResult(SkillMutationStatus.SKILL_API_UNAVAILABLE);
        }
        TensuraSubordinateSkillCompat.LearnedSkillIcon learned = projection.skills().stream()
            .filter(skill -> normalized.equals(skill.id()))
            .findFirst()
            .orElse(null);
        if (learned == null) {
            return new SkillMutationResult(SkillMutationStatus.NOT_LEARNED);
        }
        if (!learned.passive()) {
            return new SkillMutationResult(SkillMutationStatus.INELIGIBLE);
        }
        List<String> selected = new ArrayList<>(selectedPassiveSkills(loadoutId));
        if (selected.contains(normalized)) {
            return new SkillMutationResult(SkillMutationStatus.DUPLICATE);
        }
        if (slot > selected.size()) {
            return new SkillMutationResult(SkillMutationStatus.INVALID_SLOT);
        }
        if (selected.size() >= MAX_PASSIVE_SKILLS) {
            return new SkillMutationResult(SkillMutationStatus.CAPACITY_FULL);
        }
        selected.add(slot, normalized);
        passiveSkillsByLoadout.put(loadoutId, List.copyOf(selected));
        advanceRevision(loadoutId);
        return new SkillMutationResult(SkillMutationStatus.APPLIED);
    }

    public SkillMutationResult clearPassiveSkill(
        UUID loadoutId,
        LivingEntity subordinate,
        int slot,
        long observedRevision
    ) {
        if (loadoutId == null || subordinate == null) {
            return new SkillMutationResult(SkillMutationStatus.INVALID_TARGET);
        }
        bindInventoryOwner(loadoutId, loadoutId);
        if (observedRevision != revision(loadoutId)) {
            return new SkillMutationResult(SkillMutationStatus.STALE_REVISION);
        }
        if (slot < 0 || slot >= MAX_PASSIVE_SKILLS) {
            return new SkillMutationResult(SkillMutationStatus.INVALID_SLOT);
        }
        List<String> selected = new ArrayList<>(selectedPassiveSkills(loadoutId));
        if (slot >= selected.size()) {
            return new SkillMutationResult(SkillMutationStatus.NO_CHANGE);
        }
        selected.remove(slot);
        if (selected.isEmpty()) {
            passiveSkillsByLoadout.remove(loadoutId);
        } else {
            passiveSkillsByLoadout.put(loadoutId, List.copyOf(selected));
        }
        advanceRevision(loadoutId);
        return new SkillMutationResult(SkillMutationStatus.CLEARED);
    }

    private static String normalizedSkillId(String skillId) {
        var parsed = TensuraSubordinateSkillCompat.parseTensuraSkillId(skillId);
        return parsed == null ? "" : parsed.toString();
    }

    private static SkillMutationStatus mapSkillMutationStatus(
        TensuraSubordinateSkillCompat.SkillMutationStatus status
    ) {
        return switch (status) {
            case APPLIED -> SkillMutationStatus.APPLIED;
            case CLEARED -> SkillMutationStatus.CLEARED;
            case NO_CHANGE -> SkillMutationStatus.NO_CHANGE;
            case INVALID_ID -> SkillMutationStatus.INVALID_ID;
            case NOT_LEARNED -> SkillMutationStatus.NOT_LEARNED;
            case INELIGIBLE -> SkillMutationStatus.INELIGIBLE;
            case DUPLICATE -> SkillMutationStatus.DUPLICATE;
            case INVALID_SLOT -> SkillMutationStatus.INVALID_SLOT;
            case UNAVAILABLE -> SkillMutationStatus.SKILL_API_UNAVAILABLE;
            case FAILED -> SkillMutationStatus.FAILED;
        };
    }

    private void ensureDefaults(ServerPlayer player) {
        UUID playerId = player.getUUID();
        bindInventoryOwner(playerId, playerId);
        if (initializedPlayers.add(playerId)) {
            EnumMap<Category, List<ItemReference>> categories = new EnumMap<>(Category.class);
            addEquipmentDefaults(categories, player);
            if (!categories.isEmpty()) {
                selectionsByPlayer.put(playerId, categories);
            }
            equipmentDefaultsHydratedPlayers.add(playerId);
            advanceRevision(playerId);
            return;
        }
        if (!equipmentDefaultsHydratedPlayers.add(playerId)) {
            return;
        }
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.computeIfAbsent(
            playerId,
            ignored -> new EnumMap<>(Category.class)
        );
        addEquipmentDefaults(categories, player);
        if (categories.isEmpty()) {
            selectionsByPlayer.remove(playerId);
        }
        advanceRevision(playerId);
    }

    private static void addEquipmentDefaults(
        EnumMap<Category, List<ItemReference>> categories,
        ServerPlayer player
    ) {
        addDefault(categories, Category.PRIMARY_SWORD, player, player.getInventory().selected);
        addDefault(categories, Category.OFF_HAND, player, 40);
        addDefault(categories, Category.BOOTS, player, 36);
        addDefault(categories, Category.LEGGINGS, player, 37);
        addDefault(categories, Category.CHESTPLATE, player, 38);
        addDefault(categories, Category.HELMET, player, 39);
    }

    private void synchronizeFixedEquipmentSelections(UUID loadoutId, ServerPlayer player) {
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.computeIfAbsent(
            loadoutId,
            ignored -> new EnumMap<>(Category.class)
        );
        boolean changed = false;
        for (Category category : List.of(
            Category.OFF_HAND,
            Category.BOOTS,
            Category.LEGGINGS,
            Category.CHESTPLATE,
            Category.HELMET
        )) {
            int slot = equipmentInventorySlot(category, player);
            ItemStack equipped = inventoryStack(player, slot);
            List<ItemReference> replacement = category.eligible(equipped)
                ? List.of(new ItemReference(slot, 1, equipped.copyWithCount(1)))
                : List.of();
            List<ItemReference> current = categories.getOrDefault(category, List.of());
            if (!sameReferences(current, replacement)) {
                changed = true;
                if (replacement.isEmpty()) {
                    categories.remove(category);
                } else {
                    categories.put(category, replacement);
                }
            }
        }
        if (categories.isEmpty()) {
            selectionsByPlayer.remove(loadoutId);
        }
        if (changed) {
            advanceRevision(loadoutId);
        }
    }

    private static boolean sameReferences(
        List<ItemReference> first,
        List<ItemReference> second
    ) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            ItemReference left = first.get(index);
            ItemReference right = second.get(index);
            if (left.inventorySlot() != right.inventorySlot()
                || left.quantity() != right.quantity()
                || !ItemStack.isSameItemSameComponents(
                    left.expectedStack(), right.expectedStack()
                )) {
                return false;
            }
        }
        return true;
    }

    private void refreshEquipmentRuntimeComponents(UUID loadoutId, ServerPlayer player) {
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        if (categories == null || categories.isEmpty()) return;
        boolean refreshed = false;
        for (Category category : Category.values()) {
            if (!isPlayerCombatEquipment(category)) continue;
            List<ItemReference> references = categories.getOrDefault(category, List.of());
            if (references.size() != 1) continue;
            ItemReference reference = references.getFirst();
            ItemStack current = inventoryStack(player, reference.inventorySlot());
            if (current.isEmpty()
                || !category.eligible(current)
                || ItemStack.isSameItemSameComponents(current, reference.expectedStack())
                || !sameEquipmentExceptVolatileRuntimeComponents(
                    current, reference.expectedStack()
                )) {
                continue;
            }
            categories.put(category, List.of(new ItemReference(
                reference.inventorySlot(), reference.quantity(), current.copyWithCount(1)
            )));
            refreshed = true;
        }
        if (refreshed) {
            setDirty();
        }
    }

    private static boolean isPlayerCombatEquipment(Category category) {
        return switch (category) {
            case PRIMARY_SWORD, OFF_HAND, HELMET, CHESTPLATE, LEGGINGS, BOOTS -> true;
            default -> false;
        };
    }

    private static boolean sameEquipmentExceptVolatileRuntimeComponents(
        ItemStack first,
        ItemStack second
    ) {
        return sameItemSameComponentsExcept(
            first,
            second,
            component -> VOLATILE_EXISTENCE_DURABILITY_COMPONENT.equals(
                String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component))
            )
        );
    }

    static boolean sameItemSameComponentsExcept(
        ItemStack first,
        ItemStack second,
        Predicate<DataComponentType<?>> ignoredComponent
    ) {
        if (first == null || second == null || ignoredComponent == null
            || !first.is(second.getItem())) {
            return false;
        }
        return first.getComponentsPatch().forget(ignoredComponent)
            .equals(second.getComponentsPatch().forget(ignoredComponent));
    }

    private static int equipmentInventorySlot(Category category, ServerPlayer player) {
        return switch (category) {
            case PRIMARY_SWORD -> player.getInventory().selected;
            case OFF_HAND -> 40;
            case BOOTS -> 36;
            case LEGGINGS -> 37;
            case CHESTPLATE -> 38;
            case HELMET -> 39;
            default -> -1;
        };
    }

    private static int selectedEquipmentInventorySlot(
        Category category,
        ServerPlayer player,
        EnumMap<Category, List<ItemReference>> categories
    ) {
        if (category == Category.PRIMARY_SWORD) {
            List<ItemReference> references = categories.getOrDefault(category, List.of());
            return references.isEmpty()
                ? player.getInventory().selected
                : references.getFirst().inventorySlot();
        }
        return equipmentInventorySlot(category, player);
    }

    private static void equipSelection(
        ServerPlayer player,
        int sourceSlot,
        int equipmentSlot
    ) {
        if (sourceSlot == equipmentSlot) return;
        List<ItemStack> inventory = inventoryCopy(player);
        swapEquipmentSlots(inventory, sourceSlot, equipmentSlot);
        player.getInventory().setItem(sourceSlot, inventory.get(sourceSlot));
        player.getInventory().setItem(equipmentSlot, inventory.get(equipmentSlot));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void unequipSelection(
        ServerPlayer player,
        int equipmentSlot,
        int destinationSlot
    ) {
        List<ItemStack> inventory = inventoryCopy(player);
        swapEquipmentSlots(inventory, equipmentSlot, destinationSlot);
        player.getInventory().setItem(destinationSlot, inventory.get(destinationSlot));
        player.getInventory().setItem(equipmentSlot, inventory.get(equipmentSlot));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static List<ItemStack> inventoryCopy(ServerPlayer player) {
        List<ItemStack> inventory = new ArrayList<>(player.getInventory().getContainerSize());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            inventory.add(player.getInventory().getItem(slot).copy());
        }
        return inventory;
    }

    static void swapEquipmentSlots(
        List<ItemStack> inventory,
        int sourceSlot,
        int equipmentSlot
    ) {
        if (inventory == null || sourceSlot < 0 || equipmentSlot < 0
            || sourceSlot >= inventory.size() || equipmentSlot >= inventory.size()) {
            throw new IllegalArgumentException("Equipment swap slot is outside the inventory");
        }
        if (sourceSlot == equipmentSlot) return;
        ItemStack selected = inventory.get(sourceSlot);
        inventory.set(sourceSlot, inventory.get(equipmentSlot));
        inventory.set(equipmentSlot, selected);
    }

    private static int firstUnreservedStorageSlot(
        ServerPlayer player,
        Set<Integer> reserved,
        int equipmentSlot
    ) {
        return firstUnreservedStorageSlot(inventoryCopy(player), reserved, equipmentSlot);
    }

    static int firstUnreservedStorageSlot(
        List<ItemStack> inventory,
        Set<Integer> reserved,
        int equipmentSlot
    ) {
        if (inventory == null) return -1;
        Set<Integer> unavailable = reserved == null ? Set.of() : reserved;
        int storageSize = Math.min(36, inventory.size());
        for (int slot = 0; slot < storageSize; slot++) {
            if (slot != equipmentSlot
                && !unavailable.contains(slot)
                && inventory.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static void addDefault(
        EnumMap<Category, List<ItemReference>> categories,
        Category category,
        ServerPlayer player,
        int inventorySlot
    ) {
        if (categories.containsKey(category)) {
            return;
        }
        ItemStack stack = inventoryStack(player, inventorySlot);
        if (category.eligible(stack)) {
            categories.put(category, List.of(
                new ItemReference(inventorySlot, 1, stack.copyWithCount(1))
            ));
        }
    }

    private MutationResult result(
        UUID loadoutId,
        ServerPlayer inventoryOwner,
        MutationStatus status
    ) {
        return new MutationResult(status, snapshot(loadoutId, inventoryOwner));
    }

    private void reconcileSubordinateProtectionSelections(
        UUID loadoutId,
        UUID hostId,
        Container subordinateInventory,
        Container hostInventory,
        Set<UUID> partyLoadoutIds
    ) {
        EnumMap<Category, List<ItemReference>> categories = selectionsByPlayer.get(loadoutId);
        if (categories == null || categories.isEmpty()) {
            return;
        }
        Set<Integer> reserved = reservedProtectionCandidateSlots(
            loadoutId,
            hostId,
            partyLoadoutIds
        );
        boolean changed = false;
        for (Category category : List.of(
            Category.RESURRECTION_BRACELET,
            Category.RETURN_WHISTLE
        )) {
            List<ItemReference> references = categories.getOrDefault(category, List.of());
            if (references.isEmpty()) {
                continue;
            }
            ItemReference reference = references.getFirst();
            ItemStack current = protectionStack(reference, subordinateInventory, hostInventory);
            boolean valid = reference.quantity() == 1
                && !reserved.contains(reference.inventorySlot())
                && reference.matches(current)
                && category.eligible(current);
            if (valid) {
                continue;
            }
            ItemReference rebound = findProtectionRebound(
                category,
                reference,
                subordinateInventory,
                hostInventory,
                reserved
            );
            if (rebound == null) {
                categories.remove(category);
            } else {
                categories.put(category, List.of(rebound));
            }
            changed = true;
        }
        if (!changed) {
            return;
        }
        if (categories.isEmpty()) {
            selectionsByPlayer.remove(loadoutId);
        }
        advanceRevision(loadoutId);
    }

    private static ItemReference findProtectionRebound(
        Category category,
        ItemReference previous,
        Container subordinateInventory,
        Container hostInventory,
        Set<Integer> reserved
    ) {
        boolean shared = isSharedPlayerInventorySlot(previous.inventorySlot());
        Container source = shared ? hostInventory : subordinateInventory;
        if (source == null) {
            return null;
        }
        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            int encodedSlot = shared ? sharedPlayerInventorySlot(slot) : slot;
            if (reserved.contains(encodedSlot)) {
                continue;
            }
            ItemStack current = source.getItem(slot);
            if (category.eligible(current)
                && ItemStack.isSameItemSameComponents(current, previous.expectedStack())) {
                return new ItemReference(encodedSlot, 1, current.copyWithCount(1));
            }
        }
        return null;
    }

    private static ItemStack protectionStack(
        int encodedSlot,
        Container subordinateInventory,
        Container hostInventory
    ) {
        if (isSharedPlayerInventorySlot(encodedSlot)) {
            return hostInventory == null
                ? ItemStack.EMPTY
                : inventoryStack(hostInventory, decodeSharedPlayerInventorySlot(encodedSlot));
        }
        return inventoryStack(subordinateInventory, encodedSlot);
    }

    private static ItemStack protectionStack(
        ItemReference reference,
        Container subordinateInventory,
        Container hostInventory
    ) {
        return reference == null
            ? ItemStack.EMPTY
            : protectionStack(reference.inventorySlot(), subordinateInventory, hostInventory);
    }

    private static ResolvedProtectionReference resolveProtectionReference(
        UUID loadoutId,
        UUID hostId,
        ItemReference reference,
        Container subordinateInventory,
        Container hostInventory
    ) {
        if (reference == null) {
            return null;
        }
        if (isSharedPlayerInventorySlot(reference.inventorySlot())) {
            int inventorySlot = decodeSharedPlayerInventorySlot(reference.inventorySlot());
            return new ResolvedProtectionReference(
                hostId,
                inventorySlot,
                inventoryStack(hostInventory, inventorySlot),
                reference.expectedStack()
            );
        }
        return new ResolvedProtectionReference(
            loadoutId,
            reference.inventorySlot(),
            inventoryStack(subordinateInventory, reference.inventorySlot()),
            reference.expectedStack()
        );
    }

    private boolean isProtectionInventorySlotReserved(
        UUID loadoutId,
        UUID hostId,
        Category selectedCategory,
        int encodedSlot,
        Set<UUID> partyLoadoutIds
    ) {
        if (isSharedPlayerInventorySlot(encodedSlot)) {
            int actualSlot = decodeSharedPlayerInventorySlot(encodedSlot);
            return reservedInventorySlotsForParty(
                hostId,
                loadoutId,
                partyLoadoutIds
            ).contains(actualSlot);
        }
        return isInventorySlotReserved(
            loadoutId,
            loadoutId,
            selectedCategory,
            encodedSlot
        );
    }

    private MutationResult subordinateResult(
        UUID loadoutId,
        Container inventory,
        MutationStatus status
    ) {
        return new MutationResult(status, snapshotFromInventory(loadoutId, inventory, false));
    }

    private MutationResult subordinateResult(
        UUID loadoutId,
        UUID hostId,
        Container subordinateInventory,
        Container hostInventory,
        MutationStatus status
    ) {
        return new MutationResult(
            status,
            snapshotFromProtectionInventories(
                loadoutId,
                hostId,
                subordinateInventory,
                hostInventory,
                false
            )
        );
    }

    private void bindInventoryOwner(UUID loadoutId, UUID inventoryOwnerId) {
        UUID previous = inventoryOwnersByLoadout.putIfAbsent(loadoutId, inventoryOwnerId);
        if (previous == null) {
            setDirty();
            return;
        }
        if (previous.equals(inventoryOwnerId)) {
            return;
        }
        inventoryOwnersByLoadout.put(loadoutId, inventoryOwnerId);
        selectionsByPlayer.remove(loadoutId);
        initializedPlayers.remove(loadoutId);
        equipmentDefaultsHydratedPlayers.remove(loadoutId);
        advanceRevision(loadoutId);
    }

    private boolean isInventorySlotReserved(
        UUID inventoryOwnerId,
        UUID selectedLoadoutId,
        Category selectedCategory,
        int inventorySlot
    ) {
        return isInventorySlotReserved(
            selectionsByPlayer,
            inventoryOwnersByLoadout,
            inventoryOwnerId,
            selectedLoadoutId,
            selectedCategory,
            inventorySlot
        );
    }

    private boolean isInventorySlotReserved(
        UUID inventoryOwnerId,
        UUID selectedLoadoutId,
        Category selectedCategory,
        int inventorySlot,
        Set<UUID> partyLoadoutIds
    ) {
        return isInventorySlotReserved(
            inventoryOwnerId,
            selectedLoadoutId,
            selectedCategory,
            inventorySlot
        ) || reservedInventorySlotsForParty(
            inventoryOwnerId,
            selectedLoadoutId,
            partyLoadoutIds
        ).contains(inventorySlot);
    }

    static boolean isInventorySlotReserved(
        Map<UUID, EnumMap<Category, List<ItemReference>>> selectionsByLoadout,
        Map<UUID, UUID> inventoryOwnersByLoadout,
        UUID inventoryOwnerId,
        UUID selectedLoadoutId,
        Category selectedCategory,
        int inventorySlot
    ) {
        for (Map.Entry<UUID, EnumMap<Category, List<ItemReference>>> entry
            : selectionsByLoadout.entrySet()) {
            UUID loadoutId = entry.getKey();
            if (!inventoryOwnerId.equals(
                inventoryOwnersByLoadout.getOrDefault(loadoutId, loadoutId)
            )) {
                continue;
            }
            for (Category category : Category.values()) {
                if (loadoutId.equals(selectedLoadoutId) && category == selectedCategory) {
                    continue;
                }
                if (entry.getValue().getOrDefault(category, List.of()).stream()
                    .anyMatch(reference -> reference.inventorySlot() == inventorySlot)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void advanceRevision(UUID playerId) {
        revisionsByPlayer.put(playerId, revision(playerId) + 1L);
        setDirty();
    }

    private static ItemStack inventoryStack(ServerPlayer player, int slot) {
        if (player == null || slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(slot);
    }

    private static ItemStack inventoryStack(Container inventory, int slot) {
        if (inventory == null || slot < 0 || slot >= inventory.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return inventory.getItem(slot);
    }

    static void normalize(EnumMap<Category, List<ItemReference>> categories) {
        for (Category category : Category.values()) {
            List<ItemReference> references = new ArrayList<>(
                categories.getOrDefault(category, List.of())
            );
            if (category.maxQuantityPerSlot() == 1) {
                List<ItemReference> consolidated = new ArrayList<>();
                int retainedSlots = 0;
                for (ItemReference reference : references) {
                    int quantity = Math.min(reference.quantity(), category.cap() - retainedSlots);
                    if (quantity <= 0) {
                        break;
                    }
                    int existingIndex = -1;
                    for (int index = 0; index < consolidated.size(); index++) {
                        ItemReference existing = consolidated.get(index);
                        if (existing.inventorySlot() == reference.inventorySlot()
                            && ItemStack.isSameItemSameComponents(
                                existing.expectedStack(),
                                reference.expectedStack()
                            )) {
                            existingIndex = index;
                            break;
                        }
                    }
                    if (existingIndex >= 0) {
                        ItemReference existing = consolidated.get(existingIndex);
                        consolidated.set(existingIndex, new ItemReference(
                            existing.inventorySlot(),
                            existing.quantity() + quantity,
                            existing.expectedStack()
                        ));
                    } else {
                        consolidated.add(new ItemReference(
                            reference.inventorySlot(),
                            quantity,
                            reference.expectedStack()
                        ));
                    }
                    retainedSlots += quantity;
                }
                if (consolidated.isEmpty()) {
                    categories.remove(category);
                } else {
                    categories.put(category, List.copyOf(consolidated));
                }
                continue;
            }
            int retained = 0;
            List<ItemReference> normalized = new ArrayList<>();
            for (ItemReference reference : references) {
                if (retained >= category.cap()) {
                    break;
                }
                int quantity = Math.min(
                    reference.quantity(),
                    category.maxQuantityPerSlot()
                );
                if (quantity > 0) {
                    normalized.add(new ItemReference(
                        reference.inventorySlot(),
                        quantity,
                        reference.expectedStack().copyWithCount(1)
                    ));
                    retained++;
                }
            }
            if (normalized.isEmpty()) {
                categories.remove(category);
            } else {
                categories.put(category, List.copyOf(normalized));
            }
        }
    }

    static boolean isInventorySlotSelectedOutside(
        EnumMap<Category, List<ItemReference>> categories,
        Category selectedCategory,
        int inventorySlot
    ) {
        if (categories == null || selectedCategory == null || inventorySlot < 0) {
            return false;
        }
        for (Category category : Category.values()) {
            if (category == selectedCategory) {
                continue;
            }
            for (ItemReference reference : categories.getOrDefault(category, List.of())) {
                if (reference.inventorySlot() == inventorySlot) {
                    return true;
                }
            }
        }
        return false;
    }

    static void normalizeExclusiveInventorySlots(
        EnumMap<Category, List<ItemReference>> categories
    ) {
        Map<Integer, Category> claimedSlots = new HashMap<>();
        for (Category category : Category.values()) {
            List<ItemReference> retained = new ArrayList<>();
            for (ItemReference reference : categories.getOrDefault(category, List.of())) {
                Category previous = claimedSlots.putIfAbsent(reference.inventorySlot(), category);
                if (previous == null || previous == category) {
                    retained.add(reference);
                }
            }
            if (retained.isEmpty()) {
                categories.remove(category);
            } else {
                categories.put(category, List.copyOf(retained));
            }
        }
    }

    public enum Category {
        PRIMARY_SWORD(1, 1),
        OFF_HAND(1, 1),
        HELMET(1, 1),
        CHESTPLATE(1, 1),
        LEGGINGS(1, 1),
        BOOTS(1, 1),
        RESURRECTION_BRACELET(1, 1),
        RETURN_WHISTLE(1, 1),
        HEALTH_RECOVERY(3, 1),
        MAGICULE_RECOVERY(3, 1),
        UTILITY(2, 1),
        FIELD_RATIONS(2, 5);

        private final int cap;
        private final int maxQuantityPerSlot;

        Category(int cap, int maxQuantityPerSlot) {
            this.cap = cap;
            this.maxQuantityPerSlot = maxQuantityPerSlot;
        }

        public int cap() {
            return cap;
        }

        public int maxQuantityPerSlot() {
            return maxQuantityPerSlot;
        }

        int maxQuantityFromInventoryStack() {
            return maxQuantityPerSlot == 1 ? cap : maxQuantityPerSlot;
        }

        public boolean eligible(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                return false;
            }
            return switch (this) {
                case PRIMARY_SWORD -> isMainHandWeapon(stack);
                case OFF_HAND -> isMainHandWeapon(stack);
                case HELMET -> armorSlot(stack) == EquipmentSlot.HEAD;
                case CHESTPLATE -> armorSlot(stack) == EquipmentSlot.CHEST;
                case LEGGINGS -> armorSlot(stack) == EquipmentSlot.LEGS;
                case BOOTS -> armorSlot(stack) == EquipmentSlot.FEET;
                case RESURRECTION_BRACELET -> approvedGradeItem(
                    stack,
                    "resurrection_bracelet_"
                );
                case RETURN_WHISTLE -> approvedGradeItem(stack, "return_whistle_");
                case HEALTH_RECOVERY -> approvedPath(stack, "healing_")
                    || potionHasHealing(stack);
                case MAGICULE_RECOVERY -> approvedPath(stack, "magicule_");
                case UTILITY -> isPotion(stack) && !potionHasHealing(stack);
                case FIELD_RATIONS -> approvedPath(stack, "field_ration")
                    || stack.has(DataComponents.FOOD);
            };
        }

        public static Optional<Category> parse(String value) {
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(Category.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }

        private static EquipmentSlot armorSlot(ItemStack stack) {
            return stack.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot() : null;
        }

        private static boolean isMainHandWeapon(ItemStack stack) {
            if (stack.getItem() instanceof ArmorItem) {
                return false;
            }
            if (stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof MaceItem) {
                return true;
            }
            boolean[] hasAttackDamage = {false};
            stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                    hasAttackDamage[0] = true;
                }
            });
            return hasAttackDamage[0];
        }

        private static boolean approvedGradeItem(ItemStack stack, String prefix) {
            return approvedPath(stack, prefix)
                && List.of("normal", "special", "rare", "unique", "legend", "mythic", "genesis")
                    .stream()
                    .anyMatch(grade -> itemPath(stack).equals(prefix + grade));
        }

        private static boolean approvedPath(ItemStack stack, String prefix) {
            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return ("tensura_dungeon".equals(key.getNamespace())
                || "tensura_additions".equals(key.getNamespace()))
                && itemPath(stack).startsWith(prefix);
        }

        private static String itemPath(ItemStack stack) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        }

        private static boolean isPotion(ItemStack stack) {
            return stack.has(DataComponents.POTION_CONTENTS);
        }

        private static boolean potionHasHealing(ItemStack stack) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) {
                return false;
            }
            for (var effect : contents.getAllEffects()) {
                if (effect.getEffect().is(MobEffects.HEAL)
                    || effect.getEffect().is(MobEffects.REGENERATION)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record SelectionEntry(Category category, ItemReference reference) {}

    private record SelectionSlot(Category category, int inventorySlot) {}

    private record ReservationKey(UUID loadoutId, Category category) {}

    private record OwnedInventorySlot(UUID ownerId, int slot) {}

    private record PendingSelection(
        int entryIndex,
        Category category,
        ItemReference reference
    ) {}

    private record ResolvedProtectionReference(
        UUID ownerId,
        int inventorySlot,
        ItemStack current,
        ItemStack expectedStack
    ) {
        private ResolvedProtectionReference {
            expectedStack = expectedStack == null
                ? ItemStack.EMPTY
                : expectedStack.copyWithCount(1);
        }
    }

    public record ItemReference(int inventorySlot, int quantity, ItemStack expectedStack) {
        public ItemReference {
            if (inventorySlot < 0 || quantity <= 0 || expectedStack == null || expectedStack.isEmpty()) {
                throw new IllegalArgumentException("Preparation item reference is incomplete");
            }
            expectedStack = expectedStack.copyWithCount(1);
        }

        boolean matches(ItemStack current) {
            return current != null
                && !current.isEmpty()
                && current.getCount() >= quantity
                && ItemStack.isSameItemSameComponents(current, expectedStack);
        }
    }

    public record BraceletReservation(
        UUID loadoutId,
        UUID inventoryOwnerId,
        long acceptedRevision,
        int inventorySlot,
        int floor,
        ItemStack expectedStack
    ) {
        public BraceletReservation {
            if (loadoutId == null || inventoryOwnerId == null || acceptedRevision < 0L
                || inventorySlot < 0 || floor < 1 || expectedStack == null
                || expectedStack.isEmpty()) {
                throw new IllegalArgumentException("Bracelet reservation is incomplete");
            }
            expectedStack = expectedStack.copyWithCount(1);
        }

        boolean sameReference(BraceletReservation other) {
            return other != null
                && loadoutId.equals(other.loadoutId)
                && inventoryOwnerId.equals(other.inventoryOwnerId)
                && acceptedRevision == other.acceptedRevision
                && inventorySlot == other.inventorySlot
                && floor == other.floor
                && ItemStack.isSameItemSameComponents(expectedStack, other.expectedStack);
        }
    }

    public record WhistleReservation(
        UUID loadoutId,
        UUID inventoryOwnerId,
        long acceptedRevision,
        int inventorySlot,
        int floor,
        ItemStack expectedStack
    ) {
        public WhistleReservation {
            if (loadoutId == null || inventoryOwnerId == null || acceptedRevision < 0L
                || inventorySlot < 0 || floor < 1 || expectedStack == null
                || expectedStack.isEmpty()) {
                throw new IllegalArgumentException("Whistle reservation is incomplete");
            }
            expectedStack = expectedStack.copyWithCount(1);
        }

        boolean sameReference(WhistleReservation other) {
            return other != null
                && loadoutId.equals(other.loadoutId)
                && inventoryOwnerId.equals(other.inventoryOwnerId)
                && acceptedRevision == other.acceptedRevision
                && inventorySlot == other.inventorySlot
                && floor == other.floor
                && ItemStack.isSameItemSameComponents(expectedStack, other.expectedStack);
        }
    }

    public record SelectionView(
        Category category,
        int inventorySlot,
        int quantity,
        ItemStack stack,
        boolean valid
    ) {
        public SelectionView {
            if (category == null || inventorySlot < 0 || quantity <= 0) {
                throw new IllegalArgumentException("Preparation selection view is incomplete");
            }
            stack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
    }

    public record LoadoutSnapshot(
        long revision,
        Map<Category, List<SelectionView>> selections
    ) {
        public LoadoutSnapshot {
            revision = Math.max(0L, revision);
            EnumMap<Category, List<SelectionView>> copy = new EnumMap<>(Category.class);
            if (selections != null) {
                selections.forEach((category, views) -> copy.put(
                    category,
                    views == null ? List.of() : List.copyOf(views)
                ));
            }
            selections = Map.copyOf(copy);
        }

        public List<SelectionView> category(Category category) {
            return selections.getOrDefault(category, List.of());
        }

        public int count(Category category) {
            return category(category).stream()
                .filter(SelectionView::valid)
                .mapToInt(SelectionView::quantity)
                .sum();
        }

        public ItemStack firstValid(Category category) {
            return category(category).stream()
                .filter(SelectionView::valid)
                .map(SelectionView::stack)
                .findFirst()
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
        }
    }

    public enum MutationStatus {
        SELECTED,
        CLEARED,
        NO_CHANGE,
        STALE_REVISION,
        UNKNOWN_CATEGORY,
        INELIGIBLE_ITEM,
        CATEGORY_FULL,
        INSUFFICIENT_STACK_COUNT,
        ITEM_ALREADY_SELECTED,
        INVENTORY_FULL,
        INVALID_TARGET
    }

    public enum SkillMutationStatus {
        APPLIED,
        CLEARED,
        NO_CHANGE,
        STALE_REVISION,
        INVALID_TARGET,
        INVALID_ID,
        NOT_LEARNED,
        INELIGIBLE,
        DUPLICATE,
        CAPACITY_FULL,
        INVALID_SLOT,
        SKILL_API_UNAVAILABLE,
        FAILED
    }

    public record SkillMutationResult(SkillMutationStatus status) {
        public SkillMutationResult {
            status = status == null ? SkillMutationStatus.FAILED : status;
        }
    }

    public record MutationResult(MutationStatus status, LoadoutSnapshot snapshot) {}
}
