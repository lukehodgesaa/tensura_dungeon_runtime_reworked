package dev.luke.tensuradungeon.client;

import com.mineclawd.tensuradungeon.client.DungeonClientSoundEffects;
import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.function.Consumer;

public final class DungeonShopUiApi {
    public enum ActionType {
        PURCHASE,
        BARTER,
        VISIT_VENDOR
    }

    public record Action(
        ActionType type,
        String offerId,
        String vendor,
        DungeonShopView.Tab tab,
        long snapshotRevision
    ) {
        public Action(ActionType type, String offerId, String vendor) {
            this(type, offerId, vendor, null, 0L);
        }

        public Action {
            type = Objects.requireNonNull(type);
            offerId = offerId == null ? "" : offerId;
            vendor = vendor == null ? "" : vendor;
            if (snapshotRevision < 0L) {
                throw new IllegalArgumentException("Dungeon Shop snapshot revision cannot be negative");
            }
        }
    }

    private static Consumer<DungeonShopView> openAction = view -> {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DungeonShopLdLib2Screen screen) {
            screen.applyAuthoritativeView(view);
        } else {
            minecraft.setScreen(new DungeonShopLdLib2Screen(view, minecraft.screen));
        }
    };
    private static Consumer<Action> actionSink = action -> {};
    private static Consumer<DungeonShopView.Tab> tabSink = tab -> {};

    private DungeonShopUiApi() {}

    public static void open(DungeonShopView view) {
        openAction.accept(Objects.requireNonNull(view));
    }

    public static void openVisualPreview() {
        DungeonClientSoundEffects.playMenuOpen();
        open(DungeonShopView.visualPreview());
    }

    public static void setOpenAction(Consumer<DungeonShopView> action) {
        openAction = Objects.requireNonNull(action);
    }

    public static void setActionSink(Consumer<Action> sink) {
        actionSink = Objects.requireNonNull(sink);
    }

    public static void setTabSink(Consumer<DungeonShopView.Tab> sink) {
        tabSink = Objects.requireNonNull(sink);
    }

    static void requestTab(DungeonShopView.Tab tab) {
        tabSink.accept(Objects.requireNonNull(tab));
    }

    static void emit(ActionType type, DungeonShopView.Offer offer) {
        actionSink.accept(new Action(type, offer.id(), offer.vendor()));
    }

    static void emit(ActionType type, DungeonShopView view, DungeonShopView.Offer offer) {
        Objects.requireNonNull(view, "Dungeon Shop view");
        Objects.requireNonNull(offer, "Dungeon Shop offer");
        actionSink.accept(new Action(
            type,
            offer.id(),
            offer.vendor(),
            offer.tab(),
            view.revision()
        ));
    }
}
