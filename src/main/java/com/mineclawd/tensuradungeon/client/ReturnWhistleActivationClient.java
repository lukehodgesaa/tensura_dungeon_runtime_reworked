package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.network.ReturnWhistleActivationPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ReturnWhistleActivationClient {
    private ReturnWhistleActivationClient() {
    }

    public static void handle(ReturnWhistleActivationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().gameRenderer
            .displayItemActivation(payload.whistle()));
    }
}
