package com.mineclawd.tensuradungeon.block.entity;

public enum RoomProfile {
    CONNECTOR_EMPTY(0.0D, 0, 0, false, false, "Passive connector profile intended for non-combat template links."),
    STANDARD_COMBAT(1.0D, 10, 2, true, true, "Default combat room profile."),
    LARGE_COMBAT(1.6D, 15, 4, true, true, "Larger combat footprint with higher future spawn budget."),
    ARENA_WAVE(2.2D, 20, 5, true, true, "Arena-style profile intended for dense future wave encounters."),
    CORRIDOR_LIGHT(0.35D, 5, 1, true, true, "Low-pressure corridor combat profile."),
    CORRIDOR_AMBUSH(0.55D, 7, 2, true, true, "Short corridor ambush profile."),
    TRAVERSAL_HAZARD(0.2D, 2, 0, false, true, "Traversal-focused room profile with optional objective anchors."),
    TREASURE_AMBUSH(0.8D, 10, 2, true, true, "Treasure-side ambush profile."),
    BOSS_CAPABLE(2.8D, 24, 6, true, true, "Large authored room profile intended to support future boss-capable templates."),
    SAFE_ROOM(0.0D, 0, 0, false, false, "Passive room profile that does not start combat encounters.");

    private final double budgetMultiplier;
    private final int suggestedMaxAlive;
    private final int initialSpawnCount;
    private final boolean combatAllowed;
    private final boolean objectiveAllowed;
    private final String notes;

    RoomProfile(double budgetMultiplier, int suggestedMaxAlive, int initialSpawnCount, boolean combatAllowed, boolean objectiveAllowed, String notes) {
        this.budgetMultiplier = budgetMultiplier;
        this.suggestedMaxAlive = suggestedMaxAlive;
        this.initialSpawnCount = initialSpawnCount;
        this.combatAllowed = combatAllowed;
        this.objectiveAllowed = objectiveAllowed;
        this.notes = notes;
    }

    public double getBudgetMultiplier() {
        return budgetMultiplier;
    }

    public int getSuggestedMaxAlive() {
        return suggestedMaxAlive;
    }

    public int getInitialSpawnCount() {
        return initialSpawnCount;
    }

    public boolean allowsCombat() {
        return combatAllowed;
    }

    public boolean allowsObjective() {
        return objectiveAllowed;
    }

    public String getNotes() {
        return notes;
    }

    public static RoomProfile byName(String name) {
        for (RoomProfile profile : values()) {
            if (profile.name().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        return STANDARD_COMBAT;
    }
}
