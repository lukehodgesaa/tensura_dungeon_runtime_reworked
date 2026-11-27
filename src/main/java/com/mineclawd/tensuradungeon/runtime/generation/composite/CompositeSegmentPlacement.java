package com.mineclawd.tensuradungeon.runtime.generation.composite;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

public record CompositeSegmentPlacement(
    CompositeSegmentDefinition segment,
    BlockPos origin,
    Rotation rotation
) {
    public CompositeSegmentPlacement {
        if (segment == null || origin == null || rotation == null) {
            throw new IllegalArgumentException("Segment placement is incomplete");
        }
        origin = origin.immutable();
    }
}
