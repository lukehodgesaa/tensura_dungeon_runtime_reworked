package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData.CleanupState;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData.FrontierCandidate;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData.FrontierState;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData.GeneratedRoomRuntimePlan;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData.GenerationFailureState;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData.HubGateRuntimeState;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData.LastGeneratedPlacement;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData.PruneState;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonGameplayComposer;
import com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonCompositionData;
import com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

public final class GeneratedFloorSession {
    @Nullable
    private LastGeneratedPlacement placement;
    @Nullable
    private HubGateRuntimeState hubGateState;
    @Nullable
    private CleanupState cleanupState;
    @Nullable
    private RollingDungeonCompositionData.State compositionState;
    @Nullable
    private GenerationFailureState generationFailure;
    @Nullable
    private final DungeonInstanceAllocationData.InstanceKey instanceKey;

    private GeneratedFloorSession(
        @Nullable LastGeneratedPlacement placement,
        @Nullable HubGateRuntimeState hubGateState,
        @Nullable CleanupState cleanupState,
        @Nullable RollingDungeonCompositionData.State compositionState,
        @Nullable GenerationFailureState generationFailure,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        this.placement = placement;
        this.hubGateState = hubGateState;
        this.cleanupState = cleanupState;
        this.compositionState = compositionState;
        this.generationFailure = generationFailure;
        this.instanceKey = instanceKey;
    }

    public static GeneratedFloorSession hydrate(
        @Nullable LastGeneratedPlacement placement,
        @Nullable HubGateRuntimeState hubGateState,
        @Nullable CleanupState cleanupState,
        @Nullable RollingDungeonCompositionData.State compositionState,
        @Nullable GenerationFailureState generationFailure
    ) {
        return new GeneratedFloorSession(
            placement, hubGateState, cleanupState, compositionState, generationFailure, null
        );
    }

    public static GeneratedFloorSession hydrate(
        @Nullable LastGeneratedPlacement placement,
        @Nullable HubGateRuntimeState hubGateState,
        @Nullable CleanupState cleanupState,
        @Nullable RollingDungeonCompositionData.State compositionState,
        @Nullable GenerationFailureState generationFailure,
        @Nullable DungeonInstanceAllocationData.InstanceKey instanceKey
    ) {
        return new GeneratedFloorSession(
            placement, hubGateState, cleanupState, compositionState, generationFailure, instanceKey
        );
    }

    @Nullable
    public DungeonInstanceAllocationData.InstanceKey instanceKey() {
        return instanceKey;
    }

    @Nullable
    public LastGeneratedPlacement placement() {
        return placement;
    }

    @Nullable
    public HubGateRuntimeState hubGateState() {
        return hubGateState;
    }

    @Nullable
    public CleanupState cleanupState() {
        return cleanupState;
    }

    @Nullable
    public RollingDungeonCompositionData.State compositionState() {
        return compositionState;
    }

    @Nullable
    public GenerationFailureState generationFailure() {
        return generationFailure;
    }

    public void replaceGenerationFailure(@Nullable GenerationFailureState replacement) {
        generationFailure = replacement;
    }

    @Nullable
    public GenerationFailureState clearGenerationFailure() {
        GenerationFailureState removed = generationFailure;
        generationFailure = null;
        return removed;
    }

    public boolean hasPendingCleanup() {
        return cleanupState != null;
    }

    public void replacePlacement(@Nullable LastGeneratedPlacement replacement) {
        placement = replacement;
    }

    @Nullable
    public LastGeneratedPlacement removePlacement() {
        LastGeneratedPlacement removed = placement;
        placement = null;
        return removed;
    }

    public void replaceHubGateState(@Nullable HubGateRuntimeState replacement) {
        hubGateState = replacement;
    }

    public void replaceCleanupState(@Nullable CleanupState replacement) {
        cleanupState = replacement;
    }

    public void initializeComposition(
        DungeonGameplayComposer.Composition composition,
        int initiallySolved,
        RollingDungeonSpatialSolver.GenerationContract generationContract
    ) {
        if (placement != null && !placement.visualTheme().isBlank()) {
            composition = composition.withTerminalTheme(placement.visualTheme());
        }
        compositionState = RollingDungeonCompositionData.State.initialize(
            composition, initiallySolved, generationContract
        );
    }

    public boolean bindLegacyGenerationContract(RollingDungeonSpatialSolver.GenerationContract generationContract) {
        if (compositionState == null || compositionState.hasGenerationContract()) {
            return false;
        }
        compositionState = compositionState.bindGenerationContract(generationContract);
        return true;
    }

