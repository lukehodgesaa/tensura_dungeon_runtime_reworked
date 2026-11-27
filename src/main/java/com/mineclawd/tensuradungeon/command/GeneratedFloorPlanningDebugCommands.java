package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import com.mineclawd.tensuradungeon.runtime.DungeonFloorProgressData;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonGameplayComposer;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonRoomRole;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonThemeSelectionPolicy;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadata;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonPlanAdapter;
import com.mineclawd.tensuradungeon.runtime.generation.RollingDungeonSpatialSolver;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

final class GeneratedFloorPlanningDebugCommands {
    private GeneratedFloorPlanningDebugCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerMetadataCommand() {
        return Commands.literal("metadata")
            .executes(context -> showMetadata(context, false))
            .then(Commands.literal("reload").executes(context -> showMetadata(context, true)));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerComposeCommand() {
        return Commands.literal("compose")
            .then(Commands.argument("rooms", IntegerArgumentType.integer(3, 64))
                .executes(GeneratedFloorPlanningDebugCommands::compose));
    }

    static LiteralArgumentBuilder<CommandSourceStack> registerSolveCommand() {
        return Commands.literal("solve")
            .then(Commands.argument("rooms", IntegerArgumentType.integer(3, 64))
                .executes(GeneratedFloorPlanningDebugCommands::solve));
    }
    private static int showMetadata(CommandContext<CommandSourceStack> context, boolean reload) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        DungeonTemplateMetadataRegistry.Snapshot snapshot = reload
            ? DungeonTemplateMetadataRegistry.reload(level)
            : DungeonTemplateMetadataRegistry.get(level);
        if (reload) DungeonTemplateMechanicalRegistry.invalidate();
        source.sendSuccess(() -> Component.literal("Generated Template Metadata"), false);
        source.sendSuccess(() -> Component.literal("State Mutated: NO | cacheReloaded=" + reload), false);
        source.sendSuccess(() -> Component.literal("Registry: " + (snapshot.valid() ? "PASS" : "FAIL")
            + " | total=" + snapshot.templates().size()
            + " | enabled=" + snapshot.enabledTemplates().size()
            + " | excluded=" + snapshot.excludedTemplates().size()
            + " | errors=" + snapshot.errors().size()), false);
        source.sendSuccess(() -> Component.literal(
            "Mechanical Cache: SKIPPED | no NBT inspection performed by metadata diagnostic; planning inspects only the selected theme"
        ), false);
        for (String error : snapshot.errors()) source.sendFailure(Component.literal("Metadata Error: " + error));
        for (DungeonTemplateMetadata template : snapshot.excludedTemplates()) {
            source.sendSuccess(() -> Component.literal("Excluded: " + template.id() + " | reason=" + template.exclusionReason()), false);
        }
        for (DungeonRoomRole role : DungeonRoomRole.values()) {
            long count = snapshot.enabledTemplates().stream().filter(template -> template.roles().contains(role)).count();
            source.sendSuccess(() -> Component.literal("Role " + role + ": " + count + " enabled template(s)"), false);
        }
        return snapshot.valid() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int compose(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int rooms = IntegerArgumentType.getInteger(context, "rooms");
        int floor = Math.max(1, DungeonRunManager.get(level).getFloorNumber());
        long seed = level.getSeed() ^ (0x9E3779B97F4A7C15L * floor) ^ rooms;
        DungeonGameplayComposer.Composition composition = DungeonGameplayComposer.compose(floor, rooms, seed);
        source.sendSuccess(() -> Component.literal("Generated Gameplay Composition Dry Run"), false);
        source.sendSuccess(() -> Component.literal("State Mutated: NO | floor=" + floor + " | rooms=" + rooms + " | seed=" + seed), false);
        source.sendSuccess(() -> Component.literal("Targets: " + composition.targets().entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", "))), false);
        for (DungeonGameplayComposer.Beat beat : composition.beats()) {
            source.sendSuccess(() -> Component.literal(beat.index() + ". role=" + beat.role()
                + " | intensity=" + beat.intensity()
                + " | objective=" + beat.objective()
                + " | purpose=" + beat.purpose()), false);
        }
        source.sendSuccess(() -> Component.literal("Composition: PASS - abstract only; no NBT loaded and no blocks placed"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int solve(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int rooms = IntegerArgumentType.getInteger(context, "rooms");
        int floor = Math.max(1, DungeonRunManager.get(level).getFloorNumber());
        BlockPos origin = BlockPos.containing(source.getPosition());
        Direction direction = Direction.fromYRot(source.getRotation().y);
        long seed = level.getSeed() ^ origin.asLong() ^ (0x9E3779B97F4A7C15L * floor) ^ rooms;
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Generated rolling solve requires a player context."));
            return 0;
        }
        DungeonTemplateMetadataRegistry.Snapshot authored = DungeonTemplateMetadataRegistry.get(level);
        DungeonThemeSelectionPolicy.Snapshot themePolicy = DungeonThemeSelectionPolicy.get(level);
        DungeonThemeSelectionPolicy.Selection themeSelection = themePolicy.select(
            authored.visualThemes(), DungeonFloorProgressData.get(level).getHighestUnlockedFloor(player.getUUID()),
            seed, floor
        );
        if (!themeSelection.succeeded()) {
            source.sendFailure(Component.literal("Generated rolling solve cannot select a theme: " + themeSelection.reason()));
            return 0;
        }
        DungeonGameplayComposer.Composition composition = DungeonGameplayComposer.compose(floor, rooms, seed);
        DungeonTemplateMechanicalRegistry.Snapshot mechanical = DungeonTemplateMechanicalRegistry.getForTheme(
            level, authored, themeSelection.themeId()
        );
        RollingDungeonSpatialSolver.Result result = RollingDungeonSpatialSolver.solveWindow(
            composition.beats(), authored, mechanical, origin, direction,
            level.getMinBuildHeight(), level.getMaxBuildHeight(), seed,
            List.of(), List.of(), false,
            new RollingDungeonSpatialSolver.SelectionContext(
                rooms, List.of(), java.util.Set.of(), Map.of(), themeSelection.themeId()
            )
        );
        RollingDungeonPlanAdapter.Result adapted = RollingDungeonPlanAdapter.adapt(level, result, mechanical);
        source.sendSuccess(() -> Component.literal("Generated Rolling Spatial Solver Dry Run"), false);
        source.sendSuccess(() -> Component.literal("State Mutated: NO | requested=" + rooms
            + " | horizon=" + Math.min(RollingDungeonSpatialSolver.DEFAULT_HORIZON, rooms)
            + " | origin=" + origin.toShortString() + " | direction=" + direction.getName()), false);
        source.sendSuccess(() -> Component.literal("Selected Visual Theme: " + themeSelection.themeId()), false);
        source.sendSuccess(() -> Component.literal("Solver: " + (result.solved() ? "PASS" : "FAIL")
            + " | reason=" + result.reason()
            + " | visited=" + result.visitedNodes()
            + " | backtracks=" + result.backtracks()
            + " | elapsedMs=" + String.format(java.util.Locale.ROOT, "%.2f", result.elapsedMillis())), false);
        source.sendSuccess(() -> Component.literal("Persistence Adapter: " + (adapted.valid() ? "PASS" : "FAIL")
            + " | plans=" + adapted.plans().size() + " | errors=" + adapted.errors().size()), false);
        for (String error : adapted.errors()) source.sendFailure(Component.literal("Adapter Error: " + error));
        for (RollingDungeonSpatialSolver.PlacedNode node : result.nodes()) {
            source.sendSuccess(() -> Component.literal(node.roomIndex() + ". role=" + node.role()
                + " | template=" + node.templateId()
                + " | rotation=" + node.rotation()
                + " | cost=" + node.placementCost()
                + " | outgoing=" + (node.outgoingFacing() == null ? "NONE" : node.outgoingFacing().getName())
                + " | bounds=" + node.bounds()), false);
        }
        if (!result.solved()) {
            source.sendFailure(Component.literal("Top rejections: " + result.rejectionCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(6)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "))));
        }
        return result.solved() && adapted.valid() ? Command.SINGLE_SUCCESS : 0;
    }}
