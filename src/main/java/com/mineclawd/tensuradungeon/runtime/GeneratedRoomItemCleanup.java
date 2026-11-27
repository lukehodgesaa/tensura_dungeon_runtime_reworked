package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

public final class GeneratedRoomItemCleanup {
    private GeneratedRoomItemCleanup() {
    }

    public static int clearPlacementDrops(ServerLevel level, RoomControllerBlockEntity.RoomBounds bounds) {
        AABB roomBox = new AABB(
            bounds.min().getX() - 0.5D,
            bounds.min().getY() - 0.5D,
            bounds.min().getZ() - 0.5D,
            bounds.max().getX() + 1.5D,
            bounds.max().getY() + 1.5D,
            bounds.max().getZ() + 1.5D
        );
        int removed = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, roomBox)) {
            item.discard();
            removed++;
        }
        return removed;
    }
}
