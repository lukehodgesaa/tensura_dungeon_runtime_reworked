package com.mineclawd.dungeonsunbound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public record HubManifest(
    BlockPos spawnAnchor,
    BlockPos returnAnchor,
    BlockPos gateAnchor,
    BlockPos safeZoneCornerA,
    BlockPos safeZoneCornerB,
    int totalBlocks,
    List<Chunk> chunks
) {
    public static final ResourceLocation LOCATION =
        ResourceLocation.fromNamespaceAndPath(TensuraDungeonsUnbound.MOD_ID, "hub/manifest.json");

    public record Chunk(int x, int z, int originY, ResourceLocation template, int blocks, int entities) {
        public BlockPos origin() {
            return new BlockPos(x << 4, originY, z << 4);
        }
    }

    public List<Chunk> entityChunks() {
        return chunks.stream().filter(chunk -> chunk.entities() > 0).toList();
    }

    public static Optional<HubManifest> load(ResourceManager resources) {
        Optional<Resource> resource = resources.getResource(LOCATION);
        if (resource.isEmpty()) {
            TensuraDungeonsUnbound.LOGGER.error("[DU-HUB] Missing hub manifest {}", LOCATION);
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Chunk> chunks = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("chunks")) {
                JsonObject chunk = element.getAsJsonObject();
                chunks.add(new Chunk(
                    chunk.get("x").getAsInt(),
                    chunk.get("z").getAsInt(),
                    chunk.get("y").getAsInt(),
                    ResourceLocation.fromNamespaceAndPath(
                        TensuraDungeonsUnbound.MOD_ID, chunk.get("template").getAsString()
                    ),
                    chunk.get("blocks").getAsInt(),
                    chunk.get("entities").getAsInt()
                ));
            }
            return Optional.of(new HubManifest(
                pos(root.getAsJsonArray("hubSpawnAnchor")),
                pos(root.getAsJsonArray("hubReturnAnchor")),
                pos(root.getAsJsonArray("hubGateAnchor")),
                pos(root.getAsJsonArray("hubSafeZoneCornerA")),
                pos(root.getAsJsonArray("hubSafeZoneCornerB")),
                root.get("totalBlocks").getAsInt(),
                List.copyOf(chunks)
            ));
        } catch (IOException | RuntimeException exception) {
            TensuraDungeonsUnbound.LOGGER.error("[DU-HUB] Unreadable hub manifest {}", LOCATION, exception);
            return Optional.empty();
        }
    }

    private static BlockPos pos(JsonArray array) {
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }
}
