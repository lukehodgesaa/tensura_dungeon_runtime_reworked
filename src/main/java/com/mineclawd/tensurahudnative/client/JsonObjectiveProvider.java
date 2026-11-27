package com.mineclawd.tensurahudnative.client;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class JsonObjectiveProvider {
    private static final long POLL_INTERVAL_MS = 200L;
    private static long lastPollAt;
    private static Path lastPath;
    private static FileTime lastModified;
    private static BridgeSnapshot cached = BridgeSnapshot.EMPTY;

    private JsonObjectiveProvider() {
    }

    static BridgeSnapshot read(Minecraft minecraft, HudSettings settings, long now) {
        if (now - lastPollAt < POLL_INTERVAL_MS) {
            return cached;
        }
        lastPollAt = now;
        Path selected = firstExisting(resolveCandidates(minecraft, settings));
        if (selected == null) {
            cached = BridgeSnapshot.EMPTY;
            lastPath = null;
            lastModified = null;
            return cached;
        }
        try {
            FileTime modified = Files.getLastModifiedTime(selected);
            if (selected.equals(lastPath) && modified.equals(lastModified)) {
                return cached;
            }
            String text = Files.readString(selected, StandardCharsets.UTF_8);
            cached = parse(text, selected);
            lastPath = selected;
            lastModified = modified;
        } catch (IOException | IllegalArgumentException ignored) {
            cached = BridgeSnapshot.EMPTY;
        }
        return cached;
    }

    private static BridgeSnapshot parse(String text, Path source) {
        Map<String, Object> root = MiniJson.object(MiniJson.parse(stripBom(text)));
        Map<String, Object> objective = MiniJson.object(root.get("objective"));
        Map<String, Object> status = MiniJson.object(root.get("status"));
        Map<String, Object> unlock = MiniJson.object(root.get("skillUnlock"));
        if (unlock.isEmpty()) {
            unlock = MiniJson.object(root.get("unlock"));
        }

        String roomState = MiniJson.string(objective, "roomState", "NONE");
        boolean active = MiniJson.bool(objective, "active", false)
                || !(roomState.equalsIgnoreCase("NONE")
                || roomState.equalsIgnoreCase("INACTIVE")
                || roomState.equalsIgnoreCase("COMPLETED"));
        int progress = MiniJson.integer(objective, "objectiveProgress", 0);
        int target = MiniJson.integer(objective, "objectiveTarget", 0);
        int remaining = MiniJson.integer(objective, "objectiveRemainingTicks", 0);
        int duration = MiniJson.integer(objective, "objectiveDurationTicks", 0);
        List<HudSnapshot.ObjectiveLine> lines = parseLines(objective, progress, target, remaining, duration);

        HudSnapshot.Objective parsedObjective = new HudSnapshot.Objective(
                active,
                MiniJson.string(objective, "title", "Dimensional Dungeon"),
                MiniJson.string(objective, "subtitle", floorSubtitle(objective)),
                MiniJson.string(objective, "signature", ""),
                MiniJson.integer(objective, "floorNumber", 0),
                roomState,
                MiniJson.string(objective, "objectiveId", "NONE"),
                MiniJson.string(objective, "objectiveLabel", "No active room objective"),
                progress,
                target,
                remaining,
                duration,
                MiniJson.integer(objective, "enemiesAlive", 0),
                MiniJson.integer(objective, "enemiesSpawned", 0),
                firstNonBlank(
                        MiniJson.string(objective, "activeModifierNames", ""),
                        MiniJson.string(objective, "activeModifierIds", "")
                ),
                MiniJson.string(objective, "appliedEnemyModifierSummary", ""),
                MiniJson.integer(objective, "compositionSpent", 0),
                MiniJson.integer(objective, "compositionBudget", 0),
                MiniJson.string(objective, "compositionSummary", ""),
                MiniJson.longNumber(objective, "runElapsedTicks", 0L),
                MiniJson.longNumber(objective, "revisionGameTime", 0L),
                lines
        );

        UnlockEvent unlockEvent = unlock.isEmpty() ? UnlockEvent.NONE : new UnlockEvent(
                MiniJson.string(unlock, "eventId", MiniJson.string(unlock, "id", "")),
                MiniJson.string(unlock, "name", ""),
                MiniJson.string(unlock, "category", "unique"),
                MiniJson.string(unlock, "message", ""),
                MiniJson.longNumber(unlock, "updatedAt", MiniJson.longNumber(root, "updatedAt", 0L))
        );

        return new BridgeSnapshot(
                parsedObjective,
                MiniJson.string(status, "race", ""),
                MiniJson.string(status, "currentForm", ""),
                MiniJson.integer(status, "level", -1),
                MiniJson.number(status, "ep", Double.NaN),
                unlockEvent,
                source.toString()
        );
    }

    private static List<HudSnapshot.ObjectiveLine> parseLines(Map<String, Object> objective, int progress, int target,
                                                               int remaining, int duration) {
        List<HudSnapshot.ObjectiveLine> result = new ArrayList<>();
        for (Object entry : MiniJson.array(objective.get("items"))) {
            Map<String, Object> item = MiniJson.object(entry);
            String label = firstNonBlank(
                    MiniJson.string(item, "label", ""),
                    MiniJson.string(item, "text", ""),
                    MiniJson.string(item, "title", "")
            );
            if (label.isBlank()) {
                continue;
            }
            int current = MiniJson.integer(item, "current", MiniJson.integer(item, "progress", 0));
            int itemTarget = MiniJson.integer(item, "target", 0);
            int[] count = parseCountPair(item.get("count"), current, itemTarget);
            boolean bonus = MiniJson.bool(item, "bonus", false);
            result.add(new HudSnapshot.ObjectiveLine(
                    label,
                    count[0],
                    count[1],
                    MiniJson.string(item, "accent", bonus ? "lime" : "cyan")
            ));
        }
        if (result.isEmpty()) {
            String label = MiniJson.string(objective, "objectiveLabel", "No active room objective");
            result.add(new HudSnapshot.ObjectiveLine(label, progress, target, "cyan"));
            if (duration > 0) {
                int elapsed = Math.max(0, duration - remaining);
                result.add(new HudSnapshot.ObjectiveLine(
                        "Bonus: Complete within " + Math.max(1, duration / 20) + "s.",
                        elapsed / 20,
                        Math.max(1, duration / 20),
                        "lime"
                ));
            }
        }
        return result;
    }

    static int[] parseCountPair(Object value, int fallbackCurrent, int fallbackTarget) {
        if (value == null) {
            return new int[]{Math.max(0, fallbackCurrent), Math.max(0, fallbackTarget)};
        }
        String raw = String.valueOf(value).trim().replace(",", "");
        if (raw.isEmpty()) {
            return new int[]{Math.max(0, fallbackCurrent), Math.max(0, fallbackTarget)};
        }
        String[] parts = raw.split("\\s*/\\s*", 2);
        int parsedCurrent = parseNonNegative(parts[0], fallbackCurrent);
        int parsedTarget = parts.length > 1 ? parseNonNegative(parts[1], fallbackTarget) : fallbackTarget;
        return new int[]{Math.max(0, parsedCurrent), Math.max(0, parsedTarget)};
    }

    private static int parseNonNegative(String value, int fallback) {
        if (value == null) {
            return Math.max(0, fallback);
        }
        String digits = value.trim().replaceAll("[^0-9-]", "");
        if (digits.isEmpty() || "-".equals(digits)) {
            return Math.max(0, fallback);
        }
        try {
            return Math.max(0, Integer.parseInt(digits));
        } catch (NumberFormatException ignored) {
            return Math.max(0, fallback);
        }
    }

    private static String floorSubtitle(Map<String, Object> objective) {
        int floor = MiniJson.integer(objective, "floorNumber", 0);
        return floor > 0 ? "Floor " + floor : "";
    }

    private static List<Path> resolveCandidates(Minecraft minecraft, HudSettings settings) {
        return settings.objectiveBridgePath.isBlank()
                ? List.of()
                : List.of(Paths.get(settings.objectiveBridgePath));
    }

    private static Path firstExisting(List<Path> candidates) {
        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private static String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    record BridgeSnapshot(HudSnapshot.Objective objective, String race, String currentForm, int level, double ep,
                          UnlockEvent unlock, String sourcePath) {
        static final BridgeSnapshot EMPTY = new BridgeSnapshot(HudSnapshot.Objective.inactive(), "", "", -1,
                Double.NaN, UnlockEvent.NONE, "");
    }

    record UnlockEvent(String eventId, String name, String category, String message, long updatedAt) {
        static final UnlockEvent NONE = new UnlockEvent("", "", "", "", 0L);

        boolean present() {
            return !name.isBlank();
        }
    }
}
