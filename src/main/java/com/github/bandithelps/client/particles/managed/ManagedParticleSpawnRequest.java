package com.github.bandithelps.client.particles.managed;

import net.minecraft.world.phys.Vec3;

public record ManagedParticleSpawnRequest(
        Vec3 position,
        Vec3 initialVelocity,
        int lifetimeTicks,
        float alpha,
        float sizeScale,
        ManagedParticleProfile profile,
        ManagedParticleOwnerKey ownerKey,
        int beamStallAfterTicks
) {
}
