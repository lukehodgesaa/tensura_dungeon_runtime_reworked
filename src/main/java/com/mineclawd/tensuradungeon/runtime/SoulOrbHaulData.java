package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.item.SoulOrbRarity;
import com.mineclawd.tensuradungeon.item.SoulOrbItem;
import com.mineclawd.tensuradungeon.network.SoulOrbHudPayload;
import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyDefinition;
import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyDefinition.EncounterClass;
import com.mineclawd.tensuradungeon.reward.InventoryGrant;
import com.mineclawd.tensuradungeon.reward.VanillaInventoryGrant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class SoulOrbHaulData extends SavedData {
    private static final String DATA_NAME = "tensura_dungeon_soul_orb_haul";
    private static final String SELLING_UNAVAILABLE = "OBSERVATION_ONLY_PRICING";

    private final Map<UUID, Map<String, RunHaul>> playerHauls = new LinkedHashMap<>();
    private DungeonCrystalRollLedger crystalRollLedger = new DungeonCrystalRollLedger();
    private AppraisalObservationLedger appraisalObservationLedger = new AppraisalObservationLedger();

    public static SoulOrbHaulData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(SoulOrbHaulData::new, SoulOrbHaulData::load), DATA_NAME
        );
    }

    public static SoulOrbHaulData load(CompoundTag tag, HolderLookup.Provider registries) {
        SoulOrbHaulData data = new SoulOrbHaulData();
        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            CompoundTag playerTag = players.getCompound(playerIndex);
            if (!playerTag.hasUUID("PlayerId")) continue;
            UUID playerId = playerTag.getUUID("PlayerId");
            Map<String, RunHaul> sessions = new LinkedHashMap<>();
            ListTag sessionTags = playerTag.getList("Sessions", Tag.TAG_COMPOUND);
            for (int sessionIndex = 0; sessionIndex < sessionTags.size(); sessionIndex++) {
                RunHaul haul = RunHaul.load(sessionTags.getCompound(sessionIndex), registries);
                if (!haul.sessionId.isBlank() && !"NONE".equals(haul.sessionId)) {
                    sessions.put(haul.sessionId, haul);
                }
            }
            if (!sessions.isEmpty()) data.playerHauls.put(playerId, sessions);
        }
        if (tag.contains("CrystalRollLedger", Tag.TAG_COMPOUND)) {
            data.crystalRollLedger = DungeonCrystalRollLedger.load(tag.getCompound("CrystalRollLedger"));
        }
        if (tag.contains("AppraisalObservations", Tag.TAG_COMPOUND)) {
            data.appraisalObservationLedger = AppraisalObservationLedger.load(
                tag.getCompound("AppraisalObservations"), registries
            );
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Map<String, RunHaul>> playerEntry : playerHauls.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("PlayerId", playerEntry.getKey());
            ListTag sessions = new ListTag();
            for (RunHaul haul : playerEntry.getValue().values()) {
                sessions.add(haul.save(registries));
            }
            playerTag.put("Sessions", sessions);
            players.add(playerTag);
        }
        tag.put("Players", players);
        tag.put("CrystalRollLedger", crystalRollLedger.save());
        tag.put("AppraisalObservations", appraisalObservationLedger.save(registries));
        return tag;
    }

    public void beginSession(UUID playerId, String sessionId, int floor) {
        if (!validIdentity(playerId, sessionId)) return;
        Map<String, RunHaul> sessions = playerHauls.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>());
        if (!sessions.containsKey(sessionId)) {
            sessions.put(sessionId, new RunHaul(sessionId, Math.max(1, floor)));
            setDirty();
        }
    }

    public boolean registerPending(
        UUID playerId,
        String sessionId,
        int floor,
        UUID orbId,
        UUID entityId,
        SoulOrbRarity rarity,
        List<ItemStack> rolledDrops
    ) {
        return registerPending(
            playerId, sessionId, floor, orbId, entityId, rarity, rolledDrops, null
        );
    }

    public boolean registerPending(
        UUID playerId,
        String sessionId,
        int floor,
        UUID orbId,
        UUID entityId,
        SoulOrbRarity rarity,
        List<ItemStack> rolledDrops,
        CrystalCapture crystalCapture
    ) {
        if (!validIdentity(playerId, sessionId) || orbId == null || entityId == null) return false;
        beginSession(playerId, sessionId, floor);
        RunHaul haul = playerHauls.get(playerId).get(sessionId);
        if (haul.lifecycle != Lifecycle.CAPTURING || haul.orbs.containsKey(orbId)) return false;
        List<ItemStack> drops = new ArrayList<>();
        if (rolledDrops != null) {
            rolledDrops.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .filter(stack -> !SoulOrbItem.isVisualToken(stack))
                .filter(stack -> !isMonsterCrystal(stack))
                .map(ItemStack::copy)
                .forEach(drops::add);
        }
        List<DropEntry> entries = new ArrayList<>(drops.size());
        for (int index = 0; index < drops.size(); index++) {
            entries.add(new DropEntry(entryId(orbId, index), drops.get(index), Disposition.UNRESOLVED));
        }
        haul.orbs.put(orbId, new OrbRecord(
            orbId, entityId, rarity == null ? SoulOrbRarity.COMMON : rarity, false, entries,
            crystalCapture == null ? "UNKNOWN" : crystalCapture.encounterClass().name()
        ));
        haul.advanceRevision();
        setDirty();
        return true;
    }

    public record CrystalCapture(
        BlockPos controllerPos,
        EncounterClass encounterClass,
        DungeonCrystalEconomyDefinition definition,
        IntUnaryOperator nextInt
    ) {
        public CrystalCapture {
            if (controllerPos == null || encounterClass == null || definition == null || nextInt == null) {
                throw new IllegalArgumentException("Crystal capture requires complete encounter provenance");
            }
            controllerPos = controllerPos.immutable();
        }
    }

    public boolean collect(UUID playerId, String sessionId, UUID orbId) {
        if (orbId == null) return false;
        return collectAll(playerId, sessionId, List.of(orbId)) == 1;
    }

    public int collectAll(UUID playerId, String sessionId, List<UUID> orbIds) {
        RunHaul haul = haul(playerId, sessionId);
        if (haul == null || orbIds == null || orbIds.isEmpty()) return 0;
        int collected = 0;
        java.util.LinkedHashSet<UUID> uniqueIds = new java.util.LinkedHashSet<>(orbIds);
        for (UUID orbId : uniqueIds) {
            OrbRecord orb = orbId == null ? null : haul.orbs.get(orbId);
            if (orb != null && !orb.collected) {
                orb.collected = true;
                collected++;
            }
        }
        if (collected == 0) return 0;
        if (haul.lifecycle == Lifecycle.CAPTURING) {
            haul.advanceRevision();
        }
        setDirty();
        return collected;
    }

    public void completeSession(ServerLevel level, ServerPlayer player, String sessionId, int floor) {
        beginSession(player.getUUID(), sessionId, floor);
        freezeSession(player.getUUID(), sessionId, floor);
        syncHud(player);
    }

    public boolean registerCompletionRewards(UUID playerId, String sessionId, List<ItemStack> rewardStacks) {
        RunHaul haul = haul(playerId, sessionId);
        if (haul == null || haul.lifecycle != Lifecycle.READY || haul.completionRewardsRegistered) {
            return false;
        }
        if (rewardStacks != null) {
            int index = 0;
            for (ItemStack rewardStack : rewardStacks) {
                if (rewardStack == null || rewardStack.isEmpty()) continue;
                haul.completionRewards.add(new DropEntry(
                    completionEntryId(index++), rewardStack, Disposition.UNRESOLVED
                ));
            }
        }
        haul.completionRewardsRegistered = true;
        haul.advanceRevision();
        setDirty();
        return true;
    }

    void freezeSession(UUID playerId, String sessionId, int floor) {
        beginSession(playerId, sessionId, floor);
        RunHaul haul = haul(playerId, sessionId);
        if (haul != null && haul.lifecycle == Lifecycle.CAPTURING) {
            haul.lifecycle = Lifecycle.READY;
            haul.advanceRevision();
            setDirty();
        }
    }

    public Summary snapshot(UUID playerId, String sessionId) {
        RunHaul haul = haul(playerId, sessionId);
        return haul == null ? Summary.missing(sessionId) : haul.snapshot();
    }

    public Summary revealForDisplay(UUID playerId, String sessionId) {
        RunHaul haul = haul(playerId, sessionId);
        if (haul == null) return Summary.missing(sessionId);
        if (haul.lifecycle == Lifecycle.READY) {
            commitAppraisalObservations(haul);
            haul.lifecycle = Lifecycle.APPRAISED;
            if (haul.unresolvedEntries().isEmpty()) haul.refreshResolutionLifecycle();
            haul.advanceRevision();
            setDirty();
        }
        return haul.snapshot();
    }

    public Summary latestUnresolved(UUID playerId, String activeSessionId) {
        Map<String, RunHaul> sessions = playerHauls.get(playerId);
        if (sessions == null) return Summary.missing("");
        RunHaul latest = null;
        for (RunHaul haul : sessions.values()) {
            if (haul.lifecycle == Lifecycle.CAPTURING && !haul.sessionId.equals(activeSessionId)) {
                for (OrbRecord orb : haul.orbs.values()) orb.collected = true;
                haul.lifecycle = Lifecycle.READY;
                haul.advanceRevision();
                setDirty();
            }
            if (haul.lifecycle == Lifecycle.READY
                || haul.lifecycle == Lifecycle.APPRAISED
                || haul.lifecycle == Lifecycle.PARTIALLY_RESOLVED) {
                latest = haul;
            }
        }
        return latest == null ? Summary.missing("") : latest.snapshot();
    }

    public ActionResult appraise(UUID playerId, String sessionId, long observedRevision) {
        RunHaul haul = haul(playerId, sessionId);
        if (haul == null) return ActionResult.failure(ActionStatus.SESSION_NOT_FOUND, Summary.missing(sessionId));
        if (observedRevision != haul.revision) return ActionResult.failure(ActionStatus.STALE_REVISION, haul.snapshot());
        if (haul.lifecycle == Lifecycle.READY) {
            commitAppraisalObservations(haul);
            haul.lifecycle = Lifecycle.APPRAISED;
            if (haul.unresolvedEntries().isEmpty()) haul.refreshResolutionLifecycle();
            haul.advanceRevision();
            setDirty();
            return ActionResult.success(haul.snapshot());
        }
        if (haul.lifecycle == Lifecycle.APPRAISED
            || haul.lifecycle == Lifecycle.PARTIALLY_RESOLVED
            || haul.lifecycle == Lifecycle.RESOLVED) {
            return ActionResult.success(haul.snapshot());
        }
        return ActionResult.failure(ActionStatus.NOT_READY, haul.snapshot());
    }

    public AppraisalObservationLedger.Snapshot appraisalObservations(int limit) {
        return appraisalObservationLedger.snapshot(limit);
    }

    private void commitAppraisalObservations(RunHaul haul) {
        if (haul.observationsCommitted) return;
        appraisalObservationLedger.observeHaul(haul.floor, haul.observationInputs());
        haul.observationsCommitted = true;
    }

    public ActionResult keepAll(ServerPlayer player, String sessionId, long observedRevision) {
        return keepAll(player, sessionId, observedRevision, VanillaInventoryGrant.INSTANCE);
    }

    ActionResult keepAll(ServerPlayer player, String sessionId, long observedRevision, InventoryGrant inventoryGrant) {
        RunHaul haul = haul(player.getUUID(), sessionId);
        if (haul == null) return ActionResult.failure(ActionStatus.SESSION_NOT_FOUND, Summary.missing(sessionId));
        if (observedRevision != haul.revision) return ActionResult.failure(ActionStatus.STALE_REVISION, haul.snapshot());
        if (haul.lifecycle != Lifecycle.APPRAISED && haul.lifecycle != Lifecycle.PARTIALLY_RESOLVED) {
            return ActionResult.failure(ActionStatus.NOT_APPRAISED, haul.snapshot());
        }
        List<DropEntry> unresolved = haul.unresolvedEntries();
        if (unresolved.isEmpty()) return ActionResult.failure(ActionStatus.NOTHING_TO_RESOLVE, haul.snapshot());
        List<ItemStack> stacks = unresolved.stream().map(entry -> entry.stack.copy()).toList();
        if (!inventoryGrant.canGrant(player, stacks)) {
            return ActionResult.failure(ActionStatus.INVENTORY_FULL, haul.snapshot());
        }
        inventoryGrant.grant(player, stacks);
        unresolved.forEach(entry -> entry.disposition = Disposition.KEPT);
        haul.refreshResolutionLifecycle();
        haul.advanceRevision();
        setDirty();
        return ActionResult.success(haul.snapshot());
    }

    public ActionResult keepEntry(
        ServerPlayer player,
        String sessionId,
        long observedRevision,
        String entryId
    ) {
        return keepEntry(player, sessionId, observedRevision, entryId, VanillaInventoryGrant.INSTANCE);
    }

    ActionResult keepEntry(
        ServerPlayer player,
        String sessionId,
        long observedRevision,
        String entryId,
        InventoryGrant inventoryGrant
    ) {
        RunHaul haul = haul(player.getUUID(), sessionId);
        if (haul == null) return ActionResult.failure(ActionStatus.SESSION_NOT_FOUND, Summary.missing(sessionId));
        if (observedRevision != haul.revision) return ActionResult.failure(ActionStatus.STALE_REVISION, haul.snapshot());
        if (haul.lifecycle != Lifecycle.APPRAISED && haul.lifecycle != Lifecycle.PARTIALLY_RESOLVED) {
            return ActionResult.failure(ActionStatus.NOT_APPRAISED, haul.snapshot());
        }
        List<DropEntry> selected = haul.unresolvedEntriesForSnapshot(entryId);
        if (selected.isEmpty()) return ActionResult.failure(ActionStatus.ENTRY_NOT_FOUND, haul.snapshot());
        List<ItemStack> stacks = selected.stream().map(entry -> entry.stack.copy()).toList();
        if (!inventoryGrant.canGrant(player, stacks)) {
            return ActionResult.failure(ActionStatus.INVENTORY_FULL, haul.snapshot());
        }
        inventoryGrant.grant(player, stacks);
        selected.forEach(entry -> entry.disposition = Disposition.KEPT);
        haul.refreshResolutionLifecycle();
        haul.advanceRevision();
        setDirty();
        return ActionResult.success(haul.snapshot());
    }

    public Counts counts(UUID playerId, String sessionId) {
        RunHaul haul = haul(playerId, sessionId);
        if (haul == null) return Counts.EMPTY;
        int common = 0;
        int uncommon = 0;
        int rare = 0;
        int epic = 0;
        for (OrbRecord orb : haul.orbs.values()) {
            if (!orb.collected) continue;
            switch (orb.rarity) {
                case COMMON -> common++;
                case UNCOMMON -> uncommon++;
                case RARE -> rare++;
                case EPIC -> epic++;
            }
        }
        return new Counts(common, uncommon, rare, epic);
    }

    public List<ItemStack> collectedDrops(UUID playerId, String sessionId) {
        RunHaul haul = haul(playerId, sessionId);
        if (haul == null) return List.of();
        return haul.orbs.values().stream()
            .filter(orb -> orb.collected)
            .flatMap(orb -> orb.entries.stream())
            .filter(entry -> !isMonsterCrystal(entry.stack))
            .map(entry -> entry.stack.copy())
            .toList();
    }

    public void syncHud(ServerPlayer player) {
        if (!NetworkRegistry.hasChannel(player.connection, SoulOrbHudPayload.TYPE.id())) return;
        PacketDistributor.sendToPlayer(player, new SoulOrbHudPayload(
            false, "NONE", 0, 0, 0, 0, 0
        ));
    }

    private RunHaul haul(UUID playerId, String sessionId) {
        Map<String, RunHaul> sessions = playerHauls.get(playerId);
        return sessions == null ? null : sessions.get(sessionId);
    }

    private static String entryId(UUID orbId, int index) {
        return orbId + ":" + index;
    }

    private static String completionEntryId(int index) {
        return "completion:" + index;
    }

    private static boolean validIdentity(UUID playerId, String sessionId) {
        return playerId != null && sessionId != null && !sessionId.isBlank() && !"NONE".equals(sessionId);
    }

    private static boolean isMonsterCrystal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return TensuraDungeonRuntime.MOD_ID.equals(id.getNamespace())
            && id.getPath().startsWith("monster_crystal_tier_");
    }

    public enum Lifecycle {
        CAPTURING,
        READY,
        APPRAISED,
        PARTIALLY_RESOLVED,
        RESOLVED;

        static Lifecycle parse(String value) {
            try {
                return value == null || value.isBlank() ? CAPTURING : valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return CAPTURING;
            }
        }
    }

    public enum Disposition {
        UNRESOLVED,
        KEPT,
        SOLD,
        RETIRED;

        static Disposition parse(String value) {
            try {
                return value == null || value.isBlank() ? UNRESOLVED : valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return UNRESOLVED;
            }
        }
    }

    public enum ActionStatus {
        SUCCESS,
        SESSION_NOT_FOUND,
        STALE_REVISION,
        NOT_READY,
        NOT_APPRAISED,
        INVENTORY_FULL,
        NOTHING_TO_RESOLVE,
        ENTRY_NOT_FOUND,
        SELLING_UNAVAILABLE
    }

    public record Counts(int common, int uncommon, int rare, int epic) {
        public static final Counts EMPTY = new Counts(0, 0, 0, 0);

        public int total() {
            return common + uncommon + rare + epic;
        }
    }

    public record Summary(
        boolean available,
        String sessionId,
        int floor,
        Lifecycle lifecycle,
        long revision,
        Counts counts,
        boolean canAppraise,
        boolean canKeepAll,
        boolean canQuickSell,
        boolean canSellAll,
        List<EntrySnapshot> entries
    ) {
        public Summary {
            sessionId = sessionId == null ? "" : sessionId;
            floor = Math.max(0, floor);
            lifecycle = lifecycle == null ? Lifecycle.CAPTURING : lifecycle;
            revision = Math.max(0L, revision);
            counts = counts == null ? Counts.EMPTY : counts;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        static Summary missing(String sessionId) {
            return new Summary(false, sessionId, 0, Lifecycle.CAPTURING, 0L, Counts.EMPTY,
                false, false, false, false, List.of());
        }
    }

    public record EntrySnapshot(
        String entryId,
        ItemStack stack,
        SoulOrbRarity rarity,
        Disposition disposition,
        boolean sellable,
        String unavailableReason
    ) {
        public EntrySnapshot {
            entryId = entryId == null ? "" : entryId;
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            rarity = rarity == null ? SoulOrbRarity.COMMON : rarity;
            disposition = disposition == null ? Disposition.UNRESOLVED : disposition;
            unavailableReason = unavailableReason == null ? SELLING_UNAVAILABLE : unavailableReason;
        }
    }

    public record ActionResult(ActionStatus status, Summary summary) {
        static ActionResult success(Summary summary) {
            return new ActionResult(ActionStatus.SUCCESS, summary);
        }

        static ActionResult failure(ActionStatus status, Summary summary) {
            return new ActionResult(status, summary);
        }

        public boolean succeeded() {
            return status == ActionStatus.SUCCESS;
        }
    }

    private static final class RunHaul {
        private final String sessionId;
        private final int floor;
        private Lifecycle lifecycle = Lifecycle.CAPTURING;
        private long revision = 1L;
        private final Map<UUID, OrbRecord> orbs = new LinkedHashMap<>();
        private final List<DropEntry> completionRewards = new ArrayList<>();
        private boolean completionRewardsRegistered;
        private boolean observationsCommitted;

        private RunHaul(String sessionId, int floor) {
            this.sessionId = sessionId;
            this.floor = floor;
        }

        private void advanceRevision() {
            revision = revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1L;
        }

        private List<DropEntry> unresolvedEntries() {
            return allEntries()
                .filter(entry -> entry.disposition == Disposition.UNRESOLVED)
                .filter(entry -> !isMonsterCrystal(entry.stack))
                .toList();
        }

        private java.util.stream.Stream<DropEntry> allEntries() {
            return java.util.stream.Stream.concat(
                orbs.values().stream().flatMap(orb -> orb.entries.stream()),
                completionRewards.stream()
            );
        }

        private List<AppraisalObservationLedger.Input> observationInputs() {
            List<AppraisalObservationLedger.Input> inputs = new ArrayList<>();
            for (OrbRecord orb : orbs.values()) {
                for (DropEntry entry : orb.entries) {
                    if (isMonsterCrystal(entry.stack)) continue;
                    inputs.add(new AppraisalObservationLedger.Input(entry.stack, orb.encounterClass));
                }
            }
            return inputs;
        }

        private List<DropEntry> unresolvedEntriesForSnapshot(String entryId) {
            DropEntry anchor = unresolvedEntries().stream()
                .filter(entry -> entry.entryId.equals(entryId))
                .findFirst()
                .orElse(null);
            if (anchor == null) return List.of();
            return unresolvedEntries().stream()
                .filter(entry -> ItemStack.isSameItemSameComponents(entry.stack, anchor.stack))
                .toList();
        }

        private void refreshResolutionLifecycle() {
            long unresolved = allEntries()
                .filter(entry -> !isMonsterCrystal(entry.stack))
                .filter(entry -> entry.disposition == Disposition.UNRESOLVED)
                .count();
            long resolved = allEntries()
                .filter(entry -> !isMonsterCrystal(entry.stack))
                .filter(entry -> entry.disposition != Disposition.UNRESOLVED)
                .count();
            lifecycle = unresolved == 0L ? Lifecycle.RESOLVED
                : resolved == 0L ? Lifecycle.APPRAISED : Lifecycle.PARTIALLY_RESOLVED;
        }

        private Summary snapshot() {
            boolean revealed = lifecycle == Lifecycle.APPRAISED
                || lifecycle == Lifecycle.PARTIALLY_RESOLVED
                || lifecycle == Lifecycle.RESOLVED;
            List<EntrySnapshot> snapshots = revealed ? aggregatedEntrySnapshots() : List.of();
            boolean unresolved = revealed && snapshots.stream()
                .anyMatch(entry -> entry.disposition == Disposition.UNRESOLVED);
            return new Summary(true, sessionId, floor, lifecycle, revision, securedCounts(),
                lifecycle == Lifecycle.READY, unresolved, false, false, snapshots);
        }

        private List<EntrySnapshot> aggregatedEntrySnapshots() {
            List<EntryAccumulator> aggregated = new ArrayList<>();
            for (OrbRecord orb : orbs.values()) {
                for (DropEntry entry : orb.entries) {
                    if (isMonsterCrystal(entry.stack)) continue;
                    accumulate(aggregated, entry, orb.rarity);
                }
            }
            for (DropEntry entry : completionRewards) {
                if (!isMonsterCrystal(entry.stack)) accumulate(aggregated, entry, SoulOrbRarity.COMMON);
            }
            return aggregated.stream().map(entry -> new EntrySnapshot(
                entry.entryId, entry.stack, entry.rarity, entry.disposition, false, SELLING_UNAVAILABLE
            )).toList();
        }

        private static void accumulate(
            List<EntryAccumulator> aggregated,
            DropEntry entry,
            SoulOrbRarity rarity
        ) {
            EntryAccumulator match = null;
            for (EntryAccumulator candidate : aggregated) {
                if (candidate.disposition == entry.disposition
                    && ItemStack.isSameItemSameComponents(candidate.stack, entry.stack)) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                aggregated.add(new EntryAccumulator(
                    entry.entryId, entry.stack.copy(), rarity, entry.disposition
                ));
            } else {
                match.stack.grow(entry.stack.getCount());
                if (rarity.ordinal() > match.rarity.ordinal()) match.rarity = rarity;
            }
        }

        private Counts counts() {
            int common = 0;
            int uncommon = 0;
            int rare = 0;
            int epic = 0;
            for (OrbRecord orb : orbs.values()) {
                if (!orb.collected) continue;
                switch (orb.rarity) {
                    case COMMON -> common++;
                    case UNCOMMON -> uncommon++;
                    case RARE -> rare++;
                    case EPIC -> epic++;
                }
            }
            return new Counts(common, uncommon, rare, epic);
        }

        private Counts securedCounts() {
            int common = 0;
            int uncommon = 0;
            int rare = 0;
            int epic = 0;
            for (OrbRecord orb : orbs.values()) {
                switch (orb.rarity) {
                    case COMMON -> common++;
                    case UNCOMMON -> uncommon++;
                    case RARE -> rare++;
                    case EPIC -> epic++;
                }
            }
            return new Counts(common, uncommon, rare, epic);
        }

        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("SessionId", sessionId);
            tag.putInt("Floor", floor);
            tag.putString("Lifecycle", lifecycle.name());
            tag.putLong("Revision", revision);
            tag.putBoolean("ObservationsCommitted", observationsCommitted);
            tag.putBoolean("CompletionRewardsRegistered", completionRewardsRegistered);
            ListTag completionRewardTags = new ListTag();
            for (DropEntry entry : completionRewards) completionRewardTags.add(entry.save(registries));
            tag.put("CompletionRewards", completionRewardTags);
            ListTag entries = new ListTag();
            for (OrbRecord orb : orbs.values()) entries.add(orb.save(registries));
            tag.put("Orbs", entries);
            return tag;
        }

        private static RunHaul load(CompoundTag tag, HolderLookup.Provider registries) {
            RunHaul haul = new RunHaul(tag.getString("SessionId"), Math.max(1, tag.getInt("Floor")));
            haul.lifecycle = Lifecycle.parse(tag.getString("Lifecycle"));
            haul.revision = Math.max(1L, tag.getLong("Revision"));
            haul.observationsCommitted = tag.getBoolean("ObservationsCommitted");
            haul.completionRewardsRegistered = tag.getBoolean("CompletionRewardsRegistered");
            ListTag completionRewardTags = tag.getList("CompletionRewards", Tag.TAG_COMPOUND);
            for (int index = 0; index < completionRewardTags.size(); index++) {
                DropEntry entry = DropEntry.load(completionRewardTags.getCompound(index), registries);
                if (entry != null) haul.completionRewards.add(entry);
            }
            if (!haul.completionRewards.isEmpty()) haul.completionRewardsRegistered = true;
            ListTag entries = tag.getList("Orbs", Tag.TAG_COMPOUND);
            for (int index = 0; index < entries.size(); index++) {
                OrbRecord orb = OrbRecord.load(entries.getCompound(index), registries);
                if (orb != null) {
                    orb.entries.stream()
                        .filter(entry -> isMonsterCrystal(entry.stack))
                        .filter(entry -> entry.disposition == Disposition.UNRESOLVED)
                        .forEach(entry -> entry.disposition = Disposition.RETIRED);
                    haul.orbs.put(orb.orbId, orb);
                }
            }
            haul.normalizeLifecycle();
            return haul;
        }

        private void normalizeLifecycle() {
            if (lifecycle == Lifecycle.PARTIALLY_RESOLVED || lifecycle == Lifecycle.RESOLVED) {
                refreshResolutionLifecycle();
            }
        }

        private static final class EntryAccumulator {
            private final String entryId;
            private final ItemStack stack;
            private SoulOrbRarity rarity;
            private final Disposition disposition;

            private EntryAccumulator(
                String entryId,
                ItemStack stack,
                SoulOrbRarity rarity,
                Disposition disposition
            ) {
                this.entryId = entryId;
                this.stack = stack;
                this.rarity = rarity;
                this.disposition = disposition;
            }
        }
    }

    private static final class OrbRecord {
        private final UUID orbId;
        private final UUID entityId;
        private final SoulOrbRarity rarity;
        private boolean collected;
        private final List<DropEntry> entries;
        private final String encounterClass;

        private OrbRecord(
            UUID orbId,
            UUID entityId,
            SoulOrbRarity rarity,
            boolean collected,
            List<DropEntry> entries,
            String encounterClass
        ) {
            this.orbId = orbId;
            this.entityId = entityId;
            this.rarity = rarity;
            this.collected = collected;
            this.entries = new ArrayList<>(entries);
            this.encounterClass = encounterClass == null || encounterClass.isBlank() ? "UNKNOWN" : encounterClass;
        }

        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("OrbId", orbId);
            tag.putUUID("EntityId", entityId);
            tag.putString("Rarity", rarity.name());
            tag.putBoolean("Collected", collected);
            tag.putString("EncounterClass", encounterClass);
            ListTag dropEntries = new ListTag();
            for (DropEntry entry : entries) dropEntries.add(entry.save(registries));
            tag.put("Entries", dropEntries);
            return tag;
        }

        private static OrbRecord load(CompoundTag tag, HolderLookup.Provider registries) {
            if (!tag.hasUUID("OrbId") || !tag.hasUUID("EntityId")) return null;
            UUID orbId = tag.getUUID("OrbId");
            List<DropEntry> entries = new ArrayList<>();
            if (tag.contains("Entries", Tag.TAG_LIST)) {
                ListTag entryTags = tag.getList("Entries", Tag.TAG_COMPOUND);
                for (int index = 0; index < entryTags.size(); index++) {
                    DropEntry entry = DropEntry.load(entryTags.getCompound(index), registries);
                    if (entry != null) entries.add(entry);
                }
            } else {
                ListTag legacyDrops = tag.getList("RolledDrops", Tag.TAG_COMPOUND);
                for (int index = 0; index < legacyDrops.size(); index++) {
                    int entryIndex = index;
                    ItemStack.parse(registries, legacyDrops.getCompound(index))
                        .filter(stack -> !stack.isEmpty())
                        .ifPresent(stack -> entries.add(new DropEntry(
                            entryId(orbId, entryIndex), stack, Disposition.UNRESOLVED
                        )));
                }
            }
            return new OrbRecord(
                orbId,
                tag.getUUID("EntityId"),
                SoulOrbRarity.parse(tag.getString("Rarity")),
                tag.getBoolean("Collected"),
                entries,
                tag.getString("EncounterClass")
            );
        }
    }

    private static final class DropEntry {
        private final String entryId;
        private final ItemStack stack;
        private Disposition disposition;

        private DropEntry(String entryId, ItemStack stack, Disposition disposition) {
            this.entryId = entryId;
            this.stack = stack.copy();
            this.disposition = disposition;
        }

        private CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("EntryId", entryId);
            tag.put("Stack", stack.save(registries));
            tag.putString("Disposition", disposition.name());
            return tag;
        }

        private static DropEntry load(CompoundTag tag, HolderLookup.Provider registries) {
            if (!tag.contains("Stack", Tag.TAG_COMPOUND)) return null;
            return ItemStack.parse(registries, tag.getCompound("Stack"))
                .filter(stack -> !stack.isEmpty())
                .map(stack -> new DropEntry(
                    tag.getString("EntryId"), stack, Disposition.parse(tag.getString("Disposition"))
                ))
                .orElse(null);
        }
    }
}
