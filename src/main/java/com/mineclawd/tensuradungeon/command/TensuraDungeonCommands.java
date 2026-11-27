package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.RoomProtectionView;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class TensuraDungeonCommands {
    static final int DEBUG_RADIUS = 32;

    private TensuraDungeonCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tensuradungeon")
            .requires(source -> source.hasPermission(2));
        RoomCommandRegistrar.register(root);
        LiteralArgumentBuilder<CommandSourceStack> generatedFloor = GeneratedFloorCommandRegistrar.register();
        RunCommandRegistrar.register(root, generatedFloor);
        RewardCommandRegistrar.register(root);
        CoreSatchelCommandRegistrar.register(root);
        DungeonShopCommandRegistrar.register(root);
        DungeonBankCommandRegistrar.register(root);
        KurobeForgeCommandRegistrar.register(root);
        CurrencyCommandRegistrar.register(root);
        ScenarioCommandRegistrar.register(root);
        DiagnosticsCommandRegistrar.register(root);
        CosmeticDebugCommandRegistrar.register(root);
        CompositeAuthoringCommandRegistrar.register(root);
        OverworldRiftCommandRegistrar.register(root);
        event.getDispatcher().register(root);
    }

    static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception ex) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return null;
        }
    }

    static RoomControllerBlockEntity findNearestController(ServerLevel level, BlockPos center) {
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
            RoomProtectionView protection = controller.protectionView(level);
            if (containing == null && protection.hasBounds() && protection.bounds().contains(center)) {
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

    static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    static String formatNullablePos(BlockPos pos) {
        return pos == null ? "NONE" : formatPos(pos);
    }

    static String formatDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
