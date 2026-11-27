package dev.luke.tensuradungeon.client;

import dev.luke.tensuradungeon.reward.DungeonRewardRuntime;
import dev.luke.tensuradungeon.reward.RewardPackage;
import dev.luke.tensuradungeon.reward.RewardProfileRegistry;
import dev.luke.tensuradungeon.reward.RewardProfileRegistry.PotentialReward;
import com.mineclawd.tensuradungeon.network.DungeonUiSnapshotPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class DungeonData {
    public static final String MOD_ID = "tensura_dungeon_ui";
    public static final int CURRENT_FLOOR = 25;
    public static final int HIGHEST_UNLOCKED = 25;
    private static final Set<String> ADDON_ICON_KEYS = Set.of(
            "glass-cannon", "barbed-hide", "rallying-cry", "dread-aura",
            "shatterproof", "kin", "boss-affinity", "rising-heat"
    );

    public enum Kind { PLAYER, ENEMY, ENVIRONMENT, REWARD, BOSS }

    public enum Tone {
        BLUE(0xFF35A9FF, 0xFF071B31),
        VIOLET(0xFFAD50FF, 0xFF210B38),
        GREEN(0xFF1EE4A0, 0xFF07291F),
        RED(0xFFFF4051, 0xFF310B13),
        ORANGE(0xFFFF8B20, 0xFF321707),
        GOLD(0xFFFFB730, 0xFF302208);

        public final int accent;
        public final int dark;

        Tone(int accent, int dark) {
            this.accent = accent;
            this.dark = dark;
        }
    }

    public record Modifier(
            String id,
            String name,
            Kind kind,
            Tone tone,
            String tier,
            int minFloor,
            String shortDescription,
            String description,
            List<String> effects,
            List<String> counters,
            String status,
            String iconKey
    ) {
        public Modifier(
                String id,
                String name,
                Kind kind,
                Tone tone,
                String tier,
                int minFloor,
                String shortDescription,
                String description,
                List<String> effects,
                List<String> counters
        ) {
            this(id, name, kind, tone, tier, minFloor, shortDescription, description,
                    effects, counters, "LEGACY", id);
        }

        public ResourceLocation texture() {
            return ResourceLocation.fromNamespaceAndPath(
                    MOD_ID, "textures/gui/modifiers/" + iconKey + ".png");
        }
    }

    public record RewardType(String id, String name, Tone tone) {
        public ResourceLocation texture() {
            return ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/rewards/" + id + ".png");
        }
    }

    public record Reward(
            RewardType type,
            int amount,
            String value,
            String details,
            boolean firstClearOnly
    ) {
        public Reward(RewardType type, int amount) {
            this(type, amount, String.format(Locale.ROOT, "%,d", amount),
                    type.name() + "\nAmount: " + String.format(Locale.ROOT, "%,d", amount), false);
        }

        public Reward {
            value = value == null ? "" : value;
            details = details == null ? "" : details;
        }

        public String tooltip() {
            StringBuilder result = new StringBuilder(type.name());
            if (!value.isBlank()) result.append("\n").append(value);
            if (firstClearOnly) result.append("\nFIRST CLEAR");
            if (!details.isBlank()) result.append("\n").append(details);
            return result.toString();
        }
    }

    public record Theme(String name, String layout, String description) {}

    public record Floor(
            int number,
            Theme theme,
            int recommendedEp,
            boolean unlocked,
            boolean current,
            boolean boss,
            List<Modifier> playerModifiers,
            List<Modifier> enemyModifiers,
            List<Modifier> previewModifiers,
            List<Reward> rewards,
            int seed,
            String sessionId
    ) {
        public int totalModifierCount() {
            return playerModifiers.size() + enemyModifiers.size();
        }

        public String searchableText() {
            StringBuilder text = new StringBuilder();
            text.append(number).append(' ').append(theme.name()).append(' ').append(theme.layout()).append(' ');
            for (Modifier modifier : playerModifiers) text.append(modifier.name()).append(' ');
            for (Modifier modifier : enemyModifiers) text.append(modifier.name()).append(' ');
            for (Reward reward : rewards) text.append(reward.type().name()).append(' ');
            return text.toString().toLowerCase(Locale.ROOT);
        }
    }

    public static final List<Modifier> MODIFIERS = List.of(
            modifier("magicule-surge", "Magicule Surge", Kind.PLAYER, Tone.BLUE, "COMMON", 1,
                    "Magicule gain +25%", "Increases magicule generation from all sources, allowing more frequent skill use.",
                    List.of("Magicule gain +25%", "Skill resource recovery +10%"),
                    List.of("High-cost skill builds", "Sustained magic damage")),
            modifier("cooldown-reduction", "Cooldown Reduction", Kind.PLAYER, Tone.BLUE, "COMMON", 1,
                    "Skill cooldown -15%", "Reduces the recovery time of equipped skills during the entire floor.",
                    List.of("Skill cooldown -15%", "Ultimate cooldown -8%"),
                    List.of("Burst rotations", "Mobility skills")),
            modifier("healing-essence", "Healing Essence", Kind.PLAYER, Tone.GREEN, "COMMON", 1,
                    "Healing received +20%", "Improves all direct healing and regeneration received during the run.",
                    List.of("Healing received +20%", "Potion potency +15%"),
                    List.of("Attrition floors", "Regenerative enemy packages")),
            modifier("damage-boost", "Damage Boost", Kind.PLAYER, Tone.ORANGE, "UNCOMMON", 8,
                    "Player damage +18%", "Increases outgoing damage, with a smaller bonus against elites and bosses.",
                    List.of("Damage +18%", "Boss damage +8%"),
                    List.of("Armored enemies", "Boss Rush layouts")),
            modifier("barrier", "Barrier", Kind.PLAYER, Tone.BLUE, "UNCOMMON", 10,
                    "Start each room shielded", "Grants a protective barrier at the start of every combat room.",
                    List.of("Barrier equal to 15% max HP", "Refreshes each room"),
                    List.of("Burst enemy packages", "Ambush rooms")),
            modifier("resourceful", "Resourceful", Kind.REWARD, Tone.VIOLET, "RARE", 14,
                    "Better reward quantities", "Increases currency, crystal, and fragment quantities earned from the floor.",
                    List.of("Currency +20%", "Crystal quantity +12%"),
                    List.of("Farming floors", "Treasure Hunt layouts")),

            modifier("swarm", "Swarm", Kind.ENEMY, Tone.RED, "COMMON", 1,
                    "More weak enemies", "Greatly increases the number of weaker enemies while reducing their individual durability.",
                    List.of("Enemy count +100% to +200%", "Enemy HP -30%", "Enemy damage -20%"),
                    List.of("Area damage skills", "Crowd control", "High mobility")),
            modifier("armored", "Armored", Kind.ENEMY, Tone.BLUE, "COMMON", 3,
                    "Enemies have increased DEF", "Enemies gain armor, toughness, and knockback resistance.",
                    List.of("Defense +35%", "Toughness +20%", "Knockback resistance +40%"),
                    List.of("Armor penetration", "Magic damage", "Defense reduction")),
            modifier("berserker", "Berserker", Kind.ENEMY, Tone.RED, "UNCOMMON", 6,
                    "More damage below 50% HP", "Enemies become much more dangerous when wounded.",
                    List.of("Damage +30% below 50% HP", "Attack speed +12% below 25% HP"),
                    List.of("Execute effects", "Burst damage", "Crowd control")),
            modifier("regenerative", "Regenerative", Kind.ENEMY, Tone.ORANGE, "UNCOMMON", 6,
                    "Enemies slowly regenerate HP", "Enemies regenerate over time; elites may project a healing aura.",
                    List.of("Regenerate 2% HP every 4s", "Elite healing aura"),
                    List.of("Burst damage", "Healing reduction", "Target prioritization")),
            modifier("summoner", "Summoner", Kind.ENEMY, Tone.VIOLET, "RARE", 10,
                    "Enemies summon minions", "Selected enemies periodically summon lesser creatures to assist them.",
                    List.of("Summon interval 12s", "Strict room summon cap", "Summons inherit 50% modifiers"),
                    List.of("Interrupts", "Focus summoners first", "Area damage")),
            modifier("molten", "Molten", Kind.ENEMY, Tone.RED, "RARE", 12,
                    "Enemies deal fire damage", "Enemies resist fire and may ignite attackers or leave burning ground.",
                    List.of("Fire immunity", "Ignite on hit", "Occasional burning zones"),
                    List.of("Fire resistance", "Ranged damage", "Cleanse effects")),
            modifier("electrified", "Electrified", Kind.ENEMY, Tone.BLUE, "UNCOMMON", 13,
                    "Attacks may chain lightning", "Enemy attacks may arc lightning between nearby targets.",
                    List.of("15% chain-lightning chance", "Lightning resistance +50%"),
                    List.of("Spacing", "Lightning resistance", "Fast target removal")),
            modifier("poisonous", "Poisonous", Kind.ENEMY, Tone.GREEN, "COMMON", 9,
                    "Enemies inflict poison", "Enemy attacks apply stacking poison damage over time.",
                    List.of("Poison on hit", "Maximum 5 stacks"),
                    List.of("Cleanse effects", "Status resistance", "Hit-and-run tactics")),
            modifier("explosive", "Explosive", Kind.ENEMY, Tone.ORANGE, "RARE", 18,
                    "Enemies explode on death", "Some enemies detonate on death, dealing area damage and disrupting positioning.",
                    List.of("Death explosion", "Elite explosion radius +50%"),
                    List.of("Ranged finishing blows", "Mobility skills", "Spacing")),
            modifier("shadowed", "Shadowed", Kind.ENVIRONMENT, Tone.VIOLET, "EPIC", 22,
                    "Reduced visibility", "Abyssal fog limits vision while flank spawns become more common.",
                    List.of("Visibility reduced", "Flank weight +45%"),
                    List.of("Detection skills", "Close formation", "Audio cues")),
            modifier("swift", "Swift", Kind.ENEMY, Tone.GREEN, "UNCOMMON", 16,
                    "Enemies move and attack faster", "Enemies gain movement and attack speed, increasing pressure in open rooms.",
                    List.of("Move speed +18%", "Attack speed +12%"),
                    List.of("Slows", "Roots", "Defensive spacing")),
            modifier("elite-vanguard", "Elite Vanguard", Kind.BOSS, Tone.RED, "EPIC", 25,
                    "Every room includes an elite", "Every combat room is reinforced by at least one elite enemy when anchors allow.",
                    List.of("Guaranteed elite presence", "Elite reward multiplier +10%"),
                    List.of("Single-target burst", "Elite control effects", "Resource conservation")),
            modifier("boss-touched", "Boss-Touched", Kind.BOSS, Tone.GOLD, "LEGENDARY", 30,
                    "Higher boss override chance", "Normal rooms have a chance to mutate into miniboss or boss encounters.",
                    List.of("Miniboss chance +15%", "Boss override chance +6%", "Rewards +22%"),
                    List.of("Flexible builds", "Reserve ultimate skills", "Defensive consumables")),
            modifier("crystal-rich", "Crystal-Rich", Kind.REWARD, Tone.VIOLET, "RARE", 12,
                    "More crystals, more elites", "The floor produces more crystals but attracts stronger enemies.",
                    List.of("Crystal rewards +35%", "Elite chance +10%"),
                    List.of("Dedicated farming builds", "Elite burst damage"))
    );

    public static final Map<String, Modifier> MODIFIER_BY_ID;

    public static final List<RewardType> REWARD_TYPES = List.of(
            new RewardType("existence-points", "Existence Growth", Tone.BLUE),
            new RewardType("max-ep-capacity", "Maximum EP Capacity", Tone.VIOLET),
            new RewardType("coin-bronze", "Bronze Coin", Tone.GOLD),
            new RewardType("coin-silver", "Silver Coin", Tone.BLUE),
            new RewardType("coin-gold", "Gold Coin", Tone.GOLD),
            new RewardType("coin-stellar-gold", "Stellar Gold Coin", Tone.VIOLET),
            new RewardType("magicule-orb", "Magicule Orb", Tone.BLUE),
            new RewardType("magic-crystal-low", "Low-Quality Magic Crystal", Tone.BLUE),
            new RewardType("magic-crystal-medium", "Medium-Quality Magic Crystal", Tone.VIOLET),
            new RewardType("magic-crystal-high", "High-Quality Magic Crystal", Tone.GOLD),
            new RewardType("monster-crystal-tier-1", "Tier I Monster Core", Tone.BLUE),
            new RewardType("monster-crystal-tier-2", "Tier II Monster Core", Tone.BLUE),
            new RewardType("monster-crystal-tier-3", "Tier III Monster Core", Tone.BLUE),
            new RewardType("monster-crystal-tier-4", "Tier IV Monster Core", Tone.VIOLET),
            new RewardType("monster-crystal-tier-5", "Tier V Monster Core", Tone.VIOLET),
            new RewardType("monster-crystal-tier-6", "Tier VI Monster Core", Tone.VIOLET),
            new RewardType("monster-crystal-tier-7", "Tier VII Apex Core", Tone.GOLD),
            new RewardType("guardian-coffer", "Guardian Coffer", Tone.ORANGE),
            new RewardType("recovery-supplies", "Recovery Supplies", Tone.GREEN),
            new RewardType("equipment-cache", "Equipment Cache", Tone.VIOLET),
            new RewardType("reward-cache", "Reward Cache", Tone.ORANGE),
            new RewardType("elemental-core", "Elemental Core", Tone.ORANGE),
            new RewardType("recording-crystal", "Recording Crystal", Tone.VIOLET),
            new RewardType("skill-ticket", "Skill Ticket", Tone.BLUE)
    );

    public static final Map<String, RewardType> REWARD_TYPE_BY_ID;

    public static final List<Theme> THEMES = List.of(
            new Theme("Verdant Threshold", "Forest Variant A", "A living maze where roots and dimensional energy reshape every path."),
            new Theme("Ember Bastion", "Citadel Variant C", "A scorched fortress sustained by unstable elemental cores."),
            new Theme("Silent Catacombs", "Catacombs Variant B", "Buried halls filled with sealed chambers and forgotten guardians."),
            new Theme("Astral Causeway", "Skybridge Variant A", "Floating bridges cross a void alive with hostile magicule currents."),
            new Theme("Sacred Ruins", "Ruins Variant B", "An ancient ruin twisted by dimensional powers.\nFace relentless foes and prove your strength."),
            new Theme("Abyssal Foundry", "Foundry Variant D", "Ancient machines awaken beneath rivers of condensed magicule."),
            new Theme("Crystal Labyrinth", "Labyrinth Variant C", "Reflections and false corridors conceal dangerous crystal growths."),
            new Theme("Demon Lord's Approach", "Warpath Variant A", "A relentless route defended by elite vanguards and hostile auras."),
            new Theme("Celestial Archive", "Archive Variant B", "Forbidden knowledge manifests as living spells and summoned sentries."),
            new Theme("Throne Beyond Time", "Boss Rush Variant Omega", "A final ascent through fractured eras and ruler-class enemies.")
    );

    public static final List<Floor> FLOORS;

    static {
        Map<String, Modifier> modifierMap = new LinkedHashMap<>();
        for (Modifier modifier : MODIFIERS) modifierMap.put(modifier.id(), modifier);
        MODIFIER_BY_ID = Collections.unmodifiableMap(modifierMap);

        Map<String, RewardType> rewardMap = new LinkedHashMap<>();
        for (RewardType rewardType : REWARD_TYPES) rewardMap.put(rewardType.id(), rewardType);
        REWARD_TYPE_BY_ID = Collections.unmodifiableMap(rewardMap);

        List<Floor> floors = new ArrayList<>(99);
        for (int i = 1; i <= 99; i++) floors.add(generateFloor(i));
        FLOORS = Collections.unmodifiableList(floors);
    }

    private DungeonData() {}

    public static Floor floor(int number) {
        Floor base = FLOORS.get(Math.max(1, Math.min(99, number)) - 1);
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        if (snapshot == null || snapshot.preparedModifierPlan().floor() != base.number()) return base;
        Map<String, Modifier> catalog = modifierMap(snapshot);
        List<Modifier> hostile = snapshot.preparedModifierPlan().hostileModifierIds().stream()
                .map(catalog::get).filter(java.util.Objects::nonNull).toList();
        Modifier boon = catalog.get(snapshot.preparedModifierPlan().boonModifierId());
        List<Modifier> boons = boon == null ? List.of() : List.of(boon);
        List<Modifier> preview = new ArrayList<>(boons);
        preview.addAll(hostile);
        return new Floor(
                base.number(), base.theme(), base.recommendedEp(), base.unlocked(), base.current(),
                base.boss(), boons, hostile, List.copyOf(preview), base.rewards(),
                Long.hashCode(snapshot.preparedModifierPlan().seed()), base.sessionId()
        );
    }

    public static List<Modifier> modifiers() {
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        if (snapshot == null || snapshot.modifierCatalog().isEmpty()) return MODIFIERS;
        return List.copyOf(modifierMap(snapshot).values());
    }

    public static Modifier modifierById(String id) {
        if (id == null || id.isBlank()) return null;
        DungeonUiSnapshotPayload snapshot = DungeonRuntimeBridge.snapshot();
        if (snapshot != null && !snapshot.modifierCatalog().isEmpty()) {
            Modifier modifier = modifierMap(snapshot).get(id);
            if (modifier != null) return modifier;
        }
        return MODIFIER_BY_ID.get(id);
    }

    private static Map<String, Modifier> modifierMap(DungeonUiSnapshotPayload snapshot) {
        Map<String, Modifier> result = new LinkedHashMap<>();
        for (DungeonUiSnapshotPayload.ModifierCatalogEntry entry : snapshot.modifierCatalog()) {
            Kind kind = switch (entry.alignment()) {
                case "BOON" -> Kind.PLAYER;
                case "REWARD" -> Kind.REWARD;
                case "ENVIRONMENT" -> Kind.ENVIRONMENT;
                case "BOSS" -> Kind.BOSS;
                default -> Kind.ENEMY;
            };
            Tone tone = toneFor(entry);
            String shortDescription = entry.description().length() <= 48
                    ? entry.description()
                    : entry.description().substring(0, 45) + "...";
            result.put(entry.id(), new Modifier(
                    entry.id(), entry.displayName(), kind, tone, entry.classification(),
                    entry.minFloor(), shortDescription, entry.description(),
                    List.of(entry.description()), entry.counters(), entry.status(),
                    existingIconKey(entry)
            ));
        }
        return result;
    }

    private static Tone toneFor(DungeonUiSnapshotPayload.ModifierCatalogEntry entry) {
        if (entry.classification().equals("PRIMARY")) return Tone.VIOLET;
        if (entry.wardCategory().contains("fire") || entry.wardCategory().contains("heat")) return Tone.RED;
        if (entry.wardCategory().contains("poison") || entry.wardCategory().contains("earth")) return Tone.GREEN;
        if (!entry.wardCategory().isBlank()) return Tone.BLUE;
        return switch (entry.alignment()) {
            case "BOON" -> Tone.BLUE;
            case "REWARD" -> Tone.GOLD;
            case "ENVIRONMENT" -> Tone.VIOLET;
            case "BOSS" -> Tone.RED;
            default -> Tone.ORANGE;
        };
    }

    private static String existingIconKey(DungeonUiSnapshotPayload.ModifierCatalogEntry entry) {
        if (entry.iconKey().startsWith("ward-")) return entry.iconKey();
        if (MODIFIER_BY_ID.containsKey(entry.iconKey())
                || ADDON_ICON_KEYS.contains(entry.iconKey())) return entry.iconKey();
        String category = entry.wardCategory();
        if (category.contains("fire") || category.contains("heat") || category.contains("thermal")) return "molten";
        if (category.contains("electric")) return "electrified";
        if (category.contains("poison") || category.contains("corrosion")) return "poisonous";
        if (category.contains("physical") || category.contains("pierce")) return "armored";
        if (category.contains("dark") || category.contains("spatial")) return "shadowed";
        if (!category.isBlank()) return "barrier";
        return entry.id().equals("riftbound") ? "summoner" : "barrier";
    }

    public static List<Reward> rewardsForFloor(Floor floor) {
        return rewardsForFloor(floor, true);
    }

    public static List<Reward> rewardsForFloor(Floor floor, boolean firstClearAvailable) {
        if (floor == null) return List.of();
        List<String> modifierIds = new ArrayList<>();
        for (Modifier modifier : floor.playerModifiers()) modifierIds.add(modifier.id());
        for (Modifier modifier : floor.enemyModifiers()) modifierIds.add(modifier.id());
        RewardProfileRegistry.RewardPlan plan = RewardProfileRegistry.instance().planForFloor(
                floor.number(), floor.boss(), floor.theme().name(), modifierIds,
                firstClearAvailable);
        List<Reward> rewards = new ArrayList<>(
                rewardsFromPotential(RewardProfileRegistry.instance().potentialRewards(plan)));
        rewards.add(0, crystalVisualPreview(floor.number()));
        return List.copyOf(rewards);
    }

    private static Reward crystalVisualPreview(int floor) {
        CrystalTierVisual tier = CrystalTierVisual.forFloor(floor);
        RewardType type = REWARD_TYPE_BY_ID.get(tier.rewardId());
        return new Reward(
                type,
                0,
                "PREVIEW",
                tier.displayName() + "\n"
                        + tier.plannedBand() + "\n"
                        + "Visual preview only; tiered drops activate in the later reward-system patch.",
                false
        );
    }

    public static List<Reward> rewardsForPackage(RewardPackage rewardPackage) {
        return rewardsFromPotential(DungeonRewardRuntime.packagePreview(rewardPackage));
    }

    private static List<Reward> rewardsFromPotential(List<PotentialReward> potentialRewards) {
        List<Reward> rewards = new ArrayList<>();
        for (PotentialReward potential : potentialRewards) {
            RewardType type = REWARD_TYPE_BY_ID.getOrDefault(
                    potential.typeId(), REWARD_TYPE_BY_ID.get("reward-cache"));
            rewards.add(new Reward(
                    type,
                    potential.amount(),
                    potential.value(),
                    potential.details(),
                    potential.firstClearOnly()
            ));
        }
        return List.copyOf(rewards);
    }

    public static List<Floor> findFloors(String query, Filter filter) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Floor> result = new ArrayList<>();
        for (Floor floor : FLOORS) {
            if (!normalized.isEmpty() && !floor.searchableText().contains(normalized)) continue;
            if (filter == Filter.UNLOCKED && !floor.unlocked()) continue;
            if (filter == Filter.BOSS && !floor.boss()) continue;
            result.add(floor);
        }
        return result;
    }

    public enum Filter { ALL, UNLOCKED, BOSS }

    private static Modifier modifier(String id, String name, Kind kind, Tone tone, String tier, int minFloor,
                                     String shortDescription, String description, List<String> effects, List<String> counters) {
        return new Modifier(id, name, kind, tone, tier, minFloor, shortDescription, description, effects, counters);
    }

    private static int epForFloor(int floor) {
        return switch (floor) {
            case 21 -> 450_000;
            case 22 -> 480_000;
            case 23 -> 520_000;
            case 24 -> 600_000;
            case 25 -> 850_000;
            case 26 -> 1_000_000;
            default -> {
                if (floor < 21) {
                    yield (int) (Math.round((16_000.0 * Math.pow(floor, 1.36)) / 5_000.0) * 5_000L);
                }
                yield (int) Math.min(Integer.MAX_VALUE,
                        Math.round((1_000_000.0 * Math.pow(1.075, floor - 26)) / 5_000.0) * 5_000L);
            }
        };
    }

    private static Floor generateFloor(int floor) {
        Random random = new Random(942_811L + floor * 104_729L);
        Theme theme = floor == 25 ? THEMES.get(4) : THEMES.get(Math.min(THEMES.size() - 1, (floor - 1) / 10));
        List<Modifier> playerModifiers = List.of();
        List<Modifier> enemyModifiers = List.of();

        List<String> rewardModifierIds = new ArrayList<>();
        for (Modifier modifier : playerModifiers) rewardModifierIds.add(modifier.id());
        for (Modifier modifier : enemyModifiers) rewardModifierIds.add(modifier.id());
        RewardProfileRegistry.RewardPlan rewardPlan = RewardProfileRegistry.instance().planForFloor(
                floor,
                floor % 5 == 0,
                theme.name(),
                rewardModifierIds,
                true
        );
        List<Reward> rewards = rewardsFromPotential(
                RewardProfileRegistry.instance().potentialRewards(rewardPlan));

        List<Modifier> preview = new ArrayList<>();
        preview.addAll(playerModifiers);
        preview.addAll(enemyModifiers);
        return new Floor(
                floor,
                theme,
                epForFloor(floor),
                floor <= HIGHEST_UNLOCKED,
                floor == CURRENT_FLOOR,
                floor % 5 == 0,
                List.copyOf(playerModifiers),
                List.copyOf(enemyModifiers),
                List.copyOf(preview),
                List.copyOf(rewards),
                random.nextInt(900_000) + 100_000,
                hex(random) + "-" + hex(random) + "-" + hex(random)
        );
    }

    private static String hex(Random random) {
        return String.format(Locale.ROOT, "%04X", random.nextInt(65_536));
    }

    private static List<Modifier> ids(String... ids) {
        List<Modifier> result = new ArrayList<>();
        for (String id : ids) result.add(MODIFIER_BY_ID.get(id));
        return result;
    }

    private static <T> List<T> pickUnique(List<T> pool, int count, Random random) {
        List<T> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, random);
        return new ArrayList<>(copy.subList(0, Math.min(count, copy.size())));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
