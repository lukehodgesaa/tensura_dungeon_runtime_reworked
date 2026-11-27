package com.mineclawd.tensuradungeon.client;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.ExpeditionTableBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class ExpeditionTableGeoModel extends GeoModel<ExpeditionTableBlockEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID, "geo/expedition_table.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID, "textures/block/expedition_table.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID, "animations/expedition_table.animation.json");

    @Override
    public ResourceLocation getModelResource(ExpeditionTableBlockEntity blockEntity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ExpeditionTableBlockEntity blockEntity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ExpeditionTableBlockEntity blockEntity) {
        return ANIMATION;
    }

    @Override
    public RenderType getRenderType(ExpeditionTableBlockEntity blockEntity, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
