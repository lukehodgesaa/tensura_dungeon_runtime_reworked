package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.GeneratedRoomBoundsWriter;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.StructureMarkerReadinessReport;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

final class GeneratedLinearFloorDebugCommands {
    private static final int ORIGIN_SCAN_RADIUS = 128;
    private static final int DEFAULT_ROOM_SPACING = 128;
    private static final int DEFAULT_CLAIM_RADIUS = 120;
    private static final int MAX_ROOMS = 16;
    private static final int FLOOR_START_HINT_STEP_RADIUS = 256;

    private static final List<TemplateRequirement> DEFAULT_ROOM_TEMPLATES = List.of(
        new TemplateRequirement("dwarven_corridor01", "rooms/linear/dwarven_corridor01"),
        new TemplateRequirement("dwarven_corridor03", "rooms/linear/dwarven_corridor03"),
        new TemplateRequirement("dwarven_corridor04", "rooms/linear/dwarven_corridor04"),
        new TemplateRequirement("waterfall_corridor01", "rooms/linear/waterfall_corridor01"),
        new TemplateRequirement("corridor_cave_01", "rooms/linear/corridor_cave_01"),
        new TemplateRequirement("corridor_cave_02", "rooms/linear/corridor_cave_02"),
        new TemplateRequirement("corridor_magicbrick01", "rooms/linear/corridor_magicbrick01"),
        new TemplateRequirement("grand_corridor01", "rooms/linear/grand_corridor01"),
        new TemplateRequirement("grand_corridor02", "rooms/linear/grand_corridor02"),
        new TemplateRequirement("grand_corridor_ruins_01", "rooms/linear/grand_corridor_ruins_01"),
        new TemplateRequirement("overgrown_corridor_corner_01", "rooms/linear/overgrown_corridor_corner_01"),
        new TemplateRequirement("rubble_corridor_corner_01", "rooms/linear/rubble_corridor_corner_01"),
        new TemplateRequirement("grand_corner01", "rooms/linear/grand_corner01"),
        new TemplateRequirement("elven_stairs01", "rooms/linear/elven_stairs01"),
        new TemplateRequirement("dwarven_stairs01", "rooms/linear/dwarven_stairs01"),
        new TemplateRequirement("grand_corridor_stairs01", "rooms/linear/grand_corridor_stairs01"),
        new TemplateRequirement("corridor_deadend_01", "rooms/linear/corridor_deadend_01"),
        new TemplateRequirement("colosseum_combat_room_d", "rooms/linear/colosseum_combat_room_d"),
        new TemplateRequirement("colosseum_combat_room_a", "rooms/linear/colosseum_combat_room_a"),
        new TemplateRequirement("combat_room_e", "rooms/linear/combat_room_e"),
        new TemplateRequirement("descend_combat_structure_c", "rooms/linear/descend_combat_structure_c"),
        new TemplateRequirement("dungeon_double_storage01", "rooms/linear/dungeon_double_storage01"),
        new TemplateRequirement("rubble_combatzone_f", "rooms/linear/rubble_combatzone_f"),
        new TemplateRequirement("combatzone_i", "rooms/linear/combatzone_i"),
        new TemplateRequirement("ruins_entrance01", "rooms/linear/ruins_entrance01"),
        new TemplateRequirement("standard_combat_a", "rooms/linear/standard_combat_a"),
        new TemplateRequirement("standard_combat_b", "rooms/linear/standard_combat_b"),
        new TemplateRequirement("gothic_standard_combat_b", "rooms/linear/gothic_standard_combat_b"),
        new TemplateRequirement("city_threeway_01", "rooms/linear/city_threeway_01"),
        new TemplateRequirement("dungeon_three_way01", "rooms/linear/dungeon_three_way01")
    );

    private GeneratedLinearFloorDebugCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("generated")
            .then(Commands.literal("structurehelp").executes(GeneratedLinearFloorDebugCommands::structureHelp))
            .then(Commands.literal("origin").executes(GeneratedLinearFloorDebugCommands::scanOrigin))
            .then(Commands.literal("verifytemplates").executes(GeneratedLinearFloorDebugCommands::verifyTemplates))
            .then(GeneratedLinearConnectorPlacementCommands.registerStatusCommand())
            .then(GeneratedLinearConnectorPlacementCommands.registerConnectorsCommand())
            .then(GeneratedLinearConnectorPlacementCommands.registerGateCommand())
            .then(GeneratedLinearConnectorPlacementCommands.registerClearCommand())
            .then(GeneratedLinearConnectorPlacementCommands.registerCleanupCommand())
            .then(GeneratedFloorPlanningDebugCommands.registerMetadataCommand())
            .then(GeneratedFloorPlanningDebugCommands.registerComposeCommand())
            .then(GeneratedFloorPlanningDebugCommands.registerSolveCommand())
            .then(Commands.literal("plan")
                .then(Commands.argument("rooms", IntegerArgumentType.integer(1, MAX_ROOMS))
                    .executes(context -> GeneratedLinearConnectorPlacementCommands.planLinearFloor(context, DEFAULT_ROOM_SPACING))
                    .then(Commands.argument("spacing", IntegerArgumentType.integer(16, 512))
                        .executes(context -> GeneratedLinearConnectorPlacementCommands.planLinearFloor(context, IntegerArgumentType.getInteger(context, "spacing"))))))
            .then(Commands.literal("start")
                .then(Commands.argument("rooms", IntegerArgumentType.integer(1, MAX_ROOMS))
                    .executes(GeneratedLinearConnectorPlacementCommands::startGeneratedFloorFromStoredPlacement)))
            .then(Commands.literal("place")
                .then(Commands.argument("rooms", IntegerArgumentType.integer(1, MAX_ROOMS))
                    .executes(context -> GeneratedLinearConnectorPlacementCommands.placeLinearFloor(context, DEFAULT_ROOM_SPACING, DEFAULT_CLAIM_RADIUS))
                    .then(Commands.argument("spacing", IntegerArgumentType.integer(16, 512))
                        .executes(context -> GeneratedLinearConnectorPlacementCommands.placeLinearFloor(context, IntegerArgumentType.getInteger(context, "spacing"), DEFAULT_CLAIM_RADIUS))
                        .then(Commands.argument("claimRadius", IntegerArgumentType.integer(1, 256))
                            .executes(context -> GeneratedLinearConnectorPlacementCommands.placeLinearFloor(
                                context,
                                IntegerArgumentType.getInteger(context, "spacing"),
                                IntegerArgumentType.getInteger(context, "claimRadius")
                            ))))))
            .then(Commands.literal("placeandstart")
                .then(Commands.argument("rooms", IntegerArgumentType.integer(1, MAX_ROOMS))
                    .executes(context -> GeneratedLinearConnectorPlacementCommands.placeAndStartLinearFloor(context, DEFAULT_ROOM_SPACING, DEFAULT_CLAIM_RADIUS))
                    .then(Commands.argument("spacing", IntegerArgumentType.integer(16, 512))
                        .executes(context -> GeneratedLinearConnectorPlacementCommands.placeAndStartLinearFloor(context, IntegerArgumentType.getInteger(context, "spacing"), DEFAULT_CLAIM_RADIUS))
                        .then(Commands.argument("claimRadius", IntegerArgumentType.integer(1, 256))
                            .executes(context -> GeneratedLinearConnectorPlacementCommands.placeAndStartLinearFloor(
                                context,
                                IntegerArgumentType.getInteger(context, "spacing"),
                                IntegerArgumentType.getInteger(context, "claimRadius")
                            ))))));
    }

    private static int structureHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Generated Linear Floor Structure Help"), false);
        source.sendSuccess(() -> Component.literal("Structure templates required: YES"), false);
        source.sendSuccess(() -> Component.literal("Save templates as vanilla .nbt structure files under:"), false);
        source.sendSuccess(() -> Component.literal("src/main/resources/data/" + TensuraDungeonRuntime.MOD_ID + "/structure/rooms/linear/<template>.nbt"), false);
        source.sendSuccess(() -> Component.literal("Initial templates expected:"), false);
        for (TemplateRequirement template : DEFAULT_ROOM_TEMPLATES) {
            source.sendSuccess(() -> Component.literal("- " + template.resourceId() + " -> " + template.resourcePath() + " | " + template.metadataSummary()), false);
        }
        source.sendSuccess(() -> Component.literal("Each room template must contain exactly one room_controller, at least two bounds_anchor blocks, an exit or floor_entry marker, combat spawns, barrier channel anchors, and one mob_reset_anchor for combat rooms."), false);
        source.sendSuccess(() -> Component.literal("Connector-aware placement prefers floor_connector_anchor and connector_exit blocks. Add floor_connector_anchor at entrances/generic links and connector_exit at intended outgoing links. floor_entry_anchor and exit_anchor remain fallback connectors."), false);
        source.sendSuccess(() -> Component.literal("Use /tensuradungeon floor generated verifytemplates to confirm the runtime can load every registered generated pool template."), false);
        source.sendSuccess(() -> Component.literal("Use /tensuradungeon floor generated origin to verify the hub-side floor_build_origin marker."), false);
        source.sendSuccess(() -> Component.literal("Use /tensuradungeon floor generated status for restart-aware recovery guidance across stored placement, gate state, and active run state."), false);
        source.sendSuccess(() -> Component.literal("Use /tensuradungeon floor generated gate debug to confirm whether hub-gate ownership came from tracked state or a bounded runtime-barrier scan."), false);
        source.sendSuccess(() -> Component.literal("Use /tensuradungeon floor generated cleanup status to watch budgeted generated-floor deletion before starting another run."), false);
        source.sendSuccess(() -> Component.literal("Use /tensuradungeon floor generated connectors <template> to inspect template connector markers."), false);
        source.sendSuccess(() -> Component.literal("Use /tensuradungeon floor generated plan/place <rooms> [spacing] [claimRadius] for seeded-random connector-snapped placement."), false);
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int scanOrigin(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos origin = findNearestFloorBuildOrigin(level, player.blockPosition(), ORIGIN_SCAN_RADIUS);
        source.sendSuccess(() -> Component.literal("Generated Linear Floor Origin Scan"), false);
        source.sendSuccess(() -> Component.literal("Origin Scan Radius: " + ORIGIN_SCAN_RADIUS), false);
        source.sendSuccess(() -> Component.literal("Player Position: " + formatPos(player.blockPosition())), false);
        source.sendSuccess(() -> Component.literal("Player Facing: " + player.getDirection()), false);
        if (origin == null) {
            source.sendFailure(Component.literal("Floor Build Origin: MISSING"));
            source.sendFailure(Component.literal("Place a floor_build_origin marker near the hub gate / generated-floor start."));
            source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Floor Build Origin: " + formatPos(origin)), false);
        source.sendSuccess(() -> Component.literal("Distance Sqr: " + origin.distSqr(player.blockPosition())), false);
        source.sendSuccess(() -> Component.literal("Generated Linear Origin Readiness: PASS"), false);
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int verifyTemplates(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        boolean allPresent = true;
        source.sendSuccess(() -> Component.literal("Generated Linear Template Verification"), false);
        source.sendSuccess(() -> Component.literal("Runtime Structure Resource Root: data/" + TensuraDungeonRuntime.MOD_ID + "/structure/"), false);
        for (TemplateRequirement template : DEFAULT_ROOM_TEMPLATES) {
            StructureTemplate loaded = loadTemplate(level, template);
            if (loaded == null) {
                allPresent = false;
                source.sendFailure(Component.literal("Template MISSING: " + template.resourceId() + " | runtime resource: " + template.runtimeResourcePath()));
                continue;
            }
            Vec3i size = loaded.getSize();
            source.sendSuccess(() -> Component.literal("Template PASS: " + template.resourceId() + " | runtime resource: " + template.runtimeResourcePath() + " | size=" + size.getX() + "x" + size.getY() + "x" + size.getZ()), false);
        }
        boolean verificationPass = allPresent;
        source.sendSuccess(() -> Component.literal("Generated Linear Template Verification: " + (verificationPass ? "PASS" : "FAIL")), false);
        source.sendSuccess(() -> Component.literal("State Mutated: NO"), false);
        return verificationPass ? Command.SINGLE_SUCCESS : 0;
    }

    private static StructureTemplate loadTemplate(ServerLevel level, TemplateRequirement template) {
        Optional<StructureTemplate> loaded = level.getStructureManager().get(template.resourceLocation());
        return loaded.orElse(null);
    }

    private static BlockPos findNearestFloorBuildOrigin(ServerLevel level, BlockPos center, int radius) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        BlockPos min = center.offset(-radius, -radius, -radius);
        BlockPos max = center.offset(radius, radius, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockPos checkedPos = pos.immutable();
            if (!level.getBlockState(checkedPos).is(ModBlocks.FLOOR_BUILD_ORIGIN.get())) {
                continue;
            }
            double distance = checkedPos.distSqr(center);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = checkedPos;
            }
        }
        return nearest;
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Generated linear floor command requires a player context."));
            return null;
        }
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String formatBounds(RoomControllerBlockEntity.RoomBounds bounds) {
        return formatPos(bounds.min()) + " -> " + formatPos(bounds.max())
            + " (" + bounds.getWidth() + "x" + bounds.getHeight() + "x" + bounds.getDepth() + ")";
    }

    private static String formatControllerList(List<BlockPos> controllers) {
        if (controllers.isEmpty()) {
            return "NONE";
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < controllers.size(); i++) {
            parts.add((i + 1) + ":" + formatPos(controllers.get(i)));
        }
        return String.join(" | ", parts);
    }

    private record TemplateRequirement(String label, String resourcePath, String archetype, boolean turnCapable, boolean terminalCapable) {
        TemplateRequirement(String label, String resourcePath) {
            this(label, resourcePath, "corridor", false, true);
        }

        ResourceLocation resourceLocation() {
            return ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, resourcePath);
        }

        String resourceId() {
            return TensuraDungeonRuntime.MOD_ID + ":" + resourcePath;
        }

        String runtimeResourcePath() {
            return "data/" + TensuraDungeonRuntime.MOD_ID + "/structure/" + resourcePath + ".nbt";
        }

        String metadataSummary() {
            return "archetype=" + archetype + ", turnCapable=" + turnCapable + ", terminalCapable=" + terminalCapable;
        }
    }

    private record LoadedPlannedRoom(int index, TemplateRequirement template, StructureTemplate structureTemplate, Vec3i size, BlockPos placementOrigin, RoomControllerBlockEntity.RoomBounds bounds) {
    }
}
