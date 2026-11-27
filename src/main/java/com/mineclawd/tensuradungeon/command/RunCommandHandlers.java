package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity.ValidationReport;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.RoomDebugSnapshot;
import com.mineclawd.tensuradungeon.block.entity.RoomObjectiveType;
import com.mineclawd.tensuradungeon.block.entity.RoomProfile;
import com.mineclawd.tensuradungeon.block.entity.RoomProtectionView;
import com.mineclawd.tensuradungeon.block.entity.StructureMarkerReadinessReport;
import com.mineclawd.tensuradungeon.block.entity.StructureMarkerReadinessValidator;
import com.mineclawd.tensuradungeon.compat.TensuraSkillCompatHooks;
import com.mineclawd.tensuradungeon.event.ActiveRoomProtectionEvents;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mineclawd.tensuradungeon.registry.ModDimensions;
import com.mineclawd.tensuradungeon.runtime.DungeonBudgetEngine;
import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.DungeonHudHooks;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.DungeonModifierDefinition;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.DungeonRunService;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRepository;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorRuntimeData;
import com.mineclawd.tensuradungeon.runtime.RewardMailboxData;
import com.mineclawd.tensuradungeon.runtime.RoomBudgetResult;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.formatDecimal;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.formatNullablePos;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.formatPos;

final class RunCommandHandlers {
    static final int PRESET_SCAN_RADIUS = 96;
    private static final int CHAINED_PRESET_DEFAULT_STEP_RADIUS = 256;
    private static final int CHAINED_PRESET_MAX_STEP_RADIUS = 512;
    private static final int CHAINED_PRESET_MAX_ROOMS = 32;
    private static final int CHAINED_PRESET_MAX_STEPS = 32;
    static final int POST_PLACEMENT_CLAIM_DEFAULT_RADIUS = 100;
    private static final int POST_PLACEMENT_CLAIM_MAX_RADIUS = 256;
    private static final Map<String, BlockPos> HUB_SAFE_ZONE_PENDING = new HashMap<>();

    private RunCommandHandlers() {
    }

