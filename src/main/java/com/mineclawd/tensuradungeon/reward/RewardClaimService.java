package com.mineclawd.tensuradungeon.reward;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class RewardClaimService {
    public static final String STATUS_NONE = "NONE";
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String PATH_NONE = "NONE";

    private final InventoryGrant inventoryGrant;
    private final MaxEpMutator maxEpMutator;

    public RewardClaimService(InventoryGrant inventoryGrant, MaxEpMutator maxEpMutator) {
        this.inventoryGrant = inventoryGrant;
        this.maxEpMutator = maxEpMutator;
    }

    public ClaimAttempt claim(Player player, ClaimRequest request) {
        if (!inventoryGrant.canGrant(player, request.rewardStacks())) {
            return new ClaimAttempt(false, false, "Inventory full. Reward remains in mailbox.", 0,
                STATUS_NONE, PATH_NONE, "NONE");
        }

        int applied = 0;
        String status = STATUS_NONE;
        String path = PATH_NONE;
        if (request.maxEpClaimAmount() > 0) {
            MaxEpMutator.MutationResult mutation = maxEpMutator.increaseMaxEp(player, request.maxEpClaimAmount());
            path = mutation.mutationPath();
            if (!"NONE".equals(mutation.error()) || !mutation.valueChanged()) {
                String error = "NONE".equals(mutation.error()) ? "NO_VALUE_CHANGE" : mutation.error();
                boolean persistFailedStatus = !"PLAYER_NOT_SERVER_PLAYER".equals(error);
                if (!persistFailedStatus) {
                    path = request.priorMutationPath();
                }
                return new ClaimAttempt(false, persistFailedStatus, "Tensura max EP reward failed. Reward remains in mailbox.",
                    0, STATUS_FAILED, path, error);
            }
            applied = request.maxEpClaimAmount();
            status = STATUS_APPLIED;
        }

        inventoryGrant.grant(player, request.rewardStacks());
        return new ClaimAttempt(true, false, "Claimed reward package " + request.rewardId() + ".", applied,
            status, path, "NONE");
    }

    public record ClaimRequest(long rewardId, List<ItemStack> rewardStacks, int maxEpClaimAmount, String priorMutationPath) {
        public ClaimRequest(long rewardId, List<ItemStack> rewardStacks, int maxEpClaimAmount) {
            this(rewardId, rewardStacks, maxEpClaimAmount, PATH_NONE);
        }

        public ClaimRequest {
            rewardStacks = List.copyOf(rewardStacks);
            priorMutationPath = priorMutationPath == null || priorMutationPath.isBlank() ? PATH_NONE : priorMutationPath;
        }
    }

    public record ClaimAttempt(
        boolean claimed,
        boolean persistFailedStatus,
        String message,
        int maxEpApplied,
        String tensuraStatus,
        String mutationPath,
        String error
    ) {
    }
}