    public boolean clearComposition() {
        if (compositionState == null) {
            return false;
        }
        compositionState = null;
        return true;
    }

    @Nullable
    public RollingExpansion commitRollingExpansion(
        int expectedCursor,
        int deferredOffset,
        List<GeneratedRoomRuntimePlan> appendedPlans
    ) {
        if (placement == null || compositionState == null || appendedPlans.isEmpty()) {
            return null;
        }
        int newCursor = expectedCursor + appendedPlans.size();
        RollingDungeonCompositionData.State advanced = deferredOffset > 0
            ? compositionState.deferAndAdvance(expectedCursor, deferredOffset, newCursor)
            : compositionState.advance(expectedCursor, newCursor);
        if (advanced == null) {
            return null;
        }
        List<GeneratedRoomRuntimePlan> merged = new ArrayList<>(placement.roomPlans());
        merged.addAll(appendedPlans);
        LastGeneratedPlacement updated = placement.withRuntime(
            placement.bounds(), placement.controllers(), List.copyOf(merged), placement.frontier(),
            placement.retainedRoute(), placement.pruneState()
        );
        compositionState = advanced;
        placement = updated;
        return new RollingExpansion(advanced, updated, appendedPlans.stream()
            .map(GeneratedRoomRuntimePlan::controllerPos).toList());
    }

    public boolean stageFrontier(BlockPos sourceController, List<FrontierCandidate> candidates) {
        if (placement == null || candidates.isEmpty()) {
            return false;
        }
        placement = placement.withRuntime(
            placement.bounds(), placement.controllers(), placement.roomPlans(),
            new FrontierState(sourceController.immutable(), List.copyOf(candidates)),
            placement.retainedRoute(), placement.pruneState()
        );
        return true;
    }

    public boolean isFrontierCandidate(BlockPos controllerPos) {
        return placement != null && placement.frontier() != null && placement.frontier().candidates().stream()
            .anyMatch(candidate -> candidate.plan().controllerPos().equals(controllerPos));
    }

    @Nullable
    public FrontierCommit commitFrontier(
        BlockPos enteredControllerPos,
        List<GeneratedRoomRuntimePlan> resolvedPlans
    ) {
        if (placement == null || placement.frontier() == null) {
            return null;
        }
        FrontierState frontier = placement.frontier();
        FrontierCandidate selected = frontier.candidates().stream()
            .filter(candidate -> candidate.plan().controllerPos().equals(enteredControllerPos))
            .findFirst().orElse(null);
        if (selected == null) {
            return null;
        }

        List<RoomControllerBlockEntity.RoomBounds> discarded = new ArrayList<>();
        List<BlockPos> controllers = new ArrayList<>(placement.controllers());
        List<RoomControllerBlockEntity.RoomBounds> bounds = new ArrayList<>(placement.bounds());
        for (FrontierCandidate candidate : frontier.candidates()) {
            if (candidate == selected) {
                continue;
            }
            discarded.add(candidate.bounds());
            controllers.remove(candidate.plan().controllerPos());
            bounds.remove(candidate.bounds());
        }

        List<BlockPos> route = new ArrayList<>(placement.retainedRoute());
        route.remove(frontier.sourceController());
        route.remove(enteredControllerPos);
        route.add(frontier.sourceController());
        route.add(enteredControllerPos.immutable());
        while (route.size() > 3) {
            BlockPos old = route.remove(0);
            GeneratedRoomRuntimePlan oldPlan = findPlan(placement.roomPlans(), old);
            if (oldPlan != null && oldPlan.bounds() != null) {
                discarded.add(oldPlan.bounds());
                bounds.remove(oldPlan.bounds());
            }
        }

        LastGeneratedPlacement previous = placement;
        LastGeneratedPlacement committed = placement.withRuntime(
            List.copyOf(bounds), List.copyOf(controllers), List.copyOf(resolvedPlans), null,
            List.copyOf(route), mergePrune(placement.pruneState(), discarded)
        );
        placement = committed;
        return new FrontierCommit(previous, selected, List.copyOf(discarded), List.copyOf(route), committed);
    }

