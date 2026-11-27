package dev.luke.tensuradungeon.reward;

import dev.luke.tensuradungeon.reward.RewardPackage.CrystalAmounts;
import dev.luke.tensuradungeon.reward.RewardProfileRegistry.BossMaterialDefinition;
import dev.luke.tensuradungeon.reward.RewardProfileRegistry.CofferDefinition;
import dev.luke.tensuradungeon.reward.RewardProfileRegistry.EquipmentDefinition;
import dev.luke.tensuradungeon.reward.RewardProfileRegistry.IntRange;
import dev.luke.tensuradungeon.reward.RewardProfileRegistry.PotentialReward;
import dev.luke.tensuradungeon.reward.RewardProfileRegistry.RewardPlan;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

public final class DungeonRewardRuntime {
    private static final RewardProfileRegistry REGISTRY = RewardProfileRegistry.instance();
    private static RewardStateStore store = createDefaultStore();

    private DungeonRewardRuntime() {}

    public static RewardProfileRegistry registry() {
        return REGISTRY;
    }

    public static synchronized RewardStateStore stateStore() {
        return store;
    }

    public static synchronized void setStateStore(RewardStateStore replacement) {
        if (replacement == null) throw new IllegalArgumentException("Reward state store cannot be null");
        store = replacement;
    }

    public static boolean isFirstClearAvailable(int floor) {
        return !stateStore().hasCompletedFloor(floor);
    }

    public static CoinValue wallet() {
        return stateStore().wallet();
    }

    public static long totalEpEarned() {
        return stateStore().totalEpEarned();
    }

    public static long maximumEpCapacityGrowth() {
        return stateStore().maximumEpCapacityGrowth();
    }

    public static List<RewardPackage> mailboxPackages() {
        return stateStore().mailboxPackages();
    }

    public static RewardPlan previewPlan(
            int floor,
            boolean boss,
            String themeName,
            Collection<String> modifierIds
    ) {
        return REGISTRY.planForFloor(
                floor,
                boss,
                themeName,
                modifierIds,
                isFirstClearAvailable(floor)
        );
    }

    public static List<PotentialReward> previewRewards(
            int floor,
            boolean boss,
            String themeName,
            Collection<String> modifierIds
    ) {
        return REGISTRY.potentialRewards(previewPlan(floor, boss, themeName, modifierIds));
    }

    public static synchronized RewardPackage registerFloorCompletion(
            int floor,
            boolean boss,
            String themeName,
            Collection<String> modifierIds,
            String sessionId,
            String source
    ) {
        RewardStateStore currentStore = stateStore();
        String safeSource = source == null || source.isBlank() ? "floor_clear" : source;
        RewardPackage existing = currentStore.packageForCompletion(floor, sessionId, safeSource);
        if (existing != null) {
            String existingMilestone = existing.floor() % 10 == 0 && existing.firstClear()
                    ? "floor_" + existing.floor() : "";
            currentStore.persistCompletionPackage(
                    existing, existing.firstClear(), existingMilestone);
            return existing;
        }

        boolean firstClear = !currentStore.hasCompletedFloor(floor);
        RewardPlan plan = REGISTRY.planForFloor(floor, boss, themeName, modifierIds, firstClear);
        Random random = deterministicRandom(currentStore.playerId(), floor, sessionId, source);
        RewardPackage rewardPackage = rollPackage(
                plan,
                currentStore.playerId(),
                sessionId,
                safeSource,
                firstClear,
                random
        );
        String milestone = floor % 10 == 0 && firstClear ? "floor_" + floor : "";
        currentStore.persistCompletionPackage(rewardPackage, firstClear, milestone);
        return rewardPackage;
    }

    public static boolean claimPackage(
            String packageId,
            Predicate<RewardPackage> grantHandler
    ) {
        return stateStore().claimPackage(packageId, grantHandler);
    }

