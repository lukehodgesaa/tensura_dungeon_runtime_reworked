package com.mineclawd.tensuraadditions.registry;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(Registries.SOUND_EVENT, TensuraAdditions.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SPAWN_RUNE_ACTIVATE =
        register("spawn_rune_activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPAWN_RUNE_EMERGE =
        register("spawn_rune_emerge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPAWN_RUNE_FINISH =
        register("spawn_rune_finish");

    private ModSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TensuraAdditions.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
