package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.network.DungeonHubMusicStatePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, value = Dist.CLIENT)
public final class DungeonHubMusicController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DungeonHubMusicController.class);
    private static final List<DungeonHubMusicSound> ACTIVE = new ArrayList<>();
    private static boolean inHub;
    private static String lastTrackKey;
    private static long rotationDeadlineMillis;
    private static float lastConfiguredVolume = -1.0F;
    private static boolean startBlocked;

    private DungeonHubMusicController() {
    }

    public static boolean inHub() {
        return inHub;
    }

    public static void handle(DungeonHubMusicStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (inHub != payload.inHub()) {
                LOGGER.info("[TD-MUSIC] Hub presence changed: inHub={}", payload.inHub());
            }
            inHub = payload.inHub();
            startBlocked = false;
        });
    }

    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        if (inHub && DungeonHubMusicConfig.ENABLED.get()) {
            event.setMusic(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stopAll();
            inHub = false;
            return;
        }
        if (!DungeonHubMusicConfig.ENABLED.get()) {
            fadeOutAll();
            return;
        }
        if (inHub) {
            restoreOrStart(minecraft);
            rotateWhenDue(minecraft);
            applyVolumeChanges();
        } else {
            fadeOutAll();
        }
        prune();
    }

    private static void restoreOrStart(Minecraft minecraft) {
        if (ACTIVE.isEmpty()) {
            if (!startBlocked) {
                startTrack(minecraft, null);
            }
            return;
        }
        boolean allFadingOut = ACTIVE.stream().allMatch(DungeonHubMusicSound::isFadingOut);
        if (allFadingOut) {
            float volume = DungeonHubMusicConfig.VOLUME.get().floatValue();
            DungeonHubMusicSound latest = ACTIVE.getLast();
            latest.fadeTo(volume);
            for (int index = 0; index < ACTIVE.size() - 1; index++) {
                ACTIVE.get(index).fadeTo(0.0F);
            }
        }
    }

    private static void rotateWhenDue(Minecraft minecraft) {
        if (ACTIVE.size() != 1) {
            return;
        }
        if (System.currentTimeMillis() >= rotationDeadlineMillis) {
            startTrack(minecraft, lastTrackKey);
        }
    }

    private static void startTrack(Minecraft minecraft, String previousKey) {
        Map<String, Integer> weights = DungeonHubMusicConfig.rotationWeights();
        DungeonHubMusicCatalog.Track track = DungeonHubMusicCatalog.nextTrack(
            previousKey,
            weights,
            minecraft.level.random
        );
        SoundEvent soundEvent = soundEvent(track);
        if (soundEvent == null) {
            LOGGER.warn("[TD-MUSIC] Skipping unavailable hub track '{}'", track.soundId());
            startBlocked = true;
            return;
        }
        float volume = DungeonHubMusicConfig.VOLUME.get().floatValue();
        float fadeSeconds = DungeonHubMusicConfig.FADE_SECONDS.get().floatValue();
        DungeonHubMusicSound sound = new DungeonHubMusicSound(
            soundEvent,
            soundSource(),
            volume,
            fadeSeconds
        );
        LOGGER.info("[TD-MUSIC] Hub music started: {} ({})", track.displayName(), track.soundId());
        ACTIVE.add(sound);
        minecraft.getSoundManager().play(sound);
        if (previousKey != null) {
            for (DungeonHubMusicSound active : ACTIVE) {
                if (active != sound) {
                    active.fadeTo(0.0F);
                }
            }
        }
        lastTrackKey = track.key();
        rotationDeadlineMillis = System.currentTimeMillis()
            + (long) Math.ceil(track.durationSeconds() * 1000.0D);
    }

    private static SoundSource soundSource() {
        return switch (DungeonHubMusicConfig.SOUND_SOURCE.get()) {
            case "music" -> SoundSource.MUSIC;
            case "ambient" -> SoundSource.AMBIENT;
            default -> SoundSource.RECORDS;
        };
    }

    private static SoundEvent soundEvent(DungeonHubMusicCatalog.Track track) {
        ResourceLocation id = ResourceLocation.tryParse(track.soundId());
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
    }

    private static void fadeOutAll() {
        for (DungeonHubMusicSound sound : ACTIVE) {
            if (!sound.isFadingOut()) {
                sound.fadeTo(0.0F);
            }
        }
    }

    private static void applyVolumeChanges() {
        float configured = DungeonHubMusicConfig.VOLUME.get().floatValue();
        if (lastConfiguredVolume < 0.0F) {
            lastConfiguredVolume = configured;
            return;
        }
        if (Math.abs(lastConfiguredVolume - configured) <= 0.001F) {
            return;
        }
        lastConfiguredVolume = configured;
        for (DungeonHubMusicSound sound : ACTIVE) {
            if (!sound.isFadingOut()
                && sound.isAtTarget()
                && Math.abs(sound.targetVolume() - configured) > 0.001F) {
                sound.fadeTo(configured);
            }
        }
    }

    private static void stopAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            ACTIVE.clear();
            return;
        }
        for (DungeonHubMusicSound sound : ACTIVE) {
            minecraft.getSoundManager().stop(sound);
        }
        ACTIVE.clear();
        lastTrackKey = null;
        rotationDeadlineMillis = 0L;
        lastConfiguredVolume = -1.0F;
        startBlocked = false;
    }

    private static void prune() {
        ACTIVE.removeIf(DungeonHubMusicSound::isStopped);
    }
}
