package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomProfile;
import java.util.Collection;

public final class DungeonBudgetEngine {
    private static final double DEFAULT_MODIFIER_MULTIPLIER = 1.0D;
    private static final int GLOBAL_MAX_ALIVE_CAP = 24;
    private static final int GLOBAL_MAX_TOTAL_CAP = 32;

    private DungeonBudgetEngine() {
    }

    public static RoomBudgetResult calculate(int floorNumber, RoomProfile profile, Collection<DungeonModifierDefinition> modifiers) {
        int safeFloorNumber = Math.max(1, floorNumber);
        RoomProfile safeProfile = profile == null ? RoomProfile.STANDARD_COMBAT : profile;
        int floorBaseBudget = calculateFloorBaseBudget(safeFloorNumber);
        double profileMultiplier = safeProfile.getBudgetMultiplier();
        double modifierMultiplier = buildModifierMultiplier(modifiers);
        int roomTotalBudget = (int) Math.round(floorBaseBudget * profileMultiplier * modifierMultiplier);
        int floorBonus = Math.min(8, Math.max(0, (safeFloorNumber - 1) / 5));
        int modifierMaxAliveBonus = buildModifierMaxAliveBonus(modifiers);
        int maxAlive = Math.min(GLOBAL_MAX_ALIVE_CAP, Math.max(0, safeProfile.getSuggestedMaxAlive() + floorBonus + modifierMaxAliveBonus));
        int budgetConvertedMaxTotal = roomTotalBudget <= 0 ? 0 : Math.max(1, (int) Math.round(roomTotalBudget / 2.5D));
        int maxTotal = Math.min(GLOBAL_MAX_TOTAL_CAP, budgetConvertedMaxTotal);
        if (safeProfile.allowsCombat()) {
            maxAlive = Math.max(15, maxAlive);
            maxTotal = Math.max(15, maxTotal);
        }
        boolean spawnCapApplied = maxAlive < safeProfile.getSuggestedMaxAlive() || maxTotal < safeProfile.getInitialSpawnCount();
        return new RoomBudgetResult(
            safeFloorNumber,
            floorBaseBudget,
            safeProfile,
            profileMultiplier,
            modifierMultiplier,
            roomTotalBudget,
            maxAlive,
            maxTotal,
            spawnCapApplied,
            buildMobCountMultiplier(modifiers),
            buildHealthMultiplier(modifiers),
            modifierMaxAliveBonus,
            !modifiers.isEmpty(),
            !modifiers.isEmpty()
        );
    }

    public static int calculateFloorBaseBudget(int floorNumber) {
        int floor = Math.max(1, floorNumber);
        if (floor <= 5) {
            return interpolate(floor, 1, 5, 10, 25);
        }
        if (floor <= 15) {
            return interpolate(floor, 6, 15, 30, 60);
        }
        if (floor <= 30) {
            return interpolate(floor, 16, 30, 70, 120);
        }
        if (floor <= 50) {
            return interpolate(floor, 31, 50, 140, 260);
        }
        if (floor <= 100) {
            return interpolate(floor, 51, 100, 280, 650);
        }
        return 650;
    }

    private static double buildModifierMultiplier(Collection<DungeonModifierDefinition> modifiers) {
        double total = DEFAULT_MODIFIER_MULTIPLIER;
        for (DungeonModifierDefinition modifier : modifiers) {
            total *= modifier.getBudgetMultiplier();
        }
        return total;
    }

    private static double buildMobCountMultiplier(Collection<DungeonModifierDefinition> modifiers) {
        double total = 1.0D;
        for (DungeonModifierDefinition modifier : modifiers) {
            total *= modifier.getMobCountMultiplier();
        }
        return total;
    }

    private static double buildHealthMultiplier(Collection<DungeonModifierDefinition> modifiers) {
        double total = 1.0D;
        for (DungeonModifierDefinition modifier : modifiers) {
            total *= modifier.getHealthMultiplier();
        }
        return total;
    }

    private static int buildModifierMaxAliveBonus(Collection<DungeonModifierDefinition> modifiers) {
        int total = 0;
        for (DungeonModifierDefinition modifier : modifiers) {
            total += modifier.getMaxAliveBonus();
        }
        return total;
    }

    private static int interpolate(int value, int minInput, int maxInput, int minOutput, int maxOutput) {
        if (maxInput <= minInput) {
            return minOutput;
        }
        double progress = (double) (value - minInput) / (double) (maxInput - minInput);
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        return (int) Math.round(minOutput + progress * (maxOutput - minOutput));
    }
}
