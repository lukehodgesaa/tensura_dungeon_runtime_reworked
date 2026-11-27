package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierCatalog;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.PreparedFloorModifierPlan;
import com.mineclawd.tensuradungeon.runtime.RewardMailboxData;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinCatalog;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

final class DungeonUiSnapshotFactory {
    private static final List<String> KIN_ROLE_LANES = List.of(
        "MELEE", "RANGED", "FLANK", "HIGHGROUND", "ELITE_CANDIDATE"
    );
    private static final List<String> KIN_EXCLUSIONS = List.of(
        "SWARM", "SUMMONER", "RIFTBOUND", "BOSS_ONLY", "LATE_BOSS_ONLY",
        "PEGASUS_PEACEFUL_OR_NEUTRAL", "ORDINARY_TO_BOSS_PROMOTION"
    );

    private DungeonUiSnapshotFactory() {}

    static DungeonUiSnapshotPayload create(ServerPlayer player, String message) {
        ServerLevel level = player.serverLevel();
        DungeonRunManager run = DungeonRunManager.get(level);
        DungeonFloorProgressData progress = DungeonFloorProgressData.get(level);
        RewardMailboxData mailbox = RewardMailboxData.get(level);

        List<String> modifierIds = run.getActiveModifiers().stream()
            .map(definition -> definition.getId().toString())
            .sorted()
            .toList();
        DungeonUiSnapshotPayload.RunSummary runSummary = new DungeonUiSnapshotPayload.RunSummary(
            run.isActiveRun(), run.isRunCompleted(), run.isFailed(), run.getFloorNumber(),
            run.getRunSessionId(), run.getRunSeed(), run.getCompletedRoomCount(),
            run.getTargetRoomCount(), run.getFloorModeLabel(), modifierIds
        );
        DungeonUiSnapshotPayload.ProgressSummary progressSummary =
            new DungeonUiSnapshotPayload.ProgressSummary(
                progress.getHighestUnlockedFloor(player.getUUID()),
                progress.getSelectedFloor(player.getUUID()),
                progress.getCompletedFloors(player.getUUID())
            );
        PreparedFloorModifierPlan preparedPlan = progress.getOrPrepareModifierPlan(
            player.getUUID(), progressSummary.selectedFloor()
        );
        DungeonUiSnapshotPayload.PreparedModifierPlanSummary preparedPlanSummary =
            new DungeonUiSnapshotPayload.PreparedModifierPlanSummary(
                preparedPlan.floor(),
                preparedPlan.seed(),
                preparedPlan.algorithmVersion(),
                preparedPlan.catalogVersion(),
                preparedPlan.hostileModifierIds(),
                preparedPlan.boonModifierId(),
                preparedPlan.reason(),
                preparedPlan.validationStatus(),
                preparedPlan.validationReason(),
                preparedPlan.roomPressureId(),
                kinPreview(preparedPlan),
                new DungeonUiSnapshotPayload.BossPreview(
                    preparedPlan.bossSlotAvailable(),
                    preparedPlan.bossMilestone(),
                    preparedPlan.bossCatalogVersion(),
                    preparedPlan.bossAffinityKinGroup(),
                    preparedPlan.bossAffinityTheme(),
                    preparedPlan.bossCandidateIds(),
                    preparedPlan.bossMatchingCandidateIds(),
                    preparedPlan.selectedBossId(),
                    preparedPlan.bossResolutionReason()
                )
            );
        List<DungeonUiSnapshotPayload.ModifierCatalogEntry> modifierCatalog =
            DungeonModifierCatalog.entries().stream()
                .map(entry -> {
                    String description = modifierDescription(entry, preparedPlan);
                    return new DungeonUiSnapshotPayload.ModifierCatalogEntry(
                        entry.id(),
                        entry.displayName(),
                        entry.alignment(),
                        entry.classification(),
                        entry.status(),
                        entry.minFloor(),
                        entry.weight(),
                        description,
                        entry.counters(),
                        entry.rewardMultiplier(),
                        entry.iconKey(),
                        entry.nativeSkillId(),
                        entry.wardCategory(),
                        entry.availability().name(),
                        entry.availabilityReason()
                    );
                })
                .toList();
        DungeonFloorProgressData.CompletedRunSummary latestRun =
            progress.getLatestCompletedRun(player.getUUID());
        DungeonUiSnapshotPayload.CompletionSummary completionSummary =
            completionSummary(latestRun);
        List<DungeonUiSnapshotPayload.LocalFloorRecord> localFloorRecords =
            progress.getLocalRecords(progressSummary.selectedFloor()).stream()
                .map(record -> new DungeonUiSnapshotPayload.LocalFloorRecord(
                    record.playerId().toString(),
                    record.playerName(),
                    record.floor(),
                    record.clearTicks(),
                    record.clearRank()
                ))
                .toList();
        List<RewardMailboxData.RewardPackage> unclaimedPackages = mailbox.getMailbox(player.getUUID())
            .stream()
            .filter(reward -> !reward.claimed())
            .sorted(Comparator.comparingLong(RewardMailboxData.RewardPackage::rewardId).reversed())
            .toList();
        List<DungeonUiSnapshotPayload.MailboxPackage> packages = unclaimedPackages.stream()
            .map(reward -> new DungeonUiSnapshotPayload.MailboxPackage(
                reward.rewardId(), reward.floorNumber(), reward.claimed(), reward.sourceType(),
                reward.rewardTier(), reward.itemSummary(), reward.tensuraMaxEpClaimAmount()
            ))
            .toList();
        DungeonUiSnapshotPayload.IntegrationSummary integrations =
            new DungeonUiSnapshotPayload.IntegrationSummary(
                completionSummary.available()
                    ? completionSummary.performance().epStatus()
                    : "UNAVAILABLE_NOT_TRACKED",
                "NOT_IMPLEMENTED",
                "NOT_IMPLEMENTED"
            );
        DungeonUiSnapshotPayload.CurrencySummary currency =
            new DungeonUiSnapshotPayload.CurrencySummary(
                countItem(player, "tensura:bronze_coin"),
                countItem(player, "tensura:silver_coin"),
                countItem(player, "tensura:gold_coin"),
                countItem(player, "tensura:stellar_gold_coin")
            );
        long revision = revision(
            runSummary, progressSummary, integrations, currency, completionSummary,
            localFloorRecords, packages, modifierCatalog, preparedPlanSummary
        );
        return new DungeonUiSnapshotPayload(
            DungeonUiSnapshotPayload.CURRENT_SCHEMA,
            revision,
            runSummary,
            progressSummary,
            integrations,
            currency,
            completionSummary,
            localFloorRecords,
            unclaimedPackages.size(),
            packages,
            DungeonModifierCatalog.CATALOG_VERSION,
            modifierCatalog,
            preparedPlanSummary,
            message
        );
    }

