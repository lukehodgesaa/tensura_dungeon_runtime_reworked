package com.mineclawd.tensuraadditions.client;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import com.mineclawd.tensuraadditions.entity.SpawnRuneEntity;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class SpawnRuneModel extends GeoModel<SpawnRuneEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
        TensuraAdditions.MOD_ID, "geo/spawn_rune.geo.json");
    private static final ResourceLocation TEXTURE_PURPLE = ResourceLocation.fromNamespaceAndPath(
        TensuraAdditions.MOD_ID, "textures/entity/spawn_rune_purple.png");
    private static final ResourceLocation TEXTURE_CYAN = ResourceLocation.fromNamespaceAndPath(
        TensuraAdditions.MOD_ID, "textures/entity/spawn_rune_cyan.png");
    private static final ResourceLocation[] TEXTURE_RED_FRAMES = new ResourceLocation[] {
        redFrame(0),
        redFrame(1),
        redFrame(2),
        redFrame(3),
        redFrame(4),
        redFrame(5),
        redFrame(6),
        redFrame(7),
        redFrame(8),
        redFrame(9),
        redFrame(10),
        redFrame(11)
    };
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
        TensuraAdditions.MOD_ID, "animations/spawn_rune.animation.json");

    @Override
    public ResourceLocation getModelResource(SpawnRuneEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(SpawnRuneEntity entity) {
        return switch (entity.getPalette()) {
            case "cyan" -> TEXTURE_CYAN;
            case "red" -> TEXTURE_RED_FRAMES[entity.getTextureFrame()];
            default -> TEXTURE_PURPLE;
        };
    }

    @Override
    public ResourceLocation getAnimationResource(SpawnRuneEntity entity) {
        return ANIMATION;
    }

    private static ResourceLocation redFrame(int frame) {
        return ResourceLocation.fromNamespaceAndPath(
            TensuraAdditions.MOD_ID,
            "textures/entity/spawn_rune_ancient_red_"
                + String.format(Locale.ROOT, "%02d", frame) + ".png");
    }
}
