package com.mineclawd.tensurahudnative.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ClientHudController {
    private static final KeyMapping TOGGLE = new KeyMapping(
            "key.tensura_hud_native.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.tensura_hud_native"
    );
    private static final Set<ResourceLocation> HOTBAR_LAYERS = layers("HOTBAR", "SELECTED_ITEM_NAME");
    private static final Set<ResourceLocation> VITAL_LAYERS = layers("PLAYER_HEALTH", "FOOD_LEVEL", "AIR_LEVEL");
    private static final Set<ResourceLocation> ARMOR_LAYERS = layers("ARMOR_LEVEL");
    private static final Set<ResourceLocation> EXPERIENCE_LAYERS = layers("EXPERIENCE_BAR", "EXPERIENCE_LEVEL");
    private static int lastSelectedSlot = -1;
    private static long hotbarVisibleUntil;
    private static Boolean originalTensuraHud;
    private static Boolean appliedTensuraHud;

    private ClientHudController() {
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        RuntimeCosmeticProvider.sync();
        HudSettings settings = HudSettings.get();
        syncTensuraHud(settings);
        while (TOGGLE.consumeClick()) {
            HudPreviewController.cancel();
            settings.toggle();
            syncTensuraHud(settings);
            if (minecraft.player != null) {
                Object message = ReflectionUtil.callStatic(
                        "net.minecraft.network.chat.Component",
                        "literal",
                        "Tensura Native HUD: " + (settings.enabled ? "Enabled" : "Disabled"));
                ReflectionUtil.call(minecraft.player, "displayClientMessage", message, true);
            }
        }
    }

    private static void syncTensuraHud(HudSettings settings) {
        Object config = ReflectionUtil.staticField(
                "io.github.manasmods.tensura.handler.client.OverlayHandler", "cfg");
        if (config == null) {
            return;
        }
        boolean current = ReflectionUtil.bool(
                ReflectionUtil.call(config, "isTensuraHud"),
                ReflectionUtil.bool(readField(config, "tensuraHud"), false)
        );
        if (originalTensuraHud == null) {
            originalTensuraHud = current;
        }
        boolean questOnly = HudThemes.active(settings).overlayPolicy().equals(HudTheme.OverlayPolicy.questOnly());
        boolean desired;
        if (HudPreviewController.isActive()) {
            desired = false;
        } else if (settings.enabled) {
            desired = questOnly;
        } else {
            desired = originalTensuraHud;
        }
        if (appliedTensuraHud == null || appliedTensuraHud != desired || current != desired) {
            if (ReflectionUtil.setField(config, "tensuraHud", desired)) {
                appliedTensuraHud = desired;
            }
        }
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        HudSettings settings = HudSettings.get();
        boolean previewActive = HudPreviewController.isActive();
        if ((!settings.enabled && !previewActive) || player == null || minecraft.options == null
                || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        long now = System.currentTimeMillis();
        int selectedSlot = player.getInventory().selected;
        if (lastSelectedSlot >= 0 && selectedSlot != lastSelectedSlot) {
            hotbarVisibleUntil = now + 1_800L;
        }
        lastSelectedSlot = selectedSlot;
        TensuraDataProvider.Sample sample = TensuraDataProvider.sample(minecraft, player, settings, now);
        HudSnapshot snapshot = sample.snapshot();
        NativeHudRenderer.render(event.getGuiGraphics(), event.getPartialTick(), minecraft, snapshot, settings, now,
                hotbarVisibleUntil > now, previewActive ? HudPreviewController.remainingSeconds(now) : 0);
    }

    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        HudSettings settings = HudSettings.get();
        Minecraft minecraft = Minecraft.getInstance();
        boolean previewActive = HudPreviewController.isActive();
        if ((!settings.enabled && !previewActive) || minecraft.player == null
                || minecraft.options == null || minecraft.options.hideGui) {
            return;
        }
        ResourceLocation layer = event.getName();
        if (layer == null) {
            return;
        }
        HudTheme.OverlayPolicy policy = HudThemes.active(settings).overlayPolicy();
        boolean hide = (policy.replaceVanillaHotbar()
                && (previewActive || settings.hideVanillaHotbar) && HOTBAR_LAYERS.contains(layer))
                || (policy.replaceVanillaVitals()
                && (previewActive || settings.hideVanillaVitals) && VITAL_LAYERS.contains(layer))
                || (policy.replaceVanillaArmor()
                && (previewActive || settings.hideVanillaArmor) && ARMOR_LAYERS.contains(layer))
                || (policy.replaceVanillaExperience()
                && (previewActive || settings.hideVanillaExperience) && EXPERIENCE_LAYERS.contains(layer));
        if (hide) {
            event.setCanceled(true);
        }
    }

    private static Set<ResourceLocation> layers(String... fieldNames) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (String fieldName : fieldNames) {
            try {
                Field field = VanillaGuiLayers.class.getField(fieldName);
                Object value = field.get(null);
                if (value instanceof ResourceLocation location) {
                    result.add(location);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Set.copyOf(result);
    }
}