    public static List<PotentialReward> packagePreview(RewardPackage rewardPackage) {
        if (rewardPackage == null) return List.of();
        List<PotentialReward> result = new ArrayList<>(10);
        result.add(new PotentialReward(
                "existence-points",
                rewardPackage.ep(),
                rewardPackage.ep() > 0 ? "+" + compactNumber(rewardPackage.ep()) : "SYNC PENDING",
                "Existence Growth observed during this run: " + formatNumber(rewardPackage.ep())
                        + "\nAlready applied by combat; this package does not grant it again.",
                false
        ));
        if (rewardPackage.maxEpCapacity() > 0) {
            result.add(new PotentialReward(
                    "max-ep-capacity",
                    rewardPackage.maxEpCapacity(),
                    rewardPackage.maxEpCapacity() > 0 ? "+" + compactNumber(rewardPackage.maxEpCapacity()) : "+0",
                    "Permanent Existence Capacity Growth.\nFirst-clear award: "
                            + formatNumber(rewardPackage.maxEpCapacity()),
                    true
            ));
        }
        if (rewardPackage.coinBronze() > 0) {
            addCoinPackagePreviews(result, rewardPackage.coinBronze());
        }
        CrystalAmounts crystals = rewardPackage.crystals();
        addCrystalPackagePreview(result, "magic-crystal-low", "Low-Quality Magic Crystal", crystals.low());
        addCrystalPackagePreview(result, "magic-crystal-medium", "Medium-Quality Magic Crystal", crystals.medium());
        addCrystalPackagePreview(result, "magic-crystal-high", "High-Quality Magic Crystal", crystals.high());
        for (String cache : rewardPackage.equipmentCaches()) {
            result.add(new PotentialReward("equipment-cache", 1, "1", cache, false));
        }
        if (!rewardPackage.bossMaterials().isEmpty()) {
            result.add(new PotentialReward(
                    "magicule-orb",
                    rewardPackage.bossMaterials().size(),
                    "CAPTURED",
                    "Guardian materials captured by the Magicule Orb:\n"
                            + String.join("\n", rewardPackage.bossMaterials()),
                    false));
        }
        if (!rewardPackage.items().isEmpty()) {
            result.add(new PotentialReward(
                    "recovery-supplies",
                    rewardPackage.items().size(),
                    String.valueOf(rewardPackage.items().size()),
                    "Recovered supplies:\n" + String.join("\n", rewardPackage.items()),
                    false
            ));
        }
        return List.copyOf(result);
    }

    private static void addCrystalPackagePreview(
            List<PotentialReward> result,
            String typeId,
            String displayName,
            int amount
    ) {
        if (amount <= 0) return;
        result.add(new PotentialReward(
                typeId, amount, String.valueOf(amount),
                displayName + " captured from defeated enemies", false));
    }

    private static void addCoinPackagePreviews(List<PotentialReward> result, long bronzeValue) {
        CoinValue.Breakdown coins = CoinValue.normalizeCoinValue(bronzeValue);
        addCoinPackagePreview(result, "coin-stellar-gold", "Stellar Gold Coin", coins.stellarGold());
        addCoinPackagePreview(result, "coin-gold", "Gold Coin", coins.gold());
        addCoinPackagePreview(result, "coin-silver", "Silver Coin", coins.silver());
        addCoinPackagePreview(result, "coin-bronze", "Bronze Coin", coins.bronze());
    }

    private static void addCoinPackagePreview(
            List<PotentialReward> result,
            String typeId,
            String displayName,
            long amount
    ) {
        if (amount <= 0) return;
        result.add(new PotentialReward(
                typeId,
                (int) Math.min(Integer.MAX_VALUE, amount),
                String.valueOf(amount),
                displayName + "\nAppraised haul payout\nFloor completion",
                false));
    }

