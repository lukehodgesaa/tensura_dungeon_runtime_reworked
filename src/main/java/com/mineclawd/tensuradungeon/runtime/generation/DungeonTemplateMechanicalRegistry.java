package com.mineclawd.tensuradungeon.runtime.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeRoomRuntimeRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeTemplateDescriptor;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonTemplateMechanicalRegistry {
    private static final long LARGE_PLACEMENT_VOLUME = 250_000L;
    private static final long UNSAFE_PLACEMENT_VOLUME = 2_000_000L;
    private static final ResourceLocation SNAPSHOT_ID = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID, "dungeon/template_mechanics.json"
    );
    private static ResourceManager cachedManager;
    private static DerivedSnapshot cachedDerived;
    private static final Map<String, Snapshot> cachedSnapshots = new LinkedHashMap<>();

    private DungeonTemplateMechanicalRegistry() {
    }

    public static synchronized Snapshot get(ServerLevel level, DungeonTemplateMetadataRegistry.Snapshot authored) {
        return getForTemplateIds(level, authored, authored.templates().keySet());
    }

    public static synchronized Snapshot getForTheme(
        ServerLevel level,
        DungeonTemplateMetadataRegistry.Snapshot authored,
        String visualTheme
    ) {
        List<String> templateIds = authored.enabledTemplates().stream()
            .filter(metadata -> visualTheme != null && visualTheme.equals(metadata.visualTheme()))
            .map(DungeonTemplateMetadata::id)
            .toList();
        return getForTemplateIds(level, authored, templateIds);
    }

    public static synchronized Snapshot getForTemplateIds(
        ServerLevel level,
        DungeonTemplateMetadataRegistry.Snapshot authored,
        Collection<String> templateIds
    ) {
        ResourceManager manager = level.getServer().getResourceManager();
        if (cachedManager != manager) {
            cachedManager = manager;
            cachedDerived = loadDerived(manager);
            cachedSnapshots.clear();
        }
        List<String> normalizedIds = new ArrayList<>(new LinkedHashSet<>(
            templateIds == null ? List.of() : templateIds
        ));
        String cacheKey = String.join("\u001F", normalizedIds);
        Snapshot cached = cachedSnapshots.get(cacheKey);
        if (cached == null) {
            cached = inspect(level, authored, normalizedIds, cachedDerived);
            cachedSnapshots.put(cacheKey, cached);
        }
        return cached;
    }

    public static synchronized Snapshot reload(ServerLevel level, DungeonTemplateMetadataRegistry.Snapshot authored) {
        return reload(level, authored, authored.templates().keySet());
    }

    public static synchronized Snapshot reload(
        ServerLevel level,
        DungeonTemplateMetadataRegistry.Snapshot authored,
        Collection<String> templateIds
    ) {
        cachedManager = level.getServer().getResourceManager();
        cachedDerived = loadDerived(cachedManager);
        cachedSnapshots.clear();
        return getForTemplateIds(level, authored, templateIds);
    }

    public static synchronized void invalidate() {
        cachedManager = null;
        cachedDerived = null;
        cachedSnapshots.clear();
        CompositeRoomRuntimeRegistry.invalidate();
    }

    private static Snapshot inspect(
        ServerLevel level,
        DungeonTemplateMetadataRegistry.Snapshot authored,
        Collection<String> templateIds,
        DerivedSnapshot derived
    ) {
        Map<String, DungeonTemplateMechanicalMetadata> templates = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>(derived.errors());
        for (String templateId : templateIds) {
            DungeonTemplateMetadata metadata = authored.templates().get(templateId);
            if (metadata == null) {
                errors.add(templateId + ": metadata entry missing");
                continue;
            }
            if (!metadata.enabled()) {
                templates.put(metadata.id(), authoredDisabled(metadata));
                continue;
            }
            if (CompositeTemplateDescriptor.isCompositeId(metadata.id())) {
                CompositeRoomRuntimeRegistry.Resolution composite =
                    CompositeRoomRuntimeRegistry.resolve(level, metadata.id());
                if (composite.success() && composite.mechanical() != null) {
                    templates.put(metadata.id(), composite.mechanical());
                } else {
                    templates.put(metadata.id(), compositeFailure(metadata.id(), composite.failureSummary()));
                }
                continue;
            }
            DerivedTemplate entry = derived.templates().get(metadata.id());
            if (entry == null) {
                errors.add(metadata.id() + ": generated mechanical snapshot entry missing");
                templates.put(metadata.id(), missing(metadata.id()));
                continue;
            }
            templates.put(metadata.id(), fromDerived(metadata, entry));
        }
        return new Snapshot(Map.copyOf(templates), List.copyOf(errors));
    }

    private static DungeonTemplateMechanicalMetadata fromDerived(
        DungeonTemplateMetadata authored,
        DerivedTemplate derived
    ) {
        int width = derived.width();
        int height = derived.height();
        int depth = derived.depth();
        long volume = Math.max(1L, (long) width * height * depth);
        BoundingBox bounds = new BoundingBox(0, 0, 0, width - 1, height - 1, depth - 1);
        List<DungeonTemplateMechanicalMetadata.Connector> primary = new ArrayList<>();
        addConnectors(primary, derived, bounds, "floor_connector_anchor", true);
        addConnectors(primary, derived, bounds, "connector_exit", true);
        List<DungeonTemplateMechanicalMetadata.Connector> connectors = primary;
        if (connectors.isEmpty()) {
            connectors = new ArrayList<>();
            addConnectors(connectors, derived, bounds, "floor_entry_anchor", false);
            addConnectors(connectors, derived, bounds, "exit_anchor", false);
        }

        Map<String, Integer> markers = new LinkedHashMap<>();
        markers.put("room_controller", derived.markerCount("room_controller"));
        markers.put("bounds", derived.markerCount("bounds_anchor"));
        markers.put("mob_reset", derived.markerCount("mob_reset_anchor"));
        int barrierCount = derived.markerCount("barrier_anchor");
        for (int channel = 1; channel <= 4; channel++) {
            int channelCount = derived.markerCount("barrier_anchor_" + channel);
            markers.put("barrier_" + channel, channelCount);
            barrierCount += channelCount;
        }
        markers.put("barrier", barrierCount);
        int combatSpawns = derived.markerCount("spawn_melee")
            + derived.markerCount("spawn_ranged")
            + derived.markerCount("spawn_flank")
            + derived.markerCount("spawn_highground")
            + derived.markerCount("spawn_elite")
            + derived.markerCount("spawn_boss");
        markers.put("combat_spawn", combatSpawns);
        markers.put("objective", derived.markerCount("objective_anchor"));
        markers.put("interact", derived.markerCount("interact_anchor"));
        markers.put("floor_connector", derived.markerCount("floor_connector_anchor"));
        markers.put("connector_exit", derived.markerCount("connector_exit"));
        markers.put("exit_anchor", derived.markerCount("exit_anchor"));
        markers.put("role_socket", derived.markerCount("role_socket"));
        markers.put("segment_connector", derived.markerCount("segment_connector"));
        markers.put("segment_connector_internal", derived.markerCount("segment_connector_internal"));
        markers.put("segment_connector_external", derived.markerCount("segment_connector_external"));

        List<String> rejection = new ArrayList<>();
        int uncompiledCompositeMarkers = markers.getOrDefault("role_socket", 0)
            + markers.getOrDefault("segment_connector", 0)
            + markers.getOrDefault("segment_connector_internal", 0)
            + markers.getOrDefault("segment_connector_external", 0);
        if (uncompiledCompositeMarkers > 0) {
            rejection.add("UNCOMPILED_COMPOSITE_AUTHORING_MARKERS_" + uncompiledCompositeMarkers);
        }
        if (!authored.enabled()) rejection.add("AUTHORED_DISABLED: " + authored.exclusionReason());
        if (markers.getOrDefault("room_controller", 0) != 1) rejection.add("ROOM_CONTROLLER_COUNT_" + markers.getOrDefault("room_controller", 0));
        if (markers.getOrDefault("bounds", 0) != 2) rejection.add("BOUNDS_ANCHOR_COUNT_" + markers.getOrDefault("bounds", 0));
        if (connectors.size() < (authored.terminalAllowed() ? 1 : 2)) rejection.add("INSUFFICIENT_CONNECTORS_" + connectors.size());
        int typedExits = markers.getOrDefault("connector_exit", 0);
        int typedEntrances = markers.getOrDefault("floor_connector", 0);
        if (typedExits > 0 && typedEntrances != 1) rejection.add("TYPED_ENTRANCE_COUNT_" + typedEntrances);
        boolean combatRole = authored.roles().stream().anyMatch(role -> role == DungeonRoomRole.COMBAT
            || role == DungeonRoomRole.LARGE_COMBAT || role == DungeonRoomRole.ARENA || role == DungeonRoomRole.FINALE);
        if (combatRole && combatSpawns <= 0) rejection.add("COMBAT_ROLE_WITHOUT_SPAWNS");
        if (combatRole && markers.getOrDefault("mob_reset", 0) <= 0) rejection.add("COMBAT_ROLE_WITHOUT_MOB_RESET");
        if (combatRole && markers.getOrDefault("barrier", 0) <= 0) rejection.add("COMBAT_ROLE_WITHOUT_BARRIERS");
        for (int channel = 1; channel <= 4; channel++) {
            int count = markers.getOrDefault("barrier_" + channel, 0);
            if (count != 0 && count != 2) {
                rejection.add("AMBIGUOUS_BARRIER_CHANNEL_" + channel + "_COUNT_" + count);
            }
        }
        if (volume > UNSAFE_PLACEMENT_VOLUME) rejection.add("UNSAFE_PLACEMENT_VOLUME_" + volume);
        if (derived.entityCount() > 0) {
            rejection.add("TEMPLATE_ENTITIES_UNSUPPORTED_" + derived.entityCount());
        }
        return new DungeonTemplateMechanicalMetadata(
            authored.id(), width, height, depth, volume,
            derived.serializedBlocks(), derived.nonAirBlocks(), derived.fluidBearingBlocks(),
            derived.entityCount(), placementCost(volume), connectors,
            Map.copyOf(markers), derived.markerPositions(), rejection.isEmpty(), List.copyOf(rejection)
        );
    }

    private static void addConnectors(
        List<DungeonTemplateMechanicalMetadata.Connector> output,
        DerivedTemplate template,
        BoundingBox bounds,
        String type,
        boolean primary
    ) {
        for (BlockPos pos : template.connectorPositions().getOrDefault(type, List.of())) {
            Direction facing = inferFacing(pos, bounds);
            output.add(new DungeonTemplateMechanicalMetadata.Connector(
                type, pos, facing, clearance(pos, facing), primary
            ));
        }
    }

    private static int placementCost(long volume) {
        if (volume > UNSAFE_PLACEMENT_VOLUME) return 5;
        if (volume > 1_000_000L) return 4;
        if (volume > LARGE_PLACEMENT_VOLUME) return 3;
        if (volume > 75_000L) return 2;
        return 1;
    }

    private static DungeonTemplateMechanicalMetadata.ClearanceBox clearance(BlockPos pos, Direction facing) {
        int sideX = facing.getAxis() == Direction.Axis.Z ? 2 : 1;
        int sideZ = facing.getAxis() == Direction.Axis.X ? 2 : 1;
        int forwardX = facing.getStepX() * 4;
        int forwardZ = facing.getStepZ() * 4;
        return new DungeonTemplateMechanicalMetadata.ClearanceBox(
            Math.min(pos.getX() - sideX, pos.getX() + forwardX - sideX),
            pos.getY() - 1,
            Math.min(pos.getZ() - sideZ, pos.getZ() + forwardZ - sideZ),
            Math.max(pos.getX() + sideX, pos.getX() + forwardX + sideX),
            pos.getY() + 4,
            Math.max(pos.getZ() + sideZ, pos.getZ() + forwardZ + sideZ)
        );
    }

    private static Direction inferFacing(BlockPos pos, BoundingBox bounds) {
        int west = Math.abs(pos.getX() - bounds.minX());
        int east = Math.abs(pos.getX() - bounds.maxX());
        int north = Math.abs(pos.getZ() - bounds.minZ());
        int south = Math.abs(pos.getZ() - bounds.maxZ());
        int best = Math.min(Math.min(west, east), Math.min(north, south));
        if (west == best) return Direction.WEST;
        if (east == best) return Direction.EAST;
        return north == best ? Direction.NORTH : Direction.SOUTH;
    }

    private static DerivedSnapshot loadDerived(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(SNAPSHOT_ID);
        if (resource.isEmpty()) {
            return new DerivedSnapshot(Map.of(), List.of("Missing mechanical snapshot resource: " + SNAPSHOT_ID));
        }
        Map<String, DerivedTemplate> templates = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("schemaVersion") || root.get("schemaVersion").getAsInt() != 2) {
                errors.add("Unsupported mechanical snapshot schema");
            }
            JsonArray entries = root.getAsJsonArray("templates");
            if (entries == null) {
                errors.add("Mechanical snapshot has no templates array");
            } else {
                for (JsonElement element : entries) {
                    if (!element.isJsonObject()) {
                        errors.add("Mechanical snapshot entry is not an object");
                        continue;
                    }
                    DerivedTemplate template = parseDerived(element.getAsJsonObject(), errors);
                    if (template == null) continue;
                    if (templates.putIfAbsent(template.id(), template) != null) {
                        errors.add(template.id() + ": duplicate mechanical snapshot entry");
                    }
                }
            }
        } catch (Exception exception) {
            errors.add("Mechanical snapshot parse failed: " + exception.getClass().getSimpleName()
                + ": " + exception.getMessage());
        }
        return new DerivedSnapshot(Map.copyOf(templates), List.copyOf(errors));
    }

    private static DerivedTemplate parseDerived(JsonObject object, List<String> errors) {
        String id = object.has("id") ? object.get("id").getAsString().trim() : "";
        JsonArray size = object.getAsJsonArray("size");
        if (id.isBlank() || size == null || size.size() != 3) {
            errors.add((id.isBlank() ? "<unknown>" : id) + ": invalid mechanical snapshot identity or size");
            return null;
        }
        int width = size.get(0).getAsInt();
        int height = size.get(1).getAsInt();
        int depth = size.get(2).getAsInt();
        if (width <= 0 || height <= 0 || depth <= 0) {
            errors.add(id + ": mechanical snapshot dimensions must be positive");
            return null;
        }
        long volume = (long) width * height * depth;
        int serializedBlocks = object.has("serializedBlocks")
            ? object.get("serializedBlocks").getAsInt() : -1;
        int structureVoidBlocks = object.has("structureVoidBlocks")
            ? object.get("structureVoidBlocks").getAsInt() : -1;
        int nonAirBlocks = object.has("nonAirBlocks")
            ? object.get("nonAirBlocks").getAsInt() : -1;
        int fluidBearingBlocks = object.has("fluidBearingBlocks")
            ? object.get("fluidBearingBlocks").getAsInt() : -1;
        int entityCount = object.has("entityCount") ? object.get("entityCount").getAsInt() : -1;
        if (serializedBlocks < 0 || serializedBlocks > volume || structureVoidBlocks < 0
            || nonAirBlocks < 0 || (long) nonAirBlocks + structureVoidBlocks > serializedBlocks
            || fluidBearingBlocks < 0 || fluidBearingBlocks > nonAirBlocks || entityCount < 0) {
            errors.add(id + ": invalid placement census");
            return null;
        }
        Map<String, Integer> markerCounts = new LinkedHashMap<>();
        JsonObject counts = object.getAsJsonObject("markerCounts");
        if (counts != null) {
            for (Map.Entry<String, JsonElement> entry : counts.entrySet()) {
                int count = entry.getValue().getAsInt();
                if (count < 0) {
                    errors.add(id + ": negative marker count for " + entry.getKey());
                    return null;
                }
                markerCounts.put(entry.getKey(), count);
            }
        }
        Map<String, List<BlockPos>> connectorPositions = new LinkedHashMap<>();
        JsonObject positions = object.getAsJsonObject("connectorPositions");
        if (positions != null) {
            for (Map.Entry<String, JsonElement> entry : positions.entrySet()) {
                List<BlockPos> parsed = new ArrayList<>();
                for (JsonElement positionElement : entry.getValue().getAsJsonArray()) {
                    JsonArray position = positionElement.getAsJsonArray();
                    if (position.size() != 3) {
                        errors.add(id + ": invalid connector position for " + entry.getKey());
                        return null;
                    }
                    parsed.add(new BlockPos(
                        position.get(0).getAsInt(),
                        position.get(1).getAsInt(),
                        position.get(2).getAsInt()
                    ));
                }
                if (parsed.stream().anyMatch(pos -> pos.getX() < 0 || pos.getX() >= width
                    || pos.getY() < 0 || pos.getY() >= height
                    || pos.getZ() < 0 || pos.getZ() >= depth)) {
                    errors.add(id + ": out-of-bounds connector position for " + entry.getKey());
                    return null;
                }
                connectorPositions.put(entry.getKey(), List.copyOf(parsed));
            }
        }
        Map<String, List<BlockPos>> markerPositions = parsePositions(
            id, object.getAsJsonObject("markerPositions"), width, height, depth, errors
        );
        if (markerPositions == null) {
            return null;
        }
        Set<String> markerNames = new LinkedHashSet<>(markerCounts.keySet());
        markerNames.addAll(markerPositions.keySet());
        for (String markerName : markerNames) {
            if (markerCounts.getOrDefault(markerName, -1)
                != markerPositions.getOrDefault(markerName, List.of()).size()) {
                errors.add(id + ": marker count mismatch for " + markerName);
                return null;
            }
        }
        for (String connector : List.of(
            "floor_connector_anchor", "connector_exit", "floor_entry_anchor", "exit_anchor"
        )) {
            List<BlockPos> connectorFacts = connectorPositions.getOrDefault(connector, List.of());
            List<BlockPos> markerFacts = markerPositions.getOrDefault(connector, List.of());
            if (markerCounts.getOrDefault(connector, 0) != connectorFacts.size()
                || !new LinkedHashSet<>(connectorFacts).equals(new LinkedHashSet<>(markerFacts))) {
                errors.add(id + ": connector facts mismatch for " + connector);
                return null;
            }
        }
        return new DerivedTemplate(
            id,
            width,
            height,
            depth,
            serializedBlocks,
            nonAirBlocks,
            fluidBearingBlocks,
            entityCount,
            Map.copyOf(markerCounts),
            Map.copyOf(connectorPositions),
            Map.copyOf(markerPositions)
        );
    }

    private static Map<String, List<BlockPos>> parsePositions(
        String id,
        JsonObject positions,
        int width,
        int height,
        int depth,
        List<String> errors
    ) {
        if (positions == null) {
            errors.add(id + ": markerPositions missing");
            return null;
        }
        Map<String, List<BlockPos>> parsedByName = new LinkedHashMap<>();
        Set<BlockPos> seenPositions = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : positions.entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                errors.add(id + ": invalid marker position list for " + entry.getKey());
                return null;
            }
            List<BlockPos> parsed = new ArrayList<>();
            for (JsonElement positionElement : entry.getValue().getAsJsonArray()) {
                if (!positionElement.isJsonArray()) {
                    errors.add(id + ": invalid marker position for " + entry.getKey());
                    return null;
                }
                JsonArray position = positionElement.getAsJsonArray();
                if (position.size() != 3) {
                    errors.add(id + ": invalid marker position for " + entry.getKey());
                    return null;
                }
                BlockPos markerPos = new BlockPos(
                    position.get(0).getAsInt(),
                    position.get(1).getAsInt(),
                    position.get(2).getAsInt()
                );
                if (markerPos.getX() < 0 || markerPos.getX() >= width
                    || markerPos.getY() < 0 || markerPos.getY() >= height
                    || markerPos.getZ() < 0 || markerPos.getZ() >= depth) {
                    errors.add(id + ": out-of-bounds marker position for " + entry.getKey());
                    return null;
                }
                if (!seenPositions.add(markerPos)) {
                    errors.add(id + ": duplicate marker position " + markerPos.toShortString());
                    return null;
                }
                parsed.add(markerPos);
            }
            parsedByName.put(entry.getKey(), List.copyOf(parsed));
        }
        return parsedByName;
    }

    private static DungeonTemplateMechanicalMetadata authoredDisabled(DungeonTemplateMetadata metadata) {
        return new DungeonTemplateMechanicalMetadata(
            metadata.id(), 0, 0, 0, 0L, 0, 0, 0, 0, 5, List.of(), Map.of(), Map.of(), false,
            List.of("AUTHORED_DISABLED: " + metadata.exclusionReason())
        );
    }

    private static DungeonTemplateMechanicalMetadata missing(String id) {
        return new DungeonTemplateMechanicalMetadata(
            id, 0, 0, 0, 0L, 0, 0, 0, 0, 5, List.of(), Map.of(), Map.of(), false,
            List.of("TEMPLATE_MISSING")
        );
    }

    private static DungeonTemplateMechanicalMetadata compositeFailure(String id, String reason) {
        return new DungeonTemplateMechanicalMetadata(
            id, 0, 0, 0, 0L, 0, 0, 0, 0, 5, List.of(), Map.of(), Map.of(), false,
            List.of("COMPOSITE_COMPILATION_FAILED: " + reason)
        );
    }

    private record DerivedSnapshot(Map<String, DerivedTemplate> templates, List<String> errors) {
    }

    private record DerivedTemplate(
        String id,
        int width,
        int height,
        int depth,
        int serializedBlocks,
        int nonAirBlocks,
        int fluidBearingBlocks,
        int entityCount,
        Map<String, Integer> markerCounts,
        Map<String, List<BlockPos>> connectorPositions,
        Map<String, List<BlockPos>> markerPositions
    ) {
        int markerCount(String marker) {
            return markerCounts.getOrDefault(marker, 0);
        }
    }

    public record Snapshot(Map<String, DungeonTemplateMechanicalMetadata> templates, List<String> errors) {
        public long eligibleCount() {
            return templates.values().stream().filter(DungeonTemplateMechanicalMetadata::solverEligible).count();
        }

        public boolean valid() {
            return errors.isEmpty() && !templates.isEmpty();
        }
    }
}
