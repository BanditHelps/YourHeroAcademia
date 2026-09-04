package com.github.bandithelps.throwable;

import com.github.bandithelps.ModGameRules;
import net.minecraft.server.level.ServerLevel;

/**
 * Per-item configuration for a {@link ThrowableWeaponItem}. Set these values at item
 * registration; {@link #scale()} multiplies radius, explosion power, and effect duration
 * at detonation time.
 */
public final class ThrowableWeaponSpec {
    public static final ThrowableWeaponSpec DEFAULT = builder().build();

    private final float scale;
    private final float minThrowSpeed;
    private final float maxThrowSpeed;
    private final int maxChargeTicks;
    private final int minChargeTicks;
    private final int fuseTicks;
    private final FuseMode fuseMode;
    private final boolean bounce;
    private final float bounceDamping;
    private final boolean stickOnImpact;
    private final boolean breaksBlocks;
    private final float explosionRadius;
    private final float explosionDamage;
    private final float explosionKnockback;
    private final float effectRadius;
    private final int effectDurationTicks;
    private final int effectAmplifier;
    private final int cooldownTicks;
    private final ThrowableDetonation detonation;

    private ThrowableWeaponSpec(Builder builder) {
        this.scale = Math.max(0.0f, builder.scale);
        this.minThrowSpeed = Math.max(0.0f, builder.minThrowSpeed);
        this.maxThrowSpeed = Math.max(this.minThrowSpeed, builder.maxThrowSpeed);
        this.maxChargeTicks = Math.max(0, builder.maxChargeTicks);
        this.minChargeTicks = Math.max(0, builder.minChargeTicks);
        this.fuseTicks = Math.max(0, builder.fuseTicks);
        this.fuseMode = builder.fuseMode == null ? FuseMode.FROM_THROW : builder.fuseMode;
        this.bounce = builder.bounce;
        this.bounceDamping = Math.max(0.0f, builder.bounceDamping);
        this.stickOnImpact = builder.stickOnImpact;
        this.breaksBlocks = builder.breaksBlocks;
        this.explosionRadius = Math.max(0.0f, builder.explosionRadius);
        this.explosionDamage = builder.explosionDamage;
        this.explosionKnockback = Math.max(0.0f, builder.explosionKnockback);
        this.effectRadius = Math.max(0.0f, builder.effectRadius);
        this.effectDurationTicks = Math.max(0, builder.effectDurationTicks);
        this.effectAmplifier = Math.max(0, builder.effectAmplifier);
        this.cooldownTicks = Math.max(0, builder.cooldownTicks);
        this.detonation = builder.detonation == null ? ThrowableDetonation.NONE : builder.detonation;
    }

    public static Builder builder() {
        return new Builder();
    }

    public float scale() {
        return scale;
    }

    public float minThrowSpeed() {
        return minThrowSpeed;
    }

    public float maxThrowSpeed() {
        return maxThrowSpeed;
    }

    public int maxChargeTicks() {
        return maxChargeTicks;
    }

    public int minChargeTicks() {
        return minChargeTicks;
    }

    public int fuseTicks() {
        return fuseTicks;
    }

    public FuseMode fuseMode() {
        return fuseMode;
    }

    public boolean bounce() {
        return bounce;
    }

    public float bounceDamping() {
        return bounceDamping;
    }

    public boolean stickOnImpact() {
        return stickOnImpact;
    }

    public boolean breaksBlocks() {
        return breaksBlocks;
    }

    public boolean shouldBreakBlocks(ServerLevel level) {
        return breaksBlocks && ModGameRules.throwableBlockDamage(level);
    }

    public float explosionRadius() {
        return explosionRadius;
    }

    public float scaledExplosionRadius() {
        return explosionRadius * scale;
    }

    /**
     * Max entity damage at the blast center. Negative means vanilla radius-scaled damage.
     * {@code 0} deals no entity damage. Multiplied by {@link #scale()}.
     */
    public float explosionDamage() {
        return explosionDamage;
    }

    public float scaledExplosionDamage() {
        if (explosionDamage < 0.0f) {
            return explosionDamage;
        }
        return explosionDamage * scale;
    }

    public boolean usesVanillaExplosionDamage() {
        return explosionDamage < 0.0f;
    }

    public float explosionKnockback() {
        return explosionKnockback;
    }

