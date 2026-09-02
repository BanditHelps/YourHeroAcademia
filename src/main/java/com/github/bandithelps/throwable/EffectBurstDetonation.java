package com.github.bandithelps.throwable;

import com.github.bandithelps.effects.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Instant effect burst. Cover blocks line of sight. Players looking at the blast get the
 * full flash; looking away shortens and weakens it instead of skipping it.
 */
public final class EffectBurstDetonation implements ThrowableDetonation {
    public static final EffectBurstDetonation FLASHBANG = new EffectBurstDetonation();

    /** Half-angle of the player's view cone. Inside this, exposure is full. */
    private static final float PLAYER_VISION_HALF_ANGLE_DEGREES = 80.0f;
    private static final double PLAYER_VISION_DOT = Math.cos(PLAYER_VISION_HALF_ANGLE_DEGREES * Mth.DEG_TO_RAD);
    /** Even fully turned around, a nearby blast still washes the screen. */
    private static final float MIN_LOOK_AWAY_EXPOSURE = 0.4f;
    /** Amplifier 0 = looking at it, {@value} = fully looking away. Overlay reads this. */
    public static final int LOOK_AWAY_AMPLIFIER_MAX = 3;
    /** Inside this distance the flash is in your face even if you are looking slightly away. */
    private static final double POINT_BLANK_DISTANCE_SQR = 3.0;

    private EffectBurstDetonation() {
    }

    @Override
    public void detonate(ThrownWeaponEntity projectile, ServerLevel level, ThrowableWeaponSpec spec) {
        float radius = spec.scaledEffectRadius();
        int duration = spec.scaledEffectDurationTicks();
        int amplifier = spec.effectAmplifier();
        Vec3 origin = projectile.position();

        if (radius > 0.0f && duration > 0) {
            AABB box = AABB.ofSize(origin, radius * 2.0, radius * 2.0, radius * 2.0);
            List<LivingEntity> victims = level.getEntitiesOfClass(
                    LivingEntity.class,
                    box,
                    living -> living.isAlive() && living.distanceToSqr(origin) <= (double) radius * radius
            );
            for (LivingEntity living : victims) {
                if (!hasLineOfSight(level, origin, living, projectile)) {
                    continue;
                }
                int appliedDuration = duration;
                int appliedAmplifier = amplifier;
                if (living instanceof Player) {
                    float exposure = facingExposure(living, origin);
                    appliedDuration = Math.max(8, Math.round(duration * exposure));
                    appliedAmplifier = amplifier + lookAwayAmplifier(exposure);
                }
                living.addEffect(new MobEffectInstance(ModEffects.FLASHBANGED, appliedDuration, appliedAmplifier, false, false, false));
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y, origin.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y + 0.2, origin.z, 18, 0.4, 0.3, 0.4, 0.05);
        level.sendParticles(ParticleTypes.FIREWORK, origin.x, origin.y, origin.z, 12, 0.35, 0.25, 0.35, 0.08);
        level.playSound(
                null,
                projectile.blockPosition(),
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS,
                0.9f,
                1.7f
        );
        projectile.discard();
    }

    private static boolean hasLineOfSight(ServerLevel level, Vec3 origin, LivingEntity target, ThrownWeaponEntity projectile) {
        Vec3 to = target.getEyePosition();
        if (origin.distanceToSqr(to) < 0.04) {
            return true;
        }
        BlockHitResult hit = level.clip(new ClipContext(
                origin,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                projectile
        ));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(origin) >= origin.distanceToSqr(to) - 0.15;
    }

    /**
     * 1 if the blast is in the camera cone (or point-blank), down to
     * {@link #MIN_LOOK_AWAY_EXPOSURE} when looking directly away.
     */
    private static float facingExposure(LivingEntity player, Vec3 origin) {
        Vec3 toFlash = origin.subtract(player.getEyePosition());
        double distanceSqr = toFlash.lengthSqr();
        if (distanceSqr < POINT_BLANK_DISTANCE_SQR) {
            return 1.0f;
        }
        double dot = player.getLookAngle().dot(toFlash.normalize());
        if (dot >= PLAYER_VISION_DOT) {
            return 1.0f;
        }
        float t = Mth.inverseLerp((float) dot, -1.0f, (float) PLAYER_VISION_DOT);
        return Mth.lerp(Mth.clamp(t, 0.0f, 1.0f), MIN_LOOK_AWAY_EXPOSURE, 1.0f);
    }

    private static int lookAwayAmplifier(float exposure) {
        float lookingAway = 1.0f - Mth.inverseLerp(exposure, MIN_LOOK_AWAY_EXPOSURE, 1.0f);
        return Mth.clamp(Math.round(lookingAway * LOOK_AWAY_AMPLIFIER_MAX), 0, LOOK_AWAY_AMPLIFIER_MAX);
    }
}
