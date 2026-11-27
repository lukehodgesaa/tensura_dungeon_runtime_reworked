package com.mineclawd.tensuradungeon.reward;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class DungeonCrystalEconomyRegistry {
    private static final ResourceLocation DEFINITION_ID = ResourceLocation.fromNamespaceAndPath(
        TensuraDungeonRuntime.MOD_ID, "dungeon/rewards/crystal_economy.json"
    );
    private static ResourceManager cachedManager;
    private static DungeonCrystalEconomyDefinition cachedDefinition;

    private DungeonCrystalEconomyRegistry() {}

    public static synchronized DungeonCrystalEconomyDefinition get(ServerLevel level) {
        ResourceManager manager = level.getServer().getResourceManager();
        if (cachedDefinition == null || cachedManager != manager) {
            cachedManager = manager;
            cachedDefinition = load(manager);
        }
        return cachedDefinition;
    }

    private static DungeonCrystalEconomyDefinition load(ResourceManager manager) {
        Resource resource = manager.getResource(DEFINITION_ID).orElseThrow(() ->
            new IllegalStateException("Missing crystal economy definition: " + DEFINITION_ID)
        );
        try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            return DungeonCrystalEconomyDefinition.parse(reader);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid crystal economy definition: " + DEFINITION_ID, exception);
        }
    }
}
