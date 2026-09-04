package com.github.bandithelps.throwable;

import com.github.bandithelps.effects.ModEffects;
import com.github.bandithelps.entities.ModEntities;
import com.github.bandithelps.entities.PotionEffectGeneratorEntity;
import com.github.bandithelps.items.SmokeCanisterData;
import com.github.bandithelps.particles.ModParticles;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawns the existing smokescreen potion cloud. Tier still drives radius/lifetime;
 * {@link ThrowableWeaponSpec#scale()} is applied on top.
 */
public final class SmokeCloudDetonation implements ThrowableDetonation {
    public static final SmokeCloudDetonation INSTANCE = new SmokeCloudDetonation();
    private static final int INFUSED_EFFECT_DURATION_TICKS = 12;

    private SmokeCloudDetonation() {
    }

    @Override
    public void detonate(ThrownWeaponEntity projectile, ServerLevel serverLevel, ThrowableWeaponSpec spec) {
        ItemStack canisterStack = projectile.getItem();
        int tier = Math.max(1, SmokeCanisterData.getTier(canisterStack));
        float radius = (3.0f + (tier * 1.5f)) * spec.scale();
        int smokeDuration = 21;
        int cloudLifetime = Math.round((100 + (tier * 40)) * spec.scale());
        float particleDensity = (1.0f + (tier * 0.8f)) * spec.scale();

        List<Holder<MobEffect>> baseEffects = List.of(ModEffects.SMOKE_BLIND);
        List<MobEffectInstance> extraEffects = resolvePotionEffects(canisterStack);

        PotionEffectGeneratorEntity cloud = new PotionEffectGeneratorEntity(ModEntities.POTION_GENERATOR.get(), serverLevel);
        cloud.setPos(projectile.position());
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
        cloud.setExpirationTicks(Math.max(1, cloudLifetime));
        cloud.setInvisible(true);
        cloud.setNoGravity(true);
        serverLevel.addFreshEntity(cloud);

        serverLevel.playSound(null, projectile.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 0.9f);
        projectile.discard();
    }

    private static List<MobEffectInstance> resolvePotionEffects(ItemStack canisterStack) {
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
