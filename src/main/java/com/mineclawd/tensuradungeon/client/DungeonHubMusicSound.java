package com.mineclawd.tensuradungeon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class DungeonHubMusicSound extends AbstractTickableSoundInstance {
    private final float fadeTicks;
    private float targetVolume;
    private float fadeStepPerTick;
    private int ticksExisted;

    public DungeonHubMusicSound(
        SoundEvent soundEvent,
        SoundSource soundSource,
        float volume,
        float fadeSeconds
    ) {
        super(soundEvent, soundSource, SoundInstance.createUnseededRandom());
        this.fadeTicks = Math.max(1.0F, fadeSeconds * 20.0F);
        this.volume = 0.0F;
        this.targetVolume = clampVolume(volume);
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
        this.looping = true;
        this.delay = 0;
        this.fadeStepPerTick = this.targetVolume / this.fadeTicks;
    }

    @Override
    public boolean canPlaySound() {
        return true;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        float difference = targetVolume - volume;
        if (Math.abs(difference) <= fadeStepPerTick) {
            volume = targetVolume;
        } else {
            volume += Math.copySign(fadeStepPerTick, difference);
        }
        if (++ticksExisted % 100 == 0 && volume > 0.001F) {
            Minecraft.getInstance().getMusicManager().stopPlaying();
        }
        if (targetVolume <= 0.001F && volume <= 0.001F) {
            stop();
        }
    }

    public void fadeTo(float target) {
        targetVolume = clampVolume(target);
        fadeStepPerTick = Math.abs(targetVolume - volume) / fadeTicks;
    }

    public boolean isFadingOut() {
        return targetVolume <= 0.001F;
    }

    public boolean isAtTarget() {
        return Math.abs(volume - targetVolume) <= 0.001F;
    }

    public float targetVolume() {
        return targetVolume;
    }

    private static float clampVolume(float value) {
        return Math.max(0.0F, Math.min(2.0F, value));
    }
}
