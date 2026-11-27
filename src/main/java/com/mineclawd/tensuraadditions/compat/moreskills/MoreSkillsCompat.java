package com.mineclawd.tensuraadditions.compat.moreskills;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class MoreSkillsCompat {
    public static final String MOD_ID = "tensuramoreskills";
    public static final ResourceLocation CHAOTIC_IRON_MAIDEN_ID = ResourceLocation.fromNamespaceAndPath(
            "tensura_additions",
            "moreskills/beretta/chaotic_iron_maiden"
    );
    public static final ResourceLocation VOID_METEOR_STORM_ID = ResourceLocation.fromNamespaceAndPath(
            "tensura_additions",
            "moreskills/beretta/void_meteor_storm"
    );
    private static final Logger LOGGER = LogUtils.getLogger();

    private MoreSkillsCompat() {
    }

    public static void initialize() {
        if (!isLoaded()) {
            return;
        }

        BerettaAttackRegistry.bootstrap();
        BerettaAttackExecutorRegistry.replace(
                CHAOTIC_IRON_MAIDEN_ID,
                new ChaoticIronMaidenExecutor()
        );
        BerettaAttackExecutorRegistry.replace(
                VOID_METEOR_STORM_ID,
                new VoidMeteorStormExecutor()
        );
        LOGGER.info("TensuraMoreSkills detected; Beretta compatibility definitions are available.");
    }

    public static boolean isLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }
}
