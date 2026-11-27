package com.mineclawd.tensuradungeon.registry;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import com.mineclawd.tensuradungeon.item.DungeonRecoveryPotionItem;
import com.mineclawd.tensuradungeon.item.SoulOrbItem;
import com.mineclawd.tensuradungeon.item.SubordinateSkillScrollItem;
import com.mineclawd.tensuradungeon.item.SubordinateSkillScrollCatalog;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TensuraDungeonRuntime.MOD_ID);

    public static final DeferredItem<SoulOrbItem> SOUL_ORB = ITEMS.register("soul_orb",
        () -> new SoulOrbItem(new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<Item> MONSTER_CRYSTAL_TIER_1 = ITEMS.register("monster_crystal_tier_1",
        () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> MONSTER_CRYSTAL_TIER_2 = ITEMS.register("monster_crystal_tier_2",
        () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> MONSTER_CRYSTAL_TIER_3 = ITEMS.register("monster_crystal_tier_3",
        () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> MONSTER_CRYSTAL_TIER_4 = ITEMS.register("monster_crystal_tier_4",
        () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> MONSTER_CRYSTAL_TIER_5 = ITEMS.register("monster_crystal_tier_5",
        () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> MONSTER_CRYSTAL_TIER_6 = ITEMS.register("monster_crystal_tier_6",
        () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> PINNACLE_CAPSULE = ITEMS.register("pinnacle_capsule",
        () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<DungeonRecoveryPotionItem> HEALING_VIAL =
        registerRecoveryPotion("healing_vial", DungeonRecoveryPotionItem.Family.HEALTH,
            DungeonRecoveryPotionItem.Size.VIAL);
    public static final DeferredItem<DungeonRecoveryPotionItem> HEALING_FLASK =
        registerRecoveryPotion("healing_flask", DungeonRecoveryPotionItem.Family.HEALTH,
            DungeonRecoveryPotionItem.Size.FLASK);
    public static final DeferredItem<DungeonRecoveryPotionItem> GREATER_HEALING_FLASK =
        registerRecoveryPotion("greater_healing_flask", DungeonRecoveryPotionItem.Family.HEALTH,
            DungeonRecoveryPotionItem.Size.GREATER_FLASK);
    public static final DeferredItem<DungeonRecoveryPotionItem> MAGICULE_VIAL =
        registerRecoveryPotion("magicule_vial", DungeonRecoveryPotionItem.Family.MAGICULE,
            DungeonRecoveryPotionItem.Size.VIAL);
    public static final DeferredItem<DungeonRecoveryPotionItem> MAGICULE_FLASK =
        registerRecoveryPotion("magicule_flask", DungeonRecoveryPotionItem.Family.MAGICULE,
            DungeonRecoveryPotionItem.Size.FLASK);
    public static final DeferredItem<DungeonRecoveryPotionItem> GREATER_MAGICULE_FLASK =
        registerRecoveryPotion("greater_magicule_flask", DungeonRecoveryPotionItem.Family.MAGICULE,
            DungeonRecoveryPotionItem.Size.GREATER_FLASK);
    public static final DeferredItem<SubordinateSkillScrollItem> SUBORDINATE_SKILL_SCROLL =
        ITEMS.register("subordinate_skill_scroll",
            () -> new SubordinateSkillScrollItem(new Item.Properties()));
    public static final Map<String, DeferredItem<SubordinateSkillScrollItem>> SUBORDINATE_SKILL_SCROLLS =
        registerSubordinateSkillScrolls();
    public static final List<DeferredItem<SubordinateSkillScrollItem>> APPROVED_SUBORDINATE_SKILL_SCROLLS =
        List.copyOf(SUBORDINATE_SKILL_SCROLLS.values());

    public static final DeferredItem<Item> RESURRECTION_BRACELET_NORMAL = registerBracelet("normal");
    public static final DeferredItem<Item> RESURRECTION_BRACELET_SPECIAL = registerBracelet("special");
    public static final DeferredItem<Item> RESURRECTION_BRACELET_RARE = registerBracelet("rare");
    public static final DeferredItem<Item> RESURRECTION_BRACELET_UNIQUE = registerBracelet("unique");
    public static final DeferredItem<Item> RESURRECTION_BRACELET_LEGEND = registerBracelet("legend");
    public static final DeferredItem<Item> RESURRECTION_BRACELET_MYTHIC = registerBracelet("mythic");

    public static final DeferredItem<Item> RETURN_WHISTLE_NORMAL = registerWhistle("normal");
    public static final DeferredItem<Item> RETURN_WHISTLE_SPECIAL = registerWhistle("special");
    public static final DeferredItem<Item> RETURN_WHISTLE_RARE = registerWhistle("rare");
    public static final DeferredItem<Item> RETURN_WHISTLE_UNIQUE = registerWhistle("unique");
    public static final DeferredItem<Item> RETURN_WHISTLE_LEGEND = registerWhistle("legend");
    public static final DeferredItem<Item> RETURN_WHISTLE_MYTHIC = registerWhistle("mythic");

    public static final DeferredItem<BlockItem> ROOM_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.ROOM_CONTROLLER);
    public static final DeferredItem<BlockItem> SPAWN_MELEE = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWN_MELEE);
    public static final DeferredItem<BlockItem> SPAWN_RANGED = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWN_RANGED);
    public static final DeferredItem<BlockItem> SPAWN_FLANK = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWN_FLANK);
    public static final DeferredItem<BlockItem> SPAWN_HIGHGROUND = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWN_HIGHGROUND);
    public static final DeferredItem<BlockItem> SPAWN_ELITE = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWN_ELITE);
    public static final DeferredItem<BlockItem> SPAWN_BOSS = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWN_BOSS);
    public static final DeferredItem<BlockItem> BARRIER_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.BARRIER_ANCHOR);
    public static final DeferredItem<BlockItem> BARRIER_ANCHOR_1 = ITEMS.registerSimpleBlockItem(ModBlocks.BARRIER_ANCHOR_1);
    public static final DeferredItem<BlockItem> BARRIER_ANCHOR_2 = ITEMS.registerSimpleBlockItem(ModBlocks.BARRIER_ANCHOR_2);
    public static final DeferredItem<BlockItem> BARRIER_ANCHOR_3 = ITEMS.registerSimpleBlockItem(ModBlocks.BARRIER_ANCHOR_3);
    public static final DeferredItem<BlockItem> BARRIER_ANCHOR_4 = ITEMS.registerSimpleBlockItem(ModBlocks.BARRIER_ANCHOR_4);
    public static final DeferredItem<BlockItem> MOB_RESET_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.MOB_RESET_ANCHOR);
    public static final DeferredItem<BlockItem> EXIT_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.EXIT_ANCHOR);
    public static final DeferredItem<BlockItem> SAFE_ZONE_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.SAFE_ZONE_ANCHOR);
    public static final DeferredItem<BlockItem> BOUNDS_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.BOUNDS_ANCHOR);
    public static final DeferredItem<BlockItem> OBJECTIVE_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.OBJECTIVE_ANCHOR);
    public static final DeferredItem<BlockItem> INTERACT_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.INTERACT_ANCHOR);
    public static final DeferredItem<BlockItem> HUB_SPAWN_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.HUB_SPAWN_ANCHOR);
    public static final DeferredItem<BlockItem> HUB_RETURN_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.HUB_RETURN_ANCHOR);
    public static final DeferredItem<BlockItem> HUB_SAFE_ZONE_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.HUB_SAFE_ZONE_ANCHOR);
    public static final DeferredItem<BlockItem> HUB_GATE_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.HUB_GATE_ANCHOR);
    public static final DeferredItem<BlockItem> HUB_FLOOR_SELECT_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.HUB_FLOOR_SELECT_ANCHOR);
    public static final DeferredItem<BlockItem> FLOOR_BUILD_ORIGIN = ITEMS.registerSimpleBlockItem(ModBlocks.FLOOR_BUILD_ORIGIN);
    public static final DeferredItem<BlockItem> FLOOR_ENTRY_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.FLOOR_ENTRY_ANCHOR);
    public static final DeferredItem<BlockItem> FLOOR_CONNECTOR_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.FLOOR_CONNECTOR_ANCHOR);
    public static final DeferredItem<BlockItem> ROLE_SOCKET = ITEMS.registerSimpleBlockItem(ModBlocks.ROLE_SOCKET);
    public static final DeferredItem<BlockItem> SEGMENT_CONNECTOR = ITEMS.registerSimpleBlockItem(ModBlocks.SEGMENT_CONNECTOR);
    public static final DeferredItem<BlockItem> SEGMENT_CONNECTOR_INTERNAL = ITEMS.registerSimpleBlockItem(ModBlocks.SEGMENT_CONNECTOR_INTERNAL);
    public static final DeferredItem<BlockItem> SEGMENT_CONNECTOR_EXTERNAL = ITEMS.registerSimpleBlockItem(ModBlocks.SEGMENT_CONNECTOR_EXTERNAL);
    public static final DeferredItem<BlockItem> CONNECTOR_EXIT = ITEMS.registerSimpleBlockItem(ModBlocks.CONNECTOR_EXIT);
    public static final DeferredItem<BlockItem> FLOOR_EXIT_PORTAL = ITEMS.registerSimpleBlockItem(ModBlocks.FLOOR_EXIT_PORTAL);
    public static final DeferredItem<BlockItem> EXPEDITION_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.EXPEDITION_TABLE);
    public static final DeferredItem<BlockItem> DEEPSLATE_BRICK_WALL_V2 = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_BRICK_WALL_V2);

    private ModItems() {
    }

    private static DeferredItem<Item> registerBracelet(String grade) {
        return ITEMS.register("resurrection_bracelet_" + grade,
            () -> new Item(new Item.Properties().stacksTo(5)));
    }

    private static DeferredItem<Item> registerWhistle(String grade) {
        return ITEMS.register("return_whistle_" + grade,
            () -> new Item(new Item.Properties().stacksTo(3)));
    }

    private static DeferredItem<DungeonRecoveryPotionItem> registerRecoveryPotion(
        String id,
        DungeonRecoveryPotionItem.Family family,
        DungeonRecoveryPotionItem.Size size
    ) {
        return ITEMS.register(id, () -> new DungeonRecoveryPotionItem(family, size));
    }

    private static Map<String, DeferredItem<SubordinateSkillScrollItem>> registerSubordinateSkillScrolls() {
        LinkedHashMap<String, DeferredItem<SubordinateSkillScrollItem>> registered = new LinkedHashMap<>();
        for (SubordinateSkillScrollCatalog.Definition definition : SubordinateSkillScrollCatalog.approved()) {
            registered.put(
                definition.slug(),
                ITEMS.register(
                    definition.itemId(),
                    () -> new SubordinateSkillScrollItem(new Item.Properties(), definition)
                )
            );
        }
        return Collections.unmodifiableMap(registered);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
