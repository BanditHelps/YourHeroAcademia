package com.github.bandithelps.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Short-lived tinted flake used when a Blackwhip chain dissolves in the air.
 */
public class BlackwhipDissolveParticle extends SingleQuadParticle {

    private final SpriteSet sprites;
    private final float startAlpha;

    protected BlackwhipDissolveParticle(ClientLevel level, double x, double y, double z,
                                        double xa, double ya, double za, SpriteSet sprites, int argb) {
        super(level, x, y, z, xa, ya, za, sprites.first());
        this.sprites = sprites;
        this.gravity = 0.012f;
        this.hasPhysics = false;
        this.friction = 0.90f;
        this.lifetime = 8 + this.random.nextInt(6);
        this.quadSize = 0.06f + this.random.nextFloat() * 0.05f;
        this.startAlpha = 0.55f + this.random.nextFloat() * 0.30f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        this.setColor(r, g, b);
        this.setAlpha(this.startAlpha);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float life = 1.0f - (float) this.age / (float) Math.max(1, this.lifetime);
        this.setAlpha(this.startAlpha * life);
        this.quadSize *= 0.94f;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static void spawn(ClientLevel level, Vec3 pos, Vec3 vel, int argb) {
        SpriteSet sprites = Provider.sprites;
        if (sprites == null || level == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.particleEngine == null) {
            return;
        }
        mc.particleEngine.add(new BlackwhipDissolveParticle(
                level, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, sprites, argb));
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        static SpriteSet sprites;
        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
            sprites = spriteSet;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            int argb = 0xFF25BE9C;
            return new BlackwhipDissolveParticle(level, x, y, z, xd, yd, zd, this.spriteSet, argb);
        }
    }
}
