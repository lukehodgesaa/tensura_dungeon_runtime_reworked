package com.mineclawd.tensuradungeon.registry;

import com.mineclawd.tensuradungeon.TensuraDungeonRuntime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSoundEvents {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, TensuraDungeonRuntime.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_GATE_OPEN = register("hub_gate_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_GATE_CLOSE = register("hub_gate_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROOM_AWAKEN = register("room_awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROOM_BARRIER_CLOSE = register("room_barrier_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROOM_BARRIER_OPEN = register("room_barrier_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> OBJECTIVE_START = register("objective_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> OBJECTIVE_PROGRESS = register("objective_progress");
    public static final DeferredHolder<SoundEvent, SoundEvent> OBJECTIVE_COMPLETE = register("objective_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAVE_INCOMING = register("wave_incoming");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELITE_REVEAL = register("elite_reveal");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROOM_COMPLETE = register("room_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOOR_FINALE = register("floor_finale");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOOR_COMPLETE = register("floor_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAFE_ROOM = register("safe_room");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLORATION_AMBIENCE = register("exploration_ambience");
    public static final DeferredHolder<SoundEvent, SoundEvent> CREATURE_AMBIENCE = register("creature_ambience");
    public static final DeferredHolder<SoundEvent, SoundEvent> BRANCH_PREVIEW = register("branch_preview");
    public static final DeferredHolder<SoundEvent, SoundEvent> RETURN_WHISTLE_CHANNEL = register("return_whistle_channel");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_CRACK = register("rift_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_OPEN = register("rift_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_CLOSE = register("rift_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_PRESSURE_WHIRL = register("rift_pressure_whirl");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_VOID_BOOM = register("rift_void_boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_VOID_CRACK_1 = register("rift_void_crack_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_VOID_CRACK_2 = register("rift_void_crack_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_VOID_CRACK_3 = register("rift_void_crack_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_VOID_CRACK_4 = register("rift_void_crack_4");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_PULL_WIND = register("rift_pull_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_MENU_HOVER = register("ui_menu_hover");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_MENU_SELECT = register("ui_menu_select");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_MENU_OPEN = register("ui_menu_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_MENU_CLOSE = register("ui_menu_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOP_CONFIRMATION_OPEN = register("shop_confirmation_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOP_PURCHASE_SUCCESS = register("shop_purchase_success");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOP_PURCHASE_FAIL = register("shop_purchase_fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOP_MAJOR_UNLOCK = register("shop_major_unlock");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOADOUT_EQUIPPED_HELMET = register("loadout_equipped_helmet");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOADOUT_EQUIPPED_CHEST = register("loadout_equipped_chest");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOADOUT_EQUIPPED_LEGS = register("loadout_equipped_legs");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOADOUT_EQUIPPED_SHOES = register("loadout_equipped_shoes");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOADOUT_EQUIPPED_WEAPON = register("loadout_equipped_weapon");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIMURU_OPENING_LOADOUT = register("rimuru_opening_loadout");
    private static final List<String> DIALOGUE_VOICE_CHARACTERS = List.of(
        "gobta", "kaijin", "kurobe", "mjmile", "shuna", "treyni", "fallback"
    );
    private static final List<String> DIALOGUE_VOICE_POOLS = List.of(
        "calm", "soft", "bright", "uncertain", "intense", "neutral"
    );
    private static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> DIALOGUE_VOICE_POOL_EVENTS =
        registerDialogueVoicePools();
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_MUSIC_ECHOES = register("hub_music_echoes");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_MUSIC_INNER_HALL = register("hub_music_inner_hall");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_MUSIC_LABYRINTH = register("hub_music_labyrinth");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_MUSIC_ASCENDANT = register("hub_music_ascendant");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_MUSIC_MOMENT = register("hub_music_moment");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_MUSIC_SANCTUM = register("hub_music_sanctum");

    private ModSoundEvents() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    public static DeferredHolder<SoundEvent, SoundEvent> dialogueVoicePool(
        String character,
        String pool,
        boolean variantB
    ) {
        String key = "dialogue_voice_" + character + "_" + pool + "_" + (variantB ? "b" : "a");
        return DIALOGUE_VOICE_POOL_EVENTS.getOrDefault(
            key,
            DIALOGUE_VOICE_POOL_EVENTS.get("dialogue_voice_fallback_neutral_a")
        );
    }

    private static Map<String, DeferredHolder<SoundEvent, SoundEvent>> registerDialogueVoicePools() {
        Map<String, DeferredHolder<SoundEvent, SoundEvent>> events = new LinkedHashMap<>();
        for (String character : DIALOGUE_VOICE_CHARACTERS) {
            for (String pool : DIALOGUE_VOICE_POOLS) {
                events.put(
                    "dialogue_voice_" + character + "_" + pool + "_a",
                    register("dialogue_voice_" + character + "_" + pool + "_a")
                );
                events.put(
                    "dialogue_voice_" + character + "_" + pool + "_b",
                    register("dialogue_voice_" + character + "_" + pool + "_b")
                );
            }
        }
        return Map.copyOf(events);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TensuraDungeonRuntime.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
