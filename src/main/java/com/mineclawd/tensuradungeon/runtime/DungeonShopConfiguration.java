package com.mineclawd.tensuradungeon.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.DungeonShopSnapshotPayload;
import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DungeonShopConfiguration {
    static final int CURRENT_SCHEMA = 1;
    static final String CONFIG_DIRECTORY = "tensura_dungeon/shop";
    static final String ESSENTIALS_FILE = "essentials.json";
    static final String TODAYS_FINDS_FILE = "todays_finds.json";
    static final String WEEKLY_FILE = "weekly.json";
    static final String BARTERS_FILE = "barters.json";
    private static final String DEFAULT_RESOURCE_ROOT = "/defaultconfigs/" + CONFIG_DIRECTORY + "/";
    private static final int MAX_OFFERS_PER_FILE = 256;
    private static final int MAX_BARTERS = 256;
    private static final int MAX_INGREDIENTS = 24;
    private static final int MAX_ITEM_ALTERNATIVES = 12;
    private static final Logger LOGGER = LoggerFactory.getLogger(DungeonShopConfiguration.class);
    private static final AtomicReference<Snapshot> CURRENT =
        new AtomicReference<>(loadBundledDefaults());

    private DungeonShopConfiguration() {}

    public static Snapshot current() {
        return CURRENT.get();
    }

    public static Path runtimeDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_DIRECTORY);
    }

    public static int schemaVersion() {
        return CURRENT_SCHEMA;
    }

    public static ValidationReport validateRuntimeConfig() {
        Path directory = runtimeDirectory();
        try {
            Snapshot candidate = loadDirectory(
                directory, DungeonShopConfiguration::registeredItem, false
            );
            return ValidationReport.valid(directory, candidate);
        } catch (RuntimeException | IOException exception) {
            return ValidationReport.invalid(directory, exception.getMessage());
        }
    }

    static Snapshot installForTests(Snapshot snapshot) {
        return CURRENT.getAndSet(Objects.requireNonNull(snapshot, "Dungeon Shop test snapshot"));
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        Objects.requireNonNull(event, "Dungeon Shop reload event");
        event.addListener((ResourceManagerReloadListener) resources -> reloadRuntimeConfig());
    }

    public static boolean reloadRuntimeConfig() {
        Path directory = runtimeDirectory();
        try {
            Snapshot loaded = loadDirectory(directory, DungeonShopConfiguration::registeredItem, true);
            CURRENT.set(loaded);
            LOGGER.info(
                "Loaded Dungeon Shop config: {} essentials, {} Today's Finds, {} weekly, {} barters",
                loaded.essentials().size(),
                loaded.todaysFinds().size(),
                loaded.weekly().size(),
                loaded.barters().size()
            );
            return true;
        } catch (RuntimeException | IOException exception) {
            LOGGER.error(
                "Dungeon Shop config reload rejected; retaining the previous known-good catalog",
                exception
            );
            return false;
        }
    }

    static Snapshot loadDirectory(
        Path directory,
        Predicate<String> itemExists,
        boolean createMissingDefaults
    ) throws IOException {
        Objects.requireNonNull(directory, "Dungeon Shop config directory");
        Objects.requireNonNull(itemExists, "Dungeon Shop item validator");
        if (createMissingDefaults) {
            Files.createDirectories(directory);
            copyDefaultIfMissing(directory, ESSENTIALS_FILE);
            copyDefaultIfMissing(directory, TODAYS_FINDS_FILE);
            copyDefaultIfMissing(directory, WEEKLY_FILE);
            copyDefaultIfMissing(directory, BARTERS_FILE);
        }
        OfferFile essentialsFile = readOfferFile(
            directory.resolve(ESSENTIALS_FILE), DungeonShopCatalog.Category.ESSENTIALS
        );
        List<DungeonShopCatalog.OfferDefinition> todaysFinds =
            readOfferFile(directory.resolve(TODAYS_FINDS_FILE),
                DungeonShopCatalog.Category.TODAYS_FINDS).offers();
        List<DungeonShopCatalog.OfferDefinition> weekly =
            readOfferFile(directory.resolve(WEEKLY_FILE),
                DungeonShopCatalog.Category.WEEKLY).offers();
        List<DungeonPotionBarterCatalog.Recipe> barters =
            readBarters(directory.resolve(BARTERS_FILE));
        Snapshot result = new Snapshot(
            essentialsFile.offers(), todaysFinds, weekly, barters,
            essentialsFile.gradePriceMultipliers()
        );
        validate(result, itemExists);
        return result;
    }

    private static Snapshot loadBundledDefaults() {
        try {
            OfferFile essentialsFile = readOfferFile(
                defaultReader(ESSENTIALS_FILE), DungeonShopCatalog.Category.ESSENTIALS,
                DEFAULT_RESOURCE_ROOT + ESSENTIALS_FILE
            );
            List<DungeonShopCatalog.OfferDefinition> todaysFinds = readOfferFile(
                defaultReader(TODAYS_FINDS_FILE), DungeonShopCatalog.Category.TODAYS_FINDS,
                DEFAULT_RESOURCE_ROOT + TODAYS_FINDS_FILE
            ).offers();
            List<DungeonShopCatalog.OfferDefinition> weekly = readOfferFile(
                defaultReader(WEEKLY_FILE), DungeonShopCatalog.Category.WEEKLY,
                DEFAULT_RESOURCE_ROOT + WEEKLY_FILE
            ).offers();
            List<DungeonPotionBarterCatalog.Recipe> barters = readBarters(
                defaultReader(BARTERS_FILE), DEFAULT_RESOURCE_ROOT + BARTERS_FILE
            );
            Snapshot defaults = new Snapshot(
                essentialsFile.offers(), todaysFinds, weekly, barters,
                essentialsFile.gradePriceMultipliers()
            );
            validate(defaults, ignored -> true);
            return defaults;
        } catch (RuntimeException | IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void copyDefaultIfMissing(Path directory, String fileName) throws IOException {
        Path destination = directory.resolve(fileName);
        if (Files.exists(destination)) {
            return;
        }
        try (InputStream source = defaultStream(fileName)) {
            Files.copy(source, destination);
        }
    }

    private static Reader defaultReader(String fileName) {
        return new InputStreamReader(defaultStream(fileName), StandardCharsets.UTF_8);
    }

    private static InputStream defaultStream(String fileName) {
        InputStream stream = DungeonShopConfiguration.class.getResourceAsStream(
            DEFAULT_RESOURCE_ROOT + fileName
        );
        if (stream == null) {
            throw new IllegalStateException("Missing bundled Dungeon Shop config: " + fileName);
        }
        return stream;
    }

    private static OfferFile readOfferFile(
        Path path,
        DungeonShopCatalog.Category category
    ) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return readOfferFile(reader, category, path.toString());
        }
    }

    private static OfferFile readOfferFile(
        Reader reader,
        DungeonShopCatalog.Category category,
        String source
    ) throws IOException {
        try (reader) {
            JsonObject root = object(JsonParser.parseReader(reader), source);
            requireSchema(root, source);
            JsonArray offers = array(root, "offers", source);
            if (offers.size() > MAX_OFFERS_PER_FILE) {
                throw invalid(source, "contains too many offers");
            }
            List<DungeonShopCatalog.OfferDefinition> result = new ArrayList<>();
            for (int index = 0; index < offers.size(); index++) {
                JsonObject offer = object(offers.get(index), source + " offers[" + index + "]");
                if (!optionalBoolean(offer, "enabled", true, source)) {
                    continue;
                }
                result.add(readOffer(offer, category, source + " offers[" + index + "]"));
            }
            Map<DungeonShopCatalog.Grade, GradePriceMultiplier> multipliers =
                category == DungeonShopCatalog.Category.ESSENTIALS
                    ? readGradePriceMultipliers(root, source)
                    : defaultGradePriceMultipliers();
            return new OfferFile(result, multipliers);
        }
    }

    private static Map<DungeonShopCatalog.Grade, GradePriceMultiplier>
        readGradePriceMultipliers(JsonObject root, String source) {
        Map<DungeonShopCatalog.Grade, GradePriceMultiplier> result =
            new EnumMap<>(defaultGradePriceMultipliers());
        if (!root.has("grade_price_multipliers")) {
            return Map.copyOf(result);
        }
        JsonObject configured = object(
            root.get("grade_price_multipliers"), source + " grade_price_multipliers"
        );
        Set<String> supportedGrades = java.util.Arrays.stream(DungeonShopCatalog.Grade.values())
            .map(DungeonShopCatalog.Grade::id)
            .collect(java.util.stream.Collectors.toSet());
        for (String configuredGrade : configured.keySet()) {
            if (!supportedGrades.contains(configuredGrade)) {
                throw invalid(
                    source + " grade_price_multipliers",
                    "has unsupported grade " + configuredGrade
                );
            }
        }
        for (DungeonShopCatalog.Grade grade : DungeonShopCatalog.Grade.values()) {
            if (!configured.has(grade.id())) {
                continue;
            }
            String multiplierSource = source + " grade_price_multipliers." + grade.id();
            JsonObject multiplier = object(configured.get(grade.id()), multiplierSource);
            result.put(grade, new GradePriceMultiplier(
                positiveInt(multiplier, "numerator", multiplierSource),
                positiveInt(multiplier, "denominator", multiplierSource)
            ));
        }
        return Map.copyOf(result);
    }

    static Map<DungeonShopCatalog.Grade, GradePriceMultiplier> defaultGradePriceMultipliers() {
        EnumMap<DungeonShopCatalog.Grade, GradePriceMultiplier> result =
            new EnumMap<>(DungeonShopCatalog.Grade.class);
        result.put(DungeonShopCatalog.Grade.NORMAL, new GradePriceMultiplier(1, 1));
        result.put(DungeonShopCatalog.Grade.SPECIAL, new GradePriceMultiplier(3, 2));
        result.put(DungeonShopCatalog.Grade.RARE, new GradePriceMultiplier(2, 1));
        result.put(DungeonShopCatalog.Grade.UNIQUE, new GradePriceMultiplier(3, 1));
        result.put(DungeonShopCatalog.Grade.LEGEND, new GradePriceMultiplier(7, 2));
        result.put(DungeonShopCatalog.Grade.MYTHIC, new GradePriceMultiplier(6, 1));
        return Map.copyOf(result);
    }

    private static DungeonShopCatalog.OfferDefinition readOffer(
        JsonObject json,
        DungeonShopCatalog.Category category,
        String source
    ) {
        String id = resourceId(string(json, "id", source), source + " id");
        DungeonShopCatalog.ProductType product = enumValue(
            DungeonShopCatalog.ProductType.class,
            string(json, "product", source),
            source + " product"
        );
        List<String> itemIds = optionalStringList(json, "item_ids", source).stream()
            .map(value -> resourceId(value, source + " item_ids"))
            .toList();
        String cosmeticId = optionalString(json, "cosmetic", "", source);
        CosmeticKey cosmetic = cosmeticId.isBlank()
            ? null
            : CosmeticKey.fromCanonicalId(cosmeticId).orElseThrow(() ->
                invalid(source, "has unknown cosmetic " + cosmeticId));
        String satchelId = optionalString(json, "satchel_grade", "", source);
        CoreSatchelData.SatchelGrade satchelGrade = satchelId.isBlank()
            ? null
            : enumValue(
                CoreSatchelData.SatchelGrade.class,
                satchelId,
                source + " satchel_grade"
            );
        DungeonShopSnapshotPayload.Icon icon = enumValue(
            DungeonShopSnapshotPayload.Icon.class,
            string(json, "icon", source),
            source + " icon"
        );
        JsonObject stock = object(required(json, "stock", source), source + " stock");
        DungeonShopCatalog.StockMode stockMode = enumValue(
            DungeonShopCatalog.StockMode.class,
            string(stock, "mode", source),
            source + " stock.mode"
        );
        return new DungeonShopCatalog.OfferDefinition(
            category,
            id,
            product,
            itemIds,
            cosmetic,
            satchelGrade,
            bounded(string(json, "name", source), 64, source + " name"),
            bounded(string(json, "grade", source), 24, source + " grade"),
            bounded(string(json, "display_type", source), 24, source + " display_type"),
            icon,
            color(optionalString(json, "accent", "38D7FF", source), source + " accent"),
            priceBronze(object(required(json, "price", source), source + " price"), source),
            optionalBoolean(json, "price_scales_with_grade", false, source),
            nonNegativeInt(json, "required_floor", source),
            stockMode,
            positiveInt(stock, "minimum", source + " stock"),
            positiveInt(stock, "maximum", source + " stock"),
            positiveInt(json, "output_count", source),
            optionalInt(json, "appearance_basis_points", 10_000, source),
            optionalInt(
                json,
                json.has("guaranteed_by_eligible_period")
                    ? "guaranteed_by_eligible_period"
                    : "guaranteed_by_eligible_week",
                1,
                source
            ),
            bounded(string(json, "description", source), 160, source + " description"),
            bounded(string(json, "comparison", source), 128, source + " comparison"),
            bounded(string(json, "requirements", source), 160, source + " requirements")
        );
    }

    private static List<DungeonPotionBarterCatalog.Recipe> readBarters(Path path)
        throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return readBarters(reader, path.toString());
        }
    }

    private static List<DungeonPotionBarterCatalog.Recipe> readBarters(
        Reader reader,
        String source
    ) throws IOException {
        try (reader) {
            JsonObject root = object(JsonParser.parseReader(reader), source);
            requireSchema(root, source);
            JsonArray recipes = array(root, "recipes", source);
            if (recipes.size() > MAX_BARTERS) {
                throw invalid(source, "contains too many barter recipes");
            }
            List<DungeonPotionBarterCatalog.Recipe> result = new ArrayList<>();
            for (int index = 0; index < recipes.size(); index++) {
                String recipeSource = source + " recipes[" + index + "]";
                JsonObject recipe = object(recipes.get(index), recipeSource);
                if (!optionalBoolean(recipe, "enabled", true, recipeSource)) {
                    continue;
                }
                JsonArray ingredients = array(recipe, "ingredients", recipeSource);
                if (ingredients.isEmpty() || ingredients.size() > MAX_INGREDIENTS) {
                    throw invalid(recipeSource, "requires between 1 and " + MAX_INGREDIENTS
                        + " ingredients");
                }
                List<DungeonPotionBarterCatalog.Ingredient> parsedIngredients = new ArrayList<>();
                for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
                    String ingredientSource = recipeSource + " ingredients[" + ingredientIndex + "]";
                    JsonObject ingredient = object(ingredients.get(ingredientIndex), ingredientSource);
                    List<String> itemIds = stringList(ingredient, "item_ids", ingredientSource).stream()
                        .map(value -> resourceId(value, ingredientSource + " item_ids"))
                        .toList();
                    if (itemIds.size() > MAX_ITEM_ALTERNATIVES) {
                        throw invalid(ingredientSource, "has too many item alternatives");
                    }
                    parsedIngredients.add(new DungeonPotionBarterCatalog.Ingredient(
                        positiveInt(ingredient, "quantity", ingredientSource),
                        bounded(string(ingredient, "name", ingredientSource), 64,
                            ingredientSource + " name"),
                        itemIds
                    ));
                }
                List<String> outputItems = stringList(recipe, "output_item_ids", recipeSource).stream()
                    .map(value -> resourceId(value, recipeSource + " output_item_ids"))
                    .toList();
                result.add(new DungeonPotionBarterCatalog.Recipe(
                    resourceId(string(recipe, "id", recipeSource), recipeSource + " id"),
                    outputItems,
                    positiveInt(recipe, "output_count", recipeSource),
                    bounded(string(recipe, "output_name", recipeSource), 64,
                        recipeSource + " output_name"),
                    bounded(string(recipe, "family", recipeSource), 24,
                        recipeSource + " family"),
                    enumValue(
                        DungeonShopSnapshotPayload.Icon.class,
                        string(recipe, "icon", recipeSource),
                        recipeSource + " icon"
                    ),
                    color(optionalString(recipe, "accent", "38D7FF", recipeSource),
                        recipeSource + " accent"),
                    bounded(optionalString(recipe, "grade", "Normal", recipeSource), 24,
                        recipeSource + " grade"),
                    bounded(optionalString(recipe, "display_type", "Barter", recipeSource), 24,
                        recipeSource + " display_type"),
                    bounded(optionalString(recipe, "comparison", "Configured barter exchange",
                        recipeSource), 128, recipeSource + " comparison"),
                    List.copyOf(parsedIngredients)
                ));
            }
            return List.copyOf(result);
        }
    }

    private static void validate(Snapshot snapshot, Predicate<String> itemExists) {
        Set<String> offerIds = new HashSet<>();
        Set<CoreSatchelData.SatchelGrade> satchelGrades = new HashSet<>();
        Set<CosmeticKey> cosmetics = new HashSet<>();
        for (DungeonShopCatalog.OfferDefinition offer : snapshot.allOffers()) {
            if (!offerIds.add(offer.id())) {
                throw invalid("Dungeon Shop config", "duplicates offer id " + offer.id());
            }
            validateOffer(offer, itemExists);
            if (offer.priceScalesWithGrade()) {
                for (Map.Entry<DungeonShopCatalog.Grade, GradePriceMultiplier> multiplier
                    : snapshot.gradePriceMultipliers().entrySet()) {
                    try {
                        long price = multiplier.getValue().apply(offer.basePriceBronze());
                        if (offer.basePriceBronze() > 0L && price <= 0L) {
                            throw invalid(
                                offer.id(),
                                multiplier.getKey().displayName()
                                    + " grade multiplier rounds a nonzero price to zero"
                            );
                        }
                    } catch (ArithmeticException exception) {
                        throw invalid(
                            offer.id(),
                            multiplier.getKey().displayName() + " grade price overflows"
                        );
                    }
                }
            }
            if (offer.satchelGrade() != null && !satchelGrades.add(offer.satchelGrade())) {
                throw invalid("Dungeon Shop config", "duplicates satchel grade "
                    + offer.satchelGrade().id());
            }
            if (offer.cosmetic() != null && !cosmetics.add(offer.cosmetic())) {
                throw invalid("Dungeon Shop config", "duplicates cosmetic "
                    + offer.cosmetic().canonicalId());
            }
        }
        Set<String> barterIds = new HashSet<>();
        for (DungeonPotionBarterCatalog.Recipe recipe : snapshot.barters()) {
            if (!barterIds.add(recipe.id()) || offerIds.contains(recipe.id())) {
                throw invalid("Dungeon Shop config", "duplicates id " + recipe.id());
            }
            if (recipe.outputItemIds().stream().noneMatch(itemExists)) {
                throw invalid(recipe.id(), "has no registered output item");
            }
            if (recipe.outputCount() > 2_304) {
                throw invalid(recipe.id(), "output_count exceeds one player inventory");
            }
            if (recipe.icon() == DungeonShopSnapshotPayload.Icon.THEME) {
                throw invalid(recipe.id(), "barters cannot use the reserved THEME icon");
            }
            for (DungeonPotionBarterCatalog.Ingredient ingredient : recipe.ingredients()) {
                if (ingredient.itemIds().stream().noneMatch(itemExists)) {
                    throw invalid(recipe.id(), "ingredient has no registered item: "
                        + ingredient.displayName());
                }
            }
        }
    }

    private static void validateOffer(
        DungeonShopCatalog.OfferDefinition offer,
        Predicate<String> itemExists
    ) {
        if (offer.minimumQuantity() > offer.maximumQuantity()) {
            throw invalid(offer.id(), "stock minimum exceeds maximum");
        }
        if (offer.outputCount() > 2_304) {
            throw invalid(offer.id(), "output_count exceeds one player inventory");
        }
        if (offer.appearanceBasisPoints() < 0 || offer.appearanceBasisPoints() > 10_000) {
            throw invalid(offer.id(), "appearance_basis_points must be between 0 and 10000");
        }
        if (offer.guaranteedByEligibleWeek() < 1) {
            throw invalid(offer.id(), "guaranteed_by_eligible_period must be positive");
        }
        switch (offer.productType()) {
            case ITEM, RECOVERY_POTION -> {
                if (offer.itemIds().isEmpty() || offer.itemIds().stream().noneMatch(itemExists)) {
                    throw invalid(offer.id(), "has no registered grant item");
                }
                if (offer.cosmetic() != null || offer.satchelGrade() != null) {
                    throw invalid(offer.id(), "item products cannot carry special persistence keys");
                }
                if (offer.icon() == DungeonShopSnapshotPayload.Icon.THEME) {
                    throw invalid(offer.id(), "ordinary items cannot use the reserved THEME icon");
                }
            }
            case HUD_THEME -> {
                if (offer.category() != DungeonShopCatalog.Category.WEEKLY
                    || offer.cosmetic() == null || !offer.cosmetic().cataloged()
                    || !offer.itemIds().isEmpty() || offer.satchelGrade() != null
                    || offer.icon() != DungeonShopSnapshotPayload.Icon.THEME) {
                    throw invalid(offer.id(), "HUD themes require one approved weekly cosmetic key");
                }
                String expectedId = approvedCosmeticOfferId(offer.cosmetic());
                if (!offer.id().equals(expectedId)) {
                    throw invalid(offer.id(), "does not match its approved cosmetic identity");
                }
            }
            case SATCHEL_UPGRADE -> {
                if (offer.category() != DungeonShopCatalog.Category.TODAYS_FINDS
                    || offer.satchelGrade() == null
                    || offer.satchelGrade() == CoreSatchelData.SatchelGrade.NORMAL
                    || offer.cosmetic() != null || !offer.itemIds().isEmpty()) {
                    throw invalid(offer.id(), "satchel upgrades require a paid satchel grade");
                }
            }
        }
        if (offer.productType() == DungeonShopCatalog.ProductType.RECOVERY_POTION
            && offer.category() != DungeonShopCatalog.Category.ESSENTIALS) {
            throw invalid(offer.id(), "recovery potions belong in Essentials");
        }
        if (offer.priceScalesWithGrade()
            && offer.productType() != DungeonShopCatalog.ProductType.RECOVERY_POTION) {
            throw invalid(offer.id(), "only recovery potion prices may scale with grade");
        }
        if (offer.category() == DungeonShopCatalog.Category.WEEKLY
            && offer.stockMode() != DungeonShopCatalog.StockMode.PER_PERIOD) {
            throw invalid(offer.id(), "Weekly offers must use PER_PERIOD stock");
        }
        if (offer.productType() == DungeonShopCatalog.ProductType.SATCHEL_UPGRADE
            && offer.stockMode() != DungeonShopCatalog.StockMode.PER_PERIOD) {
            throw invalid(offer.id(), "satchel upgrades must use PER_PERIOD stock");
        }
        if (offer.category() != DungeonShopCatalog.Category.WEEKLY
            && offer.productType() != DungeonShopCatalog.ProductType.SATCHEL_UPGRADE
            && offer.stockMode() == DungeonShopCatalog.StockMode.PER_PERIOD) {
            throw invalid(offer.id(), "Daily Shop items must use UNLIMITED or DAILY stock");
        }
    }

    private static boolean registeredItem(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        return key != null && BuiltInRegistries.ITEM.getOptional(key)
            .filter(item -> item != Items.AIR)
            .isPresent();
    }

    private static String approvedCosmeticOfferId(CosmeticKey cosmetic) {
        if (CosmeticKey.HUD_GOTHIC.equals(cosmetic)) {
            return "tensura_dungeon:gothic_hud_theme";
        }
        if (CosmeticKey.HUD_FREY.equals(cosmetic)) {
            return "tensura_dungeon:frey_hud_theme";
        }
        if (CosmeticKey.QUEST_GOTHIC.equals(cosmetic)) {
            return "tensura_dungeon:gothic_quest_hud_theme";
        }
        return "";
    }

    private static void requireSchema(JsonObject root, String source) {
        int schema = positiveInt(root, "schema", source);
        if (schema != CURRENT_SCHEMA) {
            throw invalid(source, "uses unsupported schema " + schema);
        }
    }

    private static long priceBronze(JsonObject price, String source) {
        long bronze = optionalLong(price, "bronze", 0L, source);
        long silver = optionalLong(price, "silver", 0L, source);
        long gold = optionalLong(price, "gold", 0L, source);
        long stellar = optionalLong(price, "stellar_gold", 0L, source);
        if (bronze < 0L || silver < 0L || gold < 0L || stellar < 0L) {
            throw invalid(source, "price denominations cannot be negative");
        }
        try {
            return Math.addExact(
                Math.addExact(
                    bronze,
                    Math.multiplyExact(silver, DungeonCurrencyPolicy.BRONZE_PER_SILVER)
                ),
                Math.addExact(
                    Math.multiplyExact(gold, DungeonCurrencyPolicy.BRONZE_PER_GOLD),
                    Math.multiplyExact(stellar, DungeonCurrencyPolicy.BRONZE_PER_STELLAR)
                )
            );
        } catch (ArithmeticException exception) {
            throw invalid(source, "price is too large");
        }
    }

    private static int color(String value, String source) {
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        if (!normalized.matches("[0-9a-fA-F]{6}")) {
            throw invalid(source, "must be a six-digit RGB color");
        }
        return 0xFF000000 | Integer.parseInt(normalized, 16);
    }

    private static String resourceId(String value, String source) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null || value.length() > 64) {
            throw invalid(source, "is not a valid namespaced id: " + value);
        }
        return parsed.toString();
    }

    private static <E extends Enum<E>> E enumValue(
        Class<E> type,
        String value,
        String source
    ) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(source, "has unsupported value " + value);
        }
    }

    private static JsonElement required(JsonObject object, String key, String source) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            throw invalid(source, "is missing " + key);
        }
        return value;
    }

    private static JsonObject object(JsonElement element, String source) {
        if (element == null || !element.isJsonObject()) {
            throw invalid(source, "must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String key, String source) {
        JsonElement element = required(object, key, source);
        if (!element.isJsonArray()) {
            throw invalid(source, key + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static String string(JsonObject object, String key, String source) {
        JsonElement element = required(object, key, source);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw invalid(source, key + " must be text");
        }
        String value = element.getAsString().trim();
        if (value.isEmpty()) {
            throw invalid(source, key + " cannot be blank");
        }
        return value;
    }

    private static String optionalString(
        JsonObject object,
        String key,
        String fallback,
        String source
    ) {
        return object.has(key) ? string(object, key, source) : fallback;
    }

    private static boolean optionalBoolean(
        JsonObject object,
        String key,
        boolean fallback,
        String source
    ) {
        if (!object.has(key)) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw invalid(source, key + " must be true or false");
        }
        return element.getAsBoolean();
    }

    private static int positiveInt(JsonObject object, String key, String source) {
        int value = exactInt(required(object, key, source), source + " " + key);
        if (value <= 0) {
            throw invalid(source, key + " must be positive");
        }
        return value;
    }

    private static int nonNegativeInt(JsonObject object, String key, String source) {
        int value = exactInt(required(object, key, source), source + " " + key);
        if (value < 0) {
            throw invalid(source, key + " cannot be negative");
        }
        return value;
    }

    private static int optionalInt(JsonObject object, String key, int fallback, String source) {
        return object.has(key) ? exactInt(object.get(key), source + " " + key) : fallback;
    }

    private static long optionalLong(JsonObject object, String key, long fallback, String source) {
        if (!object.has(key)) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw invalid(source, key + " must be a whole number");
        }
        try {
            return element.getAsLong();
        } catch (NumberFormatException exception) {
            throw invalid(source, key + " must be a whole number");
        }
    }

    private static int exactInt(JsonElement element, String source) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw invalid(source, "must be a whole number");
        }
        try {
            long value = element.getAsLong();
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw invalid(source, "is outside the supported integer range");
            }
            return (int) value;
        } catch (NumberFormatException exception) {
            throw invalid(source, "must be a whole number");
        }
    }

    private static List<String> stringList(JsonObject object, String key, String source) {
        JsonArray array = array(object, key, source);
        if (array.isEmpty()) {
            throw invalid(source, key + " cannot be empty");
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw invalid(source, key + " must contain only text values");
            }
            String value = element.getAsString().trim();
            if (value.isEmpty()) {
                throw invalid(source, key + " cannot contain blank values");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static List<String> optionalStringList(
        JsonObject object,
        String key,
        String source
    ) {
        return object.has(key) ? stringList(object, key, source) : List.of();
    }

    private static String bounded(String value, int maximum, String source) {
        if (value.length() > maximum) {
            throw invalid(source, "exceeds " + maximum + " characters");
        }
        return value;
    }

    private static IllegalArgumentException invalid(String source, String message) {
        return new IllegalArgumentException(source + ": " + message);
    }

    public record Snapshot(
        List<DungeonShopCatalog.OfferDefinition> essentials,
        List<DungeonShopCatalog.OfferDefinition> todaysFinds,
        List<DungeonShopCatalog.OfferDefinition> weekly,
        List<DungeonPotionBarterCatalog.Recipe> barters,
        Map<DungeonShopCatalog.Grade, GradePriceMultiplier> gradePriceMultipliers
    ) {
        public Snapshot(
            List<DungeonShopCatalog.OfferDefinition> essentials,
            List<DungeonShopCatalog.OfferDefinition> todaysFinds,
            List<DungeonShopCatalog.OfferDefinition> weekly,
            List<DungeonPotionBarterCatalog.Recipe> barters
        ) {
            this(essentials, todaysFinds, weekly, barters, defaultGradePriceMultipliers());
        }

        public Snapshot {
            essentials = List.copyOf(essentials);
            todaysFinds = List.copyOf(todaysFinds);
            weekly = List.copyOf(weekly);
            barters = List.copyOf(barters);
            gradePriceMultipliers = Map.copyOf(gradePriceMultipliers);
            if (gradePriceMultipliers.size() != DungeonShopCatalog.Grade.values().length) {
                throw new IllegalArgumentException(
                    "Dungeon Shop config requires one price multiplier per grade"
                );
            }
        }

        public List<DungeonShopCatalog.OfferDefinition> allOffers() {
            List<DungeonShopCatalog.OfferDefinition> result = new ArrayList<>(
                essentials.size() + todaysFinds.size() + weekly.size()
            );
            result.addAll(essentials);
            result.addAll(todaysFinds);
            result.addAll(weekly);
            return List.copyOf(result);
        }
    }

    private record OfferFile(
        List<DungeonShopCatalog.OfferDefinition> offers,
        Map<DungeonShopCatalog.Grade, GradePriceMultiplier> gradePriceMultipliers
    ) {
        private OfferFile {
            offers = List.copyOf(offers);
            gradePriceMultipliers = Map.copyOf(gradePriceMultipliers);
        }
    }

    public record GradePriceMultiplier(long numerator, long denominator) {
        public GradePriceMultiplier {
            if (numerator <= 0L || denominator <= 0L
                || numerator > 10_000L || denominator > 10_000L) {
                throw new IllegalArgumentException(
                    "Dungeon Shop grade price multiplier must be within 1..10000"
                );
            }
        }

        public long apply(long basePriceBronze) {
            if (basePriceBronze < 0L) {
                throw new IllegalArgumentException("Dungeon Shop base price cannot be negative");
            }
            return Math.multiplyExact(basePriceBronze, numerator) / denominator;
        }
    }

    public record ValidationReport(
        boolean valid,
        Path directory,
        String message,
        int essentials,
        int todaysFinds,
        int weekly,
        int barters
    ) {
        static ValidationReport valid(Path directory, Snapshot snapshot) {
            return new ValidationReport(
                true, directory, "All four Dungeon Shop files are valid",
                snapshot.essentials().size(), snapshot.todaysFinds().size(),
                snapshot.weekly().size(), snapshot.barters().size()
            );
        }

        static ValidationReport invalid(Path directory, String message) {
            String detail = message == null || message.isBlank()
                ? "Unknown validation failure" : message;
            return new ValidationReport(false, directory, detail, 0, 0, 0, 0);
        }
    }
}
