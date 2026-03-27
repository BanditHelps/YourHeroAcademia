package com.github.bandithelps.particles;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YhaParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, YourHeroAcademia.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STAGNANT_SMOKE =
            PARTICLES.register("stagnant_smoke", () -> new SimpleParticleType(false));

    private YhaParticles() {
    }
}
