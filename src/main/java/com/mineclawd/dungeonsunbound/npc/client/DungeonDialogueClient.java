package com.mineclawd.dungeonsunbound.npc.client;

import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.benji.dialoguestudio.network.dialogueengine.DialogueNetwork;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mineclawd.dungeonsunbound.TensuraDungeonsUnbound;
import com.mineclawd.dungeonsunbound.npc.DungeonNpcDialogues;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class DungeonDialogueClient {
    private static final String END_NODE = "__oasiso_end__";
    private static final Gson GSON = new Gson();

    private static UUID sessionId;
    private static DungeonDialogueScreen screen;

    private DungeonDialogueClient() {
    }

    public static boolean isActive() {
        return sessionId != null;
    }

    public static boolean start(UUID newSessionId, ResourceLocation dialogueId, String json) {
        if (!DungeonNpcDialogues.isOurs(dialogueId)) {
            return false;
        }
        DialogueDefinition definition;
        try {
            definition = GSON.fromJson(json, DialogueDefinition.class);
        } catch (JsonParseException exception) {
            TensuraDungeonsUnbound.LOGGER.error("[DU-NPC] Could not read dialogue {}", dialogueId, exception);
            DialogueNetwork.skip(newSessionId);
            return true;
        }
        if (definition == null || definition.nodes == null) {
            DialogueNetwork.skip(newSessionId);
            return true;
        }
        sessionId = newSessionId;
        screen = new DungeonDialogueScreen(newSessionId, definition);
        Minecraft.getInstance().setScreen(screen);
        return true;
    }

    public static boolean nodeState(UUID targetSession, String nodeId, List<Boolean> availability) {
        if (sessionId == null || !sessionId.equals(targetSession)) {
            return false;
        }
        if (END_NODE.equals(nodeId)) {
            UUID finished = sessionId;
            close();
            DialogueNetwork.finish(finished);
            return true;
        }
        if (screen != null) {
            screen.showNode(nodeId, availability);
        }
        return true;
    }

    public static boolean cancel(UUID targetSession) {
        if (sessionId == null || !sessionId.equals(targetSession)) {
            return false;
        }
        close();
        return true;
    }

    static void choose(String nodeId, int choiceIndex) {
        if (sessionId != null) {
            DialogueNetwork.choose(sessionId, nodeId, choiceIndex);
        }
    }

    static void advance(String nodeId) {
        if (sessionId != null) {
            DialogueNetwork.advanceNode(sessionId, nodeId);
        }
    }

    static void screenRemoved(DungeonDialogueScreen removed) {
        if (removed != screen || sessionId == null) {
            return;
        }
        UUID skipped = sessionId;
        sessionId = null;
        screen = null;
        DialogueNetwork.skip(skipped);
    }

    public static void reset() {
        sessionId = null;
        screen = null;
    }

    private static void close() {
        DungeonDialogueScreen open = screen;
        sessionId = null;
        screen = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (open != null && minecraft.screen == open) {
            minecraft.setScreen(null);
        }
    }
}
