package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonUiSnapshotPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

final class DungeonPreparationViewModel {
    enum ModifierTab {
        PLAYER,
        DUNGEON
    }

    private DungeonPreparationViewModel() {}

    static boolean hasFreshSnapshot(long openingSequence, long observedSequence) {
        return observedSequence > openingSequence;
    }

    static OptionalInt parseFloor(String text, int highestUnlockedFloor) {
        if (text == null || text.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            int floor = Integer.parseInt(text.trim());
            return floor >= 1 && floor <= Math.max(1, highestUnlockedFloor)
                ? OptionalInt.of(floor)
                : OptionalInt.empty();
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    static List<ModifierRow> modifiers(
        DungeonUiSnapshotPayload snapshot,
        ModifierTab tab
    ) {
        if (snapshot == null) {
            return List.of();
        }
        Map<String, DungeonUiSnapshotPayload.ModifierCatalogEntry> catalog =
            new LinkedHashMap<>();
        for (DungeonUiSnapshotPayload.ModifierCatalogEntry entry : snapshot.modifierCatalog()) {
            catalog.put(entry.id().toLowerCase(Locale.ROOT), entry);
        }

        List<String> ids = tab == ModifierTab.PLAYER
            ? snapshot.preparedModifierPlan().boonModifierId().isBlank()
                ? List.of()
                : List.of(snapshot.preparedModifierPlan().boonModifierId())
            : snapshot.preparedModifierPlan().hostileModifierIds();
        return rows(ids, catalog);
    }

    static List<ModifierRow> modifiers(
        DungeonUiSnapshotPayload snapshot,
        ModifierTab tab,
        List<String> hostileModifierIds,
        String boonModifierId
    ) {
        if (snapshot == null) return List.of();
        Map<String, DungeonUiSnapshotPayload.ModifierCatalogEntry> catalog =
            new LinkedHashMap<>();
        for (DungeonUiSnapshotPayload.ModifierCatalogEntry entry : snapshot.modifierCatalog()) {
            catalog.put(entry.id().toLowerCase(Locale.ROOT), entry);
        }
        List<String> ids = tab == ModifierTab.PLAYER
            ? boonModifierId == null || boonModifierId.isBlank() ? List.of() : List.of(boonModifierId)
            : hostileModifierIds == null ? List.of() : hostileModifierIds;
        return rows(ids, catalog);
    }

    private static List<ModifierRow> rows(
        List<String> ids,
        Map<String, DungeonUiSnapshotPayload.ModifierCatalogEntry> catalog
    ) {
        return ids.stream().map(id -> row(id, catalog.get(id.toLowerCase(Locale.ROOT)))).toList();
    }

    private static ModifierRow row(
        String id,
        DungeonUiSnapshotPayload.ModifierCatalogEntry entry
    ) {
        if (entry == null) {
            return new ModifierRow(id, id, "UNKNOWN", "Modifier details unavailable.", "");
        }
        return new ModifierRow(
            entry.id(),
            entry.displayName(),
            entry.alignment(),
            entry.description(),
            entry.iconKey()
        );
    }

    record ModifierRow(
        String id,
        String displayName,
        String alignment,
        String description,
        String iconKey
    ) {}
}
