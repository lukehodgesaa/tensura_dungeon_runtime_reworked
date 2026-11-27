package com.mineclawd.tensuraadditions.item;

import com.mineclawd.tensuraadditions.client.SoulOrbItemRenderer;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class AnimatedSoulOrbItem extends Item implements GeoItem {
    private static final RawAnimation IDLE =
        RawAnimation.begin().thenLoop("animation.soul_orb.idle_spin_sparkles");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String rarityPath;

    public AnimatedSoulOrbItem(String rarityPath) {
        super(new Item.Properties().stacksTo(1));
        this.rarityPath = rarityPath;
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public String rarityPath() {
        return rarityPath;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private SoulOrbItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new SoulOrbItemRenderer();
                }
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle",
            state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
