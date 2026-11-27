package dev.luke.tensuradungeon.reward;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

public final class RewardStateStore {
    public static final int SCHEMA_VERSION = 1;
    private static final long LEGACY_STARTING_BALANCE_BRONZE = 12_450L;

    private final Path directory;
    private final String playerId;
    private final Path progressFile;
    private final Path mailboxFile;

    private final Properties progress = new Properties();
    private final Map<String, RewardPackage> packages = new LinkedHashMap<>();

    public RewardStateStore(Path directory, String playerId) {
        this.directory = directory.toAbsolutePath().normalize();
        this.playerId = sanitizePlayerId(playerId);
        this.progressFile = this.directory.resolve(this.playerId + "-progress.properties");
        this.mailboxFile = this.directory.resolve(this.playerId + "-mailbox.properties");
        load();
    }

    public synchronized String playerId() {
        return playerId;
    }

    public synchronized boolean hasCompletedFloor(int floor) {
        return Boolean.parseBoolean(progress.getProperty("first_clear.floor_" + Math.max(1, floor), "false"));
    }

    public synchronized boolean hasClaimedMilestone(String milestoneId) {
        return Boolean.parseBoolean(progress.getProperty("milestone." + safeKey(milestoneId), "false"));
    }

    public synchronized CoinValue wallet() {
        return CoinValue.ofBronze(longValue(progress, "wallet_bronze", LEGACY_STARTING_BALANCE_BRONZE));
    }

    public synchronized long totalEpEarned() {
        return longValue(progress, "ep_earned_total", 0L);
    }

    public synchronized long maximumEpCapacityGrowth() {
        return longValue(progress, "max_ep_capacity_growth", 0L);
    }

    public synchronized List<RewardPackage> mailboxPackages() {
        List<RewardPackage> result = new ArrayList<>(packages.values());
        result.sort(Comparator.comparingLong(RewardPackage::createdAtEpochMillis).reversed());
        return List.copyOf(result);
    }

    public synchronized RewardPackage packageById(String packageId) {
        return packages.get(packageId);
    }

    public synchronized RewardPackage packageForCompletion(
            int floor,
            String sessionId,
            String source
    ) {
        if (sessionId == null || sessionId.isBlank()) return null;
        String safeSource = source == null || source.isBlank() ? "floor_clear" : source;
        for (RewardPackage rewardPackage : packages.values()) {
            if (rewardPackage.floor() == Math.max(1, floor)
                    && rewardPackage.sessionId().equals(sessionId)
                    && rewardPackage.source().equals(safeSource)) {
                return rewardPackage;
            }
        }
        return null;
    }

    public synchronized void persistCompletionPackage(
            RewardPackage rewardPackage,
            boolean markFirstClear,
            String milestoneId
    ) {
        if (rewardPackage == null || rewardPackage.packageId().isBlank()) {
            throw new IllegalArgumentException("Reward package requires a stable package id");
        }
        if (!packages.containsKey(rewardPackage.packageId())) {
            packages.put(rewardPackage.packageId(), rewardPackage);
            saveMailbox();
        }

        if (markFirstClear) {
            progress.setProperty("first_clear.floor_" + rewardPackage.floor(), "true");
        }
        if (milestoneId != null && !milestoneId.isBlank()) {
            progress.setProperty("milestone." + safeKey(milestoneId), "true");
        }
        progress.setProperty("schema_version", Integer.toString(SCHEMA_VERSION));
        saveProgress();
    }

    public synchronized boolean claimPackage(
            String packageId,
            Predicate<RewardPackage> grantHandler
    ) {
        RewardPackage rewardPackage = packages.get(packageId);
        if (rewardPackage == null || rewardPackage.claimed()) return false;
        Predicate<RewardPackage> safeHandler = grantHandler == null ? ignored -> false : grantHandler;
        if (!safeHandler.test(rewardPackage)) return false;

        long wallet = CoinValue.addCoinValue(
                longValue(progress, "wallet_bronze", LEGACY_STARTING_BALANCE_BRONZE),
                rewardPackage.coinBronze()
        );
        progress.setProperty("wallet_bronze", Long.toString(wallet));
        progress.setProperty("ep_earned_total", Long.toString(
                safeAdd(longValue(progress, "ep_earned_total", 0L), rewardPackage.ep())));
        progress.setProperty("max_ep_capacity_growth", Long.toString(
                safeAdd(longValue(progress, "max_ep_capacity_growth", 0L), rewardPackage.maxEpCapacity())));

        packages.put(packageId, rewardPackage.withClaimed(true));
        saveProgress();
        saveMailbox();
        return true;
    }

    public synchronized boolean removePackageForRollback(String packageId) {
        RewardPackage removed = packages.remove(packageId);
        if (removed == null) return false;
        saveMailbox();
        return true;
    }

    public synchronized void markFloorUnclearedForRollback(int floor) {
        progress.remove("first_clear.floor_" + Math.max(1, floor));
        saveProgress();
    }

    private void load() {
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to create reward state directory " + directory, error);
        }

        loadProperties(progressFile, progress);
        migrateProgress();

        Properties mailbox = new Properties();
        loadProperties(mailboxFile, mailbox);
        int count = integer(mailbox, "package_count", 0);
        for (int i = 0; i < count; i++) {
            String prefix = "package." + i + ".";
            RewardPackage rewardPackage = RewardPackage.read(mailbox, prefix);
            if (!rewardPackage.packageId().isBlank()) packages.put(rewardPackage.packageId(), rewardPackage);
        }
    }

    private void migrateProgress() {
        int version = integer(progress, "schema_version", 0);
        if (version < 1) {
            progress.putIfAbsent("wallet_bronze", Long.toString(LEGACY_STARTING_BALANCE_BRONZE));
            progress.putIfAbsent("ep_earned_total", "0");
            progress.putIfAbsent("max_ep_capacity_growth", "0");
            progress.setProperty("schema_version", Integer.toString(SCHEMA_VERSION));
            saveProgress();
        }
    }

    private void saveProgress() {
        atomicStore(progressFile, progress,
                "Tensura Dimensional Dungeon reward progress - schema " + SCHEMA_VERSION);
    }

    private void saveMailbox() {
        Properties mailbox = new Properties();
        mailbox.setProperty("schema_version", Integer.toString(SCHEMA_VERSION));
        List<RewardPackage> ordered = new ArrayList<>(packages.values());
        ordered.sort(Comparator.comparingLong(RewardPackage::createdAtEpochMillis));
        mailbox.setProperty("package_count", Integer.toString(ordered.size()));
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).write(mailbox, "package." + i + ".");
        atomicStore(mailboxFile, mailbox,
                "Tensura Dimensional Dungeon mailbox - schema " + SCHEMA_VERSION);
    }

    private static void loadProperties(Path file, Properties target) {
        if (!Files.isRegularFile(file)) return;
        try (InputStream input = Files.newInputStream(file)) {
            target.load(input);
        } catch (IOException ignored) {
        }
    }

    private static void atomicStore(Path destination, Properties properties, String comment) {
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, comment);
            }
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to persist dungeon reward state at " + destination, error);
        }
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(Math.max(0L, left), Math.max(0L, right));
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static String sanitizePlayerId(String playerId) {
        String candidate = playerId == null || playerId.isBlank() ? "local" : playerId;
        return candidate.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private static String safeKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static long longValue(Properties properties, String key, long fallback) {
        try {
            return Long.parseLong(properties.getProperty(key, Long.toString(fallback)));
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }
}
