package com.mineclawd.tensuradungeon.entity;

import com.mineclawd.tensuradungeon.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class RiftVoidWarpEntity extends Entity implements GeoEntity {
    private static final RawAnimation START = RawAnimation.begin().thenPlay("animation.rift_magic_circle.start");
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.rift_magic_circle.spin");
    private static final RawAnimation END = RawAnimation.begin().thenPlayAndHold("animation.rift_magic_circle.end");

    private static final int START_TICKS = 20;
    private static final int SPIN_TICKS = 50;
    private static final int END_TICKS = 20;
    private static final int TOTAL_TICKS = START_TICKS + SPIN_TICKS + END_TICKS;

    private static final double PARTICLE_TOP = 22.0D;
    private static final double PARTICLE_BOTTOM = 6.0D;

    private static final EntityDataAccessor<Float> ORIENTATION = SynchedEntityData.defineId(
        RiftVoidWarpEntity.class,
        EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(
        RiftVoidWarpEntity.class,
        EntityDataSerializers.FLOAT
    );

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RiftVoidWarpEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static RiftVoidWarpEntity create(
        ServerLevel level,
        Vec3 position,
        float orientationDegrees,
        float size
    ) {
        RiftVoidWarpEntity entity = ModEntities.RIFT_VOID_WARP.get().create(level);
        if (entity == null) {
            return null;
        }
        entity.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        entity.setOrientationDegrees(orientationDegrees);
        entity.setSize(size);
        return entity;
    }

    public float getOrientationDegrees() {
        return this.entityData.get(ORIENTATION);
    }

    public void setOrientationDegrees(float orientationDegrees) {
        this.entityData.set(ORIENTATION, orientationDegrees);
    }

    public float getSize() {
        return this.entityData.get(SIZE);
    }

    public void setSize(float size) {
        this.entityData.set(SIZE, Math.max(0.05F, size));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ORIENTATION, 0.0F);
        builder.define(SIZE, 1.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);
        if (this.tickCount >= TOTAL_TICKS) {
            this.discard();
            return;
        }
        if (this.level().isClientSide) {
            spawnFallingDust();
        }
    }

    private void spawnFallingDust() {
        int count;
        if (this.tickCount < START_TICKS) {
            count = 2;
        } else if (this.tickCount < START_TICKS + SPIN_TICKS) {
            count = 5;
        } else {
            count = 3;
        }
        float circleRadius = this.getSize() * 4.2F;
        for (int index = 0; index < count; index++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double radial = circleRadius * (0.35D + this.random.nextDouble() * 0.65D);
            double height = PARTICLE_BOTTOM + this.random.nextDouble() * (PARTICLE_TOP - PARTICLE_BOTTOM);
            double speed = 0.28D + this.random.nextDouble() * 0.28D;
            this.level().addParticle(
                new DustParticleOptions(new Vector3f(1.0F, 0.13F, 0.17F), 1.15F),
                this.getX() + Math.cos(angle) * radial,
                this.getY() + height,
                this.getZ() + Math.sin(angle) * radial,
                this.random.nextGaussian() * 0.02D,
                -speed,
                this.random.nextGaussian() * 0.02D
            );
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float size = this.getSize();
        return EntityDimensions.fixed(size, size);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "warp", this::warpAnimation));
    }

    private PlayState warpAnimation(AnimationState<RiftVoidWarpEntity> state) {
        if (this.tickCount < START_TICKS) {
            state.setAndContinue(START);
        } else if (this.tickCount < START_TICKS + SPIN_TICKS) {
            state.setAndContinue(SPIN);
        } else {
            state.setAndContinue(END);
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
