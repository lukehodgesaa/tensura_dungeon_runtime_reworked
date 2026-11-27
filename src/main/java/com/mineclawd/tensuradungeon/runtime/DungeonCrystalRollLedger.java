package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyDefinition;
import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyDefinition.EncounterClass;
import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyDefinition.RegularBand;
import com.mineclawd.tensuradungeon.reward.DungeonCrystalEconomyDefinition.RollResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class DungeonCrystalRollLedger {
    private final Map<String, Map<SourceKey, CommittedRoll>> sessions = new LinkedHashMap<>();

    Claim claim(
        String sessionId,
        UUID ownerId,
        UUID orbId,
        int floor,
        BlockPos controllerPos,
        EncounterClass encounterClass,
        DungeonCrystalEconomyDefinition definition,
        IntUnaryOperator nextInt
    ) {
        if (sessionId == null || sessionId.isBlank() || ownerId == null || orbId == null
            || controllerPos == null || encounterClass == null || definition == null || nextInt == null) {
            throw new IllegalArgumentException("Crystal roll claim requires complete death provenance");
        }
        Map<SourceKey, CommittedRoll> session = sessions.computeIfAbsent(
            sessionId, ignored -> new LinkedHashMap<>()
        );
        SourceKey source = new SourceKey(controllerPos.asLong(), encounterClass);
        CommittedRoll existing = session.get(source);
        if (existing != null) return new Claim(ClaimStatus.ALREADY_COMMITTED, existing);

        long committedForClass = session.keySet().stream()
            .filter(candidate -> candidate.encounterClass == encounterClass)
            .count();
        int sessionBudgetCount = definition.encounterRules().get(encounterClass).sessionBudgetCount();
        if (committedForClass >= sessionBudgetCount) {
            return new Claim(ClaimStatus.SESSION_BUDGET_EXHAUSTED, null);
        }

        RegularBand band = definition.bandForFloor(floor);
        RollResult result = definition.rollRegular(floor, encounterClass, nextInt);
        CommittedRoll committed = new CommittedRoll(
            ownerId, orbId, floor, controllerPos.asLong(), encounterClass, band.id(), band.itemId(), result
        );
        session.put(source, committed);
        return new Claim(ClaimStatus.COMMITTED, committed);
    }

    CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag sessionTags = new ListTag();
        for (Map.Entry<String, Map<SourceKey, CommittedRoll>> session : sessions.entrySet()) {
            CompoundTag sessionTag = new CompoundTag();
            sessionTag.putString("SessionId", session.getKey());
            ListTag rollTags = new ListTag();
            for (CommittedRoll roll : session.getValue().values()) rollTags.add(roll.save());
            sessionTag.put("Rolls", rollTags);
            sessionTags.add(sessionTag);
        }
        root.put("Sessions", sessionTags);
        return root;
    }

    static DungeonCrystalRollLedger load(CompoundTag root) {
        DungeonCrystalRollLedger ledger = new DungeonCrystalRollLedger();
        ListTag sessionTags = root.getList("Sessions", Tag.TAG_COMPOUND);
        for (int sessionIndex = 0; sessionIndex < sessionTags.size(); sessionIndex++) {
            CompoundTag sessionTag = sessionTags.getCompound(sessionIndex);
            String sessionId = sessionTag.getString("SessionId");
            if (sessionId.isBlank() || "NONE".equalsIgnoreCase(sessionId)) continue;
            Map<SourceKey, CommittedRoll> rolls = new LinkedHashMap<>();
            ListTag rollTags = sessionTag.getList("Rolls", Tag.TAG_COMPOUND);
            for (int rollIndex = 0; rollIndex < rollTags.size(); rollIndex++) {
                CommittedRoll roll = CommittedRoll.load(rollTags.getCompound(rollIndex));
                if (roll == null) continue;
                rolls.putIfAbsent(new SourceKey(roll.controllerPos(), roll.encounterClass()), roll);
            }
            if (!rolls.isEmpty()) ledger.sessions.put(sessionId, rolls);
        }
        return ledger;
    }

    int committedCount(String sessionId, EncounterClass encounterClass) {
        Map<SourceKey, CommittedRoll> session = sessions.get(sessionId);
        if (session == null) return 0;
        return (int) session.keySet().stream()
            .filter(source -> source.encounterClass == encounterClass)
            .count();
    }

    enum ClaimStatus {
        COMMITTED,
        ALREADY_COMMITTED,
        SESSION_BUDGET_EXHAUSTED
    }

    record Claim(ClaimStatus status, CommittedRoll roll) {
        boolean committedNow() {
            return status == ClaimStatus.COMMITTED;
        }
    }

    record CommittedRoll(
        UUID ownerId,
        UUID orbId,
        int floor,
        long controllerPos,
        EncounterClass encounterClass,
        String bandId,
        String itemId,
        RollResult result
    ) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("OwnerId", ownerId);
            tag.putUUID("OrbId", orbId);
            tag.putInt("Floor", floor);
            tag.putLong("ControllerPos", controllerPos);
            tag.putString("EncounterClass", encounterClass.name());
            tag.putString("BandId", bandId);
            tag.putString("ItemId", itemId);
            tag.putInt("Opportunities", result.opportunities());
            tag.putInt("SuccessBasisPoints", result.successBasisPoints());
            tag.putInt("RareSameTierBasisPoints", result.rareSameTierBasisPoints());
            tag.putInt("AwardedCrystals", result.awardedCrystals());
            return tag;
        }

        private static CommittedRoll load(CompoundTag tag) {
            if (!tag.hasUUID("OwnerId") || !tag.hasUUID("OrbId") || !tag.contains("ControllerPos")) return null;
            try {
                EncounterClass encounterClass = EncounterClass.valueOf(tag.getString("EncounterClass"));
                String bandId = tag.getString("BandId");
                String itemId = tag.getString("ItemId");
                if (bandId.isBlank() || itemId.isBlank()) return null;
                RollResult result = new RollResult(
                    tag.getInt("Opportunities"),
                    tag.getInt("SuccessBasisPoints"),
                    tag.getInt("RareSameTierBasisPoints"),
                    tag.getInt("AwardedCrystals")
                );
                return new CommittedRoll(
                    tag.getUUID("OwnerId"), tag.getUUID("OrbId"), tag.getInt("Floor"),
                    tag.getLong("ControllerPos"), encounterClass, bandId, itemId, result
                );
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    private record SourceKey(long controllerPos, EncounterClass encounterClass) {}
}
