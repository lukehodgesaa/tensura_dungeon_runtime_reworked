package com.mineclawd.tensuradungeon.block.entity;

import net.minecraft.core.BlockPos;

public record RoomProtectionView(
    BlockPos controllerPos,
    RoomControllerBlockEntity.RoomState roomState,
    RoomControllerBlockEntity.RoomBounds bounds,
    boolean ruleZoneProtectionActive,
    boolean protectionGraceActive,
    long protectionGraceTicksRemaining,
    String protectedBoundsMode
) {
    public boolean hasBounds() {
        return bounds != null;
    }

    public boolean anyRuleZoneProtectionActive() {
        return ruleZoneProtectionActive || protectionGraceActive;
    }

    public boolean containsKnownProtectedArea(BlockPos pos, int fallbackRadius) {
        return bounds != null
            ? bounds.contains(pos)
            : controllerPos.distSqr(pos) <= fallbackRadius * fallbackRadius;
    }
}
