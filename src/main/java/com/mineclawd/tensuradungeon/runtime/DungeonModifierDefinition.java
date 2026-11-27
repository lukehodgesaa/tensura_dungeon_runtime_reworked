package com.mineclawd.tensuradungeon.runtime;

import java.util.List;

public enum DungeonModifierDefinition {
    ARMORED(
        "armored",
        "Armored",
        DungeonModifierCategory.DEFENSE,
        1,
        15,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of(),
        DungeonModifierPerformanceCost.LOW,
        1.15D,
        1.00D,
        1.00D,
        0,
        1.05D,
        "Slightly tougher enemies with a small defensive bonus."
    ),
    REGENERATIVE(
        "regenerative",
        "Regenerative",
        DungeonModifierCategory.SUSTAIN,
        1,
        15,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of(),
        DungeonModifierPerformanceCost.MEDIUM,
        1.12D,
        1.00D,
        1.00D,
        0,
        1.05D,
        "Enemies recover a small amount of health at a fixed interval while the room is active."
    ),
    SWIFT(
        "swift",
        "Swift",
        DungeonModifierCategory.MOBILITY,
        5,
        12,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of(),
        DungeonModifierPerformanceCost.LOW,
        1.08D,
        1.00D,
        1.00D,
        0,
        1.05D,
        "Enemies move 18% faster and attack 12% faster."
    ),
    BERSERKER(
        "berserker",
        "Berserker",
        DungeonModifierCategory.OFFENSE,
        6,
        10,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of("molten"),
        DungeonModifierPerformanceCost.LOW,
        1.12D,
        1.00D,
        1.00D,
        0,
        1.05D,
        "Enemies deal 30% more damage below half health and attack 12% faster below quarter health."
    ),
    POISONOUS(
        "poisonous",
        "Poisonous",
        DungeonModifierCategory.STATUS,
        9,
        12,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of(),
        DungeonModifierPerformanceCost.LOW,
        1.10D,
        1.00D,
        1.00D,
        0,
        1.05D,
        "Direct enemy attacks build poison up to five stacks."
    ),
    MOLTEN(
        "molten",
        "Molten",
        DungeonModifierCategory.OFFENSE,
        1,
        15,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of(),
        DungeonModifierPerformanceCost.LOW,
        1.10D,
        1.00D,
        1.00D,
        0,
        1.05D,
        "Enemies resist fire and ignite dungeon players they damage for 15 seconds."
    ),
    SUMMONER(
        "summoner",
        "Summoner",
        DungeonModifierCategory.SUMMONING,
        1,
        15,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of("swarm", "riftbound"),
        DungeonModifierPerformanceCost.MEDIUM,
        1.18D,
        1.00D,
        1.00D,
        2,
        1.05D,
        "A small number of tracked enemies periodically call weaker same-kind adds with strict caps."
    ),
    SWARM(
        "swarm",
        "Swarm",
        DungeonModifierCategory.MOB_COUNT,
        1,
        15,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of("summoner", "riftbound", "explosive"),
        DungeonModifierPerformanceCost.MEDIUM,
        1.45D,
        1.45D,
        0.75D,
        4,
        1.10D,
        "More enemies, lower individual health, strict total cap."
    ),
    EXPLOSIVE(
        "explosive",
        "Explosive",
        DungeonModifierCategory.OFFENSE,
        12,
        9,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of("swarm"),
        DungeonModifierPerformanceCost.MEDIUM,
        1.15D,
        1.00D,
        1.00D,
        0,
        1.08D,
        "Ordinary enemies leave a telegraphed, non-destructive blast when they die."
    ),
    RIFTBOUND(
        "riftbound",
        "Riftbound",
        DungeonModifierCategory.SUMMONING,
        16,
        8,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of("swarm", "summoner"),
        DungeonModifierPerformanceCost.MEDIUM,
        1.18D,
        1.00D,
        1.00D,
        2,
        1.08D,
        "One marked carrier calls a bounded number of weaker same-family reinforcements."
    ),
    SHADOWED(
        "shadowed",
        "Shadowed",
        DungeonModifierCategory.ENVIRONMENT,
        22,
        6,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of(),
        DungeonModifierPerformanceCost.LOW,
        1.12D,
        1.00D,
        1.00D,
        0,
        1.08D,
        "Abyssal haze reduces visibility and increases flank-role selection by 45%."
    ),
    ELITE_VANGUARD(
        "elite-vanguard",
        "Elite Vanguard",
        DungeonModifierCategory.ELITE,
        25,
        5,
        DungeonModifierStackingRule.EXCLUSIVE_CATEGORY,
        List.of(),
        DungeonModifierPerformanceCost.MEDIUM,
        1.20D,
        1.00D,
        1.00D,
        1,
        1.10D,
        "Each combat room requires one eligible elite anchor."
    );

