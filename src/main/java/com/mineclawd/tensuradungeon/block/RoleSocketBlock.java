package com.mineclawd.tensuradungeon.block;

import com.mineclawd.tensuradungeon.runtime.generation.composite.CompositeSocketCapability;
import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class RoleSocketBlock extends Block {
    public static final MapCodec<RoleSocketBlock> CODEC = simpleCodec(RoleSocketBlock::new);
    public static final EnumProperty<SocketRole> ROLE =
        EnumProperty.create("role", SocketRole.class);
    public static final BooleanProperty SYSTEM_CAPABLE = BooleanProperty.create("system_capable");
    public static final BooleanProperty GROUND_CAPABLE = BooleanProperty.create("ground_capable");
    public static final BooleanProperty RANGED_CAPABLE = BooleanProperty.create("ranged_capable");
    public static final BooleanProperty ELITE_CAPABLE = BooleanProperty.create("elite_capable");
    public static final BooleanProperty BARRIER_CAPABLE = BooleanProperty.create("barrier_capable");

    public RoleSocketBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(ROLE, SocketRole.SYSTEM)
            .setValue(SYSTEM_CAPABLE, false)
            .setValue(GROUND_CAPABLE, false)
            .setValue(RANGED_CAPABLE, false)
            .setValue(ELITE_CAPABLE, false)
            .setValue(BARRIER_CAPABLE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        if (!player.getAbilities().instabuild) {
            return InteractionResult.PASS;
        }
        SocketRole next = state.getValue(ROLE).next();
        if (!level.isClientSide) {
            level.setBlock(pos, clearExplicitCapabilities(state).setValue(ROLE, next), Block.UPDATE_CLIENTS);
            player.displayClientMessage(
                Component.translatable(
                    "message.tensura_dungeon.role_socket_role",
                    Component.translatable(
                        "role.tensura_dungeon.role_socket." + next.getSerializedName()
                    )
                ),
                true
            );
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hitResult
    ) {
        CompositeSocketCapability capability = capabilityForDye(stack);
        if (capability == null || !player.getAbilities().instabuild) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            BlockState explicit = ensureExplicitCapabilities(state);
            BooleanProperty property = property(capability);
            explicit = explicit.setValue(property, !explicit.getValue(property));
            if (!hasExplicitCapabilities(explicit)) {
                explicit = explicit.setValue(property, true);
            }
            SocketRole primary = SocketRole.forCapability(primaryCapability(explicit));
            explicit = explicit.setValue(ROLE, primary);
            level.setBlock(pos, explicit, Block.UPDATE_CLIENTS);
            player.displayClientMessage(
                Component.translatable(
                    "message.tensura_dungeon.role_socket_capabilities",
                    capabilitiesSummary(explicit)
                ),
                true
            );
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    public static Set<CompositeSocketCapability> capabilities(BlockState state) {
        EnumSet<CompositeSocketCapability> result = EnumSet.noneOf(CompositeSocketCapability.class);
        if (state.hasProperty(SYSTEM_CAPABLE) && state.getValue(SYSTEM_CAPABLE)) {
            result.add(CompositeSocketCapability.SYSTEM);
        }
        if (state.hasProperty(GROUND_CAPABLE) && state.getValue(GROUND_CAPABLE)) {
            result.add(CompositeSocketCapability.GROUND_SPAWN);
        }
        if (state.hasProperty(RANGED_CAPABLE) && state.getValue(RANGED_CAPABLE)) {
            result.add(CompositeSocketCapability.RANGED_SPAWN);
        }
        if (state.hasProperty(ELITE_CAPABLE) && state.getValue(ELITE_CAPABLE)) {
            result.add(CompositeSocketCapability.ELITE_SPAWN);
        }
        if (state.hasProperty(BARRIER_CAPABLE) && state.getValue(BARRIER_CAPABLE)) {
            result.add(CompositeSocketCapability.BARRIER);
        }
        if (result.isEmpty()) {
            result.add(state.getValue(ROLE).capability());
        }
        return Set.copyOf(result);
    }

    private static BlockState ensureExplicitCapabilities(BlockState state) {
        if (hasExplicitCapabilities(state)) return state;
        return state.setValue(property(state.getValue(ROLE).capability()), true);
    }

    private static BlockState clearExplicitCapabilities(BlockState state) {
        return state
            .setValue(SYSTEM_CAPABLE, false)
            .setValue(GROUND_CAPABLE, false)
            .setValue(RANGED_CAPABLE, false)
            .setValue(ELITE_CAPABLE, false)
            .setValue(BARRIER_CAPABLE, false);
    }

    private static boolean hasExplicitCapabilities(BlockState state) {
        return state.getValue(SYSTEM_CAPABLE)
            || state.getValue(GROUND_CAPABLE)
            || state.getValue(RANGED_CAPABLE)
            || state.getValue(ELITE_CAPABLE)
            || state.getValue(BARRIER_CAPABLE);
    }

    private static BooleanProperty property(CompositeSocketCapability capability) {
        return switch (capability) {
            case SYSTEM -> SYSTEM_CAPABLE;
            case GROUND_SPAWN -> GROUND_CAPABLE;
            case RANGED_SPAWN -> RANGED_CAPABLE;
            case ELITE_SPAWN -> ELITE_CAPABLE;
            case BARRIER -> BARRIER_CAPABLE;
        };
    }

    private static CompositeSocketCapability primaryCapability(BlockState state) {
        for (CompositeSocketCapability capability : CompositeSocketCapability.values()) {
            if (state.getValue(property(capability))) return capability;
        }
        return state.getValue(ROLE).capability();
    }

    private static CompositeSocketCapability capabilityForDye(ItemStack stack) {
        if (stack.is(Items.WHITE_DYE)) return CompositeSocketCapability.SYSTEM;
        if (stack.is(Items.RED_DYE)) return CompositeSocketCapability.GROUND_SPAWN;
        if (stack.is(Items.LIGHT_BLUE_DYE)) return CompositeSocketCapability.RANGED_SPAWN;
        if (stack.is(Items.MAGENTA_DYE)) return CompositeSocketCapability.ELITE_SPAWN;
        if (stack.is(Items.BLUE_DYE)) return CompositeSocketCapability.BARRIER;
        return null;
    }

    private static String capabilitiesSummary(BlockState state) {
        return capabilities(state).stream()
            .map(CompositeSocketCapability::serializedName)
            .sorted()
            .collect(java.util.stream.Collectors.joining(" + "));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
            ROLE,
            SYSTEM_CAPABLE,
            GROUND_CAPABLE,
            RANGED_CAPABLE,
            ELITE_CAPABLE,
            BARRIER_CAPABLE
        );
    }

    public enum SocketRole implements StringRepresentable {
        SYSTEM("system", CompositeSocketCapability.SYSTEM),
        GROUND_SPAWN("ground_spawn", CompositeSocketCapability.GROUND_SPAWN),
        RANGED_SPAWN("ranged_spawn", CompositeSocketCapability.RANGED_SPAWN),
        ELITE_SPAWN("elite_spawn", CompositeSocketCapability.ELITE_SPAWN),
        BARRIER("barrier", CompositeSocketCapability.BARRIER);

        private static final SocketRole[] VALUES = values();
        private final String serializedName;
        private final CompositeSocketCapability capability;

        SocketRole(String serializedName, CompositeSocketCapability capability) {
            this.serializedName = serializedName;
            this.capability = capability;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public CompositeSocketCapability capability() {
            return capability;
        }

        private static SocketRole forCapability(CompositeSocketCapability capability) {
            for (SocketRole role : VALUES) {
                if (role.capability == capability) return role;
            }
            return SYSTEM;
        }

        private SocketRole next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }
}
