package com.mineclawd.tensurahudnative.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RuntimeObjectiveProvider {
    private static final String BRIDGE_CLASS = "com.mineclawd.tensuradungeon.client.DungeonObjectiveClient";
    private static final long STALE_AFTER_MS = 2_000L;
    private static boolean resolved;
    private static Method snapshotMethod;

    private RuntimeObjectiveProvider() {}

    static Result read(long now) {
        Method method = resolve();
        if (method == null) {
            return Result.UNAVAILABLE;
        }
        try {
            Object value = method.invoke(null);
            if (value instanceof Map<?, ?> map) {
                return fromMap(map, now);
            }
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
        return Result.UNAVAILABLE;
    }

    static Result fromMap(Map<?, ?> map, long now) {
        if (map == null || map.isEmpty()) {
            return new Result(true, HudSnapshot.Objective.inactive());
        }
        long receivedAt = longValue(map, "receivedAtMillis", 0L);
        if (receivedAt <= 0L || now - receivedAt > STALE_AFTER_MS) {
            return new Result(true, HudSnapshot.Objective.inactive());
        }

        boolean active = booleanValue(map, "active", false);
        int floor = integer(map, "floorNumber", 0);
        String roomState = string(map, "roomState", "NONE");
        String objectiveId = string(map, "objectiveId", "NONE");
        String label = string(map, "objectiveLabel", "No active room objective");
        long revision = longValue(map, "revisionGameTime", 0L);
        String signature = floor + "|" + roomState.toUpperCase(Locale.ROOT) + "|"
                + objectiveId.toUpperCase(Locale.ROOT) + "|" + revision;
        HudSnapshot.Objective objective = new HudSnapshot.Objective(
                active,
                "Dimensional Dungeon",
                floor > 0 ? "Floor " + floor : "",
                signature,
                floor,
                roomState,
                objectiveId,
                label,
                integer(map, "objectiveProgress", 0),
                integer(map, "objectiveTarget", 0),
                integer(map, "remainingTicks", 0),
                integer(map, "durationTicks", 0),
                integer(map, "enemiesAlive", 0),
                integer(map, "enemiesSpawned", 0),
                string(map, "modifierNames", ""),
                string(map, "modifierSummary", ""),
                integer(map, "compositionSpent", 0),
                integer(map, "compositionBudget", 0),
                string(map, "compositionSummary", ""),
                longValue(map, "runElapsedTicks", 0L),
                revision,
                recoveryLines(map)
        );
        return new Result(true, objective);
    }

    private static List<HudSnapshot.ObjectiveLine> recoveryLines(Map<?, ?> map) {
        int rolling = integer(map, "recoveryWindowPermille", 0);
        int charge = integer(map, "recoveryLastCharge", 0);
        if (rolling <= 0 && charge <= 0) return List.of();
        String tier = string(map, "recoveryTier", "STABLE").toUpperCase(Locale.ROOT);
        String accent = tier.equals("OVERDRAWN") ? "red" : tier.equals("STRAINED") ? "orange" : "green";
        return List.of(new HudSnapshot.ObjectiveLine(
                "Recovery " + tier + " · Cost " + charge,
                Math.min(rolling, 300),
                300,
                accent
        ));
    }

    private static Method resolve() {
        if (resolved) {
            return snapshotMethod;
        }
        resolved = true;
        try {
            snapshotMethod = Class.forName(BRIDGE_CLASS).getMethod("snapshot");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            snapshotMethod = null;
        }
        return snapshotMethod;
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static int integer(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? Math.max(0, number.intValue()) : fallback;
    }

    private static long longValue(Map<?, ?> map, String key, long fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? Math.max(0L, number.longValue()) : fallback;
    }

    private static boolean booleanValue(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    record Result(boolean available, HudSnapshot.Objective objective) {
        static final Result UNAVAILABLE = new Result(false, HudSnapshot.Objective.inactive());
    }
}
