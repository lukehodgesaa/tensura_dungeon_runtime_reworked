package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.network.DungeonShopSnapshotPayload;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DungeonPotionBarterCatalog {
    private DungeonPotionBarterCatalog() {}

    public static List<Recipe> recipes() {
        return DungeonShopConfiguration.current().barters();
    }

    public static Recipe byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return recipes().stream()
            .filter(recipe -> recipe.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    public static List<MissingIngredient> missing(ServerPlayer player, Recipe recipe) {
        Objects.requireNonNull(recipe, "Potion barter recipe");
        if (player == null) {
            return recipe.ingredients().stream()
                .map(ingredient -> new MissingIngredient(
                    ingredient.displayName(), ingredient.quantity()
                ))
                .toList();
        }
        return recipe.ingredients().stream()
            .map(ingredient -> {
                int available = count(player, ingredient);
                int missing = Math.max(0, ingredient.quantity() - available);
                return missing == 0
                    ? null
                    : new MissingIngredient(ingredient.displayName(), missing);
            })
            .filter(Objects::nonNull)
            .toList();
    }

    public static String missingMessage(ServerPlayer player, Recipe recipe) {
        List<MissingIngredient> missing = missing(player, recipe);
        if (missing.isEmpty()) {
            return "";
        }
        return bound(
            "Missing barter materials: "
                + missing.stream().map(MissingIngredient::format).collect(Collectors.joining("; ")),
            256
        );
    }

    public static String missingSummary(ServerPlayer player, Recipe recipe) {
        List<MissingIngredient> missing = missing(player, recipe);
        if (missing.isEmpty()) {
            return "Ingredients ready; unlimited conversion supply.";
        }
        return bound(
            "Missing: " + missing.stream().map(MissingIngredient::format)
                .collect(Collectors.joining("; ")),
            128
        );
    }

    static Item resolve(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    public static Item resolveOutput(Recipe recipe) {
        Objects.requireNonNull(recipe, "Potion barter recipe");
        return recipe.outputItemIds().stream()
            .map(DungeonPotionBarterCatalog::resolve)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    public static String resolveDisplayItemId(List<String> configuredItemIds) {
        if (configuredItemIds == null) {
            return "";
        }
        return configuredItemIds.stream()
            .filter(itemId -> resolve(itemId) != null)
            .findFirst()
            .orElse("");
    }

    public static Item resolvePotionItem(String configuredOfferId) {
        DungeonShopCatalog.Entry entry = DungeonShopCatalog.entryById(configuredOfferId, 0);
        if (entry != null) {
            Item configured = entry.itemIds().stream()
                .map(DungeonPotionBarterCatalog::resolve)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
            if (configured != null) {
                return configured;
            }
        }
        return resolve(configuredOfferId);
    }

    static void consume(ServerPlayer player, Recipe recipe) {
        Objects.requireNonNull(player, "Potion barter player");
        Objects.requireNonNull(recipe, "Potion barter recipe");
        Inventory inventory = player.getInventory();
        for (Ingredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.quantity();
            for (String itemId : ingredient.itemIds()) {
                if (remaining <= 0) {
                    break;
                }
                Item item = resolve(itemId);
                if (item == null) {
                    continue;
                }
                for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.isEmpty() || !stack.is(item)) {
                        continue;
                    }
                    int taken = Math.min(remaining, stack.getCount());
                    stack.shrink(taken);
                    remaining -= taken;
                }
            }
            if (remaining > 0) {
                throw new IllegalStateException(
                    "Potion barter inventory changed while consuming " + ingredient.displayName()
                );
            }
        }
        inventory.setChanged();
    }

    private static int count(ServerPlayer player, Ingredient ingredient) {
        return ingredient.itemIds().stream()
            .map(DungeonPotionBarterCatalog::resolve)
            .filter(Objects::nonNull)
            .mapToInt(player.getInventory()::countItem)
            .sum();
    }

    private static String bound(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    public record Recipe(
        String id,
        List<String> outputItemIds,
        int outputCount,
        String outputName,
        String family,
        DungeonShopSnapshotPayload.Icon icon,
        int accent,
        String grade,
        String displayType,
        String comparison,
        List<Ingredient> ingredients
    ) {
        public Recipe {
            id = Objects.requireNonNull(id, "Potion barter recipe id");
            outputItemIds = List.copyOf(outputItemIds);
            outputName = Objects.requireNonNull(outputName, "Potion barter output name");
            family = Objects.requireNonNull(family, "Potion barter family");
            icon = Objects.requireNonNull(icon, "Potion barter icon");
            grade = Objects.requireNonNull(grade, "Potion barter grade");
            displayType = Objects.requireNonNull(displayType, "Potion barter display type");
            comparison = Objects.requireNonNull(comparison, "Potion barter comparison");
            ingredients = List.copyOf(ingredients);
            if (outputItemIds.isEmpty() || outputCount <= 0 || ingredients.isEmpty()) {
                throw new IllegalArgumentException("Potion barter recipe is incomplete: " + id);
            }
        }

        public String outputItemId() {
            return outputItemIds.getFirst();
        }

        public String inputSummary() {
            return ingredients.stream().map(Ingredient::format).collect(Collectors.joining(" + "));
        }

        public String exactExchangeDescription() {
            return inputSummary() + " -> " + outputCount + "x " + outputName;
        }
    }

    public record Ingredient(int quantity, String displayName, List<String> itemIds) {
        public Ingredient {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Potion barter ingredient quantity must be positive");
            }
            displayName = Objects.requireNonNull(displayName, "Potion barter ingredient name");
            itemIds = List.copyOf(itemIds);
            if (itemIds.isEmpty()) {
                throw new IllegalArgumentException("Potion barter ingredient requires an item identity");
            }
        }

        public String format() {
            return quantity + "x " + displayName;
        }
    }

    public record MissingIngredient(String displayName, int quantity) {
        public MissingIngredient {
            displayName = Objects.requireNonNull(displayName, "Missing ingredient name");
            if (quantity <= 0) {
                throw new IllegalArgumentException("Missing ingredient quantity must be positive");
            }
        }

        public String format() {
            return quantity + "x " + displayName;
        }
    }
}
