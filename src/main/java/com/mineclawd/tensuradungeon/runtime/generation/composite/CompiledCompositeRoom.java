package com.mineclawd.tensuradungeon.runtime.generation.composite;

import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalMetadata;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public record CompiledCompositeRoom(
    String id,
    int width,
    int height,
    int depth,
    List<Cell> cells,
    Map<String, List<BlockPos>> markerPositions,
    List<ExternalConnector> externalConnectors,
    BlockPos controllerPos,
    List<String> sourceSegments,
    List<Assignment> assignments
) {
    public CompiledCompositeRoom {
        if (id == null || id.isBlank() || width < 1 || height < 1 || depth < 1) {
            throw new IllegalArgumentException("Compiled room identity and dimensions are required");
        }
        cells = cells == null ? List.of() : List.copyOf(cells);
        Map<String, List<BlockPos>> copiedMarkers = new LinkedHashMap<>();
        if (markerPositions != null) {
            markerPositions.forEach((name, positions) -> copiedMarkers.put(
                name,
                positions == null ? List.of() : positions.stream().map(BlockPos::immutable).toList()
            ));
        }
        markerPositions = Map.copyOf(copiedMarkers);
        externalConnectors = externalConnectors == null ? List.of() : List.copyOf(externalConnectors);
        controllerPos = controllerPos == null ? BlockPos.ZERO : controllerPos.immutable();
        sourceSegments = sourceSegments == null ? List.of() : List.copyOf(sourceSegments);
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }

    public long volume() {
        return (long) width * height * depth;
    }

    public DungeonTemplateMechanicalMetadata mechanicalMetadata() {
        Map<String, Integer> markers = new LinkedHashMap<>();
        markerPositions.forEach((name, positions) -> markers.put(name, positions.size()));
        markers.put("room_controller", count("room_controller"));
        markers.put("bounds", 0);
        markers.put("mob_reset", count("mob_reset_anchor"));
        int barrierCount = count("barrier_anchor");
        for (int channel = 1; channel <= 4; channel++) {
            int channelCount = count("barrier_anchor_" + channel);
            markers.put("barrier_" + channel, channelCount);
            barrierCount += channelCount;
        }
        markers.put("barrier", barrierCount);
        int combatSpawnCount = count("spawn_melee") + count("spawn_ranged")
            + count("spawn_flank") + count("spawn_highground")
            + count("spawn_elite") + count("spawn_boss");
        markers.put("combat_spawn", combatSpawnCount);
        markers.put("objective", count("objective_anchor"));
        markers.put("interact", count("interact_anchor"));
        markers.put("floor_connector", count("floor_connector_anchor"));
        markers.put("connector_exit", 0);
        markers.put("exit_anchor", 0);
        markers.put("role_socket", 0);
        markers.put("segment_connector", 0);
        markers.put("segment_connector_internal", 0);
        markers.put("segment_connector_external", 0);

        List<DungeonTemplateMechanicalMetadata.Connector> connectors = externalConnectors.stream()
            .map(connector -> new DungeonTemplateMechanicalMetadata.Connector(
                "floor_connector_anchor",
                connector.pos(),
                connector.facing(),
                clearance(connector.pos(), connector.facing()),
                true
            ))
            .toList();
        int nonAir = cells.size();
        int fluidBearing = (int) cells.stream().filter(cell -> !cell.state().getFluidState().isEmpty()).count();
        return new DungeonTemplateMechanicalMetadata(
            id,
            width,
            height,
            depth,
            volume(),
            cells.size(),
            nonAir,
            fluidBearing,
            0,
            placementCost(volume()),
            connectors,
            Map.copyOf(markers),
            markerPositions,
            true,
            List.of()
        );
    }

    public StructureTemplate createTemplate(ServerLevel level) {
        Map<BlockState, Integer> paletteIds = new LinkedHashMap<>();
        ListTag palette = new ListTag();
        ListTag blocks = new ListTag();
        for (Cell cell : cells) {
            int stateId = paletteIds.computeIfAbsent(cell.state(), state -> {
                palette.add(NbtUtils.writeBlockState(state));
                return palette.size() - 1;
            });
            CompoundTag encoded = new CompoundTag();
            encoded.put("pos", integerList(cell.pos().getX(), cell.pos().getY(), cell.pos().getZ()));
            encoded.putInt("state", stateId);
            if (cell.nbt() != null) encoded.put("nbt", cell.nbt());
            blocks.add(encoded);
        }
        CompoundTag root = new CompoundTag();
        root.put("size", integerList(width, height, depth));
        root.put("palette", palette);
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        StructureTemplate template = new StructureTemplate();
        template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), root);
        template.setAuthor("tensura_dungeon:composite_compiler");
        return template;
    }

    private int count(String marker) {
        return markerPositions.getOrDefault(marker, List.of()).size();
    }

    private static ListTag integerList(int... values) {
        ListTag list = new ListTag();
        for (int value : values) list.add(IntTag.valueOf(value));
        return list;
    }

    private static int placementCost(long volume) {
        if (volume > 2_000_000L) return 5;
        if (volume > 1_000_000L) return 4;
        if (volume > 250_000L) return 3;
        if (volume > 75_000L) return 2;
        return 1;
    }

    private DungeonTemplateMechanicalMetadata.ClearanceBox clearance(BlockPos pos, Direction facing) {
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

    public record Cell(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
        public Cell {
            if (pos == null || state == null) throw new IllegalArgumentException("Compiled cell is incomplete");
            pos = pos.immutable();
            nbt = nbt == null ? null : nbt.copy();
        }

        @Override
        public CompoundTag nbt() {
            return nbt == null ? null : nbt.copy();
        }
    }

    public record ExternalConnector(BlockPos pos, Direction facing) {
        public ExternalConnector {
            pos = pos.immutable();
        }
    }

    public record Assignment(
        BlockPos pos,
        CompositeFinalRole role,
        int barrierChannel,
        String sourceSegment
    ) {
        public Assignment {
            pos = pos.immutable();
            sourceSegment = sourceSegment == null ? "" : sourceSegment;
        }
    }
}
