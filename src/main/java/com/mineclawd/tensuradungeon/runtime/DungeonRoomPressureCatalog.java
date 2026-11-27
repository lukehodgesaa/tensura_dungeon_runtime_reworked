package com.mineclawd.tensuradungeon.runtime;

import java.util.List;
import java.util.Locale;

public final class DungeonRoomPressureCatalog {
    public static final int CATALOG_VERSION = 1;
      private static final String TEST_ACTIVATED_REASON = "TEST_ACTIVATED_2026-08-07";
      private static final List<Entry> ENTRIES = List.of(
          new Entry(
              "rising-heat",
              "Rising Heat",
              DungeonModifierDesignStatus.LIVE,
              DungeonModifierAvailability.AVAILABLE,
              TEST_ACTIVATED_REASON
          )
      );

    private DungeonRoomPressureCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry byId(String id) {
        String normalized = normalize(id);
        return ENTRIES.stream()
            .filter(entry -> entry.id().equals(normalized))
            .findFirst()
            .orElse(null);
    }

    public static boolean isKnown(String id) {
        return byId(id) != null;
    }

    public static boolean isRollable(String id) {
        Entry entry = byId(id);
        return entry != null && entry.isRollable();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Entry(
        String id,
        String displayName,
        DungeonModifierDesignStatus designStatus,
        DungeonModifierAvailability availability,
        String availabilityReason
    ) {
        public Entry {
            id = normalize(id);
            displayName = displayName == null ? "" : displayName.trim();
            availabilityReason = availabilityReason == null ? "" : availabilityReason.trim();
            if (id.isBlank() || displayName.isBlank() || designStatus == null || availability == null) {
                throw new IllegalArgumentException("Incomplete room-pressure entry");
            }
        }

        public boolean isRollable() {
            return designStatus == DungeonModifierDesignStatus.LIVE
                && availability == DungeonModifierAvailability.AVAILABLE;
        }
    }
}
