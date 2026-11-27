package com.mineclawd.tensuradungeon.item;

import com.mineclawd.tensuradungeon.compat.tensura.TensuraCompat;
import com.mineclawd.tensuradungeon.compat.tensura.TensuraSubordinateSkillCompat;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class SubordinateSkillScrollItem extends Item {
    public static final String SKILL_ID_TAG = "SkillId";
    private final String registeredSkillId;

    public SubordinateSkillScrollItem(Properties properties) {
        this(properties, null);
    }

    public SubordinateSkillScrollItem(
        Properties properties,
        SubordinateSkillScrollCatalog.Definition definition
    ) {
        super(properties.stacksTo(64));
        this.registeredSkillId = definition == null ? "" : definition.skillId();
    }

    public static Optional<String> skillId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        if (stack.getItem() instanceof SubordinateSkillScrollItem scroll
            && !scroll.registeredSkillId.isBlank()) {
            return Optional.of(scroll.registeredSkillId);
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        CompoundTag root = customData.copyTag();
        ResourceLocation parsed = TensuraSubordinateSkillCompat.parseTensuraSkillId(
            root.getString(SKILL_ID_TAG)
        );
        return parsed == null ? Optional.empty() : Optional.of(parsed.toString());
    }

    public static boolean isScroll(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && stack.getItem() instanceof SubordinateSkillScrollItem
            && skillId(stack).isPresent();
    }

    public static Optional<SubordinateSkillScrollCatalog.Definition> definition(ItemStack stack) {
        return skillId(stack).flatMap(SubordinateSkillScrollCatalog::findSkill);
    }

    @Override
    public InteractionResult interactLivingEntity(
        ItemStack stack,
        Player player,
        LivingEntity target,
        InteractionHand hand
    ) {
        return teach(stack, player, target, hand);
    }

    public static InteractionResult teach(
        ItemStack stack,
        Player player,
        LivingEntity target,
        InteractionHand hand
    ) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        UUID playerId = serverPlayer.getUUID();
        if (TensuraCompat.subordinateOwnerId(target).filter(playerId::equals).isEmpty()) {
            tell(serverPlayer, "Only the subordinate's owner can teach this scroll.");
            return InteractionResult.FAIL;
        }
        Optional<String> skillId = skillId(stack);
        if (skillId.isEmpty()) {
            tell(serverPlayer, "This scroll has no valid Tensura skill.");
            return InteractionResult.FAIL;
        }

        TensuraSubordinateSkillCompat.LearnResult result =
            TensuraSubordinateSkillCompat.learn(target, skillId.get());
        switch (result.status()) {
            case LEARNED -> {
                String subordinateName = target.getName().getString();
                String learnedName = definition(stack)
                    .map(SubordinateSkillScrollCatalog.Definition::name)
                    .orElse(result.skillId());
                if (!serverPlayer.getAbilities().instabuild) {
                    stack.shrink(1);
                    serverPlayer.getInventory().setChanged();
                }
                serverPlayer.sendSystemMessage(Component.literal(
                    subordinateName + " has learned " + learnedName + "."
                ));
                serverPlayer.playNotifySound(
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.15F
                );
                serverPlayer.swing(hand, true);
                return InteractionResult.CONSUME;
            }
            case ALREADY_KNOWN -> tell(serverPlayer, "The subordinate already knows this spell.");
            case INVALID_ID -> tell(serverPlayer, "This scroll points to an unavailable skill.");
            case UNAVAILABLE -> tell(serverPlayer, "The Tensura skill API is unavailable.");
            case FAILED -> tell(serverPlayer, "The subordinate could not learn this spell.");
        }
        return InteractionResult.FAIL;
    }

    private static void tell(Player player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}
