package com.mineclawd.tensuradungeon.mixin;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLifecycleHooks.class, remap = false)
public abstract class ServerLifecycleHooksMixin {
    @Inject(method = "handleServerStopped", at = @At("HEAD"), cancellable = true)
    private static void tensuraDungeon$dontClearNewServerState(MinecraftServer server, CallbackInfo callback) {
        MinecraftServer current = ServerLifecycleHooks.getCurrentServer();
        if (current != null && current != server) {
            callback.cancel();
        }
    }
}
