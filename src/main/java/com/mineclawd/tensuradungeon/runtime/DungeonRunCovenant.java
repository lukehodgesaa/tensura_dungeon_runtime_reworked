package com.mineclawd.tensuradungeon.runtime;

public enum DungeonRunCovenant {
    GREED_PACT(
        "greed-pact",
        1.35D,
        DungeonModifierDesignStatus.APPROVED_NOT_LIVE,
        "Exact +35% reward benefit; reward scope, rounding, burden room, and initial burden policy remain pending."
    );

    private final String id;
    private final double rewardMultiplier;
    private final DungeonModifierDesignStatus designStatus;
    private final String statusReason;

    DungeonRunCovenant(
        String id,
        double rewardMultiplier,
        DungeonModifierDesignStatus designStatus,
        String statusReason
    ) {
        this.id = id;
        this.rewardMultiplier = rewardMultiplier;
        this.designStatus = designStatus;
        this.statusReason = statusReason;
    }

    public String id() {
        return id;
    }

    public double rewardMultiplier() {
        return rewardMultiplier;
    }

    public DungeonModifierDesignStatus designStatus() {
        return designStatus;
    }

    public String statusReason() {
        return statusReason;
    }

    public boolean isRollable() {
        return designStatus == DungeonModifierDesignStatus.LIVE;
    }

    public static DungeonRunCovenant byId(String id) {
        if (id == null || id.isBlank()) return null;
        for (DungeonRunCovenant covenant : values()) {
            if (covenant.id.equals(id)) return covenant;
        }
        return null;
    }
}
