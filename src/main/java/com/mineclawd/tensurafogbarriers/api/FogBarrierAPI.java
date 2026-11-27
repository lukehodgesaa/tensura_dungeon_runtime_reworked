package com.mineclawd.tensurafogbarriers.api;

import com.mineclawd.tensurafogbarriers.data.FogAnimationStyle;
import com.mineclawd.tensurafogbarriers.data.FogBarrierPanel;
import com.mineclawd.tensurafogbarriers.data.FogVariant;
import com.mineclawd.tensurafogbarriers.service.FogBarrierService;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class FogBarrierAPI {
    private FogBarrierAPI() {
    }

    public static int createPanel(
        ServerLevel level,
        Vec3 first,
        Vec3 second,
        String type,
        @Nullable ServerPlayer owner
    ) {
        try {
            return FogBarrierService.get(level.getServer()).create(level, first, second, type).id();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return -1;
        }
    }

    public static boolean setVariant(int id, String variant) {
        return id > 0 && FogVariant.byName(variant)
            .map(value -> FogBarrierService.recolorAny(id, value))
            .orElse(false);
    }

    public static boolean setStyle(int id, String style) {
        return id > 0 && FogAnimationStyle.byName(style)
            .map(value -> FogBarrierService.restyleAny(id, value))
            .orElse(false);
    }

    public static boolean setSolid(int id, boolean solid) {
        return id > 0 && FogBarrierService.setSolidAny(id, solid);
    }

    public static boolean removeNow(int id) {
        return id > 0 && FogBarrierService.removeAny(id);
    }

    public static List<FogBarrierPanel> panels(ServerLevel level) {
        return FogBarrierService.get(level.getServer()).panels(level);
    }
}
