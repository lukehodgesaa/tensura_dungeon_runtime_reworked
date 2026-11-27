package dev.luke.tensuradungeon.reward;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RewardProfileRegistry {
    public static final String PROFILE_RESOURCE =
            "/data/tensura_dungeon_ui/dungeon_rewards/reward_profiles.json";
    public static final String MODIFIER_RESOURCE =
            "/data/tensura_dungeon_ui/dungeon_rewards/modifier_reward_effects.json";

    private static final RewardProfileRegistry INSTANCE = loadDefault();

    public record IntRange(int min, int max) {
        public IntRange {
            int safeMin = Math.max(0, Math.min(min, max));
            int safeMax = Math.max(safeMin, Math.max(min, max));
            min = safeMin;
            max = safeMax;
        }

        public int clamp(int value) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public record CrystalRanges(IntRange low, IntRange medium, IntRange high) {}

    public record CofferDefinition(
            int count,
            boolean guaranteed,
            String lootTable,
            String displayName,
            String quality
    ) {}

    public record EquipmentDefinition(
            int count,
            boolean guaranteed,
            String qualityProfile,
            String displayName
    ) {}

    public record BossMaterialDefinition(
            boolean guaranteed,
            double chance,
            String lootTable,
            String displayName,
            String iconId
    ) {}

    public record RewardProfile(
            String id,
            int referenceFloor,
            double perFloorGrowth,
            IntRange ep,
            int firstClearEpBonus,
            IntRange maxEpCapacity,
            boolean capacityFirstClearOnly,
            boolean capacityMilestoneOnly,
            int repeatCapacityValue,
            IntRange coinBronze,
            CrystalRanges crystals,
            CofferDefinition guardianCoffer,
            IntRange recoverySupplies,
            String recoveryLootTable,
            EquipmentDefinition equipmentCache,
            BossMaterialDefinition bossMaterial,
            double recordingCrystalChance,
            double recordingCrystalFirstClearBonus,
            double masterworkChance
    ) {}

    public record ProfileRule(
            String profile,
            int minFloor,
            int maxFloor,
            int divisibleBy,
            boolean bossOnly,
            String themeContains
    ) {
        boolean matches(int floor, boolean boss, String theme) {
            if (floor < minFloor || floor > maxFloor) return false;
            if (divisibleBy > 0 && floor % divisibleBy != 0) return false;
            if (bossOnly && !boss) return false;
            if (themeContains != null && !themeContains.isBlank()) {
                String normalizedTheme = theme == null ? "" : theme.toLowerCase(Locale.ROOT);
                if (!normalizedTheme.contains(themeContains.toLowerCase(Locale.ROOT))) return false;
            }
            return true;
        }
    }

    public record ModifierRewardEffect(
            double epMultiplier,
            double coinMultiplier,
            double crystalQuantityMultiplier,
            double equipmentQualityBonus,
            double cofferQualityBonus,
            double bossMaterialBonus,
            double recordingCrystalBonus,
            double masterworkBonus,
            String materialPool
    ) {
        static ModifierRewardEffect neutral() {
            return new ModifierRewardEffect(1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, "");
        }
    }

    public record AppliedRewardEffects(
            double epMultiplier,
            double coinMultiplier,
            double crystalQuantityMultiplier,
            double equipmentQualityBonus,
            double cofferQualityBonus,
            double bossMaterialBonus,
            double recordingCrystalBonus,
            double masterworkBonus,
            List<String> materialPools,
            List<String> contributingModifiers
    ) {}

    public record RewardPlan(
            int floor,
            String profileId,
            boolean firstClearAvailable,
            IntRange ep,
            int firstClearEpBonus,
            IntRange maxEpCapacity,
            boolean capacityFirstClearOnly,
            boolean capacityMilestoneOnly,
            int repeatCapacityValue,
            IntRange coinBronze,
            CrystalRanges crystals,
            CofferDefinition guardianCoffer,
            IntRange recoverySupplies,
            String recoveryLootTable,
            EquipmentDefinition equipmentCache,
            BossMaterialDefinition bossMaterial,
            double recordingCrystalChance,
            double masterworkChance,
            AppliedRewardEffects appliedEffects
    ) {}

    public record PotentialReward(
            String typeId,
            int amount,
            String value,
            String details,
            boolean firstClearOnly
    ) {}

    private final Map<String, RewardProfile> profiles;
    private final List<ProfileRule> rules;
    private final Map<Integer, String> floorOverrides;
    private final Map<String, ModifierRewardEffect> modifierEffects;
    private final RewardProfile fallbackProfile;

    private RewardProfileRegistry(
            Map<String, RewardProfile> profiles,
            List<ProfileRule> rules,
            Map<Integer, String> floorOverrides,
            Map<String, ModifierRewardEffect> modifierEffects,
            RewardProfile fallbackProfile
    ) {
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        this.rules = List.copyOf(rules);
        this.floorOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(floorOverrides));
        this.modifierEffects = Collections.unmodifiableMap(new LinkedHashMap<>(modifierEffects));
        this.fallbackProfile = fallbackProfile;
    }

    public static RewardProfileRegistry instance() {
        return INSTANCE;
    }

    public Map<String, RewardProfile> profiles() {
        return profiles;
    }

    public Map<String, ModifierRewardEffect> modifierEffects() {
        return modifierEffects;
    }

    public RewardProfile profileForFloor(int floor, boolean boss, String themeName) {
        int safeFloor = Math.max(1, Math.min(999, floor));
        String override = floorOverrides.get(safeFloor);
        if (override != null && profiles.containsKey(override)) return profiles.get(override);
        for (ProfileRule rule : rules) {
            if (rule.matches(safeFloor, boss, themeName)) {
                RewardProfile profile = profiles.get(rule.profile());
                if (profile != null) return profile;
            }
        }
        return fallbackProfile;
    }

    public RewardPlan planForFloor(
            int floor,
            boolean boss,
            String themeName,
            Collection<String> modifierIds,
            boolean firstClearAvailable
    ) {
        RewardProfile profile = profileForFloor(floor, boss, themeName);
        double depthMultiplier = depthMultiplier(profile, floor);
        AppliedRewardEffects effects = combineModifierEffects(modifierIds);

        IntRange baseEp = scale(profile.ep(), depthMultiplier);
        IntRange adjustedEp = scale(baseEp, effects.epMultiplier());
        IntRange baseCoin = scale(profile.coinBronze(), depthMultiplier);
        IntRange adjustedCoin = scale(baseCoin, effects.coinMultiplier());

        CrystalRanges scaledCrystals = new CrystalRanges(
                scaleQuantity(profile.crystals().low(), depthMultiplier),
                scaleQuantity(profile.crystals().medium(), depthMultiplier),
                scaleQuantity(profile.crystals().high(), depthMultiplier)
        );
        CrystalRanges adjustedCrystals = new CrystalRanges(
                scaleQuantity(scaledCrystals.low(), effects.crystalQuantityMultiplier()),
                scaleQuantity(scaledCrystals.medium(), effects.crystalQuantityMultiplier()),
                scaleQuantity(scaledCrystals.high(), effects.crystalQuantityMultiplier())
        );

        IntRange capacity = profile.maxEpCapacity();
        boolean milestoneEligible = !profile.capacityMilestoneOnly() || floor % 10 == 0;
        boolean capacityAllowed = milestoneEligible
                && (firstClearAvailable || !profile.capacityFirstClearOnly());
        if (!capacityAllowed) {
            capacity = new IntRange(profile.repeatCapacityValue(), profile.repeatCapacityValue());
        }

        double recordingChance = clampChance(
                profile.recordingCrystalChance()
                        + (firstClearAvailable ? profile.recordingCrystalFirstClearBonus() : 0.0)
                        + effects.recordingCrystalBonus(),
                0.25
        );
        double masterworkChance = clampChance(
                profile.masterworkChance() + effects.masterworkBonus(),
                0.20
        );

        BossMaterialDefinition material = profile.bossMaterial();
        if (material != null && !material.guaranteed()) {
            material = new BossMaterialDefinition(
                    false,
                    clampChance(material.chance() + effects.bossMaterialBonus(), 0.75),
                    material.lootTable(),
                    material.displayName(),
                    material.iconId()
            );
        }

        return new RewardPlan(
                floor,
                profile.id(),
                firstClearAvailable,
                adjustedEp,
                scaleValue(profile.firstClearEpBonus(), depthMultiplier),
                capacity,
                profile.capacityFirstClearOnly(),
                profile.capacityMilestoneOnly(),
                profile.repeatCapacityValue(),
                adjustedCoin,
                adjustedCrystals,
                profile.guardianCoffer(),
                scale(profile.recoverySupplies(), depthMultiplier),
                profile.recoveryLootTable(),
                profile.equipmentCache(),
                material,
                recordingChance,
                masterworkChance,
                effects
        );
    }

    public List<PotentialReward> potentialRewards(RewardPlan plan) {
        List<PotentialReward> rewards = new ArrayList<>(10);

        boolean milestoneFirstClear = plan.floor() % 10 == 0 && plan.firstClearAvailable();
        if (milestoneFirstClear && plan.maxEpCapacity().max() > 0) {
            rewards.add(new PotentialReward(
                    "max-ep-capacity",
                    plan.maxEpCapacity().max(),
                    "+" + compactRange(plan.maxEpCapacity()),
                    "Maximum EP Capacity\nFirst-clear milestone\nEvery 10 floors",
                    true
            ));
        }

        addCoinPreviews(rewards, plan.coinBronze());

        addCrystalPreview(rewards, "magic-crystal-low", "Low-Quality Magic Crystal",
                plan.crystals().low());
        addCrystalPreview(rewards, "magic-crystal-medium", "Medium-Quality Magic Crystal",
                plan.crystals().medium());
        addCrystalPreview(rewards, "magic-crystal-high", "High-Quality Magic Crystal",
                plan.crystals().high());

        EquipmentDefinition equipment = plan.equipmentCache();
        if (equipment != null && equipment.count() > 0) {
            String details = "Equipment Cache\n"
                    + (equipment.guaranteed() ? "Guaranteed completion reward" : "Rare completion reward")
                    + "\nRequires an active equipment pool";
            rewards.add(new PotentialReward(
                    "equipment-cache",
                    equipment.count(),
                    String.valueOf(equipment.count()),
                    details,
                    false
            ));
        }

        if (milestoneFirstClear && plan.recoverySupplies().max() > 0) {
            rewards.add(new PotentialReward(
                    "recovery-supplies",
                    plan.recoverySupplies().max(),
                    compactRange(plan.recoverySupplies()),
                    "Recovery Supplies\nFirst-clear milestone\nEvery 10 floors",
                    true
            ));
        }

        return List.copyOf(rewards);
    }

    private static void addCoinPreviews(List<PotentialReward> rewards, IntRange bronzeValue) {
        if (bronzeValue.max() <= 0) return;
        rewards.add(new PotentialReward(
                "coin-bronze", 0, "", "Bronze Coin\nPossible appraisal payout\nFloor completion", false));
        if (bronzeValue.max() >= CoinValue.BRONZE_PER_SILVER) {
            rewards.add(new PotentialReward(
                    "coin-silver", 0, "", "Silver Coin\nPossible appraisal payout\nFloor completion", false));
        }
        if (bronzeValue.max() >= CoinValue.BRONZE_PER_GOLD) {
            rewards.add(new PotentialReward(
                    "coin-gold", 0, "", "Gold Coin\nPossible appraisal payout\nFloor completion", false));
        }
        if (bronzeValue.max() >= CoinValue.BRONZE_PER_STELLAR_GOLD) {
            rewards.add(new PotentialReward(
                    "coin-stellar-gold", 0, "", "Stellar Gold Coin\nPossible appraisal payout\nFloor completion", false));
        }
    }

    private static void addCrystalPreview(
            List<PotentialReward> rewards,
            String typeId,
            String displayName,
            IntRange range
    ) {
        if (range.max() <= 0) return;
        rewards.add(new PotentialReward(
                typeId,
                range.max(),
                compactRange(range),
                displayName + "\nEnemy drop\nExpected: " + compactRange(range),
                false
        ));
    }

    private AppliedRewardEffects combineModifierEffects(Collection<String> modifierIds) {
        double epDelta = 0.0;
        double coinDelta = 0.0;
        double crystalDelta = 0.0;
        double equipment = 0.0;
        double coffer = 0.0;
        double material = 0.0;
        double recording = 0.0;
        double masterwork = 0.0;
        List<String> pools = new ArrayList<>();
        List<String> contributors = new ArrayList<>();

        if (modifierIds != null) {
            for (String id : modifierIds) {
                ModifierRewardEffect effect = modifierEffects.get(id);
                if (effect == null) continue;
                epDelta += effect.epMultiplier() - 1.0;
                coinDelta += effect.coinMultiplier() - 1.0;
                crystalDelta += effect.crystalQuantityMultiplier() - 1.0;
                equipment += effect.equipmentQualityBonus();
                coffer += effect.cofferQualityBonus();
                material += effect.bossMaterialBonus();
                recording += effect.recordingCrystalBonus();
                masterwork += effect.masterworkBonus();
                if (effect.materialPool() != null && !effect.materialPool().isBlank()
                        && !pools.contains(effect.materialPool())) {
                    pools.add(effect.materialPool());
                }
                contributors.add(id);
            }
        }

        return new AppliedRewardEffects(
                clamp(1.0 + epDelta, 0.50, 2.00),
                clamp(1.0 + coinDelta, 0.50, 2.00),
                clamp(1.0 + crystalDelta, 0.50, 2.25),
                clamp(equipment, 0.0, 0.15),
                clamp(coffer, 0.0, 0.25),
                clamp(material, 0.0, 0.30),
                clamp(recording, 0.0, 0.10),
                clamp(masterwork, 0.0, 0.10),
                List.copyOf(pools),
                List.copyOf(contributors)
        );
    }

    private static RewardProfileRegistry loadDefault() {
        try {
            Map<String, Object> profileRoot = readObject(PROFILE_RESOURCE);
            Map<String, Object> effectRoot = readObject(MODIFIER_RESOURCE);
            return parse(profileRoot, effectRoot);
        } catch (RuntimeException | IOException error) {
            return fallback();
        }
    }

    private static Map<String, Object> readObject(String resource) throws IOException {
        try (InputStream stream = RewardProfileRegistry.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing reward resource " + resource);
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return SimpleJson.object(SimpleJson.parse(reader));
            }
        }
    }

    private static RewardProfileRegistry parse(
            Map<String, Object> root,
            Map<String, Object> effectRoot
    ) {
        RewardProfile defaults = parseProfile("defaults", SimpleJson.object(root.get("defaults")), null);
        Map<String, RewardProfile> profiles = new LinkedHashMap<>();
        Map<String, Object> profileObjects = SimpleJson.object(root.get("profiles"));
        for (Map.Entry<String, Object> entry : profileObjects.entrySet()) {
            profiles.put(entry.getKey(), parseProfile(entry.getKey(), SimpleJson.object(entry.getValue()), defaults));
        }
        if (profiles.isEmpty()) profiles.put("standard_floor", defaults);

        List<ProfileRule> rules = new ArrayList<>();
        for (Object value : SimpleJson.array(root.get("rules"))) {
            Map<String, Object> object = SimpleJson.object(value);
            rules.add(new ProfileRule(
                    SimpleJson.string(object, "profile", "standard_floor"),
                    SimpleJson.integer(object, "min_floor", 1),
                    SimpleJson.integer(object, "max_floor", 999),
                    SimpleJson.integer(object, "divisible_by", 0),
                    SimpleJson.bool(object, "boss_only", false),
                    SimpleJson.string(object, "theme_contains", "")
            ));
        }

        Map<Integer, String> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : SimpleJson.object(root.get("floor_overrides")).entrySet()) {
            try {
                overrides.put(Integer.parseInt(entry.getKey()), String.valueOf(entry.getValue()));
            } catch (NumberFormatException ignored) {
            }
        }

        Map<String, ModifierRewardEffect> effects = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : SimpleJson.object(effectRoot.get("modifiers")).entrySet()) {
            Map<String, Object> object = SimpleJson.object(entry.getValue());
            effects.put(entry.getKey(), new ModifierRewardEffect(
                    SimpleJson.decimal(object, "ep_multiplier", 1.0),
                    SimpleJson.decimal(object, "coin_multiplier", 1.0),
                    SimpleJson.decimal(object, "crystal_quantity_multiplier", 1.0),
                    SimpleJson.decimal(object, "equipment_quality_bonus", 0.0),
                    SimpleJson.decimal(object, "coffer_quality_bonus", 0.0),
                    SimpleJson.decimal(object, "boss_material_bonus", 0.0),
                    SimpleJson.decimal(object, "recording_crystal_bonus", 0.0),
                    SimpleJson.decimal(object, "masterwork_bonus", 0.0),
                    SimpleJson.string(object, "material_pool", "")
            ));
        }

        RewardProfile fallback = profiles.getOrDefault("standard_floor", defaults);
        return new RewardProfileRegistry(profiles, rules, overrides, effects, fallback);
    }

    private static RewardProfile parseProfile(
            String id,
            Map<String, Object> object,
            RewardProfile parent
    ) {
        RewardProfile base = parent == null ? builtInStandard() : parent;

        Map<String, Object> ep = SimpleJson.object(object.get("ep"));
        Map<String, Object> capacity = SimpleJson.object(object.get("max_ep_capacity"));
        Map<String, Object> coin = SimpleJson.object(object.get("coin_value"));
        Map<String, Object> crystals = SimpleJson.object(object.get("crystals"));
        Map<String, Object> low = SimpleJson.object(crystals.get("low"));
        Map<String, Object> medium = SimpleJson.object(crystals.get("medium"));
        Map<String, Object> high = SimpleJson.object(crystals.get("high"));
        Map<String, Object> coffer = SimpleJson.object(object.get("guardian_coffer"));
        Map<String, Object> recovery = SimpleJson.object(object.get("recovery_supplies"));
        Map<String, Object> equipment = SimpleJson.object(object.get("equipment_cache"));
        Map<String, Object> material = SimpleJson.object(object.get("boss_material"));
        Map<String, Object> recording = SimpleJson.object(object.get("recording_crystal"));
        Map<String, Object> masterwork = SimpleJson.object(object.get("masterwork_equipment"));

        return new RewardProfile(
                id,
                SimpleJson.integer(object, "reference_floor", base.referenceFloor()),
                SimpleJson.decimal(object, "per_floor_growth", base.perFloorGrowth()),
                range(ep, base.ep()),
                SimpleJson.integer(ep, "first_clear_bonus", base.firstClearEpBonus()),
                range(capacity, base.maxEpCapacity()),
                SimpleJson.bool(capacity, "first_clear_only", base.capacityFirstClearOnly()),
                SimpleJson.bool(capacity, "milestone_only", base.capacityMilestoneOnly()),
                SimpleJson.integer(capacity, "repeat_clear_value", base.repeatCapacityValue()),
                new IntRange(
                        SimpleJson.integer(coin, "min_bronze", base.coinBronze().min()),
                        SimpleJson.integer(coin, "max_bronze", base.coinBronze().max())
                ),
                new CrystalRanges(
                        range(low, base.crystals().low()),
                        range(medium, base.crystals().medium()),
                        range(high, base.crystals().high())
                ),
                new CofferDefinition(
                        SimpleJson.integer(coffer, "count", base.guardianCoffer().count()),
                        SimpleJson.bool(coffer, "guaranteed", base.guardianCoffer().guaranteed()),
                        SimpleJson.string(coffer, "loot_table", base.guardianCoffer().lootTable()),
                        SimpleJson.string(coffer, "display_name", base.guardianCoffer().displayName()),
                        SimpleJson.string(coffer, "quality", base.guardianCoffer().quality())
                ),
                range(recovery, base.recoverySupplies()),
                SimpleJson.string(recovery, "loot_table", base.recoveryLootTable()),
                new EquipmentDefinition(
                        SimpleJson.integer(equipment, "count", base.equipmentCache().count()),
                        SimpleJson.bool(equipment, "guaranteed", base.equipmentCache().guaranteed()),
                        SimpleJson.string(equipment, "quality_profile", base.equipmentCache().qualityProfile()),
                        SimpleJson.string(equipment, "display_name", base.equipmentCache().displayName())
                ),
                new BossMaterialDefinition(
                        SimpleJson.bool(material, "guaranteed", base.bossMaterial().guaranteed()),
                        SimpleJson.decimal(material, "chance", base.bossMaterial().chance()),
                        SimpleJson.string(material, "loot_table", base.bossMaterial().lootTable()),
                        SimpleJson.string(material, "display_name", base.bossMaterial().displayName()),
                        SimpleJson.string(material, "icon_id", base.bossMaterial().iconId())
                ),
                SimpleJson.decimal(recording, "chance", base.recordingCrystalChance()),
                SimpleJson.decimal(recording, "first_clear_bonus", base.recordingCrystalFirstClearBonus()),
                SimpleJson.decimal(masterwork, "chance", base.masterworkChance())
        );
    }

    private static IntRange range(Map<String, Object> object, IntRange fallback) {
        return new IntRange(
                SimpleJson.integer(object, "min", fallback.min()),
                SimpleJson.integer(object, "max", fallback.max())
        );
    }

    private static RewardProfileRegistry fallback() {
        RewardProfile standard = builtInStandard();
        Map<String, RewardProfile> profiles = Map.of(
                "standard_floor", standard,
                "high_threat_ruins", floor25Fallback()
        );
        List<ProfileRule> rules = List.of(
                new ProfileRule("standard_floor", 1, 999, 0, false, "")
        );
        return new RewardProfileRegistry(
                profiles,
                rules,
                Map.of(25, "high_threat_ruins"),
                Map.of(),
                standard
        );
    }

    private static RewardProfile builtInStandard() {
        return new RewardProfile(
                "standard_floor",
                15,
                0.035,
                new IntRange(7_500, 13_500),
                2_000,
                new IntRange(0, 1_500),
                true,
                false,
                0,
                new IntRange(2_500, 6_000),
                new CrystalRanges(new IntRange(2, 6), new IntRange(0, 3), new IntRange(0, 1)),
                new CofferDefinition(1, true, "guardian_coffer_standard", "Guardian Coffer", "standard"),
                new IntRange(1, 3),
                "recovery_supplies_standard",
                new EquipmentDefinition(1, false, "uncommon", "Equipment Cache"),
                new BossMaterialDefinition(false, 0.10, "guardian_material_standard",
                        "Guardian Material", "guardian-material"),
                0.01,
                0.0,
                0.0025
        );
    }

    private static RewardProfile floor25Fallback() {
        return new RewardProfile(
                "high_threat_ruins",
                25,
                0.0,
                new IntRange(18_000, 32_000),
                5_000,
                new IntRange(0, 5_000),
                true,
                false,
                0,
                new IntRange(8_000, 16_000),
                new CrystalRanges(new IntRange(0, 0), new IntRange(4, 9), new IntRange(0, 2)),
                new CofferDefinition(1, true, "guardian_coffer_floor_25", "Guardian Coffer", "rare"),
                new IntRange(2, 4),
                "recovery_supplies_floor_25",
                new EquipmentDefinition(1, true, "rare", "Equipment Cache"),
                new BossMaterialDefinition(true, 1.0, "guardian_material_floor_25",
                        "Dimensional Shard", "guardian-material"),
                0.03,
                0.0,
                0.01
        );
    }

    private static double depthMultiplier(RewardProfile profile, int floor) {
        int delta = Math.max(0, floor - profile.referenceFloor());
        return Math.pow(1.0 + Math.max(0.0, profile.perFloorGrowth()), delta);
    }

    private static IntRange scale(IntRange range, double multiplier) {
        if (range == null) return new IntRange(0, 0);
        double safe = Math.max(0.0, multiplier);
        return new IntRange(
                (int) Math.floor(range.min() * safe + 1.0e-9),
                (int) Math.ceil(range.max() * safe - 1.0e-9)
        );
    }

    private static IntRange scaleQuantity(IntRange range, double multiplier) {
        if (range == null) return new IntRange(0, 0);
        double safe = Math.max(0.0, multiplier);
        int min = (int) Math.round(range.min() * safe);
        int max = (int) Math.round(range.max() * safe);
        return new IntRange(min, Math.max(min, max));
    }

    private static int scaleValue(int value, double multiplier) {
        return (int) Math.round(Math.max(0, value) * Math.max(0.0, multiplier));
    }

    private static double clampChance(double value, double max) {
        return clamp(value, 0.0, max);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String chanceLine(String label, double chance) {
        if (chance <= 0.0) return "";
        return "\n" + label + ": " + percent(chance);
    }

    private static String compactRange(IntRange range) {
        if (range.min() == range.max()) return compactNumber(range.max());
        return compactNumber(range.min()) + "-" + compactNumber(range.max());
    }

    private static String exactRange(IntRange range) {
        if (range.min() == range.max()) return formatNumber(range.max());
        return formatNumber(range.min()) + "-" + formatNumber(range.max());
    }

    private static String compactCoinRange(IntRange range) {
        CoinValue min = CoinValue.ofBronze(range.min());
        CoinValue max = CoinValue.ofBronze(range.max());
        if (range.min() == range.max()) return min.compact();
        return min.compact() + "-" + max.compact();
    }

    private static String crystalCompact(CrystalRanges crystals) {
        if (crystals.medium().max() > 0) {
            String medium = compactRange(crystals.medium()) + " M";
            if (crystals.high().max() > 0) return medium + " / " + compactRange(crystals.high()) + " H";
            return medium;
        }
        if (crystals.low().max() > 0) return compactRange(crystals.low()) + " L";
        return compactRange(crystals.high()) + " H";
    }

    private static String compactNumber(int value) {
        if (value >= 1_000_000) return stripZero(value / 1_000_000.0) + "M";
        if (value >= 1_000) return stripZero(value / 1_000.0) + "k";
        return formatNumber(value);
    }

    private static String stripZero(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) return String.format(Locale.ROOT, "%.0f", value);
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String percent(double chance) {
        double percent = Math.max(0.0, chance) * 100.0;
        if (Math.abs(percent - Math.rint(percent)) < 0.005) {
            return String.format(Locale.ROOT, "%.0f%%", percent);
        }
        return String.format(Locale.ROOT, "%.1f%%", percent);
    }

    private static String title(String value) {
        if (value == null || value.isBlank()) return "Standard";
        String[] words = value.replace('_', ' ').split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
