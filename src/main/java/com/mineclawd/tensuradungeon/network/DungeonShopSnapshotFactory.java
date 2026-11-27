package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.runtime.CoreSatchelData;
import com.mineclawd.tensuradungeon.runtime.CoreSatchelShopData;
import com.mineclawd.tensuradungeon.runtime.CosmeticKey;
import com.mineclawd.tensuradungeon.runtime.DungeonCosmeticData;
import com.mineclawd.tensuradungeon.runtime.DungeonBankData;
import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.DungeonShopCatalog;
import com.mineclawd.tensuradungeon.runtime.DungeonShopStockData;
import com.mineclawd.tensuradungeon.runtime.DungeonPotionShopData;
import com.mineclawd.tensuradungeon.runtime.DungeonPotionBarterCatalog;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonShopSnapshotFactory {
    private static final DateTimeFormatter RESET_FORMAT =
        DateTimeFormatter.ofPattern("MMM d, h:mm a z", Locale.ROOT);
    private static final DungeonShopSnapshotPayload.Status READY =
            new DungeonShopSnapshotPayload.Status(
                "READY",
                "Supported purchases and potion barters are enabled; Bracelet and Whistle supply is unlimited per player."
            );
    private static final DungeonShopSnapshotPayload.Status STALE_CLOCK =
        new DungeonShopSnapshotPayload.Status(
            "CLOCK_STALE",
            "Server time predates persisted Shop stock; offers are hidden without rerolling."
        );

    private DungeonShopSnapshotFactory() {}

    public static void sendOpen(ServerPlayer player) {
        PacketDistributor.sendToPlayer(
            Objects.requireNonNull(player, "Dungeon Shop player"),
            create(player, DungeonShopSnapshotPayload.Tab.DAILY, 0L)
        );
    }

    static DungeonShopSnapshotPayload create(
        ServerPlayer player,
        DungeonShopSnapshotPayload.Tab tab,
        long requestId
    ) {
        return create(player, tab, requestId, READY);
    }

    static DungeonShopSnapshotPayload create(
        ServerPlayer player,
        DungeonShopSnapshotPayload.Tab tab,
        long requestId,
        DungeonShopSnapshotPayload.Status requestedStatus
    ) {
        Objects.requireNonNull(player, "Dungeon Shop player");
        Objects.requireNonNull(tab, "Dungeon Shop tab");
        Objects.requireNonNull(requestedStatus, "Dungeon Shop snapshot status");
        ServerLevel level = player.serverLevel();
        RotationTime rotation = RotationTime.at(ZonedDateTime.now(ZoneId.systemDefault()));
        UUID playerId = player.getUUID();
        var bank = DungeonBankData.get(level).snapshot(playerId).balance();
        DungeonShopSnapshotPayload.Wallet wallet = new DungeonShopSnapshotPayload.Wallet(
            Math.addExact(
                DungeonUiSnapshotFactory.countItem(player, "tensura:bronze_coin"), bank.bronze()
            ),
            Math.addExact(
                DungeonUiSnapshotFactory.countItem(player, "tensura:silver_coin"), bank.silver()
            ),
            Math.addExact(
                DungeonUiSnapshotFactory.countItem(player, "tensura:gold_coin"), bank.gold()
            ),
            Math.addExact(
                DungeonUiSnapshotFactory.countItem(player, "tensura:stellar_gold_coin"),
                bank.stellarGold()
            )
        );

        DungeonCosmeticData.Snapshot cosmetics =
            DungeonCosmeticData.get(level).snapshot(playerId);
        CoreSatchelShopData dailyData = CoreSatchelShopData.get(level);
        DungeonShopStockData stockData = DungeonShopStockData.get(level);
        DungeonPotionShopData potionData = DungeonPotionShopData.get(level);
        CoreSatchelShopData.OfferSnapshot priorDaily = dailyData.snapshot(playerId);
        DungeonShopStockData.DailySnapshot priorEssentials =
            stockData.dailySnapshot(playerId);
        DungeonPotionShopData.PotionSnapshot priorPotions = potionData.snapshot(playerId);
        DungeonShopStockData.WeeklySnapshot priorWeekly = stockData.snapshot(playerId);
        boolean stale = clockIsStale(
            rotation, priorDaily, priorEssentials, priorWeekly, priorPotions
        );
        CoreSatchelShopData.OfferSnapshot daily = priorDaily;
        DungeonShopStockData.DailySnapshot essentials = priorEssentials;
        DungeonPotionShopData.PotionSnapshot potions = priorPotions;
        DungeonShopStockData.WeeklySnapshot weekly = priorWeekly;
        int highestCompletedFloor = 0;
        if (!stale) {
            highestCompletedFloor =
                DungeonFloorProgressData.get(level).getHighestCompletedFloor(playerId);
            daily = dailyData.refresh(
                playerId,
                CoreSatchelData.get(level),
                highestCompletedFloor,
                rotation.dayKey()
            ).snapshot();
            essentials = stockData.refreshDaily(
                playerId,
                rotation.dayKey(),
                highestCompletedFloor
            ).snapshot();
            potions = potionData.refresh(playerId, rotation.dayKey()).snapshot();
            weekly = stockData.refreshWeekly(
                playerId,
                rotation.weekKey(),
                Set.copyOf(cosmetics.owned())
            ).snapshot();
        }
        List<DungeonShopSnapshotPayload.Offer> offers = stale
            ? List.of()
            : projectOffers(
                tab,
                daily,
                essentials,
                potions,
                weekly,
                Set.copyOf(cosmetics.owned()),
                highestCompletedFloor,
                player
            );
        DungeonShopSnapshotPayload.Status status = stale ? STALE_CLOCK : requestedStatus;
        DungeonShopSnapshotPayload.ResetWindow resets = rotation.resets();
        long revision = Integer.toUnsignedLong(Objects.hash(tab, resets, wallet, offers));
        return new DungeonShopSnapshotPayload(
            DungeonShopSnapshotPayload.CURRENT_SCHEMA,
            revision,
            requestId,
            true,
            tab,
            resets,
            wallet,
            offers,
            status
        );
    }

    static DungeonShopSnapshotPayload.Status resultStatus(
        DungeonShopActionResultPayload.ResultStatus result,
        String message
    ) {
        Objects.requireNonNull(result, "Dungeon Shop action result");
        return new DungeonShopSnapshotPayload.Status(result.name(), message == null ? "" : message);
    }

    static List<DungeonShopSnapshotPayload.Offer> projectOffers(
        DungeonShopSnapshotPayload.Tab tab,
        CoreSatchelShopData.OfferSnapshot daily,
        DungeonShopStockData.DailySnapshot essentials,
        DungeonShopStockData.WeeklySnapshot weekly,
        Set<CosmeticKey> ownedCosmetics
    ) {
        return projectOffers(
            tab,
            daily,
            essentials,
            null,
            weekly,
            ownedCosmetics,
            0,
            null
        );
    }

    static List<DungeonShopSnapshotPayload.Offer> projectOffers(
        DungeonShopSnapshotPayload.Tab tab,
        CoreSatchelShopData.OfferSnapshot daily,
        DungeonShopStockData.DailySnapshot essentials,
        DungeonPotionShopData.PotionSnapshot potions,
        DungeonShopStockData.WeeklySnapshot weekly,
        Set<CosmeticKey> ownedCosmetics,
        int highestCompletedFloor
    ) {
        return projectOffers(
            tab,
            daily,
            essentials,
            potions,
            weekly,
            ownedCosmetics,
            highestCompletedFloor,
            null
        );
    }

    static List<DungeonShopSnapshotPayload.Offer> projectOffers(
        DungeonShopSnapshotPayload.Tab tab,
        CoreSatchelShopData.OfferSnapshot daily,
        DungeonShopStockData.DailySnapshot essentials,
        DungeonPotionShopData.PotionSnapshot potions,
        DungeonShopStockData.WeeklySnapshot weekly,
        Set<CosmeticKey> ownedCosmetics,
        int highestCompletedFloor,
        ServerPlayer player
    ) {
        Objects.requireNonNull(tab, "Dungeon Shop projection tab");
        Set<CosmeticKey> owned = ownedCosmetics == null ? Set.of() : Set.copyOf(ownedCosmetics);
        return switch (tab) {
            case DAILY -> dailyOffers(daily, essentials, potions, highestCompletedFloor);
            case WEEKLY -> weeklyOffers(weekly, owned);
            case BARTER -> barterOffers(player);
        };
    }

    static boolean clockIsStale(
        RotationTime rotation,
        CoreSatchelShopData.OfferSnapshot daily,
        DungeonShopStockData.DailySnapshot essentials,
        DungeonShopStockData.WeeklySnapshot weekly
    ) {
        return daily != null && daily.dayKey() > rotation.dayKey()
            || essentials != null && essentials.dayKey() > rotation.dayKey()
            || weekly != null && weekly.weekKey() > rotation.weekKey();
    }

    static boolean clockIsStale(
        RotationTime rotation,
        CoreSatchelShopData.OfferSnapshot daily,
        DungeonShopStockData.DailySnapshot essentials,
        DungeonShopStockData.WeeklySnapshot weekly,
        DungeonPotionShopData.PotionSnapshot potions
    ) {
        return clockIsStale(rotation, daily, essentials, weekly)
            || potions != null && potions.dayKey() > rotation.dayKey();
    }

    private static List<DungeonShopSnapshotPayload.Offer> dailyOffers(
        CoreSatchelShopData.OfferSnapshot satchel,
        DungeonShopStockData.DailySnapshot essentials
    ) {
        return dailyOffers(satchel, essentials, null, 0);
    }

    private static List<DungeonShopSnapshotPayload.Offer> dailyOffers(
        CoreSatchelShopData.OfferSnapshot satchel,
        DungeonShopStockData.DailySnapshot essentials,
        DungeonPotionShopData.PotionSnapshot potions,
        int highestCompletedFloor
    ) {
        List<DungeonShopSnapshotPayload.Offer> offers = new ArrayList<>();
        if (essentials != null) {
            for (DungeonShopCatalog.Entry entry
                : DungeonShopCatalog.essentialSupplies(highestCompletedFloor)) {
                Integer quantity = essentials.quantities().get(entry.id());
                if (quantity != null) {
                    offers.add(dailyItemOffer(
                        entry, DungeonShopSnapshotPayload.Section.ESSENTIAL, quantity
                    ));
                }
            }
        }
        if (satchel != null && satchel.offered()
            && DungeonShopCatalog.hasSatchelUpgrade(satchel.offeredGrade())) {
            offers.add(satchelOffer(satchel));
        }
        if (essentials != null) {
            for (DungeonShopCatalog.Entry entry
                : DungeonShopCatalog.todaysFinds(highestCompletedFloor)) {
                if (entry.productType() != DungeonShopCatalog.ProductType.ITEM) {
                    continue;
                }
                if (!essentials.todaysOffers().contains(entry.id())) {
                    continue;
                }
                Integer quantity = essentials.quantities().get(entry.id());
                if (quantity != null) {
                    offers.add(dailyItemOffer(
                        entry, DungeonShopSnapshotPayload.Section.TIMED, quantity
                    ));
                }
            }
        }
        if (potions != null) {
            for (DungeonShopCatalog.Entry entry : DungeonShopCatalog.potionSupplies(
                highestCompletedFloor
            )) {
                Integer quantity = potions.quantities().get(entry.id());
                if (quantity == null) {
                    continue;
                }
                boolean unlimited = DungeonShopCatalog.potionIsUnlimited(entry.id());
                boolean available = unlimited || quantity > 0;
                offers.add(statefulOffer(
                    entry,
                    null,
                    DungeonShopSnapshotPayload.Tab.DAILY,
                    DungeonShopSnapshotPayload.Section.ESSENTIAL,
                    entry.icon(),
                    quantity,
                    unlimited,
                    available
                        ? DungeonShopSnapshotPayload.AvailabilityState.AVAILABLE
                        : DungeonShopSnapshotPayload.AvailabilityState.SOLD_OUT,
                    available
                        ? DungeonShopSnapshotPayload.Action.PURCHASE
                        : DungeonShopSnapshotPayload.Action.NONE,
                    available ? "PURCHASE" : "SOLD OUT",
                    unlimited
                        ? "Unlimited per-player supply."
                        : quantity + " remaining for this server day."
                ));
            }
        }
        return List.copyOf(offers);
    }

    private static DungeonShopSnapshotPayload.Offer dailyItemOffer(
        DungeonShopCatalog.Entry entry,
        DungeonShopSnapshotPayload.Section section,
        int quantity
    ) {
        boolean available = entry.unlimited() || quantity > 0;
        return statefulOffer(
            entry,
            entry.cosmetic(),
            DungeonShopSnapshotPayload.Tab.DAILY,
            section,
            entry.icon(),
            quantity,
            entry.unlimited(),
            available
                ? DungeonShopSnapshotPayload.AvailabilityState.AVAILABLE
                : DungeonShopSnapshotPayload.AvailabilityState.SOLD_OUT,
            available
                ? DungeonShopSnapshotPayload.Action.PURCHASE
                : DungeonShopSnapshotPayload.Action.NONE,
            available ? "PURCHASE" : "SOLD OUT",
            entry.unlimited()
                ? "Unlimited per-player supply; progression requirements still apply."
                : quantity + " remaining for this server day."
        );
    }

    private static DungeonShopSnapshotPayload.Offer satchelOffer(
        CoreSatchelShopData.OfferSnapshot satchel
    ) {
        CoreSatchelData.SatchelGrade grade = satchel.offeredGrade();
        DungeonShopCatalog.Entry entry = DungeonShopCatalog.satchelUpgrade(grade);
        boolean purchased = satchel.purchased();
        return statefulOffer(
            entry,
            null,
            DungeonShopSnapshotPayload.Tab.DAILY,
            DungeonShopSnapshotPayload.Section.TIMED,
            DungeonShopSnapshotPayload.Icon.SATCHEL,
            1,
            purchased
                ? DungeonShopSnapshotPayload.AvailabilityState.PURCHASED
                : DungeonShopSnapshotPayload.AvailabilityState.AVAILABLE,
            purchased ? DungeonShopSnapshotPayload.Action.NONE : DungeonShopSnapshotPayload.Action.PURCHASE,
            purchased ? "PURCHASED" : "PURCHASE",
            purchased
                ? "This Core Satchel upgrade is permanently applied and cannot be purchased again."
                : "Permanently increases the player-bound Monster Core Satchel capacity."
        );
    }

    private static List<DungeonShopSnapshotPayload.Offer> weeklyOffers(
        DungeonShopStockData.WeeklySnapshot weekly,
        Set<CosmeticKey> owned
    ) {
        if (weekly == null) {
            return List.of();
        }
        List<DungeonShopSnapshotPayload.Offer> offers = new ArrayList<>();
        for (String offerId : weekly.offers()) {
            DungeonShopCatalog.Entry entry = DungeonShopCatalog.weeklyThemes().stream()
                .filter(candidate -> candidate.id().equals(offerId))
                .findFirst()
                .orElse(null);
            if (entry == null) {
                continue;
            }
            CosmeticKey key = entry.cosmetic();
            boolean alreadyOwned = key != null && owned.contains(key);
            boolean purchased = weekly.purchasedOffers().contains(entry.id());
            boolean unavailable = alreadyOwned || purchased;
            offers.add(statefulOffer(
                entry,
                key,
                DungeonShopSnapshotPayload.Tab.WEEKLY,
                DungeonShopSnapshotPayload.Section.TIMED,
                entry.icon(),
                1,
                alreadyOwned
                    ? DungeonShopSnapshotPayload.AvailabilityState.OWNED
                    : purchased
                        ? DungeonShopSnapshotPayload.AvailabilityState.PURCHASED
                        : DungeonShopSnapshotPayload.AvailabilityState.AVAILABLE,
                unavailable
                    ? DungeonShopSnapshotPayload.Action.NONE
                    : DungeonShopSnapshotPayload.Action.PURCHASE,
                alreadyOwned ? "ALREADY OWNED" : purchased ? "PURCHASED" : "PURCHASE",
                alreadyOwned
                    ? "This rolled cosmetic is already owned; no replacement or reroll is granted."
                    : purchased
                        ? "This Weekly item was already purchased during the current rotation."
                        : key == null
                            ? "Available once during the current Weekly rotation."
                            : "Permanently unlocks the " + entry.name() + "."
            ));
        }
        return List.copyOf(offers);
    }

    private static List<DungeonShopSnapshotPayload.Offer> barterOffers(ServerPlayer player) {
        List<DungeonShopSnapshotPayload.Offer> offers = new ArrayList<>();
        for (DungeonPotionBarterCatalog.Recipe recipe : DungeonPotionBarterCatalog.recipes()) {
            boolean outputRegistered = DungeonPotionBarterCatalog.resolveOutput(recipe) != null;
            List<DungeonPotionBarterCatalog.MissingIngredient> missing =
                DungeonPotionBarterCatalog.missing(player, recipe);
            boolean ready = outputRegistered && missing.isEmpty();
            String requirement = outputRegistered
                ? DungeonPotionBarterCatalog.missingSummary(player, recipe)
                : "Output item is not registered on this server.";
            DungeonShopSnapshotPayload.AvailabilityState state = ready
                ? DungeonShopSnapshotPayload.AvailabilityState.AVAILABLE
                : DungeonShopSnapshotPayload.AvailabilityState.LOCKED;
            offers.add(new DungeonShopSnapshotPayload.Offer(
                recipe.id(),
                null,
                DungeonShopSnapshotPayload.Tab.BARTER,
                DungeonShopSnapshotPayload.Section.BARTER,
                new DungeonShopSnapshotPayload.Display(
                    recipe.icon(),
                    recipe.outputName(),
                    recipe.grade().toUpperCase(Locale.ROOT),
                    recipe.grade(),
                    recipe.displayType(),
                    recipe.accent(),
                    DungeonPotionBarterCatalog.resolveDisplayItemId(recipe.outputItemIds())
                ),
                new DungeonShopSnapshotPayload.Price("BARTER", 0L, 0L, 0L, 0L),
                new DungeonShopSnapshotPayload.Availability(
                    state,
                    1,
                    bounded(requirement, 96),
                    ready
                ),
                new DungeonShopSnapshotPayload.Routing(
                    "Dungeon Shop",
                    DungeonShopSnapshotPayload.Action.BARTER,
                    ready ? "BARTER" : "MISSING MATERIALS"
                ),
                new DungeonShopSnapshotPayload.Detail(
                    recipe.exactExchangeDescription(),
                    recipe.comparison(),
                    requirement
                )
            ));
        }
        return List.copyOf(offers);
    }

    private static DungeonShopSnapshotPayload.Offer displayOnlyOffer(
        DungeonShopCatalog.Entry entry,
        CosmeticKey cosmetic,
        DungeonShopSnapshotPayload.Tab tab,
        DungeonShopSnapshotPayload.Section section,
        DungeonShopSnapshotPayload.Icon icon,
        int quantity,
        String actionLabel,
        String reason
    ) {
        return statefulOffer(
            entry,
            cosmetic,
            tab,
            section,
            icon,
            quantity,
            DungeonShopSnapshotPayload.AvailabilityState.DISPLAY_ONLY,
            DungeonShopSnapshotPayload.Action.NONE,
            actionLabel,
            reason
        );
    }

    private static DungeonShopSnapshotPayload.Offer unlimitedEssentialOffer(
        DungeonShopCatalog.Entry entry,
        CosmeticKey cosmetic,
        DungeonShopSnapshotPayload.Tab tab,
        DungeonShopSnapshotPayload.Section section,
        DungeonShopSnapshotPayload.Icon icon,
        int quantity
    ) {
        return statefulOffer(
            entry,
            cosmetic,
            tab,
            section,
            icon,
            quantity,
            true,
            DungeonShopSnapshotPayload.AvailabilityState.AVAILABLE,
            DungeonShopSnapshotPayload.Action.PURCHASE,
            "PURCHASE",
            "Unlimited per-player supply; floor and grade eligibility still apply."
        );
    }

    private static DungeonShopSnapshotPayload.Offer statefulOffer(
        DungeonShopCatalog.Entry entry,
        CosmeticKey cosmetic,
        DungeonShopSnapshotPayload.Tab tab,
        DungeonShopSnapshotPayload.Section section,
        DungeonShopSnapshotPayload.Icon icon,
        int quantity,
        DungeonShopSnapshotPayload.AvailabilityState state,
        DungeonShopSnapshotPayload.Action action,
        String actionLabel,
        String reason
    ) {
        return statefulOffer(
            entry,
            cosmetic,
            tab,
            section,
            icon,
            quantity,
            false,
            state,
            action,
            actionLabel,
            reason
        );
    }

    private static DungeonShopSnapshotPayload.Offer statefulOffer(
        DungeonShopCatalog.Entry entry,
        CosmeticKey cosmetic,
        DungeonShopSnapshotPayload.Tab tab,
        DungeonShopSnapshotPayload.Section section,
        DungeonShopSnapshotPayload.Icon icon,
        int quantity,
        boolean unlimited,
        DungeonShopSnapshotPayload.AvailabilityState state,
        DungeonShopSnapshotPayload.Action action,
        String actionLabel,
        String reason
    ) {
        return new DungeonShopSnapshotPayload.Offer(
            entry.id(),
            cosmetic,
            tab,
            section,
            entry.display(icon),
            entry.price(),
            new DungeonShopSnapshotPayload.Availability(
                state,
                quantity,
                reason,
                unlimited
            ),
            new DungeonShopSnapshotPayload.Routing(
                "Dungeon Shop",
                action,
                actionLabel
            ),
            entry.detail()
        );
    }

    record RotationTime(
        long dayKey,
        long weekKey,
        DungeonShopSnapshotPayload.ResetWindow resets
    ) {
        static RotationTime at(ZonedDateTime now) {
            Objects.requireNonNull(now, "Dungeon Shop server time");
            ZoneId zone = now.getZone();
            java.time.LocalDate day = now.toLocalDate();
            java.time.LocalDate monday = day.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            );
            ZonedDateTime nextDaily = day.plusDays(1).atStartOfDay(zone);
            ZonedDateTime nextWeekly = monday.plusWeeks(1).atStartOfDay(zone);
            return new RotationTime(
                day.toEpochDay(),
                monday.toEpochDay(),
                new DungeonShopSnapshotPayload.ResetWindow(
                    day.toString(),
                    "Resets " + RESET_FORMAT.format(nextDaily),
                    monday.toString(),
                    "Resets " + RESET_FORMAT.format(nextWeekly)
                )
            );
        }
    }

    private static String bounded(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
