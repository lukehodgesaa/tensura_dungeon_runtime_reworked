package com.mineclawd.tensuraadditions.client;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import com.mineclawd.tensuraadditions.item.AnimatedSoulOrbItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class SoulOrbItemModel extends GeoModel<AnimatedSoulOrbItem> {
    private static final ResourceLocation MODEL =
        ResourceLocation.fromNamespaceAndPath(TensuraAdditions.MOD_ID, "geo/soul_orb.geo.json");
    private static final ResourceLocation ANIMATION =
        ResourceLocation.fromNamespaceAndPath(
            TensuraAdditions.MOD_ID, "animations/soul_orb.animation.json");

    @Override
    public ResourceLocation getModelResource(AnimatedSoulOrbItem item) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(AnimatedSoulOrbItem item) {
        return ResourceLocation.fromNamespaceAndPath(
            TensuraAdditions.MOD_ID,
            "textures/item/soul_orb_" + item.rarityPath() + "_model.png");
    }

    @Override
    public ResourceLocation getAnimationResource(AnimatedSoulOrbItem item) {
        return ANIMATION;
    }
}
