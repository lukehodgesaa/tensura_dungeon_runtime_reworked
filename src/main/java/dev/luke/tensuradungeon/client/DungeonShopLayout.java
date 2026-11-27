package dev.luke.tensuradungeon.client;

import java.util.List;

public final class DungeonShopLayout {
    public static final int VIRTUAL_WIDTH = 960;
    public static final int VIRTUAL_HEIGHT = 540;
    public static final int BODY_TOP = 126;
    public static final int LEFT_X = 18;
    public static final int LEFT_WIDTH = 622;
    public static final int PREVIEW_X = 650;
    public static final int PREVIEW_WIDTH = 292;
    public static final int ESSENTIAL_VISIBLE = 3;
    public static final int TIMED_VISIBLE = 4;

    private DungeonShopLayout() {}

    public static List<DungeonShopView.Offer> visibleOffers(
        DungeonShopView view,
        DungeonShopView.Tab tab,
        DungeonShopView.Section section,
        String search,
        String grade,
        String type,
        String vendor
    ) {
        return view.offers().stream()
            .filter(offer -> offer.tab() == tab)
            .filter(offer -> offer.section() == section)
            .filter(offer -> offer.matches(search, grade, type, vendor))
            .toList();
    }

    public static int clampScroll(int requested, int total, int visible) {
        return Math.max(0, Math.min(requested, Math.max(0, total - visible)));
    }

    public static int pageScroll(int current, int direction, int total, int visible) {
        if (direction == 0) return clampScroll(current, total, visible);
        return clampScroll(current + Integer.signum(direction) * visible, total, visible);
    }

    public static DungeonShopView.Offer firstVisible(
        DungeonShopView view,
        DungeonShopView.Tab tab,
        String search,
        String grade,
        String type,
        String vendor
    ) {
        return view.offers().stream()
            .filter(offer -> offer.tab() == tab)
            .filter(offer -> offer.matches(search, grade, type, vendor))
            .findFirst()
            .orElse(null);
    }
}
