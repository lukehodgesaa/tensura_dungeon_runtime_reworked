package com.mineclawd.tensuradungeon.runtime.generation.composite;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public record CompositeSegmentDefinition(
    ResourceLocation id,
    String theme,
    Kind kind,
    int width,
    int height,
    int depth,
    List<Cell> cells,
    List<Connector> connectors,
    List<Socket> sockets
) {
    public CompositeSegmentDefinition {
        if (id == null) throw new IllegalArgumentException("Segment id is required");
        theme = theme == null ? "" : theme.trim().toLowerCase(Locale.ROOT);
        if (theme.isBlank()) throw new IllegalArgumentException("Segment theme is required");
        if (kind == null) throw new IllegalArgumentException("Segment kind is required");
        if (width < 1 || height < 1 || depth < 1) {
            throw new IllegalArgumentException("Segment dimensions must be positive");
        }
        cells = cells == null ? List.of() : List.copyOf(cells);
        connectors = connectors == null ? List.of() : List.copyOf(connectors);
        sockets = sockets == null ? List.of() : List.copyOf(sockets);
        cells.forEach(cell -> requireInside(cell.pos(), width, height, depth, "cell"));
        connectors.forEach(connector -> requireInside(connector.pos(), width, height, depth, "connector"));
        sockets.forEach(socket -> requireInside(socket.pos(), width, height, depth, "socket"));
    }

    private static void requireInside(BlockPos pos, int width, int height, int depth, String type) {
        if (pos == null || pos.getX() < 0 || pos.getX() >= width
            || pos.getY() < 0 || pos.getY() >= height
            || pos.getZ() < 0 || pos.getZ() >= depth) {
            throw new IllegalArgumentException("Out-of-bounds segment " + type + ": " + pos);
        }
    }

    public boolean isLinearPrototypeEligible() {
        return connectors.size() == 2;
    }

    public boolean isCompositeEligible() {
        return connectors.size() >= 2 && connectors.size() <= 4;
    }

    public boolean connectorCountMatchesKind() {
        if (!isCompositeEligible()) return false;
        return switch (kind) {
            case ENTRANCE, EXIT, STRAIGHT, CORNER, STAIRS, COMBAT, TRANSITION ->
                connectors.size() == 2;
            case INTERSECTION_THREE -> connectors.size() == 3;
            case INTERSECTION_FOUR -> connectors.size() == 4;
        };
    }

    public record Cell(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
        public Cell {
            if (pos == null || state == null) throw new IllegalArgumentException("Cell position and state are required");
            pos = pos.immutable();
            nbt = nbt == null ? null : nbt.copy();
        }

        @Override
        public CompoundTag nbt() {
            return nbt == null ? null : nbt.copy();
        }
    }

    public record Connector(BlockPos pos, Direction facing, ConnectorRole role) {
        public Connector(BlockPos pos, Direction facing) {
            this(pos, facing, ConnectorRole.AUTO);
        }

        public Connector {
            if (pos == null || facing == null || !facing.getAxis().isHorizontal()) {
                throw new IllegalArgumentException("Segment connectors require a horizontal facing");
            }
            pos = pos.immutable();
            role = role == null ? ConnectorRole.AUTO : role;
        }
    }

    public enum ConnectorRole {
        AUTO,
        INTERNAL,
        EXTERNAL;

        public boolean mayPair() {
            return this != EXTERNAL;
        }

        public boolean mayRemainExternal() {
            return this != INTERNAL;
        }
    }

    public record Socket(BlockPos pos, Set<CompositeSocketCapability> capabilities) {
        public Socket {
            if (pos == null) throw new IllegalArgumentException("Socket position is required");
            pos = pos.immutable();
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
            if (capabilities.isEmpty()) throw new IllegalArgumentException("Socket needs at least one capability");
        }
    }

    public enum Kind {
        ENTRANCE,
        EXIT,
        STRAIGHT,
        CORNER,
        STAIRS,
        COMBAT,
        TRANSITION,
        INTERSECTION_THREE,
        INTERSECTION_FOUR;

        public boolean isBody() {
            return this != ENTRANCE && this != EXIT;
        }

        public boolean isIntersection() {
            return this == INTERSECTION_THREE || this == INTERSECTION_FOUR;
        }

        public static Kind parse(String value) {
            if (value == null) return null;
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "entrance", "entrance_cap", "start" -> ENTRANCE;
                case "exit", "exit_cap", "end" -> EXIT;
                case "straight", "corridor", "corridor_straight" -> STRAIGHT;
                case "corner", "corridor_corner" -> CORNER;
                case "stairs", "stair", "vertical" -> STAIRS;
                case "combat", "chamber", "arena", "widening" -> COMBAT;
                case "transition", "widening_combat" -> TRANSITION;
                case "intersection", "intersection_3", "three_way", "t_junction" ->
                    INTERSECTION_THREE;
                case "intersection_4", "four_way", "cross", "crossroads" ->
                    INTERSECTION_FOUR;
                default -> null;
            };
        }
    }
}
