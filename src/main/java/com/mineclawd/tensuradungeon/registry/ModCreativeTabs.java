package com.mineclawd.tensuradungeon.registry;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TensuraDungeonRuntime.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TENSURA_DUNGEON_TAB = TABS.register("tensura_dungeon",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tensura_dungeon"))
            .icon(() -> ModItems.ROOM_CONTROLLER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.ROOM_CONTROLLER.get());
                output.accept(ModItems.SPAWN_MELEE.get());
                output.accept(ModItems.SPAWN_RANGED.get());
                output.accept(ModItems.SPAWN_FLANK.get());
                output.accept(ModItems.SPAWN_HIGHGROUND.get());
                output.accept(ModItems.SPAWN_ELITE.get());
                output.accept(ModItems.SPAWN_BOSS.get());
                output.accept(ModItems.BARRIER_ANCHOR.get());
                output.accept(ModItems.BARRIER_ANCHOR_1.get());
                output.accept(ModItems.BARRIER_ANCHOR_2.get());
                output.accept(ModItems.BARRIER_ANCHOR_3.get());
                output.accept(ModItems.BARRIER_ANCHOR_4.get());
                output.accept(ModItems.MOB_RESET_ANCHOR.get());
                output.accept(ModItems.EXIT_ANCHOR.get());
                output.accept(ModItems.SAFE_ZONE_ANCHOR.get());
                output.accept(ModItems.BOUNDS_ANCHOR.get());
                output.accept(ModItems.OBJECTIVE_ANCHOR.get());
                output.accept(ModItems.INTERACT_ANCHOR.get());
                output.accept(ModItems.HUB_SPAWN_ANCHOR.get());
                output.accept(ModItems.HUB_RETURN_ANCHOR.get());
                output.accept(ModItems.HUB_SAFE_ZONE_ANCHOR.get());
                output.accept(ModItems.HUB_GATE_ANCHOR.get());
                output.accept(ModItems.HUB_FLOOR_SELECT_ANCHOR.get());
                output.accept(ModItems.FLOOR_BUILD_ORIGIN.get());
                output.accept(ModItems.FLOOR_ENTRY_ANCHOR.get());
                output.accept(ModItems.FLOOR_CONNECTOR_ANCHOR.get());
                output.accept(ModItems.ROLE_SOCKET.get());
                output.accept(ModItems.SEGMENT_CONNECTOR.get());
                output.accept(ModItems.SEGMENT_CONNECTOR_INTERNAL.get());
                output.accept(ModItems.SEGMENT_CONNECTOR_EXTERNAL.get());
                output.accept(ModItems.CONNECTOR_EXIT.get());
                output.accept(ModItems.FLOOR_EXIT_PORTAL.get());
                output.accept(ModItems.EXPEDITION_TABLE.get());
                output.accept(ModItems.DEEPSLATE_BRICK_WALL_V2.get());
                 output.accept(ModItems.SUBORDINATE_SKILL_SCROLL.get());
                 for (var scroll : ModItems.APPROVED_SUBORDINATE_SKILL_SCROLLS) {
                     output.accept(scroll.get());
                 }
                 output.accept(ModItems.HEALING_VIAL.get());
                output.accept(ModItems.HEALING_FLASK.get());
                output.accept(ModItems.GREATER_HEALING_FLASK.get());
                output.accept(ModItems.MAGICULE_VIAL.get());
                output.accept(ModItems.MAGICULE_FLASK.get());
                output.accept(ModItems.GREATER_MAGICULE_FLASK.get());
            })
            .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
