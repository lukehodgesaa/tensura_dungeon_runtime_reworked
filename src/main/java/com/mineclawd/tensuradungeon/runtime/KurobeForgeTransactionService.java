package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.network.KurobeForgeActionRequestPayload;
import com.mineclawd.tensuradungeon.network.KurobeForgeActionResultPayload;
import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotFactory;
import com.mineclawd.tensuradungeon.network.KurobeForgeSnapshotPayload;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class KurobeForgeTransactionService {
    private static final Object TRANSACTION_LOCK = new Object();
    private static final int MAX_REPLAY_IDS_PER_PLAYER = 128;
    private static final Map<UUID, LinkedHashMap<Long, Boolean>> SEEN_REQUESTS = new HashMap<>();

    private KurobeForgeTransactionService() {}

    public static Result execute(
        ServerPlayer player,
        KurobeForgeActionRequestPayload request,
        KurobeForgeSnapshotPayload authoritativeSnapshot
    ) {
        if (player == null || request == null || authoritativeSnapshot == null) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.INVALID_REQUEST,
                "Kurobe action context is incomplete");
        }
        synchronized (TRANSACTION_LOCK) {
            if (!claimRequest(player.getUUID(), request.requestId())) {
                return failure(KurobeForgeActionResultPayload.ResultStatus.REPLAYED_REQUEST,
                    "This Kurobe request was already processed");
            }
            Result validation = validateRequest(request, authoritativeSnapshot);
            if (!validation.success()) return validation;

            KurobeForgeSnapshotPayload.Weapon weapon = authoritativeSnapshot.weapon(request.weaponId());
            if (weapon == null) {
                return failure(KurobeForgeActionResultPayload.ResultStatus.WEAPON_NOT_FOUND,
                    "The selected weapon is no longer present");
            }
            ItemStack stack = player.getInventory().getItem(weapon.inventorySlot());
            if (!KurobeForgeSnapshotFactory.eligible(stack)
                || !KurobeForgeSnapshotFactory.weaponId(weapon.inventorySlot(), stack).equals(weapon.id())) {
                return failure(KurobeForgeActionResultPayload.ResultStatus.WEAPON_INELIGIBLE,
                    "The selected inventory item is no longer an eligible Equipment EP weapon");
            }
            if (KurobeForgeWeaponData.hasCorruptSocket(stack)) {
                return failure(KurobeForgeActionResultPayload.ResultStatus.CORRUPT_WEAPON_DATA,
                    "Corrupt socket data was quarantined; no Forge mutation was performed");
            }
            if (request.service() != KurobeForgeSnapshotPayload.Service.SOCKET
                && (!request.coreId().isBlank() || request.coreSlot() != -1)) {
                return failure(KurobeForgeActionResultPayload.ResultStatus.INVALID_REQUEST,
                    "This Kurobe service does not accept a Satchel Core selection");
            }

            return switch (request.service()) {
                case PROMOTE -> promote(player, stack);
                case CONDUIT -> conduit(player, stack);
                case SOCKET -> socket(player, stack, request, authoritativeSnapshot);
                case RECHARGE -> recharge(player, stack);
                case REMOVE -> remove(player, stack);
            };
        }
    }

    static Result validateRequest(
        KurobeForgeActionRequestPayload request,
        KurobeForgeSnapshotPayload snapshot
    ) {
        if (request == null || snapshot == null) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.INVALID_REQUEST, "Missing request");
        }
        if (request.schemaVersion() != KurobeForgeActionRequestPayload.CURRENT_SCHEMA
            || !snapshot.supportedSchema()) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.INVALID_REQUEST,
                "Kurobe protocol version is unsupported");
        }
        if (request.observedRevision() != snapshot.revision()) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.STALE_REVISION,
                "Forge inventory, Satchel, certification, or currency changed; review the refreshed quote");
        }
        if (snapshot.weapon(request.weaponId()) == null) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.WEAPON_NOT_FOUND,
                "The selected weapon was not found in the authoritative snapshot");
        }
        return Result.success("");
    }

    private static Result promote(ServerPlayer player, ItemStack stack) {
        UUID playerId = player.getUUID();
        KurobeForgeWeaponData.State current = KurobeForgeWeaponData.read(stack);
        KurobeForgeSnapshotPayload.Grade target = KurobeForgeRules.nextGrade(current.grade());
        if (target == null) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.MAXIMUM_GRADE,
                "This weapon is already Mythic Equipment Grade");
        }
        if (target == KurobeForgeSnapshotPayload.Grade.GENESIS) {
            return failure(
                KurobeForgeActionResultPayload.ResultStatus.MAXIMUM_GRADE,
                "Genesis / Grade VII is reserved for future content"
            );
        }
        int highestFloor = DungeonFloorProgressData.get(player.serverLevel())
            .getHighestCompletedFloor(playerId);
        if (highestFloor < KurobeForgeRules.requiredFloor(target)) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.FLOOR_LOCKED,
                "Complete Floor " + KurobeForgeRules.requiredFloor(target) + " before " + target + " work");
        }

        KurobeForgeData forgeData = KurobeForgeData.get(player.serverLevel());
        KurobeForgeSnapshotPayload.Grade certified = forgeData.certifiedGrade(playerId);
        if (certified.ordinal() < target.ordinal()) {
            if (certified.ordinal() + 1 != target.ordinal()) {
                return failure(KurobeForgeActionResultPayload.ResultStatus.CERTIFICATION_REQUIRED,
                    "Forge Certifications must be purchased in grade order");
            }
            List<Item> materials = resolveMaterials(target);
            if (materials == null || !hasMaterials(player.getInventory(), materials)) {
                return failure(KurobeForgeActionResultPayload.ResultStatus.MISSING_MATERIALS,
                    "Required certification material: "
                        + KurobeForgeRules.certificationMaterialLabel(target));
            }
            Optional<DungeonShopCurrency.PaymentPlan> payment = DungeonShopCurrency.prepare(
                player, KurobeForgeRules.certificationPriceBronze(target)
            );
            if (payment.isEmpty()) return insufficientFunds();
            DungeonShopCurrency.DebitReceipt receipt = null;
            try {
                receipt = DungeonShopCurrency.debit(player, payment.orElseThrow());
                consumeMaterials(player.getInventory(), materials);
                if (!forgeData.certify(playerId, target)) {
                    throw new IllegalStateException("Certification progression changed during transaction");
                }
                player.getInventory().setChanged();
                return Result.success(target + " Forge Certification purchased; confirm Promote again for the weapon");
            } catch (DungeonShopCurrency.ChangeInventoryException failure) {
                return inventoryFull();
            } catch (RuntimeException failure) {
                forgeData.rollbackCertification(playerId, target);
                if (receipt != null) receipt.rollback(player);
                return internalRollback("Certification");
            }
        }

        KurobeForgeSnapshotFactory.EpSnapshot ep = KurobeForgeSnapshotFactory.equipmentEp(stack);
        if (ep == null || ep.current() < ep.maximum()) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.NATIVE_REQUIREMENT,
                "The native weapon must reach its maximum Equipment EP first");
        }
        if (!KurobeForgeSnapshotFactory.nativePromotionEligible(stack, target)) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.NATIVE_REQUIREMENT,
                "The native weapon material does not support " + target + " promotion");
        }
        Optional<DungeonShopCurrency.PaymentPlan> payment = DungeonShopCurrency.prepare(
            player, KurobeForgeRules.promotionPriceBronze(target)
        );
        if (payment.isEmpty()) return insufficientFunds();
        DungeonShopCurrency.DebitReceipt receipt = null;
        try {
            receipt = DungeonShopCurrency.debit(player, payment.orElseThrow());
            KurobeForgeWeaponData.promote(stack, target);
            player.getInventory().setChanged();
            return Result.success("Weapon promoted to " + target + "; Conduit returned to Unrefined");
        } catch (DungeonShopCurrency.ChangeInventoryException failure) {
            return inventoryFull();
        } catch (RuntimeException failure) {
            if (receipt != null) receipt.rollback(player);
            return internalRollback("Weapon promotion");
        }
    }

    private static Result conduit(ServerPlayer player, ItemStack stack) {
        KurobeForgeWeaponData.State current = KurobeForgeWeaponData.read(stack);
        if (!KurobeForgeRules.isActiveGrade(current.grade())) {
            return failure(
                KurobeForgeActionResultPayload.ResultStatus.MAXIMUM_GRADE,
                "Genesis / Grade VII is reserved for future content"
            );
        }
        KurobeForgeSnapshotPayload.Grade certified = KurobeForgeData.get(player.serverLevel())
            .certifiedGrade(player.getUUID());
        if (certified.ordinal() < current.grade().ordinal()) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.CERTIFICATION_REQUIRED,
                "Forge Certification is required for this weapon grade");
        }
        if (current.conduitRank() >= 6) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.MAXIMUM_RANK,
                "This weapon is already Conduit Rank VI");
        }
        Optional<DungeonShopCurrency.PaymentPlan> payment = DungeonShopCurrency.prepare(
            player, KurobeForgeRules.conduitPriceBronze(current.grade())
        );
        if (payment.isEmpty()) return insufficientFunds();
        DungeonShopCurrency.DebitReceipt receipt = null;
        try {
            receipt = DungeonShopCurrency.debit(player, payment.orElseThrow());
            int nextRank = current.conduitRank() + 1;
            KurobeForgeWeaponData.setConduitRank(stack, nextRank);
            player.getInventory().setChanged();
            return Result.success("Conduit refined to Rank " + roman(nextRank));
        } catch (DungeonShopCurrency.ChangeInventoryException failure) {
            return inventoryFull();
        } catch (RuntimeException failure) {
            if (receipt != null) receipt.rollback(player);
            return internalRollback("Conduit");
        }
    }

    private static Result socket(
        ServerPlayer player,
        ItemStack stack,
        KurobeForgeActionRequestPayload request,
        KurobeForgeSnapshotPayload snapshot
    ) {
        if (KurobeForgeSnapshotFactory.dungeonServicesLocked(player)) return dungeonActive();
        if (request.coreId().isBlank() || request.coreSlot() < 0) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.CORE_NOT_SELECTED,
                "Select one exact Core from the Core Satchel");
        }
        KurobeForgeSnapshotPayload.CoreOption option = snapshot.core(request.coreId(), request.coreSlot());
        if (option == null) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.CORE_NOT_FOUND,
                "The selected Satchel Core is stale or missing");
        }
        UUID coreId;
        try {
            coreId = UUID.fromString(request.coreId());
        } catch (IllegalArgumentException invalid) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.INVALID_REQUEST,
                "The selected Core identity is invalid");
        }

        UUID playerId = player.getUUID();
        KurobeForgeWeaponData.State current = KurobeForgeWeaponData.read(stack);
        if (!KurobeForgeRules.isActiveGrade(current.grade())) {
            return failure(
                KurobeForgeActionResultPayload.ResultStatus.CORE_INCOMPATIBLE,
                "Genesis / Grade VII is reserved for future content"
            );
        }
        if (current.core() != null) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.SOCKET_OCCUPIED,
                "This weapon already contains a Monster Core");
        }
        if (KurobeForgeData.get(player.serverLevel()).certifiedGrade(playerId).ordinal()
            < current.grade().ordinal()) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.CERTIFICATION_REQUIRED,
                "Forge Certification is required for this weapon grade");
        }
        CoreSatchelData satchel = CoreSatchelData.get(player.serverLevel());
        if (!satchel.snapshot(playerId).access().acquired()) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.SATCHEL_REQUIRED,
                "A Core Satchel is required for socketing");
        }
        CoreSatchelData.CoreRecord selected = satchel.coreAt(playerId, request.coreSlot()).orElse(null);
        if (selected == null || !selected.coreId().equals(coreId)) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.CORE_NOT_FOUND,
                "The selected Core is no longer in that Satchel slot");
        }
        if (selected.tier() > KurobeForgeRules.maximumCoreTier(current.grade())) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.CORE_INCOMPATIBLE,
                "This weapon grade cannot support Core Tier " + roman(selected.tier()));
        }
        KurobeForgeData forgeData = KurobeForgeData.get(player.serverLevel());
        if (forgeData.binding(coreId) != null) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.CORE_BINDING_CONFLICT,
                "This exact Core identity is already bound to a weapon");
        }
        if (current.weaponId() != null && countWeaponIdentity(player.getInventory(), current.weaponId()) != 1) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.CORE_BINDING_CONFLICT,
                "Duplicate weapon identity detected; socketing was refused");
        }

        boolean tutorialFree = selected.tier() == 1 && forgeData.tutorialSocketAvailable(playerId);
        long price = tutorialFree ? 0L : KurobeForgeRules.socketPriceBronze(selected.tier());
        Optional<DungeonShopCurrency.PaymentPlan> payment = price == 0L
            ? Optional.empty()
            : DungeonShopCurrency.prepare(player, price);
        if (price > 0L && payment.isEmpty()) return insufficientFunds();

        ItemStack beforeWeapon = stack.copy();
        DungeonShopCurrency.DebitReceipt receipt = null;
        boolean coreTaken = false;
        boolean bound = false;
        boolean tutorialMarked = false;
        try {
            if (price > 0L) receipt = DungeonShopCurrency.debit(player, payment.orElseThrow());
            CoreSatchelData.TakeResult taken = satchel.takeAt(playerId, request.coreSlot(), coreId);
            if (taken.status() != CoreSatchelData.TakeStatus.TAKEN) {
                throw new IllegalStateException("Satchel Core changed during socket transaction");
            }
            coreTaken = true;
            KurobeForgeWeaponData.socket(stack, selected);
            UUID weaponId = KurobeForgeWeaponData.read(stack).weaponId();
            if (!forgeData.bindCore(coreId, playerId, weaponId)) {
                throw new IllegalStateException("Exact Core binding could not be claimed");
            }
            bound = true;
            if (tutorialFree) {
                if (!forgeData.markTutorialSocketUsed(playerId)) {
                    throw new IllegalStateException("Tutorial socket entitlement changed");
                }
                tutorialMarked = true;
            }
            player.getInventory().setChanged();
            return Result.success(tutorialFree
                ? "Tier I Monster Core socketed with the one-time tutorial service"
                : "Tier " + roman(selected.tier()) + " Monster Core socketed");
        } catch (DungeonShopCurrency.ChangeInventoryException failure) {
            return inventoryFull();
        } catch (RuntimeException failure) {
            KurobeForgeWeaponData.State mutated = KurobeForgeWeaponData.read(stack);
            if (tutorialMarked) forgeData.rollbackTutorialSocket(playerId);
            if (bound && mutated.weaponId() != null) forgeData.unbindCore(coreId, playerId, mutated.weaponId());
            if (coreTaken) satchel.storeAt(playerId, request.coreSlot(), selected);
            restoreWeapon(player, snapshot.weapon(request.weaponId()).inventorySlot(), beforeWeapon);
            if (receipt != null) receipt.rollback(player);
            return internalRollback("Core socket");
        }
    }

    private static Result remove(ServerPlayer player, ItemStack stack) {
        if (KurobeForgeSnapshotFactory.dungeonServicesLocked(player)) return dungeonActive();
        UUID playerId = player.getUUID();
        KurobeForgeWeaponData.State current = KurobeForgeWeaponData.read(stack);
        CoreSatchelData.CoreRecord core = current.core();
        if (core == null) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.NO_SOCKETED_CORE,
                "This weapon has no Monster Core to remove");
        }
        KurobeForgeData forgeData = KurobeForgeData.get(player.serverLevel());
        if (current.weaponId() == null
            || !forgeData.bindingMatches(core.coreId(), playerId, current.weaponId())
            || countWeaponIdentity(player.getInventory(), current.weaponId()) != 1) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.CORE_BINDING_CONFLICT,
                "Exact Core binding validation failed; no Core was granted");
        }
        CoreSatchelData satchel = CoreSatchelData.get(player.serverLevel());
        CoreSatchelData.SatchelSnapshot satchelSnapshot = satchel.snapshot(playerId);
        if (!satchelSnapshot.access().acquired()) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.SATCHEL_REQUIRED,
                "A Core Satchel is required for safe removal");
        }
        if (satchelSnapshot.cores().size() >= satchelSnapshot.access().capacity()) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.SATCHEL_FULL,
                "The Core Satchel has no free slot");
        }
        Optional<DungeonShopCurrency.PaymentPlan> payment = DungeonShopCurrency.prepare(
            player, KurobeForgeRules.removalPriceBronze(core.tier())
        );
        if (payment.isEmpty()) return insufficientFunds();

        ItemStack beforeWeapon = stack.copy();
        DungeonShopCurrency.DebitReceipt receipt = null;
        CoreSatchelData.StoreResult stored = null;
        boolean unbound = false;
        try {
            receipt = DungeonShopCurrency.debit(player, payment.orElseThrow());
            stored = satchel.storeFirstAvailable(playerId, core);
            if (stored.status() != CoreSatchelData.StoreStatus.STORED) {
                if (stored.status() == CoreSatchelData.StoreStatus.SATCHEL_FULL) {
                    receipt.rollback(player);
                    return failure(KurobeForgeActionResultPayload.ResultStatus.SATCHEL_FULL,
                        "The Core Satchel became full; no currency was charged");
                }
                throw new IllegalStateException("Exact Core could not enter the Satchel");
            }
            KurobeForgeWeaponData.removeCore(stack);
            if (!forgeData.unbindCore(core.coreId(), playerId, current.weaponId())) {
                throw new IllegalStateException("Exact Core binding could not be released");
            }
            unbound = true;
            player.getInventory().setChanged();
            return Result.success("Monster Core safely returned to Satchel slot " + (stored.slot() + 1));
        } catch (DungeonShopCurrency.ChangeInventoryException failure) {
            return inventoryFull();
        } catch (RuntimeException failure) {
            if (unbound) forgeData.bindCore(core.coreId(), playerId, current.weaponId());
            if (stored != null && stored.status() == CoreSatchelData.StoreStatus.STORED) {
                satchel.takeAt(playerId, stored.slot(), core.coreId());
            }
            restoreWeapon(player, findSlot(player.getInventory(), stack), beforeWeapon);
            if (receipt != null) receipt.rollback(player);
            return internalRollback("Safe removal");
        }
    }

    private static Result recharge(ServerPlayer player, ItemStack stack) {
        if (KurobeForgeSnapshotFactory.dungeonServicesLocked(player)) return dungeonActive();
        UUID playerId = player.getUUID();
        KurobeForgeWeaponData.State current = KurobeForgeWeaponData.read(stack);
        CoreSatchelData.CoreRecord core = current.core();
        if (core == null) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.NO_SOCKETED_CORE,
                "This weapon has no Monster Core to recharge");
        }
        KurobeForgeData forgeData = KurobeForgeData.get(player.serverLevel());
        if (current.weaponId() == null
            || !forgeData.bindingMatches(core.coreId(), playerId, current.weaponId())
            || countWeaponIdentity(player.getInventory(), current.weaponId()) != 1) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.CORE_BINDING_CONFLICT,
                "Exact Core binding validation failed; no charge was changed");
        }
        if (core.totalCharge() >= core.capacity()) {
            return failure(KurobeForgeActionResultPayload.ResultStatus.ALREADY_FULL,
                "This Monster Core is already fully charged");
        }
        long price = KurobeForgeRules.rechargePriceBronze(
            core.tier(), core.totalCharge(), core.capacity()
        );
        Optional<DungeonShopCurrency.PaymentPlan> payment = DungeonShopCurrency.prepare(player, price);
        if (payment.isEmpty()) return insufficientFunds();
        DungeonShopCurrency.DebitReceipt receipt = null;
        try {
            receipt = DungeonShopCurrency.debit(player, payment.orElseThrow());
            KurobeForgeWeaponData.recharge(stack);
            player.getInventory().setChanged();
            return Result.success("Monster Core rapidly recharged to full; Natural Charge was preserved");
        } catch (DungeonShopCurrency.ChangeInventoryException failure) {
            return inventoryFull();
        } catch (RuntimeException failure) {
            if (receipt != null) receipt.rollback(player);
            return internalRollback("Rapid recharge");
        }
    }

    private static List<Item> resolveMaterials(KurobeForgeSnapshotPayload.Grade target) {
        java.util.ArrayList<Item> items = new java.util.ArrayList<>();
        for (String id : KurobeForgeRules.certificationMaterials(target)) {
            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null);
            if (item == null) return null;
            items.add(item);
        }
        return List.copyOf(items);
    }

    private static boolean hasMaterials(Inventory inventory, List<Item> materials) {
        return materials.stream().allMatch(item -> inventory.countItem(item) >= 1);
    }

    private static void consumeMaterials(Inventory inventory, List<Item> materials) {
        for (Item item : materials) {
            boolean consumed = false;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.is(item)) {
                    stack.shrink(1);
                    consumed = true;
                    break;
                }
            }
            if (!consumed) throw new IllegalStateException("Certification material inventory changed");
        }
        inventory.setChanged();
    }

    private static int countWeaponIdentity(Inventory inventory, UUID weaponId) {
        if (weaponId == null) return 0;
        int count = 0;
        int end = Math.min(36, inventory.getContainerSize());
        for (int slot = 0; slot < end; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!KurobeForgeSnapshotFactory.eligible(candidate)) continue;
            if (weaponId.equals(KurobeForgeWeaponData.read(candidate).weaponId())) count++;
        }
        return count;
    }

    private static int findSlot(Inventory inventory, ItemStack target) {
        int end = Math.min(36, inventory.getContainerSize());
        for (int slot = 0; slot < end; slot++) {
            if (inventory.getItem(slot) == target) return slot;
        }
        return -1;
    }

    private static void restoreWeapon(ServerPlayer player, int slot, ItemStack before) {
        if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
            player.getInventory().setItem(slot, before.copy());
            player.getInventory().setChanged();
        }
    }

    static boolean claimRequest(UUID playerId, long requestId) {
        if (playerId == null || requestId <= 0L) return false;
        LinkedHashMap<Long, Boolean> seen = SEEN_REQUESTS.computeIfAbsent(
            playerId, ignored -> new LinkedHashMap<>()
        );
        if (seen.containsKey(requestId)) return false;
        seen.put(requestId, Boolean.TRUE);
        while (seen.size() > MAX_REPLAY_IDS_PER_PLAYER) {
            seen.remove(seen.keySet().iterator().next());
        }
        return true;
    }

    static void resetForTests() {
        synchronized (TRANSACTION_LOCK) {
            SEEN_REQUESTS.clear();
        }
    }

    private static Result insufficientFunds() {
        return failure(KurobeForgeActionResultPayload.ResultStatus.INSUFFICIENT_FUNDS,
            "Equivalent Bronze, Silver, Gold, or Stellar Gold payment is unavailable");
    }

    private static Result inventoryFull() {
        return failure(KurobeForgeActionResultPayload.ResultStatus.INVENTORY_FULL,
            "Make room for deterministic currency change before confirming");
    }

    private static Result dungeonActive() {
        return failure(KurobeForgeActionResultPayload.ResultStatus.DUNGEON_ACTIVE,
            "Socket, Remove, and Recharge are unavailable during an active dungeon run");
    }

    private static Result internalRollback(String operation) {
        return failure(KurobeForgeActionResultPayload.ResultStatus.INTERNAL_ERROR,
            operation + " rolled back without committing a partial transaction");
    }

    private static Result failure(KurobeForgeActionResultPayload.ResultStatus status, String message) {
        return Result.failure(status, message);
    }

    private static String roman(int rank) {
        return switch (rank) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> "UNREFINED";
        };
    }

    public record Result(KurobeForgeActionResultPayload.ResultStatus status, String message) {
        public Result {
            if (status == null) throw new IllegalArgumentException("Kurobe result status is required");
            message = message == null ? "" : message;
        }

        public boolean success() {
            return status == KurobeForgeActionResultPayload.ResultStatus.SUCCESS;
        }

        public static Result success(String message) {
            return new Result(KurobeForgeActionResultPayload.ResultStatus.SUCCESS, message);
        }

        public static Result failure(KurobeForgeActionResultPayload.ResultStatus status, String message) {
            return new Result(status, message);
        }
    }
}
