package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonExitPromptPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonExitPromptClient {
    private DungeonExitPromptClient() {}

    public static void handle(DungeonExitPromptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new DungeonExitPromptScreen(payload)));
    }
}
