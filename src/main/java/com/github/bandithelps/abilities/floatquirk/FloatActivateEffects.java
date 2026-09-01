package com.github.bandithelps.abilities.floatquirk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One-shot particles when Float turns on. Ground lift and mid-air arrest read
 * differently, so they use two bursts.
 */
final class FloatActivateEffects {

    private static final int RING_POINTS = 16;

    private FloatActivateEffects() {
    }

    static void play(LivingEntity entity, boolean wasOnGround, Vec3 velocityBefore) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (wasOnGround) {
            playGround(level, entity);
        } else {
            playAir(level, entity, velocityBefore.y < -0.08d);
        }
    }

    /**
     * Dust ring at the feet plus a soft lift, so it reads as leaving the floor.
     */
    private static void playGround(ServerLevel level, LivingEntity entity) {
        double x = entity.getX();
        double y = entity.getY() + 0.05d;
        double z = entity.getZ();

        spawnRing(level, ParticleTypes.CLOUD, x, y, z, 0.55d, 0.02d);
        spawnRing(level, ParticleTypes.POOF, x, y + 0.08d, z, 0.35d, 0.01d);
        level.sendParticles(ParticleTypes.CLOUD, x, y + 0.15d, z, 8, 0.28d, 0.04d, 0.28d, 0.01d);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.2d, z, 6, 0.22d, 0.15d, 0.22d, 0.02d);

        BlockPos below = entity.getBlockPosBelowThatAffectsMyMovement();
        BlockState floor = level.getBlockState(below);
        if (!floor.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor),
                    x, y, z, 10, 0.25d, 0.02d, 0.25d, 0.02d);
        }
    }

    /**
     * Soft arrest burst around the body. Stronger downward streaks when they
     * were actually falling.
     */
    private static void playAir(ServerLevel level, LivingEntity entity, boolean falling) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5d;
        double z = entity.getZ();

        spawnRing(level, ParticleTypes.CLOUD, x, y, z, falling ? 0.65d : 0.5d, 0.015d);
        level.sendParticles(ParticleTypes.CLOUD, x, y, z, falling ? 18 : 10, 0.35d, 0.25d, 0.35d, 0.02d);
        level.sendParticles(ParticleTypes.POOF, x, y - 0.15d, z, falling ? 10 : 5, 0.22d, 0.12d, 0.22d, 0.01d);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, falling ? 10 : 6, 0.28d, 0.35d, 0.28d, 0.015d);

        if (falling) {
            level.sendParticles(ParticleTypes.WHITE_ASH, x, y - 0.2d, z, 14, 0.3d, 0.2d, 0.3d, 0.01d);
            level.sendParticles(ParticleTypes.CLOUD, x, entity.getY(), z, 8, 0.2d, 0.05d, 0.2d, 0.0d);
        }
    }

    private static void spawnRing(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle,
                                  double x, double y, double z, double radius, double speed) {
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = (Math.PI * 2.0d * i) / RING_POINTS;
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            level.sendParticles(particle, px, y, pz, 1, 0.0d, 0.02d, 0.0d, speed);
        }
    }
}
