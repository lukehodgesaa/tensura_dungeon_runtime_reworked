package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonShopSnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DungeonShopProjectionMapper {
    private DungeonShopProjectionMapper() {}

    public static DungeonShopView from(DungeonShopSnapshotPayload snapshot) {
        if (snapshot == null || !snapshot.supportedSchema()) {
            throw new IllegalArgumentException("Unsupported Dungeon Shop snapshot schema");
        }
        DungeonShopView.Tab activeTab = tab(snapshot.activeTab());
        return new DungeonShopView(
            new DungeonShopView.Wallet(
                snapshot.wallet().bronze(),
                snapshot.wallet().silver(),
                snapshot.wallet().gold(),
                snapshot.wallet().stellarGold()
            ),
            resetLabel(snapshot, activeTab),
            snapshot.offers().stream().map(DungeonShopProjectionMapper::offer).toList(),
            false,
            activeTab,
            snapshot.status().message(),
            snapshot.revision()
        );
    }

    private static DungeonShopView.Offer offer(DungeonShopSnapshotPayload.Offer offer) {
        return new DungeonShopView.Offer(
            offer.id(),
            tab(offer.tab()),
            section(offer.section()),
            icon(offer.display().icon()),
            offer.display().name(),
            offer.display().rarity(),
            offer.display().grade(),
            offer.display().type(),
            offer.routing().vendor(),
            offer.price().label(),
            offer.detail().description(),
            offer.detail().comparison(),
            offer.detail().requirements(),
            offer.availability().reason(),
            offer.routing().actionLabel(),
            state(offer.availability().state()),
            offer.availability().quantity(),
            offer.display().accent(),
            offer.availability().unlimited(),
            offer.display().itemId()
        );
    }

    static String walletLabel(DungeonShopSnapshotPayload.Wallet wallet) {
        List<String> parts = new ArrayList<>(4);
        append(parts, wallet.stellarGold(), "STELLAR GOLD");
        append(parts, wallet.gold(), "GOLD");
        append(parts, wallet.silver(), "SILVER");
        append(parts, wallet.bronze(), "BRONZE");
        return parts.isEmpty() ? "0 GOLD" : String.join("  ", parts);
    }

    private static void append(List<String> parts, long value, String unit) {
        if (value > 0L) {
            parts.add(String.format(Locale.ROOT, "%,d %s", value, unit));
        }
    }

    private static String resetLabel(
        DungeonShopSnapshotPayload snapshot,
        DungeonShopView.Tab activeTab
    ) {
        return switch (activeTab) {
            case DAILY -> snapshot.resets().dailyLabel();
            case WEEKLY -> snapshot.resets().weeklyLabel();
            case BARTER -> "NO RESET";
        };
    }

    private static DungeonShopView.Tab tab(DungeonShopSnapshotPayload.Tab tab) {
        return switch (tab) {
            case DAILY -> DungeonShopView.Tab.DAILY;
            case WEEKLY -> DungeonShopView.Tab.WEEKLY;
            case BARTER -> DungeonShopView.Tab.BARTER;
        };
    }

    private static DungeonShopView.Section section(DungeonShopSnapshotPayload.Section section) {
        return switch (section) {
            case ESSENTIAL -> DungeonShopView.Section.ESSENTIAL;
            case TIMED -> DungeonShopView.Section.TIMED;
            case BARTER -> DungeonShopView.Section.BARTER;
        };
    }

    private static DungeonShopView.Icon icon(DungeonShopSnapshotPayload.Icon icon) {
        return switch (icon) {
            case BRACELET -> DungeonShopView.Icon.BRACELET;
            case WHISTLE -> DungeonShopView.Icon.WHISTLE;
            case HEALING -> DungeonShopView.Icon.HEALING;
            case MAGICULE -> DungeonShopView.Icon.MAGICULE;
            case SATCHEL -> DungeonShopView.Icon.SATCHEL;
            case CLOAK -> DungeonShopView.Icon.CLOAK;
            case STONE -> DungeonShopView.Icon.STONE;
            case KEY -> DungeonShopView.Icon.KEY;
            case WEAPON -> DungeonShopView.Icon.WEAPON;
            case ARMOR -> DungeonShopView.Icon.ARMOR;
            case CHEST -> DungeonShopView.Icon.CHEST;
            case THEME -> DungeonShopView.Icon.THEME;
            case TRADE -> DungeonShopView.Icon.TRADE;
        };
    }

    private static DungeonShopView.State state(
        DungeonShopSnapshotPayload.AvailabilityState state
    ) {
        return switch (state) {
            case AVAILABLE -> DungeonShopView.State.AVAILABLE;
            case DISPLAY_ONLY -> DungeonShopView.State.DISPLAY_ONLY;
            case PURCHASED -> DungeonShopView.State.PURCHASED;
            case OWNED -> DungeonShopView.State.OWNED;
            case LOCKED -> DungeonShopView.State.LOCKED;
            case SOLD_OUT -> DungeonShopView.State.SOLD_OUT;
            case VENDOR_ROUTED -> DungeonShopView.State.VENDOR_ROUTED;
        };
    }
}
