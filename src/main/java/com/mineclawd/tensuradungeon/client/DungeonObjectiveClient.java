package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonObjectivePayload;
import java.util.Map;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonObjectiveClient {
    private static volatile Map<String, Object> latest = Map.of();

    private DungeonObjectiveClient() {}

    public static void handle(DungeonObjectivePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> latest = Map.ofEntries(
            Map.entry("receivedAtMillis", System.currentTimeMillis()),
            Map.entry("active", payload.active()),
            Map.entry("floorNumber", payload.floorNumber()),
            Map.entry("roomState", payload.roomState()),
            Map.entry("objectiveId", payload.objectiveId()),
            Map.entry("objectiveLabel", payload.objectiveLabel()),
            Map.entry("objectiveProgress", payload.objectiveProgress()),
            Map.entry("objectiveTarget", payload.objectiveTarget()),
            Map.entry("remainingTicks", payload.remainingTicks()),
            Map.entry("durationTicks", payload.durationTicks()),
            Map.entry("enemiesAlive", payload.enemiesAlive()),
            Map.entry("enemiesSpawned", payload.enemiesSpawned()),
            Map.entry("modifierNames", payload.modifierNames()),
            Map.entry("modifierSummary", payload.modifierSummary()),
            Map.entry("compositionSpent", payload.compositionSpent()),
            Map.entry("compositionBudget", payload.compositionBudget()),
            Map.entry("compositionSummary", payload.compositionSummary()),
            Map.entry("runElapsedTicks", payload.runElapsedTicks()),
            Map.entry("revisionGameTime", payload.revisionGameTime()),
            Map.entry("recoveryTier", payload.recoveryTier()),
            Map.entry("recoveryWindowPermille", payload.recoveryWindowPermille()),
            Map.entry("recoveryLastCharge", payload.recoveryLastCharge())
        ));
    }

    public static Map<String, Object> snapshot() {
        return latest;
    }
}
