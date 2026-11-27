package com.mineclawd.dungeonsunbound.npc.client;

import com.mineclawd.dungeonsunbound.npc.DungeonNpc;
import com.mineclawd.dungeonsunbound.npc.DungeonNpcProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class DungeonNpcRenderer extends EntityRenderer<DungeonNpc> {
    private final GeoEntityRenderer<DungeonNpc> gecko;
    private final HumanoidMobRenderer<DungeonNpc, PlayerModel<DungeonNpc>> player;

    public DungeonNpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.gecko = new GeoEntityRenderer<>(context, new DungeonNpcGeoModel());
        this.player = new HumanoidMobRenderer<>(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F) {
            @Override
            public ResourceLocation getTextureLocation(DungeonNpc npc) {
                return npc.profile().texture();
            }

            @Override
            protected boolean shouldShowName(DungeonNpc npc) {
                return false;
            }
        };
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(DungeonNpc npc, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (npc.profile().model() == DungeonNpcProfile.Model.PLAYER) {
            player.render(npc, yaw, partialTick, poseStack, buffers, light);
        } else {
            gecko.render(npc, yaw, partialTick, poseStack, buffers, light);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(DungeonNpc npc) {
        return npc.profile().texture();
    }
}
