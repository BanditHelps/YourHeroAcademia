package com.github.bandithelps.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class ManagedSmokeParticle extends SmokeParticle {
    private static SpriteSet spriteSet;
    private final float baseQuadSize;

    public ManagedSmokeParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, 1.0F, requireSpriteSet());
        this.friction = 0.985F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.alpha = 0.8F;
        this.quadSize *= 1.2F;
        this.baseQuadSize = this.quadSize;
    }

    public static boolean isReady() {
        return spriteSet != null;
    }

    public void setManagedVelocity(Vec3 velocity) {
        this.xd = velocity.x;
        this.yd = velocity.y;
        this.zd = velocity.z;
    }

    public Vec3 managedVelocity() {
        return new Vec3(this.xd, this.yd, this.zd);
    }

    public Vec3 managedPosition() {
        return new Vec3(this.x, this.y, this.z);
    }

    public boolean isManagedAlive() {
        return this.isAlive();
    }

    public int ageTicks() {
        return this.age;
    }

    public int remainingLifetimeTicks() {
        return Math.max(0, this.lifetime - this.age);
    }

    public void setLifetimeTicks(int lifetimeTicks) {
        int clamped = Math.max(1, lifetimeTicks);
        this.lifetime = clamped;
        if (this.age >= clamped) {
            this.remove();
        }
    }

    public void clampRemainingLifetime(int maxRemainingTicks) {
        int clampedRemaining = Math.max(1, maxRemainingTicks);
        int targetLifetime = this.age + clampedRemaining;
        if (targetLifetime < this.lifetime) {
            this.lifetime = targetLifetime;
        }
    }

    public void setDrag(float drag) {
        this.friction = Mth.clamp(drag, 0.0F, 1.0F);
    }

    public void setManagedAlpha(float alpha) {
        this.alpha = Mth.clamp(alpha, 0.0F, 1.0F);
    }

    public void setManagedSize(float sizeScale) {
        this.quadSize = Math.max(0.03F, this.baseQuadSize * sizeScale);
    }

    private static SpriteSet requireSpriteSet() {
        if (spriteSet == null) {
            throw new IllegalStateException("Managed smoke sprite set is not registered yet.");
        }
        return spriteSet;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
            ManagedSmokeParticle.spriteSet = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                RandomSource random
        ) {
            return new ManagedSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
