package dev.luke.tensuradungeon.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public record RewardPackage(
        String packageId,
        String playerId,
        String sessionId,
        int floor,
        String source,
        boolean firstClear,
        int ep,
        int maxEpCapacity,
        long coinBronze,
        CrystalAmounts crystals,
        List<String> items,
        List<String> guardianCoffers,
        List<String> equipmentCaches,
        List<String> bossMaterials,
        boolean recordingCrystalAwarded,
        boolean masterworkAwarded,
        boolean claimed,
        long createdAtEpochMillis
) {
    public RewardPackage {
        packageId = safe(packageId);
        playerId = safe(playerId);
        sessionId = safe(sessionId);
        floor = Math.max(1, floor);
        source = safe(source);
        ep = Math.max(0, ep);
        maxEpCapacity = Math.max(0, maxEpCapacity);
        coinBronze = Math.max(0L, coinBronze);
        crystals = crystals == null ? new CrystalAmounts(0, 0, 0) : crystals;
        items = List.copyOf(items == null ? List.of() : items);
        guardianCoffers = List.copyOf(guardianCoffers == null ? List.of() : guardianCoffers);
        equipmentCaches = List.copyOf(equipmentCaches == null ? List.of() : equipmentCaches);
        bossMaterials = List.copyOf(bossMaterials == null ? List.of() : bossMaterials);
        createdAtEpochMillis = Math.max(0L, createdAtEpochMillis);
    }

    public String floorId() {
        return "floor_" + floor;
    }

    public RewardPackage withClaimed(boolean newClaimed) {
        return new RewardPackage(
                packageId, playerId, sessionId, floor, source, firstClear,
                ep, maxEpCapacity, coinBronze, crystals, items, guardianCoffers,
                equipmentCaches, bossMaterials, recordingCrystalAwarded,
                masterworkAwarded, newClaimed, createdAtEpochMillis
        );
    }

    public void write(Properties properties, String prefix) {
        properties.setProperty(prefix + "package_id", packageId);
        properties.setProperty(prefix + "player_id", playerId);
        properties.setProperty(prefix + "session_id", sessionId);
        properties.setProperty(prefix + "floor", Integer.toString(floor));
        properties.setProperty(prefix + "source", source);
        properties.setProperty(prefix + "first_clear", Boolean.toString(firstClear));
        properties.setProperty(prefix + "ep", Integer.toString(ep));
        properties.setProperty(prefix + "max_ep_capacity", Integer.toString(maxEpCapacity));
        properties.setProperty(prefix + "coin_bronze", Long.toString(coinBronze));
        properties.setProperty(prefix + "crystals_low", Integer.toString(crystals.low()));
        properties.setProperty(prefix + "crystals_medium", Integer.toString(crystals.medium()));
        properties.setProperty(prefix + "crystals_high", Integer.toString(crystals.high()));
        properties.setProperty(prefix + "items", join(items));
        properties.setProperty(prefix + "guardian_coffers", join(guardianCoffers));
        properties.setProperty(prefix + "equipment_caches", join(equipmentCaches));
        properties.setProperty(prefix + "boss_materials", join(bossMaterials));
        properties.setProperty(prefix + "recording_crystal_awarded", Boolean.toString(recordingCrystalAwarded));
        properties.setProperty(prefix + "masterwork_awarded", Boolean.toString(masterworkAwarded));
        properties.setProperty(prefix + "claimed", Boolean.toString(claimed));
        properties.setProperty(prefix + "created_at", Long.toString(createdAtEpochMillis));
    }

    public static RewardPackage read(Properties properties, String prefix) {
        return new RewardPackage(
                properties.getProperty(prefix + "package_id", ""),
                properties.getProperty(prefix + "player_id", ""),
                properties.getProperty(prefix + "session_id", ""),
                integer(properties, prefix + "floor", 1),
                properties.getProperty(prefix + "source", "floor_clear"),
                bool(properties, prefix + "first_clear", false),
                integer(properties, prefix + "ep", 0),
                integer(properties, prefix + "max_ep_capacity", 0),
                longValue(properties, prefix + "coin_bronze", 0L),
                new CrystalAmounts(
                        integer(properties, prefix + "crystals_low", 0),
                        integer(properties, prefix + "crystals_medium", 0),
                        integer(properties, prefix + "crystals_high", 0)
                ),
                split(properties.getProperty(prefix + "items", "")),
                split(properties.getProperty(prefix + "guardian_coffers", "")),
                split(properties.getProperty(prefix + "equipment_caches", "")),
                split(properties.getProperty(prefix + "boss_materials", "")),
                bool(properties, prefix + "recording_crystal_awarded", false),
                bool(properties, prefix + "masterwork_awarded", false),
                bool(properties, prefix + "claimed", false),
                longValue(properties, prefix + "created_at", 0L)
        );
    }

    public String summary() {
        return String.format(Locale.ROOT,
                "Floor %d | %,d EP | %s | %dM/%dH crystals",
                floor, ep, CoinValue.formatCoinValue(coinBronze),
                crystals.medium(), crystals.high());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        List<String> escaped = new ArrayList<>(values.size());
        for (String value : values) {
            escaped.add(value.replace("\\", "\\\\").replace("|", "\\|"));
        }
        return String.join("|", escaped);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '|') {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (escaped) current.append('\\');
        result.add(current.toString());
        return List.copyOf(result);
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

    private static boolean bool(Properties properties, String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)));
    }

    public record CrystalAmounts(int low, int medium, int high) {
        public CrystalAmounts {
            low = Math.max(0, low);
            medium = Math.max(0, medium);
            high = Math.max(0, high);
        }
    }
}
