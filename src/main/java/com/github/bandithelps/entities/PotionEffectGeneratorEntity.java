package com.github.bandithelps.entities;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class PotionEffectGeneratorEntity extends ArmorStand {

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.ATTACK_DAMAGE, 0);
    }

    private float radius;
    private int duration;
    private int amplifier;
    private boolean showEffectParticles;
    private boolean generateParticles;
    private float particleSize;
    private float particleDensity;
    private List<Holder<MobEffect>> effects;
    private List<MobEffectInstance> extraEffects;
    private List<SimpleParticleType> particles;
    private @Nullable Integer expirationTicks;

    public PotionEffectGeneratorEntity(EntityType<? extends ArmorStand> p_31553_, Level p_31554_) {
        super(p_31553_, p_31554_);
        this.radius = 5;
        this.duration = 20;
        this.amplifier = 0;
        this.showEffectParticles = false;
        this.generateParticles = false;
        this.particleSize = 0.25f;
        this.particleDensity = 1.0f;
        this.extraEffects = Collections.emptyList();
        this.particles = Collections.emptyList();
        this.expirationTicks = null;
        this.setInvisible(true);
        this.setNoGravity(true);
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public void setEffects(List<Holder<MobEffect>> effects) {
        this.effects = effects;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setExtraEffects(List<MobEffectInstance> extraEffects) {
        if (extraEffects == null || extraEffects.isEmpty()) {
            this.extraEffects = Collections.emptyList();
            return;
        }
        List<MobEffectInstance> sanitized = new ArrayList<>(extraEffects.size());
        for (MobEffectInstance effectInstance : extraEffects) {
            sanitized.add(new MobEffectInstance(effectInstance));
        }
        this.extraEffects = sanitized;
    }

    public void setAmplifier(int amplifier) {
        this.amplifier = amplifier;
    }

    public void setEffectVisible(boolean visible) {
        this.showEffectParticles = visible;
    }

    public void setGenerateParticles(boolean generateParticles) {
        this.generateParticles = generateParticles;
    }

    public void setParticles(List<SimpleParticleType> particles) {
        this.particles = particles;
    }

    public void setParticleSize(float particleSize) {
        this.particleSize = Math.max(0.0f, particleSize);
    }

    public void setParticleDensity(float particleDensity) {
        this.particleDensity = Math.max(0.0f, particleDensity);
    }

    public void setExpirationTicks(@Nullable Integer expirationTicks) {
        this.expirationTicks = expirationTicks;
    }

    public void setGeneratorHealth(float health) {
        float clampedHealth = Math.max(1.0f, health);
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(clampedHealth);
        }
        this.setHealth(clampedHealth);
    }

    @Override
    public @Nullable AttributeInstance getAttribute(Holder<Attribute> attribute) {
        return super.getAttribute(attribute);
    }

    @Override
    public void tick() {
        if (this.expirationTicks != null && this.tickCount >= this.expirationTicks) {
            if (!this.level().isClientSide()) {
                this.discard();
            }
            return;
        }

        if (!this.level().isClientSide() && this.generateParticles && this.particles != null && !this.particles.isEmpty() && this.particleDensity > 0.0f && (this.tickCount % 2) == 0) {
            ServerLevel serverLevel = (ServerLevel) this.level();
            int particleCount = Math.max(1, Math.round(this.radius * 2.0f * this.particleDensity));
            for (int i = 0; i < particleCount; i++) {
                SimpleParticleType particle = this.particles.get(this.getRandom().nextInt(this.particles.size()));
                double x;
                double y;
                double z;
                double lenSq;
                do {
                    x = this.getRandom().nextDouble() * 2.0 - 1.0;
                    y = this.getRandom().nextDouble() * 2.0 - 1.0;
                    z = this.getRandom().nextDouble() * 2.0 - 1.0;
                    lenSq = x * x + y * y + z * z;
                } while (lenSq > 1.0);

                serverLevel.sendParticles(
                        particle,
                        this.getX() + (x * this.radius),
                        this.getY() + (y * this.radius),
                        this.getZ() + (z * this.radius),
                        1,
                        this.particleSize,
                        this.particleSize,
                        this.particleSize,
                        0.0
                );
            }
        }

        if ((this.tickCount % 10) == 0 && this.effects != null && !this.effects.isEmpty()) {

            AABB box = this.getBoundingBox().inflate(this.radius);

            List<Entity> nearby = this.level().getEntities(this, box);

            List<Entity> inCircle = nearby.stream()
                    .filter(e -> e != this && e instanceof LivingEntity living && living.isAlive() && (e.distanceToSqr(this) <= radius * radius))
                    .toList();

            for (Entity e : inCircle) {
                LivingEntity livingEntity = (LivingEntity) e;

                for (Holder<MobEffect> effect : this.effects) {
                    livingEntity.addEffect(new MobEffectInstance(effect, this.duration, this.amplifier, true, this.showEffectParticles));
                }
                if (this.extraEffects != null && !this.extraEffects.isEmpty()) {
                    for (MobEffectInstance effectInstance : this.extraEffects) {
                        livingEntity.addEffect(new MobEffectInstance(effectInstance));
                    }
                }

            }

        }

        super.tick();
    }
}
