package com.mineclawd.tensuraadditions.compat.moreskills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class BerettaAttackContext {
    private final ServerLevel level;
    private final LivingEntity beretta;
    private final LivingEntity target;
    private final Vec3 origin;
    private final Vec3 impactPoint;
    private final int seed;
    private final BerettaAttackDefinition definition;
    private final Set<UUID> damagedTargets = new HashSet<>();
    private final Set<Integer> processedImpacts = new HashSet<>();
    private int age;
    private boolean impactApplied;

    public BerettaAttackContext(
            ServerLevel level,
            LivingEntity beretta,
            LivingEntity target,
            Vec3 origin,
            Vec3 impactPoint,
            int seed,
            BerettaAttackDefinition definition
    ) {
        this.level = level;
        this.beretta = beretta;
        this.target = target;
        this.origin = origin;
        this.impactPoint = impactPoint;
        this.seed = seed;
        this.definition = definition;
    }

    public ServerLevel level() {
        return level;
    }

    public LivingEntity beretta() {
        return beretta;
    }

    public LivingEntity target() {
        return target;
    }

    public Vec3 origin() {
        return origin;
    }

    public Vec3 impactPoint() {
        return impactPoint;
    }

    public int seed() {
        return seed;
    }

    public BerettaAttackDefinition definition() {
        return definition;
    }

    public int age() {
        return age;
    }

    public void setAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("attack age cannot be negative");
        }
        this.age = age;
    }

    public void advance() {
        age++;
    }

    public boolean hasDamaged(LivingEntity entity) {
        return damagedTargets.contains(entity.getUUID());
    }

    public void markDamaged(LivingEntity entity) {
        damagedTargets.add(entity.getUUID());
    }

    public boolean hasProcessedImpact(int impactId) {
        return processedImpacts.contains(impactId);
    }

    public void markProcessedImpact(int impactId) {
        processedImpacts.add(impactId);
    }

    public boolean impactApplied() {
        return impactApplied;
    }

    public void markImpactApplied() {
        impactApplied = true;
    }
}