    public float effectRadius() {
        return effectRadius;
    }

    public float scaledEffectRadius() {
        return effectRadius * scale;
    }

    public int effectDurationTicks() {
        return effectDurationTicks;
    }

    public int scaledEffectDurationTicks() {
        return Math.max(1, Math.round(effectDurationTicks * scale));
    }

    public int effectAmplifier() {
        return effectAmplifier;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public ThrowableDetonation detonation() {
        return detonation;
    }

    public boolean isInstantThrow() {
        return maxChargeTicks <= 0;
    }

    public float throwSpeedForCharge(int usedTicks) {
        if (isInstantThrow() || maxChargeTicks <= 0) {
            return maxThrowSpeed;
        }
        float power = Math.max(0.1f, Math.min(1.0f, usedTicks / (float) maxChargeTicks));
        return minThrowSpeed + (maxThrowSpeed - minThrowSpeed) * power;
    }

    public static final class Builder {
        private float scale = 1.0f;
        private float minThrowSpeed = 0.4f;
        private float maxThrowSpeed = 1.5f;
        private int maxChargeTicks = 20;
        private int minChargeTicks = 4;
        private int fuseTicks = 40;
        private FuseMode fuseMode = FuseMode.FROM_THROW;
        private boolean bounce = true;
        private float bounceDamping = 0.55f;
        private boolean stickOnImpact = false;
        private boolean breaksBlocks = false;
        private float explosionRadius = 0.0f;
        private float explosionDamage = -1.0f;
        private float explosionKnockback = 1.0f;
        private float effectRadius = 0.0f;
        private int effectDurationTicks = 0;
        private int effectAmplifier = 0;
        private int cooldownTicks = 15;
        private ThrowableDetonation detonation = ThrowableDetonation.NONE;

        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

        public Builder minThrowSpeed(float minThrowSpeed) {
            this.minThrowSpeed = minThrowSpeed;
            return this;
        }

        public Builder maxThrowSpeed(float maxThrowSpeed) {
            this.maxThrowSpeed = maxThrowSpeed;
            return this;
        }

        public Builder maxChargeTicks(int maxChargeTicks) {
            this.maxChargeTicks = maxChargeTicks;
            return this;
        }

        public Builder minChargeTicks(int minChargeTicks) {
            this.minChargeTicks = minChargeTicks;
            return this;
        }

        public Builder fuseTicks(int fuseTicks) {
            this.fuseTicks = fuseTicks;
            return this;
        }

        public Builder fuseMode(FuseMode fuseMode) {
            this.fuseMode = fuseMode;
            return this;
        }

        public Builder bounce(boolean bounce) {
            this.bounce = bounce;
            return this;
        }

        public Builder bounceDamping(float bounceDamping) {
            this.bounceDamping = bounceDamping;
            return this;
        }

        public Builder stickOnImpact(boolean stickOnImpact) {
            this.stickOnImpact = stickOnImpact;
            return this;
        }

        public Builder breaksBlocks(boolean breaksBlocks) {
            this.breaksBlocks = breaksBlocks;
            return this;
        }

        public Builder explosionRadius(float explosionRadius) {
            this.explosionRadius = explosionRadius;
            return this;
        }

        /**
         * Max hearts-scale damage at the center of the blast (20 = a full player health bar).
         * Omit for vanilla radius-scaled damage. {@code 0} damages no entities.
         */
        public Builder explosionDamage(float explosionDamage) {
            this.explosionDamage = explosionDamage;
            return this;
        }

        public Builder explosionKnockback(float explosionKnockback) {
            this.explosionKnockback = explosionKnockback;
            return this;
        }

        public Builder effectRadius(float effectRadius) {
            this.effectRadius = effectRadius;
            return this;
        }

        public Builder effectDurationTicks(int effectDurationTicks) {
            this.effectDurationTicks = effectDurationTicks;
            return this;
        }

        public Builder effectAmplifier(int effectAmplifier) {
            this.effectAmplifier = effectAmplifier;
            return this;
        }

        public Builder cooldownTicks(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
            return this;
        }

        public Builder detonation(ThrowableDetonation detonation) {
            this.detonation = detonation;
            return this;
        }

        public ThrowableWeaponSpec build() {
            return new ThrowableWeaponSpec(this);
        }
    }
}
