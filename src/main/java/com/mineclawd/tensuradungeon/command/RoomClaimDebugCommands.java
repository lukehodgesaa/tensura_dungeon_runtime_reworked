package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

final class RoomClaimDebugCommands {
    private static final int DEBUG_RADIUS = 32;

    private RoomClaimDebugCommands() {
    }

    static int claimRoomMarkers(CommandContext<CommandSourceStack> context, boolean force) {
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

        int radius = IntegerArgumentType.getInteger(context, "radius");
        RoomControllerBlockEntity.MarkerClaimResult result = controller.claimMarkers(level, radius, force);
        RoomControllerBlockEntity.ClaimBoundsResolution boundsResolution = controller.getLastClaimBoundsResolution();
        source.sendSuccess(() -> Component.literal("Room marker claim completed."), false);
        source.sendSuccess(() -> Component.literal("Controller: " + formatPos(controller.getBlockPos())), false);
        source.sendSuccess(() -> Component.literal("Requested Radius: " + radius), false);
        source.sendSuccess(() -> Component.literal("Applied Radius: " + result.radius()), false);
        source.sendSuccess(() -> Component.literal("Claim Force Mode: " + force), false);
        source.sendSuccess(() -> Component.literal("Claim Mode: " + (result.boundsFiltered() ? "BOUNDS_FILTERED_CLAIMED_MARKERS" : "LEGACY_RADIUS_CLAIM")), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Reason: " + boundsResolution.selectedReason()), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Controller Count: " + boundsResolution.selectedControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Foreign Controllers: " + boundsResolution.selectedForeignControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Bounds Pair Rejects: foreignController=" + boundsResolution.rejectForeignControllerCount() + ", doesNotContainController=" + boundsResolution.rejectMissingControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Raw Nearby Marker Count: " + result.rawScanned()), false);
        source.sendSuccess(() -> Component.literal("Claimed Marker Count: " + result.totalClaimed()), false);
        source.sendSuccess(() -> Component.literal("Skipped Outside Bounds: " + result.skippedOutsideBounds()), false);
        source.sendSuccess(() -> Component.literal("Skipped Owned By Other: " + result.skippedOwnedByOther()), false);
        source.sendSuccess(() -> Component.literal("Stale Removed: " + result.staleRemoved()), false);
        source.sendSuccess(() -> Component.literal("Claim Summary: " + result.summary()), false);
        source.sendSuccess(() -> Component.literal("Claim Reason: " + result.reason()), false);
        if (!result.boundsFiltered()) {
            if ("NO_SINGLE_CONTROLLER_BOUNDS_PAIR".equals(boundsResolution.selectedReason())) {
                source.sendFailure(Component.literal("Claim refused: no safe single-controller bounds pair was found. Nearby adjacent-room bounds anchors were rejected."));
            } else {
                source.sendFailure(Component.literal("Could not resolve valid room bounds; used legacy radius marker claiming. Nearby cloned rooms may be overclaimed."));
            }
        }
        source.sendSuccess(() -> Component.literal("Claim Duration Ms: " + formatDecimal(result.durationMs() < 0.0D ? 0.0D : result.durationMs())), false);
        return result.totalClaimed() > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    static int claimPreview(CommandContext<CommandSourceStack> context, boolean force) {
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

        int radius = IntegerArgumentType.getInteger(context, "radius");
        RoomControllerBlockEntity.ClaimPreviewResult result = controller.previewClaimMarkers(level, radius, force);
        RoomControllerBlockEntity.ClaimBoundsResolution boundsResolution = result.boundsResolution();
        source.sendSuccess(() -> Component.literal("Room claim preview placed."), false);
        source.sendSuccess(() -> Component.literal("Controller: " + formatPos(controller.getBlockPos())), false);
        source.sendSuccess(() -> Component.literal("Claim Preview Radius: " + result.radius()), false);
        source.sendSuccess(() -> Component.literal("Force Mode: " + result.force()), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds: " + result.boundsFiltered()), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Reason: " + boundsResolution.selectedReason()), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Controller Count: " + boundsResolution.selectedControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Foreign Controllers: " + boundsResolution.selectedForeignControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Bounds Pair Rejects: foreignController=" + boundsResolution.rejectForeignControllerCount() + ", doesNotContainController=" + boundsResolution.rejectMissingControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Min: " + (result.selectedBounds() == null ? "NONE" : formatPos(result.selectedBounds().min()))), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Max: " + (result.selectedBounds() == null ? "NONE" : formatPos(result.selectedBounds().max()))), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Size: " + (result.selectedBounds() == null ? "NONE" : result.selectedBounds().getWidth() + " x " + result.selectedBounds().getHeight() + " x " + result.selectedBounds().getDepth())), false);
        source.sendSuccess(() -> Component.literal("Raw Nearby Marker Count: " + result.rawScanned()), false);
        source.sendSuccess(() -> Component.literal("Claimed Marker Count: " + result.totalClaimed()), false);
        source.sendSuccess(() -> Component.literal("Skipped Outside Bounds: " + result.skippedOutsideBounds()), false);
        source.sendSuccess(() -> Component.literal("Skipped Owned By Other: " + result.skippedOwnedByOther()), false);
        source.sendSuccess(() -> Component.literal("Preview Blocks Placed: " + result.previewBlocksPlaced()), false);
        source.sendSuccess(() -> Component.literal("Preview Placement Skipped: " + result.previewPlacementSkipped()), false);
        source.sendSuccess(() -> Component.literal("Bounds Edge Blocks Placed: " + result.edgeBlocksPlaced() + (result.edgePlacementCapped() ? " (CAPPED)" : "")), false);
        source.sendSuccess(() -> Component.literal("Claimed Marker Summary: " + result.claimedSummary()), false);
        source.sendSuccess(() -> Component.literal("Skipped Outside Bounds Summary: " + result.skippedOutsideSummary()), false);
        source.sendSuccess(() -> Component.literal("Skipped Owned By Other Summary: " + result.skippedOwnedByOtherSummary()), false);
        if (!result.boundsFiltered()) {
            if ("NO_SINGLE_CONTROLLER_BOUNDS_PAIR".equals(boundsResolution.selectedReason())) {
                source.sendFailure(Component.literal("No safe single-controller bounds pair was found; preview refused to avoid a cross-room bounds claim."));
            } else {
                source.sendFailure(Component.literal("Selected bounds was not found; preview is using legacy radius diagnostics only."));
            }
        }
        return result.previewBlocksPlaced() > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    static int debugRoomClaimBounds(CommandContext<CommandSourceStack> context) {
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

        int radius = IntegerArgumentType.getInteger(context, "radius");
        RoomControllerBlockEntity.ClaimBoundsResolution resolution = controller.resolveClaimBoundsForController(level, radius);
        source.sendSuccess(() -> Component.literal("Room Claim Bounds Debug"), false);
        source.sendSuccess(() -> Component.literal("Controller: " + formatPos(controller.getBlockPos())), false);
        source.sendSuccess(() -> Component.literal("Requested Radius: " + radius), false);
        source.sendSuccess(() -> Component.literal("Raw Bounds Anchors Found: " + resolution.nearbyBoundsAnchors().size()), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds: " + (resolution.selectedBounds() == null ? "NONE" : formatPos(resolution.selectedBounds().min()) + " -> " + formatPos(resolution.selectedBounds().max()))), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Reason: " + resolution.selectedReason()), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Controller Count: " + resolution.selectedControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Selected Bounds Foreign Controllers: " + resolution.selectedForeignControllerCount()), false);
        source.sendSuccess(() -> Component.literal("Bounds Pair Rejects: foreignController=" + resolution.rejectForeignControllerCount() + ", doesNotContainController=" + resolution.rejectMissingControllerCount()), false);
        for (int i = 0; i < Math.min(5, resolution.candidates().size()); i++) {
            RoomControllerBlockEntity.ClaimBoundsCandidate candidate = resolution.candidates().get(i);
            int index = i + 1;
            source.sendSuccess(() -> Component.literal(index + ". " + formatPos(candidate.bounds().min()) + " -> " + formatPos(candidate.bounds().max())
                + " | size=" + candidate.bounds().getWidth() + " x " + candidate.bounds().getHeight() + " x " + candidate.bounds().getDepth()
                + " | volume=" + candidate.volume()
                + " | containsController=" + candidate.containsController()
                + " | controllers=" + candidate.controllerCount()
                + " | foreignControllers=" + candidate.foreignControllerCount()
                + " | rejection=" + candidate.rejectionReason()
                + " | selected=" + candidate.selected()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    static int boundsPreview(CommandContext<CommandSourceStack> context) {
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
        if (!controller.placeBoundsPreview(level)) {
            source.sendFailure(Component.literal("No bounds preview could be placed. Check bounds anchors and open space."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bounds preview placed."), false);
        return Command.SINGLE_SUCCESS;
    }

    static int clearPreview(CommandContext<CommandSourceStack> context) {
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

        if (!controller.clearAllPreviews(level)) {
            source.sendFailure(Component.literal("No preview blocks were active."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("All room previews cleared."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception ex) {
            source.sendFailure(Component.literal("This command must be run by a player."));
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
            if (!level.getBlockState(pos).is(ModBlocks.ROOM_CONTROLLER.get())) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof RoomControllerBlockEntity controller)) {
                continue;
            }

            controller.scanMarkers(level);
            if (containing == null && controller.hasBoundsBox() && controller.isInsideBounds(center)) {
                containing = controller;
            }

            double distance = pos.distSqr(center);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = controller;
            }
        }
        return containing != null ? containing : nearest;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String formatDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
