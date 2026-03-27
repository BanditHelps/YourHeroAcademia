package com.github.bandithelps.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public final class StagnantSmokeParticle extends SmokeParticle {
    private StagnantSmokeParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            SpriteSet sprites
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, 1.0F, sprites);
        this.lifetime = 260 + this.random.nextInt(120);
        this.friction = 0.99F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.quadSize *= 1.35F;
        this.alpha = 0.8F;
        this.xd *= 0.04D;
        this.yd = 0.0D;
        this.zd *= 0.04D;
    }

    @Override
    public void tick() {
        super.tick();
        this.xd *= 0.92D;
        this.yd = 0.0D;
        this.zd *= 0.92D;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
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
            return new StagnantSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
