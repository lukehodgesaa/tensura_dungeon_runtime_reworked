package com.mineclawd.tensuraadditions.client;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import com.mineclawd.tensuraadditions.entity.DungeonExitPortalEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class DungeonExitPortalModel extends GeoModel<DungeonExitPortalEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
        TensuraAdditions.MOD_ID, "geo/dungeon_exit_portal.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        TensuraAdditions.MOD_ID, "textures/entity/dungeon_exit_portal.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
        TensuraAdditions.MOD_ID, "animations/dungeon_exit_portal.animation.json");

    @Override
    public ResourceLocation getModelResource(DungeonExitPortalEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DungeonExitPortalEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DungeonExitPortalEntity entity) {
        return ANIMATION;
    }
}
