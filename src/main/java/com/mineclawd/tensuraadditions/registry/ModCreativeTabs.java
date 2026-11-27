package com.mineclawd.tensuraadditions.registry;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TensuraAdditions.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TENSURA_ADDITIONS_TAB =
        TABS.register("tensura_additions", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tensura_additions"))
            .icon(() -> ModItems.SOUL_ORB_EPIC.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.DEEPSLATE_BRICK_WALL_V2.get());
                output.accept(ModItems.SOUL_ORB_COMMON.get());
                output.accept(ModItems.SOUL_ORB_UNCOMMON.get());
                output.accept(ModItems.SOUL_ORB_RARE.get());
                output.accept(ModItems.SOUL_ORB_EPIC.get());
                output.accept(ModItems.DUNGEON_EXIT_PORTAL.get());
                output.accept(ModItems.ABYSSAL_LEVIATHAN_KEY.get());
                output.accept(ModItems.INFERNAL_PHOENIX_KEY.get());
                output.accept(ModItems.RUNIC_COLOSSUS_KEY.get());
                output.accept(ModItems.VERDANT_WORLDROOT_KEY.get());
                output.accept(ModItems.LUNAR_FENRIR_KEY.get());
                output.accept(ModItems.IMPERIAL_DEMON_GATE_KEY.get());
                output.accept(ModItems.PRIMORDIAL_MAW_KEY.get());
                output.accept(ModItems.TWIN_WYRM_KEY.get());
                output.accept(ModItems.TEMPEST_DRAGON_KEY.get());
                output.accept(ModItems.CORE_SATCHEL_NORMAL.get());
                output.accept(ModItems.CORE_SATCHEL_SPECIAL.get());
                output.accept(ModItems.CORE_SATCHEL_RARE.get());
                output.accept(ModItems.CORE_SATCHEL_UNIQUE.get());
                output.accept(ModItems.CORE_SATCHEL_LEGEND.get());
                output.accept(ModItems.CORE_SATCHEL_MYTHIC.get());
                output.accept(ModItems.CORE_SATCHEL_GENESIS.get());
                output.accept(ModItems.HEALING_VIAL.get());
                output.accept(ModItems.HEALING_FLASK.get());
                output.accept(ModItems.GREATER_HEALING_FLASK.get());
                output.accept(ModItems.MAGICULE_VIAL.get());
                output.accept(ModItems.MAGICULE_FLASK.get());
                output.accept(ModItems.GREATER_MAGICULE_FLASK.get());
                output.accept(ModItems.RESURRECTION_BRACELET_NORMAL.get());
                output.accept(ModItems.RESURRECTION_BRACELET_SPECIAL.get());
                output.accept(ModItems.RESURRECTION_BRACELET_RARE.get());
                output.accept(ModItems.RESURRECTION_BRACELET_UNIQUE.get());
                output.accept(ModItems.RESURRECTION_BRACELET_LEGEND.get());
                output.accept(ModItems.RESURRECTION_BRACELET_MYTHIC.get());
                output.accept(ModItems.RESURRECTION_BRACELET_GENESIS.get());
                output.accept(ModItems.RETURN_WHISTLE_NORMAL.get());
                output.accept(ModItems.RETURN_WHISTLE_SPECIAL.get());
                output.accept(ModItems.RETURN_WHISTLE_RARE.get());
                output.accept(ModItems.RETURN_WHISTLE_UNIQUE.get());
                output.accept(ModItems.RETURN_WHISTLE_LEGEND.get());
                output.accept(ModItems.RETURN_WHISTLE_MYTHIC.get());
                output.accept(ModItems.RETURN_WHISTLE_GENESIS.get());
                output.accept(ModItems.CELESTIAL_OATH.get());
                output.accept(ModItems.DARK_SWORD.get());
                output.accept(ModItems.VIOLETS_SWORD.get());
                output.accept(ModItems.VENOM_SWORD.get());
                output.accept(ModItems.SOLAR_ECLIPSE_SWORD.get());
                output.accept(ModItems.BLACKTIDE_SWORD.get());
                output.accept(ModItems.BRONTIDE_SWORD.get());
                output.accept(ModItems.GAIAS_FRACTURE.get());
                output.accept(ModItems.CATASTROPHE_SCYTHE.get());
                output.accept(ModItems.WINTERS_REQUIEM.get());
                output.accept(ModItems.IGNIS_ASTRA.get());
                output.accept(ModItems.SYLVA_ASTRA.get());
            })
            .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
