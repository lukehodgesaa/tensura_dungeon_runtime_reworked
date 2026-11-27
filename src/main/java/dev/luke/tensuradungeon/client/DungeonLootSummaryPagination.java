package dev.luke.tensuradungeon.client;

final class DungeonLootSummaryPagination {
    private DungeonLootSummaryPagination() {}

    static int pageCount(int total, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Page capacity must be positive");
        return Math.max(1, (Math.max(0, total) + capacity - 1) / capacity);
    }

    static int pageStart(int page, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Page capacity must be positive");
        return Math.max(0, page) * capacity;
    }

    static int clampPage(int requested, int total, int capacity) {
        return Math.max(0, Math.min(requested, pageCount(total, capacity) - 1));
    }
}
