package com.github.bandithelps.client.particles.managed;

import net.minecraft.world.phys.Vec3;

public final class BeamStallController implements ManagedParticleController {
    private static final double MOVING_DAMPING = 0.98D;
    private static final double STALLED_DAMPING = 0.65D;

    @Override
    public void tick(ManagedParticleInstance instance, ManagedParticleTickContext context) {
        Vec3 velocity = instance.particle().managedVelocity();
        if (instance.ticksAlive() < instance.beamStallAfterTicks()) {
            instance.particle().setManagedVelocity(velocity.scale(MOVING_DAMPING));
            instance.particle().setDrag(0.99F);
            return;
        }

        Vec3 stalled = velocity.scale(STALLED_DAMPING);
        if (stalled.lengthSqr() < 0.00008D) {
            stalled = Vec3.ZERO;
        }
        instance.particle().setManagedVelocity(new Vec3(stalled.x, 0.0D, stalled.z));
        instance.particle().setDrag(0.94F);
    }
}
