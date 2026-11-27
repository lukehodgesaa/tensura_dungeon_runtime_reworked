package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomProfile;

public final class RoomBudgetResult {
    private final int floorNumber;
    private final int floorBaseBudget;
    private final RoomProfile profile;
    private final double profileMultiplier;
    private final double modifierMultiplier;
    private final int roomTotalBudget;
    private final int maxAlive;
    private final int maxTotal;
    private final boolean spawnCapApplied;
    private final double modifierMobCountMultiplier;
    private final double modifierHealthMultiplier;
    private final int modifierMaxAliveBonus;
    private final boolean modifierSpawnApplied;
    private final boolean modifierHealthApplied;

    public RoomBudgetResult(int floorNumber, int floorBaseBudget, RoomProfile profile, double profileMultiplier, double modifierMultiplier, int roomTotalBudget, int maxAlive, int maxTotal, boolean spawnCapApplied, double modifierMobCountMultiplier, double modifierHealthMultiplier, int modifierMaxAliveBonus, boolean modifierSpawnApplied, boolean modifierHealthApplied) {
        this.floorNumber = floorNumber;
        this.floorBaseBudget = floorBaseBudget;
        this.profile = profile;
        this.profileMultiplier = profileMultiplier;
        this.modifierMultiplier = modifierMultiplier;
        this.roomTotalBudget = roomTotalBudget;
        this.maxAlive = maxAlive;
        this.maxTotal = maxTotal;
        this.spawnCapApplied = spawnCapApplied;
        this.modifierMobCountMultiplier = modifierMobCountMultiplier;
        this.modifierHealthMultiplier = modifierHealthMultiplier;
        this.modifierMaxAliveBonus = modifierMaxAliveBonus;
        this.modifierSpawnApplied = modifierSpawnApplied;
        this.modifierHealthApplied = modifierHealthApplied;
    }

    public int getFloorNumber() { return floorNumber; }
    public int getFloorBaseBudget() { return floorBaseBudget; }
    public RoomProfile getProfile() { return profile; }
    public double getProfileMultiplier() { return profileMultiplier; }
    public double getModifierMultiplier() { return modifierMultiplier; }
    public int getRoomTotalBudget() { return roomTotalBudget; }
    public int getMaxAlive() { return maxAlive; }
    public int getMaxTotal() { return maxTotal; }
    public boolean isSpawnCapApplied() { return spawnCapApplied; }
    public double getModifierMobCountMultiplier() { return modifierMobCountMultiplier; }
    public double getModifierHealthMultiplier() { return modifierHealthMultiplier; }
    public int getModifierMaxAliveBonus() { return modifierMaxAliveBonus; }
    public boolean isModifierSpawnApplied() { return modifierSpawnApplied; }
    public boolean isModifierHealthApplied() { return modifierHealthApplied; }
}
