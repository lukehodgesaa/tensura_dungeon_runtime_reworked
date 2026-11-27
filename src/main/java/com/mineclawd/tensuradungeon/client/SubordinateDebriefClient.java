package com.mineclawd.tensuradungeon.client;

import com.mineclawd.dungeonsunbound.npc.client.DungeonDialogueScreen;
import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.SubordinateDebriefPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT)
public final class SubordinateDebriefClient {

    private static final double CAMERA_BACKOFF = 2.1D;
    private static final double MIN_CAMERA_DISTANCE = 1.5D;
    private static final float DIALOGUE_FOV = 52.0F;
    private static final long CAMERA_TRANSITION_DURATION_NANOS = 900_000_000L;

    private static int activeEntityId;
    private static ArmorStand cameraAnchor;
    private static Entity previousCamera;
    private static Vec3 destinationEye;
    private static Vec3 subordinateFace;
    private static float lockedDialogueYaw;
    private static float lockedDialoguePitch;
    private static Vec3 cameraTransitionStartPosition;
    private static float cameraTransitionStartYaw;
    private static float cameraTransitionStartPitch;
    private static long cameraTransitionStartNanos;
    private static boolean cameraAnchorSpawned;
    private static String activeSpeakerName = "";

    private SubordinateDebriefClient() {
    }

