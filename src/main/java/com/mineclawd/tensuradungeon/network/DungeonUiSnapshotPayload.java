package com.mineclawd.tensuradungeon.network;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.PreparedFloorModifierPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonUiSnapshotPayload(
    int schemaVersion,
    long revision,
    RunSummary run,
    ProgressSummary progress,
    IntegrationSummary integrations,
    CurrencySummary currency,
    CompletionSummary latestCompletion,
    List<LocalFloorRecord> localFloorRecords,
    int unclaimedMailboxCount,
    List<MailboxPackage> mailbox,
    int modifierCatalogVersion,
    List<ModifierCatalogEntry> modifierCatalog,
    PreparedModifierPlanSummary preparedModifierPlan,
    String message
) implements CustomPacketPayload {
    public static final int CURRENT_SCHEMA = 7;
    public static final Type<DungeonUiSnapshotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, "native_gui_snapshot")
    );
    public static final StreamCodec<FriendlyByteBuf, DungeonUiSnapshotPayload> STREAM_CODEC =
        StreamCodec.of(DungeonUiSnapshotPayload::write, DungeonUiSnapshotPayload::read);

    public DungeonUiSnapshotPayload {
        schemaVersion = Math.max(0, schemaVersion);
        revision = Math.max(0L, revision);
        run = run == null ? RunSummary.empty() : run;
        progress = progress == null ? ProgressSummary.empty() : progress;
        integrations = integrations == null ? IntegrationSummary.unavailable() : integrations;
        currency = currency == null ? CurrencySummary.empty() : currency;
        latestCompletion = latestCompletion == null ? CompletionSummary.empty() : latestCompletion;
        localFloorRecords = localFloorRecords == null ? List.of()
            : List.copyOf(localFloorRecords.subList(0, Math.min(16, localFloorRecords.size())));
        mailbox = mailbox == null ? List.of() : List.copyOf(mailbox.subList(0, Math.min(64, mailbox.size())));
        modifierCatalogVersion = Math.max(0, modifierCatalogVersion);
        modifierCatalog = modifierCatalog == null ? List.of()
            : List.copyOf(modifierCatalog.subList(0, Math.min(96, modifierCatalog.size())));
        preparedModifierPlan = preparedModifierPlan == null
            ? PreparedModifierPlanSummary.empty()
            : preparedModifierPlan;
        unclaimedMailboxCount = Math.max(
            Math.max(0, unclaimedMailboxCount),
            (int) mailbox.stream().filter(entry -> !entry.claimed()).count()
        );
        message = bounded(message, 256);
    }

    public DungeonUiSnapshotPayload(
        int schemaVersion,
        long revision,
        RunSummary run,
        ProgressSummary progress,
        IntegrationSummary integrations,
        CurrencySummary currency,
        CompletionSummary latestCompletion,
        List<LocalFloorRecord> localFloorRecords,
        int unclaimedMailboxCount,
        List<MailboxPackage> mailbox,
        String message
    ) {
        this(
            schemaVersion, revision, run, progress, integrations, currency, latestCompletion,
            localFloorRecords, unclaimedMailboxCount, mailbox, 0, List.of(), null, message
        );
    }

    private static void write(FriendlyByteBuf buffer, DungeonUiSnapshotPayload payload) {
        buffer.writeVarInt(payload.schemaVersion);
        buffer.writeVarLong(payload.revision);
        payload.run.write(buffer);
        payload.progress.write(buffer);
        payload.integrations.write(buffer);
        payload.currency.write(buffer);
        payload.latestCompletion.write(buffer);
        buffer.writeVarInt(payload.localFloorRecords.size());
        payload.localFloorRecords.forEach(record -> record.write(buffer));
        buffer.writeVarInt(payload.unclaimedMailboxCount);
        buffer.writeVarInt(payload.mailbox.size());
        payload.mailbox.forEach(entry -> entry.write(buffer));
        buffer.writeVarInt(payload.modifierCatalogVersion);
        buffer.writeVarInt(payload.modifierCatalog.size());
        payload.modifierCatalog.forEach(entry -> entry.write(buffer));
        payload.preparedModifierPlan.write(buffer, payload.schemaVersion);
        buffer.writeUtf(payload.message, 256);
    }

    private static DungeonUiSnapshotPayload read(FriendlyByteBuf buffer) {
        int schema = buffer.readVarInt();
        long revision = buffer.readVarLong();
        RunSummary run = RunSummary.read(buffer);
        ProgressSummary progress = ProgressSummary.read(buffer);
        IntegrationSummary integrations = IntegrationSummary.read(buffer);
        CurrencySummary currency = CurrencySummary.read(buffer);
        CompletionSummary latestCompletion = CompletionSummary.read(buffer);
        int localRecordCount = readBoundedCount(buffer, 16, "local floor records");
        List<LocalFloorRecord> localFloorRecords = new ArrayList<>(localRecordCount);
        for (int i = 0; i < localRecordCount; i++) {
            localFloorRecords.add(LocalFloorRecord.read(buffer));
        }
        int unclaimedMailboxCount = buffer.readVarInt();
        int count = readBoundedCount(buffer, 64, "mailbox");
        List<MailboxPackage> mailbox = new ArrayList<>(count);
        for (int i = 0; i < count; i++) mailbox.add(MailboxPackage.read(buffer));
        int modifierCatalogVersion = buffer.readVarInt();
        int modifierCount = readBoundedCount(buffer, 96, "modifier catalog");
        List<ModifierCatalogEntry> modifierCatalog = new ArrayList<>(modifierCount);
        for (int i = 0; i < modifierCount; i++) {
            modifierCatalog.add(ModifierCatalogEntry.read(buffer));
        }
        PreparedModifierPlanSummary preparedModifierPlan = PreparedModifierPlanSummary.read(buffer, schema);
        return new DungeonUiSnapshotPayload(
            schema, revision, run, progress, integrations, currency, latestCompletion,
            localFloorRecords, unclaimedMailboxCount,
            mailbox, modifierCatalogVersion, modifierCatalog, preparedModifierPlan,
            buffer.readUtf(256)
        );
    }

    public record ModifierCatalogEntry(
        String id,
        String displayName,
        String alignment,
        String classification,
        String status,
        int minFloor,
        int weight,
        String description,
        List<String> counters,
        double rewardMultiplier,
        String iconKey,
        String nativeSkillId,
        String wardCategory,
        String availability,
        String availabilityReason
    ) {
        public ModifierCatalogEntry(
            String id,
            String displayName,
            String alignment,
            String classification,
            String status,
            int minFloor,
            int weight,
            String description,
            List<String> counters,
            double rewardMultiplier,
            String iconKey,
            String nativeSkillId,
            String wardCategory
        ) {
            this(
                id, displayName, alignment, classification, status, minFloor, weight, description,
                counters, rewardMultiplier, iconKey, nativeSkillId, wardCategory,
                "AVAILABLE", "NONE"
            );
        }

        public ModifierCatalogEntry {
            id = bounded(id, 64);
            displayName = bounded(displayName, 64);
            alignment = bounded(alignment, 24);
            classification = bounded(classification, 24);
            status = bounded(status, 32);
            minFloor = Math.max(1, minFloor);
            weight = Math.max(0, weight);
            description = bounded(description, 256);
            counters = counters == null ? List.of()
                : List.copyOf(counters.subList(0, Math.min(6, counters.size())));
            rewardMultiplier = Math.max(0.0D, rewardMultiplier);
            iconKey = bounded(iconKey, 64);
            nativeSkillId = bounded(nativeSkillId, 96);
            wardCategory = bounded(wardCategory, 48);
            availability = bounded(availability, 32);
            availabilityReason = bounded(availabilityReason, 64);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 64);
            buffer.writeUtf(displayName, 64);
            buffer.writeUtf(alignment, 24);
            buffer.writeUtf(classification, 24);
            buffer.writeUtf(status, 32);
            buffer.writeVarInt(minFloor);
            buffer.writeVarInt(weight);
            buffer.writeUtf(description, 256);
            buffer.writeVarInt(counters.size());
            counters.forEach(counter -> buffer.writeUtf(counter, 128));
            buffer.writeDouble(rewardMultiplier);
            buffer.writeUtf(iconKey, 64);
            buffer.writeUtf(nativeSkillId, 96);
            buffer.writeUtf(wardCategory, 48);
            buffer.writeUtf(availability, 32);
            buffer.writeUtf(availabilityReason, 64);
        }

        static ModifierCatalogEntry read(FriendlyByteBuf buffer) {
            String id = buffer.readUtf(64);
            String displayName = buffer.readUtf(64);
            String alignment = buffer.readUtf(24);
            String classification = buffer.readUtf(24);
            String status = buffer.readUtf(32);
            int minFloor = buffer.readVarInt();
            int weight = buffer.readVarInt();
            String description = buffer.readUtf(256);
            int counterCount = readBoundedCount(buffer, 6, "modifier counters");
            List<String> counters = new ArrayList<>(counterCount);
            for (int index = 0; index < counterCount; index++) counters.add(buffer.readUtf(128));
            return new ModifierCatalogEntry(
                id, displayName, alignment, classification, status, minFloor, weight,
                description, counters, buffer.readDouble(), buffer.readUtf(64),
                buffer.readUtf(96), buffer.readUtf(48), buffer.readUtf(32), buffer.readUtf(64)
            );
        }
    }

    public record PreparedModifierPlanSummary(
        int floor,
        long seed,
        int algorithmVersion,
        int catalogVersion,
        List<String> hostileModifierIds,
        String boonModifierId,
        String reason,
        String validationStatus,
        String validationReason,
        String roomPressureId,
        KinPreview kin,
        BossPreview boss
    ) {
        public PreparedModifierPlanSummary(
            int floor,
            long seed,
            int algorithmVersion,
            int catalogVersion,
            List<String> hostileModifierIds,
            String boonModifierId,
            String reason,
            String validationStatus,
            String validationReason
        ) {
            this(
                floor, seed, algorithmVersion, catalogVersion, hostileModifierIds, boonModifierId,
                reason, validationStatus, validationReason, "", KinPreview.empty(), BossPreview.empty()
            );
        }

        public PreparedModifierPlanSummary(
            int floor,
            long seed,
            int algorithmVersion,
            List<String> hostileModifierIds,
            String boonModifierId,
            String reason
        ) {
            this(floor, seed, algorithmVersion, 0, hostileModifierIds, boonModifierId, reason, "", "");
        }

        public PreparedModifierPlanSummary {
            floor = Math.max(0, floor);
            algorithmVersion = Math.max(0, algorithmVersion);
            catalogVersion = Math.max(0, catalogVersion);
            hostileModifierIds = hostileModifierIds == null ? List.of()
                : List.copyOf(hostileModifierIds.subList(
                    0,
                    Math.min(PreparedFloorModifierPlan.MAX_HOSTILE_MODIFIERS, hostileModifierIds.size())
                ));
            boonModifierId = bounded(boonModifierId, 64);
            reason = bounded(reason, 48);
            validationStatus = bounded(validationStatus, 32);
            validationReason = bounded(validationReason, 64);
            roomPressureId = bounded(roomPressureId, 64);
            kin = kin == null ? KinPreview.empty() : kin;
            boss = boss == null ? BossPreview.empty() : boss;
        }

        static PreparedModifierPlanSummary empty() {
            return new PreparedModifierPlanSummary(
                0, 0L, 0, 0, List.of(), "", "NONE", "", "", "",
                KinPreview.empty(), BossPreview.empty()
            );
        }

        void write(FriendlyByteBuf buffer, int schemaVersion) {
            buffer.writeVarInt(floor);
            buffer.writeLong(seed);
            buffer.writeVarInt(algorithmVersion);
            buffer.writeVarInt(catalogVersion);
            buffer.writeVarInt(hostileModifierIds.size());
            hostileModifierIds.forEach(id -> buffer.writeUtf(id, 64));
            buffer.writeUtf(boonModifierId, 64);
            buffer.writeUtf(reason, 48);
            buffer.writeUtf(validationStatus, 32);
            buffer.writeUtf(validationReason, 64);
            if (schemaVersion >= 7) {
                buffer.writeUtf(roomPressureId, 64);
                kin.write(buffer);
                boss.write(buffer);
            }
        }

        static PreparedModifierPlanSummary read(FriendlyByteBuf buffer, int schemaVersion) {
            int floor = buffer.readVarInt();
            long seed = buffer.readLong();
            int algorithmVersion = buffer.readVarInt();
            int catalogVersion = buffer.readVarInt();
            int modifierCount = readBoundedCount(
                buffer, PreparedFloorModifierPlan.MAX_HOSTILE_MODIFIERS, "prepared modifiers"
            );
            List<String> modifiers = new ArrayList<>(modifierCount);
            for (int index = 0; index < modifierCount; index++) modifiers.add(buffer.readUtf(64));
            String boonModifierId = buffer.readUtf(64);
            String reason = buffer.readUtf(48);
            String validationStatus = buffer.readUtf(32);
            String validationReason = buffer.readUtf(64);
            String roomPressureId = "";
            KinPreview kin = KinPreview.empty();
            BossPreview boss = BossPreview.empty();
            if (schemaVersion >= 7) {
                roomPressureId = buffer.readUtf(64);
                kin = KinPreview.read(buffer);
                boss = BossPreview.read(buffer);
            }
            return new PreparedModifierPlanSummary(
                floor, seed, algorithmVersion, catalogVersion, modifiers, boonModifierId,
                reason, validationStatus, validationReason,
                roomPressureId, kin, boss
            );
        }
    }

    public record KinPreview(
        String selectedFamily,
        long seed,
        int catalogVersion,
        List<String> roleLanes,
        List<String> candidateIds,
        List<String> rejectionReasons,
        List<String> exclusions
    ) {
        private static final int MAX_ROLE_LANES = 8;
        private static final int MAX_CANDIDATES = 128;
        private static final int MAX_REJECTIONS = 128;
        private static final int MAX_EXCLUSIONS = 8;

        public KinPreview {
            selectedFamily = bounded(selectedFamily, 48);
            catalogVersion = Math.max(0, catalogVersion);
            roleLanes = boundedStrings(roleLanes, MAX_ROLE_LANES, 48);
            candidateIds = boundedStrings(candidateIds, MAX_CANDIDATES, 96);
            rejectionReasons = boundedStrings(rejectionReasons, MAX_REJECTIONS, 128);
            exclusions = boundedStrings(exclusions, MAX_EXCLUSIONS, 96);
        }

        static KinPreview empty() {
            return new KinPreview("", 0L, 0, List.of(), List.of(), List.of(), List.of());
        }

        public boolean present() {
            return !selectedFamily.isBlank()
                || !candidateIds.isEmpty()
                || !rejectionReasons.isEmpty();
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(selectedFamily, 48);
            buffer.writeLong(seed);
            buffer.writeVarInt(catalogVersion);
            writeStrings(buffer, roleLanes, 48);
            writeStrings(buffer, candidateIds, 96);
            writeStrings(buffer, rejectionReasons, 128);
            writeStrings(buffer, exclusions, 96);
        }

        static KinPreview read(FriendlyByteBuf buffer) {
            return new KinPreview(
                buffer.readUtf(48),
                buffer.readLong(),
                buffer.readVarInt(),
                readStrings(buffer, MAX_ROLE_LANES, 48, "Kin role lanes"),
                readStrings(buffer, MAX_CANDIDATES, 96, "Kin candidates"),
                readStrings(buffer, MAX_REJECTIONS, 128, "Kin rejection reasons"),
                readStrings(buffer, MAX_EXCLUSIONS, 96, "Kin exclusions")
            );
        }
    }

    public record BossPreview(
        boolean slotAvailable,
        boolean milestone,
        int catalogVersion,
        String affinityKinGroup,
        String affinityTheme,
        List<String> candidateIds,
        List<String> matchingCandidateIds,
        String selectedBossId,
        String resolutionReason
    ) {
        private static final int MAX_CANDIDATES = 128;

        public BossPreview {
            milestone = slotAvailable && milestone;
            catalogVersion = Math.max(0, catalogVersion);
            affinityKinGroup = bounded(affinityKinGroup, 48);
            affinityTheme = bounded(affinityTheme, 64);
            candidateIds = boundedStrings(candidateIds, MAX_CANDIDATES, 96);
            matchingCandidateIds = boundedStrings(matchingCandidateIds, MAX_CANDIDATES, 96);
            selectedBossId = bounded(selectedBossId, 96);
            resolutionReason = bounded(resolutionReason, 96);
        }

        static BossPreview empty() {
            return new BossPreview(false, false, 0, "", "", List.of(), List.of(), "", "NONE");
        }

        public boolean present() {
            return slotAvailable
                || !selectedBossId.isBlank()
                || !resolutionReason.isBlank() && !"NONE".equals(resolutionReason);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(slotAvailable);
            buffer.writeBoolean(milestone);
            buffer.writeVarInt(catalogVersion);
            buffer.writeUtf(affinityKinGroup, 48);
            buffer.writeUtf(affinityTheme, 64);
            writeStrings(buffer, candidateIds, 96);
            writeStrings(buffer, matchingCandidateIds, 96);
            buffer.writeUtf(selectedBossId, 96);
            buffer.writeUtf(resolutionReason, 96);
        }

        static BossPreview read(FriendlyByteBuf buffer) {
            return new BossPreview(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readUtf(48),
                buffer.readUtf(64),
                readStrings(buffer, MAX_CANDIDATES, 96, "Boss candidates"),
                readStrings(buffer, MAX_CANDIDATES, 96, "Boss affinity matches"),
                buffer.readUtf(96),
                buffer.readUtf(96)
            );
        }
    }

    private static List<String> boundedStrings(List<String> values, int maximumCount, int maximumLength) {
        if (values == null || values.isEmpty()) return List.of();
        return List.copyOf(values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> bounded(value, maximumLength))
            .limit(maximumCount)
            .toList());
    }

    private static void writeStrings(FriendlyByteBuf buffer, List<String> values, int maximumLength) {
        buffer.writeVarInt(values.size());
        values.forEach(value -> buffer.writeUtf(value, maximumLength));
    }

    private static List<String> readStrings(
        FriendlyByteBuf buffer,
        int maximumCount,
        int maximumLength,
        String field
    ) {
        int count = readBoundedCount(buffer, maximumCount, field);
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(buffer.readUtf(maximumLength));
        return List.copyOf(values);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record RunSummary(
        boolean active,
        boolean completed,
        boolean failed,
        int floor,
        String sessionId,
        long seed,
        int completedRooms,
        int targetRooms,
        String mode,
        List<String> modifierIds
    ) {
        public RunSummary {
            floor = Math.max(0, floor);
            sessionId = bounded(sessionId, 96);
            completedRooms = Math.max(0, completedRooms);
            targetRooms = Math.max(0, targetRooms);
            mode = bounded(mode, 48);
            modifierIds = modifierIds == null ? List.of()
                : List.copyOf(modifierIds.subList(0, Math.min(16, modifierIds.size())));
        }

        static RunSummary empty() {
            return new RunSummary(false, false, false, 0, "", 0L, 0, 0, "NONE", List.of());
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(active);
            buffer.writeBoolean(completed);
            buffer.writeBoolean(failed);
            buffer.writeVarInt(floor);
            buffer.writeUtf(sessionId, 96);
            buffer.writeLong(seed);
            buffer.writeVarInt(completedRooms);
            buffer.writeVarInt(targetRooms);
            buffer.writeUtf(mode, 48);
            buffer.writeVarInt(modifierIds.size());
            modifierIds.forEach(id -> buffer.writeUtf(id, 64));
        }

        static RunSummary read(FriendlyByteBuf buffer) {
            boolean active = buffer.readBoolean();
            boolean completed = buffer.readBoolean();
            boolean failed = buffer.readBoolean();
            int floor = buffer.readVarInt();
            String session = buffer.readUtf(96);
            long seed = buffer.readLong();
            int completedRooms = buffer.readVarInt();
            int targetRooms = buffer.readVarInt();
            String mode = buffer.readUtf(48);
            int count = readBoundedCount(buffer, 16, "modifiers");
            List<String> modifiers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) modifiers.add(buffer.readUtf(64));
            return new RunSummary(active, completed, failed, floor, session, seed,
                completedRooms, targetRooms, mode, modifiers);
        }
    }

    public record ProgressSummary(int highestUnlockedFloor, int selectedFloor, List<Integer> completedFloors) {
        public ProgressSummary {
            highestUnlockedFloor = Math.max(1, highestUnlockedFloor);
            selectedFloor = Math.max(1, Math.min(highestUnlockedFloor, selectedFloor));
            completedFloors = completedFloors == null ? List.of()
                : List.copyOf(completedFloors.subList(0, Math.min(99, completedFloors.size())));
        }

        static ProgressSummary empty() {
            return new ProgressSummary(1, 1, List.of());
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(highestUnlockedFloor);
            buffer.writeVarInt(selectedFloor);
            buffer.writeVarInt(completedFloors.size());
            completedFloors.forEach(buffer::writeVarInt);
        }

        static ProgressSummary read(FriendlyByteBuf buffer) {
            int highest = buffer.readVarInt();
            int selected = buffer.readVarInt();
            int count = readBoundedCount(buffer, 99, "completed floors");
            List<Integer> completed = new ArrayList<>(count);
            for (int i = 0; i < count; i++) completed.add(buffer.readVarInt());
            return new ProgressSummary(highest, selected, completed);
        }
    }

    public record IntegrationSummary(
        String epGrowthStatus,
        String checkpointStatus,
        String recordingCrystalStatus
    ) {
        public IntegrationSummary {
            epGrowthStatus = bounded(epGrowthStatus, 64);
            checkpointStatus = bounded(checkpointStatus, 64);
            recordingCrystalStatus = bounded(recordingCrystalStatus, 64);
        }

        static IntegrationSummary unavailable() {
            return new IntegrationSummary("UNAVAILABLE_NOT_TRACKED", "NOT_IMPLEMENTED", "NOT_IMPLEMENTED");
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(epGrowthStatus, 64);
            buffer.writeUtf(checkpointStatus, 64);
            buffer.writeUtf(recordingCrystalStatus, 64);
        }

        static IntegrationSummary read(FriendlyByteBuf buffer) {
            return new IntegrationSummary(buffer.readUtf(64), buffer.readUtf(64), buffer.readUtf(64));
        }
    }

    public record MailboxPackage(
        long rewardId,
        int floor,
        boolean claimed,
        String sourceType,
        String rewardTier,
        String summary,
        int maxEpAmount
    ) {
        public MailboxPackage {
            rewardId = Math.max(0L, rewardId);
            floor = Math.max(0, floor);
            sourceType = bounded(sourceType, 64);
            rewardTier = bounded(rewardTier, 48);
            summary = bounded(summary, 256);
            maxEpAmount = Math.max(0, maxEpAmount);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeVarLong(rewardId);
            buffer.writeVarInt(floor);
            buffer.writeBoolean(claimed);
            buffer.writeUtf(sourceType, 64);
            buffer.writeUtf(rewardTier, 48);
            buffer.writeUtf(summary, 256);
            buffer.writeVarInt(maxEpAmount);
        }

        static MailboxPackage read(FriendlyByteBuf buffer) {
            return new MailboxPackage(buffer.readVarLong(), buffer.readVarInt(), buffer.readBoolean(),
                buffer.readUtf(64), buffer.readUtf(48), buffer.readUtf(256), buffer.readVarInt());
        }
    }

    public record CurrencySummary(long bronze, long silver, long gold, long stellarGold) {
        public CurrencySummary {
            bronze = Math.max(0L, bronze);
            silver = Math.max(0L, silver);
            gold = Math.max(0L, gold);
            stellarGold = Math.max(0L, stellarGold);
        }

        static CurrencySummary empty() {
            return new CurrencySummary(0L, 0L, 0L, 0L);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeVarLong(bronze);
            buffer.writeVarLong(silver);
            buffer.writeVarLong(gold);
            buffer.writeVarLong(stellarGold);
        }

        static CurrencySummary read(FriendlyByteBuf buffer) {
            return new CurrencySummary(buffer.readVarLong(), buffer.readVarLong(),
                buffer.readVarLong(), buffer.readVarLong());
        }
    }

    public record CompletionSummary(
        boolean available,
        String playerName,
        CompletionContext context,
        CompletionPerformance performance,
        CompletionRewards rewards
    ) {
        public CompletionSummary {
            playerName = bounded(playerName, 64);
            context = context == null ? CompletionContext.empty() : context;
            performance = performance == null ? CompletionPerformance.empty() : performance;
            rewards = rewards == null ? CompletionRewards.empty() : rewards;
        }

        static CompletionSummary empty() {
            return new CompletionSummary(false, "", null, null, null);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(available);
            buffer.writeUtf(playerName, 64);
            context.write(buffer);
            performance.write(buffer);
            rewards.write(buffer);
        }

        static CompletionSummary read(FriendlyByteBuf buffer) {
            return new CompletionSummary(
                buffer.readBoolean(),
                buffer.readUtf(64),
                CompletionContext.read(buffer),
                CompletionPerformance.read(buffer),
                CompletionRewards.read(buffer)
            );
        }
    }

    public record CompletionContext(
        String sessionId,
        int floor,
        String mode,
        String objective,
        int roomCount,
        int partySize,
        String partyComposition,
        List<String> modifierIds
    ) {
        public CompletionContext {
            sessionId = bounded(sessionId, 96);
            floor = Math.max(0, floor);
            mode = bounded(mode, 48);
            objective = bounded(objective, 96);
            roomCount = Math.max(0, roomCount);
            partySize = Math.max(0, Math.min(4, partySize));
            partyComposition = bounded(partyComposition, 96);
            modifierIds = modifierIds == null ? List.of()
                : List.copyOf(modifierIds.subList(0, Math.min(16, modifierIds.size())));
        }

        static CompletionContext empty() {
            return new CompletionContext(
                "", 0, "NONE", "UNKNOWN", 0, 0, "UNAVAILABLE_NOT_TRACKED", List.of()
            );
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(sessionId, 96);
            buffer.writeVarInt(floor);
            buffer.writeUtf(mode, 48);
            buffer.writeUtf(objective, 96);
            buffer.writeVarInt(roomCount);
            buffer.writeVarInt(partySize);
            buffer.writeUtf(partyComposition, 96);
            buffer.writeVarInt(modifierIds.size());
            modifierIds.forEach(id -> buffer.writeUtf(id, 64));
        }

        static CompletionContext read(FriendlyByteBuf buffer) {
            String sessionId = buffer.readUtf(96);
            int floor = buffer.readVarInt();
            String mode = buffer.readUtf(48);
            String objective = buffer.readUtf(96);
            int roomCount = buffer.readVarInt();
            int partySize = buffer.readVarInt();
            String partyComposition = buffer.readUtf(96);
            int modifierCount = readBoundedCount(buffer, 16, "completion modifiers");
            List<String> modifierIds = new ArrayList<>(modifierCount);
            for (int index = 0; index < modifierCount; index++) {
                modifierIds.add(buffer.readUtf(64));
            }
            return new CompletionContext(
                sessionId, floor, mode, objective, roomCount, partySize, partyComposition, modifierIds
            );
        }
    }

    public record CompletionPerformance(
        long clearTicks,
        String timingStatus,
        String result,
        String clearRank,
        String rankStatus,
        String deathsStatus,
        String checkpointStatus,
        double entryEp,
        double currentEp,
        double epGain,
        String epStatus
    ) {
        public CompletionPerformance {
            clearTicks = Math.max(0L, clearTicks);
            timingStatus = bounded(timingStatus, 48);
            result = bounded(result, 24);
            clearRank = bounded(clearRank, 8);
            rankStatus = bounded(rankStatus, 48);
            deathsStatus = bounded(deathsStatus, 48);
            checkpointStatus = bounded(checkpointStatus, 48);
            entryEp = Math.max(0.0D, entryEp);
            currentEp = Math.max(0.0D, currentEp);
            epGain = Math.max(0.0D, epGain);
            epStatus = bounded(epStatus, 48);
        }

        static CompletionPerformance empty() {
            return new CompletionPerformance(
                0L, "UNAVAILABLE", "UNKNOWN", "C", "DEFERRED_TARGETS",
                "UNAVAILABLE_NOT_TRACKED", "NOT_IMPLEMENTED", 0, 0, 0, "UNAVAILABLE"
            );
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeVarLong(clearTicks);
            buffer.writeUtf(timingStatus, 48);
            buffer.writeUtf(result, 24);
            buffer.writeUtf(clearRank, 8);
            buffer.writeUtf(rankStatus, 48);
            buffer.writeUtf(deathsStatus, 48);
            buffer.writeUtf(checkpointStatus, 48);
            buffer.writeDouble(entryEp);
            buffer.writeDouble(currentEp);
            buffer.writeDouble(epGain);
            buffer.writeUtf(epStatus, 48);
        }

        static CompletionPerformance read(FriendlyByteBuf buffer) {
            return new CompletionPerformance(
                buffer.readVarLong(), buffer.readUtf(48), buffer.readUtf(24), buffer.readUtf(8),
                buffer.readUtf(48), buffer.readUtf(48), buffer.readUtf(48),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readUtf(48)
            );
        }
    }

    public record CompletionRewards(
        long baseCurrencyBronze,
        long bonusCurrencyBronze,
        long totalCurrencyBronze,
        int commonDrops,
        int uncommonDrops,
        int rareDrops,
        int epicDrops,
        int crystalCount
    ) {
        public CompletionRewards {
            baseCurrencyBronze = Math.max(0L, baseCurrencyBronze);
            bonusCurrencyBronze = Math.max(0L, bonusCurrencyBronze);
            totalCurrencyBronze = Math.max(0L, totalCurrencyBronze);
            commonDrops = Math.max(0, commonDrops);
            uncommonDrops = Math.max(0, uncommonDrops);
            rareDrops = Math.max(0, rareDrops);
            epicDrops = Math.max(0, epicDrops);
            crystalCount = Math.max(0, crystalCount);
        }

        static CompletionRewards empty() {
            return new CompletionRewards(0, 0, 0, 0, 0, 0, 0, 0);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeVarLong(baseCurrencyBronze);
            buffer.writeVarLong(bonusCurrencyBronze);
            buffer.writeVarLong(totalCurrencyBronze);
            buffer.writeVarInt(commonDrops);
            buffer.writeVarInt(uncommonDrops);
            buffer.writeVarInt(rareDrops);
            buffer.writeVarInt(epicDrops);
            buffer.writeVarInt(crystalCount);
        }

        static CompletionRewards read(FriendlyByteBuf buffer) {
            return new CompletionRewards(
                buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt()
            );
        }
    }

    public record LocalFloorRecord(
        String playerId,
        String playerName,
        int floor,
        long clearTicks,
        String clearRank
    ) {
        public LocalFloorRecord {
            playerId = bounded(playerId, 36);
            playerName = bounded(playerName, 64);
            floor = Math.max(0, floor);
            clearTicks = Math.max(0L, clearTicks);
            clearRank = bounded(clearRank, 8);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(playerId, 36);
            buffer.writeUtf(playerName, 64);
            buffer.writeVarInt(floor);
            buffer.writeVarLong(clearTicks);
            buffer.writeUtf(clearRank, 8);
        }

        static LocalFloorRecord read(FriendlyByteBuf buffer) {
            return new LocalFloorRecord(
                buffer.readUtf(36), buffer.readUtf(64), buffer.readVarInt(),
                buffer.readVarLong(), buffer.readUtf(8)
            );
        }
    }

    private static String bounded(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static int readBoundedCount(FriendlyByteBuf buffer, int maximum, String field) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid native GUI " + field + " count: " + count);
        }
        return count;
    }
}
