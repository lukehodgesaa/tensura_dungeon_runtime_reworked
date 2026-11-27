package com.mineclawd.tensuraadditions.client;

import com.mineclawd.tensuraadditions.entity.DungeonExitPortalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class DungeonExitPortalRenderer extends GeoEntityRenderer<DungeonExitPortalEntity> {
    public DungeonExitPortalRenderer(EntityRendererProvider.Context context) {
        super(context, new DungeonExitPortalModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public RenderType getRenderType(
            DungeonExitPortalEntity entity,
            ResourceLocation texture,
            @Nullable MultiBufferSource bufferSource,
            float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
