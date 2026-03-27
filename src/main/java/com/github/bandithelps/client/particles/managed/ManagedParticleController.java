package com.github.bandithelps.client.particles.managed;

public interface ManagedParticleController {
    void tick(ManagedParticleInstance instance, ManagedParticleTickContext context);
}
