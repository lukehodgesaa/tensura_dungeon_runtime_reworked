package com.mineclawd.tensuradungeon.compat.tensura;

import com.mineclawd.tensuradungeon.block.entity.RoomControllerBlockEntity;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class RecoveryOverdrawService {
    private static final String EVENTS_TAG = "TensuraDungeonRecoveryEvents";
    private static final long WINDOW_TICKS = 200L;

    private RecoveryOverdrawService() {}

    public static void afterInstantRegenerationHeal(LivingEntity entity, float healthBefore) {
        if (!(entity.level() instanceof ServerLevel level)
            || !RoomControllerBlockEntity.isPlayerInActiveEncounter(level, entity)
            || !isApprovedRegenerationSource(entity)) {
            return;
        }
        float healed = Math.max(0.0F, entity.getHealth() - healthBefore);
        if (healed <= 0.0F || entity.getMaxHealth() <= 0.0F) return;
        double fraction = healed / entity.getMaxHealth();
        long now = level.getGameTime();
        List<HealEvent> events = loadEvents(entity, now);
        double prior = events.stream().mapToDouble(HealEvent::fraction).sum();
        events.add(new HealEvent(now, fraction));
        saveEvents(entity, events);

        double maximumMagicules = energyValue("getMaxMagicule", entity);
        if (maximumMagicules <= 0.0D) return;
        double charge = maximumMagicules * marginalCost(prior, fraction);
        drainMagicules(entity, charge);
        CompoundTag data = entity.getPersistentData();
        data.putString("TensuraDungeonRecoveryTier", tier(prior + fraction));
        data.putDouble("TensuraDungeonRecoveryLastCharge", charge);
        data.putDouble("TensuraDungeonRecoveryWindowFraction", prior + fraction);
    }

    public static double marginalCost(double priorFraction, double healedFraction) {
        double start = Math.max(0.0D, priorFraction);
        double end = start + Math.max(0.0D, healedFraction);
        double stable = overlap(start, end, 0.0D, 0.12D) * 0.15D;
        double strained = overlap(start, end, 0.12D, 0.30D) * 0.225D;
        double overdrawn = Math.max(0.0D, end - Math.max(start, 0.30D)) * 0.3375D;
        return stable + strained + overdrawn;
    }

    public static String tier(double rollingFraction) {
        return rollingFraction > 0.30D ? "OVERDRAWN" : rollingFraction > 0.12D ? "STRAINED" : "STABLE";
    }

    public static Status snapshot(LivingEntity entity, long gameTime) {
        List<HealEvent> events = loadEvents(entity, gameTime);
        saveEvents(entity, events);
        double rolling = events.stream().mapToDouble(HealEvent::fraction).sum();
        int lastCharge = Math.max(0, (int) Math.min(
            Integer.MAX_VALUE,
            Math.round(entity.getPersistentData().getDouble("TensuraDungeonRecoveryLastCharge"))
        ));
        return new Status(tier(rolling), rolling, lastCharge);
    }

    private static double overlap(double start, double end, double lower, double upper) {
        return Math.max(0.0D, Math.min(end, upper) - Math.max(start, lower));
    }

    private static boolean isApprovedRegenerationSource(LivingEntity entity) {
        try {
            Class<?> effects = Class.forName("io.github.manasmods.tensura.registry.effect.TensuraMobEffects");
            Object supplier = effects.getField("INSTANT_REGENERATION").get(null);
            Object holder = effects.getMethod("getReference", Class.forName(
                "dev.architectury.registry.registries.RegistrySupplier"
            )).invoke(null, supplier);
            Method getEffect = LivingEntity.class.getMethod("getEffect", Class.forName("net.minecraft.core.Holder"));
            Object raw = getEffect.invoke(entity, holder);
            if (!(raw instanceof MobEffectInstance effect)) return false;
            Method sourceMethod = effect.getClass().getMethod("tensura$getSourceAbility");
            Object slot = sourceMethod.invoke(effect);
            if (slot == null) return false;
            Object skill = slot.getClass().getMethod("getSkill").invoke(slot);
            Object id = skill.getClass().getMethod("getRegistryName").invoke(skill);
            return ResourceLocation.tryParse("tensura:infinite_regeneration").equals(id)
                || ResourceLocation.tryParse("tensura:ultraspeed_regeneration").equals(id);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static double energyValue(String methodName, LivingEntity entity) {
        try {
            Class<?> helper = Class.forName("io.github.manasmods.tensura.util.EnergyHelper");
            return ((Number) helper.getMethod(methodName, LivingEntity.class).invoke(null, entity)).doubleValue();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0.0D;
        }
    }

    private static void drainMagicules(LivingEntity entity, double requested) {
        try {
            Class<?> storages = Class.forName("io.github.manasmods.tensura.storage.TensuraStorages");
            Object existence = storages.getMethod("getExistenceFrom", LivingEntity.class).invoke(null, entity);
            double current = ((Number) existence.getClass().getMethod("getMagicule").invoke(existence)).doubleValue();
            existence.getClass().getMethod("setMagicule", double.class).invoke(
                existence, Math.max(0.0D, current - Math.max(0.0D, requested))
            );
            existence.getClass().getMethod("markDirty").invoke(existence);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static List<HealEvent> loadEvents(LivingEntity entity, long now) {
        List<HealEvent> events = new ArrayList<>();
        ListTag tags = entity.getPersistentData().getList(EVENTS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < tags.size(); index++) {
            CompoundTag tag = tags.getCompound(index);
            long tick = tag.getLong("Tick");
            if (now - tick <= WINDOW_TICKS && tick <= now) {
                events.add(new HealEvent(tick, Math.max(0.0D, tag.getDouble("Fraction"))));
            }
        }
        return events;
    }

    private static void saveEvents(LivingEntity entity, List<HealEvent> events) {
        ListTag tags = new ListTag();
        events.stream().limit(200).forEach(event -> {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Tick", event.tick);
            tag.putDouble("Fraction", event.fraction);
            tags.add(tag);
        });
        entity.getPersistentData().put(EVENTS_TAG, tags);
    }

    private record HealEvent(long tick, double fraction) {}

    public record Status(String tier, double rollingFraction, int lastCharge) {}
}
