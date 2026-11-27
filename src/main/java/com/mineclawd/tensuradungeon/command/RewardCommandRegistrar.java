package com.mineclawd.tensuradungeon.command;

import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.getPlayer;

import com.mineclawd.tensuradungeon.runtime.RewardMailboxData;
import com.mineclawd.tensuradungeon.runtime.AppraisalObservationLedger;
import com.mineclawd.tensuradungeon.runtime.SoulOrbHaulData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class RewardCommandRegistrar {
    private RewardCommandRegistrar() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("rewards")
            .then(Commands.literal("debug").executes(RewardCommandRegistrar::debugRewards))
            .then(Commands.literal("tierdebug")
                .then(Commands.argument("floor", IntegerArgumentType.integer(1))
                    .executes(RewardCommandRegistrar::debugRewardTier)))
            .then(Commands.literal("sourcedebug").executes(RewardCommandRegistrar::debugRewardSource))
            .then(Commands.literal("appraisalobservations")
                .executes(context -> debugAppraisalObservations(context, 10))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 20))
                    .executes(context -> debugAppraisalObservations(
                        context, IntegerArgumentType.getInteger(context, "limit")
                    ))))
            .then(Commands.literal("claim").executes(RewardCommandRegistrar::claimReward))
            .then(Commands.literal("claimall").executes(RewardCommandRegistrar::claimAllRewards))
            .then(Commands.literal("floorbonusdebug").executes(RewardCommandRegistrar::debugFloorBonusRewards))
            .then(Commands.literal("clearfloorbonuses").executes(RewardCommandRegistrar::clearFloorBonusRewards))
            .then(Commands.literal("clear").executes(RewardCommandRegistrar::clearRewards)));
    }

    static int debugAppraisalObservations(CommandContext<CommandSourceStack> context, int limit) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        AppraisalObservationLedger.Snapshot snapshot = SoulOrbHaulData.get(player.serverLevel())
            .appraisalObservations(limit);
        source.sendSuccess(() -> Component.literal("Appraisal Observations - OBSERVATION ONLY"), false);
        source.sendSuccess(() -> Component.literal("Selling Enabled: false"), false);
        source.sendSuccess(() -> Component.literal(
            "Appraised Hauls: " + snapshot.appraisedHauls()
                + " | Stacks: " + snapshot.observedStacks()
                + " | Items: " + snapshot.observedItems()
                + " | Profiles: " + snapshot.distinctProfiles()
        ), false);
        source.sendSuccess(() -> Component.literal(
            "Overflow Stacks: " + snapshot.overflowStacks() + " | Overflow Items: " + snapshot.overflowItems()
        ), false);
        for (int index = 0; index < snapshot.profiles().size(); index++) {
            AppraisalObservationLedger.ProfileSnapshot profile = snapshot.profiles().get(index);
            String durability = profile.damageable()
                ? (profile.maximumDamage() - profile.damage()) + "/" + profile.maximumDamage()
                : "N/A";
            int row = index + 1;
            source.sendSuccess(() -> Component.literal(
                row + ". " + profile.itemId()
                    + " | bracket " + profile.bracketStart() + "-" + profile.bracketEnd()
                    + " | floors " + profile.minimumFloor() + "-" + profile.maximumFloor()
                    + " | " + profile.encounterClass()
                    + " | stacks " + profile.stackObservations()
                    + " | quantity " + profile.totalQuantity()
                    + " | durability " + durability
                    + " | enchanted " + profile.enchanted()
                    + " | components " + profile.componentFingerprint()
            ), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    static int debugRewards(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        RewardMailboxData mailbox = RewardMailboxData.get(player.serverLevel());
        RewardMailboxData.RewardPackage latest = mailbox.getLatestPackage(player.getUUID());
        source.sendSuccess(() -> Component.literal("Reward Mailbox Debug"), false);
        source.sendSuccess(() -> Component.literal("Mailbox Package Count: " + mailbox.getMailbox(player.getUUID()).size()), false);
        source.sendSuccess(() -> Component.literal("Unclaimed Count: " + mailbox.getUnclaimedCount(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Claimed Count: " + mailbox.getClaimedCount(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Latest Package Id: " + (latest == null ? "NONE" : latest.rewardId())), false);
        source.sendSuccess(() -> Component.literal("Latest Source Type: " + (latest == null ? "NONE" : latest.sourceType())), false);
        source.sendSuccess(() -> Component.literal("Latest Floor Number: " + (latest == null ? "NONE" : latest.floorNumber())), false);
        source.sendSuccess(() -> Component.literal("Latest Reward Tier: " + (latest == null ? "NONE" : latest.rewardTier())), false);
        source.sendSuccess(() -> Component.literal("Latest Reward Categories: " + (latest == null ? "NONE" : latest.categorySummary())), false);
        source.sendSuccess(() -> Component.literal("Latest Room Profile: " + (latest == null ? "UNKNOWN" : latest.roomProfile())), false);
        source.sendSuccess(() -> Component.literal("Latest Objective: " + (latest == null ? "UNKNOWN" : latest.objective())), false);
        source.sendSuccess(() -> Component.literal("Latest Objective Selection Mode: " + (latest == null ? "UNKNOWN" : latest.objectiveSelectionMode())), false);
        source.sendSuccess(() -> Component.literal("Latest Modifier Id: " + (latest == null ? "NONE" : latest.modifierId())), false);
        source.sendSuccess(() -> Component.literal("Latest Run Session Id: " + (latest == null ? "UNKNOWN" : latest.runSessionId())), false);
        source.sendSuccess(() -> Component.literal("Latest Room Controller Pos: " + (latest == null ? "NONE" : latest.controllerPosSummary())), false);
        source.sendSuccess(() -> Component.literal("Latest Completion Reason: " + (latest == null ? "UNKNOWN" : latest.completionReason())), false);
        source.sendSuccess(() -> Component.literal("Latest Claimed State: " + (latest == null ? "NONE" : latest.claimed())), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Claim Type: " + (latest == null ? "NONE" : latest.tensuraClaimType())), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Max EP Claim Amount: " + (latest == null ? 0 : latest.tensuraMaxEpClaimAmount())), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Claim Reason: " + (latest == null ? "NONE" : latest.tensuraClaimReason())), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Claim Mutation Path: " + (latest == null ? "NONE" : latest.tensuraClaimMutationPath())), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Claim Status: " + (latest == null ? "NONE" : latest.tensuraClaimStatus())), false);
        source.sendSuccess(() -> Component.literal("Floor Max EP Awarded Floors: " + mailbox.getAwardedFloorCompletionFloorsSummary(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Latest Item Summary: " + (latest == null ? "NONE" : latest.itemSummary())), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugRewardTier(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int floor = IntegerArgumentType.getInteger(context, "floor");
        RewardMailboxData.RewardContents preview = RewardMailboxData.RewardContents.forFloor(floor);
        source.sendSuccess(() -> Component.literal("Reward Tier Preview"), false);
        source.sendSuccess(() -> Component.literal("Floor: " + floor), false);
        source.sendSuccess(() -> Component.literal("Tier: " + preview.tier()), false);
        source.sendSuccess(() -> Component.literal("Categories: " + (preview.categories().isEmpty() ? "NONE" : String.join(", ", preview.categories()))), false);
        source.sendSuccess(() -> Component.literal("Items: " + (preview.items().isEmpty() ? "NONE" : preview.items().stream().map(item -> item.itemId() + " x" + item.count()).reduce((left, right) -> left + ", " + right).orElse("NONE"))), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugRewardSource(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        RewardMailboxData mailbox = RewardMailboxData.get(player.serverLevel());
        RewardMailboxData.RewardPackage latest = mailbox.getLatestPackage(player.getUUID());
        source.sendSuccess(() -> Component.literal("Reward Source Debug"), false);
        if (latest == null) {
            source.sendSuccess(() -> Component.literal("Latest Package: NONE"), false);
            source.sendSuccess(() -> Component.literal("Floor Max EP Awarded Floors: " + mailbox.getAwardedFloorCompletionFloorsSummary(player.getUUID())), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(() -> Component.literal("Latest Package Id: " + latest.rewardId()), false);
        source.sendSuccess(() -> Component.literal("Latest Source Type: " + latest.sourceType()), false);
        source.sendSuccess(() -> Component.literal("Latest Floor Number: " + latest.floorNumber()), false);
        source.sendSuccess(() -> Component.literal("Latest Reward Tier: " + latest.rewardTier()), false);
        source.sendSuccess(() -> Component.literal("Latest Reward Categories: " + latest.categorySummary()), false);
        source.sendSuccess(() -> Component.literal("Latest Room Profile: " + latest.roomProfile()), false);
        source.sendSuccess(() -> Component.literal("Latest Objective: " + latest.objective()), false);
        source.sendSuccess(() -> Component.literal("Latest Objective Selection Mode: " + latest.objectiveSelectionMode()), false);
        source.sendSuccess(() -> Component.literal("Latest Modifier Id: " + latest.modifierId()), false);
        source.sendSuccess(() -> Component.literal("Latest Run Session Id: " + latest.runSessionId()), false);
        source.sendSuccess(() -> Component.literal("Latest Room Controller Pos: " + latest.controllerPosSummary()), false);
        source.sendSuccess(() -> Component.literal("Latest Source Pos: " + latest.sourcePosSummary()), false);
        source.sendSuccess(() -> Component.literal("Latest Completion Reason: " + latest.completionReason()), false);
        source.sendSuccess(() -> Component.literal("Latest Floor Completion Reason: " + latest.floorCompletionReason()), false);
        source.sendSuccess(() -> Component.literal("Tensura Preview Enabled: " + latest.tensuraPreviewEnabled()), false);
        source.sendSuccess(() -> Component.literal("EP Preview Amount: " + latest.epPreviewAmount()), false);
        source.sendSuccess(() -> Component.literal("EP Preview Status: " + latest.epPreviewStatus()), false);
        source.sendSuccess(() -> Component.literal("Magicule Preview Amount: " + latest.magiculePreviewAmount()), false);
        source.sendSuccess(() -> Component.literal("Magicule Preview Status: " + latest.magiculePreviewStatus()), false);
        source.sendSuccess(() -> Component.literal("Aura Preview Amount: " + latest.auraPreviewAmount()), false);
        source.sendSuccess(() -> Component.literal("Aura Preview Status: " + latest.auraPreviewStatus()), false);
        source.sendSuccess(() -> Component.literal("Battlewill Preview Amount: " + latest.battlewillPreviewAmount()), false);
        source.sendSuccess(() -> Component.literal("Battlewill Preview Status: " + latest.battlewillPreviewStatus()), false);
        source.sendSuccess(() -> Component.literal("Skill Preview Candidate: " + latest.skillPreviewCandidateId()), false);
        source.sendSuccess(() -> Component.literal("Skill Preview Status: " + latest.skillPreviewStatus()), false);
        source.sendSuccess(() -> Component.literal("Preview Source: " + latest.previewSource()), false);
        source.sendSuccess(() -> Component.literal("Tensura Safe Fallback Mode At Preview: " + latest.tensuraSafeFallbackModeAtPreview()), false);
        source.sendSuccess(() -> Component.literal("Latest Created Game Time: " + latest.createdGameTime()), false);
        source.sendSuccess(() -> Component.literal("Latest Claimed State: " + latest.claimed()), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Claim Type: " + latest.tensuraClaimType()), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Max EP Claim Amount: " + latest.tensuraMaxEpClaimAmount()), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Claim Reason: " + latest.tensuraClaimReason()), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Claim Mutation Path: " + latest.tensuraClaimMutationPath()), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Claim Status: " + latest.tensuraClaimStatus()), false);
        source.sendSuccess(() -> Component.literal("Latest Tensura Claim Summary: " + latest.tensuraClaimSummary()), false);
        source.sendSuccess(() -> Component.literal("Floor Max EP Awarded Floors: " + mailbox.getAwardedFloorCompletionFloorsSummary(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Latest Item Summary: " + latest.itemSummary()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugFloorBonusRewards(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        RewardMailboxData mailbox = RewardMailboxData.get(player.serverLevel());
        List<RewardMailboxData.RewardPackage> floorPackages = mailbox.getMailbox(player.getUUID()).stream()
            .filter(pkg -> "FLOOR_COMPLETION".equals(pkg.sourceType())).toList();
        source.sendSuccess(() -> Component.literal("Floor Bonus Debug"), false);
        source.sendSuccess(() -> Component.literal("Floor Bonus Package Count: " + floorPackages.size()), false);
        source.sendSuccess(() -> Component.literal("Floor Max EP Awarded Floors: " + mailbox.getAwardedFloorCompletionFloorsSummary(player.getUUID())), false);
        RewardMailboxData.RewardPackage latest = floorPackages.isEmpty() ? null : floorPackages.get(floorPackages.size() - 1);
        source.sendSuccess(() -> Component.literal("Latest Floor Bonus Package Id: " + (latest == null ? "NONE" : latest.rewardId())), false);
        source.sendSuccess(() -> Component.literal("Latest Floor Bonus Claim Summary: " + (latest == null ? "NONE" : latest.tensuraClaimSummary())), false);
        return Command.SINGLE_SUCCESS;
    }

    static int claimReward(CommandContext<CommandSourceStack> context) { return claim(context, false); }
    static int claimAllRewards(CommandContext<CommandSourceStack> context) { return claim(context, true); }

    private static int claim(CommandContext<CommandSourceStack> context, boolean all) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        RewardMailboxData mailbox = RewardMailboxData.get(player.serverLevel());
        RewardMailboxData.ClaimResult result = all ? mailbox.claimAll(player) : mailbox.claimOldest(player);
        source.sendSuccess(() -> Component.literal(result.message()), false);
        if (result.tensuraMaxEpApplied() > 0 || !"NONE".equals(result.tensuraStatus()) || !"NONE".equals(result.tensuraError())) {
            source.sendSuccess(() -> Component.literal("Tensura Max EP Applied: " + result.tensuraMaxEpApplied()), false);
            source.sendSuccess(() -> Component.literal("Tensura Status: " + result.tensuraStatus()), false);
            source.sendSuccess(() -> Component.literal("Mutation Path: " + result.tensuraMutationPath()), false);
            source.sendSuccess(() -> Component.literal("Tensura Error: " + result.tensuraError()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    static int clearFloorBonusRewards(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        int cleared = RewardMailboxData.get(player.serverLevel()).clearFloorCompletionAwards(player.getUUID());
        source.sendSuccess(() -> Component.literal("Cleared floor max EP award state for " + cleared + " floor(s)."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int clearRewards(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) return 0;
        int removed = RewardMailboxData.get(player.serverLevel()).clearMailbox(player.getUUID());
        source.sendSuccess(() -> Component.literal("Cleared " + removed + " reward package(s)."), false);
        return Command.SINGLE_SUCCESS;
    }
}
