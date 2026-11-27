package com.mineclawd.tensuradungeon.reward;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface InventoryGrant {
    boolean canGrant(Player player, List<ItemStack> rewardStacks);

    void grant(Player player, List<ItemStack> rewardStacks);
}
