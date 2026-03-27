package com.github.bandithelps.client.particles.managed;

import com.github.bandithelps.cloud.CloudSimConfig;
import net.minecraft.world.phys.Vec3;

public final class DisperseImpulseController implements ManagedParticleController {
    private static final double PER_TICK_IMPULSE_BLEND = 0.75D;

    @Override
    public void tick(ManagedParticleInstance instance, ManagedParticleTickContext context) {
        if (instance.impulseTicksRemaining() <= 0) {
            return;
        }

        Vec3 velocity = instance.particle().managedVelocity().add(instance.impulseVelocity().scale(PER_TICK_IMPULSE_BLEND));
        instance.particle().setManagedVelocity(velocity);
        instance.particle().setDrag(CloudSimConfig.managedDisperseDrag());
        instance.particle().clampRemainingLifetime(CloudSimConfig.managedDisperseLifetimeTicks());
        instance.decayImpulse(CloudSimConfig.managedDisperseImpulseDamping());
    }
}