    @Nullable
    public RouteRetention trackActivatedRoute(BlockPos controllerPos) {
        if (placement == null || findPlanIndex(placement.roomPlans(), controllerPos) < 0) {
            return null;
        }
        List<BlockPos> route = new ArrayList<>(placement.retainedRoute());
        if (!route.isEmpty() && route.getLast().equals(controllerPos)) {
            return null;
        }
        route.remove(controllerPos);
        route.add(controllerPos.immutable());
        List<RoomControllerBlockEntity.RoomBounds> bounds = new ArrayList<>(placement.bounds());
        List<RoomControllerBlockEntity.RoomBounds> removed = new ArrayList<>();
        while (route.size() > 3) {
            BlockPos old = route.remove(0);
            GeneratedRoomRuntimePlan plan = findPlan(placement.roomPlans(), old);
            if (plan != null && plan.bounds() != null) {
                removed.add(plan.bounds());
                bounds.remove(plan.bounds());
            }
        }
        placement = placement.withRuntime(
            List.copyOf(bounds), placement.controllers(), placement.roomPlans(), placement.frontier(),
            List.copyOf(route), mergePrune(placement.pruneState(), removed)
        );
        return new RouteRetention(placement, List.copyOf(removed));
    }

    public boolean appendPlacedRoom(GeneratedRoomRuntimePlan roomPlan) {
        if (placement == null) {
            return false;
        }
        List<RoomControllerBlockEntity.RoomBounds> bounds = new ArrayList<>(placement.bounds());
        if (!bounds.contains(roomPlan.bounds())) {
            bounds.add(roomPlan.bounds());
        }
        List<BlockPos> controllers = new ArrayList<>(placement.controllers());
        if (!controllers.contains(roomPlan.controllerPos())) {
            controllers.add(roomPlan.controllerPos().immutable());
        }
        placement = placement.withRuntime(
            List.copyOf(bounds), List.copyOf(controllers), placement.roomPlans(), placement.frontier(),
            placement.retainedRoute(), placement.pruneState()
        );
        return true;
    }

    public boolean removePlacedController(BlockPos controllerPos) {
        if (placement == null || controllerPos == null || !placement.controllers().contains(controllerPos)) {
            return false;
        }
        List<BlockPos> controllers = new ArrayList<>(placement.controllers());
        controllers.remove(controllerPos);
        placement = placement.withRuntime(
            placement.bounds(), List.copyOf(controllers), placement.roomPlans(), placement.frontier(),
            placement.retainedRoute(), placement.pruneState()
        );
        return true;
    }

    public boolean appendPlacedBounds(GeneratedRoomRuntimePlan roomPlan) {
        if (placement == null) {
            return false;
        }
        List<RoomControllerBlockEntity.RoomBounds> bounds = new ArrayList<>(placement.bounds());
        if (!bounds.contains(roomPlan.bounds())) {
            bounds.add(roomPlan.bounds());
        }
        placement = placement.withRuntime(
            List.copyOf(bounds), placement.controllers(), placement.roomPlans(), placement.frontier(),
            placement.retainedRoute(), placement.pruneState()
        );
        return true;
    }

    public boolean enqueueCleanupForPlacement() {
        if (placement == null || placement.bounds().isEmpty()) {
            return false;
        }
        List<RoomControllerBlockEntity.RoomBounds> cleanupBounds = new ArrayList<>(placement.bounds());
        if (placement.pruneState() != null) {
            for (int i = placement.pruneState().boundsIndex(); i < placement.pruneState().bounds().size(); i++) {
                RoomControllerBlockEntity.RoomBounds pending = placement.pruneState().bounds().get(i);
                if (!cleanupBounds.contains(pending)) {
                    cleanupBounds.add(pending);
                }
            }
        }
        cleanupState = new CleanupState(
            placement.floorBuildOrigin(), List.copyOf(cleanupBounds), 0, 0L, 0L, 0L, 0L
        );
        return true;
    }

    @Nullable
    public CleanupState removeCleanupState() {
        CleanupState removed = cleanupState;
        cleanupState = null;
        return removed;
    }

    @Nullable
    public CleanupProgress advanceCleanup(
        int nextBoundsIndex,
        long nextBoundsOffset,
        long addedVisited,
        long addedCleared
    ) {
        return advanceCleanup(
            nextBoundsIndex,
            nextBoundsOffset,
            addedVisited,
            addedCleared,
            cleanupState != null && cleanupState.liquidsObserved(),
            cleanupState == null ? 0 : cleanupState.confirmPasses()
        );
    }

