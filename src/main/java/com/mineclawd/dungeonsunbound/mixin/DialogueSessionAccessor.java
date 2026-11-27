package com.mineclawd.dungeonsunbound.mixin;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.benji.dialoguestudio.dialogue.DialogueSessionManager$Session", remap = false)
public interface DialogueSessionAccessor {
    @Accessor("sessionId")
    UUID dungeonsUnbound$sessionId();

    @Accessor("dialogueId")
    ResourceLocation dungeonsUnbound$dialogueId();
}
