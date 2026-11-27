package com.mineclawd.tensurafogbarriers.command;

import com.mineclawd.tensurafogbarriers.TensuraFogBarriers;
import com.mineclawd.tensurafogbarriers.data.FogAnimationStyle;
import com.mineclawd.tensurafogbarriers.data.FogBarrierPanel;
import com.mineclawd.tensurafogbarriers.data.FogVariant;
import com.mineclawd.tensurafogbarriers.service.FogBarrierService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = TensuraFogBarriers.MOD_ID)
public final class FogBarrierCommands {
    private static final String[] VARIANT_NAMES = Arrays.stream(FogVariant.values())
        .map(FogVariant::getSerializedName)
        .toArray(String[]::new);

    private static final String[] STYLE_NAMES = Arrays.stream(FogAnimationStyle.values())
        .map(FogAnimationStyle::getSerializedName)
        .toArray(String[]::new);

    private FogBarrierCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("fogbarrier")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                    .then(Commands.argument("first", BlockPosArgument.blockPos())
                        .then(Commands.argument("second", BlockPosArgument.blockPos())
                            .executes(context -> create(context, "golden", FogVariant.GOLD))
                            .then(Commands.argument("type", StringArgumentType.word())
                                .executes(context -> create(context, StringArgumentType.getString(context, "type"), null))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(VARIANT_NAMES, builder))
                                    .executes(context -> createWithVariant(context, StringArgumentType.getString(context, "type"))))))))
                .then(Commands.literal("createhere")
                    .then(Commands.argument("width", IntegerArgumentType.integer(1, 128))
                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 64))
                            .executes(context -> createHere(context, "golden", FogVariant.GOLD))
                            .then(Commands.argument("type", StringArgumentType.word())
                                .executes(context -> createHere(context, StringArgumentType.getString(context, "type"), null))
                                .then(Commands.argument("variant", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(VARIANT_NAMES, builder))
                                    .executes(context -> createHereWithVariant(context, StringArgumentType.getString(context, "type"))))))))
                .then(Commands.literal("color")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .then(Commands.argument("variant", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(VARIANT_NAMES, builder))
                            .executes(FogBarrierCommands::recolor))))
                .then(Commands.literal("style")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .then(Commands.argument("style", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(STYLE_NAMES, builder))
                            .executes(FogBarrierCommands::restyle))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(FogBarrierCommands::remove)))
                .then(Commands.literal("list").executes(FogBarrierCommands::list))
                .then(Commands.literal("clear").executes(FogBarrierCommands::clear))
                .then(Commands.literal("resync").executes(FogBarrierCommands::resync))
        );
    }

    private static int createWithVariant(CommandContext<CommandSourceStack> context, String type) throws CommandSyntaxException {
        Optional<FogVariant> variant = requestedVariant(context);
        if (variant.isEmpty()) return invalidVariant(context.getSource());
        return create(context, type, variant.get());
    }

    private static int create(CommandContext<CommandSourceStack> context, String type, FogVariant requested) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos firstPos = BlockPosArgument.getLoadedBlockPos(context, "first");
        BlockPos secondPos = BlockPosArgument.getLoadedBlockPos(context, "second");
        Vec3[] corners = corners(firstPos, secondPos);
        if (corners == null) {
            source.sendFailure(Component.literal("Positions must share X or Z and define a vertical plane."));
            return 0;
        }
        try {
            FogVariant variant = requested == null ? FogVariant.fromType(type) : requested;
            FogBarrierPanel panel = FogBarrierService.get(level.getServer()).create(level, corners[0], corners[1], type, variant);
            source.sendSuccess(() -> createdMessage(panel), true);
            return panel.id();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal("Could not create fog barrier: " + exception.getMessage()));
            return 0;
        }
    }

    private static int createHereWithVariant(CommandContext<CommandSourceStack> context, String type) {
        Optional<FogVariant> variant = requestedVariant(context);
        if (variant.isEmpty()) return invalidVariant(context.getSource());
        return createHere(context, type, variant.get());
    }

    private static int createHere(CommandContext<CommandSourceStack> context, String type, FogVariant requested) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("createhere requires a player."));
            return 0;
        }
        int width = IntegerArgumentType.getInteger(context, "width");
        int height = IntegerArgumentType.getInteger(context, "height");
        Direction direction = player.getDirection();
        BlockPos center = player.blockPosition().relative(direction, 2);
        int low = Math.floorDiv(width - 1, 2);
        int high = width - low;
        Vec3 first;
        Vec3 second;
        if (direction.getAxis() == Direction.Axis.X) {
            double plane = center.getX() + 0.5D;
            first = new Vec3(plane, center.getY(), center.getZ() - low);
            second = new Vec3(plane, center.getY() + height, center.getZ() + high);
        } else {
            double plane = center.getZ() + 0.5D;
            first = new Vec3(center.getX() - low, center.getY(), plane);
            second = new Vec3(center.getX() + high, center.getY() + height, plane);
        }
        try {
            FogVariant variant = requested == null ? FogVariant.fromType(type) : requested;
            FogBarrierPanel panel = FogBarrierService.get(source.getServer()).create(source.getLevel(), first, second, type, variant);
            source.sendSuccess(() -> createdMessage(panel), true);
            return panel.id();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal("Could not create fog barrier: " + exception.getMessage()));
            return 0;
        }
    }

    private static int recolor(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        Optional<FogVariant> variant = requestedVariant(context);
        if (variant.isEmpty()) return invalidVariant(context.getSource());
        boolean changed = FogBarrierService.get(context.getSource().getServer()).recolor(id, variant.get());
        if (!changed) {
            context.getSource().sendFailure(Component.literal("No fog barrier exists with ID " + id + "."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
            "Changed fog barrier #" + id + " to " + variant.get().displayName() + "."
        ), true);
        return 1;
    }

    private static int restyle(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        Optional<FogAnimationStyle> style = requestedStyle(context);
        if (style.isEmpty()) return invalidStyle(context.getSource());
        boolean changed = FogBarrierService.get(context.getSource().getServer()).restyle(id, style.get());
        if (!changed) {
            context.getSource().sendFailure(Component.literal("No fog barrier exists with ID " + id + "."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
            "Changed fog barrier #" + id + " animation to " + style.get().displayName() + "."
        ), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        boolean removed = FogBarrierService.get(context.getSource().getServer()).remove(id);
        if (!removed) {
            context.getSource().sendFailure(Component.literal("No fog barrier exists with ID " + id + "."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Removed fog barrier " + id + "."), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        List<FogBarrierPanel> panels = FogBarrierService.get(context.getSource().getServer()).panels(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal("Fog barriers in this dimension: " + panels.size()), false);
        for (FogBarrierPanel panel : panels) {
            context.getSource().sendSuccess(() -> Component.literal(
                "#" + panel.id() + " " + panel.type() + " color=" + panel.variant().getSerializedName() + " axis=" + panel.axis()
                    + " style=" + panel.style().getSerializedName() + " width=" + format(panel.width()) + " height=" + format(panel.height())
                    + " plane=" + format(panel.plane())
            ), false);
        }
        return panels.size();
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        int removed = FogBarrierService.get(context.getSource().getServer()).clear(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.literal("Cleared " + removed + " fog barrier(s) in this dimension."), true);
        return removed;
    }

    private static int resync(CommandContext<CommandSourceStack> context) {
        int synced = 0;
        for (ServerPlayer player : context.getSource().getLevel().players()) {
            FogBarrierService.get(context.getSource().getServer()).syncPlayer(player);
            synced++;
        }
        int count = synced;
        context.getSource().sendSuccess(() -> Component.literal("Resynchronized fog barriers to " + count + " player(s)."), false);
        return synced;
    }

    private static Optional<FogVariant> requestedVariant(CommandContext<CommandSourceStack> context) {
        return FogVariant.byName(StringArgumentType.getString(context, "variant"));
    }

    private static Optional<FogAnimationStyle> requestedStyle(CommandContext<CommandSourceStack> context) {
        return FogAnimationStyle.byName(StringArgumentType.getString(context, "style"));
    }

    private static int invalidStyle(CommandSourceStack source) {
        source.sendFailure(Component.literal("Unknown animation. Choose: " + String.join(", ", STYLE_NAMES) + "."));
        return 0;
    }

    private static int invalidVariant(CommandSourceStack source) {
        source.sendFailure(Component.literal("Unknown color. Choose: " + String.join(", ", VARIANT_NAMES) + "."));
        return 0;
    }

    private static Vec3[] corners(BlockPos first, BlockPos second) {
        int minY = Math.min(first.getY(), second.getY());
        int maxY = Math.max(first.getY(), second.getY()) + 1;
        if (first.getX() == second.getX() && first.getZ() != second.getZ()) {
            double plane = first.getX() + 0.5D;
            return new Vec3[] {
                new Vec3(plane, minY, Math.min(first.getZ(), second.getZ())),
                new Vec3(plane, maxY, Math.max(first.getZ(), second.getZ()) + 1.0D)
            };
        }
        if (first.getZ() == second.getZ() && first.getX() != second.getX()) {
            double plane = first.getZ() + 0.5D;
            return new Vec3[] {
                new Vec3(Math.min(first.getX(), second.getX()), minY, plane),
                new Vec3(Math.max(first.getX(), second.getX()) + 1.0D, maxY, plane)
            };
        }
        return null;
    }

    private static Component createdMessage(FogBarrierPanel panel) {
        return Component.literal("Created " + panel.variant().displayName() + " fog barrier #" + panel.id()
            + " (" + format(panel.width()) + " x " + format(panel.height()) + ").");
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
