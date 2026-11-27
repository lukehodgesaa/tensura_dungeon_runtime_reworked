package com.mineclawd.dungeonsunbound.npc;

import com.benji.dialoguestudio.dialogue.DialogueApi;
import com.benji.dialoguestudio.dialogue.DialogueRegistry;
import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.mineclawd.dungeonsunbound.TensuraDungeonsUnbound;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class DungeonNpcDialogues {
    private DungeonNpcDialogues() {
    }

    public static boolean isOurs(ResourceLocation dialogueId) {
        return dialogueId != null && TensuraDungeonsUnbound.MOD_ID.equals(dialogueId.getNamespace());
    }

    public static void talk(ServerPlayer player, DungeonNpc npc) {
        if (DialogueApi.isActive(player)) {
            return;
        }
        if (!DialogueApi.start(player, npc, npc.profile().dialogue())) {
            TensuraDungeonsUnbound.LOGGER.warn("[DU-NPC] Dialogue {} could not start for {}", npc.profile().dialogue(), player.getGameProfile().getName());
        }
    }

    public static boolean debrief(ServerPlayer player, Entity speaker, String issue) {
        if (DialogueApi.isActive(player)) {
            return false;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
            TensuraDungeonsUnbound.MOD_ID, "debrief/" + issue.toLowerCase(Locale.ROOT)
        );
        DialogueDefinition definition = DialogueRegistry.get(id);
        if (definition == null) {
            return false;
        }
        DialogueDefinition.Line name = new DialogueDefinition.Line();
        name.literal = speaker.getName().getString();
        definition.npc_name = name;
        return DialogueApi.start(player, speaker, id);
    }
}
