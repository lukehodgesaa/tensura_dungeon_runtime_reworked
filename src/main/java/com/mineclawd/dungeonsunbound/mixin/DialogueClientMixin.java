package com.mineclawd.dungeonsunbound.mixin;

import com.benji.dialoguestudio.client.dialogue.DialogueClient;
import com.mineclawd.dungeonsunbound.npc.client.DungeonDialogueClient;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DialogueClient.class, remap = false)
public abstract class DialogueClientMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private static void dungeonsUnbound$start(UUID sessionId, ResourceLocation dialogueId, String json, CallbackInfo callback) {
        if (DungeonDialogueClient.start(sessionId, dialogueId, json)) {
            callback.cancel();
        }
    }

    @Inject(method = "setNodeState", at = @At("HEAD"), cancellable = true)
    private static void dungeonsUnbound$nodeState(UUID sessionId, String nodeId, List<Boolean> availability, CallbackInfo callback) {
        if (DungeonDialogueClient.nodeState(sessionId, nodeId, availability)) {
            callback.cancel();
        }
    }

    @Inject(method = "cancel", at = @At("HEAD"), cancellable = true)
    private static void dungeonsUnbound$cancel(UUID sessionId, CallbackInfo callback) {
        if (DungeonDialogueClient.cancel(sessionId)) {
            callback.cancel();
        }
    }
}
