package com.mineclawd.tensuradungeon.block.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

final class PlayerSessionAssociation {
    static final int ACTIVATION_RADIUS = 10;
    static final int PLAYER_BOUNDS_RECOVERY_TICKS = 5;
    static final int FALLBACK_REENTRY_GRACE_TICKS = 30 * 20;
    static final long RUN_ACTIVATION_BLOCK_MESSAGE_COOLDOWN_TICKS = 100L;

    private final Set<UUID> associatedPlayers = new HashSet<>();
    private final Set<UUID> warnedPlayersOutsideBounds = new HashSet<>();
    private final Map<UUID, Integer> outsideBoundsTicks = new HashMap<>();
    private final Map<UUID, Integer> fallbackReentryTicks = new HashMap<>();
    private final Set<UUID> fallbackRecoveryUsed = new HashSet<>();
    private final Map<UUID, Long> runActivationBlockMessageGameTimes = new HashMap<>();
    private UUID pendingBoundsRecoveryPlayerId;
    private String lastRunActivationBlockReason = "NONE";

    boolean hasPlayerInActivationZone(
        ServerLevel level,
        BlockPos controllerPos,
        boolean bounded,
        RoomControllerBlockEntity.RoomBounds activationBounds
    ) {
        for (Player player : level.players()) {
            if (!player.isSpectator() && isInsideActivationZone(player, controllerPos, bounded, activationBounds)) {
                return true;
            }
        }
        return false;
    }

