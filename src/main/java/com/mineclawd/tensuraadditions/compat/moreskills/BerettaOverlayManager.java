package com.mineclawd.tensuraadditions.compat.moreskills;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BerettaOverlayManager {
    private static final ResourceLocation BERETTA_TYPE = ResourceLocation.fromNamespaceAndPath(
            MoreSkillsCompat.MOD_ID,
            "beretta"
    );
    private static final Map<Integer, ActiveAttack> ACTIVE = new HashMap<>();
    private static final Map<Integer, Long> COOLDOWN_UNTIL = new HashMap<>();

    private BerettaOverlayManager() {
    }

    public static void tick(LivingEntity candidate) {
        if (candidate.level().isClientSide || !isBeretta(candidate)) {
            return;
        }

        ActiveAttack active = ACTIVE.get(candidate.getId());
        if (active == null) {
            return;
        }
        long gameTime = candidate.level().getGameTime();
        if (active.lastTickGameTime == gameTime) {
            return;
        }
        active.lastTickGameTime = gameTime;
        if (!candidate.isAlive() || !(candidate.level() instanceof ServerLevel level)) {
            ACTIVE.remove(candidate.getId());
            return;
        }

        active.context.setAge(active.age);
        if (!active.started) {
            active.executor.start(active.context);
            active.started = true;
        }
        active.executor.tick(active.context);
        active.age++;

        if (active.age >= active.context.definition().durationTicks()) {
            active.context.setAge(active.age);
            active.executor.finish(active.context);
            ACTIVE.remove(candidate.getId());
            int cooldownTicks = active.context.definition().cooldownTicks();
            if (cooldownTicks > 0) {
                COOLDOWN_UNTIL.put(candidate.getId(), gameTime + cooldownTicks);
            }
        }
    }

    public static void tickActive() {
        for (ActiveAttack active : List.copyOf(ACTIVE.values())) {
            tick(active.context.beretta());
        }
    }

    public static StartResult startNearest(ServerLevel level, Vec3 position, String rawAttackId) {
        if (!MoreSkillsCompat.isLoaded()) {
            return StartResult.failure("TensuraMoreSkills is not loaded.");
        }

        ResourceLocation attackId = resolveAttackId(rawAttackId);
        if (attackId == null) {
            return StartResult.failure("Invalid Beretta attack id: " + rawAttackId);
        }

        BerettaAttackDefinition definition = BerettaAttackRegistry.get(attackId);
        if (definition == null) {
            return StartResult.failure("Unknown Beretta attack: " + attackId);
        }

        LivingEntity beretta = nearestBeretta(level, position);
        if (beretta == null) {
            return StartResult.failure("No Beretta was found within 32 blocks.");
        }
        if (ACTIVE.containsKey(beretta.getId())) {
            return StartResult.failure("That Beretta already has an addon attack active.");
        }
        long cooldownUntil = COOLDOWN_UNTIL.getOrDefault(beretta.getId(), Long.MIN_VALUE);
        if (level.getGameTime() < cooldownUntil) {
            long remaining = cooldownUntil - level.getGameTime();
            return StartResult.failure("That Beretta's addon attack is cooling down (" + remaining + " ticks).");
        }

        LivingEntity target = resolveTarget(level, beretta);
        Vec3 origin = BerettaSurfaceAnchor.project(level, beretta.position());
        Vec3 direction = flatDirection(beretta, target);
        Vec3 impactPoint = target == null
                ? BerettaSurfaceAnchor.project(
                level,
                origin.add(direction.scale(ChaoticIronMaidenExecutor.ATTACK_RANGE))
        )
                : BerettaSurfaceAnchor.projectTarget(level, target);
        impactPoint = limitRange(origin, impactPoint, ChaoticIronMaidenExecutor.ATTACK_RANGE);
        BerettaAttackExecutor executor = BerettaAttackExecutorRegistry.get(attackId);
        if (executor == null) {
            return StartResult.failure("No executor is registered for: " + attackId);
        }

        int seed = beretta.getRandom().nextInt();
        BerettaAttackContext context = new BerettaAttackContext(
                level,
                beretta,
                target,
                origin,
                impactPoint,
                seed,
                definition
        );
        ACTIVE.put(
                beretta.getId(),
                new ActiveAttack(context, executor, seed, target, direction)
        );
        return StartResult.success(beretta.getId(), attackId);
    }

    public static ResourceLocation resolveAttackId(String rawAttackId) {
        if (rawAttackId == null || rawAttackId.isBlank()) {
            return null;
        }
        if (rawAttackId.contains(":")) {
            return ResourceLocation.tryParse(rawAttackId);
        }
        return ResourceLocation.fromNamespaceAndPath(
                "tensura_additions",
                "moreskills/beretta/" + rawAttackId
        );
    }

    static ActiveAttack active(int entityId) {
        return ACTIVE.get(entityId);
    }

    private static LivingEntity nearestBeretta(ServerLevel level, Vec3 position) {
        AABB bounds = AABB.ofSize(position, 64.0D, 64.0D, 64.0D);
        return level.getEntitiesOfClass(LivingEntity.class, bounds, BerettaOverlayManager::isBeretta)
                .stream()
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(position)))
                .orElse(null);
    }

    private static LivingEntity resolveTarget(ServerLevel level, LivingEntity beretta) {
        if (beretta instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            return mob.getTarget();
        }

        AABB bounds = beretta.getBoundingBox().inflate(48.0D);
        List<Player> players = level.getEntitiesOfClass(Player.class, bounds, Player::isAlive);
        return players.stream()
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(beretta)))
                .orElse(null);
    }

    private static Vec3 flatDirection(LivingEntity beretta, LivingEntity target) {
        Vec3 direction = target == null
                ? beretta.getLookAngle()
                : target.position().subtract(beretta.position());
        direction = new Vec3(direction.x, 0.0D, direction.z);
        if (direction.lengthSqr() < 1.0E-4D) {
            direction = new Vec3(0.0D, 0.0D, 1.0D);
        }
        return direction.normalize();
    }

    private static Vec3 limitRange(Vec3 origin, Vec3 destination, double range) {
        Vec3 offset = destination.subtract(origin);
        Vec3 flatOffset = new Vec3(offset.x, 0.0D, offset.z);
        if (flatOffset.lengthSqr() <= range * range) {
            return destination;
        }
        Vec3 limited = flatOffset.normalize().scale(range);
        return origin.add(limited.x, offset.y, limited.z);
    }

    private static boolean isBeretta(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(BERETTA_TYPE);
    }

    public record StartResult(boolean success, String message, int entityId, ResourceLocation attackId) {
        static StartResult success(int entityId, ResourceLocation attackId) {
            return new StartResult(true, "Started " + attackId, entityId, attackId);
        }

        static StartResult failure(String message) {
            return new StartResult(false, message, -1, null);
        }
    }

    static final class ActiveAttack {
        final BerettaAttackContext context;
        final BerettaAttackExecutor executor;
        final int seed;
        final LivingEntity target;
        final Vec3 direction;
        int age;
        boolean started;
        long lastTickGameTime = Long.MIN_VALUE;

        ActiveAttack(
                BerettaAttackContext context,
                BerettaAttackExecutor executor,
                int seed,
                LivingEntity target,
                Vec3 direction
        ) {
            this.context = context;
            this.executor = executor;
            this.seed = seed;
            this.target = target;
            this.direction = direction;
        }
    }
}
