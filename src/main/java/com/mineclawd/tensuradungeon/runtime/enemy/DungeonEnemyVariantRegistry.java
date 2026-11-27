package com.mineclawd.tensuradungeon.runtime.enemy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;

public final class DungeonEnemyVariantRegistry {
    private static final String VARIANT_PACKAGE = "io.github.manasmods.tensura.entity.variant.";
    private static final Map<String, List<DungeonEnemyVariantDefinition>> TABLES = createTables();

    private DungeonEnemyVariantRegistry() {
    }

    public static List<DungeonEnemyVariantDefinition> roll(
        String entityTypeId,
        int floor,
        Set<String> activeThemeTags,
        RandomSource random
    ) {
        List<DungeonEnemyVariantDefinition> table = TABLES.get(entityTypeId);
        if (table == null || table.isEmpty()) return List.of();

        Map<String, List<DungeonEnemyVariantDefinition>> byChannel = new LinkedHashMap<>();
        for (DungeonEnemyVariantDefinition definition : table) {
            if (definition.isEligible(floor, activeThemeTags)) {
                byChannel.computeIfAbsent(definition.channel(), ignored -> new ArrayList<>()).add(definition);
            }
        }
        List<DungeonEnemyVariantDefinition> selected = new ArrayList<>();
        for (List<DungeonEnemyVariantDefinition> channel : byChannel.values()) {
            DungeonEnemyVariantDefinition choice = weightedPick(channel, random);
            if (choice != null) selected.add(choice);
        }
        return List.copyOf(selected);
    }

    public static boolean hasTable(String entityTypeId) {
        return TABLES.containsKey(entityTypeId);
    }

    private static DungeonEnemyVariantDefinition weightedPick(List<DungeonEnemyVariantDefinition> entries, RandomSource random) {
        int total = entries.stream().mapToInt(entry -> Math.max(1, entry.weight())).sum();
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (DungeonEnemyVariantDefinition entry : entries) {
            roll -= Math.max(1, entry.weight());
            if (roll < 0) return entry;
        }
        return entries.getFirst();
    }

    private static Map<String, List<DungeonEnemyVariantDefinition>> createTables() {
        Map<String, List<DungeonEnemyVariantDefinition>> tables = new LinkedHashMap<>();

        addEnum(tables, "tensura:direwolf", "variant", "setVariant", "DirewolfVariant", 1, 999,
            weights("DIREWOLF", 40, "RED_FANG", 9, "GREEN_FANG", 9, "BLUE_FANG", 9, "BROWN_FANG", 9, "PURPLE_FANG", 7, "BLACK_FANG", 7));
        addEnum(tables, "tensura:direwolf", "variant", "setVariant", "DirewolfVariant", 15, 999,
            weights("MYSTIC_FIRE_WOLF", 4, "MYSTIC_WIND_WOLF", 4, "MYSTIC_WATER_WOLF", 4, "MYSTIC_EARTH_WOLF", 4, "MYSTIC_SPACE_WOLF", 3, "MYSTIC_WOLF", 3));
        addEnum(tables, "tensura:direwolf", "variant", "setVariant", "DirewolfVariant", 30, 999,
            weights("TEMPEST_WOLF", 5));
        addEnum(tables, "tensura:direwolf", "variant", "setVariant", "DirewolfVariant", 50, 999,
            weights("STAR_WOLF", 3));
        addEnum(tables, "tensura:direwolf", "variant", "setVariant", "DirewolfVariant", 75, 999,
            weights("TEMPEST_STAR_WOLF", 2));

        addEnum(tables, "tensura:slime", "color", "setColor", "SlimeColor", 1, 999,
            weights("DEFAULT", 20, "RED", 5, "YELLOW", 5, "LIME", 5, "CYAN", 5, "PINK", 4, "WHITE", 4,
                "BLUE", 7, "GREEN", 7, "ORANGE", 5, "GRAY", 4, "LIGHT_GRAY", 4, "MAGENTA", 4,
                "PURPLE", 5, "BROWN", 4, "BLACK", 4));
        addEnum(tables, "tensura:slime", "type", "setVariant", "SlimeType", 1, 999,
            weights("PRODUCE", 70, "MERGE", 20, "SUMMONED", 10));
        addSpecial(tables, "tensura:slime", "size", "setSize", "3", 20, 1, 999);
        addSpecial(tables, "tensura:slime", "size", "setSize", "4", 20, 1, 999);
        addSpecial(tables, "tensura:slime", "size", "setSize", "5", 18, 1, 999);
        addSpecial(tables, "tensura:slime", "size", "setSize", "6", 16, 8, 999);
        addSpecial(tables, "tensura:slime", "size", "setSize", "7", 14, 15, 999);
        addSpecial(tables, "tensura:slime", "size", "setSize", "8", 12, 25, 999);

        addEnum(tables, "tensura:barghest", "flame", "setFlameType", "BarghestFlameVariant", 1, 999,
            weights("BLUE", 14, "ORANGE", 14, "TEAL", 12, "YELLOW", 12, "RED", 12, "GREEN", 10,
                "PURPLE", 10, "WHITE", 8, "BLACK", 8));
        addEnum(tables, "tensura:basilisk", "variant", "setVariant", "BasiliskVariant", 1, 999,
            weights("EAGLE", 30, "FALCON", 30, "CHICKEN", 22, "ROOSTER", 18));
        addEnum(tables, "tensura:hound_dog", "variant", "setVariant", "HoundDogVariant", 1, 999,
            weights("DEFAULT", 50, "EVOLVED", 50));

        Map<String, List<DungeonEnemyVariantDefinition>> immutable = new LinkedHashMap<>();
        tables.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return Map.copyOf(immutable);
    }

    private static void addEnum(Map<String, List<DungeonEnemyVariantDefinition>> tables, String entityId, String channel,
                                String setter, String enumName, int minFloor, int maxFloor, Map<String, Integer> weights) {
        weights.forEach((variant, weight) -> add(tables, new DungeonEnemyVariantDefinition(
            entityId, channel, setter, VARIANT_PACKAGE + enumName, variant, weight, minFloor, maxFloor, Set.of()
        )));
    }

    private static void addSpecial(Map<String, List<DungeonEnemyVariantDefinition>> tables, String entityId, String channel,
                                   String setter, String value, int weight, int minFloor, int maxFloor) {
        add(tables, new DungeonEnemyVariantDefinition(entityId, channel, setter, "", value, weight, minFloor, maxFloor, Set.of()));
    }

    private static void add(Map<String, List<DungeonEnemyVariantDefinition>> tables, DungeonEnemyVariantDefinition definition) {
        tables.computeIfAbsent(definition.entityTypeId(), ignored -> new ArrayList<>()).add(definition);
    }

    private static Map<String, Integer> weights(Object... values) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) weights.put((String) values[i], (Integer) values[i + 1]);
        return weights;
    }
}
