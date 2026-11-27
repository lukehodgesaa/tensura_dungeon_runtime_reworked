package com.mineclawd.dungeonsunbound.npc;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class DungeonNpc extends PathfinderMob implements GeoEntity {
    public static final String NPC_ID_TAG = "NpcId";
    private static final EntityDataAccessor<String> NPC_ID =
        SynchedEntityData.defineId(DungeonNpc.class, EntityDataSerializers.STRING);
    private static final double LOOK_RANGE = 48.0D;
    private static final Map<String, RawAnimation> IDLE_ANIMATIONS = new HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DungeonNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(NPC_ID, DungeonNpcProfile.GOBTA.id());
    }

    public DungeonNpcProfile profile() {
        return DungeonNpcProfile.get(entityData.get(NPC_ID));
    }

    public void setNpcId(String id) {
        entityData.set(NPC_ID, DungeonNpcProfile.get(id).id());
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(profile().scale());
        }
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (NPC_ID.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(NPC_ID_TAG, entityData.get(NPC_ID));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(NPC_ID_TAG)) {
            setNpcId(tag.getString(NPC_ID_TAG));
        }
        setInvulnerable(true);
    }

    @Override
    protected Component getTypeName() {
        return Component.literal(profile().name());
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        Player target = level().getNearestPlayer(this, LOOK_RANGE);
        if (target != null && target.isAlive()) {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            DungeonNpcDialogues.talk(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        if (getHealth() < getMaxHealth()) {
            super.setHealth(getMaxHealth());
        }
        deathTime = 0;
        hurtTime = 0;
        clearFire();
        if (!level().isClientSide && !getActiveEffects().isEmpty()) {
            removeAllEffects();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void setHealth(float health) {
        super.setHealth(getMaxHealth());
    }

    @Override
    public boolean isDeadOrDying() {
        return false;
    }

    @Override
    public void die(DamageSource source) {
    }

    @Override
    public void kill() {
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED) {
            return;
        }
        super.remove(reason);
    }

    @Override
    protected void onBelowWorld() {
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, Entity source) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 10, state -> {
            DungeonNpcProfile profile = profile();
            if (profile.model() != DungeonNpcProfile.Model.GECKO || profile.idleAnimation().isEmpty()) {
                return software.bernie.geckolib.animation.PlayState.STOP;
            }
            return state.setAndContinue(IDLE_ANIMATIONS.computeIfAbsent(
                profile.idleAnimation(), name -> RawAnimation.begin().thenLoop(name)
            ));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