    private final String id;
    private final String displayName;
    private final DungeonModifierCategory category;
    private final int minFloor;
    private final int weight;
    private final DungeonModifierStackingRule stackingRule;
    private final List<String> incompatibleWith;
    private final DungeonModifierPerformanceCost performanceCost;
    private final double budgetMultiplier;
    private final double mobCountMultiplier;
    private final double healthMultiplier;
    private final int maxAliveBonus;
    private final double rewardMultiplier;
    private final String notes;

    DungeonModifierDefinition(String id, String displayName, DungeonModifierCategory category, int minFloor, int weight, DungeonModifierStackingRule stackingRule, List<String> incompatibleWith, DungeonModifierPerformanceCost performanceCost, double budgetMultiplier, double mobCountMultiplier, double healthMultiplier, int maxAliveBonus, double rewardMultiplier, String notes) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.minFloor = minFloor;
        this.weight = weight;
        this.stackingRule = stackingRule;
        this.incompatibleWith = incompatibleWith;
        this.performanceCost = performanceCost;
        this.budgetMultiplier = budgetMultiplier;
        this.mobCountMultiplier = mobCountMultiplier;
        this.healthMultiplier = healthMultiplier;
        this.maxAliveBonus = maxAliveBonus;
        this.rewardMultiplier = rewardMultiplier;
        this.notes = notes;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public DungeonModifierCategory getCategory() { return category; }
    public int getMinFloor() { return minFloor; }
    public int getWeight() { return weight; }
    public DungeonModifierStackingRule getStackingRule() { return stackingRule; }
    public List<String> getIncompatibleWith() { return incompatibleWith; }
    public DungeonModifierPerformanceCost getPerformanceCost() { return performanceCost; }
    public double getBudgetMultiplier() { return budgetMultiplier; }
    public double getMobCountMultiplier() { return mobCountMultiplier; }
    public double getHealthMultiplier() { return healthMultiplier; }
    public int getMaxAliveBonus() { return maxAliveBonus; }
    public double getRewardMultiplier() { return rewardMultiplier; }
    public String getNotes() { return notes; }

    public boolean isPrimary() {
        return this == REGENERATIVE || this == SWARM || this == SUMMONER || this == RIFTBOUND;
    }

    public String getAlignment() {
        return this == SHADOWED ? "ENVIRONMENT" : this == ELITE_VANGUARD ? "BOSS" : "HOSTILE";
    }

    public String getIconKey() {
        return id;
    }

    public boolean isCompatibleWith(DungeonModifierDefinition other) {
        return other != null
            && this != other
            && !incompatibleWith.contains(other.id)
            && !other.incompatibleWith.contains(id)
            && (stackingRule != DungeonModifierStackingRule.EXCLUSIVE_CATEGORY || category != other.category);
    }

    public static DungeonModifierDefinition byId(String id) {
        for (DungeonModifierDefinition modifier : values()) {
            if (modifier.id.equalsIgnoreCase(id)) {
                return modifier;
            }
        }
        return null;
    }
}
