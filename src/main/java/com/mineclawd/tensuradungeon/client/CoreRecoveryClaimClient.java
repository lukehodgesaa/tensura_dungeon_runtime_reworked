package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.CoreRecoveryClaimResultPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class CoreRecoveryClaimClient {
    private CoreRecoveryClaimClient() {}

    public static void handle(CoreRecoveryClaimResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof CoreSatchelScreen screen) {
                screen.handleClaimResult(payload);
            } else {
                CoreSatchelInventoryTab.handleQuickStoreResult(payload);
            }
        });
    }
}