    private static String modifierDescription(
        DungeonModifierCatalog.Entry entry,
        PreparedFloorModifierPlan preparedPlan
    ) {
        if (DungeonEnemyKinCatalog.isKinModifierId(entry.id())) {
            String family = DungeonEnemyKinCatalog.groupForModifierId(entry.id());
            if (family.equals(preparedPlan.kinGroup()) && !preparedPlan.kinCandidateIds().isEmpty()) {
                List<String> candidates = preparedPlan.kinCandidateIds().stream()
                    .map(DungeonEnemyKinCatalog::displayNameForEntity)
                    .distinct()
                    .limit(8)
                    .toList();
                return boundedDescription(
                    "Family: " + DungeonEnemyKinCatalog.displayNameForGroup(family)
                        + " - Eligible this floor: " + String.join(", ", candidates),
                    256
                );
            }
            return entry.description();
        }
        if ("kin".equals(entry.id()) && !preparedPlan.kinGroup().isBlank()) {
            return "Legacy generic Kin card. The prepared floor uses the named "
                + kinFamilyDisplayName(preparedPlan.kinGroup()) + " family card.";
        }
        if ("boss-affinity".equals(entry.id()) && !preparedPlan.selectedBossId().isBlank()) {
            String reason = preparedPlan.bossResolutionReason();
            String suffix = "AFFINITY_NO_MATCH_NEUTRAL_FALLBACK".equals(reason)
                ? " No authored family match was available; a neutral boss was selected."
                : "";
            return boundedDescription(
                "Selected authored boss: "
                    + DungeonEnemyKinCatalog.displayNameForEntity(preparedPlan.selectedBossId())
                    + "." + suffix,
                256
            );
        }
        return entry.description();
    }

    private static String boundedDescription(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static DungeonUiSnapshotPayload.KinPreview kinPreview(PreparedFloorModifierPlan plan) {
        boolean present = !plan.kinGroup().isBlank()
            || !plan.kinCandidateIds().isEmpty()
            || !plan.kinRejectionReasons().isEmpty();
        return new DungeonUiSnapshotPayload.KinPreview(
            plan.kinGroup(),
            plan.kinSeed(),
            plan.kinCatalogVersion(),
            present ? KIN_ROLE_LANES : List.of(),
            plan.kinCandidateIds(),
            plan.kinRejectionReasons(),
            present ? KIN_EXCLUSIONS : List.of()
        );
    }

    private static String kinFamilyDisplayName(String kinGroup) {
        if (kinGroup == null || kinGroup.isBlank()) {
            return "";
        }
        return DungeonEnemyKinCatalog.displayNameForGroup(kinGroup);
    }

    private static DungeonUiSnapshotPayload.CompletionSummary completionSummary(
        DungeonFloorProgressData.CompletedRunSummary summary
    ) {
        boolean available = summary.context().floor() > 0;
        DungeonFloorProgressData.RunContext context = summary.context();
        DungeonFloorProgressData.RunPerformance performance = summary.performance();
        DungeonFloorProgressData.RunRewards rewards = summary.rewards();
        return new DungeonUiSnapshotPayload.CompletionSummary(
            available,
            summary.playerName(),
            new DungeonUiSnapshotPayload.CompletionContext(
                context.sessionId(),
                context.floor(),
                context.mode(),
                context.objective(),
                context.roomCount(),
                context.partySize(),
                context.partyComposition(),
                context.modifierIds()
            ),
            new DungeonUiSnapshotPayload.CompletionPerformance(
                performance.clearTicks(),
                performance.timingStatus(),
                performance.result(),
                performance.clearRank(),
                performance.rankStatus(),
                performance.deathsStatus(),
                performance.checkpointStatus(),
                performance.entryEp(),
                performance.currentEp(),
                performance.epGain(),
                performance.epStatus()
            ),
            new DungeonUiSnapshotPayload.CompletionRewards(
                rewards.baseCurrencyBronze(),
                rewards.bonusCurrencyBronze(),
                rewards.totalCurrencyBronze(),
                rewards.commonDrops(),
                rewards.uncommonDrops(),
                rewards.rareDrops(),
                rewards.epicDrops(),
                rewards.crystalCount()
            )
        );
    }

    static int countItem(ServerPlayer player, String itemId) {
        return player.getInventory().countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId)));
    }

    private static long revision(Object... values) {
        long revision = 1_125_899_906_842_597L;
        for (Object value : values) revision = revision * 31L + value.hashCode();
        return revision & Long.MAX_VALUE;
    }
}
