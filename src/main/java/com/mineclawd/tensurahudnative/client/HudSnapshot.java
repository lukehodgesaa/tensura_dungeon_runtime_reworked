package com.mineclawd.tensurahudnative.client;

import net.minecraft.world.item.ItemStack;

import java.util.List;

record HudSnapshot(
        Status status,
        List<Ability> abilities,
        SelectedItem selectedItem,
        Objective objective,
        Evolution evolution,
        String presetName,
        long sampledAtMillis
) {
    HudSnapshot {
        abilities = List.copyOf(abilities);
    }

    record Status(
            String playerName,
            String race,
            String currentForm,
            int level,
            double ep,
            double health,
            double maxHealth,
            double magicule,
            double maxMagicule,
            double aura,
            double maxAura,
            int defense,
            float saturation
    ) {
    }

    record Ability(
            String key,
            String name,
            String skillId,
            String modeName,
            int mode,
            int cooldownTicks,
            float masteryPercent,
            boolean empty,
            String icon,
            String category
    ) {
        Ability(String key, String name, String skillId, String modeName, int mode,
                int cooldownTicks, float masteryPercent, boolean empty, String icon) {
            this(key, name, skillId, modeName, mode, cooldownTicks, masteryPercent, empty, icon, "common");
        }

        static Ability empty(String key) {
            return new Ability(key, "Empty", "", "", -1, 0, 0.0F, true, "", "");
        }
    }

    record SelectedItem(ItemStack stack, String name, String key, int slotIndex, String itemId) {
    }

    record Objective(
            boolean active,
            String title,
            String subtitle,
            String signature,
            int floorNumber,
            String roomState,
            String objectiveId,
            String objectiveLabel,
            int objectiveProgress,
            int objectiveTarget,
            int remainingTicks,
            int durationTicks,
            int enemiesAlive,
            int enemiesSpawned,
            String modifierNames,
            String modifierSummary,
            int compositionSpent,
            int compositionBudget,
            String compositionSummary,
            long runElapsedTicks,
            long revisionGameTime,
            List<ObjectiveLine> lines
    ) {
        Objective {
            lines = List.copyOf(lines);
        }

        static Objective inactive() {
            return new Objective(false, "Dimensional Dungeon", "", "", 0, "NONE", "NONE",
                    "No active room objective", 0, 0, 0, 0, 0, 0,
                    "", "", 0, 0, "", 0L, 0L, List.of());
        }
    }

    record ObjectiveLine(String label, int current, int target, String accent) {
    }

    record Evolution(
            String currentRace,
            String trackedEvolution,
            float trackedProgress,
            boolean demonLordSeed,
            boolean trueDemonLord,
            boolean heroEgg,
            boolean trueHero,
            boolean blessed,
            boolean spiritualForm,
            int soulPoints,
            String alignment
    ) {
        String statusLabel() {
            if (trueDemonLord) {
                return "TRUE DEMON LORD";
            }
            if (trueHero) {
                return "TRUE HERO";
            }
            if (demonLordSeed) {
                return "DEMON LORD SEED";
            }
            if (heroEgg) {
                return "HERO'S EGG";
            }
            if (spiritualForm) {
                return "SPIRITUAL FORM";
            }
            return blessed ? "BLESSED" : alignment;
        }
    }

    record LearnedSkill(String id, String name, String icon, String category) {
    }
}
