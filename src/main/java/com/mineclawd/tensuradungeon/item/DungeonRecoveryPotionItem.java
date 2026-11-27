package com.mineclawd.tensuradungeon.item;

import com.mineclawd.tensuradungeon.compat.tensura.TensuraAddonCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraEnergyDebugHelper;
import java.util.OptionalDouble;
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

public final class DungeonRecoveryPotionItem extends Item {
    public static final int SHARED_COOLDOWN_TICKS = 200;

    private final Family family;
    private final Size size;

    public DungeonRecoveryPotionItem(Family family, Size size) {
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
                player.displayClientMessage(
                    Component.literal("Health is already full."),
                    true
                );
            }
            return InteractionResultHolder.fail(stack);
        }
        if (family == Family.MAGICULE && player instanceof ServerPlayer serverPlayer) {
            if (magiculeMaximum(serverPlayer).isEmpty()
                || TensuraAddonCompat.readMagicule(serverPlayer).isEmpty()) {
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
        for (Item candidate : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (candidate instanceof DungeonRecoveryPotionItem potion) {
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
        OptionalDouble current = TensuraAddonCompat.readMagicule(player);
        OptionalDouble maximum = magiculeMaximum(player);
        if (current.isEmpty() || maximum.isEmpty()) {
            return false;
        }
        double next = current.getAsDouble() + maximum.getAsDouble() * recoveryFraction();
        return Double.isFinite(next) && TensuraAddonCompat.writeMagicule(player, next);
    }

    private static OptionalDouble magiculeMaximum(ServerPlayer player) {
        Double maximum = TensuraEnergyDebugHelper.inspectSource(player).energyHelperMaxMagicule();
        return maximum != null && Double.isFinite(maximum) && maximum >= 0.0D
            ? OptionalDouble.of(maximum)
            : OptionalDouble.empty();
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
}
