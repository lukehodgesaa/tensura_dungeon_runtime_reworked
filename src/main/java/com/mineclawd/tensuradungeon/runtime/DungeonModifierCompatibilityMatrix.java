package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinCatalog;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DungeonModifierCompatibilityMatrix {
    private DungeonModifierCompatibilityMatrix() {}

    public static CompatibilityResult validate(List<String> modifierIds) {
        if (modifierIds == null) return new CompatibilityResult(false, "NULL_MODIFIER_LIST");
        Set<String> normalizedIds = new HashSet<>();
        List<String> ids = modifierIds.stream()
            .map(DungeonModifierCompatibilityMatrix::normalize)
            .toList();
        for (String id : ids) {
            if (id.isBlank()) return new CompatibilityResult(false, "BLANK_MODIFIER_ID");
            if (!normalizedIds.add(id)) return new CompatibilityResult(false, "DUPLICATE_MODIFIER:" + id);
            if (DungeonModifierCatalog.byId(id) == null) {
                return new CompatibilityResult(false, "UNKNOWN_MODIFIER:" + id);
            }
        }

        int wardCount = 0;
        int nullificationCount = 0;
        Set<String> wardCategories = new HashSet<>();
        for (String id : ids) {
            DungeonModifierCatalog.Entry entry = DungeonModifierCatalog.byId(id);
            if (entry.wardCategory().isBlank()) continue;
            wardCount++;
            if (id.startsWith("ward-nullification-")) nullificationCount++;
            if (!wardCategories.add(entry.wardCategory())) {
                return new CompatibilityResult(false, "WARD_CATEGORY_DUPLICATE:" + entry.wardCategory());
            }
        }
        if (wardCount > 2) return new CompatibilityResult(false, "WARD_COUNT_EXCEEDED");
        if (nullificationCount > 1) return new CompatibilityResult(false, "NULLIFICATION_COUNT_EXCEEDED");

        for (int leftIndex = 0; leftIndex < ids.size(); leftIndex++) {
            String leftId = ids.get(leftIndex);
            DungeonModifierDefinition left = DungeonModifierDefinition.byId(leftId);
            for (int rightIndex = leftIndex + 1; rightIndex < ids.size(); rightIndex++) {
                String rightId = ids.get(rightIndex);
                DungeonModifierDefinition right = DungeonModifierDefinition.byId(rightId);
                if (left != null && right != null && !left.isCompatibleWith(right)) {
                    return new CompatibilityResult(false, "CORE_PAIR_INCOMPATIBLE:" + leftId + "+" + rightId);
                }
                String hardConflict = hardConflict(leftId, rightId);
                if (hardConflict != null) return new CompatibilityResult(false, hardConflict);
            }
        }
        return new CompatibilityResult(true, "NONE");
    }

    public static boolean areCompatible(List<String> modifierIds) {
        return validate(modifierIds).compatible();
    }

    private static String hardConflict(String left, String right) {
        if (samePair(left, right, "rallying-cry", "regenerative")) {
            return "RALLYING_CRY_REGENERATIVE_INCOMPATIBLE";
        }
        if (samePair(left, right, "reinforcement-bell", "summoner")
            || samePair(left, right, "reinforcement-bell", "riftbound")) {
            return "REINFORCEMENT_SUMMONING_INCOMPATIBLE";
        }
        if (samePair(left, right, "corrosive-remnant", "explosive")) {
            return "CORROSIVE_EXPLOSIVE_INCOMPATIBLE";
        }
        if (samePair(left, right, "spell-dampening", "ward-resistance-magic")
            || samePair(left, right, "spell-dampening", "ward-nullification-magic")) {
            return "SPELL_DAMPENING_MAGIC_WARD_STACKING_UNAPPROVED";
        }
        if (DungeonEnemyKinCatalog.isKinModifierId(left)
            && DungeonEnemyKinCatalog.isKinModifierId(right)) {
            return "KIN_FAMILY_DUPLICATE";
        }
        if ((DungeonEnemyKinCatalog.isKinModifierId(left)
                && (right.equals("swarm") || right.equals("summoner") || right.equals("riftbound")))
            || (DungeonEnemyKinCatalog.isKinModifierId(right)
                && (left.equals("swarm") || left.equals("summoner") || left.equals("riftbound")))) {
            return "KIN_POPULATION_INCOMPATIBLE";
        }
        if (samePair(left, right, "kin", "swarm")
            || samePair(left, right, "kin", "summoner")
            || samePair(left, right, "kin", "riftbound")) {
            return "KIN_POPULATION_INCOMPATIBLE";
        }
        return null;
    }

    private static boolean samePair(String left, String right, String expectedLeft, String expectedRight) {
        return (left.equals(expectedLeft) && right.equals(expectedRight))
            || (left.equals(expectedRight) && right.equals(expectedLeft));
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public record CompatibilityResult(boolean compatible, String reason) {}
}