    public static void handle(SubordinateDebriefPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> begin(payload));
    }

    public static boolean isDirectCameraOverrideActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return cameraAnchor != null
            && destinationEye != null
            && subordinateFace != null
            && minecraft.screen instanceof DungeonDialogueScreen;
    }

    public static Vec3 directCameraPosition() {
        if (destinationEye == null || cameraTransitionStartPosition == null) {
            return destinationEye;
        }
        return cameraTransitionStartPosition.lerp(destinationEye, easedCameraTransitionProgress());
    }

    public static float directCameraYaw() {
        if (cameraTransitionStartPosition == null) {
            return lockedDialogueYaw;
        }
        return interpolateAngle(
            cameraTransitionStartYaw,
            lockedDialogueYaw,
            easedCameraTransitionProgress()
        );
    }

    public static float directCameraPitch() {
        if (cameraTransitionStartPosition == null) {
            return lockedDialoguePitch;
        }
        return Mth.lerp(
            easedCameraTransitionProgress(),
            cameraTransitionStartPitch,
            lockedDialoguePitch
        );
    }

    static boolean hasDialogueVoicePosition() {
        return subordinateFace != null;
    }

    static void playDialogueVoiceCue(String dialogueText) {
        ExpeditionTableDialogueClient.playDialogueVoiceCueAt(
            subordinateFace,
            activeSpeakerName,
            dialogueText
        );
    }

    static void begin(SubordinateDebriefPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
            || (minecraft.screen != null && !(minecraft.screen instanceof DungeonDialogueScreen))
            || isDirectCameraOverrideActive()) {
            return;
        }
        Entity subordinate = minecraft.level.getEntity(payload.entityId());
        if (subordinate == null || subordinate.isRemoved()) {
            return;
        }

        stopCinematic("begin() replacing previous debrief state");
        activeEntityId = payload.entityId();
        activeSpeakerName = payload.subordinateName();
        previousCamera = minecraft.getCameraEntity();

        subordinateFace = subordinate.getEyePosition(1.0F);
        Vec3 playerEye = minecraft.player.getEyePosition(1.0F);
        Vec3 direction = subordinateFace.subtract(playerEye);
        double horizontal = direction.horizontalDistance();
        if (horizontal < 1.0E-4D) {
            direction = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            direction = direction.normalize();
        }
        double distance = Math.max(MIN_CAMERA_DISTANCE, playerEye.distanceTo(subordinateFace));
        destinationEye = subordinateFace.subtract(direction.scale(Math.min(distance, CAMERA_BACKOFF)));
        destinationEye = new Vec3(destinationEye.x, subordinateFace.y, destinationEye.z);

        lockedDialogueYaw = yawTo(destinationEye, subordinateFace);
        lockedDialoguePitch = pitchTo(destinationEye, subordinateFace);
        captureCameraTransitionStart(minecraft, previousCamera);

        cameraAnchor = new ArmorStand(
            minecraft.level, destinationEye.x, destinationEye.y, destinationEye.z
        );
        cameraAnchor.setInvisible(true);
        cameraAnchor.setNoGravity(true);
        syncCameraAnchorPose(cameraAnchor, destinationEye, lockedDialogueYaw, lockedDialoguePitch);
        minecraft.level.addFreshEntity(cameraAnchor);
        cameraAnchorSpawned = true;
        minecraft.setCameraEntity(cameraAnchor);
    }

    public static void stopCinematic() {
        stopCinematic("screen closed");
    }

    public static void stopCinematic(String reason) {
        Minecraft minecraft = Minecraft.getInstance();
        if (cameraAnchor != null) {
            Entity restore = previousCamera;
            if (restore == null || minecraft.level == null || restore.level() != minecraft.level) {
                restore = minecraft.player;
            }
            if (restore != null) {
                minecraft.setCameraEntity(restore);
            }
            if (cameraAnchorSpawned) {
                cameraAnchor.remove(Entity.RemovalReason.DISCARDED);
            }
        }
        activeEntityId = 0;
        activeSpeakerName = "";
        cameraAnchor = null;
        previousCamera = null;
        destinationEye = null;
        subordinateFace = null;
        lockedDialogueYaw = 0.0F;
        lockedDialoguePitch = 0.0F;
        cameraTransitionStartPosition = null;
        cameraTransitionStartYaw = 0.0F;
        cameraTransitionStartPitch = 0.0F;
        cameraTransitionStartNanos = 0L;
        cameraAnchorSpawned = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (cameraAnchor == null) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null
            || !(minecraft.screen instanceof DungeonDialogueScreen)) {
            stopCinematic("client tick: dialogue screen is no longer active");
            return;
        }
        if (minecraft.getCameraEntity() != cameraAnchor) {
            minecraft.setCameraEntity(cameraAnchor);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void keepDebriefCamera(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (cameraAnchor == null || !(minecraft.screen instanceof DungeonDialogueScreen)) {
            return;
        }
        if (minecraft.getCameraEntity() != cameraAnchor) {
            minecraft.setCameraEntity(cameraAnchor);
        }
    }

    @SubscribeEvent
    public static void adjustDebriefFov(ViewportEvent.ComputeFov event) {
        if (cameraAnchor != null) {
            event.setFOV(DIALOGUE_FOV);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void lockDebriefCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (cameraAnchor == null || minecraft.getCameraEntity() != cameraAnchor) {
            return;
        }
        event.setYaw(lockedDialogueYaw);
        event.setPitch(lockedDialoguePitch);
        event.setRoll(0.0F);
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (cameraAnchor != null && event.getScreen() instanceof DungeonDialogueScreen) {
            stopCinematic("dialogue screen closing");
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stopCinematic("client logged out");
    }

    private static void captureCameraTransitionStart(Minecraft minecraft, Entity fallback) {
        Camera currentCamera = minecraft.gameRenderer.getMainCamera();
        if (currentCamera != null && currentCamera.isInitialized()) {
            cameraTransitionStartPosition = currentCamera.getPosition();
            cameraTransitionStartYaw = currentCamera.getYRot();
            cameraTransitionStartPitch = currentCamera.getXRot();
        } else if (fallback != null) {
            cameraTransitionStartPosition = fallback.getEyePosition(1.0F);
            cameraTransitionStartYaw = fallback.getYRot();
            cameraTransitionStartPitch = fallback.getXRot();
        } else {
            cameraTransitionStartPosition = destinationEye;
            cameraTransitionStartYaw = lockedDialogueYaw;
            cameraTransitionStartPitch = lockedDialoguePitch;
        }
        cameraTransitionStartNanos = System.nanoTime();
    }

    private static float easedCameraTransitionProgress() {
        if (cameraTransitionStartNanos == 0L) {
            return 1.0F;
        }
        double elapsed = (System.nanoTime() - cameraTransitionStartNanos)
            / (double) CAMERA_TRANSITION_DURATION_NANOS;
        double linearProgress = Math.max(0.0D, Math.min(1.0D, elapsed));
        return (float) (1.0D - Math.pow(1.0D - linearProgress, 3.0D));
    }

    private static float interpolateAngle(float start, float end, float progress) {
        return start + Mth.wrapDegrees(end - start) * progress;
    }

    private static void syncCameraAnchorPose(
        ArmorStand anchor,
        Vec3 eye,
        float yaw,
        float pitch
    ) {
        anchor.setPos(eye.x, eye.y - anchor.getEyeHeight(), eye.z);
        anchor.setDeltaMovement(Vec3.ZERO);
        anchor.xo = anchor.getX();
        anchor.yo = anchor.getY();
        anchor.zo = anchor.getZ();
        anchor.setYRot(yaw);
        anchor.setXRot(pitch);
        anchor.yRotO = yaw;
        anchor.xRotO = pitch;
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
