package com.mineclawd.tensurafogbarriers.service;

import com.mineclawd.tensurafogbarriers.block.GoldenFogBarrierBlock;
import com.mineclawd.tensurafogbarriers.block.FogPanelBackingBlock;
import com.mineclawd.tensurafogbarriers.config.FogBarrierConfig;
import com.mineclawd.tensurafogbarriers.data.FogAnimationStyle;
import com.mineclawd.tensurafogbarriers.data.FogBarrierPanel;
import com.mineclawd.tensurafogbarriers.data.FogBarrierSavedData;
import com.mineclawd.tensurafogbarriers.data.FogVariant;
import com.mineclawd.tensurafogbarriers.network.FogBarrierSnapshotPayload;
import com.mineclawd.tensurafogbarriers.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FogBarrierService {
    private static final Map<MinecraftServer, FogBarrierService> ACTIVE = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final String IMPACT_PANEL_TAG = "TensuraFogBarrierImpactPanel";
    private static final String IMPACT_TIME_TAG = "TensuraFogBarrierImpactTime";
    private static final int MAX_CHUNKS_PER_QUERY = 256;
    private static final double MAX_RESPONSE_SPEED = 6.0D;

    private final MinecraftServer server;
    private final FogBarrierSavedData data;
    private final Map<ResourceLocation, List<FogBarrierPanel>> panelCache = new HashMap<>();
    private final Map<ResourceLocation, SpatialPanelIndex> panelIndexCache = new HashMap<>();
    private final Map<UUID, PlayerPositionSample> playerPositionSamples = new HashMap<>();

    private FogBarrierService(MinecraftServer server) {
        this.server = server;
        this.data = FogBarrierSavedData.get(server);
    }

    public static FogBarrierService get(MinecraftServer server) {
        synchronized (ACTIVE) {
            return ACTIVE.computeIfAbsent(server, FogBarrierService::new);
        }
    }

    public static void unbind(MinecraftServer server) {
        ACTIVE.remove(server);
    }

    public FogBarrierPanel create(ServerLevel level, Vec3 first, Vec3 second, String type) {
        return create(level, first, second, type, FogVariant.fromType(type));
    }

    public FogBarrierPanel create(ServerLevel level, Vec3 first, Vec3 second, String type, FogVariant variant) {
        requireServerThread();
        if (panels(level).size() >= FogBarrierSavedData.MAX_PANELS_PER_DIMENSION) {
            throw new IllegalStateException("Fog barrier limit reached for this dimension");
        }
        FogBarrierPanel validated = FogBarrierPanel.fromCorners(
            1, level.dimension().location(), first, second, type, level.getGameTime()
        ).withVariant(variant);
        int id = data.allocateId();
        FogBarrierPanel panel = validated.withId(id);
        data.put(panel);
        refresh(panel.dimension());
        if (!reconcilePanelBacking(level, panel, false, false)) {
            data.remove(panel.id());
            refresh(panel.dimension());
            removeUnownedPanelBacking(level, panel);
            throw new IllegalStateException("Fog barrier backing could not be materialized");
        }
        syncDimension(level);
        Vec3 center = panel.visualBounds().getCenter();
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 0.78F);
        return panel;
    }

    public boolean remove(int id) {
        requireServerThread();
        FogBarrierPanel removed = data.remove(id);
        if (removed == null) return false;
        refresh(removed.dimension());
        ServerLevel level = level(removed.dimension());
        if (level != null) {
            removeUnownedPanelBacking(level, removed);
            syncDimension(level);
            Vec3 center = removed.visualBounds().getCenter();
            level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.75F, 1.12F);
        }
        return true;
    }

    public boolean setSolid(int id, boolean solid) {
        requireServerThread();
        FogBarrierPanel panel = data.getPanel(id).orElse(null);
        if (panel == null) return false;
        ServerLevel level = level(panel.dimension());
        return level != null && reconcilePanelBacking(level, panel, solid, true);
    }

    public boolean recolor(int id, FogVariant variant) {
        requireServerThread();
        FogBarrierPanel existing = data.getPanel(id).orElse(null);
        if (existing == null || variant == null) return false;
        if (existing.variant() == variant) return true;
        FogBarrierPanel updated = existing.withVariant(variant);
        data.put(updated);
        refresh(updated.dimension());
        ServerLevel level = level(updated.dimension());
        if (level != null) {
            reconcilePanelBacking(level, updated, false, false);
            syncDimension(level);
            Vec3 center = updated.visualBounds().getCenter();
            level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.8F, 0.72F + variant.ordinal() * 0.1F);
        }
        return true;
    }

    public boolean restyle(int id, FogAnimationStyle style) {
        requireServerThread();
        FogBarrierPanel existing = data.getPanel(id).orElse(null);
        if (existing == null || style == null) return false;
        if (existing.style() == style) return true;
        FogBarrierPanel updated = existing.withStyle(style);
        data.put(updated);
        refresh(updated.dimension());
        ServerLevel level = level(updated.dimension());
        if (level != null) {
            reconcilePanelBacking(level, updated, false, false);
            syncDimension(level);
            Vec3 center = updated.visualBounds().getCenter();
            level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.8F, 0.82F + style.ordinal() * 0.08F);
        }
        return true;
    }

    public boolean cycleVariant(ServerPlayer player, int id) {
        requireServerThread();
        if (player == null || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return false;
        FogBarrierPanel panel = data.getPanel(id).orElse(null);
        if (panel == null || !(player.level() instanceof ServerLevel level)) return false;
        if (!panel.dimension().equals(level.dimension().location())) return false;
        if (panel.visualBounds().distanceToSqr(player.getEyePosition()) > 64.0D) return false;

        FogVariant next = panel.variant().next();
        if (!recolor(id, next)) return false;
        player.displayClientMessage(Component.literal("Fog barrier color: " + next.displayName()), true);
        return true;
    }

    public boolean cycleStyle(ServerPlayer player, int id) {
        requireServerThread();
        if (player == null || !player.isShiftKeyDown() || !player.getMainHandItem().is(Items.CLOCK)) return false;
        FogBarrierPanel panel = data.getPanel(id).orElse(null);
        if (panel == null || !(player.level() instanceof ServerLevel level)) return false;
        if (!panel.dimension().equals(level.dimension().location())) return false;
        if (panel.visualBounds().distanceToSqr(player.getEyePosition()) > 64.0D) return false;

        FogAnimationStyle next = panel.style().next();
        if (!restyle(id, next)) return false;
        player.displayClientMessage(Component.literal("Fog barrier animation: " + next.displayName()), true);
        return true;
    }

    public static boolean recolorAny(int id, FogVariant variant) {
        List<FogBarrierService> services = new ArrayList<>();
        synchronized (ACTIVE) {
            services.addAll(ACTIVE.values());
        }
        for (FogBarrierService service : services) {
            if (service.data.getPanel(id).isPresent()) return service.recolor(id, variant);
        }
        return false;
    }

    public static boolean restyleAny(int id, FogAnimationStyle style) {
        List<FogBarrierService> services = new ArrayList<>();
        synchronized (ACTIVE) {
            services.addAll(ACTIVE.values());
        }
        for (FogBarrierService service : services) {
            if (service.data.getPanel(id).isPresent()) return service.restyle(id, style);
        }
        return false;
    }

    public static boolean setSolidAny(int id, boolean solid) {
        List<FogBarrierService> services = new ArrayList<>();
        synchronized (ACTIVE) {
            services.addAll(ACTIVE.values());
        }
        for (FogBarrierService service : services) {
            if (service.data.getPanel(id).isPresent()) return service.setSolid(id, solid);
        }
        return false;
    }

    public static boolean removeAny(int id) {
        List<FogBarrierService> services = new ArrayList<>();
        synchronized (ACTIVE) {
            services.addAll(ACTIVE.values());
        }
        for (FogBarrierService service : services) {
            if (service.data.getPanel(id).isPresent()) return service.remove(id);
        }
        return false;
    }

    public int clear(ServerLevel level) {
        requireServerThread();
        List<FogBarrierPanel> removedPanels = panels(level);
        int removed = data.clear(level.dimension().location());
        if (removed > 0) {
            refresh(level.dimension().location());
            for (FogBarrierPanel panel : removedPanels) removeUnownedPanelBacking(level, panel);
            syncDimension(level);
        }
        return removed;
    }

    public void reconcileLoadedBackings() {
        requireServerThread();
        for (ServerLevel level : server.getAllLevels()) {
            for (FogBarrierPanel panel : panels(level)) {
                reconcilePanelBacking(level, panel, false, false);
            }
        }
    }

    public List<FogBarrierPanel> panels(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        List<FogBarrierPanel> panels = panelCache.computeIfAbsent(dimension, data::forDimension);
        panelIndexCache.computeIfAbsent(dimension, ignored -> SpatialPanelIndex.build(panels));
        return panels;
    }

    public void syncPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        PacketDistributor.sendToPlayer(player, FogBarrierSnapshotPayload.fromPanels(panels(level)));
    }

    public void syncDimension(ServerLevel level) {
        FogBarrierSnapshotPayload payload = FogBarrierSnapshotPayload.fromPanels(panels(level));
        for (ServerPlayer player : level.players()) PacketDistributor.sendToPlayer(player, payload);
    }

    public void collide(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.isRemoved()) return;
        Vec3 previousPosition = previousPosition(entity, level);
        try {
            collideInternal(entity, level, previousPosition);
        } finally {
            if (entity instanceof ServerPlayer player && !entity.isRemoved()) {
                playerPositionSamples.put(player.getUUID(), new PlayerPositionSample(
                    level.dimension().location(), player.position(), level.getGameTime()
                ));
            }
        }
    }

    private void collideInternal(Entity entity, ServerLevel level, Vec3 previousPosition) {
        if (entity.noPhysics || entity.isSpectator()) return;
        AABB bounds = entity.getBoundingBox();
        if (bounds.getSize() > 64.0D) return;

        if (entity instanceof ServerPlayer player) {
            if (bounceFromTeleportedBlock(level, player, previousPosition)) return;
            if (player.horizontalCollision && bounceFromPlacedBlock(level, player, bounds)) return;
        }

        List<FogBarrierPanel> panels = panels(level);
        if (panels.isEmpty()) return;
        if (entity instanceof ServerPlayer player && player.isCreative() && FogBarrierConfig.CREATIVE_MODE_EXEMPT.get()) return;

        AABB sweptBounds = sweptBounds(entity, bounds, previousPosition);
        int minChunkX = Mth.floor(sweptBounds.minX) >> 4;
        int maxChunkX = Mth.floor(sweptBounds.maxX) >> 4;
        int minChunkZ = Mth.floor(sweptBounds.minZ) >> 4;
        int maxChunkZ = Mth.floor(sweptBounds.maxZ) >> 4;
        long chunkCount = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);

        if (chunkCount > MAX_CHUNKS_PER_QUERY) {
            for (FogBarrierPanel panel : panels) {
                if (collideWithPanel(level, entity, bounds, sweptBounds, panel, previousPosition)) return;
            }
            return;
        }

        SpatialPanelIndex index = panelIndexCache.get(level.dimension().location());
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                List<FogBarrierPanel> candidates = index.at(chunkX, chunkZ);
                for (FogBarrierPanel panel : candidates) {
                    if (collideWithPanel(level, entity, bounds, sweptBounds, panel, previousPosition)) return;
                }
            }
        }
    }

    public boolean reflectPlacedBlockProjectile(Projectile projectile, BlockHitResult hit, Direction.Axis axis) {
        if (!(projectile.level() instanceof ServerLevel level) || projectile.isRemoved() || !FogBarrierConfig.REFLECT_PROJECTILES.get()) {
            return false;
        }
        int impactKey = 0x80000000 ^ hit.getBlockPos().hashCode();
        if (cooldownActive(projectile, impactKey, level.getGameTime(), FogBarrierConfig.PROJECTILE_COOLDOWN_TICKS.get())) {
            return true;
        }

        BlockPos pos = hit.getBlockPos();
        double center = axis == Direction.Axis.X ? pos.getX() + 0.5D : pos.getZ() + 0.5D;
        double oldNormal = axis == Direction.Axis.X ? projectile.xo : projectile.zo;
        double normal = axis == Direction.Axis.X ? projectile.getX() : projectile.getZ();
        double side = sideOf(center, oldNormal, normal);
        double halfWidth = Math.max(0.03D, projectile.getBbWidth() * 0.5D);
        double target = axis == Direction.Axis.X
            ? (side < 0.0D ? pos.getX() - halfWidth - 0.04D : pos.getX() + 1.0D + halfWidth + 0.04D)
            : (side < 0.0D ? pos.getZ() - halfWidth - 0.04D : pos.getZ() + 1.0D + halfWidth + 0.04D);
        double x = axis == Direction.Axis.X ? target : projectile.getX();
        double z = axis == Direction.Axis.Z ? target : projectile.getZ();
        Vec3 reflected = reflectedVelocity(projectile.getDeltaMovement(), axis, side);

        projectile.setPos(x, projectile.getY(), z);
        projectile.setDeltaMovement(reflected);
        projectile.xo = x;
        projectile.zo = z;
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;
        markImpact(projectile, impactKey, level.getGameTime());
        playImpactEffects(level, hit.getLocation(), true);
        return true;
    }

    private boolean collideWithPanel(ServerLevel level, Entity entity, AABB bounds, AABB sweptBounds,
                                     FogBarrierPanel panel, Vec3 previousPosition) {
        if (!panel.overlapsVerticalAndLateral(sweptBounds)) return false;
        double oldNormal = panel.axis() == FogBarrierPanel.Axis.X ? previousPosition.x : previousPosition.z;
        double normal = panel.axis() == FogBarrierPanel.Axis.X ? entity.getX() : entity.getZ();
        boolean intersects = bounds.intersects(panel.collisionBounds());
        boolean crossed = crossesPanel(entity, previousPosition, panel);
        if (!intersects && !crossed) return false;

        int cooldown = entity instanceof Projectile
            ? FogBarrierConfig.PROJECTILE_COOLDOWN_TICKS.get()
            : FogBarrierConfig.BOUNCE_COOLDOWN_TICKS.get();
        if (cooldownActive(entity, panel.id(), level.getGameTime(), cooldown)) return true;

        double side = sideOf(panel.plane(), oldNormal, normal);
        if (entity instanceof Projectile projectile && FogBarrierConfig.REFLECT_PROJECTILES.get()) {
            reflectPanelProjectile(level, projectile, panel, side);
        } else {
            pushOutside(level, entity, panel, side, FogBarrierConfig.BOUNCE_ENABLED.get());
        }
        markImpact(entity, panel.id(), level.getGameTime());
        return true;
    }

    private void reflectPanelProjectile(ServerLevel level, Projectile projectile, FogBarrierPanel panel, double side) {
        double halfWidth = Math.max(0.03D, projectile.getBbWidth() * 0.5D);
        double target = panel.plane() + side * (FogBarrierPanel.COLLISION_THICKNESS * 0.5D + halfWidth + 0.04D);
        double x = panel.axis() == FogBarrierPanel.Axis.X ? target : projectile.getX();
        double z = panel.axis() == FogBarrierPanel.Axis.Z ? target : projectile.getZ();
        Direction.Axis axis = panel.axis() == FogBarrierPanel.Axis.X ? Direction.Axis.X : Direction.Axis.Z;
        Vec3 reflected = reflectedVelocity(projectile.getDeltaMovement(), axis, side);

        projectile.setPos(x, projectile.getY(), z);
        projectile.setDeltaMovement(reflected);
        projectile.xo = x;
        projectile.zo = z;
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;
        playImpactEffects(level, projectile.position(), true);
    }

    private void pushOutside(ServerLevel level, Entity entity, FogBarrierPanel panel, double side, boolean bounce) {
        double halfWidth = Math.max(0.05D, entity.getBbWidth() * 0.5D);
        double target = panel.plane() + side * (FogBarrierPanel.COLLISION_THICKNESS * 0.5D + halfWidth + 0.025D);
        double x = panel.axis() == FogBarrierPanel.Axis.X ? target : entity.getX();
        double z = panel.axis() == FogBarrierPanel.Axis.Z ? target : entity.getZ();
        Vec3 velocity = entity.getDeltaMovement();
        double normalSpeed = panel.axis() == FogBarrierPanel.Axis.X ? velocity.x : velocity.z;
        double outwardSpeed = bounce
            ? Math.min(MAX_RESPONSE_SPEED, Math.max(FogBarrierConfig.BOUNCE_STRENGTH.get(), Math.abs(normalSpeed)))
            : Math.max(0.0D, normalSpeed * side);
        double vertical = bounce ? Math.max(velocity.y, FogBarrierConfig.BOUNCE_VERTICAL_LIFT.get()) : velocity.y;
        Vec3 response = panel.axis() == FogBarrierPanel.Axis.X
            ? new Vec3(side * outwardSpeed, vertical, velocity.z)
            : new Vec3(velocity.x, vertical, side * outwardSpeed);

        moveEntity(entity, x, z);
        entity.setDeltaMovement(response);
        entity.hurtMarked = true;
        if (bounce) playImpactEffects(level, entity.position(), false);
    }

    private boolean bounceFromTeleportedBlock(ServerLevel level, ServerPlayer player, Vec3 previousPosition) {
        if (!FogBarrierConfig.BOUNCE_ENABLED.get()
            || (player.isCreative() && FogBarrierConfig.CREATIVE_MODE_EXEMPT.get())) return false;
        Vec3 current = player.position();
        if (current.distanceToSqr(previousPosition) < 2.25D) return false;

        double centerHeight = Math.max(0.1D, player.getBbHeight() * 0.5D);
        BlockHitResult hit = level.clip(new ClipContext(
            previousPosition.add(0.0D, centerHeight, 0.0D),
            current.add(0.0D, centerHeight, 0.0D),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));
        if (!level.getBlockState(hit.getBlockPos()).is(ModBlocks.GOLDEN_FOG_BARRIER.get())) return false;

        BlockState state = level.getBlockState(hit.getBlockPos());
        Direction.Axis axis = state.getValue(GoldenFogBarrierBlock.AXIS);
        double center = axis == Direction.Axis.X ? hit.getBlockPos().getX() + 0.5D : hit.getBlockPos().getZ() + 0.5D;
        double oldNormal = axis == Direction.Axis.X ? previousPosition.x : previousPosition.z;
        double side = oldNormal <= center ? -1.0D : 1.0D;
        Vec3 incoming = current.subtract(previousPosition);
        double normalSpeed = axis == Direction.Axis.X ? incoming.x : incoming.z;
        double outwardSpeed = Math.min(MAX_RESPONSE_SPEED,
            Math.max(FogBarrierConfig.BOUNCE_STRENGTH.get(), Math.abs(normalSpeed)));
        Vec3 response = axis == Direction.Axis.X
            ? new Vec3(side * outwardSpeed, FogBarrierConfig.BOUNCE_VERTICAL_LIFT.get(), 0.0D)
            : new Vec3(0.0D, FogBarrierConfig.BOUNCE_VERTICAL_LIFT.get(), side * outwardSpeed);

        player.connection.teleport(previousPosition.x, previousPosition.y, previousPosition.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(response);
        player.hurtMarked = true;
        int impactKey = 0x20000000 ^ hit.getBlockPos().hashCode();
        if (!cooldownActive(player, impactKey, level.getGameTime(), FogBarrierConfig.BOUNCE_COOLDOWN_TICKS.get())) {
            markImpact(player, impactKey, level.getGameTime());
            playImpactEffects(level, hit.getLocation(), false);
        }
        return true;
    }

    private boolean bounceFromPlacedBlock(ServerLevel level, ServerPlayer player, AABB bounds) {
        if (!FogBarrierConfig.BOUNCE_ENABLED.get()
            || (player.isCreative() && FogBarrierConfig.CREATIVE_MODE_EXEMPT.get())) return false;

        AABB search = bounds.inflate(0.09D, 0.02D, 0.09D);
        int minX = Mth.floor(search.minX);
        int maxX = Mth.floor(search.maxX);
        int minY = Mth.floor(search.minY);
        int maxY = Mth.floor(search.maxY);
        int minZ = Mth.floor(search.minZ);
        int maxZ = Mth.floor(search.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(ModBlocks.GOLDEN_FOG_BARRIER.get())) continue;
                    Direction.Axis axis = state.getValue(GoldenFogBarrierBlock.AXIS);
                    double center = axis == Direction.Axis.X ? x + 0.5D : z + 0.5D;
                    double oldNormal = axis == Direction.Axis.X ? player.xo : player.zo;
                    double normal = axis == Direction.Axis.X ? player.getX() : player.getZ();
                    double side = sideOf(center, oldNormal, normal);
                    if ((normal - oldNormal) * side >= -1.0E-5D) continue;

                    int impactKey = 0x40000000 ^ pos.hashCode();
                    if (cooldownActive(player, impactKey, level.getGameTime(), FogBarrierConfig.BOUNCE_COOLDOWN_TICKS.get())) {
                        return true;
                    }

                    double halfWidth = Math.max(0.05D, player.getBbWidth() * 0.5D);
                    double target = axis == Direction.Axis.X
                        ? (side < 0.0D ? x - halfWidth - 0.035D : x + 1.0D + halfWidth + 0.035D)
                        : (side < 0.0D ? z - halfWidth - 0.035D : z + 1.0D + halfWidth + 0.035D);
                    double targetX = axis == Direction.Axis.X ? target : player.getX();
                    double targetZ = axis == Direction.Axis.Z ? target : player.getZ();
                    Vec3 velocity = player.getDeltaMovement();
                    double normalSpeed = axis == Direction.Axis.X ? velocity.x : velocity.z;
                    double outwardSpeed = Math.min(MAX_RESPONSE_SPEED,
                        Math.max(FogBarrierConfig.BOUNCE_STRENGTH.get(), Math.abs(normalSpeed)));
                    Vec3 response = axis == Direction.Axis.X
                        ? new Vec3(side * outwardSpeed, Math.max(velocity.y, FogBarrierConfig.BOUNCE_VERTICAL_LIFT.get()), velocity.z)
                        : new Vec3(velocity.x, Math.max(velocity.y, FogBarrierConfig.BOUNCE_VERTICAL_LIFT.get()), side * outwardSpeed);
                    moveEntity(player, targetX, targetZ);
                    player.setDeltaMovement(response);
                    player.hurtMarked = true;
                    markImpact(player, impactKey, level.getGameTime());
                    playImpactEffects(level, player.position(), false);
                    return true;
                }
            }
        }
        return false;
    }

    private Vec3 reflectedVelocity(Vec3 velocity, Direction.Axis axis, double side) {
        double normalSpeed = axis == Direction.Axis.X ? velocity.x : velocity.z;
        double reflectedSpeed = Math.min(MAX_RESPONSE_SPEED, Math.max(0.25D,
            Math.abs(normalSpeed) * FogBarrierConfig.PROJECTILE_SPEED_MULTIPLIER.get()));
        return axis == Direction.Axis.X
            ? new Vec3(side * reflectedSpeed, velocity.y, velocity.z)
            : new Vec3(velocity.x, velocity.y, side * reflectedSpeed);
    }

    private void moveEntity(Entity entity, double x, double z) {
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(x, entity.getY(), z, player.getYRot(), player.getXRot());
        } else {
            entity.setPos(x, entity.getY(), z);
        }
        entity.xo = x;
        entity.zo = z;
    }

    private void playImpactEffects(ServerLevel level, Vec3 position, boolean projectile) {
        if (!FogBarrierConfig.IMPACT_EFFECTS.get()) return;
        level.playSound(null, position.x, position.y, position.z, SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.BLOCKS, projectile ? 0.72F : 0.9F, projectile ? 1.35F : 0.92F);
        level.sendParticles(ParticleTypes.END_ROD, position.x, position.y + 0.5D, position.z,
            projectile ? 6 : 10, 0.16D, 0.28D, 0.16D, 0.025D);
    }

    private static double sideOf(double plane, double oldNormal, double normal) {
        if (oldNormal < plane) return -1.0D;
        if (oldNormal > plane) return 1.0D;
        return normal <= plane ? -1.0D : 1.0D;
    }

    private static boolean cooldownActive(Entity entity, int impactKey, long gameTime, int cooldownTicks) {
        CompoundTag tag = entity.getPersistentData();
        if (tag.getInt(IMPACT_PANEL_TAG) != impactKey) return false;
        long elapsed = gameTime - tag.getLong(IMPACT_TIME_TAG);
        return elapsed >= 0L && elapsed < cooldownTicks;
    }

    private static void markImpact(Entity entity, int impactKey, long gameTime) {
        CompoundTag tag = entity.getPersistentData();
        tag.putInt(IMPACT_PANEL_TAG, impactKey);
        tag.putLong(IMPACT_TIME_TAG, gameTime);
    }

    private Vec3 previousPosition(Entity entity, ServerLevel level) {
        if (entity instanceof ServerPlayer player) {
            PlayerPositionSample sample = playerPositionSamples.get(player.getUUID());
            if (sample != null && sample.dimension().equals(level.dimension().location())) {
                long elapsed = level.getGameTime() - sample.gameTime();
                if (elapsed >= 0L && elapsed <= 5L) return sample.position();
            }
        }
        return new Vec3(entity.xo, entity.yo, entity.zo);
    }

    private static boolean crossesPanel(Entity entity, Vec3 previous, FogBarrierPanel panel) {
        Vec3 current = entity.position();
        double oldNormal = panel.axis() == FogBarrierPanel.Axis.X ? previous.x : previous.z;
        double newNormal = panel.axis() == FogBarrierPanel.Axis.X ? current.x : current.z;
        double normalDelta = newNormal - oldNormal;
        if (Math.abs(normalDelta) < 1.0E-7D) return false;
        double fraction = (panel.plane() - oldNormal) / normalDelta;
        if (fraction < 0.0D || fraction > 1.0D) return false;

        double feetY = previous.y + (current.y - previous.y) * fraction;
        double lateral = panel.axis() == FogBarrierPanel.Axis.X
            ? previous.z + (current.z - previous.z) * fraction
            : previous.x + (current.x - previous.x) * fraction;
        double halfWidth = Math.max(0.03D, entity.getBbWidth() * 0.5D);
        return feetY + entity.getBbHeight() > panel.minY()
            && feetY < panel.maxY()
            && lateral + halfWidth > panel.minLateral()
            && lateral - halfWidth < panel.maxLateral();
    }

    private static AABB sweptBounds(Entity entity, AABB current, Vec3 previous) {
        double dx = previous.x - entity.getX();
        double dy = previous.y - entity.getY();
        double dz = previous.z - entity.getZ();
        return new AABB(
            Math.min(current.minX, current.minX + dx), Math.min(current.minY, current.minY + dy), Math.min(current.minZ, current.minZ + dz),
            Math.max(current.maxX, current.maxX + dx), Math.max(current.maxY, current.maxY + dy), Math.max(current.maxZ, current.maxZ + dz)
        );
    }

    private ServerLevel level(ResourceLocation dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) return level;
        }
        return null;
    }

    private void refresh(ResourceLocation dimension) {
        List<FogBarrierPanel> panels = data.forDimension(dimension);
        panelCache.put(dimension, panels);
        panelIndexCache.put(dimension, SpatialPanelIndex.build(panels));
    }

    private boolean reconcilePanelBacking(
        ServerLevel level,
        FogBarrierPanel panel,
        boolean solid,
        boolean updateExisting
    ) {
        Direction.Axis axis = panel.axis() == FogBarrierPanel.Axis.X ? Direction.Axis.X : Direction.Axis.Z;
        BlockState presentation = ModBlocks.FOG_PANEL_BACKING.get().defaultBlockState()
            .setValue(FogPanelBackingBlock.AXIS, axis)
            .setValue(FogPanelBackingBlock.VARIANT, panel.variant())
            .setValue(FogPanelBackingBlock.STYLE, panel.style());
        boolean complete = true;
        for (BlockPos pos : panelBackingPositions(panel)) {
            BlockState existing = level.getBlockState(pos);
            if (existing.is(ModBlocks.FOG_PANEL_BACKING.get())) {
                boolean desiredSolid = updateExisting ? solid : existing.getValue(FogPanelBackingBlock.SOLID);
                BlockState desired = presentation.setValue(FogPanelBackingBlock.SOLID, desiredSolid);
                if (!existing.equals(desired)) {
                    complete &= level.setBlock(pos, desired, Block.UPDATE_ALL);
                }
            } else if (existing.isAir()) {
                BlockState desired = presentation.setValue(FogPanelBackingBlock.SOLID, solid);
                complete &= level.setBlock(pos, desired, Block.UPDATE_ALL);
            }
        }
        return complete;
    }

    private void removeUnownedPanelBacking(ServerLevel level, FogBarrierPanel removed) {
        List<FogBarrierPanel> remaining = panels(level);
        for (BlockPos pos : panelBackingPositions(removed)) {
            if (!level.getBlockState(pos).is(ModBlocks.FOG_PANEL_BACKING.get())) continue;
            if (remaining.stream().noneMatch(panel -> panelCoversBlock(panel, pos))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static Iterable<BlockPos> panelBackingPositions(FogBarrierPanel panel) {
        int minY = Mth.floor(panel.minY());
        int maxY = Mth.ceil(panel.maxY()) - 1;
        int minLateral = Mth.floor(panel.minLateral());
        int maxLateral = Mth.ceil(panel.maxLateral()) - 1;
        int plane = Mth.floor(panel.plane());
        BlockPos min = panel.axis() == FogBarrierPanel.Axis.X
            ? new BlockPos(plane, minY, minLateral)
            : new BlockPos(minLateral, minY, plane);
        BlockPos max = panel.axis() == FogBarrierPanel.Axis.X
            ? new BlockPos(plane, maxY, maxLateral)
            : new BlockPos(maxLateral, maxY, plane);
        return BlockPos.betweenClosed(min, max);
    }

    private static boolean panelCoversBlock(FogBarrierPanel panel, BlockPos pos) {
        int plane = Mth.floor(panel.plane());
        if (panel.axis() == FogBarrierPanel.Axis.X && pos.getX() != plane) return false;
        if (panel.axis() == FogBarrierPanel.Axis.Z && pos.getZ() != plane) return false;
        int lateral = panel.axis() == FogBarrierPanel.Axis.X ? pos.getZ() : pos.getX();
        return pos.getY() >= Mth.floor(panel.minY())
            && pos.getY() < Mth.ceil(panel.maxY())
            && lateral >= Mth.floor(panel.minLateral())
            && lateral < Mth.ceil(panel.maxLateral());
    }

    private void requireServerThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Fog barrier mutation must run on the server thread");
    }

    private record PlayerPositionSample(ResourceLocation dimension, Vec3 position, long gameTime) { }

    private static final class SpatialPanelIndex {
        private final Map<Long, List<FogBarrierPanel>> byChunk;

        private SpatialPanelIndex(Map<Long, List<FogBarrierPanel>> byChunk) {
            this.byChunk = byChunk;
        }

        static SpatialPanelIndex build(List<FogBarrierPanel> panels) {
            Map<Long, List<FogBarrierPanel>> mutable = new HashMap<>();
            for (FogBarrierPanel panel : panels) {
                AABB bounds = panel.collisionBounds();
                int minChunkX = Mth.floor(bounds.minX) >> 4;
                int maxChunkX = Mth.floor(bounds.maxX) >> 4;
                int minChunkZ = Mth.floor(bounds.minZ) >> 4;
                int maxChunkZ = Mth.floor(bounds.maxZ) >> 4;
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        mutable.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), ignored -> new ArrayList<>()).add(panel);
                    }
                }
            }
            Map<Long, List<FogBarrierPanel>> immutable = new HashMap<>();
            mutable.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
            return new SpatialPanelIndex(Map.copyOf(immutable));
        }

        List<FogBarrierPanel> at(int chunkX, int chunkZ) {
            return byChunk.getOrDefault(ChunkPos.asLong(chunkX, chunkZ), List.of());
        }
    }
}
