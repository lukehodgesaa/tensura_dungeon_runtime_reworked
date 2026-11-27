package com.mineclawd.tensuradungeon.runtime.generation.composite;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.RoleSocketBlock;
import com.mineclawd.tensuradungeon.mixin.StructureTemplateAccessor;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalMetadata;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class CompositeRoomRuntimeRegistry {
    private static final String RESOURCE_PREFIX = "structure/segments/";
    private static final Map<MinecraftServer, ServerCache> CACHES = new WeakHashMap<>();

    private CompositeRoomRuntimeRegistry() {
    }

    public static synchronized Resolution resolve(ServerLevel level, String id) {
        if (level == null || !CompositeTemplateDescriptor.isCompositeId(id)) {
            return Resolution.failed("NOT_A_COMPOSITE_TEMPLATE");
        }
        MinecraftServer server = level.getServer();
        ResourceManager manager = server.getResourceManager();
        ServerCache cache = CACHES.get(server);
        if (cache == null || cache.manager() != manager) {
            cache = new ServerCache(manager, loadCatalog(level, manager), new HashMap<>());
            CACHES.put(server, cache);
        }
        Resolution existing = cache.rooms().get(id);
        if (existing != null) return existing;
        Resolution compiled = compile(level, id, cache.catalog());
        cache.rooms().put(id, compiled);
        return compiled;
    }

    public static synchronized void invalidate() {
        CACHES.clear();
    }

    private static Resolution compile(ServerLevel level, String id, CatalogSnapshot catalog) {
        CompositeTemplateDescriptor descriptor = CompositeTemplateDescriptor.parse(id);
        if (descriptor == null) return Resolution.failed("INVALID_COMPOSITE_TEMPLATE_ID_" + id);
        try {
            List<CompositeSegmentDefinition> themeSegments = catalog.theme(descriptor.theme());
            if (themeSegments.isEmpty()) {
                return Resolution.failed("COMPOSITE_THEME_HAS_NO_SEGMENTS_" + descriptor.theme());
            }
            CompositeRoomPlanner.Result planned = CompositeRoomPlanner.planAndCompile(
                id,
                descriptor.theme(),
                themeSegments,
                descriptor.roomRequirements(),
                descriptor.compilationSeed(),
                descriptor.minimumSegments(),
                descriptor.maximumSegments()
            );
            if (!planned.success() || planned.room() == null) {
                List<String> errors = new ArrayList<>(planned.errors());
                errors.addAll(catalog.errors());
                return new Resolution(false, null, null, null, List.copyOf(errors));
            }
            CompiledCompositeRoom room = planned.room();
            return new Resolution(
                true,
                room,
                room.createTemplate(level),
                room.mechanicalMetadata(),
                List.of()
            );
        } catch (RuntimeException exception) {
            return Resolution.failed("COMPOSITE_MATERIALIZATION_"
                + exception.getClass().getSimpleName() + "_" + safe(exception.getMessage()));
        }
    }

    private static CatalogSnapshot loadCatalog(ServerLevel level, ResourceManager manager) {
        List<CompositeSegmentDefinition> segments = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        manager.listResources(
            "structure/segments",
            id -> id.getPath().endsWith(".nbt")
        ).keySet().stream()
            .filter(id -> TensuraDungeonRuntime.MOD_ID.equals(id.getNamespace()))
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .forEach(resourceId -> inspectSegment(level, resourceId, segments, errors));
        return new CatalogSnapshot(List.copyOf(segments), List.copyOf(errors));
    }

    private static void inspectSegment(
        ServerLevel level,
        ResourceLocation resourceId,
        List<CompositeSegmentDefinition> output,
        List<String> errors
    ) {
        String path = resourceId.getPath();
        if (!path.startsWith(RESOURCE_PREFIX) || !path.endsWith(".nbt")) return;
        String relative = path.substring(RESOURCE_PREFIX.length(), path.length() - 4);
        String[] parts = relative.split("/");
        if (parts.length < 3) {
            errors.add(resourceId + ": expected segments/<theme>/<kind>/<name>.nbt");
            return;
        }
        CompositeSegmentDefinition.Kind kind = CompositeSegmentDefinition.Kind.parse(parts[1]);
        if (kind == null) {
            errors.add(resourceId + ": unknown segment kind " + parts[1]);
            return;
        }
        String templatePath = path.substring("structure/".length(), path.length() - 4);
        ResourceLocation templateId = ResourceLocation.fromNamespaceAndPath(
            resourceId.getNamespace(), templatePath
        );
        StructureTemplate template = level.getStructureManager().get(templateId).orElse(null);
        if (template == null) {
            errors.add(resourceId + ": structure manager could not load " + templateId);
            return;
        }
        StructureTemplateAccessor accessor = (StructureTemplateAccessor) (Object) template;
        if (accessor.tensuraDungeon$getPalettes().size() != 1
            || !accessor.tensuraDungeon$getEntityInfoList().isEmpty()) {
            errors.add(resourceId + ": exactly one palette and zero entities are required");
            return;
        }

        List<CompositeSegmentDefinition.Cell> cells = new ArrayList<>();
        List<CompositeSegmentDefinition.Connector> connectors = new ArrayList<>();
        List<CompositeSegmentDefinition.Socket> sockets = new ArrayList<>();
        Set<Long> markerPositions = new LinkedHashSet<>();
        for (StructureTemplate.StructureBlockInfo info
            : accessor.tensuraDungeon$getPalettes().getFirst().blocks()) {
            cells.add(new CompositeSegmentDefinition.Cell(info.pos(), info.state(), info.nbt()));
            CompositeSegmentDefinition.ConnectorRole connectorRole = connectorRole(info.state());
            if (connectorRole != null) {
                if (!markerPositions.add(info.pos().asLong())) {
                    errors.add(resourceId + ": duplicate authoring marker at " + info.pos());
                    return;
                }
                connectors.add(new CompositeSegmentDefinition.Connector(
                    info.pos(),
                    info.state().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING),
                    connectorRole
                ));
            } else if (info.state().is(ModBlocks.ROLE_SOCKET.get())) {
                if (!markerPositions.add(info.pos().asLong())) {
                    errors.add(resourceId + ": duplicate authoring marker at " + info.pos());
                    return;
                }
                sockets.add(new CompositeSegmentDefinition.Socket(
                    info.pos(), RoleSocketBlock.capabilities(info.state())
                ));
            } else if (CompositeRoomCompiler.isFinalRoomMarker(info.state())) {
                errors.add(resourceId + ": segment contains final runtime marker at " + info.pos());
                return;
            }
        }
        if (connectors.size() < 2 || connectors.size() > 4) {
            errors.add(resourceId + ": segments require two to four connectors, found "
                + connectors.size());
            return;
        }
        for (CompositeSegmentDefinition.Connector connector : connectors) {
            if (!onSegmentBoundary(
                connector,
                template.getSize().getX(),
                template.getSize().getZ()
            )) {
                errors.add(resourceId + ": connector facing does not match its boundary at " + connector.pos());
                return;
            }
        }
        try {
            CompositeSegmentDefinition definition = new CompositeSegmentDefinition(
                templateId,
                parts[0],
                kind,
                template.getSize().getX(),
                template.getSize().getY(),
                template.getSize().getZ(),
                cells,
                connectors,
                sockets
            );
            if (!definition.connectorCountMatchesKind()) {
                errors.add(resourceId + ": kind " + kind + " does not accept "
                    + connectors.size() + " connectors");
                return;
            }
            output.add(definition);
        } catch (IllegalArgumentException exception) {
            errors.add(resourceId + ": " + exception.getMessage());
        }
    }

    @Nullable
    private static CompositeSegmentDefinition.ConnectorRole connectorRole(
        net.minecraft.world.level.block.state.BlockState state
    ) {
        if (state.is(ModBlocks.SEGMENT_CONNECTOR_INTERNAL.get())) {
            return CompositeSegmentDefinition.ConnectorRole.INTERNAL;
        }
        if (state.is(ModBlocks.SEGMENT_CONNECTOR_EXTERNAL.get())) {
            return CompositeSegmentDefinition.ConnectorRole.EXTERNAL;
        }
        if (state.is(ModBlocks.SEGMENT_CONNECTOR.get())) {
            return CompositeSegmentDefinition.ConnectorRole.AUTO;
        }
        return null;
    }

    private static boolean onSegmentBoundary(
        CompositeSegmentDefinition.Connector connector,
        int width,
        int depth
    ) {
        BlockPos pos = connector.pos();
        return switch (connector.facing()) {
            case WEST -> pos.getX() == 0;
            case EAST -> pos.getX() == width - 1;
            case NORTH -> pos.getZ() == 0;
            case SOUTH -> pos.getZ() == depth - 1;
            default -> false;
        };
    }

    private static String safe(String value) {
        return value == null ? "NO_MESSAGE" : value.replace(' ', '_');
    }

    private record ServerCache(
        ResourceManager manager,
        CatalogSnapshot catalog,
        Map<String, Resolution> rooms
    ) {
    }

    private record CatalogSnapshot(
        List<CompositeSegmentDefinition> segments,
        List<String> errors
    ) {
        private List<CompositeSegmentDefinition> theme(String theme) {
            return segments.stream().filter(segment -> segment.theme().equals(theme)).toList();
        }
    }

    public record Resolution(
        boolean success,
        @Nullable CompiledCompositeRoom room,
        @Nullable StructureTemplate template,
        @Nullable DungeonTemplateMechanicalMetadata mechanical,
        List<String> errors
    ) {
        public Resolution {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public static Resolution failed(String error) {
            return new Resolution(false, null, null, null, List.of(error));
        }

        public String failureSummary() {
            return errors.isEmpty() ? "NONE" : String.join(" | ", errors);
        }
    }
}
