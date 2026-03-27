package com.github.bandithelps.client.particles.managed;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public record ManagedParticleTickContext(
        RandomSource random,
        Vec3 playerPosition
) {
}
