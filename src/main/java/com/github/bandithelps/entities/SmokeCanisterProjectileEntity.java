package com.github.bandithelps.entities;

import com.github.bandithelps.effects.ModEffects;
import com.github.bandithelps.items.SmokeCanisterData;
import com.github.bandithelps.particles.ModParticles;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;

public class SmokeCanisterProjectileEntity extends ThrowableItemProjectile {
    private static final int FUSE_TICKS = 40;
    // Potion effects are reapplied every 10 ticks by the cloud generator; keep duration short
    // so infused effects quickly fall off after leaving the AoE.
    private static final int INFUSED_EFFECT_DURATION_TICKS = 12;

    private boolean armed = false;
    private int armedTicks = 0;

    public SmokeCanisterProjectileEntity(EntityType<? extends SmokeCanisterProjectileEntity> entityType, Level level) {
        super(entityType, level);
    }

    public SmokeCanisterProjectileEntity(Level level, LivingEntity thrower) {
        super(ModEntities.SMOKE_CANISTER_PROJECTILE.get(), thrower, level, ItemStack.EMPTY);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AIR;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.armIfNeeded();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        this.armIfNeeded();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || !this.armed) {
            return;
        }

        this.armedTicks++;
        if (this.armedTicks >= FUSE_TICKS) {
            this.detonate();
        }
    }

    private void armIfNeeded() {
        if (this.armed || this.level().isClientSide()) {
            return;
        }
        this.armed = true;
        this.armedTicks = 0;
        this.setDeltaMovement(0.0d, 0.0d, 0.0d);
        this.setNoGravity(true);
        this.level().playSound(null, this.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.35f, 1.5f);
    }

    private void detonate() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.discard();
            return;
        }

        ItemStack canisterStack = this.getItem();
        int tier = Math.max(1, SmokeCanisterData.getTier(canisterStack));
        float radius = 3.0f + (tier * 1.5f);
        int smokeDuration = 21;
        int cloudLifetime = 100 + (tier * 40);
        float particleDensity = 1.0f + (tier * 0.8f);

        List<Holder<MobEffect>> baseEffects = List.of(ModEffects.SMOKE_BLIND);
        List<MobEffectInstance> extraEffects = this.resolvePotionEffects(canisterStack);

        PotionEffectGeneratorEntity cloud = new PotionEffectGeneratorEntity(ModEntities.POTION_GENERATOR.get(), serverLevel);
        cloud.setPos(this.position());
        cloud.setRadius(radius);
        cloud.setDuration(smokeDuration);
        cloud.setAmplifier(0);
        cloud.setGeneratorHealth(100.0f);
        cloud.setEffects(baseEffects);
        cloud.setExtraEffects(extraEffects);
        cloud.setEffectVisible(false);
        cloud.setGenerateParticles(true);
        cloud.setParticles(List.of(ModParticles.SMOKESCREEN.get()));
        cloud.setParticleSize(0.8f);
        cloud.setParticleDensity(particleDensity);
        cloud.setExpirationTicks(cloudLifetime);
        cloud.setInvisible(true);
        cloud.setNoGravity(true);
        serverLevel.addFreshEntity(cloud);

        this.level().playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 0.9f);
        this.discard();
    }

    private List<MobEffectInstance> resolvePotionEffects(ItemStack canisterStack) {
        PotionContents potionContents = SmokeCanisterData.getPotionContents(canisterStack);
        if (potionContents == null) {
            return List.of();
        }

        List<MobEffectInstance> extraEffects = new ArrayList<>();
        for (MobEffectInstance effectInstance : potionContents.getAllEffects()) {
            if (effectInstance.getEffect().is(ModEffects.SMOKE_BLIND)) {
                continue;
            }
            int clampedAmplifier = Math.max(0, effectInstance.getAmplifier());
            extraEffects.add(new MobEffectInstance(effectInstance.getEffect(), INFUSED_EFFECT_DURATION_TICKS, clampedAmplifier, true, false));
        }
        return extraEffects;
    }
}
