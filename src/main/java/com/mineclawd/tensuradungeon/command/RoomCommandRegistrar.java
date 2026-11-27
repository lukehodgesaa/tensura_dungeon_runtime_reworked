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
import com.mineclawd.tensuradungeon.runtime.DungeonModifierDefinition;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
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
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.DEBUG_RADIUS;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.findNearestController;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.formatDecimal;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.formatPos;
import static com.mineclawd.tensuradungeon.command.TensuraDungeonCommands.getPlayer;
final class RoomCommandRegistrar {
    private RoomCommandRegistrar() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("room")
            .then(Commands.literal("debug").executes(RoomCommandRegistrar::debugRoom))
            .then(Commands.literal("inspect").executes(RoomCommandRegistrar::inspectRoomReadiness))
            .then(Commands.literal("anchors").executes(RoomCommandRegistrar::inspectRoomAnchors))
            .then(Commands.literal("budgetdebug").executes(RoomCommandRegistrar::debugRoomBudget))
            .then(Commands.literal("reset").executes(RoomCommandRegistrar::resetRoom))
            .then(Commands.literal("activate").executes(RoomCommandRegistrar::activateRoom))
            .then(Commands.literal("complete").executes(RoomCommandRegistrar::completeRoom))
            .then(Commands.literal("validate").executes(RoomCommandRegistrar::validateRoom))
            .then(Commands.literal("cleanup").executes(RoomCommandRegistrar::cleanupRoom))
            .then(Commands.literal("barrierpreview").executes(RoomCommandRegistrar::barrierPreview))
            .then(Commands.literal("boundspreview").executes(RoomClaimDebugCommands::boundsPreview))
            .then(Commands.literal("clearpreview").executes(RoomClaimDebugCommands::clearPreview))
            .then(Commands.literal("claimpreview")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                    .executes(context -> RoomClaimDebugCommands.claimPreview(context, false))
                    .then(Commands.literal("force")
                        .executes(context -> RoomClaimDebugCommands.claimPreview(context, true)))))
            .then(Commands.literal("boundsdebug")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                    .executes(RoomClaimDebugCommands::debugRoomClaimBounds)))
            .then(Commands.literal("claimmarkers")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                    .executes(context -> RoomClaimDebugCommands.claimRoomMarkers(context, false))
                    .then(Commands.literal("force")
                        .executes(context -> RoomClaimDebugCommands.claimRoomMarkers(context, true)))))
            .then(GeneratedRoomBoundsDebugCommands.register())
            .then(Commands.literal("fail").executes(RoomCommandRegistrar::failRoom))
            .then(Commands.literal("debugactivation")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(RoomCommandRegistrar::setDebugActivationOverride)))
            .then(Commands.literal("objective")
                .then(Commands.literal("auto").executes(RoomCommandRegistrar::setObjectiveAuto))
                .then(Commands.literal("clear_all")
                    .executes(context -> setObjective(context, RoomObjectiveType.CLEAR_ALL)))
                .then(Commands.literal("kill_elite")
                    .executes(context -> setObjective(context, RoomObjectiveType.KILL_ELITE)))
                .then(Commands.literal("survive_timer")
                    .executes(context -> setObjective(context, RoomObjectiveType.SURVIVE_TIMER)))
                .then(Commands.literal("activate_runes")
                    .executes(context -> setObjective(context, RoomObjectiveType.ACTIVATE_RUNES))))
            .then(Commands.literal("objectivedebug").executes(RoomCommandRegistrar::debugObjective))
            .then(Commands.literal("activaterune").executes(RoomCommandRegistrar::activateRune))
            .then(Commands.literal("profile")
                .then(Commands.literal("standard_combat")
                    .executes(context -> setProfile(context, RoomProfile.STANDARD_COMBAT)))
                .then(Commands.literal("large_combat")
                    .executes(context -> setProfile(context, RoomProfile.LARGE_COMBAT)))
                .then(Commands.literal("arena_wave")
                    .executes(context -> setProfile(context, RoomProfile.ARENA_WAVE)))
                .then(Commands.literal("corridor_light")
                    .executes(context -> setProfile(context, RoomProfile.CORRIDOR_LIGHT)))
                .then(Commands.literal("corridor_ambush")
                    .executes(context -> setProfile(context, RoomProfile.CORRIDOR_AMBUSH)))
                .then(Commands.literal("connector_empty")
                    .executes(context -> setProfile(context, RoomProfile.CONNECTOR_EMPTY)))
                .then(Commands.literal("traversal_hazard")
                    .executes(context -> setProfile(context, RoomProfile.TRAVERSAL_HAZARD)))
                .then(Commands.literal("treasure_ambush")
                    .executes(context -> setProfile(context, RoomProfile.TREASURE_AMBUSH)))
                .then(Commands.literal("boss_capable")
                    .executes(context -> setProfile(context, RoomProfile.BOSS_CAPABLE)))
                .then(Commands.literal("safe_room")
                    .executes(context -> setProfile(context, RoomProfile.SAFE_ROOM)))));
    }

    static int debugRoomBudget(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        controller.scanMarkers(level);
        RoomDebugSnapshot snapshot = controller.roomDebugSnapshot(level);
        RoomDebugSnapshot.Core core = snapshot.core();
        RoomDebugSnapshot.Objective objective = snapshot.objective();
        RoomDebugSnapshot.Encounter encounter = snapshot.encounter();
        RoomDebugSnapshot.Markers markers = snapshot.markers();
        RoomBudgetResult budgetResult = core.budgetResult();
        source.sendSuccess(() -> Component.literal("=== Profile / Director ==="), false);
        source.sendSuccess(() -> Component.literal("Room Controller Pos: " + formatPos(controller.getBlockPos())), false);
        source.sendSuccess(() -> Component.literal("Current Room Profile: " + core.roomProfile()), false);
        source.sendSuccess(() -> Component.literal("Profile Budget Multiplier: " + formatDecimal(core.profileBudgetMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Profile Base Max Alive: " + core.profileBaseMaxAlive()), false);
        source.sendSuccess(() -> Component.literal("Profile Target Max Alive: " + core.profileTargetMaxAlive()), false);
        source.sendSuccess(() -> Component.literal("Active Floor Number: " + core.activeFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Active Modifiers: " + DungeonRunManager.get(level).getModifierIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Active Modifier Display Names: " + DungeonRunManager.get(level).getModifierDisplayNamesSummary()), false);
        source.sendSuccess(() -> Component.literal("Modifier Budget Multiplier: " + formatDecimal(budgetResult.getModifierMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Final Calculated Room Budget: " + core.finalCalculatedRoomBudget()), false);
        source.sendSuccess(() -> Component.literal("Final Calculated Max Alive: " + core.finalCalculatedMaxAlive()), false);
        source.sendSuccess(() -> Component.literal("Profile Allows Combat: " + core.profileAllowsCombat()), false);
        source.sendSuccess(() -> Component.literal("Profile Completion Policy: " + core.profileCompletionPolicy()), false);
        source.sendSuccess(() -> Component.literal("Boss-Capable Room: " + core.bossCapableRoom()), false);
        source.sendSuccess(() -> Component.literal("Spawn Boss Anchor Present: " + core.spawnBossAnchorPresent()), false);
        source.sendSuccess(() -> Component.literal("Spawn Boss Anchor Count: " + core.spawnBossAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Objective Anchor Count: " + markers.objectiveAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Elite Anchor Count: " + markers.eliteSpawnAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Exit Anchor Count: " + markers.exitAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Objective Selection Mode: " + objective.selectionMode()), false);
        source.sendSuccess(() -> Component.literal("Active Objective: " + objective.activeType()), false);
        source.sendSuccess(() -> Component.literal("Auto Objective Candidate List: " + objective.autoCandidateList()), false);
        source.sendSuccess(() -> Component.literal("Profile / Director Fallback Reason: " + core.profileDirectorFallbackReason()), false);
        source.sendSuccess(() -> Component.literal("=== Budget ==="), false);
        source.sendSuccess(() -> Component.literal("Budget Floor Number: " + budgetResult.getFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Budget Floor Base: " + budgetResult.getFloorBaseBudget()), false);
        source.sendSuccess(() -> Component.literal("Budget Profile: " + budgetResult.getProfile()), false);
        source.sendSuccess(() -> Component.literal("Budget Profile Multiplier: " + formatDecimal(budgetResult.getProfileMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Budget Modifier Multiplier: " + formatDecimal(budgetResult.getModifierMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Budget Room Total: " + budgetResult.getRoomTotalBudget()), false);
        source.sendSuccess(() -> Component.literal("Budget Max Alive: " + budgetResult.getMaxAlive()), false);
        source.sendSuccess(() -> Component.literal("Budget Max Total: " + budgetResult.getMaxTotal()), false);
        source.sendSuccess(() -> Component.literal("Budget Spawn Cap Applied: " + budgetResult.isSpawnCapApplied()), false);
        source.sendSuccess(() -> Component.literal("Active Modifiers: " + DungeonRunManager.get(level).getModifierIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Applied Modifier Effects: " + (DungeonRunManager.get(level).getModifierCount() > 0 ? DungeonRunManager.get(level).getModifierDisplayNamesSummary().toUpperCase() : "NONE")), false);
        source.sendSuccess(() -> Component.literal("Last Spawn Modifier Summary: " + encounter.lastSpawnModifierSummary()), false);
        source.sendSuccess(() -> Component.literal("Last Spawn Armor Base: " + formatDecimal(encounter.lastSpawnArmorValue())), false);
        source.sendSuccess(() -> Component.literal("Last Spawn Armor Toughness Base: " + formatDecimal(encounter.lastSpawnArmorToughnessValue())), false);
        source.sendSuccess(() -> Component.literal("Molten Modifier Active: " + encounter.moltenModifierDebugStatus()), false);
        source.sendSuccess(() -> Component.literal("Last Molten Retaliation Tick: " + encounter.lastMoltenRetaliationTickSummary()), false);
        source.sendSuccess(() -> Component.literal("Last Molten Retaliation Target: " + encounter.lastMoltenRetaliationTargetSummary()), false);
        source.sendSuccess(() -> Component.literal("Total Molten Retaliations: " + encounter.totalMoltenRetaliations()), false);
        source.sendSuccess(() -> Component.literal("Molten Damage Events Seen: " + encounter.moltenDamageEventsSeen()), false);
        source.sendSuccess(() -> Component.literal("Molten Damage Events With Living Attacker: " + encounter.moltenDamageEventsWithLivingAttacker()), false);
        source.sendSuccess(() -> Component.literal("Molten Damage Events With Tracked Target: " + encounter.moltenDamageEventsWithTrackedTarget()), false);
        source.sendSuccess(() -> Component.literal("Last Molten Rejection Reason: " + encounter.lastMoltenRejectionReason()), false);
        source.sendSuccess(() -> Component.literal("Regen Modifier Active: " + encounter.regenModifierDebugStatus()), false);
        source.sendSuccess(() -> Component.literal("Regen Interval Ticks: " + encounter.regenerativeIntervalTicks()), false);
        source.sendSuccess(() -> Component.literal("Last Regen Pulse Tick: " + encounter.lastRegenPulseTickSummary()), false);
        source.sendSuccess(() -> Component.literal("Last Regen Pulse Healed Mobs: " + encounter.lastRegenPulseHealedCount()), false);
        source.sendSuccess(() -> Component.literal("Last Regen Pulse Health Restored: " + formatDecimal(encounter.lastRegenPulseTotalHealthRestored())), false);
        source.sendSuccess(() -> Component.literal("Summoner Modifier Active: " + encounter.summonerModifierDebugStatus()), false);
        source.sendSuccess(() -> Component.literal("Summoner Interval Ticks: " + encounter.summonerIntervalTicks()), false);
        source.sendSuccess(() -> Component.literal("Summoners Selected: " + encounter.summonersSelectedCount()), false);
        source.sendSuccess(() -> Component.literal("Active Summoners: " + encounter.activeSummonersCount()), false);
        source.sendSuccess(() -> Component.literal("Active Summoned Adds: " + encounter.activeSummonedAddsCount()), false);
        source.sendSuccess(() -> Component.literal("Total Summons This Room: " + encounter.totalSummonsThisRoom()), false);
        source.sendSuccess(() -> Component.literal("Last Summon Pulse Tick: " + encounter.lastSummonPulseTickSummary()), false);
        source.sendSuccess(() -> Component.literal("Last Summon Result: " + encounter.lastSummonResult()), false);
        source.sendSuccess(() -> Component.literal("Last Summon Rejection Reason: " + encounter.lastSummonRejectionReason()), false);
        source.sendSuccess(() -> Component.literal("Last Summoner Entity Type: " + encounter.lastSummonerEntityType()), false);
        source.sendSuccess(() -> Component.literal("Last Summoned Entity Type: " + encounter.lastSummonedEntityType()), false);
        return Command.SINGLE_SUCCESS;
    }

    static int cleanupRoom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        controller.cleanupRoom(level);
        source.sendSuccess(() -> Component.literal("Room cleanup completed."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int resetRoom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        RoomControllerBlockEntity controller = findNearestController(player.serverLevel(), player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        controller.resetRoom(player.serverLevel());
        source.sendSuccess(() -> Component.literal("Room reset to INACTIVE."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int activateRoom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        controller.scanMarkers(level);
        if (!controller.activate(level)) {
            source.sendFailure(Component.literal("Room could not activate. " + controller.getLastActivationFailureReason()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Room activated."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int completeRoom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        boolean completed = controller.completeRoom(level, player);
        if (!completed) {
            source.sendFailure(Component.literal("Room was already complete or could not be completed. " + controller.getLastActivationFailureReason()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Room marked complete."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int barrierPreview(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        controller.scanMarkers(level);
        if (!controller.placeBarrierPreview(level)) {
            source.sendFailure(Component.literal("No preview blocks were placed. Check barrier anchors, doorway space, marker ownership, and bounds."));
            RoomDebugSnapshot.Markers markers = controller.roomMarkerDebugSnapshot(level);
            RoomDebugSnapshot.Barrier barrier = controller.roomDebugSnapshot(level).barrier();
            source.sendFailure(Component.literal("Marker Ownership Mode: " + markers.markerOwnershipMode()));
            source.sendFailure(Component.literal("Claimed Marker Summary: " + markers.lastMarkerClaimSummary()));
            source.sendFailure(Component.literal("Barrier Solve Mode: " + barrier.lastSolveMode()));
            source.sendFailure(Component.literal("Barrier Solve Result: " + barrier.lastSolveResult()));
            source.sendFailure(Component.literal("Barrier Solve Reason: " + barrier.lastSolveReason()));
            for (String line : barrier.lastDebugLines()) {
                source.sendFailure(Component.literal(line));
            }
            return 0;
        }

        RoomDebugSnapshot.Barrier barrier = controller.roomDebugSnapshot(level).barrier();
        source.sendSuccess(() -> Component.literal("Barrier preview placed."), false);
        source.sendSuccess(() -> Component.literal("Runtime Barrier Material: " + barrier.lastMaterialSummary()), false);
        source.sendSuccess(() -> Component.literal("Barrier Solve Mode: " + barrier.lastSolveMode()), false);
        source.sendSuccess(() -> Component.literal("Barrier Solve Result: " + barrier.lastSolveResult()), false);
        source.sendSuccess(() -> Component.literal("Barrier Solve Reason: " + barrier.lastSolveReason()), false);
        source.sendSuccess(() -> Component.literal("Barrier Plane: " + barrier.lastPlaneSummary()), false);
        source.sendSuccess(() -> Component.literal("Barrier Volume Bounds: " + barrier.lastVolumeBoundsSummary()), false);
        source.sendSuccess(() -> Component.literal("Barrier Volume Size: " + barrier.lastVolumeSizeSummary()), false);
        source.sendSuccess(() -> Component.literal("Barrier Fill Candidates: " + barrier.lastFillCandidateCount()), false);
        source.sendSuccess(() -> Component.literal("Barrier Solved Width x Height: " + barrier.lastSolvedWidth() + " x " + barrier.lastSolvedHeight()), false);
        source.sendSuccess(() -> Component.literal("Barrier Solved Positions: " + barrier.lastSolvedPositionCount()), false);
        source.sendSuccess(() -> Component.literal("Barrier Placed / Skipped / Cleared: " + barrier.lastPlacedBlockCount() + " / " + barrier.lastSkippedBlockCount() + " / " + barrier.lastClearedBlockCount()), false);
        for (String line : barrier.lastDebugLines()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    static int failRoom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        if (!controller.failRoomCommand(level)) {
            source.sendFailure(Component.literal("Room is not active."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Room forced through failure recovery."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int setDebugActivationOverride(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        DungeonRunManager.get(player.serverLevel()).setDebugRoomActivationOverride(enabled);
        source.sendSuccess(() -> Component.literal("Room debug activation override set to " + enabled + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int setObjective(CommandContext<CommandSourceStack> context, RoomObjectiveType objectiveType) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }
        if (controller.getRoomState() == RoomControllerBlockEntity.RoomState.ACTIVE) {
            source.sendFailure(Component.literal("Cannot change objective while a room is active."));
            return 0;
        }

        controller.setConfiguredObjectiveType(objectiveType);
        source.sendSuccess(() -> Component.literal("Room objective set to " + objectiveType + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int setObjectiveAuto(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }
        if (controller.getRoomState() == RoomControllerBlockEntity.RoomState.ACTIVE) {
            source.sendFailure(Component.literal("Cannot change objective while a room is active."));
            return 0;
        }

        controller.setConfiguredObjectiveType(null);
        source.sendSuccess(() -> Component.literal("Room objective override cleared. Objective selection is now automatic."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int activateRune(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }
        if (!controller.activateNearestRune(level, player)) {
            source.sendFailure(Component.literal("No selected rune crystal in range or rune objective is not active."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Nearest rune crystal activated."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugObjective(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        RoomDebugSnapshot snapshot = controller.roomDebugSnapshot(level);
        RoomDebugSnapshot.Core core = snapshot.core();
        RoomDebugSnapshot.Objective objective = snapshot.objective();
        source.sendSuccess(() -> Component.literal("=== Objective Summary ==="), false);
        source.sendSuccess(() -> Component.literal("Active Objective: " + objective.activeType()), false);
        source.sendSuccess(() -> Component.literal("Objective Selection Mode: " + objective.selectionMode()), false);
        source.sendSuccess(() -> Component.literal("Forced Objective Override: " + objective.forcedOverrideSummary()), false);
        source.sendSuccess(() -> Component.literal("Auto Objective Selected: " + objective.autoSelected()), false);
        source.sendSuccess(() -> Component.literal("Auto Objective Candidate List: " + objective.autoCandidateList()), false);
        source.sendSuccess(() -> Component.literal("Auto Objective Rejection Summary: " + objective.autoRejectionSummary()), false);
        source.sendSuccess(() -> Component.literal("Auto Objective Roll Value: " + objective.autoRollValue()), false);
        source.sendSuccess(() -> Component.literal("Profile Completion Policy: " + core.profileCompletionPolicy()), false);
        source.sendSuccess(() -> Component.literal("Objective Completed: " + objective.completed()), false);
        source.sendSuccess(() -> Component.literal("Last Objective Result: " + objective.lastResult()), false);
        source.sendSuccess(() -> Component.literal("Last Objective Rejection Reason: " + objective.lastRejectionReason()), false);
        source.sendSuccess(() -> Component.literal("Objective Status: " + buildObjectiveStatusLine(objective)), false);

        source.sendSuccess(() -> Component.literal("=== Clear All ==="), false);
        source.sendSuccess(() -> Component.literal("Clear-All Remaining Mob Count: " + objective.clearAllRemainingMobCount()), false);

        source.sendSuccess(() -> Component.literal("=== Kill Elite ==="), false);
        source.sendSuccess(() -> Component.literal("Objective Target UUID: " + objective.targetUuid()), false);
        source.sendSuccess(() -> Component.literal("Objective Target Entity Type: " + objective.targetEntityType()), false);
        source.sendSuccess(() -> Component.literal("Objective Target Alive: " + objective.targetAlive()), false);
        source.sendSuccess(() -> Component.literal("Kill-Elite Target Remaining: " + objective.killEliteTargetRemaining()), false);

        source.sendSuccess(() -> Component.literal("=== Survive Timer ==="), false);
        source.sendSuccess(() -> Component.literal("Survive Timer Duration Ticks: " + objective.durationTicks()), false);
        source.sendSuccess(() -> Component.literal("Survive Timer Remaining Ticks: " + objective.remainingTicks()), false);
        source.sendSuccess(() -> Component.literal("Survive Timer Elapsed Ticks: " + objective.elapsedTicks()), false);
        source.sendSuccess(() -> Component.literal("Survive Timer Active: " + objective.surviveTimerActive()), false);
        source.sendSuccess(() -> Component.literal("Survive Timer Paused: " + objective.surviveTimerPaused()), false);
        source.sendSuccess(() -> Component.literal("Survive Timer Players In Bounds: " + objective.objectivePlayersInBounds()), false);

        source.sendSuccess(() -> Component.literal("=== Activate Runes ==="), false);
        source.sendSuccess(() -> Component.literal("Rune Objective Active: " + objective.runeObjectiveActive()), false);
        source.sendSuccess(() -> Component.literal("Rune Required Count: " + objective.runeRequiredCount()), false);
        source.sendSuccess(() -> Component.literal("Rune Activated Count: " + objective.runeActivatedCount()), false);
        source.sendSuccess(() -> Component.literal("Rune Remaining Count: " + objective.runeRemainingCount()), false);
        source.sendSuccess(() -> Component.literal("Rune Selected Positions: " + objective.runeSelectedPositionsSummary()), false);
        source.sendSuccess(() -> Component.literal("Rune Crystal UUIDs: " + objective.runeCrystalUuidsSummary()), false);
        source.sendSuccess(() -> Component.literal("Nearest Rune Distance: " + formatDecimal(controller.getNearestRuneDistance(player, level))), false);
        source.sendSuccess(() -> Component.literal("Rune Activation Radius: " + formatDecimal(objective.runeActivationRadius())), false);
        source.sendSuccess(() -> Component.literal("Rune Players In Range: " + objective.runePlayersInRange()), false);
        source.sendSuccess(() -> Component.literal("Remaining Rune Crystal Count: " + objective.remainingRuneCrystalCount()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static String buildObjectiveStatusLine(RoomDebugSnapshot.Objective objective) {
        String mode = objective.selectionMode();
        String selected = objective.autoSelected();
        if ("FORCED".equals(mode)) {
            return "Forced objective " + objective.forcedOverrideSummary() + ".";
        }
        if (selected == null || "NONE".equals(selected)) {
            return "Auto mode idle; fallback objective is CLEAR_ALL until activation.";
        }
        return "Auto selected " + selected + " from candidates: " + objective.autoCandidateList() + ".";
    }

    static int setProfile(CommandContext<CommandSourceStack> context, RoomProfile roomProfile) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        controller.setRoomProfile(roomProfile);
        source.sendSuccess(() -> Component.literal("Room profile set to " + roomProfile + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int debugRoom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        controller.scanMarkers(level);
        RoomDebugSnapshot snapshot = controller.roomDebugSnapshot(level);
        RoomDebugSnapshot.Core core = snapshot.core();
        RoomDebugSnapshot.Objective objective = snapshot.objective();
        RoomDebugSnapshot.Encounter encounter = snapshot.encounter();
        RoomDebugSnapshot.Barrier barrier = snapshot.barrier();
        RoomDebugSnapshot.Markers markers = snapshot.markers();
        ValidationReport report = controller.validate(level, player.blockPosition());
        BlockPos pos = controller.getBlockPos();
        double nearestControllerDistance = Math.sqrt(pos.distSqr(player.blockPosition()));
        boolean playerInsideBounds = markers.hasBounds() && markers.roomBounds().contains(player.blockPosition());
        boolean playerInsideActivationArea = markers.activationBounds() != null && markers.activationBounds().contains(player.blockPosition());
        boolean playerInsideActiveProtectedBounds = ActiveRoomProtectionEvents.isInsideActiveRoomProtectedBounds(level, player.blockPosition());
        String protectionMode = ActiveRoomProtectionEvents.getProtectionMode(level, player.blockPosition());
        Map<Direction, Integer> facingCounts = markers.barrierFacingCounts();
        source.sendSuccess(() -> Component.literal("Tensura Dungeon Room Debug"), false);
        source.sendSuccess(() -> Component.literal("Validation Summary: " + report.summary()), false);
        source.sendSuccess(() -> Component.literal("Room State: " + core.roomState()), false);
        source.sendSuccess(() -> Component.literal("Controller Pos: " + formatPos(pos)), false);
        source.sendSuccess(() -> Component.literal("Nearest Controller Distance: " + formatDecimal(nearestControllerDistance)), false);
        source.sendSuccess(() -> Component.literal("Current Dimension: " + level.dimension().location()), false);
        source.sendSuccess(() -> Component.literal("Is Dungeon Dimension: " + ActiveRoomProtectionEvents.isDungeonDimension(level)), false);
        source.sendSuccess(() -> Component.literal("Protection Mode: " + protectionMode), false);
        source.sendSuccess(() -> Component.literal("Difficulty: " + core.difficulty()), false);
        source.sendSuccess(() -> Component.literal("Activation Radius: " + core.activationRadius()), false);
        source.sendSuccess(() -> Component.literal("Activation Area Mode: " + core.activationAreaMode()), false);
        source.sendSuccess(() -> Component.literal("Activation Inset: " + core.activationInsetStatus()), false);
        source.sendSuccess(() -> Component.literal("Activation Blocked: " + core.activationBlocked()), false);
        source.sendSuccess(() -> Component.literal("Activation Block Reason: " + core.activationBlockReason()), false);
        source.sendSuccess(() -> Component.literal("Requires Active Run: " + core.requiresActiveRun()), false);
        source.sendSuccess(() -> Component.literal("Debug Activation Override: " + DungeonRunManager.get(level).isDebugRoomActivationOverride()), false);
        source.sendSuccess(() -> Component.literal("Registered To Run: " + core.registeredToRun()), false);
        source.sendSuccess(() -> Component.literal("Run Activation Block Reason: " + core.runActivationBlockReason()), false);
        emitMarkerCacheDebugLines(source, markers);
        source.sendSuccess(() -> Component.literal("Room Profile: " + core.roomProfile()), false);
        RoomBudgetResult budgetResult = core.budgetResult();
        source.sendSuccess(() -> Component.literal("Profile Budget Multiplier: " + formatDecimal(core.profileBudgetMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Profile Suggested Max Alive: " + core.profileSuggestedMaxAlive()), false);
        source.sendSuccess(() -> Component.literal("Profile Initial Spawn Count: " + core.profileInitialSpawnCount()), false);
        source.sendSuccess(() -> Component.literal("Budget Floor Number: " + budgetResult.getFloorNumber()), false);
        source.sendSuccess(() -> Component.literal("Budget Floor Base: " + budgetResult.getFloorBaseBudget()), false);
        source.sendSuccess(() -> Component.literal("Budget Profile: " + budgetResult.getProfile()), false);
        source.sendSuccess(() -> Component.literal("Budget Profile Multiplier: " + formatDecimal(budgetResult.getProfileMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Budget Modifier Multiplier: " + formatDecimal(budgetResult.getModifierMultiplier())), false);
        source.sendSuccess(() -> Component.literal("Budget Room Total: " + budgetResult.getRoomTotalBudget()), false);
        source.sendSuccess(() -> Component.literal("Budget Max Alive: " + budgetResult.getMaxAlive()), false);
        source.sendSuccess(() -> Component.literal("Budget Max Total: " + budgetResult.getMaxTotal()), false);
        source.sendSuccess(() -> Component.literal("Budget Spawn Cap Applied: " + budgetResult.isSpawnCapApplied()), false);
        source.sendSuccess(() -> Component.literal("Active Modifiers: " + DungeonRunManager.get(level).getModifierIdsSummary()), false);
        source.sendSuccess(() -> Component.literal("Applied Modifier Effects: " + (DungeonRunManager.get(level).getModifierCount() > 0 ? DungeonRunManager.get(level).getModifierDisplayNamesSummary().toUpperCase() : "NONE")), false);
        source.sendSuccess(() -> Component.literal("Profile Allows Combat: " + core.profileAllowsCombat()), false);
        source.sendSuccess(() -> Component.literal("Profile Allows Objective: " + core.profileAllowsObjective()), false);
        source.sendSuccess(() -> Component.literal("Profile Notes: " + core.profileNotes()), false);
        source.sendSuccess(() -> Component.literal("Objective Type: " + objective.configuredType()), false);
        source.sendSuccess(() -> Component.literal("Objective Active: " + objective.active()), false);
        source.sendSuccess(() -> Component.literal("Objective Timer: " + objective.timerTicks() + " / " + objective.timerMaxTicks()), false);
        source.sendSuccess(() -> Component.literal("Completion Reason: " + core.lastCompletionReason()), false);
        source.sendSuccess(() -> Component.literal("Raw Melee Spawn Anchors: " + markers.rawSpawnAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Melee Spawn Anchors: " + markers.spawnAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Raw Ranged Spawn Anchors: " + markers.rawRangedSpawnAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Ranged Spawn Anchors: " + markers.rangedSpawnAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Raw Flank Spawn Anchors: " + markers.rawFlankSpawnAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Flank Spawn Anchors: " + markers.flankSpawnAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Raw Highground Spawn Anchors: " + markers.rawHighgroundSpawnAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Highground Spawn Anchors: " + markers.highgroundSpawnAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Raw Elite Spawn Anchors: " + markers.rawEliteSpawnAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Elite Spawn Anchors: " + markers.eliteSpawnAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Elite Mob UUID: " + objective.eliteMobUuid()), false);
        source.sendSuccess(() -> Component.literal("Elite Present: " + objective.elitePresent()), false);
        source.sendSuccess(() -> Component.literal("Elite Alive: " + objective.eliteAlive()), false);
        source.sendSuccess(() -> Component.literal("Elite Defeated: " + objective.eliteDefeated()), false);
        source.sendSuccess(() -> Component.literal("Rule-Zone Protection Active: " + core.ruleZoneProtectionActive()), false);
        source.sendSuccess(() -> Component.literal("Protection Grace Active: " + core.protectionGraceActive()), false);
        source.sendSuccess(() -> Component.literal("Protection Grace Ticks Remaining: " + core.protectionGraceTicksRemaining()), false);
        source.sendSuccess(() -> Component.literal("Protected Bounds Mode: " + core.protectedBoundsMode()), false);
        source.sendSuccess(() -> Component.literal("Last Activation Failure: " + core.lastActivationFailure()), false);
        source.sendSuccess(() -> Component.literal("Spawn Role Strategy: " + encounter.lastSpawnStrategy()), false);
        source.sendSuccess(() -> Component.literal("Last Spawn Modifier Summary: " + encounter.lastSpawnModifierSummary()), false);
        source.sendSuccess(() -> Component.literal("Last Spawn Armor Base: " + formatDecimal(encounter.lastSpawnArmorValue())), false);
        source.sendSuccess(() -> Component.literal("Last Spawn Armor Toughness Base: " + formatDecimal(encounter.lastSpawnArmorToughnessValue())), false);
        source.sendSuccess(() -> Component.literal("Molten Modifier Active: " + encounter.moltenModifierDebugStatus()), false);
        source.sendSuccess(() -> Component.literal("Last Molten Retaliation Tick: " + encounter.lastMoltenRetaliationTickSummary()), false);
        source.sendSuccess(() -> Component.literal("Last Molten Retaliation Target: " + encounter.lastMoltenRetaliationTargetSummary()), false);
        source.sendSuccess(() -> Component.literal("Total Molten Retaliations: " + encounter.totalMoltenRetaliations()), false);
        source.sendSuccess(() -> Component.literal("Molten Damage Events Seen: " + encounter.moltenDamageEventsSeen()), false);
        source.sendSuccess(() -> Component.literal("Molten Damage Events With Living Attacker: " + encounter.moltenDamageEventsWithLivingAttacker()), false);
        source.sendSuccess(() -> Component.literal("Molten Damage Events With Tracked Target: " + encounter.moltenDamageEventsWithTrackedTarget()), false);
        source.sendSuccess(() -> Component.literal("Last Molten Rejection Reason: " + encounter.lastMoltenRejectionReason()), false);
        source.sendSuccess(() -> Component.literal("Regen Modifier Active: " + encounter.regenModifierDebugStatus()), false);
        source.sendSuccess(() -> Component.literal("Regen Interval Ticks: " + encounter.regenerativeIntervalTicks()), false);
        source.sendSuccess(() -> Component.literal("Last Regen Pulse Tick: " + encounter.lastRegenPulseTickSummary()), false);
        source.sendSuccess(() -> Component.literal("Last Regen Pulse Healed Mobs: " + encounter.lastRegenPulseHealedCount()), false);
        source.sendSuccess(() -> Component.literal("Last Regen Pulse Health Restored: " + formatDecimal(encounter.lastRegenPulseTotalHealthRestored())), false);
        source.sendSuccess(() -> Component.literal("Summoner Modifier Active: " + encounter.summonerModifierDebugStatus()), false);
        source.sendSuccess(() -> Component.literal("Summoner Interval Ticks: " + encounter.summonerIntervalTicks()), false);
        source.sendSuccess(() -> Component.literal("Summoners Selected: " + encounter.summonersSelectedCount()), false);
        source.sendSuccess(() -> Component.literal("Active Summoners: " + encounter.activeSummonersCount()), false);
        source.sendSuccess(() -> Component.literal("Active Summoned Adds: " + encounter.activeSummonedAddsCount()), false);
        source.sendSuccess(() -> Component.literal("Total Summons This Room: " + encounter.totalSummonsThisRoom()), false);
        source.sendSuccess(() -> Component.literal("Last Summon Pulse Tick: " + encounter.lastSummonPulseTickSummary()), false);
        source.sendSuccess(() -> Component.literal("Last Summon Result: " + encounter.lastSummonResult()), false);
        source.sendSuccess(() -> Component.literal("Last Summon Rejection Reason: " + encounter.lastSummonRejectionReason()), false);
        source.sendSuccess(() -> Component.literal("Last Summoner Entity Type: " + encounter.lastSummonerEntityType()), false);
        source.sendSuccess(() -> Component.literal("Last Summoned Entity Type: " + encounter.lastSummonedEntityType()), false);
        source.sendSuccess(() -> Component.literal("Enemy Pool Registry Enabled: " + encounter.enemyPoolRegistryEnabled()), false);
        source.sendSuccess(() -> Component.literal("Last Enemy Pool Spawn Source: " + encounter.lastEnemyPoolSpawnSource()), false);
        source.sendSuccess(() -> Component.literal("Last Enemy Pool Role Requested: " + encounter.lastEnemyPoolRoleRequested()), false);
        source.sendSuccess(() -> Component.literal("Last Enemy Pool Entity Selected: " + encounter.lastEnemyPoolEntitySelected()), false);
        source.sendSuccess(() -> Component.literal("Last Enemy Pool Family: " + encounter.lastEnemyPoolFamily()), false);
        source.sendSuccess(() -> Component.literal("Last Enemy Pool Source Mod: " + encounter.lastEnemyPoolSourceMod()), false);
        source.sendSuccess(() -> Component.literal("Last Enemy Pool Fallback Reason: " + encounter.lastEnemyPoolFallbackReason()), false);
        source.sendSuccess(() -> Component.literal("Last Enemy Pool Candidate Count: " + encounter.lastEnemyPoolCandidateCount()), false);
        source.sendSuccess(() -> Component.literal("Last Enemy Pool Error: " + encounter.lastEnemyPoolError()), false);
        source.sendSuccess(() -> Component.literal("Raw Spawn Anchors: " + markers.rawSpawnAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Spawn Anchors: " + markers.spawnAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Raw Barrier Anchors: " + markers.rawBarrierAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Barrier Anchors: " + markers.barrierAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Runtime Barrier Material: " + barrier.lastMaterialSummary()), false);
        source.sendSuccess(() -> Component.literal("Barrier Solve Mode: " + barrier.lastSolveMode()), false);
        source.sendSuccess(() -> Component.literal("Barrier Solve Result: " + barrier.lastSolveResult()), false);
        source.sendSuccess(() -> Component.literal("Barrier Solve Reason: " + barrier.lastSolveReason()), false);
        source.sendSuccess(() -> Component.literal("Barrier Plane: " + barrier.lastPlaneSummary()), false);
        source.sendSuccess(() -> Component.literal("Barrier Volume Bounds: " + barrier.lastVolumeBoundsSummary()), false);
        source.sendSuccess(() -> Component.literal("Barrier Volume Size: " + barrier.lastVolumeSizeSummary()), false);
        source.sendSuccess(() -> Component.literal("Barrier Fill Candidates: " + barrier.lastFillCandidateCount()), false);
        source.sendSuccess(() -> Component.literal("Barrier Solved Width x Height: " + barrier.lastSolvedWidth() + " x " + barrier.lastSolvedHeight()), false);
        source.sendSuccess(() -> Component.literal("Barrier Solved Positions: " + barrier.lastSolvedPositionCount()), false);
        source.sendSuccess(() -> Component.literal("Barrier Placed / Skipped / Cleared: " + barrier.lastPlacedBlockCount() + " / " + barrier.lastSkippedBlockCount() + " / " + barrier.lastClearedBlockCount()), false);
        for (String line : barrier.lastDebugLines()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal("Raw Bounds Anchors: " + markers.rawBoundsAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Bounds Anchors: " + markers.boundsAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Raw Objective Anchors: " + markers.rawObjectiveAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Objective Anchors: " + markers.objectiveAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Bounds Mode: " + markers.boundsMode()), false);
        if (markers.hasBounds()) {
            RoomControllerBlockEntity.RoomBounds bounds = markers.roomBounds();
            source.sendSuccess(() -> Component.literal("Bounds Min: " + formatPos(bounds.min())), false);
            source.sendSuccess(() -> Component.literal("Bounds Max: " + formatPos(bounds.max())), false);
            source.sendSuccess(() -> Component.literal("Bounds Dimensions: " + bounds.getWidth() + " x " + bounds.getHeight() + " x " + bounds.getDepth()), false);
            RoomControllerBlockEntity.RoomBounds activationBounds = markers.activationBounds();
            source.sendSuccess(() -> Component.literal("Activation Min: " + formatPos(activationBounds.min())), false);
            source.sendSuccess(() -> Component.literal("Activation Max: " + formatPos(activationBounds.max())), false);
            source.sendSuccess(() -> Component.literal("Player Inside Bounds: " + playerInsideBounds), false);
            source.sendSuccess(() -> Component.literal("Player Inside Activation Area: " + playerInsideActivationArea), false);
        }
        source.sendSuccess(() -> Component.literal("Player Inside Active Protected Bounds: " + playerInsideActiveProtectedBounds), false);
        source.sendSuccess(() -> Component.literal("Raw Mob Reset Anchors: " + markers.rawMobResetAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Mob Reset Anchors: " + markers.mobResetAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Raw Exit Anchors: " + markers.rawExitAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Exit Anchors: " + markers.exitAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Current Alive Tracked Mobs: " + encounter.currentAliveTrackedMobCount()), false);
        source.sendSuccess(() -> Component.literal("Current Tracked Mobs: " + encounter.trackedMobCount()), false);
        source.sendSuccess(() -> Component.literal("Spawn Requested Count: " + encounter.lastSpawnRequestedCount()), false);
        source.sendSuccess(() -> Component.literal("Spawn Actual Count Last Activation: " + encounter.lastSpawnActualCount()), false);
        source.sendSuccess(() -> Component.literal("Spawn Skipped Reason: " + encounter.lastSpawnSkippedReason()), false);
        source.sendSuccess(() -> Component.literal("Last Spawn Role Counts: melee=" + encounter.lastSpawnMeleeUsedCount() + ", ranged=" + encounter.lastSpawnRangedUsedCount() + ", flank=" + encounter.lastSpawnFlankUsedCount() + ", highground=" + encounter.lastSpawnHighgroundUsedCount() + ", generic=" + encounter.lastSpawnGenericUsedCount()), false);
        source.sendSuccess(() -> Component.literal("Associated Active Players: " + core.associatedActivePlayerCount()), false);
        source.sendSuccess(() -> Component.literal("Unresolved Tracked Mobs: " + encounter.unresolvedTrackedMobCount()), false);
        source.sendSuccess(() -> Component.literal("Stale Tracked Mobs: " + encounter.staleTrackedMobCount()), false);
        source.sendSuccess(() -> Component.literal("Mob Reset Cooldowns: " + encounter.mobResetCooldownCount()), false);
        source.sendSuccess(() -> Component.literal("Protection Break Events: " + ActiveRoomProtectionEvents.getBreakEventsFired()), false);
        source.sendSuccess(() -> Component.literal("Protection Place Events: " + ActiveRoomProtectionEvents.getPlaceEventsFired()), false);
        source.sendSuccess(() -> Component.literal("Protection Last Event: " + ActiveRoomProtectionEvents.getLastEventType()), false);
        source.sendSuccess(() -> Component.literal("Protection Last Player: " + ActiveRoomProtectionEvents.getLastPlayerName()), false);
        source.sendSuccess(() -> Component.literal("Protection Target Pos: " + ActiveRoomProtectionEvents.getLastTargetPos()), false);
        source.sendSuccess(() -> Component.literal("Protection Last Server Side: " + ActiveRoomProtectionEvents.wasLastServerSide()), false);
        source.sendSuccess(() -> Component.literal("Protection Controller Found: " + ActiveRoomProtectionEvents.wasLastControllerFound()), false);
        source.sendSuccess(() -> Component.literal("Protection Controller Pos: " + ActiveRoomProtectionEvents.getLastControllerPos()), false);
        source.sendSuccess(() -> Component.literal("Protection Controller State: " + ActiveRoomProtectionEvents.getLastControllerState()), false);
        source.sendSuccess(() -> Component.literal("Protection Bounds Mode: " + ActiveRoomProtectionEvents.getLastBoundsMode()), false);
        source.sendSuccess(() -> Component.literal("Protection Bounds Min: " + ActiveRoomProtectionEvents.getLastBoundsMin()), false);
        source.sendSuccess(() -> Component.literal("Protection Bounds Max: " + ActiveRoomProtectionEvents.getLastBoundsMax()), false);
        source.sendSuccess(() -> Component.literal("Protection Target Inside Bounds: " + ActiveRoomProtectionEvents.wasLastTargetInsideBounds()), false);
        source.sendSuccess(() -> Component.literal("Protection Cancellation Applied: " + ActiveRoomProtectionEvents.wasLastCancellationApplied()), false);
        source.sendSuccess(() -> Component.literal("Future Dimension-Wide Protection: " + ActiveRoomProtectionEvents.isFutureDungeonDimensionWideProtectionEnabled()), false);
        source.sendSuccess(() -> Component.literal("Environmental Protection Active: " + ActiveRoomProtectionEvents.isEnvironmentalProtectionActive()), false);
        source.sendSuccess(() -> Component.literal("Active Registered Protection Controllers: " + ActiveRoomProtectionEvents.getRegisteredProtectionControllerCount(level)), false);
        source.sendSuccess(() -> Component.literal("Protection Mob Grief Events: " + ActiveRoomProtectionEvents.getMobGriefEventsFired()), false);
        source.sendSuccess(() -> Component.literal("Protection Mob Grief Blocked: " + ActiveRoomProtectionEvents.getMobGriefEventsBlocked()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Count: " + ActiveRoomProtectionEvents.getProtectionLookupCount()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Hits: " + ActiveRoomProtectionEvents.getProtectionLookupHits()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Misses: " + ActiveRoomProtectionEvents.getProtectionLookupMisses()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Candidates Last: " + ActiveRoomProtectionEvents.getProtectionLookupCandidateCountLast()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Registered Pruned: " + ActiveRoomProtectionEvents.getProtectionLookupRegisteredPruned()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Source: " + ActiveRoomProtectionEvents.getLastProtectionLookupSource()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Used World Scan: " + ActiveRoomProtectionEvents.wasLastProtectionLookupUsedWorldScan()), false);
        source.sendSuccess(() -> Component.literal("Protection Lookup Attempted Marker Refresh: " + ActiveRoomProtectionEvents.wasLastProtectionLookupAttemptedMarkerRefresh()), false);
        source.sendSuccess(() -> Component.literal("Explosion Debug Enabled: " + ActiveRoomProtectionEvents.isExplosionDebugEnabled()), false);
        source.sendSuccess(() -> Component.literal("Explosion Events Fired: " + ActiveRoomProtectionEvents.getExplosionEventsFired()), false);
        source.sendSuccess(() -> Component.literal("Explosion Protected Blocks Removed: " + ActiveRoomProtectionEvents.getProtectedExplosionBlocksRemoved()), false);
        source.sendSuccess(() -> Component.literal("Explosion Pos: " + ActiveRoomProtectionEvents.getLastExplosionPos()), false);
        source.sendSuccess(() -> Component.literal("Explosion Affected Before: " + ActiveRoomProtectionEvents.getLastExplosionAffectedBefore()), false);
        source.sendSuccess(() -> Component.literal("Explosion Protected Removed Last: " + ActiveRoomProtectionEvents.getLastExplosionProtectedRemoved()), false);
        source.sendSuccess(() -> Component.literal("Explosion Affected After: " + ActiveRoomProtectionEvents.getLastExplosionAffectedAfter()), false);
        source.sendSuccess(() -> Component.literal("Active Nearby Controllers: " + core.nearbyActiveRoomCount()), false);
        source.sendSuccess(() -> Component.literal("Completed: " + core.completed()), false);
        source.sendSuccess(() -> Component.literal("Runtime Barrier Blocks: " + barrier.placedBlockCount()), false);
        source.sendSuccess(() -> Component.literal("Runtime Barrier Positions: " + barrier.runtimePositionCount()), false);
        source.sendSuccess(() -> Component.literal("Preview Blocks: " + barrier.previewBlockCount()), false);
        source.sendSuccess(() -> Component.literal("Barrier Preview Blocks: " + barrier.barrierPreviewBlockCount()), false);
        source.sendSuccess(() -> Component.literal("Bounds Preview Blocks: " + markers.boundsPreviewBlockCount()), false);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int count = facingCounts.getOrDefault(direction, 0);
            source.sendSuccess(() -> Component.literal(direction.getName().toUpperCase() + ": " + count), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    static int inspectRoomReadiness(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("Room Inspect: FAIL"));
            source.sendFailure(Component.literal("Nearest Controller: NONE"));
            return 0;
        }

        controller.scanMarkers(level);
        ValidationReport report = controller.validate(level, player.blockPosition());
        RoomDebugSnapshot.Markers markers = controller.roomMarkerDebugSnapshot(level);
        RoomControllerBlockEntity.RoomBounds bounds = markers.roomBounds();
        double distance = Math.sqrt(controller.getBlockPos().distSqr(player.blockPosition()));
        String readiness = stage15ReadinessStatus(controller, report);
        String requiredAnchors = stage15RequiredAnchorsStatus(controller, report);
        StructureMarkerReadinessReport structureReport = StructureMarkerReadinessValidator.analyze(level, controller);
        String exitPortalReadiness = !markers.exitAnchors().isEmpty() ? "PASS" : "FAIL";
        DungeonRunManager.ExitPortalPlacementResult exitPortalPlacement = DungeonRunManager.analyzeExitPortalPlacement(level, controller.getBlockPos(), controller);

        source.sendSuccess(() -> Component.literal("Room Inspect: " + readiness), false);
        source.sendSuccess(() -> Component.literal("Nearest Controller: FOUND"), false);
        source.sendSuccess(() -> Component.literal("Controller Pos: " + formatPos(controller.getBlockPos())), false);
        source.sendSuccess(() -> Component.literal("Distance: " + formatDecimal(distance)), false);
        source.sendSuccess(() -> Component.literal("Dimension: " + level.dimension().location()), false);
        source.sendSuccess(() -> Component.literal("Room State: " + controller.getRoomState()), false);
        source.sendSuccess(() -> Component.literal("Room Profile: " + controller.getRoomProfile()), false);
        source.sendSuccess(() -> Component.literal("Objective Type: " + controller.getConfiguredObjectiveType()), false);
        source.sendSuccess(() -> Component.literal("Bounds Mode: " + markers.boundsMode()), false);
        source.sendSuccess(() -> Component.literal("Bounds Anchors: " + markers.boundsAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Bounds Min: " + (bounds == null ? "NONE" : formatPos(bounds.min()))), false);
        source.sendSuccess(() -> Component.literal("Structure Marker Readiness: " + structureReport.readinessLabel()), false);
        source.sendSuccess(() -> Component.literal("Jigsaw Template Readiness: " + structureReport.jigsawTemplateReadinessLabel()), false);
        source.sendSuccess(() -> Component.literal("Structure Missing Required Markers: " + structureReport.missingSummary()), false);
        source.sendSuccess(() -> Component.literal("Bounds Max: " + (bounds == null ? "NONE" : formatPos(bounds.max()))), false);
        source.sendSuccess(() -> Component.literal("Bounds Dimensions: " + (bounds == null ? "NONE" : bounds.getWidth() + " x " + bounds.getHeight() + " x " + bounds.getDepth())), false);
        source.sendSuccess(() -> Component.literal("Player Inside Bounds: " + (bounds != null && bounds.contains(player.blockPosition()))), false);
        source.sendSuccess(() -> Component.literal("Player Inside Activation Area: " + (markers.activationBounds() != null && markers.activationBounds().contains(player.blockPosition()))), false);
        source.sendSuccess(() -> Component.literal("Required Anchors: " + requiredAnchors), false);
        source.sendSuccess(() -> Component.literal("Combat Anchors Owned: melee=" + markers.spawnAnchors().size() + " ranged=" + markers.rangedSpawnAnchors().size() + " flank=" + markers.flankSpawnAnchors().size() + " highground=" + markers.highgroundSpawnAnchors().size() + " elite=" + markers.eliteSpawnAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Barrier Anchors Owned: " + markers.barrierAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Mob Reset Anchors Owned: " + markers.mobResetAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Exit Anchors Owned: " + markers.exitAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Objective Anchors Owned: " + markers.objectiveAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Interact Anchors Owned: " + markers.interactAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Raw Marker Counts: bounds=" + markers.rawBoundsAnchorCount() + ", melee=" + markers.rawSpawnAnchorCount() + ", ranged=" + markers.rawRangedSpawnAnchorCount() + ", flank=" + markers.rawFlankSpawnAnchorCount() + ", highground=" + markers.rawHighgroundSpawnAnchorCount() + ", elite=" + markers.rawEliteSpawnAnchorCount() + ", barrier=" + markers.rawBarrierAnchorCount() + ", mob_reset=" + markers.rawMobResetAnchorCount() + ", exit=" + markers.rawExitAnchorCount() + ", objective=" + markers.rawObjectiveAnchorCount() + ", interact=" + markers.rawInteractAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Marker Counts: bounds=" + markers.boundsAnchors().size() + ", melee=" + markers.spawnAnchors().size() + ", ranged=" + markers.rangedSpawnAnchors().size() + ", flank=" + markers.flankSpawnAnchors().size() + ", highground=" + markers.highgroundSpawnAnchors().size() + ", elite=" + markers.eliteSpawnAnchors().size() + ", barrier=" + markers.barrierAnchors().size() + ", mob_reset=" + markers.mobResetAnchors().size() + ", exit=" + markers.exitAnchors().size() + ", objective=" + markers.objectiveAnchors().size() + ", interact=" + markers.interactAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Activation Block Reason: " + controller.getActivationBlockReason(level)), false);
        source.sendSuccess(() -> Component.literal("Run Activation Block Reason: " + controller.getRunActivationBlockReason(level)), false);
        emitMarkerCacheDebugLines(source, markers);
        source.sendSuccess(() -> Component.literal("Exit Portal Readiness: " + exitPortalReadiness), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Readiness Reason: " + exitPortalPlacement.reason()), false);
        source.sendSuccess(() -> Component.literal("Exit Portal Readiness Details: " + exitPortalPlacement.details()), false);
        source.sendSuccess(() -> Component.literal("Boss Anchor Support: " + (structureReport.bossSpawnCount() > 0 ? "MARKER_PRESENT" : "NOT_PRESENT")), false);
        source.sendSuccess(() -> Component.literal("Validation Summary: " + report.summary()), false);
        return "FAIL".equals(readiness) ? 0 : Command.SINGLE_SUCCESS;
    }

    static int inspectRoomAnchors(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("Room Anchors: FAIL - no room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        controller.scanMarkers(level);
        RoomDebugSnapshot.Markers markers = controller.roomMarkerDebugSnapshot(level);
        StructureMarkerReadinessReport structureReport = StructureMarkerReadinessValidator.analyze(level, controller);
        source.sendSuccess(() -> Component.literal("Room Anchors: " + stage15RequiredAnchorsStatus(controller, controller.validate(level, player.blockPosition()))), false);
        source.sendSuccess(() -> Component.literal("Controller Pos: " + formatPos(controller.getBlockPos())), false);
        source.sendSuccess(() -> Component.literal("Bounds Mode: " + markers.boundsMode()), false);
        source.sendSuccess(() -> Component.literal("Raw Marker Counts: bounds=" + markers.rawBoundsAnchorCount() + ", melee=" + markers.rawSpawnAnchorCount() + ", ranged=" + markers.rawRangedSpawnAnchorCount() + ", flank=" + markers.rawFlankSpawnAnchorCount() + ", highground=" + markers.rawHighgroundSpawnAnchorCount() + ", elite=" + markers.rawEliteSpawnAnchorCount() + ", barrier=" + markers.rawBarrierAnchorCount() + ", mob_reset=" + markers.rawMobResetAnchorCount() + ", exit=" + markers.rawExitAnchorCount() + ", objective=" + markers.rawObjectiveAnchorCount() + ", interact=" + markers.rawInteractAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Owned Marker Counts: bounds=" + markers.boundsAnchors().size() + ", melee=" + markers.spawnAnchors().size() + ", ranged=" + markers.rangedSpawnAnchors().size() + ", flank=" + markers.flankSpawnAnchors().size() + ", highground=" + markers.highgroundSpawnAnchors().size() + ", elite=" + markers.eliteSpawnAnchors().size() + ", barrier=" + markers.barrierAnchors().size() + ", mob_reset=" + markers.mobResetAnchors().size() + ", exit=" + markers.exitAnchors().size() + ", objective=" + markers.objectiveAnchors().size() + ", interact=" + markers.interactAnchors().size()), false);
        emitMarkerCacheDebugLines(source, markers);
        source.sendSuccess(() -> Component.literal("Boss Anchor Support: " + (structureReport.bossSpawnCount() > 0 ? "MARKER_PRESENT" : "NOT_PRESENT")), false);
        return Command.SINGLE_SUCCESS;
    }

    static int inspectStructureMarkers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("Structure Markers: FAIL - no room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        emitStructureMarkerReadiness(source, StructureMarkerReadinessValidator.analyze(level, controller), false);
        return Command.SINGLE_SUCCESS;
    }

    static int validateStructureReadiness(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("Structure Marker Readiness: FAIL - no room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        StructureMarkerReadinessReport report = StructureMarkerReadinessValidator.analyze(level, controller);
        emitStructureMarkerReadiness(source, report, true);
        return report.readinessPass() ? Command.SINGLE_SUCCESS : 0;
    }

    static int debugStructureBounds(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("Structure Bounds Debug: FAIL - no room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        controller.scanMarkers(level);
        RoomDebugSnapshot.Markers markers = controller.roomMarkerDebugSnapshot(level);
        RoomControllerBlockEntity.RoomBounds bounds = markers.roomBounds();
        source.sendSuccess(() -> Component.literal("Structure Bounds Debug"), false);
        source.sendSuccess(() -> Component.literal("Controller: " + formatPos(controller.getBlockPos())), false);
        source.sendSuccess(() -> Component.literal("Bounds Mode: " + markers.boundsMode()), false);
        source.sendSuccess(() -> Component.literal("Bounds Anchors: " + markers.boundsAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Bounds: " + (bounds == null ? "NONE" : formatPos(bounds.min()) + " -> " + formatPos(bounds.max()))), false);
        if (bounds != null) {
            source.sendSuccess(() -> Component.literal("Bounds Dimensions: " + bounds.getWidth() + " x " + bounds.getHeight() + " x " + bounds.getDepth()), false);
        }
        emitMarkerCacheDebugLines(source, markers);
        return Command.SINGLE_SUCCESS;
    }

    private static void emitStructureMarkerReadiness(CommandSourceStack source, StructureMarkerReadinessReport report, boolean includeMissingAndWarnings) {
        source.sendSuccess(() -> Component.literal("Structure Marker Readiness"), false);
        source.sendSuccess(() -> Component.literal("Controller: " + formatPos(report.nearestControllerPos())), false);
        source.sendSuccess(() -> Component.literal("Profile: " + report.roomProfile()), false);
        source.sendSuccess(() -> Component.literal("Bounds: " + report.boundsSummary()), false);
        source.sendSuccess(() -> Component.literal("Bounds Anchors: " + report.boundsAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Room Controllers In Template: " + report.roomControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Spawn Markers: melee=" + report.meleeSpawnCount() + ", ranged=" + report.rangedSpawnCount() + ", flank=" + report.flankSpawnCount() + ", highground=" + report.highgroundSpawnCount() + ", elite=" + report.eliteSpawnCount() + ", boss=" + report.bossSpawnCount()), false);
        source.sendSuccess(() -> Component.literal("Barrier Anchors: " + report.barrierAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Objective Anchors: " + report.objectiveAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Exit Anchors: " + report.exitAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Safe Zone Anchors: " + report.safeZoneAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Mob Reset Anchors: " + report.mobResetAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Floor Entry Anchors: " + report.floorEntryAnchorCount()), false);
        source.sendSuccess(() -> Component.literal("Required Markers: " + report.readinessLabel()), false);
        source.sendSuccess(() -> Component.literal("Jigsaw Template Readiness: " + report.jigsawTemplateReadinessLabel()), false);
        if (includeMissingAndWarnings || !report.missingRequiredMarkers().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Missing: " + report.missingSummary()), false);
        }
        source.sendSuccess(() -> Component.literal("Warnings: " + report.warningsSummary()), false);
        source.sendSuccess(() -> Component.literal("Future Hub/Floor Markers: hub_spawn=" + report.hubSpawnAnchorCount() + ", hub_return=" + report.hubReturnAnchorCount() + ", hub_safe_zone=" + report.hubSafeZoneAnchorCount() + ", hub_gate=" + report.hubGateAnchorCount() + ", hub_floor_select=" + report.hubFloorSelectAnchorCount() + ", floor_build_origin=" + report.floorBuildOriginCount()), false);
    }
    static String stage15ReadinessStatus(RoomControllerBlockEntity controller, ValidationReport report) {
        if (stage15HasHardFail(controller, report)) {
            return "FAIL";
        }
        if (stage15HasWarnings(controller, report)) {
            return "PASS_WITH_WARNINGS";
        }
        return "PASS";
    }

    private static String stage15RequiredAnchorsStatus(RoomControllerBlockEntity controller, ValidationReport report) {
        RoomDebugSnapshot.Markers markers = roomMarkers(controller);
        if (stage15HasRequiredAnchorFailure(controller) || report.summary() == RoomControllerBlockEntity.ValidationSummary.FAIL) {
            return "FAIL";
        }
        if (markers.boundsAnchors().isEmpty() || markers.boundsAnchors().size() == 1 || report.summary() == RoomControllerBlockEntity.ValidationSummary.WARN) {
            return "WARN";
        }
        return "PASS";
    }

    static boolean stage15HasHardFail(RoomControllerBlockEntity controller, ValidationReport report) {
        if (report.summary() == RoomControllerBlockEntity.ValidationSummary.FAIL) {
            return true;
        }
        if (roomMarkers(controller).boundsAnchors().size() == 1) {
            return true;
        }
        return stage15HasRequiredAnchorFailure(controller);
    }

    private static boolean stage15HasRequiredAnchorFailure(RoomControllerBlockEntity controller) {
        RoomDebugSnapshot.Markers markers = roomMarkers(controller);
        if (controller.doesProfileAllowCombat() && markers.ownedCombatAnchorCount() == 0) {
            return true;
        }
        if (!hasAnyOwnedBarrierAnchor(controller)) {
            return true;
        }
        if (markers.mobResetAnchors().isEmpty()) {
            return true;
        }
        if (markers.exitAnchors().isEmpty()) {
            return true;
        }
        return controller.getConfiguredObjectiveType().requiresObjectiveAnchor() && markers.objectiveAnchors().isEmpty();
    }

    private static boolean stage15HasWarnings(RoomControllerBlockEntity controller, ValidationReport report) {
        return report.summary() == RoomControllerBlockEntity.ValidationSummary.WARN || roomMarkers(controller).boundsAnchors().isEmpty();
    }

    private static boolean hasAnyOwnedBarrierAnchor(RoomControllerBlockEntity controller) {
        return roomMarkers(controller).hasAnyBarrierAnchor();
    }

    static int ownedCombatAnchorCount(RoomControllerBlockEntity controller) {
        return roomMarkers(controller).ownedCombatAnchorCount();
    }

    static int ownedBarrierAnchorCount(RoomControllerBlockEntity controller) {
        RoomDebugSnapshot.Markers markers = roomMarkers(controller);
        return markers.barrierAnchors().size()
            + markers.barrierAnchor1Anchors().size()
            + markers.barrierAnchor2Anchors().size()
            + markers.barrierAnchor3Anchors().size()
            + markers.barrierAnchor4Anchors().size();
    }

    private static RoomDebugSnapshot.Markers roomMarkers(RoomControllerBlockEntity controller) {
        return controller.roomMarkerDebugSnapshot((ServerLevel) controller.getLevel());
    }

    private static void emitMarkerCacheDebugLines(CommandSourceStack source, RoomDebugSnapshot.Markers markers) {
        source.sendSuccess(() -> Component.literal("Marker Cache Ready: " + markers.markerCacheReady()), false);
        source.sendSuccess(() -> Component.literal("Marker Cache Dirty: " + markers.markerCacheDirty()), false);
        source.sendSuccess(() -> Component.literal("Marker Cache Dirty Reason: " + markers.markerCacheDirtyReason()), false);
        source.sendSuccess(() -> Component.literal("Marker Scan Count: " + markers.markerScanCount()), false);
        source.sendSuccess(() -> Component.literal("Last Marker Scan Reason: " + markers.lastMarkerScanReason()), false);
        source.sendSuccess(() -> Component.literal("Last Marker Scan Game Time: " + markers.lastMarkerScanGameTime()), false);
        source.sendSuccess(() -> Component.literal("Last Marker Scan Duration Ms: " + formatDecimal(markers.lastMarkerScanDurationMs() < 0.0D ? 0.0D : markers.lastMarkerScanDurationMs())), false);
        source.sendSuccess(() -> Component.literal("Last Marker Scan Warning: " + markers.lastMarkerScanPerformanceWarning()), false);
        source.sendSuccess(() -> Component.literal("Marker Ownership Mode: " + markers.markerOwnershipMode()), false);
        source.sendSuccess(() -> Component.literal("Claimed Marker Mode: " + markers.claimedMarkerMode()), false);
        source.sendSuccess(() -> Component.literal("Last Marker Claim Radius: " + markers.lastMarkerClaimRadius()), false);
        source.sendSuccess(() -> Component.literal("Last Marker Claim Total: " + markers.lastMarkerClaimTotal()), false);
        source.sendSuccess(() -> Component.literal("Last Marker Claim Skipped Owned By Other: " + markers.lastMarkerClaimSkippedOwnedByOther()), false);
        source.sendSuccess(() -> Component.literal("Last Marker Claim Stale Removed: " + markers.lastMarkerClaimStaleRemoved()), false);
        source.sendSuccess(() -> Component.literal("Last Marker Claim Summary: " + markers.lastMarkerClaimSummary()), false);
        source.sendSuccess(() -> Component.literal("Last Marker Claim Reason: " + markers.lastMarkerClaimReason()), false);
        source.sendSuccess(() -> Component.literal("Last Marker Claim Duration Ms: " + formatDecimal(markers.lastMarkerClaimDurationMs() < 0.0D ? 0.0D : markers.lastMarkerClaimDurationMs())), false);
    }

    static int validateRoom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("FAIL: No room_controller found within " + DEBUG_RADIUS + " blocks."));
            return 0;
        }

        controller.scanMarkers(level);
        ValidationReport report = controller.validate(level, player.blockPosition());
        StructureMarkerReadinessReport structureReport = StructureMarkerReadinessValidator.analyze(level, controller);
        source.sendSuccess(() -> Component.literal("Room Validation: " + report.summary()), false);
        source.sendSuccess(() -> Component.literal("Structure Marker Readiness: " + structureReport.readinessLabel()), false);
        source.sendSuccess(() -> Component.literal("Jigsaw Template Readiness: " + structureReport.jigsawTemplateReadinessLabel()), false);
        source.sendSuccess(() -> Component.literal("Missing Required Markers: " + structureReport.missingSummary()), false);
        for (String line : report.lines()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
