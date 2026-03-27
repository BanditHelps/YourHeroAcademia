package com.github.bandithelps.client.particles.managed;

import com.github.bandithelps.particles.ManagedSmokeParticle;
import net.minecraft.world.phys.Vec3;

public final class ManagedParticleInstance {
    private final ManagedSmokeParticle particle;
    private final ManagedParticleOwnerKey ownerKey;
    private final ManagedParticleProfile profile;
    private int ticksAlive;
    private final int beamStallAfterTicks;
    private Vec3 impulseVelocity = Vec3.ZERO;
    private int impulseTicksRemaining;

    public ManagedParticleInstance(
            ManagedSmokeParticle particle,
            ManagedParticleOwnerKey ownerKey,
            ManagedParticleProfile profile,
            int beamStallAfterTicks
    ) {
        this.particle = particle;
        this.ownerKey = ownerKey;
        this.profile = profile;
        this.beamStallAfterTicks = Math.max(0, beamStallAfterTicks);
    }

    public ManagedSmokeParticle particle() {
        return this.particle;
    }

    public ManagedParticleOwnerKey ownerKey() {
        return this.ownerKey;
    }

    public ManagedParticleProfile profile() {
        return this.profile;
    }

    public int ticksAlive() {
        return this.ticksAlive;
    }

    public void incrementTicksAlive() {
        this.ticksAlive++;
    }

    public int beamStallAfterTicks() {
        return this.beamStallAfterTicks;
    }

    public Vec3 impulseVelocity() {
        return this.impulseVelocity;
    }

    public int impulseTicksRemaining() {
        return this.impulseTicksRemaining;
    }

    public void setImpulse(Vec3 impulseVelocity, int impulseTicks) {
        this.impulseVelocity = impulseVelocity;
        this.impulseTicksRemaining = Math.max(0, impulseTicks);
    }

    public void decayImpulse(double dampingFactor) {
        this.impulseVelocity = this.impulseVelocity.scale(dampingFactor);
        if (this.impulseTicksRemaining > 0) {
            this.impulseTicksRemaining--;
        }
        if (this.impulseTicksRemaining <= 0) {
            this.impulseVelocity = Vec3.ZERO;
        }
    }

    public boolean isAlive() {
        return this.particle.isManagedAlive();
    }
}
