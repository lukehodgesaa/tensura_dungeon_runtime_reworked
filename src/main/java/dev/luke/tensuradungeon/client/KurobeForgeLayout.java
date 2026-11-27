package dev.luke.tensuradungeon.client;

final class KurobeForgeLayout {
    static final int LOGICAL_WIDTH = 960;
    static final int LOGICAL_HEIGHT = 560;
    static final int OUTER_PADDING = 14;
    static final int HEADER_HEIGHT = 48;
    static final int STATUS_HEIGHT = 16;
    static final int WEAPON_ROW_HEIGHT = 62;

    private KurobeForgeLayout() {}

    static int progressPixels(long current, long maximum, int availableWidth) {
        if (maximum <= 0L || availableWidth <= 0) return 0;
        long bounded = Math.max(0L, Math.min(current, maximum));
        return (int) Math.min(
            availableWidth,
            Math.round((double) bounded * availableWidth / maximum)
        );
    }
}
