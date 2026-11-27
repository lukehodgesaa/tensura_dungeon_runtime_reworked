package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonCompositionData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;

public final class GeneratedFloorRepository {
    private final String dimensionKey;
    private final GeneratedFloorRuntimeData runtimeData;
    private final RollingDungeonCompositionData compositionData;
    private final boolean explicitScope;
    @Nullable
    private DungeonInstanceAllocationData.InstanceKey instanceKey;

    public GeneratedFloorRepository(
        String dimensionKey,
        GeneratedFloorRuntimeData runtimeData,
        RollingDungeonCompositionData compositionData
    ) {
        this.dimensionKey = dimensionKey;
        this.runtimeData = runtimeData;
        this.compositionData = compositionData;
        this.explicitScope = false;
    }

    public GeneratedFloorRepository(
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey,
        GeneratedFloorRuntimeData runtimeData,
        RollingDungeonCompositionData compositionData
    ) {
        this.dimensionKey = dimensionKey;
        this.runtimeData = runtimeData;
        this.compositionData = compositionData;
        this.explicitScope = true;
        this.instanceKey = instanceKey;
    }

    public static GeneratedFloorRepository get(ServerLevel level) {
        return get(level, level.dimension().location().toString());
    }

    public static GeneratedFloorRepository get(ServerLevel level, String dimensionKey) {
        GeneratedFloorRuntimeData runtimeData = GeneratedFloorRuntimeData.get(level);
        RollingDungeonCompositionData compositionData = RollingDungeonCompositionData.get(level);
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            DungeonInstanceAllocationData.get(level).currentIdentity(dimensionKey).orElse(null);
        if (instanceKey != null
            && (runtimeData.getInstanceKeys(dimensionKey).contains(instanceKey)
                || compositionData.getInstanceKeys(dimensionKey).contains(instanceKey))) {
            return new GeneratedFloorRepository(
                dimensionKey, instanceKey, runtimeData, compositionData
            );
        }
        return new GeneratedFloorRepository(
            dimensionKey,
            runtimeData,
            compositionData
        );
    }

    public static GeneratedFloorRepository get(
        ServerLevel level,
        String dimensionKey,
        DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return new GeneratedFloorRepository(
            dimensionKey,
            instanceKey,
            GeneratedFloorRuntimeData.get(level),
            RollingDungeonCompositionData.get(level)
        );
    }

    public static List<GeneratedFloorRepository> all(ServerLevel level) {
        String dimensionKey = level.dimension().location().toString();
        GeneratedFloorRuntimeData runtimeData = GeneratedFloorRuntimeData.get(level);
        RollingDungeonCompositionData compositionData = RollingDungeonCompositionData.get(level);
        Set<DungeonInstanceAllocationData.InstanceKey> distinctKeys = new LinkedHashSet<>();
        distinctKeys.addAll(runtimeData.getInstanceKeys(dimensionKey));
        distinctKeys.addAll(compositionData.getInstanceKeys(dimensionKey));
        if (distinctKeys.isEmpty()) {
            return List.of(new GeneratedFloorRepository(
                dimensionKey, runtimeData, compositionData
            ));
        }
        List<DungeonInstanceAllocationData.InstanceKey> keys = new ArrayList<>(distinctKeys);
        keys.sort(
            Comparator.comparing(
                (DungeonInstanceAllocationData.InstanceKey key) -> key.instanceId().toString()
            ).thenComparingLong(DungeonInstanceAllocationData.InstanceKey::epoch)
        );
        return keys.stream()
            .map(key -> new GeneratedFloorRepository(
                dimensionKey, key, runtimeData, compositionData
            ))
            .toList();
    }

    public GeneratedFloorSession hydrate() {
        if (instanceKey != null) {
            boolean runtimeOwns = runtimeData.getInstanceKeys(dimensionKey).contains(instanceKey);
            boolean compositionOwns = compositionData.getInstanceKeys(dimensionKey).contains(instanceKey);
            return GeneratedFloorSession.hydrate(
                runtimeData.getLastGeneratedPlacement(dimensionKey, instanceKey),
                runtimeData.getHubGateState(dimensionKey, instanceKey),
                runtimeData.getCleanupState(dimensionKey, instanceKey),
                compositionData.getState(dimensionKey, instanceKey),
                runtimeData.getGenerationFailure(dimensionKey, instanceKey),
                runtimeOwns && compositionOwns ? instanceKey : null
            );
        }
        DungeonInstanceAllocationData.InstanceKey runtimeKey = runtimeData.getInstanceKey(dimensionKey);
        DungeonInstanceAllocationData.InstanceKey compositionKey = compositionData.getInstanceKey(dimensionKey);
        boolean ownershipMismatch = runtimeKey != null && compositionKey != null
            && !runtimeKey.equals(compositionKey);
        return GeneratedFloorSession.hydrate(
            runtimeData.getLastGeneratedPlacement(dimensionKey),
            runtimeData.getHubGateState(dimensionKey),
            runtimeData.getCleanupState(dimensionKey),
            compositionData.getState(dimensionKey),
            runtimeData.getGenerationFailure(dimensionKey),
            ownershipMismatch ? null : runtimeKey != null ? runtimeKey : compositionKey
        );
    }

