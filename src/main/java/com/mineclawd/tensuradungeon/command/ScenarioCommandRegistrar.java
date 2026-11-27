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
final class ScenarioCommandRegistrar {
    private ScenarioCommandRegistrar() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("test")
            .executes(ScenarioCommandRegistrar::testHelp)
            .then(Commands.literal("help").executes(ScenarioCommandRegistrar::testHelp))
            .then(Commands.literal("scenarios").executes(ScenarioCommandRegistrar::listScenarios))
            .then(Commands.literal("scenario")
                .executes(ScenarioCommandRegistrar::listScenarios)
                .then(Commands.literal("safe_room").executes(ScenarioCommandRegistrar::scenarioSafeRoom))

                ));
    }
    static int testHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("=== Tensura Dungeon Test Commands ==="), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon test scenario safe_room - verify safe-room profile sanity."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon run nextfloor - advance to the next unlocked floor."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon floor progress - inspect persistent highest-unlocked and completed-floor state."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon floor start <floor> - start an unlocked floor directly, including redo."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon floor preset scan - scan nearby authored room controllers for preset-floor ordering."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon floor linear scan - legacy fixed-radius preset scan under TD-20 linear floor naming."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon floor linear chainscan <rooms> [stepRadius] - command-time chained controller discovery for long authored floors."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon floor preset start <rooms> - start a preset-mode floor using the next nearby usable controllers."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon floor linear start <rooms> - alias for preset start under TD-20 linear floor naming."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon floor linear start <rooms> chained [stepRadius] - chained discovery and immediate preset-linear floor start."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon floor preset status - inspect preset-mode room ordering state."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon floor linear status - alias for preset floor status."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon hub preparepreset <rooms> - arm a preset floor from the hub flow."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon run presetstart <rooms> - alias for preset floor start."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon run linearstart <rooms> - alias for preset floor start under TD-20 linear naming."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon run linearstart <rooms> chained [stepRadius] - run-branch alias for chained linear floor start."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon structure preset scan - alias of floor preset scan for authored structures."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon rewards floorbonusdebug - inspect floor completion max EP reward mailbox state."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon rewards clearfloorbonuses - clear saved first-time floor max EP award state for testing."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon tensura sourcedebug - inspect storage, attributes, race/skill summaries, and safe reload-drift evidence."), false);
        source.sendSuccess(() -> Component.literal("/tensuradungeon enemy debug - inspect enemy pool registration and last room selection state."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int listScenarios(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("=== Scenario Tests ==="), false);
        source.sendSuccess(() -> Component.literal("safe_room"), false);
        return Command.SINGLE_SUCCESS;
    }

    static int scenarioSafeRoom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        List<Component> lines = new ArrayList<>();
        boolean overallPass = true;

        lines.add(Component.literal("=== Scenario Test: SAFE_ROOM ==="));
        if (controller == null) {
            lines.add(passLine("Setup", "FAIL - no controller near player/current command context"));
            lines.add(Component.literal("State Mutated: NO"));
            lines.add(Component.literal("Overall: FAIL"));
            for (Component line : lines) {
                source.sendSuccess(() -> line, false);
            }
            return 0;
        }

        boolean mutated = false;
        try {
            controller.setRoomProfile(RoomProfile.SAFE_ROOM);
            controller.setConfiguredObjectiveType(null);
            mutated = true;
            lines.add(passLine("Setup", "PASS"));
        } catch (Exception ex) {
            overallPass = false;
            lines.add(passLine("Setup", "FAIL - " + ex.getClass().getSimpleName()));
        }

        lines.add(passLine("Profile Applied", controller.getRoomProfile() == RoomProfile.SAFE_ROOM ? "PASS - SAFE_ROOM" : "FAIL - " + controller.getRoomProfile()));
        overallPass &= controller.getRoomProfile() == RoomProfile.SAFE_ROOM;
        lines.add(passLine("Budget Zero", controller.getFinalCalculatedRoomBudget(level) == 0 && controller.getFinalCalculatedMaxAlive(level) == 0 ? "PASS" : "FAIL - budget=" + controller.getFinalCalculatedRoomBudget(level) + ", maxAlive=" + controller.getFinalCalculatedMaxAlive(level)));
        overallPass &= controller.getFinalCalculatedRoomBudget(level) == 0 && controller.getFinalCalculatedMaxAlive(level) == 0;
        lines.add(passLine("Allows Combat False", !controller.doesProfileAllowCombat() ? "PASS" : "FAIL - combat still allowed"));
        overallPass &= !controller.doesProfileAllowCombat();
        lines.add(passLine("Completion Policy", "SAFE_AUTO_COMPLETE".equals(controller.getProfileCompletionPolicy()) ? "PASS - SAFE_AUTO_COMPLETE" : "FAIL - " + controller.getProfileCompletionPolicy()));
        overallPass &= "SAFE_AUTO_COMPLETE".equals(controller.getProfileCompletionPolicy());
        boolean suppressPass = "SAFE_ROOM_NO_COMBAT_OBJECTIVE".equals(controller.getAutoObjectiveRejectionSummary()) || "NONE".equals(controller.getAutoObjectiveRejectionSummary());
        lines.add(passLine("Combat Objective Suppressed", suppressPass ? "PASS" : "FAIL - " + controller.getAutoObjectiveRejectionSummary()));
        overallPass &= suppressPass;
        lines.add(passLine("No Spawn Expected", controller.getFinalCalculatedMaxAlive(level) == 0 ? "PASS" : "FAIL - maxAlive=" + controller.getFinalCalculatedMaxAlive(level)));
        lines.add(passLine("Safe Auto Completion", "SKIPPED - requires live activation"));
        lines.add(Component.literal("State Mutated: " + (mutated ? "YES - profile safe_room, objective auto" : "NO")));
        lines.add(Component.literal("Overall: " + (overallPass ? "PASS_WITH_SKIPPED" : "FAIL")));
        for (Component line : lines) {
            source.sendSuccess(() -> line, false);
        }
        return overallPass ? Command.SINGLE_SUCCESS : 0;
    }

    private static Component passLine(String label, String result) {
        return Component.literal(label + ": " + result);
    }
}
