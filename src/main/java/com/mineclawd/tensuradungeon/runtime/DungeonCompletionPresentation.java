package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.reward.DungeonCurrencyPolicy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonCompletionPresentation {
    private DungeonCompletionPresentation() {}

    public static void show(
        ServerPlayer player,
        int completedFloor,
        int nextFloor,
        long awardedCurrencyBronze
    ) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
            Component.literal("FLOOR " + Math.max(1, completedFloor) + " COMPLETE")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        ));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal(subtitle(completedFloor, nextFloor, awardedCurrencyBronze))
                .withStyle(ChatFormatting.AQUA)
        ));
    }

    static String subtitle(int completedFloor, int nextFloor, long awardedCurrencyBronze) {
        String nextDepth = "NEXT DEPTH: " + Math.max(completedFloor + 1, nextFloor);
        if (awardedCurrencyBronze <= 0L) return nextDepth;
        DungeonCurrencyPolicy.SessionPayout payout =
            DungeonCurrencyPolicy.decomposeSession(awardedCurrencyBronze);
        List<String> denominations = new ArrayList<>(3);
        if (payout.goldCoins() > 0) denominations.add(payout.goldCoins() + " GOLD");
        if (payout.silverCoins() > 0) denominations.add(payout.silverCoins() + " SILVER");
        if (payout.bronzeCoins() > 0) denominations.add(payout.bronzeCoins() + " BRONZE");
        return "REWARD: " + String.join(" + ", denominations) + "  |  " + nextDepth;
    }
}
