package com.mineclawd.tensuradungeon.client;

import com.mineclawd.dungeonsunbound.npc.DungeonNpc;
import com.mineclawd.dungeonsunbound.npc.client.DungeonDialogueClient;
import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT)
public final class NpcInteractionClient {
    private static final double PROMPT_RADIUS = 3.0D;
    private static final long PAN_DURATION_NANOS = 700_000_000L;
    private static final long HOLD_DEADLINE_NANOS = 8_000_000_000L;

    private enum Phase {
        IDLE,
        PANNING_IN,
        HOLDING,
        RETURNING
    }

    private static Phase phase = Phase.IDLE;
    private static int focusEntityId;
    private static float startYaw;
    private static float startPitch;
    private static float targetYaw;
    private static float targetPitch;
    private static long phaseStartNanos;
    private static long holdDeadlineNanos;

    private NpcInteractionClient() {
    }

    private static boolean isCustomNpc(Entity entity) {
        return entity instanceof DungeonNpc;
    }

    public static void playDialogueVoice(String speakerName, String dialogueText) {
        if (SubordinateDebriefClient.hasDialogueVoicePosition()) {
            SubordinateDebriefClient.playDialogueVoiceCue(dialogueText);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Entity npc = minecraft.level.getEntity(focusEntityId);
        if (!(npc instanceof DungeonNpc) || !npc.getName().getString().equals(speakerName)) {
            npc = minecraft.level.getEntitiesOfClass(
                DungeonNpc.class,
                minecraft.player.getBoundingBox().inflate(16.0D),
                candidate -> candidate.getName().getString().equals(speakerName)
            ).stream().min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(minecraft.player))).orElse(null);
        }
        Vec3 voicePosition = npc != null ? npc.getEyePosition(1.0F) : minecraft.player.getEyePosition(1.0F);
        ExpeditionTableDialogueClient.playDialogueVoiceCueAt(voicePosition, speakerName, dialogueText);
    }

    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
            || !(minecraft.hitResult instanceof EntityHitResult hit)) {
            return;
        }
        Entity npc = hit.getEntity();
        if (!isCustomNpc(npc)) {
            return;
        }
        beginFocus(npc);
    }

    private static void beginFocus(Entity npc) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        focusEntityId = npc.getId();
        startYaw = minecraft.player.getYRot();
        startPitch = minecraft.player.getXRot();
        recomputeTargetAngles(minecraft.player.getEyePosition(1.0F), npc);
        phase = Phase.PANNING_IN;
        phaseStartNanos = System.nanoTime();
        holdDeadlineNanos = phaseStartNanos + HOLD_DEADLINE_NANOS;
    }

    private static void recomputeTargetAngles(Vec3 from, Entity npc) {
        Vec3 face = npc.getEyePosition(1.0F);
        targetYaw = yawTo(from, face);
        targetPitch = pitchTo(from, face);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (phase == Phase.IDLE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        Entity npc = minecraft.level == null ? null : minecraft.level.getEntity(focusEntityId);
        boolean dialogueOpen = DungeonDialogueClient.isActive();

        if (npc == null || npc.isRemoved()) {
            phase = Phase.IDLE;
            return;
        }
        if (phase == Phase.PANNING_IN) {
            if (now - phaseStartNanos >= PAN_DURATION_NANOS) {
                phase = Phase.HOLDING;
                phaseStartNanos = now;
            }
        } else if (phase == Phase.HOLDING) {
            if (!dialogueOpen || now >= holdDeadlineNanos) {
                phase = Phase.RETURNING;
                phaseStartNanos = now;
            } else if (minecraft.player != null) {
                recomputeTargetAngles(minecraft.player.getEyePosition(1.0F), npc);
            }
        } else if (phase == Phase.RETURNING) {
            if (now - phaseStartNanos >= PAN_DURATION_NANOS) {
                phase = Phase.IDLE;
            }
        }
    }

    @SubscribeEvent
    public static void applyFocusAngles(ViewportEvent.ComputeCameraAngles event) {
        if (phase == Phase.IDLE
            || ExpeditionTableDialogueClient.isDirectCameraOverrideActive()
            || SubordinateDebriefClient.isDirectCameraOverrideActive()) {
            return;
        }
        event.setYaw(focusYaw());
        event.setPitch(focusPitch());
        event.setRoll(0.0F);
    }

    private static float focusYaw() {
        return switch (phase) {
            case PANNING_IN -> interpolateAngle(startYaw, targetYaw, easedProgress());
            case HOLDING -> targetYaw;
            case RETURNING -> interpolateAngle(targetYaw, startYaw, easedProgress());
            case IDLE -> 0.0F;
        };
    }

    private static float focusPitch() {
        return switch (phase) {
            case PANNING_IN -> Mth.lerp(easedProgress(), startPitch, targetPitch);
            case HOLDING -> targetPitch;
            case RETURNING -> Mth.lerp(easedProgress(), targetPitch, startPitch);
            case IDLE -> 0.0F;
        };
    }

    private static float easedProgress() {
        if (phaseStartNanos == 0L) {
            return 1.0F;
        }
        double elapsed = (System.nanoTime() - phaseStartNanos) / (double) PAN_DURATION_NANOS;
        double linear = Math.max(0.0D, Math.min(1.0D, elapsed));
        return (float) (1.0D - Math.pow(1.0D - linear, 3.0D));
    }

    private static float interpolateAngle(float start, float end, float progress) {
        return start + Mth.wrapDegrees(end - start) * progress;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        phase = Phase.IDLE;
        focusEntityId = 0;
    }

    @SubscribeEvent
    public static void renderTalkPrompt(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
            || minecraft.options.hideGui) {
            return;
        }
        Entity nearest = nearestNpc(minecraft);
        if (nearest == null) {
            return;
        }

        String name = nearest.getDisplayName().getString();
        Component prompt = Component.literal("[talk to " + name + "]");
        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 18;
        int width = minecraft.font.width(prompt) + 14;
        graphics.fill(centerX - width / 2, y - 4, centerX + width / 2, y + 13, 0xB0060A0F);
        graphics.fill(centerX - width / 2, y - 4, centerX + width / 2, y - 3, npcAccentColor(name));
        graphics.drawCenteredString(minecraft.font, prompt, centerX, y, 0xFFF1F7FF);
    }

    private static Entity nearestNpc(Minecraft minecraft) {
        AABB area = minecraft.player.getBoundingBox().inflate(PROMPT_RADIUS);
        List<Entity> npcs = minecraft.level.getEntities(
            (Entity) null,
            area,
            NpcInteractionClient::isCustomNpc
        );
        return npcs.stream()
            .filter(entity -> !entity.isRemoved())
            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(minecraft.player)))
            .orElse(null);
    }

    private static int npcAccentColor(String name) {
        if ("Gobta".equalsIgnoreCase(name)) {
            return 0xDD00AA00;
        }
        if ("Kurobe".equalsIgnoreCase(name)) {
            return 0xDD0000AA;
        }
        if ("Kaijin".equalsIgnoreCase(name)) {
            return 0xDDB87333;
        }
        return 0xDD64D9FF;
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from);
        return (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static float pitchTo(Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from);
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return (float) (-(Mth.atan2(direction.y, horizontal) * Mth.RAD_TO_DEG));
    }
}
