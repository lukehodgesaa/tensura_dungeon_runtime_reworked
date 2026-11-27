package com.mineclawd.tensuradungeon.runtime.generation.composite;

import java.util.EnumMap;
import java.util.Map;

public record CompositeRoomRequirements(
    Map<CompositeFinalRole, Integer> requiredRoles,
    int externalConnectorCount,
    int barriersPerExternalConnector,
    boolean requireCombatSegment
) {
    public CompositeRoomRequirements {
        EnumMap<CompositeFinalRole, Integer> copied = new EnumMap<>(CompositeFinalRole.class);
        if (requiredRoles != null) {
            requiredRoles.forEach((role, count) -> {
                if (role != null && role != CompositeFinalRole.BARRIER && count != null && count > 0) {
                    copied.put(role, count);
                }
            });
        }
        requiredRoles = Map.copyOf(copied);
        if (externalConnectorCount < 1 || externalConnectorCount > 4) {
            throw new IllegalArgumentException("Composite rooms support one to four external connectors");
        }
        if (barriersPerExternalConnector < 0 || barriersPerExternalConnector > 2) {
            throw new IllegalArgumentException("Barrier count per connector must be between zero and two");
        }
    }

    public static CompositeRoomRequirements smallCombatPrototype() {
        return new CompositeRoomRequirements(
            Map.of(
                CompositeFinalRole.ROOM_CONTROLLER, 1,
                CompositeFinalRole.MOB_RESET, 1,
                CompositeFinalRole.GROUND_SPAWN, 2,
                CompositeFinalRole.RANGED_SPAWN, 1,
                CompositeFinalRole.ELITE_SPAWN, 1
            ),
            2,
            2,
            true
        );
    }

    public static CompositeRoomRequirements intersectionPrototype(int externalConnectorCount) {
        if (externalConnectorCount != 3 && externalConnectorCount != 4) {
            throw new IllegalArgumentException(
                "Composite intersections require three or four external connectors"
            );
        }
        return new CompositeRoomRequirements(
            Map.of(
                CompositeFinalRole.ROOM_CONTROLLER, 1,
                CompositeFinalRole.MOB_RESET, 1,
                CompositeFinalRole.GROUND_SPAWN, 2,
                CompositeFinalRole.RANGED_SPAWN, 1,
                CompositeFinalRole.ELITE_SPAWN, 1
            ),
            externalConnectorCount,
            2,
            false
        );
    }

    public int requiredSocketCount() {
        return requiredRoles.values().stream().mapToInt(Integer::intValue).sum()
            + externalConnectorCount * barriersPerExternalConnector;
    }
}