    public @Nullable DungeonInstanceAllocationData.InstanceKey scopeIdentity() {
        return explicitScope ? instanceKey : null;
    }

    public boolean initializeInstance() {
        if (!explicitScope || instanceKey == null) {
            return false;
        }
        return runtimeData.initializeInstance(dimensionKey, instanceKey)
            && compositionData.initializeInstance(dimensionKey, instanceKey);
    }

    public boolean bindInstance(DungeonInstanceAllocationData.InstanceKey instanceKey) {
        if (explicitScope) {
            return this.instanceKey.equals(instanceKey)
                && runtimeData.getInstanceKeys(dimensionKey).contains(instanceKey)
                && compositionData.getInstanceKeys(dimensionKey).contains(instanceKey);
        }
        if (!canBindInstance(instanceKey)) {
            return false;
        }
        boolean bound = runtimeData.bindInstance(dimensionKey, instanceKey)
            && compositionData.bindInstance(dimensionKey, instanceKey);
        if (bound) {
            this.instanceKey = instanceKey;
        }
        return bound;
    }

    public boolean canBindInstance(DungeonInstanceAllocationData.InstanceKey instanceKey) {
        if (explicitScope) {
            return this.instanceKey.equals(instanceKey)
                && runtimeData.getInstanceKeys(dimensionKey).contains(instanceKey)
                && compositionData.getInstanceKeys(dimensionKey).contains(instanceKey);
        }
        return runtimeData.canBindInstance(dimensionKey, instanceKey)
            && compositionData.canBindInstance(dimensionKey, instanceKey);
    }

    public void flushPlacement(GeneratedFloorSession session) {
        if (session.placement() == null) {
            if (instanceKey == null) runtimeData.removeLastGeneratedPlacement(dimensionKey);
            else runtimeData.removeLastGeneratedPlacement(dimensionKey, instanceKey);
        } else {
            if (instanceKey == null) runtimeData.setLastGeneratedPlacement(dimensionKey, session.placement());
            else runtimeData.setLastGeneratedPlacement(dimensionKey, instanceKey, session.placement());
        }
    }

    public void flushHubGate(GeneratedFloorSession session) {
        if (session.hubGateState() == null) {
            if (instanceKey == null) runtimeData.clearHubGateState(dimensionKey);
            else runtimeData.clearHubGateState(dimensionKey, instanceKey);
        } else {
            if (instanceKey == null) runtimeData.setHubGateState(dimensionKey, session.hubGateState());
            else runtimeData.setHubGateState(dimensionKey, instanceKey, session.hubGateState());
        }
    }

    public void flushCleanup(GeneratedFloorSession session) {
        if (session.cleanupState() == null) {
            if (instanceKey == null) runtimeData.removeCleanupState(dimensionKey);
            else runtimeData.removeCleanupState(dimensionKey, instanceKey);
        } else {
            if (instanceKey == null) runtimeData.setCleanupState(dimensionKey, session.cleanupState());
            else runtimeData.setCleanupState(dimensionKey, instanceKey, session.cleanupState());
        }
    }

    public void flushComposition(GeneratedFloorSession session) {
        if (session.compositionState() == null) {
            if (instanceKey == null) compositionData.remove(dimensionKey);
            else compositionData.remove(dimensionKey, instanceKey);
        } else {
            if (instanceKey == null) compositionData.replaceState(dimensionKey, session.compositionState());
            else compositionData.replaceState(dimensionKey, instanceKey, session.compositionState());
        }
    }

    public void flushGenerationFailure(GeneratedFloorSession session) {
        if (session.generationFailure() == null) {
            if (instanceKey == null) runtimeData.clearGenerationFailure(dimensionKey);
            else runtimeData.clearGenerationFailure(dimensionKey, instanceKey);
        } else {
            if (instanceKey == null) runtimeData.setGenerationFailure(dimensionKey, session.generationFailure());
            else runtimeData.setGenerationFailure(dimensionKey, instanceKey, session.generationFailure());
        }
    }
}
