package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.ResurrectionBraceletActivationPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ResurrectionBraceletActivationClient {
    private ResurrectionBraceletActivationClient() {
    }

    public static void handle(
        ResurrectionBraceletActivationPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> Minecraft.getInstance().gameRenderer
            .displayItemActivation(payload.bracelet()));
    }
}
