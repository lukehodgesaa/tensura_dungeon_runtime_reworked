package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.registry.ModSoundEvents;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

final class DialogueVoiceClient {
    private static final long CUE_COOLDOWN_NANOS = 100_000_000L;
    private static long lastPlayedNanos;
    private static boolean nextVariantB;

    private DialogueVoiceClient() {
    }

    static void playAt(Vec3 voicePosition, String speakerName, String dialogueText) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || voicePosition == null) {
            return;
        }

        long now = System.nanoTime();
        if (lastPlayedNanos != 0L && now - lastPlayedNanos < CUE_COOLDOWN_NANOS) {
            return;
        }

        String character = characterKey(speakerName);
        String pool = DialogueVoiceIntent.fromText(dialogueText).id();
        minecraft.level.playLocalSound(
            voicePosition.x,
            voicePosition.y,
            voicePosition.z,
            ModSoundEvents.dialogueVoicePool(character, pool, nextVariantB).get(),
            SoundSource.VOICE,
            0.75F,
            1.0F,
            false
        );
        nextVariantB = !nextVariantB;
        lastPlayedNanos = now;
    }

    private static String characterKey(String speakerName) {
        String normalized = speakerName == null
            ? ""
            : speakerName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.contains("gobta")) {
            return "gobta";
        }
        if (normalized.contains("kaijin")) {
            return "kaijin";
        }
        if (normalized.contains("kurobe") || normalized.contains("kurobei")) {
            return "kurobe";
        }
        if (normalized.contains("mjmile") || normalized.contains("mjollmile")) {
            return "mjmile";
        }
        if (normalized.contains("shuna")) {
            return "shuna";
        }
        if (normalized.contains("treyni")) {
            return "treyni";
        }
        return "fallback";
    }
}