    static RewardPackage rollPackage(
            RewardPlan plan,
            String playerId,
            String sessionId,
            String source,
            boolean firstClear,
            Random random
    ) {
        int ep = 0;

        int capacity = 0;
        boolean milestoneFirstClear = firstClear && plan.floor() % 10 == 0;
        if (milestoneFirstClear) {
            capacity = roll(plan.maxEpCapacity(), random);
        }

        long coin = 0;
        CrystalAmounts crystals = new CrystalAmounts(0, 0, 0);

        List<String> items = new ArrayList<>();
        int supplyRolls = milestoneFirstClear ? roll(plan.recoverySupplies(), random) : 0;
        for (int i = 0; i < supplyRolls; i++) {
            items.add(i == 0 ? "High Recovery Potion" : "Recovery Supply Roll");
        }

        List<String> coffers = new ArrayList<>();

        List<String> equipmentCaches = new ArrayList<>();

        List<String> materials = new ArrayList<>();

        String stableSession = sessionId == null ? "" : sessionId;
        String packageIdentity = playerId + "|" + floorKey(plan.floor()) + "|"
                + stableSession + "|" + source;
        if (stableSession.isBlank()) {
            packageIdentity += "|" + System.nanoTime();
        }
        String packageId = UUID.nameUUIDFromBytes(
                packageIdentity.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        ).toString();

        return new RewardPackage(
                packageId,
                playerId,
                sessionId == null ? "" : sessionId,
                plan.floor(),
                source,
                firstClear,
                ep,
                capacity,
                coin,
                crystals,
                items,
                coffers,
                equipmentCaches,
                materials,
                false,
                false,
                false,
                System.currentTimeMillis()
        );
    }

    private static RewardStateStore createDefaultStore() {
        Path root = Path.of(System.getProperty("user.dir", "."))
                .resolve("config")
                .resolve("tensura_dungeon_ui")
                .resolve("rewards");
        return new RewardStateStore(root, resolvePlayerId());
    }

    private static String resolvePlayerId() {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Method getInstance = minecraftClass.getMethod("getInstance");
            Object minecraft = getInstance.invoke(null);
            Field playerField = minecraftClass.getField("player");
            Object player = playerField.get(minecraft);
            if (player != null) {
                Method getUuid = player.getClass().getMethod("getUUID");
                Object uuid = getUuid.invoke(player);
                if (uuid != null) return uuid.toString();
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return "local";
    }

    private static Random deterministicRandom(
            String playerId,
            int floor,
            String sessionId,
            String source
    ) {
        long seed = 0x9E3779B97F4A7C15L;
        seed ^= ((long) Math.max(1, floor) * 104_729L);
        seed ^= (playerId == null ? 0 : playerId.hashCode()) * 31L;
        seed ^= (sessionId == null ? 0 : sessionId.hashCode()) * 131L;
        seed ^= (source == null ? 0 : source.hashCode()) * 17L;
        return new Random(seed);
    }

    private static int roll(IntRange range, Random random) {
        if (range == null || range.max() <= range.min()) return range == null ? 0 : range.min();
        return range.min() + random.nextInt(range.max() - range.min() + 1);
    }

    private static int safeAdd(int left, int right) {
        long value = (long) Math.max(0, left) + Math.max(0, right);
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String maybePromoteQuality(
            String quality,
            double upgradeChance,
            Random random
    ) {
        String base = quality == null || quality.isBlank()
                ? "standard" : quality.toLowerCase(Locale.ROOT);
        if (upgradeChance <= 0.0 || random.nextDouble() >= clamp01(upgradeChance)) {
            return base;
        }
        return switch (base) {
            case "basic", "common" -> "standard";
            case "standard", "uncommon" -> "rare";
            case "rare", "boss", "treasure", "milestone" -> "epic";
            case "epic" -> "masterwork";
            default -> base;
        };
    }

    private static String floorKey(int floor) {
        return String.format(Locale.ROOT, "floor_%02d", floor);
    }

    private static String crystalValue(CrystalAmounts crystals) {
        if (crystals.medium() > 0) {
            return crystals.medium() + "M" + (crystals.high() > 0 ? " / " + crystals.high() + "H" : "");
        }
        if (crystals.low() > 0) return crystals.low() + "L";
        return crystals.high() + "H";
    }

    private static String compactNumber(int value) {
        if (value >= 1_000_000) return stripZero(value / 1_000_000.0) + "M";
        if (value >= 1_000) return stripZero(value / 1_000.0) + "k";
        return formatNumber(value);
    }

    private static String stripZero(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
