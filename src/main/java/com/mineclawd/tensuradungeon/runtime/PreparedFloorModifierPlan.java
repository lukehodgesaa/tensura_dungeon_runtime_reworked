package com.mineclawd.tensuradungeon.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonBossCatalog;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonBossResolution;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinCatalog;
import com.mineclawd.tensuradungeon.runtime.enemy.DungeonEnemyKinSelection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public record PreparedFloorModifierPlan(
    int floor,
    long seed,
    int algorithmVersion,
    int catalogVersion,
    List<String> hostileModifierIds,
    String boonModifierId,
    String reason,
    String validationStatus,
    String validationReason,
    List<String> covenantIds,
    String greedPactBurdenType,
    int greedPactBurdenRoom,
    String roomPressureId,
    String kinGroup,
    long kinSeed,
    int kinCatalogVersion,
    List<String> kinCandidateIds,
    List<String> kinRejectionReasons,
    boolean bossSlotAvailable,
    boolean bossMilestone,
    int bossCatalogVersion,
    String bossAffinityKinGroup,
    String bossAffinityTheme,
    List<String> bossCandidateIds,
    List<String> bossMatchingCandidateIds,
    String selectedBossId,
    String bossResolutionReason
) {
    public static final int CURRENT_ALGORITHM = 2;
    public static final int MAX_HOSTILE_MODIFIERS = 10;
    public static final int MAX_COVENANTS = 4;
    public static final int MAX_BURDEN_ROOM = 1_000_000;
    public static final int MAX_KIN_CANDIDATES = DungeonEnemyKinSelection.MAX_CANDIDATES;
    public static final int MAX_KIN_REJECTION_REASONS = DungeonEnemyKinSelection.MAX_REJECTION_REASONS;
    public static final int MAX_BOSS_CANDIDATES = DungeonBossResolution.MAX_CANDIDATES;

    public PreparedFloorModifierPlan(
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
            floor,
            seed,
            algorithmVersion,
            catalogVersion,
            hostileModifierIds,
            boonModifierId,
            reason,
            validationStatus,
            validationReason,
            List.of(),
            "",
            -1,
            "",
            "",
            0L,
            0,
            List.of(),
            List.of(),
            false,
            false,
            0,
            "",
            "",
            List.of(),
            List.of(),
            "",
            ""
        );
    }

    public PreparedFloorModifierPlan(
        int floor,
        long seed,
        int algorithmVersion,
        int catalogVersion,
        List<String> hostileModifierIds,
        String boonModifierId,
        String reason,
        String validationStatus,
        String validationReason,
        List<String> covenantIds,
        String greedPactBurdenType,
        int greedPactBurdenRoom
    ) {
        this(
            floor,
            seed,
            algorithmVersion,
            catalogVersion,
            hostileModifierIds,
            boonModifierId,
            reason,
            validationStatus,
            validationReason,
            covenantIds,
            greedPactBurdenType,
            greedPactBurdenRoom,
            "",
            "",
            0L,
            0,
            List.of(),
            List.of(),
            false,
            false,
            0,
            "",
            "",
            List.of(),
            List.of(),
            "",
            ""
        );
    }

    public PreparedFloorModifierPlan(
        int floor,
        long seed,
        int algorithmVersion,
        int catalogVersion,
        List<String> hostileModifierIds,
        String boonModifierId,
        String reason,
        String validationStatus,
        String validationReason,
        List<String> covenantIds,
        String greedPactBurdenType,
        int greedPactBurdenRoom,
        String kinGroup,
        long kinSeed,
        int kinCatalogVersion,
        List<String> kinCandidateIds,
        List<String> kinRejectionReasons
    ) {
        this(
            floor,
            seed,
            algorithmVersion,
            catalogVersion,
            hostileModifierIds,
            boonModifierId,
            reason,
            validationStatus,
            validationReason,
            covenantIds,
            greedPactBurdenType,
            greedPactBurdenRoom,
            "",
            kinGroup,
            kinSeed,
            kinCatalogVersion,
            kinCandidateIds,
            kinRejectionReasons,
            false,
            false,
            0,
            "",
            "",
            List.of(),
            List.of(),
            "",
            ""
        );
    }

    public PreparedFloorModifierPlan(
        int floor,
        long seed,
        int algorithmVersion,
        List<String> hostileModifierIds,
        String boonModifierId,
        String reason
    ) {
        this(
            floor,
            seed,
            algorithmVersion,
            DungeonModifierCatalog.CATALOG_VERSION,
            hostileModifierIds,
            boonModifierId,
            reason,
            "",
            "",
            List.of(),
            "",
            -1,
            "",
            "",
            0L,
            0,
            List.of(),
            List.of(),
            false,
            false,
            0,
            "",
            "",
            List.of(),
            List.of(),
            "",
            ""
        );
    }

    public PreparedFloorModifierPlan {
        List<String> suppliedHostileModifierIds = hostileModifierIds == null
            ? List.of()
            : List.copyOf(hostileModifierIds);
        List<String> suppliedCovenantIds = covenantIds == null
            ? List.of()
            : List.copyOf(covenantIds);
        List<String> suppliedKinCandidateIds = kinCandidateIds == null
            ? List.of()
            : List.copyOf(kinCandidateIds);
        List<String> suppliedKinRejectionReasons = kinRejectionReasons == null
            ? List.of()
            : List.copyOf(kinRejectionReasons);
        List<String> suppliedBossCandidateIds = bossCandidateIds == null
            ? List.of()
            : List.copyOf(bossCandidateIds);
        List<String> suppliedBossMatchingCandidateIds = bossMatchingCandidateIds == null
            ? List.of()
            : List.copyOf(bossMatchingCandidateIds);
        List<String> normalizedKinCandidateIds = suppliedKinCandidateIds.stream()
            .map(value -> sanitize(value, 96).toLowerCase(Locale.ROOT))
            .toList();
        List<String> normalizedBossCandidateIds = suppliedBossCandidateIds.stream()
            .map(value -> sanitize(value, 96).toLowerCase(Locale.ROOT))
            .toList();
        List<String> normalizedBossMatchingCandidateIds = suppliedBossMatchingCandidateIds.stream()
            .map(value -> sanitize(value, 96).toLowerCase(Locale.ROOT))
            .toList();
        boolean tooManyHostileModifiers = suppliedHostileModifierIds.size() > MAX_HOSTILE_MODIFIERS;
        boolean duplicateHostileModifiers = suppliedHostileModifierIds.size()
            != new LinkedHashSet<>(suppliedHostileModifierIds).size();
        boolean tooManyCovenants = suppliedCovenantIds.size() > MAX_COVENANTS;
        boolean duplicateCovenants = suppliedCovenantIds.size()
            != new LinkedHashSet<>(suppliedCovenantIds).size();
        boolean tooManyKinCandidates = suppliedKinCandidateIds.size() > MAX_KIN_CANDIDATES;
        boolean duplicateKinCandidates = normalizedKinCandidateIds.size()
            != new LinkedHashSet<>(normalizedKinCandidateIds).size();
        boolean tooManyKinRejectionReasons = suppliedKinRejectionReasons.size() > MAX_KIN_REJECTION_REASONS;
        boolean tooManyBossCandidates = suppliedBossCandidateIds.size() > MAX_BOSS_CANDIDATES;
        boolean tooManyBossMatchingCandidates = suppliedBossMatchingCandidateIds.size() > MAX_BOSS_CANDIDATES;
        boolean duplicateBossCandidates = normalizedBossCandidateIds.size()
            != new LinkedHashSet<>(normalizedBossCandidateIds).size();
        boolean duplicateBossMatchingCandidates = normalizedBossMatchingCandidateIds.size()
            != new LinkedHashSet<>(normalizedBossMatchingCandidateIds).size();
        floor = Math.max(1, floor);
        algorithmVersion = Math.max(1, algorithmVersion);
        catalogVersion = Math.max(1, catalogVersion);
        kinGroup = sanitize(kinGroup, 48).toLowerCase(Locale.ROOT);
        kinCatalogVersion = Math.max(0, kinCatalogVersion);
        roomPressureId = normalizeRoomPressureId(roomPressureId);
        bossMilestone = bossSlotAvailable && bossMilestone;
        bossCatalogVersion = Math.max(0, bossCatalogVersion);
        bossAffinityKinGroup = sanitize(bossAffinityKinGroup, 48).toLowerCase(Locale.ROOT);
        bossAffinityTheme = sanitize(bossAffinityTheme, 64).toLowerCase(Locale.ROOT);
        selectedBossId = sanitize(selectedBossId, 96).toLowerCase(Locale.ROOT);
        bossResolutionReason = normalizeReason(bossResolutionReason);
        hostileModifierIds = suppliedHostileModifierIds.isEmpty()
            ? List.of()
            : List.copyOf(suppliedHostileModifierIds.stream().limit(MAX_HOSTILE_MODIFIERS).toList());
        covenantIds = suppliedCovenantIds.isEmpty()
            ? List.of()
            : List.copyOf(suppliedCovenantIds.stream()
                .limit(MAX_COVENANTS)
                .map(value -> sanitize(value, 64))
                .toList());
        kinCandidateIds = suppliedKinCandidateIds.isEmpty()
            ? List.of()
            : List.copyOf(normalizedKinCandidateIds.stream()
                .limit(MAX_KIN_CANDIDATES)
                .toList());
        kinRejectionReasons = suppliedKinRejectionReasons.isEmpty()
            ? List.of()
            : List.copyOf(suppliedKinRejectionReasons.stream()
                .limit(MAX_KIN_REJECTION_REASONS)
                .map(value -> sanitize(value, 128))
                .toList());
        bossCandidateIds = suppliedBossCandidateIds.isEmpty()
            ? List.of()
            : List.copyOf(normalizedBossCandidateIds.stream()
                .limit(MAX_BOSS_CANDIDATES)
                .toList());
        bossMatchingCandidateIds = suppliedBossMatchingCandidateIds.isEmpty()
            ? List.of()
            : List.copyOf(normalizedBossMatchingCandidateIds.stream()
                .limit(MAX_BOSS_CANDIDATES)
                .toList());
        boonModifierId = sanitize(boonModifierId, 64);
        reason = sanitize(reason, 48);
        validationStatus = sanitize(validationStatus, 32);
        validationReason = sanitize(validationReason, 64);
        greedPactBurdenType = sanitize(greedPactBurdenType, 64);

        String requestedValidationStatus = validationStatus;
        ValidationResult validation = validateIds(hostileModifierIds, boonModifierId, floor);
        if (tooManyHostileModifiers) validation = new ValidationResult(false, "HOSTILE_MODIFIER_COUNT_EXCEEDED");
        if (duplicateHostileModifiers) validation = new ValidationResult(false, "DUPLICATE_HOSTILE_MODIFIER");
        if (tooManyCovenants) validation = new ValidationResult(false, "COVENANT_COUNT_EXCEEDED");
        if (duplicateCovenants) validation = new ValidationResult(false, "DUPLICATE_COVENANT");
        if (tooManyKinCandidates) validation = new ValidationResult(false, "KIN_CANDIDATE_COUNT_EXCEEDED");
        if (duplicateKinCandidates) validation = new ValidationResult(false, "DUPLICATE_KIN_CANDIDATE");
        if (tooManyKinRejectionReasons) validation = new ValidationResult(false, "KIN_REJECTION_COUNT_EXCEEDED");
        if (tooManyBossCandidates || tooManyBossMatchingCandidates) {
            validation = new ValidationResult(false, "BOSS_CANDIDATE_COUNT_EXCEEDED");
        }
        if (duplicateBossCandidates || duplicateBossMatchingCandidates) {
            validation = new ValidationResult(false, "DUPLICATE_BOSS_CANDIDATE");
        }
        if (validation.valid()) {
            validation = validateCovenants(covenantIds, greedPactBurdenType, greedPactBurdenRoom);
        }
        if (validation.valid()) {
            validation = validateRoomPressure(roomPressureId);
        }
        if (validation.valid()) {
            validation = validateKin(
                kinGroup,
                kinCatalogVersion,
                kinCandidateIds
            );
        }
        if (validation.valid()) {
            validation = validateBossResolution(
                bossSlotAvailable,
                bossMilestone,
                bossCatalogVersion,
                bossAffinityKinGroup,
                bossAffinityTheme,
                bossCandidateIds,
                bossMatchingCandidateIds,
                selectedBossId,
                bossResolutionReason
            );
        }
        if (validationStatus.isBlank()) {
            validationStatus = validation.valid() ? "VALID" : "INVALID";
            validationReason = validation.reason();
        } else if ("VALID".equals(validationStatus) && !validation.valid()) {
            validationStatus = "INVALID";
            validationReason = validation.reason();
        } else if (!"VALID".equals(validationStatus) && !"INVALID".equals(validationStatus)) {
            validationStatus = "INVALID";
            validationReason = "UNKNOWN_VALIDATION_STATUS:" + bounded(requestedValidationStatus, 32);
        } else if (validationReason.isBlank()) {
            validationReason = validation.reason();
        }
    }

    public boolean isDispatchEligible() {
        return "VALID".equals(validationStatus);
    }

    public boolean isRoomPressureDispatchEligible() {
        return isDispatchEligible()
            && !roomPressureId.isBlank()
            && DungeonRoomPressureCatalog.isRollable(roomPressureId);
    }

    public PreparedFloorModifierPlan withRoomPressure(String pressureId) {
        return new PreparedFloorModifierPlan(
            floor,
            seed,
            algorithmVersion,
            catalogVersion,
            hostileModifierIds,
            boonModifierId,
            reason,
            validationStatus,
            validationReason,
            covenantIds,
            greedPactBurdenType,
            greedPactBurdenRoom,
            pressureId,
            kinGroup,
            kinSeed,
            kinCatalogVersion,
            kinCandidateIds,
            kinRejectionReasons,
            bossSlotAvailable,
            bossMilestone,
            bossCatalogVersion,
            bossAffinityKinGroup,
            bossAffinityTheme,
            bossCandidateIds,
            bossMatchingCandidateIds,
            selectedBossId,
            bossResolutionReason
        );
    }

    public Set<DungeonModifierDefinition> resolveHostileModifiers() {
        Set<DungeonModifierDefinition> resolved = new LinkedHashSet<>();
        for (String id : hostileModifierIds) {
            DungeonModifierDefinition definition = DungeonModifierDefinition.byId(id);
            if (definition != null) resolved.add(definition);
        }
        return Set.copyOf(resolved);
    }

    public Set<DungeonRunCovenant> resolveCovenants() {
        Set<DungeonRunCovenant> resolved = new LinkedHashSet<>();
        for (String id : covenantIds) {
            DungeonRunCovenant covenant = DungeonRunCovenant.byId(id);
            if (covenant != null) resolved.add(covenant);
        }
        return Set.copyOf(resolved);
    }

    public Optional<GreedPactBurdenIdentity> greedPactBurdenIdentity() {
        if (!covenantIds.contains(DungeonRunCovenant.GREED_PACT.id())
            || greedPactBurdenType.isBlank()
            || greedPactBurdenRoom < 0) {
            return Optional.empty();
        }
        return Optional.of(new GreedPactBurdenIdentity(greedPactBurdenType, greedPactBurdenRoom));
    }

    public Optional<DungeonEnemyKinSelection> kinSelection() {
        if (kinGroup.isBlank() && kinCandidateIds.isEmpty() && kinRejectionReasons.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DungeonEnemyKinSelection(
            floor,
            kinSeed,
            kinCatalogVersion > 0 ? kinCatalogVersion : DungeonEnemyKinCatalog.CATALOG_VERSION,
            kinGroup,
            kinCandidateIds,
            kinRejectionReasons,
            "PERSISTED"
        ));
    }

    public Optional<DungeonBossResolution> bossResolution() {
        if (bossCatalogVersion <= 0
            && !bossSlotAvailable
            && !bossMilestone
            && bossAffinityKinGroup.isBlank()
            && bossAffinityTheme.isBlank()
            && bossCandidateIds.isEmpty()
            && bossMatchingCandidateIds.isEmpty()
            && selectedBossId.isBlank()
            && bossResolutionReason.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DungeonBossResolution(
            floor,
            seed,
            bossCatalogVersion > 0 ? bossCatalogVersion : DungeonBossCatalog.CATALOG_VERSION,
            bossSlotAvailable,
            bossMilestone,
            bossAffinityKinGroup,
            bossAffinityTheme,
            bossCandidateIds,
            bossMatchingCandidateIds,
            selectedBossId,
            bossResolutionReason
        ));
    }

    public PreparedFloorModifierPlan withKinSelection(DungeonEnemyKinSelection selection) {
        if (selection == null) return this;
        return new PreparedFloorModifierPlan(
            floor,
            seed,
            algorithmVersion,
            catalogVersion,
            hostileModifierIds,
            boonModifierId,
            reason,
            validationStatus,
            validationReason,
            covenantIds,
            greedPactBurdenType,
            greedPactBurdenRoom,
            roomPressureId,
            selection.selectedKinGroup(),
            selection.seed(),
            selection.catalogVersion(),
            selection.eligibleCandidateIds(),
            selection.rejectionReasons(),
            bossSlotAvailable,
            bossMilestone,
            bossCatalogVersion,
            bossAffinityKinGroup,
            bossAffinityTheme,
            bossCandidateIds,
            bossMatchingCandidateIds,
            selectedBossId,
            bossResolutionReason
        );
    }

    public PreparedFloorModifierPlan withBossResolution(DungeonBossResolution resolution) {
        if (resolution == null) return this;
        return new PreparedFloorModifierPlan(
            floor,
            seed,
            algorithmVersion,
            catalogVersion,
            hostileModifierIds,
            boonModifierId,
            reason,
            validationStatus,
            validationReason,
            covenantIds,
            greedPactBurdenType,
            greedPactBurdenRoom,
            roomPressureId,
            kinGroup,
            kinSeed,
            kinCatalogVersion,
            kinCandidateIds,
            kinRejectionReasons,
            resolution.bossSlotAvailable(),
            resolution.milestoneBoss(),
            resolution.catalogVersion(),
            resolution.affinityKinGroup(),
            resolution.authoredTheme(),
            resolution.eligibleCandidateIds(),
            resolution.matchingCandidateIds(),
            resolution.selectedBossId(),
            resolution.reason()
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Floor", floor);
        tag.putLong("Seed", seed);
        tag.putInt("AlgorithmVersion", algorithmVersion);
        tag.putInt("CatalogVersion", catalogVersion);
        ListTag hostile = new ListTag();
        hostileModifierIds.forEach(id -> hostile.add(StringTag.valueOf(id)));
        tag.put("HostileModifiers", hostile);
        if (!boonModifierId.isBlank()) tag.putString("BoonModifier", boonModifierId);
        tag.putString("Reason", reason);
        tag.putString("ValidationStatus", validationStatus);
        tag.putString("ValidationReason", validationReason);
        if (!covenantIds.isEmpty()) {
            ListTag covenants = new ListTag();
            covenantIds.forEach(id -> covenants.add(StringTag.valueOf(id)));
            tag.put("Covenants", covenants);
        }
        if (!greedPactBurdenType.isBlank()) {
            tag.putString("GreedPactBurdenType", greedPactBurdenType);
        }
        if (greedPactBurdenRoom >= 0) {
            tag.putInt("GreedPactBurdenRoom", greedPactBurdenRoom);
        }
        if (!roomPressureId.isBlank()) {
            tag.putString("RoomPressureId", roomPressureId);
        }
        boolean hasKinMetadata = kinCatalogVersion > 0
            || !kinGroup.isBlank()
            || !kinCandidateIds.isEmpty()
            || !kinRejectionReasons.isEmpty();
        if (hasKinMetadata) {
            tag.putLong("KinSeed", kinSeed);
            tag.putInt("KinCatalogVersion", kinCatalogVersion);
            if (!kinGroup.isBlank()) tag.putString("KinGroup", kinGroup);
            if (!kinCandidateIds.isEmpty()) {
                ListTag candidates = new ListTag();
                kinCandidateIds.forEach(id -> candidates.add(StringTag.valueOf(id)));
                tag.put("KinCandidateIds", candidates);
            }
            if (!kinRejectionReasons.isEmpty()) {
                ListTag rejections = new ListTag();
                kinRejectionReasons.forEach(reason -> rejections.add(StringTag.valueOf(reason)));
                tag.put("KinRejectionReasons", rejections);
            }
        }
        boolean hasBossMetadata = bossCatalogVersion > 0
            || bossSlotAvailable
            || bossMilestone
            || !bossAffinityKinGroup.isBlank()
            || !bossAffinityTheme.isBlank()
            || !bossCandidateIds.isEmpty()
            || !bossMatchingCandidateIds.isEmpty()
            || !selectedBossId.isBlank()
            || !bossResolutionReason.isBlank();
        if (hasBossMetadata) {
            tag.putBoolean("BossSlotAvailable", bossSlotAvailable);
            if (bossMilestone) tag.putBoolean("BossMilestone", true);
            tag.putInt("BossCatalogVersion", bossCatalogVersion);
            if (!bossAffinityKinGroup.isBlank()) tag.putString("BossAffinityKinGroup", bossAffinityKinGroup);
            if (!bossAffinityTheme.isBlank()) tag.putString("BossAffinityTheme", bossAffinityTheme);
            if (!bossCandidateIds.isEmpty()) {
                ListTag candidates = new ListTag();
                bossCandidateIds.forEach(id -> candidates.add(StringTag.valueOf(id)));
                tag.put("BossCandidateIds", candidates);
            }
            if (!bossMatchingCandidateIds.isEmpty()) {
                ListTag matches = new ListTag();
                bossMatchingCandidateIds.forEach(id -> matches.add(StringTag.valueOf(id)));
                tag.put("BossMatchingCandidateIds", matches);
            }
            if (!selectedBossId.isBlank()) tag.putString("SelectedBossId", selectedBossId);
            if (!bossResolutionReason.isBlank()) tag.putString("BossResolutionReason", bossResolutionReason);
        }
        return tag;
    }

    public static PreparedFloorModifierPlan load(CompoundTag tag) {
        List<String> hostile = new ArrayList<>();
        ListTag hostileTag = tag.getList("HostileModifiers", Tag.TAG_STRING);
        for (int index = 0; index < hostileTag.size() && index < MAX_HOSTILE_MODIFIERS; index++) {
            hostile.add(hostileTag.getString(index));
        }
        List<String> covenants = new ArrayList<>();
        ListTag covenantTag = tag.getList("Covenants", Tag.TAG_STRING);
        for (int index = 0; index < covenantTag.size() && index < MAX_COVENANTS; index++) {
            covenants.add(covenantTag.getString(index));
        }
        boolean tooManyCovenants = covenantTag.size() > MAX_COVENANTS;
        boolean duplicateCovenants = hasDuplicate(covenants);
        List<String> kinCandidates = new ArrayList<>();
        ListTag kinCandidateTag = tag.getList("KinCandidateIds", Tag.TAG_STRING);
        for (int index = 0; index < kinCandidateTag.size() && index < MAX_KIN_CANDIDATES; index++) {
            kinCandidates.add(kinCandidateTag.getString(index));
        }
        List<String> kinRejections = new ArrayList<>();
        ListTag kinRejectionTag = tag.getList("KinRejectionReasons", Tag.TAG_STRING);
        for (int index = 0; index < kinRejectionTag.size() && index < MAX_KIN_REJECTION_REASONS; index++) {
            kinRejections.add(kinRejectionTag.getString(index));
        }
        boolean tooManyKinCandidates = kinCandidateTag.size() > MAX_KIN_CANDIDATES;
        boolean duplicateKinCandidates = hasDuplicate(kinCandidates);
        boolean tooManyKinRejections = kinRejectionTag.size() > MAX_KIN_REJECTION_REASONS;
        List<String> bossCandidates = new ArrayList<>();
        ListTag bossCandidateTag = tag.getList("BossCandidateIds", Tag.TAG_STRING);
        for (int index = 0; index < bossCandidateTag.size() && index < MAX_BOSS_CANDIDATES; index++) {
            bossCandidates.add(bossCandidateTag.getString(index));
        }
        List<String> bossMatches = new ArrayList<>();
        ListTag bossMatchingTag = tag.getList("BossMatchingCandidateIds", Tag.TAG_STRING);
        for (int index = 0; index < bossMatchingTag.size() && index < MAX_BOSS_CANDIDATES; index++) {
            bossMatches.add(bossMatchingTag.getString(index));
        }
        boolean tooManyBossCandidates = bossCandidateTag.size() > MAX_BOSS_CANDIDATES
            || bossMatchingTag.size() > MAX_BOSS_CANDIDATES;
        boolean duplicateBossCandidates = hasDuplicate(bossCandidates)
            || hasDuplicate(bossMatches);
        boolean malformedRoomPressure = tag.contains("RoomPressureId")
            && !tag.contains("RoomPressureId", Tag.TAG_STRING);
        String loadedValidationStatus = malformedRoomPressure
            || hostileTag.size() > MAX_HOSTILE_MODIFIERS || hasDuplicate(hostile)
            ? "INVALID"
            : tooManyCovenants || duplicateCovenants
                ? "INVALID"
                : tooManyKinCandidates || duplicateKinCandidates || tooManyKinRejections
                    || tooManyBossCandidates || duplicateBossCandidates
                    ? "INVALID"
                    : tag.getString("ValidationStatus");
        String loadedValidationReason = malformedRoomPressure
            ? "ROOM_PRESSURE_ID_INVALID"
            : hostileTag.size() > MAX_HOSTILE_MODIFIERS
                ? "HOSTILE_MODIFIER_COUNT_EXCEEDED"
            : hasDuplicate(hostile)
                ? "DUPLICATE_HOSTILE_MODIFIER"
                : tooManyCovenants
                    ? "COVENANT_COUNT_EXCEEDED"
                    : duplicateCovenants
                        ? "DUPLICATE_COVENANT"
                        : tooManyKinCandidates
                            ? "KIN_CANDIDATE_COUNT_EXCEEDED"
                            : duplicateKinCandidates
                                ? "DUPLICATE_KIN_CANDIDATE"
                                : tooManyKinRejections
                                    ? "KIN_REJECTION_COUNT_EXCEEDED"
                                    : tooManyBossCandidates
                                        ? "BOSS_CANDIDATE_COUNT_EXCEEDED"
                                        : duplicateBossCandidates
                                            ? "DUPLICATE_BOSS_CANDIDATE"
                                            : tag.getString("ValidationReason");
        return new PreparedFloorModifierPlan(
            tag.getInt("Floor"),
            tag.getLong("Seed"),
            Math.max(1, tag.getInt("AlgorithmVersion")),
            tag.contains("CatalogVersion") ? Math.max(1, tag.getInt("CatalogVersion")) : 1,
            hostile,
            tag.getString("BoonModifier"),
            tag.getString("Reason"),
            loadedValidationStatus,
            loadedValidationReason,
            covenants,
            tag.getString("GreedPactBurdenType"),
            tag.contains("GreedPactBurdenRoom") ? tag.getInt("GreedPactBurdenRoom") : -1,
            tag.contains("RoomPressureId", Tag.TAG_STRING) ? tag.getString("RoomPressureId") : "",
            tag.getString("KinGroup"),
            tag.contains("KinSeed") ? tag.getLong("KinSeed") : 0L,
            tag.contains("KinCatalogVersion") ? Math.max(0, tag.getInt("KinCatalogVersion")) : 0,
            kinCandidates,
            kinRejections,
            tag.contains("BossSlotAvailable", Tag.TAG_BYTE) && tag.getBoolean("BossSlotAvailable"),
            tag.contains("BossMilestone", Tag.TAG_BYTE) && tag.getBoolean("BossMilestone"),
            tag.contains("BossCatalogVersion") ? Math.max(0, tag.getInt("BossCatalogVersion")) : 0,
            tag.getString("BossAffinityKinGroup"),
            tag.getString("BossAffinityTheme"),
            bossCandidates,
            bossMatches,
            tag.getString("SelectedBossId"),
            tag.getString("BossResolutionReason")
        );
    }

    private static ValidationResult validateIds(List<String> hostileIds, String boonId, int floor) {
        if (hostileIds.isEmpty()) return new ValidationResult(false, "EMPTY_HOSTILE_PLAN");
        for (String id : hostileIds) {
            DungeonModifierCatalog.Entry entry = DungeonModifierCatalog.byId(id);
            if (entry == null) return new ValidationResult(false, "UNKNOWN_MODIFIER:" + bounded(id, 48));
            if (!entry.isRollable()) {
                return new ValidationResult(false, "MODIFIER_NOT_LIVE:" + bounded(id, 48));
            }
            if (!isFloorUnlocked(entry, floor)) {
                return new ValidationResult(false, "MODIFIER_FLOOR_LOCKED:" + bounded(id, 48));
            }
        }
        if (!boonId.isBlank()) {
            DungeonModifierCatalog.Entry boon = DungeonModifierCatalog.byId(boonId);
            if (boon == null) return new ValidationResult(false, "UNKNOWN_BOON:" + bounded(boonId, 48));
            if (!boon.isRollable()) {
                return new ValidationResult(false, "BOON_NOT_LIVE:" + bounded(boonId, 48));
            }
            if (!isFloorUnlocked(boon, floor)) {
                return new ValidationResult(false, "BOON_FLOOR_LOCKED:" + bounded(boonId, 48));
            }
        }
        List<String> allModifierIds = new ArrayList<>(hostileIds);
        if (!boonId.isBlank()) allModifierIds.add(boonId);
        DungeonModifierCompatibilityMatrix.CompatibilityResult compatibility =
            DungeonModifierCompatibilityMatrix.validate(allModifierIds);
        if (!compatibility.compatible()) {
            return new ValidationResult(false, compatibility.reason());
        }
        return new ValidationResult(true, "NONE");
    }

    static boolean isFloorUnlocked(DungeonModifierCatalog.Entry entry, int floor) {
        return entry != null && Math.max(1, floor) >= entry.minFloor();
    }

    private static ValidationResult validateRoomPressure(String pressureId) {
        if (pressureId.isBlank()) {
            return new ValidationResult(true, "NONE");
        }
        return DungeonRoomPressureCatalog.isKnown(pressureId)
            ? new ValidationResult(true, "NONE")
            : new ValidationResult(false, "UNKNOWN_ROOM_PRESSURE:" + bounded(pressureId, 48));
    }

    private static ValidationResult validateCovenants(
        List<String> covenantIds,
        String burdenType,
        int burdenRoom
    ) {
        if (burdenRoom < -1 || burdenRoom > MAX_BURDEN_ROOM) {
            return new ValidationResult(false, "GREED_PACT_BURDEN_ROOM_INVALID");
        }
        boolean hasGreedPact = false;
        for (String id : covenantIds) {
            DungeonRunCovenant covenant = DungeonRunCovenant.byId(id);
            if (covenant == null) {
                return new ValidationResult(false, "UNKNOWN_COVENANT:" + bounded(id, 48));
            }
            if (!covenant.isRollable()) {
                return new ValidationResult(false, "COVENANT_NOT_LIVE:" + bounded(id, 48));
            }
            hasGreedPact |= covenant == DungeonRunCovenant.GREED_PACT;
        }
        if (!hasGreedPact && (!burdenType.isBlank() || burdenRoom >= 0)) {
            return new ValidationResult(false, "ORPHAN_GREED_PACT_BURDEN");
        }
        if (hasGreedPact && (burdenType.isBlank() || burdenRoom < 0)) {
            return new ValidationResult(false, "GREED_PACT_BURDEN_UNRESOLVED");
        }
        return new ValidationResult(true, "NONE");
    }

    private static ValidationResult validateKin(
        String group,
        int catalogVersion,
        List<String> candidateIds
    ) {
        if (group.isBlank()) {
            return candidateIds.isEmpty()
                ? new ValidationResult(true, "NONE")
                : new ValidationResult(false, "ORPHAN_KIN_CANDIDATES");
        }
        if (catalogVersion != DungeonEnemyKinCatalog.CATALOG_VERSION) {
            return new ValidationResult(false, "KIN_CATALOG_VERSION_MISMATCH");
        }
        if (!DungeonEnemyKinCatalog.isKnownGroup(group)) {
            return new ValidationResult(false, "UNKNOWN_KIN_GROUP:" + bounded(group, 48));
        }
        if (candidateIds.isEmpty()) {
            return new ValidationResult(false, "KIN_CANDIDATE_SET_EMPTY");
        }
        for (String candidateId : candidateIds) {
            Optional<DungeonEnemyKinCatalog.Entry> entry = DungeonEnemyKinCatalog.forEntity(candidateId);
            if (entry.isEmpty()) {
                return new ValidationResult(false, "UNKNOWN_KIN_CANDIDATE:" + bounded(candidateId, 48));
            }
            if (!group.equals(entry.get().kinGroup())) {
                return new ValidationResult(false, "KIN_CANDIDATE_GROUP_MISMATCH:" + bounded(candidateId, 48));
            }
        }
        return new ValidationResult(true, "NONE");
    }

    private static ValidationResult validateBossResolution(
        boolean bossSlotAvailable,
        boolean bossMilestone,
        int bossCatalogVersion,
        String affinityKinGroup,
        String affinityTheme,
        List<String> candidateIds,
        List<String> matchingCandidateIds,
        String selectedBossId,
        String resolutionReason
    ) {
        boolean hasBossMetadata = bossCatalogVersion > 0
            || bossSlotAvailable
            || bossMilestone
            || !affinityKinGroup.isBlank()
            || !affinityTheme.isBlank()
            || !candidateIds.isEmpty()
            || !matchingCandidateIds.isEmpty()
            || !selectedBossId.isBlank()
            || !resolutionReason.isBlank();
        if (!hasBossMetadata) return new ValidationResult(true, "NONE");
        if (resolutionReason.isBlank()) {
            return new ValidationResult(false, "BOSS_RESOLUTION_REASON_MISSING");
        }
        if (!Set.of(
            "NO_BOSS_SLOT",
            "NO_AUTHORED_BOSS_CANDIDATES",
            "NEUTRAL_BOSS_SELECTED",
            "AFFINITY_MATCH_SELECTED",
            "AFFINITY_NO_MATCH_NEUTRAL_FALLBACK"
        ).contains(resolutionReason)) {
            return new ValidationResult(false, "BOSS_RESOLUTION_REASON_UNKNOWN");
        }
        if (!bossSlotAvailable) {
            if (bossMilestone || !candidateIds.isEmpty() || !matchingCandidateIds.isEmpty()
                || !selectedBossId.isBlank()) {
                return new ValidationResult(false, "BOSS_DATA_WITHOUT_BOSS_SLOT");
            }
            return "NO_BOSS_SLOT".equals(resolutionReason)
                ? new ValidationResult(true, "NONE")
                : new ValidationResult(false, "BOSS_REASON_WITHOUT_BOSS_SLOT");
        }
        if (bossCatalogVersion != DungeonBossCatalog.CATALOG_VERSION) {
            return new ValidationResult(false, "BOSS_CATALOG_VERSION_MISMATCH");
        }
        if (candidateIds.isEmpty()) {
            return "NO_AUTHORED_BOSS_CANDIDATES".equals(resolutionReason)
                ? new ValidationResult(true, "NONE")
                : new ValidationResult(false, "BOSS_CANDIDATE_SET_EMPTY");
        }
        for (String candidateId : candidateIds) {
            if (DungeonBossCatalog.forEntity(candidateId).isEmpty()) {
                return new ValidationResult(false, "UNKNOWN_BOSS_CANDIDATE:" + bounded(candidateId, 48));
            }
        }
        for (String matchingId : matchingCandidateIds) {
            if (!candidateIds.contains(matchingId)) {
                return new ValidationResult(false, "BOSS_MATCH_NOT_IN_CANDIDATES:" + bounded(matchingId, 48));
            }
            if (!DungeonBossCatalog.forEntity(matchingId)
                .map(entry -> entry.matches(affinityKinGroup, affinityTheme))
                .orElse(false)) {
                return new ValidationResult(false, "BOSS_MATCH_AFFINITY_MISMATCH:" + bounded(matchingId, 48));
            }
        }
        if (!matchingCandidateIds.isEmpty() && affinityKinGroup.isBlank() && affinityTheme.isBlank()) {
            return new ValidationResult(false, "ORPHAN_BOSS_MATCHES");
        }
        if ("AFFINITY_NO_MATCH_NEUTRAL_FALLBACK".equals(resolutionReason)) {
            if (affinityKinGroup.isBlank() && affinityTheme.isBlank()) {
                return new ValidationResult(false, "BOSS_FALLBACK_WITHOUT_AFFINITY");
            }
            if (!matchingCandidateIds.isEmpty()) {
                return new ValidationResult(false, "BOSS_FALLBACK_HAS_MATCHES");
            }
            for (String candidateId : candidateIds) {
                if (DungeonBossCatalog.forEntity(candidateId)
                    .map(entry -> entry.matches(affinityKinGroup, affinityTheme))
                    .orElse(false)) {
                    return new ValidationResult(false, "BOSS_FALLBACK_CANDIDATE_MATCHES:" + bounded(candidateId, 48));
                }
            }
        }
        if (selectedBossId.isBlank()) {
            return new ValidationResult(false, "BOSS_SELECTION_MISSING");
        }
        if (!candidateIds.contains(selectedBossId)) {
            return new ValidationResult(false, "SELECTED_BOSS_NOT_IN_CANDIDATES");
        }
        if ("AFFINITY_MATCH_SELECTED".equals(resolutionReason)
            && !matchingCandidateIds.contains(selectedBossId)) {
            return new ValidationResult(false, "SELECTED_BOSS_NOT_IN_AFFINITY_MATCHES");
        }
        if ("AFFINITY_MATCH_SELECTED".equals(resolutionReason)
            && !DungeonBossCatalog.forEntity(selectedBossId)
                .map(entry -> entry.matches(affinityKinGroup, affinityTheme))
                .orElse(false)) {
            return new ValidationResult(false, "SELECTED_BOSS_AFFINITY_MISMATCH");
        }
        return new ValidationResult(true, "NONE");
    }

    private static String sanitize(String value, int maximum) {
        return bounded(value == null ? "" : value, maximum);
    }

    private static String normalizeReason(String value) {
        String normalized = sanitize(value, 96).trim().toUpperCase(Locale.ROOT);
        return normalized;
    }

    private static String normalizeRoomPressureId(String value) {
        return sanitize(value, 64).trim().toLowerCase(Locale.ROOT);
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static boolean hasDuplicate(List<String> values) {
        return values.size() != new LinkedHashSet<>(values).size();
    }

    private record ValidationResult(boolean valid, String reason) {}

    public record GreedPactBurdenIdentity(String type, int room) {
        public GreedPactBurdenIdentity {
            type = bounded(type == null ? "" : type, 64);
            room = Math.max(-1, Math.min(MAX_BURDEN_ROOM, room));
        }

        public String stableKey() {
            return type + "@" + room;
        }
    }
}