    ServerPlayer nearestPlayerInActivationZone(
        ServerLevel level,
        BlockPos controllerPos,
        boolean bounded,
        RoomControllerBlockEntity.RoomBounds activationBounds
    ) {
        ServerPlayer selectedPlayer = null;
        double selectedDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !isInsideActivationZone(player, controllerPos, bounded, activationBounds)) {
                continue;
            }
            double distance = player.blockPosition().distSqr(controllerPos);
            if (distance < selectedDistance) {
                selectedDistance = distance;
                selectedPlayer = player;
            }
        }
        return selectedPlayer;
    }

    void associateTriggeringPlayer(
        ServerLevel level,
        BlockPos controllerPos,
        boolean bounded,
        RoomControllerBlockEntity.RoomBounds activationBounds
    ) {
        ServerPlayer selectedPlayer = nearestPlayerInActivationZone(level, controllerPos, bounded, activationBounds);
        if (selectedPlayer != null) {
            associatedPlayers.add(selectedPlayer.getUUID());
        }
    }

    void notifyRunActivationBlocked(
        ServerLevel level,
        BlockPos controllerPos,
        boolean bounded,
        RoomControllerBlockEntity.RoomBounds activationBounds,
        Component message
    ) {
        ServerPlayer player = nearestPlayerInActivationZone(level, controllerPos, bounded, activationBounds);
        if (player == null) {
            return;
        }
        long gameTime = level.getGameTime();
        if (!recordRunActivationBlockMessage(player.getUUID(), gameTime)) {
            return;
        }
        player.sendSystemMessage(message);
    }

    void pruneRunActivationBlockMessageCooldowns(
        ServerLevel level,
        BlockPos controllerPos,
        boolean bounded,
        RoomControllerBlockEntity.RoomBounds activationBounds
    ) {
        Set<UUID> eligiblePlayers = new HashSet<>();
        for (UUID playerId : runActivationBlockMessageGameTimes.keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null
                && !player.isRemoved()
                && !player.isSpectator()
                && isInsideActivationZone(player, controllerPos, bounded, activationBounds)) {
                eligiblePlayers.add(playerId);
            }
        }
        retainRunActivationBlockMessageCooldowns(eligiblePlayers);
    }

    ActivePlayerTickResult tickActivePlayers(
        ServerLevel level,
        BlockPos controllerPos,
        boolean roomActive,
        boolean completed,
        boolean bounded,
        RoomControllerBlockEntity.RoomBounds roomBounds,
        RoomControllerBlockEntity.RoomBounds activationBounds
    ) {
        if (!roomActive || completed) {
            clearAssociatedPlayers();
            return ActivePlayerTickResult.CONTINUE;
        }
        if (roomBounds == null) {
            clearAssociatedPlayers();
            return ActivePlayerTickResult.CONTINUE;
        }
        if (associatedPlayers.isEmpty()) {
            associateTriggeringPlayer(level, controllerPos, bounded, activationBounds);
        }

        int validPlayers = 0;
        for (UUID playerId : new HashSet<>(associatedPlayers)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null || player.isRemoved() || player.isSpectator() || player.serverLevel() != level) {
                outsideBoundsTicks.remove(playerId);
                clearOutsideBoundsWarning(playerId);
                continue;
            }

            validPlayers++;
            if (player.isDeadOrDying()) {
                return ActivePlayerTickResult.PLAYER_DIED;
            }

            if (roomBounds.contains(player.blockPosition())) {
                outsideBoundsTicks.remove(playerId);
                fallbackReentryTicks.remove(playerId);
                fallbackRecoveryUsed.remove(playerId);
                clearOutsideBoundsWarning(playerId);
                continue;
            }

            Integer reentryTicks = fallbackReentryTicks.get(playerId);
            if (reentryTicks != null && reentryTicks > 0) {
                fallbackReentryTicks.put(playerId, reentryTicks - 1);
                return ActivePlayerTickResult.PLAYER_RECOVERING_OUTSIDE_BOUNDS;
            }
            fallbackReentryTicks.remove(playerId);

            int ticks = outsideBoundsTicks.getOrDefault(playerId, 0) + 1;
            outsideBoundsTicks.put(playerId, ticks);
            if (recordOutsideBoundsWarning(playerId)) {
                player.sendSystemMessage(Component.literal("You are leaving the active room bounds."));
            }
            if (ticks >= PLAYER_BOUNDS_RECOVERY_TICKS) {
                pendingBoundsRecoveryPlayerId = playerId;
                return ActivePlayerTickResult.PLAYER_LEFT_ACTIVE_BOUNDS;
            }
        }

        associatedPlayers.removeIf(playerId -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            return player == null || player.isRemoved() || player.serverLevel() != level || player.isSpectator();
        });
        warnedPlayersOutsideBounds.retainAll(associatedPlayers);
        outsideBoundsTicks.keySet().retainAll(associatedPlayers);
        fallbackReentryTicks.keySet().retainAll(associatedPlayers);
        fallbackRecoveryUsed.retainAll(associatedPlayers);

        return validPlayers <= 0
            ? ActivePlayerTickResult.NO_VALID_ACTIVE_PLAYER
            : ActivePlayerTickResult.CONTINUE;
    }

    ServerPlayer resolveHubReturnPlayer(
        ServerLevel level,
        BlockPos controllerPos,
        boolean bounded,
        RoomControllerBlockEntity.RoomBounds activationBounds
    ) {
        for (UUID playerId : associatedPlayers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && !player.isRemoved() && !player.isSpectator() && player.serverLevel() == level) {
                return player;
            }
        }
        return nearestPlayerInActivationZone(level, controllerPos, bounded, activationBounds);
    }

    ServerPlayer rewardRecipient(
        ServerLevel level,
        BlockPos controllerPos,
        boolean bounded,
        RoomControllerBlockEntity.RoomBounds activationBounds
    ) {
        for (UUID playerId : associatedPlayers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && !player.isSpectator()) {
                return player;
            }
        }
        return nearestPlayerInActivationZone(level, controllerPos, bounded, activationBounds);
    }

    List<ServerPlayer> validPlayers(ServerLevel level) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID playerId : associatedPlayers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && !player.isRemoved() && !player.isSpectator() && player.serverLevel() == level) {
                players.add(player);
            }
        }
        return List.copyOf(players);
    }

    boolean isAssociated(UUID playerId) {
        return associatedPlayers.contains(playerId);
    }

    UUID pendingBoundsRecoveryPlayerId() {
        return pendingBoundsRecoveryPlayerId;
    }

    void completeBoundsRecovery(UUID playerId) {
        outsideBoundsTicks.remove(playerId);
        clearOutsideBoundsWarning(playerId);
        if (playerId != null && playerId.equals(pendingBoundsRecoveryPlayerId)) {
            pendingBoundsRecoveryPlayerId = null;
        }
    }

    void beginFallbackBoundsRecovery(UUID playerId) {
        completeBoundsRecovery(playerId);
        fallbackRecoveryUsed.add(playerId);
        fallbackReentryTicks.put(playerId, FALLBACK_REENTRY_GRACE_TICKS);
    }

    boolean fallbackRecoveryUsed(UUID playerId) {
        return playerId != null && fallbackRecoveryUsed.contains(playerId);
    }

    int associatedPlayerCount() {
        return associatedPlayers.size();
    }

    int validActivePlayerCount(ServerLevel level) {
        return validPlayers(level).size();
    }

    boolean isAnyAssociatedPlayerOutsideBounds() {
        return !outsideBoundsTicks.isEmpty();
    }

    int outsideBoundsTimer() {
        int maxTicks = 0;
        for (int ticks : outsideBoundsTicks.values()) {
            maxTicks = Math.max(maxTicks, ticks);
        }
        return maxTicks;
    }

    boolean recordRunActivationBlockMessage(UUID playerId, long gameTime) {
        long lastMessageTime = runActivationBlockMessageGameTimes.getOrDefault(playerId, Long.MIN_VALUE);
        if (gameTime - lastMessageTime < RUN_ACTIVATION_BLOCK_MESSAGE_COOLDOWN_TICKS) {
            return false;
        }
        runActivationBlockMessageGameTimes.put(playerId, gameTime);
        return true;
    }

    void retainRunActivationBlockMessageCooldowns(Set<UUID> eligiblePlayers) {
        runActivationBlockMessageGameTimes.keySet().retainAll(eligiblePlayers);
    }

    boolean recordOutsideBoundsWarning(UUID playerId) {
        return warnedPlayersOutsideBounds.add(playerId);
    }

    void clearOutsideBoundsWarning(UUID playerId) {
        warnedPlayersOutsideBounds.remove(playerId);
    }

    void clearAssociatedPlayers() {
        associatedPlayers.clear();
        warnedPlayersOutsideBounds.clear();
        outsideBoundsTicks.clear();
        fallbackReentryTicks.clear();
        fallbackRecoveryUsed.clear();
        pendingBoundsRecoveryPlayerId = null;
    }

    void clearTransientState() {
        clearAssociatedPlayers();
        runActivationBlockMessageGameTimes.clear();
    }

    void reset() {
        clearTransientState();
        lastRunActivationBlockReason = "NONE";
    }

    void recordRunActivationBlockReason(String reason) {
        lastRunActivationBlockReason = reason;
    }

    String lastRunActivationBlockReason() {
        return lastRunActivationBlockReason;
    }

    private static boolean isInsideActivationZone(
        Player player,
        BlockPos controllerPos,
        boolean bounded,
        RoomControllerBlockEntity.RoomBounds activationBounds
    ) {
        return bounded
            ? activationBounds != null && activationBounds.contains(player.blockPosition())
            : new AABB(controllerPos).inflate(ACTIVATION_RADIUS).contains(player.position());
    }

    enum ActivePlayerTickResult {
        CONTINUE,
        PLAYER_DIED,
        PLAYER_LEFT_ACTIVE_BOUNDS,
        PLAYER_RECOVERING_OUTSIDE_BOUNDS,
        NO_VALID_ACTIVE_PLAYER
    }
}
