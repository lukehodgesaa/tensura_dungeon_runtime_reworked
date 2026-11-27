package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.block.entity.GeneratedRoomBoundsWriter;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.StructureMarkerReadinessReport;
import com.mineclawd.tensuradungeon.block.entity.StructureMarkerReadinessValidator;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

final class GeneratedRoomBoundsDebugCommands {
    private static final int DEBUG_RADIUS = 32;

    private GeneratedRoomBoundsDebugCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("generatedbounds")
            .then(Commands.literal("set")
                .then(Commands.argument("minX", IntegerArgumentType.integer())
                    .then(Commands.argument("minY", IntegerArgumentType.integer())
                        .then(Commands.argument("minZ", IntegerArgumentType.integer())
                            .then(Commands.argument("maxX", IntegerArgumentType.integer())
                                .then(Commands.argument("maxY", IntegerArgumentType.integer())
                                    .then(Commands.argument("maxZ", IntegerArgumentType.integer())
                                        .executes(context -> writeGeneratedBounds(context, false, false)))))))))
            .then(Commands.literal("claim")
                .then(Commands.argument("minX", IntegerArgumentType.integer())
                    .then(Commands.argument("minY", IntegerArgumentType.integer())
                        .then(Commands.argument("minZ", IntegerArgumentType.integer())
                            .then(Commands.argument("maxX", IntegerArgumentType.integer())
                                .then(Commands.argument("maxY", IntegerArgumentType.integer())
                                    .then(Commands.argument("maxZ", IntegerArgumentType.integer())
                                        .then(Commands.argument("claimRadius", IntegerArgumentType.integer(1, 256))
                                            .executes(context -> writeGeneratedBounds(context, true, false))
                                            .then(Commands.literal("force")
                                                .executes(context -> writeGeneratedBounds(context, true, true)))))))))));
    }

    private static int writeGeneratedBounds(CommandContext<CommandSourceStack> context, boolean claimMarkers, boolean forceClaim) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RoomControllerBlockEntity controller = findNearestController(level, player.blockPosition());
        if (controller == null) {
            source.sendFailure(Component.literal("Generated Bounds Write: FAIL - no room_controller found within " + DEBUG_RADIUS + " blocks"));
            return 0;
        }

        RoomControllerBlockEntity.RoomBounds bounds = new RoomControllerBlockEntity.RoomBounds(
            new BlockPos(
                IntegerArgumentType.getInteger(context, "minX"),
                IntegerArgumentType.getInteger(context, "minY"),
                IntegerArgumentType.getInteger(context, "minZ")
            ),
            new BlockPos(
                IntegerArgumentType.getInteger(context, "maxX"),
                IntegerArgumentType.getInteger(context, "maxY"),
                IntegerArgumentType.getInteger(context, "maxZ")
            )
        );

        if (!claimMarkers) {
            GeneratedRoomBoundsWriter.Result result = controller.writeGeneratedRoomBounds(level, bounds);
            emitWriteResult(source, controller, result, false);
            if (!result.success()) {
                return 0;
            }

            StructureMarkerReadinessReport readiness = StructureMarkerReadinessValidator.analyze(level, controller);
            emitReadiness(source, readiness);
            source.sendSuccess(() -> Component.literal("Next: run /tensuradungeon room generatedbounds claim <minX> <minY> <minZ> <maxX> <maxY> <maxZ> <claimRadius> to claim markers inside these bounds."), false);
            return Command.SINGLE_SUCCESS;
        }

        int claimRadius = IntegerArgumentType.getInteger(context, "claimRadius");
        GeneratedRoomBoundsWriter.ClaimResult result = GeneratedRoomBoundsWriter.writeGeneratedBoundsAndClaim(level, controller, bounds, claimRadius, forceClaim);
        emitWriteResult(source, controller, result.boundsResult(), true);
        if (!result.success()) {
            return 0;
        }

        RoomControllerBlockEntity.MarkerClaimResult claim = result.claimResult();
        source.sendSuccess(() -> Component.literal("Generated Bounds Claim: PASS"), false);
        source.sendSuccess(() -> Component.literal("Claim Force Mode: " + forceClaim), false);
        source.sendSuccess(() -> Component.literal("Claim Radius: " + claim.radius()), false);
        source.sendSuccess(() -> Component.literal("Claimed Marker Count: " + claim.totalClaimed()), false);
        source.sendSuccess(() -> Component.literal("Skipped Outside Bounds: " + claim.skippedOutsideBounds()), false);
        source.sendSuccess(() -> Component.literal("Skipped Owned By Other: " + claim.skippedOwnedByOther()), false);
        source.sendSuccess(() -> Component.literal("Claim Reason: " + claim.reason()), false);
        source.sendSuccess(() -> Component.literal("Claim Summary: " + claim.summary()), false);
        emitReadiness(source, result.readinessReport());
        return Command.SINGLE_SUCCESS;
    }

    private static void emitWriteResult(CommandSourceStack source, RoomControllerBlockEntity controller, GeneratedRoomBoundsWriter.Result result, boolean claimMode) {
        if (result == null) {
            source.sendFailure(Component.literal("Generated Bounds Write: FAIL - NULL_RESULT"));
            return;
        }
        if (!result.success()) {
            source.sendFailure(Component.literal("Generated Bounds Write: FAIL - " + result.reason()));
            source.sendFailure(Component.literal("Controller: " + formatPos(controller.getBlockPos())));
            source.sendFailure(Component.literal("Foreign Controllers: " + result.foreignControllerCount()));
            if (result.bounds() != null) {
                source.sendFailure(Component.literal("Rejected Bounds: " + formatBounds(result.bounds())));
            }
            return;
        }

        source.sendSuccess(() -> Component.literal("Generated Bounds Write: PASS"), false);
        source.sendSuccess(() -> Component.literal("Mode: " + (claimMode ? "WRITE_AND_CLAIM" : "WRITE_ONLY")), false);
        source.sendSuccess(() -> Component.literal("Reason: " + result.reason()), false);
        source.sendSuccess(() -> Component.literal("Controller: " + formatPos(controller.getBlockPos())), false);
        source.sendSuccess(() -> Component.literal("Saved Bounds: " + formatBounds(result.bounds())), false);
        source.sendSuccess(() -> Component.literal("Foreign Controllers: " + result.foreignControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Marker Cache Refresh: FORCED_SCAN"), false);
    }

    private static void emitReadiness(CommandSourceStack source, StructureMarkerReadinessReport report) {
        if (report == null) {
            source.sendFailure(Component.literal("Structure Marker Readiness: UNKNOWN"));
            return;
        }
        source.sendSuccess(() -> Component.literal("Structure Marker Readiness: " + report.readinessLabel()), false);
        source.sendSuccess(() -> Component.literal("Jigsaw Template Readiness: " + report.jigsawTemplateReadinessLabel()), false);
        source.sendSuccess(() -> Component.literal("Missing Required Markers: " + report.missingSummary()), false);
        source.sendSuccess(() -> Component.literal("Warnings: " + report.warningsSummary()), false);
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Generated Bounds Write: FAIL - player context required"));
            return null;
        }
    }

    private static RoomControllerBlockEntity findNearestController(ServerLevel level, BlockPos center) {
        RoomControllerBlockEntity nearest = null;
        RoomControllerBlockEntity containing = null;
        double nearestDistance = Double.MAX_VALUE;

        BlockPos min = center.offset(-DEBUG_RADIUS, -DEBUG_RADIUS, -DEBUG_RADIUS);
        BlockPos max = center.offset(DEBUG_RADIUS, DEBUG_RADIUS, DEBUG_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockPos checkedPos = pos.immutable();
            if (!level.getBlockState(checkedPos).is(ModBlocks.ROOM_CONTROLLER.get())) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(checkedPos);
            if (!(blockEntity instanceof RoomControllerBlockEntity controller)) {
                continue;
            }

            controller.scanMarkers(level);
            if (containing == null && controller.hasBoundsBox() && controller.isInsideBounds(center)) {
                containing = controller;
            }

            double distance = checkedPos.distSqr(center);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = controller;
            }
        }
        return containing != null ? containing : nearest;
    }

    private static String formatBounds(RoomControllerBlockEntity.RoomBounds bounds) {
        return formatPos(bounds.min()) + " -> " + formatPos(bounds.max()) + " (" + bounds.getWidth() + "x" + bounds.getHeight() + "x" + bounds.getDepth() + ")";
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
