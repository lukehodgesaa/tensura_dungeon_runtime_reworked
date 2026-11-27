package com.mineclawd.dungeonsunbound;

import com.mineclawd.tensuradungeon.registry.ModDimensions;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = TensuraDungeonsUnbound.MOD_ID)
public final class HubBootstrap {
    private static final long BLOCK_BUDGET_NANOS = 15_000_000L;
    private static final int PREFETCH_AHEAD = 16;
    private static final long REST_NANOS_PER_TICK = 35_000_000L;
    private static final int ENTITY_WAIT_TIMEOUT_TICKS = 20 * 30;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final TicketType<ChunkPos> BUILD_TICKET =
        TicketType.create("tensura_dungeons_unbound_hub", Comparator.comparingLong(ChunkPos::toLong), 20 * 15);

    private static HubManifest manifest;
    private static final Map<ResourceLocation, CompletableFuture<StructureTemplate>> PENDING = new HashMap<>();
    private static int restTicks;
    private static int entityWaitTicks;
    private static int lastReportedPercent = -1;
    private static long placeNanos;
    private static int placedChunks;

    private HubBootstrap() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        reset();
        ServerLevel dungeon = server.getLevel(ModDimensions.DUNGEON);
        if (dungeon == null) {
            TensuraDungeonsUnbound.LOGGER.error("[DU-HUB] Dungeon dimension {} is not loaded; hub cannot be built.", ModDimensions.DUNGEON.location());
            return;
        }
        HubBootstrapData data = HubBootstrapData.get(server);
        if (data.state() == HubBootstrapData.State.NONE) {
            if (DungeonRunManager.get(dungeon).getHubSpawnAnchorPos() != null) {
                data.setState(HubBootstrapData.State.SKIPPED_EXISTING_HUB);
                TensuraDungeonsUnbound.LOGGER.info("[DU-HUB] World already has a configured dungeon hub; bootstrap skipped.");
                return;
            }
            data.restart();
            TensuraDungeonsUnbound.LOGGER.info("[DU-HUB] No dungeon hub configured; building the hub.");
        }
        if (isBuilding(data)) {
            manifest = HubManifest.load(server.getResourceManager()).orElse(null);
            if (manifest == null) {
                TensuraDungeonsUnbound.LOGGER.error("[DU-HUB] Hub build cannot continue without its manifest.");
            } else {
                TensuraDungeonsUnbound.LOGGER.info(
                    "[DU-HUB] Hub build {} at chunk {}/{} ({} blocks total).",
                    data.state(), data.nextChunk(), manifest.chunks().size(), manifest.totalBlocks()
                );
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        reset();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (manifest == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel dungeon = server.getLevel(ModDimensions.DUNGEON);
        if (dungeon == null) {
            return;
        }
        HubBootstrapData data = HubBootstrapData.get(server);
        switch (data.state()) {
            case PLACING_BLOCKS -> placeBlocks(server, dungeon, data);
            case PLACING_ENTITIES -> placeEntities(server, dungeon, data);
            default -> reset();
        }
    }

    public static boolean rebuild(MinecraftServer server) {
        HubManifest loaded = HubManifest.load(server.getResourceManager()).orElse(null);
        if (loaded == null) {
            return false;
        }
        reset();
        manifest = loaded;
        HubBootstrapData.get(server).restart();
        return true;
    }

    public static boolean isBuilding(HubBootstrapData data) {
        return data.state() == HubBootstrapData.State.PLACING_BLOCKS
            || data.state() == HubBootstrapData.State.PLACING_ENTITIES;
    }

    public static int chunkCount(MinecraftServer server) {
        if (manifest != null) {
            return manifest.chunks().size();
        }
        return HubManifest.load(server.getResourceManager()).map(m -> m.chunks().size()).orElse(0);
    }

    private static void reset() {
        manifest = null;
        PENDING.values().forEach(future -> future.cancel(false));
        PENDING.clear();
        restTicks = 0;
        entityWaitTicks = 0;
        lastReportedPercent = -1;
        placeNanos = 0L;
        placedChunks = 0;
    }

    private static void placeBlocks(MinecraftServer server, ServerLevel dungeon, HubBootstrapData data) {
        List<HubManifest.Chunk> chunks = manifest.chunks();
        int index = data.nextChunk();
        prefetch(server, chunks, index);
        if (restTicks > 0) {
            restTicks--;
            return;
        }
        long start = System.nanoTime();
        long deadline = start + BLOCK_BUDGET_NANOS;
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setIgnoreEntities(true)
            .setKnownShape(true);
        while (index < chunks.size() && System.nanoTime() < deadline) {
            HubManifest.Chunk chunk = chunks.get(index);
            CompletableFuture<StructureTemplate> future = PENDING.get(chunk.template());
            if (future == null || !future.isDone()) {
                break;
            }
            PENDING.remove(chunk.template());
            StructureTemplate template = resolve(chunk, future);
            if (template != null) {
                place(dungeon, template, chunk, settings);
            }
            index++;
            prefetch(server, chunks, index);
        }
        long spent = System.nanoTime() - start;
        placeNanos += spent;
        restTicks = (int) Math.max(0L, (spent - BLOCK_BUDGET_NANOS) / REST_NANOS_PER_TICK);
        if (index != data.nextChunk()) {
            placedChunks += index - data.nextChunk();
            data.setNextChunk(index);
            reportProgress(index, chunks.size());
        }
        if (index >= chunks.size()) {
            data.setState(HubBootstrapData.State.PLACING_ENTITIES);
            data.setNextEntityChunk(0);
            entityWaitTicks = 0;
            TensuraDungeonsUnbound.LOGGER.info(
                "[DU-HUB] Hub blocks placed ({} chunks, {} ms server-thread time this session); placing NPCs.",
                chunks.size(), placeNanos / 1_000_000L
            );
        }
    }

    private static void placeEntities(MinecraftServer server, ServerLevel dungeon, HubBootstrapData data) {
        List<HubManifest.Chunk> entityChunks = manifest.entityChunks();
        int index = data.nextEntityChunk();
        if (index >= entityChunks.size()) {
            finish(server, dungeon, data);
            return;
        }
        HubManifest.Chunk chunk = entityChunks.get(index);
        CompletableFuture<StructureTemplate> future = PENDING.computeIfAbsent(
            chunk.template(), key -> load(server, chunk)
        );
        ChunkPos pos = new ChunkPos(chunk.x(), chunk.z());
        dungeon.setChunkForced(pos.x, pos.z, true);
        boolean entitiesLoaded = dungeon.areEntitiesLoaded(pos.toLong());
        if ((!entitiesLoaded || !future.isDone()) && entityWaitTicks++ < ENTITY_WAIT_TIMEOUT_TICKS) {
            return;
        }
        if (!entitiesLoaded) {
            TensuraDungeonsUnbound.LOGGER.warn("[DU-HUB] Entities of chunk {} did not load in time; placing anyway.", pos);
        }
        PENDING.remove(chunk.template());
        StructureTemplate template = resolve(chunk, future.isDone() ? future : CompletableFuture.completedFuture(future.join()));
        if (template != null) {
            StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setFinalizeEntities(false)
                .setKnownShape(true);
            place(dungeon, template, chunk, settings);
        }
        dungeon.setChunkForced(pos.x, pos.z, false);
        entityWaitTicks = 0;
        data.setNextEntityChunk(index + 1);
    }

    private static void prefetch(MinecraftServer server, List<HubManifest.Chunk> chunks, int from) {
        int end = Math.min(chunks.size(), from + PREFETCH_AHEAD);
        for (int i = from; i < end; i++) {
            HubManifest.Chunk chunk = chunks.get(i);
            PENDING.computeIfAbsent(chunk.template(), key -> load(server, chunk));
        }
    }

    private static CompletableFuture<StructureTemplate> load(MinecraftServer server, HubManifest.Chunk chunk) {
        ResourceManager resources = server.getResourceManager();
        HolderGetter<Block> blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        ResourceLocation file = chunk.template().withPath(path -> "structure/" + path + ".nbt");
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream input = resources.open(file)) {
                CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
                StructureTemplate template = new StructureTemplate();
                template.load(blocks, tag);
                return template;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, Util.backgroundExecutor());
    }

    private static StructureTemplate resolve(HubManifest.Chunk chunk, CompletableFuture<StructureTemplate> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            TensuraDungeonsUnbound.LOGGER.error("[DU-HUB] Could not read hub template {}", chunk.template(), exception.getCause());
            return null;
        }
    }

