package com.mineclawd.dungeonsunbound.client;

import com.mineclawd.dungeonsunbound.TensuraDungeonsUnbound;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = TensuraDungeonsUnbound.MOD_ID, value = Dist.CLIENT)
public final class DungeonScreenScale {
    private static final int MIN_WIDTH = 800;
    private static final int MIN_HEIGHT = 450;
    private static final String SCREEN_PREFIX = "com.mineclawd.tensuradungeon.client.DungeonPreparation";
    private static final String LDLIB_SCREEN_PACKAGE = "dev.luke.tensuradungeon.client.";
    private static final String LDLIB_SCREEN_SUFFIX = "LdLib2Screen";

    private static boolean overridden;

    private DungeonScreenScale() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInitPre(ScreenEvent.Init.Pre event) {
        Screen screen = event.getScreen();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null && minecraft.screen != screen) {
            return;
        }
        if (applyScale(minecraft, screen) || isDungeonScreen(screen)) {
            Window window = minecraft.getWindow();
            screen.width = window.getGuiScaledWidth();
            screen.height = window.getGuiScaledHeight();
        }
    }

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        Minecraft minecraft = Minecraft.getInstance();
        if (screen != minecraft.screen || !isDungeonScreen(screen)) {
            return;
        }
        if (applyScale(minecraft, screen)) {
            Window window = minecraft.getWindow();
            screen.resize(minecraft, window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
    }

    @SubscribeEvent
    public static void onClosing(ScreenEvent.Closing event) {
        if (isDungeonScreen(event.getScreen())) {
            applyScale(Minecraft.getInstance(), null);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isDungeonScreen(minecraft.screen) && applyScale(minecraft, minecraft.screen) && minecraft.screen != null) {
            Window window = minecraft.getWindow();
            minecraft.screen.resize(minecraft, window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (isDungeonScreen(screen)) {
            return;
        }
        if (applyScale(minecraft, screen) && screen != null) {
            Window window = minecraft.getWindow();
            screen.resize(minecraft, window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
    }

    private static boolean applyScale(Minecraft minecraft, Screen screen) {
        boolean dungeon = isDungeonScreen(screen);
        if (!dungeon && !overridden) {
            return false;
        }
        Window window = minecraft.getWindow();
        int option = optionScale(minecraft);
        int desired = dungeon ? fittedScale(minecraft, option) : option;
        overridden = desired != option;
        if (window.getGuiScale() == desired) {
            return false;
        }
        window.setGuiScale(desired);
        return true;
    }

    private static int optionScale(Minecraft minecraft) {
        return minecraft.getWindow().calculateScale(
            minecraft.options.guiScale().get(), minecraft.options.forceUnicodeFont().get()
        );
    }

    private static int fittedScale(Minecraft minecraft, int option) {
        Window window = minecraft.getWindow();
        int scale = option;
        while (scale > 1 && (window.getWidth() / scale < MIN_WIDTH || window.getHeight() / scale < MIN_HEIGHT)) {
            scale--;
        }
        return scale;
    }

    private static boolean isDungeonScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        String name = screen.getClass().getName();
        return name.startsWith(SCREEN_PREFIX)
            || (name.startsWith(LDLIB_SCREEN_PACKAGE) && name.endsWith(LDLIB_SCREEN_SUFFIX));
    }
}