    static int startRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (manager.isActiveRun()) {
            source.sendFailure(Component.literal("A dungeon run is already active. Use /tensuradungeon run end or /tensuradungeon run reset first."));
            return 0;
        }
        manager.startRun(level);
        source.sendSuccess(() -> Component.literal("Dungeon run started."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugFloorProgress(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonFloorProgressData floorProgress = DungeonFloorProgressData.get(level);
        DungeonRunManager manager = DungeonRunManager.get(level);
        RewardMailboxData mailbox = RewardMailboxData.get(level);
        DungeonFloorProgressData.PlayerFloorProgress progress = floorProgress.getOrCreate(player.getUUID());
        source.sendSuccess(() -> Component.literal("Floor Progress Debug"), false);
        source.sendSuccess(() -> Component.literal("Highest Unlocked Floor: " + progress.highestUnlockedFloor()), false);
        source.sendSuccess(() -> Component.literal("Completed Floors: " + floorProgress.getCompletedFloorsSummary(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Last Started Floor: " + (progress.lastStartedFloor() <= 0 ? "NONE" : progress.lastStartedFloor())), false);
        source.sendSuccess(() -> Component.literal("Last Completed Floor: " + (progress.lastCompletedFloor() <= 0 ? "NONE" : progress.lastCompletedFloor())), false);
        source.sendSuccess(() -> Component.literal("Last Completion Game Time: " + progress.lastCompletionGameTime()), false);
        source.sendSuccess(() -> Component.literal("Current Run Active: " + manager.isActiveRun()), false);
        source.sendSuccess(() -> Component.literal("Current Run Floor: " + manager.getFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Current Run Completed: " + manager.isRunCompleted()), false);
        source.sendSuccess(() -> Component.literal("Next Floor Eligible: " + manager.isNextFloorEligible()), false);
        source.sendSuccess(() -> Component.literal("Next Floor Number: " + manager.getNextFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Archived Preset Order Debug: " + manager.getArchivedPresetOrderSummary()), false);
        source.sendSuccess(() -> Component.literal("Stage 13 Floor Max EP Awarded Floors: " + mailbox.getAwardedFloorCompletionFloorsSummary(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Reward Mailbox Package Count: " + mailbox.getMailbox(player.getUUID()).size()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int clearFloorProgress(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonFloorProgressData floorProgress = DungeonFloorProgressData.get(level);
        RewardMailboxData mailbox = RewardMailboxData.get(level);
        floorProgress.clearProgress(player.getUUID());
        source.sendSuccess(() -> Component.literal("Floor progress cleared."), false);
        source.sendSuccess(() -> Component.literal("Highest Unlocked Floor: " + floorProgress.getHighestUnlockedFloor(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Completed Floors: " + floorProgress.getCompletedFloorsSummary(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Stage 13 Floor Max EP Awarded Floors Untouched: " + mailbox.getAwardedFloorCompletionFloorsSummary(player.getUUID())), false);
        return Command.SINGLE_SUCCESS;
    }

    static int startFloorRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int requestedFloor = IntegerArgumentType.getInteger(context, "floor");
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (GeneratedLinearConnectorPlacementCommands.blockIfGeneratedCleanupPending(source, level)) return 0;
        DungeonFloorProgressData floorProgress = DungeonFloorProgressData.get(level);
        RewardMailboxData mailbox = RewardMailboxData.get(level);
        RoomControllerBlockEntity controller = TensuraDungeonCommands.findNearestController(level, player.blockPosition());
        DungeonRunService.FloorStartOperationResult operation =
            manager.startFloor(level, player.getUUID(), requestedFloor, controller);
        DungeonFloorProgressData.FloorStartResult startResult = operation.floorStart();
        if ("FLOOR_LOCKED".equals(operation.reason())) {
            source.sendFailure(Component.literal(requestedFloor < 1 ? "Floor must be at least 1." : "Floor " + requestedFloor + " is locked. Highest unlocked floor: " + startResult.highestUnlockedFloor() + "."));
            return 0;
        }
        if ("CONTROLLER_ACTIVE".equals(operation.reason())) {
            source.sendFailure(Component.literal("Cannot start floor while a room is active."));
            return 0;
        }
        if ("RUN_IN_PROGRESS".equals(operation.reason())) {
            source.sendFailure(Component.literal("Cannot replace an in-progress floor. Use /tensuradungeon run reset or complete it first."));
            return 0;
        }
        boolean preparedController = operation.preparedController();
        source.sendSuccess(() -> Component.literal("Floor Start: PASS"), false);
        source.sendSuccess(() -> Component.literal("Requested Floor: " + requestedFloor), false);
        source.sendSuccess(() -> Component.literal("Highest Unlocked Floor: " + floorProgress.getHighestUnlockedFloor(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Redo Floor: " + startResult.redoFloor()), false);
        source.sendSuccess(() -> Component.literal("Active Run Floor: " + manager.getFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Prepared Nearest Room Controller: " + preparedController), false);
        source.sendSuccess(() -> Component.literal("Completed Floors: " + floorProgress.getCompletedFloorsSummary(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Stage 13 Floor Max EP Awarded Floors: " + mailbox.getAwardedFloorCompletionFloorsSummary(player.getUUID())), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        DungeonFloorProgressData floorProgress = DungeonFloorProgressData.get(player.serverLevel());
        DungeonFloorProgressData.PlayerFloorProgress progress = floorProgress.getOrCreate(player.getUUID());
        source.sendSuccess(() -> Component.literal("Run Active: " + manager.isActiveRun()), false);
        source.sendSuccess(() -> Component.literal("Run Requires Active Room Gate: " + manager.requiresActiveRunForRoomActivation()), false);
        source.sendSuccess(() -> Component.literal("Debug Room Activation Override: " + manager.isDebugRoomActivationOverride()), false);
        source.sendSuccess(() -> Component.literal("Run Session ID / Floor / Seed: " + manager.getRunSessionId() + " / " + manager.getFloorNumber() + " / " + manager.getRunSeed()), false);
        source.sendSuccess(() -> Component.literal("Current Floor Base Budget: " + manager.getCurrentFloorBaseBudget()), false);
        source.sendSuccess(() -> Component.literal("Current Floor: " + manager.getFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Floor Mode: " + manager.getFloorModeLabel()), false);
        source.sendSuccess(() -> Component.literal("Player Highest Unlocked Floor: " + progress.highestUnlockedFloor()), false);
        source.sendSuccess(() -> Component.literal("Player Completed Floors: " + floorProgress.getCompletedFloorsSummary(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Player Last Started Floor: " + (progress.lastStartedFloor() <= 0 ? "NONE" : progress.lastStartedFloor())), false);
        source.sendSuccess(() -> Component.literal("Player Last Completed Floor: " + (progress.lastCompletedFloor() <= 0 ? "NONE" : progress.lastCompletedFloor())), false);
        source.sendSuccess(() -> Component.literal("Run Completed: " + manager.isRunCompleted()), false);
        source.sendSuccess(() -> Component.literal("Floor Completed: " + manager.isFloorCompleted()), false);
        source.sendSuccess(() -> Component.literal("Next Floor Eligible: " + manager.isNextFloorEligible()), false);
        source.sendSuccess(() -> Component.literal("Next Floor Number: " + manager.getNextFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Target / Completed / Remaining Rooms: " + manager.getTargetRoomCount() + " / " + manager.getCompletedRoomCount() + " / " + manager.getRemainingRoomCount()), false);
        source.sendSuccess(() -> Component.literal("Run Progress Summary: " + manager.getRunProgressSummary()), false);
        source.sendSuccess(() -> Component.literal("Active Room Count: " + manager.getActiveRoomCount()), false);
        source.sendSuccess(() -> Component.literal("Active Room Controller Pos: " + manager.getActiveRoomControllerPosString()), false);
        source.sendSuccess(() -> Component.literal("Active Room Positions: " + manager.getActiveRoomPositionsSummary()), false);
        source.sendSuccess(() -> Component.literal("Completed Room Positions: " + manager.getCompletedRoomPositionsSummary()), false);
        source.sendSuccess(() -> Component.literal("Run Failed: " + manager.isFailed()), false);
        source.sendSuccess(() -> Component.literal("Run Failure Reason: " + manager.getFailureReason()), false);
        source.sendSuccess(() -> Component.literal("Run Completion Reason: " + manager.getCompletionReason()), false);
        source.sendSuccess(() -> Component.literal("Floor Completion Reason: " + manager.getCompletionReason()), false);
        source.sendSuccess(() -> Component.literal("Active Modifiers: " + manager.getModifierIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Active: " + manager.isExitPortalActive()), false);
        source.sendSuccess(() -> Component.literal("Hub Enabled: " + manager.isHubEnabled()), false);
        source.sendSuccess(() -> Component.literal("Player Inside Hub Safe Zone: " + manager.isPlayerInsideHubSafeZone(player.blockPosition())), false);
        source.sendSuccess(() -> Component.literal("Hub Spawn Anchor: " + TensuraDungeonCommands.formatNullablePos(manager.getHubSpawnAnchorPos())), false);
        source.sendSuccess(() -> Component.literal("Hub Gate Anchor: " + TensuraDungeonCommands.formatNullablePos(manager.getHubGateAnchorPos())), false);
        source.sendSuccess(() -> Component.literal("Hub Gate State: " + manager.getHubGateState()), false);
        source.sendSuccess(() -> Component.literal("Hub Floor Prepared: " + manager.isPreparedFloorReady()), false);
        source.sendSuccess(() -> Component.literal("Hub Prepared Floor: " + (manager.isPreparedFloorReady() ? manager.getPreparedFloorNumber() : "NONE")), false);
        source.sendSuccess(() -> Component.literal("Hub Prepared Preset Rooms: " + (manager.getHubPreparedPresetRooms() > 0 ? manager.getHubPreparedPresetRooms() : "NONE")), false);
        source.sendSuccess(() -> Component.literal("Preset Armed From Hub: " + manager.isPresetArmedFromHub()), false);
        source.sendSuccess(() -> Component.literal("Selected Floor: " + manager.getPreparedFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Selected Modifiers: " + manager.getPreparedModifierIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Retry Preserves Modifiers: " + manager.isRetryPreservingModifiers()), false);
        source.sendSuccess(() -> Component.literal("Last Hub Transition Reason: " + manager.getLastHubTransitionReason()), false);
        source.sendSuccess(() -> Component.literal("Floor Mode: " + manager.getFloorModeLabel()), false);
        source.sendSuccess(() -> Component.literal("Preset Mode: " + manager.isPresetMode()), false);
        source.sendSuccess(() -> Component.literal("Preset Room Count: " + manager.getPresetRoomCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Current Index: " + manager.getPresetCurrentIndex()), false);
        source.sendSuccess(() -> Component.literal("Preset Expected Controller: " + TensuraDungeonCommands.formatNullablePos(manager.getPresetExpectedControllerPos())), false);
        source.sendSuccess(() -> Component.literal("Preset Completed Count: " + manager.getPresetCompletedCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Remaining Count: " + manager.getPresetRemainingCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Transition Reason: " + manager.getLastPresetTransitionReason()), false);
        source.sendSuccess(() -> Component.literal("Preset Rejected Controller: " + manager.getLastPresetRejectedControllerSummary()), false);
        source.sendSuccess(() -> Component.literal("Preset Order Debug: " + manager.getPresetOrderDebugSummary()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Pos: " + manager.getExitPortalPosString()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Source: " + manager.getExitPortalSource()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Reason: " + manager.getExitPortalReason()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Details: " + manager.getExitPortalReadinessDetails()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Debug State: " + manager.getExitPortalDebugState(player.serverLevel())), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Block Present: " + manager.isExitPortalBlockPresent(player.serverLevel())), false);
        source.sendSuccess(() -> Component.literal("Active Registered Protection Controllers: " + ActiveRoomProtectionEvents.getRegisteredProtectionControllerCount(player.serverLevel())), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Source: " + ActiveRoomProtectionEvents.getLastProtectionLookupSource()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Used World Scan: " + ActiveRoomProtectionEvents.wasLastProtectionLookupUsedWorldScan()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Attempted Marker Refresh: " + ActiveRoomProtectionEvents.wasLastProtectionLookupAttemptedMarkerRefresh()), false);
        source.sendSuccess(() -> Component.literal("Protection Mob Grief Events / Blocked: " + ActiveRoomProtectionEvents.getMobGriefEventsFired() + " / " + ActiveRoomProtectionEvents.getMobGriefEventsBlocked()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugRunExitPortal(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        source.sendSuccess(() -> Component.literal("Exit Portal Active: " + manager.isExitPortalActive()), false);
        source.sendSuccess(() -> Component.literal("Floor Mode: " + manager.getFloorModeLabel()), false);
        source.sendSuccess(() -> Component.literal("Preset Mode: " + manager.isPresetMode()), false);
        source.sendSuccess(() -> Component.literal("Preset Room Count: " + manager.getPresetRoomCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Current Index: " + manager.getPresetCurrentIndex()), false);
        source.sendSuccess(() -> Component.literal("Preset Expected Controller: " + TensuraDungeonCommands.formatNullablePos(manager.getPresetExpectedControllerPos())), false);
        source.sendSuccess(() -> Component.literal("Preset Completed Count: " + manager.getPresetCompletedCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Remaining Count: " + manager.getPresetRemainingCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Transition Reason: " + manager.getLastPresetTransitionReason()), false);
        source.sendSuccess(() -> Component.literal("Preset Rejected Controller: " + manager.getLastPresetRejectedControllerSummary()), false);
        source.sendSuccess(() -> Component.literal("Preset Order Debug: " + manager.getPresetOrderDebugSummary()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Pos: " + manager.getExitPortalPosString()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Source: " + manager.getExitPortalSource()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Reason: " + manager.getExitPortalReason()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Details: " + manager.getExitPortalReadinessDetails()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Debug State: " + manager.getExitPortalDebugState(player.serverLevel())), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Block Present: " + manager.isExitPortalBlockPresent(player.serverLevel())), false);
        source.sendSuccess(() -> Component.literal("Active Registered Protection Controllers: " + ActiveRoomProtectionEvents.getRegisteredProtectionControllerCount(player.serverLevel())), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Source: " + ActiveRoomProtectionEvents.getLastProtectionLookupSource()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Used World Scan: " + ActiveRoomProtectionEvents.wasLastProtectionLookupUsedWorldScan()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Attempted Marker Refresh: " + ActiveRoomProtectionEvents.wasLastProtectionLookupAttemptedMarkerRefresh()), false);
        source.sendSuccess(() -> Component.literal("Protection Mob Grief Events / Blocked: " + ActiveRoomProtectionEvents.getMobGriefEventsFired() + " / " + ActiveRoomProtectionEvents.getMobGriefEventsBlocked()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugRunModifiers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        source.sendSuccess(() -> Component.literal("Active Modifiers: " + manager.getModifierIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Modifier Count: " + manager.getModifierCount()), false);
        source.sendSuccess(() -> Component.literal("Modifier IDs: " + manager.getModifierIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Modifier Display Names: " + manager.getModifierDisplayNamesSummary()), false);
        source.sendSuccess(() -> Component.literal("Modifier Categories: " + manager.getModifierCategoriesSummary()), false);
        source.sendSuccess(() -> Component.literal("Modifier Budget Multiplier: " + TensuraDungeonCommands.formatDecimal(manager.getModifierBudgetMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Modifier Mob Count Multiplier: " + TensuraDungeonCommands.formatDecimal(manager.getModifierMobCountMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Modifier Health Multiplier: " + TensuraDungeonCommands.formatDecimal(manager.getModifierHealthMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Modifier Max Alive Bonus: " + manager.getModifierMaxAliveBonus()), false);
        source.sendSuccess(() -> Component.literal("Modifier Performance Cost: " + manager.getModifierPerformanceCostSummary()), false);
        source.sendSuccess(() -> Component.literal("Modifier Reason: " + manager.getModifierReason()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int setRunModifier(CommandContext<CommandSourceStack> context, String modifierId) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        if (!manager.isActiveRun()) {
            source.sendFailure(Component.literal("No active dungeon run. Start a run before changing modifiers."));
            return 0;
        }
        if (manager.getActiveRoomCount() > 0) {
            source.sendFailure(Component.literal("Cannot change modifiers while a room is active."));
            return 0;
        }
        if (!manager.setForcedModifier(modifierId)) {
            source.sendFailure(Component.literal("Unknown modifier id: " + modifierId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dungeon run modifier set to " + modifierId + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int clearRunModifiers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        if (!manager.isActiveRun()) {
            source.sendFailure(Component.literal("No active dungeon run. Start a run before changing modifiers."));
            return 0;
        }
        if (manager.getActiveRoomCount() > 0) {
            source.sendFailure(Component.literal("Cannot change modifiers while a room is active."));
            return 0;
        }
        manager.clearModifiers();
        source.sendSuccess(() -> Component.literal("Dungeon run modifiers cleared."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int setRunFloorNumber(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        int floorNumber = IntegerArgumentType.getInteger(context, "number");
        if (!manager.isActiveRun()) {
            source.sendFailure(Component.literal("No active dungeon run. Start a run before changing floor number."));
            return 0;
        }
        if (manager.getActiveRoomCount() > 0) {
            source.sendFailure(Component.literal("Cannot change floor number while a room is active."));
            return 0;
        }
        if (!manager.setFloorNumberForTesting(floorNumber)) {
            source.sendFailure(Component.literal("Floor number could not be changed."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dungeon run floor number set to " + manager.getFloorNumber() + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int setRunTargetRoomCount(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        int targetRoomCount = IntegerArgumentType.getInteger(context, "count");
        if (!manager.isActiveRun()) {
            source.sendFailure(Component.literal("No active dungeon run. Start a run before changing target room count."));
            return 0;
        }
        if (manager.getActiveRoomCount() > 0 || manager.getCompletedRoomCount() > 0) {
            source.sendFailure(Component.literal("Cannot change target room count after floor progress has begun."));
            return 0;
        }
        if (!manager.setTargetRoomCountForTesting(targetRoomCount)) {
            source.sendFailure(Component.literal("Target room count could not be changed."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dungeon run target room count set to " + manager.getTargetRoomCount() + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int advanceRunFloor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        RoomControllerBlockEntity controller = TensuraDungeonCommands.findNearestController(level, player.blockPosition());
        DungeonRunService.AdvanceFloorOperationResult operation =
            manager.advanceFloorFromCommand(level, player.getUUID(), controller);
        if ("ACTIVE_ROOM".equals(operation.reason())) {
            source.sendFailure(Component.literal("Cannot advance floor while a room is active."));
            return 0;
        }
        return renderAdvanceFloorResult(source, player, level, manager, operation);
    }

    static int continueRunFromPortal(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            playerInstanceKey(level, player);
        DungeonRunService.PortalCommandOperationResult operation = instanceKey == null
            ? manager.continueFromPortalCommand(
                level, player, () -> GeneratedLinearConnectorPlacementCommands
                    .cleanupGeneratedFloorAfterHubReturn(level, source)
            )
            : manager.continueFromPortalCommand(instanceKey, level, player);
        if (!operation.succeeded()) {
            source.sendFailure(Component.literal("Exit portal continue blocked: " + operation.reason()));
            source.sendFailure(Component.literal("Exit Portal Reason: " + manager.getExitPortalReason()));
            source.sendFailure(Component.literal("Exit Portal Details: " + manager.getExitPortalReadinessDetails()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Returned to hub via exit portal."), false);
        source.sendSuccess(() -> Component.literal("Hub Gate State: " + manager.getHubGateState()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int returnHomeFromPortal(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            playerInstanceKey(level, player);
        DungeonRunService.PortalCommandOperationResult operation = instanceKey == null
            ? manager.returnHomeFromPortalCommand(
                level, player, () -> GeneratedLinearConnectorPlacementCommands
                    .cleanupGeneratedFloorAfterHubReturn(level, source)
            )
            : manager.returnHomeFromPortalCommand(instanceKey, level, player);
        if (!operation.succeeded()) {
            source.sendFailure(Component.literal("Exit portal return-home blocked: " + operation.reason()));
            if ("OVERWORLD_UNAVAILABLE".equals(operation.reason())) {
                return 0;
            }
            source.sendFailure(Component.literal("Exit Portal Reason: " + manager.getExitPortalReason()));
            source.sendFailure(Component.literal("Exit Portal Details: " + manager.getExitPortalReadinessDetails()));
            return 0;
        }
        BlockPos spawnPos = operation.sharedSpawn();
        source.sendSuccess(() -> Component.literal("Returned home via exit portal."), false);
        source.sendSuccess(() -> Component.literal("Overworld Shared Spawn: " + TensuraDungeonCommands.formatPos(spawnPos)), false);
        source.sendSuccess(() -> Component.literal("Run Cleared: true"), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Active: " + manager.isExitPortalActive()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int endRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            playerInstanceKey(level, player);
        if (instanceKey == null) {
            DungeonFloorProgressData.get(level).clearPreparedModifierPlan(
                player.getUUID(), manager.getFloorNumber()
            );
            manager.endRunWithControllerCleanup(level);
        } else if (!manager.abandonInstance(
            instanceKey, level, player, "INSTANCE_RUN_ABANDONED"
        )) {
            source.sendFailure(Component.literal("Dungeon instance abandonment was rejected."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dungeon run ended."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int resetRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        DungeonInstanceAllocationData.InstanceKey instanceKey =
            playerInstanceKey(level, player);
        if (instanceKey == null) {
            DungeonFloorProgressData.get(level).clearPreparedModifierPlan(
                player.getUUID(), manager.getFloorNumber()
            );
            manager.resetRunWithControllerCleanup(level);
        } else if (!manager.abandonInstance(
            instanceKey, level, player, "INSTANCE_RUN_RESET"
        )) {
            source.sendFailure(Component.literal("Dungeon instance reset was rejected."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dungeon run reset."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static DungeonInstanceAllocationData.InstanceKey playerInstanceKey(
        ServerLevel level,
        ServerPlayer player
    ) {
        return DungeonInstanceAllocationData.get(level)
            .forPlayer(player.getUUID())
            .map(DungeonInstanceAllocationData.Allocation::key)
            .orElse(null);
    }

    static int debugHub(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        source.sendSuccess(() -> Component.literal("Hub Enabled: " + manager.isHubEnabled()), false);
        source.sendSuccess(() -> Component.literal("Player Inside Hub Safe Zone: " + manager.isPlayerInsideHubSafeZone(player.blockPosition())), false);
        source.sendSuccess(() -> Component.literal("Hub Spawn Anchor: " + TensuraDungeonCommands.formatNullablePos(manager.getHubSpawnAnchorPos())), false);
        source.sendSuccess(() -> Component.literal("Hub Return Anchor: " + TensuraDungeonCommands.formatNullablePos(manager.getHubReturnAnchorPos())), false);
        source.sendSuccess(() -> Component.literal("Hub Gate Anchor: " + TensuraDungeonCommands.formatNullablePos(manager.getHubGateAnchorPos())), false);
        source.sendSuccess(() -> Component.literal("Hub Safe Zone Corner A: " + TensuraDungeonCommands.formatNullablePos(manager.getHubSafeZoneCornerA())), false);
        source.sendSuccess(() -> Component.literal("Hub Safe Zone Corner B: " + TensuraDungeonCommands.formatNullablePos(manager.getHubSafeZoneCornerB())), false);
        source.sendSuccess(() -> Component.literal("Hub Gate State: " + manager.getHubGateState()), false);
        source.sendSuccess(() -> Component.literal("Selected Floor: " + manager.getPreparedFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Selected Modifiers: " + manager.getPreparedModifierIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Retry Preserves Modifiers: " + manager.isRetryPreservingModifiers()), false);
        source.sendSuccess(() -> Component.literal("Retry Floor: " + manager.getRetryFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Retry Modifiers: " + manager.getRetryModifierIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Archived Preset Order Debug: " + manager.getArchivedPresetOrderSummary()), false);
        source.sendSuccess(() -> Component.literal("Last Hub Transition Reason: " + manager.getLastHubTransitionReason()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int setHubSpawn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        manager.setHubSpawnAnchor(player.blockPosition());
        manager.setHubReturnAnchor(player.blockPosition());
        source.sendSuccess(() -> Component.literal("Hub spawn anchor set to " + TensuraDungeonCommands.formatPos(player.blockPosition()) + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int setHubReturn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        manager.setHubReturnAnchor(player.blockPosition());
        source.sendSuccess(() -> Component.literal("Hub return anchor set to " + TensuraDungeonCommands.formatPos(player.blockPosition()) + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int setHubGate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        manager.setHubGateAnchor(player.blockPosition());
        source.sendSuccess(() -> Component.literal("Hub gate anchor set to " + TensuraDungeonCommands.formatPos(player.blockPosition()) + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int setHubSafeZone(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        String key = player.serverLevel().dimension().location().toString();
        BlockPos pending = HUB_SAFE_ZONE_PENDING.get(key);
        if (pending == null) {
            HUB_SAFE_ZONE_PENDING.put(key, player.blockPosition().immutable());
            source.sendSuccess(() -> Component.literal("Hub safe zone corner A set to " + TensuraDungeonCommands.formatPos(player.blockPosition()) + ". Run the command again at the opposite corner."), false);
            return Command.SINGLE_SUCCESS;
        }
        manager.setHubSafeZone(pending, player.blockPosition());
        HUB_SAFE_ZONE_PENDING.remove(key);
        source.sendSuccess(() -> Component.literal("Hub safe zone set from " + TensuraDungeonCommands.formatPos(pending) + " to " + TensuraDungeonCommands.formatPos(player.blockPosition()) + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int returnToHub(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        if (!manager.returnPlayerToHubFromCommand(player.serverLevel(), player, "MANUAL_HUB_RETURN")) {
            source.sendFailure(Component.literal("Hub return blocked: hub spawn/return anchor missing."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Returned to hub."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int prepareHubFloor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        DungeonFloorProgressData floorProgress = DungeonFloorProgressData.get(level);
        int floor = IntegerArgumentType.getInteger(context, "floor");
        DungeonRunService.HubFloorPreparationResult preparation =
            manager.prepareFloorFromHubCommand(level, player.getUUID(), floor);
        if ("FLOOR_LOCKED".equals(preparation.reason())) {
            source.sendFailure(Component.literal("Floor " + floor + " is locked. Highest unlocked floor: " + floorProgress.getHighestUnlockedFloor(player.getUUID()) + "."));
            return 0;
        }
        if (!preparation.succeeded()) {
            source.sendFailure(Component.literal("Hub floor preparation failed."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Prepared floor " + manager.getPreparedFloorNumber() + " from hub."), false);
        source.sendSuccess(() -> Component.literal("Hub Gate State: " + manager.getHubGateState()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int prepareHubPreset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        int rooms = IntegerArgumentType.getInteger(context, "rooms");
        if (!manager.isHubEnabled() || manager.getHubSpawnAnchorPos() == null || manager.getHubGateAnchorPos() == null) {
            source.sendFailure(Component.literal("Hub is not configured enough. Set hub spawn and gate first."));
            return 0;
        }
        if (!manager.isPreparedFloorReady()) {
            source.sendFailure(Component.literal("No hub floor prepared. Use /tensuradungeon hub preparefloor <floor> first."));
            return 0;
        }
        if (rooms <= 0) {
            source.sendFailure(Component.literal("Preset room count must be greater than 0."));
            return 0;
        }
        return armPresetFloor(
            source, player, level, manager, manager.getPreparedFloorNumber(), rooms, true
        );
    }

    static int openHubGate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        manager.setHubGateState(manager.isPreparedFloorReady() ? "READY" : "PREPARING", "HUB_GATE_OPENED");
        source.sendSuccess(() -> Component.literal("Hub gate state set to " + manager.getHubGateState() + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int closeHubGate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        DungeonRunManager manager = DungeonRunManager.get(player.serverLevel());
        manager.setHubGateState("CLOSED", "HUB_GATE_CLOSED");
        source.sendSuccess(() -> Component.literal("Hub gate closed."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int armPresetFloor(
        CommandSourceStack source,
        ServerPlayer player,
        ServerLevel level,
        DungeonRunManager manager,
        int requestedFloor,
        int rooms,
        boolean armedFromHub
    ) {
        List<PresetRoomCandidate> candidates =
            findPresetRoomCandidates(level, player.blockPosition());
        return armPresetFloor(
            source, player, level, manager, requestedFloor, rooms, armedFromHub, candidates, null
        );
    }

    static int armPresetFloor(
        CommandSourceStack source,
        ServerPlayer player,
        ServerLevel level,
        DungeonRunManager manager,
        int requestedFloor,
        int rooms,
        boolean armedFromHub,
        List<PresetRoomCandidate> candidates,
        ChainedPresetScanResult chainedResult
    ) {
        List<PresetRoomCandidate> usable = candidates.stream()
            .filter(PresetRoomCandidate::usable)
            .limit(rooms)
            .toList();
        if (usable.size() < rooms) {
            source.sendFailure(Component.literal(
                "Not enough usable preset room controllers nearby. Needed " + rooms + ", found " + usable.size() + "."
            ));
            return 0;
        }

        List<BlockPos> order = usable.stream().map(PresetRoomCandidate::pos).toList();
        DungeonRunService.PresetArmOperationResult operation = manager.armPresetFloor(
            level, player.getUUID(), requestedFloor, rooms, armedFromHub, order
        );
        if ("CONTROLLER_ACTIVE".equals(operation.reason())) {
            source.sendFailure(Component.literal(
                "Cannot preset-start while controller is active: "
                    + TensuraDungeonCommands.formatPos(operation.blockingController())
            ));
            return 0;
        }
        if ("FLOOR_LOCKED".equals(operation.reason())) {
            source.sendFailure(Component.literal(
                "Floor " + requestedFloor + " is locked. Highest unlocked floor: "
                    + operation.floorStart().highestUnlockedFloor() + "."
            ));
            return 0;
        }
        if ("RUN_IN_PROGRESS".equals(operation.reason())) {
            source.sendFailure(Component.literal(
                "Cannot replace an in-progress floor. Use /tensuradungeon run reset or complete it first."
            ));
            return 0;
        }
        if ("DUPLICATE_CONTROLLERS".equals(operation.reason())) {
            source.sendFailure(Component.literal("Preset order contains duplicate controllers. Refusing to start."));
            return 0;
        }

        DungeonFloorProgressData floorProgress = DungeonFloorProgressData.get(level);
        RewardMailboxData mailbox = RewardMailboxData.get(level);
        if (armedFromHub) {
            source.sendSuccess(() -> Component.literal(
                "Prepared hub preset floor " + requestedFloor + " with " + rooms + " rooms."
            ), false);
            source.sendSuccess(() -> Component.literal("Use /tensuradungeon hub open to begin."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Preset Floor Start: PASS"), false);
            source.sendSuccess(() -> Component.literal("Requested Floor: " + requestedFloor), false);
        }
        source.sendSuccess(() -> Component.literal("Selected Preset Rooms: " + order.size()), false);
        source.sendSuccess(() -> Component.literal("Prepared Controllers: " + operation.preparedControllers()), false);
        source.sendSuccess(() -> Component.literal("Floor Mode: " + manager.getFloorModeLabel()), false);
        source.sendSuccess(() -> Component.literal(
            "Selected Order: " + order.stream().map(TensuraDungeonCommands::formatPos).toList()
        ), false);
        source.sendSuccess(() -> Component.literal(
            "Stage 17 Chain Readiness: " + presetChainReadiness(candidates, rooms)
        ), false);
        source.sendSuccess(() -> Component.literal(
            "Preset Expected Controller: " + TensuraDungeonCommands.formatNullablePos(manager.getPresetExpectedControllerPos())
        ), false);
        source.sendSuccess(() -> Component.literal("Preset Completed Count: " + manager.getPresetCompletedCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Remaining Count: " + manager.getPresetRemainingCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Transition Reason: " + manager.getLastPresetTransitionReason()), false);
        source.sendSuccess(() -> Component.literal("Preset Order Debug: " + manager.getPresetOrderDebugSummary()), false);
        source.sendSuccess(() -> Component.literal("Remaining Rooms: " + manager.getRemainingRoomCount()), false);
        source.sendSuccess(() -> Component.literal(
            "Highest Unlocked Floor: " + floorProgress.getHighestUnlockedFloor(player.getUUID())
        ), false);
        source.sendSuccess(() -> Component.literal(
            "Completed Floors: " + floorProgress.getCompletedFloorsSummary(player.getUUID())
        ), false);
        source.sendSuccess(() -> Component.literal(
            "Stage 13 Floor Max EP Awarded Floors: "
                + mailbox.getAwardedFloorCompletionFloorsSummary(player.getUUID())
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    static int scanPresetFloor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        long startedAt = System.nanoTime();
        List<PresetRoomCandidate> candidates =
            findPresetRoomCandidates(level, player.blockPosition());
        double presetScanTimeMs = (System.nanoTime() - startedAt) / 1_000_000.0D;
        source.sendSuccess(() -> Component.literal("Preset Floor Scan"), false);
        source.sendSuccess(() -> Component.literal("Origin: " + TensuraDungeonCommands.formatPos(player.blockPosition())), false);
        source.sendSuccess(() -> Component.literal("Radius: " + PRESET_SCAN_RADIUS), false);
        source.sendSuccess(() -> Component.literal("Preset Scan Time Ms: " + TensuraDungeonCommands.formatDecimal(presetScanTimeMs)), false);
        source.sendSuccess(() -> Component.literal("Candidates Found: " + candidates.size()), false);
        int usableCount = countUsablePresetCandidates(candidates);
        source.sendSuccess(() -> Component.literal("Usable Controllers: " + usableCount), false);
        source.sendSuccess(() -> Component.literal("Stage 17 3-Room Readiness: " + presetChainReadiness(candidates, 3)), false);
        source.sendSuccess(() -> Component.literal("Stage 17 5-Room Readiness: " + presetChainReadiness(candidates, 5)), false);
        for (int i = 0; i < candidates.size(); i++) {
            PresetRoomCandidate candidate = candidates.get(i);
            RoomDebugSnapshot.Markers markers = candidate.controller().roomMarkerDebugSnapshot(level);
            int index = i + 1;
            source.sendSuccess(() -> Component.literal(index + ". " + TensuraDungeonCommands.formatPos(candidate.pos())
                + " | usable=" + candidate.usable()
                + " | profile=" + candidate.controller().getRoomProfile()
                + " | anchors=" + markers.spawnAnchors().size() + "/" + markers.barrierAnchors().size() + "/" + markers.exitAnchors().size()
                + " | readiness=" + RoomCommandRegistrar.stage15ReadinessStatus(candidate.controller(), candidate.report())
                + " | exitPortal=" + candidate.exitPortalPlacement().reason()), false);
            source.sendSuccess(() -> Component.literal("   Preset Ordering: " + (candidate.usable() ? "INCLUDED" : "EXCLUDED_FROM_PRESET_ORDERING")), false);
        }
        List<String> warnings = presetScanWarnings(candidates);
        source.sendSuccess(() -> Component.literal("Warnings: " + (warnings.isEmpty() ? "NONE" : String.join("; ", warnings))), false);
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return Command.SINGLE_SUCCESS;
    }

    static int chainScanPresetFloor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int requestedRooms = clampRequestedRooms(IntegerArgumentType.getInteger(context, "rooms"));
        int stepRadius = getRequestedChainedStepRadius(context);
        long startedAt = System.nanoTime();
        ChainedPresetScanResult result =
            findChainedPresetRoomCandidates(level, player.blockPosition(), requestedRooms, stepRadius);
        double presetScanTimeMs = (System.nanoTime() - startedAt) / 1_000_000.0D;
        emitChainedPresetScan(source, player.blockPosition(), result, presetScanTimeMs);
        return Command.SINGLE_SUCCESS;
    }

    static int postPlacementClaimLinearScan(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        int requestedRooms = clampRequestedRooms(IntegerArgumentType.getInteger(context, "rooms"));
        int stepRadius = getRequestedChainedStepRadius(context);
        StoredGeneratedPresetScanResult storedResult =
            findStoredGeneratedPresetRoomCandidates(level, requestedRooms);
        if (storedResult.hasUsableRequestedRooms()) {
            emitStoredGeneratedPresetScan(source, player.blockPosition(), storedResult);
            return Command.SINGLE_SUCCESS;
        }
        if (storedResult.hasStoredPlacement()) {
            emitStoredGeneratedPresetScan(source, player.blockPosition(), storedResult);
            source.sendSuccess(() -> Component.literal("Discovery Path: FALLBACK_CHAIN_SCAN"), false);
        }
        long startedAt = System.nanoTime();
        ChainedPresetScanResult result =
            findPostPlacementClaimChainedPresetRoomCandidates(
                level, player.blockPosition(), requestedRooms, stepRadius,
                POST_PLACEMENT_CLAIM_DEFAULT_RADIUS
            );
        double presetScanTimeMs = (System.nanoTime() - startedAt) / 1_000_000.0D;
        emitChainedPresetScan(source, player.blockPosition(), result, presetScanTimeMs);
        emitPostPlacementClaimScanDetails(source, result);
        return Command.SINGLE_SUCCESS;
    }

    static int startPresetFloorRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (GeneratedLinearConnectorPlacementCommands.blockIfGeneratedCleanupPending(source, level)) return 0;
        int rooms = IntegerArgumentType.getInteger(context, "rooms");
        return armPresetFloor(source, player, level, manager, 1, rooms, false);
    }

    static int startChainedPresetFloorRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (GeneratedLinearConnectorPlacementCommands.blockIfGeneratedCleanupPending(source, level)) return 0;
        int rooms = clampRequestedRooms(IntegerArgumentType.getInteger(context, "rooms"));
        int stepRadius = getRequestedChainedStepRadius(context);
        StoredGeneratedPresetScanResult storedResult =
            findStoredGeneratedPresetRoomCandidates(level, rooms);
        if (storedResult.hasUsableRequestedRooms()) {
            emitStoredGeneratedPresetScan(source, player.blockPosition(), storedResult);
            return armPresetFloor(source, player, level, manager, 1, rooms, false, storedResult.candidates(), null);
        }
        if (storedResult.hasStoredPlacement()) {
            emitStoredGeneratedPresetScan(source, player.blockPosition(), storedResult);
            source.sendSuccess(() -> Component.literal("Discovery Path: FALLBACK_CHAIN_SCAN"), false);
        }
        ChainedPresetScanResult result =
            findChainedPresetRoomCandidates(level, player.blockPosition(), rooms, stepRadius);
        if (result.usableCandidates().size() < rooms) {
            emitChainedPresetScan(source, player.blockPosition(), result, result.scanTimeMs());
            source.sendFailure(Component.literal("Not enough usable preset room controllers discovered by chained scan. Needed " + rooms + ", found " + result.usableCandidates().size() + "."));
            return 0;
        }
        return armPresetFloor(
            source, player, level, manager, 1, rooms, false, result.discoveredCandidates(), result
        );
    }

    static int startPostPlacementClaimLinearRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        if (GeneratedLinearConnectorPlacementCommands.blockIfGeneratedCleanupPending(source, level)) return 0;
        int rooms = clampRequestedRooms(IntegerArgumentType.getInteger(context, "rooms"));
        int stepRadius = getRequestedChainedStepRadius(context);
        StoredGeneratedPresetScanResult storedResult =
            findStoredGeneratedPresetRoomCandidates(level, rooms);
        if (storedResult.hasUsableRequestedRooms()) {
            emitStoredGeneratedPresetScan(source, player.blockPosition(), storedResult);
            source.sendSuccess(() -> Component.literal("Post-Placement Claim Gate: PASS - stored generated placement already claimed during placement"), false);
            return armPresetFloor(source, player, level, manager, 1, rooms, false, storedResult.candidates(), null);
        }
        if (storedResult.hasStoredPlacement()) {
            emitStoredGeneratedPresetScan(source, player.blockPosition(), storedResult);
            source.sendSuccess(() -> Component.literal("Discovery Path: FALLBACK_CHAIN_SCAN"), false);
        }
        ChainedPresetScanResult result =
            findPostPlacementClaimChainedPresetRoomCandidates(
                level, player.blockPosition(), rooms, stepRadius,
                POST_PLACEMENT_CLAIM_DEFAULT_RADIUS
            );
        if (result.usableCandidates().size() < rooms) {
            emitChainedPresetScan(source, player.blockPosition(), result, result.scanTimeMs());
            emitPostPlacementClaimScanDetails(source, result);
            source.sendFailure(Component.literal("Not enough usable post-placement claimed controllers. Needed " + rooms + ", found " + result.usableCandidates().size() + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Post-Placement Claim Gate: PASS"), false);
        emitPostPlacementClaimScanDetails(source, result);
        return armPresetFloor(
            source, player, level, manager, 1, rooms, false, result.discoveredCandidates(), result
        );
    }

    static int presetFloorStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = TensuraDungeonCommands.getPlayer(source);
        if (player == null) return 0;
        ServerLevel level = player.serverLevel();
        DungeonRunManager manager = DungeonRunManager.get(level);
        DungeonFloorProgressData floorProgress = DungeonFloorProgressData.get(level);
        RewardMailboxData mailbox = RewardMailboxData.get(level);
        DungeonFloorProgressData.PlayerFloorProgress progress = floorProgress.getOrCreate(player.getUUID());
        source.sendSuccess(() -> Component.literal("Preset Floor Status"), false);
        source.sendSuccess(() -> Component.literal("Floor Mode: " + manager.getFloorModeLabel()), false);
        source.sendSuccess(() -> Component.literal("Preset Mode: " + manager.isPresetMode()), false);
        source.sendSuccess(() -> Component.literal("Hub Floor Prepared: " + manager.isPreparedFloorReady()), false);
        source.sendSuccess(() -> Component.literal("Hub Prepared Floor: " + (manager.isPreparedFloorReady() ? manager.getPreparedFloorNumber() : "NONE")), false);
        source.sendSuccess(() -> Component.literal("Hub Prepared Preset Rooms: " + (manager.getHubPreparedPresetRooms() > 0 ? manager.getHubPreparedPresetRooms() : "NONE")), false);
        source.sendSuccess(() -> Component.literal("Preset Armed From Hub: " + manager.isPresetArmedFromHub()), false);
        source.sendSuccess(() -> Component.literal("Active Run Floor: " + manager.getFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Target / Completed / Remaining Rooms: " + manager.getTargetRoomCount() + " / " + manager.getCompletedRoomCount() + " / " + manager.getRemainingRoomCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Current Index: " + manager.getPresetCurrentIndex()), false);
        source.sendSuccess(() -> Component.literal("Preset Expected Controller: " + TensuraDungeonCommands.formatNullablePos(manager.getPresetExpectedControllerPos())), false);
        source.sendSuccess(() -> Component.literal("Preset Completed Count: " + manager.getPresetCompletedCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Remaining Count: " + manager.getPresetRemainingCount()), false);
        source.sendSuccess(() -> Component.literal("Preset Transition Reason: " + manager.getLastPresetTransitionReason()), false);
        source.sendSuccess(() -> Component.literal("Preset Rejected Controller: " + manager.getLastPresetRejectedControllerSummary()), false);
        source.sendSuccess(() -> Component.literal("Preset Order Debug: " + manager.getPresetOrderDebugSummary()), false);
        List<BlockPos> order = manager.getPresetRoomOrder();
        for (int i = 0; i < order.size(); i++) {
            BlockPos pos = order.get(i);
            int index = i + 1;
            source.sendSuccess(() -> Component.literal(index + ". " + TensuraDungeonCommands.formatPos(pos)
                + " | state=" + manager.getPresetControllerStatus(pos)
                + " | completed=" + manager.isRoomRegisteredCompleted(pos)
                + " | active=" + manager.isRoomRegisteredActive(pos)), false);
        }
        source.sendSuccess(() -> Component.literal("Next Floor Eligible: " + manager.isNextFloorEligible()), false);
        source.sendSuccess(() -> Component.literal("Run Completed: " + manager.isRunCompleted()), false);
        source.sendSuccess(() -> Component.literal("Highest Unlocked Floor: " + progress.highestUnlockedFloor()), false);
        source.sendSuccess(() -> Component.literal("Completed Floors: " + floorProgress.getCompletedFloorsSummary(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Stage 13 Floor Max EP Awarded Floors: " + mailbox.getAwardedFloorCompletionFloorsSummary(player.getUUID())), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int renderAdvanceFloorResult(
        CommandSourceStack source,
        ServerPlayer player,
        ServerLevel level,
        DungeonRunManager manager,
        DungeonRunService.AdvanceFloorOperationResult operation
    ) {
        DungeonFloorProgressData floorProgress = DungeonFloorProgressData.get(level);
        if ("NO_ACTIVE_RUN".equals(operation.reason())) {
            source.sendFailure(Component.literal("No active dungeon run. Start a run before advancing floors."));
            return 0;
        }
        if ("NEXT_FLOOR_NOT_ELIGIBLE".equals(operation.reason())) {
            source.sendFailure(Component.literal("Next floor is not yet unlocked. Complete the current floor first."));
            return 0;
        }
        int nextFloor = manager.getNextFloorNumber();
        if ("FLOOR_LOCKED".equals(operation.reason())) {
            source.sendFailure(Component.literal("Floor " + nextFloor + " is locked. Highest unlocked floor: " + floorProgress.getHighestUnlockedFloor(player.getUUID()) + "."));
            return 0;
        }
        if ("ADVANCE_BLOCKED".equals(operation.reason())) {
            source.sendFailure(Component.literal("Floor could not be advanced."));
            source.sendFailure(Component.literal("Advance Debug: " + manager.getAdvanceToNextFloorBlockReason()));
            source.sendFailure(Component.literal("Advance Flags: activeRun=" + manager.isActiveRun()
                + ", runCompleted=" + manager.isRunCompleted()
                + ", nextFloorEligible=" + manager.isNextFloorEligible()
                + ", nextFloorNumber=" + manager.getNextFloorNumber()
                + ", currentFloor=" + manager.getFloorNumber()
                + ", failed=" + manager.isFailed()
                + ", activeRooms=" + manager.getActiveRoomCount()
                + ", completedRooms=" + manager.getCompletedRoomCount()));
            return 0;
        }
        boolean preparedController = operation.preparedController();
        source.sendSuccess(() -> Component.literal("Dungeon run advanced to floor " + manager.getFloorNumber() + "."), false);
        source.sendSuccess(() -> Component.literal("Highest Unlocked Floor: " + floorProgress.getHighestUnlockedFloor(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Completed Floors: " + floorProgress.getCompletedFloorsSummary(player.getUUID())), false);
        source.sendSuccess(() -> Component.literal("Prepared Nearest Room Controller: " + preparedController), false);
        return Command.SINGLE_SUCCESS;
    }

    static int clampRequestedRooms(int rooms) {
        return Math.max(1, Math.min(CHAINED_PRESET_MAX_ROOMS, rooms));
    }

    static int getRequestedChainedStepRadius(CommandContext<CommandSourceStack> context) {
        try {
            return clampChainedStepRadius(IntegerArgumentType.getInteger(context, "stepRadius"));
        } catch (IllegalArgumentException ignored) {
            return CHAINED_PRESET_DEFAULT_STEP_RADIUS;
        }
    }

    private static int clampChainedStepRadius(int stepRadius) {
        return Math.max(PRESET_SCAN_RADIUS, Math.min(CHAINED_PRESET_MAX_STEP_RADIUS, stepRadius));
    }

    private static int clampPostPlacementClaimRadius(int claimRadius) {
        return Math.max(1, Math.min(POST_PLACEMENT_CLAIM_MAX_RADIUS, claimRadius));
    }

    static ChainedPresetScanResult findChainedPresetRoomCandidates(ServerLevel level, BlockPos origin, int requestedRooms, int stepRadius) {
        long startedAt = System.nanoTime();
        int clampedRooms = clampRequestedRooms(requestedRooms);
        int clampedStepRadius = clampChainedStepRadius(stepRadius);
        List<PresetRoomCandidate> startCandidates = findPresetRoomCandidates(level, origin, clampedStepRadius);
        PresetRoomCandidate start = selectChainedStartCandidate(startCandidates, origin);
        if (start == null) {
            double scanTimeMs = (System.nanoTime() - startedAt) / 1_000_000.0D;
            return new ChainedPresetScanResult(clampedRooms, clampedStepRadius, CHAINED_PRESET_MAX_STEPS, origin.immutable(), null, List.of(), List.of(), List.of(), "NO_START_CONTROLLER_FOUND", scanTimeMs);
        }

        Map<BlockPos, PresetRoomCandidate> discovered = new HashMap<>();
        List<ChainedPresetStep> steps = new ArrayList<>();
        List<PresetRoomCandidate> order = new ArrayList<>();
        Set<BlockPos> visited = new LinkedHashSet<>();
        PresetRoomCandidate current = start;
        order.add(start);
        discovered.put(start.pos(), start);
        visited.add(start.pos());
        String stopReason = start.usable() && clampedRooms == 1 ? "REQUESTED_ROOM_COUNT_REACHED" : "NO_NEW_CONTROLLERS_FOUND";

        for (int stepIndex = 1; stepIndex <= CHAINED_PRESET_MAX_STEPS; stepIndex++) {
            List<PresetRoomCandidate> nearby = findPresetRoomCandidates(level, current.pos(), clampedStepRadius);
            List<PresetRoomCandidate> unvisited = new ArrayList<>();
            for (PresetRoomCandidate candidate : nearby) {
                discovered.putIfAbsent(candidate.pos(), candidate);
                if (!visited.contains(candidate.pos())) {
                    unvisited.add(candidate);
                }
            }
            unvisited.sort(chainedStepComparator(current.pos()));
            PresetRoomCandidate next = unvisited.isEmpty() ? null : unvisited.get(0);
            String stepStopReason = next == null ? "NO_NEW_UNVISITED_CANDIDATES" : (order.stream().filter(PresetRoomCandidate::usable).limit(clampedRooms).count() >= clampedRooms ? "REQUESTED_ROOM_COUNT_REACHED" : "CHAIN_CONTINUES");
            steps.add(new ChainedPresetStep(stepIndex, current.pos().immutable(), unvisited.size(), next == null ? null : next.pos().immutable(), stepStopReason));
            if (next == null) {
                stopReason = "NO_NEW_CONTROLLERS_FOUND";
                break;
            }
            current = next;
            visited.add(current.pos());
            order.add(current);
            if (order.stream().filter(PresetRoomCandidate::usable).limit(clampedRooms).count() >= clampedRooms) {
                stopReason = "REQUESTED_ROOM_COUNT_REACHED";
                break;
            }
            if (stepIndex == CHAINED_PRESET_MAX_STEPS) {
                stopReason = "MAX_SCAN_STEPS_REACHED";
            }
        }

        List<PresetRoomCandidate> discoveredCandidates = new ArrayList<>(order);
        List<PresetRoomCandidate> usableCandidates = discoveredCandidates.stream().filter(PresetRoomCandidate::usable).limit(clampedRooms).toList();
        double scanTimeMs = (System.nanoTime() - startedAt) / 1_000_000.0D;
        return new ChainedPresetScanResult(clampedRooms, clampedStepRadius, CHAINED_PRESET_MAX_STEPS, origin.immutable(), start.pos().immutable(), discoveredCandidates, usableCandidates, steps, stopReason, scanTimeMs);
    }

    static ChainedPresetScanResult findPostPlacementClaimChainedPresetRoomCandidates(ServerLevel level, BlockPos origin, int requestedRooms, int stepRadius, int claimRadius) {
        long startedAt = System.nanoTime();
        int clampedRooms = clampRequestedRooms(requestedRooms);
        int clampedStepRadius = clampChainedStepRadius(stepRadius);
        int clampedClaimRadius = clampPostPlacementClaimRadius(claimRadius);

        List<PresetRoomCandidate> startCandidates = findPostPlacementClaimPresetRoomCandidates(level, origin, clampedStepRadius, clampedClaimRadius);
        PresetRoomCandidate start = selectChainedStartCandidate(startCandidates, origin);
        if (start == null) {
            double scanTimeMs = (System.nanoTime() - startedAt) / 1_000_000.0D;
            return new ChainedPresetScanResult(clampedRooms, clampedStepRadius, CHAINED_PRESET_MAX_STEPS, origin.immutable(), null, List.of(), List.of(), List.of(), "NO_START_CONTROLLER_FOUND", scanTimeMs);
        }

        List<ChainedPresetStep> steps = new ArrayList<>();
        List<PresetRoomCandidate> order = new ArrayList<>();
        Set<BlockPos> visited = new LinkedHashSet<>();
        PresetRoomCandidate current = start;
        order.add(start);
        visited.add(start.pos());

        String stopReason = start.usable() && clampedRooms == 1 ? "REQUESTED_ROOM_COUNT_REACHED" : "NO_NEW_CONTROLLERS_FOUND";

        for (int stepIndex = 1; stepIndex <= CHAINED_PRESET_MAX_STEPS; stepIndex++) {
            List<PresetRoomCandidate> nearby = findPostPlacementClaimPresetRoomCandidates(level, current.pos(), clampedStepRadius, clampedClaimRadius);
            List<PresetRoomCandidate> unvisited = new ArrayList<>();
            for (PresetRoomCandidate candidate : nearby) {
                if (!visited.contains(candidate.pos())) {
                    unvisited.add(candidate);
                }
            }
            unvisited.sort(chainedStepComparator(current.pos()));
            PresetRoomCandidate next = unvisited.isEmpty() ? null : unvisited.get(0);
            String stepStopReason = next == null
                ? "NO_NEW_UNVISITED_CANDIDATES"
                : (order.stream().filter(PresetRoomCandidate::usable).limit(clampedRooms).count() >= clampedRooms ? "REQUESTED_ROOM_COUNT_REACHED" : "CHAIN_CONTINUES");
            steps.add(new ChainedPresetStep(stepIndex, current.pos().immutable(), unvisited.size(), next == null ? null : next.pos().immutable(), stepStopReason));
            if (next == null) {
                stopReason = "NO_NEW_CONTROLLERS_FOUND";
                break;
            }
            current = next;
            visited.add(current.pos());
            order.add(current);
            if (order.stream().filter(PresetRoomCandidate::usable).limit(clampedRooms).count() >= clampedRooms) {
                stopReason = "REQUESTED_ROOM_COUNT_REACHED";
                break;
            }
            if (stepIndex == CHAINED_PRESET_MAX_STEPS) {
                stopReason = "MAX_SCAN_STEPS_REACHED";
            }
        }

        List<PresetRoomCandidate> usableCandidates = order.stream().filter(PresetRoomCandidate::usable).limit(clampedRooms).toList();
        double scanTimeMs = (System.nanoTime() - startedAt) / 1_000_000.0D;
        return new ChainedPresetScanResult(clampedRooms, clampedStepRadius, CHAINED_PRESET_MAX_STEPS, origin.immutable(), start.pos().immutable(), order, usableCandidates, steps, stopReason, scanTimeMs);
    }

    private static PresetRoomCandidate selectChainedStartCandidate(List<PresetRoomCandidate> candidates, BlockPos origin) {
        List<PresetRoomCandidate> usable = candidates.stream().filter(PresetRoomCandidate::usable).sorted(chainedStepComparator(origin)).toList();
        if (!usable.isEmpty()) {
            return usable.get(0);
        }
        return candidates.stream().sorted(chainedStepComparator(origin)).findFirst().orElse(null);
    }

    private static Comparator<PresetRoomCandidate> chainedStepComparator(BlockPos center) {
        return Comparator.comparing((PresetRoomCandidate candidate) -> !candidate.usable())
            .thenComparingDouble(candidate -> candidate.pos().distSqr(center))
            .thenComparing(PresetRoomCandidate::pos, RunCommandHandlers::compareBlockPos);
    }

    private static int compareBlockPos(BlockPos left, BlockPos right) {
        int x = Integer.compare(left.getX(), right.getX());
        if (x != 0) {
            return x;
        }
        int y = Integer.compare(left.getY(), right.getY());
        if (y != 0) {
            return y;
        }
        return Integer.compare(left.getZ(), right.getZ());
    }

    static void emitChainedPresetScan(CommandSourceStack source, BlockPos origin, ChainedPresetScanResult result, double scanTimeMs) {
        source.sendSuccess(() -> Component.literal("Preset Floor Chained Scan"), false);
        source.sendSuccess(() -> Component.literal("Origin: " + formatPos(origin)), false);
        source.sendSuccess(() -> Component.literal("Discovery Path: FALLBACK_CHAIN_SCAN"), false);
        source.sendSuccess(() -> Component.literal("Chained Scan: true"), false);
        source.sendSuccess(() -> Component.literal("Requested Room Count: " + result.requestedRooms()), false);
        source.sendSuccess(() -> Component.literal("Step Radius: " + result.stepRadius()), false);
        source.sendSuccess(() -> Component.literal("Max Steps: " + result.maxSteps()), false);
        source.sendSuccess(() -> Component.literal("Preset Scan Time Ms: " + formatDecimal(scanTimeMs)), false);
        source.sendSuccess(() -> Component.literal("Start Controller: " + formatNullablePos(result.startController())), false);
        source.sendSuccess(() -> Component.literal("Controllers Discovered: " + result.discoveredCandidates().size()), false);
        source.sendSuccess(() -> Component.literal("Usable Controllers: " + result.usableCandidates().size()), false);
        source.sendSuccess(() -> Component.literal("Chain Order: " + formatPresetOrderList(result.discoveredCandidates().stream().map(PresetRoomCandidate::pos).toList())), false);
        source.sendSuccess(() -> Component.literal("Stop Reason: " + result.stopReason()), false);
        source.sendSuccess(() -> Component.literal("Stage 17 Requested Readiness: " + presetChainReadiness(result.discoveredCandidates(), result.requestedRooms())), false);
        for (int i = 0; i < result.discoveredCandidates().size(); i++) {
            PresetRoomCandidate candidate = result.discoveredCandidates().get(i);
            int index = i + 1;
            source.sendSuccess(() -> Component.literal(index + ". " + formatPos(candidate.pos())
                + " | usable=" + candidate.usable()
                + " | profile=" + candidate.controller().getRoomProfile()
                + " | readiness=" + RoomCommandRegistrar.stage15ReadinessStatus(candidate.controller(), candidate.report())
                + " | readinessReason=" + summarizeValidationLines(candidate.report())
                + " | anchors=" + RoomCommandRegistrar.ownedCombatAnchorCount(candidate.controller()) + "/" + RoomCommandRegistrar.ownedBarrierAnchorCount(candidate.controller()) + "/" + candidate.controller().getExitAnchors().size()
                + " | claim=" + (candidate.postPlacementClaimAttempted() ? candidate.postPlacementClaimReason() + " " + candidate.postPlacementClaimSummary() : "NOT_ATTEMPTED")), false);
        }
        for (ChainedPresetStep step : result.steps()) {
            source.sendSuccess(() -> Component.literal("Step " + step.stepNumber() + ": origin=" + formatPos(step.scanOrigin()) + " | candidates=" + step.candidatesFound() + " | selected=" + formatNullablePos(step.selectedNextController()) + " | reason=" + step.stopReason()), false);
        }
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
    }

    static void emitStoredGeneratedPresetScan(CommandSourceStack source, BlockPos origin, StoredGeneratedPresetScanResult result) {
        source.sendSuccess(() -> Component.literal("Generated Floor Stored Placement Scan"), false);
        source.sendSuccess(() -> Component.literal("Origin: " + formatPos(origin)), false);
        source.sendSuccess(() -> Component.literal("Discovery Path: FAST_STORED_GENERATED_PLACEMENT"), false);
        source.sendSuccess(() -> Component.literal("Requested Room Count: " + result.requestedRooms()), false);
        source.sendSuccess(() -> Component.literal("Stored Placement Present: " + result.hasStoredPlacement()), false);
        source.sendSuccess(() -> Component.literal("Stored Floor Build Origin: " + formatNullablePos(result.floorBuildOrigin())), false);
        source.sendSuccess(() -> Component.literal("Stored Controller Count: " + result.storedControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Controllers Discovered: " + result.candidates().size()), false);
        source.sendSuccess(() -> Component.literal("Usable Controllers: " + result.usableCandidates().size()), false);
        source.sendSuccess(() -> Component.literal("Stored Scan Time Ms: " + formatDecimal(result.scanTimeMs())), false);
        source.sendSuccess(() -> Component.literal("Selected Order: " + formatPresetOrderList(result.usableCandidates().stream().map(PresetRoomCandidate::pos).limit(result.requestedRooms()).toList())), false);
        source.sendSuccess(() -> Component.literal("Stop Reason: " + result.stopReason()), false);
        source.sendSuccess(() -> Component.literal("Stage 17 Requested Readiness: " + presetChainReadiness(result.candidates(), result.requestedRooms())), false);
        for (int i = 0; i < result.candidates().size(); i++) {
            PresetRoomCandidate candidate = result.candidates().get(i);
            int index = i + 1;
            source.sendSuccess(() -> Component.literal(index + ". " + formatPos(candidate.pos())
                + " | usable=" + candidate.usable()
                + " | profile=" + candidate.controller().getRoomProfile()
                + " | readiness=" + RoomCommandRegistrar.stage15ReadinessStatus(candidate.controller(), candidate.report())
                + " | readinessReason=" + summarizeValidationLines(candidate.report())
                + " | anchors=" + RoomCommandRegistrar.ownedCombatAnchorCount(candidate.controller()) + "/" + RoomCommandRegistrar.ownedBarrierAnchorCount(candidate.controller()) + "/" + candidate.controller().getExitAnchors().size()), false);
        }
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
    }

    static void emitPostPlacementClaimScanDetails(CommandSourceStack source, ChainedPresetScanResult result) {
        source.sendSuccess(() -> Component.literal("Post-Placement Claim Gate: ENABLED"), false);
        for (int i = 0; i < result.discoveredCandidates().size(); i++) {
            PresetRoomCandidate candidate = result.discoveredCandidates().get(i);
            int index = i + 1;
            source.sendSuccess(() -> Component.literal(index + ". Claim attempted=" + candidate.postPlacementClaimAttempted()
                + " | radius=" + candidate.postPlacementClaimRadius()
                + " | claimed=" + candidate.postPlacementClaimTotal()
                + " | skippedOutside=" + candidate.postPlacementClaimSkippedOutsideBounds()
                + " | skippedOwned=" + candidate.postPlacementClaimSkippedOwnedByOther()
                + " | reason=" + candidate.postPlacementClaimReason()
                + " | summary=" + candidate.postPlacementClaimSummary()), false);
        }
    }

    private static String summarizeValidationLines(ValidationReport report) {
        if (report == null || report.lines().isEmpty()) {
            return report == null ? "NONE" : report.summary().name();
        }
        return report.lines().get(0);
    }
    static int countUsablePresetCandidates(List<PresetRoomCandidate> candidates) {
        int count = 0;
        for (PresetRoomCandidate candidate : candidates) {
            if (candidate.usable()) {
                count += 1;
            }
        }
        return count;
    }

    static String presetChainReadiness(List<PresetRoomCandidate> candidates, int requestedRooms) {
        int usable = countUsablePresetCandidates(candidates);
        if (requestedRooms <= 1) {
            return "ONE_ROOM_STAGE16_LEGACY_MODE";
        }
        if (usable < requestedRooms) {
            return "FAIL_NOT_ENOUGH_USABLE_CONTROLLERS_" + usable + "_OF_" + requestedRooms;
        }
        if (requestedRooms < 3) {
            return "PASS_WITH_WARNINGS_TWO_ROOM_SMOKE_ONLY";
        }
        if (requestedRooms <= 5) {
            return "PASS_STAGE17_SHORT_PRESET_READY";
        }
        return "PASS_WITH_WARNINGS_ABOVE_STAGE17_SHORT_TARGET";
    }

    private static String formatPresetOrderList(List<BlockPos> order) {
        if (order == null || order.isEmpty()) {
            return "NONE";
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            parts.add((i + 1) + ":" + formatPos(order.get(i)));
        }
        return String.join(" | ", parts);
    }

    static boolean hasDuplicatePositions(List<BlockPos> order) {
        return new LinkedHashSet<>(order).size() != order.size();
    }

    static List<PresetRoomCandidate> findPresetRoomCandidates(ServerLevel level, BlockPos center) {
        return findPresetRoomCandidates(level, center, PRESET_SCAN_RADIUS);
    }

    private static List<PresetRoomCandidate> findPresetRoomCandidates(ServerLevel level, BlockPos center, int radius) {
        List<PresetRoomCandidate> candidates = new ArrayList<>();
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).is(ModBlocks.ROOM_CONTROLLER.get())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof RoomControllerBlockEntity controller)) {
                continue;
            }
            controller.scanMarkers(level);
            ValidationReport report = controller.validate(level, center);
            boolean usable = !RoomCommandRegistrar.stage15HasHardFail(controller, report);
            DungeonRunManager.ExitPortalPlacementResult exitPortalPlacement = DungeonRunManager.analyzeExitPortalPlacement(level, controller.getBlockPos(), controller);
            candidates.add(presetRoomCandidate(controller.getBlockPos().immutable(), controller, report, usable, exitPortalPlacement));
        }
        candidates.sort(chainedStepComparator(center));
        return candidates;
    }

    private static List<PresetRoomCandidate> findPostPlacementClaimPresetRoomCandidates(ServerLevel level, BlockPos center, int radius, int claimRadius) {
        List<PresetRoomCandidate> candidates = new ArrayList<>();
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).is(ModBlocks.ROOM_CONTROLLER.get())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof RoomControllerBlockEntity controller)) {
                continue;
            }
            candidates.add(postPlacementClaimPresetRoomCandidate(level, controller.getBlockPos().immutable(), controller, claimRadius));
        }
        candidates.sort(chainedStepComparator(center));
        return candidates;
    }

    static StoredGeneratedPresetScanResult findStoredGeneratedPresetRoomCandidates(ServerLevel level, int requestedRooms) {
        long startedAt = System.nanoTime();
        int clampedRooms = clampRequestedRooms(requestedRooms);
        GeneratedFloorRuntimeData.LastGeneratedPlacement placement = GeneratedFloorRepository.get(level).hydrate().placement();
        if (placement == null) {
            return new StoredGeneratedPresetScanResult(clampedRooms, null, 0, List.of(), "NO_STORED_GENERATED_PLACEMENT", elapsedMs(startedAt));
        }
        List<BlockPos> storedControllers = placement.controllers();
        List<PresetRoomCandidate> candidates = new ArrayList<>();
        for (BlockPos controllerPos : storedControllers) {
            if (!level.getBlockState(controllerPos).is(ModBlocks.ROOM_CONTROLLER.get())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(controllerPos);
            if (!(blockEntity instanceof RoomControllerBlockEntity controller)) {
                continue;
            }
            controller.scanMarkers(level);
            ValidationReport report = controller.validate(level, controllerPos);
            boolean usable = !RoomCommandRegistrar.stage15HasHardFail(controller, report);
            DungeonRunManager.ExitPortalPlacementResult exitPortalPlacement = DungeonRunManager.analyzeExitPortalPlacement(level, controller.getBlockPos(), controller);
            candidates.add(presetRoomCandidate(controller.getBlockPos().immutable(), controller, report, usable, exitPortalPlacement));
        }
        int usableCount = countUsablePresetCandidates(candidates);
        String stopReason;
        if (storedControllers.isEmpty()) {
            stopReason = "STORED_PLACEMENT_HAS_NO_CONTROLLERS";
        } else if (candidates.size() < storedControllers.size()) {
            stopReason = usableCount >= clampedRooms ? "PASS_WITH_MISSING_STORED_CONTROLLERS" : "MISSING_STORED_CONTROLLERS";
        } else if (usableCount < clampedRooms) {
            stopReason = "NOT_ENOUGH_USABLE_STORED_CONTROLLERS";
        } else {
            stopReason = "REQUESTED_ROOM_COUNT_REACHED";
        }
        return new StoredGeneratedPresetScanResult(clampedRooms, placement.floorBuildOrigin(), storedControllers.size(), candidates, stopReason, elapsedMs(startedAt));
    }

    private static double elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000.0D;
    }

    static List<String> presetScanWarnings(List<PresetRoomCandidate> candidates) {
        List<String> warnings = new ArrayList<>();
        long usableCount = candidates.stream().filter(PresetRoomCandidate::usable).count();
        if (usableCount == 0) {
            warnings.add("no usable preset controllers found");
        }
        if (candidates.stream().anyMatch(candidate -> candidate.report().summary() == RoomControllerBlockEntity.ValidationSummary.WARN)) {
            warnings.add("one or more controllers have readiness warnings");
        }
        if (candidates.stream().anyMatch(candidate -> !candidate.usable())) {
            warnings.add("one or more controllers are excluded from preset ordering");
        }
        return warnings;
    }

    private static boolean isPresetOrderStable(List<PresetRoomCandidate> candidates, BlockPos center) {
        double lastDistance = -1.0D;
        for (PresetRoomCandidate candidate : candidates) {
            double currentDistance = candidate.pos().distSqr(center);
            if (currentDistance < lastDistance) {
                return false;
            }
            lastDistance = currentDistance;
        }
        return true;
    }

    private static PresetRoomCandidate presetRoomCandidate(
        BlockPos pos,
        RoomControllerBlockEntity controller,
        ValidationReport report,
        boolean usable,
        DungeonRunManager.ExitPortalPlacementResult exitPortalPlacement
    ) {
        return new PresetRoomCandidate(
            pos,
            controller,
            report,
            usable,
            exitPortalPlacement,
            false,
            0,
            0,
            0,
            0,
            "NOT_ATTEMPTED",
            "NONE"
        );
    }

    private static PresetRoomCandidate postPlacementClaimPresetRoomCandidate(
        ServerLevel level,
        BlockPos pos,
        RoomControllerBlockEntity controller,
        int claimRadius
    ) {
        RoomControllerBlockEntity.MarkerClaimResult claimResult = controller.claimMarkers(level, claimRadius, false);
        ValidationReport report = controller.validate(level, pos);
        boolean usable = !RoomCommandRegistrar.stage15HasHardFail(controller, report);
        DungeonRunManager.ExitPortalPlacementResult exitPortalPlacement = DungeonRunManager.analyzeExitPortalPlacement(level, controller.getBlockPos(), controller);
        return new PresetRoomCandidate(
            controller.getBlockPos().immutable(),
            controller,
            report,
            usable,
            exitPortalPlacement,
            true,
            claimResult.radius(),
            claimResult.totalClaimed(),
            claimResult.skippedOutsideBounds(),
            claimResult.skippedOwnedByOther(),
            claimResult.reason(),
            claimResult.summary()
        );
    }

    record PresetRoomCandidate(
        BlockPos pos,
        RoomControllerBlockEntity controller,
        ValidationReport report,
        boolean usable,
        DungeonRunManager.ExitPortalPlacementResult exitPortalPlacement,
        boolean postPlacementClaimAttempted,
        int postPlacementClaimRadius,
        int postPlacementClaimTotal,
        int postPlacementClaimSkippedOutsideBounds,
        int postPlacementClaimSkippedOwnedByOther,
        String postPlacementClaimReason,
        String postPlacementClaimSummary
    ) {
    }

    private record ChainedPresetStep(int stepNumber, BlockPos scanOrigin, int candidatesFound, BlockPos selectedNextController, String stopReason) {
    }

    record ChainedPresetScanResult(int requestedRooms, int stepRadius, int maxSteps, BlockPos origin, BlockPos startController, List<PresetRoomCandidate> discoveredCandidates, List<PresetRoomCandidate> usableCandidates, List<ChainedPresetStep> steps, String stopReason, double scanTimeMs) {
    }

    record StoredGeneratedPresetScanResult(int requestedRooms, BlockPos floorBuildOrigin, int storedControllerCount, List<PresetRoomCandidate> candidates, String stopReason, double scanTimeMs) {
        boolean hasStoredPlacement() {
            return floorBuildOrigin != null || storedControllerCount > 0;
        }

        List<PresetRoomCandidate> usableCandidates() {
            return candidates.stream().filter(PresetRoomCandidate::usable).limit(requestedRooms).toList();
        }

        boolean hasUsableRequestedRooms() {
            return usableCandidates().size() >= requestedRooms;
        }
    }
}