    private static void place(
        ServerLevel dungeon,
        StructureTemplate template,
        HubManifest.Chunk chunk,
        StructurePlaceSettings settings
    ) {
        ChunkPos pos = new ChunkPos(chunk.x(), chunk.z());
        dungeon.getChunkSource().addRegionTicket(BUILD_TICKET, pos, 1, pos);
        dungeon.getChunk(chunk.x(), chunk.z());
        BlockPos origin = chunk.origin();
        template.placeInWorld(dungeon, origin, origin, settings, RandomSource.create(0L), PLACE_FLAGS);
    }

    private static void finish(MinecraftServer server, ServerLevel dungeon, HubBootstrapData data) {
        DungeonRunManager runManager = DungeonRunManager.get(dungeon);
        runManager.setHubSpawnAnchor(manifest.spawnAnchor());
        runManager.setHubReturnAnchor(manifest.returnAnchor());
        runManager.setHubGateAnchor(manifest.gateAnchor());
        runManager.setHubSafeZone(manifest.safeZoneCornerA(), manifest.safeZoneCornerB());
        data.setState(HubBootstrapData.State.DONE);
        TensuraDungeonsUnbound.LOGGER.info(
            "[DU-HUB] Dungeon hub built and registered: spawn={} return={} gate={} safeZone={}..{} hubEnabled={}",
            manifest.spawnAnchor(), manifest.returnAnchor(), manifest.gateAnchor(),
            manifest.safeZoneCornerA(), manifest.safeZoneCornerB(), runManager.isHubEnabled()
        );
        broadcast(server, Component.literal("The dimensional dungeon hub has formed. Rifts can now open.")
            .withStyle(ChatFormatting.LIGHT_PURPLE));
        reset();
    }

    private static void reportProgress(int done, int total) {
        int percent = total == 0 ? 100 : done * 100 / total;
        int step = percent / 25 * 25;
        if (step != lastReportedPercent) {
            lastReportedPercent = step;
            long average = placedChunks == 0 ? 0 : placeNanos / placedChunks / 1_000L;
            TensuraDungeonsUnbound.LOGGER.info(
                "[DU-HUB] Building dungeon hub: {}% ({}/{} chunks, avg {} us/chunk on server thread)",
                percent, done, total, average
            );
        }
    }

    private static void broadcast(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }
}
