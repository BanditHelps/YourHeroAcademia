package com.github.bandithelps.entities;

import net.minecraft.core.Holder;
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

import java.util.List;

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
    private List<Holder<MobEffect>> effects;
    private @Nullable Integer expirationTicks;

    public PotionEffectGeneratorEntity(EntityType<? extends ArmorStand> p_31553_, Level p_31554_) {
        super(p_31553_, p_31554_);
        this.radius = 5;
        this.duration = 20;
        this.amplifier = 0;
        this.showEffectParticles = false;
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

    public void setAmplifier(int amplifier) {
        this.amplifier = amplifier;
    }

    public void setEffectVisible(boolean visible) {
        this.showEffectParticles = visible;
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

            }

        }

        super.tick();
    }
}
