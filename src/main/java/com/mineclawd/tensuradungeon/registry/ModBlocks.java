package com.mineclawd.tensuradungeon.registry;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.block.BarrierAnchorBlock;
import com.mineclawd.tensuradungeon.block.ExpeditionTableBlock;
import com.mineclawd.tensuradungeon.block.ExpeditionTablePartBlock;
import com.mineclawd.tensuradungeon.block.FloorExitPortalBlock;
import com.mineclawd.tensuradungeon.block.RoomControllerBlock;
import com.mineclawd.tensuradungeon.block.RoleSocketBlock;
import com.mineclawd.tensuradungeon.block.SegmentConnectorBlock;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TensuraDungeonRuntime.MOD_ID);

    public static final DeferredBlock<Block> ROOM_CONTROLLER = BLOCKS.register("room_controller",
        () -> new RoomControllerBlock(markerProperties(Blocks.LODESTONE, 4.0F, 6.0F)));
    public static final DeferredBlock<Block> SPAWN_MELEE = registerMarker("spawn_melee", Blocks.RED_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> SPAWN_RANGED = registerMarker("spawn_ranged", Blocks.LIGHT_BLUE_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> SPAWN_FLANK = registerMarker("spawn_flank", Blocks.GREEN_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> SPAWN_HIGHGROUND = registerMarker("spawn_highground", Blocks.CYAN_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> SPAWN_ELITE = registerMarker("spawn_elite", Blocks.MAGENTA_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> SPAWN_BOSS = registerMarker("spawn_boss", Blocks.PURPLE_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> BARRIER_ANCHOR = BLOCKS.register("barrier_anchor",
        () -> new BarrierAnchorBlock(markerProperties(Blocks.BLUE_CONCRETE, 1.5F, 6.0F)));
    public static final DeferredBlock<Block> BARRIER_ANCHOR_1 = registerMarker("barrier_anchor_1", Blocks.BLUE_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> BARRIER_ANCHOR_2 = registerMarker("barrier_anchor_2", Blocks.ORANGE_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> BARRIER_ANCHOR_3 = registerMarker("barrier_anchor_3", Blocks.PURPLE_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> BARRIER_ANCHOR_4 = registerMarker("barrier_anchor_4", Blocks.LIME_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> MOB_RESET_ANCHOR = registerMarker("mob_reset_anchor", Blocks.LIME_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> EXIT_ANCHOR = registerMarker("exit_anchor", Blocks.BLACK_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> SAFE_ZONE_ANCHOR = registerMarker("safe_zone_anchor", Blocks.WHITE_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> BOUNDS_ANCHOR = registerMarker("bounds_anchor", Blocks.ORANGE_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> OBJECTIVE_ANCHOR = registerMarker("objective_anchor", Blocks.YELLOW_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> INTERACT_ANCHOR = registerMarker("interact_anchor", Blocks.PINK_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> HUB_SPAWN_ANCHOR = registerMarker("hub_spawn_anchor", Blocks.LIGHT_GRAY_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> HUB_RETURN_ANCHOR = registerMarker("hub_return_anchor", Blocks.GRAY_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> HUB_SAFE_ZONE_ANCHOR = registerMarker("hub_safe_zone_anchor", Blocks.WHITE_TERRACOTTA, 1.5F, 6.0F);
    public static final DeferredBlock<Block> HUB_GATE_ANCHOR = registerMarker("hub_gate_anchor", Blocks.BLUE_TERRACOTTA, 1.5F, 6.0F);
    public static final DeferredBlock<Block> HUB_FLOOR_SELECT_ANCHOR = registerMarker("hub_floor_select_anchor", Blocks.YELLOW_TERRACOTTA, 1.5F, 6.0F);
    public static final DeferredBlock<Block> FLOOR_BUILD_ORIGIN = registerMarker("floor_build_origin", Blocks.BROWN_CONCRETE, 1.5F, 6.0F);
    public static final DeferredBlock<Block> FLOOR_ENTRY_ANCHOR = registerMarker("floor_entry_anchor", Blocks.GREEN_TERRACOTTA, 1.5F, 6.0F);
    public static final DeferredBlock<Block> FLOOR_CONNECTOR_ANCHOR = registerMarker("floor_connector_anchor", Blocks.MAGENTA_TERRACOTTA, 1.5F, 6.0F);
    public static final DeferredBlock<Block> ROLE_SOCKET = BLOCKS.register("role_socket",
        () -> new RoleSocketBlock(markerProperties(Blocks.WHITE_CONCRETE, 1.5F, 6.0F)));
    public static final DeferredBlock<Block> SEGMENT_CONNECTOR = BLOCKS.register("segment_connector",
        () -> new SegmentConnectorBlock(markerProperties(Blocks.LIGHT_GRAY_CONCRETE, 1.5F, 6.0F)));
    public static final DeferredBlock<Block> SEGMENT_CONNECTOR_INTERNAL = BLOCKS.register("segment_connector_internal",
        () -> new SegmentConnectorBlock(markerProperties(Blocks.CYAN_CONCRETE, 1.5F, 6.0F)));
    public static final DeferredBlock<Block> SEGMENT_CONNECTOR_EXTERNAL = BLOCKS.register("segment_connector_external",
        () -> new SegmentConnectorBlock(markerProperties(Blocks.ORANGE_CONCRETE, 1.5F, 6.0F)));
    public static final DeferredBlock<Block> CONNECTOR_EXIT = registerMarker("connector_exit", Blocks.RED_TERRACOTTA, 1.5F, 6.0F);
    public static final DeferredBlock<Block> FLOOR_EXIT_PORTAL = BLOCKS.register("floor_exit_portal",
        () -> new FloorExitPortalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SEA_LANTERN)
            .strength(1.5F, 6.0F)
            .lightLevel(state -> 15)
            .noCollission()
            .noOcclusion()));
    public static final DeferredBlock<Block> EXPEDITION_TABLE = BLOCKS.register("expedition_table",
        () -> new ExpeditionTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CARTOGRAPHY_TABLE)
            .strength(2.5F, 6.0F)
            .noOcclusion()
            .pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<Block> EXPEDITION_TABLE_PART = BLOCKS.register("expedition_table_part",
        () -> new ExpeditionTablePartBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CARTOGRAPHY_TABLE)
            .strength(2.5F, 6.0F)
            .noOcclusion()
            .noLootTable()
            .pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<Block> DEEPSLATE_BRICK_WALL_V2 = BLOCKS.register("deepslate_brick_wall_v2",
        () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_BRICKS)));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private static DeferredBlock<Block> registerMarker(String name, Block visualBase, float destroyTime, float explosionResistance) {
        Supplier<Block> block = () -> new Block(markerProperties(visualBase, destroyTime, explosionResistance));
        return BLOCKS.register(name, block);
    }

    private static BlockBehaviour.Properties markerProperties(Block visualBase, float destroyTime, float explosionResistance) {
        return BlockBehaviour.Properties.ofFullCopy(visualBase)
            .strength(destroyTime, explosionResistance)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops();
    }
}
