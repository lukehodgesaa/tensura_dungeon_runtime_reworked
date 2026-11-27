package com.mineclawd.tensuradungeon.client;

record CoreSatchelLayout(
    int columns,
    int rows,
    int slotSize,
    int slotGap,
    int panelWidth,
    int panelHeight,
    int panelX,
    int panelY,
    int gridX,
    int gridY
) {
    private static final int HEADER_HEIGHT = 27;
    private static final int FOOTER_HEIGHT = 20;
    private static final int PANEL_PADDING = 10;

    static CoreSatchelLayout create(int capacity, int screenWidth, int screenHeight) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }

        int columns = columnsFor(capacity);
        int rows = (capacity + columns - 1) / columns;
        int gap = 2;
        int verticalAllowance = Math.max(18, screenHeight - HEADER_HEIGHT - FOOTER_HEIGHT - PANEL_PADDING - 16);
        int slotSize = Math.max(18, Math.min(25, (verticalAllowance - gap * (rows - 1)) / rows));
        int gridWidth = columns * slotSize + (columns - 1) * gap;
        int gridHeight = rows * slotSize + (rows - 1) * gap;
        int panelWidth = Math.max(196, gridWidth + PANEL_PADDING * 2);
        int panelHeight = HEADER_HEIGHT + gridHeight + FOOTER_HEIGHT + PANEL_PADDING;
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = (screenHeight - panelHeight) / 2;
        int gridX = panelX + (panelWidth - gridWidth) / 2;
        int gridY = panelY + HEADER_HEIGHT;

        return new CoreSatchelLayout(
            columns, rows, slotSize, gap, panelWidth, panelHeight, panelX, panelY, gridX, gridY
        );
    }

    int slotX(int slot) {
        return gridX + (slot % columns) * (slotSize + slotGap);
    }

    int slotY(int slot) {
        return gridY + (slot / columns) * (slotSize + slotGap);
    }

    boolean containsSlot(int slot, double mouseX, double mouseY) {
        int x = slotX(slot);
        int y = slotY(slot);
        return mouseX >= x && mouseX < x + slotSize && mouseY >= y && mouseY < y + slotSize;
    }

    private static int columnsFor(int capacity) {
        if (capacity <= 6) return 3;
        if (capacity <= 12) return 4;
        if (capacity <= 18) return 6;
        if (capacity <= 24) return 6;
        if (capacity <= 36) return 6;
        return 8;
    }
}
