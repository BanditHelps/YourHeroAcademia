package com.github.bandithelps.particles;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, YourHeroAcademia.MODID);

    public static final Supplier<SimpleParticleType> SMOKESCREEN =
            PARTICLE_TYPES.register("smokescreen", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
