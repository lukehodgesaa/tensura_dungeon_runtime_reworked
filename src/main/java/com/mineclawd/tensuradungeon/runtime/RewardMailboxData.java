package com.mineclawd.tensuradungeon.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraRewardPreviewSource;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraMaxEpMutator;
import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import com.mineclawd.tensuradungeon.reward.RewardClaimService;
import com.mineclawd.tensuradungeon.reward.RewardCatalog;
import com.mineclawd.tensuradungeon.reward.RewardPreviewSource;
import com.mineclawd.tensuradungeon.reward.VanillaInventoryGrant;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

public class RewardMailboxData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_reward_mailbox";
    private static final int FLOOR_COMPLETION_EP_INTERVAL = 10;
    private static final int FLOOR_COMPLETION_EP_AMOUNT = 1;
    private static final String TENSURA_CLAIM_TYPE_NONE = "NONE";
    private static final String TENSURA_CLAIM_TYPE_MAX_EP = "MAX_EP";
    private static final String TENSURA_CLAIM_REASON_NONE = "NONE";
    private static final String TENSURA_CLAIM_REASON_FIRST_TIME_FLOOR_COMPLETION = "FIRST_TIME_FLOOR_COMPLETION";
    private static final String TENSURA_CLAIM_STATUS_NONE = "NONE";
    private static final String TENSURA_CLAIM_STATUS_UNCLAIMED = "UNCLAIMED";
    private static final String TENSURA_MUTATION_PATH_NONE = "NONE";
    public static final String CORE_SATCHEL_WELCOME_SOURCE = "DUNGEON_SHOP_WELCOME";
    public static final String CORE_SATCHEL_WELCOME_TIER = "CORE_SATCHEL_WELCOME";
    public static final String CORE_SATCHEL_WELCOME_CATEGORY = "core_satchel_unlock";
    private final Map<UUID, List<RewardPackage>> mailboxes = new HashMap<>();
    private final Map<UUID, Set<Integer>> awardedFloorCompletionEpFloors = new HashMap<>();
    private final RewardCatalog rewardCatalog;
    private final RewardPreviewSource rewardPreviewSource;
    private final RewardClaimService rewardClaimService;
    private long nextRewardId = 1L;

    public RewardMailboxData() {
        this(new RewardCatalog(), TensuraRewardPreviewSource.INSTANCE,
            new RewardClaimService(VanillaInventoryGrant.INSTANCE, TensuraMaxEpMutator.INSTANCE));
    }

    public RewardMailboxData(RewardCatalog rewardCatalog, RewardPreviewSource rewardPreviewSource) {
        this(rewardCatalog, rewardPreviewSource,
            new RewardClaimService(VanillaInventoryGrant.INSTANCE, TensuraMaxEpMutator.INSTANCE));
    }

    public RewardMailboxData(RewardCatalog rewardCatalog, RewardPreviewSource rewardPreviewSource, RewardClaimService rewardClaimService) {
        this.rewardCatalog = rewardCatalog;
        this.rewardPreviewSource = rewardPreviewSource;
        this.rewardClaimService = rewardClaimService;
    }

    public static RewardMailboxData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(RewardMailboxData::new, RewardMailboxData::load), DATA_NAME);
    }

    public static RewardMailboxData load(CompoundTag tag, HolderLookup.Provider registries) {
        RewardMailboxData data = new RewardMailboxData();
        data.nextRewardId = tag.contains("NextRewardId") ? Math.max(1L, tag.getLong("NextRewardId")) : 1L;
        ListTag mailboxesTag = tag.getList("Mailboxes", Tag.TAG_COMPOUND);
        for (int i = 0; i < mailboxesTag.size(); i++) {
            CompoundTag mailboxTag = mailboxesTag.getCompound(i);
            if (!mailboxTag.hasUUID("Player")) {
                continue;
            }
            UUID playerId = mailboxTag.getUUID("Player");
            List<RewardPackage> rewards = new ArrayList<>();
            ListTag rewardsTag = mailboxTag.getList("Rewards", Tag.TAG_COMPOUND);
            for (int j = 0; j < rewardsTag.size(); j++) {
                rewards.add(RewardPackage.load(rewardsTag.getCompound(j)));
            }
            rewards.sort(Comparator.comparingLong(RewardPackage::rewardId));
            data.mailboxes.put(playerId, rewards);
        }
        ListTag awardedFloorsTag = tag.getList("AwardedFloorCompletionEpFloors", Tag.TAG_COMPOUND);
        for (int i = 0; i < awardedFloorsTag.size(); i++) {
            CompoundTag awardedTag = awardedFloorsTag.getCompound(i);
            if (!awardedTag.hasUUID("Player")) {
                continue;
            }
            UUID playerId = awardedTag.getUUID("Player");
            Set<Integer> floors = new LinkedHashSet<>();
            for (int floor : awardedTag.getIntArray("Floors")) {
                if (floor > 0) {
                    floors.add(floor);
                }
            }
            if (!floors.isEmpty()) {
                data.awardedFloorCompletionEpFloors.put(playerId, floors);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextRewardId", nextRewardId);
        ListTag mailboxesTag = new ListTag();
        for (Map.Entry<UUID, List<RewardPackage>> entry : mailboxes.entrySet()) {
            CompoundTag mailboxTag = new CompoundTag();
            mailboxTag.putUUID("Player", entry.getKey());
            ListTag rewardsTag = new ListTag();
            for (RewardPackage rewardPackage : entry.getValue()) {
                rewardsTag.add(rewardPackage.save());
            }
            mailboxTag.put("Rewards", rewardsTag);
            mailboxesTag.add(mailboxTag);
        }
        tag.put("Mailboxes", mailboxesTag);
        ListTag awardedFloorsTag = new ListTag();
        for (Map.Entry<UUID, Set<Integer>> entry : awardedFloorCompletionEpFloors.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag awardedTag = new CompoundTag();
            awardedTag.putUUID("Player", entry.getKey());
            awardedTag.putIntArray("Floors", entry.getValue().stream().mapToInt(Integer::intValue).sorted().toArray());
            awardedFloorsTag.add(awardedTag);
        }
        tag.put("AwardedFloorCompletionEpFloors", awardedFloorsTag);
        return tag;
    }

    public RewardPackage createRoomReward(ServerLevel level, UUID playerId, RewardSourceMetadata sourceMetadata, long gameTime) {
        RewardSourceMetadata metadata = sourceMetadata == null ? RewardSourceMetadata.unknown() : sourceMetadata.normalized();
        RewardContents rewardContents = RewardContents.from(rewardCatalog.contentsForFloor(metadata.floorNumber()));
        RewardPreviewSource.RewardPreview tensuraPreview = buildTensuraRewardPreview(level, playerId, metadata.floorNumber(), rewardContents.tier(), metadata.sourceType());
        RewardPackage rewardPackage = new RewardPackage(
            new RewardIdentity(nextRewardId++, playerId, gameTime),
            metadata,
            rewardContents,
            new StoredRewardPreview(tensuraPreview, gameTime),
            RewardClaimState.none(),
            DungeonCurrencyAward.none()
        );
        mailboxes.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(rewardPackage);
        setDirty();
        return rewardPackage;
    }

    public FloorCompletionEpRewardResult createFirstTimeFloorCompletionEpReward(ServerLevel level, UUID playerId, RewardSourceMetadata sourceMetadata, long gameTime) {
        RewardSourceMetadata metadata = sourceMetadata == null ? RewardSourceMetadata.unknown() : sourceMetadata.normalized();
        int floorNumber = Math.max(1, metadata.floorNumber());
        if (!isFloorEligibleForCompletionEp(floorNumber)) {
            return new FloorCompletionEpRewardResult("FLOOR_NOT_ELIGIBLE", null, getAwardedFloorCompletionFloors(playerId));
        }
        Set<Integer> awardedFloors = awardedFloorCompletionEpFloors.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>());
        if (awardedFloors.contains(floorNumber)) {
            return new FloorCompletionEpRewardResult("ALREADY_AWARDED", null, List.copyOf(awardedFloors.stream().sorted().toList()));
        }
        RewardPackage rewardPackage = new RewardPackage(
            new RewardIdentity(nextRewardId++, playerId, gameTime),
            metadata,
            new RewardContents(
                "FLOOR_COMPLETION_TENSURA_EP",
                List.of("tensura_floor_completion_max_ep"),
                List.of()
            ),
            new StoredRewardPreview(
                new RewardPreviewSource.RewardPreview(
                    false, 0, "NONE", 0, "NONE", 0, "NONE", 0, "NONE",
                    "NONE", "NONE", "FLOOR_COMPLETION_MAX_EP_MVP", true
                ),
                gameTime
            ),
            new RewardClaimState(
                false,
                FLOOR_COMPLETION_EP_AMOUNT,
                TENSURA_CLAIM_TYPE_MAX_EP,
                TENSURA_CLAIM_REASON_FIRST_TIME_FLOOR_COMPLETION,
                TENSURA_CLAIM_STATUS_UNCLAIMED,
                "ATTRIBUTE_BASE_SPLIT_SYNC"
            ),
            DungeonCurrencyAward.none()
        );
        mailboxes.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(rewardPackage);
        awardedFloors.add(floorNumber);
        setDirty();
        return new FloorCompletionEpRewardResult("CREATED", rewardPackage, List.copyOf(awardedFloors.stream().sorted().toList()));
    }

    public FloorCompletionCurrencyRewardResult createSuccessfulFloorCompletionCurrencyReward(
        UUID playerId,
        RewardSourceMetadata sourceMetadata,
        DungeonCurrencyPolicy.ClearRank clearRank,
        int modifierBonusBasisPoints,
        long gameTime
    ) {
        if (playerId == null) {
            throw new IllegalArgumentException("Currency reward player is required");
        }
        RewardSourceMetadata metadata = sourceMetadata == null ? RewardSourceMetadata.unknown() : sourceMetadata.normalized();
        String runSessionId = metadata.runSessionId();
        if ("UNKNOWN_RUN".equals(runSessionId)) {
            throw new IllegalArgumentException("Currency reward requires an authoritative run session ID");
        }
        if (hasCurrencyRewardForSession(playerId, runSessionId)) {
            return new FloorCompletionCurrencyRewardResult("ALREADY_AWARDED", null, runSessionId);
        }

        DungeonCurrencyPolicy.CompletionAward calculated = DungeonCurrencyPolicy.completionAward(
            metadata.floorNumber(), clearRank, modifierBonusBasisPoints
        );
        DungeonCurrencyAward currencyAward = DungeonCurrencyAward.from(calculated);
        RewardPackage rewardPackage = new RewardPackage(
            new RewardIdentity(nextRewardId++, playerId, gameTime),
            metadata,
            new RewardContents(
                "FLOOR_COMPLETION_CURRENCY",
                List.of("dungeon_completion_currency"),
                currencyEntries(calculated.payout())
            ),
            new StoredRewardPreview(
                new RewardPreviewSource.RewardPreview(
                    false, 0, "NONE", 0, "NONE", 0, "NONE", 0, "NONE",
                    "NONE", "NONE", "DUNGEON_COMPLETION_CURRENCY", true
                ),
                gameTime
            ),
            RewardClaimState.none(),
            currencyAward
        );
        mailboxes.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(rewardPackage);
        setDirty();
        return new FloorCompletionCurrencyRewardResult("CREATED", rewardPackage, runSessionId);
    }

    public ForgeMaterialRewardResult createSuccessfulFloorCompletionForgeMaterialReward(
        UUID playerId,
        RewardSourceMetadata sourceMetadata,
        ForgeMaterialRecoveryPolicy.FloorSource floorSource,
        boolean firstClear,
        long runSeed,
        long gameTime
    ) {
        if (playerId == null) {
            throw new IllegalArgumentException("Forge-material reward player is required");
        }
        RewardSourceMetadata metadata = sourceMetadata == null ? RewardSourceMetadata.unknown() : sourceMetadata.normalized();
        String runSessionId = metadata.runSessionId();
        if ("UNKNOWN_RUN".equals(runSessionId)) {
            throw new IllegalArgumentException("Forge-material reward requires an authoritative run session ID");
        }
        RewardPackage existing = findForgeMaterialReceiptForSession(playerId, runSessionId);
        if (existing != null) {
            return new ForgeMaterialRewardResult("ALREADY_ROLLED", existing, null);
        }

        ForgeMaterialRecoveryPolicy.RecoveryRoll roll = ForgeMaterialRecoveryPolicy.roll(
            new ForgeMaterialRecoveryPolicy.RecoveryRequest(
                runSessionId,
                metadata.floorNumber(),
                floorSource,
                firstClear,
                runSeed
            )
        );
        List<RewardEntry> items = roll.awards().stream()
            .map(award -> new RewardEntry(award.itemId(), award.count()))
            .toList();
        RewardPackage rewardPackage = new RewardPackage(
            new RewardIdentity(nextRewardId++, playerId, gameTime),
            metadata,
            new RewardContents(
                roll.recovered()
                    ? "FLOOR_COMPLETION_FORGE_MATERIAL"
                    : "FLOOR_COMPLETION_FORGE_MATERIAL_MISS",
                List.of(roll.recovered()
                    ? "forge_material_floor_reward"
                    : "forge_material_roll_receipt"),
                items
            ),
            new StoredRewardPreview(
                new RewardPreviewSource.RewardPreview(
                    false, 0, "NONE", 0, "NONE", 0, "NONE", 0, "NONE",
                    "NONE", "NONE", "FLOOR_COMPLETION_FORGE_MATERIAL", true
                ),
                gameTime
            ),
            roll.recovered() ? RewardClaimState.none() : RewardClaimState.none().withClaimed(true),
            DungeonCurrencyAward.none()
        );
        mailboxes.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(rewardPackage);
        setDirty();
        return new ForgeMaterialRewardResult(
            roll.recovered() ? "CREATED_REWARD" : "CREATED_MISS_RECEIPT",
            rewardPackage,
            roll
        );
    }

    public CoreSatchelWelcomeResult createCoreSatchelWelcome(UUID playerId, long gameTime) {
        if (playerId == null) {
            throw new IllegalArgumentException("Core Satchel welcome player is required");
        }
        RewardPackage existing = mailboxes.getOrDefault(playerId, List.of()).stream()
            .filter(RewardPackage::isCoreSatchelWelcome)
            .findFirst()
            .orElse(null);
        if (existing != null) {
            return new CoreSatchelWelcomeResult("ALREADY_SENT", existing);
        }

        RewardPackage rewardPackage = new RewardPackage(
            new RewardIdentity(nextRewardId++, playerId, gameTime),
            new RewardSourceMetadata(
                CORE_SATCHEL_WELCOME_SOURCE,
                1,
                null,
                "DUNGEON_SHOP",
                "CORE_SATCHEL_TUTORIAL",
                "PLAYER_ACKNOWLEDGEMENT",
                "NONE",
                "WELCOME",
                null,
                "TUTORIAL_ACKNOWLEDGED",
                "NOT_APPLICABLE"
            ),
            new RewardContents(
                CORE_SATCHEL_WELCOME_TIER,
                List.of(CORE_SATCHEL_WELCOME_CATEGORY),
                List.of()
            ),
            new StoredRewardPreview(
                new RewardPreviewSource.RewardPreview(
                    false, 0, "NONE", 0, "NONE", 0, "NONE", 0, "NONE",
                    "NONE", "NONE", CORE_SATCHEL_WELCOME_SOURCE, true
                ),
                gameTime
            ),
            RewardClaimState.none(),
            DungeonCurrencyAward.none()
        );
        mailboxes.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(rewardPackage);
        setDirty();
        return new CoreSatchelWelcomeResult("CREATED", rewardPackage);
    }

    private boolean hasCurrencyRewardForSession(UUID playerId, String runSessionId) {
        return mailboxes.getOrDefault(playerId, List.of()).stream().anyMatch(reward ->
            "FLOOR_COMPLETION_CURRENCY".equals(reward.rewardTier())
                && runSessionId.equals(reward.runSessionId())
                && reward.currencyAward().active()
        );
    }

    private RewardPackage findForgeMaterialReceiptForSession(UUID playerId, String runSessionId) {
        return mailboxes.getOrDefault(playerId, List.of()).stream()
            .filter(reward -> reward.rewardTier().startsWith("FLOOR_COMPLETION_FORGE_MATERIAL"))
            .filter(reward -> runSessionId.equals(reward.runSessionId()))
            .findFirst()
            .orElse(null);
    }

    private static List<RewardEntry> currencyEntries(DungeonCurrencyPolicy.SessionPayout payout) {
        List<RewardEntry> entries = new ArrayList<>(3);
        if (payout.goldCoins() > 0) {
            entries.add(new RewardEntry(DungeonCurrencyPolicy.GOLD_COIN_ITEM_ID, payout.goldCoins()));
        }
        if (payout.silverCoins() > 0) {
            entries.add(new RewardEntry(DungeonCurrencyPolicy.SILVER_COIN_ITEM_ID, payout.silverCoins()));
        }
        if (payout.bronzeCoins() > 0) {
            entries.add(new RewardEntry(DungeonCurrencyPolicy.BRONZE_COIN_ITEM_ID, payout.bronzeCoins()));
        }
        return List.copyOf(entries);
    }

    public List<RewardPackage> getMailbox(UUID playerId) {
        return List.copyOf(mailboxes.getOrDefault(playerId, List.of()));
    }

    public int clearMailbox(UUID playerId) {
        List<RewardPackage> removed = mailboxes.remove(playerId);
        if (removed != null && !removed.isEmpty()) {
            setDirty();
            return removed.size();
        }
        return 0;
    }

    public int clearFloorCompletionAwards(UUID playerId) {
        Set<Integer> removed = awardedFloorCompletionEpFloors.remove(playerId);
        if (removed != null && !removed.isEmpty()) {
            setDirty();
            return removed.size();
        }
        return 0;
    }

    public List<Integer> getAwardedFloorCompletionFloors(UUID playerId) {
        Set<Integer> floors = awardedFloorCompletionEpFloors.get(playerId);
        if (floors == null || floors.isEmpty()) {
            return List.of();
        }
        return List.copyOf(floors.stream().sorted().toList());
    }

    public String getAwardedFloorCompletionFloorsSummary(UUID playerId) {
        List<Integer> floors = getAwardedFloorCompletionFloors(playerId);
        if (floors.isEmpty()) {
            return "NONE";
        }
        return floors.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    public ClaimResult claimOldest(Player player) {
        List<RewardPackage> mailbox = mailboxes.get(player.getUUID());
        if (mailbox == null || mailbox.isEmpty()) {
            return ClaimResult.messageOnly("No reward packages found.", 0);
        }
        for (int i = 0; i < mailbox.size(); i++) {
            RewardPackage rewardPackage = mailbox.get(i);
            if (rewardPackage.claimed()) {
                continue;
            }
            return claimSingleReward(player, mailbox, i, rewardPackage, 0);
        }
        return ClaimResult.messageOnly("No unclaimed reward packages found.", 0);
    }

    public ClaimResult claimAll(Player player) {
        List<RewardPackage> mailbox = mailboxes.get(player.getUUID());
        if (mailbox == null || mailbox.isEmpty()) {
            return ClaimResult.messageOnly("No reward packages found.", 0);
        }
        int claimed = 0;
        ClaimResult lastTensuraResult = ClaimResult.empty(getUnclaimedCount(player.getUUID()));
        for (int i = 0; i < mailbox.size(); i++) {
            RewardPackage rewardPackage = mailbox.get(i);
            if (rewardPackage.claimed()) {
                continue;
            }
            ClaimResult result = claimSingleReward(player, mailbox, i, rewardPackage, claimed);
            if (result.claimedCount() == claimed) {
                String message = claimed > 0 && result.message().startsWith("Inventory full")
                    ? "Claimed " + claimed + " reward package(s). Inventory full. Remaining rewards stayed in mailbox."
                    : result.message();
                return result.withMessage(message);
            }
            claimed = result.claimedCount();
            if (result.tensuraMaxEpApplied() > 0 || !TENSURA_CLAIM_STATUS_NONE.equals(result.tensuraStatus()) || !"NONE".equals(result.tensuraError())) {
                lastTensuraResult = result;
            }
        }
        if (claimed > 0) {
            return new ClaimResult(claimed, getUnclaimedCount(player.getUUID()), "Claimed " + claimed + " reward package(s).", lastTensuraResult.tensuraMaxEpApplied(), lastTensuraResult.tensuraStatus(), lastTensuraResult.tensuraMutationPath(), lastTensuraResult.tensuraError());
        }
        return ClaimResult.messageOnly("No unclaimed reward packages found.", 0);
    }

    public int getUnclaimedCount(UUID playerId) {
        return (int) getMailbox(playerId).stream().filter(rewardPackage -> !rewardPackage.claimed()).count();
    }

    public int getClaimedCount(UUID playerId) {
        return (int) getMailbox(playerId).stream().filter(RewardPackage::claimed).count();
    }

    public RewardPackage getLatestPackage(UUID playerId) {
        List<RewardPackage> mailbox = mailboxes.get(playerId);
        return mailbox == null || mailbox.isEmpty() ? null : mailbox.get(mailbox.size() - 1);
    }

    private ClaimResult claimSingleReward(Player player, List<RewardPackage> mailbox, int index, RewardPackage rewardPackage, int claimedBefore) {
        if (rewardPackage.isCoreSatchelWelcome()) {
            return claimCoreSatchelWelcome(player, mailbox, index, rewardPackage, claimedBefore);
        }
        List<ItemStack> rewardStacks = rewardPackage.toItemStacks();
        int maxEpAmount = rewardPackage.hasClaimableTensuraMaxEp() ? rewardPackage.tensuraMaxEpClaimAmount() : 0;
        RewardClaimService.ClaimAttempt attempt = rewardClaimService.claim(
            player,
            new RewardClaimService.ClaimRequest(rewardPackage.rewardId(), rewardStacks, maxEpAmount,
                rewardPackage.tensuraClaimMutationPath())
        );
        if (!attempt.claimed()) {
            if (attempt.persistFailedStatus()) {
                mailbox.set(index, rewardPackage.withTensuraClaimStatus(attempt.tensuraStatus()));
                setDirty();
            }
            return new ClaimResult(claimedBefore, getUnclaimedCount(player.getUUID()), attempt.message(), 0,
                attempt.tensuraStatus(), attempt.mutationPath(), attempt.error());
        }

        RewardPackage updatedPackage = maxEpAmount > 0
            ? rewardPackage.withTensuraClaimStatus(attempt.tensuraStatus())
            : rewardPackage;
        mailbox.set(index, updatedPackage.asClaimedTombstone());
        setDirty();
        return new ClaimResult(claimedBefore + 1, getUnclaimedCount(player.getUUID()), attempt.message(),
            attempt.maxEpApplied(), attempt.tensuraStatus(), attempt.mutationPath(), attempt.error());
    }

    private ClaimResult claimCoreSatchelWelcome(
        Player player,
        List<RewardPackage> mailbox,
        int index,
        RewardPackage rewardPackage,
        int claimedBefore
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ClaimResult.messageOnly(
                "Core Satchel welcome package requires a server player.",
                getUnclaimedCount(player.getUUID())
            );
        }

        CoreSatchelData.GrantResult grant = CoreSatchelData.get(serverPlayer.serverLevel())
            .grantNormal(serverPlayer.getUUID());
        CoreSatchelScoreboardSync.sync(serverPlayer);
        mailbox.set(index, rewardPackage.asClaimedTombstone());
        setDirty();
        String message = grant == CoreSatchelData.GrantResult.GRANTED
            ? "Normal Core Satchel unlocked."
            : "Core Satchel was already unlocked; welcome package resolved.";
        return new ClaimResult(
            claimedBefore + 1,
            getUnclaimedCount(player.getUUID()),
            message,
            0,
            TENSURA_CLAIM_STATUS_NONE,
            TENSURA_MUTATION_PATH_NONE,
            "NONE"
        );
    }

    private static boolean isFloorEligibleForCompletionEp(int floorNumber) {
        return floorNumber > 0 && floorNumber % FLOOR_COMPLETION_EP_INTERVAL == 0;
    }

    public record FloorCompletionEpRewardResult(String status, RewardPackage rewardPackage, List<Integer> awardedFloors) {
    }

    public record FloorCompletionCurrencyRewardResult(String status, RewardPackage rewardPackage, String runSessionId) {
    }

    public record ForgeMaterialRewardResult(
        String status,
        RewardPackage rewardPackage,
        ForgeMaterialRecoveryPolicy.RecoveryRoll roll
    ) {
    }

    public record CoreSatchelWelcomeResult(String status, RewardPackage rewardPackage) {
    }

    public record ClaimResult(int claimedCount, int remainingUnclaimed, String message, int tensuraMaxEpApplied, String tensuraStatus, String tensuraMutationPath, String tensuraError) {
        public static ClaimResult messageOnly(String message, int remainingUnclaimed) {
            return new ClaimResult(0, remainingUnclaimed, message, 0, TENSURA_CLAIM_STATUS_NONE, TENSURA_MUTATION_PATH_NONE, "NONE");
        }

        public static ClaimResult empty(int remainingUnclaimed) {
            return messageOnly("", remainingUnclaimed);
        }

        public ClaimResult withMessage(String newMessage) {
            return new ClaimResult(claimedCount, remainingUnclaimed, newMessage, tensuraMaxEpApplied, tensuraStatus, tensuraMutationPath, tensuraError);
        }
    }

    public record RewardPackage(
        RewardIdentity identity,
        RewardSourceMetadata source,
        RewardContents contents,
        StoredRewardPreview preview,
        RewardClaimState claim,
        DungeonCurrencyAward currencyAward
    ) {
        public RewardPackage(
            long rewardId,
            UUID playerId,
            String sourceType,
            int floorNumber,
            BlockPos controllerPos,
            String modifierId,
            String rewardTier,
            List<String> rewardCategories,
            String roomProfile,
            String objective,
            String objectiveSelectionMode,
            String runSessionId,
            BlockPos sourcePos,
            String completionReason,
            String floorCompletionReason,
            boolean tensuraPreviewEnabled,
            int epPreviewAmount,
            String epPreviewStatus,
            int magiculePreviewAmount,
            String magiculePreviewStatus,
            int auraPreviewAmount,
            String auraPreviewStatus,
            int battlewillPreviewAmount,
            String battlewillPreviewStatus,
            String skillPreviewCandidateId,
            String skillPreviewStatus,
            String previewSource,
            long previewGeneratedGameTime,
            boolean tensuraSafeFallbackModeAtPreview,
            long createdGameTime,
            boolean claimed,
            int tensuraMaxEpClaimAmount,
            String tensuraClaimType,
            String tensuraClaimReason,
            String tensuraClaimStatus,
            String tensuraClaimMutationPath,
            DungeonCurrencyAward currencyAward,
            List<RewardEntry> items
        ) {
            this(
                new RewardIdentity(rewardId, playerId, createdGameTime),
                new RewardSourceMetadata(
                    sourceType,
                    floorNumber,
                    controllerPos,
                    roomProfile,
                    objective,
                    objectiveSelectionMode,
                    modifierId,
                    runSessionId,
                    sourcePos,
                    completionReason,
                    floorCompletionReason
                ),
                new RewardContents(rewardTier, rewardCategories, items),
                new StoredRewardPreview(
                    new RewardPreviewSource.RewardPreview(
                        tensuraPreviewEnabled,
                        epPreviewAmount,
                        epPreviewStatus,
                        magiculePreviewAmount,
                        magiculePreviewStatus,
                        auraPreviewAmount,
                        auraPreviewStatus,
                        battlewillPreviewAmount,
                        battlewillPreviewStatus,
                        skillPreviewCandidateId,
                        skillPreviewStatus,
                        previewSource,
                        tensuraSafeFallbackModeAtPreview
                    ),
                    previewGeneratedGameTime
                ),
                new RewardClaimState(
                    claimed,
                    tensuraMaxEpClaimAmount,
                    tensuraClaimType,
                    tensuraClaimReason,
                    tensuraClaimStatus,
                    tensuraClaimMutationPath
                ),
                currencyAward
            );
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("RewardId", rewardId());
            tag.putUUID("PlayerId", playerId());
            tag.putString("SourceType", sourceType());
            tag.putInt("FloorNumber", floorNumber());
            if (controllerPos() != null) {
                tag.putLong("ControllerPos", controllerPos().asLong());
            }
            tag.putString("ModifierId", modifierId());
            tag.putString("RewardTier", rewardTier());
            ListTag categoriesTag = new ListTag();
            for (String category : rewardCategories()) {
                categoriesTag.add(StringTag.valueOf(category));
            }
            tag.put("RewardCategories", categoriesTag);
            tag.putString("RoomProfile", roomProfile());
            tag.putString("Objective", objective());
            tag.putString("ObjectiveSelectionMode", objectiveSelectionMode());
            tag.putString("RunSessionId", runSessionId());
            if (sourcePos() != null) {
                tag.putLong("SourcePos", sourcePos().asLong());
            }
            tag.putString("CompletionReason", completionReason());
            tag.putString("FloorCompletionReason", floorCompletionReason());
            tag.putBoolean("TensuraPreviewEnabled", tensuraPreviewEnabled());
            tag.putInt("EpPreviewAmount", epPreviewAmount());
            tag.putString("EpPreviewStatus", epPreviewStatus());
            tag.putInt("MagiculePreviewAmount", magiculePreviewAmount());
            tag.putString("MagiculePreviewStatus", magiculePreviewStatus());
            tag.putInt("AuraPreviewAmount", auraPreviewAmount());
            tag.putString("AuraPreviewStatus", auraPreviewStatus());
            tag.putInt("BattlewillPreviewAmount", battlewillPreviewAmount());
            tag.putString("BattlewillPreviewStatus", battlewillPreviewStatus());
            tag.putString("SkillPreviewCandidateId", skillPreviewCandidateId());
            tag.putString("SkillPreviewStatus", skillPreviewStatus());
            tag.putString("PreviewSource", previewSource());
            tag.putLong("PreviewGeneratedGameTime", previewGeneratedGameTime());
            tag.putBoolean("TensuraSafeFallbackModeAtPreview", tensuraSafeFallbackModeAtPreview());
            tag.putLong("CreatedGameTime", createdGameTime());
            tag.putBoolean("Claimed", claimed());
            tag.putInt("TensuraMaxEpClaimAmount", tensuraMaxEpClaimAmount());
            tag.putString("TensuraClaimType", tensuraClaimType());
            tag.putString("TensuraClaimReason", tensuraClaimReason());
            tag.putString("TensuraClaimStatus", tensuraClaimStatus());
            tag.putString("TensuraClaimMutationPath", tensuraClaimMutationPath());
            if (currencyAward().active()) {
                tag.put("DungeonCurrencyAward", currencyAward().save());
            }
            ListTag itemsTag = new ListTag();
            for (RewardEntry item : items()) {
                itemsTag.add(item.save());
            }
            tag.put("Items", itemsTag);
            return tag;
        }

        private static RewardPackage load(CompoundTag tag) {
            List<RewardEntry> items = new ArrayList<>();
            ListTag itemsTag = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < itemsTag.size(); i++) {
                items.add(RewardEntry.load(itemsTag.getCompound(i)));
            }
            List<String> rewardCategories = new ArrayList<>();
            ListTag categoriesTag = tag.getList("RewardCategories", Tag.TAG_STRING);
            for (int i = 0; i < categoriesTag.size(); i++) {
                rewardCategories.add(categoriesTag.getString(i));
            }
            if (rewardCategories.isEmpty()) {
                rewardCategories.add("currency_placeholder");
                rewardCategories.add("experience_placeholder");
            }

            long createdGameTime = tag.getLong("CreatedGameTime");
            RewardIdentity identity = new RewardIdentity(
                tag.getLong("RewardId"),
                tag.getUUID("PlayerId"),
                createdGameTime
            );
            RewardSourceMetadata source = new RewardSourceMetadata(
                tag.getString("SourceType"),
                tag.getInt("FloorNumber"),
                tag.contains("ControllerPos") ? BlockPos.of(tag.getLong("ControllerPos")) : null,
                tag.contains("RoomProfile") ? tag.getString("RoomProfile") : "UNKNOWN_PROFILE",
                tag.contains("Objective") ? tag.getString("Objective") : "UNKNOWN_OBJECTIVE",
                tag.contains("ObjectiveSelectionMode") ? tag.getString("ObjectiveSelectionMode") : "UNKNOWN_SELECTION_MODE",
                tag.contains("ModifierId") ? tag.getString("ModifierId") : "NONE",
                tag.contains("RunSessionId") ? tag.getString("RunSessionId") : "UNKNOWN_RUN",
                tag.contains("SourcePos") ? BlockPos.of(tag.getLong("SourcePos")) : null,
                tag.contains("CompletionReason") ? tag.getString("CompletionReason") : "UNKNOWN_REASON",
                tag.contains("FloorCompletionReason") ? tag.getString("FloorCompletionReason") : "UNKNOWN_REASON"
            );
            RewardContents contents = new RewardContents(
                tag.contains("RewardTier") ? tag.getString("RewardTier") : "BASIC",
                List.copyOf(rewardCategories),
                List.copyOf(items)
            );
            StoredRewardPreview preview = new StoredRewardPreview(
                new RewardPreviewSource.RewardPreview(
                    tag.contains("TensuraPreviewEnabled") && tag.getBoolean("TensuraPreviewEnabled"),
                    tag.contains("EpPreviewAmount") ? Math.max(0, tag.getInt("EpPreviewAmount")) : 0,
                    tag.contains("EpPreviewStatus") ? tag.getString("EpPreviewStatus") : "UNAVAILABLE_HOOK_MISSING",
                    tag.contains("MagiculePreviewAmount") ? Math.max(0, tag.getInt("MagiculePreviewAmount")) : 0,
                    tag.contains("MagiculePreviewStatus") ? tag.getString("MagiculePreviewStatus") : "UNAVAILABLE_HOOK_MISSING",
                    tag.contains("AuraPreviewAmount") ? Math.max(0, tag.getInt("AuraPreviewAmount")) : 0,
                    tag.contains("AuraPreviewStatus") ? tag.getString("AuraPreviewStatus") : "UNAVAILABLE_HOOK_MISSING",
                    tag.contains("BattlewillPreviewAmount") ? Math.max(0, tag.getInt("BattlewillPreviewAmount")) : 0,
                    tag.contains("BattlewillPreviewStatus") ? tag.getString("BattlewillPreviewStatus") : "UNAVAILABLE_HOOK_MISSING",
                    tag.contains("SkillPreviewCandidateId") ? tag.getString("SkillPreviewCandidateId") : "NONE",
                    tag.contains("SkillPreviewStatus") ? tag.getString("SkillPreviewStatus") : "UNAVAILABLE_HOOK_MISSING",
                    tag.contains("PreviewSource") ? tag.getString("PreviewSource") : "NONE",
                    tag.contains("TensuraSafeFallbackModeAtPreview") && tag.getBoolean("TensuraSafeFallbackModeAtPreview")
                ),
                tag.contains("PreviewGeneratedGameTime") ? tag.getLong("PreviewGeneratedGameTime") : createdGameTime
            );
            RewardClaimState claim = new RewardClaimState(
                tag.getBoolean("Claimed"),
                tag.contains("TensuraMaxEpClaimAmount") ? Math.max(0, tag.getInt("TensuraMaxEpClaimAmount")) : 0,
                tag.contains("TensuraClaimType") ? tag.getString("TensuraClaimType") : TENSURA_CLAIM_TYPE_NONE,
                tag.contains("TensuraClaimReason") ? tag.getString("TensuraClaimReason") : TENSURA_CLAIM_REASON_NONE,
                tag.contains("TensuraClaimStatus") ? tag.getString("TensuraClaimStatus") : TENSURA_CLAIM_STATUS_NONE,
                tag.contains("TensuraClaimMutationPath") ? tag.getString("TensuraClaimMutationPath") : TENSURA_MUTATION_PATH_NONE
            );
            DungeonCurrencyAward currencyAward = tag.contains("DungeonCurrencyAward", Tag.TAG_COMPOUND)
                ? DungeonCurrencyAward.load(tag.getCompound("DungeonCurrencyAward"))
                : DungeonCurrencyAward.none();
            return new RewardPackage(identity, source, contents, preview, claim, currencyAward);
        }

        public RewardPackage withClaimed(boolean claimed) {
            return new RewardPackage(identity, source, contents, preview, claim.withClaimed(claimed), currencyAward);
        }

        public RewardPackage asClaimedTombstone() {
            return new RewardPackage(identity, source, contents.withItems(List.of()), preview, claim.withClaimed(true), currencyAward);
        }

        public RewardPackage withTensuraClaimStatus(String claimStatus) {
            return new RewardPackage(identity, source, contents, preview, claim.withStatus(claimStatus), currencyAward);
        }

        public long rewardId() {
            return identity.rewardId();
        }

        public UUID playerId() {
            return identity.playerId();
        }

        public long createdGameTime() {
            return identity.createdGameTime();
        }

        public String sourceType() {
            return source.sourceType();
        }

        public int floorNumber() {
            return source.floorNumber();
        }

        public BlockPos controllerPos() {
            return source.controllerPos();
        }

        public String modifierId() {
            return source.modifierId();
        }

        public String roomProfile() {
            return source.roomProfile();
        }

        public String objective() {
            return source.objective();
        }

        public String objectiveSelectionMode() {
            return source.objectiveSelectionMode();
        }

        public String runSessionId() {
            return source.runSessionId();
        }

        public BlockPos sourcePos() {
            return source.sourcePos();
        }

        public String completionReason() {
            return source.completionReason();
        }

        public String floorCompletionReason() {
            return source.floorCompletionReason();
        }

        public String rewardTier() {
            return contents.tier();
        }

        public List<String> rewardCategories() {
            return contents.categories();
        }

        public List<RewardEntry> items() {
            return contents.items();
        }

        public boolean tensuraPreviewEnabled() {
            return preview.rewardPreview().previewEnabled();
        }

        public int epPreviewAmount() {
            return preview.rewardPreview().epAmount();
        }

        public String epPreviewStatus() {
            return preview.rewardPreview().epStatus();
        }

        public int magiculePreviewAmount() {
            return preview.rewardPreview().magiculeAmount();
        }

        public String magiculePreviewStatus() {
            return preview.rewardPreview().magiculeStatus();
        }

        public int auraPreviewAmount() {
            return preview.rewardPreview().auraAmount();
        }

        public String auraPreviewStatus() {
            return preview.rewardPreview().auraStatus();
        }

        public int battlewillPreviewAmount() {
            return preview.rewardPreview().battlewillAmount();
        }

        public String battlewillPreviewStatus() {
            return preview.rewardPreview().battlewillStatus();
        }

        public String skillPreviewCandidateId() {
            return preview.rewardPreview().skillCandidateId();
        }

        public String skillPreviewStatus() {
            return preview.rewardPreview().skillStatus();
        }

        public String previewSource() {
            return preview.rewardPreview().previewSource();
        }

        public long previewGeneratedGameTime() {
            return preview.generatedGameTime();
        }

        public boolean tensuraSafeFallbackModeAtPreview() {
            return preview.rewardPreview().safeFallbackMode();
        }

        public boolean claimed() {
            return claim.claimed();
        }

        public int tensuraMaxEpClaimAmount() {
            return claim.maxEpAmount();
        }

        public String tensuraClaimType() {
            return claim.type();
        }

        public String tensuraClaimReason() {
            return claim.reason();
        }

        public String tensuraClaimStatus() {
            return claim.status();
        }

        public String tensuraClaimMutationPath() {
            return claim.mutationPath();
        }

        public List<ItemStack> toItemStacks() {
            List<ItemStack> stacks = new ArrayList<>();
            for (RewardEntry item : items()) {
                Item resolvedItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.itemId()));
                if (resolvedItem != null) {
                    stacks.add(new ItemStack(resolvedItem, item.count()));
                }
            }
            return stacks;
        }

        public boolean hasClaimableTensuraMaxEp() {
            return tensuraMaxEpClaimAmount() > 0 && TENSURA_CLAIM_TYPE_MAX_EP.equals(tensuraClaimType()) && !claimed();
        }

        public String tensuraClaimSummary() {
            if (TENSURA_CLAIM_TYPE_NONE.equals(tensuraClaimType()) || tensuraMaxEpClaimAmount() <= 0) {
                return "NONE";
            }
            return tensuraClaimType() + " " + tensuraMaxEpClaimAmount() + " reason=" + tensuraClaimReason()
                + " status=" + tensuraClaimStatus() + " path=" + tensuraClaimMutationPath();
        }

        public String itemSummary() {
            if (isCoreSatchelWelcome()) {
                return "Normal Core Satchel unlock";
            }
            if (items().isEmpty()) {
                return "NONE";
            }
            return items().stream().map(item -> item.itemId() + " x" + item.count()).collect(Collectors.joining(", "));
        }

        public String categorySummary() {
            if (rewardCategories().isEmpty()) {
                return "NONE";
            }
            return String.join(", ", rewardCategories());
        }

        public String controllerPosSummary() {
            return formatPos(controllerPos());
        }

        public String sourcePosSummary() {
            return formatPos(sourcePos());
        }

        public boolean isCoreSatchelWelcome() {
            return CORE_SATCHEL_WELCOME_SOURCE.equals(sourceType())
                && CORE_SATCHEL_WELCOME_TIER.equals(rewardTier())
                && rewardCategories().contains(CORE_SATCHEL_WELCOME_CATEGORY);
        }
    }

    public record RewardIdentity(long rewardId, UUID playerId, long createdGameTime) {
    }

    public record StoredRewardPreview(RewardPreviewSource.RewardPreview rewardPreview, long generatedGameTime) {
    }

    public record RewardClaimState(
        boolean claimed,
        int maxEpAmount,
        String type,
        String reason,
        String status,
        String mutationPath
    ) {
        private static RewardClaimState none() {
            return new RewardClaimState(
                false,
                0,
                TENSURA_CLAIM_TYPE_NONE,
                TENSURA_CLAIM_REASON_NONE,
                TENSURA_CLAIM_STATUS_NONE,
                TENSURA_MUTATION_PATH_NONE
            );
        }

        private RewardClaimState withClaimed(boolean claimed) {
            return new RewardClaimState(claimed, maxEpAmount, type, reason, status, mutationPath);
        }

        private RewardClaimState withStatus(String status) {
            return new RewardClaimState(claimed, maxEpAmount, type, reason, status, mutationPath);
        }
    }

    public record DungeonCurrencyAward(
        String clearRank,
        long baseBronze,
        int rankBonusBasisPoints,
        int modifierBonusBasisPoints,
        int appliedCombinedBonusBasisPoints,
        long bonusBronze,
        long totalBronze
    ) {
        private static DungeonCurrencyAward from(DungeonCurrencyPolicy.CompletionAward award) {
            return new DungeonCurrencyAward(
                award.clearRank().name(), award.baseBronze(), award.rankBonusBasisPoints(),
                award.modifierBonusBasisPoints(), award.appliedCombinedBonusBasisPoints(),
                award.bonusBronze(), award.payout().canonicalBronze()
            );
        }

        private static DungeonCurrencyAward none() {
            return new DungeonCurrencyAward("NONE", 0L, 0, 0, 0, 0L, 0L);
        }

        public boolean active() {
            return totalBronze > 0L;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("ClearRank", clearRank);
            tag.putLong("BaseBronze", baseBronze);
            tag.putInt("RankBonusBasisPoints", rankBonusBasisPoints);
            tag.putInt("ModifierBonusBasisPoints", modifierBonusBasisPoints);
            tag.putInt("AppliedCombinedBonusBasisPoints", appliedCombinedBonusBasisPoints);
            tag.putLong("BonusBronze", bonusBronze);
            tag.putLong("TotalBronze", totalBronze);
            return tag;
        }

        private static DungeonCurrencyAward load(CompoundTag tag) {
            return new DungeonCurrencyAward(
                tag.contains("ClearRank") ? tag.getString("ClearRank") : "NONE",
                Math.max(0L, tag.getLong("BaseBronze")),
                Math.max(0, tag.getInt("RankBonusBasisPoints")),
                Math.max(0, tag.getInt("ModifierBonusBasisPoints")),
                Math.max(0, tag.getInt("AppliedCombinedBonusBasisPoints")),
                Math.max(0L, tag.getLong("BonusBronze")),
                Math.max(0L, tag.getLong("TotalBronze"))
            );
        }
    }

    public record RewardSourceMetadata(String sourceType, int floorNumber, BlockPos controllerPos, String roomProfile, String objective, String objectiveSelectionMode, String modifierId, String runSessionId, BlockPos sourcePos, String completionReason, String floorCompletionReason) {
        public static RewardSourceMetadata unknown() {
            return new RewardSourceMetadata("ROOM_CLEAR", 1, null, "UNKNOWN_PROFILE", "UNKNOWN_OBJECTIVE", "UNKNOWN_SELECTION_MODE", "NONE", "UNKNOWN_RUN", null, "UNKNOWN_REASON", "UNKNOWN_REASON");
        }

        public RewardSourceMetadata normalized() {
            return new RewardSourceMetadata(
                normalizeString(sourceType, "ROOM_CLEAR"),
                Math.max(1, floorNumber),
                immutableOrNull(controllerPos),
                normalizeString(roomProfile, "UNKNOWN_PROFILE"),
                normalizeString(objective, "UNKNOWN_OBJECTIVE"),
                normalizeString(objectiveSelectionMode, "UNKNOWN_SELECTION_MODE"),
                normalizeString(modifierId, "NONE"),
                normalizeString(runSessionId, "UNKNOWN_RUN"),
                immutableOrNull(sourcePos),
                normalizeString(completionReason, "UNKNOWN_REASON"),
                normalizeString(floorCompletionReason, "UNKNOWN_REASON")
            );
        }
    }

    private RewardPreviewSource.RewardPreview buildTensuraRewardPreview(ServerLevel level, UUID playerId, int floorNumber, String rewardTier, String sourceType) {
        if (level == null || playerId == null) {
            return RewardPreviewSource.RewardPreview.disabled();
        }
        return rewardPreviewSource.preview(level.getServer().getPlayerList().getPlayer(playerId), floorNumber, rewardTier, sourceType);
    }

    private static BlockPos immutableOrNull(BlockPos pos) {
        return pos == null ? null : pos.immutable();
    }

    private static String normalizeString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String formatPos(BlockPos pos) {
        return pos == null ? "NONE" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    public record RewardContents(String tier, List<String> categories, List<RewardEntry> items) {
        private static final RewardCatalog CATALOG = new RewardCatalog();

        public static RewardContents forFloor(int floorNumber) {
            return from(CATALOG.contentsForFloor(floorNumber));
        }

        private static RewardContents from(RewardCatalog.RewardContents contents) {
            return new RewardContents(
                contents.tier(),
                contents.categories(),
                contents.items().stream().map(item -> new RewardEntry(item.itemId(), item.count())).toList()
            );
        }

        private RewardContents withItems(List<RewardEntry> replacementItems) {
            return new RewardContents(tier, categories, List.copyOf(replacementItems));
        }
    }

    public record RewardEntry(String itemId, int count) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("ItemId", itemId);
            tag.putInt("Count", count);
            return tag;
        }

        private static RewardEntry load(CompoundTag tag) {
            return new RewardEntry(tag.getString("ItemId"), tag.getInt("Count"));
        }
    }
}
