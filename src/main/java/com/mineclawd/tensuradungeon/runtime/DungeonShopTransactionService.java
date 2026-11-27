package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.network.DungeonShopActionRequestPayload;
import com.mineclawd.tensuradungeon.network.DungeonShopActionResultPayload;
import com.mineclawd.tensuradungeon.network.DungeonShopSnapshotPayload;
import com.mineclawd.tensuradungeon.reward.VanillaInventoryGrant;
import java.util.ArrayList;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DungeonShopTransactionService {
    private static final Object TRANSACTION_LOCK = new Object();
    private static final int MAX_REPLAY_IDS_PER_PLAYER = 128;
    private static final Map<UUID, LinkedHashMap<Long, Boolean>> SEEN_REQUESTS = new HashMap<>();

    private DungeonShopTransactionService() {}

    public static Result execute(
        ServerPlayer player,
        DungeonShopActionRequestPayload request,
        DungeonShopSnapshotPayload authoritativeSnapshot
    ) {
        if (player == null) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.INVALID_REQUEST,
                "Shop player is required"
            );
        }
        if (request == null) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.INVALID_REQUEST,
                "Shop action request is required"
            );
        }

        synchronized (TRANSACTION_LOCK) {
            if (!claimRequest(player.getUUID(), request.requestId())) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.REPLAYED_REQUEST,
                    "This Shop request was already processed"
                );
            }

            Result requestValidation = validateRequest(request, authoritativeSnapshot);
            if (!requestValidation.success()) {
                return requestValidation;
            }

            Product product = resolveProduct(player, request.offerId(), request.action());
            if (product == null || !product.supportsTab(request.tab())) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.UNGRANTABLE_PRODUCT,
                    "This Shop product is not currently grantable"
                );
            }

            Result productValidation = validateCurrentProduct(player, product);
            if (!productValidation.success()) {
                return productValidation;
            }

            if (product.kind() == ProductKind.BARTER) {
                return executeBarter(player, product.barterRecipe());
            }

            Optional<DungeonShopCurrency.PaymentPlan> payment =
                DungeonShopCurrency.prepare(player, product.entry().priceBronze());
            if (payment.isEmpty()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.INSUFFICIENT_FUNDS,
                    "Equivalent Bronze/Silver/Gold/Stellar Gold payment is unavailable"
                );
            }

            DungeonShopCurrency.DebitReceipt receipt = null;
            DungeonPotionShopData potionStock = null;
            DungeonPotionShopData.Reservation potionReservation = null;
            DungeonShopStockData configuredStock = null;
            DungeonShopStockData.Reservation configuredReservation = null;
            boolean satchelUpgraded = false;
            try {
                if (product.kind() == ProductKind.POTION) {
                    potionStock = DungeonPotionShopData.get(player.serverLevel());
                    DungeonPotionShopData.ReservationResult reservation = potionStock.reserve(
                        player.getUUID(),
                        product.entry().id()
                    );
                    if (reservation.status() != DungeonPotionShopData.ReservationStatus.RESERVED
                        && reservation.status() != DungeonPotionShopData.ReservationStatus.UNLIMITED) {
                        return mapPotionStockFailure(reservation.status());
                    }
                    potionReservation = reservation.reservation();
                } else if (product.kind() == ProductKind.ESSENTIAL
                    || product.kind() == ProductKind.DAILY_ITEM) {
                    configuredStock = DungeonShopStockData.get(player.serverLevel());
                    DungeonShopStockData.ReservationResult reservation =
                        configuredStock.reserveDaily(
                            player.getUUID(), currentDayKey(), product.entry().id()
                        );
                    if (reservation.status() != DungeonShopStockData.ReservationStatus.RESERVED
                        && reservation.status() != DungeonShopStockData.ReservationStatus.UNLIMITED) {
                        return mapConfiguredStockFailure(reservation.status(), false);
                    }
                    configuredReservation = reservation.reservation();
                } else if (product.kind() == ProductKind.WEEKLY_ITEM) {
                    configuredStock = DungeonShopStockData.get(player.serverLevel());
                    DungeonShopStockData.ReservationResult reservation =
                        configuredStock.reserveWeekly(
                            player.getUUID(), currentWeekKey(), product.entry().id()
                        );
                    if (reservation.status() != DungeonShopStockData.ReservationStatus.RESERVED) {
                        return mapConfiguredStockFailure(reservation.status(), true);
                    }
                    configuredReservation = reservation.reservation();
                }
                receipt = DungeonShopCurrency.debit(player, payment.orElseThrow());
                if (product.kind() == ProductKind.SATCHEL) {
                    CoreSatchelData satchels = CoreSatchelData.get(player.serverLevel());
                    CoreSatchelData.UpgradeStatus upgrade = satchels.upgradeTo(
                        player.getUUID(),
                        product.satchelGrade()
                    );
                    if (upgrade != CoreSatchelData.UpgradeStatus.UPGRADED) {
                        receipt.rollback(player);
                        return mapUpgradeFailure(upgrade);
                    }
                    satchelUpgraded = true;
                    CoreSatchelShopData.PurchaseStateStatus purchaseState =
                        CoreSatchelShopData.get(player.serverLevel()).recordPurchased(
                            player.getUUID(),
                            product.satchelGrade(),
                            currentDayKey()
                        );
                    if (purchaseState != CoreSatchelShopData.PurchaseStateStatus.PURCHASE_RECORDED) {
                        satchels.rollbackUpgrade(player.getUUID(), product.satchelGrade());
                        receipt.rollback(player);
                        return mapShopStateFailure(purchaseState);
                    }
                    return Result.success("Core Satchel upgrade purchased");
                }

                if (product.kind() == ProductKind.ESSENTIAL
                    || product.kind() == ProductKind.DAILY_ITEM
                    || product.kind() == ProductKind.WEEKLY_ITEM
                    || product.kind() == ProductKind.POTION) {
                    Item item = resolveGrantItem(product);
                    if (item == null) {
                        if (configuredStock != null) {
                            configuredStock.rollback(configuredReservation);
                        }
                        if (potionStock != null) {
                            potionStock.rollback(potionReservation);
                        }
                        receipt.rollback(player);
                        return Result.failure(
                            DungeonShopActionResultPayload.ResultStatus.UNGRANTABLE_PRODUCT,
                            "This configured Shop item is not registered"
                        );
                    }
                    VanillaInventoryGrant.INSTANCE.grant(
                        player, grantStacks(item, product.entry().outputCount())
                    );
                    return Result.success(product.entry().name() + " purchased");
                }

                DungeonCosmeticData.MutationResult grant = DungeonCosmeticData.get(player.serverLevel()).grant(
                    player.getUUID(),
                    product.cosmetic()
                );
                if (grant.status() != DungeonCosmeticData.MutationStatus.GRANTED) {
                    receipt.rollback(player);
                    return mapCosmeticFailure(grant.status());
                }
                return Result.success(product.entry().name() + " unlocked");
            } catch (DungeonShopCurrency.ChangeInventoryException failure) {
                if (potionStock != null) {
                    potionStock.rollback(potionReservation);
                }
                if (configuredStock != null) {
                    configuredStock.rollback(configuredReservation);
                }
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.INVENTORY_FULL,
                    "Make room for currency change before purchasing this offer"
                );
            } catch (RuntimeException failure) {
                if (satchelUpgraded) {
                    CoreSatchelData.get(player.serverLevel()).rollbackUpgrade(
                        player.getUUID(),
                        product.satchelGrade()
                    );
                }
                if (potionStock != null) {
                    potionStock.rollback(potionReservation);
                }
                if (configuredStock != null) {
                    configuredStock.rollback(configuredReservation);
                }
                if (receipt != null) {
                    receipt.rollback(player);
                }
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.INTERNAL_ERROR,
                    "Shop transaction could not be completed"
                );
            }
        }
    }

    static Result validateRequest(
        DungeonShopActionRequestPayload request,
        DungeonShopSnapshotPayload snapshot
    ) {
        if (request == null || snapshot == null) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.INVALID_REQUEST,
                "Shop action context is incomplete"
            );
        }
        if (!request.supportedSchema() || !snapshot.supportedSchema()) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.INVALID_REQUEST,
                "Shop protocol version is unsupported"
            );
        }
        if (request.action() != DungeonShopActionRequestPayload.Action.PURCHASE
            && request.action() != DungeonShopActionRequestPayload.Action.BARTER) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.UNSUPPORTED_ACTION,
                "This Shop action is not supported"
            );
        }
        if (request.tab() != snapshot.activeTab()) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.INVALID_REQUEST,
                "Shop tab does not match the authoritative snapshot"
            );
        }
        if (request.snapshotRevision() != snapshot.revision()) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.STALE_REVISION,
                "The Shop changed; reopen the current offer"
            );
        }
        if ("CLOCK_STALE".equals(snapshot.status().code())) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.CLOCK_STALE,
                "Shop rotation time is stale; offers are temporarily hidden"
            );
        }

        DungeonShopSnapshotPayload.Offer offer = snapshot.offers().stream()
            .filter(candidate -> candidate.id().equals(request.offerId()))
            .findFirst()
            .orElse(null);
        if (offer == null) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.OFFER_NOT_FOUND,
                "That Shop offer is not in the current rotation"
            );
        }
        if (request.action() == DungeonShopActionRequestPayload.Action.BARTER) {
            if (offer.routing().action() != DungeonShopSnapshotPayload.Action.BARTER) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.UNSUPPORTED_ACTION,
                    "This offer is not a barter recipe"
                );
            }
            if (offer.availability().state() == DungeonShopSnapshotPayload.AvailabilityState.AVAILABLE
                || offer.availability().state() == DungeonShopSnapshotPayload.AvailabilityState.LOCKED) {
                return Result.success("Barter recipe accepted");
            }
        }

        return switch (offer.availability().state()) {
            case AVAILABLE -> offer.routing().action() == DungeonShopSnapshotPayload.Action.PURCHASE
                && request.action() == DungeonShopActionRequestPayload.Action.PURCHASE
                ? Result.success("Offer accepted")
                : Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.UNSUPPORTED_ACTION,
                    "This offer is not a matching Shop action"
                );
            case OWNED, PURCHASED -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.ALREADY_OWNED,
                "This Shop offer is already owned"
            );
            case SOLD_OUT -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.SOLD_OUT,
                "This Shop offer is sold out"
            );
            case LOCKED -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.LOCKED_REQUIREMENT,
                "Shop requirements are not met"
            );
            case VENDOR_ROUTED -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.UNSUPPORTED_ACTION,
                "This entry is owned by its vendor"
            );
            case DISPLAY_ONLY -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.UNGRANTABLE_PRODUCT,
                "This Shop entry is display-only"
            );
        };
    }

    static void resetForTests() {
        synchronized (TRANSACTION_LOCK) {
            SEEN_REQUESTS.clear();
        }
    }

    private static Result executeBarter(
        ServerPlayer player,
        DungeonPotionBarterCatalog.Recipe recipe
    ) {
        List<ItemStack> before = snapshot(player.getInventory());
        try {
            String missing = DungeonPotionBarterCatalog.missingMessage(player, recipe);
            if (!missing.isBlank()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.MISSING_INGREDIENTS,
                    missing
                );
            }
            Item output = DungeonPotionBarterCatalog.resolveOutput(recipe);
            if (output == null) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.UNGRANTABLE_PRODUCT,
                    "This barter output is not registered on the server"
                );
            }
            List<ItemStack> outputStacks = grantStacks(output, recipe.outputCount());
            if (!VanillaInventoryGrant.INSTANCE.canGrant(player, outputStacks)) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.INVENTORY_FULL,
                    "Make room in your inventory before completing this barter"
                );
            }
            DungeonPotionBarterCatalog.consume(player, recipe);
            VanillaInventoryGrant.INSTANCE.grant(player, outputStacks);
            return Result.success(recipe.outputName() + " created by barter");
        } catch (RuntimeException failure) {
            restore(player.getInventory(), before);
            String missing = DungeonPotionBarterCatalog.missingMessage(player, recipe);
            if (!missing.isBlank()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.MISSING_INGREDIENTS,
                    missing
                );
            }
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.INTERNAL_ERROR,
                "Barter could not be completed; no ingredients were consumed"
            );
        }
    }

    private static Result validateCurrentProduct(ServerPlayer player, Product product) {
        UUID playerId = player.getUUID();
        if (product.kind() == ProductKind.BARTER) {
            DungeonPotionBarterCatalog.Recipe recipe = product.barterRecipe();
            Item output = DungeonPotionBarterCatalog.resolveOutput(recipe);
            if (output == null) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.UNGRANTABLE_PRODUCT,
                    "This barter output is not registered on the server"
                );
            }
            String missing = DungeonPotionBarterCatalog.missingMessage(player, recipe);
            if (!missing.isBlank()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.MISSING_INGREDIENTS,
                    missing
                );
            }
            if (!VanillaInventoryGrant.INSTANCE.canGrant(
                player,
                grantStacks(output, recipe.outputCount())
            )) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.INVENTORY_FULL,
                    "Make room in your inventory before completing this barter"
                );
            }
            return Result.success("Barter ingredients are available");
        }
        if (product.kind() == ProductKind.SATCHEL) {
            CoreSatchelData satchels = CoreSatchelData.get(player.serverLevel());
            Optional<CoreSatchelData.SatchelGrade> current = satchels.grade(playerId);
            if (current.orElse(null) == product.satchelGrade()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.ALREADY_OWNED,
                    "This Core Satchel grade is already owned"
                );
            }
            int highestFloor = DungeonFloorProgressData.get(player.serverLevel())
                .getHighestCompletedFloor(playerId);
            if (satchels.nextEligibleUpgrade(playerId, highestFloor)
                .filter(product.satchelGrade()::equals)
                .isEmpty()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.LOCKED_REQUIREMENT,
                    "Core Satchel floor or prior-grade requirements are not met"
                );
            }
            CoreSatchelShopData.OfferSnapshot shop = CoreSatchelShopData.get(player.serverLevel())
                .snapshot(playerId);
            if (shop.purchased()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.ALREADY_OWNED,
                    "This Core Satchel offer was already purchased"
                );
            }
            if (shop.offeredGrade() != product.satchelGrade()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.OFFER_EXPIRED,
                    "The Core Satchel offer is no longer current"
                );
            }
            if (shop.dayKey() > currentDayKey()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.CLOCK_STALE,
                    "Shop rotation time is stale; offers are temporarily hidden"
                );
            }
            return Result.success("Core Satchel offer is current");
        }

        int highestFloor = DungeonFloorProgressData.get(player.serverLevel())
            .getHighestCompletedFloor(playerId);
        int accessibleFloor = Math.max(1, highestFloor + 1);
        DungeonShopCatalog.Entry current = DungeonShopCatalog.entryById(
            product.entry().id(), highestFloor
        );
        if (current == null || current.requiredFloor() > accessibleFloor) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.LOCKED_REQUIREMENT,
                "This configured Shop offer is not unlocked"
            );
        }

        if (product.kind() == ProductKind.ESSENTIAL
            || product.kind() == ProductKind.DAILY_ITEM) {
            DungeonShopStockData.DailySnapshot stock = DungeonShopStockData.get(
                player.serverLevel()
            ).dailySnapshot(playerId);
            if (stock.dayKey() != currentDayKey()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.OFFER_EXPIRED,
                    "The Daily Shop stock is no longer current"
                );
            }
            if (product.kind() == ProductKind.DAILY_ITEM
                && !stock.todaysOffers().contains(product.entry().id())) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.OFFER_EXPIRED,
                    "This Today's Finds offer is not in the current rotation"
                );
            }
            int quantity = stock.quantities().getOrDefault(product.entry().id(), 0);
            if (!product.entry().unlimited() && quantity <= 0) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.SOLD_OUT,
                    "This Daily Shop item is sold out"
                );
            }
            return validateItemGrant(player, product, "configured Daily item");
        }

        if (product.kind() == ProductKind.POTION) {
            DungeonPotionShopData.PotionSnapshot stock = DungeonPotionShopData.get(
                player.serverLevel()
            ).snapshot(playerId);
            int quantity = stock.quantities().getOrDefault(product.entry().id(), 0);
            if (stock.dayKey() != currentDayKey()) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.OFFER_EXPIRED,
                    "The Daily potion stock is no longer current"
                );
            }
            if (!product.entry().unlimited() && quantity <= 0) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.SOLD_OUT,
                    "This recovery potion is sold out for today"
                );
            }
            return validateItemGrant(player, product, "recovery potion");
        }

        DungeonShopStockData.WeeklySnapshot weekly = DungeonShopStockData.get(player.serverLevel())
            .snapshot(playerId);
        if (weekly.weekKey() > currentWeekKey()) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.CLOCK_STALE,
                "Shop rotation time is stale; offers are temporarily hidden"
            );
        }
        if (weekly.weekKey() != currentWeekKey()
            || !weekly.offers().contains(product.entry().id())) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.OFFER_EXPIRED,
                "The Weekly offer is no longer in the current rotation"
            );
        }
        if (product.kind() == ProductKind.WEEKLY_ITEM) {
            if (weekly.purchasedOffers().contains(product.entry().id())) {
                return Result.failure(
                    DungeonShopActionResultPayload.ResultStatus.ALREADY_OWNED,
                    "This Weekly item was already purchased"
                );
            }
            return validateItemGrant(player, product, "Weekly item");
        }

        DungeonCosmeticData.Snapshot cosmetics = DungeonCosmeticData.get(player.serverLevel())
            .snapshot(playerId);
        if (cosmetics.owned().contains(product.cosmetic())) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.ALREADY_OWNED,
                "This HUD theme is already owned"
            );
        }
        return Result.success("HUD theme offer is current");
    }

    private static Result validateItemGrant(
        ServerPlayer player,
        Product product,
        String label
    ) {
        Item item = resolveGrantItem(product);
        if (item == null) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.UNGRANTABLE_PRODUCT,
                "This " + label + " is not registered"
            );
        }
        if (!VanillaInventoryGrant.INSTANCE.canGrant(
            player,
            grantStacks(item, product.entry().outputCount())
        )) {
            return Result.failure(
                DungeonShopActionResultPayload.ResultStatus.INVENTORY_FULL,
                "Make room in your inventory before purchasing this offer"
            );
        }
        return Result.success("Configured Shop item is grantable");
    }

    private static Product resolveProduct(
        ServerPlayer player,
        String offerId,
        DungeonShopActionRequestPayload.Action action
    ) {
        if (action == DungeonShopActionRequestPayload.Action.BARTER) {
            DungeonPotionBarterCatalog.Recipe recipe = DungeonPotionBarterCatalog.byId(offerId);
            return recipe == null
                ? null
                : new Product(ProductKind.BARTER, null, null, null, recipe);
        }
        int highestFloor = DungeonFloorProgressData.get(player.serverLevel())
            .getHighestCompletedFloor(player.getUUID());
        DungeonShopCatalog.Entry entry = DungeonShopCatalog.entryById(offerId, highestFloor);
        if (entry == null) {
            return null;
        }
        return switch (entry.productType()) {
            case ITEM -> new Product(
                entry.category() == DungeonShopCatalog.Category.WEEKLY
                    ? ProductKind.WEEKLY_ITEM
                    : entry.category() == DungeonShopCatalog.Category.TODAYS_FINDS
                        ? ProductKind.DAILY_ITEM
                        : ProductKind.ESSENTIAL,
                entry,
                null,
                null,
                null
            );
            case RECOVERY_POTION -> new Product(
                ProductKind.POTION, entry, null, null, null
            );
            case SATCHEL_UPGRADE -> new Product(
                ProductKind.SATCHEL, entry, null, entry.satchelGrade(), null
            );
            case HUD_THEME -> new Product(
                ProductKind.HUD_THEME, entry, entry.cosmetic(), null, null
            );
        };
    }

    static boolean claimRequest(UUID playerId, long requestId) {
        LinkedHashMap<Long, Boolean> requests = SEEN_REQUESTS.computeIfAbsent(
            playerId,
            ignored -> new LinkedHashMap<>(MAX_REPLAY_IDS_PER_PLAYER + 1, 0.75F, false)
        );
        if (requests.containsKey(requestId)) {
            return false;
        }
        requests.put(requestId, Boolean.TRUE);
        while (requests.size() > MAX_REPLAY_IDS_PER_PLAYER) {
            requests.remove(requests.keySet().iterator().next());
        }
        return true;
    }

    private static Result mapUpgradeFailure(CoreSatchelData.UpgradeStatus status) {
        return switch (status) {
            case ALREADY_OWNED -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.ALREADY_OWNED,
                "This Core Satchel grade is already owned"
            );
            case SATCHEL_NOT_OWNED, NOT_NEXT_GRADE -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.LOCKED_REQUIREMENT,
                "Core Satchel requirements are not met"
            );
            case UPGRADED -> Result.success("Core Satchel upgrade purchased");
        };
    }

    private static Result mapShopStateFailure(CoreSatchelShopData.PurchaseStateStatus status) {
        return switch (status) {
            case ALREADY_PURCHASED -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.ALREADY_OWNED,
                "This Core Satchel offer was already purchased"
            );
            case NO_OFFER, STALE_DAY, GRADE_MISMATCH, OFFER_EXPIRED -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.OFFER_EXPIRED,
                "The Core Satchel offer is no longer current"
            );
            case PURCHASE_RECORDED -> Result.success("Core Satchel upgrade purchased");
        };
    }

    private static Result mapCosmeticFailure(DungeonCosmeticData.MutationStatus status) {
        return switch (status) {
            case ALREADY_OWNED -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.ALREADY_OWNED,
                "This HUD theme is already owned"
            );
            case UNKNOWN_COSMETIC -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.UNGRANTABLE_PRODUCT,
                "This HUD theme is not grantable"
            );
            case GRANTED -> Result.success("HUD theme unlocked");
            case EQUIPPED, NO_CHANGE, NOT_OWNED, STALE_REVISION -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.INTERNAL_ERROR,
                "HUD theme grant returned an unexpected status"
            );
        };
    }

    private static Result mapPotionStockFailure(
        DungeonPotionShopData.ReservationStatus status
    ) {
        return switch (status) {
            case SOLD_OUT -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.SOLD_OUT,
                "This recovery potion is sold out for today"
            );
            case NO_DAY -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.OFFER_EXPIRED,
                "The Daily potion stock has expired"
            );
            case NOT_FOUND -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.UNGRANTABLE_PRODUCT,
                "This recovery potion is not in the approved catalog"
            );
            case RESERVED, UNLIMITED -> Result.success("Potion stock reserved");
        };
    }

    private static Result mapConfiguredStockFailure(
        DungeonShopStockData.ReservationStatus status,
        boolean weekly
    ) {
        return switch (status) {
            case SOLD_OUT -> Result.failure(
                weekly
                    ? DungeonShopActionResultPayload.ResultStatus.ALREADY_OWNED
                    : DungeonShopActionResultPayload.ResultStatus.SOLD_OUT,
                weekly
                    ? "This Weekly item was already purchased"
                    : "This Daily item is sold out"
            );
            case NO_PERIOD -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.OFFER_EXPIRED,
                "This configured Shop rotation has expired"
            );
            case NOT_FOUND -> Result.failure(
                DungeonShopActionResultPayload.ResultStatus.UNGRANTABLE_PRODUCT,
                "This item is no longer in the configured Shop catalog"
            );
            case RESERVED, UNLIMITED -> Result.success("Configured stock reserved");
        };
    }

    private static Item resolveGrantItem(Product product) {
        if (product == null || product.entry() == null) {
            return null;
        }
        return product.entry().itemIds().stream()
            .map(DungeonPotionBarterCatalog::resolve)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private static long currentDayKey() {
        return LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }

    private static long currentWeekKey() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
    }

    private enum ProductKind {
        ESSENTIAL,
        DAILY_ITEM,
        POTION,
        SATCHEL,
        HUD_THEME,
        WEEKLY_ITEM,
        BARTER
    }

    private record Product(
        ProductKind kind,
        DungeonShopCatalog.Entry entry,
        CosmeticKey cosmetic,
        CoreSatchelData.SatchelGrade satchelGrade,
        DungeonPotionBarterCatalog.Recipe barterRecipe
    ) {
        boolean supportsTab(DungeonShopSnapshotPayload.Tab tab) {
            return switch (kind) {
                case ESSENTIAL, DAILY_ITEM, POTION, SATCHEL ->
                    tab == DungeonShopSnapshotPayload.Tab.DAILY;
                case HUD_THEME, WEEKLY_ITEM -> tab == DungeonShopSnapshotPayload.Tab.WEEKLY;
                case BARTER -> tab == DungeonShopSnapshotPayload.Tab.BARTER;
            };
        }
    }

    public record Result(DungeonShopActionResultPayload.ResultStatus status, String message) {
        public Result {
            Objects.requireNonNull(status, "Shop transaction status");
            message = message == null ? "" : message;
        }

        public boolean success() {
            return status == DungeonShopActionResultPayload.ResultStatus.SUCCESS;
        }

        static Result success(String message) {
            return new Result(DungeonShopActionResultPayload.ResultStatus.SUCCESS, message);
        }

        static Result failure(DungeonShopActionResultPayload.ResultStatus status, String message) {
            return new Result(status, message);
        }
    }

    private static List<ItemStack> snapshot(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            result.add(inventory.getItem(slot).copy());
        }
        return result;
    }

    private static List<ItemStack> grantStacks(Item item, int count) {
        if (item == null || count <= 0) {
            return List.of();
        }
        int maximum = Math.max(1, item.getDefaultInstance().getMaxStackSize());
        List<ItemStack> stacks = new ArrayList<>((count + maximum - 1) / maximum);
        int remaining = count;
        while (remaining > 0) {
            int stackCount = Math.min(maximum, remaining);
            stacks.add(new ItemStack(item, stackCount));
            remaining -= stackCount;
        }
        return List.copyOf(stacks);
    }

    private static void restore(Inventory inventory, List<ItemStack> snapshot) {
        for (int slot = 0; slot < snapshot.size(); slot++) {
            inventory.setItem(slot, snapshot.get(slot).copy());
        }
        inventory.setChanged();
    }
}
