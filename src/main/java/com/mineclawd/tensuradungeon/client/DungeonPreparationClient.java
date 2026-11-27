package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.DungeonPreparationOpenPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonPreparationClient {
    private DungeonPreparationClient() {}

    public static void handle(DungeonPreparationOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ExpeditionTableDialogueClient.stopCinematic();
            DungeonClientSoundEffects.playMenuOpen();
            Minecraft.getInstance().setScreen(new DungeonPreparationScreen());
        });
    }
}
