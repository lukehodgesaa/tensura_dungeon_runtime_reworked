package com.mineclawd.tensuraadditions.compat.moreskills;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BerettaSurfaceAnchor {
    private static final int BELOW_RANGE = 12;
    private static final int ABOVE_RANGE = 24;

    private BerettaSurfaceAnchor() {
    }

    public static Vec3 project(ServerLevel level, Vec3 point) {
        int blockX = BlockPos.containing(point).getX();
        int blockZ = BlockPos.containing(point).getZ();
        int centerY = BlockPos.containing(point).getY();

        int bestY = centerY;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = centerY - BELOW_RANGE; y <= centerY + ABOVE_RANGE; y++) {
            BlockPos floorPos = new BlockPos(blockX, y - 1, blockZ);
            BlockPos airPos = new BlockPos(blockX, y, blockZ);
            BlockState floor = level.getBlockState(floorPos);
            BlockState air = level.getBlockState(airPos);
            if (floor.getCollisionShape(level, floorPos).isEmpty()
                    || !air.getCollisionShape(level, airPos).isEmpty()) {
                continue;
            }

            int distance = Math.abs(y - centerY);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestY = y;
            }
        }

        return new Vec3(point.x, bestY + 0.02D, point.z);
    }

    public static Vec3 projectTarget(ServerLevel level, LivingEntity target) {
        return project(level, target.position());
    }
}
