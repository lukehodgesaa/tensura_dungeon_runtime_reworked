package com.mineclawd.dungeonsunbound.npc.client;

import com.mineclawd.dungeonsunbound.npc.DungeonNpc;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public final class DungeonNpcGeoModel extends GeoModel<DungeonNpc> {
    @Override
    public ResourceLocation getModelResource(DungeonNpc npc) {
        return npc.profile().geo();
    }

    @Override
    public ResourceLocation getTextureResource(DungeonNpc npc) {
        return npc.profile().texture();
    }

    @Override
    public ResourceLocation getAnimationResource(DungeonNpc npc) {
        return npc.profile().animation();
    }

    @Override
    public void setCustomAnimations(DungeonNpc npc, long instanceId, AnimationState<DungeonNpc> state) {
        super.setCustomAnimations(npc, instanceId, state);
        String boneName = npc.profile().headBone();
        if (boneName.isEmpty()) {
            return;
        }
        GeoBone head = getAnimationProcessor().getBone(boneName);
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head == null || data == null) {
            return;
        }
        head.setRotX(head.getRotX() + data.headPitch() * Mth.DEG_TO_RAD);
        head.setRotY(head.getRotY() + data.netHeadYaw() * Mth.DEG_TO_RAD);
    }
}
