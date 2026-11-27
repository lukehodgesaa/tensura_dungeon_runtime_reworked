package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.ExpeditionTableBlock;
import com.mineclawd.tensuradungeon.block.entity.ExpeditionTableBlockEntity;
import com.mineclawd.tensuradungeon.network.ExpeditionTableDialogueOpenPayload;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT)
public final class ExpeditionTableDialogueClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpeditionTableDialogueClient.class);

    private static final boolean DEBUG_CAMERA = Boolean.getBoolean("tensura_dungeon.debugCamera");

    private static final double PROMPT_RADIUS_SQUARED = 100.0D;
    private static final double DIALOGUE_FOV = 52.0D;
    private static final long CAMERA_TRANSITION_DURATION_NANOS = 650_000_000L;

    private static BlockPos activeTable;
    private static ArmorStand cameraAnchor;
    private static Entity previousCamera;
    private static Vec3 destinationEye;
    private static Vec3 rimuruFace;
    private static float lockedDialogueYaw;
    private static float lockedDialoguePitch;
    private static Vec3 cameraTransitionStartPosition;
    private static float cameraTransitionStartYaw;
    private static float cameraTransitionStartPitch;
    private static long cameraTransitionStartNanos;
    private static boolean cameraHandoffConfirmed;
    private static boolean cameraAnchorSpawned;
    private static Object lastLoggedCameraIdentity;

    private ExpeditionTableDialogueClient() {
    }

    public static void handle(
        ExpeditionTableDialogueOpenPayload payload,
        IPayloadContext context
    ) {
        context.enqueueWork(() -> begin(payload.tablePos()));
    }

    public static boolean isDirectCameraOverrideActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return cameraAnchor != null
            && destinationEye != null
            && rimuruFace != null
            && minecraft.screen instanceof ExpeditionTableDialogueScreen;
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

    public static void playDialogueVoiceCue() {
        playDialogueVoiceCue("");
    }

    public static void playDialogueVoiceCue(String dialogueText) {
        DialogueVoiceClient.playAt(rimuruFace, "Rimuru", dialogueText);
    }

    static void playDialogueVoiceCueAt(Vec3 voicePosition) {
        playDialogueVoiceCueAt(voicePosition, "", "");
    }

    static void playDialogueVoiceCueAt(Vec3 voicePosition, String speakerName) {
        playDialogueVoiceCueAt(voicePosition, speakerName, "");
    }

    static void playDialogueVoiceCueAt(
        Vec3 voicePosition,
        String speakerName,
        String dialogueText
    ) {
        DialogueVoiceClient.playAt(voicePosition, speakerName, dialogueText);
    }

    public static void begin(BlockPos tablePos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
            || !minecraft.level.getBlockState(tablePos).is(ModBlocks.EXPEDITION_TABLE.get())) {
            return;
        }
        if (tablePos.equals(activeTable) && cameraAnchor != null
            && minecraft.screen instanceof ExpeditionTableDialogueScreen) {
            return;
        }
        stopCinematic("begin() replacing previous dialogue state");

        activeTable = tablePos.immutable();
        setDialogueAnimation(activeTable, true);
        previousCamera = minecraft.getCameraEntity();

        rimuruFace = new Vec3(
            tablePos.getX() + 0.5D,
            tablePos.getY() + 1.75D,
            tablePos.getZ() + 1.46875D
        );
        destinationEye = new Vec3(
            tablePos.getX() + 0.5D,
            tablePos.getY() + 1.92D,
            tablePos.getZ() - 0.05D
        );

        lockedDialogueYaw = yawTo(destinationEye, rimuruFace);
        lockedDialoguePitch = pitchTo(destinationEye, rimuruFace);
        captureCameraTransitionStart(minecraft, previousCamera);
        cameraAnchor = new ArmorStand(minecraft.level, destinationEye.x, destinationEye.y, destinationEye.z);
        cameraAnchor.setInvisible(true);
        cameraAnchor.setNoGravity(true);
        syncCameraAnchorPose(cameraAnchor, destinationEye, lockedDialogueYaw, lockedDialoguePitch);
        minecraft.level.addFreshEntity(cameraAnchor);
        cameraAnchorSpawned = true;
        minecraft.setCameraEntity(cameraAnchor);
        cameraHandoffConfirmed = false;
        lastLoggedCameraIdentity = null;
        minecraft.setScreen(new ExpeditionTableDialogueScreen(activeTable));
        playDialogueVoiceCue(Component.translatable(
            "gui.tensura_dungeon.expedition_table.dialogue.greeting"
        ).getString());

        logCameraState("begin() end", minecraft, true);
    }

    public static void stopCinematic() {
        stopCinematic("unspecified");
    }

    public static void stopCinematic(String reason) {
        Minecraft minecraft = Minecraft.getInstance();
        if (DEBUG_CAMERA && cameraAnchor != null) {
            LOGGER.info(
                "[ExpeditionCamera] stopCinematic reason={} activeTable={} anchorId={}",
                reason, activeTable, System.identityHashCode(cameraAnchor)
            );
        }
        if (activeTable != null) {
            setDialogueAnimation(activeTable, false);
        }
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
        activeTable = null;
        cameraAnchor = null;
        previousCamera = null;
        destinationEye = null;
        rimuruFace = null;
        lockedDialogueYaw = 0.0F;
        lockedDialoguePitch = 0.0F;
        cameraTransitionStartPosition = null;
        cameraTransitionStartYaw = 0.0F;
        cameraTransitionStartPitch = 0.0F;
        cameraTransitionStartNanos = 0L;
        cameraHandoffConfirmed = false;
        cameraAnchorSpawned = false;
        lastLoggedCameraIdentity = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (cameraAnchor == null) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null
            || !(minecraft.screen instanceof ExpeditionTableDialogueScreen)) {
            stopCinematic("client tick: invalid player/level/screen state");
            return;
        }

        logCameraState("ClientTickEvent.Post before reclaim", minecraft, false);

        if (!cameraHandoffConfirmed || minecraft.getCameraEntity() != cameraAnchor) {
            minecraft.setCameraEntity(cameraAnchor);
            cameraHandoffConfirmed = true;
        }

        logCameraState("ClientTickEvent.Post after reclaim", minecraft, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void keepDialogueCamera(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (cameraAnchor == null || !(minecraft.screen instanceof ExpeditionTableDialogueScreen)) {
            return;
        }
        logCameraState("RenderFrameEvent.Pre before reclaim", minecraft, false);
        if (minecraft.getCameraEntity() != cameraAnchor) {
            minecraft.setCameraEntity(cameraAnchor);
        }
        cameraHandoffConfirmed = true;
        logCameraState("RenderFrameEvent.Pre after reclaim", minecraft, false);
    }

    @SubscribeEvent
    public static void adjustDialogueFov(ViewportEvent.ComputeFov event) {
        if (cameraAnchor == null) {
            return;
        }
        event.setFOV(DIALOGUE_FOV);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void lockDialogueCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (cameraAnchor == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() != cameraAnchor) {
            if (DEBUG_CAMERA) {
                LOGGER.warn(
                    "[ExpeditionCamera] ComputeCameraAngles saw camera={} instead of anchor={}; "
                        + "reclaiming before applying dialogue angles",
                    describeCamera(minecraft.getCameraEntity()), System.identityHashCode(cameraAnchor)
                );
            }
            minecraft.setCameraEntity(cameraAnchor);
            cameraHandoffConfirmed = true;
            if (minecraft.getCameraEntity() != cameraAnchor) {
                return;
            }
        }
        event.setYaw(lockedDialogueYaw);
        event.setPitch(lockedDialoguePitch);
        event.setRoll(0.0F);
    }

    @SubscribeEvent
    public static void renderUsePrompt(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
            || minecraft.options.hideGui || !(minecraft.hitResult instanceof BlockHitResult blockHit)
            || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos hitPos = blockHit.getBlockPos();
        var hitState = minecraft.level.getBlockState(hitPos);
        if (!ExpeditionTableBlock.isMasterOrPart(hitState)) {
            return;
        }
        BlockPos masterPos = ExpeditionTableBlock.resolveMaster(hitPos, hitState);
        if (!minecraft.level.getBlockState(masterPos).is(ModBlocks.EXPEDITION_TABLE.get())
            || minecraft.player.distanceToSqr(Vec3.atCenterOf(masterPos)) > PROMPT_RADIUS_SQUARED) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Component prompt = Component.translatable("gui.tensura_dungeon.expedition_table.prompt");
        int centerX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 18;
        int width = minecraft.font.width(prompt) + 14;
        graphics.fill(centerX - width / 2, y - 4, centerX + width / 2, y + 13, 0xB0060A0F);
        graphics.fill(centerX - width / 2, y - 4, centerX + width / 2, y - 3, 0xDD64D9FF);
        graphics.drawCenteredString(minecraft.font, prompt, centerX, y, 0xFFF1F7FF);
    }

    private static void logCameraState(String site, Minecraft minecraft, boolean force) {
        if (!DEBUG_CAMERA) {
            return;
        }
        Entity active = minecraft.getCameraEntity();
        Object identity = active == null ? null : System.identityHashCode(active);
        if (!force && java.util.Objects.equals(identity, lastLoggedCameraIdentity)) {
            return;
        }
        lastLoggedCameraIdentity = identity;
        LOGGER.info(
            "[ExpeditionCamera] {} activeTable={} eye={} target={} yaw={} pitch={} "
                + "transitionStart={} transitionProgress={} transitionDurationMs={} "
                + "anchor={} anchorRemoved={} activeCamera={} screen={}",
            site,
            activeTable,
            destinationEye,
            rimuruFace,
            lockedDialogueYaw,
            lockedDialoguePitch,
            cameraTransitionStartPosition,
            cameraTransitionStartPosition == null ? 1.0F : easedCameraTransitionProgress(),
            CAMERA_TRANSITION_DURATION_NANOS / 1_000_000L,
            cameraAnchor == null ? "null" : System.identityHashCode(cameraAnchor),
            cameraAnchor != null && cameraAnchor.isRemoved(),
            describeCamera(active),
            minecraft.screen == null ? "null" : minecraft.screen.getClass().getSimpleName()
        );
    }

    private static String describeCamera(Entity camera) {
        if (camera == null) {
            return "null";
        }
        return camera.getClass().getSimpleName() + "#" + System.identityHashCode(camera)
            + "@" + camera.getX() + "," + camera.getY() + "," + camera.getZ();
    }

    private static void setDialogueAnimation(BlockPos tablePos, boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
            && minecraft.level.getBlockEntity(tablePos) instanceof ExpeditionTableBlockEntity table) {
            table.setClientDialogueActive(active);
        }
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

    private static void setCameraEye(ArmorStand anchor, Vec3 eye) {
        anchor.setPos(eye.x, eye.y - anchor.getEyeHeight(), eye.z);
    }

    private static void syncCameraAnchorPose(
        ArmorStand anchor,
        Vec3 eye,
        float yaw,
        float pitch
    ) {
        setCameraEye(anchor, eye);
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
