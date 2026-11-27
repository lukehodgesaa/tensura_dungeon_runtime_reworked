package com.mineclawd.tensuradungeon.runtime;

import java.util.Locale;

public final class CoreRecoveryEncounterClassifier {
    private CoreRecoveryEncounterClassifier() {}

    public static Classification classify(int floor, String roomProfile, String objective) {
        MonsterCoreRecoveryPolicy.currentTier(floor);
        String profile = normalized(roomProfile, "UNKNOWN_PROFILE");
        String objectiveId = normalized(objective, "UNKNOWN_OBJECTIVE");

        if ("BOSS_CAPABLE".equals(profile)) {
            if (floor % 50 == 0) {
                return Classification.eligible(
                    CoreRecoveryData.ReceiptKind.MILESTONE_BOSS,
                    "APPROVED_FIRST_CLEAR_MILESTONE_PROVENANCE"
                );
            }
            if (floor % 10 == 0) {
                return Classification.eligible(
                    CoreRecoveryData.ReceiptKind.REGULAR_FLOOR_BOSS,
                    "APPROVED_REGULAR_FLOOR_BOSS"
                );
            }
            return Classification.skipped("UNRESOLVED_BOSS_CAPABLE_ENCOUNTER_CLASS");
        }

        if ("KILL_ELITE".equals(objectiveId)) {
            return switch (profile) {
                case "STANDARD_COMBAT", "LARGE_COMBAT", "ARENA_WAVE",
                     "CORRIDOR_LIGHT", "CORRIDOR_AMBUSH" ->
                    Classification.eligible(CoreRecoveryData.ReceiptKind.ELITE, "APPROVED_ELITE_OBJECTIVE");
                default -> Classification.skipped("ELITE_OBJECTIVE_ON_UNAPPROVED_ROOM_PROFILE");
            };
        }

        return switch (profile) {
            case "STANDARD_COMBAT", "LARGE_COMBAT", "ARENA_WAVE",
                 "CORRIDOR_LIGHT", "CORRIDOR_AMBUSH" ->
                Classification.eligible(
                    CoreRecoveryData.ReceiptKind.ORDINARY_COMBAT,
                    "APPROVED_ORDINARY_COMBAT_ROOM"
                );
            case "SAFE_ROOM", "CONNECTOR_EMPTY", "TRAVERSAL_HAZARD" ->
                Classification.skipped("NON_COMBAT_ROOM");
            case "TREASURE_AMBUSH" ->
                Classification.skipped("UNRESOLVED_OPTIONAL_REWARD_ROOM");
            default -> Classification.skipped("UNKNOWN_ROOM_PROFILE");
        };
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.toUpperCase(Locale.ROOT);
    }

    public record Classification(
        boolean eligible,
        CoreRecoveryData.ReceiptKind kind,
        String reason
    ) {
        private static Classification eligible(CoreRecoveryData.ReceiptKind kind, String reason) {
            return new Classification(true, kind, reason);
        }

        private static Classification skipped(String reason) {
            return new Classification(false, null, reason);
        }
    }
}
