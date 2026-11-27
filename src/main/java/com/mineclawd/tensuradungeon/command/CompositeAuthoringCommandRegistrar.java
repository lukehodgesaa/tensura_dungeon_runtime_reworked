package com.mineclawd.tensuradungeon.command;

import com.mineclawd.tensuradungeon.runtime.generation.composite.CompiledCompositeRoom;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeFinalRole;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeRoomRuntimeRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeTemplateDescriptor;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

final class CompositeAuthoringCommandRegistrar {
    private static final Pattern SAFE_THEME = Pattern.compile("[a-z0-9_.-]+");

    private CompositeAuthoringCommandRegistrar() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> composite = Commands.literal("composite");
        composite.then(Commands.literal("inspect")
            .then(Commands.argument("theme", StringArgumentType.word())
                .then(Commands.argument("topology", StringArgumentType.word())
                    .suggests(CompositeAuthoringCommandRegistrar::suggestTopologies)
                    .executes(context -> inspect(context, 0))
                    .then(Commands.argument("variant", IntegerArgumentType.integer(0, 11))
                        .executes(context -> inspect(
                            context,
                            IntegerArgumentType.getInteger(context, "variant")
                        ))))));
        composite.then(Commands.literal("validate")
            .then(Commands.argument("theme", StringArgumentType.word())
                .then(Commands.argument("topology", StringArgumentType.word())
                    .suggests(CompositeAuthoringCommandRegistrar::suggestTopologies)
                    .executes(CompositeAuthoringCommandRegistrar::validate))));
        root.then(composite);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestTopologies(
        CommandContext<CommandSourceStack> context,
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        builder.suggest("linear");
        builder.suggest("intersection3");
        builder.suggest("intersection4");
        return builder.buildFuture();
    }

    private static int inspect(CommandContext<CommandSourceStack> context, int variant) {
        CommandSourceStack source = context.getSource();
        String theme = normalizedTheme(context);
        Topology topology = topology(context);
        if (theme == null || topology == null) return 0;
        if (variant >= topology.candidateCount()) {
            source.sendFailure(Component.literal(
                topology.label() + " variants are 0-" + (topology.candidateCount() - 1)
            ));
            return 0;
        }
        String id = topology.id(theme, variant);
        CompositeRoomRuntimeRegistry.Resolution resolution =
            CompositeRoomRuntimeRegistry.resolve(source.getLevel(), id);
        if (!resolution.success() || resolution.room() == null) {
            source.sendFailure(Component.literal(
                "Composite " + id + " failed: " + resolution.failureSummary()
            ));
            return 0;
        }
        CompiledCompositeRoom room = resolution.room();
        Map<CompositeFinalRole, Integer> roles = new EnumMap<>(CompositeFinalRole.class);
        room.assignments().forEach(assignment -> roles.merge(assignment.role(), 1, Integer::sum));
        source.sendSuccess(() -> Component.literal(
            "Composite READY id=" + id
                + " size=" + room.width() + "x" + room.height() + "x" + room.depth()
                + " volume=" + room.volume()
                + " cells=" + room.cells().size()
                + " segments=" + room.sourceSegments().size()
                + " exits=" + room.externalConnectors().size()
        ), false);
        source.sendSuccess(() -> Component.literal(
            "Sources: " + String.join(" -> ", room.sourceSegments())
        ), false);
        source.sendSuccess(() -> Component.literal(
            "Roles: " + roles + " | Connectors: " + room.externalConnectors()
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String theme = normalizedTheme(context);
        Topology topology = topology(context);
        if (theme == null || topology == null) return 0;
        int ready = 0;
        String firstFailure = null;
        for (int variant = 0; variant < topology.candidateCount(); variant++) {
            String id = topology.id(theme, variant);
            CompositeRoomRuntimeRegistry.Resolution resolution =
                CompositeRoomRuntimeRegistry.resolve(source.getLevel(), id);
            if (resolution.success()) {
                ready++;
            } else if (firstFailure == null) {
                firstFailure = resolution.failureSummary();
            }
        }
        int finalReady = ready;
        source.sendSuccess(() -> Component.literal(
            "Composite kit theme=" + theme
                + " topology=" + topology.label()
                + " ready=" + finalReady + "/" + topology.candidateCount()
        ), false);
        if (ready != topology.candidateCount()) {
            source.sendFailure(Component.literal("First failure: " + firstFailure));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String normalizedTheme(CommandContext<CommandSourceStack> context) {
        String theme = StringArgumentType.getString(context, "theme").toLowerCase(Locale.ROOT);
        if (!SAFE_THEME.matcher(theme).matches()) {
            context.getSource().sendFailure(Component.literal("Invalid composite theme: " + theme));
            return null;
        }
        return theme;
    }

    private static Topology topology(CommandContext<CommandSourceStack> context) {
        String value = StringArgumentType.getString(context, "topology");
        Topology topology = Topology.parse(value);
        if (topology == null) {
            context.getSource().sendFailure(Component.literal(
                "Unknown topology " + value + "; use linear, intersection3, or intersection4"
            ));
        }
        return topology;
    }

    private enum Topology {
        LINEAR("linear", CompositeTemplateDescriptor.ID_PREFIX,
            CompositeTemplateDescriptor.CANDIDATES_PER_THEME),
        INTERSECTION_THREE("intersection3", CompositeTemplateDescriptor.INTERSECTION_THREE_ID_PREFIX,
            CompositeTemplateDescriptor.INTERSECTION_CANDIDATES_PER_THEME),
        INTERSECTION_FOUR("intersection4", CompositeTemplateDescriptor.INTERSECTION_FOUR_ID_PREFIX,
            CompositeTemplateDescriptor.INTERSECTION_CANDIDATES_PER_THEME);

        private final String label;
        private final String prefix;
        private final int candidateCount;

        Topology(String label, String prefix, int candidateCount) {
            this.label = label;
            this.prefix = prefix;
            this.candidateCount = candidateCount;
        }

        private String label() {
            return label;
        }

        private int candidateCount() {
            return candidateCount;
        }

        private String id(String theme, int variant) {
            return prefix + theme + "__" + String.format(Locale.ROOT, "%02d", variant);
        }

        private static Topology parse(String value) {
            if (value == null) return null;
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "linear", "room" -> LINEAR;
                case "intersection3", "intersection_3", "t", "t_junction" -> INTERSECTION_THREE;
                case "intersection4", "intersection_4", "cross", "crossroads" -> INTERSECTION_FOUR;
                default -> null;
            };
        }
    }
}
