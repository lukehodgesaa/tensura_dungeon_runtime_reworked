package com.mineclawd.tensuraadditions.item;

import com.mineclawd.tensuraadditions.entity.DungeonExitPortalEntity;
import com.mineclawd.tensuraadditions.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public final class DungeonExitPortalItem extends Item {
    public DungeonExitPortalItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        DungeonExitPortalEntity portal = ModEntities.DUNGEON_EXIT_PORTAL.get().create(level);
        if (portal == null) {
            return InteractionResult.FAIL;
        }

        BlockPos placementPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 center = Vec3.atBottomCenterOf(placementPos);
        portal.moveTo(center.x, center.y, center.z, 0.0F, 0.0F);

        if (!level.addFreshEntity(portal)) {
            return InteractionResult.FAIL;
        }

        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
