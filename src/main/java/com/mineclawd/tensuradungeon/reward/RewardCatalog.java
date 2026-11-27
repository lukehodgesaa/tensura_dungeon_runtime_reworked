package com.mineclawd.tensuradungeon.reward;

import java.util.ArrayList;
import java.util.List;

public final class RewardCatalog {
    public RewardContents contentsForFloor(int floorNumber) {
        String tier = tierForFloor(floorNumber);
        List<String> categories = new ArrayList<>();
        List<RewardItem> items = new ArrayList<>();
        add(categories, items, "currency_placeholder", "minecraft:gold_nugget", switch (tier) {
            case "IMPROVED" -> 5;
            case "ADVANCED" -> 7;
            case "ELITE" -> 9;
            case "MYTHIC_PLACEHOLDER" -> 12;
            default -> 3;
        });
        add(categories, items, "experience_placeholder", "minecraft:experience_bottle", switch (tier) {
            case "ADVANCED", "ELITE", "MYTHIC_PLACEHOLDER" -> 2;
            default -> 1;
        });
        if (!"BASIC".equals(tier)) {
            add(categories, items, "crystal_placeholder", "minecraft:amethyst_shard", switch (tier) {
                case "IMPROVED" -> 1;
                case "ADVANCED" -> 2;
                case "ELITE" -> 3;
                default -> 4;
            });
        }
        if ("ADVANCED".equals(tier) || "ELITE".equals(tier) || "MYTHIC_PLACEHOLDER".equals(tier)) {
            add(categories, items, "dungeon_progression_token_placeholder", "minecraft:trial_key", 1);
        }
        if ("ELITE".equals(tier) || "MYTHIC_PLACEHOLDER".equals(tier)) {
            add(categories, items, "rare_bonus_placeholder", "minecraft:emerald", "MYTHIC_PLACEHOLDER".equals(tier) ? 4 : 2);
        }
        return new RewardContents(tier, List.copyOf(categories), List.copyOf(items));
    }

    private static String tierForFloor(int floorNumber) {
        if (floorNumber >= 41) {
            return "MYTHIC_PLACEHOLDER";
        }
        if (floorNumber >= 21) {
            return "ELITE";
        }
        if (floorNumber >= 11) {
            return "ADVANCED";
        }
        if (floorNumber >= 6) {
            return "IMPROVED";
        }
        return "BASIC";
    }

    private static void add(List<String> categories, List<RewardItem> items, String category, String itemId, int count) {
        if (count <= 0) {
            return;
        }
        categories.add(category);
        items.add(new RewardItem(itemId, count));
    }

    public record RewardContents(String tier, List<String> categories, List<RewardItem> items) {
    }

    public record RewardItem(String itemId, int count) {
    }
}
