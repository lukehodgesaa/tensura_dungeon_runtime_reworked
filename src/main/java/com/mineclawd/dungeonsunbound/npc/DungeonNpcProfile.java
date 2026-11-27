package com.mineclawd.dungeonsunbound.npc;

import com.mineclawd.dungeonsunbound.TensuraDungeonsUnbound;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record DungeonNpcProfile(
    String id,
    String name,
    String title,
    boolean showName,
    float scale,
    Model model,
    ResourceLocation geo,
    ResourceLocation texture,
    ResourceLocation animation,
    String idleAnimation,
    String headBone,
    ResourceLocation dialogue
) {
    public enum Model {
        GECKO,
        PLAYER
    }

    private static final Map<String, DungeonNpcProfile> PROFILES = new LinkedHashMap<>();

    public static final DungeonNpcProfile GOBTA = add(new DungeonNpcProfile(
        "gobta", "Gobta", "", true, 1.0F, Model.PLAYER, null,
        ResourceLocation.fromNamespaceAndPath("tensuranpc", "textures/entity/humanmale/gobta.png"),
        null, "", "", dialogue("gobta")
    ));
    public static final DungeonNpcProfile TREYNI = add(gecko(
        "treyni", "Treyni", "Guardian of the Jura Forest", false, 1.0F, "treyni", "Idle", "Head"
    ));
    public static final DungeonNpcProfile KAIJIN = add(gecko(
        "kaijin", "Kaijin", "Armor Smith", true, 0.8F, "kaijin", "Idle", "head"
    ));
    public static final DungeonNpcProfile KUROBE = add(gecko(
        "kurobe", "Kurobe", "Blacksmith", true, 1.0F, "kurobe", "Idle", "h_ph_head"
    ));
    public static final DungeonNpcProfile GARD_MJOLLMILE = add(gecko(
        "gard_mjollmile", "Gard Mjollmile", "Dungeon Banker", true, 1.0F, "gard_mjollmile", "idle", "head"
    ));

    public static DungeonNpcProfile get(String id) {
        return PROFILES.getOrDefault(id, GOBTA);
    }

    public static boolean exists(String id) {
        return PROFILES.containsKey(id);
    }

    public static Iterable<String> ids() {
        return PROFILES.keySet();
    }

    private static DungeonNpcProfile gecko(
        String id,
        String name,
        String title,
        boolean showName,
        float scale,
        String asset,
        String idleAnimation,
        String headBone
    ) {
        return new DungeonNpcProfile(
            id, name, title, showName, scale, Model.GECKO,
            additions("geo/" + asset + ".geo.json"),
            additions("textures/entity/" + asset + ".png"),
            additions("animations/" + asset + ".animation.json"),
            idleAnimation, headBone, dialogue(id)
        );
    }

    private static ResourceLocation additions(String path) {
        return ResourceLocation.fromNamespaceAndPath("tensura_additions", path);
    }

    private static ResourceLocation dialogue(String id) {
        return ResourceLocation.fromNamespaceAndPath(TensuraDungeonsUnbound.MOD_ID, "npc/" + id);
    }

    private static DungeonNpcProfile add(DungeonNpcProfile profile) {
        PROFILES.put(profile.id(), profile);
        return profile;
    }
}