    public CleanupProgress advanceCleanup(
        int nextBoundsIndex,
        long nextBoundsOffset,
        long addedVisited,
        long addedCleared,
        boolean liquidsThisTick,
        int nextConfirmPasses
    ) {
        if (cleanupState == null) {
            return null;
        }
        CleanupState next = cleanupState.advance(
            nextBoundsIndex,
            nextBoundsOffset,
            addedVisited,
            addedCleared,
            liquidsThisTick,
            nextConfirmPasses
        );
        boolean completed = next.complete();
        cleanupState = completed ? null : next;
        return new CleanupProgress(next, completed);
    }

    public CleanupProgress setLiquidSweepBounds(
        List<RoomControllerBlockEntity.RoomBounds> sweepBounds
    ) {
        if (cleanupState == null) {
            return null;
        }
        cleanupState = cleanupState.withLiquidSweepBounds(sweepBounds);
        return new CleanupProgress(cleanupState, cleanupState.complete());
    }

    public CleanupProgress advanceLiquidSweep(
        int nextBoundsIndex,
        long nextBoundsOffset,
        long addedCleared,
        long[] nextFrontier,
        int nextFrontierIndex
    ) {
        if (cleanupState == null) {
            return null;
        }
        CleanupState next = cleanupState.advanceLiquidSweep(
            nextBoundsIndex,
            nextBoundsOffset,
            addedCleared,
            nextFrontier,
            nextFrontierIndex
        );
        boolean completed = next.complete();
        cleanupState = completed ? null : next;
        return new CleanupProgress(next, completed);
    }

    public boolean clearCompletedPrune() {
        if (placement == null || placement.pruneState() == null || !placement.pruneState().complete()) {
            return false;
        }
        placement = placement.withRuntime(
            placement.bounds(), placement.controllers(), placement.roomPlans(), placement.frontier(),
            placement.retainedRoute(), null
        );
        return true;
    }

    @Nullable
    public PruneProgress advancePrune(int nextBoundsIndex, long nextBoundsOffset) {
        if (placement == null || placement.pruneState() == null) {
            return null;
        }
        PruneState next = placement.pruneState().advance(nextBoundsIndex, nextBoundsOffset);
        boolean completed = next.complete();
        placement = placement.withRuntime(
            placement.bounds(), placement.controllers(), placement.roomPlans(), placement.frontier(),
            placement.retainedRoute(), completed ? null : next
        );
        return new PruneProgress(next, completed, placement);
    }

    public int findPlanIndex(BlockPos controllerPos) {
        return placement == null ? -1 : findPlanIndex(placement.roomPlans(), controllerPos);
    }

    private static int findPlanIndex(List<GeneratedRoomRuntimePlan> plans, BlockPos controllerPos) {
        for (int i = 0; i < plans.size(); i++) {
            if (plans.get(i).controllerPos().equals(controllerPos)) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static GeneratedRoomRuntimePlan findPlan(List<GeneratedRoomRuntimePlan> plans, BlockPos controllerPos) {
        return plans.stream().filter(plan -> plan.controllerPos().equals(controllerPos)).findFirst().orElse(null);
    }

    @Nullable
    private static PruneState mergePrune(
        @Nullable PruneState current,
        List<RoomControllerBlockEntity.RoomBounds> added
    ) {
        List<RoomControllerBlockEntity.RoomBounds> queued = new ArrayList<>();
        if (current != null) {
            for (int i = current.boundsIndex(); i < current.bounds().size(); i++) {
                queued.add(current.bounds().get(i));
            }
        }
        for (RoomControllerBlockEntity.RoomBounds bounds : added) {
            if (!queued.contains(bounds)) {
                queued.add(bounds);
            }
        }
        return queued.isEmpty() ? null : new PruneState(List.copyOf(queued), 0, 0L);
    }

    public record RollingExpansion(
        RollingDungeonCompositionData.State composition,
        LastGeneratedPlacement placement,
        List<BlockPos> appendedControllers
    ) {
    }

    public record FrontierCommit(
        LastGeneratedPlacement previousPlacement,
        FrontierCandidate selected,
        List<RoomControllerBlockEntity.RoomBounds> discardedBounds,
        List<BlockPos> retainedRoute,
        LastGeneratedPlacement committedPlacement
    ) {
    }

    public record RouteRetention(
        LastGeneratedPlacement placement,
        List<RoomControllerBlockEntity.RoomBounds> removedBounds
    ) {
    }

    public record CleanupProgress(CleanupState state, boolean completed) {
    }

    public record PruneProgress(PruneState state, boolean completed, LastGeneratedPlacement placement) {
    }
}
