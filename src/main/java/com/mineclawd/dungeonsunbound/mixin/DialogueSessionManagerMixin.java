package com.mineclawd.dungeonsunbound.mixin;

import com.benji.dialoguestudio.dialogue.DialogueApi;
import com.benji.dialoguestudio.dialogue.DialogueSessionManager;
import com.mineclawd.dungeonsunbound.npc.DungeonNpcDialogues;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DialogueSessionManager.class, remap = false)
public abstract class DialogueSessionManagerMixin {
    @Shadow
    @Final
    private static Map<UUID, ?> ACTIVE;

    @Inject(method = "skip", at = @At("HEAD"), cancellable = true)
    private static void dungeonsUnbound$closeInsteadOfSkipping(ServerPlayer player, UUID sessionId, CallbackInfo callback) {
        if (ACTIVE.get(player.getUUID()) instanceof DialogueSessionAccessor session
            && session.dungeonsUnbound$sessionId().equals(sessionId)
            && DungeonNpcDialogues.isOurs(session.dungeonsUnbound$dialogueId())) {
            DialogueApi.cancel(player);
            callback.cancel();
        }
    }
}
