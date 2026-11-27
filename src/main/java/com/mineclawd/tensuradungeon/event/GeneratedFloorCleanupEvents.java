package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.command.GeneratedLinearConnectorPlacementCommands;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorLazyPlacementService;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorCleanupProcessor;
import com.mineclawd.tensuradungeon.runtime.GeneratedFloorWorldWorkScheduler;
import com.mineclawd.tensuradungeon.runtime.DungeonNavigationService;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceAllocationData;
import com.mineclawd.tensuradungeon.runtime.DungeonInstanceRecoveryService;
import com.mineclawd.tensuradungeon.runtime.DungeonSubordinateTransportData;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMechanicalRegistry;
import com.mineclawd.tensuradungeon.runtime.generation.DungeonTemplateMetadataRegistry;
import com.mineclawd.tensuradungeon.registry.ModDimensions;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class GeneratedFloorCleanupEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private GeneratedFloorCleanupEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel dungeonLevel = event.getServer().getLevel(ModDimensions.DUNGEON);
        if (dungeonLevel != null) {
            long warmupStartedAt = System.nanoTime();
            DungeonTemplateMechanicalRegistry.Snapshot mechanicalSnapshot =
                DungeonTemplateMechanicalRegistry.get(
                    dungeonLevel,
                    DungeonTemplateMetadataRegistry.get(dungeonLevel)
                );
            LOGGER.info(
                "[TD-PERF] mechanical-registry-warmup result={} theme=ALL templates={} eligible={} errors={} totalMs={} dimension={}",
                mechanicalSnapshot.valid() ? "PASS" : "FAIL",
                mechanicalSnapshot.templates().size(),
                mechanicalSnapshot.eligibleCount(),
                mechanicalSnapshot.errors().size(),
                (System.nanoTime() - warmupStartedAt) / 1_000_000.0D,
                dungeonLevel.dimension().location()
            );
            GeneratedFloorLazyPlacementService.validateResumedGenerationContract(dungeonLevel);
        }
    }

    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % 100L == 0L) {
            LOGGER.debug(
                "[TD-TICK] level-tick-post heartbeat dimension={} gameTime={}",
                level.dimension().location(), level.getGameTime()
            );
        }
        GeneratedFloorWorldWorkScheduler.beginTick(level);
        DungeonInstanceRecoveryService.retryPendingResumes(level);
        DungeonSubordinateTransportData.get(level).tick(level);
        GeneratedFloorCleanupProcessor.retryDeferredTerminalCleanup(level);
        DungeonInstanceAllocationData.reconcile(level);
        GeneratedLinearConnectorPlacementCommands.processPendingPlaceAndStartStartup(level);
        GeneratedFloorWorldWorkScheduler.processPlacement(level);
        GeneratedFloorLazyPlacementService.processPendingSealRepair(level);
        GeneratedFloorWorldWorkScheduler.processCleanup(level);
        DungeonInstanceAllocationData.reconcile(level);
        GeneratedLinearConnectorPlacementCommands.processPendingPlaceAndStartGateOpen(level);
        if (level.getGameTime() % 10L == 0L) {
            DungeonNavigationService.sync(level);
        }
    }
}
