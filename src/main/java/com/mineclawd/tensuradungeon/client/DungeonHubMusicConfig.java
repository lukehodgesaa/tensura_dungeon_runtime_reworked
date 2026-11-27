package com.mineclawd.tensuradungeon.client;

import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class DungeonHubMusicConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue VOLUME;
    public static final ModConfigSpec.DoubleValue FADE_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> SOUND_SOURCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ROTATION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLED = builder
            .comment("Play the Dungeon Hub music rotation while inside the hub.")
            .define("enabled", true);
        VOLUME = builder
            .comment("Master volume for hub music. 0.0 silences it; 1.0 is full volume.")
            .defineInRange("volume", 1.0D, 0.0D, 2.0D);
        FADE_SECONDS = builder
            .comment("Crossfade length in seconds when entering/leaving the hub or switching tracks.")
            .comment("The bundled tracks loop internally without a fade; this only applies between different songs.")
            .defineInRange("fade_seconds", 3.0D, 0.0D, 15.0D);
        SOUND_SOURCE = builder
            .comment("Sound category for hub music: records (Jukebox/Note Blocks slider), music (Music slider), or ambient.")
            .comment("Default records keeps hub music independent of the Minecraft Music slider.")
            .define(
                "sound_source",
                "records",
                value -> value instanceof String text
                    && (text.equals("records") || text.equals("music") || text.equals("ambient"))
            );
        ROTATION = builder
            .comment("Hub rotation as 'key:weight' entries. Higher weight = more frequent.")
            .comment("Valid keys: echoes, inner_hall, labyrinth, ascendant, moment, sanctum.")
            .comment("Default: echoes:3, labyrinth:3, sanctum:3, moment:1, inner_hall:1, ascendant:1.")
            .defineList(
                "rotation",
                DungeonHubMusicCatalog.defaultRotationEntries(),
                entry -> entry instanceof String
            );
        SPEC = builder.build();
    }

    private DungeonHubMusicConfig() {
    }

    public static Map<String, Integer> rotationWeights() {
        return DungeonHubMusicCatalog.parseWeights(ROTATION.get());
    }
}
