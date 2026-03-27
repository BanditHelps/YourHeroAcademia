package com.github.bandithelps.client.particles.managed;

import net.minecraft.world.phys.Vec3;

public final class StagnantSmokeController implements ManagedParticleController {
    private static final double HORIZONTAL_DAMPING = 0.9D;

    @Override
    public void tick(ManagedParticleInstance instance, ManagedParticleTickContext context) {
        Vec3 velocity = instance.particle().managedVelocity();
        velocity = new Vec3(velocity.x * HORIZONTAL_DAMPING, 0.0D, velocity.z * HORIZONTAL_DAMPING);
        instance.particle().setManagedVelocity(velocity);
        instance.particle().setDrag(0.985F);
    }
}
