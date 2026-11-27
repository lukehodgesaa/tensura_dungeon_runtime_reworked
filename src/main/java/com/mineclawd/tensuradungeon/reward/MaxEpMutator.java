package com.mineclawd.tensuradungeon.reward;

import net.minecraft.world.entity.player.Player;

public interface MaxEpMutator {
    MutationResult increaseMaxEp(Player player, int requestedIncrease);

    record MutationResult(boolean valueChanged, int requestedIncrease, String mutationPath, String error) {
    }
}
