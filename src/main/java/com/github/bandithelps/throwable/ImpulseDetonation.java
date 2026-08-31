package com.github.bandithelps.throwable;

import com.github.bandithelps.utils.blockdisplays.BlockDisplaySummoner;
import com.github.bandithelps.utils.blockdisplays.BlockDisplayVisualOptions;
import com.github.bandithelps.utils.blockdisplays.RgbaBlendMode;
import com.github.bandithelps.utils.blockdisplays.RgbaColor;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Optional;

/**
 * Knockback blast with a fast blue RGBA shockwave instead of a TNT boom.
 */
public final class ImpulseDetonation implements ThrowableDetonation {
    public static final ImpulseDetonation INSTANCE = new ImpulseDetonation();

    private static final int RING_TICK_SPEED = 5;
    private static final int EFFECT_LIFETIME_TICKS = 18;
    private static final double RING_DENSITY = 48.0;
    private static final Vector3f RING_INITIAL_SCALE = new Vector3f(0.15f, 0.15f, 0.15f);
    private static final Vector3f RING_FINAL_SCALE = new Vector3f(0.45f, 0.45f, 0.45f);
    private static final Vector3f PULSE_INITIAL_SCALE = new Vector3f(0.3f, 0.3f, 0.3f);
    private static final Vector3f PULSE_FINAL_SCALE = new Vector3f(2.5f, 2.5f, 2.5f);
    private static final BlockDisplayVisualOptions BLUE_ADDITIVE = new BlockDisplayVisualOptions(
            false,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false,
            Optional.empty(),
            Optional.of(new RgbaColor(40, 160, 255, 150)),
            RgbaBlendMode.ADDITIVE
    );

    private ImpulseDetonation() {
    }

    @Override
    public void detonate(ThrownWeaponEntity projectile, ServerLevel level, ThrowableWeaponSpec spec) {
        float radius = spec.scaledExplosionRadius();
        if (radius <= 0.0f) {
            projectile.discard();
            return;
        }

        double x = projectile.getX();
        double y = projectile.getY() + 0.45;
        double z = projectile.getZ();
        Vec3 origin = new Vec3(x, y, z);

        ThrowableExplosionDamageCalculator calculator = new ThrowableExplosionDamageCalculator(
                spec.shouldBreakBlocks(),
                spec.scaledExplosionDamage() != 0.0f,
                spec.explosionKnockback(),
                spec.scaledExplosionDamage()
        );
        level.explode(
                projectile,
                null,
                calculator,
                x,
                y,
                z,
                radius,
                false,
                Level.ExplosionInteraction.NONE,
                ParticleTypes.SONIC_BOOM,
                ParticleTypes.GUST_EMITTER_SMALL,
                WeightedList.of(new ExplosionParticleInfo(ParticleTypes.GUST, 1.0f, 1.0f)),
                SoundEvents.WIND_CHARGE_BURST
        );

        BlockDisplaySummoner.summonShockwave(
                level,
                origin,
                level.getRandom(),
                radius,
                RING_TICK_SPEED,
                RING_DENSITY,
                Collections.emptyList(),
                new Vector3f(),
                RING_INITIAL_SCALE,
                RING_FINAL_SCALE,
                EFFECT_LIFETIME_TICKS,
                false,
                true,
                BLUE_ADDITIVE
        );
        BlockDisplaySummoner.summonRgbaPulse(
                level,
                origin,
                PULSE_INITIAL_SCALE,
                PULSE_FINAL_SCALE,
                RING_TICK_SPEED,
                EFFECT_LIFETIME_TICKS,
                BLUE_ADDITIVE
        );

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 24, 0.35, 0.2, 0.35, 0.12);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 16, 0.4, 0.25, 0.4, 0.04);
        level.playSound(null, projectile.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 1.3f);

        projectile.discard();
    }
}
