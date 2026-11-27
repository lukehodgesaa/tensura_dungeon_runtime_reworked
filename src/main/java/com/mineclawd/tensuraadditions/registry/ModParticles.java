package com.mineclawd.tensuraadditions.registry;

import com.mineclawd.tensuraadditions.TensuraAdditions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
        DeferredRegister.create(Registries.PARTICLE_TYPE, TensuraAdditions.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPAWN_RUNE =
        PARTICLE_TYPES.register("spawn_rune", () -> new SimpleParticleType(true));

    private ModParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
