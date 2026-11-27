package com.mineclawd.tensuradungeon.reward;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class VanillaInventoryGrant implements InventoryGrant {
    public static final VanillaInventoryGrant INSTANCE = new VanillaInventoryGrant();

    private VanillaInventoryGrant() {
    }

    @Override
    public boolean canGrant(Player player, List<ItemStack> rewardStacks) {
        Inventory inventory = player.getInventory();
        List<ItemStack> simulated = new ArrayList<>();
        for (ItemStack stack : inventory.items) {
            simulated.add(stack.copy());
        }
        for (ItemStack rewardStack : rewardStacks) {
            if (rewardStack.isEmpty()) {
                continue;
            }
            int remaining = rewardStack.getCount();
            for (ItemStack slotStack : simulated) {
                if (remaining <= 0) {
                    break;
                }
                if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, rewardStack)) {
                    continue;
                }
                int space = Math.min(slotStack.getMaxStackSize(), inventory.getMaxStackSize()) - slotStack.getCount();
                if (space <= 0) {
                    continue;
                }
                int move = Math.min(space, remaining);
                slotStack.grow(move);
                remaining -= move;
            }
            for (int i = 0; i < simulated.size() && remaining > 0; i++) {
                ItemStack slotStack = simulated.get(i);
                if (!slotStack.isEmpty()) {
                    continue;
                }
                int move = Math.min(Math.min(rewardStack.getMaxStackSize(), inventory.getMaxStackSize()), remaining);
                if (move <= 0) {
                    continue;
                }
                ItemStack copy = rewardStack.copy();
                copy.setCount(move);
                simulated.set(i, copy);
                remaining -= move;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void grant(Player player, List<ItemStack> rewardStacks) {
        Inventory inventory = player.getInventory();
        for (ItemStack rewardStack : rewardStacks) {
            inventory.add(rewardStack.copy());
        }
    }
}
