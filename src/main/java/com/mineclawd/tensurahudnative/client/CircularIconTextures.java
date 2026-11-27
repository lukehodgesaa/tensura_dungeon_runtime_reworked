package com.mineclawd.tensurahudnative.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

final class CircularIconTextures {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    private CircularIconTextures() {
    }

    static ResourceLocation get(ResourceLocation source, int cropX, int cropY, int cropSize, int outputSize) {
        String key = source + "|" + cropX + "," + cropY + "," + cropSize + "@" + outputSize;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        ResourceLocation baked = bake(source, cropX, cropY, cropSize, outputSize);
        ResourceLocation result = baked != null ? baked : source;
        CACHE.put(key, result);
        return result;
    }

    private static ResourceLocation bake(ResourceLocation source, int cropX, int cropY,
                                         int cropSize, int outputSize) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Resource resource = minecraft.getResourceManager().getResourceOrThrow(source);
            try (InputStream stream = resource.open(); NativeImage raw = NativeImage.read(stream)) {
                NativeImage circular = new NativeImage(outputSize, outputSize, true);
                float scale = cropSize / (float) outputSize;
                float radius = outputSize / 2.0F;
                float edgeSoftness = 1.25F;

                for (int y = 0; y < outputSize; y++) {
                    for (int x = 0; x < outputSize; x++) {
                        float dx = x + 0.5F - radius;
                        float dy = y + 0.5F - radius;
                        float distance = (float) Math.sqrt(dx * dx + dy * dy);
                        float coverage = 1.0F - smoothstep(radius - edgeSoftness, radius, distance);
                        if (coverage <= 0.0F) {
                            circular.setPixelRGBA(x, y, 0);
                            continue;
                        }
                        int sourceX = cropX + Math.min(cropSize - 1, (int) (x * scale));
                        int sourceY = cropY + Math.min(cropSize - 1, (int) (y * scale));
                        int rgba = raw.getPixelRGBA(sourceX, sourceY);
                        circular.setPixelRGBA(x, y, applyCoverage(rgba, coverage));
                    }
                }

                ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(source.getNamespace(),
                        source.getPath() + "_circular_" + cropX + '_' + cropY + '_' + cropSize + '_' + outputSize);
                minecraft.getTextureManager().register(textureId, new DynamicTexture(circular));
                return textureId;
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0.0F, Math.min(1.0F, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
    }

    private static int applyCoverage(int packedRgba, float coverage) {
        int alpha = packedRgba >>> 24;
        int scaledAlpha = Math.round(alpha * coverage);
        return (packedRgba & 0x00FFFFFF) | (scaledAlpha << 24);
    }
}
