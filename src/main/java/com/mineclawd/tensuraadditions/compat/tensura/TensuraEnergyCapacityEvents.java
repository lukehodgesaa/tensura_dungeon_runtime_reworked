package com.mineclawd.tensuraadditions.compat.tensura;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.event.EventResult;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.util.SubordinateHelper;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = TensuraAdditions.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class TensuraEnergyCapacityEvents {
    private static final String OVERRIDES_KEY = "TensuraAdditionsEnergyCapacityOverrides";
    private static final String MAGICULE_KEY = "MaxMagicule";
    private static final String AURA_KEY = "MaxAura";
    private static final double EPSILON = 1.0E-6D;

    private static final ResourceLocation MAGICULE_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraAdditions.MOD_ID, "persistent_max_magicule");
    private static final ResourceLocation AURA_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraAdditions.MOD_ID, "persistent_max_aura");

    private static boolean initialized;

    private TensuraEnergyCapacityEvents() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        TensuraEntityEvents.ATTRIBUTE_BASE_CHANGE_EVENT.register(
            TensuraEnergyCapacityEvents::onAttributeBaseChanged);
        initialized = true;
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        applyOverride(entity, Capacity.MAGICULE);
        applyOverride(entity, Capacity.AURA);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("tensura_additions")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("energy_capacity")
                    .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.entities())
                            .executes(TensuraEnergyCapacityEvents::clearOverrides)))));
    }

    private static EventResult onAttributeBaseChanged(
        LivingEntity entity,
        AttributeInstance attribute,
        double oldValue,
        double newValue
    ) {
        if (entity.level().isClientSide() || !isOwnedTensuraSubordinate(entity)) {
            return EventResult.pass();
        }

        Capacity capacity = Capacity.forAttribute(attribute);
        if (capacity == null || !isFinite(newValue) || !isFinite(oldValue)) {
            return EventResult.pass();
        }

        CompoundTag overrides = overrides(entity, false);
        double remembered = readOverride(overrides, capacity);

        if (newValue > oldValue + EPSILON) {
            if (newValue > remembered + EPSILON) {
                overrides(entity, true).putDouble(capacity.key, newValue);
            }
            return EventResult.pass();
        }

        if (remembered > 0.0D && newValue < oldValue - EPSILON
            && newValue < remembered - EPSILON) {
            return EventResult.interruptFalse();
        }
        return EventResult.pass();
    }

    private static void applyOverride(LivingEntity entity, Capacity capacity) {
        if (!isOwnedTensuraSubordinate(entity)) {
            return;
        }

        CompoundTag overrides = overrides(entity, false);
        double remembered = readOverride(overrides, capacity);
        if (!(remembered > 0.0D) || !isFinite(remembered)) {
            return;
        }

        AttributeInstance attribute = entity.getAttribute(capacity.attribute);
        if (attribute == null) {
            return;
        }

        double base = attribute.getBaseValue();
        AttributeModifier existing = attribute.getModifier(capacity.modifierId);
        if (base >= remembered - EPSILON) {
            if (base > remembered + EPSILON) {
                overrides(entity, true).putDouble(capacity.key, base);
            }
            if (existing != null) {
                attribute.removeModifier(capacity.modifierId);
            }
            return;
        }

        double amount = remembered - base;
        if (!isFinite(amount) || amount <= EPSILON) {
            return;
        }
        if (existing != null && Math.abs(existing.amount() - amount) <= EPSILON) {
            return;
        }
        if (existing != null) {
            attribute.removeModifier(capacity.modifierId);
        }
        attribute.addPermanentModifier(new AttributeModifier(
            capacity.modifierId,
            amount,
            AttributeModifier.Operation.ADD_VALUE));
    }

    private static int clearOverrides(CommandContext<CommandSourceStack> context)
        throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        int cleared = 0;
        for (Entity target : targets) {
            if (!(target instanceof LivingEntity entity) || !isOwnedTensuraSubordinate(entity)) {
                continue;
            }
            CompoundTag persistent = entity.getPersistentData();
            if (!persistent.contains(OVERRIDES_KEY, Tag.TAG_COMPOUND)) {
                continue;
            }
            persistent.remove(OVERRIDES_KEY);
            removeModifier(entity, Capacity.MAGICULE);
            removeModifier(entity, Capacity.AURA);
            cleared++;
        }
        int clearedCount = cleared;
        context.getSource().sendSuccess(
            () -> Component.literal("Cleared Tensura Additions energy-capacity overrides for " + clearedCount
                + " subordinate(s)."),
            true);
        return cleared;
    }

    private static void removeModifier(LivingEntity entity, Capacity capacity) {
        AttributeInstance attribute = entity.getAttribute(capacity.attribute);
        if (attribute != null && attribute.getModifier(capacity.modifierId) != null) {
            attribute.removeModifier(capacity.modifierId);
        }
    }

    private static boolean isOwnedTensuraSubordinate(LivingEntity entity) {
        try {
            return SubordinateHelper.getSubordinateOwnerUUID(entity) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static CompoundTag overrides(LivingEntity entity, boolean create) {
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(OVERRIDES_KEY, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            CompoundTag created = new CompoundTag();
            persistent.put(OVERRIDES_KEY, created);
            return created;
        }
        return persistent.getCompound(OVERRIDES_KEY);
    }

    private static double readOverride(CompoundTag overrides, Capacity capacity) {
        if (overrides == null || !overrides.contains(capacity.key, Tag.TAG_DOUBLE)) {
            return 0.0D;
        }
        double value = overrides.getDouble(capacity.key);
        return isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    private static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    private enum Capacity {
        MAGICULE(
            MAGICULE_KEY,
            TensuraAttributes.MAX_MAGICULE,
            MAGICULE_MODIFIER_ID),
        AURA(
            AURA_KEY,
            TensuraAttributes.MAX_AURA,
            AURA_MODIFIER_ID);

        private final String key;
        private final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute;
        private final ResourceLocation modifierId;

        Capacity(
            String key,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation modifierId
        ) {
            this.key = key;
            this.attribute = attribute;
            this.modifierId = modifierId;
        }

        private static Capacity forAttribute(AttributeInstance instance) {
            if (instance == null) {
                return null;
            }
            if (instance.getAttribute().equals(TensuraAttributes.MAX_MAGICULE)) {
                return MAGICULE;
            }
            if (instance.getAttribute().equals(TensuraAttributes.MAX_AURA)) {
                return AURA;
            }
            return null;
        }
    }
}
