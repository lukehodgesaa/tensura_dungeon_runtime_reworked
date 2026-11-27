package com.mineclawd.tensurahudnative.client;

import com.mineclawd.tensuradungeon.client.DungeonHubMusicController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

public final class DungeonRunState {
    private static final ResourceLocation DUNGEON_DIMENSION =
            ResourceLocation.fromNamespaceAndPath("tensura_dungeon", "dungeon");
    private static final long SETTLE_MS = 1500L;

    private static ClientLevel lastLevel;
    private static long outsideHubSince = Long.MAX_VALUE;

    private DungeonRunState() {
    }

    public static boolean inDungeon() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && DUNGEON_DIMENSION.equals(level.dimension().location());
    }

    public static boolean inRun() {
        ClientLevel level = Minecraft.getInstance().level;
        long now = System.currentTimeMillis();
        if (level != lastLevel) {
            lastLevel = level;
            outsideHubSince = now;
        }
        if (!inDungeon() || DungeonHubMusicController.inHub()) {
            outsideHubSince = now;
            return false;
        }
        return now - outsideHubSince >= SETTLE_MS;
    }
}
