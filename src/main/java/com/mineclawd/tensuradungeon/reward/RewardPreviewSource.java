package com.mineclawd.tensuradungeon.reward;

import net.minecraft.server.level.ServerPlayer;

public interface RewardPreviewSource {
    RewardPreview preview(ServerPlayer player, int floorNumber, String rewardTier, String sourceType);

    record RewardPreview(
        boolean previewEnabled,
        int epAmount,
        String epStatus,
        int magiculeAmount,
        String magiculeStatus,
        int auraAmount,
        String auraStatus,
        int battlewillAmount,
        String battlewillStatus,
        String skillCandidateId,
        String skillStatus,
        String previewSource,
        boolean safeFallbackMode
    ) {
        public static RewardPreview disabled() {
            return new RewardPreview(false, 0, "UNAVAILABLE_MOD_MISSING", 0, "UNAVAILABLE_MOD_MISSING", 0,
                "UNAVAILABLE_MOD_MISSING", 0, "UNAVAILABLE_MOD_MISSING", "NONE", "UNAVAILABLE_HOOK_MISSING",
                "NONE", true);
        }
    }
}
