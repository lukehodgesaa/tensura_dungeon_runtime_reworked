package com.mineclawd.tensuraadditions.client;

import com.mineclawd.tensuraadditions.item.AnimatedSoulOrbItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class SoulOrbItemRenderer extends GeoItemRenderer<AnimatedSoulOrbItem> {
    public SoulOrbItemRenderer() {
        super(new SoulOrbItemModel());
    }
}
