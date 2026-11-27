package com.mineclawd.tensuradungeon.reward;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.minecraft.resources.ResourceLocation;

public record DungeonCrystalEconomyDefinition(
    int schema,
    Map<EncounterClass, EncounterRule> encounterRules,
    List<RegularBand> regularBands,
    PinnacleDefinition pinnacle
) {
    private static final List<String> EXPECTED_BANDS = List.of(
        "TIER_I", "TIER_II", "TIER_III", "TIER_IV", "TIER_V", "TIER_VI"
    );

    public DungeonCrystalEconomyDefinition {
        if (schema != 1) {
            throw new IllegalArgumentException("Unsupported crystal economy schema: " + schema);
        }
        encounterRules = Map.copyOf(Objects.requireNonNull(encounterRules, "encounterRules"));
        regularBands = List.copyOf(Objects.requireNonNull(regularBands, "regularBands"));
        pinnacle = Objects.requireNonNull(pinnacle, "pinnacle");
        if (!encounterRules.keySet().equals(Set.of(EncounterClass.values()))) {
            throw new IllegalArgumentException("Crystal economy must define every encounter class exactly once");
        }
        if (regularBands.size() != EXPECTED_BANDS.size()) {
            throw new IllegalArgumentException("Crystal economy must define exactly six regular bands");
        }

        int expectedMinimumFloor = 1;
        Set<String> itemIds = new HashSet<>();
        for (int index = 0; index < regularBands.size(); index++) {
            RegularBand band = regularBands.get(index);
            if (!EXPECTED_BANDS.get(index).equals(band.id())) {
                throw new IllegalArgumentException("Unexpected regular band order at index " + index + ": " + band.id());
            }
            if (band.minimumFloor() != expectedMinimumFloor) {
                throw new IllegalArgumentException("Regular crystal bands are not contiguous before " + band.id());
            }
            expectedMinimumFloor = band.maximumFloor() + 1;
            if (!itemIds.add(band.itemId())) {
                throw new IllegalArgumentException("Duplicate regular crystal item: " + band.itemId());
            }
            for (EncounterClass encounterClass : EncounterClass.values()) {
                EncounterRule rule = encounterRules.get(encounterClass);
                int active = rule.baseOpportunities() + band.unlockedOpportunities().get(encounterClass);
                if (active > rule.hardCap()) {
                    throw new IllegalArgumentException(
                        band.id() + " exceeds " + encounterClass + " opportunity cap: " + active + "/" + rule.hardCap()
                    );
                }
            }
        }
        if (expectedMinimumFloor != 301) {
            throw new IllegalArgumentException("Regular crystal bands must cover floors 1-300 exactly");
        }
        if (itemIds.contains(pinnacle.itemId())) {
            throw new IllegalArgumentException("Pinnacle Capsule cannot share a regular-band item identity");
        }
    }

    public static DungeonCrystalEconomyDefinition parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        requireExactKeys(root, Set.of("schema", "encounter_rules", "regular_bands", "pinnacle"), "root");
        int schema = requiredInt(root, "schema");

        EnumMap<EncounterClass, EncounterRule> encounterRules = new EnumMap<>(EncounterClass.class);
        JsonObject encounters = requiredObject(root, "encounter_rules");
        requireExactKeys(encounters, serializedEncounterNames(), "encounter_rules");
        for (EncounterClass encounterClass : EncounterClass.values()) {
            JsonObject rule = requiredObject(encounters, encounterClass.serializedName());
            requireExactKeys(
                rule,
                Set.of("base_opportunities", "hard_cap", "session_budget_count"),
                encounterClass.serializedName()
            );
            encounterRules.put(encounterClass, new EncounterRule(
                requiredInt(rule, "base_opportunities"),
                requiredInt(rule, "hard_cap"),
                requiredInt(rule, "session_budget_count")
            ));
        }

        List<RegularBand> bands = new ArrayList<>();
        JsonArray bandEntries = requiredArray(root, "regular_bands");
        for (JsonElement element : bandEntries) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Regular crystal band entry must be an object");
            }
            JsonObject band = element.getAsJsonObject();
            requireExactKeys(band, Set.of(
                "id", "minimum_floor", "maximum_floor", "item", "native_refining_output", "refining_batch",
                "success_basis_points", "rare_same_tier_bonus_basis_points", "unlocked_opportunities"
            ), "regular band");
            JsonObject success = requiredObject(band, "success_basis_points");
            JsonObject rareBonus = requiredObject(band, "rare_same_tier_bonus_basis_points");
            JsonObject unlocked = requiredObject(band, "unlocked_opportunities");
            requireExactKeys(success, Set.of("start", "end"), "success_basis_points");
            requireExactKeys(rareBonus, Set.of("start", "end"), "rare_same_tier_bonus_basis_points");
            requireExactKeys(unlocked, serializedEncounterNames(), "unlocked_opportunities");
            EnumMap<EncounterClass, Integer> opportunities = new EnumMap<>(EncounterClass.class);
            for (EncounterClass encounterClass : EncounterClass.values()) {
                opportunities.put(encounterClass, requiredInt(unlocked, encounterClass.serializedName()));
            }
            bands.add(new RegularBand(
                requiredString(band, "id"),
                requiredInt(band, "minimum_floor"),
                requiredInt(band, "maximum_floor"),
                requiredString(band, "item"),
                requiredString(band, "native_refining_output"),
                requiredInt(band, "refining_batch"),
                new BasisPointRange(requiredInt(success, "start"), requiredInt(success, "end")),
                new BasisPointRange(requiredInt(rareBonus, "start"), requiredInt(rareBonus, "end")),
                opportunities
            ));
        }

        JsonObject pinnacle = requiredObject(root, "pinnacle");
        requireExactKeys(pinnacle, Set.of("item", "floor", "source", "refinable"), "pinnacle");
        return new DungeonCrystalEconomyDefinition(
            schema,
            encounterRules,
            bands,
            new PinnacleDefinition(
                requiredString(pinnacle, "item"),
                requiredInt(pinnacle, "floor"),
                EncounterClass.parse(requiredString(pinnacle, "source")),
                requiredBoolean(pinnacle, "refinable")
            )
        );
    }

    public RegularBand bandForFloor(int floorNumber) {
        if (floorNumber < 1 || floorNumber > 300) {
            throw new IllegalArgumentException("Crystal band floor must be between 1 and 300: " + floorNumber);
        }
        return regularBands.stream()
            .filter(band -> floorNumber >= band.minimumFloor() && floorNumber <= band.maximumFloor())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Validated crystal bands do not cover floor " + floorNumber));
    }

    public int activeOpportunities(String bandId, EncounterClass encounterClass) {
        Objects.requireNonNull(encounterClass, "encounterClass");
        RegularBand band = regularBands.stream().filter(candidate -> candidate.id().equals(bandId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown regular crystal band: " + bandId));
        EncounterRule rule = encounterRules.get(encounterClass);
        return rule.baseOpportunities() + band.unlockedOpportunities().get(encounterClass);
    }

    public RollResult rollRegular(int floorNumber, EncounterClass encounterClass, IntUnaryOperator nextInt) {
        Objects.requireNonNull(encounterClass, "encounterClass");
        Objects.requireNonNull(nextInt, "nextInt");
        RegularBand band = bandForFloor(floorNumber);
        int opportunities = activeOpportunities(band.id(), encounterClass);
        int successBasisPoints = interpolate(floorNumber, band, band.successBasisPoints());
        int rareBasisPoints = interpolate(floorNumber, band, band.rareSameTierBonusBasisPoints());
        int awarded = 0;
        for (int opportunity = 0; opportunity < opportunities; opportunity++) {
            if (boundedRoll(nextInt) >= successBasisPoints) continue;
            awarded++;
            if (boundedRoll(nextInt) < rareBasisPoints) awarded++;
        }
        return new RollResult(opportunities, successBasisPoints, rareBasisPoints, awarded);
    }

    private static int interpolate(int floorNumber, RegularBand band, BasisPointRange range) {
        int span = band.maximumFloor() - band.minimumFloor();
        if (span == 0) return range.end();
        double progress = (floorNumber - band.minimumFloor()) / (double) span;
        return range.start() + (int) Math.round((range.end() - range.start()) * progress);
    }

    private static int boundedRoll(IntUnaryOperator nextInt) {
        int value = nextInt.applyAsInt(10_000);
        if (value < 0 || value >= 10_000) {
            throw new IllegalArgumentException("Crystal roll source returned an out-of-range value: " + value);
        }
        return value;
    }

    public enum EncounterClass {
        ORDINARY,
        ELITE,
        MINIBOSS,
        BOSS,
        CHARYBDIS,
        GUARDIAN;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static EncounterClass parse(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Unknown crystal encounter class: " + value, exception);
            }
        }
    }

    public record EncounterRule(int baseOpportunities, int hardCap, int sessionBudgetCount) {
        public EncounterRule {
            if (baseOpportunities < 0 || hardCap < baseOpportunities || sessionBudgetCount <= 0) {
                throw new IllegalArgumentException(
                    "Invalid encounter opportunity rule: base=" + baseOpportunities + ", cap=" + hardCap
                        + ", session budgets=" + sessionBudgetCount
                );
            }
        }
    }

    public record RollResult(
        int opportunities,
        int successBasisPoints,
        int rareSameTierBasisPoints,
        int awardedCrystals
    ) {
        public RollResult {
            if (opportunities < 0 || successBasisPoints < 0 || successBasisPoints > 10_000
                || rareSameTierBasisPoints < 0 || rareSameTierBasisPoints > 10_000
                || awardedCrystals < 0 || awardedCrystals > opportunities * 2) {
                throw new IllegalArgumentException("Invalid regular crystal roll result");
            }
        }
    }

    public record BasisPointRange(int start, int end) {
        public BasisPointRange {
            if (start < 0 || start > end || end > 10_000) {
                throw new IllegalArgumentException("Invalid basis-point range: " + start + "-" + end);
            }
        }
    }

    public record RegularBand(
        String id,
        int minimumFloor,
        int maximumFloor,
        String itemId,
        String nativeRefiningOutputId,
        int refiningBatch,
        BasisPointRange successBasisPoints,
        BasisPointRange rareSameTierBonusBasisPoints,
        Map<EncounterClass, Integer> unlockedOpportunities
    ) {
        public RegularBand {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(successBasisPoints, "successBasisPoints");
            Objects.requireNonNull(rareSameTierBonusBasisPoints, "rareSameTierBonusBasisPoints");
            requireResourceLocation(itemId, "regular crystal item");
            requireResourceLocation(nativeRefiningOutputId, "native refining output");
            if (minimumFloor < 1 || maximumFloor < minimumFloor || maximumFloor > 300) {
                throw new IllegalArgumentException("Invalid regular crystal band range for " + id);
            }
            if (refiningBatch <= 0) {
                throw new IllegalArgumentException("Refining batch must be positive for " + id);
            }
            unlockedOpportunities = Map.copyOf(Objects.requireNonNull(unlockedOpportunities, "unlockedOpportunities"));
            if (!unlockedOpportunities.keySet().equals(Set.of(EncounterClass.values()))) {
                throw new IllegalArgumentException(id + " must define unlocks for every encounter class");
            }
            if (unlockedOpportunities.values().stream().anyMatch(value -> value == null || value < 0)) {
                throw new IllegalArgumentException(id + " has a negative or missing encounter unlock");
            }
        }
    }

    public record PinnacleDefinition(String itemId, int floor, EncounterClass source, boolean refinable) {
        public PinnacleDefinition {
            requireResourceLocation(itemId, "Pinnacle item");
            Objects.requireNonNull(source, "source");
            if (floor != 300 || source != EncounterClass.GUARDIAN || refinable) {
                throw new IllegalArgumentException(
                    "Pinnacle must remain non-refinable and exclusive to the Depth 300 Guardian"
                );
            }
        }
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            throw new IllegalArgumentException("Missing object: " + key);
        }
        return object.getAsJsonObject(key);
    }

    private static JsonArray requiredArray(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw new IllegalArgumentException("Missing array: " + key);
        }
        return object.getAsJsonArray(key);
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing string: " + key);
        }
        String value = object.get(key).getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Blank string: " + key);
        }
        return value;
    }

    private static int requiredInt(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing integer: " + key);
        }
        return object.get(key).getAsInt();
    }

    private static boolean requiredBoolean(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing boolean: " + key);
        }
        return object.get(key).getAsBoolean();
    }

    private static void requireResourceLocation(String value, String label) {
        if (value == null || ResourceLocation.tryParse(value) == null) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    private static Set<String> serializedEncounterNames() {
        Set<String> names = new HashSet<>();
        for (EncounterClass encounterClass : EncounterClass.values()) {
            names.add(encounterClass.serializedName());
        }
        return Set.copyOf(names);
    }

    private static void requireExactKeys(JsonObject object, Set<String> expected, String label) {
        if (!object.keySet().equals(expected)) {
            throw new IllegalArgumentException(
                label + " fields must be exactly " + expected + ", found " + object.keySet()
            );
        }
    }
}
