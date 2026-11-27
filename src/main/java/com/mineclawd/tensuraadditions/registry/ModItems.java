package com.mineclawd.tensuraadditions.registry;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import com.mineclawd.tensuraadditions.item.AnimatedSoulOrbItem;
import com.mineclawd.tensuraadditions.item.DungeonExitPortalItem;
import com.mineclawd.tensuraadditions.item.RecoveryPotionItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TensuraAdditions.MOD_ID);

    public static final DeferredItem<BlockItem> DEEPSLATE_BRICK_WALL_V2 =
        ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_BRICK_WALL_V2);
    public static final DeferredItem<AnimatedSoulOrbItem> SOUL_ORB_COMMON =
        ITEMS.register("soul_orb_common", () -> new AnimatedSoulOrbItem("common"));
    public static final DeferredItem<AnimatedSoulOrbItem> SOUL_ORB_UNCOMMON =
        ITEMS.register("soul_orb_uncommon", () -> new AnimatedSoulOrbItem("uncommon"));
    public static final DeferredItem<AnimatedSoulOrbItem> SOUL_ORB_RARE =
        ITEMS.register("soul_orb_rare", () -> new AnimatedSoulOrbItem("rare"));
    public static final DeferredItem<AnimatedSoulOrbItem> SOUL_ORB_EPIC =
        ITEMS.register("soul_orb_epic", () -> new AnimatedSoulOrbItem("epic"));
    public static final DeferredItem<DungeonExitPortalItem> DUNGEON_EXIT_PORTAL =
        ITEMS.register("dungeon_exit_portal", DungeonExitPortalItem::new);
    public static final DeferredItem<Item> ABYSSAL_LEVIATHAN_KEY =
        ITEMS.register("abyssal_leviathan_key", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> INFERNAL_PHOENIX_KEY =
        ITEMS.register("infernal_phoenix_key", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> RUNIC_COLOSSUS_KEY =
        ITEMS.register("runic_colossus_key", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> VERDANT_WORLDROOT_KEY =
        ITEMS.register("verdant_worldroot_key", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> LUNAR_FENRIR_KEY =
        ITEMS.register("lunar_fenrir_key", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> IMPERIAL_DEMON_GATE_KEY =
        ITEMS.register("imperial_demon_gate_key", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> PRIMORDIAL_MAW_KEY =
        ITEMS.register("primordial_maw_key", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> TWIN_WYRM_KEY =
        ITEMS.register("twin_wyrm_key", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> TEMPEST_DRAGON_KEY =
        ITEMS.register("tempest_dragon_key", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CORE_SATCHEL_NORMAL =
        registerSimpleItem("core_satchel_normal", 1);
    public static final DeferredItem<Item> CORE_SATCHEL_SPECIAL =
        registerSimpleItem("core_satchel_special", 1);
    public static final DeferredItem<Item> CORE_SATCHEL_RARE =
        registerSimpleItem("core_satchel_rare", 1);
    public static final DeferredItem<Item> CORE_SATCHEL_UNIQUE =
        registerSimpleItem("core_satchel_unique", 1);
    public static final DeferredItem<Item> CORE_SATCHEL_LEGEND =
        registerSimpleItem("core_satchel_legend", 1);
    public static final DeferredItem<Item> CORE_SATCHEL_MYTHIC =
        registerSimpleItem("core_satchel_mythic", 1);
    public static final DeferredItem<Item> CORE_SATCHEL_GENESIS =
        registerSimpleItem("core_satchel_genesis", 1);

    public static final DeferredItem<RecoveryPotionItem> HEALING_VIAL =
        registerRecoveryPotion("healing_vial", RecoveryPotionItem.Family.HEALTH,
            RecoveryPotionItem.Size.VIAL);
    public static final DeferredItem<RecoveryPotionItem> HEALING_FLASK =
        registerRecoveryPotion("healing_flask", RecoveryPotionItem.Family.HEALTH,
            RecoveryPotionItem.Size.FLASK);
    public static final DeferredItem<RecoveryPotionItem> GREATER_HEALING_FLASK =
        registerRecoveryPotion("greater_healing_flask", RecoveryPotionItem.Family.HEALTH,
            RecoveryPotionItem.Size.GREATER_FLASK);
    public static final DeferredItem<RecoveryPotionItem> MAGICULE_VIAL =
        registerRecoveryPotion("magicule_vial", RecoveryPotionItem.Family.MAGICULE,
            RecoveryPotionItem.Size.VIAL);
    public static final DeferredItem<RecoveryPotionItem> MAGICULE_FLASK =
        registerRecoveryPotion("magicule_flask", RecoveryPotionItem.Family.MAGICULE,
            RecoveryPotionItem.Size.FLASK);
    public static final DeferredItem<RecoveryPotionItem> GREATER_MAGICULE_FLASK =
        registerRecoveryPotion("greater_magicule_flask", RecoveryPotionItem.Family.MAGICULE,
            RecoveryPotionItem.Size.GREATER_FLASK);

    public static final DeferredItem<Item> RESURRECTION_BRACELET_NORMAL =
        registerSimpleItem("resurrection_bracelet_normal", 5);
    public static final DeferredItem<Item> RESURRECTION_BRACELET_SPECIAL =
        registerSimpleItem("resurrection_bracelet_special", 5);
    public static final DeferredItem<Item> RESURRECTION_BRACELET_RARE =
        registerSimpleItem("resurrection_bracelet_rare", 5);
    public static final DeferredItem<Item> RESURRECTION_BRACELET_UNIQUE =
        registerSimpleItem("resurrection_bracelet_unique", 5);
    public static final DeferredItem<Item> RESURRECTION_BRACELET_LEGEND =
        registerSimpleItem("resurrection_bracelet_legend", 5);
    public static final DeferredItem<Item> RESURRECTION_BRACELET_MYTHIC =
        registerSimpleItem("resurrection_bracelet_mythic", 5);
    public static final DeferredItem<Item> RESURRECTION_BRACELET_GENESIS =
        registerSimpleItem("resurrection_bracelet_genesis", 5);

    public static final DeferredItem<Item> RETURN_WHISTLE_NORMAL =
        registerSimpleItem("return_whistle_normal", 3);
    public static final DeferredItem<Item> RETURN_WHISTLE_SPECIAL =
        registerSimpleItem("return_whistle_special", 3);
    public static final DeferredItem<Item> RETURN_WHISTLE_RARE =
        registerSimpleItem("return_whistle_rare", 3);
    public static final DeferredItem<Item> RETURN_WHISTLE_UNIQUE =
        registerSimpleItem("return_whistle_unique", 3);
    public static final DeferredItem<Item> RETURN_WHISTLE_LEGEND =
        registerSimpleItem("return_whistle_legend", 3);
    public static final DeferredItem<Item> RETURN_WHISTLE_MYTHIC =
        registerSimpleItem("return_whistle_mythic", 3);
    public static final DeferredItem<Item> RETURN_WHISTLE_GENESIS =
        registerSimpleItem("return_whistle_genesis", 3);

    public static final DeferredItem<SwordItem> CELESTIAL_OATH =
        ITEMS.register("celestial_oath", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> DARK_SWORD =
        ITEMS.register("dark_sword", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> VIOLETS_SWORD =
        ITEMS.register("violets_sword", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> VENOM_SWORD =
        ITEMS.register("venom_sword", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> SOLAR_ECLIPSE_SWORD =
        ITEMS.register("solar_eclipse_sword", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> BLACKTIDE_SWORD =
        ITEMS.register("blacktide_sword", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> BRONTIDE_SWORD =
        ITEMS.register("brontide_sword", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> GAIAS_FRACTURE =
        ITEMS.register("gaias_fracture", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> CATASTROPHE_SCYTHE =
        ITEMS.register("catastrophe_scythe", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> WINTERS_REQUIEM =
        ITEMS.register("winters_requiem", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> IGNIS_ASTRA =
        ITEMS.register("ignis_astra", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));
    public static final DeferredItem<SwordItem> SYLVA_ASTRA =
        ITEMS.register("sylva_astra", () -> new SwordItem(
            Tiers.DIAMOND,
            new net.minecraft.world.item.Item.Properties()
                .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))
        ));

    private ModItems() {
    }

    private static DeferredItem<Item> registerSimpleItem(String id, int maxStackSize) {
        return ITEMS.register(id, () -> new Item(new Item.Properties().stacksTo(maxStackSize)));
    }

    private static DeferredItem<RecoveryPotionItem> registerRecoveryPotion(
        String id,
        RecoveryPotionItem.Family family,
        RecoveryPotionItem.Size size
    ) {
        return ITEMS.register(id, () -> new RecoveryPotionItem(family, size));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
