package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.network.DungeonShopSnapshotPayload;
import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DungeonShopCatalog {
    private DungeonShopCatalog() {}

    public static List<Entry> essentialSupplies(int highestCompletedFloor) {
        return entries(Category.ESSENTIALS, highestCompletedFloor).stream()
            .filter(entry -> entry.productType() != ProductType.RECOVERY_POTION)
            .filter(entry -> entry.requiredFloor() <= accessibleFloor(highestCompletedFloor))
            .toList();
    }

    public static Entry essentialById(String id) {
        OfferDefinition definition = definitionById(id);
        if (definition == null || definition.category() != Category.ESSENTIALS
            || definition.productType() == ProductType.RECOVERY_POTION) {
            throw new IllegalArgumentException(
                "Unknown configured Essential Supplies catalog id: " + id
            );
        }
        return definition.entry(0);
    }

    public static List<Entry> todaysFinds(int highestCompletedFloor) {
        return entries(Category.TODAYS_FINDS, highestCompletedFloor).stream()
            .filter(entry -> entry.requiredFloor() <= accessibleFloor(highestCompletedFloor))
            .toList();
    }

    public static List<Entry> weeklyThemes() {
        return entries(Category.WEEKLY, 0);
    }

    public static List<Entry> potionSupplies(int highestCompletedFloor) {
        return entries(Category.ESSENTIALS, highestCompletedFloor).stream()
            .filter(entry -> entry.productType() == ProductType.RECOVERY_POTION)
            .filter(entry -> entry.requiredFloor() <= accessibleFloor(highestCompletedFloor))
            .toList();
    }

    public static Entry potionById(String id, int highestCompletedFloor) {
        return potionSupplies(highestCompletedFloor).stream()
            .filter(entry -> entry.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown configured recovery potion catalog id: " + id
            ));
    }

    public static boolean isPotionId(String id) {
        OfferDefinition definition = definitionById(id);
        return definition != null && definition.productType() == ProductType.RECOVERY_POTION;
    }

    public static boolean potionIsUnlimited(String id) {
        return potionDefinition(id).stockMode() == StockMode.UNLIMITED;
    }

    public static int potionDailyQuantity(String id) {
        return potionDefinition(id).maximumQuantity();
    }

    public static Entry satchelUpgrade(CoreSatchelData.SatchelGrade grade) {
        Objects.requireNonNull(grade, "Core Satchel upgrade grade");
        if (grade == CoreSatchelData.SatchelGrade.NORMAL) {
            throw new IllegalArgumentException("The free Normal Core Satchel is not a paid Shop offer");
        }
        return definitions(Category.TODAYS_FINDS).stream()
            .filter(definition -> definition.productType() == ProductType.SATCHEL_UPGRADE)
            .filter(definition -> definition.satchelGrade() == grade)
            .findFirst()
            .map(definition -> definition.entry(grade.requiredFloor() - 1))
            .orElseThrow(() -> new IllegalArgumentException(
                "Configured Today's Finds does not contain the " + grade.displayName()
                    + " Core Satchel upgrade"
            ));
    }

    public static boolean hasSatchelUpgrade(CoreSatchelData.SatchelGrade grade) {
        return grade != null && definitions(Category.TODAYS_FINDS).stream()
            .anyMatch(definition -> definition.productType() == ProductType.SATCHEL_UPGRADE
                && definition.satchelGrade() == grade);
    }

    static List<Entry> dailyStockOffers(int highestCompletedFloor) {
        List<Entry> result = new ArrayList<>();
        result.addAll(essentialStockOffers(highestCompletedFloor));
        result.addAll(todaysItemOffers(highestCompletedFloor));
        return List.copyOf(result);
    }

    static List<Entry> essentialStockOffers(int highestCompletedFloor) {
        return essentialSupplies(highestCompletedFloor);
    }

    static List<Entry> todaysItemOffers(int highestCompletedFloor) {
        return todaysFinds(highestCompletedFloor).stream()
            .filter(entry -> entry.productType() == ProductType.ITEM)
            .toList();
    }

    public static Entry entryById(String id, int highestCompletedFloor) {
        OfferDefinition definition = definitionById(id);
        return definition == null ? null : definition.entry(highestCompletedFloor);
    }

    public static OfferDefinition definitionById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return DungeonShopConfiguration.current().allOffers().stream()
            .filter(definition -> definition.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    public static List<OfferDefinition> definitions(Category category) {
        Objects.requireNonNull(category, "Dungeon Shop category");
        DungeonShopConfiguration.Snapshot snapshot = DungeonShopConfiguration.current();
        return switch (category) {
            case ESSENTIALS -> snapshot.essentials();
            case TODAYS_FINDS -> snapshot.todaysFinds();
            case WEEKLY -> snapshot.weekly();
        };
    }

    private static List<Entry> entries(Category category, int highestCompletedFloor) {
        return definitions(category).stream()
            .map(definition -> definition.entry(highestCompletedFloor))
            .toList();
    }

    private static OfferDefinition potionDefinition(String id) {
        OfferDefinition definition = definitionById(id);
        if (definition == null || definition.productType() != ProductType.RECOVERY_POTION) {
            throw new IllegalArgumentException(
                "Unknown configured recovery potion catalog id: " + id
            );
        }
        return definition;
    }

    private static int accessibleFloor(int highestCompletedFloor) {
        return Math.max(1, highestCompletedFloor + 1);
    }

    public record OfferDefinition(
        Category category,
        String id,
        ProductType productType,
        List<String> itemIds,
        CosmeticKey cosmetic,
        CoreSatchelData.SatchelGrade satchelGrade,
        String name,
        String grade,
        String type,
        DungeonShopSnapshotPayload.Icon icon,
        int accent,
        long basePriceBronze,
        boolean priceScalesWithGrade,
        int requiredFloor,
        StockMode stockMode,
        int minimumQuantity,
        int maximumQuantity,
        int outputCount,
        int appearanceBasisPoints,
        int guaranteedByEligibleWeek,
        String description,
        String comparison,
        String requirements
    ) {
        public OfferDefinition {
            category = Objects.requireNonNull(category, "Dungeon Shop offer category");
            id = Objects.requireNonNull(id, "Dungeon Shop offer id");
            productType = Objects.requireNonNull(productType, "Dungeon Shop product type");
            itemIds = List.copyOf(itemIds);
            name = Objects.requireNonNull(name, "Dungeon Shop offer name");
            grade = Objects.requireNonNull(grade, "Dungeon Shop offer grade");
            type = Objects.requireNonNull(type, "Dungeon Shop offer display type");
            icon = Objects.requireNonNull(icon, "Dungeon Shop offer icon");
            stockMode = Objects.requireNonNull(stockMode, "Dungeon Shop stock mode");
            description = Objects.requireNonNull(description, "Dungeon Shop offer description");
            comparison = Objects.requireNonNull(comparison, "Dungeon Shop offer comparison");
            requirements = Objects.requireNonNull(requirements, "Dungeon Shop offer requirements");
            if (basePriceBronze < 0L || requiredFloor < 0 || minimumQuantity < 1
                || maximumQuantity < minimumQuantity || outputCount < 1
                || appearanceBasisPoints < 0 || appearanceBasisPoints > 10_000
                || guaranteedByEligibleWeek < 1) {
                throw new IllegalArgumentException("Dungeon Shop offer values are invalid: " + id);
            }
        }

        Entry entry(int highestCompletedFloor) {
            Grade priceGrade = priceScalesWithGrade
                ? Grade.forAccessibleFloor(highestCompletedFloor)
                : null;
            String effectiveGrade = priceGrade == null ? grade : priceGrade.displayName();
            long effectivePrice = priceGrade == null
                ? basePriceBronze
                : applyGradeMultiplier(basePriceBronze, priceGrade);
            return new Entry(
                id,
                category,
                productType,
                itemIds,
                cosmetic,
                satchelGrade,
                name,
                effectiveGrade,
                type,
                icon,
                accent,
                effectivePrice,
                requiredFloor,
                stockMode,
                minimumQuantity,
                maximumQuantity,
                outputCount,
                appearanceBasisPoints,
                guaranteedByEligibleWeek,
                replaceGrade(description, effectiveGrade),
                replaceGrade(comparison, effectiveGrade),
                replaceGrade(requirements, effectiveGrade)
            );
        }

        private static String replaceGrade(String value, String grade) {
            return value.replace("{price_grade}", grade);
        }
    }

    public record Entry(
        String id,
        Category category,
        ProductType productType,
        List<String> itemIds,
        CosmeticKey cosmetic,
        CoreSatchelData.SatchelGrade satchelGrade,
        String name,
        String grade,
        String type,
        DungeonShopSnapshotPayload.Icon icon,
        int accent,
        long priceBronze,
        int requiredFloor,
        StockMode stockMode,
        int minimumQuantity,
        int maximumQuantity,
        int outputCount,
        int appearanceBasisPoints,
        int guaranteedByEligibleWeek,
        String description,
        String comparison,
        String requirements
    ) {
        public Entry {
            id = Objects.requireNonNull(id, "Dungeon Shop catalog id");
            category = Objects.requireNonNull(category, "Dungeon Shop category");
            productType = Objects.requireNonNull(productType, "Dungeon Shop product type");
            itemIds = List.copyOf(itemIds);
            name = Objects.requireNonNull(name, "Dungeon Shop catalog name");
            grade = Objects.requireNonNull(grade, "Dungeon Shop catalog grade");
            type = Objects.requireNonNull(type, "Dungeon Shop catalog type");
            icon = Objects.requireNonNull(icon, "Dungeon Shop catalog icon");
            stockMode = Objects.requireNonNull(stockMode, "Dungeon Shop stock mode");
            description = Objects.requireNonNull(description, "Dungeon Shop catalog description");
            comparison = Objects.requireNonNull(comparison, "Dungeon Shop catalog comparison");
            requirements = Objects.requireNonNull(requirements, "Dungeon Shop catalog requirements");
        }

        public boolean unlimited() {
            return stockMode == StockMode.UNLIMITED;
        }

        public DungeonShopSnapshotPayload.Display display() {
            return new DungeonShopSnapshotPayload.Display(
                icon,
                name,
                grade.toUpperCase(Locale.ROOT),
                grade,
                type,
                accent,
                DungeonPotionBarterCatalog.resolveDisplayItemId(itemIds)
            );
        }

        public DungeonShopSnapshotPayload.Display display(DungeonShopSnapshotPayload.Icon ignored) {
            return display();
        }

        public int priceGold() {
            return Math.toIntExact(priceBronze / DungeonCurrencyPolicy.BRONZE_PER_GOLD);
        }

        public DungeonShopSnapshotPayload.Price price() {
            long gold = priceBronze / DungeonCurrencyPolicy.BRONZE_PER_GOLD;
            long afterGold = priceBronze % DungeonCurrencyPolicy.BRONZE_PER_GOLD;
            long silver = afterGold / DungeonCurrencyPolicy.BRONZE_PER_SILVER;
            long bronze = afterGold % DungeonCurrencyPolicy.BRONZE_PER_SILVER;
            List<String> parts = new ArrayList<>(3);
            if (gold > 0L) parts.add(String.format(Locale.ROOT, "%,d GOLD", gold));
            if (silver > 0L) parts.add(String.format(Locale.ROOT, "%,d SILVER", silver));
            if (bronze > 0L) parts.add(String.format(Locale.ROOT, "%,d BRONZE", bronze));
            return new DungeonShopSnapshotPayload.Price(
                String.join(" ", parts), bronze, silver, gold, 0L
            );
        }

        public DungeonShopSnapshotPayload.Detail detail() {
            return new DungeonShopSnapshotPayload.Detail(description, comparison, requirements);
        }
    }

    private static long applyGradeMultiplier(long base, Grade grade) {
        DungeonShopConfiguration.GradePriceMultiplier multiplier =
            DungeonShopConfiguration.current().gradePriceMultipliers().get(grade);
        if (multiplier == null) {
            throw new IllegalStateException(
                "Active Dungeon Shop config is missing the " + grade.id() + " price multiplier"
            );
        }
        return multiplier.apply(base);
    }

    public enum Category {
        ESSENTIALS,
        TODAYS_FINDS,
        WEEKLY
    }

    public enum ProductType {
        ITEM,
        RECOVERY_POTION,
        SATCHEL_UPGRADE,
        HUD_THEME
    }

    public enum StockMode {
        UNLIMITED,
        DAILY,
        PER_PERIOD
    }

    public enum Grade {
        NORMAL("normal", "Normal", 1),
        SPECIAL("special", "Special", 50),
        RARE("rare", "Rare", 100),
        UNIQUE("unique", "Unique", 150),
        LEGEND("legend", "Legend", 200),
        MYTHIC("mythic", "Mythic", 250);

        private final String id;
        private final String displayName;
        private final int requiredFloor;

        Grade(String id, String displayName, int requiredFloor) {
            this.id = id;
            this.displayName = displayName;
            this.requiredFloor = requiredFloor;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public int requiredFloor() {
            return requiredFloor;
        }

        public static Grade forAccessibleFloor(int highestCompletedFloor) {
            int accessibleFloor = Math.max(1, highestCompletedFloor + 1);
            Grade selected = NORMAL;
            for (Grade candidate : values()) {
                if (candidate.requiredFloor <= accessibleFloor) {
                    selected = candidate;
                }
            }
            return selected;
        }
    }
}
