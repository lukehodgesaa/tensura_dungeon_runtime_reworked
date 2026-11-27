package com.mineclawd.tensuradungeon.event;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import com.mineclawd.tensuradungeon.block.entity.RoomProtectionView;
import com.mineclawd.tensuradungeon.registry.ModDimensions;
import com.mineclawd.tensuradungeon.registry.ModBlocks;
import com.mineclawd.tensuradungeon.runtime.DungeonRunManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = TensuraDungeonRuntime.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ActiveRoomProtectionEvents {
    private static final int FALLBACK_CONTROLLER_RADIUS = 10;
    private static final boolean EXPLOSION_DEBUG_ENABLED = false;
    private static final Component BREAK_DENIED_MESSAGE = Component.literal("You cannot break blocks in protected dungeon areas.");
    private static final Component PLACE_DENIED_MESSAGE = Component.literal("You cannot place blocks in protected dungeon areas.");
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE_PROTECTING_CONTROLLERS = new HashMap<>();

    private static int breakEventsFired;
    private static int placeEventsFired;
    private static int explosionEventsFired;
    private static int protectedExplosionBlocksRemoved;
    private static int mobGriefEventsFired;
    private static int mobGriefEventsBlocked;
    private static int protectionLookupCount;
    private static int protectionLookupHits;
    private static int protectionLookupMisses;
    private static int protectionLookupCandidateCountLast;
    private static int protectionLookupRegisteredPruned;
    private static String lastProtectionLookupSource = "NONE";
    private static boolean lastProtectionLookupAttemptedMarkerRefresh;
    private static boolean lastProtectionLookupUsedWorldScan;
    private static String lastEventType = "NONE";
    private static String lastPlayerName = "NONE";
    private static String lastTargetPos = "NONE";
    private static boolean lastServerSide;
    private static boolean lastControllerFound;
    private static String lastControllerPos = "NONE";
    private static String lastControllerState = "NONE";
    private static String lastBoundsMode = "NONE";
    private static String lastBoundsMin = "NONE";
    private static String lastBoundsMax = "NONE";
    private static boolean lastTargetInsideBounds;
    private static boolean lastCancellationApplied;
    private static String lastExplosionPos = "NONE";
    private static int lastExplosionAffectedBefore;
    private static int lastExplosionProtectedRemoved;
    private static int lastExplosionAffectedAfter;

    private ActiveRoomProtectionEvents() {
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        boolean serverSide = event.getPlayer() instanceof ServerPlayer && event.getPlayer().level() instanceof ServerLevel;
        recordAttempt("BreakEvent", event.getPlayer(), event.getPos(), serverSide);
        breakEventsFired++;
        if (!serverSide) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getPlayer();
        if (player.isCreative()) {
            return;
        }

        ProtectionMatch protection = findProtectionMatch((ServerLevel) player.level(), event.getPos());
        if (!protection.protectedArea()) {
            return;
        }

        applyProtectionDebug((ServerLevel) player.level(), event.getPos(), protection.controller());
        event.setCanceled(true);
        lastCancellationApplied = event.isCanceled();
        player.sendSystemMessage(BREAK_DENIED_MESSAGE);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        boolean serverSide = event.getEntity() instanceof ServerPlayer && event.getEntity().level() instanceof ServerLevel;
        recordAttempt("LeftClickBlock", event.getEntity(), event.getPos(), serverSide);
        if (!serverSide) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (player.isCreative()) {
            return;
        }

        ProtectionMatch protection = findProtectionMatch((ServerLevel) player.level(), event.getPos());
        if (!protection.protectedArea()) {
            return;
        }

        applyProtectionDebug((ServerLevel) player.level(), event.getPos(), protection.controller());
        event.setCanceled(true);
        lastCancellationApplied = event.isCanceled();
        player.sendSystemMessage(BREAK_DENIED_MESSAGE);
    }

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        boolean serverSide = event.getEntity() instanceof ServerPlayer && event.getEntity().level() instanceof ServerLevel;
        recordAttempt("EntityPlaceEvent", event.getEntity(), event.getPos(), serverSide);
        placeEventsFired++;
        if (!serverSide) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (player.isCreative()) {
            return;
        }

        ProtectionMatch protection = findProtectionMatch((ServerLevel) player.level(), event.getPos());
        if (!protection.protectedArea()) {
            return;
        }

        applyProtectionDebug((ServerLevel) player.level(), event.getPos(), protection.controller());
        event.setCanceled(true);
        lastCancellationApplied = event.isCanceled();
        player.sendSystemMessage(PLACE_DENIED_MESSAGE);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        boolean serverSide = event.getEntity() instanceof ServerPlayer && event.getEntity().level() instanceof ServerLevel;
        ItemStack stack = event.getItemStack();
        BlockPos targetPos = event.getFace() == null ? event.getPos() : event.getPos().relative(event.getFace());
        recordAttempt("RightClickBlock", event.getEntity(), targetPos, serverSide);
        if (!serverSide) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (player.isCreative()) {
            return;
        }

        boolean placementItem = stack.getItem() instanceof BlockItem || stack.getItem() instanceof SpawnEggItem;
        if (!placementItem) {
            return;
        }

        ProtectionMatch protection = findProtectionMatch((ServerLevel) player.level(), targetPos);
        if (!protection.protectedArea()) {
            return;
        }

        applyProtectionDebug((ServerLevel) player.level(), targetPos, protection.controller());
        event.setCanceled(true);
        lastCancellationApplied = event.isCanceled();
        player.sendSystemMessage(PLACE_DENIED_MESSAGE);
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        boolean globalProtection = isDungeonDimensionProtectionActive(level);
        List<RoomControllerBlockEntity> activeControllers = getActiveProtectingControllers(level);
        BlockPos explosionCenter = BlockPos.containing(event.getExplosion().center());
        explosionEventsFired++;
        lastExplosionPos = formatPos(explosionCenter);
        lastExplosionAffectedBefore = event.getAffectedBlocks().size();
        lastExplosionProtectedRemoved = 0;

        RoomControllerBlockEntity explosionController = getExplosionProtectionController(
            level, event, activeControllers, explosionCenter
        );
        if (explosionController != null && explosionController.isTrackedRuneCrystal(event.getExplosion().getDirectSourceEntity())) {
            int removedCount = event.getAffectedBlocks().size();
            if (removedCount > 0) {
                event.getAffectedBlocks().clear();
                protectedExplosionBlocksRemoved += removedCount;
                lastExplosionProtectedRemoved = removedCount;
            }
            applyProtectionDebug(level, explosionCenter, explosionController);
            lastExplosionAffectedAfter = 0;
            return;
        }
        if (globalProtection || explosionController != null) {
            int removedCount = event.getAffectedBlocks().size();
            if (removedCount > 0) {
                event.getAffectedBlocks().clear();
                protectedExplosionBlocksRemoved += removedCount;
                lastExplosionProtectedRemoved = removedCount;
            }
            applyProtectionDebug(level, explosionCenter, explosionController);
            lastExplosionAffectedAfter = 0;
            return;
        }

        BlockPos firstProtectedRuntimeBlock = null;
        Iterator<BlockPos> iterator = event.getAffectedBlocks().iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!isProtectedDungeonBlock(level, pos)) {
                continue;
            }

            if (firstProtectedRuntimeBlock == null) {
                firstProtectedRuntimeBlock = pos.immutable();
            }
            iterator.remove();
            protectedExplosionBlocksRemoved++;
            lastExplosionProtectedRemoved++;
        }

        if (firstProtectedRuntimeBlock != null) {
            applyProtectionDebug(level, firstProtectedRuntimeBlock, null);
        }
        lastExplosionAffectedAfter = event.getAffectedBlocks().size();
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        mobGriefEventsFired++;
        ProtectionMatch protection = findProtectionMatch(level, entity.blockPosition());
        if (!protection.protectedArea()) {
            return;
        }
        applyProtectionDebug(level, entity.blockPosition(), protection.controller());
        event.setCanGrief(false);
        mobGriefEventsBlocked++;
    }

    @SubscribeEvent
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        ProtectionMatch protection = findProtectionMatch(level, pos);
        if (!protection.protectedArea()) {
            return;
        }
        applyProtectionDebug(level, pos, protection.controller());
        event.setCanceled(true);
        lastCancellationApplied = event.isCanceled();
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        ProtectionMatch protection = findProtectionMatch(level, pos);
        if (!protection.protectedArea()) {
            return;
        }
        applyProtectionDebug(level, pos, protection.controller());
        event.setCanceled(true);
        lastCancellationApplied = event.isCanceled();
    }

    public static void syncProtectionRegistration(ServerLevel level, RoomControllerBlockEntity controller) {
        if (controller.protectionView(level).anyRuleZoneProtectionActive()) {
            registerActiveController(level, controller.getBlockPos());
        } else {
            unregisterActiveController(level, controller.getBlockPos());
        }
    }

    public static void registerActiveController(ServerLevel level, BlockPos controllerPos) {
        ACTIVE_PROTECTING_CONTROLLERS.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(controllerPos.immutable());
    }

    public static void unregisterActiveController(ServerLevel level, BlockPos controllerPos) {
        Set<BlockPos> positions = ACTIVE_PROTECTING_CONTROLLERS.get(level.dimension());
        if (positions == null) {
            return;
        }
        positions.remove(controllerPos);
        if (positions.isEmpty()) {
            ACTIVE_PROTECTING_CONTROLLERS.remove(level.dimension());
            if (controllerPos != null && formatPos(controllerPos).equals(lastControllerPos)) {
                lastControllerFound = false;
                lastControllerPos = "NONE";
                lastControllerState = "NONE";
                lastBoundsMode = "NONE";
                lastBoundsMin = "NONE";
                lastBoundsMax = "NONE";
                lastTargetInsideBounds = false;
            }
        }
    }

    public static void clearActiveControllerRegistrations(ServerLevel level) {
        ACTIVE_PROTECTING_CONTROLLERS.remove(level.dimension());
        lastControllerFound = false;
        lastControllerPos = "NONE";
        lastControllerState = "NONE";
        lastBoundsMode = "NONE";
        lastBoundsMin = "NONE";
        lastBoundsMax = "NONE";
        lastTargetInsideBounds = false;
    }

    public static boolean isDungeonDimension(Level level) {
        return level != null && level.dimension().equals(ModDimensions.DUNGEON);
    }

    public static boolean isDungeonDimensionProtectionActive(Level level) {
        return isDungeonDimension(level);
    }

    public static boolean isInsideActiveRoomProtectedBounds(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            DungeonRunManager manager = DungeonRunManager.get(serverLevel);
            if (manager.isHubEnabled() && manager.isPlayerInsideHubSafeZone(pos)) {
                return false;
            }
            return getActiveProtectionController(serverLevel, pos) != null;
        }
        return false;
    }

    public static boolean isBlockProtected(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (isDungeonDimensionProtectionActive(level)) {
            return true;
        }
        return isProtectedDungeonBlock(serverLevel, pos) || getProtectionController(serverLevel, pos) != null;
    }

    public static String getProtectionMode(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            DungeonRunManager manager = DungeonRunManager.get(serverLevel);
            if (manager.isHubEnabled() && manager.isPlayerInsideHubSafeZone(pos)) {
                return "HUB_SAFE_ZONE";
            }
        }
        if (isDungeonDimensionProtectionActive(level)) {
            return "DUNGEON_DIMENSION_GLOBAL";
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return "NONE";
        }
        if (isProtectedDungeonBlock(serverLevel, pos)) {
            return "RUNTIME_BLOCK_GLOBAL";
        }
        RoomControllerBlockEntity controller = getProtectionController(serverLevel, pos);
        if (controller == null) {
            return "NONE";
        }
        RoomProtectionView protection = controller.protectionView(serverLevel);
        if (protection.ruleZoneProtectionActive()) {
            return "ACTIVE_ROOM_BOUNDS";
        }
        if (protection.protectionGraceActive()) {
            return "POST_COMPLETION_GRACE";
        }
        return protection.hasBounds() ? "KNOWN_ROOM_BOUNDS" : "KNOWN_ROOM_RADIUS";
    }

    public static int getRegisteredProtectionControllerCount(ServerLevel level) {
        return getActiveProtectingControllers(level).size();
    }

    public static int getBreakEventsFired() {
        return breakEventsFired;
    }

    public static int getPlaceEventsFired() {
        return placeEventsFired;
    }

    public static int getExplosionEventsFired() {
        return explosionEventsFired;
    }

    public static int getProtectedExplosionBlocksRemoved() {
        return protectedExplosionBlocksRemoved;
    }

    public static int getMobGriefEventsFired() {
        return mobGriefEventsFired;
    }

    public static int getMobGriefEventsBlocked() {
        return mobGriefEventsBlocked;
    }

    public static int getProtectionLookupCount() {
        return protectionLookupCount;
    }

    public static int getProtectionLookupHits() {
        return protectionLookupHits;
    }

    public static int getProtectionLookupMisses() {
        return protectionLookupMisses;
    }

    public static int getProtectionLookupCandidateCountLast() {
        return protectionLookupCandidateCountLast;
    }

    public static int getProtectionLookupRegisteredPruned() {
        return protectionLookupRegisteredPruned;
    }

    public static String getLastProtectionLookupSource() {
        return lastProtectionLookupSource;
    }

    public static boolean wasLastProtectionLookupAttemptedMarkerRefresh() {
        return lastProtectionLookupAttemptedMarkerRefresh;
    }

    public static boolean wasLastProtectionLookupUsedWorldScan() {
        return lastProtectionLookupUsedWorldScan;
    }
    public static String getLastEventType() {
        return lastEventType;
    }

    public static String getLastPlayerName() {
        return lastPlayerName;
    }

    public static String getLastTargetPos() {
        return lastTargetPos;
    }

    public static boolean wasLastServerSide() {
        return lastServerSide;
    }

    public static boolean wasLastControllerFound() {
        return lastControllerFound;
    }

    public static String getLastControllerPos() {
        return lastControllerPos;
    }

    public static String getLastControllerState() {
        return lastControllerState;
    }

    public static String getLastBoundsMode() {
        return lastBoundsMode;
    }

    public static String getLastBoundsMin() {
        return lastBoundsMin;
    }

    public static String getLastBoundsMax() {
        return lastBoundsMax;
    }

    public static boolean wasLastTargetInsideBounds() {
        return lastTargetInsideBounds;
    }

    public static boolean wasLastCancellationApplied() {
        return lastCancellationApplied;
    }

    public static boolean isFutureDungeonDimensionWideProtectionEnabled() {
        return true;
    }

    public static boolean isEnvironmentalProtectionActive() {
        return ACTIVE_PROTECTING_CONTROLLERS.values().stream().anyMatch(positions -> positions != null && !positions.isEmpty());
    }

    public static String getLastExplosionPos() {
        return lastExplosionPos;
    }

    public static int getLastExplosionAffectedBefore() {
        return lastExplosionAffectedBefore;
    }

    public static int getLastExplosionProtectedRemoved() {
        return lastExplosionProtectedRemoved;
    }

    public static int getLastExplosionAffectedAfter() {
        return lastExplosionAffectedAfter;
    }
    public static boolean isExplosionDebugEnabled() {
        return EXPLOSION_DEBUG_ENABLED;
    }

    private static void recordAttempt(String eventType, Entity entity, BlockPos pos, boolean serverSide) {
        lastEventType = eventType;
        lastPlayerName = entity instanceof Player player ? player.getGameProfile().getName() : "NONE";
        lastTargetPos = formatPos(pos);
        lastServerSide = serverSide;
        lastControllerFound = false;
        lastControllerPos = "NONE";
        lastControllerState = "NONE";
        lastBoundsMode = "NONE";
        lastBoundsMin = "NONE";
        lastBoundsMax = "NONE";
        lastTargetInsideBounds = false;
        lastCancellationApplied = false;
    }

    private static void applyProtectionDebug(ServerLevel level, BlockPos protectedPos, RoomControllerBlockEntity controller) {
        if (controller == null) {
            lastControllerFound = false;
            lastControllerPos = "NONE";
            lastControllerState = "NONE";
            if (isDungeonDimensionProtectionActive(level)) {
                lastBoundsMode = "DUNGEON_DIMENSION_GLOBAL";
            } else if (isProtectedDungeonBlock(level, protectedPos)) {
                lastBoundsMode = "RUNTIME_BLOCK_GLOBAL";
            } else {
                lastBoundsMode = "NONE";
            }
            lastBoundsMin = "NONE";
            lastBoundsMax = "NONE";
            lastTargetInsideBounds = false;
            return;
        }

        lastControllerFound = true;
        lastControllerPos = formatPos(controller.getBlockPos());
        RoomProtectionView protection = controller.protectionView(level);
        lastControllerState = protection.roomState().name();
        if (protection.ruleZoneProtectionActive()) {
            lastBoundsMode = protection.protectedBoundsMode();
        } else if (protection.protectionGraceActive()) {
            lastBoundsMode = "POST_COMPLETION_GRACE";
        } else {
            lastBoundsMode = protection.hasBounds() ? "KNOWN_ROOM_BOUNDS" : "KNOWN_ROOM_RADIUS";
        }
        RoomControllerBlockEntity.RoomBounds bounds = protection.bounds();
        if (bounds != null) {
            lastBoundsMin = formatPos(bounds.min());
            lastBoundsMax = formatPos(bounds.max());
            lastTargetInsideBounds = bounds.contains(protectedPos);
        } else {
            lastBoundsMin = "NONE";
            lastBoundsMax = "NONE";
            lastTargetInsideBounds = protection.controllerPos().distSqr(protectedPos) <= FALLBACK_CONTROLLER_RADIUS * FALLBACK_CONTROLLER_RADIUS;
        }
    }

    private static RoomControllerBlockEntity getProtectionController(ServerLevel level, BlockPos protectedPos) {
        protectionLookupCount++;
        lastProtectionLookupAttemptedMarkerRefresh = false;
        lastProtectionLookupUsedWorldScan = false;
        RoomControllerBlockEntity activeController = getActiveProtectionController(level, protectedPos);
        if (activeController != null) {
            protectionLookupHits++;
            lastProtectionLookupSource = "REGISTERED_ACTIVE_CONTROLLER";
            return activeController;
        }
        protectionLookupMisses++;
        protectionLookupCandidateCountLast = getRegisteredProtectionControllerCount(level);
        lastProtectionLookupSource = "REGISTERED_NONE";
        return null;
    }

    private static RoomControllerBlockEntity getProtectionController(ServerLevel level, BlockPos protectedPos, List<RoomControllerBlockEntity> activeControllers) {
        protectionLookupCandidateCountLast = activeControllers.size();
        for (RoomControllerBlockEntity controller : activeControllers) {
            if (controller == null) {
                continue;
            }
            RoomProtectionView protection = controller.protectionView(level);
            if (protection.anyRuleZoneProtectionActive() && protection.containsKnownProtectedArea(protectedPos, FALLBACK_CONTROLLER_RADIUS)) {
                return controller;
            }
        }
        return null;
    }

    private static RoomControllerBlockEntity getActiveProtectionController(ServerLevel level, BlockPos protectedPos) {
        return getProtectionController(level, protectedPos, getActiveProtectingControllers(level));
    }

    private static RoomControllerBlockEntity getExplosionProtectionController(ServerLevel level, ExplosionEvent.Detonate event, List<RoomControllerBlockEntity> activeControllers, BlockPos explosionCenter) {
        RoomControllerBlockEntity controller = getProtectionController(level, explosionCenter, activeControllers);
        if (controller != null) {
            return controller;
        }

        Entity sourceEntity = event.getExplosion().getDirectSourceEntity();
        if (sourceEntity != null && sourceEntity.level() == level) {
            return getProtectionController(level, sourceEntity.blockPosition(), activeControllers);
        }

        return null;
    }

    private static List<RoomControllerBlockEntity> getActiveProtectingControllers(ServerLevel level) {
        Set<BlockPos> registeredPositions = ACTIVE_PROTECTING_CONTROLLERS.get(level.dimension());
        if (registeredPositions == null || registeredPositions.isEmpty()) {
            return List.of();
        }

        List<RoomControllerBlockEntity> controllers = new ArrayList<>();
        Iterator<BlockPos> iterator = registeredPositions.iterator();
        while (iterator.hasNext()) {
            BlockPos controllerPos = iterator.next();
            if (!(level.getBlockEntity(controllerPos) instanceof RoomControllerBlockEntity controller)) {
                iterator.remove();
                protectionLookupRegisteredPruned++;
                continue;
            }

            if (!controller.protectionView(level).anyRuleZoneProtectionActive()) {
                iterator.remove();
                protectionLookupRegisteredPruned++;
                continue;
            }

            controllers.add(controller);
        }

        if (registeredPositions.isEmpty()) {
            ACTIVE_PROTECTING_CONTROLLERS.remove(level.dimension());
        }
        return controllers;
    }

    private static boolean isProtectedDungeonBlock(BlockGetter level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block == ModBlocks.ROOM_CONTROLLER.get()
            || block == ModBlocks.SPAWN_MELEE.get()
            || block == ModBlocks.SPAWN_RANGED.get()
            || block == ModBlocks.SPAWN_FLANK.get()
            || block == ModBlocks.SPAWN_HIGHGROUND.get()
            || block == ModBlocks.SPAWN_ELITE.get()
            || block == ModBlocks.BARRIER_ANCHOR.get()
            || block == ModBlocks.BARRIER_ANCHOR_1.get()
            || block == ModBlocks.BARRIER_ANCHOR_2.get()
            || block == ModBlocks.BARRIER_ANCHOR_3.get()
            || block == ModBlocks.BARRIER_ANCHOR_4.get()
            || block == ModBlocks.OBJECTIVE_ANCHOR.get()
            || block == ModBlocks.MOB_RESET_ANCHOR.get()
            || block == ModBlocks.EXIT_ANCHOR.get()
            || block == ModBlocks.BOUNDS_ANCHOR.get()
            || block == ModBlocks.FLOOR_EXIT_PORTAL.get();
    }

    private static ProtectionMatch findProtectionMatch(ServerLevel level, BlockPos pos) {
        RoomControllerBlockEntity controller = getProtectionController(level, pos);
        return new ProtectionMatch(
            controller,
            isDungeonDimensionProtectionActive(level) || controller != null || isProtectedDungeonBlock(level, pos)
        );
    }

    private record ProtectionMatch(RoomControllerBlockEntity controller, boolean protectedArea) {
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
