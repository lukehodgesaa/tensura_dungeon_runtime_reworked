package com.mineclawd.tensuradungeon.client;

record CoreSatchelTabLayout(int x, int y, int width, int height) {
    static final int COLLAPSED_WIDTH = 13;
    static final int EXPANDED_WIDTH = 112;
    static final int HEIGHT = 34;
    private static final int ATTACHMENT_OVERLAP = 2;
    private static final int VERTICAL_OFFSET = 49;
    private static final float EXPANSION_SPEED_PER_SECOND = 7.5F;

    static CoreSatchelTabLayout create(int inventoryLeft, int inventoryTop, float expansion) {
        int width = widthFor(expansion);
        return new CoreSatchelTabLayout(
            inventoryLeft - width + ATTACHMENT_OVERLAP,
            inventoryTop + VERTICAL_OFFSET,
            width,
            HEIGHT
        );
    }

    static float advance(float current, boolean hovered, double elapsedSeconds) {
        float target = hovered ? 1.0F : 0.0F;
        float movement = (float) Math.max(0.0D, Math.min(0.05D, elapsedSeconds))
            * EXPANSION_SPEED_PER_SECOND;
        if (current < target) return Math.min(target, current + movement);
        if (current > target) return Math.max(target, current - movement);
        return current;
    }

    private static int widthFor(float expansion) {
        float bounded = Math.max(0.0F, Math.min(1.0F, expansion));
        return Math.round(COLLAPSED_WIDTH + (EXPANDED_WIDTH - COLLAPSED_WIDTH) * bounded);
    }
}
