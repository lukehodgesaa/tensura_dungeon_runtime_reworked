package com.mineclawd.tensuraadditions.entity;

import com.mineclawd.tensuraadditions.registry.ModEntities;
import com.mineclawd.tensuraadditions.registry.ModParticles;
import com.mineclawd.tensuraadditions.registry.ModSounds;
import com.mineclawd.tensuraadditions.event.DungeonSpawnRuneEvents;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class SpawnRuneEntity extends Entity implements GeoEntity {
    public static final int ANIMATION_TICKS = 61;
    public static final double DEFAULT_START_DEPTH = 2.9D;
    public static final float MIN_RUNE_SCALE = 1.15F;
    public static final float MAX_RUNE_SCALE = 8.0F;
    public static final int TEXTURE_FRAME_COUNT = 12;

    private static final String TAG_MOB_UUID = "MobUuid";
    private static final String TAG_BASE_X = "BaseX";
    private static final String TAG_BASE_Y = "BaseY";
    private static final String TAG_BASE_Z = "BaseZ";
    private static final String TAG_START_DEPTH = "StartDepth";
    private static final String TAG_TICKS_LEFT = "TicksLeft";
    private static final String TAG_WAS_NO_AI = "WasNoAi";
    private static final String TAG_WAS_INVULNERABLE = "WasInvulnerable";
    private static final String TAG_WAS_SILENT = "WasSilent";
    private static final String TAG_WAS_NO_GRAVITY = "WasNoGravity";
    private static final String TAG_WAS_INVISIBLE = "WasInvisible";
    private static final String TAG_EMERGED = "Emerged";
    private static final String TAG_PALETTE = "Palette";
    private static final String TAG_SCALE = "Scale";

    private static final EntityDataAccessor<String> DATA_PALETTE =
        SynchedEntityData.defineId(SpawnRuneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_SCALE =
        SynchedEntityData.defineId(SpawnRuneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_TICKS_LEFT =
        SynchedEntityData.defineId(SpawnRuneEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("emerge");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private UUID mobUuid;
    private double baseX;
    private double baseY;
    private double baseZ;
    private double startDepth = DEFAULT_START_DEPTH;
    private int ticksLeft = ANIMATION_TICKS;
    private boolean wasNoAi;
    private boolean wasInvulnerable;
    private boolean wasSilent;
    private boolean wasNoGravity;
    private boolean wasInvisible;
    private boolean emerged;
    private boolean restored;

    public SpawnRuneEntity(EntityType<? extends SpawnRuneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SpawnRuneEntity create(
            ServerLevel level,
            Mob mob,
            String palette,
            boolean wasNoAi,
            boolean wasInvulnerable,
            boolean wasSilent,
            boolean wasNoGravity,
            boolean wasInvisible,
            double baseX,
            double baseY,
            double baseZ,
            double startDepth) {
        SpawnRuneEntity rune = ModEntities.SPAWN_RUNE.get().create(level);
        if (rune == null) {
            return null;
        }
        rune.mobUuid = mob.getUUID();
        rune.baseX = baseX;
        rune.baseY = baseY;
        rune.baseZ = baseZ;
        rune.startDepth = startDepth;
        rune.wasNoAi = wasNoAi;
        rune.wasInvulnerable = wasInvulnerable;
        rune.wasSilent = wasSilent;
        rune.wasNoGravity = wasNoGravity;
        rune.wasInvisible = wasInvisible;
        rune.setPalette(palette);
        rune.setRuneScale(scaleFor(mob));
        rune.moveTo(baseX, baseY - 0.01D, baseZ, mob.getYRot(), 0.0F);
        return rune;
    }

    public static float scaleFor(Mob mob) {
        EntityDimensions dimensions = mob.getDimensions(Pose.STANDING);
        float diameter = Math.max(
            dimensions.width() * 1.35F,
            dimensions.height() * 0.55F) + 0.5F;
        return Math.max(MIN_RUNE_SCALE, Math.min(MAX_RUNE_SCALE, diameter));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PALETTE, "purple");
        builder.define(DATA_SCALE, 1.0F);
        builder.define(DATA_TICKS_LEFT, ANIMATION_TICKS);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_MOB_UUID)) {
            this.mobUuid = tag.getUUID(TAG_MOB_UUID);
        }
        this.baseX = tag.getDouble(TAG_BASE_X);
        this.baseY = tag.getDouble(TAG_BASE_Y);
        this.baseZ = tag.getDouble(TAG_BASE_Z);
        this.startDepth = tag.getDouble(TAG_START_DEPTH);
        this.ticksLeft = tag.getInt(TAG_TICKS_LEFT);
        this.entityData.set(DATA_TICKS_LEFT,
            Math.max(0, Math.min(ANIMATION_TICKS, this.ticksLeft)));
        this.wasNoAi = tag.getBoolean(TAG_WAS_NO_AI);
        this.wasInvulnerable = tag.getBoolean(TAG_WAS_INVULNERABLE);
        this.wasSilent = tag.getBoolean(TAG_WAS_SILENT);
        this.wasNoGravity = tag.getBoolean(TAG_WAS_NO_GRAVITY);
        this.wasInvisible = tag.getBoolean(TAG_WAS_INVISIBLE);
        this.emerged = tag.getBoolean(TAG_EMERGED);
        this.entityData.set(DATA_PALETTE, tag.getString(TAG_PALETTE));
        this.entityData.set(DATA_SCALE, tag.getFloat(TAG_SCALE));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.mobUuid != null) {
            tag.putUUID(TAG_MOB_UUID, this.mobUuid);
        }
        tag.putDouble(TAG_BASE_X, this.baseX);
        tag.putDouble(TAG_BASE_Y, this.baseY);
        tag.putDouble(TAG_BASE_Z, this.baseZ);
        tag.putDouble(TAG_START_DEPTH, this.startDepth);
        tag.putInt(TAG_TICKS_LEFT, this.ticksLeft);
        tag.putBoolean(TAG_WAS_NO_AI, this.wasNoAi);
        tag.putBoolean(TAG_WAS_INVULNERABLE, this.wasInvulnerable);
        tag.putBoolean(TAG_WAS_SILENT, this.wasSilent);
        tag.putBoolean(TAG_WAS_NO_GRAVITY, this.wasNoGravity);
        tag.putBoolean(TAG_WAS_INVISIBLE, this.wasInvisible);
        tag.putBoolean(TAG_EMERGED, this.emerged);
        tag.putString(TAG_PALETTE, getPalette());
        tag.putFloat(TAG_SCALE, getRuneScale());
    }

    public String getPalette() {
        return this.entityData.get(DATA_PALETTE);
    }

    public void setPalette(String palette) {
        this.entityData.set(DATA_PALETTE,
            palette == null || palette.isBlank() ? "purple" : palette);
    }

    public int getTextureFrame() {
        int clampedTicksLeft = Math.max(0,
            Math.min(ANIMATION_TICKS, this.entityData.get(DATA_TICKS_LEFT)));
        int elapsed = ANIMATION_TICKS - clampedTicksLeft;
        return Math.max(0, Math.min(TEXTURE_FRAME_COUNT - 1,
            (elapsed * TEXTURE_FRAME_COUNT) / ANIMATION_TICKS));
    }

    public float getRuneScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public void setRuneScale(float scale) {
        this.entityData.set(DATA_SCALE,
            Math.max(MIN_RUNE_SCALE, Math.min(MAX_RUNE_SCALE, scale)));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel) || this.mobUuid == null) {
            discard();
            return;
        }

        if (this.ticksLeft <= 0) {
            finish(serverLevel, null);
            return;
        }

        Mob mob = null;
        if (serverLevel.getEntity(this.mobUuid) instanceof Mob found) {
            mob = found;
        }
        if (mob == null || !mob.isAlive() || mob.isRemoved()) {
            Mob elsewhere = findMobOnServer(serverLevel);
            if (elsewhere != null) {
                restoreFlags(elsewhere, false);
            }
            discard();
            return;
        }

        int elapsed = ANIMATION_TICKS - this.ticksLeft;
        int k = elapsed + 1;
        if (k == 1) {
            mob.moveTo(this.baseX, this.baseY - this.startDepth, this.baseZ,
                mob.getYRot(), mob.getXRot());
            serverLevel.playSound(null, blockPosition(), ModSounds.SPAWN_RUNE_ACTIVATE.get(),
                SoundSource.HOSTILE, 0.7F, 1.0F);
        }

        double riseRange = this.startDepth + 0.15D;
        double y = this.baseY - this.startDepth
            + (k / (double) ANIMATION_TICKS) * riseRange;
        mob.moveTo(this.baseX, y, this.baseZ, mob.getYRot(), mob.getXRot());
        mob.setDeltaMovement(0.0D, 0.0D, 0.0D);

        if (k >= 4 && !this.emerged) {
            this.emerged = true;
            mob.setInvisible(this.wasInvisible);
            serverLevel.playSound(null, blockPosition(), ModSounds.SPAWN_RUNE_EMERGE.get(),
                SoundSource.HOSTILE, 0.75F, 1.0F);
        }

        spawnParticles(serverLevel);

        this.ticksLeft--;
        this.entityData.set(DATA_TICKS_LEFT, Math.max(0, this.ticksLeft));
        if (this.ticksLeft <= 0) {
            finish(serverLevel, mob);
        }
    }

    private void spawnParticles(ServerLevel level) {
        level.sendParticles(ModParticles.SPAWN_RUNE.get(),
            this.baseX, this.baseY + 0.2D, this.baseZ,
            3, 0.35D, 0.35D, 0.35D, 0.02D);
    }

    private void finish(ServerLevel level, Mob mob) {
        if (mob == null && this.mobUuid != null) {
            if (level.getEntity(this.mobUuid) instanceof Mob found && found.isAlive()) {
                mob = found;
            }
            if (mob == null) {
                mob = findMobOnServer(level);
            }
        }
        if (!this.restored) {
            if (mob != null) {
                mob.setNoAi(this.wasNoAi);
                mob.setInvulnerable(this.wasInvulnerable);
                mob.setSilent(this.wasSilent);
                mob.setNoGravity(this.wasNoGravity);
                mob.setInvisible(this.wasInvisible);
                mob.moveTo(this.baseX, this.baseY, this.baseZ,
                    mob.getYRot(), mob.getXRot());
                mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
                mob.getPersistentData().remove(DungeonSpawnRuneEvents.RESTORE_KEY);
            }
            this.restored = true;
            level.playSound(null, blockPosition(), ModSounds.SPAWN_RUNE_FINISH.get(),
                SoundSource.HOSTILE, 0.55F, 1.0F);
            level.sendParticles(ModParticles.SPAWN_RUNE.get(),
                this.baseX, this.baseY + 0.25D, this.baseZ,
                12, 0.5D, 0.6D, 0.5D, 0.05D);
        }
        discard();
    }

    private Mob findMobOnServer(ServerLevel origin) {
        for (ServerLevel level : origin.getServer().getAllLevels()) {
            if (level == origin || this.mobUuid == null) {
                continue;
            }
            if (level.getEntity(this.mobUuid) instanceof Mob mob && mob.isAlive()) {
                return mob;
            }
        }
        return null;
    }

    private void restoreFlags(Mob mob) {
        restoreFlags(mob, true);
    }

    private void restoreFlags(Mob mob, boolean restorePosition) {
        if (this.restored) {
            return;
        }
        mob.setNoAi(this.wasNoAi);
        mob.setInvulnerable(this.wasInvulnerable);
        mob.setSilent(this.wasSilent);
        mob.setNoGravity(this.wasNoGravity);
        mob.setInvisible(this.wasInvisible);
        if (restorePosition) {
            mob.moveTo(this.baseX, this.baseY, this.baseZ,
                mob.getYRot(), mob.getXRot());
            mob.setDeltaMovement(0.0D, 0.0D, 0.0D);
        }
        mob.getPersistentData().remove(DungeonSpawnRuneEvents.RESTORE_KEY);
        this.restored = true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && !this.restored
                && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            if (level() instanceof ServerLevel serverLevel && this.mobUuid != null
                    && serverLevel.getEntity(this.mobUuid) instanceof Mob mob) {
                restoreFlags(mob);
            }
        }
        super.remove(reason);
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spawn_rune",
            state -> state.setAndContinue(EMERGE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
