package com.mineclawd.tensuradungeon.runtime;

import com.mineclawd.tensuradungeon.entity.RiftVoidWarpEntity;
import com.mineclawd.tensuradungeon.compat.twilightteleport.TwilightTeleportCompat;
import com.mineclawd.tensuradungeon.registry.ModDimensions;
import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class OverworldRiftEventData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "tensura_dungeon_overworld_rift";
    private static final String ACTIVE_KEY = "Active";
    private static final String ANCHOR_KEY = "Anchor";
    private static final String STARTED_AT_KEY = "StartedAt";
    private static final String ENDS_AT_KEY = "EndsAt";
    private static final String NEXT_ROLL_AT_KEY = "NextRollAt";
    private static final String ORIENTATION_KEY = "OrientationDegrees";
    private static final String WEATHER_CAPTURED_KEY = "WeatherCaptured";
    private static final String PREV_CLEAR_TIME_KEY = "PrevClearTime";
    private static final String PREV_RAIN_TIME_KEY = "PrevRainTime";
    private static final String PREV_THUNDER_TIME_KEY = "PrevThunderTime";
    private static final String PREV_RAINING_KEY = "PrevRaining";
    private static final String PREV_THUNDERING_KEY = "PrevThundering";
    private static final String DUNGEON_WEATHER_CAPTURED_KEY = "DungeonWeatherCaptured";
    private static final String DUNGEON_PREV_CLEAR_TIME_KEY = "DungeonPrevClearTime";
    private static final String DUNGEON_PREV_RAIN_TIME_KEY = "DungeonPrevRainTime";
    private static final String DUNGEON_PREV_THUNDER_TIME_KEY = "DungeonPrevThunderTime";
    private static final String DUNGEON_PREV_RAINING_KEY = "DungeonPrevRaining";
    private static final String DUNGEON_PREV_THUNDERING_KEY = "DungeonPrevThundering";

    static final long EVENT_DURATION_TICKS = 3L * 60L * 20L;
    static final long ROLL_INTERVAL_TICKS = 24_000L;
    static final double DAILY_OPEN_CHANCE = 0.10D;
    private static final int RANDOM_LOCATION_ATTEMPTS = 12;
    private static final int MIN_EVENT_DISTANCE = 320;
    private static final int EVENT_DISTANCE_VARIATION = 160;

    private boolean active;
    @Nullable
    private BlockPos anchor;
    private long startedAt;
    private long endsAt;
    private long nextRollAt;
    private float orientationDegrees;
    private boolean weatherCaptured;
    private int previousClearTime;
    private int previousRainTime;
    private int previousThunderTime;
    private boolean previousRaining;
    private boolean previousThundering;
    private boolean dungeonWeatherCaptured;
    private int dungeonPreviousClearTime;
    private int dungeonPreviousRainTime;
    private int dungeonPreviousThunderTime;
    private boolean dungeonPreviousRaining;
    private boolean dungeonPreviousThundering;

    public static OverworldRiftEventData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new Factory<>(OverworldRiftEventData::new, OverworldRiftEventData::load),
            DATA_NAME
        );
    }

    public static OverworldRiftEventData load(CompoundTag tag, HolderLookup.Provider registries) {
        OverworldRiftEventData data = new OverworldRiftEventData();
        data.active = tag.getBoolean(ACTIVE_KEY);
        data.anchor = NbtUtils.readBlockPos(tag, ANCHOR_KEY).map(BlockPos::immutable).orElse(null);
        data.startedAt = Math.max(0L, tag.getLong(STARTED_AT_KEY));
        data.endsAt = Math.max(0L, tag.getLong(ENDS_AT_KEY));
        data.nextRollAt = Math.max(0L, tag.getLong(NEXT_ROLL_AT_KEY));
        data.orientationDegrees = data.anchor == null
            ? 0.0F
            : tag.contains(ORIENTATION_KEY, Tag.TAG_FLOAT)
                ? OverworldRiftGeometry.normalizeOrientation(tag.getFloat(ORIENTATION_KEY))
                : OverworldRiftGeometry.deterministicOrientation(data.anchor);
        data.weatherCaptured = tag.getBoolean(WEATHER_CAPTURED_KEY);
        data.previousClearTime = tag.getInt(PREV_CLEAR_TIME_KEY);
        data.previousRainTime = tag.getInt(PREV_RAIN_TIME_KEY);
        data.previousThunderTime = tag.getInt(PREV_THUNDER_TIME_KEY);
        data.previousRaining = tag.getBoolean(PREV_RAINING_KEY);
        data.previousThundering = tag.getBoolean(PREV_THUNDERING_KEY);
        data.dungeonWeatherCaptured = tag.getBoolean(DUNGEON_WEATHER_CAPTURED_KEY);
        data.dungeonPreviousClearTime = tag.getInt(DUNGEON_PREV_CLEAR_TIME_KEY);
        data.dungeonPreviousRainTime = tag.getInt(DUNGEON_PREV_RAIN_TIME_KEY);
        data.dungeonPreviousThunderTime = tag.getInt(DUNGEON_PREV_THUNDER_TIME_KEY);
        data.dungeonPreviousRaining = tag.getBoolean(DUNGEON_PREV_RAINING_KEY);
        data.dungeonPreviousThundering = tag.getBoolean(DUNGEON_PREV_THUNDERING_KEY);
        if (data.active && (data.anchor == null || data.endsAt <= data.startedAt)) {
            data.active = false;
            data.anchor = null;
            data.startedAt = 0L;
            data.endsAt = 0L;
            data.orientationDegrees = 0.0F;
            data.setDirty();
        }
        if (!data.active) {
            data.weatherCaptured = false;
            data.dungeonWeatherCaptured = false;
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(ACTIVE_KEY, active);
        if (anchor != null) {
            tag.put(ANCHOR_KEY, NbtUtils.writeBlockPos(anchor));
            tag.putFloat(ORIENTATION_KEY, orientationDegrees);
        }
        tag.putLong(STARTED_AT_KEY, startedAt);
        tag.putLong(ENDS_AT_KEY, endsAt);
        tag.putLong(NEXT_ROLL_AT_KEY, nextRollAt);
        tag.putBoolean(WEATHER_CAPTURED_KEY, weatherCaptured);
        tag.putInt(PREV_CLEAR_TIME_KEY, previousClearTime);
        tag.putInt(PREV_RAIN_TIME_KEY, previousRainTime);
        tag.putInt(PREV_THUNDER_TIME_KEY, previousThunderTime);
        tag.putBoolean(PREV_RAINING_KEY, previousRaining);
        tag.putBoolean(PREV_THUNDERING_KEY, previousThundering);
        tag.putBoolean(DUNGEON_WEATHER_CAPTURED_KEY, dungeonWeatherCaptured);
        tag.putInt(DUNGEON_PREV_CLEAR_TIME_KEY, dungeonPreviousClearTime);
        tag.putInt(DUNGEON_PREV_RAIN_TIME_KEY, dungeonPreviousRainTime);
        tag.putInt(DUNGEON_PREV_THUNDER_TIME_KEY, dungeonPreviousThunderTime);
        tag.putBoolean(DUNGEON_PREV_RAINING_KEY, dungeonPreviousRaining);
        tag.putBoolean(DUNGEON_PREV_THUNDERING_KEY, dungeonPreviousThundering);
        return tag;
    }

    public Transition tick(ServerLevel overworld) {
        if (!Level.OVERWORLD.equals(overworld.dimension())) {
            return Transition.NONE;
        }
        long now = overworld.getGameTime();
        if (active) {
            if (now >= endsAt) {
                close(overworld, now, "EXPIRED");
                return Transition.CLOSED;
            }
            long age = now - startedAt;
            holdDungeonWeather(overworld);
            if (OverworldRiftGeometry.gravityPullActive(age)) {
                pullPlayersIntoEntrance(overworld, age);
            }
            if (now >= startedAt + OverworldRiftGeometry.ENTRANCE_OPENING_TICKS
                && now < endsAt - OverworldRiftGeometry.ENTRANCE_CLOSING_TICKS) {
                transferPlayersInsideEntrance(overworld);
            }
            return Transition.NONE;
        }

        if (nextRollAt <= 0L) {
            nextRollAt = now + ROLL_INTERVAL_TICKS;
            setDirty();
            return Transition.NONE;
        }
        if (now < nextRollAt) {
            return Transition.NONE;
        }

        nextRollAt = now + ROLL_INTERVAL_TICKS;
        setDirty();
        if (!hubIsReady(overworld.getServer()) || overworld.random.nextDouble() >= DAILY_OPEN_CHANCE) {
            return Transition.NONE;
        }
        EventAnchor selected = chooseEventAnchor(overworld);
        if (selected == null) {
            return Transition.NONE;
        }
        open(overworld, selected, now, "DAILY_ROLL");
        return Transition.OPENED;
    }

    public boolean forceOpen(ServerLevel overworld, BlockPos requestedAnchor) {
        if (!Level.OVERWORLD.equals(overworld.dimension()) || !hubIsReady(overworld.getServer())) {
            return false;
        }
        BlockPos surface = surfaceAnchor(overworld, requestedAnchor.getX(), requestedAnchor.getZ());
        if (!isSafeAnchor(overworld, surface)) {
            return false;
        }
        ServerPlayer observer = overworld.players().stream()
            .filter(player -> player.isAlive() && !player.isSpectator())
            .min((left, right) -> Double.compare(
                left.distanceToSqr(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D),
                right.distanceToSqr(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D)
            ))
            .orElse(null);
        float orientation = observer == null
            ? OverworldRiftGeometry.deterministicOrientation(surface)
            : OverworldRiftGeometry.orientationFacing(surface, observer.position());
        open(overworld, new EventAnchor(surface, orientation), overworld.getGameTime(), "OPERATOR");
        return true;
    }

    public boolean forceClose(ServerLevel overworld) {
        if (!active) {
            return false;
        }
        close(overworld, overworld.getGameTime(), "OPERATOR");
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(
            active,
            anchor == null ? BlockPos.ZERO : anchor,
            active ? orientationDegrees : 0.0F,
            startedAt,
            endsAt,
            nextRollAt
        );
    }

    private void open(ServerLevel overworld, EventAnchor selected, long now, String reason) {
        active = true;
        anchor = selected.pos().immutable();
        orientationDegrees = OverworldRiftGeometry.normalizeOrientation(selected.orientationDegrees());
        startedAt = now;
        endsAt = now + EVENT_DURATION_TICKS;
        nextRollAt = Math.max(nextRollAt, endsAt + ROLL_INTERVAL_TICKS);
        captureWeather(overworld);
        overworld.setWeatherParameters(0, 7_200, true, true);
        holdDungeonWeather(overworld);
        setDirty();
        LOGGER.info(
            "[TD-RIFT] result=OPENED reason={} anchor={} orientationDegrees={} durationTicks={} dimension={}",
            reason, anchor, orientationDegrees, EVENT_DURATION_TICKS, overworld.dimension().location()
        );
    }

    private void close(ServerLevel overworld, long now, String reason) {
        BlockPos closedAnchor = anchor;
        active = false;
        anchor = null;
        startedAt = 0L;
        endsAt = 0L;
        orientationDegrees = 0.0F;
        nextRollAt = Math.max(nextRollAt, now + ROLL_INTERVAL_TICKS);
        restoreWeather(overworld);
        setDirty();
        LOGGER.info("[TD-RIFT] result=CLOSED reason={} anchor={}", reason, closedAnchor);
    }

    private void captureWeather(ServerLevel overworld) {
        ServerLevelData data = (ServerLevelData) overworld.getLevelData();
        weatherCaptured = true;
        previousClearTime = data.getClearWeatherTime();
        previousRainTime = data.getRainTime();
        previousThunderTime = data.getThunderTime();
        previousRaining = data.isRaining();
        previousThundering = data.isThundering();
    }

    private void holdDungeonWeather(ServerLevel overworld) {
        if (dungeonWeatherCaptured) {
            return;
        }
        ServerLevel dungeon = overworld.getServer().getLevel(ModDimensions.DUNGEON);
        if (dungeon == null) {
            return;
        }
        ServerLevelData dungeonData = (ServerLevelData) dungeon.getLevelData();
        dungeonWeatherCaptured = true;
        dungeonPreviousClearTime = dungeonData.getClearWeatherTime();
        dungeonPreviousRainTime = dungeonData.getRainTime();
        dungeonPreviousThunderTime = dungeonData.getThunderTime();
        dungeonPreviousRaining = dungeonData.isRaining();
        dungeonPreviousThundering = dungeonData.isThundering();
        dungeon.setWeatherParameters(12_000, 0, false, false);
        setDirty();
    }

    private void restoreWeather(ServerLevel overworld) {
        if (!weatherCaptured) {
            return;
        }
        overworld.setWeatherParameters(
            previousClearTime, previousRainTime, previousRaining, previousThundering
        );
        if (overworld.getLevelData() instanceof ServerLevelData levelData) {
            levelData.setThunderTime(previousThunderTime);
        }
        weatherCaptured = false;
        previousClearTime = 0;
        previousRainTime = 0;
        previousThunderTime = 0;
        previousRaining = false;
        previousThundering = false;

        if (dungeonWeatherCaptured) {
            ServerLevel dungeon = overworld.getServer().getLevel(ModDimensions.DUNGEON);
            if (dungeon != null) {
                dungeon.setWeatherParameters(
                    dungeonPreviousClearTime,
                    dungeonPreviousRainTime,
                    dungeonPreviousRaining,
                    dungeonPreviousThundering
                );
                if (dungeon.getLevelData() instanceof ServerLevelData dungeonData) {
                    dungeonData.setThunderTime(dungeonPreviousThunderTime);
                }
            }
            dungeonWeatherCaptured = false;
            dungeonPreviousClearTime = 0;
            dungeonPreviousRainTime = 0;
            dungeonPreviousThunderTime = 0;
            dungeonPreviousRaining = false;
            dungeonPreviousThundering = false;
        }
    }

    private void transferPlayersInsideEntrance(ServerLevel overworld) {
        if (anchor == null) {
            return;
        }
        HubTarget hub = resolveHubTarget(overworld.getServer());
        if (hub == null) {
            return;
        }
        for (ServerPlayer player : List.copyOf(overworld.players())) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            double bodyOffset = player.getBbHeight() * 0.5D;
            Vec3 previousCenter = new Vec3(player.xo, player.yo + bodyOffset, player.zo);
            Vec3 currentCenter = player.position().add(0.0D, bodyOffset, 0.0D);
            if (!OverworldRiftGeometry.intersectsPortal(
                anchor, orientationDegrees, previousCenter, currentCenter
            )) {
                continue;
            }
            hub.level().getChunkAt(hub.pos());
            boolean twilightRiftVariant =
                TwilightTeleportCompat.markNextExternalTeleportAsRift(player);
            if (!twilightRiftVariant) {
                player.playNotifySound(SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.8F, 0.8F);
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            try {
                player.teleportTo(
                    hub.level(),
                    hub.pos().getX() + 0.5D,
                    hub.pos().getY() + 1.0D,
                    hub.pos().getZ() + 0.5D,
                    player.getYRot(),
                    player.getXRot()
                );
            } finally {
                TwilightTeleportCompat.clearNextExternalTeleportAsRift(player);
            }

            if (!twilightRiftVariant) {
                RiftVoidWarpEntity warp = RiftVoidWarpEntity.create(
                    hub.level(),
                    new Vec3(
                        hub.pos().getX() + 0.5D,
                        hub.pos().getY(),
                        hub.pos().getZ() + 0.5D
                    ),
                    player.getYRot(),
                    1.0F
                );
                if (warp != null) {
                    hub.level().addFreshEntity(warp);
                }
            }
            LOGGER.info(
                "[TD-RIFT] result=ENTERED player={} source={} destination={} dimension={}",
                player.getUUID(), anchor, hub.pos(), hub.level().dimension().location()
            );
        }
    }

    private void pullPlayersIntoEntrance(ServerLevel overworld, long riftAge) {
        if (anchor == null) {
            return;
        }
        double radiusSquared = OverworldRiftGeometry.GRAVITY_PULL_RADIUS
            * OverworldRiftGeometry.GRAVITY_PULL_RADIUS;
        for (ServerPlayer player : List.copyOf(overworld.players())) {
            if (!player.isAlive() || player.isSpectator()
                || OverworldRiftGeometry.horizontalDistanceSquared(anchor, player.position()) > radiusSquared) {
                continue;
            }

            double bodyOffset = player.getBbHeight() * 0.5D;
            Vec3 bodyCenter = player.position().add(0.0D, bodyOffset, 0.0D);
            Vec3 target = new Vec3(
                anchor.getX() + 0.5D,
                OverworldRiftGeometry.centerY(anchor) - bodyOffset,
                anchor.getZ() + 0.5D
            );
            Vec3 difference = target.subtract(bodyCenter);
            double distance = difference.length();
            if (distance < 0.0001D || distance > OverworldRiftGeometry.GRAVITY_PULL_RADIUS) {
                continue;
            }

            Vec3 desired = difference.scale(
                OverworldRiftGeometry.gravityPullSpeed(distance, riftAge) / distance
            );
            Vec3 velocity = player.getDeltaMovement().scale(0.22D).add(desired.scale(0.78D));
            double velocityLength = velocity.length();
            if (velocityLength > OverworldRiftGeometry.GRAVITY_PULL_MAX_SPEED) {
                velocity = velocity.scale(OverworldRiftGeometry.GRAVITY_PULL_MAX_SPEED / velocityLength);
            }
            player.setDeltaMovement(velocity);
            player.hurtMarked = true;
            player.fallDistance = 0.0F;
        }
    }

    private static boolean hubIsReady(MinecraftServer server) {
        return resolveHubTarget(server) != null;
    }

    private static @Nullable HubTarget resolveHubTarget(MinecraftServer server) {
        ServerLevel dungeon = server.getLevel(ModDimensions.DUNGEON);
        if (dungeon == null) {
            return null;
        }
        DungeonRunManager manager = DungeonRunManager.get(dungeon);
        BlockPos target = manager.getHubSpawnAnchorPos();
        return manager.isHubEnabled() && target != null ? new HubTarget(dungeon, target) : null;
    }

    private static @Nullable EventAnchor chooseEventAnchor(ServerLevel overworld) {
        List<ServerPlayer> candidates = overworld.players().stream()
            .filter(player -> player.isAlive() && !player.isSpectator())
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        for (int attempt = 0; attempt < RANDOM_LOCATION_ATTEMPTS; attempt++) {
            ServerPlayer player = candidates.get(overworld.random.nextInt(candidates.size()));
            double angle = overworld.random.nextDouble() * Math.PI * 2.0D;
            int distance = MIN_EVENT_DISTANCE + overworld.random.nextInt(EVENT_DISTANCE_VARIATION + 1);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos selected = surfaceAnchor(overworld, x, z);
            if (isSafeAnchor(overworld, selected)) {
                return new EventAnchor(
                    selected,
                    OverworldRiftGeometry.orientationFacing(selected, player.position())
                );
            }
        }
        return null;
    }

    private static BlockPos surfaceAnchor(ServerLevel overworld, int x, int z) {
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static boolean isSafeAnchor(ServerLevel overworld, BlockPos candidate) {
        return overworld.getWorldBorder().isWithinBounds(candidate)
            && candidate.getY() > overworld.getMinBuildHeight() + 2
            && candidate.getY() < overworld.getMaxBuildHeight() - 64
            && overworld.getFluidState(candidate.below()).isEmpty()
            && overworld.getBlockState(candidate.below()).isFaceSturdy(
                overworld, candidate.below(), Direction.UP
            );
    }

    public enum Transition {
        NONE,
        OPENED,
        CLOSED
    }

    public record Snapshot(
        boolean active,
        BlockPos anchor,
        float orientationDegrees,
        long startedAt,
        long endsAt,
        long nextRollAt
    ) {
        public Snapshot {
            anchor = anchor.immutable();
            orientationDegrees = active
                ? OverworldRiftGeometry.normalizeOrientation(orientationDegrees)
                : 0.0F;
        }
    }

    private record EventAnchor(BlockPos pos, float orientationDegrees) {
        private EventAnchor {
            pos = pos.immutable();
        }
    }

    private record HubTarget(ServerLevel level, BlockPos pos) {
    }
}
