package com.mineclawd.tensuradungeon.compat.tensura;

import com.mineclawd.tensuradungeon.reward.RewardPreviewSource;
import net.minecraft.server.level.ServerPlayer;

public final class TensuraRewardPreviewSource implements RewardPreviewSource {
    public static final TensuraRewardPreviewSource INSTANCE = new TensuraRewardPreviewSource();

    private TensuraRewardPreviewSource() {
    }

    @Override
    public RewardPreview preview(ServerPlayer player, int floorNumber, String rewardTier, String sourceType) {
        int floor = Math.max(1, floorNumber);
        String tier = rewardTier == null || rewardTier.isBlank() ? "BASIC" : rewardTier;
        int epAmount = switch (tier) {
            case "IMPROVED" -> 750;
            case "ADVANCED" -> 2_000;
            case "ELITE" -> 6_000;
            case "MYTHIC_PLACEHOLDER" -> 15_000;
            default -> 250;
        } + floor * 25;
        int magiculeAmount = switch (tier) {
            case "IMPROVED" -> 150;
            case "ADVANCED" -> 400;
            case "ELITE" -> 1_200;
            case "MYTHIC_PLACEHOLDER" -> 3_000;
            default -> 50;
        } + floor * 5;
        int auraAmount = switch (tier) {
            case "IMPROVED" -> 30;
            case "ADVANCED" -> 80;
            case "ELITE" -> 240;
            case "MYTHIC_PLACEHOLDER" -> 600;
            default -> 10;
        } + floor;
        int battlewillAmount = switch (tier) {
            case "IMPROVED" -> 15;
            case "ADVANCED" -> 40;
            case "ELITE" -> 120;
            case "MYTHIC_PLACEHOLDER" -> 300;
            default -> 5;
        } + floor;
        TensuraCompatStatus status = TensuraCompat.getStatus();
        String previewStatus = statusFor(status.integrationState(), player == null);
        return new RewardPreview(
            true,
            epAmount,
            previewStatus,
            magiculeAmount,
            previewStatus,
            auraAmount,
            previewStatus,
            battlewillAmount,
            previewStatus,
            "NONE",
            previewStatus,
            sourceType == null || sourceType.isBlank() ? "ROOM_CLEAR" : sourceType,
            status.integrationState() != TensuraCompatStatus.IntegrationState.AVAILABLE || player == null
        );
    }

    private static String statusFor(TensuraCompatStatus.IntegrationState state, boolean playerOffline) {
        if (playerOffline) {
            return "DEFERRED_PLAYER_OFFLINE";
        }
        return switch (state) {
            case AVAILABLE -> "AVAILABLE_PREVIEW_ONLY";
            case ABSENT -> "UNAVAILABLE_MOD_MISSING";
            case UNSUPPORTED_VERSION -> "UNAVAILABLE_UNSUPPORTED_VERSION";
            case BROKEN -> "ERROR_BROKEN_ADAPTER";
        };
    }
}
