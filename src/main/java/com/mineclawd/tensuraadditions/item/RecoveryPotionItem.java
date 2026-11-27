package com.mineclawd.tensuraadditions.item;

import java.lang.reflect.Method;
import java.util.OptionalDouble;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class RecoveryPotionItem extends Item {
    public static final int SHARED_COOLDOWN_TICKS = 200;

    private final Family family;
    private final Size size;

    public RecoveryPotionItem(Family family, Size size) {
        super(new Item.Properties().stacksTo(10));
        this.family = family;
        this.size = size;
    }

    public Family family() {
        return family;
    }

    public Size size() {
        return size;
    }

    public double recoveryFraction() {
        return size.recoveryFraction();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
        Level level,
        Player player,
        InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (family == Family.HEALTH && player.getHealth() >= player.getMaxHealth()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("Health is already full."), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (family == Family.MAGICULE && player instanceof ServerPlayer serverPlayer) {
            if (MagiculeBridge.maximum(serverPlayer).isEmpty()
                || MagiculeBridge.read(serverPlayer).isEmpty()) {
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return stack;
        }

        boolean recovered = family == Family.HEALTH
            ? restoreHealth(player)
            : restoreMagicule(player);
        if (!recovered) {
            return stack;
        }

        stack.shrink(1);
        player.getCooldowns().addCooldown(this, SHARED_COOLDOWN_TICKS);
        for (Item candidate : BuiltInRegistries.ITEM) {
            if (candidate instanceof RecoveryPotionItem potion) {
                player.getCooldowns().addCooldown(potion, SHARED_COOLDOWN_TICKS);
            }
        }
        giveBottle(player);
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return size.drinkingTicks();
    }

    private boolean restoreHealth(ServerPlayer player) {
        float maximum = player.getMaxHealth();
        if (!(maximum > 0.0F) || player.getHealth() >= maximum) {
            return false;
        }
        player.heal(maximum * (float) recoveryFraction());
        return player.getHealth() > 0.0F;
    }

    private boolean restoreMagicule(ServerPlayer player) {
        OptionalDouble current = MagiculeBridge.read(player);
        OptionalDouble maximum = MagiculeBridge.maximum(player);
        if (current.isEmpty() || maximum.isEmpty()) {
            return false;
        }
        double next = current.getAsDouble() + maximum.getAsDouble() * recoveryFraction();
        return Double.isFinite(next) && MagiculeBridge.write(player, next);
    }

    private static void giveBottle(ServerPlayer player) {
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (!player.getInventory().add(bottle)) {
            player.drop(bottle, false);
        }
    }

    public enum Family {
        HEALTH,
        MAGICULE
    }

    public enum Size {
        VIAL(0.20D, 20),
        FLASK(0.45D, 32),
        GREATER_FLASK(0.75D, 48);

        private final double recoveryFraction;
        private final int drinkingTicks;

        Size(double recoveryFraction, int drinkingTicks) {
            this.recoveryFraction = recoveryFraction;
            this.drinkingTicks = drinkingTicks;
        }

        public double recoveryFraction() {
            return recoveryFraction;
        }

        public int drinkingTicks() {
            return drinkingTicks;
        }
    }

    private static final class MagiculeBridge {
        private static final String STORAGES_CLASS =
            "io.github.manasmods.tensura.storage.TensuraStorages";
        private static final String ENERGY_HELPER_CLASS =
            "io.github.manasmods.tensura.util.EnergyHelper";

        private MagiculeBridge() {}

        static OptionalDouble read(LivingEntity entity) {
            if (entity == null) {
                return OptionalDouble.empty();
            }
            try {
                Object existence = existence(entity);
                Object value = existence.getClass().getMethod("getMagicule").invoke(existence);
                if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
                    return OptionalDouble.of(Math.max(0.0D, number.doubleValue()));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
            return OptionalDouble.empty();
        }

        static OptionalDouble maximum(LivingEntity entity) {
            if (entity == null) {
                return OptionalDouble.empty();
            }
            try {
                Class<?> helper = Class.forName(ENERGY_HELPER_CLASS, false,
                    RecoveryPotionItem.class.getClassLoader());
                Method method = helper.getMethod("getMaxMagicule", LivingEntity.class);
                Object value = method.invoke(null, entity);
                if (value instanceof Number number && Double.isFinite(number.doubleValue())
                    && number.doubleValue() >= 0.0D) {
                    return OptionalDouble.of(number.doubleValue());
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
            return OptionalDouble.empty();
        }

        static boolean write(LivingEntity entity, double value) {
            if (entity == null || entity.level().isClientSide
                || !Double.isFinite(value) || value < 0.0D) {
                return false;
            }
            try {
                Object existence = existence(entity);
                existence.getClass().getMethod("setMagicule", double.class).invoke(existence, value);
                existence.getClass().getMethod("markDirty").invoke(existence);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        private static Object existence(LivingEntity entity) throws ReflectiveOperationException {
            Class<?> storages = Class.forName(STORAGES_CLASS, false,
                RecoveryPotionItem.class.getClassLoader());
            Object existence = storages.getMethod("getExistenceFrom", LivingEntity.class)
                .invoke(null, entity);
            if (existence == null) {
                throw new IllegalStateException("Tensura existence storage is null");
            }
            return existence;
        }
    }
}
