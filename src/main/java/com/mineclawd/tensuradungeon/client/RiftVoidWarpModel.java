package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.entity.RiftVoidWarpEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.model.GeoModel;

public final class RiftVoidWarpModel extends GeoModel<RiftVoidWarpEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID,
        "geo/entity/rift_magic_circle.geo.json"
    );
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID,
        "textures/entity/rift_magic_circle.png"
    );
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID,
        "animations/entity/rift_magic_circle.animation.json"
    );

    public RiftVoidWarpModel() {
        AnimatableTexture.setAndUpdate(TEXTURE);
    }

    @Override
    public ResourceLocation getModelResource(RiftVoidWarpEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RiftVoidWarpEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RiftVoidWarpEntity entity) {
        return ANIMATION;
    }
}
